package com.rednetty.server.mechanics.item.scroll;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Manager class for all scroll-related functionality
 */
public class ScrollManager {

    private static ScrollManager instance;
    private final ScrollHandler scrollHandler;

    /**
     * Creates a new scroll manager
     */
    private ScrollManager() {
        this.scrollHandler = new ScrollHandler();
    }

    /**
     * Gets the singleton instance of the scroll manager
     *
     * @return The scroll manager instance
     */
    public static ScrollManager getInstance() {
        if (instance == null) {
            instance = new ScrollManager();
        }
        return instance;
    }

    /**
     * Initializes the scroll system
     */
    public void initialize() {
        // Initialize the item API and scroll generator
        ItemAPI.initialize();

        // Register event handlers
        scrollHandler.register();

        // Initialize the scroll purchase manager
        ScrollPurchaseManager.initialize();

        YakRealms.log("Scroll system has been initialized");
    }

    /**
     * Opens the scroll GUI for a player
     *
     * @param player The player to open the GUI for
     */
    public void openScrollGUI(Player player) {
        ScrollGUI gui = new ScrollGUI(player);
        gui.open();
    }

    /**
     * Creates a protection scroll of the specified tier
     *
     * @param tier The tier of the scroll
     * @return The created protection scroll
     */
    public ItemStack createProtectionScroll(int tier) {
                int protectionTier = Math.max(0, Math.min(5, tier - 1));

                return getScrollGenerator().createProtectionScroll(protectionTier);

        }
    /**
     * Creates an armor enhancement scroll of the specified tier
     *
     * @param tier The tier of the scroll
     * @return The created armor enhancement scroll
     */
    public ItemStack createArmorEnhancementScroll(int tier) {
        return getScrollGenerator().createArmorEnhancementScroll(tier);
    }

    /**
     * Creates a weapon enhancement scroll of the specified tier
     *
     * @param tier The tier of the scroll
     * @return The created weapon enhancement scroll
     */
    public ItemStack createWeaponEnhancementScroll(int tier) {
        return getScrollGenerator().createWeaponEnhancementScroll(tier);
    }

    /**
     * Gets the scroll generator instance
     *
     * @return The scroll generator
     */
    public static ScrollGenerator getScrollGenerator() {
        return ItemAPI.getScrollGenerator();
    }

    /**
     * Sends a command to open the scroll GUI
     *
     * @param targetPlayer The player to send the command for
     */
    public void openScrollGUICommand(Player targetPlayer) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "tellraw " + targetPlayer.getName() +
                        " {\"text\":\"[Click here to open the scroll shop]\",\"color\":\"green\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/scrolls\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Open the scroll shop\"}}");
    }
}