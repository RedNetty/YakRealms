package com.rednetty.server.core.mechanics.world.mobs.abilities.impl;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.abilities.CombatContext;
import com.rednetty.server.core.mechanics.world.mobs.abilities.EliteAbility;
import com.rednetty.server.core.mechanics.world.mobs.combat.CombatFeedbackManager;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

/**
 * Elemental Phase Shift - A strategic multi-phase ability for Elementalist elites
 * 
 * Features strategic combat elements:
 * - Multi-phase telegraph system with escalating threat
 * - Clear counterplay windows between phases
 * - Environmental interaction and positioning importance
 * - Visual clarity for player decision making
 * - Adaptive difficulty based on player positioning
 */
public class ElementalPhaseShiftAbility extends EliteAbility {
    
    private enum ElementalPhase {
        FIRE("Fire", Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT, PotionEffectType.STRENGTH),
        ICE("Ice", Particle.SNOWFLAKE, Sound.BLOCK_GLASS_BREAK, PotionEffectType.SLOWNESS),
        LIGHTNING("Lightning", Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, PotionEffectType.SPEED);
        
        private final String name;
        private final Particle particle;
        private final Sound sound;
        private final PotionEffectType effect;
        
        ElementalPhase(String name, Particle particle, Sound sound, PotionEffectType effect) {
            this.name = name;
            this.particle = particle;
            this.sound = sound;
            this.effect = effect;
        }
        
        public String getName() { return name; }
        public Particle getParticle() { return particle; }
        public Sound getSound() { return sound; }
        public PotionEffectType getEffect() { return effect; }
        
        public ElementalPhase getNext() {
            ElementalPhase[] phases = values();
            return phases[(ordinal() + 1) % phases.length];
        }
    }
    
    private ElementalPhase currentPhase = ElementalPhase.FIRE;
    private int phaseCount = 0;
    private boolean phasesComplete = false;
    
    public ElementalPhaseShiftAbility() {
        super(
            "elemental_phase_shift",
            "Elemental Phase Shift", 
            AbilityType.ULTIMATE,
            400, // 20 second cooldown
            80,  // 4 second telegraph total (phases have internal timing)
            0.12 // 12% base chance
        );
    }
    
    @Override
    protected boolean meetsPrerequisites(CustomMob mob, List<Player> targets) {
        if (targets.isEmpty()) return false;
        
        // Require multiple targets to make it worth using ultimate ability
        return targets.size() >= 2;
    }
    
    @Override
    protected double applyContextualScaling(double baseChance, CustomMob mob, 
                                          List<Player> targets, CombatContext context) {
        double chance = baseChance;
        
        // Much more likely when facing multiple enemies
        if (targets.size() >= 4) {
            chance *= 2.0;
        } else if (targets.size() >= 3) {
            chance *= 1.5;
        }
        
        // More likely when elementalist is low on health
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        if (healthPercent <= 0.3) {
            chance *= 2.5; // Desperate ultimates
        } else if (healthPercent <= 0.6) {
            chance *= 1.8;
        }
        
        // Terrain considerations
        if (context.getTerrain() == CombatContext.TerrainType.OPEN) {
            chance *= 1.3; // More effective in open areas
        }
        
        return chance;
    }
    
    @Override
    public AbilityPriority getPriority(CustomMob mob, List<Player> targets, CombatContext context) {
        double healthPercent = mob.getEntity().getHealth() / mob.getEntity().getMaxHealth();
        
        // Critical priority when very low health
        if (healthPercent <= 0.25 && targets.size() >= 3) {
            return AbilityPriority.CRITICAL;
        }
        
        // High priority for crowd control
        if (targets.size() >= 4) {
            return AbilityPriority.HIGH;
        }
        
        return AbilityPriority.NORMAL;
    }
    
    @Override
    protected void startTelegraph(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        phasesComplete = false;
        phaseCount = 0;
        
        // Initial warning to all nearby players
        targets.forEach(player -> {
            CombatFeedbackManager.getInstance().handlePhaseAbility(
                player, displayName, 1, 3, telegraphDuration, mob);
        });
        
        // Create dramatic buildup effect
        Location center = entity.getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center.add(0, 2, 0), 5, 1, 1, 1, 0.1);
        
        // Start the phase sequence with proper timing
        executePhaseSequence(mob, targets, context);
    }
    
    @Override
    protected void executeAbility(CustomMob mob, List<Player> targets, CombatContext context) {
        // The actual execution is handled by the phase sequence
        // This method is called after all phases complete
        if (phasesComplete) {
            executePhaseFinale(mob, targets, context);
        }
    }
    
    private void executePhaseSequence(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        // Phase 1: Fire Phase (0-1.5 seconds)
        executeElementalPhase(mob, targets, ElementalPhase.FIRE, 0L, context);
        
        // Phase 2: Ice Phase (1.5-3 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isValid() && isCharging) {
                    executeElementalPhase(mob, targets, ElementalPhase.ICE, 0L, context);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 30L);
        
        // Phase 3: Lightning Phase (3-4.5 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isValid() && isCharging) {
                    executeElementalPhase(mob, targets, ElementalPhase.LIGHTNING, 0L, context);
                    phasesComplete = true;
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 60L);
    }
    
    private void executeElementalPhase(CustomMob mob, List<Player> targets, ElementalPhase phase, 
                                     long delay, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        phaseCount++;
        currentPhase = phase;
        
        // Update phase metadata
        entity.setMetadata("elemental_phase", 
            new org.bukkit.metadata.FixedMetadataValue(YakRealms.getInstance(), phase.name()));
        
        // Phase-specific warning
        targets.forEach(player -> {
            CombatFeedbackManager.getInstance().handlePhaseAbility(
                player, phase.getName() + " Phase", phaseCount, 3, 30L, mob);
        });
        
        Location center = entity.getLocation();
        
        // Phase activation effects
        center.getWorld().playSound(center, phase.getSound(), 1.0f, 1.0f);
        center.getWorld().spawnParticle(phase.getParticle(), center.add(0, 1, 0), 30, 2, 2, 2, 0.1);
        
        // Apply phase-specific mob effects
        entity.removePotionEffect(PotionEffectType.STRENGTH);
        entity.removePotionEffect(PotionEffectType.SLOWNESS);
        entity.removePotionEffect(PotionEffectType.SPEED);
        entity.addPotionEffect(new PotionEffect(phase.getEffect(), 200, 1, false, false));
        
        // Execute phase-specific attack pattern
        new BukkitRunnable() {
            int ticks = 0;
            final int phaseDuration = 30; // 1.5 seconds per phase
            
            @Override
            public void run() {
                if (ticks >= phaseDuration || !entity.isValid()) {
                    cancel();
                    return;
                }
                
                // Continuous phase effects
                center.getWorld().spawnParticle(phase.getParticle(), 
                    center.add(0, 1, 0), 5, 1, 1, 1, 0.05);
                
                // Phase-specific attacks every 0.5 seconds
                if (ticks % 10 == 0) {
                    executePhaseAttack(mob, targets, phase, context);
                }
                
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), delay, 1L);
    }
    
    private void executePhaseAttack(CustomMob mob, List<Player> targets, ElementalPhase phase, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null || targets.isEmpty()) return;
        
        Location center = entity.getLocation();
        
        switch (phase) {
            case FIRE -> {
                // Fire phase: Create expanding fire rings
                createFireRings(center, mob.getTier());
                
                // Damage players in fire areas
                targets.stream()
                    .filter(p -> p.getLocation().distance(center) <= 8.0)
                    .forEach(p -> {
                        p.damage(getScaledDamage(8.0, mob.getTier()), entity);
                        p.setFireTicks(60); // 3 seconds of fire
                        
                        CombatFeedbackManager.getInstance().sendAbilityHit(
                            p, "Fire Phase", 8.0, p.getMaxHealth());
                    });
            }
            
            case ICE -> {
                // Ice phase: Slow and damage players, create ice hazards
                createIceSpikes(center, mob.getTier());
                
                targets.stream()
                    .filter(p -> p.getLocation().distance(center) <= 10.0)
                    .forEach(p -> {
                        p.damage(getScaledDamage(6.0, mob.getTier()), entity);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 2, false, false));
                        
                        CombatFeedbackManager.getInstance().sendAbilityHit(
                            p, "Ice Phase", 6.0, p.getMaxHealth());
                    });
            }
            
            case LIGHTNING -> {
                // Lightning phase: Telegraphed lightning strikes
                executeTelegraphedLightning(center, targets, mob);
            }
        }
    }
    
    private void executePhaseFinale(CustomMob mob, List<Player> targets, CombatContext context) {
        LivingEntity entity = mob.getEntity();
        if (entity == null) return;
        
        Location center = entity.getLocation();
        
        // Final warning
        targets.forEach(player -> {
            CombatFeedbackManager.getInstance().sendMessage(
                player, 
                "ELEMENTAL CONVERGENCE! All phases combining!",
                CombatFeedbackManager.MessagePriority.EMERGENCY,
                CombatFeedbackManager.ThreatLevel.LETHAL);
        });
        
        // Massive tri-elemental explosion
        center.getWorld().playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.8f);
        center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 8, 2, 2, 2, 0.2);
        
        // All elemental effects at once
        center.getWorld().spawnParticle(Particle.FLAME, center, 50, 3, 3, 3, 0.1);
        center.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 40, 3, 3, 3, 0.1);
        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 30, 3, 3, 3, 0.2);
        
        // Finale damage - massive area effect
        targets.forEach(player -> {
            double distance = player.getLocation().distance(center);
            if (distance <= 12.0) {
                double damageMultiplier = Math.max(0.3, 1.2 - (distance / 12.0));
                double finaleDamage = getScaledDamage(25.0, mob.getTier()) * damageMultiplier;
                
                player.damage(finaleDamage, entity);
                
                // All elemental effects
                player.setFireTicks(100); // 5 seconds fire
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, false, false));
                
                CombatFeedbackManager.getInstance().sendAbilityHit(
                    player, "Elemental Convergence", finaleDamage, player.getMaxHealth());
            }
        });
    }
    
    // ==================== PHASE-SPECIFIC EFFECTS ====================
    
    private void createFireRings(Location center, int tier) {
        new BukkitRunnable() {
            double radius = 1.0;
            final double maxRadius = 6.0 + tier;
            
            @Override
            public void run() {
                if (radius >= maxRadius) {
                    cancel();
                    return;
                }
                
                // Create ring of fire particles
                for (int i = 0; i < 24; i++) {
                    double angle = (2 * Math.PI * i) / 24;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    Location fireLoc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
                    
                    center.getWorld().spawnParticle(Particle.FLAME, fireLoc, 3, 0.2, 0.2, 0.2, 0.02);
                }
                
                radius += 0.8;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 3L);
    }
    
    private void createIceSpikes(Location center, int tier) {
        // Create ice spikes at random positions around the center
        for (int i = 0; i < 6 + tier; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double distance = 2.0 + Math.random() * 6.0;
            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);
            Location spikeLoc = new Location(center.getWorld(), x, center.getY(), z);
            
            // Animate ice spike growing
            new BukkitRunnable() {
                double height = 0;
                final double maxHeight = 3.0;
                
                @Override
                public void run() {
                    if (height >= maxHeight) {
                        cancel();
                        return;
                    }
                    
                    Location particleLoc = spikeLoc.clone().add(0, height, 0);
                    spikeLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, particleLoc, 5, 0.3, 0.1, 0.3, 0.02);
                    
                    height += 0.3;
                }
            }.runTaskTimer(YakRealms.getInstance(), i * 2L, 2L);
        }
    }
    
    private void executeTelegraphedLightning(Location center, List<Player> targets, CustomMob mob) {
        if (targets.isEmpty()) return;
        
        // Mark lightning strike locations with telegraph
        java.util.List<Location> strikeLocations = new java.util.ArrayList<>();
        for (Player player : targets) {
            Location strikeLoc = player.getLocation().clone().add(0, 8, 0); // Above player
            strikeLocations.add(strikeLoc);
            
            // Show telegraph warning to player
            CombatFeedbackManager.getInstance().sendMessage(
                player, 
                "Lightning Strike incoming at your location!",
                CombatFeedbackManager.MessagePriority.HIGH,
                CombatFeedbackManager.ThreatLevel.MAJOR);
            
            // Visual warning - red particles above player
            player.getWorld().spawnParticle(Particle.DUST, strikeLoc, 
                20, 1, 1, 1, new Particle.DustOptions(org.bukkit.Color.YELLOW, 3.0f));
            player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.8f, 1.5f);
        }
        
        // Execute strikes after telegraph delay
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < strikeLocations.size() && i < targets.size(); i++) {
                    Player player = targets.get(i);
                    Location strikeLoc = strikeLocations.get(i);
                    
                    // Check if player moved away (reward dodging)
                    if (player.getLocation().distance(strikeLoc.subtract(0, 8, 0)) <= 2.0) {
                        // Player didn't dodge - take damage
                        player.damage(getScaledDamage(12.0, mob.getTier()), mob.getEntity());
                        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
                        
                        CombatFeedbackManager.getInstance().sendAbilityHit(
                            player, "Lightning Strike", 12.0, player.getMaxHealth());
                            
                        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);
                    } else {
                        // Player successfully dodged
                        CombatFeedbackManager.getInstance().sendMessage(
                            player, 
                            "Lightning strike dodged!",
                            CombatFeedbackManager.MessagePriority.LOW,
                            CombatFeedbackManager.ThreatLevel.MINOR);
                    }
                    
                    // Visual lightning effect regardless
                    strikeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, 
                        strikeLoc, 25, 0.5, 4, 0.5, 0.3);
                    strikeLoc.getWorld().playSound(strikeLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 20L); // 1 second telegraph delay
    }
    
    @Override
    public boolean canBeInterrupted() {
        // Can only be interrupted during the first phase
        return phaseCount <= 1;
    }
    
    @Override
    public void onInterrupt(CustomMob mob, Player interrupter) {
        if (canBeInterrupted()) {
            super.onInterrupt(mob, interrupter);
            phasesComplete = true; // Stop the phase sequence
            
            CombatFeedbackManager.getInstance().sendCounterplaySuccess(
                interrupter, "Elemental Phase Shift interruption");
        }
    }
    
    @Override
    public String getDescription() {
        return "A devastating multi-phase elemental ability that cycles through fire, ice, and lightning attacks before a massive finale.";
    }
    
    @Override
    public String getTelegraphMessage() {
        return "is gathering elemental energies for a massive phase shift!";
    }
}