package com.rednetty.server.mechanics.mobs.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.mobs.utils.MobUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Enhanced extension of CustomMob for elite mobs with proper critical attack sequence
 * FIXED: Proper priming phase → countdown → explosion sequence
 */
public class EliteMob extends CustomMob {

    // ================ CONSTANTS ================

    private static final int CRITICAL_PRIMING_DURATION = 100; // 5 seconds of priming/countdown
    private static final double NEARBY_PLAYER_RADIUS = 6.0;
    private static final double ATTACK_RADIUS = 5.0;
    private static final long MIN_CRITICAL_COOLDOWN = 30000; // 30 seconds minimum
    private static final int MAX_CRITICALS_PER_MINUTE = 2; // Hard limit

    // ================ ELITE STATE ================

    private long lastCriticalAttack = 0;
    private int criticalAttackCount = 0;
    private int criticalAttacksThisMinute = 0;
    private long lastMinuteReset = System.currentTimeMillis();

    // ================ VISUAL EFFECTS TIMING ================

    private static final int[] WARNING_TICKS = {80, 60, 40, 20, 10}; // 4s, 3s, 2s, 1s, 0.5s warnings

    /**
     * Create a new elite mob with enhanced capabilities
     *
     * @param type Mob type configuration
     * @param tier Mob tier (1-6)
     */
    public EliteMob(MobType type, int tier) {
        super(type, tier, true);
    }

    // ================ CRITICAL STATE OVERRIDE ================

    @Override
    public void setCriticalState(int duration) {
        // FIXED: Strict conditions but allow proper priming sequence
        if (!canEnterCriticalState()) {
            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("§6[EliteMob] §7%s critical state blocked - conditions not met", type.getId()));
            }
            return;
        }

        // FIXED: Use proper priming duration, ignore passed duration
        super.setCriticalState(CRITICAL_PRIMING_DURATION);

        try {
            applyPrimingEffects();
            playInitialWarning();
            resetCriticalMinuteTracking();

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("§6[EliteMob] §7%s entering PRIMING phase (duration: %d ticks)",
                        type.getId(), CRITICAL_PRIMING_DURATION));
            }

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Critical state setup failed: %s", e.getMessage()));
            forceResetCriticalState();
        }
    }

    private boolean canEnterCriticalState() {
        if (!isValid() || inCriticalState) {
            return false;
        }

        // Check cooldown
        long timeSinceLastCrit = System.currentTimeMillis() - lastCriticalAttack;
        if (timeSinceLastCrit < MIN_CRITICAL_COOLDOWN) {
            return false;
        }

        // Check rate limiting
        resetCriticalMinuteTracking();
        if (criticalAttacksThisMinute >= MAX_CRITICALS_PER_MINUTE) {
            return false;
        }

        // Must have nearby players to justify critical
        List<Player> nearbyPlayers = getNearbyPlayers(NEARBY_PLAYER_RADIUS);
        if (nearbyPlayers.isEmpty()) {
            return false;
        }

        // Health requirement - only crit when above 25% health to prevent death spam
        double healthPercent = entity.getHealth() / entity.getMaxHealth();
        if (healthPercent < 0.25) {
            return false;
        }

        return true;
    }

    private void resetCriticalMinuteTracking() {
        long now = System.currentTimeMillis();
        if (now - lastMinuteReset > 60000) { // Reset counter every minute
            criticalAttacksThisMinute = 0;
            lastMinuteReset = now;
        }
    }

    private void applyPrimingEffects() {
        // FIXED: Apply immobilization for the ENTIRE priming duration
        if (MobUtils.isFrozenBoss(entity)) {
            // Frozen boss slows nearby players instead of itself
            getNearbyPlayers(NEARBY_PLAYER_RADIUS).forEach(player -> {
                try {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, CRITICAL_PRIMING_DURATION + 20, 0), true);
                } catch (Exception e) {
                    // Silent fail
                }
            });
        } else {
            // Standard immobilization for the full duration
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, CRITICAL_PRIMING_DURATION + 20, 10), true);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, CRITICAL_PRIMING_DURATION + 20, 128), true);
        }

        // Add glowing effect during priming
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, CRITICAL_PRIMING_DURATION + 20, 0), true);
    }

    private void playInitialWarning() {
        try {
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 0.8f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    3, 0.3, 0.3, 0.3, 0.1f);
        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    // ================ CRITICAL STATE PROCESSING ================

    @Override
    public boolean processCriticalStateTick() {
        if (!inCriticalState || !isValid()) {
            return false;
        }

        try {
            // FIXED: Proper countdown processing - countdown goes from 100 to 0
            criticalStateDuration--;

            // Always show health bar during priming
            updateHealthBar();

            // Play warning effects at specific countdown points
            for (int warningTick : WARNING_TICKS) {
                if (criticalStateDuration == warningTick) {
                    playCountdownWarning(warningTick);
                    break;
                }
            }

            // Add priming particles every few ticks
            if (criticalStateDuration % 5 == 0) {
                showPrimingParticles();
            }

            // FIXED: Only execute attack when countdown reaches ZERO
            if (criticalStateDuration <= 0) {
                executeExplosion();
                completeCriticalState();
                return false; // Critical sequence is complete
            }

            return true; // Continue countdown
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Critical tick processing failed: %s", e.getMessage()));
            forceResetCriticalState();
            return false;
        }
    }

    private void playCountdownWarning(int ticksRemaining) {
        try {
            // Calculate seconds remaining for audio cues
            double secondsRemaining = ticksRemaining / 20.0;

            if (ticksRemaining >= 60) {
                // Early warnings - low pitch
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.5f);
            } else if (ticksRemaining >= 20) {
                // Mid warnings - medium pitch
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            } else {
                // Final warnings - high pitch, more urgent
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, 2.0f);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.5f);
            }

            // Visual warning particles
            entity.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    5, 0.4, 0.4, 0.4, 0.1);

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("§6[EliteMob] §7Countdown warning: %.1f seconds remaining", secondsRemaining));
            }

        } catch (Exception e) {
            // Silent fail for warning effects
        }
    }

    private void showPrimingParticles() {
        try {
            // Different particles based on time remaining
            if (criticalStateDuration > 50) {
                // Early priming - witch particles
                entity.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH,
                        entity.getLocation().clone().add(0.0, 1.0, 0.0),
                        3, 0.3, 0.3, 0.3, 0.05);
            } else {
                // Late priming - more intense effects
                entity.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                        entity.getLocation().clone().add(0.0, 0.5, 0.0),
                        2, 0.2, 0.2, 0.2, 0.02);

                if (criticalStateDuration <= 20) {
                    // Final 1 second - very intense
                    entity.getWorld().spawnParticle(org.bukkit.Particle.LAVA,
                            entity.getLocation().clone().add(0.0, 0.5, 0.0),
                            1, 0.1, 0.1, 0.1, 0.01);
                }
            }
        } catch (Exception e) {
            // Silent fail for particles
        }
    }

    private void executeExplosion() {
        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("§6[EliteMob] §7%s EXPLODING after priming phase!", type.getId()));
        }

        // This is where the actual attack happens
        executeCriticalAttack();
    }

    private void completeCriticalState() {
        // FIXED: Proper state cleanup after explosion
        inCriticalState = false;
        criticalStateDuration = 0;

        // Clean up critical effects
        cleanupCriticalEffects();
    }

    // ================ CRITICAL ATTACK EXECUTION ================

    /**
     * Execute the elite critical attack - this is the EXPLOSION after priming
     */
    public void executeCriticalAttack() {
        if (!isValid()) {
            return;
        }

        try {
            // Record attack metrics first
            recordCriticalAttack();

            // Calculate damage
            int damage = calculateCriticalDamage();

            // Get valid targets
            List<Player> targets = getValidTargets();

            if (targets.isEmpty()) {
                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info("§6[EliteMob] §7Critical EXPLOSION - no valid targets");
                }
                playAttackEffects(); // Still show explosion even with no targets
                return;
            }

            // Execute attack on all targets
            int playersHit = 0;
            for (Player player : targets) {
                if (executeAttackOnPlayer(player, damage)) {
                    playersHit++;
                }
            }

            // Play explosion effects
            playAttackEffects();

            // Log results
            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("§6[EliteMob] §7Critical EXPLOSION hit §e%d §7players for §c%d §7damage each",
                        playersHit, damage));
            }

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Critical attack execution failed: %s", e.getMessage()));
        }
    }

    private void recordCriticalAttack() {
        lastCriticalAttack = System.currentTimeMillis();
        criticalAttackCount++;
        criticalAttacksThisMinute++;

        // Clean up metadata
        if (entity.hasMetadata("criticalState")) {
            entity.removeMetadata("criticalState", YakRealms.getInstance());
        }
    }

    private int calculateCriticalDamage() {
        try {
            // Get base damage from weapon
            List<Integer> damageRange = MobUtils.getDamageRange(entity.getEquipment().getItemInMainHand());
            int min = damageRange.get(0);
            int max = damageRange.get(1);
            int baseDamage = ThreadLocalRandom.current().nextInt(max - min + 1) + min;

            // Apply tier-based multiplier
            double multiplier = 3.0 + (tier * 0.5); // 3.0x to 6.0x based on tier

            return Math.max(15, (int)(baseDamage * multiplier)); // Minimum 15 damage

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Damage calculation failed: %s", e.getMessage()));
            return 30 + (tier * 15); // Fallback: 30-120 damage based on tier
        }
    }

    private List<Player> getValidTargets() {
        return getNearbyPlayers(ATTACK_RADIUS).stream()
                .filter(player -> player.isOnline() && !player.isDead())
                .filter(player -> player.getGameMode() != org.bukkit.GameMode.CREATIVE)
                .filter(player -> player.getGameMode() != org.bukkit.GameMode.SPECTATOR)
                .collect(Collectors.toList());
    }

    private boolean executeAttackOnPlayer(Player player, int damage) {
        try {
            // Apply damage
            player.damage(damage, entity);

            // Apply knockback
            applyKnockbackToPlayer(player);

            // Play hit effects
            playPlayerHitEffects(player);

            return true;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Failed to attack player %s: %s",
                    player.getName(), e.getMessage()));
            return false;
        }
    }

    private void applyKnockbackToPlayer(Player player) {
        try {
            Vector direction = player.getLocation().toVector()
                    .subtract(entity.getLocation().toVector())
                    .normalize();

            // Handle edge case where player is at exact same location
            if (direction.length() < 0.1) {
                direction = new Vector(
                        ThreadLocalRandom.current().nextDouble(-1, 1),
                        0.4,
                        ThreadLocalRandom.current().nextDouble(-1, 1)
                ).normalize();
            }

            // Stronger knockback for explosion
            double strength = MobUtils.isFrozenBoss(entity) ? -2.5 : 2.5; // Pull vs push
            Vector knockback = direction.multiply(strength);

            // Ensure good Y component for lift
            knockback.setY(Math.max(knockback.getY(), 0.4));

            player.setVelocity(knockback);
        } catch (Exception e) {
            // Silent fail for knockback
        }
    }

    private void playPlayerHitEffects(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.7f);
            player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.1);
        } catch (Exception e) {
            // Silent fail
        }
    }

    private void playAttackEffects() {
        try {
            Location loc = entity.getLocation();

            // MASSIVE explosion effect - this is the payoff after priming
            entity.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
            entity.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);

            // Large explosion particles
            entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE,
                    loc.clone().add(0.0, 1.0, 0.0), 15, 1.0, 1.0, 1.0, 0.2f);

            // Additional impact effects
            entity.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                    loc.clone().add(0.0, 0.5, 0.0), 50, 1.5, 0.5, 1.5, 0.1);

            // Type-specific effects
            applyTypeSpecificEffects(loc);

        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    private void applyTypeSpecificEffects(Location loc) {
        try {
            if (MobUtils.isFrozenBoss(entity)) {
                // Massive ice explosion
                entity.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                        loc.clone().add(0.0, 1.0, 0.0), 30, 2.0, 2.0, 2.0, 0.2);
                entity.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);

            } else if (type == MobType.WARDEN) {
                // Dark explosion
                entity.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                        loc.clone().add(0.0, 1.0, 0.0), 20, 1.0, 1.0, 1.0, 0.1);
                entity.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);

            } else if (tier >= 5) {
                // High tier magical explosion
                entity.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                        loc.clone().add(0.0, 1.0, 0.0), 40, 2.0, 2.0, 2.0, 0.2);
                entity.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                        loc.clone().add(0.0, 1.0, 0.0), 20, 1.0, 1.0, 1.0, 0.1);
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    // ================ STATE CLEANUP ================

    private void cleanupCriticalEffects() {
        try {
            // Remove all critical-state effects
            if (entity.hasPotionEffect(PotionEffectType.SLOW)) {
                entity.removePotionEffect(PotionEffectType.SLOW);
            }
            if (entity.hasPotionEffect(PotionEffectType.JUMP)) {
                entity.removePotionEffect(PotionEffectType.JUMP);
            }
            if (entity.hasPotionEffect(PotionEffectType.GLOWING)) {
                entity.removePotionEffect(PotionEffectType.GLOWING);
            }

            // Reapply normal mob effects after a brief delay
            org.bukkit.Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (isValid()) {
                    applyTypeProperties();
                }
            }, 5L);

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Effect cleanup failed: %s", e.getMessage()));
        }
    }

    private void forceResetCriticalState() {
        inCriticalState = false;
        criticalStateDuration = 0;

        try {
            cleanupCriticalEffects();
        } catch (Exception e) {
            // Silent fail
        }
    }

    // ================ DAMAGE PROCESSING OVERRIDE ================

    @Override
    public double damage(double damage) {
        if (!isValid()) {
            return 0;
        }

        try {
            double finalDamage = super.damage(damage);

            // Only check for special triggers if not in critical state
            if (!inCriticalState) {
                checkSpecialCriticalTriggers();
            }

            return finalDamage;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Damage processing failed: %s", e.getMessage()));
            return 0;
        }
    }

    private void checkSpecialCriticalTriggers() {
        try {
            // Very conservative special triggers
            if (MobUtils.isFrozenBoss(entity)) {
                double healthPercent = entity.getHealth() / entity.getMaxHealth();

                // Only trigger at very low health (10%) and with proper cooldown
                if (healthPercent < 0.1 && canEnterCriticalState()) {
                    if (YakRealms.getInstance().isDebugMode()) {
                        LOGGER.info("§6[EliteMob] §7Triggering emergency critical for frozen boss");
                    }
                    setCriticalState(CRITICAL_PRIMING_DURATION); // Full priming duration
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    // ================ ENHANCED CRITICAL ROLL ================

    /**
     * FIXED: Conservative critical hit chance calculation
     */
    public boolean rollForCritical() {
        // Strict eligibility check
        if (!canEnterCriticalState()) {
            return false;
        }

        try {
            // Conservative critical chances
            int critChance = calculateConservativeCriticalChance();

            // Additional restrictions
            if (MobUtils.isGolemBoss(entity) && MobUtils.getMetadataInt(entity, "stage", 0) == 3) {
                return false; // Berserker immunity
            }

            // Must be in active combat (recently damaged)
            long timeSinceLastDamage = System.currentTimeMillis() - lastDamageTime;
            if (timeSinceLastDamage > 15000) { // 15 seconds
                return false;
            }

            // Roll for critical
            Random random = new Random();
            int roll = random.nextInt(1000) + 1; // 0.1% to 1.5% chances

            boolean success = roll <= critChance;

            if (success) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.5f, 1.0f);

                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info(String.format("§6[EliteMob] §7Critical roll success: %d <= %d (%.1f%%) - STARTING PRIMING",
                            roll, critChance, (critChance / 10.0)));
                }
            }

            return success;

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Critical roll failed: %s", e.getMessage()));
            return false;
        }
    }

    private int calculateConservativeCriticalChance() {
        // Per 1000 chances (0.1% to 1.5%)
        switch (tier) {
            case 1: return 1;   // 0.1%
            case 2: return 2;   // 0.2%
            case 3: return 4;   // 0.4%
            case 4: return 7;   // 0.7%
            case 5: return 10;  // 1.0%
            case 6: return 15;  // 1.5%
            default: return 1;
        }
    }

    // ================ SPAWN ENHANCEMENTS ================

    @Override
    public boolean spawn(Location location) {
        boolean success = super.spawn(location);

        if (success && entity != null) {
            applyEliteEnhancements();
        }

        return success;
    }

    private void applyEliteEnhancements() {
        try {
            applyEliteMovementEffects();
            enhanceEliteEquipment();
            applyEliteAppearance();
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Elite enhancements failed: %s", e.getMessage()));
        }
    }

    private void applyEliteMovementEffects() {
        try {
            if (!MobUtils.isFrozenBoss(entity)) {
                int speedLevel = Math.min(tier / 3, 1); // Conservative speed boost
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedLevel));
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    private void enhanceEliteEquipment() {
        try {
            if (entity.getEquipment() == null) return;

            org.bukkit.inventory.ItemStack weapon = entity.getEquipment().getItemInMainHand();
            if (weapon != null && weapon.getType() != org.bukkit.Material.AIR) {
                if (!weapon.hasItemMeta() || (weapon.hasItemMeta() && !weapon.getItemMeta().hasEnchants())) {
                    weapon.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS, 1);
                    entity.getEquipment().setItemInMainHand(weapon);
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    private void applyEliteAppearance() {
        try {
            if (entity.getCustomName() != null) {
                String name = entity.getCustomName();
                String eliteName = org.bukkit.ChatColor.LIGHT_PURPLE + "" +
                        org.bukkit.ChatColor.BOLD +
                        org.bukkit.ChatColor.stripColor(name);

                if (!name.startsWith(org.bukkit.ChatColor.LIGHT_PURPLE.toString())) {
                    entity.setCustomName(eliteName);
                    currentDisplayName = eliteName;
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    // ================ NAME VISIBILITY ================

    @Override
    public void updateNameVisibility() {
        if (!isValid()) return;

        try {
            long now = System.currentTimeMillis();
            boolean recentlyDamaged = (now - lastDamageTime) < NAME_VISIBILITY_TIMEOUT;
            boolean shouldShowHealthBar = recentlyDamaged || inCriticalState;

            if (shouldShowHealthBar) {
                if (!isShowingHealthBar()) {
                    updateHealthBar();
                }
                nameVisible = true;
            } else {
                if (isShowingHealthBar() || nameVisible) {
                    restoreEliteName();
                    nameVisible = false;
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    private void restoreEliteName() {
        if (!isValid() || inCriticalState) return;

        try {
            String nameToRestore = getEliteNameToRestore();
            if (nameToRestore != null && !nameToRestore.isEmpty()) {
                entity.setCustomName(nameToRestore);
                entity.setCustomNameVisible(true);
                currentDisplayName = nameToRestore;
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    private String getEliteNameToRestore() {
        // Simplified name restoration
        String formattedOriginal = getFormattedOriginalName();
        if (formattedOriginal != null && !formattedOriginal.isEmpty() && !isHealthBar(formattedOriginal)) {
            return ensureEliteFormatting(formattedOriginal);
        }

        String originalName = getOriginalName();
        if (originalName != null && !originalName.isEmpty()) {
            return generateEliteFormattedName(originalName);
        }

        return generateEliteDefaultName();
    }

    private boolean isHealthBar(String name) {
        if (name == null) return false;
        return name.contains("|") && (name.contains("§a") || name.contains("§c") || name.contains("§e"));
    }

    private String ensureEliteFormatting(String name) {
        if (name == null || name.isEmpty()) {
            return generateEliteDefaultName();
        }

        if (name.contains(org.bukkit.ChatColor.BOLD.toString())) {
            return name;
        }

        String cleanName = org.bukkit.ChatColor.stripColor(name);
        return generateEliteFormattedName(cleanName);
    }

    private String generateEliteFormattedName(String baseName) {
        if (baseName == null || baseName.isEmpty()) {
            return generateEliteDefaultName();
        }

        String cleanName = org.bukkit.ChatColor.stripColor(baseName);
        org.bukkit.ChatColor tierColor = getTierColor(tier);
        return tierColor.toString() + org.bukkit.ChatColor.BOLD + cleanName;
    }

    private String generateEliteDefaultName() {
        String tierName = type.getTierSpecificName(tier);
        org.bukkit.ChatColor tierColor = getTierColor(tier);
        return tierColor.toString() + org.bukkit.ChatColor.BOLD + tierName;
    }

    private org.bukkit.ChatColor getTierColor(int tier) {
        switch (tier) {
            case 1: return org.bukkit.ChatColor.WHITE;
            case 2: return org.bukkit.ChatColor.GREEN;
            case 3: return org.bukkit.ChatColor.AQUA;
            case 4: return org.bukkit.ChatColor.LIGHT_PURPLE;
            case 5: return org.bukkit.ChatColor.YELLOW;
            case 6: return org.bukkit.ChatColor.BLUE;
            default: return org.bukkit.ChatColor.WHITE;
        }
    }

    // ================ UTILITY METHODS ================

    protected List<Player> getNearbyPlayers(double radius) {
        if (!isValid()) {
            return java.util.Collections.emptyList();
        }

        try {
            return entity.getNearbyEntities(radius, radius, radius).stream()
                    .filter(entity -> entity instanceof Player)
                    .map(entity -> (Player) entity)
                    .filter(player -> player.isOnline() && !player.isDead())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    public void onHitByPlayer(Player player, double damage) {
        if (!isValid()) return;

        try {
            lastDamageTime = System.currentTimeMillis();
            nameVisible = true;
            updateHealthBar();

            // Simple hit effects
            if (type == MobType.FROZEN_BOSS || type == MobType.FROZEN_ELITE) {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.3f, 1.2f);
            } else if (type == MobType.WARDEN) {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HURT, 0.2f, 1.5f);
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    // ================ STATUS METHODS ================

    public int getCriticalAttackCount() {
        return criticalAttackCount;
    }

    public long getLastCriticalAttack() {
        return lastCriticalAttack;
    }

    public boolean isInCriticalState() {
        return inCriticalState;
    }

    public long getSecondsSinceLastCritical() {
        if (lastCriticalAttack == 0) return -1;
        return (System.currentTimeMillis() - lastCriticalAttack) / 1000;
    }

    public int getCriticalAttacksThisMinute() {
        resetCriticalMinuteTracking(); // Update counter
        return criticalAttacksThisMinute;
    }

    public int getRemainingPrimingTicks() {
        return inCriticalState ? criticalStateDuration : 0;
    }

    public double getRemainingPrimingSeconds() {
        return getRemainingPrimingTicks() / 20.0;
    }

    /**
     * Admin method to force reset critical state
     */
    public void forceResetCritical() {
        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("§6[EliteMob] §7Force resetting critical state for %s", type.getId()));
        }
        forceResetCriticalState();
    }
}