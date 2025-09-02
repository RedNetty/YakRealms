package com.rednetty.server.core.mechanics.item;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 * Initializes and manages the menu item system for the server.
 * This class should be called during server startup to properly initialize
 * the menu item system and set up menu items for online players.
 */
public class MenuSystemInitializer {

    private static final Logger logger = YakRealms.getInstance().getLogger();
    private static boolean initialized = false;

    /**
     * Initialize the menu item system
     * Should be called during server startup after all managers are loaded
     */
    public static void initialize() {
        if (initialized) {
            logger.warning("MenuSystemInitializer already initialized");
            return;
        }

        try {
            // Initialize the MenuItemManager
            MenuItemManager menuItemManager = MenuItemManager.getInstance();
            menuItemManager.initialize();

            // Set up menu items for any players already online (in case of reload)
            new BukkitRunnable() {
                @Override
                public void run() {
                    setupMenuItemsForOnlinePlayers();
                }
            }.runTaskLater(YakRealms.getInstance(), 20L); // Delay by 1 second to ensure everything is loaded

            initialized = true;
            // Menu item system initialization completed

        } catch (Exception e) {
            logger.severe("Failed to initialize menu item system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Set up menu items for all currently online players
     * This is useful for server reloads or when the system is initialized after players have joined
     */
    private static void setupMenuItemsForOnlinePlayers() {
        MenuItemManager menuItemManager = MenuItemManager.getInstance();
        int playersSetup = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                // Only set up if player doesn't already have menu items
                if (!menuItemManager.hasMenuItems(player)) {
                    menuItemManager.setupMenuItems(player);
                    playersSetup++;
                }
            } catch (Exception e) {
                logger.warning("Failed to set up menu items for player " + player.getName() + ": " + e.getMessage());
            }
        }

        if (playersSetup > 0) {
            logger.info("Set up menu items for " + playersSetup + " online players");
        }
    }

    /**
     * Shutdown the menu item system
     * Should be called during server shutdown
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }

        try {
            MenuItemManager menuItemManager = MenuItemManager.getInstance();
            menuItemManager.shutdown();

            initialized = false;
            logger.info("Menu item system shutdown completed");

        } catch (Exception e) {
            logger.severe("Error during menu item system shutdown: " + e.getMessage());
        }
    }

    /**
     * Reload the menu item system
     * Useful for configuration changes or when adding new menu items
     */
    public static void reload() {
        logger.info("Reloading menu item system...");

        try {
            // Clear menu items from all online players
            MenuItemManager menuItemManager = MenuItemManager.getInstance();
            for (Player player : Bukkit.getOnlinePlayers()) {
                menuItemManager.clearMenuItems(player);
            }

            // Re-setup menu items after a brief delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    setupMenuItemsForOnlinePlayers();
                    logger.info("Menu item system reloaded successfully");
                }
            }.runTaskLater(YakRealms.getInstance(), 10L);

        } catch (Exception e) {
            logger.severe("Error during menu item system reload: " + e.getMessage());
        }
    }

    /**
     * Check if the menu item system is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Force setup menu items for a specific player
     * Useful for commands or manual setup
     */
    public static void setupMenuItemsForPlayer(Player player) {
        if (!initialized) {
            logger.warning("Cannot setup menu items - system not initialized");
            return;
        }

        try {
            MenuItemManager menuItemManager = MenuItemManager.getInstance();
            menuItemManager.setupMenuItems(player);
            logger.fine("Menu items manually set up for player: " + player.getName());
        } catch (Exception e) {
            logger.warning("Failed to manually set up menu items for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Get system statistics
     */
    public static String getSystemStats() {
        if (!initialized) {
            return "Menu item system not initialized";
        }

        try {
            MenuItemManager menuItemManager = MenuItemManager.getInstance();
            var stats = menuItemManager.getStatistics();

            return String.format("Menu Item System Stats:\n" +
                            "- Players with menu items: %d\n" +
                            "- Total menu item types: %d\n" +
                            "- System initialized: %b",
                    stats.get("players_with_menu_items"),
                    stats.get("total_menu_item_types"),
                    initialized);

        } catch (Exception e) {
            return "Error retrieving system stats: " + e.getMessage();
        }
    }
}