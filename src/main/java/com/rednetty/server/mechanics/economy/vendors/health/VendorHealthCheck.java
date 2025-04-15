package com.rednetty.server.mechanics.economy.vendors.health;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.Vendor;
import com.rednetty.server.mechanics.economy.vendors.VendorHologramManager;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import com.rednetty.server.mechanics.economy.vendors.config.VendorConfiguration;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * Advanced health check system for the vendor system
 */
public class VendorHealthCheck {

    private final YakRealms plugin;
    private final VendorManager vendorManager;
    private final VendorHologramManager hologramManager;
    private final VendorConfiguration config;

    // Statistics tracking
    private int totalChecks = 0;
    private int totalIssuesFixed = 0;
    private final List<String> recentIssues = new ArrayList<>();

    /**
     * Constructor
     */
    public VendorHealthCheck(YakRealms plugin, VendorManager vendorManager,
                             VendorHologramManager hologramManager) {
        this.plugin = plugin;
        this.vendorManager = vendorManager;
        this.hologramManager = hologramManager;
        this.config = VendorConfiguration.getInstance(plugin);

        if (config.getBoolean("periodic-validation")) {
            startPeriodicChecks();
        }

        if (config.getBoolean("auto-backup")) {
            startAutoBackups();
        }
    }

    /**
     * Start periodic health checks
     */
    private void startPeriodicChecks() {
        int intervalMinutes = config.getInt("validation-interval-minutes");
        if (intervalMinutes < 5) intervalMinutes = 5; // Minimum 5 minutes

        new BukkitRunnable() {
            @Override
            public void run() {
                runHealthCheck(false); // Don't force fixes
            }
        }.runTaskTimer(plugin, 20 * 60 * 5, 20 * 60 * intervalMinutes); // First check after 5 min

        plugin.getLogger().info("Vendor health checks scheduled every " + intervalMinutes + " minutes");
    }

    /**
     * Start automatic backup schedule
     */
    private void startAutoBackups() {
        int intervalHours = config.getInt("backup-interval-hours");
        if (intervalHours < 1) intervalHours = 1; // Minimum 1 hour

        new BukkitRunnable() {
            @Override
            public void run() {
                createBackup();
                cleanupOldBackups();
            }
        }.runTaskTimer(plugin, 20 * 60 * 30, 20 * 60 * 60 * intervalHours); // First backup after 30 min

        plugin.getLogger().info("Vendor backups scheduled every " + intervalHours + " hours");
    }

    /**
     * Run a health check
     */
    public synchronized int runHealthCheck(boolean forceFixes) {
        totalChecks++;
        int issuesFixed = 0;
        recentIssues.clear();

        try {
            plugin.getLogger().info("Running vendor system health check (#" + totalChecks + ")...");

            // 1. Validate vendor behaviors
            int behaviorsFixed = vendorManager.validateAndFixVendors();
            if (behaviorsFixed > 0) {
                recentIssues.add("Fixed " + behaviorsFixed + " vendor behavior issues");
                issuesFixed += behaviorsFixed;
            }

            // 2. Check NPC existence and spawning
            for (Vendor vendor : vendorManager.getVendors().values()) {
                NPC npc = null;

                try {
                    npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
                } catch (Exception e) {
                    recentIssues.add("Failed to get NPC for vendor " + vendor.getVendorId() + ": " + e.getMessage());
                    continue;
                }

                if (npc == null) {
                    recentIssues.add("NPC not found for vendor " + vendor.getVendorId());
                    if (forceFixes) {
                        // Try to recreate NPC
                        try {
                            NPC newNpc = CitizensAPI.getNPCRegistry().createNPC(
                                    org.bukkit.entity.EntityType.PLAYER,
                                    "Vendor_" + vendor.getVendorId()
                            );
                            newNpc.spawn(vendor.getLocation());

                            // Store the original behavior class
                            String originalBehaviorClass = vendor.getBehaviorClass();

                            // Create a new vendor object with the new NPC ID but same behavior class
                            Vendor updatedVendor = new Vendor(
                                    vendor.getVendorId(),
                                    newNpc.getId(),
                                    vendor.getLocation(),
                                    vendor.getHologramLines(),
                                    originalBehaviorClass
                            );

                            // Replace the old vendor with the updated one
                            vendorManager.registerVendor(updatedVendor);
                            vendorManager.saveVendorsToConfig();

                            recentIssues.add("Recreated NPC for vendor " + vendor.getVendorId() +
                                    ", assigned new NPC ID: " + newNpc.getId());
                            issuesFixed++;
                        } catch (Exception e) {
                            recentIssues.add("Failed to recreate NPC for vendor " + vendor.getVendorId() +
                                    ": " + e.getMessage());
                        }
                    }
                    continue;
                }

                if (!npc.isSpawned() && vendor.getLocation() != null) {
                    try {
                        npc.spawn(vendor.getLocation());
                        recentIssues.add("Respawned NPC for vendor " + vendor.getVendorId());
                        issuesFixed++;
                    } catch (Exception e) {
                        recentIssues.add("Failed to spawn NPC for vendor " + vendor.getVendorId() + ": " + e.getMessage());
                    }
                }
            }

            // 3. Check and fix holograms
            for (Vendor vendor : vendorManager.getVendors().values()) {
                if (hologramManager.updateVendorHologram(vendor)) {
                    recentIssues.add("Refreshed hologram for vendor " + vendor.getVendorId());
                    issuesFixed++;
                }
            }

            // Log results
            totalIssuesFixed += issuesFixed;
            if (issuesFixed == 0) {
                plugin.getLogger().info("Vendor health check completed. No issues found.");
            } else {
                plugin.getLogger().info("Vendor health check completed. Found and fixed " + issuesFixed + " issues:");
                for (String issue : recentIssues) {
                    plugin.getLogger().info("- " + issue);
                }

                // Save changes
                vendorManager.saveVendorsToConfig();
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during vendor health check", e);
        }

        return issuesFixed;
    }

    /**
     * Create a backup of the vendor configuration
     */
    public boolean createBackup() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            File vendorConfig = new File(plugin.getDataFolder(), "vendors.yml");
            File backupFile = new File(backupDir, "vendors_" + timestamp + ".yml");

            // Copy the file
            Files.copy(vendorConfig.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("Created vendor configuration backup: " + backupFile.getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create vendor configuration backup", e);
            return false;
        }
    }

    /**
     * Clean up old backups exceeding the maximum limit
     */
    private void cleanupOldBackups() {
        try {
            int maxBackups = config.getInt("max-backups");
            if (maxBackups <= 0) return;

            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) return;

            File[] backups = backupDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("vendors_") && name.endsWith(".yml");
                }
            });

            if (backups == null || backups.length <= maxBackups) return;

            // Sort files by lastModified (oldest first)
            Arrays.sort(backups, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

            // Delete oldest files exceeding the limit
            int toDelete = backups.length - maxBackups;
            for (int i = 0; i < toDelete; i++) {
                if (backups[i].delete()) {
                    plugin.getLogger().info("Deleted old vendor backup: " + backups[i].getName());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error cleaning up old vendor backups", e);
        }
    }

    /**
     * Get health check statistics
     */
    public String getStatistics() {
        return "Vendor Health Check Stats:\n" +
                "- Total checks run: " + totalChecks + "\n" +
                "- Total issues fixed: " + totalIssuesFixed + "\n" +
                "- Last check issues: " + recentIssues.size();
    }

    /**
     * Get recent issues
     */
    public List<String> getRecentIssues() {
        return new ArrayList<>(recentIssues);
    }
}