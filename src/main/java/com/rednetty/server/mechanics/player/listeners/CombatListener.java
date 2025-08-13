package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.mechanics.combat.CombatMechanics;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.combat.pvp.PvPRatingManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 *  CombatListener with comprehensive PvP Rating integration
 * Handles all combat-related events including damage processing, PvP interactions,
 * combat tagging, environmental damage, and rating/streak management.
 */
public class CombatListener extends BaseListener {

    // Combat state tracking
    private final ConcurrentHashMap<String, Long> combatMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> mobAttackCooldown = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> fireDamageCooldown = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> poisonDamageCooldown = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastAttacker = new ConcurrentHashMap<>();

    // Dependencies
    private final YakPlayerManager playerManager;
    private final CombatMechanics combatMechanics;
    private final AlignmentMechanics alignmentMechanics;
    private final PvPRatingManager pvpRatingManager;
    private final CombatLogoutMechanics combatLogoutMechanics;

    // Configuration constants
    private static final long COMBAT_TAG_DURATION = 10000; // 10 seconds
    private static final long MOB_ATTACK_COOLDOWN = 1000; // 1 second
    private static final long FIRE_DAMAGE_COOLDOWN = 500; // 0.5 seconds
    private static final long POISON_DAMAGE_COOLDOWN = 1000; // 1 second
    private static final double FIRE_DAMAGE_MULTIPLIER = 0.03;
    private static final double LAVA_DAMAGE_MULTIPLIER = 0.03;
    private static final double FIRE_TICK_MULTIPLIER = 0.01;
    private static final double POISON_DAMAGE_MULTIPLIER = 0.01;
    private static final double DROWNING_DAMAGE_MULTIPLIER = 0.04;
    private static final double FALL_DAMAGE_MULTIPLIER = 0.02;

    /**
     * Constructor - initializes all dependencies
     */
    public CombatListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
        this.combatMechanics = plugin.getCombatMechanics();
        this.alignmentMechanics = AlignmentMechanics.getInstance();
        this.pvpRatingManager = PvPRatingManager.getInstance();
        this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();
    }

    @Override
    public void initialize() {
        logger.info(" CombatListener initialized with PvP Rating integration");
    }

    @Override
    public void cleanup() {
        combatMap.clear();
        mobAttackCooldown.clear();
        fireDamageCooldown.clear();
        poisonDamageCooldown.clear();
        lastAttacker.clear();
        logger.info("CombatListener cleanup completed");
    }

    /**
     *  PVP PROTECTION - Main PvP damage handler with comprehensive protection
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPvpProtection(EntityDamageByEntityEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        if (event.getDamage() <= 0.0 || event.isCancelled()) {
            return;
        }

        Player attacker = getPlayerAttacker(event);
        Player victim = getPlayerVictim(event);

        if (attacker == null || victim == null) {
            return;
        }

        YakPlayer attackerData = playerManager.getPlayer(attacker);
        YakPlayer victimData = playerManager.getPlayer(victim);

        if (attackerData == null || victimData == null) {
            event.setCancelled(true);
            return;
        }

        // === CORE PROTECTION CHECKS ===

        // 1. Safe zone protection
        if (AlignmentMechanics.isSafeZone(attacker.getLocation()) ||
                AlignmentMechanics.isSafeZone(victim.getLocation())) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "⚠ Cannot attack in safe zones!");
            return;
        }

        // 2. Anti-PvP toggle protection
        if (Toggles.isToggled(attacker, "Anti PVP")) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "⚠ Your Anti-PvP is enabled! Use /toggle to change settings.");
            return;
        }

        // 3. Self-damage protection (unless friendly fire enabled)
        if (attacker.equals(victim) && !Toggles.isToggled(attacker, "Friendly Fire")) {
            event.setCancelled(true);
            return;
        }

        // 4. Buddy protection (unless friendly fire enabled)
        if (!Toggles.isToggled(attacker, "Friendly Fire")) {
            if (attackerData.isBuddy(victim.getName()) || victimData.isBuddy(attacker.getName())) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.YELLOW + "⚠ Cannot attack buddy " + victim.getName() +
                        "! Enable Friendly Fire in /toggle to allow this.");
                return;
            }
        }

        // 5. Party protection (unless friendly fire enabled)
        if (!Toggles.isToggled(attacker, "Friendly Fire")) {
            try {
                if (PartyMechanics.getInstance().arePartyMembers(attacker, victim)) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.YELLOW + "⚠ Cannot attack party member " + victim.getName() +
                            "! Enable Friendly Fire in /toggle to allow this.");
                    return;
                }
            } catch (Exception e) {
                logger.fine("Error checking party membership: " + e.getMessage());
            }
        }

        // 6. Guild protection (unless friendly fire enabled)
        if (!Toggles.isToggled(attacker, "Friendly Fire")) {
            if (isInSameGuild(attacker, victim)) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.YELLOW + "⚠ Cannot attack guild member " + victim.getName() +
                        "! Enable Friendly Fire in /toggle to allow this.");
                return;
            }
        }

        // 7. Chaotic protection for lawful players
        if (Toggles.isToggled(attacker, "Chaotic Protection")) {
            if ("LAWFUL".equals(victimData.getAlignment()) && "CHAOTIC".equals(attackerData.getAlignment())) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "⚠ Chaotic Protection prevents attacking lawful players!");
                attacker.sendMessage(ChatColor.GRAY + "Disable in /toggle if you want to attack lawful players.");
                return;
            }
        }

        // === PVP ATTACK PROCESSING ===
        if (!event.isCancelled()) {
            handleSuccessfulPvPAttack(attacker, victim, attackerData, victimData, event);
        }
    }


    /**
     * Process a successful PvP attack
     */
    private void handleSuccessfulPvPAttack(Player attacker, Player victim, YakPlayer attackerData,
                                           YakPlayer victimData, EntityDamageByEntityEvent event) {

        // Cancel any logout commands
        cancelLogoutCommands(attacker, victim);

        // Handle alignment changes (from AlignmentMechanics)
        handleAlignmentChanges(attacker, attackerData);

        // Tag both players for combat
        tagPlayersForCombat(attacker, victim);

        // Handle friendly fire effects if applicable
        if (isFriendlyFireHit(attacker, victim, attackerData, victimData)) {
            showFriendlyFireEffects(attacker, victim);
        }

        // Debug information
        if (Toggles.isToggled(attacker, "Debug")) {
            showDebugInfo(attacker, victim, attackerData, victimData);
        }

        // Log the attack
        logger.fine("PvP attack: " + attacker.getName() + " -> " + victim.getName() +
                " (Damage: " + event.getDamage() + ")");
    }

    /**
     * PLAYER DEATH HANDLER - Processes PvP kills and deaths with rating integration
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Clear combat state immediately
        clearCombatState(victim);

        // Process PvP kill/death FIRST (this updates ratings and streaks)
        if (killer != null && !killer.equals(victim)) {
            try {
                PvPRatingManager.KillResult killResult = pvpRatingManager.processKill(killer, victim);
                PvPRatingManager.DeathResult deathResult = pvpRatingManager.processDeath(victim, killer);

                // Log the PvP results
                if (killResult != null) {
                    logger.info("PvP Kill: " + killer.getName() + " -> " + victim.getName() +
                            " | Rating: +" + killResult.ratingChange + " | Streak: " + killResult.killStreak);
                }

                if (deathResult != null) {
                    logger.info("PvP Death: " + victim.getName() +
                            " | Rating: " + deathResult.ratingChange + " | Lost streak: " + deathResult.lostStreak);
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing PvP kill/death for " +
                        killer.getName() + " -> " + victim.getName(), e);
            }
        }

        // Play death sound
        try {
            victim.playSound(victim.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        } catch (Exception e) {
            logger.fine("Could not play death sound for " + victim.getName());
        }

        // Clean up death message and XP
        event.setDroppedExp(0);
        event.setDeathMessage(null);

        // Update killer's combat state
        if (killer != null) {
            clearCombatState(killer);
        }
    }

    /**
     * PLAYER RESPAWN HANDLER - Clean up state and ensure data consistency
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Clear all combat state
        clearCombatState(player);

        // Ensure kill streak is properly synced after respawn
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                // Verify the player's kill streak is reset in the YakPlayer data
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer != null && yakPlayer.getKillStreak() > 0) {
                    yakPlayer.setKillStreak(0);
                    playerManager.savePlayer(yakPlayer);
                    logger.fine("Reset kill streak for respawned player: " + player.getName());
                }
            }
        }, 20L);
    }

    /**
     * PLAYER JOIN HANDLER - Set up combat attributes and systems
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            // Set attack speed attribute to prevent vanilla cooldown
            if (player.getAttribute(Attribute.ATTACK_SPEED) != null) {
                player.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(1024.0D);
            }

            // Setup health display
            player.setHealthScale(20.0);
            player.setHealthScaled(true);

            logger.fine("Configured combat attributes for: " + player.getName());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error setting up combat attributes for " + player.getName(), e);
        }
    }

    /**
     * COMBAT TAGGING HANDLER - Tag players for PvP combat
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCombatTag(EntityDamageByEntityEvent event) {
        if (isPatchLockdown() || event.getDamage() <= 0.0 || event.isCancelled()) {
            return;
        }

        Player attacker = getPlayerAttacker(event);
        Player victim = getPlayerVictim(event);

        if (attacker != null && victim != null) {
            // Check for safe zone (don't tag in safe zones)
            if (AlignmentMechanics.isSafeZone(attacker.getLocation()) ||
                    AlignmentMechanics.isSafeZone(victim.getLocation())) {
                return;
            }

            // Tag both players
            tagPlayer(attacker);
            if (!attacker.equals(victim)) {
                tagPlayer(victim);
            }
        }
    }

    /**
     * GENERAL DAMAGE TAGGING - Tag for non-PvP damage
     */
    @EventHandler
    public void onDamageTag(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getDamage() <= 0.0) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Don't tag on fall damage
        if (event.getCause() != DamageCause.FALL) {
            CombatLogoutMechanics.getInstance().markCombatTagged(player);
        }
    }

    /**
     * ATTACK TAGGING - Tag when players attack
     */
    @EventHandler
    public void onAttackTag(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        if (event.getDamage() <= 0.0 || event.isCancelled()) {
            return;
        }

        Player player = (Player) event.getDamager();
        CombatLogoutMechanics.getInstance().markCombatTagged(player);
    }

    /**
     * MOB ATTACK COOLDOWN - Prevent mobs from spamming attacks
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
                System.currentTimeMillis() - mobAttackCooldown.get(attackerId) < MOB_ATTACK_COOLDOWN) {
            event.setDamage(0.0);
            event.setCancelled(true);
            return;
        }

        // Set attack cooldown
        mobAttackCooldown.put(attackerId, System.currentTimeMillis());
    }

    /**
     * ENVIRONMENTAL DAMAGE HANDLER - Handle fire, poison, drowning, etc.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) event.getEntity();
        double maxHealth = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
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
                handleWitherDamage(event, entity);
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
     * SAFE ZONE DAMAGE PROTECTION - Prevent all damage in safe zones
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSafeZoneDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

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
     * SAFE ZONE ATTACKER PROTECTION - Prevent entities in safe zones from dealing damage
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
     * TIER-BASED DAMAGE ENHANCEMENT (Optional) - Modify damage based on PvP tiers
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onTierBasedDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (event.isCancelled() || event.getDamage() <= 0.0) {
            return;
        }

        // Get tier information for  combat feedback
        PvPRatingManager.RatingTier attackerTier = pvpRatingManager.getRatingTier(attacker);
        PvPRatingManager.RatingTier victimTier = pvpRatingManager.getRatingTier(victim);

        // Optional: Modify damage based on tier differences
        /*
        if (attackerTier.ordinal() > victimTier.ordinal()) {
            double tierBonus = 1.0 + ((attackerTier.ordinal() - victimTier.ordinal()) * 0.05);
            event.setDamage(event.getDamage() * tierBonus);
        }
        */

        // Display tier information to attacker if debug toggle is enabled
        if (Toggles.isToggled(attacker, "Debug")) {
            attacker.sendMessage(ChatColor.GRAY + "PvP Tiers: " + attackerTier.color + attackerTier.name +
                    ChatColor.GRAY + " vs " + victimTier.color + victimTier.name);
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get the player attacker from a damage event
     */
    private Player getPlayerAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if (damager instanceof Player) {
            return (Player) damager;
        }

        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }

        return null;
    }

    /**
     * Get the player victim from a damage event
     */
    private Player getPlayerVictim(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            return (Player) event.getEntity();
        }
        return null;
    }

    /**
     * Check if two players are in the same guild
     */
    private boolean isInSameGuild(Player player1, Player player2) {
        if (player1 == null || player2 == null) return false;

        YakPlayer yakPlayer1 = playerManager.getPlayer(player1);
        YakPlayer yakPlayer2 = playerManager.getPlayer(player2);

        if (yakPlayer1 == null || yakPlayer2 == null) return false;

        String guild1 = yakPlayer1.getGuildName();
        String guild2 = yakPlayer2.getGuildName();

        return guild1 != null && !guild1.trim().isEmpty() &&
                guild2 != null && !guild2.trim().isEmpty() &&
                guild1.equals(guild2);
    }

    /**
     * Check if this is a friendly fire hit
     */
    private boolean isFriendlyFireHit(Player attacker, Player victim, YakPlayer attackerData, YakPlayer victimData) {
        if (!attackerData.isToggled("Friendly Fire")) {
            return false;
        }

        // Check buddies, party members, or guild members
        return attackerData.isBuddy(victim.getName()) ||
                victimData.isBuddy(attacker.getName()) ||
                isInSameGuild(attacker, victim);
    }

    /**
     * Show friendly fire effects
     */
    private void showFriendlyFireEffects(Player attacker, Player victim) {
        try {
            attacker.sendMessage(ChatColor.YELLOW + "⚠ Friendly fire hit on " + victim.getName() + "!");
            victim.sendMessage(ChatColor.YELLOW + "⚠ Friendly fire from " + attacker.getName() + "!");

            attacker.playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
            victim.playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);

            victim.getWorld().spawnParticle(Particle.ANGRY_VILLAGER,
                    victim.getLocation().add(0, 2, 0), 3, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            // Ignore particle/sound errors
        }
    }

    /**
     * Show debug information
     */
    private void showDebugInfo(Player attacker, Player victim, YakPlayer attackerData, YakPlayer victimData) {
        PvPRatingManager.RatingTier attackerTier = pvpRatingManager.getRatingTier(attacker);
        PvPRatingManager.RatingTier victimTier = pvpRatingManager.getRatingTier(victim);
        int attackerStreak = pvpRatingManager.getCurrentKillStreak(attacker);
        int victimStreak = pvpRatingManager.getCurrentKillStreak(victim);

        attacker.sendMessage(ChatColor.GRAY + "=== PvP DEBUG ===");
        attacker.sendMessage(ChatColor.GRAY + "Your Rating: " + attackerTier.color +
                attackerData.getPvpRating() + " (" + attackerTier.name + ")");
        attacker.sendMessage(ChatColor.GRAY + "Your Streak: " + ChatColor.YELLOW + attackerStreak);
        attacker.sendMessage(ChatColor.GRAY + "Target Rating: " + victimTier.color +
                victimData.getPvpRating() + " (" + victimTier.name + ")");
        attacker.sendMessage(ChatColor.GRAY + "Target Streak: " + ChatColor.YELLOW + victimStreak);
        attacker.sendMessage(ChatColor.GRAY + "Target Alignment: " + getAlignmentColor(victimData.getAlignment()) +
                victimData.getAlignment());
    }

    /**
     * Get alignment color for debug display
     */
    private ChatColor getAlignmentColor(String alignment) {
        switch (alignment) {
            case "CHAOTIC":
                return ChatColor.RED;
            case "NEUTRAL":
                return ChatColor.YELLOW;
            case "LAWFUL":
                return ChatColor.GREEN;
            default:
                return ChatColor.GRAY;
        }
    }

    /**
     * Cancel logout commands for players entering combat
     */
    private void cancelLogoutCommands(Player attacker, Player victim) {
        try {
            Class<?> logoutCommandClass = Class.forName("com.rednetty.server.commands.player.LogoutCommand");
            java.lang.reflect.Method forceCancelMethod = logoutCommandClass.getMethod("forceCancelLogout", Player.class, String.class);

            forceCancelMethod.invoke(null, attacker, "You entered combat!");
            forceCancelMethod.invoke(null, victim, "You were attacked!");

        } catch (Exception e) {
            logger.fine("Could not cancel logout commands: " + e.getMessage());
        }
    }

    /**
     * Handle alignment changes for PvP attacks
     */
    private void handleAlignmentChanges(Player attacker, YakPlayer attackerData) {
        String alignment = attackerData.getAlignment();

        if ("CHAOTIC".equals(alignment)) {
            // Already chaotic - extend timer
            attackerData.setChaoticTime(System.currentTimeMillis() / 1000 + 300); // 5 minutes
            playerManager.savePlayer(attackerData);
        } else if ("NEUTRAL".equals(alignment)) {
            // Extend neutral timer
            attackerData.setNeutralTime(System.currentTimeMillis() / 1000 + 120); // 2 minutes
            playerManager.savePlayer(attackerData);
        } else {
            // Lawful attacking - set to neutral
            AlignmentMechanics.setNeutralAlignment(attacker);
        }
    }

    /**
     * Tag players for combat using both systems
     */
    private void tagPlayersForCombat(Player attacker, Player victim) {
        // Tag using combat logout mechanics
        combatLogoutMechanics.markCombatTagged(attacker);
        combatLogoutMechanics.markCombatTagged(victim);
        combatLogoutMechanics.setLastAttacker(victim, attacker);

        // Tag using local combat map
        tagPlayer(attacker);
        tagPlayer(victim);

        // Track last attacker
        lastAttacker.put(victim.getUniqueId(), attacker.getName());
    }

    /**
     * Tag a player for combat
     */
    public void tagPlayer(Player player) {
        combatMap.put(player.getName(), System.currentTimeMillis());
    }

    /**
     * Check if a player is in combat
     */
    public boolean isInCombat(Player player) {
        if (combatMap.containsKey(player.getName())) {
            long lastCombat = combatMap.get(player.getName());
            return System.currentTimeMillis() - lastCombat <= COMBAT_TAG_DURATION;
        }
        return false;
    }

    /**
     * Get remaining combat time in seconds
     */
    public int getRemainingCombatTime(Player player) {
        if (isInCombat(player)) {
            long combatStartTime = combatMap.get(player.getName());
            long currentTime = System.currentTimeMillis();
            long remainingTime = combatStartTime + COMBAT_TAG_DURATION - currentTime;
            return Math.max((int) (remainingTime / 1000), 0);
        }
        return 0;
    }

    /**
     * Clear all combat state for a player
     */
    private void clearCombatState(Player player) {
        combatMap.remove(player.getName());
        lastAttacker.remove(player.getUniqueId());
        CombatLogoutMechanics.getInstance().clearCombatTag(player);
    }

    // ==================== ENVIRONMENTAL DAMAGE HANDLERS ====================

    /**
     * Handle fire/lava damage with proper throttling
     */
    private void handleFireDamage(EntityDamageEvent event, LivingEntity entity, double maxHealth) {
        UUID entityId = entity.getUniqueId();

        if (!fireDamageCooldown.containsKey(entityId) ||
                System.currentTimeMillis() - fireDamageCooldown.get(entityId) > FIRE_DAMAGE_COOLDOWN) {

            fireDamageCooldown.put(entityId, System.currentTimeMillis());

            double multiplier;
            switch (event.getCause()) {
                case FIRE:
                case LAVA:
                    multiplier = FIRE_DAMAGE_MULTIPLIER;
                    break;
                case FIRE_TICK:
                default:
                    multiplier = FIRE_TICK_MULTIPLIER;
                    break;
            }

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
        UUID entityId = entity.getUniqueId();

        if (!poisonDamageCooldown.containsKey(entityId) ||
                System.currentTimeMillis() - poisonDamageCooldown.get(entityId) > POISON_DAMAGE_COOLDOWN) {

            poisonDamageCooldown.put(entityId, System.currentTimeMillis());

            double damage;
            double calculatedDamage = maxHealth * POISON_DAMAGE_MULTIPLIER;

            if (calculatedDamage >= entity.getHealth()) {
                // Prevent death by poison (leave at 0.5 health)
                damage = Math.max(0, entity.getHealth() - 0.5);
            } else {
                damage = Math.max(calculatedDamage, 1.0);
            }

            event.setDamage(damage);
        } else {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
    }

    /**
     * Handle drowning damage (percentage based)
     */
    private void handleDrowningDamage(EntityDamageEvent event, LivingEntity entity, double maxHealth) {
        double damage = Math.max(maxHealth * DROWNING_DAMAGE_MULTIPLIER, 1.0);
        event.setDamage(damage);
    }

    /**
     * Handle wither damage (cancel it completely)
     */
    private void handleWitherDamage(EntityDamageEvent event, LivingEntity entity) {
        event.setCancelled(true);
        event.setDamage(0.0);

        // Remove wither effect
        if (entity.hasPotionEffect(PotionEffectType.WITHER)) {
            entity.removePotionEffect(PotionEffectType.WITHER);
        }
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

            Location teleportLocation;
            if (yakPlayer != null && "CHAOTIC".equals(yakPlayer.getAlignment())) {
                // Teleport chaotic players to random spawn point
                teleportLocation = generateRandomSpawnPoint(player.getName());
            } else {
                // Teleport to main spawn
                teleportLocation = player.getWorld().getSpawnLocation();
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(teleportLocation);
                player.sendMessage(ChatColor.YELLOW + "⚠ Teleported to safety from the void!");
            });
        }
    }

    /**
     * Handle fall damage (percentage based)
     */
    private void handleFallDamage(EntityDamageEvent event, LivingEntity entity, double maxHealth) {
        double fallHeight = event.getDamage();
        double calculatedDamage = fallHeight * maxHealth * FALL_DAMAGE_MULTIPLIER;

        double damage;
        if (calculatedDamage >= entity.getHealth()) {
            // Prevent instakill from fall (leave at 1 health)
            damage = Math.max(0, entity.getHealth() - 1.0);
        } else {
            damage = Math.max(calculatedDamage, 1.0);
        }

        event.setDamage(damage);
    }

    /**
     * Generate a random spawn point for chaotic players
     */
    private Location generateRandomSpawnPoint(String playerName) {
        Random random = new Random(playerName.hashCode() + System.currentTimeMillis());
        World world = Bukkit.getWorlds().get(0);

        double x = (random.nextDouble() - 0.5) * 2000;
        double z = (random.nextDouble() - 0.5) * 2000;

        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            Location loc = new Location(world, x, y, z);

            if (!AlignmentMechanics.isSafeZone(loc)) {
                return loc;
            }

            x = (random.nextDouble() - 0.5) * 2000;
            z = (random.nextDouble() - 0.5) * 2000;
        }

        return new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z);
    }

    // ==================== PUBLIC API METHODS ====================

    /**
     * Get the PvP Rating Manager instance
     */
    public PvPRatingManager getPvPRatingManager() {
        return pvpRatingManager;
    }

    /**
     * Get formatted PvP info for a player
     */
    public String getPlayerPvPInfo(Player player) {
        if (player == null) return "Unknown";

        PvPRatingManager.RatingTier tier = pvpRatingManager.getRatingTier(player);
        int streak = pvpRatingManager.getCurrentKillStreak(player);
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer == null) return "Unknown";

        StringBuilder info = new StringBuilder();
        info.append(tier.color).append(tier.name).append(" (").append(yakPlayer.getPvpRating()).append(")");

        if (streak > 0) {
            info.append(" ").append(ChatColor.YELLOW).append("Streak: ").append(streak);
        }

        return info.toString();
    }

    /**
     * Check if a player has a significant kill streak
     */
    public boolean hasSignificantKillStreak(Player player) {
        return pvpRatingManager.getCurrentKillStreak(player) >= 5;
    }

    /**
     * Get kill streak tier name for announcements
     */
    public String getKillStreakTierName(int streak) {
        if (streak >= 20) return "LEGENDARY";
        if (streak >= 15) return "Godlike";
        if (streak >= 10) return "Unstoppable";
        if (streak >= 7) return "Dominating";
        if (streak >= 5) return "Rampage";
        if (streak >= 3) return "Killing Spree";
        return null;
    }

    /**
     * Get the last attacker of a player
     */
    public String getLastAttacker(Player player) {
        return lastAttacker.get(player.getUniqueId());
    }

    /**
     * Force clear a player's combat state (for admin commands)
     */
    public void forceClearCombatState(Player player) {
        clearCombatState(player);
        logger.info("Force cleared combat state for: " + player.getName());
    }
}