
// === LootChestNotifier.java ===
package com.rednetty.server.mechanics.world.lootchests;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Handles notifications related to loot chests
 */
public class LootChestNotifier {

    public void initialize() {
        // Initialize notification system
    }

    /**
     * Broadcasts care package drop to all players
     */
    public void broadcastCarePackageDrop(Location location) {
        String message = ChatColor.GOLD + "A care package has dropped at " +
                ChatColor.YELLOW + "X: " + location.getBlockX() + ", Z: " + location.getBlockZ();
        Bukkit.broadcastMessage(message);
    }

    /**
     * Notifies nearby players about an event
     */
    public void notifyNearbyPlayers(Location location, String message, double radius) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) <= radius) {
                player.sendMessage(ChatColor.YELLOW + message);
            }
        }
    }

    /**
     * Sends a private message to a player
     */
    public void sendPrivateMessage(Player player, String message) {
        player.sendMessage(ChatColor.GREEN + "[Loot] " + ChatColor.WHITE + message);
    }

    /**
     * Broadcasts a special announcement
     */
    public void broadcastSpecialAnnouncement(String message) {
        Bukkit.broadcastMessage(ChatColor.GOLD + ChatColor.BOLD.toString() +
                "[SPECIAL] " + ChatColor.RESET + ChatColor.YELLOW + message);
    }
}
