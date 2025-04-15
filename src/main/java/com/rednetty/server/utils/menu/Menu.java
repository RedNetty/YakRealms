package com.rednetty.server.utils.menu;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for creating interactive menus/GUIs
 */
public abstract class Menu {

    // Static registry of all open menus
    private static final Map<UUID, Menu> openMenus = new HashMap<>();

    // Static handler for menu events
    private static boolean initialized = false;

    // Instance properties
    protected final Player player;
    protected final Inventory inventory;
    protected final Map<Integer, MenuItem> items = new HashMap<>();

    /**
     * Creates a new menu for the specified player
     *
     * @param player The player who will see the menu
     * @param title  The title of the inventory
     * @param size   The size of the inventory (must be a multiple of 9)
     */
    public Menu(Player player, String title, int size) {
        this.player = player;

        // Ensure size is valid (multiple of 9, max 54)
        size = Math.min(54, ((size + 8) / 9) * 9);

        this.inventory = Bukkit.createInventory(null, size, title);

        // Register menu event handlers if not already done
        if (!initialized) {
            Bukkit.getPluginManager().registerEvents(new MenuListener(), YakRealms.getInstance());
            initialized = true;
        }
    }

    /**
     * Opens the menu for the player
     */
    public void open() {
        // Update all items in the inventory before opening
        updateInventory();

        // Register this menu as the player's open menu
        openMenus.put(player.getUniqueId(), this);

        // Open the inventory for the player
        player.openInventory(inventory);
    }

    /**
     * Closes the menu for the player
     */
    public void close() {
        player.closeInventory();
    }

    /**
     * Sets an item in the menu
     *
     * @param slot The slot to set the item in
     * @param item The menu item to set
     */
    public void setItem(int slot, MenuItem item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            items.put(slot, item);
            inventory.setItem(slot, item.toItemStack());
        }
    }

    /**
     * Sets a raw ItemStack in the menu with no click handler
     *
     * @param slot The slot to set the item in
     * @param item The ItemStack to set
     */
    public void setItem(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            MenuItem menuItem = new MenuItem(item);
            items.put(slot, menuItem);
            inventory.setItem(slot, item);
        }
    }

    /**
     * Gets the item at the specified slot
     *
     * @param slot The slot to get the item from
     * @return The menu item at the slot, or null if none
     */
    public MenuItem getItem(int slot) {
        return items.get(slot);
    }

    /**
     * Removes the item at the specified slot
     *
     * @param slot The slot to remove the item from
     */
    public void removeItem(int slot) {
        items.remove(slot);
        inventory.setItem(slot, null);
    }

    /**
     * Updates all items in the inventory
     */
    protected void updateInventory() {
        for (Map.Entry<Integer, MenuItem> entry : items.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue().toItemStack());
        }
    }

    /**
     * Handles a click on a slot in the menu
     *
     * @param slot The slot that was clicked
     */
    protected void handleClick(int slot) {
        MenuItem item = items.get(slot);
        if (item != null && item.getClickHandler() != null) {
            item.getClickHandler().onClick(player, slot);
        }
    }

    /**
     * Event handler for inventory close
     */
    protected void onClose() {
        openMenus.remove(player.getUniqueId());
    }

    /**
     * Static method to get the menu a player currently has open
     *
     * @param player The player to check
     * @return The menu the player has open, or null if none
     */
    public static Menu getOpenMenu(Player player) {
        return openMenus.get(player.getUniqueId());
    }

    /**
     * Static method to close all open menus
     */
    public static void closeAllMenus() {
        for (Menu menu : openMenus.values()) {
            menu.close();
        }
        openMenus.clear();
    }

    /**
     * Inner class to handle menu events
     */
    private static class MenuListener implements Listener {

        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            Menu menu = openMenus.get(player.getUniqueId());

            if (menu != null && event.getView().getTopInventory().equals(menu.inventory)) {
                event.setCancelled(true);

                // Only handle clicks in the top inventory
                if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                    menu.handleClick(event.getRawSlot());
                }
            }
        }

        @EventHandler
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            Menu menu = openMenus.get(player.getUniqueId());

            if (menu != null && event.getView().getTopInventory().equals(menu.inventory)) {
                // Cancel if any slots are in the top inventory
                for (int slot : event.getRawSlots()) {
                    if (slot < event.getView().getTopInventory().getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player)) {
                return;
            }

            Player player = (Player) event.getPlayer();
            Menu menu = openMenus.get(player.getUniqueId());

            if (menu != null && event.getView().getTopInventory().equals(menu.inventory)) {
                menu.onClose();
            }
        }
    }
}