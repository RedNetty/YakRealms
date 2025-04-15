package com.rednetty.server.commands.staff;

import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command to set a player's rank
 * Fixed to not block the main thread
 */
public class SetRankCommand implements CommandExecutor, TabCompleter {

    private final ModerationMechanics moderationMechanics;

    public SetRankCommand(ModerationMechanics moderationMechanics) {
        this.moderationMechanics = moderationMechanics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /setrank <player> <rank>");
            return true;
        }

        if (!(sender instanceof Player) && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only players and console operators can use this command.");
            return true;
        }

        // Check if sender has permission
        if (sender instanceof Player && !((Player) sender).hasPermission("yakrealms.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        String targetName = args[0];
        String rankStr = args[1].toUpperCase();

        // Get the target player
        Player targetPlayer = Bukkit.getPlayer(targetName);

        // Check if rank is valid
        Rank rank;
        try {
            rank = Rank.valueOf(rankStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid rank: " + rankStr);
            sender.sendMessage(ChatColor.RED + "Available ranks: " +
                    Arrays.stream(Rank.values())
                            .map(Rank::name)
                            .collect(Collectors.joining(", ")));
            return true;
        }

        if (targetPlayer != null && targetPlayer.isOnline()) {
            // Set rank for online player
            moderationMechanics.setRank(targetPlayer, rank);

            // Send success message
            sender.sendMessage(ChatColor.GREEN + "Set " + targetPlayer.getName() + "'s rank to " + rank.name());
            targetPlayer.sendMessage(ChatColor.GREEN + "Your rank has been set to " + rank.name());
        } else {
            // Set rank for offline player - FIXED: use getOfflinePlayer and withPlayer
            try {
                // Just send a "processing" message immediately
                sender.sendMessage(ChatColor.YELLOW + "Setting " + targetName + "'s rank to " + rank.name() + "...");

                // Use UUID lookup for more reliable offline player identification
                UUID targetUUID = null;

                try {
                    // Try by exact name from the currently online players first
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().equalsIgnoreCase(targetName)) {
                            targetUUID = player.getUniqueId();
                            break;
                        }
                    }

                    // If not found, use getOfflinePlayer
                    if (targetUUID == null) {
                        targetUUID = Bukkit.getOfflinePlayer(targetName).getUniqueId();
                    }
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Could not find player: " + targetName);
                    return true;
                }

                if (targetUUID != null) {
                    // Use the non-blocking version of updateAndSaveRank
                    final UUID finalUUID = targetUUID;
                    moderationMechanics.updateAndSaveRank(targetUUID, rank, () -> {
                        sender.sendMessage(ChatColor.GREEN + "Successfully set " + targetName + "'s rank to " + rank.name());
                    });
                } else {
                    sender.sendMessage(ChatColor.RED + "Could not find player: " + targetName);
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error setting rank: " + e.getMessage());
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Tab complete player names
            String partialName = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // Tab complete rank names
            String partialRank = args[1].toUpperCase();
            for (Rank rank : Rank.values()) {
                if (rank.name().startsWith(partialRank)) {
                    completions.add(rank.name());
                }
            }
        }

        return completions;
    }
}