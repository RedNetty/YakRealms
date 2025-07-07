package com.rednetty.server.mechanics.item.stattrak;

import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Factory for creating StatTrak items
 */
public class StatTrakFactory {

    /**
     * Creates a weapon stat tracker item
     *
     * @return The weapon stat tracker item
     */
    public ItemStack createWeaponStatTracker() {
        ItemStack tracker = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = tracker.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "✦ " + ChatColor.BOLD + "Weapon Stat Tracker" +
                    ChatColor.RESET + ChatColor.GOLD + " ✦");

            List<String> lore = Arrays.asList(
                    "",
                    ChatColor.GRAY + "Apply to any weapon to start tracking",
                    ChatColor.GRAY + "combat statistics as you use it.",
                    "",
                    ChatColor.YELLOW + "Tracks:",
                    ChatColor.AQUA + "• Player Kills",
                    ChatColor.AQUA + "• Mob Kills",
                    ChatColor.AQUA + "• Normal Orbs Used",
                    ChatColor.AQUA + "• Legendary Orbs Used",
                    "",
                    ChatColor.GREEN + "Uses: " + ChatColor.WHITE + "1",
                    "",
                    ChatColor.GOLD + "✨ " + ChatColor.ITALIC + "Forged with magical essence" + ChatColor.GOLD + " ✨"
            );
            meta.setLore(lore);

            // Add enchantment glow
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            tracker.setItemMeta(meta);
        }

        // Store NBT data
        NBTAccessor nbt = new NBTAccessor(tracker);
        nbt.setString("statTrakType", "weapon");
        nbt.setDouble("creationTime", System.currentTimeMillis());

        return nbt.update();
    }

    /**
     * Creates a pickaxe stat tracker item
     *
     * @return The pickaxe stat tracker item
     */
    public ItemStack createPickaxeStatTracker() {
        ItemStack tracker = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = tracker.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "⛏ " + ChatColor.BOLD + "Pickaxe Stat Tracker" +
                    ChatColor.RESET + ChatColor.GOLD + " ⛏");

            List<String> lore = Arrays.asList(
                    "",
                    ChatColor.GRAY + "Apply to any pickaxe to start tracking",
                    ChatColor.GRAY + "mining statistics as you use it.",
                    "",
                    ChatColor.YELLOW + "Tracks:",
                    ChatColor.AQUA + "• Ores Mined",
                    ChatColor.AQUA + "• Gems Found",
                    "",
                    ChatColor.GREEN + "Uses: " + ChatColor.WHITE + "1",
                    "",
                    ChatColor.GOLD + "✨ " + ChatColor.ITALIC + "Infused with earth magic" + ChatColor.GOLD + " ✨"
            );
            meta.setLore(lore);

            // Add enchantment glow
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            tracker.setItemMeta(meta);
        }

        // Store NBT data
        NBTAccessor nbt = new NBTAccessor(tracker);
        nbt.setString("statTrakType", "pickaxe");
        nbt.setDouble("creationTime", System.currentTimeMillis());

        return nbt.update();
    }

    /**
     * Checks if an item is a weapon stat tracker
     *
     * @param item The item to check
     * @return true if it's a weapon stat tracker
     */
    public boolean isWeaponStatTracker(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }

        NBTAccessor nbt = new NBTAccessor(item);
        return "weapon".equals(nbt.getString("statTrakType"));
    }

    /**
     * Checks if an item is a pickaxe stat tracker
     *
     * @param item The item to check
     * @return true if it's a pickaxe stat tracker
     */
    public boolean isPickaxeStatTracker(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }

        NBTAccessor nbt = new NBTAccessor(item);
        return "pickaxe".equals(nbt.getString("statTrakType"));
    }
}
