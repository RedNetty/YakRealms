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
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 *  MobManager with comprehensive cleanup system, Tier 6 Netherite support, and FIXED hologram system
 * -  entity cleanup on shutdown - removes ALL custom mobs from worlds
 * - Startup cleanup - removes orphaned entities before starting
 * - Better coordination with CritManager for cleanup
 * - Systematic world scanning for custom entities
 * - Robust entity removal that ensures entities are deleted from world
 * - All original functionality preserved
 * - CRITICAL FIX: Mark cleanup kills to prevent unwanted drops
 * - : Full Tier 6 Netherite equipment support with gold trim
 * - Enhanced hologram integration with hidden entity names
 * - FIXED: Hologram trail prevention and proper cleanup
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

    // ================  VALIDATION WITH ELITE SUPPORT ================
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
    private BukkitTask untrackedMobTask;

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
     *  Initialize mob type to EntityType mapping with proper elite support
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
            mobTypeMapping.put("meridian", EntityType.WITHER_SKELETON);
            mobTypeMapping.put("pyrion", EntityType.WITHER_SKELETON);

            mobTypeMapping.put("rimeclaw", EntityType.STRAY);
            mobTypeMapping.put("thalassa", EntityType.DROWNED);
            mobTypeMapping.put("nethys", EntityType.WITHER_SKELETON);

            //T6 ELITE MAPPING:
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

    // ================  INITIALIZATION WITH CLEANUP ================

    /**
     *   initialization with startup cleanup to remove orphaned entities
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
                logger.info("§6[MobManager] §7Debug mode enabled with  tracking");
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================ STARTUP CLEANUP SYSTEM ================

    /**
     *  Comprehensive startup cleanup to remove orphaned custom mobs
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
     *  Clean up custom mobs in a specific world
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
                            // CRITICAL FIX: Mark as cleanup kill before removal
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

        // Check if it's in our active mobs list
        return activeMobs.containsKey(entity.getUniqueId());
    }

    /**
     * CRITICAL FIX: Mark entity as cleanup kill to prevent drop processing
     * This prevents the DropsHandler from processing drops for entities killed by cleanup tasks
     */
    private void markEntityAsCleanupKill(LivingEntity entity) {
        if (entity == null) return;

        try {
            // Mark with metadata that it's a cleanup kill
            entity.setMetadata("cleanup_kill", new FixedMetadataValue(plugin, true));
            entity.setMetadata("no_drops", new FixedMetadataValue(plugin, true));
            entity.setMetadata("system_kill", new FixedMetadataValue(plugin, true));

            if (debug) {
                logger.fine("§6[MobManager] §7Marked entity " + entity.getType() +
                        " as cleanup kill (no drops will be processed)");
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to mark entity as cleanup kill: " + e.getMessage());
        }
    }

    /**
     * Check if an entity was killed by cleanup tasks
     * This can be used by other systems to determine if an entity death should be processed
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
     *  Safely remove an entity with comprehensive cleanup
     *  to mark cleanup kills to prevent drop processing
     */
    private boolean removeEntitySafely(LivingEntity entity, String reason) {
        if (entity == null) return false;

        try {
            UUID entityId = entity.getUniqueId();

            if (debug) {
                logger.info("§6[MobManager] §7Removing entity " + entityId.toString().substring(0, 8) +
                        " (" + entity.getType() + ") - " + reason);
            }

            // CRITICAL FIX: Mark as cleanup kill to prevent drop processing
            markEntityAsCleanupKill(entity);

            // 0. CRITICAL: Remove hologram first
            CustomMob customMob = getCustomMob(entity);
            if (customMob != null) {
                customMob.removeHologram();
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

    // ================  SHUTDOWN SYSTEM ================

    /**
     *   shutdown with comprehensive cleanup including holograms
     * FIXED: Now ensures all holograms are properly cleaned up
     */
    public void shutdown() {
        try {
            logger.info("§6[MobManager] §7Starting  shutdown with comprehensive cleanup...");
            isShuttingDown = true;

            // 1. Cancel all tasks first
            if (mainTask != null) mainTask.cancel();
            if (cleanupTask != null) cleanupTask.cancel();
            if (untrackedMobTask != null) untrackedMobTask.cancel();

            // 2. CRITICAL: Remove all mob holograms before removing entities
            cleanupAllMobHolograms();

            // 3. Comprehensive entity cleanup
            performShutdownCleanup();

            // 4. Save spawner data
            try {
                spawner.saveSpawners();
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error saving spawners during shutdown: " + e.getMessage());
            }

            // 5. Shutdown spawner system
            try {
                spawner.shutdown();
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error shutting down spawner: " + e.getMessage());
            }

            // 6. Clean up CritManager
            try {
                if (CritManager.getInstance() != null) {
                    CritManager.getInstance().cleanup();
                }
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error cleaning up CritManager: " + e.getMessage());
            }

            // 7. CRITICAL: Final hologram cleanup
            try {
                com.rednetty.server.mechanics.world.holograms.HologramManager.cleanup();
                logger.info("§a[MobManager] §7All holograms cleaned up during shutdown");
            } catch (Exception e) {
                logger.warning("§c[MobManager] Error during hologram cleanup: " + e.getMessage());
            }

            // 8. Clear all collections
            clearAllCollections();

            // 9. Reset state
            activeWorldBoss = null;
            cleanupCompleted = true;

            logger.info("§a[MobManager] §7 shutdown completed successfully");

        } catch (Exception e) {
            logger.severe("§c[MobManager]  shutdown error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * FIXED: Clean up all mob holograms before removing entities
     * This prevents hologram trails during shutdown
     */
    private void cleanupAllMobHolograms() {
        try {
            logger.info("§6[MobManager] §7Cleaning up all mob holograms...");

            int hologramsRemoved = 0;

            mobLock.readLock().lock();
            List<CustomMob> mobsToCleanup;
            try {
                mobsToCleanup = new ArrayList<>(activeMobs.values());
            } finally {
                mobLock.readLock().unlock();
            }

            // Remove holograms from all tracked mobs
            for (CustomMob mob : mobsToCleanup) {
                if (mob != null && mob.isValid()) {
                    try {
                        mob.removeHologram();
                        hologramsRemoved++;
                    } catch (Exception e) {
                        logger.warning("§c[MobManager] Error removing hologram for mob: " + e.getMessage());
                    }
                }
            }

            logger.info("§a[MobManager] §7Removed " + hologramsRemoved + " mob holograms");

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error during mob hologram cleanup: " + e.getMessage());
        }
    }

    /**
     *  Comprehensive shutdown cleanup
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


    // ================  TASKS ================

    /**
     *  Start essential tasks with proper thread safety and hologram integration
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
                        updateActiveMobs(); // This calls mob.tick() which handles holograms
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
        }.runTaskTimer(plugin, 20L, 1L); // Every tick for smooth hologram updates

        // Untracked mob cleanup task
        untrackedMobTask = new BukkitRunnable() {
            @Override
            public void run() {
                // This ALWAYS runs on the main thread
                try {
                    if (!isShuttingDown) {
                        killAllUntrackedMobs();
                    }
                } catch (Exception e) {
                    if (!isShuttingDown) {
                        logger.warning("§c[MobManager] Untracked mob cleanup error: " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 200L, 200L); // Every 10 seconds

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

    // ================ ACTIVE MOBS MANAGEMENT ================

    /**
     * Update active mobs - now with hologram integration
     */
    private void updateActiveMobs() {
        if (activeWorldBoss != null && activeWorldBoss.isValid()) {
            activeWorldBoss.tick(); // Use the new tick method
        }

        mobLock.writeLock().lock();
        try {
            activeMobs.entrySet().removeIf(entry -> {
                CustomMob mob = entry.getValue();
                if (mob == null || !mob.isValid()) {
                    // CRITICAL: Clean up hologram before removing
                    if (mob != null) {
                        mob.removeHologram();
                    }
                    // Clean up from CritManager too
                    CritManager.getInstance().removeCrit(entry.getKey());
                    return true;
                }

                // Tick the mob (this handles hologram updates with the new optimized system)
                mob.tick();
                return false;
            });
        } finally {
            mobLock.writeLock().unlock();
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

        // CRITICAL: Remove hologram before unregistering
        mob.removeHologram();

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

    /**
     * Handle mob hit by player with immediate response
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

            // Schedule health bar update for next tick (after damage is applied)
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (mob.isValid()) {
                        mob.updateHealthBar();

                        if (debug) {
                            logger.info(String.format("§a[MobManager] §7Health bar updated for %s: %.1f%% health",
                                    mob.getType().getId(), mob.getHealthPercentage()));
                        }
                    }
                } catch (Exception e) {
                    logger.warning("§c[MobManager] Delayed health bar update error: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            logger.warning("§c[MobManager] Handle mob hit error: " + e.getMessage());
        }
    }

    /**
     * Get mob current health info for debugging
     */
    public String getMobHealthInfo(LivingEntity entity) {
        if (entity == null) return "Entity is null";

        try {
            CustomMob mob = getCustomMob(entity);
            if (mob == null) return "Not a custom mob";

            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();
            double percentage = (health / maxHealth) * 100.0;

            return String.format("%s T%d%s: %.1f/%.1f (%.1f%%) - %s",
                    mob.getType().getId(), mob.getTier(), mob.isElite() ? "+" : "",
                    health, maxHealth, percentage,
                    mob.isShowingHealthBar() ? "Showing Health Bar" : "Showing Name");

        } catch (Exception e) {
            return "Error getting health info: " + e.getMessage();
        }
    }

    /**
     * Force refresh all mob health bars (for admin debugging)
     */
    public void refreshAllMobHealthBars() {
        try {
            logger.info("§6[MobManager] §7Refreshing all mob health bars...");

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
                    mob.refreshHealthBar();
                    refreshed++;
                }
            }

            logger.info("§a[MobManager] §7Refreshed health bars for " + refreshed + " mobs");

        } catch (Exception e) {
            logger.severe("§c[MobManager] Health bar refresh failed: " + e.getMessage());
        }
    }

    /**
     * Track damage and update health bars with proper timing
     */
    public void trackDamage(LivingEntity entity, Player player, double damage) {
        if (entity == null || player == null) return;

        UUID entityUuid = entity.getUniqueId();
        UUID playerUuid = player.getUniqueId();

        // Track the damage
        damageContributions.computeIfAbsent(entityUuid, k -> new ConcurrentHashMap<>())
                .merge(playerUuid, damage, Double::sum);

        // Update mob state immediately
        CustomMob mob = getCustomMob(entity);
        if (mob != null) {
            // Mark damage time immediately for name visibility
            mob.lastDamageTime = System.currentTimeMillis();
            mob.nameVisible = true;

            if (debug) {
                logger.info(String.format("§6[MobManager] §7%s took %.1f damage from %s (Health: %.1f/%.1f)",
                        mob.getType().getId(), damage, player.getName(),
                        entity.getHealth(), entity.getMaxHealth()));
            }
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

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player && AlignmentMechanics.isSafeZone(event.getTarget().getLocation())) {
            event.setTarget(null);
            event.setCancelled(true);
        }
    }

    /**
     *  Safe mob teleportation - MAIN THREAD ONLY
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

        } catch (Exception e) {
            logger.warning("§c[MobManager] Teleport error: " + e.getMessage());
        }
    }

    // ================ CRITICAL HIT DELEGATION ================

    /**
     *  Roll for critical hit using CritManager
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

    // ================ ENTITY VALIDATION ================

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
                        CustomMob removedMob = activeMobs.remove(invalidId);
                        // CRITICAL: Clean up hologram for invalid entities
                        if (removedMob != null) {
                            removedMob.removeHologram();
                        }
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

            // Use the new comprehensive damage handling method
            handleMobHitByPlayer(entity, damager, damage);

        } catch (Exception e) {
            logger.severe("§c[MobManager] Entity damage event error: " + e.getMessage());
        }
    }

    /**
     * ADDITIONAL FIX: Monitor damage events to ensure health bars update after damage is applied
     * This catches any damage that might not go through the EntityDamageByEntity event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageMonitor(EntityDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity) || event.getEntity() instanceof Player) {
                return;
            }

            // Only handle custom mobs
            CustomMob mob = getCustomMob(entity);
            if (mob == null) return;

            // Update health bar on next tick after damage is fully applied
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (mob.isValid()) {
                        mob.updateHealthBar();
                    }
                } catch (Exception e) {
                    logger.warning("§c[MobManager] Monitor health bar update error: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            // Silent fail for monitor event
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

    /**
     *   entity death handling with MAIN THREAD SAFETY
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

            //  debug logging
            if (debug) {
                logger.info("§6[MobManager] §7Processing death: " + entity.getType() +
                        " ID: " + entityId.toString().substring(0, 8));
            }

            // CRITICAL: Clean up hologram before other cleanup
            CustomMob customMob = getCustomMob(entity);
            if (customMob != null) {
                customMob.removeHologram();
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
     *  spawner finding for dead mobs
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
        info.append("Active Holograms: ").append(com.rednetty.server.mechanics.world.holograms.HologramManager.getHologramCount()).append("\n");
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
        return String.format("Cleanup Status: shutting_down=%s, cleanup_completed=%s, active_mobs=%d, active_holograms=%d",
                isShuttingDown, cleanupCompleted, activeMobs.size(),
                com.rednetty.server.mechanics.world.holograms.HologramManager.getHologramCount());
    }

    /**
     * Force cleanup (for admin commands)
     */
    public void forceCleanup() {
        logger.info("§6[MobManager] §7Force cleanup requested by admin");

        // Clean up holograms first
        cleanupAllMobHolograms();

        // Then perform entity cleanup
        performStartupCleanup();

        // Force hologram system cleanup
        com.rednetty.server.mechanics.world.holograms.HologramManager.cleanup();
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

    // ================ SPAWNER MOB SPAWNING ================

    /**
     * Kill all untracked mobs in a specific world (keeps tracked custom mobs alive)
     * This is useful for cleaning up vanilla mobs or rogue entities while preserving custom mobs
     *  Mark entities as cleanup kills to prevent drop processing
     */
    public int killUntrackedMobsInWorld(World world) {
        if (world == null) {
            logger.warning("§c[MobManager] Cannot kill untracked mobs - world is null");
            return 0;
        }

        logger.info("§6[MobManager] §7Killing untracked mobs in world: " + world.getName());

        int killed = 0;
        Set<UUID> trackedMobIds = new HashSet<>();

        // Get all tracked mob UUIDs
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
            // Get all living entities in the world
            Collection<LivingEntity> entities = world.getLivingEntities();

            for (LivingEntity entity : entities) {
                // Skip players
                if (entity instanceof Player || entity instanceof Horse || entity instanceof ArmorStand) {
                    continue;
                }

                // Skip tracked custom mobs
                if (trackedMobIds.contains(entity.getUniqueId())) {
                    if (debug) {
                        logger.info("§6[MobManager] §7Skipping tracked mob: " + entity.getType() +
                                " ID: " + entity.getUniqueId().toString().substring(0, 8));
                    }
                    continue;
                }

                // CRITICAL FIX: Mark entity as cleanup kill BEFORE killing it
                try {
                    markEntityAsCleanupKill(entity);

                    if (debug) {
                        logger.info("§6[MobManager] §7Killing untracked mob: " + entity.getType() +
                                " at " + formatLocation(entity.getLocation()) +
                                " ID: " + entity.getUniqueId().toString().substring(0, 8));
                    }

                    // Kill the entity
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

    /**
     * Kill all untracked mobs in all worlds (keeps tracked custom mobs alive)
     */
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

    /**
     * Get count of untracked mobs in a world
     */
    public int getUntrackedMobCount(World world) {
        if (world == null) return 0;

        Set<UUID> trackedMobIds = new HashSet<>();

        // Get all tracked mob UUIDs
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

        int untrackedCount = 0;
        try {
            Collection<LivingEntity> entities = world.getLivingEntities();
            for (LivingEntity entity : entities) {
                if (!(entity instanceof Player) && !trackedMobIds.contains(entity.getUniqueId())) {
                    untrackedCount++;
                }
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Error counting untracked mobs: " + e.getMessage());
        }

        return untrackedCount;
    }

    /**
     * Get detailed info about untracked mobs in a world
     */
    public String getUntrackedMobInfo(World world) {
        if (world == null) return "World is null";

        Set<UUID> trackedMobIds = new HashSet<>();

        // Get all tracked mob UUIDs
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

        Map<EntityType, Integer> untrackedCounts = new HashMap<>();
        List<String> examples = new ArrayList<>();

        try {
            Collection<LivingEntity> entities = world.getLivingEntities();
            for (LivingEntity entity : entities) {
                if (!(entity instanceof Player) && !trackedMobIds.contains(entity.getUniqueId())) {
                    EntityType type = entity.getType();
                    untrackedCounts.put(type, untrackedCounts.getOrDefault(type, 0) + 1);

                    // Add example locations for first few
                    if (examples.size() < 5) {
                        examples.add(type + " at " + formatLocation(entity.getLocation()));
                    }
                }
            }
        } catch (Exception e) {
            return "Error getting untracked mob info: " + e.getMessage();
        }

        StringBuilder info = new StringBuilder();
        info.append("Untracked mobs in ").append(world.getName()).append(":\n");
        info.append("Tracked custom mobs: ").append(trackedMobIds.size()).append("\n");
        info.append("Untracked mob types:\n");

        if (untrackedCounts.isEmpty()) {
            info.append("  None found\n");
        } else {
            for (Map.Entry<EntityType, Integer> entry : untrackedCounts.entrySet()) {
                info.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }

            if (!examples.isEmpty()) {
                info.append("Examples:\n");
                for (String example : examples) {
                    info.append("  ").append(example).append("\n");
                }
            }
        }

        return info.toString();
    }

    /**
     *   spawner mob spawning with MAIN THREAD SAFETY and elite support
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

        //  validation with detailed error reporting
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

            // Get safe spawn location with  validation
            Location spawnLoc = getSafeSpawnLocation(location);
            if (spawnLoc == null) {
                incrementFailureCount(normalizedType);
                logger.warning("§c[MobManager] No safe spawn location found near " + formatLocation(location));
                return null;
            }

            // Create entity with  error handling - MAIN THREAD ONLY
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
     *   chunk loading with proper validation
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
     *  mob type normalization with elite support
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
     *  entity creation with MAIN THREAD SAFETY and elite support
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
            return null;
        }
    }

    /**
     *  Create entity with multiple attempts with elite support
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

                        // Set up hologram for the mob
                        CustomMob customMob = getCustomMob(entity);
                        if (customMob != null) {
                            customMob.updateHologramOverlay(); // Initialize hologram
                        }

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
            Class.forName("com.rednetty.server.mechanics.world.mobs.core.MobType");
            return MobType.getById("skeleton") != null; // Test with a known type
        } catch (Exception e) {
            if (debug) {
                logger.warning("§6[MobManager] MobType system not available, using fallback: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     *  Create entity using MobType system with elite support
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

            // CRITICAL: Elite validation
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

            // Set up hologram for the mob
            customMob.updateHologramOverlay();  // Initialize hologram

            return entity;

        } catch (Exception e) {
            logger.warning("§c[MobManager] MobType system creation failed for " + type + ": " + e.getMessage());
            if (debug) e.printStackTrace();
            return null;
        }
    }

    /**
     *  Final fallback with elite support
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

                // Set up hologram for the mob
                CustomMob customMob = getCustomMob(entity);
                if (customMob != null) {
                    customMob.updateHologramOverlay();  // Initialize hologram
                }

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
        return EntityType.ZOMBIE;
    }

    /**
     * Configure basic entity properties
     */
    private void configureBasicEntity(LivingEntity entity, String type, int tier, boolean elite) {
        try {
            // Set basic metadata
            entity.setMetadata("type", new FixedMetadataValue(plugin, type));
            entity.setMetadata("tier", new FixedMetadataValue(plugin, tier));
            entity.setMetadata("elite", new FixedMetadataValue(plugin, elite));

            // Set AI and awareness
            if (entity instanceof Mob) {
                ((Mob) entity).setAware(true);
            }

            // Apply fire resistance to prevent burning
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

            // Set custom name visibility (initially false to prevent vanilla name rendering)
            entity.setCustomNameVisible(false);

            // Configure health and attributes
            configureEntityAttributes(entity, tier, elite);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure basic entity: " + e.getMessage());
        }
    }

    /**
     * Configure entity attributes based on tier and elite status
     */
    private void configureEntityAttributes(LivingEntity entity, int tier, boolean elite) {
        try {
            double healthMultiplier = 1.0 + (tier * 0.5);
            if (elite) healthMultiplier *= 1.5;

            // Set max health
            double maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getBaseValue() * healthMultiplier;
            entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
            entity.setHealth(maxHealth);

            // Set movement speed
            double speedMultiplier = 1.0 + (tier * 0.1);
            if (elite) speedMultiplier *= 1.2;
            entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(
                    entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue() * speedMultiplier);

            // Set attack damage if applicable
            if (entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE) != null) {
                double damageMultiplier = 1.0 + (tier * 0.3);
                if (elite) damageMultiplier *= 1.4;
                entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(
                        entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue() * damageMultiplier);
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure entity attributes: " + e.getMessage());
        }
    }

    /**
     * Configure entity appearance
     */
    private void configureEntityAppearance(LivingEntity entity, String type, int tier, boolean elite) {
        try {
            String nameColor = getNameColor(tier, elite);
            String displayName = nameColor + MobType.getById(type).getFormattedName(tier);
            if (elite) displayName += " §e[Elite]";
            displayName += " §7T" + tier;

            entity.setCustomName(displayName);
            // Note: CustomNameVisible is set to false initially; hologram handles visibility
        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure entity appearance: " + e.getMessage());
        }
    }

    /**
     * Get appropriate name color based on tier and elite status
     */
    private String getNameColor(int tier, boolean elite) {
        if (elite) {
            switch (tier) {
                case 1: return "§f";
                case 2: return "§a";
                case 3: return "§b";
                case 4: return "§d";
                case 5: return "§e";
                case 6: return "§6";
                default: return "§f";
            }
        } else {
            switch (tier) {
                case 1: return "§7";
                case 2: return "§2";
                case 3: return "§3";
                case 4: return "§5";
                case 5: return "§6";
                case 6: return "§c";
                default: return "§7";
            }
        }
    }

    /**
     * Configure entity equipment with T6 Netherite support
     */
    private void configureEntityEquipment(LivingEntity entity, int tier, boolean elite) {
        try {
            if (entity.getEquipment() == null) return;

            // Equip weapon
            ItemStack weapon = createMobWeapon(tier, elite);
            entity.getEquipment().setItemInMainHand(weapon);
            entity.getEquipment().setItemInMainHandDropChance(0.0f);

            // Equip armor
            applyEntityArmor(entity, tier, elite);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to configure entity equipment: " + e.getMessage());
        }
    }

    /**
     * Create appropriate weapon for mob based on tier
     */
    private ItemStack createMobWeapon(int tier, boolean elite) {
        try {
            Material weaponMaterial;
            switch (tier) {
                case 1:
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
                    weaponMaterial = Material.NETHERITE_SWORD;
                    break;
                default:
                    weaponMaterial = Material.WOODEN_SWORD;
            }

            ItemStack weapon = new ItemStack(weaponMaterial);
            ItemMeta meta = weapon.getItemMeta();
            if (meta != null) {
                // Add lore for tier and elite status
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Tier " + tier);
                String rarityLine = elite ? ChatColor.YELLOW + "Elite Weapon" : ChatColor.GRAY + "Common";
                lore.add(rarityLine);

                meta.setLore(lore);
                meta.setUnbreakable(true);

                // Set appropriate display name
                String displayName = generateMobWeaponName(weaponMaterial, tier, elite);
                meta.setDisplayName(displayName);

                weapon.setItemMeta(meta);

                // Apply T6 Netherite enhancements if applicable
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

    /**
     *  Generate appropriate weapon names for mobs
     */
    private String generateMobWeaponName(Material weaponType, int tier, boolean elite) {
        ChatColor tierColor;
        switch (tier) {
            case 1:
                tierColor = ChatColor.WHITE;
                break;
            case 2:
                tierColor = ChatColor.GREEN;
                break;
            case 3:
                tierColor = ChatColor.AQUA;
                break;
            case 4:
                tierColor = ChatColor.LIGHT_PURPLE;
                break;
            case 5:
                tierColor = ChatColor.YELLOW;
                break;
            case 6:
                tierColor = ChatColor.GOLD;
                break;
            default:
                tierColor = ChatColor.WHITE;
        }

        String prefix = elite ? (tierColor.toString() + ChatColor.BOLD) : tierColor.toString();

        if (tier == 6) {
            return prefix + "Nether Forged Blade";
        } else {
            String baseName = weaponType.name().replace("_SWORD", "").replace("_", " ");
            baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
            return prefix + baseName + " Sword";
        }
    }

    /**
     *  Apply armor to entity with T6 Netherite support
     */
    private void applyEntityArmor(LivingEntity entity, int tier, boolean elite) {
        try {
            if (entity.getEquipment() == null) return;

            // Determine armor material based on tier
            String armorPrefix;
            switch (tier) {
                case 1:
                case 2:
                    armorPrefix = "LEATHER";
                    break;
                case 3:
                    armorPrefix = "IRON";
                    break;
                case 4:
                    armorPrefix = "DIAMOND";
                    break;
                case 5:
                    armorPrefix = "GOLDEN";
                    break;
                case 6:
                    armorPrefix = "NETHERITE"; // T6 uses Netherite
                    break;
                default:
                    armorPrefix = "LEATHER";
            }

            // Create and apply armor pieces
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

                    // Apply T6 Netherite armor enhancements
                    if (tier == 6) {
                        armorPieces[i] = applyT6NetheriteArmorEffects(armorPieces[i], elite);
                    } else {
                        // Make armor unbreakable for all tiers
                        ItemMeta armorMeta = armorPieces[i].getItemMeta();
                        if (armorMeta != null) {
                            armorMeta.setUnbreakable(true);
                            armorPieces[i].setItemMeta(armorMeta);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Material doesn't exist, skip this piece
                    logger.warning("§c[MobManager] Invalid armor material: " + armorPrefix + " for piece " + i);
                }
            }

            // Apply armor to entity
            if (armorPieces[0] != null) entity.getEquipment().setHelmet(armorPieces[0]);
            if (armorPieces[1] != null) entity.getEquipment().setChestplate(armorPieces[1]);
            if (armorPieces[2] != null) entity.getEquipment().setLeggings(armorPieces[2]);
            if (armorPieces[3] != null) entity.getEquipment().setBoots(armorPieces[3]);

            // Set drop chances to 0 - items never drop
            entity.getEquipment().setHelmetDropChance(0.0f);
            entity.getEquipment().setChestplateDropChance(0.0f);
            entity.getEquipment().setLeggingsDropChance(0.0f);
            entity.getEquipment().setBootsDropChance(0.0f);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Failed to apply entity armor: " + e.getMessage());
        }
    }

    /**
     *  Apply T6 Netherite weapon effects for spawned mobs
     */
    private ItemStack applyT6NetheriteWeaponEffects(ItemStack weapon, boolean elite) {
        if (weapon == null || weapon.getType() == Material.AIR) {
            return weapon;
        }

        try {
            ItemMeta meta = weapon.getItemMeta();
            if (meta != null) {
                // Make unbreakable
                meta.setUnbreakable(true);

                // Add enchantment glow for elite T6 weapons
                if (elite && !meta.hasEnchants()) {
                    weapon.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
                }

                // T6 weapon name
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

    /**
     *  Apply T6 Netherite armor effects for spawned mobs (including gold trim)
     */
    private ItemStack applyT6NetheriteArmorEffects(ItemStack armor, boolean elite) {
        if (armor == null || armor.getType() == Material.AIR) {
            return armor;
        }

        try {
            ItemMeta meta = armor.getItemMeta();
            if (meta != null) {
                // Make unbreakable
                meta.setUnbreakable(true);

                // T6 armor name
                String armorName = generateMobT6ArmorName(armor.getType(), elite);
                meta.setDisplayName(armorName);

                armor.setItemMeta(meta);

                // Apply gold trim to T6 Netherite armor
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

    /**
     *  Apply gold trim to Netherite armor for mobs
     */
    private ItemStack applyMobNetheriteGoldTrim(ItemStack item) {
        if (item.getItemMeta() instanceof ArmorMeta) {
            try {
                ArmorMeta armorMeta = (ArmorMeta) item.getItemMeta();

                // Apply gold trim with eye pattern for T6 mob armor
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

    /**
     *  Generate T6 weapon names for mobs
     */
    private String generateMobT6WeaponName(Material weaponType, boolean elite) {
        String prefix = elite ? "§5§lElite " : "§5";

        switch (weaponType) {
            case NETHERITE_SWORD:
                return prefix + "Nether Forged Blade";
            case NETHERITE_AXE:
                return prefix + "Nether Forged War Axe";
            case NETHERITE_PICKAXE:
                return prefix + "Nether Forged Crusher";
            case NETHERITE_SHOVEL:
                return prefix + "Nether Forged Spade";
            case NETHERITE_HOE:
                return prefix + "Nether Forged Scythe";
            default:
                String baseName = weaponType.name().replace("NETHERITE_", "").replace("_", " ");
                baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
                return prefix + "Nether Forged " + baseName;
        }
    }

    /**
     *  Generate T6 armor names for mobs
     */
    private String generateMobT6ArmorName(Material armorType, boolean elite) {
        String prefix = elite ? "§5§lElite " : "§5";

        switch (armorType) {
            case NETHERITE_HELMET:
                return prefix + "Nether Forged Crown";
            case NETHERITE_CHESTPLATE:
                return prefix + "Nether Forged Chestguard";
            case NETHERITE_LEGGINGS:
                return prefix + "Nether Forged Legguards";
            case NETHERITE_BOOTS:
                return prefix + "Nether Forged Warboots";
            default:
                String baseName = armorType.name().replace("NETHERITE_", "").replace("_", " ");
                baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
                return prefix + "Nether Forged " + baseName;
        }
    }

    /**
     * Get a safe spawn location near the target location with validation
     */
    private Location getSafeSpawnLocation(Location target) {
        if (target == null || target.getWorld() == null) {
            return null;
        }

        // Try the target location first
        if (isSafeSpawnLocation(target)) {
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
                if (isSafeSpawnLocation(candidate)) {
                    return candidate;
                }
            }
        }

        // Final fallback with forced safe location
        return target.clone().add(0.5, 5, 0.5);
    }

    /**
     * Safe spawn location checking with detailed validation
     */
    private boolean isSafeSpawnLocation(Location location) {
        try {
            if (location == null || location.getWorld() == null) {
                return false;
            }

            // Check if chunk is loaded
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return false;
            }

            // Y bounds checking
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

            // Register with MobManager
            CustomMob customMob = getCustomMob(entity);
            if (customMob != null) {
                registerMob(customMob);
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
     *  spawn request validation with elite support
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
     *  Check if spawner can spawn a mob with elite validation
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
}