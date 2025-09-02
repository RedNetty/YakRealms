package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Advanced elite combat behavior implementation for Paper Spigot 1.21.8.
 * 
 * <p>This behavior demonstrates the full capabilities of the mob behavior system with:
 * <ul>
 *   <li><strong>Dynamic Combat Scaling:</strong> Damage and abilities scale with mob tier</li>
 *   <li><strong>Rage Mechanics:</strong> Enhanced abilities when health is low</li>
 *   <li><strong>Environmental Effects:</strong> Particles and sounds during combat</li>
 *   <li><strong>Player Interaction:</strong> Special behaviors when players are detected</li>
 *   <li><strong>Paper API Integration:</strong> Modern scheduling and entity features</li>
 * </ul>
 * 
 * <h3>Combat Phases:</h3>
 * <ol>
 *   <li><strong>Normal Phase (100%-50% health):</strong> Standard elite abilities</li>
 *   <li><strong>Enraged Phase (50%-25% health):</strong> Increased damage and speed</li>
 *   <li><strong>Desperate Phase (25%-0% health):</strong> Maximum power and special attacks</li>
 * </ol>
 * 
 * @author YakRealms Development Team
 * @version 1.0.0
 * @see MobBehavior for the behavior interface
 * @see CustomMob for mob system integration
 */
public class EliteCombatBehavior implements MobBehavior {
    
    // ==================== BEHAVIOR CONSTANTS ====================
    
    /** Unique identifier for this behavior type */
    private static final String BEHAVIOR_ID = "elite_combat";
    
    /** Priority for combat-related behaviors (high priority) */
    private static final int BEHAVIOR_PRIORITY = 500;
    
    /** Health threshold for entering enraged phase */
    private static final double ENRAGED_HEALTH_THRESHOLD = 0.5;
    
    /** Health threshold for entering desperate phase */
    private static final double DESPERATE_HEALTH_THRESHOLD = 0.25;
    
    /** Minimum tier required for this behavior */
    private static final int MINIMUM_TIER = 3;
    
    /** Cooldown between special abilities (in ticks) - rare special events only */
    private static final int ABILITY_COOLDOWN_TICKS = 1200; // 60 seconds - abilities are now rare special events
    
    // ==================== INSTANCE STATE ====================
    
    /** Tracks the last tick when a special ability was used */
    private long lastAbilityTick = 0;
    
    /** Current combat phase of the mob */
    private CombatPhase currentPhase = CombatPhase.NORMAL;
    
    /** Random instance for ability variation */
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    
    /** Anti-spam tracking */
    private long lastMessageTick = 0;
    private long lastEffectTick = 0;
    private long lastSoundTick = 0;
    
    /** Ability usage tracking - limit to 1-3 times per fight */
    private int abilitiesUsedThisFight = 0;
    private static final int MAX_ABILITIES_PER_FIGHT = 3;
    private long combatStartTime = 0;
    
    /** Minimum intervals to prevent spam */
    private static final int MESSAGE_COOLDOWN = 200; // 10 seconds
    private static final int EFFECT_COOLDOWN = 60;   // 3 seconds  
    private static final int SOUND_COOLDOWN = 100;   // 5 seconds
    
    /** Reaction time constants */
    private static final int ABILITY_WARNING_TIME = 60; // 3 seconds warning before ability
    
    /**
     * Combat phases with different ability sets and power levels.
     */
    private enum CombatPhase {
        NORMAL(1.0, 1.0),
        ENRAGED(1.3, 1.2),
        DESPERATE(1.6, 1.5);
        
        private final double damageMultiplier;
        private final double speedMultiplier;
        
        CombatPhase(double damageMultiplier, double speedMultiplier) {
            this.damageMultiplier = damageMultiplier;
            this.speedMultiplier = speedMultiplier;
        }
        
        public double getDamageMultiplier() { return damageMultiplier; }
        public double getSpeedMultiplier() { return speedMultiplier; }
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
        // Only apply to elite mobs of sufficient tier
        return mob != null && 
               mob.isElite() && 
               mob.getTier() >= MINIMUM_TIER &&
               mob.isValid();
    }
    
    @Override
    public boolean isActive() {
        // Always active for elite combat mobs
        return true;
    }
    
    @Override
    public void onApply(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Grant initial elite combat bonuses
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            
            // Increase health for elite combat
            double healthBonus = mob.getTier() * 0.2; // 20% per tier
            double currentMaxHealth = entity.getMaxHealth();
            entity.setMaxHealth(currentMaxHealth * (1.0 + healthBonus));
            entity.setHealth(entity.getMaxHealth());
            
            // Create application effect
            createEliteActivationEffect(entity.getLocation());
            
        } catch (Exception e) {
            // Log error but don't fail behavior application
            System.err.println("[EliteCombatBehavior] Error applying behavior: " + e.getMessage());
        }
    }
    
    @Override
    public void onTick(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || !entity.isValid()) return;
        
        try {
            // Track combat start time
            if (combatStartTime == 0) {
                combatStartTime = System.currentTimeMillis();
            }
            
            // Update combat phase based on health
            updateCombatPhase(mob);
            
            // Apply phase-specific effects (very rarely)
            if (random.nextInt(200) == 0) { // Only 0.5% chance per tick
                applyPhaseEffects(mob);
            }
            
            // Execute special abilities - VERY rarely and limited per fight
            long currentTick = entity.getTicksLived();
            if (shouldUseSpecialAbility(currentTick)) {
                executeSpecialAbility(mob);
                lastAbilityTick = currentTick;
                abilitiesUsedThisFight++;
            }
            
        } catch (Exception e) {
            // Ignore tick errors to prevent spam
        }
    }
    
    /**
     * Determines if a special ability should be used (very restrictive)
     */
    private boolean shouldUseSpecialAbility(long currentTick) {
        // Never exceed max abilities per fight
        if (abilitiesUsedThisFight >= MAX_ABILITIES_PER_FIGHT) {
            return false;
        }
        
        // Must wait full cooldown
        if (currentTick - lastAbilityTick < ABILITY_COOLDOWN_TICKS) {
            return false;
        }
        
        // Must be in combat for at least 30 seconds before first ability
        long combatDuration = System.currentTimeMillis() - combatStartTime;
        if (abilitiesUsedThisFight == 0 && combatDuration < 30000) {
            return false;
        }
        
        // Random chance - only 10% chance when all conditions are met
        return random.nextDouble() < 0.1;
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Create damage indication effects
            createDamageEffect(entity.getLocation(), damage);
            
            // Very rare chance to trigger counter-attack in desperate phase only
            if (currentPhase == CombatPhase.DESPERATE && attacker != null && abilitiesUsedThisFight < MAX_ABILITIES_PER_FIGHT) {
                if (random.nextDouble() < 0.02) { // Extremely rare - 2% chance only
                    triggerCounterAttack(mob, attacker);
                    abilitiesUsedThisFight++; // Count as ability use
                }
            }
            
        } catch (Exception e) {
            // Ignore damage processing errors
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        try {
            // Apply phase-based damage multiplier
            double modifiedDamage = damage * currentPhase.getDamageMultiplier();
            
            // Tier-based damage scaling
            double tierMultiplier = 1.0 + (mob.getTier() - 3) * 0.1; // +10% per tier above 3
            modifiedDamage *= tierMultiplier;
            
            // Special attack effects
            if (target instanceof Player) {
                applySpecialAttackEffects(mob, (Player) target);
            }
            
            return modifiedDamage;
            
        } catch (Exception e) {
            return damage; // Return original damage on error
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        try {
            long currentTick = System.currentTimeMillis() / 50; // Convert to tick equivalent
            
            // Play intimidation sound for elite mobs (with cooldown)
            if (currentTick - lastSoundTick >= SOUND_COOLDOWN) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.3f, 1.2f);
                lastSoundTick = currentTick;
            }
            
            // Apply fear effect in desperate phase (with cooldown)
            if (currentPhase == CombatPhase.DESPERATE && currentTick - lastEffectTick >= EFFECT_COOLDOWN) {
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 60, 0, false, false)); // 3 second slowness, level 0
                lastEffectTick = currentTick;
            }
            
        } catch (Exception e) {
            // Ignore player detection errors
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        try {
            LivingEntity entity = mob.getEntity();
            if (entity != null) {
                // Create dramatic death effect
                createEliteDeathEffect(entity.getLocation());
                
                // Award bonus experience to killer if it's a player
                if (killer instanceof Player player) {
                    int bonusExp = mob.getTier() * 50; // 50 exp per tier
                    player.giveExp(bonusExp);
                }
            }
            
        } catch (Exception e) {
            // Ignore death processing errors
        }
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        try {
            LivingEntity entity = mob.getEntity();
            if (entity != null && entity.isValid()) {
                // Remove applied potion effects
                entity.removePotionEffect(PotionEffectType.RESISTANCE);
                entity.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                entity.removePotionEffect(PotionEffectType.SPEED);
                entity.removePotionEffect(PotionEffectType.STRENGTH);
            }
            
            // Reset state
            currentPhase = CombatPhase.NORMAL;
            lastAbilityTick = 0;
            
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Updates the current combat phase based on mob health percentage.
     */
    private void updateCombatPhase(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        double healthPercentage = entity.getHealth() / entity.getMaxHealth();
        CombatPhase newPhase;
        
        if (healthPercentage <= DESPERATE_HEALTH_THRESHOLD) {
            newPhase = CombatPhase.DESPERATE;
        } else if (healthPercentage <= ENRAGED_HEALTH_THRESHOLD) {
            newPhase = CombatPhase.ENRAGED;
        } else {
            newPhase = CombatPhase.NORMAL;
        }
        
        // Apply phase transition effects
        if (newPhase != currentPhase) {
            onPhaseTransition(mob, currentPhase, newPhase);
            currentPhase = newPhase;
        }
    }
    
    /**
     * Handles phase transition with appropriate effects and modifications.
     */
    private void onPhaseTransition(CustomMob mob, CombatPhase oldPhase, CombatPhase newPhase) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Remove old phase effects
            entity.removePotionEffect(PotionEffectType.SPEED);
            entity.removePotionEffect(PotionEffectType.STRENGTH);
            
            // Apply new phase effects
            switch (newPhase) {
                case ENRAGED:
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
                    createPhaseTransitionEffect(entity.getLocation(), Particle.FLAME);
                    break;
                    
                case DESPERATE:
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
                    entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false));
                    createPhaseTransitionEffect(entity.getLocation(), Particle.SOUL_FIRE_FLAME);
                    break;
                    
                default:
                    // Normal phase has no special effects
                    break;
            }
            
        } catch (Exception e) {
            // Ignore phase transition errors
        }
    }
    
    /**
     * Applies ongoing effects based on the current combat phase with anti-spam.
     */
    private void applyPhaseEffects(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            long currentTick = entity.getTicksLived();
            
            // Only spawn effects with proper cooldown to prevent spam
            if (currentTick - lastEffectTick < EFFECT_COOLDOWN) {
                return;
            }
            
            // Phase-specific particle effects (reduced frequency)
            Location loc = entity.getLocation().add(0, entity.getHeight() / 2, 0);
            
            switch (currentPhase) {
                case ENRAGED:
                    if (random.nextInt(40) == 0) { // Reduced from 10% to 2.5% chance per tick
                        entity.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0.2, 0.2, 0.2, 0.01);
                        lastEffectTick = currentTick;
                    }
                    break;
                    
                case DESPERATE:
                    if (random.nextInt(30) == 0) { // Reduced from 20% to 3.3% chance per tick
                        entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 2, 0.3, 0.3, 0.3, 0.01);
                        lastEffectTick = currentTick;
                    }
                    break;
                    
                default:
                    // No ongoing effects for normal phase
                    break;
            }
            
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    /**
     * Executes special abilities based on the current combat phase.
     */
    private void executeSpecialAbility(CustomMob mob) {
        switch (currentPhase) {
            case ENRAGED:
                executeEnragedAbility(mob);
                break;
                
            case DESPERATE:
                executeDesperateAbility(mob);
                break;
                
            default:
                executeNormalAbility(mob);
                break;
        }
    }
    
    /**
     * Executes rare intimidation ability with warning period
     */
    private void executeNormalAbility(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            Location loc = entity.getLocation();
            
            // Warning phase - ominous sounds and particles
            entity.getWorld().playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.7f, 0.6f);
            entity.getWorld().spawnParticle(Particle.SMOKE, loc.add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.02);
            
            // Schedule the actual ability after warning
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    if (entity.isValid()) {
                        // Execute intimidation roar
                        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.8f);
                        
                        // Apply brief slowness to nearby players (much reduced effect)
                        entity.getNearbyEntities(3, 2, 3).stream()
                            .filter(e -> e instanceof Player)
                            .map(e -> (Player) e)
                            .limit(2) // Only affect 2 players max
                            .forEach(player -> player.addPotionEffect(
                                new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false)));
                    }
                } catch (Exception e) {
                    // Ignore delayed execution errors
                }
            }, ABILITY_WARNING_TIME);
                    
        } catch (Exception e) {
            // Ignore ability errors
        }
    }
    
    /**
     * Executes rare fire burst ability with warning period
     */
    private void executeEnragedAbility(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            Location loc = entity.getLocation();
            
            // Warning phase - building flames
            entity.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.8f);
            entity.getWorld().spawnParticle(Particle.FLAME, loc.add(0, 0.5, 0), 15, 0.5, 0.5, 0.5, 0.02);
            
            // Schedule the actual fire burst after warning
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    if (entity.isValid()) {
                        Location burstLoc = entity.getLocation();
                        
                        // Fire burst explosion
                        entity.getWorld().playSound(burstLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                        entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, burstLoc, 2, 0.3, 0.3, 0.3, 0);
                        
                        // Damage nearby entities (much reduced)
                        entity.getNearbyEntities(2.5, 2, 2.5).stream()
                            .filter(e -> e instanceof LivingEntity && !(e instanceof CustomMob))
                            .map(e -> (LivingEntity) e)
                            .limit(3) // Only 3 targets max
                            .forEach(target -> target.damage(mob.getTier() * 1.2, entity));
                    }
                } catch (Exception e) {
                    // Ignore delayed execution errors
                }
            }, ABILITY_WARNING_TIME);
                
        } catch (Exception e) {
            // Ignore ability errors
        }
    }
    
    /**
     * Executes ultimate soul drain ability with extended warning period
     */
    private void executeDesperateAbility(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            Location loc = entity.getLocation();
            
            // Extended warning phase - ominous buildup
            entity.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.5f);
            entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.add(0, 1, 0), 20, 1, 1, 1, 0.02);
            
            // Schedule the actual soul drain after extended warning (5 seconds for ultimate ability)
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    if (entity.isValid()) {
                        Location drainLoc = entity.getLocation();
                        
                        // Soul drain effect
                        entity.getWorld().playSound(drainLoc, Sound.BLOCK_SOUL_SAND_BREAK, 0.9f, 0.5f);
                        entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, drainLoc, 25, 2, 1, 2, 0.1);
                        
                        // Drain health from nearby players (very limited)
                        double totalHealing = 0;
                        int drainedPlayers = 0;
                        for (org.bukkit.entity.Entity nearby : entity.getNearbyEntities(3, 2, 3)) {
                            if (nearby instanceof Player player && drainedPlayers < 2) { // Only 2 players max
                                double drainAmount = Math.min(player.getHealth() * 0.03, 1.5); // Very reduced - 3% max 1.5 hearts
                                player.damage(drainAmount, entity);
                                totalHealing += drainAmount * 0.2; // Very low healing efficiency - 20%
                                drainedPlayers++;
                            }
                        }
                        
                        if (totalHealing > 0) {
                            double newHealth = Math.min(entity.getHealth() + totalHealing, entity.getMaxHealth());
                            entity.setHealth(newHealth);
                        }
                    }
                } catch (Exception e) {
                    // Ignore delayed execution errors
                }
            }, ABILITY_WARNING_TIME + 40); // 5 second warning for ultimate ability
            
        } catch (Exception e) {
            // Ignore ability errors
        }
    }
    
    /**
     * Triggers a rare special counter-attack with warning time for players to react
     */
    private void triggerCounterAttack(CustomMob mob, LivingEntity attacker) {
        try {
            LivingEntity entity = mob.getEntity();
            if (entity == null) return;
            
            long currentTick = entity.getTicksLived();
            Location entityLoc = entity.getLocation();
            
            // Give players 3 seconds warning - no titles, just sound and particles
            entity.getWorld().playSound(entityLoc, Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.8f);
            entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entityLoc.add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.02);
            
            // Schedule the actual counter-attack after warning period
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    if (entity.isValid() && attacker.isValid()) {
                        // Sound effect for the actual attack
                        entity.getWorld().playSound(entityLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
                        
                        // Deal counter-attack damage (reduced)
                        double counterDamage = mob.getTier() * 1.5 * currentPhase.getDamageMultiplier();
                        attacker.damage(counterDamage, entity);
                        
                        // Small explosion effect
                        entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, entityLoc, 1, 0, 0, 0, 0);
                    }
                } catch (Exception e) {
                    // Ignore delayed execution errors
                }
            }, ABILITY_WARNING_TIME); // 3 second delay
            
            lastAbilityTick = currentTick; // Update cooldown
            
        } catch (Exception e) {
            // Ignore counter-attack errors
        }
    }
    
    /**
     * Applies special effects when attacking players.
     */
    private void applySpecialAttackEffects(CustomMob mob, Player target) {
        try {
            // Phase-specific attack effects
            switch (currentPhase) {
                case ENRAGED:
                    // Apply fire damage
                    target.setFireTicks(40); // 2 seconds
                    break;
                    
                case DESPERATE:
                    // Apply wither effect
                    target.addPotionEffect(new PotionEffect(
                        PotionEffectType.WITHER, 60, 0, false, false)); // 3 seconds
                    break;
                    
                default:
                    // Normal phase has no special effects
                    break;
            }
            
        } catch (Exception e) {
            // Ignore effect application errors
        }
    }
    
    // ==================== VISUAL EFFECT METHODS ====================
    
    /**
     * Creates the elite activation effect when behavior is first applied.
     */
    private void createEliteActivationEffect(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location.add(0, 1, 0), 30, 1, 1, 1, 0.1);
                location.getWorld().playSound(location, Sound.ITEM_TOTEM_USE, 1.0f, 1.5f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    /**
     * Creates damage indication effects.
     */
    private void createDamageEffect(Location location, double damage) {
        try {
            if (location.getWorld() != null && damage > 50.0) { // Only for significant damage
                int particleCount = Math.min((int) (damage / 25.0), 10);
                location.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, 
                    location.add(0, 1, 0), particleCount, 0.5, 0.5, 0.5, 0.1);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    /**
     * Creates phase transition effects.
     */
    private void createPhaseTransitionEffect(Location location, Particle particle) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(particle, location.add(0, 1, 0), 20, 1, 1, 1, 0.05);
                location.getWorld().playSound(location, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.8f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    /**
     * Creates the elite death effect when the mob dies.
     */
    private void createEliteDeathEffect(Location location) {
        try {
            if (location.getWorld() != null) {
                // Spectacular death explosion
                location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 3, 1, 1, 1, 0);
                location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 40, 2, 2, 2, 0.1);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
}