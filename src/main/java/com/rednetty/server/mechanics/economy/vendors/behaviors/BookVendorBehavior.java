package com.rednetty.server.mechanics.economy.vendors.behaviors;

import com.rednetty.server.mechanics.economy.vendors.menus.BookVendorMenu;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Behavior for Book Vendors that sell teleport books
 */
public class BookVendorBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        player.sendMessage(ChatColor.GRAY + "Book Vendor: " + ChatColor.WHITE + "Looking to travel somewhere? I've got just the books you need!");

        // Open the book vendor menu
        new BookVendorMenu(player).open();
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }
}