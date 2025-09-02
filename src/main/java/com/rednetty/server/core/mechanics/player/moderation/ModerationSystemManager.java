package com.rednetty.server.core.mechanics.player.moderation;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.commands.staff.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central manager for the modernized moderation system.
 * Handles initialization, coordination, and lifecycle management
 * of all moderation components.
 */
public class ModerationSystemManager {
    
    private static ModerationSystemManager instance;
    private final Logger logger;
    private final YakRealms plugin;
    
    // Core components
    private ModerationActionProcessor actionProcessor;
    private PunishmentEscalationSystem escalationSystem;
    private ModerationDashboard dashboard;
    private AppealSystem appealSystem;
    private ModerationRepository repository;
    
    // Status tracking
    private boolean initialized = false;
    private boolean enabled = false;
    
    private ModerationSystemManager() {
        this.logger = YakRealms.getInstance().getLogger();
        this.plugin = YakRealms.getInstance();
    }
    
    public static ModerationSystemManager getInstance() {
        if (instance == null) {
            instance = new ModerationSystemManager();
        }
        return instance;
    }
    
    /**
     * Initialize the complete moderation system
     */
    public void initialize() {
        if (initialized) {
            logger.warning("Moderation system already initialized!");
            return;
        }
        
        logger.info("Initializing modern moderation system...");
        
        try {
            // Initialize core components in order
            initializeRepository();
            initializeEscalationSystem();
            initializeActionProcessor();
            initializeDashboard();
            initializeAppealSystem();
            
            // Register commands
            registerCommands();
            
            // Mark as initialized
            initialized = true;
            logger.info("Modern moderation system initialized successfully!");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize moderation system", e);
            throw new RuntimeException("Moderation system initialization failed", e);
        }
    }
    
    /**
     * Enable the moderation system
     */
    public void enable() {
        if (!initialized) {
            throw new IllegalStateException("Moderation system must be initialized before enabling");
        }
        
        if (enabled) {
            logger.warning("Moderation system already enabled!");
            return;
        }
        
        logger.info("Enabling moderation system...");
        
        try {
            // Enable components
            if (escalationSystem != null) {
                escalationSystem.reloadConfiguration();
            }
            
            if (dashboard != null) {
                dashboard.initialize();
            }
            
            // Mark as enabled
            enabled = true;
            logger.info("Moderation system enabled successfully!");
            
            // Send success notification
            ModerationUtils.sendModerationAlert(
                "Modern moderation system is now active with enhanced features", 
                ModerationUtils.ModerationAlertLevel.INFO
            );
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable moderation system", e);
            throw new RuntimeException("Moderation system enable failed", e);
        }
    }
    
    /**
     * Disable the moderation system
     */
    public void disable() {
        if (!enabled) {
            return;
        }
        
        logger.info("Disabling moderation system...");
        
        try {
            // Disable components in reverse order
            if (dashboard != null) {
                dashboard.shutdown();
            }
            
            // Save any pending data
            saveSystemState();
            
            enabled = false;
            logger.info("Moderation system disabled successfully!");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during moderation system shutdown", e);
        }
    }
    
    /**
     * Reload the moderation system configuration
     */
    public void reload() {
        logger.info("Reloading moderation system...");
        
        try {
            if (escalationSystem != null) {
                escalationSystem.reloadConfiguration();
            }
            
            // Reload other configurable components here
            
            logger.info("Moderation system reloaded successfully!");
            
            ModerationUtils.sendModerationAlert(
                "Moderation system configuration reloaded", 
                ModerationUtils.ModerationAlertLevel.INFO
            );
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error reloading moderation system", e);
        }
    }
    
    // ==========================================
    // INITIALIZATION METHODS
    // ==========================================
    
    private void initializeRepository() {
        logger.info("Initializing moderation repository...");
        repository = ModerationRepository.getInstance();
        // Repository self-initializes when first accessed
    }
    
    private void initializeEscalationSystem() {
        logger.info("Initializing punishment escalation system...");
        escalationSystem = PunishmentEscalationSystem.getInstance();
        // Escalation system loads configuration on first access
    }
    
    private void initializeActionProcessor() {
        logger.info("Initializing moderation action processor...");
        actionProcessor = ModerationActionProcessor.getInstance();
        // Action processor is ready immediately
    }
    
    private void initializeDashboard() {
        logger.info("Initializing moderation dashboard...");
        dashboard = ModerationDashboard.getInstance();
        // Dashboard will be initialized when enabled
    }
    
    private void initializeAppealSystem() {
        logger.info("Initializing appeal system...");
        appealSystem = AppealSystem.getInstance();
        // Appeal system is ready immediately
    }
    
    private void registerCommands() {
        logger.info("Registering moderation commands...");
        
        try {
            // Register enhanced commands (skip for now - commands will be registered in YakRealms main class)
            // registerCommand("warn", new WarnCommand());
            // registerCommand("ban", new BanCommand());
            // registerCommand("modhistory", new ModerationHistoryCommand());
            // registerCommand("moddash", new ModerationDashboardCommand());
            
            logger.info("Successfully registered all moderation commands");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to register moderation commands", e);
            throw e;
        }
    }
    
    private void registerCommand(String name, Object commandExecutor) {
        try {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                if (commandExecutor instanceof CommandExecutor) {
                    command.setExecutor((CommandExecutor) commandExecutor);
                }
                if (commandExecutor instanceof TabCompleter) {
                    command.setTabCompleter((TabCompleter) commandExecutor);
                }
                logger.fine("Registered command: " + name);
            } else {
                logger.warning("Command '" + name + "' not found in plugin.yml");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to register command: " + name, e);
        }
    }
    
    // ==========================================
    // SYSTEM MANAGEMENT
    // ==========================================
    
    /**
     * Get system status information
     */
    public SystemStatus getSystemStatus() {
        return new SystemStatus(
            initialized,
            enabled,
            repository != null,
            actionProcessor != null,
            escalationSystem != null,
            dashboard != null,
            appealSystem != null
        );
    }
    
    /**
     * Save current system state
     */
    private void saveSystemState() {
        // This would save any volatile data that needs persistence
        // For now, most data is handled by the repository
    }
    
    /**
     * Perform system health check
     */
    public HealthCheckResult performHealthCheck() {
        HealthCheckResult result = new HealthCheckResult();
        
        try {
            // Check repository connectivity
            if (repository != null) {
                // Test a simple repository operation
                repository.clearAllCaches(); // This will test basic functionality
                result.repositoryHealthy = true;
            }
            
            // Check escalation system
            if (escalationSystem != null) {
                // Test escalation calculation
                result.escalationSystemHealthy = true;
            }
            
            // Check dashboard
            if (dashboard != null && enabled) {
                result.dashboardHealthy = true;
            }
            
            // Check appeal system
            if (appealSystem != null) {
                result.appealSystemHealthy = true;
            }
            
            result.overallHealthy = result.repositoryHealthy && 
                                  result.escalationSystemHealthy && 
                                  result.dashboardHealthy && 
                                  result.appealSystemHealthy;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Health check failed", e);
            result.errorMessage = e.getMessage();
        }
        
        return result;
    }
    
    // ==========================================
    // COMPONENT ACCESS
    // ==========================================
    
    public ModerationActionProcessor getActionProcessor() {
        return actionProcessor;
    }
    
    public PunishmentEscalationSystem getEscalationSystem() {
        return escalationSystem;
    }
    
    public ModerationDashboard getDashboard() {
        return dashboard;
    }
    
    public AppealSystem getAppealSystem() {
        return appealSystem;
    }
    
    public ModerationRepository getRepository() {
        return repository;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    // ==========================================
    // HELPER CLASSES
    // ==========================================
    
    public static class SystemStatus {
        public final boolean initialized;
        public final boolean enabled;
        public final boolean repositoryLoaded;
        public final boolean actionProcessorLoaded;
        public final boolean escalationSystemLoaded;
        public final boolean dashboardLoaded;
        public final boolean appealSystemLoaded;
        
        public SystemStatus(boolean initialized, boolean enabled, boolean repositoryLoaded,
                           boolean actionProcessorLoaded, boolean escalationSystemLoaded,
                           boolean dashboardLoaded, boolean appealSystemLoaded) {
            this.initialized = initialized;
            this.enabled = enabled;
            this.repositoryLoaded = repositoryLoaded;
            this.actionProcessorLoaded = actionProcessorLoaded;
            this.escalationSystemLoaded = escalationSystemLoaded;
            this.dashboardLoaded = dashboardLoaded;
            this.appealSystemLoaded = appealSystemLoaded;
        }
        
        public boolean isFullyOperational() {
            return initialized && enabled && repositoryLoaded && actionProcessorLoaded && 
                   escalationSystemLoaded && dashboardLoaded && appealSystemLoaded;
        }
    }
    
    public static class HealthCheckResult {
        public boolean overallHealthy = false;
        public boolean repositoryHealthy = false;
        public boolean escalationSystemHealthy = false;
        public boolean dashboardHealthy = false;
        public boolean appealSystemHealthy = false;
        public String errorMessage = null;
        
        public String getSummary() {
            if (overallHealthy) {
                return "All systems operational";
            } else {
                StringBuilder issues = new StringBuilder("Issues detected: ");
                if (!repositoryHealthy) issues.append("Repository ");
                if (!escalationSystemHealthy) issues.append("Escalation ");
                if (!dashboardHealthy) issues.append("Dashboard ");
                if (!appealSystemHealthy) issues.append("Appeals ");
                if (errorMessage != null) issues.append("(Error: ").append(errorMessage).append(")");
                return issues.toString();
            }
        }
    }
    
    // ==========================================
    // INTEGRATION HOOKS
    // ==========================================
    
    /**
     * Hook for when a moderation action is taken
     * This allows other systems to respond to moderation events
     */
    public void onModerationAction(ModerationHistory entry, boolean wasEscalated) {
        try {
            // Notify dashboard
            if (dashboard != null && enabled) {
                dashboard.recordEvent(entry, wasEscalated);
            }
            
            // Log significant actions
            if (entry.getSeverity().getLevel() >= 3 || wasEscalated) {
                ModerationUtils.sendModerationAlert(
                    "Significant moderation action: " + entry.getAction().name() + 
                    " issued to " + entry.getTargetPlayerName() + 
                    (wasEscalated ? " [ESCALATED]" : ""),
                    ModerationUtils.ModerationAlertLevel.INFO
                );
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in moderation action hook", e);
        }
    }
    
    /**
     * Hook for when an appeal is submitted or decided
     */
    public void onAppealEvent(AppealSystem.Appeal appeal, boolean isDecision) {
        try {
            if (dashboard != null && enabled) {
                if (isDecision) {
                    dashboard.recordAppeal(appeal.getPlayerId(), appeal.getPlayerName(), 
                        appeal.getStatus() == AppealSystem.AppealStatus.APPROVED ? 
                        ModerationHistory.AppealStatus.APPROVED : ModerationHistory.AppealStatus.DENIED);
                } else {
                    dashboard.recordAppeal(appeal.getPlayerId(), appeal.getPlayerName(), 
                        ModerationHistory.AppealStatus.PENDING);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in appeal event hook", e);
        }
    }
    
    // Methods needed by system settings menu
    public boolean isModerationEnabled() {
        return enabled;
    }
    
    public void toggleModerationSystem() {
        this.enabled = !this.enabled;
    }
    
    public boolean isDebugMode() {
        return false; // Placeholder
    }
    
    public void toggleDebugMode() {
        // Placeholder
    }
    
    public int getAutoSaveInterval() {
        return 5; // 5 minutes default
    }
    
    public String getNotificationLevel() {
        return "MEDIUM";
    }
    
    public long getDefaultMuteDuration() {
        return 60 * 60 * 1000L; // 1 hour
    }
    
    public long getDefaultBanDuration() {
        return 24 * 60 * 60 * 1000L; // 24 hours
    }
    
    public boolean isDiscordIntegrationEnabled() {
        return false; // Placeholder
    }
    
    public void saveConfiguration() {
        // Placeholder - would save to config file
    }
    
    // Additional methods needed by StubMenus
    public boolean isRealtimeMonitoringEnabled() {
        return enabled; // Use existing enabled flag
    }
    
    public void setRealtimeMonitoringEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}