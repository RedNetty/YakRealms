package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Enhanced hologram manager for vendors with improved error handling, performance tracking,
 * and intelligent caching. Manages vendor holograms with comprehensive validation,
 * automatic recovery mechanisms, and optimized update cycles.
 */
public class VendorHologramManager {

    private final JavaPlugin plugin;
    private final VendorManager vendorManager;

    // Enhanced tracking with thread-safe collections
    private final Map<String, Long> activeHolograms = new ConcurrentHashMap<>();
    private final Map<String, Location> lastKnownLocations = new ConcurrentHashMap<>();
    private final Set<String> failedHolograms = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();
    private final Map<String, String> lastKnownTypes = new ConcurrentHashMap<>();

    // Performance and metrics tracking
    private final AtomicInteger totalHologramsCreated = new AtomicInteger(0);
    private final AtomicInteger totalHologramsRemoved = new AtomicInteger(0);
    private final AtomicInteger failedCreationAttempts = new AtomicInteger(0);
    private final AtomicLong lastValidationTime = new AtomicLong(0);

    // Task management
    private BukkitTask validationTask;
    private BukkitTask maintenanceTask;

    // Configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long VALIDATION_INTERVAL = 20 * 60 * 15; // 15 minutes
    private static final long MAINTENANCE_INTERVAL = 20 * 60 * 5; // 5 minutes
    private static final long HOLOGRAM_UPDATE_THRESHOLD = 3600000; // 1 hour
    private static final double LOCATION_CHANGE_THRESHOLD = 0.1; // 0.1 block difference

    /**
     * Enhanced constructor with comprehensive initialization
     */
    public VendorHologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance(plugin);

        // Start management tasks
        startValidationTask();
        startMaintenanceTask();

        // Schedule initial hologram refresh with delay
        new BukkitRunnable() {
            @Override
            public void run() {
                performInitialRefresh();
            }
        }.runTaskLater(plugin, 100L); // 5 seconds after initialization

        plugin.getLogger().info("Enhanced VendorHologramManager initialized with automatic validation and maintenance");
    }

    /**
     * Enhanced initial refresh with staggered processing and better error handling
     */
    private void performInitialRefresh() {
        try {
            Map<String, Vendor> vendors = vendorManager.getVendors();
            plugin.getLogger().info("Performing initial hologram refresh for " + vendors.size() + " vendors");

            if (vendors.isEmpty()) {
                plugin.getLogger().info("No vendors found for hologram refresh");
                return;
            }

            // Clear any existing tracking data
            activeHolograms.clear();
            lastKnownLocations.clear();
            lastKnownTypes.clear();

            // Staggered processing to prevent lag
            List<String> vendorIds = new ArrayList<>(vendors.keySet());
            AtomicInteger processed = new AtomicInteger(0);

            BukkitTask refreshTask = new BukkitRunnable() {
                @Override
                public void run() {
                    int batchSize = Math.min(3, vendorIds.size() - processed.get());

                    for (int i = 0; i < batchSize; i++) {
                        int index = processed.getAndIncrement();
                        if (index >= vendorIds.size()) {
                            this.cancel();
                            plugin.getLogger().info("Initial hologram refresh completed");
                            return;
                        }

                        String vendorId = vendorIds.get(index);
                        Vendor vendor = vendors.get(vendorId);

                        if (vendor != null) {
                            try {
                                refreshSingleHologram(vendor);
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.WARNING, "Error refreshing hologram for vendor " + vendorId, e);
                                handleHologramError(vendorId, "initial refresh", e);
                            }
                        }
                    }

                    if (processed.get() >= vendorIds.size()) {
                        this.cancel();
                        plugin.getLogger().info("Initial hologram refresh completed - processed " + processed.get() + " vendors");
                    }
                }
            }.runTaskTimer(plugin, 0L, 10L); // Every 0.5 seconds

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during initial hologram refresh", e);
        }
    }

    /**
     * Enhanced hologram creation with comprehensive validation and vendor type checking
     */
    public boolean createHologram(Vendor vendor) {
        if (vendor == null || !vendor.isValid()) {
            plugin.getLogger().warning("Cannot create hologram for invalid vendor");
            return false;
        }

        String vendorId = vendor.getVendorId();

        try {
            // Check if vendor is in failed state
            if (failedHolograms.contains(vendorId)) {
                int attempts = retryAttempts.getOrDefault(vendorId, 0);
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    return false; // Don't keep trying failed vendors
                }
            }

            // Ensure vendor has proper type before creating hologram
            if ("unknown".equals(vendor.getVendorType())) {
                plugin.getLogger().warning("Vendor " + vendorId + " has unknown type, attempting to fix...");
                fixVendorType(vendor);
            }

            Location hologramLocation = calculateHologramLocation(vendor);
            if (hologramLocation == null) {
                handleHologramError(vendorId, "location calculation",
                        new IllegalStateException("Could not determine hologram location"));
                return false;
            }

            // Validate and fix hologram lines
            List<String> hologramLines = vendor.getHologramLines();
            if (hologramLines == null || hologramLines.isEmpty() || areGenericHologramLines(hologramLines)) {
                hologramLines = createAppropriateHologramLines(vendor.getVendorType());
                vendor.setHologramLines(hologramLines); // Update the vendor
                plugin.getLogger().info("Updated hologram lines for vendor " + vendorId + " (type: " + vendor.getVendorType() + ")");
            }

            // Remove any existing hologram first
            try {
                HologramManager.removeHologram(vendorId);
            } catch (Exception e) {
                // Ignore removal errors
            }

            // Create the hologram with enhanced error handling
            try {
                HologramManager.createOrUpdateHologram(
                        vendorId,
                        hologramLocation,
                        hologramLines,
                        0.30
                );

                // Track successful creation
                activeHolograms.put(vendorId, System.currentTimeMillis());
                lastKnownLocations.put(vendorId, hologramLocation.clone());
                lastKnownTypes.put(vendorId, vendor.getVendorType());
                totalHologramsCreated.incrementAndGet();

                // Clear failure state
                failedHolograms.remove(vendorId);
                retryAttempts.remove(vendorId);

                plugin.getLogger().info("Successfully created hologram for vendor " + vendorId +
                        " (type: " + vendor.getVendorType() + ")");
                return true;

            } catch (Exception e) {
                handleHologramError(vendorId, "hologram creation", e);
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Unexpected error creating hologram for vendor " + vendorId, e);
            handleHologramError(vendorId, "unexpected error", e);
            return false;
        }
    }

    /**
     * Fix vendor type if it's unknown
     */
    private void fixVendorType(Vendor vendor) {
        try {
            String vendorId = vendor.getVendorId();
            String behaviorClass = vendor.getBehaviorClass();

            if (behaviorClass != null && !behaviorClass.isEmpty()) {
                // Re-set behavior class to trigger type determination
                vendor.setBehaviorClass(behaviorClass);
            } else {
                // Determine behavior from vendor ID
                String fixedBehavior = determineDefaultBehaviorFromId(vendorId);
                vendor.setBehaviorClass(fixedBehavior);
            }

            plugin.getLogger().info("Fixed vendor " + vendorId + " type from unknown to " + vendor.getVendorType());
        } catch (Exception e) {
            plugin.getLogger().warning("Error fixing vendor type for " + vendor.getVendorId() + ": " + e.getMessage());
        }
    }

    /**
     * Determine default behavior from vendor ID
     */
    private String determineDefaultBehaviorFromId(String vendorId) {
        String basePath = "com.rednetty.server.mechanics.economy.vendors.behaviors.";
        String lowerVendorId = vendorId.toLowerCase();

        if (lowerVendorId.contains("item") || lowerVendorId.contains("shop")) {
            return basePath + "ItemVendorBehavior";
        } else if (lowerVendorId.contains("fish")) {
            return basePath + "FishermanBehavior";
        } else if (lowerVendorId.contains("book")) {
            return basePath + "BookVendorBehavior";
        } else if (lowerVendorId.contains("upgrade")) {
            return basePath + "UpgradeVendorBehavior";
        } else if (lowerVendorId.contains("bank")) {
            return basePath + "BankerBehavior";
        } else if (lowerVendorId.contains("medic") || lowerVendorId.contains("heal")) {
            return basePath + "MedicBehavior";
        } else if (lowerVendorId.contains("gambl")) {
            return basePath + "GamblerBehavior";
        }

        return basePath + "ShopBehavior";
    }

    /**
     * Check if hologram lines are generic/default
     */
    private boolean areGenericHologramLines(List<String> lines) {
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
     * Create appropriate hologram lines for vendor type
     */
    private List<String> createAppropriateHologramLines(String vendorType) {
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
                lines.add(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "Gambler");
                break;
            default:
                lines.add(ChatColor.GRAY + "" + ChatColor.ITALIC + "Vendor");
                break;
        }

        return lines;
    }

    /**
     * Enhanced location calculation with multiple fallback methods
     */
    private Location calculateHologramLocation(Vendor vendor) {
        try {
            // Primary method: Get location from spawned NPC
            NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
            if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
                Location npcLocation = npc.getEntity().getLocation();
                if (VendorUtils.isValidVendorLocation(npcLocation)) {
                    return npcLocation.clone().add(0, 2.2, 0);
                }
            }

            // Fallback method: Use stored vendor location
            Location vendorLocation = vendor.getLocation();
            if (vendorLocation != null && VendorUtils.isValidVendorLocation(vendorLocation)) {
                return vendorLocation.clone().add(0, 2.2, 0);
            }

            // Last resort: Use last known location
            Location lastKnown = lastKnownLocations.get(vendor.getVendorId());
            if (lastKnown != null && VendorUtils.isValidVendorLocation(lastKnown)) {
                return lastKnown.clone();
            }

            return null;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error calculating hologram location for vendor " + vendor.getVendorId(), e);
            return null;
        }
    }

    /**
     * Enhanced hologram refresh with better validation and type checking
     */
    public boolean refreshSingleHologram(Vendor vendor) {
        if (vendor == null || !vendor.isValid()) {
            return false;
        }

        String vendorId = vendor.getVendorId();

        try {
            // Ensure vendor has proper type and hologram lines
            if ("unknown".equals(vendor.getVendorType()) ||
                    vendor.getHologramLines() == null ||
                    vendor.getHologramLines().isEmpty() ||
                    areGenericHologramLines(vendor.getHologramLines())) {

                plugin.getLogger().info("Fixing vendor " + vendorId + " with invalid type/hologram data");

                // Fix vendor type first
                fixVendorType(vendor);

                // Update hologram lines based on corrected type
                List<String> newLines = createAppropriateHologramLines(vendor.getVendorType());
                vendor.setHologramLines(newLines);

                // Save the fixes
                vendorManager.saveVendorsToConfig();
            }

            // Check if type has changed from what we last knew
            String lastKnownType = lastKnownTypes.get(vendorId);
            if (lastKnownType != null && !lastKnownType.equals(vendor.getVendorType())) {
                plugin.getLogger().info("Vendor " + vendorId + " type changed from " + lastKnownType +
                        " to " + vendor.getVendorType() + " - updating hologram");
            }

            // Remove existing hologram first
            removeHologram(vendorId);

            // Small delay to ensure cleanup, then create new hologram
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (createHologram(vendor)) {
                        plugin.getLogger().info("Successfully refreshed hologram for vendor " + vendorId +
                                " (type: " + vendor.getVendorType() + ")");
                    } else {
                        plugin.getLogger().warning("Failed to refresh hologram for vendor " + vendorId);
                    }
                }
            }.runTaskLater(plugin, 5L);

            return true;

        } catch (Exception e) {
            handleHologramError(vendorId, "refresh", e);
            return false;
        }
    }

    /**
     * Enhanced hologram removal with verification and cleanup
     */
    public boolean removeHologram(String vendorId) {
        if (VendorUtils.isNullOrEmpty(vendorId)) {
            return false;
        }

        try {
            // Remove from HologramManager
            HologramManager.removeHologram(vendorId);

            // Update tracking
            activeHolograms.remove(vendorId);
            totalHologramsRemoved.incrementAndGet();

            plugin.getLogger().info("Removed hologram for vendor: " + vendorId);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error removing hologram for vendor " + vendorId, e);
            return false;
        }
    }

    /**
     * Enhanced hologram update with location change detection and type validation
     */
    public boolean updateVendorHologram(Vendor vendor) {
        if (vendor == null) return false;

        String vendorId = vendor.getVendorId();

        try {
            // Check if vendor type has changed
            String currentType = vendor.getVendorType();
            String lastKnownType = lastKnownTypes.get(vendorId);
            boolean typeChanged = lastKnownType == null || !lastKnownType.equals(currentType);

            // Check if location has changed significantly
            Location currentLocation = calculateHologramLocation(vendor);
            Location lastLocation = lastKnownLocations.get(vendorId);

            boolean locationChanged = currentLocation == null || lastLocation == null ||
                    !VendorUtils.locationsEqual(currentLocation, lastLocation, LOCATION_CHANGE_THRESHOLD);

            // Check if hologram needs updating
            Long lastUpdate = activeHolograms.get(vendorId);
            boolean timeToUpdate = lastUpdate == null ||
                    (System.currentTimeMillis() - lastUpdate) > HOLOGRAM_UPDATE_THRESHOLD;

            // Check if hologram lines are appropriate for current type
            boolean hologramNeedsUpdate = areGenericHologramLines(vendor.getHologramLines()) ||
                    vendor.getHologramLines() == null || vendor.getHologramLines().isEmpty();

            if (typeChanged || locationChanged || timeToUpdate || hologramNeedsUpdate) {
                plugin.getLogger().info("Updating hologram for vendor " + vendorId +
                        " - type changed: " + typeChanged + ", location changed: " + locationChanged +
                        ", time to update: " + timeToUpdate + ", lines need update: " + hologramNeedsUpdate);
                return refreshSingleHologram(vendor);
            }

            return true; // No update needed

        } catch (Exception e) {
            handleHologramError(vendorId, "update", e);
            return false;
        }
    }

    /**
     * Start validation task for periodic hologram health checks
     */
    private void startValidationTask() {
        if (validationTask != null) {
            validationTask.cancel();
        }

        validationTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    performValidation();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during hologram validation", e);
                }
            }
        }.runTaskTimer(plugin, VALIDATION_INTERVAL, VALIDATION_INTERVAL);
    }

    /**
     * Enhanced validation with comprehensive health checks and type verification
     */
    private void performValidation() {
        long startTime = System.currentTimeMillis();
        lastValidationTime.set(startTime);

        plugin.getLogger().info("Starting hologram validation...");

        Map<String, Vendor> vendors = vendorManager.getVendors();
        int fixed = 0;
        int checked = 0;

        for (Vendor vendor : vendors.values()) {
            String vendorId = vendor.getVendorId();
            checked++;

            try {
                // Check if vendor has unknown type
                if ("unknown".equals(vendor.getVendorType())) {
                    plugin.getLogger().info("Found vendor " + vendorId + " with unknown type during validation");
                    fixVendorType(vendor);
                    refreshSingleHologram(vendor);
                    fixed++;
                    continue;
                }

                // Check if hologram exists when it should
                if (!activeHolograms.containsKey(vendorId)) {
                    // Hologram missing, try to create it
                    if (createHologram(vendor)) {
                        fixed++;
                        plugin.getLogger().info("Fixed missing hologram for vendor " + vendorId);
                    }
                } else {
                    // Hologram exists, check if it needs updating
                    Long lastUpdate = activeHolograms.get(vendorId);
                    if (System.currentTimeMillis() - lastUpdate > HOLOGRAM_UPDATE_THRESHOLD) {
                        // Check if NPC is spawned and location is correct
                        try {
                            NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
                            if (npc != null && npc.isSpawned()) {
                                Location currentLocation = calculateHologramLocation(vendor);
                                Location lastLocation = lastKnownLocations.get(vendorId);

                                if (currentLocation != null &&
                                        (lastLocation == null || !VendorUtils.locationsEqual(currentLocation, lastLocation, LOCATION_CHANGE_THRESHOLD))) {

                                    if (updateVendorHologram(vendor)) {
                                        fixed++;
                                        plugin.getLogger().info("Updated hologram location for vendor " + vendorId);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Error checking NPC for vendor " + vendorId, e);
                        }
                    }

                    // Check if type has changed
                    String currentType = vendor.getVendorType();
                    String lastKnownType = lastKnownTypes.get(vendorId);
                    if (lastKnownType != null && !lastKnownType.equals(currentType)) {
                        if (updateVendorHologram(vendor)) {
                            fixed++;
                            plugin.getLogger().info("Updated hologram for vendor " + vendorId + " due to type change");
                        }
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error validating hologram for vendor " + vendorId, e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        plugin.getLogger().info("Hologram validation complete: checked " + checked +
                " vendors, fixed " + fixed + " issues in " + duration + "ms");
    }

    /**
     * Start maintenance task for cleanup and optimization
     */
    private void startMaintenanceTask() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }

        maintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    performMaintenance();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during hologram maintenance", e);
                }
            }
        }.runTaskTimer(plugin, MAINTENANCE_INTERVAL, MAINTENANCE_INTERVAL);
    }

    /**
     * Enhanced maintenance with cleanup and optimization
     */
    private void performMaintenance() {
        try {
            // Clean up tracking for vendors that no longer exist
            Set<String> currentVendors = vendorManager.getVendors().keySet();

            // Remove holograms for vendors that no longer exist
            activeHolograms.entrySet().removeIf(entry -> {
                if (!currentVendors.contains(entry.getKey())) {
                    try {
                        HologramManager.removeHologram(entry.getKey());
                        plugin.getLogger().info("Cleaned up hologram for removed vendor: " + entry.getKey());
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error cleaning up hologram for vendor " + entry.getKey());
                    }
                    return true;
                }
                return false;
            });

            // Clean up other tracking maps
            lastKnownLocations.entrySet().removeIf(entry ->
                    !currentVendors.contains(entry.getKey()));

            lastKnownTypes.entrySet().removeIf(entry ->
                    !currentVendors.contains(entry.getKey()));

            // Reset retry attempts for vendors that haven't failed recently
            long currentTime = System.currentTimeMillis();
            retryAttempts.entrySet().removeIf(entry -> {
                return currentVendors.contains(entry.getKey());
            });

            // Clean up failed holograms list periodically
            if (currentTime % (MAINTENANCE_INTERVAL * 4) == 0) { // Every 20 minutes
                failedHolograms.removeIf(vendorId -> {
                    if (currentVendors.contains(vendorId)) {
                        // Give failed vendors another chance
                        retryAttempts.remove(vendorId);
                        plugin.getLogger().info("Giving vendor " + vendorId + " another chance after maintenance");
                        return true;
                    }
                    return false;
                });
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during hologram maintenance", e);
        }
    }

    /**
     * Enhanced error handling with retry management
     */
    private void handleHologramError(String vendorId, String operation, Exception e) {
        // Track the error
        failedCreationAttempts.incrementAndGet();

        // Increment retry attempts
        int attempts = retryAttempts.getOrDefault(vendorId, 0) + 1;
        retryAttempts.put(vendorId, attempts);

        // Log the error
        String message = String.format("Hologram %s failed for vendor %s (attempt %d/%d): %s",
                operation, vendorId, attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
        plugin.getLogger().log(Level.WARNING, message);

        // Add to failed set if too many attempts
        if (attempts >= MAX_RETRY_ATTEMPTS) {
            failedHolograms.add(vendorId);
            plugin.getLogger().warning("Vendor " + vendorId + " marked as failed after " + attempts + " attempts");
        }
    }

    /**
     * Force refresh all holograms (admin command)
     */
    public void refreshAllHolograms() {
        plugin.getLogger().info("Force refreshing all vendor holograms...");

        // Remove all existing holograms first
        for (String vendorId : new HashSet<>(activeHolograms.keySet())) {
            removeHologram(vendorId);
        }

        // Clear state
        activeHolograms.clear();
        lastKnownLocations.clear();
        lastKnownTypes.clear();
        failedHolograms.clear();
        retryAttempts.clear();

        // Recreate holograms with delay
        new BukkitRunnable() {
            @Override
            public void run() {
                performInitialRefresh();
            }
        }.runTaskLater(plugin, 20L); // 1 second delay
    }

    /**
     * Retry failed holograms (admin command)
     */
    public int retryFailedHolograms() {
        Set<String> failedCopy = new HashSet<>(failedHolograms);
        int retryCount = 0;

        for (String vendorId : failedCopy) {
            Vendor vendor = vendorManager.getVendor(vendorId);
            if (vendor != null) {
                // Reset failure state
                failedHolograms.remove(vendorId);
                retryAttempts.remove(vendorId);

                // Try to create hologram
                if (createHologram(vendor)) {
                    retryCount++;
                    plugin.getLogger().info("Successfully retried hologram for vendor " + vendorId);
                }
            }
        }

        return retryCount;
    }

    /**
     * Enhanced statistics with detailed metrics
     */
    public Map<String, Object> getHologramStats() {
        Map<String, Object> stats = new HashMap<>();

        Map<String, Vendor> vendors = vendorManager.getVendors();

        stats.put("totalVendors", vendors.size());
        stats.put("activeHolograms", activeHolograms.size());
        stats.put("failedHolograms", failedHolograms.size());
        stats.put("totalCreated", totalHologramsCreated.get());
        stats.put("totalRemoved", totalHologramsRemoved.get());
        stats.put("failedAttempts", failedCreationAttempts.get());
        stats.put("lastValidation", lastValidationTime.get());

        // Calculate success rate
        int totalAttempts = totalHologramsCreated.get() + failedCreationAttempts.get();
        if (totalAttempts > 0) {
            double successRate = (double) totalHologramsCreated.get() / totalAttempts * 100.0;
            stats.put("successRate", Math.round(successRate * 100.0) / 100.0);
        } else {
            stats.put("successRate", 100.0);
        }

        // Vendor type distribution
        Map<String, Integer> typeDistribution = new HashMap<>();
        for (Vendor vendor : vendors.values()) {
            String type = vendor.getVendorType();
            typeDistribution.put(type, typeDistribution.getOrDefault(type, 0) + 1);
        }
        stats.put("vendorTypeDistribution", typeDistribution);

        return stats;
    }

    /**
     * Get failed holograms for debugging
     */
    public Set<String> getFailedHolograms() {
        return new HashSet<>(failedHolograms);
    }

    /**
     * Check if vendor has players nearby (optimization helper)
     */
    private boolean hasPlayersNearby(Location loc, double distance) {
        if (loc == null || loc.getWorld() == null) return false;

        return VendorUtils.getPlayersInRange(loc, distance).size() > 0;
    }

    /**
     * Enhanced shutdown cleanup with thorough hologram removal
     */
    public void shutdown() {
        try {
            // Cancel tasks
            if (validationTask != null) {
                validationTask.cancel();
                validationTask = null;
            }

            if (maintenanceTask != null) {
                maintenanceTask.cancel();
                maintenanceTask = null;
            }

            // Remove all holograms with individual error handling
            plugin.getLogger().info("Cleaning up " + activeHolograms.size() + " active holograms...");

            for (String vendorId : new HashSet<>(activeHolograms.keySet())) {
                try {
                    HologramManager.removeHologram(vendorId);
                    plugin.getLogger().info("Removed hologram for vendor: " + vendorId);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error removing hologram for vendor " + vendorId + ": " + e.getMessage());
                }
            }

            // Additional cleanup for any remaining holograms
            try {
                HologramManager.cleanup();
            } catch (Exception e) {
                plugin.getLogger().warning("Error during final hologram cleanup: " + e.getMessage());
            }

            // Clear tracking data
            activeHolograms.clear();
            lastKnownLocations.clear();
            lastKnownTypes.clear();
            failedHolograms.clear();
            retryAttempts.clear();

            plugin.getLogger().info("VendorHologramManager shutdown complete");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during hologram manager shutdown", e);
        }
    }
}