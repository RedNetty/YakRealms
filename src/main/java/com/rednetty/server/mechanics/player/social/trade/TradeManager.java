package com.rednetty.server.mechanics.player.social.trade;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TradeManager handles the management of trade sessions between players.
 * It manages trade requests, active trades, trade cancellations, and trade completions.
 */
public class TradeManager {

    private final YakRealms plugin; // Reference to the main plugin instance
    private final Map<UUID, Trade> activeTrades; // Map of active trades by player UUID
    private final Map<UUID, UUID> pendingTradeRequests; // Map of pending trade requests by sender UUID to recipient UUID
    private final Map<Trade, TradeMenu> tradeMenus; // Map of trades to their associated TradeMenu

    /**
     * Constructs a new TradeManager.
     *
     * @param plugin The main plugin instance.
     */
    public TradeManager(YakRealms plugin) {
        this.plugin = plugin;
        this.activeTrades = new HashMap<>();
        this.pendingTradeRequests = new HashMap<>();
        this.tradeMenus = new HashMap<>();
    }

    /**
     * Sends a trade request from one player to another.
     *
     * @param sender    The player sending the trade request.
     * @param recipient The player receiving the trade request.
     */
    public void sendTradeRequest(Player sender, Player recipient) {
        plugin.getLogger().info("Sending trade request from " + sender.getDisplayName() + " to " + recipient.getDisplayName());

        if (isPlayerTrading(sender) || isPlayerTrading(recipient)) {
            sender.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "One of the players is already in a trade.");
            return;
        }

        if (hasPendingTradeRequest(recipient)) {
            sender.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "The player already has a pending trade request.");
            return;
        }

        pendingTradeRequests.put(sender.getUniqueId(), recipient.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "✉ " + ChatColor.GRAY + "Trade request sent to " + ChatColor.YELLOW + recipient.getDisplayName());
        recipient.sendMessage(ChatColor.GREEN + "✉ " + ChatColor.YELLOW + sender.getDisplayName() + ChatColor.GRAY + " has sent you a trade request. Open their interaction menu to accept.");

        // Play sounds and spawn particles for both players
        playSoundAndParticles(sender, recipient, Sound.BLOCK_NOTE_BLOCK_CHIME, Particle.END_ROD, 1.2f, 1.0f);
    }

    /**
     * Checks if a player has any pending trade requests.
     *
     * @param recipient The player to check for pending trade requests.
     * @return True if the player has a pending trade request, false otherwise.
     */
    public boolean hasPendingTradeRequest(Player recipient) {
        return pendingTradeRequests.containsValue(recipient.getUniqueId());
    }

    /**
     * Checks if a player has a pending trade request from a specific sender.
     *
     * @param recipient The player who may have received the trade request.
     * @param sender    The player who may have sent the trade request.
     * @return True if there is a pending trade request from the sender to the recipient.
     */
    public boolean hasPendingTradeRequest(Player recipient, Player sender) {
        UUID recipientUUID = pendingTradeRequests.get(sender.getUniqueId());
        return recipientUUID != null && recipientUUID.equals(recipient.getUniqueId());
    }

    /**
     * Accepts a pending trade request.
     *
     * @param recipient The player accepting the trade request.
     * @param sender    The player who sent the trade request.
     */
    public void acceptTradeRequest(Player recipient, Player sender) {
        plugin.getLogger().info("Accepting trade request from " + sender.getDisplayName() + " by " + recipient.getDisplayName());

        if (!hasPendingTradeRequest(recipient, sender)) {
            recipient.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "No pending trade request from " + ChatColor.YELLOW + sender.getDisplayName());
            return;
        }

        pendingTradeRequests.remove(sender.getUniqueId());
        initiateTrade(sender, recipient);

        // Play sounds and spawn particles for both players
        playSoundAndParticles(sender, recipient, Sound.BLOCK_ENCHANTMENT_TABLE_USE, Particle.SPELL_INSTANT, 1.0f, 1.0f);
    }

    /**
     * Cancels a pending trade request.
     *
     * @param sender The player cancelling the trade request.
     */
    public void cancelTradeRequest(Player sender) {
        UUID recipientUUID = pendingTradeRequests.remove(sender.getUniqueId());
        if (recipientUUID != null) {
            Player recipient = plugin.getServer().getPlayer(recipientUUID);
            if (recipient != null && recipient.isOnline()) {
                recipient.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade request from " + ChatColor.YELLOW + sender.getDisplayName() + ChatColor.GRAY + " has been cancelled.");
                recipient.playSound(recipient.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            sender.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Your trade request has been cancelled.");
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            plugin.getLogger().info("Trade request cancelled by " + sender.getDisplayName());
        }
    }

    /**
     * Initiates a trade between two players.
     *
     * @param initiator The player initiating the trade.
     * @param target    The player being invited to trade.
     */
    public void initiateTrade(Player initiator, Player target) {
        plugin.getLogger().info("Initiating trade between " + initiator.getDisplayName() + " and " + target.getDisplayName());

        if (isPlayerTrading(initiator) || isPlayerTrading(target)) {
            initiator.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "One of the players is already in a trade.");
            plugin.getLogger().warning("Trade initiation failed: One of the players is already in a trade.");
            return;
        }

        Trade trade = new Trade(initiator, target);
        activeTrades.put(initiator.getUniqueId(), trade);
        activeTrades.put(target.getUniqueId(), trade);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    TradeMenu tradeMenu = new TradeMenu(plugin, trade);
                    tradeMenus.put(trade, tradeMenu);
                    tradeMenu.openMenu();
                    plugin.getLogger().info("Trade menu opened for " + initiator.getDisplayName() + " and " + target.getDisplayName());
                } catch (Exception e) {
                    plugin.getLogger().severe("Error opening trade menu: " + e.getMessage());
                    e.printStackTrace();
                    cancelTrade(initiator);
                }
            }
        }.runTask(plugin);
    }

    /**
     * Checks if a player is currently involved in a trade.
     *
     * @param player The player to check.
     * @return True if the player is trading, false otherwise.
     */
    public boolean isPlayerTrading(Player player) {
        return activeTrades.containsKey(player.getUniqueId());
    }

    /**
     * Cancels an active trade for a player.
     *
     * @param player The player whose trade is being cancelled.
     */
    public void cancelTrade(Player player) {
        plugin.getLogger().info("Cancelling trade for " + player.getDisplayName());
        Trade trade = activeTrades.remove(player.getUniqueId());
        if (trade != null) {
            Player otherPlayer = trade.getOtherPlayer(player);
            activeTrades.remove(otherPlayer.getUniqueId());
            tradeMenus.remove(trade);

            // Return items to both players
            returnItems(player, trade.getPlayerItems(player));
            returnItems(otherPlayer, trade.getPlayerItems(otherPlayer));

            player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled. Your items have been returned.");
            otherPlayer.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Trade cancelled by " + player.getDisplayName() + ". Your items have been returned.");

            // Play sounds for both players
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            otherPlayer.playSound(otherPlayer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            // Close inventories for both players
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.closeInventory();
                    otherPlayer.closeInventory();
                }
            }.runTask(plugin);

            plugin.getLogger().info("Trade cancelled between " + player.getDisplayName() + " and " + otherPlayer.getDisplayName());
        } else {
            plugin.getLogger().warning("Attempted to cancel non-existent trade for " + player.getDisplayName());
        }
    }

    /**
     * Completes a trade by exchanging items between the two players.
     *
     * @param trade The trade to complete.
     */
    public void completeTrade(Trade trade) {
        if (trade.isConfirmed()) {
            Player initiator = trade.getInitiator();
            Player target = trade.getTarget();

            // Exchange items between players
            exchangeItems(trade);

            // Mark trade as completed
            trade.setCompleted(true);

            // Remove from active trades
            activeTrades.remove(initiator.getUniqueId());
            activeTrades.remove(target.getUniqueId());
            tradeMenus.remove(trade);

            // Send success messages
            initiator.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY + "Trade completed successfully!");
            target.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY + "Trade completed successfully!");

            // Play level-up sounds for both players
            initiator.playSound(initiator.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

            plugin.getLogger().info("Trade completed between " + initiator.getDisplayName() + " and " + target.getDisplayName());
        }
    }

    /**
     * Gets the active trade for a player.
     *
     * @param player The player whose trade to retrieve.
     * @return The Trade object if the player is trading, null otherwise.
     */
    public Trade getPlayerTrade(Player player) {
        return activeTrades.get(player.getUniqueId());
    }

    /**
     * Gets the TradeMenu associated with a trade.
     *
     * @param trade The trade whose menu to retrieve.
     * @return The TradeMenu associated with the trade.
     */
    public TradeMenu getTradeMenu(Trade trade) {
        return tradeMenus.get(trade);
    }

    /**
     * Clears all active trades and pending trade requests.
     */
    public void clearAllTrades() {
        activeTrades.clear();
        pendingTradeRequests.clear();
        tradeMenus.clear();
        plugin.getLogger().info("All trades and trade requests cleared");
    }

    /**
     * Returns items to a player, dropping them if the inventory is full.
     *
     * @param player The player to return items to.
     * @param items  The list of items to return.
     */
    private void returnItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            // Drop leftover items on the ground
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        player.updateInventory();
        plugin.getLogger().info("Returned " + items.size() + " items to " + player.getDisplayName());
    }

    /**
     * Exchanges items between two players as part of a completed trade.
     *
     * @param trade The trade to process.
     */
    private void exchangeItems(Trade trade) {
        // Exchange items from initiator to target
        exchangePlayerItems(trade.getInitiator(), trade.getTarget(), trade.getPlayerItems(trade.getInitiator()));
        // Exchange items from target to initiator
        exchangePlayerItems(trade.getTarget(), trade.getInitiator(), trade.getPlayerItems(trade.getTarget()));
    }

    /**
     * Transfers items from one player to another.
     *
     * @param from  The player giving items.
     * @param to    The player receiving items.
     * @param items The list of items to transfer.
     */
    private void exchangePlayerItems(Player from, Player to, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            HashMap<Integer, ItemStack> leftover = to.getInventory().addItem(item.clone());
            // Drop any items that didn't fit in the inventory
            for (ItemStack drop : leftover.values()) {
                to.getWorld().dropItemNaturally(to.getLocation(), drop);
            }
        }
        to.updateInventory();
        plugin.getLogger().info("Exchanged " + items.size() + " items from " + from.getDisplayName() + " to " + to.getDisplayName());
    }

    /**
     * Plays sound and spawns particles for two players.
     *
     * @param sender    The first player.
     * @param recipient The second player.
     * @param sound     The sound to play.
     * @param particle  The particle effect to spawn.
     * @param pitch1    The pitch of the sound for the sender.
     * @param pitch2    The pitch of the sound for the recipient.
     */
    private void playSoundAndParticles(Player sender, Player recipient, Sound sound, Particle particle, float pitch1, float pitch2) {
        sender.playSound(sender.getLocation(), sound, 0.7f, pitch1);
        recipient.playSound(recipient.getLocation(), sound, 0.7f, pitch2);

        // Spawn particles around both players
        sender.spawnParticle(particle, sender.getLocation().add(0, 1.8, 0), 15, 0.5, 0.1, 0.5, 0.05);
        recipient.spawnParticle(particle, recipient.getLocation().add(0, 1.8, 0), 15, 0.5, 0.1, 0.5, 0.05);
    }
}