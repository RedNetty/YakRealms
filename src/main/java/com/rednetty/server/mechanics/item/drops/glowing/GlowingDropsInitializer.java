package com.rednetty.server.mechanics.item.drops.glowing;

import com.rednetty.server.YakRealms;
import com.rednetty.server.commands.staff.GlowingDropCommand;
import fr.skytasul.glowingentities.GlowingAPI;

import java.util.Map;
import java.util.Objects;

/**
 * Initializer for the GlowingEntities-based glowing drops system
 */
public class GlowingDropsInitializer {
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            YakRealms.getInstance().getLogger().warning("Glowing Drops system already initialized");
            return;
        }

        try {
            YakRealms.getInstance().getLogger().info("Initializing GlowingEntities-based Glowing Drops system...");

            // Check if GlowingEntities plugin is available first
            if (!GlowingAPI.isGlowingEntitiesAvailable()) {
                YakRealms.getInstance().getLogger().severe("GlowingEntities plugin is not available! Make sure the GlowingEntities plugin is installed and loaded.");
                throw new RuntimeException("GlowingEntities plugin not available");
            }

            YakRealms.getInstance().getLogger().info("Using GlowingEntities plugin API");

            // Initialize the manager first
            GlowingDropsManager manager = GlowingDropsManager.getInstance();
            manager.onEnable();

            // Then initialize the command
            GlowingDropCommand integration = GlowingDropCommand.getInstance();
            integration.initialize();

            initialized = true;
            YakRealms.log("GlowingEntities-based Glowing Drops system has been enabled!");

            // Log system status with proper null safety
            Map<String, Object> stats = manager.getStatistics();
            YakRealms.getInstance().getLogger().info("Glowing Drops System Status:");

            // Safe way to check boolean values from the map
            Object glowingEntitiesAvailable = stats.get("glowingEntitiesAvailable");
            YakRealms.getInstance().getLogger().info("- GlowingEntities API: " +
                    (Boolean.TRUE.equals(glowingEntitiesAvailable) ? "Available" : "Not Available"));

            // Safe way to get string values
            Object toggleName = stats.get("toggleName");
            YakRealms.getInstance().getLogger().info("- Toggle Name: " +
                    (toggleName != null ? toggleName.toString() : "Unknown"));

            // Safe way to get numeric values
            Object glowRadius = stats.get("glowRadius");
            YakRealms.getInstance().getLogger().info("- Glow Radius: " +
                    (glowRadius != null ? glowRadius.toString() : "Unknown") + " blocks");

            // Safe way to check boolean values
            Object autoEnableToggle = stats.get("autoEnableToggle");
            YakRealms.getInstance().getLogger().info("- Auto-Enable Toggle: " +
                    (Boolean.TRUE.equals(autoEnableToggle) ? "Yes" : "No"));

            Object showCommonItems = stats.get("showCommonItems");
            YakRealms.getInstance().getLogger().info("- Show Common Items: " +
                    (Boolean.TRUE.equals(showCommonItems) ? "Yes" : "No"));

            // Additional status info
            Object trackedItems = stats.get("trackedItems");
            if (trackedItems != null) {
                YakRealms.getInstance().getLogger().info("- Tracked Items: " + trackedItems.toString());
            }

            Object playersWithVisibleItems = stats.get("playersWithVisibleItems");
            if (playersWithVisibleItems != null) {
                YakRealms.getInstance().getLogger().info("- Players with Visible Items: " + playersWithVisibleItems.toString());
            }

        } catch (Exception e) {
            YakRealms.warn("Failed to initialize GlowingEntities-based Glowing Drops system: " + e.getMessage());
            e.printStackTrace();

            // Clean up on failure
            try {
                if (initialized) {
                    shutdown();
                }
            } catch (Exception cleanupException) {
                YakRealms.warn("Failed to cleanup after initialization failure: " + cleanupException.getMessage());
            }
        }
    }

    public static void shutdown() {
        if (!initialized) {
            return;
        }

        try {
            YakRealms.getInstance().getLogger().info("Shutting down GlowingEntities-based Glowing Drops system...");

            // Shutdown command first
            try {
                GlowingDropCommand integration = GlowingDropCommand.getInstance();
                if (integration != null) {
                    integration.shutdown();
                }
            } catch (Exception e) {
                YakRealms.warn("Error shutting down GlowingDropCommand: " + e.getMessage());
            }

            // Then shutdown manager
            try {
                GlowingDropsManager manager = GlowingDropsManager.getInstance();
                if (manager != null) {
                    manager.onDisable();
                }
            } catch (Exception e) {
                YakRealms.warn("Error shutting down GlowingDropsManager: " + e.getMessage());
            }

            initialized = false;
            YakRealms.log("GlowingEntities-based Glowing Drops system has been disabled!");

        } catch (Exception e) {
            YakRealms.warn("Failed to shutdown GlowingEntities-based Glowing Drops system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Helper method to safely get boolean values from a map
     */
    private static boolean getBooleanSafely(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * Helper method to safely get string values from a map
     */
    private static String getStringSafely(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    /**
     * Helper method to safely get integer values from a map
     */
    private static int getIntSafely(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}