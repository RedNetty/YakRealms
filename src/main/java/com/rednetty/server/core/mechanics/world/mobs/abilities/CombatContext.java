package com.rednetty.server.core.mechanics.world.mobs.abilities;

import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Combat Context - Provides situational information for intelligent ability selection
 * 
 * This class analyzes the current combat situation to help elites make smart
 * tactical decisions about which abilities to use and when.
 */
public class CombatContext {
    
    private final Location combatCenter;
    private final List<Player> allPlayers;
    private final List<Player> nearbyPlayers;
    private final List<Player> isolatedPlayers;
    private final EliteBehaviorArchetype archetype;
    private final TerrainType terrain;
    private final CombatPhase phase;
    private final double averagePlayerHealth;
    private final double averagePlayerDistance;
    private final boolean hasHealers;
    private final boolean hasTanks;
    private final boolean hasRanged;
    
    /**
     * Current phase of the combat encounter
     */
    public enum CombatPhase {
        OPENING,    // First 30 seconds of combat
        MID_FIGHT,  // Main combat phase  
        DESPERATE,  // Elite below 25% health
        CLEANUP     // Elite above 75% health with few enemies
    }
    
    /**
     * Type of terrain affecting combat tactics
     */
    public enum TerrainType {
        OPEN,       // Open area, good for AoE abilities
        CONFINED,   // Tight space, favors close-range abilities
        ELEVATED,   // Height differences, affects positioning
        WATER,      // Water present, affects movement
        LAVA,       // Dangerous terrain, affects positioning
        MIXED       // Mixed terrain types
    }
    
    public CombatContext(Location center, List<Player> players, EliteBehaviorArchetype archetype) {
        this.combatCenter = center;
        this.allPlayers = players;
        this.archetype = archetype;
        
        // Analyze players and distances
        this.nearbyPlayers = players.stream()
            .filter(p -> p.getLocation().distance(center) <= 8.0)
            .toList();
            
        this.isolatedPlayers = players.stream()
            .filter(p -> players.stream()
                .filter(other -> other != p)
                .noneMatch(other -> other.getLocation().distance(p.getLocation()) <= 5.0))
            .toList();
        
        this.averagePlayerDistance = players.stream()
            .mapToDouble(p -> p.getLocation().distance(center))
            .average()
            .orElse(10.0);
            
        this.averagePlayerHealth = players.stream()
            .mapToDouble(p -> p.getHealth() / p.getMaxHealth())
            .average()
            .orElse(1.0);
        
        // Analyze player roles (simplified)
        this.hasHealers = players.stream()
            .anyMatch(p -> hasHealingItems(p) || hasHealingPotions(p));
            
        this.hasTanks = players.stream()
            .anyMatch(p -> hasHeavyArmor(p));
            
        this.hasRanged = players.stream()
            .anyMatch(p -> hasRangedWeapon(p));
        
        // Determine terrain type
        this.terrain = analyzeTerrain(center);
        
        // Determine combat phase
        this.phase = determineCombatPhase(players);
    }
    
    // ==================== TACTICAL ANALYSIS METHODS ====================
    
    /**
     * Should the elite prioritize AoE abilities?
     */
    public boolean favorAoEAbilities() {
        return nearbyPlayers.size() >= 3 && 
               terrain != TerrainType.CONFINED &&
               averagePlayerDistance <= 6.0;
    }
    
    /**
     * Should the elite prioritize single-target abilities?
     */
    public boolean favorSingleTargetAbilities() {
        return isolatedPlayers.size() >= 1 ||
               (nearbyPlayers.size() <= 2 && averagePlayerDistance <= 4.0);
    }
    
    /**
     * Should the elite use defensive abilities?
     */
    public boolean favorDefensiveAbilities() {
        return nearbyPlayers.size() >= 4 ||
               averagePlayerHealth > 0.8 ||
               phase == CombatPhase.DESPERATE;
    }
    
    /**
     * Should the elite use mobility abilities?
     */
    public boolean favorMobilityAbilities() {
        return averagePlayerDistance > 8.0 ||
               hasRanged ||
               terrain == TerrainType.ELEVATED;
    }
    
    /**
     * Is this a good time for ultimate abilities?
     */
    public boolean favorUltimateAbilities() {
        return phase == CombatPhase.DESPERATE ||
               (nearbyPlayers.size() >= 5 && phase != CombatPhase.OPENING);
    }
    
    /**
     * Get priority targets for single-target abilities
     */
    public List<Player> getPriorityTargets() {
        return allPlayers.stream()
            .sorted((p1, p2) -> {
                int p1Priority = getPlayerTargetPriority(p1);
                int p2Priority = getPlayerTargetPriority(p2);
                return Integer.compare(p2Priority, p1Priority); // Higher priority first
            })
            .toList();
    }
    
    /**
     * Get the best location for AoE abilities
     */
    public Location getBestAoELocation() {
        if (nearbyPlayers.isEmpty()) {
            return combatCenter;
        }
        
        // Find the location that hits the most players
        Location bestLocation = combatCenter;
        int maxHits = 0;
        
        for (Player player : nearbyPlayers) {
            Location testLocation = player.getLocation();
            int hits = (int) nearbyPlayers.stream()
                .filter(p -> p.getLocation().distance(testLocation) <= 4.0)
                .count();
                
            if (hits > maxHits) {
                maxHits = hits;
                bestLocation = testLocation;
            }
        }
        
        return bestLocation;
    }
    
    // ==================== ARCHETYPE-SPECIFIC BONUSES ====================
    
    /**
     * Get archetype-specific ability chance modifier
     */
    public double getArchetypeBonus(EliteAbility.AbilityType abilityType) {
        return switch (archetype) {
            case BRUTE -> switch (abilityType) {
                case OFFENSIVE -> 1.3; // Brutes love offensive abilities
                case DEFENSIVE -> 0.8;
                case UTILITY -> 0.7;
                case ULTIMATE -> 1.2;
            };
            case ASSASSIN -> switch (abilityType) {
                case OFFENSIVE -> 1.2;
                case DEFENSIVE -> 0.6;
                case UTILITY -> 1.4; // Assassins love utility (stealth, movement)
                case ULTIMATE -> 1.0;
            };
            case GUARDIAN -> switch (abilityType) {
                case OFFENSIVE -> 0.8;
                case DEFENSIVE -> 1.5; // Guardians love defensive abilities
                case UTILITY -> 1.1;
                case ULTIMATE -> 1.0;
            };
            case ELEMENTALIST -> switch (abilityType) {
                case OFFENSIVE -> 1.1;
                case DEFENSIVE -> 1.1;
                case UTILITY -> 1.2;
                case ULTIMATE -> 1.3; // Elementalists love big flashy ultimates
            };
            default -> 1.0;
        };
    }
    
    // ==================== PRIVATE ANALYSIS METHODS ====================
    
    private int getPlayerTargetPriority(Player player) {
        int priority = 0;
        
        // Prioritize low health players
        double healthPercent = player.getHealth() / player.getMaxHealth();
        if (healthPercent < 0.3) priority += 30;
        else if (healthPercent < 0.6) priority += 15;
        
        // Prioritize isolated players
        if (isolatedPlayers.contains(player)) priority += 25;
        
        // Prioritize healers
        if (hasHealingItems(player)) priority += 20;
        
        // Prioritize players with good gear (threat assessment)
        if (hasGoodGear(player)) priority += 10;
        
        // Prioritize closer players
        double distance = player.getLocation().distance(combatCenter);
        if (distance <= 4.0) priority += 15;
        else if (distance <= 8.0) priority += 5;
        
        return priority;
    }
    
    private boolean hasHealingItems(Player player) {
        return player.getInventory().contains(org.bukkit.Material.GOLDEN_APPLE) ||
               player.getInventory().contains(org.bukkit.Material.ENCHANTED_GOLDEN_APPLE);
    }
    
    private boolean hasHealingPotions(Player player) {
        return java.util.Arrays.stream(player.getInventory().getContents())
            .filter(java.util.Objects::nonNull)
            .anyMatch(item -> item.getType().name().contains("POTION"));
    }
    
    private boolean hasHeavyArmor(Player player) {
        return player.getInventory().getHelmet() != null &&
               (player.getInventory().getHelmet().getType().name().contains("DIAMOND") ||
                player.getInventory().getHelmet().getType().name().contains("NETHERITE"));
    }
    
    private boolean hasRangedWeapon(Player player) {
        return player.getInventory().contains(org.bukkit.Material.BOW) ||
               player.getInventory().contains(org.bukkit.Material.CROSSBOW);
    }
    
    private boolean hasGoodGear(Player player) {
        return hasHeavyArmor(player) || hasRangedWeapon(player);
    }
    
    private TerrainType analyzeTerrain(Location center) {
        // Simplified terrain analysis
        Set<org.bukkit.Material> nearbyBlocks = new java.util.HashSet<>();
        boolean hasWater = false, hasLava = false;
        int airBlocks = 0, totalBlocks = 0;
        
        for (int x = -5; x <= 5; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -5; z <= 5; z++) {
                    org.bukkit.block.Block block = center.clone().add(x, y, z).getBlock();
                    nearbyBlocks.add(block.getType());
                    totalBlocks++;
                    
                    if (block.getType() == org.bukkit.Material.AIR) airBlocks++;
                    else if (block.getType() == org.bukkit.Material.WATER) hasWater = true;
                    else if (block.getType() == org.bukkit.Material.LAVA) hasLava = true;
                }
            }
        }
        
        if (hasLava) return TerrainType.LAVA;
        if (hasWater) return TerrainType.WATER;
        
        double openness = (double) airBlocks / totalBlocks;
        if (openness > 0.7) return TerrainType.OPEN;
        else if (openness < 0.4) return TerrainType.CONFINED;
        else return TerrainType.MIXED;
    }
    
    private CombatPhase determineCombatPhase(List<Player> players) {
        // Simplified phase determination based on player count and average health
        if (players.size() <= 2 && averagePlayerHealth < 0.5) {
            return CombatPhase.CLEANUP;
        } else if (averagePlayerHealth < 0.3) {
            return CombatPhase.DESPERATE;
        } else if (players.size() >= 4 || averagePlayerHealth > 0.8) {
            return CombatPhase.MID_FIGHT;
        } else {
            return CombatPhase.OPENING;
        }
    }
    
    // ==================== GETTERS ====================
    
    public Location getCombatCenter() { return combatCenter; }
    public List<Player> getAllPlayers() { return allPlayers; }
    public List<Player> getNearbyPlayers() { return nearbyPlayers; }
    public List<Player> getIsolatedPlayers() { return isolatedPlayers; }
    public EliteBehaviorArchetype getArchetype() { return archetype; }
    public TerrainType getTerrain() { return terrain; }
    public CombatPhase getPhase() { return phase; }
    public double getAveragePlayerHealth() { return averagePlayerHealth; }
    public double getAveragePlayerDistance() { return averagePlayerDistance; }
    public boolean hasHealers() { return hasHealers; }
    public boolean hasTanks() { return hasTanks; }
    public boolean hasRanged() { return hasRanged; }
    public int getPlayerCount() { return allPlayers.size(); }
    public int getNearbyPlayerCount() { return nearbyPlayers.size(); }
}