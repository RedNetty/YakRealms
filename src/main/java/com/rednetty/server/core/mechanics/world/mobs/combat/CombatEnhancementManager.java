package com.rednetty.server.core.mechanics.world.mobs.combat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat Enhancement Manager - Makes combat feel more responsive and satisfying
 * 
 * Features:
 * - Hit feedback with visual and audio cues
 * - Dynamic damage indicators
 * - Responsive mob reactions to damage
 * - Critical hit system with special effects
 * - Combat momentum mechanics
 * - Interactive dodge/parry windows
 */
public class CombatEnhancementManager implements Listener {
    
    private static CombatEnhancementManager instance;
    
    // Combat state tracking
    private final Map<UUID, CombatState> combatStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> combatCombo = new ConcurrentHashMap<>();
    
    // Configuration
    private static final double CRITICAL_HIT_CHANCE = 0.15; // 15% base crit chance
    private static final double COMBO_DAMAGE_BONUS = 0.1; // 10% per combo stack
    private static final int MAX_COMBO_STACKS = 5;
    private static final long COMBO_TIMEOUT = 3000; // 3 seconds
    private static final long HIT_FEEDBACK_COOLDOWN = 150; // 0.15 seconds
    
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    
    private CombatEnhancementManager() {
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
        startCombatManagementTask();
    }
    
    public static CombatEnhancementManager getInstance() {
        if (instance == null) {
            instance = new CombatEnhancementManager();
        }
        return instance;
    }
    
    // ==================== EVENT HANDLERS ====================
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        
        // Enhanced hit feedback
        processHitFeedback(player, target, event.getFinalDamage());
        
        // Check for critical hits
        if (shouldCriticalHit(player, target)) {
            processCriticalHit(player, target, event);
        }
        
        // Update combat combo
        updateCombatCombo(player, target);
        
        // Mob reaction to being hit
        if (isCustomMob(target)) {
            processMobHitReaction(target, player, event.getFinalDamage());
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        
        if (killer != null) {
            // Satisfying kill feedback
            processKillFeedback(killer, entity);
            
            // Reset combat state
            combatStates.remove(killer.getUniqueId());
            combatCombo.remove(killer.getUniqueId());
        }
        
        // Clean up combat data
        combatStates.remove(entity.getUniqueId());
    }
    
    // ==================== HIT FEEDBACK SYSTEM ====================
    
    private void processHitFeedback(Player player, LivingEntity target, double damage) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Check feedback cooldown
        if (lastHitTime.getOrDefault(playerId, 0L) + HIT_FEEDBACK_COOLDOWN > currentTime) {
            return;
        }
        lastHitTime.put(playerId, currentTime);
        
        // Visual feedback
        createHitParticles(target.getLocation(), damage);
        
        // Audio feedback
        float pitch = Math.min(2.0f, 1.0f + (float)(damage / 20.0));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, pitch);
        
        // Damage indicator
        showDamageIndicator(player, target, damage);
        
        // Screen shake effect for big hits
        if (damage >= 15.0) {
            createScreenShakeEffect(player);
        }
    }
    
    private void createHitParticles(Location location, double damage) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Subtle base hit particles - reduced from original
        int particleCount = Math.max(1, Math.min(4, (int)(damage / 5)));
        world.spawnParticle(Particle.CRIT, location.clone().add(0, 1, 0), 
            particleCount, 0.3, 0.3, 0.3, 0.05);
        
        // Very subtle additional effects for heavy hits only
        if (damage >= 25.0) {
            world.spawnParticle(Particle.DAMAGE_INDICATOR, location.clone().add(0, 1.2, 0),
                3, 0.4, 0.3, 0.4, 0.05);
        }
    }
    
    private void showDamageIndicator(Player player, LivingEntity target, double damage) {
        String damageText = String.format("%.1f", damage);
        String color = getDamageColor(damage);
        
        ActionBarUtil.addUniqueTemporaryMessage(player, 
            color + "⚡ " + damageText + " damage! ⚡", 30L);
    }
    
    private String getDamageColor(double damage) {
        if (damage >= 30.0) return "§4§l"; // Dark red bold
        if (damage >= 20.0) return "§c§l"; // Red bold
        if (damage >= 15.0) return "§6§l"; // Gold bold
        if (damage >= 10.0) return "§e";   // Yellow
        return "§f"; // White
    }
    
    private void createScreenShakeEffect(Player player) {
        // Very subtle screen shake - reduced intensity
        new BukkitRunnable() {
            int ticks = 0;
            final int duration = 3; // Reduced to 0.15 seconds
            
            @Override
            public void run() {
                if (ticks >= duration || !player.isOnline()) {
                    cancel();
                    return;
                }
                
                // Much smaller camera adjustments - barely noticeable
                float yawOffset = (random.nextFloat() - 0.5f) * 1.5f;
                float pitchOffset = (random.nextFloat() - 0.5f) * 0.8f;
                
                Location loc = player.getLocation();
                loc.setYaw(loc.getYaw() + yawOffset);
                loc.setPitch(Math.max(-90, Math.min(90, loc.getPitch() + pitchOffset)));
                
                player.teleport(loc);
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    // ==================== CRITICAL HIT SYSTEM ====================
    
    private boolean shouldCriticalHit(Player player, LivingEntity target) {
        double critChance = CRITICAL_HIT_CHANCE;
        
        // Bonus crit chance for combo
        int combo = combatCombo.getOrDefault(player.getUniqueId(), 0);
        critChance += combo * 0.02; // +2% per combo stack
        
        // Bonus crit chance when target is low health
        double healthPercent = target.getHealth() / target.getMaxHealth();
        if (healthPercent <= 0.25) {
            critChance += 0.1; // +10% crit chance on low health enemies
        }
        
        return random.nextDouble() < critChance;
    }
    
    private void processCriticalHit(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        // Increase damage
        double baseDamage = event.getDamage();
        event.setDamage(baseDamage * 1.8); // 80% more damage
        
        // Dramatic effects
        createCriticalHitEffects(target.getLocation());
        
        // Audio feedback
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.3f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.8f);
        
        // Visual feedback
        ActionBarUtil.addUniqueTemporaryMessage(player, 
            "§6§l✦ CRITICAL HIT! ✦ §e" + String.format("%.1f", event.getFinalDamage()) + " damage!", 60L);
        
        // Screen flash effect
        player.sendTitle("", "§6§l✦ CRITICAL! ✦", 0, 10, 5);
    }
    
    private void createCriticalHitEffects(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // More subtle critical hit effects
        world.spawnParticle(Particle.ENCHANTED_HIT, location.clone().add(0, 1, 0), 8, 0.6, 0.6, 0.6, 0.1);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, location.clone().add(0, 1.2, 0), 6, 0.5, 0.3, 0.5, 0.05);
        
        // Subtle golden sparkles - reduced count
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0.05);
    }
    
    // ==================== COMBO SYSTEM ====================
    
    private void updateCombatCombo(Player player, LivingEntity target) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        CombatState state = combatStates.computeIfAbsent(playerId, k -> new CombatState());
        
        // Check if this continues a combo (same target within timeout)
        if (state.lastTarget != null && 
            state.lastTarget.equals(target.getUniqueId()) &&
            currentTime - state.lastHitTime <= COMBO_TIMEOUT) {
            
            // Increase combo
            int newCombo = Math.min(MAX_COMBO_STACKS, combatCombo.getOrDefault(playerId, 0) + 1);
            combatCombo.put(playerId, newCombo);
            
            if (newCombo >= 2) {
                // Show combo feedback
                ActionBarUtil.addUniqueTemporaryMessage(player,
                    "§e⚡ §6§l" + newCombo + "x COMBO! §e⚡", 40L);
                
                // Combo sound
                float pitch = 1.0f + (newCombo * 0.2f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, pitch);
            }
        } else {
            // Reset combo for new target
            combatCombo.put(playerId, 1);
        }
        
        // Update combat state
        state.lastTarget = target.getUniqueId();
        state.lastHitTime = currentTime;
        combatStates.put(playerId, state);
    }
    
    // ==================== MOB REACTION SYSTEM ====================
    
    private void processMobHitReaction(LivingEntity mob, Player attacker, double damage) {
        // Visual hit reaction
        createMobHitReaction(mob, attacker, damage);
        
        // Behavioral reactions based on damage
        if (damage >= mob.getMaxHealth() * 0.15) { // 15% of max health
            // Big hit - stagger effect
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false));
            
            // Knockback
            Vector knockback = attacker.getLocation().getDirection().multiply(0.5);
            knockback.setY(0.2);
            mob.setVelocity(knockback);
        }
        
        // Low health desperation mechanics
        double healthPercent = mob.getHealth() / mob.getMaxHealth();
        if (healthPercent <= 0.25 && !mob.hasPotionEffect(PotionEffectType.STRENGTH)) {
            // Enrage at low health
            mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1, false, false));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, false, false));
            
            // Visual enrage effect
            createEnrageEffect(mob.getLocation());
            
            // Notify nearby players
            mob.getWorld().getNearbyEntities(mob.getLocation(), 10, 5, 10)
                .stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .forEach(p -> {
                    ActionBarUtil.addUniqueTemporaryMessage(p,
                        "§c§l⚠ " + getMobName(mob) + " §4§lENRAGED! §c§l⚠", 80L);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.5f);
                });
        }
    }
    
    private void createMobHitReaction(LivingEntity mob, Player attacker, double damage) {
        Location mobLoc = mob.getLocation();
        
        // Impact particles
        mobLoc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, 
            mobLoc.clone().add(0, mob.getHeight() / 2, 0), 
            (int)(damage / 3), 0.3, 0.3, 0.3, 0.1);
        
        // Blood effect for heavy hits
        if (damage >= 10.0) {
            mobLoc.getWorld().spawnParticle(Particle.BLOCK, mobLoc.clone().add(0, 1, 0),
                8, 0.5, 0.5, 0.5, 0.1, Material.REDSTONE_BLOCK.createBlockData());
        }
    }
    
    private void createEnrageEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Dramatic enrage effects
        world.spawnParticle(Particle.FLAME, location.clone().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, 1, 0), 20, 1, 1, 1, 0.05);
        world.playSound(location, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
    }
    
    // ==================== KILL FEEDBACK ====================
    
    private void processKillFeedback(Player killer, LivingEntity killed) {
        // Satisfying kill effects
        createKillEffects(killed.getLocation());
        
        // Audio feedback
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
        
        // Visual feedback
        String mobName = getMobName(killed);
        ActionBarUtil.addUniqueTemporaryMessage(killer, 
            "§a§l✓ " + mobName + " §2DEFEATED! §a✓", 100L);
        
        // Combo bonus feedback
        int combo = combatCombo.getOrDefault(killer.getUniqueId(), 0);
        if (combo >= 3) {
            killer.sendTitle("", "§6§l" + combo + "x COMBO KILL!", 0, 30, 10);
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
    
    private void createKillEffects(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Victory particles
        world.spawnParticle(Particle.FIREWORK, location.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0, 1, 0), 10, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0, 1, 0), 8, 1, 1, 1, 0.0);
    }
    
    // ==================== UTILITY METHODS ====================
    
    private boolean isCustomMob(LivingEntity entity) {
        return entity.hasMetadata("custommob") || entity.hasMetadata("type");
    }
    
    private String getMobName(LivingEntity entity) {
        if (entity.getCustomName() != null) {
            return ChatColor.stripColor(entity.getCustomName());
        }
        return entity.getType().toString().replace("_", " ").toLowerCase();
    }
    
    // ==================== BACKGROUND TASKS ====================
    
    private void startCombatManagementTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                
                // Clean up expired combos
                combatCombo.entrySet().removeIf(entry -> {
                    CombatState state = combatStates.get(entry.getKey());
                    return state == null || currentTime - state.lastHitTime > COMBO_TIMEOUT;
                });
                
                // Clean up old combat states
                combatStates.entrySet().removeIf(entry -> 
                    currentTime - entry.getValue().lastHitTime > COMBO_TIMEOUT * 2);
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 60L); // Every 3 seconds
    }
    
    // ==================== DATA CLASSES ====================
    
    private static class CombatState {
        public UUID lastTarget;
        public long lastHitTime;
        
        public CombatState() {
            this.lastHitTime = System.currentTimeMillis();
        }
    }
}