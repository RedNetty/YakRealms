package com.rednetty.server.core.mechanics.economy.vendors.strategies;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Strategy interface for different pricing mechanisms
 * 
 * This allows different vendor types to use different pricing algorithms
 * while maintaining a consistent interface.
 */
public interface PricingStrategy {
    
    /**
     * Calculate the price for an item for a specific player
     * 
     * @param item The item being priced
     * @param player The player purchasing (for discounts, etc.)
     * @param basePrice The base price before modifications
     * @return The final price
     */
    int calculatePrice(ItemStack item, Player player, int basePrice);
    
    /**
     * Check if this pricing strategy uses dynamic pricing
     * 
     * @return true if prices change over time
     */
    boolean hasDynamicPricing();
    
    /**
     * Get the display format for prices from this strategy
     * 
     * @param price The price to format
     * @return The formatted price string
     */
    String formatPrice(int price);
    
    /**
     * Get the pricing strategy name for configuration
     * 
     * @return The strategy name
     */
    String getStrategyName();
    
    /**
     * Check if bulk discounts are available
     * 
     * @return true if bulk pricing is supported
     */
    boolean supportsBulkPricing();
    
    /**
     * Calculate bulk pricing if supported
     * 
     * @param item The item being purchased
     * @param player The player purchasing
     * @param basePrice The base price per item
     * @param quantity The quantity being purchased
     * @return The total price for the quantity
     */
    default int calculateBulkPrice(ItemStack item, Player player, int basePrice, int quantity) {
        if (!supportsBulkPricing()) {
            return calculatePrice(item, player, basePrice) * quantity;
        }
        return calculatePrice(item, player, basePrice) * quantity;
    }
    
    /**
     * Get any applicable discounts for a player
     * 
     * @param player The player to check discounts for
     * @return The discount multiplier (1.0 = no discount, 0.9 = 10% discount)
     */
    default double getPlayerDiscount(Player player) {
        return 1.0;
    }
}