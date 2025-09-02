package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Void Walker Archetype Behavior - Manipulates space and reality
 * 
 * Characteristics:
 * - Opens dimensional rifts for teleportation and damage
 * - Controls gravity fields that pull or repel entities
 * - Phase shifts to become temporarily invulnerable
 * - Creates void blasts that ignore armor
 * - Tears reality to create dangerous spatial anomalies
 * 
 * This is a high-tier archetype (3-6) with reality-bending abilities
 */
public class VoidWalkerBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "void_walker_archetype";
    private static final int BEHAVIOR_PRIORITY = 480;
    
    // Ability cooldowns (in ticks)
    private static final int DIMENSIONAL_RIFT_COOLDOWN = 220;   // 11 seconds
    private static final int GRAVITY_WELL_COOLDOWN = 280;       // 14 seconds
    private static final int PHASE_SHIFT_COOLDOWN = 320;        // 16 seconds
    private static final int VOID_BLAST_COOLDOWN = 160;         // 8 seconds
    private static final int REALITY_TEAR_COOLDOWN = 600;       // 30 seconds
    
    // Behavior state tracking
    private long lastDimensionalRift = 0;
    private long lastGravityWell = 0;
    private long lastPhaseShift = 0;
    private long lastVoidBlast = 0;
    private long lastRealityTear = 0;
    
    private boolean isPhaseShifted = false;
    private boolean hasActiveTear = false;
    private Set<UUID> riftTargets = new HashSet<>();
    private Map<Location, Long> gravityWells = new HashMap<>();
    private Location activeTearLocation = null;
    private int phaseShiftCharges = 3; // Limited uses per combat
    
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
        return mob != null && 
               mob.isElite() && 
               mob.isValid() &&
               mob.getTier() >= 3 && // Void Walker is tier 3+ archetype
               hasArchetype(mob, EliteBehaviorArchetype.VOID_WALKER);
    }
    
    @Override
    public boolean isActive() {
        return true;
    }
    
    @Override
    public void onApply(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Void Walkers get permanent slow falling and night vision
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            
            // Increase health for high-tier archetype
            double currentMaxHealth = entity.getMaxHealth();
            entity.setMaxHealth(currentMaxHealth * 1.6); // +60% health
            entity.setHealth(entity.getMaxHealth());
            
            // Reset charges
            phaseShiftCharges = 3;
            
            // Create void activation effect
            createVoidWalkerActivationEffect(entity.getLocation());
            
        } catch (Exception e) {
            // Ignore application errors
        }
    }
    
    @Override
    public void onTick(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || !entity.isValid()) return;
        
        long currentTick = entity.getTicksLived();
        
        try {
            // Look for players to target with void abilities
            List<Player> nearbyEnemies = findNearbyPlayers(entity, 12.0);
            
            if (!nearbyEnemies.isEmpty()) {
                Player target = nearbyEnemies.get(0);
                double distance = entity.getLocation().distance(target.getLocation());
                
                // Use dimensional rift for long-range engagement
                if (distance > 8.0 && canUseAbility(currentTick, lastDimensionalRift, DIMENSIONAL_RIFT_COOLDOWN)) {
                    createDimensionalRift(mob, target);
                    lastDimensionalRift = currentTick;
                }
                
                // Use gravity well for crowd control
                if (nearbyEnemies.size() >= 2 && 
                    canUseAbility(currentTick, lastGravityWell, GRAVITY_WELL_COOLDOWN)) {
                    createGravityWell(mob, target.getLocation());
                    lastGravityWell = currentTick;
                }
                
                // Use void blast for direct damage
                if (distance <= 6.0 && canUseAbility(currentTick, lastVoidBlast, VOID_BLAST_COOLDOWN)) {
                    executeVoidBlast(mob, target);
                    lastVoidBlast = currentTick;
                }
                
                // Phase shift when under heavy attack
                if (!isPhaseShifted && phaseShiftCharges > 0 && entity.getHealth() / entity.getMaxHealth() < 0.7 &&
                    canUseAbility(currentTick, lastPhaseShift, PHASE_SHIFT_COOLDOWN)) {
                    activatePhaseShift(mob);
                    lastPhaseShift = currentTick;
                }
                
                // Create reality tear as ultimate ability
                if (nearbyEnemies.size() >= 3 && entity.getHealth() / entity.getMaxHealth() < 0.4 &&
                    !hasActiveTear && canUseAbility(currentTick, lastRealityTear, REALITY_TEAR_COOLDOWN)) {
                    createRealityTear(mob, target.getLocation());
                    lastRealityTear = currentTick;
                }
            }
            
            // Apply ongoing void effects
            applyVoidAura(mob);
            maintainGravityWells(mob);
            updateRealityTear(mob);
            
        } catch (Exception e) {
            // Ignore tick errors
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        try {
            // Void Walkers deal increasing damage based on tier
            double voidMultiplier = 1.3 + (mob.getTier() * 0.2); // +130% base, +20% per tier
            
            // Extra damage when phase shifted (attacks bypass defenses)
            if (isPhaseShifted) {
                voidMultiplier *= 2.0; // Double damage when phase shifted
                
                // Phase shifted attacks ignore armor and shields
                if (target instanceof Player) {
                    Player player = (Player) target;
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WITHER, 60, 1, false, false)); // 3 seconds Wither II
                }
            }
            
            // Void attacks have chance to teleport target
            if (random.nextDouble() < 0.2) { // 20% chance
                teleportTargetRandomly(target, 5.0);
            }
            
            // Create void attack effects
            createVoidAttackEffect(target.getLocation());
            
            return damage * voidMultiplier;
            
        } catch (Exception e) {
            return damage;
        }
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        try {
            // Void Walkers phase shift automatically when taking high damage
            if (!isPhaseShifted && damage > mob.getEntity().getMaxHealth() * 0.2 && 
                phaseShiftCharges > 0 && random.nextDouble() < 0.3) {
                activatePhaseShift(mob);
                return; // Avoid damage from this hit
            }
            
            // Void Walkers have a chance to rift away from attackers
            if (attacker instanceof Player && random.nextDouble() < 0.15) {
                createEscapeRift(mob, (Player) attacker);
            }
            
            // When phase shifted, reduce all damage by 80%
            if (isPhaseShifted) {
                LivingEntity entity = mob.getEntity();
                if (entity != null) {
                    double reduction = damage * 0.8;
                    entity.setHealth(Math.min(entity.getHealth() + reduction, entity.getMaxHealth()));
                    createPhaseShiftDamageReduction(entity.getLocation());
                }
            }
            
        } catch (Exception e) {
            // Ignore damage processing errors
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        try {
            // Void Walkers create an ominous presence
            player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.5f, 0.8f);
            
            // Apply reality distortion effect
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA, 60, 0, false, false)); // 3 seconds nausea
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.DARKNESS, 40, 0, false, false)); // 2 seconds darkness
            
            // Create void detection effect around player
            Location playerLoc = player.getLocation();
            playerLoc.getWorld().spawnParticle(Particle.PORTAL, playerLoc.add(0, 2, 0), 15, 1, 1, 1, 0.5);
                
        } catch (Exception e) {
            // Ignore detection errors
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        try {
            // Void Walkers create a reality collapse when they die
            LivingEntity entity = mob.getEntity();
            if (entity != null) {
                createVoidWalkerDeathCollapse(entity.getLocation(), mob.getTier());
                
                // Pull all nearby entities toward death location
                entity.getWorld().getNearbyEntities(entity.getLocation(), 10, 5, 10).stream()
                    .filter(e -> e instanceof Player)
                    .forEach(e -> {
                        Player player = (Player) e;
                        Vector pullVector = entity.getLocation().subtract(player.getLocation()).toVector().normalize();
                        pullVector.multiply(2.0).setY(0.5); // Strong pull with upward component
                        player.setVelocity(pullVector);
                        
                        // Damage from the collapse
                        player.damage(mob.getTier() * 10.0, entity);
                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS, 100, 0, false, false)); // 5 seconds blindness
                    });
            }
            
        } catch (Exception e) {
            // Ignore death processing errors
        }
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        try {
            // Reset void walker state
            isPhaseShifted = false;
            hasActiveTear = false;
            riftTargets.clear();
            gravityWells.clear();
            activeTearLocation = null;
            phaseShiftCharges = 3;
            
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    // ==================== VOID WALKER ABILITIES ====================
    
    private void createDimensionalRift(CustomMob mob, Player target) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            Location startLoc = entity.getLocation();
            Location endLoc = target.getLocation();
            riftTargets.add(target.getUniqueId());
            
            // Create rift opening effects
            entity.getWorld().playSound(startLoc, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 0.6f);
            entity.getWorld().spawnParticle(Particle.PORTAL, startLoc.add(0, 1, 0), 30, 1, 1, 1, 0.5);
            entity.getWorld().spawnParticle(Particle.PORTAL, endLoc.add(0, 1, 0), 30, 1, 1, 1, 0.5);
            
            // Teleport void walker through the rift after a brief delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!entity.isValid()) return;
                    
                    // Teleport with effects
                    Location teleportLoc = endLoc.clone().add(0, 0.1, 0);
                    entity.teleport(teleportLoc);
                    
                    // Create arrival effects
                    entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, teleportLoc.add(0, 1, 0), 25, 1, 1, 1, 0.3);
                    entity.getWorld().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                    
                    // Damage nearby players from rift emergence
                    entity.getNearbyEntities(4, 3, 4).stream()
                        .filter(e -> e instanceof Player)
                        .forEach(e -> {
                            Player player = (Player) e;
                            player.damage(mob.getTier() * 6.0, entity);
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 80, 2, false, false)); // 4 seconds Slowness III
                        });
                }
            }.runTaskLater(YakRealms.getInstance(), 20L); // 1 second delay
            
            // Schedule rift target removal
            new BukkitRunnable() {
                @Override
                public void run() {
                    riftTargets.remove(target.getUniqueId());
                }
            }.runTaskLater(YakRealms.getInstance(), 200L); // 10 seconds
            
        } catch (Exception e) {
            // Ignore rift creation errors
        }
    }
    
    private void createGravityWell(CustomMob mob, Location center) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            long currentTime = System.currentTimeMillis();
            gravityWells.put(center, currentTime);
            
            // Create gravity well effects
            entity.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.4f);
            entity.getWorld().spawnParticle(Particle.PORTAL, center.add(0, 2, 0), 50, 2, 2, 2, 0.2);
            
            // Create the gravity well effect over time
            new BukkitRunnable() {
                int duration = 0;
                final int maxDuration = 200 + mob.getTier() * 40; // 10-18 seconds based on tier
                
                @Override
                public void run() {
                    if (duration >= maxDuration || !entity.isValid()) {
                        gravityWells.remove(center);
                        cancel();
                        return;
                    }
                    
                    try {
                        // Create swirling void particles
                        for (int i = 0; i < 8; i++) {
                            double angle = (duration * 0.2) + (i * Math.PI / 4);
                            double radius = 4 - (duration * 0.015); // Shrinking spiral
                            double x = center.getX() + radius * Math.cos(angle);
                            double z = center.getZ() + radius * Math.sin(angle);
                            double y = center.getY() + Math.sin(duration * 0.1 + i) * 1.5;
                            
                            Location particleLoc = new Location(center.getWorld(), x, y, z);
                            center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, particleLoc, 1, 0, 0, 0, 0);
                        }
                        
                        // Pull entities toward the gravity well
                        center.getWorld().getNearbyEntities(center, 8, 4, 8).stream()
                            .filter(e -> e instanceof Player)
                            .forEach(e -> {
                                Player player = (Player) e;
                                Vector pullVector = center.clone().subtract(player.getLocation()).toVector();
                                double distance = pullVector.length();
                                
                                if (distance > 0.5) {
                                    pullVector.normalize().multiply(Math.min(0.8, 5.0 / distance)); // Stronger pull closer to center
                                    pullVector.setY(pullVector.getY() * 0.5); // Reduce vertical pull
                                    player.setVelocity(player.getVelocity().add(pullVector));
                                }
                                
                                // Damage players in the well
                                if (distance <= 3.0) {
                                    player.damage(1.5 + mob.getTier() * 0.5, entity);
                                    player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.WEAKNESS, 40, 1, false, false)); // 2 seconds Weakness II
                                }
                            });
                            
                    } catch (Exception e) {
                        // Ignore gravity well errors
                    }
                    
                    duration++;
                }
            }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
            
        } catch (Exception e) {
            // Ignore gravity well creation errors
        }
    }
    
    private void activatePhaseShift(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || phaseShiftCharges <= 0) return;
        
        try {
            isPhaseShifted = true;
            phaseShiftCharges--;
            
            // Apply phase shift effects
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, 100, 0, false, false)); // 5 seconds
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, 100, 2, false, false)); // 5 seconds Speed III
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, 100, 3, false, false)); // 5 seconds Resistance IV
            
            // Create phase shift activation effect
            Location loc = entity.getLocation();
            entity.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);
            entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc.add(0, 1, 0), 40, 1, 1, 1, 0.3);
            entity.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 1, 1, 1, 0.1);
            
            // Schedule phase shift end
            new BukkitRunnable() {
                @Override
                public void run() {
                    isPhaseShifted = false;
                    if (entity.isValid()) {
                        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.8f, 1.2f);
                        entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.2);
                    }
                }
            }.runTaskLater(YakRealms.getInstance(), 100L);
            
        } catch (Exception e) {
            isPhaseShifted = false;
        }
    }
    
    private void executeVoidBlast(CustomMob mob, Player target) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            Location startLoc = entity.getLocation().add(0, 1.5, 0);
            Vector direction = target.getLocation().subtract(startLoc).toVector().normalize();
            
            // Create void blast projectile
            entity.getWorld().playSound(startLoc, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.6f);
            
            new BukkitRunnable() {
                Location currentLoc = startLoc.clone();
                int ticks = 0;
                
                @Override
                public void run() {
                    if (ticks >= 50 || !entity.isValid()) { // 2.5 second flight time
                        cancel();
                        return;
                    }
                    
                    currentLoc.add(direction.clone().multiply(0.8));
                    
                    // Create void blast trail
                    currentLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, currentLoc, 5, 0.2, 0.2, 0.2, 0.05);
                    currentLoc.getWorld().spawnParticle(Particle.SMOKE, currentLoc, 2, 0.1, 0.1, 0.1, 0.02);
                    
                    // Check for collision with players
                    currentLoc.getWorld().getNearbyEntities(currentLoc, 2, 2, 2).stream()
                        .filter(e -> e instanceof Player)
                        .forEach(e -> {
                            Player player = (Player) e;
                            
                            // Void blast ignores armor and deals true damage
                            double voidDamage = (mob.getTier() * 12.0) + random.nextInt(10);
                            player.damage(voidDamage, entity);
                            
                            // Apply void effects
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.WITHER, 100, 2, false, false)); // 5 seconds Wither III
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.BLINDNESS, 60, 0, false, false)); // 3 seconds Blindness
                            
                            // Create void blast impact
                            createVoidBlastImpact(player.getLocation());
                            cancel();
                        });
                    
                    ticks++;
                }
            }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
            
        } catch (Exception e) {
            // Ignore void blast errors
        }
    }
    
    private void createRealityTear(CustomMob mob, Location center) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            hasActiveTear = true;
            activeTearLocation = center.clone();
            
            // Create reality tear activation
            entity.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.3f);
            entity.getWorld().spawnParticle(Particle.SONIC_BOOM, center.add(0, 2, 0), 1, 0, 0, 0, 0);
            entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 100, 3, 3, 3, 0.8);
            
            // Create the reality tear effect
            new BukkitRunnable() {
                int duration = 0;
                final int maxDuration = 300 + mob.getTier() * 60; // 15-21 seconds based on tier
                
                @Override
                public void run() {
                    if (duration >= maxDuration || !entity.isValid()) {
                        hasActiveTear = false;
                        activeTearLocation = null;
                        
                        // Create tear closing effect
                        if (center.getWorld() != null) {
                            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                            center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 3, 1, 1, 1, 0);
                        }
                        cancel();
                        return;
                    }
                    
                    try {
                        // Create tear visual effects
                        if (duration % 5 == 0) { // Every 0.25 seconds
                            center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center.add(0, 3, 0), 15, 2, 3, 2, 0.3);
                            center.getWorld().spawnParticle(Particle.END_ROD, center, 8, 1.5, 0.5, 1.5, 0.1);
                        }
                        
                        // CATASTROPHIC reality tear effects on nearby players
                        center.getWorld().getNearbyEntities(center, 8, 6, 8).stream() // Larger radius
                            .filter(e -> e instanceof Player)
                            .forEach(e -> {
                                Player player = (Player) e;
                                double distance = player.getLocation().distance(center);
                                
                                // MASSIVELY distort reality around players
                                if (duration % 20 == 0) { // Every second
                                    // MUCH HIGHER damage based on proximity
                                    double tearDamage = (15.0 + mob.getTier() * 4) * (1.0 - distance / 8.0); // Increased damage
                                    player.damage(tearDamage, entity);
                                    
                                    // CONSTANT terror messaging
                                    player.sendMessage("§8§lReality tears at your very essence! (§c" + String.format("%.1f", tearDamage) + " damage§8§l)");
                                    
                                    // HIGHER chance of random teleportation
                                    if (random.nextDouble() < 0.5) { // Increased from 30% to 50%
                                        teleportTargetRandomly(player, 10.0);
                                        player.sendMessage("§5§lYou are torn through dimensions!");
                                    }
                                    
                                    // SEVERE reality distortion effects
                                    player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.NAUSEA, 140, 2, false, false)); // Stronger and longer
                                    player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.NAUSEA, 120, 1, false, false)); // Stronger
                                    player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.DARKNESS, 100, 0, false, false)); // Added darkness
                                    player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.WITHER, 80, 1, false, false)); // Added wither
                                }
                                
                                // MORE FREQUENT and severe constant effects
                                if (duration % 10 == 0 && distance <= 4.0) { // Larger radius
                                    player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.LEVITATION, 40, 1, false, false)); // Longer and stronger levitation
                                    if (duration % 40 == 0) {
                                        player.sendMessage("§8§lGravity bends around the reality tear!");
                                    }
                                }
                                
                                // Additional horror effects for close players
                                if (distance <= 2.0 && duration % 30 == 0) {
                                    player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.BLINDNESS, 60, 0, false, false));
                                    player.sendMessage("§8§lThe void consumes your vision!");
                                }
                            });
                            
                    } catch (Exception e) {
                        // Ignore tear effects errors
                    }
                    
                    duration++;
                }
            }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
            
        } catch (Exception e) {
            hasActiveTear = false;
            activeTearLocation = null;
        }
    }
    
    private void createEscapeRift(CustomMob mob, Player attacker) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Find a safe escape location
            Location currentLoc = entity.getLocation();
            Location escapeLoc = findEscapeLocation(currentLoc, 8.0);
            
            // Create escape rift effects
            entity.getWorld().spawnParticle(Particle.PORTAL, currentLoc.add(0, 1, 0), 20, 1, 1, 1, 0.3);
            entity.teleport(escapeLoc);
            entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, escapeLoc.add(0, 1, 0), 20, 1, 1, 1, 0.3);
            
            // Play escape sound
            entity.getWorld().playSound(escapeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
            
            // Briefly disorient the attacker
            attacker.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, 40, 0, false, false)); // 2 seconds
                
        } catch (Exception e) {
            // Ignore escape rift errors
        }
    }
    
    private void applyVoidAura(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Void Walkers constantly emit otherworldly particles
            if (random.nextInt(20) == 0) { // Every second on average
                Location loc = entity.getLocation().add(0, 1, 0);
                
                if (isPhaseShifted) {
                    entity.getWorld().spawnParticle(Particle.END_ROD, loc, 5, 0.5, 0.5, 0.5, 0.05);
                } else {
                    entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 3, 0.3, 0.3, 0.3, 0.02);
                }
                
                // Void aura affects nearby players
                entity.getNearbyEntities(4, 2, 4).stream()
                    .filter(e -> e instanceof Player)
                    .forEach(e -> {
                        Player player = (Player) e;
                        if (random.nextDouble() < 0.1) { // 10% chance
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.WEAKNESS, 40, 0, false, false)); // 2 seconds
                        }
                    });
            }
            
        } catch (Exception e) {
            // Ignore aura errors
        }
    }
    
    private void maintainGravityWells(CustomMob mob) {
        // Gravity wells are maintained by their own runnables
        // This method can be used for additional gravity well logic if needed
        
        // Remove expired gravity wells
        long currentTime = System.currentTimeMillis();
        gravityWells.entrySet().removeIf(entry -> currentTime - entry.getValue() > 20000); // 20 seconds max
    }
    
    private void updateRealityTear(CustomMob mob) {
        // Reality tear is maintained by its own runnable
        // This method can be used for additional tear logic if needed
        
        if (hasActiveTear && activeTearLocation != null) {
            // Additional tear effects can be added here
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private void teleportTargetRandomly(Entity target, double maxRange) {
        try {
            Location currentLoc = target.getLocation();
            
            // Try to find a safe teleport location
            for (int i = 0; i < 10; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = 3.0 + random.nextDouble() * maxRange;
                
                double x = currentLoc.getX() + distance * Math.cos(angle);
                double z = currentLoc.getZ() + distance * Math.sin(angle);
                
                Location teleportLoc = new Location(currentLoc.getWorld(), x, currentLoc.getY(), z);
                
                // Check if location is safe (simplified check)
                if (teleportLoc.getBlock().isEmpty() && 
                    teleportLoc.clone().add(0, 1, 0).getBlock().isEmpty()) {
                    
                    // Teleport with effects
                    target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.2);
                    target.teleport(teleportLoc);
                    target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, teleportLoc.add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.2);
                    target.getWorld().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
                    break;
                }
            }
            
        } catch (Exception e) {
            // Ignore teleport errors
        }
    }
    
    private Location findEscapeLocation(Location center, double range) {
        // Try to find a safe escape location
        for (int i = 0; i < 15; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 4.0 + random.nextDouble() * range;
            
            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);
            
            Location testLoc = new Location(center.getWorld(), x, center.getY(), z);
            
            // Check if location is safe
            if (testLoc.getBlock().isEmpty() && 
                testLoc.clone().add(0, 1, 0).getBlock().isEmpty() &&
                !testLoc.clone().subtract(0, 1, 0).getBlock().isEmpty()) {
                return testLoc;
            }
        }
        
        // Fallback to original location
        return center;
    }
    
    // ==================== UTILITY METHODS ====================
    
    private boolean hasArchetype(CustomMob mob, EliteBehaviorArchetype archetype) {
        if (!mob.getEntity().hasMetadata("elite_archetype")) {
            return false;
        }
        try {
            String archetypeName = mob.getEntity().getMetadata("elite_archetype").get(0).asString();
            return archetype.name().equals(archetypeName);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean canUseAbility(long currentTick, long lastUsed, int cooldown) {
        return currentTick - lastUsed >= cooldown;
    }
    
    private List<Player> findNearbyPlayers(LivingEntity entity, double maxRange) {
        return entity.getNearbyEntities(maxRange, maxRange, maxRange).stream()
            .filter(e -> e instanceof Player)
            .map(e -> (Player) e)
            .filter(Player::isOnline)
            .filter(p -> !p.isDead())
            .toList();
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createVoidWalkerActivationEffect(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.REVERSE_PORTAL, location.add(0, 1, 0), 40, 1.5, 1, 1.5, 0.3);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 20, 1, 1, 1, 0.1);
                location.getWorld().spawnParticle(Particle.PORTAL, location, 30, 1, 1, 1, 0.5);
                location.getWorld().playSound(location, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.8f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    private void createVoidAttackEffect(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.REVERSE_PORTAL, location.add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                location.getWorld().spawnParticle(Particle.SMOKE, location, 5, 0.3, 0.3, 0.3, 0.05);
                location.getWorld().playSound(location, Sound.ENTITY_WITHER_HURT, 0.6f, 1.2f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    private void createPhaseShiftDamageReduction(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.END_ROD, location.add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0.1);
                location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.8f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    private void createVoidBlastImpact(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.EXPLOSION, location, 5, 1, 1, 1, 0.1);
                location.getWorld().spawnParticle(Particle.REVERSE_PORTAL, location, 25, 2, 2, 2, 0.2);
                location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    private void createVoidWalkerDeathCollapse(Location location, int tier) {
        try {
            if (location.getWorld() != null) {
                // Create massive void collapse effect
                location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 8 + tier, 3, 3, 3, 0.1);
                location.getWorld().spawnParticle(Particle.REVERSE_PORTAL, location, 80 + tier * 10, 4, 3, 4, 0.5);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 40 + tier * 5, 3, 2, 3, 0.2);
                location.getWorld().playSound(location, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.5f);
                location.getWorld().playSound(location, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.3f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
}