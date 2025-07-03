package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.GemPouchManager;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Enhanced ItemVendorMenu with clean, organized layout design.
 * Maintains all original functionality while providing a much better visual experience.
 */
public class ItemVendorMenu extends Menu {

    private static final String TITLE = "Item Vendor";

    // Core managers
    private final ScrollManager scrollManager;
    private final OrbManager orbManager;
    private final GemPouchManager pouchManager;
    private final PurchaseManager purchaseManager;
    private final YakRealms plugin;

    // Configuration
    private final boolean t6Enabled;
    private final Map<String, Object> menuConfig;

    // Performance tracking
    private final Map<Integer, Long> lastClickTime = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 250;

    // Enhanced pricing system
    private static final int NORMAL_ORB_BASE_PRICE = 1500;
    private static final int LEGENDARY_ORB_BASE_PRICE = 5000;
    private static final int PROTECTION_SCROLL_BASE_PRICE = 1000;
    private static final int WEAPON_SCROLL_BASE_PRICE = 250;
    private static final int ARMOR_SCROLL_BASE_PRICE = 250;
    private static final double TIER_MULTIPLIER = 2.5;
    private static final double DYNAMIC_PRICE_VARIATION = 0.1;

    // Clean layout sections
    private static final int[] TOP_BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] BOTTOM_BORDER = {45, 46, 47, 48, 49, 50, 51, 52, 53};

    // Section 1: Orbs (Row 2, Left)
    private static final int ORB_SECTION_START = 10;
    private static final int[] ORB_SLOTS = {10, 11}; // Normal, Legendary

    // Section 2: Gem Pouches (Row 2, Right)
    private static final int POUCH_SECTION_START = 14;
    private static final int[] POUCH_SLOTS = {14, 15, 16}; // Tiers 1, 2, 3

    // Section 3: Protection Scrolls (Row 3)
    private static final int PROTECTION_SECTION_START = 19;
    private static final int[] PROTECTION_SLOTS = {19, 20, 21, 22, 23, 24, 25}; // Tiers 0-6

    // Section 4: Weapon Scrolls (Row 4)
    private static final int WEAPON_SECTION_START = 28;
    private static final int[] WEAPON_SLOTS = {28, 29, 30, 31, 32, 33, 34}; // Tiers 1-6

    // Section 5: Armor Scrolls (Row 5)
    private static final int ARMOR_SECTION_START = 37;
    private static final int[] ARMOR_SLOTS = {37, 38, 39, 40, 41, 42, 43}; // Tiers 1-6

    /**
     * Enhanced constructor with comprehensive initialization
     */
    public ItemVendorMenu(Player player) {
        super(player, TITLE, 54);

        this.plugin = YakRealms.getInstance();
        this.scrollManager = ScrollManager.getInstance();
        this.orbManager = OrbManager.getInstance();
        this.pouchManager = GemPouchManager.getInstance();
        this.purchaseManager = PurchaseManager.getInstance();
        this.t6Enabled = YakRealms.isT6Enabled();
        this.menuConfig = loadMenuConfiguration();

        try {
            initializeCleanMenu();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error initializing ItemVendorMenu for player " + player.getName(), e);
            initializeBasicMenu();
        }
    }

    /**
     * Load menu configuration with defaults
     */
    private Map<String, Object> loadMenuConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("enableDynamicPricing", plugin.getConfig().getBoolean("vendors.item-vendor.dynamic-pricing", false));
        config.put("enableItemDescriptions", plugin.getConfig().getBoolean("vendors.item-vendor.detailed-descriptions", true));
        config.put("enableQuickBuy", plugin.getConfig().getBoolean("vendors.item-vendor.quick-buy", true));
        config.put("maxTierEnabled", t6Enabled ? 6 : 5);
        config.put("enableStockLimits", plugin.getConfig().getBoolean("vendors.item-vendor.stock-limits", false));
        return config;
    }

    /**
     * Initialize the clean, organized menu layout
     */
    private void initializeCleanMenu() {
        try {
            // Create clean borders
            createCleanBorders();

            // Create section headers
            createSectionHeaders();

            // Populate all sections with original items
            populateOrbsAndPouches();
            populateProtectionScrolls();
            populateWeaponEnchants();
            populateArmorEnchants();

            // Add navigation elements
            addNavigationElements();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during clean menu initialization", e);
            initializeBasicMenu();
        }
    }

    /**
     * Create clean, professional borders
     */
    private void createCleanBorders() {
        // Top border - Light blue glass
        for (int i : TOP_BORDER) {
            setItem(i, VendorUtils.createSeparator(Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.AQUA + ""));
        }

        // Bottom border - Light blue glass
        for (int i : BOTTOM_BORDER) {
            setItem(i, VendorUtils.createSeparator(Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.AQUA + ""));
        }

        // Side separators between sections (subtle gray)
        setItem(9, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(13, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(17, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(18, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(26, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(27, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(35, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(36, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(44, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    /**
     * Create clear section headers
     */
    private void createSectionHeaders() {
        // Orbs section header
        ItemStack orbHeader = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = orbHeader.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Mystical Orbs");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Reroll item statistics",
                    ChatColor.GRAY + "Normal: Random results",
                    ChatColor.GRAY + "Legendary: High-tier guaranteed"
            ));
            orbHeader.setItemMeta(meta);
        }
        setItem(1, orbHeader);

        // Pouches section header
        ItemStack pouchHeader = new ItemStack(Material.BUNDLE);
        meta = pouchHeader.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Gem Pouches");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Portable gem storage",
                    ChatColor.GRAY + "Higher tiers hold more gems",
                    ChatColor.GRAY + "Keep your wealth safe"
            ));
            pouchHeader.setItemMeta(meta);
        }
        setItem(7, pouchHeader);

        // Protection section header
        ItemStack protectionHeader = new ItemStack(Material.SHIELD);
        meta = protectionHeader.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + "" + ChatColor.BOLD + "Protection Scrolls");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Prevent item destruction",
                    ChatColor.GRAY + "Essential for safe enchanting",
                    ChatColor.GRAY + "Use before risky upgrades"
            ));
            protectionHeader.setItemMeta(meta);
        }
        setItem(4, protectionHeader);
    }

    /**
     * Populate orbs and pouches sections (original items, clean layout)
     */
    private void populateOrbsAndPouches() {
        try {
            // Orbs (keeping original creation logic)
            ItemStack normalOrb = orbManager.createNormalOrb(true);
            ItemStack legendaryOrb = orbManager.createLegendaryOrb(true);

            // Apply pricing
            normalOrb = VendorUtils.addPriceToItem(normalOrb, calculateDynamicPrice(NORMAL_ORB_BASE_PRICE, "normal_orb"));
            legendaryOrb = VendorUtils.addPriceToItem(legendaryOrb, calculateDynamicPrice(LEGENDARY_ORB_BASE_PRICE, "legendary_orb"));

            addShopItem(ORB_SLOTS[0], normalOrb, "A magical orb that randomizes item statistics");
            addShopItem(ORB_SLOTS[1], legendaryOrb, "A legendary orb that guarantees high-tier results");

            // Gem pouches (keeping original creation logic)
            int maxTier = Math.min(3, (Integer) menuConfig.get("maxTierEnabled")); // Show only first 3 tiers for clean layout
            for (int tier = 1; tier <= maxTier; tier++) {
                ItemStack pouch = pouchManager.createGemPouch(tier, true);
                if (pouch != null) {
                    addShopItem(POUCH_SLOTS[tier - 1], pouch, "Tier " + tier + " gem pouch - stores up to " + (tier * 1000) + " gems safely");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error populating orbs and pouches", e);
        }
    }

    /**
     * Populate protection scrolls (original items, horizontal layout)
     */
    private void populateProtectionScrolls() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));

            for (int tier = 0; tier <= maxTier && tier < PROTECTION_SLOTS.length; tier++) {
                ItemStack scroll = scrollManager.createProtectionScroll(tier);
                if (scroll != null) {
                    int price = calculateProtectionScrollPrice(tier);
                    scroll = VendorUtils.addPriceToItem(scroll, price);

                    String description = tier == 0 ?
                            "Basic protection against enchanting failures" :
                            "Tier " + tier + " protection - prevents item destruction";

                    addShopItem(PROTECTION_SLOTS[tier], scroll, description);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error populating protection scrolls", e);
        }
    }

    /**
     * Populate weapon enchants (original items, horizontal layout)
     */
    private void populateWeaponEnchants() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));

            for (int tier = 1; tier <= maxTier && tier <= WEAPON_SLOTS.length; tier++) {
                ItemStack scroll = scrollManager.createWeaponEnhancementScroll(tier);
                if (scroll != null) {
                    int price = calculateWeaponScrollPrice(tier);
                    scroll = VendorUtils.addPriceToItem(scroll, price);

                    String description = "Tier " + tier + " weapon enhancement - increases damage by +" + tier;

                    addShopItem(WEAPON_SLOTS[tier - 1], scroll, description);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error populating weapon enchants", e);
        }
    }

    /**
     * Populate armor enchants (original items, horizontal layout)
     */
    private void populateArmorEnchants() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));

            for (int tier = 1; tier <= maxTier && tier <= ARMOR_SLOTS.length; tier++) {
                ItemStack scroll = scrollManager.createArmorEnhancementScroll(tier);
                if (scroll != null) {
                    int price = calculateArmorScrollPrice(tier);
                    scroll = VendorUtils.addPriceToItem(scroll, price);

                    String description = "Tier " + tier + " armor enhancement - increases protection by +" + tier;

                    addShopItem(ARMOR_SLOTS[tier - 1], scroll, description);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error populating armor enchants", e);
        }
    }

    /**
     * Add navigation elements (keeping it simple)
     */
    private void addNavigationElements() {
        // Refresh button
        ItemStack refresh = new ItemStack(Material.CLOCK);
        ItemMeta meta = refresh.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Refresh Prices");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to refresh item prices",
                    ChatColor.GRAY + "Prices update based on market conditions"
            ));
            refresh.setItemMeta(meta);
        }

        MenuItem refreshItem = new MenuItem(refresh)
                .setClickHandler((p, s) -> {
                    if (isClickCooldownExpired(s)) {
                        updateClickCooldown(s);
                        refreshMenu();
                        p.sendMessage(ChatColor.GREEN + "Item prices refreshed!");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    }
                });
        setItem(49, refreshItem);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        meta = close.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Close this vendor menu"));
            close.setItemMeta(meta);
        }

        MenuItem closeItem = new MenuItem(close)
                .setClickHandler((p, s) -> p.closeInventory());
        setItem(53, closeItem);
    }

    /**
     * Enhanced shop item addition with detailed information (keeping original logic)
     */
    private void addShopItem(int slot, ItemStack itemStack, String description) {
        if (itemStack == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        try {
            // Enhanced item meta with description (original logic preserved)
            if ((Boolean) menuConfig.get("enableItemDescriptions") && description != null) {
                ItemMeta meta = itemStack.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Description:");
                    lore.add(ChatColor.GRAY + description);
                    lore.add("");
                    lore.add(ChatColor.GREEN + "Click to purchase!");
                    meta.setLore(lore);
                    itemStack.setItemMeta(meta);
                }
            }

            MenuItem menuItem = new MenuItem(itemStack)
                    .setClickHandler((p, s) -> handleItemPurchase(p, s, itemStack));

            setItem(slot, menuItem);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error adding shop item to slot " + slot, e);
        }
    }

    /**
     * Enhanced item purchase handling (keeping original logic)
     */
    private void handleItemPurchase(Player player, int slot, ItemStack item) {
        try {
            if (!isClickCooldownExpired(slot)) {
                return;
            }
            updateClickCooldown(slot);

            if (purchaseManager.isInPurchaseProcess(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You already have an active purchase. Complete it first or type 'cancel'.");
                return;
            }

            int price = PurchaseManager.getPriceFromLore(item);
            if (price <= 0) {
                player.sendMessage(ChatColor.RED + "This item is not available for purchase.");
                return;
            }

            player.closeInventory();

            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                    item.getItemMeta().getDisplayName() :
                    VendorUtils.formatVendorTypeName(item.getType().name());

            player.sendMessage(ChatColor.GREEN + "Initiating purchase for " + itemName + "...");
            purchaseManager.startPurchase(player, item, price);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling item purchase for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred while starting your purchase. Please try again.");
        }
    }

    // Keep all original price calculation methods unchanged
    private int calculateDynamicPrice(int basePrice, String itemKey) {
        if (!(Boolean) menuConfig.get("enableDynamicPricing")) {
            return basePrice;
        }

        try {
            long currentTime = System.currentTimeMillis();
            double timeFactor = Math.sin(currentTime / 3600000.0) * DYNAMIC_PRICE_VARIATION;
            double randomFactor = (Math.random() - 0.5) * DYNAMIC_PRICE_VARIATION;

            double priceMultiplier = 1.0 + timeFactor + randomFactor;
            priceMultiplier = Math.max(0.8, Math.min(1.2, priceMultiplier));

            return (int) Math.round(basePrice * priceMultiplier);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error calculating dynamic price for " + itemKey, e);
            return basePrice;
        }
    }

    private int calculateProtectionScrollPrice(int tier) {
        if (tier == 0) return calculateDynamicPrice(PROTECTION_SCROLL_BASE_PRICE, "protection_0");
        return calculateDynamicPrice((int) (PROTECTION_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier)), "protection_" + tier);
    }

    private int calculateWeaponScrollPrice(int tier) {
        return calculateDynamicPrice((int) (WEAPON_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier - 1)), "weapon_" + tier);
    }

    private int calculateArmorScrollPrice(int tier) {
        return calculateDynamicPrice((int) (ARMOR_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier - 1)), "armor_" + tier);
    }

    // Keep original cooldown management
    private boolean isClickCooldownExpired(int slot) {
        Long lastClick = lastClickTime.get(slot);
        if (lastClick == null) {
            return true;
        }
        return System.currentTimeMillis() - lastClick >= CLICK_COOLDOWN_MS;
    }

    private void updateClickCooldown(int slot) {
        lastClickTime.put(slot, System.currentTimeMillis());
    }

    /**
     * Fallback basic menu initialization (keeping original)
     */
    private void initializeBasicMenu() {
        setItem(4, createErrorNotice());
        setItem(22, VendorUtils.createSeparator(Material.BARRIER, ChatColor.RED + "Menu Error - Basic Mode"));
        setItem(53, createCloseButton());
    }

    private ItemStack createErrorNotice() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Menu Error");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "The vendor menu encountered an error",
                    ChatColor.GRAY + "Running in basic mode",
                    ChatColor.YELLOW + "Please contact an administrator"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Close this vendor menu"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Refresh menu with updated prices (keeping original logic)
     */
    private void refreshMenu() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < inventory.getSize(); i++) {
                        if (getItem(i) != null) {
                            setItem(i, (MenuItem) null);
                        }
                    }
                    initializeCleanMenu();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error refreshing vendor menu", e);
                }
            }
        }.runTask(plugin);
    }
}