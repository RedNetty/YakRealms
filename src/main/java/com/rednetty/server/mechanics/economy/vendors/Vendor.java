package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Enhanced Vendor class with improved thread safety, validation, and utility methods.
 * Simple data holder for a vendor's properties with comprehensive validation,
 * thread-safe operations, and enhanced type determination.
 */
public class Vendor {

    // Core vendor data
    private final String vendorId;
    private final int npcId;
    private volatile Location location;
    private volatile List<String> hologramLines;
    private volatile String behaviorClass;
    private volatile String vendorType;
    private volatile long lastUpdated;

    // Thread safety
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Validation state
    private volatile boolean isValid = false;
    private volatile String lastValidationError = null;

    // Performance tracking
    private volatile long creationTime;
    private volatile int accessCount = 0;
    private volatile long lastAccessTime;

    /**
     * Enhanced constructor with validation
     */
    public Vendor(String vendorId, int npcId, Location location,
                  List<String> hologramLines, String behaviorClass) {

        // Validate required parameters
        if (VendorUtils.isNullOrEmpty(vendorId)) {
            throw new IllegalArgumentException("Vendor ID cannot be null or empty");
        }

        if (!VendorUtils.isValidVendorId(vendorId)) {
            throw new IllegalArgumentException("Invalid vendor ID format: " + vendorId);
        }

        if (npcId <= 0) {
            throw new IllegalArgumentException("NPC ID must be positive: " + npcId);
        }

        // Initialize core data
        this.vendorId = VendorUtils.sanitizeVendorId(vendorId);
        this.npcId = npcId;
        this.creationTime = System.currentTimeMillis();
        this.lastUpdated = this.creationTime;
        this.lastAccessTime = this.creationTime;

        // Set behavior class and determine type FIRST
        setBehaviorClass(VendorUtils.getOrDefault(behaviorClass,
                "com.rednetty.server.mechanics.economy.vendors.behaviors.ShopBehavior"));

        // Set location with validation
        setLocation(location);

        // Set hologram lines with defaults based on determined type
        setHologramLines(hologramLines != null && !hologramLines.isEmpty() ?
                hologramLines : createDefaultHologramLinesForType(this.vendorType));

        // Validate the vendor
        validateVendor();
    }

    /**
     * Thread-safe vendor ID getter
     */
    public String getVendorId() {
        recordAccess();
        return vendorId;
    }

    /**
     * Thread-safe NPC ID getter
     */
    public int getNpcId() {
        recordAccess();
        return npcId;
    }

    /**
     * Thread-safe location getter with defensive copying
     */
    public Location getLocation() {
        recordAccess();
        lock.readLock().lock();
        try {
            return VendorUtils.safeCopyLocation(location);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Enhanced location setter with validation
     */
    public void setLocation(Location location) {
        if (location != null && !VendorUtils.isValidVendorLocation(location)) {
            throw new IllegalArgumentException("Invalid vendor location: " + location);
        }

        lock.writeLock().lock();
        try {
            this.location = VendorUtils.safeCopyLocation(location);
            this.lastUpdated = System.currentTimeMillis();
            validateVendor();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Thread-safe hologram lines getter with defensive copying
     */
    public List<String> getHologramLines() {
        recordAccess();
        lock.readLock().lock();
        try {
            return hologramLines != null ? new ArrayList<>(hologramLines) : new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Enhanced hologram lines setter with validation and smart defaults
     */
    public void setHologramLines(List<String> hologramLines) {
        lock.writeLock().lock();
        try {
            if (hologramLines == null || hologramLines.isEmpty()) {
                // Use current vendor type for defaults, fallback to "unknown"
                String currentType = this.vendorType != null ? this.vendorType : "unknown";
                this.hologramLines = createDefaultHologramLinesForType(currentType);
            } else {
                // Filter out null or empty lines
                List<String> validLines = new ArrayList<>();
                for (String line : hologramLines) {
                    if (!VendorUtils.isNullOrEmpty(line)) {
                        validLines.add(line);
                    }
                }
                this.hologramLines = validLines.isEmpty() ?
                        createDefaultHologramLinesForType(this.vendorType) : validLines;
            }
            this.lastUpdated = System.currentTimeMillis();
            validateVendor();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Thread-safe behavior class getter
     */
    public String getBehaviorClass() {
        recordAccess();
        lock.readLock().lock();
        try {
            return behaviorClass;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Enhanced behavior class setter with type determination
     */
    public void setBehaviorClass(String behaviorClass) {
        if (VendorUtils.isNullOrEmpty(behaviorClass)) {
            throw new IllegalArgumentException("Behavior class cannot be null or empty");
        }

        lock.writeLock().lock();
        try {
            this.behaviorClass = behaviorClass;
            this.vendorType = determineTypeFromBehavior(behaviorClass);
            this.lastUpdated = System.currentTimeMillis();

            // Update hologram lines if they're still default/empty
            if (this.hologramLines == null || this.hologramLines.isEmpty() ||
                    isGenericHologramLines(this.hologramLines)) {
                this.hologramLines = createDefaultHologramLinesForType(this.vendorType);
            }

            validateVendor();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Thread-safe vendor type getter
     */
    public String getVendorType() {
        recordAccess();
        lock.readLock().lock();
        try {
            return vendorType != null ? vendorType : "unknown";
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Enhanced type checking with case-insensitive comparison
     */
    public boolean isType(String type) {
        recordAccess();
        lock.readLock().lock();
        try {
            return VendorUtils.stringsEqualIgnoreCase(vendorType, type);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Thread-safe last updated getter
     */
    public long getLastUpdated() {
        recordAccess();
        return lastUpdated;
    }

    /**
     * Update the last updated timestamp
     */
    public void touch() {
        lock.writeLock().lock();
        try {
            this.lastUpdated = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Enhanced validation with detailed error tracking
     */
    public boolean isValid() {
        recordAccess();
        lock.readLock().lock();
        try {
            return isValid;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the last validation error if any
     */
    public String getLastValidationError() {
        lock.readLock().lock();
        try {
            return lastValidationError;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Comprehensive vendor validation
     */
    private void validateVendor() {
        StringBuilder errors = new StringBuilder();
        boolean valid = true;

        // Validate vendor ID
        if (VendorUtils.isNullOrEmpty(vendorId)) {
            errors.append("Vendor ID is null or empty; ");
            valid = false;
        } else if (!VendorUtils.isValidVendorId(vendorId)) {
            errors.append("Invalid vendor ID format; ");
            valid = false;
        }

        // Validate NPC ID
        if (npcId <= 0) {
            errors.append("NPC ID must be positive; ");
            valid = false;
        }

        // Validate location
        if (location == null) {
            errors.append("Location is null; ");
            valid = false;
        } else if (location.getWorld() == null) {
            errors.append("Location world is null; ");
            valid = false;
        } else if (!VendorUtils.isValidVendorLocation(location)) {
            errors.append("Invalid location coordinates; ");
            valid = false;
        }

        // Validate behavior class
        if (VendorUtils.isNullOrEmpty(behaviorClass)) {
            errors.append("Behavior class is null or empty; ");
            valid = false;
        }

        // Validate hologram lines
        if (hologramLines == null || hologramLines.isEmpty()) {
            errors.append("Hologram lines are null or empty; ");
            valid = false;
        }

        // Validate vendor type
        if (VendorUtils.isNullOrEmpty(vendorType)) {
            errors.append("Vendor type is null or empty; ");
            valid = false;
        }

        this.isValid = valid;
        this.lastValidationError = valid ? null : errors.toString().trim();
    }

    /**
     * Enhanced behavior class validation with caching
     */
    public boolean hasValidBehavior() {
        recordAccess();
        lock.readLock().lock();
        try {
            if (VendorUtils.isNullOrEmpty(behaviorClass)) {
                return false;
            }

            try {
                Class<?> clazz = Class.forName(behaviorClass);

                // Check if it implements VendorBehavior interface
                for (Class<?> iface : clazz.getInterfaces()) {
                    if ("VendorBehavior".equals(iface.getSimpleName())) {
                        return true;
                    }
                }

                // Check if superclass implements it
                Class<?> superClass = clazz.getSuperclass();
                while (superClass != null) {
                    for (Class<?> iface : superClass.getInterfaces()) {
                        if ("VendorBehavior".equals(iface.getSimpleName())) {
                            return true;
                        }
                    }
                    superClass = superClass.getSuperclass();
                }

                return false;
            } catch (ClassNotFoundException e) {
                return false;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Enhanced type determination with comprehensive behavior mapping
     */
    private String determineTypeFromBehavior(String behaviorClass) {
        if (VendorUtils.isNullOrEmpty(behaviorClass)) {
            return "unknown";
        }

        // Extract class name without package
        String className = behaviorClass;
        if (behaviorClass.contains(".")) {
            className = behaviorClass.substring(behaviorClass.lastIndexOf('.') + 1);
        }

        // Remove "Behavior" suffix if present
        if (className.endsWith("Behavior")) {
            String type = className.substring(0, className.length() - 8).toLowerCase();

            // Comprehensive mapping for all vendor types
            switch (type) {
                case "itemvendor":
                case "item":
                case "shop":
                    return "item";
                case "fisherman":
                    return "fisherman";
                case "bookvendor":
                case "book":
                    return "book";
                case "upgradevendor":
                case "upgrade":
                    return "upgrade";
                case "banker":
                    return "banker";
                case "medic":
                    return "medic";
                case "gambler":
                    return "gambler";
                default:
                    // For unknown types, try to extract meaningful name
                    return type.isEmpty() ? "unknown" : type;
            }
        }

        // Fallback: try to extract type from full class name
        String lowerCase = behaviorClass.toLowerCase();
        if (lowerCase.contains("item") || lowerCase.contains("shop")) return "item";
        if (lowerCase.contains("fisherman")) return "fisherman";
        if (lowerCase.contains("book")) return "book";
        if (lowerCase.contains("upgrade")) return "upgrade";
        if (lowerCase.contains("banker")) return "banker";
        if (lowerCase.contains("medic")) return "medic";
        if (lowerCase.contains("gambler")) return "gambler";

        return "unknown";
    }

    /**
     * Create appropriate default hologram lines for vendor type
     */
    private List<String> createDefaultHologramLinesForType(String vendorType) {
        List<String> lines = new ArrayList<>();

        switch (vendorType != null ? vendorType.toLowerCase() : "unknown") {
            case "item":
                lines.add(ChatColor.GOLD + "" + ChatColor.ITALIC + "Item Vendor");
                break;
            case "fisherman":
                lines.add(ChatColor.AQUA + "" + ChatColor.ITALIC + "Fisherman");
                break;
            case "book":
                lines.add(ChatColor.LIGHT_PURPLE + "" + ChatColor.ITALIC + "Book Vendor");
                break;
            case "upgrade":
                lines.add(ChatColor.YELLOW + "" + ChatColor.ITALIC + "Upgrade Vendor");
                break;
            case "banker":
                lines.add(ChatColor.GREEN + "" + ChatColor.ITALIC + "Banker");
                break;
            case "medic":
                lines.add(ChatColor.RED + "" + ChatColor.ITALIC + "Medic");
                break;
            case "gambler":
                lines.add(ChatColor.GOLD + "" + ChatColor.ITALIC + "Gambler");
                break;
            default:
                lines.add(ChatColor.GRAY + "" + ChatColor.ITALIC + "Vendor");
                break;
        }

        return lines;
    }

    /**
     * Check if hologram lines are generic/default
     */
    private boolean isGenericHologramLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return true;
        }

        for (String line : lines) {
            String stripped = ChatColor.stripColor(line).toLowerCase().trim();
            if (stripped.equals("vendor") || stripped.equals("unknown") || stripped.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Record access for performance tracking
     */
    private void recordAccess() {
        accessCount++;
        lastAccessTime = System.currentTimeMillis();
    }

    /**
     * Get performance metrics
     */
    public VendorMetrics getMetrics() {
        lock.readLock().lock();
        try {
            return new VendorMetrics(
                    vendorId,
                    creationTime,
                    lastUpdated,
                    lastAccessTime,
                    accessCount,
                    isValid,
                    lastValidationError
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if vendor location is in a loaded chunk
     */
    public boolean isLocationLoaded() {
        lock.readLock().lock();
        try {
            return VendorUtils.isChunkLoaded(location);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get distance to another vendor
     */
    public double getDistanceTo(Vendor other) {
        if (other == null) {
            return Double.MAX_VALUE;
        }

        lock.readLock().lock();
        try {
            return VendorUtils.safeDistance(this.location, other.location);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if vendor is within range of a location
     */
    public boolean isWithinRange(Location target, double range) {
        lock.readLock().lock();
        try {
            return VendorUtils.safeDistanceSquared(this.location, target) <= range * range;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get vendor age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Get time since last update in milliseconds
     */
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdated;
    }

    /**
     * Get time since last access in milliseconds
     */
    public long getTimeSinceLastAccess() {
        return System.currentTimeMillis() - lastAccessTime;
    }

    /**
     * Create a safe copy of this vendor
     */
    public Vendor copy() {
        lock.readLock().lock();
        try {
            return new Vendor(vendorId, npcId, location, hologramLines, behaviorClass);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Enhanced equals method
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Vendor vendor = (Vendor) obj;
        return npcId == vendor.npcId && Objects.equals(vendorId, vendor.vendorId);
    }

    /**
     * Enhanced hashCode method
     */
    @Override
    public int hashCode() {
        return Objects.hash(vendorId, npcId);
    }

    /**
     * Enhanced toString with more information
     */
    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return String.format("Vendor{id='%s', npcId=%d, type='%s', behavior='%s', valid=%s, location=%s}",
                    vendorId, npcId, vendorType,
                    behaviorClass != null ? behaviorClass.substring(behaviorClass.lastIndexOf('.') + 1) : "null",
                    isValid,
                    location != null ? String.format("%.1f,%.1f,%.1f in %s",
                            location.getX(), location.getY(), location.getZ(),
                            location.getWorld().getName()) : "null");
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Detailed string representation for debugging
     */
    public String toDetailedString() {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Vendor Details:\n");
            sb.append("  ID: ").append(vendorId).append("\n");
            sb.append("  NPC ID: ").append(npcId).append("\n");
            sb.append("  Type: ").append(vendorType).append("\n");
            sb.append("  Behavior: ").append(behaviorClass).append("\n");
            sb.append("  Valid: ").append(isValid).append("\n");
            if (lastValidationError != null) {
                sb.append("  Validation Error: ").append(lastValidationError).append("\n");
            }
            sb.append("  Location: ").append(location).append("\n");
            sb.append("  Hologram Lines: ").append(hologramLines.size()).append(" lines\n");
            for (int i = 0; i < hologramLines.size(); i++) {
                sb.append("    ").append(i + 1).append(": ").append(hologramLines.get(i)).append("\n");
            }
            sb.append("  Created: ").append(VendorUtils.formatTimestamp(creationTime)).append("\n");
            sb.append("  Last Updated: ").append(VendorUtils.formatTimestamp(lastUpdated)).append("\n");
            sb.append("  Access Count: ").append(accessCount).append("\n");
            sb.append("  Age: ").append(VendorUtils.formatDuration(getAge())).append("\n");
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Inner class for vendor metrics
     */
    public static class VendorMetrics {
        public final String vendorId;
        public final long creationTime;
        public final long lastUpdated;
        public final long lastAccessTime;
        public final int accessCount;
        public final boolean isValid;
        public final String lastValidationError;

        public VendorMetrics(String vendorId, long creationTime, long lastUpdated,
                             long lastAccessTime, int accessCount, boolean isValid,
                             String lastValidationError) {
            this.vendorId = vendorId;
            this.creationTime = creationTime;
            this.lastUpdated = lastUpdated;
            this.lastAccessTime = lastAccessTime;
            this.accessCount = accessCount;
            this.isValid = isValid;
            this.lastValidationError = lastValidationError;
        }

        public long getAge() {
            return System.currentTimeMillis() - creationTime;
        }

        public long getTimeSinceLastUpdate() {
            return System.currentTimeMillis() - lastUpdated;
        }

        public long getTimeSinceLastAccess() {
            return System.currentTimeMillis() - lastAccessTime;
        }
    }
}