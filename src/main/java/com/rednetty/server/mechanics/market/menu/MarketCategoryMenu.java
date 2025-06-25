package com.rednetty.server.mechanics.market.menu;

import com.rednetty.server.mechanics.market.MarketCategory;
import com.rednetty.server.mechanics.market.MarketManager;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Market category selection menu
 */
public class MarketCategoryMenu extends Menu {
    private final MarketManager marketManager;
    private final MarketManager.MarketSession session;

    public MarketCategoryMenu(Player player, MarketManager marketManager, MarketManager.MarketSession session) {
        super(player, "&8⚜ &6&lMarket Categories &8⚜", 45);
        this.marketManager = marketManager;
        this.session = session;

        setClickSoundsEnabled(true);
    }

    @Override
    protected void onPreOpen() {
        setupMenu();
        loadCategoryStats();
    }

    private void setupMenu() {
        // Create border
        createBorder(Material.BLACK_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        // Back button
        setItem(4, createBackButton());

        // Category buttons
        MarketCategory[] categories = MarketCategory.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        for (int i = 0; i < Math.min(categories.length, slots.length); i++) {
            setItem(slots[i], createCategoryButton(categories[i]));
        }

        // Close button
        setItem(40, createCloseButton());
    }

    private void loadCategoryStats() {
        CompletableFuture.runAsync(() -> {
            marketManager.getRepository().getCategoryStats().thenAccept(stats -> {
                getPlugin().getServer().getScheduler().runTask(getPlugin(), () -> {
                    if (isOpen()) {
                        updateCategoryStats(stats);
                    }
                });
            });
        });
    }

    private void updateCategoryStats(java.util.Map<MarketCategory, Integer> stats) {
        MarketCategory[] categories = MarketCategory.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        for (int i = 0; i < Math.min(categories.length, slots.length); i++) {
            MarketCategory category = categories[i];
            int count = stats.getOrDefault(category, 0);
            setItem(slots[i], createCategoryButton(category, count));
        }
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

    private MenuItem createCategoryButton(MarketCategory category) {
        return createCategoryButton(category, -1);
    }

    private MenuItem createCategoryButton(MarketCategory category, int itemCount) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(category.getColoredDisplayName());

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Browse items in the");
        lore.add(ChatColor.GRAY + category.getDisplayName().toLowerCase() + " category.");
        lore.add("");

        if (itemCount >= 0) {
            lore.add(ChatColor.AQUA + "Available items: " + ChatColor.WHITE + itemCount);
            lore.add("");
        }

        lore.add(ChatColor.YELLOW + "Click to browse!");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return new MenuItem(item).setClickHandler((player, slot) -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            session.setSelectedCategory(category);
            session.setCurrentPage(0);
            new MarketBrowseMenu(player, marketManager, session, category).open();
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

    private org.bukkit.plugin.Plugin getPlugin() {
        return com.rednetty.server.YakRealms.getInstance();
    }
}