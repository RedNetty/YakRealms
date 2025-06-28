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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FIXED: Completely rewritten Spawner class with reliable, simple logic
 * No complex state management, just straightforward spawning and respawning
 */
public class Spawner {
    private final Location location;
    private final List<MobEntry> mobEntries = new ArrayList<>();
    private final Map<UUID, SpawnedMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> respawnQueue = new ConcurrentHashMap<>();

    private boolean visible;
    private SpawnerProperties properties;
    private int displayMode = 2;
    private SpawnerMetrics metrics;

    private final Logger logger;
    private final MobManager mobManager;
    private final String uniqueId;

    // FIXED: Simple timing and state tracking
    private long lastProcessTime = 0;
    private long lastHologramUpdate = 0;
    private boolean needsHologramUpdate = true;

    // Timing constants
    private static final long PROCESS_INTERVAL = 1000; // 1 second between processes
    private static final long HOLOGRAM_UPDATE_INTERVAL = 5000; // 5 seconds between hologram updates
    private static final long MIN_RESPAWN_TIME = 30000; // 30 seconds minimum respawn time

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

        // Parse mob data
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

    /**
     * FIXED: Simplified mob data parsing
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

    /**
     * FIXED: Main processing method - called every tick from MobSpawner
     * Simple, reliable logic with proper timing
     */
    public void processTick() {
        long currentTime = System.currentTimeMillis();

        // Don't process too frequently
        if (currentTime - lastProcessTime < PROCESS_INTERVAL) {
            return;
        }

        try {
            // Step 1: Clean up invalid mobs
            cleanupInvalidMobs();

            // Step 2: Process respawn queue
            processRespawnQueue(currentTime);

            // Step 3: Check if we need to spawn more mobs
            if (shouldSpawnMobs()) {
                spawnMissingMobs();
            }

            // Step 4: Update hologram if needed
            updateHologramIfNeeded(currentTime);

            this.lastProcessTime = currentTime;

        } catch (Exception e) {
            logger.warning("[Spawner] Error processing tick: " + e.getMessage());
            if (isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * FIXED: Simple cleanup - remove invalid mob references
     */
    private void cleanupInvalidMobs() {
        Set<UUID> toRemove = new HashSet<>();

        for (UUID uuid : activeMobs.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || !entity.isValid() || entity.isDead()) {
                toRemove.add(uuid);
            }
        }

        if (!toRemove.isEmpty()) {
            for (UUID uuid : toRemove) {
                activeMobs.remove(uuid);
            }
            needsHologramUpdate = true;

            if (isDebugMode()) {
                logger.info("[Spawner] Cleaned up " + toRemove.size() + " invalid mobs");
            }
        }
    }

    /**
     * FIXED: Simple respawn queue processing
     */
    private void processRespawnQueue(long currentTime) {
        if (respawnQueue.isEmpty()) return;

        List<UUID> readyToRespawn = new ArrayList<>();

        // Find mobs ready to respawn
        for (Map.Entry<UUID, Long> entry : respawnQueue.entrySet()) {
            if (currentTime >= entry.getValue()) {
                readyToRespawn.add(entry.getKey());
            }
        }

        // Attempt to respawn ready mobs
        for (UUID deadMobId : readyToRespawn) {
            respawnQueue.remove(deadMobId);

            if (canSpawnHere() && !isAtCapacity()) {
                attemptRespawn();
            } else {
                // Put back in queue for later
                respawnQueue.put(deadMobId, currentTime + 10000); // Try again in 10 seconds
            }
        }

        if (!readyToRespawn.isEmpty()) {
            needsHologramUpdate = true;
        }
    }

    /**
     * FIXED: Simple check if we should spawn mobs
     */
    private boolean shouldSpawnMobs() {
        return canSpawnHere() &&
                !isAtCapacity() &&
                getCurrentMobCount() < getDesiredMobCount();
    }

    /**
     * FIXED: Simple mob spawning logic
     */
    private void spawnMissingMobs() {
        int maxToSpawn = getDesiredMobCount() - getCurrentMobCount();
        int spawned = 0;

        for (MobEntry entry : mobEntries) {
            if (spawned >= maxToSpawn) break;

            int currentCount = getCurrentCountForEntry(entry);
            int needed = entry.getAmount() - currentCount;

            for (int i = 0; i < needed && spawned < maxToSpawn; i++) {
                if (spawnSingleMob(entry)) {
                    spawned++;
                } else {
                    break; // Stop if spawning fails
                }
            }
        }

        if (spawned > 0) {
            metrics.recordSpawn(spawned);
            needsHologramUpdate = true;

            if (isDebugMode()) {
                logger.info("[Spawner] Spawned " + spawned + " mobs at " + formatLocation());
            }
        }
    }

    /**
     * FIXED: Simple single mob spawning
     */
    private boolean spawnSingleMob(MobEntry entry) {
        try {
            // Get spawn location
            Location spawnLoc = getRandomSpawnLocation();

            // FIXED: Use MobManager's reliable spawn method
            LivingEntity entity = mobManager.spawnMobFromSpawner(
                    spawnLoc,
                    entry.getMobType(),
                    entry.getTier(),
                    entry.isElite()
            );

            if (entity != null) {
                // Track this mob
                SpawnedMob spawnedMob = new SpawnedMob(
                        entity.getUniqueId(),
                        entry.getMobType(),
                        entry.getTier(),
                        entry.isElite()
                );

                activeMobs.put(entity.getUniqueId(), spawnedMob);

                // Add spawner metadata
                entity.setMetadata("spawner", new FixedMetadataValue(YakRealms.getInstance(), uniqueId));

                // Add group metadata if needed
                if (hasSpawnerGroup()) {
                    entity.setMetadata("spawnerGroup", new FixedMetadataValue(
                            YakRealms.getInstance(), properties.getSpawnerGroup()));
                }

                return true;
            }
        } catch (Exception e) {
            logger.warning("[Spawner] Failed to spawn mob: " + e.getMessage());
            metrics.recordFailedSpawn();
        }

        return false;
    }

    /**
     * FIXED: Simple respawn attempt
     */
    private void attemptRespawn() {
        // Find the first mob entry that needs more mobs
        for (MobEntry entry : mobEntries) {
            int currentCount = getCurrentCountForEntry(entry);
            if (currentCount < entry.getAmount()) {
                spawnSingleMob(entry);
                break; // Only spawn one at a time
            }
        }
    }

    /**
     * Get current count of mobs for a specific entry
     */
    private int getCurrentCountForEntry(MobEntry entry) {
        int count = 0;

        // Count active mobs
        for (SpawnedMob mob : activeMobs.values()) {
            if (mob.matches(entry)) {
                count++;
            }
        }

        // Count respawning mobs (simplified - just count queue entries)
        count += (int) respawnQueue.values().stream().count();

        return count;
    }

    /**
     * Generate a random spawn location near this spawner
     */
    private Location getRandomSpawnLocation() {
        double radiusX = properties.getSpawnRadiusX();
        double radiusY = properties.getSpawnRadiusY();
        double radiusZ = properties.getSpawnRadiusZ();

        double offsetX = (Math.random() * 2 - 1) * radiusX;
        double offsetY = (Math.random() * 2 - 1) * radiusY;
        double offsetZ = (Math.random() * 2 - 1) * radiusZ;

        Location spawnLoc = location.clone().add(offsetX, offsetY, offsetZ);

        // Simple safety check
        if (spawnLoc.getBlock().getType().isSolid()) {
            spawnLoc.add(0, 2, 0); // Move up if blocked
        }

        return spawnLoc;
    }

    /**
     * FIXED: Simple spawn condition checks
     */
    private boolean canSpawnHere() {
        return isWorldAndChunkLoaded() &&
                canSpawnByTimeAndWeather() &&
                isPlayerNearby();
    }

    /**
     * Check if at capacity
     */
    private boolean isAtCapacity() {
        return getCurrentMobCount() >= getMaxMobsAllowed();
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
     * FIXED: Register mob death and add to respawn queue
     */
    public void registerMobDeath(UUID entityId) {
        SpawnedMob mob = activeMobs.remove(entityId);

        if (mob != null) {
            // Record kill in metrics
            metrics.recordKill();

            // Calculate respawn time
            long respawnDelay = calculateRespawnDelay(mob.getTier(), mob.isElite());
            long respawnTime = System.currentTimeMillis() + respawnDelay;

            // Add to respawn queue
            respawnQueue.put(UUID.randomUUID(), respawnTime);

            needsHologramUpdate = true;

            if (isDebugMode()) {
                logger.info("[Spawner] Mob death registered: " + mob.getMobType() +
                        " T" + mob.getTier() + (mob.isElite() ? "+" : "") +
                        " will respawn in " + (respawnDelay / 1000) + " seconds");
            }
        }
    }

    /**
     * Calculate respawn delay for a mob
     */
    private long calculateRespawnDelay(int tier, boolean elite) {
        // Base delay
        long baseDelay = MIN_RESPAWN_TIME;

        // Tier factor
        double tierFactor = 1.0 + ((tier - 1) * 0.5);

        // Elite multiplier
        double eliteMultiplier = elite ? 2.0 : 1.0;

        long calculatedDelay = (long) (baseDelay * tierFactor * eliteMultiplier);

        // Add randomness (±20%)
        double randomFactor = 0.8 + (Math.random() * 0.4);
        calculatedDelay = (long) (calculatedDelay * randomFactor);

        // Clamp to reasonable values
        return Math.min(Math.max(calculatedDelay, MIN_RESPAWN_TIME), 300000L); // Max 5 minutes
    }

    /**
     * FIXED: Simple hologram update with proper timing
     */
    public void updateHologram() {
        try {
            if (!visible) {
                removeHologram();
                return;
            }

            List<String> lines = generateHologramLines();
            String holoId = "spawner_" + uniqueId;

            // Calculate height based on display mode
            double yOffset = switch (displayMode) {
                case 0 -> 1.5; // Basic
                case 1 -> 2.5; // Detailed
                case 2 -> 3.5; // Admin
                default -> 2.0;
            };

            Location holoLocation = location.clone().add(0, yOffset, 0);

            HologramManager.createOrUpdateHologram(holoId, holoLocation, lines, 0.25);

            if (isDebugMode()) {
                logger.info("[Spawner] Updated hologram at " + formatLocation());
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Error updating hologram: " + e.getMessage());
        }
    }

    /**
     * FIXED: Update hologram only when needed and with proper timing
     */
    private void updateHologramIfNeeded(long currentTime) {
        if (needsHologramUpdate && visible &&
                (currentTime - lastHologramUpdate) >= HOLOGRAM_UPDATE_INTERVAL) {

            updateHologram();
            needsHologramUpdate = false;
            lastHologramUpdate = currentTime;
        }
    }

    /**
     * FIXED: Generate hologram lines with simpler logic
     */
    private List<String> generateHologramLines() {
        List<String> lines = new ArrayList<>();

        try {
            // Title
            String displayName = properties.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + displayName);
            } else {
                lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Spawner");
            }

            // Group info
            if (hasSpawnerGroup()) {
                lines.add(ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
            }

            // Mob types
            lines.add(ChatColor.YELLOW + formatSpawnerData());

            // Status
            int active = getCurrentMobCount();
            int respawning = respawnQueue.size();
            int desired = getDesiredMobCount();

            lines.add(ChatColor.GREEN + "Active: " + ChatColor.WHITE + active +
                    ChatColor.GRAY + " | Queue: " + ChatColor.WHITE + respawning +
                    ChatColor.GRAY + " | Target: " + ChatColor.WHITE + desired);

            // Detailed info for higher display modes
            if (displayMode >= 1) {
                if (metrics.getTotalSpawned() > 0) {
                    lines.add(ChatColor.GRAY + "Total: " + metrics.getTotalSpawned() +
                            " spawned, " + metrics.getTotalKilled() + " killed");
                }

                // Next respawn info
                if (!respawnQueue.isEmpty()) {
                    long nextRespawn = Collections.min(respawnQueue.values());
                    long timeLeft = nextRespawn - System.currentTimeMillis();
                    if (timeLeft > 0) {
                        lines.add(ChatColor.YELLOW + "Next respawn: " +
                                ChatColor.WHITE + formatTime(timeLeft));
                    } else {
                        lines.add(ChatColor.GREEN + "Respawning now!");
                    }
                }
            }

            // Admin info for display mode 2
            if (displayMode >= 2) {
                lines.add(ChatColor.DARK_GRAY + formatLocation());
                lines.add(ChatColor.DARK_GRAY + "Capacity: " +
                        (active + respawning) + "/" + getMaxMobsAllowed());
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Error generating hologram lines: " + e.getMessage());
            lines.clear();
            lines.add(ChatColor.RED + "Error");
        }

        return lines;
    }

    /**
     * Format mob entries for display
     */
    private String formatSpawnerData() {
        if (mobEntries.isEmpty()) {
            return ChatColor.GRAY + "Empty";
        }

        StringBuilder result = new StringBuilder();
        int maxDisplay = Math.min(mobEntries.size(), 3);

        for (int i = 0; i < maxDisplay; i++) {
            MobEntry entry = mobEntries.get(i);
            if (i > 0) result.append(", ");

            String mobName = formatMobName(entry.getMobType());
            result.append(mobName)
                    .append(" T").append(entry.getTier())
                    .append(entry.isElite() ? "+" : "")
                    .append("×").append(entry.getAmount());
        }

        if (mobEntries.size() > 3) {
            result.append(" +").append(mobEntries.size() - 3).append(" more");
        }

        return result.toString();
    }

    /**
     * Format mob name for display
     */
    private String formatMobName(String type) {
        if (type == null || type.isEmpty()) return "Unknown";

        switch (type.toLowerCase()) {
            case "witherskeleton":
                return "Wither Skeleton";
            case "cavespider":
                return "Cave Spider";
            case "magmacube":
                return "Magma Cube";
            default:
                return type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
        }
    }

    /**
     * Format time remaining
     */
    private String formatTime(long millis) {
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
            logger.warning("[Spawner] Error removing hologram: " + e.getMessage());
        }
    }

    /**
     * Reset this spawner's state
     */
    public void reset() {
        activeMobs.clear();
        respawnQueue.clear();
        metrics.reset();
        needsHologramUpdate = true;

        if (visible) {
            // Force immediate hologram update on reset
            lastHologramUpdate = 0;
            updateHologram();
        }

        if (isDebugMode()) {
            logger.info("[Spawner] Reset at " + formatLocation());
        }
    }

    /**
     * Send detailed spawner info to a player
     */
    public void sendInfoTo(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔════ " + ChatColor.YELLOW + "Spawner Information" + ChatColor.GOLD + " ════╗");

        String displayName = properties.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Name: " + ChatColor.WHITE + displayName);
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Location: " + ChatColor.WHITE + formatLocation());

        if (hasSpawnerGroup()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE + formatSpawnerData());

        int active = getCurrentMobCount();
        int respawning = respawnQueue.size();
        int desired = getDesiredMobCount();
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Status: " +
                ChatColor.WHITE + active + " active, " + respawning + " respawning, " + desired + " target");

        if (!respawnQueue.isEmpty()) {
            long nextRespawn = Collections.min(respawnQueue.values());
            long timeLeft = nextRespawn - System.currentTimeMillis();
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Next respawn: " +
                    ChatColor.WHITE + formatTime(Math.max(0, timeLeft)));
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Visible: " +
                (visible ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        if (properties.isTimeRestricted()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Time: " +
                    ChatColor.WHITE + properties.getStartHour() + "h - " + properties.getEndHour() + "h");
        }

        if (properties.isWeatherRestricted()) {
            String weather = "";
            if (properties.canSpawnInClear()) weather += "Clear ";
            if (properties.canSpawnInRain()) weather += "Rain ";
            if (properties.canSpawnInThunder()) weather += "Thunder";
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Weather: " + ChatColor.WHITE + weather);
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Radius: " +
                ChatColor.WHITE + String.format("%.1f", properties.getSpawnRadiusX()) + "x" +
                String.format("%.1f", properties.getSpawnRadiusY()) + "x" +
                String.format("%.1f", properties.getSpawnRadiusZ()));

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Capacity: " +
                ChatColor.WHITE + (active + respawning) + "/" + getMaxMobsAllowed());

        if (metrics.getTotalSpawned() > 0) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Statistics: " +
                    ChatColor.WHITE + metrics.getTotalSpawned() + " spawned, " +
                    metrics.getTotalKilled() + " killed");
        }

        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════╝");
    }

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

    // ================ GETTERS AND SETTERS ================

    public Location getLocation() {
        return location.clone();
    }

    public boolean isVisible() {
        return visible;
    }

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

    public SpawnerProperties getProperties() {
        return properties;
    }

    public void setProperties(SpawnerProperties properties) {
        this.properties = properties;
        needsHologramUpdate = true;
    }

    public int getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(int displayMode) {
        this.displayMode = Math.max(0, Math.min(2, displayMode));
        needsHologramUpdate = true;
        if (visible) {
            lastHologramUpdate = 0; // Force immediate update
            updateHologram();
        }
    }

    public SpawnerMetrics getMetrics() {
        return metrics;
    }

    public List<MobEntry> getMobEntries() {
        return new ArrayList<>(mobEntries);
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public Collection<SpawnedMob> getActiveMobs() {
        return new ArrayList<>(activeMobs.values());
    }

    public int getTotalActiveMobs() {
        cleanupInvalidMobs();
        return activeMobs.size();
    }

    public int getQueuedCount() {
        return respawnQueue.size();
    }

    public int getCurrentMobCount() {
        return getTotalActiveMobs();
    }

    public int getDesiredMobCount() {
        return mobEntries.stream().mapToInt(MobEntry::getAmount).sum();
    }

    public int getMaxMobsAllowed() {
        return properties.getMaxMobOverride() > 0 ?
                properties.getMaxMobOverride() :
                mobManager.getMaxMobsPerSpawner();
    }

    public boolean ownsMob(UUID entityId) {
        return activeMobs.containsKey(entityId);
    }
}