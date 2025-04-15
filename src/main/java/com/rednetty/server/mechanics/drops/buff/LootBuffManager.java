package com.rednetty.server.mechanics.drops.buff;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.drops.LootNotifier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages loot buff effects that increase drop rates
 */
public class LootBuffManager implements Listener {
    private static LootBuffManager instance;
    private final Logger logger;
    private final YakRealms plugin;
    private final LootNotifier lootNotifier;

    private LootBuff activeBuff;
    private int improvedDrops = 0;

    /**
     * Private constructor for singleton pattern
     */
    private LootBuffManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.lootNotifier = LootNotifier.getInstance();
    }

    /**
     * Gets the singleton instance
     *
     * @return The LootBuffManager instance
     */
    public static LootBuffManager getInstance() {
        if (instance == null) {
            instance = new LootBuffManager();
        }
        return instance;
    }

    /**
     * Initializes the loot buff manager
     */
    public void initialize() {
        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start buff update task
        startBuffUpdateTask();

        logger.info("[LootBuffManager] has been initialized");
    }

    /**
     * Starts the task to update active buffs
     */
    private void startBuffUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeBuff == null) {
                    return;
                }

                activeBuff.update();

                if (activeBuff.expired()) {
                    endBuff(activeBuff.getOwnerName());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
    }

    /**
     * Clean up when plugin is disabled
     */
    public void shutdown() {
        if (activeBuff != null) {
            endBuff(activeBuff.getOwnerName());
        }
        logger.info("[LootBuffManager] has been shut down");
    }

    /**
     * Check if a buff is currently active
     *
     * @return true if a buff is active
     */
    public boolean isBuffActive() {
        return activeBuff != null;
    }

    /**
     * Get the currently active buff
     *
     * @return the active LootBuff or null if none active
     */
    public LootBuff getActiveBuff() {
        return activeBuff;
    }

    /**
     * Get the number of improved drops due to the active buff
     *
     * @return the count of improved drops
     */
    public int getImprovedDrops() {
        return improvedDrops;
    }

    /**
     * Update the count of improved drops
     */
    public void updateImprovedDrops() {
        this.improvedDrops++;
    }

    /**
     * Reset the improved drops counter
     */
    public void resetImprovedDrops() {
        this.improvedDrops = 0;
    }

    /**
     * End the current buff and announce it
     *
     * @param ownerName name of the buff owner
     */
    private void endBuff(String ownerName) {
        // Announce the end of the buff
        lootNotifier.announceBuffEnd(ownerName, improvedDrops);

        // Reset buff state
        activeBuff = null;
        improvedDrops = 0;
    }

    /**
     * Creates a loot buff item
     *
     * @param ownerName       player name who will own the buff
     * @param ownerId         player UUID who will own the buff
     * @param buffRate        percentage increase for drop rates
     * @param durationMinutes duration in minutes
     * @return the created buff item
     */
    public ItemStack createBuffItem(String ownerName, UUID ownerId, int buffRate, int durationMinutes) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "LOOT BUFF " + ChatColor.RED + "(" + durationMinutes + " MINUTES)");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "- Increases monster drop rate by " + buffRate + "%");
            lore.add(ChatColor.LIGHT_PURPLE + "- Increases elite drop rate by " + (buffRate / 2) + "%");
            lore.add(ChatColor.LIGHT_PURPLE + "- Expires after " + durationMinutes + " minutes");
            lore.add("");
            lore.add(ChatColor.GREEN + "Thank you for supporting the server!");

            meta.setLore(lore);
            item.setItemMeta(meta);

            // Store buff data
            org.bukkit.NamespacedKey keyBuffItem = new org.bukkit.NamespacedKey(plugin, "buff.item");
            org.bukkit.NamespacedKey keyOwner = new org.bukkit.NamespacedKey(plugin, "buff.owner");
            org.bukkit.NamespacedKey keyRate = new org.bukkit.NamespacedKey(plugin, "buff.rate");
            org.bukkit.NamespacedKey keyDuration = new org.bukkit.NamespacedKey(plugin, "buff.duration");

            org.bukkit.persistence.PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyBuffItem, org.bukkit.persistence.PersistentDataType.STRING, "true");
            container.set(keyOwner, org.bukkit.persistence.PersistentDataType.STRING, ownerName + ":" + ownerId.toString());
            container.set(keyRate, org.bukkit.persistence.PersistentDataType.INTEGER, buffRate);
            container.set(keyDuration, org.bukkit.persistence.PersistentDataType.INTEGER, durationMinutes * 60); // store in seconds

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Check if an item is a loot buff item
     *
     * @param item the item to check
     * @return true if it's a loot buff item
     */
    public boolean isBuffItem(ItemStack item) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        org.bukkit.NamespacedKey keyBuffItem = new org.bukkit.NamespacedKey(plugin, "buff.item");
        org.bukkit.persistence.PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(keyBuffItem, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * Extract buff data from an item
     *
     * @param item the buff item
     * @return the LootBuff object or null if invalid
     */
    private LootBuff extractBuffData(ItemStack item) {
        if (!isBuffItem(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        org.bukkit.NamespacedKey keyOwner = new org.bukkit.NamespacedKey(plugin, "buff.owner");
        org.bukkit.NamespacedKey keyRate = new org.bukkit.NamespacedKey(plugin, "buff.rate");
        org.bukkit.NamespacedKey keyDuration = new org.bukkit.NamespacedKey(plugin, "buff.duration");

        org.bukkit.persistence.PersistentDataContainer container = meta.getPersistentDataContainer();

        if (!container.has(keyOwner, org.bukkit.persistence.PersistentDataType.STRING) ||
                !container.has(keyRate, org.bukkit.persistence.PersistentDataType.INTEGER) ||
                !container.has(keyDuration, org.bukkit.persistence.PersistentDataType.INTEGER)) {
            return null;
        }

        String ownerData = container.get(keyOwner, org.bukkit.persistence.PersistentDataType.STRING);
        int buffRate = container.get(keyRate, org.bukkit.persistence.PersistentDataType.INTEGER);
        int duration = container.get(keyDuration, org.bukkit.persistence.PersistentDataType.INTEGER);

        if (ownerData == null) {
            return null;
        }

        String[] ownerParts = ownerData.split(":");
        if (ownerParts.length != 2) {
            return null;
        }

        String ownerName = ownerParts[0];
        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerParts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return new LootBuff(ownerName, ownerId, buffRate, duration);
    }

    /**
     * Activate a loot buff
     *
     * @param player the player activating the buff
     * @param buff   the buff to activate
     */
    private void activateBuff(Player player, LootBuff buff) {
        // Set as active buff
        this.activeBuff = buff;

        // Reset improved drops counter
        this.improvedDrops = 0;

        // Announce buff activation
        lootNotifier.announceBuffActivation(player, buff.getBuffRate(), buff.getDurationMinutes());
    }

    /**
     * Handle player using a buff item
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !isBuffItem(item)) {
            return;
        }

        // Cancel the event to prevent normal item use
        event.setCancelled(true);

        // Check if a buff is already active
        if (isBuffActive()) {
            player.sendMessage(ChatColor.RED + "Another player has already activated a loot buff. Wait until it expires!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            return;
        }

        // Extract buff data
        LootBuff buff = extractBuffData(item);
        if (buff == null) {
            player.sendMessage(ChatColor.RED + "This buff item is invalid!");
            return;
        }

        // Consume the item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Activate the buff
        activateBuff(player, buff);
    }
}