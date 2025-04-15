package com.rednetty.server.mechanics.item.scroll;

import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

    /**
     * Initialize the ItemAPI
     */
    public static void initialize() {
        scrollGenerator = new ScrollGenerator();
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
     *
     * @param item The item to check
     * @return true if the item is armor
     */
    public static boolean isArmorItem(ItemStack item) {
        if (item == null) return false;

        String type = item.getType().name();
        return type.endsWith("_HELMET") ||
                type.endsWith("_CHESTPLATE") ||
                type.endsWith("_LEGGINGS") ||
                type.endsWith("_BOOTS");
    }

    /**
     * Check if an item is a weapon
     *
     * @param item The item to check
     * @return true if the item is a weapon
     */
    public static boolean isWeaponItem(ItemStack item) {
        if (item == null) return false;

        String type = item.getType().name();
        // Support both old and new naming conventions
        return type.endsWith("_SWORD") ||
                type.endsWith("_AXE") ||
                type.endsWith("_HOE") ||
                type.endsWith("_SHOVEL") ||
                type.endsWith("_SPADE"); // For older versions
    }

    /**
     * Gets the correct tier of an item for orb processing
     * This fixes the bug where tiers were detected incorrectly
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

            if (displayName.contains(ChatColor.BLUE.toString())) {
                return 6; // Frozen/T6
            }

            if (displayName.contains(ChatColor.YELLOW.toString())) {
                return 5; // Gold/Yellow/Legendary/T5
            }

            if (displayName.contains(ChatColor.LIGHT_PURPLE.toString())) {
                return 4; // Diamond/Purple/T4
            }

            if (displayName.contains(ChatColor.AQUA.toString())) {
                return 3; // Iron/Aqua/T3
            }

            if (displayName.contains(ChatColor.GREEN.toString())) {
                return 2; // Stone/Chainmail/Green/T2
            }

            if (displayName.contains(ChatColor.WHITE.toString())) {
                return 1; // Wooden/Leather/White/T1
            }
        }

        // Fallback to material-based detection
        String material = item.getType().name();

        // Check for blue leather or diamond (T6 - Frozen)
        if ((material.contains("LEATHER_") && isBlueLeather(item))) {
            return 6;
        }

        // Check for gold (T5)
        if (material.contains("GOLD_") || material.contains("GOLDEN_")) {
            return 5;
        }

        // Check for regular diamond (T4)
        if (material.contains("DIAMOND_")) {
            return 4;
        }

        // Check for iron (T3)
        if (material.contains("IRON_")) {
            return 3;
        }

        // Check for chainmail/stone (T2)
        if (material.contains("CHAINMAIL_") || material.contains("STONE_")) {
            return 2;
        }

        // Check for leather/wooden (T1)
        if ((material.contains("LEATHER_") && !isBlueLeather(item)) ||
                material.contains("WOOD_") || material.contains("WOODEN_")) {
            return 1;
        }

        return 1;
    }

    /**
     * Check if an item is blue leather armor (frozen tier)
     *
     * @param item The item to check
     * @return true if the item is blue leather armor
     */
    public static boolean isBlueLeather(ItemStack item) {
        if (item == null) {
            return false;
        }

        String material = item.getType().name();
        if (!material.contains("LEATHER_") && !material.contains("WOOD_")) {
            return false;
        }

        return item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().contains(ChatColor.BLUE.toString());
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
            return itemTier == scrollTier;
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
     */
    public static boolean isValidArmorForScroll(ItemStack armor, ItemStack scroll) {
        if (armor == null || !armor.hasItemMeta() || scroll == null || !scroll.hasItemMeta()) {
            return false;
        }

        String armorType = armor.getType().name();
        String scrollName = scroll.getItemMeta().getDisplayName();

        // Check if it's armor
        if (!isArmorItem(armor)) {
            return false;
        }

        // Match by material and scroll type
        boolean isLeather = armorType.startsWith("LEATHER_");
        boolean isChainmail = armorType.startsWith("CHAINMAIL_");
        boolean isIron = armorType.startsWith("IRON_");
        boolean isDiamond = armorType.startsWith("DIAMOND_");
        boolean isGolden = armorType.startsWith("GOLDEN_") || armorType.startsWith("GOLD_");
        boolean isFrozen = isBlueLeather(armor) ||
                (isDiamond && armor.getItemMeta().getDisplayName().contains(ChatColor.BLUE.toString()));

        if (isLeather && !isFrozen && scrollName.contains("Leather")) return true;
        if (isChainmail && scrollName.contains("Chainmail")) return true;
        if (isIron && scrollName.contains("Iron")) return true;
        if (isDiamond && !isFrozen && scrollName.contains("Diamond")) return true;
        if (isGolden && scrollName.contains("Gold")) return true;
        if (isFrozen && scrollName.contains("Frozen")) return true;

        return false;
    }

    /**
     * Checks if a weapon item is valid for the given scroll
     */
    public static boolean isValidWeaponForScroll(ItemStack weapon, ItemStack scroll) {
        if (weapon == null || !weapon.hasItemMeta() || scroll == null || !scroll.hasItemMeta()) {
            return false;
        }

        String weaponType = weapon.getType().name();
        String scrollName = scroll.getItemMeta().getDisplayName();
        String itemName = weapon.getItemMeta().getDisplayName();

        // Check if it's a weapon
        if (!isWeaponItem(weapon)) {
            return false;
        }

        // Match by material and scroll type
        boolean isWooden = weaponType.startsWith("WOODEN_") || weaponType.startsWith("WOOD_");
        boolean isStone = weaponType.startsWith("STONE_");
        boolean isIron = weaponType.startsWith("IRON_");
        boolean isDiamond = weaponType.startsWith("DIAMOND_");
        boolean isGolden = weaponType.startsWith("GOLDEN_") || weaponType.startsWith("GOLD_");
        boolean isFrozen = isDiamond && itemName.contains(ChatColor.BLUE.toString());

        if (isWooden && scrollName.contains("Wooden")) return true;
        if (isStone && scrollName.contains("Stone")) return true;
        if (isIron && scrollName.contains("Iron")) return true;
        if (isDiamond && !isFrozen && scrollName.contains("Diamond")) return true;
        if (isGolden && scrollName.contains("Gold")) return true;
        if (isFrozen && scrollName.contains("Frozen")) return true;

        return false;
    }
}