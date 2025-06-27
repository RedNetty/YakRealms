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
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * FIXED: Manages MongoDB connection with proper initialization and error handling
 */
public class MongoDBManager {
    private static MongoDBManager instance;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private final String connectionString;
    private final String databaseName;
    private final Logger logger;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final int maxReconnectAttempts;
    private final long reconnectDelay;
    private final Plugin plugin;
    private int healthCheckTaskId = -1;
    private int reconnectTaskId = -1;
    private final int maxConnectionPoolSize;
    private final CodecRegistry codecRegistry;

    /**
     * Private constructor to enforce singleton pattern
     */
    private MongoDBManager(FileConfiguration config, Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Load connection details from config with defaults
        this.connectionString = config.getString("mongodb.connection_string", "mongodb://localhost:27017");
        this.databaseName = config.getString("mongodb.database", "yakserver");
        this.maxReconnectAttempts = config.getInt("mongodb.max_reconnect_attempts", 5);
        this.reconnectDelay = config.getLong("mongodb.reconnect_delay_seconds", 30) * 20L; // Convert to ticks
        this.maxConnectionPoolSize = config.getInt("mongodb.max_connection_pool_size", 100);

        // Set MongoDB logger to only show warnings and errors
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.WARNING);

        // Configure codec registry with POJO support
        this.codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
    }

    /**
     * Initialize the MongoDB manager
     */
    public static MongoDBManager initialize(FileConfiguration config, Plugin plugin) {
        if (instance == null) {
            synchronized (MongoDBManager.class) {
                if (instance == null) {
                    instance = new MongoDBManager(config, plugin);
                }
            }
        }
        return instance;
    }

    /**
     * Get the singleton instance
     */
    public static MongoDBManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MongoDBManager has not been initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * FIXED: Connect to the MongoDB database with proper error handling
     */
    public boolean connect() {
        if (connected.get()) {
            logger.info("Already connected to MongoDB");
            return true;
        }

        if (shuttingDown.get()) {
            logger.warning("Attempted to connect while shutting down, ignoring request");
            return false;
        }

        try {
            logger.info("Attempting to connect to MongoDB at: " + connectionString);

            // Set up connection settings with connection pool and timeouts
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .codecRegistry(codecRegistry)
                    .applyToConnectionPoolSettings(builder ->
                            builder.maxSize(maxConnectionPoolSize)
                                    .minSize(5)
                                    .maxWaitTime(10000, TimeUnit.MILLISECONDS)
                                    .maxConnectionIdleTime(60000, TimeUnit.MILLISECONDS)
                    )
                    .applyToSocketSettings(builder ->
                            builder.connectTimeout(10000, TimeUnit.MILLISECONDS)
                                    .readTimeout(15000, TimeUnit.MILLISECONDS)
                    )
                    .applyToServerSettings(builder ->
                            builder.heartbeatFrequency(20000, TimeUnit.MILLISECONDS)
                    )
                    .build();

            // Create the MongoDB client
            this.mongoClient = MongoClients.create(settings);

            // Get the database (this doesn't actually connect yet)
            this.database = mongoClient.getDatabase(databaseName);

            // Test the connection by listing collections (safer than ping)
            try {
                database.listCollectionNames().first();
                logger.info("Successfully verified connection to MongoDB");
            } catch (Exception e) {
                // Connection failed, but database might not exist yet - try to create it
                logger.info("Database might not exist, attempting to create collection to initialize it");

                // Create a test collection to initialize the database
                database.createCollection("_connection_test");
                database.getCollection("_connection_test").drop();

                logger.info("Database initialized successfully");
            }

            connected.set(true);
            shuttingDown.set(false);
            reconnectAttempts.set(0);

            logger.info("Successfully connected to MongoDB database: " + databaseName);

            // Create required collections if they don't exist
            createRequiredCollections();

            // Start health check task
            startHealthCheck();

            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect to MongoDB: " + e.getMessage(), e);
            connected.set(false);

            // Close any partially opened connection
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                } catch (Exception closeEx) {
                    logger.log(Level.WARNING, "Error closing failed connection", closeEx);
                }
                mongoClient = null;
            }

            // Schedule reconnection attempt if not already scheduled
            scheduleReconnect();

            return false;
        }
    }

    /**
     * Create required collections if they don't exist
     */
    private void createRequiredCollections() {
        try {
            // Get existing collections
            Set<String> existingCollections = new HashSet<>();
            for (String name : database.listCollectionNames()) {
                existingCollections.add(name);
            }

            // Create players collection if it doesn't exist
            if (!existingCollections.contains("players")) {
                database.createCollection("players");
                logger.info("Created 'players' collection");

                // Create indexes
                MongoCollection<Document> playersCollection = database.getCollection("players");
                playersCollection.createIndex(new Document("uuid", 1));
                playersCollection.createIndex(new Document("username", 1));
                logger.info("Created indexes for 'players' collection");
            }

            // Create players_backup collection if it doesn't exist
            if (!existingCollections.contains("players_backup")) {
                database.createCollection("players_backup");
                logger.info("Created 'players_backup' collection");

                // Create indexes
                MongoCollection<Document> backupCollection = database.getCollection("players_backup");
                backupCollection.createIndex(new Document("uuid", 1));
                backupCollection.createIndex(new Document("timestamp", -1));
                logger.info("Created indexes for 'players_backup' collection");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating required collections", e);
        }
    }

    /**
     * Start health check task to monitor database connection
     */
    private void startHealthCheck() {
        if (healthCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(healthCheckTaskId);
        }

        healthCheckTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (shuttingDown.get()) return;

            if (connected.get()) {
                try {
                    // Perform a simple operation to check connection
                    database.listCollectionNames().first();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "MongoDB health check failed: " + e.getMessage(), e);
                    connected.set(false);
                    scheduleReconnect();
                }
            }
        }, 1200L, 1200L).getTaskId(); // Run every minute (1200 ticks)
    }

    /**
     * Schedule a reconnection attempt
     */
    private void scheduleReconnect() {
        if (reconnectTaskId != -1 || shuttingDown.get()) {
            return; // Already reconnecting or shutting down
        }

        int attempts = reconnectAttempts.incrementAndGet();
        if (attempts > maxReconnectAttempts) {
            logger.severe("Maximum reconnection attempts (" + maxReconnectAttempts + ") reached. Giving up...");
            return;
        }

        logger.info("Scheduling MongoDB reconnection attempt " + attempts + "/" + maxReconnectAttempts + " in " +
                (reconnectDelay / 20) + " seconds");

        reconnectTaskId = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            reconnectTaskId = -1;

            if (shuttingDown.get()) return;

            logger.info("Attempting to reconnect to MongoDB (attempt " + attempts + "/" + maxReconnectAttempts + ")");

            if (mongoClient != null) {
                try {
                    mongoClient.close();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error closing MongoDB client during reconnect", e);
                }
                mongoClient = null;
            }

            boolean success = connect();
            if (success) {
                logger.info("Successfully reconnected to MongoDB");
            }
        }, reconnectDelay).getTaskId();
    }

    /**
     * Disconnect from the MongoDB database
     */
    public void disconnect() {
        if (shuttingDown.getAndSet(true)) {
            return; // Already shutting down
        }

        // Cancel scheduled tasks
        if (healthCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(healthCheckTaskId);
            healthCheckTaskId = -1;
        }

        if (reconnectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(reconnectTaskId);
            reconnectTaskId = -1;
        }

        if (mongoClient != null) {
            try {
                // Wait for pending operations to complete (with timeout)
                Thread.sleep(100);

                // Close the client
                mongoClient.close();
                mongoClient = null;
                connected.set(false);
                logger.info("Disconnected from MongoDB");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error while disconnecting from MongoDB", e);
            }
        }
    }

    /**
     * FIXED: Perform a safe operation with proper null checking
     */
    public <T> T performSafeOperation(DatabaseOperation<T> operation) {
        if (shuttingDown.get()) {
            logger.warning("Attempted database operation during shutdown, ignoring request");
            return null;
        }

        if (!isConnected()) {
            logger.warning("Attempted database operation while disconnected, attempting to reconnect");
            if (!connect()) {
                logger.severe("Cannot perform database operation - connection failed");
                return null;
            }
        }

        try {
            return operation.execute();
        } catch (MongoException e) {
            logger.log(Level.WARNING, "MongoDB operation failed: " + e.getMessage(), e);

            // Check if it's a connection issue
            if (e.getMessage() != null && (e.getMessage().contains("connection") || e.getMessage().contains("socket"))) {
                connected.set(false);
                scheduleReconnect();
            }

            return null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during MongoDB operation: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Functional interface for database operations
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws Exception;
    }

    /**
     * Check if the manager is connected to MongoDB
     */
    public boolean isConnected() {
        return connected.get() && !shuttingDown.get() && mongoClient != null && database != null;
    }

    /**
     * Check if the manager is in the process of shutting down
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * FIXED: Get a MongoDB collection with proper error handling
     */
    public MongoCollection<Document> getCollection(String name) {
        if (!isConnected()) {
            logger.severe("Cannot get collection '" + name + "' - not connected to MongoDB");
            // Try to reconnect
            if (!connect()) {
                throw new IllegalStateException("Not connected to MongoDB and reconnection failed");
            }
        }

        if (database == null) {
            throw new IllegalStateException("Database is null");
        }

        try {
            return database.getCollection(name);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting collection: " + name, e);
            throw new RuntimeException("Failed to get collection: " + name, e);
        }
    }

    /**
     * Get a MongoDB collection with a specific document class
     */
    public <T> MongoCollection<T> getCollection(String name, Class<T> documentClass) {
        if (!isConnected()) {
            logger.severe("Cannot get collection '" + name + "' - not connected to MongoDB");
            // Try to reconnect
            if (!connect()) {
                throw new IllegalStateException("Not connected to MongoDB and reconnection failed");
            }
        }

        if (database == null) {
            throw new IllegalStateException("Database is null");
        }

        try {
            return database.getCollection(name, documentClass);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting collection: " + name, e);
            throw new RuntimeException("Failed to get collection: " + name, e);
        }
    }

    /**
     * Get the MongoDB database
     */
    public MongoDatabase getDatabase() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB");
        }
        return database;
    }

    /**
     * Get the MongoDB client
     */
    public MongoClient getMongoClient() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB");
        }
        return mongoClient;
    }
}