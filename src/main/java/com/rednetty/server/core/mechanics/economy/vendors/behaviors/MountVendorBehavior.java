package com.rednetty.server.core.mechanics.economy.vendors.behaviors;

import com.rednetty.server.core.mechanics.economy.vendors.menus.MountVendorMenu;
import org.bukkit.entity.Player;

/**
 * Behavior class for Mount vendors.
 * Opens the mount vendor menu when interacted with.
 */
public class MountVendorBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        // Open the mount vendor menu
        new MountVendorMenu(player).open();
    }
}