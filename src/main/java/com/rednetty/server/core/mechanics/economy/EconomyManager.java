package com.rednetty.server.core.mechanics.economy;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages player economy with physical gem system - no virtual player balance
 * Players have only bank balance and physical gem items
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
        logger.info("Physical gem economy system enabled successfully");
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

        logger.info("Physical gem economy system has been disabled");
    }

    /**
     * Get the total amount of physical gems a player has in their inventory
     *
     * @param player The player
     * @return The total amount of physical gems
     */
    public int getPhysicalGems(Player player) {
        if (player == null) return 0;
        return MoneyManager.getGems(player);
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
     * Deposit gems to a player - gives physical gem items or bank note, or adds to bank if inventory full
     *
     * @param player The player
     * @param amount The amount of gems to give
     * @return Transaction result
     */
    public TransactionResult depositGems(Player player, int amount) {
        if (player == null) {
            return TransactionResult.failure("Player is null");
        }
        return depositGems(player.getUniqueId(), amount);
    }

    /**
     * Deposit gems to a player - gives physical gem items or bank note, or adds to bank if inventory full
     *
     * @param uuid   The player's UUID
     * @param amount The amount of gems to deposit
     * @return Transaction result
     */
    public TransactionResult depositGems(UUID uuid, int amount) {
        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            CompletableFuture<Boolean> future = YakPlayerManager.getInstance().withPlayer(uuid, player -> {
                Player bukkitPlayer = player.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {

                    // Try to give physical gem items first
                    if (hasInventorySpace(bukkitPlayer)) {
                        givePhysicalGems(bukkitPlayer, amount);
                        bukkitPlayer.sendMessage(ChatColor.GREEN + "Received " + amount + " physical gems!");
                    } else {
                        // Inventory full - check if we can give a bank note
                        if (bukkitPlayer.getInventory().firstEmpty() != -1) {
                            // Give bank note
                            ItemStack bankNote = BankManager.getInstance().createBankNote(amount);
                            bukkitPlayer.getInventory().addItem(bankNote);
                            bukkitPlayer.sendMessage(ChatColor.GREEN + "Received bank note for " + amount + " gems (inventory full)!");
                        } else {
                            // Completely full - add to bank balance
                            player.setBankGems(player.getBankGems() + amount);
                            bukkitPlayer.sendMessage(ChatColor.GREEN + "Added " + amount + " gems to your bank (inventory full)!");
                        }
                    }
                } else {
                    // Player offline - add to bank balance
                    player.setBankGems(player.getBankGems() + amount);
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS);

            if (success) {
                return TransactionResult.success("Deposited " + amount + " gems successfully", amount);
            } else {
                return TransactionResult.failure("Failed to deposit gems");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error depositing gems to player " + uuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Check if player has enough space in inventory for gem items
     */
    private boolean hasInventorySpace(Player player) {
        return player.getInventory().firstEmpty() != -1;
    }

    /**
     * Give physical gem items to a player
     */
    private void givePhysicalGems(Player player, int amount) {
        int remaining = amount;

        while (remaining > 0) {
            int stackAmount = Math.min(remaining, 64); // Max stack size for emeralds
            ItemStack gems = BankManager.getInstance().createGems(stackAmount);

            if (gems != null) {
                HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(gems);
                if (!notAdded.isEmpty()) {
                    // Drop remaining items at player's feet
                    for (ItemStack dropped : notAdded.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                    }
                    player.sendMessage(ChatColor.YELLOW + "Some gems were dropped at your feet (inventory full).");
                }
            }

            remaining -= stackAmount;
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
     * Add gems to a player's bank balance
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

            boolean success = future.get(10, TimeUnit.SECONDS);

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
     * Remove gems from a player's physical inventory
     *
     * @param player The player
     * @param amount The amount of gems to remove
     * @return Transaction result
     */
    public TransactionResult removePhysicalGems(Player player, int amount) {
        if (player == null) {
            return TransactionResult.failure("Player is null");
        }

        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            int availableGems = getPhysicalGems(player);

            if (availableGems < amount) {
                return TransactionResult.failure("Insufficient physical gems (has " + availableGems + ", needs " + amount + ")");
            }

            // Remove physical gems using MoneyManager
            MoneyManager.takeGems(player, amount);

            player.sendMessage(ChatColor.YELLOW + "Removed " + amount + " physical gems from inventory.");
            return TransactionResult.success("Removed " + amount + " physical gems successfully", amount);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error removing physical gems from player " + player.getName(), e);
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
     * Remove gems from a player's bank balance
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

            boolean success = future.get(10, TimeUnit.SECONDS);

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
     * Transfer physical gems from one player to another
     *
     * @param fromPlayer The sender
     * @param toPlayer   The recipient
     * @param amount     The amount to transfer
     * @return Transaction result
     */
    public TransactionResult transferPhysicalGems(Player fromPlayer, Player toPlayer, int amount) {
        if (fromPlayer == null || toPlayer == null) {
            return TransactionResult.failure("Player is null");
        }

        if (fromPlayer.getUniqueId().equals(toPlayer.getUniqueId())) {
            return TransactionResult.failure("Cannot transfer gems to yourself");
        }

        if (amount <= 0) {
            return TransactionResult.failure("Amount must be greater than zero");
        }

        try {
            // Check if sender has enough physical gems
            int availableGems = getPhysicalGems(fromPlayer);
            if (availableGems < amount) {
                return TransactionResult.failure("Insufficient physical gems to transfer");
            }

            // Remove from sender
            TransactionResult withdrawResult = removePhysicalGems(fromPlayer, amount);
            if (!withdrawResult.isSuccess()) {
                return withdrawResult;
            }

            // Give to recipient
            TransactionResult depositResult = depositGems(toPlayer, amount);
            if (!depositResult.isSuccess()) {
                // If deposit fails, refund the sender by giving them back the gems
                depositGems(fromPlayer, amount);
                return TransactionResult.failure("Transfer failed: " + depositResult.getMessage());
            }

            // Notify players about the transfer
            fromPlayer.sendMessage(ChatColor.YELLOW + "You transferred " + amount + " gems to " + toPlayer.getName());
            toPlayer.sendMessage(ChatColor.GREEN + "You received " + amount + " gems from " + fromPlayer.getName());

            return TransactionResult.success("Successfully transferred " + amount + " gems", amount);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error transferring gems from " + fromPlayer.getName() + " to " + toPlayer.getName(), e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Check if a player has enough physical gems in their inventory
     *
     * @param player The player
     * @param amount The amount to check
     * @return true if the player has enough physical gems
     */
    public boolean hasPhysicalGems(Player player, int amount) {
        if (player == null) return false;
        if (amount <= 0) return true;

        return getPhysicalGems(player) >= amount;
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
     * Transfer gems from a player's physical inventory to their bank
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
     * Transfer gems from a player's physical inventory to their bank
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
                Player bukkitPlayer = player.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    int physicalGems = getPhysicalGems(bukkitPlayer);

                    if (physicalGems >= amount) {
                        hasEnough.set(true);
                        // Remove physical gems and add to bank
                        MoneyManager.takeGems(bukkitPlayer, amount);
                        player.setBankGems(player.getBankGems() + amount);
                        operationSuccess.set(true);

                        bukkitPlayer.sendMessage(ChatColor.GREEN + "Deposited " + amount + " gems to bank. Bank balance: " +
                                player.getBankGems());
                    }
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS);

            if (success && operationSuccess.get()) {
                return TransactionResult.success("Deposited " + amount + " gems to bank successfully", amount);
            } else if (!hasEnough.get()) {
                return TransactionResult.failure("Insufficient physical gems to deposit");
            } else {
                return TransactionResult.failure("Failed to deposit gems to bank");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error depositing gems to bank for player " + uuid, e);
            return TransactionResult.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * Transfer gems from a player's bank to their physical inventory (as bank note)
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
     * Transfer gems from a player's bank to their physical inventory (as bank note)
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

                    // Give bank note to player if online
                    Player bukkitPlayer = player.getBukkitPlayer();
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        ItemStack bankNote = BankManager.getInstance().createBankNote(amount);

                        if (bukkitPlayer.getInventory().firstEmpty() != -1) {
                            bukkitPlayer.getInventory().addItem(bankNote);
                        } else {
                            bukkitPlayer.getWorld().dropItemNaturally(bukkitPlayer.getLocation(), bankNote);
                            bukkitPlayer.sendMessage(ChatColor.YELLOW + "Bank note dropped at your feet (inventory full).");
                        }

                        bukkitPlayer.sendMessage(ChatColor.GREEN + "Withdrew " + amount + " gems from bank as bank note. Bank balance: " +
                                player.getBankGems());
                        operationSuccess.set(true);
                    } else {
                        // Player offline - operation failed, revert
                        player.setBankGems(player.getBankGems() + amount);
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
     * Get the total gems a player has (physical + bank)
     *
     * @param player The player
     * @return Total gems
     */
    public int getTotalGems(Player player) {
        if (player == null) return 0;

        return getPhysicalGems(player) + getBankGems(player);
    }

    /**
     * Check if a player has enough total gems (physical + bank)
     *
     * @param player The player
     * @param amount The amount to check
     * @return true if the player has enough total gems
     */
    public boolean hasTotalGems(Player player, int amount) {
        if (player == null) return false;
        if (amount <= 0) return true;

        return getTotalGems(player) >= amount;
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