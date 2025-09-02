package com.rednetty.server.core.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import org.bukkit.event.Listener;

import java.util.logging.Logger;

/**
 * Base class for all listener implementations
 * Provides common functionality and ensures consistent implementation
 */
public abstract class BaseListener implements Listener {
    protected final YakRealms plugin;
    protected final Logger logger;

    public BaseListener() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Initialize the listener - called during onEnable
     */
    public void initialize() {
        // Default implementation does nothing - subclasses can override
    }

    /**
     * Clean up resources - called during onDisable
     */
    public void cleanup() {
        // Default implementation does nothing - subclasses can override
    }

    /**
     * Check if server is in patch lockdown mode
     */
    protected boolean isPatchLockdown() {
        try {
            return YakRealms.isPatchLockdown();
        } catch (Exception e) {
            return false;
        }
    }
}