package com.rednetty.server.mechanics.world.mobs;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.mechanics.world.mobs.core.EliteMob;
import com.rednetty.server.mechanics.world.mobs.core.MobType;
import com.rednetty.server.mechanics.world.mobs.core.WorldBoss;
import com.rednetty.server.mechanics.world.mobs.spawners.MobSpawner;
import com.rednetty.server.mechanics.world.mobs.spawners.SpawnerMetrics;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * MobManager with simplified hologram integration and  cleanup
 * Key Fixes:
 * - Simplified hologram coordination with CustomMob's new name system
 * - Removed complex hologram state management conflicts
 * - Better damage tracking and health updates
 * - Cleaner mob lifecycle management
 * -  cleanup without hologram conflicts
 */
public class MobManager implements Listener {

    // ================ CONSTANTS ================
    private static final long MIN_RESPAWN_DELAY = 180000L; // 3 minutes
    private static final long MAX_RESPAWN_DELAY = 900000L; // 15 minutes
    private static final double MAX_WANDERING_DISTANCE = 25.0;
    private static final long POSITION_CHECK_INTERVAL = 100L; // 5 seconds

    // ================ CLEANUP CONSTANTS ================
    private static final String[] CUSTOM_MOB_METADATA_KEYS = {
            "type", "tier", "customTier", "elite", "customName", "dropTier", "dropElite",
            "worldboss", "spawner", "spawnerGroup", "LightningMultiplier", "LightningMob",
            "criticalState", "eliteOnly", "mob_unique_id"
    };

    // ================ SINGLETON ================
    private static volatile MobManager instance;

    // ================ CORE MAPS WITH  THREAD SAFETY ================
    private final Map<UUID, CustomMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<String, CustomMob> mobsByUniqueId = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageContributions = new ConcurrentHashMap<>();
    private final Map<Entity, Player> mobTargets = new ConcurrentHashMap<>();
    private final Set<UUID> processedEntities = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> activeSpawning = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> mobSpawnerLocations = new ConcurrentHashMap<>();
    private final Map<String, String> entityToSpawner = new ConcurrentHashMap<>();

    // ================ THREAD SAFETY ================
    private final ReentrantReadWriteLock mobLock = new ReentrantReadWriteLock();

    // ================ RESPAWN TRACKING ================
    private final Map<String, Long> respawnTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> mobTypeLastDeath = new ConcurrentHashMap<>();

    // ================ VALIDATION WITH ELITE SUPPORT ================
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
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean cleanupCompleted = new AtomicBoolean(false);

    // ================ TASKS ================
    private BukkitTask mainTask;
    private BukkitTask cleanupTask;
    private BukkitTask untrackedMobTask;

    // ================ STATISTICS ================
    private final AtomicLong totalMobsSpawned = new AtomicLong(0);
    private final AtomicLong totalMobsRemoved = new AtomicLong(0);
    private final AtomicLong duplicateSpawnsPrevented = new AtomicLong(0);

    /**
     * Constructor with  initialization
     */
    private MobManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.spawner = MobSpawner.getInstance();

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
     *  initialization with startup cleanup
     */
    public void initialize() {
        try {
            logger.info("§6[MobManager] §7Starting  initialization with simplified hologram system...");

            // Clean up any orphaned custom mobs from previous sessions
            performStartupCleanup();

            // Initialize CritManager first
            CritManager.initialize(plugin);

            spawner.initialize();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startTasks();

            logger.info(String.format("§a[MobManager] §7 initialization completed successfully with §e%d §7spawners",
                    spawner.getAllSpawners().size()));

            if (debug) {
                logger.info("§6[MobManager] §7Debug mode enabled with simplified hologram integration");
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initialize mob type to EntityType mapping with proper elite support
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

            // Special/Custom types
            mobTypeMapping.put("imp", EntityType.ZOMBIE);
            mobTypeMapping.put("spectralguard", EntityType.ZOMBIFIED_PIGLIN);
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

            // T5 Elite mappings
            mobTypeMapping.put("meridian", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("pyrion", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("rimeclaw", EntityType.STRAY);
            mobTypeMapping.put("thalassa", EntityType.DROWNED);
            mobTypeMapping.put("nethys", EntityType.WITHER_SKELETON);

            // T6 Elite mapping
            mobTypeMapping.put("apocalypse", EntityType.WITHER_SKELETON);

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

            // Mark elite-only types
            eliteOnlyTypes.addAll(Arrays.asList(
                    "meridian", "pyrion", "rimeclaw", "thalassa", "nethys", "apocalypse",
                    "malachar", "xerathen", "veridiana", "thorgrim", "lysander", "morgana",
                    "vex_elite", "cornelius", "valdris", "seraphina", "arachnia", "karnath",
                    "zephyr", "frozenboss", "frozenelite", "frozengolem", "frostwing",
                    "chronos", "frostking", "bossSkeleton", "bossSkeletonDungeon",
                    "weakskeleton", "skellyDSkeletonGuardian", "spectralguard", "spectralKnight"
            ));

            // Populate valid types set
            validMobTypes.addAll(mobTypeMapping.keySet());

            logger.info("§a[MobManager] §7Initialized " + mobTypeMapping.size() + " mob type mappings");
            logger.info("§a[MobManager] §7Registered " + eliteOnlyTypes.size() + " elite-only types");

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

    // ================ STARTUP CLEANUP SYSTEM ================

    /**
     * Simplified startup cleanup
     */
    private void performStartupCleanup() {
        try {
            logger.info("§6[MobManager] §7Performing startup cleanup of orphaned custom mobs...");

            int totalFound = 0;
            int totalRemoved = 0;

            // First, clean up all holograms to prevent orphaned holograms
            try {
                com.rednetty.server.mechanics.world.holograms.HologramManager.forceCleanupAll();
                logger.info("§6[MobManager] §7Cleaned up all existing holograms");
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error during hologram cleanup: " + e.getMessage());
            }

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

        } catch (Exception e) {
            logger.severe("§c[MobManager] Startup cleanup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
     * Clean up custom mobs in a specific world
     */
    private CleanupResult cleanupWorldCustomMobs(World world, boolean isStartupCleanup) {
        if (world == null) {
            return new CleanupResult(0, 0);
        }

        try {
            int found = 0;
            int removed = 0;

            Collection<LivingEntity> entities = world.getLivingEntities();

            for (LivingEntity entity : entities) {
                try {
                    if (entity instanceof Player) {
                        continue; // Never remove players
                    }

                    if (isCustomMobEntity(entity)) {
                        found++;

                        // During startup cleanup, remove all custom mobs
                        if (isStartupCleanup || isTrackedCustomMob(entity)) {
                            markEntityAsCleanupKill(entity);

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
     * Check if entity is tracked by our system
     */
    private boolean isTrackedCustomMob(LivingEntity entity) {
        if (entity == null) return false;
        return activeMobs.containsKey(entity.getUniqueId());
    }

    /**
     * Mark entity as cleanup kill to prevent drop processing
     */
    private void markEntityAsCleanupKill(LivingEntity entity) {
        if (entity == null) return;

        try {
            entity.setMetadata("cleanup_kill", new FixedMetadataValue(plugin, true));
            entity.setMetadata("no_drops", new FixedMetadataValue(plugin, true));
            entity.setMetadata("system_kill", new FixedMetadataValue(plugin, true));
        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to mark entity as cleanup kill: " + e.getMessage());
        }
    }

    /**
     * Check if an entity was killed by cleanup tasks
     */
    public static boolean isCleanupKill(LivingEntity entity) {
        if (entity == null) return false;

        try {
            return entity.hasMetadata("cleanup_kill") ||
                    entity.hasMetadata("no_drops") ||
                    entity.hasMetadata("system_kill");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Safely remove an entity with simplified cleanup
     */
    private boolean removeEntitySafely(LivingEntity entity, String reason) {
        if (entity == null) return false;

        try {
            UUID entityId = entity.getUniqueId();

            if (debug) {
                logger.info("§6[MobManager] §7Removing entity " + entityId.toString().substring(0, 8) +
                        " (" + entity.getType() + ") - " + reason);
            }

            // Mark as cleanup kill
            markEntityAsCleanupKill(entity);

            // Remove from CritManager first
            if (CritManager.getInstance() != null) {
                CritManager.getInstance().removeCrit(entityId);
            }

            // Remove from our tracking systems
            removeFromAllTrackingSystems(entityId);

            // Clear all custom metadata
            clearEntityMetadata(entity);

            // Clear effects
            clearEntityEffects(entity);

            // Actually remove the entity
            entity.remove();

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
            mobLock.writeLock().lock();
            try {
                CustomMob removedMob = activeMobs.remove(entityId);
                if (removedMob != null && removedMob.getUniqueMobId() != null) {
                    mobsByUniqueId.remove(removedMob.getUniqueMobId());
                }
            } finally {
                mobLock.writeLock().unlock();
            }

            damageContributions.remove(entityId);
            mobSpawnerLocations.remove(entityId);
            entityToSpawner.remove(entityId.toString());
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
            for (PotionEffect effect : entity.getActivePotionEffects()) {
                entity.removePotionEffect(effect.getType());
            }

            entity.setGlowing(false);

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

    // ================ SHUTDOWN SYSTEM ================

    /**
     *  shutdown with comprehensive cleanup
     */
    public void shutdown() {
        try {
            logger.info("§6[MobManager] §7Starting  shutdown with simplified cleanup...");
            isShuttingDown.set(true);

            // Cancel all tasks first
            if (mainTask != null) mainTask.cancel();
            if (cleanupTask != null) cleanupTask.cancel();
            if (untrackedMobTask != null) untrackedMobTask.cancel();

            // Comprehensive entity cleanup
            performShutdownCleanup();

            // Save spawner data
            try {
                spawner.saveSpawners();
                spawner.shutdown();
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error during spawner shutdown: " + e.getMessage());
            }

            // Clean up CritManager
            try {
                if (CritManager.getInstance() != null) {
                    CritManager.getInstance().cleanup();
                }
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error cleaning up CritManager: " + e.getMessage());
            }

            // Final hologram cleanup
            try {
                com.rednetty.server.mechanics.world.holograms.HologramManager.cleanup();
                logger.info("§a[MobManager] §7All holograms cleaned up during shutdown");
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error during hologram cleanup: " + e.getMessage());
            }

            // Clear all collections
            clearAllCollections();

            activeWorldBoss = null;
            cleanupCompleted.set(true);

            logger.info("§a[MobManager] §7 shutdown completed successfully");

        } catch (Exception e) {
            logger.severe("§c[MobManager]  shutdown error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Comprehensive shutdown cleanup
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

    // ================ TASKS ================

    /**
     * Start essential tasks
     */
    private void startTasks() {
        logger.info("§6[MobManager] §7Starting essential tasks...");

        // Main processing task
        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!isShuttingDown.get()) {
                        updateActiveMobs();
                        checkMobPositions();
                        validateEntityTracking();
                    }
                } catch (Exception e) {
                    if (!isShuttingDown.get()) {
                        logger.warning("§c[MobManager] Main task error: " + e.getMessage());
                        if (debug) e.printStackTrace();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 1L); // Every tick for smooth updates

        // Untracked mob cleanup task
        untrackedMobTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!isShuttingDown.get()) {
                        killAllUntrackedMobs();
                    }
                } catch (Exception e) {
                    if (!isShuttingDown.get()) {
                        logger.warning("§c[MobManager] Untracked mob cleanup error: " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 200L, 200L); // Every 10 seconds

        // Cleanup task
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!isShuttingDown.get()) {
                        performCleanup();
                    }
                } catch (Exception e) {
                    if (!isShuttingDown.get()) {
                        logger.warning("§c[MobManager] Cleanup task error: " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every minute

        logger.info("§a[MobManager] §7Essential tasks started successfully");
    }

    // ================ ACTIVE MOBS MANAGEMENT ================

    /**
     * Update active mobs with simplified system
     */
    // In MobManager.java
    private void updateActiveMobs() {
        if (activeWorldBoss != null && activeWorldBoss.isValid()) {
            activeWorldBoss.tick();
        }

        mobLock.writeLock().lock();
        try {
            Iterator<Map.Entry<UUID, CustomMob>> iterator = activeMobs.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, CustomMob> entry = iterator.next();
                CustomMob mob = entry.getValue();

                if (mob == null || !mob.isValid()) {
                    if (mob != null) {
                        mob.remove();  // Trigger hologram cleanup
                        if (mob.getUniqueMobId() != null) {
                            mobsByUniqueId.remove(mob.getUniqueMobId());
                        }
                    }
                    CritManager.getInstance().removeCrit(entry.getKey());
                    iterator.remove();
                    continue;
                }

                // Tick the mob
                mob.tick();
            }
        } finally {
            mobLock.writeLock().unlock();
        }
    }

    // ================ MOB REGISTRATION ================

    /**
     * Register mob with  tracking
     */
    public void registerMob(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) return;

        LivingEntity entity = mob.getEntity();

        mobLock.writeLock().lock();
        try {
            activeMobs.put(entity.getUniqueId(), mob);

            if (mob.getUniqueMobId() != null) {
                mobsByUniqueId.put(mob.getUniqueMobId(), mob);
            }

            totalMobsSpawned.incrementAndGet();
        } finally {
            mobLock.writeLock().unlock();
        }

        if (mob instanceof WorldBoss) {
            activeWorldBoss = (WorldBoss) mob;
        }

        if (debug) {
            logger.info("§a[MobManager] §7Registered mob: " + mob.getType().getId() +
                    " (ID: " + mob.getUniqueMobId() + ")");
        }
    }

    /**
     * Unregister mob with simplified cleanup
     */
    public void unregisterMob(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) return;

        LivingEntity entity = mob.getEntity();
        UUID entityId = entity.getUniqueId();

        mobLock.writeLock().lock();
        try {
            activeMobs.remove(entityId);

            if (mob.getUniqueMobId() != null) {
                mobsByUniqueId.remove(mob.getUniqueMobId());
            }

            totalMobsRemoved.incrementAndGet();
        } finally {
            mobLock.writeLock().unlock();
        }

        CritManager.getInstance().removeCrit(entityId);
        mobSpawnerLocations.remove(entityId);
        entityToSpawner.remove(entityId.toString());

        if (mob instanceof WorldBoss) {
            activeWorldBoss = null;
        }

        if (debug) {
            logger.info("§a[MobManager] §7Unregistered mob: " + mob.getType().getId() +
                    " (ID: " + mob.getUniqueMobId() + ")");
        }
    }

    // ================ DAMAGE PROCESSING ================

    /**
     * Handle mob hit by player with simplified display updates
     */
    public void handleMobHitByPlayer(LivingEntity entity, Player player, double damage) {
        if (entity == null || player == null) return;

        try {
            CustomMob mob = getCustomMob(entity);
            if (mob == null) return;

            // Update damage tracking
            trackDamage(entity, player, damage);

            // Roll for critical hit
            rollForCriticalHit(entity, damage);

            // : Simplified damage handling - mob handles its own display updates
            mob.handleDamage(damage);

            if (debug) {
                logger.info(String.format("§a[MobManager] §7Damage processed for %s (ID: %s): %.1f damage",
                        mob.getType().getId(), mob.getUniqueMobId(), damage));
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Handle mob hit error: " + e.getMessage());
        }
    }

    /**
     * Track damage for mob
     */
    public void trackDamage(LivingEntity entity, Player player, double damage) {
        if (entity == null || player == null) return;

        UUID entityUuid = entity.getUniqueId();
        UUID playerUuid = player.getUniqueId();

        // Track the damage
        damageContributions.computeIfAbsent(entityUuid, k -> new ConcurrentHashMap<>())
                .merge(playerUuid, damage, Double::sum);

        if (debug) {
            logger.info(String.format("§6[MobManager] §7Damage tracked: %.1f from %s to %s",
                    damage, player.getName(), entity.getType()));
        }
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

    // ================ POSITION MONITORING ================

    private void checkMobPositions() {
        try {
            mobLock.readLock().lock();
            List<CustomMob> mobsToCheck;
            try {
                mobsToCheck = new ArrayList<>(activeMobs.values());
            } finally {
                mobLock.readLock().unlock();
            }

            for (CustomMob mob : mobsToCheck) {
                if (mob.isValid()) {
                    handleMobPositionCheck(mob);
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
     * Safe mob teleportation - MAIN THREAD ONLY
     */
    private void teleportMobToSpawner(LivingEntity entity, Location spawnerLoc, String reason) {
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: teleportMobToSpawner called from async thread!");
            Bukkit.getScheduler().runTask(plugin, () -> teleportMobToSpawner(entity, spawnerLoc, reason));
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

            entity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, entity.getLocation().clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                boolean wasAware = mob.isAware();
                mob.setAware(false);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isEntityValidAndTracked(entity)) {
                        mob.setAware(wasAware);
                    }
                }, 10L);
            }

            entity.teleport(safeLoc);
            entity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, safeLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Teleport error: " + e.getMessage());
        }
    }

    // ================ CRITICAL HIT DELEGATION ================

    /**
     * Roll for critical hit using CritManager
     */
    public void rollForCriticalHit(LivingEntity entity, double damage) {
        try {
            CustomMob customMob = getCustomMob(entity);
            if (customMob != null) {
                CritManager.getInstance().initiateCrit(customMob);
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Critical roll error: " + e.getMessage());
        }
    }

    // ================ ENTITY VALIDATION ================

    // In MobManager.java
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
                        CustomMob mob = activeMobs.get(invalidId);
                        if (mob != null) {
                            mob.remove();  // Trigger hologram cleanup
                            if (mob.getUniqueMobId() != null) {
                                mobsByUniqueId.remove(mob.getUniqueMobId());
                            }
                        }
                        activeMobs.remove(invalidId);
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
            return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        } catch (Exception e) {
            return false;
        }
    }

    // ================ CLEANUP OPERATIONS ================

    private void performCleanup() {
        try {
            cleanupDamageTracking();
            cleanupEntityMappings();
            cleanupMobLocations();
            cleanupFailureCounters();
            processedEntities.clear();
            cleanupUniqueIdMapping();

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
                        return true;
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

    private void cleanupUniqueIdMapping() {
        Set<String> invalidIds = mobsByUniqueId.keySet().stream()
                .filter(id -> {
                    CustomMob mob = mobsByUniqueId.get(id);
                    return mob == null || !mob.isValid();
                })
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidIds.forEach(mobsByUniqueId::remove);
    }

    private void cleanupFailureCounters() {
        failureCounter.entrySet().removeIf(entry -> entry.getValue() > 50);
    }

    private boolean isEntityValid(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        return entity != null && entity.isValid() && !entity.isDead();
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

    public CustomMob getCustomMobByUniqueId(String uniqueMobId) {
        if (uniqueMobId == null) return null;
        return mobsByUniqueId.get(uniqueMobId);
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
            handleMobHitByPlayer(entity, damager, damage);

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

    // ================ DEATH HANDLING ================

    // In MobManager.java
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            UUID entityId = entity.getUniqueId();

            if (entity instanceof Player || processedEntities.contains(entityId)) {
                return;
            }

            processedEntities.add(entityId);

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

            // Find and notify spawner
            Location spawnerLoc = findSpawnerForMob(entity);
            if (spawnerLoc != null) {
                spawner.registerMobDeath(spawnerLoc, entityId);

                if (debug) {
                    logger.info("§a[MobManager] §7Notified spawner at " + formatLocation(spawnerLoc) +
                            " of mob death");
                }
            }

            // Clean up tracking data
            cleanupMobData(entityId);

            // Ensure hologram cleanup on death event
            CustomMob mob = getCustomMob(entity);
            if (mob != null) {
                mob.remove();
            }
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
     *  spawner finding for dead mobs
     */
    private Location findSpawnerForMob(LivingEntity entity) {
        if (entity == null) return null;

        UUID entityId = entity.getUniqueId();

        try {
            // Check cached spawner locations
            if (mobSpawnerLocations.containsKey(entityId)) {
                Location cachedLoc = mobSpawnerLocations.get(entityId);
                if (isValidSpawnerLocation(cachedLoc)) {
                    return cachedLoc;
                } else {
                    mobSpawnerLocations.remove(entityId);
                }
            }

            // Check spawner metadata
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

            // Search for nearest spawner
            Location mobLocation = entity.getLocation();
            return spawner.findNearestSpawner(mobLocation, mobRespawnDistanceCheck);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error finding spawner for mob: " + e.getMessage());
            return null;
        }
    }

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

    private void cleanupMobData(UUID entityId) {
        try {
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

    // ================ MISC EVENT HANDLERS ================

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity)) {
                return;
            }

            if (entity.hasMetadata("type") || isCustomMobEntity(entity)) {
                event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomMobFireDamage(EntityDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity)) {
                return;
            }

            if (event.getCause() != EntityDamageEvent.DamageCause.FIRE &&
                    event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK &&
                    event.getCause() != EntityDamageEvent.DamageCause.LAVA) {
                return;
            }

            if (entity.hasMetadata("type") || isCustomMobEntity(entity)) {
                event.setCancelled(true);
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
     *  custom mob validation during cleanup
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
                if (name.contains("§") ||
                        name.contains("T1") || name.contains("T2") || name.contains("T3") ||
                        name.contains("T4") || name.contains("T5") || name.contains("T6") ||
                        name.contains("Elite") || name.contains("Boss")) {
                    return true;
                }
            }

            // Check for equipment with no drop chance
            if (entity.getEquipment() != null) {
                ItemStack weapon = entity.getEquipment().getItemInMainHand();
                if (weapon != null && weapon.getType() != Material.AIR) {
                    if (entity.getEquipment().getItemInMainHandDropChance() == 0) {
                        return true;
                    }

                    if (weapon.hasItemMeta() && weapon.getItemMeta().isUnbreakable()) {
                        return true;
                    }
                }
            }

            // Check for fire resistance
            if (entity.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
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

    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("===  MobManager Diagnostic Info ===\n");
        info.append("Valid Mob Types: ").append(validMobTypes.size()).append("\n");
        info.append("Mob Type Mappings: ").append(mobTypeMapping.size()).append("\n");
        info.append("Elite-Only Types: ").append(eliteOnlyTypes.size()).append("\n");
        info.append("Active Mobs: ").append(activeMobs.size()).append("\n");
        info.append("Unique ID Tracked Mobs: ").append(mobsByUniqueId.size()).append("\n");
        info.append("Total Mobs Spawned: ").append(totalMobsSpawned.get()).append("\n");
        info.append("Total Mobs Removed: ").append(totalMobsRemoved.get()).append("\n");
        info.append("Duplicate Spawns Prevented: ").append(duplicateSpawnsPrevented.get()).append("\n");
        info.append("Active Spawning Operations: ").append(activeSpawning.size()).append("\n");

        return info.toString();
    }

    public String getCleanupStatus() {
        return String.format(" Cleanup Status: shutting_down=%s, cleanup_completed=%s, active_mobs=%d, unique_tracked=%d",
                isShuttingDown.get(), cleanupCompleted.get(), activeMobs.size(), mobsByUniqueId.size());
    }

    public void forceCleanup() {
        logger.info("§6[MobManager] §7Force cleanup requested by admin");
        performStartupCleanup();
        com.rednetty.server.mechanics.world.holograms.HologramManager.cleanup();
        performCleanup();
    }

    private void clearAllCollections() {
        mobLock.writeLock().lock();
        try {
            activeMobs.clear();
            mobsByUniqueId.clear();
        } finally {
            mobLock.writeLock().unlock();
        }

        damageContributions.clear();
        mobTargets.clear();
        processedEntities.clear();
        activeSpawning.clear();
        respawnTimes.clear();
        mobTypeLastDeath.clear();
        mobSpawnerLocations.clear();
        entityToSpawner.clear();
        failureCounter.clear();
    }

    // ================ UNTRACKED MOB CLEANUP ================

    public int killAllUntrackedMobs() {
        logger.info("§6[MobManager] §7Killing all untracked mobs in all worlds");

        int totalKilled = 0;
        for (World world : Bukkit.getWorlds()) {
            try {
                int killed = killUntrackedMobsInWorld(world);
                totalKilled += killed;
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error killing untracked mobs in world " +
                        world.getName() + ": " + e.getMessage());
            }
        }

        logger.info("§a[MobManager] §7Total untracked mobs killed: " + totalKilled);
        return totalKilled;
    }

    public int killUntrackedMobsInWorld(World world) {
        if (world == null) {
            logger.warning("§c[MobManager] Cannot kill untracked mobs - world is null");
            return 0;
        }

        logger.info("§6[MobManager] §7Killing untracked mobs in world: " + world.getName());

        int killed = 0;
        Set<UUID> trackedMobIds = new HashSet<>();

        mobLock.readLock().lock();
        try {
            for (CustomMob mob : activeMobs.values()) {
                if (mob != null && mob.getEntity() != null) {
                    trackedMobIds.add(mob.getEntity().getUniqueId());
                }
            }
        } finally {
            mobLock.readLock().unlock();
        }

        try {
            Collection<LivingEntity> entities = world.getLivingEntities();

            for (LivingEntity entity : entities) {
                if (entity instanceof Player || entity instanceof Horse || entity instanceof ArmorStand) {
                    continue;
                }

                if (trackedMobIds.contains(entity.getUniqueId())) {
                    if (debug) {
                        logger.info("§6[MobManager] §7Skipping tracked mob: " + entity.getType() +
                                " ID: " + entity.getUniqueId().toString().substring(0, 8));
                    }
                    continue;
                }

                try {
                    markEntityAsCleanupKill(entity);

                    if (debug) {
                        logger.info("§6[MobManager] §7Killing untracked mob: " + entity.getType() +
                                " at " + formatLocation(entity.getLocation()) +
                                " ID: " + entity.getUniqueId().toString().substring(0, 8));
                    }

                    entity.setHealth(0);
                    entity.damage(999999);
                    entity.remove();
                    killed++;

                } catch (Exception e) {
                    if (debug) {
                        logger.warning("§c[MobManager] Failed to kill untracked entity " +
                                entity.getType() + ": " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error getting entities from world " + world.getName() +
                    ": " + e.getMessage());
        }

        logger.info("§a[MobManager] §7Killed " + killed + " untracked mobs in world: " + world.getName() +
                " (preserved " + trackedMobIds.size() + " tracked mobs)");

        return killed;
    }

    // ================ MOB SPAWNING ================

    public LivingEntity spawnMobFromSpawner(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: spawnMobFromSpawner called from async thread!");
            Bukkit.getScheduler().runTask(plugin, () -> spawnMobFromSpawner(location, type, tier, elite));
            return null;
        }

        if (isShuttingDown.get()) {
            return null;
        }

        if (!isValidSpawnRequest(location, type, tier, elite)) {
            if (debug) {
                logger.warning("§c[MobManager] Invalid spawn request: " + type + " T" + tier +
                        (elite ? "+" : "") + " at " + formatLocation(location));
            }
            return null;
        }

        String normalizedType = normalizeMobType(type);
        if (normalizedType == null) {
            incrementFailureCount(type);
            logger.warning("§c[MobManager] Failed to normalize mob type: " + type);
            return null;
        }

        String spawnKey = normalizedType + "_" + tier + "_" + elite + "_" + formatLocation(location);
        if (activeSpawning.contains(spawnKey)) {
            duplicateSpawnsPrevented.incrementAndGet();
            if (debug) {
                logger.info("§6[MobManager] §7Prevented duplicate spawn: " + spawnKey);
            }
            return null;
        }

        try {
            activeSpawning.add(spawnKey);

            if (!ensureChunkLoadedForSpawning(location)) {
                incrementFailureCount(normalizedType);
                logger.warning("§c[MobManager] Chunk loading failed for spawn at " + formatLocation(location));
                return null;
            }

            Location spawnLoc = getSafeSpawnLocation(location);
            if (spawnLoc == null) {
                incrementFailureCount(normalizedType);
                logger.warning("§c[MobManager] No safe spawn location found near " + formatLocation(location));
                return null;
            }

            LivingEntity entity = createEntityWithValidation(spawnLoc, normalizedType, tier, elite);

            if (entity != null) {
                registerSpawnedMob(entity, location);

                if (debug) {
                    logger.info("§a[MobManager] §7Successfully spawned " + normalizedType + " T" + tier +
                            (elite ? "+" : "") + " at " + formatLocation(spawnLoc) +
                            " with ID: " + entity.getUniqueId().toString().substring(0, 8));
                }

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
        } finally {
            activeSpawning.remove(spawnKey);
        }

        return null;
    }

    private boolean ensureChunkLoadedForSpawning(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        try {
            World world = location.getWorld();
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;

            if (world.isChunkLoaded(chunkX, chunkZ)) {
                return true;
            }

            boolean loaded = world.loadChunk(chunkX, chunkZ, true);
            if (!loaded) {
                logger.warning("§c[MobManager] Failed to load chunk at " + chunkX + "," + chunkZ);
                return false;
            }

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                logger.warning("§c[MobManager] Chunk loading verification failed at " + chunkX + "," + chunkZ);
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warning("§c[MobManager] Chunk loading error: " + e.getMessage());
            return false;
        }
    }

    private String normalizeMobType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }

        String normalized = type.toLowerCase().trim();

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

        if (mobTypeMapping.containsKey(normalized)) {
            return normalized;
        }

        if (MobType.isValidType(normalized)) {
            return normalized;
        }

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

    private LivingEntity createEntityWithValidation(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: createEntityWithValidation called from async thread!");
            return null;
        }

        try {
            if (debug) {
                logger.info("§6[MobManager] §7Creating entity: " + type + " T" + tier + (elite ? "+" : "") +
                        " (Elite-only: " + eliteOnlyTypes.contains(type) + ")");
            }

            LivingEntity entity = null;

            if (isMobTypeSystemAvailable()) {
                entity = createEntityUsingMobTypeSystem(location, type, tier, elite);
                if (entity != null) {
                    if (debug) {
                        logger.info("§a[MobManager] §7Entity created via MobType system: " + type);
                    }
                    return entity;
                }
            }

            entity = createEntityWithMultipleAttempts(location, type, tier, elite);
            if (entity != null) {
                if (debug) {
                    logger.info("§a[MobManager] §7Entity created via direct spawning: " + type);
                }
                return entity;
            }

            return createEntityFinalFallback(location, type, tier, elite);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Entity creation error for " + type + ": " + e.getMessage());
            if (debug) e.printStackTrace();
            return null;
        }
    }

    private LivingEntity createEntityWithMultipleAttempts(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("§c[MobManager] CRITICAL: createEntityWithMultipleAttempts called from async thread!");
            return null;
        }

        EntityType entityType = mobTypeMapping.get(type);
        if (entityType == null) {
            logger.warning("§c[MobManager] No EntityType mapping for: " + type);
            return null;
        }

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

                if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    ensureChunkLoadedForSpawning(location);
                }

                Entity spawnedEntity = world.spawnEntity(location, entityType);

                if (spawnedEntity instanceof LivingEntity entity) {
                    if (entity.isValid() && !entity.isDead()) {
                        configureBasicEntity(entity, type, tier, elite);
                        configureEntityAppearance(entity, type, tier, elite);
                        configureEntityEquipment(entity, tier, elite);

                        if (debug && attempt > 1) {
                            logger.info("§6[MobManager] §7Entity creation succeeded on attempt " + attempt + " for " + type);
                        }

                        return entity;
                    } else {
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

                if (attempt < 5) {
                    try {
                        Thread.sleep(50);
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

    private boolean isLivingEntityType(EntityType entityType) {
        try {
            Class<?> entityClass = entityType.getEntityClass();
            return entityClass != null && LivingEntity.class.isAssignableFrom(entityClass);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isMobTypeSystemAvailable() {
        try {
            Class.forName("com.rednetty.server.mechanics.world.mobs.core.MobType");
            return MobType.getById("skeleton") != null;
        } catch (Exception e) {
            if (debug) {
                logger.warning("§6[MobManager] MobType system not available, using fallback: " + e.getMessage());
            }
            return false;
        }
    }

    private LivingEntity createEntityUsingMobTypeSystem(Location location, String type, int tier, boolean elite) {
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

            if (eliteOnlyTypes.contains(type) && !elite) {
                logger.warning("§c[MobManager] " + type + " is an elite-only type but elite=false was specified");
                return null;
            }

            CustomMob customMob;

            if (elite || mobType.isElite()) {
                customMob = new EliteMob(mobType, tier);
                if (debug) {
                    logger.info("§d[MobManager] §7Creating EliteMob for " + type + " T" + tier);
                }
            } else {
                customMob = new CustomMob(mobType, tier, false);
                if (debug) {
                    logger.info("§a[MobManager] §7Creating CustomMob for " + type + " T" + tier);
                }
            }

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

            configureBasicEntity(entity, type, tier, elite);

            return entity;

        } catch (Exception e) {
            logger.warning("§c[MobManager] MobType system creation failed for " + type + ": " + e.getMessage());
            if (debug) e.printStackTrace();
            return null;
        }
    }

    private LivingEntity createEntityFinalFallback(Location location, String type, int tier, boolean elite) {
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

            EntityType fallbackType = getFallbackEntityType(type);
            Entity spawnedEntity = world.spawnEntity(location, fallbackType);

            if (spawnedEntity instanceof LivingEntity entity) {
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

    private EntityType getFallbackEntityType(String type) {
        EntityType mapped = mobTypeMapping.get(type);
        if (mapped != null) {
            return mapped;
        }

        if (eliteOnlyTypes.contains(type)) {
            return switch (type) {
                case "meridian" -> EntityType.WARDEN;
                case "pyrion", "nethys", "valdris", "morgana", "zephyr", "apocalypse" -> EntityType.WITHER_SKELETON;
                case "rimeclaw" -> EntityType.STRAY;
                case "thalassa" -> EntityType.DROWNED;
                case "xerathen", "karnath", "seraphina", "vex_elite" -> EntityType.ZOMBIE;
                case "veridiana" -> EntityType.HUSK;
                case "cornelius" -> EntityType.ZOMBIE_VILLAGER;
                case "arachnia" -> EntityType.SPIDER;
                default -> EntityType.SKELETON;
            };
        }

        return EntityType.ZOMBIE;
    }

    private void configureBasicEntity(LivingEntity entity, String type, int tier, boolean elite) {
        try {
            entity.setMetadata("type", new FixedMetadataValue(plugin, type));
            entity.setMetadata("tier", new FixedMetadataValue(plugin, tier));
            entity.setMetadata("elite", new FixedMetadataValue(plugin, elite));

            if (entity instanceof Mob) {
                ((Mob) entity).setAware(true);
            }

            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            entity.setCustomNameVisible(true);

            configureEntityAttributes(entity, tier, elite);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure basic entity: " + e.getMessage());
        }
    }

    private void configureEntityAttributes(LivingEntity entity, int tier, boolean elite) {
        try {
            double healthMultiplier = 1.0 + (tier * 0.5);
            if (elite) healthMultiplier *= 1.5;

            double maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue() * healthMultiplier;
            entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            entity.setHealth(maxHealth);

            if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                double damageMultiplier = 1.0 + (tier * 0.3);
                if (elite) damageMultiplier *= 1.4;
                entity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(
                        entity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getBaseValue() * damageMultiplier);
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure entity attributes: " + e.getMessage());
        }
    }

    private void configureEntityAppearance(LivingEntity entity, String type, int tier, boolean elite) {
        try {
            String nameColor = getNameColor(tier, elite);
            String displayName = nameColor + MobType.getById(type).getFormattedName(tier);
            if (elite) displayName += " §e[Elite]";
            displayName += " §7T" + tier;

            entity.setCustomName(displayName);
        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure entity appearance: " + e.getMessage());
        }
    }

    private String getNameColor(int tier, boolean elite) {
        if (elite) {
            return switch (tier) {
                case 1 -> "§f";
                case 2 -> "§a";
                case 3 -> "§b";
                case 4 -> "§d";
                case 5 -> "§e";
                case 6 -> "§6";
                default -> "§f";
            };
        } else {
            return switch (tier) {
                case 1 -> "§7";
                case 2 -> "§2";
                case 3 -> "§3";
                case 4 -> "§5";
                case 5 -> "§6";
                case 6 -> "§c";
                default -> "§7";
            };
        }
    }

    private void configureEntityEquipment(LivingEntity entity, int tier, boolean elite) {
        try {
            if (entity.getEquipment() == null) return;

            ItemStack weapon = createMobWeapon(tier, elite);
            entity.getEquipment().setItemInMainHand(weapon);
            entity.getEquipment().setItemInMainHandDropChance(0.0f);

            applyEntityArmor(entity, tier, elite);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure entity equipment: " + e.getMessage());
        }
    }

    private ItemStack createMobWeapon(int tier, boolean elite) {
        try {
            Material weaponMaterial = switch (tier) {
                case 1, 2 -> Material.STONE_SWORD;
                case 3 -> Material.IRON_SWORD;
                case 4 -> Material.DIAMOND_SWORD;
                case 5 -> Material.GOLDEN_SWORD;
                case 6 -> Material.NETHERITE_SWORD;
                default -> Material.WOODEN_SWORD;
            };

            ItemStack weapon = new ItemStack(weaponMaterial);
            ItemMeta meta = weapon.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Tier " + tier);
                String rarityLine = elite ? ChatColor.YELLOW + "Elite Weapon" : ChatColor.GRAY + "Common";
                lore.add(rarityLine);

                meta.setLore(lore);
                meta.setUnbreakable(true);

                String displayName = generateMobWeaponName(weaponMaterial, tier, elite);
                meta.setDisplayName(displayName);

                weapon.setItemMeta(meta);

                if (tier == 6) {
                    weapon = applyT6NetheriteWeaponEffects(weapon, elite);
                }
            }

            return weapon;

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to create mob weapon: " + e.getMessage());
            return new ItemStack(Material.WOODEN_SWORD);
        }
    }

    private String generateMobWeaponName(Material weaponType, int tier, boolean elite) {
        ChatColor tierColor = switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.GOLD;
            default -> ChatColor.WHITE;
        };

        String prefix = elite ? (tierColor.toString() + ChatColor.BOLD) : tierColor.toString();

        if (tier == 6) {
            return prefix + "Nether Forged Blade";
        } else {
            String baseName = weaponType.name().replace("_SWORD", "").replace("_", " ");
            baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
            return prefix + baseName + " Sword";
        }
    }

    private void applyEntityArmor(LivingEntity entity, int tier, boolean elite) {
        try {
            if (entity.getEquipment() == null) return;

            String armorPrefix = switch (tier) {
                case 1, 2 -> "LEATHER";
                case 3 -> "IRON";
                case 4 -> "DIAMOND";
                case 5 -> "GOLDEN";
                case 6 -> "NETHERITE";
                default -> "LEATHER";
            };

            Material[] armorMaterials = {
                    Material.valueOf(armorPrefix + "_HELMET"),
                    Material.valueOf(armorPrefix + "_CHESTPLATE"),
                    Material.valueOf(armorPrefix + "_LEGGINGS"),
                    Material.valueOf(armorPrefix + "_BOOTS")
            };

            ItemStack[] armorPieces = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                try {
                    armorPieces[i] = new ItemStack(armorMaterials[i]);

                    if (tier == 6) {
                        armorPieces[i] = applyT6NetheriteArmorEffects(armorPieces[i], elite);
                    } else {
                        ItemMeta armorMeta = armorPieces[i].getItemMeta();
                        if (armorMeta != null) {
                            armorMeta.setUnbreakable(true);
                            armorPieces[i].setItemMeta(armorMeta);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("§c[MobManager] Invalid armor material: " + armorPrefix + " for piece " + i);
                }
            }

            if (armorPieces[0] != null) entity.getEquipment().setHelmet(armorPieces[0]);
            if (armorPieces[1] != null) entity.getEquipment().setChestplate(armorPieces[1]);
            if (armorPieces[2] != null) entity.getEquipment().setLeggings(armorPieces[2]);
            if (armorPieces[3] != null) entity.getEquipment().setBoots(armorPieces[3]);

            entity.getEquipment().setHelmetDropChance(0.0f);
            entity.getEquipment().setChestplateDropChance(0.0f);
            entity.getEquipment().setLeggingsDropChance(0.0f);
            entity.getEquipment().setBootsDropChance(0.0f);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to apply entity armor: " + e.getMessage());
        }
    }

    private ItemStack applyT6NetheriteWeaponEffects(ItemStack weapon, boolean elite) {
        if (weapon == null || weapon.getType() == Material.AIR) {
            return weapon;
        }

        try {
            ItemMeta meta = weapon.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);

                if (elite && !meta.hasEnchants()) {
                    weapon.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
                }

                String weaponName = generateMobT6WeaponName(weapon.getType(), elite);
                meta.setDisplayName(weaponName);

                weapon.setItemMeta(meta);
            }

            if (debug) {
                logger.info("§6[MobManager] §7Applied T6 Netherite weapon effects to " + weapon.getType());
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to apply T6 weapon effects: " + e.getMessage());
        }

        return weapon;
    }

    private ItemStack applyT6NetheriteArmorEffects(ItemStack armor, boolean elite) {
        if (armor == null || armor.getType() == Material.AIR) {
            return armor;
        }

        try {
            ItemMeta meta = armor.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);

                String armorName = generateMobT6ArmorName(armor.getType(), elite);
                meta.setDisplayName(armorName);

                armor.setItemMeta(meta);

                armor = applyMobNetheriteGoldTrim(armor);
            }

            if (debug) {
                logger.info("§6[MobManager] §7Applied T6 Netherite armor effects with gold trim to " + armor.getType());
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to apply T6 armor effects: " + e.getMessage());
        }

        return armor;
    }

    private ItemStack applyMobNetheriteGoldTrim(ItemStack item) {
        if (item.getItemMeta() instanceof ArmorMeta) {
            try {
                ArmorMeta armorMeta = (ArmorMeta) item.getItemMeta();

                ArmorTrim goldTrim = new ArmorTrim(
                        TrimMaterial.GOLD,
                        TrimPattern.EYE
                );

                armorMeta.setTrim(goldTrim);
                item.setItemMeta(armorMeta);

                if (debug) {
                    logger.info("§6[MobManager] §7Applied gold trim to T6 mob Netherite armor: " + item.getType());
                }
            } catch (Exception e) {
                logger.warning("§c[MobManager] Failed to apply gold trim to mob Netherite armor: " + e.getMessage());
            }
        }
        return item;
    }

    private String generateMobT6WeaponName(Material weaponType, boolean elite) {
        String prefix = elite ? "§5§lElite " : "§5";

        return switch (weaponType) {
            case NETHERITE_SWORD -> prefix + "Nether Forged Blade";
            case NETHERITE_AXE -> prefix + "Nether Forged War Axe";
            case NETHERITE_PICKAXE -> prefix + "Nether Forged Crusher";
            case NETHERITE_SHOVEL -> prefix + "Nether Forged Spade";
            case NETHERITE_HOE -> prefix + "Nether Forged Scythe";
            default -> {
                String baseName = weaponType.name().replace("NETHERITE_", "").replace("_", " ");
                baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
                yield prefix + "Nether Forged " + baseName;
            }
        };
    }

    private String generateMobT6ArmorName(Material armorType, boolean elite) {
        String prefix = elite ? "§5§lElite " : "§5";

        return switch (armorType) {
            case NETHERITE_HELMET -> prefix + "Nether Forged Crown";
            case NETHERITE_CHESTPLATE -> prefix + "Nether Forged Chestguard";
            case NETHERITE_LEGGINGS -> prefix + "Nether Forged Legguards";
            case NETHERITE_BOOTS -> prefix + "Nether Forged Warboots";
            default -> {
                String baseName = armorType.name().replace("NETHERITE_", "").replace("_", " ");
                baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
                yield prefix + "Nether Forged " + baseName;
            }
        };
    }

    private Location getSafeSpawnLocation(Location target) {
        if (target == null || target.getWorld() == null) {
            return null;
        }

        if (isSafeSpawnLocation(target)) {
            return target.clone().add(0.5, 0, 0.5);
        }

        for (int attempts = 0; attempts < 20; attempts++) {
            double x = target.getX() + (Math.random() * 10 - 5);
            double z = target.getZ() + (Math.random() * 10 - 5);
            double y = target.getY();

            for (int yOffset = 0; yOffset <= 5; yOffset++) {
                Location candidate = new Location(target.getWorld(), x, y + yOffset, z);
                if (isSafeSpawnLocation(candidate)) {
                    return candidate;
                }
            }
        }

        return target.clone().add(0.5, 5, 0.5);
    }

    private boolean isSafeSpawnLocation(Location location) {
        try {
            if (location == null || location.getWorld() == null) {
                return false;
            }

            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return false;
            }

            if (location.getY() < location.getWorld().getMinHeight()) {
                return false;
            }

            if (location.getY() > location.getWorld().getMaxHeight() - 5) {
                return false;
            }

            Material currentBlock = location.getBlock().getType();
            if (currentBlock.isSolid()) {
                return false;
            }

            Material blockAbove = location.clone().add(0, 1, 0).getBlock().getType();
            if (blockAbove.isSolid()) {
                return false;
            }

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

    private boolean isDangerousBlock(Material material) {
        return switch (material) {
            case LAVA, FIRE, MAGMA_BLOCK, CACTUS, SWEET_BERRY_BUSH -> true;
            default -> false;
        };
    }

    private void registerSpawnedMob(LivingEntity entity, Location spawnerLocation) {
        try {
            UUID entityId = entity.getUniqueId();

            Location nearestSpawner = findNearestSpawner(spawnerLocation, 5.0);
            if (nearestSpawner != null) {
                mobSpawnerLocations.put(entityId, nearestSpawner);
                String spawnerId = generateSpawnerId(nearestSpawner);
                entity.setMetadata("spawner", new FixedMetadataValue(plugin, spawnerId));
                entityToSpawner.put(entityId.toString(), spawnerId);
            }

            CustomMob customMob = getCustomMob(entity);
            if (customMob != null) {
                registerMob(customMob);
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error registering spawned mob: " + e.getMessage());
        }
    }

    private void incrementFailureCount(String mobType) {
        int count = failureCounter.getOrDefault(mobType, 0) + 1;
        failureCounter.put(mobType, count);

        if (count > 10 && count % 5 == 0) {
            logger.warning("§c[MobManager] High failure count for " + mobType + ": " + count + " failures");
        }
    }

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

        if (eliteOnlyTypes.contains(type.toLowerCase()) && !elite) {
            if (debug) {
                logger.warning("§c[MobManager] " + type + " is an elite-only type but elite=false");
            }
            return false;
        }

        return true;
    }

    public boolean canSpawnerSpawnMob(String type, int tier, boolean elite) {
        try {
            String normalizedType = normalizeMobType(type);
            if (normalizedType == null) {
                return false;
            }

            if (!mobTypeMapping.containsKey(normalizedType) && !MobType.isValidType(normalizedType)) {
                return false;
            }

            if (tier < 1 || tier > 6) {
                return false;
            }

            if (tier > 5 && !YakRealms.isT6Enabled()) {
                return false;
            }

            if (eliteOnlyTypes.contains(normalizedType) && !elite) {
                return false;
            }

            int failures = failureCounter.getOrDefault(normalizedType, 0);
            if (failures > 20) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warning("§c[MobManager] Error checking spawner mob validity: " + e.getMessage());
            return false;
        }
    }
}