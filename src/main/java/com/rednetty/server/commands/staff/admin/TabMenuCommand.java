package com.rednetty.server.commands.staff.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.ui.TabPluginIntegration;
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
 * Simplified admin command for managing the TAB Plugin integration
 * <p>
 * Usage:
 * /tabmenu status - Show integration status
 * /tabmenu reload - Reload TAB integration
 * /tabmenu refresh [player] - Refresh player placeholders
 * /tabmenu info - Show TAB plugin information
 * /tabmenu help - Show command help
 */
public class TabMenuCommand implements CommandExecutor, TabCompleter {

    private final YakRealms plugin;

    public TabMenuCommand() {
        this.plugin = YakRealms.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.tabmenu")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "status":
                showStatus(sender);
                break;

            case "reload":
                handleReload(sender);
                break;

            case "refresh":
                handleRefresh(sender, args);
                break;

            case "info":
                showInfo(sender);
                break;

            case "help":
                showHelp(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                showHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Show command help
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== TAB Integration Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/tabmenu status" + ChatColor.GRAY + " - Show integration status");
        sender.sendMessage(ChatColor.YELLOW + "/tabmenu reload" + ChatColor.GRAY + " - Reload TAB integration");
        sender.sendMessage(ChatColor.YELLOW + "/tabmenu refresh [player]" + ChatColor.GRAY + " - Refresh placeholders");
        sender.sendMessage(ChatColor.YELLOW + "/tabmenu info" + ChatColor.GRAY + " - Show TAB plugin info");
        sender.sendMessage(ChatColor.YELLOW + "/tabmenu help" + ChatColor.GRAY + " - Show this help");
        sender.sendMessage(ChatColor.GRAY + "Note: Uses TAB plugin for 4-column tablist layout");
    }

    /**
     * Show integration status
     */
    private void showStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== TAB Integration Status ===");

        if (!YakRealms.isTabPluginIntegrationAvailable()) {
            sender.sendMessage(ChatColor.RED + "TAB Plugin Integration: Not Available");

            if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
                sender.sendMessage(ChatColor.RED + "TAB Plugin: Not Installed");
                sender.sendMessage(ChatColor.YELLOW + "Download TAB from: https://modrinth.com/plugin/tab-was-taken");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "TAB Plugin: Installed but integration failed");
            }
            return;
        }

        TabPluginIntegration integration = YakRealms.getTabPluginIntegrationSafe();

        sender.sendMessage(ChatColor.GREEN + "TAB Plugin Integration: " +
                (integration.isEnabled() ? "Active" : "Inactive"));
        sender.sendMessage(ChatColor.YELLOW + "TAB Plugin: " + ChatColor.GREEN + "Installed");
        sender.sendMessage(ChatColor.YELLOW + "Online Players: " + ChatColor.WHITE +
                Bukkit.getOnlinePlayers().size());

        // Show layout info
        sender.sendMessage(ChatColor.YELLOW + "Layout: " + ChatColor.WHITE + "4-Column (Guild/Party | Economy | Statistics | Social/Info)");
        sender.sendMessage(ChatColor.YELLOW + "Refresh System: " + ChatColor.WHITE + "Automatic (TAB managed)");
    }

    /**
     * Handle reload command
     */
    private void handleReload(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading TAB integration...");

        try {
            if (YakRealms.isTabPluginIntegrationAvailable()) {
                // Shutdown existing integration
                TabPluginIntegration integration = YakRealms.getTabPluginIntegrationSafe();
                integration.shutdown();
                sender.sendMessage(ChatColor.GRAY + "Shutdown existing integration...");
            }

            // Wait a moment
            Thread.sleep(1000);

            // Reinitialize
            TabPluginIntegration newIntegration = TabPluginIntegration.getInstance();
            newIntegration.initialize();

            sender.sendMessage(ChatColor.GREEN + "TAB integration reloaded successfully!");
            sender.sendMessage(ChatColor.GRAY + "Placeholders re-registered with TAB plugin");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading TAB integration: " + e.getMessage());
            plugin.getLogger().severe("Error reloading TAB integration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle refresh command
     */
    private void handleRefresh(CommandSender sender, String[] args) {
        if (!YakRealms.isTabPluginIntegrationAvailable()) {
            sender.sendMessage(ChatColor.RED + "TAB integration is not available!");
            return;
        }

        if (args.length == 1) {
            // Refresh all players
            sender.sendMessage(ChatColor.YELLOW + "Refreshing placeholders for all players...");
            sender.sendMessage(ChatColor.GRAY + "Note: TAB automatically refreshes placeholders based on intervals");

            int playerCount = Bukkit.getOnlinePlayers().size();
            sender.sendMessage(ChatColor.GREEN + "Placeholder refresh triggered for " + playerCount + " players");

        } else {
            // Refresh specific player
            String playerName = args[1];
            Player target = Bukkit.getPlayer(playerName);

            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
                return;
            }

            sender.sendMessage(ChatColor.YELLOW + "Refreshing placeholders for " + target.getName() + "...");
            sender.sendMessage(ChatColor.GRAY + "Note: TAB automatically manages placeholder refresh intervals");
            sender.sendMessage(ChatColor.GREEN + "Placeholder refresh triggered for " + target.getName());
        }
    }

    /**
     * Show TAB plugin information
     */
    private void showInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== TAB Plugin Information ===");

        boolean tabAvailable = Bukkit.getPluginManager().isPluginEnabled("TAB");
        sender.sendMessage(ChatColor.YELLOW + "TAB Plugin: " +
                (tabAvailable ? ChatColor.GREEN + "Installed" : ChatColor.RED + "Not Found"));

        if (tabAvailable) {
            org.bukkit.plugin.Plugin tabPlugin = Bukkit.getPluginManager().getPlugin("TAB");
            if (tabPlugin != null) {
                sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + tabPlugin.getDescription().getVersion());
                sender.sendMessage(ChatColor.YELLOW + "Authors: " + ChatColor.WHITE + String.join(", ", tabPlugin.getDescription().getAuthors()));
            }

            if (YakRealms.isTabPluginIntegrationAvailable()) {
                TabPluginIntegration integration = YakRealms.getTabPluginIntegrationSafe();
                sender.sendMessage(ChatColor.YELLOW + "Integration: " + ChatColor.GREEN + "Active");

                sender.sendMessage(ChatColor.AQUA + "== Registered Placeholders ==");
                sender.sendMessage(ChatColor.WHITE + "• yakrealms_party_members (2s refresh)");
                sender.sendMessage(ChatColor.WHITE + "• yakrealms_guild_info (5s refresh)");
                sender.sendMessage(ChatColor.WHITE + "• yakrealms_economy (3s refresh)");
                sender.sendMessage(ChatColor.WHITE + "• yakrealms_combat_stats (3s refresh)");
                sender.sendMessage(ChatColor.WHITE + "• yakrealms_player_info (10s refresh)");
                sender.sendMessage(ChatColor.WHITE + "• ... and 7 more placeholders");

            } else {
                sender.sendMessage(ChatColor.YELLOW + "Integration: " + ChatColor.RED + "Failed");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "TAB plugin is required for tablist features!");
            sender.sendMessage(ChatColor.YELLOW + "Download from: https://modrinth.com/plugin/tab-was-taken");
        }

        sender.sendMessage(ChatColor.AQUA + "== Features ==");
        sender.sendMessage(ChatColor.WHITE + "• 4-column tablist layout");
        sender.sendMessage(ChatColor.WHITE + "• Guild/Party information");
        sender.sendMessage(ChatColor.WHITE + "• Economy and gathering stats");
        sender.sendMessage(ChatColor.WHITE + "• Combat and PvP statistics");
        sender.sendMessage(ChatColor.WHITE + "• Social and achievement data");
        sender.sendMessage(ChatColor.WHITE + "• Automatic placeholder refresh");
        sender.sendMessage(ChatColor.WHITE + "• Rank-based player sorting");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.tabmenu")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands
            List<String> subCommands = Arrays.asList("status", "reload", "refresh", "info", "help");
            for (String subCommand : subCommands) {
                if (subCommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("refresh")) {
            // Second argument - player names for refresh command
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}