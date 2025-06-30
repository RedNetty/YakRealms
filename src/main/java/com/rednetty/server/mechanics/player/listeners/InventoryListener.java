package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.BankManager;
import com.rednetty.server.mechanics.item.MenuItemManager;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Enhanced inventory listener that handles menu items in the crafting grid
 * and prevents their manipulation while allowing normal inventory operations.
 */
public class InventoryListener extends BaseListener {

    private final YakPlayerManager playerManager;
    private final BankManager bankManager;
    private final MenuItemManager menuItemManager;

    public InventoryListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
        this.bankManager = YakRealms.getInstance().getBankManager();
        this.menuItemManager = MenuItemManager.getInstance();
    }

    @Override
    public void initialize() {
        logger.info("Enhanced inventory listener initialized with menu item support");
    }

    /**
     * Handle inventory opening to set up menu items
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // Set up menu items when player opens their inventory
        if (event.getInventory().getType() == InventoryType.CRAFTING) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getOpenInventory().getType() == InventoryType.CRAFTING) {
                    menuItemManager.setupMenuItems(player);
                }
            }, 1L); // Delay by 1 tick to ensure inventory is fully opened
        }
    }

    /**
     * Handle inventory closing to clean up menu items
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        // Handle bank chest closing
        if (title.contains("Bank Chest")) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
        }

        // Clean up menu items when closing crafting inventory
        if (event.getInventory().getType() == InventoryType.CRAFTING) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    menuItemManager.clearMenuItems(player);
                }
            }, 1L); // Small delay to ensure proper cleanup
        }
    }

    /**
     * Enhanced inventory click handler with menu item support
     */
    @EventHandler(priority = EventPriority.HIGHEST)
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
        ItemStack cursorItem = event.getCursor();
        int slot = event.getSlot();

        // Handle menu item interactions
        if (menuItemManager.isMenuItem(currentItem)) {
            // This is a menu item being clicked
            if (menuItemManager.handleMenuItemClick(player, currentItem, event)) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }

        // Prevent moving items to/from crafting slots if they would interfere with menu items
        if (event.getInventory().getType() == InventoryType.CRAFTING) {
            if (menuItemManager.isCraftingSlot(slot)) {
                // Player is trying to interact with a crafting slot
                if (menuItemManager.isMenuItem(currentItem)) {
                    // Prevent any manipulation of menu items
                    event.setCancelled(true);
                    player.updateInventory();
                    return;
                } else if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                    // Prevent placing items in menu item slots
                    event.setCancelled(true);
                    player.updateInventory();
                    return;
                }
            }
        }

        // Prevent placing menu items anywhere else
        if (menuItemManager.isMenuItem(cursorItem)) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        // Handle spectral knight gear and other special items
        if (currentItem != null && currentItem.getType() != Material.AIR &&
                currentItem.hasItemMeta() && currentItem.getItemMeta().hasDisplayName()) {

            handleSpectralKnightGear(player, currentItem);
            handleHighLevelEnhancedItems(currentItem);
        }

        // Handle shift-click prevention for menu items
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (menuItemManager.isMenuItem(currentItem)) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }

        // Handle number key swapping
        if (event.getAction() == InventoryAction.HOTBAR_SWAP ||
                event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            if (menuItemManager.isCraftingSlot(slot)) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }
    }

    /**
     * Prevent dragging items over menu item slots
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Prevent dragging menu items
        if (menuItemManager.isMenuItem(event.getOldCursor())) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        // Prevent dragging over crafting slots in crafting inventory
        if (event.getInventory().getType() == InventoryType.CRAFTING) {
            for (int slot : event.getRawSlots()) {
                if (menuItemManager.isCraftingSlot(slot)) {
                    event.setCancelled(true);
                    player.updateInventory();
                    return;
                }
            }
        }
    }

    /**
     * Prevent dropping menu items
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // Prevent dropping menu items
        if (menuItemManager.isMenuItem(droppedItem)) {
            event.setCancelled(true);
            player.updateInventory();

            // Notify player
            player.sendMessage(ChatColor.RED + "You cannot drop menu items!");
            logger.fine("Player " + player.getName() + " attempted to drop a menu item");
        }
    }

    /**
     * Prevent picking up menu items (in case they somehow get dropped)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem().getItemStack();

        // Check if it's a menu item that somehow got dropped
        if (menuItemManager.isMenuItem(item)) {
            event.setCancelled(true);
            event.getItem().remove(); // Remove the item from the world

            logger.warning("Menu item found on ground and removed: " + item.getType() +
                    " near player " + player.getName());
        }
    }

    /**
     * Prevent swapping menu items to offhand
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();

        // Check if either item is a menu item
        if (menuItemManager.isMenuItem(event.getMainHandItem()) ||
                menuItemManager.isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
            player.updateInventory();

            // Notify player
            player.sendMessage(ChatColor.RED + "You cannot move menu items!");
        }
    }

    /**
     * Handle spectral knight gear processing
     */
    private void handleSpectralKnightGear(Player player, ItemStack currentItem) {
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
    }

    /**
     * Handle high level enhanced items
     */
    private void handleHighLevelEnhancedItems(ItemStack item) {
        // Add glow effect to items with high enhancement level
        int plusLevel = getEnhancementLevel(item);
        if (plusLevel > 3) {
            addGlowEffect(item);
        }
    }

    /**
     * Create spectral knight gear for a player
     * This is a placeholder for the actual implementation
     */
    private void createSpectralKnightGear(Player player) {
        // TODO: Implement using the appropriate drop manager
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

        // Prevent equipping menu items as armor
        if (menuItemManager.isMenuItem(item)) {
            event.setCancelled(true);
            player.updateInventory();
            player.sendMessage(ChatColor.RED + "You cannot equip menu items!");
            return;
        }

        if (item != null && isArmor(item.getType()) &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // Cancel equipping armor directly
            event.setCancelled(true);
            player.updateInventory();
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

        // Prevent using menu items on furnaces
        if (menuItemManager.isMenuItem(handItem)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot use menu items on blocks!");
            return;
        }

        if ((handItem == null || handItem.getType() == Material.AIR) &&
                (event.getClickedBlock().getType() == Material.FURNACE ||
                        event.getClickedBlock().getType() == Material.TORCH)) {

            player.sendMessage(ChatColor.RED +
                    "This can be used to cook fish! Right click this furnace while holding raw fish to cook it.");
            event.setCancelled(true);
        }
    }

    /**
     * Additional safety check for any remaining interactions with menu items
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuItemSafetyCheck(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Final safety check - prevent any interaction with menu items
        if (menuItemManager.isMenuItem(item)) {
            // Only allow left-click in air (for opening menus)
            if (event.getAction() != Action.LEFT_CLICK_AIR) {
                event.setCancelled(true);
                player.updateInventory();
            }
        }
    }
}