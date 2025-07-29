package com.rednetty.server.mechanics.world.mobs.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * EliteMob class with  protection systems.
 * - Equipment is unbreakable to prevent durability damage.
 * - Mobs are immune to sunlight burning.
 * - Crit functionality handled by CritManager.
 * - Improved spawning and appearance systems.
 * - Cleaned up redundant code, improved structure, better error handling.
 * - Integrated hologram usage to hide entity name when hologram is active.
 */
public class EliteMob extends CustomMob {

    private static final Logger LOGGER = YakRealms.getInstance().getLogger();

    /**
     * Create a new elite mob.
     */
    public EliteMob(MobType type, int tier) {
        super(type, tier, true);
    }

    // ================ SPAWNING ================

    @Override
    public boolean spawn(Location location) {
        boolean success = super.spawn(location);
        if (success && getEntity() != null) {
            applyEliteEnhancements();
        }
        return success;
    }

    private void applyEliteEnhancements() {
        try {
            applyEliteMovementEffects();
            enhanceEliteEquipment();
            applyEliteAppearance();
            applyEliteSunlightProtection();
        } catch (Exception e) {
            LOGGER.warning("[EliteMob] Elite enhancements failed: " + e.getMessage());
        }
    }

    /**
     * Apply additional sunlight protection for elite mobs.
     */
    private void applyEliteSunlightProtection() {
        if (!isValid()) return;

        getEntity().addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 3));
        getEntity().setMetadata("elite_sunlight_immune", new FixedMetadataValue(YakRealms.getInstance(), true));

        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info("[EliteMob] Applied sunlight protection to elite " + getType().getId());
        }
    }

    private void applyEliteMovementEffects() {
        if (!isValid() || MobUtils.isFrozenBoss(entity)) return;

    }

    /**
     * Enhance elite equipment with unbreakable guarantee and basic enchants.
     */
    private void enhanceEliteEquipment() {
        if (!isValid() || getEntity().getEquipment() == null) return;

        // Enhance weapon
        ItemStack weapon = getEntity().getEquipment().getItemInMainHand();
        if (weapon != null && weapon.getType() != Material.AIR) {
            makeItemUnbreakable(weapon);
            if (!weapon.hasItemMeta() || !weapon.getItemMeta().hasEnchants()) {
                weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
            }
            getEntity().getEquipment().setItemInMainHand(weapon);
        }

        // Enhance armor
        ItemStack[] armor = {
                getEntity().getEquipment().getHelmet(),
                getEntity().getEquipment().getChestplate(),
                getEntity().getEquipment().getLeggings(),
                getEntity().getEquipment().getBoots()
        };

        for (ItemStack piece : armor) {
            if (piece != null && piece.getType() != Material.AIR) {
                makeItemUnbreakable(piece);
                if (!piece.hasItemMeta() || !piece.getItemMeta().hasEnchants()) {
                    piece.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
                }
            }
        }

        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info("[EliteMob]  and secured equipment for elite " + type.getId());
        }
    }

    /**
     * Ensure item is unbreakable.
     */
    private void makeItemUnbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && !meta.isUnbreakable()) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
    }

    private void applyEliteAppearance() {
        if (!isValid() || getEntity().getCustomName() == null) return;

        String name = getEntity().getCustomName();
        String eliteName = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + ChatColor.stripColor(name);

        if (!name.startsWith(ChatColor.LIGHT_PURPLE.toString())) {
            getEntity().setCustomName(eliteName);
            this.baseDisplayName = eliteName;
        }
    }

    // ================ TICK SYSTEM ================

    @Override
    public void tick() {
        super.tick();

        if (!isValid()) return;

        performEliteTick();
    }

    /**
     * Elite-specific tick behavior.
     */
    private void performEliteTick() {
        if (isUndeadElite() && getEntity().getFireTicks() > 0) {
            getEntity().setFireTicks(0);
        }

        if (RANDOM.nextInt(100) < 2) {
            applyEliteVisualEffects();
        }
    }

    private boolean isUndeadElite() {
        EntityType entityType = getEntity().getType();
        return entityType == EntityType.SKELETON ||
                entityType == EntityType.WITHER_SKELETON ||
                entityType == EntityType.ZOMBIE ||
                entityType == EntityType.ZOMBIE_VILLAGER ||
                entityType == EntityType.HUSK ||
                entityType == EntityType.DROWNED ||
                entityType == EntityType.STRAY ||
                entityType == EntityType.PHANTOM;
    }

    // ================ HIT HANDLING ================

    public void onHitByPlayer(Player player, double damage) {
        if (!isValid()) return;

        lastDamageTime = System.currentTimeMillis();
        updateHealthBar();

        // Elite hit effects
        if (type == MobType.FROZEN_BOSS || type == MobType.FROZEN_ELITE) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.4f, 1.2f);
        } else if (type == MobType.MERIDIAN) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HURT, 0.3f, 1.5f);
        }
    }

    // ================ DAMAGE CALCULATION ================

    /**
     * Get damage from weapon range for whirlwind calculations.
     */
    public double getDamageFromWeaponRange() {
        if (getEntity().getEquipment() == null || getEntity().getEquipment().getItemInMainHand() == null) {
            return 20 + (tier * 10);
        }

        List<Integer> damageRange = MobUtils.getDamageRange(getEntity().getEquipment().getItemInMainHand());
        int min = damageRange.get(0);
        int max = damageRange.get(1);

        return (min + max) / 2.0;
    }

    // ================ UTILITY METHODS ================

    protected List<Player> getNearbyPlayers(double radius) {
        if (!isValid()) return Collections.emptyList();

        return getEntity().getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .filter(Player::isOnline)
                .filter(p -> !p.isDead())
                .filter(p -> p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toList());
    }

    // ================ SPECIAL ELITE PROPERTIES ================

    public boolean isFrozenType() {
        return type == MobType.FROZEN_BOSS ||
                type == MobType.FROZEN_ELITE ||
                type == MobType.FROZEN_GOLEM ||
                type == MobType.FROSTWING;
    }

    public boolean isWardenType() {
        return type == MobType.MERIDIAN;
    }

    public boolean isBossType() {
        return type.isWorldBoss() ||
                type == MobType.BOSS_SKELETON ||
                type == MobType.MERIDIAN;
    }

    public ChatColor getEliteTitleColor() {
        if (isBossType()) return ChatColor.DARK_RED;
        if (isFrozenType()) return ChatColor.AQUA;
        if (isWardenType()) return ChatColor.GOLD;
        return ChatColor.LIGHT_PURPLE;
    }

    public void applyEliteVisualEffects() {
        if (!isValid()) return;

        Particle particle = null;
        int count = 0;
        double offsetX = 0, offsetY = 0, offsetZ = 0, speed = 0;

        if (isFrozenType()) {
            particle = Particle.SNOWFLAKE;
            count = 3;
            offsetX = offsetY = offsetZ = 0.3;
            speed = 0.02;
        } else if (isWardenType()) {
            particle = Particle.SMOKE_LARGE;
            count = 2;
            offsetX = offsetY = offsetZ = 0.2;
            speed = 0.01;
        } else if (tier >= 5) {
            particle = Particle.ENCHANTMENT_TABLE;
            count = 2;
            offsetX = offsetY = offsetZ = 0.3;
            speed = 0.05;
        }

        if (particle != null) {
            startInterpolatedParticleAnimation(particle, count, offsetX, offsetY, offsetZ, speed);
        }
    }

    private void startInterpolatedParticleAnimation(Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        new BukkitRunnable() {
            int ticks = 0;
            final int duration = 20;

            @Override
            public void run() {
                if (!isValid() || ticks >= duration) {
                    cancel();
                    return;
                }

                Location loc = getEntity().getLocation().add(0, 1 + Math.sin(ticks * 0.3) * 0.5, 0);
                getEntity().getWorld().spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }

    public double getEliteAttackModifier() {
        double modifier = 1.0;
        if (isBossType()) modifier += 0.5;
        if (tier >= 5) modifier += 0.2;
        if (isFrozenType()) modifier += 0.3;
        return modifier;
    }

    public double getEliteDefenseModifier() {
        double modifier = 1.0;
        if (isBossType()) modifier += 0.4;
        if (tier >= 5) modifier += 0.15;
        return modifier;
    }

    // ================ ELITE STATUS METHODS ================

    public boolean isInSpecialState() {
        return isInCriticalState() ||
                getEntity().hasPotionEffect(PotionEffectType.SLOW) ||
                getEntity().hasPotionEffect(PotionEffectType.GLOWING);
    }

    public String getEliteStatus() {
        if (isInCriticalState()) {
            return isCritReadyToAttack() ? "§5CHARGED" : "§6CRITICAL (" + getCritCountdown() + ")";
        }
        return isInSpecialState() ? "§eACTIVE" : "§7NORMAL";
    }

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

    // ================ EQUIPMENT PROTECTION ================

    public void validateEquipmentIntegrity() {
        if (!isValid() || getEntity().getEquipment() == null) return;

        ItemStack weapon = getEntity().getEquipment().getItemInMainHand();
        if (weapon != null && weapon.getType() != Material.AIR) {
            makeItemUnbreakable(weapon);
        }

        ItemStack[] armor = {
                getEntity().getEquipment().getHelmet(),
                getEntity().getEquipment().getChestplate(),
                getEntity().getEquipment().getLeggings(),
                getEntity().getEquipment().getBoots()
        };

        for (ItemStack piece : armor) {
            if (piece != null && piece.getType() != Material.AIR) {
                makeItemUnbreakable(piece);
            }
        }
    }

    // ================ REMOVAL ================

    @Override
    public void remove() {
        if (isValid()) {
            applyEliteDeathEffects();
        }
        super.remove();
    }

    private void applyEliteDeathEffects() {
        Location loc = getEntity().getLocation();
        getEntity().getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        getEntity().getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc.add(0, 1, 0), 8, 1.0, 1.0, 1.0, 0.1);

        if (isFrozenType()) {
            getEntity().getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 30, 2.0, 2.0, 2.0, 0.1);
            getEntity().getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        } else if (isWardenType()) {
            getEntity().getWorld().spawnParticle(Particle.SMOKE_LARGE, loc, 20, 1.5, 1.5, 1.5, 0.1);
            getEntity().getWorld().playSound(loc, Sound.ENTITY_WARDEN_DEATH, 0.8f, 1.0f);
        }
    }

    // ================ ELITE-SPECIFIC PROTECTION METHODS ================

    public void extinguishCompletely() {
        if (!isValid()) return;

        getEntity().setFireTicks(0);
        if (getEntity().hasPotionEffect(PotionEffectType.WITHER)) {
            getEntity().removePotionEffect(PotionEffectType.WITHER);
        }
    }

    public String getProtectionStatus() {
        if (!isValid()) return "INVALID";

        StringBuilder status = new StringBuilder();
        status.append("Fire: ").append(getEntity().hasPotionEffect(PotionEffectType.FIRE_RESISTANCE) ? "PROTECTED" : "VULNERABLE");

        boolean hasUnbreakableWeapon = false;
        ItemStack weapon = getEntity().getEquipment().getItemInMainHand();
        if (weapon != null && weapon.hasItemMeta()) {
            hasUnbreakableWeapon = weapon.getItemMeta().isUnbreakable();
        }
        status.append(", Equipment: ").append(hasUnbreakableWeapon ? "PROTECTED" : "VULNERABLE");

        boolean hasSunlightProtection = getEntity().hasMetadata("elite_sunlight_immune");
        status.append(", Sunlight: ").append(hasSunlightProtection ? "PROTECTED" : "VULNERABLE");

        return status.toString();
    }
}