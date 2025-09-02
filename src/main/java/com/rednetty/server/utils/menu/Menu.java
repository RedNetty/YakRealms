package com.rednetty.server.utils.menu;

import com.rednetty.server.YakRealms;
import com.rednetty.server.utils.messaging.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 *  base class for creating interactive menus/GUIs with improved performance and visual effects
 */
public abstract class Menu {
    private static final Logger LOGGER = Logger.getLogger(Menu.class.getName());

    //  registry with thread safety
    private static final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();
    private static final MenuEventHandler eventHandler = new MenuEventHandler();
    private static boolean initialized = false;

    // Menu properties
    protected final Player player;
    protected final Inventory inventory;
    protected final Map<Integer, MenuItem> items = new HashMap<>();
    protected final String originalTitle;

    //  features
    private BukkitTask refreshTask;
    private boolean autoRefresh = false;
    private int refreshInterval = 20; // 1 second default
    private boolean playClickSounds = true;
    private Sound clickSound = Sound.UI_BUTTON_CLICK;
    private final Set<Integer> protectedSlots = new HashSet<>();
    private final Map<String, Object> menuData = new HashMap<>();

    // Animation support
    private final Map<Integer, AnimatedMenuItem> animatedItems = new HashMap<>();
    private BukkitTask animationTask;

    /**
     *  menu constructor with validation and improved initialization
     *
     * @param player The player who will see the menu
     * @param title  The title of the inventory
     * @param size   The size of the inventory (must be a multiple of 9)
     */
    public Menu(Player player, String title, int size) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        this.player = player;
        this.originalTitle = title != null ? title : "Menu";

        // Validate and normalize size
        size = validateAndNormalizeSize(size);

        // Create inventory with proper Adventure API support - parse MiniMessage directly
        Component titleComponent;
        if (this.originalTitle.contains("<") && this.originalTitle.contains(">")) {
            // Already using MiniMessage format
            titleComponent = MessageUtils.parse(this.originalTitle);
        } else {
            // Legacy format with & codes
            titleComponent = MessageUtils.fromLegacy(ChatColor.translateAlternateColorCodes('&', this.originalTitle));
        }
        this.inventory = Bukkit.createInventory(null, size, titleComponent);

        // Initialize event handlers if needed
        initializeEventHandlers();
    }

    /**
     * Validates and normalizes inventory size
     */
    private int validateAndNormalizeSize(int size) {
        if (size <= 0) {
            LOGGER.warning("Invalid menu size " + size + ", defaulting to 9");
            return 9;
        }

        // Round up to nearest multiple of 9, max 54
        int normalizedSize = ((size + 8) / 9) * 9;
        normalizedSize = Math.min(54, normalizedSize);

        if (normalizedSize != size) {
            LOGGER.fine("Menu size adjusted from " + size + " to " + normalizedSize);
        }

        return normalizedSize;
    }

    /**
     *  event handler initialization with better thread safety
     */
    private static synchronized void initializeEventHandlers() {
        if (!initialized) {
            Bukkit.getPluginManager().registerEvents(eventHandler, YakRealms.getInstance());
            initialized = true;
            LOGGER.info("Menu event handlers initialized");
        }
    }

    /**
     * Opens the menu with  features
     */
    public void open() {
        try {
            // Close any existing menu for this player
            Menu existingMenu = openMenus.get(player.getUniqueId());
            if (existingMenu != null && existingMenu != this) {
                existingMenu.close();
            }

            // Update inventory and register menu
            updateInventory();
            openMenus.put(player.getUniqueId(), this);

            // Call pre-open hook
            onPreOpen();

            // Open inventory
            player.openInventory(inventory);

            // Start auto-refresh if enabled
            startAutoRefresh();

            // Start animations if any
            startAnimations();

            // Call post-open hook
            onPostOpen();

            LOGGER.fine("Opened menu '" + originalTitle + "' for player " + player.getName());

        } catch (Exception e) {
            LOGGER.severe("Failed to open menu for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Closes the menu with cleanup
     */
    public void close() {
        try {
            // Call pre-close hook
            onPreClose();

            // Stop tasks
            stopAutoRefresh();
            stopAnimations();

            // Close inventory
            player.closeInventory();

            // Call post-close hook
            onPostClose();

        } catch (Exception e) {
            LOGGER.warning("Error closing menu for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     *  item setting with validation
     *
     * @param slot The slot to set the item in
     * @param item The menu item to set
     */
    public void setItem(int slot, MenuItem item) {
        if (!isValidSlot(slot)) {
            LOGGER.warning("Invalid slot " + slot + " for menu size " + inventory.getSize());
            return;
        }

        if (item == null) {
            removeItem(slot);
            return;
        }

        items.put(slot, item);
        inventory.setItem(slot, item.toItemStack());

        // Handle animated items
        if (item instanceof AnimatedMenuItem) {
            animatedItems.put(slot, (AnimatedMenuItem) item);
        }
    }

    /**
     * Sets a raw ItemStack with automatic MenuItem wrapper
     *
     * @param slot The slot to set the item in
     * @param item The ItemStack to set
     */
    public void setItem(int slot, ItemStack item) {
        if (item == null) {
            removeItem(slot);
            return;
        }

        MenuItem menuItem = new MenuItem(item);
        setItem(slot, menuItem);
    }

    /**
     *  item removal with cleanup
     *
     * @param slot The slot to remove the item from
     */
    public void removeItem(int slot) {
        if (!isValidSlot(slot)) {
            return;
        }

        items.remove(slot);
        animatedItems.remove(slot);
        inventory.setItem(slot, null);
    }

    /**
     *  border creation with customizable materials and patterns
     *
     * @param material The material for the border
     * @param name     The display name for border items
     */
    public void createBorder(Material material, String name) {
        ItemStack borderItem = new ItemStack(material);
        MenuItem borderMenuItem = new MenuItem(borderItem).setDisplayName(name != null ? name : " ");

        int size = inventory.getSize();
        int rows = size / 9;

        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            setItem(i, borderMenuItem);
            if (rows > 1) {
                setItem(size - 9 + i, borderMenuItem);
            }
        }

        // Side columns
        for (int row = 1; row < rows - 1; row++) {
            setItem(row * 9, borderMenuItem);
            setItem(row * 9 + 8, borderMenuItem);
        }
    }

    /**
     * Creates a standard border with glass panes
     */
    public void createBorder() {
        createBorder(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + " ");
    }

    /**
     * Fills empty slots with a filler item
     *
     * @param material The material for the filler
     * @param name     The display name for filler items
     */
    public void fillEmpty(Material material, String name) {
        ItemStack fillerItem = new ItemStack(material);
        MenuItem fillerMenuItem = new MenuItem(fillerItem).setDisplayName(name != null ? name : " ");

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                setItem(slot, fillerMenuItem);
            }
        }
    }

    /**
     *  slot validation
     */
    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < inventory.getSize();
    }

    /**
     * Auto-refresh functionality
     */
    public void setAutoRefresh(boolean autoRefresh, int intervalTicks) {
        this.autoRefresh = autoRefresh;
        this.refreshInterval = Math.max(1, intervalTicks);

        if (isOpen()) {
            if (autoRefresh) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        }
    }

    /**
     * Starts auto-refresh task
     */
    private void startAutoRefresh() {
        if (!autoRefresh) return;

        stopAutoRefresh(); // Stop existing task

        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isOpen()) {
                    cancel();
                    return;
                }

                try {
                    onRefresh();
                    updateInventory();
                } catch (Exception e) {
                    LOGGER.warning("Error during menu refresh: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), refreshInterval, refreshInterval);
    }

    /**
     * Stops auto-refresh task
     */
    private void stopAutoRefresh() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /**
     * Animation support
     */
    private void startAnimations() {
        if (animatedItems.isEmpty()) return;

        stopAnimations(); // Stop existing animations

        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isOpen() || animatedItems.isEmpty()) {
                    cancel();
                    return;
                }

                for (Map.Entry<Integer, AnimatedMenuItem> entry : animatedItems.entrySet()) {
                    AnimatedMenuItem animItem = entry.getValue();
                    if (animItem.shouldUpdate()) {
                        animItem.update();
                        inventory.setItem(entry.getKey(), animItem.toItemStack());
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 5L, 5L); // Update every 5 ticks
    }

    /**
     * Stops animation task
     */
    private void stopAnimations() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    /**
     *  inventory update with error handling
     */
    protected void updateInventory() {
        try {
            for (Map.Entry<Integer, MenuItem> entry : items.entrySet()) {
                int slot = entry.getKey();
                if (isValidSlot(slot)) {
                    inventory.setItem(slot, entry.getValue().toItemStack());
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error updating inventory: " + e.getMessage());
        }
    }

    /**
     *  click handling with sound effects and validation
     *
     * @param slot The slot that was clicked
     */
    protected void handleClick(int slot) {
        if (!isValidSlot(slot)) {
            return;
        }

        // Check protected slots
        if (protectedSlots.contains(slot)) {
            playSound(Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }

        MenuItem item = items.get(slot);
        if (item != null) {
            // Play click sound
            if (playClickSounds) {
                playSound(clickSound, 0.5f, 1.0f);
            }

            // Execute click handler
            if (item.getClickHandler() != null) {
                try {
                    item.getClickHandler().onClick(player, slot);
                } catch (Exception e) {
                    LOGGER.warning("Error executing click handler for slot " + slot + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     *  sound playing
     */
    private void playSound(Sound sound, float volume, float pitch) {
        if (player.isOnline()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /**
     * Menu state checking
     */
    public boolean isOpen() {
        return openMenus.get(player.getUniqueId()) == this &&
                player.isOnline() &&
                player.getOpenInventory().getTopInventory().equals(inventory);
    }

    /**
     * Protected slot management
     */
    public void protectSlot(int slot) {
        protectedSlots.add(slot);
    }

    public void unprotectSlot(int slot) {
        protectedSlots.remove(slot);
    }

    /**
     * Menu data storage for sharing data between methods
     */
    public void setData(String key, Object value) {
        menuData.put(key, value);
    }

    public Object getData(String key) {
        return menuData.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = menuData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // Getter methods
    public Player getPlayer() { return player; }
    public Inventory getInventory() { return inventory; }
    public String getTitle() { return originalTitle; }
    public MenuItem getItem(int slot) { return items.get(slot); }
    public int getSize() { return inventory.getSize(); }

    // Configuration methods
    public void setClickSoundsEnabled(boolean enabled) { this.playClickSounds = enabled; }
    public void setClickSound(Sound sound) { this.clickSound = sound; }

    // Abstract/Override methods for subclasses
    protected void onPreOpen() {}
    protected void onPostOpen() {}
    protected void onPreClose() {}
    protected void onPostClose() {}
    protected void onRefresh() {}

    /**
     * Event handler for inventory close
     */
    private void onClose() {
        openMenus.remove(player.getUniqueId());
        stopAutoRefresh();
        stopAnimations();
    }

    // Static utility methods
    public static Menu getOpenMenu(Player player) {
        return openMenus.get(player.getUniqueId());
    }

    public static void closeAllMenus() {
        new ArrayList<>(openMenus.values()).forEach(Menu::close);
        openMenus.clear();
    }

    public static int getOpenMenuCount() {
        return openMenus.size();
    }

    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("openMenus", openMenus.size());
        stats.put("initialized", initialized);
        stats.put("registeredPlayers", openMenus.keySet().size());
        return stats;
    }

    /**
     *  event listener with improved error handling
     */
    private static class MenuEventHandler implements Listener {

        @EventHandler(priority = EventPriority.NORMAL)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            Menu menu = openMenus.get(player.getUniqueId());

            if (menu != null && event.getView().getTopInventory().equals(menu.inventory)) {
                event.setCancelled(true);

                // Only handle clicks in the menu inventory
                if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                    try {
                        menu.handleClick(event.getRawSlot());
                    } catch (Exception e) {
                        LOGGER.warning("Error handling menu click: " + e.getMessage());
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            Menu menu = openMenus.get(player.getUniqueId());

            if (menu != null && event.getView().getTopInventory().equals(menu.inventory)) {
                // Cancel if any slots are in the menu inventory
                for (int slot : event.getRawSlots()) {
                    if (slot < event.getView().getTopInventory().getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getPlayer();
            Menu menu = openMenus.get(player.getUniqueId());

            if (menu != null && event.getView().getTopInventory().equals(menu.inventory)) {
                try {
                    menu.onClose();
                } catch (Exception e) {
                    LOGGER.warning("Error during menu close: " + e.getMessage());
                }
            }
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onPlayerQuit(PlayerQuitEvent event) {
            // Clean up any open menus when player quits
            Menu menu = openMenus.remove(event.getPlayer().getUniqueId());
            if (menu != null) {
                try {
                    menu.stopAutoRefresh();
                    menu.stopAnimations();
                } catch (Exception e) {
                    LOGGER.warning("Error cleaning up menu on player quit: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Animated menu item support
     */
    public static abstract class AnimatedMenuItem extends MenuItem {
        private long lastUpdate = 0;
        private final long updateInterval;

        public AnimatedMenuItem(Material material, long updateIntervalTicks) {
            super(material);
            this.updateInterval = updateIntervalTicks * 50; // Convert to milliseconds
        }

        public boolean shouldUpdate() {
            return System.currentTimeMillis() - lastUpdate >= updateInterval;
        }

        public void update() {
            lastUpdate = System.currentTimeMillis();
            onUpdate();
        }

        protected abstract void onUpdate();
    }
}