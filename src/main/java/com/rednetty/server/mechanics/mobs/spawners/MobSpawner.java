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
import java.util.logging.Logger;

/**
 * FIXED: Completely rewritten MobSpawner with reliable, synchronous operations
 * No async complexity, simple and deterministic behavior
 */
public class MobSpawner implements Listener {
    private static MobSpawner instance;

    // ================ CORE STORAGE ================
    private final Map<String, Spawner> spawners = new ConcurrentHashMap<>();
    private final Map<Location, String> spawnerLocations = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> spawnerGroups = new HashMap<>();
    private final Map<UUID, String> entityToSpawner = new ConcurrentHashMap<>();

    // ================ TEMPLATES ================
    private final Map<String, String> spawnerTemplates = new HashMap<>();

    // ================ PLAYER SESSIONS ================
    private final Map<String, Location> creatingSpawner = new HashMap<>();
    private final Map<String, SpawnerCreationSession> spawnerCreationSessions = new HashMap<>();

    // ================ VISUALIZATION ================
    private final Map<String, List<ArmorStand>> visualizations = new HashMap<>();

    private final Logger logger;
    private final YakRealms plugin;

    // ================ CONFIGURATION ================
    private boolean spawnersEnabled = true;
    private boolean debug = false;
    private int maxMobsPerSpawner = 10;
    private double playerDetectionRange = 40.0;
    private double mobRespawnDistanceCheck = 25.0;
    private boolean defaultVisibility = false;

    // ================ TASKS - SIMPLIFIED ================
    private BukkitTask mainProcessingTask;
    private BukkitTask autoSaveTask;
    private BukkitTask cleanupTask;

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

            logger.info("[MobSpawner] FIXED: Initialized successfully with " + spawners.size() + " spawners");
        } catch (Exception e) {
            logger.severe("[MobSpawner] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initialize spawner templates with working configurations
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

            // Validate and clamp values
            maxMobsPerSpawner = Math.max(1, Math.min(50, maxMobsPerSpawner));
            playerDetectionRange = Math.max(10.0, Math.min(100.0, playerDetectionRange));
            mobRespawnDistanceCheck = Math.max(5.0, Math.min(50.0, mobRespawnDistanceCheck));

            if (debug) {
                logger.info("[MobSpawner] Configuration loaded successfully");
            }
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error loading config: " + e.getMessage());
        }
    }

    /**
     * FIXED: Start only essential tasks - no complex async operations
     */
    private void startTasks() {
        logger.info("[MobSpawner] Starting essential tasks...");

        // Main processing task - handles all spawner logic synchronously
        mainProcessingTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    processAllSpawners();
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Main processing error: " + e.getMessage());
                    if (debug) e.printStackTrace();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second

        // Auto-save task
        autoSaveTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    saveSpawners();
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Auto-save error: " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 1200L, 6000L); // Every 5 minutes

        // Cleanup task
        cleanupTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    performCleanup();
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Cleanup error: " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every minute

        logger.info("[MobSpawner] Essential tasks started successfully");
    }

    /**
     * FIXED: Simple, reliable spawner processing
     */
    private void processAllSpawners() {
        if (!spawnersEnabled) return;

        try {
            int processedCount = 0;
            for (Spawner spawner : spawners.values()) {
                try {
                    spawner.processTick();
                    processedCount++;
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Error processing spawner: " + e.getMessage());
                    if (debug) e.printStackTrace();
                }
            }

            if (debug && processedCount > 0) {
                // Only log every 30 seconds in debug mode to avoid spam
                if (System.currentTimeMillis() % 30000 < 1000) {
                    logger.info("[MobSpawner] Processed " + processedCount + " spawners");
                }
            }
        } catch (Exception e) {
            logger.warning("[MobSpawner] Critical error in spawner processing: " + e.getMessage());
        }
    }

    /**
     * Clean up expired sessions and invalid data
     */
    private void performCleanup() {
        try {
            // Clean up expired creation sessions
            cleanupExpiredSessions();

            // Clean up orphaned entity mappings
            cleanupOrphanedEntries();

            // Clean up old visualizations
            cleanupVisualizations();

        } catch (Exception e) {
            logger.warning("[MobSpawner] Cleanup error: " + e.getMessage());
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
    }

    /**
     * Clean up orphaned entries
     */
    private void cleanupOrphanedEntries() {
        // Clean up entity to spawner mappings for invalid entities
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

        // Clean up location mappings for non-existent spawners
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
    }

    /**
     * Clean up old visualizations
     */
    private void cleanupVisualizations() {
        long currentTime = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, List<ArmorStand>> entry : visualizations.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split("_");
            if (parts.length >= 2) {
                try {
                    long timestamp = Long.parseLong(parts[parts.length - 1]);
                    if (currentTime - timestamp > 30000) { // 30 seconds
                        for (ArmorStand stand : entry.getValue()) {
                            if (stand != null && !stand.isDead()) {
                                stand.remove();
                            }
                        }
                        toRemove.add(key);
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid format, remove anyway
                    toRemove.add(key);
                }
            }
        }

        for (String key : toRemove) {
            visualizations.remove(key);
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
                logger.info("[MobSpawner] No spawners.yml file found, starting fresh");
                return;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            int count = 0;
            int failed = 0;

            for (String key : config.getKeys(false)) {
                try {
                    if (!key.contains(",")) continue;

                    Location loc = parseLocationFromKey(key);
                    if (loc == null) {
                        logger.warning("[MobSpawner] Invalid location format: " + key);
                        failed++;
                        continue;
                    }

                    String data = getSpawnerDataFromConfig(config, key);
                    if (data == null || data.isEmpty()) {
                        logger.warning("[MobSpawner] No data for spawner: " + key);
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

                    // Load properties if they exist
                    if (config.isConfigurationSection(key + ".properties")) {
                        ConfigurationSection propsSection = config.getConfigurationSection(key + ".properties");
                        SpawnerProperties props = SpawnerProperties.loadFromConfig(propsSection);
                        spawner.setProperties(props);

                        // Add to groups
                        if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
                            spawnerGroups.computeIfAbsent(props.getSpawnerGroup(), k -> new HashSet<>()).add(spawnerId);
                        }
                    }

                    // Set block state
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

            logger.info("[MobSpawner] FIXED: Loaded " + count + " spawners" +
                    (failed > 0 ? ", " + failed + " failed" : ""));
        } catch (Exception e) {
            logger.severe("[MobSpawner] Critical error loading spawners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get spawner data from config (handles both old and new formats)
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
     * Generate a unique ID for a spawner
     */
    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    /**
     * Rebuild spawner groups
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

            // Create backup
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
            }

            config.save(file);
            logger.info("[MobSpawner] FIXED: Saved " + count + " spawners successfully");
        } catch (Exception e) {
            logger.severe("[MobSpawner] Critical error saving spawners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * FIXED: Add a spawner with better error handling
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

        // Check if spawner already exists
        if (spawners.containsKey(spawnerId)) {
            Spawner spawner = spawners.get(spawnerId);
            spawner.parseSpawnerData(data);
            spawnerLocations.put(spawnerLoc, spawnerId);
            logger.info("[MobSpawner] Updated existing spawner");
            return true;
        }

        // Create new spawner
        Spawner spawner = new Spawner(spawnerLoc, data, defaultVisibility);

        try {
            // Set block state
            boolean blockSet = setSpawnerBlock(location.getBlock(), spawnerId, defaultVisibility);

            // Register spawner even if block setting fails
            spawners.put(spawnerId, spawner);
            spawnerLocations.put(spawnerLoc, spawnerId);
            saveSpawners();

            logger.info("[MobSpawner] FIXED: Created new spawner" + (blockSet ? "" : " (block setting failed)"));
            return true;

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
            block.setType(blockType, true);

            return block.getType() == blockType;
        } catch (Exception e) {
            logger.warning("[MobSpawner] Error setting block state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Normalize a location to block center
     */
    private Location normalizeLocation(Location location) {
        return new Location(location.getWorld(),
                location.getBlockX() + 0.5,
                location.getBlockY(),
                location.getBlockZ() + 0.5);
    }

    /**
     * Add a spawner using a template
     */
    public boolean addSpawnerFromTemplate(Location location, String templateName) {
        if (location == null || templateName == null) return false;

        if (debug) {
            logger.info("[MobSpawner] Adding spawner from template: " + templateName);
        }

        String cleanTemplateName = templateName.toLowerCase().trim();
        String templateData = spawnerTemplates.get(cleanTemplateName);

        if (templateData == null) {
            // Try case-insensitive search
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

        return addSpawner(location, templateData);
    }

    /**
     * Remove a spawner
     */
    public boolean removeSpawner(Location location) {
        if (location == null) return false;

        String spawnerId = null;
        Location exactLoc = null;

        // Find the spawner
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

            // Clean up block metadata
            Block block = location.getBlock();
            if (block.hasMetadata("isSpawner")) {
                block.removeMetadata("isSpawner", plugin);
            }
            if (block.hasMetadata("spawnerId")) {
                block.removeMetadata("spawnerId", plugin);
            }

            rebuildSpawnerGroups();
            saveSpawners();

            logger.info("[MobSpawner] FIXED: Removed spawner at " + formatLocation(location));
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
     * Find the spawner at a location
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
     * Register a mob that was spawned from a spawner
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
     * Register a mob death
     */
    public void registerMobDeath(Location spawnerLocation, UUID entityId) {
        if (entityId == null) return;

        String spawnerId = entityToSpawner.remove(entityId);

        if (spawnerId != null && spawners.containsKey(spawnerId)) {
            Spawner spawner = spawners.get(spawnerId);
            spawner.registerMobDeath(entityId);
            if (debug) {
                logger.info("[MobSpawner] Registered mob death with spawner " + spawnerId);
            }
            return;
        }

        if (spawnerLocation != null) {
            Spawner spawner = getSpawnerAtLocation(spawnerLocation);
            if (spawner != null) {
                spawner.registerMobDeath(entityId);
                if (debug) {
                    logger.info("[MobSpawner] Registered mob death at " + formatLocation(spawnerLocation));
                }
            }
        }
    }

    /**
     * Get spawner ID from location
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
     * Find nearest spawner location
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
     * FIXED: Improved spawner data validation
     */
    public boolean validateSpawnerData(String data) {
        if (data == null || data.isEmpty()) {
            logger.warning("[MobSpawner] Empty data string");
            return false;
        }

        try {
            String[] entries = data.split(",");
            for (String entry : entries) {
                entry = entry.trim();

                if (!entry.contains(":") || !entry.contains("@") || !entry.contains("#")) {
                    logger.warning("[MobSpawner] Invalid entry format: " + entry);
                    return false;
                }

                // Use MobEntry validation
                try {
                    MobEntry.fromString(entry);
                } catch (Exception e) {
                    logger.warning("[MobSpawner] Failed to parse entry: " + entry + " - " + e.getMessage());
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.severe("[MobSpawner] Unexpected error validating data: " + e.getMessage());
            return false;
        }
    }

    /**
     * Format a location for logging
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

    // ================ DELEGATION METHODS ================

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
     * Shutdown the spawner system
     */
    public void shutdown() {
        try {
            saveSpawners();

            // Cancel tasks
            if (mainProcessingTask != null) mainProcessingTask.cancel();
            if (autoSaveTask != null) autoSaveTask.cancel();
            if (cleanupTask != null) cleanupTask.cancel();

            // Clean up visualizations
            for (List<ArmorStand> stands : visualizations.values()) {
                for (ArmorStand stand : stands) {
                    if (stand != null && !stand.isDead()) {
                        stand.remove();
                    }
                }
            }
            visualizations.clear();

            // Clean up holograms
            for (Spawner spawner : spawners.values()) {
                if (spawner.isVisible()) {
                    spawner.removeHologram();
                }
            }

            // Clear all data
            spawners.clear();
            spawnerLocations.clear();
            entityToSpawner.clear();
            creatingSpawner.clear();
            spawnerCreationSessions.clear();
            spawnerGroups.clear();

            logger.info("[MobSpawner] FIXED: Shutdown completed successfully");
        } catch (Exception e) {
            logger.severe("[MobSpawner] Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}