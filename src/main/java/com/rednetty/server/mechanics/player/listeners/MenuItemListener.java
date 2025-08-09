package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.MenuItemManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Complete menu item event handler based on successful CoinPouchListener patterns.
 * This replaces the scattered menu item handling in InventoryListener.
 */
public class MenuItemListener extends BaseListener {
    private static final long UPDATE_DELAY = 3L;
    private static final long INITIAL_JOIN_DELAY = 5L;
    private final YakRealms plugin;
    private final MenuItemManager menuItemManager;
    private final Logger logger;
    private final Set<UUID> updateLock = new HashSet<>();
    private BukkitTask updateTask;

    public MenuItemListener(YakRealms plugin) {
        this.plugin = plugin;
        this.menuItemManager = MenuItemManager.getInstance();
        this.logger = plugin.getLogger();

        initializeOnlinePlayers();
        startUpdateTask();
    }

    private void initializeOnlinePlayers() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                safeInitializePlayer(player);
            }
        });
    }

    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        if (player.getOpenInventory().getTopInventory() instanceof CraftingInventory) {
                            safeUpdatePlayerMenuItems(player);
                        }
                    } catch (Exception e) {
                        logError("Error in menu item update task", player, e);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, UPDATE_DELAY);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        scheduleSafeUpdate(player, 1L);
        removeMenuItemsFromInventory(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack item = event.getItem().getItemStack();

        if (menuItemManager.isMenuItem(item)) {
            event.setCancelled(true);
            event.getItem().remove();
            logWarning("Prevented menu item pickup and removed item", player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        safeInitializePlayer(player);
        scheduleSafeUpdate(player, INITIAL_JOIN_DELAY);
        removeMenuItemsFromInventory(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check for menu item interactions first
        if (menuItemManager.isMenuItem(event.getCurrentItem()) ||
                menuItemManager.isMenuItem(event.getCursor())) {
            event.setCancelled(true);

            // Handle menu item click action if clicking on a menu item
            if (menuItemManager.isMenuItem(event.getCurrentItem())) {
                menuItemManager.handleMenuItemClick(player, event.getCurrentItem(), event);
            }

            removeMenuItemsFromInventory(player);
            return;
        }

        if (event.getInventory() instanceof CraftingInventory) {
            handleCraftingInventoryClick(event, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Cancel if dragging menu item
        if (event.getOldCursor() != null && menuItemManager.isMenuItem(event.getOldCursor())) {
            event.setCancelled(true);
            removeMenuItemsFromInventory(player);
            return;
        }

        // Prevent dragging over crafting slots that might contain menu items
        if (event.getInventory() instanceof CraftingInventory) {
            for (int slot : event.getRawSlots()) {
                if (menuItemManager.isCraftingSlotInView(slot, player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (menuItemManager.isMenuItem(item)) {
            event.setCancelled(true);
            item.setAmount(0);
            logWarning("Prevented menu item inventory move", null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clearCraftingGrid(player);
        removeMenuItemsFromInventory(player);
        updateLock.remove(player.getUniqueId());
        menuItemManager.clearPlayerFromTracking(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        clearCraftingGrid(player);
        event.getDrops().removeIf(menuItemManager::isMenuItem);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            clearCraftingGrid(player);
            removeMenuItemsFromInventory(player);
            menuItemManager.clearPlayerFromTracking(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player &&
                event.getInventory() instanceof CraftingInventory) {
            scheduleSafeUpdate(player, 2L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftItem(CraftItemEvent event) {
        if (event.getInventory() instanceof CraftingInventory) {
            // Check if any menu items are in the crafting grid
            for (int i = 1; i <= 4; i++) {
                ItemStack item = event.getInventory().getItem(i);
                if (menuItemManager.isMenuItem(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (menuItemManager.isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            removeMenuItemsFromInventory(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (menuItemManager.isMenuItem(event.getMainHandItem()) ||
                menuItemManager.isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
            removeMenuItemsFromInventory(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (menuItemManager.isMenuItem(event.getCurrentItem()) ||
                menuItemManager.isMenuItem(event.getCursor())) {
            event.setCancelled(true);
            removeMenuItemsFromInventory((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (event.getCommand().toLowerCase().contains("reload")) {
            Bukkit.getOnlinePlayers().forEach(this::removeMenuItemsFromInventory);
        }
    }

    // Helper methods
    private void safeInitializePlayer(Player player) {
        try {
            removeMenuItemsFromInventory(player);
        } catch (Exception e) {
            logError("Error initializing player", player, e);
        }
    }

    private void safeUpdatePlayerMenuItems(Player player) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || updateLock.contains(playerId)) return;

        try {
            updateLock.add(playerId);
            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv instanceof CraftingInventory) {
                menuItemManager.setupMenuItems(player);
            }
        } catch (Exception e) {
            logError("Error updating menu items", player, e);
        } finally {
            updateLock.remove(playerId);
        }
    }

    private void handleCraftingInventoryClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();

        if (menuItemManager.isCraftingSlotInView(slot, player)) {
            ItemStack clickedItem = event.getCurrentItem();
            if (menuItemManager.isMenuItem(clickedItem)) {
                event.setCancelled(true);
                menuItemManager.handleMenuItemClick(player, clickedItem, event);
                return;
            }

            // Prevent placing non-menu items in menu slots
            if (event.getCursor() != null && !menuItemManager.isMenuItem(event.getCursor())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void clearCraftingGrid(Player player) {
        try {
            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv instanceof CraftingInventory) {
                for (int i = 1; i <= 4; i++) {
                    ItemStack item = topInv.getItem(i);
                    if (menuItemManager.isMenuItem(item)) {
                        topInv.setItem(i, null);
                    }
                }
            }
        } catch (Exception e) {
            logError("Error clearing crafting grid", player, e);
        }
    }

    private void removeMenuItemsFromInventory(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (menuItemManager.isMenuItem(item)) {
                player.getInventory().setItem(i, null);
                logWarning("Removed menu item from inventory", player);
            }
        }
    }

    private void scheduleSafeUpdate(Player player, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    safeUpdatePlayerMenuItems(player);
                }
            }
        }.runTaskLater(plugin, delay);
    }

    private void logError(String message, Player player, Exception e) {
        String playerInfo = player != null ? " (Player: " + player.getName() + ")" : "";
        if (e != null) {
            logger.log(Level.SEVERE, message + playerInfo, e);
        } else {
            logger.severe(message + playerInfo);
        }
    }

    private void logWarning(String message, Player player) {
        String playerInfo = player != null ? " (Player: " + player.getName() + ")" : "";
        logger.warning(message + playerInfo);
    }

    public void cleanup() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        updateLock.clear();
    }
}