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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * FIXED: Synchronous MobSpawner with reliable coordination
 * No async operations, deterministic processing
 */
public class MobSpawner implements Listener {
    private static MobSpawner instance;

    // Core spawner storage
    private final Map<String, Spawner> spawners = new ConcurrentHashMap<>();
    private final Map<Location, String> spawnerLocations = new ConcurrentHashMap<>();

    // Player creation sessions
    private final Map<String, Location> creatingSpawner = new HashMap<>();
    private final Map<String, SpawnerCreationSession> spawnerCreationSessions = new HashMap<>();

    // Spawner groups
    private final Map<String, Set<String>> spawnerGroups = new HashMap<>();

    // Entity tracking
    private final Map<UUID, String> entityToSpawner = new ConcurrentHashMap<>();

    // Visualization entities
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

    // FIXED: Single synchronous task instead of multiple async tasks
    private BukkitTask mainProcessingTask;
    private BukkitTask autoSaveTask;
    private BukkitTask sessionCleanupTask;
    private BukkitTask visualizationCleanupTask;

    /**
     * Constructor
     */
    private MobSpawner() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.debug = plugin.isDebugMode();
        this.defaultVisibility = plugin.getConfig().getBoolean("mechanics.mobs.spawner-default-visibility", false);

        initializeTemplates();
    }

    /**
     * Get the singleton instance
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
            Bukkit.getPluginManager().registerEvents(this, plugin);
            loadConfig();
            loadSpawners();
            startTasks();

            logger.info("[MobSpawner] Initialized with " + spawners.size() + " spawners");
        } catch (Exception e) {
            logger.severe("[MobSpawner] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initialize spawner templates
     */
    private void initializeTemplates() {
        spawnerTemplates.put("basic_t1", "skeleton:1@false#2,zombie:1@false#2");
        spawnerTemplates.put("basic_t2", "skeleton:2@false#2,zombie:2@false#2");
        spawnerTemplates.put("standard_t3", "skeleton:3@false#3,zombie:3@false#2,spider:3@false#1");
        spawnerTemplates.put("standard_t4", "skeleton:4@false#3,zombie:4@false#2,spider:4@false#1");
        spawnerTemplates.put("advanced_t5", "skeleton:5@false#2,witherskeleton:5@false#2,zombie:5@false#1");
        spawnerTemplates.put("elite_t3", "skeleton:3@true#1,zombie:3@true#1");
        spawnerTemplates.put("elite_t4", "skeleton:4@true#1,witherskeleton:4@true#1");
        spawnerTemplates.put("elite_t5", "witherskeleton:5@true#1");
        spawnerTemplates.put("mixed_t3", "skeleton:3@false#2,zombie:3@false#2,skeleton:3@true#1");
        spawnerTemplates.put("mixed_t4", "skeleton:4@false#2,witherskeleton:4@false#2,witherskeleton:4@true#1");
        spawnerTemplates.put("magma_t4", "magmacube:4@false#4,magmacube:4@true#1");
        spawnerTemplates.put("spider_t4", "spider:4@false#3,cavespider:4@false#2");
        spawnerTemplates.put("imp_t3", "imp:3@false#4");
        spawnerTemplates.put("boss_t5", "skeleton:5@true#1");
        spawnerTemplates.put("frozen_t6", "skeleton:6@false#2,witherskeleton:6@false#1");
    }

    /**
     * Load configuration values
     */
    private void loadConfig() {
        try {
            plugin.saveDefaultConfig();

            this.debug = plugin.getConfig().getBoolean("debug", false);
            this.maxMobsPerSpawner = plugin.getConfig().getInt("mechanics.mobs.max-mobs-per-spawner", 10);
            this.playerDetectionRange = plugin.getConfig().getDouble("mechanics.mobs.player-detection-range", 40.0);
            this.mobRespawnDistanceCheck = plugin.getConfig().getDouble("mechanics.mobs.mob-respawn-distance-check", 25.0);
            this.defaultVisibility = plugin.getConfig().getBoolean("mechanics.mobs.spawner-default-visibility", false);

            // Validate config values
            maxMobsPerSpawner = Math.max(1, Math.min(50, maxMobsPerSpawner));
            playerDetectionRange = Math.max(10.0, Math.min(100.0, playerDetectionRange));
            mobRespawnDistanceCheck = Math.max(5.0, Math.min(50.0, mobRespawnDistanceCheck));

            if (debug) {
                logger.info("[MobSpawner] Configuration loaded: MaxMobs=" + maxMobsPerSpawner +
                        ", DetectionRange=" + playerDetectionRange + ", RespawnCheck=" + mobRespawnDistanceCheck);
            }
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error loading config, using defaults: " + e.getMessage());
        }
    }

    /**
     * FIXED: Start synchronous tasks only
     */
    private void startTasks() {
        logger.info("[MobSpawner] Starting synchronous tasks...");

        // FIXED: Single main processing task that handles everything synchronously
        mainProcessingTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                processAllSpawners();
            }
        }.runTaskTimer(plugin, 20L, 1L); // Run every tick for immediate responsiveness

        // Auto-save task (less frequent)
        autoSaveTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (debug) {
                    logger.info("[MobSpawner] Performing auto-save...");
                }
                saveSpawners();
            }
        }.runTaskTimer(plugin, 1200L, 6000L); // Every 5 minutes

        // Session cleanup
        sessionCleanupTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every 1 minute

        // Visualization cleanup
        visualizationCleanupTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                cleanupVisualizations();
            }
        }.runTaskTimer(plugin, 200L, 200L); // Every 10 seconds

        logger.info("[MobSpawner] All tasks started successfully");
    }

    /**
     * FIXED: Single synchronous method that processes all spawners
     */
    private void processAllSpawners() {
        if (!spawnersEnabled) return;

        try {
            // Process each spawner synchronously
            for (Spawner spawner : spawners.values()) {
                spawner.processTick();
            }

            //I'm sorry for this.
            if(ThreadLocalRandom.current().nextInt(1, 8) < 3) cleanupOrphanedEntries();
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error processing spawners: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Clean up expired spawner creation sessions
     */
    private void cleanupExpiredSessions() {
        List<String> expiredSessions = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, SpawnerCreationSession> entry : spawnerCreationSessions.entrySet()) {
            if (currentTime - entry.getValue().getStartTime() > 300000) { // 5 minutes
                expiredSessions.add(entry.getKey());
            }
        }

        for (String playerName : expiredSessions) {
            spawnerCreationSessions.remove(playerName);
            creatingSpawner.remove(playerName);

            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Your spawner creation session has expired.");
            }
        }

        if (!expiredSessions.isEmpty() && debug) {
            logger.info("[MobSpawner] Cleaned up " + expiredSessions.size() + " expired creation sessions");
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
            String[] parts = key.split("_");
            if (parts.length == 2) {
                try {
                    long timestamp = Long.parseLong(parts[1]);
                    if (currentTime - timestamp > 30000) { // 30 seconds
                        for (ArmorStand stand : entry.getValue()) {
                            if (stand != null && !stand.isDead()) {
                                stand.remove();
                            }
                        }
                        toRemove.add(key);
                    }
                } catch (NumberFormatException ignored) {
                    // Skip malformed keys
                }
            }
        }

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
            Set<UUID> invalidEntities = new HashSet<>();
            for (UUID entityId : entityToSpawner.keySet()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    invalidEntities.add(entityId);
                }
            }

            for (UUID entityId : invalidEntities) {
                entityToSpawner.remove(entityId);
            }

            // Clean up location mappings
            Set<Location> invalidLocations = new HashSet<>();
            for (Map.Entry<Location, String> entry : spawnerLocations.entrySet()) {
                if (!spawners.containsKey(entry.getValue())) {
                    invalidLocations.add(entry.getKey());
                }
            }

            for (Location loc : invalidLocations) {
                spawnerLocations.remove(loc);
            }

            // Rebuild spawner groups
            rebuildSpawnerGroups();

        } catch (Exception e) {
            logger.warning("[MobSpawner] Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Load spawners from configuration
     */
    public void loadSpawners() {
        try {
            spawners.clear();
            spawnerLocations.clear();
            entityToSpawner.clear();

            File file = new File(plugin.getDataFolder(), "spawners.yml");
            if (!file.exists()) {
                logger.warning("[MobSpawner] No spawners.yml file found");
                return;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            int count = 0;
            int failed = 0;

            for (String key : config.getKeys(false)) {
                try {
                    if (!key.contains(",")) continue;

                    String data = getSpawnerDataFromConfig(config, key);
                    if (data == null || data.isEmpty()) {
                        failed++;
                        continue;
                    }

                    Location loc = parseLocationFromKey(key);
                    if (loc == null) {
                        logger.warning("[MobSpawner] Invalid location format: " + key);
                        failed++;
                        continue;
                    }

                    if (!validateSpawnerData(data)) {
                        logger.warning("[MobSpawner] Invalid spawner data: " + data);
                        failed++;
                        continue;
                    }

                    boolean visible = config.getBoolean(key + ".visible", defaultVisibility);
                    int displayMode = config.getInt(key + ".displayMode", 0);

                    Spawner spawner = new Spawner(loc, data, visible);
                    spawner.setDisplayMode(displayMode);

                    String spawnerId = generateSpawnerId(loc);

                    if (config.isConfigurationSection(key + ".properties")) {
                        ConfigurationSection propsSection = config.getConfigurationSection(key + ".properties");
                        SpawnerProperties props = SpawnerProperties.loadFromConfig(propsSection);
                        spawner.setProperties(props);

                        if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
                            spawnerGroups.computeIfAbsent(props.getSpawnerGroup(), k -> new HashSet<>()).add(spawnerId);
                        }
                    }

                    try {
                        setSpawnerBlock(loc.getBlock(), spawnerId, visible);
                    } catch (Exception e) {
                        logger.warning("[MobSpawner] Error setting block state: " + e.getMessage());
                    }

                    spawners.put(spawnerId, spawner);
                    spawnerLocations.put(loc, spawnerId);
                    count++;

                    if (debug) {
                        logger.info("[MobSpawner] Loaded spawner: " + key + " = " + data);
                    }
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Error loading spawner " + key + ": " + e.getMessage());
                    failed++;
                }
            }

            logger.info("[MobSpawner] Loaded " + count + " spawners" +
                    (failed > 0 ? ", " + failed + " failed" : ""));
        } catch (Exception e) {
            logger.severe("[MobSpawner] Critical error loading spawners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get spawner data from config, handling both old and new formats
     */
    private String getSpawnerDataFromConfig(YamlConfiguration config, String key) {
        if (config.contains(key + ".data")) {
            return config.getString(key + ".data");
        } else if (!config.isConfigurationSection(key)) {
            return config.getString(key);
        }
        return null;
    }

    /**
     * Parse location from a key string
     */
    private Location parseLocationFromKey(String key) {
        try {
            String[] parts = key.split(",");
            if (parts.length < 4) return null;

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                logger.warning("[MobSpawner] World not found: " + parts[0]);
                return null;
            }

            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            return new Location(world, x + 0.5, y, z + 0.5);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generate a unique ID for a spawner based on its location
     */
    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    /**
     * Rebuild spawner groups based on individual spawner properties
     */
    private void rebuildSpawnerGroups() {
        spawnerGroups.clear();

        for (Map.Entry<String, Spawner> entry : spawners.entrySet()) {
            String spawnerId = entry.getKey();
            Spawner spawner = entry.getValue();
            SpawnerProperties props = spawner.getProperties();

            String groupName = props.getSpawnerGroup();
            if (groupName != null && !groupName.isEmpty()) {
                spawnerGroups.computeIfAbsent(groupName, k -> new HashSet<>()).add(spawnerId);
            }
        }

        if (debug) {
            logger.info("[MobSpawner] Rebuilt " + spawnerGroups.size() + " spawner groups");
        }
    }

    /**
     * Save spawners to configuration
     */
    public void saveSpawners() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File file = new File(dataFolder, "spawners.yml");
            File backupFile = new File(dataFolder, "spawners.yml.bak");

            if (file.exists()) {
                try {
                    Files.copy(file.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Failed to create backup: " + e.getMessage());
                }
            }

            YamlConfiguration config = new YamlConfiguration();
            int count = 0;

            for (Map.Entry<String, Spawner> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                Spawner spawner = entry.getValue();
                Location loc = spawner.getLocation();

                if (loc == null || loc.getWorld() == null) {
                    logger.warning("[MobSpawner] Skipping null location for spawner: " + spawnerId);
                    continue;
                }

                String key = loc.getWorld().getName() + "," +
                        loc.getBlockX() + "," +
                        loc.getBlockY() + "," +
                        loc.getBlockZ();

                config.createSection(key);
                config.set(key + ".data", spawner.getDataString());
                config.set(key + ".visible", spawner.isVisible());
                config.set(key + ".displayMode", spawner.getDisplayMode());

                SpawnerProperties props = spawner.getProperties();
                ConfigurationSection propsSection = config.createSection(key + ".properties");
                props.saveToConfig(propsSection);

                count++;

                if (debug) {
                    logger.info("[MobSpawner] Saved spawner: " + key + " = " + spawner.getDataString());
                }
            }

            config.save(file);
            logger.info("[MobSpawner] Saved " + count + " spawners to " + file.getAbsolutePath());
        } catch (Exception e) {
            logger.severe("[MobSpawner] Critical error saving spawners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Add a spawner at a location
     */
    public boolean addSpawner(Location location, String data) {
        if (location == null || data == null || data.isEmpty()) {
            logger.warning("[MobSpawner] Cannot add spawner: invalid parameters");
            return false;
        }

        data = data.trim();
        if (debug) {
            logger.info("[MobSpawner] Adding spawner at " + formatLocation(location) + " with data: " + data);
        }

        if (!validateSpawnerData(data)) {
            logger.warning("[MobSpawner] Data validation failed: " + data);
            return false;
        }

        Location spawnerLoc = normalizeLocation(location);
        String spawnerId = generateSpawnerId(spawnerLoc);

        if (spawners.containsKey(spawnerId)) {
            Spawner spawner = spawners.get(spawnerId);
            spawner.parseSpawnerData(data);
            spawnerLocations.put(spawnerLoc, spawnerId);
            if (debug) {
                logger.info("[MobSpawner] Updated existing spawner");
            }
            return true;
        }

        Spawner spawner = new Spawner(spawnerLoc, data, defaultVisibility);

        try {
            if (setSpawnerBlock(location.getBlock(), spawnerId, defaultVisibility)) {
                spawners.put(spawnerId, spawner);
                spawnerLocations.put(spawnerLoc, spawnerId);
                saveSpawners();

                if (debug) {
                    logger.info("[MobSpawner] Created new spawner");
                }
                return true;
            } else {
                logger.warning("[MobSpawner] Failed to set block state - using fallback");
                spawners.put(spawnerId, spawner);
                spawnerLocations.put(spawnerLoc, spawnerId);
                saveSpawners();
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
     */
    private boolean setSpawnerBlock(Block block, String spawnerId, boolean visible) {
        try {
            block.setMetadata("isSpawner", new FixedMetadataValue(plugin, true));
            block.setMetadata("spawnerId", new FixedMetadataValue(plugin, spawnerId));

            Material blockType = visible ? Material.SPAWNER : Material.AIR;

            if (debug) {
                logger.info("[MobSpawner] Setting block to " + blockType + " at " + formatLocation(block.getLocation()));
            }

            block.setType(blockType, true);
            return block.getType() == blockType;
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error setting block state: " + e.getMessage());
        }
        return false;
    }

    /**
     * Normalize a location to the center of the block
     */
    private Location normalizeLocation(Location location) {
        return new Location(location.getWorld(),
                location.getBlockX() + 0.5,
                location.getBlockY(),
                location.getBlockZ() + 0.5);
    }

    /**
     * Add a spawner using a predefined template
     */
    public boolean addSpawnerFromTemplate(Location location, String templateName) {
        if (location == null || templateName == null) return false;

        if (debug) {
            logger.info("[MobSpawner] Adding spawner from template: " + templateName);
        }

        String cleanTemplateName = templateName.toLowerCase().trim();
        String templateData = spawnerTemplates.get(cleanTemplateName);

        if (templateData == null) {
            for (Map.Entry<String, String> entry : spawnerTemplates.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cleanTemplateName)) {
                    templateData = entry.getValue();
                    break;
                }
            }
        }

        if (templateData == null) {
            logger.warning("[MobSpawner] Template not found: " + templateName);
            return false;
        }

        Block block = location.getBlock();
        if (block.getType() == Material.AIR || block.getType() == Material.SPAWNER) {
            String spawnerId = generateSpawnerId(location);
            setSpawnerBlock(block, spawnerId, defaultVisibility);
        }

        Location spawnerLoc = normalizeLocation(location);
        Spawner spawner = new Spawner(spawnerLoc, templateData, defaultVisibility);
        String spawnerId = generateSpawnerId(spawnerLoc);

        spawners.put(spawnerId, spawner);
        spawnerLocations.put(spawnerLoc, spawnerId);
        saveSpawners();

        if (debug) {
            logger.info("[MobSpawner] Created spawner from template: " + templateName);
        }
        return true;
    }

    /**
     * Remove a spawner
     */
    public boolean removeSpawner(Location location) {
        if (location == null) return false;

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
            Spawner spawner = spawners.get(spawnerId);
            spawner.removeHologram();

            spawners.remove(spawnerId);
            spawnerLocations.remove(exactLoc);

            Block block = location.getBlock();
            if (block.hasMetadata("isSpawner")) {
                block.removeMetadata("isSpawner", plugin);
            }
            if (block.hasMetadata("spawnerId")) {
                block.removeMetadata("spawnerId", plugin);
            }

            rebuildSpawnerGroups();
            saveSpawners();

            if (debug) {
                logger.info("[MobSpawner] Removed spawner at " + formatLocation(location));
            }
            return true;
        }

        return false;
    }

    /**
     * Check if two locations are in the same block
     */
    private boolean isSameBlock(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
                loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ();
    }

    /**
     * Find the spawner that contains the given location
     */
    private Spawner getSpawnerAtLocation(Location location) {
        if (location == null) return null;

        String spawnerId = spawnerLocations.get(location);
        if (spawnerId != null) {
            return spawners.get(spawnerId);
        }

        for (Map.Entry<Location, String> entry : spawnerLocations.entrySet()) {
            if (isSameBlock(entry.getKey(), location)) {
                return spawners.get(entry.getValue());
            }
        }

        return null;
    }

    /**
     * Register a mob that was spawned from this location
     */
    public void registerMobSpawned(Location spawnerLocation, LivingEntity entity) {
        if (spawnerLocation == null || entity == null) return;

        Spawner spawner = getSpawnerAtLocation(spawnerLocation);
        if (spawner == null) {
            spawner = findNearestSpawnerObject(spawnerLocation, mobRespawnDistanceCheck);
        }

        if (spawner != null) {
            String spawnerId = getSpawnerIdFromLocation(spawner.getLocation());
            if (spawnerId != null) {
                entityToSpawner.put(entity.getUniqueId(), spawnerId);
                entity.setMetadata("spawner", new FixedMetadataValue(plugin, spawnerId));

                SpawnerProperties props = spawner.getProperties();
                if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
                    entity.setMetadata("spawnerGroup", new FixedMetadataValue(plugin, props.getSpawnerGroup()));
                }

                if (debug) {
                    logger.info("[MobSpawner] Registered mob " + entity.getUniqueId() + " with spawner " + spawnerId);
                }
            }
        }
    }

    /**
     * Register a mob death from this spawner
     */
    public void registerMobDeath(Location spawnerLocation, UUID entityId) {
        if (entityId == null) return;

        String spawnerId = entityToSpawner.remove(entityId);

        if (spawnerId != null && spawners.containsKey(spawnerId)) {
            Spawner spawner = spawners.get(spawnerId);
            spawner.registerMobDeath(entityId);
            logger.info("[MobSpawner] Registered mob death with spawner " + spawnerId);
            return;
        }

        if (spawnerLocation != null) {
            Spawner spawner = getSpawnerAtLocation(spawnerLocation);
            if (spawner != null) {
                spawner.registerMobDeath(entityId);
                logger.info("[MobSpawner] Registered mob death at " + formatLocation(spawnerLocation));
            }
        }
    }

    /**
     * Get spawner ID from a location
     */
    private String getSpawnerIdFromLocation(Location location) {
        if (location == null) return null;

        String spawnerId = spawnerLocations.get(location);
        if (spawnerId != null) {
            return spawnerId;
        }

        for (Map.Entry<Location, String> entry : spawnerLocations.entrySet()) {
            if (isSameBlock(entry.getKey(), location)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Find the nearest spawner object
     */
    private Spawner findNearestSpawnerObject(Location location, double maxDistance) {
        Location nearest = findNearestSpawner(location, maxDistance);
        if (nearest == null) return null;

        String spawnerId = spawnerLocations.get(nearest);
        return spawnerId != null ? spawners.get(spawnerId) : null;
    }

    /**
     * Find nearest spawner to a location
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
     * Validate spawner data format
     */
    public boolean validateSpawnerData(String data) {
        if (data == null || data.isEmpty()) {
            logger.warning("[MobSpawner] Empty data string");
            return false;
        }

        if (debug) {
            logger.info("[MobSpawner] Validating data: " + data);
        }

        try {
            String[] entries = data.split(",");
            for (String entry : entries) {
                entry = entry.trim();

                if (!entry.contains(":") || !entry.contains("@") || !entry.contains("#")) {
                    logger.warning("[MobSpawner] Invalid entry format: " + entry);
                    return false;
                }

                String[] parts = entry.split(":");
                if (parts.length != 2) {
                    logger.warning("[MobSpawner] Invalid type section: " + entry);
                    return false;
                }

                String mobType = parts[0].toLowerCase().trim();
                if (mobType.equals("wither_skeleton")) {
                    mobType = "witherskeleton";
                }

                if (!MobType.isValidType(mobType)) {
                    logger.warning("[MobSpawner] Invalid mob type: " + mobType);
                    return false;
                }

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

                try {
                    int tier = Integer.parseInt(tierParts[0].trim());
                    if (tier < 1 || tier > 6) {
                        logger.warning("[MobSpawner] Tier out of range: " + tier);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    logger.warning("[MobSpawner] Invalid tier: " + tierParts[0]);
                    return false;
                }

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

                String eliteStr = eliteParts[0].toLowerCase().trim();
                if (!eliteStr.equals("true") && !eliteStr.equals("false")) {
                    logger.warning("[MobSpawner] Invalid elite value: " + eliteParts[0]);
                    return false;
                }

                try {
                    int amount = Integer.parseInt(eliteParts[1].trim());
                    if (amount < 1 || amount > maxMobsPerSpawner) {
                        logger.warning("[MobSpawner] Invalid amount: " + amount);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    logger.warning("[MobSpawner] Invalid amount: " + eliteParts[1]);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.severe("[MobSpawner] Unexpected error validating data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Format a location to a readable string
     */
    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Unknown";
        }
        return String.format("%s [%d, %d, %d]",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    // Getters and delegation methods
    public boolean isDebugMode() { return debug; }
    public void setDebugMode(boolean debug) { this.debug = debug; }
    public void setSpawnersEnabled(boolean enabled) { spawnersEnabled = enabled; }
    public boolean areSpawnersEnabled() { return spawnersEnabled; }
    public double getMobRespawnDistanceCheck() { return mobRespawnDistanceCheck; }
    public boolean getDefaultVisibility() { return defaultVisibility; }
    public Map<String, String> getAllTemplates() { return new HashMap<>(spawnerTemplates); }
    public Set<String> getAllSpawnerGroups() { return new HashSet<>(spawnerGroups.keySet()); }
    public int getMaxMobsPerSpawner() { return maxMobsPerSpawner; }
    public double getPlayerDetectionRange() { return playerDetectionRange; }

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

    public int getActiveMobCount(Location location) {
        Spawner spawner = getSpawnerAtLocation(location);
        return spawner != null ? spawner.getTotalActiveMobs() : 0;
    }

    public boolean isSpawnerVisible(Location location) {
        Spawner spawner = getSpawnerAtLocation(location);
        return spawner != null && spawner.isVisible();
    }

    public boolean setSpawnerVisibility(Location location, boolean visible) {
        if (location == null) return false;

        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return false;

        spawner.setVisible(visible);
        Block block = location.getBlock();
        String spawnerId = getSpawnerIdFromLocation(location);
        if (spawnerId == null) return false;

        boolean result = setSpawnerBlock(block, spawnerId, visible);
        if (result) {
            saveSpawners();
            if (debug) {
                logger.info("[MobSpawner] Set spawner visibility to " + visible);
            }
        }
        return result;
    }

    public void createOrUpdateSpawnerHologram(Location location) {
        if (location == null) return;
        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner != null) {
            spawner.updateHologram();
        }
    }

    public void removeSpawnerHologram(Location location) {
        if (location == null) return;
        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner != null) {
            spawner.removeHologram();
        }
    }

    public SpawnerMetrics getSpawnerMetrics(Location location) {
        Spawner spawner = getSpawnerAtLocation(location);
        return spawner != null ? spawner.getMetrics() : null;
    }

    public boolean resetSpawner(Location location) {
        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) return false;
        spawner.reset();
        return true;
    }

    public void sendSpawnerInfo(Player player, Location location) {
        if (player == null || location == null) return;
        Spawner spawner = getSpawnerAtLocation(location);
        if (spawner == null) {
            player.sendMessage(ChatColor.RED + "No spawner found at this location.");
            return;
        }
        spawner.sendInfoTo(player);
    }

    public void resetAllSpawners() {
        for (Spawner spawner : spawners.values()) {
            spawner.reset();
        }
        logger.info("[MobSpawner] All spawners have been reset");
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
            logger.info("[MobSpawner] " + (show ? "Showed" : "Hid") + " " + count +
                    " spawners within " + radius + " blocks");
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
            if (loc.getWorld().equals(center.getWorld()) &&
                    loc.distanceSquared(center) <= radiusSquared) {
                result.add(loc);
            }
        }

        return result;
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
                    if (spawner.isVisible()) {
                        spawner.updateHologram();
                    }
                    break;
                default:
                    return false;
            }

            saveSpawners();
            return true;
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error setting property " + property + ": " + e.getMessage());
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
                boolean wasVisible = spawner.isVisible();
                if (enabled != wasVisible) {
                    spawner.setVisible(enabled);

                    Block block = spawner.getLocation().getBlock();
                    Material blockType = enabled ? Material.SPAWNER : Material.AIR;

                    if (block.getType() == Material.AIR || block.getType() == Material.SPAWNER) {
                        block.setType(blockType);
                    }

                    block.setMetadata("isSpawner", new FixedMetadataValue(plugin, true));
                    block.setMetadata("spawnerId", new FixedMetadataValue(plugin, spawnerId));

                    count++;
                }
            }
        }

        if (count > 0) {
            saveSpawners();
            if (debug) {
                logger.info("[MobSpawner] " + (enabled ? "Enabled" : "Disabled") +
                        " " + count + " spawners in group '" + groupName + "'");
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

        Set<String> groupIds = spawnerGroups.get(groupName);
        if (groupIds == null || groupIds.isEmpty()) return new ArrayList<>();

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
     * Begin a guided spawner creation process for a player
     *
     * @param player   The player creating the spawner
     * @param location The location for the new spawner
     */
    public void beginGuidedSpawnerCreation(Player player, Location location) {
        if (player == null || location == null) return;

        String playerName = player.getName();

        if (spawnerCreationSessions.containsKey(playerName)) {
            player.sendMessage(ChatColor.RED + "You already have an active spawner creation session.");
            player.sendMessage(ChatColor.GRAY + "Type 'cancel' to cancel the current session.");
            return;
        }

        SpawnerCreationSession session = new SpawnerCreationSession(location, playerName);
        spawnerCreationSessions.put(playerName, session);
        creatingSpawner.put(playerName, location);

        player.sendMessage(ChatColor.GREEN + "=== Spawner Creation Wizard ===");
        player.sendMessage(ChatColor.GRAY + "Creating a spawner at: " + location.getWorld().getName() +
                " [" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "]");

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
     * Create an in-game control panel for a spawner
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

        String playerId = player.getUniqueId().toString();
        String panelId = "control_panel_" + playerId;

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Spawner Control Panel");
        lines.add(ChatColor.YELLOW + "Location: " + ChatColor.WHITE +
                location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());

        SpawnerProperties props = spawner.getProperties();
        SpawnerMetrics metrics = spawner.getMetrics();

        if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
            lines.add(ChatColor.YELLOW + "Group: " + ChatColor.WHITE + props.getSpawnerGroup());
        }

        lines.add(ChatColor.YELLOW + "Visibility: " +
                (spawner.isVisible() ? ChatColor.GREEN + "Visible" : ChatColor.RED + "Hidden"));

        lines.add(ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE +
                spawner.getTotalActiveMobs() + "/" + spawner.getTotalDesiredMobs());

        if (metrics.getTotalSpawned() > 0) {
            lines.add(ChatColor.YELLOW + "Spawned: " + ChatColor.WHITE + metrics.getTotalSpawned() +
                    " | Killed: " + metrics.getTotalKilled());
        }

        lines.add(ChatColor.GREEN + "=== Available Commands ===");
        lines.add(ChatColor.GRAY + "/spawner toggle show/hide");
        lines.add(ChatColor.GRAY + "/spawner reset");
        lines.add(ChatColor.GRAY + "/spawner edit");
        lines.add(ChatColor.GRAY + "/spawner visualize");

        Location panelLoc = player.getLocation().clone().add(0, 2, 0);
        HologramManager.createOrUpdateHologram(panelId, panelLoc, lines, 0.25);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HologramManager.removeHologram(panelId);
        }, 300L); // 15 seconds

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

        cleanupPlayerVisualizations(playerUUID);

        SpawnerProperties props = spawner.getProperties();
        double radiusX = props.getSpawnRadiusX();
        double radiusY = props.getSpawnRadiusY();
        double radiusZ = props.getSpawnRadiusZ();

        List<ArmorStand> markers = new ArrayList<>();
        Location origin = location.clone();

        // Create corner markers for spawn area
        for (double x = -radiusX; x <= radiusX; x += radiusX) {
            for (double y = -radiusY; y <= radiusY; y += radiusY) {
                for (double z = -radiusZ; z <= radiusZ; z += radiusZ) {
                    Location markerLoc = origin.clone().add(x, y, z);

                    ArmorStand marker = (ArmorStand) origin.getWorld().spawnEntity(markerLoc, EntityType.ARMOR_STAND);
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
        double detectionRange = props.getPlayerDetectionRangeOverride() > 0 ?
                props.getPlayerDetectionRangeOverride() : playerDetectionRange;

        Location detectionLoc = origin.clone().add(0, 1, 0);
        ArmorStand rangeMarker = (ArmorStand) origin.getWorld().spawnEntity(detectionLoc, EntityType.ARMOR_STAND);

        rangeMarker.setVisible(false);
        rangeMarker.setGravity(false);
        rangeMarker.setMarker(true);
        rangeMarker.setCustomNameVisible(true);
        rangeMarker.setCustomName(ChatColor.GREEN + "Detection Range: " + ChatColor.WHITE +
                String.format("%.1f", detectionRange) + " blocks");

        markers.add(rangeMarker);

        // Create label for spawn boundaries
        Location labelLoc = origin.clone().add(0, 2, 0);
        ArmorStand labelMarker = (ArmorStand) origin.getWorld().spawnEntity(labelLoc, EntityType.ARMOR_STAND);

        labelMarker.setVisible(false);
        labelMarker.setGravity(false);
        labelMarker.setMarker(true);
        labelMarker.setCustomNameVisible(true);
        labelMarker.setCustomName(ChatColor.AQUA + "Spawn Radius: " + ChatColor.WHITE +
                String.format("%.1f", radiusX) + "x" +
                String.format("%.1f", radiusY) + "x" +
                String.format("%.1f", radiusZ));

        markers.add(labelMarker);

        visualizations.put(vizKey, markers);

        player.sendMessage(ChatColor.GREEN + "Spawner visualization created.");
        player.sendMessage(ChatColor.GRAY + "Visualization will disappear in 10 seconds.");

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

        for (Map.Entry<String, List<ArmorStand>> entry : visualizations.entrySet()) {
            if (entry.getKey().startsWith(playerUUID + "_")) {
                for (ArmorStand stand : entry.getValue()) {
                    if (stand != null && !stand.isDead()) {
                        stand.remove();
                    }
                }
                toRemove.add(entry.getKey());
            }
        }

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

        if (session == null) return;

        if (message.equalsIgnoreCase("cancel")) {
            spawnerCreationSessions.remove(playerName);
            creatingSpawner.remove(playerName);
            player.sendMessage(ChatColor.RED + "Spawner creation cancelled.");
            return;
        }

        // Process the creation steps (implementation would depend on SpawnerCreationSession)
        // This is a simplified version - the full implementation would handle all steps
        player.sendMessage(ChatColor.YELLOW + "Spawner creation chat processing not fully implemented in this example.");
        player.sendMessage(ChatColor.GRAY + "Use templates or direct spawner data instead.");
    }

    /**
     * Shutdown the spawner system
     */
    public void shutdown() {
        try {
            saveSpawners();

            if (mainProcessingTask != null) mainProcessingTask.cancel();
            if (autoSaveTask != null) autoSaveTask.cancel();
            if (sessionCleanupTask != null) sessionCleanupTask.cancel();
            if (visualizationCleanupTask != null) visualizationCleanupTask.cancel();

            for (List<ArmorStand> stands : visualizations.values()) {
                for (ArmorStand stand : stands) {
                    if (stand != null && !stand.isDead()) {
                        stand.remove();
                    }
                }
            }
            visualizations.clear();

            for (Spawner spawner : spawners.values()) {
                if (spawner.isVisible()) {
                    spawner.removeHologram();
                }
            }

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