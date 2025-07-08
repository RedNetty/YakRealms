package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.social.trade.Trade;
import com.rednetty.server.mechanics.player.social.trade.TradeManager;
import com.rednetty.server.mechanics.player.social.trade.TradeMenu;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles all trade-related events including initiation, interaction, and cancellation.
 * Enhanced with robust null checking and error handling.
 */
public class TradeListener extends BaseListener {

    private final YakRealms plugin;
    private TradeManager tradeManager;
    private final Map<UUID, Long> lastInteractionTime = new HashMap<>();
    private static final long INTERACTION_COOLDOWN = 1000; // 1 second cooldown
    private static final double MAX_TRADE_DISTANCE = 10.0; // Maximum distance for trading

    public TradeListener(YakRealms plugin) {
        this.plugin = plugin;
        this.tradeManager = null; // Initialize as null, will be set later

        // Schedule a task to initialize the trade manager after plugin is fully loaded
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                initializeTradeManager();
            }, 20L); // 1 second delay
        }
    }

    /**
     * Initialize the trade manager safely
     */
    private void initializeTradeManager() {
        try {
            if (plugin != null) {
                this.tradeManager = plugin.getTradeManager();
                if (this.tradeManager != null) {
                    plugin.getLogger().info("TradeListener: TradeManager initialized successfully");
                } else {
                    plugin.getLogger().warning("TradeListener: TradeManager is still null after initialization");
                    // Retry initialization after another delay
                    plugin.getServer().getScheduler().runTaskLater(plugin, this::initializeTradeManager, 40L);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "TradeListener: Error initializing TradeManager", e);
        }
    }

    /**
     * Safe getter for trade manager with null checking
     */
    private TradeManager getTradeManager() {
        if (tradeManager == null && plugin != null) {
            tradeManager = plugin.getTradeManager();
            if (tradeManager == null) {
                plugin.getLogger().warning("TradeManager is not available yet");
            }
        }
        return tradeManager;
    }

    /**
     * Handles player interactions with other players for trade initiation/acceptance.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        TradeManager tm = getTradeManager();
        if (tm == null) {
            return; // Trade system not ready yet
        }

        Player player = event.getPlayer();

        // Check if the clicked entity is a player
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }

        Player target = (Player) event.getRightClicked();

        // Check if player is shift-right-clicking
        if (!player.isSneaking()) {
            return;
        }

        event.setCancelled(true);

        // Check cooldown to prevent spam
        if (isOnCooldown(player)) {
            return;
        }

        // Don't allow self-trading
        if (player.equals(target)) {
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "You cannot trade with yourself!");
            return;
        }

        // Check distance
        if (player.getLocation().distance(target.getLocation()) > MAX_TRADE_DISTANCE) {
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "You are too far away to trade!");
            return;
        }

        // Check if either player is already trading
        if (tm.isPlayerTrading(player)) {
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "You are already in a trade!");
            return;
        }

        if (tm.isPlayerTrading(target)) {
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + target.getDisplayName() + " is already trading!");
            return;
        }

        updateCooldown(player);

        // Check if target has a pending request from this player
        if (tm.hasPendingTradeRequest(target, player)) {
            // Target is accepting the trade request
            tm.acceptTradeRequest(target, player);
        } else if (tm.hasPendingTradeRequest(player, target)) {
            // Player already sent a request, remind them
            player.sendMessage(ChatColor.YELLOW + "⏳ " + ChatColor.GRAY + "You already have a pending trade request with " + target.getDisplayName());
        } else {
            // Send new trade request
            tm.sendTradeRequest(player, target);
        }
    }

    /**
     * Handles clicks within trade inventories.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        TradeManager tm = getTradeManager();
        if (tm == null) {
            return; // Trade system not ready yet
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Trade trade = tm.getPlayerTrade(player);

        if (trade == null || !isTradeInventory(event.getView())) {
            return;
        }

        TradeMenu tradeMenu = tm.getTradeMenu(trade);
        if (tradeMenu == null) {
            event.setCancelled(true);
            return;
        }

        // Handle shift-clicks from player inventory to trade inventory
        if (event.getClick() == ClickType.SHIFT_LEFT &&
                event.getClickedInventory() != null &&
                event.getClickedInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
            return;
        }

        // Handle clicks in the trade menu
        if (event.getView().getTopInventory().equals(tradeMenu.getInventory())) {
            tradeMenu.handleClick(event);
            return;
        }

        // Cancel any other interactions with the trade inventory
        event.setCancelled(true);
    }

    /**
     * Handles inventory drag events - prevents dragging items in trade inventories.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        TradeManager tm = getTradeManager();
        if (tm == null) {
            return; // Trade system not ready yet
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Trade trade = tm.getPlayerTrade(player);

        if (trade == null || !isTradeInventory(event.getView())) {
            return;
        }

        // Cancel all drag events in trade inventory
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Handles inventory closing - cancels trade if inventory is closed.
     *  Added comprehensive null checking to prevent NullPointerException
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        // Enhanced null checking
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // Get trade manager safely
        TradeManager tm = getTradeManager();
        if (tm == null) {
            // Trade system not ready - log this for debugging
            if (plugin != null) {
                plugin.getLogger().finest("TradeListener.onInventoryClose: TradeManager not available for player " + player.getName());
            }
            return;
        }

        // Check if player has an active trade
        Trade trade = null;
        try {
            trade = tm.getPlayerTrade(player);
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Error getting player trade for " + player.getName(), e);
            }
            return;
        }

        if (trade == null || !isTradeInventory(event.getView())) {
            return;
        }

        // Get trade menu safely
        TradeMenu tradeMenu = null;
        try {
            tradeMenu = tm.getTradeMenu(trade);
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Error getting trade menu for " + player.getName(), e);
            }
            return;
        }

        if (tradeMenu != null && !trade.isCompleted()) {
            // Only handle close if player is not in a prompt
            if (!TradeMenu.isPlayerInPrompt(player.getUniqueId())) {
                try {
                    tradeMenu.handleClose(event);
                } catch (Exception e) {
                    if (plugin != null) {
                        plugin.getLogger().log(Level.WARNING, "Error handling trade menu close for " + player.getName(), e);
                    }
                }
            }
        }
    }

    /**
     * Cancels trades when players quit the server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clear any prompt state
        TradeMenu.clearPromptState(player.getUniqueId());

        // Get trade manager safely
        TradeManager tm = getTradeManager();
        if (tm != null) {
            try {
                // Cancel any active trade
                if (tm.isPlayerTrading(player)) {
                    tm.cancelTrade(player);
                }

                // Cancel any pending trade requests
                tm.cancelTradeRequest(player);
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "Error handling trade cleanup on quit for " + player.getName(), e);
                }
            }
        }

        // Clear cooldown
        lastInteractionTime.remove(player.getUniqueId());
    }

    /**
     * Cancels trades when players get kicked from the server.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        TradeManager tm = getTradeManager();
        if (tm != null) {
            try {
                if (tm.isPlayerTrading(player)) {
                    tm.cancelTrade(player);
                }

                tm.cancelTradeRequest(player);
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "Error handling trade cleanup on kick for " + player.getName(), e);
                }
            }
        }

        lastInteractionTime.remove(player.getUniqueId());
    }

    /**
     * Cancels trades when players take damage.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        TradeManager tm = getTradeManager();
        if (tm != null && tm.isPlayerTrading(player)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        TradeManager currentTm = getTradeManager();
                        if (player.isOnline() && currentTm != null && currentTm.isPlayerTrading(player)) {
                            currentTm.cancelTrade(player);
                            player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled due to taking damage!");
                        }
                    } catch (Exception e) {
                        if (plugin != null) {
                            plugin.getLogger().log(Level.WARNING, "Error cancelling trade on damage for " + player.getName(), e);
                        }
                    }
                }
            }.runTask(plugin);
        }
    }

    /**
     * Cancels trades when players die.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        TradeManager tm = getTradeManager();
        if (tm != null) {
            try {
                if (tm.isPlayerTrading(player)) {
                    tm.cancelTrade(player);
                }

                tm.cancelTradeRequest(player);
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "Error handling trade cleanup on death for " + player.getName(), e);
                }
            }
        }
    }

    /**
     * Cancels trades when players teleport (to prevent distance exploits).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        TradeManager tm = getTradeManager();
        if (tm != null && tm.isPlayerTrading(player)) {
            Trade trade = tm.getPlayerTrade(player);
            if (trade != null) {
                Player otherPlayer = trade.getOtherPlayer(player);

                // Check if teleport destination is too far from the other player
                if (event.getTo() != null &&
                        event.getTo().distance(otherPlayer.getLocation()) > MAX_TRADE_DISTANCE) {

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                TradeManager currentTm = getTradeManager();
                                if (player.isOnline() && currentTm != null && currentTm.isPlayerTrading(player)) {
                                    currentTm.cancelTrade(player);
                                    player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled due to teleporting too far away!");
                                }
                            } catch (Exception e) {
                                if (plugin != null) {
                                    plugin.getLogger().log(Level.WARNING, "Error cancelling trade on teleport for " + player.getName(), e);
                                }
                            }
                        }
                    }.runTask(plugin);
                }
            }
        }
    }

    /**
     * Cancels trades when players move too far apart.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only check if player actually moved (not just head movement)
        if (event.getFrom().distance(event.getTo()) < 0.1) {
            return;
        }

        TradeManager tm = getTradeManager();
        if (tm != null && tm.isPlayerTrading(player)) {
            Trade trade = tm.getPlayerTrade(player);
            if (trade != null) {
                Player otherPlayer = trade.getOtherPlayer(player);

                // Check distance between players
                if (player.getLocation().distance(otherPlayer.getLocation()) > MAX_TRADE_DISTANCE) {
                    try {
                        tm.cancelTrade(player);
                        player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled - players too far apart!");
                        otherPlayer.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled - players too far apart!");
                    } catch (Exception e) {
                        if (plugin != null) {
                            plugin.getLogger().log(Level.WARNING, "Error cancelling trade on move for " + player.getName(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Cancels trades when players change worlds.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        TradeManager tm = getTradeManager();
        if (tm != null && tm.isPlayerTrading(player)) {
            Trade trade = tm.getPlayerTrade(player);
            if (trade != null) {
                Player otherPlayer = trade.getOtherPlayer(player);

                // Cancel trade if players are no longer in the same world
                if (!player.getWorld().equals(otherPlayer.getWorld())) {
                    try {
                        tm.cancelTrade(player);
                        player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled due to world change!");
                    } catch (Exception e) {
                        if (plugin != null) {
                            plugin.getLogger().log(Level.WARNING, "Error cancelling trade on world change for " + player.getName(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles chat commands during trading (for potential /trade cancel command).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase();

        if (message.startsWith("/trade cancel") || message.startsWith("/t cancel")) {
            event.setCancelled(true);

            TradeManager tm = getTradeManager();
            if (tm != null) {
                try {
                    if (tm.isPlayerTrading(player)) {
                        tm.cancelTrade(player);
                        player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled!");
                    } else {
                        player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "You are not currently in a trade!");
                    }
                } catch (Exception e) {
                    if (plugin != null) {
                        plugin.getLogger().log(Level.WARNING, "Error handling trade cancel command for " + player.getName(), e);
                    }
                    player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "Error cancelling trade!");
                }
            } else {
                player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "Trade system is not available!");
            }
        }
    }

    /**
     * Prevents hoppers and other containers from interacting with trade inventories.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Prevent hoppers and other containers from interacting with trade inventory
        if (isTradeInventory(event.getSource()) || isTradeInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    /**
     * Checks if an inventory view represents a trade inventory.
     */
    private boolean isTradeInventory(InventoryView view) {
        return view != null && view.getTitle() != null && view.getTitle().startsWith("Trade: ");
    }

    /**
     * Checks if an inventory is a trade inventory by checking its viewers.
     */
    private boolean isTradeInventory(Inventory inventory) {
        if (inventory == null || inventory.getViewers().isEmpty()) {
            return false;
        }
        return isTradeInventory(inventory.getViewers().get(0).getOpenInventory());
    }

    /**
     * Checks if a player is on cooldown for trade interactions.
     */
    private boolean isOnCooldown(Player player) {
        Long lastTime = lastInteractionTime.get(player.getUniqueId());
        if (lastTime == null) {
            return false;
        }

        return (System.currentTimeMillis() - lastTime) < INTERACTION_COOLDOWN;
    }

    /**
     * Updates the cooldown for a player.
     */
    private void updateCooldown(Player player) {
        lastInteractionTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Cleans up player data when they leave.
     */
    public void cleanup() {
        lastInteractionTime.clear();
    }

    /**
     * Manual initialization method for cases where the constructor initialization fails
     */
    public void setTradeManager(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
        if (plugin != null) {
            plugin.getLogger().info("TradeListener: TradeManager set manually");
        }
    }
}