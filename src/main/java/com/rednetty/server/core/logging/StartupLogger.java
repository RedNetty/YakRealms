package com.rednetty.server.core.logging;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Centralized startup logging utility to reduce console spam during initialization
 */
public class StartupLogger {
    
    private static final Logger LOGGER = Bukkit.getLogger();
    private static final List<String> queuedMessages = new ArrayList<>();
    private static boolean verboseMode = false;
    private static YakRealms.EnhancedLogger enhancedLogger;
    
    public static void init(YakRealms.EnhancedLogger logger, boolean verbose) {
        enhancedLogger = logger;
        verboseMode = verbose;
    }
    
    /**
     * Log system initialization with condensed output
     */
    public static void logSystemInit(String systemName, boolean success, long timeMs) {
        if (verboseMode) {
            if (success) {
                info("✓ " + systemName + " (" + timeMs + "ms)");
            } else {
                warn("✗ " + systemName + " failed");
            }
        } else {
            // Only queue slow or failed systems for summary
            if (!success || timeMs > 1000) {
                String status = success ? "slow (" + timeMs + "ms)" : "failed";
                queuedMessages.add(systemName + ": " + status);
            }
        }
    }
    
    /**
     * Log batch summary instead of individual messages
     */
    public static void logBatchSummary(String category, int total, int successful, int failed) {
        if (failed > 0) {
            warn(String.format("%s: %d/%d initialized, %d failed", category, successful, total, failed));
        } else {
            info(String.format("✓ %s: %d systems initialized", category, successful));
        }
    }
    
    /**
     * Suppress verbose initialization messages
     */
    public static void logQuiet(String message) {
        if (verboseMode) {
            info(message);
        }
    }
    
    /**
     * Always log important messages
     */
    public static void info(String message) {
        if (enhancedLogger != null) {
            enhancedLogger.info(message);
        } else {
            LOGGER.info("[YakRealms] " + message);
        }
    }
    
    public static void warn(String message) {
        if (enhancedLogger != null) {
            enhancedLogger.warn(message);
        } else {
            LOGGER.warning("[YakRealms] " + message);
        }
    }
    
    public static void error(String message, Exception e) {
        if (enhancedLogger != null) {
            enhancedLogger.error(message, e);
        } else {
            LOGGER.severe("[YakRealms] " + message + (e != null ? ": " + e.getMessage() : ""));
        }
    }
    
    /**
     * Flush any queued messages in a summary
     */
    public static void flushQueuedMessages() {
        if (!queuedMessages.isEmpty() && !verboseMode) {
            info("Systems requiring attention:");
            for (String message : queuedMessages) {
                info("  " + message);
            }
            queuedMessages.clear();
        }
    }
    
    /**
     * Create a consolidated summary message
     */
    public static void logStartupSummary(String version, long totalTime, int systemsInitialized, int systemsFailed) {
        String status = systemsFailed > 0 ? 
            String.format("YakRealms v%s enabled (%dms) - %d systems initialized, %d failed", 
                version, totalTime, systemsInitialized, systemsFailed) :
            String.format("YakRealms v%s enabled (%dms) - All %d systems operational", 
                version, totalTime, systemsInitialized);
        
        if (systemsFailed > 0) {
            warn(status);
            flushQueuedMessages();
        } else {
            info(status);
        }
    }
}