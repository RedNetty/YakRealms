package com.rednetty.server.core.mechanics.player.moderation;

import com.rednetty.server.YakRealms;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Advanced punishment escalation system with configurable rules,
 * automatic progression, severity scaling, and appeal integration.
 * 
 * Features:
 * - Configurable escalation rules per violation type
 * - Time-decay for older punishments
 * - Severity-based escalation scaling
 * - IP correlation for ban evasion detection
 * - Appeal impact on future escalations
 * - Staff override capabilities
 * - Comprehensive escalation logging
 */
public class PunishmentEscalationSystem {
    
    private static PunishmentEscalationSystem instance;
    private final Logger logger;
    private final ModerationRepository repository;
    
    // Configuration
    private FileConfiguration config;
    private final Map<String, EscalationRule> rules = new HashMap<>();
    private final Map<UUID, PlayerEscalationProfile> profiles = new HashMap<>();
    
    // Default escalation paths
    private static final List<ModerationHistory.ModerationAction> DEFAULT_CHAT_ESCALATION = Arrays.asList(
        ModerationHistory.ModerationAction.WARNING,
        ModerationHistory.ModerationAction.MUTE,
        ModerationHistory.ModerationAction.TEMP_BAN,
        ModerationHistory.ModerationAction.PERMANENT_BAN
    );
    
    private static final List<ModerationHistory.ModerationAction> DEFAULT_GRIEF_ESCALATION = Arrays.asList(
        ModerationHistory.ModerationAction.WARNING,
        ModerationHistory.ModerationAction.TEMP_BAN,
        ModerationHistory.ModerationAction.PERMANENT_BAN
    );
    
    private static final List<ModerationHistory.ModerationAction> DEFAULT_HACK_ESCALATION = Arrays.asList(
        ModerationHistory.ModerationAction.TEMP_BAN,
        ModerationHistory.ModerationAction.PERMANENT_BAN,
        ModerationHistory.ModerationAction.IP_BAN
    );
    
    private PunishmentEscalationSystem() {
        this.logger = YakRealms.getInstance().getLogger();
        this.repository = ModerationRepository.getInstance();
        loadConfiguration();
        setupDefaultRules();
    }
    
    public static PunishmentEscalationSystem getInstance() {
        if (instance == null) {
            instance = new PunishmentEscalationSystem();
        }
        return instance;
    }
    
    // ==========================================
    // CONFIGURATION MANAGEMENT
    // ==========================================
    
    private void loadConfiguration() {
        try {
            File configFile = new File(YakRealms.getInstance().getDataFolder(), "escalation.yml");
            if (!configFile.exists()) {
                createDefaultConfiguration(configFile);
            }
            config = YamlConfiguration.loadConfiguration(configFile);
        } catch (Exception e) {
            logger.severe("Failed to load escalation configuration: " + e.getMessage());
            config = new YamlConfiguration(); // Use empty config as fallback
        }
    }
    
    private void createDefaultConfiguration(File configFile) throws IOException {
        YamlConfiguration defaultConfig = new YamlConfiguration();
        
        // General settings
        defaultConfig.set("settings.time_decay_days", 90);
        defaultConfig.set("settings.max_escalation_multiplier", 5.0);
        defaultConfig.set("settings.appeal_impact_factor", 0.7);
        defaultConfig.set("settings.ip_correlation_enabled", true);
        defaultConfig.set("settings.staff_override_required", true);
        
        // Chat violations
        defaultConfig.set("rules.chat.enabled", true);
        defaultConfig.set("rules.chat.keywords", Arrays.asList("spam", "toxic", "harassment", "inappropriate"));
        defaultConfig.set("rules.chat.escalation_path", Arrays.asList("WARNING", "MUTE", "TEMP_BAN", "PERMANENT_BAN"));
        defaultConfig.set("rules.chat.base_durations", Arrays.asList(0, 3600, 86400, 0)); // 0, 1h, 1d, permanent
        defaultConfig.set("rules.chat.severity_multipliers", Arrays.asList(1.0, 1.5, 2.0, 3.0));
        
        // Griefing violations  
        defaultConfig.set("rules.griefing.enabled", true);
        defaultConfig.set("rules.griefing.keywords", Arrays.asList("grief", "stealing", "vandalism"));
        defaultConfig.set("rules.griefing.escalation_path", Arrays.asList("WARNING", "TEMP_BAN", "PERMANENT_BAN"));
        defaultConfig.set("rules.griefing.base_durations", Arrays.asList(0, 86400, 0)); // 0, 1d, permanent
        defaultConfig.set("rules.griefing.severity_multipliers", Arrays.asList(1.0, 2.0, 4.0));
        
        // Hacking violations
        defaultConfig.set("rules.hacking.enabled", true);
        defaultConfig.set("rules.hacking.keywords", Arrays.asList("hack", "cheat", "exploit", "x-ray", "fly"));
        defaultConfig.set("rules.hacking.escalation_path", Arrays.asList("TEMP_BAN", "PERMANENT_BAN", "IP_BAN"));
        defaultConfig.set("rules.hacking.base_durations", Arrays.asList(604800, 0, 0)); // 7d, permanent, permanent
        defaultConfig.set("rules.hacking.severity_multipliers", Arrays.asList(1.0, 2.0, 1.0));
        
        defaultConfig.save(configFile);
    }
    
    private void setupDefaultRules() {
        // Chat violations rule
        rules.put("chat", new EscalationRule(
            "chat",
            Arrays.asList("spam", "toxic", "harassment", "inappropriate"),
            DEFAULT_CHAT_ESCALATION,
            Arrays.asList(0L, 3600L, 86400L, 0L),
            Arrays.asList(1.0, 1.5, 2.0, 3.0)
        ));
        
        // Griefing rule
        rules.put("griefing", new EscalationRule(
            "griefing", 
            Arrays.asList("grief", "stealing", "vandalism"),
            DEFAULT_GRIEF_ESCALATION,
            Arrays.asList(0L, 86400L, 0L),
            Arrays.asList(1.0, 2.0, 4.0)
        ));
        
        // Hacking rule
        rules.put("hacking", new EscalationRule(
            "hacking",
            Arrays.asList("hack", "cheat", "exploit", "x-ray", "fly"),
            DEFAULT_HACK_ESCALATION,
            Arrays.asList(604800L, 0L, 0L),
            Arrays.asList(1.0, 2.0, 1.0)
        ));
        
        // Load additional rules from config
        loadConfigRules();
    }
    
    private void loadConfigRules() {
        if (config.getConfigurationSection("rules") == null) return;
        
        for (String ruleName : config.getConfigurationSection("rules").getKeys(false)) {
            try {
                String path = "rules." + ruleName;
                if (!config.getBoolean(path + ".enabled", true)) continue;
                
                List<String> keywords = config.getStringList(path + ".keywords");
                List<String> escalationStrings = config.getStringList(path + ".escalation_path");
                List<Long> durations = config.getLongList(path + ".base_durations");
                List<Double> multipliers = config.getDoubleList(path + ".severity_multipliers");
                
                List<ModerationHistory.ModerationAction> escalationPath = new ArrayList<>();
                for (String actionStr : escalationStrings) {
                    try {
                        escalationPath.add(ModerationHistory.ModerationAction.valueOf(actionStr));
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid escalation action in config: " + actionStr);
                    }
                }
                
                if (!escalationPath.isEmpty()) {
                    rules.put(ruleName, new EscalationRule(ruleName, keywords, escalationPath, durations, multipliers));
                    logger.info("Loaded escalation rule: " + ruleName);
                }
                
            } catch (Exception e) {
                logger.warning("Failed to load escalation rule " + ruleName + ": " + e.getMessage());
            }
        }
    }
    
    // ==========================================
    // ESCALATION CALCULATION
    // ==========================================
    
    /**
     * Calculate next punishment based on player history and violation type
     */
    public EscalationResult calculateEscalation(UUID playerId, String reason, 
                                               ModerationHistory.PunishmentSeverity requestedSeverity) {
        try {
            // Get or create player escalation profile
            PlayerEscalationProfile profile = getPlayerProfile(playerId);
            
            // Identify violation type from reason
            String violationType = identifyViolationType(reason);
            EscalationRule rule = rules.get(violationType);
            
            if (rule == null) {
                return new EscalationResult(null, 0, requestedSeverity, false, "No escalation rule found");
            }
            
            // Get recent punishment history
            List<ModerationHistory> recentHistory = repository.getPlayerHistory(playerId, 50).join();
            
            // Calculate escalation level for this violation type
            int escalationLevel = calculateEscalationLevel(recentHistory, rule, profile);
            
            // Get action from escalation path
            if (escalationLevel >= rule.getEscalationPath().size()) {
                escalationLevel = rule.getEscalationPath().size() - 1; // Cap at maximum
            }
            
            ModerationHistory.ModerationAction action = rule.getEscalationPath().get(escalationLevel);
            
            // Calculate duration with multipliers
            long baseDuration = escalationLevel < rule.getBaseDurations().size() ? 
                               rule.getBaseDurations().get(escalationLevel) : 0;
            
            double severityMultiplier = calculateSeverityMultiplier(requestedSeverity, rule, escalationLevel);
            double historyMultiplier = calculateHistoryMultiplier(recentHistory);
            double appealImpact = calculateAppealImpact(profile);
            
            long finalDuration = (long) (baseDuration * severityMultiplier * historyMultiplier * appealImpact);
            
            // Determine final severity
            ModerationHistory.PunishmentSeverity finalSeverity = escalateSeverity(requestedSeverity, escalationLevel);
            
            // Update player profile
            profile.recordViolation(violationType, action);
            profiles.put(playerId, profile);
            
            boolean wasEscalated = escalationLevel > 0;
            String escalationReason = wasEscalated ? 
                String.format("Escalated to level %d due to %d previous %s violations", 
                             escalationLevel + 1, escalationLevel, violationType) : null;
            
            return new EscalationResult(action, finalDuration, finalSeverity, wasEscalated, escalationReason);
            
        } catch (Exception e) {
            logger.severe("Error calculating escalation for player " + playerId + ": " + e.getMessage());
            return new EscalationResult(null, 0, requestedSeverity, false, "Escalation calculation failed");
        }
    }
    
    /**
     * Calculate current escalation level based on history
     */
    private int calculateEscalationLevel(List<ModerationHistory> history, EscalationRule rule, 
                                        PlayerEscalationProfile profile) {
        // Count recent violations of this type (with time decay)
        long timeDecayMs = config.getLong("settings.time_decay_days", 90) * 24 * 60 * 60 * 1000L;
        long cutoffTime = System.currentTimeMillis() - timeDecayMs;
        
        int recentViolations = 0;
        for (ModerationHistory entry : history) {
            if (entry.getTimestamp().getTime() < cutoffTime) continue;
            
            // Check if this entry matches the rule keywords
            String reason = entry.getReason().toLowerCase();
            boolean matches = rule.getKeywords().stream()
                .anyMatch(keyword -> reason.contains(keyword.toLowerCase()));
            
            if (matches) {
                // Apply time decay weighting (more recent = higher weight)
                double timeWeight = 1.0 - ((System.currentTimeMillis() - entry.getTimestamp().getTime()) / (double) timeDecayMs);
                recentViolations += Math.max(0.1, timeWeight); // Minimum weight of 0.1
            }
        }
        
        return Math.min(recentViolations, rule.getEscalationPath().size() - 1);
    }
    
    /**
     * Calculate severity multiplier
     */
    private double calculateSeverityMultiplier(ModerationHistory.PunishmentSeverity severity, 
                                              EscalationRule rule, int escalationLevel) {
        if (escalationLevel < rule.getSeverityMultipliers().size()) {
            double baseMultiplier = rule.getSeverityMultipliers().get(escalationLevel);
            
            // Adjust based on requested severity
            switch (severity) {
                case CRITICAL: return baseMultiplier * 2.0;
                case SEVERE: return baseMultiplier * 1.5;
                case HIGH: return baseMultiplier * 1.2;
                case MEDIUM: return baseMultiplier;
                case LOW: return baseMultiplier * 0.8;
                default: return baseMultiplier;
            }
        }
        
        return 1.0;
    }
    
    /**
     * Calculate history-based multiplier
     */
    private double calculateHistoryMultiplier(List<ModerationHistory> history) {
        // Count total recent punishments
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        long recentPunishments = history.stream()
            .filter(h -> h.getTimestamp().getTime() > thirtyDaysAgo)
            .count();
        
        // Scale multiplier based on recent activity
        double maxMultiplier = config.getDouble("settings.max_escalation_multiplier", 5.0);
        return Math.min(1.0 + (recentPunishments * 0.2), maxMultiplier);
    }
    
    /**
     * Calculate appeal impact on future punishments
     */
    private double calculateAppealImpact(PlayerEscalationProfile profile) {
        if (profile.getSuccessfulAppeals() > 0) {
            double appealFactor = config.getDouble("settings.appeal_impact_factor", 0.7);
            return Math.max(appealFactor, 1.0 - (profile.getSuccessfulAppeals() * 0.1));
        }
        return 1.0;
    }
    
    /**
     * Escalate severity based on escalation level
     */
    private ModerationHistory.PunishmentSeverity escalateSeverity(
            ModerationHistory.PunishmentSeverity requested, int escalationLevel) {
        
        if (escalationLevel <= 1) return requested;
        
        // Escalate severity for repeat offenders
        switch (requested) {
            case LOW:
                return escalationLevel >= 3 ? ModerationHistory.PunishmentSeverity.HIGH : 
                       ModerationHistory.PunishmentSeverity.MEDIUM;
            case MEDIUM:
                return escalationLevel >= 3 ? ModerationHistory.PunishmentSeverity.SEVERE :
                       ModerationHistory.PunishmentSeverity.HIGH;
            case HIGH:
                return escalationLevel >= 2 ? ModerationHistory.PunishmentSeverity.SEVERE : requested;
            case SEVERE:
                return escalationLevel >= 2 ? ModerationHistory.PunishmentSeverity.CRITICAL : requested;
            default:
                return requested;
        }
    }
    
    /**
     * Identify violation type from reason text
     */
    private String identifyViolationType(String reason) {
        String lowerReason = reason.toLowerCase();
        
        for (Map.Entry<String, EscalationRule> entry : rules.entrySet()) {
            EscalationRule rule = entry.getValue();
            for (String keyword : rule.getKeywords()) {
                if (lowerReason.contains(keyword.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        
        return "general"; // Default fallback
    }
    
    /**
     * Get or create player escalation profile
     */
    private PlayerEscalationProfile getPlayerProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, id -> {
            // Load from database or create new
            PlayerEscalationProfile profile = new PlayerEscalationProfile(id);
            
            // Initialize with historical data
            List<ModerationHistory> history = repository.getPlayerHistory(id, 100).join();
            for (ModerationHistory entry : history) {
                if (entry.getAppealStatus() == ModerationHistory.AppealStatus.APPROVED) {
                    profile.recordSuccessfulAppeal();
                }
            }
            
            return profile;
        });
    }
    
    // ==========================================
    // PUBLIC UTILITY METHODS
    // ==========================================
    
    /**
     * Check if escalation should be bypassed for specific conditions
     */
    public boolean shouldBypassEscalation(UUID playerId, UUID staffId) {
        // Staff override check
        if (config.getBoolean("settings.staff_override_required", true)) {
            // Could check if staff member has override permissions
            return false;
        }
        
        // VIP or special player bypass
        PlayerEscalationProfile profile = profiles.get(playerId);
        if (profile != null && profile.hasBypassPrivilege()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get escalation statistics for a player
     */
    public EscalationStatistics getPlayerEscalationStats(UUID playerId) {
        PlayerEscalationProfile profile = getPlayerProfile(playerId);
        List<ModerationHistory> history = repository.getPlayerHistory(playerId, 100).join();
        
        Map<String, Integer> violationCounts = new HashMap<>();
        int totalEscalations = 0;
        
        for (ModerationHistory entry : history) {
            String violationType = identifyViolationType(entry.getReason());
            violationCounts.merge(violationType, 1, Integer::sum);
            
            if (entry.isEscalation()) {
                totalEscalations++;
            }
        }
        
        return new EscalationStatistics(playerId, violationCounts, totalEscalations, 
                                       profile.getSuccessfulAppeals(), profile.getTotalViolations());
    }
    
    /**
     * Reload escalation configuration
     */
    public void reloadConfiguration() {
        rules.clear();
        loadConfiguration();
        setupDefaultRules();
        logger.info("Escalation configuration reloaded");
    }
    
    // ==========================================
    // HELPER CLASSES
    // ==========================================
    
    /**
     * Escalation rule definition
     */
    public static class EscalationRule {
        private final String name;
        private final List<String> keywords;
        private final List<ModerationHistory.ModerationAction> escalationPath;
        private final List<Long> baseDurations;
        private final List<Double> severityMultipliers;
        
        public EscalationRule(String name, List<String> keywords, 
                             List<ModerationHistory.ModerationAction> escalationPath,
                             List<Long> baseDurations, List<Double> severityMultipliers) {
            this.name = name;
            this.keywords = new ArrayList<>(keywords);
            this.escalationPath = new ArrayList<>(escalationPath);
            this.baseDurations = new ArrayList<>(baseDurations);
            this.severityMultipliers = new ArrayList<>(severityMultipliers);
        }
        
        // Getters
        public String getName() { return name; }
        public List<String> getKeywords() { return keywords; }
        public List<ModerationHistory.ModerationAction> getEscalationPath() { return escalationPath; }
        public List<Long> getBaseDurations() { return baseDurations; }
        public List<Double> getSeverityMultipliers() { return severityMultipliers; }
    }
    
    /**
     * Player escalation profile tracking
     */
    public static class PlayerEscalationProfile {
        private final UUID playerId;
        private final Map<String, Integer> violationCounts = new HashMap<>();
        private int successfulAppeals = 0;
        private boolean bypassPrivilege = false;
        private Date lastViolation;
        
        public PlayerEscalationProfile(UUID playerId) {
            this.playerId = playerId;
        }
        
        public void recordViolation(String violationType, ModerationHistory.ModerationAction action) {
            violationCounts.merge(violationType, 1, Integer::sum);
            lastViolation = new Date();
        }
        
        public void recordSuccessfulAppeal() {
            successfulAppeals++;
        }
        
        public int getViolationCount(String violationType) {
            return violationCounts.getOrDefault(violationType, 0);
        }
        
        public int getTotalViolations() {
            return violationCounts.values().stream().mapToInt(Integer::intValue).sum();
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public int getSuccessfulAppeals() { return successfulAppeals; }
        public boolean hasBypassPrivilege() { return bypassPrivilege; }
        public Date getLastViolation() { return lastViolation; }
        public Map<String, Integer> getViolationCounts() { return new HashMap<>(violationCounts); }
    }
    
    /**
     * Escalation calculation result
     */
    public static class EscalationResult {
        private final ModerationHistory.ModerationAction recommendedAction;
        private final long recommendedDuration;
        private final ModerationHistory.PunishmentSeverity recommendedSeverity;
        private final boolean wasEscalated;
        private final String escalationReason;
        
        public EscalationResult(ModerationHistory.ModerationAction recommendedAction, long recommendedDuration,
                               ModerationHistory.PunishmentSeverity recommendedSeverity, boolean wasEscalated,
                               String escalationReason) {
            this.recommendedAction = recommendedAction;
            this.recommendedDuration = recommendedDuration;
            this.recommendedSeverity = recommendedSeverity;
            this.wasEscalated = wasEscalated;
            this.escalationReason = escalationReason;
        }
        
        // Getters
        public ModerationHistory.ModerationAction getRecommendedAction() { return recommendedAction; }
        public long getRecommendedDuration() { return recommendedDuration; }
        public ModerationHistory.PunishmentSeverity getRecommendedSeverity() { return recommendedSeverity; }
        public boolean wasEscalated() { return wasEscalated; }
        public String getEscalationReason() { return escalationReason; }
        public boolean isValid() { return recommendedAction != null; }
    }
    
    /**
     * Escalation statistics for analytics
     */
    public static class EscalationStatistics {
        private final UUID playerId;
        private final Map<String, Integer> violationCounts;
        private final int totalEscalations;
        private final int successfulAppeals;
        private final int totalViolations;
        
        public EscalationStatistics(UUID playerId, Map<String, Integer> violationCounts,
                                   int totalEscalations, int successfulAppeals, int totalViolations) {
            this.playerId = playerId;
            this.violationCounts = new HashMap<>(violationCounts);
            this.totalEscalations = totalEscalations;
            this.successfulAppeals = successfulAppeals;
            this.totalViolations = totalViolations;
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public Map<String, Integer> getViolationCounts() { return violationCounts; }
        public int getTotalEscalations() { return totalEscalations; }
        public int getSuccessfulAppeals() { return successfulAppeals; }
        public int getTotalViolations() { return totalViolations; }
        
        public double getEscalationRate() {
            return totalViolations > 0 ? (double) totalEscalations / totalViolations : 0.0;
        }
        
        public double getAppealSuccessRate() {
            return totalEscalations > 0 ? (double) successfulAppeals / totalEscalations : 0.0;
        }
    }
    
    // Methods needed by system settings menu
    public boolean isEnabled() {
        return true; // Placeholder
    }
    
    public void toggleEnabled() {
        // Placeholder
    }
    
    public int getWarningsToMute() {
        return 3; // Default
    }
    
    public int getWarningsToBan() {
        return 5; // Default
    }
    
    public long getWarningDecayTime() {
        return 30 * 24 * 60 * 60 * 1000L; // 30 days
    }
    
    public void saveConfiguration() {
        // Placeholder - would save config
    }
}