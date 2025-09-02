package com.rednetty.server.core.mechanics.world.lootchests;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * VaultChest class with robust null-safety for location handling
 * Fixes serialization issues and null pointer exceptions
 *
 * Version 4.1 - Critical null-safety fixes
 */
public class VaultChest {

    private final String id;
    private transient Location location; // Make transient to handle serialization manually
    private final LootChestManager.ChestTier tier;
    private final LootChestManager.ChestType type;
    private long createdTime;
    private String createdBy;
    private int timesOpened;
    private long lastOpenedTime;

    // Location data for JSON serialization (instead of Location object)
    private String worldName;
    private double x, y, z;
    private float yaw, pitch;

    // Transient fields (not saved to JSON) - minimal state tracking for instant refresh
    private transient boolean currentlyAnimating = false;

    public VaultChest(String id, Location location, LootChestManager.ChestTier tier,
                      LootChestManager.ChestType type) {
        this.id = id;
        this.tier = tier;
        this.type = type;
        this.createdTime = System.currentTimeMillis();
        this.timesOpened = 0;
        this.lastOpenedTime = 0;
        this.currentlyAnimating = false;

        // Safe location handling
        setLocation(location);
    }

    // ========================================
    // LOCATION HANDLING WITH NULL-SAFETY
    // ========================================

    /**
     * Safe location setter with null checking and serialization data update
     */
    public void setLocation(Location location) {
        if (location != null) {
            this.location = location.clone();
            // Update serialization data
            this.worldName = location.getWorld().getName();
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.yaw = location.getYaw();
            this.pitch = location.getPitch();
        } else {
            this.location = null;
            this.worldName = null;
            this.x = this.y = this.z = 0;
            this.yaw = this.pitch = 0;
        }
    }

    /**
     * Safe location getter with automatic reconstruction from serialization data
     */
    public Location getLocation() {
        // If location is null but we have serialization data, reconstruct it
        if (location == null && worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                location = new Location(world, x, y, z, yaw, pitch);
            }
        }

        return location != null ? location.clone() : null;
    }

    /**
     * Check if location is valid and accessible
     */
    public boolean hasValidLocation() {
        Location loc = getLocation();
        return loc != null && loc.getWorld() != null;
    }

    /**
     * Get world name safely
     */
    public String getWorldName() {
        if (worldName != null) {
            return worldName;
        }

        Location loc = getLocation();
        if (loc != null && loc.getWorld() != null) {
            return loc.getWorld().getName();
        }

        return "unknown";
    }

    /**
     * Safe world check
     */
    public boolean isInWorld(String worldName) {
        return Objects.equals(getWorldName(), worldName);
    }

    /**
     * Safe distance calculation with null checks
     */
    public double getDistanceTo(Location otherLocation) {
        if (otherLocation == null) {
            return Double.MAX_VALUE;
        }

        Location myLocation = getLocation();
        if (myLocation == null || !Objects.equals(myLocation.getWorld(), otherLocation.getWorld())) {
            return Double.MAX_VALUE;
        }

        return myLocation.distance(otherLocation);
    }

    // ========================================
    // GETTERS
    // ========================================

    public String getId() { return id; }
    public LootChestManager.ChestTier getTier() { return tier; }
    public LootChestManager.ChestType getType() { return type; }
    public long getCreatedTime() { return createdTime; }
    public String getCreatedBy() { return createdBy; }
    public int getTimesOpened() { return timesOpened; }
    public long getLastOpenedTime() { return lastOpenedTime; }

    // Transient getters for instant refresh system
    public boolean isCurrentlyAnimating() { return currentlyAnimating; }

    // ========================================
    // SETTERS
    // ========================================

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setOpened(boolean opened) {
        if (opened) {
            this.timesOpened++;
            this.lastOpenedTime = System.currentTimeMillis();
        }
    }

    public void incrementTimesOpened() {
        this.timesOpened++;
        this.lastOpenedTime = System.currentTimeMillis();
    }

    public void setCurrentlyAnimating(boolean animating) {
        this.currentlyAnimating = animating;
    }

    // ========================================
    // INSTANT REFRESH METHODS
    // ========================================

    /**
     * Check if vault is available for immediate use
     * With instant refresh, only animation status matters
     */
    public boolean isAvailableForUse() {
        return !currentlyAnimating && hasValidLocation();
    }

    /**
     * Get availability status for display
     */
    public String getAvailabilityStatus() {
        if (!hasValidLocation()) {
            return "§c✗ Invalid Location";
        }
        if (currentlyAnimating) {
            return "§e⚡ Key Animation Active";
        } else {
            return "§a✓ Ready (Instant Refresh)";
        }
    }

    /**
     * Get seconds since last use (useful for statistics)
     */
    public long getSecondsSinceLastUse() {
        if (lastOpenedTime == 0) {
            return -1; // Never used
        }
        return (System.currentTimeMillis() - lastOpenedTime) / 1000;
    }

    /**
     * Check if vault was used very recently (within last 10 seconds)
     */
    public boolean isRecentlyUsed() {
        long secondsSince = getSecondsSinceLastUse();
        return secondsSince >= 0 && secondsSince < 10;
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    public String getDisplayName() {
        return tier.getDisplayName() + " " + type.getName() + " Vault";
    }

    public String getFullDisplayName() {
        return tier.getDisplayName() + " " + type.getName() + " Vault";
    }

    /**
     * Get vault age in days
     */
    public long getAgeInDays() {
        return (System.currentTimeMillis() - createdTime) / (24 * 60 * 60 * 1000);
    }

    /**
     * Get time since last opened in hours
     */
    public long getHoursSinceLastOpened() {
        if (lastOpenedTime == 0) {
            return -1; // Never opened
        }
        return (System.currentTimeMillis() - lastOpenedTime) / (60 * 60 * 1000);
    }

    /**
     * Check if vault is considered "fresh" (recently created)
     */
    public boolean isFresh() {
        return getAgeInDays() < 1;
    }

    /**
     * Get vault status description for instant refresh system
     */
    public String getStatusDescription() {
        if (!hasValidLocation()) {
            return "Invalid Location - Needs Repair";
        }
        if (currentlyAnimating) {
            return "Key Animation in Progress";
        } else if (timesOpened == 0) {
            return "Ready (Never Used) - Instant Refresh";
        } else if (isRecentlyUsed()) {
            return "Ready (Just Used - " + timesOpened + " times) - Instant Refresh";
        } else {
            return "Ready (" + timesOpened + " times opened) - Instant Refresh";
        }
    }

    /**
     * Get vault quality rating based on tier
     */
    public double getQualityRating() {
        return tier.getLevel() / 6.0; // Scale from 0.16 to 1.0
    }

    /**
     * Get estimated value based on tier and type
     */
    public int getEstimatedValue() {
        int baseValue = tier.getLevel() * 100;

        switch (type) {
            case ELITE:
                return (int) (baseValue * 1.5);
            case FOOD:
                return (int) (baseValue * 0.8);
            default:
                return baseValue;
        }
    }

    /**
     * Get usage frequency description
     */
    public String getUsageFrequency() {
        if (timesOpened == 0) {
            return "§7Unused";
        } else if (timesOpened < 5) {
            return "§e" + timesOpened + " uses";
        } else if (timesOpened < 20) {
            return "§a" + timesOpened + " uses";
        } else if (timesOpened < 50) {
            return "§6" + timesOpened + " uses §7(Popular!)";
        } else {
            return "§6" + timesOpened + " uses §7(Very Active!)";
        }
    }

    /**
     * Check if this vault would be good for a mob drop - null-safe version
     */
    public boolean isGoodForMobDrop(Location mobLocation, double maxDistance) {
        if (mobLocation == null || !hasValidLocation()) {
            return false;
        }

        return getDistanceTo(mobLocation) <= maxDistance && !currentlyAnimating;
    }

    /**
     * Get a compact info string for lists - shows instant refresh status
     */
    public String getCompactInfo() {
        String statusInfo = "";
        if (!hasValidLocation()) {
            statusInfo = " §c[INVALID]";
        } else if (currentlyAnimating) {
            statusInfo = " §e[ANIMATING]";
        } else {
            statusInfo = " §a[READY ⚡]";
        }

        return tier.getColor() + getDisplayName() + " §7(" + getUsageFrequency() + ")" + statusInfo;
    }

    // ========================================
    // OBJECT METHODS
    // ========================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VaultChest vaultChest = (VaultChest) o;
        return Objects.equals(id, vaultChest.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "VaultChest{" +
                "id='" + id + '\'' +
                ", world='" + getWorldName() + '\'' +
                ", tier=" + tier +
                ", type=" + type +
                ", timesOpened=" + timesOpened +
                ", createdBy='" + createdBy + '\'' +
                ", ageInDays=" + getAgeInDays() +
                ", instantRefresh=true" +
                ", validLocation=" + hasValidLocation() +
                '}';
    }

    /**
     * Get a detailed information string for admin purposes - with location safety
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("§6═══ Vault Information (Instant Refresh) ═══\n");
        info.append("§7ID: §f").append(id.substring(0, 8)).append("...\n");
        info.append("§7Type: ").append(tier.getColor()).append(getDisplayName()).append("\n");
        info.append("§7Created By: §f").append(createdBy != null ? createdBy : "Unknown").append("\n");
        info.append("§7Status: §f").append(getStatusDescription()).append("\n");
        info.append("§7Usage: ").append(getUsageFrequency()).append("\n");
        info.append("§7Age: §f").append(getAgeInDays()).append(" days\n");
        info.append("§7Estimated Value: §f").append(getEstimatedValue()).append(" coins\n");
        info.append("§a§lRefresh: INSTANT ⚡ (No cooldown)\n");
        info.append("§a§lOpening: AUTOMATIC (No manual interaction)\n");

        if (hasValidLocation()) {
            info.append("§7Location: §f").append(formatLocation()).append("\n");
        } else {
            info.append("§cLocation: INVALID - Needs repair\n");
        }

        if (lastOpenedTime > 0) {
            long secondsSince = getSecondsSinceLastUse();
            if (secondsSince < 60) {
                info.append("§7Last Opened: §f").append(secondsSince).append(" seconds ago\n");
            } else if (secondsSince < 3600) {
                info.append("§7Last Opened: §f").append(secondsSince / 60).append(" minutes ago\n");
            } else {
                info.append("§7Last Opened: §f").append(getHoursSinceLastOpened()).append(" hours ago\n");
            }
        } else {
            info.append("§7Last Opened: §fNever\n");
        }

        info.append("§8System: Instant Refresh Enabled\n");
        info.append("§8Animation Only Delay: ~3 seconds\n");

        return info.toString();
    }

    /**
     * Get location coordinates for admin display - null safe
     */
    public String getLocationString() {
        if (!hasValidLocation()) {
            return "§cInvalid Location (World: " + (worldName != null ? worldName : "null") + ")";
        }
        return formatLocation();
    }

    private String formatLocation() {
        if (hasValidLocation()) {
            Location loc = getLocation();
            return String.format("%s: %d, %d, %d",
                    loc.getWorld().getName(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ()
            );
        } else {
            return String.format("%s: %.0f, %.0f, %.0f",
                    worldName != null ? worldName : "unknown",
                    x, y, z
            );
        }
    }

    /**
     * Create a summary for admin commands - null safe
     */
    public String getAdminSummary() {
        String statusIndicator;
        if (!hasValidLocation()) {
            statusIndicator = "§c[INVALID]";
        } else if (currentlyAnimating) {
            statusIndicator = "§e[ANIMATING]";
        } else {
            statusIndicator = "§a[READY⚡]";
        }

        return String.format("§7[§f%s§7] %s §7at %s §7- %s %s",
                id.substring(0, 8),
                tier.getColor() + getDisplayName(),
                getLocationString(),
                getUsageFrequency(),
                statusIndicator
        );
    }

    /**
     * Check if this vault is suitable for the given location and constraints - null safe
     */
    public boolean isSuitableFor(Location targetLocation, double maxDistance, boolean requireAvailable) {
        if (targetLocation == null || !hasValidLocation()) {
            return false;
        }

        double distance = getDistanceTo(targetLocation);
        if (distance > maxDistance) {
            return false;
        }

        // Only check animation status for availability - no cooldowns
        if (requireAvailable && currentlyAnimating) {
            return false;
        }

        return true;
    }

    /**
     * Get performance metrics for the instant refresh system
     */
    public String getPerformanceMetrics() {
        StringBuilder metrics = new StringBuilder();
        metrics.append("§6Performance Metrics (Instant Refresh):\n");
        metrics.append("§7Total Uses: §f").append(timesOpened).append("\n");

        if (timesOpened > 0) {
            long totalUptime = System.currentTimeMillis() - createdTime;
            double usesPerDay = (double) timesOpened / (totalUptime / (24.0 * 60 * 60 * 1000));
            metrics.append("§7Average Uses/Day: §f").append(String.format("%.1f", usesPerDay)).append("\n");
        }

        metrics.append("§7Current Status: ").append(getAvailabilityStatus()).append("\n");
        metrics.append("§7Instant Refresh: §a✓ ENABLED\n");
        metrics.append("§7Animation Only Delay: §f~3 seconds\n");
        metrics.append("§7Location Valid: ").append(hasValidLocation() ? "§a✓" : "§c✗").append("\n");

        return metrics.toString();
    }

    /**
     * Get a quick status for automated systems
     */
    public boolean isInstantlyAvailable() {
        return !currentlyAnimating && hasValidLocation();
    }

    /**
     * Get the automation level description
     */
    public String getAutomationLevel() {
        return "§a§lFULLY AUTOMATIC ⚡";
    }

    /**
     * Repair invalid location (admin function)
     */
    public boolean repairLocation(Location newLocation) {
        if (newLocation != null) {
            setLocation(newLocation);
            return hasValidLocation();
        }
        return false;
    }
}