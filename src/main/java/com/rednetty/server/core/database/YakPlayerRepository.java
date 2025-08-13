package com.rednetty.server.core.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import org.bson.Document;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YakPlayerRepository with improved error handling and backwards compatibility
 * NO CACHING for cross-server compatibility - Always loads fresh from database
 *  VERSION: Better data validation, corruption recovery, and null safety
 */
public class YakPlayerRepository implements Repository<YakPlayer, UUID> {
    private static final String COLLECTION_NAME = "players";
    private static final String BACKUP_COLLECTION_NAME = "players_backup";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // Core dependencies
    private final Logger logger;
    private final YakRealms plugin;
    private final File backupDir;
    private final EnhancedDocumentConverter documentConverter;

    // Database collections
    private volatile MongoCollection<Document> collection;
    private volatile MongoCollection<Document> backupCollection;

    // Repository state
    private final AtomicBoolean repositoryInitialized = new AtomicBoolean(false);

    public YakPlayerRepository() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.documentConverter = new EnhancedDocumentConverter();

        // Create backup directory
        this.backupDir = new File(plugin.getDataFolder(), "backups/players");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // Initialize repository
        initializeRepository();
    }

    /**
     * repository initialization with better error handling
     */
    private void initializeRepository() {
        try {
            logger.info("Initializing YakPlayerRepository...");

            // Get MongoDB manager
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager == null || !mongoDBManager.isConnected()) {
                throw new RuntimeException("MongoDBManager not available or not connected");
            }

            // Get collections
            this.collection = mongoDBManager.getCollection(COLLECTION_NAME);
            this.backupCollection = mongoDBManager.getCollection(BACKUP_COLLECTION_NAME);

            if (this.collection == null) {
                throw new RuntimeException("Failed to get MongoDB collection: " + COLLECTION_NAME);
            }

            // Test connection
            testConnection();

            repositoryInitialized.set(true);
            logger.info(" YakPlayerRepository initialized successfully (NO CACHING for cross-server sync)");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerRepository", e);
            repositoryInitialized.set(false);
            throw new RuntimeException("Repository initialization failed", e);
        }
    }

    /**
     * connection test
     */
    private void testConnection() {
        try {
            long count = collection.countDocuments();
            logger.info("Repository connection test successful. Document count: " + count);

            // Test document conversion with a sample
            Document testDoc = new Document("uuid", "test-uuid")
                    .append("username", "test-user")
                    .append("first_join", System.currentTimeMillis() / 1000)
                    .append("last_login", System.currentTimeMillis() / 1000);

            // Test that our converter can handle basic documents
            YakPlayer testPlayer = documentConverter.documentToPlayer(testDoc);
            if (testPlayer != null) {
                logger.info("Document converter test successful");
            } else {
                logger.warning("Document converter test failed with sample data");
            }

        } catch (Exception e) {
            throw new RuntimeException("Repository connection test failed", e);
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

            // ALWAYS load fresh from database - NO CACHING
            return performDatabaseFind(id);
        });
    }

    /**
     * database find operation with better error recovery
     */
    private Optional<YakPlayer> performDatabaseFind(UUID id) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.fine("Loading FRESH player data from database (attempt " + attempt + "): " + id);

                Document doc = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find(Filters.eq("uuid", id.toString())).first()
                );

                if (doc != null) {
                    logger.info("Found FRESH player document in database: " + id);

                    // Enhanced document validation before conversion
                    if (!documentConverter.validateDocument(doc)) {
                        logger.warning("Document validation failed for player: " + id);
                        // Try to repair the document
                        doc = documentConverter.repairDocument(doc);
                        if (doc == null) {
                            logger.severe("Document repair failed for player: " + id);
                            return Optional.empty();
                        }
                    }

                    YakPlayer player = documentConverter.documentToPlayer(doc);

                    if (player != null) {
                        logger.info("Successfully loaded FRESH player data from database: " + id);
                        return Optional.of(player);
                    } else {
                        logger.warning("Failed to convert document to player: " + id);
                        // Create emergency backup of corrupted document
                        createCorruptedDataBackup(doc, id);
                    }
                } else {
                    logger.fine("No document found for player: " + id);
                    return Optional.empty();
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error finding player by UUID: " + id + " (attempt " + attempt + ")", e);

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.warning("Failed to find player after " + MAX_RETRY_ATTEMPTS + " attempts: " + id);
        return Optional.empty();
    }

    @Override
    public CompletableFuture<YakPlayer> save(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            if (player == null) {
                logger.warning("Attempted to save null player");
                return null;
            }

            if (!validatePlayerData(player)) {
                logger.warning("Invalid player data for " + player.getUsername());
                createLocalBackup(player);
                return player;
            }

            if (!repositoryInitialized.get()) {
                logger.warning("Repository not initialized - creating local backup only: " + player.getUsername());
                createLocalBackup(player);
                return player;
            }

            return savePlayerToDatabase(player);
        });
    }

    /**
     * save operation with better validation
     */
    private YakPlayer savePlayerToDatabase(YakPlayer player) {
        if (player == null || !validatePlayerData(player)) {
            return player;
        }

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.info("Saving FRESH player to database (attempt " + attempt + "): " + player.getUsername());

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

                // Create backup before saving
                createDatabaseBackup(player, doc);

                // Perform save with upsert
                MongoDBManager.getInstance().performSafeOperation(() -> {
                    collection.replaceOne(
                            Filters.eq("uuid", player.getUUID().toString()),
                            doc,
                            new ReplaceOptions().upsert(true)
                    );
                    return null;
                });

                logger.info("Successfully saved FRESH player to database: " + player.getUsername());
                return player;

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error saving player: " + player.getUUID() + " (attempt " + attempt + ")", e);

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    createLocalBackup(player);
                    logger.severe("Failed to save player after " + MAX_RETRY_ATTEMPTS + " attempts: " + player.getUsername());
                }
            }
        }

        return player;
    }

    /**
     * synchronous save for critical scenarios
     */
    public YakPlayer saveSync(YakPlayer player) {
        if (player == null || !validatePlayerData(player)) {
            if (player != null) {
                createLocalBackup(player);
            }
            return player;
        }

        if (!repositoryInitialized.get()) {
            createLocalBackup(player);
            logger.warning("Could not sync save player - repository not initialized: " + player.getUsername());
            return player;
        }

        try {
            Document doc = documentConverter.playerToDocument(player);
            if (doc == null) {
                logger.severe("Failed to convert player to document during sync save: " + player.getUsername());
                createLocalBackup(player);
                return player;
            }

            // Enhanced validation
            if (!documentConverter.validateDocument(doc)) {
                logger.warning("Generated document failed validation during sync save for " + player.getUsername());
                createLocalBackup(player);
                return player;
            }

            // Create backup and save
            createDatabaseBackup(player, doc);
            collection.replaceOne(
                    Filters.eq("uuid", player.getUUID().toString()),
                    doc,
                    new ReplaceOptions().upsert(true)
            );

            logger.info("Successfully performed IMMEDIATE sync save for player: " + player.getUsername());
            return player;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during sync save for player: " + player.getUUID(), e);
            createLocalBackup(player);
            return player;
        }
    }

    @Override
    public CompletableFuture<List<YakPlayer>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<YakPlayer> players = new ArrayList<>();

            if (!repositoryInitialized.get()) {
                logger.severe("Repository not initialized - cannot find all players");
                return players;
            }

            try {
                logger.info("Loading all players FRESH from database...");

                FindIterable<Document> docs = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find().batchSize(100)
                );

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

                    logger.info("Loaded " + loadedCount + " players FRESH from database, " +
                            errorCount + " errors, " + repairedCount + " repaired");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error finding all players", e);
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

            try {
                DeleteResult result = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.deleteOne(Filters.eq("uuid", id.toString()))
                );

                boolean success = result != null && result.getDeletedCount() > 0;
                if (success) {
                    logger.info("Successfully deleted player: " + id);
                } else {
                    logger.warning("No player found to delete: " + id);
                }
                return success;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error deleting player: " + id, e);
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

            try {
                Long count = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.countDocuments(Filters.eq("uuid", id.toString()))
                );

                return count != null && count > 0;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error checking if player exists: " + id, e);
                return false;
            }
        });
    }

    /**
     * player data validation
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

        // Enhanced data validation and auto-correction
        boolean needsCorrection = false;

        if (player.getBankGems() < 0) {
            logger.warning("Player validation: negative bank gems for " + player.getUsername() + ", resetting to 0");
            player.setBankGems(0);
            needsCorrection = true;
        }

        if (player.getLevel() < 1 || player.getLevel() > 200) {
            logger.warning("Player validation: invalid level for " + player.getUsername() + ", resetting to 1");
            player.setLevel(1);
            needsCorrection = true;
        }

        if (player.getHealth() <= 0 || player.getHealth() > 2048) {
            logger.warning("Player validation: invalid health for " + player.getUsername() + ", resetting to 20");
            player.setHealth(20.0);
            needsCorrection = true;
        }

        if (player.getMaxHealth() <= 0 || player.getMaxHealth() > 2048) {
            logger.warning("Player validation: invalid max health for " + player.getUsername() + ", resetting to 20");
            player.setMaxHealth(20.0);
            needsCorrection = true;
        }

        if (player.getFoodLevel() < 0 || player.getFoodLevel() > 20) {
            logger.warning("Player validation: invalid food level for " + player.getUsername() + ", resetting to 20");
            player.setFoodLevel(20);
            needsCorrection = true;
        }

        if (needsCorrection) {
            logger.info("Applied data corrections to player: " + player.getUsername());
        }

        return true;
    }

    /**
     * local backup creation
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
                File backupFile = new File(playerDir, timestamp + ".json");
                Files.write(backupFile.toPath(), doc.toJson().getBytes());
                logger.fine("Created local backup for player: " + player.getUsername());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create local backup for player " + player.getUsername(), e);
        }
    }

    /**
     * database backup creation
     */
    private void createDatabaseBackup(YakPlayer player, Document doc) {
        if (backupCollection == null || player == null || doc == null) {
            return;
        }

        try {
            Document backupDoc = new Document(doc);
            backupDoc.append("timestamp", System.currentTimeMillis() / 1000);
            backupDoc.append("backup_reason", "auto_save");
            backupDoc.append("backup_version", "_v2");

            MongoDBManager.getInstance().performSafeOperation(() -> {
                backupCollection.insertOne(backupDoc);
                return null;
            });

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
            logger.warning("Created corrupted data backup: " + corruptedFile.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create corrupted data backup", e);
        }
    }

    /**
     * Public API methods
     */
    public boolean isInitialized() {
        return repositoryInitialized.get() && collection != null;
    }

    public void shutdown() {
        logger.info("Shutting down YakPlayerRepository ...");
        logger.info("Repository shutdown completed - no cache to clear");
    }

    /**
     * Document conversion helper class with improved error handling and backwards compatibility
     */
    private class EnhancedDocumentConverter {

        /**
         * document validation
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

            return true;
        }

        /**
         * document repair
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

                Double health = repaired.getDouble("health");
                if (health == null || health <= 0 || health > 2048) {
                    repaired.append("health", 20.0);
                    wasRepaired = true;
                }

                if (wasRepaired) {
                    logger.info("Successfully repaired document for UUID: " + repaired.getString("uuid"));
                    return repaired;
                }

                return repaired;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to repair document", e);
                return null;
            }
        }

        /**
         * document to player conversion with comprehensive error handling
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

                // Load bank access log safely
                List<String> bankLog = safeGetStringList(doc, "bank_access_log");
                if (bankLog != null && !bankLog.isEmpty()) {
                    player.getBankAccessLog().addAll(bankLog);
                }

                // Load bank inventory with enhanced safety
                Document bankItems = safeGetDocument(doc, "bank_inventory");
                if (bankItems != null) {
                    for (String key : bankItems.keySet()) {
                        try {
                            int page = Integer.parseInt(key);
                            String serializedData = bankItems.getString(key);
                            if (serializedData != null && !serializedData.trim().isEmpty()) {
                                player.setSerializedBankItems(page, serializedData);
                            }
                        } catch (NumberFormatException e) {
                            logger.warning("Invalid bank page number: " + key);
                        } catch (Exception e) {
                            logger.warning("Error loading bank inventory page " + key + ": " + e.getMessage());
                        }
                    }
                }

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

                // Load notification settings safely
                Document notificationSettings = safeGetDocument(doc, "notification_settings");
                if (notificationSettings != null) {
                    for (String key : notificationSettings.keySet()) {
                        try {
                            Boolean value = notificationSettings.getBoolean(key);
                            if (value != null) {
                                player.setNotificationSetting(key, value);
                            }
                        } catch (Exception e) {
                            logger.fine("Error loading notification setting " + key + ": " + e.getMessage());
                        }
                    }
                }

                // Load quest system data
                player.setCurrentQuest(safeGetString(doc, "current_quest", ""));
                player.setQuestProgress(safeGetInteger(doc, "quest_progress", 0));
                player.setQuestPoints(safeGetInteger(doc, "quest_points", 0));
                player.setDailyQuestsCompleted(safeGetInteger(doc, "daily_quests_completed", 0));
                player.setLastDailyQuestReset(safeGetLong(doc, "last_daily_quest_reset", 0L));

                // Load profession data
                player.setPickaxeLevel(safeGetInteger(doc, "pickaxe_level", 0));
                player.setFishingLevel(safeGetInteger(doc, "fishing_level", 0));
                player.setMiningXp(safeGetInteger(doc, "mining_xp", 0));
                player.setFishingXp(safeGetInteger(doc, "fishing_xp", 0));
                player.setFarmingLevel(safeGetInteger(doc, "farming_level", 0));
                player.setFarmingXp(safeGetInteger(doc, "farming_xp", 0));
                player.setWoodcuttingLevel(safeGetInteger(doc, "woodcutting_level", 0));
                player.setWoodcuttingXp(safeGetInteger(doc, "woodcutting_xp", 0));

                // Load PvP stats
                player.setT1Kills(safeGetInteger(doc, "t1_kills", 0));
                player.setT2Kills(safeGetInteger(doc, "t2_kills", 0));
                player.setT3Kills(safeGetInteger(doc, "t3_kills", 0));
                player.setT4Kills(safeGetInteger(doc, "t4_kills", 0));
                player.setT5Kills(safeGetInteger(doc, "t5_kills", 0));
                player.setT6Kills(safeGetInteger(doc, "t6_kills", 0));
                player.setKillStreak(safeGetInteger(doc, "kill_streak", 0));
                player.setBestKillStreak(safeGetInteger(doc, "best_kill_streak", 0));
                player.setPvpRating(Math.max(0, safeGetInteger(doc, "pvp_rating", 1000)));

                // Load world boss tracking safely
                loadWorldBossData(doc, player);

                // Load social settings
                player.setTradeDisabled(safeGetBoolean(doc, "trade_disabled", false));
                List<String> buddiesList = safeGetStringList(doc, "buddies");
                if (buddiesList != null) {
                    player.setBuddies(buddiesList);
                }
                List<String> blockedList = safeGetStringList(doc, "blocked_players");
                if (blockedList != null) {
                    player.getBlockedPlayers().addAll(blockedList);
                }
                player.setEnergyDisabled(safeGetBoolean(doc, "energy_disabled", false));

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

                // Load player stats with validation
                double health = Math.max(0.1, Math.min(2048, safeGetDouble(doc, "health", 20.0)));
                double maxHealth = Math.max(1, Math.min(2048, safeGetDouble(doc, "max_health", 20.0)));
                player.setHealth(Math.min(health, maxHealth)); // Ensure health doesn't exceed max
                player.setMaxHealth(maxHealth);
                player.setFoodLevel(Math.max(0, Math.min(20, safeGetInteger(doc, "food_level", 20))));
                player.setSaturation(Math.max(0, Math.min(20, safeGetFloat(doc, "saturation", 5.0f))));
                player.setXpLevel(Math.max(0, Math.min(21863, safeGetInteger(doc, "xp_level", 0))));
                player.setXpProgress(Math.max(0, Math.min(1, safeGetFloat(doc, "xp_progress", 0.0f))));
                player.setTotalExperience(Math.max(0, safeGetInteger(doc, "total_experience", 0)));
                player.setBedSpawnLocation(safeGetString(doc, "bed_spawn_location", null));
                player.setGameMode(safeGetString(doc, "gamemode", "SURVIVAL"));

                // Load achievement and reward tracking
                List<String> achievementsList = safeGetStringList(doc, "achievements");
                if (achievementsList != null) {
                    player.getAchievements().addAll(achievementsList);
                }
                player.setAchievementPoints(safeGetInteger(doc, "achievement_points", 0));
                List<String> dailyRewardsList = safeGetStringList(doc, "daily_rewards_claimed");
                if (dailyRewardsList != null) {
                    player.getDailyRewardsClaimed().addAll(dailyRewardsList);
                }
                player.setLastDailyReward(safeGetLong(doc, "last_daily_reward", 0L));

                // Load event participation tracking
                loadEventData(doc, player);

                // Load combat statistics
                player.setDamageDealt(safeGetLong(doc, "damage_dealt", 0L));
                player.setDamageTaken(safeGetLong(doc, "damage_taken", 0L));
                player.setDamageBlocked(safeGetLong(doc, "damage_blocked", 0L));
                player.setDamageDodged(safeGetLong(doc, "damage_dodged", 0L));

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

                logger.info("Successfully converted FRESH document to player: " + player.getUsername() +
                        " (State: " + player.getCombatLogoutState() + ")");
                return player;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error converting document to player", e);
                return null;
            }
        }

        /**
         * player to document conversion with comprehensive validation
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

                // Bank data
                doc.append("bank_pages", Math.max(1, Math.min(10, player.getBankPages())));
                doc.append("bank_authorized_users", new ArrayList<>(player.getBankAuthorizedUsers()));
                doc.append("bank_access_log", new ArrayList<>(player.getBankAccessLog()));

                // Bank inventory with safety
                Document bankItems = new Document();
                Map<Integer, String> bankItemsMap = player.getAllSerializedBankItems();
                if (bankItemsMap != null) {
                    for (Map.Entry<Integer, String> entry : bankItemsMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                            bankItems.append(entry.getKey().toString(), entry.getValue());
                        }
                    }
                }
                doc.append("bank_inventory", bankItems);

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

                // Notification settings with safety
                Document notificationSettings = new Document();
                Map<String, Boolean> notificationMap = player.getNotificationSettings();
                if (notificationMap != null) {
                    for (Map.Entry<String, Boolean> entry : notificationMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            notificationSettings.append(entry.getKey(), entry.getValue());
                        }
                    }
                }
                doc.append("notification_settings", notificationSettings);

                // Quest system
                doc.append("current_quest", player.getCurrentQuest() != null ? player.getCurrentQuest() : "");
                doc.append("quest_progress", player.getQuestProgress());
                doc.append("completed_quests", new ArrayList<>(player.getCompletedQuests()));
                doc.append("quest_points", player.getQuestPoints());
                doc.append("daily_quests_completed", player.getDailyQuestsCompleted());
                doc.append("last_daily_quest_reset", player.getLastDailyQuestReset());

                // Profession data
                doc.append("pickaxe_level", player.getPickaxeLevel());
                doc.append("fishing_level", player.getFishingLevel());
                doc.append("mining_xp", player.getMiningXp());
                doc.append("fishing_xp", player.getFishingXp());
                doc.append("farming_level", player.getFarmingLevel());
                doc.append("farming_xp", player.getFarmingXp());
                doc.append("woodcutting_level", player.getWoodcuttingLevel());
                doc.append("woodcutting_xp", player.getWoodcuttingXp());

                // PvP stats
                doc.append("t1_kills", player.getT1Kills());
                doc.append("t2_kills", player.getT2Kills());
                doc.append("t3_kills", player.getT3Kills());
                doc.append("t4_kills", player.getT4Kills());
                doc.append("t5_kills", player.getT5Kills());
                doc.append("t6_kills", player.getT6Kills());
                doc.append("kill_streak", player.getKillStreak());
                doc.append("best_kill_streak", player.getBestKillStreak());
                doc.append("pvp_rating", Math.max(0, player.getPvpRating()));

                // World Boss tracking with safety
                saveWorldBossData(doc, player);

                // Social settings
                doc.append("trade_disabled", player.isTradeDisabled());
                doc.append("buddies", new ArrayList<>(player.getBuddies()));
                doc.append("blocked_players", new ArrayList<>(player.getBlockedPlayers()));
                doc.append("energy_disabled", player.isEnergyDisabled());

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

                // Player stats with validation
                doc.append("health", Math.max(0.1, Math.min(2048, player.getHealth())));
                doc.append("max_health", Math.max(1, Math.min(2048, player.getMaxHealth())));
                doc.append("food_level", Math.max(0, Math.min(20, player.getFoodLevel())));
                doc.append("saturation", Math.max(0, Math.min(20, player.getSaturation())));
                doc.append("xp_level", Math.max(0, Math.min(21863, player.getXpLevel())));
                doc.append("xp_progress", Math.max(0, Math.min(1, player.getXpProgress())));
                doc.append("total_experience", Math.max(0, player.getTotalExperience()));
                doc.append("bed_spawn_location", player.getBedSpawnLocation());
                doc.append("gamemode", player.getGameMode() != null ? player.getGameMode() : "SURVIVAL");
                doc.append("active_potion_effects", new ArrayList<>(player.getActivePotionEffects()));

                // Achievement and reward tracking
                doc.append("achievements", new ArrayList<>(player.getAchievements()));
                doc.append("achievement_points", player.getAchievementPoints());
                doc.append("daily_rewards_claimed", new ArrayList<>(player.getDailyRewardsClaimed()));
                doc.append("last_daily_reward", player.getLastDailyReward());

                // Event participation tracking
                saveEventData(doc, player);

                // Combat statistics
                doc.append("damage_dealt", player.getDamageDealt());
                doc.append("damage_taken", player.getDamageTaken());
                doc.append("damage_blocked", player.getDamageBlocked());
                doc.append("damage_dodged", player.getDamageDodged());

                // Combat logout state management
                doc.append("combat_logout_state", player.getCombatLogoutState().name());

                logger.info("Successfully converted player to FRESH document: " + player.getUsername());
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
                return value != null ? value : defaultValue;
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
                    return ((Number) value).floatValue();
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

        private Document safeGetDocument(Document doc, String key) {
            try {
                return doc.get(key, Document.class);
            } catch (Exception e) {
                logger.fine("Error getting document field " + key + ": " + e.getMessage());
                return null;
            }
        }

        // Enhanced data loading methods
        private void loadWorldBossData(Document doc, YakPlayer player) {
            try {
                Document worldBossDamage = safeGetDocument(doc, "world_boss_damage");
                if (worldBossDamage != null) {
                    for (String key : worldBossDamage.keySet()) {
                        try {
                            Integer value = worldBossDamage.getInteger(key);
                            if (value != null && value >= 0) {
                                player.getWorldBossDamage().put(key, value);
                            }
                        } catch (Exception e) {
                            logger.fine("Error loading world boss damage for " + key + ": " + e.getMessage());
                        }
                    }
                }

                Document worldBossKills = safeGetDocument(doc, "world_boss_kills");
                if (worldBossKills != null) {
                    for (String key : worldBossKills.keySet()) {
                        try {
                            Integer value = worldBossKills.getInteger(key);
                            if (value != null && value >= 0) {
                                player.getWorldBossKills().put(key, value);
                            }
                        } catch (Exception e) {
                            logger.fine("Error loading world boss kills for " + key + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error loading world boss data: " + e.getMessage());
            }
        }

        private void loadEventData(Document doc, YakPlayer player) {
            try {
                Document eventsParticipated = safeGetDocument(doc, "events_participated");
                if (eventsParticipated != null) {
                    for (String key : eventsParticipated.keySet()) {
                        try {
                            Integer value = eventsParticipated.getInteger(key);
                            if (value != null && value >= 0) {
                                player.getEventsParticipated().put(key, value);
                            }
                        } catch (Exception e) {
                            logger.fine("Error loading events participated for " + key + ": " + e.getMessage());
                        }
                    }
                }

                Document eventWins = safeGetDocument(doc, "event_wins");
                if (eventWins != null) {
                    for (String key : eventWins.keySet()) {
                        try {
                            Integer value = eventWins.getInteger(key);
                            if (value != null && value >= 0) {
                                player.getEventWins().put(key, value);
                            }
                        } catch (Exception e) {
                            logger.fine("Error loading event wins for " + key + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error loading event data: " + e.getMessage());
            }
        }

        // Enhanced data saving methods
        private void saveWorldBossData(Document doc, YakPlayer player) {
            try {
                Document worldBossDamage = new Document();
                Map<String, Integer> damageMap = player.getWorldBossDamage();
                if (damageMap != null) {
                    for (Map.Entry<String, Integer> entry : damageMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null && entry.getValue() >= 0) {
                            worldBossDamage.append(entry.getKey(), entry.getValue());
                        }
                    }
                }
                doc.append("world_boss_damage", worldBossDamage);

                Document worldBossKills = new Document();
                Map<String, Integer> killsMap = player.getWorldBossKills();
                if (killsMap != null) {
                    for (Map.Entry<String, Integer> entry : killsMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null && entry.getValue() >= 0) {
                            worldBossKills.append(entry.getKey(), entry.getValue());
                        }
                    }
                }
                doc.append("world_boss_kills", worldBossKills);
            } catch (Exception e) {
                logger.warning("Error saving world boss data: " + e.getMessage());
            }
        }

        private void saveEventData(Document doc, YakPlayer player) {
            try {
                Document eventsParticipated = new Document();
                Map<String, Integer> eventsMap = player.getEventsParticipated();
                if (eventsMap != null) {
                    for (Map.Entry<String, Integer> entry : eventsMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null && entry.getValue() >= 0) {
                            eventsParticipated.append(entry.getKey(), entry.getValue());
                        }
                    }
                }
                doc.append("events_participated", eventsParticipated);

                Document eventWins = new Document();
                Map<String, Integer> winsMap = player.getEventWins();
                if (winsMap != null) {
                    for (Map.Entry<String, Integer> entry : winsMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null && entry.getValue() >= 0) {
                            eventWins.append(entry.getKey(), entry.getValue());
                        }
                    }
                }
                doc.append("event_wins", eventWins);
            } catch (Exception e) {
                logger.warning("Error saving event data: " + e.getMessage());
            }
        }
    }
}