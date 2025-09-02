package com.rednetty.server.core.mechanics.world.mobs.behaviors.modifiers;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.core.EliteMob;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Manages the application and effects of behavioral modifiers on elite mobs.
 * This system adds significant variety within each archetype by providing
 * additional layers of unique abilities and characteristics.
 */
public class EliteModifierManager {
    
    private static EliteModifierManager instance;
    private final Logger logger;
    
    // Track modifier assignments for active elite mobs
    private final Map<UUID, Set<EliteBehaviorModifier>> activeModifiers = new HashMap<>();
    
    // Statistics tracking
    private final Map<EliteBehaviorModifier, Integer> modifierSpawnCounts = new HashMap<>();
    private int totalModifiedElites = 0;
    
    private EliteModifierManager() {
        this.logger = YakRealms.getInstance().getLogger();
        initializeModifierTracking();
    }
    
    public static EliteModifierManager getInstance() {
        if (instance == null) {
            instance = new EliteModifierManager();
        }
        return instance;
    }
    
    // ==================== INITIALIZATION ====================
    
    private void initializeModifierTracking() {
        // Initialize spawn count tracking for all modifiers
        for (EliteBehaviorModifier modifier : EliteBehaviorModifier.values()) {
            modifierSpawnCounts.put(modifier, 0);
        }
        
        logger.info("Elite modifier manager initialized with " + 
                   EliteBehaviorModifier.values().length + " modifiers");
    }
    
    // ==================== MODIFIER ASSIGNMENT ====================
    
    /**
     * Assigns random modifiers to an elite mob based on tier and chance
     */
    public Set<EliteBehaviorModifier> assignModifiers(EliteMob elite) {
        if (elite == null || !elite.isValid()) {
            return new HashSet<>();
        }
        
        int tier = elite.getTier();
        UUID mobId = elite.getEntity().getUniqueId();
        Set<EliteBehaviorModifier> assignedModifiers = new HashSet<>();
        
        // Determine how many modifiers this elite gets (higher tiers get more)
        int maxModifiers = Math.min(tier / 2 + 1, 3); // 1-3 modifiers max
        int modifierCount = ThreadLocalRandom.current().nextInt(1, maxModifiers + 1);
        
        // Get available modifiers for this tier
        List<EliteBehaviorModifier> availableModifiers = EliteBehaviorModifier.getModifiersForTier(tier);
        
        if (availableModifiers.isEmpty()) {
            logger.warning("No modifiers available for tier " + tier);
            return assignedModifiers;
        }
        
        // Select modifiers without conflicts
        for (int i = 0; i < modifierCount && !availableModifiers.isEmpty(); i++) {
            EliteBehaviorModifier selectedModifier = selectWeightedModifier(availableModifiers);
            
            if (selectedModifier != null && !hasConflicts(selectedModifier, assignedModifiers)) {
                assignedModifiers.add(selectedModifier);
                availableModifiers.remove(selectedModifier);
                
                // Remove conflicting modifiers from available list
                availableModifiers.removeIf(mod -> selectedModifier.conflictsWith(mod));
                
                // Update statistics
                modifierSpawnCounts.put(selectedModifier, 
                                       modifierSpawnCounts.get(selectedModifier) + 1);
            }
        }
        
        // Store the assignment
        if (!assignedModifiers.isEmpty()) {
            activeModifiers.put(mobId, assignedModifiers);
            totalModifiedElites++;
            
            // Apply modifiers to the mob
            applyModifiersToMob(elite, assignedModifiers);
            
            logger.fine("Assigned " + assignedModifiers.size() + " modifiers to tier " + tier + " elite");
        }
        
        return assignedModifiers;
    }
    
    /**
     * Selects a modifier based on weighted probabilities
     */
    private EliteBehaviorModifier selectWeightedModifier(List<EliteBehaviorModifier> availableModifiers) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double totalWeight = availableModifiers.stream()
                .mapToDouble(EliteBehaviorModifier::getSpawnChance)
                .sum();
        
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0.0;
        
        for (EliteBehaviorModifier modifier : availableModifiers) {
            currentWeight += modifier.getSpawnChance();
            if (randomValue <= currentWeight) {
                return modifier;
            }
        }
        
        // Fallback
        return availableModifiers.get(random.nextInt(availableModifiers.size()));
    }
    
    /**
     * Checks if a modifier conflicts with already assigned modifiers
     */
    private boolean hasConflicts(EliteBehaviorModifier modifier, Set<EliteBehaviorModifier> assigned) {
        return assigned.stream().anyMatch(modifier::conflictsWith);
    }
    
    // ==================== MODIFIER APPLICATION ====================
    
    /**
     * Applies the selected modifiers' effects to the elite mob
     */
    private void applyModifiersToMob(EliteMob elite, Set<EliteBehaviorModifier> modifiers) {
        if (!elite.isValid() || modifiers.isEmpty()) return;
        
        LivingEntity entity = elite.getEntity();
        
        try {
            // Store modifiers in mob metadata
            StringBuilder modifierString = new StringBuilder();
            for (EliteBehaviorModifier modifier : modifiers) {
                if (modifierString.length() > 0) modifierString.append(",");
                modifierString.append(modifier.name());
            }
            entity.setMetadata("elite_modifiers", 
                    new FixedMetadataValue(YakRealms.getInstance(), modifierString.toString()));
            
            // Apply modifier effects
            for (EliteBehaviorModifier modifier : modifiers) {
                applyModifierEffects(elite, modifier);
            }
            
            // Update display name to show modifiers
            updateMobDisplayNameWithModifiers(elite, modifiers);
            
            // Create modifier activation effect
            createModifierActivationEffect(entity.getLocation(), modifiers);
            
        } catch (Exception e) {
            logger.warning("Failed to apply modifiers to elite mob: " + e.getMessage());
        }
    }
    
    /**
     * Applies the effects of a specific modifier to the elite mob
     */
    private void applyModifierEffects(EliteMob elite, EliteBehaviorModifier modifier) {
        LivingEntity entity = elite.getEntity();
        if (entity == null) return;
        
        double powerMultiplier = modifier.getPowerMultiplier(elite.getTier());
        
        try {
            switch (modifier) {
                case VETERAN:
                    // 50% more health, immunity to debuffs
                    entity.setMaxHealth(entity.getMaxHealth() * 1.5);
                    entity.setHealth(entity.getMaxHealth());
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));
                    break;
                    
                case ENRAGED:
                    // 75% more damage, 25% less health, constant fire
                    entity.setMaxHealth(entity.getMaxHealth() * 0.75);
                    entity.setHealth(entity.getMaxHealth());
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, Integer.MAX_VALUE, 2, false, false));
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                    startEnragedEffects(elite);
                    break;
                    
                case SWIFT:
                    // Enhanced movement and attack speed
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false));
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1, false, false));
                    break;
                    
                case PLAGUEBEARER:
                    // Poison immunity and aura
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.POISON, Integer.MAX_VALUE, 0, false, false)); // Visual effect
                    startPlaguebearerAura(elite);
                    break;
                    
                case REGENERATIVE:
                    // Constant regeneration
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION, Integer.MAX_VALUE, 2, false, false));
                    startRegenerativeAdaptation(elite);
                    break;
                    
                case PHANTOM:
                    // Ethereal effects
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                    startPhantomPhasing(elite);
                    break;
                    
                case UNSTABLE:
                    // Chaotic magic effects
                    startUnstableMagic(elite);
                    break;
                    
                case BLOODTHIRSTY:
                    // Power stacking system
                    startBloodthirstyStacking(elite);
                    break;
                    
                case DIMENSIONAL:
                    // Teleportation abilities
                    startDimensionalEffects(elite);
                    break;
                    
                case CORRUPTED:
                    // Dark aura and terrain corruption
                    startCorruptionAura(elite);
                    break;
                    
                case ASCENDANT:
                    // Massive stat boosts and aura
                    entity.setMaxHealth(entity.getMaxHealth() * 2.0);
                    entity.setHealth(entity.getMaxHealth());
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, Integer.MAX_VALUE, 3, false, false));
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 2, false, false));
                    startAscendantAura(elite);
                    break;
                    
                case ETERNAL:
                    // Time manipulation
                    startTimeManipulation(elite);
                    break;
                    
                case WORLDBANE:
                    // Environmental destruction
                    startEnvironmentalDestruction(elite);
                    break;
                    
                case VOIDTOUCHED:
                    // Reality separation
                    startVoidEffects(elite);
                    break;
                    
                case GODSLAYER:
                    // Ultimate power
                    entity.setMaxHealth(entity.getMaxHealth() * 3.0);
                    entity.setHealth(entity.getMaxHealth());
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, Integer.MAX_VALUE, 4, false, false));
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 3, false, false));
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false));
                    startGodslayerEffects(elite);
                    break;
            }
            
        } catch (Exception e) {
            logger.warning("Failed to apply modifier " + modifier.name() + ": " + e.getMessage());
        }
    }
    
    /**
     * Updates the mob's display name to include modifier information
     */
    private void updateMobDisplayNameWithModifiers(EliteMob elite, Set<EliteBehaviorModifier> modifiers) {
        if (modifiers.isEmpty()) return;
        
        String originalName = elite.getBaseDisplayName();
        if (originalName == null) {
            originalName = elite.getType().getTierSpecificName(elite.getTier());
        }
        
        // Remove existing color codes for clean formatting
        String cleanName = ChatColor.stripColor(originalName);
        
        // Add modifier prefixes (limit to 2 for readability)
        StringBuilder modifierPrefix = new StringBuilder();
        int count = 0;
        for (EliteBehaviorModifier modifier : modifiers) {
            if (count >= 2) break;
            if (count > 0) modifierPrefix.append(" ");
            modifierPrefix.append(modifier.getDisplayColor())
                         .append("[")
                         .append(modifier.getDisplayName().toUpperCase())
                         .append("]");
            count++;
        }
        
        // Create new name with modifiers
        String newName = modifierPrefix.toString() + " " + ChatColor.LIGHT_PURPLE + "§l" + cleanName;
        
        elite.getEntity().setCustomName(newName);
        // Store the display name in metadata since setBaseDisplayName may not exist
        elite.getEntity().setMetadata("elite_modifier_display_name", 
                new FixedMetadataValue(YakRealms.getInstance(), newName));
    }
    
    // ==================== MODIFIER EFFECT IMPLEMENTATIONS ====================
    
    private void startEnragedEffects(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.FLAME, loc, 2, 0.3, 0.3, 0.3, 0.01);
                    
                    // Set nearby players on fire occasionally
                    if (ThreadLocalRandom.current().nextInt(100) < 10) { // 10% chance
                        elite.getEntity().getNearbyEntities(4, 2, 4).stream()
                            .filter(e -> e instanceof Player)
                            .forEach(e -> ((Player) e).setFireTicks(40));
                    }
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 10L);
    }
    
    private void startPlaguebearerAura(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 3, 0.5, 0.5, 0.5, 0.02);
                    
                    // Apply poison to nearby players
                    elite.getEntity().getNearbyEntities(5, 3, 5).stream()
                        .filter(e -> e instanceof Player)
                        .forEach(e -> {
                            Player player = (Player) e;
                            if (ThreadLocalRandom.current().nextInt(100) < 5) { // 5% chance per tick
                                player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.POISON, 60, 1, false, false));
                            }
                        });
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 20L);
    }
    
    private void startRegenerativeAdaptation(EliteMob elite) {
        new BukkitRunnable() {
            private final Map<String, Integer> damageResistances = new HashMap<>();
            
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    // Enhanced regeneration effects
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    if (ThreadLocalRandom.current().nextInt(40) == 0) {
                        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 0.3, 0.3, 0.3, 0.05);
                    }
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 20L);
    }
    
    private void startPhantomPhasing(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.PORTAL, loc, 2, 0.3, 0.3, 0.3, 0.02);
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 30L);
    }
    
    private void startUnstableMagic(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    
                    // Random magical particles
                    Particle[] particles = {Particle.ENCHANT, Particle.WITCH, Particle.ELECTRIC_SPARK, Particle.END_ROD};
                    Particle randomParticle = particles[ThreadLocalRandom.current().nextInt(particles.length)];
                    loc.getWorld().spawnParticle(randomParticle, loc, 3, 0.5, 0.5, 0.5, 0.05);
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 15L);
    }
    
    private void startBloodthirstyStacking(EliteMob elite) {
        // Implementation would track damage dealt and kills for power stacking
        // This is a simplified version
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 1, 0.2, 0.2, 0.2, 0.01);
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 25L);
    }
    
    private void startDimensionalEffects(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 2, 0.3, 0.3, 0.3, 0.02);
                    
                    // Occasional teleportation
                    if (ThreadLocalRandom.current().nextInt(200) == 0) { // Rare teleport
                        List<Player> nearbyPlayers = elite.getEntity().getNearbyEntities(15, 5, 15).stream()
                            .filter(e -> e instanceof Player)
                            .map(e -> (Player) e)
                            .toList();
                        
                        if (!nearbyPlayers.isEmpty()) {
                            Player target = nearbyPlayers.get(ThreadLocalRandom.current().nextInt(nearbyPlayers.size()));
                            Location teleportLoc = target.getLocation().clone().add(
                                ThreadLocalRandom.current().nextDouble(-3, 3), 0, 
                                ThreadLocalRandom.current().nextDouble(-3, 3));
                            
                            elite.getEntity().teleport(teleportLoc);
                            teleportLoc.getWorld().spawnParticle(Particle.PORTAL, teleportLoc, 20, 1, 1, 1, 0.2);
                        }
                    }
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 10L);
    }
    
    private void startCorruptionAura(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 3, 0.5, 0.3, 0.5, 0.02);
                    
                    // Corrupt nearby area
                    elite.getEntity().getNearbyEntities(6, 3, 6).stream()
                        .filter(e -> e instanceof Player)
                        .forEach(e -> {
                            Player player = (Player) e;
                            if (ThreadLocalRandom.current().nextInt(100) < 3) { // 3% chance
                                player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.WITHER, 40, 0, false, false));
                            }
                        });
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 20L);
    }
    
    private void startAscendantAura(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 2, 0);
                    loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 5, 1, 1, 1, 0.05);
                    
                    // Buff nearby elite allies
                    elite.getEntity().getNearbyEntities(10, 5, 10).stream()
                        .filter(e -> e.hasMetadata("elite_mob"))
                        .forEach(e -> {
                            LivingEntity ally = (LivingEntity) e;
                            ally.addPotionEffect(new PotionEffect(
                                PotionEffectType.STRENGTH, 60, 1, false, false));
                        });
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 40L);
    }
    
    private void startTimeManipulation(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.5, 0.5, 0.5, 0.03);
                    
                    // Slow time around the elite
                    elite.getEntity().getNearbyEntities(8, 4, 8).stream()
                        .filter(e -> e instanceof Player)
                        .forEach(e -> {
                            Player player = (Player) e;
                            if (ThreadLocalRandom.current().nextInt(60) == 0) { // Occasional slowness
                                player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.SLOWNESS, 100, 2, false, false));
                            }
                        });
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 20L);
    }
    
    private void startEnvironmentalDestruction(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.5, 0.5, 0.5, 0.05);
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 30L);
    }
    
    private void startVoidEffects(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 4, 0.5, 0.5, 0.5, 0.05);
                    
                    // Reality distortion effects
                    elite.getEntity().getNearbyEntities(6, 3, 6).stream()
                        .filter(e -> e instanceof Player)
                        .forEach(e -> {
                            Player player = (Player) e;
                            if (ThreadLocalRandom.current().nextInt(200) == 0) { // Rare effect
                                player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.NAUSEA, 100, 1, false, false));
                                player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.BLINDNESS, 40, 0, false, false));
                            }
                        });
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 10L);
    }
    
    private void startGodslayerEffects(EliteMob elite) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elite.isValid()) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = elite.getEntity().getLocation().add(0, 1, 0);
                    // Ultimate particle effects
                    loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 8, 1, 1, 1, 0.1);
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc, 5, 0.5, 0.5, 0.5, 0.05);
                    loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 10, 1, 1, 1, 0.1);
                } catch (Exception e) {
                    // Ignore effect errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 5L); // More frequent effects
    }
    
    // ==================== MODIFIER RETRIEVAL ====================
    
    /**
     * Gets the modifiers assigned to a specific elite mob
     */
    public Set<EliteBehaviorModifier> getModifiers(UUID mobId) {
        return activeModifiers.getOrDefault(mobId, new HashSet<>());
    }
    
    /**
     * Gets the modifiers from mob metadata
     */
    public Set<EliteBehaviorModifier> getModifiersFromMetadata(EliteMob mob) {
        if (mob == null || !mob.isValid()) {
            return new HashSet<>();
        }
        
        if (mob.getEntity().hasMetadata("elite_modifiers")) {
            try {
                String modifierString = mob.getEntity().getMetadata("elite_modifiers").get(0).asString();
                Set<EliteBehaviorModifier> modifiers = new HashSet<>();
                
                for (String modifierName : modifierString.split(",")) {
                    try {
                        EliteBehaviorModifier modifier = EliteBehaviorModifier.valueOf(modifierName.trim());
                        modifiers.add(modifier);
                    } catch (IllegalArgumentException e) {
                        logger.warning("Unknown modifier: " + modifierName);
                    }
                }
                
                return modifiers;
            } catch (Exception e) {
                logger.warning("Failed to parse modifier metadata: " + e.getMessage());
            }
        }
        
        return new HashSet<>();
    }
    
    // ==================== CLEANUP ====================
    
    /**
     * Removes modifier tracking when an elite mob is removed
     */
    public void removeModifiers(UUID mobId) {
        activeModifiers.remove(mobId);
    }
    
    /**
     * Clears all modifier tracking (for plugin reload/restart)
     */
    public void clearAll() {
        activeModifiers.clear();
        for (EliteBehaviorModifier modifier : EliteBehaviorModifier.values()) {
            modifierSpawnCounts.put(modifier, 0);
        }
        totalModifiedElites = 0;
        logger.info("Cleared all elite modifier tracking");
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createModifierActivationEffect(Location location, Set<EliteBehaviorModifier> modifiers) {
        try {
            if (location.getWorld() == null) return;
            
            // Create effects based on modifier rarity
            for (EliteBehaviorModifier modifier : modifiers) {
                switch (modifier.getRarity()) {
                    case COMMON:
                        location.getWorld().spawnParticle(Particle.ENCHANT, location.add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.05);
                        break;
                    case UNCOMMON:
                        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 15, 0.8, 0.5, 0.8, 0.05);
                        break;
                    case RARE:
                        location.getWorld().spawnParticle(Particle.END_ROD, location, 20, 1, 1, 1, 0.1);
                        break;
                    case EPIC:
                        location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 25, 1.5, 1, 1.5, 0.1);
                        break;
                    case LEGENDARY:
                        location.getWorld().spawnParticle(Particle.SONIC_BOOM, location, 1, 0, 0, 0, 0);
                        location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 40, 2, 2, 2, 0.2);
                        break;
                }
            }
            
            // Play sound based on highest rarity modifier
            EliteBehaviorModifier.ModifierRarity highestRarity = modifiers.stream()
                .map(EliteBehaviorModifier::getRarity)
                .max(Enum::compareTo)
                .orElse(EliteBehaviorModifier.ModifierRarity.COMMON);
            
            Sound sound = switch (highestRarity) {
                case COMMON -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
                case UNCOMMON -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                case RARE -> Sound.BLOCK_BEACON_ACTIVATE;
                case EPIC -> Sound.ITEM_TOTEM_USE;
                case LEGENDARY -> Sound.ENTITY_WARDEN_SONIC_BOOM;
            };
            
            location.getWorld().playSound(location, sound, 1.0f, 1.2f);
            
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    // ==================== STATISTICS ====================
    
    /**
     * Gets spawn statistics for all modifiers
     */
    public Map<EliteBehaviorModifier, Integer> getModifierSpawnStats() {
        return new HashMap<>(modifierSpawnCounts);
    }
    
    /**
     * Gets the total number of modified elites
     */
    public int getTotalModifiedElites() {
        return totalModifiedElites;
    }
    
    /**
     * Logs current modifier distribution statistics
     */
    public void logModifierStatistics() {
        logger.info("=== Elite Modifier Statistics ===");
        logger.info("Total Modified Elites: " + totalModifiedElites);
        
        for (EliteBehaviorModifier modifier : EliteBehaviorModifier.values()) {
            int count = modifierSpawnCounts.get(modifier);
            double percentage = totalModifiedElites > 0 ? (double) count / totalModifiedElites * 100.0 : 0.0;
            logger.info(String.format("%s (%s): %d spawns (%.1f%%)", 
                       modifier.getDisplayName(), modifier.getRarity().getDisplayName(), count, percentage));
        }
        logger.info("=================================");
    }
}