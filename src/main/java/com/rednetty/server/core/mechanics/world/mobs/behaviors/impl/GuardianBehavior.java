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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simplified Guardian Elite Behavior
 * 
 * Signature Ability: Shield Fortress
 * - Creates a protective barrier that reflects damage and heals the guardian
 * - Provides temporary immunity and regeneration
 * - Only activates when taking significant damage or surrounded
 */
public class GuardianBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "guardian";
    private static final int SHIELD_FORTRESS_COOLDOWN = 450; // 22.5 seconds
    private static final double FORTRESS_RANGE = 6.0;
    private static final double REFLECTION_DAMAGE = 4.0;
    
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
        // Apply guardian buffs
        LivingEntity entity = mob.getEntity();
        if (entity != null) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0));
            
            // Give guardian equipment if applicable
            if (entity.getEquipment() != null) {
                entity.getEquipment().setItemInMainHand(new ItemStack(Material.SHIELD));
                entity.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                entity.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            }
        }
    }
    
    @Override
    public void onTick(CustomMob mob) {
        // Passive protective aura - very subtle
        if (System.currentTimeMillis() % 7000 < 50) { // Every 7 seconds
            createProtectiveAura(mob.getEntity().getLocation());
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        // 15% chance to use shield fortress when multiple enemies detected
        List<Player> nearbyEnemies = getNearbyPlayers(mob.getEntity().getLocation());
        if (nearbyEnemies.size() >= 2 && ThreadLocalRandom.current().nextDouble() < 0.15) {
            attemptShieldFortress(mob);
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        // 10% chance to use shield fortress on attack (defensive stance)
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            attemptShieldFortress(mob);
        }
        return damage; // Don't modify damage
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        // 40% chance to use shield fortress when taking significant damage
        if (damage > 6.0 && ThreadLocalRandom.current().nextDouble() < 0.4) {
            attemptShieldFortress(mob);
        }
        
        // Additional chance when health is low
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        if (healthPercent < 0.3 && ThreadLocalRandom.current().nextDouble() < 0.5) {
            attemptShieldFortress(mob);
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Final protective explosion
        createGuardianDeath(mob.getEntity().getLocation());
        
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
     * Attempt to use Shield Fortress ability with telegraph
     */
    private void attemptShieldFortress(CustomMob mob) {
        String mobId = mob.getUniqueMobId();
        long currentTime = System.currentTimeMillis();
        
        // Check cooldown
        Long lastUse = lastUsed.get(mobId);
        if (lastUse != null && (currentTime - lastUse) < SHIELD_FORTRESS_COOLDOWN * 50) {
            return; // Still on cooldown
        }
        
        LivingEntity guardian = mob.getEntity();
        Location loc = guardian.getLocation();
        
        // Find nearby enemies
        List<Player> nearbyEnemies = getNearbyPlayers(loc);
        
        if (nearbyEnemies.isEmpty()) return;
        
        // Start cooldown immediately
        lastUsed.put(mobId, currentTime);
        
        // TELEGRAPH PHASE - 3 second warning
        createShieldFortressTelegraph(guardian, loc, nearbyEnemies);
        
        // Schedule the actual fortress after telegraph
        com.rednetty.server.YakRealms.getInstance().getServer().getScheduler().runTaskLater(
            com.rednetty.server.YakRealms.getInstance(),
            () -> executeShieldFortress(mob, guardian, loc, nearbyEnemies),
            60L // 3 seconds
        );
    }
    
    /**
     * Create telegraph warning for Shield Fortress
     */
    private void createShieldFortressTelegraph(LivingEntity guardian, Location loc, List<Player> targets) {
        // Visual telegraph - fortress walls rising
        for (int i = 0; i < 60; i++) {
            com.rednetty.server.YakRealms.getInstance().getServer().getScheduler().runTaskLater(
                com.rednetty.server.YakRealms.getInstance(),
                () -> {
                    if (guardian.isValid()) {
                        loc.getWorld().spawnParticle(Particle.BLOCK, 
                            loc.clone().add(0, 0.5, 0), 3, 2.0, 0.5, 2.0, 0.1,
                            Material.IRON_BLOCK.createBlockData());
                        loc.getWorld().spawnParticle(Particle.ENCHANT, 
                            loc.clone().add(0, 1, 0), 2, 1.0, 1.0, 1.0, 0.05);
                    }
                },
                i
            );
        }
        
        // Sound telegraph
        loc.getWorld().playSound(loc, Sound.BLOCK_IRON_DOOR_CLOSE, 1.5f, 0.8f);
        
        // One-time warning to players - NO SPAM
        for (Player player : targets) {
            actionBarManager.sendActionBarMessage(player,
                Component.text("🛡 GUARDIAN RAISING SHIELD FORTRESS! PREPARE FOR REFLECTION! 🛡")
                    .color(NamedTextColor.BLUE),
                60); // 3 seconds - matches telegraph time
        }
    }
    
    /**
     * Execute the actual Shield Fortress after telegraph
     */
    private void executeShieldFortress(CustomMob mob, LivingEntity guardian, Location loc, List<Player> originalEnemies) {
        if (!guardian.isValid()) return;
        
        // Buff guardian
        guardian.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 3));
        guardian.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
        guardian.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 1));
        
        // Heal guardian
        double newHealth = Math.min(guardian.getMaxHealth(), guardian.getHealth() + 15.0);
        guardian.setHealth(newHealth);
        
        // Affect enemies still in range
        for (Player enemy : originalEnemies) {
            if (!enemy.isValid() || enemy.isDead() || 
                enemy.getLocation().distance(loc) > FORTRESS_RANGE * 1.5) {
                continue; // Enemy escaped during telegraph
            }
            
            enemy.damage(REFLECTION_DAMAGE);
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 0));
            
            // Visual effects only
            enemy.getWorld().spawnParticle(Particle.BLOCK, 
                enemy.getLocation().add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0.1,
                Material.IRON_BLOCK.createBlockData());
        }
        
        // Visual and sound effects only
        createShieldFortressEffect(loc);
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 2.0f, 1.2f);
        loc.getWorld().playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.8f);
    }
    
    /**
     * Get nearby players within range
     */
    private List<Player> getNearbyPlayers(Location loc) {
        return getNearbyPlayers(loc, FORTRESS_RANGE);
    }
    
    private List<Player> getNearbyPlayers(Location loc, double range) {
        return loc.getWorld().getNearbyEntities(loc, range, range, range).stream()
            .filter(entity -> entity instanceof Player)
            .map(entity -> (Player) entity)
            .filter(player -> !player.isDead() && player.getGameMode() != org.bukkit.GameMode.CREATIVE)
            .toList();
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createProtectiveAura(Location loc) {
        // Subtle passive effect
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 0.5, 0), 3, 
            1.2, 0.5, 1.2, 0.1);
    }
    
    private void createShieldFortressEffect(Location loc) {
        // Dramatic fortress formation
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, 50, 
            FORTRESS_RANGE * 0.8, 2.0, FORTRESS_RANGE * 0.8, 0.2,
            Material.IRON_BLOCK.createBlockData());
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 30, 
            FORTRESS_RANGE * 0.6, 1.5, FORTRESS_RANGE * 0.6, 0.1);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 
            FORTRESS_RANGE * 0.4, 1.0, FORTRESS_RANGE * 0.4, 0.05);
        
        // Additional metallic effects
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 8, 
            2.0, 2.0, 2.0, 0.1);
    }
    
    private void createGuardianDeath(Location location) {
        // Dramatic fortress collapse
        location.getWorld().spawnParticle(Particle.BLOCK, location, 60, 3.0, 2.0, 3.0, 0.3,
            Material.IRON_BLOCK.createBlockData());
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 3, 1.0, 1.0, 1.0, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_IRON_GOLEM_DEATH, 2.0f, 0.8f);
        
        // Visual effects only - no death messages
    }
}