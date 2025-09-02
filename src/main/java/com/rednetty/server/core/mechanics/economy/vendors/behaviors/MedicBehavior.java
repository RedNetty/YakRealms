package com.rednetty.server.core.mechanics.economy.vendors.behaviors;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Behavior for Medic vendors that heal players
 */
public class MedicBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        // Check if player needs healing
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20); // Also restore hunger

            player.sendMessage(ChatColor.GREEN + "You are now fully healed! Good luck out there!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(ChatColor.GRAY + "Medic: " + ChatColor.WHITE + "You're already in perfect health. Stay safe out there!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        }
    }
}