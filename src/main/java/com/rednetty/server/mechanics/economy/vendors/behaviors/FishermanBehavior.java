package com.rednetty.server.mechanics.economy.vendors.behaviors;

import com.rednetty.server.mechanics.economy.vendors.menus.FishermanMenu;
import org.bukkit.entity.Player;

/**
 * Behavior class for Fisherman vendors.
 * Opens the fisherman menu when interacted with.
 */
public class FishermanBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        // Open the fisherman menu
        new FishermanMenu(player).open();
    }
}