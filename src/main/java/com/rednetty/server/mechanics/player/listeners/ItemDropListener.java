package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles item drops, item protection, and item pickups.
 */
public class ItemDropListener extends BaseListener {

    private final YakPlayerManager playerManager;

    // Track protected items with their owner and expiry time
    private final ConcurrentHashMap<UUID, ProtectedItem> protectedItems = new ConcurrentHashMap<>();
    private final HashMap<UUID, Long> lastMessageTime = new HashMap<>();

    /**
     * Class to track protected item data
     */
    private static class ProtectedItem {
        public final UUID ownerUuid;
        public final long expiryTime;

        public ProtectedItem(UUID ownerUuid, long expiryTime) {
            this.ownerUuid = ownerUuid;
            this.expiryTime = expiryTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    public ItemDropListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();

        // Start protection cleanup task
        startCleanupTask();
    }

    @Override
    public void initialize() {
        logger.info("Item drop listener initialized");
    }

    /**
     * Start task to clean up expired item protections
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Remove expired protections
                protectedItems.entrySet().removeIf(entry -> entry.getValue().isExpired());
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 30, 20 * 30); // Run every 30 seconds
    }

    /**
     * Prevent items from being destroyed by fire
     */
    @EventHandler
    public void onItemCombust(EntityCombustEvent event) {
        if (event.getEntityType() == EntityType.DROPPED_ITEM) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent items from being damaged
     */
    @EventHandler
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.DROPPED_ITEM) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle players dropping items
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();

        // Prevent operators from dropping items unless GM mode is disabled
        if (player.isOp() && !isGodModeDisabled(player)) {
            // Check for exceptions
            if (!isExemptOperator(player.getName())) {
                event.setCancelled(true);
                return;
            }
        }

        // Add drop protection if enabled
        if (Toggles.isToggled(player, "Drop Protection")) {
            applyDropProtection(player, event.getItemDrop());
        }
    }

    /**
     * Apply drop protection to an item
     */
    private void applyDropProtection(Player player, Item item) {
        // Register the item for protection
        UUID itemUuid = item.getUniqueId();
        UUID playerUuid = player.getUniqueId();

        // Set protection for 5 seconds
        long expiryTime = System.currentTimeMillis() + 5000;
        protectedItems.put(itemUuid, new ProtectedItem(playerUuid, expiryTime));

        // Visual indication of protection
        applyProtectionVisuals(item);
    }

    /**
     * Apply visual effects to protected items
     */
    private void applyProtectionVisuals(Item item) {
        // TODO: Implement visual effect for protected items
        // This could involve particles, glowing effect, or custom metadata

        // Set item to glowing temporarily
        item.setGlowing(true);

        // Remove glow after protection expires
        new BukkitRunnable() {
            @Override
            public void run() {
                if (item.isValid()) {
                    item.setGlowing(false);
                }
            }
        }.runTaskLater(plugin, 20 * 5); // 5 second duration
    }

    /**
     * Handle gem pickups
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGemPickup(EntityPickupItemEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        ItemStack itemStack = event.getItem().getItemStack();

        // Handle gem pickups specially
        if (!event.isCancelled() && itemStack.getType() == Material.EMERALD) {
            // Remove the dropped item
            event.getItem().remove();
            event.setCancelled(true);

            // Skip if gems disabled
            if (Toggles.isToggled(player, "Gems")) {
                return;
            }

            // Add to inventory
            player.getInventory().addItem(itemStack);

            // Notify player
            player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "                    +" +
                    ChatColor.GREEN + itemStack.getAmount() + ChatColor.GREEN + ChatColor.BOLD + "G");

            // Play sound
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            // Handle drop protection for other items
            handleProtectedItemPickup(event);
        }
    }

    /**
     * Handle pickup attempts for protected items
     */
    private void handleProtectedItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        Item item = event.getItem();
        UUID itemUuid = item.getUniqueId();

        // Check if item is protected
        if (protectedItems.containsKey(itemUuid)) {
            ProtectedItem protection = protectedItems.get(itemUuid);

            // Skip if protection expired
            if (protection.isExpired()) {
                protectedItems.remove(itemUuid);
                return;
            }

            // Prevent pickup if not the owner
            if (!protection.ownerUuid.equals(player.getUniqueId())) {
                event.setCancelled(true);

                // Notify player if they haven't been notified recently
                notifyProtectionMessage(player);
            } else {
                // Remove protection when owner picks up
                protectedItems.remove(itemUuid);
            }
        }
    }

    /**
     * Send protection message to player (with rate limiting)
     */
    private void notifyProtectionMessage(Player player) {
        if(lastMessageTime.containsKey(player.getUniqueId()) && lastMessageTime.get(player.getUniqueId()) + 2000L  > System.currentTimeMillis()) return;


        // For now, just send the message
        lastMessageTime.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(ChatColor.RED + "This item is protected by its owner.");
    }

    /**
     * Check if god mode is disabled for a player
     *
     * @param player The player to check
     * @return true if god mode is disabled
     */
    private boolean isGodModeDisabled(Player player) {
        return true;
    }

    /**
     * Check if a player is exempted from operator restrictions
     *
     * @param playerName The player name to check
     * @return true if exempted
     */
    private boolean isExemptOperator(String playerName) {
        // List of operators who can drop items even in GM mode
        // This is just an example implementation - adjust as needed
        return playerName.equalsIgnoreCase("hugestroker");
    }
}