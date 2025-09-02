package com.rednetty.server.core.mechanics.player.moderation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

/**
 * Search criteria class for advanced moderation history queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationSearchCriteria {
    
    // Player-related filters
    private UUID playerId;
    private String playerName;
    private UUID staffId;
    private String staffName;
    
    // Action filters
    private ModerationHistory.ModerationAction action;
    private ModerationHistory.PunishmentSeverity severity;
    private Boolean isActive;
    private Boolean isPermanent;
    
    // Time filters
    private Date startDate;
    private Date endDate;
    private Date appealsAfter;
    private Date appealsBefore;
    
    // Content filters
    private String reasonContains;
    private String ipAddress;
    private String serverName;
    
    // Appeal filters
    private ModerationHistory.AppealStatus appealStatus;
    private Boolean appealable;
    
    // System filters
    private Boolean isSystemGenerated;
    private String moderationSystem;
    private Boolean isEscalation;
    
    // Result options
    @Builder.Default
    private int limit = 50;
    @Builder.Default
    private int offset = 0;
    @Builder.Default
    private String sortBy = "timestamp";
    @Builder.Default
    private boolean sortDescending = true;
    
    // Advanced filters
    private Integer minWarningLevel;
    private Integer maxWarningLevel;
    private Integer minTotalPunishments;
    private Integer maxTotalPunishments;
    private String locationContains;
    
    // Utility methods for common search patterns
    public static ModerationSearchCriteria forPlayer(UUID playerId) {
        return ModerationSearchCriteria.builder()
                .playerId(playerId)
                .limit(25)
                .build();
    }
    
    public static ModerationSearchCriteria forStaff(UUID staffId, Date since) {
        return ModerationSearchCriteria.builder()
                .staffId(staffId)
                .startDate(since)
                .limit(50)
                .build();
    }
    
    public static ModerationSearchCriteria forActiveOnly() {
        return ModerationSearchCriteria.builder()
                .isActive(true)
                .limit(100)
                .build();
    }
    
    public static ModerationSearchCriteria forAppeals(ModerationHistory.AppealStatus status) {
        return ModerationSearchCriteria.builder()
                .appealStatus(status)
                .appealable(true)
                .limit(25)
                .build();
    }
    
    public static ModerationSearchCriteria forIP(String ipAddress) {
        return ModerationSearchCriteria.builder()
                .ipAddress(ipAddress)
                .limit(25)
                .build();
    }
    
    public static ModerationSearchCriteria forSeverePunishments() {
        return ModerationSearchCriteria.builder()
                .severity(ModerationHistory.PunishmentSeverity.SEVERE)
                .limit(50)
                .build();
    }
    
    public static ModerationSearchCriteria forRecentActivity(int days) {
        Date since = new Date(System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000));
        return ModerationSearchCriteria.builder()
                .startDate(since)
                .limit(100)
                .build();
    }
    
    // Additional methods needed by StubMenus
    public void setFromDate(Date date) {
        this.startDate = date;
    }
    
    public void setMaxResults(int max) {
        this.limit = max;
    }
    
    public void setOnlyActive(boolean active) {
        this.isActive = active;
    }
    
}