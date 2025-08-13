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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * MongoDB connection manager with bulletproof connection handling and automatic recovery
 *  VERSION: Improved connection stability, enhanced error recovery, and comprehensive monitoring
 */
public class MongoDBManager {
    private static volatile MongoDBManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Enhanced connection management
    private volatile MongoClient mongoClient;
    private volatile MongoDatabase database;
    private final ReentrantReadWriteLock connectionLock = new ReentrantReadWriteLock();

    // Enhanced configuration
    private final String connectionString;
    private final String databaseName;
    private final int maxConnectionPoolSize;
    private final int minConnectionPoolSize;
    private final long maxConnectionIdleTime;
    private final long maxWaitTime;
    private final long socketTimeout;
    private final long connectTimeout;
    private final long heartbeatFrequency;
    private final long serverSelectionTimeout;
    private final boolean enableRetryWrites;
    private final boolean enableRetryReads;

    // Enhanced state tracking
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean healthCheckActive = new AtomicBoolean(false);
    private final AtomicBoolean autoRecoveryEnabled = new AtomicBoolean(true);

    // Enhanced performance tracking
    private final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulConnections = new AtomicInteger(0);
    private final AtomicInteger failedConnections = new AtomicInteger(0);
    private final AtomicInteger totalOperations = new AtomicInteger(0);
    private final AtomicInteger failedOperations = new AtomicInteger(0);
    private final AtomicInteger recoveryAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulRecoveries = new AtomicInteger(0);
    private final AtomicLong lastSuccessfulPing = new AtomicLong(0);
    private final AtomicLong totalDowntime = new AtomicLong(0);
    private final AtomicLong lastConnectionTime = new AtomicLong(0);

    // Core dependencies
    private final Plugin plugin;
    private final Logger logger;
    private final CodecRegistry codecRegistry;

    // Enhanced monitoring and recovery
    private BukkitTask healthCheckTask;
    private BukkitTask connectionMonitorTask;
    private final long healthCheckInterval;
    private final long connectionMonitorInterval;
    private final int maxRecoveryAttempts;
    private final long recoveryDelay;

    private MongoDBManager(FileConfiguration config, Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Enhanced configuration loading with comprehensive defaults
        this.connectionString = config.getString("mongodb.connection_string", "mongodb://localhost:27017");
        this.databaseName = config.getString("mongodb.database", "yakrealms");
        this.maxConnectionPoolSize = config.getInt("mongodb.max_connection_pool_size", 50);
        this.minConnectionPoolSize = config.getInt("mongodb.min_connection_pool_size", 5);
        this.maxConnectionIdleTime = config.getLong("mongodb.max_connection_idle_time_ms", 600000);
        this.maxWaitTime = config.getLong("mongodb.max_wait_time_ms", 15000);
        this.socketTimeout = config.getLong("mongodb.socket_timeout_ms", 30000);
        this.connectTimeout = config.getLong("mongodb.connect_timeout_ms", 10000);
        this.heartbeatFrequency = config.getLong("mongodb.heartbeat_frequency_ms", 10000);
        this.serverSelectionTimeout = config.getLong("mongodb.server_selection_timeout_ms", 30000);
        this.enableRetryWrites = config.getBoolean("mongodb.enable_retry_writes", true);
        this.enableRetryReads = config.getBoolean("mongodb.enable_retry_reads", true);

        // Enhanced monitoring configuration
        this.healthCheckInterval = config.getLong("mongodb.health_check_interval_ms", 30000);
        this.connectionMonitorInterval = config.getLong("mongodb.connection_monitor_interval_ms", 10000);
        this.maxRecoveryAttempts = config.getInt("mongodb.max_recovery_attempts", 5);
        this.recoveryDelay = config.getLong("mongodb.recovery_delay_ms", 5000);

        // Enhanced MongoDB driver logging configuration
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.WARNING);

        // Enhanced codec registry with error handling
        try {
            this.codecRegistry = fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    fromProviders(PojoCodecProvider.builder().automatic(true).build())
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize codec registry", e);
            throw new RuntimeException("Codec registry initialization failed", e);
        }

        logger.info(" MongoDBManager initialized with database: " + databaseName);
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
            throw new IllegalStateException(" MongoDBManager has not been initialized. Call initialize() first.");
        }
        return result;
    }

    /**
     * connection with comprehensive error handling and retry logic
     */
    public boolean connect() {
        connectionLock.writeLock().lock();
        try {
            if (shuttingDown.get()) {
                logger.warning("Cannot connect while shutting down");
                return false;
            }

            connectionAttempts.incrementAndGet();

            if (connected.get() && isConnectionHealthy()) {
                logger.fine("Already connected to Enhanced MongoDB");
                return true;
            }

            logger.info("Establishing enhanced connection to MongoDB: " + maskConnectionString(connectionString));

            // Close existing connection if present
            closeConnectionInternal();

            // Build enhanced connection settings
            MongoClientSettings settings = buildEnhancedConnectionSettings();
            if (settings == null) {
                logger.severe("Failed to build MongoDB connection settings");
                failedConnections.incrementAndGet();
                return false;
            }

            // Create MongoDB client with enhanced error handling
            try {
                this.mongoClient = MongoClients.create(settings);
                this.database = mongoClient.getDatabase(databaseName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to create MongoDB client", e);
                failedConnections.incrementAndGet();
                closeConnectionInternal();
                return false;
            }

            // Enhanced connection testing
            if (!performEnhancedConnectionTest()) {
                logger.severe(" connection test failed");
                failedConnections.incrementAndGet();
                closeConnectionInternal();
                return false;
            }

            // Initialize enhanced database structure
            if (!initializeEnhancedDatabaseStructure()) {
                logger.warning("Database structure initialization had issues - continuing anyway");
            }

            // Start enhanced monitoring
            startEnhancedMonitoring();

            connected.set(true);
            successfulConnections.incrementAndGet();
            lastConnectionTime.set(System.currentTimeMillis());
            lastSuccessfulPing.set(System.currentTimeMillis());

            logger.info("✓ Successfully connected to Enhanced MongoDB database: " + databaseName);
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error during enhanced MongoDB connection: " + e.getMessage(), e);
            connected.set(false);
            failedConnections.incrementAndGet();
            closeConnectionInternal();
            return false;
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    /**
     * connection settings with comprehensive configuration
     */
    private MongoClientSettings buildEnhancedConnectionSettings() {
        try {
            return MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .codecRegistry(codecRegistry)
                    .applyToConnectionPoolSettings(builder ->
                            builder.maxSize(maxConnectionPoolSize)
                                    .minSize(minConnectionPoolSize)
                                    .maxWaitTime(maxWaitTime, TimeUnit.MILLISECONDS)
                                    .maxConnectionIdleTime(maxConnectionIdleTime, TimeUnit.MILLISECONDS)
                                    .maxConnectionLifeTime(maxConnectionIdleTime * 2, TimeUnit.MILLISECONDS)
                    )
                    .applyToSocketSettings(builder ->
                            builder.connectTimeout((int) connectTimeout, TimeUnit.MILLISECONDS)
                                    .readTimeout((int) socketTimeout, TimeUnit.MILLISECONDS)
                    )
                    .applyToServerSettings(builder ->
                            builder.heartbeatFrequency(heartbeatFrequency, TimeUnit.MILLISECONDS)
                                    .minHeartbeatFrequency(Math.min(heartbeatFrequency / 2, 5000), TimeUnit.MILLISECONDS)
                    )
                    .applyToClusterSettings(builder ->
                            builder.serverSelectionTimeout(serverSelectionTimeout, TimeUnit.MILLISECONDS)
                    )
                    .retryWrites(enableRetryWrites)
                    .retryReads(enableRetryReads)
                    .build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error building enhanced connection settings", e);
            return null;
        }
    }

    /**
     * connection testing with comprehensive validation
     */
    private boolean performEnhancedConnectionTest() {
        try {
            logger.fine("Performing enhanced MongoDB connection test...");

            // Test 1: Basic ping
            Document pingResult = database.runCommand(new Document("ping", 1));
            if (pingResult == null || !pingResult.containsKey("ok")) {
                logger.warning(" MongoDB ping failed - invalid response");
                return false;
            }

            // Test 2: Collection access
            try {
                database.listCollectionNames().first();
            } catch (Exception e) {
                logger.warning(" MongoDB collection access test failed: " + e.getMessage());
                return false;
            }

            // Test 3: Write operation test
            try {
                String testCollectionName = "connection_test";
                MongoCollection<Document> testCollection = database.getCollection(testCollectionName);
                Document testDoc = new Document("test", "connection_verification")
                        .append("timestamp", System.currentTimeMillis());
                testCollection.insertOne(testDoc);
                testCollection.deleteOne(new Document("test", "connection_verification"));
                logger.fine(" write operation test successful");
            } catch (Exception e) {
                logger.warning(" write operation test failed: " + e.getMessage());
                // Don't fail the connection for write test failure - might be permissions
            }

            // Test 4: Database stats
            try {
                Document stats = database.runCommand(new Document("dbStats", 1));
                if (stats != null) {
                    logger.fine(" database stats retrieved successfully");
                }
            } catch (Exception e) {
                logger.fine("Database stats retrieval failed (non-critical): " + e.getMessage());
            }

            logger.fine(" MongoDB connection test completed successfully");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, " MongoDB connection test failed", e);
            return false;
        }
    }

    /**
     * database structure initialization
     */
    private boolean initializeEnhancedDatabaseStructure() {
        try {
            logger.info("Initializing enhanced database structure...");

            // Create collections with enhanced error handling
            boolean playersCreated = createCollectionSafely("players");
            boolean backupCreated = createCollectionSafely("players_backup");
            boolean systemCreated = createCollectionSafely("system_info");

            if (!playersCreated) {
                logger.warning("Failed to ensure players collection exists");
            }
            if (!backupCreated) {
                logger.warning("Failed to ensure players_backup collection exists");
            }

            // Create enhanced indexes
            createEnhancedIndexes();

            // Initialize system information
            initializeSystemInfo();

            logger.info(" database structure initialization completed");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during enhanced database structure initialization", e);
            return false;
        }
    }

    /**
     * collection creation with better error handling
     */
    private boolean createCollectionSafely(String collectionName) {
        try {
            // Check if collection already exists
            boolean exists = false;
            for (String name : database.listCollectionNames()) {
                if (name.equals(collectionName)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                database.createCollection(collectionName);
                logger.info("Created collection: " + collectionName);
            } else {
                logger.fine("Collection already exists: " + collectionName);
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create/verify collection: " + collectionName, e);
            return false;
        }
    }

    /**
     * index creation with comprehensive error handling
     */
    private void createEnhancedIndexes() {
        try {
            // Players collection indexes
            MongoCollection<Document> playersCollection = database.getCollection("players");
            createIndexSafely(playersCollection, new Document("uuid", 1), "uuid_index");
            createIndexSafely(playersCollection, new Document("username", 1), "username_index");
            createIndexSafely(playersCollection, new Document("last_login", -1), "last_login_index");
            createIndexSafely(playersCollection, new Document("ip_address", 1), "ip_address_index");
            createIndexSafely(playersCollection, new Document("banned", 1), "banned_index");

            // Compound indexes for better query performance
            createIndexSafely(playersCollection,
                    new Document("username", 1).append("last_login", -1), "username_last_login_index");

            // Backup collection indexes
            MongoCollection<Document> backupCollection = database.getCollection("players_backup");
            createIndexSafely(backupCollection, new Document("uuid", 1), "backup_uuid_index");
            createIndexSafely(backupCollection, new Document("timestamp", -1), "backup_timestamp_index");
            createIndexSafely(backupCollection,
                    new Document("uuid", 1).append("timestamp", -1), "backup_uuid_timestamp_index");

            // System info indexes
            MongoCollection<Document> systemCollection = database.getCollection("system_info");
            createIndexSafely(systemCollection, new Document("key", 1), "system_key_index");

            logger.info(" database indexes created successfully");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating enhanced indexes", e);
        }
    }

    /**
     * index creation with better error handling
     */
    private void createIndexSafely(MongoCollection<Document> collection, Document indexDoc, String indexName) {
        try {
            collection.createIndex(indexDoc);
            logger.fine("Created enhanced index: " + indexName);
        } catch (Exception e) {
            logger.fine(" index " + indexName + " creation failed or already exists: " + e.getMessage());
        }
    }

    /**
     * Initialize system information collection
     */
    private void initializeSystemInfo() {
        try {
            MongoCollection<Document> systemCollection = database.getCollection("system_info");

            // Store system startup information
            Document systemInfo = new Document("key", "database_info")
                    .append("database_name", databaseName)
                    .append("initialized_at", System.currentTimeMillis())
                    .append("server_version", plugin.getServer().getVersion())
                    .append("plugin_version", plugin.getDescription().getVersion())
                    .append("connection_pool_size", maxConnectionPoolSize);

            systemCollection.replaceOne(
                    new Document("key", "database_info"),
                    systemInfo,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
            );

            logger.fine("System information initialized");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to initialize system information", e);
        }
    }

    /**
     * monitoring system
     */
    private void startEnhancedMonitoring() {
        if (healthCheckActive.compareAndSet(false, true)) {
            // Health check task
            healthCheckTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!shuttingDown.get()) {
                        performEnhancedHealthCheck();
                    }
                }
            }.runTaskTimerAsynchronously(plugin,
                    healthCheckInterval / 50, // Convert to ticks
                    healthCheckInterval / 50);

            // Connection monitor task
            connectionMonitorTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!shuttingDown.get()) {
                        monitorConnectionStatus();
                    }
                }
            }.runTaskTimerAsynchronously(plugin,
                    connectionMonitorInterval / 50, // Convert to ticks
                    connectionMonitorInterval / 50);

            logger.info(" MongoDB monitoring started");
        }
    }

    /**
     * health check with comprehensive testing
     */
    private void performEnhancedHealthCheck() {
        connectionLock.readLock().lock();
        try {
            if (!connected.get() || mongoClient == null || database == null) {
                handleUnhealthyConnection("Connection state invalid");
                return;
            }

            // Perform ping test
            try {
                Document result = database.runCommand(new Document("ping", 1));
                if (result != null && result.get("ok", Number.class).intValue() == 1) {
                    lastSuccessfulPing.set(System.currentTimeMillis());
                    // Connection is healthy
                    return;
                }
            } catch (Exception e) {
                logger.fine("Health check ping failed: " + e.getMessage());
            }

            handleUnhealthyConnection("Health check failed");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during enhanced health check", e);
            handleUnhealthyConnection("Health check error: " + e.getMessage());
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * Monitor connection status and performance
     */
    private void monitorConnectionStatus() {
        try {
            long timeSinceLastPing = System.currentTimeMillis() - lastSuccessfulPing.get();

            if (timeSinceLastPing > (healthCheckInterval * 3)) {
                logger.warning("No successful ping in " + (timeSinceLastPing / 1000) + " seconds");

                if (autoRecoveryEnabled.get()) {
                    attemptAutoRecovery("Extended ping failure");
                }
            }

            // Log performance metrics occasionally
            if (connectionAttempts.get() % 10 == 0 && connectionAttempts.get() > 0) {
                logPerformanceMetrics();
            }

        } catch (Exception e) {
            logger.log(Level.FINE, "Error monitoring connection status", e);
        }
    }

    /**
     * Handle unhealthy connection state
     */
    private void handleUnhealthyConnection(String reason) {
        logger.warning(" MongoDB connection unhealthy: " + reason);

        connected.set(false);

        if (autoRecoveryEnabled.get()) {
            attemptAutoRecovery(reason);
        }
    }

    /**
     * Attempt automatic recovery
     */
    private void attemptAutoRecovery(String reason) {
        if (shuttingDown.get()) {
            return;
        }

        recoveryAttempts.incrementAndGet();

        if (recoveryAttempts.get() > maxRecoveryAttempts) {
            logger.severe("Max recovery attempts exceeded - disabling auto-recovery");
            autoRecoveryEnabled.set(false);
            return;
        }

        logger.info("Attempting enhanced MongoDB recovery (attempt " + recoveryAttempts.get() + "): " + reason);

        // Schedule recovery attempt
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                if (connect()) {
                    logger.info(" MongoDB recovery successful");
                    successfulRecoveries.incrementAndGet();
                    // Reset recovery counter on success
                    recoveryAttempts.set(0);
                } else {
                    logger.warning(" MongoDB recovery failed");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during recovery attempt", e);
            }
        }, recoveryDelay / 50); // Convert to ticks
    }

    /**
     * connection health check
     */
    private boolean isConnectionHealthy() {
        connectionLock.readLock().lock();
        try {
            if (!connected.get() || mongoClient == null || database == null) {
                return false;
            }

            // Quick health check
            long timeSinceLastPing = System.currentTimeMillis() - lastSuccessfulPing.get();
            if (timeSinceLastPing > (healthCheckInterval * 2)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.fine("Connection health check error: " + e.getMessage());
            return false;
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * safe database operation with comprehensive retry logic
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

        totalOperations.incrementAndGet();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Ensure connection before each attempt
            if (!ensureConnection()) {
                if (attempt == maxRetries) {
                    logger.severe("Cannot perform database operation - connection failed after " + maxRetries + " attempts");
                    failedOperations.incrementAndGet();
                    return null;
                }
                continue;
            }

            connectionLock.readLock().lock();
            try {
                T result = operation.execute();
                if (attempt > 1) {
                    logger.fine("Database operation succeeded on attempt " + attempt);
                }
                return result;

            } catch (MongoException e) {
                lastException = e;
                logger.log(Level.WARNING, "MongoDB operation failed (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage(), e);

                // Check if this is a connection error that warrants retry
                if (isConnectionError(e)) {
                    connected.set(false);
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(1000 * attempt); // Progressive delay
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
                logger.log(Level.SEVERE, "Unexpected error during MongoDB operation", e);
                break; // Don't retry unexpected errors
            } finally {
                connectionLock.readLock().unlock();
            }
        }

        failedOperations.incrementAndGet();
        logger.severe("Database operation failed after " + maxRetries + " attempts. Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown"));
        return null;
    }

    /**
     * connection error detection
     */
    private boolean isConnectionError(Exception e) {
        if (e instanceof MongoException) {
            String message = e.getMessage().toLowerCase();
            return message.contains("connection") ||
                    message.contains("socket") ||
                    message.contains("timeout") ||
                    message.contains("network") ||
                    message.contains("closed") ||
                    message.contains("broken") ||
                    e instanceof MongoSocketException ||
                    e instanceof MongoTimeoutException ||
                    e instanceof MongoSocketOpenException ||
                    e instanceof MongoSocketClosedException;
        }
        return false;
    }

    /**
     * connection ensuring with retry logic
     */
    private boolean ensureConnection() {
        if (connected.get() && isConnectionHealthy()) {
            return true;
        }

        // Attempt to reconnect
        return connect();
    }

    /**
     * collection retrieval with safety checks
     */
    public MongoCollection<Document> getCollection(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        connectionLock.readLock().lock();
        try {
            if (!isConnected()) {
                throw new IllegalStateException("Not connected to Enhanced MongoDB. Collection: " + name);
            }

            return database.getCollection(name);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting enhanced collection: " + name, e);
            throw new RuntimeException("Failed to get enhanced collection: " + name, e);
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * typed collection retrieval
     */
    public <T> MongoCollection<T> getCollection(String name, Class<T> documentClass) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (documentClass == null) {
            throw new IllegalArgumentException("Document class cannot be null");
        }

        connectionLock.readLock().lock();
        try {
            if (!isConnected()) {
                throw new IllegalStateException("Not connected to Enhanced MongoDB. Collection: " + name);
            }

            return database.getCollection(name, documentClass);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting enhanced typed collection: " + name, e);
            throw new RuntimeException("Failed to get enhanced typed collection: " + name, e);
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * disconnect with comprehensive cleanup
     */
    public void disconnect() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return; // Already shutting down
        }

        logger.info("Disconnecting from Enhanced MongoDB...");

        // Stop monitoring
        stopEnhancedMonitoring();

        // Close connection
        closeConnectionInternal();

        connected.set(false);
        logger.info(" MongoDB disconnection completed");
    }

    /**
     * Stop enhanced monitoring
     */
    private void stopEnhancedMonitoring() {
        healthCheckActive.set(false);

        if (healthCheckTask != null && !healthCheckTask.isCancelled()) {
            healthCheckTask.cancel();
            healthCheckTask = null;
        }

        if (connectionMonitorTask != null && !connectionMonitorTask.isCancelled()) {
            connectionMonitorTask.cancel();
            connectionMonitorTask = null;
        }

        logger.fine(" monitoring stopped");
    }

    /**
     * connection closure
     */
    private void closeConnectionInternal() {
        connectionLock.writeLock().lock();
        try {
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                    logger.fine(" MongoDB client closed successfully");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error closing enhanced MongoDB client", e);
                } finally {
                    mongoClient = null;
                    database = null;
                }
            }
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    /**
     * performance metrics logging
     */
    private void logPerformanceMetrics() {
        try {
            logger.info("=== Enhanced MongoDB Performance Metrics ===");
            logger.info("Connection Attempts: " + connectionAttempts.get());
            logger.info("Successful Connections: " + successfulConnections.get());
            logger.info("Failed Connections: " + failedConnections.get());
            logger.info("Total Operations: " + totalOperations.get());
            logger.info("Failed Operations: " + failedOperations.get());
            logger.info("Recovery Attempts: " + recoveryAttempts.get());
            logger.info("Successful Recoveries: " + successfulRecoveries.get());

            long timeSinceLastPing = System.currentTimeMillis() - lastSuccessfulPing.get();
            logger.info("Time Since Last Ping: " + (timeSinceLastPing / 1000) + "s");

            if (totalOperations.get() > 0) {
                double successRate = ((double) (totalOperations.get() - failedOperations.get()) / totalOperations.get()) * 100;
                logger.info("Operation Success Rate: " + String.format("%.2f%%", successRate));
            }

            logger.info("Connection Status: " + (connected.get() ? "CONNECTED" : "DISCONNECTED"));
            logger.info("Auto Recovery: " + (autoRecoveryEnabled.get() ? "ENABLED" : "DISABLED"));
            logger.info("===========================================");
        } catch (Exception e) {
            logger.log(Level.FINE, "Error logging performance metrics", e);
        }
    }

    /**
     * connection string masking
     */
    private String maskConnectionString(String connectionString) {
        try {
            return connectionString.replaceAll("://[^@]+@", "://***:***@");
        } catch (Exception e) {
            return "***MASKED***";
        }
    }

    // Enhanced functional interface for database operations
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws Exception;
    }

    // Enhanced getters with safety checks
    public boolean isConnected() {
        return connected.get() && !shuttingDown.get() && mongoClient != null && database != null;
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public boolean isHealthy() {
        return isConnected() && isConnectionHealthy();
    }

    public MongoDatabase getDatabase() {
        connectionLock.readLock().lock();
        try {
            if (!isConnected()) {
                throw new IllegalStateException("Not connected to Enhanced MongoDB");
            }
            return database;
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public MongoClient getMongoClient() {
        connectionLock.readLock().lock();
        try {
            if (!isConnected()) {
                throw new IllegalStateException("Not connected to Enhanced MongoDB");
            }
            return mongoClient;
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * connection statistics
     */
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
                connectionAttempts.get(),
                successfulConnections.get(),
                failedConnections.get(),
                totalOperations.get(),
                failedOperations.get(),
                recoveryAttempts.get(),
                successfulRecoveries.get(),
                lastSuccessfulPing.get(),
                lastConnectionTime.get(),
                connected.get(),
                autoRecoveryEnabled.get()
        );
    }

    /**
     * connection statistics class
     */
    public static class ConnectionStats {
        public final int connectionAttempts;
        public final int successfulConnections;
        public final int failedConnections;
        public final int totalOperations;
        public final int failedOperations;
        public final int recoveryAttempts;
        public final int successfulRecoveries;
        public final long lastSuccessfulPing;
        public final long lastConnectionTime;
        public final boolean connected;
        public final boolean autoRecoveryEnabled;

        public ConnectionStats(int connectionAttempts, int successfulConnections, int failedConnections,
                               int totalOperations, int failedOperations, int recoveryAttempts,
                               int successfulRecoveries, long lastSuccessfulPing, long lastConnectionTime,
                               boolean connected, boolean autoRecoveryEnabled) {
            this.connectionAttempts = connectionAttempts;
            this.successfulConnections = successfulConnections;
            this.failedConnections = failedConnections;
            this.totalOperations = totalOperations;
            this.failedOperations = failedOperations;
            this.recoveryAttempts = recoveryAttempts;
            this.successfulRecoveries = successfulRecoveries;
            this.lastSuccessfulPing = lastSuccessfulPing;
            this.lastConnectionTime = lastConnectionTime;
            this.connected = connected;
            this.autoRecoveryEnabled = autoRecoveryEnabled;
        }

        public double getConnectionSuccessRate() {
            if (connectionAttempts == 0) return 0.0;
            return ((double) successfulConnections / connectionAttempts) * 100;
        }

        public double getOperationSuccessRate() {
            if (totalOperations == 0) return 0.0;
            return ((double) (totalOperations - failedOperations) / totalOperations) * 100;
        }

        public long getTimeSinceLastPing() {
            return System.currentTimeMillis() - lastSuccessfulPing;
        }

        public long getTimeSinceLastConnection() {
            return System.currentTimeMillis() - lastConnectionTime;
        }

        @Override
        public String toString() {
            return String.format("ConnectionStats{attempts=%d, success=%d, failed=%d, ops=%d, " +
                            "failedOps=%d, recovery=%d/%d, connected=%s, autoRecovery=%s, " +
                            "successRate=%.1f%%, opSuccessRate=%.1f%%}",
                    connectionAttempts, successfulConnections, failedConnections, totalOperations,
                    failedOperations, successfulRecoveries, recoveryAttempts, connected, autoRecoveryEnabled,
                    getConnectionSuccessRate(), getOperationSuccessRate());
        }
    }

    /**
     * Reset recovery state (for administrative use)
     */
    public void resetRecoveryState() {
        recoveryAttempts.set(0);
        autoRecoveryEnabled.set(true);
        logger.info(" MongoDB recovery state reset");
    }

    /**
     * Enable/disable auto recovery
     */
    public void setAutoRecoveryEnabled(boolean enabled) {
        autoRecoveryEnabled.set(enabled);
        logger.info(" MongoDB auto-recovery " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Force connection health check
     */
    public boolean forceHealthCheck() {
        try {
            performEnhancedHealthCheck();
            return isConnected() && isConnectionHealthy();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Force health check failed", e);
            return false;
        }
    }
}