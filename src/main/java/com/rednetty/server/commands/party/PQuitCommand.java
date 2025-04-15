package com.rednetty.server.commands.party;

import com.rednetty.server.mechanics.party.PartyMechanics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PQuitCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 0) {
            player.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "Invalid Syntax. " +
                    ChatColor.RED + "/pquit");
            return true;
        }

        if (!PartyMechanics.getInstance().isInParty(player)) {
            player.sendMessage(ChatColor.RED + "You are not in a party.");
            return true;
        }

        player.sendMessage(ChatColor.RED + "You have left the party.");
        PartyMechanics.getInstance().removePlayerFromParty(player);
        return true;
    }
}