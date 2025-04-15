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
import java.util.logging.Logger;

/**
 * Central manager for all mob-related operations
 */

public class MobManager implements Listener {
    // Standardized minimum respawn delay for all mob types (3 minutes)
    private static final long MIN_RESPAWN_DELAY = 26000;
    // Maximum respawn delay cap to prevent excessive waits (15 minutes)
    private static final long MAX_RESPAWN_DELAY = 900000;
    // Maximum distance a mob can wander from its spawner
    private static final double MAX_WANDERING_DISTANCE = 25.0;
    // How often to check mob positions (in ticks, 100 ticks = 5 seconds)
    private static final long POSITION_CHECK_INTERVAL = 100L;

    private static MobManager instance;

    // Maps to track mobs, crits, sounds and damage times
    private final Map<UUID, CustomMob> activeMobs = new ConcurrentHashMap<>();
    // Critical state tracking: negative values mean "ready to attack", positive values are countdown
    private final Map<LivingEntity, Integer> critMobs = new HashMap<>();
    private final Map<UUID, Long> soundTimes = new HashMap<>();
    private final Map<UUID, Long> nameTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageContributions = new ConcurrentHashMap<>();
    private final Map<Entity, Player> mobTargets = new ConcurrentHashMap<>();
    private final Set<UUID> processedEntities = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long> lastSafespotCheck = new ConcurrentHashMap<>();
    private final Map<UUID, String> entityToSpawner = new ConcurrentHashMap<>();
    // Maps for respawn tracking
    private final Map<String, Long> respawnTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> mobTypeLastDeath = new ConcurrentHashMap<>();
    // Mob position tracking for teleporting wayward mobs
    private final Map<UUID, Location> mobSpawnerLocations = new ConcurrentHashMap<>();

    // Track original names separately for reliable restoration
    private final Map<UUID, String> originalNames = new ConcurrentHashMap<>();

    // Reference to spawner manager
    private final MobSpawner spawner;
    private final Logger logger;
    private final YakRealms plugin;

    // World boss reference
    private WorldBoss activeWorldBoss;

    // Feature toggles and configuration
    private boolean spawnersEnabled = true;
    private boolean debug = false;
    private int maxMobsPerSpawner = 10;
    private double playerDetectionRange = 40.0;
    private double mobRespawnDistanceCheck = 25.0;

    // Task references
    private BukkitTask criticalStateTask;
    private BukkitTask nameVisibilityTask;
    private BukkitTask activeMobsTask;
    private BukkitTask cleanupTask;
    private BukkitTask mobPositionTask;

    /**
     * Constructor
     */
    private MobManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.spawner = MobSpawner.getInstance();
        this.debug = plugin.isDebugMode();

        // Load configuration values
        loadConfiguration();
    }

    /**
     * Get the singleton instance
     *
     * @return MobManager instance
     */
    public static MobManager getInstance() {
        if (instance == null) {
            instance = new MobManager();
        }
        return instance;
    }

    /**
     * Load configuration values from plugin config
     */
    private void loadConfiguration() {
        this.maxMobsPerSpawner = plugin.getConfig().getInt("mechanics.mobs.max-mobs-per-spawner", 10);
        this.playerDetectionRange = plugin.getConfig().getDouble("mechanics.mobs.player-detection-range", 40.0);
        this.mobRespawnDistanceCheck = plugin.getConfig().getDouble("mechanics.mobs.mob-respawn-distance-check", 25.0);
    }

    /**
     * Initialize the mob manager
     */
    public void initialize() {
        try {
            // Initialize spawner
            spawner.initialize();

            // Register events
            plugin.getServer().getPluginManager().registerEvents(this, plugin);

            // Start tasks
            startTasks();

            // Start the mob position monitoring task
            startMobPositionMonitoringTask();

            logger.info("[MobManager] has been initialized with " + spawner.getAllSpawners().size() + " active spawners");
            if (debug) {
                logger.info("[MobManager] Debug mode is enabled");
            }
        } catch (Exception e) {
            logger.severe("[MobManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Start recurring tasks
     */
    private void startTasks() {
        logger.info("[MobManager] Starting tasks...");

        // Task to process critical state - more frequent to match original
        criticalStateTask = new BukkitRunnable() {
            @Override
            public void run() {
                processCriticalState();
            }
        }.runTaskTimer(plugin, 20L, 8L);  // Execute every 8 ticks (0.4 seconds)

        // Task to update name visibility
        nameVisibilityTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateNameVisibility();
            }
        }.runTaskTimer(plugin, 10L, 10L);  // Execute every 10 ticks (0.5 seconds)

        // Task to update active mobs
        activeMobsTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateActiveMobs();
            }
        }.runTaskTimer(plugin, 60L, 60L);  // Execute every 60 ticks (3 seconds)

        // Task to clean up damage tracking
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupDamageTracking();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);  // Execute every 1200 ticks (1 minute)

        logger.info("[MobManager] All tasks started successfully");
    }

    /**
     * Start the mob position monitoring task
     */
    private void startMobPositionMonitoringTask() {
        logger.info("[MobManager] Starting mob position monitoring task...");

        mobPositionTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkMobPositions();
            }
        }.runTaskTimer(plugin, 60L, POSITION_CHECK_INTERVAL);

        if (debug) {
            logger.info("[MobManager] Mob position monitoring task started");
        }
    }

    /**
     * Check if debug mode is enabled
     *
     * @return Whether debug mode is enabled
     */
    public boolean isDebugMode() {
        return debug;
    }

    /**
     * Set whether debug mode is enabled
     *
     * @param debug Whether to enable debug mode
     */
    public void setDebugMode(boolean debug) {
        this.debug = debug;
        spawner.setDebugMode(debug);
        logger.info("[MobManager] Debug mode " + (debug ? "enabled" : "disabled"));
    }

    /**
     * Get the maximum number of mobs per spawner
     *
     * @return Maximum mob count
     */
    public int getMaxMobsPerSpawner() {
        return maxMobsPerSpawner;
    }

    /**
     * Get the player detection range for spawners
     *
     * @return Detection range
     */
    public double getPlayerDetectionRange() {
        return playerDetectionRange;
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
     * Enable or disable spawners
     *
     * @param enabled Whether spawners should be enabled
     */
    public void setSpawnersEnabled(boolean enabled) {
        spawnersEnabled = enabled;
        spawner.setSpawnersEnabled(enabled);

        if (debug) {
            logger.info("[MobManager] Spawners " + (enabled ? "enabled" : "disabled"));
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
     * Process critical states for mobs
     */
    private void processCriticalState() {
        try {
            // First process registered mobs through their CustomMob objects
            processCustomMobCriticals();

            // Then process legacy mobs in critical state
            processLegacyMobCriticals();
        } catch (Exception e) {
            logger.severe("[MobManager] Unhandled exception in processCriticalState: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process critical states for CustomMob instances
     */
    private void processCustomMobCriticals() {
        for (CustomMob mob : new ArrayList<>(activeMobs.values())) {
            try {
                if (mob.isInCriticalState()) {
                    LivingEntity entity = mob.getEntity();

                    // Only log and process if not already in ready state
                    boolean isReadyState = mob.getCriticalStateDuration() < 0;

                    if (debug && !isReadyState) {
                        logger.info("[MobManager] Processing CustomMob critical: " + mob.getType() +
                                ", ticks left: " + mob.getCriticalStateDuration());
                    }

                    boolean criticalEnded = mob.processCriticalStateTick();

                    if (criticalEnded) {
                        if (mob instanceof EliteMob) {
                            // For EliteMob, execute attack automatically
                            if (debug) {
                                logger.info("[MobManager] Elite critical ended, executing attack");
                            }
                            ((EliteMob) mob).executeCriticalAttack();
                        } else if (!isReadyState) {
                            // For regular mobs, only log once when transitioning to "ready to attack" state
                            if (debug) {
                                logger.info("[MobManager] Regular mob critical countdown ended, now ready to attack");
                            }

                            // Store that this mob is in ready-to-attack state
                            if (entity != null) {
                                // Keep metadata to ensure health bar stays visible
                                entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));

                                // Add subtle visual effect to show ready state
                                entity.getWorld().spawnParticle(
                                        Particle.VILLAGER_ANGRY,
                                        entity.getLocation().clone().add(0.0, 1.0, 0.0),
                                        1, 0.1, 0.1, 0.1, 0.0);
                            }
                        }
                    }

                    // Force particles for visibility during charging phase
                    if (entity != null && mob.getCriticalStateDuration() > 0) {
                        Particle particleType = mob.isElite() ? Particle.SPELL_WITCH : Particle.CRIT;

                        entity.getWorld().spawnParticle(
                                particleType,
                                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                                5, 0.3, 0.3, 0.3, 0.05);
                    }
                }
            } catch (Exception e) {
                logger.warning("[MobManager] Error processing CustomMob critical state: " + e.getMessage());
            }
        }
    }

    /**
     * Process critical states for legacy mobs
     */
    private void processLegacyMobCriticals() {
        Iterator<Map.Entry<LivingEntity, Integer>> it = critMobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Integer> entry = it.next();
            LivingEntity entity = entry.getKey();
            int step = entry.getValue();

            // Skip invalid entities
            if (entity == null || !entity.isValid() || entity.isDead()) {
                it.remove();
                continue;
            }

            // Check if already in ready-to-attack state (negative value)
            if (step < 0) {
                // Already in ready-to-attack state, just add subtle visual effect
                if (entity.getWorld().getTime() % 20 == 0) { // Only every second
                    entity.getWorld().spawnParticle(
                            Particle.VILLAGER_ANGRY,
                            entity.getLocation().clone().add(0.0, 1.0, 0.0),
                            1, 0.1, 0.1, 0.1, 0.0);
                }
                continue;
            }

            // Process critical state based on elite status
            if (MobUtils.isElite(entity) && !MobUtils.isGolemBoss(entity)) {
                // Process elite mob critical state
                processEliteCritical(entity, step);
            } else {
                // Process regular mob critical countdown
                processRegularMobCritical(entity, step);
            }
        }
    }

    /**
     * Process an elite mob's critical state
     *
     * @param entity The entity in critical state
     * @param step   Current step counter
     */
    private void processEliteCritical(LivingEntity entity, int step) {
        try {
            // Handle frozen boss differently
            if (MobUtils.isFrozenBoss(entity)) {
                processFrozenBossCritical(entity, step);
            }

            // Update counter
            critMobs.put(entity, step - 1);

            // Play sound and particles
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);
            entity.getWorld().spawnParticle(
                    Particle.EXPLOSION_LARGE,
                    entity.getLocation().clone().add(0, 1, 0),
                    5, 0.3, 0.3, 0.3, 0.3f);

            // When critical state ends
            if (step - 1 <= 0) {
                if (debug) {
                    logger.info("[MobManager] Elite mob critical attack executing");
                }
                executeEliteCriticalAttack(entity);
            }
        } catch (Exception e) {
            logger.warning("[MobManager] Error in processEliteCritical: " + e.getMessage());
        }
    }

    /**
     * Process frozen boss critical state
     *
     * @param entity The frozen boss entity
     * @param step   Current step counter
     */
    private void processFrozenBossCritical(LivingEntity entity, int step) {
        try {
            // Remove slow effect if present
            if (entity.hasPotionEffect(PotionEffectType.SLOW)) {
                entity.removePotionEffect(PotionEffectType.SLOW);

                // Apply appropriate speed effect based on health
                boolean lowHealth = entity.getHealth() < (YakRealms.isT6Enabled() ? 100000 : 50000);
                entity.addPotionEffect(new PotionEffect(
                                PotionEffectType.SPEED,
                                25,
                                lowHealth ? 1 : 0),
                        true);
            }

            // Apply slow effect to nearby players
            if (step > 0) {
                for (Entity nearby : entity.getNearbyEntities(8.0, 8.0, 8.0)) {
                    if (nearby instanceof Player) {
                        ((Player) nearby).addPotionEffect(
                                new PotionEffect(PotionEffectType.SLOW, 25, 1),
                                true);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[MobManager] Error in processFrozenBossCritical: " + e.getMessage());
        }
    }

    /**
     * Process a regular mob's critical state
     *
     * @param entity The entity in critical state
     * @param step   Current step counter
     */
    private void processRegularMobCritical(LivingEntity entity, int step) {
        if (step > 0) {
            // Decrement counter
            critMobs.put(entity, --step);

            // Play sound effect - matches old code
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);

            // Add particles for visibility
            entity.getWorld().spawnParticle(
                    Particle.CRIT,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    10, 0.3, 0.3, 0.3, 0.1);
        }

        // When countdown reaches 0, transition to ready-to-attack state
        if (step == 0) {
            if (debug) {
                logger.info("[MobManager] Regular mob countdown complete, now ready to attack");
            }

            // Change to ready-to-attack state (-1)
            critMobs.put(entity, -1);

            // Play a ready sound
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

            // Visual effect to indicate ready
            entity.getWorld().spawnParticle(
                    Particle.SPELL_WITCH,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    15, 0.3, 0.3, 0.3, 0.1);

            // Keep health bar showing by updating the time
            nameTimes.put(entity.getUniqueId(), System.currentTimeMillis());

            // Update health bar
            updateEntityHealthBar(entity);
        }
    }

    /**
     * Execute the elite critical attack
     *
     * @param entity The entity executing the critical attack
     */
    private void executeEliteCriticalAttack(LivingEntity entity) {
        try {
            // Remove from tracking
            critMobs.remove(entity);

            // Special handling for frozen boss at low health
            if (MobUtils.isFrozenBoss(entity) &&
                    entity.getHealth() < (YakRealms.isT6Enabled() ? 100000 : 50000)) {
                critMobs.put(entity, 10);
                if (debug) {
                    logger.info("[MobManager] Frozen boss at low health - restarting critical state");
                }
            }

            // Get weapon damage
            ItemStack weapon = entity.getEquipment().getItemInMainHand();
            List<Integer> damageRange = MobUtils.getDamageRange(weapon);
            int min = damageRange.get(0);
            int max = damageRange.get(1);

            // Calculate critical damage - exactly as in original
            int dmg = (java.util.concurrent.ThreadLocalRandom.current().nextInt(max - min + 1) + min) * 3;

            if (debug) {
                logger.info("[MobManager] Elite critical damage calculated: " + dmg);
            }

            // Apply damage to nearby players
            int playersHit = 0;
            for (Entity nearby : entity.getNearbyEntities(7.0, 7.0, 7.0)) {
                if (nearby instanceof Player player) {
                    playersHit++;

                    // Store original crit state before applying damage
                    boolean hadCrit = critMobs.containsKey(entity);
                    critMobs.remove(entity);

                    // Apply damage
                    player.damage(dmg, entity);

                    // Restore previous state if needed
                    if (hadCrit) {
                        critMobs.put(entity, 0);
                    }

                    // Apply knockback
                    Vector v = player.getLocation().clone().toVector()
                            .subtract(entity.getLocation().toVector());

                    if (v.length() > 0) {
                        v.normalize();

                        // Reverse for frozen boss
                        if (MobUtils.isFrozenBoss(entity)) {
                            player.setVelocity(v.multiply(-3));
                        } else {
                            player.setVelocity(v.multiply(3));
                        }
                    }
                }
            }

            // Play sound and effects
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
            entity.getWorld().spawnParticle(
                    Particle.EXPLOSION_HUGE,
                    entity.getLocation().clone().add(0, 1, 0),
                    10, 0, 0, 0, 1.0f);

            // Add flame particles for additional effect
            entity.getWorld().spawnParticle(
                    Particle.FLAME,
                    entity.getLocation().clone().add(0.0, 0.5, 0.0),
                    40, 1.0, 0.2, 1.0, 0.1);

            // Reset potion effects
            resetElitePotionEffects(entity);

            // Update entity name - restore original name
            if (entity.hasMetadata("criticalState")) {
                entity.removeMetadata("criticalState", plugin);
            }

            // Restore original name
            restoreOriginalName(entity);

            if (debug) {
                logger.info("[MobManager] Elite critical hit " + playersHit + " players for " + dmg + " damage");
            }
        } catch (Exception e) {
            logger.warning("[MobManager] Error in executeEliteCriticalAttack: " + e.getMessage());
        }
    }

    /**
     * Reset potion effects after critical attack
     *
     * @param entity The entity to reset effects for
     */
    private void resetElitePotionEffects(LivingEntity entity) {
        try {
            if (entity.hasPotionEffect(PotionEffectType.SLOW)) {
                entity.removePotionEffect(PotionEffectType.SLOW);

                // For staff users, reapply slow
                if (entity.getEquipment().getItemInMainHand() != null &&
                        entity.getEquipment().getItemInMainHand().getType().name().contains("_HOE")) {
                    entity.addPotionEffect(new PotionEffect(
                                    PotionEffectType.SLOW, Integer.MAX_VALUE, 1),
                            true);
                }
            }

            // Remove jump effect
            if (entity.hasPotionEffect(PotionEffectType.JUMP)) {
                entity.removePotionEffect(PotionEffectType.JUMP);
            }

            // Remove glowing effect
            if (entity.hasPotionEffect(PotionEffectType.GLOWING)) {
                entity.removePotionEffect(PotionEffectType.GLOWING);
            }

            // Apply elite movement effect
            entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED, Integer.MAX_VALUE, 1),
                    true);
        } catch (Exception e) {
            logger.warning("[MobManager] Error in resetElitePotionEffects: " + e.getMessage());
        }
    }

    /**
     * Execute a normal mob critical attack
     *
     * @param entity     The mob entity
     * @param player     The player being attacked
     * @param baseDamage The base damage value
     * @return The multiplied critical damage
     */
    private int executeNormalMobCriticalAttack(LivingEntity entity, Player player, int baseDamage) {
        try {
            // Remove from critical state tracking
            critMobs.remove(entity);

            if (entity.hasMetadata("criticalState")) {
                entity.removeMetadata("criticalState", plugin);
            }

            // Apply triple damage for normal mob critical
            int critDamage = baseDamage * 3;

            // Visual and sound effects
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);

            entity.getWorld().spawnParticle(
                    Particle.EXPLOSION_LARGE,
                    entity.getLocation().clone().add(0, 1, 0),
                    20, 0.5, 0.5, 0.5, 0.1);

            // Knockback effect (mild)
            Vector v = player.getLocation().clone().toVector()
                    .subtract(entity.getLocation().toVector());
            if (v.length() > 0) {
                v.normalize();
                player.setVelocity(v.multiply(1.5));
            }

            if (debug) {
                logger.info("[MobManager] Normal mob critical hit executed! Damage: " + critDamage);
            }

            // Restore original name
            restoreOriginalName(entity);

            return critDamage;
        } catch (Exception e) {
            logger.warning("[MobManager] Error executing normal mob critical attack: " + e.getMessage());
            return baseDamage; // Fallback to regular damage
        }
    }

    /**
     * Update name visibility for mobs
     * This restores original names after 5 seconds
     */
    private void updateNameVisibility() {
        try {
            long now = System.currentTimeMillis();

            // Process registered mobs first - using the CustomMob's own visibility logic
            for (CustomMob mob : new ArrayList<>(activeMobs.values())) {
                if (mob.isValid()) {
                    mob.updateNameVisibility();
                }
            }

            // Process legacy mobs - ensure we only restore names when appropriate
            for (Map.Entry<UUID, Long> entry : new HashMap<>(nameTimes).entrySet()) {
                UUID entityId = entry.getKey();
                Long damageTime = entry.getValue();

                // Skip if damaged recently (keep showing health bar)
                if (now - damageTime < 5000) {
                    continue;
                }

                // Find the entity
                Entity entity = Bukkit.getEntity(entityId);
                if (entity instanceof LivingEntity livingEntity && !(entity instanceof Player)) {
                    // Only restore name if not in critical state
                    boolean inCriticalState = critMobs.containsKey(livingEntity) ||
                            livingEntity.hasMetadata("criticalState");

                    if (!inCriticalState) {
                        // Remove from name times tracking
                        nameTimes.remove(entityId);

                        // Restore original name
                        restoreOriginalName(livingEntity);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[MobManager] Error in updateNameVisibility: " + e.getMessage());
        }
    }

    /**
     * Restore the original name for an entity
     *
     * @param entity The entity to restore name for
     */
    private void restoreOriginalName(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.getType() == EntityType.ARMOR_STAND) {
            return;
        }

        // Check if we have the original name stored
        String originalName = null;

        // Check in our map first
        if (originalNames.containsKey(entity.getUniqueId())) {
            originalName = originalNames.get(entity.getUniqueId());
        }
        // Then check metadata
        else if (entity.hasMetadata("name")) {
            originalName = entity.getMetadata("name").get(0).asString();
            // Store it for future reference
            originalNames.put(entity.getUniqueId(), originalName);
        }
        // Check for lightning mob name
        else if (entity.hasMetadata("LightningMob")) {
            originalName = entity.getMetadata("LightningMob").get(0).asString();
            // Store it for future reference
            originalNames.put(entity.getUniqueId(), originalName);
        }
        // Fall back to reconstructing a name based on type and tier
        else {
            originalName = getDefaultNameForEntity(entity);
            if (originalName != null && !originalName.isEmpty()) {
                // Store it for future reference
                originalNames.put(entity.getUniqueId(), originalName);
            }
        }

        // Apply the name if we have one
        if (originalName != null && !originalName.isEmpty()) {
            entity.setCustomName(originalName);
            entity.setCustomNameVisible(true);

            if (debug) {
                logger.info("[MobManager] Restored original name for " + entity.getType() + ": " + originalName);
            }
        }
    }

    /**
     * Get default name for an entity if metadata is missing
     *
     * @param entity The entity to get name for
     * @return Default name for entity
     */
    public String getDefaultNameForEntity(LivingEntity entity) {
        if (entity == null) return "";

        // Get mob type and tier
        String typeId = "unknown";
        if (entity.hasMetadata("type")) {
            typeId = entity.getMetadata("type").get(0).asString();
        } else if (entity.hasMetadata("customName")) {
            typeId = entity.getMetadata("customName").get(0).asString();
        }

        int tier = getMobTier(entity);
        boolean isElite = isElite(entity);

        // Try to get the MobType
        MobType mobType = MobType.getById(typeId);
        if (mobType != null) {
            // Use tier-specific name with appropriate color
            String tierName = mobType.getTierSpecificName(tier);

            // Apply color and formatting
            ChatColor color = getTierColor(tier);
            if (isElite) {
                return color.toString() + ChatColor.BOLD + tierName;
            } else {
                return color + tierName;
            }
        }

        // If all else fails, return a simple formatted name
        ChatColor color = getTierColor(tier);
        String typeName = capitalizeEntityType(entity.getType());

        if (isElite) {
            return color.toString() + ChatColor.BOLD + typeName;
        } else {
            return color + typeName;
        }
    }

    /**
     * Get the appropriate color for a tier
     *
     * @param tier The tier (1-6)
     * @return ChatColor for the tier
     */
    private ChatColor getTierColor(int tier) {
        switch (tier) {
            case 1:
                return ChatColor.WHITE;
            case 2:
                return ChatColor.GREEN;
            case 3:
                return ChatColor.AQUA;
            case 4:
                return ChatColor.LIGHT_PURPLE;
            case 5:
                return ChatColor.YELLOW;
            case 6:
                return ChatColor.BLUE;
            default:
                return ChatColor.WHITE;
        }
    }

    /**
     * Capitalize an entity type name for display
     *
     * @param type The entity type
     * @return Capitalized name
     */
    private String capitalizeEntityType(EntityType type) {
        if (type == null) return "Unknown";

        String typeName = type.name().toLowerCase().replace("_", " ");
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : typeName.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Update active mobs
     */
    private void updateActiveMobs() {
        // Update world boss if active
        if (activeWorldBoss != null && activeWorldBoss.isValid()) {
            activeWorldBoss.update();

            // Check if world boss needs to transition phases
            activeWorldBoss.processPhaseTransitions();
        }

        // Remove invalid mobs
        activeMobs.entrySet().removeIf(entry -> {
            CustomMob mob = entry.getValue();
            return mob == null || !mob.isValid();
        });
    }

    /**
     * Check mob positions and handle mobs that have wandered too far or entered safezones
     */
    private void checkMobPositions() {
        try {
            int teleportedMobs = 0;

            // Process registered mobs through CustomMob objects
            for (CustomMob mob : new ArrayList<>(activeMobs.values())) {
                if (mob.isValid()) {
                    LivingEntity entity = mob.getEntity();
                    if (entity == null || !entity.isValid()) continue;

                    // Get spawner location from our tracking map
                    Location spawnerLoc = getSpawnerLocation(entity);
                    if (spawnerLoc == null) continue;

                    boolean shouldTeleport = false;
                    String reason = "";

                    // Check distance from spawner
                    if (entity.getLocation().getWorld().equals(spawnerLoc.getWorld())) {
                        double distanceSquared = entity.getLocation().distanceSquared(spawnerLoc);
                        if (distanceSquared > (MAX_WANDERING_DISTANCE * MAX_WANDERING_DISTANCE)) {
                            shouldTeleport = true;
                            reason = "wandered too far";
                        }
                    } else {
                        // If in a different world, always teleport back
                        shouldTeleport = true;
                        reason = "changed worlds";
                    }

                    // Check if in safezone
                    if (isInSafezone(entity)) {
                        shouldTeleport = true;
                        reason = "entered safezone";
                    }

                    // Teleport if needed
                    if (shouldTeleport) {
                        teleportMobToSpawner(entity, spawnerLoc, reason);
                        teleportedMobs++;
                    }
                }
            }

            if (debug && teleportedMobs > 0) {
                logger.info("[MobManager] Teleported " + teleportedMobs + " mobs back to their spawners");
            }
        } catch (Exception e) {
            logger.warning("[MobManager] Error in checkMobPositions: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the spawner location for an entity
     * @param entity The entity to check
     * @return The spawner location or null if not found
     */
    private Location getSpawnerLocation(LivingEntity entity) {
        if (entity == null) return null;

        UUID entityId = entity.getUniqueId();

        // First check our direct mapping
        if (mobSpawnerLocations.containsKey(entityId)) {
            return mobSpawnerLocations.get(entityId);
        }

        // Try to get spawner ID from metadata
        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();

            // Look for spawner in our spawner system
            for (Map.Entry<Location, String> entry : spawner.getAllSpawners().entrySet()) {
                String locationId = generateSpawnerId(entry.getKey());
                if (spawnerId.equals(locationId)) {
                    // Cache for future
                    mobSpawnerLocations.put(entityId, entry.getKey());
                    return entry.getKey();
                }
            }
        }

        // Last resort: try to find nearest spawner
        Location nearestSpawner = findNearestSpawner(entity.getLocation(), mobRespawnDistanceCheck);
        if (nearestSpawner != null) {
            // Cache for future use
            mobSpawnerLocations.put(entityId, nearestSpawner);
            return nearestSpawner;
        }

        return null;
    }

    /**
     * Check if an entity is in a safezone
     * @param entity The entity to check
     * @return true if in a safezone
     */
    private boolean isInSafezone(LivingEntity entity) {
        return AlignmentMechanics.isSafeZone(entity.getLocation());
    }

    /**
     * Teleport a mob back to its spawner and reset targeting
     * @param entity The entity to teleport
     * @param spawnerLoc The spawner location
     * @param reason The reason for teleporting
     */
    private void teleportMobToSpawner(LivingEntity entity, Location spawnerLoc, String reason) {
        if (entity == null || spawnerLoc == null) return;

        try {
            // Create a safe teleport location with a random offset
            Location safeLoc = spawnerLoc.clone().add(
                    (Math.random() * 4 - 2), // Random X offset within 2 blocks
                    1.0,                     // Slight Y offset for safety
                    (Math.random() * 4 - 2)  // Random Z offset within 2 blocks
            );

            // Make sure we teleport to the center of the block
            safeLoc.setX(safeLoc.getBlockX() + 0.5);
            safeLoc.setZ(safeLoc.getBlockZ() + 0.5);

            // Add visual effect before teleport
            entity.getWorld().spawnParticle(
                    Particle.PORTAL,
                    entity.getLocation().clone().add(0, 1, 0),
                    30, 0.5, 0.5, 0.5, 0.1
            );

            // Play sound effect
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            // Reset mob targeting before teleporting
            if (entity instanceof Mob) {
                // Reset target to null to stop chasing players
                ((Mob) entity).setTarget(null);

                // Optionally, temporarily disable AI to further ensure deaggro
                boolean isAware = ((Mob) entity).isAware();
                ((Mob) entity).setAware(false);

                // Schedule a task to re-enable AI after a short delay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (entity != null && entity.isValid()) {
                        ((Mob) entity).setAware(isAware);
                    }
                }, 10L); // 10 ticks (0.5 seconds) delay
            }

            // Teleport the entity
            entity.teleport(safeLoc);

            // Add arrival effect
            spawnerLoc.getWorld().spawnParticle(
                    Particle.PORTAL,
                    safeLoc.clone().add(0, 1, 0),
                    30, 0.5, 0.5, 0.5, 0.1
            );

            // Play arrival sound
            spawnerLoc.getWorld().playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            if (debug) {
                logger.info("[MobManager] Teleported " + entity.getType() + " back to spawner because it " + reason);
            }
        } catch (Exception e) {
            logger.warning("[MobManager] Error teleporting mob: " + e.getMessage());
        }
    }

    /**
     * Update a mob's health bar
     *
     * @param entity The entity to update health bar for
     */
    public void updateEntityHealthBar(LivingEntity entity) {
        if (entity == null || !entity.isValid()) return;

        // Before updating, store the original name if we don't have it yet
        if (!originalNames.containsKey(entity.getUniqueId()) && entity.getCustomName() != null) {
            // Only store if this isn't already a health bar
            String currentName = entity.getCustomName();
            if (!currentName.contains(ChatColor.GREEN + "|") && !currentName.contains(ChatColor.GRAY + "|")) {
                originalNames.put(entity.getUniqueId(), currentName);
            }
        }

        // Get tier
        int tier = getMobTier(entity);

        // Generate health bar
        String healthBar = MobUtils.generateHealthBar(
                entity,
                entity.getHealth(),
                entity.getMaxHealth(),
                tier,
                critMobs.containsKey(entity) || entity.hasMetadata("criticalState"));

        // Update display
        entity.setCustomName(healthBar);
        entity.setCustomNameVisible(true);

        // Record name update time - reset the timer whenever health bar is updated
        nameTimes.put(entity.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Roll for a critical hit on an entity
     * This is the core function that implements the critical hit chance logic
     *
     * @param entity The entity to roll for
     * @param damage Damage dealt to trigger this roll
     */
    public void rollForCriticalHit(LivingEntity entity, double damage) {
        try {
            // Skip if already in critical state
            if (critMobs.containsKey(entity) || entity.hasMetadata("criticalState")) {
                if (debug) {
                    logger.info("[MobManager] Critical hit skipped - entity already in critical state");
                }
                return;
            }

            // Get tier and calculate chance
            int tier = getMobTier(entity);

            // Roll for critical - exact algorithm from old code
            Random random = new Random();
            int roll = random.nextInt(200) + 1;

            // Apply tier-based crit chances
            int critChance = 0;
            if (tier == 1) critChance = 5;       // 2.5%
            else if (tier == 2) critChance = 7;  // 3.5%
            else if (tier == 3) critChance = 10; // 5%
            else if (tier == 4) critChance = 13; // 6.5%
            else if (tier >= 5) critChance = 20; // 10%

            // Check for golem boss in berserker state (immune to crits)
            if (MobUtils.isGolemBoss(entity) && MobUtils.getMetadataInt(entity, "stage", 0) == 3) {
                critChance = 0;
            }

            // Store original name before applying critical
            if (entity.getCustomName() != null) {
                String currentName = entity.getCustomName();
                // Only store if not already a health bar
                if (!currentName.contains(ChatColor.GREEN + "|") && !currentName.contains(ChatColor.GRAY + "|")) {
                    originalNames.put(entity.getUniqueId(), currentName);
                }
            }

            if (debug) {
                logger.info("[MobManager] CRITICAL CHECK: Entity=" + entity.getType() +
                        ", Tier=" + tier + ", Roll=" + roll + "/" + critChance);
            }

            // Check for successful roll
            if (roll <= critChance) {
                if (debug) {
                    logger.info("[MobManager] *** CRITICAL HIT SUCCESS on " + entity.getType() +
                            "! (Tier " + tier + ", Elite: " + isElite(entity) + ") ***");
                }

                // Apply critical state
                CustomMob mob = getCustomMob(entity);
                if (mob != null) {
                    // Different durations for elite vs regular
                    mob.setCriticalState(isElite(entity) ? 12 : 6);

                    // Make absolutely sure the critical state is set
                    entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));

                    // Update health bar immediately
                    mob.updateHealthBar();
                } else {
                    // Apply sound effect based on type
                    if (isElite(entity) && !MobUtils.isGolemBoss(entity)) {
                        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);

                        // Apply effects for elite mobs
                        if (MobUtils.isFrozenBoss(entity)) {
                            // Apply slowness to nearby players
                            for (Entity nearby : entity.getNearbyEntities(8.0, 8.0, 8.0)) {
                                if (nearby instanceof Player) {
                                    ((Player) nearby).addPotionEffect(
                                            new PotionEffect(PotionEffectType.SLOW, 30, 1),
                                            true);
                                }
                            }
                        } else {
                            // Apply effects to the mob itself
                            entity.addPotionEffect(
                                    new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 10),
                                    true);

                            entity.addPotionEffect(
                                    new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 127),
                                    true);

                            // Add glowing for visibility
                            entity.addPotionEffect(
                                    new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0),
                                    true);
                        }
                    } else {
                        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);

                        // Add particles for visibility of regular mob crits
                        entity.getWorld().spawnParticle(
                                Particle.CRIT,
                                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                                20, 0.3, 0.3, 0.3, 0.5);
                    }

                    // Add to critical map
                    if (isElite(entity)) {
                        critMobs.put(entity, 12); // Longer for elite
                    } else {
                        critMobs.put(entity, 6);  // Regular mobs
                    }

                    // Set metadata
                    entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));

                    // Update health bar immediately
                    updateEntityHealthBar(entity);
                }

                // Update last damage time
                nameTimes.put(entity.getUniqueId(), System.currentTimeMillis());

                // Force particles to make critical state more obvious
                entity.getWorld().spawnParticle(
                        Particle.FLAME,
                        entity.getLocation().clone().add(0.0, 1.5, 0.0),
                        30, 0.3, 0.3, 0.3, 0.05);
            }

            // Check for frozen boss critical threshold
            if (MobUtils.isFrozenBoss(entity) &&
                    entity.getHealth() < (YakRealms.isT6Enabled() ? 100000 : 50000) &&
                    !critMobs.containsKey(entity) &&
                    !entity.hasMetadata("criticalState")) {

                if (debug) {
                    logger.info("[MobManager] Applying forced critical state to Frozen Boss at low health");
                }

                // Force critical state for frozen boss at low health
                CustomMob mob = getCustomMob(entity);
                if (mob != null) {
                    mob.setCriticalState(10); // Longer duration
                } else {
                    critMobs.put(entity, 10); // Longer duration
                    entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));
                    updateEntityHealthBar(entity);
                }
            }
        } catch (Exception e) {
            logger.severe("[MobManager] Error in rollForCriticalHit: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Spawn a mob at a location
     *
     * @param location Spawn location
     * @param type     Mob type
     * @param tier     Mob tier
     * @param elite    Whether the mob is elite
     * @return The spawned entity
     */
    public LivingEntity spawnMob(Location location, String type, int tier, boolean elite) {
        if (location == null) return null;

        try {
            // Validate world is loaded
            if (location.getWorld() == null) {
                logger.warning("[MobManager] Cannot spawn mob: world is null");
                return null;
            }

            // Get mob type
            MobType mobType = MobType.getById(type);
            if (mobType == null) {
                logger.warning("[MobManager] Invalid mob type: " + type);
                return null;
            }

            // Validate tier
            if (tier < mobType.getMinTier() || tier > mobType.getMaxTier()) {
                logger.warning("[MobManager] Invalid tier " + tier + " for mob type " + type);
                return null;
            }

            // Validate tier 6 restriction
            if (tier > 5 && !YakRealms.isT6Enabled()) {
                tier = 5;
            }

            // Determine if should be elite
            elite = elite || mobType.isElite();

            // Check if this mob type is allowed to respawn yet
            if (!canRespawn(type, tier, elite)) {
                if (debug) {
                    logger.info("[MobManager] BLOCKED SPAWN: " + type + " T" + tier +
                            (elite ? "+" : "") + " is still in respawn cooldown");
                }
                return null;
            }

            // Create and spawn the mob
            CustomMob mob;
            if (mobType.isWorldBoss()) {
                // Check if we already have a world boss
                if (activeWorldBoss != null && activeWorldBoss.isValid()) {
                    logger.warning("[MobManager] Cannot spawn world boss - another world boss is already active");
                    return null;
                }

                // Create new world boss
                mob = new WorldBoss(mobType, tier);
            } else if (elite) {
                // Create elite mob
                mob = new EliteMob(mobType, tier);
            } else {
                // Create normal mob
                mob = new CustomMob(mobType, tier, false);
            }

            // Spawn the mob
            if (mob.spawn(location)) {
                if (debug) {
                    logger.info("[MobManager] Successfully spawned " + type + " T" + tier + (elite ? "+" : ""));
                }

                // Register with spawner if from a spawner
                if (location.getBlock() != null && location.getBlock().getType().toString().contains("SPAWNER")) {
                    Location spawnerLoc = location.getBlock().getLocation();
                    spawner.registerMobSpawned(spawnerLoc, mob.getEntity());
                }

                return mob.getEntity();
            } else {
                logger.warning("[MobManager] Failed to spawn mob: " + type);
                return null;
            }
        } catch (Exception e) {
            logger.severe("[MobManager] Error spawning mob: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void registerMob(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) return;

        LivingEntity entity = mob.getEntity();
        activeMobs.put(entity.getUniqueId(), mob);

        // Try to store spawner location based on entity's current location
        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();
            if (spawnerId != null) {
                // Look up the actual spawner location
                for (Map.Entry<Location, String> entry : spawner.getAllSpawners().entrySet()) {
                    String locationId = generateSpawnerId(entry.getKey());
                    if (spawnerId.equals(locationId)) {
                        mobSpawnerLocations.put(entity.getUniqueId(), entry.getKey());
                        break;
                    }
                }
            }
        }

        // If we didn't find a spawner, use entity's current location as an approximation
        if (!mobSpawnerLocations.containsKey(entity.getUniqueId())) {
            Location nearestSpawner = findNearestSpawner(entity.getLocation(), 10.0);
            if (nearestSpawner != null) {
                mobSpawnerLocations.put(entity.getUniqueId(), nearestSpawner);
            } else {
                // Last resort: use current location as the "spawner" point
                mobSpawnerLocations.put(entity.getUniqueId(), entity.getLocation().clone());
            }
        }

        if (mob instanceof WorldBoss) {
            activeWorldBoss = (WorldBoss) mob;
        }

        // Store original name when registering
        if (entity.getCustomName() != null) {
            originalNames.put(entity.getUniqueId(), entity.getCustomName());
        }
    }

    /**
     * Unregister a mob from the manager
     *
     * @param mob The mob to unregister
     */
    public void unregisterMob(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) return;

        LivingEntity entity = mob.getEntity();
        activeMobs.remove(entity.getUniqueId());

        // Remove from other tracking maps as well
        originalNames.remove(entity.getUniqueId());
        nameTimes.remove(entity.getUniqueId());
        mobSpawnerLocations.remove(entity.getUniqueId());

        // If this was a world boss, clear the reference
        if (mob instanceof WorldBoss) {
            unregisterWorldBoss();
        }
    }

    /**
     * Unregister the active world boss
     */
    public void unregisterWorldBoss() {
        activeWorldBoss = null;
    }

    /**
     * Register a world boss with the manager
     *
     * @param boss The world boss
     */
    public void registerWorldBoss(WorldBoss boss) {
        if (boss == null || boss.getEntity() == null) return;

        // Unregister any existing world boss
        if (activeWorldBoss != null) {
            unregisterWorldBoss();
        }

        // Register the new world boss
        activeWorldBoss = boss;
        activeMobs.put(boss.getEntity().getUniqueId(), boss);

        // Store original name
        if (boss.getEntity().getCustomName() != null) {
            originalNames.put(boss.getEntity().getUniqueId(), boss.getEntity().getCustomName());
        }
    }

    /**
     * Get the active world boss
     *
     * @return The active world boss or null if none
     */
    public WorldBoss getActiveWorldBoss() {
        return activeWorldBoss;
    }

    /**
     * Get the CustomMob object for an entity
     *
     * @param entity The entity
     * @return The CustomMob or null if not found
     */
    public CustomMob getCustomMob(LivingEntity entity) {
        if (entity == null) return null;
        return activeMobs.get(entity.getUniqueId());
    }

    /**
     * Check if entity is a world boss
     *
     * @param entity The entity to check
     * @return true if it's a world boss
     */
    public boolean isWorldBoss(LivingEntity entity) {
        if (entity == null) return false;

        // Check through our WorldBoss reference
        if (activeWorldBoss != null && activeWorldBoss.getEntity() != null) {
            if (activeWorldBoss.getEntity().equals(entity)) {
                return true;
            }
        }

        // Check metadata
        return entity.hasMetadata("worldboss") || MobUtils.isWorldBoss(entity);
    }

    /**
     * Get the tier of a mob
     *
     * @param entity The entity
     * @return The tier level (1-6)
     */
    public int getMobTier(LivingEntity entity) {
        // Check for registered mob first
        CustomMob mob = getCustomMob(entity);
        if (mob != null) {
            return mob.getTier();
        }

        // Fall back to utility method
        return MobUtils.getMobTier(entity);
    }

    /**
     * Check if a mob is elite
     *
     * @param entity The entity
     * @return true if it's elite
     */
    public boolean isElite(LivingEntity entity) {
        // Check for registered mob first
        CustomMob mob = getCustomMob(entity);
        if (mob != null) {
            return mob.isElite();
        }

        // Fall back to utility method
        return MobUtils.isElite(entity);
    }

    /**
     * Process damage to the mob
     *
     * @param entity The entity
     * @param damage Amount of damage
     * @return The actual damage dealt
     */
    public double damageEntity(LivingEntity entity, double damage) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return 0;
        }

        // Get the custom mob if registered
        CustomMob mob = getCustomMob(entity);
        if (mob != null) {
            return mob.damage(damage);
        }

        // Handle legacy mobs
        double finalDamage = damage;
        double health = entity.getHealth() - finalDamage;

        if (health <= 0) {
            entity.setHealth(0);
        } else {
            entity.setHealth(health);

            // Update health bar
            updateEntityHealthBar(entity);

            // Check for critical state transition (for mobs like Frozen Boss)
            if (MobUtils.isFrozenBoss(entity) &&
                    entity.getHealth() < (YakRealms.isT6Enabled() ? 100000 : 50000) &&
                    !critMobs.containsKey(entity) &&
                    !entity.hasMetadata("criticalState")) {
                // Force critical state for frozen boss at low health
                critMobs.put(entity, 10); // Longer duration
                entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));
            }
        }

        return finalDamage;
    }

    /**
     * Check if a player is safespotting
     *
     * @param player The player
     * @param mob    The mob
     * @return true if player is in a safe spot
     */
    public boolean isSafeSpot(Player player, LivingEntity mob) {
        return MobUtils.isSafeSpot(player, mob);
    }

    /**
     * Track damage for drops
     *
     * @param entity The damaged entity
     * @param player The player who dealt damage
     * @param damage The amount of damage
     */
    public void trackDamage(LivingEntity entity, Player player, double damage) {
        if (entity == null || player == null) return;

        UUID entityUuid = entity.getUniqueId();
        UUID playerUuid = player.getUniqueId();

        // Initialize map if needed
        damageContributions.computeIfAbsent(entityUuid, k -> new ConcurrentHashMap<>());

        // Add damage to the map
        Map<UUID, Double> damageMap = damageContributions.get(entityUuid);
        damageMap.merge(playerUuid, damage, Double::sum);
    }

    /**
     * Get the player who dealt the most damage
     *
     * @param entity The entity
     * @return The top damage dealer or null
     */
    public Player getTopDamageDealer(LivingEntity entity) {
        if (entity == null) return null;

        Map<UUID, Double> damageMap = damageContributions.get(entity.getUniqueId());
        if (damageMap == null || damageMap.isEmpty()) {
            return null;
        }

        // Find player with highest damage
        Map.Entry<UUID, Double> maxEntry = null;
        for (Map.Entry<UUID, Double> entry : damageMap.entrySet()) {
            if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                maxEntry = entry;
            }
        }

        if (maxEntry != null) {
            return Bukkit.getPlayer(maxEntry.getKey());
        }

        return null;
    }

    /**
     * Get a list of top damage contributors for an entity
     *
     * @param entity The entity to check damage contributors for
     * @return List of damage contributors (UUID and damage amount) in descending order
     */
    public List<Map.Entry<UUID, Double>> getSortedDamageContributors(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();

        Map<UUID, Double> damageMap = damageContributions.get(entity.getUniqueId());
        if (damageMap == null || damageMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(damageMap.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        return entries;
    }

    /**
     * Clean up damage tracking data
     */
    private void cleanupDamageTracking() {
        try {
            // Instead of using removeIf with Bukkit.getEntity (which causes async issues),
            // we'll create a copy and remove entries manually
            Set<UUID> invalidEntities = new HashSet<>();

            // First identify invalid entities
            for (UUID entityId : damageContributions.keySet()) {
                Entity entity = null;
                try {
                    // This is now on the main thread, so it's safe to call
                    entity = Bukkit.getEntity(entityId);
                } catch (Exception e) {
                    // Just in case there's still an issue
                    logger.warning("[MobManager] Error checking entity validity: " + e.getMessage());
                }

                if (entity == null || !entity.isValid()) {
                    invalidEntities.add(entityId);
                }
            }

            // Now remove the invalid entities
            for (UUID entityId : invalidEntities) {
                damageContributions.remove(entityId);
            }

            // Clean up processedEntities list (keep only recent entries)
            processedEntities.clear();

            // Clean up mob spawner location entries for entities that no longer exist
            Set<UUID> invalidMobLocations = new HashSet<>();
            for (UUID entityId : mobSpawnerLocations.keySet()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity == null || !entity.isValid()) {
                    invalidMobLocations.add(entityId);
                }
            }

            for (UUID entityId : invalidMobLocations) {
                mobSpawnerLocations.remove(entityId);
            }

            if (debug && (!invalidEntities.isEmpty() || !invalidMobLocations.isEmpty())) {
                logger.info("[MobManager] Cleaned up " + invalidEntities.size() + " invalid damage contribution entries and " +
                        invalidMobLocations.size() + " invalid mob location entries");
            }
        } catch (Exception e) {
            logger.severe("[MobManager] Error in cleanupDamageTracking: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if an entity has metadata with the given key
     *
     * @param entity The entity to check
     * @param key    The metadata key
     * @return true if the entity has the metadata
     */
    public boolean hasMetadata(LivingEntity entity, String key) {
        return entity != null && entity.hasMetadata(key);
    }

    /**
     * Get metadata as a string
     *
     * @param entity       The entity
     * @param key          The metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
     */
    public String getMetadataString(LivingEntity entity, String key, String defaultValue) {
        return MobUtils.getMetadataString(entity, key, defaultValue);
    }

    /**
     * Get metadata as a boolean
     *
     * @param entity       The entity
     * @param key          The metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
     */
    public boolean getMetadataBoolean(LivingEntity entity, String key, boolean defaultValue) {
        return MobUtils.getMetadataBoolean(entity, key, defaultValue);
    }

    /**
     * Get metadata as an integer
     *
     * @param entity       The entity
     * @param key          The metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
     */
    public int getMetadataInt(LivingEntity entity, String key, int defaultValue) {
        return MobUtils.getMetadataInt(entity, key, defaultValue);
    }

    /**
     * Set metadata on an entity
     *
     * @param entity The entity
     * @param key    The metadata key
     * @param value  The metadata value
     */
    public void setMetadata(LivingEntity entity, String key, Object value) {
        if (entity == null) return;

        if (value instanceof String) {
            entity.setMetadata(key, new FixedMetadataValue(plugin, value));
        } else if (value instanceof Integer) {
            entity.setMetadata(key, new FixedMetadataValue(plugin, value));
        } else if (value instanceof Boolean) {
            entity.setMetadata(key, new FixedMetadataValue(plugin, value));
        } else if (value instanceof Float || value instanceof Double) {
            entity.setMetadata(key, new FixedMetadataValue(plugin, value));
        }
    }

    /**
     * Display detailed mob info to a player
     * Enhanced version with more detailed information
     *
     * @param player The player
     * @param entity The entity
     */
    public void displayMobInfo(Player player, LivingEntity entity) {
        if (player == null || entity == null) return;

        player.sendMessage(ChatColor.GOLD + "╔════ " + ChatColor.YELLOW + "Mob Information" + ChatColor.GOLD + " ════╗");

        // Basic entity information
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Entity ID: " +
                ChatColor.WHITE + entity.getEntityId() + " (" + entity.getUniqueId().toString().substring(0, 8) + ")");

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Entity Type: " +
                ChatColor.WHITE + entity.getType().name());

        // Get custom name and type
        String customName = entity.getCustomName();
        if (customName != null) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Custom Name: " + customName);
        }

        // Get stored original name
        if (originalNames.containsKey(entity.getUniqueId())) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Original Name: " +
                    ChatColor.WHITE + originalNames.get(entity.getUniqueId()));
        }

        // Get mob type
        String mobType = "None";
        if (entity.hasMetadata("type")) {
            mobType = entity.getMetadata("type").get(0).asString();
        } else if (entity.hasMetadata("customName")) {
            mobType = entity.getMetadata("customName").get(0).asString();
        }
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Mob Type: " + ChatColor.WHITE + mobType);

        // Tier and status
        int tier = getMobTier(entity);
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Tier: " +
                ChatColor.WHITE + tier + getTierColorName(tier));

        boolean elite = isElite(entity);
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Elite: " +
                (elite ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        boolean worldBoss = isWorldBoss(entity);
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "World Boss: " +
                (worldBoss ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        // Health information
        double health = entity.getHealth();
        double maxHealth = entity.getMaxHealth();
        double healthPercent = (health / maxHealth) * 100;
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Health: " +
                ChatColor.WHITE + String.format("%.1f/%.1f (%.1f%%)", health, maxHealth, healthPercent));

        // Critical state information
        boolean inCritical = critMobs.containsKey(entity) || entity.hasMetadata("criticalState");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Critical State: " +
                (inCritical ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        if (inCritical && critMobs.containsKey(entity)) {
            int critState = critMobs.get(entity);
            if (critState < 0) {
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Critical State: " +
                        ChatColor.GREEN + "Ready to Attack");
            } else {
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Critical Ticks Remaining: " +
                        ChatColor.WHITE + critState);
            }
        }

        // Equipment information
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Equipment:");

        // Main hand weapon
        ItemStack weapon = entity.getEquipment().getItemInMainHand();
        if (weapon != null && weapon.getType() != Material.AIR) {
            String weaponName = weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName() ?
                    weapon.getItemMeta().getDisplayName() : weapon.getType().name();

            player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.YELLOW + "Weapon: " +
                    ChatColor.WHITE + weaponName);

            // Show damage ranges
            List<Integer> damageRange = MobUtils.getDamageRange(weapon);
            player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.YELLOW + "Damage Range: " +
                    ChatColor.WHITE + damageRange.get(0) + " - " + damageRange.get(1));
        } else {
            player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.YELLOW + "Weapon: " +
                    ChatColor.RED + "None");
        }

        // Armor
        ItemStack[] armor = entity.getEquipment().getArmorContents();
        String armorStatus = ChatColor.RED + "None";
        if (armor != null && armor.length > 0) {
            int armorPieces = 0;
            for (ItemStack piece : armor) {
                if (piece != null && piece.getType() != Material.AIR) {
                    armorPieces++;
                }
            }
            armorStatus = ChatColor.WHITE.toString() + armorPieces + "/4 pieces";
        }
        player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.YELLOW + "Armor: " + armorStatus);

        // Special mob types
        if (MobUtils.isFrozenBoss(entity)) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Special Type: " +
                    ChatColor.AQUA + "Frozen Boss");
        } else if (MobUtils.isGolemBoss(entity)) {
            int stage = MobUtils.getGolemStage(entity);
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Special Type: " +
                    ChatColor.GOLD + "Golem Boss (Stage " + stage + ")");
        } else if (MobUtils.isSkeletonElite(entity)) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Special Type: " +
                    ChatColor.LIGHT_PURPLE + "Skeleton Elite");
        }

        // Lightning multiplier info
        if (entity.hasMetadata("LightningMultiplier")) {
            int multiplier = entity.getMetadata("LightningMultiplier").get(0).asInt();
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Lightning Multiplier: " +
                    ChatColor.GOLD + "⚡ " + ChatColor.WHITE + multiplier + "x");
        }

        // Active potion effects
        Collection<PotionEffect> effects = entity.getActivePotionEffects();
        if (!effects.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Active Effects:");
            for (PotionEffect effect : effects) {
                String effectName = effect.getType().getName();
                int amplifier = effect.getAmplifier() + 1; // +1 because Minecraft displays level 0 as level I
                int duration = effect.getDuration() / 20; // Convert to seconds

                player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.WHITE +
                        capitalize(effectName) + " " + getRomanNumeral(amplifier) +
                        ChatColor.GRAY + " (" + duration + "s)");
            }
        }

        // World boss info
        if (worldBoss && activeWorldBoss != null && activeWorldBoss.getEntity() == entity) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "World Boss Info:");
            player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.YELLOW + "Phase: " +
                    ChatColor.WHITE + activeWorldBoss.getCurrentPhase());

            player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.YELLOW + "Berserk: " +
                    (activeWorldBoss.isBerserk() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

            // Top damage dealers
            List<Map.Entry<UUID, Double>> topDamagers = getSortedDamageContributors(entity);
            if (!topDamagers.isEmpty()) {
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Top Damage Dealers:");

                for (int i = 0; i < Math.min(3, topDamagers.size()); i++) {
                    Map.Entry<UUID, Double> entry = topDamagers.get(i);
                    Player damagePlayer = Bukkit.getPlayer(entry.getKey());
                    String playerName = damagePlayer != null ? damagePlayer.getName() : "Offline Player";

                    player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.YELLOW + "#" + (i + 1) + ": " +
                            ChatColor.WHITE + playerName + " - " +
                            ChatColor.RED + String.format("%.0f", entry.getValue()) + " damage");
                }
            }
        }

        // Spawner information if applicable
        Location nearestSpawner = findNearestSpawner(entity.getLocation(), getMobRespawnDistanceCheck());
        if (nearestSpawner != null) {
            double distance = entity.getLocation().distance(nearestSpawner);

            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Nearest Spawner: " +
                    ChatColor.WHITE + formatLocation(nearestSpawner) +
                    ChatColor.GRAY + " (" + String.format("%.1f", distance) + " blocks)");
        }

        // Last damage time
        if (nameTimes.containsKey(entity.getUniqueId())) {
            long lastDamage = nameTimes.get(entity.getUniqueId());
            long timeSince = System.currentTimeMillis() - lastDamage;

            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Last Damaged: " +
                    ChatColor.WHITE + String.format("%.1f", timeSince / 1000.0) + "s ago");
        }

        // Management info - which system is handling this mob
        if (getCustomMob(entity) != null) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Managed By: " +
                    ChatColor.WHITE + "CustomMob System");
        } else {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Managed By: " +
                    ChatColor.WHITE + "Legacy System");
        }

        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════╝");
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

        return String.format("%s [%d, %d, %d]",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    /**
     * Get color name for a tier
     *
     * @param tier The tier
     * @return Color name for the tier
     */
    private String getTierColorName(int tier) {
        switch (tier) {
            case 1:
                return " " + ChatColor.GRAY + "(White)";
            case 2:
                return " " + ChatColor.GREEN + "(Green)";
            case 3:
                return " " + ChatColor.AQUA + "(Aqua)";
            case 4:
                return " " + ChatColor.LIGHT_PURPLE + "(Light Purple)";
            case 5:
                return " " + ChatColor.YELLOW + "(Yellow)";
            case 6:
                return " " + ChatColor.BLUE + "(Blue)";
            default:
                return "";
        }
    }

    /**
     * Capitalize a string
     *
     * @param str The string to capitalize
     * @return Capitalized string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        String[] words = str.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Convert integer to Roman numeral (for potion effects)
     *
     * @param num The number to convert
     * @return Roman numeral string
     */
    private String getRomanNumeral(int num) {
        switch (num) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            case 5:
                return "V";
            case 6:
                return "VI";
            case 7:
                return "VII";
            case 8:
                return "VIII";
            case 9:
                return "IX";
            case 10:
                return "X";
            default:
                return String.valueOf(num);
        }
    }

    /**
     * Calculate respawn delay based on tier and elite status
     * Standardized implementation used by all components
     *
     * @param tier  Mob tier (1-6)
     * @param elite Whether the mob is elite
     * @return Respawn delay in milliseconds
     */
    public long calculateRespawnDelay(int tier, boolean elite) {
        // Base respawn time (3 minutes)
        long baseDelay = MIN_RESPAWN_DELAY;

        // Calculate tier multiplier - 1.0 for tier 1, scaling to 2.0 for tier 6
        // This creates a more balanced progression than direct multiplication
        double tierFactor = 1.0 + ((tier - 1) * 0.2);

        // Elite multiplier (elite mobs take 1.5x as long)
        double eliteMultiplier = elite ? 1.5 : 1.0;

        // Calculate delay - more balanced progression across tiers
        long calculatedDelay = (long) (baseDelay * tierFactor * eliteMultiplier);

        // Add controlled randomization (90%-110% of calculated time)
        // Reduced range to minimize extreme variations
        double randomFactor = 0.9 + (Math.random() * 0.2);
        calculatedDelay = (long) (calculatedDelay * randomFactor);

        // Enforce minimum and maximum boundaries
        return Math.min(Math.max(calculatedDelay, MIN_RESPAWN_DELAY), MAX_RESPAWN_DELAY);
    }

    /**
     * Record the death of a mob and calculate its respawn time
     * This is the central point for all respawn time management
     *
     * @param mobType The mob type ID
     * @param tier    The tier level
     * @param elite   Whether it was an elite
     * @return The calculated respawn time
     */
    public long recordMobDeath(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return 0;

        String key = getResponKey(mobType, tier, elite);
        long currentTime = System.currentTimeMillis();

        // Calculate respawn time
        long respawnDelay = calculateRespawnDelay(tier, elite);
        long respawnTime = currentTime + respawnDelay;

        // Store for future reference
        respawnTimes.put(key, respawnTime);

        // Also update the last death time tracking
        mobTypeLastDeath.put(key, currentTime);

        if (debug) {
            logger.info("[MobManager] Recorded death of " + key + " at " + currentTime);
            logger.info("[MobManager] Calculated respawn time: " + respawnTime +
                    " (in " + (respawnDelay / 1000) + " seconds)");
        }

        return respawnTime;
    }

    /**
     * Check if a mob can respawn based on its calculated respawn time
     *
     * @param mobType The mob type ID
     * @param tier    The tier level
     * @param elite   Whether it's an elite
     * @return true if allowed to spawn, false if still in cooldown
     */
    public boolean canRespawn(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return true;

        String key = getResponKey(mobType, tier, elite);

        // Get saved respawn time
        Long respawnTime = respawnTimes.get(key);
        if (respawnTime == null) return true; // No respawn time set, allow spawn

        // Check if current time has passed the respawn time
        long currentTime = System.currentTimeMillis();
        boolean canRespawn = currentTime >= respawnTime;

        if (debug && !canRespawn) {
            long remainingTime = (respawnTime - currentTime) / 1000;
            logger.info("[MobManager] Prevented respawn of " + key +
                    " - Respawn available in " + remainingTime + " seconds");
        }

        return canRespawn;
    }

    /**
     * Get the respawn time for a specific mob type/tier combination
     *
     * @param mobType The mob type ID
     * @param tier    The tier level
     * @param elite   Whether it's an elite
     * @return The respawn time in milliseconds or 0 if not found
     */
    public long getResponTime(String mobType, int tier, boolean elite) {
        String key = getResponKey(mobType, tier, elite);
        return respawnTimes.getOrDefault(key, 0L);
    }

    /**
     * Get a respawn key that uniquely identifies this mob type/tier
     *
     * @param mobType The mob type ID
     * @param tier    The tier level
     * @param elite   Whether it's an elite
     * @return A unique identifier string
     */
    public String getResponKey(String mobType, int tier, boolean elite) {
        return mobType + ":" + tier + ":" + (elite ? "elite" : "normal");
    }

    /**
     * Get the last death time for a specific mob type/tier
     *
     * @param mobType The mob type ID
     * @param tier    The tier level
     * @param elite   Whether it was an elite
     * @return The last death time in milliseconds, or 0 if not found
     */
    public long getLastDeathTime(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return 0;

        String key = getResponKey(mobType, tier, elite);
        return mobTypeLastDeath.getOrDefault(key, 0L);
    }

    /**
     * Get the remaining time until a mob can respawn
     *
     * @param mobType The mob type ID
     * @param tier    The tier level
     * @param elite   Whether it's an elite
     * @return Remaining time in milliseconds or 0 if can respawn now
     */
    public long getRemainingRespawnTime(String mobType, int tier, boolean elite) {
        String key = getResponKey(mobType, tier, elite);
        Long respawnTime = respawnTimes.get(key);

        if (respawnTime == null) return 0;

        long currentTime = System.currentTimeMillis();
        return Math.max(0, respawnTime - currentTime);
    }

    /**
     * Clear all respawn timers (for testing or admin commands)
     */
    public void clearAllRespawnTimers() {
        respawnTimes.clear();
        logger.info("[MobManager] All respawn timers cleared");
    }

    /**
     * Set spawner visibility for a specific location
     *
     * @param location The spawner location
     * @param visible  Whether to make it visible or hidden
     * @return true if successful
     */
    public boolean setSpawnerVisibility(Location location, boolean visible) {
        if (location == null) return false;
        return spawner.setSpawnerVisibility(location, visible);
    }

    /**
     * Get active mob count for a spawner
     *
     * @param location The spawner location
     * @return Number of active mobs
     */
    public int getActiveMobCount(Location location) {
        return spawner.getActiveMobCount(location);
    }

    /**
     * Check if a spawner is visible
     *
     * @param location The spawner location
     * @return true if visible
     */
    public boolean isSpawnerVisible(Location location) {
        if (location == null) return false;
        return spawner.isSpawnerVisible(location);
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
        return spawner.toggleSpawnerVisibility(center, radius, show);
    }

    /**
     * Create or update hologram for a spawner
     *
     * @param location The spawner location
     */
    public void updateSpawnerHologram(Location location) {
        if (location == null) return;
        spawner.createOrUpdateSpawnerHologram(location);
    }

    /**
     * Remove hologram for a spawner
     *
     * @param location The spawner location
     */
    public void removeSpawnerHologram(Location location) {
        if (location == null) return;
        spawner.removeSpawnerHologram(location);
    }

    /**
     * Get the spawner instance
     *
     * @return The spawner instance
     */
    public MobSpawner getSpawner() {
        return spawner;
    }

    /**
     * Get spawner metrics for a location
     *
     * @param location The spawner location
     * @return Spawner metrics or null if not found
     */
    public SpawnerMetrics getSpawnerMetrics(Location location) {
        if (location == null) return null;
        return spawner.getSpawnerMetrics(location);
    }

    /**
     * Find all spawners within a radius
     *
     * @param center Center location
     * @param radius Radius to search
     * @return List of spawner locations
     */
    public List<Location> findNearbySpawners(Location center, double radius) {
        if (center == null) return Collections.emptyList();
        return spawner.findSpawnersInRadius(center, radius);
    }

    /**
     * Reset a specific spawner's timer and counters
     *
     * @param location The spawner location
     * @return true if reset was successful
     */
    public boolean resetSpawner(Location location) {
        if (location == null) return false;
        return spawner.resetSpawner(location);
    }

    /**
     * Find nearest spawner to a location
     *
     * @param location    The location
     * @param maxDistance Maximum distance to check
     * @return Nearest spawner location or null
     */
    public Location findNearestSpawner(Location location, double maxDistance) {
        return spawner.findNearestSpawner(location, maxDistance);
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
     * Add a spawner at a location
     *
     * @param location The location
     * @param data     Spawner data string
     * @return true if successful
     */
    public boolean addSpawner(Location location, String data) {
        return spawner.addSpawner(location, data);
    }

    /**
     * Remove a spawner
     *
     * @param location The location
     * @return true if removed
     */
    public boolean removeSpawner(Location location) {
        return spawner.removeSpawner(location);
    }

    /**
     * Get all spawners
     *
     * @return Map of locations to spawner data
     */
    public Map<Location, String> getAllSpawners() {
        return spawner.getAllSpawners();
    }

    /**
     * Send spawner info to a player
     *
     * @param player   The player
     * @param location The spawner location
     */
    public void sendSpawnerInfo(Player player, Location location) {
        if (player == null || location == null) return;
        spawner.sendSpawnerInfo(player, location);
    }

    /**
     * Reset all spawners
     */
    public void resetAllSpawners() {
        spawner.resetAllSpawners();
        logger.info("[MobManager] All spawners have been reset");
    }

    /**
     * Add a spawner using a predefined template
     *
     * @param location     The location to add the spawner
     * @param templateName The name of the template
     * @return true if created successfully
     */
    public boolean addSpawnerFromTemplate(Location location, String templateName) {
        return spawner.addSpawnerFromTemplate(location, templateName);
    }

    /**
     * Get available spawner templates
     *
     * @return Map of template names to data strings
     */
    public Map<String, String> getAllSpawnerTemplates() {
        return spawner.getAllTemplates();
    }

    /**
     * Process entity damage events
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        try {
            // Skip if entity is not a living entity or is a player
            if (!(event.getEntity() instanceof LivingEntity entity) || event.getEntity() instanceof Player) {
                return;
            }

            double damage = event.getFinalDamage();

            // Identify the player who dealt damage
            Player damager = null;
            if (event.getDamager() instanceof Player) {
                damager = (Player) event.getDamager();
            } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
                if (projectile.getShooter() instanceof Player) {
                    damager = (Player) projectile.getShooter();
                }
            }

            if (damager != null) {
                // Store original name when damaged if we don't have it
                if (!originalNames.containsKey(entity.getUniqueId()) && entity.getCustomName() != null) {
                    String currentName = entity.getCustomName();
                    if (!currentName.contains(ChatColor.GREEN + "|") && !currentName.contains(ChatColor.GRAY + "|")) {
                        originalNames.put(entity.getUniqueId(), currentName);
                    }
                }

                // Check for critical hit
                rollForCriticalHit(entity, damage);

                // Track damage contribution
                trackDamage(entity, damager, damage);

                // Process damage based on mob type
                CustomMob mob = getCustomMob(entity);
                if (mob != null) {
                    // Use custom mob's damage handling
                    mob.damage(damage);

                    // Ensure health bar is visible and updated
                    mob.nameVisible = true;
                    mob.updateHealthBar();
                } else {
                    // Use manager's damage handler for legacy mobs
                    damageEntity(entity, damage);

                    // Update name tracking for health bar visibility
                    nameTimes.put(entity.getUniqueId(), System.currentTimeMillis());

                    // Update health bar
                    updateEntityHealthBar(entity);
                }

                // Check for safespotting
                if (isSafeSpot(damager, entity)) {
                    entity.teleport(damager.getLocation().clone().add(0, 1, 0));

                    if (debug) {
                        logger.info("[MobManager] Detected safespotting by " + damager.getName() +
                                " - teleporting mob to player");
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("[MobManager] Error in onEntityDamageByEntity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Block mob vs mob damage
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMobHitMob(EntityDamageByEntityEvent event) {
        // Block mob-on-mob damage
        if (event.getDamager() instanceof LivingEntity &&
                !(event.getDamager() instanceof Player) &&
                !(event.getEntity() instanceof Player)) {

            if (event.getDamager().isCustomNameVisible() &&
                    !event.getDamager().getCustomName().equalsIgnoreCase("Celestial Ally")) {
                event.setCancelled(true);
                event.setDamage(0.0);
            }
        }
    }

    /**
     * Handle mobs hitting players
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMobHitPlayer(EntityDamageByEntityEvent event) {
        try {
            if (event.getDamage() <= 0.0 ||
                    !(event.getDamager() instanceof LivingEntity mob) ||
                    !(event.getEntity() instanceof Player player)) {
                return;
            }

            // Process sound effects
            if (!soundTimes.containsKey(mob.getUniqueId()) ||
                    System.currentTimeMillis() - soundTimes.get(mob.getUniqueId()) > 500) {

                soundTimes.put(mob.getUniqueId(), System.currentTimeMillis());
                playMobHitSound(mob, event.getDamage() >= mob.getHealth());
            }

            // Calculate damage based on mob equipment and critical state
            int calculatedDamage = calculateMobDamage(mob, player);

            // Set final damage
            if (calculatedDamage > 0) {
                event.setDamage(calculatedDamage);

                if (debug) {
                    logger.info("[MobManager] Mob hit player for final damage: " + calculatedDamage);
                }
            }
        } catch (Exception e) {
            logger.warning("[MobManager] Error in onMobHitPlayer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Calculate mob damage based on equipment and critical state
     *
     * @param mob    The attacking mob
     * @param player The player being attacked
     * @return Calculated damage
     */
    private int calculateMobDamage(LivingEntity mob, Player player) {
        Random random = new Random();
        int dmg = 1;

        // Get damage from weapon if available
        if (mob.getEquipment().getItemInMainHand() != null &&
                mob.getEquipment().getItemInMainHand().getType() != Material.AIR) {

            List<Integer> damageRange = MobUtils.getDamageRange(mob.getEquipment().getItemInMainHand());
            int min = damageRange.get(0);
            int max = damageRange.get(1);
            dmg = random.nextInt(max - min + 1) + min + 1;
        }

        // Check for critical attack
        boolean isInCriticalState = critMobs.containsKey(mob);
        int criticalState = critMobs.getOrDefault(mob, 0);

        // For regular mobs: only execute critical if in ready state
        if (isInCriticalState && !MobUtils.isElite(mob) && criticalState < 0) {
            // Normal mob critical attack - execute it
            dmg = executeNormalMobCriticalAttack(mob, player, dmg);
        }
        // For elite mobs: check if they just finished their attack animation
        else if (isInCriticalState && MobUtils.isElite(mob) && criticalState == 0) {
            // Elite mob critical attack - quadruple damage
            dmg *= 4;

            // Remove elite from crit tracking after attack
            critMobs.remove(mob);
            if (mob.hasMetadata("criticalState")) {
                mob.removeMetadata("criticalState", plugin);
            }

            // Play sound effect
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.3f);

            // Restore original name after attack
            restoreOriginalName(mob);
        }

        // Apply tier and equipment-based damage modifiers
        ItemStack weapon = mob.getEquipment().getItemInMainHand();
        if (weapon != null && weapon.getType() != Material.AIR) {
            String weaponType = weapon.getType().name();
            boolean hasEnchants = weapon.getItemMeta() != null && weapon.getItemMeta().hasEnchants();

            // Apply the exact same damage multipliers as in original
            if (weaponType.contains("WOOD_")) {
                dmg = hasEnchants ? (int) ((double) dmg * 2.5) : (int) ((double) dmg * 0.8);
            } else if (weaponType.contains("STONE_")) {
                dmg = hasEnchants ? (int) ((double) dmg * 2.5) : (int) ((double) dmg * 0.9);
            } else if (weaponType.contains("IRON_")) {
                dmg = hasEnchants ? (dmg *= 3) : (int) ((double) dmg * 1.2);
            } else if (weaponType.contains("DIAMOND_") &&
                    !mob.getEquipment().getArmorContents()[0].getType().name().contains("LEATHER_")) {
                dmg = hasEnchants ? (dmg *= 5) : (int) ((double) dmg * 1.4);
            } else if (weaponType.contains("GOLD_")) {
                dmg = hasEnchants ? (dmg *= 6) : (dmg *= 2);
            } else if (weaponType.contains("DIAMOND_")) {
                dmg = hasEnchants ? (dmg *= 8) : (dmg *= 4);
            }
        }

        // Reduce magma cube damage
        if (mob instanceof org.bukkit.entity.MagmaCube) {
            dmg = (int) ((double) dmg * 0.5);
        }

        // Ensure minimum damage
        if (dmg < 1) {
            dmg = 1;
        }

        // Apply lightning multiplier
        if (mob.hasMetadata("LightningMultiplier")) {
            dmg *= mob.getMetadata("LightningMultiplier").get(0).asInt();
        }

        return dmg;
    }

    /**
     * Play appropriate sound for mob hit
     *
     * @param mob     The mob entity
     * @param isDying Whether the mob is dying
     */
    private void playMobHitSound(LivingEntity mob, boolean isDying) {
        // Play appropriate sounds based on entity type
        if (mob instanceof org.bukkit.entity.Skeleton) {
            if (isDying) {
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SKELETON_DEATH, 1.0f, 1.0f);
            }
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SKELETON_HURT, 1.0f, 1.0f);
        } else if (mob instanceof org.bukkit.entity.Zombie) {
            if (isDying) {
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ZOMBIE_DEATH, 1.0f, 1.0f);
            }
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ZOMBIE_HURT, 1.0f, 1.0f);
        } else if (mob instanceof org.bukkit.entity.Spider || mob instanceof org.bukkit.entity.CaveSpider) {
            if (isDying) {
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SPIDER_DEATH, 1.0f, 1.0f);
            }
        } else if (mob instanceof org.bukkit.entity.Silverfish) {
            if (isDying) {
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SILVERFISH_DEATH, 1.0f, 1.0f);
            }
        } else if (mob instanceof org.bukkit.entity.PigZombie) {
            if (isDying) {
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PIGLIN_DEATH, 1.0f, 1.0f);
            }
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PIGLIN_HURT, 1.0f, 1.0f);
        }
    }

    /**
     * Handle entity death events
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();

            // Skip if already processed
            if (entity instanceof Player || processedEntities.contains(entity.getUniqueId())) {
                return;
            }

            // Add to processed entities to prevent duplicate processing
            processedEntities.add(entity.getUniqueId());

            // Handle entity death
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                // Record the death for respawn timing
                if (entity.hasMetadata("type")) {
                    String mobType = entity.getMetadata("type").get(0).asString();
                    int tier = getMobTier(entity);
                    boolean elite = isElite(entity);

                    // Record the death - this sets up the respawn timer
                    long respawnTime = recordMobDeath(mobType, tier, elite);

                    // ALWAYS log respawn info
                    long delay = (respawnTime - System.currentTimeMillis()) / 1000;
                    logger.info("[MobManager] Mob " + mobType + " T" + tier + (elite ? "+" : "") +
                            " died - respawn scheduled in " + delay + " seconds");
                }

                // Notify the spawner
                Location spawnerLoc = findSpawnerForMob(entity);
                if (spawnerLoc != null) {
                    spawner.registerMobDeath(spawnerLoc, entity.getUniqueId());

                    // Log successful registration
                    logger.info("[MobManager] Registered mob death with spawner at " +
                            formatLocation(spawnerLoc));
                } else {
                    logger.warning("[MobManager] Could not find spawner for dead entity: " +
                            entity.getType());
                }

                // Clean up tracking for this mob
                mobSpawnerLocations.remove(entity.getUniqueId());
            }
        } catch (Exception e) {
            logger.warning("[MobManager] Error in onEntityDeath: " + e.getMessage());
        }
    }

    /**
     * Find the spawner that spawned this mob
     *
     * @param entity The entity
     * @return The spawner location or null
     */
    private Location findSpawnerForMob(LivingEntity entity) {
        if (entity == null) return null;

        // First check our spawner location tracking
        if (mobSpawnerLocations.containsKey(entity.getUniqueId())) {
            return mobSpawnerLocations.get(entity.getUniqueId());
        }

        // Check if entity has spawner metadata
        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();
            if (spawnerId != null && !spawnerId.isEmpty()) {
                // Iterate through all spawners to find the matching ID
                for (Map.Entry<Location, String> entry : spawner.getAllSpawners().entrySet()) {
                    Location spawnerLoc = entry.getKey();
                    // Generate ID from location to compare with entity's spawner ID
                    String locationId = generateSpawnerId(spawnerLoc);
                    if (spawnerId.equals(locationId)) {
                        return spawnerLoc;
                    }
                }
            }
        }

        // Find nearest spawner as fallback
        return spawner.findNearestSpawner(entity.getLocation(), mobRespawnDistanceCheck);
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        try {
            // Save all important data
            spawner.saveSpawners();

            // Cancel tasks
            if (criticalStateTask != null) criticalStateTask.cancel();
            if (nameVisibilityTask != null) nameVisibilityTask.cancel();
            if (activeMobsTask != null) activeMobsTask.cancel();
            if (cleanupTask != null) cleanupTask.cancel();
            if (mobPositionTask != null) mobPositionTask.cancel();

            // Shutdown spawner system
            spawner.shutdown();

            // Remove all active mobs
            for (CustomMob mob : new ArrayList<>(activeMobs.values())) {
                mob.remove();
            }

            // Clear all maps
            activeMobs.clear();
            critMobs.clear();
            soundTimes.clear();
            nameTimes.clear();
            originalNames.clear();
            damageContributions.clear();
            mobTargets.clear();
            processedEntities.clear();
            lastSafespotCheck.clear();
            respawnTimes.clear();
            mobTypeLastDeath.clear();
            mobSpawnerLocations.clear();

            // Clear world boss
            activeWorldBoss = null;

            logger.info("[MobManager] has been shut down");
        } catch (Exception e) {
            logger.severe("[MobManager] Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}