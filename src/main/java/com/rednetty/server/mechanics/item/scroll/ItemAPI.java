package com.rednetty.server.mechanics.item.scroll;

import com.rednetty.server.YakRealms;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility methods for scroll interactions with items
 */
public class ItemAPI {
    private static ScrollGenerator scrollGenerator;
    private static final Random random = new Random();

    private static final String PROTECTION_SCROLL_KEY = "itemProtectionScroll";
    private static final String PROTECTION_TIER_KEY = "itemProtectionScrollTier";
    private static final String ARMOR_ENHANCEMENT_KEY = "armorEnhancement";
    private static final String WEAPON_ENHANCEMENT_KEY = "weaponEnhancement";
    private static final String PROTECTED_KEY = "protected";

    // Namespaced keys for persistent data
    private static NamespacedKey keyRarity;
    private static NamespacedKey keyTier;
    private static NamespacedKey keyItemType;

    /**
     * Initialize the ItemAPI
     */
    public static void initialize() {
        scrollGenerator = new ScrollGenerator();

        // Initialize namespaced keys
        YakRealms plugin = YakRealms.getInstance();
        keyRarity = new NamespacedKey(plugin, "item_rarity");
        keyTier = new NamespacedKey(plugin, "item_tier");
        keyItemType = new NamespacedKey(plugin, "item_type");
    }

    public static ItemStack setUntradable(ItemStack item){
        ItemMeta meta = item.getItemMeta();

        List<String> lore = meta.getLore();
        lore.add("");
        lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + "Untradeable");
        meta.setLore(lore);
        item.setItemMeta(meta);

        NBTAccessor nbt = new NBTAccessor(item);
        nbt.setBoolean("Tradeable", false);

        return nbt.update();
    }
    /**
     * Get the scroll generator
     *
     * @return The scroll generator
     */
    public static ScrollGenerator getScrollGenerator() {
        if (scrollGenerator == null) {
            scrollGenerator = new ScrollGenerator();
        }
        return scrollGenerator;
    }

    /**
     * Check if an item is a protection scroll
     *
     * @param item The item to check
     * @return true if the item is a protection scroll
     */
    public static boolean isProtectionScroll(ItemStack item) {
        if (item == null) {
            return false;
        }

        // Support both PAPER and MAP for backward compatibility
        if (item.getType() != Material.PAPER && item.getType() != Material.MAP) {
            return false;
        }

        // First check NBT data
        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey(PROTECTION_SCROLL_KEY) && "true".equals(nbt.getString(PROTECTION_SCROLL_KEY))) {
            return true;
        }

        // Fallback to checking name and lore (for compatibility with legacy items)
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = item.getItemMeta().getDisplayName();
            return name.contains("WHITE SCROLL") || name.contains("Protect");
        }

        return false;
    }

    /**
     * Check if an item is an armor enhancement scroll
     *
     * @param item The item to check
     * @return true if the item is an armor enhancement scroll
     */
    public static boolean isArmorEnhancementScroll(ItemStack item) {
        if (item == null) {
            return false;
        }

        // Support both PAPER and MAP for backward compatibility
        if (item.getType() != Material.PAPER && item.getType() != Material.MAP) {
            return false;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        // Check NBT data first
        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey("scrollType") && "armor_enhancement".equals(nbt.getString("scrollType"))) {
            return true;
        }

        // Fallback to name check
        String name = item.getItemMeta().getDisplayName();
        return name.contains("Armor") && (name.contains("Scroll") || name.contains("Enchant"));
    }

    /**
     * Check if an item is a weapon enhancement scroll
     *
     * @param item The item to check
     * @return true if the item is a weapon enhancement scroll
     */
    public static boolean isWeaponEnhancementScroll(ItemStack item) {
        if (item == null) {
            return false;
        }

        // Support both PAPER and MAP for backward compatibility
        if (item.getType() != Material.PAPER && item.getType() != Material.MAP) {
            return false;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        // Check NBT data first
        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey("scrollType") && "weapon_enhancement".equals(nbt.getString("scrollType"))) {
            return true;
        }

        // Fallback to name check
        String name = item.getItemMeta().getDisplayName();
        return name.contains("Weapon") && (name.contains("Scroll") || name.contains("Enchant"));
    }

    /**
     * Check if an item is any type of enhancement scroll
     *
     * @param item The item to check
     * @return true if the item is an enhancement scroll
     */
    public static boolean isEnhancementScroll(ItemStack item) {
        return isArmorEnhancementScroll(item) || isWeaponEnhancementScroll(item);
    }

    /**
     * Check if an item is protected
     *
     * @param item The item to check
     * @return true if the item is protected
     */
    public static boolean isProtected(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        // Check both lore and NBT for compatibility
        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey(PROTECTED_KEY) && nbt.getBoolean(PROTECTED_KEY)) {
            return true;
        }

        // Check lore if NBT doesn't have it
        if (item.getItemMeta().hasLore()) {
            for (String line : item.getItemMeta().getLore()) {
                if (line.contains(ChatColor.GREEN + "Protected")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Apply protection to an item
     *
     * @param item The item to protect
     * @return The protected item
     */
    public static ItemStack makeProtected(ItemStack item) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Add protection lore
        boolean hasProtectionLine = false;
        for (String line : lore) {
            if (line.contains(ChatColor.GREEN + "Protected")) {
                hasProtectionLine = true;
                break;
            }
        }

        if (!hasProtectionLine) {
            lore.add(ChatColor.GREEN + "Protected");
        }

        meta.setLore(lore);
        result.setItemMeta(meta);

        // Add protection NBT
        NBTAccessor nbt = new NBTAccessor(result);
        nbt.setBoolean(PROTECTED_KEY, true);

        return nbt.update();
    }

    /**
     * Removes protection from an item
     *
     * @param item The item to remove protection from
     * @return The unprotected item
     */
    public static ItemStack removeProtection(ItemStack item) {
        if (!isProtected(item)) {
            return item;
        }

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();

        if (meta.hasLore()) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.removeIf(line -> line.contains(ChatColor.GREEN + "Protected"));
            meta.setLore(lore);
            result.setItemMeta(meta);
        }

        // Remove NBT protection flag
        NBTAccessor nbt = new NBTAccessor(result);
        nbt.setBoolean(PROTECTED_KEY, false);

        return nbt.update();
    }

    /**
     * Get the enhancement level of an item
     *
     * @param item The item to check
     * @return The enhancement level, or 0 if none
     */
    public static int getEnhancementLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return 0;
        }

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (name.startsWith("[+")) {
            try {
                return Integer.parseInt(name.split("\\[\\+")[1].split("\\]")[0]);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Check if an item is armor
     * Updated to handle custom items from DropsManager
     *
     * @param item The item to check
     * @return true if the item is armor
     */
    public static boolean isArmorItem(ItemStack item) {
        if (item == null) return false;

        // First check if it's a custom armor item (from DropsManager)
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(keyItemType, PersistentDataType.INTEGER)) {
            int itemType = item.getItemMeta().getPersistentDataContainer().get(keyItemType, PersistentDataType.INTEGER);
            // Item types 5-8 are armor (helmet, chestplate, leggings, boots)
            return itemType >= 5 && itemType <= 8;
        }

        // Fallback to material-based detection
        String type = item.getType().name();
        return type.endsWith("_HELMET") ||
                type.endsWith("_CHESTPLATE") ||
                type.endsWith("_LEGGINGS") ||
                type.endsWith("_BOOTS");
    }

    /**
     * Check if an item is a weapon
     * Updated to handle custom items from DropsManager
     *
     * @param item The item to check
     * @return true if the item is a weapon
     */
    public static boolean isWeaponItem(ItemStack item) {
        if (item == null) return false;

        // First check if it's a custom weapon item (from DropsManager)
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(keyItemType, PersistentDataType.INTEGER)) {
            int itemType = item.getItemMeta().getPersistentDataContainer().get(keyItemType, PersistentDataType.INTEGER);
            // Item types 1-4 are weapons (staff, spear, sword, axe)
            return itemType >= 1 && itemType <= 4;
        }

        // Fallback to material-based detection
        String type = item.getType().name();
        return type.endsWith("_SWORD") ||
                type.endsWith("_AXE") ||
                type.endsWith("_HOE") ||
                type.endsWith("_SHOVEL") ||
                type.endsWith("_SPADE"); // For older versions
    }

    /**
     * Gets the correct tier of an item for scroll processing
     * Updated for Tier 6 Netherite integration and custom items
     *
     * @param item The item to check
     * @return The correct tier (1-6) or 0 if not a valid tier item
     */
    public static int getItemTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        // First check if it's a custom item with tier data
        if (item.getItemMeta().getPersistentDataContainer().has(keyTier, PersistentDataType.INTEGER)) {
            return item.getItemMeta().getPersistentDataContainer().get(keyTier, PersistentDataType.INTEGER);
        }

        // Check by display name color and content
        if (item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            String strippedName = ChatColor.stripColor(displayName).toLowerCase();

            // Tier 6 - Netherite (Gold color) - Check for netherite or special names
            if (displayName.contains(ChatColor.GOLD.toString()) ||
                    strippedName.contains("netherite") ||
                    strippedName.contains("nether forged") ||
                    strippedName.contains("world ender") ||
                    strippedName.contains("apocalypse")) {
                return 6;
            }

            // Tier 5 - Legendary (Yellow)
            if (displayName.contains(ChatColor.YELLOW.toString()) ||
                    strippedName.contains("legendary")) {
                return 5;
            }

            // Tier 4 - Ancient (Light Purple)
            if (displayName.contains(ChatColor.LIGHT_PURPLE.toString()) ||
                    strippedName.contains("ancient")) {
                return 4;
            }

            // Tier 3 - Magic (Aqua)
            if (displayName.contains(ChatColor.AQUA.toString()) ||
                    strippedName.contains("magic")) {
                return 3;
            }

            // Tier 2 - Chainmail/Stone (Green)
            if (displayName.contains(ChatColor.GREEN.toString())) {
                return 2;
            }

            // Tier 1 - Leather/Wood (White)
            if (displayName.contains(ChatColor.WHITE.toString())) {
                return 1;
            }
        }

        // Fallback to material-based detection
        String material = item.getType().name();

        // Tier 6 - Netherite
        if (material.contains("NETHERITE_")) {
            return 6;
        }

        // Tier 5 - Gold
        if (material.contains("GOLD_") || material.contains("GOLDEN_")) {
            return 5;
        }

        // Tier 4 - Diamond
        if (material.contains("DIAMOND_")) {
            return 4;
        }

        // Tier 3 - Iron
        if (material.contains("IRON_")) {
            return 3;
        }

        // Tier 2 - Chainmail/Stone
        if (material.contains("CHAINMAIL_") || material.contains("STONE_")) {
            return 2;
        }

        // Tier 1 - Leather/Wood
        if (material.contains("LEATHER_") || material.contains("WOOD_") || material.contains("WOODEN_")) {
            return 1;
        }

        return 1;
    }

    /**
     * Check if an item is blue leather armor (backwards compatibility)
     * Note: This method is deprecated as Tier 6 is now Netherite
     *
     * @param item The item to check
     * @return false - no longer used for Tier 6
     * @deprecated Tier 6 is now Netherite, not blue leather
     */
    @Deprecated
    public static boolean isBlueLeather(ItemStack item) {
        // This method is kept for backwards compatibility but always returns false
        // since Tier 6 is now Netherite, not blue leather
        return false;
    }

    /**
     * Check if an item and scroll are compatible for enhancement
     *
     * @param item   The item to check
     * @param scroll The scroll to check
     * @return true if the item and scroll are compatible
     */
    public static boolean canEnchant(ItemStack item, ItemStack scroll) {
        // For protection scrolls
        if (isProtectionScroll(scroll)) {
            int itemTier = getItemTier(item);
            if (itemTier == -1) return false;

            NBTAccessor scrollNBT = new NBTAccessor(scroll);
            if (!scrollNBT.hasKey(PROTECTION_TIER_KEY)) return false;

            int scrollTier = scrollNBT.getInt(PROTECTION_TIER_KEY);
            return itemTier == scrollTier + 1; // Protection tier is 0-based, item tier is 1-based
        }

        // For armor enhancement scrolls
        if (isArmorEnhancementScroll(scroll) && isArmorItem(item)) {
            return isValidArmorForScroll(item, scroll);
        }

        // For weapon enhancement scrolls
        if (isWeaponEnhancementScroll(scroll) && isWeaponItem(item)) {
            return isValidWeaponForScroll(item, scroll);
        }

        return false;
    }

    /**
     * Checks if an armor item is valid for the given scroll
     * Updated to handle both material-based and tier-based validation
     */
    public static boolean isValidArmorForScroll(ItemStack armor, ItemStack scroll) {
        if (armor == null || !armor.hasItemMeta() || scroll == null || !scroll.hasItemMeta()) {
            return false;
        }

        // Check if it's armor
        if (!isArmorItem(armor)) {
            return false;
        }

        int armorTier = getItemTier(armor);
        int scrollTier = getScrollTier(scroll);

        // First try tier-based matching
        if (armorTier > 0 && scrollTier > 0) {
            return armorTier == scrollTier;
        }

        // Fallback to legacy material-based matching
        String armorType = armor.getType().name();
        String scrollName = scroll.getItemMeta().getDisplayName();

        boolean isLeather = armorType.startsWith("LEATHER_");
        boolean isChainmail = armorType.startsWith("CHAINMAIL_");
        boolean isIron = armorType.startsWith("IRON_");
        boolean isDiamond = armorType.startsWith("DIAMOND_");
        boolean isGolden = armorType.startsWith("GOLDEN_") || armorType.startsWith("GOLD_");
        boolean isNetherite = armorType.startsWith("NETHERITE_");

        if (isLeather && scrollName.contains("Leather")) return true;
        if (isChainmail && scrollName.contains("Chainmail")) return true;
        if (isIron && scrollName.contains("Iron")) return true;
        if (isDiamond && scrollName.contains("Diamond")) return true;
        if (isGolden && scrollName.contains("Gold")) return true;
        return isNetherite && (scrollName.contains("Netherite") || scrollName.contains("Nether Forged"));
    }

    /**
     * Checks if a weapon item is valid for the given scroll
     * Updated to handle both material-based and tier-based validation, plus T6 naming
     */
    public static boolean isValidWeaponForScroll(ItemStack weapon, ItemStack scroll) {
        if (weapon == null || !weapon.hasItemMeta() || scroll == null || !scroll.hasItemMeta()) {
            return false;
        }

        // Check if it's a weapon
        if (!isWeaponItem(weapon)) {
            return false;
        }

        int weaponTier = getItemTier(weapon);
        int scrollTier = getScrollTier(scroll);

        // First try tier-based matching - this handles custom items properly
        if (weaponTier > 0 && scrollTier > 0) {
            return weaponTier == scrollTier;
        }

        // Fallback to legacy material-based matching
        String weaponType = weapon.getType().name();
        String scrollName = scroll.getItemMeta().getDisplayName();

        boolean isWooden = weaponType.startsWith("WOODEN_") || weaponType.startsWith("WOOD_");
        boolean isStone = weaponType.startsWith("STONE_");
        boolean isIron = weaponType.startsWith("IRON_");
        boolean isDiamond = weaponType.startsWith("DIAMOND_");
        boolean isGolden = weaponType.startsWith("GOLDEN_") || weaponType.startsWith("GOLD_");
        boolean isNetherite = weaponType.startsWith("NETHERITE_");

        if (isWooden && scrollName.contains("Wooden")) return true;
        if (isStone && scrollName.contains("Stone")) return true;
        if (isIron && scrollName.contains("Iron")) return true;
        if (isDiamond && scrollName.contains("Diamond")) return true;
        if (isGolden && scrollName.contains("Gold")) return true;
        return isNetherite && (scrollName.contains("Netherite") || scrollName.contains("Nether Forged"));
    }

    /**
     * Gets the tier of a scroll from its NBT data or name
     *
     * @param scroll The scroll to check
     * @return The tier of the scroll, or 0 if not found
     */
    private static int getScrollTier(ItemStack scroll) {
        if (scroll == null || !scroll.hasItemMeta()) {
            return 0;
        }

        // Check NBT data first
        NBTAccessor nbt = new NBTAccessor(scroll);
        if (nbt.hasKey("tier")) {
            return nbt.getInt("tier");
        }

        // Fallback to name-based detection
        String scrollName = scroll.getItemMeta().getDisplayName();
        if (scrollName.contains("Nether Forged") || scrollName.contains("Netherite")) return 6;
        if (scrollName.contains("Gold")) return 5;
        if (scrollName.contains("Diamond")) return 4;
        if (scrollName.contains("Iron")) return 3;
        if (scrollName.contains("Stone") || scrollName.contains("Chainmail")) return 2;
        if (scrollName.contains("Wooden") || scrollName.contains("Leather")) return 1;

        return 0;
    }
}