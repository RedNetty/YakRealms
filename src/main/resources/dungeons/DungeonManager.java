package com.rednetty.server.core.mechanics.dungeons;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.dungeons.bosses.DungeonBoss;
import com.rednetty.server.core.mechanics.dungeons.config.DungeonTemplate;
import com.rednetty.server.core.mechanics.dungeons.instance.DungeonInstance;
import com.rednetty.server.core.mechanics.dungeons.instance.InstanceManager;
import com.rednetty.server.core.mechanics.dungeons.rewards.DungeonRewards;
import com.rednetty.server.core.mechanics.player.social.party.PartyMechanics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Complete Dungeon System Manager
 *
 * Core controller for the entire dungeon system, managing templates, instances,
 * and integration with existing party and mob systems.
 *
 * Features:
 * - Template-based dungeon creation
 * - Instance management with world instancing
 * - Party integration for group dungeons
 * - Boss encounters with phases and abilities
 * - Progress tracking and rewards
 * - Event-driven architecture
 * - Easy configuration and customization
 */
public class DungeonManager implements Listener {

    private static volatile DungeonManager instance;
    private static final Object LOCK = new Object();

    // ================ CORE COMPONENTS ================

    private final Logger logger;
    private final YakRealms plugin;
    private final InstanceManager instanceManager;
    private final PartyMechanics partyMechanics;

    // ================ DATA STORAGE ================

    private final Map<String, DungeonTemplate> dungeonTemplates = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonInstance> activeDungeons = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonInstance> playerToDungeon = new ConcurrentHashMap<>();
    private final Map<String, DungeonStatistics> dungeonStats = new ConcurrentHashMap<>();

    // ================ TASKS ================

    private BukkitTask mainProcessingTask;
    private BukkitTask cleanupTask;
    private BukkitTask statisticsTask;

    // ================ CONFIGURATION ================

    private boolean enabled = true;
    private boolean debug = false;
    private int maxConcurrentDungeons = 50;
    private long dungeonTimeout = 7200000L; // 2 hours
    private int maxPlayersPerDungeon = 8;
    private boolean requirePartyForGroup = true;

    // ================ EVENT LISTENERS ================

    private final Set<DungeonEventListener> eventListeners = ConcurrentHashMap.newKeySet();

    /**
     * Dungeon event listener interface for extensibility
     */
    public interface DungeonEventListener {
        default void onDungeonStart(DungeonInstance dungeon) {}
        default void onDungeonComplete(DungeonInstance dungeon) {}
        default void onDungeonFail(DungeonInstance dungeon) {}
        default void onPlayerEnterDungeon(DungeonInstance dungeon, UUID playerId) {}
        default void onPlayerExitDungeon(DungeonInstance dungeon, UUID playerId) {}
        default void onBossEngage(DungeonInstance dungeon, DungeonBoss boss) {}
        default void onBossDefeat(DungeonInstance dungeon, DungeonBoss boss) {}
        default void onRoomComplete(DungeonInstance dungeon, String roomId) {}
    }

    /**
     * Dungeon statistics tracking
     */
    public static class DungeonStatistics {
        private int totalRuns = 0;
        private int completedRuns = 0;
        private int failedRuns = 0;
        private long totalDuration = 0;
        private long bestTime = Long.MAX_VALUE;
        private long worstTime = 0;
        private int totalPlayers = 0;
        private final Map<String, Integer> bossKills = new HashMap<>();
        private final Map<Integer, Integer> tierCompletions = new HashMap<>();

        // Getters and increment methods
        public int getTotalRuns() { return totalRuns; }
        public void incrementTotalRuns() { this.totalRuns++; }
        public int getCompletedRuns() { return completedRuns; }
        public void incrementCompletedRuns() { this.completedRuns++; }
        public int getFailedRuns() { return failedRuns; }
        public void incrementFailedRuns() { this.failedRuns++; }
        public long getTotalDuration() { return totalDuration; }
        public void addDuration(long duration) {
            this.totalDuration += duration;
            if (duration < bestTime) bestTime = duration;
            if (duration > worstTime) worstTime = duration;
        }
        public long getBestTime() { return bestTime == Long.MAX_VALUE ? 0 : bestTime; }
        public long getWorstTime() { return worstTime; }
        public double getAverageDuration() {
            return completedRuns > 0 ? (double) totalDuration / completedRuns : 0;
        }
        public double getCompletionRate() {
            return totalRuns > 0 ? (double) completedRuns / totalRuns * 100 : 0;
        }
        public int getTotalPlayers() { return totalPlayers; }
        public void addPlayers(int count) { this.totalPlayers += count; }
        public Map<String, Integer> getBossKills() { return new HashMap<>(bossKills); }
        public void incrementBossKill(String bossId) { bossKills.merge(bossId, 1, Integer::sum); }
        public Map<Integer, Integer> getTierCompletions() { return new HashMap<>(tierCompletions); }
        public void incrementTierCompletion(int tier) { tierCompletions.merge(tier, 1, Integer::sum); }
    }

    /**
     * Private constructor for singleton pattern
     */
    private DungeonManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.instanceManager = InstanceManager.getInstance();
        this.partyMechanics = PartyMechanics.getInstance();

        loadConfiguration();
        initializeDefaultTemplates();
    }

    /**
     * Get the singleton instance with thread safety
     */
    public static DungeonManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new DungeonManager();
                }
            }
        }
        return instance;
    }

    // ================ INITIALIZATION ================

    /**
     * Initialize the dungeon system
     */
    public void initialize() {
        try {
            // Initializing dungeon system

            // Register events
            Bukkit.getPluginManager().registerEvents(this, plugin);

            // Initialize instance manager
            instanceManager.initialize();

            // Load dungeon templates
            loadDungeonTemplates();

            // Start processing tasks
            startTasks();

            logger.info("Dungeon system enabled successfully");
            logger.info("Loaded " + dungeonTemplates.size() + " dungeon templates");

        } catch (Exception e) {
            logger.severe("Failed to initialize dungeon system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load configuration from plugin config
     */
    private void loadConfiguration() {
        var config = plugin.getConfig();
        this.debug = config.getBoolean("dungeons.debug", false);
        this.maxConcurrentDungeons = config.getInt("dungeons.max-concurrent", 50);
        this.dungeonTimeout = config.getLong("dungeons.timeout-ms", 7200000L);
        this.maxPlayersPerDungeon = config.getInt("dungeons.max-players", 8);
        this.requirePartyForGroup = config.getBoolean("dungeons.require-party", true);
    }

    /**
     * Initialize default dungeon templates
     */
    private void initializeDefaultTemplates() {
        // This will be called after template loading to ensure we have basic templates
        if (debug) {
            logger.info("§6[DungeonManager] §7Initializing default templates...");
        }
    }

    /**
     * Load dungeon templates from configuration
     */
    private void loadDungeonTemplates() {
        // Example template creation - in real implementation, load from files
        createExampleDungeonTemplates();
    }

    /**
     * Create example dungeon templates for demonstration
     */
    private void createExampleDungeonTemplates() {
        // Example: Crypt of Shadows - Tier 3-4 dungeon
        DungeonTemplate cryptTemplate = DungeonTemplate.builder("crypt_of_shadows")
                .displayName("§8Crypt of Shadows")
                .description("Ancient burial grounds haunted by restless spirits")
                .minTier(3)
                .maxTier(4)
                .minPlayers(1)
                .maxPlayers(4)
                .estimatedDuration(1800000L) // 30 minutes
                .worldTemplate("dungeon_crypt")
                .spawnLocation(new Location(Bukkit.getWorld("world"), 0, 64, 0))
                .addRoom("entrance", "entrance_hall", false)
                .addRoom("corridor1", "haunted_corridor", false)
                .addRoom("puzzle1", "spirit_puzzle", false)
                .addRoom("miniboss", "guardian_chamber", true)
                .addRoom("corridor2", "bone_passage", false)
                .addRoom("boss", "lich_throne", true)
                .addBoss("lich_lord", "boss", 4, true)
                .addObjective("clear_entrance", "entrance", "eliminate_all")
                .addObjective("solve_puzzle", "puzzle1", "solve_puzzle")
                .addObjective("defeat_guardian", "miniboss", "defeat_boss")
                .addObjective("defeat_lich", "boss", "defeat_boss")
                .addReward(3, "skeleton:4@false#1", "witherskeleton:4@false#1")
                .addReward(4, "skeleton:4@true#1", "rare_bone_fragment")
                .build();

        registerTemplate(cryptTemplate);

        // Example: Frozen Citadel - Tier 5-6 dungeon
        DungeonTemplate citadelTemplate = DungeonTemplate.builder("frozen_citadel")
                .displayName("§bFrozen Citadel")
                .description("Ice fortress of the Frost King")
                .minTier(5)
                .maxTier(6)
                .minPlayers(3)
                .maxPlayers(6)
                .estimatedDuration(3600000L) // 60 minutes
                .worldTemplate("dungeon_citadel")
                .spawnLocation(new Location(Bukkit.getWorld("world"), 100, 128, 100))
                .addRoom("gates", "frozen_gates", false)
                .addRoom("courtyard", "ice_courtyard", false)
                .addRoom("towers", "twin_towers", true)
                .addRoom("throne", "frost_throne", true)
                .addBoss("frost_king", "throne", 6, true)
                .addObjective("breach_gates", "gates", "eliminate_all")
                .addObjective("clear_courtyard", "courtyard", "survive_waves")
                .addObjective("conquer_towers", "towers", "defeat_all_bosses")
                .addObjective("defeat_king", "throne", "defeat_boss")
                .addReward(5, "frozenelite:5@true#1", "ice_shard_rare")
                .addReward(6, "frozenboss:6@true#1", "frozen_crown_legendary")
                .build();

        registerTemplate(citadelTemplate);

        if (debug) {
            logger.info("§a[DungeonManager] §7Created " + dungeonTemplates.size() + " example templates");
        }
    }

    /**
     * Start processing tasks
     */
    private void startTasks() {
        // Main processing task
        mainProcessingTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    processActiveDungeons();
                } catch (Exception e) {
                    logger.warning("§c[DungeonManager] Main task error: " + e.getMessage());
                    if (debug) e.printStackTrace();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second

        // Cleanup task
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    cleanupExpiredDungeons();
                    cleanupInvalidInstances();
                } catch (Exception e) {
                    logger.warning("§c[DungeonManager] Cleanup task error: " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every minute

        // Statistics task
        statisticsTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    updateStatistics();
                } catch (Exception e) {
                    logger.warning("§c[DungeonManager] Statistics task error: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // Every 5 minutes
    }

    // ================ TEMPLATE MANAGEMENT ================

    /**
     * Register a dungeon template
     */
    public void registerTemplate(DungeonTemplate template) {
        if (template == null || template.getId() == null) {
            throw new IllegalArgumentException("Template and ID cannot be null");
        }

        dungeonTemplates.put(template.getId(), template);
        dungeonStats.putIfAbsent(template.getId(), new DungeonStatistics());

        if (debug) {
            logger.info("§a[DungeonManager] §7Registered template: " + template.getId());
        }
    }

    /**
     * Get a dungeon template by ID
     */
    public DungeonTemplate getTemplate(String templateId) {
        return dungeonTemplates.get(templateId);
    }

    /**
     * Get all available templates
     */
    public Collection<DungeonTemplate> getAllTemplates() {
        return new ArrayList<>(dungeonTemplates.values());
    }

    /**
     * Get templates suitable for a player's tier
     */
    public List<DungeonTemplate> getTemplatesForTier(int tier) {
        return dungeonTemplates.values().stream()
                .filter(template -> tier >= template.getMinTier() && tier <= template.getMaxTier())
                .collect(Collectors.toList());
    }

    /**
     * Get templates suitable for a party
     */
    public List<DungeonTemplate> getTemplatesForParty(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return Collections.emptyList();
        }

        int partySize = players.size();
        int minTier = players.stream().mapToInt(this::getPlayerTier).min().orElse(1);
        int maxTier = players.stream().mapToInt(this::getPlayerTier).max().orElse(6);

        return dungeonTemplates.values().stream()
                .filter(template -> partySize >= template.getMinPlayers() &&
                        partySize <= template.getMaxPlayers())
                .filter(template -> !(maxTier < template.getMinTier() ||
                        minTier > template.getMaxTier()))
                .collect(Collectors.toList());
    }

    // ================ DUNGEON INSTANCE MANAGEMENT ================

    /**
     * Create and start a new dungeon instance
     */
    public DungeonInstance createDungeon(String templateId, Player leader) {
        return createDungeon(templateId, Collections.singletonList(leader));
    }

    /**
     * Create and start a new dungeon instance for a party
     */
    public DungeonInstance createDungeon(String templateId, List<Player> players) {
        if (!enabled) {
            throw new IllegalStateException("Dungeon system is disabled");
        }

        if (activeDungeons.size() >= maxConcurrentDungeons) {
            throw new IllegalStateException("Maximum concurrent dungeons reached");
        }

        DungeonTemplate template = getTemplate(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Unknown dungeon template: " + templateId);
        }

        // Validate players
        validateDungeonPlayers(template, players);

        try {
            // Create dungeon instance
            DungeonInstance dungeon = new DungeonInstance(template, players);

            // Initialize the instance
            if (!dungeon.initialize()) {
                throw new RuntimeException("Failed to initialize dungeon instance");
            }

            // Register the instance
            activeDungeons.put(dungeon.getId(), dungeon);
            for (Player player : players) {
                playerToDungeon.put(player.getUniqueId(), dungeon);
            }

            // Update statistics
            DungeonStatistics stats = dungeonStats.get(templateId);
            if (stats != null) {
                stats.incrementTotalRuns();
                stats.addPlayers(players.size());
            }

            // Fire event
            fireEvent(listener -> listener.onDungeonStart(dungeon));

            if (debug) {
                logger.info("§a[DungeonManager] §7Created dungeon " + templateId +
                        " for " + players.size() + " players");
            }

            return dungeon;

        } catch (Exception e) {
            logger.severe("§c[DungeonManager] Failed to create dungeon " + templateId + ": " + e.getMessage());
            throw new RuntimeException("Failed to create dungeon", e);
        }
    }

    /**
     * Validate players for dungeon entry
     */
    private void validateDungeonPlayers(DungeonTemplate template, List<Player> players) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("No players provided");
        }

        if (players.size() < template.getMinPlayers()) {
            throw new IllegalArgumentException("Not enough players (min: " + template.getMinPlayers() + ")");
        }

        if (players.size() > template.getMaxPlayers()) {
            throw new IllegalArgumentException("Too many players (max: " + template.getMaxPlayers() + ")");
        }

        // Check if players are already in dungeons
        for (Player player : players) {
            if (isInDungeon(player)) {
                throw new IllegalArgumentException(player.getName() + " is already in a dungeon");
            }
        }

        // Check party requirements for group dungeons
        if (requirePartyForGroup && players.size() > 1) {
            UUID leaderId = players.get(0).getUniqueId();
            for (Player player : players) {
                if (!partyMechanics.arePartyMembers(leaderId, player.getUniqueId())) {
                    throw new IllegalArgumentException("All players must be in the same party");
                }
            }
        }

        // Check tier requirements
        for (Player player : players) {
            int playerTier = getPlayerTier(player);
            if (playerTier < template.getMinTier()) {
                throw new IllegalArgumentException(player.getName() + " is too low tier (min: " + template.getMinTier() + ")");
            }
            if (playerTier > template.getMaxTier()) {
                throw new IllegalArgumentException(player.getName() + " is too high tier (max: " + template.getMaxTier() + ")");
            }
        }
    }

    /**
     * Remove a player from their current dungeon
     */
    public boolean removePlayerFromDungeon(Player player) {
        return removePlayerFromDungeon(player.getUniqueId());
    }

    public boolean removePlayerFromDungeon(UUID playerId) {
        DungeonInstance dungeon = playerToDungeon.remove(playerId);
        if (dungeon == null) {
            return false;
        }

        try {
            boolean removed = dungeon.removePlayer(playerId);

            if (removed) {
                // Fire event
                fireEvent(listener -> listener.onPlayerExitDungeon(dungeon, playerId));

                // Check if dungeon should be ended
                if (dungeon.getPlayers().isEmpty()) {
                    endDungeon(dungeon.getId(), DungeonInstance.EndReason.ABANDONED);
                }
            }

            return removed;

        } catch (Exception e) {
            logger.warning("§c[DungeonManager] Error removing player from dungeon: " + e.getMessage());
            return false;
        }
    }

    /**
     * End a dungeon instance
     */
    public boolean endDungeon(UUID dungeonId, DungeonInstance.EndReason reason) {
        DungeonInstance dungeon = activeDungeons.remove(dungeonId);
        if (dungeon == null) {
            return false;
        }

        try {
            // Remove all players from mapping
            for (UUID playerId : dungeon.getPlayerIds()) {
                playerToDungeon.remove(playerId);
            }

            // Update statistics
            updateDungeonStatistics(dungeon, reason);

            // End the dungeon
            dungeon.end(reason);

            // Fire appropriate events
            switch (reason) {
                case COMPLETED:
                    fireEvent(listener -> listener.onDungeonComplete(dungeon));
                    break;
                case FAILED:
                case TIMEOUT:
                case ERROR:
                    fireEvent(listener -> listener.onDungeonFail(dungeon));
                    break;
            }

            if (debug) {
                logger.info("§6[DungeonManager] §7Ended dungeon " + dungeon.getTemplate().getId() +
                        " (reason: " + reason + ")");
            }

            return true;

        } catch (Exception e) {
            logger.severe("§c[DungeonManager] Error ending dungeon: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================ PROCESSING AND MAINTENANCE ================

    /**
     * Process all active dungeons
     */
    private void processActiveDungeons() {
        if (activeDungeons.isEmpty()) {
            return;
        }

        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, DungeonInstance> entry : activeDungeons.entrySet()) {
            UUID dungeonId = entry.getKey();
            DungeonInstance dungeon = entry.getValue();

            try {
                // Check if dungeon is still valid
                if (!dungeon.isValid()) {
                    toRemove.add(dungeonId);
                    continue;
                }

                // Process the dungeon
                dungeon.tick();

                // Check for timeout
                if (dungeon.isTimedOut(dungeonTimeout)) {
                    toRemove.add(dungeonId);
                    endDungeon(dungeonId, DungeonInstance.EndReason.TIMEOUT);
                }

                // Check completion
                if (dungeon.isCompleted()) {
                    toRemove.add(dungeonId);
                    endDungeon(dungeonId, DungeonInstance.EndReason.COMPLETED);
                }

                // Check failure
                if (dungeon.isFailed()) {
                    toRemove.add(dungeonId);
                    endDungeon(dungeonId, DungeonInstance.EndReason.FAILED);
                }

            } catch (Exception e) {
                logger.warning("§c[DungeonManager] Error processing dungeon " + dungeonId + ": " + e.getMessage());
                toRemove.add(dungeonId);
                endDungeon(dungeonId, DungeonInstance.EndReason.ERROR);
            }
        }

        // Clean up flagged dungeons
        for (UUID dungeonId : toRemove) {
            activeDungeons.remove(dungeonId);
        }
    }

    /**
     * Clean up expired dungeons
     */
    private void cleanupExpiredDungeons() {
        long currentTime = System.currentTimeMillis();
        List<UUID> expired = activeDungeons.entrySet().stream()
                .filter(entry -> (currentTime - entry.getValue().getStartTime()) > dungeonTimeout)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (UUID dungeonId : expired) {
            endDungeon(dungeonId, DungeonInstance.EndReason.TIMEOUT);
        }

        if (debug && !expired.isEmpty()) {
            logger.info("§6[DungeonManager] §7Cleaned up " + expired.size() + " expired dungeons");
        }
    }

    /**
     * Clean up invalid instances
     */
    private void cleanupInvalidInstances() {
        // Clean up player mappings for offline players
        playerToDungeon.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });

        instanceManager.cleanupInvalidInstances();
    }

    /**
     * Update dungeon statistics
     */
    private void updateDungeonStatistics(DungeonInstance dungeon, DungeonInstance.EndReason reason) {
        DungeonStatistics stats = dungeonStats.get(dungeon.getTemplate().getId());
        if (stats == null) return;

        long duration = System.currentTimeMillis() - dungeon.getStartTime();

        switch (reason) {
            case COMPLETED:
                stats.incrementCompletedRuns();
                stats.addDuration(duration);

                // Track tier completions
                int avgTier = dungeon.getPlayers().stream()
                        .mapToInt(this::getPlayerTier)
                        .sum() / dungeon.getPlayers().size();
                stats.incrementTierCompletion(avgTier);

                // Track boss kills
                for (String bossId : dungeon.getDefeatedBosses()) {
                    stats.incrementBossKill(bossId);
                }
                break;

            case FAILED:
            case TIMEOUT:
                stats.incrementFailedRuns();
                break;
        }
    }

    /**
     * Update global statistics
     */
    private void updateStatistics() {
        // This would save statistics to database in a real implementation
        if (debug) {
            int totalActive = activeDungeons.size();
            int totalCompleted = dungeonStats.values().stream()
                    .mapToInt(DungeonStatistics::getCompletedRuns)
                    .sum();

            logger.info("§6[DungeonManager] §7Statistics: " + totalActive + " active, " +
                    totalCompleted + " total completed");
        }
    }

    // ================ QUERY METHODS ================

    /**
     * Check if a player is in a dungeon
     */
    public boolean isInDungeon(Player player) {
        return isInDungeon(player.getUniqueId());
    }

    public boolean isInDungeon(UUID playerId) {
        return playerToDungeon.containsKey(playerId);
    }

    /**
     * Get the dungeon a player is in
     */
    public DungeonInstance getPlayerDungeon(Player player) {
        return getPlayerDungeon(player.getUniqueId());
    }

    public DungeonInstance getPlayerDungeon(UUID playerId) {
        return playerToDungeon.get(playerId);
    }

    /**
     * Get a dungeon by ID
     */
    public DungeonInstance getDungeon(UUID dungeonId) {
        return activeDungeons.get(dungeonId);
    }

    /**
     * Get all active dungeons
     */
    public Collection<DungeonInstance> getActiveDungeons() {
        return new ArrayList<>(activeDungeons.values());
    }

    /**
     * Get dungeon statistics
     */
    public DungeonStatistics getStatistics(String templateId) {
        return dungeonStats.get(templateId);
    }

    public Map<String, DungeonStatistics> getAllStatistics() {
        return new HashMap<>(dungeonStats);
    }

    // ================ UTILITY METHODS ================

    /**
     * Get a player's tier (integration point with your tier system)
     */
    private int getPlayerTier(Player player) {
        // This should integrate with your tier system
        // For now, return a placeholder
        return 3; // TODO: Implement proper tier detection
    }

    /**
     * Fire events to all listeners
     */
    private void fireEvent(java.util.function.Consumer<DungeonEventListener> eventCall) {
        for (DungeonEventListener listener : eventListeners) {
            try {
                eventCall.accept(listener);
            } catch (Exception e) {
                logger.warning("§c[DungeonManager] Event listener error: " + e.getMessage());
            }
        }
    }

    // ================ EVENT HANDLERS ================

    /**
     * Handle player quit
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (isInDungeon(player)) {
            removePlayerFromDungeon(player);
        }
    }

    // ================ API METHODS ================

    /**
     * Add an event listener
     */
    public void addEventListener(DungeonEventListener listener) {
        eventListeners.add(listener);
    }

    /**
     * Remove an event listener
     */
    public void removeEventListener(DungeonEventListener listener) {
        eventListeners.remove(listener);
    }

    /**
     * Send a message to all players in a dungeon
     */
    public void broadcastToDungeon(UUID dungeonId, String message) {
        DungeonInstance dungeon = getDungeon(dungeonId);
        if (dungeon != null) {
            dungeon.broadcast(message);
        }
    }

    /**
     * Teleport all players out of a dungeon
     */
    public boolean evacuateDungeon(UUID dungeonId) {
        DungeonInstance dungeon = getDungeon(dungeonId);
        if (dungeon == null) {
            return false;
        }

        try {
            dungeon.evacuateAllPlayers();
            endDungeon(dungeonId, DungeonInstance.EndReason.ABANDONED);
            return true;
        } catch (Exception e) {
            logger.warning("§c[DungeonManager] Error evacuating dungeon: " + e.getMessage());
            return false;
        }
    }

    // ================ GETTERS AND SETTERS ================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDebugMode() { return debug; }
    public void setDebugMode(boolean debug) { this.debug = debug; }
    public int getMaxConcurrentDungeons() { return maxConcurrentDungeons; }
    public void setMaxConcurrentDungeons(int max) { this.maxConcurrentDungeons = Math.max(1, max); }
    public long getDungeonTimeout() { return dungeonTimeout; }
    public void setDungeonTimeout(long timeout) { this.dungeonTimeout = Math.max(300000L, timeout); }
    public int getMaxPlayersPerDungeon() { return maxPlayersPerDungeon; }
    public void setMaxPlayersPerDungeon(int max) { this.maxPlayersPerDungeon = Math.max(1, max); }
    public boolean isRequirePartyForGroup() { return requirePartyForGroup; }
    public void setRequirePartyForGroup(boolean require) { this.requirePartyForGroup = require; }

    public int getActiveDungeonCount() { return activeDungeons.size(); }
    public int getTemplateCount() { return dungeonTemplates.size(); }

    // ================ SHUTDOWN ================

    /**
     * Shutdown the dungeon system
     */
    public void shutdown() {
        try {
            logger.info("§6[DungeonManager] §7Shutting down dungeon system...");

            // Cancel tasks
            if (mainProcessingTask != null) mainProcessingTask.cancel();
            if (cleanupTask != null) cleanupTask.cancel();
            if (statisticsTask != null) statisticsTask.cancel();

            // End all active dungeons
            List<UUID> activeDungeonIds = new ArrayList<>(activeDungeons.keySet());
            for (UUID dungeonId : activeDungeonIds) {
                endDungeon(dungeonId, DungeonInstance.EndReason.SHUTDOWN);
            }

            // Shutdown instance manager
            instanceManager.shutdown();

            // Clear data
            activeDungeons.clear();
            playerToDungeon.clear();
            eventListeners.clear();

            logger.info("§a[DungeonManager] §7Dungeon system shutdown complete");

        } catch (Exception e) {
            logger.severe("§c[DungeonManager] Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}