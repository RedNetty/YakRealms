package com.rednetty.server.core.mechanics.item.orb;

import com.rednetty.server.core.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.nbt.NBTAccessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for orb interactions with items
 */
public class OrbAPI {
    private static final Logger LOGGER = Logger.getLogger(OrbAPI.class.getName());
    private static OrbGenerator orbGenerator;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    // Orb display names
    private static final String NORMAL_ORB_NAME = LEGACY_SERIALIZER.serialize(
            Component.text("Orb of Alteration", NamedTextColor.LIGHT_PURPLE)
    );
    private static final String LEGENDARY_ORB_NAME = LEGACY_SERIALIZER.serialize(
            Component.text("Legendary Orb of Alteration", NamedTextColor.YELLOW)
    );

    // Element constants
    public static final int ELEM_FIRE = 1;
    public static final int ELEM_POISON = 2;
    public static final int ELEM_ICE = 3;

    // Item type constants
    public static final int TYPE_STAFF = 0;
    public static final int TYPE_SPEAR = 1;
    public static final int TYPE_SWORD = 2;
    public static final int TYPE_AXE = 3;
    public static final int TYPE_HELMET = 4;
    public static final int TYPE_CHESTPLATE = 5;
    public static final int TYPE_LEGGINGS = 6;
    public static final int TYPE_BOOTS = 7;
    public static final int TYPE_UNKNOWN = -1;

    // Regular expressions for extracting stats
    private static final Pattern DAMAGE_PATTERN = Pattern.compile("DMG: (\\d+) - (\\d+)");
    private static final Pattern HP_PATTERN = Pattern.compile("HP: \\+(\\d+)");
    private static final Pattern HP_REGEN_PATTERN = Pattern.compile("HP REGEN: \\+(\\d+)/s");
    private static final Pattern ENERGY_PATTERN = Pattern.compile("ENERGY REGEN: \\+(\\d+)%");

    /**
     * Initialize the OrbAPI
     */
    public static void initialize() {
        orbGenerator = new OrbGenerator();
        LOGGER.info("OrbAPI initialized");
    }

    /**
     * Get the orb generator instance
     *
     * @return The orb generator
     */
    public static OrbGenerator getOrbGenerator() {
        if (orbGenerator == null) {
            orbGenerator = new OrbGenerator();
        }
        return orbGenerator;
    }

    /**
     * Check if an item is a normal orb
     *
     * @param item The item to check
     * @return true if the item is a normal orb of alteration
     */
    public static boolean isNormalOrb(ItemStack item) {
        if (item == null || item.getType() != Material.MAGMA_CREAM) {
            return false;
        }

        // First check NBT data
        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey("orbType") && "normal".equals(nbt.getString("orbType"))) {
            return true;
        }

        // Then check item display name as fallback
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            return NORMAL_ORB_NAME.equals(displayName);
        }

        return false;
    }

    /**
     * Check if an item is a legendary orb
     *
     * @param item The item to check
     * @return true if the item is a legendary orb of alteration
     */
    public static boolean isLegendaryOrb(ItemStack item) {
        if (item == null || item.getType() != Material.MAGMA_CREAM) {
            return false;
        }

        // First check NBT data
        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey("orbType") && "legendary".equals(nbt.getString("orbType"))) {
            return true;
        }

        // Then check item display name as fallback
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            return LEGENDARY_ORB_NAME.equals(displayName);
        }

        return false;
    }

    /**
     * Check if an item is any type of orb
     *
     * @param item The item to check
     * @return true if the item is any type of orb
     */
    public static boolean isOrb(ItemStack item) {
        return isNormalOrb(item) || isLegendaryOrb(item);
    }

    /**
     * Check if an item is valid for orb application
     *
     * @param item The item to check
     * @return true if the item can be modified by orbs
     */
    public static boolean isValidItemForOrb(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // Simple validation based on item material
        String material = item.getType().name();
        if (material.contains("_SWORD") || material.contains("_AXE") ||
                material.contains("_HOE") || material.contains("_SPADE") || material.contains("_SHOVEL") ||
                material.contains("_HELMET") || material.contains("_CHESTPLATE") ||
                material.contains("_LEGGINGS") || material.contains("_BOOTS")) {

            return true;
        }

        return false;
    }

    /**
     * Get the item type based on material
     *
     * @param item The item to check
     * @return The item type (0-7) or -1 if not valid
     */
    public static int getItemType(ItemStack item) {
        if (item == null) {
            return TYPE_UNKNOWN;
        }

        String materialName = item.getType().name();

        if (materialName.contains("_HOE")) {
            return TYPE_STAFF;
        }
        if (materialName.contains("_SPADE") || materialName.contains("_SHOVEL")) {
            return TYPE_SPEAR;
        }
        if (materialName.contains("_SWORD")) {
            return TYPE_SWORD;
        }
        if (materialName.contains("_AXE")) {
            return TYPE_AXE;
        }
        if (materialName.contains("_HELMET")) {
            return TYPE_HELMET;
        }
        if (materialName.contains("_CHESTPLATE")) {
            return TYPE_CHESTPLATE;
        }
        if (materialName.contains("_LEGGINGS")) {
            return TYPE_LEGGINGS;
        }
        if (materialName.contains("_BOOTS")) {
            return TYPE_BOOTS;
        }
        return TYPE_UNKNOWN;
    }

    /**
     * Check if an item is a weapon
     *
     * @param item The item to check
     * @return true if the item is a weapon
     */
    public static boolean isWeapon(ItemStack item) {
        int type = getItemType(item);
        return type >= TYPE_STAFF && type <= TYPE_AXE;
    }

    /**
     * Check if an item is armor
     *
     * @param item The item to check
     * @return true if the item is armor
     */
    public static boolean isArmor(ItemStack item) {
        int type = getItemType(item);
        return type >= TYPE_HELMET && type <= TYPE_BOOTS;
    }

    /**
     * Gets the correct tier of an item for orb processing
     * Updated for Tier 6 Netherite integration
     *
     * @param item The item to check
     * @return The correct tier (1-6) or 0 if not a valid tier item
     */
    public static int getItemTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        // First attempt to identify tier by name color
        if (item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();

            // Tier 6 - Netherite (Dark Purple)
            if (displayName.contains(ChatColor.GOLD.toString()) ||
                    displayName.toLowerCase().contains("netherite")) {
                LOGGER.info("Detected Tier 6 (Netherite/Dark Purple) item: " + displayName);
                return 6;
            }

            // Tier 5 - Legendary (Yellow/Gold)
            if (displayName.contains(ChatColor.YELLOW.toString()) ||
                    displayName.toLowerCase().contains("legendary")) {
                LOGGER.info("Detected Tier 5 (Yellow/Legendary) item: " + displayName);
                return 5;
            }

            // Tier 4 - Ancient (Light Purple)
            if (displayName.contains(ChatColor.LIGHT_PURPLE.toString()) ||
                    displayName.toLowerCase().contains("ancient")) {
                LOGGER.info("Detected Tier 4 (Light Purple/Ancient) item: " + displayName);
                return 4;
            }

            // Tier 3 - Magic (Aqua)
            if (displayName.contains(ChatColor.AQUA.toString())) {
                LOGGER.info("Detected Tier 3 (Aqua) item: " + displayName);
                return 3;
            }

            // Tier 2 - Chainmail/Stone (Green)
            if (displayName.contains(ChatColor.GREEN.toString())) {
                LOGGER.info("Detected Tier 2 (Green) item: " + displayName);
                return 2;
            }

            // Tier 1 - Leather/Wood (White)
            if (displayName.contains(ChatColor.WHITE.toString())) {
                LOGGER.info("Detected Tier 1 (White) item: " + displayName);
                return 1;
            }
        }

        // Fallback to material-based detection
        String material = item.getType().name();

        // Tier 6 - Netherite
        if (material.contains("NETHERITE_")) {
            LOGGER.info("Detected Tier 6 from material: " + material);
            return 6;
        }

        // Tier 5 - Gold
        if (material.contains("GOLD_") || material.contains("GOLDEN_")) {
            LOGGER.info("Detected Tier 5 from material: " + material);
            return 5;
        }

        // Tier 4 - Diamond
        if (material.contains("DIAMOND_")) {
            LOGGER.info("Detected Tier 4 from material: " + material);
            return 4;
        }

        // Tier 3 - Iron
        if (material.contains("IRON_")) {
            LOGGER.info("Detected Tier 3 from material: " + material);
            return 3;
        }

        // Tier 2 - Chainmail/Stone
        if (material.contains("CHAINMAIL_") || material.contains("STONE_")) {
            LOGGER.info("Detected Tier 2 from material: " + material);
            return 2;
        }

        // Tier 1 - Leather/Wood
        if (material.contains("LEATHER_") || material.contains("WOOD_") || material.contains("WOODEN_")) {
            LOGGER.info("Detected Tier 1 from material: " + material);
            return 1;
        }

        // Default case - assume tier 1 if we can't determine
        LOGGER.warning("Could not determine tier for item: " + material +
                ", defaulting to tier 1");
        return 1;
    }

    /**
     * Check if an item has special lore lines
     *
     * @param item The item to check
     * @return true if the item has special lore
     */
    public static boolean hasSpecialLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains(ChatColor.GRAY.toString()) && line.contains(ChatColor.ITALIC.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract rare/special lore from an item that should be preserved
     *
     * @param item The item to extract lore from
     * @return List of rare lore lines
     */
    public static List<String> extractSpecialLore(ItemStack item) {
        List<String> specialLore = new ArrayList<>();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return specialLore;
        }

        try {
            List<String> lore = item.getItemMeta().getLore();
            int extraLines = 0;

            // Check for protected items
            if (isProtected(item)) {
                extraLines = 2;
            }
            // Check for items with special descriptive lore
            else if (hasSpecialLore(item)) {
                extraLines = 1;
            }

            // Extract stat tracking lines if present
            boolean hasStatTracking = false;
            for (String line : lore) {
                if (line.contains("Normal Orbs Used: ")) {
                    hasStatTracking = true;
                    break;
                }
            }

            if (hasStatTracking) {
                for (int i = Math.max(0, lore.size() - 7 - extraLines); i < lore.size(); i++) {
                    if (i < 0 || i >= lore.size()) continue;

                    String line = lore.get(i);
                    if (line.contains("Normal Orbs Used: ")) {
                        try {
                            int current = Integer.parseInt(line.split(": " + ChatColor.AQUA)[1]);
                            specialLore.add(LEGACY_SERIALIZER.serialize(
                                    Component.text("Normal Orbs Used: ", NamedTextColor.GOLD)
                                            .append(Component.text(current + 1, NamedTextColor.AQUA))
                            ));
                        } catch (Exception e) {
                            specialLore.add(line);
                        }
                    } else {
                        specialLore.add(line);
                    }
                }
            } else {
                // Extract other special lore (untradeable, etc.)
                for (int i = Math.max(0, lore.size() - 1 - extraLines); i < lore.size(); i++) {
                    if (i < 0 || i >= lore.size()) continue;

                    try {
                        if (lore.get(i).contains(ChatColor.GRAY.toString()) &&
                                !lore.get(i).toLowerCase().contains("common") &&
                                !lore.get(i).toLowerCase().contains("untradeable")) {
                            i--;
                            if (i < 0) continue;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                    specialLore.add(lore.get(i));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error extracting special lore", e);
        }

        return specialLore;
    }

    /**
     * Get the base item name for the given item type and tier
     *
     * @param type The item type
     * @param tier The item tier
     * @return The base item name
     */
    public static String getBaseItemName(int type, int tier) {
        switch (tier) {
            case 1:
                return getBaseTier1Name(type);
            case 2:
                return getBaseTier2Name(type);
            case 3:
                return getBaseTier3Name(type);
            case 4:
                return getBaseTier4Name(type);
            case 5:
                return getBaseTier5Name(type);
            case 6:
                return getBaseTier6Name(type);
            default:
                return "Unknown Item";
        }
    }

    private static String getBaseTier1Name(int type) {
        switch (type) {
            case TYPE_STAFF:
                return "Staff";
            case TYPE_SPEAR:
                return "Spear";
            case TYPE_SWORD:
                return "Shortsword";
            case TYPE_AXE:
                return "Hatchet";
            case TYPE_HELMET:
                return "Leather Coif";
            case TYPE_CHESTPLATE:
                return "Leather Chestplate";
            case TYPE_LEGGINGS:
                return "Leather Leggings";
            case TYPE_BOOTS:
                return "Leather Boots";
            default:
                return "T1 Item";
        }
    }

    private static String getBaseTier2Name(int type) {
        switch (type) {
            case TYPE_STAFF:
                return "Battlestaff";
            case TYPE_SPEAR:
                return "Halberd";
            case TYPE_SWORD:
                return "Broadsword";
            case TYPE_AXE:
                return "Great Axe";
            case TYPE_HELMET:
                return "Medium Helmet";
            case TYPE_CHESTPLATE:
                return "Chainmail";
            case TYPE_LEGGINGS:
                return "Chainmail Leggings";
            case TYPE_BOOTS:
                return "Chainmail Boots";
            default:
                return "T2 Item";
        }
    }

    private static String getBaseTier3Name(int type) {
        switch (type) {
            case TYPE_STAFF:
                return "Wizard Staff";
            case TYPE_SPEAR:
                return "Magic Polearm";
            case TYPE_SWORD:
                return "Magic Sword";
            case TYPE_AXE:
                return "War Axe";
            case TYPE_HELMET:
                return "Full Helmet";
            case TYPE_CHESTPLATE:
                return "Platemail";
            case TYPE_LEGGINGS:
                return "Platemail Leggings";
            case TYPE_BOOTS:
                return "Platemail Boots";
            default:
                return "T3 Item";
        }
    }

    private static String getBaseTier4Name(int type) {
        switch (type) {
            case TYPE_STAFF:
                return "Ancient Staff";
            case TYPE_SPEAR:
                return "Ancient Polearm";
            case TYPE_SWORD:
                return "Ancient Sword";
            case TYPE_AXE:
                return "Ancient Axe";
            case TYPE_HELMET:
                return "Ancient Helmet";
            case TYPE_CHESTPLATE:
                return "Ancient Chestplate";
            case TYPE_LEGGINGS:
                return "Ancient Leggings";
            case TYPE_BOOTS:
                return "Ancient Boots";
            default:
                return "T4 Item";
        }
    }

    private static String getBaseTier5Name(int type) {
        switch (type) {
            case TYPE_STAFF:
                return "Legendary Staff";
            case TYPE_SPEAR:
                return "Legendary Polearm";
            case TYPE_SWORD:
                return "Legendary Sword";
            case TYPE_AXE:
                return "Legendary Axe";
            case TYPE_HELMET:
                return "Legendary Helmet";
            case TYPE_CHESTPLATE:
                return "Legendary Chestplate";
            case TYPE_LEGGINGS:
                return "Legendary Leggings";
            case TYPE_BOOTS:
                return "Legendary Boots";
            default:
                return "T5 Item";
        }
    }

    private static String getBaseTier6Name(int type) {
        switch (type) {
            case TYPE_STAFF:
                return "Nether Forged Staff";
            case TYPE_SPEAR:
                return "Nether Forged Polearm";
            case TYPE_SWORD:
                return "Nether Forged Sword";
            case TYPE_AXE:
                return "Nether Forged Axe";
            case TYPE_HELMET:
                return "Nether Forged Helmet";
            case TYPE_CHESTPLATE:
                return "Nether Forged Chestplate";
            case TYPE_LEGGINGS:
                return "Nether Forged Leggings";
            case TYPE_BOOTS:
                return "Nether Forged Boots";
            default:
                return "T6 Item";
        }
    }

    /**
     * Apply color to an item name based on tier
     *
     * @param name The item name
     * @param tier The item tier
     * @return Colored item name
     */
    public static String applyColorByTier(String name, int tier) {
        switch (tier) {
            case 1:
                return LEGACY_SERIALIZER.serialize(Component.text(name, NamedTextColor.WHITE));
            case 2:
                return LEGACY_SERIALIZER.serialize(Component.text(name, NamedTextColor.GREEN));
            case 3:
                return LEGACY_SERIALIZER.serialize(Component.text(name, NamedTextColor.AQUA));
            case 4:
                return LEGACY_SERIALIZER.serialize(Component.text(name, NamedTextColor.LIGHT_PURPLE));
            case 5:
                return LEGACY_SERIALIZER.serialize(Component.text(name, NamedTextColor.YELLOW));
            case 6:
                return LEGACY_SERIALIZER.serialize(Component.text(name, NamedTextColor.GOLD)); // Netherite color
            default:
                return name;
        }
    }

    /**
     * Get damage range from an item
     *
     * @param item The item to get damage from
     * @return int[2] containing min and max damage
     */
    public static int[] getDamageRange(ItemStack item) {
        int[] range = new int[]{1, 1};

        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("DMG:")) {
                    Matcher matcher = DAMAGE_PATTERN.matcher(line);
                    if (matcher.find()) {
                        try {
                            range[0] = Integer.parseInt(matcher.group(1));
                            range[1] = Integer.parseInt(matcher.group(2));
                        } catch (NumberFormatException e) {
                            LOGGER.log(Level.WARNING, "Error parsing damage range: " + line, e);
                        }
                        break;
                    }
                }
            }
        }

        return range;
    }

    /**
     * Get HP value from an item
     *
     * @param item The item to check
     * @return The HP value
     */
    public static int getHP(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("HP: +")) {
                    Matcher matcher = HP_PATTERN.matcher(line);
                    if (matcher.find()) {
                        try {
                            return Integer.parseInt(matcher.group(1));
                        } catch (NumberFormatException e) {
                            LOGGER.log(Level.WARNING, "Error parsing HP value: " + line, e);
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Get HP regeneration value from an item
     *
     * @param item The item to check
     * @return The HP regeneration value
     */
    public static int getHPRegen(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("HP REGEN:")) {
                    Matcher matcher = HP_REGEN_PATTERN.matcher(line);
                    if (matcher.find()) {
                        try {
                            return Integer.parseInt(matcher.group(1));
                        } catch (NumberFormatException e) {
                            LOGGER.log(Level.WARNING, "Error parsing HP regen value: " + line, e);
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Get energy regeneration value from an item
     *
     * @param item The item to check
     * @return The energy regeneration value
     */
    public static int getEnergyRegen(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("ENERGY REGEN:")) {
                    Matcher matcher = ENERGY_PATTERN.matcher(line);
                    if (matcher.find()) {
                        try {
                            return Integer.parseInt(matcher.group(1));
                        } catch (NumberFormatException e) {
                            LOGGER.log(Level.WARNING, "Error parsing energy regen value: " + line, e);
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Check if an item has HP regeneration
     *
     * @param item The item to check
     * @return true if the item has HP regeneration
     */
    public static boolean hasHPRegen(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("HP REGEN:")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if an item has energy regeneration
     *
     * @param item The item to check
     * @return true if the item has energy regeneration
     */
    public static boolean hasEnergyRegen(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("ENERGY REGEN:")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if an item is protected (from ItemAPI compatibility)
     */
    public static boolean isProtected(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        // Check NBT data first
        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey("protected") && nbt.getBoolean("protected")) {
            return true;
        }

        // Check lore if NBT doesn't have it
        if (item.getItemMeta().hasLore()) {
            for (String line : item.getItemMeta().getLore()) {
                if (line.contains(LEGACY_SERIALIZER.serialize(Component.text("Protected", NamedTextColor.GREEN)))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets a stat value from an item's lore by searching for a specific pattern
     *
     * @param item         The item to check
     * @param statPrefix   The prefix text of the stat to search for
     * @param valuePattern The regex pattern to extract the numeric value
     * @return The stat value or 0 if not found
     */
    public static int getStatFromLore(ItemStack item, String statPrefix, Pattern valuePattern) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains(statPrefix)) {
                    Matcher matcher = valuePattern.matcher(line);
                    if (matcher.find()) {
                        try {
                            return Integer.parseInt(matcher.group(1));
                        } catch (NumberFormatException e) {
                            LOGGER.log(Level.WARNING, "Error parsing stat value: " + line, e);
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Checks if an item has a specific stat in its lore
     *
     * @param item     The item to check
     * @param statText The text to search for
     * @return true if the stat is found
     */
    public static boolean hasStatInLore(ItemStack item, String statText) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains(statText)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get all stats from an item as a formatted list
     *
     * @param item The item to analyze
     * @return List of all stats as strings
     */
    public static List<String> getAllItemStats(ItemStack item) {
        List<String> stats = new ArrayList<>();

        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return stats;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            stats.add(LEGACY_SERIALIZER.serialize(Component.text("Item: ", NamedTextColor.YELLOW)) + meta.getDisplayName());

            if (isWeapon(item)) {
                int[] damage = getDamageRange(item);
                stats.add(LEGACY_SERIALIZER.serialize(Component.text("Damage: " + damage[0] + "-" + damage[1], NamedTextColor.RED)));

                // Add more weapon-specific stats
                if (hasStatInLore(item, "PURE DMG")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Pure Damage: Yes", NamedTextColor.RED)));
                }
                if (hasStatInLore(item, "CRITICAL HIT")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Critical Hit: Yes", NamedTextColor.RED)));
                }
                if (hasStatInLore(item, "LIFE STEAL")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Life Steal: Yes", NamedTextColor.RED)));
                }
                if (hasStatInLore(item, "FIRE DMG")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Element: Fire", NamedTextColor.RED)));
                } else if (hasStatInLore(item, "POISON DMG")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Element: Poison", NamedTextColor.RED)));
                } else if (hasStatInLore(item, "ICE DMG")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Element: Ice", NamedTextColor.RED)));
                }
            } else if (isArmor(item)) {
                int hp = getHP(item);
                stats.add(LEGACY_SERIALIZER.serialize(Component.text("HP: +" + hp, NamedTextColor.RED)));

                if (hasHPRegen(item)) {
                    int hpRegen = getHPRegen(item);
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("HP Regen: +" + hpRegen + "/s", NamedTextColor.RED)));
                } else if (hasEnergyRegen(item)) {
                    int energyRegen = getEnergyRegen(item);
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Energy Regen: +" + energyRegen + "%", NamedTextColor.RED)));
                }

                // Add more armor-specific stats
                if (hasStatInLore(item, "DODGE")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Dodge: Yes", NamedTextColor.RED)));
                }
                if (hasStatInLore(item, "BLOCK")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Block: Yes", NamedTextColor.RED)));
                }
                if (hasStatInLore(item, "THORNS")) {
                    stats.add(LEGACY_SERIALIZER.serialize(Component.text("Thorns: Yes", NamedTextColor.RED)));
                }
            }

            // Add enhancement level
            int plusLevel = Enchants.getPlus(item);
            if (plusLevel > 0) {
                stats.add(LEGACY_SERIALIZER.serialize(Component.text("Enhancement: +" + plusLevel, NamedTextColor.GOLD)));
            }

            // Add protection status
            if (isProtected(item)) {
                stats.add(LEGACY_SERIALIZER.serialize(Component.text("Protected: Yes", NamedTextColor.GREEN)));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting item stats", e);
        }

        return stats;
    }

    /**
     * Create a deep copy of an item
     *
     * @param item The item to copy
     * @return A deep copy of the item
     */
    public static ItemStack copyItem(ItemStack item) {
        if (item == null) {
            return null;
        }

        try {
            return item.clone();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error copying item", e);
            return item;
        }
    }

    /**
     * Get a descriptive string about what orb would do to this item
     *
     * @param item        The item to describe
     * @param isLegendary Whether describing a legendary orb
     * @return Description of potential effects
     */
    public static String getOrbEffectDescription(ItemStack item, boolean isLegendary) {
        StringBuilder description = new StringBuilder();

        try {
            if (item == null || !isValidItemForOrb(item)) {
                return "This item cannot be modified by orbs.";
            }

            description.append(LEGACY_SERIALIZER.serialize(Component.text("Using ", NamedTextColor.YELLOW)));

            if (isLegendary) {
                description.append("a Legendary Orb");

                int plusLevel = Enchants.getPlus(item);
                if (plusLevel < 4) {
                    description.append(LEGACY_SERIALIZER.serialize(Component.text(" will increase the item to at least +4", NamedTextColor.YELLOW)));
                }
            } else {
                description.append("an Orb");
            }

            description.append(LEGACY_SERIALIZER.serialize(Component.text(" will randomize this item's stats.", NamedTextColor.YELLOW)));

            if (isWeapon(item)) {
                description.append("\nPossible weapon stats: damage, accuracy, life steal, critical hit, elemental damage.");
            } else if (isArmor(item)) {
                description.append("\nPossible armor stats: HP, regen, dodge, block, thorns, attributes (STR, DEX, INT, VIT).");
            }

            if (isProtected(item)) {
                description.append("\n").append(LEGACY_SERIALIZER.serialize(Component.text("Item is protected and won't be destroyed on failed enhancement.", NamedTextColor.GREEN)));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error generating orb effect description", e);
            description = new StringBuilder("This item can be modified by orbs.");
        }

        return description.toString();
    }
}