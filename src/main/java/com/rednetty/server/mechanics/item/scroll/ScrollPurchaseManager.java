package com.rednetty.server.mechanics.item.scroll;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.TransactionResult;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages scroll purchase transactions
 */
public class ScrollPurchaseManager {

    private static final Map<UUID, PendingPurchase> pendingPurchases = new HashMap<>();
    private static final int MAX_QUANTITY = 64;
    private static EconomyManager economyManager;

    /**
     * Initializes the purchase manager
     */
    public static void initialize() {
        economyManager = YakRealms.getInstance().getEconomyManager();
    }

    /**
     * Starts a new purchase transaction
     *
     * @param player    The player making the purchase
     * @param item      The item being purchased
     * @param unitPrice The price per item
     */
    public static void startPurchase(Player player, ItemStack item, int unitPrice) {
        pendingPurchases.put(player.getUniqueId(), new PendingPurchase(item, unitPrice));
    }

    /**
     * Handles a chat message as a quantity input
     *
     * @param player  The player entering the quantity
     * @param message The message containing the quantity
     * @return true if the message was handled as a purchase quantity
     */
    public static boolean handleQuantityInput(Player player, String message) {
        UUID playerId = player.getUniqueId();

        if (!pendingPurchases.containsKey(playerId)) {
            return false;
        }

        PendingPurchase purchase = pendingPurchases.get(playerId);

        try {
            int quantity = Integer.parseInt(message.trim());
            completePurchase(player, purchase, quantity);
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a valid number for quantity.");
            return true;
        }
    }

    /**
     * Cancels a pending purchase
     *
     * @param player The player whose purchase should be canceled
     */
    public static void cancelPurchase(Player player) {
        pendingPurchases.remove(player.getUniqueId());
    }

    /**
     * Completes a purchase with the specified quantity
     *
     * @param player   The player making the purchase
     * @param purchase The pending purchase details
     * @param quantity The quantity to purchase
     */
    private static void completePurchase(Player player, PendingPurchase purchase, int quantity) {
        // Validate quantity
        if (quantity <= 0) {
            player.sendMessage(ChatColor.RED + "Quantity must be greater than zero.");
            cancelPurchase(player);
            return;
        }

        if (quantity > MAX_QUANTITY) {
            player.sendMessage(ChatColor.RED + "Maximum quantity is " + MAX_QUANTITY + ".");
            quantity = MAX_QUANTITY;
        }

        // Calculate total price
        int totalPrice = purchase.unitPrice * quantity;

        // Check if player has enough gems
        if (!economyManager.hasGems(player.getUniqueId(), totalPrice)) {
            player.sendMessage(ChatColor.RED + "You don't have enough gems for this purchase.");
            player.sendMessage(ChatColor.RED + "Cost: " + totalPrice + "g for " + quantity + " items.");
            cancelPurchase(player);
            return;
        }

        // Process payment
        TransactionResult result = economyManager.removeGems(player.getUniqueId(), totalPrice);

        if (result.isSuccess()) {
            // Create items and give to player
            ItemStack itemStack = purchase.item.clone();
            itemStack.setAmount(quantity);

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);

            // Handle any items that didn't fit in inventory
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                player.sendMessage(ChatColor.YELLOW + "Some items were dropped on the ground because your inventory is full.");
            }

            // Confirmation message
            player.sendMessage(ChatColor.GREEN + "Successfully purchased " + ChatColor.YELLOW + quantity + "x " +
                    ChatColor.WHITE + itemStack.getItemMeta().getDisplayName() +
                    ChatColor.GREEN + " for " + ChatColor.GOLD + TextUtil.formatNumber(totalPrice) + "g");

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(ChatColor.RED + "Transaction failed: " + result.getMessage());
        }

        // Clean up
        cancelPurchase(player);
    }

    /**
     * Class representing a pending purchase
     */
    private static class PendingPurchase {
        private final ItemStack item;
        private final int unitPrice;

        public PendingPurchase(ItemStack item, int unitPrice) {
            this.item = item;
            this.unitPrice = unitPrice;
        }
    }
}