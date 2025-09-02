package com.rednetty.server.core.mechanics.world.mobs.behaviors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.impl.*;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized manager for Elite Archetype behaviors.
 * Ensures tight integration between archetype assignment, ability management, and hologram display.
 * 
 * This class provides:
 * - Automatic archetype assignment based on tier and configuration
 * - Centralized ability state management
 * - Hologram integration for real-time ability display
 * - Clean metadata management and cleanup
 * - Thread-safe operations for multiplayer environments
 */
public class EliteArchetypeBehaviorManager {
    
    private static final Logger LOGGER = YakRealms.getInstance().getLogger();
    private static volatile EliteArchetypeBehaviorManager instance;
    
    // Thread-safe maps for tracking elite states
    private final Map<String, EliteBehaviorArchetype> mobArchetypes = new ConcurrentHashMap<>();
    private final Map<String, String> mobCurrentAbilities = new ConcurrentHashMap<>();
    private final Map<String, Long> abilityStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> mobStateData = new ConcurrentHashMap<>();
    
    // Behavior instance cache for performance
    private final Map<EliteBehaviorArchetype, MobBehavior> behaviorInstances = new ConcurrentHashMap<>();
    
    private EliteArchetypeBehaviorManager() {
        initializeBehaviorInstances();
    }
    
    public static EliteArchetypeBehaviorManager getInstance() {
        if (instance == null) {
            synchronized (EliteArchetypeBehaviorManager.class) {
                if (instance == null) {
                    instance = new EliteArchetypeBehaviorManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Pre-initialize behavior instances for better performance
     */
    private void initializeBehaviorInstances() {
        try {
            behaviorInstances.put(EliteBehaviorArchetype.BRUTE, new BruteBehavior());
            behaviorInstances.put(EliteBehaviorArchetype.ASSASSIN, new AssassinBehavior());
            behaviorInstances.put(EliteBehaviorArchetype.ELEMENTALIST, new ElementalistBehavior());
            // Add other archetypes as their behaviors are implemented
            
            LOGGER.info("Initialized " + behaviorInstances.size() + " elite archetype behaviors");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize elite archetype behaviors", e);
        }
    }
    
    /**
     * Assigns an archetype to an elite mob with comprehensive setup
     * 
     * @param mob The elite mob to assign an archetype to
     * @param preferredArchetype Optional preferred archetype (can be null for random)
     * @return true if assignment was successful
     */
    public boolean assignArchetype(CustomMob mob, EliteBehaviorArchetype preferredArchetype) {
        if (mob == null || !mob.isElite() || !mob.isValid()) {
            return false;
        }
        
        LivingEntity entity = mob.getEntity();
        if (entity == null) {
            return false;
        }
        
        try {
            // Determine archetype
            EliteBehaviorArchetype archetype = preferredArchetype;
            if (archetype == null || !archetype.isAvailableForTier(mob.getTier())) {
                // Get random appropriate archetype for tier
                List<EliteBehaviorArchetype> available = EliteBehaviorArchetype.getArchetypesForTier(mob.getTier());
                if (!available.isEmpty()) {
                    archetype = available.get((int) (Math.random() * available.size()));
                } else {
                    archetype = EliteBehaviorArchetype.BRUTE; // Fallback
                }
            }
            
            // Store archetype mapping
            mobArchetypes.put(mob.getUniqueMobId(), archetype);
            
            // Set comprehensive metadata
            setArchetypeMetadata(entity, archetype, mob);
            
            // Initialize state data
            mobStateData.put(mob.getUniqueMobId(), new ConcurrentHashMap<>());
            
            // Apply the behavior
            MobBehavior behavior = behaviorInstances.get(archetype);
            if (behavior != null && behavior.canApplyTo(mob)) {
                behavior.onApply(mob);
                MobBehaviorManager.getInstance().applyBehavior(mob, behavior);
                
                LOGGER.info(String.format("Assigned %s archetype to elite mob %s (Tier %d)", 
                    archetype.getDisplayName(), mob.getUniqueMobId(), mob.getTier()));
                return true;
            } else {
                LOGGER.warning(String.format("No behavior implementation found for archetype %s", 
                    archetype.getDisplayName()));
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to assign archetype to mob " + mob.getUniqueMobId(), e);
            return false;
        }
    }
    
    /**
     * Sets comprehensive metadata for archetype integration
     */
    private void setArchetypeMetadata(LivingEntity entity, EliteBehaviorArchetype archetype, CustomMob mob) {
        YakRealms plugin = YakRealms.getInstance();
        
        // Core archetype metadata
        entity.setMetadata("elite_archetype", new FixedMetadataValue(plugin, archetype.name()));
        entity.setMetadata("archetype_display_name", new FixedMetadataValue(plugin, archetype.getDisplayName()));
        entity.setMetadata("archetype_color", new FixedMetadataValue(plugin, archetype.getDisplayColor()));
        
        // Power scaling metadata
        double powerScaling = archetype.getPowerScaling(mob.getTier());
        entity.setMetadata("archetype_power_scaling", new FixedMetadataValue(plugin, powerScaling));
        
        // Ability metadata
        entity.setMetadata("archetype_abilities", new FixedMetadataValue(plugin, 
            String.join(",", archetype.getAbilities())));
        
        // State tracking metadata
        entity.setMetadata("current_ability", new FixedMetadataValue(plugin, "NONE"));
        entity.setMetadata("ability_state", new FixedMetadataValue(plugin, "READY"));
    }
    
    /**
     * Records when an elite starts using an ability with improved tracking
     */
    public void startAbility(CustomMob mob, String abilityName) {
        if (mob == null || abilityName == null) return;
        
        try {
            String mobId = mob.getUniqueMobId();
            LivingEntity entity = mob.getEntity();
            
            if (entity != null) {
                // Clear any previous ability first to prevent conflicts
                String previousAbility = mobCurrentAbilities.get(mobId);
                if (previousAbility != null && !previousAbility.equals("NONE")) {
                    endAbility(mob, previousAbility);
                }
                
                // Update tracking maps with synchronized access
                synchronized (mobCurrentAbilities) {
                    mobCurrentAbilities.put(mobId, abilityName);
                    abilityStartTimes.put(mobId, System.currentTimeMillis());
                }
                
                // Update metadata for hologram integration - simplified
                entity.setMetadata("current_ability", 
                    new FixedMetadataValue(YakRealms.getInstance(), abilityName));
                entity.setMetadata("ability_state", 
                    new FixedMetadataValue(YakRealms.getInstance(), "ACTIVE"));
                
                // Throttled hologram update to reduce spam
                if (System.currentTimeMillis() % 100 == 0) { // Only update every ~100ms
                    mob.refreshHealthBar();
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to start ability tracking", e);
        }
    }
    
    /**
     * Records when an elite finishes using an ability with improved cleanup
     */
    public void endAbility(CustomMob mob, String abilityName) {
        if (mob == null) return;
        
        try {
            String mobId = mob.getUniqueMobId();
            LivingEntity entity = mob.getEntity();
            
            if (entity != null) {
                // Verify this is the correct ability to end
                String currentAbility = mobCurrentAbilities.get(mobId);
                if (currentAbility != null && !currentAbility.equals(abilityName)) {
                    LOGGER.fine("Ability mismatch: trying to end '" + abilityName + "' but current is '" + currentAbility + "'");
                    return; // Don't end if it's not the current ability
                }
                
                // Clear tracking maps with synchronized access
                synchronized (mobCurrentAbilities) {
                    mobCurrentAbilities.remove(mobId);
                    abilityStartTimes.remove(mobId);
                }
                
                // Update metadata for hologram integration
                entity.setMetadata("current_ability", 
                    new FixedMetadataValue(YakRealms.getInstance(), "NONE"));
                entity.setMetadata("ability_state", 
                    new FixedMetadataValue(YakRealms.getInstance(), "READY"));
                
                // Throttled hologram update
                if (System.currentTimeMillis() % 100 == 0) {
                    mob.refreshHealthBar();
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to end ability tracking", e);
        }
    }
    
    /**
     * Gets the current ability being used by an elite
     */
    public String getCurrentAbility(CustomMob mob) {
        if (mob == null) return null;
        return mobCurrentAbilities.get(mob.getUniqueMobId());
    }
    
    /**
     * Gets the archetype assigned to an elite mob
     */
    public EliteBehaviorArchetype getArchetype(CustomMob mob) {
        if (mob == null) return null;
        return mobArchetypes.get(mob.getUniqueMobId());
    }
    
    /**
     * Gets how long the current ability has been active (in milliseconds)
     */
    public long getAbilityDuration(CustomMob mob) {
        if (mob == null) return 0;
        
        Long startTime = abilityStartTimes.get(mob.getUniqueMobId());
        if (startTime == null) return 0;
        
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Stores custom state data for an elite mob
     */
    public void setStateData(CustomMob mob, String key, Object value) {
        if (mob == null || key == null) return;
        
        mobStateData.computeIfAbsent(mob.getUniqueMobId(), k -> new ConcurrentHashMap<>())
                   .put(key, value);
    }
    
    /**
     * Retrieves custom state data for an elite mob
     */
    @SuppressWarnings("unchecked")
    public <T> T getStateData(CustomMob mob, String key, Class<T> type) {
        if (mob == null || key == null) return null;
        
        Map<String, Object> stateMap = mobStateData.get(mob.getUniqueMobId());
        if (stateMap == null) return null;
        
        Object value = stateMap.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        
        return null;
    }
    
    /**
     * Comprehensive cleanup when an elite mob is removed
     */
    public void cleanupElite(CustomMob mob) {
        if (mob == null) return;
        
        try {
            String mobId = mob.getUniqueMobId();
            
            // Clean up tracking maps
            mobArchetypes.remove(mobId);
            mobCurrentAbilities.remove(mobId);
            abilityStartTimes.remove(mobId);
            mobStateData.remove(mobId);
            
            // Clean up entity metadata
            LivingEntity entity = mob.getEntity();
            if (entity != null) {
                YakRealms plugin = YakRealms.getInstance();
                
                // Remove all archetype-related metadata
                entity.removeMetadata("elite_archetype", plugin);
                entity.removeMetadata("archetype_display_name", plugin);
                entity.removeMetadata("archetype_color", plugin);
                entity.removeMetadata("archetype_power_scaling", plugin);
                entity.removeMetadata("archetype_abilities", plugin);
                entity.removeMetadata("current_ability", plugin);
                entity.removeMetadata("ability_state", plugin);
                
                // Remove archetype-specific metadata
                entity.removeMetadata("brute_ability", plugin);
                entity.removeMetadata("elemental_phase", plugin);
                // Add more as needed for other archetypes
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to cleanup elite mob data", e);
        }
    }
    
    /**
     * Gets formatted ability information for hologram display with reduced verbosity
     */
    public String getAbilityDisplayInfo(CustomMob mob) {
        if (mob == null) return null;
        
        EliteBehaviorArchetype archetype = getArchetype(mob);
        if (archetype == null) return null;
        
        // Use tier-based coloring to match game's existing system
        org.bukkit.ChatColor tierColor = com.rednetty.server.core.mechanics.world.mobs.utils.MobUtils.getTierColor(mob.getTier());
        
        String currentAbility = getCurrentAbility(mob);
        if (currentAbility != null && !currentAbility.equals("NONE")) {
            // Show active ability with simplified display
            String shortAbility = formatAbilityName(currentAbility);
            if (shortAbility.length() > 8) {
                shortAbility = shortAbility.substring(0, 6) + "..";
            }
            return tierColor + "[" + shortAbility + "]";
        }
        
        // Show simplified archetype name
        String archetypeName = archetype.getDisplayName();
        if (archetypeName.length() > 10) {
            archetypeName = archetypeName.substring(0, 8) + "..";
        }
        return tierColor + "[" + archetypeName + "]";
    }
    
    /**
     * Formats ability names for display - ultra simplified
     */
    private String formatAbilityName(String abilityName) {
        if (abilityName == null) return "ACT";
        
        return switch (abilityName.toUpperCase()) {
            case "CHARGING" -> "CHG";
            case "GROUND_SLAM" -> "SLM";
            case "FIRE_MASTERY" -> "FIR";
            case "ICE_MASTERY" -> "ICE";
            case "LIGHTNING_MASTERY" -> "LTN";
            case "BERSERKER_RAGE" -> "RGE";
            case "STEALTH" -> "STL";
            case "BACKSTAB" -> "BST";
            case "SHIELD_WALL" -> "SHD";
            case "ELEMENTAL_SHIELD" -> "ESH";
            default -> "ACT";
        };
    }
    
    /**
     * Gets formatted abilities for archetype display
     */
    private String getFormattedAbilities(EliteBehaviorArchetype archetype) {
        return switch (archetype) {
            case BRUTE -> "§6Charge §7• §cGround Slam §7• §4Rage";
            case ASSASSIN -> "§5Stealth §7• §4Backstab §7• §2Poison";
            case GUARDIAN -> "§bShields §7• §3Reflect §7• §1Barriers";
            case ELEMENTALIST -> "§cFire §7• §bIce §7• §eLightning";
            case NECROMANCER -> "§8Summons §7• §5Drain §7• §0Curses";
            case BERSERKER -> "§cRage Stacks §7• §6Frenzy §7• §eRampage";
            case VOID_WALKER -> "§dRifts §7• §3Gravity §7• §8Phase";
            case SHAPESHIFTER -> "§bAdapt §7• §eMimic §7• §aEvolution";
            case WARMASTER -> "§eReinforce §7• §cTactics §7• §9Commands";
            case COSMIC_ENTITY -> "§fReality §7• §eStorms §7• §5Time";
            case ANCIENT_ONE -> "§2Forbidden §7• §4Curses §7• §7Wisdom";
            case AVATAR_OF_DESTRUCTION -> "§4Cataclysm §7• §cAura §7• §8Apocalypse";
        };
    }
    
    /**
     * Gets all active elite mobs for debugging/monitoring
     */
    public Map<String, EliteBehaviorArchetype> getActiveElites() {
        return new HashMap<>(mobArchetypes);
    }
    
    /**
     * Gets statistics about archetype usage
     */
    public Map<EliteBehaviorArchetype, Integer> getArchetypeStats() {
        Map<EliteBehaviorArchetype, Integer> stats = new HashMap<>();
        for (EliteBehaviorArchetype archetype : mobArchetypes.values()) {
            stats.merge(archetype, 1, Integer::sum);
        }
        return stats;
    }
    
    /**
     * Gets the total number of elite spawns managed by this system.
     * 
     * @return The total count of elite spawns
     */
    public int getTotalEliteSpawns() {
        return mobArchetypes.size();
    }
    
    /**
     * Gets archetype spawn statistics.
     * 
     * @return A map of archetype names to spawn counts
     */
    public Map<String, Integer> getArchetypeSpawnStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (EliteBehaviorArchetype archetype : mobArchetypes.values()) {
            String name = archetype.getDisplayName();
            stats.put(name, stats.getOrDefault(name, 0) + 1);
        }
        return stats;
    }
    
    /**
     * Clears all managed mob data and state.
     */
    public void clearAll() {
        mobArchetypes.clear();
        mobStateData.clear();
        mobCurrentAbilities.clear();
        abilityStartTimes.clear();
        LOGGER.info("Cleared all elite archetype data");
    }
}