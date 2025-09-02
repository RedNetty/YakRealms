package com.rednetty.server.core.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.economy.vendors.interfaces.VendorMenuInterface;
import com.rednetty.server.core.mechanics.economy.vendors.interfaces.VendorType;
import com.rednetty.server.core.mechanics.economy.vendors.registry.VendorTypeRegistry;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.menu.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced Vendor Menu Manager - Centralized and clean vendor system management
 * 
 * Features:
 * - Registry-based vendor type management
 * - Async menu operations with CompletableFuture
 * - Permission and access control integration
 * - Menu caching and optimization
 * - Clean separation of concerns
 * - Comprehensive error handling
 * - Non-italic text enforcement
 * - Support for both new and legacy vendor systems
 */
public class VendorMenuManager {
    private static VendorMenuManager instance;
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final YakPlayerManager playerManager;
    
    // Menu caching for performance
    private final ConcurrentHashMap<String, Long> menuCooldowns = new ConcurrentHashMap<>();
    private static final long MENU_COOLDOWN_MS = 500; // 0.5 second cooldown between menu opens
    
    // Registry for vendor types
    private final VendorTypeRegistry vendorRegistry;
    
    private VendorMenuManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.playerManager = YakPlayerManager.getInstance();
        this.vendorRegistry = VendorTypeRegistry.getInstance();
    }
    
    public static void initialize(JavaPlugin plugin) {
        if (instance == null) {
            instance = new VendorMenuManager(plugin);
        }
    }
    
    public static VendorMenuManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VendorMenuManager not initialized!");
        }
        return instance;
    }
    
    /**
     * Open vendor menu with validation and error handling
     */
    public boolean openVendorMenu(Player player, String vendorTypeName) {
        if (!validateMenuRequest(player, vendorTypeName)) {
            return false;
        }
        
        return openVendorMenuAsync(player, vendorTypeName)
                .handle((success, throwable) -> {
                    if (throwable != null) {
                        logger.log(Level.SEVERE, "Error opening vendor menu for " + player.getName(), throwable);
                        player.sendMessage(createNonItalicComponent("An error occurred while opening the vendor menu.", NamedTextColor.RED));
                        return false;
                    }
                    return success;
                })
                .join();
    }
    
    /**
     * Async version of vendor menu opening
     */
    public CompletableFuture<Boolean> openVendorMenuAsync(Player player, String vendorTypeName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cooldown
                String playerId = player.getUniqueId().toString();
                Long lastOpen = menuCooldowns.get(playerId);
                if (lastOpen != null && System.currentTimeMillis() - lastOpen < MENU_COOLDOWN_MS) {
                    player.sendMessage(createNonItalicComponent("Please wait before opening another menu", NamedTextColor.YELLOW));
                    return false;
                }
                
                // Get vendor type from registry
                VendorType vendorType = vendorRegistry.getVendorType(vendorTypeName);
                if (vendorType == null) {
                    player.sendMessage(createNonItalicComponent("This vendor type is not available.", NamedTextColor.RED));
                    return false;
                }
                
                // Check if vendor is enabled and accessible
                if (!vendorType.isEnabled() || !vendorType.canAccess(player)) {
                    player.sendMessage(createNonItalicComponent("You don't have access to this vendor.", NamedTextColor.RED));
                    return false;
                }
                
                // Get YakPlayer for enhanced features
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer == null) {
                    player.sendMessage(createNonItalicComponent("Your player data is still loading. Please wait a moment.", NamedTextColor.YELLOW));
                    return false;
                }
                
                // Create menu using vendor type
                VendorMenuInterface vendorMenu = vendorType.createMenu(player);
                
                // Open menu and update cooldown
                return vendorMenu.open(player).handle((opened, throwable) -> {
                    if (throwable != null) {
                        logger.log(Level.WARNING, "Failed to open vendor menu", throwable);
                        return false;
                    }
                    
                    if (opened) {
                        menuCooldowns.put(playerId, System.currentTimeMillis());
                        logger.fine("Opened " + vendorTypeName + " menu for player " + player.getName());
                    }
                    
                    return opened;
                }).join();
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in async vendor menu opening", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Validate menu request
     */
    private boolean validateMenuRequest(Player player, String vendorTypeName) {
        if (player == null) {
            logger.warning("Null player attempted to open vendor menu");
            return false;
        }
        
        if (!player.isOnline()) {
            logger.warning("Offline player " + player.getName() + " attempted to open vendor menu");
            return false;
        }
        
        if (vendorTypeName == null || vendorTypeName.trim().isEmpty()) {
            player.sendMessage(createNonItalicComponent("Invalid vendor type.", NamedTextColor.RED));
            return false;
        }
        
        // Check basic permissions
        if (!player.hasPermission("yakrealms.vendor.use")) {
            player.sendMessage(createNonItalicComponent("You don't have permission to use vendors.", NamedTextColor.RED));
            return false;
        }
        
        return true;
    }
    
    
    /**
     * Create non-italic component for consistent text display
     */
    private Component createNonItalicComponent(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    
    /**
     * Get available vendor types for a player
     */
    public List<String> getAvailableVendorTypes(Player player) {
        return vendorRegistry.getAccessibleVendorTypes(player)
                .stream()
                .map(VendorType::getTypeName)
                .collect(Collectors.toList());
    }
    
    /**
     * Get available vendor types by category
     */
    public List<VendorType> getVendorTypesByCategory(Player player, VendorType.VendorCategory category) {
        return vendorRegistry.getAccessibleVendorTypesByCategory(player, category);
    }
    
    /**
     * Get all available categories for a player
     */
    public List<VendorType.VendorCategory> getAvailableCategories(Player player) {
        return vendorRegistry.getAccessibleVendorTypes(player)
                .stream()
                .map(VendorType::getCategory)
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * Clean up player data on disconnect
     */
    public void cleanupPlayer(Player player) {
        if (player != null) {
            menuCooldowns.remove(player.getUniqueId().toString());
        }
    }
    
    /**
     * Get vendor menu statistics
     */
    public VendorMenuStats getStats() {
        VendorTypeRegistry.RegistryStats registryStats = vendorRegistry.getStats();
        return new VendorMenuStats(
            menuCooldowns.size(),
            registryStats.getTotalTypes(),
            registryStats.getEnabledTypes(),
            registryStats.getCategories()
        );
    }
    
    
    /**
     * Statistics class for vendor menu system
     */
    public static class VendorMenuStats {
        private final int activePlayers;
        private final int totalVendorTypes;
        private final int enabledVendorTypes;
        private final int categories;
        
        public VendorMenuStats(int activePlayers, int totalVendorTypes, int enabledVendorTypes, int categories) {
            this.activePlayers = activePlayers;
            this.totalVendorTypes = totalVendorTypes;
            this.enabledVendorTypes = enabledVendorTypes;
            this.categories = categories;
        }
        
        public int getActivePlayers() { return activePlayers; }
        public int getTotalVendorTypes() { return totalVendorTypes; }
        public int getEnabledVendorTypes() { return enabledVendorTypes; }
        public int getCategories() { return categories; }
        public int getDisabledVendorTypes() { return totalVendorTypes - enabledVendorTypes; }
        
        @Override
        public String toString() {
            return String.format("VendorMenuStats{activePlayers=%d, totalTypes=%d, enabledTypes=%d, categories=%d}", 
                activePlayers, totalVendorTypes, enabledVendorTypes, categories);
        }
    }
}