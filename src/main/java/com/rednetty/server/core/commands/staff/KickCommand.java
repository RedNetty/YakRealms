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
import java.util.stream.Collectors;

public class KickCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.kick")) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>You don't have permission to use this command.");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>You don't have permission to use this command.").toString());
            }
            return true;
        }

        if (args.length < 1) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>Usage: /kick <player> [reason]");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>Usage: /kick <player> [reason]").toString());
            }
            return true;
        }

        // Find target player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>Player not found: " + args[0]);
            } else {
                sender.sendMessage(MessageUtils.parse("<red>Player not found: " + args[0]).toString());
            }
            return true;
        }

        // Check if we can kick this player (don't allow kicking higher rank staff)
        if (target.hasPermission("yakrealms.staff.unkickable") && !sender.hasPermission("yakrealms.staff.override")) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>You cannot kick this player.");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>You cannot kick this player.").toString());
            }
            return true;
        }

        // Build the reason
        String reason = "Kicked by an administrator";
        if (args.length > 1) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        // Format kick message
        String kickMessage = MessageUtils.parse("<red>You have been kicked from the server\n<gray>Reason: <white>" + reason).toString();

        // Kick the player
        target.kickPlayer(kickMessage);

        // Notify staff
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakrealms.staff") || ModerationMechanics.getInstance().isStaff(staff)) {
                MessageUtils.send(staff, "<green>" + target.getName() + " has been kicked by " + sender.getName() + " for: <gray>" + reason);
            }
        }

        // Log the kick
        Bukkit.getLogger().info(sender.getName() + " kicked " + target.getName() + " for: " + reason);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.kick")) {
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
            // Suggest common reasons
            List<String> reasons = new ArrayList<>();
            reasons.add("Spamming");
            reasons.add("Abusive_language");
            reasons.add("Advertising");
            reasons.add("Exploiting");

            return reasons.stream()
                    .filter(reason -> reason.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}