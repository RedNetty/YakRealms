package com.rednetty.server.core.mechanics.economy.vendors.base;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Configuration class for vendor menus
 * 
 * This class holds all configurable aspects of vendor menus,
 * allowing for easy customization without code changes.
 */
public class VendorMenuConfig {
    
    // Menu Layout Configuration
    private final int menuSize;
    private final String title;
    private final List<Integer> categorySlots;
    private final List<Integer> displaySlots;
    private final List<Integer> navigationSlots;
    private final List<Integer> borderSlots;
    
    // UI Elements
    private final Material borderMaterial;
    private final Material separatorMaterial;
    private final String borderName;
    
    // Behavior Configuration
    private final long clickCooldownMs;
    private final boolean enableSounds;
    private final boolean enableParticles;
    private final int maxItemsPerPage;
    
    // Pricing Configuration
    private final boolean enableDynamicPricing;
    private final double dynamicPriceVariation;
    private final Map<String, Integer> basePrices;
    
    // Messages Configuration
    private final VendorMessages messages;
    
    public VendorMenuConfig(Builder builder) {
        this.menuSize = builder.menuSize;
        this.title = builder.title;
        this.categorySlots = builder.categorySlots;
        this.displaySlots = builder.displaySlots;
        this.navigationSlots = builder.navigationSlots;
        this.borderSlots = builder.borderSlots;
        this.borderMaterial = builder.borderMaterial;
        this.separatorMaterial = builder.separatorMaterial;
        this.borderName = builder.borderName;
        this.clickCooldownMs = builder.clickCooldownMs;
        this.enableSounds = builder.enableSounds;
        this.enableParticles = builder.enableParticles;
        this.maxItemsPerPage = builder.maxItemsPerPage;
        this.enableDynamicPricing = builder.enableDynamicPricing;
        this.dynamicPriceVariation = builder.dynamicPriceVariation;
        this.basePrices = builder.basePrices;
        this.messages = builder.messages;
    }
    
    // Getters
    public int getMenuSize() { return menuSize; }
    public String getTitle() { return title; }
    public List<Integer> getCategorySlots() { return categorySlots; }
    public List<Integer> getDisplaySlots() { return displaySlots; }
    public List<Integer> getNavigationSlots() { return navigationSlots; }
    public List<Integer> getBorderSlots() { return borderSlots; }
    public Material getBorderMaterial() { return borderMaterial; }
    public Material getSeparatorMaterial() { return separatorMaterial; }
    public String getBorderName() { return borderName; }
    public long getClickCooldownMs() { return clickCooldownMs; }
    public boolean isEnableSounds() { return enableSounds; }
    public boolean isEnableParticles() { return enableParticles; }
    public int getMaxItemsPerPage() { return maxItemsPerPage; }
    public boolean isEnableDynamicPricing() { return enableDynamicPricing; }
    public double getDynamicPriceVariation() { return dynamicPriceVariation; }
    public Map<String, Integer> getBasePrices() { return basePrices; }
    public VendorMessages getMessages() { return messages; }
    
    /**
     * Builder pattern for VendorMenuConfig
     */
    public static class Builder {
        // Required parameters
        private int menuSize = 54;
        private String title = "Vendor";
        
        // Optional parameters with defaults
        private List<Integer> categorySlots = List.of(0, 9, 18, 27, 36, 45);
        private List<Integer> displaySlots = List.of(11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33, 34);
        private List<Integer> navigationSlots = List.of(49, 50, 51, 52, 53);
        private List<Integer> borderSlots = List.of(1, 2, 3, 5, 6, 7, 8, 10, 17, 19, 26, 28, 35, 37, 44, 46, 47, 48);
        
        private Material borderMaterial = Material.GRAY_STAINED_GLASS_PANE;
        private Material separatorMaterial = Material.BLACK_STAINED_GLASS_PANE;
        private String borderName = " ";
        
        private long clickCooldownMs = 250;
        private boolean enableSounds = true;
        private boolean enableParticles = false;
        private int maxItemsPerPage = 18;
        
        private boolean enableDynamicPricing = false;
        private double dynamicPriceVariation = 0.1;
        private Map<String, Integer> basePrices = Map.of();
        
        private VendorMessages messages = new VendorMessages();
        
        public Builder menuSize(int menuSize) {
            this.menuSize = menuSize;
            return this;
        }
        
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder categorySlots(List<Integer> categorySlots) {
            this.categorySlots = categorySlots;
            return this;
        }
        
        public Builder displaySlots(List<Integer> displaySlots) {
            this.displaySlots = displaySlots;
            return this;
        }
        
        public Builder navigationSlots(List<Integer> navigationSlots) {
            this.navigationSlots = navigationSlots;
            return this;
        }
        
        public Builder borderSlots(List<Integer> borderSlots) {
            this.borderSlots = borderSlots;
            return this;
        }
        
        public Builder borderMaterial(Material borderMaterial) {
            this.borderMaterial = borderMaterial;
            return this;
        }
        
        public Builder separatorMaterial(Material separatorMaterial) {
            this.separatorMaterial = separatorMaterial;
            return this;
        }
        
        public Builder borderName(String borderName) {
            this.borderName = borderName;
            return this;
        }
        
        public Builder clickCooldownMs(long clickCooldownMs) {
            this.clickCooldownMs = clickCooldownMs;
            return this;
        }
        
        public Builder enableSounds(boolean enableSounds) {
            this.enableSounds = enableSounds;
            return this;
        }
        
        public Builder enableParticles(boolean enableParticles) {
            this.enableParticles = enableParticles;
            return this;
        }
        
        public Builder maxItemsPerPage(int maxItemsPerPage) {
            this.maxItemsPerPage = maxItemsPerPage;
            return this;
        }
        
        public Builder enableDynamicPricing(boolean enableDynamicPricing) {
            this.enableDynamicPricing = enableDynamicPricing;
            return this;
        }
        
        public Builder dynamicPriceVariation(double dynamicPriceVariation) {
            this.dynamicPriceVariation = dynamicPriceVariation;
            return this;
        }
        
        public Builder basePrices(Map<String, Integer> basePrices) {
            this.basePrices = basePrices;
            return this;
        }
        
        public Builder messages(VendorMessages messages) {
            this.messages = messages;
            return this;
        }
        
        public VendorMenuConfig build() {
            return new VendorMenuConfig(this);
        }
    }
    
    /**
     * Messages configuration for vendor menus
     */
    public static class VendorMessages {
        private String insufficientFunds = "⚠ INSUFFICIENT FUNDS ⚠";
        private String purchaseSuccess = "✅ PURCHASE SUCCESSFUL! ✅";
        private String purchaseConfirm = "Click to confirm purchase for {price}";
        private String accessDenied = "❌ ACCESS DENIED ❌";
        private String itemNotAvailable = "⚠ ITEM NOT AVAILABLE ⚠";
        private String inventoryFull = "⚠ INVENTORY FULL ⚠";
        private String systemError = "❌ SYSTEM ERROR - PLEASE TRY AGAIN ❌";
        
        // Getters and Setters
        public String getInsufficientFunds() { return insufficientFunds; }
        public void setInsufficientFunds(String insufficientFunds) { this.insufficientFunds = insufficientFunds; }
        
        public String getPurchaseSuccess() { return purchaseSuccess; }
        public void setPurchaseSuccess(String purchaseSuccess) { this.purchaseSuccess = purchaseSuccess; }
        
        public String getPurchaseConfirm() { return purchaseConfirm; }
        public void setPurchaseConfirm(String purchaseConfirm) { this.purchaseConfirm = purchaseConfirm; }
        
        public String getAccessDenied() { return accessDenied; }
        public void setAccessDenied(String accessDenied) { this.accessDenied = accessDenied; }
        
        public String getItemNotAvailable() { return itemNotAvailable; }
        public void setItemNotAvailable(String itemNotAvailable) { this.itemNotAvailable = itemNotAvailable; }
        
        public String getInventoryFull() { return inventoryFull; }
        public void setInventoryFull(String inventoryFull) { this.inventoryFull = inventoryFull; }
        
        public String getSystemError() { return systemError; }
        public void setSystemError(String systemError) { this.systemError = systemError; }
    }
}