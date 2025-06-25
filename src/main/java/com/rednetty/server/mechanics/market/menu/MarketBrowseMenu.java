package com.rednetty.server.mechanics.market.menu;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.market.MarketCategory;
import com.rednetty.server.mechanics.market.MarketItem;
import com.rednetty.server.mechanics.market.MarketManager;
import com.rednetty.server.mechanics.market.MarketRepository;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced market browse menu with advanced features and chat integration
 */
public class MarketBrowseMenu extends Menu {
    private final MarketManager marketManager;
    private final MarketManager.MarketSession session;
    private final MarketCategory filterCategory;

    private List<MarketItem> currentItems = new ArrayList<>();
    private boolean isLoading = true;
    private final int itemsPerPage = 28; // 4 rows of 7 items

    public MarketBrowseMenu(Player player, MarketManager marketManager,
                            MarketManager.MarketSession session, MarketCategory filterCategory) {
        super(player, generateTitle(filterCategory, session), 54);
        this.marketManager = marketManager;
        this.session = session;
        this.filterCategory = filterCategory;

        setAutoRefresh(true, 40); // Refresh every 2 seconds
        setClickSoundsEnabled(true);
    }

    private static String generateTitle(MarketCategory category, MarketManager.MarketSession session) {
        if (category != null) {
            return "&8⚜ &6" + category.getDisplayName() + " &8⚜";
        } else if (!session.getSearchQuery().isEmpty()) {
            return "&8⚜ &6Search: " + session.getSearchQuery() + " &8⚜";
        } else {
            return "&8⚜ &6Market Browse &8⚜";
        }
    }

    @Override
    protected void onPreOpen() {
        setupStaticElements();
        loadItems();
    }

    @Override
    protected void onRefresh() {
        if (!isLoading) {
            loadItems();
        }
        updateNavigationInfo();
    }

    private void setupStaticElements() {
        // Create border
        createBorder(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        // Control panel (top row)
        setItem(1, createBackButton());
        setItem(3, createSortButton());
        setItem(4, createInfoDisplay());
        setItem(5, createRefreshButton());
        setItem(7, createSearchButton());

        // Navigation (bottom row)
        setItem(45, createPreviousPageButton());
        setItem(46, createPageInfoButton());
        setItem(49, createClearFiltersButton());
        setItem(52, createNextPageButton());
        setItem(53, createCloseButton());

        // Loading indicator
        setItem(22, createLoadingIndicator());
    }

    private void loadItems() {
        isLoading = true;
        setItem(22, createLoadingIndicator());

        CompletableFuture<List<MarketItem>> future = marketManager.getMarketItems(
                filterCategory != null ? filterCategory : session.getSelectedCategory(),
                session.getSearchQuery().isEmpty() ? null : session.getSearchQuery(),
                session.getSortOrder(),
                session.getCurrentPage(),
                itemsPerPage
        );

        future.thenAccept(items -> {
            YakRealms.getInstance().getServer().getScheduler().runTask(YakRealms.getInstance(), () -> {
                if (isOpen()) {
                    this.currentItems = items;
                    this.isLoading = false;
                    displayItems();
                    updateNavigationInfo();
                }
            });
        }).exceptionally(throwable -> {
            YakRealms.getInstance().getServer().getScheduler().runTask(YakRealms.getInstance(), () -> {
                if (isOpen()) {
                    this.isLoading = false;
                    displayError();
                }
            });
            return null;
        });
    }

    private void displayItems() {
        // Clear item display area (slots 10-43 excluding borders)
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                int slot = row * 9 + col;
                removeItem(slot);
            }
        }

        if (currentItems.isEmpty()) {
            setItem(22, createNoItemsDisplay());
            return;
        }

        // Display items
        int slot = 10;
        for (MarketItem item : currentItems) {
            if (slot >= 44) break; // Safety check

            // Skip border slots
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }

            setItem(slot, createMarketItemDisplay(item));
            slot++;
        }
    }

    private MenuItem createMarketItemDisplay(MarketItem marketItem) {
        ItemStack displayItem = marketItem.getItemStack().clone();
        ItemMeta meta = displayItem.getItemMeta();

        // Enhance the display name
        String displayName = meta != null && meta.hasDisplayName()
                ? meta.getDisplayName()
                : TextUtil.formatItemName(displayItem.getType().name());

        if (marketItem.isFeatured()) {
            displayName = ChatColor.GOLD + "⭐ " + displayName;
        }

        List<String> lore = new ArrayList<>();

        // Original lore
        if (meta != null && meta.hasLore()) {
            lore.addAll(meta.getLore());
            lore.add("");
        }

        // Market information
        lore.add(ChatColor.YELLOW + "▪ Market Information:");
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.GREEN + TextUtil.formatNumber(marketItem.getPrice()) + " gems");
        lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + marketItem.getOwnerName());
        lore.add(ChatColor.GRAY + "Category: " + marketItem.getCategory().getColoredDisplayName());
        lore.add(ChatColor.GRAY + "Amount: " + ChatColor.WHITE + marketItem.getAmount());

        if (marketItem.getViews() > 0) {
            lore.add(ChatColor.GRAY + "Views: " + ChatColor.WHITE + marketItem.getViews());
        }

        lore.add(ChatColor.GRAY + "Expires: " + ChatColor.WHITE + marketItem.getFormattedTimeRemaining());
        lore.add("");

        // Purchase information
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(getPlayer());
        boolean canAfford = yakPlayer != null && yakPlayer.getGems() >= marketItem.getPrice();
        boolean isOwnItem = marketItem.getOwnerUuid().equals(getPlayer().getUniqueId());

        if (isOwnItem) {
            lore.add(ChatColor.YELLOW + "This is your item!");
            lore.add(ChatColor.GRAY + "You cannot purchase your own items.");
        } else if (canAfford) {
            lore.add(ChatColor.GREEN + "✓ Click to purchase!");
            lore.add(ChatColor.GRAY + "You have enough gems to buy this.");
        } else {
            lore.add(ChatColor.RED + "✗ Insufficient gems!");
            lore.add(ChatColor.GRAY + "You need " + ChatColor.RED +
                    TextUtil.formatNumber(marketItem.getPrice() - (yakPlayer != null ? yakPlayer.getGems() : 0)) +
                    ChatColor.GRAY + " more gems.");
        }

        if (meta == null) {
            meta = displayItem.getItemMeta();
        }
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        displayItem.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(displayItem);

        if (!isOwnItem && canAfford) {
            menuItem.setClickHandler((player, slot) -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                // Use the MarketManager's chat system for confirmation
                marketManager.startPurchaseConfirmation(player, marketItem.getItemId());
            });
        } else {
            menuItem.setClickHandler((player, slot) -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                if (isOwnItem) {
                    player.sendMessage(ChatColor.YELLOW + "This is your own item! Use 'My Listings' to manage it.");
                } else {
                    player.sendMessage(ChatColor.RED + "You need " +
                            TextUtil.formatNumber(marketItem.getPrice()) + " gems to purchase this item.");
                }
            });
        }

        return menuItem;
    }

    // Control buttons
    private MenuItem createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "← Back to Market Hub");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Return to the main market menu.",
                "",
                ChatColor.YELLOW + "Click to go back!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            new MarketMainMenu(player, marketManager, session).open();
        });
    }

    private MenuItem createSortButton() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.BLUE + "🔄 Sort Order");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + getSortDisplayName(session.getSortOrder()),
                "",
                ChatColor.YELLOW + "Click to change sort order!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            cycleSortOrder();
            loadItems();
        });
    }

    private MenuItem createInfoDisplay() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        String title = filterCategory != null ? filterCategory.getColoredDisplayName() :
                !session.getSearchQuery().isEmpty() ? "Search Results" : "All Items";

        meta.setDisplayName(ChatColor.AQUA + "📋 " + title);
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Showing items from the market.",
                filterCategory != null ? ChatColor.GRAY + "Category: " + filterCategory.getColoredDisplayName() : "",
                !session.getSearchQuery().isEmpty() ? ChatColor.GRAY + "Search: " + ChatColor.WHITE + session.getSearchQuery() : "",
                "",
                ChatColor.GRAY + "Items loaded: " + ChatColor.WHITE + currentItems.size()
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createRefreshButton() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "🔄 Refresh");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Refresh the item list to see",
                ChatColor.GRAY + "the latest available items.",
                "",
                ChatColor.YELLOW + "Click to refresh!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            loadItems();
        });
    }

    private MenuItem createSearchButton() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "🔍 Search");
        meta.setLore(Arrays.asList(
                "",
                session.getSearchQuery().isEmpty()
                        ? ChatColor.GRAY + "No active search filter."
                        : ChatColor.GRAY + "Current search: " + ChatColor.WHITE + session.getSearchQuery(),
                "",
                ChatColor.YELLOW + "Click to modify search!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            // Use MarketManager's search input system
            marketManager.startSearchInput(player);
        });
    }

    // Navigation buttons
    private MenuItem createPreviousPageButton() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();

        boolean canGoPrevious = session.getCurrentPage() > 0;

        meta.setDisplayName(canGoPrevious ? ChatColor.GREEN + "← Previous Page" : ChatColor.GRAY + "← Previous Page");
        meta.setLore(Arrays.asList(
                "",
                canGoPrevious
                        ? ChatColor.GRAY + "Go to page " + session.getCurrentPage()
                        : ChatColor.GRAY + "Already on first page.",
                "",
                canGoPrevious ? ChatColor.YELLOW + "Click to go back!" : ChatColor.GRAY + "No previous page."
        ));

        item.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(item);
        if (canGoPrevious) {
            menuItem.setClickHandler((player, slot) -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                session.setCurrentPage(session.getCurrentPage() - 1);
                loadItems();
            });
        }

        return menuItem;
    }

    private MenuItem createNextPageButton() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();

        boolean canGoNext = currentItems.size() >= itemsPerPage;

        meta.setDisplayName(canGoNext ? ChatColor.GREEN + "Next Page →" : ChatColor.GRAY + "Next Page →");
        meta.setLore(Arrays.asList(
                "",
                canGoNext
                        ? ChatColor.GRAY + "Go to page " + (session.getCurrentPage() + 2)
                        : ChatColor.GRAY + "No more pages available.",
                "",
                canGoNext ? ChatColor.YELLOW + "Click to continue!" : ChatColor.GRAY + "End of results."
        ));

        item.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(item);
        if (canGoNext) {
            menuItem.setClickHandler((player, slot) -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                session.setCurrentPage(session.getCurrentPage() + 1);
                loadItems();
            });
        }

        return menuItem;
    }

    private MenuItem createPageInfoButton() {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "📄 Page " + (session.getCurrentPage() + 1));
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Currently viewing page " + (session.getCurrentPage() + 1),
                ChatColor.GRAY + "Showing " + currentItems.size() + " items",
                "",
                ChatColor.GRAY + "Use arrow buttons to navigate."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createClearFiltersButton() {
        ItemStack item = new ItemStack(Material.BUCKET);
        ItemMeta meta = item.getItemMeta();

        boolean hasFilters = session.getSelectedCategory() != null || !session.getSearchQuery().isEmpty();

        meta.setDisplayName(ChatColor.YELLOW + "🧹 Clear Filters");
        meta.setLore(Arrays.asList(
                "",
                hasFilters
                        ? ChatColor.GRAY + "Remove all active filters"
                        : ChatColor.GRAY + "No active filters to clear.",
                hasFilters
                        ? ChatColor.GRAY + "and show all items."
                        : "",
                "",
                hasFilters ? ChatColor.YELLOW + "Click to clear!" : ChatColor.GRAY + "No filters active."
        ));

        item.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(item);
        if (hasFilters) {
            menuItem.setClickHandler((player, slot) -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                session.setSelectedCategory(null);
                session.setSearchQuery("");
                session.setCurrentPage(0);
                new MarketBrowseMenu(player, marketManager, session, null).open();
            });
        }

        return menuItem;
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

    // Special display items
    private MenuItem createLoadingIndicator() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "⏳ Loading items...");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Please wait while we load",
                ChatColor.GRAY + "the latest market items.",
                "",
                ChatColor.YELLOW + "This may take a moment."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createNoItemsDisplay() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "No items found");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "No items match your current",
                ChatColor.GRAY + "search criteria or filters.",
                "",
                ChatColor.YELLOW + "Try adjusting your search",
                ChatColor.YELLOW + "or clearing filters."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createErrorDisplay() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "Error loading items");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "There was an error loading",
                ChatColor.GRAY + "market items from the database.",
                "",
                ChatColor.YELLOW + "Try refreshing or contact staff."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    // Utility methods
    private void cycleSortOrder() {
        MarketRepository.SortOrder[] orders = MarketRepository.SortOrder.values();
        int currentIndex = Arrays.asList(orders).indexOf(session.getSortOrder());
        int nextIndex = (currentIndex + 1) % orders.length;
        session.setSortOrder(orders[nextIndex]);
    }

    private String getSortDisplayName(MarketRepository.SortOrder sortOrder) {
        switch (sortOrder) {
            case NEWEST_FIRST: return "Newest First";
            case OLDEST_FIRST: return "Oldest First";
            case PRICE_LOW_TO_HIGH: return "Price: Low to High";
            case PRICE_HIGH_TO_LOW: return "Price: High to Low";
            case MOST_VIEWED: return "Most Viewed";
            default: return "Unknown";
        }
    }

    private void displayError() {
        // Clear display area and show error
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                int slot = row * 9 + col;
                removeItem(slot);
            }
        }
        setItem(22, createErrorDisplay());
    }

    private void updateNavigationInfo() {
        // Update page info button
        setItem(46, createPageInfoButton());

        // Update navigation buttons
        setItem(45, createPreviousPageButton());
        setItem(52, createNextPageButton());
    }
}