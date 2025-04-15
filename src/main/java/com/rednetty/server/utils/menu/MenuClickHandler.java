package com.rednetty.server.utils.menu;

import org.bukkit.entity.Player;

/**
 * Interface for handling clicks on menu items
 */
public interface MenuClickHandler {

    /**
     * Called when a player clicks on a menu item
     *
     * @param player The player who clicked
     * @param slot   The slot that was clicked
     */
    void onClick(Player player, int slot);
}