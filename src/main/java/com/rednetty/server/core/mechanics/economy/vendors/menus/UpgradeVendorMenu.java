package com.rednetty.server.core.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.core.mechanics.economy.vendors.utils.VendorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 *  Upgrade Vendor menu template with consistent formatting
 * This class provides a foundation for upgrade-related vendor functionality
 * READY FOR: Equipment upgrades, enhancement scrolls, or other upgrade services
 */
public class UpgradeVendorMenu implements Listener {

    private static final String INVENTORY_TITLE = "⚡ Enhancement Workshop";
    private static final int INVENTORY_SIZE = 45; // 5 rows for upgrades

    // Upgrade pricing structure (can be customized based on implementation needs)
    private static final int[] UPGRADE_PRICES = {100, 250, 500, 1000, 2000, 5000}; // Tier 1-6 pricing

    private final Player player;
    private final Inventory inventory;
    private final PurchaseManager purchaseManager;
    private final YakRealms plugin;

    /**
     * Constructor for the Upgrade Vendor menu
     */
    public UpgradeVendorMenu(Player player) {
        this.player = player;
        this.plugin = YakRealms.getInstance();
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        this.purchaseManager = PurchaseManager.getInstance();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Initialize inventory
        try {
            setupInventory();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up UpgradeVendorMenu for player " + player.getName(), e);
            setupFallbackInventory();
        }
    }

    /**
     * Setup the  inventory with upgrade workshop theme
     */
    private void setupInventory() {
        //  category label
        inventory.setItem(4, createCategoryLabel("Enhancement Services", Material.ANVIL));

        // Create upgrade service examples (customize based on your server's needs)
        createUpgradeServices();

        //  decorative elements
        createWorkshopDecorations();

        // Information and navigation
        inventory.setItem(40, createInfoButton());
        inventory.setItem(44, createCloseButton());
    }

    /**
     * Create upgrade services (customize this based on your server's upgrade system)
     */
    private void createUpgradeServices() {
        // Example upgrade services - customize these based on your needs

        // Weapon Enhancement Service
        ItemStack weaponUpgrade = createUpgradeService(
                Material.DIAMOND_SWORD,
                "⚔ Weapon Enhancement",
                "Enhance your weapons with magical power\nIncreases damage and adds special effects\nCompatible with all weapon types",
                500
        );
        inventory.setItem(11, weaponUpgrade);

        // Armor Enhancement Service
        ItemStack armorUpgrade = createUpgradeService(
                Material.DIAMOND_CHESTPLATE,
                "🛡 Armor Enhancement",
                "Strengthen your armor with protective magic\nIncreases defense and durability\nCompatible with all armor pieces",
                450
        );
        inventory.setItem(13, armorUpgrade);

        // Tool Enhancement Service
        ItemStack toolUpgrade = createUpgradeService(
                Material.DIAMOND_PICKAXE,
                "🔧 Tool Enhancement",
                "Improve your tools with efficiency magic\nIncreases speed and effectiveness\nCompatible with all tool types",
                350
        );
        inventory.setItem(15, toolUpgrade);

        // Enchantment Boost Service
        ItemStack enchantBoost = createUpgradeService(
                Material.ENCHANTING_TABLE,
                "✨ Enchantment Boost",
                "Amplify existing enchantments\nIncreases enchantment levels safely\nNo risk of item destruction",
                750
        );
        inventory.setItem(20, enchantBoost);

        // Item Repair Service
        ItemStack itemRepair = createUpgradeService(
                Material.MEDIUM_AMETHYST_BUD,
                "🔨 Expert Repair",
                "Professional item restoration service\nFully repairs any item to perfect condition\nMaintains all enchantments and upgrades",
                200
        );
        inventory.setItem(22, itemRepair);

        // Stat Reroll Service
        ItemStack statReroll = createUpgradeService(
                Material.EXPERIENCE_BOTTLE,
                "🎲 Stat Reroll",
                "Reroll item statistics for better results\nChance for significant improvements\nSafer than using regular orbs",
                300
        );
        inventory.setItem(24, statReroll);
    }

    /**
     * Create an upgrade service item with consistent formatting
     */
    private ItemStack createUpgradeService(Material material, String name, String description, int price) {
        ItemStack service = new ItemStack(material);
        ItemMeta meta = service.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + name);
            service.setItemMeta(meta);
        }

        // Use VendorUtils for consistent pricing and description
        return VendorUtils.createVendorItem(service, price, description);
    }

    /**
     * Create workshop decorations with enhancement theme
     */
    private void createWorkshopDecorations() {
        // Top border - workshop theme
        for (int i = 0; i < 9; i++) {
            if (i != 4) { // Skip center for header
                Material borderMaterial = (i % 2 == 0) ? Material.GRAY_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                inventory.setItem(i, VendorUtils.createSeparator(borderMaterial, " "));
            }
        }

        // Bottom border
        for (int i = 36; i < 45; i++) {
            if (inventory.getItem(i) == null) {
                Material borderMaterial = (i % 2 == 0) ? Material.GRAY_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                inventory.setItem(i, VendorUtils.createSeparator(borderMaterial, " "));
            }
        }

        // Workshop tools as decorations
        inventory.setItem(9, createWorkshopTool(Material.SMITHING_TABLE, "🔨 Smithing Station"));
        inventory.setItem(17, createWorkshopTool(Material.GRINDSTONE, "⚙ Grinding Wheel"));
        inventory.setItem(27, createWorkshopTool(Material.BLAST_FURNACE, "🔥 Enhancement Forge"));
        inventory.setItem(35, createWorkshopTool(Material.ENCHANTING_TABLE, "✨ Magic Infuser"));

        // Fill remaining slots
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null && !isReservedSlot(i)) {
                inventory.setItem(i, VendorUtils.createSeparator(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
    }

    /**
     * Create workshop tool decoration
     */
    private ItemStack createWorkshopTool(Material material, String name) {
        ItemStack tool = new ItemStack(material);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name);
            meta.setLore(List.of(ChatColor.GRAY + "Workshop equipment"));
            tool.setItemMeta(meta);
        }
        return tool;
    }

    /**
     * Check if slot is reserved for services or UI
     */
    private boolean isReservedSlot(int slot) {
        // Service slots
        int[] serviceSlots = {11, 13, 15, 20, 22, 24};
        for (int serviceSlot : serviceSlots) {
            if (slot == serviceSlot) return true;
        }

        // UI slots
        return slot == 4 || slot == 40 || slot == 44 ||
                slot == 9 || slot == 17 || slot == 27 || slot == 35;
    }

    /**
     * Opens the menu for the player
     */
    public void open() {
        player.openInventory(inventory);
        // Play workshop ambience
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.3f, 1.0f);
    }

    /**
     * Handle inventory click events
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        try {
            // Handle close button
            if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "⚡ Thanks for visiting the Enhancement Workshop!");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            // Ignore decorative items
            if (isDecorativeItem(clickedItem)) {
                return;
            }

            // Handle upgrade service purchases
            if (VendorUtils.hasValidPrice(clickedItem)) {
                handleUpgradeServicePurchase(player, clickedItem);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling click in UpgradeVendorMenu for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "❌ An error occurred. Please try again.");
        }
    }

    /**
     * Check if item is decorative
     */
    private boolean isDecorativeItem(ItemStack item) {
        return item.getType() == Material.GRAY_STAINED_GLASS_PANE ||
                item.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE ||
                item.getType() == Material.BLACK_STAINED_GLASS_PANE ||
                item.getType() == Material.SMITHING_TABLE ||
                item.getType() == Material.GRINDSTONE ||
                item.getType() == Material.BLAST_FURNACE ||
                (item.getType() == Material.ENCHANTING_TABLE && isWorkshopDecoration(item)) ||
                item.getType() == Material.KNOWLEDGE_BOOK; // Info button
    }

    /**
     * Check if enchanting table is decoration or service
     */
    private boolean isWorkshopDecoration(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        return displayName.contains("Magic Infuser");
    }

    /**
     * Handle upgrade service purchase
     */
    private void handleUpgradeServicePurchase(Player player, ItemStack clickedItem) {
        int price = VendorUtils.extractPriceFromLore(clickedItem);
        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "❌ This service is not available for purchase.");
            return;
        }

        if (purchaseManager.isInPurchaseProcess(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "⚠ You already have an active purchase. Complete it first or type " +
                    ChatColor.BOLD + "'cancel'" + ChatColor.RED + ".");
            return;
        }

        String serviceName = getCleanServiceName(clickedItem);

        player.closeInventory();

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "⚡ Starting purchase for " + ChatColor.WHITE + serviceName + ChatColor.GOLD + "...");
        player.sendMessage(ChatColor.GRAY + "🔨 Preparing the workshop tools!");
        player.sendMessage("");

        ItemStack cleanServiceForPurchase = VendorUtils.createCleanItemCopy(clickedItem);
        purchaseManager.startPurchase(player, cleanServiceForPurchase, price);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    /**
     * Get clean service name for display
     */
    private String getCleanServiceName(ItemStack service) {
        if (service.hasItemMeta() && service.getItemMeta().hasDisplayName()) {
            String displayName = service.getItemMeta().getDisplayName();
            return ChatColor.stripColor(displayName)
                    .replaceAll("[▶▷►⚡🔨🛡🔧✨🔨🎲]", "")
                    .trim();
        }
        return "Enhancement Service";
    }

    /**
     * Setup fallback inventory for errors
     */
    private void setupFallbackInventory() {
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "⚠ Error Loading Services");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Failed to load enhancement services",
                    ChatColor.GRAY + "The workshop equipment is offline",
                    "",
                    ChatColor.YELLOW + "Please contact an administrator"
            ));
            errorItem.setItemMeta(meta);
        }

        inventory.setItem(22, errorItem);
        inventory.setItem(44, createCloseButton());

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, VendorUtils.createSeparator(Material.RED_STAINED_GLASS_PANE, " "));
            }
        }
    }

    /**
     * Create  category label
     */
    private ItemStack createCategoryLabel(String name, Material icon) {
        ItemStack label = new ItemStack(icon);
        ItemMeta meta = label.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "⚡ " + name);
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Professional equipment enhancement",
                    ChatColor.GRAY + "Magical improvements for all your gear",
                    "",
                    ChatColor.YELLOW + "🔨 Click on a service below to purchase!"
            ));
            label.setItemMeta(meta);
        }
        return label;
    }

    /**
     * Create  information button
     */
    private ItemStack createInfoButton() {
        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "🔧 Workshop Guide");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GOLD + "Enhancement Services:",
                    ChatColor.GRAY + "• Purchase enhancement tokens/scrolls",
                    ChatColor.GRAY + "• Use tokens on your equipment",
                    ChatColor.GRAY + "• Each service improves different aspects",
                    ChatColor.GRAY + "• All enhancements are permanent",
                    "",
                    ChatColor.YELLOW + "💡 Workshop Tips:",
                    ChatColor.GRAY + "• Start with basic enhancements",
                    ChatColor.GRAY + "• Higher tier items benefit more",
                    ChatColor.GRAY + "• Combine different enhancement types",
                    ChatColor.GRAY + "• Ask staff about specific upgrades",
                    "",
                    ChatColor.GREEN + "⚡ Satisfaction Guaranteed!",
                    ChatColor.GRAY + "Professional enhancement services",
                    ChatColor.GRAY + "since the server began!"
            ));
            info.setItemMeta(meta);
        }
        return info;
    }

    /**
     * Create  close button
     */
    private ItemStack createCloseButton() {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta meta = close.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "❌ Leave Workshop");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Close the enhancement workshop",
                    "",
                    ChatColor.GOLD + "⚡ Come back when you need upgrades!"
            ));
            close.setItemMeta(meta);
        }
        return close;
    }
}