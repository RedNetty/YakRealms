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
 * Enhanced extension of CustomMob for elite mobs with improved name tracking and special abilities
 */
public class EliteMob extends CustomMob {

    // ================ CONSTANTS ================

    private static final int CRITICAL_DURATION = 12;
    private static final double NEARBY_PLAYER_RADIUS = 8.0;
    private static final double ATTACK_RADIUS = 7.0;
    private static final float SPIN_SPEED = 15f;

    // ================ ELITE STATE ================

    private float originalYaw = 0;
    private boolean isSpinning = false;
    private long lastCriticalAttack = 0;
    private int criticalAttackCount = 0;

    // ================ VISUAL EFFECTS TIMING ================

    private static final int[] WARNING_TICKS = {9, 6, 3}; // Countdown warnings

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
        super.setCriticalState(duration);

        if (!isValid()) return;

        try {
            initializeCriticalState();
            applyImmobilization();
            playInitialWarning();

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Critical state setup failed: %s", e.getMessage()));
        }
    }

    private void initializeCriticalState() {
        originalYaw = entity.getLocation().getYaw();
        isSpinning = true;

        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("§6[EliteMob] §7%s entering critical state", type.getId()));
        }
    }

    private void applyImmobilization() {
        if (MobUtils.isFrozenBoss(entity)) {
            handleFrozenBossImmobilization();
        } else {
            applyStandardImmobilization();
        }
    }

    private void handleFrozenBossImmobilization() {
        // Apply slowness to nearby players instead of the boss
        getNearbyPlayers(NEARBY_PLAYER_RADIUS).forEach(player ->
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 1), true)
        );
    }

    private void applyStandardImmobilization() {
        // Complete immobilization for standard elites
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 100), true);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 128), true);
    }

    private void playInitialWarning() {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
        entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                5, 0.2, 0.2, 0.2, 0.1f);
    }

    // ================ CRITICAL STATE PROCESSING ================

    @Override
    public boolean processCriticalStateTick() {
        if (!inCriticalState || !isValid()) {
            return false;
        }

        try {
            // Handle spinning animation
            if (isSpinning && criticalStateDuration > 0) {
                performSpinning();
            }

            // Process countdown
            if (criticalStateDuration > 0) {
                return processCriticalCountdown();
            }

            return false;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Critical tick processing failed: %s", e.getMessage()));
            return false;
        }
    }

    private void performSpinning() {
        try {
            Location loc = entity.getLocation();
            float newYaw = (loc.getYaw() + SPIN_SPEED) % 360;
            loc.setYaw(newYaw);
            entity.teleport(loc);
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Spinning effect failed: %s", e.getMessage()));
        }
    }

    private boolean processCriticalCountdown() {
        criticalStateDuration--;
        updateHealthBar(); // Always keep health bar visible

        // Play warning effects at specific ticks
        for (int warningTick : WARNING_TICKS) {
            if (criticalStateDuration == warningTick) {
                playWarningEffect();
                break;
            }
        }

        // Add periodic witch particles
        if (criticalStateDuration % 3 == 0) {
            showWitchParticles();
        }

        // Execute attack when countdown reaches 0
        if (criticalStateDuration == 0) {
            executeCriticalAttack();
            return true;
        }

        return false;
    }

    private void playWarningEffect() {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
        entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                5, 0.3, 0.3, 0.3, 0.1);
    }

    private void showWitchParticles() {
        entity.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                5, 0.3, 0.3, 0.3, 0.1);
    }

    // ================ CRITICAL ATTACK EXECUTION ================

    /**
     * Execute the elite critical attack with enhanced effects and mechanics
     */
    public void executeCriticalAttack() {
        if (!isValid()) return;

        try {
            prepareAttackExecution();

            int damage = calculateCriticalDamage();
            List<Player> targets = identifyTargets();

            int playersHit = executeAttackOnTargets(targets, damage);

            playAttackEffects();
            cleanupAfterAttack();

            recordAttackMetrics(playersHit, damage);

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Critical attack execution failed: %s", e.getMessage()));
        }
    }

    private void prepareAttackExecution() {
        inCriticalState = false;
        isSpinning = false;

        if (entity.hasMetadata("criticalState")) {
            entity.removeMetadata("criticalState", YakRealms.getInstance());
        }

        lastCriticalAttack = System.currentTimeMillis();
        criticalAttackCount++;
    }

    private int calculateCriticalDamage() {
        List<Integer> damageRange = MobUtils.getDamageRange(entity.getEquipment().getItemInMainHand());
        int min = damageRange.get(0);
        int max = damageRange.get(1);

        int baseDamage = ThreadLocalRandom.current().nextInt(max - min + 1) + min;

        // Apply critical multiplier with elite bonus
        int criticalMultiplier = 3;
        if (tier >= 5) criticalMultiplier = 4; // Higher tiers hit harder

        return baseDamage * criticalMultiplier;
    }

    private List<Player> identifyTargets() {
        return getNearbyPlayers(ATTACK_RADIUS);
    }

    private int executeAttackOnTargets(List<Player> targets, int damage) {
        int playersHit = 0;

        for (Player player : targets) {
            if (applyDamageToPlayer(player, damage)) {
                applyKnockbackToPlayer(player);
                playPlayerHitEffects(player);
                playersHit++;
            }
        }

        return playersHit;
    }

    private boolean applyDamageToPlayer(Player player, int damage) {
        try {
            player.damage(damage, entity);
            return true;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Failed to damage player %s: %s",
                    player.getName(), e.getMessage()));
            return false;
        }
    }

    private void applyKnockbackToPlayer(Player player) {
        try {
            Vector knockback = calculateKnockbackVector(player);
            player.setVelocity(knockback);
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Knockback failed for player %s: %s",
                    player.getName(), e.getMessage()));
        }
    }

    private Vector calculateKnockbackVector(Player player) {
        Vector direction = player.getLocation().clone().toVector()
                .subtract(entity.getLocation().toVector());

        if (direction.length() <= 0) {
            // If players are at same location, use random direction
            direction = new Vector(Math.random() - 0.5, 0.2, Math.random() - 0.5);
        }

        direction.normalize();

        // Special handling for frozen boss (reverse knockback)
        double knockbackStrength = MobUtils.isFrozenBoss(entity) ? -3.0 : 3.0;

        return direction.multiply(knockbackStrength);
    }

    private void playPlayerHitEffects(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
    }

    private void playAttackEffects() {
        Location loc = entity.getLocation();

        // Main explosion sound and effect
        entity.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE,
                loc.clone().add(0.0, 1.0, 0.0), 10, 0, 0, 0, 1.0f);

        // Additional flame effects for visual impact
        entity.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                loc.clone().add(0.0, 0.5, 0.0), 40, 1.0, 0.2, 1.0, 0.1);

        // Type-specific additional effects
        applyTypeSpecificEffects();
    }

    private void applyTypeSpecificEffects() {
        if (MobUtils.isFrozenBoss(entity)) {
            // Ice effects for frozen boss
            entity.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0), 20, 1.0, 1.0, 1.0, 0.1);
        } else if (type == MobType.WARDEN) {
            // Dark effects for warden
            entity.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0), 15, 0.5, 0.5, 0.5, 0.05);
        }
    }

    private void cleanupAfterAttack() {
        resetMobEffects();
        // FIXED: Let the enhanced name visibility system handle name restoration
        // No immediate name restoration - let the timer-based system handle it properly
    }

    private void recordAttackMetrics(int playersHit, int damage) {
        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("§6[EliteMob] §7Critical attack #%d hit §e%d §7players for §c%d §7damage",
                    criticalAttackCount, playersHit, damage));
        }
    }

    // ================ POST-ATTACK CLEANUP ================

    private void resetMobEffects() {
        try {
            // Remove immobilization effects
            removeEffect(PotionEffectType.SLOW);
            removeEffect(PotionEffectType.JUMP);
            removeEffect(PotionEffectType.GLOWING);

            // Reapply tier-specific effects
            applyTypeProperties();

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Effect reset failed: %s", e.getMessage()));
        }
    }

    private void removeEffect(PotionEffectType effectType) {
        if (entity.hasPotionEffect(effectType)) {
            entity.removePotionEffect(effectType);
        }
    }

    // ================ DAMAGE PROCESSING OVERRIDE ================

    @Override
    public double damage(double damage) {
        if (!isValid()) return 0;

        try {
            double finalDamage = super.damage(damage);

            // Check for special critical conditions
            checkSpecialCriticalTriggers();

            return finalDamage;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Damage processing failed: %s", e.getMessage()));
            return 0;
        }
    }

    private void checkSpecialCriticalTriggers() {
        // Frozen boss special critical at low health
        if (MobUtils.isFrozenBoss(entity) &&
                entity.getHealth() < (YakRealms.isT6Enabled() ? 100000 : 50000) &&
                !inCriticalState) {

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info("§6[EliteMob] §7Triggering special critical for low-health frozen boss");
            }

            setCriticalState(12); // Longer duration for special trigger
        }
    }

    // ================ SPAWN ENHANCEMENTS ================

    @Override
    public boolean spawn(Location location) {
        boolean success = super.spawn(location);

        if (success && entity != null) {
            applyEliteEnhancements();
            // FIXED: Let the parent class handle name visibility
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
        // Elite mobs get speed unless they are frozen boss
        if (!MobUtils.isFrozenBoss(entity)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        }
    }

    private void enhanceEliteEquipment() {
        if (entity.getEquipment() == null) return;

        // Enhance weapon with elite identifier
        org.bukkit.inventory.ItemStack weapon = entity.getEquipment().getItemInMainHand();
        if (weapon != null && weapon.getType() != org.bukkit.Material.AIR && !weapon.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = weapon.getItemMeta();
            if (meta != null && !meta.hasEnchants()) {
                weapon.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS, 1);
                entity.getEquipment().setItemInMainHand(weapon);
            }
        }
    }

    private void applyEliteAppearance() {
        if (entity.getCustomName() != null) {
            String name = entity.getCustomName();
            String eliteName = org.bukkit.ChatColor.LIGHT_PURPLE + "" +
                    org.bukkit.ChatColor.BOLD +
                    org.bukkit.ChatColor.stripColor(name);

            if (!name.startsWith(org.bukkit.ChatColor.LIGHT_PURPLE.toString())) {
                entity.setCustomName(eliteName);
                // FIXED: Update our tracking variables properly
                currentDisplayName = eliteName;
            }
        }
    }

    // ================ ENHANCED NAME VISIBILITY OVERRIDE ================

    @Override
    public void updateNameVisibility() {
        if (!isValid()) return;

        try {
            long now = System.currentTimeMillis();
            boolean recentlyDamaged = (now - lastDamageTime) < NAME_VISIBILITY_TIMEOUT;
            boolean shouldShowHealthBar = recentlyDamaged || inCriticalState;

            if (shouldShowHealthBar) {
                // Show health bar
                if (!isShowingHealthBar()) {
                    updateHealthBar();
                }
                nameVisible = true;
            } else {
                // Time to restore original name
                if (isShowingHealthBar() || nameVisible) {
                    restoreEliteName();
                    nameVisible = false;
                }
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Name visibility update failed: %s", e.getMessage()));
        }
    }

    /**
     * FIXED: Enhanced elite name restoration with proper tier colors
     */
    private void restoreEliteName() {
        if (!isValid() || inCriticalState) return;

        try {
            String nameToRestore = getEliteNameToRestore();

            if (nameToRestore != null && !nameToRestore.isEmpty()) {
                entity.setCustomName(nameToRestore);
                entity.setCustomNameVisible(true);
                currentDisplayName = nameToRestore;

                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info(String.format("§6[EliteMob] §7Restored elite name for %s: %s",
                            entity.getType(), nameToRestore));
                }
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Elite name restoration failed: %s", e.getMessage()));
        }
    }

    private String getEliteNameToRestore() {
        // First priority: check if we have a stored formatted original name
        String formattedOriginal = getFormattedOriginalName();
        if (formattedOriginal != null && !formattedOriginal.isEmpty() && !isHealthBar(formattedOriginal)) {
            return ensureEliteFormatting(formattedOriginal);
        }

        // Second priority: check stored original name
        String originalName = getOriginalName();
        if (originalName != null && !originalName.isEmpty()) {
            return generateEliteFormattedName(originalName);
        }

        // Third priority: metadata
        if (entity.hasMetadata("name")) {
            try {
                String metaName = entity.getMetadata("name").get(0).asString();
                if (metaName != null && !metaName.isEmpty() && !isHealthBar(metaName)) {
                    return ensureEliteFormatting(metaName);
                }
            } catch (Exception e) {
                // Fall through to next option
            }
        }

        // Fourth priority: lightning mob metadata
        if (entity.hasMetadata("LightningMob")) {
            try {
                String lightningName = entity.getMetadata("LightningMob").get(0).asString();
                if (lightningName != null && !lightningName.isEmpty()) {
                    return lightningName; // Lightning names are already properly formatted
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }

        // Last resort: generate default elite name
        return generateEliteDefaultName();
    }

    private boolean isHealthBar(String name) {
        if (name == null) return false;
        return name.contains(org.bukkit.ChatColor.GREEN + "|") ||
                name.contains(org.bukkit.ChatColor.GRAY + "|") ||
                name.contains(org.bukkit.ChatColor.LIGHT_PURPLE + "|");
    }

    private String ensureEliteFormatting(String name) {
        if (name == null || name.isEmpty()) {
            return generateEliteDefaultName();
        }

        // If already has elite formatting (bold), return as-is
        if (name.contains(org.bukkit.ChatColor.BOLD.toString())) {
            return name;
        }

        // Strip colors and re-apply with elite formatting
        String cleanName = org.bukkit.ChatColor.stripColor(name);
        return generateEliteFormattedName(cleanName);
    }

    private String generateEliteFormattedName(String baseName) {
        if (baseName == null || baseName.isEmpty()) {
            return generateEliteDefaultName();
        }

        // Strip any existing colors first
        String cleanName = org.bukkit.ChatColor.stripColor(baseName);

        // Apply tier color + bold for elite
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

    // ================ PLAYER INTERACTION ================

    /**
     * Handle special effects when hit by a player
     *
     * @param player The attacking player
     * @param damage The damage amount
     */
    public void onHitByPlayer(Player player, double damage) {
        if (!isValid()) return;

        try {
            // Update combat status
            lastDamageTime = System.currentTimeMillis();
            nameVisible = true;
            updateHealthBar();

            // Apply type-specific hit effects
            applyHitEffects(player);

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Player hit processing failed: %s", e.getMessage()));
        }
    }

    private void applyHitEffects(Player player) {
        if (type == MobType.FROZEN_BOSS || type == MobType.FROZEN_ELITE) {
            applyFrostHitEffects(player);
        } else if (type == MobType.WARDEN) {
            applyWardenHitEffects(player);
        }
    }

    private void applyFrostHitEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.2f);
        player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.05);
    }

    private void applyWardenHitEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HURT, 0.3f, 1.5f);
        player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL,
                player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.02);
    }

    // ================ UTILITY METHODS ================

    /**
     * Get all players within the specified radius
     *
     * @param radius The search radius
     * @return List of nearby players
     */
    protected List<Player> getNearbyPlayers(double radius) {
        if (!isValid()) {
            return java.util.Collections.emptyList();
        }

        try {
            return entity.getNearbyEntities(radius, radius, radius).stream()
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Failed to get nearby players: %s", e.getMessage()));
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Enhanced critical hit chance calculation for elites
     *
     * @return true if critical hit should occur
     */
    public boolean rollForCritical() {
        if (!isEligibleForCritical()) {
            return false;
        }

        try {
            int critChance = calculateEliteCriticalChance();

            // Check for golem boss berserker immunity
            if (MobUtils.isGolemBoss(entity) &&
                    MobUtils.getMetadataInt(entity, "stage", 0) == 3) {
                return false;
            }

            Random random = new Random();
            int roll = random.nextInt(250) + 1; // Slightly harder for elites

            boolean success = roll <= critChance;

            if (success) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
            }

            return success;

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[EliteMob] Critical roll failed: %s", e.getMessage()));
            return false;
        }
    }

    private int calculateEliteCriticalChance() {
        switch (tier) {
            case 1: return 5;   // 2%
            case 2: return 7;   // 2.8%
            case 3: return 10;  // 4%
            case 4: return 13;  // 5.2%
            case 5:
            case 6: return 20;  // 8%
            default: return 5;
        }
    }

    // ================ STATUS METHODS ================

    /**
     * Get the number of critical attacks performed
     *
     * @return Critical attack count
     */
    public int getCriticalAttackCount() {
        return criticalAttackCount;
    }

    /**
     * Get the time of the last critical attack
     *
     * @return Last critical attack timestamp
     */
    public long getLastCriticalAttack() {
        return lastCriticalAttack;
    }

    /**
     * Check if this elite is currently spinning (during critical state)
     *
     * @return true if spinning
     */
    public boolean isSpinning() {
        return isSpinning;
    }

    /**
     * Get the time since last critical attack in seconds
     *
     * @return Seconds since last critical attack
     */
    public long getSecondsSinceLastCritical() {
        if (lastCriticalAttack == 0) return -1;
        return (System.currentTimeMillis() - lastCriticalAttack) / 1000;
    }
}