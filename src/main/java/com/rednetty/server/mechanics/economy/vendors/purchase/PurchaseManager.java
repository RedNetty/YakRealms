package com.rednetty.server.mechanics.economy.vendors.purchase;

import com.rednetty.server.mechanics.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Manages the purchase process for vendor items
 */
public class PurchaseManager implements Listener {
    private static PurchaseManager instance;
    private final Map<UUID, ItemStack> buyingItem = new HashMap<>();
    private final Map<UUID, Integer> buyingPrice = new HashMap<>();
    private final EconomyManager economyManager;
    private final JavaPlugin plugin;

    /**
     * Private constructor for singleton pattern
     *
     * @param plugin The main plugin instance
     */
    private PurchaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = EconomyManager.getInstance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Gets the singleton instance
     *
     * @return The PurchaseManager instance
     */
    public static PurchaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PurchaseManager not initialized");
        }
        return instance;
    }

    /**
     * Initializes the PurchaseManager
     *
     * @param plugin The main plugin instance
     * @return The PurchaseManager instance
     */
    public static PurchaseManager initialize(JavaPlugin plugin) {
        if (instance == null) {
            instance = new PurchaseManager(plugin);
        }
        return instance;
    }

    /**
     * Starts a purchase process for a player
     *
     * @param player The player making the purchase
     * @param item   The item being purchased
     * @param price  The price of the item
     */
    public void startPurchase(Player player, ItemStack item, int price) {
        // Prepare item by removing price from lore
        ItemStack purchaseItem = item.clone();
        ItemMeta meta = purchaseItem.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = new ArrayList<>(meta.getLore());
            // Remove the price line
            lore.removeIf(line -> line.contains("Price:"));
            meta.setLore(lore);
            purchaseItem.setItemMeta(meta);
        }

        // Store purchase information
        UUID playerId = player.getUniqueId();
        buyingItem.put(playerId, purchaseItem);
        buyingPrice.put(playerId, price);

        // Inform player about the purchase
        player.sendMessage(ChatColor.GREEN + "Enter the " + ChatColor.BOLD + "QUANTITY" + ChatColor.GREEN + " you'd like to purchase.");
        player.sendMessage(ChatColor.GRAY + "MAX: 64X (" + price * 64 + "g), OR " + price + "g/each.");
        player.sendMessage(ChatColor.GRAY + "Type 'cancel' to void this purchase.");
    }

    /**
     * Cancels an ongoing purchase for a player
     *
     * @param playerId The UUID of the player
     */
    public void cancelPurchase(UUID playerId) {
        buyingItem.remove(playerId);
        buyingPrice.remove(playerId);
    }

    /**
     * Checks if a player is currently in a purchase process
     *
     * @param playerId The UUID of the player
     * @return true if the player is in a purchase process
     */
    public boolean isInPurchaseProcess(UUID playerId) {
        return buyingItem.containsKey(playerId) && buyingPrice.containsKey(playerId);
    }

    /**
     * Handles chat messages for quantity input
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!isInPurchaseProcess(playerId)) {
            return;
        }

        // Cancel the chat event to prevent the message from being broadcast
        event.setCancelled(true);

        String message = event.getMessage();
        ItemStack purchaseItem = buyingItem.get(playerId);
        int price = buyingPrice.get(playerId);

        // Handle cancellation
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.RED + "Purchase of item - " + ChatColor.BOLD + "CANCELLED");
            cancelPurchase(playerId);
            return;
        }

        // Parse quantity
        int quantity;
        try {
            quantity = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a valid number, or type 'cancel' to void this purchase.");
            return;
        }

        // Validate quantity
        if (quantity <= 0) {
            player.sendMessage(ChatColor.RED + "You cannot purchase a NON-POSITIVE number.");
            return;
        }

        if (quantity > 64) {
            player.sendMessage(ChatColor.RED + "You " + ChatColor.UNDERLINE + "cannot" + ChatColor.RED +
                    " buy MORE than " + ChatColor.BOLD + "64x" + ChatColor.RED + " of a material per transaction.");
            return;
        }

        // Check if player has enough gems
        int totalCost = quantity * price;
        if (!economyManager.hasGems(player, totalCost)) {
            player.sendMessage(ChatColor.RED + "You do not have enough GEM(s) to complete this purchase.");
            player.sendMessage(ChatColor.GRAY.toString() + quantity + " X " + price +
                    " gem(s)/ea = " + totalCost + " gem(s).");
            return;
        }

        // Process the purchase
        completePurchase(player, purchaseItem, quantity, totalCost);
    }

    /**
     * Completes a purchase by giving the item to the player and taking gems
     *
     * @param player    The player making the purchase
     * @param item      The item being purchased
     * @param quantity  The quantity being purchased
     * @param totalCost The total cost of the purchase
     */
    private void completePurchase(Player player, ItemStack item, int quantity, int totalCost) {
        UUID playerId = player.getUniqueId();

        // Handle single-item (non-stackable) purchases
        if (item.getMaxStackSize() == 1) {
            // Check inventory space
            int emptySlots = 0;
            for (ItemStack slot : player.getInventory().getStorageContents()) {
                if (slot == null || slot.getType().isAir()) {
                    emptySlots++;
                }
            }

            if (quantity > emptySlots) {
                player.sendMessage(ChatColor.RED + "No space available in inventory. Type 'cancel' or clear some room.");
                return;
            }

            // Give items
            for (int i = 0; i < quantity; i++) {
                player.getInventory().addItem(item.clone());
            }
        } else {
            // Handle stackable items
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(ChatColor.RED + "No space available in inventory. Type 'cancel' or clear some room.");
                return;
            }

            ItemStack stackedItem = item.clone();
            stackedItem.setAmount(quantity);
            player.getInventory().addItem(stackedItem);
        }

        // Take gems
        economyManager.removeGems(player, totalCost);
        player.sendMessage(ChatColor.RED + "-" + totalCost + ChatColor.BOLD + "G");
        player.sendMessage(ChatColor.GREEN + "Transaction successful.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

        // Clear purchase data
        cancelPurchase(playerId);
    }

    /**
     * Extracts the price from an item's lore
     *
     * @param item The item to check
     * @return The price, or -1 if not found
     */
    public static int getPriceFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return -1;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("Price:")) {
                String priceStr = ChatColor.stripColor(line);
                priceStr = priceStr.substring(priceStr.indexOf(":") + 1).trim();
                priceStr = priceStr.substring(0, priceStr.length() - 1);
                try {
                    return Integer.parseInt(priceStr);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }

        return -1;
    }
}