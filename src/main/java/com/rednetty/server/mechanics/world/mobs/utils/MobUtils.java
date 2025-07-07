package com.rednetty.server.mechanics.world.mobs.utils;

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
import java.util.logging.Logger;

/**
 * Enhanced utility class for mob-related operations with improved 1.20.2 compatibility
 */
public class MobUtils {
    private static final Random random = new Random();
    private static final Logger logger = YakRealms.getInstance().getLogger();

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
                    logger.warning("Error parsing damage from item lore: " + e.getMessage());
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
     * Enhanced for 1.20.2 with improved validation
     */
    public static boolean isSafeSpot(Player player, LivingEntity mob) {
        if (player == null || mob == null || !mob.isValid()) {
            return false;
        }

        try {
            Location target = player.getLocation();
            Location mobLoc = mob.getLocation();

            // Validate locations
            if (target == null || mobLoc == null ||
                    target.getWorld() == null || mobLoc.getWorld() == null ||
                    !target.getWorld().equals(mobLoc.getWorld())) {
                return false;
            }

            // FIXED: Enhanced line of sight check for 1.20.2
            if (!hasLineOfSight(mob, player)) {
                return true;
            }

            double distance = target.distanceSquared(mobLoc);

            // Check if mob is in liquid
            if (isInLiquid(mobLoc)) {
                return true;
            }

            // Check if player is above mob and mob is in liquid
            if (target.getBlockY() > mobLoc.getBlockY() &&
                    isInLiquid(mobLoc.clone().add(0, 1, 0))) {
                return true;
            }

            // Enhanced stuck detection for 1.20.2
            boolean mobStuck = isMobStuck(mobLoc);

            return (distance >= 3 && distance <= 36 &&
                    target.getBlockY() > (mobLoc.getBlockY() + 1)) || mobStuck;

        } catch (Exception e) {
            logger.warning("Error checking safe spot: " + e.getMessage());
            return false;
        }
    }

    /**
     * FIXED: Enhanced line of sight check for 1.20.2
     */
    private static boolean hasLineOfSight(LivingEntity mob, Player player) {
        try {
            return mob.hasLineOfSight(player);
        } catch (Exception e) {
            // Fallback to distance-based check if hasLineOfSight fails
            double distance = mob.getLocation().distance(player.getLocation());
            return distance <= 40; // Assume line of sight within reasonable range
        }
    }

    /**
     * FIXED: Enhanced liquid detection for 1.20.2
     */
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

    /**
     * FIXED: Enhanced stuck detection for 1.20.2
     */
    private static boolean isMobStuck(Location mobLoc) {
        try {
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
            return solidBlocksAround >= 7;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a player is nearby a location
     * Enhanced for 1.20.2 with better performance
     *
     * @param location The location to check
     * @param distance Maximum distance to check
     * @return true if a player is nearby
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
     *
     * @param entity       The entity
     * @param key          Metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
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
     *
     * @param entity       The entity
     * @param key          Metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
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
     *
     * @param entity       The entity
     * @param key          Metadata key
     * @param defaultValue Default value if not found
     * @return The metadata value or default
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
     * FIXED: Generate a health bar string with enhanced 1.20.2 compatibility
     */
    public static String generateHealthBar(LivingEntity entity, double health, double maxHealth, int tier, boolean inCriticalState) {
        if (entity == null) return "";

        ChatColor tierColor = null;
        try {
            boolean boss = isElite(entity);

            // FIXED: Set color based on tier properly
            tierColor = getTierColor(tier);
            String str = tierColor.toString();

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
        } catch (Exception e) {
            logger.warning("Error generating health bar: " + e.getMessage());
            return tierColor.toString() + "Error";
        }
    }

    /**
     * FIXED: Helper method to get tier color consistently
     *
     * @param tier The tier level (1-6)
     * @return The appropriate ChatColor for the tier
     */
    public static ChatColor getTierColor(int tier) {
        switch (tier) {
            case 1: return ChatColor.WHITE;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.LIGHT_PURPLE;
            case 5: return ChatColor.YELLOW;
            case 6: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }

    /**
     * FIXED: Generate a properly formatted mob name with tier colors
     *
     * @param baseName The base name of the mob
     * @param tier The tier level
     * @param elite Whether this is an elite mob
     * @return Formatted name with proper colors
     */
    public static String formatMobName(String baseName, int tier, boolean elite) {
        if (baseName == null || baseName.isEmpty()) {
            return "";
        }

        // Strip existing colors
        String cleanName = ChatColor.stripColor(baseName);
        ChatColor tierColor = getTierColor(tier);

        return elite ?
                tierColor.toString() + ChatColor.BOLD + cleanName :
                tierColor + cleanName;
    }

    /**
     * FIXED: Check if a string represents a health bar
     *
     * @param text The text to check
     * @return true if it's a health bar
     */
    public static boolean isHealthBar(String text) {
        if (text == null) return false;
        return text.contains(ChatColor.GREEN + "|") ||
                text.contains(ChatColor.GRAY + "|") ||
                text.contains(ChatColor.LIGHT_PURPLE + "|");
    }

    /**
     * FIXED: Enhanced entity validation for 1.20.2
     *
     * @param entity The entity to validate
     * @return true if entity is valid and tracked properly
     */
    public static boolean isEntityValidAndTracked(LivingEntity entity) {
        if (entity == null) return false;

        try {
            // Basic validity checks
            if (!entity.isValid() || entity.isDead()) {
                return false;
            }

            // Check if entity is properly tracked by server
            Entity foundEntity = Bukkit.getEntity(entity.getUniqueId());
            if (foundEntity == null || !foundEntity.equals(entity)) {
                return false;
            }

            // Verify world is loaded and entity is in world
            if (entity.getWorld() == null || !entity.isInWorld()) {
                return false;
            }

            // Check if chunk is loaded (important for 1.20.2)
            Location loc = entity.getLocation();
            if (loc == null || !entity.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warning("Entity validation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * FIXED: Clean a mob name of health bar formatting
     *
     * @param name The name to clean
     * @return Clean name without health bar characters
     */
    public static String cleanMobName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        // Remove health bar characters
        String cleaned = name.replaceAll("[|]", "");

        // Remove multiple consecutive color codes
        cleaned = cleaned.replaceAll("(§.){2,}", "§r");

        return cleaned.trim();
    }

    /**
     * Get the display name for a mob type
     *
     * @param mobType The mob type string
     * @return Formatted display name
     */
    public static String getDisplayName(String mobType) {
        if (mobType == null || mobType.isEmpty()) {
            return "Unknown";
        }

        // Capitalize first letter and handle special cases
        String formatted = mobType.substring(0, 1).toUpperCase() + mobType.substring(1).toLowerCase();

        switch (formatted.toLowerCase()) {
            case "witherskeleton":
                return "Wither Skeleton";
            case "cavespider":
                return "Cave Spider";
            case "magmacube":
                return "Magma Cube";
            case "zombifiedpiglin":
                return "Zombified Piglin";
            default:
                return formatted.replace("_", " ");
        }
    }

    /**
     * FIXED: Enhanced mob name capture that stores the actual original name
     *
     * @param entity The entity to capture name from
     * @return The original display name or null if not available
     */
    public static String captureOriginalName(LivingEntity entity) {
        if (entity == null) return null;

        try {
            String currentName = entity.getCustomName();

            // Don't capture health bars as original names
            if (currentName != null && !isHealthBar(currentName)) {
                return currentName;
            }

            // Try metadata if current name is a health bar
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
     * FIXED: Validate that a name is suitable for restoration
     *
     * @param name The name to validate
     * @return true if the name is valid for restoration
     */
    public static boolean isValidRestorationName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        // Don't restore health bars
        if (isHealthBar(name)) {
            return false;
        }

        // Don't restore names that are just color codes
        String stripped = ChatColor.stripColor(name);
        if (stripped == null || stripped.trim().isEmpty()) {
            return false;
        }

        return true;
    }
}