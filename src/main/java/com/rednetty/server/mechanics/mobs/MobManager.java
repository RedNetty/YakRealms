package com.rednetty.server.mechanics.mobs;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.mobs.core.CustomMob;
import com.rednetty.server.mechanics.mobs.core.EliteMob;
import com.rednetty.server.mechanics.mobs.core.MobType;
import com.rednetty.server.mechanics.mobs.core.WorldBoss;
import com.rednetty.server.mechanics.mobs.spawners.MobSpawner;
import com.rednetty.server.mechanics.mobs.spawners.SpawnerMetrics;
import com.rednetty.server.mechanics.mobs.utils.MobUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * FIXED: MobManager with comprehensive cleanup system for proper shutdown/startup handling
 * - Enhanced entity cleanup on shutdown - removes ALL custom mobs from worlds
 * - Startup cleanup - removes orphaned entities before starting
 * - Better coordination with CritManager for cleanup
 * - Systematic world scanning for custom entities
 * - Robust entity removal that ensures entities are deleted from world
 * - All original functionality preserved
 */
public class MobManager implements Listener {

    // ================ CONSTANTS ================
    private static final long MIN_RESPAWN_DELAY = 180000L; // 3 minutes
    private static final long MAX_RESPAWN_DELAY = 900000L; // 15 minutes
    private static final double MAX_WANDERING_DISTANCE = 25.0;
    private static final long POSITION_CHECK_INTERVAL = 100L; // 5 seconds
    private static final long NAME_VISIBILITY_TIMEOUT = 6500L; // 6.5 seconds

    // ================ CLEANUP CONSTANTS ================
    private static final String[] CUSTOM_MOB_METADATA_KEYS = {
            "type", "tier", "customTier", "elite", "customName", "dropTier", "dropElite",
            "worldboss", "spawner", "spawnerGroup", "LightningMultiplier", "LightningMob",
            "criticalState", "eliteOnly"
    };

    // ================ SINGLETON ================
    private static volatile MobManager instance;

    // ================ CORE MAPS WITH THREAD SAFETY ================
    private final Map<UUID, CustomMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageContributions = new ConcurrentHashMap<>();
    private final Map<Entity, Player> mobTargets = new ConcurrentHashMap<>();
    private final Set<UUID> processedEntities = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Location> mobSpawnerLocations = new ConcurrentHashMap<>();
    private final Map<String, String> entityToSpawner = new ConcurrentHashMap<>();

    // ================ THREAD SAFETY ================
    private final ReentrantReadWriteLock mobLock = new ReentrantReadWriteLock();

    // ================ RESPAWN TRACKING ================
    private final Map<String, Long> respawnTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> mobTypeLastDeath = new ConcurrentHashMap<>();

    // ================ ENHANCED VALIDATION WITH ELITE SUPPORT ================
    private final Map<String, EntityType> mobTypeMapping = new ConcurrentHashMap<>();
    private final Set<String> validMobTypes = new HashSet<>();
    private final Map<String, Integer> failureCounter = new ConcurrentHashMap<>();
    private final Set<String> eliteOnlyTypes = new HashSet<>();

    // ================ COMPONENTS ================
    private final MobSpawner spawner;
    private final Logger logger;
    private final YakRealms plugin;
    private WorldBoss activeWorldBoss;

    // ================ CONFIGURATION ================
    private volatile boolean spawnersEnabled = true;
    private volatile boolean debug = false;
    private volatile int maxMobsPerSpawner = 10;
    private volatile double playerDetectionRange = 40.0;
    private volatile double mobRespawnDistanceCheck = 25.0;

    // ================ CLEANUP STATE ================
    private volatile boolean isShuttingDown = false;
    private volatile boolean cleanupCompleted = false;

    // ================ TASKS ================
    private BukkitTask mainTask;
    private BukkitTask cleanupTask;

    /**
     * Constructor with enhanced initialization
     */
    private MobManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.spawner = MobSpawner.getInstance();
        this.debug = plugin.isDebugMode();

        initializeMobTypeMapping();
        loadConfiguration();
    }

    public static MobManager getInstance() {
        if (instance == null) {
            synchronized (MobManager.class) {
                if (instance == null) {
                    instance = new MobManager();
                }
            }
        }
        return instance;
    }

    /**
     * FIXED: Initialize mob type to EntityType mapping with proper elite support
     */
    private void initializeMobTypeMapping() {
        try {
            logger.info("§6[MobManager] §7Initializing comprehensive mob type mapping with elite support...");

            // Core mob types with proper mapping
            mobTypeMapping.put("skeleton", EntityType.SKELETON);
            mobTypeMapping.put("witherskeleton", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("wither_skeleton", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("zombie", EntityType.ZOMBIE);
            mobTypeMapping.put("spider", EntityType.SPIDER);
            mobTypeMapping.put("cavespider", EntityType.CAVE_SPIDER);
            mobTypeMapping.put("cave_spider", EntityType.CAVE_SPIDER);
            mobTypeMapping.put("magmacube", EntityType.MAGMA_CUBE);
            mobTypeMapping.put("magma_cube", EntityType.MAGMA_CUBE);
            mobTypeMapping.put("zombifiedpiglin", EntityType.ZOMBIFIED_PIGLIN);
            mobTypeMapping.put("pigzombie", EntityType.ZOMBIFIED_PIGLIN);
            mobTypeMapping.put("enderman", EntityType.ENDERMAN);
            mobTypeMapping.put("creeper", EntityType.CREEPER);
            mobTypeMapping.put("blaze", EntityType.BLAZE);
            mobTypeMapping.put("ghast", EntityType.GHAST);
            mobTypeMapping.put("slime", EntityType.SLIME);
            mobTypeMapping.put("silverfish", EntityType.SILVERFISH);
            mobTypeMapping.put("witch", EntityType.WITCH);
            mobTypeMapping.put("vindicator", EntityType.VINDICATOR);
            mobTypeMapping.put("evoker", EntityType.EVOKER);
            mobTypeMapping.put("pillager", EntityType.PILLAGER);
            mobTypeMapping.put("ravager", EntityType.RAVAGER);
            mobTypeMapping.put("vex", EntityType.VEX);
            mobTypeMapping.put("shulker", EntityType.SHULKER);
            mobTypeMapping.put("guardian", EntityType.GUARDIAN);
            mobTypeMapping.put("elderguardian", EntityType.ELDER_GUARDIAN);
            mobTypeMapping.put("warden", EntityType.WARDEN);
            mobTypeMapping.put("golem", EntityType.IRON_GOLEM);
            mobTypeMapping.put("irongolem", EntityType.IRON_GOLEM);

            // Special/Custom types (fallback to appropriate entity types)
            mobTypeMapping.put("imp", EntityType.ZOMBIE);
            mobTypeMapping.put("spectralguard", EntityType.ZOMBIFIED_PIGLIN);
            mobTypeMapping.put("frozenboss", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("elderguardian", EntityType.ELDER_GUARDIAN);
            mobTypeMapping.put("shulker", EntityType.SHULKER);
            mobTypeMapping.put("spectralguard", EntityType.SKELETON);
            mobTypeMapping.put("frozenboss", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("frozenelite", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("frozengolem", EntityType.IRON_GOLEM);
            mobTypeMapping.put("frostwing", EntityType.PHANTOM);
            mobTypeMapping.put("chronos", EntityType.WITHER);
            mobTypeMapping.put("frostking", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("weakSkeleton", EntityType.SKELETON);
            mobTypeMapping.put("weakskeleton", EntityType.SKELETON);
            mobTypeMapping.put("bossSkeleton", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("bossSkeletonDungeon", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("daemon", EntityType.ZOMBIFIED_PIGLIN);
            mobTypeMapping.put("turkey", EntityType.CHICKEN);
            mobTypeMapping.put("giant", EntityType.GIANT);
            mobTypeMapping.put("prisoner", EntityType.ZOMBIE);
            mobTypeMapping.put("skellyDSkeletonGuardian", EntityType.SKELETON);
            mobTypeMapping.put("spectralKnight", EntityType.ZOMBIFIED_PIGLIN);

            // CRITICAL: T5 Elite mappings with proper entity types
            mobTypeMapping.put("meridian", EntityType.WARDEN);
            mobTypeMapping.put("pyrion", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("rimeclaw", EntityType.STRAY);
            mobTypeMapping.put("thalassa", EntityType.DROWNED);
            mobTypeMapping.put("nethys", EntityType.WITHER_SKELETON);

            // T1-T4 Elite mappings
            mobTypeMapping.put("malachar", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("xerathen", EntityType.ZOMBIE);
            mobTypeMapping.put("veridiana", EntityType.HUSK);
            mobTypeMapping.put("thorgrim", EntityType.ZOMBIFIED_PIGLIN);
            mobTypeMapping.put("lysander", EntityType.VINDICATOR);
            mobTypeMapping.put("morgana", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("vex_elite", EntityType.ZOMBIE);
            mobTypeMapping.put("cornelius", EntityType.ZOMBIE_VILLAGER);
            mobTypeMapping.put("valdris", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("seraphina", EntityType.ZOMBIE);
            mobTypeMapping.put("arachnia", EntityType.SPIDER);
            mobTypeMapping.put("karnath", EntityType.ZOMBIE);
            mobTypeMapping.put("zephyr", EntityType.WITHER_SKELETON);

            // CRITICAL: Mark elite-only types
            eliteOnlyTypes.add("meridian");
            eliteOnlyTypes.add("pyrion");
            eliteOnlyTypes.add("rimeclaw");
            eliteOnlyTypes.add("thalassa");
            eliteOnlyTypes.add("nethys");
            eliteOnlyTypes.add("malachar");
            eliteOnlyTypes.add("xerathen");
            eliteOnlyTypes.add("veridiana");
            eliteOnlyTypes.add("thorgrim");
            eliteOnlyTypes.add("lysander");
            eliteOnlyTypes.add("morgana");
            eliteOnlyTypes.add("vex_elite");
            eliteOnlyTypes.add("cornelius");
            eliteOnlyTypes.add("valdris");
            eliteOnlyTypes.add("seraphina");
            eliteOnlyTypes.add("arachnia");
            eliteOnlyTypes.add("karnath");
            eliteOnlyTypes.add("zephyr");
            eliteOnlyTypes.add("frozenboss");
            eliteOnlyTypes.add("frozenelite");
            eliteOnlyTypes.add("frozengolem");
            eliteOnlyTypes.add("frostwing");
            eliteOnlyTypes.add("chronos");
            eliteOnlyTypes.add("frostking");
            eliteOnlyTypes.add("bossSkeleton");
            eliteOnlyTypes.add("bossSkeletonDungeon");
            eliteOnlyTypes.add("weakskeleton");
            eliteOnlyTypes.add("skellyDSkeletonGuardian");
            eliteOnlyTypes.add("spectralguard");
            eliteOnlyTypes.add("spectralKnight");

            // Populate valid types set
            validMobTypes.addAll(mobTypeMapping.keySet());

            logger.info("§a[MobManager] §7Initialized " + mobTypeMapping.size() + " mob type mappings");
            logger.info("§a[MobManager] §7Registered " + eliteOnlyTypes.size() + " elite-only types");

            if (debug) {
                logger.info("§6[MobManager] §7Elite-only types: " + String.join(", ", eliteOnlyTypes));
            }

        } catch (Exception e) {
            logger.severe("§c[MobManager] Failed to initialize mob type mapping: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadConfiguration() {
        maxMobsPerSpawner = plugin.getConfig().getInt("mechanics.mobs.max-mobs-per-spawner", 10);
        playerDetectionRange = plugin.getConfig().getDouble("mechanics.mobs.player-detection-range", 40.0);
        mobRespawnDistanceCheck = plugin.getConfig().getDouble("mechanics.mobs.mob-respawn-distance-check", 25.0);

        // Validate ranges
        maxMobsPerSpawner = Math.max(1, Math.min(50, maxMobsPerSpawner));
        playerDetectionRange = Math.max(10.0, Math.min(100.0, playerDetectionRange));
        mobRespawnDistanceCheck = Math.max(5.0, Math.min(50.0, mobRespawnDistanceCheck));
    }

    // ================ ENHANCED INITIALIZATION WITH CLEANUP ================

    /**
     * FIXED: Enhanced initialization with startup cleanup to remove orphaned entities
     */
    public void initialize() {
        try {
            logger.info("§6[MobManager] §7Starting initialization with cleanup...");

            // CRITICAL: Clean up any orphaned custom mobs from previous sessions
            performStartupCleanup();

            // Initialize CritManager first
            CritManager.initialize(plugin);

            spawner.initialize();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startTasks();

            logger.info(String.format("§a[MobManager] §7Initialized successfully with §e%d §7spawners",
                    spawner.getAllSpawners().size()));

            if (debug) {
                logger.info("§6[MobManager] §7Debug mode enabled with enhanced tracking");
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================ STARTUP CLEANUP SYSTEM ================

    /**
     * FIXED: Comprehensive startup cleanup to remove orphaned custom mobs
     */
    private void performStartupCleanup() {
        try {
            logger.info("§6[MobManager] §7Performing startup cleanup of orphaned custom mobs...");

            int totalFound = 0;
            int totalRemoved = 0;

            // Clean up all loaded worlds
            for (World world : Bukkit.getWorlds()) {
                try {
                    CleanupResult result = cleanupWorldCustomMobs(world, true);
                    totalFound += result.entitiesFound;
                    totalRemoved += result.entitiesRemoved;

                    if (result.entitiesFound > 0) {
                        logger.info("§6[MobManager] §7World " + world.getName() + ": found " +
                                result.entitiesFound + ", removed " + result.entitiesRemoved);
                    }
                } catch (Exception e) {
                    logger.warning("§c[MobManager] Error cleaning world " + world.getName() + ": " + e.getMessage());
                }
            }

            if (totalFound > 0) {
                logger.info("§a[MobManager] §7Startup cleanup complete: removed " + totalRemoved +
                        "/" + totalFound + " orphaned custom mobs");
            } else {
                logger.info("§a[MobManager] §7Startup cleanup complete: no orphaned mobs found");
            }

            // Give server a moment to process removals
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            logger.severe("§c[MobManager] Startup cleanup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================ COMPREHENSIVE CLEANUP SYSTEM ================

    /**
     * Result of a cleanup operation
     */
    private static class CleanupResult {
        final int entitiesFound;
        final int entitiesRemoved;

        CleanupResult(int found, int removed) {
            this.entitiesFound = found;
            this.entitiesRemoved = removed;
        }
    }

    /**
     * FIXED: Clean up custom mobs in a specific world
     */
    private CleanupResult cleanupWorldCustomMobs(World world, boolean isStartupCleanup) {
        if (world == null) {
            return new CleanupResult(0, 0);
        }

        try {
            int found = 0;
            int removed = 0;

            // Get all living entities in the world
            Collection<LivingEntity> entities = world.getLivingEntities();

            for (LivingEntity entity : entities) {
                try {
                    if (entity instanceof Player) {
                        continue; // Never remove players
                    }

                    if (isCustomMobEntity(entity)) {
                        found++;

                        // During startup cleanup, remove all custom mobs
                        // During shutdown cleanup, remove tracked mobs
                        if (isStartupCleanup || isTrackedCustomMob(entity)) {
                            if (removeEntitySafely(entity, isStartupCleanup ? "startup-cleanup" : "shutdown-cleanup")) {
                                removed++;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warning("§c[MobManager] Error processing entity " + entity.getUniqueId() + ": " + e.getMessage());
                }
            }

            return new CleanupResult(found, removed);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error cleaning world " + world.getName() + ": " + e.getMessage());
            return new CleanupResult(0, 0);
        }
    }

    /**
     * FIXED: Check if an entity is a custom mob by examining metadata
     */
    private boolean isCustomMobEntity(LivingEntity entity) {
        if (entity == null) return false;

        try {
            // Check for any custom mob metadata
            for (String key : CUSTOM_MOB_METADATA_KEYS) {
                if (entity.hasMetadata(key)) {
                    return true;
                }
            }

            // Check for custom names that indicate custom mobs
            if (entity.getCustomName() != null) {
                String name = entity.getCustomName();
                if (name.contains("§") || // Has color codes
                        name.contains("T1") || name.contains("T2") || name.contains("T3") ||
                        name.contains("T4") || name.contains("T5") || name.contains("T6") ||
                        name.contains("Elite") || name.contains("Boss")) {
                    return true;
                }
            }

            // Check for equipment that indicates custom mobs
            if (entity.getEquipment() != null) {
                if (entity.getEquipment().getItemInMainHand() != null &&
                        entity.getEquipment().getItemInMainHand().getType() != Material.AIR &&
                        entity.getEquipment().getItemInMainHandDropChance() == 0) {
                    return true; // Custom equipment with no drop chance
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if entity is tracked by our system
     */
    private boolean isTrackedCustomMob(LivingEntity entity) {
        if (entity == null) return false;

        // Check if it's in our active mobs list
        return activeMobs.containsKey(entity.getUniqueId());
    }

    /**
     * FIXED: Safely remove an entity with comprehensive cleanup
     */
    private boolean removeEntitySafely(LivingEntity entity, String reason) {
        if (entity == null) return false;

        try {
            UUID entityId = entity.getUniqueId();

            if (debug) {
                logger.info("§6[MobManager] §7Removing entity " + entityId.toString().substring(0, 8) +
                        " (" + entity.getType() + ") - " + reason);
            }

            // 1. Remove from CritManager first
            if (CritManager.getInstance() != null) {
                CritManager.getInstance().removeCrit(entityId);
            }

            // 2. Remove from our tracking systems
            removeFromAllTrackingSystems(entityId);

            // 3. Clear all custom metadata
            clearEntityMetadata(entity);

            // 4. Remove effects and reset entity state
            clearEntityEffects(entity);

            // 5. Actually remove the entity from the world
            entity.remove();

            // 6. Verify removal (delayed check)
            if (!isShuttingDown) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        if (entity.isValid() && !entity.isDead()) {
                            if (debug) {
                                logger.warning("§c[MobManager] Entity " + entityId.toString().substring(0, 8) +
                                        " survived removal, force removing...");
                            }
                            entity.setHealth(0);
                            entity.remove();
                        }
                    } catch (Exception e) {
                        // Silent fail for verification
                    }
                }, 5L);
            }

            return true;

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error removing entity: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove entity from all tracking systems
     */
    private void removeFromAllTrackingSystems(UUID entityId) {
        try {
            // Remove from active mobs
            mobLock.writeLock().lock();
            try {
                activeMobs.remove(entityId);
            } finally {
                mobLock.writeLock().unlock();
            }

            // Remove from damage tracking
            damageContributions.remove(entityId);

            // Remove from spawner location tracking
            mobSpawnerLocations.remove(entityId);
            entityToSpawner.remove(entityId.toString());

            // Remove from processed entities
            processedEntities.remove(entityId);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error removing from tracking systems: " + e.getMessage());
        }
    }

    /**
     * Clear all custom metadata from entity
     */
    private void clearEntityMetadata(LivingEntity entity) {
        try {
            for (String key : CUSTOM_MOB_METADATA_KEYS) {
                if (entity.hasMetadata(key)) {
                    entity.removeMetadata(key, plugin);
                }
            }
        } catch (Exception e) {
            // Silent fail for metadata cleanup
        }
    }

    /**
     * Clear all effects from entity
     */
    private void clearEntityEffects(LivingEntity entity) {
        try {
            // Remove all potion effects
            for (PotionEffect effect : entity.getActivePotionEffects()) {
                entity.removePotionEffect(effect.getType());
            }

            // Reset glowing
            entity.setGlowing(false);

            // Clear equipment drop chances
            if (entity.getEquipment() != null) {
                entity.getEquipment().setItemInMainHandDropChance(0.85f);
                entity.getEquipment().setHelmetDropChance(0.85f);
                entity.getEquipment().setChestplateDropChance(0.85f);
                entity.getEquipment().setLeggingsDropChance(0.85f);
                entity.getEquipment().setBootsDropChance(0.85f);
            }

        } catch (Exception e) {
            // Silent fail for effect cleanup
        }
    }

    // ================ ENHANCED SHUTDOWN SYSTEM ================

    /**
     * FIXED: Enhanced shutdown with comprehensive cleanup
     */
    public void shutdown() {
        try {
            logger.info("§6[MobManager] §7Starting enhanced shutdown with comprehensive cleanup...");
            isShuttingDown = true;

            // 1. Cancel all tasks first
            if (mainTask != null) mainTask.cancel();
            if (cleanupTask != null) cleanupTask.cancel();

            // 2. Comprehensive entity cleanup
            performShutdownCleanup();

            // 3. Save spawner data
            try {
                spawner.saveSpawners();
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error saving spawners during shutdown: " + e.getMessage());
            }

            // 4. Shutdown spawner system
            try {
                spawner.shutdown();
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error shutting down spawner: " + e.getMessage());
            }

            // 5. Clean up CritManager
            try {
                if (CritManager.getInstance() != null) {
                    CritManager.getInstance().cleanup();
                }
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error cleaning up CritManager: " + e.getMessage());
            }

            // 6. Clear all collections
            clearAllCollections();

            // 7. Reset state
            activeWorldBoss = null;
            cleanupCompleted = true;

            logger.info("§a[MobManager] §7Enhanced shutdown completed successfully");

        } catch (Exception e) {
            logger.severe("§c[MobManager] Enhanced shutdown error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * FIXED: Comprehensive shutdown cleanup
     */
    private void performShutdownCleanup() {
        try {
            logger.info("§6[MobManager] §7Performing comprehensive shutdown cleanup...");

            int totalFound = 0;
            int totalRemoved = 0;

            // First, try to gracefully remove all tracked mobs
            mobLock.writeLock().lock();
            try {
                List<CustomMob> mobsToRemove = new ArrayList<>(activeMobs.values());
                for (CustomMob mob : mobsToRemove) {
                    try {
                        if (mob != null && mob.isValid()) {
                            mob.remove();
                            totalRemoved++;
                        }
                    } catch (Exception e) {
                        logger.warning("§c[MobManager] Error removing tracked mob: " + e.getMessage());
                    }
                }
                totalFound += mobsToRemove.size();
            } finally {
                mobLock.writeLock().unlock();
            }

            // Give a moment for graceful removal
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Then do comprehensive world cleanup for any remaining custom mobs
            for (World world : Bukkit.getWorlds()) {
                try {
                    CleanupResult result = cleanupWorldCustomMobs(world, false);
                    totalFound += result.entitiesFound;
                    totalRemoved += result.entitiesRemoved;

                    if (result.entitiesFound > 0) {
                        logger.info("§6[MobManager] §7World " + world.getName() +
                                ": found " + result.entitiesFound + " additional entities, removed " + result.entitiesRemoved);
                    }
                } catch (Exception e) {
                    logger.warning("§c[MobManager] Error cleaning world " + world.getName() +
                            " during shutdown: " + e.getMessage());
                }
            }

            logger.info("§a[MobManager] §7Shutdown cleanup complete: removed " + totalRemoved +
                    "/" + totalFound + " custom mobs");

        } catch (Exception e) {
            logger.severe("§c[MobManager] Shutdown cleanup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================ ENHANCED TASKS ================

    /**
     * FIXED: Start essential tasks with proper thread safety
     * ALL entity operations must happen on the main thread
     */
    private void startTasks() {
        logger.info("§6[MobManager] §7Starting essential tasks...");

        // Main processing task - handles all mob logic synchronously on MAIN THREAD
        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                // This ALWAYS runs on the main thread
                try {
                    if (!isShuttingDown) {
                        updateActiveMobs();
                        checkMobPositions();
                        validateEntityTracking();
                    }
                } catch (Exception e) {
                    if (!isShuttingDown) {
                        logger.warning("§c[MobManager] Main task error: " + e.getMessage());
                        if (debug) e.printStackTrace();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second - SYNC task

        // Cleanup task - also SYNC for entity operations
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                // This ALWAYS runs on the main thread
                try {
                    if (!isShuttingDown) {
                        performCleanup();
                    }
                } catch (Exception e) {
                    if (!isShuttingDown) {
                        logger.warning("§c[MobManager] Cleanup task error: " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every minute - SYNC task

        logger.info("§a[MobManager] §7Essential tasks started successfully");
    }

    // ================ ENHANCED SPAWNER MOB SPAWNING ================

    /**
     * FIXED: Enhanced spawner mob spawning with MAIN THREAD SAFETY and elite support
     * This method MUST always be called from the main thread
     */
    public LivingEntity spawnMobFromSpawner(Location location, String type, int tier, boolean elite) {
        // CRITICAL: Verify we're on the main thread
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: spawnMobFromSpawner called from async thread! This will cause errors!");

            // Schedule on main thread and return null immediately
            Bukkit.getScheduler().runTask(plugin, () -> {
                spawnMobFromSpawner(location, type, tier, elite);
            });
            return null;
        }

        // Don't spawn during shutdown
        if (isShuttingDown) {
            return null;
        }

        // Enhanced validation with detailed error reporting
        if (!isValidSpawnRequest(location, type, tier, elite)) {
            if (debug) {
                logger.warning("§c[MobManager] Invalid spawn request: " + type + " T" + tier +
                        (elite ? "+" : "") + " at " + formatLocation(location));
            }
            return null;
        }

        // Normalize and validate mob type
        String normalizedType = normalizeMobType(type);
        if (normalizedType == null) {
            incrementFailureCount(type);
            logger.warning("§c[MobManager] Failed to normalize mob type: " + type);
            return null;
        }

        try {
            // CRITICAL: Ensure chunk is loaded BEFORE any spawn attempt
            if (!ensureChunkLoadedForSpawning(location)) {
                incrementFailureCount(normalizedType);
                logger.warning("§c[MobManager] Chunk loading failed for spawn at " + formatLocation(location));
                return null;
            }

            // Get safe spawn location with enhanced validation
            Location spawnLoc = getSafeSpawnLocationEnhanced(location);
            if (spawnLoc == null) {
                incrementFailureCount(normalizedType);
                logger.warning("§c[MobManager] No safe spawn location found near " + formatLocation(location));
                return null;
            }

            // Create entity with enhanced error handling - MAIN THREAD ONLY
            LivingEntity entity = createEntityWithValidation(spawnLoc, normalizedType, tier, elite);

            if (entity != null) {
                // Register with tracking systems
                registerSpawnedMob(entity, location);

                if (debug) {
                    logger.info("§a[MobManager] §7Successfully spawned " + normalizedType + " T" + tier +
                            (elite ? "+" : "") + " at " + formatLocation(spawnLoc) +
                            " with ID: " + entity.getUniqueId().toString().substring(0, 8));
                }

                // Reset failure counter on success
                failureCounter.put(normalizedType, 0);
                return entity;
            } else {
                incrementFailureCount(normalizedType);
                logger.warning("§c[MobManager] Failed to create entity for " + normalizedType);
            }
        } catch (Exception e) {
            incrementFailureCount(normalizedType);
            logger.severe("§c[MobManager] Critical error spawning mob " + normalizedType + ": " + e.getMessage());
            if (debug) e.printStackTrace();
        }

        return null;
    }

    /**
     * FIXED: Enhanced chunk loading with proper validation
     */
    private boolean ensureChunkLoadedForSpawning(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        try {
            World world = location.getWorld();
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;

            // Check if already loaded
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                return true;
            }

            // Force load the chunk for spawning
            boolean loaded = world.loadChunk(chunkX, chunkZ, true);
            if (!loaded) {
                logger.warning("§c[MobManager] Failed to load chunk at " + chunkX + "," + chunkZ);
                return false;
            }

            // Verify chunk is actually loaded
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                logger.warning("§c[MobManager] Chunk loading verification failed at " + chunkX + "," + chunkZ);
                return false;
            }

            // Additional validation - ensure chunk is fully generated
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            if (chunk == null) {
                logger.warning("§c[MobManager] Chunk object is null after loading");
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warning("§c[MobManager] Chunk loading error: " + e.getMessage());
            return false;
        }
    }

    /**
     * FIXED: Enhanced mob type normalization with elite support
     */
    private String normalizeMobType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }

        String normalized = type.toLowerCase().trim();

        // Handle common variations
        switch (normalized) {
            case "wither_skeleton":
            case "witherskeleton":
                return "witherskeleton";
            case "cave_spider":
            case "cavespider":
                return "cavespider";
            case "magma_cube":
            case "magmacube":
                return "magmacube";
            case "zombified_piglin":
            case "zombifiedpiglin":
            case "pigzombie":
                return "zombifiedpiglin";
            case "elder_guardian":
            case "elderguardian":
                return "elderguardian";
            case "iron_golem":
            case "irongolem":
            case "golem":
                return "irongolem";
        }

        // Check if type exists in our mapping
        if (mobTypeMapping.containsKey(normalized)) {
            return normalized;
        }

        // CRITICAL: Check MobType system for elite types
        if (MobType.isValidType(normalized)) {
            return normalized;
        }

        // Try to find close matches
        for (String validType : validMobTypes) {
            if (validType.contains(normalized) || normalized.contains(validType)) {
                if (debug) {
                    logger.info("§6[MobManager] §7Mapped " + type + " to " + validType);
                }
                return validType;
            }
        }

        logger.warning("§c[MobManager] Unknown mob type: " + type + ". Available types: " +
                String.join(", ", validMobTypes));
        return null;
    }

    /**
     * FIXED: Enhanced entity creation with MAIN THREAD SAFETY and elite support
     * This method MUST always be called from the main thread
     */
    private LivingEntity createEntityWithValidation(Location location, String type, int tier, boolean elite) {
        // CRITICAL: Verify we're on the main thread
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: createEntityWithValidation called from async thread!");
            return null;
        }

        try {
            if (debug) {
                logger.info("§6[MobManager] §7Creating entity: " + type + " T" + tier + (elite ? "+" : "") +
                        " (Elite-only: " + eliteOnlyTypes.contains(type) + ")");
            }

            // Try multiple creation methods with fallbacks
            LivingEntity entity = null;

            // Method 1: Try MobType system if available (best for elites)
            if (isMobTypeSystemAvailable()) {
                entity = createEntityUsingMobTypeSystem(location, type, tier, elite);
                if (entity != null) {
                    if (debug) {
                        logger.info("§a[MobManager] §7Entity created via MobType system: " + type);
                    }
                    return entity;
                }
            }

            // Method 2: Direct world.spawnEntity with multiple attempts
            entity = createEntityWithMultipleAttempts(location, type, tier, elite);
            if (entity != null) {
                if (debug) {
                    logger.info("§a[MobManager] §7Entity created via direct spawning: " + type);
                }
                return entity;
            }

            // Method 3: Final fallback
            return createEntityFinalFallback(location, type, tier, elite);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Entity creation error for " + type + ": " + e.getMessage());
            if (debug) e.printStackTrace();

            // Last resort fallback
            return createEntityFinalFallback(location, type, tier, elite);
        }
    }

    /**
     * FIXED: Create entity with multiple attempts with elite support
     */
    private LivingEntity createEntityWithMultipleAttempts(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: createEntityWithMultipleAttempts called from async thread!");
            return null;
        }

        // Get EntityType from mapping
        EntityType entityType = mobTypeMapping.get(type);
        if (entityType == null) {
            logger.warning("§c[MobManager] No EntityType mapping for: " + type);
            return null;
        }

        // Validate EntityType is living entity
        if (!isLivingEntityType(entityType)) {
            logger.warning("§c[MobManager] EntityType " + entityType + " is not a living entity");
            return null;
        }

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                World world = location.getWorld();
                if (world == null) {
                    logger.warning("§c[MobManager] World is null for location");
                    return null;
                }

                // Ensure chunk is still loaded
                if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    ensureChunkLoadedForSpawning(location);
                }

                // Try to spawn entity - MAIN THREAD OPERATION
                Entity spawnedEntity = world.spawnEntity(location, entityType);

                if (spawnedEntity != null && spawnedEntity instanceof LivingEntity) {
                    LivingEntity entity = (LivingEntity) spawnedEntity;

                    // Validate entity after creation
                    if (entity.isValid() && !entity.isDead()) {
                        // Configure the entity
                        configureBasicEntity(entity, type, tier, elite);
                        configureEntityAppearance(entity, type, tier, elite);
                        configureEntityEquipment(entity, tier, elite);

                        if (debug && attempt > 1) {
                            logger.info("§6[MobManager] §7Entity creation succeeded on attempt " + attempt + " for " + type);
                        }

                        return entity;
                    } else {
                        // Entity is invalid, remove it and try again
                        entity.remove();
                        if (debug) {
                            logger.warning("§c[MobManager] Created entity was invalid on attempt " + attempt);
                        }
                    }
                } else {
                    if (debug) {
                        logger.warning("§c[MobManager] world.spawnEntity returned null on attempt " + attempt + " for " + type);
                    }
                }

                // If not the last attempt, wait a tick and try again
                if (attempt < 5) {
                    try {
                        Thread.sleep(50); // Small delay between attempts
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

            } catch (Exception e) {
                logger.warning("§c[MobManager] Entity creation attempt " + attempt + " failed for " + type + ": " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Check if entity type is a living entity
     */
    private boolean isLivingEntityType(EntityType entityType) {
        try {
            Class<?> entityClass = entityType.getEntityClass();
            return entityClass != null && LivingEntity.class.isAssignableFrom(entityClass);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if MobType system is available and functioning
     */
    private boolean isMobTypeSystemAvailable() {
        try {
            // Test if MobType class exists and has required methods
            Class.forName("com.rednetty.server.mechanics.mobs.core.MobType");
            return MobType.getById("skeleton") != null; // Test with a known type
        } catch (Exception e) {
            if (debug) {
                logger.warning("§6[MobManager] MobType system not available, using fallback: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * FIXED: Create entity using MobType system with enhanced elite support
     */
    private LivingEntity createEntityUsingMobTypeSystem(Location location, String type, int tier, boolean elite) {
        // CRITICAL: Verify we're on the main thread
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: createEntityUsingMobTypeSystem called from async thread!");
            return null;
        }

        try {
            MobType mobType = MobType.getById(type);
            if (mobType == null) {
                if (debug) {
                    logger.warning("§c[MobManager] MobType.getById returned null for: " + type);
                }
                return null;
            }

            // CRITICAL: Enhanced elite validation
            if (eliteOnlyTypes.contains(type) && !elite) {
                logger.warning("§c[MobManager] " + type + " is an elite-only type but elite=false was specified");
                return null;
            }

            // Create proper CustomMob on MAIN THREAD
            CustomMob customMob;

            if (elite || mobType.isElite()) {
                // Create elite mob
                customMob = new EliteMob(mobType, tier);
                if (debug) {
                    logger.info("§d[MobManager] §7Creating EliteMob for " + type + " T" + tier);
                }
            } else {
                // Create regular mob
                customMob = new CustomMob(mobType, tier, false);
                if (debug) {
                    logger.info("§a[MobManager] §7Creating CustomMob for " + type + " T" + tier);
                }
            }

            // Spawn the mob on MAIN THREAD
            boolean spawnSuccess = customMob.spawn(location);
            if (!spawnSuccess) {
                logger.warning("§c[MobManager] CustomMob spawn failed for " + type);
                return null;
            }

            LivingEntity entity = customMob.getEntity();
            if (entity == null) {
                logger.warning("§c[MobManager] CustomMob entity is null after spawn for " + type);
                return null;
            }

            // Configure entity on MAIN THREAD
            configureBasicEntity(entity, type, tier, elite);
            return entity;

        } catch (Exception e) {
            logger.warning("§c[MobManager] MobType system creation failed for " + type + ": " + e.getMessage());
            if (debug) e.printStackTrace();
            return null;
        }
    }

    /**
     * FIXED: Final fallback with elite support
     */
    private LivingEntity createEntityFinalFallback(Location location, String type, int tier, boolean elite) {
        // CRITICAL: Verify we're on the main thread
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: createEntityFinalFallback called from async thread!");
            return null;
        }

        try {
            World world = location.getWorld();
            if (world == null) {
                logger.warning("§c[MobManager] World is null for final fallback");
                return null;
            }

            // Get appropriate fallback entity type
            EntityType fallbackType = getFallbackEntityType(type);

            // MAIN THREAD ENTITY CREATION
            Entity spawnedEntity = world.spawnEntity(location, fallbackType);

            if (spawnedEntity instanceof LivingEntity) {
                LivingEntity entity = (LivingEntity) spawnedEntity;
                configureBasicEntity(entity, type, tier, elite);

                if (debug) {
                    logger.info("§6[MobManager] §7Created entity using final fallback (" + fallbackType + ") for: " + type);
                }

                return entity;
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Final fallback failed: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get appropriate fallback entity type
     */
    private EntityType getFallbackEntityType(String type) {
        // Try to get from mapping first
        EntityType mapped = mobTypeMapping.get(type);
        if (mapped != null) {
            return mapped;
        }

        // Elite-specific fallbacks
        if (eliteOnlyTypes.contains(type)) {
            switch (type) {
                case "meridian":
                    return EntityType.WARDEN;
                case "pyrion":
                case "nethys":
                case "valdris":
                case "morgana":
                case "zephyr":
                    return EntityType.WITHER_SKELETON;
                case "rimeclaw":
                    return EntityType.STRAY;
                case "thalassa":
                    return EntityType.DROWNED;
                case "xerathen":
                case "karnath":
                case "seraphina":
                case "vex_elite":
                    return EntityType.ZOMBIE;
                case "veridiana":
                    return EntityType.HUSK;
                case "cornelius":
                    return EntityType.ZOMBIE_VILLAGER;
                case "arachnia":
                    return EntityType.SPIDER;
                default:
                    return EntityType.SKELETON; // Safe fallback
            }
        }

        // Default fallback
        return EntityType.SKELETON;
    }

    /**
     * Configure basic entity properties
     */
    private void configureBasicEntity(LivingEntity entity, String type, int tier, boolean elite) {
        try {
            // Prevent removal
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);

            // Set metadata
            YakRealms plugin = YakRealms.getInstance();
            entity.setMetadata("type", new FixedMetadataValue(plugin, type));
            entity.setMetadata("tier", new FixedMetadataValue(plugin, String.valueOf(tier)));
            entity.setMetadata("customTier", new FixedMetadataValue(plugin, tier));
            entity.setMetadata("elite", new FixedMetadataValue(plugin, elite));
            entity.setMetadata("customName", new FixedMetadataValue(plugin, type));
            entity.setMetadata("dropTier", new FixedMetadataValue(plugin, tier));
            entity.setMetadata("dropElite", new FixedMetadataValue(plugin, elite));

            // Mark elite-only types properly
            if (eliteOnlyTypes.contains(type)) {
                entity.setMetadata("elite", new FixedMetadataValue(plugin, true));
                entity.setMetadata("eliteOnly", new FixedMetadataValue(plugin, true));
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure basic entity: " + e.getMessage());
        }
    }

    /**
     * Configure entity appearance
     */
    private void configureEntityAppearance(LivingEntity entity, String type, int tier, boolean elite) {
        try {
            // Generate proper name
            String displayName = generateMobDisplayName(type, tier, elite);
            entity.setCustomName(displayName);
            entity.setCustomNameVisible(true);

            // Configure mob-specific appearance
            if (entity instanceof Zombie zombie) {
                zombie.setBaby(type.equals("imp"));
            } else if (entity instanceof PigZombie pigZombie) {
                pigZombie.setAngry(true);
                pigZombie.setBaby(type.equals("imp"));
            } else if (entity instanceof MagmaCube magmaCube) {
                magmaCube.setSize(Math.max(1, tier));
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure entity appearance: " + e.getMessage());
        }
    }

    /**
     * Generate proper mob display name with elite support
     */
    private String generateMobDisplayName(String type, int tier, boolean elite) {
        try {
            // Get base name with elite-specific handling
            String baseName = getDisplayNameForType(type);

            // Get tier color
            ChatColor tierColor = MobUtils.getTierColor(tier);

            // Enhanced formatting for elites
            if (elite || eliteOnlyTypes.contains(type)) {
                return tierColor.toString() + ChatColor.BOLD + baseName;
            } else {
                return tierColor + baseName;
            }

        } catch (Exception e) {
            return "§7Unknown Mob";
        }
    }

    /**
     * Get display name for mob type with elite support
     */
    private String getDisplayNameForType(String type) {
        // Check MobType system first
        try {
            MobType mobType = MobType.getById(type);
            if (mobType != null) {
                return mobType.getTierSpecificName(1); // Use tier 1 as base name
            }
        } catch (Exception e) {
            // Fall through to manual mapping
        }

        // Manual mapping for common types
        switch (type.toLowerCase()) {
            case "witherskeleton":
                return "Wither Skeleton";
            case "cavespider":
                return "Cave Spider";
            case "magmacube":
                return "Magma Cube";
            case "zombifiedpiglin":
                return "Zombified Piglin";
            case "elderguardian":
                return "Elder Guardian";
            case "irongolem":
                return "Iron Golem";
            case "frozenboss":
                return "Frozen Boss";
            case "frozenelite":
                return "Frozen Elite";
            case "frozengolem":
                return "Frozen Golem";
            case "bossskeleton":
                return "Boss Skeleton";

            // T5 Elite names
            case "meridian":
                return "Meridian";
            case "pyrion":
                return "Pyrion";
            case "rimeclaw":
                return "Rimeclaw";
            case "thalassa":
                return "Thalassa";
            case "nethys":
                return "Nethys";

            // Other elite names
            case "malachar":
                return "Malachar";
            case "xerathen":
                return "Xerathen";
            case "veridiana":
                return "Veridiana";
            case "thorgrim":
                return "Thorgrim";
            case "lysander":
                return "Lysander";
            case "morgana":
                return "Morgana";
            case "cornelius":
                return "Cornelius";
            case "valdris":
                return "Valdris";
            case "seraphina":
                return "Seraphina";
            case "arachnia":
                return "Arachnia";
            case "karnath":
                return "Karnath";
            case "zephyr":
                return "Zephyr";

            default:
                return type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
        }
    }

    /**
     * Configure basic entity equipment
     */
    private void configureEntityEquipment(LivingEntity entity, int tier, boolean elite) {
        try {
            if (entity.getEquipment() == null) return;

            // Create basic weapon based on tier
            Material weaponMaterial;
            switch (tier) {
                case 1:
                    weaponMaterial = Material.WOODEN_SWORD;
                    break;
                case 2:
                    weaponMaterial = Material.STONE_SWORD;
                    break;
                case 3:
                    weaponMaterial = Material.IRON_SWORD;
                    break;
                case 4:
                    weaponMaterial = Material.DIAMOND_SWORD;
                    break;
                case 5:
                    weaponMaterial = Material.GOLDEN_SWORD;
                    break;
                case 6:
                    weaponMaterial = Material.DIAMOND_SWORD;
                    break;
                default:
                    weaponMaterial = Material.WOODEN_SWORD;
            }

            entity.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(weaponMaterial));
            entity.getEquipment().setItemInMainHandDropChance(0);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure entity equipment: " + e.getMessage());
        }
    }

    /**
     * Get a safe spawn location near the target location with enhanced validation
     */
    private Location getSafeSpawnLocationEnhanced(Location target) {
        if (target == null || target.getWorld() == null) {
            return null;
        }

        // Try the target location first
        if (isSafeSpawnLocationEnhanced(target)) {
            return target.clone().add(0.5, 0, 0.5);
        }

        // Try nearby locations with more attempts
        for (int attempts = 0; attempts < 20; attempts++) {
            double x = target.getX() + (Math.random() * 10 - 5);
            double z = target.getZ() + (Math.random() * 10 - 5);
            double y = target.getY();

            // Try different Y levels
            for (int yOffset = 0; yOffset <= 5; yOffset++) {
                Location candidate = new Location(target.getWorld(), x, y + yOffset, z);
                if (isSafeSpawnLocationEnhanced(candidate)) {
                    return candidate;
                }
            }
        }

        // Final fallback with forced safe location
        return target.clone().add(0.5, 5, 0.5);
    }

    /**
     * Enhanced safe spawn location checking with detailed validation
     */
    private boolean isSafeSpawnLocationEnhanced(Location location) {
        try {
            if (location == null || location.getWorld() == null) {
                return false;
            }

            // Check if chunk is loaded
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return false;
            }

            // Enhanced Y bounds checking
            if (location.getY() < location.getWorld().getMinHeight()) {
                return false;
            }

            if (location.getY() > location.getWorld().getMaxHeight() - 5) {
                return false;
            }

            // Check current block
            Material currentBlock = location.getBlock().getType();
            if (currentBlock.isSolid()) {
                return false;
            }

            // Check block above
            Material blockAbove = location.clone().add(0, 1, 0).getBlock().getType();
            if (blockAbove.isSolid()) {
                return false;
            }

            // Check for dangerous blocks
            if (isDangerousBlock(currentBlock) || isDangerousBlock(blockAbove)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("§c[MobManager] Error validating spawn location: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Check if a block type is dangerous for mob spawning
     */
    private boolean isDangerousBlock(Material material) {
        switch (material) {
            case LAVA:
            case FIRE:
            case MAGMA_BLOCK:
            case CACTUS:
            case SWEET_BERRY_BUSH:
                return true;
            default:
                return false;
        }
    }

    /**
     * Register spawned mob with tracking systems
     */
    private void registerSpawnedMob(LivingEntity entity, Location spawnerLocation) {
        try {
            UUID entityId = entity.getUniqueId();

            // Find and link to spawner
            Location nearestSpawner = findNearestSpawner(spawnerLocation, 5.0);
            if (nearestSpawner != null) {
                mobSpawnerLocations.put(entityId, nearestSpawner);
                String spawnerId = generateSpawnerId(nearestSpawner);
                entity.setMetadata("spawner", new FixedMetadataValue(plugin, spawnerId));
                entityToSpawner.put(entityId.toString(), spawnerId);
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error registering spawned mob: " + e.getMessage());
        }
    }

    /**
     * Track failure counts for mob types
     */
    private void incrementFailureCount(String mobType) {
        int count = failureCounter.getOrDefault(mobType, 0) + 1;
        failureCounter.put(mobType, count);

        if (count > 10 && count % 5 == 0) {
            logger.warning("§c[MobManager] High failure count for " + mobType + ": " + count + " failures");
        }
    }

    /**
     * FIXED: Enhanced spawn request validation with elite support
     */
    private boolean isValidSpawnRequest(Location location, String type, int tier, boolean elite) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (type == null || type.trim().isEmpty()) {
            return false;
        }

        if (tier < 1 || tier > 6) {
            return false;
        }

        // CRITICAL: Elite-only type validation
        if (eliteOnlyTypes.contains(type.toLowerCase()) && !elite) {
            if (debug) {
                logger.warning("§c[MobManager] " + type + " is an elite-only type but elite=false");
            }
            return false;
        }

        return true;
    }

    /**
     * FIXED: Check if spawner can spawn a mob with elite validation
     */
    public boolean canSpawnerSpawnMob(String type, int tier, boolean elite) {
        try {
            // Normalize type
            String normalizedType = normalizeMobType(type);
            if (normalizedType == null) {
                return false;
            }

            // Check if we have mapping for this type
            if (!mobTypeMapping.containsKey(normalizedType) && !MobType.isValidType(normalizedType)) {
                return false;
            }

            // Check tier validity (basic validation)
            if (tier < 1 || tier > 6) {
                return false;
            }

            // Check T6 availability
            if (tier > 5 && !YakRealms.isT6Enabled()) {
                return false;
            }

            // CRITICAL: Elite-only type validation
            if (eliteOnlyTypes.contains(normalizedType) && !elite) {
                return false;
            }

            // Check failure rate
            int failures = failureCounter.getOrDefault(normalizedType, 0);
            if (failures > 20) {
                return false; // Too many failures
            }

            return true;
        } catch (Exception e) {
            logger.warning("§c[MobManager] Error checking spawner mob validity: " + e.getMessage());
            return false;
        }
    }

    // ================ ENHANCED ENTITY VALIDATION ================

    private void validateEntityTracking() {
        try {
            mobLock.readLock().lock();
            Set<UUID> invalidEntities = new HashSet<>();

            for (Map.Entry<UUID, CustomMob> entry : activeMobs.entrySet()) {
                UUID entityId = entry.getKey();
                CustomMob mob = entry.getValue();

                if (!isEntityValidAndTracked(mob.getEntity())) {
                    invalidEntities.add(entityId);
                }
            }

            if (!invalidEntities.isEmpty()) {
                mobLock.readLock().unlock();
                mobLock.writeLock().lock();
                try {
                    for (UUID invalidId : invalidEntities) {
                        activeMobs.remove(invalidId);
                        // Clean up from CritManager too
                        CritManager.getInstance().removeCrit(invalidId);
                    }
                } finally {
                    mobLock.readLock().lock();
                    mobLock.writeLock().unlock();
                }
            }
        } finally {
            mobLock.readLock().unlock();
        }
    }

    private boolean isEntityValidAndTracked(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }

        try {
            Entity foundEntity = Bukkit.getEntity(entity.getUniqueId());
            if (foundEntity == null || !foundEntity.equals(entity)) {
                return false;
            }

            World world = entity.getWorld();
            if (world == null || !entity.isInWorld()) {
                return false;
            }

            Location loc = entity.getLocation();
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================ ACTIVE MOBS MANAGEMENT ================

    private void updateActiveMobs() {
        if (activeWorldBoss != null && activeWorldBoss.isValid()) {
            activeWorldBoss.tick(); // Use the new tick method
        }

        mobLock.writeLock().lock();
        try {
            activeMobs.entrySet().removeIf(entry -> {
                CustomMob mob = entry.getValue();
                if (mob == null || !mob.isValid()) {
                    // Clean up from CritManager too
                    CritManager.getInstance().removeCrit(entry.getKey());
                    return true;
                }

                // Tick the mob
                mob.tick();
                return false;
            });
        } finally {
            mobLock.writeLock().unlock();
        }
    }

    // ================ POSITION MONITORING ================

    private void checkMobPositions() {
        try {
            int teleported = 0;

            mobLock.readLock().lock();
            List<CustomMob> mobsToCheck;
            try {
                mobsToCheck = new ArrayList<>(activeMobs.values());
            } finally {
                mobLock.readLock().unlock();
            }

            for (CustomMob mob : mobsToCheck) {
                if (mob.isValid() && handleMobPositionCheck(mob)) {
                    teleported++;
                }
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Position check error: " + e.getMessage());
        }
    }

    private boolean handleMobPositionCheck(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (!isEntityValidAndTracked(entity)) return false;

        Location spawnerLoc = getSpawnerLocation(entity);
        if (spawnerLoc == null) return false;

        if (!entity.getLocation().getWorld().equals(spawnerLoc.getWorld())) {
            teleportMobToSpawner(entity, spawnerLoc, "changed worlds");
            return true;
        }

        double distanceSquared = entity.getLocation().distanceSquared(spawnerLoc);
        if (distanceSquared > (MAX_WANDERING_DISTANCE * MAX_WANDERING_DISTANCE)) {
            teleportMobToSpawner(entity, spawnerLoc, "wandered too far");
            return true;
        }

        if (AlignmentMechanics.isSafeZone(entity.getLocation())) {
            teleportMobToSpawner(entity, spawnerLoc, "entered safezone");
            return true;
        }

        return false;
    }

    private Location getSpawnerLocation(LivingEntity entity) {
        if (entity == null) return null;

        UUID entityId = entity.getUniqueId();

        if (mobSpawnerLocations.containsKey(entityId)) {
            return mobSpawnerLocations.get(entityId);
        }

        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();
            Location spawnerLoc = findSpawnerById(spawnerId);
            if (spawnerLoc != null) {
                mobSpawnerLocations.put(entityId, spawnerLoc);
                return spawnerLoc;
            }
        }

        Location nearestSpawner = findNearestSpawner(entity.getLocation(), mobRespawnDistanceCheck);
        if (nearestSpawner != null) {
            mobSpawnerLocations.put(entityId, nearestSpawner);
        }

        return nearestSpawner;
    }

    private Location findSpawnerById(String spawnerId) {
        return spawner.getAllSpawners().entrySet().stream()
                .filter(entry -> generateSpawnerId(entry.getKey()).equals(spawnerId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * FIXED: Safe mob teleportation - MAIN THREAD ONLY
     */
    private void teleportMobToSpawner(LivingEntity entity, Location spawnerLoc, String reason) {
        // CRITICAL: Verify we're on the main thread
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: teleportMobToSpawner called from async thread!");

            // Schedule on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                teleportMobToSpawner(entity, spawnerLoc, reason);
            });
            return;
        }

        if (entity == null || spawnerLoc == null) return;

        try {
            Location safeLoc = spawnerLoc.clone().add(
                    (Math.random() * 4 - 2),
                    1.0,
                    (Math.random() * 4 - 2)
            );

            safeLoc.setX(safeLoc.getBlockX() + 0.5);
            safeLoc.setZ(safeLoc.getBlockZ() + 0.5);

            // All particle and sound operations on main thread
            entity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, entity.getLocation().clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                boolean wasAware = mob.isAware();
                mob.setAware(false);

                // Schedule awareness restoration on main thread
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isEntityValidAndTracked(entity)) {
                        mob.setAware(wasAware);
                    }
                }, 10L);
            }

            // Entity teleportation on main thread
            entity.teleport(safeLoc);

            // More effects on main thread
            entity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, safeLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            entity.getWorld().playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Teleport error: " + e.getMessage());
        }
    }

    // ================ CRITICAL HIT DELEGATION ================

    /**
     * FIXED: Roll for critical hit using CritManager
     */
    public void rollForCriticalHit(LivingEntity entity, double damage) {
        try {
            CustomMob customMob = getCustomMob(entity);
            if (customMob != null) {
                // Delegate to CritManager
                CritManager.getInstance().initiateCrit(customMob);
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Critical roll error: " + e.getMessage());
        }
    }

    // ================ MOB REGISTRATION ================

    public void registerMob(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) return;

        LivingEntity entity = mob.getEntity();

        mobLock.writeLock().lock();
        try {
            activeMobs.put(entity.getUniqueId(), mob);
        } finally {
            mobLock.writeLock().unlock();
        }

        if (mob instanceof WorldBoss) {
            activeWorldBoss = (WorldBoss) mob;
        }
    }

    public void unregisterMob(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) return;

        LivingEntity entity = mob.getEntity();
        UUID entityId = entity.getUniqueId();

        mobLock.writeLock().lock();
        try {
            activeMobs.remove(entityId);
        } finally {
            mobLock.writeLock().unlock();
        }

        // Clean up from CritManager
        CritManager.getInstance().removeCrit(entityId);

        mobSpawnerLocations.remove(entityId);
        entityToSpawner.remove(entityId.toString());

        if (mob instanceof WorldBoss) {
            activeWorldBoss = null;
        }
    }

    // ================ DAMAGE PROCESSING ================

    public void trackDamage(LivingEntity entity, Player player, double damage) {
        if (entity == null || player == null) return;

        UUID entityUuid = entity.getUniqueId();
        UUID playerUuid = player.getUniqueId();

        damageContributions.computeIfAbsent(entityUuid, k -> new ConcurrentHashMap<>())
                .merge(playerUuid, damage, Double::sum);
    }

    public Player getTopDamageDealer(LivingEntity entity) {
        if (entity == null) return null;

        Map<UUID, Double> damageMap = damageContributions.get(entity.getUniqueId());
        if (damageMap == null || damageMap.isEmpty()) return null;

        return damageMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .map(Bukkit::getPlayer)
                .orElse(null);
    }

    // ================ CLEANUP OPERATIONS ================

    private void performCleanup() {
        try {
            cleanupDamageTracking();
            cleanupEntityMappings();
            cleanupMobLocations();
            cleanupFailureCounters();
            processedEntities.clear();

        } catch (Exception e) {
            logger.severe("§c[MobManager] Cleanup error: " + e.getMessage());
        }
    }

    private void cleanupDamageTracking() {
        Set<UUID> invalidEntities = damageContributions.keySet().stream()
                .filter(id -> !isEntityValid(id))
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidEntities.forEach(damageContributions::remove);
    }

    private void cleanupEntityMappings() {
        Set<String> invalidIds = entityToSpawner.keySet().stream()
                .filter(id -> {
                    try {
                        return !isEntityValid(UUID.fromString(id));
                    } catch (Exception e) {
                        return true; // Invalid UUID format
                    }
                })
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidIds.forEach(entityToSpawner::remove);
    }

    private void cleanupMobLocations() {
        Set<UUID> invalidIds = mobSpawnerLocations.keySet().stream()
                .filter(id -> !isEntityValid(id))
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidIds.forEach(mobSpawnerLocations::remove);
    }

    private void cleanupFailureCounters() {
        // Reset failure counters that are too old or too high
        failureCounter.entrySet().removeIf(entry -> entry.getValue() > 50);
    }

    private boolean isEntityValid(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        return entity != null && entity.isValid() && !entity.isDead();
    }

    // ================ RESPAWN MANAGEMENT ================

    public long calculateRespawnDelay(int tier, boolean elite) {
        double tierFactor = 1.0 + ((tier - 1) * 0.2);
        double eliteMultiplier = elite ? 1.5 : 1.0;

        long calculatedDelay = (long) (MIN_RESPAWN_DELAY * tierFactor * eliteMultiplier);
        double randomFactor = 0.9 + (Math.random() * 0.2);
        calculatedDelay = (long) (calculatedDelay * randomFactor);

        return Math.min(Math.max(calculatedDelay, MIN_RESPAWN_DELAY), MAX_RESPAWN_DELAY);
    }

    public long recordMobDeath(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return 0;

        String key = getResponKey(mobType, tier, elite);
        long currentTime = System.currentTimeMillis();
        long respawnDelay = calculateRespawnDelay(tier, elite);
        long respawnTime = currentTime + respawnDelay;

        respawnTimes.put(key, respawnTime);
        mobTypeLastDeath.put(key, currentTime);

        return respawnTime;
    }

    public boolean canRespawn(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return true;

        String key = getResponKey(mobType, tier, elite);
        Long respawnTime = respawnTimes.get(key);

        if (respawnTime == null) return true;

        return System.currentTimeMillis() >= respawnTime;
    }

    public String getResponKey(String mobType, int tier, boolean elite) {
        return String.format("%s:%d:%s", mobType, tier, elite ? "elite" : "normal");
    }

    public long getLastDeathTime(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return 0;

        String key = getResponKey(mobType, tier, elite);
        return mobTypeLastDeath.getOrDefault(key, 0L);
    }

    // ================ UTILITY METHODS ================

    public int getMobTier(LivingEntity entity) {
        CustomMob mob = getCustomMob(entity);
        return mob != null ? mob.getTier() : MobUtils.getMobTier(entity);
    }

    public boolean isElite(LivingEntity entity) {
        CustomMob mob = getCustomMob(entity);
        return mob != null ? mob.isElite() : MobUtils.isElite(entity);
    }

    public CustomMob getCustomMob(LivingEntity entity) {
        if (entity == null) return null;

        mobLock.readLock().lock();
        try {
            return activeMobs.get(entity.getUniqueId());
        } finally {
            mobLock.readLock().unlock();
        }
    }

    public Location findNearestSpawner(Location location, double maxDistance) {
        return spawner.findNearestSpawner(location, maxDistance);
    }

    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

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

    // ================ ENHANCED DEATH HANDLING ================

    /**
     * FIXED: Enhanced entity death handling with MAIN THREAD SAFETY
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        // This event is always called on the main thread, so it's safe
        try {
            LivingEntity entity = event.getEntity();
            UUID entityId = entity.getUniqueId();

            if (entity instanceof Player || processedEntities.contains(entityId)) {
                return;
            }

            processedEntities.add(entityId);

            // Enhanced debug logging
            if (debug) {
                logger.info("§6[MobManager] §7Processing death: " + entity.getType() +
                        " ID: " + entityId.toString().substring(0, 8));
            }

            // Clean up from CritManager
            CritManager.getInstance().removeCrit(entityId);

            // Handle custom mob death
            if (entity.hasMetadata("type")) {
                handleCustomMobDeath(entity);
            }

            // Always try to find and notify the spawner
            Location spawnerLoc = findSpawnerForMob(entity);
            if (spawnerLoc != null) {
                spawner.registerMobDeath(spawnerLoc, entityId);

                if (debug) {
                    logger.info("§a[MobManager] §7Notified spawner at " + formatLocation(spawnerLoc) +
                            " of mob death");
                }
            } else if (debug) {
                logger.warning("§c[MobManager] §7Could not find spawner for dead mob: " + entityId.toString().substring(0, 8));
            }

            // Clean up tracking data
            cleanupMobData(entityId);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Entity death processing error: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    private void handleCustomMobDeath(LivingEntity entity) {
        String mobType = entity.getMetadata("type").get(0).asString();
        int tier = getMobTier(entity);
        boolean elite = isElite(entity);

        recordMobDeath(mobType, tier, elite);

        Location spawnerLoc = findSpawnerForMob(entity);
        if (spawnerLoc != null) {
            spawner.registerMobDeath(spawnerLoc, entity.getUniqueId());
        }
    }

    /**
     * Enhanced spawner finding for dead mobs
     */
    private Location findSpawnerForMob(LivingEntity entity) {
        if (entity == null) return null;

        UUID entityId = entity.getUniqueId();

        try {
            // Method 1: Check cached spawner locations
            if (mobSpawnerLocations.containsKey(entityId)) {
                Location cachedLoc = mobSpawnerLocations.get(entityId);
                if (isValidSpawnerLocation(cachedLoc)) {
                    return cachedLoc;
                } else {
                    mobSpawnerLocations.remove(entityId);
                }
            }

            // Method 2: Check spawner metadata
            if (entity.hasMetadata("spawner")) {
                try {
                    String spawnerId = entity.getMetadata("spawner").get(0).asString();
                    Location spawnerLoc = findSpawnerLocationById(spawnerId);
                    if (spawnerLoc != null) {
                        return spawnerLoc;
                    }
                } catch (Exception e) {
                    if (debug) {
                        logger.warning("§c[MobManager] Error reading spawner metadata: " + e.getMessage());
                    }
                }
            }

            // Method 3: Search for nearest spawner
            Location mobLocation = entity.getLocation();
            Location nearestSpawner = spawner.findNearestSpawner(mobLocation, mobRespawnDistanceCheck);

            if (nearestSpawner != null) {
                if (debug) {
                    logger.info("§e[MobManager] §7Found nearest spawner for mob at distance: " +
                            String.format("%.1f", mobLocation.distance(nearestSpawner)));
                }
                return nearestSpawner;
            }

            return null;

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error finding spawner for mob: " + e.getMessage());
            return null;
        }
    }

    /**
     * Validate spawner location exists
     */
    private boolean isValidSpawnerLocation(Location location) {
        if (location == null || location.getWorld() == null) return false;

        try {
            Map<Location, String> allSpawners = spawner.getAllSpawners();

            for (Location spawnerLoc : allSpawners.keySet()) {
                if (spawnerLoc.getWorld().equals(location.getWorld()) &&
                        spawnerLoc.getBlockX() == location.getBlockX() &&
                        spawnerLoc.getBlockY() == location.getBlockY() &&
                        spawnerLoc.getBlockZ() == location.getBlockZ()) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Find spawner location by ID
     */
    private Location findSpawnerLocationById(String spawnerId) {
        try {
            Map<Location, String> allSpawners = spawner.getAllSpawners();

            for (Location loc : allSpawners.keySet()) {
                String generatedId = generateSpawnerId(loc);
                if (generatedId.equals(spawnerId)) {
                    return loc;
                }
            }

            return null;
        } catch (Exception e) {
            if (debug) {
                logger.warning("§c[MobManager] Error finding spawner by ID: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Clean up all mob-related data
     */
    private void cleanupMobData(UUID entityId) {
        try {
            // Remove from all tracking maps
            mobSpawnerLocations.remove(entityId);
            entityToSpawner.remove(entityId.toString());
            damageContributions.remove(entityId);

            if (debug) {
                logger.info("§6[MobManager] §7Cleaned up data for mob: " + entityId.toString().substring(0, 8));
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error cleaning up mob data: " + e.getMessage());
        }
    }

    // ================ DAMAGE EVENTS ================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity) || event.getEntity() instanceof Player) {
                return;
            }

            Player damager = extractPlayerDamager(event);
            if (damager == null) return;

            double damage = event.getFinalDamage();

            // Roll for critical hit using CritManager
            rollForCriticalHit(entity, damage);

            // Track damage
            trackDamage(entity, damager, damage);

            // Update mob's health bar
            CustomMob mob = getCustomMob(entity);
            if (mob != null) {
                mob.updateHealthBar();
            }

        } catch (Exception e) {
            logger.severe("§c[MobManager] Entity damage event error: " + e.getMessage());
        }
    }

    private Player extractPlayerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobHitMob(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity damager &&
                !(event.getDamager() instanceof Player) &&
                !(event.getEntity() instanceof Player)) {

            if (damager.isCustomNameVisible() &&
                    !damager.getCustomName().equalsIgnoreCase("Celestial Ally")) {
                event.setCancelled(true);
                event.setDamage(0.0);
            }
        }
    }

    // ================ SPAWNER DELEGATION ================

    public boolean setSpawnerVisibility(Location location, boolean visible) {
        return location != null && spawner.setSpawnerVisibility(location, visible);
    }

    public int getActiveMobCount(Location location) {
        return spawner.getActiveMobCount(location);
    }

    public boolean isSpawnerVisible(Location location) {
        return location != null && spawner.isSpawnerVisible(location);
    }

    public void updateSpawnerHologram(Location location) {
        if (location != null) spawner.createOrUpdateSpawnerHologram(location);
    }

    public void removeSpawnerHologram(Location location) {
        if (location != null) spawner.removeSpawnerHologram(location);
    }

    public MobSpawner getSpawner() {
        return spawner;
    }

    public SpawnerMetrics getSpawnerMetrics(Location location) {
        return location != null ? spawner.getSpawnerMetrics(location) : null;
    }

    public boolean resetSpawner(Location location) {
        return location != null && spawner.resetSpawner(location);
    }

    public boolean addSpawner(Location location, String data) {
        return spawner.addSpawner(location, data);
    }

    public boolean removeSpawner(Location location) {
        return spawner.removeSpawner(location);
    }

    public Map<Location, String> getAllSpawners() {
        return spawner.getAllSpawners();
    }

    public void sendSpawnerInfo(Player player, Location location) {
        if (player != null && location != null) {
            spawner.sendSpawnerInfo(player, location);
        }
    }

    public void resetAllSpawners() {
        spawner.resetAllSpawners();
        logger.info("§a[MobManager] §7All spawners reset");
    }

    // ================ CONFIGURATION GETTERS ================

    public boolean isDebugMode() {
        return debug;
    }

    public void setDebugMode(boolean debug) {
        this.debug = debug;
        spawner.setDebugMode(debug);
    }

    public int getMaxMobsPerSpawner() {
        return maxMobsPerSpawner;
    }

    public double getPlayerDetectionRange() {
        return playerDetectionRange;
    }

    public double getMobRespawnDistanceCheck() {
        return mobRespawnDistanceCheck;
    }

    public void setSpawnersEnabled(boolean enabled) {
        spawnersEnabled = enabled;
        spawner.setSpawnersEnabled(enabled);
    }

    public boolean areSpawnersEnabled() {
        return spawnersEnabled;
    }

    // ================ DIAGNOSTIC METHODS ================

    /**
     * Get diagnostic information for troubleshooting
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== MobManager Diagnostic Info ===\n");
        info.append("Valid Mob Types: ").append(validMobTypes.size()).append("\n");
        info.append("Mob Type Mappings: ").append(mobTypeMapping.size()).append("\n");
        info.append("Elite-Only Types: ").append(eliteOnlyTypes.size()).append("\n");
        info.append("Active Mobs: ").append(activeMobs.size()).append("\n");
        info.append("Failure Counters:\n");

        for (Map.Entry<String, Integer> entry : failureCounter.entrySet()) {
            if (entry.getValue() > 0) {
                info.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        info.append("Elite-Only Types:\n");
        for (String eliteType : eliteOnlyTypes) {
            EntityType entityType = mobTypeMapping.get(eliteType);
            info.append("  ").append(eliteType).append(" -> ").append(entityType).append("\n");
        }

        info.append("Available EntityTypes for common mobs:\n");
        String[] commonTypes = {"skeleton", "witherskeleton", "zombie", "spider", "meridian", "pyrion"};
        for (String type : commonTypes) {
            EntityType entityType = mobTypeMapping.get(type);
            info.append("  ").append(type).append(" -> ").append(entityType).append("\n");
        }

        return info.toString();
    }

    /**
     * Get cleanup status for debugging
     */
    public String getCleanupStatus() {
        return String.format("Cleanup Status: shutting_down=%s, cleanup_completed=%s, active_mobs=%d",
                isShuttingDown, cleanupCompleted, activeMobs.size());
    }

    // ================ ENHANCED PROTECTION EVENT HANDLERS ================

    /**
     * CRITICAL FIX: Prevent custom mobs from burning in sunlight
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity)) {
                return;
            }

            // Check if this is a custom mob
            if (entity.hasMetadata("type") || isCustomMobEntity(entity)) {
                // Cancel all combustion for custom mobs
                event.setCancelled(true);

                // Also extinguish them if they're already burning
                entity.setFireTicks(0);

                if (debug) {
                    logger.info("§6[MobManager] §7Prevented custom mob " +
                            entity.getType() + " from burning (sunlight protection)");
                }
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Error in sunlight protection: " + e.getMessage());
        }
    }

    /**
     * CRITICAL FIX: Prevent custom mobs from taking fire damage (additional protection)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomMobFireDamage(EntityDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity)) {
                return;
            }

            // Only handle fire-related damage
            if (event.getCause() != EntityDamageEvent.DamageCause.FIRE &&
                    event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK &&
                    event.getCause() != EntityDamageEvent.DamageCause.LAVA) {
                return;
            }

            // Check if this is a custom mob
            if (entity.hasMetadata("type") || isCustomMobEntity(entity)) {
                // Cancel fire damage for custom mobs
                event.setCancelled(true);

                // Ensure they're not on fire
                entity.setFireTicks(0);

                if (debug) {
                    logger.info("§6[MobManager] §7Prevented fire damage to custom mob " +
                            entity.getType() + " (damage cause: " + event.getCause() + ")");
                }
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Error in fire damage protection: " + e.getMessage());
        }
    }

    /**
     * CRITICAL FIX: Ensure custom mob equipment integrity during combat
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCustomMobEquipmentCheck(EntityDamageByEntityEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity) || event.getEntity() instanceof Player) {
                return;
            }

            // Check if this is a custom mob
            if (entity.hasMetadata("type") || isCustomMobEntity(entity)) {
                // Schedule equipment validation for next tick to ensure integrity
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        validateMobEquipmentIntegrity(entity);
                    } catch (Exception e) {
                        // Silent fail for equipment validation
                    }
                });
            }
        } catch (Exception e) {
            // Silent fail for equipment monitoring
        }
    }

    /**
     * Validate and fix mob equipment integrity
     */
    private void validateMobEquipmentIntegrity(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.getEquipment() == null) {
            return;
        }

        try {
            // Check weapon
            org.bukkit.inventory.ItemStack weapon = entity.getEquipment().getItemInMainHand();
            if (weapon != null && weapon.getType() != Material.AIR) {
                ensureItemUnbreakable(weapon);
            }

            // Check armor pieces
            org.bukkit.inventory.ItemStack[] armor = {
                    entity.getEquipment().getHelmet(),
                    entity.getEquipment().getChestplate(),
                    entity.getEquipment().getLeggings(),
                    entity.getEquipment().getBoots()
            };

            for (org.bukkit.inventory.ItemStack piece : armor) {
                if (piece != null && piece.getType() != Material.AIR) {
                    ensureItemUnbreakable(piece);
                }
            }

        } catch (Exception e) {
            if (debug) {
                logger.warning("§c[MobManager] Equipment validation error: " + e.getMessage());
            }
        }
    }

    /**
     * Ensure an item is unbreakable
     */
    private void ensureItemUnbreakable(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        try {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null && !meta.isUnbreakable()) {
                meta.setUnbreakable(true);
                item.setItemMeta(meta);
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    /**
     * ENHANCED: Enhanced custom mob validation during cleanup
     */
    private boolean isCustomMobEntityEnhanced(LivingEntity entity) {
        if (entity == null) return false;

        try {
            // Check for any custom mob metadata
            for (String key : CUSTOM_MOB_METADATA_KEYS) {
                if (entity.hasMetadata(key)) {
                    return true;
                }
            }

            // Check for custom names that indicate custom mobs
            if (entity.getCustomName() != null) {
                String name = entity.getCustomName();
                if (name.contains("§") || // Has color codes
                        name.contains("T1") || name.contains("T2") || name.contains("T3") ||
                        name.contains("T4") || name.contains("T5") || name.contains("T6") ||
                        name.contains("Elite") || name.contains("Boss")) {
                    return true;
                }
            }

            // Check for equipment with no drop chance (indicates custom mob)
            if (entity.getEquipment() != null) {
                org.bukkit.inventory.ItemStack weapon = entity.getEquipment().getItemInMainHand();
                if (weapon != null && weapon.getType() != Material.AIR) {
                    if (entity.getEquipment().getItemInMainHandDropChance() == 0) {
                        return true; // Custom equipment with no drop chance
                    }

                    // Check if weapon is unbreakable (indicates custom mob)
                    if (weapon.hasItemMeta() && weapon.getItemMeta().isUnbreakable()) {
                        return true;
                    }
                }
            }

            // Check for fire resistance (all custom mobs have this)
            if (entity.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ENHANCED: Periodic protection maintenance task
     */
    private void startProtectionMaintenanceTask() {
        try {
            // Run protection maintenance every 30 seconds
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isShuttingDown) {
                        this.cancel();
                        return;
                    }

                    try {
                        performProtectionMaintenance();
                    } catch (Exception e) {
                        if (debug) {
                            logger.warning("§c[MobManager] Protection maintenance error: " + e.getMessage());
                        }
                    }
                }
            }.runTaskTimer(plugin, 600L, 600L); // Every 30 seconds

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to start protection maintenance task: " + e.getMessage());
        }
    }

    /**
     * Perform periodic protection maintenance
     */
    private void performProtectionMaintenance() {
        try {
            int protectedCount = 0;
            int extinguishedCount = 0;

            // Check all active custom mobs
            mobLock.readLock().lock();
            List<CustomMob> mobsToCheck;
            try {
                mobsToCheck = new ArrayList<>(activeMobs.values());
            } finally {
                mobLock.readLock().unlock();
            }

            for (CustomMob mob : mobsToCheck) {
                if (mob != null && mob.isValid()) {
                    LivingEntity entity = mob.getEntity();

                    // Ensure fire protection
                    if (entity.getFireTicks() > 0) {
                        entity.setFireTicks(0);
                        extinguishedCount++;
                    }

                    // Validate equipment integrity
                    validateMobEquipmentIntegrity(entity);
                    protectedCount++;
                }
            }

            if (debug && (protectedCount > 0 || extinguishedCount > 0)) {
                logger.info(String.format("§a[MobManager] §7Protection maintenance: %d mobs protected, %d extinguished",
                        protectedCount, extinguishedCount));
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Protection maintenance failed: " + e.getMessage());
        }
    }

    // ================ ENHANCED INITIALIZATION (ADD TO EXISTING INITIALIZE METHOD) ================

    /**
     * Add this to the existing initialize() method after the existing code:
     */
    public void initializeProtectionSystems() {
        try {
            // Start protection maintenance task
            startProtectionMaintenanceTask();

            logger.info("§a[MobManager] §7Enhanced protection systems initialized:");
            logger.info("§a[MobManager] §7- Sunlight burning prevention: ACTIVE");
            logger.info("§a[MobManager] §7- Equipment durability protection: ACTIVE");
            logger.info("§a[MobManager] §7- Fire damage immunity: ACTIVE");
            logger.info("§a[MobManager] §7- Periodic maintenance: ACTIVE");

        } catch (Exception e) {
            logger.severe("§c[MobManager] Failed to initialize protection systems: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================ PROTECTION STATUS METHODS ================

    /**
     * Get protection status for a specific mob
     */
    public String getMobProtectionStatus(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return "INVALID";
        }

        StringBuilder status = new StringBuilder();

        try {
            // Fire protection
            boolean fireProtected = entity.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE);
            status.append("Fire: ").append(fireProtected ? "PROTECTED" : "VULNERABLE");

            // Equipment protection
            boolean equipmentProtected = false;
            if (entity.getEquipment() != null) {
                org.bukkit.inventory.ItemStack weapon = entity.getEquipment().getItemInMainHand();
                if (weapon != null && weapon.hasItemMeta()) {
                    equipmentProtected = weapon.getItemMeta().isUnbreakable();
                }
            }
            status.append(", Equipment: ").append(equipmentProtected ? "PROTECTED" : "VULNERABLE");

            // Sunlight protection
            boolean sunlightProtected = entity.hasMetadata("sunlight_immune") ||
                    entity.hasMetadata("elite_sunlight_immune");
            status.append(", Sunlight: ").append(sunlightProtected ? "PROTECTED" : "VULNERABLE");

            // Fire ticks
            status.append(", Fire Ticks: ").append(entity.getFireTicks());

        } catch (Exception e) {
            status.append("ERROR: ").append(e.getMessage());
        }

        return status.toString();
    }

    /**
     * Get overall protection system status
     */
    public String getProtectionSystemStatus() {
        try {
            int totalMobs = activeMobs.size();
            int protectedMobs = 0;
            int burningMobs = 0;

            mobLock.readLock().lock();
            try {
                for (CustomMob mob : activeMobs.values()) {
                    if (mob != null && mob.isValid()) {
                        LivingEntity entity = mob.getEntity();

                        if (entity.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                            protectedMobs++;
                        }

                        if (entity.getFireTicks() > 0) {
                            burningMobs++;
                        }
                    }
                }
            } finally {
                mobLock.readLock().unlock();
            }

            return String.format("Protection Status: %d/%d mobs protected, %d burning (should be 0)",
                    protectedMobs, totalMobs, burningMobs);

        } catch (Exception e) {
            return "Protection Status: ERROR - " + e.getMessage();
        }
    }

    /**
     * Force protection refresh for all active mobs
     */
    public void refreshAllProtections() {
        try {
            logger.info("§6[MobManager] §7Refreshing protections for all active mobs...");

            int refreshed = 0;

            mobLock.readLock().lock();
            List<CustomMob> mobsToRefresh;
            try {
                mobsToRefresh = new ArrayList<>(activeMobs.values());
            } finally {
                mobLock.readLock().unlock();
            }

            for (CustomMob mob : mobsToRefresh) {
                if (mob != null && mob.isValid()) {
                    LivingEntity entity = mob.getEntity();

                    // Refresh fire protection
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 2));

                    // Extinguish any fire
                    entity.setFireTicks(0);

                    // Refresh equipment protection
                    validateMobEquipmentIntegrity(entity);

                    refreshed++;
                }
            }

            logger.info("§a[MobManager] §7Protection refresh complete: " + refreshed + " mobs updated");

        } catch (Exception e) {
            logger.severe("§c[MobManager] Protection refresh failed: " + e.getMessage());
        }
    }
    /**
     * Force cleanup (for admin commands)
     */
    public void forceCleanup() {
        logger.info("§6[MobManager] §7Force cleanup requested by admin");
        performStartupCleanup();
    }

    private void clearAllCollections() {
        mobLock.writeLock().lock();
        try {
            activeMobs.clear();
        } finally {
            mobLock.writeLock().unlock();
        }

        damageContributions.clear();
        mobTargets.clear();
        processedEntities.clear();
        respawnTimes.clear();
        mobTypeLastDeath.clear();
        mobSpawnerLocations.clear();
        entityToSpawner.clear();
        failureCounter.clear();
    }
}