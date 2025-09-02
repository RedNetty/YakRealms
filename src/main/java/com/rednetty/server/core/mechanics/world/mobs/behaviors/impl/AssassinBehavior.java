package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.core.mechanics.world.mobs.behaviors.ActionBarMessageManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simplified Assassin Elite Behavior
 * 
 * Signature Ability: Shadow Strike
 * - Teleports behind target and delivers high-damage backstab with poison
 * - Only activates when positioned correctly behind enemies
 * - Creates dramatic stealth effects
 */
public class AssassinBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "assassin";
    private static final int SHADOW_STRIKE_COOLDOWN = 300; // 15 seconds
    private static final double STRIKE_RANGE = 8.0;
    private static final double BACKSTAB_DAMAGE = 12.0;
    
    private final Map<String, Long> lastUsed = new HashMap<>();
    private final ActionBarMessageManager actionBarManager = ActionBarMessageManager.getInstance();
    
    @Override
    public String getBehaviorId() {
        return BEHAVIOR_ID;
    }
    
    @Override
    public boolean canApplyTo(CustomMob mob) {
        return mob.getTier() >= 3; // Only tier 3+ mobs
    }
    
    @Override
    public void onApply(CustomMob mob) {
        // Apply assassin buffs
        LivingEntity entity = mob.getEntity();
        if (entity != null) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 0));
        }
    }
    
    @Override
    public void onTick(CustomMob mob) {
        // Passive stealth aura - very subtle
        if (System.currentTimeMillis() % 8000 < 50) { // Every 8 seconds
            createShadowAura(mob.getEntity().getLocation());
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        // 25% chance to trigger shadow strike on attack
        if (ThreadLocalRandom.current().nextDouble() < 0.25) {
            attemptShadowStrike(mob, target);
        }
        return damage; // Don't modify damage
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        // 35% chance to use shadow strike when taking significant damage
        if (damage > 6.0 && ThreadLocalRandom.current().nextDouble() < 0.35) {
            if (attacker instanceof Player) {
                attemptShadowStrike(mob, attacker);
            }
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        // 20% chance to use shadow strike when player detected
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            attemptShadowStrike(mob, player);
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Final shadow explosion
        createAssassinDeath(mob.getEntity().getLocation());
        
        // Clean up tracking
        lastUsed.remove(mob.getUniqueMobId());
    }
    
    @Override
    public int getPriority() {
        return 95; // High priority
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        // Clean up tracking
        lastUsed.remove(mob.getUniqueMobId());
    }
    
    /**
     * Attempt to use Shadow Strike ability with telegraph
     */
    private void attemptShadowStrike(CustomMob mob, LivingEntity target) {
        if (!(target instanceof Player player)) return;
        
        String mobId = mob.getUniqueMobId();
        long currentTime = System.currentTimeMillis();
        
        // Check cooldown
        Long lastUse = lastUsed.get(mobId);
        if (lastUse != null && (currentTime - lastUse) < SHADOW_STRIKE_COOLDOWN * 50) {
            return; // Still on cooldown
        }
        
        LivingEntity assassin = mob.getEntity();
        Location startLoc = assassin.getLocation();
        
        // Check if target is within range
        if (startLoc.distance(player.getLocation()) > STRIKE_RANGE) {
            return;
        }
        
        // Start cooldown immediately
        lastUsed.put(mobId, currentTime);
        
        // TELEGRAPH PHASE - 2 second warning
        createShadowStrikeTelegraph(assassin, player);
        
        // Schedule the actual strike after telegraph
        com.rednetty.server.YakRealms.getInstance().getServer().getScheduler().runTaskLater(
            com.rednetty.server.YakRealms.getInstance(),
            () -> executeShadowStrike(mob, assassin, player, startLoc),
            40L // 2 seconds
        );
    }
    
    /**
     * Create telegraph warning for Shadow Strike
     */
    private void createShadowStrikeTelegraph(LivingEntity assassin, Player target) {
        // Visual telegraph - assassin becomes semi-visible and menacing
        for (int i = 0; i < 40; i++) {
            com.rednetty.server.YakRealms.getInstance().getServer().getScheduler().runTaskLater(
                com.rednetty.server.YakRealms.getInstance(),
                () -> {
                    if (assassin.isValid()) {
                        assassin.getWorld().spawnParticle(Particle.SMOKE, 
                            assassin.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0.05);
                        assassin.getWorld().spawnParticle(Particle.PORTAL, 
                            assassin.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.1);
                    }
                },
                i
            );
        }
        
        // Sound telegraph
        assassin.getWorld().playSound(assassin.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.8f, 1.5f);
        
        // One-time warning to target - NO SPAM
        actionBarManager.sendActionBarMessage(target,
            Component.text("⚔ ASSASSIN PREPARING SHADOW STRIKE! WATCH YOUR BACK! ⚔")
                .color(NamedTextColor.DARK_RED),
            40); // 2 seconds - matches telegraph time
    }
    
    /**
     * Execute the actual Shadow Strike after telegraph
     */
    private void executeShadowStrike(CustomMob mob, LivingEntity assassin, Player target, Location originalLoc) {
        if (!assassin.isValid() || !target.isValid() || target.isDead()) return;
        
        // Check if target moved out of range during telegraph
        if (assassin.getLocation().distance(target.getLocation()) > STRIKE_RANGE * 1.5) {
            return; // Target escaped
        }
        
        // Calculate position behind target
        Vector direction = target.getLocation().getDirection().normalize();
        Location behindTarget = target.getLocation().subtract(direction.multiply(2.5));
        behindTarget.setDirection(direction);
        
        // Teleport and strike
        createShadowStrikeEffect(originalLoc, behindTarget);
        assassin.teleport(behindTarget);
        
        // Deal damage and effects
        target.damage(BACKSTAB_DAMAGE);
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
        
        // Visual and sound effects only - no more messages
        createBackstabEffect(target.getLocation());
        behindTarget.getWorld().playSound(behindTarget, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.3f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.8f);
    }
    
    /**
     * Get nearby players within range
     */
    private List<Player> getNearbyPlayers(Location loc, double range) {
        return loc.getWorld().getNearbyEntities(loc, range, range, range).stream()
            .filter(entity -> entity instanceof Player)
            .map(entity -> (Player) entity)
            .filter(player -> !player.isDead() && player.getGameMode() != org.bukkit.GameMode.CREATIVE)
            .toList();
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createShadowAura(Location loc) {
        // Subtle passive effect
        loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.5, 0), 3, 
            1.5, 0.5, 1.5, 0.01);
    }
    
    private void createShadowStrikeEffect(Location from, Location to) {
        // Create teleportation particle trail
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);
        
        for (double d = 0; d < distance; d += 0.5) {
            Location particleLoc = from.clone().add(direction.clone().multiply(d));
            from.getWorld().spawnParticle(Particle.PORTAL, particleLoc, 2, 0.2, 0.2, 0.2, 0.1);
        }
        
        // Dramatic emergence effect
        to.getWorld().spawnParticle(Particle.SMOKE, to.clone().add(0, 1, 0), 15, 1.0, 1.0, 1.0, 0.1);
    }
    
    private void createBackstabEffect(Location location) {
        // Critical hit effects
        location.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, location, 20, 0.5, 1.0, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.CRIT, location, 15, 0.3, 0.3, 0.3, 0.2);
        
        // Poison cloud
        location.getWorld().spawnParticle(Particle.WITCH, location, 12, 0.8, 0.5, 0.8, 0.05);
    }
    
    private void createAssassinDeath(Location location) {
        // Dramatic death explosion
        location.getWorld().spawnParticle(Particle.SMOKE, location, 30, 2.0, 1.5, 2.0, 0.15);
        location.getWorld().spawnParticle(Particle.PORTAL, location, 20, 1.5, 1.0, 1.5, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_DEATH, 1.0f, 0.8f);
    }
}