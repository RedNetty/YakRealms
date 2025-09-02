package com.rednetty.server.core.mechanics.world.mobs;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.abilities.EliteAbilityManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import com.rednetty.server.core.mechanics.world.mobs.combat.CombatFeedbackManager;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.core.mechanics.world.mobs.core.EliteMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Enhanced Elite System - Main coordination hub for professional strategic combat
 * 
 * This system integrates:
 * - Advanced ability management with telegraphing
 * - Professional combat feedback and warnings
 * - Strategic AI decision making
 * - Dynamic difficulty scaling
 * - Player skill recognition and adaptation
 * - Professional visual and audio design
 */
public class EnhancedEliteSystem {
    
    private static EnhancedEliteSystem instance;
    private static final Logger LOGGER = YakRealms.getInstance().getLogger();
    
    // Core system managers
    private final EliteAbilityManager abilityManager;
    private final CombatFeedbackManager feedbackManager;
    
    // Active elite tracking
    private final Map<UUID, EliteSystemData> activeElites = new ConcurrentHashMap<>();
    
    // Performance metrics
    private final Map<UUID, PlayerSkillData> playerSkillData = new ConcurrentHashMap<>();
    
    // System configuration
    private boolean systemEnabled = true;
    private double globalDifficultyModifier = 1.0;
    
    private EnhancedEliteSystem() {
        this.abilityManager = EliteAbilityManager.getInstance();
        this.feedbackManager = CombatFeedbackManager.getInstance();
        
        startSystemManagementTask();
    }
    
    public static EnhancedEliteSystem getInstance() {
        if (instance == null) {
            instance = new EnhancedEliteSystem();
        }
        return instance;
    }
    
    // ==================== ELITE LIFECYCLE MANAGEMENT ====================
    
    /**
     * Register a new elite mob with the enhanced system
     */
    public void registerElite(CustomMob mob, EliteBehaviorArchetype archetype) {
        if (!systemEnabled || mob == null || archetype == null) {
            return;
        }
        
        UUID mobId = mob.getEntity().getUniqueId();
        
        try {
            // Initialize ability system
            abilityManager.initializeMobAbilities(mob, archetype);
            
            // Create system tracking data
            EliteSystemData systemData = new EliteSystemData(mob, archetype);
            activeElites.put(mobId, systemData);
            
            // Set archetype metadata for other systems
            mob.getEntity().setMetadata("elite_archetype", 
                new org.bukkit.metadata.FixedMetadataValue(YakRealms.getInstance(), archetype.name()));
            
            
        } catch (Exception e) {
            LOGGER.warning("[EnhancedEliteSystem] Failed to register elite: " + e.getMessage());
        }
    }
    
    /**
     * Process elite AI and abilities for strategic combat
     */
    public void processEliteCombat(CustomMob mob, List<Player> nearbyPlayers) {
        if (!systemEnabled || mob == null || nearbyPlayers.isEmpty()) {
            return;
        }
        
        UUID mobId = mob.getEntity().getUniqueId();
        EliteSystemData systemData = activeElites.get(mobId);
        
        if (systemData == null) {
            return; // Elite not registered with enhanced system
        }
        
        try {
            // Update combat metrics
            systemData.updateCombatMetrics(nearbyPlayers);
            
            // Process abilities with professional timing and feedback
            abilityManager.processAbilities(mob, nearbyPlayers);
            
            // Handle player skill recognition and adaptation
            adaptToPlayerSkill(mob, nearbyPlayers, systemData);
            
            // Update feedback systems
            updateCombatFeedback(mob, nearbyPlayers, systemData);
            
        } catch (Exception e) {
            LOGGER.warning("[EnhancedEliteSystem] Error processing elite combat: " + e.getMessage());
        }
    }
    
    /**
     * Handle elite damage with enhanced feedback
     */
    public void handleEliteDamage(CustomMob mob, double damage, LivingEntity attacker) {
        UUID mobId = mob.getEntity().getUniqueId();
        EliteSystemData systemData = activeElites.get(mobId);
        
        if (systemData == null || !(attacker instanceof Player)) {
            return;
        }
        
        Player player = (Player) attacker;
        
        // Track player performance
        updatePlayerSkillData(player, "damage_dealt", damage);
        
        // Check for ability interruption opportunities
        if (abilityManager.isUsingAbility(mobId)) {
            double interruptChance = calculateInterruptChance(player, damage, mob.getEntity().getMaxHealth());
            
            if (Math.random() < interruptChance) {
                abilityManager.interruptAbilities(mob, player);
                feedbackManager.sendCounterplaySuccess(player, "Ability interruption");
                updatePlayerSkillData(player, "interrupts", 1);
            }
        }
        
        // Update system data
        systemData.recordDamage(damage, player);
    }
    
    /**
     * Clean up elite data when mob is removed
     */
    public void unregisterElite(UUID mobId) {
        EliteSystemData systemData = activeElites.remove(mobId);
        
        if (systemData != null) {
            abilityManager.cleanupMobData(mobId);
        }
    }
    
    // ==================== SKILL ADAPTATION SYSTEM ====================
    
    private void adaptToPlayerSkill(CustomMob mob, List<Player> players, EliteSystemData systemData) {
        if (players.isEmpty()) return;
        
        // Calculate average player skill level
        double avgSkillLevel = players.stream()
            .mapToDouble(this::getPlayerSkillLevel)
            .average()
            .orElse(1.0);
        
        // Adjust elite behavior based on player skill
        systemData.setSkillAdaptation(avgSkillLevel);
        
        // Modify ability chances and timing based on skill
        if (avgSkillLevel > 1.5) {
            // Advanced players - more frequent abilities, shorter telegraphs
            systemData.setAbilityChanceMultiplier(1.3);
            systemData.setTelegraphReduction(0.8);
        } else if (avgSkillLevel < 0.7) {
            // Newer players - fewer abilities, longer telegraphs
            systemData.setAbilityChanceMultiplier(0.7);
            systemData.setTelegraphReduction(1.2);
        }
    }
    
    private double getPlayerSkillLevel(Player player) {
        PlayerSkillData skillData = playerSkillData.get(player.getUniqueId());
        if (skillData == null) {
            return 1.0; // Default skill level
        }
        
        // Calculate skill based on various metrics
        double dodgeRate = skillData.getDodgeSuccessRate();
        double interruptRate = skillData.getInterruptSuccessRate();
        double averageReactionTime = skillData.getAverageReactionTime();
        
        // Combine metrics into overall skill level
        double skillLevel = 1.0;
        skillLevel += Math.max(0, dodgeRate - 0.5); // Bonus for >50% dodge rate
        skillLevel += Math.max(0, interruptRate - 0.2); // Bonus for >20% interrupt rate
        skillLevel -= Math.max(0, (averageReactionTime - 2.0) * 0.1); // Penalty for slow reactions
        
        return Math.max(0.3, Math.min(2.0, skillLevel)); // Clamp between 0.3 and 2.0
    }
    
    private void updatePlayerSkillData(Player player, String metric, double value) {
        PlayerSkillData skillData = playerSkillData.computeIfAbsent(
            player.getUniqueId(), k -> new PlayerSkillData(player));
        skillData.updateMetric(metric, value);
    }
    
    // ==================== COMBAT FEEDBACK COORDINATION ====================
    
    private void updateCombatFeedback(CustomMob mob, List<Player> players, EliteSystemData systemData) {
        // This method coordinates feedback with other systems
        // Most specific feedback is handled directly by the ability system
        // This handles general elite state changes and environmental feedback
        
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        
        // Notify of critical health state
        if (healthPercent <= 0.25 && !systemData.hasSentLowHealthWarning()) {
            players.forEach(player -> 
                feedbackManager.handleEliteStateChange(player, mob, "CRITICAL STATE", 
                    "increased aggression and new abilities!"));
            systemData.setSentLowHealthWarning(true);
        }
    }
    
    private double calculateInterruptChance(Player player, double damage, double maxHealth) {
        double basechance = 0.15; // 15% base chance
        
        // Higher damage = higher interrupt chance
        double damageModifier = Math.min(1.0, damage / (maxHealth * 0.2));
        
        // Player skill modifier
        double skillLevel = getPlayerSkillLevel(player);
        double skillModifier = Math.min(2.0, skillLevel);
        
        return basechance * damageModifier * skillModifier;
    }
    
    // ==================== SYSTEM MANAGEMENT ====================
    
    private void startSystemManagementTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!systemEnabled) return;
                
                try {
                    // Clean up data for removed mobs
                    cleanupInactiveElites();
                    
                    // Update player skill data
                    updatePlayerSkillAnalytics();
                    
                    // Adjust global difficulty if needed
                    adjustGlobalDifficulty();
                    
                } catch (Exception e) {
                    LOGGER.warning("[EnhancedEliteSystem] Error in management task: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 100L, 100L); // Every 5 seconds
    }
    
    private void cleanupInactiveElites() {
        Set<UUID> toRemove = new HashSet<>();
        
        for (Map.Entry<UUID, EliteSystemData> entry : activeElites.entrySet()) {
            UUID mobId = entry.getKey();
            if (org.bukkit.Bukkit.getEntity(mobId) == null) {
                toRemove.add(mobId);
            }
        }
        
        toRemove.forEach(this::unregisterElite);
    }
    
    private void updatePlayerSkillAnalytics() {
        // Clean up offline player data
        playerSkillData.entrySet().removeIf(entry -> {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });
        
        // Update skill level calculations
        playerSkillData.values().forEach(PlayerSkillData::updateSkillLevel);
    }
    
    private void adjustGlobalDifficulty() {
        if (playerSkillData.isEmpty()) return;
        
        // Calculate server-wide average skill
        double avgServerSkill = playerSkillData.values().stream()
            .mapToDouble(PlayerSkillData::getSkillLevel)
            .average()
            .orElse(1.0);
        
        // Gradually adjust global difficulty
        double targetDifficulty = 0.5 + (avgServerSkill * 0.5); // Range: 0.5 to 1.5
        globalDifficultyModifier = lerp(globalDifficultyModifier, targetDifficulty, 0.1);
    }
    
    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
    
    // ==================== PUBLIC API ====================
    
    /**
     * Check if the enhanced system is managing a specific elite
     */
    public boolean isEnhancedElite(UUID mobId) {
        return activeElites.containsKey(mobId);
    }
    
    /**
     * Get system data for a specific elite
     */
    public EliteSystemData getEliteData(UUID mobId) {
        return activeElites.get(mobId);
    }
    
    /**
     * Get player skill data
     */
    public PlayerSkillData getPlayerSkillData(UUID playerId) {
        return playerSkillData.get(playerId);
    }
    
    /**
     * Enable/disable the enhanced system
     */
    public void setSystemEnabled(boolean enabled) {
        this.systemEnabled = enabled;
    }
    
    /**
     * Get system statistics for debugging/admin
     */
    public String getSystemStats() {
        int activeEliteCount = activeElites.size();
        int trackedPlayerCount = playerSkillData.size();
        double avgSkill = playerSkillData.values().stream()
            .mapToDouble(PlayerSkillData::getSkillLevel)
            .average()
            .orElse(1.0);
        
        return String.format("§e=== Enhanced Elite System Stats ===\n" +
                           "§fActive Elites: %d\n" +
                           "§fTracked Players: %d\n" +
                           "§fAverage Skill Level: %.2f\n" +
                           "§fGlobal Difficulty: %.2f\n" +
                           "§fSystem Enabled: %s",
                           activeEliteCount, trackedPlayerCount, avgSkill, 
                           globalDifficultyModifier, systemEnabled ? "§aYES" : "§cNO");
    }
    
    // Getters for system components
    public EliteAbilityManager getAbilityManager() { return abilityManager; }
    public CombatFeedbackManager getFeedbackManager() { return feedbackManager; }
    public double getGlobalDifficultyModifier() { return globalDifficultyModifier; }
}

// ==================== DATA CLASSES ====================

/**
 * Tracks system-specific data for each elite
 */
class EliteSystemData {
    private final CustomMob mob;
    private final EliteBehaviorArchetype archetype;
    private final long spawnTime;
    
    private boolean sentLowHealthWarning = false;
    private double skillAdaptation = 1.0;
    private double abilityChanceMultiplier = 1.0;
    private double telegraphReduction = 1.0;
    
    private double totalDamageReceived = 0;
    private final Map<UUID, Double> playerDamageContribution = new HashMap<>();
    private long lastCombatTime = System.currentTimeMillis();
    
    public EliteSystemData(CustomMob mob, EliteBehaviorArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.spawnTime = System.currentTimeMillis();
    }
    
    public void updateCombatMetrics(List<Player> players) {
        if (!players.isEmpty()) {
            lastCombatTime = System.currentTimeMillis();
        }
    }
    
    public void recordDamage(double damage, Player player) {
        totalDamageReceived += damage;
        playerDamageContribution.merge(player.getUniqueId(), damage, Double::sum);
    }
    
    public long getCombatDurationSeconds() {
        return (System.currentTimeMillis() - spawnTime) / 1000;
    }
    
    // Getters and setters
    public boolean hasSentLowHealthWarning() { return sentLowHealthWarning; }
    public void setSentLowHealthWarning(boolean sent) { this.sentLowHealthWarning = sent; }
    public double getSkillAdaptation() { return skillAdaptation; }
    public void setSkillAdaptation(double adaptation) { this.skillAdaptation = adaptation; }
    public double getAbilityChanceMultiplier() { return abilityChanceMultiplier; }
    public void setAbilityChanceMultiplier(double multiplier) { this.abilityChanceMultiplier = multiplier; }
    public double getTelegraphReduction() { return telegraphReduction; }
    public void setTelegraphReduction(double reduction) { this.telegraphReduction = reduction; }
}

/**
 * Tracks player skill metrics for adaptive difficulty
 */
class PlayerSkillData {
    private final UUID playerId;
    private final String playerName;
    
    private int dodgeAttempts = 0;
    private int dodgeSuccesses = 0;
    private int interruptAttempts = 0;
    private int interruptSuccesses = 0;
    
    private final List<Long> reactionTimes = new ArrayList<>();
    private double skillLevel = 1.0;
    private double totalDamageDealt = 0;
    
    public PlayerSkillData(Player player) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
    }
    
    public void updateMetric(String metric, double value) {
        switch (metric.toLowerCase()) {
            case "dodge_success" -> {
                dodgeAttempts++;
                dodgeSuccesses++;
            }
            case "dodge_fail" -> dodgeAttempts++;
            case "interrupt_success" -> {
                interruptAttempts++;
                interruptSuccesses++;
            }
            case "interrupt_fail" -> interruptAttempts++;
            case "reaction_time" -> reactionTimes.add((long) value);
            case "damage_dealt" -> totalDamageDealt += value;
            case "interrupts" -> interruptSuccesses++; // Direct interrupt count
        }
    }
    
    public void updateSkillLevel() {
        // Recalculate skill level based on current metrics
        double newSkillLevel = 1.0;
        
        if (dodgeAttempts > 0) {
            newSkillLevel += Math.max(0, getDodgeSuccessRate() - 0.5);
        }
        
        if (interruptAttempts > 0) {
            newSkillLevel += Math.max(0, getInterruptSuccessRate() - 0.2);
        }
        
        if (!reactionTimes.isEmpty()) {
            double avgReaction = getAverageReactionTime();
            newSkillLevel -= Math.max(0, (avgReaction - 2.0) * 0.1);
        }
        
        this.skillLevel = Math.max(0.3, Math.min(2.0, newSkillLevel));
    }
    
    public double getDodgeSuccessRate() {
        return dodgeAttempts > 0 ? (double) dodgeSuccesses / dodgeAttempts : 0.0;
    }
    
    public double getInterruptSuccessRate() {
        return interruptAttempts > 0 ? (double) interruptSuccesses / interruptAttempts : 0.0;
    }
    
    public double getAverageReactionTime() {
        return reactionTimes.isEmpty() ? 3.0 : 
               reactionTimes.stream().mapToLong(Long::longValue).average().orElse(3.0) / 1000.0;
    }
    
    public double getSkillLevel() { return skillLevel; }
    public double getTotalDamageDealt() { return totalDamageDealt; }
}