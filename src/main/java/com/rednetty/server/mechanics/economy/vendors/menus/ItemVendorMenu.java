package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.GemPouchManager;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.sound.Sound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ItemVendorMenu that stores original clean items separately from display items.
 * Players receive the exact same items that the original item classes generate.
 * Updated to use Adventure API and modern Paper features.
 */
public class ItemVendorMenu extends Menu {

    private static final String TITLE = "Item Vendor";
    private static final int MENU_SIZE = 54; // 6 rows to accommodate all categories

    // Core managers
    private final ScrollManager scrollManager;
    private final OrbManager orbManager;
    private final GemPouchManager pouchManager;
    private final PurchaseManager purchaseManager;
    private final YakRealms plugin;

    // Layout configuration
    private static final int[] CATEGORY_SLOTS = {0, 9, 18, 27, 36, 45}; // Left column
    private final Map<Integer, ItemStack> originalItems = new HashMap<>(); // slot -> original clean item

    // Configuration
    private final boolean t6Enabled;
    private final Map<String, Object> menuConfig;

    // Performance tracking
    private final Map<Integer, Long> lastClickTime = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 250;

    // Pricing system
    private static final int NORMAL_ORB_BASE_PRICE = 1500;
    private static final int LEGENDARY_ORB_BASE_PRICE = 20000;
    private static final int PROTECTION_SCROLL_BASE_PRICE = 1000;
    private static final int WEAPON_SCROLL_BASE_PRICE = 150;
    private static final int ARMOR_SCROLL_BASE_PRICE = 150;
    private static final double TIER_MULTIPLIER = 2.25;
    private static final double DYNAMIC_PRICE_VARIATION = 0.1;
    private final Map<Integer, Integer> itemPrices = new HashMap<>(); // slot -> price

    private Category currentCategory = Category.ORBS;

    /**
     * Constructor with better error handling
     */
    public ItemVendorMenu(Player player) {
        super(player, TITLE, MENU_SIZE);

        this.plugin = YakRealms.getInstance();
        this.scrollManager = ScrollManager.getInstance();
        this.orbManager = OrbManager.getInstance();
        this.pouchManager = GemPouchManager.getInstance();
        this.purchaseManager = PurchaseManager.getInstance();
        this.t6Enabled = YakRealms.isT6Enabled();
        this.menuConfig = loadMenuConfiguration();

        try {
            initializeCategoryMenu();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error initializing ItemVendorMenu for player " + player.getName(), e);
            initializeBasicMenu();
        }
    }

    private static final int[] DISPLAY_SLOTS = {
            11, 12, 13, 14, 15, 16,     // Row 1 of display area
            20, 21, 22, 23, 24, 25,     // Row 2 of display area
            29, 30, 31, 32, 33, 34      // Row 3 of display area
    };
    private static final int[] BOTTOM_NAV_SLOTS = {49, 50, 51, 52, 53}; // Bottom navigation

    /**
     * Load menu configuration
     */
    private Map<String, Object> loadMenuConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("enableDynamicPricing", plugin.getConfig().getBoolean("vendors.item-vendor.dynamic-pricing", false));
        config.put("enableItemDescriptions", plugin.getConfig().getBoolean("vendors.item-vendor.detailed-descriptions", true));
        config.put("maxTierEnabled", t6Enabled ? 6 : 5);
        return config;
    }

    /**
     * Initialize the category-based menu
     */
    private void initializeCategoryMenu() {
        try {
            // Clear menu and stored data
            for (int i = 0; i < MENU_SIZE; i++) {
                setItem(i, (MenuItem) null);
            }
            originalItems.clear();
            itemPrices.clear();

            // Create borders and separators
            createBorders();

            // Create category buttons
            createCategoryButtons();

            // Display items for current category
            displayCategoryItems();

            // Add navigation elements
            addNavigationElements();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during category menu initialization", e);
            initializeBasicMenu();
        }
    }

    /**
     * Create borders for the menu
     */
    private void createBorders() {
        // Top border
        for (int i = 1; i <= 8; i++) {
            if (i != 4) { // Skip center for header
                setItem(i, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        // Vertical separator
        setItem(10, VendorUtils.createSeparator(Material.BLACK_STAINED_GLASS_PANE, " "));
        setItem(19, VendorUtils.createSeparator(Material.BLACK_STAINED_GLASS_PANE, " "));
        setItem(28, VendorUtils.createSeparator(Material.BLACK_STAINED_GLASS_PANE, " "));
        setItem(37, VendorUtils.createSeparator(Material.BLACK_STAINED_GLASS_PANE, " "));
        setItem(46, VendorUtils.createSeparator(Material.BLACK_STAINED_GLASS_PANE, " "));

        // Right border
        setItem(17, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(26, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(35, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(44, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));

        // Bottom border
        setItem(47, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(48, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    /**
     * Create category selection buttons
     */
    private void createCategoryButtons() {
        int index = 0;
        for (Category category : Category.values()) {
            if (index >= CATEGORY_SLOTS.length) break;

            // Skip armor category if not available
            if (category == Category.ARMOR && !t6Enabled && !hasArmorScrollsAvailable()) {
                continue;
            }

            ItemStack categoryItem = new ItemStack(category.icon);
            ItemMeta meta = categoryItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("▶ " + category.name)
                        .color(category.color));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text(category.description, NamedTextColor.GRAY));
                lore.add(Component.empty());

                // Add item count preview
                int itemCount = getItemCountForCategory(category);
                lore.add(Component.text("📦 Available items: ", NamedTextColor.YELLOW)
                        .append(Component.text(itemCount, NamedTextColor.WHITE)));

                lore.add(Component.empty());
                if (currentCategory == category) {
                    lore.add(Component.text("✅ Currently Selected", NamedTextColor.GREEN));
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add(Component.text("👆 Click to view items!", NamedTextColor.YELLOW));
                }

                meta.lore(lore);
                categoryItem.setItemMeta(meta);
            }

            final Category cat = category;
            MenuItem categoryMenuItem = new MenuItem(categoryItem)
                    .setClickHandler((p, s) -> {
                        if (isClickCooldownExpired(s)) {
                            updateClickCooldown(s);
                            if (currentCategory != cat) {
                                currentCategory = cat;
                                p.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK, Sound.Source.PLAYER, 0.7f, 1.2f));
                                initializeCategoryMenu();
                            }
                        }
                    });

            setItem(CATEGORY_SLOTS[index], categoryMenuItem);
            index++;
        }
    }

    /**
     * Display items based on selected category
     */
    private void displayCategoryItems() {
        // Clear display area first
        for (int slot : DISPLAY_SLOTS) {
            setItem(slot, (MenuItem) null);
        }

        // Add header for current category
        createCategoryHeader();

        // Display items based on category
        switch (currentCategory) {
            case ORBS:
                displayOrbs();
                break;
            case POUCHES:
                displayPouches();
                break;
            case PROTECTION:
                displayProtectionScrolls();
                break;
            case WEAPON:
                displayWeaponScrolls();
                break;
            case ARMOR:
                displayArmorScrolls();
                break;
        }
    }

    /**
     * Create category header
     */
    private void createCategoryHeader() {
        ItemStack header = new ItemStack(currentCategory.icon);
        ItemMeta meta = header.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("🛒 " + currentCategory.name + " Shop")
                    .color(currentCategory.color));

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    Component.text(currentCategory.description, NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Browse and purchase items below", NamedTextColor.YELLOW)
            );
            meta.lore(lore);
            header.setItemMeta(meta);
        }
        setItem(4, header);
    }

    /**
     * Display orbs with original item storage
     */
    private void displayOrbs() {
        try {
            // Normal Orb - store original and create display version
            ItemStack originalNormalOrb = orbManager.createNormalOrb(true);
            if (originalNormalOrb != null) {
                int price = calculateDynamicPrice(NORMAL_ORB_BASE_PRICE, "normal_orb");
                storeOriginalItemWithPrice(DISPLAY_SLOTS[0], originalNormalOrb, price);
                ItemStack displayItem = VendorUtils.createVendorDisplayItem(originalNormalOrb, price, "");
                addShopItemFromDisplay(DISPLAY_SLOTS[0], displayItem);
            }

            // Legendary Orb - store original and create display version
            ItemStack originalLegendaryOrb = orbManager.createLegendaryOrb(true);
            if (originalLegendaryOrb != null) {
                int price = calculateDynamicPrice(LEGENDARY_ORB_BASE_PRICE, "legendary_orb");
                String description = "A legendary orb that guarantees high-tier results\nBest for enhancing valuable equipment\nExperienced adventurers only";

                storeOriginalItemWithPrice(DISPLAY_SLOTS[1], originalLegendaryOrb, price);
                ItemStack displayItem = VendorUtils.createVendorDisplayItem(originalLegendaryOrb, price, "");
                addShopItemFromDisplay(DISPLAY_SLOTS[1], displayItem);
            }

            // Add orb information panel
            createOrbInfoPanel();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying orbs", e);
            createErrorItem(DISPLAY_SLOTS[0], "Failed to load orbs");
        }
    }

    /**
     * Display pouches with original item storage
     */
    private void displayPouches() {
        try {
            int displayIndex = 0;
            for (int tier = 1; tier <= 3 && displayIndex < DISPLAY_SLOTS.length; tier++) {
                ItemStack originalPouch = GemPouchManager.createGemPouch(tier, true);
                if (originalPouch != null) {
                    int price = calculatePouchPrice(tier);
                    int capacity = tier * 1000;

                    String description = String.format(
                            "Tier %d gem pouch with %s capacity\nKeeps gems safe on death\nEssential for all adventurers\nUpgrade from tier %d recommended",
                            tier, VendorUtils.formatNumber(capacity), Math.max(1, tier - 1)
                    );

                    int slot = DISPLAY_SLOTS[displayIndex];
                    storeOriginalItemWithPrice(slot, originalPouch, price);
                    ItemStack displayItem = VendorUtils.createVendorDisplayItem(originalPouch, price, "");
                    addShopItemFromDisplay(slot, displayItem);
                    displayIndex++;
                }
            }

            // Add pouch comparison chart
            createPouchComparisonChart();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying pouches", e);
            createErrorItem(DISPLAY_SLOTS[0], "Failed to load pouches");
        }
    }

    /**
     * Display protection scrolls with original item storage
     */
    private void displayProtectionScrolls() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));
            int displayIndex = 0;

            for (int tier = 0; tier <= maxTier && displayIndex < DISPLAY_SLOTS.length; tier++) {
                ItemStack originalScroll = scrollManager.createProtectionScroll(tier);
                if (originalScroll != null) {
                    int price = calculateProtectionScrollPrice(tier);

                    String description = tier == 0 ?
                            "Basic protection scroll\nPrevents enchantment failures\nSaves your items from destruction\nAffordable starter protection" :
                            String.format("Tier %d protection scroll\nAdvanced failure prevention\nSuitable for tier %d+ items\nProfessional grade protection", tier, tier);

                    int slot = DISPLAY_SLOTS[displayIndex];
                    storeOriginalItemWithPrice(slot, originalScroll, price);
                    ItemStack displayItem = VendorUtils.createVendorDisplayItem(originalScroll, price, "");
                    addShopItemFromDisplay(slot, displayItem);
                    displayIndex++;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying protection scrolls", e);
            createErrorItem(DISPLAY_SLOTS[0], "Failed to load protection scrolls");
        }
    }

    /**
     * Display weapon scrolls with original item storage
     */
    private void displayWeaponScrolls() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));
            int displayIndex = 0;

            for (int tier = 1; tier <= maxTier && displayIndex < DISPLAY_SLOTS.length; tier++) {
                ItemStack originalScroll = scrollManager.createWeaponEnhancementScroll(tier);
                if (originalScroll != null) {
                    int price = calculateWeaponScrollPrice(tier);

                    String description = String.format(
                            "Tier %d weapon enhancement scroll\nIncreases weapon damage by +%d\nPermanent upgrade to your weapon\nStackable with other enhancements",
                            tier, tier
                    );

                    int slot = DISPLAY_SLOTS[displayIndex];
                    storeOriginalItemWithPrice(slot, originalScroll, price);
                    ItemStack displayItem = VendorUtils.createVendorDisplayItem(originalScroll, price, "");
                    addShopItemFromDisplay(slot, displayItem);
                    displayIndex++;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying weapon scrolls", e);
            createErrorItem(DISPLAY_SLOTS[0], "Failed to load weapon scrolls");
        }
    }

    /**
     * Display armor scrolls with original item storage
     */
    private void displayArmorScrolls() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));
            int displayIndex = 0;

            for (int tier = 1; tier <= maxTier && displayIndex < DISPLAY_SLOTS.length; tier++) {
                ItemStack originalScroll = scrollManager.createArmorEnhancementScroll(tier);
                if (originalScroll != null) {
                    int price = calculateArmorScrollPrice(tier);

                    String description = String.format(
                            "Tier %d armor enhancement scroll\nIncreases armor protection by +%d\nPermanent defensive upgrade\nStackable with other enhancements",
                            tier, tier
                    );

                    int slot = DISPLAY_SLOTS[displayIndex];
                    storeOriginalItemWithPrice(slot, originalScroll, price);
                    ItemStack displayItem = VendorUtils.createVendorDisplayItem(originalScroll, price, "");
                    addShopItemFromDisplay(slot, displayItem);
                    displayIndex++;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying armor scrolls", e);
            createErrorItem(DISPLAY_SLOTS[0], "Failed to load armor scrolls");
        }
    }

    /**
     * Store original item and price for a slot
     */
    private void storeOriginalItemWithPrice(int slot, ItemStack originalItem, int price) {
        originalItems.put(slot, originalItem.clone());
        itemPrices.put(slot, price);
    }

    /**
     * Add shop item from display item
     */
    private void addShopItemFromDisplay(int slot, ItemStack displayItem) {
        if (displayItem == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        try {
            MenuItem menuItem = new MenuItem(displayItem)
                    .setClickHandler((p, s) -> handleItemPurchase(p, s));

            setItem(slot, menuItem);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error adding shop item to slot " + slot, e);
        }
    }

    /**
     * Item purchase handling using stored original items
     */
    private void handleItemPurchase(Player player, int slot) {
        try {
            if (!isClickCooldownExpired(slot)) {
                return;
            }
            updateClickCooldown(slot);

            if (purchaseManager.isInPurchaseProcess(player.getUniqueId())) {
                player.sendMessage(Component.text("⚠ You already have an active purchase. Complete it first or type ")
                        .color(NamedTextColor.RED)
                        .append(Component.text("'cancel'", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text(".", NamedTextColor.RED)));
                return;
            }

            // Get the original item and price from storage
            ItemStack originalItem = originalItems.get(slot);
            Integer price = itemPrices.get(slot);

            if (originalItem == null || price == null) {
                player.sendMessage(Component.text("❌ This item is not available for purchase.", NamedTextColor.RED));
                return;
            }

            player.closeInventory();

            // Get display name from the original item (not the vendor display item)
            String itemName = originalItem.hasItemMeta() && originalItem.getItemMeta().hasDisplayName() ?
                    ChatColor.stripColor(originalItem.getItemMeta().getDisplayName()) :
                    VendorUtils.formatVendorTypeName(originalItem.getType().name());

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("🛒 Starting purchase for ", NamedTextColor.GREEN)
                    .append(Component.text(itemName, NamedTextColor.WHITE))
                    .append(Component.text("...", NamedTextColor.GREEN)));
            player.sendMessage(Component.empty());

            // Use the original clean item directly (no need to clean since it's already clean)
            purchaseManager.startPurchase(player, originalItem.clone(), price);
            player.playSound(Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, Sound.Source.PLAYER, 1.0f, 1.2f));

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling item purchase for player " + player.getName(), e);
            player.sendMessage(Component.text("❌ An error occurred while starting your purchase. Please try again.", NamedTextColor.RED));
        }
    }

    /**
     * Create orb information panel
     */
    private void createOrbInfoPanel() {
        ItemStack orbInfo = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = orbInfo.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("📚 Orb Guide")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    Component.text("How Orbs Work:", NamedTextColor.GOLD),
                    Component.text("• Right-click on an item to use", NamedTextColor.GRAY),
                    Component.text("• Rerolls all item statistics", NamedTextColor.GRAY),
                    Component.text("• Normal: Random tier result", NamedTextColor.GRAY),
                    Component.text("• Legendary: High-tier guaranteed", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("💡 Pro Tips:", NamedTextColor.YELLOW),
                    Component.text("• Use on gear you plan to keep", NamedTextColor.GRAY),
                    Component.text("• Save legendary orbs for best items", NamedTextColor.GRAY),
                    Component.text("• Check item value before using", NamedTextColor.GRAY)
            );
            meta.lore(lore);
            orbInfo.setItemMeta(meta);
        }
        setItem(DISPLAY_SLOTS[5], orbInfo);
    }

    /**
     * Create pouch comparison chart
     */
    private void createPouchComparisonChart() {
        ItemStack pouchInfo = new ItemStack(Material.BOOK);
        ItemMeta meta = pouchInfo.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("📊 Pouch Comparison")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    Component.text("Pouch Capacities:", NamedTextColor.GOLD),
                    Component.text("• Tier 1: ", NamedTextColor.GRAY)
                            .append(Component.text("200 gems", NamedTextColor.WHITE)),
                    Component.text("• Tier 2: ", NamedTextColor.GRAY)
                            .append(Component.text("350 gems", NamedTextColor.WHITE)),
                    Component.text("• Tier 3: ", NamedTextColor.GRAY)
                            .append(Component.text("500 gems", NamedTextColor.WHITE))
            );
            meta.lore(lore);
            pouchInfo.setItemMeta(meta);
        }
        setItem(DISPLAY_SLOTS[5], pouchInfo);
    }

    /**
     * Add navigation elements
     */
    private void addNavigationElements() {
        // Help button
        ItemStack help = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = help.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("📖 Help & Guide")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    Component.text("How to Use This Shop:", NamedTextColor.GREEN),
                    Component.text("1. Click categories on the left", NamedTextColor.GRAY),
                    Component.text("2. Browse items in each category", NamedTextColor.GRAY),
                    Component.text("3. Click items to purchase them", NamedTextColor.GRAY),
                    Component.text("4. Follow the purchase prompts", NamedTextColor.GRAY)
            );
            meta.lore(lore);
            help.setItemMeta(meta);
        }
        setItem(49, help);

        // Refresh button
        ItemStack refresh = new ItemStack(Material.CLOCK);
        meta = refresh.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("🔄 Refresh Prices")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    Component.text("Click to refresh item prices", NamedTextColor.GRAY),
                    Component.text("Prices may change based on market conditions", NamedTextColor.GRAY)
            );
            meta.lore(lore);
            refresh.setItemMeta(meta);
        }

        MenuItem refreshItem = new MenuItem(refresh)
                .setClickHandler((p, s) -> {
                    if (isClickCooldownExpired(s)) {
                        updateClickCooldown(s);
                        initializeCategoryMenu();
                        p.sendMessage(Component.text("💰 Item prices refreshed!", NamedTextColor.GREEN));
                        p.playSound(Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, Sound.Source.PLAYER, 1.0f, 1.0f));
                    }
                });
        setItem(51, refreshItem);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        meta = close.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("❌ Close Shop")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    Component.text("Close the vendor menu", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Thanks for shopping!", NamedTextColor.YELLOW)
            );
            meta.lore(lore);
            close.setItemMeta(meta);
        }

        MenuItem closeItem = new MenuItem(close)
                .setClickHandler((p, s) -> {
                    p.closeInventory();
                    p.sendMessage(Component.text("👋 Thanks for visiting the Item Vendor!", NamedTextColor.GREEN));
                });
        setItem(53, closeItem);
    }

    /**
     * Create error item for failed loads
     */
    private void createErrorItem(int slot, String errorMsg) {
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("⚠ " + errorMsg, NamedTextColor.RED));

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    Component.text("This item failed to load properly", NamedTextColor.GRAY),
                    Component.text("Please contact an administrator", NamedTextColor.GRAY)
            );
            meta.lore(lore);
            errorItem.setItemMeta(meta);
        }
        setItem(slot, errorItem);
    }

    /**
     * Get item count for category preview
     */
    private int getItemCountForCategory(Category category) {
        switch (category) {
            case ORBS:
                return 2;
            case POUCHES:
                return 3;
            case PROTECTION:
                return Math.min(7, (Integer) menuConfig.get("maxTierEnabled") + 1);
            case WEAPON:
                return Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));
            case ARMOR:
                return hasArmorScrollsAvailable() ? Math.min(6, (Integer) menuConfig.get("maxTierEnabled")) : 0;
            default:
                return 0;
        }
    }

    /**
     * Calculate pouch price based on tier
     */
    private int calculatePouchPrice(int tier) {
        return calculateDynamicPrice(tier * 500, "pouch_" + tier);
    }

    /**
     * Check if armor scrolls are available
     */
    private boolean hasArmorScrollsAvailable() {
        for (int tier = 1; tier <= 6; tier++) {
            ItemStack scroll = scrollManager.createArmorEnhancementScroll(tier);
            if (scroll != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate dynamic price with market variations
     */
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

    /**
     * Calculate protection scroll price based on tier
     */
    private int calculateProtectionScrollPrice(int tier) {
        if (tier == 0) return calculateDynamicPrice(PROTECTION_SCROLL_BASE_PRICE, "protection_0");
        return calculateDynamicPrice((int) (PROTECTION_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier)), "protection_" + tier);
    }

    /**
     * Calculate weapon scroll price based on tier
     */
    private int calculateWeaponScrollPrice(int tier) {
        return calculateDynamicPrice((int) (WEAPON_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier - 1)), "weapon_" + tier);
    }

    /**
     * Calculate armor scroll price based on tier
     */
    private int calculateArmorScrollPrice(int tier) {
        return calculateDynamicPrice((int) (ARMOR_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier - 1)), "armor_" + tier);
    }

    /**
     * Fallback basic menu initialization
     */
    private void initializeBasicMenu() {
        setItem(22, createBasicErrorNotice());
        setItem(49, createBasicCloseButton());
    }

    /**
     * Check if click cooldown has expired for a slot
     */
    private boolean isClickCooldownExpired(int slot) {
        Long lastClick = lastClickTime.get(slot);
        if (lastClick == null) {
            return true;
        }
        return System.currentTimeMillis() - lastClick >= CLICK_COOLDOWN_MS;
    }

    /**
     * Update click cooldown for a slot
     */
    private void updateClickCooldown(int slot) {
        lastClickTime.put(slot, System.currentTimeMillis());
    }

    /**
     * Create basic error notice item
     */
    private ItemStack createBasicErrorNotice() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("⚠ Menu Error")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    Component.text("The vendor menu encountered an error", NamedTextColor.GRAY),
                    Component.text("Running in basic mode", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Please contact an administrator", NamedTextColor.YELLOW)
            );
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create basic close button
     */
    private ItemStack createBasicCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("❌ Close")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = Arrays.asList(
                    Component.text("Close this vendor menu", NamedTextColor.GRAY)
            );
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Category enumeration for menu organization
     */
    private enum Category {
        ORBS("Mystical Orbs", Material.MAGMA_CREAM, NamedTextColor.GOLD, "Magical orbs that reroll item statistics"),
        POUCHES("Gem Pouches", Material.BUNDLE, NamedTextColor.GREEN, "Store your gems safely in portable pouches"),
        PROTECTION("Protection Scrolls", Material.SHIELD, NamedTextColor.BLUE, "Protect items from enchantment failures"),
        WEAPON("Weapon Scrolls", Material.IRON_SWORD, NamedTextColor.RED, "Enhance your weapons with powerful scrolls"),
        ARMOR("Armor Scrolls", Material.IRON_CHESTPLATE, NamedTextColor.AQUA, "Strengthen your armor with defensive scrolls");

        private final String name;
        private final Material icon;
        private final NamedTextColor color;
        private final String description;

        Category(String name, Material icon, NamedTextColor color, String description) {
            this.name = name;
            this.icon = icon;
            this.color = color;
            this.description = description;
        }
    }
}