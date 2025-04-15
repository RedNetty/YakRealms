package com.rednetty.server.mechanics.economy.vendors.behaviors;

import org.bukkit.entity.Player;

/**
 * Represents the behavior a vendor should have when a player interacts with it.
 */
public interface VendorBehavior {
    /**
     * Called when a player interacts with this vendor.
     */
    void onInteract(Player player);
}