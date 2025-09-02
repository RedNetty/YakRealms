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
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Earthquake Stomp - A telegraphed area-of-effect ability with expanding shockwaves
 * 
 * Strategic Elements:
 * - Long telegraph allows players to position away from epicenter
 * - Expanding shockwave rings give clear visual indication
 * - Distance-based damage falloff rewards proper positioning
 * - Ground-based effect that can be avoided by movement
 * - Stunning effect punishes poor positioning but is avoidable
 */
public class EarthquakeStompAbility extends EliteAbility {
    
    private static final double MAX_STOMP_RADIUS = 12.0;
    private static final double BASE_DAMAGE = 20.0;
    private static final int STUN_DURATION = 40; // 2 seconds
    
    private Location stompCenter;
    private boolean hasStomped = false;
    
    public EarthquakeStompAbility() {
        super(
            "earthquake_stomp",
            "Earthquake Stomp",
            AbilityType.OFFENSIVE,
            EliteAbilityConfig.Brute.CHARGE_COOLDOWN + 100, // Slightly longer cooldown
            80, // 4 second telegraph - enough time to move
            0.18 // 18% base chance
        );
    }
    
    @Override
    protected boolean meetsPrerequisites(CustomMob mob, List<Player> targets) {
        if (targets.isEmpty()) return false;
        
        // Effective against groups
        return targets.size() >= 2;
    }
    
    @Override
    protected double applyContextualScaling(double baseChance, CustomMob mob, 
                                          List<Player> targets, CombatContext context) {
        double chance = baseChance;
        
        // More likely against grouped up players
        if (targets.size() >= 4) {
            chance *= 2.0;
        } else if (targets.size() >= 3) {
            chance *= 1.5;
        }
        
        // More effective in open terrain
        if (context.getTerrain() == CombatContext.TerrainType.OPEN) {
            chance *= 1.4;
        }
        
        // Less effective if players are spread out
        if (context.getAveragePlayerDistance() > 8.0) {
            chance *= 0.6;
        }
        
        return chance;
    }
    
    @Override
    public AbilityPriority getPriority(CustomMob mob, List<Player> targets, CombatContext context) {
        // High priority against grouped players
        if (targets.size() >= 4 && context.getAveragePlayerDistance() < 8.0) {
            return AbilityPriority.HIGH;
        }
        
        return AbilityPriority.NORMAL;
    }
    
    @Override
    protected void startTelegraph(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        stompCenter = entity.getLocation().clone();
        hasStomped = false;
        
        // Clear warning to all players
        targets.forEach(player -> {
            double distance = player.getLocation().distance(stompCenter);
            if (distance <= MAX_STOMP_RADIUS) {
                ActionBarUtil.addUniqueTemporaryMessage(player, 
                    "§c§l⚡ " + mob.getType().getTierSpecificName(mob.getTier()) + " §4§lPREPARING EARTHQUAKE STOMP! §e§lMOVE AWAY! ⚡", 
                    telegraphDuration);
                player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.7f);
            } else {
                ActionBarUtil.addUniqueTemporaryMessage(player, 
                    "§6§l⚠ EARTHQUAKE STOMP incoming! §7Stay back! ⚠", 
                    telegraphDuration);
            }
        });
        
        // Show telegraph effects
        createStompTelegraphEffects(mob, stompCenter);
        
        // Telegraph animation showing expanding danger zones
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = telegraphDuration;
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !entity.isValid() || !isCharging) {
                    cancel();
                    return;
                }
                
                // Show expanding danger rings
                if (ticks % 8 == 0) { // Every 0.4 seconds
                    showEarthquakeWarningRings(stompCenter);
                }
                
                // Intensifying ground rumble
                if (ticks % 10 == 0) { // Every 0.5 seconds
                    float pitch = 0.5f + ((float) ticks / maxTicks) * 0.8f;
                    entity.getWorld().playSound(stompCenter, Sound.ENTITY_RAVAGER_STEP, 0.9f, pitch);
                }
                
                // Final warning
                if (ticks == maxTicks - 20) { // 1 second before execution
                    targets.forEach(player -> {
                        if (player.getLocation().distance(stompCenter) <= MAX_STOMP_RADIUS) {
                            ActionBarUtil.addUniqueTemporaryMessage(player, 
                                "§4§l💀 EARTHQUAKE IMMINENT! MOVE NOW! 💀", 20);
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                        }
                    });
                }
                
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    @Override
    protected void executeAbility(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || stompCenter == null) return;
        
        hasStomped = true;
        
        // Massive impact sound and effect
        entity.getWorld().playSound(stompCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        entity.getWorld().playSound(stompCenter, Sound.ENTITY_RAVAGER_ATTACK, 1.2f, 0.8f);
        
        // Impact effect at epicenter
        stompCenter.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, stompCenter, 3, 1, 1, 1, 0.1);
        stompCenter.getWorld().spawnParticle(Particle.BLOCK, stompCenter.add(0, 0.1, 0), 40, 2, 0.5, 2, 0.1,
            stompCenter.getBlock().getBlockData());
        
        // Create expanding shockwave rings
        createExpandingShockwaves(mob, stompCenter, targets);
        
        // Apply earthquake effect to nearby players
        applyEarthquakeEffects(mob, targets, stompCenter);
    }
    
    private void createExpandingShockwaves(CustomMob mob, Location center, List<Player> targets) {
        new BukkitRunnable() {
            double currentRadius = 1.0;
            int wave = 0;
            final int totalWaves = 4;
            
            @Override
            public void run() {
                if (wave >= totalWaves || currentRadius > MAX_STOMP_RADIUS + 2) {
                    cancel();
                    return;
                }
                
                // Create shockwave ring
                createShockwaveRing(center, currentRadius);
                
                // Damage players caught in this wave
                targets.stream()
                    .filter(p -> {
                        double distance = p.getLocation().distance(center);
                        return distance >= currentRadius - 1.5 && distance <= currentRadius + 1.5;
                    })
                    .forEach(p -> hitPlayerWithShockwave(mob, p, center, currentRadius));
                
                currentRadius += 3.0; // Each wave expands by 3 blocks
                wave++;
                
                // Sound for each wave
                center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f + (wave * 0.2f));
            }
        }.runTaskTimer(YakRealms.getInstance(), 5L, 8L); // Waves every 0.4 seconds after initial delay
    }
    
    private void hitPlayerWithShockwave(CustomMob mob, Player player, Location center, double waveRadius) {
        double distance = player.getLocation().distance(center);
        
        // Distance-based damage falloff
        double damageMultiplier = Math.max(0.3, 1.0 - (distance / MAX_STOMP_RADIUS));
        double damage = getScaledDamage(BASE_DAMAGE, mob.getTier()) * damageMultiplier;
        
        player.damage(damage, mob.getEntity());
        
        // Stun effect for players too close
        if (distance <= MAX_STOMP_RADIUS * 0.6) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, STUN_DURATION, 4, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 1, false, false));
            
            ActionBarUtil.addUniqueTemporaryMessage(player, 
                "§4§l💥 STUNNED by Earthquake! 💥", 60);
        } else {
            ActionBarUtil.addUniqueTemporaryMessage(player, 
                "§6§l⚡ Hit by shockwave! ⚡", 40);
        }
        
        // Knockback effect - push away from center
        Vector knockback = player.getLocation().subtract(center).toVector().normalize();
        knockback.multiply(1.5).setY(0.3);
        player.setVelocity(knockback);
        
        // Impact sound for hit player
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
    }
    
    private void applyEarthquakeEffects(CustomMob mob, List<Player> targets, Location center) {
        // Ground cracking effect
        new BukkitRunnable() {
            int ticks = 0;
            final int duration = 100; // 5 seconds of aftereffects
            
            @Override
            public void run() {
                if (ticks >= duration) {
                    cancel();
                    return;
                }
                
                // Random ground crack effects
                if (ticks % 10 == 0) { // Every 0.5 seconds
                    for (int i = 0; i < 3; i++) {
                        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                        double distance = ThreadLocalRandom.current().nextDouble() * MAX_STOMP_RADIUS;
                        double x = center.getX() + distance * Math.cos(angle);
                        double z = center.getZ() + distance * Math.sin(angle);
                        Location crackLoc = new Location(center.getWorld(), x, center.getY(), z);
                        
                        crackLoc.getWorld().spawnParticle(Particle.BLOCK, crackLoc, 8, 0.5, 0.1, 0.5, 0.1,
                            crackLoc.getBlock().getBlockData());
                    }
                }
                
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 2L);
    }
    
    private void createStompTelegraphEffects(CustomMob mob, Location center) {
        // Dramatic buildup at mob location
        center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center.add(0, 1, 0), 25, 1.5, 1, 1.5, 0.1);
        center.getWorld().spawnParticle(Particle.CRIT, center.add(0, 1, 0), 20, 0.8, 0.8, 0.8, 0.1);
        center.getWorld().playSound(center, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.5f);
        center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
    }
    
    private void showEarthquakeWarningRings(Location center) {
        // Show multiple danger rings
        for (double radius = 3.0; radius <= MAX_STOMP_RADIUS; radius += 3.0) {
            createWarningRing(center, radius);
        }
    }
    
    private void createWarningRing(Location center, double radius) {
        for (int i = 0; i < 32; i++) {
            double angle = (2 * Math.PI * i) / 32;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            
            center.getWorld().spawnParticle(Particle.DUST, particleLoc, 
                2, 0.1, 0.1, 0.1, new Particle.DustOptions(org.bukkit.Color.ORANGE, 2.0f));
        }
    }
    
    private void createShockwaveRing(Location center, double radius) {
        for (int i = 0; i < 48; i++) {
            double angle = (2 * Math.PI * i) / 48;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.2, z);
            
            center.getWorld().spawnParticle(Particle.LARGE_SMOKE, particleLoc, 3, 0.3, 0.2, 0.3, 0.05);
            center.getWorld().spawnParticle(Particle.CRIT, particleLoc, 2, 0.2, 0.2, 0.2, 0.1);
        }
    }
    
    @Override
    public boolean canBeInterrupted() {
        return true; // Can be interrupted during telegraph
    }
    
    @Override
    public void onInterrupt(CustomMob mob, Player interrupter) {
        if (isCharging) {
            super.onInterrupt(mob, interrupter);
            
            // Reward the interrupt
            interrupter.sendMessage("§a§l✓ You interrupted the Earthquake Stomp!");
            ActionBarUtil.addUniqueTemporaryMessage(interrupter, "§a§l✓ STOMP INTERRUPTED! Excellent timing! ✓", 60);
            interrupter.playSound(interrupter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
            
            // Reset state
            stompCenter = null;
            hasStomped = false;
        }
    }
    
    @Override
    public String getDescription() {
        return "A devastating ground-based attack that creates expanding shockwaves. Can be avoided by moving away from the epicenter.";
    }
    
    @Override
    public String getTelegraphMessage() {
        return "is raising its fists for a massive earthquake stomp!";
    }
}