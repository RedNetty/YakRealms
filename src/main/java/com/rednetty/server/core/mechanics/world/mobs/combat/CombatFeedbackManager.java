package com.rednetty.server.core.mechanics.world.mobs.combat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat Feedback Manager - Professional combat communication system
 * 
 * This system provides:
 * - Clear and concise ability warnings with strategic timing
 * - Prioritized message system to prevent spam
 * - Professional visual and audio feedback
 * - Scalable danger levels and threat assessment
 * - Context-aware player notifications
 * - Smart message queuing and cooldowns
 */
public class CombatFeedbackManager {
    
    private static CombatFeedbackManager instance;
    
    // Message priority system
    public enum MessagePriority {
        LOW(1, 60),        // General info, 3 seconds
        NORMAL(2, 80),     // Standard combat info, 4 seconds
        HIGH(3, 100),      // Important warnings, 5 seconds
        CRITICAL(4, 120),  // Immediate danger, 6 seconds
        EMERGENCY(5, 140); // Life-threatening, 7 seconds
        
        private final int priority;
        private final long duration;
        
        MessagePriority(int priority, long duration) {
            this.priority = priority;
            this.duration = duration;
        }
        
        public int getPriority() { return priority; }
        public long getDuration() { return duration; }
    }
    
    // Threat levels for different situations
    public enum ThreatLevel {
        MINOR("§7", ""),
        MODERATE("§e", "⚠ "),
        MAJOR("§c", "§c§l⚠ "),
        CRITICAL("§4", "§4§l⚠ "),
        LETHAL("§4§l", "§4§l💀 ");
        
        private final String color;
        private final String prefix;
        
        ThreatLevel(String color, String prefix) {
            this.color = color;
            this.prefix = prefix;
        }
        
        public String getColor() { return color; }
        public String getPrefix() { return prefix; }
    }
    
    // Per-player message tracking
    private final Map<UUID, PlayerFeedbackData> playerData = new ConcurrentHashMap<>();
    
    // Global cooldowns for different message types
    private final Map<String, Long> globalCooldowns = new ConcurrentHashMap<>();
    
    // Configuration
    private static final long ABILITY_WARNING_COOLDOWN = 2000; // 2 seconds
    private static final long GENERAL_MESSAGE_COOLDOWN = 5000; // 5 seconds
    private static final long CRITICAL_MESSAGE_COOLDOWN = 1000; // 1 second
    
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    
    private CombatFeedbackManager() {
        startFeedbackManagementTask();
    }
    
    public static CombatFeedbackManager getInstance() {
        if (instance == null) {
            instance = new CombatFeedbackManager();
        }
        return instance;
    }
    
    // ==================== CORE MESSAGING SYSTEM ====================
    
    /**
     * Send a prioritized message to a player
     */
    public void sendMessage(Player player, String message, MessagePriority priority, ThreatLevel threat) {
        if (player == null || !player.isOnline()) return;
        
        UUID playerId = player.getUniqueId();
        PlayerFeedbackData data = playerData.computeIfAbsent(playerId, k -> new PlayerFeedbackData());
        
        // Check if we can send this message based on priority and cooldowns
        if (!canSendMessage(data, priority)) {
            return;
        }
        
        // Format the message with threat level
        String formattedMessage = threat.getPrefix() + threat.getColor() + message;
        
        // Send the message
        ActionBarUtil.addUniqueTemporaryMessage(player, formattedMessage, priority.getDuration());
        
        // Update player data
        data.setLastMessage(System.currentTimeMillis(), priority);
        data.incrementMessageCount();
        
        // Play appropriate sound based on threat level
        playThreatSound(player, threat);
    }
    
    /**
     * Send an ability warning with proper telegraphing
     */
    public void sendAbilityWarning(Player player, String abilityName, long warningDurationTicks, 
                                  boolean isTargeted, CustomMob mob) {
        if (!canSendAbilityWarning(player, abilityName)) {
            return;
        }
        
        ThreatLevel threat = isTargeted ? ThreatLevel.CRITICAL : ThreatLevel.MAJOR;
        MessagePriority priority = isTargeted ? MessagePriority.CRITICAL : MessagePriority.HIGH;
        
        String targetInfo = isTargeted ? " §4§lTARGETING YOU" : "";
        String message = mob.getType().getTierSpecificName(mob.getTier()) + " §6preparing §e§l" + 
                        abilityName.toUpperCase() + targetInfo + " §6(" + (warningDurationTicks/20) + "s)";
        
        sendMessage(player, message, priority, threat);
        
        // Mark ability warning sent
        markAbilityWarning(player, abilityName);
    }
    
    /**
     * Send ability execution notification
     */
    public void sendAbilityExecution(Player player, String abilityName, CustomMob mob) {
        ThreatLevel threat = ThreatLevel.MAJOR;
        MessagePriority priority = MessagePriority.HIGH;
        
        String message = mob.getType().getTierSpecificName(mob.getTier()) + " §c§lexecuting §e" + abilityName + "!";
        
        sendMessage(player, message, priority, threat);
    }
    
    /**
     * Send ability hit notification with damage severity
     */
    public void sendAbilityHit(Player player, String abilityName, double damage, double maxHealth) {
        double damagePercent = damage / maxHealth;
        ThreatLevel threat;
        String severity;
        
        if (damagePercent >= 0.5) {
            threat = ThreatLevel.LETHAL;
            severity = "DEVASTATING";
        } else if (damagePercent >= 0.3) {
            threat = ThreatLevel.CRITICAL;
            severity = "SEVERE";
        } else if (damagePercent >= 0.15) {
            threat = ThreatLevel.MAJOR;
            severity = "HEAVY";
        } else {
            threat = ThreatLevel.MODERATE;
            severity = "MODERATE";
        }
        
        String message = severity + " hit by " + abilityName + "! (-" + String.format("%.1f", damage) + " HP)";
        
        sendMessage(player, message, MessagePriority.HIGH, threat);
    }
    
    /**
     * Send counterplay opportunity notification
     */
    public void sendCounterplayHint(Player player, String hint, int timeWindowTicks) {
        String message = "§a§lCOUNTER: " + hint + " §a(" + (timeWindowTicks/20) + "s window)";
        
        sendMessage(player, message, MessagePriority.HIGH, ThreatLevel.MODERATE);
        
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
    }
    
    /**
     * Send successful counterplay acknowledgment
     */
    public void sendCounterplaySuccess(Player player, String action) {
        String message = "§a§l✓ EXCELLENT! " + action + " successfully countered!";
        
        sendMessage(player, message, MessagePriority.HIGH, ThreatLevel.MINOR);
        
        // Reward sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 2.0f);
        
        // Visual reward effect
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, 
            player.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0.1);
    }
    
    // ==================== SPECIALIZED FEEDBACK METHODS ====================
    
    /**
     * Handle complex multi-phase ability feedback
     */
    public void handlePhaseAbility(Player player, String abilityName, int currentPhase, int totalPhases, 
                                  long phaseDuration, CustomMob mob) {
        String phaseInfo = "Phase " + currentPhase + "/" + totalPhases;
        ThreatLevel threat = currentPhase == totalPhases ? ThreatLevel.CRITICAL : ThreatLevel.MAJOR;
        
        String message = mob.getType().getTierSpecificName(mob.getTier()) + " §6" + abilityName + " " + phaseInfo + 
                        " §6(" + (phaseDuration/20) + "s)";
        
        sendMessage(player, message, MessagePriority.HIGH, threat);
    }
    
    /**
     * Handle area-of-effect ability warnings
     */
    public void handleAoEWarning(Player player, String abilityName, double distanceFromCenter, 
                               double maxRange, long warningTime, CustomMob mob) {
        boolean inDangerZone = distanceFromCenter <= maxRange;
        ThreatLevel threat = inDangerZone ? ThreatLevel.CRITICAL : ThreatLevel.MAJOR;
        
        String zoneInfo = inDangerZone ? " §4§lIN DANGER ZONE!" : " §6nearby";
        String message = mob.getType().getTierSpecificName(mob.getTier()) + " §6" + abilityName + zoneInfo + 
                        " §6(" + (warningTime/20) + "s)";
        
        sendMessage(player, message, MessagePriority.CRITICAL, threat);
    }
    
    /**
     * Handle environmental hazard notifications
     */
    public void handleEnvironmentalHazard(Player player, String hazardType, int duration, ThreatLevel threat) {
        String message = hazardType + " hazard active! Duration: " + (duration/20) + "s";
        
        sendMessage(player, message, MessagePriority.NORMAL, threat);
    }
    
    /**
     * Handle elite state changes (rage, phase transitions, etc.)
     */
    public void handleEliteStateChange(Player player, CustomMob mob, String newState, String description) {
        ThreatLevel threat = ThreatLevel.MAJOR;
        
        String message = mob.getType().getTierSpecificName(mob.getTier()) + " §c§lenters " + newState + 
                        "! §6" + description;
        
        sendMessage(player, message, MessagePriority.HIGH, threat);
    }
    
    // ==================== UTILITY METHODS ====================
    
    private boolean canSendMessage(PlayerFeedbackData data, MessagePriority priority) {
        long currentTime = System.currentTimeMillis();
        
        // Always allow emergency messages
        if (priority == MessagePriority.EMERGENCY) {
            return true;
        }
        
        // Check if enough time has passed based on priority
        long cooldown = switch (priority) {
            case CRITICAL -> CRITICAL_MESSAGE_COOLDOWN;
            case HIGH -> ABILITY_WARNING_COOLDOWN;
            default -> GENERAL_MESSAGE_COOLDOWN;
        };
        
        return currentTime - data.getLastMessageTime() >= cooldown || 
               data.getLastPriority().getPriority() < priority.getPriority();
    }
    
    private boolean canSendAbilityWarning(Player player, String abilityName) {
        String key = player.getUniqueId() + ":" + abilityName;
        Long lastWarning = globalCooldowns.get(key);
        
        if (lastWarning == null) {
            return true;
        }
        
        return System.currentTimeMillis() - lastWarning >= ABILITY_WARNING_COOLDOWN;
    }
    
    private void markAbilityWarning(Player player, String abilityName) {
        String key = player.getUniqueId() + ":" + abilityName;
        globalCooldowns.put(key, System.currentTimeMillis());
    }
    
    private void playThreatSound(Player player, ThreatLevel threat) {
        switch (threat) {
            case LETHAL -> {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 2.0f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            case CRITICAL -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.6f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f);
            }
            case MAJOR -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.0f);
            }
            case MODERATE -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.6f, 1.2f);
            }
            case MINOR -> {
                // No sound for minor threats to avoid spam
            }
        }
    }
    
    // ==================== BACKGROUND MANAGEMENT ====================
    
    private void startFeedbackManagementTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredData();
            }
        }.runTaskTimer(YakRealms.getInstance(), 200L, 200L); // Every 10 seconds
    }
    
    private void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 30000; // 30 seconds
        
        // Clean up player data for offline players
        playerData.entrySet().removeIf(entry -> {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            return player == null || !player.isOnline() || 
                   currentTime - entry.getValue().getLastMessageTime() > expireTime;
        });
        
        // Clean up global cooldowns
        globalCooldowns.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > expireTime);
    }
    
    // ==================== VISUAL ENHANCEMENT SYSTEM ====================
    
    /**
     * Create visual indicators for ability telegraphs
     */
    public void createVisualTelegraph(Location center, double range, Particle particle, 
                                    long durationTicks, String shape) {
        new BukkitRunnable() {
            long ticksRemaining = durationTicks;
            
            @Override
            public void run() {
                if (ticksRemaining <= 0) {
                    cancel();
                    return;
                }
                
                switch (shape.toLowerCase()) {
                    case "circle" -> createCirclePattern(center, range, particle, ticksRemaining, durationTicks);
                    case "line" -> createLinePattern(center, range, particle, ticksRemaining, durationTicks);
                    case "cone" -> createConePattern(center, range, particle, ticksRemaining, durationTicks);
                    default -> createCirclePattern(center, range, particle, ticksRemaining, durationTicks);
                }
                
                ticksRemaining--;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    private void createCirclePattern(Location center, double range, Particle particle, 
                                   long ticksRemaining, long totalTicks) {
        int points = Math.max(8, (int)(range * 4));
        double intensity = 1.0 - (double) ticksRemaining / totalTicks;
        
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + range * Math.cos(angle);
            double z = center.getZ() + range * Math.sin(angle);
            Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            
            int count = Math.max(1, (int)(3 * intensity));
            center.getWorld().spawnParticle(particle, particleLoc, count, 0.1, 0.1, 0.1, 0.02);
        }
    }
    
    private void createLinePattern(Location center, double range, Particle particle, 
                                 long ticksRemaining, long totalTicks) {
        // Implementation for line patterns (charges, etc.)
        double intensity = 1.0 - (double) ticksRemaining / totalTicks;
        
        for (double d = 0; d <= range; d += 0.5) {
            Location particleLoc = center.clone().add(d, 0.1, 0);
            int count = Math.max(1, (int)(2 * intensity));
            center.getWorld().spawnParticle(particle, particleLoc, count, 0.1, 0.1, 0.1, 0.02);
        }
    }
    
    private void createConePattern(Location center, double range, Particle particle, 
                                 long ticksRemaining, long totalTicks) {
        // Implementation for cone patterns (breath attacks, etc.)
        double intensity = 1.0 - (double) ticksRemaining / totalTicks;
        
        for (double d = 0; d <= range; d += 0.5) {
            double coneWidth = d * 0.5; // Cone gets wider with distance
            for (double w = -coneWidth; w <= coneWidth; w += 0.5) {
                Location particleLoc = center.clone().add(d, 0.1, w);
                int count = Math.max(1, (int)(2 * intensity));
                center.getWorld().spawnParticle(particle, particleLoc, count, 0.1, 0.1, 0.1, 0.02);
            }
        }
    }
    
    // ==================== PLAYER DATA CLASS ====================
    
    private static class PlayerFeedbackData {
        private long lastMessageTime = 0;
        private MessagePriority lastPriority = MessagePriority.LOW;
        private int messageCount = 0;
        
        public void setLastMessage(long time, MessagePriority priority) {
            this.lastMessageTime = time;
            this.lastPriority = priority;
        }
        
        public void incrementMessageCount() {
            this.messageCount++;
        }
        
        public long getLastMessageTime() { return lastMessageTime; }
        public MessagePriority getLastPriority() { return lastPriority; }
        public int getMessageCount() { return messageCount; }
    }
    
    // ==================== DEBUG AND ADMIN METHODS ====================
    
    /**
     * Get debug information about the feedback system
     */
    public String getDebugInfo() {
        return String.format("§e=== Combat Feedback Manager ===\n" +
                           "§fActive Players: %d\n" +
                           "§fActive Cooldowns: %d\n" +
                           "§fTotal Messages Today: %d",
                           playerData.size(),
                           globalCooldowns.size(),
                           playerData.values().stream().mapToInt(PlayerFeedbackData::getMessageCount).sum());
    }
    
    /**
     * Test the feedback system (admin command)
     */
    public void testFeedback(Player player, ThreatLevel threat, MessagePriority priority) {
        String message = "Test message - Threat: " + threat.name() + ", Priority: " + priority.name();
        sendMessage(player, message, priority, threat);
    }
}