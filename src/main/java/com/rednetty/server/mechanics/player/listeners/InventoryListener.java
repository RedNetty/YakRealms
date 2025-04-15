package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.BankManager;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Handles inventory interactions including click events,
 * opening/closing inventories, and item manipulations.
 */
public class InventoryListener extends BaseListener {

    private final YakPlayerManager playerManager;
    private final BankManager bankManager;

    public InventoryListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
        this.bankManager = YakRealms.getInstance().getBankManager();
    }

    @Override
    public void initialize() {
        logger.info("Inventory listener initialized");
    }

    /**
     * Handle bank chest closing to save contents
     */
    @EventHandler
    public void onCloseChest(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        if (title.contains("Bank Chest")) {
            // Play sound
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);

            // Bank saving is handled by BankManager
        }
    }

    /**
     * Handle inventory clicks to check for special items
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack currentItem = event.getCurrentItem();

        if (currentItem != null && currentItem.getType() != Material.AIR &&
                currentItem.hasItemMeta() && currentItem.getItemMeta().hasDisplayName()) {

            // Handle spectral knight gear
            if (currentItem.getItemMeta().getDisplayName().contains("Spectral")) {
                NBTAccessor nbtAccessor = new NBTAccessor(currentItem);

                if (!nbtAccessor.hasKey("fixedgear")) {
                    // Check if it's diamond armor
                    switch (currentItem.getType()) {
                        case DIAMOND_HELMET:
                        case DIAMOND_CHESTPLATE:
                        case DIAMOND_LEGGINGS:
                        case DIAMOND_BOOTS:
                            // Replace with fixed gear - this is handled by appropriate drops manager
                            // In the original code, it used EliteDrops.createCustomEliteDrop("spectralKnight");
                            // We'll use a placeholder here and implement the full functionality when needed
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                createSpectralKnightGear(player);
                            });
                            break;
                        default:
                            // Not armor, do nothing
                            break;
                    }
                }
            }

            // Additional special item processing as needed
        }
    }

    /**
     * Create spectral knight gear for a player
     * This is a placeholder for the actual implementation
     */
    private void createSpectralKnightGear(Player player) {
        // TODO: Implement using the appropriate drop manager
        // EliteDrops.createCustomEliteDrop("spectralKnight");
        logger.info("Creating spectral knight gear for player " + player.getName());
    }

    /**
     * Prevent players from putting armor on directly
     */
    @EventHandler
    public void onArmorEquip(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && isArmor(item.getType()) &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // Cancel equipping armor directly
            event.setCancelled(true);
            player.updateInventory();
        }
    }


    /**
     * Add glow effect to items with high enhancement level
     */
    private void applyGlowToHighLevelItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta() ||
                !item.getItemMeta().hasDisplayName()) {
            return;
        }

        // Check for enhancement level ("+4" or higher)
        int plusLevel = getEnhancementLevel(item);
        if (plusLevel > 3) {
            // Add glow enchantment
            // In original code: item.addUnsafeEnchantment(Enchants.glow, 1);
            // We'll use a placeholder enchantment for now
            addGlowEffect(item);
        }
    }

    /**
     * Get enhancement level from item name
     */
    private int getEnhancementLevel(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return 0;
        }

        String name = item.getItemMeta().getDisplayName();
        if (name.startsWith(ChatColor.RED + "[+")) {
            try {
                int endIndex = name.indexOf("]");
                if (endIndex > 3) { // [+X] format
                    String levelStr = name.substring(3, endIndex);
                    return Integer.parseInt(levelStr);
                }
            } catch (Exception e) {
                // Parsing failed, assume 0
            }
        }

        return 0;
    }

    /**
     * Add glow effect to an item
     */
    private void addGlowEffect(ItemStack item) {
        // TODO: This requires implementing the custom glow enchantment from original code
        // For now, use a placeholder - this will be properly implemented when needed
        item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    /**
     * Check if a material is armor
     */
    private boolean isArmor(Material material) {
        String name = material.name();
        return name.contains("_HELMET") ||
                name.contains("_CHESTPLATE") ||
                name.contains("_LEGGINGS") ||
                name.contains("_BOOTS");
    }

    /**
     * Handle clicks on furnaces
     */
    @EventHandler
    public void onFurnaceInteract(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if ((handItem == null || handItem.getType() == Material.AIR) &&
                (event.getClickedBlock().getType() == Material.FURNACE ||
                        event.getClickedBlock().getType() == Material.TORCH)) {

            player.sendMessage(ChatColor.RED +
                    "This can be used to cook fish! Right click this furnace while holding raw fish to cook it.");
            event.setCancelled(true);
        }
    }
}