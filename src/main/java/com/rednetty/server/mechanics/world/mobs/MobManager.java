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
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
 * MobManager with simplified hologram integration and enhanced cleanup
 * Key Fixes:
 * - Simplified hologram coordination with CustomMob's new name system
 * - Removed complex hologram state management conflicts
 * - Better damage tracking and health updates
 * - Cleaner mob lifecycle management
 * - Enhanced cleanup without hologram conflicts
 * - Modernized with Adventure API and Paper 1.21.7 capabilities
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

    // ================ ADVENTURE API CONSTANTS ================
    private static final Component LOG_PREFIX = Component.text("[MobManager]", NamedTextColor.GOLD);
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    // ================ SINGLETON ================
    private static volatile MobManager instance;

    // ================ CORE MAPS WITH THREAD SAFETY ================
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
     * Constructor with enhanced initialization
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
     * Enhanced initialization with startup cleanup
     */
    public void initialize() {
        try {
            logInfo(Component.text("Starting enhanced initialization with simplified hologram system...", NamedTextColor.GRAY));

            // Clean up any orphaned custom mobs from previous sessions
            performStartupCleanup();

            // Initialize CritManager first
            CritManager.initialize(plugin);

            spawner.initialize();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startTasks();

            logInfo(Component.text("Enhanced initialization completed successfully with ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(spawner.getAllSpawners().size(), NamedTextColor.YELLOW))
                    .append(Component.text(" spawners", NamedTextColor.GRAY)));

            if (debug) {
                logInfo(Component.text("Debug mode enabled with simplified hologram integration", NamedTextColor.GRAY));
            }
        } catch (Exception e) {
            logSevere(Component.text("Failed to initialize: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    /**
     * Initialize mob type to EntityType mapping with proper elite support
     */
    private void initializeMobTypeMapping() {
        try {
            logInfo(Component.text("Initializing comprehensive mob type mapping with elite support...", NamedTextColor.GRAY));

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

            logInfo(Component.text("Initialized " + mobTypeMapping.size() + " mob type mappings", NamedTextColor.GREEN));
            logInfo(Component.text("Registered " + eliteOnlyTypes.size() + " elite-only types", NamedTextColor.GREEN));

        } catch (Exception e) {
            logSevere(Component.text("Failed to initialize mob type mapping: " + e.getMessage(), NamedTextColor.RED));
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
            logInfo(Component.text("Performing startup cleanup of orphaned custom mobs...", NamedTextColor.GRAY));

            int totalFound = 0;
            int totalRemoved = 0;

            // First, clean up all holograms to prevent orphaned holograms
            try {
                com.rednetty.server.mechanics.world.holograms.HologramManager.forceCleanupAll();
                logInfo(Component.text("Cleaned up all existing holograms", NamedTextColor.GRAY));
            } catch (Exception e) {
                logWarning(Component.text("Error during hologram cleanup: " + e.getMessage(), NamedTextColor.YELLOW));
            }

            // Clean up all loaded worlds
            for (World world : Bukkit.getWorlds()) {
                try {
                    CleanupResult result = cleanupWorldCustomMobs(world, true);
                    totalFound += result.entitiesFound;
                    totalRemoved += result.entitiesRemoved;

                    if (result.entitiesFound > 0) {
                        logInfo(Component.text("World " + world.getName() + ": found " +
                                result.entitiesFound + ", removed " + result.entitiesRemoved, NamedTextColor.GRAY));
                    }
                } catch (Exception e) {
                    logWarning(Component.text("Error cleaning world " + world.getName() + ": " + e.getMessage(), NamedTextColor.YELLOW));
                }
            }

            if (totalFound > 0) {
                logInfo(Component.text("Startup cleanup complete: removed " + totalRemoved +
                        "/" + totalFound + " orphaned custom mobs", NamedTextColor.GREEN));
            } else {
                logInfo(Component.text("Startup cleanup complete: no orphaned mobs found", NamedTextColor.GREEN));
            }

        } catch (Exception e) {
            logSevere(Component.text("Startup cleanup failed: " + e.getMessage(), NamedTextColor.RED));
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
                    logWarning(Component.text("Error processing entity " + entity.getUniqueId() + ": " + e.getMessage(), NamedTextColor.YELLOW));
                }
            }

            return new CleanupResult(found, removed);

        } catch (Exception e) {
            logWarning(Component.text("Error cleaning world " + world.getName() + ": " + e.getMessage(), NamedTextColor.YELLOW));
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
            logWarning(Component.text("Failed to mark entity as cleanup kill: " + e.getMessage(), NamedTextColor.YELLOW));
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
                logInfo(Component.text("Removing entity " + entityId.toString().substring(0, 8) +
                        " (" + entity.getType() + ") - " + reason, NamedTextColor.GRAY));
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
            logWarning(Component.text("Error removing entity: " + e.getMessage(), NamedTextColor.YELLOW));
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
            logWarning(Component.text("Error removing from tracking systems: " + e.getMessage(), NamedTextColor.YELLOW));
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
     * Enhanced shutdown with comprehensive cleanup
     */
    public void shutdown() {
        try {
            logInfo(Component.text("Starting enhanced shutdown with simplified cleanup...", NamedTextColor.GRAY));
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
                logWarning(Component.text("Error during spawner shutdown: " + e.getMessage(), NamedTextColor.YELLOW));
            }

            // Clean up CritManager
            try {
                if (CritManager.getInstance() != null) {
                    CritManager.getInstance().cleanup();
                }
            } catch (Exception e) {
                logWarning(Component.text("Error cleaning up CritManager: " + e.getMessage(), NamedTextColor.YELLOW));
            }

            // Final hologram cleanup
            try {
                com.rednetty.server.mechanics.world.holograms.HologramManager.cleanup();
                logInfo(Component.text("All holograms cleaned up during shutdown", NamedTextColor.GREEN));
            } catch (Exception e) {
                logWarning(Component.text("Error during hologram cleanup: " + e.getMessage(), NamedTextColor.YELLOW));
            }

            // Clear all collections
            clearAllCollections();

            activeWorldBoss = null;
            cleanupCompleted.set(true);

            logInfo(Component.text("Enhanced shutdown completed successfully", NamedTextColor.GREEN));

        } catch (Exception e) {
            logSevere(Component.text("Enhanced shutdown error: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    /**
     * Comprehensive shutdown cleanup
     */
    private void performShutdownCleanup() {
        try {
            logInfo(Component.text("Performing comprehensive shutdown cleanup...", NamedTextColor.GRAY));

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
                        logWarning(Component.text("Error removing tracked mob: " + e.getMessage(), NamedTextColor.YELLOW));
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
                        logInfo(Component.text("World " + world.getName() +
                                ": found " + result.entitiesFound + " additional entities, removed " + result.entitiesRemoved, NamedTextColor.GRAY));
                    }
                } catch (Exception e) {
                    logWarning(Component.text("Error cleaning world " + world.getName() +
                            " during shutdown: " + e.getMessage(), NamedTextColor.YELLOW));
                }
            }

            logInfo(Component.text("Shutdown cleanup complete: removed " + totalRemoved +
                    "/" + totalFound + " custom mobs", NamedTextColor.GREEN));

        } catch (Exception e) {
            logSevere(Component.text("Shutdown cleanup failed: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    // ================ TASKS ================

    /**
     * Start essential tasks
     */
    private void startTasks() {
        logInfo(Component.text("Starting essential tasks...", NamedTextColor.GRAY));

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
                        logWarning(Component.text("Main task error: " + e.getMessage(), NamedTextColor.YELLOW));
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
                        logWarning(Component.text("Untracked mob cleanup error: " + e.getMessage(), NamedTextColor.YELLOW));
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
                        logWarning(Component.text("Cleanup task error: " + e.getMessage(), NamedTextColor.YELLOW));
                    }
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every minute

        logInfo(Component.text("Essential tasks started successfully", NamedTextColor.GREEN));
    }

    // ================ ACTIVE MOBS MANAGEMENT ================

    /**
     * Update active mobs with simplified system
     */
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
     * Register mob with enhanced tracking
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
            logInfo(Component.text("Registered mob: " + mob.getType().getId() +
                    " (ID: " + mob.getUniqueMobId() + ")", NamedTextColor.GREEN));
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
            logInfo(Component.text("Unregistered mob: " + mob.getType().getId() +
                    " (ID: " + mob.getUniqueMobId() + ")", NamedTextColor.GREEN));
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

            // Enhanced: Simplified damage handling - mob handles its own display updates
            mob.handleDamage(damage);

            if (debug) {
                logInfo(Component.text(String.format("Damage processed for %s (ID: %s): %.1f damage",
                        mob.getType().getId(), mob.getUniqueMobId(), damage), NamedTextColor.GREEN));
            }

        } catch (Exception e) {
            logWarning(Component.text("Handle mob hit error: " + e.getMessage(), NamedTextColor.YELLOW));
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
            logInfo(Component.text(String.format("Damage tracked: %.1f from %s to %s",
                    damage, player.getName(), entity.getType()), NamedTextColor.GRAY));
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
            logWarning(Component.text("Position check error: " + e.getMessage(), NamedTextColor.YELLOW));
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
     * Safe mob teleportation with Adventure API sounds - MAIN THREAD ONLY
     */
    private void teleportMobToSpawner(LivingEntity entity, Location spawnerLoc, String reason) {
        if (!Bukkit.isPrimaryThread()) {
            logSevere(Component.text("CRITICAL: teleportMobToSpawner called from async thread!", NamedTextColor.RED));
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

            // Enhanced: Use Adventure Sound API
            Sound teleportSound = Sound.sound(org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, Sound.Source.HOSTILE, 1.0f, 1.0f);
            entity.getWorld().playSound(teleportSound, entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ());

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
            logWarning(Component.text("Teleport error: " + e.getMessage(), NamedTextColor.YELLOW));
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
            logSevere(Component.text("Critical roll error: " + e.getMessage(), NamedTextColor.RED));
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
            logSevere(Component.text("Cleanup error: " + e.getMessage(), NamedTextColor.RED));
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
            logSevere(Component.text("Entity damage event error: " + e.getMessage(), NamedTextColor.RED));
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
                logInfo(Component.text("Processing death: " + entity.getType() +
                        " ID: " + entityId.toString().substring(0, 8), NamedTextColor.GRAY));
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
                    logInfo(Component.text("Notified spawner at " + formatLocation(spawnerLoc) +
                            " of mob death", NamedTextColor.GREEN));
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
            logWarning(Component.text("Entity death processing error: " + e.getMessage(), NamedTextColor.YELLOW));
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
                        logWarning(Component.text("Error reading spawner metadata: " + e.getMessage(), NamedTextColor.YELLOW));
                    }
                }
            }

            // Search for nearest spawner
            Location mobLocation = entity.getLocation();
            return spawner.findNearestSpawner(mobLocation, mobRespawnDistanceCheck);

        } catch (Exception e) {
            logWarning(Component.text("Error finding spawner for mob: " + e.getMessage(), NamedTextColor.YELLOW));
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
                logWarning(Component.text("Error finding spawner by ID: " + e.getMessage(), NamedTextColor.YELLOW));
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
                logInfo(Component.text("Cleaned up data for mob: " + entityId.toString().substring(0, 8), NamedTextColor.GRAY));
            }

        } catch (Exception e) {
            logWarning(Component.text("Error cleaning up mob data: " + e.getMessage(), NamedTextColor.YELLOW));
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
                    logInfo(Component.text("Prevented custom mob " +
                            entity.getType() + " from burning (sunlight protection)", NamedTextColor.GRAY));
                }
            }
        } catch (Exception e) {
            logWarning(Component.text("Error in sunlight protection: " + e.getMessage(), NamedTextColor.YELLOW));
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
                    logInfo(Component.text("Prevented fire damage to custom mob " +
                            entity.getType() + " (damage cause: " + event.getCause() + ")", NamedTextColor.GRAY));
                }
            }
        } catch (Exception e) {
            logWarning(Component.text("Error in fire damage protection: " + e.getMessage(), NamedTextColor.YELLOW));
        }
    }

    /**
     * Enhanced custom mob validation during cleanup
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
        logInfo(Component.text("All spawners reset", NamedTextColor.GREEN));
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
        info.append("=== Enhanced MobManager Diagnostic Info ===\n");
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
        return String.format("Enhanced Cleanup Status: shutting_down=%s, cleanup_completed=%s, active_mobs=%d, unique_tracked=%d",
                isShuttingDown.get(), cleanupCompleted.get(), activeMobs.size(), mobsByUniqueId.size());
    }

    public void forceCleanup() {
        logInfo(Component.text("Force cleanup requested by admin", NamedTextColor.GRAY));
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
        logInfo(Component.text("Killing all untracked mobs in all worlds", NamedTextColor.GRAY));

        int totalKilled = 0;
        for (World world : Bukkit.getWorlds()) {
            try {
                int killed = killUntrackedMobsInWorld(world);
                totalKilled += killed;
            } catch (Exception e) {
                logWarning(Component.text("Error killing untracked mobs in world " +
                        world.getName() + ": " + e.getMessage(), NamedTextColor.YELLOW));
            }
        }

        logInfo(Component.text("Total untracked mobs killed: " + totalKilled, NamedTextColor.GREEN));
        return totalKilled;
    }

    public int killUntrackedMobsInWorld(World world) {
        if (world == null) {
            logWarning(Component.text("Cannot kill untracked mobs - world is null", NamedTextColor.YELLOW));
            return 0;
        }

        logInfo(Component.text("Killing untracked mobs in world: " + world.getName(), NamedTextColor.GRAY));

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
                        logInfo(Component.text("Skipping tracked mob: " + entity.getType() +
                                " ID: " + entity.getUniqueId().toString().substring(0, 8), NamedTextColor.GRAY));
                    }
                    continue;
                }

                try {
                    markEntityAsCleanupKill(entity);

                    if (debug) {
                        logInfo(Component.text("Killing untracked mob: " + entity.getType() +
                                " at " + formatLocation(entity.getLocation()) +
                                " ID: " + entity.getUniqueId().toString().substring(0, 8), NamedTextColor.GRAY));
                    }

                    entity.setHealth(0);
                    entity.damage(999999);
                    entity.remove();
                    killed++;

                } catch (Exception e) {
                    if (debug) {
                        logWarning(Component.text("Failed to kill untracked entity " +
                                entity.getType() + ": " + e.getMessage(), NamedTextColor.YELLOW));
                    }
                }
            }

        } catch (Exception e) {
            logWarning(Component.text("Error getting entities from world " + world.getName() +
                    ": " + e.getMessage(), NamedTextColor.YELLOW));
        }

        logInfo(Component.text("Killed " + killed + " untracked mobs in world: " + world.getName() +
                " (preserved " + trackedMobIds.size() + " tracked mobs)", NamedTextColor.GREEN));

        return killed;
    }

    // ================ MOB SPAWNING ================

    public LivingEntity spawnMobFromSpawner(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logSevere(Component.text("CRITICAL: spawnMobFromSpawner called from async thread!", NamedTextColor.RED));
            Bukkit.getScheduler().runTask(plugin, () -> spawnMobFromSpawner(location, type, tier, elite));
            return null;
        }

        if (isShuttingDown.get()) {
            return null;
        }

        if (!isValidSpawnRequest(location, type, tier, elite)) {
            if (debug) {
                logWarning(Component.text("Invalid spawn request: " + type + " T" + tier +
                        (elite ? "+" : "") + " at " + formatLocation(location), NamedTextColor.YELLOW));
            }
            return null;
        }

        String normalizedType = normalizeMobType(type);
        if (normalizedType == null) {
            incrementFailureCount(type);
            logWarning(Component.text("Failed to normalize mob type: " + type, NamedTextColor.YELLOW));
            return null;
        }

        String spawnKey = normalizedType + "_" + tier + "_" + elite + "_" + formatLocation(location);
        if (activeSpawning.contains(spawnKey)) {
            duplicateSpawnsPrevented.incrementAndGet();
            if (debug) {
                logInfo(Component.text("Prevented duplicate spawn: " + spawnKey, NamedTextColor.GRAY));
            }
            return null;
        }

        try {
            activeSpawning.add(spawnKey);

            if (!ensureChunkLoadedForSpawning(location)) {
                incrementFailureCount(normalizedType);
                logWarning(Component.text("Chunk loading failed for spawn at " + formatLocation(location), NamedTextColor.YELLOW));
                return null;
            }

            Location spawnLoc = getSafeSpawnLocation(location);
            if (spawnLoc == null) {
                incrementFailureCount(normalizedType);
                logWarning(Component.text("No safe spawn location found near " + formatLocation(location), NamedTextColor.YELLOW));
                return null;
            }

            LivingEntity entity = createEntityWithValidation(spawnLoc, normalizedType, tier, elite);

            if (entity != null) {
                registerSpawnedMob(entity, location);

                if (debug) {
                    logInfo(Component.text("Successfully spawned " + normalizedType + " T" + tier +
                            (elite ? "+" : "") + " at " + formatLocation(spawnLoc) +
                            " with ID: " + entity.getUniqueId().toString().substring(0, 8), NamedTextColor.GREEN));
                }

                failureCounter.put(normalizedType, 0);
                return entity;
            } else {
                incrementFailureCount(normalizedType);
                logWarning(Component.text("Failed to create entity for " + normalizedType, NamedTextColor.YELLOW));
            }
        } catch (Exception e) {
            incrementFailureCount(normalizedType);
            logSevere(Component.text("Critical error spawning mob " + normalizedType + ": " + e.getMessage(), NamedTextColor.RED));
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
                logWarning(Component.text("Failed to load chunk at " + chunkX + "," + chunkZ, NamedTextColor.YELLOW));
                return false;
            }

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                logWarning(Component.text("Chunk loading verification failed at " + chunkX + "," + chunkZ, NamedTextColor.YELLOW));
                return false;
            }

            return true;
        } catch (Exception e) {
            logWarning(Component.text("Chunk loading error: " + e.getMessage(), NamedTextColor.YELLOW));
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
                    logInfo(Component.text("Mapped " + type + " to " + validType, NamedTextColor.GRAY));
                }
                return validType;
            }
        }

        logWarning(Component.text("Unknown mob type: " + type + ". Available types: " +
                String.join(", ", validMobTypes), NamedTextColor.YELLOW));
        return null;
    }

    private LivingEntity createEntityWithValidation(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logSevere(Component.text("CRITICAL: createEntityWithValidation called from async thread!", NamedTextColor.RED));
            return null;
        }

        try {
            if (debug) {
                logInfo(Component.text("Creating entity: " + type + " T" + tier + (elite ? "+" : "") +
                        " (Elite-only: " + eliteOnlyTypes.contains(type) + ")", NamedTextColor.GRAY));
            }

            LivingEntity entity = null;

            if (isMobTypeSystemAvailable()) {
                entity = createEntityUsingMobTypeSystem(location, type, tier, elite);
                if (entity != null) {
                    if (debug) {
                        logInfo(Component.text("Entity created via MobType system: " + type, NamedTextColor.GREEN));
                    }
                    return entity;
                }
            }

            entity = createEntityWithMultipleAttempts(location, type, tier, elite);
            if (entity != null) {
                if (debug) {
                    logInfo(Component.text("Entity created via direct spawning: " + type, NamedTextColor.GREEN));
                }
                return entity;
            }

            return createEntityFinalFallback(location, type, tier, elite);

        } catch (Exception e) {
            logWarning(Component.text("Entity creation error for " + type + ": " + e.getMessage(), NamedTextColor.YELLOW));
            if (debug) e.printStackTrace();
            return null;
        }
    }

    private LivingEntity createEntityWithMultipleAttempts(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logSevere(Component.text("CRITICAL: createEntityWithMultipleAttempts called from async thread!", NamedTextColor.RED));
            return null;
        }

        EntityType entityType = mobTypeMapping.get(type);
        if (entityType == null) {
            logWarning(Component.text("No EntityType mapping for: " + type, NamedTextColor.YELLOW));
            return null;
        }

        if (!isLivingEntityType(entityType)) {
            logWarning(Component.text("EntityType " + entityType + " is not a living entity", NamedTextColor.YELLOW));
            return null;
        }

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                World world = location.getWorld();
                if (world == null) {
                    logWarning(Component.text("World is null for location", NamedTextColor.YELLOW));
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
                            logInfo(Component.text("Entity creation succeeded on attempt " + attempt + " for " + type, NamedTextColor.GRAY));
                        }

                        return entity;
                    } else {
                        entity.remove();
                        if (debug) {
                            logWarning(Component.text("Created entity was invalid on attempt " + attempt, NamedTextColor.YELLOW));
                        }
                    }
                } else {
                    if (debug) {
                        logWarning(Component.text("world.spawnEntity returned null on attempt " + attempt + " for " + type, NamedTextColor.YELLOW));
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
                logWarning(Component.text("Entity creation attempt " + attempt + " failed for " + type + ": " + e.getMessage(), NamedTextColor.YELLOW));
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
                logWarning(Component.text("MobType system not available, using fallback: " + e.getMessage(), NamedTextColor.YELLOW));
            }
            return false;
        }
    }

    private LivingEntity createEntityUsingMobTypeSystem(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logSevere(Component.text("CRITICAL: createEntityUsingMobTypeSystem called from async thread!", NamedTextColor.RED));
            return null;
        }

        try {
            MobType mobType = MobType.getById(type);
            if (mobType == null) {
                if (debug) {
                    logWarning(Component.text("MobType.getById returned null for: " + type, NamedTextColor.YELLOW));
                }
                return null;
            }

            if (eliteOnlyTypes.contains(type) && !elite) {
                logWarning(Component.text(type + " is an elite-only type but elite=false was specified", NamedTextColor.YELLOW));
                return null;
            }

            CustomMob customMob;

            if (elite || mobType.isElite()) {
                customMob = new EliteMob(mobType, tier);
                if (debug) {
                    logInfo(Component.text("Creating EliteMob for " + type + " T" + tier, NamedTextColor.LIGHT_PURPLE));
                }
            } else {
                customMob = new CustomMob(mobType, tier, false);
                if (debug) {
                    logInfo(Component.text("Creating CustomMob for " + type + " T" + tier, NamedTextColor.GREEN));
                }
            }

            boolean spawnSuccess = customMob.spawn(location);
            if (!spawnSuccess) {
                logWarning(Component.text("CustomMob spawn failed for " + type, NamedTextColor.YELLOW));
                return null;
            }

            LivingEntity entity = customMob.getEntity();
            if (entity == null) {
                logWarning(Component.text("CustomMob entity is null after spawn for " + type, NamedTextColor.YELLOW));
                return null;
            }

            configureBasicEntity(entity, type, tier, elite);

            return entity;

        } catch (Exception e) {
            logWarning(Component.text("MobType system creation failed for " + type + ": " + e.getMessage(), NamedTextColor.YELLOW));
            if (debug) e.printStackTrace();
            return null;
        }
    }

    private LivingEntity createEntityFinalFallback(Location location, String type, int tier, boolean elite) {
        if (!Bukkit.isPrimaryThread()) {
            logSevere(Component.text("CRITICAL: createEntityFinalFallback called from async thread!", NamedTextColor.RED));
            return null;
        }

        try {
            World world = location.getWorld();
            if (world == null) {
                logWarning(Component.text("World is null for final fallback", NamedTextColor.YELLOW));
                return null;
            }

            EntityType fallbackType = getFallbackEntityType(type);
            Entity spawnedEntity = world.spawnEntity(location, fallbackType);

            if (spawnedEntity instanceof LivingEntity entity) {
                configureBasicEntity(entity, type, tier, elite);

                if (debug) {
                    logInfo(Component.text("Created entity using final fallback (" + fallbackType + ") for: " + type, NamedTextColor.GRAY));
                }

                return entity;
            }
        } catch (Exception e) {
            logSevere(Component.text("Final fallback failed: " + e.getMessage(), NamedTextColor.RED));
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
            logWarning(Component.text("Failed to configure basic entity: " + e.getMessage(), NamedTextColor.YELLOW));
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
            logWarning(Component.text("Failed to configure entity attributes: " + e.getMessage(), NamedTextColor.YELLOW));
        }
    }

    private void configureEntityAppearance(LivingEntity entity, String type, int tier, boolean elite) {
        try {
            Component nameColor = getNameColor(tier, elite);
            String displayName = MobType.getById(type).getFormattedName(tier);

            // Enhanced: Build display name using Adventure Components
            Component fullName = nameColor
                    .append(Component.text(displayName));

            if (elite) {
                fullName = fullName.append(Component.text(" [Elite]", NamedTextColor.YELLOW));
            }

            fullName = fullName.append(Component.text(" T" + tier, NamedTextColor.GRAY));

            // Convert to legacy string for backwards compatibility with Bukkit
            entity.setCustomName(LEGACY_SERIALIZER.serialize(fullName));
        } catch (Exception e) {
            logWarning(Component.text("Failed to configure entity appearance: " + e.getMessage(), NamedTextColor.YELLOW));
        }
    }

    private Component getNameColor(int tier, boolean elite) {
        if (elite) {
            return switch (tier) {
                case 1 -> Component.empty().color(NamedTextColor.WHITE);
                case 2 -> Component.empty().color(NamedTextColor.GREEN);
                case 3 -> Component.empty().color(NamedTextColor.AQUA);
                case 4 -> Component.empty().color(NamedTextColor.LIGHT_PURPLE);
                case 5 -> Component.empty().color(NamedTextColor.YELLOW);
                case 6 -> Component.empty().color(NamedTextColor.GOLD);
                default -> Component.empty().color(NamedTextColor.WHITE);
            };
        } else {
            return switch (tier) {
                case 1 -> Component.empty().color(NamedTextColor.GRAY);
                case 2 -> Component.empty().color(NamedTextColor.DARK_GREEN);
                case 3 -> Component.empty().color(NamedTextColor.DARK_AQUA);
                case 4 -> Component.empty().color(NamedTextColor.DARK_PURPLE);
                case 5 -> Component.empty().color(NamedTextColor.GOLD);
                case 6 -> Component.empty().color(NamedTextColor.RED);
                default -> Component.empty().color(NamedTextColor.GRAY);
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
            logWarning(Component.text("Failed to configure entity equipment: " + e.getMessage(), NamedTextColor.YELLOW));
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
                // Enhanced: Use Adventure Components for lore
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Tier " + tier, NamedTextColor.GRAY));
                Component rarityLine = elite ? Component.text("Elite Weapon", NamedTextColor.YELLOW) : Component.text("Common", NamedTextColor.GRAY);
                lore.add(rarityLine);

                meta.lore(lore);
                meta.setUnbreakable(true);

                Component displayName = generateMobWeaponName(weaponMaterial, tier, elite);
                meta.displayName(displayName);

                weapon.setItemMeta(meta);

                if (tier == 6) {
                    weapon = applyT6NetheriteWeaponEffects(weapon, elite);
                }
            }

            return weapon;

        } catch (Exception e) {
            logWarning(Component.text("Failed to create mob weapon: " + e.getMessage(), NamedTextColor.YELLOW));
            return new ItemStack(Material.WOODEN_SWORD);
        }
    }

    private Component generateMobWeaponName(Material weaponType, int tier, boolean elite) {
        TextColor tierColor = switch (tier) {
            case 1 -> NamedTextColor.WHITE;
            case 2 -> NamedTextColor.GREEN;
            case 3 -> NamedTextColor.AQUA;
            case 4 -> NamedTextColor.LIGHT_PURPLE;
            case 5 -> NamedTextColor.YELLOW;
            case 6 -> NamedTextColor.GOLD;
            default -> NamedTextColor.WHITE;
        };

        Component nameComponent = Component.empty().color(tierColor);
        if (elite) {
            nameComponent = nameComponent.decorate(TextDecoration.BOLD);
        }

        if (tier == 6) {
            return nameComponent.append(Component.text("Nether Forged Blade"));
        } else {
            String baseName = weaponType.name().replace("_SWORD", "").replace("_", " ");
            baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
            return nameComponent.append(Component.text(baseName + " Sword"));
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
                    logWarning(Component.text("Invalid armor material: " + armorPrefix + " for piece " + i, NamedTextColor.YELLOW));
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
            logWarning(Component.text("Failed to apply entity armor: " + e.getMessage(), NamedTextColor.YELLOW));
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

                Component weaponName = generateMobT6WeaponName(weapon.getType(), elite);
                meta.displayName(weaponName);

                weapon.setItemMeta(meta);
            }

            if (debug) {
                logInfo(Component.text("Applied T6 Netherite weapon effects to " + weapon.getType(), NamedTextColor.GRAY));
            }

        } catch (Exception e) {
            logWarning(Component.text("Failed to apply T6 weapon effects: " + e.getMessage(), NamedTextColor.YELLOW));
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

                Component armorName = generateMobT6ArmorName(armor.getType(), elite);
                meta.displayName(armorName);

                armor.setItemMeta(meta);

                armor = applyMobNetheriteGoldTrim(armor);
            }

            if (debug) {
                logInfo(Component.text("Applied T6 Netherite armor effects with gold trim to " + armor.getType(), NamedTextColor.GRAY));
            }

        } catch (Exception e) {
            logWarning(Component.text("Failed to apply T6 armor effects: " + e.getMessage(), NamedTextColor.YELLOW));
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
                    logInfo(Component.text("Applied gold trim to T6 mob Netherite armor: " + item.getType(), NamedTextColor.GRAY));
                }
            } catch (Exception e) {
                logWarning(Component.text("Failed to apply gold trim to mob Netherite armor: " + e.getMessage(), NamedTextColor.YELLOW));
            }
        }
        return item;
    }

    private Component generateMobT6WeaponName(Material weaponType, boolean elite) {
        Component baseComponent = Component.empty().color(NamedTextColor.DARK_PURPLE);
        if (elite) {
            baseComponent = baseComponent.decorate(TextDecoration.BOLD);
        }

        return switch (weaponType) {
            case NETHERITE_SWORD -> baseComponent.append(Component.text("Nether Forged Blade"));
            case NETHERITE_AXE -> baseComponent.append(Component.text("Nether Forged War Axe"));
            case NETHERITE_PICKAXE -> baseComponent.append(Component.text("Nether Forged Crusher"));
            case NETHERITE_SHOVEL -> baseComponent.append(Component.text("Nether Forged Spade"));
            case NETHERITE_HOE -> baseComponent.append(Component.text("Nether Forged Scythe"));
            default -> {
                String baseName = weaponType.name().replace("NETHERITE_", "").replace("_", " ");
                baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
                yield baseComponent.append(Component.text("Nether Forged " + baseName));
            }
        };
    }

    private Component generateMobT6ArmorName(Material armorType, boolean elite) {
        Component baseComponent = Component.empty().color(NamedTextColor.DARK_PURPLE);
        if (elite) {
            baseComponent = baseComponent.decorate(TextDecoration.BOLD);
        }

        return switch (armorType) {
            case NETHERITE_HELMET -> baseComponent.append(Component.text("Nether Forged Crown"));
            case NETHERITE_CHESTPLATE -> baseComponent.append(Component.text("Nether Forged Chestguard"));
            case NETHERITE_LEGGINGS -> baseComponent.append(Component.text("Nether Forged Legguards"));
            case NETHERITE_BOOTS -> baseComponent.append(Component.text("Nether Forged Warboots"));
            default -> {
                String baseName = armorType.name().replace("NETHERITE_", "").replace("_", " ");
                baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1).toLowerCase();
                yield baseComponent.append(Component.text("Nether Forged " + baseName));
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
                logWarning(Component.text("Error validating spawn location: " + e.getMessage(), NamedTextColor.YELLOW));
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
            logWarning(Component.text("Error registering spawned mob: " + e.getMessage(), NamedTextColor.YELLOW));
        }
    }

    private void incrementFailureCount(String mobType) {
        int count = failureCounter.getOrDefault(mobType, 0) + 1;
        failureCounter.put(mobType, count);

        if (count > 10 && count % 5 == 0) {
            logWarning(Component.text("High failure count for " + mobType + ": " + count + " failures", NamedTextColor.YELLOW));
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
                logWarning(Component.text(type + " is an elite-only type but elite=false", NamedTextColor.YELLOW));
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
            logWarning(Component.text("Error checking spawner mob validity: " + e.getMessage(), NamedTextColor.YELLOW));
            return false;
        }
    }

    // ================ ADVENTURE API LOGGING HELPERS ================

    /**
     * Enhanced logging methods using Adventure Components for consistency
     */
    private void logInfo(Component message) {
        logger.info(LEGACY_SERIALIZER.serialize(LOG_PREFIX.append(Component.space()).append(message)));
    }

    private void logWarning(Component message) {
        logger.warning(LEGACY_SERIALIZER.serialize(LOG_PREFIX.append(Component.space()).append(message)));
    }

    private void logSevere(Component message) {
        logger.severe(LEGACY_SERIALIZER.serialize(LOG_PREFIX.append(Component.space()).append(message)));
    }
}