package com.rednetty.server.mechanics.economy.merchant;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main integration class for the Merchant System
 * Handles initialization, coordination, and lifecycle management of all merchant components
 */
public class MerchantSystem {

    private static MerchantSystem instance;
    private final Logger logger;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    // System components
    private MerchantManager merchantManager;
    private MerchantConfig merchantConfig;

    /**
     * Private constructor for singleton pattern
     */
    private MerchantSystem() {
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Get the singleton instance
     */
    public static MerchantSystem getInstance() {
        if (instance == null) {
            instance = new MerchantSystem();
        }
        return instance;
    }

    /**
     * Initialize the merchant system
     * This should be called during server startup after all dependencies are loaded
     */
    public void initialize() {
        if (initialized.get()) {
            logger.warning("MerchantSystem is already initialized!");
            return;
        }

        try {
            logger.info("Initializing Merchant System...");

            // Validate dependencies first
            if (!validateDependencies()) {
                throw new RuntimeException("Required dependencies not available");
            }

            // Initialize configuration first
            initializeConfiguration();

            // Initialize core components
            initializeManager();

            // Register commands if needed
            registerCommands();

            // Mark as initialized
            initialized.set(true);
            logger.info("Merchant System initialization completed successfully!");

            // Enable the system if configuration allows it
            if (merchantConfig.isEnabled()) {
                enable();
            } else {
                logger.info("Merchant System is disabled in configuration");
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize Merchant System", e);
            throw new RuntimeException("Merchant System initialization failed", e);
        }
    }

    /**
     * Initialize the configuration system
     */
    private void initializeConfiguration() {
        try {
            logger.fine("Initializing merchant configuration...");
            merchantConfig = MerchantConfig.getInstance();
            merchantConfig.loadConfig();
            merchantConfig.validateAndFixConfig();

            if (merchantConfig.isDebugMode()) {
                logger.info("Merchant configuration loaded with debug mode enabled");
                logger.info(merchantConfig.getConfigSummary());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize merchant configuration", e);
            throw new RuntimeException("Configuration initialization failed", e);
        }
    }

    /**
     * Initialize the merchant manager
     */
    private void initializeManager() {
        try {
            logger.fine("Initializing merchant manager...");
            merchantManager = MerchantManager.getInstance();

            // Don't enable yet - wait for explicit enable() call
            logger.fine("Merchant manager initialized successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize merchant manager", e);
            throw new RuntimeException("Manager initialization failed", e);
        }
    }

    /**
     * Register commands
     */
    private void registerCommands() {
        try {
            logger.fine("Registering merchant commands...");

            // Register the main merchant command if it exists in plugin.yml
            PluginCommand merchantCmd = YakRealms.getInstance().getCommand("merchant");
            if (merchantCmd != null) {
                // If you have a MerchantCommand class, register it here
                // merchantCommand = new MerchantCommand();
                // merchantCmd.setExecutor(merchantCommand);
                // merchantCmd.setTabCompleter(merchantCommand);

                // Set command properties
                merchantCmd.setDescription("Merchant system administration commands");
                merchantCmd.setUsage("/merchant <subcommand> [args...]");
                merchantCmd.setPermission("merchant.admin");

                logger.fine("Merchant commands registered successfully");
            } else {
                logger.fine("No merchant command found in plugin.yml - skipping command registration");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to register merchant commands", e);
            // Don't throw exception here as commands are optional
        }
    }

    /**
     * Enable the merchant system
     */
    public void enable() {
        if (!initialized.get()) {
            throw new IllegalStateException("Merchant System must be initialized before enabling");
        }

        if (enabled.get()) {
            logger.warning("Merchant System is already enabled!");
            return;
        }

        try {
            logger.info("Enabling Merchant System...");

            // Perform health check before enabling
            if (!healthCheck()) {
                throw new RuntimeException("Health check failed - cannot enable system");
            }

            // Enable merchant manager
            if (merchantManager != null) {
                merchantManager.onEnable();
            }

            enabled.set(true);
            logger.info("Merchant System enabled successfully!");

            // Log system status if debug mode is on
            if (merchantConfig.isDebugMode()) {
                logSystemStatus();
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable Merchant System", e);
            enabled.set(false);
            throw new RuntimeException("Merchant System enable failed", e);
        }
    }

    /**
     * Disable the merchant system
     */
    public void disable() {
        if (!enabled.get()) {
            logger.info("Merchant System is not currently enabled");
            return;
        }

        try {
            logger.info("Disabling Merchant System...");

            // Disable merchant manager
            if (merchantManager != null) {
                merchantManager.onDisable();
            }

            enabled.set(false);
            logger.info("Merchant System disabled successfully!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during Merchant System shutdown", e);
        }
    }

    /**
     * Shutdown the merchant system
     * This should be called during server shutdown
     */
    public void shutdown() {
        try {
            logger.info("Shutting down Merchant System...");

            // Disable if enabled
            if (enabled.get()) {
                disable();
            }

            // Save configuration
            if (merchantConfig != null) {
                merchantConfig.saveConfig();
            }

            // Reset state
            initialized.set(false);

            logger.info("Merchant System shutdown completed");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during Merchant System shutdown", e);
        }
    }

    /**
     * Reload the merchant system
     */
    public void reload() {
        if (!initialized.get()) {
            logger.warning("Cannot reload - Merchant System is not initialized");
            return;
        }

        try {
            logger.info("Reloading Merchant System...");

            boolean wasEnabled = enabled.get();

            // Disable if currently enabled
            if (wasEnabled) {
                disable();
            }

            // Reload configuration
            merchantConfig.reloadConfig();
            merchantConfig.validateAndFixConfig();

            // Re-enable if it was enabled before and config allows it
            if (wasEnabled && merchantConfig.isEnabled()) {
                enable();
            }

            logger.info("Merchant System reloaded successfully!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to reload Merchant System", e);
        }
    }

    /**
     * Get the merchant manager instance
     */
    public MerchantManager getMerchantManager() {
        return merchantManager;
    }

    /**
     * Get the merchant configuration instance
     */
    public MerchantConfig getMerchantConfig() {
        return merchantConfig;
    }

    /**
     * Check if the system is initialized
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Check if the system is enabled
     */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Check if the system is ready for use
     */
    public boolean isReady() {
        return initialized.get() && enabled.get() && merchantConfig.isEnabled();
    }

    /**
     * Get system status information
     */
    public String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Merchant System Status:\n");
        status.append("  Initialized: ").append(initialized.get()).append("\n");
        status.append("  Enabled: ").append(enabled.get()).append("\n");
        status.append("  Config Enabled: ").append(merchantConfig != null ? merchantConfig.isEnabled() : "N/A").append("\n");
        status.append("  Debug Mode: ").append(merchantConfig != null ? merchantConfig.isDebugMode() : "N/A").append("\n");
        status.append("  Ready: ").append(isReady()).append("\n");

        if (merchantConfig != null) {
            status.append("\nConfiguration Summary:\n");
            status.append(merchantConfig.getConfigSummary());
        }

        return status.toString();
    }

    /**
     * Log detailed system status
     */
    private void logSystemStatus() {
        logger.info("=== Merchant System Status ===");
        logger.info("Initialized: " + initialized.get());
        logger.info("Enabled: " + enabled.get());
        logger.info("Ready: " + isReady());

        if (merchantConfig != null) {
            logger.info("Configuration loaded with " +
                    (merchantConfig.isDebugMode() ? "debug mode enabled" : "debug mode disabled"));
        }

        logger.info("==============================");
    }

    /**
     * Validate system dependencies
     */
    public boolean validateDependencies() {
        try {
            // Check if YakRealms is available
            if (YakRealms.getInstance() == null) {
                logger.severe("YakRealms instance not available");
                return false;
            }

            // Check if Bukkit server is available
            if (Bukkit.getServer() == null) {
                logger.severe("Bukkit server not available");
                return false;
            }

            // Check if plugin manager is available
            if (Bukkit.getPluginManager() == null) {
                logger.severe("Plugin manager not available");
                return false;
            }

            // Check if economy system is available
            try {
                Class.forName("com.rednetty.server.mechanics.economy.EconomyManager");
            } catch (ClassNotFoundException e) {
                logger.severe("Economy system not available - required for merchant functionality");
                return false;
            }

            // Check if player system is available
            try {
                Class.forName("com.rednetty.server.mechanics.player.YakPlayerManager");
            } catch (ClassNotFoundException e) {
                logger.severe("Player management system not available");
                return false;
            }

            // Check if text utils are available
            try {
                Class.forName("com.rednetty.server.utils.text.TextUtil");
            } catch (ClassNotFoundException e) {
                logger.warning("Text utility system not available - some features may not work properly");
                // Don't return false here as it's not critical
            }

            // Check if moderation mechanics are available
            try {
                Class.forName("com.rednetty.server.mechanics.moderation.ModerationMechanics");
            } catch (ClassNotFoundException e) {
                logger.warning("Moderation mechanics not available - bonus multipliers may not work");
                // Don't return false here as it's not critical
            }

            logger.fine("All merchant system dependencies validated successfully");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error validating merchant system dependencies", e);
            return false;
        }
    }

    /**
     * Perform system health check
     */
    public boolean healthCheck() {
        try {
            // Check initialization
            if (!initialized.get()) {
                logger.warning("Health check failed: System not initialized");
                return false;
            }

            // Check dependencies
            if (!validateDependencies()) {
                logger.warning("Health check failed: Dependencies not satisfied");
                return false;
            }

            // Check configuration
            if (merchantConfig == null) {
                logger.warning("Health check failed: Configuration not loaded");
                return false;
            }

            // Check manager
            if (merchantManager == null) {
                logger.warning("Health check failed: Manager not initialized");
                return false;
            }

            logger.fine("Merchant system health check passed");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during merchant system health check", e);
            return false;
        }
    }

    /**
     * Get version information
     */
    public String getVersionInfo() {
        return "YakRealms Merchant System v2.1.0 - Modern Trading Platform (Fixed)";
    }

    /**
     * Emergency disable - forces system to stop without proper cleanup
     * Only use this in critical situations
     */
    public void emergencyDisable() {
        logger.warning("Emergency disable triggered for Merchant System!");

        enabled.set(false);
        initialized.set(false);

        if (merchantManager != null) {
            try {
                merchantManager.onDisable();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during emergency disable", e);
            }
        }

        logger.warning("Merchant System emergency shutdown complete");
    }

    /**
     * Get detailed diagnostic information
     */
    public String getDiagnosticInfo() {
        StringBuilder diag = new StringBuilder();
        diag.append("=== Merchant System Diagnostics ===\n");
        diag.append("Version: ").append(getVersionInfo()).append("\n");
        diag.append("Initialized: ").append(initialized.get()).append("\n");
        diag.append("Enabled: ").append(enabled.get()).append("\n");
        diag.append("Ready: ").append(isReady()).append("\n");
        diag.append("Dependencies Valid: ").append(validateDependencies()).append("\n");
        diag.append("Health Check: ").append(healthCheck()).append("\n");

        if (merchantConfig != null) {
            diag.append("\nConfiguration Status:\n");
            diag.append("  Config Enabled: ").append(merchantConfig.isEnabled()).append("\n");
            diag.append("  Debug Mode: ").append(merchantConfig.isDebugMode()).append("\n");
            diag.append("  Update Interval: ").append(merchantConfig.getUpdateIntervalTicks()).append(" ticks\n");
        }

        if (merchantManager != null) {
            diag.append("\nManager Status: Initialized\n");
        } else {
            diag.append("\nManager Status: Not Initialized\n");
        }

        diag.append("================================");

        return diag.toString();
    }
}