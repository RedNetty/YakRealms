package com.rednetty.server.core.mechanics.world.mobs.behaviors.impl;

import com.rednetty.server.core.mechanics.world.mobs.behaviors.ActionBarMessageManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehavior;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simplified Necromancer Elite Behavior
 * 
 * Signature Ability: Life Drain
 * - Drains health from nearby enemies and heals self
 * - Creates dramatic visual effects
 * - Only activates under specific conditions
 */
public class NecromancerBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "necromancer";
    private static final int LIFE_DRAIN_COOLDOWN = 400; // 20 seconds
    private static final double LIFE_DRAIN_RANGE = 8.0;
    private static final double LIFE_DRAIN_DAMAGE = 6.0;
    
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
        // Apply necromancer visual effects
        LivingEntity entity = mob.getEntity();
        if (entity != null) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
        }
    }
    
    @Override
    public void onTick(CustomMob mob) {
        // Passive death aura effect every 5 seconds
        if (System.currentTimeMillis() % 5000 < 50) { // Roughly every 5 seconds
            createDeathAura(mob.getEntity().getLocation());
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        // 30% chance to use life drain when player detected
        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            attemptLifeDrain(mob);
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        // 20% chance to trigger life drain on attack
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            attemptLifeDrain(mob);
        }
        return damage; // Don't modify damage
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        // 25% chance to use life drain when damaged and low health
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        if (healthPercent < 0.5 && ThreadLocalRandom.current().nextDouble() < 0.25) {
            attemptLifeDrain(mob);
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Dramatic death effect
        createNecromancerDeath(mob.getEntity().getLocation());
        
        // Clean up tracking
        lastUsed.remove(mob.getUniqueMobId());
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        // Clean up tracking
        lastUsed.remove(mob.getUniqueMobId());
    }
    
    /**
     * Attempt to use Life Drain ability with telegraph
     */
    private void attemptLifeDrain(CustomMob mob) {
        String mobId = mob.getUniqueMobId();
        long currentTime = System.currentTimeMillis();
        
        // Check cooldown
        Long lastUse = lastUsed.get(mobId);
        if (lastUse != null && (currentTime - lastUse) < LIFE_DRAIN_COOLDOWN * 50) {
            return; // Still on cooldown
        }
        
        LivingEntity necromancer = mob.getEntity();
        Location loc = necromancer.getLocation();
        
        // Find nearby players to drain
        List<Player> nearbyPlayers = loc.getWorld().getNearbyEntities(loc, 
            LIFE_DRAIN_RANGE, LIFE_DRAIN_RANGE, LIFE_DRAIN_RANGE).stream()
            .filter(entity -> entity instanceof Player)
            .map(entity -> (Player) entity)
            .filter(player -> !player.isDead() && player.getGameMode() != org.bukkit.GameMode.CREATIVE)
            .toList();
        
        if (nearbyPlayers.isEmpty()) return;
        
        // Start cooldown immediately to prevent spam
        lastUsed.put(mobId, currentTime);
        
        // TELEGRAPH PHASE - 2.5 second warning
        createLifeDrainTelegraph(loc, nearbyPlayers);
        
        // Schedule the actual ability after telegraph
        com.rednetty.server.YakRealms.getInstance().getServer().getScheduler().runTaskLater(
            com.rednetty.server.YakRealms.getInstance(),
            () -> executeLifeDrain(mob, necromancer, loc, nearbyPlayers),
            50L // 2.5 seconds
        );
    }
    
    /**
     * Create telegraph warning for Life Drain
     */
    private void createLifeDrainTelegraph(Location loc, List<Player> targets) {
        // Visual telegraph - dark energy building up
        for (int i = 0; i < 50; i++) {
            com.rednetty.server.YakRealms.getInstance().getServer().getScheduler().runTaskLater(
                com.rednetty.server.YakRealms.getInstance(),
                () -> {
                    loc.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0), 3, 
                        1.0, 1.0, 1.0, 0.05);
                    loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0, 0.5, 0), 2, 
                        0.8, 0.8, 0.8, 0.02);
                },
                i
            );
        }
        
        // Sound telegraph
        loc.getWorld().playSound(loc, Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.5f);
        
        // One-time warning to players - NO SPAM
        for (Player player : targets) {
            actionBarManager.sendActionBarMessage(player,
                Component.text("⚫ NECROMANCER CHARGING LIFE DRAIN! MOVE AWAY! ⚫")
                    .color(NamedTextColor.RED),
                50); // 2.5 seconds - matches telegraph time
        }
    }
    
    /**
     * Execute the actual Life Drain after telegraph
     */
    private void executeLifeDrain(CustomMob mob, LivingEntity necromancer, Location loc, List<Player> originalTargets) {
        if (!necromancer.isValid()) return;
        
        double totalHealing = 0;
        
        // Only affect players still in range
        for (Player player : originalTargets) {
            if (!player.isValid() || player.isDead() || 
                player.getLocation().distance(loc) > LIFE_DRAIN_RANGE) {
                continue;
            }
            
            // Damage player
            player.damage(LIFE_DRAIN_DAMAGE);
            totalHealing += LIFE_DRAIN_DAMAGE * 0.7;
            
            // Visual effects only
            createLifeDrainEffect(player.getLocation(), loc);
        }
        
        // Heal necromancer
        double newHealth = Math.min(necromancer.getMaxHealth(), 
            necromancer.getHealth() + totalHealing);
        necromancer.setHealth(newHealth);
        
        // Sound effects only - no more messages
        loc.getWorld().playSound(loc, Sound.ENTITY_WITCH_DRINK, 2.0f, 0.6f);
        createLifeDrainAura(loc);
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createLifeDrainEffect(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double distance = from.distance(to);
        
        // Create particle beam
        for (double d = 0; d < distance; d += 0.3) {
            Location particleLoc = from.clone().add(direction.clone().multiply(d));
            from.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 
                new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.5f)); // Dark red
        }
    }
    
    private void createLifeDrainAura(Location loc) {
        // Central vortex effect
        loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 20, 
            1.0, 1.0, 1.0, 0.1);
        loc.getWorld().spawnParticle(Particle.WITCH, loc, 15, 
            LIFE_DRAIN_RANGE * 0.7, 1.0, LIFE_DRAIN_RANGE * 0.7, 0.05);
    }
    
    private void createDeathAura(Location loc) {
        // Subtle passive effect
        loc.getWorld().spawnParticle(Particle.WITCH, loc, 3, 
            2.0, 0.5, 2.0, 0.01);
    }
    
    private void createNecromancerDeath(Location loc) {
        // Dramatic death explosion
        loc.getWorld().spawnParticle(Particle.WITCH, loc, 50, 3.0, 2.0, 3.0, 0.2);
        loc.getWorld().spawnParticle(Particle.SOUL, loc, 30, 2.0, 1.5, 2.0, 0.15);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
    }
}