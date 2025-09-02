package com.rednetty.server.core.mechanics.economy;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Utility class for currency-related operations with physical gem economy
 */
public class MoneyManager {

    private static final GemPouchManager pouchManager = GemPouchManager.getInstance();
    private static final BankManager bankManager = BankManager.getInstance();

    /**
     * Private constructor to prevent instantiation
     */
    private MoneyManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Check if a player has enough physical gems across all forms (gems, bank notes, pouches)
     *
     * @param player The player to check
     * @param amount The amount of gems required
     * @return true if the player has enough physical gems
     */
    public static boolean hasEnoughGems(Player player, int amount) {
        if (amount <= 0) return true;

        int totalPhysicalGems = getGems(player);
        return totalPhysicalGems >= amount;
    }

    /**
     * Get the total amount of physical gems a player has (gems, bank notes, pouches)
     *
     * @param player The player
     * @return The total amount of physical gems
     */
    public static int getGems(Player player) {
        int gems = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            if (isGem(item)) {
                gems += item.getAmount();
            } else if (isBankNote(item)) {
                gems += getGemValue(item);
            } else if (pouchManager.isGemPouch(item)) {
                gems += pouchManager.getCurrentValue(item);
            }
        }

        return gems;
    }

    /**
     * Take physical gems from a player's inventory
     *
     * @param player The player
     * @param amount The amount to take
     * @return true if successfully taken, false if not enough gems
     */
    public static boolean takeGems(Player player, int amount) {
        if (amount <= 0) return true;

        if (!hasEnoughGems(player, amount)) {
            return false;
        }

        int remaining = amount;

        // Loop through inventory and take gems as needed
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;

            if (isGem(item)) {
                if (remaining >= item.getAmount()) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            } else if (isBankNote(item)) {
                int noteValue = getGemValue(item);

                if (remaining >= noteValue) {
                    remaining -= noteValue;
                    player.getInventory().setItem(i, null);
                } else {
                    // Create a new note with the remaining value
                    ItemStack newNote = bankManager.createBankNote(noteValue - remaining);
                    player.getInventory().setItem(i, newNote);
                    remaining = 0;
                }
            } else if (pouchManager.isGemPouch(item)) {
                int pouchValue = pouchManager.getCurrentValue(item);

                if (remaining >= pouchValue) {
                    remaining -= pouchValue;
                    pouchManager.setPouchValue(item, 0);
                } else {
                    pouchManager.setPouchValue(item, pouchValue - remaining);
                    remaining = 0;
                }
            }
        }

        // Update player's inventory
        player.updateInventory();
        return remaining == 0;
    }

    /**
     * Give physical gems to a player
     *
     * @param player The player
     * @param amount The amount to give
     * @return true if successfully given, false if inventory is full
     */
    public static boolean giveGems(Player player, int amount) {
        if (amount <= 0) return true;

        int remaining = amount;

        while (remaining > 0) {
            int stackAmount = Math.min(remaining, 64); // Max stack size for emeralds
            ItemStack gems = makeGems(stackAmount);

            if (gems != null) {
                HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(gems);
                if (!notAdded.isEmpty()) {
                    // Inventory full - drop at player's feet
                    for (ItemStack dropped : notAdded.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                    }
                    return false; // Indicate that some items were dropped
                }
            }

            remaining -= stackAmount;
        }

        player.updateInventory();
        return true;
    }

    /**
     * Get the number of elite shards a player has
     *
     * @param player The player
     * @return The number of elite shards
     */
    public static int getShards(Player player) {
        int shardCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.PRISMARINE_SHARD) {
                shardCount += item.getAmount();
            }
        }
        return shardCount;
    }

    /**
     * Check if an item is a valid gem
     *
     * @param item The item to check
     * @return true if the item is a gem
     */
    public static boolean isGem(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() &&
                meta.getDisplayName().equals(ChatColor.WHITE + "Gem") &&
                meta.hasLore() &&
                !meta.getLore().isEmpty() &&
                meta.getLore().get(0).contains("The currency of Andalucia");
    }

    /**
     * Check if an item is a bank note
     *
     * @param item The item to check
     * @return true if the item is a bank note
     */
    public static boolean isBankNote(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() &&
                meta.getDisplayName().equals(ChatColor.GREEN + "Bank Note") &&
                meta.hasLore() &&
                !meta.getLore().isEmpty() &&
                meta.getLore().get(0).contains("Value");
    }

    /**
     * Create a bank note with a specific value
     *
     * @param amount The amount of gems
     * @return The bank note ItemStack
     */
    public static ItemStack createBankNote(int amount) {
        return bankManager.createBankNote(amount);
    }

    /**
     * Create a gem ItemStack with a specific amount
     *
     * @param amount The amount of gems
     * @return The gem ItemStack
     */
    public static ItemStack makeGems(int amount) {
        if (amount <= 0) return null;

        ItemStack gem = new ItemStack(Material.EMERALD, Math.min(amount, 64));
        ItemMeta meta = gem.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Gem");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "The currency of Andalucia"));
        gem.setItemMeta(meta);

        return gem;
    }

    /**
     * Get the gem value of a bank note
     *
     * @param item The bank note
     * @return The gem value
     */
    public static int getGemValue(ItemStack item) {
        if (!isBankNote(item)) return 0;

        try {
            List<String> lore = item.getItemMeta().getLore();
            String line = ChatColor.stripColor(lore.get(0));
            return Integer.parseInt(line.split(": ")[1].split(" Gems")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Check if a player has any form of currency (gems, bank notes, pouches)
     *
     * @param player The player
     * @return true if player has any currency
     */
    public static boolean hasCurrency(Player player) {
        return getGems(player) > 0;
    }

    /**
     * Get detailed breakdown of a player's physical currency
     *
     * @param player The player
     * @return String breakdown of currency types
     */
    public static String getCurrencyBreakdown(Player player) {
        int physicalGems = 0;
        int bankNoteValue = 0;
        int pouchValue = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            if (isGem(item)) {
                physicalGems += item.getAmount();
            } else if (isBankNote(item)) {
                bankNoteValue += getGemValue(item);
            } else if (pouchManager.isGemPouch(item)) {
                pouchValue += pouchManager.getCurrentValue(item);
            }
        }

        StringBuilder breakdown = new StringBuilder();
        breakdown.append("Physical Gems: ").append(physicalGems);
        if (bankNoteValue > 0) {
            breakdown.append(", Bank Notes: ").append(bankNoteValue);
        }
        if (pouchValue > 0) {
            breakdown.append(", Pouches: ").append(pouchValue);
        }

        int total = physicalGems + bankNoteValue + pouchValue;
        breakdown.append(" (Total: ").append(total).append(")");

        return breakdown.toString();
    }

    /**
     * Convert all physical currency to optimal form (minimize inventory slots)
     *
     * @param player The player
     * @return true if conversion was successful
     */
    public static boolean optimizeCurrency(Player player) {
        int totalGems = getGems(player);
        if (totalGems <= 0) return true;

        // Remove all current currency
        if (!takeGems(player, totalGems)) {
            return false;
        }

        // Give back in optimal form
        int remaining = totalGems;

        // Create bank notes for large amounts
        while (remaining >= 1000) {
            int noteValue = Math.min(remaining, 10000); // Max bank note value
            ItemStack bankNote = createBankNote(noteValue);

            HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(bankNote);
            if (!notAdded.isEmpty()) {
                // Inventory full, give remaining as physical gems
                return giveGems(player, remaining);
            }
            remaining -= noteValue;
        }

        // Give remaining as physical gems
        if (remaining > 0) {
            return giveGems(player, remaining);
        }

        return true;
    }

    /**
     * Transfer physical currency from one player to another
     *
     * @param from The player giving currency
     * @param to The player receiving currency
     * @param amount The amount to transfer
     * @return true if transfer was successful
     */
    public static boolean transferCurrency(Player from, Player to, int amount) {
        if (amount <= 0) return false;
        if (!hasEnoughGems(from, amount)) return false;

        // Take from sender
        if (!takeGems(from, amount)) {
            return false;
        }

        // Give to receiver
        if (!giveGems(to, amount)) {
            // If giving failed, refund sender
            giveGems(from, amount);
            return false;
        }

        return true;
    }
}