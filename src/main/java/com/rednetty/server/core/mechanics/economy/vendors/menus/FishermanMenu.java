package com.rednetty.server.core.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.economy.vendors.purchase.PurchaseManager;
import com.rednetty.server.core.mechanics.economy.vendors.utils.VendorUtils;
import com.rednetty.server.core.mechanics.player.items.SpeedfishMechanics;
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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 *  Fisherman vendor menu that stores original fish items separately from display items
 * Players receive the exact same items that SpeedfishMechanics generates
 */
public class FishermanMenu implements Listener {

    private static final String INVENTORY_TITLE = "🐟 Magical Fisherman";
    private static final int INVENTORY_SIZE = 27;

    //  pricing for magical fish
    private static final int[] FISH_PRICES = {0, 0, 400, 900, 1600, 2500}; // Tier 0-1 not sold, 2-5 available

    private final Player player;
    private final Inventory inventory;
    private final PurchaseManager purchaseManager;
    private final YakRealms plugin;

    //  Store original clean fish items separately from display items
    private final Map<Integer, ItemStack> originalFishItems = new HashMap<>(); // slot -> original clean fish
    private final Map<Integer, Integer> fishPrices = new HashMap<>(); // slot -> price

    /**
     * Constructor with better error handling
     */
    public FishermanMenu(Player player) {
        this.player = player;
        this.plugin = YakRealms.getInstance();
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        this.purchaseManager = PurchaseManager.getInstance();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Initialize inventory with  design
        try {
            setupInventory();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up FishermanMenu for player " + player.getName(), e);
            setupFallbackInventory();
        }
    }

    /**
     *   inventory setup that stores original fish items
     */
    private void setupInventory() {
        // Clear stored data
        originalFishItems.clear();
        fishPrices.clear();

        //  category label with oceanic theme
        inventory.setItem(4, createCategoryLabel("Magical Speedfish", Material.FISHING_ROD));

        try {
            // Create magical fish items (tiers 2-5) with original item storage
            for (int tier = 2; tier <= 5; tier++) {
                ItemStack originalFish = SpeedfishMechanics.createSpeedfish(tier, true);

                if (originalFish != null) {
                    // Store the original clean fish and its price
                    int slot = getSlotForTier(tier);
                    int price = FISH_PRICES[tier];

                    originalFishItems.put(slot, originalFish.clone());
                    fishPrices.put(slot, price);

                    // Create display item with vendor information
                    String description = createFishDescription(tier);
                    ItemStack displayFish = VendorUtils.createVendorDisplayItem(originalFish, price, description);

                    inventory.setItem(slot, displayFish);
                } else {
                    // Create fallback fish if SpeedfishMechanics fails
                    createFallbackFish(tier);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error creating speedfish items", e);
            createFallbackItems();
        }

        //  decorative elements
        createDecorations();

        //  information panel
        inventory.setItem(22, createFishermanInfoPanel());

        //  close button
        inventory.setItem(26, createCloseButton());
    }

    /**
     * Get slot position for each tier (centered layout)
     */
    private int getSlotForTier(int tier) {
        switch (tier) {
            case 2:
                return 10; // Left side
            case 3:
                return 12; // Center-left
            case 4:
                return 14; // Center-right
            case 5:
                return 16; // Right side
            default:
                return 13; // Fallback center
        }
    }

    /**
     * Create  description for each fish tier
     */
    private String createFishDescription(int tier) {
        switch (tier) {
            case 2:
                return "A basic magical speedfish\nGrants temporary speed boost\nPerfect for new adventurers\nDuration: 30 seconds";
            case 3:
                return "An improved magical speedfish\n speed and duration\nRecommended for regular use\nDuration: 45 seconds";
            case 4:
                return "A powerful magical speedfish\nSignificant speed enhancement\nFor experienced travelers\nDuration: 60 seconds";
            case 5:
                return "The ultimate magical speedfish\nMaximum speed and endurance\nLegendary adventurer grade\nDuration: 90 seconds";
            default:
                return "A mysterious magical fish\nEffects unknown\nUse with caution";
        }
    }

    /**
     *  Create fallback fish with original item storage
     */
    private void createFallbackFish(int tier) {
        try {
            // Create basic fallback fish
            ItemStack fallbackFish = new ItemStack(Material.COD);
            ItemMeta meta = fallbackFish.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(getTierColor(tier) + "⚡ Tier " + tier + " Speedfish");
                fallbackFish.setItemMeta(meta);
            }

            // Store the original fallback fish
            int slot = getSlotForTier(tier);
            int price = FISH_PRICES[tier];

            originalFishItems.put(slot, fallbackFish.clone());
            fishPrices.put(slot, price);

            // Create display version
            String description = createFishDescription(tier) + "\n⚠ Fallback item - contact admin if you see this";
            ItemStack displayFish = VendorUtils.createVendorDisplayItem(fallbackFish, price, description);

            inventory.setItem(slot, displayFish);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error creating fallback fish tier " + tier, e);
        }
    }

    /**
     * Get color for each tier
     */
    private String getTierColor(int tier) {
        switch (tier) {
            case 2:
                return ChatColor.GREEN.toString();
            case 3:
                return ChatColor.AQUA.toString();
            case 4:
                return ChatColor.LIGHT_PURPLE.toString();
            case 5:
                return ChatColor.GOLD.toString();
            default:
                return ChatColor.GRAY.toString();
        }
    }

    /**
     * Create  fallback items for all tiers
     */
    private void createFallbackItems() {
        for (int tier = 2; tier <= 5; tier++) {
            createFallbackFish(tier);
        }
    }

    /**
     * Create  decorative elements with oceanic theme
     */
    private void createDecorations() {
        // Top border - ocean blue theme
        fillRow(0, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        // Bottom border - ocean blue theme
        fillRow(2, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        // Fill remaining slots with appropriate decorations
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                // Use cyan glass for water effect
                inventory.setItem(i, createSeparator(Material.CYAN_STAINED_GLASS_PANE));
            }
        }
    }

    /**
     * Opens the  menu for the player
     */
    public void open() {
        player.openInventory(inventory);
        // Play oceanic sound effect
        player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_WATER_AMBIENT, 0.5f, 1.0f);
    }

    /**
     *  Click handling with original fish item usage
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        try {
            // Handle close button
            if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
                player.sendMessage(ChatColor.AQUA + "🌊 Thanks for visiting the Fisherman!");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            // Ignore decorative items
            if (isDecorativeItem(clickedItem)) {
                return;
            }

            // Handle purchasing fish (COD items)
            if (clickedItem.getType() == Material.COD) {
                handleFishPurchase(player, event.getSlot());
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling click in FishermanMenu for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "❌ An error occurred. Please try again.");
        }
    }

    /**
     * Check if clicked item is decorative
     */
    private boolean isDecorativeItem(ItemStack item) {
        return item.getType() == Material.CYAN_STAINED_GLASS_PANE ||
                item.getType() == Material.LIGHT_BLUE_STAINED_GLASS_PANE ||
                item.getType() == Material.ENCHANTED_BOOK ||
                item.getType() == Material.FISHING_ROD;
    }

    /**
     * Fish purchase handling using stored original fish
     */
    private void handleFishPurchase(Player player, int slot) {
        // Get the original fish and price from storage
        ItemStack originalFish = originalFishItems.get(slot);
        Integer price = fishPrices.get(slot);

        if (originalFish == null || price == null) {
            player.sendMessage(ChatColor.RED + "❌ This fish is not available for purchase.");
            return;
        }

        // Validate purchase state
        if (purchaseManager.isInPurchaseProcess(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "⚠ You already have an active purchase. Complete it first or type " +
                    ChatColor.BOLD + "'cancel'" + ChatColor.RED + ".");
            return;
        }

        // Get clean fish display name from original fish
        String itemName = getCleanFishName(originalFish);

        player.closeInventory();

        //  purchase initiation message
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "🐟 Starting purchase for " + ChatColor.WHITE + itemName + ChatColor.AQUA + "...");
        player.sendMessage(ChatColor.GRAY + "🌊 Fresh from the mystical waters!");
        player.sendMessage("");

        // Use the original clean fish directly (no cleaning needed)
        purchaseManager.startPurchase(player, originalFish.clone(), price);

        // Play purchase initiation sounds
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SWIM, 0.7f, 1.2f);
    }

    /**
     * Get clean fish name for display
     */
    private String getCleanFishName(ItemStack fish) {
        if (fish.hasItemMeta() && fish.getItemMeta().hasDisplayName()) {
            // The fish is already clean, just strip colors
            String displayName = fish.getItemMeta().getDisplayName();
            return ChatColor.stripColor(displayName);
        }
        return "Magical Speedfish";
    }

    /**
     * Setup fallback inventory for errors
     */
    private void setupFallbackInventory() {
        // Create basic error notice
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "⚠ Error Loading Fish");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Failed to load magical fish",
                    ChatColor.GRAY + "The fisherman's nets came up empty",
                    "",
                    ChatColor.YELLOW + "Please contact an administrator"
            ));
            errorItem.setItemMeta(meta);
        }

        inventory.setItem(13, errorItem);
        inventory.setItem(26, createCloseButton());

        // Fill with ocean-themed glass
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator(Material.BLUE_STAINED_GLASS_PANE));
            }
        }
    }

    /**
     * Create fisherman information panel
     */
    private ItemStack createFishermanInfoPanel() {
        ItemStack info = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = info.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "🐟 Fisherman's Guide");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GOLD + "About Speedfish:",
                    ChatColor.GRAY + "• Magical fish that grant speed boosts",
                    ChatColor.GRAY + "• Right-click to consume and gain effect",
                    ChatColor.GRAY + "• Higher tiers = better speed & duration",
                    ChatColor.GRAY + "• Effects stack with potions",
                    "",
                    ChatColor.YELLOW + "🏃 Speed Effects by Tier:",
                    ChatColor.GREEN + "• Tier 2: " + ChatColor.WHITE + "Speed I (30s)",
                    ChatColor.AQUA + "• Tier 3: " + ChatColor.WHITE + "Speed I (45s)",
                    ChatColor.LIGHT_PURPLE + "• Tier 4: " + ChatColor.WHITE + "Speed II (60s)",
                    ChatColor.GOLD + "• Tier 5: " + ChatColor.WHITE + "Speed II (90s)",
                    "",
                    ChatColor.GREEN + "💡 Pro Tips:",
                    ChatColor.GRAY + "• Save high-tier fish for long journeys",
                    ChatColor.GRAY + "• Great for escaping dangerous situations",
                    ChatColor.GRAY + "• Perfect for exploring new areas quickly",
                    "",
                    ChatColor.AQUA + "🌊 Fresh from the mystical waters!"
            ));
            info.setItemMeta(meta);
        }

        return info;
    }

    /**
     * Create  category label with oceanic theme
     */
    private ItemStack createCategoryLabel(String name, Material icon) {
        ItemStack label = new ItemStack(icon);
        ItemMeta meta = label.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "🌊 " + name);
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Fresh catches from mystical waters",
                    ChatColor.GRAY + "Each fish grants magical speed powers",
                    "",
                    ChatColor.YELLOW + "🐟 Click on a fish below to purchase!"
            ));
            label.setItemMeta(meta);
        }
        return label;
    }

    /**
     * Fill a row with  decorative glass
     */
    private void fillRow(int row, Material material) {
        for (int i = row * 9; i < (row + 1) * 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator(material));
            }
        }
    }

    /**
     * Create  separator glass pane with oceanic names
     */
    private ItemStack createSeparator(Material material) {
        ItemStack separator = new ItemStack(material);
        ItemMeta meta = separator.getItemMeta();
        if (meta != null) {
            // Give oceanic-themed names to decorations
            if (material == Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
                meta.setDisplayName(ChatColor.BLUE + "🌊 Ocean Waves");
            } else if (material == Material.CYAN_STAINED_GLASS_PANE) {
                meta.setDisplayName(ChatColor.DARK_AQUA + "💧 Mystical Waters");
            } else {
                meta.setDisplayName(" ");
            }
            separator.setItemMeta(meta);
        }
        return separator;
    }

    /**
     * Create  close button
     */
    private ItemStack createCloseButton() {
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta meta = closeButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "❌ Leave Fisherman");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Close the fisherman's stall",
                    "",
                    ChatColor.AQUA + "🌊 Come back anytime for fresh fish!"
            ));
            closeButton.setItemMeta(meta);
        }
        return closeButton;
    }
}