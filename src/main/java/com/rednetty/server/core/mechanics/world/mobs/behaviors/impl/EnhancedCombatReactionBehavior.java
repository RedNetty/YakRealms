package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enhanced Combat Reaction Behavior - Subtle improvements to mob combat responses
 * 
 * Features:
 * - Improved hit reactions and staggers
 * - Dynamic health-based behavior changes
 * - Subtle visual feedback when taking damage
 * - Enhanced death sequences
 * - No over-the-top effects, maintains original feel
 */
public class EnhancedCombatReactionBehavior implements MobBehavior, Listener {
    
    private static final String BEHAVIOR_ID = "enhanced_combat_reaction";
    private static final int BEHAVIOR_PRIORITY = 75; // Lower priority, doesn't override main behaviors
    
    // Reaction state tracking
    private final Map<UUID, ReactionData> reactionStates = new ConcurrentHashMap<>();
    
    // Configuration - keeping effects subtle
    private static final double BIG_HIT_THRESHOLD = 0.15; // 15% of max health
    private static final double LOW_HEALTH_THRESHOLD = 0.3; // 30% health
    private static final int STAGGER_DURATION = 15; // 0.75 seconds
    private static final int HIT_REACTION_COOLDOWN = 40; // 2 seconds
    
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    
    public EnhancedCombatReactionBehavior() {
        // Register as event listener
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
    }
    
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
        // Apply to all valid mobs
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
        reactionStates.put(mobId, new ReactionData());
        
        entity.setMetadata("enhanced_reactions", 
            new org.bukkit.metadata.FixedMetadataValue(YakRealms.getInstance(), true));
    }
    
    @Override
    public void onTick(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || !entity.isValid()) return;
        
        UUID mobId = entity.getUniqueId();
        ReactionData data = reactionStates.get(mobId);
        if (data == null) return;
        
        // Update health-based behaviors
        updateHealthBasedBehavior(mob, data);
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        UUID mobId = mob.getEntity().getUniqueId();
        ReactionData data = reactionStates.get(mobId);
        if (data == null) return;
        
        LivingEntity entity = mob.getEntity();
        double damagePercent = damage / entity.getMaxHealth();
        
        // Enhanced hit reaction for significant damage
        if (damagePercent >= BIG_HIT_THRESHOLD) {
            performBigHitReaction(entity, attacker, data);
        }
        
        // Update last hit time
        data.lastHitTime = System.currentTimeMillis();
        data.lastAttacker = attacker;
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Enhanced death effects
        LivingEntity entity = mob.getEntity();
        if (entity != null) {
            performDeathReaction(entity, killer);
            reactionStates.remove(entity.getUniqueId());
        }
    }
    
    private void performBigHitReaction(LivingEntity entity, LivingEntity attacker, ReactionData data) {
        createSubtleHitReaction(entity, true);
        createStaggerReaction(entity, entity.getHealth() / entity.getMaxHealth() <= LOW_HEALTH_THRESHOLD);
    }
    
    private void performDeathReaction(LivingEntity entity, LivingEntity killer) {
        ReactionData data = reactionStates.get(entity.getUniqueId());
        if (data != null) {
            createEnhancedDeathEffect(entity, data);
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        // No damage modification for reaction behavior
        return damage;
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        // Enhanced alert behavior when detecting players
        UUID mobId = mob.getEntity().getUniqueId();
        ReactionData data = reactionStates.get(mobId);
        if (data == null) return;
        
        // Only react if not recently hit
        if (System.currentTimeMillis() - data.lastHitTime > 3000) {
            LivingEntity entity = mob.getEntity();
            // Subtle alert effects
            if (random.nextDouble() < 0.3) {
                entity.getWorld().spawnParticle(Particle.CRIT, 
                    entity.getEyeLocation(), 2, 0.2, 0.2, 0.2, 0.1);
            }
        }
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        if (mob.getEntity() != null) {
            reactionStates.remove(mob.getEntity().getUniqueId());
        }
    }
    
    // ==================== EVENT HANDLERS ====================
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof LivingEntity damaged)) return;
        if (!(event.getDamager() instanceof Player damager)) return;
        
        // Check if this is a custom mob with enhanced reactions
        if (!damaged.hasMetadata("enhanced_reactions")) return;
        
        ReactionData data = reactionStates.get(damaged.getUniqueId());
        if (data == null) return;
        
        processHitReaction(damaged, damager, event.getFinalDamage(), data);
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasMetadata("enhanced_reactions")) return;
        
        ReactionData data = reactionStates.get(entity.getUniqueId());
        if (data != null) {
            processDeathReaction(entity, data);
        }
        
        // Clean up
        reactionStates.remove(entity.getUniqueId());
    }
    
    // ==================== REACTION PROCESSING ====================
    
    private void processHitReaction(LivingEntity damaged, Player damager, double damage, ReactionData data) {
        long currentTime = System.currentTimeMillis();
        
        // Check reaction cooldown to prevent spam
        if (currentTime - data.lastHitReaction < HIT_REACTION_COOLDOWN * 50) return;
        
        double healthPercent = damaged.getHealth() / damaged.getMaxHealth();
        double damagePercent = damage / damaged.getMaxHealth();
        
        // Determine reaction intensity
        boolean isBigHit = damagePercent >= BIG_HIT_THRESHOLD;
        boolean isLowHealth = healthPercent <= LOW_HEALTH_THRESHOLD;
        
        // Subtle hit reaction
        createSubtleHitReaction(damaged, isBigHit);
        
        // Stagger for big hits
        if (isBigHit && !damaged.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            createStaggerReaction(damaged, isLowHealth);
        }
        
        // Low health desperation (only once)
        if (isLowHealth && !data.hasEnteredLowHealthMode) {
            enterLowHealthMode(damaged, data);
        }
        
        data.lastHitReaction = currentTime;
    }
    
    private void createSubtleHitReaction(LivingEntity damaged, boolean isBigHit) {
        // Very subtle particle effects - just a few particles
        if (isBigHit) {
            damaged.getWorld().spawnParticle(Particle.CRIT, 
                damaged.getLocation().add(0, damaged.getHeight() / 2, 0), 
                3, 0.3, 0.3, 0.3, 0.1);
        } else {
            damaged.getWorld().spawnParticle(Particle.CRIT, 
                damaged.getLocation().add(0, damaged.getHeight() / 2, 0), 
                1, 0.2, 0.2, 0.2, 0.05);
        }
        
        // Subtle sound - only for bigger hits
        if (isBigHit) {
            damaged.getWorld().playSound(damaged.getLocation(), 
                Sound.ENTITY_GENERIC_HURT, 0.3f, 1.2f);
        }
    }
    
    private void createStaggerReaction(LivingEntity damaged, boolean isLowHealth) {
        // Brief slowness for stagger effect
        int duration = isLowHealth ? STAGGER_DURATION / 2 : STAGGER_DURATION;
        damaged.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, false, false));
        
        // Subtle knockback
        Vector velocity = damaged.getVelocity();
        velocity.setY(0.1); // Small upward bump
        damaged.setVelocity(velocity);
    }
    
    private void enterLowHealthMode(LivingEntity damaged, ReactionData data) {
        data.hasEnteredLowHealthMode = true;
        data.lowHealthModeStart = System.currentTimeMillis();
        
        // Subtle effects for low health - no dramatic changes
        damaged.getWorld().spawnParticle(Particle.SMOKE, 
            damaged.getLocation().add(0, damaged.getHeight() / 2, 0), 
            5, 0.3, 0.3, 0.3, 0.02);
        
        // Slight speed increase for desperation
        damaged.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false));
        
        // Subtle sound cue
        damaged.getWorld().playSound(damaged.getLocation(), 
            Sound.ENTITY_VILLAGER_HURT, 0.4f, 0.8f);
    }
    
    private void processDeathReaction(LivingEntity entity, ReactionData data) {
        // Enhanced but subtle death effects
        createEnhancedDeathEffect(entity, data);
    }
    
    private void createEnhancedDeathEffect(LivingEntity entity, ReactionData data) {
        // Slightly enhanced death particles
        entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, 
            entity.getLocation().add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0.05);
        
        entity.getWorld().spawnParticle(Particle.ASH, 
            entity.getLocation().add(0, 0.5, 0), 6, 0.8, 0.5, 0.8, 0.02);
        
        // Subtle death sound enhancement
        entity.getWorld().playSound(entity.getLocation(), 
            Sound.ENTITY_GENERIC_DEATH, 0.6f, 1.0f);
        
        // For elites, add a tiny bit more flair (still subtle)
        if (entity.hasMetadata("elite") || entity.hasMetadata("custommob")) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= 10) { // 0.5 seconds
                        cancel();
                        return;
                    }
                    
                    entity.getWorld().spawnParticle(Particle.END_ROD, 
                        entity.getLocation().add(0, 0.5, 0), 1, 0.3, 0.2, 0.3, 0.01);
                    ticks++;
                }
            }.runTaskTimer(YakRealms.getInstance(), 5L, 2L);
        }
    }
    
    // ==================== HEALTH-BASED BEHAVIOR ====================
    
    private void updateHealthBasedBehavior(CustomMob mob, ReactionData data) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        double healthPercent = entity.getHealth() / entity.getMaxHealth();
        long currentTime = System.currentTimeMillis();
        
        // Low health behavior adjustments (very subtle)
        if (data.hasEnteredLowHealthMode && 
            currentTime - data.lowHealthModeStart > 5000 && // After 5 seconds
            currentTime - data.lastDesperation > 10000) { // Every 10 seconds
            
            if (healthPercent <= 0.15 && random.nextDouble() < 0.3) { // 15% health, 30% chance
                createDesperationBehavior(entity, data);
            }
        }
    }
    
    private void createDesperationBehavior(LivingEntity entity, ReactionData data) {
        data.lastDesperation = System.currentTimeMillis();
        
        // Very subtle desperation effects
        entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, 
            entity.getLocation().add(0, entity.getHeight() + 0.5, 0), 
            2, 0.3, 0.3, 0.3, 0.0);
        
        // Brief strength boost (subtle)
        entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, false, false));
        
        // Quiet growl sound
        entity.getWorld().playSound(entity.getLocation(), 
            Sound.ENTITY_WOLF_GROWL, 0.3f, 1.4f);
    }
    
    private void processOngoingReactions(CustomMob mob, ReactionData data, long currentTick) {
        // Process any ongoing reaction states
        // Currently minimal - could be expanded for specific reaction types
        
        // Clean up old state data
        long currentTime = System.currentTimeMillis();
        if (currentTime - data.lastHitReaction > 30000) { // 30 seconds
            // Reset some states if no recent combat
            data.hasEnteredLowHealthMode = false;
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    private boolean isCustomMob(LivingEntity entity) {
        return entity.hasMetadata("custommob") || entity.hasMetadata("type");
    }
    
    // ==================== DATA CLASSES ====================
    
    private static class ReactionData {
        public long lastHitReaction = 0;
        public long lastDesperation = 0;
        public long lowHealthModeStart = 0;
        public long lastHitTime = 0;
        public boolean hasEnteredLowHealthMode = false;
        public LivingEntity lastAttacker = null;
        
        public ReactionData() {}
    }
}