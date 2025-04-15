package com.rednetty.server.mechanics.mobs.utils;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility class for mob-related operations
 */
public class MobUtils {
    private static final Random random = new Random();

    /**
     * Get the tier of a mob based on its equipment
     * Exact match to original MobHandler.getTier implementation
     */
    public static int getMobTier(LivingEntity entity) {
        if (entity == null || entity.getEquipment() == null ||
                entity.getEquipment().getItemInMainHand() == null) {
            return 1;
        }

        String itemType = entity.getEquipment().getItemInMainHand().getType().name();
        boolean isT6 = isBlueLeather(entity.getEquipment().getArmorContents()[0]);

        if (itemType.contains("WOOD_") || itemType.contains("WOODEN_")) return 1;
        if (itemType.contains("STONE_")) return 2;
        if (itemType.contains("IRON_")) return 3;
        if (itemType.contains("DIAMOND_") && !isT6) return 4;
        if (itemType.contains("GOLD_") || itemType.contains("GOLDEN_")) return 5;
        if (itemType.contains("DIAMOND_") && isT6) return 6;

        return 1; // Default to tier 1
    }

    /**
     * Check if an item is blue leather (T6 indicator)
     * Exactly matches the isBlueLeather method from old Items class
     */
    public static boolean isBlueLeather(ItemStack item) {
        return item != null &&
                item.getType() != Material.AIR &&
                item.getType().name().contains("LEATHER_") &&
                item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().contains(ChatColor.BLUE.toString());
    }

    /**
     * Get a player's tier based on their armor
     * Exact match to original getPlayerTier implementation
     */
    public static int getPlayerTier(Player player) {
        int tier = 0;
        for (ItemStack is : player.getInventory().getArmorContents()) {
            if (is != null && is.getType() != Material.AIR) {
                String armorType = is.getType().name();
                if (armorType.contains("LEATHER_")) {
                    tier = Math.max(isBlueLeather(is) ? 6 : 1, tier);
                } else if (armorType.contains("CHAINMAIL_"))
                    tier = Math.max(2, tier);
                else if (armorType.contains("IRON_"))
                    tier = Math.max(3, tier);
                else if (armorType.contains("DIAMOND_"))
                    tier = Math.max(4, tier);
                else if (armorType.contains("GOLD_") || armorType.contains("GOLDEN_"))
                    tier = Math.max(5, tier);
            }
        }
        return tier;
    }

    /**
     * Check if an entity is an elite mob
     * Exact match to original isElite implementation
     */
    public static boolean isElite(LivingEntity entity) {
        if (entity == null || entity.getEquipment() == null ||
                entity.getEquipment().getItemInMainHand() == null ||
                !entity.getEquipment().getItemInMainHand().hasItemMeta()) {
            return false;
        }

        // Check for enchanted weapon or chest
        return entity.getEquipment().getItemInMainHand().getItemMeta().hasEnchants() ||
                (entity.getEquipment().getChestplate() != null &&
                        entity.getEquipment().getChestplate().hasItemMeta() &&
                        entity.getEquipment().getChestplate().getItemMeta().hasEnchants());
    }

    /**
     * Check if entity is a frozen boss
     */
    public static boolean isFrozenBoss(LivingEntity entity) {
        return entity != null && entity.hasMetadata("type") &&
                entity.getMetadata("type").get(0).asString().equals("frozenBoss");
    }

    /**
     * Check if entity is a golem boss
     */
    public static boolean isGolemBoss(LivingEntity entity) {
        return entity != null && entity.hasMetadata("type") &&
                entity.getMetadata("type").get(0).asString().equals("frozenGolem");
    }

    /**
     * Check if entity is a skeleton elite
     */
    public static boolean isSkeletonElite(Entity entity) {
        return entity != null && entity.hasMetadata("type") &&
                entity.getMetadata("type").get(0).asString().equals("bossSkeletonDungeon");
    }

    /**
     * Get the current stage of a golem boss
     * Returns 0 if not a golem or stage not set
     */
    public static int getGolemStage(LivingEntity entity) {
        return entity != null && entity.hasMetadata("stage") ?
                entity.getMetadata("stage").get(0).asInt() : 0;
    }

    /**
     * Check if an entity is a world boss
     *
     * @param entity The entity to check
     * @return true if world boss
     */
    public static boolean isWorldBoss(LivingEntity entity) {
        if (entity == null) return false;

        // Check metadata
        if (entity.hasMetadata("worldboss")) {
            return entity.getMetadata("worldboss").get(0).asBoolean();
        }

        // Check entity type
        if (entity.hasMetadata("type")) {
            String type = entity.getMetadata("type").get(0).asString();
            return type.equals("frostwing") || type.equals("chronos") ||
                    type.equals("frozenboss") || type.equals("frozenelite") ||
                    type.equals("frozengolem");
        }

        return false;
    }

    /**
     * Get damage range from an item's lore
     * Same as original Damage.getDamageRange method
     */
    public static List<Integer> getDamageRange(ItemStack item) {
        List<Integer> range = new ArrayList<>();
        range.add(1); // min damage
        range.add(1); // max damage

        if (item == null || item.getType() == Material.AIR ||
                !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return range;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("DMG:")) {
                try {
                    String damageText = line.split("DMG: ")[1];
                    if (damageText.contains(" - ")) {
                        String[] parts = damageText.split(" - ");
                        range.set(0, Integer.parseInt(parts[0]));
                        range.set(1, Integer.parseInt(parts[1]));
                    } else {
                        int damage = Integer.parseInt(damageText);
                        range.set(0, damage);
                        range.set(1, damage);
                    }
                    break;
                } catch (Exception e) {
                    // Keep default values
                    YakRealms.getInstance().getLogger().info("Error parsing damage from item lore: " + e.getMessage());
                }
            }
        }

        return range;
    }

    /**
     * Calculate health from armor items
     *
     * @param entity The entity to check
     * @return Health value from armor
     */
    public static int calculateArmorHealth(LivingEntity entity) {
        int health = 0;

        for (ItemStack item : entity.getEquipment().getArmorContents()) {
            if (item != null && item.getType() != Material.AIR &&
                    item.hasItemMeta() && item.getItemMeta().hasLore()) {

                List<String> lore = item.getItemMeta().getLore();
                for (String line : lore) {
                    if (line.contains("HP:")) {
                        try {
                            String hpValue = line.split("HP: ")[1].trim();
                            if (hpValue.startsWith("+")) {
                                hpValue = hpValue.substring(1);
                            }
                            health += Integer.parseInt(hpValue);
                            break;
                        } catch (Exception e) {
                            // Skip this item if parsing fails
                        }
                    }
                }
            }
        }

        return health;
    }

    /**
     * Apply tier-based health multiplier
     *
     * @param baseHealth Base health value
     * @param tier       Tier level
     * @param elite      Whether this is an elite mob
     * @return Modified health value
     */
    public static int applyHealthMultiplier(int baseHealth, int tier, boolean elite) {
        if (elite) {
            switch (tier) {
                case 1:
                    return (int) (baseHealth * 1.8);
                case 2:
                    return (int) (baseHealth * 2.5);
                case 3:
                    return baseHealth * 3;
                case 4:
                    return baseHealth * 5;
                case 5:
                    return baseHealth * 6;
                case 6:
                    return baseHealth * 7;
                default:
                    return baseHealth * 2;
            }
        } else {
            switch (tier) {
                case 1:
                    return (int) (baseHealth * 0.4);
                case 2:
                    return (int) (baseHealth * 0.9);
                case 3:
                    return (int) (baseHealth * 1.2);
                case 4:
                    return (int) (baseHealth * 1.4);
                case 5:
                    return baseHealth * 2;
                case 6:
                    return (int) (baseHealth * 2.5);
                default:
                    return baseHealth;
            }
        }
    }

    /**
     * Get the appropriate health bar length based on tier
     *
     * @param tier Mob tier
     * @return Bar length
     */
    public static int getBarLength(int tier) {
        switch (tier) {
            case 2:
                return 30;
            case 3:
                return 35;
            case 4:
                return 40;
            case 5:
                return 50;
            case 6:
                return 60;
            default:
                return 25;
        }
    }

    /**
     * Check if a player is safespotting
     * Exact match to the isSafeSpot method in old Mobs class
     */
    public static boolean isSafeSpot(Player player, LivingEntity mob) {
        Location target = player.getLocation();
        Location mobLoc = mob.getLocation();

        // Check if there is a clear line of sight
        if (!mob.hasLineOfSight(player)) {
            return true;
        }

        double distance = target.distanceSquared(mobLoc);

        // Check if mob is in liquid
        if (mobLoc.getBlock().isLiquid()) {
            return true;
        }

        // Check if player is above mob and mob is in liquid
        if (target.getBlockY() > (mobLoc.getBlockY()) &&
                (mobLoc.clone().add(0, 1, 0).getBlock().isLiquid())) {
            return true;
        }

        // Check distance and height difference
        boolean mobStuck = false;
        int solidBlocksAround = 0;

        // Check blocks around the mob for potential stuck state
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // Skip center

                Location checkLoc = mobLoc.clone().add(x, 0, z);
                if (checkLoc.getBlock().getType().isSolid()) {
                    solidBlocksAround++;
                }
            }
        }

        // If mob is surrounded by 7+ solid blocks, it's likely stuck
        if (solidBlocksAround >= 7) {
            mobStuck = true;
        }

        return (distance >= 3 && distance <= 36 &&
                target.getBlockY() > (mobLoc.getBlockY() + 1)) || mobStuck;
    }

    /**
     * Check if a player is nearby a location
     *
     * @param location The location to check
     * @param distance Maximum distance to check
     * @return true if a player is nearby
     */
    public static boolean isPlayerNearby(Location location, double distance) {
        double distanceSquared = distance * distance;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld()) &&
                    player.getLocation().distanceSquared(location) <= distanceSquared) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract a metadata value as an integer from an entity
     *
     * @param entity       The entity
     * @param key          Metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
     */
    public static int getMetadataInt(LivingEntity entity, String key, int defaultValue) {
        if (entity.hasMetadata(key)) {
            List<MetadataValue> values = entity.getMetadata(key);
            if (!values.isEmpty()) {
                return values.get(0).asInt();
            }
        }
        return defaultValue;
    }

    /**
     * Extract a metadata value as a string from an entity
     *
     * @param entity       The entity
     * @param key          Metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
     */
    public static String getMetadataString(LivingEntity entity, String key, String defaultValue) {
        if (entity.hasMetadata(key)) {
            List<MetadataValue> values = entity.getMetadata(key);
            if (!values.isEmpty()) {
                return values.get(0).asString();
            }
        }
        return defaultValue;
    }

    /**
     * Extract a metadata value as a boolean from an entity
     *
     * @param entity       The entity
     * @param key          Metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
     */
    public static boolean getMetadataBoolean(LivingEntity entity, String key, boolean defaultValue) {
        if (entity.hasMetadata(key)) {
            List<MetadataValue> values = entity.getMetadata(key);
            if (!values.isEmpty()) {
                return values.get(0).asBoolean();
            }
        }
        return defaultValue;
    }

    /**
     * Generate a health bar string based on entity's health
     * Exact match for the original generateOverheadBar method
     */
    public static String generateHealthBar(LivingEntity entity, double health, double maxHealth, int tier, boolean inCriticalState) {
        boolean boss = isElite(entity);

        // Set color based on tier
        String str = "";
        switch (tier) {
            case 1:
                str = ChatColor.WHITE + "";
                break;
            case 2:
                str = ChatColor.GREEN + "";
                break;
            case 3:
                str = ChatColor.AQUA + "";
                break;
            case 4:
                str = ChatColor.LIGHT_PURPLE + "";
                break;
            case 5:
                str = ChatColor.YELLOW + "";
                break;
            case 6:
                str = ChatColor.BLUE + "";
                break;
            default:
                str = ChatColor.WHITE + "";
                break;
        }

        // Ensure health values are valid
        if (health <= 0) health = 0.1;
        if (maxHealth <= 0) maxHealth = 1;

        // Calculate health percentage
        double perc = health / maxHealth;
        int lines = 40;

        // Set bar color based on critical state
        String barColor = inCriticalState ?
                ChatColor.LIGHT_PURPLE.toString() : ChatColor.GREEN.toString();

        // Generate the bar
        for (int i = 1; i <= lines; ++i) {
            str = perc >= (double) i / (double) lines ?
                    str + barColor + "|" : str + ChatColor.GRAY + "|";
        }

        // Remove last character for non-elite mobs
        if (!boss) {
            str = str.substring(0, str.length() - 1);
        }

        return str;
    }
}