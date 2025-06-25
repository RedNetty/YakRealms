package com.rednetty.server.mechanics.market.menu;

import com.rednetty.server.mechanics.market.MarketManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Menu for listing items for sale
 */
public class MarketListingMenu extends Menu {
    private final MarketManager marketManager;
    private final MarketManager.MarketSession session;

    public MarketListingMenu(Player player, MarketManager marketManager, MarketManager.MarketSession session) {
        super(player, "&8⚜ &6&lList Item for Sale &8⚜", 54);
        this.marketManager = marketManager;
        this.session = session;

        setClickSoundsEnabled(true);
    }

    @Override
    protected void onPreOpen() {
        setupMenu();
        loadPlayerInventory();
    }

    private void setupMenu() {
        // Create border
        createBorder(Material.BLACK_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        // Control buttons
        setItem(4, createInfoButton());
        setItem(0, createBackButton());
        setItem(8, createHelpButton());

        // Bottom controls
        setItem(45, createRefreshButton());
        setItem(49, createListingInfoButton());
        setItem(53, createCloseButton());
    }

    private void loadPlayerInventory() {
        // Display player's inventory items (excluding armor and hotbar)
        ItemStack[] contents = getPlayer().getInventory().getStorageContents();
        Set<Material> bannedMaterials = marketManager.getBannedMaterials();

        int slot = 9; // Start from second row

        for (int i = 9; i < contents.length && slot < 45; i++) { // Skip hotbar
            ItemStack item = contents[i];

            if (item != null && item.getType() != Material.AIR) {
                // Skip banned materials
                if (bannedMaterials.contains(item.getType())) {
                    continue;
                }

                // Skip border slots
                if (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                    continue;
                }

                setItem(slot, createInventoryItemButton(item, i));
                slot++;
            }
        }
    }

    private MenuItem createInfoButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(getPlayer());

        meta.setDisplayName(ChatColor.GOLD + "📋 Listing Information");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Select an item from your inventory",
                ChatColor.GRAY + "to list it for sale on the market.",
                "",
                ChatColor.AQUA + "Your Gems: " + ChatColor.WHITE + (yakPlayer != null ? TextUtil.formatNumber(yakPlayer.getGems()) : "0"),
                ChatColor.AQUA + "Daily Listings: " + ChatColor.WHITE + "TODO/" + marketManager.getMaxListingsPerPlayer(),
                ChatColor.AQUA + "Market Tax: " + ChatColor.WHITE + String.format("%.1f%%", marketManager.getMarketTaxRate() * 100),
                "",
                ChatColor.YELLOW + "Click an item to list it!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createInventoryItemButton(ItemStack originalItem, int inventorySlot) {
        ItemStack displayItem = originalItem.clone();
        ItemMeta meta = displayItem.getItemMeta();

        List<String> lore = new ArrayList<>();

        // Original lore
        if (meta != null && meta.hasLore()) {
            lore.addAll(meta.getLore());
            lore.add("");
        }

        // Market info
        lore.add(ChatColor.YELLOW + "▪ Market Listing:");
        lore.add(ChatColor.GRAY + "Amount: " + ChatColor.WHITE + originalItem.getAmount());
        lore.add(ChatColor.GRAY + "Price range: " + ChatColor.GREEN +
                TextUtil.formatNumber(marketManager.getMinItemPrice()) + " - " +
                TextUtil.formatNumber(marketManager.getMaxItemPrice()) + " gems");
        lore.add("");
        lore.add(ChatColor.GREEN + "✓ Click to list this item!");

        if (meta == null) {
            meta = displayItem.getItemMeta();
        }
        meta.setLore(lore);
        displayItem.setItemMeta(meta);

        return new MenuItem(displayItem).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            startListingProcess(player, originalItem, inventorySlot);
        });
    }

    private void startListingProcess(Player player, ItemStack item, int inventorySlot) {
        session.setItemToList(item);
        session.setListingMode(true);

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "⚡ " + ChatColor.YELLOW + "List Item for Sale");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Item: " + ChatColor.WHITE +
                (item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                        item.getItemMeta().getDisplayName() :
                        TextUtil.formatItemName(item.getType().name())));
        player.sendMessage(ChatColor.GRAY + "Amount: " + ChatColor.WHITE + item.getAmount());
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Enter the price in gems:");
        player.sendMessage(ChatColor.GRAY + "Min: " + TextUtil.formatNumber(marketManager.getMinItemPrice()) +
                " | Max: " + TextUtil.formatNumber(marketManager.getMaxItemPrice()));
        player.sendMessage(ChatColor.GRAY + "Type 'cancel' to go back.");
        player.sendMessage("");

        // Note: Price input would be handled by chat listener in MarketManager
        // For now, we'll use a default price for demonstration
        processListing(player, item, 100, false);
    }

    private void processListing(Player player, ItemStack item, int price, boolean featured) {
        marketManager.listItem(player, item, price, featured).thenAccept(result -> {
            getPlugin().getServer().getScheduler().runTask(getPlugin(), () -> {
                switch (result) {
                    case SUCCESS:
                        player.sendMessage(ChatColor.GREEN + "✓ Item listed successfully!");
                        break;
                    case INVALID_ITEM:
                        player.sendMessage(ChatColor.RED + "✗ Invalid item or price!");
                        break;
                    case LISTING_LIMIT_REACHED:
                        player.sendMessage(ChatColor.RED + "✗ Daily listing limit reached!");
                        break;
                    case INSUFFICIENT_FUNDS:
                        player.sendMessage(ChatColor.RED + "✗ Not enough gems for featured listing!");
                        break;
                    default:
                        player.sendMessage(ChatColor.RED + "✗ Listing failed: " + result.name());
                        break;
                }

                // Return to market menu
                new MarketMainMenu(player, marketManager, session).open();
            });
        });
    }

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

    private MenuItem createHelpButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "❓ Listing Help");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Tips for listing items:",
                "",
                ChatColor.YELLOW + "• " + ChatColor.GRAY + "Set competitive prices",
                ChatColor.YELLOW + "• " + ChatColor.GRAY + "Check market demand",
                ChatColor.YELLOW + "• " + ChatColor.GRAY + "Featured listings get more views",
                ChatColor.YELLOW + "• " + ChatColor.GRAY + "Items expire after 7 days",
                "",
                ChatColor.GREEN + "Good luck selling!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
    }

    private MenuItem createRefreshButton() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "🔄 Refresh Inventory");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Refresh the display to show",
                ChatColor.GRAY + "your current inventory items.",
                "",
                ChatColor.YELLOW + "Click to refresh!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            // Clear current display
            for (int i = 9; i < 45; i++) {
                if (i % 9 != 0 && i % 9 != 8) {
                    removeItem(i);
                }
            }
            loadPlayerInventory();
        });
    }

    private MenuItem createListingInfoButton() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "💎 Featured Listing");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Featured listings appear at the",
                ChatColor.GRAY + "top of browse results and get",
                ChatColor.GRAY + "more visibility from buyers.",
                "",
                ChatColor.AQUA + "Cost: " + ChatColor.WHITE + TextUtil.formatNumber(marketManager.getFeaturedListingCost()) + " gems",
                "",
                ChatColor.YELLOW + "Available when listing items!"
        ));

        item.setItemMeta(meta);
        return new MenuItem(item);
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

    private org.bukkit.plugin.Plugin getPlugin() {
        return com.rednetty.server.YakRealms.getInstance();
    }
}