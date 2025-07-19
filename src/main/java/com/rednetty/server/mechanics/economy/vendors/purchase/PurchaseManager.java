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
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * FIXED purchase manager that works with original clean items from vendor menus
 * Now receives clean items directly and gives them to players without modification
 */
public class PurchaseManager implements Listener {
    // Configuration
    private static final long PURCHASE_TIMEOUT_MS = 60000; // 1 minute timeout
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private static final long PURCHASE_COOLDOWN_MS = 500; // 500ms cooldown
    private static final int MIN_QUANTITY = 1;
    private static PurchaseManager instance;
    private final Map<UUID, PurchaseSession> activePurchases = new ConcurrentHashMap<>();
    private static final int MAX_QUANTITY = 64;
    private final Map<UUID, Long> lastPurchaseTime = new ConcurrentHashMap<>();

    private PurchaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = EconomyManager.getInstance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start cleanup task for expired sessions
        startCleanupTask();
    }

    public static void initialize(JavaPlugin plugin) {
        if (instance == null) {
            instance = new PurchaseManager(plugin);
        }
    }

    public static PurchaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PurchaseManager not initialized!");
        }
        return instance;
    }

    /**
     *  Start purchase session with original clean item (no cleaning needed)
     */
    public boolean startPurchase(Player player, ItemStack originalCleanItem, int pricePerItem) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        UUID playerId = player.getUniqueId();

        // Check for existing session
        if (activePurchases.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "⚠ You already have an active purchase. Type " +
                    ChatColor.BOLD + "'cancel'" + ChatColor.RED + " to cancel it.");
            return false;
        }

        // Check purchase cooldown
        if (isOnCooldown(playerId)) {
            player.sendMessage(ChatColor.RED + "Please wait a moment before starting another purchase.");
            return false;
        }

        // Validate item
        if (originalCleanItem == null || originalCleanItem.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Invalid item for purchase.");
            return false;
        }

        // Validate price
        if (pricePerItem <= 0) {
            player.sendMessage(ChatColor.RED + "This item is not available for purchase.");
            return false;
        }

        // Comprehensive funds check
        int playerGems = economyManager.getPhysicalGems(player);
        if (playerGems < pricePerItem) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.RED + "                    ⚠ INSUFFICIENT FUNDS ⚠");
            player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.RED + "Required: " + ChatColor.WHITE + VendorUtils.formatColoredCurrency(pricePerItem));
            player.sendMessage(ChatColor.RED + "Your gems: " + ChatColor.WHITE + VendorUtils.formatColoredCurrency(playerGems));
            player.sendMessage(ChatColor.RED + "Needed: " + ChatColor.WHITE + VendorUtils.formatColoredCurrency(pricePerItem - playerGems));
            player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            return false;
        }

        try {
            //  Use the original clean item directly (no cleaning needed)
            ItemStack cleanItem = originalCleanItem.clone();

            // Validate clean item
            if (cleanItem == null || cleanItem.getType().isAir()) {
                player.sendMessage(ChatColor.RED + "Failed to process item for purchase.");
                return false;
            }

            // Create session with clean item
            PurchaseSession session = new PurchaseSession(cleanItem, pricePerItem, System.currentTimeMillis());
            activePurchases.put(playerId, session);

            // Send  purchase prompt
            sendPurchasePrompt(player, session);

            plugin.getLogger().info("Started purchase session for " + player.getName() +
                    " - Item: " + cleanItem.getType() + ", Price: " + pricePerItem);

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error starting purchase for " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred while starting the purchase. Please try again.");
            return false;
        }
    }

    /**
     *  purchase prompt with beautiful formatting
     */
    private void sendPurchasePrompt(Player player, PurchaseSession session) {
        String itemName = getCleanItemDisplayName(session.item);
        int maxAffordable = Math.min(MAX_QUANTITY, economyManager.getPhysicalGems(player) / session.pricePerItem);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GOLD + "                      🛒 PURCHASE MENU 🛒");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "📦 Item: " + ChatColor.WHITE + itemName);
        player.sendMessage(ChatColor.GREEN + "💰 Price per item: " + VendorUtils.formatColoredCurrency(session.pricePerItem));
        player.sendMessage(ChatColor.GREEN + "💎 Your gems: " + VendorUtils.formatColoredCurrency(economyManager.getPhysicalGems(player)));
        player.sendMessage(ChatColor.GREEN + "📊 Max affordable: " + ChatColor.WHITE + maxAffordable + " items");

        if (maxAffordable >= 10) {
            long cost10 = (long) session.pricePerItem * 10;
            player.sendMessage(ChatColor.GRAY + "   💡 10 items would cost: " + VendorUtils.formatColoredCurrency(cost10));
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "📝 Enter the " + ChatColor.BOLD + "QUANTITY" + ChatColor.YELLOW + " you want to buy:");
        player.sendMessage(ChatColor.GRAY + "   Range: " + ChatColor.WHITE + MIN_QUANTITY + " - " + Math.min(MAX_QUANTITY, maxAffordable));
        player.sendMessage(ChatColor.GRAY + "   Type " + ChatColor.RED + "'cancel'" + ChatColor.GRAY + " to exit");
        player.sendMessage(ChatColor.GRAY + "   ⏱ Auto-expires in 60 seconds");
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        // Play a pleasant sound
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /**
     * Handle chat input with better validation and feedback
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        PurchaseSession session = activePurchases.get(playerId);
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        // Check session timeout
        if (System.currentTimeMillis() - session.startTime > PURCHASE_TIMEOUT_MS) {
            activePurchases.remove(playerId);
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "⏱ Purchase session timed out. Please try again.");
            player.sendMessage("");
            return;
        }

        // Handle cancellation with multiple variations
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("c") ||
                message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("quit")) {
            cancelPurchase(playerId, ChatColor.YELLOW + "Purchase cancelled by player.");
            return;
        }

        //  quantity parsing with better error messages
        int quantity;
        try {
            quantity = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ Invalid input: '" + message + "'");
            player.sendMessage(ChatColor.YELLOW + "Please enter a valid number (" + MIN_QUANTITY + "-" + MAX_QUANTITY + ") or 'cancel'");
            player.sendMessage("");
            return;
        }

        //  quantity validation
        if (quantity < MIN_QUANTITY) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ Quantity too low! Minimum: " + MIN_QUANTITY);
            player.sendMessage("");
            return;
        }

        if (quantity > MAX_QUANTITY) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ Quantity too high! Maximum: " + MAX_QUANTITY);
            player.sendMessage(ChatColor.GRAY + "For larger purchases, contact an administrator.");
            player.sendMessage("");
            return;
        }

        // Check affordability
        long totalCost = (long) session.pricePerItem * quantity;
        if (totalCost > economyManager.getPhysicalGems(player)) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ Cannot afford " + quantity + " items!");
            player.sendMessage(ChatColor.RED + "Cost: " + VendorUtils.formatColoredCurrency(totalCost));
            player.sendMessage(ChatColor.RED + "Your gems: " + VendorUtils.formatColoredCurrency(economyManager.getPhysicalGems(player)));
            player.sendMessage("");
            return;
        }

        // Process purchase on main thread
        Bukkit.getScheduler().runTask(plugin, () -> processPurchase(player, session, quantity));
    }

    /**
     *  Process purchase with original clean items (no modification needed)
     */
    private void processPurchase(Player player, PurchaseSession session, int quantity) {
        UUID playerId = player.getUniqueId();

        try {
            // Validate player is still online
            if (!player.isOnline()) {
                activePurchases.remove(playerId);
                return;
            }

            // Calculate total cost with overflow protection
            long totalCost = (long) session.pricePerItem * quantity;
            if (totalCost > Integer.MAX_VALUE) {
                player.sendMessage(ChatColor.RED + "❌ Purchase amount too large! Please reduce quantity.");
                cancelPurchase(playerId, "Purchase amount too large");
                return;
            }

            // Final funds check (player might have spent gems while in menu)
            if (!economyManager.hasPhysicalGems(player, (int) totalCost)) {
                int currentGems = economyManager.getPhysicalGems(player);
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "❌ Insufficient gems!");
                player.sendMessage(ChatColor.RED + "Required: " + VendorUtils.formatColoredCurrency((int) totalCost));
                player.sendMessage(ChatColor.RED + "Available: " + VendorUtils.formatColoredCurrency(currentGems));
                player.sendMessage("");
                cancelPurchase(playerId, "Insufficient funds");
                return;
            }

            // Check inventory space before payment
            if (!hasInventorySpace(player, session.item, quantity)) {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "❌ Not enough inventory space for " + quantity + " items!");
                player.sendMessage(ChatColor.YELLOW + "Free up some space and try again.");
                player.sendMessage("");
                cancelPurchase(playerId, "Insufficient inventory space");
                return;
            }

            // Process payment
            var paymentResult = economyManager.removePhysicalGems(player, (int) totalCost);
            if (!paymentResult.isSuccess()) {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "❌ Payment failed: " + paymentResult.getMessage());
                player.sendMessage("");
                cancelPurchase(playerId, "Payment failed: " + paymentResult.getMessage());
                return;
            }

            //  Give items directly without any modification (session.item is already clean)
            int itemsGiven = giveItems(player, session.item, quantity);

            if (itemsGiven < quantity) {
                // Partial success - refund the difference
                int refund = (quantity - itemsGiven) * session.pricePerItem;
                economyManager.addBankGems(player, refund);

                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "⚠ Partial purchase completed!");
                player.sendMessage(ChatColor.YELLOW + "Items given: " + itemsGiven + "/" + quantity);
                player.sendMessage(ChatColor.YELLOW + "Refunded: " + VendorUtils.formatColoredCurrency(refund));
                player.sendMessage("");
            }

            // Send beautiful success message
            sendSuccessMessage(player, session, itemsGiven, itemsGiven * session.pricePerItem);

            // Update purchase tracking
            lastPurchaseTime.put(playerId, System.currentTimeMillis());

            // Log successful purchase
            plugin.getLogger().info("Purchase completed: " + player.getName() +
                    " bought " + itemsGiven + "x " + session.item.getType() +
                    " for " + (itemsGiven * session.pricePerItem) + " gems");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error processing purchase for " + player.getName(), e);
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ An error occurred while processing your purchase.");
            player.sendMessage(ChatColor.RED + "Please contact an administrator if gems were deducted.");
            player.sendMessage("");

            // Attempt emergency refund
            try {
                int totalCost = session.pricePerItem * quantity;
                economyManager.addBankGems(player, totalCost);
                player.sendMessage(ChatColor.GREEN + "💰 Emergency refund of " +
                        VendorUtils.formatColoredCurrency(totalCost) + " issued.");
            } catch (Exception refundError) {
                plugin.getLogger().log(Level.SEVERE, "CRITICAL: Failed to emergency refund player " + player.getName(), refundError);
            }

        } finally {
            // Always clean up the session
            activePurchases.remove(playerId);
        }
    }

    /**
     * Send  success message with detailed information
     */
    private void sendSuccessMessage(Player player, PurchaseSession session, int quantity, int totalCost) {
        String itemName = getCleanItemDisplayName(session.item);

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GREEN + "                    ✅ PURCHASE SUCCESSFUL! ✅");
        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "🎉 Purchase completed successfully!");
        player.sendMessage(ChatColor.GREEN + "📦 Bought: " + ChatColor.WHITE + quantity + "x " + itemName);
        player.sendMessage(ChatColor.GREEN + "💸 Total cost: " + ChatColor.RED + "-" + VendorUtils.formatColoredCurrency(totalCost));
        player.sendMessage(ChatColor.GREEN + "💎 Remaining gems: " + VendorUtils.formatColoredCurrency(economyManager.getPhysicalGems(player)));

        if (quantity > 1) {
            player.sendMessage(ChatColor.GREEN + "📊 Price per item: " + VendorUtils.formatColoredCurrency(session.pricePerItem));
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "🎁 Items have been added to your inventory!");
        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        // Play success sounds
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    /**
     *  cancel purchase with better messaging
     */
    private void cancelPurchase(UUID playerId, String reason) {
        activePurchases.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "🚫 " + reason);
            player.sendMessage("");
        }
    }

    /**
     * Get clean display name for items (no cleaning needed, items are already clean)
     */
    private String getCleanItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            // The item is already clean, just get the display name
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return VendorUtils.formatVendorTypeName(item.getType().name());
    }

    /**
     *  inventory space checking with better stack handling
     */
    private boolean hasInventorySpace(Player player, ItemStack item, int quantity) {
        try {
            int spaceNeeded = quantity;
            int maxStack = item.getMaxStackSize();

            for (ItemStack slot : player.getInventory().getStorageContents()) {
                if (slot == null || slot.getType().isAir()) {
                    spaceNeeded -= maxStack;
                } else if (slot.isSimilar(item)) {
                    int canAdd = maxStack - slot.getAmount();
                    spaceNeeded -= canAdd;
                }

                if (spaceNeeded <= 0) {
                    return true;
                }
            }

            return spaceNeeded <= 0;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking inventory space for " + player.getName(), e);
            return false; // Err on the side of caution
        }
    }

    /**
     * Give items to player (items are already clean, no modification needed)
     */
    private int giveItems(Player player, ItemStack item, int quantity) {
        int given = 0;

        try {
            // Use the clean item directly (no cloning needed for modification)
            ItemStack toGive = item.clone();

            while (quantity > 0 && given < MAX_QUANTITY) {
                int stackSize = Math.min(quantity, item.getMaxStackSize());
                toGive.setAmount(stackSize);

                Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive.clone());

                if (leftover.isEmpty()) {
                    // All items were added
                    given += stackSize;
                    quantity -= stackSize;
                } else {
                    // Some items couldn't be added
                    int notAdded = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int actuallyAdded = stackSize - notAdded;
                    given += actuallyAdded;

                    // Drop remaining items at player's feet with notification
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }

                    if (actuallyAdded < stackSize) {
                        player.sendMessage(ChatColor.YELLOW + "⚠ Some items were dropped at your feet due to full inventory.");
                        break; // Stop trying if inventory is full
                    }

                    quantity -= stackSize;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error giving items to " + player.getName(), e);
        }

        return given;
    }

    /**
     * Check if player is on purchase cooldown
     */
    private boolean isOnCooldown(UUID playerId) {
        Long lastPurchase = lastPurchaseTime.get(playerId);
        return lastPurchase != null && (System.currentTimeMillis() - lastPurchase) < PURCHASE_COOLDOWN_MS;
    }

    /**
     * Start cleanup task for expired sessions
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }.runTaskTimer(plugin, 600L, 600L); // Run every 30 seconds
    }

    /**
     * Clean up expired purchase sessions
     */
    private void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();

        activePurchases.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            PurchaseSession session = entry.getValue();

            boolean expired = (currentTime - session.startTime) > PURCHASE_TIMEOUT_MS;

            if (expired) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "⏱ Your purchase session has expired.");
                    player.sendMessage("");
                }
                plugin.getLogger().info("Cleaned up expired purchase session for " + playerId);
            }

            return expired;
        });

        // Clean up old purchase times (keep for 5 minutes)
        lastPurchaseTime.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > 300000);
    }

    /**
     * Handle player quit/kick events
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerLeave(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerLeave(event.getPlayer());
    }

    /**
     * Handle player leaving the server
     */
    private void handlePlayerLeave(Player player) {
        UUID playerId = player.getUniqueId();
        PurchaseSession session = activePurchases.remove(playerId);

        if (session != null) {
            plugin.getLogger().info("Cleaned up purchase session for disconnected player: " + player.getName());
        }
    }

    /**
     * Check if player is in purchase process
     */
    public boolean isInPurchaseProcess(UUID playerId) {
        return activePurchases.containsKey(playerId);
    }

    /**
     * Force cancel a purchase (admin function)
     */
    public boolean cancelPurchase(UUID playerId) {
        PurchaseSession session = activePurchases.remove(playerId);
        if (session != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.RED + "🚫 Your purchase was cancelled by an administrator.");
            }
            return true;
        }
        return false;
    }

    /**
     * Get active purchase count (for monitoring)
     */
    public int getActivePurchaseCount() {
        return activePurchases.size();
    }

    /**
     *  Purchase session data class - stores original clean items
     */
    private static class PurchaseSession {
        final ItemStack item; // This is now guaranteed to be the original clean item
        final int pricePerItem;
        final long startTime;

        PurchaseSession(ItemStack originalCleanItem, int pricePerItem, long startTime) {
            this.item = originalCleanItem;
            this.pricePerItem = pricePerItem;
            this.startTime = startTime;
        }
    }
}