package com.rednetty.server.core.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simplified YakPlayer repository with reliable synchronous operations
 */
public class YakPlayerRepository implements Repository<YakPlayer, UUID> {
    private static final String COLLECTION_NAME = "players";
    private static final String BACKUP_COLLECTION_NAME = "players_backup";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // Core dependencies
    private final Logger logger;
    private final Plugin plugin;
    private final File backupDir;
    private final DocumentConverter documentConverter;

    // Database collections
    private volatile MongoCollection<Document> collection;
    private volatile MongoCollection<Document> backupCollection;

    // Repository state
    private final AtomicBoolean repositoryInitialized = new AtomicBoolean(false);

    // Simple caching
    private final Map<UUID, YakPlayer> playerCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuidCache = new ConcurrentHashMap<>();

    // Background tasks
    private int cacheCleanupTaskId = -1;
    private int autoSaveTaskId = -1;

    public YakPlayerRepository() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.documentConverter = new DocumentConverter();

        // Create backup directory
        this.backupDir = new File(plugin.getDataFolder(), "backups/players");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // Initialize repository
        initializeRepository();
    }

    /**
     * Simple synchronous repository initialization
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
            logger.info("YakPlayerRepository initialized successfully");

            // Start background tasks
            startBackgroundTasks();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerRepository", e);
            repositoryInitialized.set(false);
            throw new RuntimeException("Repository initialization failed", e);
        }
    }

    /**
     * Test repository connection
     */
    private void testConnection() {
        try {
            long count = collection.countDocuments();
            logger.info("Repository connection test successful. Document count: " + count);
        } catch (Exception e) {
            throw new RuntimeException("Repository connection test failed", e);
        }
    }

    /**
     * Start background maintenance tasks
     */
    private void startBackgroundTasks() {
        // Cache cleanup task
        cacheCleanupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::performCacheCleanup,
                20L * 60 * 5, // 5 minutes initial delay
                20L * 60 * 5  // 5 minutes interval
        ).getTaskId();

        // Auto-save dirty players task
        autoSaveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::performAutoSave,
                20L * 60,     // 1 minute initial delay
                20L * 60 * 3  // 3 minutes interval
        ).getTaskId();

        logger.info("Repository background tasks started");
    }

    @Override
    public CompletableFuture<Optional<YakPlayer>> findById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            if (id == null) {
                logger.warning("Attempted to find player with null UUID");
                return Optional.empty();
            }

            // Check cache first
            YakPlayer cachedPlayer = playerCache.get(id);
            if (cachedPlayer != null) {
                logger.fine("Found player in cache: " + id);
                return Optional.of(cachedPlayer);
            }

            if (!repositoryInitialized.get()) {
                logger.severe("Repository not initialized - cannot find player: " + id);
                return Optional.empty();
            }

            return performDatabaseFind(id);
        });
    }

    /**
     * Perform database find operation with retries
     */
    private Optional<YakPlayer> performDatabaseFind(UUID id) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.fine("Finding player by UUID (attempt " + attempt + "): " + id);

                Document doc = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find(Filters.eq("uuid", id.toString())).first()
                );

                if (doc != null) {
                    logger.fine("Found player document in database: " + id);
                    YakPlayer player = documentConverter.documentToPlayer(doc);

                    if (player != null) {
                        cachePlayer(player);
                        logger.fine("Successfully loaded and cached player: " + id);
                        return Optional.of(player);
                    } else {
                        logger.warning("Failed to convert document to player: " + id);
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

            // Update cache
            cachePlayer(player);

            if (!repositoryInitialized.get()) {
                logger.warning("Repository not initialized - creating local backup only: " + player.getUsername());
                createLocalBackup(player);
                return player;
            }

            return savePlayerToDatabase(player);
        });
    }

    /**
     * Save player to database with retry logic
     */
    private YakPlayer savePlayerToDatabase(YakPlayer player) {
        if (player == null || !validatePlayerData(player)) {
            return player;
        }

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.fine("Saving player to database (attempt " + attempt + "): " + player.getUsername());

                Document doc = documentConverter.playerToDocument(player);
                if (doc == null) {
                    logger.severe("Failed to convert player to document: " + player.getUsername());
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

                logger.fine("Successfully saved player to database: " + player.getUsername());
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
     * Synchronous save for shutdown scenarios
     */
    public YakPlayer saveSync(YakPlayer player) {
        if (player == null || !validatePlayerData(player)) {
            if (player != null) {
                createLocalBackup(player);
            }
            return player;
        }

        cachePlayer(player);

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

            // Create backup and save
            createDatabaseBackup(player, doc);
            collection.replaceOne(
                    Filters.eq("uuid", player.getUUID().toString()),
                    doc,
                    new ReplaceOptions().upsert(true)
            );

            logger.fine("Successfully performed sync save for player: " + player.getUsername());
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
                logger.info("Loading all players from database...");

                FindIterable<Document> docs = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find().batchSize(100)
                );

                if (docs != null) {
                    int loadedCount = 0;
                    int errorCount = 0;

                    for (Document doc : docs) {
                        try {
                            YakPlayer player = documentConverter.documentToPlayer(doc);
                            if (player != null) {
                                players.add(player);
                                cachePlayer(player);
                                loadedCount++;
                            } else {
                                errorCount++;
                            }
                        } catch (Exception e) {
                            errorCount++;
                            logger.log(Level.WARNING, "Error processing document during findAll", e);
                        }
                    }

                    logger.info("Loaded " + loadedCount + " players successfully, " + errorCount + " errors");
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

            // Create backup before deletion
            YakPlayer cachedPlayer = playerCache.get(id);
            if (cachedPlayer != null) {
                createLocalBackup(cachedPlayer);
            }

            // Remove from cache
            removeFromCache(id);

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

            // Check cache first
            if (playerCache.containsKey(id)) {
                return true;
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
     * Cache player data
     */
    private void cachePlayer(YakPlayer player) {
        if (player == null || player.getUUID() == null) {
            logger.warning("Attempted to cache null player or player with null UUID");
            return;
        }

        playerCache.put(player.getUUID(), player);

        // Update name cache
        if (player.getUsername() != null) {
            nameToUuidCache.put(player.getUsername().toLowerCase(), player.getUUID());
        }

        logger.fine("Cached player: " + player.getUUID() + " (" + player.getUsername() + ")");
    }

    /**
     * Remove player from cache
     */
    private void removeFromCache(UUID playerId) {
        YakPlayer removed = playerCache.remove(playerId);
        if (removed != null) {
            if (removed.getUsername() != null) {
                nameToUuidCache.remove(removed.getUsername().toLowerCase());
            }
        }
    }

    /**
     * Perform cache cleanup
     */
    private void performCacheCleanup() {
        int removedCount = 0;

        // Remove offline players from cache
        List<UUID> toRemove = new ArrayList<>();
        for (UUID playerId : playerCache.keySet()) {
            if (Bukkit.getPlayer(playerId) == null) {
                toRemove.add(playerId);
            }
        }

        for (UUID playerId : toRemove) {
            removeFromCache(playerId);
            removedCount++;
        }

        if (removedCount > 0) {
            logger.fine("Cache cleanup: removed " + removedCount + " offline players");
        }
    }

    /**
     * Perform auto-save of cached players
     */
    private void performAutoSave() {
        int savedCount = 0;
        for (YakPlayer player : playerCache.values()) {
            if (Bukkit.getPlayer(player.getUUID()) != null) {
                try {
                    savePlayerToDatabase(player);
                    savedCount++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error during auto-save", e);
                }
            }
        }

        if (savedCount > 0) {
            logger.fine("Auto-save completed: " + savedCount + " players saved");
        }
    }

    /**
     * Validate player data
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

        // Fix common data issues
        if (player.getGems() < 0) {
            logger.warning("Player validation: negative gems for " + player.getUsername() + ", resetting to 0");
            player.setGems(0);
        }

        if (player.getLevel() < 1) {
            logger.warning("Player validation: invalid level for " + player.getUsername() + ", resetting to 1");
            player.setLevel(1);
        }

        if (player.getHealth() <= 0) {
            logger.warning("Player validation: invalid health for " + player.getUsername() + ", resetting to 20");
            player.setHealth(20.0);
        }

        return true;
    }

    /**
     * Create local backup
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
     * Create database backup
     */
    private void createDatabaseBackup(YakPlayer player, Document doc) {
        if (backupCollection == null || player == null || doc == null) {
            return;
        }

        try {
            Document backupDoc = new Document(doc);
            backupDoc.append("timestamp", System.currentTimeMillis() / 1000);
            backupDoc.append("backup_reason", "auto_save");

            MongoDBManager.getInstance().performSafeOperation(() -> {
                backupCollection.insertOne(backupDoc);
                return null;
            });

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create database backup for player " + player.getUsername(), e);
        }
    }

    /**
     * Public API methods
     */
    public boolean isInitialized() {
        return repositoryInitialized.get() && collection != null;
    }

    public YakPlayer getFromCache(UUID playerId) {
        return playerCache.get(playerId);
    }

    public YakPlayer getFromCache(String playerName) {
        UUID playerId = nameToUuidCache.get(playerName.toLowerCase());
        return playerId != null ? getFromCache(playerId) : null;
    }

    public void shutdown() {
        logger.info("Shutting down YakPlayerRepository...");

        // Cancel background tasks
        if (cacheCleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cacheCleanupTaskId);
        }
        if (autoSaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        }

        // Save all cached players
        int savedCount = 0;
        for (YakPlayer player : playerCache.values()) {
            try {
                saveSync(player);
                savedCount++;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error saving player during shutdown", e);
            }
        }

        logger.info("Repository shutdown completed. Saved " + savedCount + " players.");
    }

    /**
     * Document conversion helper class
     */
    private class DocumentConverter {

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

                UUID uuid = UUID.fromString(uuidStr);
                YakPlayer player = new YakPlayer(uuid);

                // Load basic information
                player.setUsername(doc.getString("username") != null ? doc.getString("username") : "Unknown");
                player.setLastLogin(doc.getLong("last_login") != null ? doc.getLong("last_login") : System.currentTimeMillis() / 1000);
                player.setLastLogout(doc.getLong("last_logout") != null ? doc.getLong("last_logout") : 0);
                player.setFirstJoin(doc.getLong("first_join") != null ? doc.getLong("first_join") : System.currentTimeMillis() / 1000);
                player.setTotalPlaytime(doc.getLong("total_playtime") != null ? doc.getLong("total_playtime") : 0L);

                String ipAddress = doc.getString("ip_address");
                if (ipAddress != null) {
                    player.setIpAddress(ipAddress);
                }

                // Load progression
                player.setLevel(doc.getInteger("level", 1));
                player.setExp(doc.getInteger("exp", 0));
                player.setMonsterKills(doc.getInteger("monster_kills", 0));
                player.setPlayerKills(doc.getInteger("player_kills", 0));
                player.setDeaths(doc.getInteger("deaths", 0));
                player.setOreMined(doc.getInteger("ore_mined", 0));
                player.setFishCaught(doc.getInteger("fish_caught", 0));
                player.setBlocksBroken(doc.getInteger("blocks_broken", 0));
                player.setDistanceTraveled(doc.getDouble("distance_traveled") != null ? doc.getDouble("distance_traveled") : 0.0);

                // Load economic data
                player.setGems(doc.getInteger("gems", 0));
                player.setBankGems(doc.getInteger("bank_gems", 0));
                player.setEliteShards(doc.getInteger("elite_shards", 0));
                player.setTotalGemsEarned(doc.getLong("total_gems_earned") != null ? doc.getLong("total_gems_earned") : 0L);
                player.setTotalGemsSpent(doc.getLong("total_gems_spent") != null ? doc.getLong("total_gems_spent") : 0L);

                // Load bank data
                player.setBankPages(doc.getInteger("bank_pages", 1));
                player.setBankAuthorizedUsers(doc.getList("bank_authorized_users", String.class, new ArrayList<>()));

                List<String> bankLog = doc.getList("bank_access_log", String.class);
                if (bankLog != null) {
                    player.getBankAccessLog().addAll(bankLog);
                }

                // Load bank inventory
                Document bankItems = doc.get("bank_inventory", Document.class);
                if (bankItems != null) {
                    for (String key : bankItems.keySet()) {
                        try {
                            int page = Integer.parseInt(key);
                            String serializedData = bankItems.getString(key);
                            if (serializedData != null) {
                                player.setSerializedBankItems(page, serializedData);
                            }
                        } catch (NumberFormatException e) {
                            logger.warning("Invalid bank page number: " + key);
                        }
                    }
                }

                // Load all other player data
                loadPlayerMiscData(player, doc);

                logger.fine("Successfully converted document to player: " + player.getUsername());
                return player;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error converting document to player", e);
                return null;
            }
        }

        public Document playerToDocument(YakPlayer player) {
            if (player == null || player.getUUID() == null) {
                logger.warning("Cannot convert null player or player with null UUID to document");
                return null;
            }

            try {
                Document doc = new Document();

                // Basic identification
                doc.append("uuid", player.getUUID().toString());
                doc.append("username", player.getUsername());
                doc.append("last_login", player.getLastLogin());
                doc.append("last_logout", player.getLastLogout());
                doc.append("first_join", player.getFirstJoin());
                doc.append("total_playtime", player.getTotalPlaytime());

                if (player.getIpAddress() != null) {
                    doc.append("ip_address", player.getIpAddress());
                }

                // Progression
                doc.append("level", player.getLevel());
                doc.append("exp", player.getExp());
                doc.append("monster_kills", player.getMonsterKills());
                doc.append("player_kills", player.getPlayerKills());
                doc.append("deaths", player.getDeaths());
                doc.append("ore_mined", player.getOreMined());
                doc.append("fish_caught", player.getFishCaught());
                doc.append("blocks_broken", player.getBlocksBroken());
                doc.append("distance_traveled", player.getDistanceTraveled());

                // Economic data
                doc.append("gems", player.getGems());
                doc.append("bank_gems", player.getBankGems());
                doc.append("elite_shards", player.getEliteShards());
                doc.append("total_gems_earned", player.getTotalGemsEarned());
                doc.append("total_gems_spent", player.getTotalGemsSpent());

                // Bank data
                doc.append("bank_pages", player.getBankPages());
                doc.append("bank_authorized_users", player.getBankAuthorizedUsers());
                doc.append("bank_access_log", player.getBankAccessLog());

                // Bank inventory
                Document bankItems = new Document();
                Map<Integer, String> bankItemsMap = player.getAllSerializedBankItems();
                if (bankItemsMap != null) {
                    for (Map.Entry<Integer, String> entry : bankItemsMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            bankItems.append(entry.getKey().toString(), entry.getValue());
                        }
                    }
                }
                doc.append("bank_inventory", bankItems);

                // Save all other player data
                savePlayerMiscData(player, doc);

                logger.fine("Successfully converted player to document: " + player.getUsername());
                return doc;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error converting player to document: " + player.getUsername(), e);
                return null;
            }
        }

        // Helper method to load miscellaneous player data
        private void loadPlayerMiscData(YakPlayer player, Document doc) {
            // Alignment
            player.setAlignment(doc.getString("alignment") != null ? doc.getString("alignment") : "LAWFUL");
            player.setChaoticTime(doc.getLong("chaotic_time") != null ? doc.getLong("chaotic_time") : 0);
            player.setNeutralTime(doc.getLong("neutral_time") != null ? doc.getLong("neutral_time") : 0);
            player.setAlignmentChanges(doc.getInteger("alignment_changes", 0));

            // Moderation
            player.setRank(doc.getString("rank") != null ? doc.getString("rank") : "DEFAULT");
            player.setBanned(doc.getBoolean("banned", false));
            player.setBanReason(doc.getString("ban_reason") != null ? doc.getString("ban_reason") : "");
            player.setBanExpiry(doc.getLong("ban_expiry") != null ? doc.getLong("ban_expiry") : 0);
            player.setMuteTime(doc.getInteger("muted", 0));
            player.setWarnings(doc.getInteger("warnings", 0));
            player.setLastWarning(doc.getLong("last_warning") != null ? doc.getLong("last_warning") : 0);

            // Chat
            player.setChatTag(doc.getString("chat_tag") != null ? doc.getString("chat_tag") : "DEFAULT");
            player.setUnlockedChatTags(doc.getList("unlocked_chat_tags", String.class, new ArrayList<>()));
            player.setChatColor(doc.getString("chat_color") != null ? doc.getString("chat_color") : "WHITE");

            // Mount and Guild
            player.setHorseTier(doc.getInteger("horse_tier", 0));
            player.setHorseName(doc.getString("horse_name") != null ? doc.getString("horse_name") : "");
            player.setGuildName(doc.getString("guild_name") != null ? doc.getString("guild_name") : "");
            player.setGuildRank(doc.getString("guild_rank") != null ? doc.getString("guild_rank") : "");
            player.setGuildContribution(doc.getInteger("guild_contribution", 0));

            // Toggle settings
            player.setToggleSettings(new HashSet<>(doc.getList("toggle_settings", String.class, new ArrayList<>())));

            // Location
            player.setWorld(doc.getString("world"));
            player.setLocationX(doc.getDouble("location_x") != null ? doc.getDouble("location_x") : 0.0);
            player.setLocationY(doc.getDouble("location_y") != null ? doc.getDouble("location_y") : 0.0);
            player.setLocationZ(doc.getDouble("location_z") != null ? doc.getDouble("location_z") : 0.0);
            player.setLocationYaw(doc.get("location_yaw") instanceof Number ? ((Number) doc.get("location_yaw")).floatValue() : 0.0f);
            player.setLocationPitch(doc.get("location_pitch") instanceof Number ? ((Number) doc.get("location_pitch")).floatValue() : 0.0f);

            // Inventory
            player.setSerializedInventory(doc.getString("inventory_contents"));
            player.setSerializedArmor(doc.getString("armor_contents"));
            player.setSerializedEnderChest(doc.getString("ender_chest_contents"));
            player.setSerializedOffhand(doc.getString("offhand_item"));

            // Stats
            player.setHealth(doc.getDouble("health") != null ? doc.getDouble("health") : 20.0);
            player.setMaxHealth(doc.getDouble("max_health") != null ? doc.getDouble("max_health") : 20.0);
            player.setFoodLevel(doc.getInteger("food_level", 20));
            player.setSaturation(doc.get("saturation") instanceof Number ? ((Number) doc.get("saturation")).floatValue() : 5.0f);
            player.setXpLevel(doc.getInteger("xp_level", 0));
            player.setXpProgress(doc.get("xp_progress") instanceof Number ? ((Number) doc.get("xp_progress")).floatValue() : 0.0f);
            player.setTotalExperience(doc.getInteger("total_experience", 0));
            player.setBedSpawnLocation(doc.getString("bed_spawn_location"));
            player.setGameMode(doc.getString("gamemode") != null ? doc.getString("gamemode") : "SURVIVAL");
            player.setActivePotionEffects(doc.getList("active_potion_effects", String.class, new ArrayList<>()));

            // Social
            player.setTradeDisabled(doc.getBoolean("trade_disabled", false));
            player.setBuddies(doc.getList("buddies", String.class, new ArrayList<>()));
            player.setEnergyDisabled(doc.getBoolean("energy_disabled", false));

            // PvP and combat
            player.setT1Kills(doc.getInteger("t1_kills", 0));
            player.setT2Kills(doc.getInteger("t2_kills", 0));
            player.setT3Kills(doc.getInteger("t3_kills", 0));
            player.setT4Kills(doc.getInteger("t4_kills", 0));
            player.setT5Kills(doc.getInteger("t5_kills", 0));
            player.setT6Kills(doc.getInteger("t6_kills", 0));
            player.setKillStreak(doc.getInteger("kill_streak", 0));
            player.setBestKillStreak(doc.getInteger("best_kill_streak", 0));
            player.setPvpRating(doc.getInteger("pvp_rating", 1000));

            // Professions
            player.setPickaxeLevel(doc.getInteger("pickaxe_level", 0));
            player.setFishingLevel(doc.getInteger("fishing_level", 0));
            player.setMiningXp(doc.getInteger("mining_xp", 0));
            player.setFishingXp(doc.getInteger("fishing_xp", 0));
            player.setFarmingLevel(doc.getInteger("farming_level", 0));
            player.setFarmingXP(doc.getInteger("farming_xp", 0));
            player.setWoodcuttingLevel(doc.getInteger("woodcutting_level", 0));
            player.setWoodcuttingXP(doc.getInteger("woodcutting_xp", 0));

            // Achievement data
            player.setAchievementPoints(doc.getInteger("achievement_points", 0));
            player.setLastDailyReward(doc.getLong("last_daily_reward") != null ? doc.getLong("last_daily_reward") : 0);
            player.setDamageDealt(doc.getLong("damage_dealt") != null ? doc.getLong("damage_dealt") : 0);
            player.setDamageTaken(doc.getLong("damage_taken") != null ? doc.getLong("damage_taken") : 0);
            player.setDamageBlocked(doc.getLong("damage_blocked") != null ? doc.getLong("damage_blocked") : 0);
            player.setDamageDodged(doc.getLong("damage_dodged") != null ? doc.getLong("damage_dodged") : 0);

            // Quest data
            player.setCurrentQuest(doc.getString("current_quest") != null ? doc.getString("current_quest") : "");
            player.setQuestProgress(doc.getInteger("quest_progress", 0));
            player.setCompletedQuests(doc.getList("completed_quests", String.class, new ArrayList<>()));
            player.setQuestPoints(doc.getInteger("quest_points", 0));
            player.setDailyQuestsCompleted(doc.getInteger("daily_quests_completed", 0));
            player.setLastDailyQuestReset(doc.getLong("last_daily_quest_reset") != null ? doc.getLong("last_daily_quest_reset") : 0);
        }

        // Helper method to save miscellaneous player data
        private void savePlayerMiscData(YakPlayer player, Document doc) {
            // Alignment
            doc.append("alignment", player.getAlignment());
            doc.append("chaotic_time", player.getChaoticTime());
            doc.append("neutral_time", player.getNeutralTime());
            doc.append("alignment_changes", player.getAlignmentChanges());

            // Moderation
            doc.append("rank", player.getRank());
            doc.append("banned", player.isBanned());
            doc.append("ban_reason", player.getBanReason());
            doc.append("ban_expiry", player.getBanExpiry());
            doc.append("muted", player.getMuteTime());
            doc.append("warnings", player.getWarnings());
            doc.append("last_warning", player.getLastWarning());

            // Chat
            doc.append("chat_tag", player.getChatTag());
            doc.append("unlocked_chat_tags", new ArrayList<>(player.getUnlockedChatTags()));
            doc.append("chat_color", player.getChatColor());

            // Mount and Guild
            doc.append("horse_tier", player.getHorseTier());
            doc.append("horse_name", player.getHorseName());
            doc.append("guild_name", player.getGuildName());
            doc.append("guild_rank", player.getGuildRank());
            doc.append("guild_contribution", player.getGuildContribution());

            // Toggle settings
            doc.append("toggle_settings", new ArrayList<>(player.getToggleSettings()));

            // Location
            doc.append("world", player.getWorld());
            doc.append("location_x", player.getLocationX());
            doc.append("location_y", player.getLocationY());
            doc.append("location_z", player.getLocationZ());
            doc.append("location_yaw", player.getLocationYaw());
            doc.append("location_pitch", player.getLocationPitch());

            // Inventory
            doc.append("inventory_contents", player.getSerializedInventory());
            doc.append("armor_contents", player.getSerializedArmor());
            doc.append("ender_chest_contents", player.getSerializedEnderChest());
            doc.append("offhand_item", player.getSerializedOffhand());

            // Stats
            doc.append("health", player.getHealth());
            doc.append("max_health", player.getMaxHealth());
            doc.append("food_level", player.getFoodLevel());
            doc.append("saturation", player.getSaturation());
            doc.append("xp_level", player.getXpLevel());
            doc.append("xp_progress", player.getXpProgress());
            doc.append("total_experience", player.getTotalExperience());
            doc.append("bed_spawn_location", player.getBedSpawnLocation());
            doc.append("gamemode", player.getGameMode());
            doc.append("active_potion_effects", player.getActivePotionEffects());

            // Social
            doc.append("trade_disabled", player.isTradeDisabled());
            doc.append("buddies", new ArrayList<>(player.getBuddies()));
            doc.append("energy_disabled", player.isEnergyDisabled());

            // PvP and combat
            doc.append("t1_kills", player.getT1Kills());
            doc.append("t2_kills", player.getT2Kills());
            doc.append("t3_kills", player.getT3Kills());
            doc.append("t4_kills", player.getT4Kills());
            doc.append("t5_kills", player.getT5Kills());
            doc.append("t6_kills", player.getT6Kills());
            doc.append("kill_streak", player.getKillStreak());
            doc.append("best_kill_streak", player.getBestKillStreak());
            doc.append("pvp_rating", player.getPvpRating());

            // Professions
            doc.append("pickaxe_level", player.getPickaxeLevel());
            doc.append("fishing_level", player.getFishingLevel());
            doc.append("mining_xp", player.getMiningXp());
            doc.append("fishing_xp", player.getFishingXp());
            doc.append("farming_level", player.getFarmingLevel());
            doc.append("farming_xp", player.getFarmingXP());
            doc.append("woodcutting_level", player.getWoodcuttingLevel());
            doc.append("woodcutting_xp", player.getWoodcuttingXP());

            // Achievement data
            doc.append("achievement_points", player.getAchievementPoints());
            doc.append("last_daily_reward", player.getLastDailyReward());
            doc.append("damage_dealt", player.getDamageDealt());
            doc.append("damage_taken", player.getDamageTaken());
            doc.append("damage_blocked", player.getDamageBlocked());
            doc.append("damage_dodged", player.getDamageDodged());

            // Quest data
            doc.append("current_quest", player.getCurrentQuest());
            doc.append("quest_progress", player.getQuestProgress());
            doc.append("completed_quests", new ArrayList<>(player.getCompletedQuests()));
            doc.append("quest_points", player.getQuestPoints());
            doc.append("daily_quests_completed", player.getDailyQuestsCompleted());
            doc.append("last_daily_quest_reset", player.getLastDailyQuestReset());
        }
    }
}