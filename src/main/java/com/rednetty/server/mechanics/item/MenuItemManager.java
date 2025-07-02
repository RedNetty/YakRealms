package com.rednetty.server.mechanics.item;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages menu items in the player's 2x2 crafting grid.
 * Provides a clean interface for adding menu items that open various interfaces.
 * FIXED: Uses correct crafting slot access method and auto-setup
 */
public class MenuItemManager {

    private static MenuItemManager instance;
    private final Logger logger;
    private final YakPlayerManager playerManager;

    // Track players who have menu items loaded
    private final Set<UUID> playersWithMenuItems = ConcurrentHashMap.newKeySet();

    // Configuration
    private static final String MENU_ITEM_NBT_KEY = "yakMenuItemType";
    private static final String MENU_ITEM_ID_KEY = "yakMenuItemId";

    // FIXED: Correct crafting slot numbers for crafting inventory view
    private static final int[] CRAFTING_SLOTS = {1, 2, 3, 4}; // 2x2 crafting grid slots in crafting view
    private static final int CRAFTING_RESULT_SLOT = 0;

    /**
     * Enum defining available menu item types
     */
    public enum MenuItemType {
        JOURNAL("character_journal", Material.WRITTEN_BOOK, "&a&lCharacter Journal",
                Arrays.asList("&7View your character stats", "&7and progression details", "", "&e&l➤ Click to open!")),

        HELP_GUIDE("help_guide", Material.KNOWLEDGE_BOOK, "&b&lHelp Guide",
                Arrays.asList("&7Server commands and", "&7useful information", "", "&e&l➤ Click to open!")),

        SETTINGS("player_settings", Material.REDSTONE, "&c&lPlayer Settings",
                Arrays.asList("&7Configure your gameplay", "&7preferences and toggles", "", "&e&l➤ Click to open!")),

        FRIENDS("friends_list", Material.PLAYER_HEAD, "&d&lFriends & Social",
                Arrays.asList("&7Manage your friends,", "&7parties, and social features", "", "&e&l➤ Click to open!"));

        private final String id;
        private final Material material;
        private final String displayName;
        private final List<String> lore;

        MenuItemType(String id, Material material, String displayName, List<String> lore) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
        }

        public String getId() { return id; }
        public Material getMaterial() { return material; }
        public String getDisplayName() { return displayName; }
        public List<String> getLore() { return lore; }
    }

    /**
     * Data class for menu item configuration
     */
    public static class MenuItemData {
        private final MenuItemType type;
        private final int slot;
        private final boolean enabled;

        public MenuItemData(MenuItemType type, int slot, boolean enabled) {
            this.type = type;
            this.slot = slot;
            this.enabled = enabled;
        }

        public MenuItemType getType() { return type; }
        public int getSlot() { return slot; }
        public boolean isEnabled() { return enabled; }
    }

    private MenuItemManager() {
        this.logger = YakRealms.getInstance().getLogger();
        this.playerManager = YakPlayerManager.getInstance();
    }

    public static MenuItemManager getInstance() {
        if (instance == null) {
            instance = new MenuItemManager();
        }
        return instance;
    }

    /**
     * Initialize the menu item system
     */
    public void initialize() {
        logger.info("MenuItemManager initialized successfully");

        // Schedule cleanup task
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOfflinePlayers();
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L * 300, 20L * 300); // Every 5 minutes
    }

    /**
     * FIXED: Set up menu items for a player using the correct crafting inventory access
     */
    public void setupMenuItems(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            // FIXED: Check if player has crafting inventory open
            if (!hasCraftingInventoryOpen(player)) {
                return;
            }

            // Clear any existing menu items first
            clearMenuItems(player);

            // Get the crafting inventory
            Inventory craftingInventory = player.getOpenInventory().getTopInventory();

            // Get menu item configuration
            List<MenuItemData> menuItems = getMenuItemConfiguration(player);

            // Place each menu item in the appropriate slot
            for (MenuItemData itemData : menuItems) {
                if (itemData.isEnabled() && isValidCraftingSlot(itemData.getSlot())) {
                    ItemStack menuItem = createMenuItem(itemData.getType());
                    craftingInventory.setItem(itemData.getSlot(), menuItem);
                }
            }

            playersWithMenuItems.add(playerId);
            logger.fine("Menu items set up for player: " + player.getName());

        } catch (Exception e) {
            logger.warning("Error setting up menu items for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * FIXED: Clear all menu items from a player's crafting grid using correct inventory access
     */
    public void clearMenuItems(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            // FIXED: Check if player has crafting inventory open
            if (!hasCraftingInventoryOpen(player)) {
                playersWithMenuItems.remove(playerId);
                return;
            }

            // Get the crafting inventory
            Inventory craftingInventory = player.getOpenInventory().getTopInventory();

            // Clear all crafting slots
            for (int slot : CRAFTING_SLOTS) {
                ItemStack item = craftingInventory.getItem(slot);
                if (isMenuItem(item)) {
                    craftingInventory.setItem(slot, null);
                }
            }

            playersWithMenuItems.remove(playerId);
            logger.fine("Menu items cleared for player: " + player.getName());

        } catch (Exception e) {
            logger.warning("Error clearing menu items for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * FIXED: Check if player has crafting inventory open (their own inventory)
     */
    private boolean hasCraftingInventoryOpen(Player player) {
        try {
            return player.getOpenInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Handle clicking on a menu item
     */
    public boolean handleMenuItemClick(Player player, ItemStack clickedItem, InventoryClickEvent event) {
        if (!isMenuItem(clickedItem)) {
            return false;
        }

        // Cancel the click event to prevent item manipulation
        event.setCancelled(true);

        // Get the menu item type
        MenuItemType itemType = getMenuItemType(clickedItem);
        if (itemType == null) {
            logger.warning("Unknown menu item type clicked by " + player.getName());
            return true;
        }

        // Handle the menu item action
        return handleMenuItemAction(player, itemType);
    }

    /**
     * Handle the action for a specific menu item type
     */
    private boolean handleMenuItemAction(Player player, MenuItemType itemType) {
        try {
            switch (itemType) {
                case JOURNAL:
                    openCharacterJournal(player);
                    return true;

                case HELP_GUIDE:
                    openHelpGuide(player);
                    return true;

                case SETTINGS:
                    openPlayerSettings(player);
                    return true;

                case FRIENDS:
                    openFriendsInterface(player);
                    return true;

                default:
                    logger.warning("Unhandled menu item type: " + itemType);
                    return false;
            }
        } catch (Exception e) {
            logger.warning("Error handling menu item action for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "An error occurred while opening that menu. Please try again.");
            return true;
        }
    }

    /**
     * Open the character journal
     */
    private void openCharacterJournal(Player player) {
        try {
            Journal.openJournal(player);
            logger.fine("Opened character journal for " + player.getName());
        } catch (Exception e) {
            logger.warning("Error opening character journal for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Error opening character journal. Please try again.");
        }
    }

    /**
     * Open the help guide
     */
    private void openHelpGuide(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "           YakRealms Help Guide");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Essential Commands:");
        player.sendMessage(ChatColor.WHITE + "/help " + ChatColor.GRAY + "- Show this help menu");
        player.sendMessage(ChatColor.WHITE + "/stats " + ChatColor.GRAY + "- View your character stats");
        player.sendMessage(ChatColor.WHITE + "/p invite <player> " + ChatColor.GRAY + "- Invite to party");
        player.sendMessage(ChatColor.WHITE + "/p accept " + ChatColor.GRAY + "- Accept party invite");
        player.sendMessage(ChatColor.WHITE + "/add <player> " + ChatColor.GRAY + "- Add as friend");
        player.sendMessage(ChatColor.WHITE + "/msg <player> <message> " + ChatColor.GRAY + "- Private message");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Server Features:");
        player.sendMessage(ChatColor.GRAY + "• Custom RPG mechanics and leveling");
        player.sendMessage(ChatColor.GRAY + "• Player guilds and parties");
        player.sendMessage(ChatColor.GRAY + "• Economy system with gems");
        player.sendMessage(ChatColor.GRAY + "• Player vs Player combat areas");
        player.sendMessage(ChatColor.GRAY + "• Custom items and enchantments");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Need more help? Ask in chat or contact staff!");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage("");
    }

    /**
     * Open player settings
     */
    private void openPlayerSettings(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            player.sendMessage(ChatColor.RED + "Error loading your settings. Please try again.");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "         Player Settings");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Current Settings:");

        // Show current toggle settings
        Set<String> toggles = yakPlayer.getToggleSettings();
        String[] settingsList = {"Player Messages", "Drop Protection", "Sound Effects", "Party Invites"};

        for (String setting : settingsList) {
            boolean enabled = toggles.contains(setting);
            ChatColor color = enabled ? ChatColor.GREEN : ChatColor.RED;
            String status = enabled ? "ON" : "OFF";
            player.sendMessage(ChatColor.WHITE + "• " + setting + ": " + color + status);
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Use '/toggle <setting>' to change settings");
        player.sendMessage(ChatColor.GRAY + "Available settings: messages, protection, sounds, party");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage("");
    }

    /**
     * Open friends interface
     */
    private void openFriendsInterface(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            player.sendMessage(ChatColor.RED + "Error loading your friends list. Please try again.");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "         Friends & Social");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage("");

        Set<String> friends = yakPlayer.getBuddies();
        if (friends.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You don't have any friends yet.");
            player.sendMessage(ChatColor.YELLOW + "Use '/add <player>' to add friends!");
        } else {
            player.sendMessage(ChatColor.GREEN + "Your Friends (" + friends.size() + "):");

            for (String friendName : friends) {
                Player friendPlayer = Bukkit.getPlayer(friendName);
                if (friendPlayer != null && friendPlayer.isOnline()) {
                    player.sendMessage(ChatColor.WHITE + "• " + friendName + ChatColor.GREEN + " (Online)");
                } else {
                    player.sendMessage(ChatColor.WHITE + "• " + friendName + ChatColor.GRAY + " (Offline)");
                }
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Social Commands:");
        player.sendMessage(ChatColor.WHITE + "/add <player> " + ChatColor.GRAY + "- Add friend");
        player.sendMessage(ChatColor.WHITE + "/remove <player> " + ChatColor.GRAY + "- Remove friend");
        player.sendMessage(ChatColor.WHITE + "/p invite <player> " + ChatColor.GRAY + "- Invite to party");
        player.sendMessage(ChatColor.WHITE + "/msg <player> <message> " + ChatColor.GRAY + "- Private message");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
        player.sendMessage("");
    }

    /**
     * Create a menu item with proper NBT data
     */
    private ItemStack createMenuItem(MenuItemType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Set display name
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));

            // Set lore
            List<String> coloredLore = new ArrayList<>();
            for (String line : type.getLore()) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);

            // Hide all flags to make it look clean
            meta.addItemFlags(ItemFlag.values());

            item.setItemMeta(meta);
        }

        // Add NBT data to mark as menu item
        NBTAccessor nbt = new NBTAccessor(item);
        nbt.setString(MENU_ITEM_NBT_KEY, type.name());
        nbt.setString(MENU_ITEM_ID_KEY, UUID.randomUUID().toString());
        nbt.setBoolean("yakMenuItemNoPickup", true);
        nbt.setBoolean("yakMenuItemNoMove", true);
        nbt.setBoolean("yakMenuItemNoDrop", true);

        return nbt.update();
    }

    /**
     * Check if an item is a menu item
     */
    public boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        NBTAccessor nbt = new NBTAccessor(item);
        return nbt.hasKey(MENU_ITEM_NBT_KEY);
    }

    /**
     * Get the menu item type from an item
     */
    public MenuItemType getMenuItemType(ItemStack item) {
        if (!isMenuItem(item)) {
            return null;
        }

        NBTAccessor nbt = new NBTAccessor(item);
        String typeName = nbt.getString(MENU_ITEM_NBT_KEY);

        if (typeName != null) {
            try {
                return MenuItemType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid menu item type: " + typeName);
            }
        }

        return null;
    }

    /**
     * Get menu item configuration for a player
     * This can be customized based on player rank, settings, etc.
     */
    private List<MenuItemData> getMenuItemConfiguration(Player player) {
        List<MenuItemData> config = new ArrayList<>();

        // Default configuration - can be customized based on player needs
        config.add(new MenuItemData(MenuItemType.JOURNAL, 1, true));
        config.add(new MenuItemData(MenuItemType.HELP_GUIDE, 2, true));
        config.add(new MenuItemData(MenuItemType.SETTINGS, 3, true));
        config.add(new MenuItemData(MenuItemType.FRIENDS, 4, true));

        return config;
    }

    /**
     * Check if a slot is a valid crafting slot
     */
    private boolean isValidCraftingSlot(int slot) {
        for (int craftingSlot : CRAFTING_SLOTS) {
            if (craftingSlot == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a slot is a crafting slot (for use by other systems)
     */
    public boolean isCraftingSlot(int slot) {
        return isValidCraftingSlot(slot);
    }

    /**
     * FIXED: Check if a raw slot ID corresponds to a crafting slot in the open inventory
     */
    public boolean isCraftingSlotInView(int rawSlot, Player player) {
        if (!hasCraftingInventoryOpen(player)) {
            return false;
        }
        // For crafting inventory, raw slots 1-4 are the crafting grid
        return rawSlot >= 1 && rawSlot <= 4;
    }

    /**
     * Clean up offline players from tracking
     */
    private void cleanupOfflinePlayers() {
        playersWithMenuItems.removeIf(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            return player == null || !player.isOnline();
        });
    }

    /**
     * Check if a player has menu items loaded
     */
    public boolean hasMenuItems(Player player) {
        return player != null && playersWithMenuItems.contains(player.getUniqueId());
    }

    /**
     * FIXED: Force refresh menu items for a player
     */
    public void refreshMenuItems(Player player) {
        if (player != null && player.isOnline()) {
            // Schedule refresh after a brief delay to ensure inventory is ready
            new BukkitRunnable() {
                @Override
                public void run() {
                    setupMenuItems(player);
                }
            }.runTaskLater(YakRealms.getInstance(), 1L);
        }
    }

    /**
     * Add a new menu item type (for future extensibility)
     */
    public void registerCustomMenuItem(String id, Material material, String displayName, List<String> lore, MenuItemAction action) {
        // This can be implemented later for dynamic menu item registration
        logger.info("Custom menu item registration requested for: " + id);
    }

    /**
     * Interface for custom menu item actions
     */
    @FunctionalInterface
    public interface MenuItemAction {
        void execute(Player player);
    }

    /**
     * Get statistics about menu item usage
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("players_with_menu_items", playersWithMenuItems.size());
        stats.put("total_menu_item_types", MenuItemType.values().length);
        return stats;
    }

    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        // Clear menu items from all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearMenuItems(player);
        }

        playersWithMenuItems.clear();
        logger.info("MenuItemManager shutdown completed");
    }
}