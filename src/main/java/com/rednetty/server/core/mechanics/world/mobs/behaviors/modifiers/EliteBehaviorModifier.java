package com.rednetty.server.core.mechanics.world.mobs.behaviors.modifiers;

import com.rednetty.server.utils.ui.GradientColors;
import java.util.List;
import java.util.Set;

/**
 * Defines behavioral modifiers that can be applied to elite mobs to create variety
 * within the same archetype and tier. These modifiers stack with base archetype behaviors
 * to create unique combinations and enhanced gameplay variety.
 */
public enum EliteBehaviorModifier {
    
    // ==================== COMMON MODIFIERS (All Tiers) ====================
    
    /**
     * Veteran - Enhanced stats and resistance to crowd control
     */
    VETERAN("Veteran", 1, 6, 0.15,
        List.of("50% more health", "Immune to slowness and weakness", "Faster ability cooldowns"),
        Set.of("stat_boost", "cc_immunity", "cooldown_reduction")),
    
    /**
     * Enraged - Constant rage state with increased damage but reduced defense
     */
    ENRAGED("Enraged", 1, 6, 0.12,
        List.of("75% more damage", "25% less health", "Attacks apply fire", "Never retreats"),
        Set.of("damage_boost", "health_reduction", "fire_attacks", "aggressive")),
    
    /**
     * Swift - Enhanced movement and attack speed
     */
    SWIFT("Swift", 1, 6, 0.13,
        List.of("50% faster movement", "Double attack speed", "Teleport on low health"),
        Set.of("speed_boost", "attack_speed", "emergency_teleport")),
    
    // ==================== TIER 2+ MODIFIERS ====================
    
    /**
     * Plaguebearer - Spreads disease and poison effects
     */
    PLAGUEBEARER("Plaguebearer", 2, 6, 0.10,
        List.of("All attacks poison", "Death creates poison cloud", "Immune to poison damage"),
        Set.of("poison_attacks", "poison_death", "poison_immunity")),
    
    /**
     * Regenerative - Advanced healing and adaptation
     */
    REGENERATIVE("Regenerative", 2, 6, 0.09,
        List.of("Constant health regeneration", "Adapts to damage types", "Heals from magical damage"),
        Set.of("constant_regen", "damage_adaptation", "magic_absorption")),
    
    /**
     * Phantom - Incorporeal abilities and phase mechanics
     */
    PHANTOM("Phantom", 2, 6, 0.08,
        List.of("Phases through walls", "50% physical damage reduction", "Teleports when attacked"),
        Set.of("wall_phasing", "physical_resistance", "attack_teleport")),
    
    // ==================== TIER 3+ MODIFIERS ====================
    
    /**
     * Unstable - Chaotic magic with random powerful effects
     */
    UNSTABLE("Unstable", 3, 6, 0.07,
        List.of("Random spell effects each attack", "Explodes on death", "Magic immunity"),
        Set.of("random_spells", "death_explosion", "magic_immunity")),
    
    /**
     * Bloodthirsty - Gains power from killing and dealing damage
     */
    BLOODTHIRSTY("Bloodthirsty", 3, 6, 0.06,
        List.of("Gains strength from damage dealt", "Heals on kill", "Berserker fury at low health"),
        Set.of("damage_stacking", "kill_healing", "berserker_fury")),
    
    /**
     * Dimensional - Controls space and teleportation
     */
    DIMENSIONAL("Dimensional", 3, 6, 0.06,
        List.of("Teleports frequently", "Creates portal traps", "Banishes enemies temporarily"),
        Set.of("frequent_teleport", "portal_traps", "banishment")),
    
    // ==================== TIER 4+ MODIFIERS ====================
    
    /**
     * Corrupted - Dark energy manipulation and area control
     */
    CORRUPTED("Corrupted", 4, 6, 0.05,
        List.of("Corrupts terrain around it", "Spawns shadow minions", "Drains life from area"),
        Set.of("terrain_corruption", "shadow_minions", "life_drain")),
    
    /**
     * Ascendant - Divine/demonic powers with massive presence
     */
    ASCENDANT("Ascendant", 4, 6, 0.04,
        List.of("Aura affects entire battlefield", "Commands lesser elites", "Reality-altering attacks"),
        Set.of("battlefield_aura", "elite_command", "reality_attacks")),
    
    /**
     * Eternal - Time manipulation and undying nature
     */
    ETERNAL("Eternal", 4, 6, 0.03,
        List.of("Slows time around enemies", "Resurrects once per combat", "Ages enemies"),
        Set.of("time_slow", "resurrection", "aging_attacks")),
    
    // ==================== TIER 5+ MODIFIERS ====================
    
    /**
     * Worldbane - Environmental destruction and apocalyptic power
     */
    WORLDBANE("Worldbane", 5, 6, 0.02,
        List.of("Destroys terrain with attacks", "Weather manipulation", "Causes natural disasters"),
        Set.of("terrain_destruction", "weather_control", "natural_disasters")),
    
    /**
     * Voidtouched - Cosmic horror with reality-breaking abilities
     */
    VOIDTOUCHED("Voidtouched", 5, 6, 0.02,
        List.of("Exists partially outside reality", "Drives enemies insane", "Immune to conventional damage"),
        Set.of("reality_separation", "sanity_damage", "damage_immunity")),
    
    /**
     * Godslayer - Ultimate modifier for the most powerful elites
     */
    GODSLAYER("Godslayer", 6, 6, 0.01,
        List.of("All attacks are critical hits", "Immune to all debuffs", "Kills weaker enemies instantly"),
        Set.of("permanent_crit", "debuff_immunity", "instant_kill"));
    
    // ==================== MODIFIER PROPERTIES ====================
    
    private final String displayName;
    private final int minTier;
    private final int maxTier;
    private final double spawnChance;
    private final List<String> effects;
    private final Set<String> modifierTags;
    
    EliteBehaviorModifier(String displayName, int minTier, int maxTier, double spawnChance,
                         List<String> effects, Set<String> modifierTags) {
        this.displayName = displayName;
        this.minTier = minTier;
        this.maxTier = maxTier;
        this.spawnChance = spawnChance;
        this.effects = effects;
        this.modifierTags = modifierTags;
    }
    
    // ==================== GETTERS ====================
    
    public String getDisplayName() { return displayName; }
    public int getMinTier() { return minTier; }
    public int getMaxTier() { return maxTier; }
    public double getSpawnChance() { return spawnChance; }
    public List<String> getEffects() { return effects; }
    public Set<String> getModifierTags() { return modifierTags; }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Check if this modifier can be applied to the given tier
     */
    public boolean canApplyToTier(int tier) {
        return tier >= minTier && tier <= maxTier;
    }
    
    /**
     * Get all modifiers available for a specific tier
     */
    public static List<EliteBehaviorModifier> getModifiersForTier(int tier) {
        return List.of(values()).stream()
            .filter(modifier -> modifier.canApplyToTier(tier))
            .toList();
    }
    
    /**
     * Get a random modifier for the specified tier based on spawn chances
     */
    public static EliteBehaviorModifier getRandomForTier(int tier) {
        List<EliteBehaviorModifier> available = getModifiersForTier(tier);
        if (available.isEmpty()) {
            return VETERAN; // Fallback
        }
        
        // Weighted random selection based on spawn chances
        double totalWeight = available.stream().mapToDouble(EliteBehaviorModifier::getSpawnChance).sum();
        double randomValue = Math.random() * totalWeight;
        double currentWeight = 0.0;
        
        for (EliteBehaviorModifier modifier : available) {
            currentWeight += modifier.getSpawnChance();
            if (randomValue <= currentWeight) {
                return modifier;
            }
        }
        
        // Fallback - should rarely happen
        return available.get((int) (Math.random() * available.size()));
    }
    
    /**
     * Calculate the power scaling multiplier for this modifier
     */
    public double getPowerMultiplier(int tier) {
        if (!canApplyToTier(tier)) {
            return 1.0;
        }
        
        // Higher tier modifiers and rarer modifiers are more powerful
        double basePower = 1.0 + (1.0 - spawnChance) * 0.5; // Rarer = more powerful
        double tierMultiplier = 1.0 + (tier - minTier) * 0.1; // Scale with tier
        
        return basePower * tierMultiplier;
    }
    
    /**
     * Get the color code for this modifier's display
     */
    public String getDisplayColor() {
        return switch (this) {
            // Common modifiers - green shades
            case VETERAN -> "§a"; // Green
            case ENRAGED -> "§c"; // Red  
            case SWIFT -> "§e"; // Yellow
            
            // Tier 2+ modifiers - blue/purple shades
            case PLAGUEBEARER -> "§2"; // Dark Green
            case REGENERATIVE -> "§b"; // Aqua
            case PHANTOM -> "§7"; // Gray
            
            // Tier 3+ modifiers - purple/pink shades  
            case UNSTABLE -> "§d"; // Light Purple
            case BLOODTHIRSTY -> "§4"; // Dark Red
            case DIMENSIONAL -> "§5"; // Purple
            
            // Tier 4+ modifiers - special colors
            case CORRUPTED -> "§0§l"; // Bold Black
            case ASCENDANT -> "§f§l"; // Bold White
            case ETERNAL -> "§6§l"; // Bold Gold (fallback - use getDisplayText for gradient)
            
            // Tier 5+ modifiers - legendary colors
            case WORLDBANE -> "§4§l§k"; // Bold Dark Red with obfuscation
            case VOIDTOUCHED -> "§0§l§k"; // Bold Black with obfuscation
            case GODSLAYER -> "§c§l§k"; // Bold Red with obfuscation
        };
    }
    
    /**
     * Get the formatted display text with gradient support for premium modifiers
     */
    public String getDisplayText() {
        switch (this) {
            case ETERNAL:
                // ENHANCED: T6-level modifier gets gradient treatment
                return GradientColors.getT6Gradient("§l" + displayName);
            default:
                return getDisplayColor() + displayName;
        }
    }
    
    /**
     * Get the rarity tier of this modifier
     */
    public ModifierRarity getRarity() {
        if (spawnChance >= 0.10) return ModifierRarity.COMMON;
        if (spawnChance >= 0.06) return ModifierRarity.UNCOMMON;
        if (spawnChance >= 0.03) return ModifierRarity.RARE;
        if (spawnChance >= 0.02) return ModifierRarity.EPIC;
        return ModifierRarity.LEGENDARY;
    }
    
    /**
     * Check if this modifier conflicts with another modifier
     * (prevents certain combinations that don't make sense)
     */
    public boolean conflictsWith(EliteBehaviorModifier other) {
        return switch (this) {
            case ENRAGED -> other == PHANTOM || other == SWIFT; // Enraged conflicts with evasive modifiers
            case PHANTOM -> other == ENRAGED || other == BLOODTHIRSTY; // Phantom conflicts with aggressive modifiers
            case REGENERATIVE -> other == ENRAGED; // Regen conflicts with berserker style
            case ETERNAL -> other == UNSTABLE; // Eternal conflicts with chaotic modifiers
            case VOIDTOUCHED -> other == ASCENDANT; // Cosmic horror conflicts with divine power
            default -> false; // Most modifiers don't conflict
        };
    }
    
    /**
     * Modifier rarity levels
     */
    public enum ModifierRarity {
        COMMON("Common", "§7"),
        UNCOMMON("Uncommon", "§a"), 
        RARE("Rare", "§9"),
        EPIC("Epic", "§5"),
        LEGENDARY("Legendary", "§6"); // Fallback color - use getDisplayText for gradient
        
        private final String displayName;
        private final String color;
        
        ModifierRarity(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        
        /**
         * Get formatted display text with gradient support for premium rarities
         */
        public String getDisplayText() {
            switch (this) {
                case LEGENDARY:
                    // ENHANCED: Legendary rarity gets gradient treatment
                    return GradientColors.getT6Gradient(displayName);
                default:
                    return color + displayName;
            }
        }
    }
}