package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mob Personality Behavior - Adds unique personalities and combat styles
 * 
 * Features:
 * - Different combat personalities for variety
 * - Personality-based movement and attack patterns
 * - Subtle behavioral differences that make each mob unique
 * - Tier-based personality complexity
 */
public class MobPersonalityBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "mob_personality";
    private static final int BEHAVIOR_PRIORITY = 50; // Lower priority, adds flavor
    
    // Personality tracking
    private final Map<UUID, PersonalityData> personalityStates = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int PERSONALITY_UPDATE_INTERVAL = 100; // 5 seconds
    private static final double PERSONALITY_TRAIT_CHANCE = 0.4; // 40% chance per trait
    
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    
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
        // Apply to all mobs for personality variety
        return mob != null && mob.isValid();
    }
    
    @Override
    public boolean isActive() {
        return true;
    }
    
    @Override
    public void onApply(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        UUID mobId = entity.getUniqueId();
        PersonalityData data = generatePersonality(mob);
        personalityStates.put(mobId, data);
        
        entity.setMetadata("mob_personality", 
            new org.bukkit.metadata.FixedMetadataValue(YakRealms.getInstance(), data.primaryPersonality.name()));
    }
    
    @Override
    public void onTick(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || !entity.isValid()) return;
        
        UUID mobId = entity.getUniqueId();
        PersonalityData data = personalityStates.get(mobId);
        if (data == null) return;
        
        long currentTick = entity.getTicksLived();
        
        // Update personality behavior periodically
        if (currentTick % PERSONALITY_UPDATE_INTERVAL == 0) {
            processPersonalityBehavior(mob, data);
        }
        
        // Apply ongoing personality effects
        applyOngoingPersonalityEffects(mob, data, currentTick);
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        // Personality may affect response to damage
        UUID mobId = mob.getEntity().getUniqueId();
        PersonalityData data = personalityStates.get(mobId);
        if (data == null || !(attacker instanceof Player)) return;
        
        // Some personalities react differently to taking damage
        switch (data.primaryPersonality) {
            case BERSERKER -> {
                // Berserkers get more aggressive when damaged
                LivingEntity entity = mob.getEntity();
                if (entity.getHealth() < entity.getMaxHealth() * 0.5) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, false, false));
                }
            }
            case ADAPTIVE -> {
                // Adaptive mobs change tactics when damaged
                data.lastPersonalityUpdate = 0; // Force personality update
            }
        }
    }

    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Clean up personality data on death
        if (mob.getEntity() != null) {
            personalityStates.remove(mob.getEntity().getUniqueId());
        }
    }

    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        // Personality may affect damage output
        UUID mobId = mob.getEntity().getUniqueId();
        PersonalityData data = personalityStates.get(mobId);
        if (data == null) return damage;
        
        double modifier = 1.0;
        
        switch (data.primaryPersonality) {
            case AGGRESSIVE -> modifier = 1.1; // 10% more damage
            case CAUTIOUS -> modifier = 0.95; // 5% less damage but more defensive
            case BERSERKER -> {
                // More damage when low health
                double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
                modifier = 1.0 + (0.3 * (1.0 - healthPercent)); // Up to 30% more damage at low health
            }
            case PREDATORY -> {
                // More damage to weakened targets
                if (target instanceof Player player) {
                    double targetHealthPercent = player.getHealth() / player.getMaxHealth();
                    if (targetHealthPercent < 0.5) {
                        modifier = 1.15;
                    }
                }
            }
        }
        
        return damage * modifier * data.intensityMultiplier;
    }

    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        // Different personalities react differently to player detection
        UUID mobId = mob.getEntity().getUniqueId();
        PersonalityData data = personalityStates.get(mobId);
        if (data == null) return;
        
        switch (data.primaryPersonality) {
            case CAUTIOUS -> {
                // Cautious mobs back away initially
                LivingEntity entity = mob.getEntity();
                Vector direction = entity.getLocation().subtract(player.getLocation()).toVector().normalize();
                entity.setVelocity(direction.multiply(0.2).setY(0));
            }
            case TERRITORIAL -> {
                // Territorial mobs become aggressive immediately
                mob.getEntity().addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, false));
            }
            case PREDATORY -> {
                // Predatory mobs evaluate the target first
                double playerHealthPercent = player.getHealth() / player.getMaxHealth();
                if (playerHealthPercent < 0.7) {
                    // Target looks weak, become aggressive
                    mob.getEntity().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 0, false, false));
                }
            }
        }
    }

    @Override
    public void onRemove(CustomMob mob) {
        if (mob.getEntity() != null) {
            personalityStates.remove(mob.getEntity().getUniqueId());
        }
    }
    
    // ==================== PERSONALITY TYPES ====================
    
    public enum PersonalityType {
        // Basic personalities (all tiers)
        AGGRESSIVE("Aggressive", "Charges directly at enemies", 0.25),
        CAUTIOUS("Cautious", "Keeps distance and observes", 0.20),
        PERSISTENT("Persistent", "Never gives up the chase", 0.20),
        
        // Advanced personalities (tier 2+)
        CUNNING("Cunning", "Uses hit-and-run tactics", 0.15),
        PROTECTIVE("Protective", "Defends nearby allies", 0.10),
        TERRITORIAL("Territorial", "Fights fiercely in home area", 0.10),
        
        // Elite personalities (tier 3+)
        PREDATORY("Predatory", "Stalks weak targets", 0.08),
        ADAPTIVE("Adaptive", "Changes tactics mid-fight", 0.07),
        BERSERKER("Berserker", "Becomes more dangerous when hurt", 0.05);
        
        private final String displayName;
        private final String description;
        private final double rarity;
        
        PersonalityType(String displayName, String description, double rarity) {
            this.displayName = displayName;
            this.description = description;
            this.rarity = rarity;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public double getRarity() { return rarity; }
    }
    
    // ==================== PERSONALITY GENERATION ====================
    
    private PersonalityData generatePersonality(CustomMob mob) {
        PersonalityData data = new PersonalityData();
        
        // Select primary personality based on tier and rarity
        data.primaryPersonality = selectPersonalityForTier(mob.getTier());
        
        // Add secondary traits for higher tiers
        if (mob.getTier() >= 3) {
            data.secondaryTraits.addAll(selectSecondaryTraits(mob.getTier()));
        }
        
        // Elite mobs get intensity bonus
        data.intensityMultiplier = mob.isElite() ? 1.3 : 1.0;
        
        return data;
    }
    
    private PersonalityType selectPersonalityForTier(int tier) {
        List<PersonalityType> availablePersonalities = new ArrayList<>();
        
        // Always available
        availablePersonalities.addAll(Arrays.asList(
            PersonalityType.AGGRESSIVE, PersonalityType.CAUTIOUS, PersonalityType.PERSISTENT
        ));
        
        // Tier 2+ personalities
        if (tier >= 2) {
            availablePersonalities.addAll(Arrays.asList(
                PersonalityType.CUNNING, PersonalityType.PROTECTIVE, PersonalityType.TERRITORIAL
            ));
        }
        
        // Tier 3+ personalities
        if (tier >= 3) {
            availablePersonalities.addAll(Arrays.asList(
                PersonalityType.PREDATORY, PersonalityType.ADAPTIVE, PersonalityType.BERSERKER
            ));
        }
        
        // Weighted random selection based on rarity
        double totalWeight = availablePersonalities.stream().mapToDouble(PersonalityType::getRarity).sum();
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0.0;
        
        for (PersonalityType personality : availablePersonalities) {
            currentWeight += personality.getRarity();
            if (randomValue <= currentWeight) {
                return personality;
            }
        }
        
        return PersonalityType.AGGRESSIVE; // Fallback
    }
    
    private List<PersonalityType> selectSecondaryTraits(int tier) {
        List<PersonalityType> traits = new ArrayList<>();
        List<PersonalityType> availableTraits = new ArrayList<>(Arrays.asList(PersonalityType.values()));
        
        int maxTraits = Math.min(2, tier - 2); // Max 2 traits, starting from tier 3
        
        for (int i = 0; i < maxTraits; i++) {
            if (random.nextDouble() < PERSONALITY_TRAIT_CHANCE && !availableTraits.isEmpty()) {
                PersonalityType trait = availableTraits.get(random.nextInt(availableTraits.size()));
                traits.add(trait);
                availableTraits.remove(trait);
            }
        }
        
        return traits;
    }
    
    // ==================== PERSONALITY BEHAVIOR PROCESSING ====================
    
    private void processPersonalityBehavior(CustomMob mob, PersonalityData data) {
        List<Player> nearbyPlayers = findNearbyPlayers(mob.getEntity(), 15.0);
        if (nearbyPlayers.isEmpty()) return;
        
        Player target = findClosestPlayer(mob.getEntity(), nearbyPlayers);
        if (target == null) return;
        
        // Apply primary personality behavior
        applyPersonalityBehavior(mob, target, data.primaryPersonality, data.intensityMultiplier);
        
        // Apply secondary traits
        for (PersonalityType trait : data.secondaryTraits) {
            applyPersonalityBehavior(mob, target, trait, data.intensityMultiplier * 0.6); // Reduced intensity for secondary traits
        }
        
        data.lastPersonalityUpdate = System.currentTimeMillis();
    }
    
    private void applyPersonalityBehavior(CustomMob mob, Player target, PersonalityType personality, double intensity) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        switch (personality) {
            case AGGRESSIVE -> applyAggressiveBehavior(entity, target, intensity);
            case CAUTIOUS -> applyCautiousBehavior(entity, target, intensity);
            case PERSISTENT -> applyPersistentBehavior(entity, target, intensity);
            case CUNNING -> applyCunningBehavior(entity, target, intensity);
            case PROTECTIVE -> applyProtectiveBehavior(entity, target, intensity);
            case TERRITORIAL -> applyTerritorialBehavior(entity, target, intensity);
            case PREDATORY -> applyPredatoryBehavior(entity, target, intensity);
            case ADAPTIVE -> applyAdaptiveBehavior(entity, target, intensity);
            case BERSERKER -> applyBerserkerBehavior(entity, target, intensity);
        }
    }
    
    // ==================== PERSONALITY IMPLEMENTATIONS ====================
    
    private void applyAggressiveBehavior(LivingEntity entity, Player target, double intensity) {
        // Direct charge toward target
        Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
        double speed = 0.3 * intensity;
        entity.setVelocity(direction.multiply(speed).setY(0));
        
        // Occasional speed burst
        if (random.nextDouble() < 0.1 * intensity) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
        }
    }
    
    private void applyCautiousBehavior(LivingEntity entity, Player target, double intensity) {
        double distance = entity.getLocation().distance(target.getLocation());
        double optimalDistance = 6.0 + (2.0 * intensity);
        
        Vector direction;
        if (distance < optimalDistance) {
            // Back away
            direction = entity.getLocation().subtract(target.getLocation()).toVector().normalize();
        } else if (distance > optimalDistance + 3.0) {
            // Move closer cautiously
            direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
        } else {
            // Circle strafe
            Vector toTarget = target.getLocation().subtract(entity.getLocation()).toVector();
            direction = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        }
        
        entity.setVelocity(direction.multiply(0.2 * intensity).setY(0));
    }
    
    private void applyPersistentBehavior(LivingEntity entity, Player target, double intensity) {
        // Never stops chasing - even at long range
        Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
        entity.setVelocity(direction.multiply(0.25 * intensity).setY(0));
        
        // Resistance to knockback and slowing
        if (random.nextDouble() < 0.2 * intensity) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, false));
        }
    }
    
    private void applyCunningBehavior(LivingEntity entity, Player target, double intensity) {
        // Hit and run tactics - approach, attack, then retreat
        double distance = entity.getLocation().distance(target.getLocation());
        
        if (distance > 8.0) {
            // Approach quickly
            Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
            entity.setVelocity(direction.multiply(0.4 * intensity).setY(0));
        } else if (distance < 3.0) {
            // Retreat after getting close
            Vector direction = entity.getLocation().subtract(target.getLocation()).toVector().normalize();
            entity.setVelocity(direction.multiply(0.35 * intensity).setY(0));
        }
    }
    
    private void applyProtectiveBehavior(LivingEntity entity, Player target, double intensity) {
        // Look for nearby allies to protect
        List<LivingEntity> allies = findNearbyAllies(entity);
        if (allies.isEmpty()) return;
        
        // Position between allies and threat
        LivingEntity ally = allies.get(0);
        Vector protectivePosition = ally.getLocation().add(target.getLocation()).toVector().multiply(0.5);
        Location targetPos = new Location(entity.getWorld(), protectivePosition.getX(), entity.getY(), protectivePosition.getZ());
        
        Vector direction = targetPos.subtract(entity.getLocation()).toVector().normalize();
        entity.setVelocity(direction.multiply(0.3 * intensity).setY(0));
    }
    
    private void applyTerritorialBehavior(LivingEntity entity, Player target, double intensity) {
        // Fights more fiercely near spawn location
        // For simplicity, consider current area as territory
        double distance = entity.getLocation().distance(target.getLocation());
        
        if (distance < 10.0) { // In territory
            // More aggressive
            Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
            entity.setVelocity(direction.multiply(0.35 * intensity).setY(0));
            
            // Occasional strength boost
            if (random.nextDouble() < 0.15 * intensity) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, false));
            }
        }
    }
    
    private void applyPredatoryBehavior(LivingEntity entity, Player target, double intensity) {
        // Focus on weakened targets
        double targetHealthPercent = target.getHealth() / target.getMaxHealth();
        
        if (targetHealthPercent < 0.5) {
            // Aggressive pursuit of weak target
            Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
            entity.setVelocity(direction.multiply(0.4 * intensity).setY(0));
            
            // Speed boost when hunting weak prey
            if (random.nextDouble() < 0.2 * intensity) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false));
            }
        } else {
            // More cautious with healthy targets
            applyCautiousBehavior(entity, target, intensity * 0.7);
        }
    }
    
    private void applyAdaptiveBehavior(LivingEntity entity, Player target, double intensity) {
        PersonalityData data = personalityStates.get(entity.getUniqueId());
        if (data == null) return;
        
        // Change tactics every few seconds
        long timeSinceUpdate = System.currentTimeMillis() - data.lastPersonalityUpdate;
        if (timeSinceUpdate > 8000) { // 8 seconds
            // Randomly switch between different base behaviors
            PersonalityType[] adaptiveBehaviors = {
                PersonalityType.AGGRESSIVE, PersonalityType.CAUTIOUS, PersonalityType.CUNNING
            };
            PersonalityType currentBehavior = adaptiveBehaviors[random.nextInt(adaptiveBehaviors.length)];
            applyPersonalityBehavior(null, target, currentBehavior, intensity);
        }
    }
    
    private void applyBerserkerBehavior(LivingEntity entity, Player target, double intensity) {
        double healthPercent = entity.getHealth() / entity.getMaxHealth();
        
        // Becomes more dangerous as health decreases
        double berserkerIntensity = intensity * (1.5 - healthPercent); // More intense at lower health
        
        Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
        entity.setVelocity(direction.multiply(0.25 * berserkerIntensity).setY(0));
        
        // Strength and speed increase when very low
        if (healthPercent < 0.3 && random.nextDouble() < 0.25 * intensity) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 0, false, false));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false));
        }
    }
    
    // ==================== ONGOING EFFECTS ====================
    
    private void applyOngoingPersonalityEffects(CustomMob mob, PersonalityData data, long currentTick) {
        // Apply subtle ongoing effects based on personality
        LivingEntity entity = mob.getEntity();
        
        // Very subtle ongoing effects every 5 seconds
        if (currentTick % 100 == 0) {
            switch (data.primaryPersonality) {
                case PERSISTENT -> {
                    // Slight resistance to slowing effects
                    if (entity.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                        entity.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                }
                case CAUTIOUS -> {
                    // Better at avoiding damage (subtle resistance)
                    if (random.nextDouble() < 0.1) {
                        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 0, false, false));
                    }
                }
                case BERSERKER -> {
                    // Gradual strength increase as fight continues
                    if (entity.getHealth() < entity.getMaxHealth() * 0.7) {
                        if (random.nextDouble() < 0.05) {
                            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
                        }
                    }
                }
            }
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    private List<Player> findNearbyPlayers(LivingEntity entity, double radius) {
        return entity.getWorld().getNearbyEntities(entity.getLocation(), radius, radius, radius)
            .stream()
            .filter(e -> e instanceof Player && ((Player) e).isValid())
            .map(e -> (Player) e)
            .toList();
    }
    
    private Player findClosestPlayer(LivingEntity entity, List<Player> players) {
        return players.stream()
            .filter(p -> p.getGameMode() != org.bukkit.GameMode.SPECTATOR)
            .min((p1, p2) -> Double.compare(
                entity.getLocation().distance(p1.getLocation()),
                entity.getLocation().distance(p2.getLocation())))
            .orElse(null);
    }
    
    private List<LivingEntity> findNearbyAllies(LivingEntity entity) {
        return entity.getWorld().getNearbyEntities(entity.getLocation(), 8, 4, 8)
            .stream()
            .filter(e -> e instanceof LivingEntity && e != entity)
            .filter(e -> !(e instanceof Player))
            .filter(e -> e.hasMetadata("custommob") || e.hasMetadata("type"))
            .map(e -> (LivingEntity) e)
            .toList();
    }
    
    // ==================== DATA CLASSES ====================
    
    private static class PersonalityData {
        public PersonalityType primaryPersonality;
        public List<PersonalityType> secondaryTraits = new ArrayList<>();
        public double intensityMultiplier = 1.0;
        public long lastPersonalityUpdate = 0;
        
        public PersonalityData() {}
    }
}