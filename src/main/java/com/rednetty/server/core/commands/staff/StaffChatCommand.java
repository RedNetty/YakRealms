package com.rednetty.server.core.commands.staff;

import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.Rank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffChatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Check if sender has permission
        if (!sender.hasPermission("yakrealms.staff.chat")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /staffchat <message>");
            return true;
        }

        // Build the message from args
        StringBuilder messageBuilder = new StringBuilder();
        for (String arg : args) {
            messageBuilder.append(arg).append(" ");
        }
        String message = messageBuilder.toString().trim();

        // Create the staff chat format
        String staffName = sender.getName();
        String prefix = ChatColor.RED + "[STAFF] " + ChatColor.RESET;

        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (ModerationMechanics.getInstance().isStaff(player)) {
                Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());
                staffName = ChatColor.translateAlternateColorCodes('&', rank.tag) + " " + player.getName();
            }
        }

        String fullMessage = prefix + staffName + ChatColor.WHITE + ": " + message;

        // Send to all staff members
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (ModerationMechanics.getInstance().isStaff(staff) || staff.hasPermission("yakrealms.staff.chat")) {
                staff.sendMessage(fullMessage);
            }
        }

        // If console is the sender, they've already sent the message
        if (!(sender instanceof Player)) {
            sender.sendMessage(fullMessage);
        }

        // Log the staff chat message
        Bukkit.getLogger().info("[STAFFCHAT] " + sender.getName() + ": " + message);

        return true;
    }
}