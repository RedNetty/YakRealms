package com.rednetty.server.utils.particles;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;

/**
 * Utility class for displaying particle effects
 */
public class ParticleUtil {

    /**
     * Shows a success effect at the specified location
     *
     * @param location The location to show the effect at
     */
    public static void showSuccessEffect(Location location) {
        if (location == null) return;

        World world = location.getWorld();

        // Green particles for success
        DustOptions dustOptions = new DustOptions(Color.GREEN, 2.0f);
        world.spawnParticle(Particle.DUST, location.clone().add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0.1, dustOptions);

        // Add some sparkles
        world.spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0, 1, 0),
                15, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * Shows a failure effect at the specified location
     *
     * @param location The location to show the effect at
     */
    public static void showFailureEffect(Location location) {
        if (location == null) return;

        World world = location.getWorld();

        // Add some smoke/lava particles
        world.spawnParticle(Particle.SMOKE, location.clone().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1);

        world.spawnParticle(Particle.LAVA, location.clone().add(0, 1, 0),
                5, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * Creates a circular particle effect at the specified location
     *
     * @param location The center location of the circle
     * @param particle The particle type to display
     * @param radius   The radius of the circle
     * @param density  The density of particles (points per circle)
     */
    public static void createCircle(Location location, Particle particle, double radius, int density) {
        if (location == null) return;

        World world = location.getWorld();
        double increment = (2 * Math.PI) / density;

        for (int i = 0; i < density; i++) {
            double angle = i * increment;
            double x = location.getX() + (radius * Math.cos(angle));
            double z = location.getZ() + (radius * Math.sin(angle));

            Location particleLocation = new Location(world, x, location.getY(), z);
            world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Creates a helix particle effect at the specified location
     *
     * @param location    The base location of the helix
     * @param particle    The particle type to display
     * @param radius      The radius of the helix
     * @param height      The height of the helix
     * @param density     The density of particles (points per revolution)
     * @param revolutions The number of revolutions
     */
    public static void createHelix(Location location, Particle particle, double radius,
                                   double height, int density, int revolutions) {
        if (location == null) return;

        World world = location.getWorld();
        double increment = (2 * Math.PI) / density;
        double heightIncrement = height / (density * revolutions);

        for (int i = 0; i < density * revolutions; i++) {
            double angle = i * increment;
            double x = location.getX() + (radius * Math.cos(angle));
            double z = location.getZ() + (radius * Math.sin(angle));
            double y = location.getY() + (i * heightIncrement);

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Creates a colored dust cloud at the specified location
     *
     * @param location The location to show the dust at
     * @param color    The color of the dust
     * @param size     The size of each dust particle
     * @param count    The number of particles
     * @param radius   The radius of the cloud
     */
    public static void createDustCloud(Location location, Color color, float size, int count, double radius) {
        if (location == null) return;

        World world = location.getWorld();
        DustOptions dustOptions = new DustOptions(color, size);

        world.spawnParticle(Particle.DUST, location, count, radius, radius, radius, 0, dustOptions);
    }

    /**
     * Creates a line of particles between two locations
     *
     * @param start    The starting location
     * @param end      The ending location
     * @param particle The particle type to display
     * @param density  The number of particles per block
     */
    public static void createLine(Location start, Location end, Particle particle, double density) {
        if (start == null || end == null || !start.getWorld().equals(end.getWorld())) {
            return;
        }

        World world = start.getWorld();
        double distance = start.distance(end);
        double points = distance * density;

        // Vector from start to end
        double dx = (end.getX() - start.getX()) / points;
        double dy = (end.getY() - start.getY()) / points;
        double dz = (end.getZ() - start.getZ()) / points;

        for (int i = 0; i < points; i++) {
            double x = start.getX() + (dx * i);
            double y = start.getY() + (dy * i);
            double z = start.getZ() + (dz * i);

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Creates an explosion effect without causing damage
     *
     * @param location The location of the explosion
     * @param size     The size of the explosion (affects particle count)
     */
    public static void createExplosionEffect(Location location, float size) {
        if (location == null) return;

        World world = location.getWorld();

        // Scale particle counts based on size
        int particleCount = Math.round(size * 10);

        // Explosion particles
        world.spawnParticle(Particle.EXPLOSION, location, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION, location, particleCount,
                size / 2, size / 2, size / 2, 0.1);

        // Smoke trail
        world.spawnParticle(Particle.LARGE_SMOKE, location, particleCount,
                size / 2, size / 2, size / 2, 0.05);
    }
}