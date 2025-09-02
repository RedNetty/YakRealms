package com.rednetty.server.core.mechanics.player.moderation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Represents a staff note about a player
 * Used for internal staff communication and record keeping
 */
public class StaffNote {
    private String id;
    private UUID playerId;
    private String playerName;
    private String author;
    private String content;
    private Importance importance;
    private long timestamp;
    private boolean visible;
    
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    
    public enum Importance {
        LOW,        // General observations
        MEDIUM,     // Notable behavior
        HIGH,       // Concerning activity
        CRITICAL    // Urgent attention required
    }
    
    public StaffNote(String id, UUID playerId, String playerName, String author, String content, Importance importance) {
        this.id = id;
        this.playerId = playerId;
        this.playerName = playerName;
        this.author = author;
        this.content = content;
        this.importance = importance;
        this.timestamp = System.currentTimeMillis();
        this.visible = true;
    }
    
    // Getters
    public String getId() {
        return id;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public String getContent() {
        return content;
    }
    
    public Importance getImportance() {
        return importance;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getFormattedDate() {
        return dateFormat.format(new Date(timestamp));
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    // Setters
    public void setContent(String content) {
        this.content = content;
    }
    
    public void setImportance(Importance importance) {
        this.importance = importance;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}