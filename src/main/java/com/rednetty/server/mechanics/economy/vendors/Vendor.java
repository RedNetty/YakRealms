package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.List;

/**
 * Simple data holder for a vendor's properties.
 * Enhanced with improved type determination and validation.
 */
public class Vendor {
    private final String vendorId;      // Unique string ID for this vendor
    private final int npcId;           // Citizens NPC ID
    private Location location;         // Fallback or known spawn location
    private List<String> hologramLines; // Lines of text for the hologram
    private String behaviorClass;      // e.g. "com.rednetty.server.mechanics.economy.vendors.behaviors.ShopBehavior"
    private String vendorType;         // Vendor type for easier identification (item, fisherman, etc.)
    private long lastUpdated;          // Timestamp of last update to this vendor

    /**
     * Creates a new vendor with the specified properties
     *
     * @param vendorId      The unique vendor ID
     * @param npcId         The Citizens NPC ID
     * @param location      The vendor's location
     * @param hologramLines The hologram text lines
     * @param behaviorClass The behavior class path
     */
    public Vendor(String vendorId,
                  int npcId,
                  Location location,
                  List<String> hologramLines,
                  String behaviorClass) {
        this.vendorId = vendorId;
        this.npcId = npcId;
        this.location = location;
        this.hologramLines = hologramLines;
        this.behaviorClass = behaviorClass;
        this.lastUpdated = System.currentTimeMillis();

        // Determine vendor type from behavior class with improved logic
        this.vendorType = determineTypeFromBehavior(behaviorClass);


    }

    /**
     * Determine vendor type from behavior class
     *
     * @param behaviorClass The behavior class name
     * @return The determined vendor type
     */
    private String determineTypeFromBehavior(String behaviorClass) {
        if (behaviorClass == null || behaviorClass.isEmpty()) {
            return "unknown";
        }

        // Extract class name without package
        String className = behaviorClass;
        if (behaviorClass.contains(".")) {
            className = behaviorClass.substring(behaviorClass.lastIndexOf('.') + 1);
        }

        // Remove "Behavior" suffix if present
        if (className.endsWith("Behavior")) {
            return className.substring(0, className.length() - 8).toLowerCase();
        } else {
            return "unknown";
        }
    }

    /**
     * Get the vendor's unique ID
     *
     * @return The vendor ID
     */
    public String getVendorId() {
        return vendorId;
    }

    /**
     * Get the Citizens NPC ID
     *
     * @return The NPC ID
     */
    public int getNpcId() {
        return npcId;
    }

    /**
     * Get the vendor's location
     *
     * @return The vendor's location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Set the vendor's location
     *
     * @param location The new location
     */
    public void setLocation(Location location) {
        this.location = location;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Get the hologram text lines
     *
     * @return The hologram lines
     */
    public List<String> getHologramLines() {
        return hologramLines;
    }

    /**
     * Set the hologram text lines
     *
     * @param hologramLines The new hologram lines
     */
    public void setHologramLines(List<String> hologramLines) {
        this.hologramLines = hologramLines;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Get the vendor's behavior class
     *
     * @return The behavior class path
     */
    public String getBehaviorClass() {
        return behaviorClass;
    }

    /**
     * Set the vendor's behavior class
     *
     * @param behaviorClass The new behavior class path
     */
    public void setBehaviorClass(String behaviorClass) {
        this.behaviorClass = behaviorClass;
        this.vendorType = determineTypeFromBehavior(behaviorClass);
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Get the vendor type
     *
     * @return The vendor type
     */
    public String getVendorType() {
        return vendorType;
    }

    /**
     * Check if this vendor is of a specific type
     *
     * @param type The type to check
     * @return true if this vendor is of the specified type
     */
    public boolean isType(String type) {
        return vendorType.equalsIgnoreCase(type);
    }

    /**
     * Get the timestamp of when this vendor was last updated
     *
     * @return The last updated timestamp
     */
    public long getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Update the last updated timestamp to current time
     */
    public void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Validate this vendor's data
     *
     * @return true if the vendor data is valid
     */
    public boolean isValid() {
        return vendorId != null && !vendorId.isEmpty() &&
                npcId > 0 &&
                location != null && location.getWorld() != null &&
                behaviorClass != null && !behaviorClass.isEmpty();
    }

    /**
     * Check if this vendor has a valid behavior class
     *
     * @return true if the behavior class exists and is assignable to VendorBehavior
     */
    public boolean hasValidBehavior() {
        if (behaviorClass == null || behaviorClass.isEmpty()) {
            return false;
        }

        try {
            Class<?> clazz = Class.forName(behaviorClass);
            return clazz.getInterfaces().length > 0 &&
                    clazz.getInterfaces()[0].getSimpleName().equals("VendorBehavior");
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "Vendor{" +
                "id='" + vendorId + '\'' +
                ", npcId=" + npcId +
                ", type='" + vendorType + '\'' +
                ", behavior='" + behaviorClass + '\'' +
                '}';
    }
}