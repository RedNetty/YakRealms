package com.rednetty.server.core.mechanics.world.mobs.behaviors.types;

import java.util.List;
import java.util.Set;

/**
 * Defines the different behavioral archetypes for elite mobs.
 * Each archetype provides unique combat patterns, abilities, and characteristics
 * that create variety within and between tiers.
 */
public enum EliteBehaviorArchetype {
    
    // ==================== TIER 1-2 ARCHETYPES ====================
    
    /**
     * Brute - Focuses on raw damage and direct confrontation
     * Abilities: Charge attacks, ground slams, berserker rage
     */
    BRUTE("Brute", 1, 6, 
        List.of("Charges directly at enemies", "High damage melee attacks", "Becomes stronger when injured"),
        Set.of("charge_attack", "ground_slam", "berserker_rage", "intimidating_roar")),
    
    /**
     * Assassin - Uses stealth and hit-and-run tactics
     * Abilities: Invisibility, backstab, poison, teleportation
     */
    ASSASSIN("Assassin", 1, 6,
        List.of("Uses stealth to ambush enemies", "Quick strikes with poison", "Teleports to avoid damage"),
        Set.of("stealth", "backstab", "poison_strike", "shadow_step", "vanish")),
    
    /**
     * Guardian - Defensive elite with protection abilities
     * Abilities: Shield walls, damage reflection, area denial
     */
    GUARDIAN("Guardian", 1, 6,
        List.of("Creates protective barriers", "Reflects damage back to attackers", "Controls battlefield zones"),
        Set.of("shield_wall", "damage_reflection", "area_denial", "protective_aura")),
    
    // ==================== TIER 3-4 ARCHETYPES ====================
    
    /**
     * Elementalist - Masters elemental magic and environmental control
     * Abilities: Fire/ice/lightning attacks, weather control, elemental shields
     */
    ELEMENTALIST("Elementalist", 2, 6,
        List.of("Commands elemental forces", "Changes battlefield environment", "Immune to matching elements"),
        Set.of("fire_mastery", "ice_mastery", "lightning_mastery", "weather_control", "elemental_shield")),
    
    /**
     * Necromancer - Summons undead and uses death magic
     * Abilities: Raise skeletons, life drain, curse spells, corpse explosion
     */
    NECROMANCER("Necromancer", 2, 6,
        List.of("Raises undead minions", "Drains life from enemies", "Curses affect large areas"),
        Set.of("raise_undead", "life_drain", "mass_curse", "corpse_explosion", "death_aura")),
    
    /**
     * Berserker - Becomes increasingly powerful as fight continues
     * Abilities: Rage stacks, frenzy mode, damage immunity phases
     */
    BERSERKER("Berserker", 2, 6,
        List.of("Gains power as fight continues", "Enters unstoppable frenzy", "Briefly immune to all damage"),
        Set.of("rage_stacks", "blood_frenzy", "unstoppable_force", "damage_immunity", "rampage")),
    
    // ==================== TIER 4-5 ARCHETYPES ====================
    
    /**
     * Void Walker - Manipulates space and reality
     * Abilities: Dimensional rifts, gravity control, phase shifting
     */
    VOID_WALKER("Void Walker", 3, 6,
        List.of("Opens rifts in space-time", "Controls gravity fields", "Phases through attacks"),
        Set.of("dimensional_rift", "gravity_well", "phase_shift", "void_blast", "reality_tear")),
    
    /**
     * Shapeshifter - Changes forms and adapts to threats
     * Abilities: Form transformation, ability mimicry, adaptive resistance
     */
    SHAPESHIFTER("Shapeshifter", 3, 6,
        List.of("Transforms into different forms", "Copies enemy abilities", "Adapts to resist damage types"),
        Set.of("form_shift", "ability_mimic", "adaptive_resistance", "perfect_copy", "evolution")),
    
    /**
     * Warmaster - Commands battlefield and coordinates attacks
     * Abilities: Summon reinforcements, battle commands, tactical superiority
     */
    WARMASTER("Warmaster", 3, 6,
        List.of("Commands other mobs", "Uses advanced tactics", "Buffs nearby allies"),
        Set.of("summon_reinforcements", "battle_commands", "tactical_strike", "war_cry", "formation_fighting")),
    
    // ==================== TIER 5-6 ARCHETYPES ====================
    
    /**
     * Cosmic Entity - Wields power beyond mortal comprehension
     * Abilities: Reality manipulation, cosmic storms, time dilation
     */
    COSMIC_ENTITY("Cosmic Entity", 4, 6,
        List.of("Manipulates reality itself", "Creates cosmic phenomena", "Controls flow of time"),
        Set.of("reality_warp", "cosmic_storm", "time_dilation", "star_fall", "dimensional_collapse")),
    
    /**
     * Ancient One - Primordial being with vast knowledge and power
     * Abilities: Ancient curses, forbidden magic, world-shaking presence
     */
    ANCIENT_ONE("Ancient One", 4, 6,
        List.of("Casts ancient and forbidden magic", "Presence affects entire region", "Knowledge of all weaknesses"),
        Set.of("ancient_curse", "forbidden_magic", "omniscience", "world_shaker", "primordial_wrath")),
    
    /**
     * Avatar of Destruction - Pure destructive force given form
     * Abilities: Cataclysmic attacks, destruction aura, apocalypse mode
     */
    AVATAR_OF_DESTRUCTION("Avatar of Destruction", 4, 6,
        List.of("Emanates pure destructive energy", "Attacks destroy terrain", "Final form threatens all reality"),
        Set.of("cataclysmic_blast", "destruction_aura", "terrain_destroyer", "apocalypse_mode", "reality_ender"));
    
    // ==================== ARCHETYPE PROPERTIES ====================
    
    private final String displayName;
    private final int minTier;
    private final int maxTier;
    private final List<String> characteristics;
    private final Set<String> abilities;
    
    EliteBehaviorArchetype(String displayName, int minTier, int maxTier, 
                          List<String> characteristics, Set<String> abilities) {
        this.displayName = displayName;
        this.minTier = minTier;
        this.maxTier = maxTier;
        this.characteristics = characteristics;
        this.abilities = abilities;
    }
    
    // ==================== GETTERS ====================
    
    public String getDisplayName() { return displayName; }
    public int getMinTier() { return minTier; }
    public int getMaxTier() { return maxTier; }
    public List<String> getCharacteristics() { return characteristics; }
    public Set<String> getAbilities() { return abilities; }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Check if this archetype is available for the given tier
     */
    public boolean isAvailableForTier(int tier) {
        return tier >= minTier && tier <= maxTier;
    }
    
    /**
     * Get all archetypes available for a specific tier
     */
    public static List<EliteBehaviorArchetype> getArchetypesForTier(int tier) {
        return List.of(values()).stream()
            .filter(archetype -> archetype.isAvailableForTier(tier))
            .toList();
    }
    
    /**
     * Get a random archetype for the specified tier
     */
    public static EliteBehaviorArchetype getRandomForTier(int tier) {
        List<EliteBehaviorArchetype> available = getArchetypesForTier(tier);
        if (available.isEmpty()) {
            return BRUTE; // Fallback
        }
        return available.get((int) (Math.random() * available.size()));
    }
    
    /**
     * Get the power level scaling for this archetype based on tier
     */
    public double getPowerScaling(int tier) {
        if (!isAvailableForTier(tier)) {
            return 1.0;
        }
        
        // Higher tier archetypes are more powerful
        double basePower = 1.0 + (minTier - 1) * 0.2; // +20% per tier requirement
        double tierBonus = (tier - minTier) * 0.1; // +10% per tier above minimum
        
        return basePower + tierBonus;
    }
    
    /**
     * Get the rarity of this archetype (affects spawn chances)
     */
    public double getRarity() {
        return switch (this) {
            case BRUTE, ASSASSIN, GUARDIAN -> 0.4; // Common (40% chance)
            case ELEMENTALIST, NECROMANCER, BERSERKER -> 0.3; // Uncommon (30% chance)
            case VOID_WALKER, SHAPESHIFTER, WARMASTER -> 0.2; // Rare (20% chance)
            case COSMIC_ENTITY, ANCIENT_ONE, AVATAR_OF_DESTRUCTION -> 0.1; // Legendary (10% chance)
        };
    }
    
    /**
     * Get the color associated with this archetype for display purposes
     */
    public String getDisplayColor() {
        return switch (this) {
            case BRUTE -> "§c"; // Red
            case ASSASSIN -> "§8"; // Dark Gray
            case GUARDIAN -> "§9"; // Blue
            case ELEMENTALIST -> "§e"; // Yellow
            case NECROMANCER -> "§2"; // Dark Green
            case BERSERKER -> "§4"; // Dark Red
            case VOID_WALKER -> "§5"; // Purple
            case SHAPESHIFTER -> "§d"; // Light Purple
            case WARMASTER -> "§6"; // Gold
            case COSMIC_ENTITY -> "§b"; // Aqua
            case ANCIENT_ONE -> "§f"; // White
            case AVATAR_OF_DESTRUCTION -> "§0"; // Black
        };
    }
}