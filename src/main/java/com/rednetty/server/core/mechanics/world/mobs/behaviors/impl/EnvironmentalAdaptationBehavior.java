package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Environmental adaptation behavior for Paper Spigot 1.21.8 demonstrating dynamic mob adaptation.
 * 
 * <p>This behavior showcases advanced environmental interaction capabilities:
 * <ul>
 *   <li><strong>Biome Adaptation:</strong> Mobs gain bonuses based on their current biome</li>
 *   <li><strong>Time-of-Day Effects:</strong> Different abilities during day/night cycles</li>
 *   <li><strong>Weather Responsiveness:</strong> Enhanced abilities during storms</li>
 *   <li><strong>Terrain Awareness:</strong> Movement and combat bonuses based on terrain</li>
 *   <li><strong>Resource Efficiency:</strong> Minimal performance impact through smart caching</li>
 * </ul>
 * 
 * <h3>Adaptation Categories:</h3>
 * <ul>
 *   <li><strong>Desert Adaptation:</strong> Fire resistance, faster movement in sand</li>
 *   <li><strong>Ocean Adaptation:</strong> Water breathing, enhanced underwater combat</li>
 *   <li><strong>Mountain Adaptation:</strong> Fall damage immunity, increased health at altitude</li>
 *   <li><strong>Forest Adaptation:</strong> Regeneration near trees, stealth bonuses</li>
 *   <li><strong>Nether Adaptation:</strong> Immunity to hostile environment effects</li>
 * </ul>
 * 
 * @author YakRealms Development Team
 * @version 1.0.0
 * @see MobBehavior for the behavior interface
 * @see CustomMob for mob system integration
 */
public class EnvironmentalAdaptationBehavior implements MobBehavior {
    
    // ==================== BEHAVIOR CONSTANTS ====================
    
    /** Unique identifier for this behavior type */
    private static final String BEHAVIOR_ID = "environmental_adaptation";
    
    /** Priority for environmental behaviors (medium priority) */
    private static final int BEHAVIOR_PRIORITY = 200;
    
    /** How often to check environmental conditions (in ticks) */
    private static final int ENVIRONMENT_CHECK_INTERVAL = 40; // 2 seconds
    
    /** Minimum tier for significant adaptation effects */
    private static final int MINIMUM_EFFECTIVE_TIER = 2;
    
    // ==================== BIOME CATEGORIZATION ====================
    
    /** Desert and hot biomes - Registry-safe initialization for Paper 1.21.8 */
    private static final Set<Biome> DESERT_BIOMES = createBiomeSet(
        "desert", "badlands", "wooded_badlands", "eroded_badlands", 
        "savanna", "savanna_plateau", "windswept_savanna"
    );
    
    /** Ocean and water biomes - Registry-safe initialization for Paper 1.21.8 */
    private static final Set<Biome> OCEAN_BIOMES = createBiomeSet(
        "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean",
        "lukewarm_ocean", "deep_lukewarm_ocean", "warm_ocean", 
        "frozen_ocean", "deep_frozen_ocean"
    );
    
    /** Mountain and highland biomes - Registry-safe initialization for Paper 1.21.8 */
    private static final Set<Biome> MOUNTAIN_BIOMES = createBiomeSet(
        "mountains", "gravelly_mountains", "modified_gravelly_mountains",
        "windswept_hills", "windswept_gravelly_hills", "windswept_forest",
        "stony_peaks", "jagged_peaks", "frozen_peaks", "snowy_slopes"
    );
    
    /** Forest and woodland biomes - Registry-safe initialization for Paper 1.21.8 */
    private static final Set<Biome> FOREST_BIOMES = createBiomeSet(
        "forest", "dark_forest", "birch_forest", "old_growth_birch_forest",
        "tall_birch_forest", "flower_forest", "cherry_grove",
        "jungle", "bamboo_jungle", "sparse_jungle", "jungle_edge", "modified_jungle",
        "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", 
        "giant_tree_taiga", "giant_spruce_taiga"
    );
    
    // ==================== INSTANCE STATE ====================
    
    /** Last tick when environment was checked */
    private long lastEnvironmentCheck = 0;
    
    /** Currently active adaptations */
    private Set<AdaptationType> activeAdaptations = EnumSet.noneOf(AdaptationType.class);
    
    /** Cached biome information to avoid frequent lookups */
    private Biome cachedBiome = null;
    
    /** Random instance for environmental effects */
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    
    /**
     * Types of environmental adaptations that can be active.
     */
    private enum AdaptationType {
        DESERT_HEAT_RESISTANCE,
        OCEAN_WATER_MASTERY,
        MOUNTAIN_ALTITUDE_BOOST,
        FOREST_NATURAL_HARMONY,
        NETHER_HELLISH_IMMUNITY,
        NIGHT_SHADOW_AFFINITY,
        STORM_ELECTRICAL_CHARGE
    }
    
    // ==================== BEHAVIOR INTERFACE IMPLEMENTATION ====================
    
    @Override
    public String getBehaviorId() {
        return BEHAVIOR_ID;
    }
    
    @Override
    public int getPriority() {
        return BEHAVIOR_PRIORITY;
    }
    
    @Override
    public boolean canApplyTo(CustomMob mob) {
        // Can apply to any valid mob of tier 1 or higher
        return mob != null && 
               mob.isValid() && 
               mob.getTier() >= 1;
    }
    
    @Override
    public boolean isActive() {
        // Always active to continuously monitor environment
        return true;
    }
    
    @Override
    public void onApply(CustomMob mob) {
        try {
            // Perform initial environment assessment
            assessEnvironment(mob);
            
            // Apply initial adaptations based on spawn location
            applyEnvironmentalAdaptations(mob);
            
        } catch (Exception e) {
            System.err.println("[EnvironmentalAdaptationBehavior] Error applying behavior: " + e.getMessage());
        }
    }
    
    @Override
    public void onTick(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || !entity.isValid()) return;
        
        try {
            long currentTick = entity.getTicksLived();
            
            // Check environment periodically to avoid performance impact
            if (currentTick - lastEnvironmentCheck >= ENVIRONMENT_CHECK_INTERVAL) {
                assessEnvironment(mob);
                updateAdaptations(mob);
                lastEnvironmentCheck = currentTick;
            }
            
            // Apply ongoing environmental effects
            applyOngoingEffects(mob);
            
        } catch (Exception e) {
            // Ignore tick errors to prevent spam
        }
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        try {
            // Environmental damage reduction based on adaptations
            if (activeAdaptations.contains(AdaptationType.DESERT_HEAT_RESISTANCE)) {
                // Reduce fire and lava damage
                // This would be handled in the damage calculation system
            }
            
            if (activeAdaptations.contains(AdaptationType.MOUNTAIN_ALTITUDE_BOOST)) {
                // Reduce fall damage
                // This would be handled in the damage calculation system
            }
            
        } catch (Exception e) {
            // Ignore damage processing errors
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        try {
            double modifiedDamage = damage;
            
            // Apply adaptation-based damage bonuses
            if (activeAdaptations.contains(AdaptationType.OCEAN_WATER_MASTERY) && 
                isInWater(mob.getEntity())) {
                modifiedDamage *= 1.2; // 20% bonus in water
            }
            
            if (activeAdaptations.contains(AdaptationType.NIGHT_SHADOW_AFFINITY) && 
                isNightTime(mob.getEntity().getWorld())) {
                modifiedDamage *= 1.15; // 15% bonus at night
            }
            
            if (activeAdaptations.contains(AdaptationType.STORM_ELECTRICAL_CHARGE) && 
                isStorming(mob.getEntity().getWorld())) {
                modifiedDamage *= 1.25; // 25% bonus during storms
                
                // Chance to apply shock effect
                if (random.nextDouble() < 0.2 && target instanceof Player) {
                    ((Player) target).addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 40, 0, false, false));
                }
            }
            
            return modifiedDamage;
            
        } catch (Exception e) {
            return damage; // Return original damage on error
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        try {
            // Environmental awareness bonuses
            if (activeAdaptations.contains(AdaptationType.FOREST_NATURAL_HARMONY)) {
                // Enhanced detection range in forests
                // This would integrate with the mob's AI system
            }
            
            if (activeAdaptations.contains(AdaptationType.NIGHT_SHADOW_AFFINITY) && 
                isNightTime(mob.getEntity().getWorld())) {
                // Note: Removed blindness effect for better player experience
            }
            
        } catch (Exception e) {
            // Ignore detection errors
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        try {
            // Environmental death effects based on adaptations
            Location deathLoc = mob.getEntity().getLocation();
            
            if (activeAdaptations.contains(AdaptationType.FOREST_NATURAL_HARMONY)) {
                // Grow plants around death location
                growPlantsAroundLocation(deathLoc);
            }
            
            if (activeAdaptations.contains(AdaptationType.STORM_ELECTRICAL_CHARGE) &&
                isStorming(deathLoc.getWorld())) {
                // Lightning strike at death location
                deathLoc.getWorld().strikeLightning(deathLoc);
            }
            
        } catch (Exception e) {
            // Ignore death processing errors
        }
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        try {
            // Clean up environmental adaptations
            removeAllAdaptations(mob);
            
            // Reset state
            activeAdaptations.clear();
            cachedBiome = null;
            lastEnvironmentCheck = 0;
            
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Assesses the current environment and updates cached information.
     */
    private void assessEnvironment(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            Location loc = entity.getLocation();
            cachedBiome = loc.getBlock().getBiome();
            
        } catch (Exception e) {
            // Ignore assessment errors
        }
    }
    
    /**
     * Updates active adaptations based on current environment.
     */
    private void updateAdaptations(CustomMob mob) {
        Set<AdaptationType> newAdaptations = EnumSet.noneOf(AdaptationType.class);
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            World world = entity.getWorld();
            Location loc = entity.getLocation();
            
            // Biome-based adaptations
            if (DESERT_BIOMES.contains(cachedBiome)) {
                newAdaptations.add(AdaptationType.DESERT_HEAT_RESISTANCE);
            } else if (OCEAN_BIOMES.contains(cachedBiome)) {
                newAdaptations.add(AdaptationType.OCEAN_WATER_MASTERY);
            } else if (MOUNTAIN_BIOMES.contains(cachedBiome)) {
                newAdaptations.add(AdaptationType.MOUNTAIN_ALTITUDE_BOOST);
            } else if (FOREST_BIOMES.contains(cachedBiome)) {
                newAdaptations.add(AdaptationType.FOREST_NATURAL_HARMONY);
            }
            
            // Dimension-based adaptations
            if (world.getEnvironment() == World.Environment.NETHER) {
                newAdaptations.add(AdaptationType.NETHER_HELLISH_IMMUNITY);
            }
            
            // Time-based adaptations
            if (isNightTime(world)) {
                newAdaptations.add(AdaptationType.NIGHT_SHADOW_AFFINITY);
            }
            
            // Weather-based adaptations
            if (isStorming(world)) {
                newAdaptations.add(AdaptationType.STORM_ELECTRICAL_CHARGE);
            }
            
            // Apply changes if different from current adaptations
            if (!newAdaptations.equals(activeAdaptations)) {
                removeInactiveAdaptations(mob, newAdaptations);
                applyNewAdaptations(mob, newAdaptations);
                activeAdaptations = newAdaptations;
            }
            
        } catch (Exception e) {
            // Ignore update errors
        }
    }
    
    /**
     * Applies initial environmental adaptations.
     */
    private void applyEnvironmentalAdaptations(CustomMob mob) {
        updateAdaptations(mob);
    }
    
    /**
     * Removes adaptations that are no longer active.
     */
    private void removeInactiveAdaptations(CustomMob mob, Set<AdaptationType> newAdaptations) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            for (AdaptationType adaptation : activeAdaptations) {
                if (!newAdaptations.contains(adaptation)) {
                    removeAdaptation(entity, adaptation);
                }
            }
        } catch (Exception e) {
            // Ignore removal errors
        }
    }
    
    /**
     * Applies new adaptations that weren't previously active.
     */
    private void applyNewAdaptations(CustomMob mob, Set<AdaptationType> newAdaptations) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            for (AdaptationType adaptation : newAdaptations) {
                if (!activeAdaptations.contains(adaptation)) {
                    applyAdaptation(entity, adaptation, mob.getTier());
                }
            }
        } catch (Exception e) {
            // Ignore application errors
        }
    }
    
    /**
     * Applies a specific environmental adaptation.
     */
    private void applyAdaptation(LivingEntity entity, AdaptationType adaptation, int tier) {
        try {
            switch (adaptation) {
                case DESERT_HEAT_RESISTANCE:
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                    break;
                    
                case OCEAN_WATER_MASTERY:
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, false, false));
                    // Enhanced underwater speed
                    AttributeInstance swimSpeed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (swimSpeed != null && isInWater(entity)) {
                        swimSpeed.setBaseValue(swimSpeed.getBaseValue() * 1.3);
                    }
                    break;
                    
                case MOUNTAIN_ALTITUDE_BOOST:
                    // Increased health at high altitude
                    if (entity.getLocation().getY() > 100) {
                        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
                        if (health != null) {
                            double bonus = (entity.getLocation().getY() - 100) / 200.0 * tier * 0.1;
                            health.setBaseValue(health.getBaseValue() * (1.0 + bonus));
                        }
                    }
                    break;
                    
                case FOREST_NATURAL_HARMONY:
                    // Natural regeneration near trees
                    if (isNearTrees(entity.getLocation())) {
                        entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
                    }
                    break;
                    
                case NETHER_HELLISH_IMMUNITY:
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1, false, false));
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                    break;
                    
                case NIGHT_SHADOW_AFFINITY:
                    // Enhanced speed at night (removed invisibility for mob visibility)
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
                    // Note: Invisibility removed to maintain mob visibility for gameplay
                    break;
                    
                case STORM_ELECTRICAL_CHARGE:
                    // Enhanced damage and electrical effects
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false));
                    break;
                    
                default:
                    // Unknown adaptation type
                    break;
            }
        } catch (Exception e) {
            // Ignore application errors
        }
    }
    
    /**
     * Removes a specific environmental adaptation.
     */
    private void removeAdaptation(LivingEntity entity, AdaptationType adaptation) {
        try {
            switch (adaptation) {
                case DESERT_HEAT_RESISTANCE:
                    entity.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                    break;
                    
                case OCEAN_WATER_MASTERY:
                    entity.removePotionEffect(PotionEffectType.WATER_BREATHING);
                    // Reset movement speed
                    AttributeInstance swimSpeed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (swimSpeed != null) {
                        swimSpeed.setBaseValue(swimSpeed.getDefaultValue());
                    }
                    break;
                    
                case MOUNTAIN_ALTITUDE_BOOST:
                    // Reset health to base value
                    AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
                    if (health != null) {
                        health.setBaseValue(health.getDefaultValue());
                    }
                    break;
                    
                case FOREST_NATURAL_HARMONY:
                    entity.removePotionEffect(PotionEffectType.REGENERATION);
                    break;
                    
                case NETHER_HELLISH_IMMUNITY:
                    entity.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                    entity.removePotionEffect(PotionEffectType.RESISTANCE);
                    break;
                    
                case NIGHT_SHADOW_AFFINITY:
                    entity.removePotionEffect(PotionEffectType.SPEED);
                    // Note: No invisibility effect to remove (removed for mob visibility)
                    break;
                    
                case STORM_ELECTRICAL_CHARGE:
                    entity.removePotionEffect(PotionEffectType.STRENGTH);
                    break;
                    
                default:
                    // Unknown adaptation type
                    break;
            }
        } catch (Exception e) {
            // Ignore removal errors
        }
    }
    
    /**
     * Removes all adaptations from the mob.
     */
    private void removeAllAdaptations(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            for (AdaptationType adaptation : activeAdaptations) {
                removeAdaptation(entity, adaptation);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    /**
     * Applies ongoing environmental effects.
     */
    private void applyOngoingEffects(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Apply tier-based scaling to ongoing effects
            if (mob.getTier() >= MINIMUM_EFFECTIVE_TIER) {
                // Forest harmony regeneration boost
                if (activeAdaptations.contains(AdaptationType.FOREST_NATURAL_HARMONY) &&
                    isNearTrees(entity.getLocation())) {
                    
                    if (random.nextInt(200) == 0) { // Rare chance per tick
                        double healAmount = mob.getTier() * 0.5;
                        double newHealth = Math.min(entity.getHealth() + healAmount, entity.getMaxHealth());
                        entity.setHealth(newHealth);
                    }
                }
            }
            
        } catch (Exception e) {
            // Ignore ongoing effect errors
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Creates a biome set safely, handling any potential non-existent biomes.
     * Uses modern Registry API for Paper 1.21.8+ compatibility.
     * 
     * @param biomeKeys The biome keys to try to include
     * @return A safe EnumSet of biomes
     */
    private static Set<Biome> createBiomeSet(String... biomeKeys) {
        Set<Biome> biomeSet = new HashSet<>();
        for (String biomeKey : biomeKeys) {
            try {
                // Try modern registry approach first
                NamespacedKey key = NamespacedKey.minecraft(biomeKey.toLowerCase());
                Biome biome = Registry.BIOME.get(key);
                if (biome != null) {
                    biomeSet.add(biome);
                }
            } catch (Exception e) {
                // Registry approach failed, try fallback with direct enum access
                try {
                    // Convert lowercase key to uppercase for enum field name
                    String enumFieldName = biomeKey.toUpperCase();
                    java.lang.reflect.Field field = Biome.class.getField(enumFieldName);
                    if (field.getType() == Biome.class) {
                        Biome biome = (Biome) field.get(null);
                        biomeSet.add(biome);
                    }
                } catch (Exception reflectionException) {
                    // Biome doesn't exist in this version, skip it silently
                }
            }
        }
        return biomeSet;
    }
    
    /**
     * Creates a biome set from direct biome references.
     * 
     * @param biomes The biomes to include in the set
     * @return A safe EnumSet of biomes
     */
    private static Set<Biome> createBiomeSetDirect(Biome... biomes) {
        Set<Biome> biomeSet = new HashSet<>();
        for (Biome biome : biomes) {
            if (biome != null) {
                biomeSet.add(biome);
            }
        }
        return biomeSet;
    }
    
    /**
     * Checks if the entity is currently in water.
     */
    private boolean isInWater(LivingEntity entity) {
        return entity != null && entity.getLocation().getBlock().getType() == Material.WATER;
    }
    
    /**
     * Checks if it's currently night time in the world.
     */
    private boolean isNightTime(World world) {
        long time = world.getTime();
        return time > 13000 && time < 23000; // Minecraft night time
    }
    
    /**
     * Checks if it's currently storming in the world.
     */
    private boolean isStorming(World world) {
        return world.hasStorm() && world.isThundering();
    }
    
    /**
     * Checks if the location is near trees (forest harmony).
     */
    private boolean isNearTrees(Location location) {
        if (location == null) return false;
        
        try {
            // Check for wood blocks in a 3x3x3 area
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Location checkLoc = location.clone().add(x, y, z);
                        Material material = checkLoc.getBlock().getType();
                        if (material.name().contains("LOG") || material.name().contains("WOOD")) {
                            return true;
                        }
                    }
                }
            }
            return false;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Grows plants around the specified location.
     */
    private void growPlantsAroundLocation(Location location) {
        if (location == null || location.getWorld() == null) return;
        
        try {
            // Grow grass and flowers in a small radius
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (random.nextDouble() < 0.3) { // 30% chance per block
                        Location growLoc = location.clone().add(x, 0, z);
                        if (growLoc.getBlock().getType() == Material.DIRT ||
                            growLoc.getBlock().getType() == Material.GRASS_BLOCK) {
                            
                            Location aboveLoc = growLoc.clone().add(0, 1, 0);
                            if (aboveLoc.getBlock().getType() == Material.AIR) {
                                // Place random flowers or grass - Updated for Paper 1.21.8
                                Material[] plants = {Material.SHORT_GRASS, Material.DANDELION, Material.POPPY};
                                aboveLoc.getBlock().setType(plants[random.nextInt(plants.length)]);
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // Ignore plant growth errors
        }
    }
}