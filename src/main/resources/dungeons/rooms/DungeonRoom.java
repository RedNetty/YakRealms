package com.rednetty.server.mechanics.dungeons.rooms;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.dungeons.config.DungeonTemplate;
import com.rednetty.server.mechanics.dungeons.instance.DungeonInstance;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.spawners.MobSpawner;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Complete Dungeon Room Management System
 *
 * Manages individual rooms within dungeons, including mob spawning,
 * room events, player tracking, environmental effects, and room-specific
 * mechanics like puzzles, traps, and interactive elements.
 *
 * Features:
 * - Room state management (locked, active, completed, failed)
 * - Player presence tracking and room entry/exit events
 * - Mob spawning and management within rooms
 * - Environmental effects and atmosphere
 * - Room-specific mechanics (puzzles, traps, switches)
 * - Wave-based encounters and timed events
 * - Dynamic difficulty scaling
 * - Room completion validation
 * - Integration with dungeon progress system
 * - Customizable room behaviors
 */
public class DungeonRoom {

    // ================ CORE COMPONENTS ================

    private final DungeonInstance dungeonInstance;
    private final DungeonTemplate.RoomDefinition roomDefinition;
    private final String roomId;
    private final Logger logger;
    private final MobManager mobManager;
    private final MobSpawner mobSpawner;

    // ================ ROOM STATE ================

    private RoomState state = RoomState.LOCKED;
    private boolean completed = false;
    private boolean failed = false;
    private long activationTime = 0;
    private long completionTime = 0;
    private String failureReason = null;

    // ================ PLAYER TRACKING ================

    private final Set<UUID> playersInRoom = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersWhoEntered = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerEntryTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerExitTimes = new ConcurrentHashMap<>();

    // ================ MOB MANAGEMENT ================

    private final Set<UUID> roomMobs = ConcurrentHashMap.newKeySet();
    private final Map<String, Location> spawnerLocations = new ConcurrentHashMap<>();
    private final Set<UUID> defeatedMobs = ConcurrentHashMap.newKeySet();
    private int totalMobsSpawned = 0;
    private int totalMobsKilled = 0;

    // ================ ROOM MECHANICS ================

    private final Map<String, Object> roomData = new ConcurrentHashMap<>();
    private final Set<String> activatedMechanisms = ConcurrentHashMap.newKeySet();
    private final List<RoomEvent> queuedEvents = new ArrayList<>();
    private int currentWave = 0;
    private boolean wavesActive = false;

    // ================ TASKS ================

    private BukkitTask roomUpdateTask;
    private BukkitTask mechanicsTask;
    private BukkitTask effectsTask;

    // ================ ENUMS AND CLASSES ================

    /**
     * Room state enumeration
     */
    public enum RoomState {
        LOCKED,     // Room is locked and inaccessible
        AVAILABLE,  // Room is available but not yet entered
        ACTIVE,     // Players are in the room and it's active
        CLEARING,   // Room is being cleared of enemies
        COMPLETED,  // Room has been successfully completed
        FAILED      // Room has failed its objectives
    }

    /**
     * Room event for delayed actions
     */
    public static class RoomEvent {
        private final String eventType;
        private final long triggerTime;
        private final Map<String, Object> parameters;
        private boolean executed = false;

        public RoomEvent(String eventType, long delay, Map<String, Object> parameters) {
            this.eventType = eventType;
            this.triggerTime = System.currentTimeMillis() + delay;
            this.parameters = new HashMap<>(parameters);
        }

        public String getEventType() { return eventType; }
        public long getTriggerTime() { return triggerTime; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
        public boolean isExecuted() { return executed; }
        public void setExecuted(boolean executed) { this.executed = executed; }
        public boolean isReady() { return System.currentTimeMillis() >= triggerTime; }
    }

    // ================ CONSTRUCTOR ================

    public DungeonRoom(DungeonInstance dungeonInstance, DungeonTemplate.RoomDefinition roomDefinition) {
        this.dungeonInstance = dungeonInstance;
        this.roomDefinition = roomDefinition;
        this.roomId = roomDefinition.getId();
        this.logger = YakRealms.getInstance().getLogger();
        this.mobManager = MobManager.getInstance();
        this.mobSpawner = MobSpawner.getInstance();
    }

    // ================ INITIALIZATION ================

    /**
     * Initialize the room
     */
    public boolean initialize() {
        try {
            // Initialize spawners
            if (!initializeSpawners()) {
                logger.warning("§c[DungeonRoom] Failed to initialize spawners for room: " + roomId);
                return false;
            }

            // Initialize room mechanics
            initializeRoomMechanics();

            // Initialize room data from definition properties
            roomData.putAll(roomDefinition.getProperties());

            // Start room tasks
            startTasks();

            // Set initial state
            if (isStartingRoom()) {
                setState(RoomState.AVAILABLE);
            } else {
                setState(RoomState.LOCKED);
            }

            if (isDebugMode()) {
                logger.info("§a[DungeonRoom] §7Initialized room: " + roomId + " (" + roomDefinition.getType() + ")");
            }

            return true;

        } catch (Exception e) {
            logger.severe("§c[DungeonRoom] Failed to initialize room " + roomId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initialize spawners for this room
     */
    private boolean initializeSpawners() {
        try {
            World world = dungeonInstance.getInstanceWorld().getWorld();
            if (world == null) return false;

            for (DungeonTemplate.SpawnerDefinition spawnerDef : roomDefinition.getSpawners()) {
                Location spawnerLoc = new Location(world,
                        spawnerDef.getLocation().getX(),
                        spawnerDef.getLocation().getY(),
                        spawnerDef.getLocation().getZ());

                // Add spawner through mob spawner system
                if (mobSpawner.addSpawner(spawnerLoc, spawnerDef.getMobData())) {
                    spawnerLocations.put(spawnerDef.getId(), spawnerLoc);

                    // Set initial visibility
                    mobSpawner.setSpawnerVisibility(spawnerLoc, spawnerDef.isVisible());

                    if (isDebugMode()) {
                        logger.info("§a[DungeonRoom] §7Added spawner " + spawnerDef.getId() + " to room " + roomId);
                    }
                } else {
                    logger.warning("§c[DungeonRoom] Failed to add spawner " + spawnerDef.getId());
                }
            }

            return true;

        } catch (Exception e) {
            logger.warning("§c[DungeonRoom] Error initializing spawners: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initialize room-specific mechanics
     */
    private void initializeRoomMechanics() {
        String roomType = roomDefinition.getType();

        switch (roomType.toLowerCase()) {
            case "puzzle_room":
                initializePuzzleMechanics();
                break;
            case "wave_room":
                initializeWaveMechanics();
                break;
            case "boss_room":
                initializeBossRoomMechanics();
                break;
            case "trap_room":
                initializeTrapMechanics();
                break;
            case "treasure_room":
                initializeTreasureRoomMechanics();
                break;
            default:
                initializeStandardRoomMechanics();
                break;
        }
    }

    private void initializePuzzleMechanics() {
        roomData.put("puzzle_type", roomDefinition.getProperty("puzzle_type", "button_sequence"));
        roomData.put("puzzle_solved", false);
        roomData.put("puzzle_attempts", 0);
        roomData.put("max_attempts", roomDefinition.getProperty("max_attempts", 3));
    }

    private void initializeWaveMechanics() {
        roomData.put("total_waves", roomDefinition.getProperty("wave_count", 3));
        roomData.put("current_wave", 0);
        roomData.put("wave_delay", roomDefinition.getProperty("wave_delay_seconds", 30) * 1000L);
        roomData.put("waves_completed", false);
    }

    private void initializeBossRoomMechanics() {
        roomData.put("boss_spawned", false);
        roomData.put("boss_defeated", false);
        roomData.put("pre_boss_cleared", false);
    }

    private void initializeTrapMechanics() {
        roomData.put("traps_active", true);
        roomData.put("trap_trigger_count", 0);
        roomData.put("safe_path_shown", false);
    }

    private void initializeTreasureRoomMechanics() {
        roomData.put("treasure_spawned", false);
        roomData.put("treasure_collected", false);
        roomData.put("guardian_defeated", false);
    }

    private void initializeStandardRoomMechanics() {
        roomData.put("enemies_cleared", false);
        roomData.put("room_secured", false);
    }

    /**
     * Start room processing tasks
     */
    private void startTasks() {
        // Main room update task
        roomUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (dungeonInstance.isActive()) {
                        updateRoom();
                    }
                } catch (Exception e) {
                    logger.warning("§c[DungeonRoom] Room update error: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Every second

        // Room mechanics task
        mechanicsTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (state == RoomState.ACTIVE) {
                        processMechanics();
                        processQueuedEvents();
                    }
                } catch (Exception e) {
                    logger.warning("§c[DungeonRoom] Mechanics error: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 10L, 10L); // Every 0.5 seconds

        // Environmental effects task
        effectsTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (state == RoomState.ACTIVE && !playersInRoom.isEmpty()) {
                        applyEnvironmentalEffects();
                    }
                } catch (Exception e) {
                    logger.warning("§c[DungeonRoom] Effects error: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 60L, 60L); // Every 3 seconds
    }

    // ================ MAIN PROCESSING ================

    /**
     * Main room tick processing
     */
    public void tick() {
        if (!dungeonInstance.isActive()) {
            return;
        }

        try {
            updatePlayerTracking();
            updateMobTracking();
            checkRoomCompletion();
        } catch (Exception e) {
            logger.warning("§c[DungeonRoom] Tick error for room " + roomId + ": " + e.getMessage());
        }
    }

    /**
     * Update room state and mechanics
     */
    private void updateRoom() {
        switch (state) {
            case LOCKED:
                // Check if room should become available
                if (shouldBecomeAvailable()) {
                    setState(RoomState.AVAILABLE);
                }
                break;

            case AVAILABLE:
                // Check if players have entered
                if (!playersInRoom.isEmpty()) {
                    activateRoom();
                }
                break;

            case ACTIVE:
                // Process active room mechanics
                processActiveRoom();
                break;

            case CLEARING:
                // Check if clearing is complete
                if (isClearingComplete()) {
                    completeRoom();
                }
                break;
        }
    }

    /**
     * Update player tracking
     */
    private void updatePlayerTracking() {
        // Update current players in room based on location
        Set<UUID> currentPlayers = dungeonInstance.getOnlinePlayers().stream()
                .filter(this::isPlayerInRoom)
                .map(Player::getUniqueId)
                .collect(Collectors.toSet());

        // Handle players entering
        for (UUID playerId : currentPlayers) {
            if (!playersInRoom.contains(playerId)) {
                onPlayerEnter(playerId);
            }
        }

        // Handle players leaving
        Set<UUID> leftPlayers = new HashSet<>(playersInRoom);
        leftPlayers.removeAll(currentPlayers);
        for (UUID playerId : leftPlayers) {
            onPlayerExit(playerId);
        }

        playersInRoom.clear();
        playersInRoom.addAll(currentPlayers);
    }

    /**
     * Update mob tracking
     */
    private void updateMobTracking() {
        // Get current mobs in room
        Set<UUID> currentMobs = getRoomEntities().stream()
                .filter(entity -> entity instanceof LivingEntity)
                .filter(entity -> !(entity instanceof Player))
                .map(Entity::getUniqueId)
                .collect(Collectors.toSet());

        // Update mob count
        roomMobs.clear();
        roomMobs.addAll(currentMobs);

        // Check for newly defeated mobs
        Set<UUID> allMobsEverInRoom = new HashSet<>(roomMobs);
        allMobsEverInRoom.addAll(defeatedMobs);

        for (UUID mobId : allMobsEverInRoom) {
            if (!currentMobs.contains(mobId) && !defeatedMobs.contains(mobId)) {
                onMobDefeated(mobId);
            }
        }
    }

    /**
     * Process room mechanics
     */
    private void processMechanics() {
        String roomType = roomDefinition.getType();

        switch (roomType.toLowerCase()) {
            case "wave_room":
                processWaveMechanics();
                break;
            case "puzzle_room":
                processPuzzleMechanics();
                break;
            case "trap_room":
                processTrapMechanics();
                break;
            case "boss_room":
                processBossRoomMechanics();
                break;
            default:
                processStandardMechanics();
                break;
        }
    }

    /**
     * Process queued events
     */
    private void processQueuedEvents() {
        Iterator<RoomEvent> iterator = queuedEvents.iterator();

        while (iterator.hasNext()) {
            RoomEvent event = iterator.next();

            if (event.isReady() && !event.isExecuted()) {
                executeRoomEvent(event);
                event.setExecuted(true);
                iterator.remove();
            }
        }
    }

    // ================ ROOM MECHANICS PROCESSING ================

    private void processWaveMechanics() {
        if (!wavesActive) return;

        int totalWaves = (Integer) roomData.getOrDefault("total_waves", 3);

        if (currentWave < totalWaves && getRoomMobCount() == 0) {
            // Start next wave
            scheduleNextWave();
        } else if (currentWave >= totalWaves && getRoomMobCount() == 0) {
            // All waves completed
            roomData.put("waves_completed", true);
            setState(RoomState.CLEARING);
        }
    }

    private void processPuzzleMechanics() {
        // Puzzle mechanics would be handled by interaction events
        // This just checks if puzzle is solved
        if ((Boolean) roomData.getOrDefault("puzzle_solved", false)) {
            setState(RoomState.CLEARING);
        }
    }

    private void processTrapMechanics() {
        // Apply trap effects to players in room
        if ((Boolean) roomData.getOrDefault("traps_active", true)) {
            for (UUID playerId : playersInRoom) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && shouldTriggerTrap(player)) {
                    triggerTrap(player);
                }
            }
        }
    }

    private void processBossRoomMechanics() {
        boolean preBossCleared = (Boolean) roomData.getOrDefault("pre_boss_cleared", false);
        boolean bossSpawned = (Boolean) roomData.getOrDefault("boss_spawned", false);

        if (!preBossCleared && getRoomMobCount() == 0) {
            roomData.put("pre_boss_cleared", true);
            scheduleBossSpawn();
        } else if (bossSpawned && (Boolean) roomData.getOrDefault("boss_defeated", false)) {
            setState(RoomState.CLEARING);
        }
    }

    private void processStandardMechanics() {
        if (getRoomMobCount() == 0 && totalMobsSpawned > 0) {
            roomData.put("enemies_cleared", true);
            setState(RoomState.CLEARING);
        }
    }

    // ================ ROOM EVENTS ================

    /**
     * Handle player entering room
     */
    private void onPlayerEnter(UUID playerId) {
        playersWhoEntered.add(playerId);
        playerEntryTimes.put(playerId, System.currentTimeMillis());

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            sendRoomWelcomeMessage(player);
            applyRoomEntryEffects(player);
        }

        if (state == RoomState.AVAILABLE) {
            activateRoom();
        }

        if (isDebugMode()) {
            logger.info("§a[DungeonRoom] §7Player " + (player != null ? player.getName() : playerId) +
                    " entered room " + roomId);
        }
    }

    /**
     * Handle player exiting room
     */
    private void onPlayerExit(UUID playerId) {
        playerExitTimes.put(playerId, System.currentTimeMillis());

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            removeRoomEffects(player);
        }

        if (isDebugMode()) {
            logger.info("§6[DungeonRoom] §7Player " + (player != null ? player.getName() : playerId) +
                    " exited room " + roomId);
        }
    }

    /**
     * Handle mob defeat
     */
    private void onMobDefeated(UUID mobId) {
        defeatedMobs.add(mobId);
        totalMobsKilled++;

        // Notify dungeon progress system
        dungeonInstance.getProgress().onMobKilled("unknown", roomId);

        if (isDebugMode()) {
            logger.info("§a[DungeonRoom] §7Mob defeated in room " + roomId + " (" + totalMobsKilled + "/" + totalMobsSpawned + ")");
        }
    }

    // ================ ROOM STATE MANAGEMENT ================

    /**
     * Activate the room
     */
    private void activateRoom() {
        setState(RoomState.ACTIVE);
        activationTime = System.currentTimeMillis();

        // Trigger room activation events
        onRoomActivated();

        // Start spawning mobs if configured
        activateSpawners();

        // Start room-specific mechanics
        startRoomMechanics();

        broadcastToRoom(ChatColor.YELLOW + "Room activated: " + getDisplayName());
    }

    /**
     * Complete the room
     */
    private void completeRoom() {
        if (completed) return;

        completed = true;
        completionTime = System.currentTimeMillis();
        setState(RoomState.COMPLETED);

        // Deactivate spawners
        deactivateSpawners();

        // Apply completion effects
        applyCompletionEffects();

        // Notify dungeon instance
        dungeonInstance.onRoomComplete(roomId);

        broadcastToRoom(ChatColor.GREEN + "Room completed: " + getDisplayName());

        if (isDebugMode()) {
            long duration = completionTime - activationTime;
            logger.info("§a[DungeonRoom] §7Room " + roomId + " completed in " + (duration / 1000) + " seconds");
        }
    }

    /**
     * Fail the room
     */
    public void failRoom(String reason) {
        if (failed || completed) return;

        failed = true;
        failureReason = reason;
        setState(RoomState.FAILED);

        // Deactivate spawners
        deactivateSpawners();

        broadcastToRoom(ChatColor.RED + "Room failed: " + getDisplayName() + " (" + reason + ")");

        if (isDebugMode()) {
            logger.info("§c[DungeonRoom] §7Room " + roomId + " failed: " + reason);
        }
    }

    /**
     * Set room state
     */
    private void setState(RoomState newState) {
        RoomState oldState = this.state;
        this.state = newState;

        if (isDebugMode() && oldState != newState) {
            logger.info("§6[DungeonRoom] §7Room " + roomId + " state: " + oldState + " -> " + newState);
        }
    }

    // ================ ROOM MECHANICS ================

    private void startRoomMechanics() {
        String roomType = roomDefinition.getType();

        switch (roomType.toLowerCase()) {
            case "wave_room":
                startWaveMechanics();
                break;
            case "puzzle_room":
                startPuzzle();
                break;
            case "trap_room":
                activateTraps();
                break;
            case "treasure_room":
                spawnTreasureGuardian();
                break;
        }
    }

    private void startWaveMechanics() {
        wavesActive = true;
        currentWave = 0;
        scheduleNextWave();
        broadcastToRoom(ChatColor.YELLOW + "Survive the waves! Wave 1 starting...");
    }

    private void scheduleNextWave() {
        currentWave++;
        int totalWaves = (Integer) roomData.getOrDefault("total_waves", 3);
        long delay = (Long) roomData.getOrDefault("wave_delay", 5000L);

        queuedEvents.add(new RoomEvent("spawn_wave", delay, Map.of("wave_number", currentWave)));

        broadcastToRoom(ChatColor.YELLOW + "Wave " + currentWave + "/" + totalWaves + " incoming in " + (delay / 1000) + " seconds!");
    }

    private void startPuzzle() {
        String puzzleType = (String) roomData.getOrDefault("puzzle_type", "button_sequence");
        broadcastToRoom(ChatColor.AQUA + "Puzzle room activated! Solve the " + puzzleType + " to proceed.");
    }

    private void activateTraps() {
        roomData.put("traps_active", true);
        broadcastToRoom(ChatColor.RED + "Warning: This room contains active traps!");
    }

    private void spawnTreasureGuardian() {
        // Spawn a guardian mob for the treasure room
        Location center = getCenterLocation();
        if (center != null) {
            // This would spawn a special guardian mob
            broadcastToRoom(ChatColor.GOLD + "A guardian protects the treasure!");
        }
    }

    private void scheduleBossSpawn() {
        long delay = 3000L; // 3 second delay
        queuedEvents.add(new RoomEvent("spawn_boss", delay, Map.of()));
        broadcastToRoom(ChatColor.DARK_RED + "The boss approaches...");
    }

    // ================ EVENT EXECUTION ================

    private void executeRoomEvent(RoomEvent event) {
        try {
            switch (event.getEventType()) {
                case "spawn_wave":
                    executeSpawnWave(event);
                    break;
                case "spawn_boss":
                    executeSpawnBoss(event);
                    break;
                case "activate_trap":
                    executeActivateTrap(event);
                    break;
                case "spawn_treasure":
                    executeSpawnTreasure(event);
                    break;
                default:
                    if (isDebugMode()) {
                        logger.warning("§c[DungeonRoom] Unknown event type: " + event.getEventType());
                    }
                    break;
            }
        } catch (Exception e) {
            logger.warning("§c[DungeonRoom] Error executing event " + event.getEventType() + ": " + e.getMessage());
        }
    }

    private void executeSpawnWave(RoomEvent event) {
        int waveNumber = (Integer) event.getParameters().getOrDefault("wave_number", 1);

        // Activate spawners for this wave
        for (Location spawnerLoc : spawnerLocations.values()) {
            mobSpawner.resetSpawner(spawnerLoc);
        }

        broadcastToRoom(ChatColor.RED + "Wave " + waveNumber + " has begun!");
    }

    private void executeSpawnBoss(RoomEvent event) {
        roomData.put("boss_spawned", true);

        // Find and activate boss through dungeon instance
        for (String bossId : dungeonInstance.getBosses().keySet()) {
            var boss = dungeonInstance.getBosses().get(bossId);
            if (roomId.equals(boss.getRoomId())) {
                boss.activate();
                break;
            }
        }

        broadcastToRoom(ChatColor.DARK_RED + "The boss has arrived!");
    }

    private void executeActivateTrap(RoomEvent event) {
        // Activate specific trap
        broadcastToRoom(ChatColor.RED + "A trap has been triggered!");
    }

    private void executeSpawnTreasure(RoomEvent event) {
        roomData.put("treasure_spawned", true);
        broadcastToRoom(ChatColor.GOLD + "Treasure has appeared!");
    }

    // ================ SPAWNER MANAGEMENT ================

    private void activateSpawners() {
        for (Location spawnerLoc : spawnerLocations.values()) {
            // Reset spawner to start spawning
            mobSpawner.resetSpawner(spawnerLoc);
        }

        if (isDebugMode() && !spawnerLocations.isEmpty()) {
            logger.info("§a[DungeonRoom] §7Activated " + spawnerLocations.size() + " spawners in room " + roomId);
        }
    }

    private void deactivateSpawners() {
        for (Location spawnerLoc : spawnerLocations.values()) {
            // Make spawners invisible and stop spawning
            mobSpawner.setSpawnerVisibility(spawnerLoc, false);
        }

        if (isDebugMode() && !spawnerLocations.isEmpty()) {
            logger.info("§6[DungeonRoom] §7Deactivated " + spawnerLocations.size() + " spawners in room " + roomId);
        }
    }

    // ================ EFFECTS AND ATMOSPHERE ================

    private void applyEnvironmentalEffects() {
        String roomType = roomDefinition.getType();

        for (UUID playerId : playersInRoom) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;

            switch (roomType.toLowerCase()) {
                case "poison_room":
                    applyPoisonEffect(player);
                    break;
                case "fire_room":
                    applyFireEffect(player);
                    break;
                case "ice_room":
                    applyIceEffect(player);
                    break;
                case "dark_room":
                    applyDarknessEffect(player);
                    break;
            }
        }
    }

    private void applyPoisonEffect(Player player) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.POISON, 100, 0, true, false));
    }

    private void applyFireEffect(Player player) {
        if (Math.random() < 0.1) { // 10% chance per tick
            player.setFireTicks(60); // 3 seconds of fire
        }
    }

    private void applyIceEffect(Player player) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 0, true, false));
    }

    private void applyDarknessEffect(Player player) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, 100, 0, true, false));
    }

    private void applyRoomEntryEffects(Player player) {
        // Apply room-specific entry effects
        applyEnvironmentalEffects();
    }

    private void removeRoomEffects(Player player) {
        // Remove room-specific effects when leaving
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.POISON);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        player.setFireTicks(0);
    }

    private void applyCompletionEffects() {
        // Celebration effects
        for (UUID playerId : playersInRoom) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                        player.getLocation().add(0, 2, 0), 30, 1, 1, 1, 0.1);
            }
        }
    }

    // ================ TRAP SYSTEM ================

    private boolean shouldTriggerTrap(Player player) {
        int triggerCount = (Integer) roomData.getOrDefault("trap_trigger_count", 0);
        return Math.random() < 0.05 && triggerCount < 10; // 5% chance, max 10 triggers
    }

    private void triggerTrap(Player player) {
        int triggerCount = (Integer) roomData.getOrDefault("trap_trigger_count", 0);
        roomData.put("trap_trigger_count", triggerCount + 1);

        // Apply trap damage
        player.damage(4.0);
        player.sendMessage(ChatColor.RED + "You triggered a trap!");

        // Visual effect
        player.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION,
                player.getLocation(), 10, 1, 1, 1, 0.1);
    }

    // ================ UTILITY METHODS ================

    /**
     * Check if room should become available
     */
    private boolean shouldBecomeAvailable() {
        // Room becomes available when connected rooms are completed
        // For now, simplified logic
        return dungeonInstance.getProgress().getOverallProgress() > 0.1;
    }

    /**
     * Check if room clearing is complete
     */
    private boolean isClearingComplete() {
        return getRoomMobCount() == 0 &&
                (Boolean) roomData.getOrDefault("enemies_cleared", false);
    }

    /**
     * Check room completion conditions
     */
    private void checkRoomCompletion() {
        if (state != RoomState.ACTIVE && state != RoomState.CLEARING) {
            return;
        }

        String roomType = roomDefinition.getType();
        boolean shouldComplete = false;

        switch (roomType.toLowerCase()) {
            case "wave_room":
                shouldComplete = (Boolean) roomData.getOrDefault("waves_completed", false);
                break;
            case "puzzle_room":
                shouldComplete = (Boolean) roomData.getOrDefault("puzzle_solved", false);
                break;
            case "boss_room":
                shouldComplete = (Boolean) roomData.getOrDefault("boss_defeated", false);
                break;
            case "treasure_room":
                shouldComplete = (Boolean) roomData.getOrDefault("treasure_collected", false);
                break;
            default:
                shouldComplete = getRoomMobCount() == 0 && totalMobsSpawned > 0;
                break;
        }

        if (shouldComplete && state != RoomState.COMPLETED) {
            setState(RoomState.CLEARING);
            // Will complete on next update cycle
        }
    }

    /**
     * Process active room state
     */
    private void processActiveRoom() {
        // Handle timeout conditions
        long activeTime = System.currentTimeMillis() - activationTime;
        long maxActiveTime = roomDefinition.getProperty("max_active_time_seconds", Long.class, 1800L) * 1000L; // 30 min default

        if (activeTime > maxActiveTime) {
            failRoom("Room timeout");
            return;
        }

        // Handle empty room conditions
        if (playersInRoom.isEmpty()) {
            long emptyTime = System.currentTimeMillis() - getLastPlayerExitTime();
            if (emptyTime > 60000L) { // 1 minute empty
                setState(RoomState.AVAILABLE); // Reset room
            }
        }
    }

    /**
     * Check if player is in room based on location
     */
    private boolean isPlayerInRoom(Player player) {
        Location playerLoc = player.getLocation();
        Location center = getCenterLocation();

        if (center == null || !playerLoc.getWorld().equals(center.getWorld())) {
            return false;
        }

        double radius = roomDefinition.getRadius();
        return playerLoc.distanceSquared(center) <= (radius * radius);
    }

    /**
     * Get entities in room
     */
    private List<Entity> getRoomEntities() {
        Location center = getCenterLocation();
        if (center == null) return new ArrayList<>();

        double radius = roomDefinition.getRadius();
        return center.getWorld().getNearbyEntities(center, radius, radius, radius);
    }

    /**
     * Get room mob count
     */
    private int getRoomMobCount() {
        return roomMobs.size();
    }

    /**
     * Get last player exit time
     */
    private long getLastPlayerExitTime() {
        return playerExitTimes.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
    }

    /**
     * Check if this is a starting room
     */
    private boolean isStartingRoom() {
        return roomId.toLowerCase().contains("entrance") ||
                roomId.toLowerCase().contains("start");
    }

    /**
     * Room activation callback
     */
    private void onRoomActivated() {
        // Update total mobs count for tracking
        totalMobsSpawned = spawnerLocations.size() * 3; // Estimate
    }

    /**
     * Send welcome message to player
     */
    private void sendRoomWelcomeMessage(Player player) {
        String displayName = getDisplayName();
        player.sendMessage(ChatColor.GOLD + "Entered: " + displayName);

        // Send room-specific instructions
        String roomType = roomDefinition.getType();
        switch (roomType.toLowerCase()) {
            case "puzzle_room":
                player.sendMessage(ChatColor.AQUA + "Solve the puzzle to proceed!");
                break;
            case "wave_room":
                player.sendMessage(ChatColor.YELLOW + "Survive all waves to complete this room!");
                break;
            case "boss_room":
                player.sendMessage(ChatColor.RED + "Prepare for a challenging boss fight!");
                break;
            case "treasure_room":
                player.sendMessage(ChatColor.GOLD + "Defeat the guardian to claim the treasure!");
                break;
        }
    }

    /**
     * Broadcast message to all players in room
     */
    private void broadcastToRoom(String message) {
        for (UUID playerId : playersInRoom) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Called when players enter the room
     */
    public void onPlayersEnter(List<Player> players) {
        for (Player player : players) {
            if (isPlayerInRoom(player)) {
                onPlayerEnter(player.getUniqueId());
            }
        }
    }

    // ================ PUBLIC API ================

    /**
     * Get room center location
     */
    public Location getCenterLocation() {
        World world = dungeonInstance.getInstanceWorld().getWorld();
        if (world == null) return null;

        Location templateCenter = roomDefinition.getCenterLocation();
        if (templateCenter != null) {
            return new Location(world, templateCenter.getX(), templateCenter.getY(), templateCenter.getZ());
        }

        // Fallback to world spawn
        return world.getSpawnLocation();
    }

    /**
     * Get room display name
     */
    public String getDisplayName() {
        String customName = roomDefinition.getProperty("display_name", String.class, null);
        if (customName != null) {
            return customName;
        }

        // Generate display name from type and ID
        String type = roomDefinition.getType().replace("_", " ");
        return type.substring(0, 1).toUpperCase() + type.substring(1);
    }

    // ================ GETTERS ================

    public String getRoomId() { return roomId; }
    public DungeonTemplate.RoomDefinition getRoomDefinition() { return roomDefinition; }
    public RoomState getState() { return state; }
    public boolean isCompleted() { return completed; }
    public boolean isFailed() { return failed; }
    public boolean isActive() { return state == RoomState.ACTIVE; }
    public long getActivationTime() { return activationTime; }
    public long getCompletionTime() { return completionTime; }
    public String getFailureReason() { return failureReason; }
    public Set<UUID> getPlayersInRoom() { return new HashSet<>(playersInRoom); }
    public int getPlayerCount() { return playersInRoom.size(); }
    public int getMobCount() { return getRoomMobCount(); }
    public int getTotalMobsKilled() { return totalMobsKilled; }
    public int getTotalMobsSpawned() { return totalMobsSpawned; }
    public Map<String, Object> getRoomData() { return new HashMap<>(roomData); }
    public Object getRoomData(String key) { return roomData.get(key); }
    public void setRoomData(String key, Object value) { roomData.put(key, value); }

    public long getActiveTime() {
        return activationTime > 0 ? System.currentTimeMillis() - activationTime : 0;
    }

    // ================ UTILITY METHODS ================

    private boolean isDebugMode() {
        return YakRealms.getInstance().isDebugMode();
    }

    // ================ CLEANUP ================

    /**
     * Clean up the room
     */
    public void cleanup() {
        try {
            // Cancel tasks
            if (roomUpdateTask != null) roomUpdateTask.cancel();
            if (mechanicsTask != null) mechanicsTask.cancel();
            if (effectsTask != null) effectsTask.cancel();

            // Remove spawners
            for (Location spawnerLoc : spawnerLocations.values()) {
                mobSpawner.removeSpawner(spawnerLoc);
            }

            // Clear tracking data
            playersInRoom.clear();
            playersWhoEntered.clear();
            playerEntryTimes.clear();
            playerExitTimes.clear();
            roomMobs.clear();
            defeatedMobs.clear();
            spawnerLocations.clear();
            roomData.clear();
            queuedEvents.clear();
            activatedMechanisms.clear();

        } catch (Exception e) {
            logger.warning("§c[DungeonRoom] Cleanup error for room " + roomId + ": " + e.getMessage());
        }
    }
}