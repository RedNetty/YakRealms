package com.rednetty.server.core.mechanics.world.mobs.behaviors.config;

/**
 * Centralized configuration for Elite Ability balance and behavior.
 * This provides a single source of truth for all ability parameters,
 * making balance changes easier and more consistent.
 */
public class EliteAbilityConfig {
    
    // ==================== GLOBAL SETTINGS ====================
    
    /** Maximum abilities any elite can use per fight to prevent spam */
    public static final int MAX_ABILITIES_PER_FIGHT = 10;
    
    /** Base ability trigger chance (modified by specific conditions) */
    public static final double BASE_ABILITY_CHANCE = 0.12; // 12%
    
    /** Multiplier for ability chances when elite is low health */
    public static final double LOW_HEALTH_MULTIPLIER = 2.0; // Double chance when <30% health
    
    /** Multiplier for ability chances based on number of nearby enemies */
    public static final double MULTI_TARGET_MULTIPLIER = 0.5; // +50% chance per extra enemy
    
    // ==================== TELEGRAPH SETTINGS ====================
    
    /** Standard telegraph duration for major abilities (in ticks) */
    public static final int STANDARD_TELEGRAPH_DURATION = 24; // 1.2 seconds
    
    /** Extended telegraph for devastating abilities (in ticks) */
    public static final int EXTENDED_TELEGRAPH_DURATION = 40; // 2.0 seconds
    
    /** Quick telegraph for minor abilities (in ticks) */
    public static final int QUICK_TELEGRAPH_DURATION = 16; // 0.8 seconds
    
    // ==================== BRUTE ARCHETYPE ====================
    
    public static class Brute {
        // Ability cooldowns (in ticks)
        public static final int CHARGE_COOLDOWN = 240; // 12 seconds
        public static final int SLAM_COOLDOWN = 180; // 9 seconds
        public static final int RAGE_COOLDOWN = 600; // 30 seconds (once per fight)
        public static final int ROAR_COOLDOWN = 120; // 6 seconds
        
        // Ability trigger conditions
        public static final double CHARGE_MIN_DISTANCE = 5.0;
        public static final double CHARGE_MAX_DISTANCE = 15.0;
        public static final double CHARGE_HEALTH_THRESHOLD = 0.7; // Use when <70% health
        
        public static final double SLAM_RANGE = 6.0;
        public static final int SLAM_MIN_TARGETS = 1;
        public static final double SLAM_HEALTH_THRESHOLD = 0.8; // Use when <80% health
        
        public static final double RAGE_HEALTH_THRESHOLD = 0.25; // Use when <25% health
        public static final double RAGE_CHANCE = 0.15; // 15% chance when conditions are met
        
        // Damage multipliers
        public static final double CHARGE_DAMAGE_MULTIPLIER = 15.0; // High damage for telegraphed attack
        public static final double SLAM_DAMAGE_MULTIPLIER = 12.0; // Area damage
        public static final double RAGE_DAMAGE_BONUS = 1.5; // +50% damage when raging
        
        // Effect durations (in ticks)
        public static final int RAGE_DURATION = 400; // 20 seconds
        public static final int CHARGE_STUN_DURATION = 80; // 4 seconds
        public static final int SLAM_SLOW_DURATION = 100; // 5 seconds
    }
    
    // ==================== ELEMENTALIST ARCHETYPE ====================
    
    public static class Elementalist {
        // Ability cooldowns (in ticks)
        public static final int ELEMENTAL_ABILITY_COOLDOWN = 160; // 8 seconds
        public static final int PHASE_CHANGE_COOLDOWN = 400; // 20 seconds
        public static final int WEATHER_CONTROL_COOLDOWN = 280; // 14 seconds
        public static final int ELEMENTAL_SHIELD_COOLDOWN = 200; // 10 seconds
        
        // Ability trigger conditions
        public static final double PHASE_CHANGE_HEALTH_THRESHOLD = 0.6;
        public static final double PHASE_CHANGE_CHANCE = 0.3; // 30% when conditions are met
        
        public static final double WEATHER_HEALTH_THRESHOLD = 0.4; // Only when low health
        public static final int WEATHER_MIN_TARGETS = 3;
        public static final double WEATHER_CHANCE = 0.2; // 20% chance
        
        public static final double SHIELD_HEALTH_THRESHOLD = 0.6;
        public static final int SHIELD_MIN_THREATS = 2;
        public static final double SHIELD_CHANCE = 0.25; // 25% chance
        
        // Damage and effect values
        public static final double ELEMENTAL_DAMAGE_BASE = 8.0;
        public static final double ELEMENTAL_DAMAGE_PER_TIER = 2.0;
        public static final int ELEMENTAL_EFFECT_DURATION = 80; // 4 seconds
        
        // Range settings
        public static final double ELEMENTAL_ABILITY_RANGE = 10.0;
        public static final double WEATHER_EFFECT_RANGE = 12.0;
        public static final double SHIELD_PROTECTION_RANGE = 6.0;
    }
    
    // ==================== ASSASSIN ARCHETYPE ====================
    
    public static class Assassin {
        // Ability cooldowns (in ticks)
        public static final int STEALTH_COOLDOWN = 300; // 15 seconds
        public static final int BACKSTAB_COOLDOWN = 100; // 5 seconds
        public static final int POISON_COOLDOWN = 140; // 7 seconds
        public static final int SHADOW_STEP_COOLDOWN = 80; // 4 seconds
        
        // Ability trigger conditions
        public static final double STEALTH_HEALTH_THRESHOLD = 0.5;
        public static final double STEALTH_CHANCE = 0.2; // 20% when damaged
        
        public static final double BACKSTAB_ANGLE = 90.0; // Must be behind target
        public static final double BACKSTAB_RANGE = 4.0;
        public static final double BACKSTAB_DAMAGE_MULTIPLIER = 2.5;
        
        public static final int POISON_TARGETS_FOR_AOE = 3;
        public static final int POISON_DURATION = 120; // 6 seconds
        
        // Stealth settings
        public static final int STEALTH_DURATION = 100; // 5 seconds
        public static final double STEALTH_MOVEMENT_BONUS = 0.3; // +30% speed when stealthed
    }
    
    // ==================== GUARDIAN ARCHETYPE ====================
    
    public static class Guardian {
        // Ability cooldowns (in ticks)
        public static final int SHIELD_WALL_COOLDOWN = 200; // 10 seconds
        public static final int DAMAGE_REFLECT_COOLDOWN = 240; // 12 seconds
        public static final int AREA_DENIAL_COOLDOWN = 180; // 9 seconds
        public static final int PROTECTIVE_AURA_COOLDOWN = 300; // 15 seconds
        
        // Shield and protection values
        public static final double SHIELD_DAMAGE_REDUCTION = 0.4; // 40% damage reduction
        public static final double REFLECT_DAMAGE_PERCENTAGE = 0.3; // 30% of damage reflected
        public static final int SHIELD_WALL_DURATION = 160; // 8 seconds
        
        // Area denial settings
        public static final double AREA_DENIAL_RANGE = 8.0;
        public static final int AREA_DENIAL_DURATION = 200; // 10 seconds
        public static final double AREA_DENIAL_DAMAGE_PER_TICK = 2.0;
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Gets the ability chance based on elite health and nearby enemy count
     */
    public static double getScaledAbilityChance(double healthPercent, int nearbyEnemies, double baseChance) {
        double chance = baseChance;
        
        // Increase chance when low health
        if (healthPercent < 0.3) {
            chance *= LOW_HEALTH_MULTIPLIER;
        }
        
        // Increase chance with more enemies (diminishing returns)
        int extraEnemies = Math.max(0, nearbyEnemies - 1);
        chance += (extraEnemies * MULTI_TARGET_MULTIPLIER * baseChance);
        
        // Cap at reasonable maximum
        return Math.min(chance, 0.5); // Max 50% chance
    }
    
    /**
     * Gets damage scaled by tier
     */
    public static double getTierScaledDamage(double baseDamage, int tier) {
        return baseDamage + (tier * 2.0); // +2 damage per tier
    }
    
    /**
     * Gets effect duration scaled by tier
     */
    public static int getTierScaledDuration(int baseDuration, int tier) {
        return baseDuration + (tier * 10); // +10 ticks per tier
    }
    
    /**
     * Gets cooldown adjusted by tier (higher tiers have slightly shorter cooldowns)
     */
    public static int getTierAdjustedCooldown(int baseCooldown, int tier) {
        int reduction = Math.min(tier * 10, baseCooldown / 4); // Max 25% reduction
        return Math.max(baseCooldown - reduction, baseCooldown / 2); // Min 50% of base cooldown
    }
}