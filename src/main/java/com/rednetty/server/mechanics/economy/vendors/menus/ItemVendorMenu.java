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
 * Category-based ItemVendorMenu with compact, intuitive design.
 * Features clickable categories that dynamically change displayed items.
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

    // Configuration
    private final boolean t6Enabled;
    private final Map<String, Object> menuConfig;

    // Performance tracking
    private final Map<Integer, Long> lastClickTime = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 250;

    // Enhanced pricing system
    private static final int NORMAL_ORB_BASE_PRICE = 1500;
    private static final int LEGENDARY_ORB_BASE_PRICE = 20000;
    private static final int PROTECTION_SCROLL_BASE_PRICE = 1000;
    private static final int WEAPON_SCROLL_BASE_PRICE = 150;
    private static final int ARMOR_SCROLL_BASE_PRICE = 150;
    private static final double TIER_MULTIPLIER = 2.25;
    private static final double DYNAMIC_PRICE_VARIATION = 0.1;

    // Categories
    private enum Category {
        ORBS("Mystical Orbs", Material.MAGMA_CREAM, ChatColor.GOLD),
        POUCHES("Gem Pouches", Material.BUNDLE, ChatColor.GREEN),
        PROTECTION("Protection Scrolls", Material.SHIELD, ChatColor.BLUE),
        WEAPON("Weapon Scrolls", Material.IRON_SWORD, ChatColor.RED),
        ARMOR("Armor Scrolls", Material.IRON_CHESTPLATE, ChatColor.AQUA);

        private final String name;
        private final Material icon;
        private final ChatColor color;

        Category(String name, Material icon, ChatColor color) {
            this.name = name;
            this.icon = icon;
            this.color = color;
        }
    }

    private Category currentCategory = Category.ORBS;

    // Layout configuration
    private static final int[] CATEGORY_SLOTS = {0, 9, 18, 27, 36, 45}; // Left column for all 6 categories
    private static final int[] DISPLAY_SLOTS = {
            11, 12, 13, 14, 15, 16,     // Row 1 of display area
            20, 21, 22, 23, 24, 25,     // Row 2 of display area
            29, 30, 31, 32, 33, 34      // Row 3 of display area
    };
    private static final int[] BOTTOM_NAV_SLOTS = {49, 50, 51, 52, 53}; // Bottom navigation

    /**
     * Constructor with category-based initialization
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
     * Initialize the category-based menu
     */
    private void initializeCategoryMenu() {
        try {
            // Clear menu
            for (int i = 0; i < MENU_SIZE; i++) {
                setItem(i, (MenuItem) null);
            }

            // Create borders and separators
            createCompactBorders();

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
     * Create clean borders for compact menu
     */
    private void createCompactBorders() {
        // Top border
        for (int i = 1; i <= 8; i++) {
            if (i != 4) { // Skip center for potential header
                setItem(i, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        // Vertical separator between categories and items
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

        // Bottom border around navigation
        setItem(38, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(39, VendorUtils.createSeparator(Material.GRAY_STAINED_GLASS_PANE, " "));
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

            // Skip armor category if T6 is not enabled and we don't have T5 armor scrolls
            if (category == Category.ARMOR && !t6Enabled && !hasArmorScrollsAvailable()) {
                continue;
            }

            ItemStack categoryItem = new ItemStack(category.icon);
            ItemMeta meta = categoryItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(category.color + "" + ChatColor.BOLD + category.name);

                List<String> lore = new ArrayList<>();
                lore.add("");

                // Add category-specific descriptions
                switch (category) {
                    case ORBS:
                        lore.add(ChatColor.GRAY + "Magical orbs that reroll");
                        lore.add(ChatColor.GRAY + "item statistics");
                        break;
                    case POUCHES:
                        lore.add(ChatColor.GRAY + "Store your gems safely");
                        lore.add(ChatColor.GRAY + "in portable pouches");
                        break;
                    case PROTECTION:
                        lore.add(ChatColor.GRAY + "Protect items from");
                        lore.add(ChatColor.GRAY + "enchantment failures");
                        break;
                    case WEAPON:
                        lore.add(ChatColor.GRAY + "Enhance your weapons");
                        lore.add(ChatColor.GRAY + "with powerful scrolls");
                        break;
                    case ARMOR:
                        lore.add(ChatColor.GRAY + "Strengthen your armor");
                        lore.add(ChatColor.GRAY + "with defensive scrolls");
                        break;
                }

                lore.add("");
                if (currentCategory == category) {
                    lore.add(ChatColor.GREEN + "▶ Currently Selected");
                    // Add enchantment glow effect for selected category
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add(ChatColor.YELLOW + "Click to view items!");
                }

                meta.setLore(lore);
                categoryItem.setItemMeta(meta);
            }

            final Category cat = category;
            MenuItem categoryMenuItem = new MenuItem(categoryItem)
                    .setClickHandler((p, s) -> {
                        if (isClickCooldownExpired(s)) {
                            updateClickCooldown(s);
                            if (currentCategory != cat) {
                                currentCategory = cat;
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
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
        ItemStack header = new ItemStack(currentCategory.icon);
        ItemMeta meta = header.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(currentCategory.color + "" + ChatColor.BOLD + currentCategory.name);
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Browse available " + currentCategory.name.toLowerCase()
            ));
            header.setItemMeta(meta);
        }
        setItem(4, header);

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
     * Display orb items
     */
    private void displayOrbs() {
        try {
            // Normal Orb
            ItemStack normalOrb = orbManager.createNormalOrb(true);
            int normalOrbPrice = calculateDynamicPrice(NORMAL_ORB_BASE_PRICE, "normal_orb");
            addShopItem(DISPLAY_SLOTS[0], normalOrb, normalOrbPrice, "A magical orb that randomizes item statistics");

            // Legendary Orb
            ItemStack legendaryOrb = orbManager.createLegendaryOrb(true);
            int legendaryOrbPrice = calculateDynamicPrice(LEGENDARY_ORB_BASE_PRICE, "legendary_orb");
            addShopItem(DISPLAY_SLOTS[1], legendaryOrb, legendaryOrbPrice, "A legendary orb that guarantees high-tier results");

            // Add decorative elements or info
            ItemStack orbInfo = new ItemStack(Material.BOOK);
            ItemMeta meta = orbInfo.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Orb Information");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Orbs can be used on items to:",
                        ChatColor.GRAY + "• Reroll all statistics",
                        ChatColor.GRAY + "• Change item tier (Normal orb)",
                        ChatColor.GRAY + "• Guarantee high tier (Legendary)",
                        "",
                        ChatColor.YELLOW + "Use wisely!"
                ));
                orbInfo.setItemMeta(meta);
            }
            setItem(DISPLAY_SLOTS[5], orbInfo);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying orbs", e);
        }
    }

    /**
     * Display gem pouches
     */
    private void displayPouches() {
        try {
            int displayIndex = 0;
            for (int tier = 1; tier <= 3 && displayIndex < DISPLAY_SLOTS.length; tier++) {
                ItemStack pouch = pouchManager.createGemPouch(tier, true);
                if (pouch != null) {
                    // Calculate price based on tier
                    int price = tier * 500; // Base price scaling with tier
                    addShopItem(DISPLAY_SLOTS[displayIndex], pouch, price,
                            "Tier " + tier + " gem pouch - stores up to " + (tier * 1000) + " gems safely");
                    displayIndex++;
                }
            }

            // Add pouch info
            ItemStack pouchInfo = new ItemStack(Material.BOOK);
            ItemMeta meta = pouchInfo.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + "Pouch Information");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Gem pouches allow you to:",
                        ChatColor.GRAY + "• Store gems safely",
                        ChatColor.GRAY + "• Prevent gem loss on death",
                        ChatColor.GRAY + "• Higher tiers = more capacity",
                        "",
                        ChatColor.YELLOW + "Essential for adventurers!"
                ));
                pouchInfo.setItemMeta(meta);
            }
            setItem(DISPLAY_SLOTS[5], pouchInfo);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying pouches", e);
        }
    }

    /**
     * Display divider items
     */
    private void displayDividers() {
        try {
            // Basic Divider
            ItemStack basicDivider = new ItemStack(Material.PAPER);
            ItemMeta meta = basicDivider.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Basic Divider");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Organize your inventory",
                        ChatColor.GRAY + "with visual separators"
                ));
                basicDivider.setItemMeta(meta);
            }
            addShopItem(DISPLAY_SLOTS[0], basicDivider, 50, "A simple divider to organize your inventory");

            // Premium Divider
            ItemStack premiumDivider = new ItemStack(Material.PAPER);
            meta = premiumDivider.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Premium Divider");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "A fancier divider",
                        ChatColor.GRAY + "for better organization"
                ));
                premiumDivider.setItemMeta(meta);
            }
            addShopItem(DISPLAY_SLOTS[1], premiumDivider, 150, "A premium divider with style");

            // Divider info
            ItemStack dividerInfo = new ItemStack(Material.BOOK);
            meta = dividerInfo.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Divider Information");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Dividers help you:",
                        ChatColor.GRAY + "• Organize inventory sections",
                        ChatColor.GRAY + "• Create visual boundaries",
                        ChatColor.GRAY + "• Keep items separated",
                        "",
                        ChatColor.YELLOW + "Perfect for neat inventories!"
                ));
                dividerInfo.setItemMeta(meta);
            }
            setItem(DISPLAY_SLOTS[5], dividerInfo);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying dividers", e);
        }
    }

    /**
     * Display protection scrolls
     */
    private void displayProtectionScrolls() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));
            int displayIndex = 0;

            for (int tier = 0; tier <= maxTier && displayIndex < DISPLAY_SLOTS.length; tier++) {
                ItemStack scroll = scrollManager.createProtectionScroll(tier);
                if (scroll != null) {
                    int price = calculateProtectionScrollPrice(tier);

                    String description = tier == 0 ?
                            "Basic protection against enchanting failures" :
                            "Tier " + tier + " protection - prevents item destruction";

                    addShopItem(DISPLAY_SLOTS[displayIndex], scroll, price, description);
                    displayIndex++;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying protection scrolls", e);
        }
    }

    /**
     * Display weapon enhancement scrolls
     */
    private void displayWeaponScrolls() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));
            int displayIndex = 0;

            for (int tier = 1; tier <= maxTier && displayIndex < DISPLAY_SLOTS.length; tier++) {
                ItemStack scroll = scrollManager.createWeaponEnhancementScroll(tier);
                if (scroll != null) {
                    int price = calculateWeaponScrollPrice(tier);

                    String description = "Tier " + tier + " weapon enhancement - increases damage by +" + tier;

                    addShopItem(DISPLAY_SLOTS[displayIndex], scroll, price, description);
                    displayIndex++;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying weapon scrolls", e);
        }
    }

    /**
     * Display armor enhancement scrolls
     */
    private void displayArmorScrolls() {
        try {
            int maxTier = Math.min(6, (Integer) menuConfig.get("maxTierEnabled"));
            int displayIndex = 0;

            for (int tier = 1; tier <= maxTier && displayIndex < DISPLAY_SLOTS.length; tier++) {
                ItemStack scroll = scrollManager.createArmorEnhancementScroll(tier);
                if (scroll != null) {
                    int price = calculateArmorScrollPrice(tier);

                    String description = "Tier " + tier + " armor enhancement - increases protection by +" + tier;

                    addShopItem(DISPLAY_SLOTS[displayIndex], scroll, price, description);
                    displayIndex++;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error displaying armor scrolls", e);
        }
    }

    /**
     * Add navigation elements at the bottom
     */
    private void addNavigationElements() {
        // Help button
        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta meta = help.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Help");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click categories on the left",
                    ChatColor.GRAY + "to browse different items.",
                    ChatColor.GRAY + "Click items to purchase them!"
            ));
            help.setItemMeta(meta);
        }
        setItem(49, help);

        // Refresh prices button
        ItemStack refresh = new ItemStack(Material.CLOCK);
        meta = refresh.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Refresh Prices");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to refresh item prices",
                    ChatColor.GRAY + "Prices update based on market"
            ));
            refresh.setItemMeta(meta);
        }

        MenuItem refreshItem = new MenuItem(refresh)
                .setClickHandler((p, s) -> {
                    if (isClickCooldownExpired(s)) {
                        updateClickCooldown(s);
                        initializeCategoryMenu();
                        p.sendMessage(ChatColor.GREEN + "Item prices refreshed!");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    }
                });
        setItem(51, refreshItem);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        meta = close.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Close vendor menu"));
            close.setItemMeta(meta);
        }

        MenuItem closeItem = new MenuItem(close)
                .setClickHandler((p, s) -> p.closeInventory());
        setItem(53, closeItem);
    }

    /**
     * Check if armor scrolls are available
     */
    private boolean hasArmorScrollsAvailable() {
        // Check if any armor scrolls can be created
        for (int tier = 1; tier <= 6; tier++) {
            ItemStack scroll = scrollManager.createArmorEnhancementScroll(tier);
            if (scroll != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enhanced shop item addition with unified vendor lore management
     */
    private void addShopItem(int slot, ItemStack itemStack, int price, String description) {
        if (itemStack == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        try {
            // Create a clean copy of the item first (removes any existing vendor lore)
            ItemStack cleanItem = VendorUtils.createCleanItemCopy(itemStack);

            // Create comprehensive vendor lore in one place
            ItemMeta meta = cleanItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                // Add description section if enabled and description provided
                if ((Boolean) menuConfig.get("enableItemDescriptions") && description != null) {
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Description:");
                    lore.add(ChatColor.GRAY + description);
                }

                // Add vendor section with proper formatting
                lore.add("");
                lore.add(ChatColor.GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + VendorUtils.formatNumber(price) + "g");
                lore.add(ChatColor.GREEN + "⚡ Click to purchase!");

                // Add quick buy hint if enabled
                if ((Boolean) menuConfig.get("enableQuickBuy")) {
                    lore.add(ChatColor.GRAY + "⚡ Shift-click for quick buy");
                }

                lore.add(ChatColor.GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                meta.setLore(lore);
                cleanItem.setItemMeta(meta);
            }

            MenuItem menuItem = new MenuItem(cleanItem)
                    .setClickHandler((p, s) -> handleItemPurchase(p, s, cleanItem));

            setItem(slot, menuItem);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error adding shop item to slot " + slot, e);
        }
    }

    /**
     * Enhanced item purchase handling
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

            int price = VendorUtils.extractPriceFromLore(item);
            if (price <= 0) {
                player.sendMessage(ChatColor.RED + "This item is not available for purchase.");
                return;
            }

            player.closeInventory();

            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                    item.getItemMeta().getDisplayName() :
                    VendorUtils.formatVendorTypeName(item.getType().name());

            player.sendMessage(ChatColor.GREEN + "Initiating purchase for " + itemName + "...");

            // Create a clean item copy for the purchase manager
            ItemStack cleanItemForPurchase = VendorUtils.createCleanItemCopy(item);
            purchaseManager.startPurchase(player, cleanItemForPurchase, price);
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
     * Fallback basic menu initialization
     */
    private void initializeBasicMenu() {
        setItem(22, createErrorNotice());
        setItem(49, createCloseButton());
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
}