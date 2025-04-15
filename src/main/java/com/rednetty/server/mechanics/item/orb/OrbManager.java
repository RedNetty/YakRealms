package com.rednetty.server.mechanics.item.orb;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager class for all orb-related functionality
 */
public class OrbManager {
    private static OrbManager instance;
    private final OrbHandler orbHandler;
    private final OrbGenerator orbGenerator;
    private final OrbProcessor orbProcessor;

    /**
     * Private constructor for singleton pattern
     */
    private OrbManager() {
        this.orbHandler = new OrbHandler();
        this.orbGenerator = OrbAPI.getOrbGenerator();
        this.orbProcessor = new OrbProcessor();
    }

    /**
     * Gets the singleton instance
     *
     * @return The OrbManager instance
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
        // Initialize the OrbAPI
        OrbAPI.initialize();

        // Register the event handler
        orbHandler.register();

        YakRealms.log("Orb system has been initialized");
    }

    /**
     * Creates a normal Orb of Alteration
     *
     * @param showPrice Whether to display the price in the lore
     * @return The created orb
     */
    public ItemStack createNormalOrb(boolean showPrice) {
        return orbGenerator.createNormalOrb(showPrice);
    }

    /**
     * Creates a legendary Orb of Alteration
     *
     * @param showPrice Whether to display the price in the lore
     * @return The created legendary orb
     */
    public ItemStack createLegendaryOrb(boolean showPrice) {
        return orbGenerator.createLegendaryOrb(showPrice);
    }

    /**
     * Gets the price of a normal orb
     *
     * @return The price in gems
     */
    public int getNormalOrbPrice() {
        return orbGenerator.getOrbPrice(false);
    }

    /**
     * Gets the price of a legendary orb
     *
     * @return The price in gems
     */
    public int getLegendaryOrbPrice() {
        return orbGenerator.getOrbPrice(true);
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
     * Apply an orb to an item
     *
     * @param item        The item to apply the orb to
     * @param isLegendary Whether to use a legendary orb
     * @param bonusRolls  Number of bonus rolls for legendary orbs
     * @return The modified item
     */
    public ItemStack applyOrbToItem(ItemStack item, boolean isLegendary, int bonusRolls) {
        return orbProcessor.applyOrbToItem(item, isLegendary, bonusRolls);
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
        help.add(ChatColor.WHITE + "Orbs randomize the stats on equipment.");
        help.add(ChatColor.WHITE + "Normal orbs: Randomize stats with normal quality.");
        help.add(ChatColor.WHITE + "Legendary orbs: Randomize stats with high quality and ensure items are at least +4.");
        help.add(ChatColor.YELLOW + "To use: Hold an orb over the item you want to modify and click.");
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
        player.sendMessage(ChatColor.WHITE + "3. Normal orbs randomize stats");
        player.sendMessage(ChatColor.WHITE + "4. Legendary orbs guarantee better stats and at least +4");
        player.sendMessage(ChatColor.YELLOW + "Warning: Once applied, an orb's effects cannot be undone!");
    }

    /**
     * Get the OrbProcessor instance
     *
     * @return The OrbProcessor
     */
    public OrbProcessor getOrbProcessor() {
        return orbProcessor;
    }

    /**
     * Get the OrbGenerator instance
     *
     * @return The OrbGenerator
     */
    public OrbGenerator getOrbGenerator() {
        return orbGenerator;
    }
}