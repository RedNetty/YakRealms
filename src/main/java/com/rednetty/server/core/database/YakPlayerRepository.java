package com.rednetty.server.core.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import org.bson.Document;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced YakPlayerRepository with bulletproof connection state management
 * FIXED: Bank inventory persistence - now properly saves/loads bank data
 * FIXED: Health persistence - now properly preserves health values without aggressive validation
 *
 * Key Improvements:
 * - CRITICAL FIX: Added bank inventory serialization/deserialization
 * - Enhanced bank data validation and recovery
 * - Improved error handling for bank-specific data
 * - Backup system for bank inventories
 * - CRITICAL FIX: Proper health loading/saving without data loss
 */
public class YakPlayerRepository implements Repository<YakPlayer, UUID> {
    private static final String COLLECTION_NAME = "players";
    private static final String BACKUP_COLLECTION_NAME = "players_backup";
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_BASE_DELAY_MS = 500;
    private static final long MAX_RETRY_DELAY_MS = 5000;

    // Core dependencies
    private final Logger logger;
    private final YakRealms plugin;
    private final File backupDir;
    private final EnhancedDocumentConverter documentConverter;

    // Database collections - retrieved fresh each time to avoid stale references
    private final AtomicBoolean repositoryInitialized = new AtomicBoolean(false);

    // Enhanced error tracking
    private final AtomicInteger totalOperations = new AtomicInteger(0);
    private final AtomicInteger successfulOperations = new AtomicInteger(0);
    private final AtomicInteger failedOperations = new AtomicInteger(0);
    private final AtomicInteger connectionStateFailures = new AtomicInteger(0);
    private final AtomicInteger localBackupsCreated = new AtomicInteger(0);
    private final AtomicInteger databaseBackupsCreated = new AtomicInteger(0);
    private final AtomicInteger emergencyRecoveries = new AtomicInteger(0);

    // Bank-specific tracking
    private final AtomicInteger bankDataSaved = new AtomicInteger(0);
    private final AtomicInteger bankDataLoaded = new AtomicInteger(0);
    private final AtomicInteger bankDataErrors = new AtomicInteger(0);

    // Health-specific tracking
    private final AtomicInteger healthDataPreserved = new AtomicInteger(0);
    private final AtomicInteger healthDataRepaired = new AtomicInteger(0);

    public YakPlayerRepository() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.documentConverter = new EnhancedDocumentConverter();

        // Create backup directory
        this.backupDir = new File(plugin.getDataFolder(), "backups/players");
        if (!backupDir.exists()) {
            boolean created = backupDir.mkdirs();
            if (!created) {
                logger.warning("Failed to create backup directory: " + backupDir.getAbsolutePath());
            }
        }

        // Initialize repository
        initializeRepository();
    }

    /**
     * Enhanced repository initialization with better error handling
     */
    private void initializeRepository() {
        try {
            logger.info("Initializing Enhanced YakPlayerRepository with bank and health persistence fixes...");

            // Get MongoDB manager and verify connection
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager == null) {
                throw new RuntimeException("MongoDBManager not available");
            }

            if (!mongoDBManager.isConnected()) {
                logger.warning("MongoDBManager not connected - attempting connection...");
                if (!mongoDBManager.connect()) {
                    throw new RuntimeException("Failed to establish MongoDB connection");
                }
            }

            if (!mongoDBManager.isHealthy()) {
                throw new RuntimeException("MongoDB connection is not healthy");
            }

            // Test connection with enhanced validation
            testConnectionWithStateValidation();

            repositoryInitialized.set(true);
            logger.info("✅ Enhanced YakPlayerRepository initialized successfully with bank and health persistence fixes");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize Enhanced YakPlayerRepository", e);
            repositoryInitialized.set(false);
            throw new RuntimeException("Repository initialization failed", e);
        }
    }

    /**
     * Enhanced connection test with state validation
     */
    private void testConnectionWithStateValidation() {
        try {
            // Test collection access with state validation
            MongoCollection<Document> testCollection = getCollectionSafely(COLLECTION_NAME);
            if (testCollection == null) {
                throw new RuntimeException("Failed to get collection: " + COLLECTION_NAME);
            }

            // Test document count operation
            Long count = MongoDBManager.getInstance().performSafeOperation(() -> {
                return testCollection.countDocuments();
            });

            if (count == null) {
                throw new RuntimeException("Failed to perform test count operation");
            }

            logger.info("Repository connection test successful. Document count: " + count);

            // Test document conversion with bank and health data
            Document testDoc = new Document("uuid", "test-uuid")
                    .append("username", "test-user")
                    .append("first_join", System.currentTimeMillis() / 1000)
                    .append("last_login", System.currentTimeMillis() / 1000)
                    .append("health", 75.5)
                    .append("max_health", 120.0)
                    .append("bank_inventory", new Document("1", "test-bank-data"));

            // Test that our converter can handle bank and health data
            YakPlayer testPlayer = documentConverter.documentToPlayer(testDoc);
            if (testPlayer != null) {
                logger.info("✅ Document converter test with bank and health data successful");
            } else {
                logger.warning("Document converter test failed with bank and health data");
            }

        } catch (Exception e) {
            throw new RuntimeException("Repository connection test failed", e);
        }
    }

    /**
     * Safely get collection with state validation
     */
    private MongoCollection<Document> getCollectionSafely(String collectionName) {
        try {
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (!mongoDBManager.isHealthy()) {
                logger.warning("MongoDB connection not healthy when getting collection: " + collectionName);
                return null;
            }
            return mongoDBManager.getCollection(collectionName);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting collection safely: " + collectionName, e);
            return null;
        }
    }

    @Override
    public CompletableFuture<Optional<YakPlayer>> findById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            if (id == null) {
                logger.warning("Attempted to find player with null UUID");
                return Optional.empty();
            }

            if (!repositoryInitialized.get()) {
                logger.severe("Repository not initialized - cannot find player: " + id);
                return Optional.empty();
            }

            totalOperations.incrementAndGet();

            // ALWAYS load fresh from database with enhanced state validation
            Optional<YakPlayer> result = performEnhancedDatabaseFind(id);

            if (result.isPresent()) {
                successfulOperations.incrementAndGet();
            } else {
                failedOperations.incrementAndGet();
            }

            return result;
        });
    }

    /**
     * Enhanced database find operation with rigorous state validation and retry logic
     */
    private Optional<YakPlayer> performEnhancedDatabaseFind(UUID id) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.fine("Loading FRESH player data with bank and health persistence (attempt " + attempt + "): " + id);

                // Enhanced operation with state validation
                Document doc = MongoDBManager.getInstance().performSafeOperation(() -> {
                    MongoCollection<Document> collection = getCollectionSafely(COLLECTION_NAME);
                    if (collection == null) {
                        throw new RuntimeException("Collection not available");
                    }
                    return collection.find(Filters.eq("uuid", id.toString())).first();
                }, MAX_RETRY_ATTEMPTS);

                if (doc != null) {
                    logger.info("Found FRESH player document in database with bank and health data: " + id);

                    // Enhanced document validation before conversion
                    if (!documentConverter.validateDocument(doc)) {
                        logger.warning("Document validation failed for player: " + id + " - attempting repair");
                        doc = documentConverter.repairDocument(doc);
                        if (doc == null) {
                            logger.severe("Document repair failed for player: " + id);
                            lastException = new RuntimeException("Document validation and repair failed");
                            continue;
                        }
                        logger.info("Successfully repaired document for player: " + id);
                    }

                    YakPlayer player = documentConverter.documentToPlayer(doc);

                    if (player != null) {
                        logger.info("✅ Successfully loaded FRESH player data with bank inventories and health from database: " + id);
                        return Optional.of(player);
                    } else {
                        logger.warning("Failed to convert document to player: " + id);
                        // Create emergency backup of corrupted document
                        createCorruptedDataBackup(doc, id);
                        lastException = new RuntimeException("Document conversion failed");
                    }
                } else {
                    logger.fine("No document found for player: " + id);
                    return Optional.empty();
                }

            } catch (Exception e) {
                lastException = e;
                logger.log(Level.WARNING, "Error finding player by UUID: " + id + " (attempt " + attempt + ")", e);

                // Check if this is a connection state error
                if (isConnectionStateError(e)) {
                    connectionStateFailures.incrementAndGet();
                    logger.warning("Connection state error detected - will retry with backoff");
                }

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long delay = calculateRetryDelay(attempt);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.warning("Failed to find player after " + MAX_RETRY_ATTEMPTS + " attempts: " + id +
                " - Last error: " + (lastException != null ? lastException.getMessage() : "unknown"));
        return Optional.empty();
    }

    /**
     * Check if error is related to connection state
     */
    private boolean isConnectionStateError(Exception e) {
        if (e == null) return false;
        String message = e.getMessage();
        if (message == null) return false;

        return message.contains("state should be: open") ||
                message.contains("connection") ||
                message.contains("socket") ||
                message.contains("network") ||
                message.contains("timeout") ||
                message.contains("broken") ||
                message.contains("closed");
    }

    /**
     * Calculate retry delay with exponential backoff
     */
    private long calculateRetryDelay(int attempt) {
        long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1)); // Exponential backoff
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    @Override
    public CompletableFuture<YakPlayer> save(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            if (player == null) {
                logger.warning("Attempted to save null player");
                return null;
            }

            totalOperations.incrementAndGet();

            if (!validatePlayerData(player)) {
                logger.warning("Invalid player data for " + player.getUsername() + " - creating local backup");
                createLocalBackup(player);
                failedOperations.incrementAndGet();
                return player;
            }

            if (!repositoryInitialized.get()) {
                logger.warning("Repository not initialized - creating local backup only: " + player.getUsername());
                createLocalBackup(player);
                failedOperations.incrementAndGet();
                return player;
            }

            YakPlayer result = savePlayerToDatabaseEnhanced(player);

            if (result != null) {
                successfulOperations.incrementAndGet();
            } else {
                failedOperations.incrementAndGet();
            }

            return result;
        });
    }

    /**
     * Enhanced save operation with comprehensive state validation and retry logic
     */
    private YakPlayer savePlayerToDatabaseEnhanced(YakPlayer player) {
        if (player == null || !validatePlayerData(player)) {
            createLocalBackup(player);
            return player;
        }

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.info("Saving FRESH player to database with bank and health data (attempt " + attempt + "): " + player.getUsername());

                Document doc = documentConverter.playerToDocument(player);
                if (doc == null) {
                    logger.severe("Failed to convert player to document: " + player.getUsername());
                    createLocalBackup(player);
                    return player;
                }

                // Enhanced validation before saving
                if (!documentConverter.validateDocument(doc)) {
                    logger.warning("Generated document failed validation for " + player.getUsername());
                    createLocalBackup(player);
                    return player;
                }

                // Create backup before saving (with state validation)
                createDatabaseBackupEnhanced(player, doc);

                // Perform enhanced save with state validation
                Boolean saveResult = MongoDBManager.getInstance().performSafeOperation(() -> {
                    MongoCollection<Document> collection = getCollectionSafely(COLLECTION_NAME);
                    if (collection == null) {
                        throw new RuntimeException("Collection not available for save operation");
                    }

                    collection.replaceOne(
                            Filters.eq("uuid", player.getUUID().toString()),
                            doc,
                            new ReplaceOptions().upsert(true)
                    );
                    return true;
                }, MAX_RETRY_ATTEMPTS);

                if (saveResult != null && saveResult) {
                    logger.info("✅ Successfully saved FRESH player with bank and health data to database: " + player.getUsername());
                    return player;
                } else {
                    lastException = new RuntimeException("Save operation returned null or false");
                    logger.warning("Save operation failed for player: " + player.getUsername() + " (attempt " + attempt + ")");
                }

            } catch (Exception e) {
                lastException = e;
                logger.log(Level.WARNING, "Error saving player: " + player.getUUID() + " (attempt " + attempt + ")", e);

                // Check if this is a connection state error
                if (isConnectionStateError(e)) {
                    connectionStateFailures.incrementAndGet();
                    logger.warning("Connection state error during save - will retry with backoff");
                }

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long delay = calculateRetryDelay(attempt);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    createLocalBackup(player);
                    logger.severe("Failed to save player after " + MAX_RETRY_ATTEMPTS + " attempts: " + player.getUsername() +
                            " - Last error: " + (lastException != null ? lastException.getMessage() : "unknown"));
                }
            }
        }

        // Final fallback - ensure local backup exists
        createLocalBackup(player);
        return player;
    }

    /**
     * Enhanced synchronous save for critical scenarios
     */
    public YakPlayer saveSync(YakPlayer player) {
        if (player == null || !validatePlayerData(player)) {
            if (player != null) {
                createLocalBackup(player);
            }
            return player;
        }
        Player bukkitPlayer = player.getBukkitPlayer();
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            player.setHealth(bukkitPlayer.getHealth());
            player.setFoodLevel(bukkitPlayer.getFoodLevel());
            player.setSaturation(bukkitPlayer.getSaturation());
            player.setMaxHealth(Objects.requireNonNull(bukkitPlayer.getAttribute(Attribute.MAX_HEALTH)).getValue());
        }

        if (!repositoryInitialized.get()) {
            createLocalBackup(player);
            logger.warning("Could not sync save player - repository not initialized: " + player.getUsername());
            return player;
        }

        totalOperations.incrementAndGet();
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.info("Performing IMMEDIATE sync save with bank and health data (attempt " + attempt + "): " + player.getUsername());

                Document doc = documentConverter.playerToDocument(player);
                if (doc == null) {
                    logger.severe("Failed to convert player to document during sync save: " + player.getUsername());
                    createLocalBackup(player);
                    failedOperations.incrementAndGet();
                    return player;
                }

                // Enhanced validation
                if (!documentConverter.validateDocument(doc)) {
                    logger.warning("Generated document failed validation during sync save for " + player.getUsername());
                    createLocalBackup(player);
                    failedOperations.incrementAndGet();
                    return player;
                }

                // Create backup and save with enhanced state validation
                createDatabaseBackupEnhanced(player, doc);

                Boolean saveResult = MongoDBManager.getInstance().performSafeOperation(() -> {
                    MongoCollection<Document> collection = getCollectionSafely(COLLECTION_NAME);
                    if (collection == null) {
                        throw new RuntimeException("Collection not available for sync save");
                    }

                    collection.replaceOne(
                            Filters.eq("uuid", player.getUUID().toString()),
                            doc,
                            new ReplaceOptions().upsert(true)
                    );
                    return true;
                }, 1); // Single attempt for sync save to avoid blocking

                if (saveResult != null && saveResult) {
                    logger.info("✅ Successfully performed IMMEDIATE sync save with bank and health data for player: " + player.getUsername());
                    successfulOperations.incrementAndGet();
                    return player;
                } else {
                    lastException = new RuntimeException("Sync save operation returned null or false");
                }

            } catch (Exception e) {
                lastException = e;
                logger.log(Level.SEVERE, "Error during sync save for player: " + player.getUUID() + " (attempt " + attempt + ")", e);

                if (isConnectionStateError(e)) {
                    connectionStateFailures.incrementAndGet();
                }

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    // Shorter delay for sync operations
                    try {
                        Thread.sleep(Math.min(calculateRetryDelay(attempt), 1000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.severe("All sync save attempts failed for player: " + player.getUsername() +
                " - Last error: " + (lastException != null ? lastException.getMessage() : "unknown"));
        createLocalBackup(player);
        failedOperations.incrementAndGet();
        return player;
    }

    @Override
    public CompletableFuture<List<YakPlayer>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<YakPlayer> players = new ArrayList<>();

            if (!repositoryInitialized.get()) {
                logger.severe("Repository not initialized - cannot find all players");
                return players;
            }

            totalOperations.incrementAndGet();

            try {
                logger.info("Loading all players FRESH from database with bank and health data...");

                FindIterable<Document> docs = MongoDBManager.getInstance().performSafeOperation(() -> {
                    MongoCollection<Document> collection = getCollectionSafely(COLLECTION_NAME);
                    if (collection == null) {
                        throw new RuntimeException("Collection not available for findAll");
                    }
                    return collection.find().batchSize(50); // Reduced batch size for stability
                }, MAX_RETRY_ATTEMPTS);

                if (docs != null) {
                    int loadedCount = 0;
                    int errorCount = 0;
                    int repairedCount = 0;

                    for (Document doc : docs) {
                        try {
                            // Enhanced document validation and repair
                            if (!documentConverter.validateDocument(doc)) {
                                logger.fine("Attempting to repair document during findAll");
                                doc = documentConverter.repairDocument(doc);
                                if (doc != null) {
                                    repairedCount++;
                                } else {
                                    errorCount++;
                                    continue;
                                }
                            }

                            YakPlayer player = documentConverter.documentToPlayer(doc);
                            if (player != null) {
                                players.add(player);
                                loadedCount++;
                            } else {
                                errorCount++;
                            }
                        } catch (Exception e) {
                            errorCount++;
                            logger.log(Level.WARNING, "Error processing document during findAll", e);
                        }
                    }

                    logger.info("✅ Loaded " + loadedCount + " players FRESH from database with bank and health data, " +
                            errorCount + " errors, " + repairedCount + " repaired");
                    successfulOperations.incrementAndGet();
                } else {
                    failedOperations.incrementAndGet();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error finding all players", e);
                failedOperations.incrementAndGet();
            }

            return players;
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(YakPlayer player) {
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }
        return deleteById(player.getUUID());
    }

    @Override
    public CompletableFuture<Boolean> deleteById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            if (id == null) {
                return false;
            }

            if (!repositoryInitialized.get()) {
                logger.severe("Repository not initialized - cannot delete player: " + id);
                return false;
            }

            totalOperations.incrementAndGet();

            try {
                DeleteResult result = MongoDBManager.getInstance().performSafeOperation(() -> {
                    MongoCollection<Document> collection = getCollectionSafely(COLLECTION_NAME);
                    if (collection == null) {
                        throw new RuntimeException("Collection not available for delete");
                    }
                    return collection.deleteOne(Filters.eq("uuid", id.toString()));
                }, MAX_RETRY_ATTEMPTS);

                boolean success = result != null && result.getDeletedCount() > 0;
                if (success) {
                    logger.info("✅ Successfully deleted player: " + id);
                    successfulOperations.incrementAndGet();
                } else {
                    logger.warning("No player found to delete: " + id);
                    failedOperations.incrementAndGet();
                }
                return success;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error deleting player: " + id, e);
                failedOperations.incrementAndGet();
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> existsById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            if (id == null) {
                return false;
            }

            if (!repositoryInitialized.get()) {
                logger.severe("Repository not initialized - cannot check if player exists: " + id);
                return false;
            }

            totalOperations.incrementAndGet();

            try {
                Long count = MongoDBManager.getInstance().performSafeOperation(() -> {
                    MongoCollection<Document> collection = getCollectionSafely(COLLECTION_NAME);
                    if (collection == null) {
                        throw new RuntimeException("Collection not available for exists check");
                    }
                    return collection.countDocuments(Filters.eq("uuid", id.toString()));
                }, MAX_RETRY_ATTEMPTS);

                boolean exists = count != null && count > 0;
                if (exists) {
                    successfulOperations.incrementAndGet();
                } else {
                    failedOperations.incrementAndGet();
                }
                return exists;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error checking if player exists: " + id, e);
                failedOperations.incrementAndGet();
                return false;
            }
        });
    }

    /**
     * Enhanced player data validation with auto-correction
     */
    private boolean validatePlayerData(YakPlayer player) {
        if (player == null) {
            return false;
        }

        if (player.getUUID() == null) {
            logger.warning("Player validation failed: null UUID");
            return false;
        }

        if (player.getUsername() == null || player.getUsername().trim().isEmpty()) {
            logger.warning("Player validation failed: null or empty username for UUID " + player.getUUID());
            return false;
        }

        // BANK DATA VALIDATION
        Map<Integer, String> bankItems = player.getAllSerializedBankItems();
        if (bankItems != null && !bankItems.isEmpty()) {
            logger.fine("Validating bank data for " + player.getUsername() + " - " + bankItems.size() + " pages");

            // Validate bank pages are within reasonable limits
            for (Integer page : bankItems.keySet()) {
                if (page < 1 || page > 10) {
                    logger.warning("Player validation: invalid bank page " + page + " for " + player.getUsername() + ", removing");
                    player.setSerializedBankItems(page, null);
                }
            }

            // Validate bank page count matches player's bank pages
            int maxPageWithData = bankItems.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (maxPageWithData > player.getBankPages()) {
                logger.warning("Player validation: bank data exists for page " + maxPageWithData +
                        " but player only has " + player.getBankPages() + " pages for " + player.getUsername());
                // Don't auto-correct this, just log it
            }
        }


        return true;
    }

    /**
     * Enhanced local backup creation
     */
    private void createLocalBackup(YakPlayer player) {
        if (player == null) {
            return;
        }

        try {
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                logger.warning("Failed to create backup directory");
                return;
            }

            File playerDir = new File(backupDir, player.getUUID().toString());
            if (!playerDir.exists() && !playerDir.mkdirs()) {
                logger.warning("Failed to create player backup directory");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Document doc = documentConverter.playerToDocument(player);

            if (doc != null) {
                File backupFile = new File(playerDir, timestamp + "_local.json");
                Files.write(backupFile.toPath(), doc.toJson().getBytes());
                localBackupsCreated.incrementAndGet();
                logger.fine("✅ Created local backup with bank and health data for player: " + player.getUsername());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create local backup for player " + player.getUsername(), e);
        }
    }

    /**
     * Enhanced database backup creation with state validation
     */
    private void createDatabaseBackupEnhanced(YakPlayer player, Document doc) {
        if (player == null || doc == null) {
            return;
        }

        try {
            // Check if MongoDB is healthy before attempting backup
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (!mongoDBManager.isHealthy()) {
                logger.fine("MongoDB not healthy - skipping database backup for " + player.getUsername());
                return;
            }

            Document backupDoc = new Document(doc);
            backupDoc.append("timestamp", System.currentTimeMillis() / 1000);
            backupDoc.append("backup_reason", "auto_save");
            backupDoc.append("backup_version", "_v4_bank_health_fixed");
            backupDoc.append("connection_state_validated", true);

            Boolean backupResult = MongoDBManager.getInstance().performSafeOperation(() -> {
                MongoCollection<Document> backupCollection = getCollectionSafely(BACKUP_COLLECTION_NAME);
                if (backupCollection == null) {
                    throw new RuntimeException("Backup collection not available");
                }
                backupCollection.insertOne(backupDoc);
                return true;
            }, 1); // Single attempt for backup to avoid blocking

            if (backupResult != null && backupResult) {
                databaseBackupsCreated.incrementAndGet();
                logger.fine("✅ Created database backup with bank and health data for player: " + player.getUsername());
            } else {
                logger.fine("Database backup failed for player: " + player.getUsername());
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create database backup for player " + player.getUsername(), e);
        }
    }

    /**
     * Create backup of corrupted data for debugging
     */
    private void createCorruptedDataBackup(Document doc, UUID playerId) {
        try {
            File corruptedDir = new File(backupDir, "corrupted");
            if (!corruptedDir.exists() && !corruptedDir.mkdirs()) {
                logger.warning("Failed to create corrupted data backup directory");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File corruptedFile = new File(corruptedDir, playerId + "_" + timestamp + "_corrupted.json");
            Files.write(corruptedFile.toPath(), doc.toJson().getBytes());
            emergencyRecoveries.incrementAndGet();
            logger.warning("✅ Created corrupted data backup: " + corruptedFile.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create corrupted data backup", e);
        }
    }

    /**
     * Public API methods
     */
    public boolean isInitialized() {
        return repositoryInitialized.get();
    }

    public void shutdown() {
        logger.info("Shutting down Enhanced YakPlayerRepository...");
        repositoryInitialized.set(false);
        logger.info("✅ Enhanced Repository shutdown completed");
    }

    /**
     * Get repository statistics
     */
    public RepositoryStats getRepositoryStats() {
        return new RepositoryStats(
                totalOperations.get(),
                successfulOperations.get(),
                failedOperations.get(),
                connectionStateFailures.get(),
                localBackupsCreated.get(),
                databaseBackupsCreated.get(),
                emergencyRecoveries.get(),
                repositoryInitialized.get(),
                bankDataSaved.get(),
                bankDataLoaded.get(),
                bankDataErrors.get(),
                healthDataPreserved.get(),
                healthDataRepaired.get()
        );
    }

    /**
     * Repository statistics class
     */
    public static class RepositoryStats {
        public final int totalOperations;
        public final int successfulOperations;
        public final int failedOperations;
        public final int connectionStateFailures;
        public final int localBackupsCreated;
        public final int databaseBackupsCreated;
        public final int emergencyRecoveries;
        public final boolean initialized;
        public final int bankDataSaved;
        public final int bankDataLoaded;
        public final int bankDataErrors;
        public final int healthDataPreserved;
        public final int healthDataRepaired;

        public RepositoryStats(int totalOperations, int successfulOperations, int failedOperations,
                               int connectionStateFailures, int localBackupsCreated, int databaseBackupsCreated,
                               int emergencyRecoveries, boolean initialized, int bankDataSaved,
                               int bankDataLoaded, int bankDataErrors, int healthDataPreserved,
                               int healthDataRepaired) {
            this.totalOperations = totalOperations;
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.connectionStateFailures = connectionStateFailures;
            this.localBackupsCreated = localBackupsCreated;
            this.databaseBackupsCreated = databaseBackupsCreated;
            this.emergencyRecoveries = emergencyRecoveries;
            this.initialized = initialized;
            this.bankDataSaved = bankDataSaved;
            this.bankDataLoaded = bankDataLoaded;
            this.bankDataErrors = bankDataErrors;
            this.healthDataPreserved = healthDataPreserved;
            this.healthDataRepaired = healthDataRepaired;
        }

        public double getSuccessRate() {
            if (totalOperations == 0) return 0.0;
            return ((double) successfulOperations / totalOperations) * 100;
        }

        @Override
        public String toString() {
            return String.format("RepositoryStats{total=%d, success=%d, failed=%d, connectionFailures=%d, " +
                            "localBackups=%d, dbBackups=%d, emergencyRecoveries=%d, initialized=%s, successRate=%.1f%%, " +
                            "bankSaved=%d, bankLoaded=%d, bankErrors=%d, healthPreserved=%d, healthRepaired=%d}",
                    totalOperations, successfulOperations, failedOperations, connectionStateFailures,
                    localBackupsCreated, databaseBackupsCreated, emergencyRecoveries, initialized, getSuccessRate(),
                    bankDataSaved, bankDataLoaded, bankDataErrors, healthDataPreserved, healthDataRepaired);
        }
    }

    /**
     * Enhanced Document conversion helper class with BANK DATA PERSISTENCE FIXES AND HEALTH PRESERVATION
     */
    private class EnhancedDocumentConverter {

        /**
         * Enhanced document validation including bank and health data
         */
        public boolean validateDocument(Document doc) {
            if (doc == null) {
                return false;
            }

            // Check required fields
            if (!doc.containsKey("uuid") || doc.getString("uuid") == null) {
                logger.warning("Document missing required UUID field");
                return false;
            }

            try {
                UUID.fromString(doc.getString("uuid"));
            } catch (Exception e) {
                logger.warning("Document contains invalid UUID: " + doc.getString("uuid"));
                return false;
            }

            // Check for reasonable username
            String username = doc.getString("username");
            if (username == null || username.trim().isEmpty() || username.length() > 16) {
                logger.warning("Document has invalid username: " + username);
                return false;
            }

            // ✅ FIXED: Less aggressive health validation
            try {
                Double health = doc.getDouble("health");
                Double maxHealth = doc.getDouble("max_health");

                if (health != null && (Double.isNaN(health) || Double.isInfinite(health) || health < 0)) {
                    logger.warning("Document has invalid health value: " + health);
                    return false;
                }

                if (maxHealth != null && (Double.isNaN(maxHealth) || Double.isInfinite(maxHealth) || maxHealth <= 0 || maxHealth > 10000)) {
                    logger.warning("Document has invalid max health value: " + maxHealth);
                    return false;
                }
            } catch (Exception e) {
                logger.fine("Error validating health values: " + e.getMessage());
            }

            // BANK DATA VALIDATION
            try {
                Object bankInventory = doc.get("bank_inventory");
                if (bankInventory != null) {
                    if (bankInventory instanceof Document) {
                        Document bankDoc = (Document) bankInventory;
                        for (String key : bankDoc.keySet()) {
                            try {
                                int pageNum = Integer.parseInt(key);
                                if (pageNum < 1 || pageNum > 10) {
                                    logger.warning("Document has invalid bank page number: " + pageNum);
                                    return false;
                                }
                            } catch (NumberFormatException e) {
                                logger.warning("Document has non-numeric bank page key: " + key);
                                return false;
                            }
                        }
                        logger.fine("Bank data validation passed for " + bankDoc.size() + " pages");
                    } else {
                        logger.warning("Document has invalid bank_inventory type: " + bankInventory.getClass());
                        return false;
                    }
                }
            } catch (Exception e) {
                logger.warning("Error validating bank data: " + e.getMessage());
                return false;
            }

            return true;
        }

        /**
         * Enhanced document repair including bank and health data
         */
        public Document repairDocument(Document doc) {
            if (doc == null) {
                return null;
            }

            try {
                Document repaired = new Document(doc);
                boolean wasRepaired = false;

                // Fix missing UUID
                if (!repaired.containsKey("uuid") || repaired.getString("uuid") == null) {
                    logger.warning("Cannot repair document without UUID");
                    return null;
                }

                // Fix missing username
                if (!repaired.containsKey("username") || repaired.getString("username") == null) {
                    repaired.append("username", "Unknown_" + System.currentTimeMillis());
                    wasRepaired = true;
                    logger.info("Repaired missing username");
                }

                // Fix missing timestamps
                long currentTime = System.currentTimeMillis() / 1000;
                if (!repaired.containsKey("first_join") || repaired.getLong("first_join") == null) {
                    repaired.append("first_join", currentTime);
                    wasRepaired = true;
                }

                if (!repaired.containsKey("last_login") || repaired.getLong("last_login") == null) {
                    repaired.append("last_login", currentTime);
                    wasRepaired = true;
                }

                // Fix invalid numeric values
                if (repaired.getInteger("level", 0) < 1) {
                    repaired.append("level", 1);
                    wasRepaired = true;
                }

                if (repaired.getInteger("bank_gems", 0) < 0) {
                    repaired.append("bank_gems", 0);
                    wasRepaired = true;
                }

                // ✅ FIXED: Better health value repair
                Double health = repaired.getDouble("health");
                if (health == null) {
                    repaired.append("health", 50.0);
                    wasRepaired = true;
                    healthDataRepaired.incrementAndGet();
                }

                Double maxHealth = repaired.getDouble("max_health");
                if (maxHealth == null || Double.isNaN(maxHealth) || Double.isInfinite(maxHealth) || maxHealth <= 0 || maxHealth > 10000) {
                    repaired.append("max_health", 50.0);
                    wasRepaired = true;
                    healthDataRepaired.incrementAndGet();
                }

                // Ensure health doesn't exceed max health by too much
                health = repaired.getDouble("health");
                maxHealth = repaired.getDouble("max_health");
                if (health != null && maxHealth != null && health > maxHealth * 2) {
                    repaired.append("health", maxHealth);
                    wasRepaired = true;
                    healthDataRepaired.incrementAndGet();
                }

                // BANK DATA REPAIR
                try {
                    Object bankInventory = repaired.get("bank_inventory");
                    if (bankInventory != null && !(bankInventory instanceof Document)) {
                        logger.warning("Repairing invalid bank_inventory type, removing corrupted data");
                        repaired.remove("bank_inventory");
                        wasRepaired = true;
                    } else if (bankInventory instanceof Document) {
                        Document bankDoc = (Document) bankInventory;
                        Document repairedBankDoc = new Document();
                        boolean bankRepaired = false;

                        for (Map.Entry<String, Object> entry : bankDoc.entrySet()) {
                            try {
                                int pageNum = Integer.parseInt(entry.getKey());
                                if (pageNum >= 1 && pageNum <= 10 && entry.getValue() instanceof String) {
                                    repairedBankDoc.append(entry.getKey(), entry.getValue());
                                } else {
                                    logger.warning("Removing invalid bank page: " + entry.getKey());
                                    bankRepaired = true;
                                }
                            } catch (NumberFormatException e) {
                                logger.warning("Removing bank page with invalid key: " + entry.getKey());
                                bankRepaired = true;
                            }
                        }

                        if (bankRepaired) {
                            repaired.append("bank_inventory", repairedBankDoc);
                            wasRepaired = true;
                            logger.info("Repaired bank inventory data");
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Error repairing bank data, removing: " + e.getMessage());
                    repaired.remove("bank_inventory");
                    wasRepaired = true;
                }

                if (wasRepaired) {
                    logger.info("✅ Successfully repaired document with bank and health data for UUID: " + repaired.getString("uuid"));
                    return repaired;
                }

                return repaired;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to repair document", e);
                return null;
            }
        }

        /**
         * ✅ FIXED: Enhanced document to player conversion with PROPER HEALTH LOADING
         */
        public YakPlayer documentToPlayer(Document doc) {
            if (doc == null) {
                logger.warning("Cannot convert null document to player");
                return null;
            }

            try {
                String uuidStr = doc.getString("uuid");
                if (uuidStr == null || uuidStr.trim().isEmpty()) {
                    logger.severe("Document missing UUID field");
                    return null;
                }

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    logger.severe("Invalid UUID format: " + uuidStr);
                    return null;
                }

                YakPlayer player = new YakPlayer(uuid);

                // Load basic information with safe defaults
                player.setUsername(safeGetString(doc, "username", "Unknown_" + System.currentTimeMillis()));
                player.setLastLogin(safeGetLong(doc, "last_login", System.currentTimeMillis() / 1000));
                player.setLastLogout(safeGetLong(doc, "last_logout", 0L));
                player.setFirstJoin(safeGetLong(doc, "first_join", System.currentTimeMillis() / 1000));
                player.setTotalPlaytime(safeGetLong(doc, "total_playtime", 0L));

                String ipAddress = safeGetString(doc, "ip_address", null);
                if (ipAddress != null) {
                    player.setIpAddress(ipAddress);
                }

                // Load progression with validation
                player.setLevel(Math.max(1, Math.min(200, safeGetInteger(doc, "level", 1))));
                player.setExp(Math.max(0, safeGetInteger(doc, "exp", 0)));
                player.setMonsterKills(Math.max(0, safeGetInteger(doc, "monster_kills", 0)));
                player.setPlayerKills(Math.max(0, safeGetInteger(doc, "player_kills", 0)));
                player.setDeaths(Math.max(0, safeGetInteger(doc, "deaths", 0)));
                player.setOreMined(Math.max(0, safeGetInteger(doc, "ore_mined", 0)));
                player.setFishCaught(Math.max(0, safeGetInteger(doc, "fish_caught", 0)));
                player.setBlocksBroken(Math.max(0, safeGetInteger(doc, "blocks_broken", 0)));
                player.setDistanceTraveled(Math.max(0.0, safeGetDouble(doc, "distance_traveled", 0.0)));

                // Load economic data with validation
                player.setBankGems(Math.max(0, safeGetInteger(doc, "bank_gems", 0)));
                player.setEliteShards(Math.max(0, safeGetInteger(doc, "elite_shards", 0)));

                // Load bank data
                player.setBankPages(Math.max(1, Math.min(10, safeGetInteger(doc, "bank_pages", 1))));

                // *** CRITICAL FIX: LOAD BANK INVENTORY DATA ***
                try {
                    Object bankInventoryObj = doc.get("bank_inventory");
                    if (bankInventoryObj instanceof Document) {
                        Document bankInventoryDoc = (Document) bankInventoryObj;
                        int loadedPages = 0;

                        for (Map.Entry<String, Object> entry : bankInventoryDoc.entrySet()) {
                            try {
                                int pageNum = Integer.parseInt(entry.getKey());
                                if (pageNum >= 1 && pageNum <= 10 && entry.getValue() instanceof String) {
                                    String serializedData = (String) entry.getValue();
                                    if (serializedData != null && !serializedData.trim().isEmpty()) {
                                        player.setSerializedBankItems(pageNum, serializedData);
                                        loadedPages++;
                                    }
                                } else {
                                    logger.warning("Invalid bank page data: " + entry.getKey() + " = " + entry.getValue());
                                }
                            } catch (NumberFormatException e) {
                                logger.warning("Invalid bank page number: " + entry.getKey());
                            }
                        }

                        if (loadedPages > 0) {
                            bankDataLoaded.incrementAndGet();
                            logger.info("✅ Loaded " + loadedPages + " bank pages for player: " + player.getUsername());
                        }
                    } else if (bankInventoryObj != null) {
                        logger.warning("Bank inventory data is not a Document for player: " + player.getUsername() +
                                " - type: " + bankInventoryObj.getClass());
                        bankDataErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error loading bank inventory data for player: " + player.getUsername(), e);
                    bankDataErrors.incrementAndGet();
                }

                // Load collection bin data
                player.setSerializedCollectionBin(safeGetString(doc, "collection_bin", null));

                // Continue with all other fields as in original...
                // Load alignment data
                player.setAlignment(safeGetString(doc, "alignment", "LAWFUL"));
                player.setChaoticTime(safeGetLong(doc, "chaotic_time", 0L));
                player.setNeutralTime(safeGetLong(doc, "neutral_time", 0L));
                player.setAlignmentChanges(safeGetInteger(doc, "alignment_changes", 0));

                // Load moderation data
                player.setRank(safeGetString(doc, "rank", "DEFAULT"));
                player.setBanned(safeGetBoolean(doc, "banned", false));
                player.setBanReason(safeGetString(doc, "ban_reason", ""));
                player.setBanExpiry(safeGetLong(doc, "ban_expiry", 0L));
                player.setMuteTime(safeGetInteger(doc, "muted", 0));
                player.setWarnings(safeGetInteger(doc, "warnings", 0));
                player.setLastWarning(safeGetLong(doc, "last_warning", 0L));

                // Load chat data
                player.setChatTag(safeGetString(doc, "chat_tag", "DEFAULT"));
                List<String> unlockedTags = safeGetStringList(doc, "unlocked_chat_tags");
                if (unlockedTags != null) {
                    player.setUnlockedChatTags(unlockedTags);
                }
                player.setChatColor(safeGetString(doc, "chat_color", "WHITE"));

                // Load mount and guild data
                player.setHorseTier(safeGetInteger(doc, "horse_tier", 0));
                player.setHorseName(safeGetString(doc, "horse_name", ""));
                player.setGuildName(safeGetString(doc, "guild_name", ""));
                player.setGuildRank(safeGetString(doc, "guild_rank", ""));
                player.setGuildContribution(safeGetInteger(doc, "guild_contribution", 0));

                // Load player preferences
                List<String> toggleList = safeGetStringList(doc, "toggle_settings");
                if (toggleList != null) {
                    player.setToggleSettings(new HashSet<>(toggleList));
                }

                // Load location and state data
                player.setWorld(safeGetString(doc, "world", null));
                player.setLocationX(safeGetDouble(doc, "location_x", 0.0));
                player.setLocationY(safeGetDouble(doc, "location_y", 64.0)); // Safe default Y
                player.setLocationZ(safeGetDouble(doc, "location_z", 0.0));
                player.setLocationYaw(safeGetFloat(doc, "location_yaw", 0.0f));
                player.setLocationPitch(safeGetFloat(doc, "location_pitch", 0.0f));
                player.setPreviousLocation(safeGetString(doc, "previous_location", null));

                // Load serialized inventory data
                player.setSerializedInventory(safeGetString(doc, "inventory_contents", null));
                player.setSerializedArmor(safeGetString(doc, "armor_contents", null));
                player.setSerializedEnderChest(safeGetString(doc, "ender_chest_contents", null));
                player.setSerializedOffhand(safeGetString(doc, "offhand_item", null));

                // Load respawn items storage
                player.setSerializedRespawnItems(safeGetString(doc, "respawn_items", null));
                player.setRespawnItemCount(safeGetInteger(doc, "respawn_item_count", 0));
                player.setDeathTimestamp(safeGetLong(doc, "death_timestamp", 0L));

                // ✅ CRITICAL FIX: Proper health loading with preservation
                double savedHealth = safeGetDouble(doc, "health", 50.0);
                double savedMaxHealth = safeGetDouble(doc, "max_health", 50.0);

                // ✅ IMPROVED VALIDATION: Only reset if truly invalid, not just unusual values
                boolean healthNeedsRepair = false;

                // Check for truly invalid max health (NaN, Infinite, or unreasonably extreme values)
                if (Double.isNaN(savedMaxHealth) || Double.isInfinite(savedMaxHealth) ||
                        savedMaxHealth <= 0 || savedMaxHealth > 10000) {
                    logger.warning("Invalid max health detected for player: " + player.getUsername() +
                            " (value: " + savedMaxHealth + "), using default");
                    savedMaxHealth = 50.0;
                    healthNeedsRepair = true;
                    healthDataRepaired.incrementAndGet();
                }

                // Check for truly invalid health (NaN, Infinite, negative)
                if (Double.isNaN(savedHealth) || Double.isInfinite(savedHealth) || savedHealth < 0) {
                    logger.warning("Invalid health detected for player: " + player.getUsername() +
                            " (value: " + savedHealth + "), setting to max health");
                    savedHealth = savedMaxHealth;
                    healthNeedsRepair = true;
                    healthDataRepaired.incrementAndGet();
                }

                // ✅ ALLOW health to exceed max health temporarily - this can happen with equipment changes
                // Only cap it if it's extremely beyond reasonable bounds
                if (savedHealth > savedMaxHealth * 2) {
                    logger.info("Health significantly exceeds max health for player: " + player.getUsername() +
                            " (" + savedHealth + "/" + savedMaxHealth + "), capping to max health");
                    savedHealth = savedMaxHealth;
                    healthDataRepaired.incrementAndGet();
                }

                player.setHealth(savedHealth);
                player.setMaxHealth(savedMaxHealth);

                if (healthNeedsRepair) {
                    logger.info("Repaired health values for player: " + player.getUsername() +
                            " (Health: " + savedHealth + "/" + savedMaxHealth + ")");
                } else {
                    healthDataPreserved.incrementAndGet();
                    logger.fine("Loaded health for player: " + player.getUsername() +
                            " (Health: " + savedHealth + "/" + savedMaxHealth + ")");
                }

                player.setFoodLevel(Math.max(0, Math.min(20, safeGetInteger(doc, "food_level", 20))));

                float saturation = safeGetFloat(doc, "saturation", 5.0f);
                if (Float.isNaN(saturation) || Float.isInfinite(saturation)) {
                    saturation = 5.0f;
                }
                player.setSaturation(Math.max(0, Math.min(20, saturation)));

                player.setXpLevel(Math.max(0, Math.min(21863, safeGetInteger(doc, "xp_level", 0))));
                player.setXpProgress(Math.max(0, Math.min(1, safeGetFloat(doc, "xp_progress", 0.0f))));
                player.setTotalExperience(Math.max(0, safeGetInteger(doc, "total_experience", 0)));
                player.setBedSpawnLocation(safeGetString(doc, "bed_spawn_location", null));
                player.setGameMode(safeGetString(doc, "gamemode", "SURVIVAL"));

                // Load combat logout state management with enhanced safety
                String combatLogoutStateStr = safeGetString(doc, "combat_logout_state", null);
                if (combatLogoutStateStr != null && !combatLogoutStateStr.trim().isEmpty()) {
                    try {
                        YakPlayer.CombatLogoutState state = YakPlayer.CombatLogoutState.valueOf(combatLogoutStateStr);
                        player.setCombatLogoutState(state);
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid combat logout state: " + combatLogoutStateStr + " for player " + player.getUsername());
                        player.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                    }
                } else {
                    player.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                }

                logger.info("✅ Successfully converted FRESH document to player with bank and health data: " + player.getUsername() +
                        " (State: " + player.getCombatLogoutState() + ", Bank Pages: " + player.getBankPages() +
                        ", Bank Data: " + player.getAllSerializedBankItems().size() + " pages" +
                        ", Health: " + savedHealth + "/" + savedMaxHealth + ")");
                return player;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error converting document to player", e);
                return null;
            }
        }

        /**
         * ✅ FIXED: Enhanced player to document conversion with PROPER HEALTH SAVING
         */
        public Document playerToDocument(YakPlayer player) {
            if (player == null || player.getUUID() == null) {
                logger.warning("Cannot convert null player or player with null UUID to document");
                return null;
            }

            try {
                Document doc = new Document();

                // Basic identification - required fields
                doc.append("uuid", player.getUUID().toString());
                doc.append("username", player.getUsername() != null ? player.getUsername() : "Unknown");
                doc.append("last_login", player.getLastLogin());
                doc.append("last_logout", player.getLastLogout());
                doc.append("first_join", player.getFirstJoin());
                doc.append("total_playtime", player.getTotalPlaytime());

                if (player.getIpAddress() != null && !player.getIpAddress().trim().isEmpty()) {
                    doc.append("ip_address", player.getIpAddress());
                }

                // ✅ CRITICAL FIX: Better health saving with less aggressive validation
                double health = player.getHealth();
                double maxHealth = player.getMaxHealth();

                // ✅ IMPROVED VALIDATION: Only "fix" truly broken values
                boolean healthWasFixed = false;

                // Only fix truly invalid max health values
                if (Double.isNaN(maxHealth) || Double.isInfinite(maxHealth) || maxHealth <= 0 || maxHealth > 10000) {
                    logger.warning("Fixing invalid max health before save for " + player.getUsername() +
                            " (was: " + maxHealth + ", setting to: 50.0)");
                    maxHealth = 50.0;
                    healthWasFixed = true;
                    healthDataRepaired.incrementAndGet();
                }

                // Only fix truly invalid health values
                if (Double.isNaN(health) || Double.isInfinite(health) || health < 0) {
                    logger.warning("Fixing invalid health before save for " + player.getUsername() +
                            " (was: " + health + ", setting to: " + maxHealth + ")");
                    health = maxHealth;
                    healthWasFixed = true;
                    healthDataRepaired.incrementAndGet();
                }

                // ✅ ALLOW health to be above max health - this is normal during equipment changes
                // Only warn if it's extremely high
                if (health > maxHealth * 2) {
                    logger.info("Player " + player.getUsername() + " has health significantly above max health: " +
                            health + "/" + maxHealth + " - this may indicate equipment was removed");
                }

                doc.append("health", health);
                doc.append("max_health", maxHealth);

                if (healthWasFixed) {
                    logger.info("Fixed health values before saving for " + player.getUsername() +
                            " (Health: " + health + "/" + maxHealth + ")");
                } else {
                    healthDataPreserved.incrementAndGet();
                    logger.fine("Saving health for " + player.getUsername() +
                            " (Health: " + health + "/" + maxHealth + ")");
                }

                // Enhanced food and saturation validation
                doc.append("food_level", Math.max(0, Math.min(20, player.getFoodLevel())));

                float saturation = player.getSaturation();
                if (Float.isNaN(saturation) || Float.isInfinite(saturation)) {
                    saturation = 5.0f;
                }
                doc.append("saturation", Math.max(0, Math.min(20, saturation)));

                // Progression with validation
                doc.append("level", Math.max(1, Math.min(200, player.getLevel())));
                doc.append("exp", Math.max(0, player.getExp()));
                doc.append("monster_kills", Math.max(0, player.getMonsterKills()));
                doc.append("player_kills", Math.max(0, player.getPlayerKills()));
                doc.append("deaths", Math.max(0, player.getDeaths()));
                doc.append("ore_mined", Math.max(0, player.getOreMined()));
                doc.append("fish_caught", Math.max(0, player.getFishCaught()));
                doc.append("blocks_broken", Math.max(0, player.getBlocksBroken()));
                doc.append("distance_traveled", Math.max(0.0, player.getDistanceTraveled()));

                // Economic data with validation
                doc.append("bank_gems", Math.max(0, player.getBankGems()));
                doc.append("elite_shards", Math.max(0, player.getEliteShards()));

                // Bank system data
                doc.append("bank_pages", Math.max(1, Math.min(10, player.getBankPages())));

                // *** CRITICAL FIX: SAVE BANK INVENTORY DATA ***
                try {
                    Map<Integer, String> bankItems = player.getAllSerializedBankItems();
                    if (bankItems != null && !bankItems.isEmpty()) {
                        Document bankInventoryDoc = new Document();
                        int savedPages = 0;

                        for (Map.Entry<Integer, String> entry : bankItems.entrySet()) {
                            Integer pageNum = entry.getKey();
                            String serializedData = entry.getValue();

                            if (pageNum != null && pageNum >= 1 && pageNum <= 10 &&
                                    serializedData != null && !serializedData.trim().isEmpty()) {
                                bankInventoryDoc.append(pageNum.toString(), serializedData);
                                savedPages++;
                            }
                        }

                        if (savedPages > 0) {
                            doc.append("bank_inventory", bankInventoryDoc);
                            bankDataSaved.incrementAndGet();
                            logger.info("✅ Saved " + savedPages + " bank pages for player: " + player.getUsername());
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error saving bank inventory data for player: " + player.getUsername(), e);
                    bankDataErrors.incrementAndGet();
                }

                // Collection bin data
                if (player.getSerializedCollectionBin() != null && !player.getSerializedCollectionBin().trim().isEmpty()) {
                    doc.append("collection_bin", player.getSerializedCollectionBin());
                }

                // Continue with all other fields as in original...
                // Alignment data
                doc.append("alignment", player.getAlignment() != null ? player.getAlignment() : "LAWFUL");
                doc.append("chaotic_time", player.getChaoticTime());
                doc.append("neutral_time", player.getNeutralTime());
                doc.append("alignment_changes", player.getAlignmentChanges());

                // Moderation data
                doc.append("rank", player.getRank() != null ? player.getRank() : "DEFAULT");
                doc.append("banned", player.isBanned());
                doc.append("ban_reason", player.getBanReason() != null ? player.getBanReason() : "");
                doc.append("ban_expiry", player.getBanExpiry());
                doc.append("muted", player.getMuteTime());
                doc.append("warnings", player.getWarnings());
                doc.append("last_warning", player.getLastWarning());

                // Chat data
                doc.append("chat_tag", player.getChatTag() != null ? player.getChatTag() : "DEFAULT");
                doc.append("unlocked_chat_tags", new ArrayList<>(player.getUnlockedChatTags()));
                doc.append("chat_color", player.getChatColor() != null ? player.getChatColor() : "WHITE");

                // Mount and Guild data
                doc.append("horse_tier", player.getHorseTier());
                doc.append("horse_name", player.getHorseName() != null ? player.getHorseName() : "");
                doc.append("guild_name", player.getGuildName() != null ? player.getGuildName() : "");
                doc.append("guild_rank", player.getGuildRank() != null ? player.getGuildRank() : "");
                doc.append("guild_contribution", player.getGuildContribution());

                // Player preferences
                doc.append("toggle_settings", new ArrayList<>(player.getToggleSettings()));

                // Location and state data
                doc.append("world", player.getWorld());
                doc.append("location_x", player.getLocationX());
                doc.append("location_y", player.getLocationY());
                doc.append("location_z", player.getLocationZ());
                doc.append("location_yaw", player.getLocationYaw());
                doc.append("location_pitch", player.getLocationPitch());

                // Serialized inventory data
                doc.append("inventory_contents", player.getSerializedInventory());
                doc.append("armor_contents", player.getSerializedArmor());
                doc.append("ender_chest_contents", player.getSerializedEnderChest());
                doc.append("offhand_item", player.getSerializedOffhand());

                // Respawn items storage
                doc.append("respawn_items", player.getSerializedRespawnItems());
                doc.append("respawn_item_count", player.getRespawnItemCount());
                doc.append("death_timestamp", player.getDeathTimestamp());

                // Enhanced XP validation
                doc.append("xp_level", Math.max(0, Math.min(21863, player.getXpLevel())));
                doc.append("xp_progress", Math.max(0, Math.min(1, player.getXpProgress())));
                doc.append("total_experience", Math.max(0, player.getTotalExperience()));
                doc.append("bed_spawn_location", player.getBedSpawnLocation());
                doc.append("gamemode", player.getGameMode() != null ? player.getGameMode() : "SURVIVAL");

                // Combat logout state management
                doc.append("combat_logout_state", player.getCombatLogoutState().name());

                // Enhanced metadata
                doc.append("last_save_timestamp", System.currentTimeMillis());
                doc.append("save_version", "v4_bank_health_fixed");
                doc.append("connection_state_validated", true);

                logger.info("✅ Successfully converted player to FRESH document with bank and health data: " + player.getUsername() +
                        " (Bank Pages: " + player.getBankPages() + ", Bank Data: " + player.getAllSerializedBankItems().size() + " pages" +
                        ", Health: " + health + "/" + maxHealth + ")");
                return doc;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error converting player to document: " + player.getUsername(), e);
                return null;
            }
        }

        // Enhanced safe getter methods
        private String safeGetString(Document doc, String key, String defaultValue) {
            try {
                String value = doc.getString(key);
                return value != null ? value : defaultValue;
            } catch (Exception e) {
                logger.fine("Error getting string field " + key + ": " + e.getMessage());
                return defaultValue;
            }
        }

        private Integer safeGetInteger(Document doc, String key, Integer defaultValue) {
            try {
                Integer value = doc.getInteger(key);
                return value != null ? value : defaultValue;
            } catch (Exception e) {
                logger.fine("Error getting integer field " + key + ": " + e.getMessage());
                return defaultValue;
            }
        }

        private Long safeGetLong(Document doc, String key, Long defaultValue) {
            try {
                Long value = doc.getLong(key);
                return value != null ? value : defaultValue;
            } catch (Exception e) {
                logger.fine("Error getting long field " + key + ": " + e.getMessage());
                return defaultValue;
            }
        }

        private Double safeGetDouble(Document doc, String key, Double defaultValue) {
            try {
                Double value = doc.getDouble(key);
                if (value != null && !Double.isNaN(value) && !Double.isInfinite(value)) {
                    return value;
                }
                return defaultValue;
            } catch (Exception e) {
                logger.fine("Error getting double field " + key + ": " + e.getMessage());
                return defaultValue;
            }
        }

        private Float safeGetFloat(Document doc, String key, Float defaultValue) {
            try {
                // MongoDB might store as Double, so handle both cases
                Object value = doc.get(key);
                if (value instanceof Number) {
                    float floatValue = ((Number) value).floatValue();
                    if (!Float.isNaN(floatValue) && !Float.isInfinite(floatValue)) {
                        return floatValue;
                    }
                }
                return defaultValue;
            } catch (Exception e) {
                logger.fine("Error getting float field " + key + ": " + e.getMessage());
                return defaultValue;
            }
        }

        private Boolean safeGetBoolean(Document doc, String key, Boolean defaultValue) {
            try {
                Boolean value = doc.getBoolean(key);
                return value != null ? value : defaultValue;
            } catch (Exception e) {
                logger.fine("Error getting boolean field " + key + ": " + e.getMessage());
                return defaultValue;
            }
        }

        private List<String> safeGetStringList(Document doc, String key) {
            try {
                @SuppressWarnings("unchecked")
                List<String> value = doc.getList(key, String.class);
                return value;
            } catch (Exception e) {
                logger.fine("Error getting string list field " + key + ": " + e.getMessage());
                return null;
            }
        }
    }
}