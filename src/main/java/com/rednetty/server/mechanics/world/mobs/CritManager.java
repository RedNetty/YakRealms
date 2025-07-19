package com.rednetty.server.mechanics.world.mobs;

import com.rednetty.server.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.mechanics.world.mobs.core.MobType;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *  Centralized manager for ALL critical hit mechanics matching legacy behavior.
 * Key fixes:
 * - Proper 1-second countdown steps (20 ticks each)
 * - Complete state cleanup after explosions
 * - Frozen Boss special 3-step restart behavior
 * - Normal mobs consume crit state on attack
 * - Accurate timing and effect management
 * -  Elite mob explosions now properly damage players
 * - Enhanced hologram integration - critical state updates holograms instead of entity names
 */
public class CritManager {

    // ================ CONSTANTS ================
    private static final int CRIT_CHANCE_RANGE = 250; // 1-250 range per spec
    private static final int COUNTDOWN_TICK_INTERVAL = 20; // 20 ticks = 1 second per step
    private static final double NORMAL_CRIT_DAMAGE_MULTIPLIER = 3.0; // 3x for normal mobs
    private static final double ELITE_CRIT_DAMAGE_MULTIPLIER = 4.0; // 4x for elite mobs (legacy was 3x, but keeping current)
    private static final double WHIRLWIND_AOE_RANGE = 7.0; // 7x7x7 blocks
    private static final double WHIRLWIND_DAMAGE_MULTIPLIER = 3.0; // Weapon damage × 3
    private static final double WHIRLWIND_KNOCKBACK_STRENGTH = 3.0; // 3x velocity
    private static final int FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T5 = 50000; // 50k for T5
    private static final int FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T6 = 100000; // 100k for T6

    // ================ SINGLETON ================
    private static CritManager instance;
    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<UUID, CritState> activeCrits = new ConcurrentHashMap<>();

    /**
     * Internal class to hold the state of a mob currently in the crit process.
     */
    private static class CritState {
        private final UUID entityUUID;
        private final boolean isElite;
        private final int tier;
        private final MobType mobType;
        private int countdown; // Starts at 4, ticks down to 0
        private int ticksRemaining; // Exact ticks until next countdown step
        private boolean immobilized = false;
        private boolean isFrozenBossRestart = false; // For special Frozen Boss behavior

        CritState(CustomMob mob) {
            this.entityUUID = mob.getEntity().getUniqueId();
            this.isElite = mob.isElite();
            this.tier = mob.getTier();
            this.mobType = mob.getType();
            this.countdown = 4; // The crit process starts at 4
            this.ticksRemaining = COUNTDOWN_TICK_INTERVAL; // 20 ticks = 1 second
        }

        // Special constructor for Frozen Boss restart (3-step instead of 4-step)
        CritState(CustomMob mob, boolean frozenBossRestart) {
            this(mob);
            if (frozenBossRestart) {
                this.countdown = 3; // Frozen Boss restart is 3-step
                this.isFrozenBossRestart = true;
            }
        }

        // Getters
        public UUID getEntityUUID() { return entityUUID; }
        public boolean isElite() { return isElite; }
        public int getTier() { return tier; }
        public MobType getMobType() { return mobType; }
        public int getCountdown() { return countdown; }
        public boolean isImmobilized() { return immobilized; }
        public boolean isFrozenBossRestart() { return isFrozenBossRestart; }

        // State management
        public void decrementCountdown() { this.countdown--; }
        public void resetTickTimer() { this.ticksRemaining = COUNTDOWN_TICK_INTERVAL; }
        public void decrementTicks() { this.ticksRemaining--; }
        public boolean shouldDecrementCountdown() { return ticksRemaining <= 0; }
        public void setImmobilized(boolean immobilized) { this.immobilized = immobilized; }
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
            instance = new CritManager(plugin);
            instance.startMasterTask();
            instance.logger.info("§a[CritManager] §7Initialized with legacy-matching crit system and hologram integration");
        }
    }

    /**
     * Gets the singleton instance of the CritManager.
     */
    public static CritManager getInstance() {
        return instance;
    }

    /**
     * Starts the single master task that handles all active crits.
     * Now runs every tick for precise timing matching legacy system.
     */
    private void startMasterTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeCrits.isEmpty()) {
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
                    cleanupCritState(uuid);
                    iterator.remove();
                    continue;
                }

                LivingEntity livingEntity = (LivingEntity) entity;
                CustomMob mob = MobManager.getInstance().getCustomMob(livingEntity);
                if (mob == null) {
                    // Cleanup mobs not in manager
                    cleanupCritState(uuid);
                    iterator.remove();
                    continue;
                }

                // Process the crit tick
                boolean shouldRemove = processCritTick(state, mob);
                if (shouldRemove) {
                    iterator.remove();
                }
            }
        }, 0L, 1L); // Run every tick for precise timing
    }

    /**
     * Process a single crit tick for a mob - returns true if should be removed
     */
    private boolean processCritTick(CritState state, CustomMob mob) {
        LivingEntity entity = mob.getEntity();

        // Decrement tick timer
        state.decrementTicks();

        // Apply immobilization effects if not already applied
        if (!state.isImmobilized() && state.isElite()) {
            applyEliteImmobilization(mob);
            state.setImmobilized(true);
        }

        // Show continuous warning effects every few ticks
        if (state.ticksRemaining % 5 == 0) {
            showCountdownWarningEffects(entity, state);
        }

        // Check if it's time for the next major countdown step
        if (state.shouldDecrementCountdown()) {
            state.decrementCountdown();
            state.resetTickTimer();

            if (state.getCountdown() > 0) {
                // Continue countdown (4→3→2→1)
                handleCountdownStep(entity, state);

                // Update hologram to reflect critical state change
                updateMobHologramForCritState(mob);

                return false; // Continue processing
            } else {
                // Countdown reached 0 - execute final effect
                handleCountdownFinish(mob, state);
                return true; // Remove from processing
            }
        }

        return false; // Continue processing
    }

    /**
     * Update mob hologram to reflect critical state changes
     */
    private void updateMobHologramForCritState(CustomMob mob) {
        try {
            if (mob != null && mob.isValid()) {
                // Force hologram update to show current critical state
                mob.updateHologramOverlay();
            }
        } catch (Exception e) {
            logger.warning("[CritManager] Failed to update hologram for crit state: " + e.getMessage());
        }
    }

    /**
     * Attempts to trigger a crit on a mob based on tier and random roll.
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
            CritState newState = new CritState(mob);
            activeCrits.put(entityId, newState);

            // Apply initial effects
            applyInitialCritEffects(mob, newState);

            // Update hologram to show crit state (instead of updating entity name)
            updateMobHologramForCritState(mob);

            logger.info(String.format("[CritManager] %s T%d crit triggered! Roll: %d <= %d (%.1f%%)",
                    mob.getType().getId(), tier, roll, chance, (chance * 100.0 / CRIT_CHANCE_RANGE)));

            return true;
        }

        return false;
    }

    /**
     * Get correct critical chances per game design spec
     */
    private int getCritChanceByTier(int tier) {
        switch (tier) {
            case 1: return 5;   // 5/250 = 2%
            case 2: return 7;   // 7/250 = 2.8%
            case 3: return 10;  // 10/250 = 4%
            case 4: return 13;  // 13/250 = 5.2%
            case 5:
            case 6:
            default: return 20; // 20/250 = 8%
        }
    }

    /**
     * Apply initial crit effects based on mob type - matching legacy
     */
    private void applyInitialCritEffects(CustomMob mob, CritState state) {
        LivingEntity entity = mob.getEntity();

        try {
            if (state.isElite()) {
                // Elite: Creeper priming sound at 4.0f pitch (legacy exact)
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);
                // Large explosion particles (40 count, 0.3f spread - legacy exact)
                entity.getWorld().spawnParticle(Particle.EXPLOSION_LARGE,
                        entity.getLocation().add(0, 1, 0), 40, 0.3, 0.3, 0.3, 0.3f);
            } else {
                // Normal: Different sound for normal mobs
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.2f);
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
                if (entity.hasPotionEffect(PotionEffectType.SLOW)) {
                    entity.removePotionEffect(PotionEffectType.SLOW);
                }

                // Check health threshold for speed effect
                boolean isT6 = mob.getTier() >= 6;
                int threshold = isT6 ? FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T6 : FROZEN_BOSS_LOW_HEALTH_THRESHOLD_T5;

                if (health < threshold) {
                    // Below threshold: gains speed
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 0), true);
                } else {
                    // Above threshold: stays slow
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 25, 0), true);
                }

                // Slow nearby players (legacy: within 8 blocks, level 1 slow)
                List<Player> nearbyPlayers = getNearbyPlayers(entity, 8.0);
                for (Player player : nearbyPlayers) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 25, 1), true);
                }
            } else {
                // Regular elites: Complete immobilization (legacy exact)
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 10, true, false));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 127, true, false));
            }
        } catch (Exception e) {
            logger.warning("[CritManager] Failed to apply elite immobilization: " + e.getMessage());
        }
    }

    /**
     * Show countdown warning effects - legacy style
     */
    private void showCountdownWarningEffects(LivingEntity entity, CritState state) {
        try {
            if (state.isElite()) {
                // Elite effects: Large explosions (legacy style)
                entity.getWorld().spawnParticle(Particle.EXPLOSION_LARGE,
                        entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1f);
            } else {
                // Normal effects: Smaller crit particles
                entity.getWorld().spawnParticle(Particle.CRIT,
                        entity.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.05);
            }
        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    /**
     * Handle countdown step effects (4→3→2→1) - legacy matching
     */
    private void handleCountdownStep(LivingEntity entity, CritState state) {
        try {
            if (state.isElite()) {
                // Elite: Creeper priming sound at 4.0f pitch (legacy exact)
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);
                // Large explosion particles (40 count, 0.3f spread - legacy exact)
                entity.getWorld().spawnParticle(Particle.EXPLOSION_LARGE,
                        entity.getLocation().add(0, 1, 0), 40, 0.3, 0.3, 0.3, 0.3f);
            } else {
                // Normal: Different sound
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.2f);
                entity.getWorld().spawnParticle(Particle.CRIT,
                        entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    /**
     * Handle countdown finish (countdown = 0) - with proper cleanup and hologram updates
     */
    private void handleCountdownFinish(CustomMob mob, CritState state) {
        LivingEntity entity = mob.getEntity();

        try {
            if (state.isElite()) {
                // Elite: Execute whirlwind explosion then REMOVE from crit system
                executeWhirlwindExplosion(mob, state);

                // CRITICAL: Clean up immediately after explosion
                cleanupAfterExplosion(mob);

                // Special Frozen Boss behavior - restart crit if low health
                handleFrozenBossSpecialBehavior(mob);

            } else {
                // Normal: Ready for 3x damage attack, show final effects
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                entity.getWorld().spawnParticle(Particle.SPELL_WITCH,
                        entity.getLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0.1);
                // Normal mobs stay in crit state until they attack

                // Update hologram to show charged state
                updateMobHologramForCritState(mob);
            }
        } catch (Exception e) {
            logger.warning("[CritManager] Countdown finish error: " + e.getMessage());
        }
    }

    /**
     *  Execute massive whirlwind explosion for elite mobs - legacy matching with proper damage application
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

            logger.info(String.format("[CritManager] %s whirlwind explosion hit %d players for %.1f damage",
                    mob.getType().getId(), playersHit, whirlwindDamage));

        } catch (Exception e) {
            logger.warning("[CritManager] Whirlwind explosion failed: " + e.getMessage());
        }
    }

    /**
     * Clean up after explosion - CRITICAL for preventing endless ticking, now includes hologram updates
     */
    private void cleanupAfterExplosion(CustomMob mob) {
        try {
            LivingEntity entity = mob.getEntity();
            UUID entityId = entity.getUniqueId();

            // Remove all immobilization effects
            if (entity.hasPotionEffect(PotionEffectType.SLOW)) {
                entity.removePotionEffect(PotionEffectType.SLOW);

                // Reapply normal slow for staff users (legacy behavior)
                if (entity.getEquipment() != null &&
                        entity.getEquipment().getItemInMainHand() != null &&
                        entity.getEquipment().getItemInMainHand().getType().name().contains("_HOE")) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 3), true);
                }
            }
            if (entity.hasPotionEffect(PotionEffectType.JUMP)) {
                entity.removePotionEffect(PotionEffectType.JUMP);
            }
            if (entity.hasPotionEffect(PotionEffectType.GLOWING)) {
                entity.removePotionEffect(PotionEffectType.GLOWING);
            }

            // Update hologram to reflect normal state (not critical anymore)
            updateMobHologramForCritState(mob);

            // Reapply normal elite effects after small delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (entity.isValid() && !entity.isDead()) {
                    // Reapply normal elite speed
                    if (mob.isElite() && !MobUtils.isFrozenBoss(entity)) {
                        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0), true);
                    }

                    // Final hologram update after effects are restored
                    updateMobHologramForCritState(mob);
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
                        CritState restartState = new CritState(mob, true); // 3-step restart
                        activeCrits.put(mob.getEntity().getUniqueId(), restartState);

                        // Update hologram for new crit state
                        updateMobHologramForCritState(mob);

                        logger.info("[CritManager] Frozen Boss low health - restarting 3-step crit cycle");
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
     *  Apply whirlwind damage and knockback to a player - legacy matching with proper damage application
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
     *  Apply raw damage directly to player to bypass CombatMechanics interference
     */
    private void applyRawDamageToPlayer(Player player, double damage, LivingEntity source) {
        try {
            // Mark the player as being damaged by a whirlwind explosion to bypass certain protections
            player.setMetadata("whirlwindExplosionDamage", new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
            player.setMetadata("whirlwindExplosionSource", new org.bukkit.metadata.FixedMetadataValue(plugin, source.getUniqueId().toString()));

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
            entity.getWorld().spawnParticle(Particle.EXPLOSION_HUGE,
                    loc.clone().add(0, 1, 0), 40, 1.0, 1.0, 1.0, 1.0f);

            // Additional visual effects
            entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                    loc.clone().add(0, 1, 0), 20, 3.0, 1.0, 3.0, 0.3);

        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    /**
     * Handle a mob's attack, applying crit damage if applicable.
     */
    public double handleCritAttack(CustomMob attacker, Player victim, double originalDamage) {
        if (attacker == null || victim == null) {
            return originalDamage;
        }

        UUID uuid = attacker.getEntity().getUniqueId();
        CritState state = activeCrits.get(uuid);

        // Not in crit state or countdown not finished
        if (state == null || state.getCountdown() > 0) {
            return originalDamage;
        }

        // CRIT HIT! The mob is "charged" (countdown = 0)
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        double multiplier = state.isElite() ? ELITE_CRIT_DAMAGE_MULTIPLIER : NORMAL_CRIT_DAMAGE_MULTIPLIER;
        double critDamage = originalDamage * multiplier;

        logger.info(String.format("[CritManager] %s dealt crit damage: %.1f -> %.1f (%.1fx)",
                attacker.getType().getId(), originalDamage, critDamage, multiplier));

        // Normal mobs consume their crit state on use (legacy behavior)
        if (!state.isElite()) {
            removeCrit(uuid);
        }

        return critDamage;
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
     * Public method to manually remove a crit state with hologram updates.
     */
    public void removeCrit(UUID uuid) {
        CritState removedState = activeCrits.remove(uuid);
        if (removedState != null) {
            cleanupCritState(uuid);

            // Update hologram to remove critical state display
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity) {
                CustomMob mob = MobManager.getInstance().getCustomMob((LivingEntity) entity);
                if (mob != null) {
                    updateMobHologramForCritState(mob);
                }
            }
        }
    }

    /**
     * Clean up crit state and effects
     */
    private void cleanupCritState(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof LivingEntity livingEntity) {
            try {
                // Remove metadata
                if (livingEntity.hasMetadata("criticalState")) {
                    livingEntity.removeMetadata("criticalState", plugin);
                }

                // Clean up effects for elites
                if (livingEntity.hasPotionEffect(PotionEffectType.SLOW)) {
                    livingEntity.removePotionEffect(PotionEffectType.SLOW);
                }
                if (livingEntity.hasPotionEffect(PotionEffectType.JUMP)) {
                    livingEntity.removePotionEffect(PotionEffectType.JUMP);
                }
                if (livingEntity.hasPotionEffect(PotionEffectType.GLOWING)) {
                    livingEntity.removePotionEffect(PotionEffectType.GLOWING);
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
     * Get statistics about active crits
     */
    public String getStats() {
        int totalActive = activeCrits.size();
        long eliteCount = activeCrits.values().stream().filter(CritState::isElite).count();
        long chargedCount = activeCrits.values().stream().filter(s -> s.getCountdown() <= 0).count();

        return String.format("Active Crits: %d (Elite: %d, Charged: %d)",
                totalActive, eliteCount, chargedCount);
    }

    /**
     * Force cleanup of all crit states (for shutdown/reset)
     */
    public void cleanup() {
        for (UUID uuid : activeCrits.keySet()) {
            cleanupCritState(uuid);
        }
        activeCrits.clear();
        logger.info("[CritManager] Cleaned up all crit states");
    }

    /**
     * Get the damage multiplier for a mob type
     */
    public double getDamageMultiplier(boolean isElite) {
        return isElite ? ELITE_CRIT_DAMAGE_MULTIPLIER : NORMAL_CRIT_DAMAGE_MULTIPLIER;
    }
}