package com.rednetty.server.utils.inventory;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 *  InventoryUtils for Spigot 1.20.4
 * Consolidated inventory utility methods to prevent inconsistencies
 * between DeathMechanics and CombatLogoutMechanics
 * - Comprehensive item handling and validation
 * - Permanent untradeable item detection
 * - Quest item management
 * - Serialization utilities for database storage
 * - Bulletproof dupe prevention
 * -  Added equipped armor detection methods
 */
public class InventoryUtils {

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

    /**
     * ATOMIC: Get all items from player with deduplication and validation
     */
    public static List<ItemStack> getAllPlayerItems(Player player) {
        if (player == null) {
            return new ArrayList<>();
        }

        YakRealms.log("=== COLLECTING ALL PLAYER ITEMS ===");
        List<ItemStack> allItems = new ArrayList<>();
        Set<String> processedItems = new HashSet<>(); // Track processed items to prevent dupes

        try {
            // Main inventory (hotbar + main inventory) - 36 slots
            ItemStack[] mainInventory = player.getInventory().getContents();
            if (mainInventory != null) {
                for (int i = 0; i < Math.min(36, mainInventory.length); i++) { // Only first 36 slots
                    ItemStack item = mainInventory[i];
                    if (isValidItem(item)) {
                        String itemKey = createItemKey(item, "main_" + i);
                        if (!processedItems.contains(itemKey)) {
                            allItems.add(item.clone());
                            processedItems.add(itemKey);
                            YakRealms.log("Collected from main inventory slot " + i + ": " +
                                    getItemDisplayName(item) + " x" + item.getAmount());
                        }
                    }
                }
            }

            // Armor contents
            ItemStack[] armorContents = player.getInventory().getArmorContents();
            if (armorContents != null) {
                String[] armorSlots = {"boots", "leggings", "chestplate", "helmet"};
                for (int i = 0; i < armorContents.length; i++) {
                    ItemStack item = armorContents[i];
                    if (isValidItem(item)) {
                        String itemKey = createItemKey(item, "armor_" + armorSlots[i]);
                        if (!processedItems.contains(itemKey)) {
                            allItems.add(item.clone());
                            processedItems.add(itemKey);
                            YakRealms.log("Collected from armor slot " + armorSlots[i] + ": " +
                                    getItemDisplayName(item) + " x" + item.getAmount());
                        }
                    }
                }
            }

            // Offhand
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (isValidItem(offhand)) {
                String itemKey = createItemKey(offhand, "offhand");
                if (!processedItems.contains(itemKey)) {
                    allItems.add(offhand.clone());
                    processedItems.add(itemKey);
                    YakRealms.log("Collected from offhand: " + getItemDisplayName(offhand) + " x" + offhand.getAmount());
                }
            }

            // Ender chest
            ItemStack[] enderChest = player.getEnderChest().getContents();
            if (enderChest != null) {
                for (int i = 0; i < enderChest.length; i++) {
                    ItemStack item = enderChest[i];
                    if (isValidItem(item)) {
                        String itemKey = createItemKey(item, "ender_" + i);
                        if (!processedItems.contains(itemKey)) {
                            allItems.add(item.clone());
                            processedItems.add(itemKey);
                            YakRealms.log("Collected from ender chest slot " + i + ": " +
                                    getItemDisplayName(item) + " x" + item.getAmount());
                        }
                    }
                }
            }

        } catch (Exception e) {
            YakRealms.error("Error collecting player items", e);
        }

        YakRealms.log("Total unique items collected: " + allItems.size());
        YakRealms.log("=== ITEM COLLECTION COMPLETE ===");

        return allItems;
    }

    /**
     * Get all equipped armor items from a player
     */
    public static List<ItemStack> getEquippedArmor(Player player) {
        List<ItemStack> equippedArmor = new ArrayList<>();
        ItemStack[] armorContents = player.getInventory().getArmorContents();

        for (ItemStack armor : armorContents) {
            if (armor != null && armor.getType() != Material.AIR) {
                equippedArmor.add(armor.clone());
            }
        }

        return equippedArmor;
    }

    /**
     * Check if an item is currently equipped as armor
     */
    public static boolean isEquippedArmor(ItemStack item, Player player) {
        if (item == null || !isArmorItem(item)) {
            return false;
        }

        ItemStack[] armorContents = player.getInventory().getArmorContents();
        for (ItemStack equipped : armorContents) {
            if (equipped != null && equipped.isSimilar(item)) {
                return true;
            }
        }

        return false;
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

    /**
     * Separate items into equipped armor and other items
     */
    public static void separateEquippedArmor(List<ItemStack> allItems, Player player,
                                             List<ItemStack> equippedArmor,
                                             List<ItemStack> otherItems) {
        // Get currently equipped armor
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        Set<ItemStack> equippedSet = new HashSet<>();

        for (ItemStack armor : armorContents) {
            if (armor != null && armor.getType() != Material.AIR) {
                equippedSet.add(armor);
            }
        }

        // Separate items
        for (ItemStack item : allItems) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            boolean isEquipped = false;
            Iterator<ItemStack> iterator = equippedSet.iterator();
            while (iterator.hasNext()) {
                ItemStack equipped = iterator.next();
                if (equipped.isSimilar(item)) {
                    isEquipped = true;
                    iterator.remove(); // Remove to handle duplicates correctly
                    break;
                }
            }

            if (isEquipped) {
                equippedArmor.add(item.clone());
            } else {
                otherItems.add(item.clone());
            }
        }
    }

    /**
     * Create unique item key for deduplication
     */
    private static String createItemKey(ItemStack item, String location) {
        if (item == null) return "null_" + location;

        StringBuilder key = new StringBuilder();
        key.append(location).append("_");
        key.append(item.getType().name()).append("_");
        key.append(item.getAmount()).append("_");
        key.append(item.getDurability()).append("_");

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                key.append(meta.getDisplayName().hashCode()).append("_");
            }
            if (meta.hasLore()) {
                key.append(meta.getLore().hashCode()).append("_");
            }
        }

        if (item.getEnchantments() != null && !item.getEnchantments().isEmpty()) {
            key.append(item.getEnchantments().hashCode());
        }

        return key.toString();
    }

    /**
     * Comprehensive item validation
     */
    public static boolean isValidItem(ItemStack item) {
        return item != null &&
                item.getType() != null &&
                item.getType() != Material.AIR &&
                item.getAmount() > 0;
    }

    /**
     * : Determine if item should be kept based on alignment with comprehensive logic
     */
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
                return shouldKeepItemNeutral(item, firstHotbarItem, neutralShouldDropArmor, neutralShouldDropWeapon);

            case "CHAOTIC":
                // Chaotic players lose everything except permanent/quest items (already handled above)
                YakRealms.log("CHAOTIC: Dropping - " + getItemDisplayName(item));
                return false;

            default:
                YakRealms.warn("Unknown alignment: " + alignment + ", defaulting to lawful rules");
                return shouldKeepItemLawful(item, firstHotbarItem);
        }
    }

    /**
     * Lawful item retention logic: Keep armor and first hotbar item
     */
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

    /**
     * Neutral item retention logic: Random chances for armor and weapons
     */
    private static boolean shouldKeepItemNeutral(ItemStack item, ItemStack firstHotbarItem,
                                                 boolean neutralShouldDropArmor, boolean neutralShouldDropWeapon) {

        // Armor logic: 75% chance to keep (25% chance to drop)
        if (isArmorItem(item)) {
            boolean keep = !neutralShouldDropArmor;
            YakRealms.log("NEUTRAL: Armor " + (keep ? "kept" : "dropped") + " - " + getItemDisplayName(item));
            return keep;
        }

        // First hotbar item (weapon) logic: 50% chance to keep
        if (firstHotbarItem != null && isSameItem(item, firstHotbarItem)) {
            boolean keep = !neutralShouldDropWeapon;
            YakRealms.log("NEUTRAL: Weapon " + (keep ? "kept" : "dropped") + " - " + getItemDisplayName(item));
            return keep;
        }

        // All other items are dropped for neutral players
        YakRealms.log("NEUTRAL: Dropping other item - " + getItemDisplayName(item));
        return false;
    }

    /**
     * Check if item is permanent untradeable (always kept)
     */
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

    /**
     * Check if item is a quest item (gem containers, quest books, etc.)
     */
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

    /**
     * Check if item is a gem container (always quest item)
     */
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

    /**
     *  armor item detection for Spigot 1.20.4
     */
    public static boolean isArmorItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }
        return ARMOR_MATERIALS.contains(item.getType());
    }

    /**
     *  weapon item detection for Spigot 1.20.4
     */
    public static boolean isWeaponItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }
        return WEAPON_MATERIALS.contains(item.getType());
    }

    /**
     * Tool item detection for Spigot 1.20.4
     */
    public static boolean isToolItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }
        return TOOL_MATERIALS.contains(item.getType());
    }

    /**
     *  item comparison for first hotbar item detection
     */
    private static boolean isSameItem(ItemStack item1, ItemStack item2) {
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

    /**
     *  item display name extraction
     */
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

    /**
     * Format material name for display
     */
    private static String formatMaterialName(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return "Unknown";
        }

        return Arrays.stream(materialName.toLowerCase().split("_"))
                .map(word -> word.isEmpty() ? "" :
                        Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse("Unknown");
    }

    /**
     * : Place armor item in correct slot with validation
     */
    public static boolean placeArmorItem(Player player, ItemStack item) {
        if (player == null || !isValidItem(item) || !isArmorItem(item)) {
            return false;
        }

        try {
            Material type = item.getType();
            String typeName = type.name().toLowerCase();

            // Determine armor slot
            if (typeName.contains("helmet") || type == Material.TURTLE_HELMET) {
                if (player.getInventory().getHelmet() == null) {
                    player.getInventory().setHelmet(item);
                    YakRealms.log("Placed helmet: " + getItemDisplayName(item));
                    return true;
                }
            } else if (typeName.contains("chestplate") || type == Material.ELYTRA) {
                if (player.getInventory().getChestplate() == null) {
                    player.getInventory().setChestplate(item);
                    YakRealms.log("Placed chestplate: " + getItemDisplayName(item));
                    return true;
                }
            } else if (typeName.contains("leggings")) {
                if (player.getInventory().getLeggings() == null) {
                    player.getInventory().setLeggings(item);
                    YakRealms.log("Placed leggings: " + getItemDisplayName(item));
                    return true;
                }
            } else if (typeName.contains("boots")) {
                if (player.getInventory().getBoots() == null) {
                    player.getInventory().setBoots(item);
                    YakRealms.log("Placed boots: " + getItemDisplayName(item));
                    return true;
                }
            }

            // If armor slot is occupied, add to inventory
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                // Drop if inventory is full
                for (ItemStack leftoverItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                    YakRealms.log("Dropped armor overflow: " + getItemDisplayName(leftoverItem));
                }
            } else {
                YakRealms.log("Added armor to inventory: " + getItemDisplayName(item));
            }
            return true;

        } catch (Exception e) {
            YakRealms.error("Error placing armor item", e);
            return false;
        }
    }

    /**
     * Place armor in array slot for serialization
     */
    public static void placeArmorInSlot(ItemStack[] armorArray, ItemStack armor) {
        if (!isValidItem(armor) || !isArmorItem(armor) || armorArray == null || armorArray.length < 4) {
            return;
        }

        try {
            String typeName = armor.getType().name().toLowerCase();

            if (typeName.contains("helmet")) {
                armorArray[3] = armor;
            } else if (typeName.contains("chestplate") || armor.getType() == Material.ELYTRA) {
                armorArray[2] = armor;
            } else if (typeName.contains("leggings")) {
                armorArray[1] = armor;
            } else if (typeName.contains("boots")) {
                armorArray[0] = armor;
            }
        } catch (Exception e) {
            YakRealms.error("Error placing armor in slot", e);
        }
    }

    /**
     * Safely clear all player items
     */
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

    /**
     * Create empty inventory arrays
     */
    public static ItemStack[] createEmptyInventory() {
        return new ItemStack[36]; // Main inventory only
    }

    /**
     * Create empty armor array
     */
    public static ItemStack[] createEmptyArmor() {
        return new ItemStack[4];
    }

    /**
     * SERIALIZATION METHODS FOR DATABASE STORAGE
     */

    /**
     * Serialize item stack array to Base64 string for database storage
     */
    public static String serializeItemStacks(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (IOException e) {
            YakRealms.log("Error serializing item stacks");
            return null;
        }
    }

    /**
     * Deserialize Base64 string to item stack array from database
     */
    public static ItemStack[] deserializeItemStacks(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];

            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            return items;

        } catch (Exception e) {
            YakRealms.log("Error deserializing item stacks");
            return new ItemStack[0];
        }
    }

    /**
     * Serialize single item stack to Base64 string
     */
    public static String serializeItemStack(ItemStack item) {
        if (!isValidItem(item)) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (IOException e) {
            YakRealms.log("Error serializing item stack");
            return null;
        }
    }

    /**
     * Deserialize Base64 string to single item stack
     */
    public static ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            return (ItemStack) dataInput.readObject();

        } catch (Exception e) {
            YakRealms.log("Error deserializing item stack");
            return null;
        }
    }

    /**
     * Validate inventory state for debugging
     */
    public static void validateInventoryState(Player player, String context) {
        if (player == null) {
            return;
        }

        try {
            YakRealms.log("=== INVENTORY VALIDATION: " + context + " ===");

            // Count items in each section
            int mainCount = countValidItems(player.getInventory().getContents());
            int armorCount = countValidItems(player.getInventory().getArmorContents());
            int enderCount = countValidItems(player.getEnderChest().getContents());
            int offhandCount = isValidItem(player.getInventory().getItemInOffHand()) ? 1 : 0;

            YakRealms.log("Main inventory: " + mainCount + " items");
            YakRealms.log("Armor slots: " + armorCount + " items");
            YakRealms.log("Ender chest: " + enderCount + " items");
            YakRealms.log("Offhand: " + offhandCount + " items");
            YakRealms.log("Total: " + (mainCount + armorCount + enderCount + offhandCount) + " items");
            YakRealms.log("=== VALIDATION COMPLETE ===");

        } catch (Exception e) {
            YakRealms.error("Error validating inventory state", e);
        }
    }

    /**
     * Count valid items in array
     */
    private static int countValidItems(ItemStack[] items) {
        if (items == null) {
            return 0;
        }

        int count = 0;
        for (ItemStack item : items) {
            if (isValidItem(item)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Create safe item copy to prevent reference issues
     */
    public static ItemStack createSafeCopy(ItemStack original) {
        if (!isValidItem(original)) {
            return null;
        }

        try {
            return original.clone();
        } catch (Exception e) {
            YakRealms.error("Error creating safe item copy", e);
            return null;
        }
    }

    /**
     * Batch process items safely with error handling
     */
    public static List<ItemStack> processItemsBatch(List<ItemStack> items, ItemProcessor processor) {
        List<ItemStack> results = new ArrayList<>();

        if (items == null || processor == null) {
            return results;
        }

        for (ItemStack item : items) {
            try {
                ItemStack result = processor.process(item);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                YakRealms.error("Error processing item in batch: " + getItemDisplayName(item), e);
            }
        }

        return results;
    }

    /**
     * Check if an item is untradeable (broader than permanent)
     */
    public static boolean isUntradeableItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }

        try {
            if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                return false;
            }

            List<String> lore = item.getItemMeta().getLore();
            if (lore == null) return false;

            for (String line : lore) {
                String cleanLine = ChatColor.stripColor(line).toLowerCase();
                if (cleanLine.contains("untradeable") || cleanLine.contains("bound")) {
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            YakRealms.error("Error checking if item is untradeable", e);
            return false;
        }
    }

    /**
     * Check if this item is the first hotbar item
     */
    public static boolean isFirstHotbarItem(ItemStack item, ItemStack firstHotbarItem) {
        if (firstHotbarItem == null || item == null) {
            return false;
        }

        return isSameItem(item, firstHotbarItem);
    }

    /**
     * Check if item is a weapon or valid first slot item
     */
    public static boolean isWeaponOrValidFirstSlotItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }

        return isWeaponItem(item) || isToolItem(item) || isArmorItem(item);
    }

    /**
     * Get item rarity or tier for special handling
     */
    public static String getItemRarity(ItemStack item) {
        if (!isValidItem(item)) {
            return "COMMON";
        }

        try {
            if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                List<String> lore = item.getItemMeta().getLore();
                for (String line : lore) {
                    String cleanLine = ChatColor.stripColor(line).toLowerCase();
                    if (cleanLine.contains("legendary")) return "LEGENDARY";
                    if (cleanLine.contains("epic")) return "EPIC";
                    if (cleanLine.contains("rare")) return "RARE";
                    if (cleanLine.contains("uncommon")) return "UNCOMMON";
                }
            }

            // Check by material type
            String typeName = item.getType().name();
            if (typeName.contains("NETHERITE")) return "LEGENDARY";
            if (typeName.contains("DIAMOND")) return "EPIC";
            if (typeName.contains("IRON") || typeName.contains("GOLDEN")) return "RARE";
            if (typeName.contains("STONE") || typeName.contains("CHAINMAIL")) return "UNCOMMON";

            return "COMMON";

        } catch (Exception e) {
            YakRealms.error("Error getting item rarity", e);
            return "COMMON";
        }
    }

    /**
     * Check if item should be prioritized in restoration
     */
    public static boolean isHighPriorityItem(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }

        // Prioritize permanent items, quest items, and high-tier equipment
        return isPermanentUntradeable(item) ||
                isQuestItem(item) ||
                "LEGENDARY".equals(getItemRarity(item)) ||
                "EPIC".equals(getItemRarity(item));
    }

    /**
     * Get comprehensive item information for admin commands
     */
    public static String getItemInfo(ItemStack item) {
        if (!isValidItem(item)) {
            return "Invalid Item";
        }

        StringBuilder info = new StringBuilder();
        info.append("Item: ").append(getItemDisplayName(item)).append(" x").append(item.getAmount()).append("\n");
        info.append("Type: ").append(item.getType().name()).append("\n");
        info.append("Durability: ").append(item.getDurability()).append("/").append(item.getType().getMaxDurability()).append("\n");
        info.append("Rarity: ").append(getItemRarity(item)).append("\n");
        info.append("Armor: ").append(isArmorItem(item)).append("\n");
        info.append("Weapon: ").append(isWeaponItem(item)).append("\n");
        info.append("Tool: ").append(isToolItem(item)).append("\n");
        info.append("Permanent: ").append(isPermanentUntradeable(item)).append("\n");
        info.append("Quest Item: ").append(isQuestItem(item)).append("\n");
        info.append("Untradeable: ").append(isUntradeableItem(item)).append("\n");

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasLore()) {
                info.append("Lore: ").append(meta.getLore()).append("\n");
            }
            if (!item.getEnchantments().isEmpty()) {
                info.append("Enchantments: ").append(item.getEnchantments()).append("\n");
            }
        }

        return info.toString();
    }

    /**
     * Functional interface for item processing
     */
    @FunctionalInterface
    public interface ItemProcessor {
        ItemStack process(ItemStack item);
    }
}