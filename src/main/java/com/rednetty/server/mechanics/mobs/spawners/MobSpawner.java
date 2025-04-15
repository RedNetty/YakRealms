package com.rednetty.server.mechanics.mobs.spawners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.mobs.SpawnerProperties;
import com.rednetty.server.mechanics.mobs.core.MobType;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Enhanced MobSpawner with improved mob tracking and respawn handling
 */
public class MobSpawner implements Listener {
    private static MobSpawner instance;

    // Map to store all spawners
    private final Map<String, Spawner> spawners = new ConcurrentHashMap<>();
    private final Map<Location, String> spawnerLocations = new ConcurrentHashMap<>();

    // Map for players creating spawners
    private final Map<String, Location> creatingSpawner = new HashMap<>();
    private final Map<String, SpawnerCreationSession> spawnerCreationSessions = new HashMap<>();

    // Spawner groups for batch operations
    private final Map<String, Set<String>> spawnerGroups = new HashMap<>();

    // Map for entity to spawner tracking
    private final Map<UUID, String> entityToSpawner = new ConcurrentHashMap<>();

    // Map for visualization entities
    private final Map<String, List<ArmorStand>> visualizations = new HashMap<>();
    // Template configurations
    private final Map<String, String> spawnerTemplates = new HashMap<>();
    private final Logger logger;
    private final YakRealms plugin;
    // Configuration
    private boolean spawnersEnabled = true;
    private boolean debug = false;
    private int maxMobsPerSpawner = 10;
    private double playerDetectionRange = 40.0;
    private double mobRespawnDistanceCheck = 25.0;
    private boolean defaultVisibility = false;
    // Tasks
    private org.bukkit.scheduler.BukkitTask spawnerTask;
    private org.bukkit.scheduler.BukkitTask respawnTask;
    private org.bukkit.scheduler.BukkitTask cleanupTask;
    private org.bukkit.scheduler.BukkitTask hologramTask;
    private org.bukkit.scheduler.BukkitTask autoSaveTask;
    private org.bukkit.scheduler.BukkitTask sessionCleanupTask;
    private org.bukkit.scheduler.BukkitTask visualizationCleanupTask;

    /**
     * Constructor
     */
    private MobSpawner() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.debug = plugin.isDebugMode();
        this.defaultVisibility = plugin.getConfig().getBoolean("mechanics.mobs.spawner-default-visibility", false);

        // Initialize spawner templates
        initializeTemplates();
    }

    /**
     * Get the singleton instance
     *
     * @return MobSpawner instance
     */
    public static MobSpawner getInstance() {
        if (instance == null) {
            instance = new MobSpawner();
        }
        return instance;
    }

    /**
     * Initialize the spawner system
     */
    public void initialize() {
        try {
            // Register events
            Bukkit.getPluginManager().registerEvents(this, plugin);

            // Load configuration
            loadConfig();

            // Load spawners from config
            loadSpawners();

            // Start spawner task
            startTasks();

            logger.info("[MobSpawner] has been initialized with " + spawners.size() + " spawners");
        } catch (Exception e) {
            logger.severe("[MobSpawner] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initialize spawner templates
     */
    private void initializeTemplates() {
        // T1-2 Basic spawner
        spawnerTemplates.put("basic_t1", "skeleton:1@false#2,zombie:1@false#2");
        spawnerTemplates.put("basic_t2", "skeleton:2@false#2,zombie:2@false#2");

        // T3-4 Standard spawners
        spawnerTemplates.put("standard_t3", "skeleton:3@false#3,zombie:3@false#2,spider:3@false#1");
        spawnerTemplates.put("standard_t4", "skeleton:4@false#3,zombie:4@false#2,spider:4@false#1");

        // T5 Advanced spawners
        spawnerTemplates.put("advanced_t5", "skeleton:5@false#2,witherskeleton:5@false#2,zombie:5@false#1");

        // Elite spawners
        spawnerTemplates.put("elite_t3", "skeleton:3@true#1,zombie:3@true#1");
        spawnerTemplates.put("elite_t4", "skeleton:4@true#1,witherskeleton:4@true#1");
        spawnerTemplates.put("elite_t5", "witherskeleton:5@true#1");

        // Mixed spawners
        spawnerTemplates.put("mixed_t3", "skeleton:3@false#2,zombie:3@false#2,skeleton:3@true#1");
        spawnerTemplates.put("mixed_t4", "skeleton:4@false#2,witherskeleton:4@false#2,witherskeleton:4@true#1");

        // Special spawners
        spawnerTemplates.put("magma_t4", "magmacube:4@false#4,magmacube:4@true#1");
        spawnerTemplates.put("spider_t4", "spider:4@false#3,cavespider:4@false#2");
        spawnerTemplates.put("imp_t3", "imp:3@false#4");

        // Boss spawners
        spawnerTemplates.put("boss_t5", "skeleton:5@true#1");

        // T6 spawners (if enabled)
        spawnerTemplates.put("frozen_t6", "skeleton:6@false#2,witherskeleton:6@false#1");
    }

    /**
     * Load configuration values
     */
    private void loadConfig() {
        try {
            // Make sure config exists
            plugin.saveDefaultConfig();

            // Load values from config
            this.debug = plugin.getConfig().getBoolean("debug", false);
            this.maxMobsPerSpawner = plugin.getConfig().getInt("mechanics.mobs.max-mobs-per-spawner", 10);
            this.playerDetectionRange = plugin.getConfig().getDouble("mechanics.mobs.player-detection-range", 40.0);
            this.mobRespawnDistanceCheck = plugin.getConfig().getDouble("mechanics.mobs.mob-respawn-distance-check", 25.0);
            this.defaultVisibility = plugin.getConfig().getBoolean("mechanics.mobs.spawner-default-visibility", false);

            // Validate config values
            if (maxMobsPerSpawner < 1) maxMobsPerSpawner = 1;
            if (maxMobsPerSpawner > 50) maxMobsPerSpawner = 50;

            if (playerDetectionRange < 10) playerDetectionRange = 10;
            if (playerDetectionRange > 100) playerDetectionRange = 100;

            if (mobRespawnDistanceCheck < 5) mobRespawnDistanceCheck = 5;
            if (mobRespawnDistanceCheck > 50) mobRespawnDistanceCheck = 50;

            if (debug) {
                logger.info("[MobSpawner] Configuration loaded: Debug=" + debug + ", MaxMobs=" + maxMobsPerSpawner + ", DetectionRange=" + playerDetectionRange + ", RespawnCheck=" + mobRespawnDistanceCheck + ", DefaultVisibility=" + defaultVisibility);
            }
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error loading config, using defaults: " + e.getMessage());
        }
    }

    /**
     * Start recurring tasks
     */
    private void startTasks() {
        logger.info("[MobSpawner] Starting tasks...");

        // Task to process spawners (initial spawns)
        spawnerTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                processSpawners();
            }
        }.runTaskTimer(plugin, 100L, 60L); // Every 3 seconds

        // Task to process respawns (check for mobs that need to respawn)
        respawnTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                processRespawns();
            }
        }.runTaskTimer(plugin, 120L, 20L); // Every 1 second

        // Task to clean up orphaned data
        cleanupTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphanedEntries();
            }
        }.runTaskTimer(plugin, 300L, 6000L); // Every 5 minutes

        // Task to update holograms
        hologramTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                updateAllHolograms();
            }
        }.runTaskTimer(plugin, 100L, 100L); // Every 5 seconds

        // Task for auto-saving
        autoSaveTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (debug) {
                    logger.info("[MobSpawner] Performing auto-save of spawners...");
                }
                saveSpawners();
            }
        }.runTaskTimer(plugin, 1200L, 6000L); // Every 5 minutes

        // Task to clean up expired creation sessions
        sessionCleanupTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every 1 minute

        // Task to clean up visualizations
        visualizationCleanupTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                cleanupVisualizations();
            }
        }.runTaskTimer(plugin, 200L, 200L); // Every 10 seconds

        logger.info("[MobSpawner] All tasks started successfully");
    }

    /**
     * Clean up expired spawner creation sessions
     */
    private void cleanupExpiredSessions() {
        List<String> expiredSessions = new ArrayList<>();

        // Find expired sessions (older than 5 minutes)
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, SpawnerCreationSession> entry : spawnerCreationSessions.entrySet()) {
            if (currentTime - entry.getValue().getStartTime() > 300000) { // 5 minutes
                expiredSessions.add(entry.getKey());
            }
        }

        // Remove expired sessions
        for (String playerName : expiredSessions) {
            spawnerCreationSessions.remove(playerName);
            creatingSpawner.remove(playerName);

            // Notify player if online
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Your spawner creation session has expired.");
            }
        }

        if (!expiredSessions.isEmpty() && debug) {
            logger.info("[MobSpawner] Cleaned up " + expiredSessions.size() + " expired spawner creation sessions");
        }
    }

    /**
     * Process spawners - handle initial spawning
     */
    private void processSpawners() {
        if (!spawnersEnabled) return;

        int spawnedCount = 0;
        int processedSpawners = 0;

        for (Spawner spawner : new ArrayList<>(spawners.values())) {
            try {
                processedSpawners++;

                // Check if this spawner can spawn mobs
                if (canSpawnerSpawnMobs(spawner)) {
                    int spawned = spawner.spawnAllMobs();
                    spawnedCount += spawned;

                    if (spawned > 0 && debug) {
                        logger.info("[MobSpawner] INITIAL SPAWN: " + spawned + " mobs at " + formatLocation(spawner.getLocation()));
                    }
                }
            } catch (Exception e) {
                logger.warning("[MobSpawner] Error processing spawner: " + e.getMessage());
                if (debug) {
                    e.printStackTrace();
                }
            }
        }

        if (debug && spawnedCount > 0) {
            logger.info("[MobSpawner] Processed " + processedSpawners + " spawners: Spawned=" + spawnedCount);
        }
    }

    /**
     * Check if a spawner can spawn mobs
     *
     * @param spawner The spawner to check
     * @return true if it can spawn mobs
     */
    private boolean canSpawnerSpawnMobs(Spawner spawner) {
        // Check if already at capacity
        int currentActive = spawner.getTotalActiveMobs();
        int desiredTotal = spawner.getTotalDesiredMobs();

        if (currentActive >= desiredTotal) {
            return false;
        }

        // Check if there are any respawning mobs -
        // CRITICAL: Only do initial spawn if there are NO respawning mobs
        // This ensures we don't bypass the respawn queue
        return !spawner.hasRespawningMobs();
    }

    /**
     * Process respawns - check for mobs that need to respawn
     */
    private void processRespawns() {
        if (!spawnersEnabled) return;

        int respawnedCount = 0;
        int processedSpawners = 0;

        for (Spawner spawner : spawners.values()) {
            try {
                processedSpawners++;
                int respawned = spawner.processRespawns();
                respawnedCount += respawned;

                if (respawned > 0 && debug) {
                    logger.info("[MobSpawner] Respawned " + respawned + " mobs at " + formatLocation(spawner.getLocation()));
                }
            } catch (Exception e) {
                logger.warning("[MobSpawner] Error processing respawns: " + e.getMessage());
                if (debug) {
                    e.printStackTrace();
                }
            }
        }

        if (debug && respawnedCount > 0) {
            logger.info("[MobSpawner] Processed " + processedSpawners + " spawners for respawns, respawned " + respawnedCount + " mobs");
        }
    }

    /**
     * Clean up spawner visualizations that have expired
     */
    private void cleanupVisualizations() {
        long currentTime = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, List<ArmorStand>> entry : visualizations.entrySet()) {
            String key = entry.getKey();

            // Format is playerUUID_timestamp, so split to get timestamp
            String[] parts = key.split("_");
            if (parts.length == 2) {
                try {
                    long timestamp = Long.parseLong(parts[1]);
                    // Remove visualizations older than 30 seconds
                    if (currentTime - timestamp > 30000) {
                        // Remove the visualization entities
                        for (ArmorStand stand : entry.getValue()) {
                            if (stand != null && !stand.isDead()) {
                                stand.remove();
                            }
                        }
                        toRemove.add(key);
                    }
                } catch (NumberFormatException ignored) {
                    // If we can't parse the timestamp, just skip this entry
                }
            }
        }

        // Remove processed entries
        for (String key : toRemove) {
            visualizations.remove(key);
        }
    }

    /**
     * Clean up orphaned entries from all trackers
     */
    private void cleanupOrphanedEntries() {
        try {
            // Clean up entity to spawner mappings
            int entityRemoveCount = 0;
            List<UUID> toRemove = new ArrayList<>();

            for (UUID entityId : entityToSpawner.keySet()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    toRemove.add(entityId);
                }
            }

            for (UUID entityId : toRemove) {
                entityToSpawner.remove(entityId);
                entityRemoveCount++;
            }

            // Process location mappings to ensure consistency
            int locationRemoveCount = 0;
            List<Location> invalidLocations = new ArrayList<>();

            for (Map.Entry<Location, String> entry : spawnerLocations.entrySet()) {
                if (!spawners.containsKey(entry.getValue())) {
                    invalidLocations.add(entry.getKey());
                }
            }

            for (Location loc : invalidLocations) {
                spawnerLocations.remove(loc);
                locationRemoveCount++;
            }

            // Update spawner groups
            rebuildSpawnerGroups();

            if (debug && (entityRemoveCount > 0 || locationRemoveCount > 0)) {
                logger.info("[MobSpawner] Cleanup: removed " + entityRemoveCount + " invalid entity references and " + locationRemoveCount + " invalid location mappings");
            }
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error during cleanup: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Update all visible spawner holograms
     *
     * @return Number of holograms updated
     */
    public int updateAllHolograms() {
        if (!spawnersEnabled) return 0;

        int updatedCount = 0;

        try {
            for (Spawner spawner : spawners.values()) {
                if (spawner.isVisible()) {
                    spawner.updateHologram();
                    updatedCount++;
                }
            }

            if (debug && updatedCount > 0) {
                logger.info("[MobSpawner] Updated " + updatedCount + " spawner holograms");
            }
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error updating holograms: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }

        return updatedCount;
    }

    /**
     * Load spawners with updated format handling
     */
    public void loadSpawners() {
        try {
            // Clear existing data
            spawners.clear();
            spawnerLocations.clear();
            entityToSpawner.clear();

            // Get direct path to the file
            File file = new File(plugin.getDataFolder(), "spawners.yml");

            if (!file.exists()) {
                logger.warning("[MobSpawner] No spawners.yml file found at " + file.getAbsolutePath());
                return;
            }

            logger.info("[MobSpawner] Found spawners.yml, size: " + file.length() + " bytes");

            // Load configuration
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            int count = 0;
            int failed = 0;

            // Loop through all root keys (should be location strings)
            for (String key : config.getKeys(false)) {
                try {
                    // Skip if it's not a spawn location key (should contain commas)
                    if (!key.contains(",")) {
                        continue;
                    }

                    // Get the spawner data - check both direct and section format
                    String data = getSpawnerDataFromConfig(config, key);

                    // If we couldn't get data either way, skip this spawner
                    if (data == null || data.isEmpty()) {
                        logger.warning("[MobSpawner] No spawner data for key: " + key);
                        failed++;
                        continue;
                    }

                    // Parse location
                    Location loc = parseLocationFromKey(key);
                    if (loc == null) {
                        logger.warning("[MobSpawner] Invalid location format: " + key);
                        failed++;
                        continue;
                    }

                    // Validate the data
                    if (!validateSpawnerData(data)) {
                        logger.warning("[MobSpawner] Invalid spawner data: " + data);
                        failed++;
                        continue;
                    }

                    // Get visibility
                    boolean visible = config.getBoolean(key + ".visible", defaultVisibility);

                    // Get display mode
                    int displayMode = config.getInt(key + ".displayMode", 0);

                    // Create the spawner
                    Spawner spawner = new Spawner(loc, data, visible);
                    spawner.setDisplayMode(displayMode);

                    // Generate unique ID for this spawner
                    String spawnerId = generateSpawnerId(loc);

                    // Load enhanced properties
                    if (config.isConfigurationSection(key + ".properties")) {
                        ConfigurationSection propsSection = config.getConfigurationSection(key + ".properties");
                        SpawnerProperties props = SpawnerProperties.loadFromConfig(propsSection);
                        spawner.setProperties(props);

                        // Add to groups if needed
                        if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
                            spawnerGroups.computeIfAbsent(props.getSpawnerGroup(), k -> new HashSet<>()).add(spawnerId);
                        }
                    }

                    // Try to set block type based on visibility
                    try {
                        setSpawnerBlock(loc.getBlock(), spawnerId, visible);
                    } catch (Exception e) {
                        logger.warning("[MobSpawner] Error setting block state for spawner at " + formatLocation(loc) + ": " + e.getMessage());
                        // Still count as loaded since we have the data
                    }

                    // Store the spawner and location mapping
                    spawners.put(spawnerId, spawner);
                    spawnerLocations.put(loc, spawnerId);
                    count++;

                    if (debug) {
                        logger.info("[MobSpawner] Loaded spawner: " + key + " = " + data + " (visible=" + visible + ", id=" + spawnerId + ")");
                    }
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Error loading spawner from key " + key + ": " + e.getMessage());
                    failed++;
                }
            }

            logger.info("[MobSpawner] Successfully loaded " + count + " spawners" + (failed > 0 ? ", " + failed + " failed to load" : ""));
        } catch (Exception e) {
            logger.severe("[MobSpawner] Critical error loading spawners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get spawner data from config, handling both old and new formats
     *
     * @param config The configuration
     * @param key    The key to get data for
     * @return The spawner data string or null if not found
     */
    private String getSpawnerDataFromConfig(YamlConfiguration config, String key) {
        // First try to get it from the new format (.data property)
        if (config.contains(key + ".data")) {
            return config.getString(key + ".data");
        }
        // Try the old direct format as a fallback
        else if (!config.isConfigurationSection(key)) {
            return config.getString(key);
        }

        return null;
    }

    /**
     * Parse location from a key string
     *
     * @param key The key in format "world,x,y,z"
     * @return Parsed location or null if invalid
     */
    private Location parseLocationFromKey(String key) {
        try {
            String[] parts = key.split(",");
            if (parts.length < 4) {
                return null;
            }

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                logger.warning("[MobSpawner] World not found: " + parts[0]);
                return null;
            }

            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            // Create location - CRITICAL: must match exact format used in save
            return new Location(world, x + 0.5, y, z + 0.5);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generate a unique ID for a spawner based on its location
     *
     * @param location The spawner location
     * @return Unique ID string
     */
    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
    }

    /**
     * Rebuild spawner groups based on individual spawner properties
     */
    private void rebuildSpawnerGroups() {
        // Clear existing groups
        spawnerGroups.clear();

        // Build groups from spawner properties
        for (Map.Entry<String, Spawner> entry : spawners.entrySet()) {
            String spawnerId = entry.getKey();
            Spawner spawner = entry.getValue();
            SpawnerProperties props = spawner.getProperties();

            String groupName = props.getSpawnerGroup();
            if (groupName != null && !groupName.isEmpty()) {
                // Add to group
                spawnerGroups.computeIfAbsent(groupName, k -> new HashSet<>()).add(spawnerId);
            }
        }

        if (debug) {
            logger.info("[MobSpawner] Rebuilt " + spawnerGroups.size() + " spawner groups");
        }
    }

    /**
     * Save spawners with proper data format
     */
    public void saveSpawners() {
        try {
            // Get direct path to the file
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File file = new File(dataFolder, "spawners.yml");
            File backupFile = new File(dataFolder, "spawners.yml.bak");

            // Create backup of existing file if it exists
            if (file.exists()) {
                try {
                    Files.copy(file.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Failed to create backup: " + e.getMessage());
                }
            }

            // Create new configuration
            YamlConfiguration config = new YamlConfiguration();

            int count = 0;
            logger.info("[MobSpawner] Beginning spawner save operation, found " + spawners.size() + " spawners to save");

            // Save each spawner
            for (Map.Entry<String, Spawner> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                Spawner spawner = entry.getValue();
                Location loc = spawner.getLocation();

                if (loc == null || loc.getWorld() == null) {
                    logger.warning("[MobSpawner] Skipping null location or world for spawner: " + spawnerId);
                    continue;
                }

                // FIXED APPROACH: Store data in a section rather than directly
                String key = loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

                // Create a section for this spawner
                config.createSection(key);

                // Store the actual spawner data as 'data' property
                config.set(key + ".data", spawner.getDataString());

                // Store visibility as another property
                config.set(key + ".visible", spawner.isVisible());

                // Store display mode
                config.set(key + ".displayMode", spawner.getDisplayMode());

                // Store enhanced properties
                SpawnerProperties props = spawner.getProperties();
                ConfigurationSection propsSection = config.createSection(key + ".properties");
                props.saveToConfig(propsSection);

                count++;

                if (debug) {
                    logger.info("[MobSpawner] Added spawner to save: " + key + " with data: " + spawner.getDataString() + " (visible=" + spawner.isVisible() + ")");
                }
            }

            // FORCE save to file
            config.save(file);

            logger.info("[MobSpawner] Successfully saved " + count + " spawners to " + file.getAbsolutePath());
            logger.info("[MobSpawner] File exists: " + file.exists() + ", File size: " + file.length() + " bytes");
        } catch (Exception e) {
            logger.severe("[MobSpawner] Critical error saving spawners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if debug mode is enabled
     *
     * @return true if debug mode is enabled
     */
    public boolean isDebugMode() {
        return debug;
    }

    /**
     * Set debug mode
     *
     * @param debug Whether debug mode is enabled
     */
    public void setDebugMode(boolean debug) {
        this.debug = debug;
    }

    /**
     * Enable or disable spawners
     *
     * @param enabled Whether spawners should be enabled
     */
    public void setSpawnersEnabled(boolean enabled) {
        spawnersEnabled = enabled;

        if (debug) {
            logger.info("[MobSpawner] Spawners " + (enabled ? "enabled" : "disabled"));
        }
    }

    /**
     * Check if spawners are enabled
     *
     * @return true if spawners are enabled
     */
    public boolean areSpawnersEnabled() {
        return spawnersEnabled;
    }

    /**
     * Reset all spawners
     */
    public void resetAllSpawners() {
        for (Spawner spawner : spawners.values()) {
            spawner.reset();
        }

        logger.info("[MobSpawner] All spawners have been reset");
    }

    /**
     * Get all spawners
     *
     * @return Map of locations to spawner data
     */
    public Map<Location, String> getAllSpawners() {
        Map<Location, String> result = new HashMap<>();

        for (Map.Entry<Location, String> entry : spawnerLocations.entrySet()) {
            String spawnerId = entry.getValue();
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                result.put(entry.getKey(), spawner.getDataString());
            }
        }

        return result;
    }

    /**
     * Find the spawner that contains the given location
     *
     * @param location Location to check
     * @return Spawner object or null if not found
     */
    private Spawner getSpawnerAtLocation(Location location) {
        if (location == null) return null;

        // Try direct lookup first
        String spawnerId = spawnerLocations.get(location);
        if (spawnerId != null) {
            return spawners.get(spawnerId);
        }

        // If direct lookup fails, try to find by block coordinates
        for (Map.Entry<Location, String> entry : spawnerLocations.entrySet()) {
            if (isSameBlock(entry.getKey(), location)) {
                return spawners.get(entry.getValue());
            }
        }

        return null;
    }

    /**
     * Get spawner ID from a location
     *
     * @param location The location to check
     * @return Spawner ID or null if not found
     */
    private String getSpawnerIdFromLocation(Location location) {
        if (location == null) return null;

        // Try direct lookup first
        String spawnerId = spawnerLocations.get(location);
        if (spawnerId != null) {
            return spawnerId;
        }

        // If direct lookup fails, try to find by block coordinates
        for (Map.Entry<Location, String> entry : spawnerLocations.entrySet()) {
            if (isSameBlock(entry.getKey(), location)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Check if two locations are in the same block
     *
     * @param loc1 First location
     * @param loc2 Second location
     * @return true if they are in the same block
     */
    private boolean isSameBlock(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
                loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ();
    }

    /**
     * Add a spawner at a location
     *
     * @param location The location
     * @param data     Spawner data string
     * @return true if successful
     */
    public boolean addSpawner(Location location, String data) {
        if (location == null) {
            logger.warning("[MobSpawner] Cannot add spawner: location is null");
            return false;
        }

        if (data == null || data.isEmpty()) {
            logger.warning("[MobSpawner] Cannot add spawner: data is null or empty");
            return false;
        }

        // Trim the data to avoid whitespace issues
        data = data.trim();

        // Debug output
        if (debug) {
            logger.info("[MobSpawner] Adding spawner at " + formatLocation(location) + " with data: " + data);
        }

        // Validate data format
        if (!validateSpawnerData(data)) {
            logger.warning("[MobSpawner] Data validation failed: " + data);
            return false;
        }

        // Create standardized location (center of block)
        Location spawnerLoc = normalizeLocation(location);

        // Generate unique ID for this spawner
        String spawnerId = generateSpawnerId(spawnerLoc);

        // Check if a spawner already exists with this ID
        if (spawners.containsKey(spawnerId)) {
            // Update existing spawner
            Spawner spawner = spawners.get(spawnerId);
            spawner.parseSpawnerData(data);

            // Update location mapping in case it's missing
            spawnerLocations.put(spawnerLoc, spawnerId);

            if (debug) {
                logger.info("[MobSpawner] Updated existing spawner at " + formatLocation(spawnerLoc));
            }

            return true;
        }

        // Create new spawner
        Spawner spawner = new Spawner(spawnerLoc, data, defaultVisibility);

        // Try to set block type based on visibility
        try {
            // Set block type based on visibility
            if (setSpawnerBlock(location.getBlock(), spawnerId, defaultVisibility)) {
                // Store the spawner and location mapping
                spawners.put(spawnerId, spawner);
                spawnerLocations.put(spawnerLoc, spawnerId);

                // Save the changes
                saveSpawners();

                if (debug) {
                    logger.info("[MobSpawner] Created new spawner at " + formatLocation(spawnerLoc));
                }

                return true;
            } else {
                logger.warning("[MobSpawner] Failed to set block state for spawner at " +
                        formatLocation(location) + " - trying alternate approach");

                // Fall back: Still register the spawner even if we couldn't change the block
                spawners.put(spawnerId, spawner);
                spawnerLocations.put(spawnerLoc, spawnerId);

                // Save the changes
                saveSpawners();

                if (debug) {
                    logger.info("[MobSpawner] Created spawner with fallback method at " + formatLocation(spawnerLoc));
                }

                return true;
            }
        } catch (Exception e) {
            logger.severe("[MobSpawner] Critical error creating spawner: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Set the block type and metadata for a spawner
     *
     * @param block     The block to set
     * @param spawnerId The spawner ID
     * @param visible   Whether the spawner should be visible
     * @return true if successful
     */
    private boolean setSpawnerBlock(Block block, String spawnerId, boolean visible) {
        try {
            // First, add metadata regardless of what happens with the block type
            block.setMetadata("isSpawner", new FixedMetadataValue(plugin, true));
            block.setMetadata("spawnerId", new FixedMetadataValue(plugin, spawnerId));

            // IMPROVED: Force block type change based on visibility
            Material blockType = visible ? Material.SPAWNER : Material.AIR;

            // Log the attempt for debugging
            if (debug) {
                logger.info("[MobSpawner] Setting block at " + formatLocation(block.getLocation()) +
                        " to " + blockType + " (current type: " + block.getType() + ")");
            }

            // Force the block type change
            block.setType(blockType, true);  // true forces the update

            // Verify the change was successful
            if (block.getType() != blockType) {
                logger.warning("[MobSpawner] Failed to set block type to " + blockType +
                        " at " + formatLocation(block.getLocation()));

                // Try again with physics update
                block.setType(blockType, false);

            }

            return block.getType() == blockType;
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error setting block state: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Normalize a location to the center of the block
     *
     * @param location The location to normalize
     * @return Normalized location
     */
    private Location normalizeLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.5);
    }

    /**
     * Add a spawner using a predefined template
     *
     * @param location     The location to add the spawner
     * @param templateName The name of the template
     * @return true if created successfully
     */
    public boolean addSpawnerFromTemplate(Location location, String templateName) {
        if (location == null || templateName == null) return false;

        // Debug output to troubleshoot
        if (debug) {
            logger.info("[MobSpawner] Attempting to find template: " + templateName);
            logger.info("[MobSpawner] Available templates: " + String.join(", ", spawnerTemplates.keySet()));
        }

        // Get template data - strip any whitespace and ensure lowercase
        String cleanTemplateName = templateName.toLowerCase().trim();
        String templateData = spawnerTemplates.get(cleanTemplateName);

        // If not found by direct lookup, try a case-insensitive search
        if (templateData == null) {
            for (Map.Entry<String, String> entry : spawnerTemplates.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cleanTemplateName)) {
                    templateData = entry.getValue();
                    if (debug) {
                        logger.info("[MobSpawner] Found template via case-insensitive match: " + entry.getKey());
                    }
                    break;
                }
            }
        }

        if (templateData == null) {
            logger.warning("[MobSpawner] Template not found: " + templateName);
            logger.warning("[MobSpawner] Available templates: " + String.join(", ", spawnerTemplates.keySet()));
            return false;
        }

        // Directly set block type before adding spawner
        Block block = location.getBlock();
        if (block.getType() == Material.AIR || block.getType() == Material.SPAWNER) {
            String spawnerId = generateSpawnerId(location);
            setSpawnerBlock(block, spawnerId, defaultVisibility);
        }

        // Add the spawner with template data
        if (debug) {
            logger.info("[MobSpawner] Using template data: " + templateData + " for location " + formatLocation(location));
        }

        // Create standardized location (center of block)
        Location spawnerLoc = normalizeLocation(location);

        // Create new spawner with template data
        Spawner spawner = new Spawner(spawnerLoc, templateData, defaultVisibility);

        // Generate unique ID for this spawner
        String spawnerId = generateSpawnerId(spawnerLoc);

        // Store the spawner and location mapping
        spawners.put(spawnerId, spawner);
        spawnerLocations.put(spawnerLoc, spawnerId);

        // Save the changes
        saveSpawners();

        if (debug) {
            logger.info("[MobSpawner] Successfully created spawner from template: " + templateName);
        }

        return true;
    }

    /**
     * Remove a spawner
     *
     * @param location The location
     * @return true if removed
     */
    public boolean removeSpawner(Location location) {
        if (location == null) return false;

        // Find the exact location in the map
        String spawnerId = null;
        Location exactLoc = null;

        for (Map.Entry<Location, String> entry : spawnerLocations.entrySet()) {
            if (isSameBlock(entry.getKey(), location)) {
                spawnerId = entry.getValue();
                exactLoc = entry.getKey();
                break;
            }
        }

        if (spawnerId != null && spawners.containsKey(spawnerId)) {
            // Remove hologram
            Spawner spawner = spawners.get(spawnerId);
            spawner.removeHologram();

            // Remove from maps
            spawners.remove(spawnerId);
            spawnerLocations.remove(exactLoc);

            // Remove block metadata
            Block block = location.getBlock();
            if (block.hasMetadata("isSpawner")) {
                block.removeMetadata("isSpawner", plugin);
            }
            if (block.hasMetadata("spawnerId")) {
                block.removeMetadata("spawnerId", plugin);
            }

            // Rebuild spawner groups
            rebuildSpawnerGroups();

            // Save the changes
            saveSpawners();

            if (debug) {
                logger.info("[MobSpawner] Removed spawner at " + formatLocation(location));
            }

            return true;
        }

        return false;
    }

    /**
     * Create or update hologram for a spawner
     *
     * @param location The spawner location
     */
    public void createOrUpdateSpawnerHologram(Location location) {
        if (location == null) return;

        try {
            Spawner spawner = getSpawnerAtLocation(location);
            if (spawner != null) {
                spawner.updateHologram();

                if (debug) {
                    logger.info("[MobSpawner] Updated hologram for spawner at " + formatLocation(location));
                }
            }
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error updating hologram at " + formatLocation(location) + ": " + e.getMessage());
        }
    }

    /**
     * Remove a hologram for a spawner
     *
     * @param location The spawner location
     */
    public void removeSpawnerHologram(Location location) {
        if (location == null) return;

        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner != null) {
            spawner.removeHologram();
        }
    }

    /**
     * Register a mob that was spawned from this location
     *
     * @param spawnerLocation The spawner location
     * @param entity          The spawned entity
     */
    public void registerMobSpawned(Location spawnerLocation, LivingEntity entity) {
        if (spawnerLocation == null || entity == null) return;

        // Find the spawner
        Spawner spawner = getSpawnerAtLocation(spawnerLocation);
        if (spawner == null) {
            // Try to find nearest spawner
            spawner = findNearestSpawnerObject(spawnerLocation, mobRespawnDistanceCheck);
        }

        if (spawner != null) {
            String spawnerId = getSpawnerIdFromLocation(spawner.getLocation());

            if (spawnerId != null) {
                // Track this entity
                entityToSpawner.put(entity.getUniqueId(), spawnerId);

                // Add metadata to track which spawner this mob belongs to
                entity.setMetadata("spawner", new FixedMetadataValue(plugin, spawnerId));

                // Add group metadata if in a group
                SpawnerProperties props = spawner.getProperties();
                if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
                    entity.setMetadata("spawnerGroup", new FixedMetadataValue(plugin, props.getSpawnerGroup()));
                }

                // Update hologram if visible
                if (spawner.isVisible()) {
                    spawner.updateHologram();
                }

                if (debug) {
                    logger.info("[MobSpawner] Registered spawned mob " + entity.getUniqueId() + " at " + formatLocation(spawnerLocation) + " with spawner " + spawnerId);
                }
            }
        }
    }

    /**
     * Register a mob death from this spawner
     *
     * @param spawnerLocation The spawner location
     * @param entityId        UUID of the entity that died
     */
    public void registerMobDeath(Location spawnerLocation, UUID entityId) {
        if (entityId == null) return;

        // Try to find the spawner from entity mapping
        String spawnerId = entityToSpawner.remove(entityId);

        if (spawnerId != null && spawners.containsKey(spawnerId)) {
            // Register the death with this spawner
            Spawner spawner = spawners.get(spawnerId);
            spawner.registerMobDeath(entityId);

            // Log the registration
            logger.info("[MobSpawner] Registered mob death for " + entityId + " with spawner " + spawnerId);
            return;
        }

        // If we couldn't find by ID mapping, try by location
        if (spawnerLocation != null) {
            Spawner spawner = getSpawnerAtLocation(spawnerLocation);
            if (spawner != null) {
                spawner.registerMobDeath(entityId);

                logger.info("[MobSpawner] Registered mob death for " + entityId + " with spawner at " + formatLocation(spawnerLocation));
            } else {
                logger.warning("[MobSpawner] Could not find spawner at location: " + formatLocation(spawnerLocation));
            }
        } else {
            logger.warning("[MobSpawner] No spawner location provided for mob death registration");
        }
    }

    /**
     * Register a mob death by spawner ID
     *
     * @param spawnerId The spawner ID
     * @param entityId  The entity UUID
     * @return true if registered successfully
     */
    public boolean registerMobDeathById(String spawnerId, UUID entityId) {
        if (spawnerId == null || entityId == null) return false;

        Spawner spawner = spawners.get(spawnerId);
        if (spawner != null) {
            spawner.registerMobDeath(entityId);

            logger.info("[MobSpawner] Registered mob death with spawner ID: " + spawnerId);
            return true;
        }

        logger.warning("[MobSpawner] Could not find spawner with ID: " + spawnerId);
        return false;
    }

    /**
     * Get active mob count for a specific spawner
     *
     * @param location The spawner location
     * @return Number of active mobs
     */
    public int getActiveMobCount(Location location) {
        if (location == null) return 0;

        Spawner spawner = getSpawnerAtLocation(location);
        return spawner != null ? spawner.getTotalActiveMobs() : 0;
    }

    /**
     * Check if a spawner is visible
     *
     * @param location The spawner location
     * @return true if visible
     */
    public boolean isSpawnerVisible(Location location) {
        if (location == null) return false;

        Spawner spawner = getSpawnerAtLocation(location);
        return spawner != null && spawner.isVisible();
    }

    /**
     * Set visibility for a spawner
     *
     * @param location The spawner location
     * @param visible  Whether to show the spawner
     * @return true if successful
     */
    public boolean setSpawnerVisibility(Location location, boolean visible) {
        if (location == null) return false;

        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return false;

        // Update visibility
        spawner.setVisible(visible);

        // Update block type
        Block block = location.getBlock();

        // Get spawner ID for metadata
        String spawnerId = getSpawnerIdFromLocation(location);
        if (spawnerId == null) return false;

        // Update block type and metadata
        boolean result = setSpawnerBlock(block, spawnerId, visible);

        if (result) {
            // Save changes
            saveSpawners();

            if (debug) {
                logger.info("[MobSpawner] Set spawner visibility to " + visible + " at " + formatLocation(location));
            }
        }

        return result;
    }

    /**
     * Toggle visibility for spawners in a radius
     *
     * @param center Center location
     * @param radius Radius to check
     * @param show   Whether to show or hide
     * @return Number of spawners affected
     */
    public int toggleSpawnerVisibility(Location center, int radius, boolean show) {
        if (center == null) return 0;

        List<Location> nearbySpawners = findSpawnersInRadius(center, radius);
        int count = 0;

        for (Location loc : nearbySpawners) {
            boolean currentlyVisible = isSpawnerVisible(loc);

            if (show != currentlyVisible) {
                if (setSpawnerVisibility(loc, show)) {
                    count++;
                }
            }
        }

        if (count > 0 && debug) {
            logger.info("[MobSpawner] " + (show ? "Showed" : "Hid") + " " + count + " spawners within " + radius + " blocks of " + formatLocation(center));
        }

        return count;
    }

    /**
     * Find all spawners within a radius
     *
     * @param center Center location
     * @param radius Radius to search
     * @return List of spawner locations
     */
    public List<Location> findSpawnersInRadius(Location center, double radius) {
        List<Location> result = new ArrayList<>();
        if (center == null) return result;

        double radiusSquared = radius * radius;

        for (Location loc : spawnerLocations.keySet()) {
            if (loc.getWorld().equals(center.getWorld()) && loc.distanceSquared(center) <= radiusSquared) {
                result.add(loc);
            }
        }

        return result;
    }

    /**
     * Find nearest spawner to a location
     *
     * @param location    The location to check from
     * @param maxDistance Maximum distance to check
     * @return Nearest spawner location or null if none found
     */
    public Location findNearestSpawner(Location location, double maxDistance) {
        if (location == null) return null;

        double closestDistSq = maxDistance * maxDistance;
        Location closest = null;

        for (Location spawnerLoc : spawnerLocations.keySet()) {
            if (spawnerLoc.getWorld().equals(location.getWorld())) {
                double distSq = spawnerLoc.distanceSquared(location);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = spawnerLoc;
                }
            }
        }

        return closest;
    }

    /**
     * Find the nearest spawner object
     *
     * @param location    The location to check from
     * @param maxDistance Maximum distance to check
     * @return Nearest Spawner object or null
     */
    private Spawner findNearestSpawnerObject(Location location, double maxDistance) {
        Location nearest = findNearestSpawner(location, maxDistance);
        if (nearest == null) return null;

        String spawnerId = spawnerLocations.get(nearest);
        return spawnerId != null ? spawners.get(spawnerId) : null;
    }

    /**
     * Get spawner properties for a location
     *
     * @param location The spawner location
     * @return SpawnerProperties or null if not found
     */
    public SpawnerProperties getSpawnerProperties(Location location) {
        if (location == null) return null;

        Spawner spawner = getSpawnerAtLocation(location);
        return spawner != null ? spawner.getProperties() : null;
    }

    /**
     * Get spawner metrics for a location
     *
     * @param location The spawner location
     * @return SpawnerMetrics or null if not found
     */
    public SpawnerMetrics getSpawnerMetrics(Location location) {
        if (location == null) return null;

        Spawner spawner = getSpawnerAtLocation(location);
        return spawner != null ? spawner.getMetrics() : null;
    }

    /**
     * Reset a specific spawner's timer and counters
     *
     * @param location The spawner location
     * @return true if reset was successful
     */
    public boolean resetSpawner(Location location) {
        if (location == null) return false;

        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return false;

        spawner.reset();
        return true;
    }

    /**
     * Set a property value for a spawner
     *
     * @param location The spawner location
     * @param property The property name
     * @param value    The property value as a string
     * @return true if set successfully
     */
    public boolean setSpawnerProperty(Location location, String property, String value) {
        if (location == null || property == null) return false;

        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return false;

        SpawnerProperties props = spawner.getProperties();

        try {
            switch (property.toLowerCase()) {
                case "timerestricted":
                    props.setTimeRestricted(Boolean.parseBoolean(value));
                    break;
                case "starthour":
                    props.setStartHour(Integer.parseInt(value));
                    break;
                case "endhour":
                    props.setEndHour(Integer.parseInt(value));
                    break;
                case "weatherrestricted":
                    props.setWeatherRestricted(Boolean.parseBoolean(value));
                    break;
                case "spawninclear":
                    props.setSpawnInClear(Boolean.parseBoolean(value));
                    break;
                case "spawninrain":
                    props.setSpawnInRain(Boolean.parseBoolean(value));
                    break;
                case "spawninthunder":
                    props.setSpawnInThunder(Boolean.parseBoolean(value));
                    break;
                case "spawnergroup":
                    props.setSpawnerGroup(value);
                    rebuildSpawnerGroups();
                    break;
                case "spawnradiusx":
                    props.setSpawnRadiusX(Double.parseDouble(value));
                    break;
                case "spawnradiusy":
                    props.setSpawnRadiusY(Double.parseDouble(value));
                    break;
                case "spawnradiusz":
                    props.setSpawnRadiusZ(Double.parseDouble(value));
                    break;
                case "maxmoboverride":
                    props.setMaxMobOverride(Integer.parseInt(value));
                    break;
                case "playerdetectionrangeoverride":
                    props.setPlayerDetectionRangeOverride(Double.parseDouble(value));
                    break;
                case "displayname":
                    props.setDisplayName(value);
                    // Update hologram if visible
                    if (spawner.isVisible()) {
                        spawner.updateHologram();
                    }
                    break;
                default:
                    return false;
            }

            // Save changes
            saveSpawners();

            return true;
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error setting property " + property + " to " + value + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Get a property value for a spawner
     *
     * @param location The spawner location
     * @param property The property name
     * @return The property value or null if not found
     */
    public Object getSpawnerProperty(Location location, String property) {
        if (location == null || property == null) return null;

        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return null;

        SpawnerProperties props = spawner.getProperties();

        switch (property.toLowerCase()) {
            case "timerestricted":
                return props.isTimeRestricted();
            case "starthour":
                return props.getStartHour();
            case "endhour":
                return props.getEndHour();
            case "weatherrestricted":
                return props.isWeatherRestricted();
            case "spawninclear":
                return props.canSpawnInClear();
            case "spawninrain":
                return props.canSpawnInRain();
            case "spawninthunder":
                return props.canSpawnInThunder();
            case "spawnergroup":
                return props.getSpawnerGroup();
            case "spawnradiusx":
                return props.getSpawnRadiusX();
            case "spawnradiusy":
                return props.getSpawnRadiusY();
            case "spawnradiusz":
                return props.getSpawnRadiusZ();
            case "maxmoboverride":
                return props.getMaxMobOverride();
            case "playerdetectionrangeoverride":
                return props.getPlayerDetectionRangeOverride();
            case "displayname":
                return props.getDisplayName();
            default:
                return null;
        }
    }

    /**
     * Set the display mode for a spawner
     *
     * @param location The spawner location
     * @param mode     The display mode (0=basic, 1=detailed, 2=admin)
     * @return true if set successfully
     */
    public boolean setSpawnerDisplayMode(Location location, int mode) {
        if (location == null || mode < 0 || mode > 2) return false;

        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return false;

        spawner.setDisplayMode(mode);

        // Update hologram if visible
        if (spawner.isVisible()) {
            spawner.updateHologram();
        }

        // Save changes
        saveSpawners();

        return true;
    }

    /**
     * Get the display mode for a spawner
     *
     * @param location The spawner location
     * @return The display mode (0-2) or -1 if not found
     */
    public int getSpawnerDisplayMode(Location location) {
        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return -1;
        return spawner.getDisplayMode();
    }

    /**
     * Send spawner info to a player
     *
     * @param player   The player
     * @param location The spawner location
     */
    public void sendSpawnerInfo(Player player, Location location) {
        if (player == null || location == null) return;

        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) {
            player.sendMessage(ChatColor.RED + "No spawner found at this location.");
            return;
        }

        spawner.sendInfoTo(player);
    }

    /**
     * Print detailed status information about all spawners to console
     */
    public void printSpawnerStatus() {
        logger.info("[MobSpawner] === Spawner System Status ===");
        logger.info("[MobSpawner] Total Spawners: " + spawners.size());
        logger.info("[MobSpawner] Enabled: " + (spawnersEnabled ? "Yes" : "No"));
        logger.info("[MobSpawner] Debug Mode: " + (debug ? "Enabled" : "Disabled"));

        // Count active spawners
        int activeCount = 0;
        int totalActive = 0;
        int totalQueued = 0;

        for (Spawner spawner : spawners.values()) {
            int active = spawner.getTotalActiveMobs();
            int queued = spawner.getQueuedCount();

            totalActive += active;
            totalQueued += queued;

            if (active > 0) {
                activeCount++;
            }
        }

        logger.info("[MobSpawner] Active Spawners: " + activeCount);
        logger.info("[MobSpawner] Total Active Mobs: " + totalActive);
        logger.info("[MobSpawner] Total Queued Respawns: " + totalQueued);

        // Count visible spawners
        int visibleCount = 0;
        for (Spawner spawner : spawners.values()) {
            if (spawner.isVisible()) {
                visibleCount++;
            }
        }
        logger.info("[MobSpawner] Visible Spawners: " + visibleCount);

        // Print group information
        logger.info("[MobSpawner] Spawner Groups: " + spawnerGroups.size());
        for (Map.Entry<String, Set<String>> entry : spawnerGroups.entrySet()) {
            logger.info("  - " + entry.getKey() + ": " + entry.getValue().size() + " spawners");
        }

        // Print template information
        logger.info("[MobSpawner] Templates: " + spawnerTemplates.size());

        // Print configuration
        logger.info("[MobSpawner] Configuration:");
        logger.info("  - Max Mobs Per Spawner: " + maxMobsPerSpawner);
        logger.info("  - Player Detection Range: " + playerDetectionRange);
        logger.info("  - Mob Respawn Distance Check: " + mobRespawnDistanceCheck);
        logger.info("  - Default Visibility: " + defaultVisibility);

        logger.info("[MobSpawner] === End of Status ===");
    }


    /**
     * Validate spawner data format
     *
     * @param data Spawner data string
     * @return true if valid
     */
    public boolean validateSpawnerData(String data) {
        if (data == null || data.isEmpty()) {
            logger.warning("[MobSpawner] Empty data string");
            return false;
        }

        // Debug output
        if (debug) {
            logger.info("[MobSpawner] Validating data: " + data);
        }

        try {
            String[] entries = data.split(",");
            for (String entry : entries) {
                // Trim each entry to remove whitespace issues
                entry = entry.trim();

                // Basic format check
                if (!entry.contains(":") || !entry.contains("@") || !entry.contains("#")) {
                    logger.warning("[MobSpawner] Invalid entry format (missing :, @, or #): " + entry);
                    logger.warning("[MobSpawner] Expected format: mobType:tier@elite#amount");
                    return false;
                }

                String[] parts = entry.split(":");
                if (parts.length != 2) {
                    logger.warning("[MobSpawner] Invalid type section (wrong number of : separators): " + entry);
                    return false;
                }

                // Validate mob type - ensure we're using the real MOB ID not display name
                String mobType = parts[0].toLowerCase().trim();

                // Special handling for witherskeleton which might be entered with underscore
                if (mobType.equals("wither_skeleton")) {
                    mobType = "witherskeleton";
                }

                if (!MobType.isValidType(mobType)) {
                    logger.warning("[MobSpawner] Invalid mob type: " + mobType);
                    // List all valid types for debugging
                    if (debug) {
                        List<String> validTypes = new ArrayList<>();
                        for (MobType type : MobType.values()) {
                            validTypes.add(type.getId());
                        }
                        logger.info("[MobSpawner] Valid mob types: " + String.join(", ", validTypes));
                    }
                    return false;
                }

                // Get the actual mob type
                MobType type = MobType.getById(mobType);
                if (type == null) {
                    logger.warning("[MobSpawner] Failed to get MobType for id: " + mobType);
                    return false;
                }

                // Validate tier section format
                String tierSection = parts[1].trim();
                if (!tierSection.contains("@")) {
                    logger.warning("[MobSpawner] Missing @ in tier section: " + tierSection);
                    return false;
                }

                String[] tierParts = tierSection.split("@");
                if (tierParts.length != 2) {
                    logger.warning("[MobSpawner] Wrong number of @ in tier section: " + tierSection);
                    return false;
                }

                // Validate tier
                try {
                    int tier = Integer.parseInt(tierParts[0].trim());

                    // Check general tier range
                    if (tier < 1 || tier > 6) {
                        logger.warning("[MobSpawner] Tier out of valid range (1-6): " + tier);
                        return false;
                    }

                    // DISABLE tier range check for specific mob types as this causes issues
                    // This validation will be handled by the MobType system when spawning

                } catch (NumberFormatException e) {
                    logger.warning("[MobSpawner] Invalid tier (not a number): " + tierParts[0]);
                    return false;
                }

                // Validate elite/amount section format
                String eliteAmountSection = tierParts[1].trim();
                if (!eliteAmountSection.contains("#")) {
                    logger.warning("[MobSpawner] Missing # in elite/amount section: " + eliteAmountSection);
                    return false;
                }

                String[] eliteParts = eliteAmountSection.split("#");
                if (eliteParts.length != 2) {
                    logger.warning("[MobSpawner] Wrong number of # in elite/amount section: " + eliteAmountSection);
                    return false;
                }

                // Validate elite boolean
                String eliteStr = eliteParts[0].toLowerCase().trim();
                if (!eliteStr.equals("true") && !eliteStr.equals("false")) {
                    logger.warning("[MobSpawner] Invalid elite value (must be true/false): " + eliteParts[0]);
                    return false;
                }

                // Validate amount
                try {
                    int amount = Integer.parseInt(eliteParts[1].trim());
                    if (amount < 1 || amount > maxMobsPerSpawner) {
                        logger.warning("[MobSpawner] Invalid amount (must be 1-" + maxMobsPerSpawner + "): " + amount);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    logger.warning("[MobSpawner] Invalid amount (not a number): " + eliteParts[1]);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.severe("[MobSpawner] Unexpected error validating spawner data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Format a location to a readable string
     *
     * @param location The location to format
     * @return Formatted location string
     */
    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Unknown";
        }

        return String.format("%s [%d, %d, %d]", location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Get the mob respawn distance check value
     *
     * @return Distance value
     */
    public double getMobRespawnDistanceCheck() {
        return mobRespawnDistanceCheck;
    }

    /**
     * Get default visibility setting for new spawners
     *
     * @return true if spawners should be visible by default
     */
    public boolean getDefaultVisibility() {
        return defaultVisibility;
    }

    /**
     * Get available spawner templates
     *
     * @return Map of template names to data strings
     */
    public Map<String, String> getAllTemplates() {
        return new HashMap<>(spawnerTemplates);
    }

    /**
     * Get all spawner group names
     *
     * @return Set of group names
     */
    public Set<String> getAllSpawnerGroups() {
        return new HashSet<>(spawnerGroups.keySet());
    }

    /**
     * Toggle enabled/disabled state for a spawner group
     *
     * @param groupName The group name
     * @param enabled   Whether to enable or disable
     * @return Number of spawners affected
     */
    public int toggleSpawnerGroupEnabled(String groupName, boolean enabled) {
        if (groupName == null || groupName.isEmpty()) return 0;

        Set<String> groupIds = spawnerGroups.get(groupName);
        if (groupIds == null || groupIds.isEmpty()) return 0;

        int count = 0;
        for (String spawnerId : groupIds) {
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                // Update visibility to enable/disable
                boolean wasVisible = spawner.isVisible();
                if (enabled != wasVisible) {
                    spawner.setVisible(enabled);

                    // Update block
                    Block block = spawner.getLocation().getBlock();
                    Material blockType = enabled ? Material.SPAWNER : Material.AIR;

                    if (block.getType() == Material.AIR || block.getType() == Material.SPAWNER) {
                        block.setType(blockType);
                    }

                    // Keep metadata
                    block.setMetadata("isSpawner", new FixedMetadataValue(plugin, true));
                    block.setMetadata("spawnerId", new FixedMetadataValue(plugin, spawnerId));

                    count++;
                }
            }
        }

        // Save changes
        if (count > 0) {
            saveSpawners();

            if (debug) {
                logger.info("[MobSpawner] " + (enabled ? "Enabled" : "Disabled") + " " + count + " spawners in group '" + groupName + "'");
            }
        }

        return count;
    }

    /**
     * Find all spawners that belong to a specific group
     *
     * @param groupName The group name to search for
     * @return List of spawner locations in that group
     */
    public List<Location> findSpawnersInGroup(String groupName) {
        if (groupName == null || groupName.isEmpty()) return new ArrayList<>();

        // Get spawner IDs in this group
        Set<String> groupIds = spawnerGroups.get(groupName);
        if (groupIds == null || groupIds.isEmpty()) return new ArrayList<>();

        // Convert to locations
        List<Location> result = new ArrayList<>();
        for (String spawnerId : groupIds) {
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                result.add(spawner.getLocation());
            }
        }

        return result;
    }

    /**
     * Begin a guided spawner creation process for a player
     *
     * @param player   The player creating the spawner
     * @param location The location for the new spawner
     */
    public void beginGuidedSpawnerCreation(Player player, Location location) {
        if (player == null || location == null) return;

        // Create a new spawner creation session
        String playerName = player.getName();

        // Check if there's already an existing session
        if (spawnerCreationSessions.containsKey(playerName)) {
            player.sendMessage(ChatColor.RED + "You already have an active spawner creation session.");
            player.sendMessage(ChatColor.GRAY + "Type 'cancel' to cancel the current session.");
            return;
        }

        // Create new session
        SpawnerCreationSession session = new SpawnerCreationSession(location, playerName);
        spawnerCreationSessions.put(playerName, session);
        creatingSpawner.put(playerName, location);

        // Start the guided process
        player.sendMessage(ChatColor.GREEN + "=== Spawner Creation Wizard ===");
        player.sendMessage(ChatColor.GRAY + "Creating a spawner at: " + location.getWorld().getName() + " [" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "]");

        player.sendMessage(ChatColor.YELLOW + "Step 1: Enter a mob type (e.g., skeleton, zombie)");
        player.sendMessage(ChatColor.GRAY + "You can also use a template by typing 'template:name'.");
        player.sendMessage(ChatColor.GRAY + "Available templates: " + String.join(", ", spawnerTemplates.keySet()));
        player.sendMessage(ChatColor.GRAY + "Type 'cancel' at any time to cancel creation.");
    }

    /**
     * Check if a player has an active spawner creation session
     *
     * @param playerName The player name
     * @return true if they have an active session
     */
    public boolean hasActiveCreationSession(String playerName) {
        return spawnerCreationSessions.containsKey(playerName);
    }

    /**
     * Get a spawner by its unique ID
     *
     * @param spawnerId The spawner ID
     * @return The spawner or null if not found
     */
    public Spawner getSpawnerById(String spawnerId) {
        if (spawnerId == null || spawnerId.isEmpty()) return null;
        return spawners.get(spawnerId);
    }

    /**
     * Create an in-game control panel for a spawner
     * Uses a hologram to display interactive information
     *
     * @param player   The player viewing the panel
     * @param location The spawner location
     */
    public void createSpawnerControlPanel(Player player, Location location) {
        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) {
            player.sendMessage(ChatColor.RED + "No spawner found at this location.");
            return;
        }

        // Get unique ID for this control panel
        String playerId = player.getUniqueId().toString();
        String panelId = "control_panel_" + playerId;

        // Create lines for the control panel
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Spawner Control Panel");
        lines.add(ChatColor.YELLOW + "Location: " + ChatColor.WHITE + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());

        // Get properties and metrics
        SpawnerProperties props = spawner.getProperties();
        SpawnerMetrics metrics = spawner.getMetrics();

        // Add group info if present
        if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
            lines.add(ChatColor.YELLOW + "Group: " + ChatColor.WHITE + props.getSpawnerGroup());
        }

        // Add visibility status
        lines.add(ChatColor.YELLOW + "Visibility: " + (spawner.isVisible() ? ChatColor.GREEN + "Visible" : ChatColor.RED + "Hidden"));

        // Add mob info
        lines.add(ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE + spawner.getTotalActiveMobs() + "/" + spawner.getTotalDesiredMobs());

        // Add performance metrics if available
        if (metrics.getTotalSpawned() > 0) {
            lines.add(ChatColor.YELLOW + "Spawned: " + ChatColor.WHITE + metrics.getTotalSpawned() + " | Killed: " + metrics.getTotalKilled());
        }

        // Add commands info
        lines.add(ChatColor.GREEN + "=== Available Commands ===");
        lines.add(ChatColor.GRAY + "/spawner toggle show/hide");
        lines.add(ChatColor.GRAY + "/spawner reset");
        lines.add(ChatColor.GRAY + "/spawner edit");
        lines.add(ChatColor.GRAY + "/spawner visualize");

        // Create hologram at player's location, offset slightly
        Location panelLoc = player.getLocation().clone().add(0, 2, 0);

        // Create the hologram with a 15-second duration
        HologramManager.createOrUpdateHologram(panelId, panelLoc, lines, 0.25);

        // Schedule removal after 15 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HologramManager.removeHologram(panelId);
        }, 300L); // 15 seconds (20 ticks per second)

        // Message the player
        player.sendMessage(ChatColor.GREEN + "Control panel created. Look up!");
        player.sendMessage(ChatColor.GRAY + "Panel will disappear after 15 seconds.");
    }

    /**
     * Visualize spawner range and properties for a player
     *
     * @param player   The player
     * @param location The spawner location
     * @return true if visualization was created
     */
    public boolean visualizeSpawnerForPlayer(Player player, Location location) {
        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return false;

        String playerUUID = player.getUniqueId().toString();
        String vizKey = playerUUID + "_" + System.currentTimeMillis();

        // Clean up any existing visualizations for this player
        cleanupPlayerVisualizations(playerUUID);

        // Get spawner properties
        SpawnerProperties props = spawner.getProperties();
        double radiusX = props.getSpawnRadiusX();
        double radiusY = props.getSpawnRadiusY();
        double radiusZ = props.getSpawnRadiusZ();

        // Create visualization entities
        List<ArmorStand> markers = new ArrayList<>();

        // Create corner markers for spawn area
        Location origin = location.clone();

        // Create markers to show the spawn radius
        for (double x = -radiusX; x <= radiusX; x += radiusX) {
            for (double y = -radiusY; y <= radiusY; y += radiusY) {
                for (double z = -radiusZ; z <= radiusZ; z += radiusZ) {
                    Location markerLoc = origin.clone().add(x, y, z);

                    ArmorStand marker = (ArmorStand) origin.getWorld().spawnEntity(markerLoc, EntityType.ARMOR_STAND);

                    // Configure marker
                    marker.setVisible(false);
                    marker.setGravity(false);
                    marker.setMarker(true);
                    marker.setSmall(true);
                    marker.setCustomNameVisible(true);
                    marker.setCustomName(ChatColor.YELLOW + "●");

                    markers.add(marker);
                }
            }
        }

        // Create detection range indicator
        double detectionRange = props.getPlayerDetectionRangeOverride() > 0 ? props.getPlayerDetectionRangeOverride() : playerDetectionRange;

        Location detectionLoc = origin.clone().add(0, 1, 0);
        ArmorStand rangeMarker = (ArmorStand) origin.getWorld().spawnEntity(detectionLoc, EntityType.ARMOR_STAND);

        // Configure detection range marker
        rangeMarker.setVisible(false);
        rangeMarker.setGravity(false);
        rangeMarker.setMarker(true);
        rangeMarker.setCustomNameVisible(true);
        rangeMarker.setCustomName(ChatColor.GREEN + "Detection Range: " + ChatColor.WHITE + String.format("%.1f", detectionRange) + " blocks");

        markers.add(rangeMarker);

        // Create label for spawn boundaries
        Location labelLoc = origin.clone().add(0, 2, 0);
        ArmorStand labelMarker = (ArmorStand) origin.getWorld().spawnEntity(labelLoc, EntityType.ARMOR_STAND);

        // Configure label marker
        labelMarker.setVisible(false);
        labelMarker.setGravity(false);
        labelMarker.setMarker(true);
        labelMarker.setCustomNameVisible(true);
        labelMarker.setCustomName(ChatColor.AQUA + "Spawn Radius: " + ChatColor.WHITE + String.format("%.1f", radiusX) + "x" + String.format("%.1f", radiusY) + "x" + String.format("%.1f", radiusZ));

        markers.add(labelMarker);

        // Add to visualizations map
        visualizations.put(vizKey, markers);

        player.sendMessage(ChatColor.GREEN + "Spawner visualization created.");
        player.sendMessage(ChatColor.GRAY + "Visualization will disappear in 10 seconds.");

        // Schedule removal after 10 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<ArmorStand> stands = visualizations.remove(vizKey);
            if (stands != null) {
                for (ArmorStand stand : stands) {
                    if (stand != null && !stand.isDead()) {
                        stand.remove();
                    }
                }
            }
        }, 200L); // 10 seconds

        return true;
    }

    /**
     * Clean up visualizations for a specific player
     *
     * @param playerUUID Player UUID as string
     */
    private void cleanupPlayerVisualizations(String playerUUID) {
        List<String> toRemove = new ArrayList<>();

        // Find and remove all visualizations for this player
        for (Map.Entry<String, List<ArmorStand>> entry : visualizations.entrySet()) {
            if (entry.getKey().startsWith(playerUUID + "_")) {
                // Remove all entities
                for (ArmorStand stand : entry.getValue()) {
                    if (stand != null && !stand.isDead()) {
                        stand.remove();
                    }
                }
                toRemove.add(entry.getKey());
            }
        }

        // Remove keys
        for (String key : toRemove) {
            visualizations.remove(key);
        }
    }

    /**
     * Process chat message during spawner creation
     *
     * @param player  The player
     * @param message The chat message
     */
    public void processCreationChat(Player player, String message) {
        String playerName = player.getName();
        SpawnerCreationSession session = spawnerCreationSessions.get(playerName);

        // Check for cancellation
        if (message.equalsIgnoreCase("cancel")) {
            spawnerCreationSessions.remove(playerName);
            creatingSpawner.remove(playerName);
            player.sendMessage(ChatColor.RED + "Spawner creation cancelled.");
            return;
        }

        // Process message based on current step
        int currentStep = session.getCurrentStep();

        // Handle help request
        if (message.equalsIgnoreCase("help")) {
            player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
            return;
        }

        try {
            switch (currentStep) {
                case 0: // Mob type
                    processMobTypeStep(player, message, session);
                    break;

                case 1: // Tier
                    processTierStep(player, message, session);
                    break;

                case 2: // Elite
                    processEliteStep(player, message, session);
                    break;

                case 3: // Amount
                    processAmountStep(player, message, session);
                    break;

                case 4: // Confirm/Next
                    processConfirmStep(player, message, session);
                    break;

                case 5: // Template confirmation
                    processTemplateConfirmStep(player, message, session);
                    break;

                case 6: // Advanced properties
                    processAdvancedPropertiesStep(player, message, session);
                    break;

                case 7: // Final confirmation
                    processFinalConfirmStep(player, message, session);
                    break;

                default:
                    player.sendMessage(ChatColor.RED + "Invalid step. Type 'cancel' to abort.");
                    break;
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error processing input: " + e.getMessage());
            logger.warning("[MobSpawner] Error in creation chat: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Process the mob type step in spawner creation
     *
     * @param player  The player
     * @param message The chat message
     * @param session The creation session
     */
    private void processMobTypeStep(Player player, String message, SpawnerCreationSession session) {
        // Check for template
        if (message.toLowerCase().startsWith("template:")) {
            String templateName = message.substring(9).toLowerCase();
            if (spawnerTemplates.containsKey(templateName)) {
                session.setTemplateName(templateName);
                session.setCurrentStep(5); // Move to template confirmation
                player.sendMessage(ChatColor.GREEN + "Using template: " + templateName);
                player.sendMessage(ChatColor.YELLOW + "Type 'confirm' to create spawner or 'cancel' to abort.");
            } else {
                player.sendMessage(ChatColor.RED + "Template not found: " + templateName);
                player.sendMessage(ChatColor.GRAY + "Available templates: " + String.join(", ", spawnerTemplates.keySet()));
            }
            return;
        }

        // Check if valid mob type
        if (session.validateMobType(message)) {
            session.setCurrentMobType(message.toLowerCase());
            session.setCurrentStep(1);
            player.sendMessage(ChatColor.GREEN + "Mob type set to: " + message.toLowerCase());
            player.sendMessage(ChatColor.YELLOW + "Step 2: Enter tier (1-6)");
        } else {
            player.sendMessage(ChatColor.RED + "Invalid mob type: " + message);
            player.sendMessage(ChatColor.GRAY + "Examples: skeleton, zombie, spider, etc.");
        }
    }

    /**
     * Process the tier step in spawner creation
     *
     * @param player  The player
     * @param message The chat message
     * @param session The creation session
     */
    private void processTierStep(Player player, String message, SpawnerCreationSession session) {
        try {
            int tier = Integer.parseInt(message);
            if (session.validateTier(tier)) {
                session.setCurrentTier(tier);
                session.setCurrentStep(2);
                player.sendMessage(ChatColor.GREEN + "Tier set to: " + tier);
                player.sendMessage(ChatColor.YELLOW + "Step 3: Is this an elite mob? (true/false)");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid tier. Must be between 1 and 6.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid tier. Please enter a number between 1 and 6.");
        }
    }

    /**
     * Process the elite step in spawner creation
     *
     * @param player  The player
     * @param message The chat message
     * @param session The creation session
     */
    private void processEliteStep(Player player, String message, SpawnerCreationSession session) {
        if (message.equalsIgnoreCase("true") || message.equalsIgnoreCase("false")) {
            boolean elite = Boolean.parseBoolean(message);
            session.setCurrentElite(elite);
            session.setCurrentStep(3);
            player.sendMessage(ChatColor.GREEN + "Elite set to: " + elite);
            player.sendMessage(ChatColor.YELLOW + "Step 4: How many of this mob? (1-10)");
        } else {
            player.sendMessage(ChatColor.RED + "Please enter 'true' or 'false'.");
        }
    }

    /**
     * Process the amount step in spawner creation
     *
     * @param player  The player
     * @param message The chat message
     * @param session The creation session
     */
    private void processAmountStep(Player player, String message, SpawnerCreationSession session) {
        try {
            int amount = Integer.parseInt(message);
            if (amount >= 1 && amount <= 10) {
                session.setCurrentAmount(amount);
                session.setCurrentStep(4);
                player.sendMessage(ChatColor.GREEN + "Amount set to: " + amount);
                player.sendMessage(ChatColor.YELLOW + "Mob entry complete. Type:");
                player.sendMessage(ChatColor.YELLOW + "- 'add' to add another mob type");
                player.sendMessage(ChatColor.YELLOW + "- 'done' to finish the spawner");
                player.sendMessage(ChatColor.YELLOW + "- 'advanced' to configure advanced properties");
            } else {
                player.sendMessage(ChatColor.RED + "Amount must be between 1 and 10.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount. Please enter a number between 1 and 10.");
        }
    }

    /**
     * Process the confirm step in spawner creation
     *
     * @param player  The player
     * @param message The chat message
     * @param session The creation session
     */
    private void processConfirmStep(Player player, String message, SpawnerCreationSession session) {
        if (message.equalsIgnoreCase("add")) {
            // Add current mob entry and prepare for another
            session.addCurrentMobEntry();
            player.sendMessage(ChatColor.GREEN + "Added mob entry. Let's add another one.");
            player.sendMessage(ChatColor.YELLOW + "Step 1: Enter a mob type (e.g., skeleton, zombie)");
            session.setCurrentStep(0);
        } else if (message.equalsIgnoreCase("done")) {
            // Add final entry and finish
            session.addCurrentMobEntry();
            finishSpawnerCreation(player, session);
        } else if (message.equalsIgnoreCase("advanced")) {
            // Enter advanced configuration mode
            session.setConfigureAdvanced(true);
            session.setCurrentStep(6);
            player.sendMessage(ChatColor.GREEN + "Entering advanced configuration mode.");
            player.sendMessage(ChatColor.YELLOW + "Available commands:");
            player.sendMessage(ChatColor.GRAY + "- name:<display name> - Set a custom display name");
            player.sendMessage(ChatColor.GRAY + "- group:<group name> - Add to a spawner group");
            player.sendMessage(ChatColor.GRAY + "- time:<start>-<end> - Set time restriction (0-24)");
            player.sendMessage(ChatColor.GRAY + "- weather:<clear,rain,thunder> - Set weather restrictions");
            player.sendMessage(ChatColor.GRAY + "- radius:<x>,<y>,<z> - Set spawn radius");
            player.sendMessage(ChatColor.GRAY + "- display:<0-2> - Set display mode");
            player.sendMessage(ChatColor.GRAY + "Type 'done' when finished with advanced settings.");
        } else {
            player.sendMessage(ChatColor.RED + "Please type 'add', 'done', or 'advanced'.");
        }
    }

    /**
     * Process the template confirmation step in spawner creation
     *
     * @param player  The player
     * @param message The chat message
     * @param session The creation session
     */
    private void processTemplateConfirmStep(Player player, String message, SpawnerCreationSession session) {
        if (message.equalsIgnoreCase("confirm")) {
            // Create spawner from template
            String templateName = session.getTemplateName();
            Location loc = session.getLocation();

            if (addSpawnerFromTemplate(loc, templateName)) {
                player.sendMessage(ChatColor.GREEN + "Spawner created successfully from template: " + ChatColor.YELLOW + templateName);
                player.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.YELLOW + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

                // Clean up session
                spawnerCreationSessions.remove(player.getName());
                creatingSpawner.remove(player.getName());
            } else {
                player.sendMessage(ChatColor.RED + "Failed to create spawner from template.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Please type 'confirm' to create the spawner or 'cancel' to abort.");
        }
    }

    /**
     * Process the advanced properties step in spawner creation
     *
     * @param player  The player
     * @param message The chat message
     * @param session The creation session
     */
    private void processAdvancedPropertiesStep(Player player, String message, SpawnerCreationSession session) {
        if (message.equalsIgnoreCase("done")) {
            // Finish with advanced properties, show the summary and ask for final confirmation
            player.sendMessage(ChatColor.GREEN + "Advanced configuration complete.");
            player.sendMessage(ChatColor.YELLOW + "Spawner Summary:");
            player.sendMessage(ChatColor.GRAY + session.getSummary());
            player.sendMessage(ChatColor.YELLOW + "Type 'confirm' to create this spawner or 'cancel' to abort.");
            session.setCurrentStep(7);
        } else {
            // Process advanced property command
            String result = session.processAdvancedCommand(message);
            player.sendMessage(ChatColor.GRAY + result);
        }
    }

    /**
     * Process the final confirmation step in spawner creation
     *
     * @param player  The player
     * @param message The chat message
     * @param session The creation session
     */
    private void processFinalConfirmStep(Player player, String message, SpawnerCreationSession session) {
        if (message.equalsIgnoreCase("confirm")) {
            // Add final entry if needed and finish
            if (!session.getCurrentMobType().isEmpty()) {
                session.addCurrentMobEntry();
            }
            finishSpawnerCreation(player, session);
        } else {
            player.sendMessage(ChatColor.RED + "Please type 'confirm' to create the spawner or 'cancel' to abort.");
        }
    }

    /**
     * Finish spawner creation
     *
     * @param player  The player
     * @param session The creation session
     */
    private void finishSpawnerCreation(Player player, SpawnerCreationSession session) {
        // Check if session has mob entries
        if (!session.hasMobEntries()) {
            player.sendMessage(ChatColor.RED + "Cannot create spawner with no mob entries.");
            return;
        }

        // Build the spawner data
        String data = session.buildSpawnerData();
        Location loc = session.getLocation();

        // Create the spawner
        if (addSpawner(loc, data)) {
            player.sendMessage(ChatColor.GREEN + "Spawner created successfully!");
            player.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.YELLOW + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            player.sendMessage(ChatColor.GRAY + "Data: " + ChatColor.YELLOW + data);

            // Apply advanced properties if configured
            if (session.isConfigureAdvanced()) {
                String spawnerId = getSpawnerIdFromLocation(loc);
                if (spawnerId != null) {
                    Spawner spawner = spawners.get(spawnerId);
                    if (spawner != null) {
                        // Set display mode
                        spawner.setDisplayMode(session.getDisplayMode());

                        // Apply advanced properties
                        session.applyToProperties(spawner.getProperties());

                        // Update visibility based on default
                        spawner.setVisible(defaultVisibility);
                        setSpawnerBlock(loc.getBlock(), spawnerId, defaultVisibility);

                        // Update hologram if visible
                        if (spawner.isVisible()) {
                            spawner.updateHologram();
                        }

                        // Save changes
                        saveSpawners();

                        player.sendMessage(ChatColor.GREEN + "Advanced properties applied.");
                    }
                }
            }

            // Clean up session
            spawnerCreationSessions.remove(player.getName());
            creatingSpawner.remove(player.getName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create spawner. Invalid data format.");
        }
    }

    /**
     * Shutdown the spawner system
     */
    public void shutdown() {
        try {
            // Save all data
            saveSpawners();

            // Cancel tasks
            if (spawnerTask != null) spawnerTask.cancel();
            if (respawnTask != null) respawnTask.cancel();
            if (cleanupTask != null) cleanupTask.cancel();
            if (hologramTask != null) hologramTask.cancel();
            if (autoSaveTask != null) autoSaveTask.cancel();
            if (sessionCleanupTask != null) sessionCleanupTask.cancel();
            if (visualizationCleanupTask != null) visualizationCleanupTask.cancel();

            // Clean up all visualizations
            for (List<ArmorStand> stands : visualizations.values()) {
                for (ArmorStand stand : stands) {
                    if (stand != null && !stand.isDead()) {
                        stand.remove();
                    }
                }
            }
            visualizations.clear();

// Clear all holograms
            for (Spawner spawner : spawners.values()) {
                if (spawner.isVisible()) {
                    spawner.removeHologram();
                }
            }

            // Clear all maps
            spawners.clear();
            spawnerLocations.clear();
            entityToSpawner.clear();
            creatingSpawner.clear();
            spawnerCreationSessions.clear();
            spawnerGroups.clear();

            logger.info("[MobSpawner] Shutdown complete");
        } catch (Exception e) {
            logger.severe("[MobSpawner] Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}