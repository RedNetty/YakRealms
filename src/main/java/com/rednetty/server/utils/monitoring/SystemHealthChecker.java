package com.rednetty.server.utils.monitoring;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Advanced system health monitoring and diagnostic system for YakRealms
 * 
 * Features:
 * - Comprehensive health checks across all server subsystems
 * - Automatic issue detection and recovery mechanisms
 * - Health score calculation and trending
 * - Proactive alerting and notification system
 * - System recovery automation
 * - Performance degradation detection
 */
public class SystemHealthChecker {
    
    private final Logger logger;
    private final PerformanceMonitor performanceMonitor;
    
    // Health monitoring components
    private final ScheduledExecutorService healthScheduler;
    private final Map<String, HealthMetric> healthMetrics = new ConcurrentHashMap<>();
    private final Map<String, SystemRecovery> recoveryHandlers = new ConcurrentHashMap<>();
    private final AtomicLong totalHealthChecks = new AtomicLong(0);
    private final AtomicLong failedHealthChecks = new AtomicLong(0);
    
    // System beans for monitoring
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final OperatingSystemMXBean osBean;
    
    // Health thresholds
    private static final double CRITICAL_MEMORY_THRESHOLD = 0.95; // 95%
    private static final double WARNING_MEMORY_THRESHOLD = 0.85;  // 85%
    private static final double CRITICAL_TPS_THRESHOLD = 12.0;
    private static final double WARNING_TPS_THRESHOLD = 16.0;
    private static final int CRITICAL_THREAD_COUNT = 200;
    private static final int WARNING_THREAD_COUNT = 150;
    
    // Health scoring
    private volatile double currentHealthScore = 100.0;
    private final Queue<Double> healthHistory = new LinkedList<>();
    private static final int HEALTH_HISTORY_SIZE = 20; // Last 20 checks
    
    public SystemHealthChecker(PerformanceMonitor performanceMonitor) {
        this.logger = YakRealms.getInstance().getLogger();
        this.performanceMonitor = performanceMonitor;
        this.healthScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "YakRealms-HealthChecker");
            t.setDaemon(true);
            return t;
        });
        
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        
        initializeHealthMetrics();
        initializeRecoveryHandlers();
        startHealthMonitoring();
        
        logger.info("SystemHealthChecker initialized - Advanced health monitoring enabled");
    }
    
    /**
     * Initialize all health metrics
     */
    private void initializeHealthMetrics() {
        // Core system metrics
        healthMetrics.put("memory", new HealthMetric("Memory Usage", 100.0));
        healthMetrics.put("tps", new HealthMetric("TPS Performance", 100.0));
        healthMetrics.put("threads", new HealthMetric("Thread Health", 100.0));
        healthMetrics.put("players", new HealthMetric("Player Systems", 100.0));
        healthMetrics.put("worlds", new HealthMetric("World Systems", 100.0));
        healthMetrics.put("database", new HealthMetric("Database Connectivity", 100.0));
        healthMetrics.put("plugins", new HealthMetric("Plugin Integration", 100.0));
        healthMetrics.put("disk", new HealthMetric("Disk I/O", 100.0));
        healthMetrics.put("network", new HealthMetric("Network Health", 100.0));
        healthMetrics.put("economy", new HealthMetric("Economy Systems", 100.0));
    }
    
    /**
     * Initialize recovery handlers for automated issue resolution
     */
    private void initializeRecoveryHandlers() {
        // Memory recovery
        recoveryHandlers.put("memory", new SystemRecovery("Memory Recovery") {
            @Override
            public boolean attemptRecovery() {
                logger.warning("Attempting memory recovery - forcing garbage collection");
                System.gc();
                
                // Wait a moment and check if memory improved
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                Runtime runtime = Runtime.getRuntime();
                double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
                boolean recovered = memoryUsage < WARNING_MEMORY_THRESHOLD;
                
                if (recovered) {
                    logger.info("Memory recovery successful - usage now at " + String.format("%.1f%%", memoryUsage * 100));
                } else {
                    logger.severe("Memory recovery failed - usage still at " + String.format("%.1f%%", memoryUsage * 100));
                }
                
                return recovered;
            }
        });
        
        // Thread recovery
        recoveryHandlers.put("threads", new SystemRecovery("Thread Recovery") {
            @Override
            public boolean attemptRecovery() {
                logger.warning("Attempting thread recovery - checking for stuck threads");
                
                int initialThreadCount = Thread.activeCount();
                
                // Could implement more sophisticated thread recovery here
                // For now, just log the situation for manual intervention
                Thread[] threads = new Thread[initialThreadCount * 2];
                int actualCount = Thread.enumerate(threads);
                
                int suspiciousThreads = 0;
                for (int i = 0; i < actualCount; i++) {
                    Thread thread = threads[i];
                    if (thread != null && thread.getState() == Thread.State.BLOCKED) {
                        suspiciousThreads++;
                        logger.warning("Blocked thread detected: " + thread.getName() + " - " + thread.getState());
                    }
                }
                
                boolean recovered = Thread.activeCount() < WARNING_THREAD_COUNT;
                logger.info("Thread recovery check complete - " + suspiciousThreads + " suspicious threads found");
                return recovered;
            }
        });
        
        // TPS recovery
        recoveryHandlers.put("tps", new SystemRecovery("TPS Recovery") {
            @Override
            public boolean attemptRecovery() {
                logger.warning("Attempting TPS recovery - analyzing performance bottlenecks");
                
                // Collect current performance metrics
                if (performanceMonitor != null) {
                    performanceMonitor.collectMetrics();
                    Map<String, Double> metrics = performanceMonitor.getMetrics();
                    
                    // Log performance state for diagnosis
                    logger.info("TPS Recovery - Current metrics: " + 
                        String.format("TPS=%.2f, Memory=%.1f%%, Threads=%.0f, Players=%.0f",
                            metrics.getOrDefault("tps", 0.0),
                            metrics.getOrDefault("memory_usage_percent", 0.0) * 100,
                            metrics.getOrDefault("active_threads", 0.0),
                            metrics.getOrDefault("online_players", 0.0)));
                }
                
                // Force garbage collection to help with performance
                System.gc();
                
                // Check if TPS improved
                double currentTps = performanceMonitor != null ? performanceMonitor.getCurrentTps() : 20.0;
                boolean recovered = currentTps > WARNING_TPS_THRESHOLD;
                
                if (recovered) {
                    logger.info("TPS recovery successful - current TPS: " + String.format("%.2f", currentTps));
                } else {
                    logger.severe("TPS recovery failed - current TPS: " + String.format("%.2f", currentTps));
                }
                
                return recovered;
            }
        });
    }
    
    /**
     * Start the health monitoring system
     */
    private void startHealthMonitoring() {
        // Perform health checks every 30 seconds
        healthScheduler.scheduleAtFixedRate(() -> {
            try {
                performCompleteHealthCheck();
            } catch (Exception e) {
                logger.severe("Error during health check: " + e.getMessage());
                failedHealthChecks.incrementAndGet();
            }
        }, 30L, 30L, TimeUnit.SECONDS);
        
        // Perform quick checks every 10 seconds
        healthScheduler.scheduleAtFixedRate(() -> {
            try {
                performQuickHealthCheck();
            } catch (Exception e) {
                logger.warning("Error during quick health check: " + e.getMessage());
            }
        }, 10L, 10L, TimeUnit.SECONDS);
    }
    
    /**
     * Perform comprehensive health check
     */
    public HealthReport performCompleteHealthCheck() {
        totalHealthChecks.incrementAndGet();
        
        HealthReport report = new HealthReport();
        
        try {
            // Check all subsystems
            checkMemoryHealth(report);
            checkTpsHealth(report);
            checkThreadHealth(report);
            checkPlayerHealth(report);
            checkWorldHealth(report);
            checkDatabaseHealth(report);
            checkPluginHealth(report);
            checkDiskHealth(report);
            checkNetworkHealth(report);
            checkEconomyHealth(report);
            
            // Calculate overall health score
            calculateHealthScore(report);
            
            // Update health history
            updateHealthHistory();
            
            // Attempt recovery for critical issues
            attemptAutoRecovery(report);
            
            // Log health status
            logHealthStatus(report);
            
        } catch (Exception e) {
            logger.severe("Error during complete health check: " + e.getMessage());
            report.addIssue("SYSTEM", "Health check failed: " + e.getMessage(), HealthSeverity.CRITICAL);
        }
        
        return report;
    }
    
    /**
     * Perform quick health check (essential metrics only)
     */
    public void performQuickHealthCheck() {
        try {
            // Quick TPS check
            if (performanceMonitor != null) {
                double tps = performanceMonitor.getCurrentTps();
                if (tps < CRITICAL_TPS_THRESHOLD) {
                    logger.severe("Critical TPS detected: " + String.format("%.2f", tps));
                    attemptRecovery("tps");
                }
            }
            
            // Quick memory check
            Runtime runtime = Runtime.getRuntime();
            double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
            if (memoryUsage > CRITICAL_MEMORY_THRESHOLD) {
                logger.severe("Critical memory usage: " + String.format("%.1f%%", memoryUsage * 100));
                attemptRecovery("memory");
            }
            
        } catch (Exception e) {
            logger.warning("Error during quick health check: " + e.getMessage());
        }
    }
    
    /**
     * Check memory health
     */
    private void checkMemoryHealth(HealthReport report) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            double memoryUsage = (double) usedMemory / maxMemory;
            
            HealthMetric metric = healthMetrics.get("memory");
            if (memoryUsage > CRITICAL_MEMORY_THRESHOLD) {
                metric.setScore(0.0);
                report.addIssue("MEMORY", "Critical memory usage: " + String.format("%.1f%%", memoryUsage * 100), HealthSeverity.CRITICAL);
            } else if (memoryUsage > WARNING_MEMORY_THRESHOLD) {
                metric.setScore(50.0);
                report.addIssue("MEMORY", "High memory usage: " + String.format("%.1f%%", memoryUsage * 100), HealthSeverity.WARNING);
            } else {
                double score = Math.max(0, 100.0 - (memoryUsage * 100));
                metric.setScore(score);
            }
            
            // Heap memory check
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();
            double heapUsage = (double) heapUsed / heapMax;
            
            if (heapUsage > 0.95) {
                report.addIssue("MEMORY", "Critical heap usage: " + String.format("%.1f%%", heapUsage * 100), HealthSeverity.CRITICAL);
            }
            
        } catch (Exception e) {
            report.addIssue("MEMORY", "Memory check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Check TPS health
     */
    private void checkTpsHealth(HealthReport report) {
        try {
            double tps = performanceMonitor != null ? performanceMonitor.getCurrentTps() : 20.0;
            
            HealthMetric metric = healthMetrics.get("tps");
            if (tps < CRITICAL_TPS_THRESHOLD) {
                metric.setScore(0.0);
                report.addIssue("TPS", "Critical TPS: " + String.format("%.2f", tps), HealthSeverity.CRITICAL);
            } else if (tps < WARNING_TPS_THRESHOLD) {
                metric.setScore(50.0);
                report.addIssue("TPS", "Low TPS: " + String.format("%.2f", tps), HealthSeverity.WARNING);
            } else {
                double score = Math.min(100.0, (tps / 20.0) * 100.0);
                metric.setScore(score);
            }
            
        } catch (Exception e) {
            report.addIssue("TPS", "TPS check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Check thread health
     */
    private void checkThreadHealth(HealthReport report) {
        try {
            int activeThreads = threadBean.getThreadCount();
            int peakThreads = threadBean.getPeakThreadCount();
            
            HealthMetric metric = healthMetrics.get("threads");
            if (activeThreads > CRITICAL_THREAD_COUNT) {
                metric.setScore(0.0);
                report.addIssue("THREADS", "Critical thread count: " + activeThreads, HealthSeverity.CRITICAL);
            } else if (activeThreads > WARNING_THREAD_COUNT) {
                metric.setScore(50.0);
                report.addIssue("THREADS", "High thread count: " + activeThreads, HealthSeverity.WARNING);
            } else {
                double score = Math.max(0, 100.0 - (activeThreads / 2.0));
                metric.setScore(score);
            }
            
            // Check for deadlocked threads
            long[] deadlockedThreads = threadBean.findDeadlockedThreads();
            if (deadlockedThreads != null && deadlockedThreads.length > 0) {
                report.addIssue("THREADS", "Deadlocked threads detected: " + deadlockedThreads.length, HealthSeverity.CRITICAL);
            }
            
        } catch (Exception e) {
            report.addIssue("THREADS", "Thread check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Check player system health
     */
    private void checkPlayerHealth(HealthReport report) {
        try {
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            int maxPlayers = Bukkit.getMaxPlayers();
            
            HealthMetric metric = healthMetrics.get("players");
            
            // Check for player connection issues
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead()) continue; // Skip dead players
                
                // Check player connectivity (basic)
                if (!player.isOnline()) {
                    report.addIssue("PLAYERS", "Player connectivity issue detected", HealthSeverity.WARNING);
                }
            }
            
            // Player load assessment
            double playerLoad = (double) onlinePlayers / maxPlayers;
            if (playerLoad > 0.9) {
                metric.setScore(70.0);
                report.addIssue("PLAYERS", "High player load: " + String.format("%.1f%%", playerLoad * 100), HealthSeverity.WARNING);
            } else {
                metric.setScore(100.0);
            }
            
        } catch (Exception e) {
            report.addIssue("PLAYERS", "Player check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Check world system health
     */
    private void checkWorldHealth(HealthReport report) {
        try {
            List<World> worlds = Bukkit.getWorlds();
            HealthMetric metric = healthMetrics.get("worlds");
            
            int totalChunks = 0;
            int totalEntities = 0;
            
            for (World world : worlds) {
                totalChunks += world.getLoadedChunks().length;
                totalEntities += world.getEntities().size();
                
                // Check for world-specific issues
                if (world.getLoadedChunks().length > 1000) {
                    report.addIssue("WORLDS", "High chunk count in " + world.getName() + ": " + world.getLoadedChunks().length, HealthSeverity.WARNING);
                }
                
                if (world.getEntities().size() > 2000) {
                    report.addIssue("WORLDS", "High entity count in " + world.getName() + ": " + world.getEntities().size(), HealthSeverity.WARNING);
                }
            }
            
            // Overall world health
            if (totalChunks > 5000 || totalEntities > 10000) {
                metric.setScore(50.0);
                report.addIssue("WORLDS", "High world load - Chunks: " + totalChunks + ", Entities: " + totalEntities, HealthSeverity.WARNING);
            } else {
                metric.setScore(100.0);
            }
            
        } catch (Exception e) {
            report.addIssue("WORLDS", "World check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Check database health
     */
    private void checkDatabaseHealth(HealthReport report) {
        try {
            HealthMetric metric = healthMetrics.get("database");
            
            // Try to get database manager
            YakRealms instance = YakRealms.getInstance();
            if (instance.getMongoDBManager() != null) {
                // Database is available
                metric.setScore(100.0);
            } else {
                metric.setScore(0.0);
                report.addIssue("DATABASE", "Database manager not available", HealthSeverity.CRITICAL);
            }
            
        } catch (Exception e) {
            report.addIssue("DATABASE", "Database check failed: " + e.getMessage(), HealthSeverity.CRITICAL);
        }
    }
    
    /**
     * Check plugin integration health
     */
    private void checkPluginHealth(HealthReport report) {
        try {
            HealthMetric metric = healthMetrics.get("plugins");
            
            // Check if all required plugins are loaded
            int loadedPlugins = Bukkit.getPluginManager().getPlugins().length;
            int enabledPlugins = 0;
            
            for (var plugin : Bukkit.getPluginManager().getPlugins()) {
                if (plugin.isEnabled()) {
                    enabledPlugins++;
                }
            }
            
            if (enabledPlugins == loadedPlugins) {
                metric.setScore(100.0);
            } else {
                metric.setScore(70.0);
                report.addIssue("PLUGINS", "Some plugins disabled: " + (loadedPlugins - enabledPlugins) + " of " + loadedPlugins, HealthSeverity.WARNING);
            }
            
        } catch (Exception e) {
            report.addIssue("PLUGINS", "Plugin check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Check disk I/O health
     */
    private void checkDiskHealth(HealthReport report) {
        try {
            HealthMetric metric = healthMetrics.get("disk");
            
            // Check available disk space
            java.io.File serverRoot = new java.io.File(".");
            long totalSpace = serverRoot.getTotalSpace();
            long freeSpace = serverRoot.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            
            double diskUsage = (double) usedSpace / totalSpace;
            
            if (diskUsage > 0.95) {
                metric.setScore(0.0);
                report.addIssue("DISK", "Critical disk usage: " + String.format("%.1f%%", diskUsage * 100), HealthSeverity.CRITICAL);
            } else if (diskUsage > 0.85) {
                metric.setScore(50.0);
                report.addIssue("DISK", "High disk usage: " + String.format("%.1f%%", diskUsage * 100), HealthSeverity.WARNING);
            } else {
                metric.setScore(100.0);
            }
            
        } catch (Exception e) {
            report.addIssue("DISK", "Disk check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Check network health
     */
    private void checkNetworkHealth(HealthReport report) {
        try {
            HealthMetric metric = healthMetrics.get("network");
            
            // Basic network connectivity check
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            
            // For now, assume network is healthy if players are connected
            if (onlinePlayers > 0) {
                metric.setScore(100.0);
            } else {
                // Could implement more sophisticated network checks here
                metric.setScore(90.0);
            }
            
        } catch (Exception e) {
            report.addIssue("NETWORK", "Network check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Check economy system health
     */
    private void checkEconomyHealth(HealthReport report) {
        try {
            HealthMetric metric = healthMetrics.get("economy");
            
            // Check if economy managers are available
            YakRealms instance = YakRealms.getInstance();
            boolean economyHealthy = true;
            
            if (instance.getEconomyManager() == null) {
                economyHealthy = false;
                report.addIssue("ECONOMY", "Economy manager not available", HealthSeverity.WARNING);
            }
            
            if (instance.getMarketManager() == null) {
                economyHealthy = false;
                report.addIssue("ECONOMY", "Market manager not available", HealthSeverity.WARNING);
            }
            
            metric.setScore(economyHealthy ? 100.0 : 50.0);
            
        } catch (Exception e) {
            report.addIssue("ECONOMY", "Economy check failed: " + e.getMessage(), HealthSeverity.WARNING);
        }
    }
    
    /**
     * Calculate overall health score
     */
    private void calculateHealthScore(HealthReport report) {
        double totalScore = 0.0;
        int metricCount = 0;
        
        for (HealthMetric metric : healthMetrics.values()) {
            totalScore += metric.getScore();
            metricCount++;
        }
        
        currentHealthScore = metricCount > 0 ? totalScore / metricCount : 0.0;
        report.setOverallScore(currentHealthScore);
    }
    
    /**
     * Update health history for trending
     */
    private void updateHealthHistory() {
        healthHistory.offer(currentHealthScore);
        if (healthHistory.size() > HEALTH_HISTORY_SIZE) {
            healthHistory.poll();
        }
    }
    
    /**
     * Attempt automatic recovery for critical issues
     */
    private void attemptAutoRecovery(HealthReport report) {
        for (HealthIssue issue : report.getIssues()) {
            if (issue.getSeverity() == HealthSeverity.CRITICAL) {
                String systemName = issue.getSystem().toLowerCase();
                if (recoveryHandlers.containsKey(systemName)) {
                    logger.warning("Attempting automatic recovery for: " + issue.getSystem());
                    attemptRecovery(systemName);
                }
            }
        }
    }
    
    /**
     * Attempt recovery for a specific system
     */
    private boolean attemptRecovery(String systemName) {
        SystemRecovery recovery = recoveryHandlers.get(systemName);
        if (recovery != null) {
            try {
                return recovery.attemptRecovery();
            } catch (Exception e) {
                logger.severe("Recovery attempt failed for " + systemName + ": " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    /**
     * Log health status
     */
    private void logHealthStatus(HealthReport report) {
        if (report.getOverallScore() < 50.0) {
            logger.severe("SYSTEM HEALTH CRITICAL: Score " + String.format("%.1f", report.getOverallScore()) + "/100");
            for (HealthIssue issue : report.getCriticalIssues()) {
                logger.severe("  - " + issue.getDescription());
            }
        } else if (report.getOverallScore() < 80.0) {
            logger.warning("System health degraded: Score " + String.format("%.1f", report.getOverallScore()) + "/100");
            for (HealthIssue issue : report.getWarningIssues()) {
                logger.warning("  - " + issue.getDescription());
            }
        } else {
            logger.fine("System health good: Score " + String.format("%.1f", report.getOverallScore()) + "/100");
        }
    }
    
    /**
     * Get current health score
     */
    public double getCurrentHealthScore() {
        return currentHealthScore;
    }
    
    /**
     * Get health history for trending
     */
    public List<Double> getHealthHistory() {
        return new ArrayList<>(healthHistory);
    }
    
    /**
     * Get health metrics
     */
    public Map<String, HealthMetric> getHealthMetrics() {
        return new HashMap<>(healthMetrics);
    }
    
    /**
     * Get health report summary
     */
    public String getHealthSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== System Health Summary ===\n");
        summary.append(String.format("Overall Score: %.1f/100\n", currentHealthScore));
        summary.append(String.format("Total Checks: %d\n", totalHealthChecks.get()));
        summary.append(String.format("Failed Checks: %d\n", failedHealthChecks.get()));
        
        summary.append("\nSubsystem Health:\n");
        for (Map.Entry<String, HealthMetric> entry : healthMetrics.entrySet()) {
            HealthMetric metric = entry.getValue();
            summary.append(String.format("  %s: %.1f/100\n", 
                metric.getName(), metric.getScore()));
        }
        
        return summary.toString();
    }
    
    /**
     * Shutdown the health checker
     */
    public void shutdown() {
        if (healthScheduler != null && !healthScheduler.isShutdown()) {
            healthScheduler.shutdown();
            try {
                if (!healthScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    healthScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                healthScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("SystemHealthChecker shut down");
    }
    
    // Health data classes
    
    /**
     * Represents a health metric for a specific system
     */
    public static class HealthMetric {
        private final String name;
        private volatile double score;
        private volatile long lastUpdated;
        
        public HealthMetric(String name, double initialScore) {
            this.name = name;
            this.score = initialScore;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public String getName() { return name; }
        public double getScore() { return score; }
        public long getLastUpdated() { return lastUpdated; }
        
        public void setScore(double score) {
            this.score = Math.max(0.0, Math.min(100.0, score));
            this.lastUpdated = System.currentTimeMillis();
        }
    }
    
    /**
     * Health issue severity levels
     */
    public enum HealthSeverity {
        INFO, WARNING, CRITICAL
    }
    
    /**
     * Represents a health issue
     */
    public static class HealthIssue {
        private final String system;
        private final String description;
        private final HealthSeverity severity;
        private final long timestamp;
        
        public HealthIssue(String system, String description, HealthSeverity severity) {
            this.system = system;
            this.description = description;
            this.severity = severity;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getSystem() { return system; }
        public String getDescription() { return description; }
        public HealthSeverity getSeverity() { return severity; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Complete health report
     */
    public static class HealthReport {
        private final List<HealthIssue> issues = new ArrayList<>();
        private double overallScore = 100.0;
        private final long timestamp = System.currentTimeMillis();
        
        public void addIssue(String system, String description, HealthSeverity severity) {
            issues.add(new HealthIssue(system, description, severity));
        }
        
        public List<HealthIssue> getIssues() { return new ArrayList<>(issues); }
        
        public List<HealthIssue> getCriticalIssues() {
            return issues.stream()
                .filter(issue -> issue.getSeverity() == HealthSeverity.CRITICAL)
                .collect(java.util.stream.Collectors.toList());
        }
        
        public List<HealthIssue> getWarningIssues() {
            return issues.stream()
                .filter(issue -> issue.getSeverity() == HealthSeverity.WARNING)
                .collect(java.util.stream.Collectors.toList());
        }
        
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double score) { this.overallScore = score; }
        public long getTimestamp() { return timestamp; }
        
        public boolean isHealthy() { return overallScore >= 80.0; }
        public boolean isCritical() { return overallScore < 50.0; }
    }
    
    /**
     * Abstract system recovery handler
     */
    public abstract static class SystemRecovery {
        private final String name;
        private volatile long lastAttempt = 0;
        private volatile int attemptCount = 0;
        
        protected SystemRecovery(String name) {
            this.name = name;
        }
        
        public String getName() { return name; }
        public long getLastAttempt() { return lastAttempt; }
        public int getAttemptCount() { return attemptCount; }
        
        public final boolean tryRecovery() {
            lastAttempt = System.currentTimeMillis();
            attemptCount++;
            return attemptRecovery();
        }
        
        protected abstract boolean attemptRecovery();
    }
}