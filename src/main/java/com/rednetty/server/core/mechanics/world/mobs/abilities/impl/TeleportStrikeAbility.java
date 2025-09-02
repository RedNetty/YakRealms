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
 * Teleport Strike - A telegraphed teleportation attack with clear targeting
 * 
 * Strategic Elements:
 * - Long telegraph shows exact target and landing location
 * - Target can move to avoid or prepare for the attack
 * - Clear visual and audio warnings for counterplay
 * - Rewards quick positioning and timing
 * - Punishes standing still but allows skilled dodging
 */
public class TeleportStrikeAbility extends EliteAbility {
    
    private static final double STRIKE_DAMAGE = 22.0;
    private static final double STRIKE_RADIUS = 3.0;
    private static final int VULNERABILITY_DURATION = 40; // 2 seconds
    
    private Player strikeTarget;
    private Location originalLocation;
    private Location targetLocation;
    private boolean hasTeleported = false;
    
    public TeleportStrikeAbility() {
        super(
            "teleport_strike",
            "Teleport Strike",
            AbilityType.OFFENSIVE,
            EliteAbilityConfig.Assassin.SHADOW_STEP_COOLDOWN + 80,
            70, // 3.5 second telegraph - time to react and reposition
            0.16 // 16% base chance
        );
    }
    
    @Override
    protected boolean meetsPrerequisites(CustomMob mob, List<Player> targets) {
        if (targets.isEmpty()) return false;
        
        // Need a target that's not too close (teleport needs distance)
        Player bestTarget = findBestStrikeTarget(mob, targets);
        if (bestTarget == null) return false;
        
        double distance = mob.getEntity().getLocation().distance(bestTarget.getLocation());
        return distance >= 6.0 && distance <= 20.0;
    }
    
    @Override
    protected double applyContextualScaling(double baseChance, CustomMob mob, 
                                          List<Player> targets, CombatContext context) {
        double chance = baseChance;
        
        // More likely against isolated targets
        if (!context.getIsolatedPlayers().isEmpty()) {
            chance *= 1.8;
        }
        
        // More likely when assassin archetype and low health
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        if (healthPercent <= 0.4) {
            chance *= 1.6; // Desperate strikes
        }
        
        // Less likely in confined spaces
        if (context.getTerrain() == CombatContext.TerrainType.CONFINED) {
            chance *= 0.7;
        }
        
        return chance;
    }
    
    @Override
    public AbilityPriority getPriority(CustomMob mob, List<Player> targets, CombatContext context) {
        // High priority for isolated targets
        if (!context.getIsolatedPlayers().isEmpty()) {
            return AbilityPriority.HIGH;
        }
        
        // Normal priority otherwise
        return AbilityPriority.NORMAL;
    }
    
    @Override
    protected void startTelegraph(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        strikeTarget = findBestStrikeTarget(mob, targets);
        if (strikeTarget == null) return;
        
        originalLocation = entity.getLocation().clone();
        targetLocation = calculateStrikePosition(strikeTarget.getLocation());
        hasTeleported = false;
        
        // Clear warning to target and nearby players
        String targetName = strikeTarget.getName();
        targets.forEach(player -> {
            if (player == strikeTarget) {
                ActionBarUtil.addUniqueTemporaryMessage(player, 
                    "§c§l⚡ " + mob.getType().getTierSpecificName(mob.getTier()) + " §4§lTARGETING YOU §c§lfor TELEPORT STRIKE! §e§lMOVE! ⚡", 
                    telegraphDuration);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
            } else {
                ActionBarUtil.addUniqueTemporaryMessage(player, 
                    "§c§l⚡ " + mob.getType().getTierSpecificName(mob.getTier()) + " §6teleporting to strike §e" + targetName + "§6! ⚡", 
                    telegraphDuration);
            }
        });
        
        // Show telegraph effects
        createTeleportTelegraphEffects(mob, originalLocation, targetLocation);
        
        // Telegraph animation showing the incoming strike location
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = telegraphDuration;
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !entity.isValid() || !isCharging) {
                    cancel();
                    return;
                }
                
                // Show strike location to players
                if (ticks % 6 == 0) { // Every 0.3 seconds
                    showStrikeTargetArea(targetLocation);
                }
                
                // Show charging energy at mob location
                if (ticks % 8 == 0) { // Every 0.4 seconds
                    showTeleportCharging(originalLocation);
                }
                
                // Intensifying sound effects
                if (ticks % 12 == 0) { // Every 0.6 seconds
                    float pitch = 1.0f + ((float) ticks / maxTicks) * 0.8f;
                    entity.getWorld().playSound(originalLocation, Sound.ENTITY_ENDERMAN_AMBIENT, 0.8f, pitch);
                }
                
                // Final warning
                if (ticks == maxTicks - 20) { // 1 second before execution
                    strikeTarget.sendMessage("§4§l⚠ TELEPORT STRIKE INCOMING! ⚠");
                    ActionBarUtil.addUniqueTemporaryMessage(strikeTarget, 
                        "§4§l💀 STRIKE IMMINENT! DODGE NOW! 💀", 20);
                    strikeTarget.playSound(strikeTarget.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 2.0f);
                }
                
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    @Override
    protected void executeAbility(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || strikeTarget == null || targetLocation == null) return;
        
        hasTeleported = true;
        
        // Teleport effect at original location
        originalLocation.getWorld().playSound(originalLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        originalLocation.getWorld().spawnParticle(Particle.PORTAL, originalLocation.add(0, 1, 0), 30, 1, 2, 1, 0.5);
        
        // Teleport the mob
        entity.teleport(targetLocation);
        
        // Arrival effect
        targetLocation.getWorld().playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        targetLocation.getWorld().playSound(targetLocation, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
        targetLocation.getWorld().spawnParticle(Particle.PORTAL, targetLocation.add(0, 1, 0), 25, 1, 2, 1, 0.5);
        targetLocation.getWorld().spawnParticle(Particle.CRIT, targetLocation.add(0, 1, 0), 20, 1.5, 1, 1.5, 0.1);
        
        // Execute the strike after a very brief moment (allows for last-second dodging)
        new BukkitRunnable() {
            @Override
            public void run() {
                executeStrike(mob, targets);
            }
        }.runTaskLater(YakRealms.getInstance(), 3L); // 0.15 second delay for final dodge window
    }
    
    private void executeStrike(CustomMob mob, List<Player> targets) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        Location strikeLocation = entity.getLocation();
        
        // Strike effects
        strikeLocation.getWorld().playSound(strikeLocation, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.0f);
        strikeLocation.getWorld().playSound(strikeLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
        strikeLocation.getWorld().spawnParticle(Particle.SWEEP_ATTACK, strikeLocation.add(0, 1, 0), 8, 2, 1, 2, 0.1);
        strikeLocation.getWorld().spawnParticle(Particle.CRIT, strikeLocation.add(0, 1, 0), 15, 1.5, 1, 1.5, 0.1);
        
        // Check for players in strike radius
        boolean hitTarget = false;
        for (Player player : targets) {
            double distance = player.getLocation().distance(strikeLocation);
            if (distance <= STRIKE_RADIUS) {
                hitPlayer(mob, player, distance);
                hitTarget = true;
            }
        }
        
        // If no one was hit, the mob is vulnerable briefly
        if (!hitTarget) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, VULNERABILITY_DURATION, 2, false, false));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, VULNERABILITY_DURATION, 0, false, false));
            
            // Notify nearby players of vulnerability
            targets.forEach(player -> {
                if (player.getLocation().distance(strikeLocation) <= 12.0) {
                    ActionBarUtil.addUniqueTemporaryMessage(player, 
                        "§a§l✓ " + mob.getType().getTierSpecificName(mob.getTier()) + " §2missed and is vulnerable! ✓", 60);
                }
            });
        }
    }
    
    private void hitPlayer(CustomMob mob, Player player, double distance) {
        double damage = getScaledDamage(STRIKE_DAMAGE, mob.getTier());
        
        // Distance-based damage (closer = more damage)
        double damageMultiplier = Math.max(0.6, 1.2 - (distance / STRIKE_RADIUS));
        damage *= damageMultiplier;
        
        player.damage(damage, mob.getEntity());
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, false, false));
        
        // Strike hit effects
        createStrikeHitEffect(player.getLocation());
        
        // Feedback to hit player
        ActionBarUtil.addUniqueTemporaryMessage(player, 
            "§4§l💥 HIT by Teleport Strike! 💥", 60);
        
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
        
        // Knockback away from strike point
        Vector knockback = player.getLocation().subtract(mob.getEntity().getLocation()).toVector().normalize();
        knockback.multiply(1.2).setY(0.4);
        player.setVelocity(knockback);
    }
    
    private Player findBestStrikeTarget(CustomMob mob, List<Player> targets) {
        Location mobLoc = mob.getEntity().getLocation();
        
        return targets.stream()
            .filter(p -> {
                double distance = mobLoc.distance(p.getLocation());
                return distance >= 6.0 && distance <= 20.0;
            })
            .filter(p -> hasLineSight(mobLoc, p.getLocation()))
            .min((p1, p2) -> {
                // Prefer isolated targets
                boolean p1Isolated = isPlayerIsolated(p1, targets);
                boolean p2Isolated = isPlayerIsolated(p2, targets);
                
                if (p1Isolated && !p2Isolated) return -1;
                if (!p1Isolated && p2Isolated) return 1;
                
                // Then prefer targets with lower health
                double h1 = p1.getHealth() / p1.getMaxHealth();
                double h2 = p2.getHealth() / p2.getMaxHealth();
                return Double.compare(h1, h2);
            })
            .orElse(null);
    }
    
    private Location calculateStrikePosition(Location playerLoc) {
        // Teleport to a position slightly behind the player
        Vector direction = playerLoc.getDirection().normalize().multiply(-2);
        Location strikePos = playerLoc.clone().add(direction);
        strikePos.setY(playerLoc.getY()); // Same Y level
        return strikePos;
    }
    
    private boolean hasLineSight(Location from, Location to) {
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
            .noneMatch(p -> p.getLocation().distance(player.getLocation()) <= 6.0);
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createTeleportTelegraphEffects(CustomMob mob, Location start, Location target) {
        // Effects at mob location
        start.getWorld().spawnParticle(Particle.PORTAL, start.add(0, 1, 0), 25, 1, 1, 1, 0.3);
        start.getWorld().spawnParticle(Particle.ENCHANT, start.add(0, 1, 0), 20, 1, 1, 1, 0.5);
        start.getWorld().playSound(start, Sound.ENTITY_ENDERMAN_SCREAM, 0.8f, 1.2f);
        
        // Effects at target location
        target.getWorld().spawnParticle(Particle.DUST, target.add(0, 0.1, 0), 30, 1.5, 0.1, 1.5, 
            new Particle.DustOptions(org.bukkit.Color.RED, 2.5f));
    }
    
    private void showStrikeTargetArea(Location target) {
        // Show danger circle at strike location
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI * i) / 16;
            double x = target.getX() + STRIKE_RADIUS * Math.cos(angle);
            double z = target.getZ() + STRIKE_RADIUS * Math.sin(angle);
            Location particleLoc = new Location(target.getWorld(), x, target.getY() + 0.1, z);
            
            target.getWorld().spawnParticle(Particle.DUST, particleLoc, 
                2, 0.1, 0.1, 0.1, new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
        }
    }
    
    private void showTeleportCharging(Location location) {
        location.getWorld().spawnParticle(Particle.PORTAL, location.add(0, 1, 0), 8, 0.8, 1, 0.8, 0.2);
        location.getWorld().spawnParticle(Particle.ENCHANT, location.add(0, 1, 0), 6, 0.5, 1, 0.5, 0.3);
    }
    
    private void createStrikeHitEffect(Location location) {
        location.getWorld().spawnParticle(Particle.CRIT, location.add(0, 1, 0), 20, 1, 1, 1, 0.2);
        location.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, location.add(0, 1, 0), 12, 0.8, 0.8, 0.8, 0.1);
        location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 5, 1.5, 0.5, 1.5, 0.1);
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
            interrupter.sendMessage("§a§l✓ You interrupted the Teleport Strike!");
            ActionBarUtil.addUniqueTemporaryMessage(interrupter, "§a§l✓ TELEPORT INTERRUPTED! Great timing! ✓", 60);
            interrupter.playSound(interrupter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
            
            // Reset state
            strikeTarget = null;
            originalLocation = null;
            targetLocation = null;
            hasTeleported = false;
        }
    }
    
    @Override
    public String getDescription() {
        return "A telegraphed teleportation attack that targets a specific player. Can be avoided by moving away from the marked area.";
    }
    
    @Override
    public String getTelegraphMessage() {
        return "is charging energy for a teleport strike!";
    }
}