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

/**
 * Repository for YakPlayer entities using MongoDB
 * Enhanced with better caching, error handling, and data integrity
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

        try {
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager.isConnected()) {
                this.collection = mongoDBManager.getCollection(COLLECTION_NAME);
                this.backupCollection = mongoDBManager.getCollection(BACKUP_COLLECTION_NAME);
                // Create indexes for better performance
                createIndexes();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize repository: " + e.getMessage(), e);
        }

        // Start the cache cleaning task
        startCacheCleaningTask();
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
            logger.info("Cache cleaning: removed " + expiredCount + " expired entries, saved " + savedCount + " dirty entries");
        }
    }

    /**
     * Ensures the collection reference is valid
     */
    private boolean ensureCollection() {
        if (collection != null) {
            return true;
        }

        try {
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager.isConnected()) {
                this.collection = mongoDBManager.getCollection(COLLECTION_NAME);
                this.backupCollection = mongoDBManager.getCollection(BACKUP_COLLECTION_NAME);
                return true;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not get collection: " + e.getMessage());
        }

        return false;
    }

    /**
     * Creates MongoDB indexes for better performance
     */
    private void createIndexes() {
        try {
            // Create index on username for faster lookups by name
            collection.createIndex(new Document("username", 1));

            // Create index on uuid for faster lookups by ID
            collection.createIndex(new Document("uuid", 1));

            // Create indexes on backup collection
            backupCollection.createIndex(new Document("uuid", 1));
            backupCollection.createIndex(new Document("timestamp", -1));

            logger.info("Created indexes for player collections");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create indexes: " + e.getMessage(), e);
        }
    }

    /**
     * Find a player by UUID with retry mechanism
     *
     * @param id The player UUID
     * @return A CompletableFuture containing the player, or empty if not found
     */
    @Override
    public CompletableFuture<Optional<YakPlayer>> findById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache first
            CachedPlayer cachedPlayer = playerCache.get(id);
            if (cachedPlayer != null) {
                return Optional.of(cachedPlayer.getPlayer());
            }

            if (!ensureCollection()) {
                return Optional.empty();
            }

            for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    Document doc = MongoDBManager.getInstance().performSafeOperation(() ->
                            collection.find(Filters.eq("uuid", id.toString())).first()
                    );

                    if (doc != null) {
                        YakPlayer player = documentToPlayer(doc);

                        // Cache the player if successfully loaded
                        if (player != null) {
                            cachePlayer(player);
                        }

                        return Optional.ofNullable(player);
                    }

                    // Try backup collection if not found in main collection and it's not the first attempt
                    if (attempt > 0) {
                        Document backupDoc = MongoDBManager.getInstance().performSafeOperation(() ->
                                backupCollection.find(Filters.eq("uuid", id.toString()))
                                        .sort(new Document("timestamp", -1))
                                        .first()
                        );

                        if (backupDoc != null) {
                            logger.info("Recovered player data for " + id + " from backup collection");

                            // Remove the backup timestamp field
                            backupDoc.remove("timestamp");

                            YakPlayer player = documentToPlayer(backupDoc);
                            if (player != null) {
                                // Save to main collection
                                savePlayerToDatabase(player);
                                cachePlayer(player);

                                return Optional.of(player);
                            }
                        }
                    }

                    break; // Break the loop if the document was not found

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error finding player by UUID: " + id + " (attempt " + (attempt + 1) + ")", e);

                    if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            // Try local backup files as last resort
            try {
                YakPlayer player = loadFromLocalBackup(id);
                if (player != null) {
                    logger.info("Recovered player data for " + id + " from local backup");
                    cachePlayer(player);
                    savePlayerToDatabase(player);
                    return Optional.of(player);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load player from local backup: " + id, e);
            }

            return Optional.empty();
        });
    }

    /**
     * Cache a player
     *
     * @param player The player to cache
     */
    private void cachePlayer(YakPlayer player) {
        playerCache.put(player.getUUID(), new CachedPlayer(player));
        nameToUuidCache.put(player.getUsername().toLowerCase(), player.getUUID());
    }

    /**
     * Mark a cached player as dirty (needs saving)
     *
     * @param player The player to mark as dirty
     */
    private void markPlayerDirty(YakPlayer player) {
        CachedPlayer cachedPlayer = playerCache.get(player.getUUID());
        if (cachedPlayer != null) {
            cachedPlayer.markDirty();
        } else {
            cachePlayer(player);
            playerCache.get(player.getUUID()).markDirty();
        }
    }

    /**
     * Find a player by name with retry mechanism
     *
     * @param name The player name
     * @return A CompletableFuture containing the player, or empty if not found
     */
    public CompletableFuture<Optional<YakPlayer>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            // Check name cache first
            UUID id = nameToUuidCache.get(name.toLowerCase());
            if (id != null) {
                CachedPlayer cachedPlayer = playerCache.get(id);
                if (cachedPlayer != null) {
                    return Optional.of(cachedPlayer.getPlayer());
                }
            }

            if (!ensureCollection()) {
                return Optional.empty();
            }

            for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    Document doc = MongoDBManager.getInstance().performSafeOperation(() ->
                            collection.find(Filters.eq("username", name)).first()
                    );

                    if (doc != null) {
                        YakPlayer player = documentToPlayer(doc);

                        // Cache the player
                        if (player != null) {
                            cachePlayer(player);
                        }

                        return Optional.ofNullable(player);
                    }

                    break; // Break the loop if the document was not found

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error finding player by name: " + name + " (attempt " + (attempt + 1) + ")", e);

                    if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            return Optional.empty();
        });
    }

    /**
     * Find all players
     *
     * @return A CompletableFuture containing a list of all players
     */
    @Override
    public CompletableFuture<List<YakPlayer>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<YakPlayer> players = new ArrayList<>();

            if (!ensureCollection()) {
                return players;
            }

            try {
                performingBatchOperation.set(true);

                FindIterable<Document> docs = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find()
                );

                if (docs != null) {
                    for (Document doc : docs) {
                        YakPlayer player = documentToPlayer(doc);
                        if (player != null) {
                            players.add(player);

                            // Update cache
                            cachePlayer(player);
                        }
                    }
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
     * Save a player with validation and backup
     *
     * @param player The player to save
     * @return A CompletableFuture containing the saved player
     */
    @Override
    public CompletableFuture<YakPlayer> save(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            // Validate player data
            if (!validatePlayerData(player)) {
                logger.warning("Invalid player data for " + player.getUsername() + ", skipping save");
                return player;
            }

            // Always update cache and mark as dirty
            markPlayerDirty(player);

            // Check if MongoDB is shutting down
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager.isShuttingDown()) {
                // Create local backup on shutdown
                createLocalBackup(player);
                logger.info("Created local backup for " + player.getUsername() + " during shutdown");
                return player;
            }

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
     * Save player to database with retries
     *
     * @param player The player to save
     * @return The saved player
     */
    private YakPlayer savePlayerToDatabase(YakPlayer player) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Document doc = playerToDocument(player);

                // Create backup in the backup collection first
                boolean backupSuccess = createDatabaseBackup(player);
                if (!backupSuccess && attempt == 0) {
                    logger.warning("Failed to create database backup for " + player.getUsername() + ", will retry");
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

                return player;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving player: " + player.getUUID() + " (attempt " + (attempt + 1) + ")", e);

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    // Create local backup on final failure
                    createLocalBackup(player);
                }
            }
        }

        return player;
    }

    /**
     * Create a backup in the database backup collection
     *
     * @param player The player to backup
     * @return True if successful, false otherwise
     */
    private boolean createDatabaseBackup(YakPlayer player) {
        try {
            if (!ensureCollection() || backupCollection == null) {
                return false;
            }

            Document doc = playerToDocument(player);

            // Add timestamp
            doc.append("timestamp", System.currentTimeMillis());

            // Insert into backup collection
            MongoDBManager.getInstance().performSafeOperation(() -> {
                backupCollection.insertOne(doc);
                return null;
            });

            // Clean old backups (keep last 5)
            cleanOldDatabaseBackups(player.getUUID());

            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating database backup for player: " + player.getUUID(), e);
            return false;
        }
    }

    /**
     * Clean old database backups, keeping only the latest few
     *
     * @param playerId The player UUID
     */
    private void cleanOldDatabaseBackups(UUID playerId) {
        try {
            if (!ensureCollection() || backupCollection == null) {
                return;
            }

            // Get count of backups for this player
            long count = MongoDBManager.getInstance().performSafeOperation(() ->
                    backupCollection.countDocuments(Filters.eq("uuid", playerId.toString()))
            );

            if (count <= 5) {
                return; // Keep at most 5 backups
            }

            // Find oldest backups to delete
            List<Document> oldBackups = MongoDBManager.getInstance().performSafeOperation(() -> {
                List<Document> result = new ArrayList<>();
                backupCollection.find(Filters.eq("uuid", playerId.toString()))
                        .sort(new Document("timestamp", 1))
                        .limit((int) (count - 5))
                        .into(result);
                return result;
            });

            if (oldBackups != null && !oldBackups.isEmpty()) {
                List<Bson> idsToDelete = new ArrayList<>();
                for (Document doc : oldBackups) {
                    idsToDelete.add(Filters.eq("_id", doc.getObjectId("_id")));
                }

                if (!idsToDelete.isEmpty()) {
                    MongoDBManager.getInstance().performSafeOperation(() -> {
                        backupCollection.deleteMany(Filters.or(idsToDelete));
                        return null;
                    });
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning old database backups for player: " + playerId, e);
        }
    }

    /**
     * Create a local file backup of player data
     *
     * @param player The player to backup
     */
    private void createLocalBackup(YakPlayer player) {
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

            // Write to file
            File backupFile = new File(playerDir, timestamp + ".json");
            Files.write(backupFile.toPath(), doc.toJson().getBytes());

            // Keep only the last 10 backups
            cleanOldLocalBackups(playerDir);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create local backup for player " + player.getUsername(), e);
        }
    }

    /**
     * Clean old local backups, keeping only the latest few
     *
     * @param playerDir The player's backup directory
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
                backupFiles[i].delete();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning old local backups", e);
        }
    }

    /**
     * Load player data from local backup
     *
     * @param playerId The player UUID
     * @return The loaded player or null if not found
     */
    private YakPlayer loadFromLocalBackup(UUID playerId) {
        File playerDir = new File(backupDir, playerId.toString());
        if (!playerDir.exists()) {
            return null;
        }

        File[] backupFiles = playerDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (backupFiles == null || backupFiles.length == 0) {
            return null;
        }

        // Sort by last modified time (newest first)
        Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified).reversed());

        // Try to load from newest backup
        for (File backupFile : backupFiles) {
            try {
                String json = new String(Files.readAllBytes(backupFile.toPath()));
                Document doc = Document.parse(json);
                return documentToPlayer(doc);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load backup from " + backupFile.getName(), e);
            }
        }

        return null;
    }

    /**
     * Save a player synchronously (for use during shutdown)
     *
     * @param player The player to save
     * @return The saved player
     */
    public YakPlayer saveSync(YakPlayer player) {
        // Validate player data
        if (!validatePlayerData(player)) {
            logger.warning("Invalid player data for " + player.getUsername() + ", creating backup only");
            createLocalBackup(player);
            return player;
        }

        // Always update cache
        cachePlayer(player);

        // Check if MongoDB is shutting down or not connected
        MongoDBManager mongoDBManager = MongoDBManager.getInstance();
        if (mongoDBManager.isShuttingDown() || !mongoDBManager.isConnected()) {
            // Create local backup during shutdown
            createLocalBackup(player);
            logger.info("Created local backup for " + player.getUsername() + " during shutdown");
            return player;
        }

        try {
            Document doc = playerToDocument(player);

            // Try to create database backup
            createDatabaseBackup(player);

            // Use replace with upsert to insert if not exists, update if exists
            collection.replaceOne(
                    Filters.eq("uuid", player.getUUID().toString()),
                    doc,
                    new ReplaceOptions().upsert(true)
            );

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
     *
     * @param player The player to validate
     * @return true if the player data is valid
     */
    private boolean validatePlayerData(YakPlayer player) {
        if (player == null) {
            return false;
        }

        // Check essential fields
        if (player.getUUID() == null) {
            return false;
        }

        // Check for null username
        if (player.getUsername() == null || player.getUsername().isEmpty()) {
            return false;
        }

        // Additional validation logic could be added here

        return true;
    }

    /**
     * Delete a player
     *
     * @param player The player to delete
     * @return A CompletableFuture containing true if deleted, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> delete(YakPlayer player) {
        return deleteById(player.getUUID());
    }

    /**
     * Delete a player by UUID
     *
     * @param id The player UUID
     * @return A CompletableFuture containing true if deleted, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> deleteById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            // Create backup before deletion
            YakPlayer player = playerCache.containsKey(id) ?
                    playerCache.get(id).getPlayer() : null;

            if (player != null) {
                createDatabaseBackup(player);
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
                return false;
            }

            try {
                DeleteResult result = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.deleteOne(Filters.eq("uuid", id.toString()))
                );

                return result != null && result.getDeletedCount() > 0;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error deleting player: " + id, e);
                return false;
            }
        });
    }

    /**
     * Check if a player exists
     *
     * @param id The player UUID
     * @return A CompletableFuture containing true if the player exists, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> existsById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache first
            if (playerCache.containsKey(id)) {
                return true;
            }

            if (!ensureCollection()) {
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
     * Check if a player exists by name
     *
     * @param name The player name
     * @return A CompletableFuture containing true if the player exists, false otherwise
     */
    public CompletableFuture<Boolean> existsByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache first
            if (nameToUuidCache.containsKey(name.toLowerCase())) {
                return true;
            }

            if (!ensureCollection()) {
                return false;
            }

            try {
                Long count = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.countDocuments(Filters.eq("username", name))
                );

                return count != null && count > 0;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error checking if player exists by name: " + name, e);
                return false;
            }
        });
    }

    /**
     * Clear the player cache
     */
    public void clearCache() {
        // Save dirty players before clearing
        saveDirtyPlayers();

        playerCache.clear();
        nameToUuidCache.clear();
    }

    /**
     * Save all dirty players in the cache
     */
    public void saveDirtyPlayers() {
        List<YakPlayer> playersToSave = new ArrayList<>();

        for (CachedPlayer cachedPlayer : playerCache.values()) {
            if (cachedPlayer.isDirty()) {
                playersToSave.add(cachedPlayer.getPlayer());
            }
        }

        if (!playersToSave.isEmpty()) {
            logger.info("Saving " + playersToSave.size() + " dirty players");

            for (YakPlayer player : playersToSave) {
                savePlayerToDatabase(player);
            }
        }
    }

    /**
     * Remove a player from the cache
     *
     * @param id The player UUID
     */
    public void removeFromCache(UUID id) {
        CachedPlayer player = playerCache.remove(id);
        if (player != null && player.isDirty()) {
            // Save before removing from cache
            savePlayerToDatabase(player.getPlayer());

            // Remove from name cache
            for (Iterator<Map.Entry<String, UUID>> it = nameToUuidCache.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, UUID> entry = it.next();
                if (entry.getValue().equals(id)) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Update player cache and mark as dirty
     *
     * @param player The player to update in cache
     */
    public void updateCache(YakPlayer player) {
        markPlayerDirty(player);
    }

    /**
     * Convert a MongoDB document to a YakPlayer object
     *
     * @param doc The MongoDB document
     * @return The YakPlayer object
     */
    private YakPlayer documentToPlayer(Document doc) {
        try {
            // Extract UUID
            UUID uuid = UUID.fromString(doc.getString("uuid"));
            YakPlayer player = new YakPlayer(uuid);

            // Basic identification
            player.setUsername(doc.getString("username"));
            if (doc.containsKey("last_login")) player.setLastLogin(doc.getLong("last_login"));
            if (doc.containsKey("last_logout")) player.setLastLogout(doc.getLong("last_logout"));
            if (doc.containsKey("first_join")) player.setFirstJoin(doc.getLong("first_join"));
            if (doc.containsKey("ip_address")) player.setIpAddress(doc.getString("ip_address"));

            // Player progression and stats
            if (doc.containsKey("level")) player.setLevel(doc.getInteger("level"));
            if (doc.containsKey("exp")) player.setExp(doc.getInteger("exp"));
            if (doc.containsKey("monster_kills")) player.setMonsterKills(doc.getInteger("monster_kills"));
            if (doc.containsKey("player_kills")) player.setPlayerKills(doc.getInteger("player_kills"));
            if (doc.containsKey("deaths")) player.setDeaths(doc.getInteger("deaths"));
            if (doc.containsKey("ore_mined")) player.setOreMined(doc.getInteger("ore_mined"));
            if (doc.containsKey("fish_caught")) player.setFishCaught(doc.getInteger("fish_caught"));

            // Economical data
            if (doc.containsKey("gems")) player.setGems(doc.getInteger("gems"));
            if (doc.containsKey("bank_gems")) player.setBankGems(doc.getInteger("bank_gems"));
            if (doc.containsKey("elite_shards")) player.setEliteShards(doc.getInteger("elite_shards"));

            // Bank data
            if (doc.containsKey("bank_pages")) player.setBankPages(doc.getInteger("bank_pages"));
            if (doc.containsKey("bank_authorized_users")) {
                List<String> users = doc.getList("bank_authorized_users", String.class);
                player.setBankAuthorizedUsers(users);
            }

            // Bank inventory data
            if (doc.containsKey("bank_inventory")) {
                Document bankItems = doc.get("bank_inventory", Document.class);
                Map<Integer, String> serializedBankItems = new HashMap<>();

                for (String key : bankItems.keySet()) {
                    try {
                        int page = Integer.parseInt(key);
                        String serializedData = bankItems.getString(key);
                        serializedBankItems.put(page, serializedData);
                    } catch (NumberFormatException e) {
                        logger.log(Level.WARNING, "Invalid bank page number: " + key);
                    }
                }

                // Set each bank page
                for (Map.Entry<Integer, String> entry : serializedBankItems.entrySet()) {
                    player.setSerializedBankItems(entry.getKey(), entry.getValue());
                }
            }

            // Alignment data
            if (doc.containsKey("alignment")) player.setAlignment(doc.getString("alignment"));
            if (doc.containsKey("chaotic_time")) player.setChaoticTime(doc.getLong("chaotic_time"));
            if (doc.containsKey("neutral_time")) player.setNeutralTime(doc.getLong("neutral_time"));

            // Moderation data
            if (doc.containsKey("rank")) player.setRank(doc.getString("rank"));
            if (doc.containsKey("banned")) player.setBanned(doc.getBoolean("banned"));
            if (doc.containsKey("ban_reason")) player.setBanReason(doc.getString("ban_reason"));
            if (doc.containsKey("ban_expiry")) player.setBanExpiry(doc.getLong("ban_expiry"));
            if (doc.containsKey("muted")) player.setMuteTime(doc.getInteger("muted"));

            // Chat data
            if (doc.containsKey("chat_tag")) player.setChatTag(doc.getString("chat_tag"));
            if (doc.containsKey("unlocked_chat_tags")) {
                List<String> tags = doc.getList("unlocked_chat_tags", String.class);
                player.setUnlockedChatTags(tags);
            }

            // Mount data
            if (doc.containsKey("horse_tier")) player.setHorseTier(doc.getInteger("horse_tier"));

            // Guild data
            if (doc.containsKey("guild_name")) player.setGuildName(doc.getString("guild_name"));
            if (doc.containsKey("guild_rank")) player.setGuildRank(doc.getString("guild_rank"));

            // Toggle preferences
            if (doc.containsKey("toggle_settings")) {
                List<String> toggles = doc.getList("toggle_settings", String.class);
                player.setToggleSettings(new HashSet<>(toggles));
            }

            // Quest data
            if (doc.containsKey("current_quest")) player.setCurrentQuest(doc.getString("current_quest"));
            if (doc.containsKey("quest_progress")) player.setQuestProgress(doc.getInteger("quest_progress"));
            if (doc.containsKey("completed_quests")) {
                List<String> quests = doc.getList("completed_quests", String.class);
                player.setCompletedQuests(quests);
            }

            // Profession data
            if (doc.containsKey("pickaxe_level")) player.setPickaxeLevel(doc.getInteger("pickaxe_level"));
            if (doc.containsKey("fishing_level")) player.setFishingLevel(doc.getInteger("fishing_level"));
            if (doc.containsKey("mining_xp")) player.setMiningXp(doc.getInteger("mining_xp"));
            if (doc.containsKey("fishing_xp")) player.setFishingXp(doc.getInteger("fishing_xp"));

            // PvP stats
            if (doc.containsKey("t1_kills")) player.setT1Kills(doc.getInteger("t1_kills"));
            if (doc.containsKey("t2_kills")) player.setT2Kills(doc.getInteger("t2_kills"));
            if (doc.containsKey("t3_kills")) player.setT3Kills(doc.getInteger("t3_kills"));
            if (doc.containsKey("t4_kills")) player.setT4Kills(doc.getInteger("t4_kills"));
            if (doc.containsKey("t5_kills")) player.setT5Kills(doc.getInteger("t5_kills"));
            if (doc.containsKey("t6_kills")) player.setT6Kills(doc.getInteger("t6_kills"));

            // World Boss tracking
            if (doc.containsKey("world_boss_damage")) {
                Document damageDoc = doc.get("world_boss_damage", Document.class);
                Map<String, Integer> damageMap = new HashMap<>();
                for (String bossName : damageDoc.keySet()) {
                    damageMap.put(bossName, damageDoc.getInteger(bossName));
                }
                player.setWorldBossDamage(damageMap);
            }

            // Trading data
            if (doc.containsKey("trade_disabled")) player.setTradeDisabled(doc.getBoolean("trade_disabled"));

            // Player buddies (friends)
            if (doc.containsKey("buddies")) {
                List<String> buddies = doc.getList("buddies", String.class);
                player.setBuddies(buddies);
            }

            // Energy system settings
            if (doc.containsKey("energy_disabled")) player.setEnergyDisabled(doc.getBoolean("energy_disabled"));

            // Location data
            if (doc.containsKey("world")) player.setWorld(doc.getString("world"));
            if (doc.containsKey("location_x")) player.setLocationX(doc.getDouble("location_x"));
            if (doc.containsKey("location_y")) player.setLocationY(doc.getDouble("location_y"));
            if (doc.containsKey("location_z")) player.setLocationZ(doc.getDouble("location_z"));
            if (doc.containsKey("location_yaw")) player.setLocationYaw(doc.getDouble("location_yaw").floatValue());
            if (doc.containsKey("location_pitch"))
                player.setLocationPitch(doc.getDouble("location_pitch").floatValue());

            // Inventory data
            if (doc.containsKey("inventory_contents"))
                player.setSerializedInventory(doc.getString("inventory_contents"));
            if (doc.containsKey("armor_contents")) player.setSerializedArmor(doc.getString("armor_contents"));
            if (doc.containsKey("ender_chest_contents"))
                player.setSerializedEnderChest(doc.getString("ender_chest_contents"));
            if (doc.containsKey("offhand_item")) player.setSerializedOffhand(doc.getString("offhand_item"));

            // Player stats
            if (doc.containsKey("health")) player.setHealth(doc.getDouble("health"));
            if (doc.containsKey("max_health")) player.setMaxHealth(doc.getDouble("max_health"));
            if (doc.containsKey("food_level")) player.setFoodLevel(doc.getInteger("food_level"));
            if (doc.containsKey("saturation")) player.setSaturation(doc.getDouble("saturation").floatValue());
            if (doc.containsKey("xp_level")) player.setXpLevel(doc.getInteger("xp_level"));
            if (doc.containsKey("xp_progress")) player.setXpProgress(doc.getDouble("xp_progress").floatValue());
            if (doc.containsKey("total_experience")) player.setTotalExperience(doc.getInteger("total_experience"));
            if (doc.containsKey("bed_spawn_location")) player.setBedSpawnLocation(doc.getString("bed_spawn_location"));
            if (doc.containsKey("gamemode")) player.setGameMode(doc.getString("gamemode"));
            if (doc.containsKey("active_potion_effects")) {
                List<String> effects = doc.getList("active_potion_effects", String.class);
                player.setActivePotionEffects(effects);
            }

            return player;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error converting document to player", e);
            return null;
        }
    }

    /**
     * Convert a YakPlayer object to a MongoDB document
     *
     * @param player The YakPlayer object
     * @return The MongoDB document
     */
    private Document playerToDocument(YakPlayer player) {
        Document doc = new Document();

        // Basic identification
        doc.append("uuid", player.getUUID().toString());
        doc.append("username", player.getUsername());
        doc.append("last_login", player.getLastLogin());
        doc.append("last_logout", player.getLastLogout());
        doc.append("first_join", player.getFirstJoin());
        doc.append("ip_address", player.getIpAddress());

        // Player progression and stats
        doc.append("level", player.getLevel());
        doc.append("exp", player.getExp());
        doc.append("monster_kills", player.getMonsterKills());
        doc.append("player_kills", player.getPlayerKills());
        doc.append("deaths", player.getDeaths());
        doc.append("ore_mined", player.getOreMined());
        doc.append("fish_caught", player.getFishCaught());

        // Economical data
        doc.append("gems", player.getGems());
        doc.append("bank_gems", player.getBankGems());
        doc.append("elite_shards", player.getEliteShards());

        // Bank data
        doc.append("bank_pages", player.getBankPages());
        doc.append("bank_authorized_users", player.getBankAuthorizedUsers());

        // Serialize bank inventory data
        Document bankItems = new Document();
        for (Map.Entry<Integer, String> entry : player.getAllSerializedBankItems().entrySet()) {
            bankItems.append(entry.getKey().toString(), entry.getValue());
        }
        doc.append("bank_inventory", bankItems);

        // Alignment data
        doc.append("alignment", player.getAlignment());
        doc.append("chaotic_time", player.getChaoticTime());
        doc.append("neutral_time", player.getNeutralTime());

        // Moderation data
        doc.append("rank", player.getRank());
        doc.append("banned", player.isBanned());
        doc.append("ban_reason", player.getBanReason());
        doc.append("ban_expiry", player.getBanExpiry());
        doc.append("muted", player.getMuteTime());

        // Chat data
        doc.append("chat_tag", player.getChatTag());
        doc.append("unlocked_chat_tags", player.getUnlockedChatTags());

        // Mount data
        doc.append("horse_tier", player.getHorseTier());

        // Guild data
        doc.append("guild_name", player.getGuildName());
        doc.append("guild_rank", player.getGuildRank());

        // Toggle preferences
        doc.append("toggle_settings", new ArrayList<>(player.getToggleSettings()));

        // Quest data
        doc.append("current_quest", player.getCurrentQuest());
        doc.append("quest_progress", player.getQuestProgress());
        doc.append("completed_quests", player.getCompletedQuests());

        // Profession data
        doc.append("pickaxe_level", player.getPickaxeLevel());
        doc.append("fishing_level", player.getFishingLevel());
        doc.append("mining_xp", player.getMiningXp());
        doc.append("fishing_xp", player.getFishingXp());

        // PvP stats
        doc.append("t1_kills", player.getT1Kills());
        doc.append("t2_kills", player.getT2Kills());
        doc.append("t3_kills", player.getT3Kills());
        doc.append("t4_kills", player.getT4Kills());
        doc.append("t5_kills", player.getT5Kills());
        doc.append("t6_kills", player.getT6Kills());

        // World Boss tracking
        Document worldBossDamage = new Document();
        for (Map.Entry<String, Integer> entry : player.getWorldBossDamage().entrySet()) {
            worldBossDamage.append(entry.getKey(), entry.getValue());
        }
        doc.append("world_boss_damage", worldBossDamage);

        // Trading data
        doc.append("trade_disabled", player.isTradeDisabled());

        // Player buddies (friends)
        doc.append("buddies", player.getBuddies());

        // Energy system settings
        doc.append("energy_disabled", player.isEnergyDisabled());

        // Location data
        doc.append("world", player.getWorld());
        doc.append("location_x", player.getLocationX());
        doc.append("location_y", player.getLocationY());
        doc.append("location_z", player.getLocationZ());
        doc.append("location_yaw", player.getLocationYaw());
        doc.append("location_pitch", player.getLocationPitch());

        // Inventory data
        if (player.getSerializedInventory() != null) doc.append("inventory_contents", player.getSerializedInventory());
        if (player.getSerializedArmor() != null) doc.append("armor_contents", player.getSerializedArmor());
        if (player.getSerializedEnderChest() != null)
            doc.append("ender_chest_contents", player.getSerializedEnderChest());
        if (player.getSerializedOffhand() != null) doc.append("offhand_item", player.getSerializedOffhand());

        // Player stats
        doc.append("health", player.getHealth());
        doc.append("max_health", player.getMaxHealth());
        doc.append("food_level", player.getFoodLevel());
        doc.append("saturation", player.getSaturation());
        doc.append("xp_level", player.getXpLevel());
        doc.append("xp_progress", player.getXpProgress());
        doc.append("total_experience", player.getTotalExperience());
        if (player.getBedSpawnLocation() != null) doc.append("bed_spawn_location", player.getBedSpawnLocation());
        doc.append("gamemode", player.getGameMode());
        doc.append("active_potion_effects", player.getActivePotionEffects());

        return doc;
    }
}