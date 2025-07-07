package com.rednetty.server.mechanics.world.mobs.spawners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.mobs.SpawnerProperties;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * FIXED: Complete MobSpawner system with proper startup timing and dependency management
 */
public class MobSpawner implements Listener {
    private static MobSpawner instance;

    // ================ CORE STORAGE ================
    private final Map<String, Spawner> spawners = new ConcurrentHashMap<>();
    private final Map<Location, String> locationToId = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> spawnerGroups = new HashMap<>();
    private final Map<String, String> templates = new HashMap<>();
    private final Map<String, SpawnerCreationSession> creationSessions = new HashMap<>();

    // ================ CONFIGURATION ================
    private boolean enabled = true;
    private boolean debug = false;
    private int maxMobsPerSpawner = 10;
    private double playerDetectionRange = 40.0;
    private boolean defaultVisibility = false;

    // ================ STARTUP MANAGEMENT ================
    private boolean initialized = false;
    private boolean spawnersLoaded = false;
    private boolean dependenciesReady = false;
    private int loadAttempts = 0;
    private static final int MAX_LOAD_ATTEMPTS = 5;
    private BukkitTask delayedLoadTask;

    // ================ TASKS ================
    private BukkitTask mainTask;
    private BukkitTask saveTask;

    private final Logger logger;
    private final YakRealms plugin;

    private MobSpawner() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.debug = plugin.isDebugMode();
        this.defaultVisibility = plugin.getConfig().getBoolean("mechanics.mobs.spawner-default-visibility", false);
        initializeTemplates();
    }

    public static MobSpawner getInstance() {
        if (instance == null) {
            instance = new MobSpawner();
        }
        return instance;
    }

    // ================ ENHANCED INITIALIZATION WITH PROPER TIMING ================

    /**
     * FIXED: Enhanced initialization with proper dependency management and timing
     */
    public void initialize() {
        try {
            logger.info("§6[MobSpawner] §7Starting initialization...");

            // Register events first
            Bukkit.getPluginManager().registerEvents(this, plugin);

            // Load basic configuration
            loadConfig();

            // Check if we can load spawners immediately or need to delay
            if (canLoadSpawnersNow()) {
                loadSpawnersImmediate();
            } else {
                scheduleDelayedSpawnerLoad();
            }

            // Start basic tasks (spawner processing will start when spawners are loaded)
            startBasicTasks();

            initialized = true;
            logger.info("§a[MobSpawner] §7Basic initialization complete. Spawners: " +
                    (spawnersLoaded ? "Loaded (" + spawners.size() + ")" : "Loading..."));

        } catch (Exception e) {
            logger.severe("§c[MobSpawner] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if all dependencies are ready for spawner loading
     */
    private boolean canLoadSpawnersNow() {
        try {
            // Check if worlds are loaded
            if (Bukkit.getWorlds().isEmpty()) {
                if (debug) {
                    logger.info("§6[MobSpawner] §7Worlds not loaded yet, delaying spawner load");
                }
                return false;
            }

            // Check if server is still starting up
            if (!Bukkit.getServer().getPluginManager().isPluginEnabled(plugin)) {
                if (debug) {
                    logger.info("§6[MobSpawner] §7Plugin not fully enabled yet, delaying spawner load");
                }
                return false;
            }

            // Check if required dependencies are available
            try {
                // Test if HologramManager is available (this often fails during startup)
                Class.forName("com.rednetty.server.mechanics.world.holograms.HologramManager");

                // Test basic world access
                for (World world : Bukkit.getWorlds()) {
                    world.getName(); // Simple test to ensure world is accessible
                }

                dependenciesReady = true;
                return true;

            } catch (Exception e) {
                if (debug) {
                    logger.info("§6[MobSpawner] §7Dependencies not ready: " + e.getMessage());
                }
                return false;
            }

        } catch (Exception e) {
            if (debug) {
                logger.info("§6[MobSpawner] §7Dependency check failed: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Load spawners immediately (during startup if possible)
     */
    private void loadSpawnersImmediate() {
        try {
            loadSpawners();
            spawnersLoaded = true;
            startMainTasks();
            logger.info("§a[MobSpawner] §7Spawners loaded immediately: " + spawners.size());
        } catch (Exception e) {
            logger.warning("§c[MobSpawner] Immediate spawner load failed: " + e.getMessage());
            scheduleDelayedSpawnerLoad();
        }
    }

    /**
     * Schedule delayed spawner loading with retry mechanism
     */
    private void scheduleDelayedSpawnerLoad() {
        if (delayedLoadTask != null) {
            delayedLoadTask.cancel();
        }

        // Calculate delay - increase with each attempt
        long delay = Math.min(20L * (loadAttempts + 1), 100L); // 1-5 seconds max

        delayedLoadTask = new BukkitRunnable() {
            @Override
            public void run() {
                attemptDelayedSpawnerLoad();
            }
        }.runTaskLater(plugin, delay);

        logger.info("§6[MobSpawner] §7Scheduled delayed spawner load (attempt " + (loadAttempts + 1) +
                "/" + MAX_LOAD_ATTEMPTS + ") in " + (delay / 20.0) + " seconds");
    }

    /**
     * Attempt to load spawners with retry logic
     */
    private void attemptDelayedSpawnerLoad() {
        loadAttempts++;

        try {
            if (canLoadSpawnersNow()) {
                loadSpawners();
                spawnersLoaded = true;
                startMainTasks();

                logger.info("§a[MobSpawner] §7Delayed spawner load successful! Loaded " + spawners.size() +
                        " spawners (attempt " + loadAttempts + ")");
                return;
            }
        } catch (Exception e) {
            logger.warning("§c[MobSpawner] Spawner load attempt " + loadAttempts + " failed: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }

        // Retry if we haven't exceeded max attempts
        if (loadAttempts < MAX_LOAD_ATTEMPTS) {
            scheduleDelayedSpawnerLoad();
        } else {
            logger.severe("§c[MobSpawner] Failed to load spawners after " + MAX_LOAD_ATTEMPTS +
                    " attempts. Manual reload may be required.");

            // Create empty spawner system so plugin doesn't break
            spawnersLoaded = true;
            startMainTasks();
        }
    }

    /**
     * Force reload spawners (for commands/manual reload)
     */
    public void forceReloadSpawners() {
        try {
            logger.info("§6[MobSpawner] §7Force reloading spawners...");

            // Stop existing tasks
            if (mainTask != null) mainTask.cancel();
            if (saveTask != null) saveTask.cancel();
            if (delayedLoadTask != null) delayedLoadTask.cancel();

            // Clear existing data
            spawners.clear();
            locationToId.clear();
            spawnerGroups.clear();

            // Reset flags
            spawnersLoaded = false;
            loadAttempts = 0;

            // Load spawners
            loadSpawners();
            spawnersLoaded = true;

            // Restart tasks
            startMainTasks();

            logger.info("§a[MobSpawner] §7Force reload complete! Loaded " + spawners.size() + " spawners");

        } catch (Exception e) {
            logger.severe("§c[MobSpawner] Force reload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Start basic tasks that don't require spawners
     */
    private void startBasicTasks() {
        // Auto-save task - can run even without spawners loaded
        if (saveTask == null || saveTask.isCancelled()) {
            saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                try {
                    if (spawnersLoaded) {
                        saveSpawners();
                    }
                } catch (Exception e) {
                    logger.warning("§c[MobSpawner] Save error: " + e.getMessage());
                }
            }, 6000L, 6000L); // Every 5 minutes
        }
    }

    /**
     * Start main processing tasks (only when spawners are loaded)
     */
    private void startMainTasks() {
        if (mainTask != null && !mainTask.isCancelled()) {
            return; // Already running
        }

        // Main processing task - only start when spawners are loaded
        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (spawnersLoaded && enabled) {
                    processAllSpawners();
                }
            } catch (Exception e) {
                logger.warning("§c[MobSpawner] Processing error: " + e.getMessage());
                if (debug) e.printStackTrace();
            }
        }, 20L, 20L); // Every second

        logger.info("§a[MobSpawner] §7Main processing tasks started");
    }

    private void initializeTemplates() {
        templates.put("basic_t1", "skeleton:1@false#2,zombie:1@false#2");
        templates.put("basic_t2", "skeleton:2@false#2,zombie:2@false#2");
        templates.put("standard_t3", "skeleton:3@false#3,zombie:3@false#2,spider:3@false#1");
        templates.put("standard_t4", "skeleton:4@false#3,zombie:4@false#2,spider:4@false#1");
        templates.put("advanced_t5", "skeleton:5@false#2,witherskeleton:5@false#2,zombie:5@false#1");
        templates.put("elite_t3", "skeleton:3@true#1,zombie:3@true#1");
        templates.put("elite_t4", "skeleton:4@true#1,witherskeleton:4@true#1");
        templates.put("elite_t5", "witherskeleton:5@true#1");
        templates.put("mixed_t3", "skeleton:3@false#2,zombie:3@false#2,skeleton:3@true#1");
        templates.put("mixed_t4", "skeleton:4@false#2,witherskeleton:4@false#2,witherskeleton:4@true#1");
        templates.put("magma_t4", "magmacube:4@false#4,magmacube:4@true#1");
        templates.put("spider_t4", "spider:4@false#3,cavespider:4@false#2");
        templates.put("imp_t3", "imp:3@false#4");
        templates.put("boss_t5", "skeleton:5@true#1");
        templates.put("frozen_t6", "skeleton:6@false#2,witherskeleton:6@false#1");
    }

    private void loadConfig() {
        try {
            plugin.saveDefaultConfig();
            this.debug = plugin.getConfig().getBoolean("debug", false);
            this.maxMobsPerSpawner = Math.max(1, Math.min(50,
                    plugin.getConfig().getInt("mechanics.mobs.max-mobs-per-spawner", 10)));
            this.playerDetectionRange = Math.max(10.0, Math.min(100.0,
                    plugin.getConfig().getDouble("mechanics.mobs.player-detection-range", 40.0)));
            this.defaultVisibility = plugin.getConfig().getBoolean("mechanics.mobs.spawner-default-visibility", false);

            if (debug) {
                logger.info("§6[MobSpawner] §7Configuration loaded");
            }
        } catch (Exception e) {
            logger.warning("§c[MobSpawner] Config error: " + e.getMessage());
        }
    }

    /**
     * Enhanced spawner processing with better error handling
     */
    private void processAllSpawners() {
        if (!enabled || !spawnersLoaded) return;

        int processedCount = 0;
        int errorCount = 0;

        for (Map.Entry<String, Spawner> entry : spawners.entrySet()) {
            String spawnerId = entry.getKey();
            Spawner spawnerInstance = entry.getValue();

            try {
                spawnerInstance.tick();
                processedCount++;

            } catch (Exception e) {
                errorCount++;
                logger.warning("§c[MobSpawner] Spawner " + spawnerId + " error: " + e.getMessage());
                if (debug) e.printStackTrace();
            }
        }

        if (debug && (processedCount > 0 || errorCount > 0)) {
            logger.fine("§6[MobSpawner] §7Processed " + processedCount + " spawners, " + errorCount + " errors");
        }
    }

    // ================ ENHANCED LOADING AND SAVING ================

    /**
     * FIXED: Enhanced spawner loading with better error handling and validation
     */
    public void loadSpawners() {
        try {
            spawners.clear();
            locationToId.clear();
            spawnerGroups.clear();

            File file = new File(plugin.getDataFolder(), "spawners.yml");
            if (!file.exists()) {
                logger.info("§6[MobSpawner] §7No spawners.yml found, starting with empty spawner system");
                return;
            }

            if (!file.canRead()) {
                logger.warning("§c[MobSpawner] Cannot read spawners.yml file");
                return;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            int loaded = 0;
            int skipped = 0;

            for (String key : config.getKeys(false)) {
                try {
                    if (loadSingleSpawner(config, key)) {
                        loaded++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    skipped++;
                    logger.warning("§c[MobSpawner] Error loading spawner " + key + ": " + e.getMessage());
                    if (debug) e.printStackTrace();
                }
            }

            logger.info("§a[MobSpawner] §7Loaded " + loaded + " spawners" +
                    (skipped > 0 ? " (skipped " + skipped + " invalid)" : ""));

        } catch (Exception e) {
            logger.severe("§c[MobSpawner] Critical load error: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to trigger retry mechanism
        }
    }

    /**
     * Load a single spawner with validation
     */
    private boolean loadSingleSpawner(YamlConfiguration config, String key) {
        try {
            Location location = parseLocation(key);
            if (location == null) {
                if (debug) {
                    logger.warning("§c[MobSpawner] Invalid location format: " + key);
                }
                return false;
            }

            // Validate world is loaded
            if (location.getWorld() == null) {
                logger.warning("§c[MobSpawner] World not loaded for spawner: " + key);
                return false;
            }

            String data = getSpawnerData(config, key);
            if (data == null || data.isEmpty()) {
                if (debug) {
                    logger.warning("§c[MobSpawner] No data for spawner: " + key);
                }
                return false;
            }

            if (!validateSpawnerData(data)) {
                logger.warning("§c[MobSpawner] Invalid spawner data: " + key);
                return false;
            }

            boolean visible = config.getBoolean(key + ".visible", defaultVisibility);
            int displayMode = config.getInt(key + ".displayMode", 0);

            Spawner spawner = new Spawner(location, data, visible);
            spawner.setDisplayMode(displayMode);

            // Load properties
            if (config.isConfigurationSection(key + ".properties")) {
                ConfigurationSection propsSection = config.getConfigurationSection(key + ".properties");
                SpawnerProperties props = SpawnerProperties.loadFromConfig(propsSection);
                spawner.setProperties(props);

                // Add to groups
                if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
                    String spawnerId = generateSpawnerId(location);
                    spawnerGroups.computeIfAbsent(props.getSpawnerGroup(), k -> new HashSet<>()).add(spawnerId);
                }
            }

            String spawnerId = generateSpawnerId(location);
            spawners.put(spawnerId, spawner);
            locationToId.put(location, spawnerId);

            // FIXED: Set block state properly with validation
            setSpawnerBlockSafe(location.getBlock(), visible);

            return true;

        } catch (Exception e) {
            if (debug) {
                logger.warning("§c[MobSpawner] Error loading single spawner " + key + ": " + e.getMessage());
            }
            return false;
        }
    }

    private Location parseLocation(String key) {
        try {
            String[] parts = key.split(",");
            if (parts.length < 4) return null;

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;

            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            return new Location(world, x + 0.5, y, z + 0.5);
        } catch (Exception e) {
            return null;
        }
    }

    private String getSpawnerData(YamlConfiguration config, String key) {
        if (config.contains(key + ".data")) {
            return config.getString(key + ".data");
        } else if (!config.isConfigurationSection(key)) {
            return config.getString(key);
        }
        return null;
    }

    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    /**
     * FIXED: Safe spawner block setting with validation and error handling
     */
    private void setSpawnerBlockSafe(Block block, boolean visible) {
        try {
            if (block == null || block.getWorld() == null) {
                if (debug) {
                    logger.warning("§c[MobSpawner] Invalid block for spawner placement");
                }
                return;
            }

            // Check if chunk is loaded
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                if (debug) {
                    logger.warning("§c[MobSpawner] Chunk not loaded for spawner at " + formatLocation(block.getLocation()));
                }
                return;
            }

            // Set metadata first
            block.setMetadata("isSpawner", new FixedMetadataValue(plugin, true));
            block.setMetadata("spawnerVisible", new FixedMetadataValue(plugin, visible));

            // Set block type based on visibility with validation
            try {
                if (visible) {
                    block.setType(Material.SPAWNER, false);

                    // Verify the block was set correctly
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            if (block.getType() != Material.SPAWNER) {
                                if (debug) {
                                    logger.warning("§c[MobSpawner] Block type verification failed, retrying...");
                                }
                                block.setType(Material.SPAWNER, true);
                            }
                        } catch (Exception e) {
                            if (debug) {
                                logger.warning("§c[MobSpawner] Block verification error: " + e.getMessage());
                            }
                        }
                    }, 5L);
                } else {
                    block.setType(Material.AIR, false);
                }

                if (debug) {
                    logger.fine("§6[MobSpawner] §7Set spawner block at " + formatLocation(block.getLocation()) +
                            " visible=" + visible + " type=" + block.getType());
                }

            } catch (Exception e) {
                logger.warning("§c[MobSpawner] Block type setting error: " + e.getMessage());
                // Don't rethrow - this shouldn't stop spawner loading
            }

        } catch (Exception e) {
            logger.warning("§c[MobSpawner] Block setting error: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    public void saveSpawners() {
        try {
            if (!spawnersLoaded) {
                if (debug) {
                    logger.info("§6[MobSpawner] §7Skipping save - spawners not loaded yet");
                }
                return;
            }

            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File file = new File(dataFolder, "spawners.yml");
            File backupFile = new File(dataFolder, "spawners.yml.bak");

            // Create backup
            if (file.exists()) {
                try {
                    java.nio.file.Files.copy(file.toPath(), backupFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    logger.warning("§c[MobSpawner] Backup failed: " + e.getMessage());
                }
            }

            YamlConfiguration config = new YamlConfiguration();
            int count = 0;

            for (Map.Entry<String, Spawner> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                Spawner spawner = entry.getValue();
                Location loc = spawner.getLocation();

                if (loc == null || loc.getWorld() == null) continue;

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

            if (debug) {
                logger.fine("§6[MobSpawner] §7Saved " + count + " spawners");
            }
        } catch (Exception e) {
            logger.severe("§c[MobSpawner] Save error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================ SPAWNER MANAGEMENT ================

    /**
     * FIXED: Enhanced spawner addition with dependency checking
     */
    public boolean addSpawner(Location location, String data) {
        if (location == null || data == null || data.trim().isEmpty()) {
            logger.warning("§c[MobSpawner] Invalid parameters for spawner creation");
            return false;
        }

        if (!validateSpawnerData(data.trim())) {
            logger.warning("§c[MobSpawner] Invalid spawner data: " + data);
            return false;
        }

        // Check if world is loaded
        if (location.getWorld() == null) {
            logger.warning("§c[MobSpawner] World not loaded for spawner location");
            return false;
        }

        try {
            // Normalize location to block center
            Location normalizedLoc = new Location(location.getWorld(),
                    location.getBlockX() + 0.5,
                    location.getBlockY(),
                    location.getBlockZ() + 0.5);

            String spawnerId = generateSpawnerId(normalizedLoc);

            // Check if spawner exists
            if (spawners.containsKey(spawnerId)) {
                Spawner spawner = spawners.get(spawnerId);
                spawner.parseSpawnerData(data.trim());

                // Update block if needed
                setSpawnerBlockSafe(location.getBlock(), spawner.isVisible());

                logger.info("§6[MobSpawner] §7Updated existing spawner");
                saveSpawners();
                return true;
            }

            // Create new spawner
            Spawner spawner = new Spawner(normalizedLoc, data.trim(), defaultVisibility);
            spawners.put(spawnerId, spawner);
            locationToId.put(normalizedLoc, spawnerId);

            // Set the spawner block safely
            Block block = location.getBlock();
            setSpawnerBlockSafe(block, defaultVisibility);

            // Verify block was set correctly (delayed)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                boolean blockCorrect = defaultVisibility ?
                        block.getType() == Material.SPAWNER :
                        block.getType() == Material.AIR;

                if (!blockCorrect && debug) {
                    logger.warning("§c[MobSpawner] Block state incorrect after spawner creation");
                    setSpawnerBlockSafe(block, defaultVisibility);
                }
            }, 10L);

            saveSpawners();

            logger.info("§a[MobSpawner] §7Created new spawner at " + formatLocation(normalizedLoc) +
                    " with data: " + data);
            return true;

        } catch (Exception e) {
            logger.severe("§c[MobSpawner] Error creating spawner: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean addSpawnerFromTemplate(Location location, String templateName) {
        if (location == null || templateName == null) return false;

        String templateData = templates.get(templateName.toLowerCase().trim());
        if (templateData == null) {
            // Try case-insensitive search
            for (Map.Entry<String, String> entry : templates.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(templateName.trim())) {
                    templateData = entry.getValue();
                    break;
                }
            }
        }

        if (templateData == null) {
            logger.warning("§c[MobSpawner] Template not found: " + templateName);
            return false;
        }

        return addSpawner(location, templateData);
    }

    public boolean removeSpawner(Location location) {
        if (location == null) return false;

        String spawnerId = findSpawnerId(location);
        if (spawnerId == null) return false;

        Spawner spawner = spawners.get(spawnerId);
        if (spawner != null) {
            spawner.removeHologram();
            spawners.remove(spawnerId);

            // Remove from location mapping
            locationToId.entrySet().removeIf(entry -> entry.getValue().equals(spawnerId));

            // Clean up block properly
            Block block = location.getBlock();
            if (block.hasMetadata("isSpawner")) {
                block.removeMetadata("isSpawner", plugin);
            }
            if (block.hasMetadata("spawnerVisible")) {
                block.removeMetadata("spawnerVisible", plugin);
            }

            // Set block to air
            block.setType(Material.AIR, false);

            rebuildSpawnerGroups();
            saveSpawners();

            logger.info("§a[MobSpawner] §7Removed spawner at " + formatLocation(location));
            return true;
        }

        return false;
    }

    private String findSpawnerId(Location location) {
        // Direct lookup
        String spawnerId = locationToId.get(location);
        if (spawnerId != null) return spawnerId;

        // Block-based lookup
        for (Map.Entry<Location, String> entry : locationToId.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld().equals(location.getWorld()) &&
                    loc.getBlockX() == location.getBlockX() &&
                    loc.getBlockY() == location.getBlockY() &&
                    loc.getBlockZ() == location.getBlockZ()) {
                return entry.getValue();
            }
        }

        return null;
    }

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
    }

    // ================ VALIDATION ================

    public boolean validateSpawnerData(String data) {
        if (data == null || data.isEmpty()) return false;

        try {
            String[] entries = data.split(",");
            for (String entry : entries) {
                entry = entry.trim();
                if (!entry.contains(":") || !entry.contains("@") || !entry.contains("#")) {
                    return false;
                }

                try {
                    MobEntry.fromString(entry);
                } catch (Exception e) {
                    logger.warning("§c[MobSpawner] Invalid entry: " + entry + " - " + e.getMessage());
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger.warning("§c[MobSpawner] Data validation error: " + e.getMessage());
            return false;
        }
    }

    // ================ MOB REGISTRATION ================

    public void registerMobSpawned(Location spawnerLocation, LivingEntity entity) {
        if (spawnerLocation == null || entity == null) return;

        String spawnerId = findSpawnerId(spawnerLocation);
        if (spawnerId != null) {
            entity.setMetadata("spawner", new FixedMetadataValue(plugin, spawnerId));

            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                SpawnerProperties props = spawner.getProperties();
                if (props.getSpawnerGroup() != null && !props.getSpawnerGroup().isEmpty()) {
                    entity.setMetadata("spawnerGroup", new FixedMetadataValue(plugin, props.getSpawnerGroup()));
                }
            }

            if (debug) {
                logger.fine("§6[MobSpawner] §7Registered mob " + entity.getUniqueId() + " with spawner " + spawnerId);
            }
        }
    }

    /**
     * FIXED: Enhanced mob death registration with better validation
     */
    public void registerMobDeath(Location spawnerLocation, UUID entityId) {
        if (entityId == null) {
            if (debug) {
                logger.warning("§c[MobSpawner] Cannot register death - null entity ID");
            }
            return;
        }

        if (!spawnersLoaded) {
            if (debug) {
                logger.warning("§c[MobSpawner] Cannot register death - spawners not loaded");
            }
            return;
        }

        try {
            // Find the specific spawner
            String spawnerId = findSpawnerId(spawnerLocation);
            if (spawnerId == null) {
                if (debug) {
                    logger.warning("§c[MobSpawner] Cannot find spawner ID for location: " +
                            formatLocation(spawnerLocation));
                }
                return;
            }

            Spawner targetSpawner = spawners.get(spawnerId);
            if (targetSpawner == null) {
                if (debug) {
                    logger.warning("§c[MobSpawner] Cannot find spawner instance for ID: " + spawnerId);
                }
                return;
            }

            // Register the death with the specific spawner
            targetSpawner.registerMobDeath(entityId);

            if (debug) {
                logger.fine("§6[MobSpawner] §7Successfully registered death with spawner: " + spawnerId);
            }

        } catch (Exception e) {
            logger.warning("§c[MobSpawner] Error registering mob death: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    // ================ CREATION SESSIONS ================

    public void startCreationSession(Player player, Location location) {
        String playerName = player.getName();
        SpawnerCreationSession session = new SpawnerCreationSession(location, playerName);
        creationSessions.put(playerName, session);

        player.sendMessage(ChatColor.GREEN + "Starting spawner creation at " + formatLocation(location));
        player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
    }

    public void endCreationSession(Player player) {
        String playerName = player.getName();
        creationSessions.remove(playerName);
        player.sendMessage(ChatColor.GREEN + "Spawner creation session ended.");
    }

    public SpawnerCreationSession getCreationSession(Player player) {
        return creationSessions.get(player.getName());
    }

    public boolean hasCreationSession(Player player) {
        return creationSessions.containsKey(player.getName());
    }

    public boolean processCreationInput(Player player, String input) {
        SpawnerCreationSession session = getCreationSession(player);
        if (session == null) return false;

        try {
            int step = session.getCurrentStep();

            switch (step) {
                case 0: // Mob type or template
                    if (input.toLowerCase().startsWith("template:")) {
                        String templateName = input.substring(9).trim();
                        if (templates.containsKey(templateName.toLowerCase())) {
                            session.setTemplateName(templateName.toLowerCase());
                            session.setCurrentStep(5); // Skip to completion
                            player.sendMessage(ChatColor.GREEN + "Template '" + templateName + "' selected!");
                            player.sendMessage(ChatColor.YELLOW + "Type 'done' to create the spawner or 'advanced' for more options.");
                            return true;
                        } else {
                            player.sendMessage(ChatColor.RED + "Template not found: " + templateName);
                            return true;
                        }
                    } else if (session.validateMobType(input)) {
                        session.setCurrentMobType(input.toLowerCase());
                        session.setCurrentStep(1);
                        player.sendMessage(ChatColor.GREEN + "Mob type set to: " + input);
                        player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED + "Invalid mob type: " + input);
                        return true;
                    }

                case 1: // Tier
                    try {
                        int tier = Integer.parseInt(input);
                        if (session.validateTier(tier)) {
                            session.setCurrentTier(tier);
                            session.setCurrentStep(2);
                            player.sendMessage(ChatColor.GREEN + "Tier set to: " + tier);
                            player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
                            return true;
                        } else {
                            player.sendMessage(ChatColor.RED + "Tier must be between 1 and 6");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Please enter a valid number");
                        return true;
                    }

                case 2: // Elite
                    boolean elite = input.toLowerCase().equals("true") || input.toLowerCase().equals("yes");
                    session.setCurrentElite(elite);
                    session.setCurrentStep(3);
                    player.sendMessage(ChatColor.GREEN + "Elite status set to: " + elite);
                    player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
                    return true;

                case 3: // Amount
                    try {
                        int amount = Integer.parseInt(input);
                        if (amount >= 1 && amount <= 10) {
                            session.setCurrentAmount(amount);
                            session.setCurrentStep(4);
                            player.sendMessage(ChatColor.GREEN + "Amount set to: " + amount);
                            player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
                            return true;
                        } else {
                            player.sendMessage(ChatColor.RED + "Amount must be between 1 and 10");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Please enter a valid number");
                        return true;
                    }

                case 4: // Next steps
                    if (input.equalsIgnoreCase("add")) {
                        session.addCurrentMobEntry();
                        session.setCurrentStep(0);
                        player.sendMessage(ChatColor.GREEN + "Mob entry added! Configure another:");
                        player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
                        return true;
                    } else if (input.equalsIgnoreCase("done")) {
                        return completeSpawnerCreation(player, session);
                    } else if (input.equalsIgnoreCase("advanced")) {
                        session.addCurrentMobEntry();
                        session.setConfigureAdvanced(true);
                        session.setCurrentStep(6);
                        player.sendMessage(ChatColor.GREEN + "Entering advanced configuration mode:");
                        player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED + "Invalid option. Use 'add', 'done', or 'advanced'");
                        return true;
                    }

                case 5: // Template completion
                    if (input.equalsIgnoreCase("done")) {
                        return completeSpawnerCreation(player, session);
                    } else if (input.equalsIgnoreCase("advanced")) {
                        session.setConfigureAdvanced(true);
                        session.setCurrentStep(6);
                        player.sendMessage(ChatColor.GREEN + "Entering advanced configuration mode:");
                        player.sendMessage(ChatColor.YELLOW + session.getHelpForCurrentStep());
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED + "Use 'done' to create or 'advanced' for more options");
                        return true;
                    }

                case 6: // Advanced configuration
                    if (input.equalsIgnoreCase("done")) {
                        return completeSpawnerCreation(player, session);
                    } else {
                        String result = session.processAdvancedCommand(input);
                        player.sendMessage(ChatColor.GREEN + result);
                        return true;
                    }

                default:
                    player.sendMessage(ChatColor.RED + "Invalid session state. Please start over.");
                    endCreationSession(player);
                    return true;
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error processing input: " + e.getMessage());
            return true;
        }
    }

    private boolean completeSpawnerCreation(Player player, SpawnerCreationSession session) {
        try {
            if (!session.hasMobEntries()) {
                player.sendMessage(ChatColor.RED + "No mobs configured for spawner!");
                return true;
            }

            String spawnerData = session.buildSpawnerData();
            Location location = session.getLocation();

            if (addSpawner(location, spawnerData)) {
                // Apply advanced properties if configured
                if (session.isConfigureAdvanced()) {
                    String spawnerId = findSpawnerId(location);
                    if (spawnerId != null) {
                        Spawner spawner = spawners.get(spawnerId);
                        if (spawner != null) {
                            SpawnerProperties props = spawner.getProperties();
                            session.applyToProperties(props);
                            spawner.setProperties(props);
                            spawner.setDisplayMode(session.getDisplayMode());

                            // Update block visibility if changed
                            setSpawnerBlockSafe(location.getBlock(), spawner.isVisible());
                        }
                    }
                }

                player.sendMessage(ChatColor.GREEN + "Spawner created successfully!");
                player.sendMessage(ChatColor.GRAY + "Summary:");
                player.sendMessage(ChatColor.WHITE + session.getSummary());

                endCreationSession(player);
                saveSpawners();
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Failed to create spawner. Check console for errors.");
                return true;
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error creating spawner: " + e.getMessage());
            logger.warning("§c[MobSpawner] Creation error: " + e.getMessage());
            return true;
        }
    }

    // ================ UTILITY METHODS ================

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

    // ================ STATUS AND HEALTH CHECKS ================

    /**
     * Get system status including initialization state
     */
    public String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("MobSpawner System Status:\n");
        status.append("Initialized: ").append(initialized).append("\n");
        status.append("Spawners Loaded: ").append(spawnersLoaded).append("\n");
        status.append("Dependencies Ready: ").append(dependenciesReady).append("\n");
        status.append("Load Attempts: ").append(loadAttempts).append("/").append(MAX_LOAD_ATTEMPTS).append("\n");
        status.append("Enabled: ").append(enabled).append("\n");
        status.append("Active Spawners: ").append(spawners.size()).append("\n");
        status.append("Active Tasks: Main=").append(mainTask != null && !mainTask.isCancelled())
                .append(", Save=").append(saveTask != null && !saveTask.isCancelled()).append("\n");
        return status.toString();
    }

    /**
     * Check if system is ready for normal operation
     */
    public boolean isSystemReady() {
        return initialized && spawnersLoaded && dependenciesReady;
    }

    // ================ DELEGATION METHODS ================

    public boolean isDebugMode() { return debug; }
    public void setDebugMode(boolean debug) { this.debug = debug; }
    public void setSpawnersEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean areSpawnersEnabled() { return enabled; }
    public boolean getDefaultVisibility() { return defaultVisibility; }
    public Map<String, String> getAllTemplates() { return new HashMap<>(templates); }
    public Set<String> getAllSpawnerGroups() { return new HashSet<>(spawnerGroups.keySet()); }
    public int getMaxMobsPerSpawner() { return maxMobsPerSpawner; }
    public double getPlayerDetectionRange() { return playerDetectionRange; }

    public Map<Location, String> getAllSpawners() {
        Map<Location, String> result = new HashMap<>();
        for (Map.Entry<Location, String> entry : locationToId.entrySet()) {
            String spawnerId = entry.getValue();
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                result.put(entry.getKey(), spawner.getDataString());
            }
        }
        return result;
    }

    public int getActiveMobCount(Location location) {
        String spawnerId = findSpawnerId(location);
        if (spawnerId != null) {
            Spawner spawner = spawners.get(spawnerId);
            return spawner != null ? spawner.getActiveMobCount() : 0;
        }
        return 0;
    }

    public boolean isSpawnerVisible(Location location) {
        String spawnerId = findSpawnerId(location);
        if (spawnerId != null) {
            Spawner spawner = spawners.get(spawnerId);
            return spawner != null && spawner.isVisible();
        }
        return false;
    }

    public boolean setSpawnerVisibility(Location location, boolean visible) {
        String spawnerId = findSpawnerId(location);
        if (spawnerId != null) {
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                spawner.setVisible(visible);
                setSpawnerBlockSafe(location.getBlock(), visible);
                saveSpawners();
                return true;
            }
        }
        return false;
    }

    public void createOrUpdateSpawnerHologram(Location location) {
        String spawnerId = findSpawnerId(location);
        if (spawnerId != null) {
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                spawner.updateHologram();
            }
        }
    }

    public void removeSpawnerHologram(Location location) {
        String spawnerId = findSpawnerId(location);
        if (spawnerId != null) {
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                spawner.removeHologram();
            }
        }
    }

    public SpawnerMetrics getSpawnerMetrics(Location location) {
        String spawnerId = findSpawnerId(location);
        if (spawnerId != null) {
            Spawner spawner = spawners.get(spawnerId);
            return spawner != null ? spawner.getMetrics() : null;
        }
        return null;
    }

    public boolean resetSpawner(Location location) {
        String spawnerId = findSpawnerId(location);
        if (spawnerId != null) {
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                spawner.reset();
                return true;
            }
        }
        return false;
    }

    public void sendSpawnerInfo(Player player, Location location) {
        if (player == null || location == null) return;
        String spawnerId = findSpawnerId(location);
        if (spawnerId != null) {
            Spawner spawner = spawners.get(spawnerId);
            if (spawner != null) {
                spawner.sendInfoTo(player);
            } else {
                player.sendMessage(ChatColor.RED + "No spawner found at this location.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at this location.");
        }
    }

    public void resetAllSpawners() {
        for (Spawner spawner : spawners.values()) {
            spawner.reset();
        }
        logger.info("§a[MobSpawner] §7All spawners reset");
    }

    public List<Location> findSpawnersInRadius(Location center, double radius) {
        List<Location> result = new ArrayList<>();
        if (center == null) return result;

        double radiusSquared = radius * radius;
        for (Location loc : locationToId.keySet()) {
            if (loc.getWorld().equals(center.getWorld()) &&
                    loc.distanceSquared(center) <= radiusSquared) {
                result.add(loc);
            }
        }
        return result;
    }

    public Location findNearestSpawner(Location location, double maxDistance) {
        if (location == null) return null;

        double closestDistSq = maxDistance * maxDistance;
        Location closest = null;

        for (Location spawnerLoc : locationToId.keySet()) {
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
     * Get spawner debug info for debugging commands
     */
    public String getSpawnerDebugInfo(Location location) {
        String spawnerId = findSpawnerId(location);
        if (spawnerId == null) {
            return "No spawner found at " + formatLocation(location);
        }

        Spawner spawnerInstance = spawners.get(spawnerId);
        if (spawnerInstance == null) {
            return "Spawner instance not found for ID: " + spawnerId;
        }

        return spawnerInstance.getDebugInfo();
    }

    // ================ CLEANUP METHODS ================

    private void cleanupExpiredSessions() {
        List<String> expiredSessions = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, SpawnerCreationSession> entry : creationSessions.entrySet()) {
            if (currentTime - entry.getValue().getStartTime() > 300000) { // 5 minutes
                expiredSessions.add(entry.getKey());
            }
        }

        for (String playerName : expiredSessions) {
            creationSessions.remove(playerName);
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Your spawner creation session has expired.");
            }
        }
    }

    // ================ SHUTDOWN ================

    public void shutdown() {
        try {
            logger.info("§6[MobSpawner] §7Shutting down...");

            // Cancel all tasks
            if (mainTask != null) mainTask.cancel();
            if (saveTask != null) saveTask.cancel();
            if (delayedLoadTask != null) delayedLoadTask.cancel();

            // Save spawners if loaded
            if (spawnersLoaded) {
                saveSpawners();
            }

            // Clean up holograms
            for (Spawner spawner : spawners.values()) {
                if (spawner.isVisible()) {
                    spawner.removeHologram();
                }
            }

            // Clear collections
            spawners.clear();
            locationToId.clear();
            spawnerGroups.clear();
            creationSessions.clear();

            logger.info("§a[MobSpawner] §7Shutdown completed");
        } catch (Exception e) {
            logger.severe("§c[MobSpawner] Shutdown error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}