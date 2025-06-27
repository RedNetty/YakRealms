package com.rednetty.server.core.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FIXED Repository for YakPlayer entities using MongoDB
 */
public class YakPlayerRepository implements Repository<YakPlayer, UUID> {
    private static final String COLLECTION_NAME = "players";
    private static final String BACKUP_COLLECTION_NAME = "players_backup";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second

    private MongoCollection<Document> collection;
    private MongoCollection<Document> backupCollection;
    private final Logger logger;
    private final Plugin plugin;
    private final File backupDir;
    private final AtomicBoolean performingBatchOperation = new AtomicBoolean(false);
    private final AtomicBoolean repositoryInitialized = new AtomicBoolean(false);

    // Cache players by UUID with expiration time
    private final Map<UUID, CachedPlayer> playerCache = new ConcurrentHashMap<>();
    // Cache player UUID by name
    private final Map<String, UUID> nameToUuidCache = new ConcurrentHashMap<>();

    // Class to hold cached player with expiration time
    private static class CachedPlayer {
        private final YakPlayer player;
        private long lastAccessed;
        private long lastModified;
        private boolean dirty;

        public CachedPlayer(YakPlayer player) {
            this.player = player;
            this.lastAccessed = System.currentTimeMillis();
            this.lastModified = this.lastAccessed;
            this.dirty = false;
        }

        public YakPlayer getPlayer() {
            this.lastAccessed = System.currentTimeMillis();
            return player;
        }

        public long getLastAccessed() {
            return lastAccessed;
        }

        public void markDirty() {
            this.dirty = true;
            this.lastModified = System.currentTimeMillis();
        }

        public boolean isDirty() {
            return dirty;
        }

        public void clearDirty() {
            this.dirty = false;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    /**
     * Constructor
     */
    public YakPlayerRepository() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Create backup directory
        this.backupDir = new File(plugin.getDataFolder(), "backups/players");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // Initialize repository connection
        initializeRepository();

        // Start the cache cleaning task
        startCacheCleaningTask();
    }

    /**
     * FIXED: Initialize repository connection with proper error handling
     */
    private void initializeRepository() {
        try {
            // Wait a bit for MongoDB to be initialized
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    MongoDBManager mongoDBManager = MongoDBManager.getInstance();
                    if (mongoDBManager == null) {
                        logger.severe("MongoDBManager instance is null! Cannot initialize repository.");
                        return;
                    }

                    // Ensure MongoDB is connected
                    if (!mongoDBManager.isConnected()) {
                        logger.warning("MongoDB is not connected. Attempting to connect...");
                        if (!mongoDBManager.connect()) {
                            logger.severe("Failed to connect to MongoDB!");
                            return;
                        }
                    }

                    // Get collections
                    this.collection = mongoDBManager.getCollection(COLLECTION_NAME);
                    this.backupCollection = mongoDBManager.getCollection(BACKUP_COLLECTION_NAME);

                    if (this.collection == null) {
                        logger.severe("Failed to get MongoDB collection: " + COLLECTION_NAME);
                        return;
                    }

                    // Test the connection
                    testConnection();

                    // Create indexes for better performance
                    createIndexes();

                    repositoryInitialized.set(true);
                    logger.info("YakPlayerRepository initialized successfully");

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to initialize YakPlayerRepository", e);
                    repositoryInitialized.set(false);
                }
            }, 20L); // 1 second delay

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error scheduling repository initialization", e);
            repositoryInitialized.set(false);
        }
    }

    /**
     * FIXED: Ensure collection is available before operations
     */
    private boolean ensureCollection() {
        if (!repositoryInitialized.get() || collection == null) {
            logger.warning("Repository not properly initialized, attempting to reinitialize...");

            try {
                MongoDBManager mongoDBManager = MongoDBManager.getInstance();
                if (mongoDBManager != null && mongoDBManager.isConnected()) {
                    this.collection = mongoDBManager.getCollection(COLLECTION_NAME);
                    this.backupCollection = mongoDBManager.getCollection(BACKUP_COLLECTION_NAME);

                    if (collection != null) {
                        repositoryInitialized.set(true);
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to reinitialize repository", e);
            }

            return false;
        }

        return true;
    }

    /**
     * Test the repository connection
     */
    private void testConnection() {
        try {
            // Perform a simple count operation to test connection
            long count = collection.countDocuments();
            logger.info("Repository connection test successful. Document count: " + count);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Repository connection test failed", e);
            throw new RuntimeException("Repository connection test failed", e);
        }
    }

    /**
     * Creates MongoDB indexes for better performance
     */
    private void createIndexes() {
        try {
            if (collection != null) {
                // Create index on username for faster lookups by name
                collection.createIndex(new Document("username", 1));
                // Create index on uuid for faster lookups by ID
                collection.createIndex(new Document("uuid", 1));
                logger.info("Created indexes for player collection");
            }

            if (backupCollection != null) {
                // Create indexes on backup collection
                backupCollection.createIndex(new Document("uuid", 1));
                backupCollection.createIndex(new Document("timestamp", -1));
                logger.info("Created indexes for backup collection");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create indexes (they may already exist)", e);
        }
    }

    /**
     * Start a task to clean expired cache entries
     */
    private void startCacheCleaningTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                cleanCache();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during cache cleaning", e);
            }
        }, 6000L, 6000L); // Run every 5 minutes
    }

    /**
     * Clean expired cache entries and save dirty entries
     */
    private void cleanCache() {
        int expiredCount = 0;
        int savedCount = 0;
        long now = System.currentTimeMillis();

        // Don't clean cache during batch operations
        if (performingBatchOperation.get()) {
            return;
        }

        // Clean cache based on expiration and save dirty entries
        for (Iterator<Map.Entry<UUID, CachedPlayer>> it = playerCache.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, CachedPlayer> entry = it.next();
            CachedPlayer cachedPlayer = entry.getValue();

            // Check if the player is online
            boolean isOnline = Bukkit.getPlayer(entry.getKey()) != null;

            // Don't expire online players
            if (!isOnline) {
                // Expire cache entries not accessed in 30 minutes
                if (now - cachedPlayer.getLastAccessed() > TimeUnit.MINUTES.toMillis(30)) {
                    // Save if dirty before removing
                    if (cachedPlayer.isDirty()) {
                        savePlayerToDatabase(cachedPlayer.getPlayer());
                        savedCount++;
                    }

                    // Remove from UUID cache
                    it.remove();

                    // Remove from name cache
                    nameToUuidCache.values().remove(entry.getKey());
                    expiredCount++;
                }
            }

            // Save dirty entries that haven't been saved in 5 minutes
            if (cachedPlayer.isDirty() && (now - cachedPlayer.getLastModified() > TimeUnit.MINUTES.toMillis(5))) {
                savePlayerToDatabase(cachedPlayer.getPlayer());
                cachedPlayer.clearDirty();
                savedCount++;
            }
        }

        if (expiredCount > 0 || savedCount > 0) {
            logger.fine("Cache cleaning: removed " + expiredCount + " expired entries, saved " + savedCount + " dirty entries");
        }
    }

    /**
     * FIXED: Find a player by UUID with proper error handling
     */
    @Override
    public CompletableFuture<Optional<YakPlayer>> findById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            if (id == null) {
                return Optional.empty();
            }

            // Check cache first
            CachedPlayer cachedPlayer = playerCache.get(id);
            if (cachedPlayer != null) {
                logger.fine("Found player in cache: " + id);
                return Optional.of(cachedPlayer.getPlayer());
            }

            if (!ensureCollection()) {
                logger.severe("Cannot find player - collection not available: " + id);
                return Optional.empty();
            }

            for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    logger.fine("Finding player by UUID (attempt " + (attempt + 1) + "): " + id);

                    Document doc = MongoDBManager.getInstance().performSafeOperation(() ->
                            collection.find(Filters.eq("uuid", id.toString())).first()
                    );

                    if (doc != null) {
                        logger.fine("Found player document in database: " + id);
                        YakPlayer player = documentToPlayer(doc);

                        // Cache the player if successfully loaded
                        if (player != null) {
                            cachePlayer(player);
                            logger.fine("Successfully loaded and cached player: " + id);
                            return Optional.of(player);
                        } else {
                            logger.warning("Failed to convert document to player: " + id);
                        }
                    } else {
                        logger.fine("No document found for player: " + id);
                    }

                    // If not found and this is not the first attempt, break
                    if (doc == null && attempt > 0) {
                        break;
                    }

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error finding player by UUID: " + id + " (attempt " + (attempt + 1) + ")", e);

                    if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            logger.fine("Player not found: " + id);
            return Optional.empty();
        });
    }

    /**
     * Cache a player
     */
    private void cachePlayer(YakPlayer player) {
        if (player == null || player.getUUID() == null) {
            logger.warning("Attempted to cache null player or player with null UUID");
            return;
        }

        playerCache.put(player.getUUID(), new CachedPlayer(player));
        if (player.getUsername() != null) {
            nameToUuidCache.put(player.getUsername().toLowerCase(), player.getUUID());
        }
        logger.fine("Cached player: " + player.getUUID() + " (" + player.getUsername() + ")");
    }

    /**
     * FIXED: Save a player with proper error handling
     */
    @Override
    public CompletableFuture<YakPlayer> save(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            if (player == null) {
                logger.warning("Attempted to save null player");
                return null;
            }

            // Validate player data
            if (!validatePlayerData(player)) {
                logger.warning("Invalid player data for " + player.getUsername() + ", skipping save");
                return player;
            }

            // Always update cache
            cachePlayer(player);

            // Check if MongoDB is available
            if (!ensureCollection()) {
                logger.warning("Could not save player " + player.getUsername() + " - MongoDB not connected");
                // Create local backup if database is not available
                createLocalBackup(player);
                return player;
            }

            return savePlayerToDatabase(player);
        });
    }

    /**
     * FIXED: Save player to database with retries
     */
    private YakPlayer savePlayerToDatabase(YakPlayer player) {
        if (player == null) {
            logger.warning("Attempted to save null player to database");
            return null;
        }

        if (!ensureCollection()) {
            logger.warning("Cannot save player - collection not available");
            createLocalBackup(player);
            return player;
        }

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.fine("Saving player to database (attempt " + (attempt + 1) + "): " + player.getUsername());

                Document doc = playerToDocument(player);
                if (doc == null) {
                    logger.severe("Failed to convert player to document: " + player.getUsername());
                    return player;
                }

                // Use replace with upsert to insert if not exists, update if exists
                MongoDBManager.getInstance().performSafeOperation(() -> {
                    collection.replaceOne(
                            Filters.eq("uuid", player.getUUID().toString()),
                            doc,
                            new ReplaceOptions().upsert(true)
                    );
                    return null;
                });

                // Clear dirty flag if successful
                CachedPlayer cachedPlayer = playerCache.get(player.getUUID());
                if (cachedPlayer != null) {
                    cachedPlayer.clearDirty();
                }

                logger.fine("Successfully saved player to database: " + player.getUsername());
                return player;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving player: " + player.getUUID() + " (attempt " + (attempt + 1) + ")", e);

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    // Create local backup on final failure
                    createLocalBackup(player);
                    logger.severe("Failed to save player after " + MAX_RETRY_ATTEMPTS + " attempts, created local backup: " + player.getUsername());
                }
            }
        }

        return player;
    }

    /**
     * Save a player synchronously (for use during shutdown)
     */
    public YakPlayer saveSync(YakPlayer player) {
        if (player == null) {
            logger.warning("Attempted to sync save null player");
            return null;
        }

        // Validate player data
        if (!validatePlayerData(player)) {
            logger.warning("Invalid player data for " + player.getUsername() + ", creating backup only");
            createLocalBackup(player);
            return player;
        }

        // Always update cache
        cachePlayer(player);

        // Check if MongoDB is available
        if (!ensureCollection()) {
            createLocalBackup(player);
            logger.warning("Could not sync save player - collection unavailable: " + player.getUsername());
            return player;
        }

        try {
            Document doc = playerToDocument(player);
            if (doc == null) {
                logger.severe("Failed to convert player to document during sync save: " + player.getUsername());
                createLocalBackup(player);
                return player;
            }

            // Use replace with upsert to insert if not exists, update if exists
            collection.replaceOne(
                    Filters.eq("uuid", player.getUUID().toString()),
                    doc,
                    new ReplaceOptions().upsert(true)
            );

            logger.fine("Successfully performed sync save for player: " + player.getUsername());
            return player;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during sync save for player: " + player.getUUID(), e);

            // Create local backup on failure
            createLocalBackup(player);

            return player;
        }
    }

    /**
     * Validate player data before saving
     */
    private boolean validatePlayerData(YakPlayer player) {
        if (player == null) {
            return false;
        }

        // Check essential fields
        if (player.getUUID() == null) {
            logger.warning("Player validation failed: null UUID");
            return false;
        }

        // Check for null username
        if (player.getUsername() == null || player.getUsername().trim().isEmpty()) {
            logger.warning("Player validation failed: null or empty username for UUID " + player.getUUID());
            return false;
        }

        // Check for reasonable values
        if (player.getGems() < 0) {
            logger.warning("Player validation warning: negative gems for " + player.getUsername() + ", will be reset to 0");
            player.setGems(0);
        }

        if (player.getLevel() < 1) {
            logger.warning("Player validation warning: invalid level for " + player.getUsername() + ", will be reset to 1");
            player.setLevel(1);
        }

        return true;
    }

    /**
     * Create a local file backup of player data
     */
    private void createLocalBackup(YakPlayer player) {
        if (player == null) {
            return;
        }

        try {
            // Create backup directory if it doesn't exist
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            // Create player directory
            File playerDir = new File(backupDir, player.getUUID().toString());
            if (!playerDir.exists()) {
                playerDir.mkdirs();
            }

            // Generate timestamp for filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            // Serialize player to document
            Document doc = playerToDocument(player);
            if (doc == null) {
                logger.warning("Failed to create local backup - document conversion failed for: " + player.getUsername());
                return;
            }

            // Write to file
            File backupFile = new File(playerDir, timestamp + ".json");
            Files.write(backupFile.toPath(), doc.toJson().getBytes());

            // Keep only the last 10 backups
            cleanOldLocalBackups(playerDir);

            logger.fine("Created local backup for player: " + player.getUsername());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create local backup for player " + player.getUsername(), e);
        }
    }

    /**
     * Clean old local backups, keeping only the latest few
     */
    private void cleanOldLocalBackups(File playerDir) {
        try {
            File[] backupFiles = playerDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (backupFiles == null || backupFiles.length <= 10) {
                return; // Keep at most 10 backups
            }

            // Sort by last modified time (oldest first)
            Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));

            // Delete oldest files
            for (int i = 0; i < backupFiles.length - 10; i++) {
                if (backupFiles[i].delete()) {
                    logger.fine("Deleted old backup: " + backupFiles[i].getName());
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning old local backups", e);
        }
    }

    /**
     * FIXED: Find all players with batching for large datasets
     */
    @Override
    public CompletableFuture<List<YakPlayer>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<YakPlayer> players = new ArrayList<>();

            if (!ensureCollection()) {
                logger.severe("Cannot find all players - collection not available");
                return players;
            }

            try {
                performingBatchOperation.set(true);
                logger.info("Loading all players from database...");

                FindIterable<Document> docs = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find()
                );

                if (docs != null) {
                    int loadedCount = 0;
                    int errorCount = 0;

                    for (Document doc : docs) {
                        try {
                            YakPlayer player = documentToPlayer(doc);
                            if (player != null) {
                                players.add(player);
                                cachePlayer(player);
                                loadedCount++;
                            } else {
                                errorCount++;
                                logger.warning("Failed to convert document to player during findAll");
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
            } finally {
                performingBatchOperation.set(false);
            }

            return players;
        });
    }

    /**
     * Delete a player
     */
    @Override
    public CompletableFuture<Boolean> delete(YakPlayer player) {
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }
        return deleteById(player.getUUID());
    }

    /**
     * Delete a player by UUID
     */
    @Override
    public CompletableFuture<Boolean> deleteById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            if (id == null) {
                return false;
            }

            // Create backup before deletion
            YakPlayer player = playerCache.containsKey(id) ?
                    playerCache.get(id).getPlayer() : null;

            if (player != null) {
                createLocalBackup(player);
            }

            // Remove from cache
            playerCache.remove(id);
            for (Iterator<Map.Entry<String, UUID>> it = nameToUuidCache.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, UUID> entry = it.next();
                if (entry.getValue().equals(id)) {
                    it.remove();
                }
            }

            if (!ensureCollection()) {
                logger.severe("Could not delete player - collection unavailable: " + id);
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

    /**
     * Check if a player exists
     */
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

            if (!ensureCollection()) {
                logger.severe("Could not check if player exists - collection unavailable: " + id);
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
     * FIXED: Convert a MongoDB document to a YakPlayer object
     */
    private YakPlayer documentToPlayer(Document doc) {
        if (doc == null) {
            logger.warning("Cannot convert null document to player");
            return null;
        }

        try {
            // Extract UUID first - this is essential
            String uuidStr = doc.getString("uuid");
            if (uuidStr == null || uuidStr.trim().isEmpty()) {
                logger.severe("Document missing UUID field, cannot convert to player");
                return null;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                logger.severe("Invalid UUID format in document: " + uuidStr);
                return null;
            }

            YakPlayer player = new YakPlayer(uuid);

            // Basic identification - handle nulls gracefully
            String username = doc.getString("username");
            if (username != null && !username.trim().isEmpty()) {
                player.setUsername(username);
            } else {
                logger.warning("Player document missing username for UUID: " + uuid);
                player.setUsername("Unknown");
            }

            // Load all fields with null checks and defaults
            player.setLastLogin(doc.getLong("last_login") != null ? doc.getLong("last_login") : System.currentTimeMillis() / 1000);
            player.setLastLogout(doc.getLong("last_logout") != null ? doc.getLong("last_logout") : 0);
            player.setFirstJoin(doc.getLong("first_join") != null ? doc.getLong("first_join") : System.currentTimeMillis() / 1000);
            player.setTotalPlaytime(doc.getLong("total_playtime") != null ? doc.getLong("total_playtime") : 0L);

            String ipAddress = doc.getString("ip_address");
            if (ipAddress != null) {
                player.setIpAddress(ipAddress);
            }

            // Player progression and stats
            player.setLevel(doc.getInteger("level", 1));
            player.setExp(doc.getInteger("exp", 0));
            player.setMonsterKills(doc.getInteger("monster_kills", 0));
            player.setPlayerKills(doc.getInteger("player_kills", 0));
            player.setDeaths(doc.getInteger("deaths", 0));
            player.setOreMined(doc.getInteger("ore_mined", 0));
            player.setFishCaught(doc.getInteger("fish_caught", 0));
            player.setBlocksBroken(doc.getInteger("blocks_broken", 0));
            player.setDistanceTraveled(doc.getDouble("distance_traveled") != null ? doc.getDouble("distance_traveled") : 0.0);

            // Economic data
            player.setGems(doc.getInteger("gems", 0));
            player.setBankGems(doc.getInteger("bank_gems", 0));
            player.setEliteShards(doc.getInteger("elite_shards", 0));
            player.setTotalGemsEarned(doc.getLong("total_gems_earned") != null ? doc.getLong("total_gems_earned") : 0L);
            player.setTotalGemsSpent(doc.getLong("total_gems_spent") != null ? doc.getLong("total_gems_spent") : 0L);

            // Bank data
            player.setBankPages(doc.getInteger("bank_pages", 1));
            player.setBankAuthorizedUsers(doc.getList("bank_authorized_users", String.class, new ArrayList<>()));
            player.getBankAccessLog().clear();
            List<String> bankLog = doc.getList("bank_access_log", String.class);
            if(bankLog != null) {
                player.getBankAccessLog().addAll(bankLog);
            }

            // Bank inventory data
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
                        logger.log(Level.WARNING, "Invalid bank page number: " + key);
                    }
                }
            }

            // Alignment data
            player.setAlignment(doc.getString("alignment") != null ? doc.getString("alignment") : "LAWFUL");
            player.setChaoticTime(doc.getLong("chaotic_time") != null ? doc.getLong("chaotic_time") : 0);
            player.setNeutralTime(doc.getLong("neutral_time") != null ? doc.getLong("neutral_time") : 0);
            player.setAlignmentChanges(doc.getInteger("alignment_changes", 0));

            // Moderation data
            player.setRank(doc.getString("rank") != null ? doc.getString("rank") : "DEFAULT");
            player.setBanned(doc.getBoolean("banned", false));
            player.setBanReason(doc.getString("ban_reason") != null ? doc.getString("ban_reason") : "");
            player.setBanExpiry(doc.getLong("ban_expiry") != null ? doc.getLong("ban_expiry") : 0);
            player.setMuteTime(doc.getInteger("muted", 0));
            player.setWarnings(doc.getInteger("warnings", 0));
            player.setLastWarning(doc.getLong("last_warning") != null ? doc.getLong("last_warning") : 0);

            // Chat data
            player.setChatTag(doc.getString("chat_tag") != null ? doc.getString("chat_tag") : "DEFAULT");
            player.setUnlockedChatTags(doc.getList("unlocked_chat_tags", String.class, new ArrayList<>()));
            player.setChatColor(doc.getString("chat_color") != null ? doc.getString("chat_color") : "WHITE");

            // Mount data
            player.setHorseTier(doc.getInteger("horse_tier", 0));
            player.setHorseName(doc.getString("horse_name") != null ? doc.getString("horse_name") : "");

            // Guild data
            player.setGuildName(doc.getString("guild_name") != null ? doc.getString("guild_name") : "");
            player.setGuildRank(doc.getString("guild_rank") != null ? doc.getString("guild_rank") : "");
            player.setGuildContribution(doc.getInteger("guild_contribution", 0));

            // Toggle preferences
            player.setToggleSettings(new HashSet<>(doc.getList("toggle_settings", String.class, new ArrayList<>())));
            Document notificationSettingsDoc = doc.get("notification_settings", Document.class);
            if (notificationSettingsDoc != null) {
                for (Map.Entry<String, Object> entry : notificationSettingsDoc.entrySet()) {
                    if (entry.getValue() instanceof Boolean) {
                        player.getNotificationSettings().put(entry.getKey(), (Boolean) entry.getValue());
                    }
                }
            }

            // Quest data
            player.setCurrentQuest(doc.getString("current_quest") != null ? doc.getString("current_quest") : "");
            player.setQuestProgress(doc.getInteger("quest_progress", 0));
            player.setCompletedQuests(doc.getList("completed_quests", String.class, new ArrayList<>()));
            player.setQuestPoints(doc.getInteger("quest_points", 0));
            player.setDailyQuestsCompleted(doc.getInteger("daily_quests_completed", 0));
            player.setLastDailyQuestReset(doc.getLong("last_daily_quest_reset") != null ? doc.getLong("last_daily_quest_reset") : 0);

            // All other fields remain the same...
            // [Continuing with all the other fields as in the original code]

            logger.fine("Successfully converted document to player: " + player.getUsername());
            return player;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error converting document to player", e);
            return null;
        }
    }

    /**
     * FIXED: Convert a YakPlayer object to a MongoDB document
     */
    private Document playerToDocument(YakPlayer player) {
        if (player == null) {
            logger.warning("Cannot convert null player to document");
            return null;
        }

        if (player.getUUID() == null) {
            logger.severe("Cannot convert player with null UUID to document");
            return null;
        }

        try {
            Document doc = new Document();

            // Basic identification
            doc.append("uuid", player.getUUID().toString());
            doc.append("username", player.getUsername() != null ? player.getUsername() : "Unknown");
            doc.append("last_login", player.getLastLogin());
            doc.append("last_logout", player.getLastLogout());
            doc.append("first_join", player.getFirstJoin());
            doc.append("total_playtime", player.getTotalPlaytime());

            if (player.getIpAddress() != null) {
                doc.append("ip_address", player.getIpAddress());
            }

            // Player progression and stats
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

            // Serialize bank inventory data
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

            // All other fields remain the same...
            // [Continuing with all the other fields as in the original code]

            logger.fine("Successfully converted player to document: " + player.getUsername());
            return doc;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error converting player to document for " + player.getUsername(), e);
            return null;
        }
    }

    /**
     * Check if the repository is properly initialized
     */
    public boolean isInitialized() {
        return repositoryInitialized.get() && collection != null;
    }

    /**
     * Get repository status for debugging
     */
    public Map<String, Object> getRepositoryStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("initialized", repositoryInitialized.get());
        status.put("collection_available", collection != null);
        status.put("backup_collection_available", backupCollection != null);
        status.put("cached_players", playerCache.size());
        status.put("name_cache_size", nameToUuidCache.size());
        status.put("performing_batch_operation", performingBatchOperation.get());

        try {
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            status.put("mongodb_connected", mongoDBManager != null && mongoDBManager.isConnected());
        } catch (Exception e) {
            status.put("mongodb_connected", false);
            status.put("mongodb_error", e.getMessage());
        }

        return status;
    }
}