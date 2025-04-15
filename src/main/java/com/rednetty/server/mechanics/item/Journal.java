package com.rednetty.server.mechanics.item;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.CombatMechanics;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Responsible for creating and managing character journals
 * that display player stats and attributes
 */
public class Journal {

    private static final String JOURNAL_TITLE = "Character Journal";
    private static final String JOURNAL_AUTHOR = "YakRealms";

    /**
     * Creates an empty character journal
     *
     * @return The journal item
     */
    public static ItemStack createEmptyJournal() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle(JOURNAL_TITLE);
        meta.setAuthor(JOURNAL_AUTHOR);
        meta.setDisplayName(ChatColor.GREEN.toString() + ChatColor.BOLD + JOURNAL_TITLE);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "A book that displays",
                ChatColor.GRAY + "your character's stats"
        ));

        // Add NBT data to identify this as a journal
        book.setItemMeta(meta);
        NBTAccessor nbt = new NBTAccessor(book);
        nbt.setString("journalType", "character");

        return nbt.update();
    }

    /**
     * Creates a fully populated character journal for a player
     *
     * @param player The player to create the journal for
     * @return The populated journal
     */
    public static ItemStack createPlayerJournal(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        // Set basic book properties
        meta.setTitle(JOURNAL_TITLE);
        meta.setAuthor(JOURNAL_AUTHOR);
        meta.setDisplayName(ChatColor.GREEN.toString() + ChatColor.BOLD + JOURNAL_TITLE);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "A book that displays",
                ChatColor.GRAY + "your character's stats"
        ));

        // Generate all pages
        List<String> pages = generateJournalPages(player);
        meta.setPages(pages);

        book.setItemMeta(meta);

        // Add NBT data to identify this as a journal
        NBTAccessor nbt = new NBTAccessor(book);
        nbt.setString("journalType", "character");
        nbt.setString("journalOwner", player.getUniqueId().toString());

        return nbt.update();
    }

    /**
     * Opens the character journal for a player
     * Using standard Bukkit API for maximum compatibility
     *
     * @param player The player to open the journal for
     */
    public static void openJournal(Player player) {
        ItemStack journal = createPlayerJournal(player);

        // Save current held item
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack originalItem = player.getInventory().getItem(slot);

        try {
            // Temporarily replace with journal
            player.getInventory().setItem(slot, journal);

            // Open the book for the player using standard API
            player.openBook(journal);

            // Schedule task to restore original item
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.getInventory().setItem(slot, originalItem);
                    }
                }
            }.runTaskLater(YakRealms.getInstance(), 2L); // 2 tick delay (0.1 seconds)
        } catch (Exception e) {
            // Restore item immediately if error occurs
            player.getInventory().setItem(slot, originalItem);
            YakRealms.error("Failed to open journal for " + player.getName(), e);
        }
    }

    /**
     * Generate all pages for the journal
     *
     * @param player The player to generate pages for
     * @return List of page content strings
     */
    private static List<String> generateJournalPages(Player player) {
        List<String> pages = new ArrayList<>();

        // Add the pages
        pages.add(generateOverviewPage(player));
        pages.add(generateStrengthVitalityPage(player));
        pages.add(generateIntellectDexterityPage(player));

        return pages;
    }

    /**
     * Generate the overview page with alignment and primary stats
     *
     * @param player The player
     * @return The page content
     */
    private static String generateOverviewPage(Player player) {
        // Get player alignment info using existing AlignmentMechanics implementation
        String alignmentString = AlignmentMechanics.getAlignmentString(player);
        alignmentString = ChatColor.translateAlternateColorCodes('&', alignmentString);

        // Get alignment description
        String alignmentDesc = getAlignmentDescription(player);

        // Get alignment time remaining if any
        int timeRemaining = AlignmentMechanics.getAlignmentTime(player);
        String timeString = "";
        if (timeRemaining > 0) {
            String nextAlignment = getNextAlignment(player);
            timeString = "\n" + ChatColor.BLACK + ChatColor.BOLD + nextAlignment +
                    ChatColor.BLACK + " in " + timeRemaining + "s";
        }

        // Get basic stats using the CombatMechanics class for accuracy
        int armor = calculateTotalArmor(player);
        int dps = calculateTotalDps(player);
        int healthRegen = calculateTotalHealthRegen(player);
        int energy = calculateTotalEnergy(player);
        int dodge = calculateDodgeChance(player);
        int block = calculateBlockChance(player);

        // Check if in combat
        CombatMechanics combatMechanics = YakRealms.getInstance().getCombatMechanics();
        String combatText = "";
        if (combatMechanics.isInCombat(player)) {
            int remainingTime = combatMechanics.getRemainingCombatTime(player);
            Player lastAttacker = combatMechanics.getLastAttacker(player);
            String attackerName = lastAttacker != null ? lastAttacker.getName() : "Unknown";

            combatText = "\n" + ChatColor.RED + ChatColor.BOLD + "IN COMBAT - " +
                    ChatColor.RED + remainingTime + "s" +
                    ChatColor.GRAY + " (vs. " + attackerName + ")";
        }

        // Create the page
        return ChatColor.UNDERLINE.toString() + ChatColor.BOLD + "  Your Character  \n\n" +
                ChatColor.RESET + ChatColor.BOLD + "Alignment: " + alignmentString +
                timeString + combatText + "\n" +
                ChatColor.BLACK + ChatColor.ITALIC + alignmentDesc + "\n\n" +
                ChatColor.BLACK + "  " + (int) player.getHealth() + " / " +
                (int) player.getMaxHealth() + ChatColor.BOLD + " HP\n" +
                ChatColor.BLACK + "  " + armor + " - " + armor + "%" + ChatColor.BOLD + " Armor\n" +
                ChatColor.BLACK + "  " + dps + " - " + dps + "%" + ChatColor.BOLD + " DPS\n" +
                ChatColor.BLACK + "  " + healthRegen + ChatColor.BOLD + " HP/s\n" +
                ChatColor.BLACK + "  " + energy + "% " + ChatColor.BOLD + "Energy\n" +
                ChatColor.BLACK + "  " + dodge + "% " + ChatColor.BOLD + "Dodge\n" +
                ChatColor.BLACK + "  " + block + "% " + ChatColor.BOLD + "Block";
    }

    /**
     * Get alignment description based on current alignment
     *
     * @param player The player
     * @return Description of alignment effects
     */
    private static String getAlignmentDescription(Player player) {
        String alignmentString = AlignmentMechanics.getAlignmentString(player);

        if (alignmentString.contains("CHAOTIC")) {
            return "Inventory LOST on Death";
        } else if (alignmentString.contains("NEUTRAL")) {
            return "25%/50% Arm/Wep LOST on Death";
        } else {
            return "-30% Durability Arm/Wep on Death";
        }
    }

    /**
     * Get the next alignment stage
     *
     * @param player The player
     * @return The next alignment name
     */
    private static String getNextAlignment(Player player) {
        String alignmentString = AlignmentMechanics.getAlignmentString(player);

        if (alignmentString.contains("CHAOTIC")) {
            return "Neutral";
        } else if (alignmentString.contains("NEUTRAL")) {
            return "Lawful";
        } else {
            return "";
        }
    }

    /**
     * Generate the strength and vitality stats page
     *
     * @param player The player
     * @return The page content
     */
    private static String generateStrengthVitalityPage(Player player) {
        // Calculate primary attributes
        int strength = calculateTotalAttribute(player, "STR");
        int vitality = calculateTotalAttribute(player, "VIT");

        // Calculate derived stats based on CombatMechanics formulas
        int armorBonus = (int) Math.round(strength * 0.012);
        int blockBonus = (int) Math.round(strength * 0.015);
        int axeDmgBonus = Math.round(strength / 50);
        int polearmDmgBonus = Math.round(strength / 50);

        int healthBonus = (int) Math.round(vitality * 0.05);
        int hpsBonus = (int) Math.round(vitality * 0.1);
        int swordDmgBonus = Math.round(vitality / 50);

        // Create the page
        return ChatColor.BLACK.toString() + ChatColor.BOLD + "+ " + strength + " Strength\n" +
                "  " + ChatColor.BLACK + ChatColor.UNDERLINE + "'The Warrior'\n" +
                ChatColor.BLACK + "+" + armorBonus + "% Armor\n" +
                ChatColor.BLACK + "+" + blockBonus + "% Block\n" +
                ChatColor.BLACK + "+" + axeDmgBonus + "% Axe DMG\n" +
                ChatColor.BLACK + "+" + polearmDmgBonus + "% Polearm DMG\n\n" +
                ChatColor.BLACK + ChatColor.BOLD + "+ " + vitality + " Vitality\n\n" +
                "  " + ChatColor.BLACK + ChatColor.UNDERLINE + "'The Defender'\n" +
                ChatColor.BLACK + "+" + healthBonus + "% Health\n" +
                ChatColor.BLACK + "+" + hpsBonus + "   HP/s\n" +
                ChatColor.BLACK + "+" + swordDmgBonus + "% Sword DMG";
    }

    /**
     * Generate the intellect and dexterity stats page
     *
     * @param player The player
     * @return The page content
     */
    private static String generateIntellectDexterityPage(Player player) {
        // Calculate primary attributes
        int intellect = calculateTotalAttribute(player, "INT");
        int dexterity = calculateTotalAttribute(player, "DEX");

        // Calculate derived stats using CombatMechanics formulas
        int staffDmgBonus = Math.round(intellect / 50);
        int energyBonus = (int) Math.round(intellect * 0.009);
        int eleDmgBonus = Math.round(intellect / 30);
        int critBonus = (int) Math.round(intellect * 0.015);

        int dodgeBonus = (int) Math.round(dexterity * 0.015);
        int dpsBonus = (int) Math.round(dexterity * 0.012);
        int armorPenBonus = (int) (dexterity * 0.035);

        // Create the page
        return ChatColor.BLACK + "" + ChatColor.BOLD + "+ " + intellect + " Intellect\n" +
                "  " + ChatColor.BLACK + ChatColor.UNDERLINE + "'The Mage'\n" +
                ChatColor.BLACK + "+" + staffDmgBonus + "% Staff DMG\n" +
                ChatColor.BLACK + "+" + energyBonus + "% Energy\n" +
                ChatColor.BLACK + "+" + eleDmgBonus + "% Ele Damage\n" +
                ChatColor.BLACK + "+" + critBonus + "% Critical Hit\n\n" +
                ChatColor.BLACK + "" + ChatColor.BOLD + "+ " + dexterity + " Dexterity\n" +
                "  " + ChatColor.BLACK + ChatColor.UNDERLINE + "'The Archer'\n\n" +
                ChatColor.BLACK + "+" + dodgeBonus + "% Dodge\n" +
                ChatColor.BLACK + "+" + dpsBonus + "% DPS\n" +
                ChatColor.BLACK + "+" + armorPenBonus + "% Armor Pen.\n ";
    }

    /**
     * Calculate the total armor value from all equipped items
     * Using CombatMechanics formulas for consistency
     *
     * @param player The player
     * @return The total armor value
     */
    private static int calculateTotalArmor(Player player) {
        int totalArmor = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalArmor += getArmorValue(armor);
            }
        }

        // Add STR bonus to armor (matching CombatMechanics formula)
        int strength = calculateTotalAttribute(player, "STR");
        if (strength > 0) {
            totalArmor += strength * 0.1; // 0.1 armor per strength point
        }

        return totalArmor;
    }

    /**
     * Get the armor value from an item
     * Matches CombatMechanics.getArmorValue implementation
     */
    private static int getArmorValue(ItemStack item) {
        if (item != null && item.getType() != Material.AIR &&
                item.hasItemMeta() && item.getItemMeta().hasLore()) {
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
     * Calculate the total DPS value from all equipped items
     * Using CombatMechanics formulas for consistency
     *
     * @param player The player
     * @return The total DPS value
     */
    private static int calculateTotalDps(Player player) {
        int totalDps = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalDps += getDpsValue(armor);
            }
        }

        // Add DEX bonus to DPS (matching CombatMechanics formula)
        int dexterity = calculateTotalAttribute(player, "DEX");
        if (dexterity > 0) {
            totalDps += Math.round(dexterity * 0.012f);
        }

        return totalDps;
    }

    /**
     * Get the DPS value from an item
     * Matches CombatMechanics.getDpsValue implementation
     */
    private static int getDpsValue(ItemStack item) {
        if (item != null && item.getType() != Material.AIR &&
                item.hasItemMeta() && item.getItemMeta().hasLore()) {
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
     * Calculate the total health regeneration from all equipped items
     * Using CombatMechanics healing formulas
     *
     * @param player The player
     * @return The total health regeneration
     */
    private static int calculateTotalHealthRegen(Player player) {
        int totalRegen = 5; // Base regeneration

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalRegen += getHpsFromItem(armor);
            }
        }

        // Add VIT bonus to HP regen (matching CombatMechanics formula)
        int vitality = calculateTotalAttribute(player, "VIT");
        if (vitality > 0) {
            totalRegen += Math.round(vitality * 0.1f);
        }

        return totalRegen;
    }

    /**
     * Extract HPS (healing per second) value from an item
     * Matches AlignmentMechanics.getHpsFromItem implementation
     */
    private static double getHpsFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }

        for (String line : item.getItemMeta().getLore()) {
            if (line.contains("HP REGEN") || line.contains("HPS")) {
                try {
                    // Extract the numeric value
                    String valueText = line.replaceAll("[^0-9]", "");
                    return Double.parseDouble(valueText);
                } catch (Exception e) {
                    return 0;
                }
            }
        }

        return 0;
    }

    /**
     * Calculate the total energy from all equipped items
     *
     * @param player The player
     * @return The total energy
     */
    private static int calculateTotalEnergy(Player player) {
        int totalEnergy = 100; // Base energy

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
     * Extract energy value from an item
     */
    private static int getEnergyFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }

        for (String line : item.getItemMeta().getLore()) {
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

        return 0;
    }

    /**
     * Calculate the total dodge chance from all equipped items
     * Using CombatMechanics formula for consistency
     *
     * @param player The player
     * @return The total dodge chance
     */
    private static int calculateDodgeChance(Player player) {
        int totalDodge = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalDodge += getAttributePercent(armor, "DODGE");
            }
        }

        // Add DEX bonus to dodge (matching CombatMechanics formula)
        int dexterity = calculateTotalAttribute(player, "DEX");
        if (dexterity > 0) {
            totalDodge += Math.round(dexterity * 0.015f);
        }

        // Cap at max dodge chance from CombatMechanics
        return Math.min(totalDodge, 60);
    }

    /**
     * Calculate the total block chance from all equipped items
     * Using CombatMechanics formula for consistency
     *
     * @param player The player
     * @return The total block chance
     */
    private static int calculateBlockChance(Player player) {
        int totalBlock = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                totalBlock += getAttributePercent(armor, "BLOCK");
            }
        }

        // Add STR bonus to block (matching CombatMechanics formula)
        int strength = calculateTotalAttribute(player, "STR");
        if (strength > 0) {
            totalBlock += Math.round(strength * 0.015f);
        }

        // Cap at max block chance from CombatMechanics
        return Math.min(totalBlock, 60);
    }

    /**
     * Get a percentage attribute from an item
     * Matches CombatMechanics.getAttributePercent implementation
     */
    private static int getAttributePercent(ItemStack item, String attribute) {
        if (item != null && item.getType() != Material.AIR &&
                item.hasItemMeta() && item.getItemMeta().hasLore()) {
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
     * Calculate the total value of an attribute from all equipped items
     * Matches method in AlignmentMechanics and CombatMechanics
     *
     * @param player    The player
     * @param attribute The attribute to calculate (STR, VIT, INT, DEX)
     * @return The total attribute value
     */
    private static int calculateTotalAttribute(Player player, String attribute) {
        int total = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasValidItemStats(armor)) {
                total += getElementalAttribute(armor, attribute);
            }
        }

        return total;
    }

    /**
     * Get an elemental attribute value from an item
     * Matches CombatMechanics.getElementalAttribute implementation
     */
    private static int getElementalAttribute(ItemStack item, String attribute) {
        if (item != null && item.getType() != Material.AIR &&
                item.hasItemMeta() && item.getItemMeta().hasLore()) {
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
     * Check if an item has valid stats
     *
     * @param item The item to check
     * @return true if the item has valid stats
     */
    private static boolean hasValidItemStats(ItemStack item) {
        return item != null &&
                item.getType() != Material.AIR &&
                item.hasItemMeta() &&
                item.getItemMeta().hasLore();
    }
}