package com.rednetty.server.mechanics.player.mounts;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Central manager for all mount-related functionality
 */
public class MountManager {
    private static MountManager instance;

    private final YakRealms plugin;
    private final MountConfig config;
    private final Map<UUID, Mount> activeMounts = new HashMap<>();

    private HorseMount horseMount;
    private ElytraMount elytraMount;
    private MountEvents eventHandler;

    private MountManager(YakRealms plugin) {
        this.plugin = plugin;
        this.config = new MountConfig(plugin);
    }

    /**
     * Gets the singleton instance
     *
     * @return The MountManager instance
     */
    public static MountManager getInstance() {
        if (instance == null) {
            instance = new MountManager(YakRealms.getInstance());
        }
        return instance;
    }

    /**
     * Initializes the mount system
     */
    public void onEnable() {
        // Load config
        config.loadConfig();

        // Initialize mount types
        horseMount = new HorseMount(this);
        elytraMount = new ElytraMount(this);

        // Register events
        eventHandler = new MountEvents(this);
        Bukkit.getServer().getPluginManager().registerEvents(eventHandler, plugin);

        YakRealms.log("Mount system has been enabled.");
    }

    /**
     * Cleans up the mount system
     */
    public void onDisable() {
        // Remove all active mounts
        for (UUID uuid : activeMounts.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                activeMounts.get(uuid).dismount(player, false);
            }
        }
        activeMounts.clear();

        // Unregister events
        if (eventHandler != null) {
            HandlerList.unregisterAll(eventHandler);
        }

        YakRealms.log("Mount system has been disabled.");
    }

    /**
     * Gets the YakRealms plugin instance
     *
     * @return The plugin instance
     */
    public YakRealms getPlugin() {
        return plugin;
    }

    /**
     * Gets the mount config
     *
     * @return The mount config
     */
    public MountConfig getConfig() {
        return config;
    }

    /**
     * Gets the horse mount manager
     *
     * @return The horse mount manager
     */
    public HorseMount getHorseMount() {
        return horseMount;
    }

    /**
     * Gets the elytra mount manager
     *
     * @return The elytra mount manager
     */
    public ElytraMount getElytraMount() {
        return elytraMount;
    }

    /**
     * Registers an active mount
     *
     * @param playerUUID The player UUID
     * @param mount      The mount instance
     */
    public void registerActiveMount(UUID playerUUID, Mount mount) {
        activeMounts.put(playerUUID, mount);
    }

    /**
     * Unregisters an active mount
     *
     * @param playerUUID The player UUID
     * @return The mount that was unregistered, or null if none
     */
    public Mount unregisterActiveMount(UUID playerUUID) {
        return activeMounts.remove(playerUUID);
    }

    /**
     * Checks if a player has an active mount
     *
     * @param playerUUID The player UUID
     * @return True if the player has an active mount
     */
    public boolean hasActiveMount(UUID playerUUID) {
        return activeMounts.containsKey(playerUUID);
    }

    /**
     * Gets a player's active mount
     *
     * @param playerUUID The player UUID
     * @return The mount instance, or null if none
     */
    public Mount getActiveMount(UUID playerUUID) {
        return activeMounts.get(playerUUID);
    }

    /**
     * Gets a player's mount type
     *
     * @param player The player
     * @return The mount type
     */
    public MountType getMountType(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) {
            return MountType.NONE;
        }

        // Check for elytra permission first
        if (player.hasPermission("yakrp.mount.elytra")) {
            return MountType.ELYTRA;
        }

        // Then check horse tier
        int horseTier = yakPlayer.getHorseTier();
        if (horseTier >= 2) {
            return MountType.HORSE;
        }

        return MountType.NONE;
    }

    /**
     * Gets a player's horse tier
     *
     * @param player The player
     * @return The horse tier (0 if none)
     */
    public int getHorseTier(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) {
            return 0;
        }
        return yakPlayer.getHorseTier();
    }

    /**
     * Sets a player's horse tier
     *
     * @param player The player
     * @param tier   The new tier
     */
    public void setHorseTier(Player player, int tier) {
        YakPlayerManager playerManager = YakPlayerManager.getInstance();
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            return;
        }

        yakPlayer.setHorseTier(tier);
        playerManager.savePlayer(yakPlayer).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to save horse tier for " + player.getName(), ex);
            return false;
        });
    }

    /**
     * Attempts to summon a mount for a player
     *
     * @param player    The player
     * @param mountType The mount type
     * @return True if the summoning process started
     */
    public boolean summonMount(Player player, MountType mountType) {
        // Check if player already has a mount
        if (hasActiveMount(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active mount!");
            return false;
        }

        // Attempt to summon the requested mount type
        switch (mountType) {
            case HORSE:
                return horseMount.summonMount(player);
            case ELYTRA:
                return elytraMount.summonMount(player);
            default:
                return false;
        }
    }

    /**
     * Dismounts a player
     *
     * @param player      The player
     * @param sendMessage Whether to send a message
     * @return True if the player was dismounted
     */
    public boolean dismountPlayer(Player player, boolean sendMessage) {
        Mount mount = getActiveMount(player.getUniqueId());
        if (mount != null) {
            return mount.dismount(player, sendMessage);
        }
        return false;
    }
}