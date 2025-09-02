package com.rednetty.server.core.mechanics.economy.vendors.behaviors;

import com.rednetty.server.core.mechanics.economy.vendors.menus.ItemVendorMenu;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Vendor behavior that opens an item shop menu.
 */
public class ShopBehavior implements VendorBehavior {
    private static final String VENDOR_GREETING = ChatColor.GRAY + "Item Vendor: " + 
                                                   ChatColor.WHITE + "Welcome! I have many fine wares for sale.";

    @Override
    public void onInteract(Player player) {
        if (player == null) {
            return;
        }
        
        player.sendMessage(VENDOR_GREETING);
        
        try {
            new ItemVendorMenu(player).open();
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Sorry, the shop is currently unavailable.");
        }
    }
}