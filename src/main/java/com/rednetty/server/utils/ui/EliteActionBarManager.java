package com.rednetty.server.utils.ui;

import com.rednetty.server.YakRealms;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages action bar messages specifically for elite abilities to prevent spam and conflicts.
 * Uses a priority system where higher priority messages override lower ones.
 */
public class EliteActionBarManager {
    
    private static EliteActionBarManager instance;
    
    // Message storage with priority and duration
    private final Map<UUID, PriorityMessage> playerMessages = new ConcurrentHashMap<>();
    
    // Message priorities
    public enum Priority {
        LOW(1),       // General notifications
        MEDIUM(2),    // Ability warnings
        HIGH(3),      // Direct threats
        CRITICAL(4);  // Immediate danger
        
        private final int level;
        Priority(int level) { this.level = level; }
        public int getLevel() { return level; }
    }
    
    private static class PriorityMessage {
        final String message;
        final Priority priority;
        final long expiryTime;
        
        PriorityMessage(String message, Priority priority, long durationTicks) {
            this.message = message;
            this.priority = priority;
            this.expiryTime = System.currentTimeMillis() + (durationTicks * 50); // Convert to ms
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() >= expiryTime;
        }
    }
    
    private EliteActionBarManager() {
        startCleanupTask();
    }
    
    public static EliteActionBarManager getInstance() {
        if (instance == null) {
            instance = new EliteActionBarManager();
        }
        return instance;
    }
    
    /**
     * Sets a message for a player with given priority. Higher priority messages override lower ones.
     * If the current message has higher priority, the new message is ignored.
     */
    public void setMessage(Player player, String message, Priority priority, long durationTicks) {
        if (player == null || message == null) return;
        
        UUID playerId = player.getUniqueId();
        PriorityMessage current = playerMessages.get(playerId);
        
        // Check if we should override current message
        if (current != null && !current.isExpired() && 
            current.priority.getLevel() >= priority.getLevel()) {
            return; // Don't override higher/equal priority message
        }
        
        // Set new message
        playerMessages.put(playerId, new PriorityMessage(message, priority, durationTicks));
        
        // Send immediately
        ActionBarUtil.sendActionBar(player, message);
    }
    
    /**
     * Sets a low priority general notification
     */
    public void setNotification(Player player, String message, long durationTicks) {
        setMessage(player, message, Priority.LOW, durationTicks);
    }
    
    /**
     * Sets a medium priority ability warning
     */
    public void setWarning(Player player, String message, long durationTicks) {
        setMessage(player, message, Priority.MEDIUM, durationTicks);
    }
    
    /**
     * Sets a high priority threat alert
     */
    public void setThreat(Player player, String message, long durationTicks) {
        setMessage(player, message, Priority.HIGH, durationTicks);
    }
    
    /**
     * Sets a critical priority immediate danger alert
     */
    public void setCritical(Player player, String message, long durationTicks) {
        setMessage(player, message, Priority.CRITICAL, durationTicks);
    }
    
    /**
     * Clears any message for the player
     */
    public void clearMessage(Player player) {
        if (player != null) {
            playerMessages.remove(player.getUniqueId());
            ActionBarUtil.sendActionBar(player, "");
        }
    }
    
    /**
     * Gets the current message priority for a player
     */
    public Priority getCurrentPriority(Player player) {
        if (player == null) return null;
        
        PriorityMessage current = playerMessages.get(player.getUniqueId());
        if (current != null && !current.isExpired()) {
            return current.priority;
        }
        
        return null;
    }
    
    /**
     * Starts the cleanup task to remove expired messages
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                playerMessages.entrySet().removeIf(entry -> entry.getValue().isExpired());
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Run every second
    }
    
    /**
     * Helper methods for elite ability messages with appropriate priorities
     */
    public static void notifyActivation(Player player, String archetypeName) {
        getInstance().setNotification(player, "§7[" + archetypeName + " Active]", 40L);
    }
    
    public static void notifyAbilityTelegraph(Player player, String abilityName) {
        getInstance().setWarning(player, "§6[" + abilityName + " Incoming]", 30L);
    }
    
    public static void notifyDirectThreat(Player player, String threatMessage) {
        getInstance().setThreat(player, "§c" + threatMessage, 40L);
    }
    
    public static void notifyCriticalDanger(Player player, String dangerMessage) {
        getInstance().setCritical(player, "§4§l" + dangerMessage, 60L);
    }
    
    public static void notifyAbilityHit(Player player, String abilityName, String severity) {
        Priority priority = switch (severity.toLowerCase()) {
            case "light" -> Priority.LOW;
            case "medium" -> Priority.MEDIUM;
            case "severe" -> Priority.HIGH;
            default -> Priority.MEDIUM;
        };
        getInstance().setMessage(player, "§e[" + abilityName + " Hit - " + severity + "]", priority, 30L);
    }
}