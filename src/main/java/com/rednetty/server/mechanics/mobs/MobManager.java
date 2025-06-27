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
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
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
 * Enhanced central manager for all mob-related operations with improved performance and 1.20.2 compatibility
 */
public class MobManager implements Listener {

    // ================ CONSTANTS ================

    /** Minimum respawn delay for all mob types (3 minutes) */
    private static final long MIN_RESPAWN_DELAY = 180000L;

    /** Maximum respawn delay cap (15 minutes) */
    private static final long MAX_RESPAWN_DELAY = 900000L;

    /** Maximum distance a mob can wander from its spawner */
    private static final double MAX_WANDERING_DISTANCE = 25.0;

    /** Position check interval in ticks (5 seconds) */
    private static final long POSITION_CHECK_INTERVAL = 100L;

    /** Name visibility timeout (6.5 seconds) - FIXED for 1.20.2 */
    private static final long NAME_VISIBILITY_TIMEOUT = 6500L;

    /** Critical state task interval (8 ticks) */
    private static final long CRITICAL_STATE_INTERVAL = 8L;

    /** Health bar visibility timeout - FIXED for 1.20.2 */
    private static final long HEALTH_BAR_TIMEOUT = 6500L;

    /** Critical sound interval (2 seconds between piston sounds) */
    private static final long CRITICAL_SOUND_INTERVAL = 2000L;

    /** Entity tracking validation interval */
    private static final long ENTITY_VALIDATION_INTERVAL = 5000L;

    // ================ SINGLETON ================

    private static volatile MobManager instance;

    // ================ CORE MAPS WITH THREAD SAFETY ================

    private final Map<UUID, CustomMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<LivingEntity, Integer> critMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> soundTimes = new ConcurrentHashMap<>();
    private final Map<UUID, NameTrackingData> nameTrackingData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageContributions = new ConcurrentHashMap<>();
    private final Map<Entity, Player> mobTargets = new ConcurrentHashMap<>();
    private final Set<UUID> processedEntities = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long> lastSafespotCheck = new ConcurrentHashMap<>();
    private final Map<UUID, String> entityToSpawner = new ConcurrentHashMap<>();

    // ================ IMPROVED NAME TRACKING ================

    /**
     * Enhanced name tracking data structure
     */
    private static class NameTrackingData {
        private final String originalName;
        private final long lastDamageTime;
        private final boolean isInCriticalState;
        private volatile boolean nameVisible;
        private volatile boolean isHealthBarActive;

        public NameTrackingData(String originalName, long lastDamageTime, boolean isInCriticalState) {
            this.originalName = originalName;
            this.lastDamageTime = lastDamageTime;
            this.isInCriticalState = isInCriticalState;
            this.nameVisible = true;
            this.isHealthBarActive = true;
        }

        public String getOriginalName() { return originalName; }
        public long getLastDamageTime() { return lastDamageTime; }
        public boolean isInCriticalState() { return isInCriticalState; }
        public boolean isNameVisible() { return nameVisible; }
        public void setNameVisible(boolean visible) { this.nameVisible = visible; }
        public boolean isHealthBarActive() { return isHealthBarActive; }
        public void setHealthBarActive(boolean active) { this.isHealthBarActive = active; }
    }

    // ================ THREAD SAFETY ================

    private final ReentrantReadWriteLock mobLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock nameLock = new ReentrantReadWriteLock();

    // ================ RESPAWN TRACKING ================

    private final Map<String, Long> respawnTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> mobTypeLastDeath = new ConcurrentHashMap<>();
    private final Map<UUID, Location> mobSpawnerLocations = new ConcurrentHashMap<>();

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

    // ================ TASKS ================

    private final TaskManager taskManager = new TaskManager();

    /**
     * Inner class to manage all recurring tasks with enhanced 1.20.2 compatibility
     */
    private class TaskManager {
        private BukkitTask criticalStateTask;
        private BukkitTask nameVisibilityTask;
        private BukkitTask activeMobsTask;
        private BukkitTask cleanupTask;
        private BukkitTask mobPositionTask;
        private BukkitTask entityValidationTask;

        void startAllTasks() {
            logger.info("§6[MobManager] §7Starting background tasks with 1.20.2 optimizations...");

            criticalStateTask = createCriticalStateTask();
            nameVisibilityTask = createNameVisibilityTask();
            activeMobsTask = createActiveMobsTask();
            cleanupTask = createCleanupTask();
            mobPositionTask = createMobPositionTask();
            entityValidationTask = createEntityValidationTask();

            logger.info("§a[MobManager] §7All background tasks started successfully");
        }

        void cancelAllTasks() {
            Arrays.asList(criticalStateTask, nameVisibilityTask, activeMobsTask,
                            cleanupTask, mobPositionTask, entityValidationTask)
                    .forEach(task -> { if (task != null) task.cancel(); });
        }

        private BukkitTask createCriticalStateTask() {
            return new BukkitRunnable() {
                @Override
                public void run() {
                    processCriticalState();
                }
            }.runTaskTimer(plugin, 20L, CRITICAL_STATE_INTERVAL);
        }

        private BukkitTask createNameVisibilityTask() {
            return new BukkitRunnable() {
                @Override
                public void run() {
                    updateNameVisibility();
                }
            }.runTaskTimer(plugin, 10L, 10L); // More frequent updates for better responsiveness
        }

        private BukkitTask createActiveMobsTask() {
            return new BukkitRunnable() {
                @Override
                public void run() {
                    updateActiveMobs();
                }
            }.runTaskTimer(plugin, 60L, 60L);
        }

        private BukkitTask createCleanupTask() {
            return new BukkitRunnable() {
                @Override
                public void run() {
                    performCleanup();
                }
            }.runTaskTimer(plugin, 1200L, 1200L);
        }

        private BukkitTask createMobPositionTask() {
            return new BukkitRunnable() {
                @Override
                public void run() {
                    checkMobPositions();
                }
            }.runTaskTimer(plugin, 60L, POSITION_CHECK_INTERVAL);
        }

        private BukkitTask createEntityValidationTask() {
            return new BukkitRunnable() {
                @Override
                public void run() {
                    validateEntityTracking();
                }
            }.runTaskTimer(plugin, 100L, ENTITY_VALIDATION_INTERVAL / 50); // Convert to ticks
        }
    }

    // ================ CONSTRUCTOR & INITIALIZATION ================

    private MobManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.spawner = MobSpawner.getInstance();
        this.debug = plugin.isDebugMode();

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

    private void loadConfiguration() {
        maxMobsPerSpawner = plugin.getConfig().getInt("mechanics.mobs.max-mobs-per-spawner", 10);
        playerDetectionRange = plugin.getConfig().getDouble("mechanics.mobs.player-detection-range", 40.0);
        mobRespawnDistanceCheck = plugin.getConfig().getDouble("mechanics.mobs.mob-respawn-distance-check", 25.0);

        // Validate ranges
        maxMobsPerSpawner = Math.max(1, Math.min(50, maxMobsPerSpawner));
        playerDetectionRange = Math.max(10.0, Math.min(100.0, playerDetectionRange));
        mobRespawnDistanceCheck = Math.max(5.0, Math.min(50.0, mobRespawnDistanceCheck));
    }

    public void initialize() {
        try {
            spawner.initialize();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            taskManager.startAllTasks();

            logger.info(String.format("§a[MobManager] §7Initialized with §e%d §7active spawners for Spigot 1.20.2",
                    spawner.getAllSpawners().size()));

            if (debug) {
                logger.info("§6[MobManager] §7Debug mode enabled with enhanced entity tracking");
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================ ENHANCED ENTITY VALIDATION FOR 1.20.2 ================

    /**
     * Validate entity tracking - ensures entities are properly tracked by the server
     */
    private void validateEntityTracking() {
        try {
            mobLock.readLock().lock();
            Set<UUID> invalidEntities = new HashSet<>();

            for (Map.Entry<UUID, CustomMob> entry : activeMobs.entrySet()) {
                UUID entityId = entry.getKey();
                CustomMob mob = entry.getValue();

                if (!isEntityValidAndTracked(mob.getEntity())) {
                    invalidEntities.add(entityId);
                    if (debug) {
                        logger.info("§6[MobManager] §7Found invalid entity: " + entityId);
                    }
                }
            }

            if (!invalidEntities.isEmpty()) {
                mobLock.readLock().unlock();
                mobLock.writeLock().lock();
                try {
                    for (UUID invalidId : invalidEntities) {
                        CustomMob removed = activeMobs.remove(invalidId);
                        if (removed != null && debug) {
                            logger.info("§6[MobManager] §7Removed invalid mob: " + removed.getType().getId());
                        }
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

    /**
     * Enhanced entity validation for 1.20.2
     */
    private boolean isEntityValidAndTracked(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }

        // Check if entity is properly tracked by server
        try {
            // Verify entity can be found by server
            Entity foundEntity = Bukkit.getEntity(entity.getUniqueId());
            if (foundEntity == null || !foundEntity.equals(entity)) {
                return false;
            }

            // Verify world is loaded and entity is in world
            World world = entity.getWorld();
            if (world == null || !entity.isInWorld()) {
                return false;
            }

            // Check if chunk is loaded (important for 1.20.2)
            Location loc = entity.getLocation();
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            if (debug) {
                logger.warning("§c[MobManager] Entity validation error: " + e.getMessage());
            }
            return false;
        }
    }

    // ================ ENHANCED NAME VISIBILITY SYSTEM ================

    /**
     * FIXED: Enhanced name visibility management for 1.20.2
     */
    public void updateNameVisibility() {
        try {
            long currentTime = System.currentTimeMillis();
            nameLock.writeLock().lock();

            Set<UUID> toRemove = new HashSet<>();

            for (Map.Entry<UUID, NameTrackingData> entry : nameTrackingData.entrySet()) {
                UUID entityId = entry.getKey();
                NameTrackingData trackingData = entry.getValue();

                Entity entity = Bukkit.getEntity(entityId);
                if (!(entity instanceof LivingEntity livingEntity) || !isEntityValidAndTracked(livingEntity)) {
                    toRemove.add(entityId);
                    continue;
                }

                // Skip players
                if (livingEntity instanceof Player) {
                    continue;
                }

                long timeSinceLastDamage = currentTime - trackingData.getLastDamageTime();
                boolean shouldShowHealthBar = timeSinceLastDamage < HEALTH_BAR_TIMEOUT || trackingData.isInCriticalState();

                if (shouldShowHealthBar && trackingData.isNameVisible()) {
                    // Keep showing health bar
                    if (!trackingData.isHealthBarActive()) {
                        updateEntityHealthBar(livingEntity);
                        trackingData.setHealthBarActive(true);
                    }
                } else if (!shouldShowHealthBar && trackingData.isNameVisible()) {
                    // Time to restore original name
                    restoreOriginalName(livingEntity, trackingData);
                    trackingData.setNameVisible(false);
                    trackingData.setHealthBarActive(false);
                    toRemove.add(entityId); // Remove from tracking after restoration
                }
            }

            // Clean up finished tracking entries
            for (UUID entityId : toRemove) {
                nameTrackingData.remove(entityId);
            }

            // Update CustomMob instances
            mobLock.readLock().lock();
            try {
                for (CustomMob mob : activeMobs.values()) {
                    if (mob.isValid()) {
                        mob.updateNameVisibility();
                    }
                }
            } finally {
                mobLock.readLock().unlock();
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Name visibility update error: " + e.getMessage());
            if (debug) e.printStackTrace();
        } finally {
            nameLock.writeLock().unlock();
        }
    }

    /**
     * FIXED: Enhanced health bar update for 1.20.2 compatibility
     */
    public void updateEntityHealthBar(LivingEntity entity) {
        if (!isEntityValidAndTracked(entity) || entity instanceof Player) {
            return;
        }

        try {
            // Store original name if not already stored
            storeOriginalNameIfNeeded(entity);

            int tier = getMobTier(entity);
            boolean inCritical = isInCriticalState(entity);

            String healthBar = MobUtils.generateHealthBar(entity, entity.getHealth(), entity.getMaxHealth(), tier, inCritical);

            // FIXED: Ensure name is actually set and visible
            entity.setCustomName(healthBar);
            entity.setCustomNameVisible(true);

            // Update tracking data
            UUID entityId = entity.getUniqueId();
            nameLock.writeLock().lock();
            try {
                NameTrackingData existing = nameTrackingData.get(entityId);
                if (existing != null) {
                    existing.setHealthBarActive(true);
                    existing.setNameVisible(true);
                } else {
                    String originalName = getStoredOriginalName(entity);
                    nameTrackingData.put(entityId, new NameTrackingData(originalName, System.currentTimeMillis(), inCritical));
                }
            } finally {
                nameLock.writeLock().unlock();
            }

            if (debug) {
                logger.info("§6[MobManager] §7Updated health bar for " + entity.getType() + " (Critical: " + inCritical + ")");
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Health bar update failed: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    /**
     * Store original name if needed, with enhanced validation
     */
    private void storeOriginalNameIfNeeded(LivingEntity entity) {
        if (!isEntityValidAndTracked(entity)) return;

        UUID entityId = entity.getUniqueId();

        // Check if we already have tracking data
        nameLock.readLock().lock();
        try {
            if (nameTrackingData.containsKey(entityId)) {
                return; // Already stored
            }
        } finally {
            nameLock.readLock().unlock();
        }

        // Store the original name
        String currentName = entity.getCustomName();
        if (currentName != null && !isHealthBar(currentName)) {
            nameLock.writeLock().lock();
            try {
                if (!nameTrackingData.containsKey(entityId)) {
                    nameTrackingData.put(entityId, new NameTrackingData(currentName, System.currentTimeMillis(), false));
                }
            } finally {
                nameLock.writeLock().unlock();
            }
        }
    }

    /**
     * Enhanced original name restoration
     */
    private void restoreOriginalName(LivingEntity entity, NameTrackingData trackingData) {
        if (!isEntityValidAndTracked(entity) || isInCriticalState(entity)) {
            return;
        }

        try {
            String nameToRestore = trackingData.getOriginalName();

            if (nameToRestore == null || nameToRestore.isEmpty()) {
                nameToRestore = generateDefaultName(entity);
            }

            if (nameToRestore != null && !nameToRestore.isEmpty()) {
                // Apply proper tier colors when restoring name
                String restoredName = applyTierColorsToName(entity, nameToRestore);

                // FIXED: Ensure name is properly set
                entity.setCustomName(restoredName);
                entity.setCustomNameVisible(true);

                if (debug) {
                    logger.info("§6[MobManager] §7Restored name for " + entity.getType() + ": " + restoredName);
                }
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Name restoration failed: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    private String getStoredOriginalName(LivingEntity entity) {
        UUID entityId = entity.getUniqueId();

        nameLock.readLock().lock();
        try {
            NameTrackingData data = nameTrackingData.get(entityId);
            if (data != null && data.getOriginalName() != null) {
                return data.getOriginalName();
            }
        } finally {
            nameLock.readLock().unlock();
        }

        // Fallback to metadata
        if (entity.hasMetadata("name")) {
            return entity.getMetadata("name").get(0).asString();
        }

        if (entity.hasMetadata("LightningMob")) {
            return entity.getMetadata("LightningMob").get(0).asString();
        }

        return generateDefaultName(entity);
    }

    private boolean isHealthBar(String name) {
        return name != null && (name.contains(ChatColor.GREEN + "|") || name.contains(ChatColor.GRAY + "|"));
    }

    private String applyTierColorsToName(LivingEntity entity, String baseName) {
        if (baseName.contains("§")) {
            return baseName; // Already has colors
        }

        String cleanName = ChatColor.stripColor(baseName);
        int tier = getMobTier(entity);
        boolean elite = isElite(entity);
        ChatColor tierColor = getTierColorFromTier(tier);

        return elite ?
                tierColor.toString() + ChatColor.BOLD + cleanName :
                tierColor + cleanName;
    }

    private ChatColor getTierColorFromTier(int tier) {
        switch (tier) {
            case 1: return ChatColor.WHITE;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.LIGHT_PURPLE;
            case 5: return ChatColor.YELLOW;
            case 6: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }

    private String generateDefaultName(LivingEntity entity) {
        if (entity == null) return "";

        String typeId = getEntityTypeId(entity);
        int tier = getMobTier(entity);
        boolean isElite = isElite(entity);

        MobType mobType = MobType.getById(typeId);
        if (mobType != null) {
            String tierName = mobType.getTierSpecificName(tier);
            ChatColor color = getTierColorFromTier(tier);
            return isElite ? color.toString() + ChatColor.BOLD + tierName : color + tierName;
        }

        ChatColor color = getTierColorFromTier(tier);
        String typeName = capitalizeEntityType(entity.getType());
        return isElite ? color.toString() + ChatColor.BOLD + typeName : color + typeName;
    }

    private String getEntityTypeId(LivingEntity entity) {
        if (entity.hasMetadata("type")) {
            return entity.getMetadata("type").get(0).asString();
        }
        if (entity.hasMetadata("customName")) {
            return entity.getMetadata("customName").get(0).asString();
        }
        return "unknown";
    }

    private String capitalizeEntityType(EntityType type) {
        if (type == null) return "Unknown";

        return Arrays.stream(type.name().toLowerCase().split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse("Unknown");
    }

    // ================ CRITICAL STATE PROCESSING ================

    private void processCriticalState() {
        try {
            processCustomMobCriticals();
            processLegacyMobCriticals();
        } catch (Exception e) {
            logger.severe("§c[MobManager] Critical state processing error: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    private void processCustomMobCriticals() {
        mobLock.readLock().lock();
        try {
            activeMobs.values().parallelStream()
                    .filter(Objects::nonNull)
                    .filter(CustomMob::isInCriticalState)
                    .forEach(this::processCustomMobCritical);
        } finally {
            mobLock.readLock().unlock();
        }
    }

    private void processCustomMobCritical(CustomMob mob) {
        try {
            LivingEntity entity = mob.getEntity();
            if (!isEntityValidAndTracked(entity)) return;

            boolean wasReadyState = mob.getCriticalStateDuration() < 0;
            boolean criticalEnded = mob.processCriticalStateTick();

            if (criticalEnded) {
                handleCriticalEnd(mob, wasReadyState);
            }

            addCriticalParticles(entity, mob);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error processing custom mob critical: " + e.getMessage());
        }
    }

    private void handleCriticalEnd(CustomMob mob, boolean wasReadyState) {
        if (mob instanceof EliteMob) {
            if (debug) {
                logger.info("§6[MobManager] §7Elite critical ended, executing attack");
            }
            ((EliteMob) mob).executeCriticalAttack();
        } else if (!wasReadyState) {
            handleRegularMobCriticalEnd(mob.getEntity());
        }
    }

    private void handleRegularMobCriticalEnd(LivingEntity entity) {
        if (debug) {
            logger.info("§6[MobManager] §7Regular mob ready to attack");
        }

        entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));
        spawnReadyParticles(entity);
    }

    private void addCriticalParticles(LivingEntity entity, CustomMob mob) {
        if (mob.getCriticalStateDuration() > 0) {
            Particle particleType = mob.isElite() ? Particle.SPELL_WITCH : Particle.CRIT;
            entity.getWorld().spawnParticle(particleType,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    5, 0.3, 0.3, 0.3, 0.05);
        }
    }

    private void spawnReadyParticles(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.VILLAGER_ANGRY,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                1, 0.1, 0.1, 0.1, 0.0);
    }

    private void processLegacyMobCriticals() {
        Iterator<Map.Entry<LivingEntity, Integer>> iterator = critMobs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Integer> entry = iterator.next();
            LivingEntity entity = entry.getKey();
            int step = entry.getValue();

            if (!isEntityValidAndTracked(entity)) {
                iterator.remove();
                continue;
            }

            if (step < 0) {
                handleReadyToAttackState(entity);
                continue;
            }

            processLegacyMobCritical(entity, step);
        }
    }

    private void handleReadyToAttackState(LivingEntity entity) {
        if (entity.getWorld().getTime() % 20 == 0) {
            spawnReadyParticles(entity);
        }
    }

    private void processLegacyMobCritical(LivingEntity entity, int step) {
        if (MobUtils.isElite(entity) && !MobUtils.isGolemBoss(entity)) {
            processEliteCritical(entity, step);
        } else {
            processRegularMobCritical(entity, step);
        }
    }

    private void processEliteCritical(LivingEntity entity, int step) {
        try {
            if (MobUtils.isFrozenBoss(entity)) {
                processFrozenBossCritical(entity, step);
            }

            critMobs.put(entity, step - 1);
            playEliteCriticalEffects(entity);

            if (step - 1 <= 0) {
                executeEliteCriticalAttack(entity);
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Elite critical processing error: " + e.getMessage());
        }
    }

    private void playEliteCriticalEffects(LivingEntity entity) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);
        entity.getWorld().spawnParticle(Particle.EXPLOSION_LARGE,
                entity.getLocation().clone().add(0, 1, 0),
                5, 0.3, 0.3, 0.3, 0.3f);
    }

    private void processFrozenBossCritical(LivingEntity entity, int step) {
        try {
            removeFrozenBossSlowEffect(entity);
            applyFrozenBossSpeedEffect(entity);

            if (step > 0) {
                applySlowToNearbyPlayers(entity);
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Frozen boss critical error: " + e.getMessage());
        }
    }

    private void removeFrozenBossSlowEffect(LivingEntity entity) {
        if (entity.hasPotionEffect(PotionEffectType.SLOW)) {
            entity.removePotionEffect(PotionEffectType.SLOW);
        }
    }

    private void applyFrozenBossSpeedEffect(LivingEntity entity) {
        boolean lowHealth = entity.getHealth() < (YakRealms.isT6Enabled() ? 100000 : 50000);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, lowHealth ? 1 : 0), true);
    }

    private void applySlowToNearbyPlayers(LivingEntity entity) {
        entity.getNearbyEntities(8.0, 8.0, 8.0).stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .forEach(player -> player.addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOW, 25, 1), true));
    }

    private void processRegularMobCritical(LivingEntity entity, int step) {
        if (step > 0) {
            critMobs.put(entity, step - 1);
            playRegularCriticalEffects(entity);
        }

        if (step == 1) { // Will become 0 after decrement
            transitionToReadyState(entity);
        }
    }

    private void playRegularCriticalEffects(LivingEntity entity) {
        // FIXED: Play piston sound only every 2 seconds instead of every tick
        UUID entityId = entity.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastSoundTime = soundTimes.get(entityId);

        if (lastSoundTime == null || (currentTime - lastSoundTime) >= CRITICAL_SOUND_INTERVAL) {
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);
            soundTimes.put(entityId, currentTime);
        }

        entity.getWorld().spawnParticle(Particle.CRIT,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                10, 0.3, 0.3, 0.3, 0.1);
    }

    private void transitionToReadyState(LivingEntity entity) {
        if (debug) {
            logger.info("§6[MobManager] §7Regular mob countdown complete, ready to attack");
        }

        critMobs.put(entity, -1);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        entity.getWorld().spawnParticle(Particle.SPELL_WITCH,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                15, 0.3, 0.3, 0.3, 0.1);

        // FIXED: Use enhanced name tracking
        UUID entityId = entity.getUniqueId();
        nameLock.writeLock().lock();
        try {
            NameTrackingData existing = nameTrackingData.get(entityId);
            if (existing != null) {
                nameTrackingData.put(entityId, new NameTrackingData(existing.getOriginalName(), System.currentTimeMillis(), true));
            } else {
                String originalName = getStoredOriginalName(entity);
                nameTrackingData.put(entityId, new NameTrackingData(originalName, System.currentTimeMillis(), true));
            }
        } finally {
            nameLock.writeLock().unlock();
        }

        updateEntityHealthBar(entity);
    }

    // ================ ELITE CRITICAL ATTACK EXECUTION ================

    private void executeEliteCriticalAttack(LivingEntity entity) {
        try {
            critMobs.remove(entity);
            soundTimes.remove(entity.getUniqueId());

            if (MobUtils.isFrozenBoss(entity) &&
                    entity.getHealth() < (YakRealms.isT6Enabled() ? 100000 : 50000)) {
                critMobs.put(entity, 10);
                if (debug) {
                    logger.info("§6[MobManager] §7Frozen boss restarting critical state");
                }
            }

            int damage = calculateEliteCriticalDamage(entity);
            int playersHit = applyEliteCriticalDamage(entity, damage);

            playEliteCriticalAttackEffects(entity);
            resetElitePotionEffects(entity);

            if (debug) {
                logger.info(String.format("§6[MobManager] §7Elite critical hit §e%d §7players for §c%d §7damage",
                        playersHit, damage));
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Elite critical attack error: " + e.getMessage());
        }
    }

    private int calculateEliteCriticalDamage(LivingEntity entity) {
        ItemStack weapon = entity.getEquipment().getItemInMainHand();
        List<Integer> damageRange = MobUtils.getDamageRange(weapon);
        int min = damageRange.get(0);
        int max = damageRange.get(1);

        return (ThreadLocalRandom.current().nextInt(max - min + 1) + min) * 3;
    }

    private int applyEliteCriticalDamage(LivingEntity entity, int damage) {
        int playersHit = 0;

        for (Entity nearby : entity.getNearbyEntities(7.0, 7.0, 7.0)) {
            if (nearby instanceof Player player) {
                playersHit++;

                // Temporarily clear critical state for damage calculation
                boolean hadCrit = critMobs.containsKey(entity);
                critMobs.remove(entity);

                player.damage(damage, entity);

                if (hadCrit) {
                    critMobs.put(entity, 0);
                }

                applyKnockback(player, entity);
            }
        }

        return playersHit;
    }

    private void applyKnockback(Player player, LivingEntity entity) {
        Vector knockback = player.getLocation().clone().toVector()
                .subtract(entity.getLocation().toVector());

        if (knockback.length() > 0) {
            knockback.normalize();

            if (MobUtils.isFrozenBoss(entity)) {
                player.setVelocity(knockback.multiply(-3)); // Reverse knockback
            } else {
                player.setVelocity(knockback.multiply(3));
            }
        }
    }

    private void playEliteCriticalAttackEffects(LivingEntity entity) {
        Location loc = entity.getLocation();
        World world = entity.getWorld();

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION_HUGE, loc.clone().add(0, 1, 0), 10, 0, 0, 0, 1.0f);
        world.spawnParticle(Particle.FLAME, loc.clone().add(0.0, 0.5, 0.0), 40, 1.0, 0.2, 1.0, 0.1);
    }

    private void resetElitePotionEffects(LivingEntity entity) {
        try {
            removePotionEffect(entity, PotionEffectType.SLOW);
            removePotionEffect(entity, PotionEffectType.JUMP);
            removePotionEffect(entity, PotionEffectType.GLOWING);

            // Reapply staff slow effect if needed
            if (hasStaffWeapon(entity)) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 1), true);
            }

            // Apply elite movement effect
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1), true);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error resetting elite effects: " + e.getMessage());
        }
    }

    private void removePotionEffect(LivingEntity entity, PotionEffectType effectType) {
        if (entity.hasPotionEffect(effectType)) {
            entity.removePotionEffect(effectType);
        }
    }

    private boolean hasStaffWeapon(LivingEntity entity) {
        ItemStack weapon = entity.getEquipment().getItemInMainHand();
        return weapon != null && weapon.getType().name().contains("_HOE");
    }

    // ================ NORMAL MOB CRITICAL ATTACK ================

    private int executeNormalMobCriticalAttack(LivingEntity entity, Player player, int baseDamage) {
        try {
            critMobs.remove(entity);
            soundTimes.remove(entity.getUniqueId());

            if (entity.hasMetadata("criticalState")) {
                entity.removeMetadata("criticalState", plugin);
            }

            int critDamage = baseDamage * 3;

            playNormalCriticalEffects(entity, player);

            if (debug) {
                logger.info("§6[MobManager] §7Normal critical hit executed! Damage: §c" + critDamage);
            }

            return critDamage;
        } catch (Exception e) {
            logger.warning("§c[MobManager] Normal critical attack error: " + e.getMessage());
            return baseDamage;
        }
    }

    private void playNormalCriticalEffects(LivingEntity entity, Player player) {
        Location loc = entity.getLocation();

        entity.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);
        entity.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

        // Mild knockback
        Vector knockback = player.getLocation().clone().toVector().subtract(loc.toVector());
        if (knockback.length() > 0) {
            knockback.normalize();
            player.setVelocity(knockback.multiply(1.5));
        }
    }

    // ================ ACTIVE MOBS MANAGEMENT ================

    private void updateActiveMobs() {
        updateWorldBoss();
        removeInvalidMobs();
    }

    private void updateWorldBoss() {
        if (activeWorldBoss != null && activeWorldBoss.isValid()) {
            activeWorldBoss.update();
            activeWorldBoss.processPhaseTransitions();
        }
    }

    private void removeInvalidMobs() {
        mobLock.writeLock().lock();
        try {
            activeMobs.entrySet().removeIf(entry -> {
                CustomMob mob = entry.getValue();
                return mob == null || !mob.isValid();
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
                if (mob.isValid()) {
                    if (handleMobPositionCheck(mob)) {
                        teleported++;
                    }
                }
            }

            if (debug && teleported > 0) {
                logger.info(String.format("§6[MobManager] §7Teleported §e%d §7mobs back to spawners", teleported));
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Position check error: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    private boolean handleMobPositionCheck(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (!isEntityValidAndTracked(entity)) return false;

        Location spawnerLoc = getSpawnerLocation(entity);
        if (spawnerLoc == null) return false;

        PositionCheckResult result = checkEntityPosition(entity, spawnerLoc);

        if (result.shouldTeleport) {
            teleportMobToSpawner(entity, spawnerLoc, result.reason);
            return true;
        }

        return false;
    }

    private static class PositionCheckResult {
        final boolean shouldTeleport;
        final String reason;

        PositionCheckResult(boolean shouldTeleport, String reason) {
            this.shouldTeleport = shouldTeleport;
            this.reason = reason;
        }
    }

    private PositionCheckResult checkEntityPosition(LivingEntity entity, Location spawnerLoc) {
        // World check
        if (!entity.getLocation().getWorld().equals(spawnerLoc.getWorld())) {
            return new PositionCheckResult(true, "changed worlds");
        }

        // Distance check
        double distanceSquared = entity.getLocation().distanceSquared(spawnerLoc);
        if (distanceSquared > (MAX_WANDERING_DISTANCE * MAX_WANDERING_DISTANCE)) {
            return new PositionCheckResult(true, "wandered too far");
        }

        // Safezone check
        if (isInSafezone(entity)) {
            return new PositionCheckResult(true, "entered safezone");
        }

        return new PositionCheckResult(false, null);
    }

    private Location getSpawnerLocation(LivingEntity entity) {
        if (entity == null) return null;

        UUID entityId = entity.getUniqueId();

        // Check cache
        if (mobSpawnerLocations.containsKey(entityId)) {
            return mobSpawnerLocations.get(entityId);
        }

        // Check metadata
        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();
            Location spawnerLoc = findSpawnerById(spawnerId);
            if (spawnerLoc != null) {
                mobSpawnerLocations.put(entityId, spawnerLoc);
                return spawnerLoc;
            }
        }

        // Find nearest spawner
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

    private boolean isInSafezone(LivingEntity entity) {
        return AlignmentMechanics.isSafeZone(entity.getLocation());
    }

    private void teleportMobToSpawner(LivingEntity entity, Location spawnerLoc, String reason) {
        if (entity == null || spawnerLoc == null) return;

        try {
            Location safeLoc = createSafeTeleportLocation(spawnerLoc);

            playTeleportEffects(entity, safeLoc);
            resetMobTargeting(entity);
            entity.teleport(safeLoc);
            playArrivalEffects(safeLoc);

            if (debug) {
                logger.info(String.format("§6[MobManager] §7Teleported %s back to spawner: %s",
                        entity.getType(), reason));
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Teleport error: " + e.getMessage());
        }
    }

    private Location createSafeTeleportLocation(Location spawnerLoc) {
        Location safeLoc = spawnerLoc.clone().add(
                (Math.random() * 4 - 2),
                1.0,
                (Math.random() * 4 - 2)
        );

        safeLoc.setX(safeLoc.getBlockX() + 0.5);
        safeLoc.setZ(safeLoc.getBlockZ() + 0.5);

        return safeLoc;
    }

    private void playTeleportEffects(LivingEntity entity, Location destination) {
        entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation().clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void playArrivalEffects(Location location) {
        location.getWorld().spawnParticle(Particle.PORTAL, location.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void resetMobTargeting(LivingEntity entity) {
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
    }

    // ================ CRITICAL HIT SYSTEM ================

    public void rollForCriticalHit(LivingEntity entity, double damage) {
        try {
            if (!canRollCritical(entity)) return;

            int tier = getMobTier(entity);
            storeOriginalNameIfNeeded(entity);

            CriticalRollResult result = calculateCriticalRoll(entity, tier);

            if (debug) {
                logger.info(String.format("§6[MobManager] §7Critical check: %s T%d, Roll: %d/%d",
                        entity.getType(), tier, result.roll, result.chance));
            }

            if (result.isSuccess) {
                applyCriticalHit(entity, tier);
            }

            checkFrozenBossSpecialCritical(entity);

        } catch (Exception e) {
            logger.severe("§c[MobManager] Critical roll error: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    private boolean canRollCritical(LivingEntity entity) {
        return !isInCriticalState(entity);
    }

    private static class CriticalRollResult {
        final int roll;
        final int chance;
        final boolean isSuccess;

        CriticalRollResult(int roll, int chance) {
            this.roll = roll;
            this.chance = chance;
            this.isSuccess = roll <= chance;
        }
    }

    private CriticalRollResult calculateCriticalRoll(LivingEntity entity, int tier) {
        int critChance = getCriticalChance(tier);

        // Golem boss berserker immunity
        if (MobUtils.isGolemBoss(entity) && MobUtils.getMetadataInt(entity, "stage", 0) == 3) {
            critChance = 0;
        }

        int roll = ThreadLocalRandom.current().nextInt(200) + 1;
        return new CriticalRollResult(roll, critChance);
    }

    private int getCriticalChance(int tier) {
        switch (tier) {
            case 1: return 5;   // 2.5%
            case 2: return 7;   // 3.5%
            case 3: return 10;  // 5%
            case 4: return 13;  // 6.5%
            default: return 20; // 10% for tier 5+
        }
    }

    private void applyCriticalHit(LivingEntity entity, int tier) {
        if (debug) {
            logger.info(String.format("§6[MobManager] §c*** CRITICAL HIT *** §7on %s T%d (Elite: %s)",
                    entity.getType(), tier, isElite(entity)));
        }

        CustomMob mob = getCustomMob(entity);
        if (mob != null) {
            applyCriticalToCustomMob(mob);
        } else {
            applyCriticalToLegacyMob(entity);
        }

        // FIXED: Use enhanced name tracking
        UUID entityId = entity.getUniqueId();
        nameLock.writeLock().lock();
        try {
            NameTrackingData existing = nameTrackingData.get(entityId);
            if (existing != null) {
                nameTrackingData.put(entityId, new NameTrackingData(existing.getOriginalName(), System.currentTimeMillis(), true));
            } else {
                String originalName = getStoredOriginalName(entity);
                nameTrackingData.put(entityId, new NameTrackingData(originalName, System.currentTimeMillis(), true));
            }
        } finally {
            nameLock.writeLock().unlock();
        }

        addCriticalVisualEffects(entity);
    }

    private void applyCriticalToCustomMob(CustomMob mob) {
        mob.setCriticalState(mob.isElite() ? 12 : 6);
        mob.getEntity().setMetadata("criticalState", new FixedMetadataValue(plugin, true));
        mob.updateHealthBar();
    }

    private void applyCriticalToLegacyMob(LivingEntity entity) {
        boolean isElite = isElite(entity);

        if (isElite && !MobUtils.isGolemBoss(entity)) {
            applyEliteCriticalEffects(entity);
            critMobs.put(entity, 12);
        } else {
            applyRegularCriticalEffects(entity);
            critMobs.put(entity, 6);
        }

        entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));
        updateEntityHealthBar(entity);
    }

    private void applyEliteCriticalEffects(LivingEntity entity) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);

        if (MobUtils.isFrozenBoss(entity)) {
            applySlowToNearbyPlayers(entity);
        } else {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 10), true);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 127), true);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0), true);
        }
    }

    private void applyRegularCriticalEffects(LivingEntity entity) {
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);
        entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().clone().add(0.0, 1.0, 0.0), 20, 0.3, 0.3, 0.3, 0.5);
    }

    private void addCriticalVisualEffects(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation().clone().add(0.0, 1.5, 0.0), 30, 0.3, 0.3, 0.3, 0.05);
    }

    private void checkFrozenBossSpecialCritical(LivingEntity entity) {
        if (MobUtils.isFrozenBoss(entity) &&
                entity.getHealth() < (YakRealms.isT6Enabled() ? 100000 : 50000) &&
                !isInCriticalState(entity)) {

            if (debug) {
                logger.info("§6[MobManager] §7Applying forced critical to Frozen Boss at low health");
            }

            CustomMob mob = getCustomMob(entity);
            if (mob != null) {
                mob.setCriticalState(10);
            } else {
                critMobs.put(entity, 10);
                entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));
                updateEntityHealthBar(entity);
            }
        }
    }

    // ================ MOB SPAWNING ================

    public LivingEntity spawnMob(Location location, String type, int tier, boolean elite) {
        if (!isValidSpawnRequest(location, type, tier)) {
            return null;
        }

        MobType mobType = MobType.getById(type);
        tier = validateTier(tier, mobType);
        elite = elite || mobType.isElite();

        if (!canRespawn(type, tier, elite)) {
            if (debug) {
                logger.info(String.format("§6[MobManager] §cBlocked spawn: %s T%d%s (cooldown)",
                        type, tier, elite ? "+" : ""));
            }
            return null;
        }

        CustomMob mob = createMobInstance(mobType, tier, elite);
        if (mob == null) return null;

        if (mob.spawn(location)) {
            handleSuccessfulSpawn(mob, location);
            return mob.getEntity();
        } else {
            logger.warning("§c[MobManager] Failed to spawn mob: " + type);
            return null;
        }
    }

    private boolean isValidSpawnRequest(Location location, String type, int tier) {
        if (location == null || location.getWorld() == null) {
            logger.warning("§c[MobManager] Invalid spawn location");
            return false;
        }

        if (type == null || type.isEmpty()) {
            logger.warning("§c[MobManager] Invalid mob type");
            return false;
        }

        return true;
    }

    private int validateTier(int tier, MobType mobType) {
        if (mobType == null) return tier;

        tier = Math.max(mobType.getMinTier(), Math.min(mobType.getMaxTier(), tier));

        if (tier > 5 && !YakRealms.isT6Enabled()) {
            tier = 5;
        }

        return tier;
    }

    private CustomMob createMobInstance(MobType mobType, int tier, boolean elite) {
        try {
            if (mobType.isWorldBoss()) {
                if (activeWorldBoss != null && activeWorldBoss.isValid()) {
                    logger.warning("§c[MobManager] Cannot spawn world boss - another is active");
                    return null;
                }
                return new WorldBoss(mobType, tier);
            } else if (elite) {
                return new EliteMob(mobType, tier);
            } else {
                return new CustomMob(mobType, tier, false);
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Error creating mob instance: " + e.getMessage());
            return null;
        }
    }

    private void handleSuccessfulSpawn(CustomMob mob, Location location) {
        if (debug) {
            logger.info(String.format("§a[MobManager] §7Spawned %s T%d%s",
                    mob.getType().getId(), mob.getTier(), mob.isElite() ? "+" : ""));
        }

        if (location.getBlock().getType().toString().contains("SPAWNER")) {
            spawner.registerMobSpawned(location.getBlock().getLocation(), mob.getEntity());
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

        storeSpawnerLocation(entity);

        if (mob instanceof WorldBoss) {
            activeWorldBoss = (WorldBoss) mob;
        }

        // FIXED: Enhanced original name storage
        if (entity.getCustomName() != null) {
            UUID entityId = entity.getUniqueId();
            nameLock.writeLock().lock();
            try {
                if (!nameTrackingData.containsKey(entityId)) {
                    nameTrackingData.put(entityId, new NameTrackingData(entity.getCustomName(), 0, false));
                }
            } finally {
                nameLock.writeLock().unlock();
            }
        }
    }

    private void storeSpawnerLocation(LivingEntity entity) {
        UUID entityId = entity.getUniqueId();

        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();
            Location spawnerLoc = findSpawnerById(spawnerId);
            if (spawnerLoc != null) {
                mobSpawnerLocations.put(entityId, spawnerLoc);
                return;
            }
        }

        Location nearestSpawner = findNearestSpawner(entity.getLocation(), 10.0);
        if (nearestSpawner != null) {
            mobSpawnerLocations.put(entityId, nearestSpawner);
        } else {
            mobSpawnerLocations.put(entityId, entity.getLocation().clone());
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

        // Clean up tracking data
        nameLock.writeLock().lock();
        try {
            nameTrackingData.remove(entityId);
        } finally {
            nameLock.writeLock().unlock();
        }

        mobSpawnerLocations.remove(entityId);
        soundTimes.remove(entityId);

        if (mob instanceof WorldBoss) {
            unregisterWorldBoss();
        }
    }

    public void unregisterWorldBoss() {
        activeWorldBoss = null;
    }

    public void registerWorldBoss(WorldBoss boss) {
        if (boss == null || boss.getEntity() == null) return;

        if (activeWorldBoss != null) {
            unregisterWorldBoss();
        }

        activeWorldBoss = boss;

        mobLock.writeLock().lock();
        try {
            activeMobs.put(boss.getEntity().getUniqueId(), boss);
        } finally {
            mobLock.writeLock().unlock();
        }

        // FIXED: Enhanced original name storage
        if (boss.getEntity().getCustomName() != null) {
            UUID entityId = boss.getEntity().getUniqueId();
            nameLock.writeLock().lock();
            try {
                if (!nameTrackingData.containsKey(entityId)) {
                    nameTrackingData.put(entityId, new NameTrackingData(boss.getEntity().getCustomName(), 0, false));
                }
            } finally {
                nameLock.writeLock().unlock();
            }
        }
    }

    // ================ DAMAGE PROCESSING ================

    public double damageEntity(LivingEntity entity, double damage) {
        if (!isEntityValidAndTracked(entity)) return 0;

        CustomMob mob = getCustomMob(entity);
        if (mob != null) {
            return mob.damage(damage);
        }

        return processLegacyMobDamage(entity, damage);
    }

    private double processLegacyMobDamage(LivingEntity entity, double damage) {
        double health = entity.getHealth() - damage;

        if (health <= 0) {
            entity.setHealth(0);
        } else {
            entity.setHealth(health);
            updateEntityHealthBar(entity);
            checkFrozenBossSpecialCritical(entity);
        }

        return damage;
    }

    // ================ DAMAGE TRACKING ================

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

    public List<Map.Entry<UUID, Double>> getSortedDamageContributors(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();

        Map<UUID, Double> damageMap = damageContributions.get(entity.getUniqueId());
        if (damageMap == null || damageMap.isEmpty()) return Collections.emptyList();

        return damageMap.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .collect(ArrayList::new, (list, entry) -> list.add(entry), List::addAll);
    }

    // ================ CLEANUP OPERATIONS ================

    private void performCleanup() {
        try {
            cleanupDamageTracking();
            cleanupEntityMappings();
            cleanupMobLocations();
            cleanupNameTracking();
            processedEntities.clear();

            if (debug) {
                logger.info("§6[MobManager] §7Performed maintenance cleanup with enhanced 1.20.2 compatibility");
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Cleanup error: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    private void cleanupDamageTracking() {
        Set<UUID> invalidEntities = damageContributions.keySet().stream()
                .filter(id -> !isEntityValid(id))
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidEntities.forEach(damageContributions::remove);
    }

    private void cleanupEntityMappings() {
        Set<UUID> invalidIds = entityToSpawner.keySet().stream()
                .filter(id -> !isEntityValid(id))
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidIds.forEach(entityToSpawner::remove);
    }

    private void cleanupMobLocations() {
        Set<UUID> invalidIds = mobSpawnerLocations.keySet().stream()
                .filter(id -> !isEntityValid(id))
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidIds.forEach(mobSpawnerLocations::remove);
    }

    private void cleanupNameTracking() {
        nameLock.writeLock().lock();
        try {
            Set<UUID> invalidIds = nameTrackingData.keySet().stream()
                    .filter(id -> !isEntityValid(id))
                    .collect(HashSet::new, Set::add, Set::addAll);

            invalidIds.forEach(nameTrackingData::remove);
        } finally {
            nameLock.writeLock().unlock();
        }
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

        if (debug) {
            logger.info(String.format("§6[MobManager] §7Recorded death: %s (respawn in %ds)",
                    key, respawnDelay / 1000));
        }

        return respawnTime;
    }

    public boolean canRespawn(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return true;

        String key = getResponKey(mobType, tier, elite);
        Long respawnTime = respawnTimes.get(key);

        if (respawnTime == null) return true;

        boolean canRespawn = System.currentTimeMillis() >= respawnTime;

        if (debug && !canRespawn) {
            long remaining = (respawnTime - System.currentTimeMillis()) / 1000;
            logger.info(String.format("§6[MobManager] §7Prevented respawn: %s (%.0fs remaining)", key, remaining));
        }

        return canRespawn;
    }

    public String getResponKey(String mobType, int tier, boolean elite) {
        return String.format("%s:%d:%s", mobType, tier, elite ? "elite" : "normal");
    }

    public long getResponTime(String mobType, int tier, boolean elite) {
        String key = getResponKey(mobType, tier, elite);
        return respawnTimes.getOrDefault(key, 0L);
    }

    public long getLastDeathTime(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return 0;

        String key = getResponKey(mobType, tier, elite);
        return mobTypeLastDeath.getOrDefault(key, 0L);
    }

    public long getRemainingRespawnTime(String mobType, int tier, boolean elite) {
        String key = getResponKey(mobType, tier, elite);
        Long respawnTime = respawnTimes.get(key);

        if (respawnTime == null) return 0;

        return Math.max(0, respawnTime - System.currentTimeMillis());
    }

    public void clearAllRespawnTimers() {
        respawnTimes.clear();
        mobTypeLastDeath.clear();
        logger.info("§a[MobManager] §7All respawn timers cleared");
    }

    // ================ UTILITY METHODS ================

    public boolean isSafeSpot(Player player, LivingEntity mob) {
        return MobUtils.isSafeSpot(player, mob);
    }

    public boolean isWorldBoss(LivingEntity entity) {
        if (entity == null) return false;

        if (activeWorldBoss != null && activeWorldBoss.getEntity() != null) {
            if (activeWorldBoss.getEntity().equals(entity)) return true;
        }

        return entity.hasMetadata("worldboss") || MobUtils.isWorldBoss(entity);
    }

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

    public WorldBoss getActiveWorldBoss() {
        return activeWorldBoss;
    }

    private boolean isInCriticalState(LivingEntity entity) {
        return critMobs.containsKey(entity) || entity.hasMetadata("criticalState");
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

    public int toggleSpawnerVisibility(Location center, int radius, boolean show) {
        return center != null ? spawner.toggleSpawnerVisibility(center, radius, show) : 0;
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

    public List<Location> findNearbySpawners(Location center, double radius) {
        return center != null ? spawner.findSpawnersInRadius(center, radius) : Collections.emptyList();
    }

    public boolean resetSpawner(Location location) {
        return location != null && spawner.resetSpawner(location);
    }

    public Location findNearestSpawner(Location location, double maxDistance) {
        return spawner.findNearestSpawner(location, maxDistance);
    }

    private String generateSpawnerId(Location location) {
        return String.format("%s_%d_%d_%d",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
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

    public boolean addSpawnerFromTemplate(Location location, String templateName) {
        return spawner.addSpawnerFromTemplate(location, templateName);
    }

    public Map<String, String> getAllSpawnerTemplates() {
        return spawner.getAllTemplates();
    }

    // ================ INFORMATION DISPLAY ================

    public void displayMobInfo(Player player, LivingEntity entity) {
        if (player == null || entity == null) return;

        MobInfoDisplay display = new MobInfoDisplay(player, entity, this);
        display.show();
    }

    private static class MobInfoDisplay {
        private final Player player;
        private final LivingEntity entity;
        private final MobManager manager;

        MobInfoDisplay(Player player, LivingEntity entity, MobManager manager) {
            this.player = player;
            this.entity = entity;
            this.manager = manager;
        }

        void show() {
            player.sendMessage("§6╔════ §eMob Information §6════╗");

            showBasicInfo();
            showHealthInfo();
            showStateInfo();
            showEquipmentInfo();
            showSpecialInfo();
            showLocationInfo();
            showManagementInfo();

            player.sendMessage("§6╚════════════════════════════╝");
        }

        private void showBasicInfo() {
            player.sendMessage(String.format("§6║ §eEntity ID: §f%d (%s)",
                    entity.getEntityId(),
                    entity.getUniqueId().toString().substring(0, 8)));

            player.sendMessage(String.format("§6║ §eEntity Type: §f%s", entity.getType().name()));

            if (entity.getCustomName() != null) {
                player.sendMessage("§6║ §eCustom Name: " + entity.getCustomName());
            }

            String mobType = manager.getEntityTypeId(entity);
            player.sendMessage(String.format("§6║ §eMob Type: §f%s", mobType));

            int tier = manager.getMobTier(entity);
            player.sendMessage(String.format("§6║ §eTier: §f%d %s", tier, getTierColorName(tier)));

            boolean elite = manager.isElite(entity);
            player.sendMessage(String.format("§6║ §eElite: %s", elite ? "§aYes" : "§cNo"));

            boolean worldBoss = manager.isWorldBoss(entity);
            player.sendMessage(String.format("§6║ §eWorld Boss: %s", worldBoss ? "§aYes" : "§cNo"));
        }

        private void showHealthInfo() {
            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();
            double healthPercent = (health / maxHealth) * 100;

            player.sendMessage(String.format("§6║ §eHealth: §f%.1f/%.1f (%.1f%%)",
                    health, maxHealth, healthPercent));
        }

        private void showStateInfo() {
            boolean inCritical = manager.isInCriticalState(entity);
            player.sendMessage(String.format("§6║ §eCritical State: %s", inCritical ? "§aYes" : "§cNo"));

            if (inCritical && manager.critMobs.containsKey(entity)) {
                int critState = manager.critMobs.get(entity);
                if (critState < 0) {
                    player.sendMessage("§6║ §eCritical State: §aReady to Attack");
                } else {
                    player.sendMessage(String.format("§6║ §eCritical Ticks: §f%d", critState));
                }
            }
        }

        private void showEquipmentInfo() {
            player.sendMessage("§6║ §eEquipment:");

            ItemStack weapon = entity.getEquipment().getItemInMainHand();
            if (weapon != null && weapon.getType() != Material.AIR) {
                String weaponName = weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName() ?
                        weapon.getItemMeta().getDisplayName() : weapon.getType().name();

                player.sendMessage(String.format("§6║   §eWeapon: §f%s", weaponName));

                List<Integer> damageRange = MobUtils.getDamageRange(weapon);
                player.sendMessage(String.format("§6║   §eDamage Range: §f%d - %d",
                        damageRange.get(0), damageRange.get(1)));
            } else {
                player.sendMessage("§6║   §eWeapon: §cNone");
            }

            ItemStack[] armor = entity.getEquipment().getArmorContents();
            int armorPieces = (int) Arrays.stream(armor)
                    .filter(Objects::nonNull)
                    .filter(item -> item.getType() != Material.AIR)
                    .count();

            player.sendMessage(String.format("§6║   §eArmor: §f%d/4 pieces", armorPieces));
        }

        private void showSpecialInfo() {
            if (MobUtils.isFrozenBoss(entity)) {
                player.sendMessage("§6║ §eSpecial Type: §bFrozen Boss");
            } else if (MobUtils.isGolemBoss(entity)) {
                int stage = MobUtils.getGolemStage(entity);
                player.sendMessage(String.format("§6║ §eSpecial Type: §6Golem Boss (Stage %d)", stage));
            }

            if (entity.hasMetadata("LightningMultiplier")) {
                int multiplier = entity.getMetadata("LightningMultiplier").get(0).asInt();
                player.sendMessage(String.format("§6║ §eLightning Multiplier: §6⚡ §f%dx", multiplier));
            }
        }

        private void showLocationInfo() {
            Location nearestSpawner = manager.findNearestSpawner(entity.getLocation(),
                    manager.getMobRespawnDistanceCheck());
            if (nearestSpawner != null) {
                double distance = entity.getLocation().distance(nearestSpawner);
                player.sendMessage(String.format("§6║ §eNearest Spawner: §f%s §7(%.1f blocks)",
                        formatLocation(nearestSpawner), distance));
            }
        }

        private void showManagementInfo() {
            if (manager.getCustomMob(entity) != null) {
                player.sendMessage("§6║ §eManaged By: §fCustomMob System");
            } else {
                player.sendMessage("§6║ §eManaged By: §fLegacy System");
            }
        }

        private String getTierColorName(int tier) {
            switch (tier) {
                case 1: return "§7(White)";
                case 2: return "§a(Green)";
                case 3: return "§b(Aqua)";
                case 4: return "§d(Light Purple)";
                case 5: return "§e(Yellow)";
                case 6: return "§9(Blue)";
                default: return "";
            }
        }

        private String formatLocation(Location location) {
            return String.format("%s [%d, %d, %d]",
                    location.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
        }
    }

    // ================ EVENT HANDLERS ================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity) || event.getEntity() instanceof Player) {
                return;
            }

            Player damager = extractPlayerDamager(event);
            if (damager == null) return;

            double damage = event.getFinalDamage();

            storeOriginalNameIfNeeded(entity);
            rollForCriticalHit(entity, damage);
            trackDamage(entity, damager, damage);

            processMobDamage(entity, damage);
            handleSafespotCheck(damager, entity);

        } catch (Exception e) {
            logger.severe("§c[MobManager] Entity damage event error: " + e.getMessage());
            if (debug) e.printStackTrace();
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

    private void processMobDamage(LivingEntity entity, double damage) {
        CustomMob mob = getCustomMob(entity);
        if (mob != null) {
            mob.damage(damage);
            mob.nameVisible = true;
            mob.updateHealthBar();
        } else {
            damageEntity(entity, damage);
            // FIXED: Use enhanced name tracking
            UUID entityId = entity.getUniqueId();
            nameLock.writeLock().lock();
            try {
                NameTrackingData existing = nameTrackingData.get(entityId);
                if (existing != null) {
                    nameTrackingData.put(entityId, new NameTrackingData(existing.getOriginalName(), System.currentTimeMillis(), existing.isInCriticalState()));
                } else {
                    String originalName = getStoredOriginalName(entity);
                    nameTrackingData.put(entityId, new NameTrackingData(originalName, System.currentTimeMillis(), false));
                }
            } finally {
                nameLock.writeLock().unlock();
            }
            updateEntityHealthBar(entity);
        }
    }

    private void handleSafespotCheck(Player damager, LivingEntity entity) {
        if (isSafeSpot(damager, entity)) {
            entity.teleport(damager.getLocation().clone().add(0, 1, 0));

            if (debug) {
                logger.info(String.format("§6[MobManager] §7Detected safespotting by %s - teleporting mob",
                        damager.getName()));
            }
        }
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobHitPlayer(EntityDamageByEntityEvent event) {
        try {
            if (event.getDamage() <= 0.0 ||
                    !(event.getDamager() instanceof LivingEntity mob) ||
                    !(event.getEntity() instanceof Player player)) {
                return;
            }

            handleMobSound(mob);
            int damage = calculateMobDamage(mob, player);

            if (damage > 0) {
                event.setDamage(damage);

                if (debug) {
                    logger.info(String.format("§6[MobManager] §7Mob hit player for §c%d §7damage", damage));
                }
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Mob hit player error: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    private void handleMobSound(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (!soundTimes.containsKey(mobId) || currentTime - soundTimes.get(mobId) > 500) {
            soundTimes.put(mobId, currentTime);
            playMobHitSound(mob, mob.getHealth() <= 0);
        }
    }

    private int calculateMobDamage(LivingEntity mob, Player player) {
        int damage = 1;

        // Get weapon damage
        ItemStack weapon = mob.getEquipment().getItemInMainHand();
        if (weapon != null && weapon.getType() != Material.AIR) {
            List<Integer> damageRange = MobUtils.getDamageRange(weapon);
            damage = ThreadLocalRandom.current().nextInt(damageRange.get(1) - damageRange.get(0) + 1) +
                    damageRange.get(0) + 1;
        }

        // Handle critical states
        damage = handleCriticalDamage(mob, player, damage);

        // Apply equipment modifiers
        damage = applyEquipmentModifiers(mob, damage);

        // Apply entity-specific modifiers
        damage = applyEntityModifiers(mob, damage);

        return Math.max(1, damage);
    }

    private int handleCriticalDamage(LivingEntity mob, Player player, int baseDamage) {
        boolean isInCritical = critMobs.containsKey(mob);
        Integer criticalState = critMobs.get(mob);

        if (isInCritical) {
            if (!MobUtils.isElite(mob) && criticalState != null && criticalState < 0) {
                // Normal mob ready to attack
                return executeNormalMobCriticalAttack(mob, player, baseDamage);
            } else if (MobUtils.isElite(mob) && criticalState != null && criticalState == 0) {
                // Elite mob finishing attack
                return handleEliteCriticalFinish(mob, player, baseDamage);
            }
        }

        return baseDamage;
    }

    private int handleEliteCriticalFinish(LivingEntity mob, Player player, int baseDamage) {
        int damage = baseDamage * 4;

        critMobs.remove(mob);
        soundTimes.remove(mob.getUniqueId());
        if (mob.hasMetadata("criticalState")) {
            mob.removeMetadata("criticalState", plugin);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.3f);

        return damage;
    }

    private int applyEquipmentModifiers(LivingEntity mob, int damage) {
        ItemStack weapon = mob.getEquipment().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return damage;

        String weaponType = weapon.getType().name();
        boolean hasEnchants = weapon.hasItemMeta() && weapon.getItemMeta().hasEnchants();

        // Equipment damage multipliers
        if (weaponType.contains("WOOD_")) {
            return hasEnchants ? (int) (damage * 2.5) : (int) (damage * 0.8);
        } else if (weaponType.contains("STONE_")) {
            return hasEnchants ? (int) (damage * 2.5) : (int) (damage * 0.9);
        } else if (weaponType.contains("IRON_")) {
            return hasEnchants ? damage * 3 : (int) (damage * 1.2);
        } else if (weaponType.contains("DIAMOND_") &&
                !mob.getEquipment().getArmorContents()[0].getType().name().contains("LEATHER_")) {
            return hasEnchants ? damage * 5 : (int) (damage * 1.4);
        } else if (weaponType.contains("GOLD_")) {
            return hasEnchants ? damage * 6 : damage * 2;
        } else if (weaponType.contains("DIAMOND_")) {
            return hasEnchants ? damage * 8 : damage * 4;
        }

        return damage;
    }

    private int applyEntityModifiers(LivingEntity mob, int damage) {
        // Reduce magma cube damage
        if (mob instanceof MagmaCube) {
            damage = (int) (damage * 0.5);
        }

        // Apply lightning multiplier
        if (mob.hasMetadata("LightningMultiplier")) {
            damage *= mob.getMetadata("LightningMultiplier").get(0).asInt();
        }

        return damage;
    }

    private void playMobHitSound(LivingEntity mob, boolean isDying) {
        Location loc = mob.getLocation();

        if (mob instanceof Skeleton) {
            if (isDying) mob.getWorld().playSound(loc, Sound.ENTITY_SKELETON_DEATH, 1.0f, 1.0f);
            mob.getWorld().playSound(loc, Sound.ENTITY_SKELETON_HURT, 1.0f, 1.0f);
        } else if (mob instanceof Zombie) {
            if (isDying) mob.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_DEATH, 1.0f, 1.0f);
            mob.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_HURT, 1.0f, 1.0f);
        } else if (mob instanceof Spider || mob instanceof CaveSpider) {
            if (isDying) mob.getWorld().playSound(loc, Sound.ENTITY_SPIDER_DEATH, 1.0f, 1.0f);
        } else if (mob instanceof Silverfish) {
            if (isDying) mob.getWorld().playSound(loc, Sound.ENTITY_SILVERFISH_DEATH, 1.0f, 1.0f);
        } else if (mob instanceof PigZombie) {
            if (isDying) mob.getWorld().playSound(loc, Sound.ENTITY_PIGLIN_DEATH, 1.0f, 1.0f);
            mob.getWorld().playSound(loc, Sound.ENTITY_PIGLIN_HURT, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            UUID entityId = entity.getUniqueId();

            if (entity instanceof Player || processedEntities.contains(entityId)) {
                return;
            }

            processedEntities.add(entityId);

            if (entity.hasMetadata("type")) {
                handleMobDeath(entity);
            }

            // Cleanup tracking data
            mobSpawnerLocations.remove(entityId);
            soundTimes.remove(entityId);

            nameLock.writeLock().lock();
            try {
                nameTrackingData.remove(entityId);
            } finally {
                nameLock.writeLock().unlock();
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Entity death error: " + e.getMessage());
        }
    }

    private void handleMobDeath(LivingEntity entity) {
        String mobType = entity.getMetadata("type").get(0).asString();
        int tier = getMobTier(entity);
        boolean elite = isElite(entity);

        long respawnTime = recordMobDeath(mobType, tier, elite);
        long delay = (respawnTime - System.currentTimeMillis()) / 1000;

        logger.info(String.format("§6[MobManager] §7Mob %s T%d%s died - respawn in %ds",
                mobType, tier, elite ? "+" : "", delay));

        Location spawnerLoc = findSpawnerForMob(entity);
        if (spawnerLoc != null) {
            spawner.registerMobDeath(spawnerLoc, entity.getUniqueId());
            logger.info(String.format("§6[MobManager] §7Registered death with spawner at %s",
                    formatLocation(spawnerLoc)));
        } else {
            logger.warning(String.format("§c[MobManager] Could not find spawner for %s", entity.getType()));
        }
    }

    private Location findSpawnerForMob(LivingEntity entity) {
        if (entity == null) return null;

        UUID entityId = entity.getUniqueId();

        if (mobSpawnerLocations.containsKey(entityId)) {
            return mobSpawnerLocations.get(entityId);
        }

        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();
            Location spawnerLoc = findSpawnerById(spawnerId);
            if (spawnerLoc != null) return spawnerLoc;
        }

        return spawner.findNearestSpawner(entity.getLocation(), mobRespawnDistanceCheck);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) return "Unknown";

        return String.format("%s [%d, %d, %d]",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    // ================ CONFIGURATION GETTERS ================

    public boolean isDebugMode() { return debug; }
    public void setDebugMode(boolean debug) {
        this.debug = debug;
        spawner.setDebugMode(debug);
        logger.info(String.format("§6[MobManager] §7Debug mode %s", debug ? "enabled" : "disabled"));
    }

    public int getMaxMobsPerSpawner() { return maxMobsPerSpawner; }
    public double getPlayerDetectionRange() { return playerDetectionRange; }
    public double getMobRespawnDistanceCheck() { return mobRespawnDistanceCheck; }

    public void setSpawnersEnabled(boolean enabled) {
        spawnersEnabled = enabled;
        spawner.setSpawnersEnabled(enabled);

        if (debug) {
            logger.info(String.format("§6[MobManager] §7Spawners %s", enabled ? "enabled" : "disabled"));
        }
    }

    public boolean areSpawnersEnabled() { return spawnersEnabled; }

    // ================ METADATA UTILITIES ================

    public boolean hasMetadata(LivingEntity entity, String key) {
        return entity != null && entity.hasMetadata(key);
    }

    public String getMetadataString(LivingEntity entity, String key, String defaultValue) {
        return MobUtils.getMetadataString(entity, key, defaultValue);
    }

    public boolean getMetadataBoolean(LivingEntity entity, String key, boolean defaultValue) {
        return MobUtils.getMetadataBoolean(entity, key, defaultValue);
    }

    public int getMetadataInt(LivingEntity entity, String key, int defaultValue) {
        return MobUtils.getMetadataInt(entity, key, defaultValue);
    }

    public void setMetadata(LivingEntity entity, String key, Object value) {
        if (entity == null) return;

        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    // ================ SHUTDOWN ================

    public void shutdown() {
        try {
            spawner.saveSpawners();
            taskManager.cancelAllTasks();
            spawner.shutdown();

            // Clean up all active mobs
            mobLock.readLock().lock();
            try {
                activeMobs.values().forEach(CustomMob::remove);
            } finally {
                mobLock.readLock().unlock();
            }

            // Clear all collections
            clearAllCollections();

            activeWorldBoss = null;

            logger.info("§a[MobManager] §7Shutdown completed successfully with enhanced 1.20.2 cleanup");
        } catch (Exception e) {
            logger.severe("§c[MobManager] Shutdown error: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    private void clearAllCollections() {
        mobLock.writeLock().lock();
        try {
            activeMobs.clear();
        } finally {
            mobLock.writeLock().unlock();
        }

        critMobs.clear();
        soundTimes.clear();

        nameLock.writeLock().lock();
        try {
            nameTrackingData.clear();
        } finally {
            nameLock.writeLock().unlock();
        }

        damageContributions.clear();
        mobTargets.clear();
        processedEntities.clear();
        lastSafespotCheck.clear();
        respawnTimes.clear();
        mobTypeLastDeath.clear();
        mobSpawnerLocations.clear();
        entityToSpawner.clear();
    }
}