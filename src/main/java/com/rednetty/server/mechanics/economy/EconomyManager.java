package com.rednetty.server.mechanics.economy;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages player economy, gems, and currency transactions
 */
public class EconomyManager implements Listener {
    private static EconomyManager instance;
    private final Logger logger;

    /**
     * Private constructor for singleton pattern
     */
    private EconomyManager() {
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The EconomyManager instance
     */
    public static EconomyManager getInstance() {
        if (instance == null) {
            instance = new EconomyManager();
        }
        return instance;
    }

    /**
     * Initialize the economy system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        logger.info("Economy system has been enabled");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        // Save all player balances to ensure no data loss
        for (Player player : Bukkit.getOnlinePlayers()) {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer != null) {
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            }
        }

        logger.info("Economy system has been disabled");
    }

    /**
     * Get a player's gem balance
     *
     * @param player The player
     * @return The player's gem balance
     */
    public int getGems(Player player) {
        if (player == null) return 0;
        return getGems(player.getUniqueId());
    }

    /**
     * Get a player's gem balance
     *
     * @param uuid The player's UUID
     * @return The player's gem balance
     */
    public int getGems(UUID uuid) {
        YakPlayer player = YakPlayerManager.getInstance().getPlayer(uuid);
        return player != null ? player.getGems() : 0;
    }

    /**
     * Get a player's bank gem balance
     *
     * @param player The player
     * @return The player's bank gem balance
     */
    public int getBankGems(Player player) {
        if (player == null) return 0;
        return getBankGems(player.getUniqueId());
    }

    /**
     * Get a player's bank gem balance
     *
     * @param uuid The player's UUID
     * @return The player's bank gem balance
     */
    public int getBankGems(UUID uuid) {
        YakPlayer player = YakPlayerManager.getInstance().getPlayer(uuid);
        return player != null ? player.getBankGems() : 0;
    }

    /**
     * Add gems to a player's inventory balance
     *
     * @param player The player
     * @param amount The amount of gems to add
     * @return Transaction result
     */
    public TransactionResult addGems(Player player, int amount) {
        if (player == null) {
            return TransactionResult.failure("Player is null");
        }
        return addGems(player.getUniqueId(), amount);
    }

    /**
     * Add gems to a player's inventory balance with improved synchronization
     *
     * @param uuid   The player's UUID
     * @param amount The amount of gems to add
     * @return Transaction result
     */
    public TransactionResult addGems(UUID uuid, int amount) {
        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            CompletableFuture<Boolean> future = YakPlayerManager.getInstance().withPlayer(uuid, player -> {
                int currentBalance = player.getGems();
                player.setGems(currentBalance + amount);

                // Notify player if online
                Player bukkitPlayer = player.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    bukkitPlayer.sendMessage(ChatColor.GREEN + "Added " + amount + " gems. New balance: " + player.getGems());
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS); // Wait for completion with timeout

            if (success) {
                return TransactionResult.success("Added " + amount + " gems successfully", amount);
            } else {
                return TransactionResult.failure("Failed to add gems");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error adding gems to player " + uuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Add gems to a player's bank balance
     *
     * @param player The player
     * @param amount The amount of gems to add
     * @return Transaction result
     */
    public TransactionResult addBankGems(Player player, int amount) {
        if (player == null) {
            return TransactionResult.failure("Player is null");
        }
        return addBankGems(player.getUniqueId(), amount);
    }

    /**
     * Add gems to a player's bank balance with improved synchronization
     *
     * @param uuid   The player's UUID
     * @param amount The amount of gems to add
     * @return Transaction result
     */
    public TransactionResult addBankGems(UUID uuid, int amount) {
        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            CompletableFuture<Boolean> future = YakPlayerManager.getInstance().withPlayer(uuid, player -> {
                int currentBalance = player.getBankGems();
                player.setBankGems(currentBalance + amount);

                // Notify player if online
                Player bukkitPlayer = player.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    bukkitPlayer.sendMessage(ChatColor.GREEN + "Added " + amount + " gems to bank. New balance: " + player.getBankGems());
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS); // Wait for completion with timeout

            if (success) {
                return TransactionResult.success("Added " + amount + " gems to bank successfully", amount);
            } else {
                return TransactionResult.failure("Failed to add gems to bank");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error adding gems to player bank " + uuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Remove gems from a player's inventory balance
     *
     * @param player The player
     * @param amount The amount of gems to remove
     * @return Transaction result
     */
    public TransactionResult removeGems(Player player, int amount) {
        if (player == null) {
            return TransactionResult.failure("Player is null");
        }
        return removeGems(player.getUniqueId(), amount);
    }

    /**
     * Remove gems from a player's inventory balance with improved verification
     *
     * @param uuid   The player's UUID
     * @param amount The amount of gems to remove
     * @return Transaction result
     */
    public TransactionResult removeGems(UUID uuid, int amount) {
        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            AtomicBoolean hasEnough = new AtomicBoolean(false);
            CompletableFuture<Boolean> future = YakPlayerManager.getInstance().withPlayer(uuid, player -> {
                if (player.getGems() >= amount) {
                    hasEnough.set(true);
                    player.setGems(player.getGems() - amount);

                    // Notify player if online
                    Player bukkitPlayer = player.getBukkitPlayer();
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        bukkitPlayer.sendMessage(ChatColor.YELLOW + "Removed " + amount + " gems. New balance: " + player.getGems());
                    }
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS); // Wait for completion with timeout

            if (success && hasEnough.get()) {
                return TransactionResult.success("Removed " + amount + " gems successfully", amount);
            } else if (!hasEnough.get()) {
                return TransactionResult.failure("Insufficient gems");
            } else {
                return TransactionResult.failure("Failed to remove gems");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error removing gems from player " + uuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Remove gems from a player's bank balance
     *
     * @param player The player
     * @param amount The amount of gems to remove
     * @return Transaction result
     */
    public TransactionResult removeBankGems(Player player, int amount) {
        if (player == null) {
            return TransactionResult.failure("Player is null");
        }
        return removeBankGems(player.getUniqueId(), amount);
    }

    /**
     * Remove gems from a player's bank balance with improved verification
     *
     * @param uuid   The player's UUID
     * @param amount The amount of gems to remove
     * @return Transaction result
     */
    public TransactionResult removeBankGems(UUID uuid, int amount) {
        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            AtomicBoolean hasEnough = new AtomicBoolean(false);
            CompletableFuture<Boolean> future = YakPlayerManager.getInstance().withPlayer(uuid, player -> {
                if (player.getBankGems() >= amount) {
                    hasEnough.set(true);
                    player.setBankGems(player.getBankGems() - amount);

                    // Notify player if online
                    Player bukkitPlayer = player.getBukkitPlayer();
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        bukkitPlayer.sendMessage(ChatColor.YELLOW + "Removed " + amount + " gems from bank. New balance: " + player.getBankGems());
                    }
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS); // Wait for completion with timeout

            if (success && hasEnough.get()) {
                return TransactionResult.success("Removed " + amount + " gems from bank successfully", amount);
            } else if (!hasEnough.get()) {
                return TransactionResult.failure("Insufficient gems in bank");
            } else {
                return TransactionResult.failure("Failed to remove gems from bank");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error removing gems from player bank " + uuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Transfer gems from one player to another
     *
     * @param fromPlayer The sender
     * @param toPlayer   The recipient
     * @param amount     The amount to transfer
     * @return Transaction result
     */
    public TransactionResult transferGems(Player fromPlayer, Player toPlayer, int amount) {
        if (fromPlayer == null || toPlayer == null) {
            return TransactionResult.failure("Player is null");
        }
        return transferGems(fromPlayer.getUniqueId(), toPlayer.getUniqueId(), amount);
    }

    /**
     * Transfer gems from one player to another with improved transaction handling
     *
     * @param fromUuid The sender's UUID
     * @param toUuid   The recipient's UUID
     * @param amount   The amount to transfer
     * @return Transaction result
     */
    public TransactionResult transferGems(UUID fromUuid, UUID toUuid, int amount) {
        if (fromUuid.equals(toUuid)) {
            return TransactionResult.failure("Cannot transfer gems to yourself");
        }

        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            // First check if sender has enough gems
            AtomicBoolean hasEnough = new AtomicBoolean(false);
            CompletableFuture<Boolean> checkFuture = YakPlayerManager.getInstance().withPlayer(fromUuid, player -> {
                if (player.getGems() >= amount) {
                    hasEnough.set(true);
                }
            }, false);

            boolean checkSuccess = checkFuture.get(10, TimeUnit.SECONDS);

            if (!checkSuccess || !hasEnough.get()) {
                return TransactionResult.failure("Insufficient gems to transfer");
            }

            // Use transactional approach to ensure atomicity
            TransactionResult withdrawResult = removeGems(fromUuid, amount);
            if (!withdrawResult.isSuccess()) {
                return withdrawResult;
            }

            TransactionResult depositResult = addGems(toUuid, amount);
            if (!depositResult.isSuccess()) {
                // If deposit fails, refund the sender
                addGems(fromUuid, amount);
                return TransactionResult.failure("Transfer failed: " + depositResult.getMessage());
            }

            // Notify players about the transfer
            notifyPlayers(fromUuid, toUuid, amount);

            return TransactionResult.success("Successfully transferred " + amount + " gems", amount);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error transferring gems from " + fromUuid + " to " + toUuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Notify players about a transfer
     */
    private void notifyPlayers(UUID fromUuid, UUID toUuid, int amount) {
        YakPlayerManager playerManager = YakPlayerManager.getInstance();
        YakPlayer sender = playerManager.getPlayer(fromUuid);
        YakPlayer recipient = playerManager.getPlayer(toUuid);

        if (sender != null && sender.isOnline()) {
            Player fromPlayer = sender.getBukkitPlayer();
            String toName = recipient != null ? recipient.getUsername() : "another player";
            fromPlayer.sendMessage(ChatColor.YELLOW + "You transferred " + amount + " gems to " + toName);
        }

        if (recipient != null && recipient.isOnline()) {
            Player toPlayer = recipient.getBukkitPlayer();
            String fromName = sender != null ? sender.getUsername() : "another player";
            toPlayer.sendMessage(ChatColor.GREEN + "You received " + amount + " gems from " + fromName);
        }
    }

    /**
     * Check if a player has enough gems in their inventory
     *
     * @param player The player
     * @param amount The amount to check
     * @return true if the player has enough gems
     */
    public boolean hasGems(Player player, int amount) {
        if (player == null) return false;
        return hasGems(player.getUniqueId(), amount);
    }

    /**
     * Check if a player has enough gems in their inventory
     *
     * @param uuid   The player's UUID
     * @param amount The amount to check
     * @return true if the player has enough gems
     */
    public boolean hasGems(UUID uuid, int amount) {
        if (amount <= 0) return true;

        YakPlayer player = YakPlayerManager.getInstance().getPlayer(uuid);
        return player != null && player.getGems() >= amount;
    }

    /**
     * Check if a player has enough gems in their bank
     *
     * @param player The player
     * @param amount The amount to check
     * @return true if the player has enough gems
     */
    public boolean hasBankGems(Player player, int amount) {
        if (player == null) return false;
        return hasBankGems(player.getUniqueId(), amount);
    }

    /**
     * Check if a player has enough gems in their bank
     *
     * @param uuid   The player's UUID
     * @param amount The amount to check
     * @return true if the player has enough gems
     */
    public boolean hasBankGems(UUID uuid, int amount) {
        if (amount <= 0) return true;

        YakPlayer player = YakPlayerManager.getInstance().getPlayer(uuid);
        return player != null && player.getBankGems() >= amount;
    }

    /**
     * Transfer gems from a player's inventory to their bank
     *
     * @param player The player
     * @param amount The amount to transfer
     * @return Transaction result
     */
    public TransactionResult depositToBank(Player player, int amount) {
        if (player == null) {
            return TransactionResult.failure("Player is null");
        }
        return depositToBank(player.getUniqueId(), amount);
    }

    /**
     * Transfer gems from a player's inventory to their bank with improved transaction handling
     *
     * @param uuid   The player's UUID
     * @param amount The amount to transfer
     * @return Transaction result
     */
    public TransactionResult depositToBank(UUID uuid, int amount) {
        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            AtomicBoolean hasEnough = new AtomicBoolean(false);
            AtomicBoolean operationSuccess = new AtomicBoolean(false);

            CompletableFuture<Boolean> future = YakPlayerManager.getInstance().withPlayer(uuid, player -> {
                if (player.getGems() >= amount) {
                    hasEnough.set(true);
                    player.setGems(player.getGems() - amount);
                    player.setBankGems(player.getBankGems() + amount);
                    operationSuccess.set(true);

                    // Notify player if online
                    Player bukkitPlayer = player.getBukkitPlayer();
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        bukkitPlayer.sendMessage(ChatColor.GREEN + "Deposited " + amount + " gems to bank. Bank balance: " +
                                player.getBankGems() + ", Inventory balance: " + player.getGems());
                    }
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS);

            if (success && operationSuccess.get()) {
                return TransactionResult.success("Deposited " + amount + " gems to bank successfully", amount);
            } else if (!hasEnough.get()) {
                return TransactionResult.failure("Insufficient gems to deposit");
            } else {
                return TransactionResult.failure("Failed to deposit gems to bank");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error depositing gems to bank for player " + uuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Transfer gems from a player's bank to their inventory
     *
     * @param player The player
     * @param amount The amount to transfer
     * @return Transaction result
     */
    public TransactionResult withdrawFromBank(Player player, int amount) {
        if (player == null) {
            return TransactionResult.failure("Player is null");
        }
        return withdrawFromBank(player.getUniqueId(), amount);
    }

    /**
     * Transfer gems from a player's bank to their inventory with improved transaction handling
     *
     * @param uuid   The player's UUID
     * @param amount The amount to transfer
     * @return Transaction result
     */
    public TransactionResult withdrawFromBank(UUID uuid, int amount) {
        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            AtomicBoolean hasEnough = new AtomicBoolean(false);
            AtomicBoolean operationSuccess = new AtomicBoolean(false);

            CompletableFuture<Boolean> future = YakPlayerManager.getInstance().withPlayer(uuid, player -> {
                if (player.getBankGems() >= amount) {
                    hasEnough.set(true);
                    player.setBankGems(player.getBankGems() - amount);
                    player.setGems(player.getGems() + amount);
                    operationSuccess.set(true);

                    // Notify player if online
                    Player bukkitPlayer = player.getBukkitPlayer();
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        bukkitPlayer.sendMessage(ChatColor.GREEN + "Withdrew " + amount + " gems from bank. Bank balance: " +
                                player.getBankGems() + ", Inventory balance: " + player.getGems());
                    }
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS);

            if (success && operationSuccess.get()) {
                return TransactionResult.success("Withdrew " + amount + " gems from bank successfully", amount);
            } else if (!hasEnough.get()) {
                return TransactionResult.failure("Insufficient gems in bank to withdraw");
            } else {
                return TransactionResult.failure("Failed to withdraw gems from bank");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error withdrawing gems from bank for player " + uuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Initialize a player's balance on join if needed
     */
    @EventHandler
    public void onLogin(PlayerJoinEvent event) {
        // Balance is now managed through YakPlayer, so no special handling needed
        // YakPlayerManager already handles loading player data on join
    }
}