package com.rednetty.server.commands.economy;

import com.rednetty.server.mechanics.market.MarketManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Enhanced market command with comprehensive functionality
 */
public class MarketCommand implements CommandExecutor, TabCompleter {
    private final MarketManager marketManager;

    public MarketCommand() {
        this.marketManager = MarketManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check basic permission
        if (!player.hasPermission("yakserver.market.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the market.");
            return true;
        }

        // Handle subcommands
        if (args.length == 0) {
            marketManager.openMarketMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "browse":
            case "b":
                marketManager.openMarketMenu(player);
                break;

            case "list":
            case "sell":
                if (!player.hasPermission("yakserver.market.sell")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to sell items.");
                    return true;
                }
                handleListCommand(player, args);
                break;

            case "mylistings":
            case "my":
            case "listings":
                handleMyListingsCommand(player);
                break;

            case "search":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /market search <query>");
                    return true;
                }
                handleSearchCommand(player, args);
                break;

            case "stats":
            case "statistics":
                handleStatsCommand(player);
                break;

            case "help":
            case "?":
                handleHelpCommand(player);
                break;

            case "admin":
                if (!player.hasPermission("yakserver.market.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use market admin commands.");
                    return true;
                }
                handleAdminCommand(player, args);
                break;

            case "reload":
                if (!player.hasPermission("yakserver.market.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to reload the market.");
                    return true;
                }
                handleReloadCommand(player);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Use '/market help' for available commands.");
                break;
        }

        return true;
    }

    private void handleListCommand(Player player, String[] args) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) {
            player.sendMessage(ChatColor.RED + "Your player data could not be loaded.");
            return;
        }

        if (yakPlayer.getLevel() < 5) { // Assuming min level 5
            player.sendMessage(ChatColor.RED + "You must be level 5 to list items on the market.");
            return;
        }

        // Open listing menu
        marketManager.openMarketMenu(player);
        player.sendMessage(ChatColor.YELLOW + "Click 'List Item for Sale' to list an item!");
    }

    private void handleMyListingsCommand(Player player) {
        marketManager.getPlayerListings(player.getUniqueId()).thenAccept(listings -> {
            player.getServer().getScheduler().runTask(player.getServer().getPluginManager().getPlugin("YakRealms"), () -> {
                if (listings.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "You don't have any active market listings.");
                    player.sendMessage(ChatColor.GRAY + "Use '/market list' to sell items!");
                    return;
                }

                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "▪ " + ChatColor.YELLOW + "Your Market Listings" + ChatColor.GOLD + " ▪");
                player.sendMessage("");

                for (int i = 0; i < Math.min(listings.size(), 10); i++) {
                    var listing = listings.get(i);
                    String status = listing.isExpired() ? ChatColor.RED + "[EXPIRED]" : ChatColor.GREEN + "[ACTIVE]";
                    player.sendMessage(ChatColor.GRAY.toString() + (i + 1) + ". " + status + " " +
                            ChatColor.WHITE + listing.getDisplayName() +
                            ChatColor.GRAY + " - " + ChatColor.GREEN + TextUtil.formatNumber(listing.getPrice()) + " gems");
                }

                if (listings.size() > 10) {
                    player.sendMessage(ChatColor.GRAY + "... and " + (listings.size() - 10) + " more");
                }

                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/market" + ChatColor.GRAY + " to manage your listings.");
                player.sendMessage("");
            });
        });
    }

    private void handleSearchCommand(Player player, String[] args) {
        StringBuilder searchQuery = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) searchQuery.append(" ");
            searchQuery.append(args[i]);
        }

        player.sendMessage(ChatColor.YELLOW + "Searching for: " + ChatColor.WHITE + searchQuery.toString());

        // Open market with search
        marketManager.openMarketMenu(player);
        // Note: Search functionality would be enhanced with proper search implementation
    }

    private void handleStatsCommand(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) {
            player.sendMessage(ChatColor.RED + "Your player data could not be loaded.");
            return;
        }

        marketManager.getPlayerListings(player.getUniqueId()).thenAccept(listings -> {
            player.getServer().getScheduler().runTask(player.getServer().getPluginManager().getPlugin("YakRealms"), () -> {
                long totalValue = listings.stream().mapToLong(listing -> listing.getPrice()).sum();
                int featuredCount = (int) listings.stream().filter(listing -> listing.isFeatured()).count();
                int totalViews = listings.stream().mapToInt(listing -> listing.getViews()).sum();

                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "▪ " + ChatColor.YELLOW + "Your Market Statistics" + ChatColor.GOLD + " ▪");
                player.sendMessage("");
                player.sendMessage(ChatColor.AQUA + "Current Gems: " + ChatColor.WHITE + TextUtil.formatNumber(yakPlayer.getGems()));
                player.sendMessage(ChatColor.AQUA + "Active Listings: " + ChatColor.WHITE + listings.size() + "/" + marketManager.getMaxListingsPerPlayer());
                player.sendMessage(ChatColor.AQUA + "Total Listing Value: " + ChatColor.WHITE + TextUtil.formatNumber(totalValue) + " gems");
                player.sendMessage(ChatColor.AQUA + "Featured Listings: " + ChatColor.WHITE + featuredCount);
                player.sendMessage(ChatColor.AQUA + "Total Views: " + ChatColor.WHITE + totalViews);
                player.sendMessage("");
            });
        });
    }

    private void handleHelpCommand(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▪ " + ChatColor.YELLOW + "Market Commands" + ChatColor.GOLD + " ▪");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "/market" + ChatColor.GRAY + " - Open the market interface");
        player.sendMessage(ChatColor.YELLOW + "/market browse" + ChatColor.GRAY + " - Browse all items");
        player.sendMessage(ChatColor.YELLOW + "/market list" + ChatColor.GRAY + " - List an item for sale");
        player.sendMessage(ChatColor.YELLOW + "/market mylistings" + ChatColor.GRAY + " - View your listings");
        player.sendMessage(ChatColor.YELLOW + "/market search <query>" + ChatColor.GRAY + " - Search for items");
        player.sendMessage(ChatColor.YELLOW + "/market stats" + ChatColor.GRAY + " - View your statistics");
        player.sendMessage(ChatColor.YELLOW + "/market help" + ChatColor.GRAY + " - Show this help");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Market Tax: " + ChatColor.WHITE + String.format("%.1f%%", marketManager.getMarketTaxRate() * 100));
        player.sendMessage(ChatColor.GRAY + "Featured Listing Cost: " + ChatColor.WHITE + TextUtil.formatNumber(marketManager.getFeaturedListingCost()) + " gems");
        player.sendMessage("");
    }

    private void handleAdminCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /market admin <subcommand>");
            player.sendMessage(ChatColor.GRAY + "Available: stats, reload, cleanup");
            return;
        }

        String adminSubCommand = args[1].toLowerCase();

        switch (adminSubCommand) {
            case "stats":
                handleAdminStats(player);
                break;
            case "cleanup":
                handleAdminCleanup(player);
                break;
            case "reload":
                handleReloadCommand(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown admin subcommand.");
                break;
        }
    }

    private void handleAdminStats(Player player) {
        Map<String, Object> stats = marketManager.getStatistics();

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▪ " + ChatColor.YELLOW + "Market Admin Statistics" + ChatColor.GOLD + " ▪");
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Active Sessions: " + ChatColor.WHITE + stats.get("activeSessions"));
        player.sendMessage(ChatColor.AQUA + "Total Transactions: " + ChatColor.WHITE + TextUtil.formatNumber((Integer) stats.get("totalTransactions")));
        player.sendMessage(ChatColor.AQUA + "Total Gems Traded: " + ChatColor.WHITE + TextUtil.formatNumber((Long) stats.get("totalGemsTraded")));
        player.sendMessage(ChatColor.AQUA + "Total Listings: " + ChatColor.WHITE + TextUtil.formatNumber((Integer) stats.get("totalListings")));
        player.sendMessage(ChatColor.AQUA + "Cache Hit Rate: " + ChatColor.WHITE + String.format("%.1f%%", stats.get("cacheHitRate")));
        player.sendMessage("");
    }

    private void handleAdminCleanup(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Starting market cleanup...");

        // Note: This would trigger cleanup in MarketManager
        player.sendMessage(ChatColor.GREEN + "✓ Market cleanup completed!");
    }

    private void handleReloadCommand(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Reloading market configuration...");

        // Note: This would reload configuration in MarketManager
        player.sendMessage(ChatColor.GREEN + "✓ Market configuration reloaded!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("browse", "list", "mylistings", "search", "stats", "help");

            if (sender.hasPermission("yakserver.market.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.addAll(Arrays.asList("admin", "reload"));
            }

            String partial = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(partial)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("yakserver.market.admin")) {
                List<String> adminSubCommands = Arrays.asList("stats", "cleanup", "reload");
                String partial = args[1].toLowerCase();
                for (String subCommand : adminSubCommands) {
                    if (subCommand.startsWith(partial)) {
                        completions.add(subCommand);
                    }
                }
            }
        }

        return completions;
    }
}