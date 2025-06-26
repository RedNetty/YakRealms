package com.rednetty.server.mechanics.stattrak;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.stattrak.trackers.PickaxeStatTracker;
import com.rednetty.server.mechanics.stattrak.trackers.WeaponStatTracker;
import com.rednetty.server.mechanics.stattrak.types.StatTrakItem;
import com.rednetty.server.mechanics.stattrak.types.StatType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Enhanced StatTrak System Manager
 * Tracks statistics on weapons and tools with improved functionality
 */
public class StatTrakManager {
    private static StatTrakManager instance;
    private final YakRealms plugin;
    private final Logger logger;

    // Component trackers
    private WeaponStatTracker weaponTracker;
    private PickaxeStatTracker pickaxeTracker;
    private StatTrakHandler eventHandler;
    private StatTrakFactory factory;

    // Active stat tracking sessions
    private final Map<UUID, Long> recentKills = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentMining = new ConcurrentHashMap<>();

    // Configuration
    private static final long KILL_TRACKING_WINDOW = 5000; // 5 seconds
    private static final long MINING_TRACKING_WINDOW = 1000; // 1 second

    /**
     * Private constructor for singleton pattern
     */
    private StatTrakManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The StatTrakManager instance
     */
    public static synchronized StatTrakManager getInstance() {
        if (instance == null) {
            instance = new StatTrakManager();
        }
        return instance;
    }

    /**
     * Initializes the StatTrak system
     */
    public void initialize() {
        try {
            // Initialize components
            this.weaponTracker = new WeaponStatTracker();
            this.pickaxeTracker = new PickaxeStatTracker();
            this.factory = new StatTrakFactory();
            this.eventHandler = new StatTrakHandler();

            // Register event handler
            Bukkit.getPluginManager().registerEvents(eventHandler, plugin);

            // Start cleanup task
            startCleanupTask();

            logger.info("[StatTrakManager] Enhanced StatTrak system initialized successfully");
        } catch (Exception e) {
            logger.severe("[StatTrakManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a weapon stat tracker item
     *
     * @return The weapon stat tracker item
     */
    public ItemStack createWeaponStatTracker() {
        return factory.createWeaponStatTracker();
    }

    /**
     * Creates a pickaxe stat tracker item
     *
     * @return The pickaxe stat tracker item
     */
    public ItemStack createPickaxeStatTracker() {
        return factory.createPickaxeStatTracker();
    }

    /**
     * Applies a stat tracker to an item
     *
     * @param item        The item to apply the tracker to
     * @param trackerType The type of tracker
     * @return The modified item with stat tracking
     */
    public ItemStack applyStatTracker(ItemStack item, StatTrakItem trackerType) {
        if (item == null || !isValidItemForTracker(item, trackerType)) {
            return item;
        }

        try {
            switch (trackerType) {
                case WEAPON_TRACKER:
                    return weaponTracker.applyStatTracking(item);
                case PICKAXE_TRACKER:
                    return pickaxeTracker.applyStatTracking(item);
                default:
                    logger.warning("Unknown tracker type: " + trackerType);
                    return item;
            }
        } catch (Exception e) {
            logger.warning("Error applying stat tracker: " + e.getMessage());
            return item;
        }
    }

    /**
     * Records a player kill for weapon stat tracking
     *
     * @param killer     The player who made the kill
     * @param weaponSlot The slot containing the weapon used
     * @param isPlayer   Whether the kill was a player or mob
     */
    public void recordKill(Player killer, int weaponSlot, boolean isPlayer) {
        if (killer == null) return;

        try {
            ItemStack weapon = killer.getInventory().getItem(weaponSlot);
            if (weapon != null && hasStatTracking(weapon, StatTrakItem.WEAPON_TRACKER)) {
                UUID killerId = killer.getUniqueId();
                long currentTime = System.currentTimeMillis();

                // Check for rapid kills (prevent spam)
                Long lastKill = recentKills.get(killerId);
                if (lastKill != null && (currentTime - lastKill) < KILL_TRACKING_WINDOW) {
                    return;
                }

                recentKills.put(killerId, currentTime);

                // Update weapon stats
                ItemStack updatedWeapon = weaponTracker.recordKill(weapon, isPlayer);
                killer.getInventory().setItem(weaponSlot, updatedWeapon);

                // Visual feedback
                playStatUpdateEffects(killer, isPlayer ? "Player Kill" : "Mob Kill");
            }
        } catch (Exception e) {
            logger.warning("Error recording kill for player " + killer.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Records mining activity for pickaxe stat tracking
     *
     * @param player    The player who mined
     * @param itemSlot  The slot containing the pickaxe
     * @param material  The material that was mined
     * @param isGem     Whether the mined material is considered a gem
     */
    public void recordMining(Player player, int itemSlot, Material material, boolean isGem) {
        if (player == null) return;

        try {
            ItemStack pickaxe = player.getInventory().getItem(itemSlot);
            if (pickaxe != null && hasStatTracking(pickaxe, StatTrakItem.PICKAXE_TRACKER)) {
                UUID playerId = player.getUniqueId();
                long currentTime = System.currentTimeMillis();

                // Check for rapid mining (prevent spam)
                Long lastMining = recentMining.get(playerId);
                if (lastMining != null && (currentTime - lastMining) < MINING_TRACKING_WINDOW) {
                    return;
                }

                recentMining.put(playerId, currentTime);

                // Update pickaxe stats
                ItemStack updatedPickaxe = pickaxeTracker.recordMining(pickaxe, material, isGem);
                player.getInventory().setItem(itemSlot, updatedPickaxe);

                // Visual feedback for gems only
                if (isGem) {
                    playStatUpdateEffects(player, "Gem Found");
                }
            }
        } catch (Exception e) {
            logger.warning("Error recording mining for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Records orb usage for weapon stat tracking
     *
     * @param player     The player using the orb
     * @param weaponSlot The slot containing the weapon
     * @param isLegendary Whether it's a legendary orb
     */
    public void recordOrbUsage(Player player, int weaponSlot, boolean isLegendary) {
        if (player == null) return;

        try {
            ItemStack weapon = player.getInventory().getItem(weaponSlot);
            if (weapon != null && hasStatTracking(weapon, StatTrakItem.WEAPON_TRACKER)) {
                ItemStack updatedWeapon = weaponTracker.recordOrbUsage(weapon, isLegendary);
                player.getInventory().setItem(weaponSlot, updatedWeapon);

                // Visual feedback
                playStatUpdateEffects(player, isLegendary ? "Legendary Orb Used" : "Orb Used");
            }
        } catch (Exception e) {
            logger.warning("Error recording orb usage for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Checks if an item has stat tracking of a specific type
     *
     * @param item        The item to check
     * @param trackerType The tracker type to look for
     * @return true if the item has the specified stat tracking
     */
    public boolean hasStatTracking(ItemStack item, StatTrakItem trackerType) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }

        String trackerIndicator = switch (trackerType) {
            case WEAPON_TRACKER -> "StatTrak";
            case PICKAXE_TRACKER -> "StatTrak";
        };

        return item.getItemMeta().getLore().stream()
                .anyMatch(line -> line.contains(trackerIndicator));
    }

    /**
     * Gets the current stat value for a specific stat type from an item
     *
     * @param item     The item to check
     * @param statType The stat type to get
     * @return The current stat value, or 0 if not found
     */
    public int getStatValue(ItemStack item, StatType statType) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }

        String statName = statType.getDisplayName();
        return item.getItemMeta().getLore().stream()
                .filter(line -> line.contains(statName + ": "))
                .findFirst()
                .map(line -> {
                    try {
                        String valueStr = line.split(": " + ChatColor.AQUA)[1];
                        return Integer.parseInt(valueStr);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .orElse(0);
    }

    /**
     * Checks if an item is valid for a specific tracker type
     *
     * @param item        The item to check
     * @param trackerType The tracker type
     * @return true if the item is valid for the tracker
     */
    private boolean isValidItemForTracker(ItemStack item, StatTrakItem trackerType) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        String materialName = item.getType().name();

        return switch (trackerType) {
            case WEAPON_TRACKER -> isWeapon(materialName);
            case PICKAXE_TRACKER -> isPickaxe(materialName);
        };
    }

    /**
     * Checks if a material name represents a weapon
     *
     * @param materialName The material name
     * @return true if it's a weapon
     */
    private boolean isWeapon(String materialName) {
        return materialName.contains("_SWORD") ||
                materialName.contains("_AXE") ||
                materialName.contains("_HOE") ||
                materialName.contains("_SHOVEL");
    }

    /**
     * Checks if a material name represents a pickaxe
     *
     * @param materialName The material name
     * @return true if it's a pickaxe
     */
    private boolean isPickaxe(String materialName) {
        return materialName.contains("_PICKAXE");
    }

    /**
     * Plays visual and audio effects when stats are updated
     *
     * @param player   The player to show effects to
     * @param statName The name of the stat that was updated
     */
    private void playStatUpdateEffects(Player player, String statName) {
        // Subtle sound effect
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);

        // Optional: Send action bar message for important stats
        if (statName.contains("Kill") || statName.contains("Gem")) {
            // This would require ActionBarUtil integration
            // ActionBarUtil.sendActionBar(player, ChatColor.GOLD + "StatTrak: " + statName + "!");
        }
    }

    /**
     * Starts the cleanup task for expired tracking data
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            // Clean up expired kill tracking
            recentKills.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > KILL_TRACKING_WINDOW * 2);

            // Clean up expired mining tracking
            recentMining.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > MINING_TRACKING_WINDOW * 2);

        }, 1200L, 1200L); // Run every minute
    }

    /**
     * Shuts down the StatTrak system
     */
    public void shutdown() {
        recentKills.clear();
        recentMining.clear();
        logger.info("[StatTrakManager] has been shut down");
    }

    // Getter methods
    public WeaponStatTracker getWeaponTracker() { return weaponTracker; }
    public PickaxeStatTracker getPickaxeTracker() { return pickaxeTracker; }
    public StatTrakHandler getEventHandler() { return eventHandler; }
    public StatTrakFactory getFactory() { return factory; }

    /**
     * Gets manager statistics for debugging
     *
     * @return Statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("recentKills", recentKills.size());
        stats.put("recentMining", recentMining.size());
        stats.put("killTrackingWindow", KILL_TRACKING_WINDOW);
        stats.put("miningTrackingWindow", MINING_TRACKING_WINDOW);
        return stats;
    }
}

