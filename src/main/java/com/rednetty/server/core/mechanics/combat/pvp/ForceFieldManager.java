package com.rednetty.server.core.mechanics.combat.pvp;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.world.WorldGuardManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
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

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * ForceFieldManager with improved performance, error handling, and backwards compatibility
 *
 * Key Improvements:
 * - Fixed DataFixer hanging issue by removing deprecated getData() calls
 * - Enhanced backwards compatibility for MC 1.8+ through 1.21+
 * - Improved performance with optimized boundary detection
 * - Better error handling and logging
 * - Memory leak prevention
 * - Async-safe operations
 * - Enhanced combat tag integration
 */
public class ForceFieldManager implements Listener {

    // Version compatibility
    private static final String MC_VERSION = Bukkit.getVersion();
    private static final boolean IS_MODERN_MC = isModernMinecraft();
    private static final boolean HAS_BLOCK_DATA_API = hasBlockDataAPI();

    // Constants
    private static final int UPDATE_FREQUENCY = 2; // ticks
    private static final int FORCEFIELD_RADIUS = 10;
    private static final int MAX_FIELD_HEIGHT = 5;
    private static final int MAX_BOUNDARY_BLOCKS = 500; // Prevent memory issues
    private static final long PLAYER_JOIN_DELAY = 5L; // ticks

    // Boundary detection faces
    private static final List<BlockFace> ADJACENT_FACES = Arrays.asList(
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    );

    // Force field materials by color
    private static final Map<Integer, Material> STAINED_GLASS_MATERIALS = new HashMap<>();
    static {
        initializeStainedGlassMaterials();
    }

    // Singleton instance
    private static volatile ForceFieldManager instance;

    // Core components
    private BukkitTask updateTask;
    private final Map<UUID, Set<Location>> activeForceFields = new ConcurrentHashMap<>();
    private final Set<UUID> playersNeedingUpdate = new CopyOnWriteArraySet<>();

    // Dependencies
    private final WorldGuardManager worldGuardManager;
    private final YakPlayerManager playerManager;
    private final ProtocolManager protocolManager;

    // Performance tracking
    private long lastPerformanceCheck = 0;
    private int boundaryCalculations = 0;

    // Reflection cache for backwards compatibility
    private static Method getBlockDataMethod = null;
    private static Method getLegacyDataMethod = null;
    private static boolean reflectionInitialized = false;

    /**
     * Get the singleton instance with thread safety
     */
    public static ForceFieldManager getInstance() {
        if (instance == null) {
            synchronized (ForceFieldManager.class) {
                if (instance == null) {
                    instance = new ForceFieldManager();
                }
            }
        }
        return instance;
    }

    /**
     * constructor with better dependency management
     */
    private ForceFieldManager() {
        this.worldGuardManager = WorldGuardManager.getInstance();
        this.playerManager = YakPlayerManager.getInstance();
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        initializeReflection();
        YakRealms.log("ForceFieldManager initialized for MC version: " + MC_VERSION);
    }

    /**
     * Initialize the force field system with enhanced error handling
     */
    public void onEnable() {
        try {
            // Register events
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Start update task
            startUpdateTask();

            // Initialize existing players
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isOnline()) {
                        schedulePlayerUpdate(player);
                    }
                }
            }, 20L);

            YakRealms.log(" ForceFieldManager enabled successfully");

        } catch (Exception e) {
            YakRealms.error("Failed to enable ForceFieldManager", e);
        }
    }

    /**
     * cleanup on disable
     */
    public void onDisable() {
        try {
            // Cancel update task
            if (updateTask != null && !updateTask.isCancelled()) {
                updateTask.cancel();
                updateTask = null;
            }

            // Clean up all force fields
            removeAllForceFields();

            // Clear tracking sets
            playersNeedingUpdate.clear();

            YakRealms.log("ForceFieldManager disabled cleanly");

        } catch (Exception e) {
            YakRealms.error("Error during ForceFieldManager disable", e);
        }
    }

    /**
     * update task with performance monitoring
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.currentTimeMillis();

                    // Process players needing updates
                    Set<UUID> playersToUpdate = new HashSet<>(playersNeedingUpdate);
                    playersNeedingUpdate.clear();

                    // Add players who might need updates
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        UUID playerId = player.getUniqueId();
                        boolean shouldHave = shouldPlayerHaveForceField(player);
                        boolean currentlyHas = activeForceFields.containsKey(playerId);

                        if (shouldHave != currentlyHas || shouldHave) {
                            playersToUpdate.add(playerId);
                        }
                    }

                    // Update force fields
                    int updateCount = 0;
                    for (UUID playerId : playersToUpdate) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            updatePlayerForceFieldSafe(player);
                            updateCount++;
                        }
                    }

                    // Performance monitoring
                    long processingTime = System.currentTimeMillis() - startTime;
                    if (processingTime > 50) { // More than 50ms
                        YakRealms.log("ForceField update took " + processingTime + "ms for " + updateCount + " players");
                    }

                    // Periodic performance stats
                    if (System.currentTimeMillis() - lastPerformanceCheck > 60000) { // Every minute
                        logPerformanceStats();
                        lastPerformanceCheck = System.currentTimeMillis();
                    }

                } catch (Exception e) {
                    YakRealms.error("Error in ForceField update task", e);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20, UPDATE_FREQUENCY);
    }

    /**
     * force field condition checking
     */
    private boolean shouldPlayerHaveForceField(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                return false;
            }

            // Check alignment and combat status
            boolean isChaotic = "CHAOTIC".equals(yakPlayer.getAlignment());
            boolean isCombatTagged = isPlayerCombatTagged(player);

            return isChaotic || isCombatTagged;

        } catch (Exception e) {
            YakRealms.error("Error checking force field condition for " + player.getName(), e);
            return false;
        }
    }

    /**
     * combat tag checking with fallback
     */
    private boolean isPlayerCombatTagged(Player player) {
        try {
            AlignmentMechanics alignmentMechanics = AlignmentMechanics.getInstance();
            if (alignmentMechanics != null) {
                return alignmentMechanics.isPlayerTagged(player);
            }
        } catch (Exception e) {
            YakRealms.error("Error checking combat tag for " + player.getName(), e);
        }
        return false;
    }

    /**
     * Thread-safe wrapper for updating player force fields
     */
    private void updatePlayerForceFieldSafe(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            updatePlayerForceField(player);
        } catch (Exception e) {
            YakRealms.error("Error updating force field for " + player.getName(), e);
            // Remove player from active tracking to prevent repeated errors
            activeForceFields.remove(player.getUniqueId());
        }
    }

    /**
     * force field update method without getData() calls
     */
    public void updatePlayerForceField(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        boolean shouldHaveFields = shouldPlayerHaveForceField(player);

        if (shouldHaveFields) {
            // Calculate boundary blocks
            Set<Location> currentBlocks = findBoundaryBlocks(player);

            // Limit block count to prevent performance issues
            if (currentBlocks.size() > MAX_BOUNDARY_BLOCKS) {
                YakRealms.log("Limiting force field blocks for " + player.getName() +
                        " from " + currentBlocks.size() + " to " + MAX_BOUNDARY_BLOCKS);
                currentBlocks = limitBlockSet(currentBlocks, MAX_BOUNDARY_BLOCKS);
            }

            Set<Location> previousBlocks = activeForceFields.getOrDefault(playerId, new HashSet<>());

            // Calculate differences
            Set<Location> blocksToRemove = new HashSet<>(previousBlocks);
            blocksToRemove.removeAll(currentBlocks);

            Set<Location> blocksToAdd = new HashSet<>(currentBlocks);
            blocksToAdd.removeAll(previousBlocks);

            // Apply changes
            for (Location location : blocksToRemove) {
                sendOriginalBlockChange(player, location);
            }

            for (Location location : blocksToAdd) {
                sendForceFieldBlockChange(player, location);
            }

            // Update tracking
            if (currentBlocks.isEmpty()) {
                activeForceFields.remove(playerId);
            } else {
                activeForceFields.put(playerId, currentBlocks);
            }

        } else {
            // Remove force fields
            removePlayerForceField(player);
        }
    }

    /**
     * Send original block without using deprecated getData()
     */
    private void sendOriginalBlockChange(Player player, Location location) {
        try {
            Block block = location.getBlock();

            if (HAS_BLOCK_DATA_API && IS_MODERN_MC) {
                // Modern approach using BlockData API
                sendModernBlockChange(player, location, block.getBlockData());
            } else {
                // Legacy approach with safe fallback
                sendLegacyBlockChange(player, location, block.getType());
            }

        } catch (Exception e) {
            YakRealms.error("Failed to send original block change for " + player.getName(), e);
        }
    }

    /**
     * Send force field block (red stained glass)
     */
    private void sendForceFieldBlockChange(Player player, Location location) {
        try {
            Material glassMaterial = getRedStainedGlass();

            if (HAS_BLOCK_DATA_API && IS_MODERN_MC) {
                BlockData blockData = glassMaterial.createBlockData();
                sendModernBlockChange(player, location, blockData);
            } else {
                sendLegacyBlockChange(player, location, glassMaterial);
            }

        } catch (Exception e) {
            YakRealms.error("Failed to send force field block for " + player.getName(), e);
        }
    }

    /**
     * Modern block change using BlockData API (MC 1.13+)
     */
    private void sendModernBlockChange(Player player, Location location, BlockData blockData) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            packet.getBlockPositionModifier().write(0, new BlockPosition(
                    location.getBlockX(), location.getBlockY(), location.getBlockZ()
            ));

            packet.getBlockData().write(0, WrappedBlockData.createData(blockData));
            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            YakRealms.error("Failed to send modern block change packet", e);
        }
    }

    /**
     * Legacy block change for older MC versions
     */
    private void sendLegacyBlockChange(Player player, Location location, Material material) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            packet.getBlockPositionModifier().write(0, new BlockPosition(
                    location.getBlockX(), location.getBlockY(), location.getBlockZ()
            ));

            packet.getBlockData().write(0, WrappedBlockData.createData(material));
            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            YakRealms.error("Failed to send legacy block change packet", e);
        }
    }

    /**
     * boundary detection with performance optimizations
     */
    private Set<Location> findBoundaryBlocks(Player player) {
        Set<Location> boundaryBlocks = new HashSet<>();

        if (!shouldPlayerHaveForceField(player)) {
            return boundaryBlocks;
        }

        boundaryCalculations++;
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        int playerY = playerLoc.getBlockY();
        int searchRadius = FORCEFIELD_RADIUS;

        // Optimized boundary detection using square iteration
        for (int x = playerLoc.getBlockX() - searchRadius; x <= playerLoc.getBlockX() + searchRadius; x++) {
            for (int z = playerLoc.getBlockZ() - searchRadius; z <= playerLoc.getBlockZ() + searchRadius; z++) {

                // Quick distance check using square distance
                if (distanceSquared(x, z, playerLoc.getBlockX(), playerLoc.getBlockZ()) > searchRadius * searchRadius) {
                    continue;
                }

                Location checkLoc = new Location(world, x, playerY, z);

                if (isAtSafeZoneBoundary(checkLoc)) {
                    // Add vertical blocks for wall effect
                    int minY = Math.max(world.getMinHeight(), playerY - 1);
                    int maxY = Math.min(world.getMaxHeight() - 1, playerY + MAX_FIELD_HEIGHT);

                    for (int y = minY; y <= maxY; y++) {
                        Location fieldBlock = new Location(world, x, y, z);
                        Block block = fieldBlock.getBlock();

                        if (isBlockTransparent(block)) {
                            boundaryBlocks.add(fieldBlock);

                            // Prevent excessive block counts
                            if (boundaryBlocks.size() >= MAX_BOUNDARY_BLOCKS) {
                                return boundaryBlocks;
                            }
                        }
                    }
                }
            }
        }

        return boundaryBlocks;
    }

    /**
     * transparency check with version compatibility
     */
    private boolean isBlockTransparent(Block block) {
        try {
            Material material = block.getType();

            // Quick checks for common transparent blocks
            if (material == Material.AIR) return true;

            // Version-dependent transparency check
            if (IS_MODERN_MC) {
                try {
                    return material.isTransparent() || !material.isOccluding();
                } catch (Exception e) {
                    // Fallback for compatibility issues
                    return isLegacyTransparent(material);
                }
            } else {
                return isLegacyTransparent(material);
            }

        } catch (Exception e) {
            return true; // Default to transparent on error
        }
    }

    /**
     * Legacy transparency check for older MC versions
     */
    private boolean isLegacyTransparent(Material material) {
        // Common transparent materials across versions
        return material == Material.AIR ||
                material.name().contains("GLASS") ||
                material.name().contains("LEAVES") ||
                material.name().contains("WATER") ||
                material.name().contains("LAVA");
    }

    /**
     * safe zone boundary detection
     */
    private boolean isAtSafeZoneBoundary(Location location) {
        try {
            boolean locationSafe = worldGuardManager.isSafeZone(location);

            // Check adjacent blocks for boundary
            for (BlockFace face : ADJACENT_FACES) {
                Location adjacent = location.clone().add(
                        face.getModX(), face.getModY(), face.getModZ()
                );

                boolean adjacentSafe = worldGuardManager.isSafeZone(adjacent);

                if (locationSafe != adjacentSafe) {
                    return true;
                }
            }

        } catch (Exception e) {
            YakRealms.error("Error checking safe zone boundary", e);
        }

        return false;
    }

    /**
     * player force field removal
     */
    public void removePlayerForceField(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        Set<Location> blocks = activeForceFields.remove(playerId);

        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        try {
            for (Location location : blocks) {
                if (player.isOnline()) {
                    sendOriginalBlockChange(player, location);
                }
            }

        } catch (Exception e) {
            YakRealms.error("Error removing force field for " + player.getName(), e);
        }
    }

    /**
     * Schedule a player for force field update
     */
    public void schedulePlayerUpdate(Player player) {
        if (player != null && player.isOnline()) {
            playersNeedingUpdate.add(player.getUniqueId());
        }
    }

    /**
     * player movement handling
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null ||
                event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Player player = event.getPlayer();
        Set<Location> forceFieldBlocks = activeForceFields.get(player.getUniqueId());

        if (forceFieldBlocks != null && !forceFieldBlocks.isEmpty()) {
            Location targetBlock = event.getTo().getBlock().getLocation();

            if (forceFieldBlocks.contains(targetBlock)) {
                event.setCancelled(true);

                // Enhanced feedback
                player.sendMessage(ChatColor.RED + "⚡ You cannot pass through the force field barrier!");

                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 2.0f);
                } catch (Exception e) {
                    // Fallback for missing sound
                    try {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                    } catch (Exception ex) {
                        // Silent fallback
                    }
                }
            }
        }
    }

    /**
     * player join handling
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delayed update to ensure all systems are ready
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (player.isOnline()) {
                schedulePlayerUpdate(player);
            }
        }, PLAYER_JOIN_DELAY);
    }

    /**
     * Player quit handling with cleanup
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        activeForceFields.remove(playerId);
        playersNeedingUpdate.remove(playerId);
    }

    /**
     * Plugin disable handling
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (YakRealms.getInstance().getName().equals(event.getPlugin().getName())) {
            onDisable();
        }
    }

    /**
     * Public API methods
     */
    public void forceUpdatePlayerForceField(Player player) {
        schedulePlayerUpdate(player);
    }

    public boolean hasActiveForceFields(Player player) {
        if (player == null) return false;
        Set<Location> blocks = activeForceFields.get(player.getUniqueId());
        return blocks != null && !blocks.isEmpty();
    }

    public int getActiveForceFieldCount(Player player) {
        if (player == null) return 0;
        Set<Location> blocks = activeForceFields.get(player.getUniqueId());
        return blocks != null ? blocks.size() : 0;
    }

    /**
     * debug information
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("total_players_with_fields", activeForceFields.size());
        info.put("update_task_running", updateTask != null && !updateTask.isCancelled());
        info.put("total_field_blocks", activeForceFields.values().stream().mapToInt(Set::size).sum());
        info.put("boundary_calculations", boundaryCalculations);
        info.put("minecraft_version", MC_VERSION);
        info.put("is_modern_mc", IS_MODERN_MC);
        info.put("has_block_data_api", HAS_BLOCK_DATA_API);
        info.put("players_needing_update", playersNeedingUpdate.size());

        Map<String, Integer> playerFieldCounts = new HashMap<>();
        for (Map.Entry<UUID, Set<Location>> entry : activeForceFields.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            String playerName = player != null ? player.getName() : entry.getKey().toString();
            playerFieldCounts.put(playerName, entry.getValue().size());
        }
        info.put("player_field_counts", playerFieldCounts);

        return info;
    }

    // Utility methods

    private void removeAllForceFields() {
        for (UUID playerId : new HashSet<>(activeForceFields.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removePlayerForceField(player);
            }
        }
        activeForceFields.clear();
    }

    private Set<Location> limitBlockSet(Set<Location> blocks, int maxSize) {
        if (blocks.size() <= maxSize) return blocks;

        Set<Location> limited = new HashSet<>();
        int count = 0;
        for (Location location : blocks) {
            limited.add(location);
            if (++count >= maxSize) break;
        }
        return limited;
    }

    private int distanceSquared(int x1, int z1, int x2, int z2) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        return dx * dx + dz * dz;
    }

    private void logPerformanceStats() {
        YakRealms.log(String.format(
                "ForceField Performance - Players: %d, Total Blocks: %d, Boundary Calcs: %d",
                activeForceFields.size(),
                activeForceFields.values().stream().mapToInt(Set::size).sum(),
                boundaryCalculations
        ));
        boundaryCalculations = 0; // Reset counter
    }

    // Version compatibility methods

    private static boolean isModernMinecraft() {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = version.split("\\.");
            if (parts.length >= 4) {
                String versionString = parts[3]; // e.g., "v1_21_R1"
                if (versionString.startsWith("v1_")) {
                    String[] versionParts = versionString.substring(3).split("_");
                    if (versionParts.length >= 1) {
                        int majorVersion = Integer.parseInt(versionParts[0]);
                        return majorVersion >= 13; // 1.13+ is considered modern
                    }
                }
            }
        } catch (Exception e) {
            // Fallback detection
        }

        // Fallback: check for BlockData class existence
        try {
            Class.forName("org.bukkit.block.data.BlockData");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean hasBlockDataAPI() {
        try {
            Class.forName("org.bukkit.block.data.BlockData");
            Block.class.getMethod("getBlockData");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void initializeStainedGlassMaterials() {
        // Initialize stained glass materials for different versions
        String[] colors = {
                "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
                "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
        };

        for (int i = 0; i < colors.length; i++) {
            try {
                // Modern naming
                Material material = Material.valueOf(colors[i] + "_STAINED_GLASS");
                STAINED_GLASS_MATERIALS.put(i, material);
            } catch (IllegalArgumentException e) {
                // Legacy fallback
                try {
                    Material legacy = Material.valueOf("STAINED_GLASS");
                    STAINED_GLASS_MATERIALS.put(i, legacy);
                } catch (IllegalArgumentException ex) {
                    // Ultimate fallback
                    STAINED_GLASS_MATERIALS.put(i, Material.GLASS);
                }
            }
        }
    }

    private Material getRedStainedGlass() {
        return STAINED_GLASS_MATERIALS.getOrDefault(14, Material.GLASS);
    }

    private void initializeReflection() {
        if (reflectionInitialized) return;

        try {
            // Try to get modern BlockData method
            if (HAS_BLOCK_DATA_API) {
                getBlockDataMethod = Block.class.getMethod("getBlockData");
            } else {
                // Try to get legacy data method safely
                try {
                    getLegacyDataMethod = Block.class.getMethod("getData");
                } catch (NoSuchMethodException e) {
                    // Method not available, will use fallback
                }
            }
            reflectionInitialized = true;
        } catch (Exception e) {
            YakRealms.log("Could not initialize reflection for block data: " + e.getMessage());
        }
    }
}