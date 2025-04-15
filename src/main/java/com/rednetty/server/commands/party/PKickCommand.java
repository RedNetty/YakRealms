package com.rednetty.server.commands.party;

import com.rednetty.server.mechanics.party.PartyMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


public class PKickCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "Invalid Syntax. " +
                    ChatColor.RED + "/pkick <player>");
            return true;
        }

        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            player.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + targetName +
                    " is not in your party.");
            return true;
        }

        PartyMechanics.getInstance().kickPlayerFromParty(player, targetPlayer);
        return true;
    }
}