package com.rednetty.server.mechanics.item.orb;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MAJOR FIX: Complete rewrite of OrbManager to prevent duplicate stats
 * Core Logic: When applying orbs, completely rebuild item lore instead of adding to existing stats
 * Orbs now preserve base damage/HP/regen for normal orbs, and enhance them for legendary orbs when upgrading plus levels
 */
public class OrbManager {
    private static OrbManager instance;
    private final Logger logger;
    private OrbHandler orbHandler;
    private OrbGenerator orbGenerator;
    private OrbProcessor orbProcessor;

    // Constants for legendary orb enhancement
    private static final int LEGENDARY_MINIMUM_PLUS = 4;

    // Pattern for extracting plus levels from item names
    private static final Pattern PLUS_PATTERN = Pattern.compile("\\[\\+(\\d+)\\]");

    /**
     * Private constructor for singleton pattern
     */
    private OrbManager() {
        this.logger = YakRealms.getInstance().getLogger();
        // Use lazy initialization to prevent circular dependencies
        this.orbHandler = null;
        this.orbGenerator = null;
        this.orbProcessor = null;
    }

    /**
     * Gets the singleton instance
     */
    public static OrbManager getInstance() {
        if (instance == null) {
            instance = new OrbManager();
        }
        return instance;
    }

    /**
     * Initializes the orb system
     */
    public void initialize() {
        // Initialize the OrbAPI first
        OrbAPI.initialize();

        // Initialize generator first (no dependencies)
        getOrbGeneratorInstance();

        // Then processor (now has safe lazy initialization)
        getOrbProcessorInstance();

        // Finally handler (depends on processor)
        getOrbHandler();

        // Register the event handler
        if (orbHandler != null) {
            orbHandler.register();
        }

        logger.info("§a[OrbManager] §7Orb system initialized with complete stat replacement logic");
    }

    /**
     * Lazy initialization of OrbHandler
     */
    private OrbHandler getOrbHandler() {
        if (orbHandler == null) {
            orbHandler = new OrbHandler();
        }
        return orbHandler;
    }

    /**
     * Lazy initialization of OrbGenerator
     */
    private OrbGenerator getOrbGeneratorInstance() {
        if (orbGenerator == null) {
            orbGenerator = OrbAPI.getOrbGenerator();
        }
        return orbGenerator;
    }

    /**
     * Lazy initialization of OrbProcessor
     */
    private OrbProcessor getOrbProcessorInstance() {
        if (orbProcessor == null) {
            orbProcessor = new OrbProcessor();
        }
        return orbProcessor;
    }

    /**
     * Creates a normal Orb of Alteration
     *
     * @param showPrice Whether to display the price in the lore
     * @return The created orb
     */
    public ItemStack createNormalOrb(boolean showPrice) {
        return getOrbGeneratorInstance().createNormalOrb(showPrice);
    }

    /**
     * Creates a legendary Orb of Alteration
     *
     * @param showPrice Whether to display the price in the lore
     * @return The created legendary orb
     */
    public ItemStack createLegendaryOrb(boolean showPrice) {
        return getOrbGeneratorInstance().createLegendaryOrb(showPrice);
    }

    /**
     * Gets the price of a normal orb
     *
     * @return The price in gems
     */
    public int getNormalOrbPrice() {
        return getOrbGeneratorInstance().getOrbPrice(false);
    }

    /**
     * Gets the price of a legendary orb
     *
     * @return The price in gems
     */
    public int getLegendaryOrbPrice() {
        return getOrbGeneratorInstance().getOrbPrice(true);
    }

    /**
     * Give orbs to a player
     *
     * @param player          The player to give orbs to
     * @param normalAmount    The amount of normal orbs to give
     * @param legendaryAmount The amount of legendary orbs to give
     */
    public void giveOrbsToPlayer(Player player, int normalAmount, int legendaryAmount) {
        if (player == null) return;

        if (normalAmount > 0) {
            ItemStack normalOrbs = createNormalOrb(false);
            normalOrbs.setAmount(Math.min(normalAmount, 64));
            player.getInventory().addItem(normalOrbs);
        }

        if (legendaryAmount > 0) {
            ItemStack legendaryOrbs = createLegendaryOrb(false);
            legendaryOrbs.setAmount(Math.min(legendaryAmount, 64));
            player.getInventory().addItem(legendaryOrbs);
        }
    }

    /**
     * CORE FIX: Apply an orb to an item with complete stat replacement
     * This method completely rebuilds the item's lore to prevent duplicate stats
     * Now preserves base damage/HP/regen for normal orbs, and enhances them for legendary orbs when upgrading plus levels
     *
     * @param item        The item to apply the orb to
     * @param isLegendary Whether to use a legendary orb
     * @param bonusRolls  Number of bonus rolls for legendary orbs
     * @return The modified item with completely new stats
     */
    public ItemStack applyOrbToItem(ItemStack item, boolean isLegendary, int bonusRolls) {
        if (item == null || !item.hasItemMeta()) {
            logger.warning("§c[OrbManager] Cannot apply orb to null item or item without metadata");
            return item;
        }

        try {
            // Clone the item to avoid modifying the original
            ItemStack result = item.clone();
            ItemMeta meta = result.getItemMeta();

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("§6[OrbManager] §7Applying " + (isLegendary ? "legendary" : "normal") +
                        " orb to: " + (meta.hasDisplayName() ? meta.getDisplayName() : result.getType().name()));
            }

            // Get item information
            int tier = OrbAPI.getItemTier(result);
            int itemType = OrbAPI.getItemType(result);
            boolean isWeapon = OrbAPI.isWeapon(result);

            if (tier <= 0 || itemType == OrbAPI.TYPE_UNKNOWN) {
                logger.warning("§c[OrbManager] Cannot determine item tier (" + tier + ") or type (" + itemType + ") for orb application");
                return item;
            }

            // Extract and preserve special lore (untradeable, protection, etc.)
            List<String> specialLore = OrbAPI.extractSpecialLore(result);

            // Get current plus level
            int currentPlus = getCurrentPlusLevel(result);

            // For legendary orbs, ensure minimum +4
            int newPlus = currentPlus;
            if (isLegendary && currentPlus < LEGENDARY_MINIMUM_PLUS) {
                newPlus = LEGENDARY_MINIMUM_PLUS;
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("§6[OrbManager] §7Legendary orb upgrading item from +" + currentPlus + " to +" + newPlus);
                }
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("§6[OrbManager] §7" + (isLegendary ? "Legendary" : "Normal") + " orb preserving plus level: +" + currentPlus);
                }
            }

            // Generate completely new stats
            OrbGenerator.StatResult stats = getOrbGeneratorInstance().generateStats(tier, isWeapon, isLegendary, bonusRolls);

            // CORE FIX: Completely rebuild the item with new stats (preserving base damage/HP/regen for normal orbs, enhancing for legendary upgrades)
            ItemStack rebuiltItem = completelyRebuildItem(result, tier, itemType, stats, newPlus, currentPlus, specialLore, isLegendary);

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("§a[OrbManager] §7Successfully rebuilt item with new stats (tier " + tier + ", type " + itemType + ")");
            }

            return rebuiltItem;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "§c[OrbManager] Error applying orb to item", e);
            return item; // Return original item if anything goes wrong
        }
    }

    /**
     * CORE METHOD: Completely rebuilds an item with new stats, preventing all duplicates
     * Now preserves base damage/HP/regen for normal orbs, and applies enhancement bonuses for legendary orbs when upgrading plus levels
     */
    private ItemStack completelyRebuildItem(ItemStack item, int tier, int itemType, OrbGenerator.StatResult stats,
                                            int newPlusLevel, int currentPlusLevel, List<String> specialLore, boolean isLegendary) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }

            // Generate new base item name if needed
            String newName = generateItemName(item, tier, itemType, stats, newPlusLevel);
            meta.setDisplayName(newName);

            // Build completely new lore from scratch
            List<String> newLore = new ArrayList<>();

            if (OrbAPI.isWeapon(item)) {
                buildWeaponLore(newLore, item, tier, itemType, stats, newPlusLevel, currentPlusLevel);
            } else if (OrbAPI.isArmor(item)) {
                buildArmorLore(newLore, item, tier, itemType, stats, newPlusLevel, currentPlusLevel);
            }

            // Add preserved special lore at the end
            if (!specialLore.isEmpty()) {
                newLore.addAll(specialLore);
            }

            // Apply the new lore
            meta.setLore(newLore);
            item.setItemMeta(meta);

            // Apply enhancement effects if needed
            if (newPlusLevel >= 4) {
                Enchants.addGlow(item);
            }

            // Reset item durability
            item.setDurability((short) 0);

            return item;

        } catch (Exception e) {
            logger.log(Level.WARNING, "§c[OrbManager] Error rebuilding item", e);
            return item;
        }
    }

    /**
     * Build weapon lore with  base damage (for plus upgrades) and new secondary stats
     * Applies enhancement bonuses when plus level increases, preserves base otherwise
     */
    private void buildWeaponLore(List<String> lore, ItemStack item, int tier, int itemType, OrbGenerator.StatResult stats, int newPlusLevel, int currentPlusLevel) {
        try {
            // Get original base damage (more conservative approach)
            int[] originalDamage = getOriginalBaseDamage(item, tier, newPlusLevel, currentPlusLevel);
            int minDmg = originalDamage[0];
            int maxDmg = originalDamage[1];

            logger.fine("§6[OrbManager] §7Original damage extracted: " + minDmg + "-" + maxDmg +
                    " (plus: " + currentPlusLevel + " -> " + newPlusLevel + ")");

            // Apply enhancement bonuses ONLY if plus level increased
            if (newPlusLevel > currentPlusLevel) {
                // Apply enhancement calculation for the new plus level
                double enhancementMultiplier = calculateEnhancementMultiplier(newPlusLevel);
                int MinDmg = (int) Math.round(minDmg * enhancementMultiplier);
                int MaxDmg = (int) Math.round(maxDmg * enhancementMultiplier);

                logger.fine("§6[OrbManager] §7Applied +"+newPlusLevel+" enhancement (x" + enhancementMultiplier + "): " +
                        minDmg + "-" + maxDmg + " -> " + MinDmg + "-" + MaxDmg);

                minDmg = MinDmg;
                maxDmg = MaxDmg;
            } else {
                logger.fine("§6[OrbManager] §7Preserving original damage: " + minDmg + "-" + maxDmg);
            }

            // Add damage line ( or preserved)
            lore.add(ChatColor.RED + "DMG: " + minDmg + " - " + maxDmg);

            // Add ONLY secondary stats - NO base damage modifications

            // Add Pure DMG for axes only (secondary stat)
            if (itemType == OrbAPI.TYPE_AXE && stats.pureActive) {
                lore.add(ChatColor.RED + "PURE DMG: +" + stats.pureAmount);
            }

            // Add accuracy (secondary stat)
            if (stats.accuracyActive) {
                lore.add(ChatColor.RED + "ACCURACY: " + stats.accuracyAmount + "%");
            }

            // Add VS Monsters (secondary stat)
            if (stats.vsMonstersActive) {
                lore.add(ChatColor.RED + "VS MONSTERS: " + stats.vsMonstersAmount + "%");
            }

            // Add VS Players (secondary stat)
            if (stats.vsPlayersActive) {
                lore.add(ChatColor.RED + "VS PLAYERS: " + stats.vsPlayersAmount + "%");
            }

            // Add Life Steal (secondary stat)
            if (stats.lifeStealActive) {
                lore.add(ChatColor.RED + "LIFE STEAL: " + stats.lifeStealAmount + "%");
            }

            // Add Critical Hit (secondary stat)
            if (stats.criticalHitActive) {
                lore.add(ChatColor.RED + "CRITICAL HIT: " + stats.criticalHitAmount + "%");
            }

            // Add Elemental Damage (secondary stat)
            if (stats.elementType > 0) {
                String elementName;
                switch (stats.elementType) {
                    case OrbAPI.ELEM_FIRE:
                        elementName = "FIRE DMG";
                        break;
                    case OrbAPI.ELEM_POISON:
                        elementName = "POISON DMG";
                        break;
                    case OrbAPI.ELEM_ICE:
                        elementName = "ICE DMG";
                        break;
                    default:
                        elementName = "ELEMENT DMG";
                        break;
                }
                lore.add(ChatColor.RED + elementName + ": +" + stats.elementAmount);
            }

            logger.fine("§6[OrbManager] §7Built weapon lore with " +
                    (newPlusLevel > currentPlusLevel ? "" : "preserved") +
                    " damage: " + minDmg + "-" + maxDmg);

        } catch (Exception e) {
            logger.log(Level.WARNING, "§c[OrbManager] Error building weapon lore", e);
        }
    }

    /**
     * Build armor lore with  base HP/regen (for plus upgrades) and new secondary stats
     * Applies enhancement bonuses when plus level increases, preserves base otherwise
     */
    private void buildArmorLore(List<String> lore, ItemStack item, int tier, int itemType, OrbGenerator.StatResult stats, int newPlusLevel, int currentPlusLevel) {
        try {
            // Get original base HP (more conservative approach)
            int originalHP = getOriginalBaseHP(item, tier, itemType, newPlusLevel, currentPlusLevel);

            logger.fine("§6[OrbManager] §7Original HP extracted: " + originalHP +
                    " (plus: " + currentPlusLevel + " -> " + newPlusLevel + ")");

            // Apply enhancement bonuses ONLY if plus level increased
            if (newPlusLevel > currentPlusLevel) {
                // Apply enhancement calculation for the new plus level
                double enhancementMultiplier = calculateEnhancementMultiplier(newPlusLevel);
                int HP = (int) Math.round(originalHP * enhancementMultiplier);

                logger.fine("§6[OrbManager] §7Applied +"+newPlusLevel+" HP enhancement (x" + enhancementMultiplier + "): " +
                        originalHP + " -> " + HP);

                originalHP = HP;
            } else {
                logger.fine("§6[OrbManager] §7Preserving original HP: " + originalHP);
            }

            // Add HP line ( or preserved)
            lore.add(ChatColor.RED + "HP: +" + originalHP);

            // Handle regen stats with enhancement bonuses
            boolean hasOriginalHPRegen = OrbAPI.hasHPRegen(item);
            boolean hasOriginalEnergyRegen = OrbAPI.hasEnergyRegen(item);

            if (hasOriginalHPRegen) {
                // Get and enhance original HP regen
                int originalHPRegen = getOriginalHPRegen(item, tier, newPlusLevel, currentPlusLevel);
                if (newPlusLevel > currentPlusLevel) {
                    double enhancementMultiplier = calculateEnhancementMultiplier(newPlusLevel);
                    originalHPRegen = (int) Math.round(originalHPRegen * enhancementMultiplier);
                }
                lore.add(ChatColor.RED + "HP REGEN: +" + originalHPRegen + "/s");
            } else if (hasOriginalEnergyRegen) {
                // Get and enhance original energy regen
                int originalEnergyRegen = getOriginalEnergyRegen(item, tier, newPlusLevel, currentPlusLevel);
                if (newPlusLevel > currentPlusLevel) {
                    double enhancementMultiplier = calculateEnhancementMultiplier(newPlusLevel);
                    originalEnergyRegen = (int) Math.round(originalEnergyRegen * enhancementMultiplier);
                }
                lore.add(ChatColor.RED + "ENERGY REGEN: +" + originalEnergyRegen + "%");
            } else {
                // If no original regen, add tier-appropriate default with enhancement
                int baseEnergyRegen = Math.max(1, tier);
                if (newPlusLevel > currentPlusLevel) {
                    double enhancementMultiplier = calculateEnhancementMultiplier(newPlusLevel);
                    baseEnergyRegen = (int) Math.round(baseEnergyRegen * enhancementMultiplier);
                }
                lore.add(ChatColor.RED + "ENERGY REGEN: +" + baseEnergyRegen + "%");
            }

            // Add ONLY secondary stats - NO base HP or regen modifications

            // Add Intelligence (secondary stat)
            if (stats.intelligenceActive) {
                lore.add(ChatColor.RED + "INT: +" + stats.intelligenceAmount);
            }

            // Add Strength (secondary stat)
            if (stats.strengthActive) {
                lore.add(ChatColor.RED + "STR: +" + stats.strengthAmount);
            }

            // Add Vitality (secondary stat)
            if (stats.vitalityActive) {
                lore.add(ChatColor.RED + "VIT: +" + stats.vitalityAmount);
            }

            // Add Dexterity (secondary stat)
            if (stats.dexterityActive) {
                lore.add(ChatColor.RED + "DEX: +" + stats.dexterityAmount);
            }

            // Add Dodge (secondary stat)
            if (stats.dodgeActive) {
                lore.add(ChatColor.RED + "DODGE: " + stats.dodgeAmount + "%");
            }

            // Add Thorns (secondary stat)
            if (stats.thornsActive) {
                lore.add(ChatColor.RED + "THORNS: " + stats.thornsAmount + "%");
            }

            // Add Block (secondary stat)
            if (stats.blockActive) {
                lore.add(ChatColor.RED + "BLOCK: " + stats.blockAmount + "%");
            }

            logger.fine("§6[OrbManager] §7Built armor lore with " +
                    (newPlusLevel > currentPlusLevel ? "" : "preserved") +
                    " HP: " + originalHP);

        } catch (Exception e) {
            logger.log(Level.WARNING, "§c[OrbManager] Error building armor lore", e);
        }
    }

    /**
     * Generate a new item name based on stats and tier
     */
    private String generateItemName(ItemStack item, int tier, int itemType, OrbGenerator.StatResult stats, int plusLevel) {
        try {
            // Get base name or generate one
            String baseName = OrbAPI.getBaseItemName(itemType, tier);

            // Add prefixes based on stats
            if (OrbAPI.isWeapon(item)) {
                // Weapon prefixes
                if (itemType == OrbAPI.TYPE_AXE && stats.pureActive) {
                    baseName = "Pure " + baseName;
                } else if (stats.accuracyActive) {
                    baseName = "Accurate " + baseName;
                } else if (stats.lifeStealActive) {
                    baseName = "Vampyric " + baseName;
                } else if (stats.criticalHitActive) {
                    baseName = "Deadly " + baseName;
                }

                // Add elemental suffixes
                if (stats.elementType == OrbAPI.ELEM_FIRE) {
                    baseName = baseName + " of Fire";
                } else if (stats.elementType == OrbAPI.ELEM_POISON) {
                    baseName = baseName + " of Poison";
                } else if (stats.elementType == OrbAPI.ELEM_ICE) {
                    baseName = baseName + " of Ice";
                }
            } else {
                // Armor prefixes
                if (stats.dodgeActive) {
                    baseName = "Agile " + baseName;
                } else if (stats.thornsActive) {
                    baseName = "Thorny " + baseName;
                } else if (stats.blockActive) {
                    baseName = "Protective " + baseName;
                }

                // Add suffixes for armor
                if (stats.vitalityActive && stats.strengthActive) {
                    baseName = baseName + " of Power";
                } else if (stats.intelligenceActive) {
                    baseName = baseName + " of Wisdom";
                } else if (stats.dexterityActive) {
                    baseName = baseName + " of Agility";
                }
            }

            // Apply tier coloring
            String coloredName = OrbAPI.applyColorByTier(baseName, tier);

            // Add plus level if applicable
            if (plusLevel > 0) {
                coloredName = ChatColor.RED + "[+" + plusLevel + "] " + coloredName;
            }

            return coloredName;

        } catch (Exception e) {
            logger.log(Level.WARNING, "§c[OrbManager] Error generating item name", e);
            return item.getItemMeta().hasDisplayName() ?
                    item.getItemMeta().getDisplayName() :
                    OrbAPI.applyColorByTier(item.getType().name(), tier);
        }
    }

    /**
     * Get current plus level from item name
     */
    private int getCurrentPlusLevel(ItemStack item) {
        try {
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
                return 0;
            }

            String displayName = item.getItemMeta().getDisplayName();
            Matcher matcher = PLUS_PATTERN.matcher(displayName);

            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }

            return 0;
        } catch (Exception e) {
            logger.log(Level.WARNING, "§c[OrbManager] Error getting plus level", e);
            return 0;
        }
    }

    /**
     * Get base damage for tier (for items without existing damage)
     */
    private int getBaseDamageForTier(int tier, boolean isMin) {
        int baseDamage;
        switch (tier) {
            case 1:
                baseDamage = isMin ? 8 : 12;
                break;
            case 2:
                baseDamage = isMin ? 18 : 28;
                break;
            case 3:
                baseDamage = isMin ? 45 : 65;
                break;
            case 4:
                baseDamage = isMin ? 105 : 155;
                break;
            case 5:
                baseDamage = isMin ? 250 : 370;
                break;
            case 6:
                baseDamage = isMin ? 600 : 890;
                break;
            default:
                baseDamage = isMin ? 8 : 12;
                break;
        }
        return baseDamage;
    }

    /**
     * Get base HP for tier (for items without existing HP)
     */
    private int getBaseHPForTier(int tier, int itemType) {
        int baseHP;
        switch (tier) {
            case 1:
                baseHP = 120;
                break;
            case 2:
                baseHP = 350;
                break;
            case 3:
                baseHP = 750;
                break;
            case 4:
                baseHP = 1500;
                break;
            case 5:
                baseHP = 3500;
                break;
            case 6:
                baseHP = 6500;
                break;
            default:
                baseHP = 120;
                break;
        }

        // Adjust for armor piece type
        if (itemType == OrbAPI.TYPE_HELMET || itemType == OrbAPI.TYPE_BOOTS) {
            baseHP = (int) (baseHP * 0.7); // Helmets and boots get less HP
        } else if (itemType == OrbAPI.TYPE_CHESTPLATE || itemType == OrbAPI.TYPE_LEGGINGS) {
            baseHP = (int) (baseHP * 1.2); // Chestplates and leggings get more HP
        }

        return baseHP;
    }

    /**
     * Calculate the enhancement multiplier for a given plus level
     * Now matches your actual enhancement system (5% compounding per level)
     */
    private double calculateEnhancementMultiplier(int plusLevel) {
        if (plusLevel <= 0) {
            return 1.0; // No enhancement for +0 or negative
        }

        // Your enhancement system: 5% increase per level, compounding
        // +1 = 1.05x, +2 = 1.1025x, +3 = 1.157625x, +4 = 1.21550625x, etc.
        return Math.pow(1.05, plusLevel);
    }

    /**
     * Get original base damage without any enhancement bonuses
     * More conservative approach - only reverse-engineer when actually upgrading plus levels
     */
    private int[] getOriginalBaseDamage(ItemStack item, int tier, int newPlusLevel, int currentPlusLevel) {
        int[] currentDamage = OrbAPI.getDamageRange(item);

        // If we're not upgrading plus levels, just return current damage as-is (CONSERVATIVE)
        if (newPlusLevel <= currentPlusLevel) {
            if (currentDamage[0] > 0 && currentDamage[1] > 0) {
                logger.fine("§6[OrbManager] §7Conservative mode: preserving exact damage " +
                        currentDamage[0] + "-" + currentDamage[1]);
                return currentDamage;
            }
        }

        // Only reverse-engineer if we're actually upgrading plus levels
        if (newPlusLevel > currentPlusLevel && currentDamage[0] > 0 && currentDamage[1] > 0) {
            if (currentPlusLevel > 0) {
                // Reverse the current enhancement to get base damage
                double currentMultiplier = calculateEnhancementMultiplier(currentPlusLevel);
                int baseMin = (int) Math.round(currentDamage[0] / currentMultiplier);
                int baseMax = (int) Math.round(currentDamage[1] / currentMultiplier);
                return new int[]{baseMin, baseMax};
            } else {
                // Already base damage
                return currentDamage;
            }
        }

        // Fallback to tier-appropriate base damage only if no current damage exists
        if (currentDamage[0] <= 0 || currentDamage[1] <= 0) {
            return new int[]{getBaseDamageForTier(tier, true), getBaseDamageForTier(tier, false)};
        }

        return currentDamage;
    }

    /**
     * Get original base HP without any enhancement bonuses
     * More conservative approach - only reverse-engineer when actually upgrading plus levels
     */
    private int getOriginalBaseHP(ItemStack item, int tier, int itemType, int newPlusLevel, int currentPlusLevel) {
        int currentHP = OrbAPI.getHP(item);

        // If we're not upgrading plus levels, just return current HP as-is (CONSERVATIVE)
        if (newPlusLevel <= currentPlusLevel) {
            if (currentHP > 0) {
                logger.fine("§6[OrbManager] §7Conservative mode: preserving exact HP " + currentHP);
                return currentHP;
            }
        }

        // Only reverse-engineer if we're actually upgrading plus levels
        if (newPlusLevel > currentPlusLevel && currentHP > 0) {
            if (currentPlusLevel > 0) {
                // Reverse the current enhancement to get base HP
                double currentMultiplier = calculateEnhancementMultiplier(currentPlusLevel);
                return (int) Math.round(currentHP / currentMultiplier);
            } else {
                // Already base HP
                return currentHP;
            }
        }

        // Fallback to tier-appropriate base HP only if no current HP exists
        if (currentHP <= 0) {
            return getBaseHPForTier(tier, itemType);
        }

        return currentHP;
    }

    /**
     * Get original base HP regen without any enhancement bonuses
     * More conservative approach - only reverse-engineer when actually upgrading plus levels
     */
    private int getOriginalHPRegen(ItemStack item, int tier, int newPlusLevel, int currentPlusLevel) {
        int currentHPRegen = OrbAPI.getHPRegen(item);

        // If we're not upgrading plus levels, just return current HP regen as-is
        if (newPlusLevel <= currentPlusLevel) {
            if (currentHPRegen > 0) {
                return currentHPRegen;
            }
        }

        // Only reverse-engineer if we're actually upgrading plus levels
        if (newPlusLevel > currentPlusLevel && currentHPRegen > 0) {
            if (currentPlusLevel > 0) {
                // Reverse the current enhancement to get base HP regen
                double currentMultiplier = calculateEnhancementMultiplier(currentPlusLevel);
                return (int) Math.round(currentHPRegen / currentMultiplier);
            } else {
                // Already base HP regen
                return currentHPRegen;
            }
        }

        // Fallback to tier-appropriate base HP regen
        return Math.max(1, tier);
    }

    /**
     * Get original base energy regen without any enhancement bonuses
     * More conservative approach - only reverse-engineer when actually upgrading plus levels
     */
    private int getOriginalEnergyRegen(ItemStack item, int tier, int newPlusLevel, int currentPlusLevel) {
        int currentEnergyRegen = OrbAPI.getEnergyRegen(item);

        // If we're not upgrading plus levels, just return current energy regen as-is
        if (newPlusLevel <= currentPlusLevel) {
            if (currentEnergyRegen > 0) {
                return currentEnergyRegen;
            }
        }

        // Only reverse-engineer if we're actually upgrading plus levels
        if (newPlusLevel > currentPlusLevel && currentEnergyRegen > 0) {
            if (currentPlusLevel > 0) {
                // Reverse the current enhancement to get base energy regen
                double currentMultiplier = calculateEnhancementMultiplier(currentPlusLevel);
                return (int) Math.round(currentEnergyRegen / currentMultiplier);
            } else {
                // Already base energy regen
                return currentEnergyRegen;
            }
        }

        // Fallback to tier-appropriate base energy regen
        return Math.max(1, tier);
    }

    /**
     * Checks if an item is valid for orb application
     *
     * @param item The item to check
     * @return true if the item can be modified by orbs
     */
    public boolean isValidItemForOrb(ItemStack item) {
        return OrbAPI.isValidItemForOrb(item);
    }

    /**
     * Check if an item is a normal orb
     *
     * @param item The item to check
     * @return true if the item is a normal orb
     */
    public boolean isNormalOrb(ItemStack item) {
        return OrbAPI.isNormalOrb(item);
    }

    /**
     * Check if an item is a legendary orb
     *
     * @param item The item to check
     * @return true if the item is a legendary orb
     */
    public boolean isLegendaryOrb(ItemStack item) {
        return OrbAPI.isLegendaryOrb(item);
    }

    /**
     * Creates a description of orbs for a help command
     *
     * @return A list of strings describing orbs
     */
    public List<String> getOrbHelpDescription() {
        List<String> help = new ArrayList<>();
        help.add(ChatColor.YELLOW + "=== Orbs of Alteration ===");
        help.add(ChatColor.WHITE + "Orbs reroll secondary stats and can enhance base stats.");
        help.add(ChatColor.WHITE + "Normal orbs: Generate new secondary stats with normal quality.");
        help.add(ChatColor.WHITE + "Legendary orbs: Generate better secondary stats and ensure items are at least +4.");
        help.add(ChatColor.YELLOW + "To use: Hold an orb over the item you want to modify and click.");
        help.add(ChatColor.GREEN + "Note: Legendary orbs enhance damage/HP/regen when upgrading plus levels!");
        return help;
    }

    /**
     * Opens the orb shop GUI for a player
     * This would be implemented when adding a shop GUI
     *
     * @param player The player to open the shop for
     */
    public void openOrbShopGUI(Player player) {
        // This would create and open an orb shop GUI
        // Left as a placeholder for future implementation
        player.sendMessage(ChatColor.YELLOW + "The orb shop is not yet implemented.");
    }

    /**
     * Send orb usage instructions to a player
     *
     * @param player The player to send instructions to
     */
    public void sendUsageInstructions(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== How to Use Orbs ===");
        player.sendMessage(ChatColor.WHITE + "1. Pick up the orb with your cursor");
        player.sendMessage(ChatColor.WHITE + "2. Click on the item you want to modify");
        player.sendMessage(ChatColor.WHITE + "3. Normal orbs reroll secondary stats");
        player.sendMessage(ChatColor.WHITE + "4. Legendary orbs guarantee better stats and at least +4");
        player.sendMessage(ChatColor.GREEN + "Note: Legendary orbs enhance damage/HP/regen when upgrading plus levels!");
    }

    /**
     * Get the OrbProcessor instance
     *
     * @return The OrbProcessor
     */
    public OrbProcessor getOrbProcessor() {
        return getOrbProcessorInstance();
    }

    /**
     * Get the OrbGenerator instance
     *
     * @return The OrbGenerator
     */
    public OrbGenerator getOrbGenerator() {
        return getOrbGeneratorInstance();
    }

    /**
     * Debug method to test orb application
     */
    public void debugOrbApplication(Player player, ItemStack testItem, boolean isLegendary) {
        if (player == null || testItem == null) {
            return;
        }

        try {
            ItemStack originalItem = testItem.clone();
            ItemStack modifiedItem = applyOrbToItem(testItem, isLegendary, 0);

            player.sendMessage(ChatColor.GOLD + "=== Orb Debug Results ===");
            player.sendMessage(ChatColor.YELLOW + "Original: " +
                    (originalItem.getItemMeta().hasDisplayName() ?
                            originalItem.getItemMeta().getDisplayName() :
                            originalItem.getType().name()));
            player.sendMessage(ChatColor.YELLOW + "Modified: " +
                    (modifiedItem.getItemMeta().hasDisplayName() ?
                            modifiedItem.getItemMeta().getDisplayName() :
                            modifiedItem.getType().name()));
            player.sendMessage(ChatColor.YELLOW + "Orb Type: " + (isLegendary ? "Legendary" : "Normal"));
            player.sendMessage(ChatColor.YELLOW + "Tier: " + OrbAPI.getItemTier(testItem));
            player.sendMessage(ChatColor.YELLOW + "Type: " + OrbAPI.getItemType(testItem));
            player.sendMessage(ChatColor.GREEN + "Enhancement: " + (isLegendary ? "Applies +4 bonuses if upgrading plus level" : "Preserves existing base stats"));
            player.sendMessage(ChatColor.GOLD + "=======================");

            // Give the modified item to the player
            player.getInventory().addItem(modifiedItem);

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error during orb debug: " + e.getMessage());
            logger.log(Level.SEVERE, "Error in orb debug", e);
        }
    }
}