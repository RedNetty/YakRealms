package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Tactical AI Mob Behavior - Smart positioning and group coordination
 * 
 * Features:
 * - Intelligent positioning based on mob tier and type
 * - Group coordination and focus targeting
 * - Flanking maneuvers for higher-tier mobs
 * - Adaptive tactics based on player behavior
 * - No environmental changes, subtle improvements only
 */
public class TacticalAIMobBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "tactical_ai";
    private static final int BEHAVIOR_PRIORITY = 150; // Higher than basic behaviors
    
    // Tactical state tracking
    private final Map<UUID, TacticalData> tacticalStates = new ConcurrentHashMap<>();
    private final Map<Location, GroupCoordination> groupCoordinators = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int POSITIONING_UPDATE_INTERVAL = 30; // 1.5 seconds
    private static final int GROUP_COORDINATION_RADIUS = 12; // blocks
    private static final double FLANKING_ATTEMPT_CHANCE = 0.3; // 30% for tier 3+
    private static final double RETREAT_HEALTH_THRESHOLD = 0.25; // 25% health
    
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
        // Apply to tier 2+ mobs for tactical behavior
        return mob != null && 
               mob.isValid() && 
               mob.getTier() >= 2 &&
               !mob.isWorldBoss(); // World bosses have their own AI
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
        TacticalData data = new TacticalData(mob.getTier(), mob.isElite());
        tacticalStates.put(mobId, data);
        
        // Set tactical metadata
        entity.setMetadata("tactical_ai", 
            new org.bukkit.metadata.FixedMetadataValue(YakRealms.getInstance(), true));
    }
    
    @Override
    public void onTick(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || !entity.isValid()) return;
        
        UUID mobId = entity.getUniqueId();
        TacticalData data = tacticalStates.get(mobId);
        if (data == null) return;
        
        long currentTick = entity.getTicksLived();
        
        // Update positioning every interval
        if (currentTick % POSITIONING_UPDATE_INTERVAL == 0) {
            processTacticalBehavior(mob, data, currentTick);
        }
        
        // Update group coordination
        updateGroupCoordination(mob, data);
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        // Update tactical state when taking damage
        UUID mobId = mob.getEntity().getUniqueId();
        TacticalData data = tacticalStates.get(mobId);
        if (data != null && attacker instanceof Player) {
            data.currentTarget = (Player) attacker;
            data.tacticalMode = TacticalMode.AGGRESSIVE;
        }
    }

    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Clean up tactical data on death
        if (mob.getEntity() != null) {
            tacticalStates.remove(mob.getEntity().getUniqueId());
        }
    }

    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        // No damage modifications for tactical behavior
        return damage;
    }

    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        // Set player as target when detected
        UUID mobId = mob.getEntity().getUniqueId();
        TacticalData data = tacticalStates.get(mobId);
        if (data != null && data.currentTarget == null) {
            data.currentTarget = player;
            data.tacticalMode = TacticalMode.AGGRESSIVE;
        }
    }

    @Override
    public void onRemove(CustomMob mob) {
        if (mob.getEntity() != null) {
            tacticalStates.remove(mob.getEntity().getUniqueId());
        }
    }
    
    // ==================== TACTICAL BEHAVIOR PROCESSING ====================
    
    private void processTacticalBehavior(CustomMob mob, TacticalData data, long currentTick) {
        List<Player> nearbyPlayers = findNearbyPlayers(mob.getEntity(), 15.0);
        if (nearbyPlayers.isEmpty()) {
            data.currentTarget = null;
            data.tacticalMode = TacticalMode.PATROL;
            return;
        }
        
        // Select target based on tactical priorities
        Player target = selectTacticalTarget(mob, nearbyPlayers, data);
        data.currentTarget = target;
        
        if (target == null) return;
        
        // Determine tactical mode
        determineTacticalMode(mob, target, data);
        
        // Execute tactical behavior
        switch (data.tacticalMode) {
            case AGGRESSIVE -> executeAggressive(mob, target, data);
            case FLANKING -> executeFlanking(mob, target, data);
            case DEFENSIVE -> executeDefensive(mob, target, data);
            case RETREAT -> executeRetreat(mob, target, data);
            case SUPPORT -> executeSupport(mob, target, data);
            default -> executePatrol(mob, data);
        }
    }
    
    private void determineTacticalMode(CustomMob mob, Player target, TacticalData data) {
        LivingEntity entity = mob.getEntity();
        double healthPercent = entity.getHealth() / entity.getMaxHealth();
        double distance = entity.getLocation().distance(target.getLocation());
        
        // Low health - retreat if possible
        if (healthPercent <= RETREAT_HEALTH_THRESHOLD && mob.getTier() >= 3) {
            data.tacticalMode = TacticalMode.RETREAT;
            return;
        }
        
        // Check for flanking opportunities (higher tiers only)
        if (mob.getTier() >= 3 && shouldAttemptFlanking(mob, target, data)) {
            data.tacticalMode = TacticalMode.FLANKING;
            return;
        }
        
        // Support nearby allies if elite
        if (mob.isElite() && hasNearbyAllies(entity)) {
            data.tacticalMode = TacticalMode.SUPPORT;
            return;
        }
        
        // Default tactical modes based on distance and tier
        if (distance <= 4.0) {
            data.tacticalMode = mob.getTier() >= 4 ? TacticalMode.DEFENSIVE : TacticalMode.AGGRESSIVE;
        } else if (distance <= 8.0) {
            data.tacticalMode = TacticalMode.AGGRESSIVE;
        } else {
            data.tacticalMode = TacticalMode.AGGRESSIVE; // Close distance
        }
    }
    
    // ==================== TACTICAL EXECUTION METHODS ====================
    
    private void executeAggressive(CustomMob mob, Player target, TacticalData data) {
        LivingEntity entity = mob.getEntity();
        
        // Move directly toward target with slight randomization
        Vector direction = target.getLocation().subtract(entity.getLocation()).toVector();
        direction.normalize();
        
        // Add slight unpredictability for higher tiers
        if (mob.getTier() >= 3) {
            double randomAngle = (random.nextDouble() - 0.5) * Math.PI / 6; // ±30 degrees
            direction = rotateVector(direction, randomAngle);
        }
        
        // Set velocity with tier-based speed
        double speed = 0.3 + (mob.getTier() * 0.1);
        direction.multiply(speed).setY(0);
        entity.setVelocity(direction);
        
        data.lastTacticalAction = System.currentTimeMillis();
    }
    
    private void executeFlanking(CustomMob mob, Player target, TacticalData data) {
        LivingEntity entity = mob.getEntity();
        
        // Calculate flanking position (90 degrees from player's facing direction)
        Vector playerDirection = target.getLocation().getDirection();
        Vector flankDirection = new Vector(-playerDirection.getZ(), 0, playerDirection.getX());
        
        // Choose left or right flank randomly, or continue current flank
        if (data.flankingSide == FlankingSide.NONE) {
            data.flankingSide = random.nextBoolean() ? FlankingSide.LEFT : FlankingSide.RIGHT;
        }
        
        if (data.flankingSide == FlankingSide.LEFT) {
            flankDirection.multiply(-1);
        }
        
        // Move to flanking position
        Location flankPosition = target.getLocation().add(flankDirection.multiply(6));
        Vector toFlank = flankPosition.subtract(entity.getLocation()).toVector();
        toFlank.normalize().multiply(0.4).setY(0);
        entity.setVelocity(toFlank);
        
        data.lastTacticalAction = System.currentTimeMillis();
        
        // Reset flanking after some time
        if (System.currentTimeMillis() - data.lastTacticalAction > 5000) { // 5 seconds
            data.flankingSide = FlankingSide.NONE;
            data.tacticalMode = TacticalMode.AGGRESSIVE;
        }
    }
    
    private void executeDefensive(CustomMob mob, Player target, TacticalData data) {
        LivingEntity entity = mob.getEntity();
        
        // Maintain optimal combat distance
        double currentDistance = entity.getLocation().distance(target.getLocation());
        double optimalDistance = 4.0 + (mob.getTier() * 0.5);
        
        Vector direction;
        if (currentDistance < optimalDistance) {
            // Too close - back away
            direction = entity.getLocation().subtract(target.getLocation()).toVector();
        } else if (currentDistance > optimalDistance + 2.0) {
            // Too far - move closer
            direction = target.getLocation().subtract(entity.getLocation()).toVector();
        } else {
            // Circle strafe
            Vector toTarget = target.getLocation().subtract(entity.getLocation()).toVector();
            direction = new Vector(-toTarget.getZ(), 0, toTarget.getX());
            if (random.nextBoolean()) direction.multiply(-1);
        }
        
        direction.normalize().multiply(0.25).setY(0);
        entity.setVelocity(direction);
        
        data.lastTacticalAction = System.currentTimeMillis();
    }
    
    private void executeRetreat(CustomMob mob, Player target, TacticalData data) {
        LivingEntity entity = mob.getEntity();
        
        // Move away from target while looking for cover or allies
        Vector retreatDirection = entity.getLocation().subtract(target.getLocation()).toVector();
        retreatDirection.normalize().multiply(0.5).setY(0);
        entity.setVelocity(retreatDirection);
        
        // Add speed boost for retreat
        if (!entity.hasPotionEffect(PotionEffectType.SPEED)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
        }
        
        data.lastTacticalAction = System.currentTimeMillis();
        
        // Return to combat if health recovers or allies arrive
        double healthPercent = entity.getHealth() / entity.getMaxHealth();
        if (healthPercent > RETREAT_HEALTH_THRESHOLD + 0.1 || hasNearbyAllies(entity)) {
            data.tacticalMode = TacticalMode.AGGRESSIVE;
        }
    }
    
    private void executeSupport(CustomMob mob, Player target, TacticalData data) {
        LivingEntity entity = mob.getEntity();
        
        // Find nearby ally that needs support
        List<LivingEntity> allies = findNearbyAllies(entity);
        if (allies.isEmpty()) {
            data.tacticalMode = TacticalMode.AGGRESSIVE;
            return;
        }
        
        // Move to support position (between ally and enemy)
        LivingEntity ally = allies.get(0);
        Vector supportPosition = ally.getLocation().add(target.getLocation()).toVector().multiply(0.5);
        Location supportLoc = new Location(entity.getWorld(), supportPosition.getX(), entity.getY(), supportPosition.getZ());
        
        Vector toSupport = supportLoc.subtract(entity.getLocation()).toVector();
        toSupport.normalize().multiply(0.35).setY(0);
        entity.setVelocity(toSupport);
        
        data.lastTacticalAction = System.currentTimeMillis();
    }
    
    private void executePatrol(CustomMob mob, TacticalData data) {
        // Simple wandering behavior when no targets
        LivingEntity entity = mob.getEntity();
        
        if (System.currentTimeMillis() - data.lastTacticalAction > 3000) { // Change direction every 3s
            Vector randomDirection = new Vector(
                random.nextDouble(-1, 1),
                0,
                random.nextDouble(-1, 1)
            ).normalize().multiply(0.15);
            
            entity.setVelocity(randomDirection);
            data.lastTacticalAction = System.currentTimeMillis();
        }
    }
    
    // ==================== GROUP COORDINATION ====================
    
    private void updateGroupCoordination(CustomMob mob, TacticalData data) {
        if (!mob.isElite()) return; // Only elites coordinate
        
        LivingEntity entity = mob.getEntity();
        Location area = entity.getLocation().clone();
        area = new Location(area.getWorld(), 
            Math.floor(area.getX() / 16) * 16, 
            area.getY(), 
            Math.floor(area.getZ() / 16) * 16);
        
        GroupCoordination group = groupCoordinators.computeIfAbsent(area, k -> new GroupCoordination());
        
        // Update group members
        List<LivingEntity> nearbyAllies = findNearbyAllies(entity);
        group.updateMembers(nearbyAllies);
        
        // Coordinate target selection
        if (data.currentTarget != null && nearbyAllies.size() >= 2) {
            coordinateFocusTarget(nearbyAllies, data.currentTarget);
        }
    }
    
    private void coordinateFocusTarget(List<LivingEntity> allies, Player target) {
        // Simple coordination - all allies focus the same target
        for (LivingEntity ally : allies) {
            if (ally.hasMetadata("tactical_ai")) {
                TacticalData allyData = tacticalStates.get(ally.getUniqueId());
                if (allyData != null && allyData.currentTarget == null) {
                    allyData.currentTarget = target;
                    allyData.tacticalMode = TacticalMode.AGGRESSIVE;
                }
            }
        }
    }
    
    // ==================== TARGET SELECTION ====================
    
    private Player selectTacticalTarget(CustomMob mob, List<Player> players, TacticalData data) {
        if (players.isEmpty()) return null;
        
        // Prioritize current target if still valid
        if (data.currentTarget != null && players.contains(data.currentTarget)) {
            double distance = mob.getEntity().getLocation().distance(data.currentTarget.getLocation());
            if (distance <= 20.0) { // Don't lose target unless they're far away
                return data.currentTarget;
            }
        }
        
        // Select new target based on tactical priorities
        return players.stream()
            .filter(p -> p.getGameMode() != org.bukkit.GameMode.SPECTATOR)
            .min((p1, p2) -> {
                // Priority 1: Closest player
                double d1 = mob.getEntity().getLocation().distance(p1.getLocation());
                double d2 = mob.getEntity().getLocation().distance(p2.getLocation());
                
                // Priority 2: Lower health players (for elites)
                if (mob.isElite()) {
                    double h1 = p1.getHealth() / p1.getMaxHealth();
                    double h2 = p2.getHealth() / p2.getMaxHealth();
                    
                    if (Math.abs(h1 - h2) > 0.3) { // Significant health difference
                        return Double.compare(h1, h2);
                    }
                }
                
                return Double.compare(d1, d2);
            })
            .orElse(null);
    }
    
    // ==================== UTILITY METHODS ====================
    
    private List<Player> findNearbyPlayers(LivingEntity entity, double radius) {
        return entity.getWorld().getNearbyEntities(entity.getLocation(), radius, radius, radius)
            .stream()
            .filter(e -> e instanceof Player && ((Player) e).isValid())
            .map(e -> (Player) e)
            .collect(Collectors.toList());
    }
    
    private List<LivingEntity> findNearbyAllies(LivingEntity entity) {
        return entity.getWorld().getNearbyEntities(entity.getLocation(), GROUP_COORDINATION_RADIUS, 5, GROUP_COORDINATION_RADIUS)
            .stream()
            .filter(e -> e instanceof LivingEntity && e != entity)
            .filter(e -> !(e instanceof Player))
            .filter(e -> e.hasMetadata("custommob") || e.hasMetadata("type"))
            .map(e -> (LivingEntity) e)
            .collect(Collectors.toList());
    }
    
    private boolean hasNearbyAllies(LivingEntity entity) {
        return !findNearbyAllies(entity).isEmpty();
    }
    
    private boolean shouldAttemptFlanking(CustomMob mob, Player target, TacticalData data) {
        if (mob.getTier() < 3) return false;
        if (System.currentTimeMillis() - data.lastFlankingAttempt < 10000) return false; // 10s cooldown
        
        double distance = mob.getEntity().getLocation().distance(target.getLocation());
        if (distance < 6.0 || distance > 15.0) return false; // Need medium distance
        
        if (random.nextDouble() < FLANKING_ATTEMPT_CHANCE) {
            data.lastFlankingAttempt = System.currentTimeMillis();
            return true;
        }
        
        return false;
    }
    
    private Vector rotateVector(Vector vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z);
    }
    
    // ==================== DATA CLASSES ====================
    
    private enum TacticalMode {
        PATROL, AGGRESSIVE, FLANKING, DEFENSIVE, RETREAT, SUPPORT
    }
    
    private enum FlankingSide {
        NONE, LEFT, RIGHT
    }
    
    private static class TacticalData {
        public final int tier;
        public final boolean isElite;
        public TacticalMode tacticalMode = TacticalMode.PATROL;
        public FlankingSide flankingSide = FlankingSide.NONE;
        public Player currentTarget = null;
        public long lastTacticalAction = 0;
        public long lastFlankingAttempt = 0;
        
        public TacticalData(int tier, boolean isElite) {
            this.tier = tier;
            this.isElite = isElite;
        }
    }
    
    private static class GroupCoordination {
        public List<UUID> memberIds = new ArrayList<>();
        public Player focusTarget = null;
        public long lastUpdate = 0;
        
        public void updateMembers(List<LivingEntity> allies) {
            memberIds.clear();
            allies.forEach(ally -> memberIds.add(ally.getUniqueId()));
            lastUpdate = System.currentTimeMillis();
        }
    }
}