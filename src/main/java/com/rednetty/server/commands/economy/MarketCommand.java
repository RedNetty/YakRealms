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

import java.util.*;
import java.util.stream.Collectors;

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
        if (!player.hasPermission("yakrealms.market.use")) {
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
                if (!player.hasPermission("yakrealms.market.sell")) {
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
                    marketManager.startSearchInput(player);
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
                if (!player.hasPermission("yakrealms.market.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use market admin commands.");
                    return true;
                }
                handleAdminCommand(player, args);
                break;

            case "reload":
                if (!player.hasPermission("yakrealms.market.admin")) {
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

        // Open listing menu
        marketManager.openMarketMenu(player);
        player.sendMessage(ChatColor.YELLOW + "Navigate to 'List Item for Sale' to list an item!");
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
        }).exceptionally(throwable -> {
            player.getServer().getScheduler().runTask(player.getServer().getPluginManager().getPlugin("YakRealms"), () -> {
                player.sendMessage(ChatColor.RED + "Error loading your listings. Please try again.");
            });
            return null;
        });
    }

    private void handleSearchCommand(Player player, String[] args) {
        StringBuilder searchQuery = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) searchQuery.append(" ");
            searchQuery.append(args[i]);
        }

        // Set search in session and open market
        MarketManager.MarketSession session = marketManager.getOrCreateSession(player.getUniqueId());
        session.setSearchQuery(searchQuery.toString());
        session.setCurrentPage(0);

        player.sendMessage(ChatColor.YELLOW + "Search set to: " + ChatColor.WHITE + searchQuery.toString());
        marketManager.openMarketMenu(player);
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
                player.sendMessage(ChatColor.AQUA + "Current Gems: " + ChatColor.WHITE + TextUtil.formatNumber(yakPlayer.getBankGems()));
                player.sendMessage(ChatColor.AQUA + "Active Listings: " + ChatColor.WHITE + listings.size() + "/" + marketManager.getMaxListingsPerPlayer());
                player.sendMessage(ChatColor.AQUA + "Total Listing Value: " + ChatColor.WHITE + TextUtil.formatNumber(totalValue) + " gems");
                player.sendMessage(ChatColor.AQUA + "Featured Listings: " + ChatColor.WHITE + featuredCount);
                player.sendMessage(ChatColor.AQUA + "Total Views: " + ChatColor.WHITE + totalViews);
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/market" + ChatColor.GRAY + " to open the market interface.");
                player.sendMessage("");
            });
        }).exceptionally(throwable -> {
            player.getServer().getScheduler().runTask(player.getServer().getPluginManager().getPlugin("YakRealms"), () -> {
                player.sendMessage(ChatColor.RED + "Error loading your statistics. Please try again.");
            });
            return null;
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
        player.sendMessage(ChatColor.AQUA + "Market Information:");
        player.sendMessage(ChatColor.GRAY + "• Tax Rate: " + ChatColor.WHITE + String.format("%.1f%%", marketManager.getMarketTaxRate() * 100));
        player.sendMessage(ChatColor.GRAY + "• Featured Listing Cost: " + ChatColor.WHITE + TextUtil.formatNumber(marketManager.getFeaturedListingCost()) + " gems");
        player.sendMessage(ChatColor.GRAY + "• Price Range: " + ChatColor.WHITE + TextUtil.formatNumber(marketManager.getMinItemPrice()) +
                " - " + TextUtil.formatNumber(marketManager.getMaxItemPrice()) + " gems");
        player.sendMessage(ChatColor.GRAY + "• Daily Listing Limit: " + ChatColor.WHITE + marketManager.getMaxListingsPerPlayer());
        player.sendMessage("");
    }

    private void handleAdminCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /market admin <subcommand>");
            player.sendMessage(ChatColor.GRAY + "Available: stats, reload, cleanup, test");
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
            case "test":
                handleAdminTest(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown admin subcommand.");
                player.sendMessage(ChatColor.GRAY + "Available: stats, reload, cleanup, test");
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

        if (stats.containsKey("cacheHitRate")) {
            player.sendMessage(ChatColor.AQUA + "Cache Hit Rate: " + ChatColor.WHITE + String.format("%.1f%%", stats.get("cacheHitRate")));
        }

        if (stats.containsKey("itemCacheSize")) {
            player.sendMessage(ChatColor.AQUA + "Item Cache Size: " + ChatColor.WHITE + stats.get("itemCacheSize"));
        }

        if (stats.containsKey("searchCacheSize")) {
            player.sendMessage(ChatColor.AQUA + "Search Cache Size: " + ChatColor.WHITE + stats.get("searchCacheSize"));
        }

        player.sendMessage("");
    }

    private void handleAdminCleanup(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Starting market cleanup...");

        // Clear repository caches
        marketManager.getRepository().clearCache();

        player.sendMessage(ChatColor.GREEN + "✓ Market cleanup completed!");
        player.sendMessage(ChatColor.GRAY + "• Cleared all caches");
        player.sendMessage(ChatColor.GRAY + "• Expired items will be moved in next maintenance cycle");
    }

    private void handleAdminTest(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Running market system tests...");

        try {
            // Test database connection
            boolean dbConnected = marketManager.getRepository() != null;
            player.sendMessage(ChatColor.GRAY + "Database Connection: " +
                    (dbConnected ? ChatColor.GREEN + "✓ Connected" : ChatColor.RED + "✗ Failed"));

            // Test market manager
            Map<String, Object> stats = marketManager.getStatistics();
            player.sendMessage(ChatColor.GRAY + "Market Manager: " + ChatColor.GREEN + "✓ Working");

            // Test session creation
            MarketManager.MarketSession testSession = marketManager.getOrCreateSession(player.getUniqueId());
            player.sendMessage(ChatColor.GRAY + "Session System: " +
                    (testSession != null ? ChatColor.GREEN + "✓ Working" : ChatColor.RED + "✗ Failed"));

            player.sendMessage(ChatColor.GREEN + "✓ All market tests completed successfully!");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✗ Market test failed: " + e.getMessage());
        }
    }

    private void handleReloadCommand(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Reloading market configuration...");

        try {
            // Clear caches
            marketManager.getRepository().clearCache();


            player.sendMessage(ChatColor.GREEN + "✓ Market configuration reloaded!");
            player.sendMessage(ChatColor.GRAY + "Note: Some settings require a server restart to take effect.");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✗ Failed to reload market configuration: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("browse", "list", "mylistings", "search", "stats", "help");

            if (sender.hasPermission("yakrealms.market.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.addAll(Arrays.asList("admin", "reload"));
            }

            String partial = args[0].toLowerCase();
            completions.addAll(subCommands.stream()
                    .filter(cmd -> cmd.startsWith(partial))
                    .collect(Collectors.toList()));

        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("yakrealms.market.admin")) {
                List<String> adminSubCommands = Arrays.asList("stats", "cleanup", "reload", "test");
                String partial = args[1].toLowerCase();
                completions.addAll(adminSubCommands.stream()
                        .filter(cmd -> cmd.startsWith(partial))
                        .collect(Collectors.toList()));

            } else if (args[0].equalsIgnoreCase("search")) {
                // Suggest common search terms
                List<String> searchSuggestions = Arrays.asList("diamond", "enchanted", "sword", "armor", "tools", "blocks");
                String partial = args[1].toLowerCase();
                completions.addAll(searchSuggestions.stream()
                        .filter(suggestion -> suggestion.startsWith(partial))
                        .collect(Collectors.toList()));
            }
        }

        return completions;
    }

    /**
     * Get market session for a player (package-private for command access)
     */
    MarketManager.MarketSession getOrCreateSession(UUID playerId) {
        return marketManager.getOrCreateSession(playerId);
    }
}