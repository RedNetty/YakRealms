package com.rednetty.server.core.mechanics.player.moderation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Represents a player's appeal for a punishment
 */
public class Appeal {
    
    private String id;
    private UUID playerId;
    private String playerName;
    private String punishmentId;
    private String punishmentType;
    private String appealReason;
    private long submissionTime;
    private AppealStatus status;
    private String reviewedBy;
    private long reviewTime;
    private String reviewReason;
    private String staffComments;
    
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    
    // Constructors
    public Appeal() {}
    
    public Appeal(UUID playerId, String playerName, String punishmentId, 
                  String punishmentType, String appealReason) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.punishmentId = punishmentId;
        this.punishmentType = punishmentType;
        this.appealReason = appealReason;
        this.submissionTime = System.currentTimeMillis();
        this.status = AppealStatus.PENDING;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    
    public String getPunishmentId() { return punishmentId; }
    public void setPunishmentId(String punishmentId) { this.punishmentId = punishmentId; }
    
    public String getPunishmentType() { return punishmentType; }
    public void setPunishmentType(String punishmentType) { this.punishmentType = punishmentType; }
    
    public String getAppealReason() { return appealReason; }
    public void setAppealReason(String appealReason) { this.appealReason = appealReason; }
    
    public long getSubmissionTime() { return submissionTime; }
    public void setSubmissionTime(long submissionTime) { this.submissionTime = submissionTime; }
    
    public AppealStatus getStatus() { return status; }
    public void setStatus(AppealStatus status) { this.status = status; }
    
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    
    public long getReviewTime() { return reviewTime; }
    public void setReviewTime(long reviewTime) { this.reviewTime = reviewTime; }
    
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    
    public String getStaffComments() { return staffComments; }
    public void setStaffComments(String staffComments) { this.staffComments = staffComments; }
    
    // Utility methods
    public void approve(String staffName, String reason) {
        this.status = AppealStatus.APPROVED;
        this.reviewedBy = staffName;
        this.reviewTime = System.currentTimeMillis();
        this.reviewReason = reason;
    }
    
    public void deny(String staffName, String reason) {
        this.status = AppealStatus.DENIED;
        this.reviewedBy = staffName;
        this.reviewTime = System.currentTimeMillis();
        this.reviewReason = reason;
    }
    
    public boolean isUrgent() {
        return status == AppealStatus.PENDING && 
               (System.currentTimeMillis() - submissionTime) > 3 * 24 * 60 * 60 * 1000L; // 3 days
    }
    
    public String getFormattedSubmissionDate() {
        return dateFormat.format(new Date(submissionTime));
    }
    
    public String getFormattedReviewDate() {
        return reviewTime > 0 ? dateFormat.format(new Date(reviewTime)) : "Not reviewed";
    }
    
    public String getReviewNotes() {
        return reviewReason; // Alias for consistency with menu expectations
    }
}