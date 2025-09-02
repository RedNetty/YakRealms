package com.rednetty.server.core.commands.player;

import com.rednetty.server.core.mechanics.player.settings.Toggles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to open the toggles menu for players
 */
public class TogglesCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // If player is trying to open toggle menu for another player
        if (args.length > 0 && player.hasPermission("yakrealms.toggles.others")) {
            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player " + targetName + " is not online.");
                return true;
            }

            // Open toggle menu for the target player
            player.openInventory(Toggles.getInstance().getToggleMenu(target));
            player.sendMessage(ChatColor.GREEN + "Opened toggle menu for " + target.getName());
            return true;
        }

        // Open toggle menu for the player
        player.openInventory(Toggles.getInstance().getToggleMenu(player));
        player.sendMessage(ChatColor.GREEN + "Toggle settings menu opened.");
        return true;
    }
}