package com.rednetty.server.core.mechanics.item.scroll;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.item.enchants.EnchantmentProcessor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all events related to scrolls including:
 * - Protection scroll application
 * - Enhancement scroll usage
 * - Scroll purchase chat handling
 * - Preventing scroll items from being used as regular items
 */
public class ScrollHandler implements Listener {

    private static final String CRAFTING_INVENTORY = "container.crafting";
    private final EnchantmentProcessor enchantmentProcessor;

    /**
     * Creates a new scroll handler
     */
    public ScrollHandler() {
        this.enchantmentProcessor = new EnchantmentProcessor();
    }

    /**
     * Registers this handler with the server
     */
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
        YakRealms.log("Scroll handler has been registered");
    }

    /**
     * Prevents using scrolls as regular items
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onScrollUse(PlayerInteractEvent event) {
        ItemStack itemInHand = event.getItem();

        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            return;
        }

        // Cancel all interactions with scroll items (prevent using them as regular items)
        if ((itemInHand.getType() == Material.PAPER || itemInHand.getType() == Material.MAP) &&
                ((ItemAPI.isProtectionScroll(itemInHand) || ItemAPI.isEnhancementScroll(itemInHand)))) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles chat messages for scroll quantity input
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check if this is a response to a scroll purchase prompt
        if (ScrollPurchaseManager.handleQuantityInput(player, message)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles applying protection scrolls to items
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProtectionScroll(InventoryClickEvent event) {
        // Early validation
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack scrollItem = event.getCursor();
        ItemStack targetItem = event.getCurrentItem();

        // More granular validation with debug logging
        if (scrollItem == null || scrollItem.getType() == Material.AIR) {
            return;
        }

        if (targetItem == null || targetItem.getType() == Material.AIR) {
            return;
        }

        // Check if we're in a crafting inventory
        if (event.getInventory().getType() != InventoryType.CRAFTING ||
                event.getSlotType() == InventoryType.SlotType.ARMOR) {
            return;
        }

        // Check if the cursor item is a protection scroll
        if (!ItemAPI.isProtectionScroll(scrollItem)) {
            return;
        }

        YakRealms.debug("Protection scroll detected: " + scrollItem.getItemMeta().getDisplayName());

        // Check if the item is already protected
        if (ItemAPI.isProtected(targetItem)) {
            player.sendMessage(ChatColor.RED + "ITEM ALREADY PROTECTED");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Check if the item and scroll tiers match
        if (!ItemAPI.canEnchant(targetItem, scrollItem)) {
            player.sendMessage(ChatColor.RED + "ITEM CAN'T BE PROTECTED: MUST BE THE SAME TIER");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Apply protection
        event.setCancelled(true);
        event.setCurrentItem(ItemAPI.makeProtected(targetItem));

        // Success feedback
        player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD +
                "       ->  ITEM PROTECTED");
        player.sendMessage(ChatColor.GRAY.toString() + ChatColor.ITALIC +
                "       " + targetItem.getItemMeta().getDisplayName());

        // Play success sound
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        // Consume protection scroll
        consumeItem(event, scrollItem);
    }

    /**
     * Handles enhancing items with scrolls
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnhancementScroll(InventoryClickEvent event) {
        // Early validation
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack scrollItem = event.getCursor();
        ItemStack targetItem = event.getCurrentItem();

        // More granular validation with debug logging
        if (scrollItem == null || scrollItem.getType() == Material.AIR) {
            return;
        }

        if (targetItem == null || targetItem.getType() == Material.AIR) {
            return;
        }

        // Check if we're in a crafting inventory
        if (event.getInventory().getType() != InventoryType.CRAFTING ||
                event.getSlotType() == InventoryType.SlotType.ARMOR) {
            return;
        }

        YakRealms.debug("Potential enhancement: Scroll=" + scrollItem.getType() + ", Target=" + targetItem.getType());
        YakRealms.debug("IsArmorScroll: " + ItemAPI.isArmorEnhancementScroll(scrollItem));
        YakRealms.debug("IsWeaponScroll: " + ItemAPI.isWeaponEnhancementScroll(scrollItem));
        YakRealms.debug("IsArmorItem: " + ItemAPI.isArmorItem(targetItem));
        YakRealms.debug("IsWeaponItem: " + ItemAPI.isWeaponItem(targetItem));

        // Handle armor enhancement scrolls
        if (ItemAPI.isArmorEnhancementScroll(scrollItem) && ItemAPI.isArmorItem(targetItem)) {
            event.setCancelled(true);
            enchantmentProcessor.processArmorEnhancement(player, event, scrollItem, targetItem);
            return;
        }

        // Handle weapon enhancement scrolls
        if (ItemAPI.isWeaponEnhancementScroll(scrollItem) && ItemAPI.isWeaponItem(targetItem)) {
            event.setCancelled(true);
            enchantmentProcessor.processWeaponEnhancement(player, event, scrollItem, targetItem);
            return;
        }
    }

    /**
     * Helper method to safely consume one item from a stack
     */
    private void consumeItem(InventoryClickEvent event, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            event.setCursor(null);
        }
    }
}