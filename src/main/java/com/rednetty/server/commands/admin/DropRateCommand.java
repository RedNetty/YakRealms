package com.rednetty.server.commands.admin;

import com.rednetty.server.mechanics.drops.DropsManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DropRateCommand implements CommandExecutor {
    private final DropsManager dropsManager;

    public DropRateCommand(DropsManager dropsManager) {
        this.dropsManager = dropsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.drops")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /droprate <tier> <rate>");
            return true;
        }

        try {
            int tier = Integer.parseInt(args[0]);
            int rate = Integer.parseInt(args[1]);

            if (tier < 1 || tier > 6) {
                sender.sendMessage(ChatColor.RED + "Tier must be between 1 and 6.");
                return true;
            }

            if (rate < 0 || rate > 100) {
                sender.sendMessage(ChatColor.RED + "Rate must be between 0 and 100.");
                return true;
            }

            dropsManager.setDropRate(tier, rate);

            // Send centered message to all players
            TextUtil.broadcastCentered(ChatColor.YELLOW + "DROP RATES" + ChatColor.GRAY + " - " +
                    ChatColor.AQUA + "Tier " + tier + " drop rates have been changed to " + rate + "%");

            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number format. Usage: /droprate <tier> <rate>");
            return true;
        }
    }
}