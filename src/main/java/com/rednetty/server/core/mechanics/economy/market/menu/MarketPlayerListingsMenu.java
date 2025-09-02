package com.rednetty.server.core.mechanics.economy.market.menu;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.economy.market.MarketItem;
import com.rednetty.server.core.mechanics.economy.market.MarketManager;
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
 * Menu for viewing and managing player's market listings with chat integration
 */
public class MarketPlayerListingsMenu extends Menu {
    private final MarketManager marketManager;
    private final MarketManager.MarketSession session;

    private List<MarketItem> playerListings = new ArrayList<>();
    private boolean isLoading = true;

    public MarketPlayerListingsMenu(Player player, MarketManager marketManager, MarketManager.MarketSession session) {
        super(player, "&8⚜ &6&lYour Market Listings &8⚜", 54);
        this.marketManager = marketManager;
        this.session = session;

        setAutoRefresh(true, 60); // Refresh every 3 seconds
        setClickSoundsEnabled(true);
    }

    @Override
    protected void onPreOpen() {
        setupStaticElements();
        loadPlayerListings();
    }

    @Override
    protected void onRefresh() {
        if (!isLoading) {
            loadPlayerListings();
        }
    }

    private void setupStaticElements() {
        // Create border
        createBorder(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        // Control panel
        setItem(1, createBackButton());
        setItem(4, createInfoDisplay());
        setItem(7, createRefreshButton());

        // Bottom controls
        setItem(45, createListNewItemButton());
        setItem(49, createStatsButton());
        setItem(53, createCloseButton());

        // Loading indicator
        setItem(22, createLoadingIndicator());
    }

    private void loadPlayerListings() {
        isLoading = true;
        setItem(22, createLoadingIndicator());

        CompletableFuture<List<MarketItem>> future = marketManager.getPlayerListings(getPlayer().getUniqueId());

        future.thenAccept(listings -> {
            YakRealms.getInstance().getServer().getScheduler().runTask(YakRealms.getInstance(), () -> {
                if (isOpen()) {
                    this.playerListings = listings;
                    this.isLoading = false;
                    displayListings();
                    updateInfoDisplay();
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

    private void displayListings() {
        // Clear display area (slots 10-43 excluding borders)
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                int slot = row * 9 + col;
                removeItem(slot);
            }
        }

        if (playerListings.isEmpty()) {
            setItem(22, createNoListingsDisplay());
            return;
        }

        // Display listings
        int slot = 10;
        for (MarketItem listing : playerListings) {
            if (slot >= 44) break; // Safety check

            // Skip border slots
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }

            setItem(slot, createListingDisplay(listing));
            slot++;
        }
    }

    private MenuItem createListingDisplay(MarketItem listing) {
        ItemStack displayItem = listing.getItemStack().clone();
        ItemMeta meta = displayItem.getItemMeta();

        // Enhance display name
        String displayName = meta != null && meta.hasDisplayName()
                ? meta.getDisplayName()
                : TextUtil.formatItemName(displayItem.getType().name());

        if (listing.isFeatured()) {
            displayName = ChatColor.GOLD + "⭐ " + displayName;
        }

        if (listing.isExpired()) {
            displayName = ChatColor.RED + "[EXPIRED] " + displayName;
        }

        List<String> lore = new ArrayList<>();

        // Original lore
        if (meta != null && meta.hasLore()) {
            lore.addAll(meta.getLore());
            lore.add("");
        }

        // Listing information
        lore.add(ChatColor.YELLOW + "▪ Your Listing:");
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.GREEN + TextUtil.formatNumber(listing.getPrice()) + " gems");
        lore.add(ChatColor.GRAY + "Category: " + listing.getCategory().getColoredDisplayName());
        lore.add(ChatColor.GRAY + "Amount: " + ChatColor.WHITE + listing.getAmount());
        lore.add(ChatColor.GRAY + "Views: " + ChatColor.WHITE + listing.getViews());
        lore.add(ChatColor.GRAY + "Listed: " + ChatColor.WHITE + getTimeAgo(listing.getListedTime()));

        if (listing.isExpired()) {
            lore.add(ChatColor.RED + "Status: " + ChatColor.DARK_RED + "EXPIRED");
            lore.add(ChatColor.GRAY + "This listing is no longer visible to buyers.");
        } else {
            lore.add(ChatColor.GRAY + "Expires: " + ChatColor.WHITE + listing.getFormattedTimeRemaining());
        }

        if (listing.isFeatured()) {
            lore.add(ChatColor.GOLD + "★ Featured Listing");
        }

        lore.add("");
        lore.add(ChatColor.RED + "✗ Click to remove listing!");
        lore.add(ChatColor.GRAY + "Item will be returned to your inventory.");

        if (meta == null) {
            meta = displayItem.getItemMeta();
        }
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        displayItem.setItemMeta(meta);

        return new MenuItem(displayItem).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            // Use MarketManager's chat system for confirmation
            marketManager.startRemovalConfirmation(player, listing.getItemId());
        });
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

    private MenuItem createInfoDisplay() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "📋 Your Listings");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Manage your active market listings.",
                ChatColor.GRAY + "Click items to remove them.",
                "",
                ChatColor.GRAY + "Loading listing information..."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private void updateInfoDisplay() {
        ItemStack infoItem = getInventory().getItem(4);
        if (infoItem != null) {
            ItemMeta meta = infoItem.getItemMeta();
            if (meta != null) {
                long totalValue = playerListings.stream().mapToLong(MarketItem::getPrice).sum();
                int featuredCount = (int) playerListings.stream().filter(MarketItem::isFeatured).count();
                int expiredCount = (int) playerListings.stream().filter(MarketItem::isExpired).count();
                int activeCount = playerListings.size() - expiredCount;

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.GRAY + "Manage your active market listings.");
                lore.add(ChatColor.GRAY + "Click items to remove them.");
                lore.add("");
                lore.add(ChatColor.AQUA + "Total Listings: " + ChatColor.WHITE + playerListings.size() + "/" + marketManager.getMaxListingsPerPlayer());
                lore.add(ChatColor.AQUA + "Active: " + ChatColor.GREEN + activeCount + ChatColor.GRAY + " | Expired: " + ChatColor.RED + expiredCount);
                lore.add(ChatColor.AQUA + "Total Value: " + ChatColor.WHITE + TextUtil.formatNumber(totalValue) + " gems");

                if (featuredCount > 0) {
                    lore.add(ChatColor.AQUA + "Featured: " + ChatColor.GOLD + "★ " + ChatColor.WHITE + featuredCount);
                }

                lore.add("");

                meta.setLore(lore);
                infoItem.setItemMeta(meta);
            }
        }
    }

    private MenuItem createRefreshButton() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "🔄 Refresh");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Refresh your listings to see",
                ChatColor.GRAY + "the latest information.",
                "",
                ChatColor.YELLOW + "Click to refresh!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            loadPlayerListings();
        });
    }

    private MenuItem createListNewItemButton() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "💎 List New Item");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "List a new item for sale",
                ChatColor.GRAY + "on the market.",
                "",
                ChatColor.YELLOW + "Click to list an item!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new MarketListingMenu(player, marketManager, session).open();
        });
    }

    private MenuItem createStatsButton() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "📊 Your Statistics");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "View your market selling",
                ChatColor.GRAY + "statistics and performance.",
                "",
                ChatColor.YELLOW + "Click to view stats!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            showPlayerStats(player);
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

    // Special display items
    private MenuItem createLoadingIndicator() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "⏳ Loading your listings...");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Please wait while we load",
                ChatColor.GRAY + "your market listings.",
                "",
                ChatColor.YELLOW + "This may take a moment."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createNoListingsDisplay() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "No active listings");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "You don't have any items",
                ChatColor.GRAY + "listed on the market.",
                "",
                ChatColor.GREEN + "Click 'List New Item' to",
                ChatColor.GREEN + "start selling your items!",
                "",
                ChatColor.GRAY + "You can list up to " + ChatColor.WHITE +
                        marketManager.getMaxListingsPerPlayer() + ChatColor.GRAY + " items per day."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createErrorDisplay() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "Error loading listings");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "There was an error loading",
                ChatColor.GRAY + "your market listings.",
                "",
                ChatColor.YELLOW + "Try refreshing or contact staff."
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    // Utility methods
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

    private String getTimeAgo(long timestamp) {
        long now = java.time.Instant.now().getEpochSecond();
        long diff = now - timestamp;

        if (diff < 60) return diff + "s ago";
        if (diff < 3600) return (diff / 60) + "m ago";
        if (diff < 86400) return (diff / 3600) + "h ago";
        return (diff / 86400) + "d ago";
    }

    private void showPlayerStats(Player player) {
        player.closeInventory();

        long totalValue = playerListings.stream().mapToLong(MarketItem::getPrice).sum();
        int featuredCount = (int) playerListings.stream().filter(MarketItem::isFeatured).count();
        int totalViews = playerListings.stream().mapToInt(MarketItem::getViews).sum();
        int expiredCount = (int) playerListings.stream().filter(MarketItem::isExpired).count();
        int activeCount = playerListings.size() - expiredCount;

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▪ " + ChatColor.YELLOW + "Your Market Statistics" + ChatColor.GOLD + " ▪");
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Total Listings: " + ChatColor.WHITE + playerListings.size() + "/" + marketManager.getMaxListingsPerPlayer());
        player.sendMessage(ChatColor.AQUA + "Active Listings: " + ChatColor.GREEN + activeCount);
        player.sendMessage(ChatColor.AQUA + "Expired Listings: " + ChatColor.RED + expiredCount);
        player.sendMessage(ChatColor.AQUA + "Total Value: " + ChatColor.WHITE + TextUtil.formatNumber(totalValue) + " gems");
        player.sendMessage(ChatColor.AQUA + "Featured Listings: " + ChatColor.WHITE + featuredCount);
        player.sendMessage(ChatColor.AQUA + "Total Views: " + ChatColor.WHITE + totalViews);

        if (activeCount > 0) {
            double avgViews = (double) totalViews / activeCount;
            player.sendMessage(ChatColor.AQUA + "Average Views per Item: " + ChatColor.WHITE + String.format("%.1f", avgViews));
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/market" + ChatColor.GRAY + " to return to the market.");
        player.sendMessage("");
    }
}