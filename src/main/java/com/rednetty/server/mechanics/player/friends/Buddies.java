package com.rednetty.server.mechanics.player.friends;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.logging.Logger;

/**
 * Manages player buddy (friend) relationships and notifications
 */
public class Buddies implements Listener {
    private static Buddies instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    /**
     * Private constructor for singleton pattern
     */
    private Buddies() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The Buddies instance
     */
    public static Buddies getInstance() {
        if (instance == null) {
            instance = new Buddies();
        }
        return instance;
    }

    /**
     * Initialize the buddy system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        YakRealms.log("Buddies system has been enabled.");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        YakRealms.log("Buddies system has been disabled.");
    }

    /**
     * Add a buddy relationship
     *
     * @param player    The player adding a buddy
     * @param buddyName The name of the buddy to add
     * @return true if successfully added
     */
    public boolean addBuddy(Player player, String buddyName) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

        // Check if already a buddy
        if (yakPlayer.isBuddy(buddyName)) {
            player.sendMessage(ChatColor.RED + buddyName + " is already on your buddy list!");
            return false;
        }

        // Add buddy
        yakPlayer.addBuddy(buddyName);
        playerManager.savePlayer(yakPlayer);

        player.sendMessage(ChatColor.GREEN + "Added " + buddyName + " to your buddy list!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // Notify the added buddy if they're online
        Player buddyPlayer = Bukkit.getPlayerExact(buddyName);
        if (buddyPlayer != null && buddyPlayer.isOnline()) {
            buddyPlayer.sendMessage(ChatColor.GREEN + player.getName() + " has added you as a buddy!");
        }

        return true;
    }

    /**
     * Remove a buddy relationship
     *
     * @param player    The player removing a buddy
     * @param buddyName The name of the buddy to remove
     * @return true if successfully removed
     */
    public boolean removeBuddy(Player player, String buddyName) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

        // Check if actually a buddy
        if (!yakPlayer.isBuddy(buddyName)) {
            player.sendMessage(ChatColor.RED + buddyName + " is not on your buddy list!");
            return false;
        }

        // Remove buddy
        yakPlayer.removeBuddy(buddyName);
        playerManager.savePlayer(yakPlayer);

        player.sendMessage(ChatColor.YELLOW + "Removed " + buddyName + " from your buddy list.");

        return true;
    }

    /**
     * Get a list of a player's buddies
     *
     * @param player The player to check
     * @return List of buddy names
     */
    public List<String> getBuddies(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null ? yakPlayer.getBuddies() : List.of();
    }

    /**
     * Get a list of a player's buddies by player name
     *
     * @param playerName The player name to check
     * @return List of buddy names
     */
    public static List<String> getBuddies(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) return List.of();

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        return yakPlayer != null ? yakPlayer.getBuddies() : List.of();
    }

    /**
     * Check if a player is a buddy of another player
     *
     * @param player    The player to check
     * @param buddyName The potential buddy's name
     * @return true if they are buddies
     */
    public boolean isBuddy(Player player, String buddyName) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && yakPlayer.isBuddy(buddyName);
    }

    /**
     * Handle buddy notifications when a player joins
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();

        // Don't notify for staff or operators
        if (joiningPlayer.isOp()) return;

        String joiningPlayerName = joiningPlayer.getName();

        // Notify all online buddies
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(joiningPlayer)) continue;

            YakPlayer onlineYakPlayer = playerManager.getPlayer(onlinePlayer);
            if (onlineYakPlayer != null && onlineYakPlayer.isBuddy(joiningPlayerName)) {
                onlinePlayer.sendMessage(ChatColor.YELLOW + joiningPlayerName + " has joined the server.");
                onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 1.2f);
            }
        }
    }

    /**
     * Handle buddy notifications when a player leaves
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player leavingPlayer = event.getPlayer();

        // Don't notify for staff or operators
        if (leavingPlayer.isOp()) return;

        String leavingPlayerName = leavingPlayer.getName();

        // Notify all online buddies
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(leavingPlayer)) continue;

            YakPlayer onlineYakPlayer = playerManager.getPlayer(onlinePlayer);
            if (onlineYakPlayer != null && onlineYakPlayer.isBuddy(leavingPlayerName)) {
                onlinePlayer.sendMessage(ChatColor.YELLOW + leavingPlayerName + " has left the server.");
                onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 0.5f);
            }
        }
    }

    /**
     * Find a player by partial name for buddy commands
     *
     * @param partialName The partial name to search for
     * @return The found player or null
     */
    public Player findPlayer(String partialName) {
        Player exactMatch = Bukkit.getPlayerExact(partialName);
        if (exactMatch != null) return exactMatch;

        // Try partial match
        List<Player> matches = Bukkit.matchPlayer(partialName);
        return matches.isEmpty() ? null : matches.get(0);
    }
}