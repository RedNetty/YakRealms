package com.rednetty.server.mechanics.economy.vendors.purchase;

import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Enhanced PurchaseManager with improved transaction handling, validation, and error recovery.
 * Manages the complete purchase process for vendor items with comprehensive error handling,
 * transaction timeout management, and performance tracking.
 */
public class PurchaseManager implements Listener {

    private static volatile PurchaseManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Core components
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;

    // Transaction state tracking (thread-safe)
    private final Map<UUID, PurchaseTransaction> activeTransactions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractionTime = new ConcurrentHashMap<>();

    // Performance and analytics
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong successfulTransactions = new AtomicLong(0);
    private final AtomicLong failedTransactions = new AtomicLong(0);
    private final AtomicLong cancelledTransactions = new AtomicLong(0);

    // Configuration
    private static final long TRANSACTION_TIMEOUT_MS = 60000; // 1 minute
    private static final long MESSAGE_COOLDOWN_MS = 500; // 500ms between messages
    private static final int MAX_QUANTITY = 64;
    private static final int MAX_CONCURRENT_TRANSACTIONS = 100;

    /**
     * Enhanced constructor with validation
     */
    private PurchaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = EconomyManager.getInstance();

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start cleanup task for expired transactions
        startMaintenanceTask();

        plugin.getLogger().info("Enhanced PurchaseManager initialized with transaction management");
    }

    /**
     * Thread-safe singleton getter
     */
    public static PurchaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PurchaseManager not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Initialize the PurchaseManager
     */
    public static PurchaseManager initialize(JavaPlugin plugin) {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new PurchaseManager(plugin);
                }
            }
        }
        return instance;
    }

    /**
     * Start maintenance task for cleanup and monitoring
     */
    private void startMaintenanceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    cleanupExpiredTransactions();
                    cleanupOldInteractionTimes();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during PurchaseManager maintenance", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L); // Every minute
    }

    /**
     * Enhanced purchase initiation with comprehensive validation
     */
    public void startPurchase(Player player, ItemStack item, int price) {
        UUID playerId = player.getUniqueId();

        try {
            // Validate input parameters
            if (!validatePurchaseInputs(player, item, price)) {
                return;
            }

            // Check if player already has an active transaction
            if (activeTransactions.containsKey(playerId)) {
                player.sendMessage(ChatColor.RED + "You already have an active purchase. Type 'cancel' to cancel it.");
                return;
            }

            // Check system load
            if (activeTransactions.size() >= MAX_CONCURRENT_TRANSACTIONS) {
                player.sendMessage(ChatColor.RED + "The vendor system is currently busy. Please try again in a moment.");
                return;
            }

            // Clean item for purchase (remove price from lore)
            ItemStack purchaseItem = VendorUtils.removePriceFromItem(item);
            if (purchaseItem == null) {
                player.sendMessage(ChatColor.RED + "Invalid item. Please try again.");
                return;
            }

            // Create transaction
            PurchaseTransaction transaction = new PurchaseTransaction(
                    playerId,
                    purchaseItem,
                    price,
                    System.currentTimeMillis()
            );

            activeTransactions.put(playerId, transaction);
            totalTransactions.incrementAndGet();

            // Send purchase prompts with enhanced formatting
            sendPurchasePrompts(player, purchaseItem, price);

            // Schedule timeout task
            scheduleTransactionTimeout(playerId);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error starting purchase for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred while starting your purchase. Please try again.");
            cleanupTransaction(playerId);
        }
    }

    /**
     * Enhanced input validation
     */
    private boolean validatePurchaseInputs(Player player, ItemStack item, int price) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Invalid item for purchase.");
            return false;
        }

        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "Invalid price for this item.");
            return false;
        }

        if (price > 1000000) { // Reasonable upper limit
            player.sendMessage(ChatColor.RED + "This item is too expensive to purchase.");
            return false;
        }

        return true;
    }

    /**
     * Enhanced purchase prompts with better formatting
     */
    private void sendPurchasePrompts(Player player, ItemStack item, int price) {
        String itemName = getItemDisplayName(item);
        long maxCost = (long) price * MAX_QUANTITY;

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬ " + ChatColor.YELLOW + "PURCHASE CONFIRMATION" + ChatColor.GOLD + " ▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GREEN + "Item: " + ChatColor.WHITE + itemName);
        player.sendMessage(ChatColor.GREEN + "Price per item: " + ChatColor.WHITE + VendorUtils.formatCurrency(price));
        player.sendMessage(ChatColor.GREEN + "Maximum quantity: " + ChatColor.WHITE + MAX_QUANTITY + " (" + VendorUtils.formatCurrency(maxCost) + ")");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Enter the " + ChatColor.BOLD + "QUANTITY" + ChatColor.YELLOW + " you'd like to purchase:");
        player.sendMessage(ChatColor.GRAY + "• Type a number between 1 and " + MAX_QUANTITY);
        player.sendMessage(ChatColor.GRAY + "• Type '" + ChatColor.RED + "cancel" + ChatColor.GRAY + "' to void this purchase");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
    }

    /**
     * Get display name for item
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return VendorUtils.capitalizeFirst(item.getType().name().toLowerCase().replace('_', ' '));
    }

    /**
     * Schedule transaction timeout
     */
    private void scheduleTransactionTimeout(UUID playerId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                PurchaseTransaction transaction = activeTransactions.get(playerId);
                if (transaction != null &&
                        System.currentTimeMillis() - transaction.startTime > TRANSACTION_TIMEOUT_MS) {

                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(ChatColor.RED + "Purchase timed out. Transaction cancelled.");
                    }

                    cancelTransaction(playerId);
                }
            }
        }.runTaskLater(plugin, (TRANSACTION_TIMEOUT_MS / 50) + 20); // Convert to ticks + buffer
    }

    /**
     * Enhanced chat input handling with validation
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player has active transaction
        PurchaseTransaction transaction = activeTransactions.get(playerId);
        if (transaction == null) {
            return;
        }

        // Cancel the chat event
        event.setCancelled(true);

        // Check message cooldown
        if (!isMessageCooldownExpired(playerId)) {
            return;
        }
        updateMessageCooldown(playerId);

        String message = event.getMessage().trim();

        // Handle cancellation
        if (message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "Purchase cancelled.");
                cancelTransaction(playerId);
            });
            return;
        }

        // Process quantity input on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            processQuantityInput(player, message, transaction);
        });
    }

    /**
     * Enhanced quantity input processing
     */
    private void processQuantityInput(Player player, String message, PurchaseTransaction transaction) {
        UUID playerId = player.getUniqueId();

        try {
            // Parse quantity
            int quantity;
            try {
                quantity = Integer.parseInt(message);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Please enter a valid number, or type '" + ChatColor.BOLD + "cancel" + ChatColor.RED + "' to void this purchase.");
                return;
            }

            // Validate quantity
            if (quantity <= 0) {
                player.sendMessage(ChatColor.RED + "Quantity must be greater than 0.");
                return;
            }

            if (quantity > MAX_QUANTITY) {
                player.sendMessage(ChatColor.RED + "Maximum quantity is " + MAX_QUANTITY + " items per transaction.");
                return;
            }

            // Calculate total cost
            long totalCost = (long) quantity * transaction.pricePerItem;

            // Validate total cost doesn't overflow
            if (totalCost > Integer.MAX_VALUE) {
                player.sendMessage(ChatColor.RED + "Total cost is too high. Please choose a smaller quantity.");
                return;
            }

            // Check if player has enough gems
            if (!economyManager.hasPhysicalGems(player, (int) totalCost)) {
                player.sendMessage(ChatColor.RED + "Insufficient funds! You need " + VendorUtils.formatCurrency(totalCost) + " but only have " + VendorUtils.formatCurrency(economyManager.getPhysicalGems(player)) + ".");
                player.sendMessage(ChatColor.GRAY + "Calculation: " + quantity + " × " + VendorUtils.formatCurrency(transaction.pricePerItem) + " = " + VendorUtils.formatCurrency(totalCost));
                return;
            }

            // Check inventory space
            if (!hasInventorySpace(player, transaction.item, quantity)) {
                player.sendMessage(ChatColor.RED + "Insufficient inventory space! Clear some room and try again.");
                return;
            }

            // Complete the purchase
            completePurchase(player, transaction, quantity, (int) totalCost);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing quantity input for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred while processing your purchase. Please try again.");
            cancelTransaction(playerId);
        }
    }

    /**
     * Enhanced inventory space checking
     */
    private boolean hasInventorySpace(Player player, ItemStack item, int quantity) {
        if (item.getMaxStackSize() == 1) {
            // Non-stackable items need individual slots
            int emptySlots = 0;
            for (ItemStack slot : player.getInventory().getStorageContents()) {
                if (slot == null || slot.getType().isAir()) {
                    emptySlots++;
                }
            }
            return quantity <= emptySlots;
        } else {
            // Stackable items - check available space
            Map<Integer, ? extends ItemStack> similar = player.getInventory().all(item.getType());
            int availableSpace = 0;

            // Count space in existing stacks
            for (ItemStack stack : similar.values()) {
                if (stack.isSimilar(item)) {
                    availableSpace += item.getMaxStackSize() - stack.getAmount();
                }
            }

            // Count empty slots
            for (ItemStack slot : player.getInventory().getStorageContents()) {
                if (slot == null || slot.getType().isAir()) {
                    availableSpace += item.getMaxStackSize();
                }
            }

            return quantity <= availableSpace;
        }
    }

    /**
     * Enhanced purchase completion
     */
    private void completePurchase(Player player, PurchaseTransaction transaction, int quantity, int totalCost) {
        UUID playerId = player.getUniqueId();

        try {
            // Double-check funds (race condition protection)
            if (!economyManager.hasPhysicalGems(player, totalCost)) {
                player.sendMessage(ChatColor.RED + "Insufficient funds! Your balance may have changed.");
                cancelTransaction(playerId);
                return;
            }

            // Remove gems first
            if (!economyManager.removePhysicalGems(player, totalCost).isSuccess()) {
                player.sendMessage(ChatColor.RED + "Failed to process payment. Transaction cancelled.");
                cancelTransaction(playerId);
                return;
            }

            // Give items
            boolean itemsGiven = giveItemsToPlayer(player, transaction.item, quantity);

            if (!itemsGiven) {
                // Refund if item giving failed
                economyManager.addBankGems(player, totalCost);
                player.sendMessage(ChatColor.RED + "Failed to give items. Payment refunded.");
                cancelTransaction(playerId);
                return;
            }

            // Success!
            sendSuccessMessage(player, transaction.item, quantity, totalCost);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            // Update statistics
            successfulTransactions.incrementAndGet();
            cleanupTransaction(playerId);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error completing purchase for player " + player.getName(), e);

            // Attempt refund
            try {
                economyManager.addBankGems(player, totalCost);
                player.sendMessage(ChatColor.RED + "Purchase failed due to an error. Payment refunded.");
            } catch (Exception refundError) {
                plugin.getLogger().log(Level.SEVERE, "Failed to refund player " + player.getName() + " " + totalCost + " gems", refundError);
                player.sendMessage(ChatColor.DARK_RED + "Critical error: Purchase failed and refund failed. Contact an administrator immediately.");
            }

            failedTransactions.incrementAndGet();
            cancelTransaction(playerId);
        }
    }

    /**
     * Enhanced item giving with better error handling
     */
    private boolean giveItemsToPlayer(Player player, ItemStack item, int quantity) {
        try {
            if (item.getMaxStackSize() == 1) {
                // Non-stackable items
                for (int i = 0; i < quantity; i++) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                    if (!leftover.isEmpty()) {
                        // Inventory full, drop remaining items and notify
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                        player.sendMessage(ChatColor.YELLOW + "Some items were dropped at your feet due to full inventory.");
                    }
                }
            } else {
                // Stackable items
                int remaining = quantity;
                while (remaining > 0) {
                    int stackSize = Math.min(remaining, item.getMaxStackSize());
                    ItemStack stack = item.clone();
                    stack.setAmount(stackSize);

                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                    if (!leftover.isEmpty()) {
                        // Drop leftover items
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                        player.sendMessage(ChatColor.YELLOW + "Some items were dropped at your feet due to full inventory.");
                    }

                    remaining -= stackSize;
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error giving items to player " + player.getName(), e);
            return false;
        }
    }

    /**
     * Enhanced success message
     */
    private void sendSuccessMessage(Player player, ItemStack item, int quantity, int totalCost) {
        String itemName = getItemDisplayName(item);

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬ " + ChatColor.GOLD + "PURCHASE SUCCESSFUL" + ChatColor.GREEN + " ▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GREEN + "✓ Purchased: " + ChatColor.WHITE + quantity + "x " + itemName);
        player.sendMessage(ChatColor.GREEN + "✓ Total cost: " + ChatColor.RED + "-" + VendorUtils.formatCurrency(totalCost));
        player.sendMessage(ChatColor.GREEN + "✓ Remaining balance: " + ChatColor.GOLD + VendorUtils.formatCurrency(economyManager.getPhysicalGems(player)));
        player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
    }

    /**
     * Enhanced transaction cancellation
     */
    public void cancelTransaction(UUID playerId) {
        PurchaseTransaction transaction = activeTransactions.remove(playerId);
        if (transaction != null) {
            cancelledTransactions.incrementAndGet();
        }
    }

    /**
     * Clean up transaction without marking as cancelled
     */
    private void cleanupTransaction(UUID playerId) {
        activeTransactions.remove(playerId);
    }

    /**
     * Player quit event handling
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        cancelTransaction(playerId);
        lastInteractionTime.remove(playerId);
    }

    /**
     * Message cooldown management
     */
    private boolean isMessageCooldownExpired(UUID playerId) {
        Long lastTime = lastInteractionTime.get(playerId);
        if (lastTime == null) {
            return true;
        }
        return System.currentTimeMillis() - lastTime >= MESSAGE_COOLDOWN_MS;
    }

    private void updateMessageCooldown(UUID playerId) {
        lastInteractionTime.put(playerId, System.currentTimeMillis());
    }

    /**
     * Cleanup expired transactions
     */
    private void cleanupExpiredTransactions() {
        long currentTime = System.currentTimeMillis();

        activeTransactions.entrySet().removeIf(entry -> {
            PurchaseTransaction transaction = entry.getValue();
            if (currentTime - transaction.startTime > TRANSACTION_TIMEOUT_MS) {
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Your purchase timed out and was cancelled.");
                }
                cancelledTransactions.incrementAndGet();
                return true;
            }
            return false;
        });
    }

    /**
     * Cleanup old interaction times
     */
    private void cleanupOldInteractionTimes() {
        long currentTime = System.currentTimeMillis();
        lastInteractionTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 300000); // 5 minutes
    }

    /**
     * Enhanced price extraction with better parsing
     */
    public static int getPriceFromLore(ItemStack item) {
        return VendorUtils.extractPriceFromLore(item);
    }

    /**
     * Check if player is in purchase process
     */
    public boolean isInPurchaseProcess(UUID playerId) {
        return activeTransactions.containsKey(playerId);
    }

    /**
     * Get active transaction count
     */
    public int getActiveTransactionCount() {
        return activeTransactions.size();
    }

    /**
     * Get purchase statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTransactions", totalTransactions.get());
        stats.put("successfulTransactions", successfulTransactions.get());
        stats.put("failedTransactions", failedTransactions.get());
        stats.put("cancelledTransactions", cancelledTransactions.get());
        stats.put("activeTransactions", activeTransactions.size());

        long total = totalTransactions.get();
        if (total > 0) {
            stats.put("successRate", (double) successfulTransactions.get() / total * 100.0);
        } else {
            stats.put("successRate", 0.0);
        }

        return stats;
    }

    /**
     * Force cancel all transactions (admin command)
     */
    public void cancelAllTransactions() {
        for (UUID playerId : new HashSet<>(activeTransactions.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.RED + "Your purchase was cancelled by an administrator.");
            }
            cancelTransaction(playerId);
        }
        plugin.getLogger().info("All active transactions cancelled by administrator");
    }

    /**
     * Transaction data class
     */
    private static class PurchaseTransaction {
        final UUID playerId;
        final ItemStack item;
        final int pricePerItem;
        final long startTime;

        PurchaseTransaction(UUID playerId, ItemStack item, int pricePerItem, long startTime) {
            this.playerId = playerId;
            this.item = item.clone(); // Defensive copy
            this.pricePerItem = pricePerItem;
            this.startTime = startTime;
        }
    }
}