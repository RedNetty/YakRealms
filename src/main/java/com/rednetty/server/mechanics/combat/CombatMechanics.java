package com.rednetty.server.mechanics.combat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.holograms.CombatHologramHandler;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.world.mobs.CritManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stamina.Energy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UPDATED: Enhanced combat mechanics with improved hologram system
 * - Damage calculation and application with authentic legacy calculations
 * - Armor, block, and dodge mechanics
 * - Critical hits and combat effects
 * - Advanced animated hologram system with arcing trajectories
 * - Enhanced PVP protection system
 * - FIXED: Proper mob-to-player damage calculations
 * - FIXED: Elite mob explosion damage bypass system
 */
public class CombatMechanics implements Listener {
    // Constants
    private static final int MAX_BLOCK_CHANCE = 60;
    private static final int MAX_DODGE_CHANCE = 60;
    private static final int COMBAT_DURATION = 5000; // Milliseconds
    private static final int PLAYER_SLOW_DURATION = 3000; // Milliseconds

    // Knockback constants
    private static final double KNOCKBACK_BASE = 2.5;
    private static final double KNOCKBACK_VERTICAL_BASE = 0.45;
    private static final double KNOCKBACK_RANDOMNESS = 0.01;
    private static final double WEAPON_KNOCK_MODIFIER = 1.1;

    // Elemental effect IDs
    private static final int ICE_EFFECT_ID = 13565951;
    private static final int POISON_EFFECT_ID = 8196;
    private static final int FIRE_EFFECT_ID = 8259;

    // Tracking maps
    private final Map<UUID, Long> combatTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerSlowEffects = new ConcurrentHashMap<>();
    private final Map<UUID, Long> knockbackCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> polearmSwingProcessed = new HashSet<>();

    // Entity damage visualization tracking
    private final Map<UUID, Long> entityDamageEffects = new ConcurrentHashMap<>();

    // Dependencies
    private final YakPlayerManager playerManager;
    private final CombatHologramHandler hologramHandler;

    public CombatMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.hologramHandler = CombatHologramHandler.getInstance();
    }

    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
        hologramHandler.onEnable();
        startMovementSpeedRestoreTask();
        startEntityDamageEffectCleanupTask();
        YakRealms.log("Combat Mechanics have been enabled with advanced hologram system");
    }

    public void onDisable() {
        hologramHandler.onDisable();
        YakRealms.log("Combat Mechanics have been disabled");
    }

    // ============= ENHANCED PVP PROTECTION SYSTEM =============

    /**
     * Comprehensive PVP protection check that handles friends, party members, and settings
     * @param attacker The attacking player
     * @param victim The victim player
     * @return PVPResult indicating if PVP should be allowed and why
     */
    public PVPResult checkPVPProtection(Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return new PVPResult(false, "Invalid players");
        }

        // Don't allow self-damage
        if (attacker.equals(victim)) {
            return new PVPResult(false, "Cannot attack yourself");
        }

        YakPlayer attackerData = playerManager.getPlayer(attacker);
        YakPlayer victimData = playerManager.getPlayer(victim);

        if (attackerData == null || victimData == null) {
            return new PVPResult(false, "Player data not loaded");
        }

        // Check if attacker has Anti PVP enabled
        if (attackerData.isToggled("Anti PVP")) {
            return new PVPResult(false, "You have PVP disabled! Use /toggle to enable it.",
                    PVPResult.ResultType.ATTACKER_PVP_DISABLED);
        }

        // Check if victim has Anti PVP enabled
        if (victimData.isToggled("Anti PVP")) {
            return new PVPResult(false, victim.getName() + " has PVP disabled!",
                    PVPResult.ResultType.VICTIM_PVP_DISABLED);
        }

        // Check buddy protection (requires friendly fire to be enabled by ATTACKER)
        if (attackerData.isBuddy(victim.getName())) {
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, "You cannot attack your buddy " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.",
                        PVPResult.ResultType.BUDDY_PROTECTION);
            }
        }

        // Check if victim considers attacker a buddy (mutual protection)
        if (victimData.isBuddy(attacker.getName())) {
            if (!victimData.isToggled("Friendly Fire")) {
                return new PVPResult(false, victim.getName() + " has you as a buddy and friendly fire disabled!",
                        PVPResult.ResultType.MUTUAL_BUDDY_PROTECTION);
            }
        }

        // Check party member protection
        if (PartyMechanics.getInstance().arePartyMembers(attacker, victim)) {
            // Check attacker's friendly fire setting
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, "You cannot attack your party member " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.",
                        PVPResult.ResultType.PARTY_PROTECTION);
            }

            // Check victim's friendly fire setting for mutual protection
            if (!victimData.isToggled("Friendly Fire")) {
                return new PVPResult(false, victim.getName() + " is your party member and has friendly fire disabled!",
                        PVPResult.ResultType.MUTUAL_PARTY_PROTECTION);
            }
        }

        // Check guild protection (if guild system is implemented)
        if (isInSameGuild(attacker, victim)) {
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, "You cannot attack your guild member " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.",
                        PVPResult.ResultType.GUILD_PROTECTION);
            }
        }

        // Check chaotic protection
        if (attackerData.isToggled("Chaotic Protection") && isLawfulPlayer(victim)) {
            return new PVPResult(false, "Chaotic Protection prevented you from attacking " + victim.getName() + "! This player is lawful. Disable Chaotic Protection to attack them.",
                    PVPResult.ResultType.CHAOTIC_PROTECTION);
        }

        // Check safe zone protection
        if (isSafeZone(attacker.getLocation()) || isSafeZone(victim.getLocation())) {
            return new PVPResult(false, "PVP is not allowed in safe zones!",
                    PVPResult.ResultType.SAFE_ZONE);
        }

        // All checks passed - PVP is allowed
        return new PVPResult(true, "PVP allowed");
    }

    /**
     * Result class for PVP protection checks
     */
    public static class PVPResult {
        public enum ResultType {
            ALLOWED,
            ATTACKER_PVP_DISABLED,
            VICTIM_PVP_DISABLED,
            BUDDY_PROTECTION,
            MUTUAL_BUDDY_PROTECTION,
            PARTY_PROTECTION,
            MUTUAL_PARTY_PROTECTION,
            GUILD_PROTECTION,
            CHAOTIC_PROTECTION,
            SAFE_ZONE,
            OTHER
        }

        private final boolean allowed;
        private final String message;
        private final ResultType resultType;

        public PVPResult(boolean allowed, String message) {
            this(allowed, message, allowed ? ResultType.ALLOWED : ResultType.OTHER);
        }

        public PVPResult(boolean allowed, String message, ResultType resultType) {
            this.allowed = allowed;
            this.message = message;
            this.resultType = resultType;
        }

        public boolean isAllowed() { return allowed; }
        public String getMessage() { return message; }
        public ResultType getResultType() { return resultType; }
    }

    /**
     * Enhanced PVP damage handler with comprehensive protection
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnhancedPvpProtection(EntityDamageByEntityEvent event) {
        // Only handle player vs player damage
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        if (event.getDamage() <= 0.0 || event.isCancelled()) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        // Check all PVP protections
        PVPResult result = checkPVPProtection(attacker, victim);

        if (!result.isAllowed()) {
            event.setCancelled(true);
            event.setDamage(0.0);

            // Show immunity hologram
            hologramHandler.showCombatHologram(attacker, victim, CombatHologramHandler.HologramType.IMMUNE, 0);

            // Send appropriate message to attacker
            attacker.sendMessage(ChatColor.RED + "§l⚠ §c" + result.getMessage());

            // Play appropriate sound based on protection type
            Sound sound = getProtectionSound(result.getResultType());
            attacker.playSound(attacker.getLocation(), sound, 1.0f, 0.5f);

            // Send additional help message for certain protection types
            sendAdditionalHelpMessage(attacker, result.getResultType());

            // Log the blocked PVP attempt if needed
            logBlockedPVPAttempt(attacker, victim, result.getResultType());

            return;
        }

        // PVP is allowed - let the event continue to other handlers
        // The existing damage calculation logic in other handlers will take over
    }

    /**
     * Get appropriate sound for different protection types
     */
    private Sound getProtectionSound(PVPResult.ResultType resultType) {
        switch (resultType) {
            case BUDDY_PROTECTION:
            case MUTUAL_BUDDY_PROTECTION:
                return Sound.ENTITY_VILLAGER_NO;
            case PARTY_PROTECTION:
            case MUTUAL_PARTY_PROTECTION:
                return Sound.BLOCK_NOTE_BLOCK_BASS;
            case GUILD_PROTECTION:
                return Sound.ENTITY_HORSE_ANGRY;
            case SAFE_ZONE:
                return Sound.BLOCK_ANVIL_LAND;
            default:
                return Sound.BLOCK_NOTE_BLOCK_BASS;
        }
    }

    /**
     * Send additional help messages for certain protection types
     */
    private void sendAdditionalHelpMessage(Player attacker, PVPResult.ResultType resultType) {
        switch (resultType) {
            case BUDDY_PROTECTION:
            case PARTY_PROTECTION:
            case GUILD_PROTECTION:
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    if (attacker.isOnline()) {
                        attacker.sendMessage(ChatColor.GRAY + "Enable " + ChatColor.WHITE + "Friendly Fire" +
                                ChatColor.GRAY + " in " + ChatColor.WHITE + "/toggle" +
                                ChatColor.GRAY + " to allow this.");
                    }
                }, 5L);
                break;
            case CHAOTIC_PROTECTION:
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    if (attacker.isOnline()) {
                        attacker.sendMessage(ChatColor.GRAY + "Disable " + ChatColor.WHITE + "Chaotic Protection" +
                                ChatColor.GRAY + " in " + ChatColor.WHITE + "/toggle" +
                                ChatColor.GRAY + " to attack lawful players.");
                    }
                }, 5L);
                break;
        }
    }

    /**
     * Log blocked PVP attempts for monitoring and admin purposes
     */
    private void logBlockedPVPAttempt(Player attacker, Player victim, PVPResult.ResultType resultType) {
        if (Toggles.isToggled(attacker, "Debug")) {
        }

        // Log to console for admins if needed
        YakRealms.getInstance().getLogger().fine("PVP blocked: " + attacker.getName() +
                " -> " + victim.getName() +
                " (Reason: " + resultType.name() + ")");
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

        // Both must be in a guild and the same guild
        return guild1 != null && !guild1.trim().isEmpty() &&
                guild2 != null && !guild2.trim().isEmpty() &&
                guild1.equals(guild2);
    }

    /**
     * Check if a player is lawful (for chaotic protection)
     */
    private boolean isLawfulPlayer(Player player) {
        if (player == null) return false;

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && "LAWFUL".equals(yakPlayer.getAlignment());
    }

    /**
     * Public API method for other plugins to check if PVP is allowed
     */
    public boolean isPVPAllowed(Player attacker, Player victim) {
        return checkPVPProtection(attacker, victim).isAllowed();
    }

    /**
     * Public API method to get detailed PVP check result
     */
    public PVPResult getPVPCheckResult(Player attacker, Player victim) {
        return checkPVPProtection(attacker, victim);
    }

    // ============= AUTHENTIC LEGACY STAT CALCULATION METHODS =============

    public static int getHp(ItemStack is) {
        List<String> lore;
        if (is != null && is.getType() != Material.AIR && is.getItemMeta().hasLore() &&
                (lore = is.getItemMeta().getLore()).size() > 1 && lore.get(1).contains("HP")) {
            try {
                return Integer.parseInt(lore.get(1).split(": +")[1]);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public static int getArmor(ItemStack is) {
        List<String> lore;
        if (is != null && is.getType() != Material.AIR && is.getItemMeta().hasLore() &&
                (lore = is.getItemMeta().getLore()).size() > 0 && lore.get(0).contains("ARMOR")) {
            try {
                return Integer.parseInt(lore.get(0).split(" - ")[1].split("%")[0]);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public static int getDps(ItemStack is) {
        List<String> lore;
        if (is != null && is.getType() != Material.AIR && is.getItemMeta().hasLore() &&
                (lore = is.getItemMeta().getLore()).size() > 0 && lore.get(0).contains("DPS")) {
            try {
                return Integer.parseInt(lore.get(0).split(" - ")[1].split("%")[0]);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public static int getEnergy(ItemStack is) {
        List<String> lore;
        if (is != null && is.getType() != Material.AIR && is.getItemMeta().hasLore() &&
                (lore = is.getItemMeta().getLore()).size() > 2 && lore.get(2).contains("ENERGY REGEN")) {
            try {
                return Integer.parseInt(lore.get(2).split(": +")[1].split("%")[0]);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public static int getHps(ItemStack is) {
        List<String> lore;
        if (is != null && is.getType() != Material.AIR && is.getItemMeta().hasLore() &&
                (lore = is.getItemMeta().getLore()).size() > 2 && lore.get(2).contains("HP REGEN")) {
            try {
                return Integer.parseInt(lore.get(2).split(": +")[1].split("/s")[0]);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public static int getPercent(ItemStack is, String type) {
        if (is != null && is.getType() != Material.AIR && is.getItemMeta().hasLore()) {
            List<String> lore = is.getItemMeta().getLore();
            for (String s : lore) {
                if (!s.contains(type)) continue;
                try {
                    return Integer.parseInt(s.split(": ")[1].split("%")[0]);
                } catch (Exception e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public static int getElem(ItemStack itemStack, String type) {
        if (itemStack != null && itemStack.getType() != Material.AIR && itemStack.hasItemMeta()) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta.hasLore()) {
                List<String> lore = itemMeta.getLore();
                for (String line : lore) {
                    if (line.contains(type)) {
                        try {
                            return Integer.parseInt(line.split(": +")[1]);
                        } catch (Exception e) {
                            return 0;
                        }
                    }
                }
            }
        }
        return 0;
    }

    public static List<Integer> getDamageRange(ItemStack itemStack) {
        List<Integer> damageRange = new ArrayList<>(Arrays.asList(1, 1));
        if (itemStack != null && itemStack.getType() != Material.AIR && itemStack.hasItemMeta()) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta.hasLore()) {
                List<String> lore = itemMeta.getLore();
                if (lore.size() > 0 && lore.get(0).contains("DMG")) {
                    try {
                        String[] dmgValues = lore.get(0).split("DMG: ")[1].split(" - ");
                        int min = Integer.parseInt(dmgValues[0]);
                        int max = Integer.parseInt(dmgValues[1]);
                        damageRange.set(0, min);
                        damageRange.set(1, max);
                    } catch (Exception e) {
                        damageRange.set(0, 1);
                        damageRange.set(1, 1);
                    }
                }
            }
        }
        return damageRange;
    }

    public static int getCrit(Player player) {
        int crit = 0;
        ItemStack weapon = player.getInventory().getItemInMainHand();

        // Check for staff weapon using existing system
        if (MagicStaff.isRecentStaffShot(player)) {
            weapon = MagicStaff.getLastUsedStaff(player);
        }

        if (weapon != null && weapon.getType() != Material.AIR && weapon.hasItemMeta()) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta.hasLore()) {
                List<String> lore = weaponMeta.getLore();
                for (String line : lore) {
                    if (line.contains("CRITICAL HIT")) {
                        crit = getPercent(weapon, "CRITICAL HIT");
                    }
                }
                if (weapon.getType().name().contains("_AXE")) {
                    crit += 10;
                }
                int intel = 0;
                ItemStack[] armorContents = player.getInventory().getArmorContents();
                for (ItemStack armor : armorContents) {
                    if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta()) {
                        int addInt = getElem(armor, "INT");
                        intel += addInt;
                    }
                }
                if (intel > 0) {
                    crit += Math.round(intel * 0.015);
                }
            }
        }
        return crit;
    }

    // ============= AUTHENTIC LEGACY COMBAT CALCULATIONS =============

    private double calculateWeaponTypeBonus(double baseDamage, double statValue, double divisor) {
        return baseDamage * (1 + (statValue / divisor));
    }

    private int calculateLifeSteal(double damageDealt, double lifeStealPercentage) {
        return Math.max(1, (int) (damageDealt * (lifeStealPercentage / 125.0)));
    }

    private double[] calculateStats(Player p) {
        double dps = 0.0;
        double vit = 0.0;
        double str = 0.0;

        for (ItemStack is : p.getInventory().getArmorContents()) {
            if (is != null && is.getType() != Material.AIR && is.hasItemMeta() && is.getItemMeta().hasLore()) {
                dps += getDps(is);
                vit += getElem(is, "VIT");
                str += getElem(is, "STR");
                dps += getElem(is, "DEX") * 0.012; // DEX contribution to DPS
            }
        }

        return new double[]{dps, vit, str};
    }

    // ============= FIXED: MOB-TO-PLAYER DAMAGE CALCULATION SYSTEM =============

    /**
     * FIXED: Calculate proper mob damage based on mob's weapon and stats
     */
    private double calculateMobDamage(LivingEntity mobAttacker, Player victim) {
        try {
            // Get mob's weapon
            ItemStack weapon = null;
            if (mobAttacker.getEquipment() != null) {
                weapon = mobAttacker.getEquipment().getItemInMainHand();
            }

            // If no weapon, use tier-based fallback
            if (weapon == null || weapon.getType() == Material.AIR) {
                return calculateTierBasedDamage(mobAttacker);
            }

            // Calculate damage from weapon stats
            List<Integer> damageRange = getDamageRange(weapon);
            int minDamage = damageRange.get(0);
            int maxDamage = damageRange.get(1);

            // Random damage within range
            int baseDamage = ThreadLocalRandom.current().nextInt(minDamage, maxDamage + 1);

            // Add elemental damage
            baseDamage += calculateMobElementalDamage(weapon);

            // Apply tier-based multipliers
            int tier = MobUtils.getMobTier(mobAttacker);
            boolean isElite = MobUtils.isElite(mobAttacker);

            double tierMultiplier = 1.0;
            switch (tier) {
                case 1: tierMultiplier = 0.8; break;
                case 2: tierMultiplier = 1.0; break;
                case 3: tierMultiplier = 1.2; break;
                case 4: tierMultiplier = 1.5; break;
                case 5: tierMultiplier = 2.0; break;
                case 6: tierMultiplier = 2.5; break;
            }

            if (isElite) {
                tierMultiplier *= 1.5; // Elite multiplier
            }

            baseDamage = (int) (baseDamage * tierMultiplier);

            // Apply VS PLAYERS bonus if present
            if (hasBonus(weapon, "VS PLAYERS")) {
                double bonus = getPercent(weapon, "VS PLAYERS") / 100.0;
                baseDamage = (int) (baseDamage * (1 + bonus));
            }

            // Check for critical hit via CritManager
            CustomMob customMob = MobManager.getInstance().getCustomMob(mobAttacker);
            if (customMob != null) {
                double critDamage = CritManager.getInstance().handleCritAttack(customMob, victim, baseDamage);
                if (critDamage > baseDamage) {
                    // Show critical hit hologram
                    hologramHandler.showCombatHologram(mobAttacker, victim, CombatHologramHandler.HologramType.CRITICAL_DAMAGE, (int) critDamage);
                    return critDamage;
                }
            }

            return Math.max(1, baseDamage);

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Error calculating mob damage: " + e.getMessage());
            return calculateTierBasedDamage(mobAttacker);
        }
    }

    /**
     * Calculate tier-based damage for mobs without proper weapons
     */
    private double calculateTierBasedDamage(LivingEntity mobAttacker) {
        int tier = MobUtils.getMobTier(mobAttacker);
        boolean isElite = MobUtils.isElite(mobAttacker);

        int baseDamage;
        switch (tier) {
            case 1: baseDamage = ThreadLocalRandom.current().nextInt(8, 15); break;
            case 2: baseDamage = ThreadLocalRandom.current().nextInt(15, 25); break;
            case 3: baseDamage = ThreadLocalRandom.current().nextInt(25, 40); break;
            case 4: baseDamage = ThreadLocalRandom.current().nextInt(40, 65); break;
            case 5: baseDamage = ThreadLocalRandom.current().nextInt(65, 100); break;
            case 6: baseDamage = ThreadLocalRandom.current().nextInt(100, 150); break;
            default: baseDamage = ThreadLocalRandom.current().nextInt(8, 15); break;
        }

        if (isElite) {
            baseDamage = (int) (baseDamage * 1.5);
        }

        return baseDamage;
    }

    /**
     * Calculate elemental damage from mob weapon
     */
    private int calculateMobElementalDamage(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) {
            return 0;
        }

        int elementalDamage = 0;
        List<String> lore = weapon.getItemMeta().getLore();

        for (String line : lore) {
            if (line.contains("ICE DMG")) {
                elementalDamage += getElem(weapon, "ICE DMG");
            }
            if (line.contains("POISON DMG")) {
                elementalDamage += getElem(weapon, "POISON DMG");
            }
            if (line.contains("FIRE DMG")) {
                elementalDamage += getElem(weapon, "FIRE DMG");
            }
            if (line.contains("PURE DMG")) {
                elementalDamage += getElem(weapon, "PURE DMG");
            }
        }

        return elementalDamage;
    }

    // ============= EXISTING TASK METHODS =============

    private void startMovementSpeedRestoreTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();

                    if (playerSlowEffects.containsKey(playerId)) {
                        if (currentTime - playerSlowEffects.get(playerId) > PLAYER_SLOW_DURATION) {
                            setPlayerSpeed(player, 0.2f);
                            playerSlowEffects.remove(playerId);
                        }
                    } else if (player.getWalkSpeed() != 0.2f) {
                        setPlayerSpeed(player, 0.2f);
                    }
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20, 20);
    }

    private void startEntityDamageEffectCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> iterator = entityDamageEffects.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    if (currentTime - entry.getValue() > 500) {
                        iterator.remove();
                    }
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 10, 10);
    }

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

    private void setPlayerSpeed(Player player, float speed) {
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> player.setWalkSpeed(speed));
    }

    private void markPlayerInCombat(Player victim, Player attacker) {
        UUID victimId = victim.getUniqueId();
        UUID attackerId = attacker.getUniqueId();

        combatTimestamps.put(victimId, System.currentTimeMillis());
        lastAttackers.put(victimId, attackerId);
        combatTimestamps.put(attackerId, System.currentTimeMillis());
    }

    public boolean isInCombat(Player player) {
        UUID playerId = player.getUniqueId();

        if (!combatTimestamps.containsKey(playerId)) {
            return false;
        }

        long lastCombatTime = combatTimestamps.get(playerId);
        long timeSinceCombat = System.currentTimeMillis() - lastCombatTime;

        return timeSinceCombat < COMBAT_DURATION;
    }

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

    public Player getLastAttacker(Player player) {
        UUID playerId = player.getUniqueId();

        if (!lastAttackers.containsKey(playerId)) {
            return null;
        }

        UUID attackerId = lastAttackers.get(playerId);
        return Bukkit.getPlayer(attackerId);
    }

    // ============= BLOCK AND DODGE CALCULATIONS =============

    private int calculateBlockChance(Player player) {
        int blockChance = 0;
        int strength = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                blockChance += getPercent(armor, "BLOCK");
                strength += getElem(armor, "STR");
            }
        }

        blockChance += Math.round(strength * 0.015f);
        return Math.min(blockChance, MAX_BLOCK_CHANCE);
    }

    private int calculateDodgeChance(Player player) {
        int dodgeChance = 0;
        int dexterity = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dodgeChance += getPercent(armor, "DODGE");
                dexterity += getElem(armor, "DEX");
            }
        }

        dodgeChance += Math.round(dexterity * 0.015f);
        return Math.min(dodgeChance, MAX_DODGE_CHANCE);
    }

    private int getAccuracy(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        return getPercent(weapon, "ACCURACY");
    }

    private int calculateThornsChance(Player player) {
        int thornsChance = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                thornsChance += getPercent(armor, "THORNS");
            }
        }

        return thornsChance;
    }

    private int calculateCriticalChance(Player player, ItemStack weapon) {
        int critChance = 0;

        critChance += getPercent(weapon, "CRITICAL HIT");

        if (weapon.getType().name().contains("_AXE")) {
            critChance += 10;
        }

        int intelligence = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                intelligence += getElem(armor, "INT");
            }
        }

        if (intelligence > 0) {
            critChance += Math.round(intelligence * 0.015f);
        }

        return critChance;
    }

    // ============= ARMOR CALCULATION WITH LEGACY FORMULA =============

    private double calculateDiminishingReturns(double value, double scale, double power) {
        return value / (1.0 + Math.pow(value / scale, power));
    }

    private double calculateArmorReduction(Player defender, LivingEntity attacker) {
        double armorRating = 0.0;

        for (ItemStack armor : defender.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                armorRating += getArmor(armor);
                int strength = getElem(armor, "STR");
                armorRating += strength * 0.001; // 0.1% armor per strength point
            }
        }

        // Apply diminishing returns using legacy formula (scale=400, n=1.8)
        double effectiveArmor = armorRating / (1.0 + Math.pow(armorRating / 400, 1.8));

        // Calculate armor penetration (if attacker is player)
        double armorPenetration = 0.0;
        if (attacker instanceof Player) {
            Player playerAttacker = (Player) attacker;
            ItemStack weapon = playerAttacker.getInventory().getItemInMainHand();
            armorPenetration = getElem(weapon, "ARMOR PEN") / 100.0;

            int totalDex = 0;
            for (ItemStack armor : playerAttacker.getInventory().getArmorContents()) {
                if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                    totalDex += getElem(armor, "DEX");
                }
            }
            armorPenetration += totalDex * 0.00035; // 0.035% per dexterity point
        }

        armorPenetration = Math.max(0, Math.min(1, armorPenetration));

        double remainingArmor = effectiveArmor * (1 - armorPenetration);
        return remainingArmor / 100.0; // Convert to percentage
    }

    // ============= ELEMENTAL DAMAGE CALCULATION =============

    private int calculateElementalDamage(Player attacker, LivingEntity target, ItemStack weapon) {
        int elementalDamage = 0;
        List<String> lore = weapon.getItemMeta().getLore();
        int dexterity = 0;

        for (ItemStack armor : attacker.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dexterity += getElem(armor, "DEX");
            }
        }

        int tier = getWeaponTier(weapon);

        for (String line : lore) {
            if (line.contains("ICE DMG")) {
                int iceDamage = getElem(weapon, "ICE DMG");
                int iceDamageBonus = Math.round(iceDamage * (1 + Math.round(dexterity / 3000f)));
                elementalDamage += iceDamageBonus;

                target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, ICE_EFFECT_ID);

                int duration = 40 + (tier * 5);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration, 0));

                // Show elemental damage hologram
                hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_ICE, iceDamageBonus);
            }

            if (line.contains("POISON DMG")) {
                int poisonDamage = getElem(weapon, "POISON DMG");
                int poisonDamageBonus = Math.round(poisonDamage * (1 + Math.round(dexterity / 3000f)));
                elementalDamage += poisonDamageBonus;

                target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, POISON_EFFECT_ID);

                int duration = 15 + (tier * 5);
                int amplifier = tier >= 3 ? 1 : 0;
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, amplifier));

                // Show elemental damage hologram
                hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_POISON, poisonDamageBonus);
            }

            if (line.contains("FIRE DMG")) {
                int fireDamage = getElem(weapon, "FIRE DMG");
                int fireDamageBonus = Math.round(fireDamage * (1 + Math.round(dexterity / 3000f)));
                elementalDamage += fireDamageBonus;

                target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, FIRE_EFFECT_ID);
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);

                int fireDuration = 15 + (tier * 5);
                target.setFireTicks(fireDuration);

                // Show elemental damage hologram
                hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_FIRE, fireDamageBonus);
            }

            if (line.contains("PURE DMG")) {
                int pureDamage = getElem(weapon, "PURE DMG");
                int pureDamageBonus = Math.round(pureDamage * (1 + Math.round(dexterity / 3000f)));
                elementalDamage += pureDamageBonus;

                // Show elemental damage hologram
                hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_PURE, pureDamageBonus);
            }
        }

        if (target instanceof Player && hasBonus(weapon, "VS PLAYERS")) {
            elementalDamage *= (1 + getPercent(weapon, "VS PLAYERS") / 100.0);
        } else if (!(target instanceof Player) && hasBonus(weapon, "VS MONSTERS")) {
            elementalDamage *= (1 + getPercent(weapon, "VS MONSTERS") / 100.0);
        }

        return elementalDamage;
    }

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

    // ============= UTILITY METHODS =============

    private boolean isGodModeDisabled(Player player) {
        return Toggles.isToggled(player, "God Mode Disabled");
    }

    private boolean isSafeZone(Location location) {
        return AlignmentMechanics.isSafeZone(location);
    }

    private String getEnhancedMobName(LivingEntity mobAttacker) {
        try {
            MobManager mobManager = MobManager.getInstance();
            if (mobManager != null) {
                CustomMob customMob = mobManager.getCustomMob(mobAttacker);
                if (customMob != null) {
                    String customName = customMob.getOriginalName();
                    if (customName != null && !customName.isEmpty() && !MobUtils.isHealthBar(customName)) {
                        return customName;
                    }
                }
            }

            String currentName = mobAttacker.getCustomName();
            if (currentName != null && !currentName.isEmpty() && !MobUtils.isHealthBar(currentName)) {
                return currentName;
            }

            String[] metadataKeys = {"name", "customName", "type", "originalName"};
            for (String key : metadataKeys) {
                if (mobAttacker.hasMetadata(key)) {
                    try {
                        String metaName = mobAttacker.getMetadata(key).get(0).asString();
                        if (metaName != null && !metaName.isEmpty() && !MobUtils.isHealthBar(metaName)) {
                            if (key.equals("type")) {
                                return reconstructFormattedMobName(mobAttacker, metaName);
                            }
                            return metaName.contains("§") ? metaName : ChatColor.stripColor(metaName);
                        }
                    } catch (Exception e) {
                        // Continue to next metadata key
                    }
                }
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Error getting enhanced mob name: " + e.getMessage());
        }

        return "Unknown Mob";
    }

    private String reconstructFormattedMobName(LivingEntity mobAttacker, String mobType) {
        try {
            int tier = MobUtils.getMobTier(mobAttacker);
            boolean elite = MobUtils.isElite(mobAttacker);
            String baseName = MobUtils.getDisplayName(mobType);
            return MobUtils.formatMobName(baseName, tier, elite);
        } catch (Exception e) {
            return MobUtils.getDisplayName(mobType);
        }
    }

    // ============= EVENT HANDLERS =============

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreventDeadPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.isDead() || player.getHealth() <= 0) {
                event.setCancelled(true);
                event.setDamage(0.0);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event.getEntity().hasMetadata("pet")) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (player.hasMetadata("NPC")) {
                event.setCancelled(true);
                event.setDamage(0.0);
                return;
            }

            if (player.isOp() || player.getGameMode() == GameMode.CREATIVE || player.isFlying()) {
                if (!isGodModeDisabled(player)) {
                    event.setCancelled(true);
                    event.setDamage(0.0);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (event.getDamager().getType() == EntityType.FIREWORK || event.getCause() == DamageCause.ENTITY_EXPLOSION) {
            event.setDamage(0);
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof LivingEntity && event.getDamager() instanceof Player) {
            if (event.getDamage() > 0 && !event.isCancelled()) {
                Player damager = (Player) event.getDamager();
                LivingEntity entity = (LivingEntity) event.getEntity();
                int damage = (int) event.getDamage();

                // Show basic damage hologram for non-player entities
                if (!(entity instanceof Player)) {
                    hologramHandler.showCombatHologram(damager, entity, CombatHologramHandler.HologramType.DAMAGE, damage);
                }
            }
        }
    }

    // ============= FIXED: MOB-TO-PLAYER DAMAGE HANDLING =============

    /**
     * FIXED: Handle mob-to-player damage with proper weapon-based calculations
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onMobToPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        // Only handle mob-to-player damage
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof LivingEntity mobAttacker)) {
            return;
        }

        // Skip if attacker is also a player (handled by PVP system)
        if (mobAttacker instanceof Player) {
            return;
        }

        // FIXED: Check for whirlwind explosion damage (should bypass normal calculations)
        if (victim.hasMetadata("whirlwindExplosionDamage")) {
            // This is whirlwind explosion damage - let it pass through unchanged
            double explosionDamage = victim.getMetadata("whirlwindExplosionDamage").get(0).asDouble();
            event.setDamage(explosionDamage);

            // Show explosion damage hologram
            hologramHandler.showCombatHologram(mobAttacker, victim, CombatHologramHandler.HologramType.DAMAGE, (int) explosionDamage);

            if (Toggles.isToggled(victim, "Debug")) {
                String mobName = getEnhancedMobName(mobAttacker);
                victim.sendMessage(ChatColor.RED + "            -" + (int) explosionDamage + ChatColor.RED + ChatColor.BOLD + "HP " +
                        ChatColor.RED + "-> " + ChatColor.RESET + mobName + " " + ChatColor.GRAY + "[EXPLOSION]");
            }
            return;
        }

        // Calculate proper mob damage
        double calculatedDamage = calculateMobDamage(mobAttacker, victim);

        // Apply armor reduction
        double armorReduction = calculateArmorReduction(victim, mobAttacker);
        double finalDamage = calculatedDamage * (1 - armorReduction);
        finalDamage = Math.max(1, Math.round(finalDamage));

        // Set the calculated damage
        event.setDamage(finalDamage);

        // Show damage hologram
        hologramHandler.showCombatHologram(mobAttacker, victim, CombatHologramHandler.HologramType.DAMAGE, (int) finalDamage);

        // Debug display
        if (Toggles.isToggled(victim, "Debug")) {
            String mobName = getEnhancedMobName(mobAttacker);
            int expectedHealth = Math.max(0, (int) (victim.getHealth() - finalDamage));
            double effectiveReduction = ((calculatedDamage - finalDamage) / calculatedDamage) * 100;

            victim.sendMessage(ChatColor.RED + "            -" + (int) finalDamage + ChatColor.RED + ChatColor.BOLD + "HP " +
                    ChatColor.GRAY + "[" + String.format("%.1f", effectiveReduction) + "%A -> -" +
                    (int) (calculatedDamage - finalDamage) + ChatColor.BOLD + "DMG" + ChatColor.GRAY + "] " +
                    ChatColor.GREEN + "[" + expectedHealth + ChatColor.BOLD + "HP" + ChatColor.GREEN + "] " +
                    ChatColor.RED + "-> " + ChatColor.RESET + mobName);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDefensiveAction(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }

        YakPlayer yakDefender = playerManager.getPlayer(defender);
        if (yakDefender == null) {
            return;
        }

        // Use authentic legacy calculations
        int blockChance = calculateBlockChance(defender);
        int dodgeChance = calculateDodgeChance(defender);

        if (event.getDamager() instanceof Player attacker) {
            int accuracy = getAccuracy(attacker);

            // Apply legacy diminishing returns formula (scale=300, nS=1.35)
            double scale = 300;
            double nS = 1.35;

            double effectiveBlockDiminishingFactor = 1.0 / (1.0 + Math.pow(blockChance / scale, nS));
            double effectiveBlock = blockChance * effectiveBlockDiminishingFactor;

            double effectiveDodgeDiminishingFactor = 1.0 / (1.0 + Math.pow(dodgeChance / scale, nS));
            double effectiveDodge = dodgeChance * effectiveDodgeDiminishingFactor;

            int blockReduction = (int)(effectiveBlock * (accuracy / 100.0));
            int dodgeReduction = (int)(effectiveDodge * (accuracy / 100.0));

            blockChance = (int) Math.max(0, effectiveBlock - blockReduction);
            dodgeChance = (int) Math.max(0, effectiveDodge - dodgeReduction);

            // Additional accuracy reduction for high values
            blockChance = blockChance > 40 ? blockChance - (int) (accuracy * (.05 * ((double) blockChance / 10))) : blockChance;
            dodgeChance = dodgeChance > 40 ? dodgeChance - (int) (accuracy * (.05 * ((double) dodgeChance / 10))) : dodgeChance;
        }

        Random random = new Random();

        // Handle block
        if (random.nextInt(100) < blockChance) {
            event.setCancelled(true);
            event.setDamage(0.0);

            defender.playSound(defender.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

            if (event.getDamager() instanceof Player attacker) {
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);
                hologramHandler.showCombatHologram(attacker, defender, CombatHologramHandler.HologramType.BLOCK, 0);

                if (Toggles.isToggled(attacker, "Debug")) {
                    attacker.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "*OPPONENT BLOCKED* (" + defender.getName() + ")");
                }

                if (Toggles.isToggled(defender, "Debug")) {
                    defender.sendMessage(ChatColor.DARK_GREEN + ChatColor.BOLD.toString() + "*BLOCK* (" + attacker.getName() + ")");
                }
            } else {
                // Mob attack blocked
                hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.BLOCK, 0);

                if (Toggles.isToggled(defender, "Debug")) {
                    String mobName = getEnhancedMobName((LivingEntity) event.getDamager());
                    defender.sendMessage(ChatColor.DARK_GREEN + ChatColor.BOLD.toString() + "*BLOCK* (" + mobName + ")");
                }
            }
            return;
        }

        // Handle dodge
        if (random.nextInt(100) < dodgeChance) {
            event.setCancelled(true);
            event.setDamage(0.0);

            defender.playSound(defender.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 1.0f, 1.0f);
            defender.getWorld().spawnParticle(Particle.CLOUD, defender.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);

            if (event.getDamager() instanceof Player attacker) {
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 1.0f, 1.0f);
                hologramHandler.showCombatHologram(attacker, defender, CombatHologramHandler.HologramType.DODGE, 0);

                if (Toggles.isToggled(attacker, "Debug")) {
                    attacker.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "*OPPONENT DODGED* (" + defender.getName() + ")");
                }

                if (Toggles.isToggled(defender, "Debug")) {
                    defender.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "*DODGE* (" + attacker.getName() + ")");
                }
            } else {
                // Mob attack dodged
                hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.DODGE, 0);

                if (Toggles.isToggled(defender, "Debug")) {
                    String mobName = getEnhancedMobName((LivingEntity) event.getDamager());
                    defender.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "*DODGE* (" + mobName + ")");
                }
            }
            return;
        }

        // Handle shield blocking (50% damage reduction)
        if (defender.isBlocking() && random.nextInt(100) <= 80) {
            event.setDamage(event.getDamage() / 2);

            if (event.getDamager() instanceof Player attacker) {
                hologramHandler.showCombatHologram(attacker, defender, CombatHologramHandler.HologramType.BLOCK, 0);
                defender.playSound(defender.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

                if (Toggles.isToggled(attacker, "Debug")) {
                    attacker.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "*OPPONENT BLOCKED* (" + defender.getName() + ")");
                }

                if (Toggles.isToggled(defender, "Debug")) {
                    defender.sendMessage(ChatColor.DARK_GREEN + ChatColor.BOLD.toString() + "*BLOCK* (" + attacker.getName() + ")");
                }
            } else {
                // Mob attack partially blocked
                hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.BLOCK, 0);
                defender.playSound(defender.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

                if (Toggles.isToggled(defender, "Debug")) {
                    String mobName = getEnhancedMobName((LivingEntity) event.getDamager());
                    defender.sendMessage(ChatColor.DARK_GREEN + ChatColor.BOLD.toString() + "*PARTIAL BLOCK* (" + mobName + ")");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWeaponDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        YakPlayer yakAttacker = playerManager.getPlayer(attacker);
        if (yakAttacker == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Check for staff weapon using existing system
        if (MagicStaff.isRecentStaffShot(attacker)) {
            ItemStack staffWeapon = MagicStaff.getLastUsedStaff(attacker);
            if (staffWeapon != null) {
                weapon = staffWeapon;
            }
            MagicStaff.clearStaffShot(attacker);
        }

        // Clear off-hand item (legacy behavior)
        if (attacker.getInventory().getItemInOffHand().getType() != Material.AIR) {
            ItemStack material = attacker.getInventory().getItemInOffHand();
            attacker.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            if (attacker.getInventory().firstEmpty() == -1) {
                attacker.getWorld().dropItemNaturally(attacker.getLocation(), material);
            } else {
                attacker.getInventory().addItem(material);
            }
        }

        if (weapon == null || weapon.getType() == Material.AIR || !weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) {
            event.setDamage(1.0);
            return;
        }

        // Calculate base damage using legacy method
        List<Integer> damageRange = getDamageRange(weapon);
        int minDamage = damageRange.get(0);
        int maxDamage = damageRange.get(1);

        Random random = new Random();
        int damage = random.nextInt(maxDamage - minDamage + 1) + minDamage;

        // Add elemental damage using legacy calculations
        damage += calculateElementalDamage(attacker, target, weapon);

        // Apply VS PLAYERS/VS MONSTERS bonuses
        if (target instanceof Player && hasBonus(weapon, "VS PLAYERS")) {
            damage *= (1 + getPercent(weapon, "VS PLAYERS") / 100.0);
        } else if (!(target instanceof Player) && hasBonus(weapon, "VS MONSTERS")) {
            damage *= (1 + getPercent(weapon, "VS MONSTERS") / 100.0);
        }

        // Calculate stats and apply weapon type bonuses (legacy formulas)
        double[] stats = calculateStats(attacker);
        double dps = stats[0];
        double vit = stats[1];
        double str = stats[2];

        String weaponType = weapon.getType().name();
        if (weaponType.contains("_SWORD")) {
            damage *= (1 + vit / 5000.0); // Legacy sword bonus
        } else if (weaponType.contains("_AXE")) {
            damage *= (1 + str / 4500.0); // Legacy axe bonus
        } else if (weaponType.contains("_HOE")) {
            damage *= (1 + stats[2] / 100.0); // Legacy staff bonus (using intelligence from armor)
        }

        damage *= (1 + dps / 100.0); // Apply DPS bonus

        // Calculate critical hit using legacy method
        int critChance = getCrit(attacker);
        boolean isCritical = random.nextInt(100) < critChance;

        if (isCritical) {
            damage *= 2;
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.5f, 0.5f);
            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);

            // Show critical hit hologram
            hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.CRITICAL_DAMAGE, damage);
        } else {
            // Show normal damage hologram
            hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.DAMAGE, damage);
        }

        // Apply life steal using legacy calculation
        if (hasBonus(weapon, "LIFE STEAL") && !event.getEntityType().equals(EntityType.ARMOR_STAND)) {
            target.getWorld().playEffect(target.getEyeLocation(), Effect.STEP_SOUND, Material.REDSTONE_WIRE);
            double lifeStealPercentage = getPercent(weapon, "LIFE STEAL");
            int lifeStolen = calculateLifeSteal(damage, lifeStealPercentage);

            if (attacker.getHealth() < attacker.getMaxHealth() - (double) lifeStolen) {
                attacker.setHealth(attacker.getHealth() + (double) lifeStolen);
                if (Toggles.isToggled(attacker, "Debug")) {
                    attacker.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "            +" + ChatColor.GREEN
                            + lifeStolen + ChatColor.GREEN + ChatColor.BOLD + " HP " + ChatColor.GRAY + "["
                            + (int) attacker.getHealth() + "/" + (int) attacker.getMaxHealth() + "HP]");
                }
            } else {
                attacker.setHealth(attacker.getMaxHealth());
                if (Toggles.isToggled(attacker, "Debug")) {
                    attacker.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + " " + "           +" + ChatColor.GREEN
                            + lifeStolen + ChatColor.GREEN + ChatColor.BOLD + " HP " + ChatColor.GRAY + "["
                            + (int) attacker.getMaxHealth() + "/" + (int) attacker.getMaxHealth() + "HP]");
                }
            }

            // Show life steal hologram above attacker
            hologramHandler.showCombatHologram(target, attacker, CombatHologramHandler.HologramType.LIFESTEAL, lifeStolen);
        }

        // Apply thorns effect if target has thorns (legacy calculation)
        if (target instanceof Player defender) {
            int thornsChance = calculateThornsChance(defender);

            if (thornsChance > 1 && random.nextBoolean()) {
                int thornsDamage = (int) (damage * ((thornsChance * 0.5) / 100)) + 1;

                defender.getWorld().spawnParticle(Particle.BLOCK_CRACK, defender.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.01, new MaterialData(Material.OAK_LEAVES));
                attacker.setHealth(attacker.getHealth() - thornsDamage);

                // Show thorns damage hologram above attacker
                hologramHandler.showCombatHologram(defender, attacker, CombatHologramHandler.HologramType.THORNS, thornsDamage);
            }
        }

        event.setDamage(damage);

        if (target instanceof Player) {
            markPlayerInCombat((Player) target, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorCalculation(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (!(event.getEntity() instanceof Player defender) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        double damage = event.getDamage();
        double damageReduction = calculateArmorReduction(defender, attacker);

        double reducedDamage = damage * (1 - damageReduction);
        int finalDamage = (int) Math.max(1, Math.round(reducedDamage));

        if (Toggles.isToggled(defender, "Debug")) {
            int expectedHealth = Math.max(0, (int) (defender.getHealth() - finalDamage));
            double effectiveReduction = ((damage - finalDamage) / damage) * 100;

            defender.sendMessage(ChatColor.RED + "            -" + finalDamage + ChatColor.RED + ChatColor.BOLD + "HP " + ChatColor.GRAY + "[" + String.format("%.2f", effectiveReduction) + "%A -> -" + (int) (damage - finalDamage) + ChatColor.BOLD + "DMG" + ChatColor.GRAY + "] " + ChatColor.GREEN + "[" + expectedHealth + ChatColor.BOLD + "HP" + ChatColor.GREEN + "]");
        }

        event.setDamage(finalDamage);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKnockback(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target) || !(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }

        UUID targetId = target.getUniqueId();
        if (knockbackCooldowns.containsKey(targetId) && System.currentTimeMillis() - knockbackCooldowns.get(targetId) < 200) {
            return;
        }

        target.setNoDamageTicks(0);
        knockbackCooldowns.put(targetId, System.currentTimeMillis());

        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            applyKnockback(target, attacker);
        });
    }

    private void applyKnockback(LivingEntity target, LivingEntity attacker) {
        if (target.isDead()) return;

        double knockbackForce = 0.5;
        double verticalKnockback = 0.35;

        if (target instanceof Player) {
            knockbackForce = 0.24;
            verticalKnockback = 0.0;
        } else if (attacker instanceof Player) {
            Player player = (Player) attacker;
            if (player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInMainHand().getType().name().contains("_SHOVEL")) {
                knockbackForce = 0.7;
                verticalKnockback = 0.3;
            } else {
                knockbackForce = 0.3;
                verticalKnockback = 0.1;
            }
        }

        Vector knockbackDirection = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
        Vector knockbackVelocity = knockbackDirection.multiply(knockbackForce).setY(verticalKnockback);

        target.setVelocity(target.getVelocity().add(knockbackVelocity));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPolearmAoeAttack(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity primaryTarget)) {
            return;
        }

        if (polearmSwingProcessed.contains(attacker.getUniqueId())) {
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR || !weapon.getType().name().contains("_SHOVEL")) {
            return;
        }

        polearmSwingProcessed.add(attacker.getUniqueId());

        try {
            YakPlayer yakAttacker = playerManager.getPlayer(attacker);
            if (yakAttacker != null) {
                Energy.getInstance().removeEnergy(attacker, 5);

                for (Entity nearbyEntity : primaryTarget.getNearbyEntities(1, 2, 1)) {
                    if (!(nearbyEntity instanceof LivingEntity secondaryTarget) || nearbyEntity == primaryTarget || nearbyEntity == attacker) {
                        continue;
                    }

                    secondaryTarget.setNoDamageTicks(0);
                    Energy.getInstance().removeEnergy(attacker, 2);
                    secondaryTarget.damage(1.0, attacker);

                    // Show AOE damage hologram
                    hologramHandler.showCombatHologram(attacker, secondaryTarget, CombatHologramHandler.HologramType.DAMAGE, 1);
                }
            }
        } finally {
            polearmSwingProcessed.remove(attacker.getUniqueId());
        }
    }

    @EventHandler
    public void onDamageSound(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity target) {
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

            if (target instanceof Player) {
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.6f);
            }
        }

        if (event.getEntity() instanceof Player victim && !(event.getDamager() instanceof Player) && event.getDamager() instanceof LivingEntity) {
            victim.setWalkSpeed(0.165f);
            playerSlowEffects.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDebugDisplay(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        int damage = (int) event.getDamage();
        int remainingHealth = Math.max(0, (int) (target.getHealth() - damage));

        String targetName = target instanceof Player ? target.getName() : getEnhancedMobName(target);

        if (Toggles.isToggled(attacker, "Debug")) {
            String message = String.format("%s%d%s DMG %s-> %s%s [%dHP]", ChatColor.RED, damage, ChatColor.RED.toString() + ChatColor.BOLD, ChatColor.RED, ChatColor.RESET, targetName, remainingHealth);
            attacker.sendMessage(message);
        }

        if (target instanceof Player) {
            markPlayerInCombat((Player) target, attacker);
        }
    }

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

        // Use legacy damage calculation for dummy
        List<Integer> damageRange = getDamageRange(weapon);
        int minDamage = damageRange.get(0);
        int maxDamage = damageRange.get(1);

        int damage = ThreadLocalRandom.current().nextInt(minDamage, maxDamage + 1);

        // Add elemental damage
        List<String> lore = weapon.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("ICE DMG")) {
                damage += getElem(weapon, "ICE DMG");
            }
            if (line.contains("POISON DMG")) {
                damage += getElem(weapon, "POISON DMG");
            }
            if (line.contains("FIRE DMG")) {
                damage += getElem(weapon, "FIRE DMG");
            }
            if (line.contains("PURE DMG")) {
                damage += getElem(weapon, "PURE DMG");
            }
        }

        // Calculate stat bonuses using legacy method
        double[] stats = calculateStats(player);
        double dps = stats[0];
        double vit = stats[1];
        double str = stats[2];

        // Apply weapon-specific bonuses (legacy formulas)
        String weaponType = weapon.getType().name();
        if (weaponType.contains("_SWORD") && vit > 0) {
            damage = (int) calculateWeaponTypeBonus(damage, vit, 5000.0);
        }
        if (weaponType.contains("_AXE") && str > 0) {
            damage = (int) calculateWeaponTypeBonus(damage, str, 4500.0);
        }
        if (weaponType.contains("_HOE")) {
            // For staves, use intelligence from armor
            double intel = 0;
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                    intel += getElem(armor, "INT");
                }
            }
            if (intel > 0) {
                double divide = intel / 100.0;
                double pre = (double) damage * divide;
                damage = (int) ((double) damage + pre);
            }
        }

        // Apply DPS bonus
        if (dps > 0) {
            double divide = dps / 100.0;
            double pre = (double) damage * divide;
            damage = (int) ((double) damage + pre);
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "            " + damage + ChatColor.RED + ChatColor.BOLD + " DMG " + ChatColor.RED + "-> " + ChatColor.RESET + "DPS DUMMY" + " [" + 99999999 + "HP]");

        // Show dummy damage hologram with animation at block location
        Location dummyLocation = block.getLocation().add(0.5, 1.5, 0.5);
        hologramHandler.showCustomHologram(player, null, ChatColor.RED + "" + ChatColor.BOLD + "-" + damage, CombatHologramHandler.HologramType.DAMAGE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            if (event.getDamage() >= entity.getHealth()) {
                knockbackCooldowns.remove(entity.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBypassArmor(EntityDamageEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }

        if (entity.isDead() || entity.getHealth() <= 0) {
            return;
        }

        double damage = event.getDamage();

        event.setDamage(0.0);
        event.setCancelled(true);

        entity.playEffect(EntityEffect.HURT);
        entity.setLastDamageCause(event);
        entity.setMetadata("lastDamaged", new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis()));

        double newHealth = entity.getHealth() - damage;
        if (newHealth <= 0.0) {
            entity.setHealth(0.0);
        } else {
            entity.setHealth(newHealth);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        combatTimestamps.remove(playerId);
        lastAttackers.remove(playerId);
        playerSlowEffects.remove(playerId);
        knockbackCooldowns.remove(playerId);
        polearmSwingProcessed.remove(playerId);

        entityDamageEffects.remove(playerId);
    }
}