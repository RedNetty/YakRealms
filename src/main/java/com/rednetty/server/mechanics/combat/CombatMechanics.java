package com.rednetty.server.mechanics.combat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stamina.Energy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all combat and damage mechanics including:
 * - Damage calculation and application
 * - Armor, block, and dodge mechanics
 * - Critical hits and combat effects
 * - Combat visualization (holograms, health bar)
 * - Weapon-specific mechanics
 * FIXED: Proper mob damage to players
 */
public class CombatMechanics implements Listener {
    // Constants
    private static final int HOLOGRAM_DURATION = 20; // Ticks
    private static final int MAX_BLOCK_CHANCE = 60;
    private static final int MAX_DODGE_CHANCE = 60;
    private static final int COMBAT_DURATION = 5000; // Milliseconds
    private static final int PLAYER_SLOW_DURATION = 3000; // Milliseconds

    // Knockback constants - reduced for less glitchy effect
    private static final double KNOCKBACK_BASE = 2.5;  // Base knockback force (reduced from 2.0)
    private static final double KNOCKBACK_VERTICAL_BASE = 0.45;  // Base vertical knockback (reduced from 0.15)
    private static final double KNOCKBACK_RANDOMNESS = 0.01;  // Random factor (reduced from 0.03)
    private static final double WEAPON_KNOCK_MODIFIER = 1.1;  // Modifier for weapon-specific knockback (reduced from 1.2)

    // Elemental effect IDs
    private static final int ICE_EFFECT_ID = 13565951;    // Light blue potion effect
    private static final int POISON_EFFECT_ID = 8196;  // Green potion effect
    private static final int FIRE_EFFECT_ID = 8259;    // Orange potion effect

    // Tracking maps
    private final Map<UUID, Long> combatTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerSlowEffects = new ConcurrentHashMap<>();
    private final Map<UUID, Long> knockbackCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> polearmSwingProcessed = new HashSet<>();
    private final Map<UUID, BossBar> healthBars = new ConcurrentHashMap<>();

    // Entity damage visualization tracking
    private final Map<UUID, Long> entityDamageEffects = new ConcurrentHashMap<>();

    // Dependencies
    private final YakPlayerManager playerManager;

    /**
     * Constructor initializes dependencies
     */
    public CombatMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
    }

    /**
     * Registers this listener and starts necessary tasks
     */
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Start health bar update task
        startHealthBarUpdateTask();

        // Start player movement speed restore task
        startMovementSpeedRestoreTask();

        // Start entity damage effect cleanup task
        startEntityDamageEffectCleanupTask();

        YakRealms.log("Combat Mechanics have been enabled");
    }

    /**
     * Cleans up resources when disabling
     */
    public void onDisable() {
        // Remove all boss bars
        for (BossBar bar : healthBars.values()) {
            bar.removeAll();
        }
        healthBars.clear();
        YakRealms.log("Combat Mechanics have been disabled");
    }

    /**
     * Starts a task to update player health bars
     */
    private void startHealthBarUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateHealthBar(player);
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, 1);
    }

    /**
     * Starts a task to restore player movement speed after slowing effects
     */
    private void startMovementSpeedRestoreTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();

                    if (playerSlowEffects.containsKey(playerId)) {
                        if (currentTime - playerSlowEffects.get(playerId) > PLAYER_SLOW_DURATION) {
                            // Restore normal speed
                            setPlayerSpeed(player, 0.2f);
                            playerSlowEffects.remove(playerId);
                        }
                    } else if (player.getWalkSpeed() != 0.2f) {
                        // Ensure player has correct speed
                        setPlayerSpeed(player, 0.2f);
                    }
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20, 20);
    }

    /**
     * Starts a task to cleanup entity damage effect tracking
     */
    private void startEntityDamageEffectCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> iterator = entityDamageEffects.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    if (currentTime - entry.getValue() > 500) { // 500ms effect duration
                        iterator.remove();
                    }
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 10, 10);
    }

    /**
     * Updates a player's health bar display
     *
     * @param player The player to update
     */
    private void updateHealthBar(Player player) {
        double maxHealth = player.getMaxHealth();
        double currentHealth = player.getHealth();
        double healthPercentage = Math.min(1.0, currentHealth / maxHealth);

        // Format the title with appropriate colors
        BarColor barColor = getHealthBarColor(player);
        ChatColor titleColor = getHealthTextColor(player);

        String safeZoneText = isSafeZone(player.getLocation()) ? ChatColor.GRAY + " - " + ChatColor.GREEN + ChatColor.BOLD + "SAFE-ZONE" : "";

        String title = titleColor + "" + ChatColor.BOLD + "HP " + titleColor + (int) currentHealth + titleColor + ChatColor.BOLD + " / " + titleColor + (int) maxHealth + safeZoneText;

        // Get or create the boss bar
        BossBar bossBar = healthBars.get(player.getUniqueId());
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(title, barColor, BarStyle.SOLID);
            bossBar.addPlayer(player);
            healthBars.put(player.getUniqueId(), bossBar);
        }

        // Update the boss bar
        bossBar.setColor(barColor);
        bossBar.setTitle(title);
        bossBar.setProgress((float) healthPercentage);
    }

    /**
     * Get the appropriate boss bar color based on health percentage
     *
     * @param player The player to check
     * @return The appropriate BarColor
     */
    private BarColor getHealthBarColor(Player player) {
        double healthPercentage = player.getHealth() / player.getMaxHealth();

        if (healthPercentage > 0.5) {
            return BarColor.GREEN;
        } else if (healthPercentage > 0.25) {
            return BarColor.YELLOW;
        } else {
            return BarColor.RED;
        }
    }

    /**
     * Get the appropriate text color for health display
     *
     * @param player The player to check
     * @return The appropriate ChatColor
     */
    private ChatColor getHealthTextColor(Player player) {
        double healthPercentage = player.getHealth() / player.getMaxHealth();

        if (healthPercentage > 0.5) {
            return ChatColor.GREEN;
        } else if (healthPercentage > 0.25) {
            return ChatColor.YELLOW;
        } else {
            return ChatColor.RED;
        }
    }

    /**
     * Set a player's walk speed safely across threads
     *
     * @param player The player to update
     * @param speed  The new walk speed
     */
    private void setPlayerSpeed(Player player, float speed) {
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> player.setWalkSpeed(speed));
    }

    /**
     * Mark a player as in combat with another player
     *
     * @param victim   The player being attacked
     * @param attacker The attacking player
     */
    private void markPlayerInCombat(Player victim, Player attacker) {
        UUID victimId = victim.getUniqueId();
        UUID attackerId = attacker.getUniqueId();

        combatTimestamps.put(victimId, System.currentTimeMillis());
        lastAttackers.put(victimId, attackerId);

        // Mark attacker as in combat too
        combatTimestamps.put(attackerId, System.currentTimeMillis());
    }

    /**
     * Check if a player is in combat
     *
     * @param player The player to check
     * @return true if the player is in combat
     */
    public boolean isInCombat(Player player) {
        UUID playerId = player.getUniqueId();

        if (!combatTimestamps.containsKey(playerId)) {
            return false;
        }

        long lastCombatTime = combatTimestamps.get(playerId);
        long timeSinceCombat = System.currentTimeMillis() - lastCombatTime;

        return timeSinceCombat < COMBAT_DURATION;
    }

    /**
     * Get the remaining combat time in seconds
     *
     * @param player The player to check
     * @return The remaining time in seconds, or 0 if not in combat
     */
    public int getRemainingCombatTime(Player player) {
        UUID playerId = player.getUniqueId();

        if (!combatTimestamps.containsKey(playerId)) {
            return 0;
        }

        long lastCombatTime = combatTimestamps.get(playerId);
        long timeSinceCombat = System.currentTimeMillis() - lastCombatTime;
        int remainingTime = (int) ((COMBAT_DURATION - timeSinceCombat) / 1000);

        return Math.max(0, remainingTime);
    }

    /**
     * Get a player's last attacker
     *
     * @param player The player to check
     * @return The last player who attacked them, or null if none
     */
    public Player getLastAttacker(Player player) {
        UUID playerId = player.getUniqueId();

        if (!lastAttackers.containsKey(playerId)) {
            return null;
        }

        UUID attackerId = lastAttackers.get(playerId);
        return Bukkit.getPlayer(attackerId);
    }

    /**
     * Prevent damage to NPCs and operators
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcDamage(EntityDamageEvent event) {
        // Skip if already cancelled
        if (event.isCancelled()) {
            return;
        }

        // Cancel damage to pets
        if (event.getEntity().hasMetadata("pet")) {
            event.setCancelled(true);
            return;
        }

        // Handle damage to players
        if (event.getEntity() instanceof Player player) {

            // Cancel damage to NPCs
            if (player.hasMetadata("NPC")) {
                event.setCancelled(true);
                event.setDamage(0.0);
                return;
            }

            // Cancel damage to operators and creative mode players unless explicitly enabled
            if (player.isOp() || player.getGameMode() == GameMode.CREATIVE || player.isFlying()) {
                if (!isGodModeDisabled(player)) {
                    event.setCancelled(true);
                    event.setDamage(0.0);
                }
            }
        }
    }

    /**
     * Handle firework and explosion damage cancellation
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Cancel firework and explosion damage
        if (event.getDamager().getType() == EntityType.FIREWORK || event.getCause() == DamageCause.ENTITY_EXPLOSION) {
            event.setDamage(0);
            event.setCancelled(true);
            return;
        }

        // Create damage hologram if appropriate
        if (event.getDamager() instanceof Player damager && event.getEntity() instanceof LivingEntity entity) {
            int damage = (int) event.getDamage();
            //showCombatHologram(damager, entity, "dmg", damage);
        }
    }

    /**
     * Handle block and dodge mechanics
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDefensiveAction(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Only handle player victims
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }

        YakPlayer yakDefender = playerManager.getPlayer(defender);

        if (yakDefender == null) {
            return;
        }

        // Calculate block chance
        int blockChance = calculateBlockChance(defender);

        // Calculate dodge chance
        int dodgeChance = calculateDodgeChance(defender);

        // Apply accuracy reduction if attacker is a player
        if (event.getDamager() instanceof Player attacker) {
            int accuracy = getAccuracy(attacker);

            // Reduce block and dodge chances based on accuracy
            if (blockChance > 40) {
                blockChance -= (int) (accuracy * (0.05 * (blockChance / 10.0)));
            }

            if (dodgeChance > 40) {
                dodgeChance -= (int) (accuracy * (0.05 * (dodgeChance / 10.0)));
            }
        }

        Random random = new Random();
        int roll = random.nextInt(100) + 1;

        // Handle block
        if (roll <= blockChance) {
            event.setCancelled(true);
            event.setDamage(0.0);

            // Play block sound
            defender.playSound(defender.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

            // Show block hologram
            if (event.getDamager() instanceof Player attacker) {
                //showCombatHologram(attacker, defender, "block", 0);

                // Show debug messages
                Toggles.getInstance();
                if (Toggles.isToggled(attacker, "Debug")) {
                    attacker.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "*OPPONENT BLOCKED* (" + defender.getName() + ")");
                }

                Toggles.getInstance();
                if (Toggles.isToggled(defender, "Debug")) {
                    defender.sendMessage(ChatColor.DARK_GREEN + ChatColor.BOLD.toString() + "*BLOCK* (" + attacker.getName() + ")");
                }
            }
            return;
        }

        // Handle dodge
        roll = random.nextInt(100) + 1;
        if (roll <= dodgeChance) {
            event.setCancelled(true);
            event.setDamage(0.0);

            // Play dodge sound
            defender.playSound(defender.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 1.0f, 1.0f);

            // Show dodge effect
            defender.getWorld().spawnParticle(Particle.CLOUD, defender.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);

            // Show dodge hologram
            if (event.getDamager() instanceof Player attacker) {
                //showCombatHologram(attacker, defender, "dodge", 0);

                // Show debug messages
                Toggles.getInstance();
                if (Toggles.isToggled(attacker, "Debug")) {
                    attacker.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "*OPPONENT DODGED* (" + defender.getName() + ")");
                }

                Toggles.getInstance();
                if (Toggles.isToggled(defender, "Debug")) {
                    defender.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "*DODGE* (" + attacker.getName() + ")");
                }
            }
            return;
        }

        // Handle shield blocking (partial damage reduction)
        if (defender.isBlocking() && random.nextInt(100) <= 80) {
            event.setDamage(event.getDamage() / 2);

            // Show block hologram
            if (event.getDamager() instanceof Player attacker) {
                //showCombatHologram(attacker, defender, "block", 0);

                // Play block sound
                defender.playSound(defender.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

                // Show debug messages
                Toggles.getInstance();
                if (Toggles.isToggled(attacker, "Debug")) {
                    attacker.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "*OPPONENT BLOCKED* (" + defender.getName() + ")");
                }

                Toggles.getInstance();
                if (Toggles.isToggled(defender, "Debug")) {
                    defender.sendMessage(ChatColor.DARK_GREEN + ChatColor.BOLD.toString() + "*BLOCK* (" + attacker.getName() + ")");
                }
            }
        }
    }

    /**
     * FIXED: Handle mob damage to players with proper calculation
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDamageToPlayer(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Only handle mob attackers and player defenders
        if (event.getDamager() instanceof Player || !(event.getEntity() instanceof Player defender)) {
            return;
        }

        // This is mob damage to a player - calculate proper damage
        LivingEntity mobAttacker = (LivingEntity) event.getDamager();

        try {
            // Get base mob damage
            double baseMobDamage = calculateMobBaseDamage(mobAttacker);

            // Apply mob damage calculations
            double finalDamage = applyMobDamageCalculation(defender, baseMobDamage, mobAttacker);

            // Set the calculated damage
            event.setDamage(finalDamage);

            // Apply mob-specific effects
            applyMobEffectsToPlayer(defender, mobAttacker);

            // Mark player in combat (for PvP tracking purposes)
            combatTimestamps.put(defender.getUniqueId(), System.currentTimeMillis());

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Error calculating mob damage: " + e.getMessage());
            // Fallback to original damage if calculation fails
        }
    }

    /**
     * Calculate base damage for a mob based on its type and metadata
     *
     * @param mob The attacking mob
     * @return The base damage amount
     */
    private double calculateMobBaseDamage(LivingEntity mob) {
        // Check for custom mob damage from metadata
        if (mob.hasMetadata("baseDamage")) {
            try {
                return mob.getMetadata("baseDamage").get(0).asDouble();
            } catch (Exception e) {
                // Fall through to default calculation
            }
        }

        // Check for weapon-based damage
        if (mob.getEquipment() != null && mob.getEquipment().getItemInMainHand() != null) {
            ItemStack weapon = mob.getEquipment().getItemInMainHand();
            if (weapon.getType() != Material.AIR && weapon.hasItemMeta() && weapon.getItemMeta().hasLore()) {
                List<Integer> damageRange = getDamageRange(weapon);
                if (damageRange.get(0) > 1 || damageRange.get(1) > 1) {
                    Random random = new Random();
                    return random.nextInt(damageRange.get(1) - damageRange.get(0) + 1) + damageRange.get(0);
                }
            }
        }

        // Default mob damage based on type
        EntityType mobType = mob.getType();
        switch (mobType) {
            case ZOMBIE:
            case SKELETON:
                return 8.0;
            case ZOMBIE_VILLAGER:
                return 9.0;
            case HUSK:
                return 10.0;
            case STRAY:
                return 7.0;
            case WITHER_SKELETON:
                return 15.0;
            case PIGLIN:
            case ZOMBIFIED_PIGLIN:
                return 12.0;
            case SPIDER:
            case CAVE_SPIDER:
                return 6.0;
            case CREEPER:
                return 20.0; // Explosion damage
            case ENDERMAN:
                return 14.0;
            case SLIME:
            case MAGMA_CUBE:
                return 4.0 + (mob.getMetadata("slime.size").isEmpty() ? 0 : mob.getMetadata("slime.size").get(0).asInt() * 2);
            case BLAZE:
                return 11.0;
            case GHAST:
                return 16.0;
            case WITHER:
                return 25.0;
            case ENDER_DRAGON:
                return 30.0;
            default:
                return 5.0; // Default for unknown mobs
        }
    }

    /**
     * Apply mob damage calculation including armor, resistances, etc.
     *
     * @param defender The player being attacked
     * @param baseDamage The base damage amount
     * @param mobAttacker The attacking mob
     * @return The final damage amount
     */
    private double applyMobDamageCalculation(Player defender, double baseDamage, LivingEntity mobAttacker) {
        double damage = baseDamage;

        // Calculate player's armor rating
        double armorRating = 0.0;
        for (ItemStack armor : defender.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                armorRating += getArmorValue(armor);

                // Add strength bonus to armor
                int strength = getElementalAttribute(armor, "STR");
                armorRating += strength * 0.1;
            }
        }

        // Apply diminishing returns to armor
        double effectiveArmor = calculateDiminishingReturns(armorRating, 500, 1.5);

        // Calculate damage reduction (cap at 75% for mobs to ensure they can still hurt players)
        double damageReduction = Math.min(0.75, effectiveArmor / 100.0);

        // Apply damage reduction
        double reducedDamage = damage * (1.0 - damageReduction);

        // Ensure minimum damage (mobs should always do at least 1 damage)
        double finalDamage = Math.max(1, Math.round(reducedDamage));

        // Show debug info if enabled
        if (Toggles.isToggled(defender, "Debug")) {
            int expectedHealth = Math.max(0, (int) (defender.getHealth() - finalDamage));
            double effectiveReduction = ((damage - finalDamage) / damage) * 100;

            String mobName = mobAttacker.hasMetadata("name") ?
                    mobAttacker.getMetadata("name").get(0).asString() :
                    mobAttacker.getType().name();

            defender.sendMessage(ChatColor.RED + "            -" + (int)finalDamage + ChatColor.RED + ChatColor.BOLD + "HP " +
                    ChatColor.GRAY + "[" + String.format("%.1f", effectiveReduction) + "%A] " +
                    ChatColor.GREEN + "[" + expectedHealth + ChatColor.BOLD + "HP" + ChatColor.GREEN + "] " +
                    ChatColor.GRAY + "from " + mobName);
        }

        return finalDamage;
    }

    /**
     * Apply mob-specific effects to players
     *
     * @param defender The player being attacked
     * @param mobAttacker The attacking mob
     */
    private void applyMobEffectsToPlayer(Player defender, LivingEntity mobAttacker) {
        EntityType mobType = mobAttacker.getType();

        switch (mobType) {
            case CAVE_SPIDER:
                // Poison effect
                defender.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                break;

            case WITHER_SKELETON:
                // Wither effect
                defender.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0));
                break;

            case HUSK:
                // Hunger effect
                defender.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 140, 0));
                break;

            case STRAY:
                // Slowness effect
                defender.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 0));
                break;

            case MAGMA_CUBE:
                // Fire damage
                defender.setFireTicks(60);
                break;
        }

        // Check for custom mob effects from metadata
        if (mobAttacker.hasMetadata("poisonOnHit")) {
            int duration = mobAttacker.getMetadata("poisonOnHit").get(0).asInt();
            defender.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0));
        }

        if (mobAttacker.hasMetadata("slowOnHit")) {
            int duration = mobAttacker.getMetadata("slowOnHit").get(0).asInt();
            defender.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration, 0));
        }

        if (mobAttacker.hasMetadata("witherOnHit")) {
            int duration = mobAttacker.getMetadata("witherOnHit").get(0).asInt();
            defender.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, 0));
        }
    }

    /**
     * Handle weapon damage calculation and effects (PLAYER ATTACKERS ONLY)
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWeaponDamage(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Only handle player attackers
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        YakPlayer yakAttacker = playerManager.getPlayer(attacker);

        if (yakAttacker == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // Get weapon
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Check for staff weapon
        if (MagicStaff.isRecentStaffShot(attacker)) {
            ItemStack staffWeapon = MagicStaff.getLastUsedStaff(attacker);
            if (staffWeapon != null) {
                weapon = staffWeapon;
            }
            MagicStaff.clearStaffShot(attacker);
        }

        // Skip if no valid weapon
        if (weapon == null || weapon.getType() == Material.AIR || !weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) {
            return;
        }

        // Calculate base damage
        List<Integer> damageRange = getDamageRange(weapon);
        int minDamage = damageRange.get(0);
        int maxDamage = damageRange.get(1);

        Random random = new Random();
        int damage = random.nextInt(maxDamage - minDamage + 1) + minDamage;

        // Add elemental damage
        damage += calculateElementalDamage(attacker, target, weapon);

        // Calculate attribute bonuses
        damage = applyAttributeBonuses(attacker, weapon, damage);

        // Calculate critical hit
        int critChance = calculateCriticalChance(attacker, weapon);
        boolean isCritical = random.nextInt(100) < critChance;

        if (isCritical) {
            damage *= 2;
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.5f, 0.5f);
            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
        }

        // Apply life steal if applicable
        applyLifeSteal(attacker, weapon, damage);

        // Apply thorns effect if target has thorns
        if (target instanceof Player defender) {
            int thornsChance = calculateThornsChance(defender);

            if (thornsChance > 0 && random.nextBoolean()) {
                int thornsDamage = (int) (damage * ((thornsChance * 0.5) / 100)) + 1;

                // Apply thorns damage
                attacker.getWorld().spawnParticle(Particle.BLOCK_CRACK, attacker.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.01, new MaterialData(Material.OAK_LEAVES));

                // Directly damage attacker to bypass armor
                double newHealth = Math.max(0, attacker.getHealth() - thornsDamage);
                attacker.setHealth(newHealth);
            }
        }

        // Set final damage
        event.setDamage(damage);

        // Mark combat status for PvP
        if (target instanceof Player) {
            markPlayerInCombat((Player) target, attacker);
        }
    }

    /**
     * Handle armor calculations (FOR PLAYER DEFENDERS)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorCalculation(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Only handle player defenders with player attackers
        if (!(event.getEntity() instanceof Player defender) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        double damage = event.getDamage();

        // Calculate armor rating
        double armorRating = 0.0;

        for (ItemStack armor : defender.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                armorRating += getArmorValue(armor);

                // Add strength bonus to armor (small percentage)
                int strength = getElementalAttribute(armor, "STR");
                armorRating += strength * 0.1; // 0.1 armor per strength point
            }
        }

        // Calculate armor penetration
        double armorPenetration = 0.0;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Weapon-based armor penetration
        if (weapon != null && weapon.getType() != Material.AIR && weapon.hasItemMeta() && weapon.getItemMeta().hasLore()) {
            armorPenetration = getAttributePercent(weapon, "ARMOR PEN") / 100.0;
        }

        // Dexterity-based armor penetration
        int dexterity = 0;
        for (ItemStack armor : attacker.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dexterity += getElementalAttribute(armor, "DEX");
            }
        }

        // Add dexterity bonus to armor penetration
        armorPenetration += dexterity * 0.0003; // 0.03% per dexterity point

        // Apply diminishing returns to armor
        double effectiveArmor = calculateDiminishingReturns(armorRating, 500, 1.5);

        // Apply armor penetration (capped at 100%)
        armorPenetration = Math.min(1.0, Math.max(0.0, armorPenetration));
        double remainingArmor = effectiveArmor * (1.0 - armorPenetration);

        // Calculate damage reduction (cap at 80%)
        double damageReduction = Math.min(0.8, remainingArmor / 100.0);

        // Apply damage reduction
        double reducedDamage = damage * (1.0 - damageReduction);
        int finalDamage = (int) Math.max(1, Math.round(reducedDamage));

        // Show debug info if applicable
        if (Toggles.isToggled(defender, "Debug")) {
            int expectedHealth = Math.max(0, (int) (defender.getHealth() - finalDamage));
            double effectiveReduction = ((damage - finalDamage) / damage) * 100;

            defender.sendMessage(ChatColor.RED + "            -" + finalDamage + ChatColor.RED + ChatColor.BOLD + "HP " + ChatColor.GRAY + "[" + String.format("%.2f", effectiveReduction) + "%A -> -" + (int) (damage - finalDamage) + ChatColor.BOLD + "DMG" + ChatColor.GRAY + "] " + ChatColor.GREEN + "[" + expectedHealth + ChatColor.BOLD + "HP" + ChatColor.GREEN + "]");
        }

        // Set final damage
        event.setDamage(finalDamage);
    }

    /**
     * Handle knockback with smoother, less glitchy effects
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onKnockback(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Only handle living entities
        if (!(event.getEntity() instanceof LivingEntity target) || !(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }

        // Skip if on cooldown
        UUID targetId = target.getUniqueId();
        if (knockbackCooldowns.containsKey(targetId) && System.currentTimeMillis() - knockbackCooldowns.get(targetId) < 200) { // Increased cooldown to prevent jitter
            return;
        }

        // Reset damage invulnerability
        target.setNoDamageTicks(0);

        // Mark cooldown
        knockbackCooldowns.put(targetId, System.currentTimeMillis());

        // Different knockback handling for players vs mobs for better feel
        boolean isTargetPlayer = target instanceof Player;

        // Apply smoother knockback with a task
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            applySmootherKnockback(target, attacker, isTargetPlayer);
        });
    }

    /**
     * Apply smoother knockback effect
     *
     * @param target         The entity being knocked back
     * @param attacker       The attacking entity
     * @param isTargetPlayer Whether the target is a player
     */
    private void applySmootherKnockback(LivingEntity target, LivingEntity attacker, boolean isTargetPlayer) {
        // Skip if entity died
        if (target.isDead()) return;

        // Less knockback for players, more for mobs
        double baseFactor = isTargetPlayer ? 0.2 : 0.35;
        double knockbackForce = KNOCKBACK_BASE * baseFactor;
        double verticalForce = KNOCKBACK_VERTICAL_BASE * baseFactor;

        // Add minimal randomness for natural feel
        Random random = new Random();
        double randomFactor = 1.0 + (random.nextDouble() * KNOCKBACK_RANDOMNESS);
        knockbackForce *= randomFactor;

        // Apply weapon-specific knockback for player attackers
        if (attacker instanceof Player player) {
            ItemStack weapon = player.getInventory().getItemInMainHand();

            if (weapon != null && weapon.getType() != Material.AIR) {
                // Different weapon types have different knockback profiles
                String weaponType = weapon.getType().name();

                if (weaponType.contains("_SHOVEL")) {
                    // Heavy knockback for shovel weapons
                    knockbackForce *= 1.2;
                    verticalForce *= 1.3;
                } else if (weaponType.contains("_AXE")) {
                    // Medium-heavy knockback for axes
                    knockbackForce *= 1.1;
                    verticalForce *= 1.1;
                } else if (weaponType.contains("_SWORD")) {
                    // Balanced knockback for swords
                    knockbackForce *= 1.0;
                    verticalForce *= 1.0;
                } else if (weaponType.contains("_HOE")) {
                    // Special knockback for staves/magic weapons
                    knockbackForce *= 0.8;
                    verticalForce *= 1.2;
                }

                // Apply bonus from weapon attributes
                double weaponKnockBonus = getAttributePercent(weapon, "KNOCKBACK") / 100.0;
                if (weaponKnockBonus > 0) {
                    knockbackForce *= (1 + weaponKnockBonus * 0.5);
                    verticalForce *= (1 + weaponKnockBonus * 0.3);
                }
            }
        }

        // Calculate direction with minimal randomness
        Vector knockbackDir = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();

        // Mobs get more predictable knockback (less random)
        if (!isTargetPlayer) {
            // Add minimal horizontal randomness
            knockbackDir.setX(knockbackDir.getX() + (random.nextDouble() * 0.05 - 0.025));
            knockbackDir.setZ(knockbackDir.getZ() + (random.nextDouble() * 0.05 - 0.025));
            knockbackDir.normalize();
        }

        // Create final knockback vector with appropriate vertical component
        Vector finalVel = knockbackDir.multiply(knockbackForce).setY(verticalForce);

        // For mobs, blend with existing velocity more gently
        Vector currentVel = target.getVelocity();
        double blendFactor = isTargetPlayer ? 0.5 : 0.3;
        Vector blendedVel = currentVel.multiply(1 - blendFactor).add(finalVel.multiply(blendFactor));

        // Ensure minimum Y component to prevent getting stuck in ground
        if (blendedVel.getY() < 0.08) {
            blendedVel.setY(Math.max(0.08, currentVel.getY()));
        }

        // Apply the knockback
        target.setVelocity(blendedVel);
    }

    /**
     * Handle polearm AoE attacks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPolearmAoeAttack(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Only handle player attackers and living entity targets
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity primaryTarget)) {
            return;
        }

        // Skip if already processed this swing
        if (polearmSwingProcessed.contains(attacker.getUniqueId())) {
            return;
        }

        // Check for polearm (shovel) weapon
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR || !weapon.getType().name().contains("_SHOVEL")) {
            return;
        }

        // Apply AoE attack to nearby entities
        polearmSwingProcessed.add(attacker.getUniqueId());

        try {
            // Consume energy
            YakPlayer yakAttacker = playerManager.getPlayer(attacker);
            if (yakAttacker != null) {
                Energy.getInstance().removeEnergy(attacker, 5);

                // Find nearby entities for AoE damage
                for (Entity nearbyEntity : primaryTarget.getNearbyEntities(1, 2, 1)) {
                    if (!(nearbyEntity instanceof LivingEntity secondaryTarget) || nearbyEntity == primaryTarget || nearbyEntity == attacker) {
                        continue;
                    }

                    // Reset damage invulnerability
                    secondaryTarget.setNoDamageTicks(0);

                    // Apply small amount of energy cost for each additional target
                    Energy.getInstance().removeEnergy(attacker, 2);

                    // Apply AoE damage
                    secondaryTarget.damage(1.0, attacker);
                }
            }
        } finally {
            // Ensure we always remove the processed flag
            polearmSwingProcessed.remove(attacker.getUniqueId());
        }
    }

    /**
     * Handle combat sounds
     */
    @EventHandler
    public void onDamageSound(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Handle player attacker sounds
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity target) {

            // Hit sound for attacker
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

            // Hit confirmation sound for player targets
            if (target instanceof Player) {
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 1.0f, 1.6f);
            } else {
                // Enhanced hit effects for mobs
                playEnhancedHitEffects(attacker, target, (int) event.getDamage());
            }
        }

        // Apply slow effect to players hit by mobs
        if (event.getEntity() instanceof Player victim && !(event.getDamager() instanceof Player) && event.getDamager() instanceof LivingEntity) {

            // Apply temporary movement slowdown
            victim.setWalkSpeed(0.165f);
            playerSlowEffects.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Handle debug info display
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDebugDisplay(EntityDamageByEntityEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Only handle player attackers and living entity targets
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        int damage = (int) event.getDamage();
        int remainingHealth = Math.max(0, (int) (target.getHealth() - damage));

        // Get target name
        String targetName = target instanceof Player ? target.getName() : (target.hasMetadata("name") ? target.getMetadata("name").get(0).asString() : "Unknown");

        // Show debug info if enabled
        Toggles.getInstance();
        if (Toggles.isToggled(attacker, "Debug")) {
            String message = String.format("%s%d%s DMG %s-> %s%s [%dHP]", ChatColor.RED, damage, ChatColor.RED.toString() + ChatColor.BOLD, ChatColor.RED, ChatColor.RESET, targetName, remainingHealth);

            attacker.sendMessage(message);
        }

        // Update combat state for PvP
        if (target instanceof Player) {
            markPlayerInCombat((Player) target, attacker);
        }
    }

    /**
     * FIXED: Only bypass armor for non-player entities to prevent issues with player damage calculation
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBypassArmor(EntityDamageEvent event) {
        // Skip if already cancelled or no damage
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // FIXED: Only process non-player entities to avoid interfering with player damage calculation
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }

        double damage = event.getDamage();

        // Apply enhanced hit effects for non-players if damaged by a player
        if (event instanceof EntityDamageByEntityEvent edbe) {
            if (edbe.getDamager() instanceof Player attacker) {
                playEnhancedHitEffects(attacker, entity, (int) damage);
            }
        }

        // Cancel normal damage calculation for mobs only
        event.setDamage(0.0);
        event.setCancelled(true);

        // Apply damage effects
        entity.playEffect(EntityEffect.HURT);
        entity.setLastDamageCause(event);

        // Tag the entity to show it's been recently hit
        entity.setMetadata("lastDamaged", new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis()));

        // Calculate new health
        double newHealth = entity.getHealth() - damage;

        // Set health directly
        if (newHealth <= 0.0) {
            entity.setHealth(0.0);
        } else {
            entity.setHealth(newHealth);
        }
    }

    /**
     * Play enhanced hit effects for entity damage
     *
     * @param attacker The attacking player
     * @param target   The target entity
     * @param damage   The damage amount
     */
    private void playEnhancedHitEffects(Player attacker, LivingEntity target, int damage) {
        // Skip if recently played effects for this entity
        UUID targetId = target.getUniqueId();
        long now = System.currentTimeMillis();

        if (entityDamageEffects.containsKey(targetId) && now - entityDamageEffects.get(targetId) < 200) {
            return;
        }

        // Track this effect
        entityDamageEffects.put(targetId, now);

        // Play enhanced hit sounds
        Sound hitSound = Sound.ENTITY_ZOMBIE_HURT;

        // Different sounds based on entity type for better feedback
        EntityType entityType = target.getType();

        switch (entityType) {
            case SKELETON:
                hitSound = Sound.ENTITY_SKELETON_HURT;
                break;
            case PIGLIN, ZOMBIFIED_PIGLIN:
                hitSound = Sound.ENTITY_PIGLIN_HURT;
                break;
            case ZOMBIE:
                hitSound = Sound.ENTITY_ZOMBIE_HURT;
                break;
            case WITHER_SKELETON:
                hitSound = Sound.ENTITY_WITHER_SKELETON_HURT;
                break;
            case CAVE_SPIDER:
                hitSound = Sound.ENTITY_SPIDER_HURT;
                break;
            case SLIME, MAGMA_CUBE:
                hitSound = Sound.ENTITY_SLIME_HURT;
                break;
        }

        // Play sound with slightly randomized pitch for variety
        float pitch = 0.8f + (float) (Math.random() * 0.4);
        target.getWorld().playSound(target.getLocation(), hitSound, 1.0f, pitch);

        // Play a hit sound for the attacker (better feedback)
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.4f, 0.8f + (float) (Math.random() * 0.4));

        // Impact effect
        Location effectLoc = target.getLocation().add(0, 1.0, 0);
        target.getWorld().spawnParticle(Particle.CRIT, effectLoc, damage * 2, 0.3, 0.3, 0.3, 0.05);

        // Dust particles (varies by damage for better feedback)
        if (damage > 5) {
            target.getWorld().spawnParticle(Particle.CLOUD, effectLoc, 3, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /**
     * Handle dummy target for weapon testing
     */
    @EventHandler
    public void onDummyUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ARMOR_STAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon == null || weapon.getType() == Material.AIR || !weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) {
            return;
        }

        // Calculate damage against dummy
        List<Integer> damageRange = getDamageRange(weapon);
        int minDamage = damageRange.get(0);
        int maxDamage = damageRange.get(1);

        Random random = new Random();
        int damage = random.nextInt(maxDamage - minDamage + 1) + minDamage;

        // Add elemental damage
        List<String> lore = weapon.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("ICE DMG")) {
                damage += getElementalAttribute(weapon, "ICE DMG");
            }
            if (line.contains("POISON DMG")) {
                damage += getElementalAttribute(weapon, "POISON DMG");
            }
            if (line.contains("FIRE DMG")) {
                damage += getElementalAttribute(weapon, "FIRE DMG");
            }
            if (line.contains("PURE DMG")) {
                damage += getElementalAttribute(weapon, "PURE DMG");
            }
        }

        // Calculate stat bonuses
        double dpsBonus = 0.0;
        double vitality = 0.0;
        double dexterity = 0.0;
        double intelligence = 0.0;
        double strength = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dpsBonus += getArmorValue(armor);
                vitality += getElementalAttribute(armor, "VIT");
                dexterity += getElementalAttribute(armor, "DEX");
                intelligence += getElementalAttribute(armor, "INT");
                strength += getElementalAttribute(armor, "STR");
            }
        }

        // Apply weapon-specific stat bonuses
        String weaponType = weapon.getType().name();
        if (weaponType.contains("_SWORD") && vitality > 0) {
            damage = (int) (damage * (1 + vitality / 5000.0));
        }
        if (weaponType.contains("_AXE") && strength > 0) {
            damage = (int) (damage * (1 + strength / 4500.0));
        }
        if (weaponType.contains("_HOE") && intelligence > 0) {
            damage = (int) (damage * (1 + intelligence / 100.0));
        }

        // Apply DPS bonus
        if (dpsBonus > 0) {
            damage = (int) (damage * (1 + dpsBonus / 100.0));
        }

        // Cancel the event to prevent normal block damage
        event.setCancelled(true);

        // Display damage information
        player.sendMessage(ChatColor.RED + "            " + damage + ChatColor.RED + ChatColor.BOLD + " DMG " + ChatColor.RED + "-> " + ChatColor.RESET + "DPS DUMMY" + " [" + 99999999 + "HP]");
    }

    /**
     * Calculate block chance for a player
     *
     * @param player The player to calculate for
     * @return The block chance percentage
     */
    private int calculateBlockChance(Player player) {
        int blockChance = 0;
        int strength = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                blockChance += getAttributePercent(armor, "BLOCK");
                strength += getElementalAttribute(armor, "STR");
            }
        }

        // Add strength bonus to block chance
        blockChance += Math.round(strength * 0.015f);

        // Cap at maximum block chance
        return Math.min(blockChance, MAX_BLOCK_CHANCE);
    }

    /**
     * Calculate dodge chance for a player
     *
     * @param player The player to calculate for
     * @return The dodge chance percentage
     */
    private int calculateDodgeChance(Player player) {
        int dodgeChance = 0;
        int dexterity = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dodgeChance += getAttributePercent(armor, "DODGE");
                dexterity += getElementalAttribute(armor, "DEX");
            }
        }

        // Add dexterity bonus to dodge chance
        dodgeChance += Math.round(dexterity * 0.015f);

        // Cap at maximum dodge chance
        return Math.min(dodgeChance, MAX_DODGE_CHANCE);
    }

    /**
     * Calculate accuracy for a player
     *
     * @param player The player to calculate for
     * @return The accuracy percentage
     */
    private int getAccuracy(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        return getAttributePercent(weapon, "ACCURACY");
    }

    /**
     * Calculate thorns chance for a player
     *
     * @param player The player to calculate for
     * @return The thorns percentage
     */
    private int calculateThornsChance(Player player) {
        int thornsChance = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                thornsChance += getAttributePercent(armor, "THORNS");
            }
        }

        return thornsChance;
    }

    /**
     * Calculate critical hit chance for a player
     *
     * @param player The player to calculate for
     * @param weapon The weapon being used
     * @return The critical hit chance percentage
     */
    private int calculateCriticalChance(Player player, ItemStack weapon) {
        int critChance = 0;

        // Base critical chance from weapon
        critChance += getAttributePercent(weapon, "CRITICAL HIT");

        // Weapon type bonus (axes have higher crit chance)
        if (weapon.getType().name().contains("_AXE")) {
            critChance += 10;
        }

        // Intelligence bonus to critical chance
        int intelligence = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                intelligence += getElementalAttribute(armor, "INT");
            }
        }

        if (intelligence > 0) {
            critChance += Math.round(intelligence * 0.015f);
        }

        return critChance;
    }

    /**
     * Apply attribute bonuses to damage calculation
     *
     * @param player     The attacking player
     * @param weapon     The weapon being used
     * @param baseDamage The base damage
     * @return The modified damage
     */
    private int applyAttributeBonuses(Player player, ItemStack weapon, int baseDamage) {
        double damage = baseDamage;

        // Calculate attribute totals
        double dpsBonus = 0.0;
        double vitality = 0.0;
        double dexterity = 0.0;
        double intelligence = 0.0;
        double strength = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dpsBonus += getDpsValue(armor);
                vitality += getElementalAttribute(armor, "VIT");
                dexterity += getElementalAttribute(armor, "DEX");
                intelligence += getElementalAttribute(armor, "INT");
                strength += getElementalAttribute(armor, "STR");
            }
        }

        // Apply weapon-specific attribute bonuses
        String weaponType = weapon.getType().name();

        if (weaponType.contains("_SWORD") && vitality > 0) {
            // Swords benefit from vitality
            damage *= (1 + vitality / 5000.0);
        }

        if (weaponType.contains("_AXE") && strength > 0) {
            // Axes benefit from strength
            damage *= (1 + strength / 4500.0);
        }

        if (weaponType.contains("_HOE") && intelligence > 0) {
            // Staves benefit from intelligence
            damage *= (1 + intelligence / 100.0);
        }

        // Apply general DPS bonus
        if (dpsBonus > 0) {
            damage *= (1 + dpsBonus / 100.0);
        }

        return (int) Math.round(damage);
    }

    /**
     * Calculate elemental damage from a weapon
     *
     * @param attacker The attacking player
     * @param target   The target entity
     * @param weapon   The weapon being used
     * @return The total elemental damage
     */
    private int calculateElementalDamage(Player attacker, LivingEntity target, ItemStack weapon) {
        int elementalDamage = 0;
        List<String> lore = weapon.getItemMeta().getLore();
        int dexterity = 0;

        // Calculate dexterity for elemental bonus
        for (ItemStack armor : attacker.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dexterity += getElementalAttribute(armor, "DEX");
            }
        }

        // Get weapon tier for effect duration
        int tier = getWeaponTier(weapon);

        for (String line : lore) {
            // Ice damage
            if (line.contains("ICE DMG")) {
                int iceDamage = getElementalAttribute(weapon, "ICE DMG");
                int iceDamageBonus = Math.round(iceDamage * (1 + Math.round(dexterity / 3000f)));
                elementalDamage += iceDamageBonus;

                // Apply slow effect with correct blue ice particle
                target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, ICE_EFFECT_ID);

                int duration = 40 + (tier * 5);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration, 0));
            }

            // Poison damage
            if (line.contains("POISON DMG")) {
                int poisonDamage = getElementalAttribute(weapon, "POISON DMG");
                int poisonDamageBonus = Math.round(poisonDamage * (1 + Math.round(dexterity / 3000f)));
                elementalDamage += poisonDamageBonus;

                // Apply poison effect with green particles
                target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, POISON_EFFECT_ID);

                int duration = 15 + (tier * 5);
                int amplifier = tier >= 3 ? 1 : 0;
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, amplifier));
            }

            // Fire damage
            if (line.contains("FIRE DMG")) {
                int fireDamage = getElementalAttribute(weapon, "FIRE DMG");
                int fireDamageBonus = Math.round(fireDamage * (1 + Math.round(dexterity / 3000f)));
                elementalDamage += fireDamageBonus;

                // Apply fire effect with orange/red particles
                target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, FIRE_EFFECT_ID);
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);

                int fireDuration = 15 + (tier * 5);
                target.setFireTicks(fireDuration);
            }

            // Pure damage (no effects)
            if (line.contains("PURE DMG")) {
                int pureDamage = getElementalAttribute(weapon, "PURE DMG");
                int pureDamageBonus = Math.round(pureDamage * (1 + Math.round(dexterity / 3000f)));
                elementalDamage += pureDamageBonus;
            }
        }

        // Apply target type bonuses
        if (target instanceof Player && hasBonus(weapon, "VS PLAYERS")) {
            elementalDamage *= (1 + getAttributePercent(weapon, "VS PLAYERS") / 100.0);
        } else if (!(target instanceof Player) && hasBonus(weapon, "VS MONSTERS")) {
            elementalDamage *= (1 + getAttributePercent(weapon, "VS MONSTERS") / 100.0);
        }

        return elementalDamage;
    }

    /**
     * Apply life steal to a player
     *
     * @param player The player to heal
     * @param weapon The weapon with life steal
     * @param damage The damage dealt
     */
    private void applyLifeSteal(Player player, ItemStack weapon, int damage) {
        if (!hasBonus(weapon, "LIFE STEAL")) {
            return;
        }

        // Calculate life steal amount
        double lifeStealPercent = getAttributePercent(weapon, "LIFE STEAL");
        int healAmount = Math.max(1, (int) (damage * (lifeStealPercent / 125.0)));

        // Apply visual effect
        player.getWorld().playEffect(player.getLocation().add(0, 1.5, 0), Effect.STEP_SOUND, Material.REDSTONE_BLOCK);

        // Heal the player
        double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + healAmount);
        player.setHealth(newHealth);

        // Show debug message if enabled
        Toggles.getInstance();
        if (Toggles.isToggled(player, "Debug")) {
            player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "            +" + ChatColor.GREEN + healAmount + ChatColor.GREEN + ChatColor.BOLD + " HP " + ChatColor.GRAY + "[" + (int) player.getHealth() + "/" + (int) player.getMaxHealth() + "HP]");
        }
    }

    /**
     * Apply diminishing returns to a value
     *
     * @param value The input value
     * @param scale The scale factor
     * @param power The power factor (higher = more diminishing)
     * @return The value after diminishing returns
     */
    private double calculateDiminishingReturns(double value, double scale, double power) {
        return value / (1.0 + Math.pow(value / scale, power));
    }

    /**
     * Get the weapon tier based on its color/name
     *
     * @param weapon The weapon to check
     * @return The tier (1-5)
     */
    private int getWeaponTier(ItemStack weapon) {
        if (!weapon.hasItemMeta() || !weapon.getItemMeta().hasDisplayName()) {
            return 1;
        }

        String name = weapon.getItemMeta().getDisplayName();

        if (name.contains(ChatColor.WHITE.toString())) {
            return 1;
        } else if (name.contains(ChatColor.GREEN.toString())) {
            return 2;
        } else if (name.contains(ChatColor.BLUE.toString())) {
            return 3;
        } else if (name.contains(ChatColor.DARK_PURPLE.toString())) {
            return 4;
        } else if (name.contains(ChatColor.GOLD.toString())) {
            return 5;
        }

        return 1;
    }

    /**
     * Get the damage range for a weapon
     *
     * @param weapon The weapon to check
     * @return List containing [minDamage, maxDamage]
     */
    private List<Integer> getDamageRange(ItemStack weapon) {
        List<Integer> range = new ArrayList<>(Arrays.asList(1, 1));

        if (weapon != null && weapon.getType() != Material.AIR && weapon.hasItemMeta() && weapon.getItemMeta().hasLore()) {
            List<String> lore = weapon.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("DMG:")) {
                    try {
                        String[] parts = line.split("DMG: ")[1].split(" - ");
                        int min = Integer.parseInt(parts[0]);
                        int max = Integer.parseInt(parts[1]);
                        range.set(0, min);
                        range.set(1, max);
                        break;
                    } catch (Exception e) {
                        // Keep default values
                    }
                }
            }
        }

        return range;
    }

    /**
     * Get the armor value from an item
     *
     * @param item The item to check
     * @return The armor value
     */
    private int getArmorValue(ItemStack item) {
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("ARMOR")) {
                    try {
                        return Integer.parseInt(line.split(" - ")[1].split("%")[0]);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Get the DPS value from an item
     *
     * @param item The item to check
     * @return The DPS value
     */
    private int getDpsValue(ItemStack item) {
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("DPS")) {
                    try {
                        return Integer.parseInt(line.split(" - ")[1].split("%")[0]);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Get an elemental attribute value from an item
     *
     * @param item      The item to check
     * @param attribute The attribute name (e.g., "STR", "DEX")
     * @return The attribute value
     */
    private int getElementalAttribute(ItemStack item, String attribute) {
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains(attribute)) {
                    try {
                        return Integer.parseInt(line.split(": ")[1]);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Get a percentage attribute from an item
     *
     * @param item      The item to check
     * @param attribute The attribute name (e.g., "BLOCK", "DODGE")
     * @return The attribute percentage
     */
    private int getAttributePercent(ItemStack item, String attribute) {
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains(attribute)) {
                    try {
                        return Integer.parseInt(line.split(": ")[1].split("%")[0]);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Check if an item has a specific bonus attribute
     *
     * @param item      The item to check
     * @param attribute The attribute name
     * @return true if the item has the attribute
     */
    private boolean hasBonus(ItemStack item, String attribute) {
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains(attribute)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Utility method to check if a player has god mode disabled
     *
     * @param player The player to check
     * @return true if god mode is disabled
     */
    private boolean isGodModeDisabled(Player player) {
        Toggles.getInstance();
        return Toggles.isToggled(player, "God Mode Disabled");
    }

    /**
     * Checks if the location is in a safe zone based on Alignments
     *
     * @param location The location to check
     * @return true if the location is in a safe zone
     */
    private boolean isSafeZone(Location location) {
        return AlignmentMechanics.isSafeZone(location);
    }
}