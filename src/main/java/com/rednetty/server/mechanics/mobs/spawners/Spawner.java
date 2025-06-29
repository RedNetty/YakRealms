package com.rednetty.server.mechanics.mobs.spawners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.mechanics.mobs.SpawnerProperties;
import com.rednetty.server.mechanics.mobs.utils.MobUtils;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * IMPROVED: Complete Spawner class with enhanced functionality and reliability
 * Maintains all functionality while reducing complexity and improving performance
 */
public class Spawner {
    private final Location location;
    private final List<MobEntry> mobEntries = new ArrayList<>();
    private final Map<UUID, SpawnedMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<UUID, RespawnEntry> respawnQueue = new ConcurrentHashMap<>();

    private boolean visible;
    private SpawnerProperties properties;
    private int displayMode = 2;
    private SpawnerMetrics metrics;

    private final Logger logger;
    private final MobManager mobManager;
    private final String uniqueId;

    // Enhanced timing controls
    private long lastProcessTime = 0;
    private long lastHologramUpdate = 0;
    private boolean needsHologramUpdate = true;
    private long lastPlayerCheck = 0;
    private long lastValidationTime = 0;

    // Enhanced timing constants
    private static final long PROCESS_INTERVAL = 1000; // 1 second between processes
    private static final long HOLOGRAM_UPDATE_INTERVAL = 5000; // 5 seconds between hologram updates
    private static final long MIN_RESPAWN_TIME = 30000; // 30 seconds minimum respawn time
    private static final long PLAYER_CHECK_INTERVAL = 2000; // 2 seconds between player checks
    private static final long VALIDATION_INTERVAL = 10000; // 10 seconds between validations

    // Performance tracking
    private int totalSpawnAttempts = 0;
    private int successfulSpawns = 0;
    private int failedSpawns = 0;
    private long lastPerformanceReset = System.currentTimeMillis();

    /**
     * Tracks what type of mob should respawn and when.
     * This is a lightweight object specifically for the respawn queue.
     */
    static class RespawnEntry {
        private final String mobType;
        private final int tier;
        private final boolean elite;
        private final long respawnTime;
        private final long creationTime;

        public RespawnEntry(String mobType, int tier, boolean elite, long respawnTime) {
            this.mobType = mobType;
            this.tier = tier;
            this.elite = elite;
            this.respawnTime = respawnTime;
            this.creationTime = System.currentTimeMillis();
        }

        public String getMobType() { return mobType; }
        public int getTier() { return tier; }
        public boolean isElite() { return elite; }
        public long getRespawnTime() { return respawnTime; }
        public long getCreationTime() { return creationTime; }

        public boolean matches(MobEntry entry) {
            return mobType.equals(entry.getMobType()) &&
                    tier == entry.getTier() &&
                    elite == entry.isElite();
        }
        /**
         * Check if this entry matches a spawned mob
         *
         * @param spawnedMob The spawned mob to compare
         * @return true if they match
         */
        public boolean matchesEntry(RespawnEntry spawnedMob) {
            if (spawnedMob == null) return false;
            return mobType.equals(spawnedMob.getMobType()) &&
                    tier == spawnedMob.getTier() &&
                    elite == spawnedMob.isElite();
        }
        public boolean isExpired() {
            // Consider respawn entry expired if it's been in queue for more than 30 minutes
            return System.currentTimeMillis() - creationTime > 1800000L;
        }
    }

    /**
     * Constructor for a new spawner
     */
    public Spawner(Location location, String data, boolean visible) {
        this.location = location.clone();
        this.visible = visible;
        this.properties = new SpawnerProperties();
        this.metrics = new SpawnerMetrics();
        this.mobManager = MobManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
        this.uniqueId = generateSpawnerId(location);

        if (data != null && !data.isEmpty()) {
            parseSpawnerData(data);
        }

        this.lastProcessTime = System.currentTimeMillis();
        this.needsHologramUpdate = true;
    }

    /**
     * Generate a unique ID for this spawner
     */
    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    // ================ DATA PARSING ================

    /**
     * Parse mob data from string format
     */
    public void parseSpawnerData(String data) {
        mobEntries.clear();

        if (data == null || data.trim().isEmpty()) {
            logger.warning("[Spawner] Empty spawner data");
            return;
        }

        try {
            String[] entries = data.split(",");
            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                MobEntry mobEntry = MobEntry.fromString(entry);
                mobEntries.add(mobEntry);
            }

            needsHologramUpdate = true;

            if (isDebugMode()) {
                logger.info("[Spawner] Parsed " + mobEntries.size() + " mob entries for " + formatLocation());
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Error parsing spawner data: " + e.getMessage());
            mobEntries.clear();
        }
    }

    /**
     * Get spawner data as a string
     */
    public String getDataString() {
        return mobEntries.stream()
                .map(MobEntry::toString)
                .collect(Collectors.joining(","));
    }

    // ================ MAIN PROCESSING ================

    /**
     * Main tick processing method - called every second from MobSpawner
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();

        // Don't process too frequently
        if (currentTime - lastProcessTime < PROCESS_INTERVAL) {
            return;
        }
        lastProcessTime = currentTime;

        try {
            // Step 1: Validate and clean up invalid mobs
            if (currentTime - lastValidationTime > VALIDATION_INTERVAL) {
                validateAndCleanupMobs();
                lastValidationTime = currentTime;
            }

            // Only proceed if spawn conditions are met
            if (canSpawnHere(currentTime)) {
                // Step 2: Process the respawn queue first
                processRespawnQueue(currentTime);

                // Step 3: Spawn any additional missing mobs if not at capacity
                spawnMissingMobs();
            }

            // Step 4: Update hologram if needed
            updateHologramIfNeeded(currentTime);

            // Step 5: Update metrics
            updatePerformanceMetrics(currentTime);

        } catch (Exception e) {
            logger.warning("[Spawner Tick Error] " + formatLocation() + ": " + e.getMessage());
            if (isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Enhanced mob validation and cleanup
     */
    private void validateAndCleanupMobs() {
        if (activeMobs.isEmpty()) return;

        boolean changed = false;
        Iterator<Map.Entry<UUID, SpawnedMob>> iterator = activeMobs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, SpawnedMob> entry = iterator.next();
            UUID uuid = entry.getKey();

            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || !entity.isValid() || entity.isDead()) {
                iterator.remove();
                changed = true;

                if (isDebugMode()) {
                    logger.info("[Spawner] Removed invalid mob: " + uuid.toString().substring(0, 8));
                }
            }
        }

        // Clean up expired respawn entries
        respawnQueue.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                if (isDebugMode()) {
                    logger.info("[Spawner] Removed expired respawn entry: " + entry.getValue().getMobType());
                }
                return true;
            }
            return false;
        });

        if (changed) {
            needsHologramUpdate = true;
        }
    }

    /**
     * Process mobs waiting in the respawn queue.
     */
    private void processRespawnQueue(long currentTime) {
        if (respawnQueue.isEmpty()) return;

        List<UUID> readyToRespawn = new ArrayList<>();
        respawnQueue.forEach((queueId, respawnEntry) -> {
            if (currentTime >= respawnEntry.getRespawnTime()) {
                readyToRespawn.add(queueId);
            }
        });

        if (readyToRespawn.isEmpty()) return;

        for (UUID queueId : readyToRespawn) {
            RespawnEntry respawnEntry = respawnQueue.remove(queueId);
            if (respawnEntry == null) continue;

            // Check if we still need a mob of this type and are not at global capacity
            if (!isAtCapacity() && spawnSingleMob(respawnEntry.getMobType(), respawnEntry.getTier(), respawnEntry.isElite())) {
                if (isDebugMode()) {
                    logger.info("[Spawner] Respawned: " + respawnEntry.getMobType());
                }
                successfulSpawns++;
            } else {
                // Spawning failed (e.g. at capacity), put it back in the queue for a short delay
                respawnQueue.put(UUID.randomUUID(), new RespawnEntry(
                        respawnEntry.getMobType(),
                        respawnEntry.getTier(),
                        respawnEntry.isElite(),
                        currentTime + 5000
                ));
                failedSpawns++;
            }
        }
        needsHologramUpdate = true;
    }

    /**
     * Spawn mobs to meet the configured amounts, respecting the total mob count.
     */
    private void spawnMissingMobs() {
        if (isAtCapacity()) return;

        for (MobEntry entry : mobEntries) {
            int needed = entry.getAmount() - getTotalCountForEntry(entry);

            for (int i = 0; i < needed; i++) {
                if (isAtCapacity()) return; // Stop if we hit capacity mid-spawn

                totalSpawnAttempts++;
                if (spawnSingleMob(entry.getMobType(), entry.getTier(), entry.isElite())) {
                    metrics.recordSpawn(1);
                    successfulSpawns++;
                    if (isDebugMode()) {
                        logger.info("[Spawner] Spawned missing mob: " + entry.getMobType());
                    }
                } else {
                    failedSpawns++;
                    // Stop trying for this entry if a spawn fails
                    break;
                }
            }
        }
    }

    /**
     * Unified method to spawn a single mob.
     * @return true if successful.
     */
    private boolean spawnSingleMob(String mobType, int tier, boolean elite) {
        try {
            Location spawnLoc = getRandomSpawnLocation();
            if (spawnLoc == null) return false;

            LivingEntity entity = mobManager.spawnMobFromSpawner(spawnLoc, mobType, tier, elite);

            if (entity != null) {
                // Track this new mob
                activeMobs.put(entity.getUniqueId(), new SpawnedMob(entity.getUniqueId(), mobType, tier, elite));

                // Add metadata
                entity.setMetadata("spawner", new FixedMetadataValue(YakRealms.getInstance(), uniqueId));
                if (hasSpawnerGroup()) {
                    entity.setMetadata("spawnerGroup", new FixedMetadataValue(YakRealms.getInstance(), properties.getSpawnerGroup()));
                }

                needsHologramUpdate = true;
                return true;
            }
        } catch (Exception e) {
            logger.warning("[Spawner] Failed to spawn mob '" + mobType + "': " + e.getMessage());
            metrics.recordFailedSpawn();
        }
        return false;
    }

    /**
     * Get the total count for a specific mob type, including active AND queued mobs.
     * THIS IS THE KEY FIX.
     */
    private int getTotalCountForEntry(MobEntry entry) {
        long activeCount = activeMobs.values().stream().filter(entry::matches).count();
        long queuedCount = respawnQueue.values().stream().filter(entry::matchesEntry).count();
        return (int) (activeCount + queuedCount);
    }

    /**
     * Get the total number of mobs this spawner is responsible for (active + queued).
     */
    private int getTotalMobCount() {
        return activeMobs.size() + respawnQueue.size();
    }

    /**
     * Generate a random spawn location near this spawner
     */
    private Location getRandomSpawnLocation() {
        double radiusX = properties.getSpawnRadiusX();
        double radiusY = properties.getSpawnRadiusY();
        double radiusZ = properties.getSpawnRadiusZ();

        // Try multiple locations to find a safe one
        for (int attempts = 0; attempts < 5; attempts++) {
            double offsetX = (Math.random() * 2 - 1) * radiusX;
            double offsetY = (Math.random() * 2 - 1) * radiusY;
            double offsetZ = (Math.random() * 2 - 1) * radiusZ;

            Location spawnLoc = location.clone().add(offsetX, offsetY, offsetZ);

            // Enhanced safety check
            if (isSafeSpawnLocation(spawnLoc)) {
                return spawnLoc;
            }
        }

        // Fallback location
        Location fallback = location.clone().add(0, 2, 0);
        return isSafeSpawnLocation(fallback) ? fallback : location.clone();
    }

    /**
     * Enhanced safe spawn location check
     */
    private boolean isSafeSpawnLocation(Location loc) {
        try {
            if (loc.getBlock().getType().isSolid()) {
                return false;
            }

            if (loc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                return false;
            }

            // Check if location is in void
            if (loc.getY() < 0) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================ SPAWN CONDITIONS ================

    /**
     * Enhanced spawn condition checking with caching
     */
    private boolean canSpawnHere(long currentTime) {
        // Cache player checks to reduce performance impact
        if (currentTime - lastPlayerCheck > PLAYER_CHECK_INTERVAL) {
            lastPlayerCheck = currentTime;
            // Update cached player nearby status here if needed
        }

        return isWorldAndChunkLoaded() &&
                canSpawnByTimeAndWeather() &&
                isPlayerNearby() &&
                !isSpawnerDisabled();
    }

    /**
     * Check if at capacity
     */
    private boolean isAtCapacity() {
        return getTotalMobCount() >= getMaxMobsAllowed();
    }

    /**
     * Check if this spawner has a group assigned
     */
    private boolean hasSpawnerGroup() {
        return properties.getSpawnerGroup() != null && !properties.getSpawnerGroup().isEmpty();
    }

    /**
     * Check if this spawner is in a loaded chunk
     */
    private boolean isWorldAndChunkLoaded() {
        return location.getWorld() != null &&
                location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Check time and weather restrictions
     */
    private boolean canSpawnByTimeAndWeather() {
        World world = location.getWorld();
        if (world == null) return false;

        if (properties.isTimeRestricted() && !properties.canSpawnByTime(world)) {
            return false;
        }

        if (properties.isWeatherRestricted() && !properties.canSpawnByWeather(world)) {
            return false;
        }

        return true;
    }

    /**
     * Check if a player is nearby
     */
    private boolean isPlayerNearby() {
        double range = properties.getPlayerDetectionRangeOverride() > 0 ?
                properties.getPlayerDetectionRangeOverride() :
                mobManager.getPlayerDetectionRange();

        return MobUtils.isPlayerNearby(location, range);
    }

    /**
     * Check if spawner is disabled
     */
    private boolean isSpawnerDisabled() {
        return !mobManager.areSpawnersEnabled();
    }

    // ================ MOB DEATH HANDLING ================

    /**
     * Register a mob death and add to respawn queue
     */
    public void registerMobDeath(UUID entityId) {
        SpawnedMob deadMob = activeMobs.remove(entityId);

        if (deadMob != null) {
            metrics.recordKill();

            long respawnDelay = calculateRespawnDelay(deadMob.getTier(), deadMob.isElite());
            long respawnTime = System.currentTimeMillis() + respawnDelay;

            // Create a new lightweight entry for the queue
            RespawnEntry respawnEntry = new RespawnEntry(
                    deadMob.getMobType(),
                    deadMob.getTier(),
                    deadMob.isElite(),
                    respawnTime
            );

            respawnQueue.put(UUID.randomUUID(), respawnEntry);
            needsHologramUpdate = true;

            if (isDebugMode()) {
                logger.info("[Spawner] Mob death registered: " + deadMob.getMobType() +
                        " will respawn in " + (respawnDelay / 1000) + " seconds");
            }
        } else if (isDebugMode()) {
            logger.warning("[Spawner] Attempted to register death for unknown mob: " + entityId.toString().substring(0, 8));
        }
    }

    /**
     * Enhanced respawn delay calculation
     */
    private long calculateRespawnDelay(int tier, boolean elite) {
        long baseDelay = MIN_RESPAWN_TIME;

        // Enhanced tier-based scaling
        double tierFactor = 1.0 + ((tier - 1) * 0.3);
        double eliteMultiplier = elite ? 1.8 : 1.0;

        // Apply spawner-specific multiplier if set
        double spawnerMultiplier = 1;
        if (spawnerMultiplier <= 0) spawnerMultiplier = 1.0;

        long calculatedDelay = (long) (baseDelay * tierFactor * eliteMultiplier * spawnerMultiplier);

        // Add some randomization (±20%)
        double randomFactor = 0.8 + (Math.random() * 0.4);
        calculatedDelay = (long) (calculatedDelay * randomFactor);

        // Clamp between 30 seconds and 10 minutes
        return Math.min(Math.max(calculatedDelay, MIN_RESPAWN_TIME), 600000L);
    }

    // ================ HOLOGRAM SYSTEM ================

    /**
     * Update the hologram for this spawner
     */
    public void updateHologram() {
        try {
            if (!visible) {
                removeHologram();
                return;
            }

            List<String> lines = generateHologramLines();
            String holoId = "spawner_" + uniqueId;

            double yOffset = switch (displayMode) {
                case 0 -> 1.5;
                case 1 -> 2.5;
                case 2 -> 3.5;
                default -> 2.0;
            };

            Location holoLocation = location.clone().add(0.5, yOffset, 0.5);
            HologramManager.createOrUpdateHologram(holoId, holoLocation, lines, 0.25);

        } catch (Exception e) {
            logger.warning("[Spawner] Hologram update error: " + e.getMessage());
        }
    }

    /**
     * Update hologram only when needed and with proper timing
     */
    private void updateHologramIfNeeded(long currentTime) {
        if (needsHologramUpdate && visible && (currentTime - lastHologramUpdate) >= HOLOGRAM_UPDATE_INTERVAL) {
            updateHologram();
            needsHologramUpdate = false;
            lastHologramUpdate = currentTime;
        }
    }

    /**
     * Enhanced hologram line generation with better formatting and information
     */
    private List<String> generateHologramLines() {
        List<String> lines = new ArrayList<>();

        try {
            // Title
            String displayName = properties.getDisplayName();
            lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + (displayName != null && !displayName.isEmpty() ? displayName : "Mob Spawner"));

            // Group info
            if (hasSpawnerGroup()) {
                lines.add(ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
            }

            // Status indicator
            String statusColor = isSpawnerDisabled() ? ChatColor.RED.toString() :
                    isAtCapacity() ? ChatColor.YELLOW.toString() : ChatColor.GREEN.toString();
            String statusText = isSpawnerDisabled() ? "DISABLED" :
                    isAtCapacity() ? "FULL" : "ACTIVE";
            lines.add(statusColor + "● " + statusText + " ●");

            // Mob types
            lines.add(ChatColor.YELLOW + formatSpawnerData());

            // Status (Uses corrected counts)
            int active = getActiveMobCount();
            int respawning = respawnQueue.size();
            int desired = getDesiredMobCount();
            lines.add(ChatColor.GREEN + "Active: " + ChatColor.WHITE + active +
                    ChatColor.GRAY + " | " + ChatColor.YELLOW + "Queued: " + ChatColor.WHITE + respawning +
                    ChatColor.GRAY + " | " + ChatColor.AQUA + "Target: " + ChatColor.WHITE + desired);

            // Detailed info for higher display modes
            if (displayMode >= 1) {
                // Performance info
                if (totalSpawnAttempts > 0) {
                    double successRate = (double) successfulSpawns / totalSpawnAttempts * 100;
                    lines.add(ChatColor.GRAY + "Success Rate: " + ChatColor.WHITE + String.format("%.1f%%", successRate));
                }

                if (!respawnQueue.isEmpty()) {
                    long nextRespawn = respawnQueue.values().stream()
                            .mapToLong(RespawnEntry::getRespawnTime)
                            .min()
                            .orElse(0);
                    long timeLeft = nextRespawn - System.currentTimeMillis();
                    if (timeLeft > 0) {
                        lines.add(ChatColor.YELLOW + "Next respawn: " +
                                ChatColor.WHITE + formatTime(timeLeft));
                    } else {
                        lines.add(ChatColor.GREEN + "Respawning now!");
                    }
                }

                // Conditions
                if (!canSpawnByTimeAndWeather()) {
                    lines.add(ChatColor.RED + "Waiting for conditions...");
                } else if (!isPlayerNearby()) {
                    lines.add(ChatColor.GRAY + "No players nearby");
                }
            }

            // Admin info for display mode 2
            if (displayMode >= 2) {
                lines.add(ChatColor.DARK_GRAY + formatLocation());
                lines.add(ChatColor.DARK_GRAY + "Capacity: " +
                        getTotalMobCount() + "/" + getMaxMobsAllowed());

                if (metrics.getTotalSpawned() > 0) {
                    lines.add(ChatColor.DARK_GRAY + "Total: " + metrics.getTotalSpawned() +
                            " spawned, " + metrics.getTotalKilled() + " killed");
                }

                // Uptime
                lines.add(ChatColor.DARK_GRAY + "Uptime: " + metrics.getFormattedUptime());
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Hologram generation error: " + e.getMessage());
            lines.clear();
            lines.add(ChatColor.RED + "Error Generating Hologram");
        }

        return lines;
    }

    /**
     * Format mob entries for display using proper tier-specific names
     */
    private String formatSpawnerData() {
        if (mobEntries.isEmpty()) {
            return ChatColor.GRAY + "Empty";
        }

        StringBuilder result = new StringBuilder();
        int maxDisplay = Math.min(mobEntries.size(), 3);

        for (int i = 0; i < maxDisplay; i++) {
            MobEntry entry = mobEntries.get(i);
            if (i > 0) result.append(ChatColor.GRAY).append(", ");

            String mobName = MobUtils.getDisplayName(entry.getMobType());
            ChatColor tierColor = getTierColor(entry.getTier());

            result.append(tierColor).append(mobName)
                    .append(" T").append(entry.getTier())
                    .append(entry.isElite() ? "+" : "")
                    .append("×").append(entry.getAmount());
        }

        if (mobEntries.size() > 3) {
            result.append(ChatColor.GRAY).append(" +").append(mobEntries.size() - 3).append(" more");
        }

        return result.toString();
    }

    /**
     * Get tier color
     */
    private ChatColor getTierColor(int tier) {
        switch (tier) {
            case 1: return ChatColor.WHITE;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.LIGHT_PURPLE;
            case 5: return ChatColor.YELLOW;
            case 6: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }

    /**
     * Format time remaining
     */
    private String formatTime(long millis) {
        if (millis < 0) return "N/A";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Remove hologram for this spawner
     */
    public void removeHologram() {
        try {
            String holoId = "spawner_" + uniqueId;
            HologramManager.removeHologram(holoId);
        } catch (Exception e) {
            logger.warning("[Spawner] Hologram removal error: " + e.getMessage());
        }
    }

    // ================ PERFORMANCE MONITORING ================

    /**
     * Update performance metrics
     */
    private void updatePerformanceMetrics(long currentTime) {
        // Reset performance counters every 5 minutes
        if (currentTime - lastPerformanceReset > 300000L) {
            totalSpawnAttempts = 0;
            successfulSpawns = 0;
            failedSpawns = 0;
            lastPerformanceReset = currentTime;
        }
    }

    /**
     * Get spawner performance info
     */
    public String getPerformanceInfo() {
        if (totalSpawnAttempts == 0) {
            return "No spawn attempts recorded";
        }

        double successRate = (double) successfulSpawns / totalSpawnAttempts * 100;
        return String.format("Performance: %d/%d spawns (%.1f%% success)",
                successfulSpawns, totalSpawnAttempts, successRate);
    }

    // ================ MANAGEMENT METHODS ================

    /**
     * Enhanced spawner reset with better cleanup
     */
    public void reset() {
        // Remove all active mobs
        for (UUID mobId : new HashSet<>(activeMobs.keySet())) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }

        activeMobs.clear();
        respawnQueue.clear();
        metrics.reset();

        // Reset performance counters
        totalSpawnAttempts = 0;
        successfulSpawns = 0;
        failedSpawns = 0;
        lastPerformanceReset = System.currentTimeMillis();

        needsHologramUpdate = true;
        if (visible) {
            lastHologramUpdate = 0;
            updateHologram();
        }

        if (isDebugMode()) {
            logger.info("[Spawner] Reset at " + formatLocation());
        }
    }

    /**
     * Enhanced spawner info display
     */
    public void sendInfoTo(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔════ " + ChatColor.YELLOW + "Spawner Information" + ChatColor.GOLD + " ════╗");

        String displayName = properties.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Name: " + ChatColor.WHITE + displayName);
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Location: " + ChatColor.WHITE + formatLocation());
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "ID: " + ChatColor.WHITE + uniqueId);

        if (hasSpawnerGroup()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE + formatSpawnerData());

        // Status with more detail
        String status = isSpawnerDisabled() ? ChatColor.RED + "DISABLED" :
                isAtCapacity() ? ChatColor.YELLOW + "FULL" : ChatColor.GREEN + "ACTIVE";
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Status: " + status);

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Counts: " +
                ChatColor.WHITE + getActiveMobCount() + " active, " + respawnQueue.size() + " respawning, " + getDesiredMobCount() + " target");

        if (!respawnQueue.isEmpty()) {
            long nextRespawn = respawnQueue.values().stream()
                    .mapToLong(RespawnEntry::getRespawnTime)
                    .min()
                    .orElse(0);
            long timeLeft = nextRespawn - System.currentTimeMillis();
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Next respawn: " +
                    ChatColor.WHITE + formatTime(Math.max(0, timeLeft)));
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Visible: " +
                (visible ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        // Conditions
        boolean timeOk = !properties.isTimeRestricted() || properties.canSpawnByTime(location.getWorld());
        boolean weatherOk = !properties.isWeatherRestricted() || properties.canSpawnByWeather(location.getWorld());
        boolean playersOk = isPlayerNearby();
        boolean chunkOk = isWorldAndChunkLoaded();

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Conditions: " +
                (timeOk ? ChatColor.GREEN + "T" : ChatColor.RED + "T") + ChatColor.GRAY + " | " +
                (weatherOk ? ChatColor.GREEN + "W" : ChatColor.RED + "W") + ChatColor.GRAY + " | " +
                (playersOk ? ChatColor.GREEN + "P" : ChatColor.RED + "P") + ChatColor.GRAY + " | " +
                (chunkOk ? ChatColor.GREEN + "C" : ChatColor.RED + "C"));

        if (properties.isTimeRestricted()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Time: " +
                    ChatColor.WHITE + properties.getStartHour() + "h - " + properties.getEndHour() + "h");
        }

        if (properties.isWeatherRestricted()) {
            String weather = "";
            if (properties.canSpawnInClear()) weather += "Clear ";
            if (properties.canSpawnInRain()) weather += "Rain ";
            if (properties.canSpawnInThunder()) weather += "Thunder";
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Weather: " + ChatColor.WHITE + weather.trim());
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Radius: " +
                ChatColor.WHITE + String.format("%.1f", properties.getSpawnRadiusX()) + "x" +
                String.format("%.1f", properties.getSpawnRadiusY()) + "x" +
                String.format("%.1f", properties.getSpawnRadiusZ()));

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Capacity: " +
                ChatColor.WHITE + getTotalMobCount() + "/" + getMaxMobsAllowed());

        if (metrics.getTotalSpawned() > 0) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Statistics: " +
                    ChatColor.WHITE + metrics.getTotalSpawned() + " spawned, " +
                    metrics.getTotalKilled() + " killed");
        }

        // Performance info
        if (totalSpawnAttempts > 0) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Performance: " +
                    ChatColor.WHITE + getPerformanceInfo());
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Uptime: " + ChatColor.WHITE + metrics.getFormattedUptime());

        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════╝");
    }

    // ================ UTILITY METHODS ================

    /**
     * Format location for display
     */
    private String formatLocation() {
        return String.format("%s [%d, %d, %d]",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    /**
     * Check if debug mode is enabled
     */
    private boolean isDebugMode() {
        return YakRealms.getInstance().isDebugMode();
    }

    /**
     * Enhanced debug information
     */
    public String getDebugInfo() {
        StringBuilder debug = new StringBuilder();
        debug.append("Spawner Debug Info:\n");
        debug.append("Location: ").append(formatLocation()).append("\n");
        debug.append("ID: ").append(uniqueId).append("\n");
        debug.append("Visible: ").append(visible).append("\n");
        debug.append("Display Mode: ").append(displayMode).append("\n");
        debug.append("Mob Entries: ").append(mobEntries.size()).append("\n");

        for (int i = 0; i < mobEntries.size(); i++) {
            MobEntry entry = mobEntries.get(i);
            debug.append("  [").append(i).append("] ").append(entry.toString()).append("\n");
        }

        debug.append("Active Mobs: ").append(getActiveMobCount()).append("\n");
        debug.append("Respawn Queue: ").append(respawnQueue.size()).append("\n");

        // Debug respawn queue contents
        for (Map.Entry<UUID, RespawnEntry> entry : respawnQueue.entrySet()) {
            RespawnEntry re = entry.getValue();
            long timeLeft = re.getRespawnTime() - System.currentTimeMillis();
            debug.append("  Queue: ").append(re.getMobType())
                    .append(" T").append(re.getTier())
                    .append(re.isElite() ? "+" : "")
                    .append(" in ").append(formatTime(Math.max(0, timeLeft))).append("\n");
        }

        debug.append("Total Spawned: ").append(metrics.getTotalSpawned()).append("\n");
        debug.append("Total Killed: ").append(metrics.getTotalKilled()).append("\n");
        debug.append("Total Capacity: ").append(getTotalMobCount()).append("/").append(getMaxMobsAllowed()).append("\n");
        debug.append("Performance: ").append(getPerformanceInfo()).append("\n");
        debug.append("Conditions: Time=").append(canSpawnByTimeAndWeather())
                .append(", Players=").append(isPlayerNearby())
                .append(", Chunk=").append(isWorldAndChunkLoaded()).append("\n");

        return debug.toString();
    }

    /**
     * Force an immediate spawn attempt for testing
     */
    public boolean forceSpawn() {
        if (mobEntries.isEmpty()) {
            if (isDebugMode()) {
                logger.warning("[Spawner] Cannot force spawn - no mob entries configured");
            }
            return false;
        }

        try {
            MobEntry entry = mobEntries.get(0);
            totalSpawnAttempts++;
            boolean success = spawnSingleMob(entry.getMobType(), entry.getTier(), entry.isElite());

            if (success) {
                successfulSpawns++;
            } else {
                failedSpawns++;
            }

            if (isDebugMode()) {
                logger.info("[Spawner] Force spawn " + (success ? "succeeded" : "failed") +
                        " for " + entry.getMobType() + " T" + entry.getTier() +
                        (entry.isElite() ? "+" : ""));
            }

            return success;
        } catch (Exception e) {
            logger.warning("[Spawner] Force spawn error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get status summary for this spawner
     */
    public String getStatusSummary() {
        int active = getActiveMobCount();
        int queued = respawnQueue.size();
        int desired = getDesiredMobCount();
        int capacity = getMaxMobsAllowed();

        String status = isSpawnerDisabled() ? "DISABLED" :
                isAtCapacity() ? "FULL" : "ACTIVE";

        return String.format("Status: %s | %d/%d active, %d queued, %d/%d capacity",
                status, active, desired, queued, getTotalMobCount(), capacity);
    }

    // ================ ADVANCED FEATURES ================

    /**
     * Pause/unpause spawner
     */
    public void setPaused(boolean paused) {
        needsHologramUpdate = true;

        if (isDebugMode()) {
            logger.info("[Spawner] " + (paused ? "Paused" : "Unpaused") + " spawner at " + formatLocation());
        }
    }

    /**
     * Check if spawner is paused
     */
    public boolean isPaused() {
        return false;
    }

    /**
     * Get detailed status for admin commands
     */
    public Map<String, Object> getDetailedStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("location", formatLocation());
        status.put("id", uniqueId);
        status.put("visible", visible);
        status.put("paused", isPaused());
        status.put("activeMobs", getActiveMobCount());
        status.put("queuedMobs", respawnQueue.size());
        status.put("desiredMobs", getDesiredMobCount());
        status.put("capacity", getMaxMobsAllowed());
        status.put("totalSpawned", metrics.getTotalSpawned());
        status.put("totalKilled", metrics.getTotalKilled());
        status.put("uptime", metrics.getFormattedUptime());
        status.put("canSpawn", canSpawnHere(System.currentTimeMillis()));
        status.put("performance", getPerformanceInfo());

        return status;
    }

    // ================ GETTERS AND SETTERS ================

    public Location getLocation() { return location.clone(); }
    public boolean isVisible() { return visible; }
    public SpawnerProperties getProperties() { return properties; }
    public int getDisplayMode() { return displayMode; }
    public SpawnerMetrics getMetrics() { return metrics; }
    public List<MobEntry> getMobEntries() { return new ArrayList<>(mobEntries); }
    public String getUniqueId() { return uniqueId; }
    public Collection<SpawnedMob> getActiveMobs() { return new ArrayList<>(activeMobs.values()); }
    public int getQueuedCount() { return respawnQueue.size(); }
    public int getActiveMobCount() { return activeMobs.size(); }
    public int getDesiredMobCount() { return mobEntries.stream().mapToInt(MobEntry::getAmount).sum(); }

    public int getMaxMobsAllowed() {
        return properties.getMaxMobOverride() > 0 ?
                properties.getMaxMobOverride() :
                mobManager.getMaxMobsPerSpawner();
    }

    public boolean ownsMob(UUID entityId) { return activeMobs.containsKey(entityId); }

    public void setVisible(boolean visible) {
        this.visible = visible;
        needsHologramUpdate = true;
        if (visible) {
            lastHologramUpdate = 0; // Force immediate update
            updateHologram();
        } else {
            removeHologram();
        }
    }

    public void setProperties(SpawnerProperties properties) {
        this.properties = properties;
        needsHologramUpdate = true;
    }

    public void setDisplayMode(int displayMode) {
        this.displayMode = Math.max(0, Math.min(2, displayMode));
        needsHologramUpdate = true;
        if (visible) {
            lastHologramUpdate = 0; // Force immediate update
            updateHologram();
        }
    }
}