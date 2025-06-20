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
import org.bukkit.event.inventory.InventoryClickEvent;
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
 * Enhanced ItemVendorMenu with improved error handling, performance optimization,
 * and comprehensive item management. Provides a robust shopping experience with
 * detailed item information, dynamic pricing, and seamless purchase integration.
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
    private static final long CLICK_COOLDOWN_MS = 250; // 250ms between clicks

    // Enhanced pricing system with dynamic calculations
    private static final int NORMAL_ORB_BASE_PRICE = 1500;
    private static final int LEGENDARY_ORB_BASE_PRICE = 5000;
    private static final int PROTECTION_SCROLL_BASE_PRICE = 1000;
    private static final int WEAPON_SCROLL_BASE_PRICE = 250;
    private static final int ARMOR_SCROLL_BASE_PRICE = 250;
    private static final double TIER_MULTIPLIER = 2.5;
    private static final double DYNAMIC_PRICE_VARIATION = 0.1; // 10% price variation

    // Category definitions for better organization
    private enum ItemCategory {
        ORBS_AND_POUCHES(0, 17, "Magical Items", ChatColor.DARK_PURPLE),
        PROTECTION_SCROLLS(18, 26, "Protection Scrolls", ChatColor.BLUE),
        WEAPON_SCROLLS(27, 35, "Weapon Enchants", ChatColor.RED),
        ARMOR_SCROLLS(36, 44, "Armor Enchants", ChatColor.GREEN),
        NAVIGATION(45, 53, "Navigation", ChatColor.GRAY);

        public final int startSlot;
        public final int endSlot;
        public final String displayName;
        public final ChatColor color;

        ItemCategory(int startSlot, int endSlot, String displayName, ChatColor color) {
            this.startSlot = startSlot;
            this.endSlot = endSlot;
            this.displayName = displayName;
            this.color = color;
        }
    }

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
            initializeMenu();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error initializing ItemVendorMenu for player " + player.getName(), e);
            // Fallback: create a basic menu
            initializeBasicMenu();
        }
    }

    /**
     * Load menu configuration with defaults
     */
    private Map<String, Object> loadMenuConfiguration() {
        Map<String, Object> config = new HashMap<>();

        // Load from plugin config or use defaults
        config.put("enableDynamicPricing", plugin.getConfig().getBoolean("vendors.item-vendor.dynamic-pricing", false));
        config.put("enableItemDescriptions", plugin.getConfig().getBoolean("vendors.item-vendor.detailed-descriptions", true));
        config.put("enableQuickBuy", plugin.getConfig().getBoolean("vendors.item-vendor.quick-buy", true));
        config.put("maxTierEnabled", t6Enabled ? 6 : 5);
        config.put("enableStockLimits", plugin.getConfig().getBoolean("vendors.item-vendor.stock-limits", false));

        return config;
    }

    /**
     * Enhanced menu initialization with error handling
     */
    private void initializeMenu() {
        try {
            // Create category headers
            createCategoryHeaders();

            // Populate orbs and pouches
            populateOrbsAndPouches();

            // Populate protection scrolls
            populateProtectionScrolls();

            // Populate weapon enchants
            populateWeaponEnchants();

            // Populate armor enchants
            populateArmorEnchants();

            // Add navigation and information
            addNavigationElements();

            // Fill remaining slots with separators
            fillEmptySlots();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during menu initialization, falling back to basic menu", e);
            initializeBasicMenu();
        }
    }

    /**
     * Fallback basic menu initialization
     */
    private void initializeBasicMenu() {
        // Create a minimal functional menu
        setItem(4, createErrorNotice());
        setItem(22, VendorUtils.createSeparator(Material.BARRIER, ChatColor.RED + "Menu Error - Basic Mode"));
        setItem(53, createCloseButton());
    }

    /**
     * Create category headers for better organization
     */
    private void createCategoryHeaders() {
        for (ItemCategory category : ItemCategory.values()) {
            if (category == ItemCategory.NAVIGATION) continue;

            int headerSlot = category.startSlot + 4; // Center of the category row
            ItemStack header = createCategoryHeader(category);
            setItem(headerSlot, header);
        }
    }

    /**
     * Enhanced category header creation
     */
    private ItemStack createCategoryHeader(ItemCategory category) {
        Material iconMaterial;
        switch (category) {
            case ORBS_AND_POUCHES:
                iconMaterial = Material.ENDER_PEARL;
                break;
            case PROTECTION_SCROLLS:
                iconMaterial = Material.SHIELD;
                break;
            case WEAPON_SCROLLS:
                iconMaterial = Material.DIAMOND_SWORD;
                break;
            case ARMOR_SCROLLS:
                iconMaterial = Material.DIAMOND_CHESTPLATE;
                break;
            default:
                iconMaterial = Material.PAPER;
        }

        ItemStack header = new ItemStack(iconMaterial);
        ItemMeta meta = header.getItemMeta();
        meta.setDisplayName(category.color + "" + ChatColor.BOLD + category.displayName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Browse items in this category");
        lore.add(ChatColor.GRAY + "Click items below to purchase");

        // Add category-specific information
        switch (category) {
            case ORBS_AND_POUCHES:
                lore.add("");
                lore.add(ChatColor.YELLOW + "Orbs: " + ChatColor.WHITE + "Randomize item stats");
                lore.add(ChatColor.YELLOW + "Pouches: " + ChatColor.WHITE + "Store gems safely");
                break;
            case PROTECTION_SCROLLS:
                lore.add("");
                lore.add(ChatColor.YELLOW + "Protects items from destruction");
                lore.add(ChatColor.YELLOW + "when enchanting fails");
                break;
            case WEAPON_SCROLLS:
                lore.add("");
                lore.add(ChatColor.YELLOW + "Enhances weapon damage");
                lore.add(ChatColor.YELLOW + "Risk of destruction above +3");
                break;
            case ARMOR_SCROLLS:
                lore.add("");
                lore.add(ChatColor.YELLOW + "Enhances armor protection");
                lore.add(ChatColor.YELLOW + "Risk of destruction above +3");
                break;
        }

        meta.setLore(lore);
        header.setItemMeta(meta);
        return header;
    }

    /**
     * Enhanced orbs and pouches population
     */
    private void populateOrbsAndPouches() {
        try {
            // Orbs
            ItemStack normalOrb = orbManager.createNormalOrb(true);
            ItemStack legendaryOrb = orbManager.createLegendaryOrb(true);

            // Apply pricing with dynamic calculation
            normalOrb = VendorUtils.addPriceToItem(normalOrb, calculateDynamicPrice(NORMAL_ORB_BASE_PRICE, "normal_orb"));
            legendaryOrb = VendorUtils.addPriceToItem(legendaryOrb, calculateDynamicPrice(LEGENDARY_ORB_BASE_PRICE, "legendary_orb"));

            addShopItem(2, normalOrb, "A magical orb that randomizes item statistics");
            addShopItem(3, legendaryOrb, "A legendary orb that guarantees high-tier results");

            // Gem pouches
            int maxTier = (Integer) menuConfig.get("maxTierEnabled");
            for (int tier = 1; tier <= maxTier; tier++) {
                ItemStack pouch = pouchManager.createGemPouch(tier, true);
                if (pouch != null) {
                    addShopItem(10 + tier, pouch, "Tier " + tier + " gem pouch - stores up to " +
                            (tier * 1000) + " gems safely");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error populating orbs and pouches", e);
        }
    }

    /**
     * Enhanced protection scrolls population
     */
    private void populateProtectionScrolls() {
        try {
            int maxTier = (Integer) menuConfig.get("maxTierEnabled");

            for (int tier = 0; tier <= maxTier; tier++) {
                ItemStack scroll = scrollManager.createProtectionScroll(tier);
                if (scroll != null) {
                    int price = calculateProtectionScrollPrice(tier);
                    scroll = VendorUtils.addPriceToItem(scroll, price);

                    String description = tier == 0 ?
                            "Basic protection against enchanting failures" :
                            "Tier " + tier + " protection - prevents item destruction";

                    addShopItem(ItemCategory.PROTECTION_SCROLLS.startSlot + 1 + tier, scroll, description);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error populating protection scrolls", e);
        }
    }

    /**
     * Enhanced weapon enchants population
     */
    private void populateWeaponEnchants() {
        try {
            int maxTier = (Integer) menuConfig.get("maxTierEnabled");

            for (int tier = 1; tier <= maxTier; tier++) {
                ItemStack scroll = scrollManager.createWeaponEnhancementScroll(tier);
                if (scroll != null) {
                    int price = calculateWeaponScrollPrice(tier);
                    scroll = VendorUtils.addPriceToItem(scroll, price);

                    String description = "Tier " + tier + " weapon enhancement - increases damage by +" + tier;

                    addShopItem(ItemCategory.WEAPON_SCROLLS.startSlot + 1 + tier, scroll, description);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error populating weapon enchants", e);
        }
    }

    /**
     * Enhanced armor enchants population
     */
    private void populateArmorEnchants() {
        try {
            int maxTier = (Integer) menuConfig.get("maxTierEnabled");

            for (int tier = 1; tier <= maxTier; tier++) {
                ItemStack scroll = scrollManager.createArmorEnhancementScroll(tier);
                if (scroll != null) {
                    int price = calculateArmorScrollPrice(tier);
                    scroll = VendorUtils.addPriceToItem(scroll, price);

                    String description = "Tier " + tier + " armor enhancement - increases protection by +" + tier;

                    addShopItem(ItemCategory.ARMOR_SCROLLS.startSlot + 1 + tier, scroll, description);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error populating armor enchants", e);
        }
    }

    /**
     * Add navigation and information elements
     */
    private void addNavigationElements() {
        // Information buttons
        setItem(8, createInfoButton("Orb Information", Arrays.asList(
                ChatColor.GRAY + "Normal orbs randomize stats",
                ChatColor.GRAY + "Legendary orbs guarantee +4 or higher",
                ChatColor.GRAY + "Use orbs on equipment to reroll"
        )));

        setItem(17, createInfoButton("Pouch Information", Arrays.asList(
                ChatColor.GRAY + "Store gems safely in pouches",
                ChatColor.GRAY + "Higher tiers hold more gems",
                ChatColor.GRAY + "Right-click to deposit/withdraw"
        )));

        setItem(26, createInfoButton("Protection Information", Arrays.asList(
                ChatColor.GRAY + "Prevents item destruction",
                ChatColor.GRAY + "when enchanting fails",
                ChatColor.GRAY + "Higher tiers provide better protection"
        )));

        setItem(35, createInfoButton("Enhancement Information", Arrays.asList(
                ChatColor.GRAY + "Upgrade weapons and armor",
                ChatColor.GRAY + "Risk of destruction above +3",
                ChatColor.GRAY + "Use protection scrolls for safety"
        )));

        // Navigation buttons
        setItem(49, createRefreshButton());
        setItem(53, createCloseButton());
    }

    /**
     * Enhanced shop item addition with detailed information
     */
    private void addShopItem(int slot, ItemStack itemStack, String description) {
        if (itemStack == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        try {
            // Enhanced item meta with description
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
     * Enhanced item purchase handling with validation and cooldown
     */
    private void handleItemPurchase(Player player, int slot, ItemStack item) {
        try {
            // Check click cooldown
            if (!isClickCooldownExpired(slot)) {
                return;
            }
            updateClickCooldown(slot);

            // Validate purchase state
            if (purchaseManager.isInPurchaseProcess(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You already have an active purchase. Complete it first or type 'cancel'.");
                return;
            }

            // Extract price
            int price = VendorUtils.extractPriceFromLore(item);
            if (price <= 0) {
                player.sendMessage(ChatColor.RED + "This item is not available for purchase.");
                return;
            }

            // Close inventory and start purchase
            player.closeInventory();

            // Add purchase feedback
            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                    item.getItemMeta().getDisplayName() :
                    VendorUtils.formatVendorTypeName(item.getType().name());

            player.sendMessage(ChatColor.GREEN + "Initiating purchase for " + itemName + "...");

            // Start purchase process
            purchaseManager.startPurchase(player, item, price);

            // Play sound effect
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling item purchase for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred while starting your purchase. Please try again.");
        }
    }

    /**
     * Dynamic pricing calculation with market simulation
     */
    private int calculateDynamicPrice(int basePrice, String itemKey) {
        if (!(Boolean) menuConfig.get("enableDynamicPricing")) {
            return basePrice;
        }

        try {
            // Simple dynamic pricing based on time and randomness
            long currentTime = System.currentTimeMillis();
            double timeFactor = Math.sin(currentTime / 3600000.0) * DYNAMIC_PRICE_VARIATION; // Hourly cycle
            double randomFactor = (Math.random() - 0.5) * DYNAMIC_PRICE_VARIATION;

            double priceMultiplier = 1.0 + timeFactor + randomFactor;
            priceMultiplier = Math.max(0.8, Math.min(1.2, priceMultiplier)); // Clamp between 80% and 120%

            return (int) Math.round(basePrice * priceMultiplier);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error calculating dynamic price for " + itemKey, e);
            return basePrice;
        }
    }

    /**
     * Enhanced price calculation methods
     */
    private int calculateProtectionScrollPrice(int tier) {
        if (tier == 0) return calculateDynamicPrice(PROTECTION_SCROLL_BASE_PRICE, "protection_0");
        return calculateDynamicPrice((int) (PROTECTION_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier)),
                "protection_" + tier);
    }

    private int calculateWeaponScrollPrice(int tier) {
        return calculateDynamicPrice((int) (WEAPON_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier - 1)),
                "weapon_" + tier);
    }

    private int calculateArmorScrollPrice(int tier) {
        return calculateDynamicPrice((int) (ARMOR_SCROLL_BASE_PRICE * Math.pow(TIER_MULTIPLIER, tier - 1)),
                "armor_" + tier);
    }

    /**
     * Click cooldown management
     */
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
     * Enhanced UI element creation methods
     */
    private ItemStack createInfoButton(String title, List<String> information) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + title);
        meta.setLore(information);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRefreshButton() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Refresh Prices");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click to refresh item prices",
                ChatColor.GRAY + "Prices update based on market conditions"
        ));
        item.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(item)
                .setClickHandler((p, s) -> {
                    if (isClickCooldownExpired(s)) {
                        updateClickCooldown(s);
                        refreshMenu();
                        p.sendMessage(ChatColor.GREEN + "Item prices refreshed!");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    }
                });

        setItem(49, menuItem);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Close this vendor menu"));
        item.setItemMeta(meta);

        MenuItem menuItem = new MenuItem(item)
                .setClickHandler((p, s) -> p.closeInventory());

        setItem(53, menuItem);
        return item;
    }

    private ItemStack createErrorNotice() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Menu Error");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "The vendor menu encountered an error",
                ChatColor.GRAY + "Running in basic mode",
                ChatColor.YELLOW + "Please contact an administrator"
        ));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Fill empty slots with appropriate separators
     */
    private void fillEmptySlots() {
        for (ItemCategory category : ItemCategory.values()) {
            if (category == ItemCategory.NAVIGATION) continue;

            for (int i = category.startSlot; i <= category.endSlot; i++) {
                if (getItem(i) == null) {
                    setItem(i, VendorUtils.createColoredSeparator(category.color, " "));
                }
            }
        }
    }

    /**
     * Refresh menu with updated prices
     */
    private void refreshMenu() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Clear current items
                    for (int i = 0; i < inventory.getSize(); i++) {
                        if (getItem(i) != null) {
                            setItem(i, (MenuItem) null);
                        }
                    }

                    // Reinitialize menu
                    initializeMenu();

                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error refreshing vendor menu", e);
                }
            }
        }.runTask(plugin);
    }
}