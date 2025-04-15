package com.rednetty.server.mechanics.item.orb;

import com.rednetty.server.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes the application of orbs to items
 */
public class OrbProcessor {
    private static final Logger LOGGER = Logger.getLogger(OrbProcessor.class.getName());
    private static final int LEGENDARY_MINIMUM_PLUS = 4;

    private final OrbGenerator orbGenerator;

    /**
     * Constructor
     */
    public OrbProcessor() {
        this.orbGenerator = OrbAPI.getOrbGenerator();
    }

    /**
     * Apply an orb to an item
     *
     * @param item        The item to modify
     * @param isLegendary Whether using a legendary orb
     * @param bonusRolls  Bonus rolls for legendary orbs
     * @return The modified item
     */
    public ItemStack applyOrbToItem(ItemStack item, boolean isLegendary, int bonusRolls) {
        if (item == null) {
            LOGGER.warning("Cannot apply orb to null item");
            return null;
        }

        if (!item.hasItemMeta()) {
            LOGGER.warning("Cannot apply orb to item without metadata");
            return item;
        }

        LOGGER.info("Applying " + (isLegendary ? "legendary" : "normal") + " orb to item: " +
                item.getType() + ", with display name: " +
                (item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "none"));

        try {
            // Clone the item to avoid modifying the original during processing
            ItemStack result = item.clone();

            // Get item information
            String oldName = result.getItemMeta().hasDisplayName() ?
                    result.getItemMeta().getDisplayName() :
                    result.getType().name();

            LOGGER.info("Item old name: " + oldName);

            // Initialize lore if it doesn't exist
            ItemMeta meta = result.getItemMeta();
            List<String> oldLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            List<String> newLore = new ArrayList<>();

            // Extract rare/special lore that should be preserved
            List<String> specialLore = OrbAPI.extractSpecialLore(result);
            LOGGER.info("Extracted special lore: " + specialLore.size() + " lines");

            // Get item tier and type
            int tier = OrbAPI.getItemTier(result);
            int itemType = OrbAPI.getItemType(result);

            LOGGER.info("Item tier: " + tier + ", Item type: " + itemType);

            if (tier <= 0) {
                // Default to tier 1 if not detectable
                tier = 1;
                LOGGER.warning("Could not detect item tier, defaulting to 1");
            }

            if (itemType == OrbAPI.TYPE_UNKNOWN) {
                // Try to determine item type from material
                String material = result.getType().name();
                if (material.contains("_SWORD") || material.contains("_AXE") ||
                        material.contains("_HOE") || material.contains("_SPADE")) {
                    itemType = OrbAPI.TYPE_SWORD; // Default to sword for weapons
                    LOGGER.info("Detected item as weapon, using sword type");
                } else if (material.contains("_HELMET") || material.contains("_CHESTPLATE") ||
                        material.contains("_LEGGINGS") || material.contains("_BOOTS")) {
                    itemType = OrbAPI.TYPE_HELMET; // Default to helmet for armor
                    LOGGER.info("Detected item as armor, using helmet type");
                } else {
                    LOGGER.warning("Could not determine item type, cannot apply orb");
                    return item; // Return original if still can't determine
                }
            }

            // For legendary orbs, enhance items below +4
            int plus = Enchants.getPlus(result);
            LOGGER.info("Current plus level: " + plus);

            if (isLegendary && plus < LEGENDARY_MINIMUM_PLUS) {
                LOGGER.info("Enhancing item from +" + plus + " to +" + LEGENDARY_MINIMUM_PLUS);
                enhanceItemStats(result, oldLore, plus);
                plus = LEGENDARY_MINIMUM_PLUS;
            }

            // Generate stat results based on tier and type
            LOGGER.info("Generating stats for tier " + tier + ", isWeapon=" + OrbAPI.isWeapon(result));
            OrbGenerator.StatResult stats = orbGenerator.generateStats(
                    tier,
                    OrbAPI.isWeapon(result),
                    isLegendary,
                    bonusRolls
            );

            // Generate item name based on stats
            String name = generateItemName(result, tier, itemType, stats);
            LOGGER.info("Generated new name: " + name);

            // Generate appropriate lore based on item type
            if (OrbAPI.isWeapon(result)) {
                LOGGER.info("Adding weapon stats to lore");
                addWeaponStatsToLore(result, newLore, oldLore, stats);
            } else if (OrbAPI.isArmor(result)) {
                LOGGER.info("Adding armor stats to lore");
                addArmorStatsToLore(result, newLore, oldLore, stats);
            }

            // Preserve elite item names
            NBTAccessor nbt = new NBTAccessor(result);
            if (oldName != null && nbt.getInteger("namedElite") == 1) {
                LOGGER.info("Preserving elite item name: " + oldName);
                name = oldName;

                // Add custom item description lore
                for (String line : oldLore) {
                    if (line.startsWith(ChatColor.GRAY.toString()) && !newLore.contains(line)) {
                        newLore.add(line);
                    }
                }
            }

            // Add special lore lines
            newLore.addAll(specialLore);

            // Apply proper coloring and plus level to name
            name = OrbAPI.applyColorByTier(name, tier);
            name = applyPlusToName(name, plus);

            // Update item metadata
            meta.setDisplayName(name);
            meta.setLore(newLore);
            result.setItemMeta(meta);

            // Add glow effect for higher plus items
            if (plus >= 4) {
                Enchants.addGlow(result);
            }

            // Reset item durability
            result.setDurability((short) 0);

            LOGGER.info("Successfully modified item. New name: " + name + ", New lore lines: " + newLore.size());
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error applying orb to item", e);
            return item; // Return original item if anything goes wrong
        }
    }

    /**
     * Generate an item name based on stats and type
     *
     * @param item     The item being modified
     * @param tier     The item tier
     * @param itemType The item type
     * @param stats    The generated stats
     * @return The generated item name
     */
    private String generateItemName(ItemStack item, int tier, int itemType, OrbGenerator.StatResult stats) {
        String name = OrbAPI.getBaseItemName(itemType, tier);

        // Add prefixes and suffixes based on active stats
        if (OrbAPI.isWeapon(item)) {
            if (itemType == OrbAPI.TYPE_AXE && stats.pureActive) {
                name = "Pure " + name;
            }
            if (stats.accuracyActive) {
                name = "Accurate " + name;
            }
            if (stats.lifeStealActive) {
                name = "Vampyric " + name;
            }
            if (stats.criticalHitActive) {
                name = "Deadly " + name;
            }
            if (stats.elementType == OrbAPI.ELEM_FIRE) {
                name = name + " of Fire";
            } else if (stats.elementType == OrbAPI.ELEM_POISON) {
                name = name + " of Poison";
            } else if (stats.elementType == OrbAPI.ELEM_ICE) {
                name = name + " of Ice";
            }
        } else if (OrbAPI.isArmor(item)) {
            if (OrbAPI.hasHPRegen(item)) {
                name = "Mending " + name;
            }
            if (stats.dodgeActive) {
                name = "Agile " + name;
            }
            if (stats.thornsActive) {
                name = "Thorny " + name;
            }
            if (stats.blockActive) {
                name = "Protective " + name;
            }
            if (OrbAPI.hasEnergyRegen(item)) {
                name = name + " of Fortitude";
            }
        }

        return name;
    }

    /**
     * Enhance an item's stats based on plus level for legendary orbs
     *
     * @param item    The item to enhance
     * @param oldLore The item's current lore
     * @param plus    The current plus level
     */
    private void enhanceItemStats(ItemStack item, List<String> oldLore, int plus) {
        try {
            if (OrbAPI.isWeapon(item)) {
                enhanceWeaponStats(item, oldLore, plus);
            } else if (OrbAPI.isArmor(item)) {
                enhanceArmorStats(item, oldLore, plus);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error enhancing item stats", e);
        }
    }

    /**
     * Enhance weapon stats based on plus level
     *
     * @param item    The weapon to enhance
     * @param oldLore The weapon's current lore
     * @param plus    The current plus level
     */
    private void enhanceWeaponStats(ItemStack item, List<String> oldLore, int plus) {
        try {
            int[] damage = OrbAPI.getDamageRange(item);
            int minDamage = damage[0];
            int maxDamage = damage[1];

            double addedMin = 0;
            double addedMax = 0;

            switch (plus) {
                case 0:
                    addedMin = minDamage * 0.20;
                    addedMax = maxDamage * 0.20;
                    break;
                case 1:
                    addedMin = minDamage * 0.15;
                    addedMax = maxDamage * 0.15;
                    break;
                case 2:
                    addedMin = minDamage * 0.10;
                    addedMax = maxDamage * 0.10;
                    break;
                case 3:
                    addedMin = minDamage * 0.05;
                    addedMax = maxDamage * 0.05;
                    break;
                default:
                    // No enhancement needed for higher plus levels
                    return;
            }

            addedMin = Math.max(1.0, addedMin);
            addedMax = Math.max(1.0, addedMax);

            int newMin = (int) (minDamage + addedMin);
            int newMax = (int) (maxDamage + addedMax);

            LOGGER.info("Enhancing weapon damage from " + minDamage + "-" + maxDamage +
                    " to " + newMin + "-" + newMax);

            // Update the damage line in lore
            for (int i = 0; i < oldLore.size(); i++) {
                String line = oldLore.get(i);
                if (line.contains("DMG:")) {
                    oldLore.set(i, ChatColor.RED + "DMG: " + newMin + " - " + newMax);
                    return;
                }
            }

            // Add damage line if not found
            if (oldLore.isEmpty()) {
                oldLore.add(ChatColor.RED + "DMG: " + newMin + " - " + newMax);
            } else {
                oldLore.add(0, ChatColor.RED + "DMG: " + newMin + " - " + newMax);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error enhancing weapon stats", e);
        }
    }

    /**
     * Enhance armor stats based on plus level
     *
     * @param item    The armor to enhance
     * @param oldLore The armor's current lore
     * @param plus    The current plus level
     */
    private void enhanceArmorStats(ItemStack item, List<String> oldLore, int plus) {
        try {
            int hp = OrbAPI.getHP(item);
            int hpRegen = OrbAPI.getHPRegen(item);
            int energy = OrbAPI.getEnergyRegen(item);

            double addedHp = 0;
            double addedHpRegen = 0;
            int addedEnergy = energy;

            switch (plus) {
                case 0:
                    addedHp = hp * 0.20;
                    addedHpRegen = hpRegen * 0.20;
                    addedEnergy += 4;
                    break;
                case 1:
                    addedHp = hp * 0.15;
                    addedHpRegen = hpRegen * 0.15;
                    addedEnergy += 3;
                    break;
                case 2:
                    addedHp = hp * 0.10;
                    addedHpRegen = hpRegen * 0.10;
                    addedEnergy += 2;
                    break;
                case 3:
                    addedHp = hp * 0.05;
                    addedHpRegen = hpRegen * 0.05;
                    addedEnergy += 1;
                    break;
                default:
                    // No enhancement needed for higher plus levels
                    return;
            }

            addedHp = Math.max(1.0, addedHp);
            int newHp = (int) (hp + addedHp);

            LOGGER.info("Enhancing armor HP from " + hp + " to " + newHp);

            // Update or add the HP line
            boolean foundHp = false;
            boolean foundRegen = false;

            for (int i = 0; i < oldLore.size(); i++) {
                String line = oldLore.get(i);
                if (line.contains("HP: +")) {
                    oldLore.set(i, ChatColor.RED + "HP: +" + newHp);
                    foundHp = true;
                } else if (line.contains("ENERGY REGEN:")) {
                    oldLore.set(i, ChatColor.RED + "ENERGY REGEN: +" + addedEnergy + "%");
                    foundRegen = true;
                } else if (line.contains("HP REGEN:")) {
                    addedHpRegen = Math.max(1.0, addedHpRegen);
                    int newHpRegen = (int) (hpRegen + addedHpRegen);
                    oldLore.set(i, ChatColor.RED + "HP REGEN: +" + newHpRegen + "/s");
                    foundRegen = true;
                }
            }

            // Add HP line if not found
            if (!foundHp) {
                if (oldLore.isEmpty()) {
                    oldLore.add(ChatColor.RED + "HP: +" + newHp);
                } else {
                    oldLore.add(0, ChatColor.RED + "HP: +" + newHp);
                }
            }

            // Add regen line if not found
            if (!foundRegen) {
                if (oldLore.isEmpty()) {
                    oldLore.add(ChatColor.RED + "ENERGY REGEN: +" + addedEnergy + "%");
                } else if (oldLore.size() == 1) {
                    oldLore.add(ChatColor.RED + "ENERGY REGEN: +" + addedEnergy + "%");
                } else {
                    oldLore.add(1, ChatColor.RED + "ENERGY REGEN: +" + addedEnergy + "%");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error enhancing armor stats", e);
        }
    }

    /**
     * Add weapon-specific stats to the lore
     *
     * @param item    The weapon item
     * @param newLore The new lore list to modify
     * @param oldLore The current lore list
     * @param stats   The generated stats
     */
    private void addWeaponStatsToLore(ItemStack item, List<String> newLore, List<String> oldLore, OrbGenerator.StatResult stats) {
        try {
            // Add base damage line
            boolean foundDamage = false;
            for (String line : oldLore) {
                if (line.contains("DMG:")) {
                    newLore.add(line);
                    foundDamage = true;
                    break;
                }
            }

            // Add default damage if not found
            if (!foundDamage) {
                int[] damage = OrbAPI.getDamageRange(item);
                newLore.add(ChatColor.RED + "DMG: " + damage[0] + " - " + damage[1]);
            }

            int itemType = OrbAPI.getItemType(item);

            // Add Pure DMG for axes
            if (itemType == OrbAPI.TYPE_AXE && stats.pureActive) {
                newLore.add(ChatColor.RED + "PURE DMG: +" + stats.pureAmount);
            }

            // Add Accuracy
            if (stats.accuracyActive) {
                newLore.add(ChatColor.RED + "ACCURACY: " + stats.accuracyAmount + "%");
            }

            // Add VS Monsters
            if (stats.vsMonstersActive) {
                newLore.add(ChatColor.RED + "VS MONSTERS: " + stats.vsMonstersAmount + "%");
            }

            // Add VS Players
            if (stats.vsPlayersActive) {
                newLore.add(ChatColor.RED + "VS PLAYERS: " + stats.vsPlayersAmount + "%");
            }

            // Add Life Steal
            if (stats.lifeStealActive) {
                newLore.add(ChatColor.RED + "LIFE STEAL: " + stats.lifeStealAmount + "%");
            }

            // Add Critical Hit
            if (stats.criticalHitActive) {
                newLore.add(ChatColor.RED + "CRITICAL HIT: " + stats.criticalHitAmount + "%");
            }

            // Add Elemental Damage
            if (stats.elementType > 0) {
                switch (stats.elementType) {
                    case OrbAPI.ELEM_FIRE:
                        newLore.add(ChatColor.RED + "FIRE DMG: +" + stats.elementAmount);
                        break;
                    case OrbAPI.ELEM_POISON:
                        newLore.add(ChatColor.RED + "POISON DMG: +" + stats.elementAmount);
                        break;
                    case OrbAPI.ELEM_ICE:
                        newLore.add(ChatColor.RED + "ICE DMG: +" + stats.elementAmount);
                        break;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error adding weapon stats to lore", e);
        }
    }

    /**
     * Add armor-specific stats to the lore
     *
     * @param item    The armor item
     * @param newLore The new lore list to modify
     * @param oldLore The current lore list
     * @param stats   The generated stats
     */
    private void addArmorStatsToLore(ItemStack item, List<String> newLore, List<String> oldLore, OrbGenerator.StatResult stats) {
        try {
            // Add base armor stats (first 3 lines or less if not that many)
            int linesToKeep = Math.min(3, oldLore.size());
            for (int i = 0; i < linesToKeep; i++) {
                newLore.add(oldLore.get(i));
            }

            // If no base stats, add some defaults
            if (linesToKeep == 0) {
                newLore.add(ChatColor.RED + "HP: +20");
                newLore.add(ChatColor.RED + "ENERGY REGEN: +1%");
            } else if (linesToKeep == 1 && !oldLore.get(0).contains("ENERGY REGEN") && !oldLore.get(0).contains("HP REGEN")) {
                newLore.add(ChatColor.RED + "ENERGY REGEN: +1%");
            }

            // Add Intelligence
            if (stats.intelligenceActive) {
                newLore.add(ChatColor.RED + "INT: +" + stats.intelligenceAmount);
            }

            // Add Strength
            if (stats.strengthActive) {
                newLore.add(ChatColor.RED + "STR: +" + stats.strengthAmount);
            }

            // Add Vitality
            if (stats.vitalityActive) {
                newLore.add(ChatColor.RED + "VIT: +" + stats.vitalityAmount);
            }

            // Add Dexterity
            if (stats.dexterityActive) {
                newLore.add(ChatColor.RED + "DEX: +" + stats.dexterityAmount);
            }

            // Add Dodge
            if (stats.dodgeActive) {
                newLore.add(ChatColor.RED + "DODGE: " + stats.dodgeAmount + "%");
            }

            // Add Thorns
            if (stats.thornsActive) {
                newLore.add(ChatColor.RED + "THORNS: " + stats.thornsAmount + "%");
            }

            // Add Block
            if (stats.blockActive) {
                newLore.add(ChatColor.RED + "BLOCK: " + stats.blockAmount + "%");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error adding armor stats to lore", e);
        }
    }

    /**
     * Apply plus level to an item name
     *
     * @param name The item name
     * @param plus The plus level
     * @return The name with plus level
     */
    private String applyPlusToName(String name, int plus) {
        if (plus <= 0) {
            return name;
        }

        // Look for existing plus pattern and replace it
        Pattern plusPattern = Pattern.compile("\\[\\+\\d+\\]");
        Matcher matcher = plusPattern.matcher(name);

        if (matcher.find()) {
            return name.replaceAll("\\[\\+\\d+\\]", "[+" + plus + "]");
        } else {
            // Add new plus prefix
            return ChatColor.RED + "[+" + plus + "] " + name;
        }
    }
}