package com.rednetty.server.core.mechanics.item.enchants;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages custom enchantments for the server
 */
public class Enchants {

    // Pattern for finding plus level in item names
    private static final Pattern PLUS_PATTERN = Pattern.compile("\\[\\+(\\d+)\\]");

    /**
     * Initializes the enchantment system
     */
    public static void initialize() {
        YakRealms.log("Item glow effect system initialized");
    }

    /**
     * Gets the plus level from an item's name
     *
     * @param item The item to check
     * @return The plus level or 0 if none found
     */
    public static int getPlus(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return 0;
        }

        String name = item.getItemMeta().getDisplayName();
        Matcher matcher = PLUS_PATTERN.matcher(ChatColor.stripColor(name));

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        return 0;
    }

    /**
     * Adds a glow effect to an item
     *  to properly handle ArmorMeta with trims (T6 netherite gear)
     *
     * @param item The item to add glow to
     * @return The item with glow effect
     */
    public static ItemStack addGlow(ItemStack item) {
        if (item == null) {
            return item;
        }

        try {
            // Get the current meta
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }

            // Preserve armor trim data if this is armor meta
            ArmorTrim existingTrim = null;
            if (meta instanceof ArmorMeta armorMeta) {
                existingTrim = armorMeta.getTrim();
            }

            // Add the dummy enchantment for glow effect
            item.addUnsafeEnchantment(Enchantment.RESPIRATION, 1);

            // Get the meta again after enchantment is added
            meta = item.getItemMeta();
            if (meta != null) {
                // Hide the enchantment from the item tooltip
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

                // If this is armor meta and we had a trim, restore it
                if (meta instanceof ArmorMeta armorMeta && existingTrim != null) {
                    armorMeta.setTrim(existingTrim);
                }

                // Apply the modified meta back to the item
                item.setItemMeta(meta);
            }

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Failed to add glow effect to item: " + e.getMessage());
        }

        return item;
    }

    /**
     * Removes the glow effect from an item
     * Updated to properly handle ArmorMeta with trims
     *
     * @param item The item to remove glow from
     * @return The item without glow effect
     */
    public static ItemStack removeGlow(ItemStack item) {
        if (item == null) {
            return item;
        }

        try {
            // Preserve armor trim data if this is armor meta
            ArmorTrim existingTrim = null;
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof ArmorMeta armorMeta) {
                existingTrim = armorMeta.getTrim();
            }

            // Remove all enchantments
            for (Enchantment enchantment : item.getEnchantments().keySet()) {
                item.removeEnchantment(enchantment);
            }

            // Update item meta to remove the HIDE_ENCHANTS flag
            meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
                    meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
                }

                // If this is armor meta and we had a trim, restore it
                if (meta instanceof ArmorMeta armorMeta && existingTrim != null) {
                    armorMeta.setTrim(existingTrim);
                }

                item.setItemMeta(meta);
            }

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Failed to remove glow effect from item: " + e.getMessage());
        }

        return item;
    }

    /**
     * Checks if an item has the glow effect
     *
     * @param item The item to check
     * @return true if the item has the glow effect
     */
    public static boolean hasGlow(ItemStack item) {
        return item != null &&
                item.containsEnchantment(Enchantment.AQUA_AFFINITY) &&
                item.getItemMeta() != null &&
                item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS);
    }

    /**
     * Safely adds glow to an item while preserving all existing meta data
     * This is specifically designed for complex items like T6 netherite gear
     *
     * @param item The item to add glow to
     * @return The item with glow effect, preserving all existing data
     */
    public static ItemStack addGlowSafely(ItemStack item) {
        if (item == null) {
            return item;
        }

        try {
            // Clone the item to avoid modifying the original
            ItemStack glowedItem = item.clone();

            // Add enchantment first
            glowedItem.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);

            // Get fresh meta and only modify the enchant visibility
            ItemMeta freshMeta = glowedItem.getItemMeta();
            if (freshMeta != null) {
                freshMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                glowedItem.setItemMeta(freshMeta);
            }

            return glowedItem;

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Failed to safely add glow effect: " + e.getMessage());
            return item;
        }
    }
}