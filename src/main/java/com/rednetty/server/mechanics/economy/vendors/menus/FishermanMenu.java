package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.mechanics.player.items.SpeedfishMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Menu for the Fisherman vendor
 */
public class FishermanMenu implements Listener {

    private static final String INVENTORY_TITLE = "Fisherman";
    private static final int INVENTORY_SIZE = 27;

    private final Player player;
    private final Inventory inventory;
    private final PurchaseManager purchaseManager;

    /**
     * Creates a fisherman menu for a player
     *
     * @param player The player to open the menu for
     */
    public FishermanMenu(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        this.purchaseManager = PurchaseManager.getInstance();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Initialize inventory
        setupInventory();
    }

    /**
     * Setup the inventory with items
     */
    private void setupInventory() {
        // Category Label
        inventory.setItem(4, createCategoryLabel("Magical Fish", Material.FISHING_ROD));

        // Magical Fish Items (tiers 2-5)
        inventory.setItem(10, SpeedfishMechanics.createSpeedfish(2, true));
        inventory.setItem(12, SpeedfishMechanics.createSpeedfish(3, true));
        inventory.setItem(14, SpeedfishMechanics.createSpeedfish(4, true));
        inventory.setItem(16, SpeedfishMechanics.createSpeedfish(5, true));

        // Add colored separators for decoration
        fillRow(0, (byte) 3);  // Light Blue
        fillRow(2, (byte) 3);  // Light Blue

        // Fill remaining slots with gray glass
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator((byte) 7)); // Gray
            }
        }

        // Close button
        inventory.setItem(26, createCloseButton());
    }

    /**
     * Opens the menu for the player
     */
    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    /**
     * Handle inventory click events
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Handle close button
        if (clickedItem.getType() == Material.AIR) {
            player.closeInventory();
            return;
        }

        // Ignore decorative items
        if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        // Handle purchasing fish
        if (clickedItem.getType() == Material.COD) {
            int price = PurchaseManager.getPriceFromLore(clickedItem);
            if (price > 0) {
                purchaseManager.startPurchase(player, clickedItem, price);
                player.closeInventory();
            }
        }
    }

    /**
     * Create a category label item
     *
     * @param name The name of the category
     * @param icon The icon material
     * @return The created item
     */
    private ItemStack createCategoryLabel(String name, Material icon) {
        ItemStack label = new ItemStack(icon);
        ItemMeta meta = label.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + name);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click on a fish",
                ChatColor.GRAY + "below to purchase it!"
        ));
        label.setItemMeta(meta);
        return label;
    }

    /**
     * Fill a row with a specific colored glass
     *
     * @param row   The row number (0-2)
     * @param color The glass color data value
     */
    private void fillRow(int row, byte color) {
        for (int i = row * 9; i < (row + 1) * 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator(color));
            }
        }
    }

    /**
     * Create a separator glass pane
     *
     * @param color The color data value
     * @return The created item
     */
    private ItemStack createSeparator(byte color) {
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1, color);
        ItemMeta meta = separator.getItemMeta();
        meta.setDisplayName(" ");
        separator.setItemMeta(meta);
        return separator;
    }

    /**
     * Create a close button
     *
     * @return The created item
     */
    private ItemStack createCloseButton() {
        ItemStack closeButton = new ItemStack(Material.AIR);
        ItemMeta meta = closeButton.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
        closeButton.setItemMeta(meta);
        return closeButton;
    }
}