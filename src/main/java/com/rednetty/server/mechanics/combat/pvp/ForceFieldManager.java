package com.rednetty.server.mechanics.combat.pvp;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIXED Manages force field visualization for safe zone boundaries
 *
 * CRITICAL FIXES:
 * - Added public updatePlayerForceField method for immediate force field updates
 * - Improved combat tag detection integration
 * - Better error handling and logging
 * - Optimized force field boundary detection
 */
public class ForceFieldManager implements Listener {
    // Constants
    private static final int UPDATE_FREQUENCY = 2; // ticks
    private static final int FORCEFIELD_RADIUS = 10;
    private static final int MAX_FIELD_HEIGHT = 5;
    private static final List<BlockFace> ADJACENT_FACES = Arrays.asList(
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    );

    // Singleton instance
    private static ForceFieldManager instance;

    // Task that updates force fields
    private BukkitTask updateTask;

    // Map of player UUID to the set of block locations currently showing force field blocks
    private final Map<UUID, Set<Location>> activeForceFields = new ConcurrentHashMap<>();

    // Dependencies
    private final WorldGuardManager worldGuardManager;
    private final YakPlayerManager playerManager;
    private final ProtocolManager protocolManager;

    /**
     * Get the singleton instance
     *
     * @return The ForceFieldManager instance
     */
    public static ForceFieldManager getInstance() {
        if (instance == null) {
            instance = new ForceFieldManager();
        }
        return instance;
    }

    /**
     * Constructor initializes dependencies
     */
    private ForceFieldManager() {
        this.worldGuardManager = WorldGuardManager.getInstance();
        this.playerManager = YakPlayerManager.getInstance();
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    /**
     * Initialize the force field system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        startUpdateTask();
        YakRealms.log("FIXED Force field mechanics have been enabled with proper combat tag integration.");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        // Clean up all force fields
        removeAllForceFields();
        YakRealms.log("FIXED Force field mechanics have been disabled.");
    }

    /**
     * Start the task that updates force fields
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> playersToUpdate = new HashSet<>();

                // Find players who need force field updates
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (shouldPlayerHaveForceField(player)) {
                        playersToUpdate.add(player.getUniqueId());
                    }
                    // Also update for players who already have force fields but shouldn't
                    else if (activeForceFields.containsKey(player.getUniqueId())) {
                        playersToUpdate.add(player.getUniqueId());
                    }
                }

                // Update force fields for those players
                for (UUID playerId : playersToUpdate) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        updatePlayerForceField(player);
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20, UPDATE_FREQUENCY);
    }

    /**
     * FIXED: Check if a player should have force fields displayed
     *
     * @param player The player to check
     * @return true if the player should see force fields
     */
    private boolean shouldPlayerHaveForceField(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            return false;
        }

        // FIXED: Show force fields for chaotic players OR combat tagged players
        return "CHAOTIC".equals(yakPlayer.getAlignment()) ||
                isPlayerCombatTagged(player);
    }

    /**
     * FIXED: Check if a player is combat tagged using AlignmentMechanics
     *
     * @param player The player to check
     * @return true if the player is combat tagged
     */
    private boolean isPlayerCombatTagged(Player player) {
        try {
            // Use AlignmentMechanics to check combat tag status
            return AlignmentMechanics.getInstance().isPlayerTagged(player);
        } catch (Exception e) {
            YakRealms.error("Error checking combat tag status for " + player.getName(), e);
            return false;
        }
    }

    /**
     * FIXED: Update the force field for a player (now public for external calls)
     *
     * @param player The player to update
     */
    public void updatePlayerForceField(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            UUID playerId = player.getUniqueId();

            // Check if player should have force fields
            boolean shouldHaveFields = shouldPlayerHaveForceField(player);

            if (shouldHaveFields) {
                // Get current force field blocks
                Set<Location> currentBlocks = findBoundaryBlocks(player);

                // Get previously shown blocks
                Set<Location> previousBlocks = activeForceFields.getOrDefault(playerId, new HashSet<>());

                // Determine blocks to add and remove
                Set<Location> blocksToRemove = new HashSet<>(previousBlocks);
                blocksToRemove.removeAll(currentBlocks);

                Set<Location> blocksToAdd = new HashSet<>(currentBlocks);
                blocksToAdd.removeAll(previousBlocks);

                // Apply changes
                for (Location location : blocksToRemove) {
                    Block block = location.getBlock();
                    sendBlockChange(player, location, block.getType(), block.getData());
                }

                for (Location location : blocksToAdd) {
                    // Send red stained glass block
                    sendStainedGlassBlockChange(player, location, (byte) 14); // 14 is red
                }

                // Update active blocks
                if (currentBlocks.isEmpty()) {
                    activeForceFields.remove(playerId);
                } else {
                    activeForceFields.put(playerId, currentBlocks);
                }
            } else {
                // Player shouldn't have force fields, remove them
                removePlayerForceField(player);
            }

        } catch (Exception e) {
            YakRealms.error("Error updating force field for " + player.getName(), e);
        }
    }

    /**
     * Send a block change packet using ProtocolLib
     * This approach works with all Minecraft versions
     *
     * @param player   The player to send to
     * @param location The block location
     * @param material The material to show
     * @param data     The block data value (legacy)
     */
    private void sendBlockChange(Player player, Location location, Material material, byte data) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
            packet.getBlockPositionModifier().write(0, new BlockPosition(
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            ));

            // Get WrappedBlockData for the material
            packet.getBlockData().write(0, WrappedBlockData.createData(material));

            // Send the packet
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            YakRealms.error("Failed to send block change packet to " + player.getName(), e);
        }
    }

    /**
     * Send a stained glass block change packet using ProtocolLib
     * This method handles both modern and legacy versions of Minecraft
     *
     * @param player    The player to send to
     * @param location  The block location
     * @param colorData The color data value (0-15)
     */
    private void sendStainedGlassBlockChange(Player player, Location location, byte colorData) {
        try {
            // Modern method using the block change packet with block data
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            // Set the position
            packet.getBlockPositionModifier().write(0, new BlockPosition(
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            ));

            // Use the correct approach based on Minecraft version
            // This is a workaround for Minecraft 1.13+ where legacy data values were removed
            Material material = Material.RED_STAINED_GLASS;
            try {
                // Try to get the stained glass material with the color
                // For legacy versions, we try to create the correct colored glass
                Class<?> materialClass = Material.class;
                String colorName;

                switch (colorData) {
                    case 0:
                        colorName = "WHITE";
                        break;
                    case 1:
                        colorName = "ORANGE";
                        break;
                    case 2:
                        colorName = "MAGENTA";
                        break;
                    case 3:
                        colorName = "LIGHT_BLUE";
                        break;
                    case 4:
                        colorName = "YELLOW";
                        break;
                    case 5:
                        colorName = "LIME";
                        break;
                    case 6:
                        colorName = "PINK";
                        break;
                    case 7:
                        colorName = "GRAY";
                        break;
                    case 8:
                        colorName = "LIGHT_GRAY";
                        break;
                    case 9:
                        colorName = "CYAN";
                        break;
                    case 10:
                        colorName = "PURPLE";
                        break;
                    case 11:
                        colorName = "BLUE";
                        break;
                    case 12:
                        colorName = "BROWN";
                        break;
                    case 13:
                        colorName = "GREEN";
                        break;
                    case 14:
                        colorName = "RED";
                        break;
                    case 15:
                        colorName = "BLACK";
                        break;
                    default:
                        colorName = "RED";
                        break;
                }

                try {
                    // Try to get the modern stained glass material by name
                    Material newMaterial = Material.valueOf(colorName + "_STAINED_GLASS");
                    if (newMaterial != null) {
                        material = newMaterial;
                    }
                } catch (Exception e) {
                    // Fall back to legacy approach
                    try {
                        Material legacyMaterial = Material.valueOf("STAINED_GLASS");
                        if (legacyMaterial != null) {
                            material = legacyMaterial;

                            // We'll handle the data value in the block data wrapper
                            WrappedBlockData blockData = WrappedBlockData.createData(material, colorData);
                            packet.getBlockData().write(0, blockData);
                            protocolManager.sendServerPacket(player, packet);
                            return;
                        }
                    } catch (Exception ex) {
                        // Fall back to default red stained glass
                    }
                }
            } catch (Exception e) {
                // Fallback for any errors - use RED_STAINED_GLASS
            }

            // Modern approach - use the correct colored glass material
            packet.getBlockData().write(0, WrappedBlockData.createData(material));
            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            YakRealms.error("Failed to send stained glass packet to " + player.getName(), e);
        }
    }

    /**
     * FIXED: Find blocks that should be shown as force field blocks for a player
     *
     * @param player The player to check
     * @return Set of block locations that should be shown as force field
     */
    private Set<Location> findBoundaryBlocks(Player player) {
        Set<Location> boundaryBlocks = new HashSet<>();

        if (!shouldPlayerHaveForceField(player)) {
            return boundaryBlocks;
        }

        Location playerLoc = player.getLocation();

        // Find boundary blocks in a radius around the player
        World world = player.getWorld();
        int playerY = playerLoc.getBlockY();
        int searchRadius = FORCEFIELD_RADIUS;

        // Optimize - only search in a box around the player
        for (int x = playerLoc.getBlockX() - searchRadius; x <= playerLoc.getBlockX() + searchRadius; x++) {
            for (int z = playerLoc.getBlockZ() - searchRadius; z <= playerLoc.getBlockZ() + searchRadius; z++) {
                // Skip blocks that are too far away (use square distance for efficiency)
                if (distanceSquared(x, z, playerLoc.getBlockX(), playerLoc.getBlockZ()) > searchRadius * searchRadius) {
                    continue;
                }

                // Check if this location is at a safe zone boundary
                Location checkLoc = new Location(world, x, playerY, z);
                if (isAtSafeZoneBoundary(checkLoc)) {
                    // Add blocks vertically to create a wall
                    for (int y = Math.max(0, playerY - 1); y <= playerY + MAX_FIELD_HEIGHT; y++) {
                        Location fieldBlock = new Location(world, x, y, z);
                        if (fieldBlock.getBlock().getType().isAir() ||
                                fieldBlock.getBlock().getType().isTransparent()) {
                            boundaryBlocks.add(fieldBlock);
                        }
                    }
                }
            }
        }

        return boundaryBlocks;
    }

    /**
     * Calculate square distance between two points (more efficient than distance)
     *
     * @param x1 First point x
     * @param z1 First point z
     * @param x2 Second point x
     * @param z2 Second point z
     * @return Square of the distance
     */
    private int distanceSquared(int x1, int z1, int x2, int z2) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        return dx * dx + dz * dz;
    }

    /**
     * Check if a location is at the boundary between safe and non-safe zones
     *
     * @param location The location to check
     * @return true if the location is at a boundary
     */
    private boolean isAtSafeZoneBoundary(Location location) {
        boolean locationSafe = worldGuardManager.isSafeZone(location);

        // Check adjacent blocks
        for (BlockFace face : ADJACENT_FACES) {
            Location adjacent = location.clone().add(
                    face.getModX(),
                    face.getModY(),
                    face.getModZ()
            );

            boolean adjacentSafe = worldGuardManager.isSafeZone(adjacent);

            // If one side is safe and the other isn't, we're at a boundary
            if (locationSafe != adjacentSafe) {
                return true;
            }
        }

        return false;
    }

    /**
     * FIXED: Remove force field blocks for a player
     *
     * @param player The player to update
     */
    public void removePlayerForceField(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Set<Location> blocks = activeForceFields.remove(playerId);
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        try {
            for (Location location : blocks) {
                Block block = location.getBlock();
                sendBlockChange(player, location, block.getType(), block.getData());
            }

            YakRealms.log("Removed force field for " + player.getName() + " (" + blocks.size() + " blocks)");
        } catch (Exception e) {
            YakRealms.error("Error removing force field for " + player.getName(), e);
        }
    }

    /**
     * Remove all force fields for all players
     */
    private void removeAllForceFields() {
        for (UUID playerId : new HashSet<>(activeForceFields.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removePlayerForceField(player);
            }
        }
        activeForceFields.clear();
    }

    /**
     * FIXED: Handle a player joining the server
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Schedule force field update after a short delay to ensure all systems are initialized
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (player.isOnline()) {
                updatePlayerForceField(player);
            }
        }, 3L);
    }

    /**
     * Handle a player leaving the server
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeForceFields.remove(event.getPlayer().getUniqueId());
    }

    /**
     * FIXED: Prevent players from walking through force fields
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return; // Not actually moving to a new block
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Set<Location> forceFieldBlocks = activeForceFields.get(playerId);

        if (forceFieldBlocks != null && !forceFieldBlocks.isEmpty()) {
            // Check if the player is trying to move into a force field block
            Location targetBlock = event.getTo().getBlock().getLocation();
            if (forceFieldBlocks.contains(targetBlock)) {
                event.setCancelled(true);

                // Provide feedback to the player
                player.sendMessage(ChatColor.RED + "You cannot pass through the force field barrier!");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 2.0f);
            }
        }
    }

    /**
     * Handle plugin disable to clean up force fields
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals(YakRealms.getInstance().getName())) {
            onDisable();
        }
    }

    /**
     * FIXED: Public API method to force an immediate update for a specific player
     * This is called when a player's combat state changes
     *
     * @param player The player whose force field should be updated
     */
    public void forceUpdatePlayerForceField(Player player) {
        if (player != null && player.isOnline()) {
            updatePlayerForceField(player);
        }
    }

    /**
     * FIXED: Check if a player currently has active force fields
     *
     * @param player The player to check
     * @return true if the player has active force fields
     */
    public boolean hasActiveForceFields(Player player) {
        if (player == null) {
            return false;
        }

        Set<Location> blocks = activeForceFields.get(player.getUniqueId());
        return blocks != null && !blocks.isEmpty();
    }

    /**
     * FIXED: Get the count of active force field blocks for a player
     *
     * @param player The player to check
     * @return The number of active force field blocks
     */
    public int getActiveForceFieldCount(Player player) {
        if (player == null) {
            return 0;
        }

        Set<Location> blocks = activeForceFields.get(player.getUniqueId());
        return blocks != null ? blocks.size() : 0;
    }

    /**
     * FIXED: Get debug information about the force field system
     *
     * @return A map of debug information
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("total_players_with_fields", activeForceFields.size());
        info.put("update_task_running", updateTask != null && !updateTask.isCancelled());
        info.put("total_field_blocks", activeForceFields.values().stream().mapToInt(Set::size).sum());

        Map<String, Integer> playerFieldCounts = new HashMap<>();
        for (Map.Entry<UUID, Set<Location>> entry : activeForceFields.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            String playerName = player != null ? player.getName() : entry.getKey().toString();
            playerFieldCounts.put(playerName, entry.getValue().size());
        }
        info.put("player_field_counts", playerFieldCounts);

        return info;
    }
}