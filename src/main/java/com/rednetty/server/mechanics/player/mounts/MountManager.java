package com.rednetty.server.mechanics.player.mounts;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.mounts.type.ElytraMount;
import com.rednetty.server.mechanics.player.mounts.type.HorseMount;
import com.rednetty.server.mechanics.player.mounts.type.Mount;
import com.rednetty.server.mechanics.player.mounts.type.MountType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for all mount-related functionality with improved state tracking and validation
 */
public class MountManager {
    private static MountManager instance;

    private final YakRealms plugin;
    private final MountConfig config;

    // IMPROVED: Use ConcurrentHashMap for thread safety
    private final Map<UUID, Mount> activeMounts = new ConcurrentHashMap<>();
    private final Map<UUID, MountType> activeMountTypes = new ConcurrentHashMap<>();

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
                Mount mount = activeMounts.get(uuid);
                if (mount != null) {
                    mount.dismount(player, false);
                }
            }
        }
        activeMounts.clear();
        activeMountTypes.clear();

        // Cleanup mount type specific data
        if (horseMount != null) {
            horseMount.cleanup();
        }

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
     *  Gets a copy of the active mounts map for validation
     *
     * @return A copy of the active mounts map
     */
    public Map<UUID, Mount> getActiveMounts() {
        return new HashMap<>(activeMounts);
    }

    /**
     * IMPROVED: Registers an active mount with  tracking and validation
     *
     * @param playerUUID The player UUID
     * @param mount      The mount instance
     */
    public void registerActiveMount(UUID playerUUID, Mount mount) {
        if (mount == null) {
            YakRealms.getInstance().getLogger().warning("Attempted to register null mount for player " + playerUUID);
            return;
        }

        // CRITICAL: Validate the mount state before registering
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) {
            YakRealms.getInstance().getLogger().warning("Attempted to register mount for offline player " + playerUUID);
            return;
        }

        // Validate mount type specific requirements
        if (mount.getType() == MountType.HORSE) {
            if (!player.isInsideVehicle() || !(player.getVehicle() instanceof Horse)) {
                YakRealms.getInstance().getLogger().warning("Attempted to register horse mount for player not in horse vehicle: " + player.getName());
                return;
            }
        } else if (mount.getType() == MountType.ELYTRA) {
            if (!player.isGliding()) {
                YakRealms.getInstance().getLogger().warning("Attempted to register elytra mount for player not gliding: " + player.getName());
                return;
            }
        }

        activeMounts.put(playerUUID, mount);
        activeMountTypes.put(playerUUID, mount.getType());

        // Debug logging
        YakRealms.getInstance().getLogger().info("Registered " + mount.getType() + " mount for " + player.getName());
    }

    /**
     * IMPROVED: Unregisters an active mount with  cleanup
     *
     * @param playerUUID The player UUID
     * @return The mount that was unregistered, or null if none
     */
    public Mount unregisterActiveMount(UUID playerUUID) {
        Mount removed = activeMounts.remove(playerUUID);
        MountType removedType = activeMountTypes.remove(playerUUID);

        // Debug logging
        if (removed != null) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                YakRealms.getInstance().getLogger().info("Unregistered " + removedType + " mount for " + player.getName());
            } else {
                YakRealms.getInstance().getLogger().info("Unregistered " + removedType + " mount for offline player " + playerUUID);
            }
        }

        return removed;
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
     * IMPROVED: Gets a player's active mount type
     *
     * @param playerUUID The player UUID
     * @return The mount type, or NONE if no active mount
     */
    public MountType getActiveMountType(UUID playerUUID) {
        return activeMountTypes.getOrDefault(playerUUID, MountType.NONE);
    }

    /**
     * IMPROVED: Checks if a player has a specific type of active mount
     *
     * @param playerUUID The player UUID
     * @param mountType  The mount type to check
     * @return True if the player has the specified mount type active
     */
    public boolean hasActiveMountType(UUID playerUUID, MountType mountType) {
        MountType activeType = activeMountTypes.get(playerUUID);
        return activeType != null && activeType == mountType;
    }

    /**
     *  Validates a player's mount state and fixes issues if found
     *
     * @param player The player to validate
     * @return True if the mount state is valid or was successfully 
     */
    public boolean validateMountState(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!hasActiveMount(playerUUID)) {
            return true; // No mount to validate
        }

        Mount activeMount = getActiveMount(playerUUID);
        MountType mountType = getActiveMountType(playerUUID);

        switch (mountType) {
            case HORSE -> {
                // Validate horse mount
                if (!player.isInsideVehicle() || !(player.getVehicle() instanceof Horse)) {
                    // Player is not in a horse vehicle
                    Horse horse = horseMount.getActiveHorse(player);
                    if (horse == null || !horse.isValid()) {
                        // Horse doesn't exist, clear registration
                        unregisterActiveMount(playerUUID);
                        player.sendMessage(ChatColor.YELLOW + "Your mount registration has been cleared due to an error.");
                        return true;
                    } else {
                        // Try to remount if close
                        if (horse.getLocation().distance(player.getLocation()) <= 10.0 && horse.getPassengers().isEmpty()) {
                            if (horse.addPassenger(player)) {
                                player.sendMessage(ChatColor.GREEN + "Remounted your horse.");
                                return true;
                            }
                        }
                        // If remounting failed, dismount properly
                        activeMount.dismount(player, false);
                        player.sendMessage(ChatColor.YELLOW + "Your mount was too far away or invalid and has been dismissed.");
                        return true;
                    }
                }
                return true; // Horse mount is valid
            }
            case ELYTRA -> {
                // Validate elytra mount
                if (!player.isGliding() || !elytraMount.isUsingElytra(player)) {
                    // Player is not actually using elytra
                    activeMount.dismount(player, false);
                    player.sendMessage(ChatColor.YELLOW + "Your elytra mount registration has been cleared due to an error.");
                    return true;
                }
                return true; // Elytra mount is valid
            }
            default -> {
                // Unknown mount type, clear it
                unregisterActiveMount(playerUUID);
                player.sendMessage(ChatColor.YELLOW + "Unknown mount type cleared.");
                return true;
            }
        }
    }

    /**
     * Gets a player's mount type preference based on permissions and tiers
     *
     * @param player The player
     * @return The mount type the player can use
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

        // Then check horse tier ( Allow tier 1+)
        int horseTier = yakPlayer.getHorseTier();
        if (horseTier >= 1) {
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
     * IMPROVED: Attempts to summon a mount for a player with better validation
     *
     * @param player    The player
     * @param mountType The mount type
     * @return True if the summoning process started
     */
    public boolean summonMount(Player player, MountType mountType) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        UUID playerUUID = player.getUniqueId();

        // CRITICAL: Validate current mount state first
        if (hasActiveMount(playerUUID)) {
            if (!validateMountState(player)) {
                return false; // Validation failed
            }

            // Check again after validation
            if (hasActiveMount(playerUUID)) {
                MountType activeType = getActiveMountType(playerUUID);
                player.sendMessage("§cYou already have an active " + activeType.name().toLowerCase() + " mount!");
                return false;
            }
        }

        // Check if player is already summoning any mount
        if (horseMount.isSummoning(player) || elytraMount.isSummoning(player)) {
            player.sendMessage("§cYou are already summoning a mount.");
            return false;
        }

        // Validate mount type permissions/requirements
        if (!canPlayerUseMountType(player, mountType)) {
            return false;
        }

        // Attempt to summon the requested mount type
        boolean success = switch (mountType) {
            case HORSE -> horseMount.summonMount(player);
            case ELYTRA -> elytraMount.summonMount(player);
            default -> false;
        };

        if (success) {
            YakRealms.getInstance().getLogger().info(player.getName() + " started summoning " + mountType + " mount");
        }

        return success;
    }

    /**
     * IMPROVED: Checks if a player can use a specific mount type
     *
     * @param player    The player
     * @param mountType The mount type
     * @return True if the player can use this mount type
     */
    private boolean canPlayerUseMountType(Player player, MountType mountType) {
        switch (mountType) {
            case HORSE -> {
                int horseTier = getHorseTier(player);
                if (horseTier < 1) {
                    player.sendMessage("§cYou don't have a horse mount.");
                    player.sendMessage("§eVisit the Mount Stable to purchase tier access!");
                    return false;
                }
                return true;
            }
            case ELYTRA -> {
                return true;
            }
            default -> {
                player.sendMessage("§cInvalid mount type.");
                return false;
            }
        }
    }

    /**
     * IMPROVED: Dismounts a player with better error handling and validation
     *
     * @param player      The player
     * @param sendMessage Whether to send a message
     * @return True if the player was dismounted
     */
    public boolean dismountPlayer(Player player, boolean sendMessage) {
        if (player == null) {
            return false;
        }

        UUID playerUUID = player.getUniqueId();
        Mount mount = getActiveMount(playerUUID);

        if (mount != null) {
            boolean success = mount.dismount(player, sendMessage);
            if (success) {
                YakRealms.getInstance().getLogger().info(player.getName() + " was dismounted from " + mount.getType() + " mount");
            }
            return success;
        } else {
            // No mount registered, but make sure player isn't in a problematic state
            if (player.isInsideVehicle() && player.getVehicle() instanceof Horse) {
                // Player is in a horse but not registered - force exit
                player.getVehicle().removePassenger(player);
                player.sendMessage(ChatColor.YELLOW + "Cleared unregistered horse mount.");
                return true;
            }
        }

        return false;
    }

    /**
     * IMPROVED: Emergency dismount all players (for server shutdown)
     */
    public void dismountAllPlayers() {
        YakRealms.getInstance().getLogger().info("Emergency dismounting all players...");

        for (Map.Entry<UUID, Mount> entry : activeMounts.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                Mount mount = entry.getValue();
                try {
                    mount.dismount(player, false);
                } catch (Exception e) {
                    YakRealms.getInstance().getLogger().warning("Failed to dismount " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        activeMounts.clear();
        activeMountTypes.clear();
    }

    /**
     * IMPROVED: Gets debugging information about active mounts
     *
     * @return Debug information string
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Active Mounts: ").append(activeMounts.size()).append("\n");

        for (Map.Entry<UUID, Mount> entry : activeMounts.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            String playerName = player != null ? player.getName() : "OFFLINE";
            String vehicleStatus = "UNKNOWN";

            if (player != null && player.isOnline()) {
                if (entry.getValue().getType() == MountType.HORSE) {
                    vehicleStatus = player.isInsideVehicle() && player.getVehicle() instanceof Horse ? "MOUNTED" : "NOT_MOUNTED";
                } else if (entry.getValue().getType() == MountType.ELYTRA) {
                    vehicleStatus = player.isGliding() ? "GLIDING" : "NOT_GLIDING";
                }
            }

            sb.append("- ").append(playerName).append(": ").append(entry.getValue().getType())
                    .append(" (").append(vehicleStatus).append(")").append("\n");
        }

        return sb.toString();
    }
}