package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.vendors.utils.VendorUtils;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.mounts.MountConfig;
import com.rednetty.server.mechanics.player.mounts.MountManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Mount Stable - Clean, modern UI for purchasing mount tier access
 * Modernized with Adventure API and simplified design
 */
public class MountVendorMenu implements Listener {

    private static final Component INVENTORY_TITLE = Component.text("🐎 Mount Stable", NamedTextColor.GOLD);
    private static final int INVENTORY_SIZE = 27; // Simplified to 3 rows
    private static final int MIN_HORSE_TIER = 2;
    private static final int MAX_HORSE_TIER = 5;

    // Simplified layout - clean and organized
    private static final int[] TIER_SLOTS = {10, 12, 14, 16}; // Centered tier options
    private static final int STATUS_SLOT = 4;
    private static final int INFO_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private final Player player;
    private final Inventory inventory;
    private final YakPlayer yakPlayer;
    private final MountManager mountManager;
    private final EconomyManager economyManager;
    private final YakRealms plugin;

    /**
     * Constructor with error handling
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
     * Setup inventory with clean, modern design
     */
    private void setupInventory() {
        createSimpleDecorations();
        setupHeader();
        setupTierUpgrades();
        setupUIElements();
    }

    /**
     * Create simple, clean decorations
     */
    private void createSimpleDecorations() {
        // Simple top and bottom borders
        ItemStack border = createSeparator(Material.GRAY_STAINED_GLASS_PANE);

        // Top row border (except status slot)
        for (int i = 0; i < 9; i++) {
            if (i != STATUS_SLOT) {
                inventory.setItem(i, border);
            }
        }

        // Bottom row border (except close slot)
        for (int i = 18; i < 27; i++) {
            if (i != INFO_SLOT && i != CLOSE_SLOT) {
                inventory.setItem(i, border);
            }
        }
    }

    /**
     * Setup header with current mount status
     */
    private void setupHeader() {
        int currentTier = yakPlayer.getHorseTier();

        ItemStack statusItem = new ItemStack(Material.GOLDEN_HORSE_ARMOR);
        ItemMeta meta = statusItem.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("🐎 Mount Status", NamedTextColor.GOLD, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Current Tier: ", NamedTextColor.GREEN)
                    .append(getTierDisplay(currentTier)));

            if (currentTier >= MIN_HORSE_TIER) {
                MountConfig.HorseStats currentStats = mountManager.getConfig().getHorseStats(currentTier);
                if (currentStats != null) {
                    lore.add(Component.text("Mount: ", NamedTextColor.GREEN)
                            .append(Component.text(currentStats.getName(), NamedTextColor.WHITE)));
                    lore.add(Component.text("Speed: ", NamedTextColor.GREEN)
                            .append(Component.text((int) (currentStats.getSpeed() * 100) + "%", NamedTextColor.WHITE)));
                    lore.add(Component.text("Jump: ", NamedTextColor.GREEN)
                            .append(Component.text((int) (currentStats.getJump() * 100) + "%", NamedTextColor.WHITE)));
                }
            } else {
                lore.add(Component.text("🏇 No mount access yet!", NamedTextColor.YELLOW));
                lore.add(Component.text("Purchase Tier " + MIN_HORSE_TIER + " to get your first horse!", NamedTextColor.YELLOW));
            }

            lore.add(Component.empty());
            lore.add(Component.text("💰 Your gems: ", NamedTextColor.GRAY)
                    .append(Component.text(VendorUtils.formatColoredCurrency(yakPlayer.getBankGems()))));

            meta.lore(lore);
            statusItem.setItemMeta(meta);
        }

        inventory.setItem(STATUS_SLOT, statusItem);
    }

    /**
     * Setup tier upgrade options with clean design
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
     * Create clean tier upgrade item
     */
    private ItemStack createTierItem(int tier, MountConfig.HorseStats stats, int currentTier) {
        Material itemMaterial = getItemMaterialForTier(tier, currentTier);
        ItemStack item = new ItemStack(itemMaterial);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            NamedTextColor tierColor = getTierColor(tier);
            meta.displayName(Component.text("🐎 Tier " + tier, tierColor, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(getTierStatus(tier, currentTier));
            lore.add(Component.empty());

            // Mount information
            lore.add(Component.text("🐴 " + stats.getName(), tierColor));
            lore.add(Component.text("⚡ Speed: " + (int) (stats.getSpeed() * 100) + "%", NamedTextColor.YELLOW));
            lore.add(Component.text("🦘 Jump: " + (int) (stats.getJump() * 100) + "%", NamedTextColor.YELLOW));
            lore.add(Component.empty());

            // Action information
            if (tier <= currentTier) {
                lore.add(Component.text("✅ You own this tier!", NamedTextColor.GREEN));
            } else if (tier == currentTier + 1) {
                lore.add(Component.text("💰 Price: ", NamedTextColor.YELLOW)
                        .append(Component.text(VendorUtils.formatColoredCurrency(stats.getPrice()))));
                lore.add(Component.text("👆 Click to purchase!", NamedTextColor.GREEN));

                if (yakPlayer.getBankGems() >= stats.getPrice()) {
                    lore.add(Component.text("✅ You can afford this!", NamedTextColor.GREEN));
                } else {
                    lore.add(Component.text("❌ Need " + VendorUtils.formatColoredCurrency(stats.getPrice() - yakPlayer.getBankGems()) + " more gems", NamedTextColor.RED));
                }
            } else {
                lore.add(Component.text("🔒 Requires Tier " + (tier - 1) + " first!", NamedTextColor.RED));
            }

            meta.lore(lore);

            // Add enchantment glow for available/owned tiers
            if (tier <= currentTier || tier == currentTier + 1) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
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
     * Get status display for tiers
     */
    private Component getTierStatus(int tier, int currentTier) {
        if (tier <= currentTier) {
            return Component.text("✅ OWNED", NamedTextColor.GREEN);
        } else if (tier == currentTier + 1) {
            return Component.text("💰 AVAILABLE", NamedTextColor.YELLOW);
        } else {
            return Component.text("🔒 LOCKED", NamedTextColor.RED);
        }
    }

    /**
     * Setup UI elements
     */
    private void setupUIElements() {
        inventory.setItem(INFO_SLOT, createInfoButton());
        inventory.setItem(CLOSE_SLOT, createCloseButton());
    }

    /**
     * Create info button
     */
    private ItemStack createInfoButton() {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("📚 How It Works", NamedTextColor.YELLOW, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Mount Tier System:", NamedTextColor.GOLD));
            lore.add(Component.text("• Purchase tier access (permanent)", NamedTextColor.GRAY));
            lore.add(Component.text("• Higher tiers = better mounts", NamedTextColor.GRAY));
            lore.add(Component.text("• Must buy tiers in order", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("After Purchase:", NamedTextColor.YELLOW));
            lore.add(Component.text("• Use mount menus to spawn horses", NamedTextColor.GRAY));
            lore.add(Component.text("• Better performance at higher tiers", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("🐎 Welcome to the Mount Stable!", NamedTextColor.AQUA));

            meta.lore(lore);
            info.setItemMeta(meta);
        }
        return info;
    }

    /**
     * Handle inventory clicks with validation
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clickPlayer)) return;
        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        try {
            // Close button
            if (event.getSlot() == CLOSE_SLOT) {
                clickPlayer.closeInventory();
                clickPlayer.sendMessage(Component.text("🐎 Thanks for visiting the Mount Stable!", NamedTextColor.GOLD));
                clickPlayer.playSound(Sound.sound(org.bukkit.Sound.ENTITY_HORSE_BREATHE, Sound.Source.PLAYER, 1.0f, 1.0f));
                return;
            }

            // Tier upgrade clicks
            if (isTierSlot(event.getSlot())) {
                int tier = getTierFromSlot(event.getSlot());
                handleTierPurchase(clickPlayer, tier);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error in MountVendorMenu click", e);
            clickPlayer.sendMessage(Component.text("❌ An error occurred. Please try again.", NamedTextColor.RED));
        }
    }

    /**
     * Handle tier purchase with clean feedback
     */
    private void handleTierPurchase(Player purchasePlayer, int tier) {
        int currentTier = yakPlayer.getHorseTier();
        if (currentTier == 0) currentTier = 1;

        // Check if already owned
        if (tier <= currentTier) {
            purchasePlayer.sendMessage(Component.text("❌ You already have access to Tier " + tier + "!", NamedTextColor.RED));
            purchasePlayer.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
            return;
        }

        // Check if next tier
        if (tier != currentTier + 1) {
            purchasePlayer.sendMessage(Component.text("❌ You must purchase tiers in order!", NamedTextColor.RED));
            purchasePlayer.sendMessage(Component.text("Next available: Tier " + (currentTier + 1), NamedTextColor.YELLOW));
            purchasePlayer.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
            return;
        }

        // Get stats and validate
        MountConfig.HorseStats stats = mountManager.getConfig().getHorseStats(tier);
        if (stats == null) {
            purchasePlayer.sendMessage(Component.text("❌ Tier " + tier + " is not available.", NamedTextColor.RED));
            return;
        }

        int price = stats.getPrice();
        int playerGems = yakPlayer.getBankGems();

        // Check affordability
        if (playerGems < price) {
            purchasePlayer.sendMessage(Component.text("⚠️ INSUFFICIENT GEMS", NamedTextColor.RED, TextDecoration.BOLD));
            purchasePlayer.sendMessage(Component.text("Required: ", NamedTextColor.RED)
                    .append(Component.text(VendorUtils.formatColoredCurrency(price))));
            purchasePlayer.sendMessage(Component.text("Your gems: ", NamedTextColor.RED)
                    .append(Component.text(VendorUtils.formatColoredCurrency(playerGems))));
            purchasePlayer.sendMessage(Component.text("Needed: ", NamedTextColor.RED)
                    .append(Component.text(VendorUtils.formatColoredCurrency(price - playerGems))));
            purchasePlayer.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
            return;
        }

        // Process purchase
        yakPlayer.setBankGems(playerGems - price);
        yakPlayer.setHorseTier(tier);

        // Save player data
        YakPlayerManager.getInstance().savePlayer(yakPlayer).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to save mount tier upgrade for " + purchasePlayer.getName(), ex);
            return false;
        });

        // Success feedback - clean and concise
        purchasePlayer.closeInventory();

        purchasePlayer.sendMessage(Component.text("🎉 MOUNT TIER UPGRADED! 🎉", NamedTextColor.GOLD, TextDecoration.BOLD));
        purchasePlayer.sendMessage(Component.empty());
        purchasePlayer.sendMessage(Component.text("🐴 New Mount: ", NamedTextColor.GREEN)
                .append(Component.text(stats.getName(), getTierColor(tier), TextDecoration.BOLD)));
        purchasePlayer.sendMessage(Component.text("⚡ Speed: " + (int) (stats.getSpeed() * 100) + "% ", NamedTextColor.GREEN)
                .append(Component.text("🦘 Jump: " + (int) (stats.getJump() * 100) + "%", NamedTextColor.GREEN)));
        purchasePlayer.sendMessage(Component.empty());
        purchasePlayer.sendMessage(Component.text("💰 Cost: ", NamedTextColor.YELLOW)
                .append(Component.text(VendorUtils.formatColoredCurrency(price))));
        purchasePlayer.sendMessage(Component.text("💰 Remaining: ", NamedTextColor.YELLOW)
                .append(Component.text(VendorUtils.formatColoredCurrency(yakPlayer.getBankGems()))));
        purchasePlayer.sendMessage(Component.empty());
        purchasePlayer.sendMessage(Component.text("🌟 Use mount spawning menus to summon your new mount!", NamedTextColor.AQUA));

        // Success sound and effects
        purchasePlayer.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1.0f, 1.2f));
        purchasePlayer.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                purchasePlayer.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0);
    }

    /**
     * Setup error inventory
     */
    private void setupErrorInventory() {
        ItemStack error = new ItemStack(Material.BARRIER);
        ItemMeta meta = error.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("⚠️ Player Data Error", NamedTextColor.RED, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Could not load your mount data", NamedTextColor.GRAY));
            lore.add(Component.text("Please try again later or contact staff", NamedTextColor.YELLOW));
            lore.add(Component.empty());
            lore.add(Component.text("Error Code: NO_YAKPLAYER_DATA", NamedTextColor.RED));

            meta.lore(lore);
            error.setItemMeta(meta);
        }
        inventory.setItem(13, error);
        inventory.setItem(CLOSE_SLOT, createCloseButton());

        // Fill with error decorations
        ItemStack errorBorder = createSeparator(Material.RED_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, errorBorder);
            }
        }
    }

    /**
     * Open the menu
     */
    public void open() {
        player.openInventory(inventory);
        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_HORSE_BREATHE, Sound.Source.PLAYER, 1.0f, 1.0f));
    }

    // =============================================================================
    // UTILITY METHODS
    // =============================================================================

    /**
     * Get tier color scheme
     */
    private NamedTextColor getTierColor(int tier) {
        return switch (tier) {
            case 2 -> NamedTextColor.GREEN;
            case 3 -> NamedTextColor.AQUA;
            case 4 -> NamedTextColor.LIGHT_PURPLE;
            case 5 -> NamedTextColor.GOLD;
            default -> NamedTextColor.GRAY;
        };
    }

    /**
     * Get tier display with formatting
     */
    private Component getTierDisplay(int tier) {
        if (tier < MIN_HORSE_TIER) {
            return Component.text("None", NamedTextColor.GRAY);
        }
        return Component.text("Tier " + tier, getTierColor(tier));
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
                return MIN_HORSE_TIER + i;
            }
        }
        return MIN_HORSE_TIER;
    }

    /**
     * Create separator item
     */
    private ItemStack createSeparator(Material material) {
        ItemStack separator = new ItemStack(material);
        ItemMeta meta = separator.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            separator.setItemMeta(meta);
        }
        return separator;
    }

    /**
     * Create close button
     */
    private ItemStack createCloseButton() {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta meta = close.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("❌ Leave Stable", NamedTextColor.RED, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Close the mount stable", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("🐎 Ride safely, adventurer!", NamedTextColor.GOLD));

            meta.lore(lore);
            close.setItemMeta(meta);
        }
        return close;
    }
}