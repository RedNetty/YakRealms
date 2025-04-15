package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.mechanics.combat.CombatMechanics;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all combat-related events including damage processing,
 * combat tagging, and PvP interactions.
 */
public class CombatListener extends BaseListener {

    // Map to track combat state
    private final ConcurrentHashMap<String, Long> combatMap = new ConcurrentHashMap<>();

    // Map to track mob attack cooldowns to prevent spamming
    private final ConcurrentHashMap<UUID, Long> mobAttackCooldown = new ConcurrentHashMap<>();

    // Map to track fire damage cooldowns
    private final ConcurrentHashMap<UUID, Long> fireDamageCooldown = new ConcurrentHashMap<>();

    // Reference to other systems
    private final YakPlayerManager playerManager;
    private final CombatMechanics combatMechanics;
    private final AlignmentMechanics alignmentMechanics;

    /**
     * Initializes the combat listener with required dependencies
     */
    public CombatListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
        this.combatMechanics = plugin.getCombatMechanics();
        this.alignmentMechanics = AlignmentMechanics.getInstance();
    }

    @Override
    public void initialize() {
        logger.info("Combat listener initialized");
    }

    /**
     * Checks if a player is in combat
     *
     * @param player The player to check
     * @return true if the player is in combat
     */
    public boolean isInCombat(Player player) {
        if (combatMap.containsKey(player.getName())
                && System.currentTimeMillis() - combatMap.get(player.getName()) <= 10000) {
            return true;
        }
        return false;
    }

    /**
     * Gets the remaining combat time in seconds
     *
     * @param player The player to check
     * @return Remaining combat time in seconds
     */
    public int getRemainingCombatTime(Player player) {
        if (isInCombat(player)) {
            long combatStartTime = combatMap.get(player.getName());
            long currentTime = System.currentTimeMillis();
            long remainingTime = combatStartTime + 10000 - currentTime;
            int remainingSeconds = (int) (remainingTime / 1000);
            return Math.max(remainingSeconds, 0);
        } else {
            return 0;
        }
    }

    /**
     * Place player into combat state
     *
     * @param player The player to tag
     */
    public void tagPlayer(Player player) {
        combatMap.put(player.getName(), System.currentTimeMillis());
    }

    /**
     * Tag players involved in PvP combat
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCombatTag(EntityDamageByEntityEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        if (event.getDamage() <= 0.0) {
            return;
        }

        // Handle direct player attacks
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();

            // Check for safe zone
            if (AlignmentMechanics.isSafeZone(attacker.getLocation()) ||
                    AlignmentMechanics.isSafeZone(event.getEntity().getLocation())) {
                event.setCancelled(true);
                return;
            }

            // Tag the attacker
            tagPlayer(attacker);
        }

        // Handle projectile attacks
        else if (event.getDamager() instanceof Projectile && event.getEntity() instanceof Player) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                Player attacker = (Player) projectile.getShooter();
                tagPlayer(attacker);
            }
        }
    }

    /**
     * Handle damage tag for non-PvP damage
     */
    @EventHandler
    public void onDamageTag(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        if (event.getDamage() <= 0.0) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Don't tag on fall damage
        if (event.getCause() != DamageCause.FALL) {
            alignmentMechanics.markCombatTagged(player);
        }
    }

    /**
     * Handle damage tag when a player attacks
     */
    @EventHandler
    public void onAttackTag(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        if (event.getDamage() <= 0.0) {
            return;
        }

        Player player = (Player) event.getDamager();
        alignmentMechanics.markCombatTagged(player);
    }

    /**
     * Prevent mobs from spamming attacks too quickly
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMobAttackCooldown(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof LivingEntity) ||
                event.getDamager() instanceof Player) {
            return;
        }

        LivingEntity attacker = (LivingEntity) event.getDamager();
        UUID attackerId = attacker.getUniqueId();

        // Allow MagmaCube to attack without cooldown
        if (attacker instanceof MagmaCube) {
            return;
        }

        // Check if the mob is on cooldown
        if (mobAttackCooldown.containsKey(attackerId) &&
                System.currentTimeMillis() - mobAttackCooldown.get(attackerId) < 1000) {
            event.setDamage(0.0);
            event.setCancelled(true);
            return;
        }

        // Set attack cooldown
        mobAttackCooldown.put(attackerId, System.currentTimeMillis());
    }

    /**
     * Handle percentage-based environmental damage
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) event.getEntity();
        double maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double damage = event.getDamage();
        DamageCause cause = event.getCause();

        if (damage <= 0.0 || isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        switch (cause) {
            case FIRE:
            case FIRE_TICK:
            case LAVA:
                handleFireDamage(event, entity, maxHealth);
                break;
            case POISON:
                handlePoisonDamage(event, entity, maxHealth);
                break;
            case DROWNING:
                handleDrowningDamage(event, entity, maxHealth);
                break;
            case WITHER:
                event.setCancelled(true);
                event.setDamage(0.0);
                // Remove wither effect
                if (entity.hasPotionEffect(org.bukkit.potion.PotionEffectType.WITHER)) {
                    entity.removePotionEffect(org.bukkit.potion.PotionEffectType.WITHER);
                }
                break;
            case VOID:
                handleVoidDamage(event, entity);
                break;
            case FALL:
                handleFallDamage(event, entity, maxHealth);
                break;
        }
    }

    /**
     * Handle fire/lava damage with proper throttling
     */
    private void handleFireDamage(EntityDamageEvent event, LivingEntity entity, double maxHealth) {
        UUID entityId = entity.getUniqueId();

        // Apply fire damage throttling
        if (!fireDamageCooldown.containsKey(entityId) ||
                System.currentTimeMillis() - fireDamageCooldown.get(entityId) > 500) {

            fireDamageCooldown.put(entityId, System.currentTimeMillis());
            double multiplier = (event.getCause().equals(DamageCause.FIRE) ||
                    event.getCause().equals(DamageCause.LAVA)) ? 0.03 : 0.01;
            double damage = Math.max(maxHealth * multiplier, 1.0);
            event.setDamage(damage);
        } else {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
    }

    /**
     * Handle poison damage (percentage based)
     */
    private void handlePoisonDamage(EntityDamageEvent event, LivingEntity entity, double maxHealth) {
        double multiplier = 0.01;
        double damage;

        if (maxHealth * multiplier >= entity.getHealth()) {
            // Prevent death by poison (leave at 1 health)
            damage = entity.getHealth() - 1.0;
        } else if (maxHealth * multiplier < 1.0) {
            damage = 1.0;
        } else {
            damage = maxHealth * multiplier;
        }

        event.setDamage(damage);
    }

    /**
     * Handle drowning damage (percentage based)
     */
    private void handleDrowningDamage(EntityDamageEvent event, LivingEntity entity, double maxHealth) {
        double multiplier = 0.04;
        double damage = Math.max(maxHealth * multiplier, 1.0);
        event.setDamage(damage);
    }

    /**
     * Handle void damage with teleport to safety
     */
    private void handleVoidDamage(EntityDamageEvent event, LivingEntity entity) {
        event.setDamage(0.0);
        event.setCancelled(true);

        if (entity instanceof Player) {
            Player player = (Player) entity;
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            if (yakPlayer != null && "CHAOTIC".equals(yakPlayer.getAlignment())) {
                // Teleport chaotic players to random spawn point
                Location randomSpawn = generateRandomSpawnPoint(player.getName());
                player.teleport(randomSpawn);
            } else {
                // Teleport to main spawn
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.teleport(player.getWorld().getSpawnLocation());
                });
            }
        }
    }

    /**
     * Handle fall damage (percentage based)
     */
    private void handleFallDamage(EntityDamageEvent event, LivingEntity entity, double maxHealth) {
        double multiplier = event.getDamage() * maxHealth * 0.02;
        double damage;

        if (multiplier >= entity.getHealth()) {
            // Prevent instakill from fall
            damage = entity.getHealth() - 1.0;
        } else if (multiplier < 1.0) {
            damage = 1.0;
        } else {
            damage = multiplier;
        }

        event.setDamage(damage);
    }

    /**
     * Handle damage in safe zones (prevent all damage)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSafeZoneDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) event.getEntity();

        // Allow damage to DPS dummy entities
        if (entity.getType().equals(EntityType.ARMOR_STAND) ||
                (entity.getCustomName() != null && entity.getCustomName().contains("DPS Dummy"))) {
            return;
        }

        // Cancel damage in safe zones
        if (AlignmentMechanics.isSafeZone(entity.getLocation())) {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
    }

    /**
     * Prevent entities in safe zones from dealing damage
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSafeZoneAttacker(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        // Allow damage to DPS dummy entities
        if (event.getEntity().getType().equals(EntityType.ARMOR_STAND) ||
                (event.getEntity().getCustomName() != null &&
                        event.getEntity().getCustomName().contains("DPS Dummy"))) {
            return;
        }

        // Cancel damage from entities in safe zones
        if (AlignmentMechanics.isSafeZone(event.getDamager().getLocation())) {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
    }

    /**
     * Process player death events
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Play death sound
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);

        // Clear combat state
        combatMap.remove(player.getName());
        alignmentMechanics.clearCombatTag(player);

        // Clean up death message and XP
        event.setDroppedExp(0);
        event.setDeathMessage(null);
    }

    /**
     * Handle player respawn
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Clear combat tags
        combatMap.remove(player.getName());
        alignmentMechanics.clearCombatTag(player);
    }

    /**
     * Handle player join for combat system
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Set attack speed attribute to prevent cooldown
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(1024.0D);

        // Setup health display
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
    }

    /**
     * Handle logout during combat (punish combat logging)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombatLogout(PlayerKickEvent event) {
        Player player = event.getPlayer();

        // Kill player if they log out during combat outside safe zone
        if (!AlignmentMechanics.isSafeZone(player.getLocation()) &&
                alignmentMechanics.isPlayerTagged(player) &&
                alignmentMechanics.getTimeSinceLastTag(player) < 10000) {
            player.setHealth(0.0);
        }
    }

    /**
     * Generate a random spawn point for players
     *
     * @param playerName The player's name (used for seeding)
     * @return Random spawn location
     */
    private Location generateRandomSpawnPoint(String playerName) {
        // Random spawn logic for chaotic players
        Random random = new Random(playerName.hashCode() + System.currentTimeMillis());
        World world = Bukkit.getWorlds().get(0); // Main world

        double x = (random.nextDouble() - 0.5) * 2000;
        double z = (random.nextDouble() - 0.5) * 2000;

        // Ensure spawn point isn't in a safe zone
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            Location loc = new Location(world, x, y, z);

            if (!AlignmentMechanics.isSafeZone(loc)) {
                return loc;
            }

            // Try different coordinates
            x = (random.nextDouble() - 0.5) * 2000;
            z = (random.nextDouble() - 0.5) * 2000;
        }

        // Fallback to a reasonable spawn
        return new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z);
    }
}