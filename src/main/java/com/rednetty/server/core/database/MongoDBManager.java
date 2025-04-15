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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Manages MongoDB connection and provides access to database collections
 * Enhanced with better connection handling, reconnection capabilities, and health checks
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
     *
     * @param config Configuration containing MongoDB connection details
     * @param plugin The plugin instance
     */
    private MongoDBManager(FileConfiguration config, Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Load connection details from config
        this.connectionString = config.getString("mongodb.connection_string", "mongodb://localhost:27017");
        this.databaseName = config.getString("mongodb.database", "yakserver");
        this.maxReconnectAttempts = config.getInt("mongodb.max_reconnect_attempts", 5);
        this.reconnectDelay = config.getLong("mongodb.reconnect_delay_seconds", 30) * 20L; // Convert to ticks
        this.maxConnectionPoolSize = config.getInt("mongodb.max_connection_pool_size", 100);

        // Set MongoDB logger to only show warnings and errors
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.WARNING);

        // Configure codec registry with POJO support once
        this.codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
    }

    /**
     * Initialize the MongoDB manager
     *
     * @param config Configuration containing MongoDB connection details
     * @param plugin The plugin instance
     * @return The MongoDBManager instance
     */
    public static MongoDBManager initialize(FileConfiguration config, Plugin plugin) {
        if (instance == null) {
            instance = new MongoDBManager(config, plugin);
        }
        return instance;
    }

    /**
     * Get the singleton instance
     *
     * @return The MongoDBManager instance
     * @throws IllegalStateException if the manager hasn't been initialized
     */
    public static MongoDBManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MongoDBManager has not been initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Connect to the MongoDB database
     *
     * @return true if the connection was successful
     */
    public boolean connect() {
        if (connected.get()) {
            return true; // Already connected
        }

        if (shuttingDown.get()) {
            logger.warning("Attempted to connect while shutting down, ignoring request");
            return false;
        }

        try {
            // Set up connection settings with connection pool and timeouts
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .codecRegistry(codecRegistry)
                    .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
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

            // Get the database
            this.database = mongoClient.getDatabase(databaseName);

            // Test the connection by executing a simple command
            Document pingResult = database.runCommand(new Document("ping", 1));
            connected.set(true);
            shuttingDown.set(false);
            reconnectAttempts.set(0);

            logger.info("Successfully connected to MongoDB database: " + databaseName);

            // Start health check task
            startHealthCheck();

            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect to MongoDB: " + e.getMessage(), e);
            connected.set(false);

            // Schedule reconnection attempt if not already scheduled
            scheduleReconnect();

            return false;
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
                    // Perform a simple ping to check connection
                    database.runCommand(new Document("ping", 1));
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
     * Perform a safe operation with MongoDB that handles potential connection issues
     *
     * @param operation The database operation to perform
     * @param <T>       The return type of the operation
     * @return The result of the operation or null if it failed
     */
    public <T> T performSafeOperation(DatabaseOperation<T> operation) {
        if (shuttingDown.get()) {
            logger.warning("Attempted database operation during shutdown, ignoring request");
            return null;
        }

        if (!isConnected()) {
            logger.warning("Attempted database operation while disconnected, attempting to reconnect");
            if (!connect()) {
                return null;
            }
        }

        try {
            return operation.execute();
        } catch (MongoException e) {
            logger.log(Level.WARNING, "MongoDB operation failed: " + e.getMessage(), e);

            // Check if it's a connection issue
            if (e.getMessage().contains("connection") || e.getMessage().contains("socket")) {
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
     *
     * @param <T> The return type of the operation
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws Exception;
    }

    /**
     * Check if the manager is connected to MongoDB
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get() && !shuttingDown.get();
    }

    /**
     * Check if the manager is in the process of shutting down
     *
     * @return true if shutting down
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * Get a MongoDB collection
     *
     * @param name The collection name
     * @return The MongoDB collection
     * @throws IllegalStateException if not connected to MongoDB
     */
    public MongoCollection<Document> getCollection(String name) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB");
        }
        return database.getCollection(name);
    }

    /**
     * Get a MongoDB collection with a specific document class
     *
     * @param name          The collection name
     * @param documentClass The class of documents in the collection
     * @param <T>           The document type
     * @return The MongoDB collection
     * @throws IllegalStateException if not connected to MongoDB
     */
    public <T> MongoCollection<T> getCollection(String name, Class<T> documentClass) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB");
        }
        return database.getCollection(name, documentClass);
    }

    /**
     * Get the MongoDB database
     *
     * @return The MongoDB database
     * @throws IllegalStateException if not connected to MongoDB
     */
    public MongoDatabase getDatabase() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB");
        }
        return database;
    }

    /**
     * Get the MongoDB client
     *
     * @return The MongoDB client
     * @throws IllegalStateException if not connected to MongoDB
     */
    public MongoClient getMongoClient() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB");
        }
        return mongoClient;
    }
}