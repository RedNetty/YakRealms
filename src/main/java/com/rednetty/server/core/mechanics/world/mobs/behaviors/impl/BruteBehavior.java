package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.EliteArchetypeBehaviorManager;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.utils.messaging.MessageUtils;
import com.rednetty.server.utils.ui.ActionBarUtil;
import com.rednetty.server.utils.ui.EliteActionBarManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.config.EliteAbilityConfig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Brute Archetype Behavior - Raw power and direct confrontation
 * 
 * Characteristics:
 * - High damage melee attacks
 * - Charge attacks that cover distance quickly
 * - Berserker rage when health is low
 * - Ground slam attacks that affect multiple enemies
 * - Intimidating presence that weakens nearby enemies
 */
public class BruteBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "brute_archetype";
    private static final int BEHAVIOR_PRIORITY = 400;
    
    // Use centralized configuration for consistent balance
    // All cooldowns, chances, and values are now managed in EliteAbilityConfig
    
    // Simplified ability tracking using global config
    private int abilitiesUsedThisFight = 0;
    
    // Behavior state tracking
    private long lastCharge = 0;
    private long lastSlam = 0;
    private long lastRage = 0;
    private long lastRoar = 0;
    private boolean isRaging = false;
    private boolean isCharging = false;
    
    // Per-player message cooldowns to prevent spam
    private final Map<UUID, Long> lastPlayerNotification = new HashMap<>();
    private static final long PLAYER_MESSAGE_COOLDOWN = 200L; // 10 seconds
    
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
               hasArchetype(mob, EliteBehaviorArchetype.BRUTE);
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
            // Brutes get permanent strength and resistance
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false));
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            
            // Create intimidating activation effect
            createBruteActivationEffect(entity.getLocation());
            
            // Subtle activation notification - no spam
            entity.getNearbyEntities(12, 6, 12).stream()
                .filter(e -> e instanceof Player)
                .limit(3) // Only notify nearby players
                .forEach(e -> {
                    Player player = (Player) e;
                    // Use elite action bar manager
                    EliteActionBarManager.notifyActivation(player, "Brute");
                });
            
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
            // Smarter ability usage - check global limits first
            if (abilitiesUsedThisFight >= EliteAbilityConfig.MAX_ABILITIES_PER_FIGHT) {
                return; // No more abilities this fight
            }
            
            // Check for berserker rage using config values
            double healthPercentage = entity.getHealth() / entity.getMaxHealth();
            if (healthPercentage <= EliteAbilityConfig.Brute.RAGE_HEALTH_THRESHOLD && 
                !isRaging && canUseAbility(currentTick, lastRage, EliteAbilityConfig.Brute.RAGE_COOLDOWN) && 
                lastRage == 0 && // Only once per fight
                ThreadLocalRandom.current().nextDouble() < EliteAbilityConfig.Brute.RAGE_CHANCE) {
                triggerBerserkerRage(mob);
                lastRage = currentTick;
                abilitiesUsedThisFight++;
                return; // Only one ability per tick
            }
            
            // Look for targets and use abilities (very rarely)
            Player nearestPlayer = findNearestPlayer(entity, 12.0);
            if (nearestPlayer != null) {
                // Use charge attack with config-based conditions
                double distance = entity.getLocation().distance(nearestPlayer.getLocation());
                if (distance > EliteAbilityConfig.Brute.CHARGE_MIN_DISTANCE && 
                    distance <= EliteAbilityConfig.Brute.CHARGE_MAX_DISTANCE && 
                    canUseAbility(currentTick, lastCharge, EliteAbilityConfig.Brute.CHARGE_COOLDOWN) &&
                    healthPercentage <= EliteAbilityConfig.Brute.CHARGE_HEALTH_THRESHOLD &&
                    ThreadLocalRandom.current().nextDouble() < EliteAbilityConfig.getScaledAbilityChance(healthPercentage, 1, EliteAbilityConfig.BASE_ABILITY_CHANCE)) {
                    executeCharge(mob, nearestPlayer);
                    lastCharge = currentTick;
                    abilitiesUsedThisFight++;
                    return;
                }
                
                // Use ground slam with config-based targeting
                int nearbyPlayerCount = countNearbyPlayers(entity, EliteAbilityConfig.Brute.SLAM_RANGE);
                if (nearbyPlayerCount >= EliteAbilityConfig.Brute.SLAM_MIN_TARGETS &&
                    canUseAbility(currentTick, lastSlam, EliteAbilityConfig.Brute.SLAM_COOLDOWN) &&
                    healthPercentage <= EliteAbilityConfig.Brute.SLAM_HEALTH_THRESHOLD &&
                    ThreadLocalRandom.current().nextDouble() < EliteAbilityConfig.getScaledAbilityChance(healthPercentage, nearbyPlayerCount, EliteAbilityConfig.BASE_ABILITY_CHANCE)) {
                    executeGroundSlam(mob);
                    lastSlam = currentTick;
                    abilitiesUsedThisFight++;
                    return;
                }
                
                // Intimidating roar with config-based frequency
                if (canUseAbility(currentTick, lastRoar, EliteAbilityConfig.Brute.ROAR_COOLDOWN) &&
                    countNearbyPlayers(entity, 8.0) >= 1 &&
                    ThreadLocalRandom.current().nextDouble() < (EliteAbilityConfig.BASE_ABILITY_CHANCE * 0.5)) {
                    executeIntimidatingRoar(mob);
                    lastRoar = currentTick;
                }
            }
            
            // Apply ongoing brute effects
            applyBruteAura(mob);
            
        } catch (Exception e) {
            // Ignore tick errors
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        try {
            // Brutes deal extra damage based on tier
            double bruteMultiplier = 1.2 + (mob.getTier() * 0.1); // +20% base, +10% per tier
            
            // Extra damage when raging
            if (isRaging) {
                bruteMultiplier *= 1.5;
            }
            
            // Extra damage when charging
            if (isCharging) {
                bruteMultiplier *= 1.8;
                isCharging = false; // Reset charge state
            }
            
            // Create dramatic impact effects for all brute attacks
            createImpactEffect(target.getLocation());
            
            // Screen shake effect for high damage
            if (damage * bruteMultiplier > 30.0 && target instanceof Player) {
                Player player = (Player) target;
                // Create dramatic screen effects
                player.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 3, 0.5, 0.5, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
            }
            
            return damage * bruteMultiplier;
            
        } catch (Exception e) {
            return damage;
        }
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        try {
            // Brutes have a chance to ignore damage when raging (much less frequent)
            if (isRaging && random.nextDouble() < 0.1) { // Reduced to 10% chance when raging
                LivingEntity entity = mob.getEntity();
                if (entity != null) {
                    entity.setHealth(Math.min(entity.getHealth() + damage * 0.7, entity.getMaxHealth()));
                    createDamageResistEffect(entity.getLocation());
                    
                    // Dramatic resistance effect
                    entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                    entity.getWorld().spawnParticle(Particle.BLOCK, entity.getLocation().add(0, 1, 0), 15, 1, 1, 1, 0.1);
                    
                    // Show resistance message to nearby players (with cooldown)
                    entity.getNearbyEntities(8, 4, 8).stream()
                        .filter(e -> e instanceof Player)
                        .forEach(e -> sendPlayerMessage((Player) e, "§c§l⛷ " + mob.getType().getTierSpecificName(mob.getTier()) + " §6RESISTS damage! ⛷", 40L));
                }
            }
            
            // Build up rage with each hit
            buildRage(mob, damage);
            
        } catch (Exception e) {
            // Ignore damage processing errors
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        try {
            // Brutes let out a threatening growl when they spot enemies
            player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_AMBIENT, 0.8f, 0.6f);
            
            // Apply brief intimidation effect
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS, 40, 0, false, false)); // 2 seconds
                
        } catch (Exception e) {
            // Ignore detection errors
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        try {
            // Brutes explode in fury when they die
            LivingEntity entity = mob.getEntity();
            if (entity != null) {
                createBruteDeathExplosion(entity.getLocation(), mob.getTier());
                
                // Damage nearby enemies in death explosion
                entity.getNearbyEntities(3, 2, 3).stream()
                    .filter(e -> e instanceof Player)
                    .forEach(e -> ((Player) e).damage(mob.getTier() * 4.0, entity));
            }
            
        } catch (Exception e) {
            // Ignore death processing errors
        }
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        try {
            // Reset brute state
            isRaging = false;
            isCharging = false;
            lastCharge = 0;
            lastSlam = 0;
            lastRage = 0;
            lastRoar = 0;
            
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    // ==================== BRUTE ABILITIES ====================
    
    private void executeCharge(CustomMob mob, Player target) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Register ability start with centralized manager
            EliteArchetypeBehaviorManager.getInstance().startAbility(mob, "CHARGING");
            
            Vector direction = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
            Location targetLocation = target.getLocation().clone();
            
            // TELEGRAPH PHASE: Clear but concise warning (1.2 seconds)
            entity.getNearbyEntities(12, 6, 12).stream()
                .filter(e -> e instanceof Player)
                .forEach(e -> {
                    Player player = (Player) e;
                    // Use prioritized messaging system
                    if (player.equals(target)) {
                        EliteActionBarManager.notifyCriticalDanger(player, "⚡ CHARGE TARGET! MOVE! ⚡");
                        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.5f);
                    } else {
                        EliteActionBarManager.notifyAbilityTelegraph(player, "Charge");
                    }
                });
            
            // Show visual charge path for strategic counterplay
            Location startLoc = entity.getLocation();
            for (int i = 1; i <= 10; i++) {
                Location particleLoc = startLoc.clone().add(direction.clone().multiply(i));
                entity.getWorld().spawnParticle(Particle.DUST, particleLoc, 5, 0.2, 0.2, 0.2, 0, 
                    new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
            }
            
            // Telegraph buildup with increasing intensity
            new BukkitRunnable() {
                int telegraphTicks = 0;
                
                @Override
                public void run() {
                    if (telegraphTicks >= EliteAbilityConfig.STANDARD_TELEGRAPH_DURATION || !entity.isValid()) {
                        cancel();
                        executeActualCharge(mob, entity, direction, targetLocation);
                        return;
                    }
                    
                    // Charging up effects - increasing intensity
                    int intensity = Math.min(telegraphTicks / 3, 10);
                    entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, 
                        entity.getLocation().add(0, 1, 0), intensity, 0.3, 0.3, 0.3, 0.1);
                    entity.getWorld().spawnParticle(Particle.CRIT, 
                        entity.getLocation().add(0, 0.5, 0), intensity / 2, 0.2, 0.2, 0.2, 0.05);
                    
                    // Increasing sound frequency
                    if (telegraphTicks % Math.max(1, 10 - (telegraphTicks / 3)) == 0) {
                        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 
                            0.8f, 0.5f + (telegraphTicks * 0.05f));
                    }
                    
                    // Final warning at 75% telegraph
                    if (telegraphTicks == 22) {
                        entity.getNearbyEntities(8, 4, 8).stream()
                            .filter(e -> e instanceof Player)
                            .forEach(e -> {
                                Player player = (Player) e;
                                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
                            });
                    }
                    
                    telegraphTicks++;
                }
            }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
            
        } catch (Exception e) {
            if (entity != null) {
                entity.removeMetadata("brute_ability", YakRealms.getInstance());
            }
        }
    }
    
    private void executeActualCharge(CustomMob mob, LivingEntity entity, Vector direction, Location originalTarget) {
        try {
            isCharging = true;
            
            // EXECUTION PHASE: Actual devastating charge
            direction.setY(0.2); // Slight upward trajectory
            entity.setVelocity(direction.multiply(1.8)); // Faster charge for telegraphed attack
            
            // Dramatic charge initiation
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.6f);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation().add(0, 0.5, 0), 5, 0.5, 0.5, 0.5, 0.1);
            
            createChargeTrail(entity);
            
            // Enhanced impact detection with larger hitbox for fairness
            new BukkitRunnable() {
                int ticks = 0;
                boolean hasHitTarget = false;
                
                @Override
                public void run() {
                    if (!entity.isValid() || ticks >= 25 || hasHitTarget) { // 1.25 seconds max
                        isCharging = false;
                        entity.removeMetadata("brute_ability", YakRealms.getInstance());
                        cancel();
                        return;
                    }
                    
                    // Create intense trail effects during charge
                    entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0.02);
                    entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.05);
                    
                    // Sound during charge
                    if (ticks % 2 == 0) {
                        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4f, 1.5f);
                    }
                    
                    // Enhanced collision detection - larger and fairer hitbox
                    entity.getNearbyEntities(2.5, 2.5, 2.5).stream()
                        .filter(e -> e instanceof Player)
                        .forEach(e -> {
                            Player player = (Player) e;
                            if (!hasHitTarget) {
                                hasHitTarget = true;
                                
                                // Config-based damage for telegraphed attack
                                double damage = EliteAbilityConfig.getTierScaledDamage(EliteAbilityConfig.Brute.CHARGE_DAMAGE_MULTIPLIER, mob.getTier());
                                player.damage(damage, entity);
                                
                                // Massive knockback in charge direction
                                Vector knockback = direction.clone().multiply(1.8);
                                knockback.setY(0.6); // Strong vertical component
                                player.setVelocity(knockback);
                                
                                // Severe debuff effects
                                player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.SLOWNESS, 80, 4, false, false)); // 4 seconds slowness V
                                player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.WEAKNESS, 100, 2, false, false)); // 5 seconds weakness III
                                player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.NAUSEA, 60, 1, false, false)); // 3 seconds nausea
                                
                                createChargeImpact(player.getLocation());
                                
                                // Use hit notification system
                                EliteActionBarManager.notifyAbilityHit(player, "Charge", "Severe");
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
                                
                                // Create massive impact effect
                                player.getWorld().spawnParticle(Particle.EXPLOSION, 
                                    player.getLocation().add(0, 1, 0), 8, 1, 1, 1, 0.2);
                                player.getWorld().playSound(player.getLocation(), 
                                    Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                            }
                        });
                    
                    ticks++;
                }
            }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
            
        } catch (Exception e) {
            isCharging = false;
            if (entity != null) {
                entity.removeMetadata("brute_ability", YakRealms.getInstance());
            }
        }
    }
    
    private void executeGroundSlam(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Register ability start with centralized manager
            EliteArchetypeBehaviorManager.getInstance().startAbility(mob, "GROUND_SLAM");
            
            Location slamLocation = entity.getLocation();
            
            // TELEGRAPH PHASE: Concise warnings
            entity.getNearbyEntities(8, 4, 8).stream()
                .filter(e -> e instanceof Player)
                .forEach(e -> {
                    Player player = (Player) e;
                    double distance = player.getLocation().distance(slamLocation);
                    if (distance <= 6.0) {
                        EliteActionBarManager.notifyCriticalDanger(player, "⚠ SLAM ZONE! MOVE! ⚠");
                        player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.8f);
                    } else {
                        EliteActionBarManager.notifyAbilityTelegraph(player, "Ground Slam");
                    }
                });
            
            // Show danger zone visually with expanding circles
            for (int radius = 1; radius <= 6; radius++) {
                final int r = radius;
                for (int i = 0; i < 360; i += 15) {
                    double angle = Math.toRadians(i);
                    Location particleLoc = slamLocation.clone().add(
                        r * Math.cos(angle), 0.1, r * Math.sin(angle));
                    entity.getWorld().spawnParticle(Particle.DUST, particleLoc, 2, 0.1, 0.1, 0.1, 0, 
                        new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.5f));
                }
            }
            
            // Telegraph buildup phase
            new BukkitRunnable() {
                int telegraphTicks = 0;
                
                @Override
                public void run() {
                    if (telegraphTicks >= EliteAbilityConfig.STANDARD_TELEGRAPH_DURATION + 8 || !entity.isValid()) {
                        cancel();
                        executeActualGroundSlam(mob, entity, slamLocation);
                        return;
                    }
                    
                    // Buildup effects - entity preparing to slam
                    if (telegraphTicks < 30) {
                        // Rising animation effect
                        double liftHeight = (telegraphTicks / 30.0) * 1.5;
                        entity.teleport(slamLocation.clone().add(0, liftHeight, 0));
                        
                        // Charging particles
                        entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, 
                            entity.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.1);
                        entity.getWorld().spawnParticle(Particle.CRIT, 
                            entity.getLocation().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.05);
                    } else {
                        // Pause at peak before slam
                        entity.teleport(slamLocation.clone().add(0, 1.5, 0));
                        entity.getWorld().spawnParticle(Particle.FLAME, 
                            entity.getLocation(), 5, 0.3, 0.3, 0.3, 0.02);
                    }
                    
                    // Audio buildup
                    if (telegraphTicks % 8 == 0) {
                        float pitch = 0.5f + (telegraphTicks * 0.03f);
                        entity.getWorld().playSound(entity.getLocation(), 
                            Sound.ENTITY_IRON_GOLEM_STEP, 0.8f, pitch);
                    }
                    
                    // Final warning
                    if (telegraphTicks == 35) {
                        entity.getNearbyEntities(8, 4, 8).stream()
                            .filter(e -> e instanceof Player)
                            .forEach(e -> {
                                Player player = (Player) e;
                                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 2.0f);
                                EliteActionBarManager.notifyCriticalDanger(player, "⚠ SLAM NOW! ⚠");
                            });
                    }
                    
                    telegraphTicks++;
                }
            }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
            
        } catch (Exception e) {
            if (entity != null) {
                entity.removeMetadata("brute_ability", YakRealms.getInstance());
            }
        }
    }
    
    private void executeActualGroundSlam(CustomMob mob, LivingEntity entity, Location slamLocation) {
        try {
            // SLAM IMPACT!
            entity.teleport(slamLocation); // Slam down to ground
            
            // Massive slam effects
            entity.getWorld().playSound(slamLocation, Sound.ENTITY_RAVAGER_STUNNED, 1.5f, 0.2f);
            entity.getWorld().playSound(slamLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
            entity.getWorld().spawnParticle(Particle.EXPLOSION, slamLocation, 10, 2, 0, 2, 0.2);
            entity.getWorld().spawnParticle(Particle.BLOCK, slamLocation.add(0, 0.1, 0), 
                50, 3, 1, 3, 0.5, org.bukkit.Material.STONE.createBlockData());
            
            // Distance-based damage and effects
            entity.getNearbyEntities(7, 4, 7).stream()
                .filter(e -> e instanceof Player)
                .forEach(e -> {
                    Player player = (Player) e;
                    double distance = player.getLocation().distance(slamLocation);
                    
                    if (distance <= 7.0) { // Within slam range
                        // Config-based scaled damage - maximum at center, decreasing with distance
                        double damageMultiplier = Math.max(0.1, 1.0 - (distance / 7.0));
                        double damage = EliteAbilityConfig.getTierScaledDamage(EliteAbilityConfig.Brute.SLAM_DAMAGE_MULTIPLIER, mob.getTier()) * damageMultiplier;
                        player.damage(damage, entity);
                        
                        // Knockback away from slam center
                        Vector knockback = player.getLocation().subtract(slamLocation).toVector().normalize();
                        knockback.setY(0.4 + (damageMultiplier * 0.3)); // Higher knockback for closer players
                        player.setVelocity(knockback.multiply(1.8 * damageMultiplier));
                        
                        // Status effects based on distance
                        if (distance <= 3.0) {
                            // Close range - severe effects
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 100, 3, false, false)); // 5 seconds slowness IV
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.WEAKNESS, 120, 2, false, false)); // 6 seconds weakness III
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.MINING_FATIGUE, 80, 2, false, false)); // 4 seconds mining fatigue III
                            
                            EliteActionBarManager.notifyAbilityHit(player, "Slam", "Severe");
                        } else if (distance <= 5.0) {
                            // Medium range - moderate effects
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 80, 2, false, false)); // 4 seconds slowness III
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.WEAKNESS, 60, 1, false, false)); // 3 seconds weakness II
                            
                            EliteActionBarManager.notifyAbilityHit(player, "Slam", "Medium");
                        } else {
                            // Edge range - light effects
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 40, 1, false, false)); // 2 seconds slowness II
                            
                            EliteActionBarManager.notifyAbilityHit(player, "Slam", "Light");
                        }
                        
                        // Visual feedback based on damage taken
                        if (damageMultiplier > 0.7) {
                            player.getWorld().spawnParticle(Particle.EXPLOSION, 
                                player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
                        }
                        
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
                    }
                });
                
            // Enhanced shockwave effect that shows the attack's power
            createShockwave(slamLocation, mob.getTier());
            
            // End ability tracking with centralized manager
            EliteArchetypeBehaviorManager.getInstance().endAbility(mob, "GROUND_SLAM");
            
        } catch (Exception e) {
            if (entity != null) {
                entity.removeMetadata("brute_ability", YakRealms.getInstance());
            }
        }
    }
    
    private void triggerBerserkerRage(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            isRaging = true;
            
            // Apply rage effects
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH, 400, 2, false, false)); // 20 seconds Strength III
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, 400, 1, false, false)); // 20 seconds Speed II
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, 400, 1, false, false)); // 20 seconds Resistance II
            
            // Create dramatic rage activation effect
            Location loc = entity.getLocation();
            entity.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.5f, 0.5f);
            entity.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.6f);
            
            // Multiple particle layers for dramatic effect
            entity.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 5, 1, 1, 1, 0.1);
            entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, loc.clone().add(0, 2, 0), 30, 2, 2, 2, 0.2);
            entity.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 50, 2, 2, 2, 0.1);
            entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, 1, 0), 20, 1.5, 1, 1.5, 0.1);
            
            // Alert nearby players briefly
            entity.getNearbyEntities(12, 6, 12).stream()
                .filter(e -> e instanceof Player)
                .limit(4) // Limit notifications
                .forEach(e -> {
                    Player player = (Player) e;
                    EliteActionBarManager.notifyDirectThreat(player, "[Brute: RAGE MODE]");
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
                });
            
            // Schedule rage end
            new BukkitRunnable() {
                @Override
                public void run() {
                    isRaging = false;
                    if (entity.isValid()) {
                        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_HURT, 0.5f, 0.8f);
                    }
                }
            }.runTaskLater(YakRealms.getInstance(), 400L);
            
        } catch (Exception e) {
            isRaging = false; // Reset on error
        }
    }
    
    private void executeIntimidatingRoar(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            Location roarLocation = entity.getLocation();
            
            // Create intimidating sound and effects
            entity.getWorld().playSound(roarLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);
            entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, roarLocation.add(0, 1, 0), 10, 1, 0.5, 1, 0.05);
            
            // Apply fear effects to nearby players
            entity.getNearbyEntities(6, 4, 6).stream()
                .filter(e -> e instanceof Player)
                .forEach(e -> {
                    Player player = (Player) e;
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, 100, 1, false, false)); // 5 seconds Weakness II
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 80, 0, false, false)); // 4 seconds Slowness I
                    
                    // Visual fear effect for the player
                    player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.3f, 2.0f);
                });
                
        } catch (Exception e) {
            // Ignore roar errors
        }
    }
    
    private void applyBruteAura(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        try {
            // Brutes emit an intimidating aura
            if (random.nextInt(40) == 0) { // Every 2 seconds on average
                Location loc = entity.getLocation().add(0, 1, 0);
                entity.getWorld().spawnParticle(Particle.SMOKE, loc, 3, 0.5, 0.5, 0.5, 0.01);
                
                if (isRaging) {
                    entity.getWorld().spawnParticle(Particle.FLAME, loc, 2, 0.3, 0.3, 0.3, 0.01);
                }
            }
            
        } catch (Exception e) {
            // Ignore aura errors
        }
    }
    
    private void buildRage(CustomMob mob, double damage) {
        // This could be used for a more complex rage system
        // For now, rage is triggered by low health only
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Sends a message to player with cooldown to prevent spam
     */
    private void sendPlayerMessage(Player player, String message, long ticks) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastPlayerNotification.get(playerId);
        
        // Only send message if enough time has passed
        if (lastTime == null || currentTime - lastTime >= PLAYER_MESSAGE_COOLDOWN * 50) { // Convert ticks to ms
            ActionBarUtil.addUniqueTemporaryMessage(player, message, ticks);
            lastPlayerNotification.put(playerId, currentTime);
        }
    }
    
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
    
    private Player findNearestPlayer(LivingEntity entity, double maxRange) {
        return entity.getNearbyEntities(maxRange, maxRange, maxRange).stream()
            .filter(e -> e instanceof Player)
            .map(e -> (Player) e)
            .filter(Player::isOnline)
            .filter(p -> !p.isDead())
            .min((p1, p2) -> Double.compare(
                p1.getLocation().distance(entity.getLocation()),
                p2.getLocation().distance(entity.getLocation())))
            .orElse(null);
    }
    
    private int countNearbyPlayers(LivingEntity entity, double range) {
        return (int) entity.getNearbyEntities(range, range, range).stream()
            .filter(e -> e instanceof Player)
            .filter(e -> ((Player) e).isOnline() && !((Player) e).isDead())
            .count();
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createBruteActivationEffect(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, location.add(0, 1, 0), 15, 1, 1, 1, 0.1);
                location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 10, 1, 0.5, 1, 0.05);
                location.getWorld().playSound(location, Sound.ENTITY_RAVAGER_CELEBRATE, 1.0f, 0.8f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    private void createImpactEffect(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.CRIT, location, 8, 0.5, 0.5, 0.5, 0.1);
                location.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 0.9f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    private void createDamageResistEffect(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.ENCHANT, location.add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                location.getWorld().playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    private void createChargeTrail(LivingEntity entity) {
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                if (!entity.isValid() || ticks >= 20) {
                    cancel();
                    return;
                }
                
                try {
                    Location loc = entity.getLocation();
                    entity.getWorld().spawnParticle(Particle.CLOUD, loc, 3, 0.3, 0.1, 0.3, 0.05);
                    if (ticks % 5 == 0) {
                        entity.getWorld().playSound(loc, Sound.ENTITY_HORSE_GALLOP, 0.3f, 1.2f);
                    }
                } catch (Exception e) {
                    // Ignore trail errors
                }
                
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    private void createChargeImpact(Location location) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.EXPLOSION, location, 3, 0.5, 0.5, 0.5, 0.1);
                location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
    
    private void createShockwave(Location center, int tier) {
        new BukkitRunnable() {
            double radius = 0;
            final double maxRadius = 3 + tier;
            
            @Override
            public void run() {
                if (radius >= maxRadius) {
                    cancel();
                    return;
                }
                
                try {
                    // Create expanding ring of particles
                    for (int i = 0; i < 20; i++) {
                        double angle = (2 * Math.PI * i) / 20;
                        double x = center.getX() + radius * Math.cos(angle);
                        double z = center.getZ() + radius * Math.sin(angle);
                        Location particleLoc = new Location(center.getWorld(), x, center.getY(), z);
                        
                        center.getWorld().spawnParticle(Particle.BLOCK, particleLoc, 2, 0.1, 0.1, 0.1, 0.1,
                                org.bukkit.Material.STONE);
                    }
                    
                    if (radius == 0) {
                        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.8f);
                    }
                } catch (Exception e) {
                    // Ignore shockwave errors
                }
                
                radius += 0.5;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 2L);
    }
    
    private void createBruteDeathExplosion(Location location, int tier) {
        try {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 3 + tier, 1, 1, 1, 0.1);
                location.getWorld().spawnParticle(Particle.LAVA, location, 20, 2, 1, 2, 0.1);
                location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.6f);
            }
        } catch (Exception e) {
            // Ignore effect errors
        }
    }
}