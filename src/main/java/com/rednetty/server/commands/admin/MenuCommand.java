package com.rednetty.server.commands.admin;

import com.rednetty.server.mechanics.item.MenuItemManager;
import com.rednetty.server.mechanics.item.MenuSystemInitializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command handler for managing the menu item system.
 * Provides admin commands for testing, debugging, and managing menu items.
 */
public class MenuCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.menu")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "setup":
                return handleSetupCommand(sender, args);
            case "clear":
                return handleClearCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            case "stats":
                return handleStatsCommand(sender);
            case "test":
                return handleTestCommand(sender, args);
            case "help":
                sendHelpMessage(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendHelpMessage(sender);
                return true;
        }
    }

    /**
     * Handle the setup subcommand
     */
    private boolean handleSetupCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /menu setup <player|all>");
            return true;
        }

        String target = args[1].toLowerCase();

        if (target.equals("all")) {
            sender.sendMessage(ChatColor.YELLOW + "Setting up menu items for all online players...");

            int count = 0;
            MenuItemManager manager = MenuItemManager.getInstance();

            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    manager.setupMenuItems(player);
                    count++;
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Failed to setup menu items for " + player.getName() + ": " + e.getMessage());
                }
            }

            sender.sendMessage(ChatColor.GREEN + "Menu items set up for " + count + " players.");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + target);
            return true;
        }

        try {
            MenuSystemInitializer.setupMenuItemsForPlayer(targetPlayer);
            sender.sendMessage(ChatColor.GREEN + "Menu items set up for " + targetPlayer.getName());
            targetPlayer.sendMessage(ChatColor.GREEN + "Your menu items have been refreshed!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to setup menu items for " + targetPlayer.getName() + ": " + e.getMessage());
        }

        return true;
    }

    /**
     * Handle the clear subcommand
     */
    private boolean handleClearCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /menu clear <player|all>");
            return true;
        }

        String target = args[1].toLowerCase();

        if (target.equals("all")) {
            sender.sendMessage(ChatColor.YELLOW + "Clearing menu items for all online players...");

            int count = 0;
            MenuItemManager manager = MenuItemManager.getInstance();

            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    manager.clearMenuItems(player);
                    count++;
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Failed to clear menu items for " + player.getName() + ": " + e.getMessage());
                }
            }

            sender.sendMessage(ChatColor.GREEN + "Menu items cleared for " + count + " players.");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + target);
            return true;
        }

        try {
            MenuItemManager.getInstance().clearMenuItems(targetPlayer);
            sender.sendMessage(ChatColor.GREEN + "Menu items cleared for " + targetPlayer.getName());
            targetPlayer.sendMessage(ChatColor.YELLOW + "Your menu items have been cleared.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to clear menu items for " + targetPlayer.getName() + ": " + e.getMessage());
        }

        return true;
    }

    /**
     * Handle the reload subcommand
     */
    private boolean handleReloadCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading menu item system...");

        try {
            MenuSystemInitializer.reload();
            sender.sendMessage(ChatColor.GREEN + "Menu item system reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload menu item system: " + e.getMessage());
        }

        return true;
    }

    /**
     * Handle the stats subcommand
     */
    private boolean handleStatsCommand(CommandSender sender) {
        try {
            String stats = MenuSystemInitializer.getSystemStats();

            sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
            sender.sendMessage(ChatColor.YELLOW + "Menu Item System Statistics");
            sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");

            for (String line : stats.split("\n")) {
                sender.sendMessage(ChatColor.WHITE + line);
            }

            sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to retrieve statistics: " + e.getMessage());
        }

        return true;
    }

    /**
     * Handle the test subcommand
     */
    private boolean handleTestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /menu test <journal|help|settings|friends>");
            return true;
        }

        String testType = args[1].toLowerCase();
        MenuItemManager manager = MenuItemManager.getInstance();

        switch (testType) {
            case "journal":
                sender.sendMessage(ChatColor.GREEN + "Testing journal menu item...");
                com.rednetty.server.mechanics.item.Journal.openJournal(player);
                break;
            case "help":
                sender.sendMessage(ChatColor.GREEN + "Testing help guide...");
                // Help guide functionality is handled in MenuItemManager
                player.sendMessage(ChatColor.YELLOW + "Help guide test - this would open the help interface");
                break;
            case "settings":
                sender.sendMessage(ChatColor.GREEN + "Testing settings menu...");
                player.sendMessage(ChatColor.YELLOW + "Settings test - this would open the settings interface");
                break;
            case "friends":
                sender.sendMessage(ChatColor.GREEN + "Testing friends menu...");
                player.sendMessage(ChatColor.YELLOW + "Friends test - this would open the friends interface");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown test type: " + testType);
                return true;
        }

        return true;
    }

    /**
     * Send help message
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "Menu Item System Commands");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.WHITE + "/menu setup <player|all> " + ChatColor.GRAY + "- Set up menu items");
        sender.sendMessage(ChatColor.WHITE + "/menu clear <player|all> " + ChatColor.GRAY + "- Clear menu items");
        sender.sendMessage(ChatColor.WHITE + "/menu reload " + ChatColor.GRAY + "- Reload the menu system");
        sender.sendMessage(ChatColor.WHITE + "/menu stats " + ChatColor.GRAY + "- Show system statistics");
        sender.sendMessage(ChatColor.WHITE + "/menu test <type> " + ChatColor.GRAY + "- Test menu item functionality");
        sender.sendMessage(ChatColor.WHITE + "/menu help " + ChatColor.GRAY + "- Show this help message");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.menu")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("setup", "clear", "reload", "stats", "test", "help");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "setup":
                case "clear":
                    List<String> targets = new ArrayList<>();
                    targets.add("all");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        targets.add(player.getName());
                    }
                    return targets;

                case "test":
                    return Arrays.asList("journal", "help", "settings", "friends");

                default:
                    return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }
}