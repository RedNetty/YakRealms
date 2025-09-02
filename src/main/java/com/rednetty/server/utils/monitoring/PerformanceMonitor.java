package com.rednetty.server.utils.monitoring;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Advanced performance monitoring system for YakRealms
 * 
 * Features:
 * - Real-time TPS monitoring
 * - Memory usage tracking
 * - Thread performance analysis
 * - System resource monitoring
 * - Player count analytics
 * - Plugin performance metrics
 */
public class PerformanceMonitor {
    
    private final Logger logger;
    
    // Performance metrics storage
    private final Map<String, Double> metrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> timers = new ConcurrentHashMap<>();
    
    // TPS tracking
    private final long[] tickTimes = new long[600]; // Last 600 ticks (30 seconds)
    private int tickIndex = 0;
    private long lastTickTime = System.currentTimeMillis();
    
    // Memory tracking
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    
    // System health thresholds
    private static final double TPS_WARNING_THRESHOLD = 18.0;
    private static final double TPS_CRITICAL_THRESHOLD = 15.0;
    private static final double MEMORY_WARNING_THRESHOLD = 0.8; // 80%
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.9; // 90%
    
    public PerformanceMonitor() {
        this.logger = YakRealms.getInstance().getLogger();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        
        // Initialize metrics
        initializeMetrics();
        
        // Start TPS tracking
        startTpsTracking();
        
        logger.info("PerformanceMonitor initialized - Advanced metrics collection enabled");
    }
    
    /**
     * Initialize default metrics
     */
    private void initializeMetrics() {
        metrics.put("tps", 20.0);
        metrics.put("memory_usage_percent", 0.0);
        metrics.put("memory_used_mb", 0.0);
        metrics.put("memory_max_mb", 0.0);
        metrics.put("active_threads", 0.0);
        metrics.put("online_players", 0.0);
        metrics.put("loaded_chunks", 0.0);
        metrics.put("cpu_usage_percent", 0.0);
        
        counters.put("total_ticks", new AtomicLong(0));
        counters.put("lag_spikes", new AtomicLong(0));
        counters.put("memory_cleanups", new AtomicLong(0));
        counters.put("performance_warnings", new AtomicLong(0));
    }
    
    /**
     * Start TPS tracking system
     */
    private void startTpsTracking() {
        Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastTickTime;
            
            tickTimes[tickIndex] = timeDiff;
            tickIndex = (tickIndex + 1) % tickTimes.length;
            lastTickTime = currentTime;
            
            counters.get("total_ticks").incrementAndGet();
            
            // Calculate TPS every 20 ticks (1 second)
            if (counters.get("total_ticks").get() % 20 == 0) {
                updateTpsMetrics();
            }
            
        }, 1L, 1L);
    }
    
    /**
     * Update TPS calculations
     */
    private void updateTpsMetrics() {
        long totalTime = 0;
        int validTicks = 0;
        
        for (long tickTime : tickTimes) {
            if (tickTime > 0) {
                totalTime += tickTime;
                validTicks++;
            }
        }
        
        if (validTicks > 0) {
            double averageTickTime = (double) totalTime / validTicks;
            double tps = Math.min(20.0, 1000.0 / averageTickTime);
            metrics.put("tps", tps);
            
            // Detect lag spikes
            if (tps < TPS_CRITICAL_THRESHOLD) {
                counters.get("lag_spikes").incrementAndGet();
                counters.get("performance_warnings").incrementAndGet();
            }
        }
    }
    
    /**
     * Collect all performance metrics
     */
    public void collectMetrics() {
        try {
            // Memory metrics
            collectMemoryMetrics();
            
            // Thread metrics
            collectThreadMetrics();
            
            // Player metrics
            collectPlayerMetrics();
            
            // World metrics
            collectWorldMetrics();
            
            // System metrics
            collectSystemMetrics();
            
            // Check health thresholds
            checkPerformanceThresholds();
            
        } catch (Exception e) {
            logger.warning("Error collecting performance metrics: " + e.getMessage());
        }
    }
    
    /**
     * Collect memory-related metrics
     */
    private void collectMemoryMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsagePercent = (double) usedMemory / maxMemory;
        
        metrics.put("memory_used_mb", usedMemory / (1024.0 * 1024.0));
        metrics.put("memory_max_mb", maxMemory / (1024.0 * 1024.0));
        metrics.put("memory_usage_percent", memoryUsagePercent);
        
        // Heap memory from MXBean
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        metrics.put("heap_used_mb", heapUsed / (1024.0 * 1024.0));
        metrics.put("heap_max_mb", heapMax / (1024.0 * 1024.0));
    }
    
    /**
     * Collect thread-related metrics
     */
    private void collectThreadMetrics() {
        int activeThreads = threadBean.getThreadCount();
        int peakThreads = threadBean.getPeakThreadCount();
        long totalStartedThreads = threadBean.getTotalStartedThreadCount();
        
        metrics.put("active_threads", (double) activeThreads);
        metrics.put("peak_threads", (double) peakThreads);
        metrics.put("total_started_threads", (double) totalStartedThreads);
    }
    
    /**
     * Collect player-related metrics
     */
    private void collectPlayerMetrics() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        
        metrics.put("online_players", (double) onlinePlayers);
        metrics.put("max_players", (double) maxPlayers);
        metrics.put("player_usage_percent", (double) onlinePlayers / maxPlayers);
    }
    
    /**
     * Collect world-related metrics  
     */
    private void collectWorldMetrics() {
        int totalWorlds = Bukkit.getWorlds().size();
        int totalLoadedChunks = Bukkit.getWorlds().stream()
                .mapToInt(world -> world.getLoadedChunks().length)
                .sum();
        
        metrics.put("loaded_worlds", (double) totalWorlds);
        metrics.put("loaded_chunks", (double) totalLoadedChunks);
    }
    
    /**
     * Collect system-level metrics
     */
    private void collectSystemMetrics() {
        // Available processors
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        metrics.put("available_processors", (double) availableProcessors);
        
        // System load average (if available)
        double systemLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        if (systemLoad >= 0) {
            metrics.put("system_load_average", systemLoad);
            metrics.put("cpu_usage_percent", Math.min(100.0, (systemLoad / availableProcessors) * 100.0));
        }
    }
    
    /**
     * Check performance thresholds and warn if necessary
     */
    private void checkPerformanceThresholds() {
        double currentTps = metrics.get("tps");
        double memoryUsage = metrics.get("memory_usage_percent");
        
        // TPS warnings
        if (currentTps < TPS_CRITICAL_THRESHOLD) {
            logger.severe("Critical performance issue: TPS = " + String.format("%.2f", currentTps));
            counters.get("performance_warnings").incrementAndGet();
        } else if (currentTps < TPS_WARNING_THRESHOLD) {
            logger.warning("Performance warning: TPS = " + String.format("%.2f", currentTps));
        }
        
        // Memory warnings
        if (memoryUsage > MEMORY_CRITICAL_THRESHOLD) {
            logger.severe("Critical memory usage: " + String.format("%.1f%%", memoryUsage * 100));
            System.gc(); // Force garbage collection
            counters.get("memory_cleanups").incrementAndGet();
        } else if (memoryUsage > MEMORY_WARNING_THRESHOLD) {
            logger.warning("High memory usage: " + String.format("%.1f%%", memoryUsage * 100));
        }
    }
    
    /**
     * Get current performance metrics
     */
    public Map<String, Double> getMetrics() {
        return new HashMap<>(metrics);
    }
    
    /**
     * Get performance counters
     */
    public Map<String, Long> getCounters() {
        Map<String, Long> result = new HashMap<>();
        counters.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    
    /**
     * Get current TPS
     */
    public double getCurrentTps() {
        return metrics.getOrDefault("tps", 20.0);
    }
    
    /**
     * Get memory usage percentage
     */
    public double getMemoryUsagePercent() {
        return metrics.getOrDefault("memory_usage_percent", 0.0);
    }
    
    /**
     * Get formatted performance report
     */
    public String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Performance Report ===\n");
        report.append(String.format("TPS: %.2f\n", getCurrentTps()));
        report.append(String.format("Memory: %.1f%% (%.1fMB/%.1fMB)\n", 
                getMemoryUsagePercent() * 100,
                metrics.get("memory_used_mb"),
                metrics.get("memory_max_mb")));
        report.append(String.format("Players: %.0f/%.0f\n", 
                metrics.get("online_players"), 
                metrics.get("max_players")));
        report.append(String.format("Threads: %.0f (Peak: %.0f)\n",
                metrics.get("active_threads"),
                metrics.get("peak_threads")));
        report.append(String.format("Loaded Chunks: %.0f\n", metrics.get("loaded_chunks")));
        report.append(String.format("Total Ticks: %d\n", counters.get("total_ticks").get()));
        report.append(String.format("Lag Spikes: %d\n", counters.get("lag_spikes").get()));
        return report.toString();
    }
    
    /**
     * Reset performance counters
     */
    public void resetCounters() {
        counters.values().forEach(counter -> counter.set(0));
        logger.info("Performance counters reset");
    }
    
    /**
     * Check if system is healthy
     */
    public boolean isSystemHealthy() {
        return getCurrentTps() > TPS_WARNING_THRESHOLD && 
               getMemoryUsagePercent() < MEMORY_WARNING_THRESHOLD;
    }
    
    /**
     * Get system health status
     */
    public String getHealthStatus() {
        if (getCurrentTps() < TPS_CRITICAL_THRESHOLD || getMemoryUsagePercent() > MEMORY_CRITICAL_THRESHOLD) {
            return "CRITICAL";
        } else if (getCurrentTps() < TPS_WARNING_THRESHOLD || getMemoryUsagePercent() > MEMORY_WARNING_THRESHOLD) {
            return "WARNING";
        } else {
            return "HEALTHY";
        }
    }
}