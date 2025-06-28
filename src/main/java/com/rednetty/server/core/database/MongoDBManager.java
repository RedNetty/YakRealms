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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Simplified MongoDB connection manager with reliable connection handling
 */
public class MongoDBManager {
    private static volatile MongoDBManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Connection management
    private volatile MongoClient mongoClient;
    private volatile MongoDatabase database;

    // Configuration
    private final String connectionString;
    private final String databaseName;
    private final int maxConnectionPoolSize;
    private final int minConnectionPoolSize;
    private final long maxConnectionIdleTime;
    private final long maxWaitTime;
    private final long socketTimeout;
    private final long connectTimeout;

    // State tracking
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // Core dependencies
    private final Plugin plugin;
    private final Logger logger;
    private final CodecRegistry codecRegistry;

    private MongoDBManager(FileConfiguration config, Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Load configuration with defaults
        this.connectionString = config.getString("mongodb.connection_string", "mongodb://localhost:27017");
        this.databaseName = config.getString("mongodb.database", "yakrealms");
        this.maxConnectionPoolSize = config.getInt("mongodb.max_connection_pool_size", 50);
        this.minConnectionPoolSize = config.getInt("mongodb.min_connection_pool_size", 5);
        this.maxConnectionIdleTime = config.getLong("mongodb.max_connection_idle_time_ms", 600000);
        this.maxWaitTime = config.getLong("mongodb.max_wait_time_ms", 15000);
        this.socketTimeout = config.getLong("mongodb.socket_timeout_ms", 30000);
        this.connectTimeout = config.getLong("mongodb.connect_timeout_ms", 10000);

        // Configure MongoDB driver logging
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.WARNING);

        // Configure codec registry
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
     * Connect to MongoDB with simplified error handling
     */
    public boolean connect() {
        if (shuttingDown.get()) {
            logger.warning("Cannot connect while shutting down");
            return false;
        }

        if (connected.get() && isConnectionHealthy()) {
            logger.fine("Already connected to MongoDB");
            return true;
        }

        try {
            // Close existing connection if present
            closeConnection();

            logger.info("Connecting to MongoDB: " + maskConnectionString(connectionString));

            // Build connection settings
            MongoClientSettings settings = buildConnectionSettings();

            // Create MongoDB client
            this.mongoClient = MongoClients.create(settings);
            this.database = mongoClient.getDatabase(databaseName);

            // Test connection
            if (!testConnection()) {
                closeConnection();
                return false;
            }

            // Initialize database structure
            initializeDatabaseStructure();

            connected.set(true);
            logger.info("Successfully connected to MongoDB database: " + databaseName);
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect to MongoDB: " + e.getMessage(), e);
            connected.set(false);
            closeConnection();
            return false;
        }
    }

    /**
     * Build MongoDB connection settings
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
                )
                .applyToSocketSettings(builder ->
                        builder.connectTimeout((int) connectTimeout, TimeUnit.MILLISECONDS)
                                .readTimeout((int) socketTimeout, TimeUnit.MILLISECONDS)
                )
                .applyToServerSettings(builder ->
                        builder.heartbeatFrequency(20000, TimeUnit.MILLISECONDS)
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
     * Test MongoDB connection
     */
    private boolean testConnection() {
        try {
            // Simple ping test
            Document pingResult = database.runCommand(new Document("ping", 1));
            if (pingResult == null || !pingResult.containsKey("ok")) {
                logger.warning("MongoDB ping failed - invalid response");
                return false;
            }

            // Test collection access
            database.listCollectionNames().first();

            logger.fine("MongoDB connection test successful");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "MongoDB connection test failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Initialize database collections and indexes
     */
    private void initializeDatabaseStructure() {
        try {
            // Create players collection if it doesn't exist
            try {
                database.createCollection("players");
                logger.info("Created 'players' collection");
            } catch (Exception e) {
                // Collection might already exist
                logger.fine("Players collection already exists or creation failed: " + e.getMessage());
            }

            // Create backup collection if it doesn't exist
            try {
                database.createCollection("players_backup");
                logger.info("Created 'players_backup' collection");
            } catch (Exception e) {
                // Collection might already exist
                logger.fine("Players backup collection already exists or creation failed: " + e.getMessage());
            }

            // Create indexes
            createIndexes();

            logger.info("Database structure initialization completed");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during database structure initialization", e);
        }
    }

    /**
     * Create database indexes
     */
    private void createIndexes() {
        try {
            MongoCollection<Document> playersCollection = database.getCollection("players");

            // Create indexes with error handling
            createIndexSafely(playersCollection, new Document("uuid", 1), "uuid_index");
            createIndexSafely(playersCollection, new Document("username", 1), "username_index");
            createIndexSafely(playersCollection, new Document("last_login", -1), "last_login_index");

            MongoCollection<Document> backupCollection = database.getCollection("players_backup");
            createIndexSafely(backupCollection, new Document("uuid", 1), "uuid_index");
            createIndexSafely(backupCollection, new Document("timestamp", -1), "timestamp_index");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating indexes", e);
        }
    }

    /**
     * Safely create index
     */
    private void createIndexSafely(MongoCollection<Document> collection, Document indexDoc, String indexName) {
        try {
            collection.createIndex(indexDoc);
            logger.fine("Created index: " + indexName);
        } catch (Exception e) {
            logger.fine("Index " + indexName + " creation failed or already exists: " + e.getMessage());
        }
    }

    /**
     * Check if connection is healthy
     */
    private boolean isConnectionHealthy() {
        if (!connected.get() || mongoClient == null || database == null) {
            return false;
        }

        try {
            Document result = database.runCommand(new Document("ping", 1));
            return result != null && result.get("ok", Number.class).intValue() == 1;
        } catch (Exception e) {
            logger.fine("Connection health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform safe database operation with retry logic
     */
    public <T> T performSafeOperation(DatabaseOperation<T> operation) {
        return performSafeOperation(operation, 2); // Default 2 retries
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
                    logger.severe("Cannot perform database operation - connection failed");
                    return null;
                }
                continue;
            }

            try {
                T result = operation.execute();
                if (attempt > 1) {
                    logger.fine("Database operation succeeded on attempt " + attempt);
                }
                return result;

            } catch (MongoException e) {
                lastException = e;
                logger.log(Level.WARNING, "MongoDB operation failed (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage(), e);

                // Check if this is a connection error
                if (isConnectionError(e)) {
                    connected.set(false);
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(1000 * attempt); // Brief delay before retry
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
            }
        }

        logger.severe("Database operation failed after " + maxRetries + " attempts. Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown"));
        return null;
    }

    /**
     * Check if exception indicates connection error
     */
    private boolean isConnectionError(Exception e) {
        if (e instanceof MongoException) {
            String message = e.getMessage().toLowerCase();
            return message.contains("connection") ||
                    message.contains("socket") ||
                    message.contains("timeout") ||
                    message.contains("network") ||
                    e instanceof MongoSocketException ||
                    e instanceof MongoTimeoutException;
        }
        return false;
    }

    /**
     * Ensure connection is available
     */
    private boolean ensureConnection() {
        if (connected.get() && isConnectionHealthy()) {
            return true;
        }
        return connect();
    }

    /**
     * Get MongoDB collection
     */
    public MongoCollection<Document> getCollection(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB. Collection: " + name);
        }

        try {
            return database.getCollection(name);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting collection: " + name, e);
            throw new RuntimeException("Failed to get collection: " + name, e);
        }
    }

    /**
     * Get typed MongoDB collection
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

        try {
            return database.getCollection(name, documentClass);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting typed collection: " + name, e);
            throw new RuntimeException("Failed to get typed collection: " + name, e);
        }
    }

    /**
     * Disconnect from MongoDB
     */
    public void disconnect() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return; // Already shutting down
        }

        logger.info("Disconnecting from MongoDB...");
        closeConnection();
        connected.set(false);
        logger.info("MongoDB disconnection completed");
    }

    /**
     * Close MongoDB connection
     */
    private void closeConnection() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
                logger.fine("MongoDB client closed successfully");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing MongoDB client", e);
            } finally {
                mongoClient = null;
                database = null;
            }
        }
    }

    /**
     * Mask sensitive information in connection string
     */
    private String maskConnectionString(String connectionString) {
        return connectionString.replaceAll("://[^@]+@", "://***:***@");
    }

    // Functional interface for database operations
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws Exception;
    }

    // Getters
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
}