package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.commands.admin.VendorCommand;
import com.rednetty.server.mechanics.economy.vendors.admin.VendorConfigGUI;
import com.rednetty.server.mechanics.economy.vendors.visuals.VendorAuraManager;
import com.rednetty.server.mechanics.economy.vendors.config.VendorConfiguration;
import com.rednetty.server.mechanics.economy.vendors.health.VendorHealthCheck;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Initializes the vendor system with proper Citizens dependency handling
 * and advanced aura management
 */
public class VendorSystemInitializer {

    private static boolean initialized = false;
    private static VendorAuraManager auraManager;
    private static VendorHologramManager hologramManager;
    private static JavaPlugin plugin;

    /**
     * Initialize the vendor system with proper Citizens dependency checks
     */
    public static void initialize(JavaPlugin pluginInstance) {
        if (initialized) {
            return;
        }

        plugin = pluginInstance;

        // Initialize PurchaseManager right away (doesn't depend on Citizens)
        PurchaseManager.initialize(plugin);

        // Register the vendor command with aura support
        plugin.getCommand("vendor").setExecutor(new VendorCommand(plugin));

        // Check if Citizens is loaded
        if (isCitizensLoaded()) {
            // Try to initialize immediately if Citizens is loaded
            if (isCitizensReady()) {
                initializeVendorSystem(plugin);
            } else {
                // Schedule delayed initialization
                scheduleDelayedInitialization(plugin);
            }
        } else {
            // Citizens not found, log warning and schedule checks
            plugin.getLogger().warning("Citizens plugin not found. Vendor NPCs will not be initialized.");
            waitForCitizensLoad(plugin);
        }

        // Register a plugin listener to detect Citizens being enabled after our plugin
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if (event.getPlugin().getName().equals("Citizens") && !initialized) {
                    plugin.getLogger().info("Citizens plugin enabled after YakRealms. Initializing vendor system...");
                    scheduleDelayedInitialization(plugin);
                }
            }
        }, plugin);
    }

    /**
     * Set up a periodic task to validate vendors
     *
     * @param plugin The main plugin instance
     */
    public static void setupRegularValidation(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!initialized) return;

            VendorManager vendorManager = VendorManager.getInstance(plugin);
            int issuesFixed = vendorManager.validateAndFixVendors();

            if (issuesFixed > 0) {
                plugin.getLogger().info("Periodic validation fixed " + issuesFixed + " vendor issues");
            }
        }, 20 * 60 * 30, 20 * 60 * 30); // Run every 30 minutes
    }

    /**
     * Check if Citizens plugin is loaded
     *
     * @return true if Citizens is loaded
     */
    private static boolean isCitizensLoaded() {
        Plugin citizensPlugin = Bukkit.getPluginManager().getPlugin("Citizens");
        return citizensPlugin != null && citizensPlugin.isEnabled();
    }

    /**
     * Check if CitizensAPI is ready to use
     *
     * @return true if CitizensAPI is ready
     */
    private static boolean isCitizensReady() {
        try {
            // Just try accessing the NPC Registry to check if it's initialized
            CitizensAPI.getNPCRegistry();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Initialize the vendor system with all components
     *
     * @param plugin The main plugin instance
     */
    private static void initializeVendorSystem(JavaPlugin plugin) {
        try {
            // Load configuration first
            VendorConfiguration config = VendorConfiguration.getInstance((YakRealms)plugin);

            // Initialize VendorManager
            VendorManager vendorManager = VendorManager.getInstance(plugin);
            vendorManager.initialize();

            // Create hologram manager
            hologramManager = new VendorHologramManager(plugin);

            // Initialize the new vendor aura manager
            auraManager = new VendorAuraManager(plugin);

            // Set up health check system with proper parameters
            VendorHealthCheck healthCheck = new VendorHealthCheck(
                    (YakRealms)plugin,
                    vendorManager,
                    hologramManager
            );

            // Create initial backup
            healthCheck.createBackup();

            // Run initial health check
            int issuesFixed = healthCheck.runHealthCheck(true);
            if (issuesFixed > 0) {
                plugin.getLogger().info("Initial health check fixed " + issuesFixed + " issues");
            }

            // Set up periodic validation
            setupRegularValidation(plugin);

            // Register admin GUI if applicable
            if (plugin instanceof YakRealms) {
                new VendorConfigGUI((YakRealms)plugin);
            }

            // Start vendor aura effects with a delay to ensure everything is loaded
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (auraManager != null) {
                    try {
                        auraManager.startAllAuras();
                        plugin.getLogger().info("Vendor aura effects activated successfully");
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to start vendor aura effects: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }, 100L); // 5-second delay

            plugin.getLogger().info("Vendor system has been fully initialized with Citizens integration");
            initialized = true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize vendor system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Schedule a delayed initialization to wait for Citizens to be ready
     *
     * @param plugin The main plugin instance
     */
    private static void scheduleDelayedInitialization(JavaPlugin plugin) {
        new BukkitRunnable() {
            private int attempts = 0;
            private static final int MAX_ATTEMPTS = 10;

            @Override
            public void run() {
                attempts++;

                if (isCitizensReady()) {
                    initializeVendorSystem(plugin);
                    cancel();
                    return;
                }

                if (attempts >= MAX_ATTEMPTS) {
                    plugin.getLogger().warning("Citizens API still not ready after " + MAX_ATTEMPTS +
                            " attempts. Vendor NPCs may not function correctly.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second, up to 10 times
    }

    /**
     * Wait for Citizens plugin to be loaded if it's not already
     *
     * @param plugin The main plugin instance
     */
    private static void waitForCitizensLoad(JavaPlugin plugin) {
        new BukkitRunnable() {
            private int attempts = 0;
            private static final int MAX_ATTEMPTS = 20;

            @Override
            public void run() {
                attempts++;

                if (isCitizensLoaded()) {
                    plugin.getLogger().info("Citizens plugin detected. Initializing vendor system...");
                    scheduleDelayedInitialization(plugin);
                    cancel();
                    return;
                }

                if (attempts >= MAX_ATTEMPTS) {
                    plugin.getLogger().warning("Citizens plugin not detected after " + MAX_ATTEMPTS +
                            " attempts. Vendor NPCs will not be available.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // Check every 5 seconds, up to 20 times
    }

    /**
     * Check if the vendor system is initialized
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the aura manager instance
     *
     * @return The VendorAuraManager instance
     */
    public static VendorAuraManager getAuraManager() {
        return auraManager;
    }

    /**
     * Get the hologram manager instance
     *
     * @return The VendorHologramManager instance
     */
    public static VendorHologramManager getHologramManager() {
        return hologramManager;
    }

    /**
     * Restart the aura system
     */
    public static void restartAuras() {
        if (auraManager != null && initialized) {
            plugin.getLogger().info("Restarting vendor aura effects...");
            auraManager.stopAllAuras();
            auraManager.startAllAuras();
        }
    }

    /**
     * Shutdown vendor system and clean up resources
     *
     * @param plugin The plugin instance
     */
    public static void shutdown(JavaPlugin plugin) {
        if (!initialized) return;

        // Stop all aura effects first
        if (auraManager != null) {
            try {
                auraManager.stopAllAuras();
                plugin.getLogger().info("Successfully stopped vendor aura effects");
            } catch (Exception e) {
                plugin.getLogger().warning("Error stopping vendor aura effects: " + e.getMessage());
            }
        }

        // Shutdown vendor manager
        try {
            VendorManager.getInstance(plugin).shutdown();
        } catch (Exception e) {
            plugin.getLogger().warning("Error shutting down vendor manager: " + e.getMessage());
        }

        initialized = false;
        plugin.getLogger().info("Vendor system shutdown complete with aura cleanup");
    }
}