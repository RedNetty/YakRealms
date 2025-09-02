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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simplified Elementalist Elite Behavior
 * 
 * Signature Ability: Elemental Burst
 * - Creates a powerful elemental explosion that cycles through fire, ice, and lightning
 * - Applies different effects based on the current elemental phase
 * - Only activates during combat situations
 */
public class ElementalistBehavior implements MobBehavior {
    
    private static final String BEHAVIOR_ID = "elementalist";
    private static final int ELEMENTAL_BURST_COOLDOWN = 400; // 20 seconds
    private static final double BURST_RANGE = 10.0;
    private static final double BURST_DAMAGE = 8.0;
    
    private final Map<String, Long> lastUsed = new HashMap<>();
    private final Map<String, ElementalPhase> mobPhases = new HashMap<>();
    private final ActionBarMessageManager actionBarManager = ActionBarMessageManager.getInstance();
    
    private enum ElementalPhase {
        FIRE(Particle.FLAME, NamedTextColor.RED, "Fire Burst"),
        ICE(Particle.SNOWFLAKE, NamedTextColor.AQUA, "Ice Burst"), 
        LIGHTNING(Particle.END_ROD, NamedTextColor.YELLOW, "Lightning Burst");
        
        private final Particle particle;
        private final NamedTextColor color;
        private final String name;
        
        ElementalPhase(Particle particle, NamedTextColor color, String name) {
            this.particle = particle;
            this.color = color;
            this.name = name;
        }
        
        public Particle getParticle() { return particle; }
        public NamedTextColor getColor() { return color; }
        public String getName() { return name; }
        
        public ElementalPhase getNext() {
            return switch (this) {
                case FIRE -> ICE;
                case ICE -> LIGHTNING;
                case LIGHTNING -> FIRE;
            };
        }
    }
    
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
        // Initialize with random elemental phase
        ElementalPhase initialPhase = ElementalPhase.values()[ThreadLocalRandom.current().nextInt(ElementalPhase.values().length)];
        mobPhases.put(mob.getUniqueMobId(), initialPhase);
        
        // Apply elemental resistance
        LivingEntity entity = mob.getEntity();
        if (entity != null) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0));
        }
    }
    
    @Override
    public void onTick(CustomMob mob) {
        // Passive elemental aura - very subtle
        if (System.currentTimeMillis() % 6000 < 50) { // Every 6 seconds
            ElementalPhase currentPhase = mobPhases.get(mob.getUniqueMobId());
            if (currentPhase != null) {
                createElementalAura(mob.getEntity().getLocation(), currentPhase);
            }
        }
    }
    
    @Override
    public void onPlayerDetected(CustomMob mob, Player player) {
        // 25% chance to use elemental burst when player detected
        if (ThreadLocalRandom.current().nextDouble() < 0.25) {
            attemptElementalBurst(mob);
        }
    }
    
    @Override
    public double onAttack(CustomMob mob, LivingEntity target, double damage) {
        // 20% chance to trigger elemental burst on attack
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            attemptElementalBurst(mob);
        }
        return damage; // Don't modify damage
    }
    
    @Override
    public void onDamage(CustomMob mob, double damage, LivingEntity attacker) {
        // 35% chance to use elemental burst when taking significant damage
        if (damage > 7.0 && ThreadLocalRandom.current().nextDouble() < 0.35) {
            attemptElementalBurst(mob);
        }
    }
    
    @Override
    public void onDeath(CustomMob mob, LivingEntity killer) {
        // Final elemental explosion
        ElementalPhase currentPhase = mobPhases.get(mob.getUniqueMobId());
        if (currentPhase != null) {
            createElementalistDeath(mob.getEntity().getLocation(), currentPhase);
        }
        
        // Clean up tracking
        lastUsed.remove(mob.getUniqueMobId());
        mobPhases.remove(mob.getUniqueMobId());
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority
    }
    
    @Override
    public void onRemove(CustomMob mob) {
        // Clean up tracking
        lastUsed.remove(mob.getUniqueMobId());
        mobPhases.remove(mob.getUniqueMobId());
    }
    
    /**
     * Attempt to use Elemental Burst ability with telegraph
     */
    private void attemptElementalBurst(CustomMob mob) {
        String mobId = mob.getUniqueMobId();
        long currentTime = System.currentTimeMillis();
        
        // Check cooldown
        Long lastUse = lastUsed.get(mobId);
        if (lastUse != null && (currentTime - lastUse) < ELEMENTAL_BURST_COOLDOWN * 50) {
            return; // Still on cooldown
        }
        
        LivingEntity elementalist = mob.getEntity();
        Location loc = elementalist.getLocation();
        
        // Get current phase (or initialize if missing)
        ElementalPhase currentPhase = mobPhases.get(mobId);
        if (currentPhase == null) {
            currentPhase = ElementalPhase.FIRE;
            mobPhases.put(mobId, currentPhase);
        }
        
        // Find nearby enemies
        List<Player> nearbyEnemies = getNearbyPlayers(loc);
        
        if (nearbyEnemies.isEmpty()) return;
        
        // Start cooldown immediately
        lastUsed.put(mobId, currentTime);
        
        // TELEGRAPH PHASE - 2.5 second warning
        createElementalBurstTelegraph(elementalist, loc, currentPhase, nearbyEnemies);
        
        // Create final variables for lambda
        final ElementalPhase finalPhase = currentPhase;
        final Location finalLoc = loc.clone();
        final List<Player> finalTargets = List.copyOf(nearbyEnemies);
        
        // Schedule the actual burst after telegraph
        com.rednetty.server.YakRealms.getInstance().getServer().getScheduler().runTaskLater(
            com.rednetty.server.YakRealms.getInstance(),
            () -> executeElementalBurst(mob, elementalist, finalLoc, finalPhase, finalTargets),
            50L // 2.5 seconds
        );
    }
    
    /**
     * Create telegraph warning for Elemental Burst
     */
    private void createElementalBurstTelegraph(LivingEntity elementalist, Location loc, 
                                             ElementalPhase phase, List<Player> targets) {
        // Visual telegraph - elemental energy building up
        for (int i = 0; i < 50; i++) {
            com.rednetty.server.YakRealms.getInstance().getServer().getScheduler().runTaskLater(
                com.rednetty.server.YakRealms.getInstance(),
                () -> {
                    if (elementalist.isValid()) {
                        loc.getWorld().spawnParticle(phase.getParticle(), 
                            loc.clone().add(0, 2, 0), 3, 0.8, 0.8, 0.8, 0.1);
                        loc.getWorld().spawnParticle(Particle.END_ROD, 
                            loc.clone().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.05);
                    }
                },
                i
            );
        }
        
        // Sound telegraph
        Sound telegraphSound = switch (phase) {
            case FIRE -> Sound.BLOCK_FIRE_AMBIENT;
            case ICE -> Sound.BLOCK_GLASS_HIT;
            case LIGHTNING -> Sound.ENTITY_CREEPER_PRIMED;
        };
        loc.getWorld().playSound(loc, telegraphSound, 1.5f, 1.2f);
        
        // One-time warning to players - NO SPAM
        for (Player player : targets) {
            actionBarManager.sendActionBarMessage(player,
                Component.text("⚡ ELEMENTALIST CHARGING " + phase.getName().toUpperCase() + "! MOVE AWAY! ⚡")
                    .color(phase.getColor()),
                50); // 2.5 seconds - matches telegraph time
        }
    }
    
    /**
     * Execute the actual Elemental Burst after telegraph
     */
    private void executeElementalBurst(CustomMob mob, LivingEntity elementalist, Location loc, 
                                     ElementalPhase currentPhase, List<Player> originalTargets) {
        if (!elementalist.isValid()) return;
        
        // Only affect players still in range
        for (Player enemy : originalTargets) {
            if (!enemy.isValid() || enemy.isDead() || 
                enemy.getLocation().distance(loc) > BURST_RANGE * 1.2) {
                continue; // Enemy escaped during telegraph
            }
            
            enemy.damage(BURST_DAMAGE);
            
            // Phase-specific effects
            switch (currentPhase) {
                case FIRE -> {
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0));
                    enemy.setFireTicks(100);
                }
                case ICE -> {
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1));
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 120, 0));
                }
                case LIGHTNING -> {
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                }
            }
        }
        
        // Visual and sound effects only
        createElementalBurstEffect(loc, currentPhase);
        Sound burstSound = switch (currentPhase) {
            case FIRE -> Sound.ENTITY_BLAZE_SHOOT;
            case ICE -> Sound.BLOCK_GLASS_BREAK;
            case LIGHTNING -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
        };
        loc.getWorld().playSound(loc, burstSound, 2.0f, 1.0f);
        
        // Cycle to next elemental phase
        ElementalPhase nextPhase = currentPhase.getNext();
        mobPhases.put(mob.getUniqueMobId(), nextPhase);
    }
    
    /**
     * Get nearby players within range
     */
    private List<Player> getNearbyPlayers(Location loc) {
        return getNearbyPlayers(loc, BURST_RANGE);
    }
    
    private List<Player> getNearbyPlayers(Location loc, double range) {
        return loc.getWorld().getNearbyEntities(loc, range, range, range).stream()
            .filter(entity -> entity instanceof Player)
            .map(entity -> (Player) entity)
            .filter(player -> !player.isDead() && player.getGameMode() != org.bukkit.GameMode.CREATIVE)
            .toList();
    }
    
    // ==================== VISUAL EFFECTS ====================
    
    private void createElementalAura(Location loc, ElementalPhase phase) {
        // Subtle passive effect based on elemental phase
        loc.getWorld().spawnParticle(phase.getParticle(), loc.clone().add(0, 0.5, 0), 4, 
            1.0, 0.5, 1.0, 0.02);
    }
    
    private void createElementalBurstEffect(Location loc, ElementalPhase phase) {
        // Dramatic elemental explosion
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 1.0, 1.0, 1.0, 0.1);
        loc.getWorld().spawnParticle(phase.getParticle(), loc, 40, 
            BURST_RANGE * 0.7, 2.0, BURST_RANGE * 0.7, 0.2);
        
        // Additional phase-specific effects
        switch (phase) {
            case FIRE -> {
                loc.getWorld().spawnParticle(Particle.LAVA, loc, 20, 
                    BURST_RANGE * 0.5, 1.5, BURST_RANGE * 0.5, 0.1);
            }
            case ICE -> {
                loc.getWorld().spawnParticle(Particle.BLOCK, loc, 30, 
                    BURST_RANGE * 0.6, 1.5, BURST_RANGE * 0.6, 0.1, 
                    org.bukkit.Material.ICE.createBlockData());
            }
            case LIGHTNING -> {
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 25, 
                    BURST_RANGE * 0.8, 3.0, BURST_RANGE * 0.8, 0.3);
            }
        }
    }
    
    private void createElementalistDeath(Location location, ElementalPhase phase) {
        // Dramatic death explosion with all elemental forces
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 5, 2.0, 1.0, 2.0, 0.2);
        location.getWorld().spawnParticle(Particle.FLAME, location, 25, 3.0, 2.0, 3.0, 0.15);
        location.getWorld().spawnParticle(Particle.SNOWFLAKE, location, 25, 3.0, 2.0, 3.0, 0.15);
        location.getWorld().spawnParticle(Particle.END_ROD, location, 25, 3.0, 2.0, 3.0, 0.15);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        
        // Visual effects only - no death messages
    }
}