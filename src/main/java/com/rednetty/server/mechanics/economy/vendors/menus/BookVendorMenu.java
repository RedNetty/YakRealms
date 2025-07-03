package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import com.rednetty.server.mechanics.teleport.TeleportBookSystem;
import com.rednetty.server.mechanics.teleport.TeleportDestination;
import com.rednetty.server.mechanics.teleport.TeleportManager;
import com.rednetty.server.utils.menu.MenuItem;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Menu for Book Vendors selling teleport books with consistent price handling
 */
public class BookVendorMenu implements Listener {

    private static final String INVENTORY_TITLE = "Teleport Books";
    private static final int INVENTORY_SIZE = 54; // 6 rows
    private static final int BASE_PRICE = 50; // Base price for teleport books
    private static final double DISTANCE_MULTIPLIER = 0.05; // Price increases by 5% per 100 blocks

    private final Player player;
    private final Inventory inventory;
    private final TeleportManager teleportManager;
    private final TeleportBookSystem bookSystem;
    private final PurchaseManager purchaseManager;
    private final YakRealms plugin;

    /**
     * Creates a book vendor menu for a player
     *
     * @param player The player to open the menu for
     */
    public BookVendorMenu(Player player) {
        this.player = player;
        this.plugin = YakRealms.getInstance();
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        this.teleportManager = TeleportManager.getInstance();
        this.bookSystem = TeleportBookSystem.getInstance();
        this.purchaseManager = PurchaseManager.getInstance();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Initialize inventory
        try {
            setupInventory();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up BookVendorMenu for player " + player.getName(), e);
            setupFallbackInventory();
        }
    }

    /**
     * Setup the inventory with teleport books
     */
    private void setupInventory() {
        // Category Label
        inventory.setItem(4, createCategoryLabel("Teleport Books", Material.BOOK));

        // Get all available destinations
        Collection<TeleportDestination> destinations = teleportManager.getAllDestinations();

        // Track slot index
        int slot = 9; // Start at second row

        // Add items for each destination
        for (TeleportDestination destination : destinations) {
            // Skip if we run out of slots
            if (slot >= inventory.getSize() - 9) break;

            try {
                // Calculate price based on distance
                int price = calculatePrice(destination);

                // Create a teleport book for this destination (with shop flag)
                ItemStack bookItem = bookSystem.createTeleportBook(destination.getId(), true);
                if (bookItem != null) {
                    // Use VendorUtils for consistent price handling
                    bookItem = VendorUtils.addPriceToItem(bookItem, price);

                    // Add the book to the inventory
                    inventory.setItem(slot++, bookItem);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error creating book item for destination " + destination.getId(), e);
                // Continue with next destination
            }
        }

        // Fill empty slots with decorative items
        fillEmptySlots();

        // Add information and close buttons
        inventory.setItem(inventory.getSize() - 5, createInfoButton());
        inventory.setItem(inventory.getSize() - 1, createCloseButton());
    }

    /**
     * Setup fallback inventory in case of errors
     */
    private void setupFallbackInventory() {
        // Create basic error notice
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Error Loading Books");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Failed to load teleport books",
                    ChatColor.GRAY + "Please contact an administrator"
            ));
            errorItem.setItemMeta(meta);
        }

        inventory.setItem(22, errorItem);
        inventory.setItem(inventory.getSize() - 1, createCloseButton());

        // Fill with gray glass
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator((byte) 7));
            }
        }
    }

    /**
     * Calculate the price of a teleport book based on distance
     *
     * @param destination The teleport destination
     * @return The calculated price
     */
    private int calculatePrice(TeleportDestination destination) {
        try {
            // Start with the base price
            int price = BASE_PRICE;

            // Add distance-based component
            // If vendor's location is unknown, use the base destination cost
            if (player.getLocation().getWorld().equals(destination.getLocation().getWorld())) {
                double distance = player.getLocation().distance(destination.getLocation());
                double distanceFactor = 1.0 + (distance * DISTANCE_MULTIPLIER / 100);
                price = (int) (price * distanceFactor);
            }

            // Add premium tax if it's a premium destination
            if (destination.isPremium()) {
                price = (int) (price * 1.5); // 50% premium tax
            }

            // Consider the destination's own cost as minimum
            price = Math.max(price, destination.getCost());

            return Math.max(1, price); // Ensure price is at least 1

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error calculating price for destination " + destination.getId(), e);
            return BASE_PRICE; // Return base price as fallback
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

            // Handle book purchase
            if (clickedItem.getType() == Material.BOOK) {
                String destId = bookSystem.getDestinationFromBook(clickedItem);
                if (destId != null) {
                    // Use VendorUtils for consistent price extraction
                    int price = VendorUtils.extractPriceFromLore(clickedItem);
                    if (price > 0) {
                        // Validate purchase state
                        if (purchaseManager.isInPurchaseProcess(player.getUniqueId())) {
                            player.sendMessage(ChatColor.RED + "You already have an active purchase. Complete it first or type 'cancel'.");
                            return;
                        }

                        // Get item display name for feedback
                        String itemName = clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName() ?
                                clickedItem.getItemMeta().getDisplayName() :
                                "Teleport Book";

                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + "Initiating purchase for " + itemName + "...");

                        purchaseManager.startPurchase(player, clickedItem, price);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    } else {
                        player.sendMessage(ChatColor.RED + "This item is not available for purchase.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid teleport book.");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling click in BookVendorMenu for player " + player.getName(), e);
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
                    ChatColor.GRAY + "Click on a book",
                    ChatColor.GRAY + "below to purchase it!"
            ));
            label.setItemMeta(meta);
        }
        return label;
    }

    /**
     * Fill empty slots with decorative glass panes
     */
    private void fillEmptySlots() {
        // Top row (blue glass)
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator((byte) 3)); // Light Blue
            }
        }

        // Bottom row (blue glass)
        for (int i = inventory.getSize() - 9; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator((byte) 3)); // Light Blue
            }
        }

        // All other empty slots (gray glass)
        for (int i = 9; i < inventory.getSize() - 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator((byte) 7)); // Gray
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
     * Create an information button
     *
     * @return The created item
     */
    private ItemStack createInfoButton() {
        ItemStack infoButton = new ItemStack(Material.PAPER);
        ItemMeta meta = infoButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Teleport Information");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Prices are based on distance",
                    ChatColor.GRAY + "and destination rarity.",
                    ChatColor.GRAY + "",
                    ChatColor.GRAY + "Use a teleport book by",
                    ChatColor.GRAY + "right-clicking it in your hand."
            ));
            infoButton.setItemMeta(meta);
        }
        return infoButton;
    }

    /**
     * Creates a close button for the menu
     *
     * @return The created close button
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