package com.rednetty.server.core.database;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ClusterConnectionMode;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Manages MongoDB connection and provides access to database collections.
 * Enhanced with robust connection handling, automatic reconnection with exponential backoff,
 * health checks, configurable write/read concerns, and improved error handling.
 * Designed for use within a Bukkit plugin environment.
 */
public class MongoDBManager {

    // --- Constants ---
    private static final String MONGODB_DRIVER_LOGGER_NAME = "org.mongodb.driver";
    private static final long DEFAULT_RECONNECT_DELAY_SECONDS = 15; // Initial reconnect delay
    private static final long MAX_RECONNECT_DELAY_SECONDS = 300; // Max delay (5 minutes)
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 10;
    private static final int DEFAULT_MAX_CONNECTION_POOL_SIZE = 100;
    private static final int DEFAULT_MIN_CONNECTION_POOL_SIZE = 5;
    private static final long DEFAULT_MAX_WAIT_TIME_MS = 10000;
    private static final long DEFAULT_MAX_CONNECTION_IDLE_TIME_MS = 60000;
    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final long DEFAULT_READ_TIMEOUT_MS = 15000;
    private static final long DEFAULT_HEARTBEAT_FREQUENCY_MS = 20000;
    private static final long HEALTH_CHECK_INTERVAL_TICKS = 1200L; // 1 minute

    // --- Singleton Instance ---
    private static volatile MongoDBManager instance; // volatile for thread safety
    private static final Object lock = new Object(); // Lock for synchronized initialization

    // --- Instance Variables ---
    private final Plugin plugin;
    private final Logger logger;
    private final String connectionString;
    private final String databaseName;
    private final int maxReconnectAttempts;
    private final long initialReconnectDelayTicks;
    private final long maxReconnectDelayTicks;
    private final int maxConnectionPoolSize;
    private final int minConnectionPoolSize;
    private final long maxWaitTimeMs;
    private final long maxConnectionIdleTimeMs;
    private final long connectTimeoutMs;
    private final long readTimeoutMs;
    private final long heartbeatFrequencyMs;
    private final WriteConcern defaultWriteConcern;
    private final ReadConcern defaultReadConcern;
    private final CodecRegistry codecRegistry;

    private MongoClient mongoClient;
    private MongoDatabase database;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false); // Flag to prevent multiple reconnect tasks
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private BukkitTask healthCheckTask;
    private BukkitTask reconnectTask;


    /**
     * Private constructor to enforce singleton pattern.
     * Loads configuration and sets up initial state.
     *
     * @param config Configuration containing MongoDB connection details.
     * @param plugin The Bukkit plugin instance.
     */
    private MongoDBManager(FileConfiguration config, Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.logger = plugin.getLogger();

        // --- Load Configuration ---
        this.connectionString = Objects.requireNonNull(
                config.getString("mongodb.connection_string"),
                "mongodb.connection_string cannot be null in config.yml"
        );
        this.databaseName = Objects.requireNonNull(
                config.getString("mongodb.database"),
                "mongodb.database cannot be null in config.yml"
        );
        this.maxReconnectAttempts = config.getInt("mongodb.max_reconnect_attempts", DEFAULT_MAX_RECONNECT_ATTEMPTS);
        this.initialReconnectDelayTicks = config.getLong("mongodb.reconnect_delay_seconds", DEFAULT_RECONNECT_DELAY_SECONDS) * 20L;
        this.maxReconnectDelayTicks = config.getLong("mongodb.max_reconnect_delay_seconds", MAX_RECONNECT_DELAY_SECONDS) * 20L;

        // Connection Pool Settings
        this.maxConnectionPoolSize = config.getInt("mongodb.connection_pool.max_size", DEFAULT_MAX_CONNECTION_POOL_SIZE);
        this.minConnectionPoolSize = config.getInt("mongodb.connection_pool.min_size", DEFAULT_MIN_CONNECTION_POOL_SIZE);
        this.maxWaitTimeMs = config.getLong("mongodb.connection_pool.max_wait_time_ms", DEFAULT_MAX_WAIT_TIME_MS);
        this.maxConnectionIdleTimeMs = config.getLong("mongodb.connection_pool.max_idle_time_ms", DEFAULT_MAX_CONNECTION_IDLE_TIME_MS);

        // Socket/Server Settings
        this.connectTimeoutMs = config.getLong("mongodb.socket.connect_timeout_ms", DEFAULT_CONNECT_TIMEOUT_MS);
        this.readTimeoutMs = config.getLong("mongodb.socket.read_timeout_ms", DEFAULT_READ_TIMEOUT_MS);
        this.heartbeatFrequencyMs = config.getLong("mongodb.server.heartbeat_frequency_ms", DEFAULT_HEARTBEAT_FREQUENCY_MS);

        // Data Consistency Settings
        this.defaultWriteConcern = parseWriteConcern(config.getString("mongodb.write_concern", "W1"));
        this.defaultReadConcern = parseReadConcern(config.getString("mongodb.read_concern", "LOCAL"));

        // --- Configure MongoDB Driver Logging ---
        // Set MongoDB driver logger level (e.g., WARNING to reduce noise)
        Logger mongoLogger = Logger.getLogger(MONGODB_DRIVER_LOGGER_NAME);
        mongoLogger.setLevel(Level.WARNING); // Adjust level as needed (INFO, SEVERE, etc.)

        // --- Configure Codec Registry ---
        // Includes default codecs and enables automatic POJO (Plain Old Java Object) mapping.
        // This allows storing and retrieving Java objects directly.
        this.codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );

        logger.info("MongoDBManager configured for database '" + databaseName + "'.");
    }

    /**
     * Initializes the MongoDBManager singleton instance.
     * This method should be called once during plugin startup (e.g., in onEnable).
     * It's thread-safe using double-checked locking.
     *
     * @param config Configuration containing MongoDB connection details.
     * @param plugin The Bukkit plugin instance.
     * @return The initialized MongoDBManager instance.
     * @throws IllegalStateException if initialization fails.
     */
    public static MongoDBManager initialize(FileConfiguration config, Plugin plugin) {
        if (instance == null) { // First check (no lock)
            synchronized (lock) { // Synchronize on the lock object
                if (instance == null) { // Second check (within lock)
                    try {
                        instance = new MongoDBManager(config, plugin);
                        instance.connect(); // Attempt initial connection
                    } catch (Exception e) {
                        // Log the initialization error clearly
                        plugin.getLogger().log(Level.SEVERE, "Failed to initialize MongoDBManager", e);
                        // Optionally re-throw or handle differently depending on whether DB is critical
                        throw new IllegalStateException("MongoDBManager initialization failed", e);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Gets the singleton instance of MongoDBManager.
     *
     * @return The MongoDBManager instance.
     * @throws IllegalStateException if the manager hasn't been initialized via initialize().
     */
    public static MongoDBManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MongoDBManager has not been initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Attempts to establish a connection to the MongoDB database.
     * Configures the MongoClient with specified settings (timeouts, pool size, etc.).
     * Performs an initial ping to verify the connection.
     * Starts the health check task upon successful connection.
     *
     * @return true if connection is successful, false otherwise.
     */
    public synchronized boolean connect() {
        if (connected.get() || shuttingDown.get()) {
            logger.fine("Connect called but already connected or shutting down.");
            return connected.get();
        }

        logger.info("Attempting to connect to MongoDB at " + connectionString.replaceAll("mongodb://[^@]+@", "mongodb://****:****@") + "..."); // Mask credentials

        // Close existing client if present (e.g., during reconnection)
        closeClientQuietly();

        try {
            // --- Configure MongoClientSettings ---
            MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .codecRegistry(codecRegistry)
                    // Specify the Stable API version for compatibility guarantees
                    .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
                    // Connection Pool settings
                    .applyToConnectionPoolSettings(builder ->
                            builder.maxSize(maxConnectionPoolSize)
                                    .minSize(minConnectionPoolSize)
                                    .maxWaitTime(maxWaitTimeMs, TimeUnit.MILLISECONDS)
                                    .maxConnectionIdleTime(maxConnectionIdleTimeMs, TimeUnit.MILLISECONDS)
                    )
                    // Socket settings (timeouts for establishing and using connections)
                    .applyToSocketSettings(builder ->
                            builder.connectTimeout((int) connectTimeoutMs, TimeUnit.MILLISECONDS)
                                    .readTimeout((int) readTimeoutMs, TimeUnit.MILLISECONDS)
                    )
                    // Server settings (heartbeat for monitoring server status)
                    .applyToServerSettings(builder ->
                            builder.heartbeatFrequency(heartbeatFrequencyMs, TimeUnit.MILLISECONDS)
                    )
                    // Default consistency levels
                    .writeConcern(defaultWriteConcern)
                    .readConcern(defaultReadConcern);

            // For replica sets, ensure we connect to the cluster, not just a single node initially
            // This helps with failover detection.
            if (connectionString.contains(",")) { // Basic check if it looks like a replica set URI
                settingsBuilder.applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(15, TimeUnit.SECONDS) // Timeout for finding a suitable server
                                .mode(ClusterConnectionMode.MULTIPLE) // Connect to multiple seeds if provided
                );
            }


            // --- Create MongoClient ---
            this.mongoClient = MongoClients.create(settingsBuilder.build());

            // --- Get Database & Verify Connection ---
            // Getting the database doesn't confirm connection, need an operation.
            this.database = mongoClient.getDatabase(databaseName)
                    .withCodecRegistry(codecRegistry) // Ensure database uses the registry
                    .withWriteConcern(defaultWriteConcern) // Apply defaults
                    .withReadConcern(defaultReadConcern);

            // Ping the database to confirm connectivity and authentication
            database.runCommand(new Document("ping", 1)); // Throws exception on failure

            // --- Connection Successful ---
            connected.set(true);
            shuttingDown.set(false);
            reconnecting.set(false); // Ensure reconnect flag is reset
            reconnectAttempts.set(0); // Reset reconnect attempts on successful connection
            cancelTask(reconnectTask); // Cancel any pending reconnect task
            reconnectTask = null;

            logger.info("Successfully connected to MongoDB database: " + databaseName);
            logger.info("Write Concern: " + defaultWriteConcern.asDocument().toJson() +
                    ", Read Concern: " + defaultReadConcern.asDocument().toJson());

            // Start periodic health checks
            startHealthCheck();

            return true;

        } catch (MongoConfigurationException e) {
            logger.log(Level.SEVERE, "MongoDB configuration error: " + e.getMessage(), e);
            connected.set(false);
            // Configuration errors are usually fatal, don't attempt reconnect
            return false;
        } catch (MongoSecurityException e) {
            logger.log(Level.SEVERE, "MongoDB authentication failed: " + e.getMessage(), e);
            connected.set(false);
            // Authentication errors might be temporary (e.g., key rotation) or permanent.
            // Schedule reconnect, but log severity.
            scheduleReconnect();
            return false;
        } catch (MongoSocketOpenException | MongoSocketReadException | MongoTimeoutException e) {
            logger.log(Level.WARNING, "MongoDB connection/network error: " + e.getMessage());
            connected.set(false);
            scheduleReconnect(); // Network issues warrant reconnection attempts
            return false;
        } catch (Exception e) { // Catch broader exceptions during initial connect
            logger.log(Level.SEVERE, "Failed to connect to MongoDB: " + e.getMessage(), e);
            connected.set(false);
            scheduleReconnect(); // Attempt to reconnect for unexpected issues
            return false;
        }
    }

    /**
     * Starts a periodic asynchronous task to check the health of the MongoDB connection.
     * If the health check fails, it triggers the reconnection process.
     */
    private void startHealthCheck() {
        cancelTask(healthCheckTask); // Cancel existing task if any

        healthCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!connected.get() || shuttingDown.get() || reconnecting.get()) {
                return; // Don't health check if disconnected, shutting down, or already trying to reconnect
            }

            try {
                // Perform a lightweight operation (ping) to check connection status
                database.runCommand(new Document("ping", 1));
                logger.fine("MongoDB health check successful."); // Use fine for less log spam
            } catch (Exception e) {
                // Log specific types of exceptions differently if needed
                logger.log(Level.WARNING, "MongoDB health check failed: " + e.getMessage() + ". Initiating reconnect sequence.");
                connected.set(false);
                scheduleReconnect(); // Connection lost, schedule reconnect
            }
        }, HEALTH_CHECK_INTERVAL_TICKS, HEALTH_CHECK_INTERVAL_TICKS); // Run periodically

        logger.info("MongoDB health check task started.");
    }

    /**
     * Schedules an asynchronous task to attempt reconnection to MongoDB.
     * Uses exponential backoff for delays between attempts.
     */
    private void scheduleReconnect() {
        // Only schedule if not already shutting down, connected, or actively reconnecting
        if (shuttingDown.get() || connected.get() || !reconnecting.compareAndSet(false, true)) {
            logger.fine("Reconnect scheduling skipped (shutting down, connected, or already reconnecting).");
            return;
        }

        cancelTask(reconnectTask); // Cancel any existing reconnect task

        int attempt = reconnectAttempts.incrementAndGet();

        if (attempt > maxReconnectAttempts) {
            logger.severe("Maximum MongoDB reconnection attempts (" + maxReconnectAttempts + ") reached. Giving up. Manual intervention may be required.");
            reconnecting.set(false); // Stop trying
            return;
        }

        // Calculate delay with exponential backoff (capped)
        long delayTicks = (long) (initialReconnectDelayTicks * Math.pow(2, Math.min(attempt - 1, 10))); // Limit exponent to prevent overflow/extreme delays
        delayTicks = Math.min(delayTicks, maxReconnectDelayTicks); // Cap delay at max
        long delaySeconds = delayTicks / 20L;

        logger.info("Scheduling MongoDB reconnection attempt " + attempt + "/" + maxReconnectAttempts +
                " in approximately " + delaySeconds + " seconds...");

        reconnectTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            reconnecting.set(false); // Allow scheduling next attempt if this one fails

            if (shuttingDown.get() || connected.get()) {
                logger.info("Reconnect attempt aborted (shutting down or already connected).");
                return; // Abort if state changed
            }

            logger.info("Attempting to reconnect to MongoDB (attempt " + attempt + "/" + maxReconnectAttempts + ")");
            boolean success = connect(); // Try to connect again

            if (success) {
                logger.info("Successfully reconnected to MongoDB.");
                // connect() already resets attempts and sets flags
            } else {
                logger.warning("MongoDB reconnection attempt " + attempt + " failed.");
                // If connect() failed due to non-network issues, it might not schedule the next reconnect.
                // Ensure the next attempt is scheduled if applicable.
                if (!shuttingDown.get() && !connected.get()) {
                    scheduleReconnect(); // Schedule the *next* attempt
                }
            }
        }, delayTicks);
    }

    /**
     * Disconnects from the MongoDB database gracefully.
     * Cancels pending tasks (health check, reconnect) and closes the MongoClient.
     */
    public void disconnect() {
        if (!shuttingDown.compareAndSet(false, true)) {
            logger.info("Disconnect called but already shutting down.");
            return; // Already shutting down or shutdown complete
        }

        logger.info("Disconnecting from MongoDB...");

        // Cancel scheduled tasks
        cancelTask(healthCheckTask);
        healthCheckTask = null;
        cancelTask(reconnectTask);
        reconnectTask = null;

        // Set flags
        connected.set(false);
        reconnecting.set(false);

        // Close the client (this handles pool shutdown etc.)
        closeClientQuietly();

        logger.info("MongoDB connection closed.");
    }

    /**
     * Closes the MongoClient instance, logging any errors quietly.
     */
    private void closeClientQuietly() {
        if (this.mongoClient != null) {
            try {
                this.mongoClient.close();
                logger.fine("MongoClient closed successfully.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error while closing MongoClient", e);
            } finally {
                this.mongoClient = null; // Ensure it's nullified
                this.database = null; // Database object becomes invalid
            }
        }
    }

    /**
     * Safely cancels a BukkitTask if it's running.
     *
     * @param task The BukkitTask to cancel.
     */
    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error cancelling Bukkit task", e);
            }
        }
    }

    /**
     * Performs a database operation safely, handling potential connection issues
     * and wrapping exceptions. Retries connection once if disconnected.
     *
     * @param operation The database operation to perform (provided as a lambda or method reference).
     * @param <T>       The return type of the operation.
     * @return An Optional containing the result if successful, Optional.empty() otherwise.
     * @throws MongoOperationException if a MongoDB-specific error occurs during the operation (wraps original exception).
     * @throws RuntimeException        for other unexpected errors during the operation.
     */
    public <T> Optional<T> performSafeOperation(DatabaseOperation<T> operation) {
        if (shuttingDown.get()) {
            logger.warning("Attempted database operation during shutdown. Operation cancelled.");
            return Optional.empty();
        }

        if (!isConnected()) {
            logger.warning("Attempted database operation while disconnected. Attempting immediate reconnect...");
            // Try a single, immediate, synchronized reconnect attempt
            if (!connect()) {
                logger.severe("Immediate reconnect failed. Database operation cannot proceed.");
                return Optional.empty(); // Reconnect failed, cannot proceed
            }
            // If connect() succeeded, isConnected() should now be true
            if (!isConnected()) {
                logger.severe("Reconnect attempt reported success, but manager still not connected. Operation cancelled.");
                return Optional.empty();
            }
        }

        // At this point, we should be connected.
        try {
            T result = operation.execute(this.database); // Pass the database object to the operation
            return Optional.ofNullable(result); // Wrap result (even if null) in Optional
        } catch (MongoSocketException | MongoTimeoutException | MongoNotPrimaryException | MongoWriteConcernException e) {
            // These exceptions often indicate connection or cluster state issues.
            logger.log(Level.WARNING, "MongoDB network/cluster error during operation: " + e.getMessage() + ". Triggering connection check.");
            connected.set(false); // Assume connection is compromised
            scheduleReconnect(); // Trigger background reconnection attempts
            throw new MongoOperationException("Network or cluster error during MongoDB operation", e);
        } catch (MongoCommandException e) {
            // Errors related to the command itself (permissions, syntax, etc.)
            logger.log(Level.SEVERE, "MongoDB command failed: " + e.getErrorMessage() + " (Code: " + e.getErrorCode() + ")");
            throw new MongoOperationException("Command execution failed", e);
        } catch (MongoException e) {
            // Catch other MongoDB-specific exceptions
            logger.log(Level.SEVERE, "A MongoDB error occurred during operation: " + e.getMessage(), e);
            // Check if it's potentially a connection issue based on message (less reliable)
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || e.getMessage().toLowerCase().contains("socket"))) {
                connected.set(false);
                scheduleReconnect();
            }
            throw new MongoOperationException("Generic MongoDB error during operation", e);
        } catch (Exception e) {
            // Catch unexpected non-Mongo exceptions
            logger.log(Level.SEVERE, "Unexpected error during database operation: " + e.getMessage(), e);
            throw new RuntimeException("Unexpected error during MongoDB operation", e); // Re-throw as unchecked
        }
    }

    /**
     * Functional interface for database operations to be executed via performSafeOperation.
     *
     * @param <T> The return type of the operation.
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        /**
         * Executes the database operation.
         *
         * @param database The connected MongoDatabase instance.
         * @return The result of the operation.
         * @throws Exception Allows any exception to be thrown, which performSafeOperation will handle.
         */
        T execute(MongoDatabase database) throws Exception;
    }

    // --- Getters ---

    /**
     * Checks if the manager is currently connected to MongoDB and not shutting down.
     *
     * @return true if connected and operational, false otherwise.
     */
    public boolean isConnected() {
        // Ensure MongoClient isn't null AND the connected flag is true AND not shutting down.
        // A simple ping might be too slow for frequent checks, rely on the flag and health checks.
        return this.mongoClient != null && connected.get() && !shuttingDown.get();
    }

    /**
     * Checks if the manager is in the process of shutting down.
     *
     * @return true if shutting down, false otherwise.
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * Gets a MongoDB collection with default Document class and default read/write concerns.
     * Use performSafeOperation for accessing data within the collection.
     *
     * @param name The name of the collection.
     * @return The MongoCollection instance.
     * @throws IllegalStateException if not currently connected to MongoDB.
     */
    public MongoCollection<Document> getCollection(String name) {
        return getCollection(name, Document.class);
    }

    /**
     * Gets a MongoDB collection with a specific document class (POJO) and default read/write concerns.
     * Use performSafeOperation for accessing data within the collection.
     *
     * @param name          The name of the collection.
     * @param documentClass The Class type of the documents in the collection (e.g., PlayerData.class).
     * @param <T>           The document type.
     * @return The MongoCollection instance typed to the document class.
     * @throws IllegalStateException if not currently connected to MongoDB.
     */
    public <T> MongoCollection<T> getCollection(String name, Class<T> documentClass) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB. Cannot get collection '" + name + "'.");
        }
        // Return collection with the manager's default settings
        return database.getCollection(name, documentClass);
    }

    /**
     * Gets the underlying MongoDatabase object.
     * Note: Direct use bypasses the safe operation wrapper. Prefer using
     * `performSafeOperation` or `getCollection` where possible.
     *
     * @return The MongoDatabase instance.
     * @throws IllegalStateException if not currently connected to MongoDB.
     */
    public MongoDatabase getDatabase() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB.");
        }
        return database;
    }

    /**
     * Gets the underlying MongoClient object.
     * Note: Direct use bypasses the safe operation wrapper and connection checks.
     * Use with caution, primarily for advanced configuration or monitoring.
     *
     * @return The MongoClient instance.
     * @throws IllegalStateException if not currently connected to MongoDB.
     */
    public MongoClient getMongoClient() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MongoDB.");
        }
        return mongoClient;
    }

    // --- Utility Methods ---

    /**
     * Parses a WriteConcern string name (e.g., "W1", "MAJORITY") into a WriteConcern object.
     * Defaults to W1 if parsing fails or name is invalid.
     *
     * @param name The name of the write concern.
     * @return The corresponding WriteConcern object.
     */
    private WriteConcern parseWriteConcern(String name) {
        try {
            if (name == null || name.trim().isEmpty()) return WriteConcern.W1;
            WriteConcern wc = WriteConcern.valueOf(name.toUpperCase());
            return (wc != null) ? wc : WriteConcern.W1;
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid WriteConcern name '" + name + "' in config. Defaulting to W1.");
            return WriteConcern.W1;
        }
    }

    /**
     * Parses a ReadConcern string name (e.g., "LOCAL", "MAJORITY") into a ReadConcern object.
     * Defaults to LOCAL if parsing fails or name is invalid.
     *
     * @param name The name of the read concern.
     * @return The corresponding ReadConcern object.
     */
    private ReadConcern parseReadConcern(String name) {
        try {
            if (name == null || name.trim().isEmpty()) return ReadConcern.LOCAL;
            ReadConcernLevel level = ReadConcernLevel.fromString(name.toUpperCase());
            return (level != null) ? new ReadConcern(level) : ReadConcern.DEFAULT; // DEFAULT is usually LOCAL
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid ReadConcern name '" + name + "' in config. Defaulting to LOCAL.");
            return ReadConcern.LOCAL;
        }
    }

    /**
     * Custom exception class for errors occurring during safe database operations.
     */
    public static class MongoOperationException extends RuntimeException {
        public MongoOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
