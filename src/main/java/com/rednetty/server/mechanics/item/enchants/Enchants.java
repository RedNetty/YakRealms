package com.rednetty.server.mechanics.item.enchants;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
     *
     * @param item The item to add glow to
     * @return The item with glow effect
     */
    public static ItemStack addGlow(ItemStack item) {
        if (item != null) {
            // Get the item meta
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Add a dummy enchantment (WATER_WORKER is Aqua Affinity)
                item.addUnsafeEnchantment(Enchantment.WATER_WORKER, 1);

                // Hide the enchantment from the item tooltip
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

                // Apply the modified meta back to the item
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Removes the glow effect from an item
     *
     * @param item The item to remove glow from
     * @return The item without glow effect
     */
    public static ItemStack removeGlow(ItemStack item) {
        if (item != null) {
            // Remove all enchantments
            for (Enchantment enchantment : item.getEnchantments().keySet()) {
                item.removeEnchantment(enchantment);
            }

            // Update item meta to remove the HIDE_ENCHANTS flag
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
                meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
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
        return item != null && item.getEnchantments().size() > 0 &&
                item.getItemMeta() != null &&
                item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS);
    }
}