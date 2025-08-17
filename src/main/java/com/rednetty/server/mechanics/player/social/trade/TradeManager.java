package com.rednetty.server.mechanics.player.social.trade;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.settings.Toggles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TradeManager handles the management of trade sessions between players.
 * It manages trade requests, active trades, trade cancellations, and trade completions.
 * Updated for Adventure API and Paper Spigot 1.21.7.
 */
public class TradeManager {

    private final YakRealms plugin; // Reference to the main plugin instance
    private final Map<UUID, Trade> activeTrades; // Map of active trades by player UUID
    private final Map<UUID, UUID> pendingTradeRequests; // Map of pending trade requests by sender UUID to recipient UUID
    private final Map<Trade, TradeMenu> tradeMenus; // Map of trades to their associated TradeMenu
    private final MiniMessage miniMessage; // MiniMessage instance for text formatting

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
        this.miniMessage = MiniMessage.miniMessage();
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
     * Cancels a pending trade request.
     *
     * @param sender The player cancelling the trade request.
     */
    public void cancelTradeRequest(Player sender) {
        UUID recipientUUID = pendingTradeRequests.remove(sender.getUniqueId());
        if (recipientUUID != null) {
            Player recipient = plugin.getServer().getPlayer(recipientUUID);
            if (recipient != null && recipient.isOnline()) {
                Component recipientMessage = Component.text("✖ ", NamedTextColor.RED)
                        .append(Component.text("Trade request from ", NamedTextColor.GRAY))
                        .append(Component.text(sender.getDisplayName(), NamedTextColor.YELLOW))
                        .append(Component.text(" has been cancelled.", NamedTextColor.GRAY));
                recipient.sendMessage(recipientMessage);

                recipient.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
            }

            Component senderMessage = Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("Your trade request has been cancelled.", NamedTextColor.GRAY));
            sender.sendMessage(senderMessage);

            sender.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
            plugin.getLogger().info("Trade request cancelled by " + sender.getDisplayName());
        }
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

            Component playerMessage = Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("Trade cancelled. Your items have been returned.", NamedTextColor.GRAY));
            Component otherPlayerMessage = Component.text("✖ ", NamedTextColor.RED)
                    .append(Component.text("Trade cancelled by ", NamedTextColor.GRAY))
                    .append(Component.text(player.getDisplayName(), NamedTextColor.YELLOW))
                    .append(Component.text(". Your items have been returned.", NamedTextColor.GRAY));

            player.sendMessage(playerMessage);
            otherPlayer.sendMessage(otherPlayerMessage);

            // Play sounds for both players
            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));
            otherPlayer.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 1.0f, 1.0f));

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

            Component successMessage = Component.text("✓ ", NamedTextColor.GREEN)
                    .append(Component.text("Trade completed successfully!", NamedTextColor.GRAY));

            initiator.sendMessage(successMessage);
            target.sendMessage(successMessage);

            // Show title message for dramatic effect
            Title successTitle = Title.title(
                    Component.text("Trade Complete!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Items exchanged successfully", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
            );

            initiator.showTitle(successTitle);
            target.showTitle(successTitle);

            // Play level-up sounds for both players
            initiator.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1.0f, 2.0f));
            target.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1.0f, 2.0f));

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
     * Trade request sending with proper toggle validation
     */
    public void sendTradeRequest(Player sender, Player recipient) {
        plugin.getLogger().info("Sending trade request from " + sender.getDisplayName() + " to " + recipient.getDisplayName());

        // Check if sender has trading enabled
        if (!isPlayerTradingEnabled(sender)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("You have trading disabled! Use /toggle to enable it.", NamedTextColor.GRAY));
            sender.sendMessage(message);
            return;
        }

        // Check if recipient has trading enabled
        if (!isPlayerTradingEnabled(recipient)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text(recipient.getDisplayName() + " has trading disabled!", NamedTextColor.GRAY));
            sender.sendMessage(message);
            return;
        }

        if (isPlayerTrading(sender) || isPlayerTrading(recipient)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("One of the players is already in a trade.", NamedTextColor.GRAY));
            sender.sendMessage(message);
            return;
        }

        if (hasPendingTradeRequest(recipient)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("The player already has a pending trade request.", NamedTextColor.GRAY));
            sender.sendMessage(message);
            return;
        }

        pendingTradeRequests.put(sender.getUniqueId(), recipient.getUniqueId());

        Component senderMessage = Component.text("✉ ", NamedTextColor.GREEN)
                .append(Component.text("Trade request sent to ", NamedTextColor.GRAY))
                .append(Component.text(recipient.getDisplayName(), NamedTextColor.YELLOW));
        sender.sendMessage(senderMessage);

        Component recipientMessage = Component.text("✉ ", NamedTextColor.GREEN)
                .append(Component.text(sender.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(" has sent you a trade request. Open their interaction menu to accept.", NamedTextColor.GRAY));
        recipient.sendMessage(recipientMessage);

        // Play sounds and spawn particles for both players
        playSoundAndParticles(sender, recipient, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, Particle.END_ROD, 1.2f, 1.0f);
    }

    /**
     * Trade request acceptance with toggle validation
     */
    public void acceptTradeRequest(Player recipient, Player sender) {
        plugin.getLogger().info("Accepting trade request from " + sender.getDisplayName() + " by " + recipient.getDisplayName());

        // Double-check trading toggles before accepting
        if (!isPlayerTradingEnabled(recipient)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("You have trading disabled! Use /toggle to enable it.", NamedTextColor.GRAY));
            recipient.sendMessage(message);
            return;
        }

        if (!isPlayerTradingEnabled(sender)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text(sender.getDisplayName() + " has trading disabled!", NamedTextColor.GRAY));
            recipient.sendMessage(message);
            return;
        }

        if (!hasPendingTradeRequest(recipient, sender)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("No pending trade request from ", NamedTextColor.GRAY))
                    .append(Component.text(sender.getDisplayName(), NamedTextColor.YELLOW));
            recipient.sendMessage(message);
            return;
        }

        pendingTradeRequests.remove(sender.getUniqueId());
        initiateTrade(sender, recipient);

        // Play sounds and spawn particles for both players
        playSoundAndParticles(sender, recipient, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, Particle.INSTANT_EFFECT, 1.0f, 1.0f);
    }

    /**
     * Trade initiation with final toggle validation
     */
    public void initiateTrade(Player initiator, Player target) {
        plugin.getLogger().info("Initiating trade between " + initiator.getDisplayName() + " and " + target.getDisplayName());

        // Final check for trading toggles
        if (!isPlayerTradingEnabled(initiator)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("You have trading disabled! Use /toggle to enable it.", NamedTextColor.GRAY));
            initiator.sendMessage(message);
            return;
        }

        if (!isPlayerTradingEnabled(target)) {
            Component initiatorMessage = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text(target.getDisplayName() + " has trading disabled!", NamedTextColor.GRAY));
            Component targetMessage = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("You have trading disabled! Use /toggle to enable it.", NamedTextColor.GRAY));
            initiator.sendMessage(initiatorMessage);
            target.sendMessage(targetMessage);
            return;
        }

        if (isPlayerTrading(initiator) || isPlayerTrading(target)) {
            Component message = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("One of the players is already in a trade.", NamedTextColor.GRAY));
            initiator.sendMessage(message);
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
     * Check if a player has trading enabled via toggles
     */
    private boolean isPlayerTradingEnabled(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        try {
            // Use the Toggles system to check if trading is enabled
            return Toggles.isToggled(player, "Trading");
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking trading toggle for " + player.getName() + ": " + e.getMessage());
            // Default to true if there's an error checking the toggle
            return true;
        }
    }

    /**
     * Get a player's trading status for display/debugging
     */
    public String getPlayerTradingStatus(Player player) {
        if (player == null) {
            return "Unknown";
        }

        boolean tradingEnabled = isPlayerTradingEnabled(player);
        boolean currentlyTrading = isPlayerTrading(player);
        boolean hasPendingRequest = hasPendingTradeRequest(player);

        StringBuilder status = new StringBuilder();
        status.append("Trading: ").append(tradingEnabled ? "§aEnabled" : "§cDisabled");

        if (currentlyTrading) {
            status.append(" (§eCurrently Trading§r)");
        } else if (hasPendingRequest) {
            status.append(" (§yPending Request§r)");
        }

        return status.toString();
    }

    /**
     * Validate trade eligibility including toggle checks
     */
    public boolean canPlayersTradeTogether(Player player1, Player player2) {
        if (player1 == null || player2 == null) {
            return false;
        }

        if (!player1.isOnline() || !player2.isOnline()) {
            return false;
        }

        if (player1.equals(player2)) {
            return false;
        }

        // Check trading toggles
        if (!isPlayerTradingEnabled(player1) || !isPlayerTradingEnabled(player2)) {
            return false;
        }

        // Check if already trading
        if (isPlayerTrading(player1) || isPlayerTrading(player2)) {
            return false;
        }

        // Check distance (within same world and reasonable distance)
        if (!player1.getWorld().equals(player2.getWorld())) {
            return false;
        }

        double distance = player1.getLocation().distance(player2.getLocation());
        return distance <= 10.0; // Maximum trade distance of 10 blocks
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
    private void playSoundAndParticles(Player sender, Player recipient, org.bukkit.Sound sound, Particle particle, float pitch1, float pitch2) {
        sender.playSound(Sound.sound(sound, Sound.Source.PLAYER, 0.7f, pitch1));
        recipient.playSound(Sound.sound(sound, Sound.Source.PLAYER, 0.7f, pitch2));

        // Spawn particles around both players
        sender.spawnParticle(particle, sender.getLocation().add(0, 1.8, 0), 15, 0.5, 0.1, 0.5, 0.05);
        recipient.spawnParticle(particle, recipient.getLocation().add(0, 1.8, 0), 15, 0.5, 0.1, 0.5, 0.05);
    }
}