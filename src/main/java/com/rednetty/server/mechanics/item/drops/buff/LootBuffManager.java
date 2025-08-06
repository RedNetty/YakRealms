package com.rednetty.server.mechanics.item.drops.buff;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.LootNotifier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * FIXED: Manages loot buff effects that increase drop rates with proper initialization and thread safety
 * Key improvements:
 * - Thread-safe buff management
 * - Proper task scheduling and cleanup
 * - Enhanced buff validation and error handling
 * - Improved item interaction handling
 * - Better integration with drop systems
 */
public class LootBuffManager implements Listener {
    private static volatile LootBuffManager instance;
    private final Logger logger;
    private final YakRealms plugin;
    private final LootNotifier lootNotifier;

    // Thread-safe buff management
    private volatile LootBuff activeBuff;
    private final AtomicInteger improvedDrops = new AtomicInteger(0);
    private BukkitTask buffUpdateTask;

    // Namespaced keys for persistent data
    private final NamespacedKey keyBuffItem;
    private final NamespacedKey keyOwner;
    private final NamespacedKey keyRate;
    private final NamespacedKey keyDuration;

    /**
     * Private constructor for singleton pattern
     */
    private LootBuffManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.lootNotifier = LootNotifier.getInstance();

        // Initialize namespaced keys
        this.keyBuffItem = new NamespacedKey(plugin, "buff_item");
        this.keyOwner = new NamespacedKey(plugin, "buff_owner");
        this.keyRate = new NamespacedKey(plugin, "buff_rate");
        this.keyDuration = new NamespacedKey(plugin, "buff_duration");
    }

    /**
     * Gets the singleton instance with thread-safe double-checked locking
     */
    public static LootBuffManager getInstance() {
        if (instance == null) {
            synchronized (LootBuffManager.class) {
                if (instance == null) {
                    instance = new LootBuffManager();
                }
            }
        }
        return instance;
    }

    /**
     * FIXED: Initializes the loot buff manager with proper error handling
     */
    public void initialize() {
        try {
            // Register event listener
            plugin.getServer().getPluginManager().registerEvents(this, plugin);

            // Start buff update task
            startBuffUpdateTask();

            logger.info("§a[LootBuffManager] §7Initialized successfully");
        } catch (Exception e) {
            logger.severe("§c[LootBuffManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * IMPROVED: Starts the task to update active buffs with proper error handling
     */
    private void startBuffUpdateTask() {
        // Cancel existing task if running
        if (buffUpdateTask != null && !buffUpdateTask.isCancelled()) {
            buffUpdateTask.cancel();
        }

        buffUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    updateActiveBuff();
                } catch (Exception e) {
                    logger.warning("§c[LootBuffManager] Error updating buff: " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second

        logger.fine("§6[LootBuffManager] §7Buff update task started");
    }

    /**
     * FIXED: Thread-safe buff updating
     */
    private void updateActiveBuff() {
        LootBuff currentBuff = activeBuff;
        if (currentBuff == null) {
            return;
        }

        currentBuff.update();

        if (currentBuff.expired()) {
            endBuff(currentBuff.getOwnerName());
        } else {
            // Log remaining time for debugging (every minute)
            int remaining = currentBuff.getRemainingSeconds();
            if (remaining % 60 == 0 && logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("§6[LootBuffManager] §7Buff remaining: " + (remaining / 60) + " minutes");
            }
        }
    }

    /**
     * IMPROVED: Clean up when plugin is disabled with proper task cancellation
     */
    public void shutdown() {
        try {
            // Cancel update task
            if (buffUpdateTask != null && !buffUpdateTask.isCancelled()) {
                buffUpdateTask.cancel();
                buffUpdateTask = null;
            }

            // End active buff without announcement (server shutdown)
            if (activeBuff != null) {
                activeBuff = null;
                improvedDrops.set(0);
            }

            logger.info("§a[LootBuffManager] §7Shut down successfully");
        } catch (Exception e) {
            logger.warning("§c[LootBuffManager] Error during shutdown: " + e.getMessage());
        }
    }

    /**
     * Check if a buff is currently active (thread-safe)
     */
    public boolean isBuffActive() {
        return activeBuff != null && !activeBuff.expired();
    }

    /**
     * Get the currently active buff (thread-safe)
     */
    public LootBuff getActiveBuff() {
        LootBuff currentBuff = activeBuff;
        return (currentBuff != null && !currentBuff.expired()) ? currentBuff : null;
    }

    /**
     * Get the number of improved drops due to the active buff (thread-safe)
     */
    public int getImprovedDrops() {
        return improvedDrops.get();
    }

    /**
     * FIXED: Thread-safe update of improved drops counter
     */
    public void updateImprovedDrops() {
        if (isBuffActive()) {
            int newCount = improvedDrops.incrementAndGet();

            if (logger.isLoggable(java.util.logging.Level.FINEST)) {
                logger.finest("§6[LootBuffManager] §7Improved drops count: " + newCount);
            }
        }
    }

    /**
     * Reset the improved drops counter (thread-safe)
     */
    public void resetImprovedDrops() {
        improvedDrops.set(0);
    }

    /**
     * IMPROVED: End the current buff and announce it with proper cleanup
     */
    private synchronized void endBuff(String ownerName) {
        if (activeBuff == null) {
            return;
        }

        try {
            int finalImprovedDrops = improvedDrops.get();

            // Announce the end of the buff
            lootNotifier.announceBuffEnd(ownerName, finalImprovedDrops);

            // Reset buff state
            activeBuff = null;
            improvedDrops.set(0);

            logger.info("§6[LootBuffManager] §7Buff ended for " + ownerName +
                    " with " + finalImprovedDrops + " improved drops");

        } catch (Exception e) {
            logger.warning("§c[LootBuffManager] Error ending buff: " + e.getMessage());
            // Force reset even if announcement fails
            activeBuff = null;
            improvedDrops.set(0);
        }
    }

    /**
     * IMPROVED: Creates a loot buff item with enhanced validation
     */
    public ItemStack createBuffItem(String ownerName, UUID ownerId, int buffRate, int durationMinutes) {
        if (ownerName == null || ownerId == null || buffRate <= 0 || durationMinutes <= 0) {
            logger.warning("§c[LootBuffManager] Invalid parameters for buff item creation");
            return null;
        }

        try {
            ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta meta = item.getItemMeta();

            if (meta == null) {
                logger.warning("§c[LootBuffManager] Failed to get ItemMeta for buff item");
                return null;
            }

            // Set display name and lore
            meta.setDisplayName(ChatColor.GOLD + "LOOT BUFF " + ChatColor.RED + "(" + durationMinutes + " MINUTES)");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "⚡ Increases monster drop rate by " + ChatColor.GREEN + buffRate + "%");
            lore.add(ChatColor.LIGHT_PURPLE + "⚡ Increases elite drop rate by " + ChatColor.GREEN + (buffRate / 2) + "%");
            lore.add(ChatColor.LIGHT_PURPLE + "⚡ Affects gems, crates, and teleport books");
            lore.add(ChatColor.LIGHT_PURPLE + "⏰ Expires after " + ChatColor.YELLOW + durationMinutes + " minutes");
            lore.add("");
            lore.add(ChatColor.GRAY + "Right-click to activate server-wide!");
            lore.add("");
            lore.add(ChatColor.GREEN + "✨ Thank you for supporting the server! ✨");

            meta.setLore(lore);

            // Store buff data in persistent data container
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyBuffItem, PersistentDataType.STRING, "v2"); // Version marker
            container.set(keyOwner, PersistentDataType.STRING, ownerName + ":" + ownerId.toString());
            container.set(keyRate, PersistentDataType.INTEGER, buffRate);
            container.set(keyDuration, PersistentDataType.INTEGER, durationMinutes * 60); // Store in seconds

            item.setItemMeta(meta);

            logger.fine("§6[LootBuffManager] §7Created buff item for " + ownerName +
                    " (" + buffRate + "% for " + durationMinutes + " minutes)");

            return item;

        } catch (Exception e) {
            logger.warning("§c[LootBuffManager] Error creating buff item: " + e.getMessage());
            return null;
        }
    }

    /**
     * IMPROVED: Check if an item is a loot buff item with enhanced validation
     */
    public boolean isBuffItem(ItemStack item) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Check for buff item marker
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(keyBuffItem, PersistentDataType.STRING)) {
            return false;
        }

        // Validate required data is present
        return container.has(keyOwner, PersistentDataType.STRING) &&
                container.has(keyRate, PersistentDataType.INTEGER) &&
                container.has(keyDuration, PersistentDataType.INTEGER);
    }

    /**
     * FIXED: Extract buff data from an item with comprehensive error handling
     */
    private LootBuff extractBuffData(ItemStack item) {
        if (!isBuffItem(item)) {
            return null;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return null;
            }

            PersistentDataContainer container = meta.getPersistentDataContainer();

            String ownerData = container.get(keyOwner, PersistentDataType.STRING);
            Integer buffRate = container.get(keyRate, PersistentDataType.INTEGER);
            Integer duration = container.get(keyDuration, PersistentDataType.INTEGER);

            if (ownerData == null || buffRate == null || duration == null) {
                logger.warning("§c[LootBuffManager] Missing buff data in item");
                return null;
            }

            if (buffRate <= 0 || duration <= 0) {
                logger.warning("§c[LootBuffManager] Invalid buff values: rate=" + buffRate + ", duration=" + duration);
                return null;
            }

            String[] ownerParts = ownerData.split(":");
            if (ownerParts.length != 2) {
                logger.warning("§c[LootBuffManager] Invalid owner data format: " + ownerData);
                return null;
            }

            String ownerName = ownerParts[0];
            UUID ownerId;
            try {
                ownerId = UUID.fromString(ownerParts[1]);
            } catch (IllegalArgumentException e) {
                logger.warning("§c[LootBuffManager] Invalid UUID in owner data: " + ownerParts[1]);
                return null;
            }

            return new LootBuff(ownerName, ownerId, buffRate, duration);

        } catch (Exception e) {
            logger.warning("§c[LootBuffManager] Error extracting buff data: " + e.getMessage());
            return null;
        }
    }

    /**
     * IMPROVED: Activate a loot buff with validation and proper error handling
     */
    private synchronized boolean activateBuff(Player player, LootBuff buff) {
        if (buff == null) {
            return false;
        }

        // Double-check no buff is active
        if (isBuffActive()) {
            return false;
        }

        try {
            // Set as active buff
            this.activeBuff = buff;

            // Reset improved drops counter
            this.improvedDrops.set(0);

            // Announce buff activation
            lootNotifier.announceBuffActivation(player, buff.getBuffRate(), buff.getDurationMinutes());

            logger.info("§a[LootBuffManager] §7Buff activated by " + player.getName() +
                    " (" + buff.getBuffRate() + "% for " + buff.getDurationMinutes() + " minutes)");

            return true;

        } catch (Exception e) {
            logger.warning("§c[LootBuffManager] Error activating buff: " + e.getMessage());
            // Reset on error
            this.activeBuff = null;
            this.improvedDrops.set(0);
            return false;
        }
    }

    /**
     * FIXED: Handle player using a buff item with comprehensive validation
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Quick validation
        if (item == null || !isBuffItem(item)) {
            return;
        }

        // Cancel the event to prevent normal item use
        event.setCancelled(true);

        try {
            // Check if a buff is already active
            if (isBuffActive()) {
                LootBuff currentBuff = getActiveBuff();
                String timeRemaining = currentBuff != null ?
                        formatTime(currentBuff.getRemainingSeconds()) : "unknown";

                player.sendMessage(ChatColor.RED + "⚠ A loot buff is already active!");
                player.sendMessage(ChatColor.YELLOW + "Time remaining: " + ChatColor.WHITE + timeRemaining);
                player.sendMessage(ChatColor.GRAY + "Wait until it expires to activate another buff.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }

            // Extract buff data
            LootBuff buff = extractBuffData(item);
            if (buff == null) {
                player.sendMessage(ChatColor.RED + "✗ This buff item is corrupted and cannot be used!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Validate buff data
            if (buff.getBuffRate() <= 0 || buff.getDurationSeconds() <= 0) {
                player.sendMessage(ChatColor.RED + "✗ This buff item has invalid data!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Consume the item
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                event.getPlayer().getInventory().setItemInMainHand(null);
            }

            // Activate the buff
            boolean success = activateBuff(player, buff);
            if (!success) {
                player.sendMessage(ChatColor.RED + "✗ Failed to activate loot buff!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

                // Give item back on failure
                player.getInventory().addItem(item);
            } else {
                // Success sound is played by the announcement
                player.sendMessage(ChatColor.GREEN + "✓ Loot buff activated successfully!");
            }

        } catch (Exception e) {
            logger.warning("§c[LootBuffManager] Error in player interact event: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "✗ An error occurred while activating the buff!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * ADDED: Format time in a human-readable way
     */
    private String formatTime(int seconds) {
        if (seconds <= 0) {
            return "0 seconds";
        }

        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;

        if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "") +
                    (remainingSeconds > 0 ? " " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "") : "");
        } else {
            return remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "");
        }
    }

    /**
     * ADDED: Get buff status for debugging and admin commands
     */
    public String getBuffStatus() {
        LootBuff currentBuff = getActiveBuff();
        if (currentBuff == null) {
            return "No active buff";
        }

        return String.format("Active buff: %s, Rate: %d%%, Remaining: %s, Improved drops: %d",
                currentBuff.getOwnerName(),
                currentBuff.getBuffRate(),
                formatTime(currentBuff.getRemainingSeconds()),
                getImprovedDrops());
    }

    /**
     * ADDED: Force end active buff (for admin commands)
     */
    public boolean forceEndBuff() {
        if (!isBuffActive()) {
            return false;
        }

        LootBuff currentBuff = activeBuff;
        if (currentBuff != null) {
            endBuff(currentBuff.getOwnerName());
            return true;
        }
        return false;
    }

    /**
     * ADDED: Get buff statistics for debugging
     */
    public java.util.Map<String, Object> getBuffStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();

        LootBuff currentBuff = getActiveBuff();
        stats.put("isActive", currentBuff != null);
        stats.put("improvedDrops", getImprovedDrops());

        if (currentBuff != null) {
            stats.put("ownerName", currentBuff.getOwnerName());
            stats.put("buffRate", currentBuff.getBuffRate());
            stats.put("remainingSeconds", currentBuff.getRemainingSeconds());
            stats.put("durationSeconds", currentBuff.getDurationSeconds());
            stats.put("elapsedSeconds", currentBuff.getElapsedSeconds());
        }

        return stats;
    }
}