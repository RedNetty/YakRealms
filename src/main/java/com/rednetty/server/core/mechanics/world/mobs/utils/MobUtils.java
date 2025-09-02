package com.rednetty.server.core.mechanics.world.mobs.utils;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.core.mechanics.world.mobs.core.MobType;
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
import java.util.logging.Logger;

/**
 * Optimized utility class for mob-related operations with full T6 Netherite support
 */
public class MobUtils {
    private static final Random random = new Random();
    private static final Logger logger = YakRealms.getInstance().getLogger();

    /**
     * Get the tier of a mob based on its equipment with T6 Netherite support
     */
    public static int getMobTier(LivingEntity entity) {
        if (entity == null || entity.getEquipment() == null ||
                entity.getEquipment().getItemInMainHand() == null) {
            return 0;
        }

        String itemType = entity.getEquipment().getItemInMainHand().getType().name();
        boolean isT6Netherite = isNetheriteT6(entity.getEquipment().getItemInMainHand());

        if (itemType.contains("NETHERITE_")) {
            return 6; // All netherite is T6
        }
        if (itemType.contains("WOOD_") || itemType.contains("WOODEN_")) return 1;
        if (itemType.contains("STONE_")) return 2;
        if (itemType.contains("IRON_")) return 3;
        if (itemType.contains("DIAMOND_")) return 4;
        if (itemType.contains("GOLD_") || itemType.contains("GOLDEN_")) return 5;

        // Check armor for T6 indicators
        try {
            ItemStack[] armorContents = entity.getEquipment().getArmorContents();
            if (armorContents != null) {
                for (ItemStack armor : armorContents) {
                    if (isNetheriteT6(armor)) {
                        return 6;
                    }
                }
            }
        } catch (Exception e) {
            // Equipment access failed, fall back to tier 1
            logger.warning("Failed to check armor for mob tier: " + e.getMessage());
        }

        return 1;
    }

    /**
     * Check if an item is T6 Netherite (replaces old isBlueLeather method)
     */
    public static boolean isNetheriteT6(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (item.getType().name().contains("NETHERITE_")) {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String displayName = item.getItemMeta().getDisplayName();
                return displayName.contains(ChatColor.GOLD.toString());
            }
            return true;
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            return displayName.contains(ChatColor.GOLD.toString()) &&
                    (displayName.toLowerCase().contains("netherite") ||
                            displayName.toLowerCase().contains("tier 6") ||
                            displayName.toLowerCase().contains("t6"));
        }

        return false;
    }

    /**
     * @deprecated Use isNetheriteT6 instead
     */
    @Deprecated
    public static boolean isBlueLeather(ItemStack item) {
        return isNetheriteT6(item);
    }

    /**
     * Get a player's tier based on their armor with T6 Netherite support
     */
    public static int getPlayerTier(Player player) {
        int tier = 0;
        for (ItemStack is : player.getInventory().getArmorContents()) {
            if (is != null && is.getType() != Material.AIR) {
                String armorType = is.getType().name();

                if (armorType.contains("NETHERITE_")) {
                    tier = Math.max(6, tier);
                } else if (armorType.contains("LEATHER_")) {
                    tier = Math.max(isNetheriteT6(is) ? 6 : 1, tier);
                } else if (armorType.contains("CHAINMAIL_")) {
                    tier = Math.max(2, tier);
                } else if (armorType.contains("IRON_")) {
                    tier = Math.max(3, tier);
                } else if (armorType.contains("DIAMOND_")) {
                    tier = Math.max(4, tier);
                } else if (armorType.contains("GOLD_") || armorType.contains("GOLDEN_")) {
                    tier = Math.max(5, tier);
                }
            }
        }
        return tier;
    }

    /**
     * Check if an entity is an elite mob - enhanced with metadata check
     */
    public static boolean isElite(LivingEntity entity) {
        if (entity == null) return false;

        // First check metadata (most reliable)
        if (entity.hasMetadata("elite")) {
            try {
                return entity.getMetadata("elite").get(0).asBoolean();
            } catch (Exception e) {
                logger.warning("Failed to read elite metadata: " + e.getMessage());
            }
        }

        // Fallback to equipment check
        try {
            if (entity.getEquipment() == null) return false;
            
            ItemStack weapon = entity.getEquipment().getItemInMainHand();
            if (weapon != null && weapon.hasItemMeta() && weapon.getItemMeta().hasEnchants()) {
                return true;
            }

            ItemStack chestplate = entity.getEquipment().getChestplate();
            if (chestplate != null && chestplate.hasItemMeta() && chestplate.getItemMeta().hasEnchants()) {
                return true;
            }
        } catch (Exception e) {
            logger.warning("Failed to check elite equipment: " + e.getMessage());
        }

        return false;
    }

    /**
     * Check if entity is a frozen boss
     */
    public static boolean isFrozenBoss(LivingEntity entity) {
        return entity != null && entity.hasMetadata("type") &&
                entity.getMetadata("type").get(0).asString().equals("frozenboss");
    }

    /**
     * Check if entity is a golem boss
     */
    public static boolean isGolemBoss(LivingEntity entity) {
        return entity != null && entity.hasMetadata("type") &&
                entity.getMetadata("type").get(0).asString().equals("frozengolem");
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
     */
    public static int getGolemStage(LivingEntity entity) {
        return entity != null && entity.hasMetadata("stage") ?
                entity.getMetadata("stage").get(0).asInt() : 0;
    }

    /**
     * Check if an entity is a world boss
     * FIXED: Properly distinguish T6 Elites from actual World Bosses using MobType.isWorldBoss()
     */
    public static boolean isWorldBoss(LivingEntity entity) {
        if (entity == null) return false;

        // First check the explicit worldboss metadata (highest priority)
        if (entity.hasMetadata("worldboss")) {
            return entity.getMetadata("worldboss").get(0).asBoolean();
        }

        // Then check the mob type property
        if (entity.hasMetadata("type")) {
            String typeId = entity.getMetadata("type").get(0).asString();
            MobType mobType = MobType.getById(typeId);
            
            if (mobType != null) {
                // Use the MobType's isWorldBoss property - this is the authoritative check
                return mobType.isWorldBoss();
            }
            
            // Fallback to hardcoded check for legacy compatibility
            return typeId.equals("frostwing") || typeId.equals("chronos") ||
                    typeId.equals("frozenboss") || typeId.equals("frozenelite") ||
                    typeId.equals("frozengolem") || typeId.equals("frostking");
        }

        // Check if this is a CustomMob and get its type directly
        if (entity.hasMetadata("custommob")) {
            try {
                CustomMob customMob = (CustomMob) entity.getMetadata("custommob").get(0).value();
                if (customMob != null) {
                    return customMob.getType().isWorldBoss();
                }
            } catch (Exception e) {
                // Ignore casting errors
            }
        }

        return false;
    }

    /**
     * Get damage range from an item's lore
     */
    public static List<Integer> getDamageRange(ItemStack item) {
        List<Integer> range = new ArrayList<>();
        range.add(1);
        range.add(1);

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
                    logger.warning("Error parsing damage from item lore: " + e.getMessage());
                }
            }
        }

        return range;
    }

    /**
     * Calculate health from armor items
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
     * Apply tier-based health multiplier with T6 support - REBALANCED for better combat pacing
     */
    public static int applyHealthMultiplier(int baseHealth, int tier, boolean elite) {
        if (elite) {
            return switch (tier) {
                case 1 -> (int) (baseHealth * 1.4);  // Reduced from 1.8x
                case 2 -> (int) (baseHealth * 1.5);  // Reduced from 1.85x
                case 3 -> (int) (baseHealth * 1.7);  // Reduced from 2x
                case 4 -> (int) (baseHealth * 2.2);  // Reduced from 3x
                case 5 -> (int) (baseHealth * 2.8);  // Reduced from 4x
                case 6 -> (int) (baseHealth * 3.5);  // Reduced from 5x - T6 Netherite elite multiplier
                default -> (int) (baseHealth * 1.5); // Reduced from 2x
            };
        } else {
            return baseHealth;
        }
    }

    /**
     * Get the appropriate health bar length based on tier with T6 support
     */
    public static int getBarLength(int tier) {
        return switch (tier) {
            case 2 -> 30;
            case 3 -> 35;
            case 4 -> 40;
            case 5 -> 50;
            case 6 -> 65; // T6 Netherite bar length
            default -> 25;
        };
    }

    /**
     * Optimized safe spot detection
     */
    public static boolean isSafeSpot(Player player, LivingEntity mob) {
        if (player == null || mob == null || !mob.isValid()) {
            return false;
        }

        try {
            Location target = player.getLocation();
            Location mobLoc = mob.getLocation();

            if (target == null || mobLoc == null ||
                    target.getWorld() == null || mobLoc.getWorld() == null ||
                    !target.getWorld().equals(mobLoc.getWorld())) {
                return false;
            }

            if (!hasLineOfSight(mob, player)) {
                return true;
            }

            double distance = target.distanceSquared(mobLoc);

            if (isInLiquid(mobLoc)) {
                return true;
            }

            if (target.getBlockY() > mobLoc.getBlockY() &&
                    isInLiquid(mobLoc.clone().add(0, 1, 0))) {
                return true;
            }

            boolean mobStuck = isMobStuck(mobLoc);

            return (distance >= 3 && distance <= 36 &&
                    target.getBlockY() > (mobLoc.getBlockY() + 1)) || mobStuck;

        } catch (Exception e) {
            logger.warning("Error checking safe spot: " + e.getMessage());
            return false;
        }
    }

    private static boolean hasLineOfSight(LivingEntity mob, Player player) {
        try {
            return mob.hasLineOfSight(player);
        } catch (Exception e) {
            double distance = mob.getLocation().distance(player.getLocation());
            return distance <= 40;
        }
    }

    private static boolean isInLiquid(Location location) {
        try {
            Material blockType = location.getBlock().getType();
            return blockType == Material.WATER ||
                    blockType == Material.LAVA ||
                    blockType.name().contains("WATER") ||
                    blockType.name().contains("LAVA");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isMobStuck(Location mobLoc) {
        try {
            int solidBlocksAround = 0;

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;

                    Location checkLoc = mobLoc.clone().add(x, 0, z);
                    if (checkLoc.getBlock().getType().isSolid()) {
                        solidBlocksAround++;
                    }
                }
            }

            return solidBlocksAround >= 7;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a player is nearby a location
     */
    public static boolean isPlayerNearby(Location location, double distance) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        try {
            double distanceSquared = distance * distance;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != null &&
                        player.isValid() &&
                        player.getWorld().equals(location.getWorld()) &&
                        player.getLocation().distanceSquared(location) <= distanceSquared) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.warning("Error checking nearby players: " + e.getMessage());
        }

        return false;
    }

    /**
     * Extract a metadata value as an integer from an entity
     */
    public static int getMetadataInt(LivingEntity entity, String key, int defaultValue) {
        if (entity == null || !entity.hasMetadata(key)) {
            return defaultValue;
        }

        try {
            List<MetadataValue> values = entity.getMetadata(key);
            if (!values.isEmpty()) {
                return values.get(0).asInt();
            }
        } catch (Exception e) {
            logger.warning("Error getting metadata int: " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Extract a metadata value as a string from an entity
     */
    public static String getMetadataString(LivingEntity entity, String key, String defaultValue) {
        if (entity == null || !entity.hasMetadata(key)) {
            return defaultValue;
        }

        try {
            List<MetadataValue> values = entity.getMetadata(key);
            if (!values.isEmpty()) {
                return values.get(0).asString();
            }
        } catch (Exception e) {
            logger.warning("Error getting metadata string: " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Extract a metadata value as a boolean from an entity
     */
    public static boolean getMetadataBoolean(LivingEntity entity, String key, boolean defaultValue) {
        if (entity == null || !entity.hasMetadata(key)) {
            return defaultValue;
        }

        try {
            List<MetadataValue> values = entity.getMetadata(key);
            if (!values.isEmpty()) {
                return values.get(0).asBoolean();
            }
        } catch (Exception e) {
            logger.warning("Error getting metadata boolean: " + e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Optimized health bar generation with caching for better performance
     */
    public static String generateHealthBar(LivingEntity entity, double health, double maxHealth, int tier, boolean inCriticalState) {
        if (entity == null) return "";

        ChatColor tierColor = null;
        try {
            boolean boss = isElite(entity);
            tierColor = getTierColor(tier);
            StringBuilder str = new StringBuilder(tierColor.toString());

            if (health <= 0) health = 0.1;
            if (maxHealth <= 0) maxHealth = 1;

            double perc = health / maxHealth;
            int lines = getBarLength(tier);

            String barColor = inCriticalState ?
                    ChatColor.LIGHT_PURPLE.toString() : ChatColor.GREEN.toString();

            for (int i = 1; i <= lines; ++i) {
                str.append(perc >= (double) i / (double) lines ?
                        barColor + "|" : ChatColor.GRAY + "|");
            }

            if (!boss && str.length() > 0) {
                str.setLength(str.length() - 1);
            }

            return str.toString();
        } catch (Exception e) {
            logger.warning("Error generating health bar: " + e.getMessage());
            return tierColor != null ? tierColor.toString() + "Error" : "Error";
        }
    }

    /**
     * Get tier color with T6 GOLD support
     */
    public static ChatColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.GOLD; // T6 Netherite color
            default -> ChatColor.WHITE;
        };
    }

    /**
     * Format mob name with proper T6 support
     */
    public static String formatMobName(String baseName, int tier, boolean elite) {
        if (baseName == null || baseName.isEmpty()) {
            return "";
        }

        String cleanName = ChatColor.stripColor(baseName);
        ChatColor tierColor = getTierColor(tier);

        return elite ?
                tierColor.toString() + ChatColor.BOLD + cleanName :
                tierColor + cleanName;
    }

    /**
     * Optimized health bar detection
     */
    public static boolean isHealthBar(String text) {
        if (text == null) return false;
        return text.contains(ChatColor.GREEN + "|") ||
                text.contains(ChatColor.GRAY + "|") ||
                text.contains(ChatColor.LIGHT_PURPLE + "|");
    }

    /**
     * Clean a mob name of health bar formatting
     */
    public static String cleanMobName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String cleaned = name.replaceAll("[|]", "");
        cleaned = cleaned.replaceAll("(§.){2,}", "§r");

        return cleaned.trim();
    }

    /**
     * Get the display name for a mob type
     */
    public static String getDisplayName(String mobType) {
        if (mobType == null || mobType.isEmpty()) {
            return "Unknown";
        }

        String formatted = mobType.substring(0, 1).toUpperCase() + mobType.substring(1).toLowerCase();

        return switch (formatted.toLowerCase()) {
            case "witherskeleton" -> "Wither Skeleton";
            case "cavespider" -> "Cave Spider";
            case "magmacube" -> "Magma Cube";
            case "zombifiedpiglin" -> "Zombified Piglin";
            default -> formatted.replace("_", " ");
        };
    }

    /**
     * Optimized mob name capture that stores the actual original name
     */
    public static String captureOriginalName(LivingEntity entity) {
        if (entity == null) return null;

        try {
            String currentName = entity.getCustomName();

            if (currentName != null && !isHealthBar(currentName)) {
                return currentName;
            }

            if (entity.hasMetadata("name")) {
                String metaName = entity.getMetadata("name").get(0).asString();
                if (metaName != null && !isHealthBar(metaName)) {
                    return metaName;
                }
            }

            return null;
        } catch (Exception e) {
            logger.warning("Error capturing original name: " + e.getMessage());
            return null;
        }
    }

    /**
     * Validate that a name is suitable for restoration
     */
    public static boolean isValidRestorationName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        if (isHealthBar(name)) {
            return false;
        }

        String stripped = ChatColor.stripColor(name);
        if (stripped == null || stripped.trim().isEmpty()) {
            return false;
        }

        return true;
    }
}