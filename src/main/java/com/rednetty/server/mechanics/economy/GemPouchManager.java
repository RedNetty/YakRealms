package com.rednetty.server.mechanics.economy;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages gem pouches for storing gems
 */
public class GemPouchManager implements Listener {
    private static GemPouchManager instance;
    private final Logger logger;

    // Singleton pattern
    private GemPouchManager() {
        this.logger = YakRealms.getInstance().getLogger();
    }

    public static GemPouchManager getInstance() {
        if (instance == null) {
            instance = new GemPouchManager();
        }
        return instance;
    }

    /**
     * Initialize the gem pouch system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        logger.info("Gem Pouch system has been enabled");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        logger.info("Gem Pouch system has been disabled");
    }

    /**
     * Create a gem pouch item of a specific tier
     *
     * @param tier The tier of the pouch (1-6)
     * @param shop Whether this is for a shop display
     * @return The gem pouch ItemStack
     */
    public ItemStack createGemPouch(int tier, boolean shop) {
        String name;
        String lore;

        // Set name and lore based on tier
        switch (tier) {
            case 1:
                name = ChatColor.WHITE + "Small Gem Pouch" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A small linen pouch that holds " + ChatColor.BOLD + "200g";
                break;
            case 2:
                name = ChatColor.GREEN + "Medium Gem Sack" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A medium wool sack that holds " + ChatColor.BOLD + "350g";
                break;
            case 3:
                name = ChatColor.AQUA + "Large Gem Satchel" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A large leather satchel that holds " + ChatColor.BOLD + "500g";
                break;
            case 4:
                name = ChatColor.LIGHT_PURPLE + "Gigantic Gem Container" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A giant container that holds " + ChatColor.BOLD + "3000g";
                break;
            case 5:
                name = ChatColor.YELLOW + "Legendary Gem Container" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A giant container that holds " + ChatColor.BOLD + "8000g";
                break;
            case 6:
                name = ChatColor.RED + "Insane Gem Container" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A giant container that holds " + ChatColor.BOLD + "100000g";
                break;
            default:
                name = ChatColor.WHITE + "Gem Pouch" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A pouch that holds gems";
                break;
        }

        int shopPrice = tier * 3 * 750;
        String shopLore = shop ? (ChatColor.GREEN + "Price: " + ChatColor.WHITE + shopPrice + "g") : "";

        // Create the item
        ItemStack pouch = new ItemStack(Material.INK_SAC);
        ItemMeta meta = pouch.getItemMeta();
        meta.setDisplayName(name);

        if (name.contains("Insane")) {
            meta.setLore(Arrays.asList(lore, "", ChatColor.RED + "Soulbound", shopLore));
        } else {
            meta.setLore(Arrays.asList(lore, shopLore));
        }

        pouch.setItemMeta(meta);
        return pouch;
    }

    /**
     * Handle item drops to prevent dropping soulbound pouches
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack itemStack = event.getItemDrop().getItemStack();

        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() &&
                itemStack.getItemMeta().hasLore()) {

            Player player = event.getPlayer();
            boolean soulbound = false;

            // Check if item is soulbound
            for (String line : itemStack.getItemMeta().getLore()) {
                if (line.toLowerCase().contains("soulbound")) {
                    soulbound = true;
                    break;
                }
            }

            String itemName = itemStack.getItemMeta().getDisplayName();

            if (soulbound) {
                player.sendMessage(ChatColor.RED + "You've dropped your " + ChatColor.UNDERLINE + itemName);
            }

            // Prevent dropping saddles
            if (itemStack.getType() == Material.SADDLE) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handle inventory clicks on gem pouches
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // Check if clicking in crafting inventory
        if (event.getView().getTitle().equals("Crafting")) {
            ItemStack currentItem = event.getCurrentItem();
            ItemStack cursor = event.getCursor();

            // Adding gems to pouch (left click with gems on cursor)
            if (event.isLeftClick() &&
                    isGemPouch(currentItem) &&
                    cursor != null && cursor.getType() == Material.EMERALD) {

                // Only handle single pouches, not stacked
                if (currentItem.getAmount() != 1) return;

                event.setCancelled(true);
                int currentValue = getCurrentValue(currentItem);
                int maxValue = getMaxValue(currentItem);
                int gemsToAdd = cursor.getAmount();

                if (currentValue < maxValue) {
                    if (currentValue + gemsToAdd > maxValue) {
                        // Partial addition - fill pouch and return excess
                        cursor.setAmount(gemsToAdd - (maxValue - currentValue));
                        setPouchValue(currentItem, maxValue);
                    } else {
                        // Full addition - add all gems to pouch
                        event.setCursor(null);
                        setPouchValue(currentItem, currentValue + gemsToAdd);
                    }

                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }
            // Taking gems from pouch (right click with empty hand)
            else if (event.isRightClick() &&
                    isGemPouch(currentItem) &&
                    (cursor == null || cursor.getType() == Material.AIR)) {

                if (currentItem.getAmount() != 1) return;

                event.setCancelled(true);
                int currentValue = getCurrentValue(currentItem);

                if (currentValue <= 0) return;

                if (currentValue > 64) {
                    // Withdraw stack at a time
                    event.setCursor(createGems(64));
                    setPouchValue(currentItem, currentValue - 64);
                } else {
                    // Withdraw all remaining gems
                    event.setCursor(createGems(currentValue));
                    setPouchValue(currentItem, 0);
                }

                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Handle gem pickups
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        ItemStack itemStack = event.getItem().getItemStack();

        // Add item flags to all items for consistency
        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }
            itemStack.setItemMeta(meta);
        }

        // Only process emeralds
        if (itemStack.getType() != Material.EMERALD) return;

        int gemAmount = itemStack.getAmount();

        // Auto-deposit to bank if toggle is enabled
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer != null && yakPlayer.isToggled("Gems")) {
            EconomyManager.getInstance().depositToBank(player.getUniqueId(), gemAmount);

            if (yakPlayer.isToggled("Debug")) {
                TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() + ChatColor.BOLD.toString() +
                        "+" + ChatColor.GREEN + gemAmount + ChatColor.GREEN + ChatColor.BOLD + "G");
            }

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }

        // Try to add gems to pouches
        boolean fullyProcessed = tryAddToGemPouch(player, itemStack);

        if (fullyProcessed) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    /**
     * Try to add gems to a player's gem pouches
     *
     * @param player   The player
     * @param gemStack The gem ItemStack
     * @return true if all gems were processed
     */
    public boolean tryAddToGemPouch(Player player, ItemStack gemStack) {
        if (gemStack == null || gemStack.getType() != Material.EMERALD) return false;

        int gemsToAdd = gemStack.getAmount();
        if (gemsToAdd <= 0) return true;

        // Check all pouches in inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isGemPouch(item) || item.getAmount() != 1) continue;

            int currentValue = getCurrentValue(item);
            int maxValue = getMaxValue(item);

            if (currentValue < maxValue) {
                int spaceLeft = maxValue - currentValue;

                if (gemsToAdd <= spaceLeft) {
                    // Add all remaining gems
                    setPouchValue(item, currentValue + gemsToAdd);
                    TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() + ChatColor.BOLD +
                            "+" + ChatColor.GREEN + gemsToAdd + ChatColor.GREEN + ChatColor.BOLD + "G");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    return true;
                } else {
                    // Fill this pouch and continue with remainder
                    setPouchValue(item, maxValue);
                    TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() + ChatColor.BOLD +
                            "+" + ChatColor.GREEN + spaceLeft + ChatColor.GREEN + ChatColor.BOLD + "G");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    gemsToAdd -= spaceLeft;
                }
            }
        }

        // Update the stack with remaining gems
        if (gemsToAdd < gemStack.getAmount()) {
            gemStack.setAmount(gemsToAdd);
            return gemsToAdd == 0;
        }

        return false;
    }

    /**
     * Process gem pickup without event (for custom drops)
     *
     * @param player   The player picking up gems
     * @param gemStack The gem ItemStack
     */
    public void processGemPickup(Player player, ItemStack gemStack) {
        if (gemStack == null || gemStack.getType() != Material.EMERALD) return;

        int gemAmount = gemStack.getAmount();
        if (gemAmount <= 0) return;

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);

        // Auto-deposit to bank if toggle is enabled
        if (yakPlayer != null && yakPlayer.isToggled("Gems")) {
            EconomyManager.getInstance().depositToBank(player.getUniqueId(), gemAmount);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() + ChatColor.BOLD +
                    "+" + ChatColor.GREEN + gemAmount + ChatColor.GREEN + ChatColor.BOLD + "G");
            return;
        }

        // Try to add to pouches
        boolean fullyProcessed = tryAddToGemPouch(player, gemStack);

        // Add remaining gems to inventory
        if (!fullyProcessed && gemStack.getAmount() > 0) {
            player.getInventory().addItem(gemStack.clone());
            TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() + ChatColor.BOLD +
                    "+" + ChatColor.GREEN + gemStack.getAmount() + ChatColor.GREEN + ChatColor.BOLD + "G");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    /**
     * Get the maximum capacity of a gem pouch
     *
     * @param pouch The gem pouch ItemStack
     * @return The maximum capacity
     */
    public int getMaxValue(ItemStack pouch) {
        if (pouch == null || pouch.getType() != Material.INK_SAC || !pouch.hasItemMeta()) return 0;

        ItemMeta meta = pouch.getItemMeta();
        if (!meta.hasLore() || meta.getLore().isEmpty()) return 0;

        try {
            String line = ChatColor.stripColor(meta.getLore().get(0));
            if (!line.contains("holds")) return 0;

            return Integer.parseInt(line.substring(line.lastIndexOf(" ") + 1, line.lastIndexOf("g")));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting max value from gem pouch", e);
            return 0;
        }
    }

    /**
     * Get the current amount of gems in a pouch
     *
     * @param pouch The gem pouch ItemStack
     * @return The current amount of gems
     */
    public int getCurrentValue(ItemStack pouch) {
        if (!isGemPouch(pouch)) return 0;

        try {
            String displayName = pouch.getItemMeta().getDisplayName();
            String valueStr = ChatColor.stripColor(displayName);
            return Integer.parseInt(valueStr.substring(valueStr.lastIndexOf(" ") + 1, valueStr.lastIndexOf("g")));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting current value from gem pouch", e);
            return 0;
        }
    }

    /**
     * Set the amount of gems in a pouch
     *
     * @param pouch  The gem pouch ItemStack
     * @param amount The new amount of gems
     */
    public void setPouchValue(ItemStack pouch, int amount) {
        if (!isGemPouch(pouch)) return;

        try {
            String displayName = pouch.getItemMeta().getDisplayName();
            String newName = displayName.substring(0, displayName.lastIndexOf(" ")) + " " + amount + "g";

            ItemMeta meta = pouch.getItemMeta();
            meta.setDisplayName(newName);
            pouch.setItemMeta(meta);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error setting value on gem pouch", e);
        }
    }

    /**
     * Check if an ItemStack is a gem pouch
     *
     * @param item The ItemStack to check
     * @return true if the item is a gem pouch
     */
    public boolean isGemPouch(ItemStack item) {
        if (item == null || item.getType() != Material.INK_SAC) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;

        String displayName = meta.getDisplayName();
        if (!displayName.contains("g")) return false;

        // Check if it has the characteristic lore of a gem pouch
        if (!meta.hasLore() || meta.getLore().isEmpty()) return false;
        String loreLine = ChatColor.stripColor(meta.getLore().get(0));
        return loreLine.contains("holds ") && loreLine.contains("g");
    }

    /**
     * Create a gem ItemStack with specified amount
     *
     * @param amount The amount of gems
     * @return The gem ItemStack
     */
    public ItemStack createGems(int amount) {
        if (amount <= 0) return null;

        ItemStack gems = new ItemStack(Material.EMERALD, Math.min(amount, 64));
        ItemMeta meta = gems.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Gem");
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "The currency of Andalucia"));
        gems.setItemMeta(meta);

        return gems;
    }
}