package com.rednetty.server.mechanics.item.orb;


import com.rednetty.server.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates Orbs of Alteration for altering item stats
 */
public class OrbGenerator {
    private static final Logger LOGGER = Logger.getLogger(OrbGenerator.class.getName());
    private static final Random random = new Random();

    // Orb names
    private static final String NORMAL_ORB_NAME = ChatColor.LIGHT_PURPLE + "Orb of Alteration";
    private static final String LEGENDARY_ORB_NAME = ChatColor.YELLOW + "Legendary Orb of Alteration";

    /**
     * Creates a normal Orb of Alteration
     *
     * @param showPrice Whether to display the price in the lore
     * @return The created orb
     */
    public ItemStack createNormalOrb(boolean showPrice) {
        ItemStack orb = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta meta = orb.getItemMeta();

        meta.setDisplayName(NORMAL_ORB_NAME);

        ArrayList<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Randomizes stats of selected equipment.");

        if (showPrice) {
            lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + "500g");
        }

        meta.setLore(lore);
        orb.setItemMeta(meta);

        // Add NBT data to identify as orb
        NBTAccessor nbt = new NBTAccessor(orb);
        nbt.setString("orbType", "normal");

        LOGGER.info("Created normal orb with NBT data: " + nbt.getString("orbType"));
        return nbt.update();
    }

    /**
     * Creates a legendary Orb of Alteration
     *
     * @param showPrice Whether to display the price in the lore
     * @return The created legendary orb
     */
    public ItemStack createLegendaryOrb(boolean showPrice) {
        ItemStack orb = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta meta = orb.getItemMeta();

        meta.setDisplayName(LEGENDARY_ORB_NAME);

        ArrayList<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Plus 4s Items that have a plus lower than 4.");
        lore.add(ChatColor.GRAY + "It also has a extremely high chance of good orbs.");

        if (showPrice) {
            lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + "32000g");
        }

        meta.setLore(lore);
        orb.setItemMeta(meta);

        // Add glowing effect
        Enchants.addGlow(orb);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        orb.setItemMeta(meta);

        // Add NBT data to identify as legendary orb
        NBTAccessor nbt = new NBTAccessor(orb);
        nbt.setString("orbType", "legendary");

        LOGGER.info("Created legendary orb with NBT data: " + nbt.getString("orbType"));
        return nbt.update();
    }

    /**
     * Gets the price of an orb
     *
     * @param isLegendary True if the orb is legendary
     * @return The price in gems
     */
    public int getOrbPrice(boolean isLegendary) {
        return isLegendary ? 32000 : 500;
    }

    /**
     * Generate stats for an item based on its tier and type
     *
     * @param tier        The tier of the item (1-6)
     * @param isWeapon    True if the item is a weapon
     * @param isLegendary True if legendary stats should be generated
     * @param bonusRolls  Number of bonus rolls for legendary orbs
     * @return A StatResult object containing the generated stat values
     */
    public StatResult generateStats(int tier, boolean isWeapon, boolean isLegendary, int bonusRolls) {
        try {
            StatResult result = new StatResult();

            // Randomly determine which stats are active
            result.elementType = random.nextInt(3) + 1;
            result.pureActive = randomChance(30);
            result.lifeStealActive = randomChance(20);
            result.vsMonstersActive = randomChance(13);
            result.vsPlayersActive = randomChance(13);
            result.criticalHitActive = randomChance(30);
            result.accuracyActive = randomChance(25);
            result.dodgeActive = randomChance(30);
            result.blockActive = randomChance(30);
            result.vitalityActive = randomChance(30);
            result.strengthActive = randomChance(30);
            result.intelligenceActive = randomChance(30);
            result.dexterityActive = randomChance(30);
            result.thornsActive = randomChance(30);

            // Generate base stat values based on tier
            generateStatsForTier(result, tier);

            // Apply legendary bonuses if applicable
            if (isLegendary) {
                applyLegendaryBonuses(result, tier, bonusRolls);
            }

            // Apply weapon-specific adjustments
            if (isWeapon) {
                adjustWeaponStats(result);
            } else {
                adjustArmorStats(result);
            }

            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating stats", e);
            return new StatResult(); // Return empty result on error
        }
    }

    /**
     * Randomly determine if a stat should be active based on probability
     *
     * @param percentChance The percent chance (1-100) of the stat being active
     * @return True if the stat is active
     */
    private boolean randomChance(int percentChance) {
        return random.nextInt(100) < percentChance;
    }

    /**
     * Generate base stat values for a given tier
     *
     * @param result The StatResult to populate
     * @param tier   The item tier (1-6)
     */
    private void generateStatsForTier(StatResult result, int tier) {
        switch (tier) {
            case 1:
                result.dodgeAmount = random.nextInt(5) + 1;
                result.blockAmount = random.nextInt(5) + 1;
                result.vitalityAmount = random.nextInt(15) + 1;
                result.strengthAmount = random.nextInt(15) + 1;
                result.intelligenceAmount = random.nextInt(15) + 1;
                result.dexterityAmount = random.nextInt(15) + 1;
                result.elementAmount = random.nextInt(4) + 1;
                result.pureAmount = random.nextInt(4) + 1;
                result.lifeStealAmount = random.nextInt(30) + 1;
                result.criticalHitAmount = random.nextInt(3) + 1;
                result.accuracyAmount = random.nextInt(10) + 1;
                result.thornsAmount = random.nextInt(2) + 1;
                result.vsMonstersAmount = random.nextInt(4) + 1;
                result.vsPlayersAmount = random.nextInt(4) + 1;
                break;
            case 2:
                result.dodgeAmount = random.nextInt(8) + 1;
                result.blockAmount = random.nextInt(8) + 1;
                result.vitalityAmount = random.nextInt(35) + 1;
                result.dexterityAmount = random.nextInt(35) + 1;
                result.strengthAmount = random.nextInt(35) + 1;
                result.intelligenceAmount = random.nextInt(35) + 1;
                result.elementAmount = random.nextInt(9) + 1;
                result.pureAmount = random.nextInt(9) + 1;
                result.lifeStealAmount = random.nextInt(15) + 1;
                result.criticalHitAmount = random.nextInt(6) + 1;
                result.accuracyAmount = random.nextInt(12) + 1;
                result.thornsAmount = random.nextInt(3) + 1;
                result.vsMonstersAmount = random.nextInt(5) + 1;
                result.vsPlayersAmount = random.nextInt(4) + 1;
                break;
            case 3:
                result.dodgeAmount = random.nextInt(10) + 1;
                result.blockAmount = random.nextInt(10) + 1;
                result.vitalityAmount = random.nextInt(75) + 1;
                result.dexterityAmount = random.nextInt(75) + 1;
                result.strengthAmount = random.nextInt(75) + 1;
                result.intelligenceAmount = random.nextInt(75) + 1;
                result.elementAmount = random.nextInt(15) + 1;
                result.pureAmount = random.nextInt(15) + 1;
                result.lifeStealAmount = random.nextInt(12) + 1;
                result.criticalHitAmount = random.nextInt(8) + 1;
                result.accuracyAmount = random.nextInt(25) + 1;
                result.thornsAmount = random.nextInt(4) + 1;
                result.vsMonstersAmount = random.nextInt(8) + 1;
                result.vsPlayersAmount = random.nextInt(7) + 1;
                break;
            case 4:
                result.dodgeAmount = random.nextInt(12) + 1;
                result.blockAmount = random.nextInt(12) + 1;
                result.vitalityAmount = random.nextInt(115) + 1;
                result.dexterityAmount = random.nextInt(115) + 1;
                result.strengthAmount = random.nextInt(115) + 1;
                result.intelligenceAmount = random.nextInt(115) + 1;
                result.elementAmount = random.nextInt(25) + 1;
                result.pureAmount = random.nextInt(25) + 1;
                result.lifeStealAmount = random.nextInt(10) + 1;
                result.criticalHitAmount = random.nextInt(10) + 1;
                result.accuracyAmount = random.nextInt(28) + 1;
                result.thornsAmount = random.nextInt(5) + 1;
                result.vsMonstersAmount = random.nextInt(10) + 1;
                result.vsPlayersAmount = random.nextInt(9) + 1;
                break;
            case 5:
                result.dodgeAmount = random.nextInt(12) + 1;
                result.blockAmount = random.nextInt(12) + 1;
                result.vitalityAmount = random.nextInt(150) + 100;
                result.dexterityAmount = random.nextInt(150) + 100;
                result.strengthAmount = random.nextInt(150) + 100;
                result.intelligenceAmount = random.nextInt(150) + 100;
                result.elementAmount = random.nextInt(20) + 25;
                result.pureAmount = random.nextInt(20) + 25;
                result.lifeStealAmount = random.nextInt(4) + 4;
                result.criticalHitAmount = random.nextInt(5) + 6;
                result.accuracyAmount = random.nextInt(10) + 25;
                result.thornsAmount = random.nextInt(3) + 2;
                result.vsMonstersAmount = random.nextInt(12) + 1;
                result.vsPlayersAmount = random.nextInt(12) + 1;
                break;
            case 6:
                result.dodgeAmount = random.nextInt(13) + 1;
                result.blockAmount = random.nextInt(13) + 1;
                result.vitalityAmount = random.nextInt(250) + 100;
                result.dexterityAmount = random.nextInt(250) + 100;
                result.strengthAmount = random.nextInt(250) + 100;
                result.intelligenceAmount = random.nextInt(250) + 100;
                result.elementAmount = random.nextInt(30) + 40;
                result.pureAmount = random.nextInt(30) + 40;
                result.lifeStealAmount = random.nextInt(4) + 4;
                result.criticalHitAmount = random.nextInt(5) + 6;
                result.accuracyAmount = random.nextInt(20) + 20;
                result.thornsAmount = random.nextInt(2) + 3;
                result.vsMonstersAmount = random.nextInt(6) + 6;
                result.vsPlayersAmount = random.nextInt(6) + 6;
                break;
            default:
                // Default to tier 1 stats if the tier is invalid
                LOGGER.warning("Invalid tier provided: " + tier + ", defaulting to tier 1 stats");
                generateStatsForTier(result, 1);
                break;
        }
    }

    /**
     * Apply legendary bonuses to stats
     *
     * @param result     The StatResult to enhance
     * @param tier       The item tier
     * @param bonusRolls Number of bonus rolls
     */
    private void applyLegendaryBonuses(StatResult result, int tier, int bonusRolls) {
        // Guarantee some beneficial stats
        result.lifeStealActive = true;
        result.criticalHitActive = true;

        // Ensure at least one defensive stat for armor
        boolean hasDefensiveStat = result.dodgeActive || result.blockActive || result.thornsActive;
        if (!hasDefensiveStat) {
            // Randomly pick one defensive stat to enable
            int defensiveStat = random.nextInt(3);
            switch (defensiveStat) {
                case 0:
                    result.dodgeActive = true;
                    break;
                case 1:
                    result.blockActive = true;
                    break;
                case 2:
                    result.thornsActive = true;
                    break;
            }
        }

        // Apply additional bonus rolls
        for (int i = 0; i < bonusRolls; i++) {
            // Create a temporary result to compare with
            StatResult tempResult = new StatResult();
            generateStatsForTier(tempResult, tier);

            // Keep the highest values
            result.dodgeAmount = Math.max(result.dodgeAmount, tempResult.dodgeAmount);
            result.blockAmount = Math.max(result.blockAmount, tempResult.blockAmount);
            result.vitalityAmount = Math.max(result.vitalityAmount, tempResult.vitalityAmount);
            result.dexterityAmount = Math.max(result.dexterityAmount, tempResult.dexterityAmount);
            result.strengthAmount = Math.max(result.strengthAmount, tempResult.strengthAmount);
            result.intelligenceAmount = Math.max(result.intelligenceAmount, tempResult.intelligenceAmount);
            result.elementAmount = Math.max(result.elementAmount, tempResult.elementAmount);
            result.pureAmount = Math.max(result.pureAmount, tempResult.pureAmount);
            result.lifeStealAmount = Math.max(result.lifeStealAmount, tempResult.lifeStealAmount);
            result.criticalHitAmount = Math.max(result.criticalHitAmount, tempResult.criticalHitAmount);
            result.accuracyAmount = Math.max(result.accuracyAmount, tempResult.accuracyAmount);
            result.thornsAmount = Math.max(result.thornsAmount, tempResult.thornsAmount);
            result.vsMonstersAmount = Math.max(result.vsMonstersAmount, tempResult.vsMonstersAmount);
            result.vsPlayersAmount = Math.max(result.vsPlayersAmount, tempResult.vsPlayersAmount);
        }

        // Boost stats additionally for legendary items
        result.dodgeAmount += 2;
        result.blockAmount += 2;
        result.vitalityAmount += 15;
        result.dexterityAmount += 15;
        result.strengthAmount += 15;
        result.intelligenceAmount += 15;
        result.elementAmount += 5;
        result.pureAmount += 5;
        result.lifeStealAmount += 1;
        result.criticalHitAmount += 2;
        result.accuracyAmount += 3;
        result.thornsAmount += 1;
        result.vsMonstersAmount += 2;
        result.vsPlayersAmount += 2;
    }

    /**
     * Apply weapon-specific adjustments to stats
     *
     * @param result The StatResult to adjust
     */
    private void adjustWeaponStats(StatResult result) {
        // Cap accuracy for weapon balance
        if (result.accuracyActive && result.accuracyAmount > 15) {
            result.accuracyAmount = 15 + random.nextInt(6); // 15-20
        }

        // Ensure weapon always has critical hit or life steal
        if (!result.criticalHitActive && !result.lifeStealActive) {
            if (random.nextBoolean()) {
                result.criticalHitActive = true;
                result.criticalHitAmount = Math.max(1, result.criticalHitAmount);
            } else {
                result.lifeStealActive = true;
                result.lifeStealAmount = Math.max(1, result.lifeStealAmount);
            }
        }
    }

    /**
     * Apply armor-specific adjustments to stats
     *
     * @param result The StatResult to adjust
     */
    private void adjustArmorStats(StatResult result) {
        // Ensure armor always has at least one primary stat
        boolean hasAnyStat = result.vitalityActive || result.strengthActive ||
                result.intelligenceActive || result.dexterityActive;

        if (!hasAnyStat) {
            // Randomly pick one stat to enable
            int statToEnable = random.nextInt(4);
            switch (statToEnable) {
                case 0:
                    result.vitalityActive = true;
                    result.vitalityAmount = Math.max(10, result.vitalityAmount);
                    break;
                case 1:
                    result.strengthActive = true;
                    result.strengthAmount = Math.max(10, result.strengthAmount);
                    break;
                case 2:
                    result.intelligenceActive = true;
                    result.intelligenceAmount = Math.max(10, result.intelligenceAmount);
                    break;
                case 3:
                    result.dexterityActive = true;
                    result.dexterityAmount = Math.max(10, result.dexterityAmount);
                    break;
            }
        }
    }

    /**
     * Class to hold generated stat values
     */
    public static class StatResult {
        // Stat activation flags
        public boolean pureActive;
        public boolean accuracyActive;
        public boolean vsMonstersActive;
        public boolean vsPlayersActive;
        public boolean lifeStealActive;
        public boolean criticalHitActive;
        public boolean dodgeActive;
        public boolean blockActive;
        public boolean vitalityActive;
        public boolean strengthActive;
        public boolean intelligenceActive;
        public boolean dexterityActive;
        public boolean thornsActive;
        public int elementType; // 1=Fire, 2=Poison, 3=Ice

        // Stat values
        public int pureAmount;
        public int accuracyAmount;
        public int vsMonstersAmount;
        public int vsPlayersAmount;
        public int lifeStealAmount;
        public int criticalHitAmount;
        public int dodgeAmount;
        public int blockAmount;
        public int vitalityAmount;
        public int strengthAmount;
        public int intelligenceAmount;
        public int dexterityAmount;
        public int thornsAmount;
        public int elementAmount;

        /**
         * Creates a new StatResult with default values
         */
        public StatResult() {
            // Initialize with default values
            elementType = 0;
            pureActive = false;
            accuracyActive = false;
            vsMonstersActive = false;
            vsPlayersActive = false;
            lifeStealActive = false;
            criticalHitActive = false;
            dodgeActive = false;
            blockActive = false;
            vitalityActive = false;
            strengthActive = false;
            intelligenceActive = false;
            dexterityActive = false;
            thornsActive = false;

            pureAmount = 0;
            accuracyAmount = 0;
            vsMonstersAmount = 0;
            vsPlayersAmount = 0;
            lifeStealAmount = 0;
            criticalHitAmount = 0;
            dodgeAmount = 0;
            blockAmount = 0;
            vitalityAmount = 0;
            strengthAmount = 0;
            intelligenceAmount = 0;
            dexterityAmount = 0;
            thornsAmount = 0;
            elementAmount = 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("StatResult{");

            if (pureActive) sb.append("pure=").append(pureAmount).append(", ");
            if (accuracyActive) sb.append("accuracy=").append(accuracyAmount).append(", ");
            if (vsMonstersActive) sb.append("vsMonsters=").append(vsMonstersAmount).append(", ");
            if (vsPlayersActive) sb.append("vsPlayers=").append(vsPlayersAmount).append(", ");
            if (lifeStealActive) sb.append("lifeSteal=").append(lifeStealAmount).append(", ");
            if (criticalHitActive) sb.append("criticalHit=").append(criticalHitAmount).append(", ");
            if (dodgeActive) sb.append("dodge=").append(dodgeAmount).append(", ");
            if (blockActive) sb.append("block=").append(blockAmount).append(", ");
            if (vitalityActive) sb.append("vit=").append(vitalityAmount).append(", ");
            if (strengthActive) sb.append("str=").append(strengthAmount).append(", ");
            if (intelligenceActive) sb.append("int=").append(intelligenceAmount).append(", ");
            if (dexterityActive) sb.append("dex=").append(dexterityAmount).append(", ");
            if (thornsActive) sb.append("thorns=").append(thornsAmount).append(", ");
            if (elementType > 0) {
                String elementName = "unknown";
                switch (elementType) {
                    case 1:
                        elementName = "fire";
                        break;
                    case 2:
                        elementName = "poison";
                        break;
                    case 3:
                        elementName = "ice";
                        break;
                }
                sb.append("element=").append(elementName).append("(").append(elementAmount).append(")");
            }

            sb.append("}");
            return sb.toString();
        }
    }
}