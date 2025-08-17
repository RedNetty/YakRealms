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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Enhanced MongoDB Manager with bulletproof connection state management
 * Fixes: "state should be: open" errors and connection stability issues
 *
 * Key Improvements:
 * - Rigorous connection state validation before every operation
 * - Enhanced retry logic with exponential backoff
 * - Proper shutdown coordination to prevent race conditions
 * - Connection health monitoring with automatic recovery
 * - Operation queuing during connection recovery
 */
public class MongoDBManager {
    private static volatile MongoDBManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Enhanced connection management with state validation
    private volatile MongoClient mongoClient;
    private volatile MongoDatabase database;
    private final ReentrantReadWriteLock connectionLock = new ReentrantReadWriteLock();

    // Operation queue for connection recovery periods
    private final BlockingQueue<Runnable> operationQueue = new LinkedBlockingQueue<>();
    private final ExecutorService operationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MongoDB-Operations");
        thread.setDaemon(true);
        return thread;
    });

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

    // Enhanced state tracking with atomic operations
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean healthCheckActive = new AtomicBoolean(false);
    private final AtomicBoolean autoRecoveryEnabled = new AtomicBoolean(true);
    private final AtomicBoolean connectionValidated = new AtomicBoolean(false);

    // Enhanced performance tracking
    private final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulConnections = new AtomicInteger(0);
    private final AtomicInteger failedConnections = new AtomicInteger(0);
    private final AtomicInteger totalOperations = new AtomicInteger(0);
    private final AtomicInteger failedOperations = new AtomicInteger(0);
    private final AtomicInteger recoveryAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulRecoveries = new AtomicInteger(0);
    private final AtomicInteger stateValidationFailures = new AtomicInteger(0);
    private final AtomicInteger queuedOperations = new AtomicInteger(0);
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
    private BukkitTask operationProcessorTask;
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
        this.maxConnectionPoolSize = config.getInt("mongodb.max_connection_pool_size", 25); // Reduced from 50
        this.minConnectionPoolSize = config.getInt("mongodb.min_connection_pool_size", 2);  // Reduced from 5
        this.maxConnectionIdleTime = config.getLong("mongodb.max_connection_idle_time_ms", 300000); // Reduced from 600000
        this.maxWaitTime = config.getLong("mongodb.max_wait_time_ms", 10000); // Reduced from 15000
        this.socketTimeout = config.getLong("mongodb.socket_timeout_ms", 15000); // Reduced from 30000
        this.connectTimeout = config.getLong("mongodb.connect_timeout_ms", 5000); // Reduced from 10000
        this.heartbeatFrequency = config.getLong("mongodb.heartbeat_frequency_ms", 5000); // Reduced from 10000
        this.serverSelectionTimeout = config.getLong("mongodb.server_selection_timeout_ms", 10000); // Reduced from 30000
        this.enableRetryWrites = config.getBoolean("mongodb.enable_retry_writes", true);
        this.enableRetryReads = config.getBoolean("mongodb.enable_retry_reads", true);

        // Enhanced monitoring configuration
        this.healthCheckInterval = config.getLong("mongodb.health_check_interval_ms", 15000); // Reduced from 30000
        this.connectionMonitorInterval = config.getLong("mongodb.connection_monitor_interval_ms", 5000);
        this.maxRecoveryAttempts = config.getInt("mongodb.max_recovery_attempts", 3); // Reduced from 5
        this.recoveryDelay = config.getLong("mongodb.recovery_delay_ms", 2000); // Reduced from 5000

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

        // Start operation processor
        startOperationProcessor();

        logger.info("✅ Enhanced MongoDBManager initialized with state validation");
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
            throw new IllegalStateException("✗ MongoDBManager has not been initialized. Call initialize() first.");
        }
        return result;
    }

    /**
     * Start operation processor for queued operations during recovery
     */
    private void startOperationProcessor() {
        operationProcessorTask = new BukkitRunnable() {
            @Override
            public void run() {
                processQueuedOperations();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L); // Every second
    }

    /**
     * Process queued operations when connection is healthy
     */
    private void processQueuedOperations() {
        if (!isConnectionHealthyAndValidated()) {
            return;
        }

        int processedCount = 0;
        while (!operationQueue.isEmpty() && isConnectionHealthyAndValidated() && processedCount < 10) {
            try {
                Runnable operation = operationQueue.poll(100, TimeUnit.MILLISECONDS);
                if (operation != null) {
                    operationExecutor.submit(operation);
                    processedCount++;
                    queuedOperations.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing queued operation", e);
            }
        }

        if (processedCount > 0) {
            logger.info("Processed " + processedCount + " queued operations");
        }
    }

    /**
     * Enhanced connection with comprehensive error handling and retry logic
     */
    public boolean connect() {
        connectionLock.writeLock().lock();
        try {
            if (shuttingDown.get()) {
                logger.warning("Cannot connect while shutting down");
                return false;
            }

            connectionAttempts.incrementAndGet();

            if (connected.get() && isConnectionHealthyAndValidated()) {
                logger.fine("Already connected to Enhanced MongoDB with validated state");
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

            // Enhanced connection testing with state validation
            if (!performEnhancedConnectionTest()) {
                logger.severe("✗ Enhanced connection test failed");
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
            connectionValidated.set(true);
            successfulConnections.incrementAndGet();
            lastConnectionTime.set(System.currentTimeMillis());
            lastSuccessfulPing.set(System.currentTimeMillis());

            logger.info("✅ Successfully connected to Enhanced MongoDB with state validation: " + databaseName);
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error during enhanced MongoDB connection: " + e.getMessage(), e);
            connected.set(false);
            connectionValidated.set(false);
            failedConnections.incrementAndGet();
            closeConnectionInternal();
            return false;
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    /**
     * Enhanced connection settings with comprehensive configuration
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
                                    .minHeartbeatFrequency(Math.min(heartbeatFrequency / 2, 2500), TimeUnit.MILLISECONDS)
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
     * Enhanced connection testing with comprehensive validation
     */
    private boolean performEnhancedConnectionTest() {
        try {
            logger.fine("Performing enhanced MongoDB connection test with state validation...");

            // Test 1: Connection state validation
            if (!validateConnectionState()) {
                logger.warning("✗ MongoDB connection state validation failed");
                return false;
            }

            // Test 2: Basic ping with state check
            Document pingResult = database.runCommand(new Document("ping", 1));
            if (pingResult == null || !pingResult.containsKey("ok")) {
                logger.warning("✗ MongoDB ping failed - invalid response");
                return false;
            }

            // Test 3: Connection state validation after ping
            if (!validateConnectionState()) {
                logger.warning("✗ MongoDB connection state became invalid after ping");
                return false;
            }

            // Test 4: Collection access with state validation
            try {
                database.listCollectionNames().first();
                if (!validateConnectionState()) {
                    logger.warning("✗ MongoDB connection state became invalid after collection access");
                    return false;
                }
            } catch (Exception e) {
                logger.warning("✗ MongoDB collection access test failed: " + e.getMessage());
                return false;
            }

            // Test 5: Write operation test with state validation
            try {
                String testCollectionName = "connection_test";
                MongoCollection<Document> testCollection = database.getCollection(testCollectionName);

                if (!validateConnectionState()) {
                    logger.warning("✗ MongoDB connection state invalid before write test");
                    return false;
                }

                Document testDoc = new Document("test", "connection_verification")
                        .append("timestamp", System.currentTimeMillis());
                testCollection.insertOne(testDoc);

                if (!validateConnectionState()) {
                    logger.warning("✗ MongoDB connection state became invalid after insert");
                    return false;
                }

                testCollection.deleteOne(new Document("test", "connection_verification"));

                if (!validateConnectionState()) {
                    logger.warning("✗ MongoDB connection state became invalid after delete");
                    return false;
                }

                logger.fine("✅ Enhanced write operation test successful with state validation");
            } catch (Exception e) {
                logger.warning("✗ Enhanced write operation test failed: " + e.getMessage());
                // Don't fail the connection for write test failure - might be permissions
            }

            // Test 6: Final state validation
            if (!validateConnectionState()) {
                logger.warning("✗ MongoDB connection state invalid at end of tests");
                return false;
            }

            logger.fine("✅ Enhanced MongoDB connection test completed successfully with state validation");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "✗ Enhanced MongoDB connection test failed", e);
            return false;
        }
    }

    /**
     * Critical: Validate MongoDB connection state before operations
     */
    private boolean validateConnectionState() {
        try {
            if (mongoClient == null || database == null) {
                logger.fine("MongoDB client or database is null");
                stateValidationFailures.incrementAndGet();
                return false;
            }

            // Check if connection is in valid state
            // This is the key fix for "state should be: open" errors
            try {
                // Use a quick cluster description check
                mongoClient.getClusterDescription();
                return true;
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("state should be: open")) {
                    logger.warning("✗ MongoDB connection state validation failed: " + e.getMessage());
                    stateValidationFailures.incrementAndGet();
                    connected.set(false);
                    connectionValidated.set(false);
                    return false;
                }
                throw e; // Re-throw if it's a different IllegalStateException
            }

        } catch (Exception e) {
            logger.fine("Error validating MongoDB connection state: " + e.getMessage());
            stateValidationFailures.incrementAndGet();
            return false;
        }
    }

    /**
     * Enhanced database structure initialization
     */
    private boolean initializeEnhancedDatabaseStructure() {
        try {
            logger.info("Initializing enhanced database structure...");

            // Validate state before initialization
            if (!validateConnectionState()) {
                logger.warning("Cannot initialize database structure - invalid connection state");
                return false;
            }

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

            logger.info("✅ Enhanced database structure initialization completed");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during enhanced database structure initialization", e);
            return false;
        }
    }

    /**
     * Safe collection creation with better error handling
     */
    private boolean createCollectionSafely(String collectionName) {
        try {
            // Validate state before collection operation
            if (!validateConnectionState()) {
                logger.warning("Cannot create collection - invalid connection state: " + collectionName);
                return false;
            }

            // Check if collection already exists
            boolean exists = false;
            for (String name : database.listCollectionNames()) {
                if (name.equals(collectionName)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                // Validate state before creation
                if (!validateConnectionState()) {
                    logger.warning("Connection state became invalid before collection creation: " + collectionName);
                    return false;
                }

                database.createCollection(collectionName);

                // Validate state after creation
                if (!validateConnectionState()) {
                    logger.warning("Connection state became invalid after collection creation: " + collectionName);
                    return false;
                }

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
     * Enhanced index creation with comprehensive error handling
     */
    private void createEnhancedIndexes() {
        try {
            if (!validateConnectionState()) {
                logger.warning("Cannot create indexes - invalid connection state");
                return;
            }

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

            logger.info("✅ Enhanced database indexes created successfully");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating enhanced indexes", e);
        }
    }

    /**
     * Safe index creation with better error handling
     */
    private void createIndexSafely(MongoCollection<Document> collection, Document indexDoc, String indexName) {
        try {
            if (!validateConnectionState()) {
                logger.warning("Cannot create index - invalid connection state: " + indexName);
                return;
            }

            collection.createIndex(indexDoc);

            if (!validateConnectionState()) {
                logger.warning("Connection state became invalid after index creation: " + indexName);
                return;
            }

            logger.fine("Created enhanced index: " + indexName);
        } catch (Exception e) {
            logger.fine("✗ Enhanced index " + indexName + " creation failed or already exists: " + e.getMessage());
        }
    }

    /**
     * Initialize system information collection
     */
    private void initializeSystemInfo() {
        try {
            if (!validateConnectionState()) {
                logger.warning("Cannot initialize system info - invalid connection state");
                return;
            }

            MongoCollection<Document> systemCollection = database.getCollection("system_info");

            // Store system startup information
            Document systemInfo = new Document("key", "database_info")
                    .append("database_name", databaseName)
                    .append("initialized_at", System.currentTimeMillis())
                    .append("server_version", plugin.getServer().getVersion())
                    .append("plugin_version", plugin.getDescription().getVersion())
                    .append("connection_pool_size", maxConnectionPoolSize)
                    .append("state_validation_enabled", true);

            systemCollection.replaceOne(
                    new Document("key", "database_info"),
                    systemInfo,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
            );

            if (!validateConnectionState()) {
                logger.warning("Connection state became invalid after system info initialization");
                return;
            }

            logger.fine("System information initialized with state validation");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to initialize system information", e);
        }
    }

    /**
     * Enhanced monitoring system
     */
    private void startEnhancedMonitoring() {
        if (healthCheckActive.compareAndSet(false, true)) {
            // Health check task with state validation
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

            // Connection monitor task with state validation
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

            logger.info("✅ Enhanced MongoDB monitoring started with state validation");
        }
    }

    /**
     * Enhanced health check with comprehensive testing and state validation
     */
    private void performEnhancedHealthCheck() {
        connectionLock.readLock().lock();
        try {
            if (!connected.get() || mongoClient == null || database == null) {
                handleUnhealthyConnection("Connection state invalid - null components");
                return;
            }

            // Critical: Validate connection state first
            if (!validateConnectionState()) {
                handleUnhealthyConnection("Connection state validation failed");
                return;
            }

            // Perform ping test with state validation
            try {
                Document result = database.runCommand(new Document("ping", 1));

                // Validate state after ping
                if (!validateConnectionState()) {
                    handleUnhealthyConnection("Connection state became invalid after ping");
                    return;
                }

                if (result != null && result.get("ok", Number.class).intValue() == 1) {
                    lastSuccessfulPing.set(System.currentTimeMillis());
                    connectionValidated.set(true);
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
     * Monitor connection status and performance with state validation
     */
    private void monitorConnectionStatus() {
        try {
            long timeSinceLastPing = System.currentTimeMillis() - lastSuccessfulPing.get();

            if (timeSinceLastPing > (healthCheckInterval * 2)) {
                logger.warning("No successful ping in " + (timeSinceLastPing / 1000) + " seconds");

                if (autoRecoveryEnabled.get()) {
                    attemptAutoRecovery("Extended ping failure");
                }
            }

            // Additional state validation check
            if (connected.get() && !connectionValidated.get()) {
                logger.warning("Connection marked as connected but not validated");
                if (autoRecoveryEnabled.get()) {
                    attemptAutoRecovery("Connection not validated");
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
        logger.warning("✗ Enhanced MongoDB connection unhealthy: " + reason);

        connected.set(false);
        connectionValidated.set(false);

        if (autoRecoveryEnabled.get()) {
            attemptAutoRecovery(reason);
        }
    }

    /**
     * Attempt automatic recovery with enhanced logic
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

        // Schedule recovery attempt with exponential backoff
        long delay = recoveryDelay * recoveryAttempts.get(); // Exponential backoff
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                if (connect()) {
                    logger.info("✅ Enhanced MongoDB recovery successful");
                    successfulRecoveries.incrementAndGet();
                    // Reset recovery counter on success
                    recoveryAttempts.set(0);
                } else {
                    logger.warning("✗ Enhanced MongoDB recovery failed");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during recovery attempt", e);
            }
        }, delay / 50); // Convert to ticks
    }

    /**
     * Enhanced connection health check with state validation
     */
    private boolean isConnectionHealthyAndValidated() {
        connectionLock.readLock().lock();
        try {
            if (!connected.get() || mongoClient == null || database == null) {
                return false;
            }

            if (!connectionValidated.get()) {
                return false;
            }

            // Critical state validation check
            if (!validateConnectionState()) {
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
     * CRITICAL: Enhanced safe database operation with state validation and queuing
     */
    public <T> T performSafeOperation(DatabaseOperation<T> operation) {
        return performSafeOperation(operation, 3); // Default 3 retries
    }

    public <T> T performSafeOperation(DatabaseOperation<T> operation, int maxRetries) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }

        if (shuttingDown.get()) {
            logger.warning("Attempted database operation during shutdown - BLOCKING");
            return null;
        }

        totalOperations.incrementAndGet();

        // If connection is not healthy, queue the operation for later processing
        if (!isConnectionHealthyAndValidated()) {
            logger.info("Connection not healthy - queuing operation for later processing");
            queuedOperations.incrementAndGet();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<T> result = new AtomicReference<>();

            operationQueue.offer(() -> {
                try {
                    T operationResult = performSafeOperationImmediate(operation, maxRetries);
                    result.set(operationResult);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in queued operation", e);
                } finally {
                    latch.countDown();
                }
            });

            try {
                // Wait up to 30 seconds for the operation to complete
                if (latch.await(30, TimeUnit.SECONDS)) {
                    return result.get();
                } else {
                    logger.warning("Queued operation timed out after 30 seconds");
                    queuedOperations.decrementAndGet();
                    failedOperations.incrementAndGet();
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Interrupted while waiting for queued operation");
                queuedOperations.decrementAndGet();
                failedOperations.incrementAndGet();
                return null;
            }
        }

        return performSafeOperationImmediate(operation, maxRetries);
    }

    /**
     * Perform operation immediately with enhanced state validation
     */
    private <T> T performSafeOperationImmediate(DatabaseOperation<T> operation, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // CRITICAL: Ensure connection and validate state before each attempt
            if (!ensureConnectionWithValidation()) {
                if (attempt == maxRetries) {
                    logger.severe("Cannot perform database operation - connection failed after " + maxRetries + " attempts");
                    failedOperations.incrementAndGet();
                    return null;
                }

                // Exponential backoff between attempts
                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            connectionLock.readLock().lock();
            try {
                // CRITICAL: Final state validation before operation execution
                if (!validateConnectionState()) {
                    logger.warning("Connection state invalid just before operation execution (attempt " + attempt + ")");
                    stateValidationFailures.incrementAndGet();
                    connected.set(false);
                    connectionValidated.set(false);
                    lastException = new IllegalStateException("Connection state invalid before operation");
                    continue;
                }

                T result = operation.execute();

                // CRITICAL: Validate state after operation
                if (!validateConnectionState()) {
                    logger.warning("Connection state became invalid after operation execution (attempt " + attempt + ")");
                    stateValidationFailures.incrementAndGet();
                    connected.set(false);
                    connectionValidated.set(false);

                    // If we got a result but state became invalid, still return the result
                    // as the operation likely succeeded
                    if (result != null) {
                        if (attempt > 1) {
                            logger.fine("Database operation succeeded on attempt " + attempt + " (despite state becoming invalid)");
                        }
                        return result;
                    }

                    lastException = new IllegalStateException("Connection state invalid after operation");
                    continue;
                }

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
                    connectionValidated.set(false);
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
     * Enhanced connection error detection
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
                    message.contains("state should be: open") ||  // Critical addition
                    e instanceof MongoSocketException ||
                    e instanceof MongoTimeoutException ||
                    e instanceof MongoSocketOpenException ||
                    e instanceof MongoSocketClosedException;
        }

        if (e instanceof IllegalStateException) {
            String message = e.getMessage();
            return message != null && message.contains("state should be: open");
        }

        return false;
    }

    /**
     * Enhanced connection ensuring with validation
     */
    private boolean ensureConnectionWithValidation() {
        if (isConnectionHealthyAndValidated()) {
            return true;
        }

        // Attempt to reconnect with state validation
        return connect();
    }

    /**
     * Enhanced collection retrieval with safety checks and state validation
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

            // CRITICAL: Validate state before returning collection
            if (!validateConnectionState()) {
                throw new IllegalStateException("MongoDB connection state invalid when getting collection: " + name);
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
     * Enhanced typed collection retrieval
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

            // CRITICAL: Validate state before returning collection
            if (!validateConnectionState()) {
                throw new IllegalStateException("MongoDB connection state invalid when getting typed collection: " + name);
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
     * Enhanced disconnect with comprehensive cleanup
     */
    public void disconnect() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return; // Already shutting down
        }

        logger.info("Disconnecting from Enhanced MongoDB...");

        // Stop monitoring
        stopEnhancedMonitoring();

        // Stop operation processor
        if (operationProcessorTask != null && !operationProcessorTask.isCancelled()) {
            operationProcessorTask.cancel();
        }

        // Process any remaining queued operations with timeout
        processRemainingQueuedOperations();

        // Shutdown operation executor
        operationExecutor.shutdown();
        try {
            if (!operationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                operationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            operationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close connection
        closeConnectionInternal();

        connected.set(false);
        connectionValidated.set(false);
        logger.info("✅ Enhanced MongoDB disconnection completed");
    }

    /**
     * Process remaining queued operations during shutdown
     */
    private void processRemainingQueuedOperations() {
        logger.info("Processing remaining queued operations during shutdown...");

        int processedCount = 0;
        long startTime = System.currentTimeMillis();
        long maxProcessingTime = 5000; // 5 seconds max

        while (!operationQueue.isEmpty() &&
                (System.currentTimeMillis() - startTime) < maxProcessingTime &&
                processedCount < 50) { // Max 50 operations

            try {
                Runnable operation = operationQueue.poll(100, TimeUnit.MILLISECONDS);
                if (operation != null) {
                    operation.run();
                    processedCount++;
                    queuedOperations.decrementAndGet();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing queued operation during shutdown", e);
            }
        }

        int remainingOperations = operationQueue.size();
        logger.info("Processed " + processedCount + " operations, " + remainingOperations + " remaining (will be discarded)");

        // Clear remaining operations
        operationQueue.clear();
        queuedOperations.set(0);
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

        logger.fine("✅ Enhanced monitoring stopped");
    }

    /**
     * Enhanced connection closure
     */
    private void closeConnectionInternal() {
        connectionLock.writeLock().lock();
        try {
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                    logger.fine("✅ Enhanced MongoDB client closed successfully");
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
     * Enhanced performance metrics logging
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
            logger.info("State Validation Failures: " + stateValidationFailures.get());
            logger.info("Queued Operations: " + queuedOperations.get());

            long timeSinceLastPing = System.currentTimeMillis() - lastSuccessfulPing.get();
            logger.info("Time Since Last Ping: " + (timeSinceLastPing / 1000) + "s");

            if (totalOperations.get() > 0) {
                double successRate = ((double) (totalOperations.get() - failedOperations.get()) / totalOperations.get()) * 100;
                logger.info("Operation Success Rate: " + String.format("%.2f%%", successRate));
            }

            logger.info("Connection Status: " + (connected.get() ? "CONNECTED" : "DISCONNECTED"));
            logger.info("Connection Validated: " + (connectionValidated.get() ? "YES" : "NO"));
            logger.info("Auto Recovery: " + (autoRecoveryEnabled.get() ? "ENABLED" : "DISABLED"));
            logger.info("==============================================");
        } catch (Exception e) {
            logger.log(Level.FINE, "Error logging performance metrics", e);
        }
    }

    /**
     * Enhanced connection string masking
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
        return isConnectionHealthyAndValidated();
    }

    public MongoDatabase getDatabase() {
        connectionLock.readLock().lock();
        try {
            if (!isConnected()) {
                throw new IllegalStateException("Not connected to Enhanced MongoDB");
            }

            // CRITICAL: Validate state before returning database
            if (!validateConnectionState()) {
                throw new IllegalStateException("MongoDB connection state invalid when getting database");
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

            // CRITICAL: Validate state before returning client
            if (!validateConnectionState()) {
                throw new IllegalStateException("MongoDB connection state invalid when getting client");
            }

            return mongoClient;
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * Enhanced connection statistics
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
                connectionValidated.get(),
                autoRecoveryEnabled.get(),
                stateValidationFailures.get(),
                queuedOperations.get()
        );
    }

    /**
     * Enhanced connection statistics class
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
        public final boolean connectionValidated;
        public final boolean autoRecoveryEnabled;
        public final int stateValidationFailures;
        public final int queuedOperations;

        public ConnectionStats(int connectionAttempts, int successfulConnections, int failedConnections,
                               int totalOperations, int failedOperations, int recoveryAttempts,
                               int successfulRecoveries, long lastSuccessfulPing, long lastConnectionTime,
                               boolean connected, boolean connectionValidated, boolean autoRecoveryEnabled,
                               int stateValidationFailures, int queuedOperations) {
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
            this.connectionValidated = connectionValidated;
            this.autoRecoveryEnabled = autoRecoveryEnabled;
            this.stateValidationFailures = stateValidationFailures;
            this.queuedOperations = queuedOperations;
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
                            "failedOps=%d, recovery=%d/%d, connected=%s, validated=%s, autoRecovery=%s, " +
                            "validationFailures=%d, queued=%d, successRate=%.1f%%, opSuccessRate=%.1f%%}",
                    connectionAttempts, successfulConnections, failedConnections, totalOperations,
                    failedOperations, successfulRecoveries, recoveryAttempts, connected, connectionValidated,
                    autoRecoveryEnabled, stateValidationFailures, queuedOperations,
                    getConnectionSuccessRate(), getOperationSuccessRate());
        }
    }

    /**
     * Reset recovery state (for administrative use)
     */
    public void resetRecoveryState() {
        recoveryAttempts.set(0);
        autoRecoveryEnabled.set(true);
        logger.info("✅ Enhanced MongoDB recovery state reset");
    }

    /**
     * Enable/disable auto recovery
     */
    public void setAutoRecoveryEnabled(boolean enabled) {
        autoRecoveryEnabled.set(enabled);
        logger.info("✅ Enhanced MongoDB auto-recovery " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Force connection health check with state validation
     */
    public boolean forceHealthCheck() {
        try {
            performEnhancedHealthCheck();
            return isConnectionHealthyAndValidated();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Force health check failed", e);
            return false;
        }
    }

    /**
     * Force connection state validation
     */
    public boolean forceStateValidation() {
        try {
            boolean valid = validateConnectionState();
            if (valid) {
                connectionValidated.set(true);
                logger.info("✅ Connection state validation successful");
            } else {
                connectionValidated.set(false);
                logger.warning("✗ Connection state validation failed");
            }
            return valid;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Force state validation failed", e);
            connectionValidated.set(false);
            return false;
        }
    }
}