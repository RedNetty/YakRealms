package com.rednetty.server.core.mechanics.player.stats;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for calculating player statistics from equipment and attributes.
 * This class centralizes all stat calculation logic for use across the server.
 */
public class PlayerStatsCalculator {

    // Constants from CombatMechanics
    private static final int MAX_BLOCK_CHANCE = 60;
    private static final int MAX_DODGE_CHANCE = 60;

    // Regex patterns for extracting numeric values
    private static final Pattern HP_PATTERN = Pattern.compile("HP: \\+(\\d+)");
    private static final Pattern HP_REGEN_PATTERN = Pattern.compile("HP REGEN: \\+(\\d+)/s");
    private static final Pattern ENERGY_REGEN_PATTERN = Pattern.compile("ENERGY REGEN: \\+(\\d+)%");
    private static final Pattern DAMAGE_PATTERN = Pattern.compile("DMG: (\\d+) - (\\d+)");

    // Instance for static import access
    public static final PlayerStatsCalculator get = new PlayerStatsCalculator();

    /**
     * Calculate a player's total armor value from equipped items
     *
     * @param player The player to calculate for
     * @return The total armor value
     */
    public static int calculateTotalArmor(Player player) {
        int totalArmor = 0;

        // Add armor from equipped items
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalArmor += getArmorValue(armor);
            }
        }

        // Add STR bonus to armor
        int strength = calculateTotalAttribute(player, "STR");
        if (strength > 0) {
            totalArmor += strength * 0.1; // 0.1 armor per strength point
        }

        return totalArmor;
    }

    /**
     * Calculate a player's total DPS value from equipped items
     *
     * @param player The player to calculate for
     * @return The total DPS value
     */
    public static int calculateTotalDps(Player player) {
        int totalDps = 0;

        // Add DPS from equipped items
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalDps += getDpsValue(armor);
            }
        }

        // Add DEX bonus to DPS
        int dexterity = calculateTotalAttribute(player, "DEX");
        if (dexterity > 0) {
            totalDps += Math.round(dexterity * 0.012f);
        }

        return totalDps;
    }

    /**
     * Calculate a player's total health regeneration from equipped items
     *
     * @param player The player to calculate for
     * @return The total health regeneration per second
     */
    public static int calculateTotalHealthRegen(Player player) {
        int totalRegen = 5; // Base regeneration

        // Add regen from equipped items
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalRegen += getHpsFromItem(armor);
            }
        }

        // Add VIT bonus to HP regen
        int vitality = calculateTotalAttribute(player, "VIT");
        if (vitality > 0) {
            totalRegen += Math.round(vitality * 0.1f);
        }

        return totalRegen;
    }

    /**
     * Calculate a player's total energy value from equipped items
     *
     * @param player The player to calculate for
     * @return The total energy value
     */
    public static int calculateTotalEnergy(Player player) {
        int totalEnergy = 100; // Base energy

        // Add energy from equipped items
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalEnergy += getEnergyFromItem(armor);
            }
        }

        // Add INT bonus to Energy
        int intellect = calculateTotalAttribute(player, "INT");
        if (intellect > 0) {
            totalEnergy += Math.round(intellect / 125);
        }

        return totalEnergy;
    }

    /**
     * Calculate a player's dodge chance from equipped items
     *
     * @param player The player to calculate for
     * @return The dodge chance percentage (0-100)
     */
    public static int calculateDodgeChance(Player player) {
        int totalDodge = 0;

        // Add dodge from equipped items
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalDodge += getAttributePercent(armor, "DODGE");
            }
        }

        // Add DEX bonus to dodge
        int dexterity = calculateTotalAttribute(player, "DEX");
        if (dexterity > 0) {
            totalDodge += Math.round(dexterity * 0.015f);
        }

        // Cap at maximum dodge chance
        return Math.min(totalDodge, MAX_DODGE_CHANCE);
    }

    /**
     * Calculate a player's block chance from equipped items
     *
     * @param player The player to calculate for
     * @return The block chance percentage (0-100)
     */
    public static int calculateBlockChance(Player player) {
        int totalBlock = 0;

        // Add block from equipped items
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalBlock += getAttributePercent(armor, "BLOCK");
            }
        }

        // Add STR bonus to block
        int strength = calculateTotalAttribute(player, "STR");
        if (strength > 0) {
            totalBlock += Math.round(strength * 0.015f);
        }

        // Cap at maximum block chance
        return Math.min(totalBlock, MAX_BLOCK_CHANCE);
    }

    /**
     * Calculate a player's critical hit chance from equipped items and weapon
     *
     * @param player The player to calculate for
     * @param weapon The weapon being used (can be null)
     * @return The critical hit chance percentage (0-100)
     */
    public static int calculateCriticalChance(Player player, ItemStack weapon) {
        int critChance = 0;

        // Add base critical chance from weapon
        if (weapon != null && hasValidItemStats(weapon)) {
            critChance += getAttributePercent(weapon, "CRITICAL HIT");

            // Weapon type bonus (axes have higher crit chance)
            if (weapon.getType().name().contains("_AXE")) {
                critChance += 10;
            }
        }

        // Add INT bonus to critical chance
        int intelligence = calculateTotalAttribute(player, "INT");
        if (intelligence > 0) {
            critChance += Math.round(intelligence * 0.015f);
        }

        return critChance;
    }

    /**
     * Calculate a player's total attribute value from all equipped items
     *
     * @param player    The player to calculate for
     * @param attribute The attribute to calculate (STR, VIT, INT, DEX)
     * @return The total attribute value
     */
    public static int calculateTotalAttribute(Player player, String attribute) {
        int total = 0;

        // Sum the attribute from all equipped armor
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                total += getElementalAttribute(armor, attribute);
            }
        }

        return total;
    }

    /**
     * Get HP value from an item
     *
     * @param item The item to check
     * @return The HP value
     */
    public static int getHp(ItemStack item) {
        if (!hasValidItemStats(item)) {
            return 0;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("HP: +")) {
                Matcher matcher = HP_PATTERN.matcher(line);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }
        return 0;
    }

    /**
     * Get HP regen value from an item
     *
     * @param item The item to check
     * @return The HP regen value
     */
    public static int getHps(ItemStack item) {
        if (!hasValidItemStats(item)) {
            return 0;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("HP REGEN")) {
                Matcher matcher = HP_REGEN_PATTERN.matcher(line);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }
        return 0;
    }

    /**
     * Get energy regen value from an item
     *
     * @param item The item to check
     * @return The energy regen value
     */
    public static int getEnergy(ItemStack item) {
        if (!hasValidItemStats(item)) {
            return 0;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("ENERGY REGEN")) {
                Matcher matcher = ENERGY_REGEN_PATTERN.matcher(line);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }
        return 0;
    }

    /**
     * Get damage range for a weapon
     *
     * @param weapon The weapon to check
     * @return List containing [minDamage, maxDamage]
     */
    public static List<Integer> getDamageRange(ItemStack weapon) {
        List<Integer> range = new ArrayList<>(Arrays.asList(1, 1));

        if (hasValidItemStats(weapon)) {
            List<String> lore = weapon.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("DMG:")) {
                    Matcher matcher = DAMAGE_PATTERN.matcher(line);
                    if (matcher.find()) {
                        range.set(0, Integer.parseInt(matcher.group(1)));
                        range.set(1, Integer.parseInt(matcher.group(2)));
                        break;
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
    public static int getArmorValue(ItemStack item) {
        if (hasValidItemStats(item)) {
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
    public static int getDpsValue(ItemStack item) {
        if (hasValidItemStats(item)) {
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
     * Extract HPS (healing per second) value from an item
     *
     * @param item The item to check
     * @return The HPS value
     */
    public static int getHpsFromItem(ItemStack item) {
        if (hasValidItemStats(item)) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("HP REGEN") || line.contains("HPS")) {
                    try {
                        // Extract the numeric value
                        String valueText = line.replaceAll("[^0-9]", "");
                        return Integer.parseInt(valueText);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Extract energy value from an item
     *
     * @param item The item to check
     * @return The energy value
     */
    public static int getEnergyFromItem(ItemStack item) {
        if (hasValidItemStats(item)) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("ENERGY REGEN")) {
                    try {
                        // Extract the numeric value
                        String valueText = line.split("\\+")[1].split("%")[0].trim();
                        return Integer.parseInt(valueText);
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
    public static int getElementalAttribute(ItemStack item, String attribute) {
        if (hasValidItemStats(item)) {
            List<String> lore = item.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains(attribute + ":")) {
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
    public static int getAttributePercent(ItemStack item, String attribute) {
        if (hasValidItemStats(item)) {
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
    public static boolean hasBonus(ItemStack item, String attribute) {
        if (hasValidItemStats(item)) {
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
     * Check if an item has valid stats
     *
     * @param item The item to check
     * @return true if the item has valid stats
     */
    public static boolean hasValidItemStats(ItemStack item) {
        return item != null &&
                item.getType() != Material.AIR &&
                item.hasItemMeta() &&
                item.getItemMeta().hasLore();
    }

    /**
     * Calculate derived strength bonuses
     *
     * @param strength The strength value
     * @return An array of derived bonuses [armorBonus, blockBonus, axeDmgBonus, polearmDmgBonus]
     */
    public static float[] getStrengthBonuses(int strength) {
        float[] bonuses = new float[4];
        bonuses[0] = strength * 0.012f;  // Armor bonus percent
        bonuses[1] = strength * 0.015f;  // Block bonus percent
        bonuses[2] = strength / 50.0f;   // Axe damage bonus percent
        bonuses[3] = strength / 50.0f;   // Polearm damage bonus percent
        return bonuses;
    }

    /**
     * Calculate derived vitality bonuses
     *
     * @param vitality The vitality value
     * @return An array of derived bonuses [healthBonus, hpsBonus, swordDmgBonus]
     */
    public static float[] getVitalityBonuses(int vitality) {
        float[] bonuses = new float[3];
        bonuses[0] = vitality * 0.05f;   // Health bonus percent
        bonuses[1] = vitality * 0.1f;    // HP regen bonus
        bonuses[2] = vitality / 50.0f;   // Sword damage bonus percent
        return bonuses;
    }

    /**
     * Calculate derived intellect bonuses
     *
     * @param intellect The intellect value
     * @return An array of derived bonuses [staffDmgBonus, energyBonus, eleDmgBonus, critBonus]
     */
    public static float[] getIntellectBonuses(int intellect) {
        float[] bonuses = new float[4];
        bonuses[0] = intellect / 50.0f;     // Staff damage bonus percent
        bonuses[1] = intellect * 0.009f;    // Energy bonus percent
        bonuses[2] = intellect / 30.0f;     // Elemental damage bonus
        bonuses[3] = intellect * 0.015f;    // Critical hit bonus percent
        return bonuses;
    }

    /**
     * Calculate derived dexterity bonuses
     *
     * @param dexterity The dexterity value
     * @return An array of derived bonuses [dodgeBonus, dpsBonus, armorPenBonus]
     */
    public static float[] getDexterityBonuses(int dexterity) {
        float[] bonuses = new float[3];
        bonuses[0] = dexterity * 0.015f;    // Dodge bonus percent
        bonuses[1] = dexterity * 0.012f;    // DPS bonus percent
        bonuses[2] = dexterity * 0.035f;    // Armor penetration percent
        return bonuses;
    }
}