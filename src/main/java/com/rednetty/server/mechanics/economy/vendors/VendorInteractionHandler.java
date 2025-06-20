package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.behaviors.VendorBehavior;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Enhanced VendorInteractionHandler with improved performance, error handling, and behavior management.
 * Handles player interactions with vendor NPCs using optimized cooldown management and robust
 * fallback behavior system with comprehensive error recovery.
 */
public class VendorInteractionHandler implements Listener {

    private final VendorManager vendorManager;
    private final JavaPlugin plugin;

    // Enhanced cooldown management with thread-safe operations
    private final Map<UUID, Long> interactionCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends VendorBehavior>> behaviorClassCache = new ConcurrentHashMap<>();
    private final Map<String, Long> behaviorCacheTime = new ConcurrentHashMap<>();

    // Performance tracking
    private final AtomicLong totalInteractions = new AtomicLong(0);
    private final AtomicLong successfulInteractions = new AtomicLong(0);
    private final AtomicLong failedInteractions = new AtomicLong(0);

    // Configuration
    private static final long COOLDOWN_MILLIS = 500; // 500ms cooldown between interactions
    private static final long BEHAVIOR_CACHE_DURATION = 300000; // 5 minutes cache duration
    private static final int MAX_FALLBACK_ATTEMPTS = 3;

    // Error tracking
    private final Map<String, Integer> vendorErrorCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastErrorTime = new ConcurrentHashMap<>();

    /**
     * Enhanced constructor with better initialization
     */
    public VendorInteractionHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance(plugin);

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start cleanup task for old entries
        startMaintenanceTask();

        plugin.getLogger().info("Enhanced vendor interaction handler registered with performance optimizations");
    }

    /**
     * Maintenance task to clean up old cache entries and cooldowns
     */
    private void startMaintenanceTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            // Clean up old cooldowns (older than 1 minute)
            interactionCooldowns.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > 60000);

            // Clean up old behavior cache entries
            behaviorCacheTime.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > BEHAVIOR_CACHE_DURATION) {
                    behaviorClassCache.remove(entry.getKey());
                    return true;
                }
                return false;
            });

            // Clean up old error tracking (older than 1 hour)
            lastErrorTime.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > 3600000) {
                    vendorErrorCounts.remove(entry.getKey());
                    return true;
                }
                return false;
            });

        }, 1200L, 1200L); // Run every minute
    }

    /**
     * Enhanced vendor interaction handling with comprehensive error management
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVendorInteract(PlayerInteractEntityEvent event) {
        // Quick patch lockdown check
        if (YakRealms.isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        UUID playerId = player.getUniqueId();

        totalInteractions.incrementAndGet();

        try {
            // Enhanced NPC validation
            if (!isValidNPC(entity)) {
                return;
            }

            // Enhanced cooldown check with performance optimization
            if (isOnCooldown(playerId)) {
                event.setCancelled(true);
                return;
            }

            // Add to cooldown immediately to prevent double-processing
            addCooldown(playerId);

            // Enhanced NPC ID retrieval
            int npcId = getNpcId(entity);
            if (npcId == -1) {
                return;
            }

            // Find vendor with error handling
            Vendor vendor = vendorManager.getVendorByNpcId(npcId);
            if (vendor == null) {
                plugin.getLogger().warning("No vendor found for NPC ID: " + npcId);
                return;
            }

            // Cancel the event to prevent default interaction
            event.setCancelled(true);

            // Execute vendor behavior with enhanced error handling
            executeVendorBehavior(player, vendor);

            successfulInteractions.incrementAndGet();

        } catch (Exception e) {
            failedInteractions.incrementAndGet();
            plugin.getLogger().log(Level.WARNING, "Unexpected error in vendor interaction", e);
            player.sendMessage(ChatColor.RED + "An unexpected error occurred. Please try again or contact an administrator.");
        }
    }

    /**
     * Enhanced vendor behavior execution with caching and fallback mechanisms
     */
    private void executeVendorBehavior(Player player, Vendor vendor) {
        String vendorId = vendor.getVendorId();
        String behaviorClassName = vendor.getBehaviorClass();

        try {
            // Check if this vendor has had too many recent errors
            if (hasRecentErrors(vendorId)) {
                player.sendMessage(ChatColor.RED + "This vendor is temporarily unavailable. Please try again later.");
                return;
            }

            VendorBehavior behavior = getBehaviorInstance(behaviorClassName, vendor);

            if (behavior != null) {
                behavior.onInteract(player);
                // Reset error count on successful interaction
                vendorErrorCounts.remove(vendorId);
            } else {
                handleBehaviorFailure(player, vendor, "Failed to create behavior instance");
            }

        } catch (Exception e) {
            handleBehaviorFailure(player, vendor, e.getMessage());
        }
    }

    /**
     * Enhanced behavior instance creation with caching
     */
    private VendorBehavior getBehaviorInstance(String behaviorClassName, Vendor vendor) {
        if (behaviorClassName == null || behaviorClassName.isEmpty()) {
            return null;
        }

        try {
            // Check cache first
            Class<? extends VendorBehavior> behaviorClass = behaviorClassCache.get(behaviorClassName);
            long currentTime = System.currentTimeMillis();

            if (behaviorClass == null ||
                    currentTime - behaviorCacheTime.getOrDefault(behaviorClassName, 0L) > BEHAVIOR_CACHE_DURATION) {

                // Load and validate class
                Class<?> clazz = Class.forName(behaviorClassName);

                if (!VendorBehavior.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException("Class does not implement VendorBehavior: " + behaviorClassName);
                }

                @SuppressWarnings("unchecked")
                Class<? extends VendorBehavior> validatedClass = (Class<? extends VendorBehavior>) clazz;

                // Cache the class
                behaviorClass = validatedClass;
                behaviorClassCache.put(behaviorClassName, behaviorClass);
                behaviorCacheTime.put(behaviorClassName, currentTime);
            }

            // Create new instance
            return behaviorClass.getDeclaredConstructor().newInstance();

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Vendor behavior class not found: " + behaviorClassName);
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to instantiate vendor behavior: " + behaviorClassName + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Enhanced behavior failure handling with automatic fallback
     */
    private void handleBehaviorFailure(Player player, Vendor vendor, String errorMessage) {
        String vendorId = vendor.getVendorId();

        // Track error
        recordVendorError(vendorId);

        // Log error for debugging
        plugin.getLogger().warning("Vendor behavior failure for " + vendorId + ": " + errorMessage);

        // Attempt fallback behavior
        String fallbackBehavior = getFallbackBehaviorForType(vendor.getVendorType());

        if (!fallbackBehavior.equals(vendor.getBehaviorClass())) {
            try {
                VendorBehavior fallbackInstance = getBehaviorInstance(fallbackBehavior, vendor);

                if (fallbackInstance != null) {
                    // Update vendor with working behavior
                    vendor.setBehaviorClass(fallbackBehavior);
                    vendorManager.saveVendorsToConfig();

                    // Execute fallback behavior
                    fallbackInstance.onInteract(player);

                    player.sendMessage(ChatColor.YELLOW + "This vendor had an issue but has been fixed. Enjoy your interaction!");
                    plugin.getLogger().info("Successfully applied fallback behavior for vendor " + vendorId);
                    return;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Fallback behavior also failed for vendor " + vendorId + ": " + e.getMessage());
            }
        }

        // All attempts failed
        player.sendMessage(ChatColor.RED + "This vendor is currently experiencing technical difficulties. Please contact an administrator.");
    }

    /**
     * Enhanced error tracking for vendors
     */
    private void recordVendorError(String vendorId) {
        int errorCount = vendorErrorCounts.getOrDefault(vendorId, 0) + 1;
        vendorErrorCounts.put(vendorId, errorCount);
        lastErrorTime.put(vendorId, System.currentTimeMillis());

        if (errorCount >= MAX_FALLBACK_ATTEMPTS) {
            plugin.getLogger().warning("Vendor " + vendorId + " has exceeded maximum error threshold (" +
                    errorCount + " errors). Consider investigating.");
        }
    }

    /**
     * Check if vendor has recent errors
     */
    private boolean hasRecentErrors(String vendorId) {
        int errorCount = vendorErrorCounts.getOrDefault(vendorId, 0);
        long lastError = lastErrorTime.getOrDefault(vendorId, 0L);

        // If more than 5 errors in the last 10 minutes, consider it problematic
        return errorCount >= 5 && (System.currentTimeMillis() - lastError) < 600000;
    }

    /**
     * Enhanced fallback behavior determination
     */
    private String getFallbackBehaviorForType(String vendorType) {
        String basePath = "com.rednetty.server.mechanics.economy.vendors.behaviors.";

        switch (vendorType.toLowerCase()) {
            case "item":
                return basePath + "ItemVendorBehavior";
            case "fisherman":
                return basePath + "FishermanBehavior";
            case "book":
                return basePath + "BookVendorBehavior";
            case "upgrade":
                return basePath + "UpgradeVendorBehavior";
            case "banker":
                return basePath + "BankerBehavior";
            case "medic":
                return basePath + "MedicBehavior";
            case "gambler":
                return basePath + "GamblerBehavior";
            default:
                return basePath + "ShopBehavior";
        }
    }

    /**
     * Enhanced NPC validation
     */
    private boolean isValidNPC(Entity entity) {
        return entity != null && entity.isValid() && entity.hasMetadata("NPC");
    }

    /**
     * Enhanced NPC ID retrieval with multiple fallback methods
     */
    private int getNpcId(Entity entity) {
        if (!isValidNPC(entity)) {
            return -1;
        }

        try {
            // Primary method: Citizens API
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
            if (npc != null) {
                return npc.getId();
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Citizens API method failed for NPC ID retrieval: " + e.getMessage());
        }

        // Fallback method: Metadata inspection
        return extractNpcIdFromMetadata(entity);
    }

    /**
     * Enhanced metadata-based NPC ID extraction
     */
    private int extractNpcIdFromMetadata(Entity entity) {
        try {
            for (MetadataValue meta : entity.getMetadata("NPC")) {
                if (meta.getOwningPlugin() != null && "Citizens".equals(meta.getOwningPlugin().getName())) {
                    // Try direct integer value
                    if (meta.value() instanceof Integer) {
                        return (Integer) meta.value();
                    }

                    // Try string parsing
                    try {
                        String stringValue = meta.asString();
                        return Integer.parseInt(stringValue);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().fine("Could not parse NPC ID from metadata string: " + meta.asString());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error extracting NPC ID from metadata: " + e.getMessage());
        }

        return -1;
    }

    /**
     * Enhanced cooldown checking with performance optimization
     */
    private boolean isOnCooldown(UUID uuid) {
        Long lastInteraction = interactionCooldowns.get(uuid);
        if (lastInteraction == null) {
            return false;
        }

        return System.currentTimeMillis() - lastInteraction < COOLDOWN_MILLIS;
    }

    /**
     * Enhanced cooldown management
     */
    private void addCooldown(UUID uuid) {
        interactionCooldowns.put(uuid, System.currentTimeMillis());
    }

    /**
     * Enhanced metrics and statistics
     */
    public Map<String, Object> getInteractionStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        stats.put("totalInteractions", totalInteractions.get());
        stats.put("successfulInteractions", successfulInteractions.get());
        stats.put("failedInteractions", failedInteractions.get());
        stats.put("activeCooldowns", interactionCooldowns.size());
        stats.put("cachedBehaviors", behaviorClassCache.size());
        stats.put("vendorsWithErrors", vendorErrorCounts.size());

        // Calculate success rate
        long total = totalInteractions.get();
        if (total > 0) {
            double successRate = (double) successfulInteractions.get() / total * 100.0;
            stats.put("successRate", Math.round(successRate * 100.0) / 100.0);
        } else {
            stats.put("successRate", 0.0);
        }

        return stats;
    }

    /**
     * Get vendors with recent errors for administrative purposes
     */
    public Map<String, Integer> getVendorsWithErrors() {
        return new ConcurrentHashMap<>(vendorErrorCounts);
    }

    /**
     * Clear error tracking for a specific vendor (admin command)
     */
    public void clearVendorErrors(String vendorId) {
        vendorErrorCounts.remove(vendorId);
        lastErrorTime.remove(vendorId);
        plugin.getLogger().info("Cleared error tracking for vendor: " + vendorId);
    }

    /**
     * Clear all error tracking (admin command)
     */
    public void clearAllErrors() {
        vendorErrorCounts.clear();
        lastErrorTime.clear();
        plugin.getLogger().info("Cleared all vendor error tracking");
    }

    /**
     * Force clear behavior cache (admin command)
     */
    public void clearBehaviorCache() {
        behaviorClassCache.clear();
        behaviorCacheTime.clear();
        plugin.getLogger().info("Cleared vendor behavior cache");
    }

    /**
     * Manual cooldown clear for admin purposes
     */
    public void clearPlayerCooldown(UUID playerId) {
        interactionCooldowns.remove(playerId);
    }
}