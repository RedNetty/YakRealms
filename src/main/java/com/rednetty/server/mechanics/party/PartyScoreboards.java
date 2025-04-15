package com.rednetty.server.mechanics.party;

import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages party scoreboards for displaying party member health and status
 */
public class PartyScoreboards {
    private static final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    /**
     * Get a player's scoreboard or create a new one if none exists
     *
     * @param player The player
     * @return The player's scoreboard
     */
    public static Scoreboard getPlayerScoreboard(Player player) {
        UUID playerId = player.getUniqueId();

        if (!playerScoreboards.containsKey(playerId)) {
            // Create a new scoreboard for the player
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            Scoreboard scoreboard = manager.getNewScoreboard();

            // Set up teams for different player statuses
            Team redTeam = scoreboard.registerNewTeam("red");
            redTeam.setColor(ChatColor.RED);

            Team yellowTeam = scoreboard.registerNewTeam("yellow");
            yellowTeam.setColor(ChatColor.YELLOW);

            Team whiteTeam = scoreboard.registerNewTeam("white");
            whiteTeam.setColor(ChatColor.WHITE);

            // Special teams for staff
            Team gmTeam = scoreboard.registerNewTeam("gm");
            gmTeam.setPrefix(ChatColor.AQUA.toString() + ChatColor.BOLD + "GM " + ChatColor.AQUA);

            Team managerTeam = scoreboard.registerNewTeam("manager");
            managerTeam.setPrefix(ChatColor.YELLOW.toString() + ChatColor.BOLD + "MANAGER " + ChatColor.YELLOW);

            Team devTeam = scoreboard.registerNewTeam("dev");
            devTeam.setPrefix(ChatColor.RED.toString() + ChatColor.BOLD + "DEV " + ChatColor.RED);

            // Health display
            Objective healthObjective = scoreboard.registerNewObjective("health", "health", ChatColor.RED + "❤");
            healthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);

            // Store the scoreboard
            playerScoreboards.put(playerId, scoreboard);

            return scoreboard;
        }

        return playerScoreboards.get(playerId);
    }

    /**
     * Update a player's scoreboard with their party information
     *
     * @param player The player
     */
    public static void updatePlayerScoreboard(Player player) {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        // Check if the player is in a party
        if (!partyMechanics.isInParty(player)) {
            clearPlayerScoreboard(player);
            return;
        }

        // Get the player's scoreboard
        Scoreboard scoreboard = getPlayerScoreboard(player);

        // Remove existing sidebar objective if it exists
        if (scoreboard.getObjective(DisplaySlot.SIDEBAR) != null) {
            scoreboard.getObjective(DisplaySlot.SIDEBAR).unregister();
        }

        // Create new party objective
        Objective partyObjective = scoreboard.registerNewObjective("party_data", "dummy",
                ChatColor.AQUA.toString() + ChatColor.BOLD + "Party");
        partyObjective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Get party members
        List<Player> partyMembers = partyMechanics.getPartyMembers(player);
        if (partyMembers != null) {
            for (Player member : partyMembers) {
                // Display leader with bold
                String displayName = (partyMechanics.isPartyLeader(member) ?
                        ChatColor.BOLD.toString() : "") + member.getName();

                // Truncate if too long (scoreboard limitation)
                if (displayName.length() > 16) {
                    displayName = displayName.substring(0, 16);
                }

                // Set score to player's health
                partyObjective.getScore(displayName).setScore((int) member.getHealth());
            }
        }

        // Apply the scoreboard to the player
        player.setScoreboard(scoreboard);
    }

    /**
     * Refresh a player's scoreboard health values
     *
     * @param player The player
     */
    public static void refreshPlayerScoreboard(Player player) {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        // Check if the player is in a party
        if (!partyMechanics.isInParty(player)) {
            return;
        }

        // Get the player's scoreboard
        Scoreboard scoreboard = player.getScoreboard();

        // Get sidebar objective
        Objective partyObjective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
        if (partyObjective == null) {
            updatePlayerScoreboard(player);
            return;
        }

        // Get party members
        List<Player> partyMembers = partyMechanics.getPartyMembers(player);
        if (partyMembers != null) {
            for (Player member : partyMembers) {
                // Display leader with bold
                String displayName = (partyMechanics.isPartyLeader(member) ?
                        ChatColor.BOLD.toString() : "") + member.getName();

                // Truncate if too long (scoreboard limitation)
                if (displayName.length() > 16) {
                    displayName = displayName.substring(0, 16);
                }

                // Update score to current health
                partyObjective.getScore(displayName).setScore((int) member.getHealth());
            }
        }
    }

    /**
     * Clear a player's party scoreboard
     *
     * @param player The player
     */
    public static void clearPlayerScoreboard(Player player) {
        Scoreboard scoreboard = getPlayerScoreboard(player);

        // Remove sidebar objective if it exists
        if (scoreboard.getObjective(DisplaySlot.SIDEBAR) != null) {
            scoreboard.getObjective(DisplaySlot.SIDEBAR).unregister();
        }

        // Apply the scoreboard to the player
        player.setScoreboard(scoreboard);
    }

    /**
     * Update color teams for all players based on alignment and rank
     */
    public static void updateAllPlayerColors() {
        // For all online players
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = getPlayerScoreboard(viewer);

            // Update each player's team in this viewer's scoreboard
            for (Player target : Bukkit.getOnlinePlayers()) {
                // Get target's rank
                Rank rank = ModerationMechanics.getRank(target);

                if (rank == Rank.DEV) {
                    scoreboard.getTeam("dev").addEntry(target.getName());
                } else if (rank == Rank.MANAGER) {
                    scoreboard.getTeam("manager").addEntry(target.getName());
                } else if (rank == Rank.GM) {
                    scoreboard.getTeam("gm").addEntry(target.getName());
                } else {
                    // Handle alignment teams - this logic would need to be adapted based on your alignment system
                    // For now, defaulting to white team
                    scoreboard.getTeam("white").addEntry(target.getName());
                }
            }

            // Apply scoreboard to viewer
            viewer.setScoreboard(scoreboard);
        }
    }

    /**
     * Update health display for all players
     */
    public static void updateAllPlayerHealth() {
        // For all online players
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = getPlayerScoreboard(viewer);
            Objective healthObjective = scoreboard.getObjective(DisplaySlot.BELOW_NAME);

            // Update health scores for all players
            for (Player target : Bukkit.getOnlinePlayers()) {
                healthObjective.getScore(target.getName()).setScore((int) target.getHealth());
            }

            // Apply scoreboard to viewer
            viewer.setScoreboard(scoreboard);
        }
    }
}