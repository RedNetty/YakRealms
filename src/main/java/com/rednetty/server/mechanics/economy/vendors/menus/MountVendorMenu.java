package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.mounts.MountConfig;
import com.rednetty.server.mechanics.player.mounts.MountManager;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 *  Mount Stable - Sells Tier Access with beautiful UI and consistent formatting
 *  price formatting, better UX, and comprehensive mount information display
 */
public class MountVendorMenu implements Listener {

    private static final String INVENTORY_TITLE = "🐎 Mount Stable";
    private static final int INVENTORY_SIZE = 36; // 4 rows for better organization
    private static final int MIN_HORSE_TIER = 2; // Start from tier 2 (first actual horse)
    private static final int MAX_HORSE_TIER = 5; // Maximum tier available

    //  layout positions - better organization in 4 rows
    private static final int[] TIER_SLOTS = {11, 12, 14, 15}; // Tiers 2-5 centered
    private static final int STATUS_SLOT = 4;
    private static final int INFO_SLOT = 22;
    private static final int STATS_SLOT = 20;
    private static final int GUIDE_SLOT = 24;
    private static final int CLOSE_SLOT = 35;

    private final Player player;
    private final Inventory inventory;
    private final YakPlayer yakPlayer;
    private final MountManager mountManager;
    private final EconomyManager economyManager;
    private final YakRealms plugin;

    /**
     *  constructor with better error handling
     */
    public MountVendorMenu(Player player) {
        this.player = player;
        this.plugin = YakRealms.getInstance();
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        this.mountManager = MountManager.getInstance();
        this.economyManager = EconomyManager.getInstance();
        this.yakPlayer = YakPlayerManager.getInstance().getPlayer(player);

        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (yakPlayer == null) {
            setupErrorInventory();
        } else {
            setupInventory();
        }
    }

    /**
     *  inventory setup with beautiful stable theme
     */
    private void setupInventory() {
        createStableDecorations();
        setupHeader();
        setupTierUpgrades();
        setupUIElements();
    }

    /**
     * Create  stable decorations with horse theme
     */
    private void createStableDecorations() {
        // Top border - stable brown theme
        for (int i = 0; i < 9; i++) {
            if (i != STATUS_SLOT) {
                Material borderMaterial = (i % 2 == 0) ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
                inventory.setItem(i, VendorUtils.createSeparator(borderMaterial, " "));
            }
        }

        // Bottom border - stable theme
        for (int i = 27; i < 36; i++) {
            if (inventory.getItem(i) == null) {
                Material borderMaterial = (i % 2 == 0) ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
                inventory.setItem(i, VendorUtils.createSeparator(borderMaterial, " "));
            }
        }

        // Fill remaining empty slots with appropriate decorations
        for (int i = 10; i < 27; i++) {
            if (inventory.getItem(i) == null && !isReservedSlot(i)) {
                // Use light brown glass for stable floor effect
                inventory.setItem(i, VendorUtils.createSeparator(Material.YELLOW_STAINED_GLASS_PANE, " "));
            }
        }
    }

    /**
     *  header with comprehensive mount status
     */
    private void setupHeader() {
        int currentTier = yakPlayer.getHorseTier();

        ItemStack statusItem = new ItemStack(Material.GOLDEN_HORSE_ARMOR);
        ItemMeta meta = statusItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "🐎 Mount Status");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GREEN + "Current Tier: " + getTierDisplay(currentTier));

            if (currentTier >= MIN_HORSE_TIER) {
                MountConfig.HorseStats currentStats = mountManager.getConfig().getHorseStats(currentTier);
                if (currentStats != null) {
                    lore.add(ChatColor.GREEN + "Mount: " + ChatColor.WHITE + currentStats.getName());
                    lore.add(ChatColor.GREEN + "Speed: " + ChatColor.WHITE + (int) (currentStats.getSpeed() * 100) + "%");
                    lore.add(ChatColor.GREEN + "Jump: " + ChatColor.WHITE + (int) (currentStats.getJump() * 100) + "%");
                }
            }

            lore.add(ChatColor.GREEN + "Progress: " + ChatColor.WHITE + Math.max(0, currentTier - 1) + "/" + (MAX_HORSE_TIER - 1));
            lore.add("");

            if (currentTier < MIN_HORSE_TIER) {
                lore.add(ChatColor.YELLOW + "🏇 No mount access yet!");
                lore.add(ChatColor.YELLOW + "Purchase Tier " + MIN_HORSE_TIER + " to get your first horse!");
            } else if (currentTier < MAX_HORSE_TIER) {
                MountConfig.HorseStats nextStats = mountManager.getConfig().getHorseStats(currentTier + 1);
                if (nextStats != null) {
                    lore.add(ChatColor.YELLOW + "Next Upgrade: " + getTierColor(currentTier + 1) + "Tier " + (currentTier + 1));
                    lore.add(ChatColor.YELLOW + "Cost: " + VendorUtils.formatColoredCurrency(nextStats.getPrice()));
                    lore.add(ChatColor.YELLOW + "Mount: " + ChatColor.WHITE + nextStats.getName());
                }
            } else {
                lore.add(ChatColor.GOLD + "🏆 MAXIMUM TIER REACHED!");
                lore.add(ChatColor.GOLD + "You have the finest mount available!");
            }

            lore.add("");
            lore.add(ChatColor.GRAY + "💰 Your gems: " + VendorUtils.formatColoredCurrency(yakPlayer.getBankGems()));
            lore.add("");
            lore.add(ChatColor.AQUA + "Use mount spawning menus elsewhere");
            lore.add(ChatColor.AQUA + "to summon your mounts!");

            meta.setLore(lore);
            statusItem.setItemMeta(meta);
        }

        inventory.setItem(STATUS_SLOT, statusItem);
    }

    /**
     *  tier upgrade options with beautiful design
     */
    private void setupTierUpgrades() {
        int currentTier = yakPlayer.getHorseTier();

        int slotIndex = 0;
        for (int tier = MIN_HORSE_TIER; tier <= MAX_HORSE_TIER; tier++) {
            if (slotIndex >= TIER_SLOTS.length) break;

            MountConfig.HorseStats stats = mountManager.getConfig().getHorseStats(tier);
            if (stats == null) {
                slotIndex++;
                continue;
            }

            ItemStack tierItem = createTierItem(tier, stats, currentTier);
            inventory.setItem(TIER_SLOTS[slotIndex], tierItem);
            slotIndex++;
        }
    }

    /**
     * Create  tier upgrade item with perfect formatting
     */
    private ItemStack createTierItem(int tier, MountConfig.HorseStats stats, int currentTier) {
        // Use different materials based on tier status
        Material itemMaterial = getItemMaterialForTier(tier, currentTier);
        ItemStack item = new ItemStack(itemMaterial);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Determine status with  formatting
            String status = getTierStatus(tier, currentTier);

            //  title with tier color
            String tierColor = getTierColor(tier);
            meta.setDisplayName(tierColor + ChatColor.BOLD + "🐎 Tier " + tier + " Mount Access");

            List<String> lore = new ArrayList<>();
            lore.add(status);
            lore.add("");

            // Mount information
            lore.add(ChatColor.GOLD + "🐴 Mount: " + tierColor + stats.getName());
            lore.add(ChatColor.YELLOW + "⚡ Speed: " + ChatColor.WHITE + (int) (stats.getSpeed() * 100) + "%");
            lore.add(ChatColor.YELLOW + "🦘 Jump: " + ChatColor.WHITE + (int) (stats.getJump() * 100) + "%");
            lore.add("");

            // Description
            lore.add(ChatColor.GRAY + "📝 " + stats.getDescription());
            lore.add("");

            // Action information
            if (tier <= currentTier) {
                lore.add(ChatColor.GREEN + "✅ You own this tier!");
                lore.add(ChatColor.GRAY + "Use mount menus to spawn this horse");
            } else if (tier == currentTier + 1) {
                lore.add(ChatColor.YELLOW + "💰 Price: " + VendorUtils.formatColoredCurrency(stats.getPrice()));
                lore.add(ChatColor.GREEN + "👆 Click to purchase tier access!");
                lore.add("");
                lore.add(ChatColor.GRAY + "This unlocks the ability to spawn");
                lore.add(ChatColor.GRAY + "this tier of mount through other menus");

                // Add affordability check
                if (yakPlayer.getBankGems() >= stats.getPrice()) {
                    lore.add("");
                    lore.add(ChatColor.GREEN + "✅ You can afford this upgrade!");
                } else {
                    lore.add("");
                    lore.add(ChatColor.RED + "❌ Need " + VendorUtils.formatColoredCurrency(stats.getPrice() - yakPlayer.getBankGems()) + " more gems");
                }
            } else {
                lore.add(ChatColor.RED + "🔒 Requires Tier " + (tier - 1) + " first!");
                lore.add(ChatColor.GRAY + "Purchase tiers in order:");
                lore.add(ChatColor.GRAY.toString() + MIN_HORSE_TIER + " → " + (MIN_HORSE_TIER + 1) + " → " + (MIN_HORSE_TIER + 2) + " → " + MAX_HORSE_TIER);
            }

            meta.setLore(lore);

            // Add enchantment glow for available/owned tiers
            if (tier <= currentTier || tier == currentTier + 1) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Get appropriate material for tier based on status
     */
    private Material getItemMaterialForTier(int tier, int currentTier) {
        if (tier <= currentTier) {
            return Material.GOLDEN_HORSE_ARMOR; // Owned
        } else if (tier == currentTier + 1) {
            return Material.IRON_HORSE_ARMOR; // Available
        } else {
            return Material.LEATHER_HORSE_ARMOR; // Locked
        }
    }

    /**
     * Get  status display for tiers
     */
    private String getTierStatus(int tier, int currentTier) {
        if (tier <= currentTier) {
            return ChatColor.GREEN + "✅ OWNED";
        } else if (tier == currentTier + 1) {
            return ChatColor.YELLOW + "💰 AVAILABLE FOR PURCHASE";
        } else {
            return ChatColor.RED + "🔒 LOCKED";
        }
    }

    /**
     *  UI elements with comprehensive information
     */
    private void setupUIElements() {
        //  mount statistics comparison
        inventory.setItem(STATS_SLOT, createMountStatsComparison());

        //  info button
        inventory.setItem(INFO_SLOT, createInfoButton());

        //  guide button
        inventory.setItem(GUIDE_SLOT, createMountGuide());

        //  close button
        inventory.setItem(CLOSE_SLOT, createCloseButton());
    }

    /**
     * Create mount statistics comparison chart
     */
    private ItemStack createMountStatsComparison() {
        ItemStack stats = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = stats.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "📊 Mount Statistics");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GOLD + "Speed & Jump Comparison:");

            for (int tier = MIN_HORSE_TIER; tier <= MAX_HORSE_TIER; tier++) {
                MountConfig.HorseStats stats_tier = mountManager.getConfig().getHorseStats(tier);
                if (stats_tier != null) {
                    String tierColor = getTierColor(tier);
                    lore.add(tierColor + "Tier " + tier + ": " + ChatColor.WHITE +
                            (int) (stats_tier.getSpeed() * 100) + "% speed, " +
                            (int) (stats_tier.getJump() * 100) + "% jump");
                }
            }

            lore.add("");
            lore.add(ChatColor.YELLOW + "💡 Higher tiers provide:");
            lore.add(ChatColor.GRAY + "• Faster travel speed");
            lore.add(ChatColor.GRAY + "• Better jump height");
            lore.add(ChatColor.GRAY + "• More impressive appearance");

            meta.setLore(lore);
            stats.setItemMeta(meta);
        }
        return stats;
    }

    /**
     * Create  info button
     */
    private ItemStack createInfoButton() {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "📚 How It Works");
            infoMeta.setLore(Arrays.asList(
                    "",
                    ChatColor.GOLD + "Mount Tier System:",
                    ChatColor.GRAY + "• Purchase tier access (not physical items)",
                    ChatColor.GRAY + "• Each tier has better speed & jump stats",
                    ChatColor.GRAY + "• Must buy tiers in order: " + MIN_HORSE_TIER + "→" + (MIN_HORSE_TIER + 1) + "→" + (MIN_HORSE_TIER + 2) + "→" + MAX_HORSE_TIER,
                    ChatColor.GRAY + "• Tier " + MIN_HORSE_TIER + " is your first actual horse!",
                    "",
                    ChatColor.YELLOW + "After Purchase:",
                    ChatColor.GRAY + "• Your mount tier is permanently upgraded",
                    ChatColor.GRAY + "• Use mount spawning menus to summon horses",
                    ChatColor.GRAY + "• Higher tiers = better mount performance",
                    "",
                    ChatColor.GREEN + "Payment Information:",
                    ChatColor.GRAY + "• Paid with gems from your bank",
                    ChatColor.GRAY + "• One-time purchase per tier",
                    ChatColor.GRAY + "• Cannot be refunded or traded",
                    "",
                    ChatColor.AQUA + "🐎 Welcome to the Mount Stable!"
            ));
            info.setItemMeta(infoMeta);
        }
        return info;
    }

    /**
     * Create mount care guide
     */
    private ItemStack createMountGuide() {
        ItemStack guide = new ItemStack(Material.LEAD);
        ItemMeta meta = guide.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "🐴 Mount Care Guide");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GOLD + "Mount Usage Tips:",
                    ChatColor.GRAY + "• Right-click to mount your horse",
                    ChatColor.GRAY + "• Horses have limited stamina",
                    ChatColor.GRAY + "• Feed horses to restore health",
                    ChatColor.GRAY + "• Higher tiers have more durability",
                    "",
                    ChatColor.YELLOW + "Best Practices:",
                    ChatColor.GRAY + "• Don't ride into dangerous areas",
                    ChatColor.GRAY + "• Dismount before entering buildings",
                    ChatColor.GRAY + "• Use horses for long-distance travel",
                    ChatColor.GRAY + "• Higher tiers handle terrain better",
                    "",
                    ChatColor.GREEN + "Mount Commands:",
                    ChatColor.GRAY + "• Check mount spawning menus",
                    ChatColor.GRAY + "• Ask staff for additional help"
            ));
            guide.setItemMeta(meta);
        }
        return guide;
    }

    /**
     *  click handling with comprehensive validation
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        try {
            // Close button
            if (event.getSlot() == CLOSE_SLOT) {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "🐎 Thanks for visiting the Mount Stable!");
                player.playSound(player.getLocation(), Sound.ENTITY_HORSE_BREATHE, 1.0f, 1.0f);
                return;
            }

            // Tier upgrade clicks
            if (isTierSlot(event.getSlot())) {
                int tier = getTierFromSlot(event.getSlot());
                handleTierPurchase(player, tier);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error in MountVendorMenu click", e);
            player.sendMessage(ChatColor.RED + "❌ An error occurred. Please try again.");
        }
    }

    /**
     *  tier purchase handling with beautiful feedback
     */
    private void handleTierPurchase(Player player, int tier) {
        int currentTier = yakPlayer.getHorseTier();
        if(currentTier == 0 ) currentTier = 1;

        // Check if already owned
        if (tier <= currentTier) {
            player.sendMessage(ChatColor.RED + "❌ You already have access to Tier " + tier + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if next tier
        if (tier != currentTier + 1) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ You must purchase tiers in order!");
            player.sendMessage(ChatColor.YELLOW + "Current tier: " + (currentTier < MIN_HORSE_TIER ? "None" : "Tier " + currentTier));
            player.sendMessage(ChatColor.YELLOW + "Next available: Tier " + (currentTier + 1));
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Get stats and validate
        MountConfig.HorseStats stats = mountManager.getConfig().getHorseStats(tier);
        if (stats == null) {
            player.sendMessage(ChatColor.RED + "❌ Tier " + tier + " is not available.");
            return;
        }

        int price = stats.getPrice();
        int playerGems = yakPlayer.getBankGems();

        //  affordability check
        if (playerGems < price) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.RED + "                    ⚠ INSUFFICIENT GEMS ⚠");
            player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.RED + "Required: " + VendorUtils.formatColoredCurrency(price));
            player.sendMessage(ChatColor.RED + "Your gems: " + VendorUtils.formatColoredCurrency(playerGems));
            player.sendMessage(ChatColor.RED + "Needed: " + VendorUtils.formatColoredCurrency(price - playerGems));
            player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Process  purchase
        yakPlayer.setBankGems(playerGems - price);
        yakPlayer.setHorseTier(tier);

        // Save player data
        YakPlayerManager.getInstance().savePlayer(yakPlayer).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to save mount tier upgrade for " + player.getName(), ex);
            return false;
        });

        //  success feedback
        player.closeInventory();

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GOLD + "                🎉 MOUNT TIER UPGRADED! 🎉");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "🐴 New Mount: " + getTierColor(tier) + ChatColor.BOLD + stats.getName());
        player.sendMessage(ChatColor.GREEN + "⚡ Speed: " + ChatColor.WHITE + (int) (stats.getSpeed() * 100) + "% " +
                ChatColor.GREEN + "🦘 Jump: " + ChatColor.WHITE + (int) (stats.getJump() * 100) + "%");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "💰 Transaction Details:");
        player.sendMessage(ChatColor.YELLOW + "Cost: " + VendorUtils.formatColoredCurrency(price));
        player.sendMessage(ChatColor.YELLOW + "Remaining gems: " + VendorUtils.formatColoredCurrency(yakPlayer.getBankGems()));
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "🌟 Use mount spawning menus to summon your new mount!");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        //  success sounds and effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Particle effects
        player.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0);
    }

    /**
     * Setup error inventory with  error display
     */
    private void setupErrorInventory() {
        ItemStack error = new ItemStack(Material.BARRIER);
        ItemMeta meta = error.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "⚠ Player Data Error");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Could not load your mount data",
                    ChatColor.GRAY + "The stable keeper can't find your records",
                    "",
                    ChatColor.YELLOW + "Please try again later or contact staff",
                    "",
                    ChatColor.RED + "Error Code: NO_YAKPLAYER_DATA"
            ));
            error.setItemMeta(meta);
        }
        inventory.setItem(13, error);

        inventory.setItem(CLOSE_SLOT, createCloseButton());

        // Fill with error-themed decorations
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, VendorUtils.createSeparator(Material.RED_STAINED_GLASS_PANE, " "));
            }
        }
    }

    /**
     * Open the  menu
     */
    public void open() {
        player.openInventory(inventory);
        // Play stable ambience
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_BREATHE, 1.0f, 1.0f);
    }

    // =============================================================================
    // UTILITY METHODS
    // =============================================================================

    /**
     * Get  tier color scheme
     */
    private String getTierColor(int tier) {
        return switch (tier) {
            case 2 -> ChatColor.GREEN.toString(); // Green (first horse)
            case 3 -> ChatColor.AQUA.toString(); // Aqua
            case 4 -> ChatColor.LIGHT_PURPLE.toString(); // Light Purple
            case 5 -> ChatColor.GOLD.toString(); // Gold (max tier)
            default -> ChatColor.GRAY.toString(); // Gray
        };
    }

    /**
     * Get  tier display with formatting
     */
    private String getTierDisplay(int tier) {
        if (tier < MIN_HORSE_TIER) return ChatColor.GRAY + "None";
        return getTierColor(tier) + "Tier " + tier;
    }

    /**
     * Check if slot is reserved for specific UI elements
     */
    private boolean isReservedSlot(int slot) {
        // Check tier slots
        for (int tierSlot : TIER_SLOTS) {
            if (slot == tierSlot) return true;
        }
        // Check UI slots
        return slot == STATUS_SLOT || slot == INFO_SLOT || slot == STATS_SLOT ||
                slot == GUIDE_SLOT || slot == CLOSE_SLOT;
    }

    /**
     * Check if slot is a tier upgrade slot
     */
    private boolean isTierSlot(int slot) {
        for (int tierSlot : TIER_SLOTS) {
            if (slot == tierSlot) return true;
        }
        return false;
    }

    /**
     * Get tier number from slot position
     */
    private int getTierFromSlot(int slot) {
        for (int i = 0; i < TIER_SLOTS.length; i++) {
            if (TIER_SLOTS[i] == slot) {
                return MIN_HORSE_TIER + i; // Start from tier 2
            }
        }
        return MIN_HORSE_TIER;
    }

    /**
     * Create  close button
     */
    private ItemStack createCloseButton() {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "❌ Leave Stable");
            closeMeta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Close the mount stable",
                    "",
                    ChatColor.GOLD + "🐎 Ride safely, adventurer!"
            ));
            close.setItemMeta(closeMeta);
        }
        return close;
    }
}