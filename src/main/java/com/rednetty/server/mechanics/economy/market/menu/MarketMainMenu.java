package com.rednetty.server.mechanics.economy.market.menu;

import com.rednetty.server.mechanics.economy.market.MarketCategory;
import com.rednetty.server.mechanics.economy.market.MarketManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced main market menu with modern design and functionality
 */
public class MarketMainMenu extends Menu {
    private final MarketManager marketManager;
    private final MarketManager.MarketSession session;

    public MarketMainMenu(Player player, MarketManager marketManager, MarketManager.MarketSession session) {
        super(player, "&8⚜ &6&lMarket Hub &8⚜", 45);
        this.marketManager = marketManager;
        this.session = session;

        setAutoRefresh(true, 100); // Refresh every 5 seconds
        setClickSoundsEnabled(true);
    }

    @Override
    protected void onPreOpen() {
        setupMenu();
    }

    @Override
    protected void onRefresh() {
        updateStatistics();
    }

    private void setupMenu() {
        // Create border
        createBorder(Material.BLACK_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        // Header decoration
        setItem(4, createHeaderItem());

        // Main navigation buttons
        setItem(19, createBrowseAllButton());
        setItem(20, createCategoriesButton());
        setItem(21, createSearchButton());
        setItem(23, createListItemButton());
        setItem(24, createMyListingsButton());
        setItem(25, createFeaturedButton());

        // Statistics panel
        setItem(38, createStatisticsButton());
        setItem(39, createPlayerStatsButton());
        setItem(40, createHelpButton());
        setItem(41, createSettingsButton());

        // Quick categories (bottom row)
        setItem(28, createQuickCategoryButton(MarketCategory.WEAPONS));
        setItem(29, createQuickCategoryButton(MarketCategory.ARMOR));
        setItem(30, createQuickCategoryButton(MarketCategory.TOOLS));
        setItem(32, createQuickCategoryButton(MarketCategory.ENCHANTED));

        // Close button
        setItem(40, createCloseButton());
    }

    private MenuItem createHeaderItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "✦ " + ChatColor.YELLOW + "Market Hub" + ChatColor.GOLD + " ✦");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Welcome to the Global Market!",
                ChatColor.GRAY + "Buy and sell items with other players.",
                "",
                ChatColor.YELLOW + "Tips:",
                ChatColor.GRAY + "• Use categories to find items faster",
                ChatColor.GRAY + "• Search for specific items",
                ChatColor.GRAY + "• Check featured items for deals",
                ""
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createBrowseAllButton() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "🔍 Browse All Items");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Browse all available items",
                ChatColor.GRAY + "on the market.",
                "",
                ChatColor.YELLOW + "Click to browse!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new MarketBrowseMenu(player, marketManager, session, null).open();
        });
    }

    private MenuItem createCategoriesButton() {
        ItemStack item = new ItemStack(Material.BOOKSHELF);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.BLUE + "📚 Browse Categories");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Browse items organized",
                ChatColor.GRAY + "by category.",
                "",
                ChatColor.YELLOW + "Click to view categories!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new MarketCategoryMenu(player, marketManager, session).open();
        });
    }

    private MenuItem createSearchButton() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "🔎 Search Items");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Search for specific items",
                ChatColor.GRAY + "by name or type.",
                "",
                session.getSearchQuery().isEmpty()
                        ? ChatColor.YELLOW + "Click to start searching!"
                        : ChatColor.YELLOW + "Current: " + ChatColor.WHITE + session.getSearchQuery(),
                session.getSearchQuery().isEmpty()
                        ? ""
                        : ChatColor.GRAY + "Click to modify search!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.closeInventory();

            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "⚡ Enter your search term in chat:");
            player.sendMessage(ChatColor.GRAY + "Type your search or 'cancel' to go back.");
            player.sendMessage("");

            // Note: Search handling would be done via chat listener in MarketManager
            // For now, we'll open browse menu with current search
            new MarketBrowseMenu(player, marketManager, session, null).open();
        });
    }

    private MenuItem createListItemButton() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        boolean canList = yakPlayer != null; // Assuming min level 5

        meta.setDisplayName(ChatColor.GREEN + "💎 List Item for Sale");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "List an item from your",
                ChatColor.GRAY + "inventory for sale.",
                "",
                canList
                        ? ChatColor.YELLOW + "Click to list an item!"
                        : ChatColor.RED + "Requires level 5 to use.",
                canList && yakPlayer != null
                        ? ChatColor.GRAY + "Daily listings: " + ChatColor.WHITE + "TODO" + "/" + marketManager.getMaxListingsPerPlayer()
                        : ""
        ));

        item.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(item);
        if (canList) {
            menuItem.setClickHandler((player, slot) -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new MarketListingMenu(player, marketManager, session).open();
            });
        } else {
            menuItem.setClickHandler((player, slot) -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage(ChatColor.RED + "You need to be level 5 to list items!");
            });
        }

        return menuItem;
    }

    private MenuItem createMyListingsButton() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "📦 My Listings");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "View and manage your",
                ChatColor.GRAY + "active market listings.",
                "",
                ChatColor.YELLOW + "Click to view!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new MarketPlayerListingsMenu(player, marketManager, session).open();
        });
    }

    private MenuItem createFeaturedButton() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "⭐ Featured Items");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Browse premium featured",
                ChatColor.GRAY + "items and special deals.",
                "",
                ChatColor.YELLOW + "Click to view featured!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            // TODO: Implement featured items filter
            new MarketBrowseMenu(player, marketManager, session, null).open();
        });
    }

    private MenuItem createQuickCategoryButton(MarketCategory category) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(category.getColoredDisplayName());
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Quick browse " + category.getDisplayName().toLowerCase(),
                "",
                ChatColor.YELLOW + "Click to browse!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            session.setSelectedCategory(category);
            new MarketBrowseMenu(player, marketManager, session, category).open();
        });
    }

    private MenuItem createStatisticsButton() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "📊 Market Statistics");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "View global market statistics",
                ChatColor.GRAY + "and trends.",
                "",
                ChatColor.YELLOW + "Loading statistics..."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.sendMessage(ChatColor.YELLOW + "Market statistics feature coming soon!");
        });
    }

    private MenuItem createPlayerStatsButton() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);

        meta.setDisplayName(ChatColor.GREEN + "👤 Your Stats");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "View your personal",
                ChatColor.GRAY + "market statistics.",
                "",
                yakPlayer != null ? ChatColor.YELLOW + "Gems: " + ChatColor.WHITE + TextUtil.formatNumber(yakPlayer.getBankGems()) : "",
                ChatColor.YELLOW + "Click to view details!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            showPlayerStats(player);
        });
    }

    private MenuItem createHelpButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "❓ Help & Guide");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Learn how to use the",
                ChatColor.GRAY + "market system effectively.",
                "",
                ChatColor.YELLOW + "Click for help!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            showHelpInformation(player);
        });
    }

    private MenuItem createSettingsButton() {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GRAY + "⚙ Settings");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Configure your market",
                ChatColor.GRAY + "preferences and settings.",
                "",
                ChatColor.YELLOW + "Click to configure!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.sendMessage(ChatColor.YELLOW + "Market settings feature coming soon!");
        });
    }

    private MenuItem createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "✕ Close");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Close the market interface.",
                "",
                ChatColor.YELLOW + "Click to close!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            player.closeInventory();
        });
    }

    private void updateStatistics() {
        // Update statistics button with real data
        CompletableFuture.runAsync(() -> {
            Map<String, Object> stats = marketManager.getStatistics();

            getPlugin().getServer().getScheduler().runTask(getPlugin(), () -> {
                if (isOpen()) {
                    ItemStack statsItem = getInventory().getItem(38);
                    if (statsItem != null) {
                        ItemMeta meta = statsItem.getItemMeta();
                        if (meta != null) {
                            meta.setLore(Arrays.asList(
                                    "",
                                    ChatColor.GRAY + "View global market statistics",
                                    ChatColor.GRAY + "and trends.",
                                    "",
                                    ChatColor.AQUA + "Active Sessions: " + ChatColor.WHITE + stats.get("activeSessions"),
                                    ChatColor.AQUA + "Total Transactions: " + ChatColor.WHITE + TextUtil.formatNumber((Integer) stats.get("totalTransactions")),
                                    ChatColor.AQUA + "Gems Traded: " + ChatColor.WHITE + TextUtil.formatNumber((Long) stats.get("totalGemsTraded")),
                                    "",
                                    ChatColor.YELLOW + "Click for more details!"
                            ));
                            statsItem.setItemMeta(meta);
                        }
                    }
                }
            });
        });
    }

    private void showPlayerStats(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) {
            player.sendMessage(ChatColor.RED + "Could not load your statistics.");
            return;
        }

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▪ " + ChatColor.YELLOW + "Your Market Statistics" + ChatColor.GOLD + " ▪");
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Gems: " + ChatColor.WHITE + TextUtil.formatNumber(yakPlayer.getBankGems()));
        player.sendMessage(ChatColor.AQUA + "Level: " + ChatColor.WHITE + yakPlayer.getLevel());
        player.sendMessage(ChatColor.AQUA + "Daily Listings Used: " + ChatColor.WHITE + "TODO" + "/" + marketManager.getMaxListingsPerPlayer());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/market" + ChatColor.GRAY + " to return to the market.");
        player.sendMessage("");
    }

    private void showHelpInformation(Player player) {
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▪ " + ChatColor.YELLOW + "Market Help & Guide" + ChatColor.GOLD + " ▪");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Buying Items:");
        player.sendMessage(ChatColor.GRAY + "• Browse categories or search for items");
        player.sendMessage(ChatColor.GRAY + "• Click on items to purchase them");
        player.sendMessage(ChatColor.GRAY + "• Make sure you have enough gems!");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Selling Items:");
        player.sendMessage(ChatColor.GRAY + "• Click 'List Item for Sale'");
        player.sendMessage(ChatColor.GRAY + "• Select an item from your inventory");
        player.sendMessage(ChatColor.GRAY + "• Set a competitive price");
        player.sendMessage(ChatColor.GRAY + "• Tax rate: " + String.format("%.1f%%", marketManager.getMarketTaxRate() * 100));
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Featured Listings:");
        player.sendMessage(ChatColor.GRAY + "• Cost: " + TextUtil.formatNumber(marketManager.getFeaturedListingCost()) + " gems");
        player.sendMessage(ChatColor.GRAY + "• Appear at the top of browse lists");
        player.sendMessage(ChatColor.GRAY + "• Get more visibility for your items");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/market" + ChatColor.GRAY + " to return to the market.");
        player.sendMessage("");
    }

    private org.bukkit.plugin.Plugin getPlugin() {
        return com.rednetty.server.YakRealms.getInstance();
    }
}