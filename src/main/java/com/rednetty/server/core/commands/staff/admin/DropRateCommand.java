package com.rednetty.server.core.commands.staff.admin;

import com.rednetty.server.core.mechanics.item.drops.DropsManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  command for managing drop rates with improved functionality and validation
 */
public class DropRateCommand implements CommandExecutor, TabCompleter {
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

        // Show current rates if no arguments
        if (args.length == 0) {
            showCurrentRates(sender);
            return true;
        }

        // Show help
        if (args.length == 1 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?"))) {
            showHelp(sender);
            return true;
        }

        // Show specific tier rate
        if (args.length == 1) {
            try {
                int tier = Integer.parseInt(args[0]);
                if (!isValidTier(tier)) {
                    sender.sendMessage(ChatColor.RED + "Tier must be between 1 and 6.");
                    return true;
                }
                showTierRate(sender, tier);
                return true;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid tier number. Use /droprate help for usage.");
                return true;
            }
        }

        // Set drop rate
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /droprate <tier> <rate>");
            sender.sendMessage(ChatColor.GRAY + "Use /droprate help for more options.");
            return true;
        }

        try {
            int tier = Integer.parseInt(args[0]);
            int rate = Integer.parseInt(args[1]);

            if (!isValidTier(tier)) {
                sender.sendMessage(ChatColor.RED + "Tier must be between 1 and 6.");
                return true;
            }

            if (!isValidRate(rate)) {
                sender.sendMessage(ChatColor.RED + "Rate must be between 0 and 100.");
                return true;
            }

            int oldRate = dropsManager.getDropRate(tier);
            dropsManager.setDropRate(tier, rate);

            // Send success message to sender
            sender.sendMessage(ChatColor.GREEN + "Successfully updated drop rate for Tier " + tier +
                    " from " + oldRate + "% to " + rate + "%");

            // Send centered message to all players
            TextUtil.broadcastCentered(ChatColor.YELLOW + "DROP RATES" + ChatColor.GRAY + " - " +
                    ChatColor.AQUA + "Tier " + tier + " drop rates have been changed to " + rate + "%");

            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number format. Usage: /droprate <tier> <rate>");
            return true;
        }
    }

    /**
     * Show current drop rates for all tiers
     */
    private void showCurrentRates(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== Current Drop Rates ==========");
        for (int tier = 1; tier <= 6; tier++) {
            int rate = dropsManager.getDropRate(tier);
            ChatColor tierColor = getTierColor(tier);
            sender.sendMessage(tierColor + "Tier " + tier + ": " + ChatColor.WHITE + rate + "%");
        }
        sender.sendMessage(ChatColor.GRAY + "Use '/droprate <tier> <rate>' to change rates");
    }

    /**
     * Show drop rate for a specific tier
     */
    private void showTierRate(CommandSender sender, int tier) {
        int rate = dropsManager.getDropRate(tier);
        ChatColor tierColor = getTierColor(tier);
        sender.sendMessage(tierColor + "Tier " + tier + " drop rate: " + ChatColor.WHITE + rate + "%");
    }

    /**
     * Show help information
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== Drop Rate Commands ==========");
        sender.sendMessage(ChatColor.YELLOW + "/droprate" + ChatColor.WHITE + " - Show all current drop rates");
        sender.sendMessage(ChatColor.YELLOW + "/droprate <tier>" + ChatColor.WHITE + " - Show rate for specific tier");
        sender.sendMessage(ChatColor.YELLOW + "/droprate <tier> <rate>" + ChatColor.WHITE + " - Set drop rate for tier");
        sender.sendMessage(ChatColor.GRAY + "Tier: 1-6, Rate: 0-100%");
    }

    /**
     * Get color for tier display
     */
    private ChatColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.BLUE;
            default -> ChatColor.GRAY;
        };
    }

    /**
     * Validate tier number
     */
    private boolean isValidTier(int tier) {
        return tier >= 1 && tier <= 6;
    }

    /**
     * Validate rate percentage
     */
    private boolean isValidRate(int rate) {
        return rate >= 0 && rate <= 100;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.drops")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = Arrays.asList("1", "2", "3", "4", "5", "6", "help");
            return completions.stream()
                    .filter(completion -> completion.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Suggest some common rate values
            List<String> rates = Arrays.asList("0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                    "55", "60", "65", "70", "75", "80", "85", "90", "95", "100");
            return rates.stream()
                    .filter(rate -> rate.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}