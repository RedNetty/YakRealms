package com.rednetty.server.mechanics.economy.merchant;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.TransactionResult;
import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modern Merchant System for YakRealms
 * Provides a secure, aesthetically pleasing interface for trading items for gems
 */
public class MerchantManager implements Listener {

    private static MerchantManager instance;
    private final Logger logger;
    private final EconomyManager economyManager;

    // Active merchant sessions
    private final Map<UUID, MerchantSession> activeSessions = new ConcurrentHashMap<>();

    // Constants for GUI design
    private static final Component MERCHANT_TITLE_COMPONENT = Component.text("✦ ", NamedTextColor.DARK_GREEN)
            .append(Component.text("Merchant Trading Post", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.text(" ✦", NamedTextColor.DARK_GREEN));
    private static final String MERCHANT_TITLE = LegacyComponentSerializer.legacySection().serialize(MERCHANT_TITLE_COMPONENT);
    private static final int INVENTORY_SIZE = 54;
    private static final int[] TRADING_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int VALUE_DISPLAY_SLOT = 4;
    private static final int ACCEPT_SLOT = 48;
    private static final int CANCEL_SLOT = 50;

    // Update task for checking changes
    private BukkitRunnable updateTask;
    private final AtomicBoolean isEnabled = new AtomicBoolean(false);

    /**
     * Private constructor for singleton pattern
     */
    private MerchantManager() {
        this.logger = YakRealms.getInstance().getLogger();
        this.economyManager = EconomyManager.getInstance();
    }

    /**
     * Get the singleton instance
     */
    public static MerchantManager getInstance() {
        if (instance == null) {
            instance = new MerchantManager();
        }
        return instance;
    }

    /**
     * Initialize the merchant system
     */
    public void onEnable() {
        if (isEnabled.get()) {
            logger.warning("MerchantManager is already enabled!");
            return;
        }

        // Load configuration
        MerchantConfig.getInstance().loadConfig();
        MerchantConfig.getInstance().validateAndFixConfig();

        if (!MerchantConfig.getInstance().isEnabled()) {
            logger.info("Merchant system is disabled in configuration");
            return;
        }

        // Register events
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Start the update task for checking item changes
        startUpdateTask();

        isEnabled.set(true);
        logger.info("Merchant system has been enabled successfully!");
        logger.info("=== MERCHANT DEBUG INFO ===");
        logger.info("Max ore value: " + (int)(MerchantConfig.getInstance().getBaseOreValue() * 10) + " gems");
        logger.info("Max weapon/armor value: ~" + (int)(MerchantConfig.getInstance().getWeaponArmorBaseMultiplier() * 60) + " gems");
        logger.info("Orb value: " + MerchantConfig.getInstance().getOrbValue() + " gems");
        logger.info("===========================");

        if (MerchantConfig.getInstance().isDebugMode()) {
            logger.info(MerchantConfig.getInstance().getConfigSummary());
        }
    }

    /**
     * Clean up the merchant system
     */
    public void onDisable() {
        if (!isEnabled.get()) {
            return;
        }

        // Stop update task
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }

        // Close all active sessions and return items
        for (MerchantSession session : activeSessions.values()) {
            session.closeSession(true);
        }
        activeSessions.clear();

        isEnabled.set(false);
        logger.info("Merchant system has been disabled");
    }

    /**
     * Start the update task for checking item changes
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEnabled.get() || !MerchantConfig.getInstance().isEnabled()) {
                    cancel();
                    return;
                }

                // Update sessions that need updating
                for (MerchantSession session : activeSessions.values()) {
                    try {
                        session.checkForUpdates();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error checking session updates for player " + session.getPlayerUuid(), e);
                    }
                }
            }
        };

        int updateInterval = MerchantConfig.getInstance().getUpdateIntervalTicks();
        updateTask.runTaskTimer(YakRealms.getInstance(), 20L, updateInterval);
    }

    /**
     * Handle merchant NPC interactions
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMerchantInteract(PlayerInteractEntityEvent event) {
        if (!isEnabled.get() || event.isCancelled()) {
            return;
        }

        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        // Check if it's a merchant NPC
        if (!(entity instanceof HumanEntity) || !isMerchantNPC(entity)) {
            return;
        }

        event.setCancelled(true);

        // Check if player already has an active session
        if (activeSessions.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You already have a merchant session open!", NamedTextColor.RED));
            return;
        }

        // Open merchant interface
        openMerchantInterface(player);
    }

    /**
     * Check if an entity is a merchant NPC
     */
    private boolean isMerchantNPC(Entity entity) {
        if (!(entity instanceof HumanEntity)) return false;

        HumanEntity humanEntity = (HumanEntity) entity;

        // Check for NPC metadata and name
        return humanEntity.hasMetadata("NPC") &&
                humanEntity.getName() != null &&
                humanEntity.getName().equalsIgnoreCase("Merchant");
    }

    /**
     * Open the merchant trading interface
     */
    private void openMerchantInterface(Player player) {
        try {
            Inventory merchantInv = createMerchantInventory();
            MerchantSession session = new MerchantSession(player.getUniqueId(), merchantInv);

            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(merchantInv);
            player.playSound(Sound.sound(org.bukkit.Sound.BLOCK_CHEST_OPEN, Sound.Source.PLAYER, 1.0f, 1.0f));

            // Send welcome message
            TextUtil.sendCenteredMessage(player, Component.text("Merchant: ", NamedTextColor.WHITE)
                    .append(Component.text("Welcome to the Trading Post!", NamedTextColor.GRAY)));
            TextUtil.sendCenteredMessage(player, Component.text("Merchant: ", NamedTextColor.WHITE)
                    .append(Component.text("Place your items in the slots to see their value", NamedTextColor.GRAY)));

        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to open merchant interface. Please try again.", NamedTextColor.RED));
            logger.log(Level.SEVERE, "Error opening merchant interface for player " + player.getName(), e);
        }
    }

    /**
     * Create the merchant inventory with proper layout
     */
    private Inventory createMerchantInventory() {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, MERCHANT_TITLE_COMPONENT);

        // Fill border with decorative glass panes
        ItemStack borderPane = createDecorativeItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        ItemStack accentPane = createDecorativeItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" "));

        // Top and bottom borders
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, i == 4 ? null : borderPane); // Leave slot 4 for value display
            inv.setItem(45 + i, borderPane);
        }

        // Side borders
        for (int i = 9; i < 45; i += 9) {
            inv.setItem(i, accentPane);
            inv.setItem(i + 8, accentPane);
        }

        // Add control buttons
        inv.setItem(VALUE_DISPLAY_SLOT, createValueDisplayItem(0));
        inv.setItem(ACCEPT_SLOT, createAcceptButton());
        inv.setItem(CANCEL_SLOT, createCancelButton());

        // Add info item
        inv.setItem(49, createInfoItem());

        return inv;
    }

    /**
     * Create decorative glass pane items
     */
    private ItemStack createDecorativeItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the value display item
     */
    private ItemStack createValueDisplayItem(int value) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Total Value", NamedTextColor.GREEN, TextDecoration.BOLD));
        meta.lore(Arrays.asList(
                Component.text("Current Value: ", NamedTextColor.WHITE)
                        .append(Component.text(value + " gems", NamedTextColor.GREEN)),
                Component.empty(),
                Component.text("Place tradeable items in the", NamedTextColor.GRAY),
                Component.text("highlighted slots to see their value", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the accept button
     */
    private ItemStack createAcceptButton() {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✓ ACCEPT TRADE", NamedTextColor.GREEN, TextDecoration.BOLD));
        meta.lore(Arrays.asList(
                Component.text("Click to complete the trade", NamedTextColor.GRAY),
                Component.text("and receive your gems", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the cancel button
     */
    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✗ CANCEL TRADE", NamedTextColor.RED, TextDecoration.BOLD));
        meta.lore(Arrays.asList(
                Component.text("Click to cancel and", NamedTextColor.GRAY),
                Component.text("return all items", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the info item
     */
    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Trading Information", NamedTextColor.YELLOW, TextDecoration.BOLD));
        meta.lore(Arrays.asList(
                Component.text("• Only tradeable items can be sold", NamedTextColor.GRAY),
                Component.text("• Values are calculated automatically", NamedTextColor.GRAY),
                Component.text("• Higher tier items = more gems", NamedTextColor.GRAY),
                Component.text("• Rare items give bonus value", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Happy trading!", NamedTextColor.GREEN)
        ));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Handle inventory click events
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled.get() || !(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if this is a merchant interface
        if (!MERCHANT_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        MerchantSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();

        // Raw slots 0-53 are the merchant inventory (top), 54+ are player inventory (bottom)
        if (rawSlot >= INVENTORY_SIZE) {
            // Click in player's inventory - allow all operations
            // But handle shift-clicking to prevent items going to blocked slots
            if (event.isShiftClick()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                    if (!MerchantItemUtil.isTradeableItem(clickedItem)) {
                        // Don't allow shift-clicking untradeable items into merchant
                        event.setCancelled(true);
                        String reason = MerchantItemUtil.getUntradeableReason(clickedItem);
                        player.sendMessage(Component.text(reason != null ? reason : "This item cannot be traded!", NamedTextColor.RED));
                        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
                    } else {
                        // Allow shift-click but schedule update
                        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                            session.markForUpdate();
                        }, 1L);
                    }
                }
            }
            return;
        }

        // Click in merchant inventory (top) - handle specific slots
        int slot = event.getSlot();

        // Handle clicking in trading slots
        if (Arrays.stream(TRADING_SLOTS).anyMatch(i -> i == slot)) {
            handleTradingSlotClick(event, session);
            return;
        }

        // Handle control button clicks
        if (slot == ACCEPT_SLOT) {
            event.setCancelled(true);
            handleAcceptTrade(player, session);
        } else if (slot == CANCEL_SLOT) {
            event.setCancelled(true);
            handleCancelTrade(player, session);
        } else {
            // Prevent interaction with non-trading slots in merchant inventory
            event.setCancelled(true);
        }
    }

    /**
     * Handle clicks in trading slots
     */
    private void handleTradingSlotClick(InventoryClickEvent event, MerchantSession session) {
        Player player = (Player) event.getWhoClicked();
        ItemStack cursorItem = event.getCursor();

        if (MerchantConfig.getInstance().isDebugMode()) {
            logger.info("Trading slot click by " + player.getName() +
                    ", cursor item: " + (cursorItem != null ? cursorItem.getType() : "null") +
                    ", slot: " + event.getSlot() + ", click type: " + event.getClick());
        }

        // If placing an item, validate it
        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            if (!MerchantItemUtil.isTradeableItem(cursorItem)) {
                event.setCancelled(true);
                String reason = MerchantItemUtil.getUntradeableReason(cursorItem);
                player.sendMessage(Component.text(reason != null ? reason : "This item cannot be traded!", NamedTextColor.RED));
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
                return;
            }
        }

        // Schedule value update for next tick to allow the click to process first
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            session.markForUpdate();
            if (MerchantConfig.getInstance().isDebugMode()) {
                logger.info("Scheduled update for " + player.getName() + "'s merchant session");
            }
        }, 1L);
    }

    /**
     * Handle inventory drag events
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isEnabled.get() || !(event.getWhoClicked() instanceof Player)) {
            return;
        }

        if (!MERCHANT_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        MerchantSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        // Check if any dragged slots are trading slots in the TOP inventory (merchant inventory)
        boolean draggedToTradingSlot = false;
        for (int rawSlot : event.getRawSlots()) {
            // Only check slots in the top inventory (merchant inventory)
            if (rawSlot < INVENTORY_SIZE && Arrays.stream(TRADING_SLOTS).anyMatch(i -> i == rawSlot)) {
                draggedToTradingSlot = true;
                break;
            }
        }

        if (draggedToTradingSlot) {
            ItemStack draggedItem = event.getOldCursor();
            if (draggedItem != null && !MerchantItemUtil.isTradeableItem(draggedItem)) {
                event.setCancelled(true);
                String reason = MerchantItemUtil.getUntradeableReason(draggedItem);
                player.sendMessage(Component.text(reason != null ? reason : "This item cannot be traded!", NamedTextColor.RED));
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
                return;
            }

            // Schedule update
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                session.markForUpdate();
            }, 1L);
        }
    }

    /**
     * Handle accept trade button click
     */
    private void handleAcceptTrade(Player player, MerchantSession session) {
        try {
            List<ItemStack> tradingItems = session.getTradingItems();

            if (tradingItems.isEmpty()) {
                player.sendMessage(Component.text("You must place items to trade first!", NamedTextColor.RED));
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
                return;
            }

            int totalValue = session.calculateTotalValue();

            if (totalValue <= 0) {
                player.sendMessage(Component.text("The items you've placed have no trade value!", NamedTextColor.RED));
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
                return;
            }

            // Apply bonus multipliers from configuration
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            double multiplier = 1.0;
            String bonusType = "";

            if (yakPlayer != null) {
                if (ModerationMechanics.getInstance().isStaff(player)) {
                    multiplier = MerchantConfig.getInstance().getStaffMultiplier();
                    bonusType = "staff";
                } else if (ModerationMechanics.getInstance().isDonator(player)) {
                    multiplier = MerchantConfig.getInstance().getDonorMultiplier();
                    bonusType = "donor";
                }
            }

            int finalValue = (int) (totalValue * multiplier);

            if (MerchantConfig.getInstance().isDebugMode()) {
                logger.info("Player " + player.getName() + " trading items worth " + totalValue +
                        " gems (multiplier: " + multiplier + ", final: " + finalValue + ")");
            }

            // Execute the trade
            TransactionResult result = economyManager.addBankGems(player, finalValue);

            if (result.isSuccess()) {
                // Clear trading items from inventory
                session.clearTradingItems();

                // Success feedback
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, 1.0f, 1.2f));
                player.sendMessage(Component.text("Trade completed successfully. Gems deposited to bank.", NamedTextColor.GREEN));

                Component bonusMessage = multiplier > 1.0 ?
                        Component.text(" (+" + (finalValue - totalValue) + " " + bonusType + " bonus gems)", NamedTextColor.GRAY) :
                        Component.empty();

                Component tradeMessage = Component.text("✦ Received ", NamedTextColor.GREEN)
                        .append(Component.text(finalValue, NamedTextColor.GOLD))
                        .append(Component.text(" gems", NamedTextColor.GREEN))
                        .append(bonusMessage)
                        .append(Component.text(" ✦", NamedTextColor.GREEN));

                TextUtil.sendCenteredMessage(player, tradeMessage);

                // Close session
                session.closeSession(false);
                activeSessions.remove(player.getUniqueId());
                player.closeInventory();

            } else {
                player.sendMessage(Component.text("Trade failed: " + result.getMessage(), NamedTextColor.RED));
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));

                if (MerchantConfig.getInstance().isDebugMode()) {
                    logger.warning("Trade failed for player " + player.getName() + ": " + result.getMessage());
                }
            }

        } catch (Exception e) {
            player.sendMessage(Component.text("An error occurred while processing your trade!", NamedTextColor.RED));
            logger.log(Level.SEVERE, "Error processing trade for player " + player.getName(), e);
        }
    }

    /**
     * Handle cancel trade button click
     */
    private void handleCancelTrade(Player player, MerchantSession session) {
        session.closeSession(true);
        activeSessions.remove(player.getUniqueId());
        player.closeInventory();

        player.sendMessage(Component.text("Trade cancelled. All items have been returned.", NamedTextColor.YELLOW));
        player.playSound(Sound.sound(org.bukkit.Sound.BLOCK_CHEST_CLOSE, Sound.Source.PLAYER, 1.0f, 1.0f));
    }

    /**
     * Handle inventory close events
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isEnabled.get() || !(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        if (!MERCHANT_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        MerchantSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            // Return items to player when they close the inventory
            session.closeSession(true);
        }
    }

    /**
     * Handle player quit events
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MerchantSession session = activeSessions.remove(player.getUniqueId());

        if (session != null) {
            session.closeSession(true);
        }
    }

    /**
     * Inner class representing a merchant trading session
     */
    private class MerchantSession {
        private final UUID playerUuid;
        protected final Inventory inventory; // Made protected so outer class can access
        private int lastCalculatedValue = 0;
        private String lastItemsHash = "";
        private boolean needsUpdate = false;

        public MerchantSession(UUID playerUuid, Inventory inventory) {
            this.playerUuid = playerUuid;
            this.inventory = inventory;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public List<ItemStack> getTradingItems() {
            List<ItemStack> items = new ArrayList<>();

            for (int slot : TRADING_SLOTS) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    items.add(item);
                }
            }

            return items;
        }

        public int calculateTotalValue() {
            int totalValue = 0;
            MerchantConfig config = MerchantConfig.getInstance();

            for (ItemStack item : getTradingItems()) {
                int itemValue = calculateItemValue(item);
                totalValue += itemValue;

                if (config.isDebugMode()) {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null) {
                        player.sendMessage(Component.text("Debug: " + item.getType() + " x" + item.getAmount() + " = " + itemValue + " gems", NamedTextColor.GRAY));
                    }
                }
            }

            return totalValue;
        }

        public void markForUpdate() {
            needsUpdate = true;
        }

        public void checkForUpdates() {
            if (!needsUpdate) return;

            String currentItemsHash = generateItemsHash();
            if (!currentItemsHash.equals(lastItemsHash)) {
                updateValueDisplay();
                lastItemsHash = currentItemsHash;
            }
            needsUpdate = false;
        }

        private String generateItemsHash() {
            StringBuilder hash = new StringBuilder();
            for (int slot : TRADING_SLOTS) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    hash.append(item.getType().name())
                            .append(item.getAmount())
                            .append(MerchantItemUtil.generateItemHash(item))
                            .append(";");
                }
            }
            return hash.toString();
        }

        public void updateValueDisplay() {
            try {
                int currentValue = calculateTotalValue();

                if (MerchantConfig.getInstance().isDebugMode()) {
                    logger.info("Updating value display for player " + playerUuid +
                            ": " + lastCalculatedValue + " -> " + currentValue);
                }

                lastCalculatedValue = currentValue;
                inventory.setItem(VALUE_DISPLAY_SLOT, createValueDisplayItem(currentValue));

                // Update viewers
                for (org.bukkit.entity.HumanEntity viewer : inventory.getViewers()) {
                    if (viewer instanceof Player) {
                        ((Player) viewer).updateInventory();
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error updating value display for merchant session", e);
            }
        }

        public void clearTradingItems() {
            for (int slot : TRADING_SLOTS) {
                inventory.setItem(slot, null);
            }
        }

        public void closeSession(boolean returnItems) {
            if (returnItems) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    returnItemsToPlayer(player);
                }
            }
        }

        private void returnItemsToPlayer(Player player) {
            List<ItemStack> items = getTradingItems();
            clearTradingItems();

            for (ItemStack item : items) {
                // Try to add to inventory, drop if full
                HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(item);
                for (ItemStack dropped : notAdded.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                }
            }

            if (!items.isEmpty()) {
                player.updateInventory();
            }
        }
    }

    /**
     * Calculate the value of an individual item
     */
    private int calculateItemValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        try {
            MerchantConfig config = MerchantConfig.getInstance();
            int baseValue = 0;
            int amount = item.getAmount();

            if (config.isDebugMode()) {
                logger.info("Calculating value for: " + item.getType() + " x" + amount);
            }

            // Orb of Alteration
            if (MerchantItemUtil.isOrbOfAlteration(item)) {
                int totalValue = config.getOrbValue() * amount;

                if (config.isDebugMode()) {
                    logger.info("Orb value calculated: " + amount + " Orb(s) of Alteration = " + totalValue + " gems");
                }

                return totalValue;
            }

            // Ores - Simple linear calculation
            if (MerchantItemUtil.isOre(item)) {
                int oreTier = config.getOreTier(item.getType().name());
                if (oreTier > 0) {
                    // New lower values: base value (3) * tier (1-10) = 3-30 per ore
                    baseValue = (int) (config.getBaseOreValue() * oreTier);

                    if (config.isDebugMode()) {
                        logger.info("Ore value calculated: " + item.getType() + " (tier " + oreTier +
                                ") = " + baseValue + " gems per item, total: " + (baseValue * amount));
                    }

                    return baseValue * amount;
                }
            }

            // Weapons and Armor - More reasonable linear calculation
            if (MerchantItemUtil.isWeapon(item) || MerchantItemUtil.isArmor(item)) {
                int tier = MerchantItemUtil.getItemTier(item);
                if (tier > 0) {
                    // New lower values: base multiplier (1.5) * tier = 15-90 for tier 10-60
                    baseValue = (int) (config.getWeaponArmorBaseMultiplier() * tier);

                    // Apply rarity bonus (much smaller impact)
                    MerchantItemUtil.ItemRarity rarity = MerchantItemUtil.getItemRarity(item);
                    double rarityBonus = (rarity.getValueMultiplier() - 1.0) * (1.0 - config.getRarityBonusReduction());
                    baseValue = (int) (baseValue * (1.0 + rarityBonus));

                    // Add small deterministic variation
                    double variation = getDeterministicVariation(item, config);
                    baseValue = (int) (baseValue * (1.0 + variation));

                    if (config.isDebugMode()) {
                        logger.info("Item value calculated: " + item.getType() + " (tier " + tier +
                                ", rarity " + rarity + ") = " + baseValue + " gems");
                    }

                    return Math.max(1, baseValue); // Ensure at least 1 gem
                }
            }

            if (config.isDebugMode() && baseValue == 0) {
                logger.info("Item " + item.getType() + " has no trade value (not recognized as ore/weapon/armor/orb)");
            }

            return baseValue;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating value for item: " + item.getType(), e);
            return 0;
        }
    }

    /**
     * Get a deterministic variation for an item based on its hash
     * This ensures that the same item always has the same "random" variation
     */
    private double getDeterministicVariation(ItemStack item, MerchantConfig config) {
        int hash = MerchantItemUtil.generateItemHash(item);
        double normalized = (hash % 1000) / 1000.0; // Normalize to 0-1

        double min = config.getRandomVariationMin();
        double max = config.getRandomVariationMax();

        return min + (normalized * (max - min));
    }
}