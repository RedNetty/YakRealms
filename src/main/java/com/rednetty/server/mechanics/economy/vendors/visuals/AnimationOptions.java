package com.rednetty.server.mechanics.economy.vendors.visuals;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * Data class for animation configuration options
 */
public class AnimationOptions {
    public final JavaPlugin plugin;
    public final String vendorId;
    public final boolean particlesEnabled;
    public final boolean soundsEnabled;
    public final int effectDensity;

    public AnimationOptions(
            JavaPlugin plugin,
            String vendorId,
            boolean particlesEnabled,
            boolean soundsEnabled,
            int effectDensity) {
        this.plugin = plugin;
        this.vendorId = vendorId;
        this.particlesEnabled = particlesEnabled;
        this.soundsEnabled = soundsEnabled;
        this.effectDensity = effectDensity;
    }
}