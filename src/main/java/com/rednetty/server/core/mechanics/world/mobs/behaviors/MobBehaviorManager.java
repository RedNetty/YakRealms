package com.rednetty.server.core.mechanics.world.mobs.behaviors;

import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Manages mob behaviors for custom mobs.
 * Provides a clean way to add, remove, and execute behaviors without tight coupling.
 */
public class MobBehaviorManager {
    
    private static final Logger LOGGER = Logger.getLogger(MobBehaviorManager.class.getName());
    
    // Map of mob unique ID to their active behaviors
    private final Map<String, List<MobBehavior>> mobBehaviors = new ConcurrentHashMap<>();
    
    // Registry of available behavior types
    private final Map<String, Class<? extends MobBehavior>> behaviorRegistry = new ConcurrentHashMap<>();
    
    /** Cache of instantiated behaviors for performance optimization */
    private final Map<String, MobBehavior> behaviorInstances = new ConcurrentHashMap<>();
    
    /** Statistics tracking for performance monitoring */
    private final Map<String, AtomicLong> behaviorExecutionStats = new ConcurrentHashMap<>();
    
    private static volatile MobBehaviorManager instance;
    
    private MobBehaviorManager() {
        // Private constructor for singleton
    }
    
    public static MobBehaviorManager getInstance() {
        if (instance == null) {
            synchronized (MobBehaviorManager.class) {
                if (instance == null) {
                    instance = new MobBehaviorManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Registers a behavior type with default settings (no pre-instantiation).
     * 
     * @param behaviorId The unique identifier for this behavior type
     * @param behaviorClass The class implementing the MobBehavior interface
     */
    public void registerBehaviorType(String behaviorId, Class<? extends MobBehavior> behaviorClass) {
        registerBehaviorType(behaviorId, behaviorClass, false);
    }
    
    /**
     * Registers a behavior type with comprehensive validation and caching support.
     * 
     * @param behaviorId The unique identifier for this behavior type (case-insensitive)
     * @param behaviorClass The class implementing the MobBehavior interface
     * @param preInstantiate Whether to pre-instantiate the behavior for performance
     * @throws IllegalArgumentException if the parameters are invalid
     * @throws RuntimeException if the behavior class cannot be instantiated
     */
    public void registerBehaviorType(String behaviorId, Class<? extends MobBehavior> behaviorClass, boolean preInstantiate) {
        // Input validation
        if (behaviorId == null || behaviorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Behavior ID cannot be null or empty");
        }
        if (behaviorClass == null) {
            throw new IllegalArgumentException("Behavior class cannot be null");
        }
        
        String normalizedId = behaviorId.toLowerCase().trim();
        
        // Validate behavior class structure
        try {
            behaviorClass.getDeclaredConstructor(); // Ensure default constructor exists
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Behavior class must have a default constructor: " + behaviorClass.getName());
        }
        
        // Test instantiation
        try {
            MobBehavior testInstance = behaviorClass.getDeclaredConstructor().newInstance();
            if (testInstance.getBehaviorId() == null) {
                throw new IllegalArgumentException("Behavior class must return a valid behavior ID");
            }
            
            // Pre-instantiate if requested for performance
            if (preInstantiate) {
                behaviorInstances.put(normalizedId, testInstance);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate behavior class " + behaviorClass.getName() + ": " + e.getMessage(), e);
        }
        
        // Register the behavior
        behaviorRegistry.put(normalizedId, behaviorClass);
        behaviorExecutionStats.put(normalizedId, new AtomicLong(0));
        
        // Registered mob behavior: [behavior details silenced for cleaner startup]
    }
    
    /**
     * Create and apply a behavior to a mob
     */
    public boolean applyBehavior(CustomMob mob, String behaviorId) {
        if (mob == null || behaviorId == null) {
            return false;
        }
        
        try {
            Class<? extends MobBehavior> behaviorClass = behaviorRegistry.get(behaviorId.toLowerCase());
            if (behaviorClass == null) {
                LOGGER.warning("Unknown behavior type: " + behaviorId);
                return false;
            }
            
            MobBehavior behavior = behaviorClass.getDeclaredConstructor().newInstance();
            
            if (!behavior.canApplyTo(mob)) {
                LOGGER.fine("Behavior " + behaviorId + " cannot be applied to mob " + mob.getUniqueMobId());
                return false;
            }
            
            return applyBehavior(mob, behavior);
            
        } catch (Exception e) {
            LOGGER.severe("Failed to create behavior " + behaviorId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Apply a behavior instance to a mob
     */
    public boolean applyBehavior(CustomMob mob, MobBehavior behavior) {
        if (mob == null || behavior == null) {
            return false;
        }
        
        try {
            String mobId = mob.getUniqueMobId();
            List<MobBehavior> behaviors = mobBehaviors.computeIfAbsent(mobId, k -> new ArrayList<>());
            
            // Check if behavior is already applied
            if (behaviors.stream().anyMatch(b -> b.getBehaviorId().equals(behavior.getBehaviorId()))) {
                LOGGER.fine("Behavior " + behavior.getBehaviorId() + " already applied to mob " + mobId);
                return false;
            }
            
            behavior.onApply(mob);
            behaviors.add(behavior);
            
            // Sort by priority (higher priority first)
            behaviors.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
            
            LOGGER.fine("Applied behavior " + behavior.getBehaviorId() + " to mob " + mobId);
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to apply behavior to mob " + mob.getUniqueMobId() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove a behavior from a mob
     */
    public boolean removeBehavior(CustomMob mob, String behaviorId) {
        if (mob == null || behaviorId == null) {
            return false;
        }
        
        try {
            String mobId = mob.getUniqueMobId();
            List<MobBehavior> behaviors = mobBehaviors.get(mobId);
            
            if (behaviors == null || behaviors.isEmpty()) {
                return false;
            }
            
            boolean removed = behaviors.removeIf(behavior -> {
                if (behavior.getBehaviorId().equals(behaviorId)) {
                    try {
                        behavior.onRemove(mob);
                        return true;
                    } catch (Exception e) {
                        LOGGER.warning("Error removing behavior " + behaviorId + ": " + e.getMessage());
                        return true; // Remove it anyway
                    }
                }
                return false;
            });
            
            if (behaviors.isEmpty()) {
                mobBehaviors.remove(mobId);
            }
            
            if (removed) {
                LOGGER.fine("Removed behavior " + behaviorId + " from mob " + mobId);
            }
            
            return removed;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to remove behavior from mob " + mob.getUniqueMobId() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove all behaviors from a mob
     */
    public void removeAllBehaviors(CustomMob mob) {
        if (mob == null) {
            return;
        }
        
        try {
            String mobId = mob.getUniqueMobId();
            List<MobBehavior> behaviors = mobBehaviors.remove(mobId);
            
            if (behaviors != null) {
                for (MobBehavior behavior : behaviors) {
                    try {
                        behavior.onRemove(mob);
                    } catch (Exception e) {
                        LOGGER.warning("Error removing behavior " + behavior.getBehaviorId() + ": " + e.getMessage());
                    }
                }
                LOGGER.fine("Removed all behaviors from mob " + mobId);
            }
            
        } catch (Exception e) {
            LOGGER.severe("Failed to remove all behaviors from mob " + mob.getUniqueMobId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Execute tick behaviors for a mob
     */
    public void executeTick(CustomMob mob) {
        if (mob == null) {
            return;
        }
        
        List<MobBehavior> behaviors = mobBehaviors.get(mob.getUniqueMobId());
        if (behaviors == null || behaviors.isEmpty()) {
            return;
        }
        
        for (MobBehavior behavior : behaviors) {
            try {
                if (behavior.isActive()) {
                    behavior.onTick(mob);
                }
            } catch (Exception e) {
                LOGGER.warning("Error executing tick for behavior " + behavior.getBehaviorId() + 
                        " on mob " + mob.getUniqueMobId() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Execute damage behaviors for a mob
     */
    public void executeDamage(CustomMob mob, double damage, LivingEntity attacker) {
        if (mob == null) {
            return;
        }
        
        List<MobBehavior> behaviors = mobBehaviors.get(mob.getUniqueMobId());
        if (behaviors == null || behaviors.isEmpty()) {
            return;
        }
        
        for (MobBehavior behavior : behaviors) {
            try {
                if (behavior.isActive()) {
                    behavior.onDamage(mob, damage, attacker);
                }
            } catch (Exception e) {
                LOGGER.warning("Error executing damage for behavior " + behavior.getBehaviorId() + 
                        " on mob " + mob.getUniqueMobId() + ": " + e.getMessage());
            }
        }
    }
    
    
    /**
     * Execute attack behaviors for a mob
     */
    public double executeAttack(CustomMob mob, LivingEntity target, double damage) {
        if (mob == null) {
            return damage;
        }
        
        List<MobBehavior> behaviors = mobBehaviors.get(mob.getUniqueMobId());
        if (behaviors == null || behaviors.isEmpty()) {
            return damage;
        }
        
        double modifiedDamage = damage;
        for (MobBehavior behavior : behaviors) {
            try {
                if (behavior.isActive()) {
                    modifiedDamage = behavior.onAttack(mob, target, modifiedDamage);
                }
            } catch (Exception e) {
                LOGGER.warning("Error executing attack for behavior " + behavior.getBehaviorId() + 
                        " on mob " + mob.getUniqueMobId() + ": " + e.getMessage());
            }
        }
        
        return modifiedDamage;
    }
    
    /**
     * Execute player detection behaviors for a mob
     */
    public void executePlayerDetected(CustomMob mob, Player player) {
        if (mob == null || player == null) {
            return;
        }
        
        List<MobBehavior> behaviors = mobBehaviors.get(mob.getUniqueMobId());
        if (behaviors == null || behaviors.isEmpty()) {
            return;
        }
        
        for (MobBehavior behavior : behaviors) {
            try {
                if (behavior.isActive()) {
                    behavior.onPlayerDetected(mob, player);
                }
            } catch (Exception e) {
                LOGGER.warning("Error executing player detection for behavior " + behavior.getBehaviorId() + 
                        " on mob " + mob.getUniqueMobId() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Get all behaviors for a mob
     */
    public List<MobBehavior> getBehaviors(CustomMob mob) {
        if (mob == null) {
            return Collections.emptyList();
        }
        
        List<MobBehavior> behaviors = mobBehaviors.get(mob.getUniqueMobId());
        return behaviors != null ? new ArrayList<>(behaviors) : Collections.emptyList();
    }
    
    /**
     * Check if a mob has a specific behavior
     */
    public boolean hasBehavior(CustomMob mob, String behaviorId) {
        if (mob == null || behaviorId == null) {
            return false;
        }
        
        List<MobBehavior> behaviors = mobBehaviors.get(mob.getUniqueMobId());
        return behaviors != null && behaviors.stream()
                .anyMatch(b -> b.getBehaviorId().equals(behaviorId));
    }
    
    /**
     * Get diagnostic information
     */
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();
        diagnostics.put("totalMobsWithBehaviors", mobBehaviors.size());
        diagnostics.put("registeredBehaviorTypes", behaviorRegistry.size());
        diagnostics.put("behaviorTypes", new ArrayList<>(behaviorRegistry.keySet()));
        
        int totalBehaviors = mobBehaviors.values().stream()
                .mapToInt(List::size)
                .sum();
        diagnostics.put("totalActiveBehaviors", totalBehaviors);
        
        return diagnostics;
    }
    
    /**
     * Provides detailed behavior execution statistics.
     * 
     * @return A map of behavior execution statistics
     */
    public Map<String, Long> getBehaviorStatistics() {
        Map<String, Long> stats = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : behaviorExecutionStats.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().get());
        }
        return stats;
    }
    
    /**
     * Performs a basic system health check.
     * 
     * @return true if the system appears to be functioning normally
     */
    private boolean isSystemHealthy() {
        try {
            // Check if core data structures are accessible
            mobBehaviors.size();
            behaviorRegistry.size();
            behaviorInstances.size();
            
            // System is healthy if no exceptions occur
            return true;
        } catch (Exception e) {
            LOGGER.warning("System health check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Clears all cached behavior instances and statistics.
     * 
     * <p>This method is useful for development and testing scenarios where behavior
     * implementations may be reloaded. It clears all caches but preserves registrations.
     * 
     * @implNote This method is thread-safe but may cause temporary performance degradation
     */
    public void clearCaches() {
        behaviorInstances.clear();
        for (AtomicLong stat : behaviorExecutionStats.values()) {
            stat.set(0);
        }
        LOGGER.info("Behavior caches and statistics cleared");
    }
    
    /**
     * Gets the total number of behavior executions across all behaviors.
     * 
     * @return The total execution count
     */
    public long getTotalBehaviorExecutions() {
        return behaviorExecutionStats.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
    }
    
    /**
     * Checks if a specific behavior type is registered.
     * 
     * @param behaviorId The behavior ID to check (case-insensitive)
     * @return true if the behavior is registered
     */
    public boolean isBehaviorRegistered(String behaviorId) {
        return behaviorId != null && behaviorRegistry.containsKey(behaviorId.toLowerCase().trim());
    }
    
    /**
     * Gets a set of all registered behavior IDs.
     * 
     * @return An unmodifiable set of behavior IDs
     */
    public Set<String> getRegisteredBehaviorIds() {
        return Collections.unmodifiableSet(behaviorRegistry.keySet());
    }
    
    /**
     * Execute death behaviors for a mob when it dies.
     * 
     * @param mob The mob that died
     * @param killer The entity that killed the mob (null for environmental death)
     */
    public void executeDeath(CustomMob mob, LivingEntity killer) {
        if (mob == null) return;
        
        String mobId = mob.getUniqueMobId();
        List<MobBehavior> behaviors = mobBehaviors.get(mobId);
        
        if (behaviors != null && !behaviors.isEmpty()) {
            try {
                // Execute death behaviors in priority order
                for (MobBehavior behavior : behaviors) {
                    try {
                        behavior.onDeath(mob, killer);
                        
                        // Track execution for statistics
                        behaviorExecutionStats.computeIfAbsent(behavior.getBehaviorId(), k -> new AtomicLong(0))
                                .incrementAndGet();
                                
                        LOGGER.fine("Executed death behavior " + behavior.getBehaviorId() + " for mob " + mobId);
                        
                    } catch (Exception e) {
                        LOGGER.warning("Error executing death behavior " + behavior.getBehaviorId() + 
                                     " for mob " + mobId + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to execute death behaviors for mob " + mobId + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Removes all behaviors from a mob during cleanup or death.
     * 
     * @param mob The mob to remove behaviors from
     */
    public void removeBehaviors(CustomMob mob) {
        if (mob == null) return;
        
        String mobId = mob.getUniqueMobId();
        List<MobBehavior> behaviors = mobBehaviors.get(mobId);
        
        if (behaviors != null && !behaviors.isEmpty()) {
            try {
                // Create a copy to avoid concurrent modification
                List<MobBehavior> behaviorsToRemove = new ArrayList<>(behaviors);
                
                for (MobBehavior behavior : behaviorsToRemove) {
                    try {
                        behavior.onRemove(mob);
                        LOGGER.fine("Removed behavior " + behavior.getBehaviorId() + " from mob " + mobId);
                    } catch (Exception e) {
                        LOGGER.warning("Error removing behavior " + behavior.getBehaviorId() + " from mob " + mobId + ": " + e.getMessage());
                    }
                }
                
                // Clear the behavior list
                mobBehaviors.remove(mobId);
                LOGGER.fine("Cleared all behaviors for mob " + mobId);
                
            } catch (Exception e) {
                LOGGER.severe("Failed to remove behaviors from mob " + mobId + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Initialize the behavior system and register default behaviors.
     * 
     * <p>This method registers all known behavior implementations with the system.
     * It should be called during plugin initialization to ensure behaviors are available.
     * 
     * @implNote This method is thread-safe and idempotent
     */
    public void initializeDefaultBehaviors() {
        try {
            // Register core behavior systems
            registerBehaviorType("environmental_adaptation", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.EnvironmentalAdaptationBehavior.class, 
                true);
            
            registerBehaviorType("elite_combat", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.EliteCombatBehavior.class, 
                true);
            
            // Register elite archetype behaviors
            registerBehaviorType("brute_archetype", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.BruteBehavior.class, 
                true);
            
            registerBehaviorType("assassin_archetype", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.AssassinBehavior.class, 
                true);
            
            registerBehaviorType("guardian_archetype", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.GuardianBehavior.class, 
                true);
            
            registerBehaviorType("necromancer_archetype", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.NecromancerBehavior.class, 
                true);
                
            registerBehaviorType("warmaster_archetype", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.WarmasterBehavior.class, 
                true);
            
            registerBehaviorType("elementalist_archetype", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.ElementalistBehavior.class, 
                true);
            
            registerBehaviorType("void_walker_archetype", 
                com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.VoidWalkerBehavior.class, 
                true);
            
            // Register placeholder behaviors for future implementations
            registerPlaceholderBehavior("necromancer_archetype", "Necromancer archetype behavior");
            registerPlaceholderBehavior("berserker_archetype", "Berserker archetype behavior");
            registerPlaceholderBehavior("shapeshifter_archetype", "Shapeshifter archetype behavior");
            registerPlaceholderBehavior("warmaster_archetype", "Warmaster archetype behavior");
            registerPlaceholderBehavior("cosmic_entity_archetype", "Cosmic Entity archetype behavior");
            registerPlaceholderBehavior("ancient_one_archetype", "Ancient One archetype behavior");
            registerPlaceholderBehavior("avatar_of_destruction_archetype", "Avatar of Destruction archetype behavior");
            
            // Register other system behaviors
            registerPlaceholderBehavior("fire_immunity", "Fire immunity behavior");
            registerPlaceholderBehavior("flight_control", "Flight control behavior");
            registerPlaceholderBehavior("world_boss_mechanics", "World boss mechanics behavior");
            registerPlaceholderBehavior("advanced_ai", "Advanced AI behavior");
            
            LOGGER.info("Initialized " + behaviorRegistry.size() + " behaviors including " + 
                       "5 elite archetype implementations and " + (behaviorRegistry.size() - 9) + " placeholders");
            
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize default behaviors: " + e.getMessage());
        }
    }
    
    /**
     * Registers a placeholder behavior that logs when it would be executed.
     * This prevents errors when the system tries to apply behaviors that aren't implemented yet.
     */
    private void registerPlaceholderBehavior(String behaviorId, String description) {
        try {
            // Create an anonymous behavior class
            MobBehavior placeholder = new MobBehavior() {
                @Override
                public String getBehaviorId() { return behaviorId; }

                @Override
                public boolean canApplyTo(CustomMob mob) {
                    return true; // Allow placeholder behaviors to be applied for testing/logging
                }

                @Override
                public int getPriority() { return 100; }

                @Override
                public boolean isActive() {
                    return MobBehavior.super.isActive();
                }

                @Override
                public void onApply(CustomMob mob) {
                    LOGGER.info("Applied placeholder behavior '" + behaviorId + "' to mob " + mob.getUniqueMobId());
                }
                
                @Override
                public void onRemove(CustomMob mob) {
                    LOGGER.info("Removed placeholder behavior '" + behaviorId + "' from mob " + mob.getUniqueMobId());
                }
                
                @Override
                public void onTick(CustomMob mob) {
                    // Placeholder - no actual logic
                }
                
                @Override
                public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
                    // Placeholder - no actual logic
                }
                
                @Override
                public void onDeath(CustomMob mob, LivingEntity killer) {
                    // Placeholder - no actual logic
                }
                
                @Override
                public double onAttack(CustomMob mob, LivingEntity target, double damage) {
                    // Placeholder - return original damage
                    return damage;
                }
                
                @Override
                public void onPlayerDetected(CustomMob mob, Player player) {
                    // Placeholder - no actual logic
                }
            };
            
            behaviorInstances.put(behaviorId.toLowerCase().trim(), placeholder);
            // Registered placeholder behavior: [details silenced]
            
        } catch (Exception e) {
            LOGGER.warning("Failed to register placeholder behavior " + behaviorId + ": " + e.getMessage());
        }
    }
}