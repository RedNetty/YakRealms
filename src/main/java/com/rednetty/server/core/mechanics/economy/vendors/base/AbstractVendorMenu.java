package com.rednetty.server.core.mechanics.economy.vendors.base;

import com.rednetty.server.core.mechanics.economy.vendors.interfaces.VendorMenuInterface;
import com.rednetty.server.core.mechanics.economy.vendors.interfaces.VendorType;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.PricingStrategy;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.PurchaseStrategy;
import com.rednetty.server.utils.menu.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for all vendor menus
 * 
 * This class provides common functionality for all vendor menus including:
 * - Menu lifecycle management
 * - Player access control
 * - Error handling
 * - Statistics tracking
 * - Common UI elements
 */
public abstract class AbstractVendorMenu extends Menu implements VendorMenuInterface {
    
    protected final VendorType vendorType;
    protected final PricingStrategy pricingStrategy;
    protected final PurchaseStrategy purchaseStrategy;
    protected final Logger logger;
    
    // Tracking and statistics
    private final Set<UUID> currentlyOpen = ConcurrentHashMap.newKeySet();
    private long totalInteractions = 0;
    private long lastAccessed = 0;
    
    // Configuration
    protected final VendorMenuConfig config;
    
    public AbstractVendorMenu(Player player, VendorType vendorType, VendorMenuConfig config) {
        super(player, vendorType.getDisplayName().examinableName(), config.getMenuSize());
        
        this.vendorType = vendorType;
        this.config = config;
        this.logger = JavaPlugin.getProvidingPlugin(getClass()).getLogger();
        this.pricingStrategy = createPricingStrategy();
        this.purchaseStrategy = createPurchaseStrategy();
        
        this.lastAccessed = System.currentTimeMillis();
    }
    
    @Override
    public CompletableFuture<Boolean> open(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!canAccess(player)) {
                    sendErrorMessage(player, "You don't have access to this vendor.");
                    return false;
                }
                
                // Track opening
                currentlyOpen.add(player.getUniqueId());
                totalInteractions++;
                lastAccessed = System.currentTimeMillis();
                
                // Initialize menu content
                initializeMenu();
                
                // Open the menu
                super.open();
                
                logger.fine("Opened " + vendorType.getTypeName() + " menu for " + player.getName());
                return true;
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error opening vendor menu for " + player.getName(), e);
                sendErrorMessage(player, "An error occurred while opening the vendor menu.");
                return false;
            }
        });
    }
    
    @Override
    public void close(Player player) {
        try {
            currentlyOpen.remove(player.getUniqueId());
            player.closeInventory();
            
            // Cleanup player-specific resources
            onPlayerClose(player);
            
            logger.fine("Closed " + vendorType.getTypeName() + " menu for " + player.getName());
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error closing vendor menu for " + player.getName(), e);
        }
    }
    
    @Override
    public CompletableFuture<Void> refresh() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Clear current menu
                clearMenu();
                
                // Reinitialize content
                initializeMenu();
                
                logger.fine("Refreshed " + vendorType.getTypeName() + " menu");
                
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error refreshing vendor menu", e);
            }
        });
    }
    
    @Override
    public boolean canAccess(Player player) {
        return vendorType.canAccess(player);
    }
    
    @Override
    public VendorType getVendorType() {
        return vendorType;
    }
    
    @Override
    public boolean isOpen(Player player) {
        return currentlyOpen.contains(player.getUniqueId());
    }
    
    @Override
    public String getDisplayName() {
        return vendorType.getDisplayName().examinableName();
    }
    
    @Override
    public void cleanup() {
        try {
            // Close all open instances
            for (UUID playerId : currentlyOpen) {
                Player player = org.bukkit.Bukkit.getPlayer(playerId);
                if (player != null) {
                    close(player);
                }
            }
            
            currentlyOpen.clear();
            
            // Cleanup resources
            onCleanup();
            
            logger.fine("Cleaned up " + vendorType.getTypeName() + " menu");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during menu cleanup", e);
        }
    }
    
    @Override
    public VendorMenuStats getStats() {
        return new VendorMenuStats(
            vendorType.getTypeName(),
            currentlyOpen.size(),
            totalInteractions,
            lastAccessed
        );
    }
    
    @Override
    public boolean handleInteraction(Player player, int slot) {
        try {
            totalInteractions++;
            return onSlotClick(player, slot);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling interaction for " + player.getName() + " at slot " + slot, e);
            sendErrorMessage(player, "An error occurred while processing your interaction.");
            return false;
        }
    }
    
    // Abstract methods that subclasses must implement
    
    /**
     * Initialize the menu content (items, decorations, etc.)
     */
    protected abstract void initializeMenu();
    
    /**
     * Create the pricing strategy for this vendor menu
     * 
     * @return The pricing strategy to use
     */
    protected abstract PricingStrategy createPricingStrategy();
    
    /**
     * Create the purchase strategy for this vendor menu
     * 
     * @return The purchase strategy to use
     */
    protected abstract PurchaseStrategy createPurchaseStrategy();
    
    /**
     * Handle slot click events
     * 
     * @param player The player who clicked
     * @param slot The slot that was clicked
     * @return true if the interaction was handled
     */
    protected abstract boolean onSlotClick(Player player, int slot);
    
    // Protected helper methods for subclasses
    
    /**
     * Clear all items from the menu
     */
    protected void clearMenu() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }
    }
    
    /**
     * Send an error message to a player with consistent formatting
     * 
     * @param player The player to send the message to
     * @param message The error message
     */
    protected void sendErrorMessage(Player player, String message) {
        player.sendMessage(Component.text("❌ " + message, NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
    }
    
    /**
     * Send a success message to a player with consistent formatting
     * 
     * @param player The player to send the message to
     * @param message The success message
     */
    protected void sendSuccessMessage(Player player, String message) {
        player.sendMessage(Component.text("✅ " + message, NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
    }
    
    /**
     * Send an info message to a player with consistent formatting
     * 
     * @param player The player to send the message to
     * @param message The info message
     */
    protected void sendInfoMessage(Player player, String message) {
        player.sendMessage(Component.text("ℹ " + message, NamedTextColor.BLUE)
                .decoration(TextDecoration.ITALIC, false));
    }
    
    /**
     * Create a non-italic component with the specified text and color
     * 
     * @param text The text content
     * @param color The text color
     * @return The formatted component
     */
    protected Component createNonItalicText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    
    /**
     * Create a non-italic component with text, color, and decoration
     * 
     * @param text The text content
     * @param color The text color
     * @param decoration The text decoration
     * @return The formatted component
     */
    protected Component createNonItalicText(String text, NamedTextColor color, TextDecoration decoration) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(decoration, true);
    }
    
    // Hook methods for subclasses (optional to override)
    
    /**
     * Called when a player closes the menu
     * 
     * @param player The player who closed the menu
     */
    protected void onPlayerClose(Player player) {
        // Default: do nothing
    }
    
    /**
     * Called during cleanup
     */
    protected void onCleanup() {
        // Default: do nothing
    }
    
    // Getters for subclasses
    
    protected VendorMenuConfig getConfig() {
        return config;
    }
    
    protected PricingStrategy getPricingStrategy() {
        return pricingStrategy;
    }
    
    protected PurchaseStrategy getPurchaseStrategy() {
        return purchaseStrategy;
    }
    
    protected Logger getLogger() {
        return logger;
    }
}