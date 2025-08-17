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
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Comprehensive Combat Mechanics System
 *
 * Handles all combat calculations, PVP protection, damage mechanics,
 * and combat effects for the YakRealms server.
 *
 * @author YakRealms Development Team
 * @version 2.2 (Paper 1.21.7 Update)
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

    // Elemental effect IDs
    private static final int ICE_EFFECT_ID = 13565951;
    private static final int POISON_EFFECT_ID = 8196;

    // Stat scaling constants
    private static final double INTELLIGENCE_CRIT_BONUS = 0.015; // +1.5% per 100 INT
    private static final double STRENGTH_BLOCK_BONUS = 0.015; // +1.5% per 100 STR
    private static final double DEXTERITY_DODGE_BONUS = 0.015; // +1.5% per 100 DEX
    private static final double DEXTERITY_DPS_BONUS = 0.012; // +1.2% per 100 DEX
    private static final double ELEMENTAL_INT_SCALING = 3000.0; // +3.33% per 100 INT
    private static final double WEAPON_STAT_DIVISOR = 5000.0; // +2% per 100 stat
    private static final double DPS_DIVISOR = 200.0; // DPS scaling
    private static final double ARMOR_PEN_DEX_BONUS = 0.0035; // +0.35% per 100 DEX

    // Lore Stat Constants (for robust parsing)
    // NOTE: For future development, consider moving item stats to PersistentDataContainers
    // for a more robust and language-agnostic system. This string-based parsing is
    // maintained for backward compatibility with existing items.
    private static final String LORE_CRITICAL_HIT = "CRITICAL HIT";
    private static final String LORE_HP = "HP";
    private static final String LORE_ARMOR = "ARMOR";
    private static final String LORE_DPS = "DPS";
    private static final String LORE_ENERGY_REGEN = "ENERGY REGEN";
    private static final String LORE_HP_REGEN = "HP REGEN";
    private static final String LORE_BLOCK = "BLOCK";
    private static final String LORE_DODGE = "DODGE";
    private static final String LORE_THORNS = "THORNS";
    private static final String LORE_LIFE_STEAL = "LIFE STEAL";
    private static final String LORE_ACCURACY = "ACCURACY";
    private static final String LORE_ARMOR_PEN = "ARMOR PEN";
    private static final String LORE_INT = "INT";
    private static final String LORE_VIT = "VIT";
    private static final String LORE_STR = "STR";
    private static final String LORE_DEX = "DEX";
    private static final String LORE_DMG = "DMG";
    private static final String LORE_ICE_DMG = "ICE DMG";
    private static final String LORE_POISON_DMG = "POISON DMG";
    private static final String LORE_FIRE_DMG = "FIRE DMG";
    private static final String LORE_PURE_DMG = "PURE DMG";
    private static final String LORE_VS_PLAYERS = "VS PLAYERS";
    private static final String LORE_VS_MONSTERS = "VS MONSTERS";


    // ================================ FIELDS ================================

    // Tracking maps for combat state
    private final Map<UUID, Long> combatTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerSlowEffects = new ConcurrentHashMap<>();
    private final Map<UUID, Long> knockbackCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> entityDamageEffects = new ConcurrentHashMap<>();
    private final Set<UUID> polearmSwingProcessed = Collections.synchronizedSet(new HashSet<>());

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
    }

    public void onDisable() {
        hologramHandler.onDisable();
        cleanupTrackingMaps();
    }

    // ================================ PUBLIC API METHODS ================================

    /**
     * Check if a player is currently in combat
     * @param player The player to check
     * @return true if player is in combat, false otherwise
     */
    public boolean isInCombat(Player player) {
        if (player == null) return false;
        return combatTimestamps.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis() - COMBAT_DURATION;
    }

    /**
     * Get remaining combat time for a player
     * @param player The player
     * @return Remaining combat time in seconds
     */
    public int getRemainingCombatTime(Player player) {
        if (player == null) return 0;
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
        if (player == null) return null;
        UUID attackerId = lastAttackers.get(player.getUniqueId());
        return attackerId != null ? Bukkit.getPlayer(attackerId) : null;
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

    // ================================ LEGACY STATIC METHODS (Updated for Robustness) ================================

    /**
     * Get critical hit chance for a player (legacy compatibility method)
     * @param player The player
     * @return Critical hit chance percentage
     */
    public static int getCrit(Player player) {
        int crit = 0;
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (MagicStaff.isRecentStaffShot(player)) {
            weapon = MagicStaff.getLastUsedStaff(player);
        }

        if (weapon != null && weapon.getType() != Material.AIR && weapon.hasItemMeta()) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && weaponMeta.hasLore()) {
                crit = getPercent(weapon, LORE_CRITICAL_HIT);

                if (weapon.getType().name().contains("_AXE")) {
                    crit += 10;
                }

                int intel = 0;
                for (ItemStack armor : player.getInventory().getArmorContents()) {
                    if (armor != null && armor.getType() != Material.AIR) {
                        intel += getElem(armor, LORE_INT);
                    }
                }

                if (intel > 0) {
                    crit += Math.round(intel * INTELLIGENCE_CRIT_BONUS);
                }
            }
        }
        return crit;
    }

    private static int parseStatFromLore(ItemStack is, String statIdentifier, int loreLine, String splitRegex, int valueIndex) {
        if (is == null || is.getType() == Material.AIR || !is.hasItemMeta()) return 0;
        ItemMeta meta = is.getItemMeta();
        if (meta == null || !meta.hasLore()) return 0;

        List<String> lore = meta.getLore();
        if (lore == null || lore.size() <= loreLine || !lore.get(loreLine).contains(statIdentifier)) return 0;

        try {
            String[] parts = lore.get(loreLine).split(splitRegex);
            if (parts.length > valueIndex) {
                return Integer.parseInt(parts[valueIndex].replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static int getHp(ItemStack is) {
        return parseStatFromLore(is, LORE_HP, 1, ":\\s+", 1);
    }

    public static int getArmor(ItemStack is) {
        return parseStatFromLore(is, LORE_ARMOR, 0, "\\s-\\s", 1);
    }

    public static int getDps(ItemStack is) {
        return parseStatFromLore(is, LORE_DPS, 0, "\\s-\\s", 1);
    }

    public static int getEnergy(ItemStack is) {
        return parseStatFromLore(is, LORE_ENERGY_REGEN, 2, ":\\s+", 1);
    }

    public static int getHps(ItemStack is) {
        return parseStatFromLore(is, LORE_HP_REGEN, 2, ":\\s+", 1);
    }

    public static int getPercent(ItemStack is, String type) {
        if (is != null && is.getType() != Material.AIR && is.hasItemMeta()) {
            ItemMeta meta = is.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (lore == null) return 0;
                for (String s : lore) {
                    if (s.contains(type)) {
                        try {
                            String[] parts = s.split(":\\s");
                            if (parts.length > 1) {
                                return Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return 0;
    }

    public static int getElem(ItemStack itemStack, String type) {
        if (itemStack != null && itemStack.getType() != Material.AIR && itemStack.hasItemMeta()) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta != null && itemMeta.hasLore()) {
                List<String> lore = itemMeta.getLore();
                if (lore == null) return 0;
                for (String line : lore) {
                    if (line.contains(type)) {
                        try {
                            String[] parts = line.split(":\\s+");
                            if (parts.length > 1) {
                                return Integer.parseInt(parts[1]);
                            }
                        } catch (Exception ignored) {}
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
            if (itemMeta != null && itemMeta.hasLore()) {
                List<String> lore = itemMeta.getLore();
                if (lore != null && !lore.isEmpty() && lore.get(0).contains(LORE_DMG)) {
                    try {
                        String[] dmgValues = lore.get(0).split(LORE_DMG + ": ")[1].split(" - ");
                        if (dmgValues.length > 1) {
                            damageRange.set(0, Integer.parseInt(dmgValues[0]));
                            damageRange.set(1, Integer.parseInt(dmgValues[1]));
                        }
                    } catch (Exception ignored) {
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
            return new PVPResult(false, Component.text("Invalid players", NamedTextColor.RED));
        }

        if (attacker.equals(victim)) {
            return new PVPResult(false, Component.text("Cannot attack yourself", NamedTextColor.RED));
        }

        YakPlayer attackerData = playerManager.getPlayer(attacker);
        YakPlayer victimData = playerManager.getPlayer(victim);

        if (attackerData == null || victimData == null) {
            return new PVPResult(false, Component.text("Player data not loaded", NamedTextColor.RED));
        }

        // Check attacker PVP setting
        if (attackerData.isToggled("Anti PVP")) {
            return new PVPResult(false, Component.text("You have PVP disabled! Use /toggle to enable it."),
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
            return new PVPResult(false, Component.text("Chaotic Protection prevented you from attacking " + victim.getName() + "! This player is lawful.", NamedTextColor.RED),
                    PVPResult.ResultType.CHAOTIC_PROTECTION);
        }

        // Check safe zone protection
        if (isSafeZone(attacker.getLocation()) || isSafeZone(victim.getLocation())) {
            return new PVPResult(false, Component.text("PVP is not allowed in safe zones!", NamedTextColor.RED),
                    PVPResult.ResultType.SAFE_ZONE);
        }

        return new PVPResult(true, Component.text("PVP allowed"));
    }

    private PVPResult checkBuddyProtection(Player attacker, Player victim, YakPlayer attackerData, YakPlayer victimData) {
        if (attackerData.isBuddy(victim.getName())) {
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, Component.text("You cannot attack your buddy " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.", NamedTextColor.RED),
                        PVPResult.ResultType.BUDDY_PROTECTION);
            }
        }

        if (victimData.isBuddy(attacker.getName())) {
            if (!victimData.isToggled("Friendly Fire")) {
                return new PVPResult(false, Component.text(victim.getName() + " has you as a buddy and friendly fire disabled!", NamedTextColor.RED),
                        PVPResult.ResultType.MUTUAL_BUDDY_PROTECTION);
            }
        }

        return new PVPResult(true, Component.text("No buddy protection"));
    }

    private PVPResult checkPartyProtection(Player attacker, Player victim, YakPlayer attackerData, YakPlayer victimData) {
        if (PartyMechanics.getInstance().arePartyMembers(attacker, victim)) {
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, Component.text("You cannot attack your party member " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.", NamedTextColor.RED),
                        PVPResult.ResultType.PARTY_PROTECTION);
            }

            if (!victimData.isToggled("Friendly Fire")) {
                return new PVPResult(false, Component.text(victim.getName() + " is your party member and has friendly fire disabled!", NamedTextColor.RED),
                        PVPResult.ResultType.MUTUAL_PARTY_PROTECTION);
            }
        }

        return new PVPResult(true, Component.text("No party protection"));
    }

    private PVPResult checkGuildProtection(Player attacker, Player victim, YakPlayer attackerData, YakPlayer victimData) {
        if (isInSameGuild(attacker, victim)) {
            if (!attackerData.isToggled("Friendly Fire")) {
                return new PVPResult(false, Component.text("You cannot attack your guild member " + victim.getName() + "! Enable Friendly Fire in /toggle to allow this.", NamedTextColor.RED),
                        PVPResult.ResultType.GUILD_PROTECTION);
            }
        }

        return new PVPResult(true, Component.text("No guild protection"));
    }

    /**
     * PVP Result class containing protection check information
     */
    public static class PVPResult {
        public enum ResultType {
            ALLOWED, ATTACKER_PVP_DISABLED, VICTIM_PVP_DISABLED, BUDDY_PROTECTION,
            MUTUAL_BUDDY_PROTECTION, PARTY_PROTECTION, MUTUAL_PARTY_PROTECTION,
            GUILD_PROTECTION, CHAOTIC_PROTECTION, SAFE_ZONE, OTHER
        }

        private final boolean allowed;
        private final Component message;
        private final ResultType resultType;

        public PVPResult(boolean allowed, Component message) {
            this(allowed, message, allowed ? ResultType.ALLOWED : ResultType.OTHER);
        }

        public PVPResult(boolean allowed, Component message, ResultType resultType) {
            this.allowed = allowed;
            this.message = message;
            this.resultType = resultType;
        }

        public boolean isAllowed() { return allowed; }
        public Component getMessage() { return message; }
        public ResultType getResultType() { return resultType; }
    }

    // ================================ STAT CALCULATION METHODS ================================

    /**
     * Calculate all primary and secondary stats for a player
     */
    private PlayerStats calculatePlayerStats(Player player) {
        double dps = 0.0, vit = 0.0, str = 0.0, intel = 0.0, dex = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                dps += getDps(armor);
                vit += getElem(armor, LORE_VIT);
                str += getElem(armor, LORE_STR);
                intel += getElem(armor, LORE_INT);
                dex += getElem(armor, LORE_DEX);
            }
        }

        // Dexterity DPS bonus
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
        if (weapon == null || weapon.getType() == Material.AIR) {
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
            damage *= (1 + stats.vit / WEAPON_STAT_DIVISOR);
        } else if (weaponType.contains("_AXE")) {
            damage *= (1 + stats.str / WEAPON_STAT_DIVISOR);
        } else if (weaponType.contains("_HOE")) {
            damage *= (1 + stats.intel / WEAPON_STAT_DIVISOR);
        }

        return damage;
    }

    private double applyVersusBonus(double damage, ItemStack weapon, LivingEntity target) {
        if (target instanceof Player && hasBonus(weapon, LORE_VS_PLAYERS)) {
            damage *= (1 + getPercent(weapon, LORE_VS_PLAYERS) / 100.0);
        } else if (!(target instanceof Player) && hasBonus(weapon, LORE_VS_MONSTERS)) {
            damage *= (1 + getPercent(weapon, LORE_VS_MONSTERS) / 100.0);
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
            if (armor != null && armor.getType() != Material.AIR) {
                blockChance += getPercent(armor, LORE_BLOCK);
                strength += getElem(armor, LORE_STR);
            }
        }

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
            if (armor != null && armor.getType() != Material.AIR) {
                dodgeChance += getPercent(armor, LORE_DODGE);
                dexterity += getElem(armor, LORE_DEX);
            }
        }

        dodgeChance += Math.round(dexterity * DEXTERITY_DODGE_BONUS);
        return Math.min(dodgeChance, MAX_DODGE_CHANCE);
    }

    /**
     * Calculate armor reduction with balanced diminishing returns
     */
    private double calculateArmorReduction(Player defender, LivingEntity attacker) {
        double armorRating = 0.0;
        int strength = 0;

        for (ItemStack armor : defender.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                armorRating += getArmor(armor);
                strength += getElem(armor, LORE_STR);
            }
        }

        armorRating += strength * 0.1;
        double effectiveArmorPercentage = (armorRating / (armorRating + 200.0)) * 100.0;
        double armorPenetration = calculateArmorPenetration(attacker);
        double remainingArmorPercentage = effectiveArmorPercentage * (1 - armorPenetration);
        remainingArmorPercentage = Math.min(remainingArmorPercentage, MAX_ARMOR_REDUCTION * 100);

        if (attacker instanceof Player && Toggles.isToggled(defender, "Debug")) {
            Component debugMessage = Component.text()
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("DEBUG ARMOR: Raw="))
                    .append(Component.text((int) armorRating, NamedTextColor.AQUA))
                    .append(Component.text(" | Effective="))
                    .append(Component.text(String.format("%.1f%%", effectiveArmorPercentage), NamedTextColor.AQUA))
                    .append(Component.text(" | Pen="))
                    .append(Component.text(String.format("%.1f%%", armorPenetration * 100), NamedTextColor.AQUA))
                    .append(Component.text(" | Final="))
                    .append(Component.text(String.format("%.1f%%", remainingArmorPercentage), NamedTextColor.AQUA))
                    .build();
            defender.sendMessage(debugMessage);
        }

        return remainingArmorPercentage / 100.0;
    }

    private double calculateArmorPenetration(LivingEntity attacker) {
        double armorPenetration = 0.0;

        if (attacker instanceof Player playerAttacker) {
            ItemStack weapon = playerAttacker.getInventory().getItemInMainHand();
            armorPenetration = getElem(weapon, LORE_ARMOR_PEN) / 100.0;
            PlayerStats stats = calculatePlayerStats(playerAttacker);
            armorPenetration += stats.dex * ARMOR_PEN_DEX_BONUS;
        }

        return Math.max(0, Math.min(0.9, armorPenetration));
    }

    // ================================ MOB DAMAGE CALCULATION ================================

    /**
     * Calculate mob damage with weapon stats and critical hit tracking
     */
    private MobDamageResult calculateMobDamageWithCritInfo(LivingEntity mobAttacker, Player victim) {
        try {
            ItemStack weapon = mobAttacker.getEquipment() != null ? mobAttacker.getEquipment().getItemInMainHand() : null;

            if (weapon == null || weapon.getType() == Material.AIR) {
                double fallbackDamage = calculateTierBasedDamage(mobAttacker);
                return new MobDamageResult(fallbackDamage, false, fallbackDamage, 1.0);
            }

            List<Integer> damageRange = getDamageRange(weapon);
            int baseDamage = ThreadLocalRandom.current().nextInt(damageRange.get(0), damageRange.get(1) + 1);

            baseDamage += calculateMobElementalDamage(weapon);
            baseDamage = applyMobTierMultipliers(baseDamage, mobAttacker);

            if (hasBonus(weapon, LORE_VS_PLAYERS)) {
                baseDamage *= (1 + getPercent(weapon, LORE_VS_PLAYERS) / 100.0);
            }
            double originalDamage = baseDamage;

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
            YakRealms.getInstance().getLogger().log(Level.WARNING, "Error calculating mob damage", e);
            double fallbackDamage = calculateTierBasedDamage(mobAttacker);
            return new MobDamageResult(fallbackDamage, false, fallbackDamage, 1.0);
        }
    }

    private int applyMobTierMultipliers(int baseDamage, LivingEntity mobAttacker) {
        int tier = MobUtils.getMobTier(mobAttacker);
        boolean isElite = MobUtils.isElite(mobAttacker);

        double tierMultiplier = switch (tier) {
            case 1 -> 0.8; case 3 -> 1.2; case 4 -> 1.5;
            case 5 -> 2.0; case 6 -> 2.5; default -> 1.0;
        };

        if (isElite) tierMultiplier *= 1.5;

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

        if (isElite) baseDamage *= 1.5;
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
        if (weapon == null || !weapon.hasItemMeta()) return 0;

        int elementalDamage = 0;
        PlayerStats stats = calculatePlayerStats(attacker);
        double elementalBonus = 1.0 + (stats.intel / ELEMENTAL_INT_SCALING);
        int tier = getWeaponTier(weapon);

        if (hasBonus(weapon, LORE_ICE_DMG)) elementalDamage += applyIceDamage(attacker, target, weapon, elementalBonus, tier);
        if (hasBonus(weapon, LORE_POISON_DMG)) elementalDamage += applyPoisonDamage(attacker, target, weapon, elementalBonus, tier);
        if (hasBonus(weapon, LORE_FIRE_DMG)) elementalDamage += applyFireDamage(attacker, target, weapon, elementalBonus, tier);
        if (hasBonus(weapon, LORE_PURE_DMG)) elementalDamage += applyPureDamage(attacker, target, weapon, elementalBonus);

        return (int) applyVersusBonus(elementalDamage, weapon, target);
    }

    @SuppressWarnings("deprecation") // Effect.POTION_BREAK is used for custom color compatibility
    private int applyIceDamage(Player attacker, LivingEntity target, ItemStack weapon, double elementalBonus, int tier) {
        int iceDamage = (int) Math.round(getElem(weapon, LORE_ICE_DMG) * elementalBonus);
        target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, ICE_EFFECT_ID);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + (tier * 5), 0));
        hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_ICE, iceDamage);
        return iceDamage;
    }

    @SuppressWarnings("deprecation")
    private int applyPoisonDamage(Player attacker, LivingEntity target, ItemStack weapon, double elementalBonus, int tier) {
        int poisonDamage = (int) Math.round(getElem(weapon, LORE_POISON_DMG) * elementalBonus);
        target.getWorld().playEffect(target.getLocation().add(0, 1.3, 0), Effect.POTION_BREAK, POISON_EFFECT_ID);
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 15 + (tier * 5), tier >= 3 ? 1 : 0));
        hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_POISON, poisonDamage);
        return poisonDamage;
    }

    private int applyFireDamage(Player attacker, LivingEntity target, ItemStack weapon, double elementalBonus, int tier) {
        int fireDamage = (int) Math.round(getElem(weapon, LORE_FIRE_DMG) * elementalBonus);
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
        target.setFireTicks(15 + (tier * 5));
        hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_FIRE, fireDamage);
        return fireDamage;
    }

    private int applyPureDamage(Player attacker, LivingEntity target, ItemStack weapon, double elementalBonus) {
        int pureDamage = (int) Math.round(getElem(weapon, LORE_PURE_DMG) * elementalBonus);
        hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.ELEMENTAL_PURE, pureDamage);
        return pureDamage;
    }

    private int calculateMobElementalDamage(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) return 0;
        int elementalDamage = 0;
        if (hasBonus(weapon, LORE_ICE_DMG)) elementalDamage += getElem(weapon, LORE_ICE_DMG);
        if (hasBonus(weapon, LORE_POISON_DMG)) elementalDamage += getElem(weapon, LORE_POISON_DMG);
        if (hasBonus(weapon, LORE_FIRE_DMG)) elementalDamage += getElem(weapon, LORE_FIRE_DMG);
        if (hasBonus(weapon, LORE_PURE_DMG)) elementalDamage += getElem(weapon, LORE_PURE_DMG);
        return elementalDamage;
    }

    // ================================ COMBAT EFFECTS ================================

    /**
     * Apply life steal effect with balanced calculations
     */
    private void applyLifeSteal(Player attacker, LivingEntity target, ItemStack weapon, double damageDealt) {
        if (!hasBonus(weapon, LORE_LIFE_STEAL) || target.getType() == EntityType.ARMOR_STAND) {
            return;
        }

        target.getWorld().spawnParticle(Particle.BLOCK, target.getEyeLocation(), 1, Material.REDSTONE_BLOCK.createBlockData());
        double lifeStealPercentage = getPercent(weapon, LORE_LIFE_STEAL);
        int lifeStolen = calculateLifeSteal(damageDealt, lifeStealPercentage);

        attacker.setHealth(Math.min(attacker.getAttribute(Attribute.MAX_HEALTH).getValue(), attacker.getHealth() + lifeStolen));

        if (Toggles.isToggled(attacker, "Debug")) {
            Component message = Component.text()
                    .append(Component.text("+", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(lifeStolen, NamedTextColor.GREEN))
                    .append(Component.text(" HP ", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(String.format("[%d/%dHP]", (int) attacker.getHealth(), (int) attacker.getAttribute(Attribute.MAX_HEALTH).getValue()), NamedTextColor.GRAY))
                    .build();
            attacker.sendMessage(message);
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
                    10, 0.5, 0.5, 0.5, 0.01, Material.OAK_LEAVES.createBlockData());
            attacker.setHealth(Math.max(0, attacker.getHealth() - thornsDamage));
            hologramHandler.showCombatHologram(defender, attacker, CombatHologramHandler.HologramType.THORNS, thornsDamage);
        }
    }

    private int calculateThornsChance(Player player) {
        int thornsChance = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            thornsChance += getPercent(armor, LORE_THORNS);
        }
        return thornsChance;
    }

    private int calculateLifeSteal(double damageDealt, double lifeStealPercentage) {
        return Math.max(1, (int) (damageDealt * (lifeStealPercentage / 125.0)));
    }

    private boolean checkCriticalHit(Player attacker, ItemStack weapon) {
        return ThreadLocalRandom.current().nextInt(100) < calculateCriticalChance(attacker, weapon);
    }

    private int calculateCriticalChance(Player player, ItemStack weapon) {
        int critChance = getPercent(weapon, LORE_CRITICAL_HIT);
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
        Bukkit.getServer().getAsyncScheduler().runAtFixedRate(YakRealms.getInstance(), (task) -> {
            long currentTime = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (playerSlowEffects.containsKey(player.getUniqueId())) {
                    if (currentTime - playerSlowEffects.get(player.getUniqueId()) > PLAYER_SLOW_DURATION) {
                        setPlayerSpeed(player, 0.2f);
                        playerSlowEffects.remove(player.getUniqueId());
                    }
                } else if (player.getWalkSpeed() != 0.2f) {
                    setPlayerSpeed(player, 0.2f);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void startEntityDamageEffectCleanupTask() {
        Bukkit.getServer().getAsyncScheduler().runAtFixedRate(YakRealms.getInstance(), (task) -> {
            entityDamageEffects.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 500);
        }, 500, 500, TimeUnit.MILLISECONDS);
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
        long currentTime = System.currentTimeMillis();
        combatTimestamps.put(victim.getUniqueId(), currentTime);
        lastAttackers.put(victim.getUniqueId(), attacker.getUniqueId());
        combatTimestamps.put(attacker.getUniqueId(), currentTime);
    }

    private void setPlayerSpeed(Player player, float speed) {
        if (player.isOnline()) {
            player.getScheduler().run(YakRealms.getInstance(), (task) -> player.setWalkSpeed(speed), null);
        }
    }

    private boolean isInSameGuild(Player player1, Player player2) {
        if (player1 == null || player2 == null) return false;
        YakPlayer yakPlayer1 = playerManager.getPlayer(player1);
        YakPlayer yakPlayer2 = playerManager.getPlayer(player2);
        if (yakPlayer1 == null || yakPlayer2 == null) return false;
        String guild1 = yakPlayer1.getGuildName();
        String guild2 = yakPlayer2.getGuildName();
        return guild1 != null && !guild1.trim().isEmpty() && guild1.equals(guild2);
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
        if (weapon == null || !weapon.hasItemMeta()) return 1;
        Component displayName = weapon.getItemMeta().displayName();
        if (displayName == null) return 1;

        String legacyName = LegacyComponentSerializer.legacySection().serialize(displayName);

        if (legacyName.contains("§a")) return 2; // Green
        if (legacyName.contains("§9")) return 3; // Blue
        if (legacyName.contains("§6")) return 4; // Gold
        // White (or others) defaults to 1
        return 1;
    }

    private boolean hasBonus(ItemStack item, String attribute) {
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (lore == null) return false;
                for (String line : lore) {
                    if (line.contains(attribute)) return true;
                }
            }
        }
        return false;
    }

    private int getAccuracy(Player player) {
        return getPercent(player.getInventory().getItemInMainHand(), LORE_ACCURACY);
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

            Component customNameComponent = mobAttacker.customName();
            if (customNameComponent != null) {
                String currentName = LegacyComponentSerializer.legacySection().serialize(customNameComponent);
                if (!currentName.isEmpty() && !MobUtils.isHealthBar(currentName)) {
                    return currentName;
                }
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
                            return metaName.contains("§") ? metaName : LegacyComponentSerializer.legacySection().serialize(Component.text(metaName));
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().log(Level.WARNING, "Error getting mob name", e);
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
        } else if (attacker instanceof Player player) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getType().name().contains("_SHOVEL")) {
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

    // ================================ EVENT HANDLERS ================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreventDeadPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && player.isDead()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        Entity entity = event.getEntity();
        if (entity.hasMetadata("pet") || entity.hasMetadata("NPC")) {
            event.setCancelled(true);
            return;
        }
        if (entity instanceof Player player && (player.isOp() || player.getGameMode() == GameMode.CREATIVE || player.isFlying()) && !isGodModeDisabled(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPvpProtection(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
        if (event.isCancelled()) return;

        PVPResult result = checkPVPProtection(attacker, victim);

        if (!result.isAllowed()) {
            event.setCancelled(true);
            hologramHandler.showCombatHologram(attacker, victim, CombatHologramHandler.HologramType.IMMUNE, 0);

            Component message = Component.text("⚠ ", NamedTextColor.RED, TextDecoration.BOLD).append(result.getMessage().colorIfAbsent(NamedTextColor.RED));
            attacker.sendMessage(message);

            Sound sound = Sound.sound(getProtectionSound(result.getResultType()), Sound.Source.PLAYER, 1.0f, 0.5f);
            attacker.playSound(sound);

            sendAdditionalHelpMessage(attacker, result.getResultType());
            logBlockedPVPAttempt(attacker, victim, result.getResultType());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMobToPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof LivingEntity mobAttacker) || mobAttacker instanceof Player) {
            return;
        }

        if (victim.hasMetadata("whirlwindExplosionDamage")) {
            double explosionDamage = victim.getMetadata("whirlwindExplosionDamage").get(0).asDouble();
            event.setDamage(explosionDamage);
            hologramHandler.showCombatHologram(mobAttacker, victim, CombatHologramHandler.HologramType.DAMAGE, (int) explosionDamage);
            return;
        }

        MobDamageResult damageResult = calculateMobDamageWithCritInfo(mobAttacker, victim);
        double calculatedDamage = damageResult.getDamage();
        double finalDamage = Math.max(1, calculatedDamage * (1 - calculateArmorReduction(victim, mobAttacker)));
        event.setDamage(finalDamage);

        if (!damageResult.isCritical()) {
            hologramHandler.showCombatHologram(mobAttacker, victim, CombatHologramHandler.HologramType.DAMAGE, (int) finalDamage);
        }

        if (Toggles.isToggled(victim, "Debug")) {
            Component debugMessage = Component.text("-" + (int) finalDamage, NamedTextColor.RED)
                    .append(Component.text("HP ", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(damageResult.isCritical() ? Component.text(String.format("[CRIT %.1fx] ", damageResult.getCritMultiplier()), NamedTextColor.GOLD, TextDecoration.BOLD) : Component.empty())
                    .append(Component.text(String.format("[%.1f%%A] ", ((calculatedDamage - finalDamage) / calculatedDamage) * 100), NamedTextColor.GRAY))
                    .append(Component.text(String.format("[%dHP] ", Math.max(0, (int) (victim.getHealth() - finalDamage))), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("-> ", NamedTextColor.RED))
                    .append(Component.text(getMobName(mobAttacker)));
            victim.sendMessage(debugMessage);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDefensiveAction(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player defender)) return;

        int blockChance = calculateBlockChance(defender);
        int dodgeChance = calculateDodgeChance(defender);

        if (event.getDamager() instanceof Player attacker) {
            int accuracy = getAccuracy(attacker);
            blockChance = applyAccuracyReduction(blockChance, accuracy);
            dodgeChance = applyAccuracyReduction(dodgeChance, accuracy);
        }

        Random random = new Random();
        if (random.nextInt(100) < blockChance) {
            handleSuccessfulBlock(event, defender);
        } else if (random.nextInt(100) < dodgeChance) {
            handleSuccessfulDodge(event, defender);
        } else if (defender.isBlocking() && random.nextInt(100) <= 80) {
            handleShieldBlock(event, defender);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWeaponDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (MagicStaff.isRecentStaffShot(attacker)) {
            weapon = Optional.ofNullable(MagicStaff.getLastUsedStaff(attacker)).orElse(weapon);
            MagicStaff.clearStaffShot(attacker);
        }
        clearOffHandItem(attacker);

        if (weapon.getType() == Material.AIR) {
            event.setDamage(1.0);
            return;
        }

        DamageResult damageResult = calculateWeaponDamage(attacker, target, weapon);
        int finalDamage = (int) Math.round(damageResult.damage);

        if (damageResult.isCritical) {
            attacker.playSound(Sound.sound(org.bukkit.Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.PLAYER, 1f, 1.2f));
            if (Toggles.isToggled(attacker, "Debug")) attacker.sendMessage(Component.text("                *CRIT*", NamedTextColor.YELLOW, TextDecoration.BOLD));
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
            hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.CRITICAL_DAMAGE, finalDamage);
        } else {
            hologramHandler.showCombatHologram(attacker, target, CombatHologramHandler.HologramType.DAMAGE, finalDamage);
        }

        applyLifeSteal(attacker, target, weapon, finalDamage);
        if (target instanceof Player defender) applyThornsEffect(attacker, defender, finalDamage);
        event.setDamage(finalDamage);
        if (target instanceof Player) markPlayerInCombat((Player) target, attacker);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorCalculation(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player defender) || !(event.getDamager() instanceof Player attacker)) return;

        double damage = event.getDamage();
        double finalDamage = Math.max(1, damage * (1 - calculateArmorReduction(defender, attacker)));

        if (Toggles.isToggled(defender, "Debug")) {
            Component debugMessage = Component.text("-" + (int) finalDamage, NamedTextColor.RED)
                    .append(Component.text("HP ", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(String.format("[%.2f%%A -> -%dDMG] ", ((damage - finalDamage) / damage) * 100, (int)(damage - finalDamage)), NamedTextColor.GRAY, TextDecoration.BOLD))
                    .append(Component.text(String.format("[%dHP]", Math.max(0, (int) (defender.getHealth() - finalDamage))), NamedTextColor.GREEN, TextDecoration.BOLD));
            defender.sendMessage(debugMessage);
        }
        event.setDamage(finalDamage);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKnockback(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity target) || !(event.getDamager() instanceof LivingEntity attacker)) return;

        if (knockbackCooldowns.getOrDefault(target.getUniqueId(), 0L) > System.currentTimeMillis() - KNOCKBACK_COOLDOWN) return;

        target.setNoDamageTicks(0);
        knockbackCooldowns.put(target.getUniqueId(), System.currentTimeMillis());
        target.getScheduler().run(YakRealms.getInstance(), (task) -> applyKnockback(target, attacker), null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPolearmAoeAttack(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity primaryTarget)) return;

        if (polearmSwingProcessed.contains(attacker.getUniqueId())) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!weapon.getType().name().contains("_SHOVEL")) return;

        polearmSwingProcessed.add(attacker.getUniqueId());
        try {
            Energy.getInstance().removeEnergy(attacker, 5);
            for (Entity nearbyEntity : primaryTarget.getNearbyEntities(1, 2, 1)) {
                if (nearbyEntity instanceof LivingEntity secondaryTarget && nearbyEntity != primaryTarget && nearbyEntity != attacker) {
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
        if (event.isCancelled()) return;

        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity target) {
            attacker.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_HURT, Sound.Source.PLAYER, 1.0f, 1.0f));
            if (target instanceof Player) {
                target.getWorld().playSound(Sound.sound(org.bukkit.Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.PLAYER, 1.0f, 1.6f), target);
            } else {
                Optional.ofNullable(MobManager.getInstance().getCustomMob(target)).ifPresent(CustomMob::updateHealthBar);
                target.getWorld().playSound(Sound.sound(SoundUtil.getMobHurtSound(target), Sound.Source.HOSTILE, 1.0f, 1.0f), target);
            }
        }
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof LivingEntity && !(event.getDamager() instanceof Player)) {
            victim.setWalkSpeed(0.165f);
            playerSlowEffects.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDebugDisplay(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) return;

        if (Toggles.isToggled(attacker, "Debug")) {
            int damage = (int) event.getDamage();
            int remainingHealth = Math.max(0, (int) (target.getHealth() - damage));
            String targetName = target instanceof Player ? target.getName() : getMobName(target);
            Component message = Component.text(damage, NamedTextColor.RED)
                    .append(Component.text(" DMG ", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text("-> ", NamedTextColor.RED))
                    .append(Component.text(targetName, NamedTextColor.WHITE))
                    .append(Component.text(" [" + remainingHealth + "HP]", NamedTextColor.WHITE));
            attacker.sendMessage(message);
        }
        if (target instanceof Player playerTarget) markPlayerInCombat(playerTarget, attacker);
    }

    @EventHandler
    public void onDummyUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ARMOR_STAND) return;

        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) return;

        DamageResult result = calculateWeaponDamage(player, null, weapon);
        int finalDamage = (int) Math.round(result.damage);

        event.setCancelled(true);
        player.sendMessage(Component.text(finalDamage, NamedTextColor.RED)
                .append(Component.text(" DMG ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("-> ", NamedTextColor.RED))
                .append(Component.text("DPS DUMMY", NamedTextColor.WHITE))
                .append(Component.text(" [99999999HP]", NamedTextColor.WHITE)));
        hologramHandler.showCustomHologram(player, null, LegacyComponentSerializer.legacySection().serialize(Component.text("-" + finalDamage, NamedTextColor.RED, TextDecoration.BOLD)), CombatHologramHandler.HologramType.DAMAGE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && event.getDamage() >= entity.getHealth()) {
            knockbackCooldowns.remove(entity.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBypassArmor(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player || entity.isDead()) return;

        double damage = event.getDamage();
        event.setCancelled(true);
        entity.playHurtAnimation(1);
        entity.setMetadata("lastDamaged", new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis()));
        entity.setHealth(Math.max(0.0, entity.getHealth() - damage));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        combatTimestamps.remove(playerId);
        lastAttackers.remove(playerId);
        playerSlowEffects.remove(playerId);
        knockbackCooldowns.remove(playerId);
        polearmSwingProcessed.remove(playerId);
        entityDamageEffects.remove(playerId);
    }

    // ================================ HELPER METHODS FOR EVENT HANDLERS ================================

    private int applyAccuracyReduction(int chance, int accuracy) {
        double scale = 300, nS = 1.35;
        double effective = chance * (1.0 / (1.0 + Math.pow(chance / scale, nS)));
        int reduction = (int) (effective * (accuracy / 100.0));
        chance = (int) Math.max(0, effective - reduction);
        if (chance > 40) chance -= (int) (accuracy * (.05 * ((double) chance / 10)));
        return chance;
    }

    private void handleSuccessfulBlock(EntityDamageByEntityEvent event, Player defender) {
        event.setCancelled(true);
        Sound blockSound = Sound.sound(org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, Sound.Source.PLAYER, 1.0f, 1.0f);
        defender.playSound(blockSound);
        hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.BLOCK, 0);

        if (event.getDamager() instanceof Player attacker) {
            attacker.playSound(blockSound);
            if (Toggles.isToggled(attacker, "Debug")) attacker.sendMessage(Component.text("*OPPONENT BLOCKED* (" + defender.getName() + ")", NamedTextColor.RED, TextDecoration.BOLD));
            if (Toggles.isToggled(defender, "Debug")) defender.sendMessage(Component.text("*BLOCK* (" + attacker.getName() + ")", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        } else if (Toggles.isToggled(defender, "Debug") && event.getDamager() instanceof LivingEntity mob) {
            defender.sendMessage(Component.text("*BLOCK* (" + getMobName(mob) + ")", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        }
    }

    private void handleSuccessfulDodge(EntityDamageByEntityEvent event, Player defender) {
        event.setCancelled(true);
        Sound dodgeSound = Sound.sound(org.bukkit.Sound.ENTITY_ZOMBIE_INFECT, Sound.Source.PLAYER, 1.0f, 1.0f);
        defender.playSound(dodgeSound);
        defender.getWorld().spawnParticle(Particle.CLOUD, defender.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
        hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.DODGE, 0);

        if (event.getDamager() instanceof Player attacker) {
            attacker.playSound(dodgeSound);
            if (Toggles.isToggled(attacker, "Debug")) attacker.sendMessage(Component.text("*OPPONENT DODGED* (" + defender.getName() + ")", NamedTextColor.RED, TextDecoration.BOLD));
            if (Toggles.isToggled(defender, "Debug")) defender.sendMessage(Component.text("*DODGE* (" + attacker.getName() + ")", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else if (Toggles.isToggled(defender, "Debug") && event.getDamager() instanceof LivingEntity mob) {
            defender.sendMessage(Component.text("*DODGE* (" + getMobName(mob) + ")", NamedTextColor.GREEN, TextDecoration.BOLD));
        }
    }

    private void handleShieldBlock(EntityDamageByEntityEvent event, Player defender) {
        event.setDamage(event.getDamage() / 2);
        defender.playSound(Sound.sound(org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, Sound.Source.PLAYER, 1.0f, 1.0f));
        hologramHandler.showCombatHologram(event.getDamager(), defender, CombatHologramHandler.HologramType.BLOCK, 0);

        if (event.getDamager() instanceof Player attacker) {
            if (Toggles.isToggled(attacker, "Debug")) attacker.sendMessage(Component.text("*OPPONENT BLOCKED* (" + defender.getName() + ")", NamedTextColor.RED, TextDecoration.BOLD));
            if (Toggles.isToggled(defender, "Debug")) defender.sendMessage(Component.text("*PARTIAL BLOCK* (" + attacker.getName() + ")", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        } else if (Toggles.isToggled(defender, "Debug") && event.getDamager() instanceof LivingEntity mob) {
            defender.sendMessage(Component.text("*PARTIAL BLOCK* (" + getMobName(mob) + ")", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        }
    }

    private void clearOffHandItem(Player attacker) {
        ItemStack offHandItem = attacker.getInventory().getItemInOffHand();
        if (offHandItem.getType() != Material.AIR) {
            attacker.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            if (!attacker.getInventory().addItem(offHandItem).isEmpty()) {
                attacker.getWorld().dropItemNaturally(attacker.getLocation(), offHandItem);
            }
        }
    }

    private org.bukkit.Sound getProtectionSound(PVPResult.ResultType resultType) {
        return switch (resultType) {
            case BUDDY_PROTECTION, MUTUAL_BUDDY_PROTECTION -> org.bukkit.Sound.ENTITY_VILLAGER_NO;
            case PARTY_PROTECTION, MUTUAL_PARTY_PROTECTION -> org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS;
            case GUILD_PROTECTION -> org.bukkit.Sound.ENTITY_HORSE_ANGRY;
            case SAFE_ZONE -> org.bukkit.Sound.BLOCK_ANVIL_LAND;
            default -> org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS;
        };
    }

    private void sendAdditionalHelpMessage(Player attacker, PVPResult.ResultType resultType) {
        Component message = switch (resultType) {
            case BUDDY_PROTECTION, PARTY_PROTECTION, GUILD_PROTECTION -> Component.text("Enable ", NamedTextColor.GRAY)
                    .append(Component.text("Friendly Fire", NamedTextColor.WHITE))
                    .append(Component.text(" in ", NamedTextColor.GRAY))
                    .append(Component.text("/toggle", NamedTextColor.WHITE))
                    .append(Component.text(" to allow this.", NamedTextColor.GRAY));
            case CHAOTIC_PROTECTION -> Component.text("Disable ", NamedTextColor.GRAY)
                    .append(Component.text("Chaotic Protection", NamedTextColor.WHITE))
                    .append(Component.text(" in ", NamedTextColor.GRAY))
                    .append(Component.text("/toggle", NamedTextColor.WHITE))
                    .append(Component.text(" to attack lawful players.", NamedTextColor.GRAY));
            default -> null;
        };

        if (message != null) {
            attacker.getScheduler().runDelayed(YakRealms.getInstance(), (task) -> {
                if (attacker.isOnline()) attacker.sendMessage(message);
            }, null, 5L);
        }
    }

    private void logBlockedPVPAttempt(Player attacker, Player victim, PVPResult.ResultType resultType) {
        YakRealms.getInstance().getLogger().fine("PVP blocked: " + attacker.getName() + " -> " + victim.getName() + " (Reason: " + resultType.name() + ")");
    }

    // ================================ INNER CLASSES ================================
    private static class PlayerStats {
        final double dps, vit, str, intel, dex;
        PlayerStats(double dps, double vit, double str, double intel, double dex) { this.dps = dps; this.vit = vit; this.str = str; this.intel = intel; this.dex = dex; }
    }

    private static class DamageResult {
        final double damage; final boolean isCritical; final int elementalDamage;
        DamageResult(double damage, boolean isCritical, int elementalDamage) { this.damage = damage; this.isCritical = isCritical; this.elementalDamage = elementalDamage; }
    }

    private static class MobDamageResult {
        private final double damage, originalDamage, critMultiplier; private final boolean isCritical;
        MobDamageResult(double damage, boolean isCritical, double originalDamage, double critMultiplier) { this.damage = damage; this.isCritical = isCritical; this.originalDamage = originalDamage; this.critMultiplier = critMultiplier; }
        public double getDamage() { return damage; }
        public boolean isCritical() { return isCritical; }
        public double getOriginalDamage() { return originalDamage; }
        public double getCritMultiplier() { return critMultiplier; }
    }
}