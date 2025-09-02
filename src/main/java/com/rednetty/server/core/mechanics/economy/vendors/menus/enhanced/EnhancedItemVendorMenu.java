package com.rednetty.server.core.mechanics.economy.vendors.menus.enhanced;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.economy.EconomyManager;
import com.rednetty.server.core.mechanics.economy.vendors.base.AbstractVendorMenu;
import com.rednetty.server.core.mechanics.economy.vendors.base.VendorMenuConfig;
import com.rednetty.server.core.mechanics.economy.vendors.interfaces.VendorType;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.PricingStrategy;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.PurchaseStrategy;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.impl.GemPurchaseStrategy;
import com.rednetty.server.core.mechanics.economy.vendors.strategies.impl.StandardPricingStrategy;
import com.rednetty.server.core.mechanics.item.orb.OrbManager;
import com.rednetty.server.core.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.core.mechanics.economy.GemPouchManager;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.menu.MenuItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced Item Vendor Menu using the new architecture
 * 
 * This is a complete rewrite of the ItemVendorMenu using clean architecture principles:
 * - Extends AbstractVendorMenu for common functionality
 * - Uses strategy pattern for pricing and purchasing
 * - Configuration-driven layout and behavior
 * - Proper separation of concerns
 * - Comprehensive error handling
 * - Performance optimizations
 */
public class EnhancedItemVendorMenu extends AbstractVendorMenu {
    
    // Item managers
    private final ScrollManager scrollManager;
    private final OrbManager orbManager;
    private final GemPouchManager pouchManager;
    
    // Current state
    private ItemCategory currentCategory = ItemCategory.ORBS;
    private final Map<Integer, VendorItem> vendorItems = new HashMap<>();
    private final Map<Integer, Long> lastClickTime = new ConcurrentHashMap<>();
    
    // Configuration
    private final boolean t6Enabled;
    
    public EnhancedItemVendorMenu(Player player, VendorType vendorType, VendorMenuConfig config) {
        super(player, vendorType, config);
        
        // Initialize managers
        this.scrollManager = ScrollManager.getInstance();
        this.orbManager = OrbManager.getInstance();
        this.pouchManager = GemPouchManager.getInstance();
        
        // Load configuration
        this.t6Enabled = YakRealms.isT6Enabled();
    }
    
    @Override
    protected void initializeMenu() {
        clearMenu();
        vendorItems.clear();
        
        // Create menu layout
        createMenuBorders();
        createCategoryButtons();
        createCategoryHeader();
        displayCategoryItems();
        createNavigationElements();
    }
    
    @Override
    protected PricingStrategy createPricingStrategy() {
        return new StandardPricingStrategy(
            getConfig().isEnableDynamicPricing(),
            getConfig().getDynamicPriceVariation(),
            true // Enable bulk discounts
        );
    }
    
    @Override
    protected PurchaseStrategy createPurchaseStrategy() {
        return new GemPurchaseStrategy(
            YakRealms.getInstance().getEconomyManager(),
            YakPlayerManager.getInstance(),
            getLogger()
        );
    }
    
    @Override
    protected boolean onSlotClick(Player player, int slot) {
        // Check click cooldown
        if (!isClickCooldownExpired(slot)) {
            return false;
        }
        updateClickCooldown(slot);
        
        // Check if it's a vendor item
        VendorItem vendorItem = vendorItems.get(slot);
        if (vendorItem != null) {
            handleItemPurchase(player, vendorItem);
            return true;
        }
        
        // Check if it's a category button
        if (isCategorySlot(slot)) {
            handleCategorySelection(player, slot);
            return true;
        }
        
        return false;
    }
    
    /**
     * Create menu borders and decorative elements
     */
    private void createMenuBorders() {
        // Create border items
        ItemStack borderItem = createBorderItem();
        
        for (Integer slot : getConfig().getBorderSlots()) {
            setItem(slot, new MenuItem(borderItem));
        }
        
        // Create separator items
        ItemStack separatorItem = createSeparatorItem();
        List<Integer> separatorSlots = List.of(10, 19, 28, 37, 46);
        
        for (Integer slot : separatorSlots) {
            setItem(slot, new MenuItem(separatorItem));
        }
    }
    
    /**
     * Create category selection buttons
     */
    private void createCategoryButtons() {
        List<Integer> categorySlots = getConfig().getCategorySlots();
        int slotIndex = 0;
        
        for (ItemCategory category : ItemCategory.values()) {
            if (slotIndex >= categorySlots.size()) break;
            
            // Skip categories that aren't available
            if (category == ItemCategory.ARMOR && !t6Enabled && !hasArmorScrollsAvailable()) {
                continue;
            }
            
            ItemStack categoryItem = createCategoryItem(category);
            int slot = categorySlots.get(slotIndex);
            
            setItem(slot, new MenuItem(categoryItem));
            slotIndex++;
        }
    }
    
    /**
     * Create the header for the current category
     */
    private void createCategoryHeader() {
        ItemStack header = new ItemStack(currentCategory.getIcon());
        ItemMeta meta = header.getItemMeta();
        
        if (meta != null) {
            meta.displayName(createNonItalicText(
                "🛒 " + currentCategory.getDisplayName() + " Shop",
                currentCategory.getColor(),
                TextDecoration.BOLD
            ));
            
            List<Component> lore = Arrays.asList(
                Component.empty(),
                createNonItalicText(currentCategory.getDescription(), NamedTextColor.GRAY),
                Component.empty(),
                createNonItalicText("Browse and purchase items below", NamedTextColor.YELLOW)
            );
            
            meta.lore(lore);
            header.setItemMeta(meta);
        }
        
        setItem(4, new MenuItem(header));
    }
    
    /**
     * Display items for the current category
     */
    private void displayCategoryItems() {
        List<Integer> displaySlots = getConfig().getDisplaySlots();
        int slotIndex = 0;
        
        List<VendorItem> categoryItems = getItemsForCategory(currentCategory);
        
        for (VendorItem vendorItem : categoryItems) {
            if (slotIndex >= displaySlots.size()) break;
            
            int slot = displaySlots.get(slotIndex);
            ItemStack displayItem = createDisplayItem(vendorItem);
            
            setItem(slot, new MenuItem(displayItem));
            vendorItems.put(slot, vendorItem);
            
            slotIndex++;
        }
        
        // Add category information panel if there's space
        if (slotIndex < displaySlots.size()) {
            int infoSlot = displaySlots.get(displaySlots.size() - 1);
            ItemStack infoPanel = createCategoryInfoPanel(currentCategory);
            setItem(infoSlot, new MenuItem(infoPanel));
        }
    }
    
    /**
     * Create navigation elements (help, refresh, close)
     */
    private void createNavigationElements() {
        List<Integer> navSlots = getConfig().getNavigationSlots();
        if (navSlots.size() < 3) return;
        
        // Help button
        setItem(navSlots.get(0), new MenuItem(createHelpButton()));
        
        // Refresh button
        setItem(navSlots.get(1), new MenuItem(createRefreshButton()));
        
        // Close button
        setItem(navSlots.get(4), new MenuItem(createCloseButton()));
    }
    
    /**
     * Handle item purchase
     */
    private void handleItemPurchase(Player player, VendorItem vendorItem) {
        try {
            // Calculate price using strategy
            int price = getPricingStrategy().calculatePrice(
                vendorItem.getOriginalItem(), 
                player, 
                vendorItem.getBasePrice()
            );
            
            // Validate purchase using strategy
            var validation = getPurchaseStrategy().canPurchase(
                player, 
                vendorItem.getOriginalItem(), 
                1, 
                price
            );
            
            if (!validation.isValid()) {
                sendErrorMessage(player, validation.getErrorMessage());
                return;
            }
            
            // Close menu and process purchase
            close(player);
            
            sendInfoMessage(player, "Processing your purchase...");
            
            // Process purchase asynchronously
            getPurchaseStrategy().processPurchase(
                player, 
                vendorItem.getOriginalItem(), 
                1, 
                price
            ).thenAccept(result -> {
                if (result.isSuccess()) {
                    sendSuccessMessage(player, result.getMessage());
                    player.playSound(Sound.sound(
                        org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 
                        Sound.Source.PLAYER, 
                        1.0f, 
                        1.2f
                    ));
                } else {
                    sendErrorMessage(player, result.getMessage());
                }
            }).exceptionally(throwable -> {
                getLogger().warning("Error processing purchase: " + throwable.getMessage());
                sendErrorMessage(player, "An error occurred during purchase processing.");
                return null;
            });
            
        } catch (Exception e) {
            getLogger().warning("Error handling item purchase: " + e.getMessage());
            sendErrorMessage(player, "An error occurred while processing your purchase.");
        }
    }
    
    /**
     * Handle category selection
     */
    private void handleCategorySelection(Player player, int slot) {
        ItemCategory category = getCategoryFromSlot(slot);
        if (category != null && category != currentCategory) {
            currentCategory = category;
            
            player.playSound(Sound.sound(
                org.bukkit.Sound.UI_BUTTON_CLICK, 
                Sound.Source.PLAYER, 
                0.7f, 
                1.2f
            ));
            
            // Refresh menu content
            displayCategoryItems();
            createCategoryHeader();
        }
    }
    
    // Helper methods
    
    private boolean isClickCooldownExpired(int slot) {
        Long lastClick = lastClickTime.get(slot);
        if (lastClick == null) {
            return true;
        }
        return System.currentTimeMillis() - lastClick >= getConfig().getClickCooldownMs();
    }
    
    private void updateClickCooldown(int slot) {
        lastClickTime.put(slot, System.currentTimeMillis());
    }
    
    private boolean isCategorySlot(int slot) {
        return getConfig().getCategorySlots().contains(slot);
    }
    
    private ItemCategory getCategoryFromSlot(int slot) {
        List<Integer> categorySlots = getConfig().getCategorySlots();
        int index = categorySlots.indexOf(slot);
        
        if (index >= 0 && index < ItemCategory.values().length) {
            return ItemCategory.values()[index];
        }
        
        return null;
    }
    
    private List<VendorItem> getItemsForCategory(ItemCategory category) {
        List<VendorItem> items = new ArrayList<>();
        
        switch (category) {
            case ORBS:
                items.addAll(createOrbItems());
                break;
            case POUCHES:
                items.addAll(createPouchItems());
                break;
            case PROTECTION:
                items.addAll(createProtectionScrollItems());
                break;
            case WEAPON:
                items.addAll(createWeaponScrollItems());
                break;
            case ARMOR:
                items.addAll(createArmorScrollItems());
                break;
        }
        
        return items;
    }
    
    // Item creation methods (simplified for brevity)
    
    private List<VendorItem> createOrbItems() {
        List<VendorItem> items = new ArrayList<>();
        
        ItemStack normalOrb = orbManager.createNormalOrb(true);
        if (normalOrb != null) {
            items.add(new VendorItem(
                normalOrb, 
                getConfig().getBasePrices().getOrDefault("normal_orb", 1500),
                "A mystical orb that rerolls item statistics",
                ItemCategory.ORBS
            ));
        }
        
        ItemStack legendaryOrb = orbManager.createLegendaryOrb(true);
        if (legendaryOrb != null) {
            items.add(new VendorItem(
                legendaryOrb,
                getConfig().getBasePrices().getOrDefault("legendary_orb", 20000),
                "A legendary orb that guarantees high-tier results",
                ItemCategory.ORBS
            ));
        }
        
        return items;
    }
    
    private List<VendorItem> createPouchItems() {
        List<VendorItem> items = new ArrayList<>();
        
        for (int tier = 1; tier <= 3; tier++) {
            ItemStack pouch = GemPouchManager.createGemPouch(tier, true);
            if (pouch != null) {
                int capacity = tier * 333; // Approximate capacity
                items.add(new VendorItem(
                    pouch,
                    tier * 500,
                    String.format("Tier %d gem pouch with %d capacity", tier, capacity),
                    ItemCategory.POUCHES
                ));
            }
        }
        
        return items;
    }
    
    private List<VendorItem> createProtectionScrollItems() {
        List<VendorItem> items = new ArrayList<>();
        int maxTier = t6Enabled ? 6 : 5;
        
        for (int tier = 0; tier <= maxTier; tier++) {
            ItemStack scroll = scrollManager.createProtectionScroll(tier);
            if (scroll != null) {
                int basePrice = tier == 0 ? 1000 : (int)(1000 * Math.pow(2.25, tier));
                String description = tier == 0 ? 
                    "Basic protection scroll - prevents enchantment failures" :
                    String.format("Tier %d protection scroll for advanced protection", tier);
                    
                items.add(new VendorItem(scroll, basePrice, description, ItemCategory.PROTECTION));
            }
        }
        
        return items;
    }
    
    private List<VendorItem> createWeaponScrollItems() {
        List<VendorItem> items = new ArrayList<>();
        int maxTier = t6Enabled ? 6 : 5;
        
        for (int tier = 1; tier <= maxTier; tier++) {
            ItemStack scroll = scrollManager.createWeaponEnhancementScroll(tier);
            if (scroll != null) {
                int basePrice = (int)(150 * Math.pow(2.25, tier - 1));
                String description = String.format("Tier %d weapon enhancement scroll - increases damage by +%d", tier, tier);
                
                items.add(new VendorItem(scroll, basePrice, description, ItemCategory.WEAPON));
            }
        }
        
        return items;
    }
    
    private List<VendorItem> createArmorScrollItems() {
        List<VendorItem> items = new ArrayList<>();
        int maxTier = t6Enabled ? 6 : 5;
        
        for (int tier = 1; tier <= maxTier; tier++) {
            ItemStack scroll = scrollManager.createArmorEnhancementScroll(tier);
            if (scroll != null) {
                int basePrice = (int)(150 * Math.pow(2.25, tier - 1));
                String description = String.format("Tier %d armor enhancement scroll - increases protection by +%d", tier, tier);
                
                items.add(new VendorItem(scroll, basePrice, description, ItemCategory.ARMOR));
            }
        }
        
        return items;
    }
    
    private boolean hasArmorScrollsAvailable() {
        for (int tier = 1; tier <= 6; tier++) {
            if (scrollManager.createArmorEnhancementScroll(tier) != null) {
                return true;
            }
        }
        return false;
    }
    
    // UI Element creation methods
    
    private ItemStack createBorderItem() {
        ItemStack item = new ItemStack(getConfig().getBorderMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(createNonItalicText(getConfig().getBorderName(), NamedTextColor.GRAY));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createSeparatorItem() {
        ItemStack item = new ItemStack(getConfig().getSeparatorMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(createNonItalicText(" ", NamedTextColor.GRAY));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createCategoryItem(ItemCategory category) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.displayName(createNonItalicText(
                "▶ " + category.getDisplayName(),
                category.getColor()
            ));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(createNonItalicText(category.getDescription(), NamedTextColor.GRAY));
            lore.add(Component.empty());
            
            int itemCount = getItemsForCategory(category).size();
            lore.add(createNonItalicText("📦 Available items: ", NamedTextColor.YELLOW)
                .append(createNonItalicText(String.valueOf(itemCount), NamedTextColor.WHITE)));
            
            lore.add(Component.empty());
            if (currentCategory == category) {
                lore.add(createNonItalicText("✅ Currently Selected", NamedTextColor.GREEN));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(createNonItalicText("👆 Click to view items!", NamedTextColor.YELLOW));
            }
            
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createDisplayItem(VendorItem vendorItem) {
        ItemStack displayItem = vendorItem.getOriginalItem().clone();
        ItemMeta meta = displayItem.getItemMeta();
        
        if (meta != null) {
            List<Component> lore = meta.hasLore() ? 
                new ArrayList<>(meta.lore()) : new ArrayList<>();
            
            // Add vendor information
            lore.add(Component.empty());
            lore.add(createNonItalicText("Description:", NamedTextColor.YELLOW));
            
            String[] descLines = vendorItem.getDescription().split("\n");
            for (String line : descLines) {
                lore.add(createNonItalicText(line.trim(), NamedTextColor.GRAY));
            }
            
            lore.add(Component.empty());
            
            // Calculate price
            int price = getPricingStrategy().calculatePrice(
                vendorItem.getOriginalItem(), 
                player, 
                vendorItem.getBasePrice()
            );
            
            lore.add(createNonItalicText("▶ ", NamedTextColor.GOLD)
                .append(createNonItalicText("Price: ", NamedTextColor.GREEN))
                .append(createNonItalicText(getPricingStrategy().formatPrice(price), NamedTextColor.WHITE)));
                
            lore.add(createNonItalicText("▶ ", NamedTextColor.YELLOW)
                .append(createNonItalicText("Click to purchase!", NamedTextColor.GRAY)));
            
            meta.lore(lore);
            displayItem.setItemMeta(meta);
        }
        
        return displayItem;
    }
    
    private ItemStack createCategoryInfoPanel(ItemCategory category) {
        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = info.getItemMeta();
        
        if (meta != null) {
            meta.displayName(createNonItalicText(
                "📚 " + category.getDisplayName() + " Guide",
                NamedTextColor.LIGHT_PURPLE,
                TextDecoration.BOLD
            ));
            
            List<Component> lore = category.getInfoLore();
            meta.lore(lore);
            info.setItemMeta(meta);
        }
        
        return info;
    }
    
    private ItemStack createHelpButton() {
        ItemStack help = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = help.getItemMeta();
        
        if (meta != null) {
            meta.displayName(createNonItalicText("📖 Help & Guide", NamedTextColor.YELLOW, TextDecoration.BOLD));
            
            List<Component> lore = Arrays.asList(
                Component.empty(),
                createNonItalicText("How to Use This Shop:", NamedTextColor.GREEN),
                createNonItalicText("1. Click categories on the left", NamedTextColor.GRAY),
                createNonItalicText("2. Browse items in each category", NamedTextColor.GRAY),
                createNonItalicText("3. Click items to purchase them", NamedTextColor.GRAY),
                createNonItalicText("4. Confirm your purchases", NamedTextColor.GRAY)
            );
            
            meta.lore(lore);
            help.setItemMeta(meta);
        }
        
        return help;
    }
    
    private ItemStack createRefreshButton() {
        ItemStack refresh = new ItemStack(Material.CLOCK);
        ItemMeta meta = refresh.getItemMeta();
        
        if (meta != null) {
            meta.displayName(createNonItalicText("🔄 Refresh Prices", NamedTextColor.AQUA, TextDecoration.BOLD));
            
            List<Component> lore = Arrays.asList(
                Component.empty(),
                createNonItalicText("Click to refresh item prices", NamedTextColor.GRAY),
                createNonItalicText("Prices may change based on market conditions", NamedTextColor.GRAY)
            );
            
            meta.lore(lore);
            refresh.setItemMeta(meta);
        }
        
        return refresh;
    }
    
    private ItemStack createCloseButton() {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta meta = close.getItemMeta();
        
        if (meta != null) {
            meta.displayName(createNonItalicText("❌ Close Shop", NamedTextColor.RED, TextDecoration.BOLD));
            
            List<Component> lore = Arrays.asList(
                Component.empty(),
                createNonItalicText("Close the vendor menu", NamedTextColor.GRAY),
                Component.empty(),
                createNonItalicText("Thanks for shopping!", NamedTextColor.YELLOW)
            );
            
            meta.lore(lore);
            close.setItemMeta(meta);
        }
        
        return close;
    }
    
    /**
     * Item categories for organization
     */
    private enum ItemCategory {
        ORBS("Mystical Orbs", Material.MAGMA_CREAM, NamedTextColor.GOLD, "Magical orbs that reroll item statistics"),
        POUCHES("Gem Pouches", Material.BUNDLE, NamedTextColor.GREEN, "Store your gems safely in portable pouches"),
        PROTECTION("Protection Scrolls", Material.SHIELD, NamedTextColor.BLUE, "Protect items from enchantment failures"),
        WEAPON("Weapon Scrolls", Material.IRON_SWORD, NamedTextColor.RED, "Enhance your weapons with powerful scrolls"),
        ARMOR("Armor Scrolls", Material.IRON_CHESTPLATE, NamedTextColor.AQUA, "Strengthen your armor with defensive scrolls");
        
        private final String displayName;
        private final Material icon;
        private final NamedTextColor color;
        private final String description;
        
        ItemCategory(String displayName, Material icon, NamedTextColor color, String description) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
        public NamedTextColor getColor() { return color; }
        public String getDescription() { return description; }
        
        public List<Component> getInfoLore() {
            switch (this) {
                case ORBS:
                    return Arrays.asList(
                        Component.empty(),
                        Component.text("How Orbs Work:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Right-click on an item to use", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Rerolls all item statistics", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Normal: Random tier result", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Legendary: High-tier guaranteed", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("💡 Pro Tips:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Use on gear you plan to keep", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Save legendary orbs for best items", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    );
                case POUCHES:
                    return Arrays.asList(
                        Component.empty(),
                        Component.text("Pouch Capacities:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Tier 1: 333 gems", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Tier 2: 666 gems", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Tier 3: 1000 gems", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Benefits:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Keep gems safe on death", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("• Portable storage", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    );
                default:
                    return Arrays.asList(
                        Component.empty(),
                        Component.text("Category: " + displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                        Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    );
            }
        }
    }
    
    /**
     * Represents an item being sold by the vendor
     */
    private static class VendorItem {
        private final ItemStack originalItem;
        private final int basePrice;
        private final String description;
        private final ItemCategory category;
        
        public VendorItem(ItemStack originalItem, int basePrice, String description, ItemCategory category) {
            this.originalItem = originalItem.clone();
            this.basePrice = basePrice;
            this.description = description;
            this.category = category;
        }
        
        public ItemStack getOriginalItem() { return originalItem.clone(); }
        public int getBasePrice() { return basePrice; }
        public String getDescription() { return description; }
        public ItemCategory getCategory() { return category; }
    }
}