package com.rednetty.server.mechanics.dungeons.instance;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.dungeons.bosses.DungeonBoss;
import com.rednetty.server.mechanics.dungeons.config.DungeonTemplate;
import com.rednetty.server.mechanics.dungeons.progress.DungeonProgress;
import com.rednetty.server.mechanics.dungeons.rewards.DungeonRewards;
import com.rednetty.server.mechanics.dungeons.rooms.DungeonRoom;
import com.rednetty.server.mechanics.party.PartyMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ENHANCED: Complete Dungeon Instance Management
 *
 * Represents an active dungeon with players, managing the entire lifecycle
 * from initialization to completion. Handles player management, room progression,
 * boss encounters, objectives, and rewards.
 *
 * Features:
 * - Complete dungeon lifecycle management
 * - Player tracking and management
 * - Room-based progression system
 * - Boss encounter management
 * - Objective tracking and completion
 * - Real-time progress updates
 * - Reward distribution
 * - Instance world management
 * - Event-driven architecture
 * - Error handling and recovery
 */
public class DungeonInstance {

    // ================ CORE PROPERTIES ================

    private final UUID id;
    private final DungeonTemplate template;
    private final long startTime;
    private final Logger logger;

    // ================ INSTANCE STATE ================

    private DungeonState state;
    private EndReason endReason;
    private String failureMessage;
    private long endTime = 0;

    // ================ PLAYER MANAGEMENT ================

    private final Set<UUID> playerIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DungeonPlayerData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerDeathLocations = new ConcurrentHashMap<>();
    private final Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();

    // ================ DUNGEON COMPONENTS ================

    private InstanceWorld instanceWorld;
    private final Map<String, DungeonRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, DungeonBoss> bosses = new ConcurrentHashMap<>();
    private DungeonProgress progress;
    private DungeonRewards rewards;

    // ================ RUNTIME DATA ================

    private String currentRoomId;
    private final Set<String> completedRooms = ConcurrentHashMap.newKeySet();
    private final Set<String> defeatedBosses = ConcurrentHashMap.newKeySet();
    private final Map<String, Object> sessionData = new ConcurrentHashMap<>();

    // ================ TASKS ================

    private BukkitTask progressTask;
    private BukkitTask statusTask;

    // ================ ENUMS ================

    /**
     * Dungeon state enumeration
     */
    public enum DungeonState {
        INITIALIZING,
        WAITING_FOR_PLAYERS,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ENDED
    }

    /**
     * End reason enumeration
     */
    public enum EndReason {
        COMPLETED,
        FAILED,
        ABANDONED,
        TIMEOUT,
        ERROR,
        SHUTDOWN
    }

    /**
     * Player data within dungeon
     */
    public static class DungeonPlayerData {
        private final UUID playerId;
        private final long joinTime;
        private int deaths = 0;
        private long totalDamageDealt = 0;
        private long totalDamageTaken = 0;
        private int mobsKilled = 0;
        private final Map<String, Object> customData = new HashMap<>();
        private Location lastSafeLocation;
        private boolean isReady = true;

        public DungeonPlayerData(UUID playerId) {
            this.playerId = playerId;
            this.joinTime = System.currentTimeMillis();
        }

        // Getters and setters
        public UUID getPlayerId() { return playerId; }
        public long getJoinTime() { return joinTime; }
        public int getDeaths() { return deaths; }
        public void incrementDeaths() { this.deaths++; }
        public long getTotalDamageDealt() { return totalDamageDealt; }
        public void addDamageDealt(long damage) { this.totalDamageDealt += damage; }
        public long getTotalDamageTaken() { return totalDamageTaken; }
        public void addDamageTaken(long damage) { this.totalDamageTaken += damage; }
        public int getMobsKilled() { return mobsKilled; }
        public void incrementMobsKilled() { this.mobsKilled++; }
        public Map<String, Object> getCustomData() { return new HashMap<>(customData); }
        public void setCustomData(String key, Object value) { customData.put(key, value); }
        public Object getCustomData(String key) { return customData.get(key); }
        public Location getLastSafeLocation() { return lastSafeLocation; }
        public void setLastSafeLocation(Location location) { this.lastSafeLocation = location; }
        public boolean isReady() { return isReady; }
        public void setReady(boolean ready) { this.isReady = ready; }

        public long getTimeInDungeon() {
            return System.currentTimeMillis() - joinTime;
        }
    }

    // ================ CONSTRUCTOR ================

    /**
     * Create a new dungeon instance
     */
    public DungeonInstance(DungeonTemplate template, List<Player> players) {
        this.id = UUID.randomUUID();
        this.template = template;
        this.startTime = System.currentTimeMillis();
        this.logger = YakRealms.getInstance().getLogger();
        this.state = DungeonState.INITIALIZING;

        // Initialize player data
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            playerIds.add(playerId);
            playerData.put(playerId, new DungeonPlayerData(playerId));
        }

        if (isDebugMode()) {
            logger.info("§6[DungeonInstance] §7Created instance " + id.toString().substring(0, 8) +
                    " for template " + template.getId() + " with " + players.size() + " players");
        }
    }

    // ================ INITIALIZATION ================

    /**
     * Initialize the dungeon instance
     */
    public boolean initialize() {
        try {
            setState(DungeonState.INITIALIZING);

            // Create instance world
            instanceWorld = InstanceManager.getInstance().createInstance(template, id);
            if (instanceWorld == null) {
                setFailureState("Failed to create instance world");
                return false;
            }

            // Initialize rooms
            if (!initializeRooms()) {
                setFailureState("Failed to initialize rooms");
                return false;
            }

            // Initialize bosses
            if (!initializeBosses()) {
                setFailureState("Failed to initialize bosses");
                return false;
            }

            // Initialize progress tracking
            progress = new DungeonProgress(this);
            if (!progress.initialize()) {
                setFailureState("Failed to initialize progress tracking");
                return false;
            }

            // Initialize rewards
            rewards = new DungeonRewards(this);
            if (!rewards.initialize()) {
                setFailureState("Failed to initialize rewards");
                return false;
            }

            // Teleport players to dungeon
            if (!teleportPlayersToStart()) {
                setFailureState("Failed to teleport players to dungeon");
                return false;
            }

            // Start tasks
            startTasks();

            setState(DungeonState.IN_PROGRESS);

            // Set starting room
            currentRoomId = findStartingRoom();
            if (currentRoomId != null) {
                DungeonRoom startRoom = rooms.get(currentRoomId);
                if (startRoom != null) {
                    startRoom.onPlayersEnter(getOnlinePlayers());
                }
            }

            // Send welcome message
            broadcast(ChatColor.GOLD + "=== " + template.getDisplayName() + " ===");
            broadcast(ChatColor.YELLOW + template.getDescription());
            broadcast(ChatColor.GREEN + "Dungeon started! Good luck adventurers!");

            if (isDebugMode()) {
                logger.info("§a[DungeonInstance] §7Successfully initialized instance " + id.toString().substring(0, 8));
            }

            return true;

        } catch (Exception e) {
            logger.severe("§c[DungeonInstance] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            setFailureState("Initialization error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initialize all rooms from template
     */
    private boolean initializeRooms() {
        try {
            for (DungeonTemplate.RoomDefinition roomDef : template.getRooms().values()) {
                DungeonRoom room = new DungeonRoom(this, roomDef);
                if (!room.initialize()) {
                    logger.warning("§c[DungeonInstance] Failed to initialize room: " + roomDef.getId());
                    return false;
                }
                rooms.put(roomDef.getId(), room);
            }

            if (isDebugMode()) {
                logger.info("§a[DungeonInstance] §7Initialized " + rooms.size() + " rooms");
            }

            return true;
        } catch (Exception e) {
            logger.severe("§c[DungeonInstance] Room initialization error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initialize all bosses from template
     */
    private boolean initializeBosses() {
        try {
            for (DungeonTemplate.BossDefinition bossDef : template.getBosses().values()) {
                DungeonBoss boss = new DungeonBoss(this, bossDef);
                if (!boss.initialize()) {
                    logger.warning("§c[DungeonInstance] Failed to initialize boss: " + bossDef.getId());
                    return false;
                }
                bosses.put(bossDef.getId(), boss);
            }

            if (isDebugMode()) {
                logger.info("§a[DungeonInstance] §7Initialized " + bosses.size() + " bosses");
            }

            return true;
        } catch (Exception e) {
            logger.severe("§c[DungeonInstance] Boss initialization error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Teleport all players to the dungeon start
     */
    private boolean teleportPlayersToStart() {
        try {
            Location spawnLoc = getSpawnLocation();
            if (spawnLoc == null) {
                return false;
            }

            for (Player player : getOnlinePlayers()) {
                // Store original location for restoration
                DungeonPlayerData data = playerData.get(player.getUniqueId());
                if (data != null) {
                    data.setLastSafeLocation(player.getLocation());
                }

                // Teleport to dungeon
                player.teleport(spawnLoc);

                // Apply dungeon effects
                applyDungeonEffects(player);
            }

            return true;
        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Error teleporting players: " + e.getMessage());
            return false;
        }
    }

    /**
     * Apply dungeon-specific effects to a player
     */
    private void applyDungeonEffects(Player player) {
        // Clear existing effects if needed
        // Apply any dungeon-specific potion effects, permissions, etc.

        // Example: Apply night vision in dark dungeons
        if (template.getCustomProperty("dark_dungeon", Boolean.class, false)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false));
        }
    }

    /**
     * Start processing tasks
     */
    private void startTasks() {
        // Progress monitoring task
        progressTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!isValid()) {
                        cancel();
                        return;
                    }

                    processProgress();
                } catch (Exception e) {
                    logger.warning("§c[DungeonInstance] Progress task error: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Every second

        // Status update task
        statusTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!isValid()) {
                        cancel();
                        return;
                    }

                    updatePlayerStatuses();
                } catch (Exception e) {
                    logger.warning("§c[DungeonInstance] Status task error: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 100L, 100L); // Every 5 seconds
    }

    // ================ MAIN PROCESSING ================

    /**
     * Main tick processing method
     */
    public void tick() {
        if (!isValid() || state != DungeonState.IN_PROGRESS) {
            return;
        }

        try {
            // Process rooms
            for (DungeonRoom room : rooms.values()) {
                room.tick();
            }

            // Process bosses
            for (DungeonBoss boss : bosses.values()) {
                if (boss.isActive()) {
                    boss.tick();
                }
            }

            // Process progress
            if (progress != null) {
                progress.tick();
            }

            // Check completion conditions
            checkCompletion();

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Tick error: " + e.getMessage());
            if (isDebugMode()) e.printStackTrace();
        }
    }

    /**
     * Process dungeon progress
     */
    private void processProgress() {
        if (progress == null || state != DungeonState.IN_PROGRESS) {
            return;
        }

        try {
            progress.update();

            // Check if all required objectives are complete
            if (progress.areAllRequiredObjectivesComplete()) {
                complete();
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Progress processing error: " + e.getMessage());
        }
    }

    /**
     * Update player statuses
     */
    private void updatePlayerStatuses() {
        for (Player player : getOnlinePlayers()) {
            updatePlayerStatus(player);
        }
    }

    /**
     * Update individual player status
     */
    private void updatePlayerStatus(Player player) {
        try {
            UUID playerId = player.getUniqueId();
            DungeonPlayerData data = playerData.get(playerId);

            if (data == null) return;

            // Update player's current location as safe location if not in combat
            if (!isPlayerInCombat(player)) {
                data.setLastSafeLocation(player.getLocation());
            }

            // Send progress updates via action bar
            if (progress != null) {
                String progressText = progress.getProgressText();
                if (progressText != null && !progressText.isEmpty()) {
                    player.sendActionBar(progressText);
                }
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Error updating player status: " + e.getMessage());
        }
    }

    /**
     * Check completion conditions
     */
    private void checkCompletion() {
        if (state != DungeonState.IN_PROGRESS) {
            return;
        }

        // Check if all players are dead
        if (areAllPlayersDead()) {
            fail("All players have died");
            return;
        }

        // Check if no players remain
        if (getOnlinePlayers().isEmpty()) {
            fail("No players remaining in dungeon");
            return;
        }

        // Let progress system handle objective completion
    }

    // ================ PLAYER MANAGEMENT ================

    /**
     * Add a player to the dungeon
     */
    public boolean addPlayer(Player player) {
        if (player == null || state != DungeonState.IN_PROGRESS) {
            return false;
        }

        UUID playerId = player.getUniqueId();

        if (playerIds.contains(playerId)) {
            return true; // Already in dungeon
        }

        if (playerIds.size() >= template.getMaxPlayers()) {
            return false; // Dungeon full
        }

        try {
            playerIds.add(playerId);
            playerData.put(playerId, new DungeonPlayerData(playerId));

            // Teleport to current room or spawn
            Location teleportLoc = getCurrentRoomLocation();
            if (teleportLoc == null) {
                teleportLoc = getSpawnLocation();
            }

            if (teleportLoc != null) {
                player.teleport(teleportLoc);
                applyDungeonEffects(player);
            }

            broadcast(ChatColor.GREEN + player.getName() + " has joined the dungeon!");

            if (isDebugMode()) {
                logger.info("§a[DungeonInstance] §7Player " + player.getName() + " added to dungeon");
            }

            return true;

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Error adding player: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove a player from the dungeon
     */
    public boolean removePlayer(UUID playerId) {
        if (!playerIds.contains(playerId)) {
            return false;
        }

        try {
            playerIds.remove(playerId);
            DungeonPlayerData data = playerData.remove(playerId);
            deadPlayers.remove(playerId);
            playerDeathLocations.remove(playerId);

            // Teleport player out if online
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                evacuatePlayer(player, data);
            }

            if (player != null) {
                broadcast(ChatColor.YELLOW + player.getName() + " has left the dungeon!");
            }

            if (isDebugMode()) {
                logger.info("§6[DungeonInstance] §7Player " + playerId + " removed from dungeon");
            }

            return true;

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Error removing player: " + e.getMessage());
            return false;
        }
    }

    /**
     * Evacuate a player from the dungeon
     */
    private void evacuatePlayer(Player player, DungeonPlayerData data) {
        try {
            // Remove dungeon effects
            removeDungeonEffects(player);

            // Teleport to safe location
            Location safeLocation = data != null ? data.getLastSafeLocation() : null;
            if (safeLocation == null) {
                safeLocation = player.getWorld().getSpawnLocation();
            }

            player.teleport(safeLocation);
            player.sendMessage(ChatColor.GREEN + "You have been safely removed from the dungeon.");

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Error evacuating player: " + e.getMessage());
        }
    }

    /**
     * Remove dungeon effects from a player
     */
    private void removeDungeonEffects(Player player) {
        // Remove any dungeon-specific effects
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
        // Remove other effects as needed
    }

    /**
     * Evacuate all players from the dungeon
     */
    public void evacuateAllPlayers() {
        for (Player player : getOnlinePlayers()) {
            DungeonPlayerData data = playerData.get(player.getUniqueId());
            evacuatePlayer(player, data);
        }
    }

    /**
     * Handle player death
     */
    public void handlePlayerDeath(Player player) {
        UUID playerId = player.getUniqueId();
        DungeonPlayerData data = playerData.get(playerId);

        if (data == null) return;

        try {
            data.incrementDeaths();
            deadPlayers.add(playerId);
            playerDeathLocations.put(playerId, player.getLocation());

            // Check max deaths setting
            if (template.getSettings().getMaxDeaths() > 0 &&
                    data.getDeaths() >= template.getSettings().getMaxDeaths()) {

                removePlayer(playerId);
                player.sendMessage(ChatColor.RED + "You have exceeded the maximum death limit for this dungeon.");
                return;
            }

            // Handle respawning based on settings
            if (template.getSettings().isAllowRespawning()) {
                schedulePlayerRespawn(player);
            } else {
                broadcast(ChatColor.RED + player.getName() + " has fallen! They cannot be revived in this dungeon.");
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Error handling player death: " + e.getMessage());
        }
    }

    /**
     * Schedule player respawn
     */
    private void schedulePlayerRespawn(Player player) {
        long gracePeriod = template.getSettings().getGracePeriod();

        if (gracePeriod > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isValid() && deadPlayers.contains(player.getUniqueId())) {
                        respawnPlayer(player);
                    }
                }
            }.runTaskLater(YakRealms.getInstance(), gracePeriod / 50); // Convert to ticks
        } else {
            respawnPlayer(player);
        }
    }

    /**
     * Respawn a dead player
     */
    private void respawnPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        if (!deadPlayers.contains(playerId)) {
            return;
        }

        try {
            deadPlayers.remove(playerId);
            playerDeathLocations.remove(playerId);

            // Teleport to safe location
            DungeonPlayerData data = playerData.get(playerId);
            Location respawnLoc = data != null ? data.getLastSafeLocation() : getSpawnLocation();

            if (respawnLoc != null) {
                player.teleport(respawnLoc);
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20);

                broadcast(ChatColor.GREEN + player.getName() + " has been revived!");
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Error respawning player: " + e.getMessage());
        }
    }

    // ================ ROOM MANAGEMENT ================

    /**
     * Handle room completion
     */
    public void onRoomComplete(String roomId) {
        if (completedRooms.contains(roomId)) {
            return; // Already completed
        }

        completedRooms.add(roomId);

        DungeonRoom room = rooms.get(roomId);
        if (room != null) {
            broadcast(ChatColor.GREEN + "Room completed: " + room.getDisplayName());

            // Update progress
            if (progress != null) {
                progress.onRoomComplete(roomId);
            }
        }

        if (isDebugMode()) {
            logger.info("§a[DungeonInstance] §7Room completed: " + roomId);
        }
    }

    /**
     * Handle boss defeat
     */
    public void onBossDefeated(String bossId) {
        if (defeatedBosses.contains(bossId)) {
            return; // Already defeated
        }

        defeatedBosses.add(bossId);

        DungeonBoss boss = bosses.get(bossId);
        if (boss != null) {
            broadcast(ChatColor.GOLD + "Boss defeated: " + boss.getDisplayName() + "!");

            // Update progress
            if (progress != null) {
                progress.onBossDefeated(bossId);
            }

            // Handle boss room completion
            String roomId = boss.getRoomId();
            if (roomId != null) {
                onRoomComplete(roomId);
            }
        }

        if (isDebugMode()) {
            logger.info("§a[DungeonInstance] §7Boss defeated: " + bossId);
        }
    }

    // ================ COMPLETION AND ENDING ================

    /**
     * Complete the dungeon successfully
     */
    public void complete() {
        if (state != DungeonState.IN_PROGRESS) {
            return;
        }

        try {
            setState(DungeonState.COMPLETED);
            endTime = System.currentTimeMillis();

            long duration = endTime - startTime;

            broadcast(ChatColor.GREEN + "=== DUNGEON COMPLETED! ===");
            broadcast(ChatColor.YELLOW + "Completion time: " + formatDuration(duration));
            broadcast(ChatColor.GOLD + "Congratulations adventurers!");

            // Distribute rewards
            if (rewards != null) {
                rewards.distributeCompletionRewards();
            }

            // Schedule player evacuation
            scheduleEvacuation(10000L); // 10 seconds

            if (isDebugMode()) {
                logger.info("§a[DungeonInstance] §7Dungeon completed: " + template.getId() +
                        " (duration: " + formatDuration(duration) + ")");
            }

        } catch (Exception e) {
            logger.severe("§c[DungeonInstance] Error completing dungeon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Fail the dungeon
     */
    public void fail(String reason) {
        if (state != DungeonState.IN_PROGRESS) {
            return;
        }

        try {
            setState(DungeonState.FAILED);
            this.failureMessage = reason;
            endTime = System.currentTimeMillis();

            broadcast(ChatColor.RED + "=== DUNGEON FAILED ===");
            if (reason != null && !reason.isEmpty()) {
                broadcast(ChatColor.YELLOW + "Reason: " + reason);
            }

            // Distribute consolation rewards if any
            if (rewards != null) {
                rewards.distributeFailureRewards();
            }

            // Schedule player evacuation
            scheduleEvacuation(5000L); // 5 seconds

            if (isDebugMode()) {
                logger.info("§6[DungeonInstance] §7Dungeon failed: " + template.getId() +
                        " (reason: " + reason + ")");
            }

        } catch (Exception e) {
            logger.severe("§c[DungeonInstance] Error failing dungeon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * End the dungeon with specified reason
     */
    public void end(EndReason reason) {
        if (state == DungeonState.ENDED) {
            return;
        }

        try {
            setState(DungeonState.ENDED);
            this.endReason = reason;

            if (endTime == 0) {
                endTime = System.currentTimeMillis();
            }

            // Cancel tasks
            if (progressTask != null) progressTask.cancel();
            if (statusTask != null) statusTask.cancel();

            // Evacuate all players
            evacuateAllPlayers();

            // Clean up rooms and bosses
            cleanupComponents();

            // Clean up instance world
            if (instanceWorld != null) {
                instanceWorld.cleanup();
            }

            if (isDebugMode()) {
                logger.info("§6[DungeonInstance] §7Dungeon ended: " + template.getId() +
                        " (reason: " + reason + ")");
            }

        } catch (Exception e) {
            logger.severe("§c[DungeonInstance] Error ending dungeon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Schedule player evacuation
     */
    private void scheduleEvacuation(long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isValid()) {
                    evacuateAllPlayers();
                }
            }
        }.runTaskLater(YakRealms.getInstance(), delay / 50); // Convert to ticks
    }

    /**
     * Clean up dungeon components
     */
    private void cleanupComponents() {
        try {
            // Clean up rooms
            for (DungeonRoom room : rooms.values()) {
                room.cleanup();
            }

            // Clean up bosses
            for (DungeonBoss boss : bosses.values()) {
                boss.cleanup();
            }

            // Clean up progress
            if (progress != null) {
                progress.cleanup();
            }

            // Clean up rewards
            if (rewards != null) {
                rewards.cleanup();
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonInstance] Error cleaning up components: " + e.getMessage());
        }
    }

    // ================ STATE MANAGEMENT ================

    /**
     * Set dungeon state
     */
    private void setState(DungeonState newState) {
        DungeonState oldState = this.state;
        this.state = newState;

        if (isDebugMode() && oldState != newState) {
            logger.info("§6[DungeonInstance] §7State changed: " + oldState + " -> " + newState);
        }
    }

    /**
     * Set failure state with message
     */
    private void setFailureState(String message) {
        this.failureMessage = message;
        setState(DungeonState.FAILED);

        logger.warning("§c[DungeonInstance] §7Failure: " + message);
    }

    // ================ UTILITY METHODS ================

    /**
     * Get spawn location for this dungeon
     */
    public Location getSpawnLocation() {
        if (instanceWorld != null && instanceWorld.getWorld() != null) {
            Location templateSpawn = template.getSpawnLocation();
            World world = instanceWorld.getWorld();

            if (templateSpawn != null) {
                return new Location(world, templateSpawn.getX(), templateSpawn.getY(), templateSpawn.getZ(),
                        templateSpawn.getYaw(), templateSpawn.getPitch());
            } else {
                return world.getSpawnLocation();
            }
        }
        return null;
    }

    /**
     * Get current room location
     */
    private Location getCurrentRoomLocation() {
        if (currentRoomId != null) {
            DungeonRoom currentRoom = rooms.get(currentRoomId);
            if (currentRoom != null) {
                return currentRoom.getCenterLocation();
            }
        }
        return null;
    }

    /**
     * Find starting room
     */
    private String findStartingRoom() {
        // Look for room named "entrance" or "start", otherwise use first room
        for (String roomId : template.getRooms().keySet()) {
            if (roomId.toLowerCase().contains("entrance") ||
                    roomId.toLowerCase().contains("start")) {
                return roomId;
            }
        }

        // Return first room if no obvious starting room
        return template.getRooms().keySet().iterator().next();
    }

    /**
     * Check if all players are dead
     */
    private boolean areAllPlayersDead() {
        return !playerIds.isEmpty() && deadPlayers.size() >= playerIds.size();
    }

    /**
     * Check if player is in combat
     */
    private boolean isPlayerInCombat(Player player) {
        // Simple implementation - can be enhanced with damage tracking
        return player.getLastDamage() > 0 &&
                (System.currentTimeMillis() - player.getLastDamageCause().getEntity().getTicksLived() * 50) < 10000;
    }

    /**
     * Get all online players in this dungeon
     */
    public List<Player> getOnlinePlayers() {
        return playerIds.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toList());
    }

    /**
     * Get all player IDs
     */
    public List<UUID> getPlayerIds() {
        return new ArrayList<>(playerIds);
    }

    /**
     * Get players list (for compatibility)
     */
    public List<Player> getPlayers() {
        return getOnlinePlayers();
    }

    /**
     * Broadcast message to all players
     */
    public void broadcast(String message) {
        for (Player player : getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    /**
     * Format duration string
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%d seconds", seconds);
        }
    }

    /**
     * Check if debug mode is enabled
     */
    private boolean isDebugMode() {
        return YakRealms.getInstance().isDebugMode();
    }

    // ================ GETTERS ================

    public UUID getId() { return id; }
    public DungeonTemplate getTemplate() { return template; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public DungeonState getState() { return state; }
    public EndReason getEndReason() { return endReason; }
    public String getFailureMessage() { return failureMessage; }
    public InstanceWorld getInstanceWorld() { return instanceWorld; }
    public Map<String, DungeonRoom> getRooms() { return new HashMap<>(rooms); }
    public Map<String, DungeonBoss> getBosses() { return new HashMap<>(bosses); }
    public DungeonProgress getProgress() { return progress; }
    public DungeonRewards getRewards() { return rewards; }
    public String getCurrentRoomId() { return currentRoomId; }
    public Set<String> getCompletedRooms() { return new HashSet<>(completedRooms); }
    public Set<String> getDefeatedBosses() { return new HashSet<>(defeatedBosses); }
    public Map<UUID, DungeonPlayerData> getPlayerData() { return new HashMap<>(playerData); }
    public DungeonPlayerData getPlayerData(UUID playerId) { return playerData.get(playerId); }
    public Map<String, Object> getSessionData() { return new HashMap<>(sessionData); }

    // ================ STATE CHECKS ================

    public boolean isValid() {
        return state != DungeonState.ENDED && instanceWorld != null && instanceWorld.isValid();
    }

    public boolean isActive() {
        return state == DungeonState.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return state == DungeonState.COMPLETED;
    }

    public boolean isFailed() {
        return state == DungeonState.FAILED;
    }

    public boolean isEnded() {
        return state == DungeonState.ENDED;
    }

    public boolean isTimedOut(long timeoutMillis) {
        return System.currentTimeMillis() - startTime > timeoutMillis;
    }

    public boolean hasPlayer(UUID playerId) {
        return playerIds.contains(playerId);
    }

    public boolean hasPlayer(Player player) {
        return hasPlayer(player.getUniqueId());
    }

    public long getDuration() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return end - startTime;
    }

    /**
     * Get session data value
     */
    public Object getSessionData(String key) {
        return sessionData.get(key);
    }

    /**
     * Set session data value
     */
    public void setSessionData(String key, Object value) {
        sessionData.put(key, value);
    }
}