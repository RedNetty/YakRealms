package com.rednetty.server.utils.inventory;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inventory utilities for Spigot 1.20.4 with item validation and alignment-based retention logic.
 */
public class InventoryUtils {

    // Material Constants

    // Armor materials for Spigot 1.20.4
    private static final Set<Material> ARMOR_MATERIALS = EnumSet.of(
            // Helmets
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET,
            Material.DIAMOND_HELMET, Material.GOLDEN_HELMET, Material.NETHERITE_HELMET,
            Material.TURTLE_HELMET,

            // Chestplates
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.GOLDEN_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
            Material.ELYTRA,

            // Leggings
            Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS,
            Material.DIAMOND_LEGGINGS, Material.GOLDEN_LEGGINGS, Material.NETHERITE_LEGGINGS,

            // Boots
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
            Material.DIAMOND_BOOTS, Material.GOLDEN_BOOTS, Material.NETHERITE_BOOTS
    );

    // Weapon materials for Spigot 1.20.4
    private static final Set<Material> WEAPON_MATERIALS = EnumSet.of(
            // Swords
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.DIAMOND_SWORD, Material.GOLDEN_SWORD, Material.NETHERITE_SWORD,

            // Axes
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.DIAMOND_AXE, Material.GOLDEN_AXE, Material.NETHERITE_AXE,

            // Pickaxes
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.DIAMOND_PICKAXE, Material.GOLDEN_PICKAXE, Material.NETHERITE_PICKAXE,

            // Shovels
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
            Material.DIAMOND_SHOVEL, Material.GOLDEN_SHOVEL, Material.NETHERITE_SHOVEL,

            // Hoes
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.DIAMOND_HOE, Material.GOLDEN_HOE, Material.NETHERITE_HOE,

            // Ranged
            Material.BOW, Material.CROSSBOW, Material.TRIDENT
    );

    // Tool materials for Spigot 1.20.4
    private static final Set<Material> TOOL_MATERIALS = EnumSet.of(
            // All pickaxes
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.DIAMOND_PICKAXE, Material.GOLDEN_PICKAXE, Material.NETHERITE_PICKAXE,

            // Fishing tools
            Material.FISHING_ROD,

            // Other tools
            Material.FLINT_AND_STEEL, Material.COMPASS, Material.CLOCK, Material.SHEARS
    );

    // Quest item materials
    private static final Set<Material> QUEST_MATERIALS = EnumSet.of(
            Material.INK_SAC, // Gem containers
            Material.BOOK, Material.WRITTEN_BOOK, Material.KNOWLEDGE_BOOK,
            Material.MAP, Material.FILLED_MAP
    );

    // Neutral alignment constants
    private static final int NEUTRAL_WEAPON_DROP_PERCENTAGE = 50;
    private static final int NEUTRAL_ARMOR_PIECE_DROP_PERCENTAGE = 25;

    // Core Inventory Methods

    public static List<ItemStack> getAllPlayerItems(Player player) {
        if (player == null) {
            return new ArrayList<>();
        }

        List<ItemStack> allItems = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();

        try {
            // Collect from main inventory slots (excludes armor slots)
            ItemStack[] contents = inventory.getStorageContents(); // Gets only storage, not armor
            if (contents != null) {
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (isValidItem(item)) {
                        allItems.add(item.clone());
                    }
                }
            }

            // Collect worn armor separately
            ItemStack[] armorContents = inventory.getArmorContents();
            if (armorContents != null) {
                String[] armorSlots = {"boots", "leggings", "chestplate", "helmet"};
                for (int i = 0; i < armorContents.length; i++) {
                    ItemStack armor = armorContents[i];
                    if (isValidItem(armor)) {
                        allItems.add(armor.clone());
                    }
                }
            }

            // Collect offhand item
            ItemStack offhand = inventory.getItemInOffHand();
            if (isValidItem(offhand)) {
                allItems.add(offhand.clone());
            }

            return allItems;

        } catch (Exception e) {
            YakRealms.error("Error collecting player items", e);
            return allItems; // Return what we managed to collect
        }
    }

    // Item Validation

    public static boolean isValidItem(ItemStack item) {
        return item != null &&
                item.getType() != null &&
                item.getType() != Material.AIR &&
                item.getAmount() > 0;
    }

    public static ItemStack createSafeCopy(ItemStack original) {
        if (!isValidItem(original)) {
            return null;
        }

        try {
            ItemStack copy = original.clone();

            // Validate the copy
            if (!isValidItem(copy)) {
                YakRealms.warn("Item became invalid after cloning: " + original.getType());
                return null;
            }

            // Additional safety checks
            if (copy.getType() != original.getType()) {
                YakRealms.warn("Item type changed during cloning: " + original.getType() + " -> " + copy.getType());
                return null;
            }

            if (copy.getAmount() != original.getAmount()) {
                YakRealms.warn("Item amount changed during cloning: " + original.getAmount() + " -> " + copy.getAmount());
                copy.setAmount(original.getAmount());
            }

            return copy;

        } catch (Exception e) {
            YakRealms.error("Error creating safe copy of item: " + original.getType(), e);
            return null;
        }
    }

    // Item Type Detection

    public static boolean isArmorItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }
        return ARMOR_MATERIALS.contains(item.getType());
    }

    public static boolean isWeaponItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }
        return WEAPON_MATERIALS.contains(item.getType());
    }

    public static boolean isToolItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }
        return TOOL_MATERIALS.contains(item.getType());
    }

    /**
     * Get armor slot index for an item type
     *
     * @return 0=boots, 1=leggings, 2=chestplate, 3=helmet, -1=not armor
     */
    public static int getArmorSlot(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return -1;
        }

        String typeName = item.getType().name();

        if (typeName.endsWith("_BOOTS")) return 0;
        if (typeName.endsWith("_LEGGINGS")) return 1;
        if (typeName.endsWith("_CHESTPLATE")) return 2;
        if (typeName.endsWith("_HELMET")) return 3;

        // Special case for turtle helmet
        if (item.getType() == Material.TURTLE_HELMET) return 3;

        // Special case for elytra
        if (item.getType() == Material.ELYTRA) return 2;

        return -1;
    }

    // Special Item Detection

    public static boolean isPermanentUntradeable(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }

        try {
            if (!item.hasItemMeta()) {
                return false;
            }

            ItemMeta meta = item.getItemMeta();

            // Check display name for permanent markers
            if (meta.hasDisplayName()) {
                String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
                if (displayName.contains("permanent") || displayName.contains("bound") ||
                        displayName.contains("soulbound") || displayName.contains("untradeable")) {
                    return true;
                }
            }

            // Check lore for permanent markers
            if (meta.hasLore()) {
                List<String> lore = meta.getLore();
                for (String line : lore) {
                    String cleanLine = ChatColor.stripColor(line).toLowerCase();
                    if (cleanLine.contains("permanent") || cleanLine.contains("untradeable") ||
                            cleanLine.contains("bound") || cleanLine.contains("soulbound") ||
                            cleanLine.contains("cannot be dropped") || cleanLine.contains("never lost")) {
                        return true;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            YakRealms.error("Error checking if item is permanent untradeable", e);
            return false;
        }
    }

    public static boolean isQuestItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }

        try {
            // Check for gem containers
            if (isGemContainer(item)) {
                return true;
            }

            // Check for quest books and maps
            if (QUEST_MATERIALS.contains(item.getType())) {
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
                    if (displayName.contains("quest") || displayName.contains("mission") ||
                            displayName.contains("task") || displayName.contains("journal")) {
                        return true;
                    }
                }
            }

            // Check lore for quest markers
            if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                List<String> lore = item.getItemMeta().getLore();
                for (String line : lore) {
                    String cleanLine = ChatColor.stripColor(line).toLowerCase();
                    if (cleanLine.contains("quest item") || cleanLine.contains("mission item") ||
                            cleanLine.contains("quest related") || cleanLine.contains("story item")) {
                        return true;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            YakRealms.error("Error checking if item is quest item", e);
            return false;
        }
    }

    public static boolean isGemContainer(ItemStack item) {
        if (!isValidItem(item) || item.getType() != Material.INK_SAC) {
            return false;
        }

        try {
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
                return false;
            }

            String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
            return displayName.contains("gem container") || displayName.contains("gem pouch") ||
                    displayName.contains("gem bag") || displayName.contains("gem holder");

        } catch (Exception e) {
            YakRealms.error("Error checking if item is gem container", e);
            return false;
        }
    }

    // Alignment-Based Item Retention

    public static boolean determineIfItemShouldBeKept(ItemStack item, String alignment, Player player,
                                                      ItemStack firstHotbarItem, boolean neutralShouldDropArmor,
                                                      boolean neutralShouldDropWeapon) {
        if (!isValidItem(item)) {
            return false;
        }

        // ALWAYS keep permanent untradeable items regardless of alignment
        if (isPermanentUntradeable(item)) {
            YakRealms.log("ALWAYS KEEPING: Permanent untradeable - " + getItemDisplayName(item));
            return true;
        }

        // ALWAYS keep quest items regardless of alignment
        if (isQuestItem(item)) {
            YakRealms.log("ALWAYS KEEPING: Quest item - " + getItemDisplayName(item));
            return true;
        }

        switch (alignment) {
            case "LAWFUL":
                return shouldKeepItemLawful(item, firstHotbarItem);

            case "NEUTRAL":
                // FIXED: For neutral, this method should NOT be used for per-armor-piece rolls
                // This is only for compatibility with legacy calls
                return shouldKeepItemNeutralLegacy(item, firstHotbarItem, neutralShouldDropArmor, neutralShouldDropWeapon);

            case "CHAOTIC":
                // Chaotic players lose everything except permanent/quest items (already handled above)
                YakRealms.log("CHAOTIC: Dropping - " + getItemDisplayName(item));
                return false;

            default:
                YakRealms.warn("Unknown alignment: " + alignment + ", defaulting to lawful rules");
                return shouldKeepItemLawful(item, firstHotbarItem);
        }
    }

    public static boolean determineIfNeutralItemShouldBeKept(ItemStack item, ItemStack firstHotbarItem) {
        if (!isValidItem(item)) {
            return false;
        }

        // ALWAYS keep permanent untradeable items
        if (isPermanentUntradeable(item)) {
            YakRealms.log("NEUTRAL: Keeping permanent untradeable - " + getItemDisplayName(item));
            return true;
        }

        // ALWAYS keep quest items
        if (isQuestItem(item)) {
            YakRealms.log("NEUTRAL: Keeping quest item - " + getItemDisplayName(item));
            return true;
        }

        // Handle primary weapon: 50% chance to keep
        if (firstHotbarItem != null && isSameItem(item, firstHotbarItem)) {
            Random random = new Random();
            boolean shouldKeep = random.nextInt(100) >= NEUTRAL_WEAPON_DROP_PERCENTAGE;
            YakRealms.log("NEUTRAL: Weapon " + (shouldKeep ? "kept" : "dropped") + " - " + getItemDisplayName(item));
            return shouldKeep;
        }

        // Handle armor: Each piece gets its own 25% drop chance (75% keep chance)
        if (isArmorItem(item)) {
            Random random = new Random();
            boolean shouldKeep = random.nextInt(100) >= NEUTRAL_ARMOR_PIECE_DROP_PERCENTAGE;
            YakRealms.log("NEUTRAL: Armor " + (shouldKeep ? "kept" : "dropped") + " - " + getItemDisplayName(item));
            return shouldKeep;
        }

        // All other items are dropped for neutral players
        YakRealms.log("NEUTRAL: Dropping other item - " + getItemDisplayName(item));
        return false;
    }

    private static boolean shouldKeepItemLawful(ItemStack item, ItemStack firstHotbarItem) {
        // Keep all armor
        if (isArmorItem(item)) {
            YakRealms.log("LAWFUL: Keeping armor - " + getItemDisplayName(item));
            return true;
        }

        // Keep first hotbar item (weapon)
        if (firstHotbarItem != null && isSameItem(item, firstHotbarItem)) {
            YakRealms.log("LAWFUL: Keeping first hotbar item - " + getItemDisplayName(item));
            return true;
        }

        YakRealms.log("LAWFUL: Dropping - " + getItemDisplayName(item));
        return false;
    }

    private static boolean shouldKeepItemNeutralLegacy(ItemStack item, ItemStack firstHotbarItem,
                                                       boolean neutralShouldDropArmor, boolean neutralShouldDropWeapon) {

        // Armor logic: Use pre-calculated result (NOT recommended - use per-piece instead)
        if (isArmorItem(item)) {
            boolean keep = !neutralShouldDropArmor;
            YakRealms.log("NEUTRAL (LEGACY): Armor " + (keep ? "kept" : "dropped") + " - " + getItemDisplayName(item));
            return keep;
        }

        // First hotbar item (weapon) logic: Use pre-calculated result
        if (firstHotbarItem != null && isSameItem(item, firstHotbarItem)) {
            boolean keep = !neutralShouldDropWeapon;
            YakRealms.log("NEUTRAL (LEGACY): Weapon " + (keep ? "kept" : "dropped") + " - " + getItemDisplayName(item));
            return keep;
        }

        // All other items are dropped for neutral players
        YakRealms.log("NEUTRAL (LEGACY): Dropping other item - " + getItemDisplayName(item));
        return false;
    }

    // Item Comparison

    public static boolean isSameItem(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) {
            return false;
        }

        if (item1.getType() != item2.getType()) {
            return false;
        }

        if (item1.getAmount() != item2.getAmount()) {
            return false;
        }

        // Compare durability
        if (item1.getDurability() != item2.getDurability()) {
            return false;
        }

        // Compare display names
        String name1 = getItemDisplayName(item1);
        String name2 = getItemDisplayName(item2);
        if (!name1.equals(name2)) {
            return false;
        }

        // Compare enchantments
        return Objects.equals(item1.getEnchantments(), item2.getEnchantments());
    }

    // Display and Formatting

    public static String getItemDisplayName(ItemStack item) {
        if (!isValidItem(item)) {
            return "Invalid Item";
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String displayName = meta.getDisplayName();
                if (displayName != null && !displayName.trim().isEmpty()) {
                    return ChatColor.stripColor(displayName);
                }
            }

            // Fallback to formatted material name
            return formatMaterialName(item.getType().name());

        } catch (Exception e) {
            YakRealms.error("Error getting item display name", e);
            return item.getType().name();
        }
    }

    private static String formatMaterialName(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return "Unknown";
        }

        return Arrays.stream(materialName.toLowerCase().split("_"))
                .map(word -> word.isEmpty() ? "" :
                        Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    // Utility Methods

    public static void clearPlayerInventory(Player player) {
        if (player == null) {
            return;
        }

        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            if (player.getInventory().getItemInOffHand() != null) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }
            player.getEnderChest().clear();
            YakRealms.log("Cleared all items for player: " + player.getName());
        } catch (Exception e) {
            YakRealms.error("Error clearing player items", e);
        }
    }
}