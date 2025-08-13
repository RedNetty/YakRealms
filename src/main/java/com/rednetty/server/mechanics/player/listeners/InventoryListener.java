package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.BankManager;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.TransactionResult;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
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
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *  inventory listener focused on armor changes, health recalculation,
 * and general inventory operations. Menu items are handled by MenuItemListener.
 * UPDATED: Removed all menu item handling code - now handled by MenuItemListener
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
        logger.info(" inventory listener initialized - menu items handled by MenuItemListener");
    }


    /**
     *  Prevent armor from taking any durability damage ever
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if the damaged item is armor
        if (item != null && isArmor(item.getType())) {
            // Cancel all armor durability damage
            event.setCancelled(true);
            logger.fine("Prevented durability damage to armor for player: " + player.getName() +
                    " (item: " + item.getType() + ")");
        }
    }

    /**
     * Handle inventory closing for bank chests and cleanup
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

        // Menu items are now handled by MenuItemListener - no code needed here
    }

    /**
     * UPDATED:  inventory click handler focused on armor changes and special items
     * Menu item handling removed - now handled by MenuItemListener
     */
    @EventHandler(priority = EventPriority.HIGH)
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
        int rawSlot = event.getRawSlot();

        // Menu items are now handled by MenuItemListener at HIGHEST priority
        // This handler runs at HIGH priority, so menu items are already processed

        // Handle spectral knight gear and other special items
        if (currentItem != null && currentItem.getType() != Material.AIR &&
                currentItem.hasItemMeta() && currentItem.getItemMeta().hasDisplayName()) {

            handleSpectralKnightGear(player, currentItem);
            handleHighLevelItems(currentItem);
        }

        // Handle armor changes for health recalculation with comprehensive detection
        handleArmorChangeForHealthRecalculation(player, event, currentItem, cursorItem, rawSlot);
    }

    /**
     *  Comprehensive armor change detection and health recalculation trigger
     */
    private void handleArmorChangeForHealthRecalculation(Player player, InventoryClickEvent event,
                                                         ItemStack currentItem, ItemStack cursorItem, int rawSlot) {
        boolean isArmorChange = false;
        String changeType = "unknown";

        // Scenario 1: Direct armor slot interaction (removing/placing armor)
        if (isArmorSlot(rawSlot)) {
            isArmorChange = true;
            changeType = "direct_armor_slot";
        }
        // Scenario 2: Shift-clicking armor to equip it
        else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (currentItem != null && isArmor(currentItem.getType())) {
                isArmorChange = true;
                changeType = "shift_click_equip";
            }
        }
        // Scenario 3: Right-clicking armor to equip it
        else if (event.getClick().isRightClick() && currentItem != null && isArmor(currentItem.getType())) {
            isArmorChange = true;
            changeType = "right_click_equip";
        }
        // Scenario 4: Placing armor in armor slot with cursor
        else if (cursorItem != null && isArmor(cursorItem.getType()) && isArmorSlot(rawSlot)) {
            isArmorChange = true;
            changeType = "cursor_to_armor_slot";
        }
        // Scenario 5: Taking armor from armor slot to cursor
        else if (currentItem != null && isArmor(currentItem.getType()) && isArmorSlot(rawSlot) &&
                (cursorItem == null || cursorItem.getType() == Material.AIR)) {
            isArmorChange = true;
            changeType = "armor_slot_to_cursor";
        }

        if (isArmorChange) {
            logger.fine("Detected armor change for " + player.getName() + " (type: " + changeType + ")");

            
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !player.isDead()) {
                        try {
                            HealthListener healthListener = getHealthListener();
                            if (healthListener != null) {
                                healthListener.forceHealthRecalculation(player);
                            } else {
                                logger.warning("HealthListener not found for health recalculation");
                            }
                        } catch (Exception e) {
                            logger.warning("Error triggering health recalculation for " + player.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }.runTaskLater(plugin, 5L); // 5 ticks = 0.25 seconds delay to ensure inventory is updated
        }
    }

    /**
     *  Get the HealthListener instance for health recalculation
     */
    private HealthListener getHealthListener() {
        return YakRealms.getInstance().getPlayerMechanics().getListenerManager().getHealthListener();
    }

    /**
     *  Check if a raw slot is an armor slot (includes all armor slot scenarios)
     */
    private boolean isArmorSlot(int rawSlot) {
        // In player inventory view:
        // Slot 5 = boots, Slot 6 = leggings, Slot 7 = chestplate, Slot 8 = helmet
        return rawSlot >= 5 && rawSlot <= 8;
    }

    /**
     * Handle inventory dragging for armor changes
     * Menu item dragging is handled by MenuItemListener
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

        // Menu items are handled by MenuItemListener at HIGHEST priority
        // This handler runs at HIGH priority, so menu items are already processed

        // Check for armor dragging for health recalculation
        boolean isArmorDrag = false;
        if (event.getOldCursor() != null && isArmor(event.getOldCursor().getType())) {
            for (int rawSlot : event.getRawSlots()) {
                if (isArmorSlot(rawSlot)) {
                    isArmorDrag = true;
                    break;
                }
            }
        }

        if (isArmorDrag) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !player.isDead()) {
                        try {
                            HealthListener healthListener = getHealthListener();
                            if (healthListener != null) {
                                healthListener.forceHealthRecalculation(player);
                                logger.fine("Triggered health recalculation for " + player.getName() + " due to armor drag");
                            }
                        } catch (Exception e) {
                            logger.warning("Error triggering health recalculation for " + player.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }.runTaskLater(plugin, 5L);
        }
    }

    /**
     * Handle item dropping (non-menu items)
     * Menu item dropping is prevented by MenuItemListener
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        // Menu item dropping is handled by MenuItemListener
        // This can handle other special item drop logic if needed
    }

    /**
     * Handle item pickup (non-menu items)
     * Menu item pickup is prevented by MenuItemListener
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        // Menu item pickup is handled by MenuItemListener
        // This can handle other special item pickup logic if needed
    }

    /**
     * Handle hand item swapping (non-menu items)
     * Menu item swapping is prevented by MenuItemListener
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        // Menu item swapping is handled by MenuItemListener
        // This can handle other special item swap logic if needed
    }

    /**
     * Handle spectral knight gear processing
     */
    private void handleSpectralKnightGear(Player player, ItemStack currentItem) {
        if (currentItem.getItemMeta().getDisplayName().contains("Spectral")) {
            NBTAccessor nbtAccessor = new NBTAccessor(currentItem);

            if (!nbtAccessor.hasKey("gear")) {
                // Check if it's diamond armor
                switch (currentItem.getType()) {
                    case DIAMOND_HELMET:
                    case DIAMOND_CHESTPLATE:
                    case DIAMOND_LEGGINGS:
                    case DIAMOND_BOOTS:
                        // Replace with  gear - this is handled by appropriate drops manager
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
     * Handle high level  items
     */
    private void handleHighLevelItems(ItemStack item) {
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
     * UPDATED: Prevent players from putting armor on directly
     * Menu item equipping is handled by MenuItemListener
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorEquip(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Menu item interactions are handled by MenuItemListener
        // This only handles armor equipping prevention

        if (item != null && isArmor(item.getType()) &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // Cancel equipping armor directly
            event.setCancelled(true);
            player.updateInventory();

            // Trigger health recalculation if they somehow equipped it
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !player.isDead()) {
                        try {
                            HealthListener healthListener = getHealthListener();
                            if (healthListener != null) {
                                healthListener.forceHealthRecalculation(player);
                            }
                        } catch (Exception e) {
                            logger.warning("Error triggering health recalculation for armor equip: " + e.getMessage());
                        }
                    }
                }
            }.runTaskLater(plugin, 3L);
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
        item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
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
    @EventHandler(priority = EventPriority.HIGH)
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

        // Menu item interactions with blocks are handled by MenuItemListener
        // This only handles furnace-specific logic

        if ((handItem == null || handItem.getType() == Material.AIR) &&
                (event.getClickedBlock().getType() == Material.FURNACE ||
                        event.getClickedBlock().getType() == Material.TORCH)) {

            player.sendMessage(ChatColor.RED +
                    "This can be used to cook fish! Right click this furnace while holding raw fish to cook it.");
            event.setCancelled(true);
        }
    }
//NEW
    /**
     * NEW:  gem pickup handling with Auto Bank toggle support
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGemPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        ItemStack itemStack = event.getItem().getItemStack();

        // Handle gem pickups specially
        if (itemStack.getType() == Material.EMERALD) {
            event.setCancelled(true);
            event.getItem().remove();

            // Add to inventory first
            player.getInventory().addItem(itemStack);

            // Check if Auto Bank is enabled
            if (Toggles.isToggled(player, "Auto Bank")) {
                handleAutoBankDeposit(player, itemStack.getAmount());
            } else {
                // Regular gem pickup message
                player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "                    +" +
                        ChatColor.GREEN + itemStack.getAmount() + ChatColor.GREEN + ChatColor.BOLD + "G");
            }

            // Play sound
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    /**
     * NEW: Handle automatic banking of gems
     */
    private void handleAutoBankDeposit(Player player, int gemAmount) {
        try {
            // Remove gems from inventory and add to bank
            ItemStack gemsToRemove = new ItemStack(Material.EMERALD, gemAmount);
            player.getInventory().removeItem(gemsToRemove);

            // Add to bank using BankManager
            TransactionResult deposited = EconomyManager.getInstance().depositGems(player, gemAmount);

            if (deposited.isSuccess()) {
                //  auto-bank message
                player.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "                    +" +
                        ChatColor.GOLD + gemAmount + ChatColor.GOLD + ChatColor.BOLD + "G " +
                        ChatColor.GRAY + "(Auto-Banked)");

                // Show bank balance if debug is enabled
                if (Toggles.isToggled(player, "Debug")) {
                    int bankBalance = EconomyManager.getInstance().getBankGems(player);
                    player.sendMessage(ChatColor.GRAY + "Bank Balance: " + ChatColor.GOLD + bankBalance + "G");
                }
            } else {
                // Fallback to regular inventory if banking fails
                player.getInventory().addItem(new ItemStack(Material.EMERALD, gemAmount));
                player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "                    +" +
                        ChatColor.GREEN + gemAmount + ChatColor.GREEN + ChatColor.BOLD + "G " +
                        ChatColor.RED + "(Bank Full)");
            }
        } catch (Exception e) {
            logger.warning("Error with auto-bank for " + player.getName() + ": " + e.getMessage());
            // Fallback to regular pickup
            player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "                    +" +
                    ChatColor.GREEN + gemAmount + ChatColor.GREEN + ChatColor.BOLD + "G");
        }
    }

    /**
     *  inventory interaction with toggle-aware messaging
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryInteract(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        //  gem-related messaging based on Auto Bank toggle
        if (item != null && item.getType() == Material.EMERALD &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {

            boolean autoBankEnabled = Toggles.isToggled(player, "Auto Bank");
            int gemCount = 0;

            // Count total gems in inventory
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null && invItem.getType() == Material.EMERALD) {
                    gemCount += invItem.getAmount();
                }
            }

            player.sendMessage("§6§l--- Gem Status ---");
            player.sendMessage("§7Inventory Gems: §6" + gemCount + "G");

            if (autoBankEnabled) {
                player.sendMessage("§7Auto Bank: §aEnabled");
                player.sendMessage("§7Your gems will automatically go to your bank when picked up.");

                // Show bank balance
                try {
                    int bankBalance = EconomyManager.getInstance().getBankGems(player);
                    player.sendMessage("§7Bank Balance: §6" + bankBalance + "G");
                } catch (Exception e) {
                    player.sendMessage("§7Bank Balance: §cError retrieving");
                }
            } else {
                player.sendMessage("§7Auto Bank: §cDisabled");
                player.sendMessage("§7Use §f/toggle §7to enable automatic banking.");
            }

            event.setCancelled(true);
        }
    }


    /**
     *  inventory opening with toggle-aware features
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // Handle bank chest opening
        if (event.getInventory().getType() == InventoryType.CHEST) {
            String title = event.getView().getTitle();
            if (title.contains("Bank Chest")) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

                // Show auto-bank status when opening bank
                if (Toggles.isToggled(player, "Auto Bank")) {
                    player.sendMessage("§7💡 Auto Bank is §aenabled§7 - gems go here automatically!");
                } else {
                    player.sendMessage("§7💡 Auto Bank is §cdisabled§7 - use /toggle to enable automatic deposits!");
                }
            }
        }
    }


    /**
     * Handle general block interactions
     * Menu item block interactions are handled by MenuItemListener
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGeneralBlockInteract(PlayerInteractEvent event) {
        // Menu item interactions are handled by MenuItemListener
        // This can handle other block interaction logic if needed
    }
}