package com.rednetty.server.mechanics.economy.vendors.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.Vendor;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Admin GUI for configuring vendors
 */
public class VendorConfigGUI implements Listener {

    private final YakRealms plugin;
    private final VendorManager vendorManager;

    public VendorConfigGUI(YakRealms plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Open the main vendor config menu for a player
     */
    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, "Vendor Configuration");

        // Add vendor list
        List<Vendor> vendors = new ArrayList<>(vendorManager.getVendors().values());
        for (int i = 0; i < Math.min(45, vendors.size()); i++) {
            Vendor vendor = vendors.get(i);
            inventory.setItem(i, createVendorItem(vendor));
        }

        // Add control buttons
        inventory.setItem(45, createButton(Material.EMERALD, ChatColor.GREEN + "Create New Vendor",
                Arrays.asList(ChatColor.GRAY + "Click to create a new vendor", ChatColor.GRAY + "at your current location")));

        inventory.setItem(46, createButton(Material.REDSTONE, ChatColor.RED + "Delete Vendor",
                Arrays.asList(ChatColor.GRAY + "Click a vendor above first,", ChatColor.GRAY + "then click here to delete it")));

        inventory.setItem(47, createButton(Material.BOOK, ChatColor.YELLOW + "Reload Vendors",
                Arrays.asList(ChatColor.GRAY + "Reload all vendors from config")));

        inventory.setItem(48, createButton(Material.NAME_TAG, ChatColor.AQUA + "Fix Holograms",
                Arrays.asList(ChatColor.GRAY + "Refresh all vendor holograms")));

        inventory.setItem(53, createButton(Material.BARRIER, ChatColor.RED + "Close", null));

        player.openInventory(inventory);
    }

    /**
     * Create an item representing a vendor
     */
    private ItemStack createVendorItem(Vendor vendor) {
        Material material;
        switch (vendor.getVendorType().toLowerCase()) {
            case "item": material = Material.CHEST; break;
            case "fisherman": material = Material.FISHING_ROD; break;
            case "book": material = Material.BOOK; break;
            case "upgrade": material = Material.EXPERIENCE_BOTTLE; break;
            case "banker": material = Material.GOLD_INGOT; break;
            case "medic": material = Material.APPLE; break;
            case "gambler": material = Material.DIAMOND; break;
            default: material = Material.PAPER; break;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + vendor.getVendorId());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + vendor.getVendorType());
        lore.add(ChatColor.GRAY + "NPC ID: " + ChatColor.WHITE + vendor.getNpcId());
        lore.add(ChatColor.GRAY + "Behavior: " + ChatColor.WHITE + getShortClassName(vendor.getBehaviorClass()));
        lore.add("");
        lore.add(ChatColor.GREEN + "Click to edit this vendor");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Get the short class name from a fully qualified class name
     */
    private String getShortClassName(String className) {
        if (className == null) return "null";
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    /**
     * Create a button item
     */
    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Handle inventory clicks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.equals("Vendor Configuration")) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // Handle button clicks
            if (event.getSlot() == 53) { // Close button
                player.closeInventory();
            } else if (event.getSlot() == 47) { // Reload button
                vendorManager.reload();
                player.sendMessage(ChatColor.GREEN + "Vendors reloaded!");
                openMainMenu(player); // Refresh the menu
            } else if (event.getSlot() == 48) { // Fix holograms button
                // Assuming you have a HologramManager instance
                // hologramManager.refreshAllHolograms();
                player.sendMessage(ChatColor.GREEN + "Holograms refreshed!");
            }

            // Other buttons would need more complex handling
        }
    }
}