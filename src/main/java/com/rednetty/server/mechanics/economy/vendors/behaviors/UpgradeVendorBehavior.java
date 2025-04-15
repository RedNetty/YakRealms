package com.rednetty.server.mechanics.economy.vendors.behaviors;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Behavior for Upgrade Vendors that sell permanent upgrades
 */
public class UpgradeVendorBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        player.sendMessage(ChatColor.GRAY + "Upgrade Vendor: " + ChatColor.WHITE + "I offer permanent upgrades in exchange for tokens. Choose wisely!");

        // Open the upgrade vendor inventory
        Inventory upgradeInventory = createUpgradeVendorInventory(player);
        player.openInventory(upgradeInventory);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    /**
     * Create the upgrade vendor inventory
     * This is a placeholder implementation - replace with your actual implementation
     */
    private Inventory createUpgradeVendorInventory(Player player) {
        // This should be replaced with your actual upgrade vendor menu implementation
        // For example:
        // return UpgradeVendorMenu.openUpgradeVendorInventory(player);

        // As a placeholder, we'll create a simple inventory
        return org.bukkit.Bukkit.createInventory(null, 18, "Upgrade Vendor");
    }
}