package com.rednetty.server.core.mechanics.world.mobs.behaviors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for initializing elite archetypes on mob creation.
 * 
 * This class provides seamless integration between the mob spawning system
 * and the elite archetype system, ensuring that all elite mobs get appropriate
 * archetypes assigned based on their configuration.
 */
public class EliteArchetypeInitializer {
    
    private static final Logger LOGGER = YakRealms.getInstance().getLogger();
    
    /**
     * Attempts to initialize an elite archetype for the given mob.
     * 
     * This method should be called during or immediately after mob spawning
     * to ensure proper archetype assignment and behavior initialization.
     * 
     * @param mob The elite mob to initialize
     * @param preferredArchetype Optional preferred archetype (can be null)
     * @return true if archetype was successfully assigned
     */
    public static boolean initializeEliteArchetype(CustomMob mob, EliteBehaviorArchetype preferredArchetype) {
        if (mob == null || !mob.isElite() || !mob.isValid()) {
            return false;
        }
        
        try {
            // Use the centralized manager for assignment
            boolean success = EliteArchetypeBehaviorManager.getInstance()
                    .assignArchetype(mob, preferredArchetype);
            
            if (success) {
                LOGGER.info(String.format("Elite archetype initialized for mob %s (Tier %d)", 
                    mob.getUniqueMobId(), mob.getTier()));
            } else {
                LOGGER.warning(String.format("Failed to initialize elite archetype for mob %s", 
                    mob.getUniqueMobId()));
            }
            
            return success;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error initializing elite archetype for mob " + mob.getUniqueMobId(), e);
            return false;
        }
    }
    
    /**
     * Initializes an elite archetype with automatic selection based on tier and configuration.
     * 
     * @param mob The elite mob to initialize
     * @return true if archetype was successfully assigned
     */
    public static boolean initializeEliteArchetype(CustomMob mob) {
        return initializeEliteArchetype(mob, (EliteBehaviorArchetype) null);
    }
    
    /**
     * Initializes an elite archetype with a specific archetype name.
     * 
     * @param mob The elite mob to initialize
     * @param archetypeName The name of the desired archetype
     * @return true if archetype was successfully assigned
     */
    public static boolean initializeEliteArchetype(CustomMob mob, String archetypeName) {
        if (archetypeName == null || archetypeName.trim().isEmpty()) {
            return initializeEliteArchetype(mob, (EliteBehaviorArchetype) null);
        }
        
        try {
            EliteBehaviorArchetype archetype = EliteBehaviorArchetype.valueOf(archetypeName.toUpperCase());
            return initializeEliteArchetype(mob, archetype);
        } catch (IllegalArgumentException e) {
            LOGGER.warning(String.format("Unknown archetype name '%s', using automatic selection", archetypeName));
            return initializeEliteArchetype(mob, (EliteBehaviorArchetype) null);
        }
    }
    
    /**
     * Checks if a mob has an elite archetype assigned.
     * 
     * @param mob The mob to check
     * @return true if the mob has an archetype assigned
     */
    public static boolean hasEliteArchetype(CustomMob mob) {
        if (mob == null || !mob.isElite()) {
            return false;
        }
        
        return EliteArchetypeBehaviorManager.getInstance().getArchetype(mob) != null;
    }
    
    /**
     * Gets the archetype assigned to an elite mob.
     * 
     * @param mob The elite mob
     * @return The assigned archetype, or null if none assigned
     */
    public static EliteBehaviorArchetype getEliteArchetype(CustomMob mob) {
        if (mob == null || !mob.isElite()) {
            return null;
        }
        
        return EliteArchetypeBehaviorManager.getInstance().getArchetype(mob);
    }
    
    /**
     * Forces re-initialization of an elite's archetype.
     * 
     * This can be useful for debugging or when changing elite configurations.
     * 
     * @param mob The elite mob
     * @param newArchetype The new archetype to assign
     * @return true if re-initialization was successful
     */
    public static boolean reinitializeEliteArchetype(CustomMob mob, EliteBehaviorArchetype newArchetype) {
        if (mob == null || !mob.isElite() || !mob.isValid()) {
            return false;
        }
        
        try {
            // Clean up existing archetype first
            EliteArchetypeBehaviorManager.getInstance().cleanupElite(mob);
            
            // Assign new archetype
            return initializeEliteArchetype(mob, newArchetype);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error reinitializing elite archetype", e);
            return false;
        }
    }
}