package com.rednetty.server.mechanics.economy.vendors.health;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.Vendor;
import com.rednetty.server.mechanics.economy.vendors.VendorHologramManager;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import com.rednetty.server.mechanics.economy.vendors.config.VendorConfiguration;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Enhanced health check system for the vendor system with comprehensive monitoring,
 * automated recovery, intelligent diagnostics, and advanced backup management.
 * Provides proactive health monitoring with detailed reporting and recovery mechanisms.
 */
public class VendorHealthCheck {

    private final YakRealms plugin;
    private final VendorManager vendorManager;
    private final VendorHologramManager hologramManager;
    private final VendorConfiguration config;

    // Enhanced health tracking
    private final Map<String, VendorHealthStatus> vendorHealthMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCheckTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final List<HealthCheckReport> recentReports = Collections.synchronizedList(new ArrayList<>());

    // Performance metrics
    private final AtomicInteger totalChecks = new AtomicInteger(0);
    private final AtomicInteger totalIssuesFound = new AtomicInteger(0);
    private final AtomicInteger totalIssuesFixed = new AtomicInteger(0);
    private final AtomicLong lastFullCheckTime = new AtomicLong(0);
    private final AtomicLong totalCheckTime = new AtomicLong(0);

    // Task management
    private BukkitTask periodicHealthTask;
    private BukkitTask backupTask;
    private BukkitTask monitoringTask;

    // Configuration constants
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final long VENDOR_TIMEOUT_MS = 300000; // 5 minutes
    private static final int MAX_RECENT_REPORTS = 50;
    private static final long CRITICAL_ISSUE_THRESHOLD = 10;

    /**
     * Enhanced constructor with comprehensive initialization
     */
    public VendorHealthCheck(YakRealms plugin, VendorManager vendorManager, VendorHologramManager hologramManager) {
        this.plugin = plugin;
        this.vendorManager = vendorManager;
        this.hologramManager = hologramManager;
        this.config = VendorConfiguration.getInstance(plugin);

        // Initialize health tracking for existing vendors
        initializeHealthTracking();

        // Start health monitoring tasks
        if (config.getBoolean("periodic-validation")) {
            startPeriodicHealthChecks();
        }

        if (config.getBoolean("auto-backup")) {
            startAutoBackups();
        }

        // Start continuous monitoring
        startContinuousMonitoring();

        plugin.getLogger().info("Enhanced VendorHealthCheck initialized with comprehensive monitoring");
    }

    /**
     * Initialize health tracking for existing vendors
     */
    private void initializeHealthTracking() {
        try {
            Map<String, Vendor> vendors = vendorManager.getVendors();
            for (Vendor vendor : vendors.values()) {
                VendorHealthStatus status = new VendorHealthStatus(vendor.getVendorId());
                vendorHealthMap.put(vendor.getVendorId(), status);
            }
            plugin.getLogger().info("Initialized health tracking for " + vendors.size() + " vendors");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error initializing health tracking", e);
        }
    }

    /**
     * Enhanced periodic health checks with intelligent scheduling
     */
    private void startPeriodicHealthChecks() {
        int intervalMinutes = Math.max(5, config.getInt("validation-interval-minutes"));
        long intervalTicks = intervalMinutes * 60 * 20L;

        periodicHealthTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    runComprehensiveHealthCheck();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error during periodic health check", e);
                }
            }
        }.runTaskTimer(plugin, intervalTicks / 2, intervalTicks); // First check after half interval

        plugin.getLogger().info("Scheduled periodic health checks every " + intervalMinutes + " minutes");
    }

    /**
     * Enhanced auto backup with intelligent scheduling and retention
     */
    private void startAutoBackups() {
        int intervalHours = Math.max(1, config.getInt("backup-interval-hours"));
        long intervalTicks = intervalHours * 60 * 60 * 20L;

        backupTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    createBackup();
                    cleanupOldBackups();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during auto backup", e);
                }
            }
        }.runTaskTimer(plugin, 1200L, intervalTicks); // First backup after 1 minute

        plugin.getLogger().info("Scheduled automatic backups every " + intervalHours + " hours");
    }

    /**
     * Start continuous monitoring for real-time health tracking
     */
    private void startContinuousMonitoring() {
        monitoringTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    performContinuousMonitoring();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during continuous monitoring", e);
                }
            }
        }.runTaskTimer(plugin, 200L, 200L); // Every 10 seconds
    }

    /**
     * Enhanced comprehensive health check with detailed analysis
     */
    public synchronized int runComprehensiveHealthCheck() {
        long startTime = System.currentTimeMillis();
        totalChecks.incrementAndGet();
        lastFullCheckTime.set(startTime);

        HealthCheckReport report = new HealthCheckReport(startTime);

        try {
            plugin.getLogger().info("Starting comprehensive vendor health check...");

            Map<String, Vendor> vendors = vendorManager.getVendors();
            report.totalVendorsChecked = vendors.size();

            for (Vendor vendor : vendors.values()) {
                VendorHealthIssue issue = checkSingleVendor(vendor);
                if (issue != null) {
                    report.issuesFound.add(issue);
                    totalIssuesFound.incrementAndGet();

                    // Attempt to fix the issue
                    if (attemptAutoFix(vendor, issue)) {
                        report.issuesFixed.add(issue);
                        totalIssuesFixed.incrementAndGet();
                    } else {
                        report.unfixedIssues.add(issue);
                    }
                }
            }

            // Additional system-wide checks
            performSystemWideChecks(report);

            // Generate health summary
            generateHealthSummary(report);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during comprehensive health check", e);
            report.criticalError = e.getMessage();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            report.duration = duration;
            totalCheckTime.addAndGet(duration);

            addReport(report);
            logHealthCheckResults(report);
        }

        return report.issuesFixed.size();
    }

    /**
     * Enhanced single vendor checking with comprehensive validation
     */
    private VendorHealthIssue checkSingleVendor(Vendor vendor) {
        String vendorId = vendor.getVendorId();
        VendorHealthStatus status = vendorHealthMap.computeIfAbsent(vendorId, k -> new VendorHealthStatus(vendorId));

        try {
            lastCheckTimes.put(vendorId, System.currentTimeMillis());

            // Basic vendor validation
            if (!vendor.isValid()) {
                String error = vendor.getLastValidationError();
                return new VendorHealthIssue(vendorId, IssueType.INVALID_VENDOR_DATA,
                        "Vendor validation failed: " + error, IssueSeverity.HIGH);
            }

            // NPC existence and state check
            VendorHealthIssue npcIssue = checkNPCHealth(vendor);
            if (npcIssue != null) return npcIssue;

            // Location and world validation
            VendorHealthIssue locationIssue = checkLocationHealth(vendor);
            if (locationIssue != null) return locationIssue;

            // Behavior class validation
            VendorHealthIssue behaviorIssue = checkBehaviorHealth(vendor);
            if (behaviorIssue != null) return behaviorIssue;

            // Hologram validation
            VendorHealthIssue hologramIssue = checkHologramHealth(vendor);
            if (hologramIssue != null) return hologramIssue;

            // Performance check
            VendorHealthIssue performanceIssue = checkPerformanceHealth(vendor);
            if (performanceIssue != null) return performanceIssue;

            // Update health status
            status.lastSuccessfulCheck = System.currentTimeMillis();
            status.isHealthy = true;
            consecutiveFailures.remove(vendorId);

            return null; // No issues found

        } catch (Exception e) {
            int failures = consecutiveFailures.getOrDefault(vendorId, 0) + 1;
            consecutiveFailures.put(vendorId, failures);
            status.isHealthy = false;
            status.lastError = e.getMessage();

            return new VendorHealthIssue(vendorId, IssueType.CHECK_ERROR,
                    "Error checking vendor: " + e.getMessage(), IssueSeverity.MEDIUM);
        }
    }

    /**
     * Check NPC health and state
     */
    private VendorHealthIssue checkNPCHealth(Vendor vendor) {
        try {
            NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());

            if (npc == null) {
                return new VendorHealthIssue(vendor.getVendorId(), IssueType.MISSING_NPC,
                        "NPC with ID " + vendor.getNpcId() + " not found", IssueSeverity.HIGH);
            }

            if (!npc.isSpawned()) {
                Location vendorLoc = vendor.getLocation();
                if (vendorLoc != null && VendorUtils.isChunkLoaded(vendorLoc)) {
                    return new VendorHealthIssue(vendor.getVendorId(), IssueType.UNSPAWNED_NPC,
                            "NPC is not spawned but chunk is loaded", IssueSeverity.MEDIUM);
                }
            }

            return null;
        } catch (Exception e) {
            return new VendorHealthIssue(vendor.getVendorId(), IssueType.NPC_ERROR,
                    "Error checking NPC: " + e.getMessage(), IssueSeverity.MEDIUM);
        }
    }

    /**
     * Check location and world validity
     */
    private VendorHealthIssue checkLocationHealth(Vendor vendor) {
        Location location = vendor.getLocation();

        if (location == null) {
            return new VendorHealthIssue(vendor.getVendorId(), IssueType.INVALID_LOCATION,
                    "Vendor location is null", IssueSeverity.HIGH);
        }

        World world = location.getWorld();
        if (world == null) {
            return new VendorHealthIssue(vendor.getVendorId(), IssueType.INVALID_WORLD,
                    "Vendor world is null", IssueSeverity.HIGH);
        }

        if (!VendorUtils.isValidVendorLocation(location)) {
            return new VendorHealthIssue(vendor.getVendorId(), IssueType.INVALID_COORDINATES,
                    "Invalid vendor coordinates: " + location, IssueSeverity.MEDIUM);
        }

        return null;
    }

    /**
     * Check behavior class health
     */
    private VendorHealthIssue checkBehaviorHealth(Vendor vendor) {
        if (!vendor.hasValidBehavior()) {
            return new VendorHealthIssue(vendor.getVendorId(), IssueType.INVALID_BEHAVIOR,
                    "Invalid behavior class: " + vendor.getBehaviorClass(), IssueSeverity.MEDIUM);
        }
        return null;
    }

    /**
     * Check hologram health
     */
    private VendorHealthIssue checkHologramHealth(Vendor vendor) {
        try {
            Map<String, Object> hologramStats = hologramManager.getHologramStats();
            Set<String> failedHolograms = hologramManager.getFailedHolograms();

            if (failedHolograms.contains(vendor.getVendorId())) {
                return new VendorHealthIssue(vendor.getVendorId(), IssueType.FAILED_HOLOGRAM,
                        "Hologram creation has failed multiple times", IssueSeverity.MEDIUM);
            }

            return null;
        } catch (Exception e) {
            return new VendorHealthIssue(vendor.getVendorId(), IssueType.HOLOGRAM_ERROR,
                    "Error checking hologram: " + e.getMessage(), IssueSeverity.LOW);
        }
    }

    /**
     * Check vendor performance metrics
     */
    private VendorHealthIssue checkPerformanceHealth(Vendor vendor) {
        try {
            Vendor.VendorMetrics metrics = vendor.getMetrics();

            // Check if vendor hasn't been accessed in a long time
            if (metrics.getTimeSinceLastAccess() > VENDOR_TIMEOUT_MS) {
                return new VendorHealthIssue(vendor.getVendorId(), IssueType.INACTIVE_VENDOR,
                        "Vendor hasn't been accessed in " + VendorUtils.formatDuration(metrics.getTimeSinceLastAccess()),
                        IssueSeverity.LOW);
            }

            return null;
        } catch (Exception e) {
            return new VendorHealthIssue(vendor.getVendorId(), IssueType.METRICS_ERROR,
                    "Error checking performance metrics: " + e.getMessage(), IssueSeverity.LOW);
        }
    }

    /**
     * Attempt automatic fixing of issues
     */
    private boolean attemptAutoFix(Vendor vendor, VendorHealthIssue issue) {
        try {
            switch (issue.type) {
                case MISSING_NPC:
                    return fixMissingNPC(vendor);

                case UNSPAWNED_NPC:
                    return fixUnspawnedNPC(vendor);

                case INVALID_BEHAVIOR:
                    return fixInvalidBehavior(vendor);

                case FAILED_HOLOGRAM:
                    return fixFailedHologram(vendor);

                case INVALID_LOCATION:
                    return fixInvalidLocation(vendor);

                default:
                    return false; // Cannot auto-fix this type
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error attempting auto-fix for vendor " + vendor.getVendorId(), e);
            return false;
        }
    }

    /**
     * Fix missing NPC
     */
    private boolean fixMissingNPC(Vendor vendor) {
        try {
            // Try to recreate the NPC
            NPC newNpc = CitizensAPI.getNPCRegistry().createNPC(
                    org.bukkit.entity.EntityType.PLAYER,
                    "Vendor_" + vendor.getVendorId()
            );

            Location location = vendor.getLocation();
            if (location != null) {
                newNpc.spawn(location);

                // Update vendor with new NPC ID (this would require VendorManager support)
                plugin.getLogger().info("Created new NPC for vendor " + vendor.getVendorId() +
                        " with ID " + newNpc.getId());
                return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fix missing NPC for vendor " + vendor.getVendorId(), e);
        }
        return false;
    }

    /**
     * Fix unspawned NPC
     */
    private boolean fixUnspawnedNPC(Vendor vendor) {
        try {
            NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
            if (npc != null && !npc.isSpawned()) {
                Location location = vendor.getLocation();
                if (location != null && VendorUtils.isChunkLoaded(location)) {
                    npc.spawn(location);
                    plugin.getLogger().info("Respawned NPC for vendor " + vendor.getVendorId());
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fix unspawned NPC for vendor " + vendor.getVendorId(), e);
        }
        return false;
    }

    /**
     * Fix invalid behavior
     */
    private boolean fixInvalidBehavior(Vendor vendor) {
        try {
            String vendorType = vendor.getVendorType();
            String fallbackBehavior = vendorManager.getDefaultBehaviorForType(vendorType);

            vendor.setBehaviorClass(fallbackBehavior);
            vendorManager.saveVendorsToConfig();

            plugin.getLogger().info("Fixed invalid behavior for vendor " + vendor.getVendorId() +
                    " - set to " + fallbackBehavior);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fix invalid behavior for vendor " + vendor.getVendorId(), e);
        }
        return false;
    }

    /**
     * Fix failed hologram
     */
    private boolean fixFailedHologram(Vendor vendor) {
        try {
            boolean success = hologramManager.updateVendorHologram(vendor);
            if (success) {
                plugin.getLogger().info("Fixed failed hologram for vendor " + vendor.getVendorId());
            }
            return success;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fix hologram for vendor " + vendor.getVendorId(), e);
        }
        return false;
    }

    /**
     * Fix invalid location
     */
    private boolean fixInvalidLocation(Vendor vendor) {
        try {
            // Try to get location from NPC
            NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
            if (npc != null && npc.isSpawned()) {
                Location npcLocation = npc.getEntity().getLocation();
                if (VendorUtils.isValidVendorLocation(npcLocation)) {
                    vendor.setLocation(npcLocation);
                    vendorManager.saveVendorsToConfig();
                    plugin.getLogger().info("Fixed invalid location for vendor " + vendor.getVendorId());
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fix invalid location for vendor " + vendor.getVendorId(), e);
        }
        return false;
    }

    /**
     * Perform system-wide health checks
     */
    private void performSystemWideChecks(HealthCheckReport report) {
        try {
            // Check overall system health
            Map<String, Object> vendorStats = vendorManager.getMetrics();
            Map<String, Object> hologramStats = hologramManager.getHologramStats();

            // Check for too many failures
            long totalFailures = consecutiveFailures.values().stream().mapToInt(Integer::intValue).sum();
            if (totalFailures > CRITICAL_ISSUE_THRESHOLD) {
                VendorHealthIssue systemIssue = new VendorHealthIssue("system", IssueType.SYSTEM_DEGRADED,
                        "High number of vendor failures: " + totalFailures, IssueSeverity.HIGH);
                report.systemIssues.add(systemIssue);
            }

            // Check hologram success rate
            if (hologramStats.containsKey("successRate")) {
                double successRate = (Double) hologramStats.get("successRate");
                if (successRate < 80.0) {
                    VendorHealthIssue hologramIssue = new VendorHealthIssue("system", IssueType.HOLOGRAM_SYSTEM_ISSUES,
                            "Low hologram success rate: " + successRate + "%", IssueSeverity.MEDIUM);
                    report.systemIssues.add(hologramIssue);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during system-wide health checks", e);
        }
    }

    /**
     * Continuous monitoring for real-time issues
     */
    private void performContinuousMonitoring() {
        try {
            // Check for vendors that haven't been checked recently
            long currentTime = System.currentTimeMillis();

            for (Map.Entry<String, Long> entry : lastCheckTimes.entrySet()) {
                String vendorId = entry.getKey();
                long lastCheck = entry.getValue();

                if (currentTime - lastCheck > VENDOR_TIMEOUT_MS) {
                    Vendor vendor = vendorManager.getVendor(vendorId);
                    if (vendor != null) {
                        // Quick health check
                        VendorHealthIssue issue = checkSingleVendor(vendor);
                        if (issue != null && issue.severity == IssueSeverity.HIGH) {
                            plugin.getLogger().warning("Critical issue detected for vendor " + vendorId + ": " + issue.description);
                            attemptAutoFix(vendor, issue);
                        }
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during continuous monitoring", e);
        }
    }

    /**
     * Enhanced backup creation with metadata
     */
    public boolean createBackup() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            // Create main backup
            File vendorConfig = new File(plugin.getDataFolder(), "vendors.yml");
            File backupFile = new File(backupDir, "vendors_" + timestamp + ".yml");

            if (vendorConfig.exists()) {
                Files.copy(vendorConfig.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Create metadata file
                createBackupMetadata(backupDir, timestamp);

                plugin.getLogger().info("Created vendor configuration backup: " + backupFile.getName());
                return true;
            } else {
                plugin.getLogger().warning("Vendor config file not found for backup");
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create vendor configuration backup", e);
            return false;
        }
    }

    /**
     * Create backup metadata for better management
     */
    private void createBackupMetadata(File backupDir, String timestamp) throws IOException {
        File metadataFile = new File(backupDir, "backup_" + timestamp + ".meta");

        Properties metadata = new Properties();
        metadata.setProperty("timestamp", timestamp);
        metadata.setProperty("vendorCount", String.valueOf(vendorManager.getVendors().size()));
        metadata.setProperty("serverVersion", Bukkit.getVersion());
        metadata.setProperty("pluginVersion", plugin.getDescription().getVersion());

        Map<String, Object> healthStats = getHealthStatistics();
        metadata.setProperty("healthScore", healthStats.get("overallHealthScore").toString());

        try (var output = Files.newOutputStream(metadataFile.toPath())) {
            metadata.store(output, "Vendor Backup Metadata");
        }
    }

    /**
     * Enhanced cleanup with metadata awareness
     */
    private void cleanupOldBackups() {
        try {
            int maxBackups = Math.max(1, config.getInt("max-backups"));
            File backupDir = new File(plugin.getDataFolder(), "backups");

            if (!backupDir.exists()) return;

            // Get backup files
            File[] backups = backupDir.listFiles((dir, name) ->
                    name.startsWith("vendors_") && name.endsWith(".yml"));

            if (backups == null || backups.length <= maxBackups) return;

            // Sort by last modified (oldest first)
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));

            // Delete oldest files exceeding the limit
            int toDelete = backups.length - maxBackups;
            for (int i = 0; i < toDelete; i++) {
                File backup = backups[i];
                String timestamp = extractTimestampFromBackup(backup.getName());

                // Delete backup file
                if (backup.delete()) {
                    plugin.getLogger().info("Deleted old vendor backup: " + backup.getName());
                }

                // Delete associated metadata file
                File metadataFile = new File(backupDir, "backup_" + timestamp + ".meta");
                if (metadataFile.exists()) {
                    metadataFile.delete();
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error cleaning up old vendor backups", e);
        }
    }

    /**
     * Extract timestamp from backup filename
     */
    private String extractTimestampFromBackup(String filename) {
        // Extract from "vendors_TIMESTAMP.yml"
        if (filename.startsWith("vendors_") && filename.endsWith(".yml")) {
            return filename.substring(8, filename.length() - 4);
        }
        return "";
    }

    /**
     * Generate comprehensive health summary
     */
    private void generateHealthSummary(HealthCheckReport report) {
        int healthyVendors = 0;
        int unhealthyVendors = 0;

        for (VendorHealthStatus status : vendorHealthMap.values()) {
            if (status.isHealthy) {
                healthyVendors++;
            } else {
                unhealthyVendors++;
            }
        }

        report.healthyVendors = healthyVendors;
        report.unhealthyVendors = unhealthyVendors;

        // Calculate overall health score
        double healthScore = report.totalVendorsChecked > 0 ?
                (double) healthyVendors / report.totalVendorsChecked * 100.0 : 100.0;
        report.overallHealthScore = healthScore;
    }

    /**
     * Add report to recent reports list
     */
    private void addReport(HealthCheckReport report) {
        synchronized (recentReports) {
            recentReports.add(report);

            // Keep only recent reports
            while (recentReports.size() > MAX_RECENT_REPORTS) {
                recentReports.remove(0);
            }
        }
    }

    /**
     * Log health check results with appropriate detail level
     */
    private void logHealthCheckResults(HealthCheckReport report) {
        if (report.issuesFound.isEmpty() && report.systemIssues.isEmpty()) {
            plugin.getLogger().info("Health check completed - All vendors healthy (" +
                    report.totalVendorsChecked + " checked in " + report.duration + "ms)");
        } else {
            plugin.getLogger().warning("Health check found " + report.issuesFound.size() +
                    " vendor issues and " + report.systemIssues.size() + " system issues");
            plugin.getLogger().info("Fixed " + report.issuesFixed.size() + " issues, " +
                    report.unfixedIssues.size() + " remain unfixed");

            // Log critical issues
            for (VendorHealthIssue issue : report.issuesFound) {
                if (issue.severity == IssueSeverity.HIGH) {
                    plugin.getLogger().warning("Critical issue - " + issue.vendorId + ": " + issue.description);
                }
            }
        }
    }

    /**
     * Get comprehensive health statistics
     */
    public Map<String, Object> getHealthStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Basic counts
        stats.put("totalChecks", totalChecks.get());
        stats.put("totalIssuesFound", totalIssuesFound.get());
        stats.put("totalIssuesFixed", totalIssuesFixed.get());
        stats.put("lastFullCheck", lastFullCheckTime.get());
        stats.put("averageCheckTime", totalChecks.get() > 0 ?
                totalCheckTime.get() / totalChecks.get() : 0);

        // Vendor health breakdown
        int healthy = 0, unhealthy = 0;
        for (VendorHealthStatus status : vendorHealthMap.values()) {
            if (status.isHealthy) healthy++;
            else unhealthy++;
        }
        stats.put("healthyVendors", healthy);
        stats.put("unhealthyVendors", unhealthy);

        // Overall health score
        double healthScore = (healthy + unhealthy) > 0 ?
                (double) healthy / (healthy + unhealthy) * 100.0 : 100.0;
        stats.put("overallHealthScore", Math.round(healthScore * 100.0) / 100.0);

        // Recent reports summary
        stats.put("recentReportsCount", recentReports.size());

        return stats;
    }

    /**
     * Get recent health reports
     */
    public List<HealthCheckReport> getRecentReports() {
        synchronized (recentReports) {
            return new ArrayList<>(recentReports);
        }
    }

    /**
     * Get detailed vendor health status
     */
    public Map<String, VendorHealthStatus> getVendorHealthMap() {
        return new HashMap<>(vendorHealthMap);
    }

    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        try {
            if (periodicHealthTask != null) {
                periodicHealthTask.cancel();
            }
            if (backupTask != null) {
                backupTask.cancel();
            }
            if (monitoringTask != null) {
                monitoringTask.cancel();
            }

            plugin.getLogger().info("VendorHealthCheck shutdown complete");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during health check shutdown", e);
        }
    }

    // ================== DATA CLASSES ==================

    /**
     * Vendor health status tracking
     */
    public static class VendorHealthStatus {
        public final String vendorId;
        public boolean isHealthy = true;
        public long lastSuccessfulCheck = System.currentTimeMillis();
        public String lastError = null;
        public long creationTime = System.currentTimeMillis();

        public VendorHealthStatus(String vendorId) {
            this.vendorId = vendorId;
        }
    }

    /**
     * Health check report data
     */
    public static class HealthCheckReport {
        public final long timestamp;
        public long duration;
        public int totalVendorsChecked;
        public int healthyVendors;
        public int unhealthyVendors;
        public double overallHealthScore;
        public final List<VendorHealthIssue> issuesFound = new ArrayList<>();
        public final List<VendorHealthIssue> issuesFixed = new ArrayList<>();
        public final List<VendorHealthIssue> unfixedIssues = new ArrayList<>();
        public final List<VendorHealthIssue> systemIssues = new ArrayList<>();
        public String criticalError;

        public HealthCheckReport(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    /**
     * Health issue definition
     */
    public static class VendorHealthIssue {
        public final String vendorId;
        public final IssueType type;
        public final String description;
        public final IssueSeverity severity;
        public final long timestamp;

        public VendorHealthIssue(String vendorId, IssueType type, String description, IssueSeverity severity) {
            this.vendorId = vendorId;
            this.type = type;
            this.description = description;
            this.severity = severity;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Issue type enumeration
     */
    public enum IssueType {
        INVALID_VENDOR_DATA,
        MISSING_NPC,
        UNSPAWNED_NPC,
        NPC_ERROR,
        INVALID_LOCATION,
        INVALID_WORLD,
        INVALID_COORDINATES,
        INVALID_BEHAVIOR,
        FAILED_HOLOGRAM,
        HOLOGRAM_ERROR,
        INACTIVE_VENDOR,
        METRICS_ERROR,
        CHECK_ERROR,
        SYSTEM_DEGRADED,
        HOLOGRAM_SYSTEM_ISSUES
    }

    /**
     * Issue severity levels
     */
    public enum IssueSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}