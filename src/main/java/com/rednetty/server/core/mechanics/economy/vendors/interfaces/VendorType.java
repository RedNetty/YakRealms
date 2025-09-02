package com.rednetty.server.core.mechanics.economy.vendors.interfaces;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Abstract representation of a vendor type
 * 
 * This interface defines the properties and capabilities of different vendor types,
 * allowing for a flexible and extensible vendor system.
 */
public interface VendorType {
    
    /**
     * Get the unique identifier for this vendor type
     * 
     * @return The type name (e.g., "item", "fisherman", "banker")
     */
    String getTypeName();
    
    /**
     * Get the display name for this vendor type
     * 
     * @return The human-readable display name
     */
    Component getDisplayName();
    
    /**
     * Get a description of what this vendor offers
     * 
     * @return The vendor description
     */
    Component getDescription();
    
    /**
     * Create a menu instance for this vendor type
     * 
     * @param player The player who will use the menu
     * @return A new menu instance
     */
    VendorMenuInterface createMenu(Player player);
    
    /**
     * Get the permissions required to access this vendor
     * 
     * @return Set of permission nodes required
     */
    Set<String> getRequiredPermissions();
    
    /**
     * Get the icon material for this vendor type
     * 
     * @return The material to use as an icon
     */
    Material getIcon();
    
    /**
     * Get the minimum level required to access this vendor
     * 
     * @return The minimum level, or 0 if no requirement
     */
    int getMinimumLevel();
    
    /**
     * Check if this vendor type is enabled
     * 
     * @return true if enabled
     */
    boolean isEnabled();
    
    /**
     * Get the priority for this vendor type (for sorting)
     * 
     * @return The priority value (lower = higher priority)
     */
    int getPriority();
    
    /**
     * Get the category this vendor belongs to
     * 
     * @return The vendor category
     */
    VendorCategory getCategory();
    
    /**
     * Check if a player can access this vendor type
     * 
     * @param player The player to check
     * @return true if the player can access this vendor
     */
    default boolean canAccess(Player player) {
        if (!isEnabled()) {
            return false;
        }
        
        // Check level requirement
        if (getMinimumLevel() > 0 && player.getLevel() < getMinimumLevel()) {
            return false;
        }
        
        // Check permissions
        Set<String> requiredPerms = getRequiredPermissions();
        if (!requiredPerms.isEmpty()) {
            for (String permission : requiredPerms) {
                if (!player.hasPermission(permission)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Get configuration-specific data for this vendor type
     * 
     * @return Configuration data as a generic object
     */
    Object getConfigurationData();
    
    /**
     * Vendor categories for organization
     */
    enum VendorCategory {
        TRADING("Trading & Commerce"),
        UTILITIES("Utilities & Services"),
        ENHANCEMENT("Item Enhancement"),
        SPECIALTY("Specialty Vendors");
        
        private final String displayName;
        
        VendorCategory(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}