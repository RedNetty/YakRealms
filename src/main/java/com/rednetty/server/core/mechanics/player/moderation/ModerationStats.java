package com.rednetty.server.core.mechanics.player.moderation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Statistics data class for moderation system analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationStats {
    
    // General Statistics
    private int totalPunishments;
    private int activePunishments;
    private int expiredPunishments;
    private int revokedPunishments;
    
    // Action Breakdown
    private int warningsIssued;
    private int bansIssued;
    private int mutesIssued;
    private int kicksIssued;
    private int ipBansIssued;
    
    // Duration Statistics
    private int permanentPunishments;
    private int temporaryPunishments;
    private long averagePunishmentDuration;
    
    // Appeal Statistics
    private int appealsSubmitted;
    private int appealsApproved;
    private int appealsDenied;
    private int appealsPending;
    
    // Staff Statistics
    private Map<UUID, Integer> punishmentsByStaff;
    private UUID mostActiveStaff;
    private int mostActiveStaffCount;
    
    // Time-based Statistics
    private Date periodStart;
    private Date periodEnd;
    private Map<String, Integer> punishmentsByDay;
    private Map<String, Integer> punishmentsByHour;
    
    // Effectiveness Metrics
    private double appealSuccessRate;
    private double averageRecidivismScore;
    private int repeatOffenders;
    private int firstTimeOffenders;
    
    // Severity Breakdown
    private int lowSeverityPunishments;
    private int mediumSeverityPunishments;
    private int highSeverityPunishments;
    private int severePunishments;
    private int criticalPunishments;
    
    // IP-related Statistics
    private int uniqueIPs;
    private int ipBanEvaders;
    private int suspiciousIPActivity;
    
    // Helper methods for calculated fields
    public double getAppealSuccessRate() {
        if (appealsSubmitted == 0) return 0.0;
        return (double) appealsApproved / appealsSubmitted * 100.0;
    }
    
    public double getRevocationRate() {
        if (totalPunishments == 0) return 0.0;
        return (double) revokedPunishments / totalPunishments * 100.0;
    }
    
    public double getRepeatOffenderRate() {
        if (totalPunishments == 0) return 0.0;
        return (double) repeatOffenders / (repeatOffenders + firstTimeOffenders) * 100.0;
    }
    
    // Additional getters needed by the history command
    public int getExpiredPunishments() {
        // This would need to be calculated, for now return 0
        return 0;
    }
    
    public int getRevokedPunishments() {
        // This would need to be calculated, for now return 0
        return 0;
    }
    
    public int getKicksIssued() {
        // This would need to be tracked separately, for now return 0
        return 0;
    }
    
    public int getAppealsApproved() {
        // This would need to be calculated from appeal status, for now return 0
        return 0;
    }
    
    public String getSummary() {
        return String.format(
            "Moderation Summary:\n" +
            "├─ Total Punishments: %d (%d active)\n" +
            "├─ Bans: %d | Mutes: %d | Warnings: %d\n" +
            "├─ Appeals: %d submitted (%.1f%% success rate)\n" +
            "├─ Revocation Rate: %.1f%%\n" +
            "└─ Repeat Offenders: %d (%.1f%%)",
            totalPunishments, activePunishments,
            bansIssued, mutesIssued, warningsIssued,
            appealsSubmitted, getAppealSuccessRate(),
            getRevocationRate(),
            repeatOffenders, getRepeatOffenderRate()
        );
    }
    
    // Methods needed by moderation menus
    public int getActionsLast24Hours() {
        // This would be calculated from recent actions
        return 0; // Placeholder
    }
    
    // Additional methods needed by StubMenus
    public int getTotalRecords() {
        return totalPunishments;
    }
    
    public int getTodayActions() {
        return getActionsLast24Hours();
    }
    
    public Map<ModerationHistory.ModerationAction, Long> getTopActions() {
        Map<ModerationHistory.ModerationAction, Long> actions = new HashMap<>();
        actions.put(ModerationHistory.ModerationAction.WARNING, (long) warningsIssued);
        actions.put(ModerationHistory.ModerationAction.TEMP_BAN, (long) (bansIssued / 2)); // Rough split
        actions.put(ModerationHistory.ModerationAction.PERMANENT_BAN, (long) (bansIssued / 2));
        actions.put(ModerationHistory.ModerationAction.MUTE, (long) mutesIssued);
        return actions;
    }
    
    public int getThisMonthActions() {
        return totalPunishments; // Placeholder - would be calculated from date range
    }
    
    public int getThisWeekActions() {
        return totalPunishments / 4; // Placeholder - rough estimate
    }
}