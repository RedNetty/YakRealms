package com.rednetty.server.core.mechanics.player.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Handles world-related events including weather, portals, and environmental interactions
 */
public class WorldListener extends BaseListener {

    public WorldListener() {
        super();
    }

    @Override
    public void initialize() {
        logger.info("World listener initialized");
    }

    /**
     * Prevent weather changes to keep clear weather
     */
    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent portal teleportation
     */
    @EventHandler
    public void onPortalUse(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    /**
     * Prevent specific teleport types if needed
     */
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // If specific teleport restrictions are needed
        // E.g., prevent ender pearl teleportation in certain cases
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            // Currently not restricted, but can be added if needed
            // event.setCancelled(true);
        }
    }
}