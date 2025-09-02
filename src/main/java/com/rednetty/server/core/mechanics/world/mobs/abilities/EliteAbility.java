package com.rednetty.server.core.mechanics.world.mobs.abilities;

import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Enhanced Elite Ability System - Professional strategic combat mechanics
 * 
 * This system provides:
 * - Clear telegraphing with warning phases
 * - Strategic counterplay opportunities
 * - Balanced damage and timing
 * - Professional visual and audio feedback
 * - Scalable difficulty based on elite tier
 */
public abstract class EliteAbility {
    
    protected final String abilityId;
    protected final String displayName;
    protected final AbilityType type;
    protected final int baseCooldown;
    protected final int telegraphDuration;
    protected final double baseChance;
    
    // State tracking
    protected long lastUsed = 0;
    protected boolean isCharging = false;
    protected boolean isExecuting = false;
    
    /**
     * Types of elite abilities for categorization and balancing
     */
    public enum AbilityType {
        OFFENSIVE,      // Direct damage abilities
        DEFENSIVE,      // Protection and mitigation abilities  
        UTILITY,        // Movement, stealth, terrain modification
        ULTIMATE        // High-impact, long-cooldown abilities
    }
    
    /**
     * Priority levels for ability execution
     */
    public enum AbilityPriority {
        LOW(1),
        NORMAL(2), 
        HIGH(3),
        CRITICAL(4);
        
        private final int value;
        AbilityPriority(int value) { this.value = value; }
        public int getValue() { return value; }
    }
    
    protected EliteAbility(String abilityId, String displayName, AbilityType type, 
                          int baseCooldown, int telegraphDuration, double baseChance) {
        this.abilityId = abilityId;
        this.displayName = displayName;
        this.type = type;
        this.baseCooldown = baseCooldown;
        this.telegraphDuration = telegraphDuration;
        this.baseChance = baseChance;
    }
    
    // ==================== CORE ABILITY INTERFACE ====================
    
    /**
     * Check if this ability can be used right now
     */
    public boolean canUse(CustomMob mob, List<Player> targets, long currentTick) {
        if (isCharging || isExecuting) return false;
        if (currentTick - lastUsed < getScaledCooldown(mob.getTier())) return false;
        if (!meetsPrerequisites(mob, targets)) return false;
        
        return true;
    }
    
    /**
     * Calculate the actual chance this ability should trigger
     */
    public double getTriggerChance(CustomMob mob, List<Player> targets, CombatContext context) {
        double chance = baseChance;
        
        // Scale by health percentage (more desperate = higher chance)
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        if (healthPercent < 0.3) {
            chance *= 2.0; // Double chance when low health
        } else if (healthPercent < 0.6) {
            chance *= 1.5; // 50% increase when moderately damaged
        }
        
        // Scale by number of targets
        if (targets.size() >= 3) {
            chance *= 1.4; // More targets = more likely to use AoE abilities
        } else if (targets.size() == 1 && type == AbilityType.OFFENSIVE) {
            chance *= 1.2; // Slightly more likely to use single-target abilities on isolated players
        }
        
        // Apply ability-specific scaling
        return applyContextualScaling(chance, mob, targets, context);
    }
    
    /**
     * Start the ability execution process with telegraphing
     */
    public final boolean execute(CustomMob mob, List<Player> targets, CombatContext context) {
        if (!canUse(mob, targets, mob.getEntity().getTicksLived())) {
            return false;
        }
        
        lastUsed = mob.getEntity().getTicksLived();
        isCharging = true;
        
        // Start telegraph phase
        startTelegraph(mob, targets, context);
        
        // Schedule actual execution after telegraph delay
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            com.rednetty.server.YakRealms.getInstance(),
            () -> {
                if (mob.isValid() && isCharging) {
                    isCharging = false;
                    isExecuting = true;
                    
                    executeAbility(mob, targets, context);
                    
                    // Brief cooldown before next ability can start
                    org.bukkit.Bukkit.getScheduler().runTaskLater(
                        com.rednetty.server.YakRealms.getInstance(),
                        () -> isExecuting = false,
                        10L // 0.5 second execution window
                    );
                }
            },
            telegraphDuration
        );
        
        return true;
    }
    
    // ==================== ABSTRACT METHODS - IMPLEMENT IN SUBCLASSES ====================
    
    /**
     * Check ability-specific prerequisites (distance, positioning, etc.)
     */
    protected abstract boolean meetsPrerequisites(CustomMob mob, List<Player> targets);
    
    /**
     * Apply contextual scaling to trigger chance (archetype-specific bonuses, etc.)
     */
    protected abstract double applyContextualScaling(double baseChance, CustomMob mob, 
                                                    List<Player> targets, CombatContext context);
    
    /**
     * Get the priority of this ability in the given context
     */
    public abstract AbilityPriority getPriority(CustomMob mob, List<Player> targets, CombatContext context);
    
    /**
     * Create the telegraph warning phase - CRITICAL for strategic gameplay
     */
    protected abstract void startTelegraph(CustomMob mob, List<Player> targets, CombatContext context);
    
    /**
     * Execute the actual ability effect
     */
    protected abstract void executeAbility(CustomMob mob, List<Player> targets, CombatContext context);
    
    /**
     * Check if this ability can be interrupted during telegraph phase
     */
    public abstract boolean canBeInterrupted();
    
    /**
     * Handle interrupt if ability supports it
     */
    public void onInterrupt(CustomMob mob, Player interrupter) {
        if (canBeInterrupted() && isCharging) {
            isCharging = false;
            // Ability-specific interrupt handling in subclasses
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Get cooldown scaled by elite tier (higher tiers have slightly shorter cooldowns)
     */
    protected int getScaledCooldown(int tier) {
        int reduction = Math.min(tier * 5, baseCooldown / 4); // Max 25% reduction
        return Math.max(baseCooldown - reduction, baseCooldown / 2); // Min 50% of base
    }
    
    /**
     * Get damage scaled by elite tier and ability type
     */
    protected double getScaledDamage(double baseDamage, int tier) {
        double tierMultiplier = 1.0 + (tier * 0.2); // +20% per tier
        double typeMultiplier = switch (type) {
            case OFFENSIVE -> 1.0;
            case ULTIMATE -> 1.5;
            case DEFENSIVE, UTILITY -> 0.7; // Lower damage for non-offensive abilities
        };
        return baseDamage * tierMultiplier * typeMultiplier;
    }
    
    /**
     * Get effect duration scaled by tier
     */
    protected int getScaledDuration(int baseDuration, int tier) {
        return baseDuration + (tier * 10); // +0.5 seconds per tier
    }
    
    /**
     * Get ability range scaled by tier
     */
    protected double getScaledRange(double baseRange, int tier) {
        return baseRange + (tier * 0.5); // +0.5 blocks per tier
    }
    
    /**
     * Create standardized warning message for players
     */
    protected void notifyPlayersOfTelegraph(List<Player> targets, String warningMessage, int durationTicks) {
        targets.forEach(player -> {
            com.rednetty.server.utils.ui.ActionBarUtil.addUniqueTemporaryMessage(
                player, 
                "§c§l⚠ " + displayName + " §e" + warningMessage + " (" + (durationTicks/20) + "s) ⚠",
                durationTicks
            );
        });
    }
    
    // ==================== GETTERS ====================
    
    public String getAbilityId() { return abilityId; }
    public String getDisplayName() { return displayName; }
    public AbilityType getType() { return type; }
    public boolean isCharging() { return isCharging; }
    public boolean isExecuting() { return isExecuting; }
    public long getLastUsed() { return lastUsed; }
    
    /**
     * Get a description of what this ability does for debugging/admin purposes
     */
    public abstract String getDescription();
    
    /**
     * Get telegraphed warning text shown to players
     */
    public abstract String getTelegraphMessage();
}