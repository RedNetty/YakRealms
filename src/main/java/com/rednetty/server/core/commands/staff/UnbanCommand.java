package com.rednetty.server.core.commands.staff;

import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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

public class UnbanCommand implements CommandExecutor, TabCompleter {
    private final ModerationMechanics moderationMechanics;

    public UnbanCommand(ModerationMechanics moderationMechanics) {
        this.moderationMechanics = moderationMechanics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.ban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
            return true;
        }

        String targetName = args[0];

        // Try to find player by exact name
        OfflinePlayer offlinePlayer = Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                .findFirst()
                .orElse(null);

        if (offlinePlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }

        UUID targetUuid = offlinePlayer.getUniqueId();

        // Unban the player
        moderationMechanics.unbanPlayer(targetUuid, sender.getName());

        // Notify staff
        String staffMessage = ChatColor.GREEN + targetName + " has been unbanned by " + sender.getName();

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakrealms.staff.ban") || ModerationMechanics.getInstance().isStaff(staff)) {
                staff.sendMessage(staffMessage);
            }
        }

        // Log the unban
        Bukkit.getLogger().info(sender.getName() + " unbanned " + targetName);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.ban")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            // Here we would ideally suggest banned players, but finding banned players is
            // more complex since we need to access the player data repository
            // For now, we'll just return any players the sender has seen before
            return Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName)
                    .filter(name -> name != null && name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .limit(10) // Limit to avoid too many suggestions
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}