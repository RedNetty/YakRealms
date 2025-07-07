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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all trade-related events including initiation, interaction, and cancellation.
 */
public class TradeListener extends BaseListener{

    private final YakRealms plugin;
    private final TradeManager tradeManager;
    private final Map<UUID, Long> lastInteractionTime = new HashMap<>();
    private static final long INTERACTION_COOLDOWN = 1000; // 1 second cooldown
    private static final double MAX_TRADE_DISTANCE = 10.0; // Maximum distance for trading

    public TradeListener(YakRealms plugin) {
        super();
        this.plugin = plugin;
        this.tradeManager = plugin.getTradeManager();
    }



    /**
     * Handles player interactions with other players for trade initiation/acceptance.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
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
        if (tradeManager.isPlayerTrading(player)) {
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "You are already in a trade!");
            return;
        }

        if (tradeManager.isPlayerTrading(target)) {
            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + target.getDisplayName() + " is already trading!");
            return;
        }

        updateCooldown(player);

        // Check if target has a pending request from this player
        if (tradeManager.hasPendingTradeRequest(target, player)) {
            // Target is accepting the trade request
            tradeManager.acceptTradeRequest(target, player);
        } else if (tradeManager.hasPendingTradeRequest(player, target)) {
            // Player already sent a request, remind them
            player.sendMessage(ChatColor.YELLOW + "⏳ " + ChatColor.GRAY + "You already have a pending trade request with " + target.getDisplayName());
        } else {
            // Send new trade request
            tradeManager.sendTradeRequest(player, target);
        }
    }

    /**
     * Handles clicks within trade inventories.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Trade trade = tradeManager.getPlayerTrade(player);

        if (trade == null) {
            return;
        }

        TradeMenu tradeMenu = tradeManager.getTradeMenu(trade);
        if (tradeMenu == null || !event.getInventory().equals(tradeMenu.getInventory())) {
            return;
        }

        // Handle the click in the trade menu
        tradeMenu.handleClick(event);
    }

    /**
     * Handles inventory closing - cancels trade if inventory is closed.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Trade trade = tradeManager.getPlayerTrade(player);

        if (trade == null) {
            return;
        }

        TradeMenu tradeMenu = tradeManager.getTradeMenu(trade);
        if (tradeMenu == null || !event.getInventory().equals(tradeMenu.getInventory())) {
            return;
        }

        // Handle the inventory close in the trade menu
        tradeMenu.handleClose(event);
    }

    /**
     * Cancels trades when players quit the server.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel any active trade
        if (tradeManager.isPlayerTrading(player)) {
            tradeManager.cancelTrade(player);
        }

        // Cancel any pending trade requests
        tradeManager.cancelTradeRequest(player);

        // Clear cooldown
        lastInteractionTime.remove(player.getUniqueId());
    }

    /**
     * Cancels trades when players get kicked from the server.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        if (tradeManager.isPlayerTrading(player)) {
            tradeManager.cancelTrade(player);
        }

        tradeManager.cancelTradeRequest(player);
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

        if (tradeManager.isPlayerTrading(player)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && tradeManager.isPlayerTrading(player)) {
                        tradeManager.cancelTrade(player);
                        player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled due to taking damage!");
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

        if (tradeManager.isPlayerTrading(player)) {
            tradeManager.cancelTrade(player);
        }

        tradeManager.cancelTradeRequest(player);
    }

    /**
     * Cancels trades when players teleport (to prevent distance exploits).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (tradeManager.isPlayerTrading(player)) {
            Trade trade = tradeManager.getPlayerTrade(player);
            Player otherPlayer = trade.getOtherPlayer(player);

            // Check if teleport destination is too far from the other player
            if (event.getTo() != null &&
                    event.getTo().distance(otherPlayer.getLocation()) > MAX_TRADE_DISTANCE) {

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && tradeManager.isPlayerTrading(player)) {
                            tradeManager.cancelTrade(player);
                            player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled due to teleporting too far away!");
                        }
                    }
                }.runTask(plugin);
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

        if (tradeManager.isPlayerTrading(player)) {
            Trade trade = tradeManager.getPlayerTrade(player);
            Player otherPlayer = trade.getOtherPlayer(player);

            // Check distance between players
            if (player.getLocation().distance(otherPlayer.getLocation()) > MAX_TRADE_DISTANCE) {
                tradeManager.cancelTrade(player);
                player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled - players too far apart!");
                otherPlayer.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled - players too far apart!");
            }
        }
    }

    /**
     * Cancels trades when players change worlds.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (tradeManager.isPlayerTrading(player)) {
            Trade trade = tradeManager.getPlayerTrade(player);
            Player otherPlayer = trade.getOtherPlayer(player);

            // Cancel trade if players are no longer in the same world
            if (!player.getWorld().equals(otherPlayer.getWorld())) {
                tradeManager.cancelTrade(player);
                player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled due to world change!");
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

            if (tradeManager.isPlayerTrading(player)) {
                tradeManager.cancelTrade(player);
                player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled!");
            } else {
                player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "You are not currently in a trade!");
            }
        }
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
}