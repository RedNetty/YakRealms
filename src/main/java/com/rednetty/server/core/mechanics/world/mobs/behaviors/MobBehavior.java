package com.rednetty.server.core.mechanics.world.mobs.behaviors;

import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Comprehensive behavior interface for Paper Spigot 1.21.8 mob customization system.
 * 
 * <p>This interface defines the contract for all mob behaviors, enabling a modular and
 * extensible approach to mob functionality. Behaviors can implement complex mechanics
 * such as custom AI, special abilities, environmental interactions, and combat modifications.
 * 
 * <h3>Behavior Lifecycle:</h3>
 * <ol>
 *   <li><strong>Registration:</strong> Behavior classes are registered with the MobBehaviorManager</li>
 *   <li><strong>Application:</strong> Behaviors are applied to specific mobs via {@link #onApply(CustomMob)}</li>
 *   <li><strong>Execution:</strong> Behaviors respond to various mob events (tick, damage, death, etc.)</li>
 *   <li><strong>Removal:</strong> Behaviors are cleaned up via {@link #onRemove(CustomMob)} when no longer needed</li>
 * </ol>
 * 
 * <h3>Event System:</h3>
 * <p>Behaviors respond to different mob events in priority order:
 * <ul>
 *   <li><strong>Tick Events:</strong> Continuous updates for ongoing behavior logic</li>
 *   <li><strong>Combat Events:</strong> Damage taken, damage dealt, and death scenarios</li>
 *   <li><strong>Interaction Events:</strong> Player detection and environmental triggers</li>
 * </ul>
 * 
 * <h3>Implementation Guidelines:</h3>
 * <ul>
 *   <li><strong>Thread Safety:</strong> All methods must be thread-safe as they may be called concurrently</li>
 *   <li><strong>Performance:</strong> Tick methods should be optimized for minimal overhead</li>
 *   <li><strong>Error Handling:</strong> Robust error handling to prevent system-wide failures</li>
 *   <li><strong>State Management:</strong> Proper cleanup of resources in {@link #onRemove(CustomMob)}</li>
 * </ul>
 * 
 * <h3>Usage Example:</h3>
 * <pre><code>
 * public class FireImmunityBehavior implements MobBehavior {
 *     {@code @Override}
 *     public String getBehaviorId() {
 *         return "fire_immunity";
 *     }
 * 
 *     {@code @Override}
 *     public void onApply(CustomMob mob) {
 *         // Grant fire resistance potion effect
 *         LivingEntity entity = mob.getEntity();
 *         if (entity != null) {
 *             entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
 *         }
 *     }
 * 
 *     {@code @Override}
 *     public boolean canApplyTo(CustomMob mob) {
 *         return mob.getType().getCategory() == MobType.MobCategory.NETHER;
 *     }
 *     
 *     // ... other method implementations
 * }
 * </code></pre>
 * 
 * @author YakRealms Development Team
 * @version 2.0.0
 * @since 1.0.0
 * @see MobBehaviorManager for behavior management
 * @see CustomMob for mob system integration
 */
public interface MobBehavior {
    
    /**
     * Called when the behavior is first applied to a mob for initialization.
     * 
     * <p>This method is invoked immediately after behavior validation during the application
     * process. It should perform any necessary setup such as:
     * <ul>
     *   <li>Applying initial potion effects or attributes</li>
     *   <li>Registering event listeners specific to this behavior</li>
     *   <li>Initializing behavior-specific state variables</li>
     *   <li>Modifying mob appearance or equipment</li>
     * </ul>
     * 
     * <p><strong>Thread Safety:</strong> This method is called on the main thread and should
     * complete quickly to avoid blocking server operations.
     * 
     * <p><strong>Error Handling:</strong> Exceptions thrown by this method will prevent the
     * behavior from being applied and will be logged by the behavior manager.
     * 
     * @param mob The mob this behavior is being applied to (guaranteed to be non-null and valid)
     * @throws RuntimeException if initialization fails critically
     * @see MobBehaviorManager#applyBehavior(CustomMob, MobBehavior)
     * @implNote This method should be idempotent where possible
     */
    void onApply(CustomMob mob);
    
    /**
     * Called every server tick while the behavior is active for continuous updates.
     * 
     * <p>This method is invoked frequently (typically 20 times per second) and should be
     * highly optimized for performance. Common use cases include:
     * <ul>
     *   <li>Updating custom AI behaviors and pathfinding</li>
     *   <li>Managing cooldowns and timers</li>
     *   <li>Checking environmental conditions</li>
     *   <li>Updating visual effects and particles</li>
     *   <li>Managing temporary state changes</li>
     * </ul>
     * 
     * <p><strong>Performance Considerations:</strong>
     * <ul>
     *   <li>This method should complete in under 1ms to avoid server lag</li>
     *   <li>Expensive operations should be spread across multiple ticks</li>
     *   <li>Use early returns to minimize unnecessary computations</li>
     *   <li>Cache frequently accessed data to avoid repeated lookups</li>
     * </ul>
     * 
     * <p><strong>Execution Context:</strong> This method is always called on the main thread
     * with the mob's current state. The mob is guaranteed to be valid when this method is called.
     * 
     * @param mob The mob this behavior belongs to (guaranteed to be non-null and valid)
     * @throws RuntimeException if a critical error occurs (will be logged and may cause behavior removal)
     * @see #isActive() for controlling when this method is called
     * @implNote Implementations should handle edge cases gracefully to maintain system stability
     */
    void onTick(CustomMob mob);
    
    /**
     * Called when the mob takes damage, allowing behaviors to respond to combat events.
     * 
     * <p>This method is triggered whenever the mob receives damage from any source, enabling
     * behaviors to implement:
     * <ul>
     *   <li>Damage reduction or immunity mechanics</li>
     *   <li>Counter-attack behaviors and retaliation</li>
     *   <li>Special effects triggered by taking damage</li>
     *   <li>Rage or berserk modes activated by low health</li>
     *   <li>Defensive ability activation</li>
     * </ul>
     * 
     * <p><strong>Damage Information:</strong>
     * <ul>
     *   <li>The damage value represents the final amount after armor and resistance calculations</li>
     *   <li>Environmental damage (fire, fall, etc.) will have a null attacker</li>
     *   <li>Player attacks will provide the attacking player as the attacker</li>
     *   <li>Mob vs mob combat will provide the attacking mob entity</li>
     * </ul>
     * 
     * <p><strong>Execution Context:</strong> This method is called on the main thread immediately
     * after the damage event. The mob's health has already been reduced by the damage amount.
     * 
     * @param mob The mob that took damage (guaranteed to be non-null and valid)
     * @param damage The amount of damage taken (always positive)
     * @param attacker The entity that caused the damage (null for environmental damage)
     * @throws RuntimeException if a critical error occurs (will be logged)
     * @see CustomMob#handleDamage(double, LivingEntity) for damage processing integration
     * @implNote This method should not modify the damage amount directly
     */
    void onDamage(CustomMob mob, double damage, LivingEntity attacker);
    
    /**
     * Called when the mob dies, allowing behaviors to handle death-related mechanics.
     * 
     * <p>This method is invoked when the mob's health reaches zero, enabling behaviors to:
     * <ul>
     *   <li>Execute death animations and special effects</li>
     *   <li>Drop special items or rewards</li>
     *   <li>Trigger area-of-effect abilities upon death</li>
     *   <li>Spawn additional mobs or minions</li>
     *   <li>Award experience or achievements to players</li>
     *   <li>Clean up behavior-specific resources</li>
     * </ul>
     * 
     * <p><strong>Death Context:</strong>
     * <ul>
     *   <li>The mob entity is still valid but will be removed shortly after this call</li>
     *   <li>This is the last opportunity to access mob state and location</li>
     *   <li>Multiple behaviors may execute death logic in priority order</li>
     *   <li>The killer parameter identifies the entity that dealt the killing blow</li>
     * </ul>
     * 
     * <p><strong>Cleanup Responsibility:</strong> While {@link #onRemove(CustomMob)} handles
     * general cleanup, this method should handle death-specific cleanup and finalization.
     * 
     * @param mob The mob that died (guaranteed to be non-null, entity may still be valid)
     * @param killer The entity that killed the mob (null if death was from environmental causes)
     * @throws RuntimeException if a critical error occurs (will be logged)
     * @see #onRemove(CustomMob) for general behavior cleanup
     * @implNote This method should complete quickly as the server is processing a death event
     */
    void onDeath(CustomMob mob, LivingEntity killer);
    
    /**
     * Called when the mob attacks another entity, allowing damage modification and special effects.
     * 
     * <p>This method enables behaviors to modify outgoing damage and implement special attack
     * mechanics such as:
     * <ul>
     *   <li>Damage scaling based on mob state or target type</li>
     *   <li>Critical hit calculations and bonus damage</li>
     *   <li>Status effect application (poison, wither, etc.)</li>
     *   <li>Special attack animations and sound effects</li>
     *   <li>Weapon or ability-specific damage modifications</li>
     *   <li>Target-specific damage adjustments</li>
     * </ul>
     * 
     * <p><strong>Damage Modification:</strong>
     * <ul>
     *   <li>Return the original damage value if no modification is needed</li>
     *   <li>Return a modified value to increase or decrease damage output</li>
     *   <li>Return 0 to completely negate the attack</li>
     *   <li>Multiple behaviors can stack damage modifications</li>
     * </ul>
     * 
     * <p><strong>Execution Order:</strong> Behaviors are called in priority order, with each
     * receiving the damage value modified by previous behaviors in the chain.
     * 
     * @param mob The mob that is attacking (guaranteed to be non-null and valid)
     * @param target The target being attacked (guaranteed to be non-null and valid)
     * @param damage The base damage amount before behavior modifications (always non-negative)
     * @return The modified damage amount to be applied to the target
     * @throws RuntimeException if a critical error occurs (will be logged)
     * @see MobBehaviorManager#executeAttack(CustomMob, LivingEntity, double) for chaining logic
     * @implNote This method should not directly apply damage or effects to the target
     */
    double onAttack(CustomMob mob, LivingEntity target, double damage);
    
    /**
     * Called when a player enters the mob's detection range, enabling proximity-based behaviors.
     * 
     * <p>This method allows behaviors to respond to player presence with:
     * <ul>
     *   <li>Aggressive targeting and combat initiation</li>
     *   <li>Warning messages or dialogue systems</li>
     *   <li>Environmental changes (lighting, sounds, particles)</li>
     *   <li>Mob state changes (alert, defensive, hostile)</li>
     *   <li>Summoning additional mobs or allies</li>
     *   <li>Special ability activation based on player proximity</li>
     * </ul>
     * 
     * <p><strong>Detection Context:</strong>
     * <ul>
     *   <li>Players in creative or spectator mode may be excluded from detection</li>
     *   <li>Detection range is configurable per mob type and behavior</li>
     *   <li>Line-of-sight checks may apply depending on implementation</li>
     *   <li>Multiple players may trigger this method independently</li>
     * </ul>
     * 
     * <p><strong>Performance Note:</strong> This method may be called frequently when players
     * are near mobs, so implementations should be optimized for minimal overhead.
     * 
     * @param mob The mob detecting the player (guaranteed to be non-null and valid)
     * @param player The player that was detected (guaranteed to be non-null and online)
     * @throws RuntimeException if a critical error occurs (will be logged)
     * @see CustomMob#getEntity() for accessing mob entity and location data
     * @implNote Consider implementing cooldowns to prevent spam when players move in and out of range
     */
    void onPlayerDetected(CustomMob mob, Player player);
    
    /**
     * Called when the behavior is removed from a mob for cleanup and resource management.
     * 
     * <p>This method is the final lifecycle event and should handle all cleanup operations:
     * <ul>
     *   <li>Removing applied potion effects and attribute modifications</li>
     *   <li>Unregistering event listeners and scheduled tasks</li>
     *   <li>Cleaning up temporary world modifications</li>
     *   <li>Saving persistent behavior data if required</li>
     *   <li>Releasing any held resources or references</li>
     * </ul>
     * 
     * <p><strong>Removal Scenarios:</strong>
     * <ul>
     *   <li>Mob death (called after {@link #onDeath(CustomMob, LivingEntity)})</li>
     *   <li>Explicit behavior removal by administrators</li>
     *   <li>System shutdown and cleanup operations</li>
     *   <li>Behavior replacement or updates</li>
     *   <li>Error conditions requiring behavior removal</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong> This method should handle errors gracefully and
     * continue cleanup even if some operations fail. Exceptions are logged but don't
     * prevent other cleanup operations.
     * 
     * @param mob The mob this behavior is being removed from (may be invalid if mob is dead)
     * @throws RuntimeException if a critical error occurs (will be logged but won't stop removal)
     * @see #onApply(CustomMob) for the corresponding initialization method
     * @implNote This method should be idempotent and safe to call multiple times
     */
    void onRemove(CustomMob mob);
    
    /**
     * Returns the unique identifier for this behavior type.
     * 
     * <p>The behavior ID is used for:
     * <ul>
     *   <li>Registration and lookup in the behavior management system</li>
     *   <li>Preventing duplicate behavior application to the same mob</li>
     *   <li>Configuration file references and admin commands</li>
     *   <li>Logging and debugging operations</li>
     *   <li>Statistics tracking and performance monitoring</li>
     * </ul>
     * 
     * <p><strong>ID Requirements:</strong>
     * <ul>
     *   <li>Must be unique across all behavior implementations</li>
     *   <li>Should be lowercase with underscores for consistency</li>
     *   <li>Must be stable across server restarts and updates</li>
     *   <li>Should be descriptive of the behavior's primary function</li>
     * </ul>
     * 
     * <p><strong>Examples:</strong> "fire_immunity", "elite_combat", "world_boss_mechanics"
     * 
     * @return The unique behavior identifier (never null, never empty)
     * @implNote This value should be constant for the lifetime of the behavior class
     */
    String getBehaviorId();
    
    /**
     * Determines if this behavior can be applied to the specified mob.
     * 
     * <p>This validation method allows behaviors to implement compatibility checks based on:
     * <ul>
     *   <li>Mob type and category compatibility</li>
     *   <li>Tier restrictions and requirements</li>
     *   <li>Elite status and special mob configurations</li>
     *   <li>Current world or environment conditions</li>
     *   <li>Existing behavior conflicts or dependencies</li>
     *   <li>Configuration settings and permissions</li>
     * </ul>
     * 
     * <p><strong>Validation Examples:</strong>
     * <ul>
     *   <li>Fire immunity only for Nether-category mobs</li>
     *   <li>Flight behaviors only for mobs with FLIGHT ability</li>
     *   <li>Elite behaviors only for elite-tier mobs</li>
     *   <li>World boss mechanics only for T5+ mobs</li>
     * </ul>
     * 
     * <p><strong>Performance Note:</strong> This method should execute quickly as it's called
     * during behavior application. Expensive validation should be cached when possible.
     * 
     * @param mob The mob to validate compatibility against (guaranteed to be non-null)
     * @return true if the behavior is compatible and can be safely applied, false otherwise
     * @throws RuntimeException if validation encounters a critical error
     * @see MobBehaviorManager#applyBehavior(CustomMob, String) for the application process
     * @implNote This method should not modify the mob state during validation
     */
    boolean canApplyTo(CustomMob mob);
    
    /**
     * Returns the execution priority of this behavior for ordering multiple behaviors.
     * 
     * <p>Priority determines the execution order when multiple behaviors are active on the same mob:
     * <ul>
     *   <li><strong>Higher Priority (positive values):</strong> Execute first, critical behaviors</li>
     *   <li><strong>Normal Priority (0):</strong> Default execution order, most behaviors</li>
     *   <li><strong>Lower Priority (negative values):</strong> Execute last, cleanup behaviors</li>
     * </ul>
     * 
     * <p><strong>Priority Guidelines:</strong>
     * <ul>
     *   <li><strong>1000+:</strong> Critical system behaviors (damage immunity, core mechanics)</li>
     *   <li><strong>100-999:</strong> Important behaviors (combat modifiers, special abilities)</li>
     *   <li><strong>1-99:</strong> Standard behaviors (AI enhancements, visual effects)</li>
     *   <li><strong>0:</strong> Default priority for most behaviors</li>
     *   <li><strong>Negative:</strong> Cleanup and finalization behaviors</li>
     * </ul>
     * 
     * <p><strong>Impact on Execution:</strong>
     * <ul>
     *   <li>Tick processing: Higher priority behaviors tick first</li>
     *   <li>Damage modification: Higher priority modifiers applied first</li>
     *   <li>Event handling: Higher priority responses processed first</li>
     * </ul>
     * 
     * @return The behavior priority (higher values execute first, default is 0)
     * @implNote Priority should remain constant for the lifetime of the behavior instance
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Checks if this behavior is currently active and should participate in mob events.
     * 
     * <p>Inactive behaviors are skipped during event processing, allowing for:
     * <ul>
     *   <li>Conditional activation based on mob state or health</li>
     *   <li>Time-based behaviors with cooldown periods</li>
     *   <li>Environmental dependencies (day/night, weather, location)</li>
     *   <li>Player presence or absence requirements</li>
     *   <li>Temporary disabling for special game modes or events</li>
     * </ul>
     * 
     * <p><strong>Activation Examples:</strong>
     * <ul>
     *   <li>Berserk behavior only active when health is below 25%</li>
     *   <li>Day/night behaviors based on world time</li>
     *   <li>Combat behaviors only active when in combat</li>
     *   <li>Ability behaviors with cooldown timers</li>
     * </ul>
     * 
     * <p><strong>Performance Impact:</strong> This method is called before each event
     * processing cycle, so it should be highly optimized. Consider caching expensive
     * calculations and updating them periodically rather than computing on each call.
     * 
     * @return true if the behavior should participate in event processing, false to skip
     * @implNote This method should not have side effects and should be safe to call frequently
     */
    default boolean isActive() {
        return true;
    }
}