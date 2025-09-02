package com.rednetty.server.core.mechanics.player.moderation;

import java.util.UUID;

/**
 * Represents a punishment record in the moderation system
 */
public class PunishmentRecord {
    
    public enum PunishmentType {
        BAN, MUTE, WARN, KICK, TEMPBAN
    }
    
    private String id;
    private UUID targetId;
    private String targetName;
    private UUID staffId;
    private String staffName;
    private PunishmentType type;
    private String reason;
    private long timestamp;
    private long duration; // in milliseconds, 0 for permanent
    private long expiryTime; // timestamp when punishment expires
    private boolean active;
    private boolean lifted;
    private String liftedBy;
    private long liftedTime;
    
    // Constructors
    public PunishmentRecord() {}
    
    public PunishmentRecord(UUID targetId, String targetName, UUID staffId, String staffName,
                           PunishmentType type, String reason, long duration) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.staffId = staffId;
        this.staffName = staffName;
        this.type = type;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
        this.duration = duration;
        this.expiryTime = duration > 0 ? timestamp + duration : 0;
        this.active = true;
        this.lifted = false;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }
    
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    
    public UUID getStaffId() { return staffId; }
    public void setStaffId(UUID staffId) { this.staffId = staffId; }
    
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    
    public PunishmentType getType() { return type; }
    public void setType(PunishmentType type) { this.type = type; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    
    public long getExpiryTime() { return expiryTime; }
    public void setExpiryTime(long expiryTime) { this.expiryTime = expiryTime; }
    
    public boolean isActive() { return active && !isExpired(); }
    public void setActive(boolean active) { this.active = active; }
    
    public boolean isLifted() { return lifted; }
    public void setLifted(boolean lifted) { this.lifted = lifted; }
    
    public String getLiftedBy() { return liftedBy; }
    public void setLiftedBy(String liftedBy) { this.liftedBy = liftedBy; }
    
    public long getLiftedTime() { return liftedTime; }
    public void setLiftedTime(long liftedTime) { this.liftedTime = liftedTime; }
    
    // Utility methods
    public boolean isExpired() {
        return expiryTime > 0 && System.currentTimeMillis() > expiryTime;
    }
    
    public boolean isPermanent() {
        return duration == 0;
    }
    
    public long getRemainingTime() {
        if (isPermanent()) return -1;
        if (isExpired()) return 0;
        return expiryTime - System.currentTimeMillis();
    }
    
    public void lift(String staffName) {
        this.lifted = true;
        this.active = false;
        this.liftedBy = staffName;
        this.liftedTime = System.currentTimeMillis();
    }
}