package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import com.rednetty.server.mechanics.world.teleport.TeleportBookSystem;
import com.rednetty.server.mechanics.world.teleport.TeleportDestination;
import com.rednetty.server.mechanics.world.teleport.TeleportManager;
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
 *  Book Vendor menu with perfect price formatting and beautiful teleport book display
 *  Uses  VendorUtils for consistent pricing and complete vendor lore removal
 */
public class BookVendorMenu implements Listener {

    private static final String INVENTORY_TITLE = "📚 Teleport Library";
    private static final int INVENTORY_SIZE = 54; // 6 rows for better organization

    //  pricing system for teleport books
    private static final int BASE_PRICE = 75; // Increased base price for better economy balance
    private static final double DISTANCE_MULTIPLIER = 0.08; // Slightly increased distance factor
    private static final double PREMIUM_MULTIPLIER = 2.0; // Premium destinations cost 2x
    private static final double DANGER_MULTIPLIER = 1.5; // Dangerous areas cost 1.5x

    private final Player player;
    private final Inventory inventory;
    private final TeleportManager teleportManager;
    private final TeleportBookSystem bookSystem;
    private final PurchaseManager purchaseManager;
    private final YakRealms plugin;

    /**
     *  constructor with better error handling
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

        // Initialize inventory with  design
        try {
            setupInventory();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up BookVendorMenu for player " + player.getName(), e);
            setupFallbackInventory();
        }
    }

    /**
     *  inventory setup with beautiful library theme
     */
    private void setupInventory() {
        //  category label with library theme
        inventory.setItem(4, createCategoryLabel("Mystical Teleport Tomes", Material.ENCHANTED_BOOK));

        // Get all available destinations
        Collection<TeleportDestination> destinations = teleportManager.getAllDestinations();

        if (destinations.isEmpty()) {
            createNoDestinationsNotice();
            return;
        }

        //  destination organization
        List<TeleportDestination> sortedDestinations = organizeDestinations(destinations);

        // Track slot index starting from row 2
        int slot = 9; // Start at second row

        // Add  books for each destination
        for (TeleportDestination destination : sortedDestinations) {
            // Skip if we run out of slots (leave space for bottom elements)
            if (slot >= inventory.getSize() - 9) break;

            try {
                // Create  teleport book
                ItemStack bookItem = createTeleportBook(destination);
                if (bookItem != null) {
                    inventory.setItem(slot++, bookItem);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error creating  book item for destination " + destination.getId(), e);
                // Continue with next destination
            }
        }

        //  decorative elements
        createLibraryDecorations();

        //  information and navigation
        inventory.setItem(inventory.getSize() - 5, createInfoButton());
        inventory.setItem(inventory.getSize() - 1, createCloseButton());
    }

    /**
     * Organize destinations by category and importance
     */
    private List<TeleportDestination> organizeDestinations(Collection<TeleportDestination> destinations) {
        List<TeleportDestination> sorted = new ArrayList<>(destinations);

        // Sort by: Premium first, then by distance from spawn, then alphabetically
        sorted.sort((dest1, dest2) -> {
            // Premium destinations first
            if (dest1.isPremium() && !dest2.isPremium()) return -1;
            if (!dest1.isPremium() && dest2.isPremium()) return 1;

            // Then by cost (lower cost first for easier access)
            int costDiff = dest1.getCost() - dest2.getCost();
            if (costDiff != 0) return costDiff;

            // Finally alphabetically
            return dest1.getId().compareToIgnoreCase(dest2.getId());
        });

        return sorted;
    }

    /**
     * Create  teleport book with perfect formatting
     */
    private ItemStack createTeleportBook(TeleportDestination destination) {
        try {
            // Create the base teleport book
            ItemStack bookItem = bookSystem.createTeleportBook(destination.getId(), true);
            if (bookItem == null) {
                return createFallbackBook(destination);
            }

            // Calculate  price
            int price = calculatePrice(destination);

            // Create  description
            String description = createBookDescription(destination);

            // Use VendorUtils.createVendorItem for perfect consistency
            return VendorUtils.createVendorItem(bookItem, price, description);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error creating  teleport book for " + destination.getId(), e);
            return createFallbackBook(destination);
        }
    }

    /**
     * Calculate  price with multiple factors
     */
    private int calculatePrice(TeleportDestination destination) {
        try {
            // Start with the  base price
            double price = BASE_PRICE;

            // Add distance-based component if in same world
            if (player.getLocation().getWorld().equals(destination.getLocation().getWorld())) {
                double distance = player.getLocation().distance(destination.getLocation());
                double distanceFactor = 1.0 + (distance * DISTANCE_MULTIPLIER / 100);
                price *= distanceFactor;
            } else {
                // Cross-world teleportation is more expensive
                price *= 1.8;
            }

            // Premium destination multiplier
            if (destination.isPremium()) {
                price *= PREMIUM_MULTIPLIER;
            }

            // Check for dangerous areas (based on world name or destination properties)
            if (isDangerousDestination(destination)) {
                price *= DANGER_MULTIPLIER;
            }

            // Consider the destination's own base cost
            price = Math.max(price, destination.getCost() * 1.2);

            // Add rarity bonus for unique destinations
            if (isRareDestination(destination)) {
                price *= 1.3;
            }

            return Math.max(50, (int) Math.round(price)); // Minimum price of 50

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error calculating  price for destination " + destination.getId(), e);
            return BASE_PRICE;
        }
    }

    /**
     * Check if destination is dangerous based on various factors
     */
    private boolean isDangerousDestination(TeleportDestination destination) {
        String destId = destination.getId().toLowerCase();
        String worldName = destination.getLocation().getWorld().getName().toLowerCase();

        return destId.contains("nether") || destId.contains("end") || destId.contains("dungeon") ||
                destId.contains("boss") || destId.contains("pvp") || destId.contains("danger") ||
                worldName.contains("nether") || worldName.contains("end");
    }

    /**
     * Check if destination is rare/unique
     */
    private boolean isRareDestination(TeleportDestination destination) {
        String destId = destination.getId().toLowerCase();

        return destId.contains("secret") || destId.contains("hidden") || destId.contains("rare") ||
                destId.contains("legendary") || destId.contains("exclusive") || destination.getCost() > 1000;
    }

    /**
     * Create  book description with detailed information
     */
    private String createBookDescription(TeleportDestination destination) {
        StringBuilder description = new StringBuilder();

        // Basic description
        description.append("Instantly teleport to ").append(destination.getId());

        // Add world information
        String worldName = destination.getLocation().getWorld().getName();
        if (!worldName.equalsIgnoreCase("world")) {
            description.append("\nLocation: ").append(VendorUtils.capitalizeFirst(worldName));
        }

        // Add coordinates for reference
        int x = (int) destination.getLocation().getX();
        int z = (int) destination.getLocation().getZ();
        description.append("\nCoordinates: ").append(x).append(", ").append(z);

        // Add special properties
        if (destination.isPremium()) {
            description.append("\n✨ Premium destination with exclusive access");
        }

        if (isDangerousDestination(destination)) {
            description.append("\n⚠ Warning: Dangerous area! Come prepared");
        }

        if (isRareDestination(destination)) {
            description.append("\n🌟 Rare destination - Limited availability");
        }

        // Add usage instructions
        description.append("\n📖 Right-click to use after purchase");

        return description.toString();
    }

    /**
     * Create fallback book if TeleportBookSystem fails
     */
    private ItemStack createFallbackBook(TeleportDestination destination) {
        ItemStack fallbackBook = new ItemStack(Material.BOOK);
        ItemMeta meta = fallbackBook.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "📖 " + VendorUtils.capitalizeFirst(destination.getId()) + " Teleport");
            fallbackBook.setItemMeta(meta);
        }

        // Add price and description using VendorUtils
        int price = calculatePrice(destination);
        String description = createBookDescription(destination) + "\n⚠ Fallback book - contact admin if you see this";

        return VendorUtils.createVendorItem(fallbackBook, price, description);
    }

    /**
     * Create notice for when no destinations are available
     */
    private void createNoDestinationsNotice() {
        ItemStack notice = new ItemStack(Material.BARRIER);
        ItemMeta meta = notice.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "📚 No Destinations Available");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "The librarian's shelves are empty",
                    ChatColor.GRAY + "No teleport destinations are configured",
                    "",
                    ChatColor.YELLOW + "Please contact an administrator"
            ));
            notice.setItemMeta(meta);
        }
        inventory.setItem(22, notice);
    }

    /**
     * Create  library decorations with magical theme
     */
    private void createLibraryDecorations() {
        // Top border - magical library theme
        for (int i = 0; i < 9; i++) {
            if (i != 4) { // Skip center for header
                Material borderMaterial = (i % 2 == 0) ? Material.PURPLE_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
                inventory.setItem(i, VendorUtils.createSeparator(borderMaterial, " "));
            }
        }

        // Bottom border - magical library theme
        for (int i = inventory.getSize() - 9; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                Material borderMaterial = (i % 2 == 0) ? Material.PURPLE_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
                inventory.setItem(i, VendorUtils.createSeparator(borderMaterial, " "));
            }
        }

        // Fill remaining empty slots with bookshelves for theme
        for (int i = 9; i < inventory.getSize() - 9; i++) {
            if (inventory.getItem(i) == null) {
                // Create magical bookshelf decorations
                ItemStack bookshelf = new ItemStack(Material.BOOKSHELF);
                ItemMeta meta = bookshelf.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.DARK_PURPLE + "📚 Mystical Library Shelf");
                    meta.setLore(List.of(ChatColor.GRAY + "Ancient tomes of teleportation magic"));
                    bookshelf.setItemMeta(meta);
                }
                inventory.setItem(i, bookshelf);
            }
        }
    }

    /**
     * Opens the  menu for the player
     */
    public void open() {
        player.openInventory(inventory);
        // Play magical library sounds
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
    }

    /**
     *  click handling with better validation and feedback
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
                handleCloseButton(player, clickedItem);
                return;
            }

            // Ignore decorative items
            if (isDecorativeItem(clickedItem)) {
                return;
            }

            // Handle book purchase (BOOK items)
            if (clickedItem.getType() == Material.BOOK || clickedItem.getType() == Material.ENCHANTED_BOOK) {
                handleBookPurchase(player, clickedItem);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling click in BookVendorMenu for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "❌ An error occurred. Please try again.");
        }
    }

    /**
     * Handle close button click
     */
    private void handleCloseButton(Player player, ItemStack clickedItem) {
        // Check if it's the actual close button or error notice
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
            String displayName = clickedItem.getItemMeta().getDisplayName();
            if (ChatColor.stripColor(displayName).toLowerCase().contains("close") ||
                    ChatColor.stripColor(displayName).toLowerCase().contains("leave")) {

                player.closeInventory();
                player.sendMessage(ChatColor.LIGHT_PURPLE + "📚 Thanks for visiting the Teleport Library!");
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.8f);
            }
        }
    }

    /**
     * Check if item is decorative
     */
    private boolean isDecorativeItem(ItemStack item) {
        return item.getType() == Material.PURPLE_STAINED_GLASS_PANE ||
                item.getType() == Material.MAGENTA_STAINED_GLASS_PANE ||
                item.getType() == Material.BOOKSHELF ||
                (item.getType() == Material.KNOWLEDGE_BOOK) || // Info button
                (item.getType() == Material.BARRIER && !isCloseButton(item)); // Error notices
    }

    /**
     * Check if barrier item is the close button
     */
    private boolean isCloseButton(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
        return displayName.contains("close") || displayName.contains("leave");
    }

    /**
     *  book purchase handling
     */
    private void handleBookPurchase(Player player, ItemStack clickedItem) {
        // Extract destination ID from the book
        String destId = bookSystem.getDestinationFromBook(clickedItem);
        if (destId == null) {
            player.sendMessage(ChatColor.RED + "❌ Invalid teleport book.");
            return;
        }

        // Use  VendorUtils for price extraction
        int price = VendorUtils.extractPriceFromLore(clickedItem);
        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "❌ This book is not available for purchase.");
            return;
        }

        // Validate purchase state
        if (purchaseManager.isInPurchaseProcess(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "⚠ You already have an active purchase. Complete it first or type " +
                    ChatColor.BOLD + "'cancel'" + ChatColor.RED + ".");
            return;
        }

        // Get clean book display name
        String itemName = getCleanBookName(clickedItem);

        player.closeInventory();

        //  purchase initiation message
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "📖 Starting purchase for " + ChatColor.WHITE + itemName + ChatColor.LIGHT_PURPLE + "...");
        player.sendMessage(ChatColor.GRAY + "✨ From the mystical library archives!");
        player.sendMessage("");

        // Create completely clean book for purchase using  VendorUtils
        ItemStack cleanBookForPurchase = VendorUtils.createCleanItemCopy(clickedItem);

        purchaseManager.startPurchase(player, cleanBookForPurchase, price);

        // Play purchase initiation sounds
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    /**
     * Get clean book name for display
     */
    private String getCleanBookName(ItemStack book) {
        if (book.hasItemMeta() && book.getItemMeta().hasDisplayName()) {
            // Remove any vendor formatting symbols and colors
            String displayName = book.getItemMeta().getDisplayName();
            return ChatColor.stripColor(displayName)
                    .replaceAll("[▶▷►📖📚✨🌟⚠]", "")
                    .trim();
        }
        return "Teleport Book";
    }

    /**
     * Setup fallback inventory for errors
     */
    private void setupFallbackInventory() {
        // Create basic error notice
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "⚠ Error Loading Books");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Failed to load teleport books",
                    ChatColor.GRAY + "The library shelves are magically sealed",
                    "",
                    ChatColor.YELLOW + "Please contact an administrator"
            ));
            errorItem.setItemMeta(meta);
        }

        inventory.setItem(22, errorItem);
        inventory.setItem(inventory.getSize() - 1, createCloseButton());

        // Fill with magical library theme
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, VendorUtils.createSeparator(Material.PURPLE_STAINED_GLASS_PANE, " "));
            }
        }
    }

    /**
     * Create  category label with library theme
     */
    private ItemStack createCategoryLabel(String name, Material icon) {
        ItemStack label = new ItemStack(icon);
        ItemMeta meta = label.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✨ " + name);
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Ancient books of teleportation magic",
                    ChatColor.GRAY + "Instantly travel to distant realms",
                    "",
                    ChatColor.YELLOW + "📖 Click on a book below to purchase!"
            ));
            label.setItemMeta(meta);
        }
        return label;
    }

    /**
     * Create  information button
     */
    private ItemStack createInfoButton() {
        ItemStack infoButton = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = infoButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "📚 Teleportation Guide");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GOLD + "How Teleport Books Work:",
                    ChatColor.GRAY + "• Purchase a book for your destination",
                    ChatColor.GRAY + "• Right-click the book to teleport",
                    ChatColor.GRAY + "• Books are consumed after use",
                    ChatColor.GRAY + "• Each book works only once",
                    "",
                    ChatColor.YELLOW + "💰 Pricing Information:",
                    ChatColor.GRAY + "• Base price: " + VendorUtils.formatCurrency(BASE_PRICE),
                    ChatColor.GRAY + "• Distance affects price",
                    ChatColor.GRAY + "• Premium destinations cost more",
                    ChatColor.GRAY + "• Dangerous areas have surcharges",
                    "",
                    ChatColor.GREEN + "💡 Pro Tips:",
                    ChatColor.GRAY + "• Buy books before you need them",
                    ChatColor.GRAY + "• Stock up for dangerous expeditions",
                    ChatColor.GRAY + "• Some destinations are rare finds!",
                    "",
                    ChatColor.LIGHT_PURPLE + "✨ Welcome to the Mystical Library!"
            ));
            infoButton.setItemMeta(meta);
        }
        return infoButton;
    }

    /**
     * Create  close button
     */
    private ItemStack createCloseButton() {
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta meta = closeButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "❌ Leave Library");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Close the teleport library",
                    "",
                    ChatColor.LIGHT_PURPLE + "📚 May your travels be swift and safe!"
            ));
            closeButton.setItemMeta(meta);
        }
        return closeButton;
    }
}