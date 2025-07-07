package com.rednetty.server.mechanics.economy.merchant;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.TransactionResult;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
    private static final String MERCHANT_TITLE = ChatColor.DARK_GREEN + "✦ " + ChatColor.GREEN + ChatColor.BOLD + "Merchant Trading Post" + ChatColor.DARK_GREEN + " ✦";
    private static final int INVENTORY_SIZE = 54;
    private static final int[] TRADING_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int VALUE_DISPLAY_SLOT = 4;
    private static final int ACCEPT_SLOT = 48;
    private static final int CANCEL_SLOT = 50;

    // Update task for real-time value updates
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

        // Start the update task for real-time value calculations
        startUpdateTask();

        isEnabled.set(true);
        logger.info("Merchant system has been enabled");

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
     * Start the real-time update task
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEnabled.get() || !MerchantConfig.getInstance().isEnabled()) {
                    cancel();
                    return;
                }

                // Update all active merchant sessions
                for (MerchantSession session : activeSessions.values()) {
                    try {
                        session.updateValueDisplay();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error updating merchant session for player " + session.getPlayerUuid(), e);
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
            player.sendMessage(ChatColor.RED + "You already have a merchant session open!");
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
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

            // Send welcome message
            TextUtil.sendCenteredMessage(player, ChatColor.WHITE + "Merchant: " + ChatColor.GRAY + "Welcome to the Trading Post!");
            TextUtil.sendCenteredMessage(player, ChatColor.WHITE + "Merchant: " + ChatColor.GRAY + "Place your items in the slots to see their value");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to open merchant interface. Please try again.");
            logger.log(Level.SEVERE, "Error opening merchant interface for player " + player.getName(), e);
        }
    }

    /**
     * Create the merchant inventory with proper layout
     */
    private Inventory createMerchantInventory() {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, MERCHANT_TITLE);

        // Fill border with decorative glass panes
        ItemStack borderPane = createDecorativeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack accentPane = createDecorativeItem(Material.GREEN_STAINED_GLASS_PANE, " ");

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
    private ItemStack createDecorativeItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
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
        meta.setDisplayName(ChatColor.GREEN + ChatColor.BOLD.toString() + "Total Value");
        meta.setLore(Arrays.asList(
                ChatColor.WHITE + "Current Value: " + ChatColor.GREEN + value + " gems",
                "",
                ChatColor.GRAY + "Place tradeable items in the",
                ChatColor.GRAY + "highlighted slots to see their value"
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
        meta.setDisplayName(ChatColor.GREEN + ChatColor.BOLD.toString() + "✓ ACCEPT TRADE");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click to complete the trade",
                ChatColor.GRAY + "and receive your gems"
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
        meta.setDisplayName(ChatColor.RED + ChatColor.BOLD.toString() + "✗ CANCEL TRADE");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click to cancel and",
                ChatColor.GRAY + "return all items"
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
        meta.setDisplayName(ChatColor.YELLOW + ChatColor.BOLD.toString() + "Trading Information");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "• Only tradeable items can be sold",
                ChatColor.GRAY + "• Values are calculated automatically",
                ChatColor.GRAY + "• Higher tier items = more gems",
                ChatColor.GRAY + "• Rare items give bonus value",
                "",
                ChatColor.GREEN + "Happy trading!"
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
        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null || !MERCHANT_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        MerchantSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

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
            // Prevent interaction with non-trading slots
            event.setCancelled(true);
        }
    }

    /**
     * Handle clicks in trading slots
     */
    private void handleTradingSlotClick(InventoryClickEvent event, MerchantSession session) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Allow normal inventory operations for trading slots
        // But validate items being placed
        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            if (!isTradeableItem(cursorItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "This item cannot be traded with the merchant!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        // Schedule value update for next tick
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), session::updateValueDisplay, 1L);
    }

    /**
     * Handle accept trade button click
     */
    private void handleAcceptTrade(Player player, MerchantSession session) {
        try {
            List<ItemStack> tradingItems = session.getTradingItems();

            if (tradingItems.isEmpty()) {
                player.sendMessage(ChatColor.RED + "You must place items to trade first!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            int totalValue = session.calculateTotalValue();

            if (totalValue <= 0) {
                player.sendMessage(ChatColor.RED + "The items you've placed have no trade value!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Apply bonus multipliers from configuration
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            double multiplier = 1.0;
            String bonusType = "";

            if (yakPlayer != null) {
                if (ModerationMechanics.isStaff(player)) {
                    multiplier = MerchantConfig.getInstance().getStaffMultiplier();
                    bonusType = "staff";
                } else if (ModerationMechanics.isDonator(player)) {
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
            TransactionResult result = economyManager.depositGems(player, finalValue);

            if (result.isSuccess()) {
                // Clear trading items from inventory
                session.clearTradingItems();

                // Success feedback
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                player.sendMessage(ChatColor.GREEN + "Trade completed successfully!");

                String bonusMessage = multiplier > 1.0 ?
                        ChatColor.GRAY + " (+" + (finalValue - totalValue) + " " + bonusType + " bonus gems)" : "";

                TextUtil.sendCenteredMessage(player,
                        ChatColor.GREEN + "✦ Received " + ChatColor.GOLD + finalValue +
                                ChatColor.GREEN + " gems" + bonusMessage + ChatColor.GREEN + " ✦");

                // Close session
                session.closeSession(false);
                activeSessions.remove(player.getUniqueId());
                player.closeInventory();

            } else {
                player.sendMessage(ChatColor.RED + "Trade failed: " + result.getMessage());
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

                if (MerchantConfig.getInstance().isDebugMode()) {
                    logger.warning("Trade failed for player " + player.getName() + ": " + result.getMessage());
                }
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "An error occurred while processing your trade!");
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

        player.sendMessage(ChatColor.YELLOW + "Trade cancelled. All items have been returned.");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
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
     * Check if an item is tradeable
     */
    private boolean isTradeableItem(ItemStack item) {
        return MerchantItemUtil.isTradeableItem(item);
    }

    /**
     * Inner class representing a merchant trading session
     */
    private class MerchantSession {
        private final UUID playerUuid;
        private final Inventory inventory;
        private int lastCalculatedValue = 0;

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

            for (ItemStack item : getTradingItems()) {
                totalValue += calculateItemValue(item);
            }

            return totalValue;
        }

        public void updateValueDisplay() {
            try {
                int currentValue = calculateTotalValue();

                // Only update if value changed to reduce unnecessary operations
                if (currentValue != lastCalculatedValue) {
                    lastCalculatedValue = currentValue;
                    inventory.setItem(VALUE_DISPLAY_SLOT, createValueDisplayItem(currentValue));

                    // Update viewers
                    for (org.bukkit.entity.HumanEntity viewer : inventory.getViewers()) {
                        if (viewer instanceof Player) {
                            ((Player) viewer).updateInventory();
                        }
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
            int baseValue = 0;
            int amount = item.getAmount();

            // Orb of Alteration
            if (item.getType() == Material.MAGMA_CREAM && item.hasItemMeta() &&
                    item.getItemMeta().hasDisplayName() &&
                    ChatColor.stripColor(item.getItemMeta().getDisplayName()).equalsIgnoreCase("Orb of Alteration")) {
                return 500 * amount;
            }

            // Ores
            if (item.getType().name().contains("_ORE") && !item.getType().name().contains("PICKAXE")) {
                int oreTier = getOreTier(item.getType());
                if (oreTier > 0) {
                    baseValue = 20;
                    return (int) ((baseValue * amount * oreTier) * 1.23);
                }
            }

            // Weapons and Armor
            if (isWeaponOrArmor(item)) {
                int tier = getItemTier(item);
                if (tier > 0) {
                    baseValue = (int) (((tier / 10.0) + 1.0) * (tier * tier) * 12.0);

                    // Apply rarity bonus
                    double rarityMultiplier = getRarityMultiplier(item);
                    baseValue = (int) (baseValue * rarityMultiplier);

                    // Add some randomization (10-30% variation)
                    Random random = new Random();
                    double variation = 0.1 + (random.nextDouble() * 0.2); // 10-30%
                    baseValue = (int) (baseValue * (1.0 + variation));

                    return baseValue;
                }
            }

            return baseValue;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating value for item: " + item.getType(), e);
            return 0;
        }
    }

    /**
     * Get the tier of an ore
     */
    private int getOreTier(Material material) {
        switch (material) {
            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
                return 1;
            case COPPER_ORE:
            case DEEPSLATE_COPPER_ORE:
                return 2;
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                return 3;
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                return 4;
            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                return 5;
            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
                return 6;
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                return 7;
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                return 8;
            case ANCIENT_DEBRIS:
                return 10;
            default:
                return 0;
        }
    }

    /**
     * Check if an item is a weapon or armor piece
     */
    private boolean isWeaponOrArmor(ItemStack item) {
        String typeName = item.getType().name();

        return typeName.contains("SWORD") || typeName.contains("AXE") || typeName.contains("BOW") ||
                typeName.contains("HELMET") || typeName.contains("CHESTPLATE") ||
                typeName.contains("LEGGINGS") || typeName.contains("BOOTS") ||
                typeName.contains("TRIDENT") || typeName.contains("CROSSBOW");
    }

    /**
     * Get the tier of an item based on its material or color coding
     */
    private int getItemTier(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return getMaterialTier(item.getType());
        }

        String displayName = item.getItemMeta().getDisplayName();

        // Check color codes for tier determination
        if (displayName.contains(ChatColor.WHITE.toString())) return 10;
        if (displayName.contains(ChatColor.GREEN.toString())) return 20;
        if (displayName.contains(ChatColor.BLUE.toString())) return 30;
        if (displayName.contains(ChatColor.LIGHT_PURPLE.toString())) return 40;
        if (displayName.contains(ChatColor.YELLOW.toString())) return 50;
        if (displayName.contains(ChatColor.RED.toString())) return 60;

        return getMaterialTier(item.getType());
    }

    /**
     * Get base tier from material type
     */
    private int getMaterialTier(Material material) {
        String name = material.name();

        if (name.contains("WOOD") || name.contains("LEATHER")) return 10;
        if (name.contains("STONE")) return 15;
        if (name.contains("IRON") || name.contains("CHAINMAIL")) return 25;
        if (name.contains("GOLD")) return 20;
        if (name.contains("DIAMOND")) return 35;
        if (name.contains("NETHERITE")) return 50;

        return 10; // Default tier
    }

    /**
     * Get rarity multiplier based on item lore
     */
    private double getRarityMultiplier(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 1.0;
        }

        List<String> lore = item.getItemMeta().getLore();
        String lastLine = lore.get(lore.size() - 1);
        String cleanLine = ChatColor.stripColor(lastLine).toLowerCase();

        if (cleanLine.contains("common")) return 1.0;
        if (cleanLine.contains("uncommon")) return 1.25;
        if (cleanLine.contains("rare")) return 1.5;
        if (cleanLine.contains("epic")) return 2.0;
        if (cleanLine.contains("legendary")) return 3.0;
        if (cleanLine.contains("mythic")) return 5.0;

        return 1.0;
    }
}