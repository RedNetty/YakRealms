package com.rednetty.server.core.mechanics.world.mobs.abilities.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.abilities.CombatContext;
import com.rednetty.server.core.mechanics.world.mobs.abilities.EliteAbility;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.config.EliteAbilityConfig;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Void Pulse - A telegraphed expanding void wave ability 
 * 
 * Strategic Elements:
 * - Multiple expanding void rings that players must time their movement to avoid
 * - Clear visual telegraph showing when and where each wave will expand
 * - Rewards precise timing and positioning
 * - Punishes poor movement but allows skillful dodging
 * - Creates temporary void zones that persist after the ability
 */
public class VoidPulseAbility extends EliteAbility {
    
    private static final double MAX_PULSE_RADIUS = 15.0;
    private static final double PULSE_DAMAGE = 18.0;
    private static final int VOID_DURATION = 100; // 5 seconds
    private static final int NUM_PULSES = 4;
    
    private Location pulseCenter;
    private List<Double> pulseRadii = new ArrayList<>();
    private boolean pulsesStarted = false;
    
    public VoidPulseAbility() {
        super(
            "void_pulse",
            "Void Pulse",
            AbilityType.ULTIMATE,
            300, // 15 seconds cooldown
            90, // 4.5 second telegraph - time to understand the pattern
            0.14 // 14% base chance
        );
    }
    
    @Override
    protected boolean meetsPrerequisites(CustomMob mob, List<Player> targets) {
        if (targets.isEmpty()) return false;
        
        // Effective against multiple targets
        return targets.size() >= 2;
    }
    
    @Override
    protected double applyContextualScaling(double baseChance, CustomMob mob, 
                                          List<Player> targets, CombatContext context) {
        double chance = baseChance;
        
        // More likely when facing multiple enemies
        if (targets.size() >= 4) {
            chance *= 2.2;
        } else if (targets.size() >= 3) {
            chance *= 1.7;
        }
        
        // More likely when void walker is at low health
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        if (healthPercent <= 0.3) {
            chance *= 2.0; // Desperate void magic
        }
        
        // More effective in open areas
        if (context.getTerrain() == CombatContext.TerrainType.OPEN) {
            chance *= 1.4;
        }
        
        return chance;
    }
    
    @Override
    public AbilityPriority getPriority(CustomMob mob, List<Player> targets, CombatContext context) {
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        
        // Critical priority when very low health with multiple targets
        if (healthPercent <= 0.25 && targets.size() >= 3) {
            return AbilityPriority.CRITICAL;
        }
        
        // High priority for crowd control
        if (targets.size() >= 4) {
            return AbilityPriority.HIGH;
        }
        
        return AbilityPriority.NORMAL;
    }
    
    @Override
    protected void startTelegraph(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        pulseCenter = entity.getLocation().clone();
        pulsesStarted = false;
        
        // Calculate pulse radii for predictable pattern
        pulseRadii.clear();
        for (int i = 0; i < NUM_PULSES; i++) {
            pulseRadii.add(3.0 + (i * 3.5)); // Evenly spaced rings
        }
        
        // Clear warning to all players
        targets.forEach(player -> {
            ActionBarUtil.addUniqueTemporaryMessage(player, 
                "§5§l⚡ " + mob.getType().getTierSpecificName(mob.getTier()) + " §d§lCHARGING VOID PULSE! §e§lTIME YOUR MOVEMENT! ⚡", 
                telegraphDuration);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 1.2f);
        });
        
        // Show telegraph effects
        createVoidTelegraphEffects(mob, pulseCenter);
        
        // Telegraph animation showing the pulse pattern
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = telegraphDuration;
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !entity.isValid() || !isCharging) {
                    cancel();
                    return;
                }
                
                // Show pulse preview rings
                if (ticks % 10 == 0) { // Every 0.5 seconds
                    showPulsePreview(pulseCenter);
                }
                
                // Intensifying void energy
                if (ticks % 8 == 0) { // Every 0.4 seconds
                    showVoidCharging(pulseCenter);
                }
                
                // Sound effects building up
                if (ticks % 15 == 0) { // Every 0.75 seconds
                    float pitch = 0.8f + ((float) ticks / maxTicks) * 0.8f;
                    entity.getWorld().playSound(pulseCenter, Sound.ENTITY_WITHER_SHOOT, 0.8f, pitch);
                }
                
                // Final warning with timing hint
                if (ticks == maxTicks - 30) { // 1.5 seconds before execution
                    targets.forEach(player -> {
                        ActionBarUtil.addUniqueTemporaryMessage(player, 
                            "§5§l💀 VOID PULSE STARTING! Watch the pattern! 💀", 30);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
                    });
                }
                
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    @Override
    protected void executeAbility(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || pulseCenter == null) return;
        
        pulsesStarted = true;
        
        // Initial void convergence effect
        pulseCenter.getWorld().playSound(pulseCenter, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
        pulseCenter.getWorld().spawnParticle(Particle.PORTAL, pulseCenter.add(0, 2, 0), 50, 2, 2, 2, 1.0);
        
        // Execute the pulse sequence with timing
        executePulseSequence(mob, targets);
    }
    
    private void executePulseSequence(CustomMob mob, List<Player> targets) {
        new BukkitRunnable() {
            int currentPulse = 0;
            int ticks = 0;
            final int pulseTiming = 25; // 1.25 seconds between pulses
            
            @Override
            public void run() {
                if (currentPulse >= NUM_PULSES || !mob.getEntity().isValid()) {
                    // Create lingering void zones after all pulses
                    createLingeringVoidZones(mob, targets);
                    cancel();
                    return;
                }
                
                if (ticks % pulseTiming == 0) {
                    executeSinglePulse(mob, targets, pulseRadii.get(currentPulse), currentPulse);
                    currentPulse++;
                }
                
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    private void executeSinglePulse(CustomMob mob, List<Player> targets, double radius, int pulseNumber) {
        // Pulse effects
        pulseCenter.getWorld().playSound(pulseCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f + (pulseNumber * 0.3f));
        pulseCenter.getWorld().playSound(pulseCenter, Sound.ENTITY_WITHER_SHOOT, 0.9f, 1.0f + (pulseNumber * 0.2f));
        
        // Create expanding void ring
        createVoidRing(pulseCenter, radius);
        
        // Check for players caught in this pulse
        targets.stream()
            .filter(p -> {
                double distance = p.getLocation().distance(pulseCenter);
                return distance >= radius - 1.0 && distance <= radius + 1.0;
            })
            .forEach(p -> hitPlayerWithVoidPulse(mob, p, pulseNumber));
        
        // Warning for next pulse
        if (pulseNumber < NUM_PULSES - 1) {
            double nextRadius = pulseRadii.get(pulseNumber + 1);
            targets.forEach(player -> {
                double distance = player.getLocation().distance(pulseCenter);
                if (distance >= nextRadius - 2.0 && distance <= nextRadius + 2.0) {
                    ActionBarUtil.addUniqueTemporaryMessage(player, 
                        "§5§l⚠ Next void pulse coming! ⚠", 20);
                }
            });
        }
    }
    
    private void hitPlayerWithVoidPulse(CustomMob mob, Player player, int pulseNumber) {
        double damage = getScaledDamage(PULSE_DAMAGE, mob.getTier());
        
        // Increasing damage for later pulses (reward survival)
        damage *= (1.0 + (pulseNumber * 0.3));
        
        player.damage(damage, mob.getEntity());
        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false));
        
        // Void hit effects
        createVoidHitEffect(player.getLocation());
        
        // Feedback to hit player
        ActionBarUtil.addUniqueTemporaryMessage(player, 
            "§5§l💥 HIT by Void Pulse " + (pulseNumber + 1) + "! 💥", 60);
        
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 1.5f);
        
        // Push away from center
        Vector knockback = player.getLocation().subtract(pulseCenter).toVector().normalize();
        knockback.multiply(0.8 + (pulseNumber * 0.2)).setY(0.3);
        player.setVelocity(knockback);
    }
    
    private void createLingeringVoidZones(CustomMob mob, List<Player> targets) {
        // Create persistent void hazards at each pulse radius
        for (double radius : pulseRadii) {
            createPersistentVoidZone(pulseCenter, radius, mob, targets);
        }
    }
    
    private void createPersistentVoidZone(Location center, double radius, CustomMob mob, List<Player> targets) {
        new BukkitRunnable() {
            int ticks = 0;
            final int duration = VOID_DURATION;
            
            @Override
            public void run() {
                if (ticks >= duration) {
                    // Dissipation effect
                    center.getWorld().playSound(center, Sound.ENTITY_WITHER_DEATH, 0.5f, 1.8f);
                    cancel();
                    return;
                }
                
                // Void zone effects every 0.5 seconds
                if (ticks % 10 == 0) {
                    showVoidZoneEffect(center, radius);
                    
                    // Damage players standing in void zones
                    targets.stream()
                        .filter(p -> {
                            double distance = p.getLocation().distance(center);
                            return distance >= radius - 1.5 && distance <= radius + 1.5;
                        })
                        .forEach(p -> {
                            p.damage(getScaledDamage(3.0, mob.getTier()), mob.getEntity());
                            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 20, 0, false, false));
                            
                            ActionBarUtil.addUniqueTemporaryMessage(p, 
                                "§5§l⚡ Standing in void zone! ⚡", 15);
                        });
                }
                
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 1L); // Start after 1 second delay
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createVoidTelegraphEffects(CustomMob mob, Location center) {
        // Dramatic void energy buildup
        center.getWorld().spawnParticle(Particle.PORTAL, center.add(0, 2, 0), 40, 2, 2, 2, 0.8);
        center.getWorld().spawnParticle(Particle.SMOKE, center.add(0, 1, 0), 30, 1.5, 1, 1.5, 0.1);
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_AMBIENT, 1.2f, 0.8f);
        center.getWorld().playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.6f);
    }
    
    private void showPulsePreview(Location center) {
        // Show all pulse radii as preview rings
        for (int i = 0; i < pulseRadii.size(); i++) {
            double radius = pulseRadii.get(i);
            int intensity = pulseRadii.size() - i; // Inner rings more intense
            
            for (int j = 0; j < 24; j++) {
                double angle = (2 * Math.PI * j) / 24;
                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
                Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
                
                center.getWorld().spawnParticle(Particle.DUST, particleLoc, 
                    intensity, 0.1, 0.1, 0.1, new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.5f));
            }
        }
    }
    
    private void showVoidCharging(Location center) {
        center.getWorld().spawnParticle(Particle.PORTAL, center.add(0, 1, 0), 12, 1, 1, 1, 0.4);
        center.getWorld().spawnParticle(Particle.SMOKE, center.add(0, 1, 0), 8, 0.8, 1, 0.8, 0.05);
    }
    
    private void createVoidRing(Location center, double radius) {
        // Create expanding void ring with multiple particle types
        for (int i = 0; i < 64; i++) {
            double angle = (2 * Math.PI * i) / 64;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.3, z);
            
            center.getWorld().spawnParticle(Particle.PORTAL, particleLoc, 5, 0.3, 0.5, 0.3, 0.2);
            center.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 3, 0.2, 0.3, 0.2, 0.05);
        }
    }
    
    private void createVoidHitEffect(Location location) {
        location.getWorld().spawnParticle(Particle.PORTAL, location.add(0, 1, 0), 15, 0.8, 0.8, 0.8, 0.3);
        location.getWorld().spawnParticle(Particle.SMOKE, location.add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, location.add(0, 1, 0), 8, 0.6, 0.6, 0.6, 0.1);
    }
    
    private void showVoidZoneEffect(Location center, double radius) {
        // Subtle ongoing void zone effects
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI * i) / 16;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            
            if (ThreadLocalRandom.current().nextDouble() < 0.3) { // 30% chance per particle
                center.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.1, 0.2, 0.1, 0.02);
            }
        }
    }
    
    @Override
    public boolean canBeInterrupted() {
        return !pulsesStarted; // Can only be interrupted before pulses start
    }
    
    @Override
    public void onInterrupt(CustomMob mob, Player interrupter) {
        if (canBeInterrupted()) {
            super.onInterrupt(mob, interrupter);
            
            // Reward the interrupt
            interrupter.sendMessage("§a§l✓ You interrupted the Void Pulse!");
            ActionBarUtil.addUniqueTemporaryMessage(interrupter, "§a§l✓ VOID PULSE STOPPED! Amazing! ✓", 60);
            interrupter.playSound(interrupter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            
            // Reset state
            pulseCenter = null;
            pulseRadii.clear();
            pulsesStarted = false;
        }
    }
    
    @Override
    public String getDescription() {
        return "A devastating void ability that creates multiple expanding pulse rings. Players must time their movement to avoid each wave.";
    }
    
    @Override
    public String getTelegraphMessage() {
        return "is gathering void energy for a massive pulse attack!";
    }
}