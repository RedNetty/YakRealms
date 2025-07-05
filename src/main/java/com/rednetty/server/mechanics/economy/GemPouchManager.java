package com.rednetty.server.mechanics.economy;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.nbt.NBTAccessor;
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages gem pouches for storing gems - NBT-based storage
 */
public class GemPouchManager implements Listener {
    private static GemPouchManager instance;
    private final Logger logger;

    // NBT Keys
    private static final String NBT_MAX_CAPACITY = "gem_pouch_max";
    private static final String NBT_CURRENT_GEMS = "gem_pouch_gems";
    private static final String NBT_IS_GEM_POUCH = "is_gem_pouch";

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
        logger.info("Gem Pouch system has been enabled and events registered");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        logger.info("Gem Pouch system has been disabled");
    }

    /**
     * Create a gem pouch item of a specific tier
     */
    public static ItemStack createGemPouch(int tier, boolean shop) {
        String name;
        String lore;
        int maxCapacity;

        switch (tier) {
            case 1:
                name = ChatColor.WHITE + "Small Gem Pouch" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A small linen pouch that holds " + ChatColor.BOLD + "200g";
                maxCapacity = 200;
                break;
            case 2:
                name = ChatColor.GREEN + "Medium Gem Sack" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A medium wool sack that holds " + ChatColor.BOLD + "350g";
                maxCapacity = 350;
                break;
            case 3:
                name = ChatColor.AQUA + "Large Gem Satchel" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A large leather satchel that holds " + ChatColor.BOLD + "500g";
                maxCapacity = 500;
                break;
            case 4:
                name = ChatColor.LIGHT_PURPLE + "Gigantic Gem Container" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A giant container that holds " + ChatColor.BOLD + "3000g";
                maxCapacity = 3000;
                break;
            case 5:
                name = ChatColor.YELLOW + "Legendary Gem Container" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A giant container that holds " + ChatColor.BOLD + "8000g";
                maxCapacity = 8000;
                break;
            case 6:
                name = ChatColor.RED + "Insane Gem Container" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A giant container that holds " + ChatColor.BOLD + "100000g";
                maxCapacity = 100000;
                break;
            default:
                name = ChatColor.WHITE + "Gem Pouch" + ChatColor.GREEN + ChatColor.BOLD + " 0g";
                lore = ChatColor.GRAY + "A pouch that holds gems";
                maxCapacity = 100;
                break;
        }

        int shopPrice = tier * 3 * 750;
        String shopLore = shop ? (ChatColor.GREEN + "Price: " + ChatColor.WHITE + shopPrice + "g") : "";

        ItemStack pouch = new ItemStack(Material.INK_SAC);
        ItemMeta meta = pouch.getItemMeta();
        meta.setDisplayName(name);

        if (name.contains("Insane")) {
            meta.setLore(Arrays.asList(lore, "", ChatColor.RED + "Soulbound", shopLore));
        } else {
            meta.setLore(Arrays.asList(lore, shopLore));
        }

        pouch.setItemMeta(meta);

        // Set NBT data
        NBTAccessor nbt = new NBTAccessor(pouch);
        nbt.setBoolean(NBT_IS_GEM_POUCH, true)
                .setInt(NBT_MAX_CAPACITY, maxCapacity)
                .setInt(NBT_CURRENT_GEMS, 0)
                .update();

        return pouch;
    }

    /**
     * Handle inventory clicks - NBT-based
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // Handle clicks in player inventory
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
                event.getInventory().getType() != InventoryType.PLAYER) {
            return;
        }

        // Left click: Add gems to pouch
        if (event.isLeftClick() &&
                event.getCurrentItem() != null &&
                event.getCurrentItem().getType() == Material.INK_SAC &&
                isGemPouch(event.getCurrentItem()) &&
                event.getCursor() != null &&
                event.getCursor().getType() == Material.EMERALD &&
                MoneyManager.isGem(event.getCursor())) {

            // Only handle single pouches, not stacked
            if (event.getCurrentItem().getAmount() != 1) {
                return;
            }

            event.setCancelled(true);

            int currentAmount = getCurrentValue(event.getCurrentItem());
            int maxValue = getMaxValue(event.getCurrentItem());
            int gemsToAdd = event.getCursor().getAmount();

            logger.info("[GemPouchManager] Processing gem deposit: current=" + currentAmount + ", max=" + maxValue + ", adding=" + gemsToAdd);

            if (currentAmount < maxValue) {
                if (currentAmount + gemsToAdd > maxValue) {
                    // Partial addition - fill pouch and return excess
                    int canAdd = maxValue - currentAmount;
                    event.getCursor().setAmount(gemsToAdd - canAdd);
                    setPouchValue(event.getCurrentItem(), maxValue);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    player.sendMessage(ChatColor.GREEN + "Added " + canAdd + " gems to pouch! (" + (gemsToAdd - canAdd) + " remaining)");
                    logger.info("[GemPouchManager] Partially filled pouch, " + canAdd + " added");
                } else {
                    // Full addition - add all gems to pouch
                    event.setCursor(null);
                    setPouchValue(event.getCurrentItem(), currentAmount + gemsToAdd);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    player.sendMessage(ChatColor.GREEN + "Added " + gemsToAdd + " gems to pouch!");
                    logger.info("[GemPouchManager] Added all " + gemsToAdd + " gems to pouch");
                }
            } else {
                player.sendMessage(ChatColor.RED + "This pouch is already full!");
                logger.info("[GemPouchManager] Pouch is full, cannot add more");
            }
        }

        // Right click: Remove gems from pouch
        if (event.isRightClick() &&
                event.getCurrentItem() != null &&
                event.getCurrentItem().getType() == Material.INK_SAC &&
                isGemPouch(event.getCurrentItem()) &&
                (event.getCursor() == null || event.getCursor().getType() == Material.AIR)) {

            // Only handle single pouches, not stacked
            if (event.getCurrentItem().getAmount() != 1) {
                return;
            }

            event.setCancelled(true);

            int currentAmount = getCurrentValue(event.getCurrentItem());
            if (currentAmount <= 0) {
                player.sendMessage(ChatColor.RED + "This pouch is empty!");
                return;
            }

            logger.info("[GemPouchManager] Processing gem withdrawal: current=" + currentAmount);

            if (currentAmount > 64) {
                // Withdraw one stack (64 gems)
                event.setCursor(createGems(64));
                setPouchValue(event.getCurrentItem(), currentAmount - 64);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                player.sendMessage(ChatColor.GREEN + "Withdrew 64 gems from pouch!");
                logger.info("[GemPouchManager] Withdrew 64 gems from pouch");
            } else {
                // Withdraw all remaining gems
                event.setCursor(createGems(currentAmount));
                setPouchValue(event.getCurrentItem(), 0);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                player.sendMessage(ChatColor.GREEN + "Withdrew " + currentAmount + " gems from pouch!");
                logger.info("[GemPouchManager] Withdrew all " + currentAmount + " gems from pouch");
            }
        }
    }

    /**
     * Handle gem pickups - auto-deposit to pouches if available
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();

        if (event.isCancelled()) {
            return;
        }

        // Add item flags to all items for consistency
        if (event.getItem().getItemStack().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemStack().getItemMeta();
            for (ItemFlag itemFlag : ItemFlag.values()) {
                meta.addItemFlags(itemFlag);
            }
            event.getItem().getItemStack().setItemMeta(meta);
        }

        // Only process valid gem items
        if (event.getItem().getItemStack().getType() != Material.EMERALD ||
                !MoneyManager.isGem(event.getItem().getItemStack())) {
            return;
        }

        int gemsToAdd = event.getItem().getItemStack().getAmount();
        logger.info("[GemPouchManager] Processing gem pickup: " + gemsToAdd + " valid gems");

        // Check for auto-deposit to bank toggle
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer != null && yakPlayer.isToggled("Gems")) {
            logger.info("[GemPouchManager] Auto-deposit to bank enabled, skipping pouch logic");
            EconomyManager.getInstance().depositToBank(player.getUniqueId(), gemsToAdd);

            if (yakPlayer.isToggled("Debug")) {
                TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() + ChatColor.BOLD.toString() +
                        "+" + ChatColor.GREEN + gemsToAdd + ChatColor.GREEN + ChatColor.BOLD + "G");
            }

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }

        // Try to add to gem pouches
        ItemStack[] inventory = player.getInventory().getContents();

        for (int i = 0; i < inventory.length; i++) {
            ItemStack item = inventory[i];
            if (item != null && isGemPouch(item)) {
                logger.info("[GemPouchManager] Found gem pouch at slot " + i);

                // Only handle single pouches, not stacked
                if (item.getAmount() != 1) {
                    continue;
                }

                int currentAmount = getCurrentValue(item);
                int maxValue = getMaxValue(item);

                if (gemsToAdd > 0 && currentAmount < maxValue) {
                    if (currentAmount + gemsToAdd > maxValue) {
                        // Partial addition - fill pouch and continue with remainder
                        int canAdd = maxValue - currentAmount;
                        ItemStack newItem = event.getItem().getItemStack();
                        gemsToAdd -= canAdd;
                        newItem.setAmount(gemsToAdd);
                        event.getItem().setItemStack(newItem);
                        setPouchValue(item, maxValue);
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        event.setCancelled(true);

                        TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() + ChatColor.BOLD +
                                "+" + ChatColor.GREEN + canAdd + ChatColor.GREEN + ChatColor.BOLD + "G");
                        logger.info("[GemPouchManager] Partially filled pouch on pickup, added " + canAdd);
                    } else {
                        // Full addition - add all gems to pouch
                        event.getItem().remove();
                        setPouchValue(item, currentAmount + gemsToAdd);
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        event.setCancelled(true);
                        TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() + ChatColor.BOLD +
                                "+" + ChatColor.GREEN + gemsToAdd + ChatColor.GREEN + ChatColor.BOLD + "G");
                        logger.info("[GemPouchManager] Fully added " + gemsToAdd + " gems to pouch on pickup");
                        gemsToAdd = 0;
                        break;
                    }
                }
            }
        }
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

            if (itemStack.getType() == Material.SADDLE) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Check if an ItemStack is a gem pouch using NBT
     */
    public static boolean isGemPouch(ItemStack item) {
        if (item == null || item.getType() != Material.INK_SAC) return false;

        NBTAccessor nbt = new NBTAccessor(item);
        return nbt.getBoolean(NBT_IS_GEM_POUCH);
    }

    /**
     * Get the maximum capacity of a gem pouch from NBT
     */
    public static int getMaxValue(ItemStack pouch) {
        if (!isGemPouch(pouch)) return 0;

        NBTAccessor nbt = new NBTAccessor(pouch);
        return nbt.getInt(NBT_MAX_CAPACITY);
    }

    /**
     * Get the current amount of gems in a pouch from NBT
     */
    public static int getCurrentValue(ItemStack pouch) {
        if (!isGemPouch(pouch)) return 0;

        NBTAccessor nbt = new NBTAccessor(pouch);
        return nbt.getInt(NBT_CURRENT_GEMS);
    }

    /**
     * Set the amount of gems in a pouch using NBT and update display
     */
    public static void setPouchValue(ItemStack pouch, int amount) {
        if (!isGemPouch(pouch)) return;

        // Update NBT data
        NBTAccessor nbt = new NBTAccessor(pouch);
        nbt.setInt(NBT_CURRENT_GEMS, amount).update();

        // Update display name
        updatePouchDisplay(pouch, amount);
    }

    /**
     * Alternative method name for compatibility
     */
    public static void setPouchBal(ItemStack pouch, int amount) {
        setPouchValue(pouch, amount);
    }

    /**
     * Update the pouch's display name to show current gem count
     */
    private static void updatePouchDisplay(ItemStack pouch, int amount) {
        if (!pouch.hasItemMeta()) return;

        ItemMeta meta = pouch.getItemMeta();
        if (!meta.hasDisplayName()) return;

        String displayName = meta.getDisplayName();

        // Find the last occurrence of a number followed by 'g' and replace it
        String newName = displayName.replaceAll("\\d+g$", amount + "g");

        meta.setDisplayName(newName);
        pouch.setItemMeta(meta);
    }

    /**
     * Create a gem ItemStack with specified amount
     */
    public static ItemStack createGems(int amount) {
        if (amount <= 0) return null;

        ItemStack gems = new ItemStack(Material.EMERALD, Math.min(amount, 64));
        ItemMeta meta = gems.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Gem");
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "The currency of Andalucia"));
        gems.setItemMeta(meta);

        return gems;
    }
}