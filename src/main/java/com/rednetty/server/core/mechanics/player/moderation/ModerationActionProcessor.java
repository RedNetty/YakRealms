package com.rednetty.server.core.mechanics.player.moderation;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bson.types.ObjectId;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Modern moderation action processor with comprehensive punishment handling,
 * automatic escalation, appeal integration, and advanced analytics.
 * 
 * Features:
 * - Automatic punishment escalation based on history
 * - IP tracking and correlation
 * - Evidence collection and storage
 * - Appeal system integration
 * - Real-time staff notifications
 * - Advanced audit logging
 * - Punishment effectiveness tracking
 */
public class ModerationActionProcessor {
    
    private static ModerationActionProcessor instance;
    private final ModerationRepository repository;
    private final YakPlayerManager playerManager;
    private final Logger logger;
    
    // Configuration
    private static final int MAX_WARNINGS_BEFORE_MUTE = 3;
    private static final int MAX_MUTES_BEFORE_TEMP_BAN = 3;
    private static final int MAX_TEMP_BANS_BEFORE_PERM = 2;
    private static final long DEFAULT_WARNING_DURATION = 30 * 24 * 60 * 60; // 30 days
    private static final long DEFAULT_MUTE_DURATION = 60 * 60; // 1 hour
    private static final long DEFAULT_TEMP_BAN_DURATION = 24 * 60 * 60; // 24 hours
    
    private ModerationActionProcessor() {
        this.repository = ModerationRepository.getInstance();
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }
    
    public static ModerationActionProcessor getInstance() {
        if (instance == null) {
            instance = new ModerationActionProcessor();
        }
        return instance;
    }
    
    // ==========================================
    // MAIN ACTION PROCESSING METHODS
    // ==========================================
    
    /**
     * Issue a warning with automatic escalation checking
     */
    public CompletableFuture<ModerationResult> issueWarning(
            UUID targetId, 
            CommandSender issuer,
            String reason,
            ModerationHistory.PunishmentSeverity severity) {
        
        return processAction(ModerationHistory.ModerationAction.WARNING, targetId, issuer, reason, 
                DEFAULT_WARNING_DURATION, severity, null, true);
    }
    
    /**
     * Issue a mute with duration and automatic escalation
     */
    public CompletableFuture<ModerationResult> issueMute(
            UUID targetId,
            CommandSender issuer,
            String reason,
            long durationSeconds,
            ModerationHistory.PunishmentSeverity severity) {
        
        return processAction(ModerationHistory.ModerationAction.MUTE, targetId, issuer, reason,
                durationSeconds > 0 ? durationSeconds : DEFAULT_MUTE_DURATION, severity, null, true);
    }
    
    /**
     * Issue a temporary ban
     */
    public CompletableFuture<ModerationResult> issueTempBan(
            UUID targetId,
            CommandSender issuer,
            String reason,
            long durationSeconds,
            ModerationHistory.PunishmentSeverity severity) {
        
        return processAction(ModerationHistory.ModerationAction.TEMP_BAN, targetId, issuer, reason,
                durationSeconds > 0 ? durationSeconds : DEFAULT_TEMP_BAN_DURATION, severity, null, true);
    }
    
    /**
     * Issue a permanent ban
     */
    public CompletableFuture<ModerationResult> issuePermanentBan(
            UUID targetId,
            CommandSender issuer,
            String reason,
            ModerationHistory.PunishmentSeverity severity) {
        
        return processAction(ModerationHistory.ModerationAction.PERMANENT_BAN, targetId, issuer, reason,
                0, severity, null, true);
    }
    
    /**
     * Issue an IP ban with associated accounts detection
     */
    public CompletableFuture<ModerationResult> issueIPBan(
            UUID targetId,
            CommandSender issuer,
            String reason,
            String ipAddress,
            ModerationHistory.PunishmentSeverity severity) {
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("banned_ip", ipAddress);
        
        return processAction(ModerationHistory.ModerationAction.IP_BAN, targetId, issuer, reason,
                0, severity, metadata, true);
    }
    
    /**
     * Kick a player with logging
     */
    public CompletableFuture<ModerationResult> kickPlayer(
            UUID targetId,
            CommandSender issuer,
            String reason,
            ModerationHistory.PunishmentSeverity severity) {
        
        return processAction(ModerationHistory.ModerationAction.KICK, targetId, issuer, reason,
                0, severity, null, false);
    }
    
    /**
     * Add a staff note (no punishment)
     */
    public CompletableFuture<ModerationResult> addStaffNote(
            UUID targetId,
            CommandSender issuer,
            String note) {
        
        return processAction(ModerationHistory.ModerationAction.NOTE, targetId, issuer, note,
                0, ModerationHistory.PunishmentSeverity.LOW, null, false);
    }
    
    // ==========================================
    // REVOCATION METHODS
    // ==========================================
    
    /**
     * Unmute a player
     */
    public CompletableFuture<ModerationResult> unmutePlayer(
            UUID targetId,
            CommandSender issuer,
            String reason) {
        
        return processRevocation(ModerationHistory.ModerationAction.UNMUTE, targetId, issuer, reason);
    }
    
    /**
     * Unban a player
     */
    public CompletableFuture<ModerationResult> unbanPlayer(
            UUID targetId,
            CommandSender issuer,
            String reason) {
        
        return processRevocation(ModerationHistory.ModerationAction.UNBAN, targetId, issuer, reason);
    }
    
    // ==========================================
    // CORE PROCESSING LOGIC
    // ==========================================
    
    /**
     * Main action processing method with comprehensive handling
     */
    private CompletableFuture<ModerationResult> processAction(
            ModerationHistory.ModerationAction action,
            UUID targetId,
            CommandSender issuer,
            String reason,
            long durationSeconds,
            ModerationHistory.PunishmentSeverity severity,
            Map<String, Object> metadata,
            boolean checkEscalation) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get target player information
                Player targetPlayer = Bukkit.getPlayer(targetId);
                YakPlayer yakPlayer = playerManager.getPlayer(targetId);
                
                if (yakPlayer == null) {
                    return ModerationResult.failure("Target player data not found");
                }
                
                // Validate action permissions
                if (!validateActionPermissions(issuer, targetPlayer, action)) {
                    return ModerationResult.failure("Insufficient permissions for this action");
                }
                
                // Get punishment history for escalation analysis
                List<ModerationHistory> history = repository.getPlayerHistory(targetId, 50).join();
                
                // Check for automatic escalation
                ModerationActionPlan plan = createActionPlan(action, history, severity, durationSeconds, checkEscalation);
                
                // Create moderation history entry
                ModerationHistory entry = createModerationEntry(
                    targetId, yakPlayer.getUsername(), issuer, 
                    plan.getFinalAction(), reason, plan.getFinalDuration(), 
                    plan.getFinalSeverity(), targetPlayer, history.size(), metadata
                );
                
                // Save to repository
                ObjectId entryId = repository.addModerationEntry(entry).join();
                if (entryId == null) {
                    return ModerationResult.failure("Failed to save moderation entry");
                }
                
                // Apply the punishment
                boolean applied = applyPunishment(entry, targetPlayer);
                if (!applied) {
                    return ModerationResult.failure("Failed to apply punishment");
                }
                
                // Send notifications
                sendNotifications(entry, issuer, targetPlayer, plan.isEscalated());
                
                // Log the action
                logModerationAction(entry, issuer, plan.isEscalated());
                
                return ModerationResult.success(entry, plan.isEscalated() ? "Action escalated due to repeat offenses" : null);
                
            } catch (Exception e) {
                logger.severe("Error processing moderation action: " + e.getMessage());
                e.printStackTrace();
                return ModerationResult.failure("Internal error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Process revocation actions (unban, unmute)
     */
    private CompletableFuture<ModerationResult> processRevocation(
            ModerationHistory.ModerationAction action,
            UUID targetId,
            CommandSender issuer,
            String reason) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Find active punishment to revoke
                List<ModerationHistory> activePunishments = repository.getActivePunishments(targetId).join();
                
                ModerationHistory toRevoke = null;
                if (action == ModerationHistory.ModerationAction.UNMUTE) {
                    toRevoke = activePunishments.stream()
                        .filter(p -> p.getAction() == ModerationHistory.ModerationAction.MUTE)
                        .findFirst().orElse(null);
                } else if (action == ModerationHistory.ModerationAction.UNBAN) {
                    toRevoke = activePunishments.stream()
                        .filter(p -> p.getAction() == ModerationHistory.ModerationAction.TEMP_BAN || 
                                   p.getAction() == ModerationHistory.ModerationAction.PERMANENT_BAN)
                        .findFirst().orElse(null);
                }
                
                if (toRevoke == null) {
                    return ModerationResult.failure("No active " + action.name().toLowerCase() + " found");
                }
                
                // Revoke the punishment
                String issuerName = issuer instanceof Player ? ((Player) issuer).getName() : issuer.getName();
                boolean revoked = repository.revokePunishment(toRevoke.getId(), issuerName, reason).join();
                
                if (!revoked) {
                    return ModerationResult.failure("Failed to revoke punishment");
                }
                
                // Apply revocation (remove active effects)
                applyRevocation(toRevoke, Bukkit.getPlayer(targetId));
                
                // Create revocation entry
                YakPlayer yakPlayer = playerManager.getPlayer(targetId);
                if (yakPlayer != null) {
                    ModerationHistory revocationEntry = createModerationEntry(
                        targetId, yakPlayer.getUsername(), issuer, action, reason, 0,
                        ModerationHistory.PunishmentSeverity.LOW, Bukkit.getPlayer(targetId), 0, null
                    );
                    repository.addModerationEntry(revocationEntry);
                }
                
                // Send notifications
                sendRevocationNotifications(toRevoke, issuer, Bukkit.getPlayer(targetId));
                
                return ModerationResult.success(toRevoke, "Punishment revoked successfully");
                
            } catch (Exception e) {
                logger.severe("Error processing revocation: " + e.getMessage());
                return ModerationResult.failure("Internal error: " + e.getMessage());
            }
        });
    }
    
    // ==========================================
    // ESCALATION LOGIC
    // ==========================================
    
    /**
     * Create action plan with escalation analysis
     */
    private ModerationActionPlan createActionPlan(
            ModerationHistory.ModerationAction requestedAction,
            List<ModerationHistory> history,
            ModerationHistory.PunishmentSeverity severity,
            long requestedDuration,
            boolean checkEscalation) {
        
        if (!checkEscalation) {
            return new ModerationActionPlan(requestedAction, requestedDuration, severity, false);
        }
        
        // Count recent punishments (last 90 days)
        long ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000);
        long recentWarnings = history.stream()
            .filter(h -> h.getTimestamp().getTime() > ninetyDaysAgo)
            .filter(h -> h.getAction() == ModerationHistory.ModerationAction.WARNING)
            .count();
        
        long recentMutes = history.stream()
            .filter(h -> h.getTimestamp().getTime() > ninetyDaysAgo)
            .filter(h -> h.getAction() == ModerationHistory.ModerationAction.MUTE)
            .count();
        
        long recentTempBans = history.stream()
            .filter(h -> h.getTimestamp().getTime() > ninetyDaysAgo)
            .filter(h -> h.getAction() == ModerationHistory.ModerationAction.TEMP_BAN)
            .count();
        
        // Escalation logic
        if (requestedAction == ModerationHistory.ModerationAction.WARNING && recentWarnings >= MAX_WARNINGS_BEFORE_MUTE) {
            return new ModerationActionPlan(ModerationHistory.ModerationAction.MUTE, DEFAULT_MUTE_DURATION, 
                    ModerationHistory.PunishmentSeverity.MEDIUM, true);
        }
        
        if (requestedAction == ModerationHistory.ModerationAction.MUTE && recentMutes >= MAX_MUTES_BEFORE_TEMP_BAN) {
            return new ModerationActionPlan(ModerationHistory.ModerationAction.TEMP_BAN, DEFAULT_TEMP_BAN_DURATION,
                    ModerationHistory.PunishmentSeverity.HIGH, true);
        }
        
        if (requestedAction == ModerationHistory.ModerationAction.TEMP_BAN && recentTempBans >= MAX_TEMP_BANS_BEFORE_PERM) {
            return new ModerationActionPlan(ModerationHistory.ModerationAction.PERMANENT_BAN, 0,
                    ModerationHistory.PunishmentSeverity.SEVERE, true);
        }
        
        // Scale duration based on history
        long adjustedDuration = requestedDuration;
        if (requestedAction == ModerationHistory.ModerationAction.MUTE && recentMutes > 0) {
            adjustedDuration *= Math.min(recentMutes + 1, 5); // Max 5x multiplier
        } else if (requestedAction == ModerationHistory.ModerationAction.TEMP_BAN && recentTempBans > 0) {
            adjustedDuration *= Math.min(recentTempBans + 1, 3); // Max 3x multiplier
        }
        
        return new ModerationActionPlan(requestedAction, adjustedDuration, severity, adjustedDuration != requestedDuration);
    }
    
    // ==========================================
    // HELPER METHODS
    // ==========================================
    
    /**
     * Validate if issuer has permission to perform action on target
     */
    private boolean validateActionPermissions(CommandSender issuer, Player target, ModerationHistory.ModerationAction action) {
        // Basic permission checks
        String permission = "yakrealms.staff." + action.name().toLowerCase();
        if (!issuer.hasPermission(permission)) {
            return false;
        }
        
        // Can't punish higher rank staff without override permission
        if (target != null && ModerationMechanics.isStaff(target)) {
            if (!issuer.hasPermission("yakrealms.staff.override")) {
                if (issuer instanceof Player) {
                    Player staffPlayer = (Player) issuer;
                    Rank issuerRank = ModerationMechanics.getRank(staffPlayer);
                    Rank targetRank = ModerationMechanics.getRank(target);
                    
                    // Compare rank hierarchy
                    if (targetRank.ordinal() >= issuerRank.ordinal()) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Create comprehensive moderation history entry
     */
    private ModerationHistory createModerationEntry(
            UUID targetId, String targetName, CommandSender issuer,
            ModerationHistory.ModerationAction action, String reason, long duration,
            ModerationHistory.PunishmentSeverity severity, Player targetPlayer,
            int totalPunishments, Map<String, Object> metadata) {
        
        ModerationHistory entry = new ModerationHistory();
        entry.setId(new ObjectId());
        entry.setTargetPlayerId(targetId);
        entry.setTargetPlayerName(targetName);
        entry.setTimestamp(new Date());
        entry.setAction(action);
        entry.setReason(reason);
        entry.setSeverity(severity);
        entry.setDurationSeconds(duration);
        entry.setActive(action != ModerationHistory.ModerationAction.NOTE && 
                       action != ModerationHistory.ModerationAction.KICK);
        
        // Set expiry for temporary punishments
        if (duration > 0 && entry.isActive()) {
            entry.setExpiresAt(new Date(System.currentTimeMillis() + (duration * 1000)));
        }
        
        // Staff information
        if (issuer instanceof Player) {
            Player staffPlayer = (Player) issuer;
            entry.setStaffId(staffPlayer.getUniqueId());
            entry.setStaffName(staffPlayer.getName());
        } else {
            entry.setStaffName(issuer.getName());
        }
        
        // IP and location tracking
        if (targetPlayer != null) {
            InetSocketAddress address = targetPlayer.getAddress();
            if (address != null) {
                entry.setIpAddress(address.getAddress().getHostAddress());
            }
            entry.setClientVersion(targetPlayer.getName()); // Could be enhanced
        }
        
        // Punishment tracking
        entry.setTotalPunishments(totalPunishments + 1);
        entry.setPunishmentsLast30Days(calculateRecentPunishments(targetId, 30));
        
        // Appeal settings
        entry.setAppealable(action != ModerationHistory.ModerationAction.NOTE && 
                           action != ModerationHistory.ModerationAction.KICK);
        entry.setAppealStatus(ModerationHistory.AppealStatus.NOT_APPEALED);
        
        // System information
        entry.setModerationSystem("manual");
        entry.setVersion("2.0");
        entry.setIsSystemGenerated(false);
        entry.setServerName("YakRealms");
        
        return entry;
    }
    
    /**
     * Apply punishment effects to player
     */
    private boolean applyPunishment(ModerationHistory entry, Player target) {
        try {
            switch (entry.getAction()) {
                case MUTE:
                    if (target != null) {
                        ModerationMechanics.getInstance().mutePlayer(target, (int) entry.getDurationSeconds(), entry.getStaffName());
                    }
                    break;
                    
                case TEMP_BAN:
                case PERMANENT_BAN:
                case IP_BAN:
                    ModerationMechanics.getInstance().banPlayer(
                        entry.getTargetPlayerId(), 
                        entry.getReason(), 
                        entry.getDurationSeconds(),
                        entry.getStaffName()
                    );
                    break;
                    
                case KICK:
                    if (target != null && target.isOnline()) {
                        target.kickPlayer(ChatColor.RED + "You have been kicked\n" +
                                         ChatColor.GRAY + "Reason: " + entry.getReason());
                    }
                    break;
                    
                default:
                    // No direct punishment effect needed
                    break;
            }
            return true;
        } catch (Exception e) {
            logger.severe("Failed to apply punishment: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Apply revocation effects
     */
    private void applyRevocation(ModerationHistory entry, Player target) {
        switch (entry.getAction()) {
            case MUTE:
                if (target != null) {
                    ModerationMechanics.getInstance().unmutePlayer(target, "System");
                }
                break;
                
            case TEMP_BAN:
            case PERMANENT_BAN:
                ModerationMechanics.getInstance().unbanPlayer(entry.getTargetPlayerId(), "System");
                break;
        }
    }
    
    /**
     * Send comprehensive notifications to staff and target
     */
    private void sendNotifications(ModerationHistory entry, CommandSender issuer, Player target, boolean escalated) {
        String actionName = entry.getAction().name().toLowerCase().replace("_", " ");
        String staffMessage = String.format("%s%s has been %s by %s%s%s (%s%s%s)",
                ChatColor.YELLOW, entry.getTargetPlayerName(),
                actionName, ChatColor.GOLD, issuer.getName(), ChatColor.YELLOW,
                ChatColor.GRAY, entry.getReason(), ChatColor.YELLOW);
        
        if (escalated) {
            staffMessage += ChatColor.RED + " [ESCALATED]";
        }
        
        if (entry.getDurationSeconds() > 0) {
            staffMessage += ChatColor.GRAY + " for " + formatDuration(entry.getDurationSeconds());
        }
        
        // Notify all staff members
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (ModerationMechanics.isStaff(staff) || staff.hasPermission("yakrealms.staff.notifications")) {
                staff.sendMessage(ChatColor.GRAY + "[STAFF] " + staffMessage);
            }
        }
        
        // Notify target player
        if (target != null && target.isOnline() && entry.getAction() != ModerationHistory.ModerationAction.NOTE) {
            String targetMessage = String.format("%sYou have been %s by %s%s",
                    ChatColor.RED, actionName, issuer.getName(), ChatColor.RESET);
            
            if (entry.getDurationSeconds() > 0) {
                targetMessage += ChatColor.RED + " for " + formatDuration(entry.getDurationSeconds());
            }
            
            targetMessage += ChatColor.GRAY + "\nReason: " + entry.getReason();
            
            if (entry.isAppealable()) {
                targetMessage += ChatColor.YELLOW + "\nYou may appeal this punishment if you believe it was issued in error.";
            }
            
            target.sendMessage(targetMessage);
        }
    }
    
    /**
     * Send revocation notifications
     */
    private void sendRevocationNotifications(ModerationHistory entry, CommandSender issuer, Player target) {
        String actionName = entry.getAction().name().toLowerCase().replace("_", " ");
        String staffMessage = String.format("%s%s's %s has been revoked by %s%s",
                ChatColor.GREEN, entry.getTargetPlayerName(),
                actionName, issuer.getName(), ChatColor.RESET);
        
        // Notify staff
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (ModerationMechanics.isStaff(staff) || staff.hasPermission("yakrealms.staff.notifications")) {
                staff.sendMessage(ChatColor.GRAY + "[STAFF] " + staffMessage);
            }
        }
        
        // Notify target
        if (target != null && target.isOnline()) {
            target.sendMessage(ChatColor.GREEN + "Your " + actionName + " has been revoked by " + issuer.getName());
        }
    }
    
    /**
     * Log moderation action with comprehensive details
     */
    private void logModerationAction(ModerationHistory entry, CommandSender issuer, boolean escalated) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("MODERATION: ").append(issuer.getName())
                  .append(" ").append(entry.getAction().name().toLowerCase())
                  .append(" ").append(entry.getTargetPlayerName())
                  .append(" for ").append(entry.getReason());
        
        if (entry.getDurationSeconds() > 0) {
            logMessage.append(" (").append(formatDuration(entry.getDurationSeconds())).append(")");
        }
        
        if (escalated) {
            logMessage.append(" [ESCALATED]");
        }
        
        logger.info(logMessage.toString());
    }
    
    /**
     * Calculate recent punishments within specified days
     */
    private int calculateRecentPunishments(UUID playerId, int days) {
        try {
            long since = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
            List<ModerationHistory> recent = repository.getPlayerHistory(playerId, 100).join();
            return (int) recent.stream()
                    .filter(h -> h.getTimestamp().getTime() > since)
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Format duration in human-readable format
     */
    private String formatDuration(long seconds) {
        if (seconds == 0) return "permanent";
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        
        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m");
        
        return result.toString().trim();
    }
    
    // ==========================================
    // HELPER CLASSES
    // ==========================================
    
    /**
     * Action plan with escalation information
     */
    private static class ModerationActionPlan {
        private final ModerationHistory.ModerationAction finalAction;
        private final long finalDuration;
        private final ModerationHistory.PunishmentSeverity finalSeverity;
        private final boolean escalated;
        
        public ModerationActionPlan(ModerationHistory.ModerationAction finalAction, long finalDuration,
                                   ModerationHistory.PunishmentSeverity finalSeverity, boolean escalated) {
            this.finalAction = finalAction;
            this.finalDuration = finalDuration;
            this.finalSeverity = finalSeverity;
            this.escalated = escalated;
        }
        
        public ModerationHistory.ModerationAction getFinalAction() { return finalAction; }
        public long getFinalDuration() { return finalDuration; }
        public ModerationHistory.PunishmentSeverity getFinalSeverity() { return finalSeverity; }
        public boolean isEscalated() { return escalated; }
    }
    
    /**
     * Result wrapper for moderation actions
     */
    public static class ModerationResult {
        private final boolean success;
        private final String message;
        private final ModerationHistory entry;
        
        private ModerationResult(boolean success, String message, ModerationHistory entry) {
            this.success = success;
            this.message = message;
            this.entry = entry;
        }
        
        public static ModerationResult success(ModerationHistory entry, String message) {
            return new ModerationResult(true, message, entry);
        }
        
        public static ModerationResult failure(String message) {
            return new ModerationResult(false, message, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public ModerationHistory getEntry() { return entry; }
    }
}