package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.GemPouchManager;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Menu for the Item Vendor
 */
public class ItemVendorMenu extends Menu {

    private static final String TITLE = "Item Vendor";
    private final ScrollManager scrollManager;
    private final OrbManager orbManager;
    private final GemPouchManager pouchManager;
    private final PurchaseManager purchaseManager;
    private final boolean t6Enabled;

    // Define standard prices for each item type
    private static final int NORMAL_ORB_PRICE = 1500;
    private static final int LEGENDARY_ORB_PRICE = 5000;

    private static final int PROTECTION_SCROLL_BASE_PRICE = 1000;
    private static final int WEAPON_SCROLL_BASE_PRICE = 250;
    private static final int ARMOR_SCROLL_BASE_PRICE = 250;

    // Price multiplier for tier level (T1=1x, T2=2x, etc.)
    private static final double TIER_MULTIPLIER = 2.5;

    /**
     * Creates a new Item Vendor menu
     *
     * @param player The player to open the menu for
     */
    public ItemVendorMenu(Player player) {
        super(player, TITLE, 54);
        this.scrollManager = ScrollManager.getInstance();
        this.orbManager = OrbManager.getInstance();
        this.pouchManager = GemPouchManager.getInstance();
        this.purchaseManager = PurchaseManager.getInstance();
        this.t6Enabled = YakRealms.isT6Enabled();

        initializeMenu();
    }

    /**
     * Initialize the menu with all items
     */
    private void initializeMenu() {
        // Category Labels (Row 0)
        setItem(0, createCategoryLabel("Orbs & Pouches", Material.ENDER_PEARL));
        setItem(9, createCategoryLabel("Protection", Material.MAP));
        setItem(18, createCategoryLabel("Weapon Enchants", Material.DIAMOND_SWORD));
        setItem(36, createCategoryLabel("Armor Enchants", Material.DIAMOND_CHESTPLATE));

        // Orbs and Pouches (Row 0-1)
        addShopItem(2, ensureItemHasPrice(orbManager.createNormalOrb(true), NORMAL_ORB_PRICE));
        addShopItem(3, ensureItemHasPrice(orbManager.createLegendaryOrb(true), LEGENDARY_ORB_PRICE));

        // Add gem pouches in row 1
        // Note: Pouches likely already have prices from the pouchManager.createGemPouch(tier, true) method
        addShopItem(11, pouchManager.createGemPouch(1, true));
        addShopItem(12, pouchManager.createGemPouch(2, true));
        addShopItem(13, pouchManager.createGemPouch(3, true));
        addShopItem(14, pouchManager.createGemPouch(4, true));
        addShopItem(15, pouchManager.createGemPouch(5, true));
        if (t6Enabled) {
            addShopItem(16, pouchManager.createGemPouch(6, true));
        }

        // Protection Scrolls (Row 2-3)
        for (int tier = 0; tier <= 5; tier++) {
            int price = calculateProtectionScrollPrice(tier);
            int slot = 20 + tier;
            ItemStack scroll = scrollManager.createProtectionScroll(tier);
            addShopItem(slot, ensureItemHasPrice(scroll, price));

            // Skip T6 if not enabled
            if (tier == 5 && !t6Enabled) break;
        }

        // Weapon Enchants (Row 4-5)
        for (int tier = 1; tier <= 6; tier++) {
            int price = calculateWeaponScrollPrice(tier);
            int slot = 37 + tier;
            ItemStack scroll = scrollManager.createWeaponEnhancementScroll(tier);
            addShopItem(slot, ensureItemHasPrice(scroll, price));

            // Skip T6 if not enabled
            if (tier == 5 && !t6Enabled) break;
        }

        // Armor Enchants (Row 6-7)
        for (int tier = 1; tier <= 6; tier++) {
            int price = calculateArmorScrollPrice(tier);
            int slot = 46 + tier;
            ItemStack scroll = scrollManager.createArmorEnhancementScroll(tier);
            addShopItem(slot, ensureItemHasPrice(scroll, price));

            // Skip T6 if not enabled
            if (tier == 5 && !t6Enabled) break;
        }

        // Add colored separators for visual organization
        fillEmptySlots(10, 17, (byte) 3);  // Light Blue (pouches)
        fillEmptySlots(19, 26, (byte) 4);  // Yellow (protection)
        fillEmptySlots(37, 44, (byte) 11); // Blue (weapon enchants)
        fillEmptySlots(46, 53, (byte) 5);  // Lime (armor enchants)

        // Navigation and information
        setItem(8, createInfoButton("About Orbs", Arrays.asList(
                ChatColor.GRAY + "Orbs randomize item stats",
                ChatColor.GRAY + "Legendary orbs guarantee +4"
        )));

        setItem(17, createInfoButton("About Pouches", Arrays.asList(
                ChatColor.GRAY + "Pouches store gems",
                ChatColor.GRAY + "Each tier holds more gems"
        )));

        setItem(26, createInfoButton("About Protection", Arrays.asList(
                ChatColor.GRAY + "Protects an item from being",
                ChatColor.GRAY + "destroyed when enchanting fails"
        )));

        setItem(35, createInfoButton("About Enchants", Arrays.asList(
                ChatColor.GRAY + "Enhances weapons and armor",
                ChatColor.GRAY + "Risk of destruction above +3"
        )));

        // Close button
        setItem(53, createCloseButton());
    }

    /**
     * Calculate price for protection scrolls based on tier
     *
     * @param tier The scroll tier
     * @return The price in gems
     */
    private int calculateProtectionScrollPrice(int tier) {
        if (tier == 0) return PROTECTION_SCROLL_BASE_PRICE;
        return (int) (PROTECTION_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier));
    }

    /**
     * Calculate price for weapon enhancement scrolls based on tier
     *
     * @param tier The scroll tier
     * @return The price in gems
     */
    private int calculateWeaponScrollPrice(int tier) {
        return (int) (WEAPON_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier - 1));
    }

    /**
     * Calculate price for armor enhancement scrolls based on tier
     *
     * @param tier The scroll tier
     * @return The price in gems
     */
    private int calculateArmorScrollPrice(int tier) {
        return (int) (ARMOR_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier - 1));
    }

    /**
     * Ensures an item has a price in its lore
     *
     * @param item  The item to check/modify
     * @param price The price to set if missing
     * @return The item with price
     */
    private ItemStack ensureItemHasPrice(ItemStack item, int price) {
        if (item == null) return null;

        // Check if the item already has a price
        if (getPriceFromLore(item) > 0) {
            return item;
        }

        // Add price to the item's lore
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Add price line
        lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + price + "g");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Adds a shop item to the menu with purchase handling
     *
     * @param slot      The slot to place the item in
     * @param itemStack The item to add
     */
    private void addShopItem(int slot, ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }

        MenuItem menuItem = new MenuItem(itemStack)
                .setClickHandler((p, s) -> {
                    int price = getPriceFromLore(itemStack);
                    if (price > 0) {
                        p.closeInventory();
                        purchaseManager.startPurchase(p, itemStack, price);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                });

        setItem(slot, menuItem);
    }

    /**
     * Creates a category label for the menu
     *
     * @param name The name of the category
     * @param icon The icon for the category
     * @return The created label item
     */
    private ItemStack createCategoryLabel(String name, Material icon) {
        ItemStack label = new ItemStack(icon);
        ItemMeta meta = label.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + name);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Click on an item", ChatColor.GRAY + "below to purchase it!"));
        label.setItemMeta(meta);
        return label;
    }

    /**
     * Creates an information button
     *
     * @param title The title of the info button
     * @param lore  The information to display
     * @return The info button item
     */
    private ItemStack createInfoButton(String title, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + title);
        meta.setLore(lore);
        item.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(item);
        // No click handler needed - just displays info

        return item;
    }

    /**
     * Fill empty slots in a range with a colored glass pane
     *
     * @param start The starting slot (inclusive)
     * @param end   The ending slot (inclusive)
     * @param color The color of the glass panes
     */
    private void fillEmptySlots(int start, int end, byte color) {
        for (int i = start; i <= end; i++) {
            if (getItem(i) == null) {
                setItem(i, createSeparator(color));
            }
        }
    }

    /**
     * Creates a separator glass pane
     *
     * @param color The color of the glass pane
     * @return The created separator item
     */
    private ItemStack createSeparator(byte color) {
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1, color);
        ItemMeta meta = separator.getItemMeta();
        meta.setDisplayName(" ");
        separator.setItemMeta(meta);
        return separator;
    }

    /**
     * Creates a close button for the menu
     *
     * @return The created close button
     */
    private ItemStack createCloseButton() {
        ItemStack closeButton = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = closeButton.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
        closeButton.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(closeButton)
                .setClickHandler((p, s) -> p.closeInventory());

        setItem(53, menuItem);

        return closeButton;
    }

    /**
     * Extract the price from an item's lore
     *
     * @param item The item to check
     * @return The price, or -1 if not found
     */
    private int getPriceFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return -1;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("Price:")) {
                String priceStr = ChatColor.stripColor(line);
                priceStr = priceStr.substring(priceStr.indexOf(":") + 1).trim();
                if (priceStr.endsWith("g")) {
                    priceStr = priceStr.substring(0, priceStr.length() - 1).trim();
                }
                try {
                    return Integer.parseInt(priceStr);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }

        return -1;
    }
}