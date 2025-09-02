package com.rednetty.server.core.mechanics.player.moderation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Comprehensive moderation history entry for audit trail and tracking
 * Enhanced with modern features like IP tracking, severity levels, and appeal status
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationHistory {
    
    @BsonId
    private ObjectId id;
    
    // Core Information
    private UUID targetPlayerId;
    private String targetPlayerName;
    private UUID staffId;
    private String staffName;
    private Date timestamp;
    private ModerationAction action;
    private String reason;
    private PunishmentSeverity severity;
    
    // Duration and Timing
    private long durationSeconds; // 0 for permanent
    private Date expiresAt; // null for permanent
    private boolean isActive;
    private Date revokedAt;
    private String revokedBy;
    private String revokeReason;
    
    // Enhanced Tracking
    private String ipAddress;
    private String clientVersion;
    private String location; // GeoIP if available
    private List<String> previousNames;
    private List<String> associatedIPs;
    
    // Evidence and Context
    private List<String> evidence; // Screenshots, logs, etc.
    private String context; // Additional context about the incident
    private List<String> witnesses; // Other players who witnessed
    private String serverName; // Multi-server support
    
    // Appeal System
    private boolean appealable;
    private AppealStatus appealStatus;
    private Date appealedAt;
    private String appealReason;
    private String appealResponse;
    private String appealHandledBy;
    private Date appealHandledAt;
    
    // Escalation and Warnings
    private int warningLevel; // 1-5 scale
    private boolean isEscalation; // Auto-escalated from warnings
    private ObjectId parentWarningId; // Links to previous warning
    private int totalPunishments; // Count at time of punishment
    private int punishmentsLast30Days;
    
    // System Information
    private String moderationSystem; // "manual", "auto", "appeal_system"
    private String version; // Version of moderation system
    private boolean isSystemGenerated;
    private String automationRule; // If auto-generated, what rule triggered it
    
    // Statistics and Analytics
    private boolean effectivenessMeasured;
    private int recidivismScore; // 0-100, higher = more likely to reoffend
    private Date nextEligiblePunishment; // Cooldown prevention
    
    public enum ModerationAction {
        WARNING,
        MUTE,
        TEMP_BAN,
        PERMANENT_BAN,
        IP_BAN,
        KICK,
        UNMUTE,
        UNBAN,
        RANK_CHANGE,
        NOTE,
        WATCHLIST_ADD,
        WATCHLIST_REMOVE
    }
    
    public enum PunishmentSeverity {
        LOW(1, "Minor infraction"),
        MEDIUM(2, "Moderate violation"), 
        HIGH(3, "Serious offense"),
        SEVERE(4, "Major violation"),
        CRITICAL(5, "Extreme violation");
        
        private final int level;
        private final String description;
        
        PunishmentSeverity(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }
    
    public enum AppealStatus {
        NOT_APPEALED,
        PENDING,
        UNDER_REVIEW,
        APPROVED,
        DENIED,
        WITHDRAWN
    }
    
    // Helper methods
    public boolean isExpired() {
        if (expiresAt == null) return false; // Permanent
        return new Date().after(expiresAt);
    }
    
    public boolean isPermanent() {
        return durationSeconds == 0 || expiresAt == null;
    }
    
    public long getRemainingTimeSeconds() {
        if (isPermanent()) return -1;
        if (isExpired()) return 0;
        return (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
    }
    
    public String getFormattedDuration() {
        if (isPermanent()) return "Permanent";
        
        long seconds = durationSeconds;
        if (seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " hours";
        } else if (seconds < 604800) {
            return (seconds / 86400) + " days";
        } else {
            return (seconds / 604800) + " weeks";
        }
    }
    
    public boolean canBeAppealed() {
        return appealable && appealStatus == AppealStatus.NOT_APPEALED 
                && isActive && (isPermanent() || getRemainingTimeSeconds() > 86400); // Must have more than 1 day left
    }
    
    public boolean requiresEscalation(int currentWarnings) {
        // Auto-escalation rules
        if (action == ModerationAction.WARNING) {
            return currentWarnings >= 3; // 3 warnings = temp mute
        }
        return false;
    }
    
    // Additional utility methods for the history command
    public Date getAppealSubmittedAt() {
        return appealedAt;
    }
    
    // Methods for moderation menus
    public int getTotalPunishments() {
        return totalPunishments;
    }
    
    public int getActiveWarnings() {
        // This would be calculated based on active warning count
        return warningLevel;
    }
    
    // Manual setter needed for boolean fields with "is" prefix
    public void setIsSystemGenerated(boolean isSystemGenerated) {
        this.isSystemGenerated = isSystemGenerated;
    }
}