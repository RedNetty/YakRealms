package com.rednetty.server.core.mechanics.economy.vendors.strategies;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

/**
 * Strategy interface for different purchase mechanisms
 * 
 * This allows different vendor types to handle purchases in different ways
 * while maintaining a consistent interface for the purchase flow.
 */
public interface PurchaseStrategy {
    
    /**
     * Check if a player can purchase an item
     * 
     * @param player The player attempting to purchase
     * @param item The item being purchased
     * @param quantity The quantity to purchase
     * @param totalPrice The total price for the purchase
     * @return A purchase validation result
     */
    PurchaseValidation canPurchase(Player player, ItemStack item, int quantity, int totalPrice);
    
    /**
     * Process the actual purchase transaction
     * 
     * @param player The player making the purchase
     * @param item The item being purchased
     * @param quantity The quantity to purchase
     * @param totalPrice The total price for the purchase
     * @return CompletableFuture with the purchase result
     */
    CompletableFuture<PurchaseResult> processPurchase(Player player, ItemStack item, int quantity, int totalPrice);
    
    /**
     * Get the currency type this strategy uses
     * 
     * @return The currency type
     */
    CurrencyType getCurrencyType();
    
    /**
     * Get the strategy name for identification
     * 
     * @return The strategy name
     */
    String getStrategyName();
    
    /**
     * Check if this strategy supports quantity selection
     * 
     * @return true if quantity selection is supported
     */
    boolean supportsQuantitySelection();
    
    /**
     * Get the maximum quantity that can be purchased at once
     * 
     * @param player The player purchasing
     * @param item The item being purchased
     * @return The maximum quantity, or -1 for unlimited
     */
    default int getMaxQuantity(Player player, ItemStack item) {
        return 64; // Default to a full stack
    }
    
    /**
     * Handle purchase cancellation/rollback if needed
     * 
     * @param player The player whose purchase was cancelled
     * @param item The item that was being purchased
     * @param quantity The quantity that was being purchased
     * @param refundAmount The amount to refund
     * @return CompletableFuture with rollback result
     */
    CompletableFuture<Boolean> rollbackPurchase(Player player, ItemStack item, int quantity, int refundAmount);
    
    /**
     * Currency types supported by purchase strategies
     */
    enum CurrencyType {
        GEMS("Gems", "g"),
        EXPERIENCE("Experience", "XP"),
        ITEMS("Items", "items"),
        CUSTOM("Custom", "");
        
        private final String displayName;
        private final String symbol;
        
        CurrencyType(String displayName, String symbol) {
            this.displayName = displayName;
            this.symbol = symbol;
        }
        
        public String getDisplayName() { return displayName; }
        public String getSymbol() { return symbol; }
    }
    
    /**
     * Purchase validation result
     */
    class PurchaseValidation {
        private final boolean valid;
        private final String errorMessage;
        private final ErrorType errorType;
        
        public PurchaseValidation(boolean valid, String errorMessage, ErrorType errorType) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.errorType = errorType;
        }
        
        public static PurchaseValidation success() {
            return new PurchaseValidation(true, null, null);
        }
        
        public static PurchaseValidation failure(String message, ErrorType type) {
            return new PurchaseValidation(false, message, type);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public ErrorType getErrorType() { return errorType; }
        
        public enum ErrorType {
            INSUFFICIENT_FUNDS,
            INSUFFICIENT_SPACE,
            ITEM_NOT_AVAILABLE,
            PERMISSION_DENIED,
            COOLDOWN_ACTIVE,
            INVALID_QUANTITY,
            SYSTEM_ERROR
        }
    }
    
    /**
     * Purchase result
     */
    class PurchaseResult {
        private final boolean success;
        private final String message;
        private final ItemStack purchasedItem;
        private final int quantityPurchased;
        private final int totalCost;
        
        public PurchaseResult(boolean success, String message, ItemStack purchasedItem, int quantityPurchased, int totalCost) {
            this.success = success;
            this.message = message;
            this.purchasedItem = purchasedItem;
            this.quantityPurchased = quantityPurchased;
            this.totalCost = totalCost;
        }
        
        public static PurchaseResult success(String message, ItemStack item, int quantity, int cost) {
            return new PurchaseResult(true, message, item, quantity, cost);
        }
        
        public static PurchaseResult failure(String message) {
            return new PurchaseResult(false, message, null, 0, 0);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public ItemStack getPurchasedItem() { return purchasedItem; }
        public int getQuantityPurchased() { return quantityPurchased; }
        public int getTotalCost() { return totalCost; }
    }
}