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

/**
 * Extension of CustomMob for elite mobs with special abilities
 */
public class EliteMob extends CustomMob {
    private float originalYaw = 0;
    private boolean isSpinning = false;

    /**
     * Create a new elite mob
     *
     * @param type Mob type
     * @param tier Mob tier (1-6)
     */
    public EliteMob(MobType type, int tier) {
        super(type, tier, true);
    }

    @Override
    public void setCriticalState(int duration) {
        super.setCriticalState(duration);

        if (entity == null || !entity.isValid()) return;

        // Store the original yaw for restoring later
        originalYaw = entity.getLocation().getYaw();
        isSpinning = true;

        // Apply freeze effect to elite mobs - NO MOVEMENT
        if (MobUtils.isFrozenBoss(entity)) {
            // For frozen boss, apply slowness to nearby players
            for (Player player : getNearbyPlayers(8.0)) {
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOW, 30, 1),
                        true
                );
            }
        } else {
            // Complete immobilization - stronger than before
            entity.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 100),
                    true
            );
            entity.addPotionEffect(
                    new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 128),
                    true
            );
        }

        // Initial warning explosion effect
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
        entity.getWorld().spawnParticle(
                org.bukkit.Particle.EXPLOSION_LARGE,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                5, 0.2, 0.2, 0.2, 0.1f
        );
    }

    /**
     * Execute the critical attack with enhanced visual effects
     */
    public void executeCriticalAttack() {
        if (entity == null || !entity.isValid()) return;

        // End critical state
        inCriticalState = false;
        isSpinning = false;

        // Clear metadata
        if (entity.hasMetadata("criticalState")) {
            entity.removeMetadata("criticalState", YakRealms.getInstance());
        }

        // Reset effects
        resetMobEffects();

        // Get weapon damage range
        List<Integer> damageRange = MobUtils.getDamageRange(entity.getEquipment().getItemInMainHand());
        int min = damageRange.get(0);
        int max = damageRange.get(1);
        int dmg = (ThreadLocalRandom.current().nextInt(max - min + 1) + min) * 3;

        // Get nearby players to damage
        for (Player player : getNearbyPlayers(7.0)) {
            // Apply damage
            player.damage(dmg, entity);

            // Apply knockback
            Vector direction = player.getLocation().clone().toVector()
                    .subtract(entity.getLocation().toVector());

            if (direction.length() > 0) {
                direction.normalize();

                // Reverse direction for frozen boss
                if (MobUtils.isFrozenBoss(entity)) {
                    direction.multiply(-3);
                } else {
                    direction.multiply(3);
                }

                player.setVelocity(direction);
            }

            // Play hit effects for the player
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
        }

        // FINAL EXPLOSION - much larger and more dramatic
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);

        // Large explosion effect
        entity.getWorld().spawnParticle(
                org.bukkit.Particle.EXPLOSION_HUGE,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                10, 0, 0, 0, 1.0f
        );

        // Add flame particles for additional effect
        entity.getWorld().spawnParticle(
                org.bukkit.Particle.FLAME,
                entity.getLocation().clone().add(0.0, 0.5, 0.0),
                40, 1.0, 0.2, 1.0, 0.1
        );

        // Restore original name
        restoreName();
    }

    /**
     * Reset all effects on the mob after critical attack
     */
    private void resetMobEffects() {
        if (entity == null) return;

        // Remove slowness effect
        if (entity.hasPotionEffect(PotionEffectType.SLOW)) {
            entity.removePotionEffect(PotionEffectType.SLOW);
        }

        // Remove jump effect
        if (entity.hasPotionEffect(PotionEffectType.JUMP)) {
            entity.removePotionEffect(PotionEffectType.JUMP);
        }

        // Reapply any tier-specific effects
        applyTypeProperties();
    }

    @Override
    public boolean processCriticalStateTick() {
        if (!inCriticalState || entity == null || !entity.isValid()) {
            return false;
        }

        // Handle spinning effect for elite mobs during critical state
        if (isSpinning && criticalStateDuration > 0) {
            // Calculate new rotation
            float spinSpeed = 15f; // Consistent spin speed
            Location loc = entity.getLocation();
            float newYaw = (loc.getYaw() + spinSpeed) % 360;

            // Apply rotation
            loc.setYaw(newYaw);
            entity.teleport(loc);
        }

        // Only decrement counter if still counting down (positive)
        if (criticalStateDuration > 0) {
            criticalStateDuration--;

            // Always update the health bar during critical state
            updateHealthBar();

            // Play countdown explosion effects at specific ticks
            // We'll use 3 visual warnings plus the final explosion (total of 4)
            if (criticalStateDuration == 9) { // First warning
                // Play creeper prime sound
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);

                // Visual explosion only
                entity.getWorld().spawnParticle(
                        org.bukkit.Particle.EXPLOSION_LARGE,
                        entity.getLocation().clone().add(0.0, 1.0, 0.0),
                        5, 0.3, 0.3, 0.3, 0.1
                );
            } else if (criticalStateDuration == 6) { // Second warning
                // Play creeper prime sound
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);

                // Visual explosion only
                entity.getWorld().spawnParticle(
                        org.bukkit.Particle.EXPLOSION_LARGE,
                        entity.getLocation().clone().add(0.0, 1.0, 0.0),
                        5, 0.3, 0.3, 0.3, 0.1
                );
            } else if (criticalStateDuration == 3) { // Third warning
                // Play creeper prime sound
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);

                // Visual explosion only
                entity.getWorld().spawnParticle(
                        org.bukkit.Particle.EXPLOSION_LARGE,
                        entity.getLocation().clone().add(0.0, 1.0, 0.0),
                        5, 0.3, 0.3, 0.3, 0.1
                );
            }

            // Add witch particles effect for visual indicator
            if (criticalStateDuration % 3 == 0) {
                entity.getWorld().spawnParticle(
                        org.bukkit.Particle.SPELL_WITCH,
                        entity.getLocation().clone().add(0.0, 1.0, 0.0),
                        5, 0.3, 0.3, 0.3, 0.1
                );
            }

            // When countdown reaches 0, execute attack
            if (criticalStateDuration == 0) {
                executeCriticalAttack();
                return true;
            }
        }

        return false;
    }

    /**
     * Process damage specifically for elites
     */
    @Override
    public double damage(double damage) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return 0;
        }

        // Apply elite-specific damage modifications
        double finalDamage = super.damage(damage);

        // Special handling for frozen boss
        if (MobUtils.isFrozenBoss(entity) && entity.getHealth() <
                (YakRealms.isT6Enabled() ? 100000 : 50000) && !inCriticalState) {
            // Set critical state for frozen boss at low health
            setCriticalState(12);  // Longer duration
        }

        return finalDamage;
    }

    /**
     * Get nearby players
     *
     * @param radius Radius to check
     * @return List of nearby players
     */
    protected List<Player> getNearbyPlayers(double radius) {
        if (entity == null || !entity.isValid()) {
            return java.util.Collections.emptyList();
        }

        List<Player> players = new java.util.ArrayList<>();

        for (org.bukkit.entity.Entity nearby :
                entity.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player) {
                players.add((Player) nearby);
            }
        }

        return players;
    }

    /**
     * Roll for a critical hit chance
     *
     * @return true if critical hit should occur
     */
    public boolean rollForCritical() {
        if (!isEligibleForCritical()) {
            return false;
        }

        // Calculate critical chance based on tier
        int critChance;
        switch (tier) {
            case 1:
                critChance = 5;
                break;   // 2%
            case 2:
                critChance = 7;
                break;   // 2.8%
            case 3:
                critChance = 10;
                break;  // 4%
            case 4:
                critChance = 13;
                break;  // 5.2%
            case 5:
            case 6:
                critChance = 20;
                break;  // 8%
            default:
                critChance = 5;
                break;
        }

        // Check for golem boss in berserker state (immune to crits)
        if (MobUtils.isGolemBoss(entity) &&
                MobUtils.getMetadataInt(entity, "stage", 0) == 3) {
            return false;
        }

        // Roll for critical
        Random random = new Random();
        int roll = random.nextInt(250) + 1;

        // Track if this is a successful roll
        boolean success = roll <= critChance;

        // If successful, play a warning sound
        if (success) {
            entity.getWorld().playSound(entity.getLocation(),
                    Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
        }

        return success;
    }

    @Override
    public boolean spawn(Location location) {
        boolean success = super.spawn(location);

        if (success && entity != null) {
            // Apply elite-specific effects

            // Elite mobs get speed unless they are frozen boss
            if (!MobUtils.isFrozenBoss(entity)) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            }

            // Apply special equipment modifications
            enhanceEliteEquipment();
            updateNameVisibility();
        }

        return success;
    }

    /**
     * Enhance equipment specifically for elites
     */
    private void enhanceEliteEquipment() {
        if (entity == null || entity.getEquipment() == null) return;

        // Modify weapon if needed
        org.bukkit.inventory.ItemStack weapon = entity.getEquipment().getItemInMainHand();
        if (weapon != null && weapon.getType() != org.bukkit.Material.AIR && !weapon.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = weapon.getItemMeta();
            if (meta != null && !meta.hasEnchants()) {
                // Add elite identifier enchant
                weapon.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS, 1);
                entity.getEquipment().setItemInMainHand(weapon);
            }
        }

        // Apply appropriate color to custom name
        if (entity.getCustomName() != null) {
            String name = entity.getCustomName();
            if (!name.contains(org.bukkit.ChatColor.LIGHT_PURPLE.toString() + org.bukkit.ChatColor.BOLD)) {
                entity.setCustomName(org.bukkit.ChatColor.LIGHT_PURPLE + "" + org.bukkit.ChatColor.BOLD + org.bukkit.ChatColor.stripColor(name));

                // Update our original name record
                originalName = entity.getCustomName();
            }
        }
    }

    /**
     * Apply special effects for elite mobs
     * Called when the mob is hit by a player
     */
    public void onHitByPlayer(Player player, double damage) {
        if (entity == null || !entity.isValid()) return;

        // Update combat status
        lastDamageTime = System.currentTimeMillis();
        nameVisible = true;
        updateHealthBar();

        // Special handling for specific elite types
        if (type == MobType.FROZEN_BOSS || type == MobType.FROZEN_ELITE) {
            // Apply frost effects when player hits these elites
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.2f);

            // Add particles around the player
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.SNOWFLAKE,
                    player.getLocation().add(0, 1, 0),
                    10, 0.5, 0.5, 0.5, 0.05
            );
        }
    }
}