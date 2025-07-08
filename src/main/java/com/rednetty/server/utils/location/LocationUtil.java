package com.rednetty.server.utils.location;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Utility for easier location and world operations
 * Provides common location calculations and world interactions
 */
public class LocationUtil {

    /**
     * Calculate distance between two locations (2D)
     * @param loc1 First location
     * @param loc2 Second location
     * @return Distance in blocks
     */
    public static double distance2D(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return Double.MAX_VALUE;

        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Calculate distance between two locations (3D)
     */
    public static double distance3D(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return Double.MAX_VALUE;
        return loc1.distance(loc2);
    }

    /**
     * Check if location is within radius of center
     * @param center Center location
     * @param location Location to check
     * @param radius Radius in blocks
     * @param include3D Whether to include Y axis
     * @return True if within radius
     */
    public static boolean isWithinRadius(Location center, Location location, double radius, boolean include3D) {
        double distance = include3D ? distance3D(center, location) : distance2D(center, location);
        return distance <= radius;
    }

    /**
     * Get all players within radius
     * @param center Center location
     * @param radius Radius in blocks
     * @param include3D Whether to include Y axis
     * @return List of players within radius
     */
    public static List<Player> getPlayersWithinRadius(Location center, double radius, boolean include3D) {
        return center.getWorld().getPlayers().stream()
                .filter(player -> isWithinRadius(center, player.getLocation(), radius, include3D))
                .collect(Collectors.toList());
    }

    /**
     * Get all entities within radius
     * @param center Center location
     * @param radius Radius in blocks
     * @param entityClass Class of entities to find (null for all)
     * @return List of entities within radius
     */
    public static <T extends Entity> List<T> getEntitiesWithinRadius(Location center, double radius, Class<T> entityClass) {
        Collection<Entity> entities = center.getWorld().getNearbyEntities(center, radius, radius, radius);

        if (entityClass == null) {
            return (List<T>) new ArrayList<>(entities);
        }

        return entities.stream()
                .filter(entityClass::isInstance)
                .map(entityClass::cast)
                .collect(Collectors.toList());
    }

    /**
     * Get random location within radius
     * @param center Center location
     * @param minRadius Minimum radius
     * @param maxRadius Maximum radius
     * @param safeLocation Whether to ensure safe spawning location
     * @return Random location
     */
    public static Location getRandomLocationWithinRadius(Location center, double minRadius, double maxRadius, boolean safeLocation) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = random.nextDouble(minRadius, maxRadius);

        double x = center.getX() + radius * Math.cos(angle);
        double z = center.getZ() + radius * Math.sin(angle);

        Location location = new Location(center.getWorld(), x, center.getY(), z);

        if (safeLocation) {
            location = findSafeLocation(location);
        }

        return location;
    }

    /**
     * Find safe location (solid ground, air above)
     * @param location Starting location
     * @return Safe location or original if none found
     */
    public static Location findSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();

        // Search from build limit down to bedrock
        for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);

            if (block.getType().isSolid() &&
                    above.getType().isAir() &&
                    above2.getType().isAir()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5, location.getYaw(), location.getPitch());
            }
        }

        return location;
    }

    /**
     * Get blocks in sphere around location
     * @param center Center location
     * @param radius Radius of sphere
     * @param hollow Whether sphere should be hollow
     * @return List of blocks in sphere
     */
    public static List<Block> getBlocksInSphere(Location center, double radius, boolean hollow) {
        List<Block> blocks = new ArrayList<>();
        World world = center.getWorld();

        int radiusInt = (int) Math.ceil(radius);

        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int y = -radiusInt; y <= radiusInt; y++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);

                    if (hollow) {
                        if (distance <= radius && distance >= radius - 1) {
                            blocks.add(world.getBlockAt(
                                    center.getBlockX() + x,
                                    center.getBlockY() + y,
                                    center.getBlockZ() + z
                            ));
                        }
                    } else {
                        if (distance <= radius) {
                            blocks.add(world.getBlockAt(
                                    center.getBlockX() + x,
                                    center.getBlockY() + y,
                                    center.getBlockZ() + z
                            ));
                        }
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Get blocks in cylinder around location
     * @param center Center location
     * @param radius Radius of cylinder
     * @param height Height of cylinder
     * @return List of blocks in cylinder
     */
    public static List<Block> getBlocksInCylinder(Location center, double radius, int height) {
        List<Block> blocks = new ArrayList<>();
        World world = center.getWorld();

        int radiusInt = (int) Math.ceil(radius);

        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int z = -radiusInt; z <= radiusInt; z++) {
                double distance = Math.sqrt(x * x + z * z);

                if (distance <= radius) {
                    for (int y = 0; y < height; y++) {
                        blocks.add(world.getBlockAt(
                                center.getBlockX() + x,
                                center.getBlockY() + y,
                                center.getBlockZ() + z
                        ));
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Get direction vector between two locations
     * @param from Starting location
     * @param to Target location
     * @return Normalized direction vector
     */
    public static Vector getDirection(Location from, Location to) {
        return to.toVector().subtract(from.toVector()).normalize();
    }

    /**
     * Calculate yaw from direction vector
     * @param direction Direction vector
     * @return Yaw in degrees
     */
    public static float getYawFromDirection(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    /**
     * Calculate pitch from direction vector
     * @param direction Direction vector
     * @return Pitch in degrees
     */
    public static float getPitchFromDirection(Vector direction) {
        double xz = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        return (float) Math.toDegrees(Math.atan2(-direction.getY(), xz));
    }

    /**
     * Face location towards another location
     * @param from Location to face from
     * @param to Location to face towards
     * @return Location with updated yaw and pitch
     */
    public static Location faceLocation(Location from, Location to) {
        Vector direction = getDirection(from, to);
        float yaw = getYawFromDirection(direction);
        float pitch = getPitchFromDirection(direction);

        Location faced = from.clone();
        faced.setYaw(yaw);
        faced.setPitch(pitch);
        return faced;
    }

    /**
     * Get location between two locations
     * @param loc1 First location
     * @param loc2 Second location
     * @param percentage Percentage between (0.0 = loc1, 1.0 = loc2)
     * @return Interpolated location
     */
    public static Location getLocationBetween(Location loc1, Location loc2, double percentage) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return loc1.clone();

        percentage = Math.max(0.0, Math.min(1.0, percentage));

        double x = loc1.getX() + (loc2.getX() - loc1.getX()) * percentage;
        double y = loc1.getY() + (loc2.getY() - loc1.getY()) * percentage;
        double z = loc1.getZ() + (loc2.getZ() - loc1.getZ()) * percentage;

        return new Location(loc1.getWorld(), x, y, z);
    }

    /**
     * Check if location is loaded
     * @param location Location to check
     * @return True if chunk is loaded
     */
    public static boolean isLocationLoaded(Location location) {
        return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Get highest block at X,Z coordinates
     * @param world World to check
     * @param x X coordinate
     * @param z Z coordinate
     * @return Highest block location
     */
    public static Location getHighestBlock(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x, y, z);
    }

    /**
     * Check if location is in same chunk as another
     */
    public static boolean isSameChunk(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
                (loc1.getBlockX() >> 4) == (loc2.getBlockX() >> 4) &&
                (loc1.getBlockZ() >> 4) == (loc2.getBlockZ() >> 4);
    }

    /**
     * Get chunk coordinates from location
     * @param location Location to get chunk coordinates from
     * @return Array with [chunkX, chunkZ]
     */
    public static int[] getChunkCoordinates(Location location) {
        return new int[]{location.getBlockX() >> 4, location.getBlockZ() >> 4};
    }

    /**
     * Convert location to formatted string
     * @param location Location to format
     * @param includeWorld Whether to include world name
     * @param precision Decimal precision for coordinates
     * @return Formatted location string
     */
    public static String locationToString(Location location, boolean includeWorld, int precision) {
        String format = "%." + precision + "f";
        String coords = String.format(format + ", " + format + ", " + format,
                location.getX(), location.getY(), location.getZ());

        if (includeWorld) {
            return location.getWorld().getName() + ": " + coords;
        }
        return coords;
    }

    /**
     * Parse location from string (format: "world:x,y,z" or "x,y,z")
     * @param locationString String to parse
     * @param defaultWorld Default world if not specified
     * @return Parsed location or null if invalid
     */
    public static Location locationFromString(String locationString, World defaultWorld) {
        try {
            String[] parts = locationString.split(":");
            World world = defaultWorld;
            String coords;

            if (parts.length == 2) {
                world = Bukkit.getWorld(parts[0]);
                coords = parts[1];
            } else {
                coords = parts[0];
            }

            if (world == null) return null;

            String[] coordParts = coords.split(",");
            if (coordParts.length < 3) return null;

            double x = Double.parseDouble(coordParts[0].trim());
            double y = Double.parseDouble(coordParts[1].trim());
            double z = Double.parseDouble(coordParts[2].trim());

            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}