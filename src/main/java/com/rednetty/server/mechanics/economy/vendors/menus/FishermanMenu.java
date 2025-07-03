package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
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
import java.util.logging.Level;

/**
 * Enhanced menu for the Fisherman vendor with consistent error handling and pricing
 */
public class FishermanMenu implements Listener {

    private static final String INVENTORY_TITLE = "Fisherman";
    private static final int INVENTORY_SIZE = 27;

    private final Player player;
    private final Inventory inventory;
    private final PurchaseManager purchaseManager;
    private final YakRealms plugin;

    /**
     * Creates a fisherman menu for a player
     *
     * @param player The player to open the menu for
     */
    public FishermanMenu(Player player) {
        this.player = player;
        this.plugin = YakRealms.getInstance();
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        this.purchaseManager = PurchaseManager.getInstance();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Initialize inventory
        try {
            setupInventory();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up FishermanMenu for player " + player.getName(), e);
            setupFallbackInventory();
        }
    }

    /**
     * Setup the inventory with items
     */
    private void setupInventory() {
        // Category Label
        inventory.setItem(4, createCategoryLabel("Magical Fish", Material.FISHING_ROD));

        try {
            // Magical Fish Items (tiers 2-5) - these should already have prices from SpeedfishMechanics
            ItemStack fish2 = SpeedfishMechanics.createSpeedfish(2, true);
            ItemStack fish3 = SpeedfishMechanics.createSpeedfish(3, true);
            ItemStack fish4 = SpeedfishMechanics.createSpeedfish(4, true);
            ItemStack fish5 = SpeedfishMechanics.createSpeedfish(5, true);

            // Validate that items have prices (SpeedfishMechanics should handle this)
            if (fish2 != null) inventory.setItem(10, fish2);
            if (fish3 != null) inventory.setItem(12, fish3);
            if (fish4 != null) inventory.setItem(14, fish4);
            if (fish5 != null) inventory.setItem(16, fish5);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error creating speedfish items", e);
            // Create fallback items
            createFallbackItems();
        }

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
     * Setup fallback inventory in case of errors
     */
    private void setupFallbackInventory() {
        // Create basic error notice
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Error Loading Fish");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Failed to load magical fish",
                    ChatColor.GRAY + "Please contact an administrator"
            ));
            errorItem.setItemMeta(meta);
        }

        inventory.setItem(13, errorItem);
        inventory.setItem(26, createCloseButton());

        // Fill with gray glass
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator((byte) 7));
            }
        }
    }

    /**
     * Create fallback items if SpeedfishMechanics fails
     */
    private void createFallbackItems() {
        // Create basic cod items with prices as fallback
        for (int tier = 2; tier <= 5; tier++) {
            try {
                ItemStack fallbackFish = new ItemStack(Material.COD);
                ItemMeta meta = fallbackFish.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.AQUA + "Tier " + tier + " Speedfish");
                    meta.setLore(Arrays.asList(
                            ChatColor.GRAY + "A magical fish that grants speed",
                            ChatColor.GRAY + "Tier: " + tier,
                            ChatColor.RED + "Error: Using fallback item"
                    ));
                    fallbackFish.setItemMeta(meta);
                }

                // Add price using VendorUtils
                int price = 100 * tier * tier; // Simple tier-based pricing
                fallbackFish = VendorUtils.addPriceToItem(fallbackFish, price);

                int slot = 8 + (tier * 2); // 10, 12, 14, 16
                if (slot < inventory.getSize()) {
                    inventory.setItem(slot, fallbackFish);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error creating fallback fish tier " + tier, e);
            }
        }
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

        try {
            // Handle close button
            if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
                return;
            }

            // Ignore decorative items
            if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            // Handle purchasing fish
            if (clickedItem.getType() == Material.COD) {
                // Use PurchaseManager for consistent price extraction
                int price = PurchaseManager.getPriceFromLore(clickedItem);
                if (price > 0) {
                    // Validate purchase state
                    if (purchaseManager.isInPurchaseProcess(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "You already have an active purchase. Complete it first or type 'cancel'.");
                        return;
                    }

                    // Get item display name for feedback
                    String itemName = clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName() ?
                            clickedItem.getItemMeta().getDisplayName() :
                            "Magical Fish";

                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Initiating purchase for " + itemName + "...");

                    purchaseManager.startPurchase(player, clickedItem, price);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                } else {
                    player.sendMessage(ChatColor.RED + "This item is not available for purchase.");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling click in FishermanMenu for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred. Please try again.");
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
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + name);
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click on a fish",
                    ChatColor.GRAY + "below to purchase it!"
            ));
            label.setItemMeta(meta);
        }
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
        if (meta != null) {
            meta.setDisplayName(" ");
            separator.setItemMeta(meta);
        }
        return separator;
    }

    /**
     * Create a close button
     *
     * @return The created item
     */
    private ItemStack createCloseButton() {
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta meta = closeButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Close this vendor menu"));
            closeButton.setItemMeta(meta);
        }
        return closeButton;
    }
}