package com.rednetty.server.core.commands.economy;

import com.rednetty.server.core.mechanics.economy.GemPouchManager;
import com.rednetty.server.utils.ui.GradientColors;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GemPouchCommand implements CommandExecutor, TabCompleter {
    private final GemPouchManager gemPouchManager;
    private final List<String> subCommands = Arrays.asList("get", "info", "help");

    public GemPouchCommand(GemPouchManager gemPouchManager) {
        this.gemPouchManager = gemPouchManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            displayHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "get":
                if (!player.hasPermission("yakrealms.gempouch.get")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to get gem pouches.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /gempouch get <tier>");
                    return true;
                }

                int tier;
                try {
                    tier = Integer.parseInt(args[1]);
                    if (tier < 1 || tier > 6) {
                        player.sendMessage(ChatColor.RED + "Tier must be between 1 and 6.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid tier: " + args[1]);
                    return true;
                }

                // Create and give pouch
                ItemStack pouch = gemPouchManager.createGemPouch(tier, false);
                player.getInventory().addItem(pouch);

                player.sendMessage(ChatColor.GREEN + "You received a tier " + tier + " gem pouch.");
                break;

            case "info":
                player.sendMessage(ChatColor.GOLD + "=== Gem Pouch Info ===");
                player.sendMessage(ChatColor.WHITE + "Tier 1: " + ChatColor.GRAY + "Small Gem Pouch - Holds 200 gems");
                player.sendMessage(ChatColor.GREEN + "Tier 2: " + ChatColor.GRAY + "Medium Gem Sack - Holds 350 gems");
                player.sendMessage(ChatColor.AQUA + "Tier 3: " + ChatColor.GRAY + "Large Gem Satchel - Holds 500 gems");
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Tier 4: " + ChatColor.GRAY + "Gigantic Gem Container - Holds 3000 gems");
                player.sendMessage(ChatColor.YELLOW + "Tier 5: " + ChatColor.GRAY + "Legendary Gem Container - Holds 8000 gems");
                String tier6Info = "Tier 6: " + ChatColor.GRAY + "Insane Gem Container - Holds 100000 gems";
                player.sendMessage(GradientColors.getT6Gradient("Tier 6: ") + ChatColor.GRAY + "Insane Gem Container - Holds 100000 gems");
                player.sendMessage(ChatColor.GREEN + "Right-click pouch with gems to add, right-click empty-handed to withdraw.");
                break;

            case "help":
                displayHelp(player);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown gempouch command: " + subCommand);
                player.sendMessage(ChatColor.YELLOW + "Type /gempouch help for available commands");
                break;
        }

        return true;
    }

    private void displayHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Gem Pouch Commands ===");
        player.sendMessage(ChatColor.GREEN + "/gempouch info" + ChatColor.GRAY + " - Display information about gem pouches");

        if (player.hasPermission("yakrealms.gempouch.get")) {
            player.sendMessage(ChatColor.GREEN + "/gempouch get <tier>" + ChatColor.GRAY + " - Get a gem pouch of specified tier");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    if (!subCommand.equals("get") || sender.hasPermission("yakrealms.gempouch.get")) {
                        completions.add(subCommand);
                    }
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("get") && sender.hasPermission("yakrealms.gempouch.get")) {
                for (int i = 1; i <= 6; i++) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions;
    }
}