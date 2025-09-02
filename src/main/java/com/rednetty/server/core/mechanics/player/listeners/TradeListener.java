package com.rednetty.server.core.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.settings.Toggles;
import com.rednetty.server.core.mechanics.player.social.trade.Trade;
import com.rednetty.server.core.mechanics.player.social.trade.TradeManager;
import com.rednetty.server.core.mechanics.player.social.trade.TradeMenu;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 *  Handles all trade-related events with proper synchronization and no double-handling
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
                    // TradeListener: TradeManager initialized
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
     *  Simplified inventory click handling - only handles trade-specific logic
     * No longer competing with TradeMenu for event handling
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

        // Only handle if player is in a trade and clicking in trade inventory
        if (trade == null || !isTradeInventory(event.getView())) {
            return;
        }

        TradeMenu tradeMenu = tm.getTradeMenu(trade);
        if (tradeMenu == null) {
            event.setCancelled(true);
            return;
        }

        //  Only handle clicks in the top inventory (trade inventory)
        // Let TradeMenu handle the actual click logic
        if (event.getClickedInventory() != null &&
                event.getClickedInventory().equals(event.getView().getTopInventory())) {
            // This is a click in the trade inventory - let TradeMenu handle it
            tradeMenu.handleClick(event);
            return;
        }

        //  Handle clicks in player inventory differently
        if (event.getClickedInventory() != null &&
                event.getClickedInventory().equals(player.getInventory())) {
            // This is a click in player's inventory while trade is open
            // Only allow left-clicks on items to add to trade
            if (event.getClick() == ClickType.LEFT &&
                    event.getCurrentItem() != null &&
                    event.getCurrentItem().getType() != org.bukkit.Material.AIR) {
                // Let TradeMenu handle this
                tradeMenu.handleClick(event);
            } else {
                // Cancel other types of clicks in player inventory during trade
                event.setCancelled(true);
            }
            return;
        }

        // Cancel any other interactions
        event.setCancelled(true);
    }

    /**
     *  Simplified drag handling - just prevent all drags in trade inventories
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        TradeManager tm = getTradeManager();
        if (tm == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Trade trade = tm.getPlayerTrade(player);

        if (trade != null && isTradeInventory(event.getView())) {
            // Always cancel drag events in trade inventories
            event.setCancelled(true);
        }
    }

    /**
     *   inventory close handling with better error handling
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        TradeManager tm = getTradeManager();
        if (tm == null) {
            return;
        }

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

        TradeManager tm = getTradeManager();
        if (tm != null) {
            try {
                if (tm.isPlayerTrading(player)) {
                    tm.cancelTrade(player);
                }
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
     *  Optimized move checking - only check occasionally to reduce lag
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only check if player actually moved significantly (not just head movement)
        if (event.getFrom().distance(event.getTo()) < 1.0) {
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
     *  Prevents hoppers and other containers from interacting with trade inventories.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Prevent hoppers and other containers from interacting with trade inventory
        if (isTradeInventory(event.getSource()) || isTradeInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    /**
     *  Better trade inventory detection
     */
    private boolean isTradeInventory(InventoryView view) {
        return view != null && view.getTitle() != null && view.getTitle().startsWith("Trade: ");
    }

    /**
     *  Better trade inventory detection for Inventory objects
     */
    private boolean isTradeInventory(org.bukkit.inventory.Inventory inventory) {
        if (inventory == null || inventory.getViewers().isEmpty()) {
            return false;
        }

        // Check if any viewer has a trade inventory open
        return inventory.getViewers().stream()
                .anyMatch(viewer -> isTradeInventory(viewer.getOpenInventory()));
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
     *   cleanup with better error handling
     */
    public void cleanup() {
        lastInteractionTime.clear();

        // Clean up any remaining trade menus
        TradeManager tm = getTradeManager();
        if (tm != null) {
            try {
                tm.clearAllTrades();
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "Error during trade listener cleanup", e);
                }
            }
        }
    }
//NEW
    /**
     *  player interaction handler with trading toggle validation
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

        //  Check if player has trading enabled
        if (!isPlayerTradingEnabled(player)) {
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "You have trading disabled! Use /toggle to enable it.");
            return;
        }

        //  Check if target has trading enabled
        if (!isPlayerTradingEnabled(target)) {
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + target.getDisplayName() + " has trading disabled!");
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
     *  Check if a player has trading enabled via toggles
     */
    private boolean isPlayerTradingEnabled(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        try {

            return Toggles.isToggled(player, "Trading");
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning("Error checking trading toggle for " + player.getName() + ": " + e.getMessage());
            }
            // Default to true if there's an error checking the toggle
            return true;
        }
    }

    /**
     *  command preprocessing with toggle status display
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
        //  Handle trade status command
        else if (message.startsWith("/trade status") || message.startsWith("/t status")) {
            event.setCancelled(true);

            boolean tradingEnabled = isPlayerTradingEnabled(player);
            player.sendMessage("§6§l--- Trading Status ---");
            player.sendMessage("§7Trading: " + (tradingEnabled ? "§aEnabled" : "§cDisabled"));
            player.sendMessage("§7Use §f/toggle §7to change your trading settings.");

            TradeManager tm = getTradeManager();
            if (tm != null && tm.isPlayerTrading(player)) {
                Trade trade = tm.getPlayerTrade(player);
                if (trade != null) {
                    Player otherPlayer = trade.getOtherPlayer(player);
                    player.sendMessage("§7Currently trading with: §f" + otherPlayer.getName());
                }
            }
        }
        //  Handle toggle shortcut
        else if (message.startsWith("/trade toggle") || message.startsWith("/t toggle")) {
            event.setCancelled(true);

            // This could redirect to the toggle command or provide a shortcut
            player.sendMessage("§7Use §f/toggle §7to manage your trading and other settings.");
            player.sendMessage("§7Look for the §f'Trading' §7option in the Social section.");
        }
    }

    /**
     *  teleport handler with better feedback about trading status
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

                                    // Provide helpful information about trading settings
                                    if (!isPlayerTradingEnabled(player)) {
                                        player.sendMessage(ChatColor.YELLOW + "💡 " + ChatColor.GRAY + "Tip: Your trading is currently disabled. Use /toggle to enable it.");
                                    }
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
     * Manual initialization method for cases where the constructor initialization fails
     */
    public void setTradeManager(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
        if (plugin != null) {
            plugin.getLogger().info("TradeListener: TradeManager set manually");
        }
    }
}