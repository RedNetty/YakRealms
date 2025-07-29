package com.rednetty.server.mechanics.item;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.mounts.type.MountType;
import com.rednetty.server.mechanics.player.settings.Toggles;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Complete  MenuItemManager with journal, mount integration and toggles functionality.
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
    private static final int[] CRAFTING_SLOTS = {1, 2, 3, 4};

    public enum MenuItemType {
        JOURNAL("character_journal", Material.WRITTEN_BOOK, "&a&lCharacter Journal",
                Arrays.asList("&7View your character stats", "&7and progression details", "", "&e&l➤ Click to open!")),

        ELYTRA_MOUNT("elytra_mount", Material.ELYTRA, "&3&lElytra Mount",
                Arrays.asList("&7Magical wings for soaring", "&7through the skies gracefully", "", "&e&l➤ Click to summon!")),

        SETTINGS("player_settings", Material.REDSTONE, "&c&lToggle Settings",
                Arrays.asList("&7Configure your gameplay", "&7preferences and toggles", "", "&e&l➤ Click to open!")),

        HORSE_MOUNT("horse_mount", Material.SADDLE, "&6&lHorse Mount",
                Arrays.asList("&7Summon your loyal horse", "&7companion for swift travel", "", "&e&l➤ Click to summon!"));

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

    public void initialize() {
        logger.info("MenuItemManager initialized successfully with journal and mount integration");
    }

    /**
     * Set up menu items for a player - core functionality matching CoinPouch pattern
     */
    public void setupMenuItems(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            if (!hasCraftingInventoryOpen(player)) {
                return;
            }

            Inventory craftingInventory = player.getOpenInventory().getTopInventory();
            List<MenuItemData> menuItems = getMenuItemConfiguration(player);

            for (MenuItemData itemData : menuItems) {
                if (itemData.isEnabled() && isValidCraftingSlot(itemData.getSlot())) {
                    ItemStack currentItem = craftingInventory.getItem(itemData.getSlot());

                    // Only place if slot is empty or already contains the same menu item
                    if (currentItem == null ||
                            (isMenuItem(currentItem) && getMenuItemType(currentItem) == itemData.getType())) {
                        ItemStack menuItem = createMenuItem(itemData.getType());
                        craftingInventory.setItem(itemData.getSlot(), menuItem);
                    }
                }
            }

            playersWithMenuItems.add(playerId);

        } catch (Exception e) {
            logger.warning("Error setting up menu items for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Clear menu items from player's crafting grid
     */
    public void clearMenuItems(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            if (!hasCraftingInventoryOpen(player)) {
                playersWithMenuItems.remove(playerId);
                return;
            }

            Inventory craftingInventory = player.getOpenInventory().getTopInventory();

            for (int slot : CRAFTING_SLOTS) {
                ItemStack item = craftingInventory.getItem(slot);
                if (isMenuItem(item)) {
                    craftingInventory.setItem(slot, null);
                }
            }

            playersWithMenuItems.remove(playerId);

        } catch (Exception e) {
            logger.warning("Error clearing menu items for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Remove player from tracking (called on quit/disconnect)
     */
    public void clearPlayerFromTracking(Player player) {
        if (player != null) {
            playersWithMenuItems.remove(player.getUniqueId());
        }
    }

    private boolean hasCraftingInventoryOpen(Player player) {
        try {
            return player.getOpenInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Handle menu item clicks
     */
    public boolean handleMenuItemClick(Player player, ItemStack clickedItem, InventoryClickEvent event) {
        if (!isMenuItem(clickedItem)) {
            return false;
        }

        event.setCancelled(true);
        MenuItemType itemType = getMenuItemType(clickedItem);
        if (itemType == null) {
            logger.warning("Unknown menu item type clicked by " + player.getName());
            return true;
        }

        return handleMenuItemAction(player, itemType);
    }

    private boolean handleMenuItemAction(Player player, MenuItemType itemType) {
        try {
            switch (itemType) {
                case JOURNAL:
                    openCharacterJournal(player);
                    return true;
                case ELYTRA_MOUNT:
                    handleElytraMount(player);
                    return true;
                case SETTINGS:
                    openToggleSettings(player);
                    return true;
                case HORSE_MOUNT:
                    handleHorseMount(player);
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
     * Handle elytra mount summoning
     */
    private void handleElytraMount(Player player) {
        try {
            // Get the mount manager
            com.rednetty.server.mechanics.player.mounts.MountManager mountManager =
                    com.rednetty.server.mechanics.player.mounts.MountManager.getInstance();

            // Check if player already has an active mount
            if (mountManager.hasActiveMount(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You already have an active mount! Dismount first.");
                return;
            }

            // Check damage cooldown
            if (mountManager.getElytraMount().isOnDamageCooldown(player)) {
                long timeLeft = mountManager.getElytraMount().getRemainingCooldownTime(player);
                player.sendMessage(ChatColor.RED + "Elytra mount is on cooldown for " +
                        (timeLeft / 1000) + " more seconds after taking damage.");
                return;
            }

            // Attempt to summon the elytra
            if (mountManager.summonMount(player, MountType.ELYTRA)) {
                logger.fine("Elytra mount summoned for " + player.getName());
                // Close the crafting inventory to prevent interference
                player.closeInventory();
            } else {
                player.sendMessage(ChatColor.RED + "Failed to summon elytra mount. Please try again.");
            }

        } catch (Exception e) {
            logger.warning("Error handling elytra mount for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Error summoning elytra mount. Please try again.");
        }
    }

    /**
     * Handle horse mount summoning
     */
    private void handleHorseMount(Player player) {
        try {
            // Get the mount manager
            com.rednetty.server.mechanics.player.mounts.MountManager mountManager =
                    com.rednetty.server.mechanics.player.mounts.MountManager.getInstance();

            // Check if player has a horse mount
            int horseTier = mountManager.getHorseTier(player);
            if (horseTier < 2) {
                player.sendMessage(ChatColor.RED + "═══════════════════════════════════");
                player.sendMessage(ChatColor.GOLD + "         Horse Mount Locked");
                player.sendMessage(ChatColor.RED + "═══════════════════════════════════");
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "You don't have a horse mount yet!");
                player.sendMessage(ChatColor.YELLOW + "Purchase one from the mount shop to");
                player.sendMessage(ChatColor.YELLOW + "unlock fast travel across the realm.");
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "═══════════════════════════════════");
                return;
            }

            // Check if player already has an active mount
            if (mountManager.hasActiveMount(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You already have an active mount! Dismount first.");
                return;
            }

            // Attempt to summon the horse
            if (mountManager.summonMount(player, MountType.HORSE)) {
                logger.fine("Horse mount summoned for " + player.getName());
                // Close the crafting inventory to prevent interference
                player.closeInventory();
            } else {
                player.sendMessage(ChatColor.RED + "Failed to summon horse mount. Please try again.");
            }

        } catch (Exception e) {
            logger.warning("Error handling horse mount for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Error summoning horse mount. Please try again.");
        }
    }

    /**
     * Open toggle settings menu
     */
    private void openToggleSettings(Player player) {
        try {
            // Get the toggles instance and open the menu
            Toggles toggles = Toggles.getInstance();

            if (toggles != null) {
                player.closeInventory();
                toggles.openToggleMenu(player);
                logger.fine("Opened toggle menu for " + player.getName());
            } else {
                player.sendMessage(ChatColor.RED + "Toggle system is not available. Please try again.");
                logger.warning("Toggles instance is null when trying to open menu for " + player.getName());
            }

        } catch (Exception e) {
            logger.warning("Error opening toggle settings for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Error opening toggle settings. Please try again.");
        }
    }

    /**
     * Create a menu item with proper NBT marking
     */
    private ItemStack createMenuItem(MenuItemType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));

            List<String> coloredLore = new ArrayList<>();
            for (String line : type.getLore()) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
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

    private List<MenuItemData> getMenuItemConfiguration(Player player) {
        List<MenuItemData> config = new ArrayList<>();
        // Slot 1: Character Journal (green book)
        config.add(new MenuItemData(MenuItemType.JOURNAL, 1, true));
        // Slot 2: Elytra Mount (replacing help guide)
        config.add(new MenuItemData(MenuItemType.ELYTRA_MOUNT, 2, true));
        // Slot 3: Toggle Settings (redstone, same position)
        config.add(new MenuItemData(MenuItemType.SETTINGS, 3, true));
        // Slot 4: Horse Mount (replacing friends/player head)
        config.add(new MenuItemData(MenuItemType.HORSE_MOUNT, 4, true));
        return config;
    }

    private boolean isValidCraftingSlot(int slot) {
        for (int craftingSlot : CRAFTING_SLOTS) {
            if (craftingSlot == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a slot is a crafting slot
     */
    public boolean isCraftingSlot(int slot) {
        return isValidCraftingSlot(slot);
    }

    /**
     * Check if a raw slot ID corresponds to a crafting slot in the current view
     */
    public boolean isCraftingSlotInView(int rawSlot, Player player) {
        if (!hasCraftingInventoryOpen(player)) {
            return false;
        }
        return rawSlot >= 1 && rawSlot <= 4;
    }

    public boolean hasMenuItems(Player player) {
        return player != null && playersWithMenuItems.contains(player.getUniqueId());
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("players_with_menu_items", playersWithMenuItems.size());
        stats.put("total_menu_item_types", MenuItemType.values().length);
        return stats;
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearMenuItems(player);
        }
        playersWithMenuItems.clear();
        logger.info("MenuItemManager shutdown completed");
    }
}