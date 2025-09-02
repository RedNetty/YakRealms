package com.rednetty.server.core.mechanics.item.drops.glowing;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.commands.staff.GlowingDropCommand;
import fr.skytasul.glowingentities.GlowingAPI;

import java.util.Map;

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
            // Check if GlowingEntities plugin is available first
            if (!GlowingAPI.isGlowingEntitiesAvailable()) {
                YakRealms.getInstance().getLogger().severe("GlowingEntities plugin is not available! Make sure the GlowingEntities plugin is installed and loaded.");
                throw new RuntimeException("GlowingEntities plugin not available");
            }

            // Initialize the manager first
            GlowingDropsManager manager = GlowingDropsManager.getInstance();
            manager.onEnable();

            // Then initialize the command
            GlowingDropCommand integration = GlowingDropCommand.getInstance();
            integration.initialize();

            initialized = true;

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