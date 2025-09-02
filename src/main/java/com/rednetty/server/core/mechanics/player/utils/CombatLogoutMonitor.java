package com.rednetty.server.core.mechanics.player.utils;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Monitoring and alerting system for combat logout processing
 * Tracks statistics and alerts administrators to issues
 */
public class CombatLogoutMonitor {

    // Statistics
    private static final AtomicInteger totalCombatLogouts = new AtomicInteger(0);
    private static final AtomicInteger successfulProcessing = new AtomicInteger(0);
    private static final AtomicInteger failedProcessing = new AtomicInteger(0);
    private static final AtomicInteger totalRejoinDeaths = new AtomicInteger(0);
    private static final AtomicInteger failedRejoinDeaths = new AtomicInteger(0);
    private static final AtomicInteger rollbacksPerformed = new AtomicInteger(0);

    // Tracking
    private static final ConcurrentHashMap<UUID, ProcessingAttempt> processingAttempts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> failuresByReason = new ConcurrentHashMap<>();

    // Configuration
    private static final int ALERT_THRESHOLD = 3;
    private static final long STATS_LOG_INTERVAL = 600000; // 10 minutes
    private static final long PROCESSING_TIMEOUT = 30000; // 30 seconds

    /**
     * Initialize the monitoring system
     */
    public static void initialize() {
        // Start stats logging task
        new BukkitRunnable() {
            @Override
            public void run() {
                logDetailedStats();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(),
                STATS_LOG_INTERVAL / 50,
                STATS_LOG_INTERVAL / 50);

        // Start timeout monitoring
        new BukkitRunnable() {
            @Override
            public void run() {
                checkForTimedOutProcessing();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 200L, 200L); // Every 10 seconds

        YakRealms.log("CombatLogoutMonitor enabled successfully");
    }

    /**
     * Record the start of combat logout processing
     */
    public static void recordCombatLogoutStart(Player player, String alignment) {
        try {
            UUID playerId = player.getUniqueId();

            totalCombatLogouts.incrementAndGet();
            processingAttempts.put(playerId, new ProcessingAttempt(player.getName(), alignment));

            YakRealms.log("Combat logout processing started for: " + player.getName() + " (alignment: " + alignment + ")");

        } catch (Exception e) {
            YakRealms.error("Error recording combat logout start", e);
        }
    }

    /**
     * Record combat logout processing completion
     */
    public static void recordCombatLogoutComplete(Player player, boolean success) {
        recordCombatLogoutComplete(player, success, null);
    }

    /**
     * Record combat logout processing completion with reason
     */
    public static void recordCombatLogoutComplete(Player player, boolean success, String failureReason) {
        try {
            UUID playerId = player.getUniqueId();
            ProcessingAttempt attempt = processingAttempts.remove(playerId);

            if (attempt != null) {
                attempt.completed = true;

                long duration = attempt.getDuration();

                if (success) {
                    successfulProcessing.incrementAndGet();
                    YakRealms.log("Combat logout processing completed successfully for: " + player.getName() +
                            " (duration: " + duration + "ms)");
                } else {
                    failedProcessing.incrementAndGet();

                    if (failureReason != null) {
                        failuresByReason.put(failureReason, failuresByReason.getOrDefault(failureReason, 0) + 1);
                    }

                    YakRealms.log("Combat logout processing FAILED for: " + player.getName() +
                            " (duration: " + duration + "ms, reason: " + failureReason + ")");

                    // Alert admins if failure rate is high
                    if (failedProcessing.get() >= ALERT_THRESHOLD) {
                        alertAdmins("Combat logout system experiencing failures! " +
                                "Failed: " + failedProcessing.get() + ", Success: " + successfulProcessing.get());
                    }
                }
            } else {
                YakRealms.warn("No processing attempt found for player: " + player.getName());
            }

        } catch (Exception e) {
            YakRealms.error("Error recording combat logout completion", e);
        }
    }

    /**
     * Record rejoin death completion
     */
    public static void recordRejoinDeath(Player player, boolean success) {
        try {
            totalRejoinDeaths.incrementAndGet();

            if (success) {
                YakRealms.log("Combat logout rejoin death completed for: " + player.getName());
            } else {
                failedRejoinDeaths.incrementAndGet();
                YakRealms.log("Combat logout rejoin death FAILED for: " + player.getName());

                if (failedRejoinDeaths.get() >= ALERT_THRESHOLD) {
                    alertAdmins("Combat logout rejoin deaths failing! Check death processing system.");
                }
            }

        } catch (Exception e) {
            YakRealms.error("Error recording rejoin death", e);
        }
    }

    /**
     * Record rollback performed
     */
    public static void recordRollback(Player player, String reason) {
        try {
            rollbacksPerformed.incrementAndGet();

            YakRealms.warn("Combat logout rollback performed for: " + player.getName() + " (reason: " + reason + ")");

            // Always alert on rollbacks as they indicate serious issues
            alertAdmins("Combat logout rollback performed for " + player.getName() + ": " + reason);

        } catch (Exception e) {
            YakRealms.error("Error recording rollback", e);
        }
    }

    /**
     * Check for timed out processing
     */
    private static void checkForTimedOutProcessing() {
        try {
            processingAttempts.entrySet().removeIf(entry -> {
                ProcessingAttempt attempt = entry.getValue();

                if (attempt.isTimedOut() && !attempt.completed) {
                    YakRealms.log("Combat logout processing TIMED OUT for: " + attempt.playerName +
                            " (alignment: " + attempt.alignment + ", duration: " + attempt.getDuration() + "ms)");

                    failedProcessing.incrementAndGet();
                    failuresByReason.put("TIMEOUT", failuresByReason.getOrDefault("TIMEOUT", 0) + 1);

                    alertAdmins("Combat logout processing timeout for " + attempt.playerName + "!");

                    return true; // Remove from map
                }

                return false;
            });

        } catch (Exception e) {
            YakRealms.error("Error checking for timed out processing", e);
        }
    }

    /**
     * Alert administrators
     */
    private static void alertAdmins(String message) {
        try {
            String alertMessage = ChatColor.RED + "[COMBAT LOGOUT ALERT] " + ChatColor.YELLOW + message;

            // Send to online admins
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("yakrealms.admin")) {
                    admin.sendMessage(alertMessage);
                }
            }

            // Log to console
            YakRealms.warn("ADMIN ALERT: " + message);

        } catch (Exception e) {
            YakRealms.error("Error alerting admins", e);
        }
    }

    /**
     * Get basic statistics
     */
    public static MonitorStats getStats() {
        return new MonitorStats(
                totalCombatLogouts.get(),
                successfulProcessing.get(),
                failedProcessing.get(),
                totalRejoinDeaths.get(),
                failedRejoinDeaths.get(),
                rollbacksPerformed.get(),
                processingAttempts.size()
        );
    }

    /**
     * Log basic statistics
     */
    public static void logStats() {
        MonitorStats stats = getStats();
        YakRealms.log("=== Combat Logout Monitor Stats ===");
        YakRealms.log("Total Combat Logouts: " + stats.totalCombatLogouts);
        YakRealms.log("Successful Processing: " + stats.successfulProcessing);
        YakRealms.log("Failed Processing: " + stats.failedProcessing);
        YakRealms.log("Success Rate: " + String.format("%.1f%%", stats.getSuccessRate()));
        YakRealms.log("Rejoin Deaths: " + stats.totalRejoinDeaths);
        YakRealms.log("Failed Rejoin Deaths: " + stats.failedRejoinDeaths);
        YakRealms.log("Rollbacks: " + stats.rollbacksPerformed);
        YakRealms.log("Active Processing: " + stats.activeProcessing);
        YakRealms.log("==================================");
    }

    /**
     * Log detailed statistics
     */
    public static void logDetailedStats() {
        try {
            logStats();

            // Log failure reasons
            if (!failuresByReason.isEmpty()) {
                YakRealms.log("=== Failure Reasons ===");
                failuresByReason.forEach((reason, count) -> {
                    YakRealms.log(reason + ": " + count);
                });
                YakRealms.log("=======================");
            }

            // Log active processing
            if (!processingAttempts.isEmpty()) {
                YakRealms.log("=== Active Processing ===");
                processingAttempts.forEach((playerId, attempt) -> {
                    YakRealms.log(attempt.playerName + " (" + attempt.alignment + "): " +
                            attempt.getDuration() + "ms");
                });
                YakRealms.log("=========================");
            }

        } catch (Exception e) {
            YakRealms.error("Error logging detailed stats", e);
        }
    }

    /**
     * Reset statistics
     */
    public static void resetStats() {
        totalCombatLogouts.set(0);
        successfulProcessing.set(0);
        failedProcessing.set(0);
        totalRejoinDeaths.set(0);
        failedRejoinDeaths.set(0);
        rollbacksPerformed.set(0);
        failuresByReason.clear();
        processingAttempts.clear();

        YakRealms.log("Combat logout monitor statistics reset");
    }

    /**
     * Get health status
     */
    public static boolean isHealthy() {
        MonitorStats stats = getStats();

        // Consider system healthy if:
        // - Success rate is above 90%
        // - No active processing taking too long
        // - Not too many rollbacks

        if (stats.totalCombatLogouts > 0 && stats.getSuccessRate() < 90.0) {
            return false;
        }

        if (stats.rollbacksPerformed > 5) {
            return false;
        }

        // Check for stuck processing
        long currentTime = System.currentTimeMillis();
        for (ProcessingAttempt attempt : processingAttempts.values()) {
            if (currentTime - attempt.startTime > PROCESSING_TIMEOUT) {
                return false;
            }
        }

        return true;
    }

    /**
     * Shutdown the monitoring system
     */
    public static void shutdown() {
        try {
            // Log final stats
            logDetailedStats();

            // Clear data
            processingAttempts.clear();
            failuresByReason.clear();

            YakRealms.log("CombatLogoutMonitor system shut down");

        } catch (Exception e) {
            YakRealms.error("Error during monitor system shutdown", e);
        }
    }

    /**
     * Processing attempt tracking
     */
    private static class ProcessingAttempt {
        final long startTime;
        final String playerName;
        final String alignment;
        boolean completed;

        ProcessingAttempt(String playerName, String alignment) {
            this.startTime = System.currentTimeMillis();
            this.playerName = playerName;
            this.alignment = alignment;
            this.completed = false;
        }

        boolean isTimedOut() {
            return System.currentTimeMillis() - startTime > PROCESSING_TIMEOUT;
        }

        long getDuration() {
            return System.currentTimeMillis() - startTime;
        }
    }

    /**
     * Statistics class
     */
    public static class MonitorStats {
        public final int totalCombatLogouts;
        public final int successfulProcessing;
        public final int failedProcessing;
        public final int totalRejoinDeaths;
        public final int failedRejoinDeaths;
        public final int rollbacksPerformed;
        public final int activeProcessing;

        MonitorStats(int totalCombatLogouts, int successfulProcessing, int failedProcessing,
                     int totalRejoinDeaths, int failedRejoinDeaths, int rollbacksPerformed, int activeProcessing) {
            this.totalCombatLogouts = totalCombatLogouts;
            this.successfulProcessing = successfulProcessing;
            this.failedProcessing = failedProcessing;
            this.totalRejoinDeaths = totalRejoinDeaths;
            this.failedRejoinDeaths = failedRejoinDeaths;
            this.rollbacksPerformed = rollbacksPerformed;
            this.activeProcessing = activeProcessing;
        }

        public double getSuccessRate() {
            if (totalCombatLogouts == 0) return 100.0;
            return (double) successfulProcessing / totalCombatLogouts * 100.0;
        }

        public double getRejoinSuccessRate() {
            if (totalRejoinDeaths == 0) return 100.0;
            return (double) (totalRejoinDeaths - failedRejoinDeaths) / totalRejoinDeaths * 100.0;
        }

        @Override
        public String toString() {
            return String.format("MonitorStats{total=%d, success=%d, failed=%d, successRate=%.1f%%, rollbacks=%d}",
                    totalCombatLogouts, successfulProcessing, failedProcessing, getSuccessRate(), rollbacksPerformed);
        }
    }
}