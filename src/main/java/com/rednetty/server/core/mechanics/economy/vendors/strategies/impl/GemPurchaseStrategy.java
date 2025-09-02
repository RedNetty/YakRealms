package com.rednetty.server.core.mechanics.economy.vendors.strategies.impl;

import com.rednetty.server.core.mechanics.economy.EconomyManager;
import com.rednetty.server.core.mechanics.economy.TransactionResult;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.PurchaseStrategy;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.PurchaseStrategy.PurchaseResult;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.PurchaseStrategy.PurchaseValidation;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.PurchaseStrategy.CurrencyType;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Purchase strategy that uses gems as currency
 * 
 * This strategy handles purchases using the server's gem economy system.
 */
public class GemPurchaseStrategy implements PurchaseStrategy {
    
    private final EconomyManager economyManager;
    private final YakPlayerManager playerManager;
    private final Logger logger;
    
    public GemPurchaseStrategy(EconomyManager economyManager, YakPlayerManager playerManager, Logger logger) {
        this.economyManager = economyManager;
        this.playerManager = playerManager;
        this.logger = logger;
    }
    
    @Override
    public PurchaseValidation canPurchase(Player player, ItemStack item, int quantity, int totalPrice) {
        try {
            // Check if player data is available
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                return PurchaseValidation.failure(
                    "Your player data is still loading. Please wait a moment.",
                    PurchaseValidation.ErrorType.SYSTEM_ERROR
                );
            }
            
            // Check if quantity is valid
            if (quantity <= 0 || quantity > getMaxQuantity(player, item)) {
                return PurchaseValidation.failure(
                    "Invalid quantity. Must be between 1 and " + getMaxQuantity(player, item),
                    PurchaseValidation.ErrorType.INVALID_QUANTITY
                );
            }
            
            // Check if player has enough gems
            long playerGems = yakPlayer.getBankGems();
            if (playerGems < totalPrice) {
                return PurchaseValidation.failure(
                    "Insufficient gems. You need " + totalPrice + "g but only have " + playerGems + "g",
                    PurchaseValidation.ErrorType.INSUFFICIENT_FUNDS
                );
            }
            
            // Check if player has inventory space
            if (!hasInventorySpace(player, item, quantity)) {
                return PurchaseValidation.failure(
                    "Not enough inventory space for this purchase",
                    PurchaseValidation.ErrorType.INSUFFICIENT_SPACE
                );
            }
            
            return PurchaseValidation.success();
            
        } catch (Exception e) {
            logger.warning("Error validating purchase for " + player.getName() + ": " + e.getMessage());
            return PurchaseValidation.failure(
                "System error during validation. Please try again.",
                PurchaseValidation.ErrorType.SYSTEM_ERROR
            );
        }
    }
    
    @Override
    public CompletableFuture<PurchaseResult> processPurchase(Player player, ItemStack item, int quantity, int totalPrice) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate purchase again (double-check)
                PurchaseValidation validation = canPurchase(player, item, quantity, totalPrice);
                if (!validation.isValid()) {
                    return PurchaseResult.failure(validation.getErrorMessage());
                }
                
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer == null) {
                    return PurchaseResult.failure("Player data not available");
                }
                
                // Attempt to deduct gems from bank
                TransactionResult deductResult = economyManager.removeBankGems(player, totalPrice);
                if (!deductResult.isSuccess()) {
                    return PurchaseResult.failure("Failed to deduct gems from your bank: " + deductResult.getMessage());
                }
                
                // Give item to player
                ItemStack purchaseItem = item.clone();
                purchaseItem.setAmount(quantity);
                
                if (!giveItemToPlayer(player, purchaseItem)) {
                    // Rollback - return the gems
                    economyManager.addBankGems(player, totalPrice);
                    return PurchaseResult.failure("Failed to give item - gems have been refunded");
                }
                
                // Success
                String successMessage = String.format(
                    "Successfully purchased %dx %s for %dg",
                    quantity,
                    getItemDisplayName(item),
                    totalPrice
                );
                
                return PurchaseResult.success(successMessage, purchaseItem, quantity, totalPrice);
                
            } catch (Exception e) {
                logger.warning("Error processing purchase for " + player.getName() + ": " + e.getMessage());
                return PurchaseResult.failure("System error during purchase. Please contact an administrator.");
            }
        });
    }
    
    @Override
    public CurrencyType getCurrencyType() {
        return CurrencyType.GEMS;
    }
    
    @Override
    public String getStrategyName() {
        return "gem_purchase";
    }
    
    @Override
    public boolean supportsQuantitySelection() {
        return true;
    }
    
    @Override
    public int getMaxQuantity(Player player, ItemStack item) {
        // Limit based on available inventory space
        int availableSlots = getAvailableInventorySlots(player);
        int maxStackSize = item.getMaxStackSize();
        
        // Calculate maximum quantity based on inventory space
        int maxFromSpace = availableSlots * maxStackSize;
        
        // Also check existing partial stacks
        int existingAmount = 0;
        for (ItemStack inventoryItem : player.getInventory().getContents()) {
            if (inventoryItem != null && inventoryItem.isSimilar(item)) {
                existingAmount += (maxStackSize - inventoryItem.getAmount());
            }
        }
        
        return Math.min(maxFromSpace + existingAmount, 64 * 9); // Reasonable upper limit
    }
    
    @Override
    public CompletableFuture<Boolean> rollbackPurchase(Player player, ItemStack item, int quantity, int refundAmount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer == null) {
                    return false;
                }
                
                // Try to remove the items from player inventory
                ItemStack removeItem = item.clone();
                removeItem.setAmount(quantity);
                
                if (removeItemFromPlayer(player, removeItem)) {
                    // Successfully removed items, refund gems
                    economyManager.addBankGems(player, refundAmount);
                    return true;
                }
                
                return false;
                
            } catch (Exception e) {
                logger.warning("Error rolling back purchase for " + player.getName() + ": " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Check if player has enough inventory space
     * 
     * @param player The player to check
     * @param item The item being purchased
     * @param quantity The quantity being purchased
     * @return true if there's enough space
     */
    private boolean hasInventorySpace(Player player, ItemStack item, int quantity) {
        int availableSlots = getAvailableInventorySlots(player);
        int maxStackSize = item.getMaxStackSize();
        
        // Check existing partial stacks
        int existingSpace = 0;
        for (ItemStack inventoryItem : player.getInventory().getContents()) {
            if (inventoryItem != null && inventoryItem.isSimilar(item)) {
                existingSpace += (maxStackSize - inventoryItem.getAmount());
            }
        }
        
        int totalSpace = (availableSlots * maxStackSize) + existingSpace;
        return totalSpace >= quantity;
    }
    
    /**
     * Get the number of available inventory slots
     * 
     * @param player The player to check
     * @return Number of empty slots
     */
    private int getAvailableInventorySlots(Player player) {
        int emptySlots = 0;
        for (ItemStack inventoryItem : player.getInventory().getStorageContents()) {
            if (inventoryItem == null) {
                emptySlots++;
            }
        }
        return emptySlots;
    }
    
    /**
     * Give an item to a player
     * 
     * @param player The player to give the item to
     * @param item The item to give
     * @return true if successful
     */
    private boolean giveItemToPlayer(Player player, ItemStack item) {
        try {
            var remaining = player.getInventory().addItem(item);
            
            // If there are remaining items, drop them at player location
            if (!remaining.isEmpty()) {
                for (ItemStack remainingItem : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), remainingItem);
                }
            }
            
            return true;
        } catch (Exception e) {
            logger.warning("Error giving item to player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove an item from a player's inventory
     * 
     * @param player The player to remove from
     * @param item The item to remove
     * @return true if successful
     */
    private boolean removeItemFromPlayer(Player player, ItemStack item) {
        try {
            return player.getInventory().removeItem(item).isEmpty();
        } catch (Exception e) {
            logger.warning("Error removing item from player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the display name of an item
     * 
     * @param item The item to get the name of
     * @return The display name
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        
        // Convert material name to readable format
        return item.getType().name().toLowerCase().replace('_', ' ');
    }
}