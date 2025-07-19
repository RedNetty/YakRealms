package com.rednetty.server.mechanics.world.lootchests.types;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable value object representing a chest location
 * Provides validation, serialization, and proper equals/hashCode for map usage
 */
public class ChestLocation {
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    // Validation constants
    private static final Pattern WORLD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]+$");
    private static final int MIN_COORDINATE = -30000000;
    private static final int MAX_COORDINATE = 30000000;
    private static final int MIN_Y = -2048;
    private static final int MAX_Y = 2048;

    // Cached hash code for performance
    private final int hashCode;

    /**
     * Creates a ChestLocation from a Bukkit Location
     * @param location The Bukkit location
     * @throws IllegalArgumentException if location is invalid
     */
    public ChestLocation(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }

        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }

        String worldName = location.getWorld().getName();
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }

        this.worldName = validateWorldName(worldName);
        this.x = validateCoordinate(location.getBlockX(), "X");
        this.y = validateYCoordinate(location.getBlockY());
        this.z = validateCoordinate(location.getBlockZ(), "Z");

        // Pre-compute hash code for performance
        this.hashCode = Objects.hash(this.worldName, this.x, this.y, this.z);
    }

    /**
     * Creates a ChestLocation from individual components
     * @param worldName The world name
     * @param x The X coordinate
     * @param y The Y coordinate
     * @param z The Z coordinate
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public ChestLocation(String worldName, int x, int y, int z) {
        this.worldName = validateWorldName(worldName);
        this.x = validateCoordinate(x, "X");
        this.y = validateYCoordinate(y);
        this.z = validateCoordinate(z, "Z");

        // Pre-compute hash code for performance
        this.hashCode = Objects.hash(this.worldName, this.x, this.y, this.z);
    }

    // === Validation Methods ===

    private static String validateWorldName(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }

        String trimmed = worldName.trim();

        if (trimmed.length() > 50) {
            throw new IllegalArgumentException("World name too long: " + trimmed.length() + " characters (max 50)");
        }

        if (!WORLD_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid world name format: " + trimmed);
        }

        return trimmed;
    }

    private static int validateCoordinate(int coordinate, String coordinateName) {
        if (coordinate < MIN_COORDINATE || coordinate > MAX_COORDINATE) {
            throw new IllegalArgumentException(coordinateName + " coordinate out of bounds: " + coordinate +
                    " (must be between " + MIN_COORDINATE + " and " + MAX_COORDINATE + ")");
        }
        return coordinate;
    }

    private static int validateYCoordinate(int y) {
        if (y < MIN_Y || y > MAX_Y) {
            throw new IllegalArgumentException("Y coordinate out of bounds: " + y +
                    " (must be between " + MIN_Y + " and " + MAX_Y + ")");
        }
        return y;
    }

    // === Factory Methods ===

    /**
     * Creates a ChestLocation from a string representation
     * Expected format: "worldname@x,y,z"
     * @param str The string representation
     * @return The parsed ChestLocation
     * @throws IllegalArgumentException if string format is invalid
     */
    public static ChestLocation fromString(String str) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException("Location string cannot be null or empty");
        }

        try {
            String trimmed = str.trim();

            // Expected format: worldname@x,y,z
            if (!trimmed.contains("@") || !trimmed.contains(",")) {
                throw new IllegalArgumentException("Invalid format. Expected: worldname@x,y,z");
            }

            String[] mainParts = trimmed.split("@");
            if (mainParts.length != 2) {
                throw new IllegalArgumentException("Invalid format. Expected: worldname@x,y,z");
            }

            String worldName = mainParts[0];
            String coordinateString = mainParts[1];

            String[] coords = coordinateString.split(",");
            if (coords.length != 3) {
                throw new IllegalArgumentException("Invalid coordinate format. Expected: x,y,z");
            }

            // Parse coordinates
            int x = Integer.parseInt(coords[0].trim());
            int y = Integer.parseInt(coords[1].trim());
            int z = Integer.parseInt(coords[2].trim());

            return new ChestLocation(worldName, x, y, z);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate numbers in: " + str, e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse location string '" + str + "': " + e.getMessage(), e);
        }
    }

    /**
     * Safely creates a ChestLocation from a string, returning null on failure
     * @param str The string representation
     * @return The parsed ChestLocation, or null if parsing fails
     */
    public static ChestLocation fromStringSafe(String str) {
        try {
            return fromString(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safely creates a ChestLocation from a Bukkit Location, returning null on failure
     * @param location The Bukkit location
     * @return The ChestLocation, or null if creation fails
     */
    public static ChestLocation fromLocationSafe(Location location) {
        try {
            return new ChestLocation(location);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Validates a location string without creating the object
     * @param str The string to validate
     * @return true if the string represents a valid location
     */
    public static boolean isValidLocationString(String str) {
        return fromStringSafe(str) != null;
    }

    // === Getters ===

    /**
     * Gets the world name
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * Gets the X coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the Y coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Gets the Z coordinate
     */
    public int getZ() {
        return z;
    }

    // === Bukkit Integration ===

    /**
     * Converts to a Bukkit Location
     * @return The Bukkit Location
     * @throws IllegalStateException if the world is not loaded
     */
    public Location getBukkitLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World '" + worldName + "' is not loaded or does not exist");
        }
        return new Location(world, x, y, z);
    }

    /**
     * Safely converts to a Bukkit Location, returning null if world is not loaded
     * @return The Bukkit Location, or null if world is not available
     */
    public Location getBukkitLocationSafe() {
        try {
            World world = Bukkit.getWorld(worldName);
            return world != null ? new Location(world, x, y, z) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if the world is currently loaded
     * @return true if the world is loaded
     */
    public boolean isWorldLoaded() {
        return Bukkit.getWorld(worldName) != null;
    }

    // === Utility Methods ===

    /**
     * Calculates the distance to another location (if in the same world)
     * @param other The other location
     * @return The distance, or Double.MAX_VALUE if in different worlds
     */
    public double distanceTo(ChestLocation other) {
        if (other == null || !this.worldName.equals(other.worldName)) {
            return Double.MAX_VALUE;
        }

        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculates the 2D distance (ignoring Y coordinate) to another location
     * @param other The other location
     * @return The 2D distance, or Double.MAX_VALUE if in different worlds
     */
    public double distance2DTo(ChestLocation other) {
        if (other == null || !this.worldName.equals(other.worldName)) {
            return Double.MAX_VALUE;
        }

        double dx = this.x - other.x;
        double dz = this.z - other.z;

        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Creates a new location offset by the given amounts
     * @param offsetX X offset
     * @param offsetY Y offset
     * @param offsetZ Z offset
     * @return A new ChestLocation with the offsets applied
     */
    public ChestLocation offset(int offsetX, int offsetY, int offsetZ) {
        return new ChestLocation(worldName, x + offsetX, y + offsetY, z + offsetZ);
    }

    /**
     * Gets the chunk coordinates for this location
     * @return The chunk coordinates as a ChunkCoordinate object
     */
    public ChunkCoordinate getChunkCoordinate() {
        return new ChunkCoordinate(worldName, x >> 4, z >> 4);
    }

    /**
     * Checks if this location is in the same chunk as another location
     * @param other The other location
     * @return true if both locations are in the same chunk
     */
    public boolean isInSameChunk(ChestLocation other) {
        if (other == null || !this.worldName.equals(other.worldName)) {
            return false;
        }
        return (this.x >> 4) == (other.x >> 4) && (this.z >> 4) == (other.z >> 4);
    }

    /**
     * Checks if this location represents a valid block position
     * @return true if the location is valid
     */
    public boolean isValidBlockLocation() {
        try {
            // Coordinates already validated in constructor
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // === Display Methods ===

    /**
     * Gets a user-friendly display string
     * @return A formatted display string
     */
    public String toDisplayString() {
        return String.format("World: %s, X: %d, Y: %d, Z: %d", worldName, x, y, z);
    }

    /**
     * Gets a compact display string
     * @return A compact string representation
     */
    public String toCompactString() {
        return String.format("%s (%d, %d, %d)", worldName, x, y, z);
    }

    // === Object Methods ===

    /**
     * String representation in the format "worldname@x,y,z"
     * This format is used for serialization
     */
    @Override
    public String toString() {
        return worldName + "@" + x + "," + y + "," + z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChestLocation that = (ChestLocation) obj;
        return x == that.x && y == that.y && z == that.z &&
                Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return hashCode; // Use pre-computed hash code
    }

    // === Debug Methods ===

    /**
     * Gets debug information about this location
     * @return A map of debug information
     */
    public java.util.Map<String, Object> getDebugInfo() {
        java.util.Map<String, Object> debug = new java.util.HashMap<>();
        debug.put("worldName", worldName);
        debug.put("x", x);
        debug.put("y", y);
        debug.put("z", z);
        debug.put("isWorldLoaded", isWorldLoaded());
        debug.put("isValidBlockLocation", isValidBlockLocation());
        debug.put("chunkCoordinate", getChunkCoordinate().toString());
        debug.put("hashCode", hashCode);
        debug.put("toString", toString());
        return debug;
    }

    // === Nested Classes ===

    /**
     * Represents chunk coordinates for grouping locations
     */
    public static class ChunkCoordinate {
        private final String worldName;
        private final int chunkX;
        private final int chunkZ;
        private final int hashCode;

        public ChunkCoordinate(String worldName, int chunkX, int chunkZ) {
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.hashCode = Objects.hash(worldName, chunkX, chunkZ);
        }

        public String getWorldName() { return worldName; }
        public int getChunkX() { return chunkX; }
        public int getChunkZ() { return chunkZ; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ChunkCoordinate that = (ChunkCoordinate) obj;
            return chunkX == that.chunkX && chunkZ == that.chunkZ &&
                    Objects.equals(worldName, that.worldName);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return worldName + "@chunk(" + chunkX + "," + chunkZ + ")";
        }
    }
}