package com.rednetty.server.core.mechanics.economy.vendors;

import com.rednetty.server.core.mechanics.economy.vendors.behaviors.*;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced Vendor Interaction Handler - Clean and efficient vendor system
 * 
 * Features:
 * - Direct integration with VendorMenuManager
 * - Improved error handling and logging
 * - Player cleanup on disconnect
 * - Rate limiting for interactions
 * - Non-italic text compliance
 * - Simplified architecture without behavior classes
 */
public class VendorInteractionHandler implements Listener {
    private final VendorManager vendorManager;
    private final VendorMenuManager menuManager;
    private final Logger logger;
    
    // Rate limiting
    private final ConcurrentHashMap<String, Long> lastInteraction = new ConcurrentHashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 1000; // 1 second between interactions
    
    public VendorInteractionHandler(JavaPlugin plugin) {
        this.vendorManager = VendorManager.getInstance();
        this.menuManager = VendorMenuManager.getInstance();
        this.logger = plugin.getLogger();
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("Enhanced Vendor Interaction Handler initialized");
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        
        if (player == null || !player.isOnline()) {
            return;
        }
        
        try {
            // Check rate limiting
            String playerId = player.getUniqueId().toString();
            Long lastClick = lastInteraction.get(playerId);
            if (lastClick != null && System.currentTimeMillis() - lastClick < INTERACTION_COOLDOWN_MS) {
                return; // Silent ignore for rate limiting
            }
            
            // Get vendor from NPC
            Vendor vendor = vendorManager.getVendorByNpcId(event.getNPC().getId());
            if (vendor == null) {
                return; // Not a vendor NPC, ignore
            }
            
            // Update rate limiting
            lastInteraction.put(playerId, System.currentTimeMillis());
            
            // Validate vendor type
            String vendorType = vendor.getVendorType();
            if (vendorType == null || vendorType.trim().isEmpty()) {
                sendNonItalicMessage(player, "This vendor is not properly configured.", NamedTextColor.RED);
                logger.warning("Vendor " + vendor.getId() + " has invalid vendor type");
                return;
            }
            
            // Try the new menu manager first
            boolean success = menuManager.openVendorMenu(player, vendorType);
            
            if (success) {
                logger.fine("Player " + player.getName() + " opened " + vendorType + " vendor menu");
            } else {
                // Fall back to legacy behavior system
                VendorBehavior behavior = getLegacyBehavior(vendorType);
                if (behavior != null) {
                    logger.fine("Using legacy behavior for " + vendorType + " vendor for " + player.getName());
                    behavior.onInteract(player);
                } else {
                    sendNonItalicMessage(player, "This vendor type is not available.", NamedTextColor.RED);
                    logger.warning("No handler found for vendor type: " + vendorType);
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling vendor interaction for " + player.getName(), e);
            sendNonItalicMessage(player, "An error occurred while interacting with this vendor.", NamedTextColor.RED);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            // Clean up player data
            lastInteraction.remove(player.getUniqueId().toString());
            menuManager.cleanupPlayer(player);
        }
    }
    
    /**
     * Get legacy behavior instance for vendor type (for backwards compatibility)
     */
    private VendorBehavior getLegacyBehavior(String vendorType) {
        switch (vendorType.toLowerCase()) {
            case "fisherman":
                return new FishermanBehavior();
            case "book":
                return new BookVendorBehavior();
            case "upgrade":
                return new UpgradeVendorBehavior();
            case "banker":
                return new BankerBehavior();
            case "medic":
                return new MedicBehavior();
            case "gambler":
                return new GamblerBehavior();
            case "mount":
                return new MountVendorBehavior();
            case "shop":
                return new ShopBehavior();
            default:
                return null;
        }
    }
    
    /**
     * Send non-italic message to player
     */
    private void sendNonItalicMessage(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message, color).decoration(TextDecoration.ITALIC, false));
    }
    
    /**
     * Get interaction statistics
     */
    public VendorInteractionStats getStats() {
        return new VendorInteractionStats(
            lastInteraction.size(),
            vendorManager.getAllVendors().size(),
            INTERACTION_COOLDOWN_MS
        );
    }
    
    /**
     * Statistics class for vendor interactions
     */
    public static class VendorInteractionStats {
        private final int activePlayerInteractions;
        private final int totalVendors;
        private final long cooldownMs;
        
        public VendorInteractionStats(int activePlayerInteractions, int totalVendors, long cooldownMs) {
            this.activePlayerInteractions = activePlayerInteractions;
            this.totalVendors = totalVendors;
            this.cooldownMs = cooldownMs;
        }
        
        public int getActivePlayerInteractions() { return activePlayerInteractions; }
        public int getTotalVendors() { return totalVendors; }
        public long getCooldownMs() { return cooldownMs; }
        
        @Override
        public String toString() {
            return String.format("VendorInteractionStats{activePlayerInteractions=%d, totalVendors=%d, cooldownMs=%d}",
                activePlayerInteractions, totalVendors, cooldownMs);
        }
    }
}