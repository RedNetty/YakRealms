package com.rednetty.server.core.mechanics.economy.vendors.behaviors;

import com.rednetty.server.core.mechanics.economy.vendors.menus.ItemVendorMenu;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Behavior for Item Vendors that sell various items and enchantments
 */
public class ItemVendorBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        player.sendMessage(ChatColor.GRAY + "Item Vendor: " + ChatColor.WHITE + "Welcome! I have many fine wares for sale.");

        // Open the item vendor menu
        new ItemVendorMenu(player).open();
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }
}