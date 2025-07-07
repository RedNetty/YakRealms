package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Core manager that registers and controls all player-related listeners
 * This replaces the old monolithic Listeners.java class with a modular approach
 */
public class PlayerListenerManager {
    private static PlayerListenerManager instance;
    private final YakRealms plugin;
    private final Logger logger;
    private final List<BaseListener> listeners = new ArrayList<>();
    private BukkitTask foodLevelTask;
    private BukkitTask effectsTask;

    // Specific listener instances
    private CombatListener combatListener;
    private HealthListener healthListener;
    private EquipmentListener equipmentListener;
    private ItemInteractionListener itemInteractionListener;
    private JoinLeaveListener joinLeaveListener;
    private InventoryListener inventoryListener;
    private ItemDropListener itemDropListener;
    private VisualEffectsListener visualEffectsListener;
    private WorldListener worldListener;
    private MenuItemListener menuItemListener;
    private TradeListener tradeListener;

    /**
     * Private constructor for singleton pattern
     */
    private PlayerListenerManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The PlayerListenerManager instance
     */
    public static PlayerListenerManager getInstance() {
        if (instance == null) {
            instance = new PlayerListenerManager();
        }
        return instance;
    }

    /**
     * Initialize and register all listeners
     */
    public void onEnable() {
        logger.info("[PlayerListenerManager] Initializing listener system...");
        PluginManager pm = Bukkit.getPluginManager();

        // Initialize all listeners
        combatListener = new CombatListener();
        healthListener = new HealthListener();
        equipmentListener = new EquipmentListener();
        itemInteractionListener = new ItemInteractionListener();
        joinLeaveListener = new JoinLeaveListener();
        inventoryListener = new InventoryListener();
        itemDropListener = new ItemDropListener();
        visualEffectsListener = new VisualEffectsListener();
        worldListener = new WorldListener();
        menuItemListener = new MenuItemListener(YakRealms.getInstance());
        tradeListener = new TradeListener(YakRealms.getInstance());

        // Add to list for management
        listeners.add(menuItemListener);
        listeners.add(combatListener);
        listeners.add(healthListener);
        listeners.add(equipmentListener);
        listeners.add(joinLeaveListener);
        listeners.add(inventoryListener);
        listeners.add(itemDropListener);
        listeners.add(itemInteractionListener);
        listeners.add(visualEffectsListener);
        listeners.add(worldListener);
        listeners.add(tradeListener);

        // Register each listener
        for (BaseListener listener : listeners) {
            pm.registerEvents(listener, plugin);
            listener.initialize();
        }

        // Start recurring tasks
        startRecurringTasks();

        logger.info("[PlayerListenerManager] All listeners have been initialized and registered.");
    }

    /**
     * Start recurring tasks previously in the Listeners class
     */
    private void startRecurringTasks() {
        // Food level maintenance task (every 5 seconds)
        foodLevelTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
            });
        }, 200L, 100L);

        // Visual effects task (every 0.5 seconds)
        effectsTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Delegate to the visual effects processor
            visualEffectsListener.processPlayerEffects();
        }, 0L, 10L);
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        // Cancel tasks
        if (foodLevelTask != null) {
            foodLevelTask.cancel();
        }
        if (effectsTask != null) {
            effectsTask.cancel();
        }

        // Clean up all listeners
        for (BaseListener listener : listeners) {
            listener.cleanup();
        }

        listeners.clear();
        logger.info("[PlayerListenerManager] All listeners have been disabled.");
    }

    // Accessor methods for specific listeners

    /**
     * Get the combat listener
     *
     * @return The combat listener instance
     */
    public CombatListener getCombatListener() {
        return combatListener;
    }

    /**
     * Get the health listener
     *
     * @return The health listener instance
     */
    public HealthListener getHealthListener() {
        return healthListener;
    }

    /**
     * Get the equipment listener
     *
     * @return The equipment listener instance
     */
    public EquipmentListener getEquipmentListener() {
        return equipmentListener;
    }

    /**
     * Get the item interaction listener
     *
     * @return The item interaction listener instance
     */
    public ItemInteractionListener getItemInteractionListener() {
        return itemInteractionListener;
    }

    /**
     * Get the join/leave listener
     *
     * @return The join/leave listener instance
     */
    public JoinLeaveListener getJoinLeaveListener() {
        return joinLeaveListener;
    }

    /**
     * Get the inventory listener
     *
     * @return The inventory listener instance
     */
    public InventoryListener getInventoryListener() {
        return inventoryListener;
    }

    /**
     * Get the item drop listener
     *
     * @return The item drop listener instance
     */
    public ItemDropListener getItemDropListener() {
        return itemDropListener;
    }

    /**
     * Get the visual effects listener
     *
     * @return The visual effects listener instance
     */
    public VisualEffectsListener getVisualEffectsListener() {
        return visualEffectsListener;
    }

    /**
     * Get the world listener
     *
     * @return The world listener instance
     */
    public WorldListener getWorldListener() {
        return worldListener;
    }
}