package com.rednetty.server.mechanics.mobs.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.mobs.utils.MobUtils;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CLEANED: EliteMob class with crit system delegated to CritManager
 * - Removed all duplicate crit system code
 * - Simplified to focus on elite-specific behavior
 * - All crit functionality now handled by CritManager
 * - Enhanced spawning and appearance systems
 */
public class EliteMob extends CustomMob {

    /**
     * Create a new elite mob
     */
    public EliteMob(MobType type, int tier) {
        super(type, tier, true);
    }

    // ================ ENHANCED SPAWNING ================

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
            LOGGER.warning(String.format("[EliteMob] Elite enhancements failed: %s", e.getMessage()));
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

            ItemStack weapon = entity.getEquipment().getItemInMainHand();
            if (weapon != null && weapon.getType() != Material.AIR) {
                if (!weapon.hasItemMeta() || (weapon.hasItemMeta() && !weapon.getItemMeta().hasEnchants())) {
                    weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
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
                String eliteName = ChatColor.LIGHT_PURPLE + "" +
                        ChatColor.BOLD +
                        ChatColor.stripColor(name);

                if (!name.startsWith(ChatColor.LIGHT_PURPLE.toString())) {
                    entity.setCustomName(eliteName);
                    currentDisplayName = eliteName;
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    // ================ ELITE BEHAVIOR ================

    @Override
    public void tick() {
        super.tick(); // Call parent tick first

        if (!isValid()) return;

        try {
            // Elite-specific behavior can be added here
            // For now, just enhanced hit detection
        } catch (Exception e) {
            LOGGER.warning("[EliteMob] Elite tick error: " + e.getMessage());
        }
    }

    // ================ ENHANCED HIT HANDLING ================

    public void onHitByPlayer(Player player, double damage) {
        if (!isValid()) return;

        try {
            lastDamageTime = System.currentTimeMillis();
            nameVisible = true;
            updateHealthBar();

            // Enhanced hit effects for elites
            if (type == MobType.FROZEN_BOSS || type == MobType.FROZEN_ELITE) {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.4f, 1.2f);
            } else if (type == MobType.MERIDIAN) {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HURT, 0.3f, 1.5f);
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    // ================ DAMAGE CALCULATION ================

    /**
     * Get damage from weapon range for whirlwind calculations
     */
    public double getDamageFromWeaponRange() {
        try {
            if (entity.getEquipment() == null || entity.getEquipment().getItemInMainHand() == null) {
                return 20 + (tier * 10); // Fallback based on tier
            }

            List<Integer> damageRange = MobUtils.getDamageRange(entity.getEquipment().getItemInMainHand());
            int min = damageRange.get(0);
            int max = damageRange.get(1);

            // Return average damage
            return (min + max) / 2.0;

        } catch (Exception e) {
            return 30 + (tier * 5); // Safe fallback
        }
    }

    // ================ UTILITY METHODS ================

    protected List<Player> getNearbyPlayers(double radius) {
        if (!isValid()) {
            return Collections.emptyList();
        }

        try {
            return entity.getNearbyEntities(radius, radius, radius).stream()
                    .filter(entity -> entity instanceof Player)
                    .map(entity -> (Player) entity)
                    .filter(player -> player.isOnline() && !player.isDead())
                    .filter(player -> player.getGameMode() != GameMode.CREATIVE)
                    .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ================ SPECIAL ELITE PROPERTIES ================

    /**
     * Check if this elite has special frozen properties
     */
    public boolean isFrozenType() {
        return type == MobType.FROZEN_BOSS ||
                type == MobType.FROZEN_ELITE ||
                type == MobType.FROZEN_GOLEM ||
                type == MobType.FROSTWING;
    }

    /**
     * Check if this elite has special warden properties
     */
    public boolean isWardenType() {
        return type == MobType.MERIDIAN;
    }

    /**
     * Check if this elite has special boss properties
     */
    public boolean isBossType() {
        return type.isWorldBoss() ||
                type == MobType.BOSS_SKELETON ||
                type == MobType.MERIDIAN;
    }

    /**
     * Get the appropriate elite title color
     */
    public ChatColor getEliteTitleColor() {
        if (isBossType()) {
            return ChatColor.DARK_RED;
        } else if (isFrozenType()) {
            return ChatColor.AQUA;
        } else if (isWardenType()) {
            return ChatColor.DARK_PURPLE;
        } else {
            return ChatColor.LIGHT_PURPLE;
        }
    }

    /**
     * Apply elite-specific visual effects
     */
    public void applyEliteVisualEffects() {
        if (!isValid()) return;

        try {
            if (isFrozenType()) {
                // Frozen effects
                entity.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        entity.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.02);
            } else if (isWardenType()) {
                // Warden effects
                entity.getWorld().spawnParticle(Particle.SMOKE_LARGE,
                        entity.getLocation().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0.01);
            } else if (tier >= 5) {
                // High tier magical effects
                entity.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                        entity.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0.05);
            }
        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    /**
     * Get elite combat modifiers
     */
    public double getEliteAttackModifier() {
        double modifier = 1.0;

        if (isBossType()) {
            modifier += 0.5; // +50% for bosses
        }

        if (tier >= 5) {
            modifier += 0.2; // +20% for T5+
        }

        if (isFrozenType()) {
            modifier += 0.3; // +30% for frozen types
        }

        return modifier;
    }

    /**
     * Get elite defense modifiers
     */
    public double getEliteDefenseModifier() {
        double modifier = 1.0;

        if (isBossType()) {
            modifier += 0.4; // +40% defense for bosses
        }

        if (tier >= 5) {
            modifier += 0.15; // +15% defense for T5+
        }

        return modifier;
    }

    // ================ ENHANCED HEALTH BAR ================

    @Override
    public String generateHealthBar() {
        if (!isValid()) return "";

        try {
            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            if (health <= 0) health = 0.1;
            if (maxHealth <= 0) maxHealth = 1;

            // Enhanced bar length for elites
            int barLength = Math.max(40, MobUtils.getBarLength(tier));

            // Check if in crit state
            boolean inCritState = isInCriticalState();

            ChatColor tierColor = MobUtils.getTierColor(tier);
            String str = tierColor.toString();

            // Calculate health percentage
            double perc = health / maxHealth;

            // Set bar color based on critical state
            String barColor = inCritState ?
                    ChatColor.LIGHT_PURPLE.toString() : ChatColor.GREEN.toString();

            // Generate the bar with elite length
            for (int i = 1; i <= barLength; ++i) {
                str = perc >= (double) i / (double) barLength ?
                        str + barColor + "|" : str + ChatColor.GRAY + "|";
            }

            return str;

        } catch (Exception e) {
            LOGGER.warning("[EliteMob] Health bar generation failed: " + e.getMessage());
            return this.getOriginalName() != null ? getOriginalName() : generateDefaultNameSafe();
        }
    }

    // ================ ELITE STATUS METHODS ================

    /**
     * Check if elite is in any special state
     */
    public boolean isInSpecialState() {
        return isInCriticalState() ||
                entity.hasPotionEffect(PotionEffectType.SLOW) ||
                entity.hasPotionEffect(PotionEffectType.GLOWING);
    }

    /**
     * Get elite status description
     */
    public String getEliteStatus() {
        if (isInCriticalState()) {
            if (isCritReadyToAttack()) {
                return "§5CHARGED";
            } else {
                return "§6CRITICAL (" + getCritCountdown() + ")";
            }
        }

        if (isInSpecialState()) {
            return "§eACTIVE";
        }

        return "§7NORMAL";
    }

    /**
     * Get elite display information
     */
    public String getEliteDisplayInfo() {
        StringBuilder info = new StringBuilder();

        info.append(getEliteTitleColor()).append("§l[ELITE] ");
        info.append(type.getTierSpecificName(tier));
        info.append(" §7T").append(tier);

        String status = getEliteStatus();
        if (!status.equals("§7NORMAL")) {
            info.append(" ").append(status);
        }

        return info.toString();
    }

    // ================ ENHANCED REMOVAL ================

    @Override
    public void remove() {
        try {
            // Apply death effects for elites
            if (isValid()) {
                applyEliteDeathEffects();
            }

            // Call parent removal
            super.remove();

        } catch (Exception e) {
            LOGGER.warning("[EliteMob] Elite removal failed: " + e.getMessage());
        }
    }

    private void applyEliteDeathEffects() {
        try {
            Location loc = entity.getLocation();

            // Elite death explosion
            entity.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            entity.getWorld().spawnParticle(Particle.EXPLOSION_LARGE,
                    loc.add(0, 1, 0), 8, 1.0, 1.0, 1.0, 0.1);

            // Type-specific death effects
            if (isFrozenType()) {
                entity.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        loc, 30, 2.0, 2.0, 2.0, 0.1);
                entity.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            } else if (isWardenType()) {
                entity.getWorld().spawnParticle(Particle.SMOKE_LARGE,
                        loc, 20, 1.5, 1.5, 1.5, 0.1);
                entity.getWorld().playSound(loc, Sound.ENTITY_WARDEN_DEATH, 0.8f, 1.0f);
            }

        } catch (Exception e) {
            // Silent fail for death effects
        }
    }
}