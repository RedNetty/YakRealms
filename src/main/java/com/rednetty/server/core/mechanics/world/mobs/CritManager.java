package com.rednetty.server.core.mechanics.world.mobs;

import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.core.mechanics.world.mobs.core.MobType;
import com.rednetty.server.core.mechanics.world.mobs.utils.MobUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Updated: CritManager with immediate charging for normal mobs and countdown system for elites
 * Key Changes:
 * - Normal mobs skip countdown and go directly to charged state
 * - Elite mobs continue with 4-step countdown system leading to whirlwind explosion
 * - Normal mob crit damage reduced to 2x (from 3x)
 * - Proper state management for both mob types
 */
public class CritManager {

    // ================ CONSTANTS ================
    private static final int CRIT_CHANCE_RANGE = 250; // 1-250 range per spec
    private static final int COUNTDOWN_TICK_INTERVAL = 20; // 20 ticks = 1 second per step
    private static final double NORMAL_CRIT_DAMAGE_MULTIPLIER = 2.0; // Changed: 2x for normal mobs
    private static final double ELITE_CRIT_DAMAGE_MULTIPLIER = 4.0; // 4x for elite mobs
    private static final double WHIRLWIND_AOE_RANGE = 7.0; // 7x7x7 blocks
    private static final double WHIRLWIND_DAMAGE_MULTIPLIER = 3.0; // Weapon damage × 3
    private static final double WHIRLWIND_KNOCKBACK_STRENGTH = 3.0; // 3x velocity
    private static final int FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T5 = 50000; // 50k for T5
    private static final int FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T6 = 100000; // 100k for T6

    // ================ SINGLETON ================
    private static volatile CritManager instance;
    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<UUID, CritState> activeCrits = new ConcurrentHashMap<>();

    // ================  STATE TRACKING ================
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicLong totalCritsInitiated = new AtomicLong(0);
    private final AtomicLong totalExplosionsExecuted = new AtomicLong(0);

    /**
     * Updated: Internal class to hold the state of a mob currently in the crit process
     */
    private static class CritState {
        private final UUID entityUUID;
        private final String mobUniqueId;
        private final boolean isElite;
        private final int tier;
        private final MobType mobType;
        private volatile int countdown; // Starts at 4 for elites, 0 for normal mobs
        private volatile int ticksRemaining; // Exact ticks until next countdown step
        private final AtomicBoolean immobilized = new AtomicBoolean(false);
        private final AtomicBoolean isFrozenBossRestart = new AtomicBoolean(false);
        private final boolean isNormalMobInstantCharge; // NEW: Flag for normal mobs that skip countdown

        // Constructor for elite mobs (countdown system)
        CritState(CustomMob mob) {
            this.entityUUID = mob.getEntity().getUniqueId();
            this.mobUniqueId = mob.getUniqueMobId();
            this.isElite = mob.isElite();
            this.tier = mob.getTier();
            this.mobType = mob.getType();
            this.countdown = 4; // Elite mobs start countdown at 4
            this.ticksRemaining = COUNTDOWN_TICK_INTERVAL; // 20 ticks = 1 second
            this.isNormalMobInstantCharge = false;
        }

        // Constructor for normal mobs (instant charge)
        CritState(CustomMob mob, boolean instantCharge) {
            this.entityUUID = mob.getEntity().getUniqueId();
            this.mobUniqueId = mob.getUniqueMobId();
            this.isElite = mob.isElite();
            this.tier = mob.getTier();
            this.mobType = mob.getType();
            if (instantCharge && !mob.isElite()) {
                this.countdown = 0; // Normal mobs start charged immediately
                this.ticksRemaining = 0;
                this.isNormalMobInstantCharge = true;
            } else {
                this.countdown = 4;
                this.ticksRemaining = COUNTDOWN_TICK_INTERVAL;
                this.isNormalMobInstantCharge = false;
            }
        }

        // Special constructor for Frozen Boss restart (3-step instead of 4-step)
        CritState(CustomMob mob, boolean frozenBossRestart, boolean isRestart) {
            this(mob);
            if (frozenBossRestart && isRestart) {
                this.countdown = 3; // Frozen Boss restart is 3-step
                this.isFrozenBossRestart.set(true);
            }
        }

        // Getters
        public UUID getEntityUUID() { return entityUUID; }
        public String getMobUniqueId() { return mobUniqueId; }
        public boolean isElite() { return isElite; }
        public int getTier() { return tier; }
        public MobType getMobType() { return mobType; }
        public int getCountdown() { return countdown; }
        public boolean isImmobilized() { return immobilized.get(); }
        public boolean isFrozenBossRestart() { return isFrozenBossRestart.get(); }
        public boolean isInstantCharge() { return isNormalMobInstantCharge; }

        // State management
        public void decrementCountdown() { this.countdown--; }
        public void resetTickTimer() { this.ticksRemaining = COUNTDOWN_TICK_INTERVAL; }
        public void decrementTicks() { this.ticksRemaining--; }
        public boolean shouldDecrementCountdown() { return ticksRemaining <= 0; }
        public void setImmobilized(boolean immobilized) { this.immobilized.set(immobilized); }
    }

    private CritManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Initializes the singleton instance of the CritManager.
     */
    public static void initialize(JavaPlugin plugin) {
        if (instance == null) {
            synchronized (CritManager.class) {
                if (instance == null) {
                    instance = new CritManager(plugin);
                    instance.startMasterTask();
                    instance.logger.info("CritManager initialized successfully");
                }
            }
        }
    }

    /**
     * Gets the singleton instance of the CritManager.
     */
    public static CritManager getInstance() {
        return instance;
    }

    /**
     * Updated: Starts the single master task that handles all active crits
     */
    private void startMasterTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isShuttingDown.get() || activeCrits.isEmpty()) {
                return;
            }

            Iterator<Map.Entry<UUID, CritState>> iterator = activeCrits.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, CritState> entry = iterator.next();
                UUID uuid = entry.getKey();
                CritState state = entry.getValue();

                // Get the mob entity
                Entity entity = Bukkit.getEntity(uuid);
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    // Cleanup invalid mobs
                    cleanupCritState(uuid, state);
                    iterator.remove();
                    continue;
                }

                LivingEntity livingEntity = (LivingEntity) entity;
                CustomMob mob = MobManager.getInstance().getCustomMob(livingEntity);
                if (mob == null) {
                    // Cleanup mobs not in manager
                    cleanupCritState(uuid, state);
                    iterator.remove();
                    continue;
                }

                // Process the crit tick
                boolean shouldRemove = processCritTick(state, mob);
                if (shouldRemove) {
                    cleanupCritState(uuid, state);
                    iterator.remove();
                }
            }
        }, 0L, 1L); // Run every tick for precise timing
    }

    /**
     * Updated: Process a single crit tick for a mob with different logic for normal vs elite mobs
     */
    private boolean processCritTick(CritState state, CustomMob mob) {
        LivingEntity entity = mob.getEntity();

        // NEW: Normal mobs that are instantly charged don't need tick processing
        if (state.isInstantCharge() && state.getCountdown() <= 0) {
            // Just show occasional warning effects and update display
            if (entity.getTicksLived() % 20 == 0) { // Every second
                showNormalMobChargedEffects(entity);
                triggerMobDisplayUpdate(mob);
            }
            return false; // Keep processing (don't remove until they attack)
        }

        // Elite mobs go through countdown system
        if (state.isElite()) {
            return processEliteCritTick(state, mob, entity);
        }

        // This shouldn't happen for normal mobs with new system, but safety fallback
        return false;
    }

    /**
     * NEW: Process elite mob crit tick (original countdown logic)
     */
    private boolean processEliteCritTick(CritState state, CustomMob mob, LivingEntity entity) {
        // Decrement tick timer
        state.decrementTicks();

        // Apply immobilization effects if not already applied
        if (!state.isImmobilized()) {
            applyEliteImmobilization(mob);
            state.setImmobilized(true);
        }

        // Show continuous warning effects every few ticks
        if (state.ticksRemaining % 5 == 0) {
            showCountdownWarningEffects(entity, state);
        }

        // Trigger display updates
        if (state.ticksRemaining % 10 == 0) { // Every 0.5 seconds
            triggerMobDisplayUpdate(mob);
        }

        // Check if it's time for the next major countdown step
        if (state.shouldDecrementCountdown()) {
            state.decrementCountdown();
            state.resetTickTimer();

            if (state.getCountdown() > 0) {
                // Continue countdown (4→3→2→1)
                handleCountdownStep(entity, state);
                triggerMobDisplayUpdate(mob);
                return false; // Continue processing
            } else {
                // Countdown reached 0 - execute whirlwind explosion
                handleCountdownFinish(mob, state);
                return true; // Remove from processing
            }
        }

        return false; // Continue processing
    }

    /**
     * NEW: Show effects for normal mobs that are charged and waiting
     */
    private void showNormalMobChargedEffects(LivingEntity entity) {
        try {
            // Subtle but noticeable effects for charged normal mobs
            entity.getWorld().spawnParticle(Particle.CRIT,
                    entity.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.05);

            // Occasional glow effect
            if (entity.getTicksLived() % 60 == 0) { // Every 3 seconds
                entity.getWorld().spawnParticle(Particle.WITCH,
                        entity.getLocation().add(0, 1.5, 0), 5, 0.5, 0.5, 0.5, 0.1);
            }
        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    /**
     * Simplified display update - let CustomMob handle its own display logic
     */
    private void triggerMobDisplayUpdate(CustomMob mob) {
        try {
            if (mob != null && mob.isValid()) {
                // CustomMob's new system will automatically handle critical state display
                // We just need to make sure it knows about state changes
                mob.updateDamageTime(); // This triggers display updates
            }
        } catch (Exception e) {
            logger.warning("[CritManager] Failed to trigger display update: " + e.getMessage());
        }
    }

    /**
     * Updated: Attempts to trigger a crit on a mob with different behavior for normal vs elite
     */
    public boolean initiateCrit(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) {
            return false;
        }

        LivingEntity entity = mob.getEntity();
        UUID entityId = entity.getUniqueId();

        if (activeCrits.containsKey(entityId)) {
            return false; // Already in crit state
        }

        // Roll for crit using proper 1-250 range
        int tier = mob.getTier();
        int roll = ThreadLocalRandom.current().nextInt(1, CRIT_CHANCE_RANGE + 1);
        int chance = getCritChanceByTier(tier);

        if (roll <= chance) {
            // CRIT TRIGGERED!
            CritState newState;

            if (mob.isElite()) {
                // Elite mobs use countdown system
                newState = new CritState(mob);
                logger.info(String.format("[CritManager] %s T%d ELITE crit triggered! Starting countdown. Roll: %d <= %d (%.1f%%) (ID: %s)",
                        mob.getType().getId(), tier, roll, chance, (chance * 100.0 / CRIT_CHANCE_RANGE), mob.getUniqueMobId()));
            } else {
                // Normal mobs skip countdown and go straight to charged
                newState = new CritState(mob, true); // true = instant charge
                logger.info(String.format("[CritManager] %s T%d NORMAL crit triggered! Instantly charged. Roll: %d <= %d (%.1f%%) (ID: %s)",
                        mob.getType().getId(), tier, roll, chance, (chance * 100.0 / CRIT_CHANCE_RANGE), mob.getUniqueMobId()));
            }

            activeCrits.put(entityId, newState);

            // Apply initial effects
            applyInitialCritEffects(mob, newState);

            // Trigger display update
            triggerMobDisplayUpdate(mob);

            totalCritsInitiated.incrementAndGet();

            return true;
        }

        return false;
    }

    /**
     * Get correct critical chances per game design spec
     */
    private int getCritChanceByTier(int tier) {
        return switch (tier) {
            case 1 -> 5;   // 5/250 = 2%
            case 2 -> 7;   // 7/250 = 2.8%
            case 3 -> 10;  // 10/250 = 4%
            case 4 -> 13;  // 13/250 = 5.2%
            case 5, 6 -> 20; // 20/250 = 8%
            default -> 20;
        };
    }

    /**
     * Updated: Apply initial crit effects with different behavior for normal vs elite mobs
     */
    private void applyInitialCritEffects(CustomMob mob, CritState state) {
        LivingEntity entity = mob.getEntity();

        try {
            if (state.isElite()) {
                // Elite: Creeper priming sound at 4.0f pitch for countdown start
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);
                // Large explosion particles for countdown warning
                entity.getWorld().spawnParticle(Particle.EXPLOSION,
                        entity.getLocation().add(0, 1, 0), 40, 0.3, 0.3, 0.3, 0.3f);
            } else {
                // Normal: Different sound and effects for instant charge
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                entity.getWorld().spawnParticle(Particle.WITCH,
                        entity.getLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0.1);
                entity.getWorld().spawnParticle(Particle.CRIT,
                        entity.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
            }
        } catch (Exception e) {
            logger.warning("[CritManager] Initial crit effects failed: " + e.getMessage());
        }
    }

    /**
     * Apply immobilization effects for elite mobs during countdown - matching legacy
     */
    private void applyEliteImmobilization(CustomMob mob) {
        LivingEntity entity = mob.getEntity();

        try {
            if (MobUtils.isFrozenBoss(entity)) {
                // Frozen Boss special behavior - affects nearby players instead
                double health = entity.getHealth();
                double maxHealth = entity.getMaxHealth();

                // Remove own slow effect first
                if (entity.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                    entity.removePotionEffect(PotionEffectType.SLOWNESS);
                }

                // Check health threshold for speed effect
                boolean isT6 = mob.getTier() >= 6;
                int threshold = isT6 ? FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T6 : FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T5;

                if (health < threshold) {
                    // Below threshold: gains speed
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 0), true);
                } else {
                    // Above threshold: stays slow
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0), true);
                }

                // Slow nearby players (legacy: within 8 blocks, level 1 slow)
                List<Player> nearbyPlayers = getNearbyPlayers(entity, 8.0);
                for (Player player : nearbyPlayers) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 1), true);
                }
            } else {
                // Regular elites: Complete immobilization (legacy exact)
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 10, true, false));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 127, true, false));
            }
        } catch (Exception e) {
            logger.warning("[CritManager] Failed to apply elite immobilization: " + e.getMessage());
        }
    }

    /**
     * Show countdown warning effects - legacy style (ELITE ONLY)
     */
    private void showCountdownWarningEffects(LivingEntity entity, CritState state) {
        try {
            // Elite effects: Large explosions (legacy style)
            entity.getWorld().spawnParticle(Particle.EXPLOSION,
                    entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1f);
        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    /**
     * Handle countdown step effects (4→3→2→1) - ELITE ONLY
     */
    private void handleCountdownStep(LivingEntity entity, CritState state) {
        try {
            // Elite: Creeper priming sound at 4.0f pitch (legacy exact)
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);
            // Large explosion particles (40 count, 0.3f spread - legacy exact)
            entity.getWorld().spawnParticle(Particle.EXPLOSION,
                    entity.getLocation().add(0, 1, 0), 40, 0.3, 0.3, 0.3, 0.3f);
        } catch (Exception e) {
            // Silent fail
        }
    }

    /**
     * Handle countdown finish (countdown = 0) - ELITE ONLY (whirlwind explosion)
     */
    private void handleCountdownFinish(CustomMob mob, CritState state) {
        LivingEntity entity = mob.getEntity();

        try {
            // Elite: Execute whirlwind explosion then REMOVE from crit system
            executeWhirlwindExplosion(mob, state);

            // CRITICAL: Clean up immediately after explosion
            cleanupAfterExplosion(mob, state);

            // Special Frozen Boss behavior - restart crit if low health
            handleFrozenBossSpecialBehavior(mob);

        } catch (Exception e) {
            logger.warning("[CritManager] Countdown finish error: " + e.getMessage());
        }
    }

    /**
     * Execute massive whirlwind explosion for elite mobs with proper damage application
     */
    private void executeWhirlwindExplosion(CustomMob mob, CritState state) {
        LivingEntity entity = mob.getEntity();

        try {
            // Calculate damage
            double whirlwindDamage = calculateWhirlwindDamage(mob);

            // Get targets in range
            List<Player> targets = getNearbyPlayers(entity, WHIRLWIND_AOE_RANGE);

            // Apply damage and knockback
            int playersHit = 0;
            for (Player player : targets) {
                if (applyWhirlwindDamageAndKnockback(player, entity, whirlwindDamage)) {
                    playersHit++;
                }
            }

            // Play massive explosion effects (legacy exact)
            playWhirlwindExplosionEffects(entity);

            totalExplosionsExecuted.incrementAndGet();

            logger.info(String.format("[CritManager] %s (ID: %s) whirlwind explosion hit %d players for %.1f damage",
                    mob.getType().getId(), mob.getUniqueMobId(), playersHit, whirlwindDamage));

        } catch (Exception e) {
            logger.warning("[CritManager] Whirlwind explosion failed: " + e.getMessage());
        }
    }

    /**
     * Clean up after explosion with simplified coordination
     */
    private void cleanupAfterExplosion(CustomMob mob, CritState state) {
        try {
            LivingEntity entity = mob.getEntity();

            // Remove all immobilization effects
            if (entity.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                entity.removePotionEffect(PotionEffectType.SLOWNESS);

                // Reapply normal slow for staff users (legacy behavior)
                if (entity.getEquipment() != null &&
                        entity.getEquipment().getItemInMainHand() != null &&
                        entity.getEquipment().getItemInMainHand().getType().name().contains("_HOE")) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 3), true);
                }
            }
            if (entity.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
                entity.removePotionEffect(PotionEffectType.JUMP_BOOST);
            }
            if (entity.hasPotionEffect(PotionEffectType.GLOWING)) {
                entity.removePotionEffect(PotionEffectType.GLOWING);
            }

            // Trigger display update to reflect normal state
            triggerMobDisplayUpdate(mob);

            // Reapply normal elite effects after small delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (entity.isValid() && !entity.isDead()) {
                    // Reapply normal elite speed
                    if (mob.isElite() && !MobUtils.isFrozenBoss(entity)) {
                        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0), true);
                    }

                    // Final display update after effects are restored
                    triggerMobDisplayUpdate(mob);
                }
            }, 20L);

        } catch (Exception e) {
            logger.warning("[CritManager] Cleanup after explosion failed: " + e.getMessage());
        }
    }

    /**
     * Handle Frozen Boss special behavior - restart crit cycle if low health
     */
    private void handleFrozenBossSpecialBehavior(CustomMob mob) {
        try {
            if (!MobUtils.isFrozenBoss(mob.getEntity())) {
                return;
            }

            double health = mob.getEntity().getHealth();
            boolean isT6 = mob.getTier() >= 6;
            int threshold = isT6 ? FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T6 : FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T5;

            if (health < threshold) {
                // Start NEW 3-step countdown immediately (legacy behavior)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (mob.isValid() && mob.getEntity().getHealth() < threshold) {
                        CritState restartState = new CritState(mob, true, true); // 3-step restart
                        activeCrits.put(mob.getEntity().getUniqueId(), restartState);

                        // Trigger display update for new crit state
                        triggerMobDisplayUpdate(mob);

                        logger.info("[CritManager] Frozen Boss low health - restarting 3-step crit cycle (ID: " + mob.getUniqueMobId() + ")");
                    }
                }, 1L); // Start next tick
            }
        } catch (Exception e) {
            logger.warning("[CritManager] Frozen Boss special behavior failed: " + e.getMessage());
        }
    }

    /**
     * Calculate whirlwind explosion damage (weapon damage × 3)
     */
    private double calculateWhirlwindDamage(CustomMob mob) {
        try {
            LivingEntity entity = mob.getEntity();
            if (entity.getEquipment() == null || entity.getEquipment().getItemInMainHand() == null) {
                return 30 + (mob.getTier() * 15); // Fallback based on tier
            }

            List<Integer> damageRange = MobUtils.getDamageRange(entity.getEquipment().getItemInMainHand());
            int min = damageRange.get(0);
            int max = damageRange.get(1);
            int baseDamage = ThreadLocalRandom.current().nextInt(max - min + 1) + min;

            return Math.max(30, baseDamage * WHIRLWIND_DAMAGE_MULTIPLIER);

        } catch (Exception e) {
            return 40 + (mob.getTier() * 15); // Safe fallback
        }
    }

    /**
     * Apply whirlwind damage and knockback to a player with proper damage application
     */
    private boolean applyWhirlwindDamageAndKnockback(Player player, LivingEntity mob, double damage) {
        try {
            // This ensures the explosion damage actually hits the player
            applyRawDamageToPlayer(player, damage, mob);

            // Calculate knockback direction
            Vector direction = player.getLocation().toVector()
                    .subtract(mob.getLocation().toVector())
                    .normalize();

            if (direction.length() < 0.1) {
                // Player at same location, random direction
                direction = new Vector(
                        ThreadLocalRandom.current().nextDouble(-1, 1),
                        0.6,
                        ThreadLocalRandom.current().nextDouble(-1, 1)
                ).normalize();
            }

            Vector knockback;
            if (MobUtils.isFrozenBoss(mob)) {
                // Frozen Boss: Pull players toward mob (legacy behavior)
                knockback = direction.multiply(-WHIRLWIND_KNOCKBACK_STRENGTH);
                knockback.setY(Math.max(knockback.getY(), 0.5));
            } else {
                // Normal elites: Push players away
                knockback = direction.multiply(WHIRLWIND_KNOCKBACK_STRENGTH);
                knockback.setY(Math.max(knockback.getY(), 0.8));
            }

            player.setVelocity(knockback);

            // Play hit effects
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.7f);
            player.getWorld().spawnParticle(Particle.CRIT,
                    player.getLocation().add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0.1);

            return true;

        } catch (Exception e) {
            logger.warning(String.format("[CritManager] Failed to apply whirlwind to %s: %s",
                    player.getName(), e.getMessage()));
            return false;
        }
    }

    /**
     * Apply raw damage directly to player to bypass CombatMechanics interference
     */
    private void applyRawDamageToPlayer(Player player, double damage, LivingEntity source) {
        try {
            // Mark the player as being damaged by a whirlwind explosion to bypass certain protections
            player.setMetadata("whirlwindExplosionDamage", new FixedMetadataValue(plugin, damage));
            player.setMetadata("whirlwindExplosionSource", new FixedMetadataValue(plugin, source.getUniqueId().toString()));

            // Schedule damage application on next tick to ensure metadata is set
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Apply the damage directly
                    double newHealth = Math.max(0, player.getHealth() - damage);
                    player.setHealth(newHealth);

                    // Remove metadata after damage is applied
                    player.removeMetadata("whirlwindExplosionDamage", plugin);
                    player.removeMetadata("whirlwindExplosionSource", plugin);

                    // Play hurt effects
                    player.playEffect(org.bukkit.EntityEffect.HURT);

                } catch (Exception e) {
                    logger.warning("[CritManager] Error applying raw damage: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            logger.warning("[CritManager] Error setting up raw damage: " + e.getMessage());
            // Fallback to normal damage
            player.damage(damage, source);
        }
    }

    /**
     * Play massive whirlwind explosion effects - legacy exact
     */
    private void playWhirlwindExplosionEffects(LivingEntity entity) {
        try {
            Location loc = entity.getLocation();

            // Explosion sound at 0.5f pitch (legacy exact)
            entity.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);

            // Huge explosion particles (40 count, 1.0f spread - legacy exact)
            entity.getWorld().spawnParticle(Particle.EXPLOSION,
                    loc.clone().add(0, 1, 0), 40, 1.0, 1.0, 1.0, 1.0f);

            // Additional visual effects
            entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                    loc.clone().add(0, 1, 0), 20, 3.0, 1.0, 3.0, 0.3);

        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    /**
     * Updated: Handle a mob's attack, applying crit damage if applicable
     * Now uses 2x damage for normal mobs and proper sound effects
     * Returns: CritResult with original damage, final damage, and crit info
     */
    public CritResult handleCritAttack(CustomMob attacker, Player victim, double originalDamage) {
        if (attacker == null || victim == null) {
            return new CritResult(originalDamage, false, 1.0);
        }

        UUID uuid = attacker.getEntity().getUniqueId();
        CritState state = activeCrits.get(uuid);

        // Not in crit state or countdown not finished
        if (state == null || state.getCountdown() > 0) {
            return new CritResult(originalDamage, false, 1.0);
        }

        // CRIT HIT! The mob is "charged" (countdown = 0)
        double multiplier = state.isElite() ? ELITE_CRIT_DAMAGE_MULTIPLIER : NORMAL_CRIT_DAMAGE_MULTIPLIER;
        double critDamage = originalDamage * multiplier;

        // Different sounds for different mob types
        if (state.isElite()) {
            // Elite explosion sound (they shouldn't normally get here due to whirlwind, but safety)
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        } else {
            // Updated: Normal mob boom sound (as requested)
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            victim.getWorld().spawnParticle(Particle.EXPLOSION,
                    victim.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
        }

        logger.info(String.format("[CritManager] %s (ID: %s) dealt crit damage: %.1f -> %.1f (%.1fx)",
                attacker.getType().getId(), attacker.getUniqueMobId(), originalDamage, critDamage, multiplier));

        // All mobs consume their crit state on use
        removeCrit(uuid);

        return new CritResult(critDamage, true, multiplier);
    }

    /**
     * Result class for crit attack information
     */
    public static class CritResult {
        private final double damage;
        private final boolean isCritical;
        private final double multiplier;

        public CritResult(double damage, boolean isCritical, double multiplier) {
            this.damage = damage;
            this.isCritical = isCritical;
            this.multiplier = multiplier;
        }

        public double getDamage() { return damage; }
        public boolean isCritical() { return isCritical; }
        public double getMultiplier() { return multiplier; }
    }

    /**
     * Check if a mob is currently "charged" and ready to deal crit damage.
     */
    public boolean isCritCharged(UUID uuid) {
        CritState state = activeCrits.get(uuid);
        return state != null && state.getCountdown() <= 0;
    }

    /**
     * Check if a mob is in any crit state (including countdown)
     */
    public boolean isInCritState(UUID uuid) {
        return activeCrits.containsKey(uuid);
    }

    /**
     * Get the current countdown value for a mob in crit state
     */
    public int getCritCountdown(UUID uuid) {
        CritState state = activeCrits.get(uuid);
        return state != null ? state.getCountdown() : -1;
    }

    /**
     * Public method to manually remove a crit state with simplified cleanup
     */
    public void removeCrit(UUID uuid) {
        CritState removedState = activeCrits.remove(uuid);
        if (removedState != null) {
            cleanupCritState(uuid, removedState);

            // Trigger display update to reflect normal state
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity) {
                CustomMob mob = MobManager.getInstance().getCustomMob((LivingEntity) entity);
                if (mob != null) {
                    triggerMobDisplayUpdate(mob);
                }
            }
        }
    }

    /**
     * Clean up crit state and effects with simplified coordination
     */
    private void cleanupCritState(UUID uuid, CritState state) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof LivingEntity livingEntity) {
            try {
                // Remove metadata
                if (livingEntity.hasMetadata("criticalState")) {
                    livingEntity.removeMetadata("criticalState", plugin);
                }

                // Clean up effects for elites only (normal mobs don't get immobilized)
                if (state.isElite()) {
                    if (livingEntity.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                        livingEntity.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                    if (livingEntity.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
                        livingEntity.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    }
                }

                if (livingEntity.hasPotionEffect(PotionEffectType.GLOWING)) {
                    livingEntity.removePotionEffect(PotionEffectType.GLOWING);
                }

                // Trigger display update after cleanup
                CustomMob mob = MobManager.getInstance().getCustomMob(livingEntity);
                if (mob != null) {
                    triggerMobDisplayUpdate(mob);
                }

            } catch (Exception e) {
                // Silent cleanup
            }
        }
    }

    /**
     * Get nearby players for whirlwind effects
     */
    private List<Player> getNearbyPlayers(LivingEntity entity, double radius) {
        try {
            return entity.getNearbyEntities(radius, radius, radius).stream()
                    .filter(e -> e instanceof Player)
                    .map(e -> (Player) e)
                    .filter(player -> player.isOnline() && !player.isDead())
                    .filter(player -> player.getGameMode() != org.bukkit.GameMode.CREATIVE)
                    .filter(player -> player.getGameMode() != org.bukkit.GameMode.SPECTATOR)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Get comprehensive statistics about active crits
     */
    public String getStats() {
        int totalActive = activeCrits.size();
        long eliteCount = activeCrits.values().stream().filter(CritState::isElite).count();
        long chargedCount = activeCrits.values().stream().filter(s -> s.getCountdown() <= 0).count();
        long normalChargedCount = activeCrits.values().stream()
                .filter(s -> !s.isElite() && s.getCountdown() <= 0).count();

        return String.format("Active Crits: %d (Elite: %d, Normal Charged: %d, Total Charged: %d) | Total Initiated: %d, Explosions: %d",
                totalActive, eliteCount, normalChargedCount, chargedCount,
                totalCritsInitiated.get(), totalExplosionsExecuted.get());
    }

    /**
     * Force cleanup of all crit states with simplified coordination
     */
    public void cleanup() {
        try {
            isShuttingDown.set(true);

            // Clean up all active crit states
            for (Map.Entry<UUID, CritState> entry : activeCrits.entrySet()) {
                cleanupCritState(entry.getKey(), entry.getValue());
            }

            activeCrits.clear();

            logger.info("[CritManager]: Cleaned up all crit states with instant normal mob charging system");
            logger.info(String.format("[CritManager] Final stats - Crits: %d, Explosions: %d",
                    totalCritsInitiated.get(), totalExplosionsExecuted.get()));
        } catch (Exception e) {
            logger.warning("[CritManager] Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Get the damage multiplier for a mob type
     */
    public double getDamageMultiplier(boolean isElite) {
        return isElite ? ELITE_CRIT_DAMAGE_MULTIPLIER : NORMAL_CRIT_DAMAGE_MULTIPLIER;
    }

    /**
     * Get detailed crit information for debugging
     */
    public String getDetailedCritInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Updated CritManager Details ===\n");
        info.append("Active Crits: ").append(activeCrits.size()).append("\n");
        info.append("Total Initiated: ").append(totalCritsInitiated.get()).append("\n");
        info.append("Total Explosions: ").append(totalExplosionsExecuted.get()).append("\n");
        info.append("Is Shutting Down: ").append(isShuttingDown.get()).append("\n");
        info.append("Normal Crit Multiplier: ").append(NORMAL_CRIT_DAMAGE_MULTIPLIER).append("x\n");
        info.append("Elite Crit Multiplier: ").append(ELITE_CRIT_DAMAGE_MULTIPLIER).append("x\n");

        if (!activeCrits.isEmpty()) {
            info.append("\nActive Crit Details:\n");
            for (Map.Entry<UUID, CritState> entry : activeCrits.entrySet()) {
                CritState state = entry.getValue();
                info.append(String.format("  ID: %s | Mob: %s | Countdown: %d | Elite: %s | InstantCharge: %s\n",
                        entry.getKey().toString().substring(0, 8),
                        state.getMobUniqueId() != null ? state.getMobUniqueId() : "N/A",
                        state.getCountdown(),
                        state.isElite(),
                        state.isInstantCharge()));
            }
        }

        return info.toString();
    }
}