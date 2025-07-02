package com.rednetty.server.mechanics.teleport;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * System for managing teleport books and scrolls
 */
public class TeleportBookSystem implements Listener {
    private static TeleportBookSystem instance;
    private final Logger logger;
    private final TeleportManager teleportManager;
    private final NamespacedKey destinationKey;
    private final NamespacedKey bookIdKey;

    // Map to track books by ID for persistence
    private final Map<String, ItemStack> bookTemplates = new HashMap<>();
    private File configFile;
    private FileConfiguration config;

    /**
     * Private constructor for singleton pattern
     */
    private TeleportBookSystem() {
        this.logger = YakRealms.getInstance().getLogger();
        this.teleportManager = TeleportManager.getInstance();
        this.destinationKey = new NamespacedKey(YakRealms.getInstance(), "teleport_destination");
        this.bookIdKey = new NamespacedKey(YakRealms.getInstance(), "teleport_book_id");
    }

    /**
     * Gets the singleton instance
     *
     * @return The TeleportBookSystem instance
     */
    public static TeleportBookSystem getInstance() {
        if (instance == null) {
            instance = new TeleportBookSystem();
        }
        return instance;
    }

    /**
     * Initializes the teleport book system
     */
    public void onEnable() {
        try {
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Load book templates from configuration
            loadBookTemplates();

            // Start periodic saving to prevent data loss
            startPeriodicSave();

            logger.info("TeleportBookSystem has been enabled");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable TeleportBookSystem", e);
        }
    }

    /**
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
        try {
            // Save book templates to configuration
            saveBookTemplates();

            logger.info("TeleportBookSystem has been disabled");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during TeleportBookSystem shutdown", e);
        }
    }

    /**
     * Loads book templates from configuration
     */
    private void loadBookTemplates() {
        try {
            // Clear existing templates
            bookTemplates.clear();

            // Initialize config file
            configFile = new File(YakRealms.getInstance().getDataFolder(), "teleport_books.yml");

            if (!configFile.exists()) {
                try {
                    YakRealms.getInstance().getDataFolder().mkdirs();
                    configFile.createNewFile();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Could not create teleport_books.yml", e);
                    return;
                }
            }

            config = YamlConfiguration.loadConfiguration(configFile);

            // Load custom books from config first
            loadCustomBooksFromConfig();

            // Create default books for destinations that don't have custom books
            boolean needsSave = false;
            for (TeleportDestination dest : teleportManager.getAllDestinations()) {
                String defaultBookId = "book_" + dest.getId().toLowerCase();
                if (!bookTemplates.containsKey(defaultBookId)) {
                    ItemStack template = createBookTemplate(dest.getId(), dest.getDisplayName());
                    if (template != null) {
                        needsSave = true;
                    }
                }
            }

            // Save immediately if we created new default templates
            if (needsSave) {
                saveBookTemplates();
            }

            logger.info("Loaded " + bookTemplates.size() + " teleport book templates");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading book templates", e);
        }
    }

    /**
     * Loads custom books from configuration
     */
    private void loadCustomBooksFromConfig() {
        if (config == null || !config.contains("books")) {
            return;
        }

        Set<String> bookIds = config.getConfigurationSection("books").getKeys(false);
        for (String bookId : bookIds) {
            try {
                String destId = config.getString("books." + bookId + ".destination");
                String customName = config.getString("books." + bookId + ".custom_name");
                List<String> customLore = config.getStringList("books." + bookId + ".custom_lore");

                if (destId == null) {
                    logger.warning("Missing destination for book " + bookId);
                    continue;
                }

                // Recreate the book from saved data
                TeleportDestination dest = teleportManager.getDestination(destId);
                if (dest != null) {
                    ItemStack book = createCustomBook(bookId, dest, customName, customLore);
                    if (book != null) {
                        bookTemplates.put(bookId, book);
                    }
                } else {
                    logger.warning("Destination not found for book " + bookId + ": " + destId);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error loading custom book " + bookId, e);
            }
        }
    }

    /**
     * Saves book templates to configuration
     */
    private void saveBookTemplates() {
        if (config == null) {
            config = new YamlConfiguration();
        }

        try {
            // Clear existing config
            config.set("books", null);

            // Save each book template
            for (Map.Entry<String, ItemStack> entry : bookTemplates.entrySet()) {
                String bookId = entry.getKey();
                ItemStack book = entry.getValue();

                if (book == null || !book.hasItemMeta()) {
                    continue;
                }

                ItemMeta meta = book.getItemMeta();
                if (meta == null) {
                    continue;
                }

                PersistentDataContainer container = meta.getPersistentDataContainer();
                String destId = container.has(destinationKey, PersistentDataType.STRING) ?
                        container.get(destinationKey, PersistentDataType.STRING) : null;

                if (destId != null) {
                    String path = "books." + bookId;
                    config.set(path + ".destination", destId);

                    if (meta.hasDisplayName()) {
                        config.set(path + ".custom_name", meta.getDisplayName());
                    }

                    if (meta.hasLore()) {
                        config.set(path + ".custom_lore", meta.getLore());
                    }
                }
            }

            // Save to file
            config.save(configFile);
            logger.fine("Saved " + bookTemplates.size() + " book templates to config");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save teleport book templates", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error saving teleport book templates", e);
        }
    }

    /**
     * Adds periodic saving task to prevent data loss
     */
    private void startPeriodicSave() {
        // Save book templates every 5 minutes
        Bukkit.getScheduler().runTaskTimerAsynchronously(YakRealms.getInstance(), () -> {
            try {
                saveBookTemplates();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during periodic book template save", e);
            }
        }, 6000L, 6000L); // 5 minutes = 6000 ticks
    }

    /**
     * Creates a book template for a destination
     *
     * @param destId      The destination ID
     * @param displayName The display name
     * @return The created template or null if destination not found
     */
    private ItemStack createBookTemplate(String destId, String displayName) {
        if (destId == null || displayName == null) {
            return null;
        }

        TeleportDestination destination = teleportManager.getDestination(destId);
        if (destination == null) {
            return null;
        }

        String bookId = "book_" + destId.toLowerCase();

        // Create a new book
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            // Set name
            meta.setDisplayName(ChatColor.WHITE.toString() + ChatColor.BOLD + "Teleport:" +
                    ChatColor.WHITE + " " + displayName);

            // Set lore
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Teleports the user to " + displayName + ".");
            meta.setLore(lore);

            // Store destination ID and book ID in persistent data
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(destinationKey, PersistentDataType.STRING, destination.getId());
            container.set(bookIdKey, PersistentDataType.STRING, bookId);

            book.setItemMeta(meta);
        }

        // Store template
        bookTemplates.put(bookId, book);

        return book;
    }

    /**
     * Creates a custom book for a destination
     *
     * @param bookId      The book ID
     * @param destination The destination
     * @param customName  Custom name or null
     * @param customLore  Custom lore or null
     * @return The created book
     */
    private ItemStack createCustomBook(String bookId, TeleportDestination destination, String customName, List<String> customLore) {
        if (bookId == null || destination == null) {
            return null;
        }

        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            // Set name
            if (customName != null && !customName.isEmpty()) {
                meta.setDisplayName(customName);
            } else {
                meta.setDisplayName(ChatColor.WHITE.toString() + ChatColor.BOLD + "Teleport:" +
                        ChatColor.WHITE + " " + destination.getDisplayName());
            }

            // Set lore
            if (customLore != null && !customLore.isEmpty()) {
                meta.setLore(customLore);
            } else {
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Teleports the user to " + destination.getDisplayName() + ".");
                meta.setLore(lore);
            }

            // Store destination ID and book ID in persistent data
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(destinationKey, PersistentDataType.STRING, destination.getId());
            container.set(bookIdKey, PersistentDataType.STRING, bookId);

            book.setItemMeta(meta);
        }

        return book;
    }

    /**
     * Creates a teleport book for a specific destination
     *
     * @param destId The destination ID
     * @param inShop Whether this is for shop display
     * @return The created teleport book or null if destination not found
     */
    public ItemStack createTeleportBook(String destId, boolean inShop) {
        if (destId == null) {
            return null;
        }

        TeleportDestination destination = teleportManager.getDestination(destId);
        if (destination == null) {
            logger.warning("Attempted to create teleport book for unknown destination: " + destId);
            return null;
        }

        // Check for existing template
        String bookId = "book_" + destId.toLowerCase();
        ItemStack template = bookTemplates.get(bookId);

        // If no template exists, create one
        if (template == null) {
            template = createBookTemplate(destId, destination.getDisplayName());
            if (template == null) {
                logger.warning("Failed to create template for destination: " + destId);
                return null;
            }
            // Save the new template immediately
            saveBookTemplates();
        }

        // Clone the template
        ItemStack book = template.clone();

        if (inShop && book.hasItemMeta()) {
            ItemMeta meta = book.getItemMeta();
            if (meta != null) {
                // If this is for shop display, add price info
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + destination.getCost() + "g");
                meta.setLore(lore);

                // Generate a unique instance ID for this book
                String instanceId = bookId + "_" + UUID.randomUUID().toString().substring(0, 8);
                PersistentDataContainer container = meta.getPersistentDataContainer();
                container.set(bookIdKey, PersistentDataType.STRING, instanceId);

                book.setItemMeta(meta);
            }
        }

        return book;
    }

    /**
     * Gets the destination ID from a teleport book
     *
     * @param item The item to check
     * @return The destination ID or null if not a teleport book
     */
    public String getDestinationFromBook(ItemStack item) {
        if (!isTeleportBook(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        // Check for stored destination in persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(destinationKey, PersistentDataType.STRING)) {
            return container.get(destinationKey, PersistentDataType.STRING);
        }

        // Fallback to legacy name parsing
        if (meta.hasDisplayName()) {
            return parseDestinationFromDisplayName(meta.getDisplayName(), item);
        }

        return null;
    }

    /**
     * Parses destination from display name (legacy support)
     */
    private String parseDestinationFromDisplayName(String displayName, ItemStack item) {
        if (displayName == null) {
            return null;
        }

        String strippedName = ChatColor.stripColor(displayName);
        String[] parts = strippedName.split(":");

        if (parts.length >= 2) {
            String destName = parts[1].trim();

            // Try to match by name
            for (TeleportDestination dest : teleportManager.getAllDestinations()) {
                if (dest.getDisplayName().equalsIgnoreCase(destName)) {
                    // Store this as persistent data for future use
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        PersistentDataContainer container = meta.getPersistentDataContainer();
                        container.set(destinationKey, PersistentDataType.STRING, dest.getId());
                        item.setItemMeta(meta);
                    }
                    return dest.getId();
                }
            }
        }

        return null;
    }

    /**
     * Checks if an item is a teleport book
     *
     * @param item The item to check
     * @return True if it's a teleport book
     */
    public boolean isTeleportBook(ItemStack item) {
        if (item == null || item.getType() != Material.BOOK || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Check for stored destination key
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(destinationKey, PersistentDataType.STRING)) {
            return true;
        }

        // Fallback to legacy detection via name
        if (meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            return displayName.contains("Teleport:") || displayName.contains("Scroll to");
        }

        return false;
    }

    /**
     * Registers a custom teleport book template
     *
     * @param bookId      Unique identifier for the book
     * @param destId      Destination ID
     * @param displayName Custom display name
     * @param lore        Custom lore
     * @return The created book template or null if failed
     */
    public ItemStack registerCustomBook(String bookId, String destId, String displayName, List<String> lore) {
        if (bookId == null || destId == null) {
            return null;
        }

        TeleportDestination destination = teleportManager.getDestination(destId);
        if (destination == null) {
            logger.warning("Cannot register custom book for unknown destination: " + destId);
            return null;
        }

        // Create the custom book
        ItemStack book = createCustomBook(bookId, destination, displayName, lore);
        if (book != null) {
            bookTemplates.put(bookId, book);
            // Save immediately when registering custom books
            saveBookTemplates();
        }

        return book;
    }

    /**
     * Creates a consumable for a teleport book
     *
     * @param player The player
     * @param item   The teleport book item
     * @return A consumable that will remove one book from the player's inventory
     */
    private TeleportConsumable createBookConsumable(Player player, ItemStack item) {
        return () -> {
            try {
                // Only consume the item if it's still in the player's inventory
                ItemStack handItem = player.getInventory().getItemInMainHand();

                if (handItem != null && handItem.getType() == Material.BOOK &&
                        handItem.hasItemMeta() && item.hasItemMeta() &&
                        handItem.getItemMeta() != null && item.getItemMeta() != null) {

                    // Compare the persistent data to ensure it's the same book
                    PersistentDataContainer handContainer = handItem.getItemMeta().getPersistentDataContainer();
                    PersistentDataContainer itemContainer = item.getItemMeta().getPersistentDataContainer();

                    String handDest = handContainer.get(destinationKey, PersistentDataType.STRING);
                    String itemDest = itemContainer.get(destinationKey, PersistentDataType.STRING);

                    if (Objects.equals(handDest, itemDest)) {
                        if (handItem.getAmount() > 1) {
                            handItem.setAmount(handItem.getAmount() - 1);
                        } else {
                            player.getInventory().setItemInMainHand(null);
                        }
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error consuming teleport book", e);
            }
        };
    }

    /**
     * Handles teleport book usage
     */
    @EventHandler
    public void onBookUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if the item is a teleport book
        if (!isTeleportBook(item)) {
            return;
        }

        // Get the destination ID
        String destId = getDestinationFromBook(item);
        if (destId == null) {
            player.sendMessage(ChatColor.RED + "This teleport book is invalid.");
            return;
        }

        // Prevent usage with off-hand items
        event.setCancelled(true);

        // Get the destination
        TeleportDestination destination = teleportManager.getDestination(destId);
        if (destination == null) {
            player.sendMessage(ChatColor.RED + "Invalid teleport destination.");
            return;
        }

        // Check if player is already teleporting
        if (teleportManager.isTeleporting(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already teleporting.");
            return;
        }

        // Check for alignment restrictions (chaotic players can't teleport)
        if (player.hasMetadata("alignment") &&
                player.getMetadata("alignment").size() > 0 &&
                "CHAOTIC".equals(player.getMetadata("alignment").get(0).asString())) {
            player.sendMessage(ChatColor.RED + "You " + ChatColor.UNDERLINE + "cannot" +
                    ChatColor.RED + " do this while chaotic!");
            return;
        }

        // Check for combat/duel restrictions
        if (player.hasMetadata("inCombat") &&
                player.getMetadata("inCombat").size() > 0 &&
                player.getMetadata("inCombat").get(0).asBoolean()) {
            player.sendMessage(ChatColor.RED + "You cannot teleport while in combat!");
            return;
        }

        // Create a consumable to remove the book
        TeleportConsumable consumable = createBookConsumable(player, item);

        // Start the teleport
        if (!teleportManager.startTeleport(player, destination, 5, consumable, TeleportEffectType.SCROLL)) {
            player.sendMessage(ChatColor.RED + "Failed to start teleportation.");
        }
    }

    /**
     * Gets all book templates
     *
     * @return A copy of the book templates map
     */
    public Map<String, ItemStack> getBookTemplates() {
        return new HashMap<>(bookTemplates);
    }

    /**
     * Reloads book templates from configuration
     */
    public void reloadBookTemplates() {
        try {
            loadBookTemplates();
            logger.info("Teleport book templates reloaded successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error reloading book templates", e);
        }
    }

    /**
     * Debug method to check book template status
     */
    public void debugBookTemplates() {
        logger.info("=== TELEPORT BOOK DEBUG INFO ===");
        logger.info("Config file exists: " + (configFile != null && configFile.exists()));
        logger.info("Config object: " + (config != null ? "loaded" : "null"));
        logger.info("Book templates in memory: " + bookTemplates.size());

        for (Map.Entry<String, ItemStack> entry : bookTemplates.entrySet()) {
            String bookId = entry.getKey();
            ItemStack book = entry.getValue();

            String destId = "unknown";
            if (book != null && book.hasItemMeta() && book.getItemMeta() != null) {
                PersistentDataContainer container = book.getItemMeta().getPersistentDataContainer();
                destId = container.get(destinationKey, PersistentDataType.STRING);
            }

            logger.info("  - " + bookId + " -> " + destId);
        }

        // Check config contents
        if (config != null && config.contains("books")) {
            Set<String> configBooks = config.getConfigurationSection("books").getKeys(false);
            logger.info("Books in config file: " + configBooks.size());
            for (String bookId : configBooks) {
                String destId = config.getString("books." + bookId + ".destination");
                logger.info("  - " + bookId + " -> " + destId);
            }
        } else {
            logger.info("No books section in config file");
        }

        logger.info("Available destinations: " + teleportManager.getAllDestinations().size());
        for (TeleportDestination dest : teleportManager.getAllDestinations()) {
            logger.info("  - " + dest.getId() + " (" + dest.getDisplayName() + ")");
        }
        logger.info("=== END DEBUG INFO ===");
    }

    /**
     * Forces a reload and save of all book templates
     */
    public void forceReloadAndSave() {
        logger.info("Forcing reload and save of book templates...");
        loadBookTemplates();
        saveBookTemplates();
        logger.info("Force reload and save completed");
    }

    /**
     * Manually triggers a save of book templates
     */
    public void forceSave() {
        try {
            saveBookTemplates();
            logger.info("Book templates saved successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error manually saving book templates", e);
        }
    }

    /**
     * Gets the number of book templates currently loaded
     */
    public int getBookTemplateCount() {
        return bookTemplates.size();
    }

    /**
     * Checks if a book template exists for a destination
     */
    public boolean hasBookTemplate(String destId) {
        if (destId == null) {
            return false;
        }
        String bookId = "book_" + destId.toLowerCase();
        return bookTemplates.containsKey(bookId);
    }

    /**
     * Removes a book template
     */
    public boolean removeBookTemplate(String bookId) {
        if (bookId == null) {
            return false;
        }

        boolean removed = bookTemplates.remove(bookId) != null;
        if (removed) {
            saveBookTemplates();
        }

        return removed;
    }
}