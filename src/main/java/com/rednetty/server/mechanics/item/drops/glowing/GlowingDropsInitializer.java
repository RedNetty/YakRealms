package com.rednetty.server.mechanics.item.drops.glowing;

import com.rednetty.server.YakRealms;
import com.rednetty.server.commands.staff.GlowingDropCommand;

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
            YakRealms.getInstance().getLogger().info("Using shaded GlowingEntities library (no external plugin required)");

            // Initialize the manager first
            GlowingDropsManager manager = GlowingDropsManager.getInstance();
            manager.onEnable();

            // Then initialize the command
            GlowingDropCommand integration = GlowingDropCommand.getInstance();
            integration.initialize();

            initialized = true;
            YakRealms.log("GlowingEntities-based Glowing Drops system has been enabled!");

            // Log system status
            var stats = manager.getStatistics();
            YakRealms.getInstance().getLogger().info("Glowing Drops System Status:");
            YakRealms.getInstance().getLogger().info("- GlowingEntities API: " + (stats.get("glowingEntitiesInitialized").equals(true) ? "Available" : "Not Available"));
            YakRealms.getInstance().getLogger().info("- Toggle Name: " + stats.get("toggleName"));
            YakRealms.getInstance().getLogger().info("- Glow Radius: " + stats.get("glowRadius") + " blocks");
            YakRealms.getInstance().getLogger().info("- Auto-Enable Toggle: " + stats.get("autoEnableToggle"));
            YakRealms.getInstance().getLogger().info("- Show Common Items: " + stats.get("showCommonItems"));

        } catch (Exception e) {
            YakRealms.warn("Failed to initialize GlowingEntities-based Glowing Drops system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void shutdown() {
        if (!initialized) {
            return;
        }

        try {
            YakRealms.getInstance().getLogger().info("Shutting down GlowingEntities-based Glowing Drops system...");

            // Shutdown command first
            GlowingDropCommand integration = GlowingDropCommand.getInstance();
            integration.shutdown();

            // Then shutdown manager
            GlowingDropsManager manager = GlowingDropsManager.getInstance();
            manager.onDisable();

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
}