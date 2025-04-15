package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.friends.Buddies;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stamina.Energy;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

/**
 * Main initializer for all player-related mechanics
 */
public class PlayerMechanics implements Listener {
    private static PlayerMechanics instance;

    private Energy energySystem;
    private Toggles toggleSystem;
    private Buddies buddySystem;

    /**
     * Private constructor for singleton pattern
     */
    private PlayerMechanics() {
        // Initialize subsystems
        this.energySystem = Energy.getInstance();
        this.toggleSystem = Toggles.getInstance();
        this.buddySystem = Buddies.getInstance();
    }

    /**
     * Gets the singleton instance
     *
     * @return The PlayerMechanics instance
     */
    public static PlayerMechanics getInstance() {
        if (instance == null) {
            instance = new PlayerMechanics();
        }
        return instance;
    }

    /**
     * Initialize all player mechanics
     */
    public void onEnable() {
        // Enable each subsystem
        energySystem.onEnable();
        toggleSystem.onEnable();
        buddySystem.onEnable();

        // Register this class as a listener too
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        YakRealms.log("Player mechanics have been enabled.");
    }

    /**
     * Clean up all player mechanics on disable
     */
    public void onDisable() {
        // Disable each subsystem
        energySystem.onDisable();
        toggleSystem.onDisable();
        buddySystem.onDisable();

        YakRealms.log("Player mechanics have been disabled.");
    }

    /**
     * Get the energy system
     *
     * @return The Energy instance
     */
    public Energy getEnergySystem() {
        return energySystem;
    }

    /**
     * Get the toggle system
     *
     * @return The Toggles instance
     */
    public Toggles getToggleSystem() {
        return toggleSystem;
    }

    /**
     * Get the buddy system
     *
     * @return The Buddies instance
     */
    public Buddies getBuddySystem() {
        return buddySystem;
    }
}