
// === LootChestLocation.java ===
package com.rednetty.server.mechanics.world.lootchests.types;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Wrapper class for chest locations with proper equals/hashCode for map usage
 */
public class LootChestLocation {
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public LootChestLocation(Location location) {
        this.worldName = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }

    public LootChestLocation(String worldName, int x, int y, int z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Location getBukkitLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World " + worldName + " is not loaded");
        }
        return new Location(world, x, y, z);
    }

    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LootChestLocation that = (LootChestLocation) obj;
        return x == that.x && y == that.y && z == that.z &&
                Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z);
    }

    @Override
    public String toString() {
        return worldName + "@" + x + "," + y + "," + z;
    }

    /**
     * Creates a location from a string representation
     */
    public static LootChestLocation fromString(String str) {
        String[] parts = str.split("@")[1].split(",");
        return new LootChestLocation(
                str.split("@")[0],
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }
}
