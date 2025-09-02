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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Devastating Charge - A telegraphed high-damage ability with clear counterplay
 * 
 * Strategic Elements:
 * - Long telegraph gives players time to react and position
 * - High damage rewards successful hits but punishes poor positioning
 * - Clear audio/visual warnings for professional feel
 * - Can be avoided with proper movement and timing
 * - Devastating if it connects but completely avoidable if played well
 */
public class DevastatingChargeAbility extends EliteAbility {
    
    private static final double CHARGE_DAMAGE_BASE = 25.0;
    private static final double CHARGE_SPEED = 2.0; // blocks per tick during charge
    private static final double CHARGE_RANGE = 20.0;
    private static final int STUN_DURATION = 60; // 3 seconds
    
    private Player chargeTarget;
    private Location chargeStartLocation;
    private Location chargeEndLocation;
    private boolean hasHit = false;
    
    public DevastatingChargeAbility() {
        super(
            "devastating_charge",
            "Devastating Charge",
            AbilityType.OFFENSIVE,
            EliteAbilityConfig.Brute.CHARGE_COOLDOWN,
            60, // 3 second telegraph - plenty of time to react
            0.15 // 15% base chance
        );
    }
    
    @Override
    protected boolean meetsPrerequisites(CustomMob mob, List<Player> targets) {
        if (targets.isEmpty()) return false;
        
        // Need a target within charge range but not too close
        Player bestTarget = findBestChargeTarget(mob, targets);
        if (bestTarget == null) return false;
        
        double distance = mob.getEntity().getLocation().distance(bestTarget.getLocation());
        return distance >= 5.0 && distance <= CHARGE_RANGE;
    }
    
    @Override
    protected double applyContextualScaling(double baseChance, CustomMob mob, 
                                          List<Player> targets, CombatContext context) {
        double chance = baseChance;
        
        // More likely if there's an isolated target
        if (!context.getIsolatedPlayers().isEmpty()) {
            chance *= 1.5;
        }
        
        // More likely on open terrain
        if (context.getTerrain() == CombatContext.TerrainType.OPEN) {
            chance *= 1.3;
        }
        
        // Less likely if players are very close (charge needs distance)
        if (context.getAveragePlayerDistance() < 6.0) {
            chance *= 0.7;
        }
        
        return chance;
    }
    
    @Override
    public AbilityPriority getPriority(CustomMob mob, List<Player> targets, CombatContext context) {
        // High priority if there's a good isolated target
        if (!context.getIsolatedPlayers().isEmpty() && 
            context.getTerrain() == CombatContext.TerrainType.OPEN) {
            return AbilityPriority.HIGH;
        }
        
        // Normal priority otherwise
        return AbilityPriority.NORMAL;
    }
    
    @Override
    protected void startTelegraph(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        chargeTarget = findBestChargeTarget(mob, targets);
        
        if (chargeTarget == null) return;
        
        chargeStartLocation = entity.getLocation().clone();
        chargeEndLocation = calculateChargeEndpoint(chargeStartLocation, chargeTarget.getLocation());
        hasHit = false;
        
        // CLEAR WARNING TO ALL PLAYERS
        String targetName = chargeTarget.getName();
        targets.forEach(player -> {
            if (player == chargeTarget) {
                ActionBarUtil.addUniqueTemporaryMessage(player, 
                    "§c§l⚡ " + mob.getType().getTierSpecificName(mob.getTier()) + " §4§lTARGETING YOU §c§lfor DEVASTATING CHARGE! §e§lMOVE NOW! ⚡", 
                    telegraphDuration);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            } else {
                ActionBarUtil.addUniqueTemporaryMessage(player, 
                    "§c§l⚡ " + mob.getType().getTierSpecificName(mob.getTier()) + " §6charging at §e" + targetName + "§6! §7Stay clear! ⚡", 
                    telegraphDuration);
            }
        });
        
        // Dramatic charge preparation effects
        createChargeTelegraphEffects(mob, chargeStartLocation, chargeEndLocation);
        
        // Telegraph animation - show the charge path
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = telegraphDuration;
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !entity.isValid() || !isCharging) {
                    cancel();
                    return;
                }
                
                // Show charge path to players
                if (ticks % 5 == 0) { // Every 0.25 seconds
                    showChargePath(chargeStartLocation, chargeEndLocation);
                }
                
                // Intensifying sound effects as charge approaches
                if (ticks % 15 == 0) { // Every 0.75 seconds
                    float pitch = 0.5f + ((float) ticks / maxTicks) * 1.0f;
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, pitch);
                }
                
                // Final warning
                if (ticks == maxTicks - 20) { // 1 second before execution
                    targets.forEach(player -> {
                        if (player == chargeTarget) {
                            ActionBarUtil.addUniqueTemporaryMessage(player, 
                                "§4§l💀 CHARGE INCOMING! DODGE NOW! 💀", 20);
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
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
        
        if (chargeTarget == null || chargeStartLocation == null || chargeEndLocation == null) {
            return;
        }
        
        // Execute the charge with dramatic effects
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.0f, 0.8f);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.2f);
        
        // Animate the charge movement
        new BukkitRunnable() {
            final Vector direction = chargeEndLocation.subtract(chargeStartLocation).toVector().normalize();
            final double totalDistance = chargeStartLocation.distance(chargeEndLocation);
            double currentDistance = 0;
            boolean hitWall = false;
            
            @Override
            public void run() {
                if (!entity.isValid() || currentDistance >= totalDistance || hitWall) {
                    // Charge complete - create impact effect
                    createChargeEndEffect(entity.getLocation());
                    cancel();
                    return;
                }
                
                Location newLocation = entity.getLocation().add(direction.clone().multiply(CHARGE_SPEED));
                
                // Check for wall collision
                if (!newLocation.getBlock().getType().isAir() || 
                    !newLocation.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                    hitWall = true;
                    createWallImpactEffect(entity.getLocation());
                    return;
                }
                
                entity.teleport(newLocation);
                currentDistance += CHARGE_SPEED;
                
                // Create charge trail effects
                entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, entity.getLocation(), 5, 0.3, 0.3, 0.3, 0.1);
                entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0.1);
                
                // Check for player hits during charge
                entity.getNearbyEntities(2.0, 2.0, 2.0).stream()
                    .filter(e -> e instanceof Player)
                    .map(e -> (Player) e)
                    .forEach(player -> {
                        if (!hasHit) { // Only hit the first player encountered
                            hitPlayer(mob, player);
                            hasHit = true;
                        }
                    });
                
                // Play charge sound
                if (currentDistance % 5 == 0) {
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_HORSE_GALLOP, 0.8f, 1.2f);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    private void hitPlayer(CustomMob mob, Player player) {
        double damage = getScaledDamage(CHARGE_DAMAGE_BASE, mob.getTier());
        
        // Devastating impact
        player.damage(damage, mob.getEntity());
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, STUN_DURATION, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 1, false, false));
        
        // Dramatic hit effects
        createChargeHitEffect(player.getLocation());
        
        // Clear feedback to the hit player
        ActionBarUtil.addUniqueTemporaryMessage(player, 
            "§4§l💥 DEVASTATING CHARGE HIT! You are stunned! 💥", 60);
        
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f);
        
        // Knockback effect
        Vector knockback = player.getLocation().subtract(mob.getEntity().getLocation()).toVector().normalize();
        knockback.multiply(2.0).setY(0.5);
        player.setVelocity(knockback);
    }
    
    private Player findBestChargeTarget(CustomMob mob, List<Player> targets) {
        Location mobLoc = mob.getEntity().getLocation();
        
        return targets.stream()
            .filter(p -> {
                double distance = mobLoc.distance(p.getLocation());
                return distance >= 5.0 && distance <= CHARGE_RANGE;
            })
            .filter(p -> hasLineSight(mobLoc, p.getLocation()))
            .min((p1, p2) -> {
                // Prefer isolated targets
                boolean p1Isolated = isPlayerIsolated(p1, targets);
                boolean p2Isolated = isPlayerIsolated(p2, targets);
                
                if (p1Isolated && !p2Isolated) return -1;
                if (!p1Isolated && p2Isolated) return 1;
                
                // Then prefer closer targets
                double d1 = mobLoc.distance(p1.getLocation());
                double d2 = mobLoc.distance(p2.getLocation());
                return Double.compare(d1, d2);
            })
            .orElse(null);
    }
    
    private Location calculateChargeEndpoint(Location start, Location targetLoc) {
        Vector direction = targetLoc.subtract(start).toVector().normalize();
        return start.clone().add(direction.multiply(CHARGE_RANGE));
    }
    
    private boolean hasLineSight(Location from, Location to) {
        // Simplified line-of-sight check
        Vector direction = to.subtract(from).toVector().normalize();
        double distance = from.distance(to);
        
        for (double d = 1.0; d < distance; d += 1.0) {
            Location checkLoc = from.clone().add(direction.clone().multiply(d));
            if (!checkLoc.getBlock().getType().isAir()) {
                return false;
            }
        }
        return true;
    }
    
    private boolean isPlayerIsolated(Player player, List<Player> allPlayers) {
        return allPlayers.stream()
            .filter(p -> p != player)
            .noneMatch(p -> p.getLocation().distance(player.getLocation()) <= 5.0);
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createChargeTelegraphEffects(CustomMob mob, Location start, Location end) {
        // Dramatic preparation effect at mob location
        start.getWorld().spawnParticle(Particle.LARGE_SMOKE, start.clone().add(0, 1, 0), 20, 1, 1, 1, 0.1);
        start.getWorld().spawnParticle(Particle.CRIT, start.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
        start.getWorld().playSound(start, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.6f);
        start.getWorld().playSound(start, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.8f);
    }
    
    private void showChargePath(Location start, Location end) {
        Vector direction = end.subtract(start).toVector().normalize();
        double distance = start.distance(end);
        
        for (double d = 2.0; d < distance; d += 2.0) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));
            particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc.add(0, 0.1, 0), 
                3, 0.2, 0.1, 0.2, new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
        }
    }
    
    private void createChargeHitEffect(Location location) {
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 3, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, location.add(0, 1, 0), 15, 0.8, 0.8, 0.8, 0.1);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 10, 0.5, 0.5, 0.5, 0.1);
    }
    
    private void createChargeEndEffect(Location location) {
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 15, 1, 1, 1, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_HORSE_LAND, 1.0f, 0.8f);
    }
    
    private void createWallImpactEffect(Location location) {
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 2, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.BLOCK, location, 20, 1, 1, 1, 0.1, 
            location.getBlock().getBlockData());
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f);
    }
    
    @Override
    public boolean canBeInterrupted() {
        return true; // Can be interrupted during telegraph phase
    }
    
    @Override
    public void onInterrupt(CustomMob mob, Player interrupter) {
        if (isCharging) {
            super.onInterrupt(mob, interrupter);
            
            // Reward the interrupt
            interrupter.sendMessage("§a§l✓ You interrupted the Devastating Charge!");
            ActionBarUtil.addUniqueTemporaryMessage(interrupter, "§a§l✓ CHARGE INTERRUPTED! Well played! ✓", 60);
            interrupter.playSound(interrupter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            
            // Reset state
            chargeTarget = null;
            chargeStartLocation = null;
            chargeEndLocation = null;
        }
    }
    
    @Override
    public String getDescription() {
        return "A telegraphed high-damage charge attack that can be avoided with proper positioning and timing.";
    }
    
    @Override
    public String getTelegraphMessage() {
        return "is preparing a devastating charge attack!";
    }
}