package com.rednetty.server.core.mechanics.world.teleport;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.scheduler.BukkitTask;

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
    private final Map<String, BookTemplate> bookTemplates = new HashMap<>();
    private File configFile;
    private FileConfiguration config;
    private BukkitTask periodicSaveTask;
    private boolean isShuttingDown = false;

    /**
     * Initializes the teleport book system
     */
    public void onEnable() {
        try {
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Initialize configuration file
            initializeConfigFile();

            // Load book templates from configuration
            loadBookTemplates();

            // Start periodic saving to prevent data loss
            startPeriodicSave();

            logger.info("TeleportBookSystem enabled successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable TeleportBookSystem", e);
        }
    }

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
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
        try {
            isShuttingDown = true;

            // Cancel periodic save task
            if (periodicSaveTask != null && !periodicSaveTask.isCancelled()) {
                periodicSaveTask.cancel();
                periodicSaveTask = null;
            }

            // Perform final save
            saveBookTemplates();

            logger.info("TeleportBookSystem has been disabled");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during TeleportBookSystem shutdown", e);
        }
    }

    /**
     * Initializes the configuration file
     */
    private void initializeConfigFile() {
        try {
            // Ensure data folder exists
            File dataFolder = YakRealms.getInstance().getDataFolder();
            if (!dataFolder.exists()) {
                boolean created = dataFolder.mkdirs();
                if (!created) {
                    logger.severe("Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
                    return;
                }
            }

            // Initialize config file
            configFile = new File(dataFolder, "teleport_books.yml");
            logger.info("Config file path: " + configFile.getAbsolutePath());

            if (!configFile.exists()) {
                try {
                    boolean created = configFile.createNewFile();
                    if (created) {
                        logger.info("Created new teleport_books.yml file");
                    } else {
                        logger.warning("Failed to create teleport_books.yml file");
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed to create teleport_books.yml file", e);
                    return;
                }
            }

            // Load configuration
            config = YamlConfiguration.loadConfiguration(configFile);
            logger.info("Loaded configuration file successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing config file", e);
        }
    }

    /**
     * Loads book templates from configuration
     */
    private void loadBookTemplates() {
        try {
            logger.info("Loading book templates...");

            // Clear existing templates
            bookTemplates.clear();

            if (config == null) {
                logger.warning("Config is null, cannot load book templates");
                return;
            }

            // Load custom books from config first
            loadCustomBooksFromConfig();

            // Wait a tick to ensure destinations are fully loaded
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                try {
                    createDefaultBookTemplates();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error creating default book templates", e);
                }
            });

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading book templates", e);
        }
    }

    /**
     * Loads custom books from configuration
     */
    private void loadCustomBooksFromConfig() {
        if (config == null) {
            logger.warning("Config is null, cannot load custom books");
            return;
        }

        ConfigurationSection booksSection = config.getConfigurationSection("books");
        if (booksSection == null) {
            logger.info("No existing books section found in config");
            return;
        }

        int loadedCount = 0;
        for (String bookId : booksSection.getKeys(false)) {
            try {
                ConfigurationSection bookSection = booksSection.getConfigurationSection(bookId);
                if (bookSection == null) {
                    logger.warning("Invalid book section for " + bookId);
                    continue;
                }

                String destId = bookSection.getString("destination");
                String customName = bookSection.getString("custom_name");
                List<String> customLore = bookSection.getStringList("custom_lore");

                if (destId == null || destId.isEmpty()) {
                    logger.warning("Missing destination for book " + bookId);
                    continue;
                }

                // Validate destination exists
                TeleportDestination dest = teleportManager.getDestination(destId);
                if (dest == null) {
                    logger.warning("Destination not found for book " + bookId + ": " + destId);
                    continue;
                }

                // Create book template
                BookTemplate template = new BookTemplate(bookId, destId, customName, customLore);
                bookTemplates.put(bookId, template);
                loadedCount++;

                logger.fine("Loaded custom book: " + bookId + " -> " + destId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error loading custom book " + bookId, e);
            }
        }

        logger.info("Loaded " + loadedCount + " custom books from config");
    }

    /**
     * Creates default book templates for destinations that don't have custom books
     */
    private void createDefaultBookTemplates() {
        Collection<TeleportDestination> destinations = teleportManager.getAllDestinations();
        if (destinations.isEmpty()) {
            logger.warning("No destinations available for creating default book templates");
            return;
        }

        int createdCount = 0;
        boolean needsSave = false;

        for (TeleportDestination dest : destinations) {
            String defaultBookId = "book_" + dest.getId().toLowerCase();
            if (!bookTemplates.containsKey(defaultBookId)) {
                createDefaultBookTemplate(dest);
                createdCount++;
                needsSave = true;
            }
        }

        if (needsSave) {
            logger.info("Created " + createdCount + " default book templates");
            saveBookTemplates();
        }

        logger.info("Total book templates loaded: " + bookTemplates.size());
    }

    /**
     * Creates a default book template for a destination
     */
    private void createDefaultBookTemplate(TeleportDestination dest) {
        String bookId = "book_" + dest.getId().toLowerCase();
        BookTemplate template = new BookTemplate(bookId, dest.getId(), null, null);
        bookTemplates.put(bookId, template);
        logger.fine("Created default book template: " + bookId + " -> " + dest.getId());
    }

    /**
     * Saves book templates to configuration
     */
    private synchronized void saveBookTemplates() {
        if (config == null) {
            logger.warning("Cannot save book templates: config is null");
            return;
        }

        if (configFile == null) {
            logger.warning("Cannot save book templates: configFile is null");
            return;
        }

        try {
            logger.fine("Saving " + bookTemplates.size() + " book templates...");

            // Clear existing books section
            config.set("books", null);

            // Save each book template
            for (BookTemplate template : bookTemplates.values()) {
                String path = "books." + template.bookId;
                config.set(path + ".destination", template.destinationId);

                if (template.customName != null && !template.customName.trim().isEmpty()) {
                    config.set(path + ".custom_name", template.customName);
                }

                if (template.customLore != null && !template.customLore.isEmpty()) {
                    config.set(path + ".custom_lore", template.customLore);
                }
            }

            // Save to file
            config.save(configFile);
            logger.info("Successfully saved " + bookTemplates.size() + " book templates to " + configFile.getName());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save teleport book templates to file", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error saving teleport book templates", e);
        }
    }

    /**
     * Starts periodic saving task to prevent data loss
     */
    private void startPeriodicSave() {
        // Cancel existing task if any
        if (periodicSaveTask != null && !periodicSaveTask.isCancelled()) {
            periodicSaveTask.cancel();
        }

        // Save book templates every 5 minutes on main thread
        periodicSaveTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            if (!isShuttingDown) {
                try {
                    saveBookTemplates();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error during periodic book template save", e);
                }
            }
        }, 6000L, 6000L); // 5 minutes = 6000 ticks

        logger.info("Started periodic save task for teleport book templates");
    }

    /**
     * Creates an ItemStack from a book template
     */
    private ItemStack createItemFromTemplate(BookTemplate template, boolean inShop) {
        TeleportDestination destination = teleportManager.getDestination(template.destinationId);
        if (destination == null) {
            logger.warning("Cannot create book for unknown destination: " + template.destinationId);
            return null;
        }

        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            // Set name
            if (template.customName != null && !template.customName.trim().isEmpty()) {
                meta.setDisplayName(template.customName);
            } else {
                meta.setDisplayName(ChatColor.WHITE.toString() + ChatColor.BOLD + "Teleport:" +
                        ChatColor.WHITE + " " + destination.getDisplayName());
            }

            // Set lore
            List<String> lore = new ArrayList<>();
            if (template.customLore != null && !template.customLore.isEmpty()) {
                lore.addAll(template.customLore);
            } else {
                lore.add(ChatColor.GRAY + "Teleports the user to " + destination.getDisplayName() + ".");
            }

            // Add price info if in shop
            if (inShop) {
                lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + destination.getCost() + "g");
            }

            meta.setLore(lore);

            // Store destination ID and book ID in persistent data
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(destinationKey, PersistentDataType.STRING, destination.getId());
            container.set(bookIdKey, PersistentDataType.STRING, template.bookId);

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
        BookTemplate template = bookTemplates.get(bookId);

        // If no template exists, create one and save immediately
        if (template == null) {
            createDefaultBookTemplate(destination);
            template = bookTemplates.get(bookId);
            saveBookTemplates();
        }

        return createItemFromTemplate(template, inShop);
    }

    /**
     * Gets the display name from a teleport book
     *
     * @param item The item to check
     * @return The display name or null if not a teleport book or no display name
     */
    public String getDisplayNameFromBook(ItemStack item) {
        if (!isTeleportBook(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }

        return meta.getDisplayName();
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
     * Registers a custom teleport book template
     *
     * @param bookId      Unique identifier for the book
     * @param destId      Destination ID
     * @param displayName Custom display name
     * @param lore        Custom lore
     * @return True if successfully registered
     */
    public boolean registerCustomBook(String bookId, String destId, String displayName, List<String> lore) {
        if (bookId == null || destId == null) {
            return false;
        }

        TeleportDestination destination = teleportManager.getDestination(destId);
        if (destination == null) {
            logger.warning("Cannot register custom book for unknown destination: " + destId);
            return false;
        }

        // Create the custom book template
        BookTemplate template = new BookTemplate(bookId, destId, displayName, lore);
        bookTemplates.put(bookId, template);

        // Save immediately when registering custom books
        saveBookTemplates();
        logger.info("Registered custom book: " + bookId + " -> " + destId);
        return true;
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
     * Creates a consumable for a teleport book with improved robustness
     *
     * @param player The player
     * @param item   The teleport book item
     * @return A consumable that will remove one book from the player's inventory
     */
    private TeleportConsumable createBookConsumable(Player player, ItemStack item) {
        // Store the original item details for comparison
        final String originalDestination = getDestinationFromBook(item);
        final String originalDisplayName = getDisplayNameFromBook(item);

        return () -> {
            try {
                if (player == null || !player.isOnline()) {
                    return;
                }

                // Check main hand first
                ItemStack handItem = player.getInventory().getItemInMainHand();
                boolean consumedFromMainHand = false;

                if (isMatchingTeleportBook(handItem, originalDestination, originalDisplayName)) {
                    if (handItem.getAmount() > 1) {
                        handItem.setAmount(handItem.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                    consumedFromMainHand = true;
                    logger.fine("Consumed teleport book from main hand for player " + player.getName());
                }

                // If not consumed from main hand, search entire inventory
                if (!consumedFromMainHand) {
                    ItemStack[] contents = player.getInventory().getContents();
                    if (contents != null) {
                        for (int i = 0; i < contents.length; i++) {
                            ItemStack invItem = contents[i];
                            if (isMatchingTeleportBook(invItem, originalDestination, originalDisplayName)) {
                                if (invItem.getAmount() > 1) {
                                    invItem.setAmount(invItem.getAmount() - 1);
                                } else {
                                    player.getInventory().setItem(i, null);
                                }
                                logger.fine("Consumed teleport book from inventory slot " + i + " for player " + player.getName());
                                break;
                            }
                        }
                    }
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error consuming teleport book for player " + player.getName(), e);
            }
        };
    }

    /**
     * Checks if an item matches the original teleport book
     *
     * @param item                The item to check
     * @param originalDestination The original destination ID
     * @param originalDisplayName The original display name
     * @return True if it's a matching teleport book
     */
    private boolean isMatchingTeleportBook(ItemStack item, String originalDestination, String originalDisplayName) {
        if (!isTeleportBook(item)) {
            return false;
        }

        // Compare destination ID
        String itemDestination = getDestinationFromBook(item);
        if (!Objects.equals(originalDestination, itemDestination)) {
            return false;
        }

        // Compare display name if available
        if (originalDisplayName != null) {
            String itemDisplayName = getDisplayNameFromBook(item);
            return Objects.equals(originalDisplayName, itemDisplayName);
        }

        return true;
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

        // Get the display name from the book for the teleport message
        String bookDisplayName = getDisplayNameFromBook(item);

        // Create a consumable to remove the book
        TeleportConsumable consumable = createBookConsumable(player, item);

        // Start the teleport with the book's display name
        if (!teleportManager.startTeleport(player, destination, 5, consumable, TeleportEffectType.SCROLL, bookDisplayName)) {
            player.sendMessage(ChatColor.RED + "Failed to start teleportation.");
        }
    }

    /**
     * Gets all book templates
     *
     * @return A copy of the book templates map
     */
    public Map<String, ItemStack> getBookTemplates() {
        Map<String, ItemStack> items = new HashMap<>();
        for (Map.Entry<String, BookTemplate> entry : bookTemplates.entrySet()) {
            ItemStack item = createItemFromTemplate(entry.getValue(), false);
            if (item != null) {
                items.put(entry.getKey(), item);
            }
        }
        return items;
    }

    /**
     * Debug method to check book template status
     */
    public void debugBookTemplates() {
        logger.info("=== TELEPORT BOOK DEBUG INFO ===");
        logger.info("Config file exists: " + (configFile != null && configFile.exists()));
        logger.info("Config file path: " + (configFile != null ? configFile.getAbsolutePath() : "null"));
        logger.info("Config object: " + (config != null ? "loaded" : "null"));
        logger.info("Book templates in memory: " + bookTemplates.size());

        for (Map.Entry<String, BookTemplate> entry : bookTemplates.entrySet()) {
            String bookId = entry.getKey();
            BookTemplate template = entry.getValue();
            logger.info("  - " + bookId + " -> " + template.destinationId +
                    (template.customName != null ? " (custom: " + template.customName + ")" : " (default)"));
        }

        // Check config contents
        if (config != null && config.contains("books")) {
            ConfigurationSection booksSection = config.getConfigurationSection("books");
            if (booksSection != null) {
                Set<String> configBooks = booksSection.getKeys(false);
                logger.info("Books in config file: " + configBooks.size());
                for (String bookId : configBooks) {
                    String destId = config.getString("books." + bookId + ".destination");
                    String customName = config.getString("books." + bookId + ".custom_name");
                    logger.info("  - " + bookId + " -> " + destId +
                            (customName != null ? " (custom: " + customName + ")" : " (default)"));
                }
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
     * Forces a reload and save of all book templates
     */
    public void forceReloadAndSave() {
        logger.info("Forcing reload and save of book templates...");
        loadBookTemplates();
        // Wait a tick for async loading to complete
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            saveBookTemplates();
            logger.info("Force reload and save completed");
        }, 2L);
    }

    /**
     * Manually triggers a save of book templates
     */
    public void forceSave() {
        try {
            saveBookTemplates();
            logger.info("Book templates force saved successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error manually saving book templates", e);
        }
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
            logger.info("Removed book template: " + bookId);
        }

        return removed;
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
     * Creates a hearthstone for a player with their current home location
     *
     * @param player The player
     * @return The created hearthstone or null if failed
     */
    public ItemStack createHearthstoneForPlayer(Player player) {
        if (player == null) {
            return null;
        }

        String homeLocation = HearthstoneSystem.getInstance().getPlayerHomeLocation(player);
        return HearthstoneSystem.getInstance().createHearthstone(homeLocation);
    }

    /**
     * Gets the config file for external access
     */
    public File getConfigFile() {
        return configFile;
    }

    /**
     * Checks if the system is currently shutting down
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    // Book template data class
    private static class BookTemplate {
        final String bookId;
        final String destinationId;
        final String customName;
        final List<String> customLore;

        BookTemplate(String bookId, String destinationId, String customName, List<String> customLore) {
            this.bookId = bookId;
            this.destinationId = destinationId;
            this.customName = customName;
            this.customLore = customLore != null ? new ArrayList<>(customLore) : new ArrayList<>();
        }
    }
}