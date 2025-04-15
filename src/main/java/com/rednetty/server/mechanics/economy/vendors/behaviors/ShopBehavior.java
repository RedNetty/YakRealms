package com.rednetty.server.mechanics.economy.vendors.behaviors;

import com.rednetty.server.mechanics.economy.vendors.menus.ItemVendorMenu;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Example vendor behavior that opens a specific shop menu.
 */
public class ShopBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        player.sendMessage(ChatColor.GRAY + "Item Vendor: " + ChatColor.WHITE + "Welcome! I have many fine wares for sale.");

        new ItemVendorMenu(player).open();
    }
}