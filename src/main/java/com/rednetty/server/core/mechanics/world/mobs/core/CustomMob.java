package com.rednetty.server.core.mechanics.world.mobs.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.item.drops.DropsManager;
import com.rednetty.server.core.mechanics.item.drops.DropConfig;
import com.rednetty.server.core.mechanics.world.mobs.CritManager;
import com.rednetty.server.core.mechanics.world.mobs.MobManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehaviorManager;
import com.rednetty.server.core.mechanics.world.mobs.utils.MobUtils;
import com.rednetty.server.core.mechanics.world.holograms.HologramManager;
import com.rednetty.server.utils.ui.GradientColors;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Advanced CustomMob implementation for Paper Spigot 1.21.8 with comprehensive features.
 * 
 * <p>This class provides a complete mob management system with the following capabilities:
 * <ul>
 *   <li><strong>Modern Paper API Integration:</strong> Full utilization of Paper 1.21.8 features including PaperEntity enhancements</li>
 *   <li><strong>Adventure API:</strong> Rich text components, sound effects, and modern UI elements</li>
 *   <li><strong>Elite Mob System:</strong> Named elites with custom configurations and world boss mechanics</li>
 *   <li><strong>Thread-Safe Operations:</strong> Concurrent-safe design with volatile fields and atomic operations</li>
 *   <li><strong>Advanced Display System:</strong> Dynamic health bars, combat indicators, and hologram integration</li>
 *   <li><strong>Behavioral Framework:</strong> Extensible behavior system for custom mob mechanics</li>
 *   <li><strong>Performance Optimized:</strong> Efficient tick processing, memory management, and resource cleanup</li>
 * </ul>
 * 
 * <h3>Architecture Overview:</h3>
 * <p>The CustomMob follows a component-based architecture where different aspects of mob behavior
 * are handled by separate, loosely-coupled systems. This allows for easy extension and maintenance.</p>
 * 
 * <h3>Paper 1.21.8 Features Used:</h3>
 * <ul>
 *   <li>Entity scheduling API for better performance</li>
 *   <li>Modern item components and data components</li>
 *   <li>Enhanced entity attributes and AI systems</li>
 *   <li>Improved chunk loading and world state management</li>
 * </ul>
 * 
 * @author YakRealms Development Team
 * @version 2.0.0
 * @since 1.0.0
 * @see MobManager
 * @see MobType
 * @see MobBehaviorManager
 */
@Getter
public class CustomMob {

    // ==================== SYSTEM CONSTANTS ====================
    
    /** Random instance optimized for concurrent access */
    public static final Random RANDOM = ThreadLocalRandom.current();
    
    /** Logger instance for debugging and error reporting */
    public static final Logger LOGGER = YakRealms.getInstance().getLogger();
    
    /** Duration in milliseconds before a mob exits combat state */
    public static final long COMBAT_TIMEOUT_MS = 6500L;
    
    /** Minimum interval between hologram updates to optimize performance */
    public static final long HOLOGRAM_UPDATE_INTERVAL_MS = 50L; // 50ms = ~1 tick for smooth position updates
    
    /** Health change threshold (percentage) required to trigger hologram updates */
    public static final double HEALTH_CHANGE_THRESHOLD = 0.10;
    
    /** Maximum number of entity spawn attempts before failure */
    public static final int MAX_SPAWN_ATTEMPTS = 3;
    
    /** Length of health bar in characters for display */
    public static final int HEALTH_BAR_LENGTH = 36;
    
    /** Compact health bar length for holograms */
    public static final int COMPACT_BAR_LENGTH = 16;
    
    /** Maximum health value to prevent overflow issues */
    public static final double MAX_HEALTH_LIMIT = 2_000_000.0;
    
    /** Hologram height offsets based on mob type */
    public static final double NORMAL_MOB_HEIGHT_OFFSET = 0.8;
    public static final double ELITE_MOB_HEIGHT_OFFSET = 1.0;
    public static final double WORLD_BOSS_HEIGHT_OFFSET = 1.5;
    
    /** Minimum distance threshold for spawn location validation */
    public static final double MIN_SPAWN_DISTANCE = 0.5;
    
    /** Paper Entity scheduling context for better performance */
    private static final String TICK_TASK_NAME = "custom-mob-tick";

    // Adventure API serializer for backwards compatibility
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    // Core properties
    @Getter public final MobType type;
    @Getter public final int tier;
    @Getter public final boolean elite;
    @Getter public final String uniqueMobId;

    // ADDED: Named elite properties
    @Getter public final boolean isNamedElite;
    @Getter public final String eliteConfigName;

    // Entity reference - using volatile for thread-safe access without locks
    public volatile LivingEntity entity;

    // Display system
    public volatile String baseDisplayName;
    public volatile String originalDisplayName; // ADDED: Store original name for drop notifications
    public volatile Component baseDisplayComponent; // Modern Adventure component
    public volatile Component originalDisplayComponent; // Modern Adventure component
    public volatile String hologramId;
    public volatile long lastHologramUpdate = 0;

    // State management
    public final AtomicBoolean valid = new AtomicBoolean(true);
    public final AtomicBoolean removing = new AtomicBoolean(false);
    public final AtomicBoolean hologramActive = new AtomicBoolean(false);

    // Combat tracking
    public volatile long lastDamageTime = 0;
    public volatile boolean inCombat = false;

    // Health tracking
    public volatile double lastKnownHealth = -1;
    public volatile double lastKnownMaxHealth = -1;
    
    // Enhanced hologram state
    public volatile boolean hologramFading = false;
    public volatile long hologramCombatStartTime = 0;
    public volatile double hologramOpacity = 1.0;

    // Special modifiers
    @Getter public int lightningMultiplier = 0;

    /**
     * Creates a new CustomMob instance with basic configuration.
     * 
     * <p>This constructor is used for standard mob creation without elite configurations.
     * The mob will be created with default settings appropriate for its type and tier.
     * 
     * @param type The mob type defining the entity type and characteristics
     * @param tier The power tier (1-6) determining the mob's strength
     * @param elite Whether this mob should have elite status and enhanced abilities
     * @throws IllegalArgumentException if type is null
     * @see #CustomMob(MobType, int, boolean, String) for elite configuration support
     */
    public CustomMob(MobType type, int tier, boolean elite) {
        this(type, tier, elite, null);
    }

    /**
     * Creates a new CustomMob instance with comprehensive elite configuration support.
     * 
     * <p>This is the primary constructor that supports the full range of CustomMob features including:
     * <ul>
     *   <li>Named elite configurations with custom drop tables</li>
     *   <li>World boss detection and special handling</li>
     *   <li>Automatic tier validation and clamping</li>
     *   <li>Unique ID generation for tracking and management</li>
     * </ul>
     * 
     * <p><strong>Elite Configuration:</strong><br>
     * When an elite configuration name is provided, the system will:
     * <ol>
     *   <li>Validate the configuration exists in the drop system</li>
     *   <li>Apply special naming and appearance modifications</li>
     *   <li>Enable enhanced combat mechanics and behaviors</li>
     *   <li>Configure appropriate loot tables and drop rates</li>
     * </ol>
     * 
     * @param type The mob type defining entity characteristics and behavior patterns
     * @param tier The power tier (1-6) with automatic clamping to valid range
     * @param elite Whether this mob has elite status with enhanced capabilities
     * @param eliteConfigName Optional elite configuration name for named elites (can be null)
     * @throws IllegalArgumentException if type is null
     * @see DropConfig#isNamedElite(String) for elite configuration validation
     * @see #generateUniqueMobId() for ID generation details
     */
    public CustomMob(MobType type, int tier, boolean elite, String eliteConfigName) {
        Objects.requireNonNull(type, "MobType cannot be null");

        this.type = type;
        this.tier = Math.max(1, Math.min(6, tier)); // Clamp to valid range
        this.elite = elite;
        this.eliteConfigName = eliteConfigName;
        this.isNamedElite = (eliteConfigName != null && !eliteConfigName.trim().isEmpty() &&
                DropConfig.isNamedElite(eliteConfigName));
        this.uniqueMobId = generateUniqueMobId();

        logDebug("Creating %s mob: %s T%d%s (ID: %s) %s",
                elite ? "elite" : "normal", type.getId(), this.tier, elite ? "+" : "", uniqueMobId,
                isNamedElite ? "[Named: " + eliteConfigName + "]" : "");
        
        // Elite mobs and world bosses should show holograms immediately
        if (elite || isWorldBoss()) {
            hologramActive.set(true);
        }
    }

    private String generateUniqueMobId() {
        return String.format("mob_%s_%d_%d", type.getId(), System.currentTimeMillis(), RANDOM.nextInt(10000));
    }

    // ==================== ADVENTURE API COMPATIBILITY HELPERS ====================

    /**
     * Convert Component to legacy string for backwards compatibility
     */
    private String componentToLegacyString(Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Convert legacy string to Component
     */
    private Component legacyStringToComponent(String legacyString) {
        return LEGACY_SERIALIZER.deserialize(legacyString);
    }

    // ==================== LIFECYCLE MANAGEMENT ====================

    /**
     * Main tick method optimized for Paper 1.21.8 - called periodically to update mob state.
     * 
     * <p>This method handles the core mob state updates including:
     * <ul>
     *   <li><strong>Combat State Management:</strong> Tracks combat status and timeout handling</li>
     *   <li><strong>Display Updates:</strong> Health bars, name tags, and hologram synchronization</li>
     *   <li><strong>Environmental Protection:</strong> Sunlight damage prevention and world interaction</li>
     *   <li><strong>Behavior Execution:</strong> Delegates to the behavior system for custom mechanics</li>
     * </ul>
     * 
     * <p><strong>Performance Considerations:</strong><br>
     * This method is called frequently (typically every server tick for active mobs), so it's
     * optimized for minimal overhead. Entity reference caching and early returns prevent
     * unnecessary computation for invalid or inactive mobs.
     * 
     * <p><strong>Thread Safety:</strong><br>
     * All operations within this method are designed to be thread-safe, with proper volatile
     * field access patterns and atomic state updates.
     * 
     * @implNote Uses Paper's entity scheduling system when available for better performance
     * @see #updateCombatState() for combat mechanics
     * @see #updateDisplayElements() for visual updates
     * @see #preventSunlightDamage() for environmental protection
     */
    public void tick() {
        if (!isValid()) return;

        // Cache entity reference to avoid multiple volatile reads - Paper optimization
        final LivingEntity currentEntity = entity;
        if (currentEntity == null || !currentEntity.isValid()) {
            return;
        }

        try {
            // Use Paper's entity scheduler if available for better performance
            boolean usedPaperScheduler = false;
            try {
                // Try to use Paper entity scheduler (may not exist in all versions)
                Object scheduler = currentEntity.getClass().getMethod("getScheduler").invoke(currentEntity);
                if (scheduler != null) {
                    // Use reflection to call scheduler.run() safely
                    scheduler.getClass().getMethod("run", 
                        org.bukkit.plugin.Plugin.class, Runnable.class, Runnable.class)
                        .invoke(scheduler, YakRealms.getInstance(), (Runnable) () -> {
                            updateCombatState();
                            updateDisplayElements();
                            preventSunlightDamage();
                            MobBehaviorManager.getInstance().executeTick(this);
                        }, null);
                    usedPaperScheduler = true;
                }
            } catch (Exception paperError) {
                // Paper scheduler not available or failed, use fallback
            }
            
            if (!usedPaperScheduler) {
                // Fallback for non-Paper implementations or when Paper scheduler fails
                updateCombatState();
                updateDisplayElements();
                preventSunlightDamage();
                MobBehaviorManager.getInstance().executeTick(this);
            }

        } catch (Exception e) {
            logError("Tick processing error for mob " + uniqueMobId, e);
            // Continue execution to prevent system-wide failures
        }
    }

    /**
     * Spawns the mob at the specified location using Paper 1.21.8 optimized entity creation.
     * 
     * <p>This method handles the complete mob spawning process with the following phases:
     * <ol>
     *   <li><strong>Thread Safety Validation:</strong> Ensures spawning occurs on the main thread</li>
     *   <li><strong>Location Validation:</strong> Finds a safe spawn location near the target</li>
     *   <li><strong>Entity Creation:</strong> Creates the underlying Bukkit entity with retries</li>
     *   <li><strong>Mob Initialization:</strong> Applies all mob-specific properties and metadata</li>
     *   <li><strong>System Registration:</strong> Registers the mob with the management system</li>
     * </ol>
     * 
     * <p><strong>Paper 1.21.8 Enhancements:</strong>
     * <ul>
     *   <li>Uses Paper's improved entity spawning API for better performance</li>
     *   <li>Leverages Paper's chunk loading optimizations</li>
     *   <li>Implements Paper's entity scheduling for initialization tasks</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong><br>
     * This method implements comprehensive error handling with automatic cleanup.
     * If any step fails, all resources are properly released and the method returns false.
     * 
     * @param location The target location for spawning (world must be loaded)
     * @return true if the mob was successfully spawned, false otherwise
     * @throws IllegalStateException if called from an async thread
     * @see #findSafeSpawnLocation(Location) for location validation
     * @see #createEntity(Location) for entity creation details
     * @see #initializeMob() for initialization process
     */
    public boolean spawn(Location location) {
        if (!Bukkit.isPrimaryThread()) {
            IllegalStateException ex = new IllegalStateException(
                "spawn() called from async thread for mob " + uniqueMobId + "!");
            LOGGER.severe(ex.getMessage());
            throw ex;
        }

        try {
            // Phase 1: Location validation with Paper optimizations
            Location spawnLoc = findSafeSpawnLocation(location);
            if (spawnLoc == null) {
                logDebug("Failed to find safe spawn location for %s at %s", 
                    uniqueMobId, formatLocation(location));
                return false;
            }

            // Phase 2: Entity creation with Paper's improved spawning
            LivingEntity newEntity = createEntityWithPaperOptimizations(spawnLoc);
            if (newEntity == null) {
                logDebug("Failed to create entity for %s at %s", 
                    uniqueMobId, formatLocation(spawnLoc));
                return false;
            }

            // Phase 3: Atomic entity reference assignment
            entity = newEntity;

            // Phase 4: Mob initialization with full configuration
            if (!initializeMob()) {
                logDebug("Failed to initialize mob %s", uniqueMobId);
                cleanup();
                return false;
            }

            // Phase 5: Elite archetype initialization
            if (isElite()) {
                try {
                    boolean archetypeAssigned = com.rednetty.server.core.mechanics.world.mobs.behaviors.EliteArchetypeInitializer
                            .initializeEliteArchetype(this);
                    if (archetypeAssigned) {
                        logDebug("Elite archetype assigned to %s", uniqueMobId);
                    }
                } catch (Exception archetypeError) {
                    logError("Failed to assign elite archetype", archetypeError);
                }
            }
            
            // Phase 6: System registration and final setup
            MobManager.getInstance().registerMob(this);
            
            // Apply initial behaviors if any are configured
            applyInitialBehaviors();
            
            logDebug("Successfully spawned %s at %s", uniqueMobId, formatLocation(spawnLoc));
            return true;

        } catch (Exception e) {
            logError("Spawn error for mob " + uniqueMobId + " at " + formatLocation(location), e);
            cleanup();
            return false;
        }
    }

    /**
     * Removes the mob and cleans up resources with comprehensive elite cleanup
     */
    public void remove() {
        if (!removing.compareAndSet(false, true)) return;

        try {
            valid.set(false);
            
            // Execute behavior cleanup before mob removal
            try {
                MobBehaviorManager.getInstance().removeBehaviors(this);
            } catch (Exception behaviorError) {
                logError("Error removing behaviors during cleanup", behaviorError);
            }
            
            // Elite archetype cleanup using centralized manager
            if (isElite()) {
                try {
                    com.rednetty.server.core.mechanics.world.mobs.behaviors.EliteArchetypeBehaviorManager
                            .getInstance().cleanupElite(this);
                } catch (Exception eliteError) {
                    logError("Error cleaning up elite data", eliteError);
                }
            }
            
            removeHologram();

            // Cache the entity reference for cleanup
            LivingEntity entityToRemove = entity;
            if (entityToRemove != null && entityToRemove.isValid()) {
                MobManager.getInstance().recordMobDeath(type.getId(), tier, elite);
                entityToRemove.remove();
            }

            MobManager.getInstance().unregisterMob(this);

        } catch (Exception e) {
            logError("Removal error", e);
        } finally {
            // Clear the entity reference
            entity = null;
        }
    }

    // ==================== COMBAT MANAGEMENT ====================

    /**
     * Handles damage taken by the mob with Paper 1.21.8 enhanced damage processing.
     * 
     * <p>This method processes incoming damage and triggers appropriate responses:
     * <ul>
     *   <li><strong>Combat State Updates:</strong> Manages combat timing and state transitions</li>
     *   <li><strong>Visual Feedback:</strong> Updates health displays and combat indicators</li>
     *   <li><strong>Behavior Integration:</strong> Notifies behavior system of damage events</li>
     *   <li><strong>Performance Tracking:</strong> Records damage statistics for balancing</li>
     * </ul>
     * 
     * @param damage The amount of damage received (before any reductions)
     * @param attacker The entity that caused the damage (may be null for environmental damage)
     * @see MobBehaviorManager#executeDamage(CustomMob, double, LivingEntity)
     */
    public void handleDamage(double damage, LivingEntity attacker) {
        if (!isValid()) return;

        final long currentTime = System.currentTimeMillis();
        lastDamageTime = currentTime;
        
        try {
            // Update display elements immediately for responsive feedback
            updateDisplayElements();
            
            // Execute damage behaviors for custom mechanics
            MobBehaviorManager.getInstance().executeDamage(this, damage, attacker);
            
            // Special handling for high damage values
            if (damage > 100.0 && isWorldBoss()) {
                // Create visual impact effects for significant damage to world bosses
                createDamageVisualEffects(damage, attacker);
            }
            
            // Track damage statistics for balancing purposes
            if (attacker instanceof Player) {
                recordPlayerDamage((Player) attacker, damage);
            }
            
        } catch (Exception e) {
            logError("Error processing damage for mob " + uniqueMobId, e);
        }
    }
    
    /**
     * Legacy damage handling method for backward compatibility.
     * @deprecated Use {@link #handleDamage(double, LivingEntity)} for full functionality
     */
    @Deprecated
    public void handleDamage(double damage) {
        handleDamage(damage, null);
    }
    
    /**
     * Creates visual effects for significant damage dealt to this mob.
     * 
     * @param damage The damage amount
     * @param attacker The attacking entity (may be null)
     */
    private void createDamageVisualEffects(double damage, LivingEntity attacker) {
        final LivingEntity currentEntity = entity;
        if (currentEntity == null || !currentEntity.isValid()) {
            return;
        }
        
        try {
            Location loc = currentEntity.getLocation().add(0, currentEntity.getHeight() / 2, 0);
            World world = loc.getWorld();
            
            if (world != null) {
                // Scale particle count based on damage amount
                int particleCount = Math.min((int) (damage / 50.0), 10);
                
                world.spawnParticle(Particle.CRIT,
                    loc, particleCount, 1.0, 1.0, 1.0, 0.1);
                    
                // Sound effect for massive damage
                if (damage > 500.0) {
                    world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_HURT,
                        org.bukkit.SoundCategory.HOSTILE, 1.0f, 0.8f);
                }
            }
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }
    
    /**
     * Records damage statistics for player vs mob balancing.
     * 
     * @param player The player who dealt damage
     * @param damage The damage amount
     */
    private void recordPlayerDamage(Player player, double damage) {
        try {
            // This could integrate with a statistics system for game balancing
            // For now, we'll just log significant damage events
            if (damage > 200.0) {
                logDebug("High damage event: %s dealt %.1f damage to %s (T%d%s)",
                    player.getName(), damage, type.getId(), tier, elite ? " Elite" : "");
            }
        } catch (Exception e) {
            // Ignore statistics errors
        }
    }

    /**
     * Updates combat state with enhanced hologram fade management
     */
    private void updateCombatState() {
        long currentTime = System.currentTimeMillis();
        boolean wasInCombat = inCombat;

        inCombat = (currentTime - lastDamageTime) < COMBAT_TIMEOUT_MS;

        if (inCombat != wasInCombat) {
            if (inCombat) {
                onEnterCombat();
            } else {
                scheduleExitCombat();
            }
        }
        
        // Handle hologram fade effects
        if (inCombat && hologramOpacity < 1.0) {
            // Fade in over 1 second (20 ticks)
            hologramOpacity = Math.min(1.0, hologramOpacity + 0.05);
        } else if (hologramFading && hologramOpacity > 0.0) {
            // Fade out over 2 seconds (40 ticks)
            hologramOpacity = Math.max(0.0, hologramOpacity - 0.025);
            if (hologramOpacity <= 0.0) {
                hologramActive.set(false);
                hologramFading = false;
                removeHologram();
            }
        }
    }

    private void onEnterCombat() {
        hologramActive.set(true);
        hologramCombatStartTime = System.currentTimeMillis();
        hologramFading = false;
        hologramOpacity = 0.3; // Start faded for smooth fade-in
    }

    private void scheduleExitCombat() {
        hologramFading = true; // Start fade-out process
        // The actual deactivation happens in updateCombatState() when fade completes
    }

    // ==================== DISPLAY SYSTEM ====================

    /**
     * Updates all display elements (name and hologram)
     */
    private void updateDisplayElements() {
        updateMobName();

        if (shouldUpdateHologram()) {
            updateHologram();
        }
    }

    /**
     * Updates the mob's display name using Paper 1.21.8 enhanced Adventure API features.
     * 
     * <p>This method handles dynamic name updates with advanced features:
     * <ul>
     *   <li><strong>Component Caching:</strong> Optimizes performance by caching display components</li>
     *   <li><strong>World Boss Indicators:</strong> Special formatting for world boss entities</li>
     *   <li><strong>Combat State Display:</strong> Dynamic health percentages during combat</li>
     *   <li><strong>Critical State Indicators:</strong> Visual warnings for special mob states</li>
     * </ul>
     * 
     * <p><strong>Paper 1.21.8 Features:</strong>
     * <ul>
     *   <li>Uses enhanced Adventure Component system with rich text formatting</li>
     *   <li>Leverages Paper's optimized entity name change detection</li>
     *   <li>Implements efficient component-to-legacy conversion when needed</li>
     * </ul>
     * 
     * @implNote This method is called frequently during combat, so it's optimized for minimal overhead
     * @see #generateBaseDisplayComponent() for component creation
     * @see #generateCurrentDisplayComponent() for dynamic display logic
     */
    private void updateMobName() {
        // Cache entity reference to avoid multiple volatile reads - Paper optimization
        final LivingEntity currentEntity = entity;
        if (currentEntity == null || !currentEntity.isValid()) {
            return;
        }

        try {
            // Initialize base display components with lazy loading
            if (baseDisplayComponent == null) {
                baseDisplayComponent = generateBaseDisplayComponent();
                if (baseDisplayComponent != null) {
                    baseDisplayName = componentToLegacyString(baseDisplayComponent);
                    originalDisplayComponent = baseDisplayComponent;
                    originalDisplayName = baseDisplayName; // Store original for drop notifications
                } else {
                    logError("Failed to generate base display component for " + uniqueMobId, null);
                    return;
                }
            }

            // Generate current display with Paper optimizations
            Component displayComponent = generateCurrentDisplayComponent();
            if (displayComponent == null) {
                logError("Failed to generate current display component for " + uniqueMobId, null);
                return;
            }

            // Paper 1.21.8: Use component comparison for better performance
            Component currentNameComponent = currentEntity.customName();
            if (!displayComponent.equals(currentNameComponent)) {
                // Apply the new name using Paper's enhanced Adventure API
                currentEntity.customName(displayComponent);
                currentEntity.setCustomNameVisible(true);
                
                // Paper optimization: Batch entity updates when possible (with fallback)
                try {
                    Object scheduler = currentEntity.getClass().getMethod("getScheduler").invoke(currentEntity);
                    if (scheduler != null) {
                        scheduler.getClass().getMethod("run", 
                            org.bukkit.plugin.Plugin.class, Runnable.class, Runnable.class)
                            .invoke(scheduler, YakRealms.getInstance(), (Runnable) () -> {
                                // Additional visual effects for special mob types
                                if (isWorldBoss() && inCombat) {
                                    applyWorldBossVisualEffects();
                                }
                            }, null);
                    }
                } catch (Exception paperError) {
                    // Fallback: Apply effects immediately
                    if (isWorldBoss() && inCombat) {
                        applyWorldBossVisualEffects();
                    }
                }
            }

        } catch (Exception e) {
            logError("Name update failed for mob " + uniqueMobId + " at " + 
                formatLocation(currentEntity.getLocation()), e);
            
            // Fallback to basic name if component system fails
            try {
                String fallbackName = type.getDefaultName() + " T" + tier;
                currentEntity.setCustomName(fallbackName);
                currentEntity.setCustomNameVisible(true);
            } catch (Exception fallbackError) {
                logError("Even fallback name update failed for " + uniqueMobId, fallbackError);
            }
        }
    }
    
    /**
     * Applies special visual effects for world boss entities during combat.
     * 
     * <p>This method uses Paper 1.21.8 particle and sound systems to create
     * immersive world boss encounters with dynamic visual feedback.
     */
    private void applyWorldBossVisualEffects() {
        final LivingEntity currentEntity = entity;
        if (currentEntity == null || !currentEntity.isValid()) {
            return;
        }
        
        try {
            Location loc = currentEntity.getLocation();
            World world = loc.getWorld();
            
            if (world != null) {
                // Create menacing particle effects around world bosses
                world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, 
                    loc.add(0, currentEntity.getHeight() / 2, 0), 
                    3, 0.5, 0.5, 0.5, 0.01);
                    
                // Add subtle screen shake effect for nearby players (Paper feature)
                currentEntity.getNearbyEntities(10, 10, 10).stream()
                    .filter(entity -> entity instanceof Player)
                    .map(entity -> (Player) entity)
                    .forEach(player -> {
                        // Paper 1.21.8: Enhanced player effect system
                        try {
                            player.playSound(player.getLocation(), 
                                org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 
                                org.bukkit.SoundCategory.HOSTILE, 
                                0.1f, 0.8f);
                        } catch (Exception soundError) {
                            // Ignore sound errors
                        }
                    });
            }
        } catch (Exception e) {
            // Don't log visual effect errors as they're non-critical
        }
    }

    /**
     * Generates the base display component for the mob with named elite support - enhanced validation
     */
    private Component generateBaseDisplayComponent() {
        try {
            if (isNamedElite && eliteConfigName != null && !eliteConfigName.trim().isEmpty()) {
                // Use the elite configuration name for named elites
                String formattedName = formatEliteConfigName(eliteConfigName);
                if (formattedName == null || formattedName.trim().isEmpty()) {
                    // Fallback to type name if formatting failed
                    formattedName = type != null ? type.getId() : "Unknown Elite";
                }

                // Add tier and elite indicators with T6 gradient support
                Component eliteIndicator = elite ? Component.text(" ✦") : Component.empty();
                Component nameComponent = getTierFormattedComponent(formattedName, tier);

                // Check if this should be a world boss
                if (isWorldBoss()) {
                    Component worldBossLabel = tier == 6 ? 
                        getTierFormattedComponent("[WORLD BOSS] ", tier) : 
                        Component.text("[WORLD BOSS] ", getTierColor(tier));
                    
                    return Component.text()
                            .append(worldBossLabel)
                            .append(nameComponent)
                            .append(eliteIndicator)
                            .build();
                } else {
                    return Component.text()
                            .append(nameComponent)
                            .append(eliteIndicator)
                            .build();
                }
            } else {
                // Use standard tier-specific naming with T6 gradient support
                if (type != null) {
                    String tierName = type.getTierSpecificName(tier);
                    if (tierName != null && !tierName.trim().isEmpty()) {
                        // Check if we should use T6 gradient instead of legacy formatting
                        if (tier == 6) {
                            return getTierFormattedComponent(tierName, tier);
                        } else {
                            String legacyFormatted = MobUtils.formatMobName(tierName, tier, elite);
                            if (legacyFormatted != null && !legacyFormatted.trim().isEmpty()) {
                                return legacyStringToComponent(legacyFormatted);
                            }
                        }
                    }
                }
                
                // Final fallback with T6 gradient support
                String fallbackName = type != null ? type.getId() : "Unknown Mob";
                return getTierFormattedComponent(fallbackName, tier);
            }
        } catch (Exception e) {
            logError("Error generating base display component", e);
            // Ultimate fallback
            String safeName = (type != null ? type.getId() : "Mob") + " T" + tier;
            return Component.text(safeName, NamedTextColor.WHITE);
        }
    }

    /**
     * ADDED: Format elite config name for display
     */
    private String formatEliteConfigName(String configName) {
        if (configName == null || configName.trim().isEmpty()) {
            return type.getId();
        }

        // Convert camelCase or snake_case to Title Case
        String formatted = configName.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace("_", " ")
                .replace("-", " ");

        // Capitalize each word
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            String word = words[i].toLowerCase();
            if (word.length() > 0) {
                result.append(word.substring(0, 1).toUpperCase());
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }

        return result.toString();
    }

    /**
     * Determines if this mob should be treated as a world boss
     * Fixed: T6 mobs are NOT automatically world bosses - only specific ones marked as such
     */
    public boolean isWorldBoss() {
        // FIXED: Use the authoritative MobType.isWorldBoss() property first
        if (type != null && type.isWorldBoss()) {
            return true;
        }

        // Check explicit metadata override
        if (entity != null && entity.hasMetadata("worldboss")) {
            return entity.getMetadata("worldboss").get(0).asBoolean();
        }

        // Legacy fallback checks for backwards compatibility
        if (isNamedElite && eliteConfigName != null) {
            String lowerName = eliteConfigName.toLowerCase();
            return lowerName.contains("boss") ||
                    lowerName.contains("warden") ||
                    lowerName.equals("chronos") ||
                    lowerName.equals("frostwing") ||
                    lowerName.equals("frozengolem") ||
                    lowerName.equals("frozenboss") ||
                    lowerName.contains("dungeon");
        }

        return false;
    }

    /**
     * Get tier color for display using Adventure API
     */
    private NamedTextColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> NamedTextColor.WHITE;
            case 2 -> NamedTextColor.GREEN;
            case 3 -> NamedTextColor.AQUA;
            case 4 -> NamedTextColor.LIGHT_PURPLE;
            case 5 -> NamedTextColor.YELLOW;
            case 6 -> NamedTextColor.GOLD; // T6 Netherite - fallback color
            default -> NamedTextColor.WHITE;
        };
    }

    /**
     * Get formatted tier name component with T6 gradient support
     * For T6 mobs, applies beautiful gradient instead of plain gold
     */
    private Component getTierFormattedComponent(String name, int tier) {
        if (tier == 6) {
            // T6 gets special gradient treatment
            String gradientName = GradientColors.getT6Gradient(name);
            return legacyStringToComponent(gradientName);
        } else {
            // Other tiers use standard colors
            NamedTextColor color = getTierColor(tier);
            return Component.text(name, color);
        }
    }

    /**
     * Generates the current display component based on mob state - enhanced with error handling
     */
    private Component generateCurrentDisplayComponent() {
        if (baseDisplayComponent == null) {
            // Fallback to basic component if base is null
            String fallbackName = type != null ? type.getId() : "Unknown Mob";
            return Component.text(fallbackName, NamedTextColor.WHITE);
        }

        // Cache entity reference to avoid multiple volatile reads
        LivingEntity currentEntity = entity;

        // Show health percentage in combat
        if(!inCombat) {
            removeHologram();
        }
        
        if (inCombat && currentEntity != null && currentEntity.isValid()) {
            try {
                double currentHealth = currentEntity.getHealth();
                double maxHealth = currentEntity.getMaxHealth();
                
                if (maxHealth > 0 && currentHealth >= 0) {
                    double healthPercent = (currentHealth / maxHealth) * 100.0;
                    NamedTextColor healthColor = getHealthColor(healthPercent);
                    return Component.text()
                            .append(baseDisplayComponent)
                            .append(Component.text(" "))
                            .append(Component.text(String.format("(%.1f%%)", healthPercent), healthColor))
                            .build();
                }
            } catch (Exception e) {
                // Log warning but continue with fallback
                logError("Failed to get health for display component", e);
            }
        }

        // Show critical state
        if (isInCriticalState()) {
            int countdown = getCritCountdown();
            if (countdown > 0) {
                return Component.text()
                        .append(baseDisplayComponent)
                        .append(Component.text(" ⚠ " + countdown + " ⚠", NamedTextColor.RED))
                        .build();
            } else {
                return Component.text()
                        .append(baseDisplayComponent)
                        .append(Component.text(" ⚡ CHARGED ⚡", NamedTextColor.DARK_PURPLE))
                        .build();
            }
        }

        return baseDisplayComponent;
    }

    /**
     * Generates the current display name based on mob state (backwards compatibility)
     */
    private String generateCurrentDisplayName() {
        return componentToLegacyString(generateCurrentDisplayComponent());
    }

    /**
     * Generates the base display name for the mob (backwards compatibility)
     */
    private String generateBaseDisplayName() {
        return componentToLegacyString(generateBaseDisplayComponent());
    }

    /**
     * Determines if hologram should be updated with adaptive frequency
     */
    private boolean shouldUpdateHologram() {
        if (!hologramActive.get()) return false;

        long currentTime = System.currentTimeMillis();
        
        // Get dynamic update interval based on combat state and mob importance
        long updateInterval = getDynamicUpdateInterval();
        
        // Only update if enough time has passed to reduce CPU usage
        if ((currentTime - lastHologramUpdate) < updateInterval) {
            return false;
        }

        // Check if we need to update due to significant changes
        LivingEntity currentEntity = entity;
        if (currentEntity != null) {
            double currentHealth = currentEntity.getHealth();
            double healthChange = Math.abs(currentHealth - lastKnownHealth);
            double healthPercentChange = lastKnownMaxHealth > 0 ? 
                healthChange / lastKnownMaxHealth : 0;
                
            // Force update for significant health changes (5% or more)
            if (healthPercentChange >= 0.05) {
                return true;
            }
            
            // Force update during fade effects
            if (hologramFading || hologramOpacity < 1.0) {
                return true;
            }
        }

        return true;
    }

    /**
     * Gets dynamic update interval based on mob state and importance
     */
    private long getDynamicUpdateInterval() {
        // World bosses get fastest updates for smooth experience
        if (isWorldBoss()) {
            return 25L; // ~1.25 ticks
        }
        
        // Elite mobs get medium frequency
        if (elite) {
            // Higher tier elites get more frequent updates
            return tier >= 5 ? 35L : 45L; // ~1.75-2.25 ticks
        }
        
        // Normal mobs get standard frequency
        return HOLOGRAM_UPDATE_INTERVAL_MS; // 50ms = ~2.5 ticks
    }

    /**
     * Updates the hologram display with dynamic positioning and fade effects
     */
    private void updateHologram() {
        // Cache entity reference to avoid multiple volatile reads
        LivingEntity currentEntity = entity;
        if (currentEntity == null || !hologramActive.get()) return;

        try {
            if (hologramId == null) {
                hologramId = "holo_" + uniqueMobId;
            }

            List<String> lines = createHologramLines();
            if (lines.isEmpty()) return;

            // Dynamic height offset based on mob type and tier
            double heightOffset = calculateDynamicHeightOffset(currentEntity);
            Location holoLoc = currentEntity.getLocation().add(0, heightOffset, 0);

            // Apply fade effect to hologram lines
            if (hologramOpacity < 1.0) {
                lines = applyFadeEffect(lines, hologramOpacity);
            }

            // Dynamic line spacing based on mob importance
            double lineSpacing = isWorldBoss() ? 0.25 : (elite ? 0.22 : 0.20);

            boolean updated = HologramManager.updateHologramEfficiently(
                    hologramId, holoLoc, lines, lineSpacing, getEntityUuid()
            );

            if (updated) {
                lastHologramUpdate = System.currentTimeMillis();
                
                // Update health tracking for change detection
                if (currentEntity != null) {
                    lastKnownHealth = currentEntity.getHealth();
                    lastKnownMaxHealth = currentEntity.getMaxHealth();
                }
            }

        } catch (Exception e) {
            logError("Hologram update failed", e);
        }
    }

    /**
     * Calculates dynamic height offset based on mob properties
     */
    private double calculateDynamicHeightOffset(LivingEntity currentEntity) {
        double baseOffset = currentEntity.getHeight();
        
        if (isWorldBoss()) {
            return baseOffset + WORLD_BOSS_HEIGHT_OFFSET;
        } else if (elite) {
            // Elite mobs get higher positioning based on tier
            return baseOffset + ELITE_MOB_HEIGHT_OFFSET + (tier * 0.1);
        } else {
            return baseOffset + NORMAL_MOB_HEIGHT_OFFSET;
        }
    }

    /**
     * Applies fade effect to hologram lines
     */
    private List<String> applyFadeEffect(List<String> originalLines, double opacity) {
        if (opacity >= 1.0) return originalLines;
        
        List<String> fadedLines = new ArrayList<>();
        ChatColor fadeColor = opacity > 0.7 ? ChatColor.WHITE : 
                             opacity > 0.4 ? ChatColor.GRAY : ChatColor.DARK_GRAY;
        
        for (String line : originalLines) {
            // Apply fade by prepending fade color (simple approach)
            if (opacity < 0.5) {
                fadedLines.add(fadeColor + line);
            } else {
                fadedLines.add(line); // Keep original for higher opacity
            }
        }
        
        return fadedLines;
    }

    public String getEntityUuid() {
        LivingEntity ent = getEntity();
        return ent != null ? ent.getUniqueId().toString() : null;
    }

    /**
     * Creates hologram display lines with compact HP bar and abilities - exactly 2 lines max
     */
    private List<String> createHologramLines() {
        List<String> lines = new ArrayList<>();

        // Cache entity reference to avoid multiple volatile reads
        LivingEntity currentEntity = entity;
        if (!inCombat || currentEntity == null) return lines;

        try {
            double health = currentEntity.getHealth();
            double maxHealth = currentEntity.getMaxHealth();

            if (maxHealth > 0) {
                // LINE 1: Clean HP bar only - [||||||||||||||||]
                lines.add(createCompactHealthBar(health, maxHealth));

                // LINE 2: Compact abilities/status with symbols and colors
                if (isElite()) {
                    String compactAbilities = getCompactAbilitiesLine();
                    if (compactAbilities != null && !compactAbilities.trim().isEmpty()) {
                        lines.add(compactAbilities);
                    }
                }
            }

        } catch (Exception e) {
            logError("Hologram line creation failed", e);
        }

        return lines;
    }
    
    /**
     * Gets simple elite archetype info - matches game's tier color system
     */
    private String getSimpleEliteInfo() {
        if (!isElite()) return null;
        
        try {
            // Use tier-based coloring to match existing system
            ChatColor tierColor = MobUtils.getTierColor(tier);
            
            LivingEntity entityRef = this.entity;
            if (entityRef != null && entityRef.hasMetadata("elite_archetype")) {
                String archetype = entityRef.getMetadata("elite_archetype").get(0).asString();
                String archetypeName = switch (archetype.toUpperCase()) {
                    case "BRUTE" -> "BRUTE";
                    case "ELEMENTALIST" -> "ELEMENTALIST";
                    case "ASSASSIN" -> "ASSASSIN";
                    case "GUARDIAN" -> "GUARDIAN";
                    case "NECROMANCER" -> "NECROMANCER";
                    case "BERSERKER" -> "BERSERKER";
                    default -> "ELITE";
                };
                return tierColor + "§l[" + archetypeName + "]";
            }
            return tierColor + "§l[ELITE]";
            
        } catch (Exception e) {
            return ChatColor.LIGHT_PURPLE + "§l[ELITE]";
        }
    }
    
    /**
     * Gets elemental phase information for Elementalist archetype
     */
    private String getElementalPhaseInfo() {
        try {
            // Try to get current elemental phase from metadata or behavior system
            LivingEntity entityRef = this.entity; // Use instance field
            if (entityRef != null && entityRef.hasMetadata("elemental_phase")) {
                String phase = entityRef.getMetadata("elemental_phase").get(0).asString();
                return switch (phase.toUpperCase()) {
                    case "FIRE" -> "§c§lFIRE §7• §6Lightning §7• §bIce";
                    case "ICE" -> "§bIce §7• §c§lFIRE §7• §6Lightning";
                    case "LIGHTNING" -> "§6Lightning §7• §bIce §7• §c§lFIRE";
                    default -> "§eElemental Mastery";
                };
            }
            return "§cFire §7• §bIce §7• §eLightning";
        } catch (Exception e) {
            return "§eElemental Powers";
        }
    }
    
    /**
     * Gets information about the next ability that will be used
     */
    private String getNextAbilityInfo() {
        if (!isElite() || this.entity == null) return null;
        
        try {
            // Check for active abilities using centralized manager
            String currentAbility = com.rednetty.server.core.mechanics.world.mobs.behaviors.EliteArchetypeBehaviorManager
                    .getInstance().getCurrentAbility(this);
            
            if (currentAbility != null && !currentAbility.equals("NONE")) {
                // Show active ability warnings
                return switch (currentAbility.toUpperCase()) {
                    case "CHARGING" -> "§c§l»§6§l DEVASTATING CHARGE INCOMING! §c§l«";
                    case "GROUND_SLAM" -> "§4§l»§6§l MASSIVE GROUND SLAM - MOVE! §4§l«";
                    case "FIRE_MASTERY" -> "§c§l»§6§l INFERNO BARRAGE INCOMING! §c§l«";
                    case "ICE_MASTERY" -> "§b§l»§6§l FROZEN BLIZZARD INCOMING! §b§l«";
                    case "LIGHTNING_MASTERY" -> "§e§l»§6§l LIGHTNING STORM INCOMING! §e§l«";
                    case "BERSERKER_RAGE" -> "§4§l»§6§l UNSTOPPABLE RAGE ACTIVE! §4§l«";
                    default -> "§6§l» USING SPECIAL ABILITY «";
                };
            }
            
            // Check for elemental phase information
            LivingEntity entityRef = this.entity; // Use instance field instead
            if (entityRef != null && entityRef.hasMetadata("elemental_phase")) {
                String phase = entityRef.getMetadata("elemental_phase").get(0).asString();
                return switch (phase.toUpperCase()) {
                    case "FIRE" -> "§c§lFire Phase §7- §6Inferno abilities ready";
                    case "ICE" -> "§b§lIce Phase §7- §bFreezing abilities ready";
                    case "LIGHTNING" -> "§e§lLightning Phase §7- §eStorm abilities ready";
                    default -> "§eElemental Power Active";
                };
            }
            
            // Check current mob state for ability predictions
            if (entityRef == null) return null;
            double healthPercent = (entityRef.getHealth() / entityRef.getMaxHealth()) * 100;
            
            // Show critical state information
            if (isInCriticalState()) {
                int countdown = getCritCountdown();
                if (countdown > 0) {
                    return String.format("§5§l⚡ CHARGING CRITICAL (§6%d§5§l) ⚡", countdown);
                } else {
                    return "§5§l⚡ §4§lCRITICAL READY - EXTREME DANGER! §5§l⚡";
                }
            }
            
            // Health-based ability warnings
            if (healthPercent <= 30) {
                if (entityRef.hasMetadata("elite_archetype")) {
                    String archetype = entityRef.getMetadata("elite_archetype").get(0).asString();
                    return switch (archetype.toUpperCase()) {
                        case "BRUTE" -> "§4§l⚠ BERSERKER RAGE IMMINENT! ⚠";
                        case "ASSASSIN" -> "§8§l⚠ PREPARING ESCAPE ABILITIES! ⚠";
                        case "ELEMENTALIST" -> "§6§l⚠ ELEMENTAL STORM BUILDING! ⚠";
                        case "NECROMANCER" -> "§2§l⚠ DEATH MAGIC INTENSIFYING! ⚠";
                        default -> "§c§l⚠ DESPERATE ABILITIES READY! ⚠";
                    };
                }
                return "§c§l⚠ LOW HEALTH - VERY DANGEROUS! ⚠";
            }
            
            // Normal state ability hints
            if (entityRef.hasMetadata("elite_archetype")) {
                String archetype = entityRef.getMetadata("elite_archetype").get(0).asString();
                return switch (archetype.toUpperCase()) {
                    case "BRUTE" -> "§7Analyzing targets for §6Charge §7or §cGround Slam";
                    case "ASSASSIN" -> "§7Preparing §5Stealth §7or §2Poison attacks";
                    case "GUARDIAN" -> "§7Ready to use §bShields §7or §3Reflections";
                    case "ELEMENTALIST" -> "§7Considering §ePhase Change §7or §cElemental burst";
                    case "VOID_WALKER" -> "§7Preparing §5Reality manipulation §7or §dPhase shift";
                    default -> "§7Analyzing combat situation...";
                };
            }
            
            return null; // No ability info for non-elites
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a compact health bar with dynamic scaling based on mob tier
     */
    private String createCompactHealthBar(double health, double maxHealth) {
        double percentage = (health / maxHealth) * 100.0;
        
        // Dynamic bar length based on mob importance
        int barLength = getDynamicBarLength();
        int filled = (int) Math.round((health / maxHealth) * barLength);
        filled = Math.max(0, Math.min(barLength, filled));

        ChatColor barColor = getHealthBarColorLegacy(percentage);
        ChatColor emptyColor = ChatColor.DARK_GRAY;
        ChatColor frameColor = isWorldBoss() ? ChatColor.GOLD : 
                              elite ? ChatColor.YELLOW : ChatColor.GRAY;

        // Enhanced format with tier-based styling
        StringBuilder bar = new StringBuilder();
        
        // World bosses get special brackets
        if (isWorldBoss()) {
            bar.append(frameColor).append("«");
        } else {
            bar.append(frameColor).append("[");
        }
        
        // Use bolded bars for elites, normal bars for regular mobs
        String filledBars = elite ? "§l" + "|".repeat(filled) : "|".repeat(filled);
        String emptyBars = "|".repeat(barLength - filled);
        
        bar.append(barColor).append(filledBars);
        bar.append(emptyColor).append(emptyBars);
        
        if (isWorldBoss()) {
            bar.append(frameColor).append("»");
        } else {
            bar.append(frameColor).append("]");
        }

        return bar.toString();
    }

    /**
     * Gets dynamic bar length based on mob tier and type
     */
    private int getDynamicBarLength() {
        if (isWorldBoss()) {
            return 40; // Longer bar for world bosses
        } else if (elite && tier >= 5) {
            return 36; // T5+ elites get longer bars
        } else if (elite) {
            return 34; // Regular elites
        } else {
            return 32; // Normal mobs get minimum 32 bars
        }
    }

    /**
     * Gets enhanced compact abilities line with dynamic symbols and visual effects
     */
    private String getCompactAbilitiesLine() {
        if (!isElite() || entity == null) return null;
        
        try {
            StringBuilder abilities = new StringBuilder();
            
            // Add tier indicator with special formatting
            String tierIndicator = getTierIndicator();
            if (tierIndicator != null) {
                abilities.append(tierIndicator);
            }
            
            // Get archetype with symbol
            String archetype = getArchetypeSymbol();
            if (archetype != null) {
                if (abilities.length() > 0) abilities.append(" ");
                abilities.append(archetype);
            }
            
            // Add phase/state indicators
            String phase = getCurrentPhaseIndicator();
            if (phase != null) {
                if (abilities.length() > 0) abilities.append(" ");
                abilities.append(phase);
            }
            
            // Add warning indicators if needed
            String warning = getWarningIndicator();
            if (warning != null) {
                if (abilities.length() > 0) abilities.append(" ");
                abilities.append(warning);
            }
            
            // Add combat duration indicator for long fights
            long combatDuration = hologramCombatStartTime > 0 ? 
                (System.currentTimeMillis() - hologramCombatStartTime) / 1000 : 0;
            if (combatDuration > 30) { // Show after 30 seconds
                String duration = getDurationIndicator(combatDuration);
                if (duration != null) {
                    if (abilities.length() > 0) abilities.append(" ");
                    abilities.append(duration);
                }
            }
            
            return abilities.length() > 0 ? abilities.toString() : null;
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets tier indicator with special formatting for high tiers
     */
    private String getTierIndicator() {
        ChatColor tierColor = MobUtils.getTierColor(tier);
        
        if (isWorldBoss()) {
            return tierColor + "§l◆" + tier + "◆";
        } else if (tier >= 5) {
            return tierColor + "§l★" + tier;
        } else if (tier >= 3) {
            return tierColor + "T" + tier;
        } else {
            return null; // Don't show for low tiers
        }
    }

    /**
     * Gets combat duration indicator for extended fights
     */
    private String getDurationIndicator(long seconds) {
        if (seconds > 300) { // 5+ minutes
            return "§c§l⏰" + (seconds / 60) + "m";
        } else if (seconds > 120) { // 2+ minutes
            return "§6§l⏰" + (seconds / 60) + "m";
        } else if (seconds > 60) { // 1+ minute
            return "§e§l⏰" + (seconds / 60) + "m";
        }
        return null;
    }

    /**
     * Gets archetype symbol with tier color
     */
    private String getArchetypeSymbol() {
        try {
            ChatColor tierColor = MobUtils.getTierColor(tier);
            
            if (entity.hasMetadata("elite_archetype")) {
                String archetype = entity.getMetadata("elite_archetype").get(0).asString();
                String symbol = switch (archetype.toUpperCase()) {
                    case "BRUTE" -> "⚔";
                    case "ELEMENTALIST" -> "✦";
                    case "ASSASSIN" -> "⚡";
                    case "GUARDIAN" -> "◆";
                    case "NECROMANCER" -> "☠";
                    case "BERSERKER" -> "⚡";
                    default -> "✦";
                };
                return tierColor + "§l" + symbol;
            }
            return tierColor + "§l✦";
            
        } catch (Exception e) {
            return ChatColor.LIGHT_PURPLE + "§l✦";
        }
    }

    /**
     * Gets current phase indicator (for elementalists, etc)
     */
    private String getCurrentPhaseIndicator() {
        try {
            if (entity.hasMetadata("elemental_phase")) {
                String phase = entity.getMetadata("elemental_phase").get(0).asString();
                return switch (phase.toUpperCase()) {
                    case "FIRE" -> "§c▲";
                    case "ICE" -> "§b❅";
                    case "LIGHTNING" -> "§e⚡";
                    default -> "§e●";
                };
            }
            
            // Health-based phase
            double healthPercent = (entity.getHealth() / entity.getMaxHealth()) * 100;
            if (healthPercent <= 25) {
                return "§4§l!"; // Desperate
            } else if (healthPercent <= 50) {
                return "§6§l!"; // Enraged
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets warning indicator for critical states with enhanced visual effects
     */
    private String getWarningIndicator() {
        try {
            if (isInCriticalState()) {
                int countdown = getCritCountdown();
                if (countdown > 0) {
                    // Pulsing effect based on countdown
                    String pulse = (countdown % 2 == 0) ? "§5§l" : "§d§l";
                    return pulse + "⚡" + countdown;
                } else {
                    // Maximum danger - alternating colors for attention
                    long time = System.currentTimeMillis() / 250; // 4 times per second
                    String danger = (time % 2 == 0) ? "§4§l" : "§c§l";
                    return danger + "⚡⚡⚡";
                }
            }
            
            // Low health warning for elites
            LivingEntity currentEntity = entity;
            if (elite && currentEntity != null) {
                double healthPercent = (currentEntity.getHealth() / currentEntity.getMaxHealth()) * 100;
                if (healthPercent <= 10) {
                    return "§c§l!";
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets health bar color using legacy ChatColor for consistency
     */
    private ChatColor getHealthBarColorLegacy(double percentage) {
        if (percentage >= 80) return ChatColor.GREEN;
        if (percentage >= 60) return ChatColor.YELLOW;
        if (percentage >= 40) return ChatColor.GOLD;
        if (percentage >= 25) return ChatColor.RED;  // Use &c (RED) for low health
        return ChatColor.RED;  // Use &c (RED) for critical health instead of DARK_RED
    }

    /**
     * Creates a visual health bar using Adventure Components (legacy method)
     */
    private String createHealthBar(double health, double maxHealth) {
        double percentage = (health / maxHealth) * 100.0;
        int filled = (int) Math.round((health / maxHealth) * HEALTH_BAR_LENGTH);
        filled = Math.max(0, Math.min(HEALTH_BAR_LENGTH, filled));

        NamedTextColor barColor = getHealthBarColor(percentage);

        Component healthBar = Component.text()
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text("["))
                .append(Component.text("|".repeat(filled), barColor))
                .append(Component.text("|".repeat(HEALTH_BAR_LENGTH - filled), NamedTextColor.GRAY))
                .append(Component.text("]"))
                .build();

        return componentToLegacyString(healthBar);
    }

    /**
     * Removes the hologram display
     */
    private void removeHologram() {
        if (hologramId == null) return;

        try {
            String entityUuidStr = entity.getUniqueId().toString();
            if (entityUuidStr != null) {
                HologramManager.removeHologramByMob(entityUuidStr);
            }
            HologramManager.removeHologram(hologramId);
            hologramActive.set(false);
        } catch (Exception e) {
            logError("Hologram removal failed", e);
        }
    }

    // ==================== MOB INITIALIZATION ====================

    /**
     * Creates the entity at the specified location
     */
    /**
     * Creates the underlying Bukkit entity using Paper 1.21.8 optimizations.
     * 
     * <p>This method uses Paper's enhanced entity spawning system for better performance
     * and reliability. It includes retry logic with exponential backoff and proper
     * resource cleanup on failure.
     * 
     * @param location The validated spawn location
     * @return The created LivingEntity or null if creation failed
     * @see #createEntity(Location) for the legacy implementation
     */
    private LivingEntity createEntityWithPaperOptimizations(Location location) {
        World world = location.getWorld();
        if (world == null) {
            logDebug("Cannot create entity - world is null for location %s", formatLocation(location));
            return null;
        }

        EntityType entityType = determineEntityType();
        if (entityType == null) {
            logError("Cannot determine entity type for mob type: " + type.getId(), null);
            return null;
        }

        // Use Paper's enhanced entity spawning with proper error handling
        for (int attempt = 1; attempt <= MAX_SPAWN_ATTEMPTS; attempt++) {
            try {
                // Paper 1.21.8: Use the enhanced spawn method with better chunk loading (with fallback)
                Entity spawned = null;
                try {
                    // Try to use Paper's optimized spawning when available
                    if (world.getClass().getSimpleName().contains("CraftWorld")) {
                        // Use reflection to safely access CreatureSpawnEvent.SpawnReason.CUSTOM
                        Class<?> reasonClass = Class.forName("org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason");
                        Object customReason = reasonClass.getField("CUSTOM").get(null);
                        spawned = (Entity) world.getClass().getMethod("spawnEntity", 
                            Location.class, EntityType.class, reasonClass)
                            .invoke(world, location, entityType, customReason);
                    }
                } catch (Exception paperError) {
                    // Paper method not available or failed, use standard Bukkit method
                }
                
                // Fallback to standard Bukkit method if Paper approach failed
                if (spawned == null) {
                    spawned = world.spawnEntity(location, entityType);
                }

                // Validate the spawned entity
                if (spawned instanceof LivingEntity living && living.isValid()) {
                    // Paper optimization: Pre-load the entity's chunk data
                    if (living.getChunk().isLoaded()) {
                        return living;
                    }
                }

                // Clean up failed spawn attempt
                if (spawned != null && spawned.isValid()) {
                    spawned.remove();
                }

            } catch (Exception e) {
                logDebug("Entity creation attempt %d failed for %s: %s", 
                    attempt, uniqueMobId, e.getMessage());
                
                // For critical errors, don't retry
                if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
                    logError("Critical error in entity creation, aborting: " + e.getMessage(), e);
                    break;
                }
            }

            // Exponential backoff with jitter for retry attempts
            if (attempt < MAX_SPAWN_ATTEMPTS) {
                try {
                    long backoffMs = (long) (Math.pow(2, attempt - 1) * 10 + RANDOM.nextInt(10));
                    Thread.sleep(Math.min(backoffMs, 100)); // Max 100ms delay
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logDebug("Entity creation interrupted for %s", uniqueMobId);
                    break;
                }
            }
        }

        logError("Failed to create entity after " + MAX_SPAWN_ATTEMPTS + " attempts for mob: " + uniqueMobId, null);
        return null;
    }
    
    /**
     * Applies initial behaviors to the mob based on its type and elite status.
     * 
     * <p>This method is called during the spawn process to configure default behaviors
     * that should be active from the moment the mob is created. Behaviors can include
     * special combat mechanics, movement patterns, or environmental interactions.
     * 
     * @implNote This method integrates with the MobBehaviorManager for behavior application
     * @see MobBehaviorManager#applyBehavior(CustomMob, String)
     */
    private void applyInitialBehaviors() {
        try {
            MobBehaviorManager behaviorManager = MobBehaviorManager.getInstance();
            
            // Apply type-specific behaviors
            if (type.hasAbility(MobType.MobAbility.FIRE_MASTERY)) {
                behaviorManager.applyBehavior(this, "fire_immunity");
            }
            
            if (type.hasAbility(MobType.MobAbility.FLIGHT)) {
                behaviorManager.applyBehavior(this, "flight_control");
            }
            
            // Apply environmental adaptation for all mobs tier 2+
            if (tier >= 2) {
                behaviorManager.applyBehavior(this, "environmental_adaptation");
            }
            
            // Apply elite-specific behaviors
            if (isElite() && isNamedElite) {
                behaviorManager.applyBehavior(this, "elite_combat");
                if (isWorldBoss()) {
                    behaviorManager.applyBehavior(this, "world_boss_mechanics");
                }
            }
            
            // Apply tier-based behaviors for high-tier mobs
            if (tier >= 5) {
                behaviorManager.applyBehavior(this, "advanced_ai");
            }
            
        } catch (Exception e) {
            logError("Failed to apply initial behaviors", e);
            // Don't fail spawning if behaviors can't be applied
        }
    }
    
    /**
     * Formats a location for logging and debugging purposes.
     * 
     * @param location The location to format
     * @return A human-readable string representation of the location
     */
    private String formatLocation(Location location) {
        if (location == null) {
            return "<null location>";
        }
        if (location.getWorld() == null) {
            return "<null world>";
        }
        return String.format("%s[%.1f, %.1f, %.1f]", 
            location.getWorld().getName(),
            location.getX(),
            location.getY(), 
            location.getZ());
    }
    
    /**
     * Legacy entity creation method for compatibility.
     * @deprecated Use {@link #createEntityWithPaperOptimizations(Location)} for better performance
     */
    @Deprecated
    private LivingEntity createEntity(Location location) {
        return createEntityWithPaperOptimizations(location);
    }

    /**
     * Initializes the mob with properties and equipment, including named elite metadata
     */
    private boolean initializeMob() {
        if (entity == null) return false;

        try {
            applyMetadata();
            applyBaseProperties();
            applyEquipment();
            applyHealthAndStats();

            baseDisplayComponent = generateBaseDisplayComponent();
            baseDisplayName = componentToLegacyString(baseDisplayComponent);
            originalDisplayComponent = baseDisplayComponent;
            originalDisplayName = baseDisplayName; // Store for notifications
            updateMobName();

            // Initialize hologram for elite mobs and world bosses
            if (hologramActive.get()) {
                updateHologram();
            }

            return true;

        } catch (Exception e) {
            logError("Initialization failed", e);
            return false;
        }
    }

    /**
     * Applies metadata to the entity with named elite support
     */
    private void applyMetadata() {
        YakRealms plugin = YakRealms.getInstance();

        // Basic metadata
        entity.setMetadata("type", new FixedMetadataValue(plugin, type.getId()));
        entity.setMetadata("tier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("customTier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("elite", new FixedMetadataValue(plugin, elite));
        entity.setMetadata("mob_unique_id", new FixedMetadataValue(plugin, uniqueMobId));

        // ADDED: Named elite metadata
        if (isNamedElite && eliteConfigName != null) {
            entity.setMetadata("namedElite", new FixedMetadataValue(plugin, true));
            entity.setMetadata("eliteConfigName", new FixedMetadataValue(plugin, eliteConfigName));
            entity.setMetadata("customName", new FixedMetadataValue(plugin, eliteConfigName));

            if (logDebug("Applied named elite metadata: %s", eliteConfigName)) {
                logDebug("Named elite metadata applied for %s: %s", uniqueMobId, eliteConfigName);
            }
        }

        // World boss metadata
        if (isWorldBoss()) {
            entity.setMetadata("worldboss", new FixedMetadataValue(plugin, true));
        }

        // Legacy compatibility
        if (type.isWorldBoss()) {
            entity.setMetadata("worldboss", new FixedMetadataValue(plugin, true));
        }
    }

    /**
     * Applies base properties to the entity
     */
    private void applyBaseProperties() {
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 2));

        if (isUndead()) {
            entity.setMetadata("sunlight_immune",
                    new FixedMetadataValue(YakRealms.getInstance(), true));
        }
        
        // Make all mobs in custom system hostile (including normally passive ones)
        makeEntityHostile();
        
        // Configure custom damage overrides for special vanilla mobs
        configureCustomDamage();
    }

    /**
     * Makes normally passive mobs hostile when used in the custom mob system
     */
    private void makeEntityHostile() {
        try {
            // Force hostile behavior for all mobs regardless of vanilla type
            if (entity instanceof org.bukkit.entity.Mob mob) {
                // Set AI to be aggressive toward players
                Player nearestPlayer = findNearestPlayerForMob(entity, 16.0);
                if (nearestPlayer != null) {
                    mob.setTarget(nearestPlayer);
                }
            }
            
            // For passive mobs, we need to override their AI
            EntityType entityType = entity.getType();
            switch (entityType) {
                case COW, SHEEP, PIG, CHICKEN, RABBIT, VILLAGER, IRON_GOLEM, CAMEL -> {
                    // Mark as custom hostile
                    entity.setMetadata("forced_hostile", new FixedMetadataValue(YakRealms.getInstance(), true));
                    entity.setMetadata("custom_damage_override", new FixedMetadataValue(YakRealms.getInstance(), true));
                    
                    // Try to make them aggressive using reflection for better compatibility
                    makePassiveMobAggressive();
                    
                    // Schedule continuous re-targeting for passive mobs
                    startContinuousTargeting();
                }
                case WARDEN, VINDICATOR, RAVAGER, PIGLIN_BRUTE -> {
                    // Special vanilla mobs that need damage overrides
                    entity.setMetadata("vanilla_special_damage", new FixedMetadataValue(YakRealms.getInstance(), true));
                    entity.setMetadata("custom_damage_override", new FixedMetadataValue(YakRealms.getInstance(), true));
                }
                default -> {
                    // All other mobs still get damage overrides
                    entity.setMetadata("custom_damage_override", new FixedMetadataValue(YakRealms.getInstance(), true));
                }
            }
            
        } catch (Exception e) {
            logError("Failed to make entity hostile: " + e.getMessage(), e);
        }
    }

    /**
     * Configures custom damage calculations for all mob types
     */
    private void configureCustomDamage() {
        try {
            // Calculate our custom attack damage based on tier and type
            double customAttackDamage = calculateCustomAttackDamage();
            
            // Store custom damage value in metadata for damage listeners to use
            entity.setMetadata("custom_attack_damage", new FixedMetadataValue(YakRealms.getInstance(), customAttackDamage));
            
            // Special handling for mobs that have unique vanilla damage mechanics
            EntityType entityType = entity.getType();
            switch (entityType) {
                case WARDEN -> {
                    // Warden's sonic boom and normal attacks use our system
                    entity.setMetadata("warden_sonic_damage", new FixedMetadataValue(YakRealms.getInstance(), customAttackDamage * 2.5));
                    entity.setMetadata("warden_melee_damage", new FixedMetadataValue(YakRealms.getInstance(), customAttackDamage * 1.5));
                }
                case VINDICATOR -> {
                    // Vindicator axe attacks
                    entity.setMetadata("vindicator_axe_damage", new FixedMetadataValue(YakRealms.getInstance(), customAttackDamage * 1.3));
                }
                case RAVAGER -> {
                    // Ravager charge and bite attacks
                    entity.setMetadata("ravager_charge_damage", new FixedMetadataValue(YakRealms.getInstance(), customAttackDamage * 2.0));
                    entity.setMetadata("ravager_bite_damage", new FixedMetadataValue(YakRealms.getInstance(), customAttackDamage * 1.2));
                }
                case PIGLIN_BRUTE -> {
                    // Brute axe attacks
                    entity.setMetadata("brute_axe_damage", new FixedMetadataValue(YakRealms.getInstance(), customAttackDamage * 1.4));
                }
                case COW, SHEEP, PIG, CHICKEN, RABBIT, VILLAGER, IRON_GOLEM, CAMEL -> {
                    // Passive mobs made hostile get custom damage
                    entity.setMetadata("passive_attack_damage", new FixedMetadataValue(YakRealms.getInstance(), customAttackDamage * 0.8));
                }
            }
            
        } catch (Exception e) {
            logError("Failed to configure custom damage: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates custom attack damage based on tier, elite status, and mob type
     */
    private double calculateCustomAttackDamage() {
        // Base damage from tier
        double baseDamage = switch (tier) {
            case 1 -> 4.0;
            case 2 -> 7.0;
            case 3 -> 12.0;
            case 4 -> 18.0;
            case 5 -> 26.0;
            case 6 -> 35.0;
            default -> 4.0;
        };
        
        // Elite multiplier
        if (elite) {
            baseDamage *= 1.5;
        }
        
        // World boss multiplier
        if (isWorldBoss()) {
            baseDamage *= 2.0;
        }
        
        // Type-specific modifiers
        baseDamage *= type.getDamageMultiplier();
        
        return Math.max(1.0, baseDamage);
    }

    /**
     * Uses reflection to make passive mobs aggressive (better compatibility)
     */
    private void makePassiveMobAggressive() {
        try {
            if (!(entity instanceof org.bukkit.entity.Mob mob)) return;
            
            // Use Paper 1.21+ methods for aggressive behavior
            try {
                // Remove existing AI goals that make them passive
                if (mob.getPathfinder() != null) {
                    mob.setAware(true); // Make sure mob is aware of surroundings
                }
                
                // For Paper 1.21+, try to use the proper mob behavior modification
                if (entity instanceof org.bukkit.entity.Animals) {
                    org.bukkit.entity.Animals animal = (org.bukkit.entity.Animals) entity;
                    // Animals need special handling to become aggressive
                    animal.setBreed(false); // Stop breeding behavior
                    if (animal instanceof org.bukkit.entity.Ageable) {
                        ((org.bukkit.entity.Ageable) animal).setAdult(); // Make sure they're adult
                    }
                }
                
                // Set aggressive attributes
                if (entity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED) != null) {
                    entity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.25); // Slightly faster
                }
                if (entity.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE) != null) {
                    entity.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE).setBaseValue(16.0); // Larger follow range
                }
                
                // Try to set the mob to target players immediately
                Player nearestPlayer = findNearestPlayerForMob(entity, 16.0);
                if (nearestPlayer != null) {
                    mob.setTarget(nearestPlayer);
                    // Force pathfinding toward target
                    if (mob.getPathfinder() != null) {
                        mob.getPathfinder().moveTo(nearestPlayer, 1.2); // Move faster toward target
                    }
                }
                
                entity.setMetadata("ai_overridden", new FixedMetadataValue(YakRealms.getInstance(), true));
                
            } catch (Exception nmsError) {
                // NMS/Paper methods not available, rely on metadata flags
                logDebug("NMS methods not available for mob hostility, using metadata flags");
            }
            
        } catch (Exception e) {
            logError("Failed to make passive mob aggressive: " + e.getMessage(), e);
        }
        
        // Apply type-specific properties at the end
        applyTypeSpecificProperties();
    }

    /**
     * Finds the nearest player to the mob within range
     */
    private Player findNearestPlayerForMob(LivingEntity mobEntity, double maxDistance) {
        Player nearest = null;
        double nearestDistance = maxDistance;
        
        for (Player player : mobEntity.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(mobEntity.getLocation());
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }

    /**
     * Starts continuous targeting for passive mobs to keep them aggressive
     */
    private void startContinuousTargeting() {
        if (!(entity instanceof org.bukkit.entity.Mob mob)) return;
        
        // Schedule a repeating task to make passive mobs continuously target players
        Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                // Check if mob is still valid and has forced_hostile metadata
                if (!entity.isValid() || entity.isDead() || 
                    !entity.hasMetadata("forced_hostile")) {
                    return;
                }
                
                // If mob has no target or target is too far away, find a new one
                LivingEntity currentTarget = mob.getTarget();
                if (currentTarget == null || !currentTarget.isValid() || 
                    currentTarget.getLocation().distance(entity.getLocation()) > 20.0) {
                    
                    Player nearestPlayer = findNearestPlayerForMob(entity, 16.0);
                    if (nearestPlayer != null && !nearestPlayer.isDead() && 
                        nearestPlayer.getGameMode() != org.bukkit.GameMode.CREATIVE &&
                        nearestPlayer.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        
                        mob.setTarget(nearestPlayer);
                        
                        // Make sure the mob is actually trying to move toward the target
                        if (mob.getPathfinder() != null) {
                            try {
                                mob.getPathfinder().moveTo(nearestPlayer, 1.2); // Move faster
                            } catch (Exception e) {
                                // Pathfinder might not be available, ignore
                            }
                        }
                        
                        // Debug logging (remove in production)
                        logDebug("Passive mob " + entity.getType() + " targeting player " + nearestPlayer.getName());
                    }
                }
            } catch (Exception e) {
                // Ignore errors in targeting
            }
        }, 1L, 20L); // Start immediately, repeat every 1 second
    }

    /**
     * Applies type-specific properties
     */
    private void applyTypeSpecificProperties() {
        if (entity instanceof Zombie zombie) {
            zombie.setBaby(type.getId().equalsIgnoreCase("imp"));
        } else if (entity instanceof PigZombie pigZombie) {
            pigZombie.setAngry(true);
            pigZombie.setBaby(type.getId().equalsIgnoreCase("imp"));
        } else if (entity instanceof MagmaCube magmaCube) {
            magmaCube.setSize(Math.max(1, Math.min(tier, 4)));
        }
    }

    /**
     * Applies equipment to the entity
     */
    private void applyEquipment() {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;

        equipment.clear();

        // Weapon
        ItemStack weapon = createWeapon();
        if (weapon != null) {
            makeUnbreakable(weapon);
            equipment.setItemInMainHand(weapon);
            equipment.setItemInMainHandDropChance(0.0f);
        }

        // Armor
        int gearPieces = calculateGearPieces();
        ItemStack[] armor = createArmorSet(gearPieces);

        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) {
                makeUnbreakable(armor[i]);
                setArmorPiece(equipment, i, armor[i]);
            }
        }

        // No drops
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    /**
     * Applies health and stats to the entity with world boss scaling
     */
    private void applyHealthAndStats() {
        int health = calculateHealth();

        if (lightningMultiplier > 0) {
            health *= lightningMultiplier;
        }

        // World bosses get bonus health - REDUCED for better pacing
        if (isWorldBoss()) {
            health = (int) (health * 1.25); // 25% bonus health for world bosses (reduced from 50%)
        }

        health = Math.max(1, Math.min(health, 2_000_000));

        entity.setMaxHealth(health);
        entity.setHealth(health);

        lastKnownHealth = health;
        lastKnownMaxHealth = health;
    }

    /**
     * Calculates the mob's health based on tier and equipment
     */
    private int calculateHealth() {
        int baseHealth = MobUtils.calculateArmorHealth(entity);
        baseHealth = MobUtils.applyHealthMultiplier(baseHealth, tier, elite);

        // Special mob health overrides
        return switch (type.getId().toLowerCase()) {
            case "warden" -> 85_000;
            case "bossskeleton", "bossskeletondungeon" -> 115_000;
            case "frostwing", "chronos" -> ThreadLocalRandom.current().nextInt(210_000, 234_444);
            case "frozenelite" -> YakRealms.isT6Enabled() ? 200_000 : 100_000;
            case "frozenboss" -> YakRealms.isT6Enabled() ? 300_000 : 200_000;
            case "frozengolem" -> YakRealms.isT6Enabled() ? 400_000 : 200_000;
            default -> baseHealth;
        };
    }

    // ==================== EQUIPMENT CREATION ====================

    /**
     * Calculates number of gear pieces based on tier
     */
    private int calculateGearPieces() {
        if (tier >= 4 || elite) return 4;
        if (tier == 3) return RANDOM.nextBoolean() ? 3 : 4;
        return RANDOM.nextInt(3) + 1;
    }

    /**
     * Creates a weapon for the mob
     */
    private ItemStack createWeapon() {
        try {
            DropsManager drops = DropsManager.getInstance();
            if (drops != null) {
                int weaponType = ThreadLocalRandom.current().nextInt(1, 5);
                ItemStack weapon = drops.createDrop(tier, weaponType);

                if (weapon != null) {
                    if (elite) {
                        weapon.addUnsafeEnchantment(Enchantment.LOOTING, 1);
                    }
                    return weapon;
                }
            }
        } catch (Exception e) {
            // Fall through to default weapon
        }

        return createDefaultWeapon();
    }

    /**
     * Creates a default weapon based on tier
     */
    private ItemStack createDefaultWeapon() {
        Material material = switch (tier) {
            case 1 -> Material.WOODEN_SWORD;
            case 2 -> Material.STONE_SWORD;
            case 3 -> Material.IRON_SWORD;
            case 4, 6 -> Material.DIAMOND_SWORD;
            case 5 -> Material.GOLDEN_SWORD;
            default -> Material.WOODEN_SWORD;
        };

        ItemStack weapon = new ItemStack(material);
        if (elite) {
            weapon.addUnsafeEnchantment(Enchantment.LOOTING, 1);
        }
        return weapon;
    }

    /**
     * Creates armor set for the mob
     */
    private ItemStack[] createArmorSet(int pieces) {
        ItemStack[] armor = new ItemStack[4];
        Set<Integer> usedSlots = new HashSet<>();

        try {
            DropsManager drops = DropsManager.getInstance();
            if (drops != null) {
                while (pieces > 0 && usedSlots.size() < 4) {
                    int slot = RANDOM.nextInt(4);

                    if (!usedSlots.contains(slot)) {
                        ItemStack piece = drops.createDrop(tier, slot + 5);

                        if (piece != null) {
                            if (elite) {
                                piece.addUnsafeEnchantment(Enchantment.LOOTING, 1);
                            }
                            armor[slot] = piece;
                            usedSlots.add(slot);
                        }
                        pieces--;
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to default armor
        }

        return armor;
    }

    /**
     * Sets an armor piece on the equipment
     */
    private void setArmorPiece(EntityEquipment equipment, int slot, ItemStack item) {
        switch (slot) {
            case 0 -> equipment.setHelmet(item);
            case 1 -> equipment.setChestplate(item);
            case 2 -> equipment.setLeggings(item);
            case 3 -> equipment.setBoots(item);
        }
    }

    /**
     * Makes an item unbreakable using modern Adventure API
     */
    private void makeUnbreakable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
    }

    // ==================== SPECIAL FEATURES ====================

    /**
     * Enhances the mob as a lightning mob using Adventure API
     */
    public void enhanceAsLightningMob(int multiplier) {
        if (!isValid()) return;

        // Cache entity reference
        LivingEntity currentEntity = entity;
        if (currentEntity == null || !currentEntity.isValid()) {
            return;
        }

        try {
            this.lightningMultiplier = multiplier;

            double newHealth = currentEntity.getMaxHealth() * multiplier;
            currentEntity.setMaxHealth(newHealth);
            currentEntity.setHealth(newHealth);
            currentEntity.setGlowing(true);

            // Create lightning enhanced name using Adventure Components
            Component lightningName = Component.text()
                    .color(NamedTextColor.GOLD)
                    .append(Component.text("⚡ Lightning "))
                    .append(Component.text(baseDisplayComponent != null ?
                            componentToLegacyString(baseDisplayComponent).replaceAll("§[0-9a-fk-or]", "") :
                            type.getId()))
                    .append(Component.text(" ⚡"))
                    .build();

            baseDisplayComponent = lightningName;
            baseDisplayName = componentToLegacyString(lightningName);
            originalDisplayComponent = lightningName;
            originalDisplayName = baseDisplayName; // Update original name too

            updateMobName();

            YakRealms plugin = YakRealms.getInstance();
            currentEntity.setMetadata("LightningMultiplier", new FixedMetadataValue(plugin, multiplier));
            currentEntity.setMetadata("LightningMob", new FixedMetadataValue(plugin, baseDisplayName));

            logInfo("Enhanced %s as lightning mob (x%d)", uniqueMobId, multiplier);

        } catch (Exception e) {
            logError("Lightning enhancement failed", e);
        }
    }

    // ==================== CRITICAL HIT SYSTEM ====================

    public boolean rollForCritical() {
        return CritManager.getInstance().initiateCrit(this);
    }

    public double applyCritDamageToPlayer(Player player, double baseDamage) {
        //return CritManager.getInstance().handleCritAttack(this, player, baseDamage);
        return 0;
    }

    public boolean isInCriticalState() {
        return entity != null && CritManager.getInstance().isInCritState(entity.getUniqueId());
    }

    public boolean isCritReadyToAttack() {
        return entity != null && CritManager.getInstance().isCritCharged(entity.getUniqueId());
    }

    public int getCritCountdown() {
        return entity != null ? CritManager.getInstance().getCritCountdown(entity.getUniqueId()) : 0;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Finds a safe spawn location near the target
     */
    private Location findSafeSpawnLocation(Location target) {
        if (target == null || target.getWorld() == null) return null;

        if (isSafeLocation(target)) {
            return target.clone().add(0.5, 0, 0.5);
        }

        // Try nearby locations
        for (int i = 0; i < 5; i++) {
            Location candidate = target.clone().add(
                    RANDOM.nextDouble() * 4 - 2,
                    RANDOM.nextDouble() * 2,
                    RANDOM.nextDouble() * 4 - 2
            );

            if (isSafeLocation(candidate)) {
                return candidate;
            }
        }

        // Last resort - spawn above
        return target.clone().add(0.5, 2, 0.5);
    }

    /**
     * Checks if a location is safe for spawning
     */
    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (loc.getY() < 0 || loc.getY() > loc.getWorld().getMaxHeight() - 3) return false;

        Material block = loc.getBlock().getType();
        return block != Material.LAVA && block != Material.BEDROCK;
    }

    /**
     * Determines the entity type for spawning
     */
    private EntityType determineEntityType() {
        EntityType configuredType = type.getEntityType();
        if (configuredType != null && isValidEntityType(configuredType)) {
            return configuredType;
        }

        // Fallback based on type ID
        return switch (type.getId().toLowerCase()) {
            case "skeleton" -> EntityType.SKELETON;
            case "witherskeleton", "wither_skeleton" -> EntityType.WITHER_SKELETON;
            case "zombie" -> EntityType.ZOMBIE;
            case "spider" -> EntityType.SPIDER;
            case "cavespider", "cave_spider" -> EntityType.CAVE_SPIDER;
            case "magmacube", "magma_cube" -> EntityType.MAGMA_CUBE;
            case "zombifiedpiglin", "pigzombie" -> EntityType.ZOMBIFIED_PIGLIN;
            case "enderman" -> EntityType.ENDERMAN;
            case "creeper" -> EntityType.CREEPER;
            case "blaze" -> EntityType.BLAZE;
            case "warden" -> EntityType.WARDEN;
            case "golem", "irongolem" -> EntityType.IRON_GOLEM;
            default -> EntityType.SKELETON;
        };
    }

    /**
     * Validates if an entity type can be used
     */
    private boolean isValidEntityType(EntityType type) {
        return type != null &&
                type.getEntityClass() != null &&
                LivingEntity.class.isAssignableFrom(type.getEntityClass()) &&
                type.isSpawnable();
    }

    /**
     * Checks if the mob is undead
     */
    private boolean isUndead() {
        if (entity == null) return false;

        EntityType type = entity.getType();
        return type == EntityType.SKELETON ||
                type == EntityType.WITHER_SKELETON ||
                type == EntityType.ZOMBIE ||
                type == EntityType.ZOMBIE_VILLAGER ||
                type == EntityType.HUSK ||
                type == EntityType.DROWNED ||
                type == EntityType.STRAY ||
                type == EntityType.PHANTOM;
    }

    /**
     * Prevents sunlight damage for undead mobs
     */
    private void preventSunlightDamage() {
        if (!isUndead() || entity == null) return;

        if (entity.getFireTicks() > 0) {
            entity.setFireTicks(0);
        }
    }

    /**
     * Checks if health has changed significantly
     */
    private boolean hasHealthChangedSignificantly() {
        if (entity == null) return false;

        try {
            double currentHealth = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            if (Math.abs(currentHealth - lastKnownHealth) > (maxHealth * HEALTH_CHANGE_THRESHOLD)) {
                lastKnownHealth = currentHealth;
                lastKnownMaxHealth = maxHealth;
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    /**
     * Gets color based on health percentage using Adventure API
     */
    private NamedTextColor getHealthColor(double percentage) {
        if (percentage > 75) return NamedTextColor.GREEN;
        if (percentage > 50) return NamedTextColor.YELLOW;
        if (percentage > 25) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    /**
     * Gets health bar color based on percentage using Adventure API
     */
    private NamedTextColor getHealthBarColor(double percentage) {
        if (isInCriticalState()) return NamedTextColor.LIGHT_PURPLE;
        return getHealthColor(percentage);
    }

    /**
     * Cleans up resources
     */
    private void cleanup() {
        valid.set(false);
        removeHologram();

        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        entity = null;
    }

    // ==================== STATE CHECKS ====================

    /**
     * Checks if the mob is still valid
     */
    public boolean isValid() {
        if (!valid.get()) return false;
        
        LivingEntity currentEntity = entity;
        return currentEntity != null && currentEntity.isValid() && !currentEntity.isDead();
    }

    /**
     * Gets the entity (thread-safe)
     */
    public LivingEntity getEntity() {
        return entity; // Volatile field ensures thread-safe read
    }

    /**
     * Gets the current health percentage
     */
    public double getHealthPercentage() {
        LivingEntity currentEntity = entity;
        if (currentEntity == null) return 0.0;

        try {
            double current = currentEntity.getHealth();
            double max = currentEntity.getMaxHealth();
            return max > 0 ? (current / max) * 100.0 : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ==================== LOGGING UTILITIES ====================

    private boolean logDebug(String format, Object... args) {
        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("[CustomMob] " + format, args));
            return true;
        }
        return false;
    }

    private void logInfo(String format, Object... args) {
        LOGGER.info(String.format("[CustomMob] " + format, args));
    }

    private void logError(String message, Exception e) {
        LOGGER.log(Level.WARNING, "[CustomMob] " + message + " for " + uniqueMobId, e);
    }

    // ==================== LEGACY COMPATIBILITY AND NEW METHODS ====================

    public String getCustomName() {
        return baseDisplayName;
    }

    /**
     * ADDED: Get the original name for drop notifications (without health bars)
     */
    public String getOriginalName() {
        return originalDisplayName != null ? originalDisplayName : baseDisplayName;
    }

    /**
     * ADDED: Get the original display component for modern systems
     */
    public Component getOriginalDisplayComponent() {
        return originalDisplayComponent != null ? originalDisplayComponent : baseDisplayComponent;
    }

    /**
     * ADDED: Get the current display component for modern systems
     */
    public Component getCurrentDisplayComponent() {
        return generateCurrentDisplayComponent();
    }

    public boolean isInCombat() {
        return inCombat;
    }

    public boolean isHologramActive() {
        return hologramActive.get();
    }

    /**
     * Updates the health bar display with Paper 1.21.8 optimizations.
     * 
     * <p>This is a convenience method that triggers a complete display update,
     * including health bars, combat indicators, and hologram synchronization.
     * 
     * @see #updateDisplayElements() for the complete update process
     */
    public void updateHealthBar() {
        updateDisplayElements();
    }

    /**
     * Asynchronously refreshes the health bar using Paper's enhanced scheduling system.
     * 
     * <p>This method ensures thread-safe health bar updates by scheduling the update
     * on the main thread if called from an async context. Uses Paper 1.21.8's
     * improved entity scheduling when available.
     * 
     * @implNote Prefers Paper's entity scheduler over global scheduler for better performance
     */
    public void refreshHealthBar() {
        if (!isValid()) return;
        
        final LivingEntity currentEntity = entity;
        if (currentEntity != null) {
            // Try to use Paper's entity-specific scheduler for better performance
            boolean usedPaperScheduler = false;
            try {
                Object scheduler = currentEntity.getClass().getMethod("getScheduler").invoke(currentEntity);
                if (scheduler != null) {
                    scheduler.getClass().getMethod("run", 
                        org.bukkit.plugin.Plugin.class, Runnable.class, Runnable.class)
                        .invoke(scheduler, YakRealms.getInstance(), 
                            (Runnable) this::updateDisplayElements, null);
                    usedPaperScheduler = true;
                }
            } catch (Exception paperError) {
                // Paper scheduler not available or failed
            }
            
            if (!usedPaperScheduler) {
                // Fallback to global scheduler
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), this::updateDisplayElements);
            }
        }
    }

    public void updateDamageTime() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * ADDED: Get mob information for debugging
     */
    public Map<String, Object> getMobInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("uniqueId", uniqueMobId);
        info.put("type", type.getId());
        info.put("tier", tier);
        info.put("elite", elite);
        info.put("isNamedElite", isNamedElite);
        info.put("eliteConfigName", eliteConfigName);
        info.put("isWorldBoss", isWorldBoss());
        info.put("baseDisplayName", baseDisplayName);
        info.put("originalDisplayName", originalDisplayName);
        info.put("inCombat", inCombat);
        info.put("valid", isValid());
        info.put("lightningMultiplier", lightningMultiplier);

        if (entity != null) {
            info.put("entityType", entity.getType().name());
            info.put("health", entity.getHealth());
            info.put("maxHealth", entity.getMaxHealth());
            info.put("location", entity.getLocation().toString());
        }

        return info;
    }
}