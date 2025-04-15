package com.rednetty.server.mechanics.economy;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Utility class for currency-related operations
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
     * Check if a player has enough gems across all forms (gems, bank notes, pouches)
     *
     * @param player The player to check
     * @param amount The amount of gems required
     * @return true if the player has enough gems
     */
    public static boolean hasEnoughGems(Player player, int amount) {
        if (amount <= 0) return true;

        int totalGems = getGems(player);
        return totalGems >= amount;
    }

    /**
     * Get the total amount of gems a player has (gems, bank notes, pouches)
     *
     * @param player The player
     * @return The total amount of gems
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
     * Take gems from a player's inventory
     *
     * @param player The player
     * @param amount The amount to take
     */
    public static void takeGems(Player player, int amount) {
        if (amount <= 0) return;

        if (hasEnoughGems(player, amount)) {
            int remaining = amount;

            // Loop through inventory and take gems as needed
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item == null) continue;

                if (remaining <= 0) break;

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
        }
    }

    /**
     * Get the number of elite shards a player has
     *
     * @param player The player
     * @return The number of elite shards
     */
    public static int getShards(Player player) {
        HashMap<Integer, ? extends ItemStack> itemData = player.getInventory().all(Material.PRISMARINE_SHARD);
        return itemData.size();
    }

    /**
     * Check if an item is a gem
     *
     * @param item The item to check
     * @return true if the item is a gem
     */
    public static boolean isGem(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().toLowerCase().contains("gem");
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
        return meta.hasDisplayName() && meta.getDisplayName().toLowerCase().contains("bank note");
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
}