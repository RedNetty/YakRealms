package com.rednetty.server.core.mechanics.economy.vendors;

import com.rednetty.server.core.mechanics.economy.EconomyManager;
import com.rednetty.server.core.mechanics.economy.vendors.registry.VendorTypeRegistry;
import com.rednetty.server.core.mechanics.economy.vendors.types.ItemVendorType;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Main manager for the vendor system
 * 
 * This class handles the initialization and management of the entire vendor system,
 * including registry setup, vendor type registration, and system lifecycle.
 */
public class VendorSystemManager {
    private static VendorSystemManager instance;
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final EconomyManager economyManager;
    private final YakPlayerManager playerManager;
    
    // System components
    private VendorTypeRegistry vendorRegistry;
    private VendorMenuManager menuManager;
    
    private boolean initialized = false;
    
    private VendorSystemManager(JavaPlugin plugin, EconomyManager economyManager, YakPlayerManager playerManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.economyManager = economyManager;
        this.playerManager = playerManager;
    }
    
    public static void initialize(JavaPlugin plugin, EconomyManager economyManager, YakPlayerManager playerManager) {
        if (instance == null) {
            instance = new VendorSystemManager(plugin, economyManager, playerManager);
            instance.initializeSystem();
        }
    }
    
    public static VendorSystemManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VendorSystemManager not initialized!");
        }
        return instance;
    }
    
    /**
     * Initialize the complete vendor system
     */
    private void initializeSystem() {
        if (initialized) {
            logger.warning("VendorSystemManager already initialized!");
            return;
        }
        
        logger.info("Initializing Enhanced Vendor System...");
        
        try {
            // Initialize core components
            initializeRegistry();
            initializeMenuManager();
            registerVendorTypes();
            
            initialized = true;
            logger.info("Enhanced Vendor System initialized successfully!");
            
            // Log statistics
            logSystemStats();
            
        } catch (Exception e) {
            logger.severe("Failed to initialize vendor system: " + e.getMessage());
            throw new RuntimeException("Vendor system initialization failed", e);
        }
    }
    
    /**
     * Initialize the vendor type registry
     */
    private void initializeRegistry() {
        VendorTypeRegistry.initialize(logger);
        vendorRegistry = VendorTypeRegistry.getInstance();
        logger.info("Vendor type registry initialized");
    }
    
    /**
     * Initialize the vendor menu manager
     */
    private void initializeMenuManager() {
        VendorMenuManager.initialize(plugin);
        menuManager = VendorMenuManager.getInstance();
        logger.info("Vendor menu manager initialized");
    }
    
    /**
     * Register all vendor types with the registry
     */
    private void registerVendorTypes() {
        logger.info("Registering vendor types...");
        
        // Register Item Vendor  
        ItemVendorType itemVendor = new ItemVendorType(economyManager, playerManager, logger);
        vendorRegistry.registerVendorType(itemVendor);
        
        // TODO: Register other vendor types as they get converted to the new system
        // - UpgradeVendorType
        // - MountVendorType
        // - BookVendorType
        // - FishermanVendorType
        // - GamblerVendorType
        // - BankerVendorType
        // - MedicVendorType
        
        logger.info("Registered " + vendorRegistry.getAllVendorTypes().size() + " vendor types");
    }
    
    /**
     * Get the vendor registry
     */
    public VendorTypeRegistry getVendorRegistry() {
        return vendorRegistry;
    }
    
    /**
     * Get the menu manager
     */
    public VendorMenuManager getMenuManager() {
        return menuManager;
    }
    
    /**
     * Check if the system is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Validate the entire vendor system
     */
    public SystemValidationResult validateSystem() {
        SystemValidationResult result = new SystemValidationResult();
        
        // Validate registry
        if (vendorRegistry == null) {
            result.addError("Vendor registry not initialized");
        } else {
            var registryErrors = vendorRegistry.validateRegistry();
            result.addErrors(registryErrors);
        }
        
        // Validate menu manager
        if (menuManager == null) {
            result.addError("Menu manager not initialized");
        }
        
        // Validate dependencies
        if (economyManager == null) {
            result.addError("Economy manager not available");
        }
        
        if (playerManager == null) {
            result.addError("Player manager not available");
        }
        
        return result;
    }
    
    /**
     * Log system statistics
     */
    private void logSystemStats() {
        if (vendorRegistry != null) {
            var stats = vendorRegistry.getStats();
            logger.info("Vendor System Stats: " + stats.toString());
        }
        
        if (menuManager != null) {
            var menuStats = menuManager.getStats();
            logger.info("Menu Manager Stats: " + menuStats.toString());
        }
    }
    
    /**
     * Shutdown the vendor system
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logger.info("Shutting down vendor system...");
        
        try {
            // Clear registries
            if (vendorRegistry != null) {
                vendorRegistry.clear();
            }
            
            initialized = false;
            logger.info("Vendor system shutdown complete");
            
        } catch (Exception e) {
            logger.severe("Error during vendor system shutdown: " + e.getMessage());
        }
    }
    
    /**
     * System validation result
     */
    public static class SystemValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addErrors(java.util.List<String> errors) {
            this.errors.addAll(errors);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }
        
        public java.util.List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }
        
        public int getErrorCount() {
            return errors.size();
        }
        
        public int getWarningCount() {
            return warnings.size();
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{errors=%d, warnings=%d, valid=%b}", 
                    getErrorCount(), getWarningCount(), isValid());
        }
    }
}