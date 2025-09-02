package com.rednetty.server.core.mechanics.economy.vendors.types;

import com.rednetty.server.core.mechanics.economy.EconomyManager;
import com.rednetty.server.core.mechanics.economy.vendors.interfaces.VendorMenuInterface;
import com.rednetty.server.core.mechanics.economy.vendors.interfaces.VendorType;
import com.rednetty.server.core.mechanics.economy.vendors.menus.enhanced.EnhancedItemVendorMenu;
import com.rednetty.server.core.mechanics.economy.vendors.base.VendorMenuConfig;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Item vendor type implementation
 * 
 * This vendor type provides access to various items for purchase using gems.
 * It supports categorized browsing, quantity selection, and dynamic pricing.
 */
public class ItemVendorType implements VendorType {
    
    private final EconomyManager economyManager;
    private final YakPlayerManager playerManager;
    private final Logger logger;
    private final boolean enabled;
    
    public ItemVendorType(EconomyManager economyManager, YakPlayerManager playerManager, Logger logger) {
        this.economyManager = economyManager;
        this.playerManager = playerManager;
        this.logger = logger;
        this.enabled = true;
    }
    
    @Override
    public String getTypeName() {
        return "item";
    }
    
    @Override
    public Component getDisplayName() {
        return Component.text("Item Vendor")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
    }
    
    @Override
    public Component getDescription() {
        return Component.text("Purchase various items using gems")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }
    
    @Override
    public Material getIcon() {
        return Material.CHEST;
    }
    
    @Override
    public VendorCategory getCategory() {
        return VendorCategory.TRADING;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority for main vendor
    }
    
    @Override
    public boolean canAccess(Player player) {
        // Admin bypass
        if (player.hasPermission("yakrealms.vendor.admin")) {
            return true;
        }
        
        // Check specific permission
        if (!player.hasPermission("yakrealms.vendor.item")) {
            return false;
        }
        
        // Check if player data is available
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            return false;
        }
        
        // Additional checks can be added here (level requirements, etc.)
        return true;
    }
    
    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("yakrealms.vendor.item");
    }
    
    @Override
    public int getMinimumLevel() {
        return 0; // No level requirement
    }
    
    @Override
    public VendorMenuInterface createMenu(Player player) {
        // Create default configuration
        VendorMenuConfig config = new VendorMenuConfig.Builder()
                .title("Item Vendor")
                .enableDynamicPricing(false)
                .build();
        
        return new EnhancedItemVendorMenu(player, this, config);
    }
    
    @Override
    public Object getConfigurationData() {
        // Return configuration data for this vendor type
        return new ItemVendorConfig();
    }
    
    /**
     * Configuration data for item vendor
     */
    private static class ItemVendorConfig {
        private final boolean dynamicPricing = false;
        private final double priceVariation = 0.1;
        private final List<String> categories = List.of("tools", "materials", "consumables");
        
        public boolean isDynamicPricing() { return dynamicPricing; }
        public double getPriceVariation() { return priceVariation; }
        public List<String> getCategories() { return categories; }
    }
    
    
    @Override
    public String toString() {
        return "ItemVendorType{" +
                "typeName='" + getTypeName() + '\'' +
                ", displayName='" + getDisplayName() + '\'' +
                ", enabled=" + enabled +
                ", priority=" + getPriority() +
                '}';
    }
}