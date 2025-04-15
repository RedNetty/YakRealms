package com.rednetty.server.utils.particles;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

/**
 * Utility class for creating and displaying firework effects
 */
public class FireworkUtil {

    /**
     * Spawns a firework at the specified location
     *
     * @param location The location to spawn the firework at
     * @param type     The type of firework effect
     * @param color    The primary color of the firework
     */
    public static void spawnFirework(Location location, FireworkEffect.Type type, Color color) {
        spawnFirework(location, type, color, color, false, false, 0);
    }

    /**
     * Spawns a firework at the specified location with custom settings
     *
     * @param location  The location to spawn the firework at
     * @param type      The type of firework effect
     * @param color     The primary color of the firework
     * @param fadeColor The fade color of the firework
     * @param trail     Whether the firework should have a trail
     * @param flicker   Whether the firework should flicker
     * @param power     The power of the firework (0-3)
     */
    public static void spawnFirework(Location location, FireworkEffect.Type type, Color color,
                                     Color fadeColor, boolean trail, boolean flicker, int power) {
        // Validate input parameters
        if (location == null || type == null || color == null || fadeColor == null) {
            return;
        }

        // Cap power between 0 and 3
        power = Math.max(0, Math.min(3, power));

        // Create the firework and get its meta
        Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK);
        FireworkMeta meta = firework.getFireworkMeta();

        // Create the effect
        FireworkEffect effect = FireworkEffect.builder()
                .with(type)
                .withColor(color)
                .withFade(fadeColor)
                .trail(trail)
                .flicker(flicker)
                .build();

        // Apply the effect and power
        meta.addEffect(effect);
        meta.setPower(power);
        firework.setFireworkMeta(meta);
    }

    /**
     * Spawns an instant firework at the specified location
     * The firework will detonate immediately without flying up
     *
     * @param location The location to spawn the firework at
     * @param type     The type of firework effect
     * @param color    The primary color of the firework
     */
    public static void spawnInstantFirework(Location location, FireworkEffect.Type type, Color color) {
        // Create the firework and get its meta
        Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK);
        FireworkMeta meta = firework.getFireworkMeta();

        // Create the effect
        FireworkEffect effect = FireworkEffect.builder()
                .with(type)
                .withColor(color)
                .build();

        // Apply the effect and power
        meta.addEffect(effect);
        meta.setPower(0);
        firework.setFireworkMeta(meta);

        // Detonate immediately
        firework.detonate();
    }
}