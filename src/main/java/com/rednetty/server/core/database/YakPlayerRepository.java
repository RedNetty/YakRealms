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
 * Enhanced with better error handling, simplified connection logic, and reliable data operations
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
     * Initialize repository connection with proper error handling
     */
    private void initializeRepository() {
        try {
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager == null) {
                logger.severe("MongoDBManager instance is null! Cannot initialize repository.");
                return;
            }

            if (!mongoDBManager.isConnected()) {
                logger.warning("MongoDB is not connected. Repository operations will be limited.");
                return;
            }

            this.collection = mongoDBManager.getCollection(COLLECTION_NAME);
            this.backupCollection = mongoDBManager.getCollection(BACKUP_COLLECTION_NAME);

            if (this.collection == null) {
                logger.severe("Failed to get MongoDB collection: " + COLLECTION_NAME);
                return;
            }

            // Test the connection with a simple operation
            testConnection();

            // Create indexes for better performance
            createIndexes();

            repositoryInitialized.set(true);
            logger.info("YakPlayerRepository initialized successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerRepository", e);
            repositoryInitialized.set(false);
        }
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
     * Ensures the collection reference is valid
     */
    private boolean ensureCollection() {
        if (!repositoryInitialized.get()) {
            logger.warning("Repository not initialized, attempting to reinitialize...");
            initializeRepository();
        }

        if (collection == null) {
            logger.severe("MongoDB collection is null, cannot perform operations");
            return false;
        }

        try {
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager == null || !mongoDBManager.isConnected()) {
                logger.warning("MongoDB connection lost, attempting to reconnect...");
                initializeRepository();
                return collection != null;
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking collection state", e);
            return false;
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
            logger.log(Level.WARNING, "Failed to create indexes", e);
        }
    }

    /**
     * Find a player by UUID with enhanced error handling
     *
     * @param id The player UUID
     * @return A CompletableFuture containing the player, or empty if not found
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

                    // Try backup collection if not found in main collection and it's not the first attempt
                    if (attempt > 0 && backupCollection != null) {
                        logger.info("Attempting to find player in backup collection: " + id);
                        Document backupDoc = MongoDBManager.getInstance().performSafeOperation(() ->
                                backupCollection.find(Filters.eq("uuid", id.toString()))
                                        .sort(new Document("timestamp", -1))
                                        .first()
                        );

                        if (backupDoc != null) {
                            logger.info("Recovered player data from backup collection: " + id);

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
                            break;
                        }
                    }
                }
            }

            // Try local backup files as last resort
            try {
                YakPlayer player = loadFromLocalBackup(id);
                if (player != null) {
                    logger.info("Recovered player data from local backup: " + id);
                    cachePlayer(player);
                    savePlayerToDatabase(player);
                    return Optional.of(player);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load player from local backup: " + id, e);
            }

            logger.fine("Player not found: " + id);
            return Optional.empty();
        });
    }

    /**
     * Cache a player
     *
     * @param player The player to cache
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
     * Mark a cached player as dirty (needs saving)
     *
     * @param player The player to mark as dirty
     */
    private void markPlayerDirty(YakPlayer player) {
        if (player == null || player.getUUID() == null) {
            return;
        }

        CachedPlayer cachedPlayer = playerCache.get(player.getUUID());
        if (cachedPlayer != null) {
            cachedPlayer.markDirty();
        } else {
            cachePlayer(player);
            CachedPlayer newCached = playerCache.get(player.getUUID());
            if (newCached != null) {
                newCached.markDirty();
            }
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
            if (name == null || name.trim().isEmpty()) {
                return Optional.empty();
            }

            String normalizedName = name.toLowerCase().trim();

            // Check name cache first
            UUID id = nameToUuidCache.get(normalizedName);
            if (id != null) {
                CachedPlayer cachedPlayer = playerCache.get(id);
                if (cachedPlayer != null) {
                    logger.fine("Found player by name in cache: " + name);
                    return Optional.of(cachedPlayer.getPlayer());
                }
            }

            if (!ensureCollection()) {
                logger.severe("Cannot find player by name - collection not available: " + name);
                return Optional.empty();
            }

            for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    logger.fine("Finding player by name (attempt " + (attempt + 1) + "): " + name);

                    Document doc = MongoDBManager.getInstance().performSafeOperation(() ->
                            collection.find(Filters.eq("username", name)).first()
                    );

                    if (doc != null) {
                        YakPlayer player = documentToPlayer(doc);

                        // Cache the player
                        if (player != null) {
                            cachePlayer(player);
                            logger.fine("Successfully found and cached player by name: " + name);
                            return Optional.of(player);
                        }
                    }

                    break; // Break the loop if the document was not found

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error finding player by name: " + name + " (attempt " + (attempt + 1) + ")", e);

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

            logger.fine("Player not found by name: " + name);
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
     * Save a player with validation and backup
     *
     * @param player The player to save
     * @return A CompletableFuture containing the saved player
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

            // Always update cache and mark as dirty
            markPlayerDirty(player);

            // Check if MongoDB is shutting down
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager != null && mongoDBManager.isShuttingDown()) {
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
        if (player == null) {
            logger.warning("Attempted to save null player to database");
            return null;
        }

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                logger.fine("Saving player to database (attempt " + (attempt + 1) + "): " + player.getUsername());

                Document doc = playerToDocument(player);
                if (doc == null) {
                    logger.severe("Failed to convert player to document: " + player.getUsername());
                    return player;
                }

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
     * Create a backup in the database backup collection
     *
     * @param player The player to backup
     * @return True if successful, false otherwise
     */
    private boolean createDatabaseBackup(YakPlayer player) {
        if (player == null) {
            return false;
        }

        try {
            if (!ensureCollection() || backupCollection == null) {
                return false;
            }

            Document doc = playerToDocument(player);
            if (doc == null) {
                return false;
            }

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
            Long count = MongoDBManager.getInstance().performSafeOperation(() ->
                    backupCollection.countDocuments(Filters.eq("uuid", playerId.toString()))
            );

            if (count == null || count <= 5) {
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
                if (backupFiles[i].delete()) {
                    logger.fine("Deleted old backup: " + backupFiles[i].getName());
                }
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
                YakPlayer player = documentToPlayer(doc);
                if (player != null) {
                    logger.info("Loaded player from local backup: " + backupFile.getName());
                    return player;
                }
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

        // Check if MongoDB is shutting down or not connected
        MongoDBManager mongoDBManager = MongoDBManager.getInstance();
        if (mongoDBManager == null || mongoDBManager.isShuttingDown() || !mongoDBManager.isConnected()) {
            // Create local backup during shutdown
            createLocalBackup(player);
            logger.info("Created local backup for " + player.getUsername() + " during sync save");
            return player;
        }

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

            // Try to create database backup
            createDatabaseBackup(player);

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
     * Delete a player
     *
     * @param player The player to delete
     * @return A CompletableFuture containing true if deleted, false otherwise
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
     *
     * @param id The player UUID
     * @return A CompletableFuture containing true if deleted, false otherwise
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
     *
     * @param id The player UUID
     * @return A CompletableFuture containing true if the player exists, false otherwise
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
     * Check if a player exists by name
     *
     * @param name The player name
     * @return A CompletableFuture containing true if the player exists, false otherwise
     */
    public CompletableFuture<Boolean> existsByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            if (name == null || name.trim().isEmpty()) {
                return false;
            }

            // Check cache first
            if (nameToUuidCache.containsKey(name.toLowerCase().trim())) {
                return true;
            }

            if (!ensureCollection()) {
                logger.severe("Could not check if player exists by name - collection unavailable: " + name);
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
        logger.info("Player cache cleared");
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
                try {
                    savePlayerToDatabase(player);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error saving dirty player: " + player.getUsername(), e);
                }
            }
        }
    }

    /**
     * Remove a player from the cache
     *
     * @param id The player UUID
     */
    public void removeFromCache(UUID id) {
        if (id == null) {
            return;
        }

        CachedPlayer player = playerCache.remove(id);
        if (player != null && player.isDirty()) {
            // Save before removing from cache
            try {
                savePlayerToDatabase(player.getPlayer());
                logger.fine("Saved dirty player before cache removal: " + id);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error saving player before cache removal: " + id, e);
            }

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
        if (player != null) {
            markPlayerDirty(player);
        }
    }

    /**
     * Convert a MongoDB document to a YakPlayer object with enhanced error handling
     *
     * @param doc The MongoDB document
     * @return The YakPlayer object
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
            player.setLastLogin(doc.getLong("last_login") != null ? doc.getLong("last_login") : 0);
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


            // Profession data
            player.setPickaxeLevel(doc.getInteger("pickaxe_level", 0));
            player.setFishingLevel(doc.getInteger("fishing_level", 0));
            player.setMiningXp(doc.getInteger("mining_xp", 0));
            player.setFishingXp(doc.getInteger("fishing_xp", 0));
            player.setFarmingLevel(doc.getInteger("farming_level", 0));
            player.setFarmingXP(doc.getInteger("farming_xp", 0));
            player.setWoodcuttingLevel(doc.getInteger("woodcutting_level", 0));
            player.setWoodcuttingXP(doc.getInteger("woodcutting_xp", 0));


            // PvP stats
            player.setT1Kills(doc.getInteger("t1_kills", 0));
            player.setT2Kills(doc.getInteger("t2_kills", 0));
            player.setT3Kills(doc.getInteger("t3_kills", 0));
            player.setT4Kills(doc.getInteger("t4_kills", 0));
            player.setT5Kills(doc.getInteger("t5_kills", 0));
            player.setT6Kills(doc.getInteger("t6_kills", 0));
            player.setKillStreak(doc.getInteger("kill_streak", 0));
            player.setBestKillStreak(doc.getInteger("best_kill_streak", 0));
            player.setPvpRating(doc.getInteger("pvp_rating", 1000));

            // World Boss tracking
            Document worldBossDamage = doc.get("world_boss_damage", Document.class);
            if (worldBossDamage != null) {
                Map<String, Integer> damageMap = new HashMap<>();
                for (String bossName : worldBossDamage.keySet()) {
                    damageMap.put(bossName, worldBossDamage.getInteger(bossName));
                }
                player.setWorldBossDamage(damageMap);
            }
            Document worldBossKills = doc.get("world_boss_kills", Document.class);
            if (worldBossKills != null) {
                Map<String, Integer> killsMap = new HashMap<>();
                for (String bossName : worldBossKills.keySet()) {
                    killsMap.put(bossName, worldBossKills.getInteger(bossName));
                }
                player.getWorldBossKills().putAll(killsMap);
            }


            // Social data
            player.setTradeDisabled(doc.getBoolean("trade_disabled", false));
            player.setBuddies(doc.getList("buddies", String.class, new ArrayList<>()));
            player.getBlockedPlayers().clear();
            List<String> blocked = doc.getList("blocked_players", String.class);
            if(blocked != null) {
                player.getBlockedPlayers().addAll(blocked);
            }


            // Energy system settings
            player.setEnergyDisabled(doc.getBoolean("energy_disabled", false));

            // Location data
            player.setWorld(doc.getString("world"));
            player.setLocationX(doc.getDouble("location_x") != null ? doc.getDouble("location_x") : 0.0);
            player.setLocationY(doc.getDouble("location_y") != null ? doc.getDouble("location_y") : 64.0);
            player.setLocationZ(doc.getDouble("location_z") != null ? doc.getDouble("location_z") : 0.0);
            player.setLocationYaw(doc.getDouble("location_yaw") != null ? doc.getDouble("location_yaw").floatValue() : 0.0f);
            player.setLocationPitch(doc.getDouble("location_pitch") != null ? doc.getDouble("location_pitch").floatValue() : 0.0f);
            player.setPreviousLocation(doc.getString("previous_location"));


            // Inventory data
            player.setSerializedInventory(doc.getString("inventory_contents"));
            player.setSerializedArmor(doc.getString("armor_contents"));
            player.setSerializedEnderChest(doc.getString("ender_chest_contents"));
            player.setSerializedOffhand(doc.getString("offhand_item"));

            // Player stats
            player.setHealth(doc.getDouble("health") != null ? doc.getDouble("health") : 20.0);
            player.setMaxHealth(doc.getDouble("max_health") != null ? doc.getDouble("max_health") : 20.0);
            player.setFoodLevel(doc.getInteger("food_level", 20));
            player.setSaturation(doc.getDouble("saturation") != null ? doc.getDouble("saturation").floatValue() : 5.0f);
            player.setXpLevel(doc.getInteger("xp_level", 0));
            player.setXpProgress(doc.getDouble("xp_progress") != null ? doc.getDouble("xp_progress").floatValue() : 0.0f);
            player.setTotalExperience(doc.getInteger("total_experience", 0));

            player.setBedSpawnLocation(doc.getString("bed_spawn_location"));
            player.setGameMode(doc.getString("gamemode") != null ? doc.getString("gamemode") : "SURVIVAL");
            player.setActivePotionEffects(doc.getList("active_potion_effects", String.class, new ArrayList<>()));

            // Achievement & Rewards
            player.getAchievements().clear();
            List<String> achievements = doc.getList("achievements", String.class);
            if(achievements != null) player.getAchievements().addAll(achievements);
            player.setAchievementPoints(doc.getInteger("achievement_points", 0));
            player.getDailyRewardsClaimed().clear();
            List<String> dailyRewards = doc.getList("daily_rewards_claimed", String.class);
            if(dailyRewards != null) player.getDailyRewardsClaimed().addAll(dailyRewards);
            player.setLastDailyReward(doc.getLong("last_daily_reward") != null ? doc.getLong("last_daily_reward") : 0);

            // Event Tracking
            Document eventsParticipatedDoc = doc.get("events_participated", Document.class);
            if(eventsParticipatedDoc != null) {
                for(String key : eventsParticipatedDoc.keySet()){
                    player.getEventsParticipated().put(key, eventsParticipatedDoc.getInteger(key));
                }
            }
            Document eventWinsDoc = doc.get("event_wins", Document.class);
            if(eventWinsDoc != null) {
                for(String key : eventWinsDoc.keySet()){
                    player.getEventWins().put(key, eventWinsDoc.getInteger(key));
                }
            }

            // Combat Stats
            player.setDamageDealt(doc.getLong("damage_dealt") != null ? doc.getLong("damage_dealt") : 0);
            player.setDamageTaken(doc.getLong("damage_taken") != null ? doc.getLong("damage_taken") : 0);
            player.setDamageBlocked(doc.getLong("damage_blocked") != null ? doc.getLong("damage_blocked") : 0);
            player.setDamageDodged(doc.getLong("damage_dodged") != null ? doc.getLong("damage_dodged") : 0);


            logger.fine("Successfully converted document to player: " + player.getUsername());
            return player;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error converting document to player", e);
            return null;
        }
    }

    /**
     * Convert a YakPlayer object to a MongoDB document with enhanced error handling
     *
     * @param player The YakPlayer object
     * @return The MongoDB document
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
            doc.append("chat_color", player.getChatColor());

            // Mount data
            doc.append("horse_tier", player.getHorseTier());
            doc.append("horse_name", player.getHorseName());


            // Guild data
            doc.append("guild_name", player.getGuildName() != null ? player.getGuildName() : "");
            doc.append("guild_rank", player.getGuildRank() != null ? player.getGuildRank() : "");
            doc.append("guild_contribution", player.getGuildContribution());

            // Toggle preferences
            doc.append("toggle_settings", new ArrayList<>(player.getToggleSettings()));
            doc.append("notification_settings", new Document(player.getNotificationSettings()));

            // Quest data
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
            doc.append("farming_xp", player.getFarmingXP());
            doc.append("woodcutting_level", player.getWoodcuttingLevel());
            doc.append("woodcutting_xp", player.getWoodcuttingXP());

            // PvP stats
            doc.append("t1_kills", player.getT1Kills());
            doc.append("t2_kills", player.getT2Kills());
            doc.append("t3_kills", player.getT3Kills());
            doc.append("t4_kills", player.getT4Kills());
            doc.append("t5_kills", player.getT5Kills());
            doc.append("t6_kills", player.getT6Kills());
            doc.append("kill_streak", player.getKillStreak());
            doc.append("best_kill_streak", player.getBestKillStreak());
            doc.append("pvp_rating", player.getPvpRating());

            // World Boss tracking
            doc.append("world_boss_damage", new Document(player.getWorldBossDamage()));
            doc.append("world_boss_kills", new Document(player.getWorldBossKills()));

            // Social data
            doc.append("trade_disabled", player.isTradeDisabled());
            doc.append("buddies", new ArrayList<>(player.getBuddies()));
            doc.append("blocked_players", new ArrayList<>(player.getBlockedPlayers()));

            // Energy system settings
            doc.append("energy_disabled", player.isEnergyDisabled());

            // Location data
            if (player.getWorld() != null) {
                doc.append("world", player.getWorld());
            }
            doc.append("location_x", player.getLocationX());
            doc.append("location_y", player.getLocationY());
            doc.append("location_z", player.getLocationZ());
            doc.append("location_yaw", (double) player.getLocationYaw());
            doc.append("location_pitch", (double) player.getLocationPitch());


            // Inventory data
            doc.append("inventory_contents", player.getSerializedInventory());
            doc.append("armor_contents", player.getSerializedArmor());
            doc.append("ender_chest_contents", player.getSerializedEnderChest());
            doc.append("offhand_item", player.getSerializedOffhand());

            // Player stats
            doc.append("health", player.getHealth());
            doc.append("max_health", player.getMaxHealth());
            doc.append("food_level", player.getFoodLevel());
            doc.append("saturation", (double) player.getSaturation());
            doc.append("xp_level", player.getXpLevel());
            doc.append("xp_progress", (double) player.getXpProgress());
            doc.append("total_experience", player.getTotalExperience());

            if (player.getBedSpawnLocation() != null) {
                doc.append("bed_spawn_location", player.getBedSpawnLocation());
            }

            doc.append("gamemode", player.getGameMode() != null ? player.getGameMode() : "SURVIVAL");
            doc.append("active_potion_effects", player.getActivePotionEffects() != null ? player.getActivePotionEffects() : new ArrayList<>());

            // Achievements & Rewards
            doc.append("achievements", new ArrayList<>(player.getAchievements()));
            doc.append("achievement_points", player.getAchievementPoints());
            doc.append("daily_rewards_claimed", new ArrayList<>(player.getDailyRewardsClaimed()));
            doc.append("last_daily_reward", player.getLastDailyReward());

            // Event Tracking
            doc.append("events_participated", new Document(player.getEventsParticipated()));
            doc.append("event_wins", new Document(player.getEventWins()));

            // Combat Stats
            doc.append("damage_dealt", player.getDamageDealt());
            doc.append("damage_taken", player.getDamageTaken());
            doc.append("damage_blocked", player.getDamageBlocked());
            doc.append("damage_dodged", player.getDamageDodged());


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
        return repositoryInitialized.get();
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
