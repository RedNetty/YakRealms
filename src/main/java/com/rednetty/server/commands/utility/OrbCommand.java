package com.rednetty.server.commands.utility;

import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for handling orb-related actions
 */
public class OrbCommand implements CommandExecutor {

    private final OrbManager orbManager;

    /**
     * Constructor
     *
     * @param orbManager The orb manager instance
     */
    public OrbCommand(OrbManager orbManager) {
        this.orbManager = orbManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "normal":
                handleNormalOrbCommand(player, args);
                break;
            case "legendary":
                handleLegendaryOrbCommand(player, args);
                break;
            case "help":
                sendHelpMessage(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    /**
     * Handles the normal orb subcommand
     *
     * @param player The player
     * @param args   Command arguments
     */
    private void handleNormalOrbCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.command.orb.normal")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
                amount = Math.max(1, Math.min(64, amount));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid amount. Please specify a number between 1 and 64.");
                return;
            }
        }

        orbManager.giveOrbsToPlayer(player, amount, 0);
        player.sendMessage(TextUtil.colorize("&aGave &e" + amount + "x &5Orb of Alteration &ato you."));
    }

    /**
     * Handles the legendary orb subcommand
     *
     * @param player The player
     * @param args   Command arguments
     */
    private void handleLegendaryOrbCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.command.orb.legendary")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
                amount = Math.max(1, Math.min(64, amount));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid amount. Please specify a number between 1 and 64.");
                return;
            }
        }

        orbManager.giveOrbsToPlayer(player, 0, amount);
        player.sendMessage(TextUtil.colorize("&aGave &e" + amount + "x &6Legendary Orb of Alteration &ato you."));
    }

    /**
     * Sends the help message to the player
     *
     * @param player The player
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "==== Orb Command Help ====");
        player.sendMessage(ChatColor.GOLD + "/orb normal [amount]" + ChatColor.WHITE + " - Get normal orbs");
        player.sendMessage(ChatColor.GOLD + "/orb legendary [amount]" + ChatColor.WHITE + " - Get legendary orbs");
        player.sendMessage(ChatColor.GOLD + "/orb help" + ChatColor.WHITE + " - Show this help message");
    }
}