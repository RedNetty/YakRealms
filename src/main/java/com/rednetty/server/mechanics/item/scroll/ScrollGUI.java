package com.rednetty.server.mechanics.item.scroll;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuClickHandler;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * GUI for purchasing and managing various scroll items
 */
public class ScrollGUI extends Menu {

    private static final String TITLE = "Dungeoneer";
    private static final int SIZE = 9;

    /**
     * Creates a new scroll GUI for the specified player
     *
     * @param player The player viewing the GUI
     */
    public ScrollGUI(Player player) {
        super(player, TITLE, SIZE);
        initialize();
    }

    /**
     * Initializes the menu items
     */
    private void initialize() {
        // Determine the max tier of scrolls to display based on server settings
        int maxTier = YakRealms.getInstance().isT6Enabled() ? 5 : 4;

        // Add protection scrolls
        for (int tier = 0; tier <= maxTier; tier++) {
            addProtectionScroll(tier);
        }

        // Fill the rest of the slots with glass panes
        for (int i = maxTier + 1; i < SIZE; i++) {
            setItem(i, createGlassPane());
        }
    }

    /**
     * Adds a protection scroll of the given tier to the menu
     *
     * @param tier The tier of the scroll
     */
    private void addProtectionScroll(int tier) {
        ItemStack scroll = ItemAPI.getScrollGenerator().createProtectionScroll(tier);
        int price = ItemAPI.getScrollGenerator().getProtectionScrollPrice(tier);

        // Skip if not for sale
        if (price < 0) {
            return;
        }

        // Add price information to the lore
        ItemMeta meta = scroll.getItemMeta();
        List<String> lore = meta.getLore();
        lore.add("");
        lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + price + "g");
        meta.setLore(lore);
        scroll.setItemMeta(meta);

        // Add NBT data for GUI
        NBTAccessor nbtAccessor = new NBTAccessor(scroll);
        nbtAccessor.setInt("guiPrice", price);
        nbtAccessor.setInt("guiTier", tier);
        scroll = nbtAccessor.update();

        // Create a menu item with the scroll and a click handler
        MenuItem menuItem = new MenuItem(scroll)
                .setClickHandler(new ProtectionScrollClickHandler(tier, price));

        setItem(tier, menuItem);
    }

    /**
     * Creates a decorative glass pane for empty slots
     *
     * @return The glass pane item
     */
    private ItemStack createGlassPane() {
        return new MenuItem(Material.GRAY_STAINED_GLASS_PANE, " ")
                .setGlowing(false)
                .toItemStack();
    }

    /**
     * Click handler for protection scroll purchase
     */
    private class ProtectionScrollClickHandler implements MenuClickHandler {
        private final int tier;
        private final int price;

        public ProtectionScrollClickHandler(int tier, int price) {
            this.tier = tier;
            this.price = price;
        }

        @Override
        public void onClick(Player player, int slot) {
            // Check if the player has enough gems
            if (price <= 0) {
                player.sendMessage(ChatColor.RED + "This item cannot be purchased");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }

            EconomyManager economyManager = YakRealms.getInstance().getEconomyManager();

            if (economyManager.hasGems(player.getUniqueId(), price)) {
                // Start the quantity selection process
                ItemStack scroll = ItemAPI.getScrollGenerator().createProtectionScroll(tier);
                ScrollPurchaseManager.startPurchase(player, scroll, price);

                // Show quantity prompt
                player.sendMessage(ChatColor.GREEN + "Enter the " + ChatColor.BOLD + "QUANTITY" +
                        ChatColor.GREEN + " you'd like to purchase.");
                player.sendMessage(ChatColor.GRAY + "MAX: 64X (" + (price * 64) +
                        "g), OR " + price + "g/each.");

                // Close the menu
                player.closeInventory();
            } else {
                // Not enough gems
                player.sendMessage(ChatColor.RED + "You do NOT have enough gems to purchase this " +
                        ItemAPI.getScrollGenerator().createProtectionScroll(tier).getItemMeta().getDisplayName());
                player.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "COST: " +
                        ChatColor.RED + price + ChatColor.BOLD + "G");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                player.closeInventory();
            }
        }
    }
}