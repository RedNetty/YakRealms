package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.commands.staff.admin.VendorCommand;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Level;

/**
 * Robust vendor system initializer with proper dependency management and error handling
 */
public class VendorSystemInitializer {
    private static boolean initialized = false;
    private static boolean initializing = false;
    private static JavaPlugin pluginInstance;

    /**
     * Initialize the vendor system with proper dependency checking
     */
    public static void initialize(JavaPlugin plugin) {
        if (initialized) {
            plugin.getLogger().info("Vendor system already initialized");
            return;
        }

        if (initializing) {
            plugin.getLogger().warning("Vendor system initialization already in progress");
            return;
        }

        pluginInstance = plugin;
        initializing = true;

        // Initialize with delay to ensure all dependencies are loaded
        new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = 20; // 20 seconds max wait

            @Override
            public void run() {
                attempts++;

                if (checkDependencies(plugin)) {
                    performInitialization(plugin);
                    cancel();
                } else if (attempts >= maxAttempts) {
                    plugin.getLogger().severe("Failed to initialize vendor system after " + maxAttempts + " attempts - dependencies not available");
                    initializing = false;
                    cancel();
                } else {
                    plugin.getLogger().info("Waiting for dependencies... (attempt " + attempts + "/" + maxAttempts + ")");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }

    /**
     * Check if all required dependencies are available
     */
    private static boolean checkDependencies(JavaPlugin plugin) {
        try {
            // Check Citizens
            if (plugin.getServer().getPluginManager().getPlugin("Citizens") == null) {
                plugin.getLogger().warning("Citizens plugin not found");
                return false;
            }

            // Check if Citizens API is accessible
            try {
                Class.forName("net.citizensnpcs.api.CitizensAPI");
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            } catch (Exception e) {
                plugin.getLogger().warning("Citizens API not ready: " + e.getMessage());
                return false;
            }

            // Check if economy manager is available (assuming it's a dependency)
            try {
                com.rednetty.server.mechanics.economy.EconomyManager.getInstance();
            } catch (Exception e) {
                plugin.getLogger().warning("Economy Manager not ready: " + e.getMessage());
                return false;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking dependencies", e);
            return false;
        }
    }

    /**
     * Perform the actual initialization
     */
    private static void performInitialization(JavaPlugin plugin) {
        try {
            plugin.getLogger().info("Starting vendor system initialization...");

            // Initialize components in order
            initializePurchaseManager(plugin);
            initializeVendorManager(plugin);
            initializeInteractionHandler(plugin);
            initializeCommands(plugin);

            // Setup shutdown hook
            setupShutdownHook(plugin);

            initialized = true;
            initializing = false;

            plugin.getLogger().info("Vendor system initialized successfully");

            // Log system status
            logSystemStatus(plugin);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize vendor system", e);
            initializing = false;

            // Attempt cleanup of partially initialized components
            cleanup(plugin);
        }
    }

    /**
     * Initialize purchase manager
     */
    private static void initializePurchaseManager(JavaPlugin plugin) {
        try {
            PurchaseManager.initialize(plugin);
            plugin.getLogger().info("Purchase manager initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize purchase manager", e);
        }
    }

    /**
     * Initialize vendor manager
     */
    private static void initializeVendorManager(JavaPlugin plugin) {
        try {
            VendorManager.initialize(plugin);
            plugin.getLogger().info("Vendor manager initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize vendor manager", e);
        }
    }

    /**
     * Initialize interaction handler
     */
    private static void initializeInteractionHandler(JavaPlugin plugin) {
        try {
            new VendorInteractionHandler(plugin);
            plugin.getLogger().info("Vendor interaction handler initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize interaction handler", e);
        }
    }

    /**
     * Initialize commands
     */
    private static void initializeCommands(JavaPlugin plugin) {
        try {
            if (plugin.getCommand("vendor") != null) {
                plugin.getCommand("vendor").setExecutor(new VendorCommand(plugin));
                plugin.getLogger().info("Vendor command registered");
            } else {
                plugin.getLogger().warning("Vendor command not found in plugin.yml");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize vendor command", e);
            // Don't throw here - commands are not critical
        }
    }

    /**
     * Setup shutdown hook for proper cleanup
     */
    private static void setupShutdownHook(JavaPlugin plugin) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (initialized) {
                plugin.getLogger().info("Emergency vendor system shutdown triggered");
                performShutdown(plugin, false);
            }
        }));
    }

    /**
     * Log system status for debugging
     */
    private static void logSystemStatus(JavaPlugin plugin) {
        try {
            VendorManager manager = VendorManager.getInstance();
            var stats = manager.getStats();

            plugin.getLogger().info("Vendor System Status:");
            plugin.getLogger().info("  - Total Vendors: " + stats.get("totalVendors"));
            plugin.getLogger().info("  - Pending Vendors: " + stats.get("pendingVendors"));
            plugin.getLogger().info("  - Citizens Available: " + stats.get("citizensAvailable"));

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to log system status", e);
        }
    }

    /**
     * Shutdown the vendor system with proper cleanup
     */
    public static void shutdown(JavaPlugin plugin) {
        performShutdown(plugin, true);
    }

    /**
     * Perform the actual shutdown
     */
    private static void performShutdown(JavaPlugin plugin, boolean logOutput) {
        if (!initialized) {
            if (logOutput) {
                plugin.getLogger().info("Vendor system not initialized - nothing to shutdown");
            }
            return;
        }

        try {
            if (logOutput) {
                plugin.getLogger().info("Shutting down vendor system...");
            }

            // Shutdown vendor manager (this saves data)
            try {
                VendorManager manager = VendorManager.getInstance();
                manager.shutdown();
                if (logOutput) {
                    plugin.getLogger().info("Vendor manager shutdown complete");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error during vendor manager shutdown", e);
            }

            // Clear static references
            initialized = false;
            initializing = false;
            pluginInstance = null;

            if (logOutput) {
                plugin.getLogger().info("Vendor system shutdown complete");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during vendor system shutdown", e);
        }
    }

    /**
     * Cleanup partially initialized components
     */
    private static void cleanup(JavaPlugin plugin) {
        try {
            plugin.getLogger().info("Cleaning up partially initialized vendor system...");

            // Reset static state
            initialized = false;
            initializing = false;

            plugin.getLogger().info("Vendor system cleanup complete");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during cleanup", e);
        }
    }

    /**
     * Force reload the entire vendor system
     */
    public static void reload(JavaPlugin plugin) {
        if (!initialized) {
            plugin.getLogger().warning("Cannot reload - vendor system not initialized");
            return;
        }

        try {
            plugin.getLogger().info("Reloading vendor system...");

            // Reload vendor manager
            VendorManager manager = VendorManager.getInstance();
            manager.reload();

            plugin.getLogger().info("Vendor system reload complete");
            logSystemStatus(plugin);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during vendor system reload", e);
        }
    }

    /**
     * Check if the system is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if the system is currently initializing
     */
    public static boolean isInitializing() {
        return initializing;
    }

    /**
     * Get initialization status info
     */
    public static String getStatusInfo() {
        if (initialized) {
            return "Initialized";
        } else if (initializing) {
            return "Initializing...";
        } else {
            return "Not initialized";
        }
    }

    /**
     * Manual initialization trigger (for testing/debugging)
     */
    public static void forceInitialize(JavaPlugin plugin) {
        if (initialized) {
            shutdown(plugin);
        }

        initialized = false;
        initializing = false;

        // Wait a moment then initialize
        new BukkitRunnable() {
            @Override
            public void run() {
                initialize(plugin);
            }
        }.runTaskLater(plugin, 5L);
    }
}