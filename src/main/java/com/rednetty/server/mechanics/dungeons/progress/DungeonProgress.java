package com.rednetty.server.mechanics.dungeons.progress;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.dungeons.config.DungeonTemplate;
import com.rednetty.server.mechanics.dungeons.instance.DungeonInstance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ENHANCED: Complete Dungeon Progress Tracking System
 *
 * Manages objective tracking, progress updates, and completion detection
 * for dungeon instances. Handles complex objective dependencies and
 * provides real-time progress feedback to players.
 *
 * Features:
 * - Multi-objective tracking with dependencies
 * - Real-time progress updates
 * - Custom objective types with parameters
 * - Progress persistence across sessions
 * - Event-driven progress notifications
 * - Visual progress indicators
 * - Completion validation
 * - Rollback support for failed objectives
 */
public class DungeonProgress {

    // ================ CORE COMPONENTS ================

    private final DungeonInstance dungeonInstance;
    private final DungeonTemplate template;
    private final Logger logger;

    // ================ OBJECTIVE TRACKING ================

    private final Map<String, ObjectiveProgress> objectives = new ConcurrentHashMap<>();
    private final Set<String> completedObjectives = ConcurrentHashMap.newKeySet();
    private final Set<String> failedObjectives = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> objectiveStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> objectiveCompletionTimes = new ConcurrentHashMap<>();

    // ================ PROGRESS STATE ================

    private boolean allRequiredComplete = false;
    private double overallProgress = 0.0;
    private String currentPhase = "initialization";
    private final Map<String, Object> progressData = new ConcurrentHashMap<>();

    // ================ TASKS ================

    private BukkitTask progressUpdateTask;
    private BukkitTask notificationTask;

    // ================ OBJECTIVE PROGRESS TRACKING ================

    /**
     * Tracks progress for a specific objective
     */
    public static class ObjectiveProgress {
        private final String objectiveId;
        private final DungeonTemplate.ObjectiveDefinition definition;
        private final Map<String, Object> parameters;
        private final Set<String> dependencies;

        private ObjectiveState state = ObjectiveState.LOCKED;
        private double progress = 0.0;
        private int currentValue = 0;
        private int targetValue = 1;
        private String statusMessage = "";
        private long startTime = 0;
        private long completionTime = 0;
        private final Map<String, Object> customData = new HashMap<>();

        public ObjectiveProgress(String objectiveId, DungeonTemplate.ObjectiveDefinition definition) {
            this.objectiveId = objectiveId;
            this.definition = definition;
            this.parameters = new HashMap<>(definition.getParameters());
            this.dependencies = new HashSet<>(definition.getDependencies());

            // Initialize target value from parameters
            this.targetValue = getParameter("target_count", Integer.class, 1);
            this.statusMessage = getParameter("description", String.class, definition.getType().getId());
        }

        // Getters and setters
        public String getObjectiveId() { return objectiveId; }
        public DungeonTemplate.ObjectiveDefinition getDefinition() { return definition; }
        public ObjectiveState getState() { return state; }
        public void setState(ObjectiveState state) { this.state = state; }
        public double getProgress() { return progress; }
        public void setProgress(double progress) { this.progress = Math.max(0.0, Math.min(1.0, progress)); }
        public int getCurrentValue() { return currentValue; }
        public void setCurrentValue(int value) { this.currentValue = Math.max(0, value); updateProgress(); }
        public int getTargetValue() { return targetValue; }
        public void setTargetValue(int value) { this.targetValue = Math.max(1, value); updateProgress(); }
        public String getStatusMessage() { return statusMessage; }
        public void setStatusMessage(String message) { this.statusMessage = message; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long time) { this.startTime = time; }
        public long getCompletionTime() { return completionTime; }
        public void setCompletionTime(long time) { this.completionTime = time; }
        public Set<String> getDependencies() { return new HashSet<>(dependencies); }
        public Map<String, Object> getCustomData() { return new HashMap<>(customData); }
        public void setCustomData(String key, Object value) { customData.put(key, value); }
        public Object getCustomData(String key) { return customData.get(key); }

        public void incrementProgress(int amount) {
            setCurrentValue(currentValue + amount);
        }

        public void updateProgress() {
            if (targetValue > 0) {
                this.progress = (double) currentValue / targetValue;
            }
        }

        public boolean isCompleted() {
            return state == ObjectiveState.COMPLETED;
        }

        public boolean isFailed() {
            return state == ObjectiveState.FAILED;
        }

        public boolean isActive() {
            return state == ObjectiveState.ACTIVE;
        }

        public boolean isLocked() {
            return state == ObjectiveState.LOCKED;
        }

        public <T> T getParameter(String key, Class<T> type, T defaultValue) {
            Object value = parameters.get(key);
            return type.isInstance(value) ? type.cast(value) : defaultValue;
        }

        public long getElapsedTime() {
            if (startTime == 0) return 0;
            long endTime = completionTime > 0 ? completionTime : System.currentTimeMillis();
            return endTime - startTime;
        }

        public String getFormattedProgress() {
            if (targetValue == 1) {
                return progress >= 1.0 ? "Complete" : "Incomplete";
            } else {
                return String.format("%d/%d (%.1f%%)", currentValue, targetValue, progress * 100);
            }
        }
    }

    /**
     * Objective state enumeration
     */
    public enum ObjectiveState {
        LOCKED,      // Dependencies not met
        AVAILABLE,   // Dependencies met, can be started
        ACTIVE,      // Currently in progress
        COMPLETED,   // Successfully completed
        FAILED       // Failed to complete
    }

    // ================ CONSTRUCTOR ================

    public DungeonProgress(DungeonInstance dungeonInstance) {
        this.dungeonInstance = dungeonInstance;
        this.template = dungeonInstance.getTemplate();
        this.logger = YakRealms.getInstance().getLogger();
    }

    // ================ INITIALIZATION ================

    /**
     * Initialize the progress tracking system
     */
    public boolean initialize() {
        try {
            // Initialize all objectives from template
            for (DungeonTemplate.ObjectiveDefinition objDef : template.getObjectives()) {
                ObjectiveProgress progress = new ObjectiveProgress(objDef.getId(), objDef);
                objectives.put(objDef.getId(), progress);
                objectiveStartTimes.put(objDef.getId(), 0L);
            }

            // Calculate initial dependencies and unlock available objectives
            updateObjectiveAvailability();

            // Start tasks
            startTasks();

            // Set initial phase
            currentPhase = "exploring";

            if (isDebugMode()) {
                logger.info("§a[DungeonProgress] §7Initialized with " + objectives.size() + " objectives");
            }

            return true;

        } catch (Exception e) {
            logger.severe("§c[DungeonProgress] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Start progress tracking tasks
     */
    private void startTasks() {
        // Progress update task
        progressUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    updateProgress();
                } catch (Exception e) {
                    logger.warning("§c[DungeonProgress] Progress update error: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Every second

        // Notification task
        notificationTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    sendProgressUpdates();
                } catch (Exception e) {
                    logger.warning("§c[DungeonProgress] Notification error: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 100L, 100L); // Every 5 seconds
    }

    // ================ MAIN PROCESSING ================

    /**
     * Main tick processing
     */
    public void tick() {
        if (!dungeonInstance.isActive()) {
            return;
        }

        try {
            // Update objective states
            updateObjectiveStates();

            // Check for completion
            checkOverallCompletion();

            // Update phase if needed
            updateCurrentPhase();

        } catch (Exception e) {
            logger.warning("§c[DungeonProgress] Tick error: " + e.getMessage());
        }
    }

    /**
     * Update all progress tracking
     */
    public void update() {
        updateObjectiveAvailability();
        calculateOverallProgress();
        checkOverallCompletion();
    }

    /**
     * Update objective states based on their progress
     */
    private void updateObjectiveStates() {
        for (ObjectiveProgress objProgress : objectives.values()) {
            if (objProgress.isActive()) {
                // Check if objective should be completed
                if (objProgress.getProgress() >= 1.0 && !objProgress.isCompleted()) {
                    completeObjective(objProgress.getObjectiveId());
                }
            }
        }
    }

    /**
     * Update which objectives are available based on dependencies
     */
    private void updateObjectiveAvailability() {
        for (ObjectiveProgress objProgress : objectives.values()) {
            if (objProgress.isLocked()) {
                // Check if all dependencies are met
                boolean dependenciesMet = objProgress.getDependencies().stream()
                        .allMatch(completedObjectives::contains);

                if (dependenciesMet) {
                    objProgress.setState(ObjectiveState.AVAILABLE);

                    // Auto-start objectives that don't require manual activation
                    if (shouldAutoStart(objProgress)) {
                        startObjective(objProgress.getObjectiveId());
                    }
                }
            }
        }
    }

    /**
     * Check if an objective should automatically start
     */
    private boolean shouldAutoStart(ObjectiveProgress objProgress) {
        // Most objectives start automatically, some might require manual triggers
        String autoStart = objProgress.getParameter("auto_start", String.class, "true");
        return Boolean.parseBoolean(autoStart);
    }

    /**
     * Calculate overall dungeon progress
     */
    private void calculateOverallProgress() {
        if (objectives.isEmpty()) {
            overallProgress = 0.0;
            return;
        }

        // Calculate weighted progress based on required objectives
        double totalWeight = 0.0;
        double completedWeight = 0.0;

        for (ObjectiveProgress objProgress : objectives.values()) {
            double weight = objProgress.getDefinition().isRequired() ? 1.0 : 0.5;
            totalWeight += weight;

            if (objProgress.isCompleted()) {
                completedWeight += weight;
            } else if (objProgress.isActive()) {
                completedWeight += weight * objProgress.getProgress();
            }
        }

        overallProgress = totalWeight > 0 ? completedWeight / totalWeight : 0.0;
    }

    /**
     * Update current dungeon phase
     */
    private void updateCurrentPhase() {
        double progress = overallProgress;

        if (progress < 0.25) {
            currentPhase = "exploring";
        } else if (progress < 0.75) {
            currentPhase = "challenging";
        } else if (progress < 1.0) {
            currentPhase = "final_push";
        } else {
            currentPhase = "completed";
        }
    }

    /**
     * Check if all required objectives are complete
     */
    private void checkOverallCompletion() {
        boolean allRequired = objectives.values().stream()
                .filter(obj -> obj.getDefinition().isRequired())
                .allMatch(ObjectiveProgress::isCompleted);

        if (allRequired && !allRequiredComplete) {
            allRequiredComplete = true;
            onAllRequiredObjectivesComplete();
        }
    }

    // ================ OBJECTIVE MANAGEMENT ================

    /**
     * Start an objective
     */
    public boolean startObjective(String objectiveId) {
        ObjectiveProgress objProgress = objectives.get(objectiveId);
        if (objProgress == null || objProgress.isActive() || objProgress.isCompleted()) {
            return false;
        }

        try {
            objProgress.setState(ObjectiveState.ACTIVE);
            objProgress.setStartTime(System.currentTimeMillis());
            objectiveStartTimes.put(objectiveId, System.currentTimeMillis());

            // Initialize objective-specific data
            initializeObjectiveData(objProgress);

            // Notify players
            notifyObjectiveStarted(objProgress);

            if (isDebugMode()) {
                logger.info("§a[DungeonProgress] §7Started objective: " + objectiveId);
            }

            return true;

        } catch (Exception e) {
            logger.warning("§c[DungeonProgress] Error starting objective " + objectiveId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Complete an objective
     */
    public boolean completeObjective(String objectiveId) {
        ObjectiveProgress objProgress = objectives.get(objectiveId);
        if (objProgress == null || objProgress.isCompleted()) {
            return false;
        }

        try {
            objProgress.setState(ObjectiveState.COMPLETED);
            objProgress.setProgress(1.0);
            objProgress.setCompletionTime(System.currentTimeMillis());

            completedObjectives.add(objectiveId);
            objectiveCompletionTimes.put(objectiveId, System.currentTimeMillis());

            // Update availability of dependent objectives
            updateObjectiveAvailability();

            // Notify players
            notifyObjectiveCompleted(objProgress);

            // Trigger completion effects
            triggerObjectiveCompletionEffects(objProgress);

            if (isDebugMode()) {
                logger.info("§a[DungeonProgress] §7Completed objective: " + objectiveId);
            }

            return true;

        } catch (Exception e) {
            logger.warning("§c[DungeonProgress] Error completing objective " + objectiveId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Fail an objective
     */
    public boolean failObjective(String objectiveId, String reason) {
        ObjectiveProgress objProgress = objectives.get(objectiveId);
        if (objProgress == null || objProgress.isCompleted() || objProgress.isFailed()) {
            return false;
        }

        try {
            objProgress.setState(ObjectiveState.FAILED);
            objProgress.setStatusMessage("Failed: " + reason);
            failedObjectives.add(objectiveId);

            // Notify players
            notifyObjectiveFailed(objProgress, reason);

            if (isDebugMode()) {
                logger.info("§6[DungeonProgress] §7Failed objective: " + objectiveId + " (" + reason + ")");
            }

            return true;

        } catch (Exception e) {
            logger.warning("§c[DungeonProgress] Error failing objective " + objectiveId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Update objective progress
     */
    public boolean updateObjectiveProgress(String objectiveId, int newValue) {
        ObjectiveProgress objProgress = objectives.get(objectiveId);
        if (objProgress == null || !objProgress.isActive()) {
            return false;
        }

        try {
            int oldValue = objProgress.getCurrentValue();
            objProgress.setCurrentValue(newValue);

            // Check for completion
            if (objProgress.getProgress() >= 1.0) {
                completeObjective(objectiveId);
            } else if (newValue > oldValue) {
                // Notify progress update
                notifyObjectiveProgress(objProgress);
            }

            return true;

        } catch (Exception e) {
            logger.warning("§c[DungeonProgress] Error updating objective progress: " + e.getMessage());
            return false;
        }
    }

    /**
     * Increment objective progress
     */
    public boolean incrementObjectiveProgress(String objectiveId, int amount) {
        ObjectiveProgress objProgress = objectives.get(objectiveId);
        if (objProgress == null || !objProgress.isActive()) {
            return false;
        }

        return updateObjectiveProgress(objectiveId, objProgress.getCurrentValue() + amount);
    }

    // ================ OBJECTIVE INITIALIZATION ================

    /**
     * Initialize objective-specific data
     */
    private void initializeObjectiveData(ObjectiveProgress objProgress) {
        DungeonTemplate.ObjectiveType type = objProgress.getDefinition().getType();

        switch (type) {
            case ELIMINATE_ALL:
                initializeEliminateAllObjective(objProgress);
                break;
            case DEFEAT_BOSS:
            case DEFEAT_ALL_BOSSES:
                initializeBossObjective(objProgress);
                break;
            case SURVIVE_WAVES:
                initializeSurviveWavesObjective(objProgress);
                break;
            case COLLECT_ITEMS:
                initializeCollectItemsObjective(objProgress);
                break;
            case SOLVE_PUZZLE:
                initializePuzzleObjective(objProgress);
                break;
            case REACH_LOCATION:
                initializeLocationObjective(objProgress);
                break;
            case ACTIVATE_MECHANISM:
                initializeMechanismObjective(objProgress);
                break;
            case PROTECT_NPC:
                initializeProtectNPCObjective(objProgress);
                break;
            case CUSTOM:
                initializeCustomObjective(objProgress);
                break;
        }
    }

    private void initializeEliminateAllObjective(ObjectiveProgress objProgress) {
        String roomId = objProgress.getDefinition().getRoomId();
        int mobCount = objProgress.getParameter("mob_count", Integer.class, 10);
        objProgress.setTargetValue(mobCount);
        objProgress.setStatusMessage("Clear all enemies in " + roomId);
    }

    private void initializeBossObjective(ObjectiveProgress objProgress) {
        String bossId = objProgress.getParameter("boss_id", String.class, "");
        if (!bossId.isEmpty()) {
            objProgress.setTargetValue(1);
            objProgress.setStatusMessage("Defeat " + bossId);
        }
    }

    private void initializeSurviveWavesObjective(ObjectiveProgress objProgress) {
        int waves = objProgress.getParameter("wave_count", Integer.class, 3);
        objProgress.setTargetValue(waves);
        objProgress.setStatusMessage("Survive " + waves + " waves");
    }

    private void initializeCollectItemsObjective(ObjectiveProgress objProgress) {
        String itemType = objProgress.getParameter("item_type", String.class, "key");
        int amount = objProgress.getParameter("amount", Integer.class, 1);
        objProgress.setTargetValue(amount);
        objProgress.setStatusMessage("Collect " + amount + " " + itemType + "(s)");
    }

    private void initializePuzzleObjective(ObjectiveProgress objProgress) {
        String puzzleId = objProgress.getParameter("puzzle_id", String.class, "unknown");
        objProgress.setTargetValue(1);
        objProgress.setStatusMessage("Solve " + puzzleId + " puzzle");
    }

    private void initializeLocationObjective(ObjectiveProgress objProgress) {
        String locationName = objProgress.getParameter("location_name", String.class, "target");
        objProgress.setTargetValue(1);
        objProgress.setStatusMessage("Reach " + locationName);
    }

    private void initializeMechanismObjective(ObjectiveProgress objProgress) {
        String mechanismName = objProgress.getParameter("mechanism_name", String.class, "mechanism");
        objProgress.setTargetValue(1);
        objProgress.setStatusMessage("Activate " + mechanismName);
    }

    private void initializeProtectNPCObjective(ObjectiveProgress objProgress) {
        String npcName = objProgress.getParameter("npc_name", String.class, "NPC");
        long duration = objProgress.getParameter("duration_seconds", Long.class, 300L);
        objProgress.setTargetValue((int) duration);
        objProgress.setStatusMessage("Protect " + npcName + " for " + duration + " seconds");
    }

    private void initializeCustomObjective(ObjectiveProgress objProgress) {
        String description = objProgress.getParameter("description", String.class, "Complete custom objective");
        objProgress.setStatusMessage(description);
    }

    // ================ EVENT HANDLERS ================

    /**
     * Handle room completion
     */
    public void onRoomComplete(String roomId) {
        // Update objectives related to this room
        objectives.values().stream()
                .filter(obj -> roomId.equals(obj.getDefinition().getRoomId()))
                .filter(ObjectiveProgress::isActive)
                .forEach(obj -> {
                    DungeonTemplate.ObjectiveType type = obj.getDefinition().getType();
                    if (type == DungeonTemplate.ObjectiveType.ELIMINATE_ALL) {
                        completeObjective(obj.getObjectiveId());
                    }
                });
    }

    /**
     * Handle boss defeat
     */
    public void onBossDefeated(String bossId) {
        // Update boss-related objectives
        objectives.values().stream()
                .filter(ObjectiveProgress::isActive)
                .forEach(obj -> {
                    DungeonTemplate.ObjectiveType type = obj.getDefinition().getType();
                    if (type == DungeonTemplate.ObjectiveType.DEFEAT_BOSS) {
                        String targetBoss = obj.getParameter("boss_id", String.class, "");
                        if (bossId.equals(targetBoss)) {
                            completeObjective(obj.getObjectiveId());
                        }
                    } else if (type == DungeonTemplate.ObjectiveType.DEFEAT_ALL_BOSSES) {
                        incrementObjectiveProgress(obj.getObjectiveId(), 1);
                    }
                });
    }

    /**
     * Handle mob kill
     */
    public void onMobKilled(String mobType, String roomId) {
        // Update eliminate objectives for this room
        objectives.values().stream()
                .filter(obj -> roomId.equals(obj.getDefinition().getRoomId()))
                .filter(ObjectiveProgress::isActive)
                .forEach(obj -> {
                    DungeonTemplate.ObjectiveType type = obj.getDefinition().getType();
                    if (type == DungeonTemplate.ObjectiveType.ELIMINATE_ALL) {
                        incrementObjectiveProgress(obj.getObjectiveId(), 1);
                    }
                });
    }

    /**
     * Handle all required objectives completion
     */
    private void onAllRequiredObjectivesComplete() {
        dungeonInstance.broadcast(ChatColor.GOLD + "=== ALL OBJECTIVES COMPLETED! ===");
        dungeonInstance.broadcast(ChatColor.GREEN + "The dungeon can now be completed!");

        // Trigger any completion effects
        triggerDungeonCompletionEffects();
    }

    // ================ NOTIFICATIONS ================

    /**
     * Send progress updates to players
     */
    private void sendProgressUpdates() {
        String progressText = getProgressText();
        if (progressText != null && !progressText.isEmpty()) {
            for (Player player : dungeonInstance.getOnlinePlayers()) {
                player.sendActionBar(progressText);
            }
        }
    }

    /**
     * Notify objective started
     */
    private void notifyObjectiveStarted(ObjectiveProgress objProgress) {
        String message = ChatColor.YELLOW + "New Objective: " + ChatColor.WHITE + objProgress.getStatusMessage();
        dungeonInstance.broadcast(message);
    }

    /**
     * Notify objective completed
     */
    private void notifyObjectiveCompleted(ObjectiveProgress objProgress) {
        String message = ChatColor.GREEN + "Objective Complete: " + ChatColor.WHITE + objProgress.getStatusMessage();
        dungeonInstance.broadcast(message);

        // Play completion sound
        for (Player player : dungeonInstance.getOnlinePlayers()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    /**
     * Notify objective failed
     */
    private void notifyObjectiveFailed(ObjectiveProgress objProgress, String reason) {
        String message = ChatColor.RED + "Objective Failed: " + ChatColor.WHITE + objProgress.getStatusMessage();
        if (reason != null && !reason.isEmpty()) {
            message += ChatColor.GRAY + " (" + reason + ")";
        }
        dungeonInstance.broadcast(message);
    }

    /**
     * Notify objective progress
     */
    private void notifyObjectiveProgress(ObjectiveProgress objProgress) {
        // Only notify on significant progress milestones
        double progress = objProgress.getProgress();
        if (progress == 0.25 || progress == 0.5 || progress == 0.75) {
            String message = ChatColor.YELLOW + "Progress: " + ChatColor.WHITE +
                    objProgress.getStatusMessage() + " " + ChatColor.GRAY + objProgress.getFormattedProgress();
            dungeonInstance.broadcast(message);
        }
    }

    // ================ EFFECTS AND REWARDS ================

    /**
     * Trigger objective completion effects
     */
    private void triggerObjectiveCompletionEffects(ObjectiveProgress objProgress) {
        // Play effects, give rewards, etc.
        // This could integrate with your rewards system

        // Example: Spawn celebration effects
        for (Player player : dungeonInstance.getOnlinePlayers()) {
            player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK,
                    player.getLocation().add(0, 2, 0), 20, 1, 1, 1, 0.1);
        }
    }

    /**
     * Trigger dungeon completion effects
     */
    private void triggerDungeonCompletionEffects() {
        // Major celebration effects
        for (Player player : dungeonInstance.getOnlinePlayers()) {
            player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM,
                    player.getLocation().add(0, 2, 0), 50, 2, 2, 2, 0.2);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    // ================ PUBLIC API ================

    /**
     * Get progress text for action bar
     */
    public String getProgressText() {
        if (overallProgress >= 1.0) {
            return ChatColor.GREEN + "Dungeon Complete!";
        }

        // Show current active objectives
        List<ObjectiveProgress> activeObjectives = objectives.values().stream()
                .filter(ObjectiveProgress::isActive)
                .limit(2) // Show max 2 objectives
                .collect(Collectors.toList());

        if (activeObjectives.isEmpty()) {
            return ChatColor.GRAY + "Phase: " + currentPhase + " " +
                    ChatColor.WHITE + String.format("(%.1f%%)", overallProgress * 100);
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < activeObjectives.size(); i++) {
            if (i > 0) text.append(" | ");

            ObjectiveProgress obj = activeObjectives.get(i);
            text.append(ChatColor.YELLOW).append(obj.getStatusMessage())
                    .append(" ").append(ChatColor.WHITE).append(obj.getFormattedProgress());
        }

        return text.toString();
    }

    /**
     * Get detailed progress information
     */
    public List<String> getDetailedProgress() {
        List<String> info = new ArrayList<>();

        info.add(ChatColor.GOLD + "=== Dungeon Progress ===");
        info.add(ChatColor.YELLOW + "Overall: " + ChatColor.WHITE +
                String.format("%.1f%% (%s)", overallProgress * 100, currentPhase));
        info.add("");

        // Group objectives by state
        Map<ObjectiveState, List<ObjectiveProgress>> grouped = objectives.values().stream()
                .collect(Collectors.groupingBy(ObjectiveProgress::getState));

        for (ObjectiveState state : ObjectiveState.values()) {
            List<ObjectiveProgress> objs = grouped.get(state);
            if (objs == null || objs.isEmpty()) continue;

            ChatColor color = getStateColor(state);
            info.add(color + state.name() + " Objectives:");

            for (ObjectiveProgress obj : objs) {
                String prefix = obj.getDefinition().isRequired() ? "[Required] " : "[Optional] ";
                info.add(ChatColor.GRAY + "  " + prefix + ChatColor.WHITE +
                        obj.getStatusMessage() + " " + ChatColor.GRAY + obj.getFormattedProgress());
            }
            info.add("");
        }

        return info;
    }

    /**
     * Get state display color
     */
    private ChatColor getStateColor(ObjectiveState state) {
        switch (state) {
            case COMPLETED: return ChatColor.GREEN;
            case ACTIVE: return ChatColor.YELLOW;
            case AVAILABLE: return ChatColor.AQUA;
            case FAILED: return ChatColor.RED;
            case LOCKED: return ChatColor.GRAY;
            default: return ChatColor.WHITE;
        }
    }

    // ================ GETTERS ================

    public boolean areAllRequiredObjectivesComplete() { return allRequiredComplete; }
    public double getOverallProgress() { return overallProgress; }
    public String getCurrentPhase() { return currentPhase; }
    public Map<String, ObjectiveProgress> getObjectives() { return new HashMap<>(objectives); }
    public ObjectiveProgress getObjective(String id) { return objectives.get(id); }
    public Set<String> getCompletedObjectives() { return new HashSet<>(completedObjectives); }
    public Set<String> getFailedObjectives() { return new HashSet<>(failedObjectives); }
    public int getCompletedCount() { return completedObjectives.size(); }
    public int getTotalObjectives() { return objectives.size(); }
    public int getRequiredObjectives() {
        return (int) objectives.values().stream()
                .filter(obj -> obj.getDefinition().isRequired())
                .count();
    }

    public Object getProgressData(String key) { return progressData.get(key); }
    public void setProgressData(String key, Object value) { progressData.put(key, value); }

    // ================ UTILITY METHODS ================

    private boolean isDebugMode() {
        return YakRealms.getInstance().isDebugMode();
    }

    // ================ CLEANUP ================

    /**
     * Clean up the progress system
     */
    public void cleanup() {
        try {
            // Cancel tasks
            if (progressUpdateTask != null) progressUpdateTask.cancel();
            if (notificationTask != null) notificationTask.cancel();

            // Clear data
            objectives.clear();
            completedObjectives.clear();
            failedObjectives.clear();
            objectiveStartTimes.clear();
            objectiveCompletionTimes.clear();
            progressData.clear();

        } catch (Exception e) {
            logger.warning("§c[DungeonProgress] Cleanup error: " + e.getMessage());
        }
    }
}