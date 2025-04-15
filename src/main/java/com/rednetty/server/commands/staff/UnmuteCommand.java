package com.rednetty.server.commands.staff;

import com.rednetty.server.mechanics.moderation.ModerationMechanics;
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

public class UnmuteCommand implements CommandExecutor, TabCompleter {
    private final ModerationMechanics moderationMechanics;

    public UnmuteCommand(ModerationMechanics moderationMechanics) {
        this.moderationMechanics = moderationMechanics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.mute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unmute <player>");
            return true;
        }

        // Find the target player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        // Unmute the player
        moderationMechanics.unmutePlayer(target, sender.getName());

        // Notify staff and target
        String staffMessage = ChatColor.GREEN + target.getName() + " has been unmuted by " + sender.getName();
        String targetMessage = ChatColor.GREEN + "You have been unmuted by " + sender.getName();

        // Broadcast to staff
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakrealms.staff.mute") || ModerationMechanics.isStaff(staff)) {
                staff.sendMessage(staffMessage);
            }
        }

        // Notify the target
        target.sendMessage(targetMessage);

        // Log the unmute
        Bukkit.getLogger().info(sender.getName() + " unmuted " + target.getName());

        return true;
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

        return new ArrayList<>();
    }
}