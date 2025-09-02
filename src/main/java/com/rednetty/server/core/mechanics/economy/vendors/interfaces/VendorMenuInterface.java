package com.rednetty.server.core.mechanics.economy.vendors.interfaces;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Core interface for all vendor menu implementations
 * 
 * This interface defines the contract that all vendor menus must follow,
 * providing a consistent API for menu operations and lifecycle management.
 */
public interface VendorMenuInterface {
    
    /**
     * Open the vendor menu for the specified player
     * 
     * @param player The player to open the menu for
     * @return CompletableFuture that completes when the menu is opened
     */
    CompletableFuture<Boolean> open(Player player);
    
    /**
     * Close the vendor menu for the specified player
     * 
     * @param player The player to close the menu for
     */
    void close(Player player);
    
    /**
     * Refresh the menu contents (prices, availability, etc.)
     * 
     * @return CompletableFuture that completes when refresh is done
     */
    CompletableFuture<Void> refresh();
    
    /**
     * Check if a player can access this vendor menu
     * 
     * @param player The player to check access for
     * @return true if the player can access this menu
     */
    boolean canAccess(Player player);
    
    /**
     * Get the vendor type this menu represents
     * 
     * @return The vendor type
     */
    VendorType getVendorType();
    
    /**
     * Check if the menu is currently open for a player
     * 
     * @param player The player to check
     * @return true if the menu is open for this player
     */
    boolean isOpen(Player player);
    
    /**
     * Get the display name of this vendor menu
     * 
     * @return The display name
     */
    String getDisplayName();
    
    /**
     * Cleanup resources when the menu is no longer needed
     */
    void cleanup();
    
    /**
     * Handle a player interaction within the menu
     * 
     * @param player The player who interacted
     * @param slot The slot that was clicked
     * @return true if the interaction was handled
     */
    boolean handleInteraction(Player player, int slot);
    
    /**
     * Get menu statistics for monitoring
     * 
     * @return Menu statistics
     */
    VendorMenuStats getStats();
    
    /**
     * Statistics data class for vendor menus
     */
    class VendorMenuStats {
        private final String menuType;
        private final int currentlyOpen;
        private final long totalInteractions;
        private final long lastAccessed;
        
        public VendorMenuStats(String menuType, int currentlyOpen, long totalInteractions, long lastAccessed) {
            this.menuType = menuType;
            this.currentlyOpen = currentlyOpen;
            this.totalInteractions = totalInteractions;
            this.lastAccessed = lastAccessed;
        }
        
        public String getMenuType() { return menuType; }
        public int getCurrentlyOpen() { return currentlyOpen; }
        public long getTotalInteractions() { return totalInteractions; }
        public long getLastAccessed() { return lastAccessed; }
        
        @Override
        public String toString() {
            return String.format("VendorMenuStats{type='%s', open=%d, interactions=%d, lastAccessed=%d}",
                menuType, currentlyOpen, totalInteractions, lastAccessed);
        }
    }
}