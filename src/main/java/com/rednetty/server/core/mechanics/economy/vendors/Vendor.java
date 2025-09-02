package com.rednetty.server.core.mechanics.economy.vendors;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Robust vendor data class with validation and proper encapsulation
 */
public class Vendor {
    private final String id;
    private final int npcId;
    private final Location location;
    private final String vendorType;
    private final List<String> hologramLines;

    /**
     * Create a new vendor with full validation
     */
    public Vendor(String id, int npcId, Location location, String vendorType, List<String> hologramLines) {
        // Validate required parameters
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Vendor ID cannot be null or empty");
        }

        if (npcId <= 0) {
            throw new IllegalArgumentException("NPC ID must be positive");
        }

        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }

        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }

        if (vendorType == null || vendorType.trim().isEmpty()) {
            throw new IllegalArgumentException("Vendor type cannot be null or empty");
        }

        // Store validated data
        this.id = id.trim();
        this.npcId = npcId;
        this.location = location.clone(); // Defensive copy
        this.vendorType = vendorType.trim().toLowerCase();
        this.hologramLines = hologramLines != null ? new ArrayList<>(hologramLines) : new ArrayList<>();
    }

    /**
     * Get vendor ID
     */
    public String getId() {
        return id;
    }

    /**
     * Get NPC ID
     */
    public int getNpcId() {
        return npcId;
    }

    /**
     * Get vendor location (defensive copy)
     */
    public Location getLocation() {
        return location.clone();
    }

    /**
     * Get vendor type
     */
    public String getVendorType() {
        return vendorType;
    }

    /**
     * Get hologram lines (defensive copy)
     */
    public List<String> getHologramLines() {
        return new ArrayList<>(hologramLines);
    }

    /**
     * Check if vendor is in the specified world
     */
    public boolean isInWorld(World world) {
        return world != null && world.equals(location.getWorld());
    }

    /**
     * Check if vendor is in the specified world by name
     */
    public boolean isInWorld(String worldName) {
        return worldName != null && worldName.equals(location.getWorld().getName());
    }

    /**
     * Get distance to a location (returns -1 if different worlds)
     */
    public double getDistanceTo(Location other) {
        if (other == null || !other.getWorld().equals(location.getWorld())) {
            return -1;
        }
        return location.distance(other);
    }

    /**
     * Check if vendor is within range of a location
     */
    public boolean isWithinRange(Location other, double range) {
        double distance = getDistanceTo(other);
        return distance >= 0 && distance <= range;
    }

    /**
     * Get world name
     */
    public String getWorldName() {
        return location.getWorld().getName();
    }

    /**
     * Validate vendor data integrity
     */
    public boolean isValid() {
        try {
            // Check basic data
            if (id == null || id.trim().isEmpty()) return false;
            if (npcId <= 0) return false;
            if (location == null) return false;
            if (location.getWorld() == null) return false;
            if (vendorType == null || vendorType.trim().isEmpty()) return false;

            // Check location bounds (reasonable world coordinates)
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();

            if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) return false;
            return !(y < -2000) && !(y > 2000);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get vendor type as formatted display name
     */
    public String getFormattedVendorType() {
        return vendorType.substring(0, 1).toUpperCase() + vendorType.substring(1).toLowerCase().replace('_', ' ');
    }

    /**
     * Check if this vendor has the specified type
     */
    public boolean isType(String type) {
        return type != null && vendorType.equalsIgnoreCase(type.trim());
    }

    /**
     * Get a summary string for logging/debugging
     */
    public String getSummary() {
        return String.format("Vendor{id='%s', npcId=%d, type='%s', world='%s', x=%.1f, y=%.1f, z=%.1f}",
                id, npcId, vendorType, getWorldName(),
                location.getX(), location.getY(), location.getZ());
    }

    /**
     * Create a copy of this vendor with a new ID
     */
    public Vendor copyWithNewId(String newId, int newNpcId) {
        return new Vendor(newId, newNpcId, location, vendorType, hologramLines);
    }

    /**
     * Check if two vendors represent the same logical vendor (same ID)
     */
    public boolean isSameVendor(Vendor other) {
        return other != null && id.equals(other.id);
    }

    /**
     * Check if two vendors are at the same location (within small tolerance)
     */
    public boolean isAtSameLocation(Vendor other) {
        if (other == null || !location.getWorld().equals(other.location.getWorld())) {
            return false;
        }

        double threshold = 0.1;
        return Math.abs(location.getX() - other.location.getX()) < threshold &&
                Math.abs(location.getY() - other.location.getY()) < threshold &&
                Math.abs(location.getZ() - other.location.getZ()) < threshold;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Vendor vendor = (Vendor) obj;
        return npcId == vendor.npcId &&
                Objects.equals(id, vendor.id) &&
                Objects.equals(vendorType, vendor.vendorType) &&
                Objects.equals(location.getWorld(), vendor.location.getWorld()) &&
                Math.abs(location.getX() - vendor.location.getX()) < 0.01 &&
                Math.abs(location.getY() - vendor.location.getY()) < 0.01 &&
                Math.abs(location.getZ() - vendor.location.getZ()) < 0.01;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, npcId, vendorType, location.getWorld().getName(),
                (int) location.getX(), (int) location.getY(), (int) location.getZ());
    }

    @Override
    public String toString() {
        return getSummary();
    }
}