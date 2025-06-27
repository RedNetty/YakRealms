package com.rednetty.server.core.database;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Enhanced MongoDB connection manager with improved error handling,
 * connection stability, and performance optimizations
 */
public class MongoDBManager {
    private static volatile MongoDBManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Connection management
    private volatile MongoClient mongoClient;
    private volatile MongoDatabase database;
    private final ReentrantReadWriteLock connectionLock = new ReentrantReadWriteLock();

    // Configuration
    private final String connectionString;
    private final String databaseName;
    private final int maxReconnectAttempts;
    private final long reconnectDelay;
    private final int maxConnectionPoolSize;
    private final int minConnectionPoolSize;
    private final long maxConnectionIdleTime;
    private final long maxWaitTime;
    private final long socketTimeout;
    private final long connectTimeout;
    private final long heartbeatFrequency;

    // State tracking
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicInteger operationCount = new AtomicInteger(0);
    private final AtomicInteger failedOperations = new AtomicInteger(0);

    // Tasks and monitoring
    private final Plugin plugin;
    private final Logger logger;
    private final CodecRegistry codecRegistry;
    private int healthCheckTaskId = -1;
    private int reconnectTaskId = -1;

    // Performance tracking
    private volatile long lastSuccessfulOperation = System.currentTimeMillis();
    private volatile long lastConnectionAttempt = 0;
    private volatile String lastError = "";

    private MongoDBManager(FileConfiguration config, Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Load configuration with comprehensive defaults
        this.connectionString = config.getString("mongodb.connection_string", "mongodb://localhost:27017");
        this.databaseName = config.getString("mongodb.database", "yakserver");
        this.maxReconnectAttempts = config.getInt("mongodb.max_reconnect_attempts", 5);
        this.reconnectDelay = config.getLong("mongodb.reconnect_delay_seconds", 30) * 20L;
        this.maxConnectionPoolSize = config.getInt("mongodb.max_connection_pool_size", 100);
        this.minConnectionPoolSize = config.getInt("mongodb.min_connection_pool_size", 5);
        this.maxConnectionIdleTime = config.getLong("mongodb.max_connection_idle_time_ms", 600000);
        this.maxWaitTime = config.getLong("mongodb.max_wait_time_ms", 15000);
        this.socketTimeout = config.getLong("mongodb.socket_timeout_ms", 30000);
        this.connectTimeout = config.getLong("mongodb.connect_timeout_ms", 10000);
        this.heartbeatFrequency = config.getLong("mongodb.heartbeat_frequency_ms", 20000);

        // Configure MongoDB driver logging
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.WARNING);

        // Configure codec registry with enhanced POJO support
        this.codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );

        logger.info("MongoDBManager initialized with database: " + databaseName);
    }

    public static MongoDBManager initialize(FileConfiguration config, Plugin plugin) {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new MongoDBManager(config, plugin);
                }
            }
        }
        return instance;
    }

    public static MongoDBManager getInstance() {
        MongoDBManager result = instance;
        if (result == null) {
            throw new IllegalStateException("MongoDBManager has not been initialized. Call initialize() first.");
        }
        return result;
    }

    /**
     * Enhanced connection method with comprehensive error handling and validation
     */
    public boolean connect() {
        if (shuttingDown.get()) {
            logger.warning("Cannot connect while shutting down");
            return false;
        }

        connectionLock.writeLock().lock();
        try {
            if (connected.get() && isConnectionHealthy()) {
                logger.fine("Already connected to MongoDB and connection is healthy");
                return true;
            }

            // Close existing connection if present
            closeConnectionSafely();

            logger.info("Establishing connection to MongoDB: " + maskConnectionString(connectionString));
            lastConnectionAttempt = System.currentTimeMillis();

            // Build optimized connection settings
            MongoClientSettings settings = buildConnectionSettings();

            // Create MongoDB client with enhanced settings
            this.mongoClient = MongoClients.create(settings);
            this.database = mongoClient.getDatabase(databaseName);

            // Verify connection with timeout
            if (!verifyConnection()) {
                closeConnectionSafely();
                return false;
            }

            // Initialize database structure
            initializeDatabaseStructure();

            // Update state
            connected.set(true);
            reconnectAttempts.set(0);
            lastSuccessfulOperation = System.currentTimeMillis();
            lastError = "";

            logger.info("Successfully connected to MongoDB database: " + databaseName);
            startHealthCheck();

            return true;

        } catch (Exception e) {
            lastError = e.getMessage();
            logger.log(Level.SEVERE, "Failed to connect to MongoDB: " + e.getMessage(), e);
            connected.set(false);
            closeConnectionSafely();
            scheduleReconnect();
            return false;
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    /**
     * Build optimized MongoDB connection settings
     */
    private MongoClientSettings buildConnectionSettings() {
        return MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .codecRegistry(codecRegistry)
                .applyToConnectionPoolSettings(builder ->
                        builder.maxSize(maxConnectionPoolSize)
                                .minSize(minConnectionPoolSize)
                                .maxWaitTime(maxWaitTime, TimeUnit.MILLISECONDS)
                                .maxConnectionIdleTime(maxConnectionIdleTime, TimeUnit.MILLISECONDS)
                                .maxConnectionLifeTime(1800000, TimeUnit.MILLISECONDS) // 30 minutes
                )
                .applyToSocketSettings(builder ->
                        builder.connectTimeout((int) connectTimeout, TimeUnit.MILLISECONDS)
                                .readTimeout((int) socketTimeout, TimeUnit.MILLISECONDS)
                )
                .applyToServerSettings(builder ->
                        builder.heartbeatFrequency(heartbeatFrequency, TimeUnit.MILLISECONDS)
                                .minHeartbeatFrequency(5000, TimeUnit.MILLISECONDS)
                )
                .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(15000, TimeUnit.MILLISECONDS)
                )
                .retryWrites(true)
                .retryReads(true)
                .build();
    }

    /**
     * Enhanced connection verification with comprehensive health checks
     */
    private boolean verifyConnection() {
        try {
            // Test 1: Basic ping
            Document pingResult = database.runCommand(new Document("ping", 1));
            if (pingResult == null || !pingResult.containsKey("ok")) {
                logger.warning("MongoDB ping failed - invalid response");
                return false;
            }

            // Test 2: List collections (tests database access)
            database.listCollectionNames().first();

            // Test 3: Simple read operation
            MongoCollection<Document> testCollection = database.getCollection("_connection_test");
            testCollection.countDocuments();

            logger.fine("MongoDB connection verification successful");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "MongoDB connection verification failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Initialize required database collections and indexes
     */
    private void initializeDatabaseStructure() {
        try {
            Set<String> existingCollections = new HashSet<>();
            for (String name : database.listCollectionNames()) {
                existingCollections.add(name);
            }

            // Create players collection with indexes
            if (!existingCollections.contains("players")) {
                database.createCollection("players");
                logger.info("Created 'players' collection");
            }

            MongoCollection<Document> playersCollection = database.getCollection("players");
            createIndexSafely(playersCollection, new Document("uuid", 1), "uuid_index");
            createIndexSafely(playersCollection, new Document("username", 1), "username_index");
            createIndexSafely(playersCollection, new Document("last_login", -1), "last_login_index");

            // Create backup collection with indexes
            if (!existingCollections.contains("players_backup")) {
                database.createCollection("players_backup");
                logger.info("Created 'players_backup' collection");
            }

            MongoCollection<Document> backupCollection = database.getCollection("players_backup");
            createIndexSafely(backupCollection, new Document("uuid", 1), "uuid_index");
            createIndexSafely(backupCollection, new Document("timestamp", -1), "timestamp_index");
            createIndexSafely(backupCollection,
                    new Document("uuid", 1).append("timestamp", -1), "uuid_timestamp_compound");

            logger.info("Database structure initialization completed");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during database structure initialization", e);
        }
    }

    /**
     * Safely create index with error handling
     */
    private void createIndexSafely(MongoCollection<Document> collection, Document indexDoc, String indexName) {
        try {
            collection.createIndex(indexDoc);
            logger.fine("Created index: " + indexName + " on collection: " + collection.getNamespace().getCollectionName());
        } catch (Exception e) {
            logger.fine("Index " + indexName + " already exists or creation failed: " + e.getMessage());
        }
    }

    /**
     * Enhanced health check with comprehensive monitoring
     */
    private void startHealthCheck() {
        if (healthCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(healthCheckTaskId);
        }

        healthCheckTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (shuttingDown.get()) return;

            boolean isHealthy = performHealthCheck();
            if (!isHealthy && connected.get()) {
                logger.warning("MongoDB health check failed, marking as disconnected");
                connected.set(false);
                scheduleReconnect();
            }
        }, 1200L, 1200L).getTaskId(); // Every minute
    }

    /**
     * Comprehensive health check implementation
     */
    private boolean performHealthCheck() {
        if (!connected.get() || mongoClient == null || database == null) {
            return false;
        }

        try {
            // Quick ping test
            Document result = database.runCommand(new Document("ping", 1));
            boolean pingSuccess = result != null && result.get("ok", Number.class).intValue() == 1;

            if (pingSuccess) {
                lastSuccessfulOperation = System.currentTimeMillis();
                return true;
            } else {
                logger.warning("MongoDB ping returned unsuccessful result");
                return false;
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "MongoDB health check failed", e);
            lastError = e.getMessage();
            return false;
        }
    }

    /**
     * Enhanced connection health check
     */
    private boolean isConnectionHealthy() {
        if (!connected.get() || mongoClient == null || database == null) {
            return false;
        }

        // Check if too much time has passed since last successful operation
        long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessfulOperation;
        if (timeSinceLastSuccess > 300000) { // 5 minutes
            logger.warning("No successful MongoDB operations in " + (timeSinceLastSuccess / 1000) + " seconds");
            return false;
        }

        return true;
    }

    /**
     * Enhanced safe operation execution with better error handling and retry logic
     */
    public <T> T performSafeOperation(DatabaseOperation<T> operation) {
        return performSafeOperation(operation, 3); // Default 3 retries
    }

    public <T> T performSafeOperation(DatabaseOperation<T> operation, int maxRetries) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }

        if (shuttingDown.get()) {
            logger.warning("Attempted database operation during shutdown");
            return null;
        }

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Check connection before each attempt
            if (!ensureConnection()) {
                if (attempt == maxRetries) {
                    logger.severe("Cannot perform database operation - connection failed after " + maxRetries + " attempts");
                    return null;
                }
                continue;
            }

            connectionLock.readLock().lock();
            try {
                operationCount.incrementAndGet();
                T result = operation.execute();
                lastSuccessfulOperation = System.currentTimeMillis();

                if (attempt > 1) {
                    logger.info("Database operation succeeded on attempt " + attempt);
                }

                return result;

            } catch (MongoException e) {
                lastException = e;
                failedOperations.incrementAndGet();

                logger.log(Level.WARNING, "MongoDB operation failed (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage(), e);

                // Analyze error and determine if reconnection is needed
                if (isConnectionError(e)) {
                    connected.set(false);
                    if (attempt < maxRetries) {
                        // Short delay before retry
                        try {
                            Thread.sleep(1000 * attempt); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } else {
                    // Non-connection error, don't retry
                    break;
                }

            } catch (Exception e) {
                lastException = e;
                failedOperations.incrementAndGet();
                logger.log(Level.SEVERE, "Unexpected error during MongoDB operation (attempt " + attempt + "/" + maxRetries + ")", e);

                // For non-MongoDB exceptions, only retry if it might be transient
                if (attempt < maxRetries && isRetryableError(e)) {
                    try {
                        Thread.sleep(500 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break;
                }
            } finally {
                connectionLock.readLock().unlock();
            }
        }

        logger.severe("Database operation failed after " + maxRetries + " attempts. Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown"));
        return null;
    }

    /**
     * Determine if an exception indicates a connection error
     */
    private boolean isConnectionError(Exception e) {
        if (e instanceof MongoException) {
            String message = e.getMessage().toLowerCase();
            return message.contains("connection") ||
                    message.contains("socket") ||
                    message.contains("timeout") ||
                    message.contains("network") ||
                    message.contains("broken pipe") ||
                    e instanceof MongoSocketException ||
                    e instanceof MongoTimeoutException;
        }
        return false;
    }

    /**
     * Determine if an error is retryable
     */
    private boolean isRetryableError(Exception e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("network") ||
                message.contains("temporary");
    }

    /**
     * Ensure connection is available, attempting to connect if needed
     */
    private boolean ensureConnection() {
        if (connected.get() && isConnectionHealthy()) {
            return true;
        }

        // Attempt to reconnect
        return connect();
    }

    /**
     * Enhanced reconnection scheduling with exponential backoff
     */
    private void scheduleReconnect() {
        if (reconnectTaskId != -1 || shuttingDown.get()) {
            return;
        }

        int attempts = reconnectAttempts.incrementAndGet();
        if (attempts > maxReconnectAttempts) {
            logger.severe("Maximum reconnection attempts (" + maxReconnectAttempts + ") reached. Giving up.");
            return;
        }

        // Exponential backoff with jitter
        long delay = Math.min(reconnectDelay * (1L << (attempts - 1)), 300 * 20L); // Max 5 minutes
        delay += (long) (Math.random() * 20L * 10); // Add jitter (0-10 seconds)

        logger.info("Scheduling MongoDB reconnection attempt " + attempts + "/" + maxReconnectAttempts +
                " in " + (delay / 20) + " seconds");

        reconnectTaskId = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            reconnectTaskId = -1;

            if (shuttingDown.get()) return;

            logger.info("Attempting to reconnect to MongoDB (attempt " + attempts + "/" + maxReconnectAttempts + ")");

            boolean success = connect();
            if (success) {
                logger.info("Successfully reconnected to MongoDB");
            } else {
                logger.warning("Reconnection attempt " + attempts + " failed");
            }
        }, delay).getTaskId();
    }

    /**
     * Enhanced disconnection with proper cleanup
     */
    public void disconnect() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return; // Already shutting down
        }

        logger.info("Disconnecting from MongoDB...");

        // Cancel tasks
        if (healthCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(healthCheckTaskId);
            healthCheckTaskId = -1;
        }

        if (reconnectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(reconnectTaskId);
            reconnectTaskId = -1;
        }

        // Close connection
        closeConnectionSafely();

        // Reset state
        connected.set(false);

        logger.info("MongoDB disconnection completed");
    }

    /**
     * Safely close MongoDB connection with proper error handling
     */
    private void closeConnectionSafely() {
        connectionLock.writeLock().lock();
        try {
            if (mongoClient != null) {
                try {
                    // Give pending operations a moment to complete
                    Thread.sleep(100);
                    mongoClient.close();
                    logger.fine("MongoDB client closed successfully");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error closing MongoDB client", e);
                } finally {
                    mongoClient = null;
                    database = null;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during connection cleanup", e);
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    /**
     * Enhanced collection getter with validation and error handling
     */
    public MongoCollection<Document> getCollection(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB. Collection: " + name);
        }

        connectionLock.readLock().lock();
        try {
            if (database == null) {
                throw new IllegalStateException("Database reference is null");
            }

            return database.getCollection(name);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting collection: " + name, e);
            throw new RuntimeException("Failed to get collection: " + name, e);
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * Enhanced typed collection getter
     */
    public <T> MongoCollection<T> getCollection(String name, Class<T> documentClass) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (documentClass == null) {
            throw new IllegalArgumentException("Document class cannot be null");
        }

        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB. Collection: " + name);
        }

        connectionLock.readLock().lock();
        try {
            if (database == null) {
                throw new IllegalStateException("Database reference is null");
            }

            return database.getCollection(name, documentClass);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting typed collection: " + name, e);
            throw new RuntimeException("Failed to get typed collection: " + name, e);
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    // Functional interface for database operations
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws Exception;
    }

    // Enhanced getters and status methods
    public boolean isConnected() {
        return connected.get() && !shuttingDown.get() && mongoClient != null && database != null;
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public MongoDatabase getDatabase() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB");
        }
        return database;
    }

    public MongoClient getMongoClient() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB");
        }
        return mongoClient;
    }

    /**
     * Get comprehensive connection statistics
     */
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
                connected.get(),
                shuttingDown.get(),
                operationCount.get(),
                failedOperations.get(),
                reconnectAttempts.get(),
                lastSuccessfulOperation,
                lastConnectionAttempt,
                lastError
        );
    }

    /**
     * Utility method to mask sensitive information in connection string
     */
    private String maskConnectionString(String connectionString) {
        return connectionString.replaceAll("://[^@]+@", "://***:***@");
    }

    /**
     * Connection statistics class
     */
    public static class ConnectionStats {
        public final boolean connected;
        public final boolean shuttingDown;
        public final int totalOperations;
        public final int failedOperations;
        public final int reconnectAttempts;
        public final long lastSuccessfulOperation;
        public final long lastConnectionAttempt;
        public final String lastError;

        public ConnectionStats(boolean connected, boolean shuttingDown, int totalOperations,
                               int failedOperations, int reconnectAttempts, long lastSuccessfulOperation,
                               long lastConnectionAttempt, String lastError) {
            this.connected = connected;
            this.shuttingDown = shuttingDown;
            this.totalOperations = totalOperations;
            this.failedOperations = failedOperations;
            this.reconnectAttempts = reconnectAttempts;
            this.lastSuccessfulOperation = lastSuccessfulOperation;
            this.lastConnectionAttempt = lastConnectionAttempt;
            this.lastError = lastError;
        }

        public double getSuccessRate() {
            return totalOperations > 0 ? (double) (totalOperations - failedOperations) / totalOperations : 1.0;
        }

        public long getTimeSinceLastSuccess() {
            return System.currentTimeMillis() - lastSuccessfulOperation;
        }

        @Override
        public String toString() {
            return String.format("ConnectionStats{connected=%s, operations=%d, success_rate=%.2f%%, reconnects=%d}",
                    connected, totalOperations, getSuccessRate() * 100, reconnectAttempts);
        }
    }
}