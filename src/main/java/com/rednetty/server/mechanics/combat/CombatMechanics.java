package com.rednetty.server.mechanics.combat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.holograms.CombatHologramHandler;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import com.rednetty.server.mechanics.player.stamina.Energy;
import com.rednetty.server.mechanics.world.mobs.CritManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import com.rednetty.server.utils.sounds.SoundUtil;
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
 * Comprehensive Combat Mechanics System
 *
 * Features:
 * - Advanced PVP protection with buddy/party/guild systems
 * - Balanced damage calculations with stat scaling
 * - Armor reduction with diminishing returns
 * - Block, dodge, and defensive mechanics
 * - Critical hits and elemental damage
 * - Animated hologram damage display system
 * - Mob-to-player damage calculations
 * - Combat tracking and status management
 *
 * @author YakRealms Development Team
 * @version 2.0 - Refactored and Optimized
 */
public class CombatMechanics implements Listener {

    // ================================ CONSTANTS ================================

    // Combat timing constants
    private static final int COMBAT_DURATION = 5000; // Milliseconds
    private static final int PLAYER_SLOW_DURATION = 3000; // Milliseconds
    private static final int KNOCKBACK_COOLDOWN = 200; // Milliseconds

    // Balance constants
    private static final int MAX_BLOCK_CHANCE = 60;
    private static final int MAX_DODGE_CHANCE = 60;
    private static final double MAX_ARMOR_REDUCTION = 0.75; // 75% max armor reduction

    // Knockback constants
    private static final double KNOCKBACK_BASE = 2.5;
    private static final double KNOCKBACK_VERTICAL_BASE = 0.45;
    private static final double KNOCKBACK_RANDOMNESS = 0.01;
    private static final double WEAPON_KNOCK_MODIFIER = 1.1;

    // Elemental effect IDs
    private static final int ICE_EFFECT_ID = 13565951;
    private static final int POISON_EFFECT_ID = 8196;
    private static final int FIRE_EFFECT_ID = 8259;

    // Stat scaling constants (matched to Journal.java)
    private static final double INTELLIGENCE_CRIT_BONUS = 0.015; // +1.5% per 100 INT
    private static final double STRENGTH_BLOCK_BONUS = 0.015; // +1.5% per 100 STR
    private static final double DEXTERITY_DODGE_BONUS = 0.015; // +1.5% per 100 DEX
    private static final double DEXTERITY_DPS_BONUS = 0.012; // +1.2% per 100 DEX
    private static final double ELEMENTAL_INT_SCALING = 3000.0; // +3.33% per 100 INT
    private static final double WEAPON_STAT_DIVISOR = 5000.0; // +2% per 100 stat
    private static final double DPS_DIVISOR = 200.0; // DPS scaling
    private static final double ARMOR_PEN_DEX_BONUS = 0.0035; // +0.35% per 100 DEX

    // ================================ FIELDS ================================

    // Tracking maps for combat state
    private final Map<UUID, Long> combatTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerSlowEffects = new ConcurrentHashMap<>();
    private final Map<UUID, Long> knockbackCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> entityDamageEffects = new ConcurrentHashMap<>();
    private final Set<UUID> polearmSwingProcessed = new HashSet<>();

    // Dependencies
    private final YakPlayerManager playerManager;
    private final CombatHologramHandler hologramHandler;

    // ================================ CONSTRUCTOR & LIFECYCLE ================================

    public CombatMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.hologramHandler = CombatHologramHandler.getInstance();
    }

    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
        hologramHandler.onEnable();
        startAsyncTasks();
        YakRealms.log("Combat Mechanics enabled with balanced calculations and enhanced PVP protection");
    }

    public void onDisable() {
        hologramHandler.onDisable();
        cleanupTrackingMaps();
        YakRealms.log("Combat Mechanics disabled");
    }

    // ================================ PUBLIC API METHODS ================================
    // These methods maintain backwards compatibility

    /**
     * Check if a player is currently in combat
     * @param player The player to check
     * @return true if player is in combat, false otherwise
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
     * Get remaining combat time for a player
     * @param player The player
     * @return Remaining combat time in seconds
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
     * Get the last player that attacked this player
     * @param player The victim player
     * @return The last attacker, or null if none
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
     * Check if PVP is allowed between two players
     * @param attacker The attacking player
     * @param victim The victim player
     * @return true if PVP is allowed, false otherwise
     */
    public boolean isPVPAllowed(Player attacker, Player victim) {
        return checkPVPProtection(attacker, victim).isAllowed();
    }

    /**
     * Get detailed PVP check result
     * @param attacker The attacking player
     * @param victim The victim player
     * @return Detailed PVP result with reason
     */
    public PVPResult getPVPCheckResult(Player attacker, Player victim) {
        return checkPVPProtection(attacker, victim);
    }

    // ================================ LEGACY STATIC METHODS ================================
    // These maintain compatibility with existing code

    /**
     * Get critical hit chance for a player (legacy compatibility method)
     * @param player The player
     * @return Critical hit chance percentage
     */
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
                        intel += getElem(armor, "INT");
                    }
                }

                // Intelligence critical hit bonus (+1.5% per 100 INT)
                if (intel > 0) {
                    crit += Math.round(intel * INTELLIGENCE_CRIT_BONUS);
                }
            }
        }
        return crit;
    }

    // Legacy item stat extraction methods
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

    // ================================ PVP PROTECTION SYSTEM ================================

    /**
     * Comprehensive PVP protection check that handles all protection types
     */
    public PVPResult checkPVPProtection(Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return new PVPResult(false, "Invalid players");
        }

        if (attacker.equals(victim)) {
            return new PVPResult(false, "Cannot attack yourself");
        }

        YakPlayer attackerData = playerManager.getPlayer(attacker);
        YakPlayer victimData = playerManager.getPlayer(victim);

        if (attackerData == null || victimData == null) {
            return new PVPResult(false, "Player data not loaded");
        }

        // Check attacker PVP setting
        if (attackerData.isToggled("Anti PVP")) {
            return new PVPResult(false, "You have PVP disabled! Use /toggle to enable it.",
                    PVPResult.ResultType.ATTACKER_PVP_DISABLED);
        }

        // Check buddy protection
        PVPResult buddyResult = checkBuddyProtection(attacker, victim, attackerData, victimData);
        if (!buddyResult.isAllowed()) {
            return buddyResult;
        }

        // Check party protection
        PVPResult partyResult = checkPartyProtection(attacker, victim, attackerData, victimData);
        if (!partyResult.isAllowed()) {
            return partyResult;
        }

        // Check guild protection
        PVPResult guildResult = checkGuildProtection(attacker, victim, attackerData, victimData);
        if (!guildResult.isAllowed()) {
            return guildResult;
        }

        // Check chaotic protection
        if (attackerData.isToggled("Chaotic Protection") && isLawfulPlayer(victim)) {
            return new PVPResult(false, "Chaotic Protection prevented you from attacking " + victim.getName() + "! This player is lawful.",
                    PVPResult.ResultType.CHAOTIC_PROTECTION);
        }

        // Check safe zone protection
        if (isSafeZone(attacker.getLocation()) || isSafeZone(victim.getLocation())) {
            return new PVPResult(false, "PVP is not allowed in safe zones!",
                    PVPResult.ResultType.SAFE_ZONE);
        }

        return new PVPResult(true, "PVP allowed");
    }

    private PVPResult checkBuddyProtection(Player attacker, Player victim, YakPlayer attackerData, YakPlayer victimData) {
        if (attackerData.isBuddy(victim.getName())) {
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, "You cannot attack your buddy " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.",
                        PVPResult.ResultType.BUDDY_PROTECTION);
            }
        }

        if (victimData.isBuddy(attacker.getName())) {
            if (!victimData.isToggled("Friendly Fire")) {
                return new PVPResult(false, victim.getName() + " has you as a buddy and friendly fire disabled!",
                        PVPResult.ResultType.MUTUAL_BUDDY_PROTECTION);
            }
        }

        return new PVPResult(true, "No buddy protection");
    }

    private PVPResult checkPartyProtection(Player attacker, Player victim, YakPlayer attackerData, YakPlayer victimData) {
        if (PartyMechanics.getInstance().arePartyMembers(attacker, victim)) {
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, "You cannot attack your party member " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.",
                        PVPResult.ResultType.PARTY_PROTECTION);
            }

            if (!victimData.isToggled("Friendly Fire")) {
                return new PVPResult(false, victim.getName() + " is your party member and has friendly fire disabled!",
                        PVPResult.ResultType.MUTUAL_PARTY_PROTECTION);
            }
        }

        return new PVPResult(true, "No party protection");
    }

    private PVPResult checkGuildProtection(Player attacker, Player victim, YakPlayer attackerData, YakPlayer victimData) {
        if (isInSameGuild(attacker, victim)) {
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, "You cannot attack your guild member " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.",
                        PVPResult.ResultType.GUILD_PROTECTION);
            }
        }

        return new PVPResult(true, "No guild protection");
    }

    /**
     * PVP Result class containing protection check information
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

    // ================================ STAT CALCULATION METHODS ================================

    /**
     * Calculate all primary and secondary stats for a player
     * Formulas aligned with Journal.java for consistency
     */
    private PlayerStats calculatePlayerStats(Player player) {
        double dps = 0.0, vit = 0.0, str = 0.0, intel = 0.0, dex = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dps += getDps(armor);
                vit += getElem(armor, "VIT");
                str += getElem(armor, "STR");
                intel += getElem(armor, "INT");
                dex += getElem(armor, "DEX");
            }
        }

        // Dexterity DPS bonus (+1.2% DPS per 100 DEX)
        dps += dex * DEXTERITY_DPS_BONUS;

        return new PlayerStats(dps, vit, str, intel, dex);
    }

    /**
     * Legacy compatibility method returning array format
     */
    private double[] calculateAllStats(Player player) {
        PlayerStats stats = calculatePlayerStats(player);
        return new double[]{stats.dps, stats.vit, stats.str, stats.intel, stats.dex};
    }

    // ================================ DAMAGE CALCULATION METHODS ================================

    /**
     * Calculate weapon damage with stat bonuses and elemental effects
     */
    private DamageResult calculateWeaponDamage(Player attacker, LivingEntity target, ItemStack weapon) {
        if (weapon == null || weapon.getType() == Material.AIR || !weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) {
            return new DamageResult(1.0, false, 0);
        }

        // Base damage calculation
        List<Integer> damageRange = getDamageRange(weapon);
        int minDamage = damageRange.get(0);
        int maxDamage = damageRange.get(1);
        double baseDamage = ThreadLocalRandom.current().nextInt(minDamage, maxDamage + 1);

        // Add elemental damage
        int elementalDamage = calculateElementalDamage(attacker, target, weapon);
        baseDamage += elementalDamage;

        // Apply VS bonuses
        baseDamage = applyVersusBonus(baseDamage, weapon, target);

        // Apply stat bonuses
        baseDamage = applyStatBonuses(baseDamage, attacker, weapon);

        // Apply DPS bonus
        PlayerStats stats = calculatePlayerStats(attacker);
        baseDamage *= (1 + stats.dps / DPS_DIVISOR);

        // Check for critical hit
        boolean isCritical = checkCriticalHit(attacker, weapon);
        if (isCritical) {
            baseDamage *= 2;
        }

        return new DamageResult(baseDamage, isCritical, elementalDamage);
    }

    private double applyStatBonuses(double damage, Player attacker, ItemStack weapon) {
        PlayerStats stats = calculatePlayerStats(attacker);
        String weaponType = weapon.getType().name();

        if (weaponType.contains("_SWORD")) {
            // Sword vitality bonus (+2% DMG per 100 VIT)
            damage *= (1 + stats.vit / WEAPON_STAT_DIVISOR);
        } else if (weaponType.contains("_AXE")) {
            // Axe strength bonus (+2% DMG per 100 STR)
            damage *= (1 + stats.str / WEAPON_STAT_DIVISOR);
        } else if (weaponType.contains("_HOE")) {
            // Staff intelligence bonus (+2% DMG per 100 INT)
            damage *= (1 + stats.intel / WEAPON_STAT_DIVISOR);
        }

        return damage;
    }

    private double applyVersusBonus(double damage, ItemStack weapon, LivingEntity target) {
        if (target instanceof Player && hasBonus(weapon, "VS PLAYERS")) {
            damage *= (1 + getPercent(weapon, "VS PLAYERS") / 100.0);
        } else if (!(target instanceof Player) && hasBonus(weapon, "VS MONSTERS")) {
            damage *= (1 + getPercent(weapon, "VS MONSTERS") / 100.0);
        }
        return damage;
    }

    // ================================ DEFENSIVE MECHANICS ================================

    /**
     * Calculate block chance with strength bonus and diminishing returns
     */
    private int calculateBlockChance(Player player) {
        int blockChance = 0;
        int strength = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                blockChance += getPercent(armor, "BLOCK");
                strength += getElem(armor, "STR");
            }
        }

        // Strength block bonus (+1.5% per 100 STR)
        blockChance += Math.round(strength * STRENGTH_BLOCK_BONUS);
        return Math.min(blockChance, MAX_BLOCK_CHANCE);
    }

    /**
     * Calculate dodge chance with dexterity bonus and diminishing returns
     */
    private int calculateDodgeChance(Player player) {
        int dodgeChance = 0;
        int dexterity = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dodgeChance += getPercent(armor, "DODGE");
                dexterity += getElem(armor, "DEX");
            }
        }

        // Dexterity dodge bonus (+1.5% per 100 DEX)
        dodgeChance += Math.round(dexterity * DEXTERITY_DODGE_BONUS);
        return Math.min(dodgeChance, MAX_DODGE_CHANCE);
    }

    /**
     * Calculate armor reduction with balanced diminishing returns
     */
    private double calculateArmorReduction(Player defender, LivingEntity attacker) {
        double armorRating = 0.0;
        int strength = 0;

        // Calculate total armor from equipment
        for (ItemStack armor : defender.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                armorRating += getArmor(armor);
                strength += getElem(armor, "STR");
            }
        }

        // Add strength bonus (+0.1 flat armor per STR)
        armorRating += strength * 0.1;

        // Apply balanced diminishing returns
        double effectiveArmorPercentage = (armorRating / (armorRating + 200.0)) * 100.0;

        // Calculate armor penetration
        double armorPenetration = calculateArmorPenetration(attacker);

        // Apply armor penetration
        double remainingArmorPercentage = effectiveArmorPercentage * (1 - armorPenetration);

        // Cap maximum armor reduction at 75%
        remainingArmorPercentage = Math.min(remainingArmorPercentage, MAX_ARMOR_REDUCTION * 100);

        // Debug output
        if (attacker instanceof Player && Toggles.isToggled(defender, "Debug")) {
            defender.sendMessage(ChatColor.GRAY + "DEBUG ARMOR: Raw=" + (int) armorRating +
                    " | Effective=" + String.format("%.1f", effectiveArmorPercentage) + "%" +
                    " | Pen=" + String.format("%.1f", armorPenetration * 100) + "%" +
                    " | Final=" + String.format("%.1f", remainingArmorPercentage) + "%");
        }

        return remainingArmorPercentage / 100.0;
    }

    private double calculateArmorPenetration(LivingEntity attacker) {
        double armorPenetration = 0.0;

        if (attacker instanceof Player) {
            Player playerAttacker = (Player) attacker;
            ItemStack weapon = playerAttacker.getInventory().getItemInMainHand();

            // Get armor pen from weapon
            if (weapon != null && weapon.hasItemMeta() && weapon.getItemMeta().hasLore()) {
                armorPenetration = getElem(weapon, "ARMOR PEN") / 100.0;
            }

            // Add dexterity armor penetration bonus
            PlayerStats stats = calculatePlayerStats(playerAttacker);
            armorPenetration += stats.dex * ARMOR_PEN_DEX_BONUS;
        }

        // Cap armor penetration between 0% and 90%
        return Math.max(0, Math.min(0.9, armorPenetration));
    }

    // ================================ MOB DAMAGE CALCULATION ================================

    /**
     * Calculate mob damage with weapon stats and critical hit tracking
     */
    private MobDamageResult calculateMobDamageWithCritInfo(LivingEntity mobAttacker, Player victim) {
        try {
            ItemStack weapon = null;
            if (mobAttacker.getEquipment() != null) {
                weapon = mobAttacker.getEquipment().getItemInMainHand();
            }

            // If no weapon, use tier-based fallback
            if (weapon == null || weapon.getType() == Material.AIR) {
                double fallbackDamage = calculateTierBasedDamage(mobAttacker);
                return new MobDamageResult(fallbackDamage, false, fallbackDamage, 1.0);
            }

            // Calculate damage from weapon stats
            List<Integer> damageRange = getDamageRange(weapon);
            int baseDamage = ThreadLocalRandom.current().nextInt(damageRange.get(0), damageRange.get(1) + 1);

            // Add elemental damage
            baseDamage += calculateMobElementalDamage(weapon);

            // Apply tier-based multipliers
            baseDamage = applyMobTierMultipliers(baseDamage, mobAttacker);

            // Apply VS PLAYERS bonus
            if (hasBonus(weapon, "VS PLAYERS")) {
                double bonus = getPercent(weapon, "VS PLAYERS") / 100.0;
                baseDamage = (int) (baseDamage * (1 + bonus));
            }

            double originalDamage = baseDamage;

            // Check for critical hit via CritManager
            CustomMob customMob = MobManager.getInstance().getCustomMob(mobAttacker);
            if (customMob != null) {
                CritManager.CritResult critResult = CritManager.getInstance().handleCritAttack(customMob, victim, baseDamage);

                if (critResult.isCritical()) {
                    hologramHandler.showCombatHologram(mobAttacker, victim, CombatHologramHandler.HologramType.CRITICAL_DAMAGE, (int) critResult.getDamage());
                    return new MobDamageResult(critResult.getDamage(), true, originalDamage, critResult.getMultiplier());
                }
            }

            return new MobDamageResult(Math.max(1, baseDamage), false, originalDamage, 1.0);

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Error calculating mob damage: " + e.getMessage());
            double fallbackDamage = calculateTierBasedDamage(mobAttacker);
            return new MobDamageResult(fallbackDamage, false, fallbackDamage, 1.0);
        }
    }

    private int applyMobTierMultipliers(int baseDamage, LivingEntity mobAttacker) {
        int tier = MobUtils.getMobTier(mobAttacker);
        boolean isElite = MobUtils.isElite(mobAttacker);

        double tierMultiplier = switch (tier) {
            case 1 -> 0.8;
            case 2 -> 1.0;
            case 3 -> 1.2;
            case 4 -> 1.5;
            case 5 -> 2.0;
            case 6 -> 2.5;
            default -> 1.0;
        };

        if (isElite) {
            tierMultiplier *= 1.5;
        }

        return (int) (baseDamage * tierMultiplier);
    }

    private double calculateTierBasedDamage(LivingEntity mobAttacker) {
        int tier = MobUtils.getMobTier(mobAttacker);
        boolean isElite = MobUtils.isElite(mobAttacker);

        int baseDamage = switch (tier) {
            case 1 -> ThreadLocalRandom.current().nextInt(8, 15);
            case 2 -> ThreadLocalRandom.current().nextInt(15, 25);
            case 3 -> ThreadLocalRandom.current().nextInt(25, 40);
            case 4 -> ThreadLocalRandom.current().nextInt(40, 65);
            case 5 -> ThreadLocalRandom.current().nextInt(65, 100);
            case 6 -> ThreadLocalRandom.current().nextInt(100, 150);
            default -> ThreadLocalRandom.current().nextInt(8, 15);
        };

        if (isElite) {
            baseDamage = (int) (baseDamage * 1.5);
        }

        return baseDamage;
    }

    /**
     * Legacy compatibility method
     */
    private double calculateMobDamage(LivingEntity mobAttacker, Player victim) {
        return calculateMobDamageWithCritInfo(mobAttacker, victim).getDamage();
    }

    // ================================ ELEMENTAL DAMAGE SYSTEM ================================

    /**
     * Calculate elemental damage with intelligence scaling
     */
    private int calculateElementalDamage(Player attacker, LivingEntity target, ItemStack weapon) {
        if (!weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) {
            return 0;
        }

        int elementalDamage = 0;
        List<String> lore = weapon.getItemMeta().getLore();

        PlayerStats stats = calculatePlayerStats(attacker);
        double elementalBonus = 1.0 + (stats.intel / ELEMENTAL_INT_SCALING);
        int tier = getWeaponTier(weapon);

        for (String line : lore) {
            if (line.contains("ICE DMG")) {
                elementalDamage += applyIceDamage(attacker, target, weapon, elementalBonus, tier);
            }
            if (line.contains("POISON DMG")) {
                elementalDamage += applyPoisonDamage(attacker, target, weapon, elementalBonus, tier);
            }
            if (line.contains("FIRE DMG")) {
                elementalDamage += applyFireDamage(attacker, target, weapon, elementalBonus, tier);
            }
            if (line.contains("PURE DMG")) {
                elementalDamage += applyPureDamage(attacker, target, weapon, elementalBonus);
            }
        }

        return (int) applyVersusBonus(elementalDamage, weapon, target);
    }

    private int applyIceDamage(Player attacker, LivingEntity target, ItemStack weapon, double elementalBonus, int tier) {
        int iceDamage = getElem(weapon, "ICE DMG");
        int iceDamageBonus = (int) Math.round(iceDamage * elementalBonus);

        target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, ICE_EFFECT_ID);
        int duration = 40 + (tier * 5);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0));

        hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_ICE, iceDamageBonus);
        return iceDamageBonus;
    }

    private int applyPoisonDamage(Player attacker, LivingEntity target, ItemStack weapon, double elementalBonus, int tier) {
        int poisonDamage = getElem(weapon, "POISON DMG");
        int poisonDamageBonus = (int) Math.round(poisonDamage * elementalBonus);

        target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, POISON_EFFECT_ID);
        int duration = 15 + (tier * 5);
        int amplifier = tier >= 3 ? 1 : 0;
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, amplifier));

        hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_POISON, poisonDamageBonus);
        return poisonDamageBonus;
    }

    private int applyFireDamage(Player attacker, LivingEntity target, ItemStack weapon, double elementalBonus, int tier) {
        int fireDamage = getElem(weapon, "FIRE DMG");
        int fireDamageBonus = (int) Math.round(fireDamage * elementalBonus);

        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
        int fireDuration = 15 + (tier * 5);
        target.setFireTicks(fireDuration);

        hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_FIRE, fireDamageBonus);
        return fireDamageBonus;
    }

    private int applyPureDamage(Player attacker, LivingEntity target, ItemStack weapon, double elementalBonus) {
        int pureDamage = getElem(weapon, "PURE DMG");
        int pureDamageBonus = (int) Math.round(pureDamage * elementalBonus);

        hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_PURE, pureDamageBonus);
        return pureDamageBonus;
    }

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

    // ================================ COMBAT EFFECTS ================================

    /**
     * Apply life steal effect with balanced calculations
     */
    private void applyLifeSteal(Player attacker, LivingEntity target, ItemStack weapon, double damageDealt) {
        if (!hasBonus(weapon, "LIFE STEAL") || target.getType().equals(EntityType.ARMOR_STAND)) {
            return;
        }

        target.getWorld().playEffect(target.getEyeLocation(), Effect.STEP_SOUND, Material.REDSTONE_WIRE);
        double lifeStealPercentage = getPercent(weapon, "LIFE STEAL");
        int lifeStolen = calculateLifeSteal(damageDealt, lifeStealPercentage);

        if (attacker.getHealth() < attacker.getMaxHealth() - lifeStolen) {
            attacker.setHealth(attacker.getHealth() + lifeStolen);
        } else {
            attacker.setHealth(attacker.getMaxHealth());
        }

        if (Toggles.isToggled(attacker, "Debug")) {
            attacker.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "            +" + ChatColor.GREEN
                    + lifeStolen + ChatColor.GREEN + ChatColor.BOLD + " HP " + ChatColor.GRAY + "["
                    + (int) attacker.getHealth() + "/" + (int) attacker.getMaxHealth() + "HP]");
        }

        hologramHandler.showCombatHologram(target, attacker, CombatHologramHandler.HologramType.LIFESTEAL, lifeStolen);
    }

    /**
     * Apply thorns effect with balanced damage calculation
     */
    private void applyThornsEffect(Player attacker, Player defender, double finalDamage) {
        int thornsChance = calculateThornsChance(defender);

        if (thornsChance > 1 && ThreadLocalRandom.current().nextBoolean()) {
            int thornsDamage = (int) (finalDamage * ((thornsChance * 0.5) / 100)) + 1;

            defender.getWorld().spawnParticle(Particle.BLOCK, defender.getLocation().add(0, 1, 0),
                    10, 0.5, 0.5, 0.5, 0.01, new MaterialData(Material.OAK_LEAVES));
            attacker.setHealth(Math.max(0, attacker.getHealth() - thornsDamage));

            hologramHandler.showCombatHologram(defender, attacker, CombatHologramHandler.HologramType.THORNS, thornsDamage);
        }
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

    private int calculateLifeSteal(double damageDealt, double lifeStealPercentage) {
        return Math.max(1, (int) (damageDealt * (lifeStealPercentage / 125.0)));
    }

    private boolean checkCriticalHit(Player attacker, ItemStack weapon) {
        int critChance = calculateCriticalChance(attacker, weapon);
        return ThreadLocalRandom.current().nextInt(100) < critChance;
    }

    private int calculateCriticalChance(Player player, ItemStack weapon) {
        int critChance = getPercent(weapon, "CRITICAL HIT");

        if (weapon.getType().name().contains("_AXE")) {
            critChance += 10;
        }

        PlayerStats stats = calculatePlayerStats(player);
        critChance += Math.round(stats.intel * INTELLIGENCE_CRIT_BONUS);

        return critChance;
    }

    // ================================ ASYNC TASKS & CLEANUP ================================

    private void startAsyncTasks() {
        startMovementSpeedRestoreTask();
        startEntityDamageEffectCleanupTask();
    }

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
                entityDamageEffects.entrySet().removeIf(entry -> currentTime - entry.getValue() > 500);
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 10, 10);
    }

    private void cleanupTrackingMaps() {
        combatTimestamps.clear();
        lastAttackers.clear();
        playerSlowEffects.clear();
        knockbackCooldowns.clear();
        entityDamageEffects.clear();
        polearmSwingProcessed.clear();
    }

    // ================================ UTILITY METHODS ================================

    private void markPlayerInCombat(Player victim, Player attacker) {
        UUID victimId = victim.getUniqueId();
        UUID attackerId = attacker.getUniqueId();
        long currentTime = System.currentTimeMillis();

        combatTimestamps.put(victimId, currentTime);
        lastAttackers.put(victimId, attackerId);
        combatTimestamps.put(attackerId, currentTime);
    }

    private void setPlayerSpeed(Player player, float speed) {
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> player.setWalkSpeed(speed));
    }

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

    private boolean isLawfulPlayer(Player player) {
        if (player == null) return false;
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && "LAWFUL".equals(yakPlayer.getAlignment());
    }

    private boolean isSafeZone(Location location) {
        return AlignmentMechanics.isSafeZone(location);
    }

    private boolean isGodModeDisabled(Player player) {
        return true;
    }

    private int getWeaponTier(ItemStack weapon) {
        if (!weapon.hasItemMeta() || !weapon.getItemMeta().hasDisplayName()) {
            return 1;
        }

        String name = weapon.getItemMeta().getDisplayName();

        if (name.contains(ChatColor.WHITE.toString())) return 1;
        if (name.contains(ChatColor.GREEN.toString())) return 2;
        if (name.contains(ChatColor.BLUE.toString())) return 3;
        if (name.contains(ChatColor.GOLD.toString())) return 4;

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

    private int getAccuracy(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        return getPercent(weapon, "ACCURACY");
    }

    private String getMobName(LivingEntity mobAttacker) {
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
            YakRealms.getInstance().getLogger().warning("Error getting mob name: " + e.getMessage());
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

    private void applyKnockback(LivingEntity target, LivingEntity attacker) {
        if (target.isDead()) return;

        double knockbackForce = 0.5;
        double verticalKnockback = 0.35;

        if (target instanceof Player) {
            knockbackForce = 0.24;
            verticalKnockback = 0.0;
        } else if (attacker instanceof Player) {
            Player player = (Player) attacker;
            if (player.getInventory().getItemInMainHand() != null &&
                    player.getInventory().getItemInMainHand().getType().name().contains("_SHOVEL")) {
                knockbackForce = 0.7;
                verticalKnockback = 0.3;
            } else {
                knockbackForce = 0.3;
                verticalKnockback = 0.1;
            }
        }

        Vector knockbackDirection = target.getLocation().toVector()
                .subtract(attacker.getLocation().toVector()).normalize();
        Vector knockbackVelocity = knockbackDirection.multiply(knockbackForce).setY(verticalKnockback);

        target.setVelocity(target.getVelocity().add(knockbackVelocity));
    }

    // ================================ EVENT HANDLERS ================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreventDeadPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.isDead() || player.getHealth() <= 0) {
                event.setCancelled(true);
                event.setDamage(0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;

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
    public void onPvpProtection(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (event.getDamage() <= 0.0 || event.isCancelled()) {
            return;
        }

        PVPResult result = checkPVPProtection(attacker, victim);

        if (!result.isAllowed()) {
            event.setCancelled(true);
            event.setDamage(0.0);

            hologramHandler.showCombatHologram(attacker, victim, CombatHologramHandler.HologramType.IMMUNE, 0);
            attacker.sendMessage(ChatColor.RED + "§l⚠ §c" + result.getMessage());

            Sound sound = getProtectionSound(result.getResultType());
            attacker.playSound(attacker.getLocation(), sound, 1.0f, 0.5f);

            sendAdditionalHelpMessage(attacker, result.getResultType());
            logBlockedPVPAttempt(attacker, victim, result.getResultType());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMobToPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof LivingEntity mobAttacker)) {
            return;
        }

        if (mobAttacker instanceof Player) return;

        // Check for whirlwind explosion damage
        if (victim.hasMetadata("whirlwindExplosionDamage")) {
            double explosionDamage = victim.getMetadata("whirlwindExplosionDamage").get(0).asDouble();
            event.setDamage(explosionDamage);
            hologramHandler.showCombatHologram(mobAttacker, victim, CombatHologramHandler.HologramType.DAMAGE, (int) explosionDamage);

            if (Toggles.isToggled(victim, "Debug")) {
                String mobName = getMobName(mobAttacker);
                victim.sendMessage(ChatColor.RED + "            -" + (int) explosionDamage + ChatColor.RED + ChatColor.BOLD + "HP " +
                        ChatColor.RED + "-> " + ChatColor.RESET + mobName + " " + ChatColor.GRAY + "[EXPLOSION]");
            }
            return;
        }

        // Calculate mob damage with crit tracking
        MobDamageResult damageResult = calculateMobDamageWithCritInfo(mobAttacker, victim);
        double calculatedDamage = damageResult.getDamage();

        // Apply armor reduction
        double armorReduction = calculateArmorReduction(victim, mobAttacker);
        double finalDamage = calculatedDamage * (1 - armorReduction);
        finalDamage = Math.max(1, Math.round(finalDamage));

        event.setDamage(finalDamage);

        if (!damageResult.isCritical()) {
            hologramHandler.showCombatHologram(mobAttacker, victim, CombatHologramHandler.HologramType.DAMAGE, (int) finalDamage);
        }

        // Enhanced debug display
        if (Toggles.isToggled(victim, "Debug")) {
            String mobName = getMobName(mobAttacker);
            int expectedHealth = Math.max(0, (int) (victim.getHealth() - finalDamage));
            double effectiveReduction = ((calculatedDamage - finalDamage) / calculatedDamage) * 100;

            StringBuilder debugMessage = new StringBuilder();
            debugMessage.append(ChatColor.RED).append("            -").append((int) finalDamage)
                    .append(ChatColor.RED).append(ChatColor.BOLD).append("HP ");

            if (damageResult.isCritical()) {
                debugMessage.append(ChatColor.GOLD).append(ChatColor.BOLD).append("[CRIT ")
                        .append(String.format("%.1fx", damageResult.getCritMultiplier()))
                        .append(": ").append((int) damageResult.getOriginalDamage())
                        .append("->").append((int) calculatedDamage).append("] ");
            }

            debugMessage.append(ChatColor.GRAY).append("[")
                    .append(String.format("%.1f", effectiveReduction)).append("%A -> -")
                    .append((int) (calculatedDamage - finalDamage)).append(ChatColor.BOLD).append("DMG")
                    .append(ChatColor.GRAY).append("] ")
                    .append(ChatColor.GREEN).append("[").append(expectedHealth)
                    .append(ChatColor.BOLD).append("HP").append(ChatColor.GREEN).append("] ")
                    .append(ChatColor.RED).append("-> ").append(ChatColor.RESET).append(mobName);

            victim.sendMessage(debugMessage.toString());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDefensiveAction(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (!(event.getEntity() instanceof Player defender)) return;

        YakPlayer yakDefender = playerManager.getPlayer(defender);
        if (yakDefender == null) return;

        int blockChance = calculateBlockChance(defender);
        int dodgeChance = calculateDodgeChance(defender);

        if (event.getDamager() instanceof Player attacker) {
            // Apply accuracy reduction for PvP
            int accuracy = getAccuracy(attacker);
            blockChance = applyAccuracyReduction(blockChance, accuracy);
            dodgeChance = applyAccuracyReduction(dodgeChance, accuracy);
        }

        Random random = new Random();

        // Handle block
        if (random.nextInt(100) < blockChance) {
            handleSuccessfulBlock(event, defender);
            return;
        }

        // Handle dodge
        if (random.nextInt(100) < dodgeChance) {
            handleSuccessfulDodge(event, defender);
            return;
        }

        // Handle shield blocking (50% damage reduction)
        if (defender.isBlocking() && random.nextInt(100) <= 80) {
            handleShieldBlock(event, defender);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWeaponDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (!(event.getDamager() instanceof Player attacker)) return;

        YakPlayer yakAttacker = playerManager.getPlayer(attacker);
        if (yakAttacker == null || !(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Check for staff weapon
        if (MagicStaff.isRecentStaffShot(attacker)) {
            ItemStack staffWeapon = MagicStaff.getLastUsedStaff(attacker);
            if (staffWeapon != null) {
                weapon = staffWeapon;
            }
            MagicStaff.clearStaffShot(attacker);
        }

        // Clear off-hand item (legacy behavior)
        clearOffHandItem(attacker);

        if (weapon == null || weapon.getType() == Material.AIR || !weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) {
            event.setDamage(1.0);
            return;
        }

        // Calculate weapon damage
        DamageResult damageResult = calculateWeaponDamage(attacker, target, weapon);
        int finalDamage = (int) Math.round(damageResult.damage);

        if (damageResult.isCritical) {
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, 1.2f);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
            hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.CRITICAL_DAMAGE, finalDamage);
        } else {
            hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.DAMAGE, finalDamage);
        }

        // Apply life steal
        applyLifeSteal(attacker, target, weapon, finalDamage);

        // Apply thorns effect
        if (target instanceof Player defender) {
            applyThornsEffect(attacker, defender, finalDamage);
        }

        event.setDamage(finalDamage);

        if (target instanceof Player) {
            markPlayerInCombat((Player) target, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorCalculation(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (!(event.getEntity() instanceof Player defender)) return;

        // Armor reduction for mobs is handled in onMobToPlayerDamage
        if (!(event.getDamager() instanceof Player attacker)) return;

        double damage = event.getDamage();
        double damageReduction = calculateArmorReduction(defender, attacker);
        double reducedDamage = damage * (1 - damageReduction);
        int finalDamage = (int) Math.max(1, Math.round(reducedDamage));

        if (Toggles.isToggled(defender, "Debug")) {
            int expectedHealth = Math.max(0, (int) (defender.getHealth() - finalDamage));
            double effectiveReduction = ((damage - finalDamage) / damage) * 100;

            defender.sendMessage(ChatColor.RED + "            -" + finalDamage + ChatColor.RED + ChatColor.BOLD + "HP " +
                    ChatColor.GRAY + "[" + String.format("%.2f", effectiveReduction) + "%A -> -" +
                    (int) (damage - finalDamage) + ChatColor.BOLD + "DMG" + ChatColor.GRAY + "] " +
                    ChatColor.GREEN + "[" + expectedHealth + ChatColor.BOLD + "HP" + ChatColor.GREEN + "]");
        }

        event.setDamage(finalDamage);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKnockback(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (!(event.getEntity() instanceof LivingEntity target) || !(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }

        UUID targetId = target.getUniqueId();
        if (knockbackCooldowns.containsKey(targetId) &&
                System.currentTimeMillis() - knockbackCooldowns.get(targetId) < KNOCKBACK_COOLDOWN) {
            return;
        }

        target.setNoDamageTicks(0);
        knockbackCooldowns.put(targetId, System.currentTimeMillis());

        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> applyKnockback(target, attacker));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPolearmAoeAttack(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity primaryTarget)) {
            return;
        }

        if (polearmSwingProcessed.contains(attacker.getUniqueId())) return;

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
                    if (!(nearbyEntity instanceof LivingEntity secondaryTarget) ||
                            nearbyEntity == primaryTarget || nearbyEntity == attacker) {
                        continue;
                    }

                    secondaryTarget.setNoDamageTicks(0);
                    Energy.getInstance().removeEnergy(attacker, 2);
                    secondaryTarget.damage(1.0, attacker);

                    hologramHandler.showCombatHologram(attacker, secondaryTarget, CombatHologramHandler.HologramType.DAMAGE, 1);
                }
            }
        } finally {
            polearmSwingProcessed.remove(attacker.getUniqueId());
        }
    }

    @EventHandler
    public void onDamageSound(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity target) {
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

            if (target instanceof Player) {
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.6f);
            } else {
                if (MobManager.getInstance().getCustomMob(target) != null) {
                    MobManager.getInstance().getCustomMob(target).updateHealthBar();
                }
                Sound mobHurtSound = SoundUtil.getMobHurtSound(target);
                target.getWorld().playSound(target.getLocation(), mobHurtSound, 1.0f, 1.0f);
            }
        }

        if (event.getEntity() instanceof Player victim && !(event.getDamager() instanceof Player) &&
                event.getDamager() instanceof LivingEntity) {
            victim.setWalkSpeed(0.165f);
            playerSlowEffects.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDebugDisplay(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        int damage = (int) event.getDamage();
        int remainingHealth = Math.max(0, (int) (target.getHealth() - damage));
        String targetName = target instanceof Player ? target.getName() : getMobName(target);

        if (Toggles.isToggled(attacker, "Debug")) {
            String message = String.format("%s%d%s DMG %s-> %s%s [%dHP]",
                    ChatColor.RED, damage, ChatColor.RED.toString() + ChatColor.BOLD,
                    ChatColor.RED, ChatColor.RESET, targetName, remainingHealth);
            attacker.sendMessage(message);
        }

        if (target instanceof Player) {
            markPlayerInCombat((Player) target, attacker);
        }
    }

    @EventHandler
    public void onDummyUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ARMOR_STAND) return;

        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon == null || weapon.getType() == Material.AIR || !weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) {
            return;
        }

        // Calculate dummy damage using weapon damage calculation
        DamageResult result = calculateWeaponDamage(player, null, weapon);
        int finalDamage = (int) Math.round(result.damage);

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "            " + finalDamage + ChatColor.RED + ChatColor.BOLD +
                " DMG " + ChatColor.RED + "-> " + ChatColor.RESET + "DPS DUMMY" + " [99999999HP]");

        hologramHandler.showCustomHologram(player, null, ChatColor.RED + "" + ChatColor.BOLD + "-" + finalDamage,
                CombatHologramHandler.HologramType.DAMAGE);
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
        if (event.isCancelled() || event.getDamage() <= 0) return;

        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) return;

        if (entity.isDead() || entity.getHealth() <= 0) return;

        double damage = event.getDamage();
        event.setDamage(0.0);
        event.setCancelled(true);

        entity.playEffect(EntityEffect.HURT);
        entity.setMetadata("lastDamaged", new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis()));

        double newHealth = entity.getHealth() - damage;
        entity.setHealth(newHealth <= 0.0 ? 0.0 : newHealth);
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

    // ================================ HELPER METHODS FOR EVENT HANDLERS ================================

    private int applyAccuracyReduction(int chance, int accuracy) {
        double scale = 300;
        double nS = 1.35;

        double effectiveDiminishingFactor = 1.0 / (1.0 + Math.pow(chance / scale, nS));
        double effective = chance * effectiveDiminishingFactor;
        int reduction = (int)(effective * (accuracy / 100.0));
        chance = (int) Math.max(0, effective - reduction);

        // Additional accuracy reduction for high values
        if (chance > 40) {
            chance = chance - (int) (accuracy * (.05 * ((double) chance / 10)));
        }

        return chance;
    }

    private void handleSuccessfulBlock(EntityDamageByEntityEvent event, Player defender) {
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
            hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.BLOCK, 0);
            if (Toggles.isToggled(defender, "Debug")) {
                String mobName = getMobName((LivingEntity) event.getDamager());
                defender.sendMessage(ChatColor.DARK_GREEN + ChatColor.BOLD.toString() + "*BLOCK* (" + mobName + ")");
            }
        }
    }

    private void handleSuccessfulDodge(EntityDamageByEntityEvent event, Player defender) {
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
            hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.DODGE, 0);
            if (Toggles.isToggled(defender, "Debug")) {
                String mobName = getMobName((LivingEntity) event.getDamager());
                defender.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "*DODGE* (" + mobName + ")");
            }
        }
    }

    private void handleShieldBlock(EntityDamageByEntityEvent event, Player defender) {
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
            hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.BLOCK, 0);
            defender.playSound(defender.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

            if (Toggles.isToggled(defender, "Debug")) {
                String mobName = getMobName((LivingEntity) event.getDamager());
                defender.sendMessage(ChatColor.DARK_GREEN + ChatColor.BOLD.toString() + "*PARTIAL BLOCK* (" + mobName + ")");
            }
        }
    }

    private void clearOffHandItem(Player attacker) {
        if (attacker.getInventory().getItemInOffHand().getType() != Material.AIR) {
            ItemStack material = attacker.getInventory().getItemInOffHand();
            attacker.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            if (attacker.getInventory().firstEmpty() == -1) {
                attacker.getWorld().dropItemNaturally(attacker.getLocation(), material);
            } else {
                attacker.getInventory().addItem(material);
            }
        }
    }

    private Sound getProtectionSound(PVPResult.ResultType resultType) {
        return switch (resultType) {
            case BUDDY_PROTECTION, MUTUAL_BUDDY_PROTECTION -> Sound.ENTITY_VILLAGER_NO;
            case PARTY_PROTECTION, MUTUAL_PARTY_PROTECTION -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case GUILD_PROTECTION -> Sound.ENTITY_HORSE_ANGRY;
            case SAFE_ZONE -> Sound.BLOCK_ANVIL_LAND;
            default -> Sound.BLOCK_NOTE_BLOCK_BASS;
        };
    }

    private void sendAdditionalHelpMessage(Player attacker, PVPResult.ResultType resultType) {
        switch (resultType) {
            case BUDDY_PROTECTION, PARTY_PROTECTION, GUILD_PROTECTION ->
                    Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                        if (attacker.isOnline()) {
                            attacker.sendMessage(ChatColor.GRAY + "Enable " + ChatColor.WHITE + "Friendly Fire" +
                                    ChatColor.GRAY + " in " + ChatColor.WHITE + "/toggle" +
                                    ChatColor.GRAY + " to allow this.");
                        }
                    }, 5L);
            case CHAOTIC_PROTECTION ->
                    Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                        if (attacker.isOnline()) {
                            attacker.sendMessage(ChatColor.GRAY + "Disable " + ChatColor.WHITE + "Chaotic Protection" +
                                    ChatColor.GRAY + " in " + ChatColor.WHITE + "/toggle" +
                                    ChatColor.GRAY + " to attack lawful players.");
                        }
                    }, 5L);
        }
    }

    private void logBlockedPVPAttempt(Player attacker, Player victim, PVPResult.ResultType resultType) {
        if (Toggles.isToggled(attacker, "Debug")) {
            // Debug output if needed
        }

        YakRealms.getInstance().getLogger().fine("PVP blocked: " + attacker.getName() +
                " -> " + victim.getName() + " (Reason: " + resultType.name() + ")");
    }

    // ================================ INNER CLASSES ================================

    /**
     * Player stats container for cleaner code organization
     */
    private static class PlayerStats {
        final double dps, vit, str, intel, dex;

        PlayerStats(double dps, double vit, double str, double intel, double dex) {
            this.dps = dps;
            this.vit = vit;
            this.str = str;
            this.intel = intel;
            this.dex = dex;
        }
    }

    /**
     * Damage calculation result container
     */
    private static class DamageResult {
        final double damage;
        final boolean isCritical;
        final int elementalDamage;

        DamageResult(double damage, boolean isCritical, int elementalDamage) {
            this.damage = damage;
            this.isCritical = isCritical;
            this.elementalDamage = elementalDamage;
        }
    }

    /**
     * Mob damage result with crit information
     */
    private static class MobDamageResult {
        private final double damage;
        private final boolean isCritical;
        private final double originalDamage;
        private final double critMultiplier;

        public MobDamageResult(double damage, boolean isCritical, double originalDamage, double critMultiplier) {
            this.damage = damage;
            this.isCritical = isCritical;
            this.originalDamage = originalDamage;
            this.critMultiplier = critMultiplier;
        }

        public double getDamage() { return damage; }
        public boolean isCritical() { return isCritical; }
        public double getOriginalDamage() { return originalDamage; }
        public double getCritMultiplier() { return critMultiplier; }
    }
}