package com.rednetty.server.mechanics.economy.vendors.behaviors;

import org.bukkit.entity.Player;

/**
 * Interface that all vendor behaviors must implement
 * This defines what happens when a player interacts with a vendor
 */
public interface VendorBehavior {

    /**
     * Called when a player right-clicks on the vendor NPC
     *
     * @param player The player who clicked the vendor
     */
    void onInteract(Player player);
}