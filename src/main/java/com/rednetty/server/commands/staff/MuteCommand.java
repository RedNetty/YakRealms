package com.rednetty.server.commands.staff;

import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MuteCommand implements CommandExecutor, TabCompleter {
    private final ModerationMechanics moderationMechanics;

    public MuteCommand(ModerationMechanics moderationMechanics) {
        this.moderationMechanics = moderationMechanics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.mute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /mute <player> <duration> [reason]");
            sender.sendMessage(ChatColor.GRAY + "Duration examples: 5 (5 minutes), 1h (1 hour), 1d (1 day), 0 (permanent)");
            return true;
        }

        // Find the target player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        // Check if we can mute this player (don't allow muting higher rank staff)
        if (target.hasPermission("yakrealms.staff.unmutable") && !sender.hasPermission("yakrealms.staff.override")) {
            sender.sendMessage(ChatColor.RED + "You cannot mute this player.");
            return true;
        }

        // Parse duration
        int durationSeconds = parseDuration(args[1]);
        if (durationSeconds < 0) {
            sender.sendMessage(ChatColor.RED + "Invalid duration format. Use numbers with optional suffixes: s, m, h, d");
            return true;
        }

        // Build reason if provided
        String reason = "No reason specified";
        if (args.length > 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        // Apply the mute
        moderationMechanics.mutePlayer(target, durationSeconds, sender.getName());

        // Notify staff and target
        String durationText = durationSeconds == 0 ? "permanently" :
                "for " + formatDuration(durationSeconds);

        String staffMessage = ChatColor.GREEN + target.getName() + " has been muted " + durationText +
                ChatColor.GREEN + " by " + sender.getName() +
                ChatColor.GRAY + " (" + reason + ")";

        String targetMessage = ChatColor.RED + "You have been muted " + durationText +
                ChatColor.RED + " by " + sender.getName() +
                ChatColor.GRAY + "\nReason: " + reason;

        // Broadcast to staff
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakrealms.staff.mute") || ModerationMechanics.getInstance().isStaff(staff)) {
                staff.sendMessage(staffMessage);
            }
        }

        // Notify the target
        target.sendMessage(targetMessage);

        // Log the mute
        Bukkit.getLogger().info(sender.getName() + " muted " + target.getName() + " " + durationText + " for: " + reason);

        return true;
    }

    private int parseDuration(String input) {
        try {
            // Check for permanent mute
            if (input.equals("0") || input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) {
                return 0;
            }

            // Parse with suffixes
            int multiplier = 60; // Default to minutes
            int value;

            if (input.endsWith("s")) {
                multiplier = 1; // seconds
                value = Integer.parseInt(input.substring(0, input.length() - 1));
            } else if (input.endsWith("m")) {
                multiplier = 60; // minutes
                value = Integer.parseInt(input.substring(0, input.length() - 1));
            } else if (input.endsWith("h")) {
                multiplier = 3600; // hours
                value = Integer.parseInt(input.substring(0, input.length() - 1));
            } else if (input.endsWith("d")) {
                multiplier = 86400; // days
                value = Integer.parseInt(input.substring(0, input.length() - 1));
            } else {
                // No suffix, assume minutes
                value = Integer.parseInt(input);
                multiplier = 60;
            }

            return value * multiplier;
        } catch (NumberFormatException e) {
            return -1; // Indicates parsing error
        }
    }

    private String formatDuration(int seconds) {
        if (seconds == 0) {
            return "permanently";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        StringBuilder result = new StringBuilder();
        if (days > 0) {
            result.append(days).append(" day").append(days != 1 ? "s" : "");
            if (hours > 0 || minutes > 0 || remainingSeconds > 0) result.append(", ");
        }
        if (hours > 0) {
            result.append(hours).append(" hour").append(hours != 1 ? "s" : "");
            if (minutes > 0 || remainingSeconds > 0) result.append(", ");
        }
        if (minutes > 0) {
            result.append(minutes).append(" minute").append(minutes != 1 ? "s" : "");
            if (remainingSeconds > 0) result.append(", ");
        }
        if (remainingSeconds > 0) {
            result.append(remainingSeconds).append(" second").append(remainingSeconds != 1 ? "s" : "");
        }

        return result.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.mute")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            // Suggest online players
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Suggest durations
            List<String> durations = new ArrayList<>();
            durations.add("5m"); // 5 minutes
            durations.add("10m"); // 10 minutes
            durations.add("30m"); // 30 minutes
            durations.add("1h"); // 1 hour
            durations.add("1d"); // 1 day
            durations.add("0"); // Permanent

            return durations.stream()
                    .filter(duration -> duration.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}