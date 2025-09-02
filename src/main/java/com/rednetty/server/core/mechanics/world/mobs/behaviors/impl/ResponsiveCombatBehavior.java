package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Responsive Combat Behavior - Makes all mobs more engaging and reactive
 * 
 * Features:
 * - Dynamic positioning based on player behavior
 * - Reactive abilities triggered by player actions
 * - Adaptive difficulty based on player skill
 * - Context-aware combat decisions
 * - Interactive dodge/counter mechanics
 */
public class ResponsiveCombatBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "responsive_combat";
    private static final int BEHAVIOR_PRIORITY = 100; // High priority to apply to most mobs
    
    // Combat state tracking
    private final Map<UUID, CombatData> combatDataMap = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int DODGE_COOLDOWN = 160; // 8 seconds
    private static final int COUNTER_ATTACK_COOLDOWN = 120; // 6 seconds
    private static final int POSITIONING_UPDATE_INTERVAL = 40; // 2 seconds
    
    private static final double DODGE_CHANCE = 0.25; // 25% base dodge chance
    private static final double COUNTER_CHANCE = 0.35; // 35% counter attack chance
    
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
        // Apply to all valid mobs except world bosses (they have their own behaviors)
        return mob != null && 
               mob.isValid() && 
               !mob.isWorldBoss() &&
               mob.getTier() >= 1;
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
        combatDataMap.put(mobId, new CombatData());
        
        // Set initial behavior metadata
        entity.setMetadata("responsive_combat", 
            new org.bukkit.metadata.FixedMetadataValue(YakRealms.getInstance(), true));
    }
    
    @Override
    public void onTick(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || !entity.isValid()) return;
        
        UUID mobId = entity.getUniqueId();
        CombatData data = combatDataMap.get(mobId);
        if (data == null) return;
        
        long currentTick = entity.getTicksLived();
        
        // Update combat positioning
        if (currentTick % POSITIONING_UPDATE_INTERVAL == 0) {
            updateCombatPositioning(mob, data);
        }
        
        // Update responsive behaviors
        updateResponsiveBehaviors(mob, data, currentTick);
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        UUID mobId = mob.getEntity().getUniqueId();
        CombatData data = combatDataMap.get(mobId);
        if (data == null) return;
        
        // Record damage for adaptive behavior
        data.totalDamageTaken += damage;
        data.lastDamageTime = System.currentTimeMillis();
        data.lastAttacker = attacker;
        
        // Attempt dodge or counter for significant damage
        if (attacker instanceof Player player) {
            double damagePercent = damage / mob.getEntity().getMaxHealth();
            if (damagePercent > 0.1) { // Significant damage
                attemptDodgeResponse(mob, player, data);
            }
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Clean up combat data on death
        if (mob.getEntity() != null) {
            combatDataMap.remove(mob.getEntity().getUniqueId());
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        UUID mobId = mob.getEntity().getUniqueId();
        CombatData data = combatDataMap.get(mobId);
        if (data == null) return damage;
        
        // Increase damage based on combat experience
        double modifier = 1.0 + (data.damageDealt / 1000.0 * 0.1); // Up to 10% bonus
        modifier = Math.min(modifier, 1.2); // Cap at 20% bonus
        
        // Track damage dealt
        double finalDamage = damage * modifier;
        data.damageDealt += finalDamage;
        
        return finalDamage;
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        UUID mobId = mob.getEntity().getUniqueId();
        CombatData data = combatDataMap.get(mobId);
        if (data == null) return;
        
        // Start combat tracking
        data.currentTarget = player;
        data.combatStartTime = System.currentTimeMillis();
        data.lastPlayerDetection = System.currentTimeMillis();
        
        // Initial combat positioning
        initiateResponsiveCombat(mob, player, data);
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        if (mob.getEntity() != null) {
            combatDataMap.remove(mob.getEntity().getUniqueId());
        }
    }
    
    // ==================== COMBAT MECHANICS ====================
    
    
    private void updateResponsiveBehaviors(CustomMob mob, CombatData data, long currentTick) {
        if (data.currentTarget == null) return;
        
        // Check if target is still valid
        Player target = data.currentTarget;
        if (!target.isOnline() || target.isDead()) {
            data.currentTarget = null;
            data.isInCombat = false;
            return;
        }
        
        // Update combat state
        double distance = mob.getEntity().getLocation().distance(target.getLocation());
        data.isInCombat = distance <= 16.0; // Combat range
        
        // Process reactive combat mechanics
        if (data.isInCombat) {
            processReactiveCombat(mob, target, data, currentTick);
        }
    }
    
    private void attemptDodgeResponse(CustomMob mob, Player player, CombatData data) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - data.lastDodgeAttempt < DODGE_COOLDOWN * 50) return;
        
        if (random.nextDouble() < DODGE_CHANCE * getTierModifier(mob)) {
            performDodge(mob, player, data);
            data.lastDodgeAttempt = currentTime;
        }
    }
    
    private void performDodge(CustomMob mob, Player player, CombatData data) {
        LivingEntity entity = mob.getEntity();
        Vector dodgeDirection = entity.getLocation().subtract(player.getLocation()).toVector();
        dodgeDirection.normalize().multiply(0.5).setY(0.2);
        entity.setVelocity(dodgeDirection);
        
        // Dodge effects
        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation(), 5, 0.3, 0.1, 0.3, 0.1);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
    }
    
    private void initiateResponsiveCombat(CustomMob mob, Player player, CombatData data) {
        // Set up initial combat state
        data.combatMode = CombatMode.ENGAGING;
    }
    
    private double getTierModifier(CustomMob mob) {
        return 0.5 + (mob.getTier() * 0.15); // Tier 1: 0.65x, Tier 5: 1.25x
    }
    
    // ==================== RESPONSIVE COMBAT MECHANICS ====================
    
    private void processReactiveCombat(CustomMob mob, Player target, CombatData data, long currentTick) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        // Check for dodge opportunities
        if (shouldDodge(mob, target, data, currentTick)) {
            executeDodge(mob, target, data, currentTick);
        }
        
        // Check for counter attack opportunities
        if (shouldCounterAttack(mob, target, data, currentTick)) {
            executeCounterAttack(mob, target, data, currentTick);
        }
        
        // Update threat assessment
        updateThreatAssessment(mob, target, data);
        
        // Adaptive behavior based on player skill
        adaptToPlayerBehavior(mob, target, data);
    }
    
    // ==================== DODGE SYSTEM ====================
    
    private boolean shouldDodge(CustomMob mob, Player target, CombatData data, long currentTick) {
        if (currentTick - data.lastDodge < DODGE_COOLDOWN) return false;
        
        // Check if player is about to attack (looking at mob, close distance, has weapon ready)
        if (!isPlayerAggressive(target, mob.getEntity())) return false;
        
        // Higher tier mobs dodge more frequently
        double dodgeChance = DODGE_CHANCE + (mob.getTier() * 0.05);
        
        // Elite mobs are better at dodging
        if (mob.isElite()) {
            dodgeChance += 0.15;
        }
        
        // Lower health = more desperate dodging
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        if (healthPercent <= 0.5) {
            dodgeChance += 0.2;
        }
        
        return random.nextDouble() < dodgeChance;
    }
    
    private void executeDodge(CustomMob mob, Player target, CombatData data, long currentTick) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        data.lastDodge = currentTick;
        
        // Calculate dodge direction (perpendicular to player's approach)
        Vector playerDirection = target.getLocation().subtract(entity.getLocation()).toVector().normalize();
        Vector dodgeDirection = new Vector(-playerDirection.getZ(), 0, playerDirection.getX());
        
        // Randomly choose left or right
        if (random.nextBoolean()) {
            dodgeDirection.multiply(-1);
        }
        
        // Execute dodge movement
        dodgeDirection.multiply(1.2).setY(0.3);
        entity.setVelocity(dodgeDirection);
        
        // Visual and audio feedback
        createDodgeEffects(entity.getLocation());
        
        // Brief speed boost after dodge
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 2, false, false));
        
        // Notify nearby players
        target.getWorld().getNearbyEntities(entity.getLocation(), 8, 4, 8)
            .stream()
            .filter(e -> e instanceof Player)
            .map(e -> (Player) e)
            .forEach(p -> {
                ActionBarUtil.addUniqueTemporaryMessage(p,
                    "§e⚡ " + getMobName(mob) + " §6dodged! ⚡", 40L);
                p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.5f);
            });
    }
    
    private void createDodgeEffects(Location location) {
        location.getWorld().spawnParticle(Particle.CLOUD, location.clone().add(0, 0.5, 0), 
            8, 0.5, 0.3, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.CRIT, location.clone().add(0, 1, 0),
            6, 0.8, 0.5, 0.8, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.8f);
    }
    
    // ==================== COUNTER ATTACK SYSTEM ====================
    
    private boolean shouldCounterAttack(CustomMob mob, Player target, CombatData data, long currentTick) {
        if (currentTick - data.lastCounterAttack < COUNTER_ATTACK_COOLDOWN) return false;
        
        // Only counter if player recently missed or finished an attack
        if (!hasPlayerAttackWindow(target, data)) return false;
        
        double counterChance = COUNTER_CHANCE;
        
        // Elite mobs counter more often
        if (mob.isElite()) {
            counterChance += 0.2;
        }
        
        // Higher tier = better counters
        counterChance += mob.getTier() * 0.08;
        
        return random.nextDouble() < counterChance;
    }
    
    private void executeCounterAttack(CustomMob mob, Player target, CombatData data, long currentTick) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        data.lastCounterAttack = currentTick;
        
        // Execute counter with enhanced effects
        createCounterAttackEffects(entity.getLocation());
        
        // Apply counter attack debuff to player
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false));
        
        // Counter attack damage
        double counterDamage = 8.0 + (mob.getTier() * 2.0);
        if (mob.isElite()) {
            counterDamage *= 1.5;
        }
        
        target.damage(counterDamage, entity);
        
        // Feedback
        ActionBarUtil.addUniqueTemporaryMessage(target,
            "§c§l⚡ COUNTER ATTACK! ⚡ §4Slowed and weakened!", 80L);
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
        
        // Notify other players
        entity.getWorld().getNearbyEntities(entity.getLocation(), 10, 5, 10)
            .stream()
            .filter(e -> e instanceof Player && e != target)
            .map(e -> (Player) e)
            .forEach(p -> {
                ActionBarUtil.addUniqueTemporaryMessage(p,
                    "§c" + getMobName(mob) + " §6counter-attacked §c" + target.getName() + "!", 60L);
            });
    }
    
    private void createCounterAttackEffects(Location location) {
        location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location.clone().add(0, 1, 0),
            3, 1.5, 0.5, 1.5, 0.1);
        location.getWorld().spawnParticle(Particle.CRIT, location.clone().add(0, 1, 0),
            12, 1, 1, 1, 0.15);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
    }
    
    // ==================== COMBAT POSITIONING ====================
    
    private void updateCombatPositioning(CustomMob mob, CombatData data) {
        if (!data.isInCombat || data.currentTarget == null) return;
        
        LivingEntity entity = mob.getEntity();
        Player target = data.currentTarget;
        
        if (entity == null || !target.isOnline()) return;
        
        double distance = entity.getLocation().distance(target.getLocation());
        
        // Maintain optimal combat distance based on mob type and tier
        double optimalDistance = getOptimalCombatDistance(mob);
        
        if (distance < optimalDistance - 2.0) {
            // Too close - back away
            repositionMob(entity, target, false);
        } else if (distance > optimalDistance + 3.0) {
            // Too far - move closer
            repositionMob(entity, target, true);
        }
        
        // Circle strafe for higher tier mobs
        if (mob.getTier() >= 3 && random.nextDouble() < 0.3) {
            circleStrafe(entity, target);
        }
    }
    
    private double getOptimalCombatDistance(CustomMob mob) {
        double base = 4.0;
        
        // Ranged mobs stay further back
        if (hasRangedWeapon(mob.getEntity())) {
            base += 3.0;
        }
        
        // Elite mobs are more tactical
        if (mob.isElite()) {
            base += 1.0;
        }
        
        // Higher tier mobs use better positioning
        base += mob.getTier() * 0.5;
        
        return base;
    }
    
    private void repositionMob(LivingEntity entity, Player target, boolean moveCloser) {
        Vector direction = moveCloser ? 
            target.getLocation().subtract(entity.getLocation()).toVector().normalize() :
            entity.getLocation().subtract(target.getLocation()).toVector().normalize();
        
        direction.multiply(0.8).setY(0);
        entity.setVelocity(direction);
    }
    
    private void circleStrafe(LivingEntity entity, Player target) {
        Vector toTarget = target.getLocation().subtract(entity.getLocation()).toVector();
        Vector perpendicular = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        
        // Randomly choose clockwise or counterclockwise
        if (random.nextBoolean()) {
            perpendicular.multiply(-1);
        }
        
        perpendicular.multiply(0.6);
        entity.setVelocity(perpendicular);
        
        // Visual effect for advanced movement
        entity.getWorld().spawnParticle(Particle.CLOUD, 
            entity.getLocation().clone().add(0, 0.1, 0), 3, 0.3, 0.1, 0.3, 0.02);
    }
    
    // ==================== THREAT ASSESSMENT ====================
    
    private void updateThreatAssessment(CustomMob mob, Player target, CombatData data) {
        // Assess player threat level based on various factors
        int threatLevel = 0;
        
        // Equipment assessment
        threatLevel += assessPlayerGear(target);
        
        // Player behavior assessment
        if (isPlayerAggressive(target, mob.getEntity())) {
            threatLevel += 2;
        }
        
        // Player health assessment
        double playerHealthPercent = target.getHealth() / target.getMaxHealth();
        if (playerHealthPercent >= 0.8) {
            threatLevel += 1;
        }
        
        data.playerThreatLevel = threatLevel;
        
        // Adjust behavior based on threat level
        if (threatLevel >= 5) {
            // High threat - play more defensively
            data.defensiveMode = true;
        } else if (threatLevel <= 2) {
            // Low threat - be more aggressive
            data.defensiveMode = false;
        }
    }
    
    private void adaptToPlayerBehavior(CustomMob mob, Player target, CombatData data) {
        // Adapt mob behavior based on observed player patterns
        if (data.defensiveMode) {
            // More cautious behavior
            if (random.nextDouble() < 0.4) {
                // Occasional defensive ability or positioning
                LivingEntity entity = mob.getEntity();
                if (entity != null) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, false, false));
                }
            }
        } else {
            // More aggressive behavior
            if (random.nextDouble() < 0.3) {
                // Occasional aggressive push
                repositionMob(mob.getEntity(), target, true);
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
    
    private Player findBestTarget(LivingEntity entity, List<Player> players) {
        return players.stream()
            .filter(p -> p.getGameMode() != org.bukkit.GameMode.SPECTATOR)
            .min((p1, p2) -> Double.compare(
                entity.getLocation().distance(p1.getLocation()),
                entity.getLocation().distance(p2.getLocation())))
            .orElse(null);
    }
    
    private boolean isPlayerAggressive(Player player, LivingEntity mob) {
        // Check if player is looking at mob
        Vector playerDirection = player.getLocation().getDirection();
        Vector toMob = mob.getLocation().subtract(player.getLocation()).toVector().normalize();
        double angle = playerDirection.angle(toMob);
        
        boolean lookingAtMob = angle < Math.PI / 4; // 45 degree cone
        boolean closeDistance = player.getLocation().distance(mob.getLocation()) <= 6.0;
        boolean hasWeapon = player.getInventory().getItemInMainHand().getType().name().contains("SWORD") ||
                           player.getInventory().getItemInMainHand().getType().name().contains("AXE");
        
        return lookingAtMob && closeDistance && hasWeapon;
    }
    
    private boolean hasPlayerAttackWindow(Player player, CombatData data) {
        // Simplified check for player attack cooldown/recovery
        return System.currentTimeMillis() - data.lastPlayerAttackTime < 1500; // 1.5 second window
    }
    
    private boolean hasRangedWeapon(LivingEntity entity) {
        if (entity.getEquipment() == null) return false;
        String weaponType = entity.getEquipment().getItemInMainHand().getType().name();
        return weaponType.contains("BOW") || weaponType.contains("CROSSBOW") || weaponType.contains("TRIDENT");
    }
    
    private int assessPlayerGear(Player player) {
        // Simple gear assessment (0-5 scale)
        int gearLevel = 0;
        
        // Check armor
        for (org.bukkit.inventory.ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType().name().contains("DIAMOND")) {
                gearLevel += 1;
            } else if (armor != null && armor.getType().name().contains("NETHERITE")) {
                gearLevel += 2;
            }
        }
        
        // Check weapon
        String weaponType = player.getInventory().getItemInMainHand().getType().name();
        if (weaponType.contains("DIAMOND")) {
            gearLevel += 1;
        } else if (weaponType.contains("NETHERITE")) {
            gearLevel += 2;
        }
        
        return Math.min(5, gearLevel);
    }
    
    private String getMobName(CustomMob mob) {
        if (mob.getEntity().getCustomName() != null) {
            return org.bukkit.ChatColor.stripColor(mob.getEntity().getCustomName());
        }
        return mob.getType().getDefaultName();
    }
    
    // ==================== DATA CLASSES ====================
    
    private static class CombatData {
        public boolean isInCombat = false;
        public boolean defensiveMode = false;
        public Player currentTarget = null;
        public int playerThreatLevel = 0;
        
        public long lastDodge = 0;
        public long lastCounterAttack = 0;
        public long lastPlayerAttackTime = 0;
        public long lastDodgeAttempt = 0;
        public long combatStartTime = 0;
        public long lastPlayerDetection = 0;
        public long lastDamageTime = 0;
        
        public double totalDamageTaken = 0;
        public double damageDealt = 0;
        public LivingEntity lastAttacker = null;
        
        public CombatMode combatMode = CombatMode.IDLE;
        
        public CombatData() {}
    }
    
    private enum CombatMode {
        IDLE, ENGAGING, AGGRESSIVE, DEFENSIVE, RETREATING
    }
}