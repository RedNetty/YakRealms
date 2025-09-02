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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simplified Warmaster Elite Behavior
 * 
 * Signature Ability: Battle Command
 * - Issues powerful war cry that buffs nearby allies and debuffs enemies
 * - Creates tactical battlefield effects
 * - Only activates during intense combat situations
 */
public class WarmasterBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "warmaster";
    private static final int BATTLE_COMMAND_COOLDOWN = 500; // 25 seconds
    private static final double COMMAND_RANGE = 12.0;
    private static final double ENEMY_DAMAGE = 5.0;
    
    private final Map<String, Long> lastUsed = new HashMap<>();
    private final ActionBarMessageManager actionBarManager = ActionBarMessageManager.getInstance();
    
    @Override
    public String getBehaviorId() {
        return BEHAVIOR_ID;
    }
    
    @Override
    public boolean canApplyTo(CustomMob mob) {
        return mob.getTier() >= 4; // Only tier 4+ mobs
    }
    
    @Override
    public void onApply(CustomMob mob) {
        // Apply warmaster buffs
        LivingEntity entity = mob.getEntity();
        if (entity != null) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
            
            // Give warmaster equipment if applicable
            if (entity.getEquipment() != null) {
                entity.getEquipment().setChestplate(new org.bukkit.inventory.ItemStack(Material.IRON_CHESTPLATE));
                entity.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.IRON_HELMET));
            }
        }
    }
    
    @Override
    public void onTick(CustomMob mob) {
        // Passive command aura - very subtle
        if (System.currentTimeMillis() % 10000 < 50) { // Every 10 seconds
            createCommandAura(mob.getEntity().getLocation());
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        // 40% chance to use battle command when multiple enemies detected
        List<Player> nearbyEnemies = getNearbyPlayers(mob.getEntity().getLocation());
        if (nearbyEnemies.size() >= 2 && ThreadLocalRandom.current().nextDouble() < 0.4) {
            attemptBattleCommand(mob);
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        // 15% chance to use battle command on attack
        if (ThreadLocalRandom.current().nextDouble() < 0.15) {
            attemptBattleCommand(mob);
        }
        return damage; // Don't modify damage
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        // 30% chance to use battle command when taking significant damage
        if (damage > 8.0 && ThreadLocalRandom.current().nextDouble() < 0.3) {
            attemptBattleCommand(mob);
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Final rallying cry
        createFinalWarCry(mob.getEntity().getLocation());
        
        // Clean up tracking
        lastUsed.remove(mob.getUniqueMobId());
    }
    
    @Override
    public int getPriority() {
        return 90; // High priority
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        // Clean up tracking
        lastUsed.remove(mob.getUniqueMobId());
    }
    
    /**
     * Attempt to use Battle Command ability
     */
    private void attemptBattleCommand(CustomMob mob) {
        String mobId = mob.getUniqueMobId();
        long currentTime = System.currentTimeMillis();
        
        // Check cooldown
        Long lastUse = lastUsed.get(mobId);
        if (lastUse != null && (currentTime - lastUse) < BATTLE_COMMAND_COOLDOWN * 50) {
            return; // Still on cooldown
        }
        
        LivingEntity warmaster = mob.getEntity();
        Location loc = warmaster.getLocation();
        
        // Find nearby entities
        List<Player> nearbyEnemies = getNearbyPlayers(loc);
        List<LivingEntity> nearbyAllies = getNearbyAllies(loc);
        
        if (nearbyEnemies.isEmpty()) return;
        
        // Use the ability
        lastUsed.put(mobId, currentTime);
        
        // Buff self significantly
        warmaster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 400, 2)); // 20 seconds, level 3
        warmaster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1)); // 20 seconds, level 2
        warmaster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 1)); // 20 seconds, level 2
        
        // Buff nearby allies
        for (LivingEntity ally : nearbyAllies) {
            ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1)); // 15 seconds
            ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
            ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 0));
            
            // Visual effect on ally
            ally.getWorld().spawnParticle(Particle.ENCHANT, 
                ally.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 1.0);
        }
        
        // Debuff and damage enemies
        for (Player enemy : nearbyEnemies) {
            // Damage from intimidation
            enemy.damage(ENEMY_DAMAGE);
            
            // Debuff effects
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 1)); // 10 seconds
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0)); // 10 seconds
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0)); // 5 seconds
            
            // Visual effect on enemy
            enemy.getWorld().spawnParticle(Particle.SMOKE, 
                enemy.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
            
            // No more spam messages - visual effects only
        }
        
        // Dramatic effects
        createBattleCommandEffect(loc);
        
        // Sound effects only - no spam messages
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.7f);
    }
    
    /**
     * Get nearby players within range
     */
    private List<Player> getNearbyPlayers(Location loc) {
        return getNearbyPlayers(loc, COMMAND_RANGE);
    }
    
    private List<Player> getNearbyPlayers(Location loc, double range) {
        return loc.getWorld().getNearbyEntities(loc, range, range, range).stream()
            .filter(entity -> entity instanceof Player)
            .map(entity -> (Player) entity)
            .filter(player -> !player.isDead() && player.getGameMode() != org.bukkit.GameMode.CREATIVE)
            .toList();
    }
    
    /**
     * Get nearby allied mobs
     */
    private List<LivingEntity> getNearbyAllies(Location loc) {
        return loc.getWorld().getNearbyEntities(loc, COMMAND_RANGE, COMMAND_RANGE, COMMAND_RANGE).stream()
            .filter(entity -> entity instanceof LivingEntity)
            .filter(entity -> !(entity instanceof Player))
            .map(entity -> (LivingEntity) entity)
            .filter(entity -> !entity.isDead())
            .toList();
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createCommandAura(Location loc) {
        // Subtle passive effect
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.5, 0), 2, 
            1.0, 0.5, 1.0, 0.1);
    }
    
    private void createBattleCommandEffect(Location loc) {
        // Dramatic command explosion
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 30, 
            COMMAND_RANGE * 0.8, 2.0, COMMAND_RANGE * 0.8, 0.1);
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 
            COMMAND_RANGE * 0.6, 1.5, COMMAND_RANGE * 0.6, 0.1);
        
        // Sound effects
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.2f);
    }
    
    private void createFinalWarCry(Location loc) {
        // Dramatic final effect
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 2.0, 1.0, 2.0, 0.2);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 50, 4.0, 2.0, 4.0, 0.15);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 2.0f, 0.6f);
        
        // Notify nearby players of the fall
        for (Player player : getNearbyPlayers(loc, 20.0)) {
            actionBarManager.sendActionBarMessage(player,
                Component.text("⚔ The Warmaster has fallen! ⚔")
                    .color(NamedTextColor.GRAY),
                100); // 5 seconds
        }
    }
}