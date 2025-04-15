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
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Load book templates from configuration
        loadBookTemplates();

        logger.info("TeleportBookSystem has been enabled");
    }

    /**
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
        // Save book templates to configuration
        saveBookTemplates();

        logger.info("TeleportBookSystem has been disabled");
    }

    /**
     * Loads book templates from configuration
     */
    private void loadBookTemplates() {
        // Clear existing templates
        bookTemplates.clear();

        // Initialize config file
        configFile = new File(YakRealms.getInstance().getDataFolder(), "teleport_books.yml");

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not create teleport_books.yml", e);
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Load default books for standard destinations
        for (TeleportDestination dest : teleportManager.getAllDestinations()) {
            // Create a standard book template for each destination
            createBookTemplate(dest.getId(), dest.getDisplayName());
        }

        // Load custom books from config
        if (config.contains("books")) {
            for (String bookId : config.getConfigurationSection("books").getKeys(false)) {
                String destId = config.getString("books." + bookId + ".destination");
                String customName = config.getString("books." + bookId + ".custom_name");
                List<String> customLore = config.getStringList("books." + bookId + ".custom_lore");

                // Recreate the book from saved data
                TeleportDestination dest = teleportManager.getDestination(destId);
                if (dest != null) {
                    ItemStack book = createCustomBook(bookId, dest, customName, customLore);
                    if (book != null) {
                        bookTemplates.put(bookId, book);
                    }
                }
            }
        }

        logger.info("Loaded " + bookTemplates.size() + " teleport book templates");
    }

    /**
     * Saves book templates to configuration
     */
    private void saveBookTemplates() {
        if (config == null) {
            config = new YamlConfiguration();
        }

        // Clear existing config
        config.set("books", null);

        // Save each book template
        for (Map.Entry<String, ItemStack> entry : bookTemplates.entrySet()) {
            String bookId = entry.getKey();
            ItemStack book = entry.getValue();
            ItemMeta meta = book.getItemMeta();

            if (meta != null) {
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
        }

        // Save to file
        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save teleport book templates", e);
        }
    }

    /**
     * Creates a book template for a destination
     *
     * @param destId      The destination ID
     * @param displayName The display name
     * @return The created template or null if destination not found
     */
    private ItemStack createBookTemplate(String destId, String displayName) {
        TeleportDestination destination = teleportManager.getDestination(destId);
        if (destination == null) {
            return null;
        }

        String bookId = "book_" + destId.toLowerCase();

        // Skip if we already have this template
        if (bookTemplates.containsKey(bookId)) {
            return bookTemplates.get(bookId);
        }

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
        }

        // Clone the template
        ItemStack book = template.clone();
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            // If this is for shop display, add price info
            if (inShop) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();
                lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + destination.getCost() + "g");
                meta.setLore(lore);
            }

            // Generate a unique instance ID for this book
            String instanceId = bookId + "_" + UUID.randomUUID().toString().substring(0, 8);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(bookIdKey, PersistentDataType.STRING, instanceId);

            book.setItemMeta(meta);
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
        if (item == null || item.getType() != Material.BOOK || !item.hasItemMeta()) {
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
            String displayName = ChatColor.stripColor(meta.getDisplayName());
            String[] parts = displayName.split(":");

            if (parts.length >= 2) {
                String destName = parts[1].trim();

                // Try to match by name
                for (TeleportDestination dest : teleportManager.getAllDestinations()) {
                    if (dest.getDisplayName().equalsIgnoreCase(destName)) {
                        // Store this as persistent data for future use
                        container.set(destinationKey, PersistentDataType.STRING, dest.getId());
                        item.setItemMeta(meta);
                        return dest.getId();
                    }
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
        TeleportDestination destination = teleportManager.getDestination(destId);
        if (destination == null) {
            logger.warning("Cannot register custom book for unknown destination: " + destId);
            return null;
        }

        // Create the custom book
        ItemStack book = createCustomBook(bookId, destination, displayName, lore);
        if (book != null) {
            bookTemplates.put(bookId, book);
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
            // Only consume the item if it's still in the player's inventory
            ItemStack handItem = player.getInventory().getItemInMainHand();

            if (handItem != null && handItem.getType() == Material.BOOK &&
                    handItem.getItemMeta() != null && handItem.getItemMeta().equals(item.getItemMeta())) {

                if (handItem.getAmount() > 1) {
                    handItem.setAmount(handItem.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
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
        if (player.hasMetadata("alignment") && player.getMetadata("alignment").get(0).asString().equals("CHAOTIC")) {
            player.sendMessage(ChatColor.RED + "You " + ChatColor.UNDERLINE + "cannot" +
                    ChatColor.RED + " do this while chaotic!");
            return;
        }

        // Check for combat/duel restrictions
        if (player.hasMetadata("inCombat") && player.getMetadata("inCombat").get(0).asBoolean()) {
            player.sendMessage(ChatColor.RED + "You cannot teleport while in combat!");
            return;
        }

        // Create a consumable to remove the book
        TeleportConsumable consumable = createBookConsumable(player, item);

        // Start the teleport
        teleportManager.startTeleport(player, destination, 5, consumable, TeleportEffectType.SCROLL);
    }
}