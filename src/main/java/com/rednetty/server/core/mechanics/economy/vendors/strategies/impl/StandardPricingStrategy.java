package com.rednetty.server.core.mechanics.economy.vendors.strategies.impl;

import com.rednetty.server.core.mechanics.economy.vendors.strategies.PricingStrategy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;

/**
 * Standard pricing strategy implementation
 * 
 * This strategy provides basic pricing with optional dynamic variations
 * and player-specific discounts.
 */
public class StandardPricingStrategy implements PricingStrategy {
    
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");
    private static final DecimalFormat LARGE_NUMBER_FORMAT = new DecimalFormat("#,###.#");
    
    private final boolean enableDynamicPricing;
    private final double dynamicVariation;
    private final boolean enableBulkDiscounts;
    
    public StandardPricingStrategy(boolean enableDynamicPricing, double dynamicVariation, boolean enableBulkDiscounts) {
        this.enableDynamicPricing = enableDynamicPricing;
        this.dynamicVariation = Math.max(0.0, Math.min(0.5, dynamicVariation)); // Clamp between 0% and 50%
        this.enableBulkDiscounts = enableBulkDiscounts;
    }
    
    public StandardPricingStrategy() {
        this(false, 0.1, false);
    }
    
    @Override
    public int calculatePrice(ItemStack item, Player player, int basePrice) {
        if (basePrice <= 0) {
            return 0;
        }
        
        double finalPrice = basePrice;
        
        // Apply dynamic pricing if enabled
        if (enableDynamicPricing) {
            finalPrice = applyDynamicPricing(finalPrice);
        }
        
        // Apply player-specific discount
        double discount = getPlayerDiscount(player);
        finalPrice *= discount;
        
        return Math.max(1, (int) Math.round(finalPrice));
    }
    
    @Override
    public boolean hasDynamicPricing() {
        return enableDynamicPricing;
    }
    
    @Override
    public String formatPrice(int price) {
        return formatCurrency(price);
    }
    
    @Override
    public String getStrategyName() {
        return "standard";
    }
    
    @Override
    public boolean supportsBulkPricing() {
        return enableBulkDiscounts;
    }
    
    @Override
    public int calculateBulkPrice(ItemStack item, Player player, int basePrice, int quantity) {
        if (!supportsBulkPricing() || quantity <= 1) {
            return calculatePrice(item, player, basePrice) * quantity;
        }
        
        double unitPrice = calculatePrice(item, player, basePrice);
        double bulkDiscount = getBulkDiscount(quantity);
        
        return Math.max(quantity, (int) Math.round(unitPrice * quantity * bulkDiscount));
    }
    
    @Override
    public double getPlayerDiscount(Player player) {
        double discount = 1.0;
        
        // VIP discount
        if (player.hasPermission("yakrealms.vendor.discount.vip")) {
            discount *= 0.95; // 5% discount
        }
        
        // Premium discount
        if (player.hasPermission("yakrealms.vendor.discount.premium")) {
            discount *= 0.90; // 10% discount
        }
        
        // Staff discount
        if (player.hasPermission("yakrealms.vendor.discount.staff")) {
            discount *= 0.75; // 25% discount
        }
        
        return discount;
    }
    
    /**
     * Apply dynamic pricing variations
     * 
     * @param basePrice The base price
     * @return The modified price
     */
    private double applyDynamicPricing(double basePrice) {
        if (!enableDynamicPricing || dynamicVariation <= 0) {
            return basePrice;
        }
        
        // Use time-based variation for consistency
        long currentTime = System.currentTimeMillis();
        double timeFactor = Math.sin(currentTime / 3600000.0) * dynamicVariation; // Hourly cycle
        
        // Add some randomness but keep it stable for short periods
        long stableRandom = (currentTime / 600000) % 100; // 10-minute stable periods
        double randomFactor = (stableRandom / 100.0 - 0.5) * dynamicVariation * 0.5;
        
        double priceMultiplier = 1.0 + timeFactor + randomFactor;
        
        // Ensure we don't go below 50% or above 150% of base price
        priceMultiplier = Math.max(0.5, Math.min(1.5, priceMultiplier));
        
        return basePrice * priceMultiplier;
    }
    
    /**
     * Calculate bulk discount based on quantity
     * 
     * @param quantity The quantity being purchased
     * @return The discount multiplier (1.0 = no discount)
     */
    private double getBulkDiscount(int quantity) {
        if (quantity >= 64) {
            return 0.90; // 10% discount for full stack
        } else if (quantity >= 32) {
            return 0.95; // 5% discount for half stack
        } else if (quantity >= 16) {
            return 0.98; // 2% discount for quarter stack
        }
        
        return 1.0; // No discount
    }
    
    /**
     * Format currency with proper styling
     * 
     * @param amount The amount to format
     * @return The formatted currency string
     */
    private String formatCurrency(long amount) {
        String formatted;
        
        if (amount >= 1000000) {
            formatted = LARGE_NUMBER_FORMAT.format(amount / 1000000.0) + "M";
        } else if (amount >= 1000) {
            formatted = NUMBER_FORMAT.format(amount);
        } else {
            formatted = String.valueOf(amount);
        }
        
        return formatted + "g";
    }
    
    /**
     * Get a description of this pricing strategy
     * 
     * @return Strategy description
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder("Standard pricing");
        
        if (enableDynamicPricing) {
            desc.append(" with dynamic variations (±").append((int)(dynamicVariation * 100)).append("%)");
        }
        
        if (enableBulkDiscounts) {
            desc.append(", bulk discounts available");
        }
        
        return desc.toString();
    }
}