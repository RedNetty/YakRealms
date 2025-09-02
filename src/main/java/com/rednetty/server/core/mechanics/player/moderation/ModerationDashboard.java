package com.rednetty.server.core.mechanics.player.moderation;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.messaging.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Real-time moderation dashboard providing comprehensive monitoring,
 * analytics, and alerting for staff members.
 * 
 * Features:
 * - Real-time punishment tracking
 * - Staff activity monitoring
 * - Automated alert system
 * - Performance analytics
 * - Trend analysis
 * - Risk assessment
 * - Interactive notifications
 */
public class ModerationDashboard {
    
    private static ModerationDashboard instance;
    private final Logger logger;
    private final ModerationRepository repository;
    private final PunishmentEscalationSystem escalationSystem;
    
    // Real-time tracking
    private final Map<UUID, DashboardSession> activeSessions = new ConcurrentHashMap<>();
    private final List<ModerationEvent> recentEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, StaffPerformance> staffMetrics = new ConcurrentHashMap<>();
    private final Map<UUID, RiskAssessment> playerRiskCache = new ConcurrentHashMap<>();
    
    // Statistics tracking
    private final AtomicInteger totalPunishmentsToday = new AtomicInteger(0);
    private final AtomicInteger escalationsToday = new AtomicInteger(0);
    private final AtomicInteger appealsToday = new AtomicInteger(0);
    private final AtomicInteger activePunishments = new AtomicInteger(0);
    
    // Update tasks
    private BukkitTask dashboardUpdateTask;
    private BukkitTask alertTask;
    private BukkitTask cleanupTask;
    
    // Configuration
    private static final int MAX_RECENT_EVENTS = 100;
    private static final int DASHBOARD_UPDATE_INTERVAL = 200; // 10 seconds
    private static final int ALERT_CHECK_INTERVAL = 600; // 30 seconds
    private static final int CLEANUP_INTERVAL = 12000; // 10 minutes
    
    private ModerationDashboard() {
        this.logger = YakRealms.getInstance().getLogger();
        this.repository = ModerationRepository.getInstance();
        this.escalationSystem = PunishmentEscalationSystem.getInstance();
    }
    
    public static ModerationDashboard getInstance() {
        if (instance == null) {
            instance = new ModerationDashboard();
        }
        return instance;
    }
    
    // ==========================================
    // LIFECYCLE MANAGEMENT
    // ==========================================
    
    /**
     * Initialize the dashboard system
     */
    public void initialize() {
        logger.info("Initializing moderation dashboard...");
        
        // Load initial statistics
        loadInitialStatistics();
        
        // Start update tasks
        startUpdateTasks();
        
        logger.info("Moderation dashboard initialized successfully");
    }
    
    /**
     * Shutdown the dashboard system
     */
    public void shutdown() {
        logger.info("Shutting down moderation dashboard...");
        
        // Cancel tasks
        if (dashboardUpdateTask != null) dashboardUpdateTask.cancel();
        if (alertTask != null) alertTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        
        // Clear caches
        activeSessions.clear();
        recentEvents.clear();
        staffMetrics.clear();
        playerRiskCache.clear();
        
        logger.info("Moderation dashboard shutdown complete");
    }
    
    private void loadInitialStatistics() {
        // Load today's statistics
        Date startOfDay = getStartOfDay();
        
        repository.getModerationStats(startOfDay).thenAccept(stats -> {
            totalPunishmentsToday.set(stats.getTotalPunishments());
            escalationsToday.set(stats.getTotalPunishments() - stats.getWarningsIssued()); // Rough escalation count
            appealsToday.set(stats.getAppealsSubmitted());
            activePunishments.set(stats.getActivePunishments());
        });
    }
    
    private void startUpdateTasks() {
        // Dashboard update task
        dashboardUpdateTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            updateDashboardSessions();
        }, DASHBOARD_UPDATE_INTERVAL, DASHBOARD_UPDATE_INTERVAL);
        
        // Alert checking task
        alertTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            checkForAlerts();
        }, ALERT_CHECK_INTERVAL, ALERT_CHECK_INTERVAL);
        
        // Cleanup task
        cleanupTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            performCleanup();
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }
    
    // ==========================================
    // SESSION MANAGEMENT
    // ==========================================
    
    /**
     * Start a dashboard session for a staff member
     */
    public void startSession(Player staff) {
        if (!ModerationMechanics.isStaff(staff)) {
            MessageUtils.send(staff, "<red>Dashboard access denied - insufficient permissions.");
            return;
        }
        
        UUID staffId = staff.getUniqueId();
        DashboardSession session = new DashboardSession(staffId, staff.getName());
        activeSessions.put(staffId, session);
        
        MessageUtils.send(staff, "<green>Dashboard session started. Use <white>/moddash</white> for commands.");
        
        // Send initial dashboard view
        sendDashboardSummary(staff);
    }
    
    /**
     * Stop a dashboard session
     */
    public void stopSession(Player staff) {
        UUID staffId = staff.getUniqueId();
        DashboardSession session = activeSessions.remove(staffId);
        
        if (session != null) {
            MessageUtils.send(staff, "<yellow>Dashboard session ended. " +
                             "Session duration: " + formatDuration(session.getDuration()));
        }
    }
    
    /**
     * Check if a player has an active dashboard session
     */
    public boolean hasActiveSession(Player staff) {
        return activeSessions.containsKey(staff.getUniqueId());
    }
    
    // ==========================================
    // EVENT TRACKING
    // ==========================================
    
    /**
     * Record a moderation event
     */
    public void recordEvent(ModerationHistory entry, boolean wasEscalated) {
        ModerationEvent event = new ModerationEvent(
            entry.getAction(),
            entry.getTargetPlayerName(),
            entry.getStaffName(),
            entry.getSeverity(),
            wasEscalated,
            new Date()
        );
        
        synchronized (recentEvents) {
            recentEvents.add(0, event); // Add to beginning
            
            // Keep only recent events
            while (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.remove(recentEvents.size() - 1);
            }
        }
        
        // Update statistics
        totalPunishmentsToday.incrementAndGet();
        if (wasEscalated) {
            escalationsToday.incrementAndGet();
        }
        
        // Update staff metrics
        updateStaffMetrics(entry.getStaffId(), entry.getStaffName(), entry.getAction());
        
        // Notify active dashboard sessions
        notifyActiveSessions(event);
        
        // Update player risk assessment
        updatePlayerRisk(entry.getTargetPlayerId(), entry.getTargetPlayerName(), entry);
    }
    
    /**
     * Record an appeal event
     */
    public void recordAppeal(UUID playerId, String playerName, ModerationHistory.AppealStatus status) {
        if (status == ModerationHistory.AppealStatus.PENDING) {
            appealsToday.incrementAndGet();
        }
        
        ModerationEvent event = new ModerationEvent(
            ModerationHistory.ModerationAction.NOTE, // Using NOTE for appeals
            playerName,
            "System",
            ModerationHistory.PunishmentSeverity.LOW,
            false,
            new Date()
        );
        event.setAppealEvent(true);
        event.setAppealStatus(status);
        
        synchronized (recentEvents) {
            recentEvents.add(0, event);
        }
        
        // Notify sessions about appeal activity
        notifyActiveSessions(event);
    }
    
    private void updateStaffMetrics(UUID staffId, String staffName, ModerationHistory.ModerationAction action) {
        if (staffId == null) return;
        
        StaffPerformance metrics = staffMetrics.computeIfAbsent(staffId, 
            id -> new StaffPerformance(id, staffName));
        
        metrics.recordAction(action);
    }
    
    private void updatePlayerRisk(UUID playerId, String playerName, ModerationHistory entry) {
        if (playerId == null) return;
        
        RiskAssessment risk = playerRiskCache.computeIfAbsent(playerId,
            id -> new RiskAssessment(id, playerName));
        
        risk.updateFromEntry(entry);
    }
    
    // ==========================================
    // DASHBOARD VIEWS
    // ==========================================
    
    /**
     * Enhanced dashboard summary with real-time data integration
     */
    public void sendDashboardSummary(CommandSender sender) {
        sendMessage(sender, "<gold>========================================");
        sendMessage(sender, "<yellow>        MODERATION DASHBOARD");
        sendMessage(sender, "<gold>========================================");
        
        // Real-time YakPlayer integration check
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int yakPlayerCount = 0;
        int moderationDataLoaded = 0;
        
        try {
            YakPlayerManager playerManager = YakPlayerManager.getInstance();
            for (Player player : Bukkit.getOnlinePlayers()) {
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer != null) {
                    yakPlayerCount++;
                    // Check if moderation data is properly loaded
                    if (yakPlayer.getRank() != null && !yakPlayer.getRank().isEmpty()) {
                        moderationDataLoaded++;
                    }
                }
            }
        } catch (Exception e) {
            sendMessage(sender, "<red>Warning: YakPlayer integration issues detected");
        }
        
        // System Health Check
        sendMessage(sender, "<white>System Health:");
        sendMessage(sender, "<gray>  Players Online: <green>" + onlinePlayers);
        sendMessage(sender, "<gray>  YakPlayer Loaded: <aqua>" + yakPlayerCount + "<gray>/" + onlinePlayers);
        sendMessage(sender, "<gray>  Moderation Data: " + 
                          (moderationDataLoaded == yakPlayerCount ? "<green>" : "<yellow>") + 
                          moderationDataLoaded + "<gray>/" + yakPlayerCount);
        
        // Current statistics with enhanced metrics
        sendMessage(sender, "<white>Today's Statistics:");
        sendMessage(sender, "<gray>  Punishments: <yellow>" + totalPunishmentsToday.get());
        sendMessage(sender, "<gray>  Escalations: <red>" + escalationsToday.get() + 
                          "<gray> (" + getEscalationRate() + "%)");
        sendMessage(sender, "<gray>  Appeals: <blue>" + appealsToday.get());
        sendMessage(sender, "<gray>  Active: <gold>" + activePunishments.get());
        
        // Staff activity with performance metrics
        sendMessage(sender, "<white>Staff Activity:");
        List<StaffPerformance> topStaff = getTopActiveStaff(3);
        if (topStaff.isEmpty()) {
            sendMessage(sender, "<gray>  No staff activity today");
        } else {
            for (int i = 0; i < topStaff.size(); i++) {
                StaffPerformance staff = topStaff.get(i);
                sendMessage(sender, "<gray>  " + (i + 1) + ". <aqua>" + staff.getStaffName() + "<gray>" + 
                                 " - " + staff.getTodayActions() + " actions" +
                                 " (Effectiveness: " + String.format("%.1f", staff.getEffectivenessScore()) + "%)");
            }
        }
        
        // Recent events (last 5)
        sendMessage(sender, "<white>Recent Events:");
        List<ModerationEvent> recent = getRecentEvents(5);
        for (int i = 0; i < recent.size(); i++) {
            ModerationEvent event = recent.get(i);
            String escalated = event.isEscalated() ? ChatColor.RED + " [ESC]" : "";
            sender.sendMessage(ChatColor.GRAY + "  " + (i + 1) + ". " +
                             getActionColor(event.getAction()) + event.getAction().name() + 
                             ChatColor.WHITE + " " + event.getTargetPlayer() + ChatColor.GRAY +
                             " by " + ChatColor.AQUA + event.getStaffName() + escalated);
        }
        
        // High-risk players
        List<RiskAssessment> highRisk = getHighRiskPlayers(3);
        if (!highRisk.isEmpty()) {
            sender.sendMessage(ChatColor.WHITE + "High-Risk Players:");
            for (int i = 0; i < highRisk.size(); i++) {
                RiskAssessment risk = highRisk.get(i);
                sender.sendMessage(ChatColor.GRAY + "  " + (i + 1) + ". " +
                                 ChatColor.RED + risk.getPlayerName() + ChatColor.GRAY +
                                 " - Risk: " + risk.getRiskLevel() + "%" +
                                 " (" + risk.getRecentViolations() + " recent)");
            }
        }
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/moddash help" + 
                          ChatColor.YELLOW + " for available commands");
    }
    
    /**
     * Send live feed to staff member
     */
    public void sendLiveFeed(CommandSender sender, int count) {
        sender.sendMessage(ChatColor.GOLD + "======== Live Moderation Feed ========");
        
        List<ModerationEvent> events = getRecentEvents(count);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        
        for (ModerationEvent event : events) {
            String time = timeFormat.format(event.getTimestamp());
            String escalated = event.isEscalated() ? ChatColor.RED + " ⚠" : "";
            String severity = getSeverityIndicator(event.getSeverity());
            
            if (event.isAppealEvent()) {
                sender.sendMessage(ChatColor.GRAY + "[" + time + "] " +
                                 ChatColor.BLUE + "APPEAL " + 
                                 ChatColor.WHITE + event.getTargetPlayer() + ChatColor.GRAY +
                                 " - " + event.getAppealStatus().name());
            } else {
                sender.sendMessage(ChatColor.GRAY + "[" + time + "] " +
                                 getActionColor(event.getAction()) + event.getAction().name() + 
                                 ChatColor.WHITE + " " + event.getTargetPlayer() + ChatColor.GRAY +
                                 " by " + ChatColor.AQUA + event.getStaffName() + 
                                 severity + escalated);
            }
        }
        
        sender.sendMessage(ChatColor.GOLD + "=====================================");
    }
    
    /**
     * Send staff performance overview
     */
    public void sendStaffOverview(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "======== Staff Performance ========");
        
        List<StaffPerformance> allStaff = new ArrayList<>(staffMetrics.values());
        allStaff.sort((a, b) -> Integer.compare(b.getTodayActions(), a.getTodayActions()));
        
        for (int i = 0; i < Math.min(allStaff.size(), 10); i++) {
            StaffPerformance staff = allStaff.get(i);
            double efficiency = staff.getEfficiencyScore();
            ChatColor efficiencyColorCode = efficiency >= 0.8 ? ChatColor.GREEN :
                                   efficiency >= 0.6 ? ChatColor.YELLOW : ChatColor.RED;
            
            sender.sendMessage(ChatColor.GRAY + "" + (i + 1) + ". " +
                             ChatColor.AQUA + staff.getStaffName() + ChatColor.GRAY +
                             " - " + staff.getTodayActions() + " actions" +
                             " (Eff: " + efficiencyColorCode + String.format("%.1f", efficiency * 100) + "%" +
                             ChatColor.GRAY + ")");
            
            // Show breakdown
            Map<ModerationHistory.ModerationAction, Integer> breakdown = staff.getActionBreakdown();
            if (!breakdown.isEmpty()) {
                StringBuilder breakdownStr = new StringBuilder("    ");
                breakdown.forEach((action, count) -> {
                    breakdownStr.append(getActionColor(action))
                               .append(action.name(), 0, Math.min(4, action.name().length()))
                               .append(":").append(count).append(" ");
                });
                sender.sendMessage(ChatColor.DARK_GRAY + breakdownStr.toString());
            }
        }
        
        sender.sendMessage(ChatColor.GOLD + "=================================");
    }
    
    /**
     * Send risk assessment overview
     */
    public void sendRiskOverview(CommandSender sender, int count) {
        sender.sendMessage(ChatColor.GOLD + "======== Risk Assessment ========");
        
        List<RiskAssessment> risks = getAllRiskAssessments();
        risks.sort((a, b) -> Integer.compare(b.getRiskLevel(), a.getRiskLevel()));
        
        for (int i = 0; i < Math.min(risks.size(), count); i++) {
            RiskAssessment risk = risks.get(i);
            ChatColor riskColor = getRiskColor(risk.getRiskLevel());
            
            sender.sendMessage(ChatColor.GRAY + "" + (i + 1) + ". " +
                             riskColor + risk.getPlayerName() + ChatColor.GRAY +
                             " - Risk: " + riskColor + risk.getRiskLevel() + "%" + ChatColor.GRAY +
                             " (" + risk.getRecentViolations() + " recent, " +
                             risk.getTotalPunishments() + " total)");
            
            if (risk.hasActiveWarnings()) {
                sender.sendMessage(ChatColor.YELLOW + "    Active warnings detected");
            }
            if (risk.isEscalationCandidate()) {
                sender.sendMessage(ChatColor.RED + "    Escalation candidate");
            }
        }
        
        sender.sendMessage(ChatColor.GOLD + "===============================");
    }
    
    // ==========================================
    // ALERT SYSTEM
    // ==========================================
    
    private void checkForAlerts() {
        // Check for unusual activity spikes
        checkActivitySpikes();
        
        // Check for staff performance issues
        checkStaffPerformance();
        
        // Check for high-risk players
        checkHighRiskPlayers();
        
        // Check for appeal backlog
        checkAppealBacklog();
    }
    
    private void checkActivitySpikes() {
        int recentActivity = getRecentActivityCount(30); // Last 30 minutes
        int normalActivity = getAverageActivity();
        
        if (recentActivity > normalActivity * 2.5) { // 250% of normal
            String alert = ChatColor.RED + "[ALERT] " + ChatColor.YELLOW +
                          "High moderation activity detected: " + recentActivity + 
                          " punishments in last 30 minutes (normal: " + normalActivity + ")";
            
            broadcastAlert(alert, "yakrealms.staff.alerts.activity");
        }
    }
    
    private void checkStaffPerformance() {
        for (StaffPerformance staff : staffMetrics.values()) {
            if (staff.getTodayActions() > 0) {
                double efficiency = staff.getEfficiencyScore();
                if (efficiency < 0.3) { // Very low efficiency
                    String alert = ChatColor.YELLOW + "[NOTICE] " +
                                  "Staff member " + staff.getStaffName() + 
                                  " has low efficiency score: " + String.format("%.1f", efficiency * 100) + "%";
                    
                    broadcastAlert(alert, "yakrealms.staff.alerts.performance");
                }
            }
        }
    }
    
    private void checkHighRiskPlayers() {
        List<RiskAssessment> highRisk = playerRiskCache.values().stream()
                .filter(risk -> risk.getRiskLevel() >= 80)
                .collect(Collectors.toList());
        
        if (highRisk.size() > 5) { // More than 5 high-risk players
            String alert = ChatColor.RED + "[ALERT] " + ChatColor.YELLOW +
                          highRisk.size() + " players are currently high-risk (80%+)";
            
            broadcastAlert(alert, "yakrealms.staff.alerts.risk");
        }
    }
    
    private void checkAppealBacklog() {
        // This would need integration with appeal system
        // For now, just a placeholder
    }
    
    private void broadcastAlert(String message, String permission) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(permission)) {
                staff.sendMessage(message);
            }
        }
        
        logger.warning("Moderation Alert: " + ChatColor.stripColor(message));
    }
    
    // ==========================================
    // UPDATE METHODS
    // ==========================================
    
    private void updateDashboardSessions() {
        for (DashboardSession session : activeSessions.values()) {
            Player staff = Bukkit.getPlayer(session.getStaffId());
            if (staff != null && staff.isOnline()) {
                session.updateLastActivity();
                
                // Send periodic updates if requested
                if (session.isAutoUpdate() && session.shouldUpdate()) {
                    sendQuickUpdate(staff);
                    session.updateLastUpdate();
                }
            } else {
                // Staff went offline, remove session
                activeSessions.remove(session.getStaffId());
            }
        }
    }
    
    private void sendQuickUpdate(Player staff) {
        staff.sendMessage(ChatColor.GRAY + "━━━ Quick Update ━━━");
        staff.sendMessage(ChatColor.YELLOW + "Punishments: " + totalPunishmentsToday.get() + 
                         " | Escalations: " + escalationsToday.get() + 
                         " | Active: " + activePunishments.get());
        
        // Show most recent event
        if (!recentEvents.isEmpty()) {
            ModerationEvent latest = recentEvents.get(0);
            String escalated = latest.isEscalated() ? ChatColor.RED + " [ESC]" : "";
            staff.sendMessage(ChatColor.GRAY + "Latest: " + 
                             getActionColor(latest.getAction()) + latest.getAction().name() + 
                             ChatColor.WHITE + " " + latest.getTargetPlayer() + escalated);
        }
    }
    
    private void performCleanup() {
        // Clean old events
        synchronized (recentEvents) {
            long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours
            recentEvents.removeIf(event -> event.getTimestamp().getTime() < cutoff);
        }
        
        // Clean old risk assessments
        long riskCutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000); // 7 days
        playerRiskCache.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().getTime() < riskCutoff);
        
        // Reset daily counters at midnight
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) < 10) {
            totalPunishmentsToday.set(0);
            escalationsToday.set(0);
            appealsToday.set(0);
        }
    }
    
    private void notifyActiveSessions(ModerationEvent event) {
        String notification = ChatColor.GRAY + "[LIVE] " +
                             getActionColor(event.getAction()) + event.getAction().name() + 
                             ChatColor.WHITE + " " + event.getTargetPlayer() + 
                             (event.isEscalated() ? ChatColor.RED + " ⚠" : "");
        
        for (DashboardSession session : activeSessions.values()) {
            if (session.isLiveFeed()) {
                Player staff = Bukkit.getPlayer(session.getStaffId());
                if (staff != null && staff.isOnline()) {
                    staff.sendMessage(notification);
                }
            }
        }
    }
    
    // ==========================================
    // UTILITY METHODS
    // ==========================================
    
    public List<ModerationEvent> getRecentEvents(int count) {
        synchronized (recentEvents) {
            return recentEvents.stream()
                    .limit(count)
                    .collect(Collectors.toList());
        }
    }
    
    private List<StaffPerformance> getTopActiveStaff(int count) {
        return staffMetrics.values().stream()
                .filter(staff -> staff.getTodayActions() > 0)
                .sorted((a, b) -> Integer.compare(b.getTodayActions(), a.getTodayActions()))
                .limit(count)
                .collect(Collectors.toList());
    }
    
    private List<RiskAssessment> getHighRiskPlayers(int count) {
        return playerRiskCache.values().stream()
                .filter(risk -> risk.getRiskLevel() >= 60)
                .sorted((a, b) -> Integer.compare(b.getRiskLevel(), a.getRiskLevel()))
                .limit(count)
                .collect(Collectors.toList());
    }
    
    private List<RiskAssessment> getAllRiskAssessments() {
        return new ArrayList<>(playerRiskCache.values());
    }
    
    private int getEscalationRate() {
        int total = totalPunishmentsToday.get();
        return total > 0 ? (escalationsToday.get() * 100) / total : 0;
    }
    
    private int getRecentActivityCount(int minutes) {
        long cutoff = System.currentTimeMillis() - (minutes * 60 * 1000L);
        synchronized (recentEvents) {
            return (int) recentEvents.stream()
                    .filter(event -> event.getTimestamp().getTime() > cutoff)
                    .count();
        }
    }
    
    private int getAverageActivity() {
        // Simple average based on total today divided by hours elapsed
        Calendar cal = Calendar.getInstance();
        int hoursElapsed = Math.max(1, cal.get(Calendar.HOUR_OF_DAY));
        return totalPunishmentsToday.get() / hoursElapsed;
    }
    
    private ChatColor getActionColor(ModerationHistory.ModerationAction action) {
        switch (action) {
            case WARNING: return ChatColor.YELLOW;
            case MUTE: return ChatColor.GOLD;
            case TEMP_BAN: return ChatColor.RED;
            case PERMANENT_BAN:
            case IP_BAN: return ChatColor.DARK_RED;
            case KICK: return ChatColor.AQUA;
            case NOTE: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }
    
    private String getSeverityIndicator(ModerationHistory.PunishmentSeverity severity) {
        switch (severity) {
            case CRITICAL: return ChatColor.DARK_RED + " ●●●";
            case SEVERE: return ChatColor.RED + " ●●";
            case HIGH: return ChatColor.GOLD + " ●";
            case MEDIUM: return ChatColor.YELLOW + " ○";
            case LOW: return "";
            default: return "";
        }
    }
    
    private ChatColor getRiskColor(int riskLevel) {
        if (riskLevel >= 80) return ChatColor.DARK_RED;
        if (riskLevel >= 60) return ChatColor.RED;
        if (riskLevel >= 40) return ChatColor.GOLD;
        if (riskLevel >= 20) return ChatColor.YELLOW;
        return ChatColor.GREEN;
    }
    
    /**
     * Helper method to send messages to both Player and CommandSender
     */
    private void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player) {
            MessageUtils.send((Player) sender, message);
        } else {
            sender.sendMessage(MessageUtils.toLegacy(MessageUtils.parse(message)));
        }
    }
    
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
    
    private Date getStartOfDay() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
    
    // ==========================================
    // HELPER CLASSES
    // ==========================================
    
    public static class DashboardSession {
        private final UUID staffId;
        private final String staffName;
        private final Date startTime;
        private Date lastActivity;
        private Date lastUpdate;
        private boolean autoUpdate = false;
        private boolean liveFeed = false;
        private static final long AUTO_UPDATE_INTERVAL = 60000; // 1 minute
        
        public DashboardSession(UUID staffId, String staffName) {
            this.staffId = staffId;
            this.staffName = staffName;
            this.startTime = new Date();
            this.lastActivity = new Date();
            this.lastUpdate = new Date();
        }
        
        public void updateLastActivity() { this.lastActivity = new Date(); }
        public void updateLastUpdate() { this.lastUpdate = new Date(); }
        
        public boolean shouldUpdate() {
            return System.currentTimeMillis() - lastUpdate.getTime() > AUTO_UPDATE_INTERVAL;
        }
        
        public long getDuration() {
            return System.currentTimeMillis() - startTime.getTime();
        }
        
        // Getters and setters
        public UUID getStaffId() { return staffId; }
        public String getStaffName() { return staffName; }
        public Date getStartTime() { return startTime; }
        public boolean isAutoUpdate() { return autoUpdate; }
        public boolean isLiveFeed() { return liveFeed; }
        public void setAutoUpdate(boolean autoUpdate) { this.autoUpdate = autoUpdate; }
        public void setLiveFeed(boolean liveFeed) { this.liveFeed = liveFeed; }
    }
    
    public static class ModerationEvent {
        private final ModerationHistory.ModerationAction action;
        private final String targetPlayer;
        private final String staffName;
        private final ModerationHistory.PunishmentSeverity severity;
        private final boolean escalated;
        private final Date timestamp;
        private boolean appealEvent = false;
        private ModerationHistory.AppealStatus appealStatus;
        
        public ModerationEvent(ModerationHistory.ModerationAction action, String targetPlayer, 
                              String staffName, ModerationHistory.PunishmentSeverity severity,
                              boolean escalated, Date timestamp) {
            this.action = action;
            this.targetPlayer = targetPlayer;
            this.staffName = staffName;
            this.severity = severity;
            this.escalated = escalated;
            this.timestamp = timestamp;
        }
        
        // Getters and setters
        public ModerationHistory.ModerationAction getAction() { return action; }
        public String getTargetPlayer() { return targetPlayer; }
        public String getStaffName() { return staffName; }
        public ModerationHistory.PunishmentSeverity getSeverity() { return severity; }
        public boolean isEscalated() { return escalated; }
        public Date getTimestamp() { return timestamp; }
        public boolean isAppealEvent() { return appealEvent; }
        public ModerationHistory.AppealStatus getAppealStatus() { return appealStatus; }
        public void setAppealEvent(boolean appealEvent) { this.appealEvent = appealEvent; }
        public void setAppealStatus(ModerationHistory.AppealStatus appealStatus) { this.appealStatus = appealStatus; }
    }
    
    public static class StaffPerformance {
        private final UUID staffId;
        private final String staffName;
        private final Map<ModerationHistory.ModerationAction, Integer> actionCounts = new HashMap<>();
        private int todayActions = 0;
        private Date lastActivity;
        
        public StaffPerformance(UUID staffId, String staffName) {
            this.staffId = staffId;
            this.staffName = staffName;
            this.lastActivity = new Date();
        }
        
        public void recordAction(ModerationHistory.ModerationAction action) {
            actionCounts.merge(action, 1, Integer::sum);
            todayActions++;
            lastActivity = new Date();
        }
        
        public double getEfficiencyScore() {
            // Simple efficiency based on action variety and frequency
            int uniqueActions = actionCounts.size();
            int totalActions = todayActions;
            
            if (totalActions == 0) return 0.0;
            
            // Efficiency = (variety factor) * (consistency factor)
            double varietyScore = Math.min(1.0, uniqueActions / 4.0); // Max efficiency with 4+ action types
            double consistencyScore = Math.min(1.0, totalActions / 10.0); // Max efficiency with 10+ actions
            
            return (varietyScore + consistencyScore) / 2.0;
        }
        
        public double getEffectivenessScore() {
            // Calculate effectiveness based on action success rates
            // For now, use efficiency score as a proxy
            return getEfficiencyScore() * 100;
        }
        
        // Getters
        public UUID getStaffId() { return staffId; }
        public String getStaffName() { return staffName; }
        public int getTodayActions() { return todayActions; }
        public Date getLastActivity() { return lastActivity; }
        public Map<ModerationHistory.ModerationAction, Integer> getActionBreakdown() { 
            return new HashMap<>(actionCounts); 
        }
    }
    
    public static class RiskAssessment {
        private final UUID playerId;
        private final String playerName;
        private int riskLevel = 0;
        private int recentViolations = 0;
        private int totalPunishments = 0;
        private Date lastActivity;
        private boolean hasActiveWarnings = false;
        private boolean escalationCandidate = false;
        
        public RiskAssessment(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.lastActivity = new Date();
        }
        
        public void updateFromEntry(ModerationHistory entry) {
            totalPunishments++;
            lastActivity = new Date();
            
            // Check if recent (last 7 days)
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            if (entry.getTimestamp().getTime() > sevenDaysAgo) {
                recentViolations++;
            }
            
            // Update risk factors
            if (entry.getAction() == ModerationHistory.ModerationAction.WARNING && entry.isActive()) {
                hasActiveWarnings = true;
            }
            
            if (entry.isEscalation()) {
                escalationCandidate = true;
            }
            
            // Calculate risk level
            calculateRiskLevel();
        }
        
        private void calculateRiskLevel() {
            int risk = 0;
            
            // Recent violations factor (0-40 points)
            risk += Math.min(recentViolations * 8, 40);
            
            // Total punishments factor (0-30 points)
            risk += Math.min(totalPunishments * 3, 30);
            
            // Active warnings (0-15 points)
            if (hasActiveWarnings) risk += 15;
            
            // Escalation candidate (0-15 points)
            if (escalationCandidate) risk += 15;
            
            this.riskLevel = Math.min(risk, 100);
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public int getRiskLevel() { return riskLevel; }
        public int getRecentViolations() { return recentViolations; }
        public int getTotalPunishments() { return totalPunishments; }
        public Date getLastActivity() { return lastActivity; }
        public boolean hasActiveWarnings() { return hasActiveWarnings; }
        public boolean isEscalationCandidate() { return escalationCandidate; }
    }
    
    // Methods needed by moderation menus
    public ModerationStats getCurrentStats() {
        return ModerationStats.builder()
            .totalPunishments(totalPunishmentsToday.get())
            .activePunishments(activePunishments.get())
            .appealsSubmitted(appealsToday.get())
            .build();
    }
}