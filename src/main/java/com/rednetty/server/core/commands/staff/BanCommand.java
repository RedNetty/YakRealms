package com.rednetty.server.core.commands.staff;

import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.utils.messaging.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BanCommand implements CommandExecutor, TabCompleter {
    private final ModerationMechanics moderationMechanics;

    public BanCommand(ModerationMechanics moderationMechanics) {
        this.moderationMechanics = moderationMechanics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.ban")) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>You don't have permission to use this command.");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>You don't have permission to use this command.").toString());
            }
            return true;
        }

        if (args.length < 2) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>Usage: /ban <player> <duration> [reason]");
                MessageUtils.send((Player) sender, "<gray>Duration examples: 1d (1 day), 7d (7 days), 30d (30 days), 0 (permanent)");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>Usage: /ban <player> <duration> [reason]").toString());
                sender.sendMessage(MessageUtils.parse("<gray>Duration examples: 1d (1 day), 7d (7 days), 30d (30 days), 0 (permanent)").toString());
            }
            return true;
        }

        // Get target player UUID
        String targetName = args[0];
        UUID targetUuid = null;

        // Check if they're online
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            targetUuid = targetPlayer.getUniqueId();

            // Check if we can ban this player (don't allow banning higher rank staff)
            if (targetPlayer.hasPermission("yakrealms.staff.unbannable") && !sender.hasPermission("yakrealms.staff.override")) {
                if (sender instanceof Player) {
                    MessageUtils.send((Player) sender, "<red>You cannot ban this player.");
                } else {
                    sender.sendMessage(MessageUtils.parse("<red>You cannot ban this player.").toString());
                }
                return true;
            }
        } else {
            // Try to find by name in stored data
            // This would need an actual implementation to look up offline players
            // For now, just say player not found
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>Player not found. They must be online to be banned.");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>Player not found. They must be online to be banned.").toString());
            }
            return true;
        }

        if (targetUuid == null) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>Could not find player: " + targetName);
            } else {
                sender.sendMessage(MessageUtils.parse("<red>Could not find player: " + targetName).toString());
            }
            return true;
        }

        // Parse duration
        long durationSeconds = parseDuration(args[1]);
        if (durationSeconds < 0) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>Invalid duration format. Use numbers with optional suffixes: h, d, w, m");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>Invalid duration format. Use numbers with optional suffixes: h, d, w, m").toString());
            }
            return true;
        }

        // Build reason if provided
        String reason = "Banned by an administrator";
        if (args.length > 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        // Apply the ban
        moderationMechanics.banPlayer(targetUuid, reason, durationSeconds, sender.getName());

        // Notify staff
        String durationText = durationSeconds == 0 ? "permanently" :
                "for " + formatDuration(durationSeconds);


        // Log the ban
        Bukkit.getLogger().info(sender.getName() + " banned " + targetName + " " + durationText + " for: " + reason);

        return true;
    }

    private long parseDuration(String input) {
        try {
            // Check for permanent ban
            if (input.equals("0") || input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) {
                return 0;
            }

            // Parse with suffixes
            long multiplier = 86400; // Default to days
            long value;

            if (input.endsWith("h")) {
                multiplier = 3600; // hours
                value = Long.parseLong(input.substring(0, input.length() - 1));
            } else if (input.endsWith("d")) {
                multiplier = 86400; // days
                value = Long.parseLong(input.substring(0, input.length() - 1));
            } else if (input.endsWith("w")) {
                multiplier = 604800; // weeks
                value = Long.parseLong(input.substring(0, input.length() - 1));
            } else if (input.endsWith("m")) {
                multiplier = 2592000; // months (approx 30 days)
                value = Long.parseLong(input.substring(0, input.length() - 1));
            } else {
                // No suffix, assume days
                value = Long.parseLong(input);
            }

            return value * multiplier;
        } catch (NumberFormatException e) {
            return -1; // Indicates parsing error
        }
    }

    private String formatDuration(long seconds) {
        if (seconds == 0) {
            return "permanently";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;

        StringBuilder result = new StringBuilder();
        if (days > 0) {
            result.append(days).append(" day").append(days != 1 ? "s" : "");
            if (hours > 0) result.append(", ");
        }
        if (hours > 0) {
            result.append(hours).append(" hour").append(hours != 1 ? "s" : "");
        }

        return result.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.ban")) {
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
            durations.add("1d"); // 1 day
            durations.add("3d"); // 3 days
            durations.add("7d"); // 1 week
            durations.add("30d"); // 30 days
            durations.add("0"); // Permanent

            return durations.stream()
                    .filter(duration -> duration.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            // Suggest common reasons
            List<String> reasons = new ArrayList<>();
            reasons.add("Cheating");
            reasons.add("Harassment");
            reasons.add("Inappropriate_content");
            reasons.add("Ban_evasion");

            return reasons.stream()
                    .filter(reason -> reason.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}