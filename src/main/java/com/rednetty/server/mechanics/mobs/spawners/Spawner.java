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
 * Class representing a single mob spawner with enhanced functionality
 */
public class Spawner {
    private final Location location;
    private final List<MobEntry> mobEntries = new ArrayList<>();
    private final Map<UUID, SpawnedMob> activeMobs = new ConcurrentHashMap<>();
    private boolean visible;
    private SpawnerProperties properties;
    private int displayMode = 2;
    private SpawnerMetrics metrics;

    private final Logger logger;
    private final MobManager mobManager;
    private final String uniqueId;

    // Queue of mobs waiting to respawn, ordered by respawn time
    private final PriorityQueue<SpawnedMob> respawnQueue = new PriorityQueue<>(
            Comparator.comparing(SpawnedMob::getRespawnTime));

    /**
     * Constructor for a new spawner
     *
     * @param location The spawner location
     * @param data     The spawner data string
     * @param visible  Whether the spawner is visible
     */
    public Spawner(Location location, String data, boolean visible) {
        this.location = location.clone();
        this.visible = visible;
        this.properties = new SpawnerProperties();
        this.metrics = new SpawnerMetrics();
        this.mobManager = MobManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
        this.uniqueId = generateSpawnerId(location);

        // Parse and initialize mob entries
        if (data != null && !data.isEmpty()) {
            parseSpawnerData(data);
        }
    }

    /**
     * Generate a unique ID for this spawner based on its location
     */
    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    /**
     * Parse spawner data string into mob entries
     *
     * @param data The spawner data string (format: mobType:tier@elite#amount,...)
     */
    public void parseSpawnerData(String data) {
        mobEntries.clear();

        // Trim the data to remove any leading/trailing whitespace
        data = data.trim();

        String[] entries = data.split(",");
        for (String entry : entries) {
            try {
                // Trim each entry to handle whitespace properly
                entry = entry.trim();

                if (entry.isEmpty()) {
                    continue; // Skip empty entries
                }

                String[] parts = entry.split(":");
                if (parts.length != 2) {
                    logger.warning("[Spawner] Invalid entry format (wrong number of : separators): " + entry);
                    continue;
                }

                String mobType = parts[0].toLowerCase().trim();

                // Special handling for witherskeleton which might be entered with underscore
                if (mobType.equals("wither_skeleton")) {
                    mobType = "witherskeleton";
                }

                String[] tierInfo = parts[1].split("@");
                if (tierInfo.length != 2) {
                    logger.warning("[Spawner] Invalid tier section (wrong number of @ separators): " + entry);
                    continue;
                }

                int tier;
                try {
                    tier = Integer.parseInt(tierInfo[0].trim());
                } catch (NumberFormatException e) {
                    logger.warning("[Spawner] Invalid tier (not a number): " + tierInfo[0]);
                    continue;
                }

                // Validate tier range (1-6)
                if (tier < 1 || tier > 6) {
                    logger.warning("[Spawner] Invalid tier in mob entry: " + entry + " - Tier must be 1-6");
                    continue; // Skip invalid entry
                }

                String[] eliteInfo = tierInfo[1].split("#");
                if (eliteInfo.length != 2) {
                    logger.warning("[Spawner] Invalid elite/amount section (wrong number of # separators): " + entry);
                    continue;
                }

                boolean elite;
                try {
                    String eliteStr = eliteInfo[0].trim().toLowerCase();
                    if (!eliteStr.equals("true") && !eliteStr.equals("false")) {
                        logger.warning("[Spawner] Invalid elite value (must be true/false): " + eliteInfo[0]);
                        continue;
                    }
                    elite = Boolean.parseBoolean(eliteStr);
                } catch (Exception e) {
                    logger.warning("[Spawner] Error parsing elite value: " + eliteInfo[0]);
                    continue;
                }

                int amount;
                try {
                    amount = Integer.parseInt(eliteInfo[1].trim());
                    if (amount < 1) {
                        logger.warning("[Spawner] Invalid amount (must be at least 1): " + eliteInfo[1]);
                        amount = 1; // Fix: set to minimum valid value
                    }
                } catch (NumberFormatException e) {
                    logger.warning("[Spawner] Invalid amount (not a number): " + eliteInfo[1]);
                    continue;
                }

                mobEntries.add(new MobEntry(mobType, tier, elite, amount));
            } catch (Exception e) {
                logger.warning("[Spawner] Error parsing mob entry: " + entry + " - " + e.getMessage());
            }
        }
    }

    /**
     * Get spawner data as a string
     *
     * @return Formatted data string
     */
    public String getDataString() {
        return mobEntries.stream()
                .map(MobEntry::toString)
                .collect(Collectors.joining(","));
    }

    /**
     * Spawn all mobs based on the spawner configuration
     *
     * @return Number of mobs successfully spawned
     */
    public int spawnAllMobs() {
        // Check all prerequisites before attempting to spawn
        if (!canSpawnMobs()) {
            return 0;
        }

        int maxMobsAllowed = getMaxMobsAllowed();
        int currentTotal = getTotalActiveMobs();

        // If we're already at max capacity, skip spawning
        if (currentTotal >= maxMobsAllowed) {
            return 0;
        }

        // Calculate available capacity
        int remainingCapacity = maxMobsAllowed - currentTotal;
        int totalDesiredMobs = getTotalDesiredMobs();
        int toSpawn = Math.min(remainingCapacity, totalDesiredMobs - currentTotal);

        // If no mobs to spawn, return early
        if (toSpawn <= 0) {
            return 0;
        }

        // Distribute and spawn mobs
        int totalSpawned = distributeAndSpawnMobs(toSpawn);

        // Record metrics and update display if needed
        if (totalSpawned > 0) {
            // Record in metrics
            metrics.recordSpawn(totalSpawned);

            // Update hologram if visible
            if (isVisible()) {
                updateHologram();
            }

            if (isDebugMode()) {
                logger.info("[Spawner] Spawned " + totalSpawned + " mobs at " + formatLocation());
            }
        }

        return totalSpawned;
    }

    /**
     * Check if mobs can spawn from this spawner
     *
     * @return true if spawning is possible
     */
    private boolean canSpawnMobs() {
        return isWorldAndChunkLoaded() &&
                canSpawnByTimeAndWeather() &&
                isPlayerNearby() &&
                !hasRespawningMobs(); // Don't spawn new mobs if respawns are pending
    }

    /**
     * Distribute spawning among mob entries based on their proportions
     *
     * @param totalToSpawn Total mobs to spawn
     * @return Number of mobs actually spawned
     */
    private int distributeAndSpawnMobs(int totalToSpawn) {
        int totalSpawned = 0;

        // Calculate the total weight
        int totalWeight = mobEntries.stream()
                .mapToInt(MobEntry::getAmount)
                .sum();

        if (totalWeight == 0) {
            return 0;
        }

        // Calculate proportions for each mob type
        Map<MobEntry, Integer> spawnCounts = calculateSpawnDistribution(totalToSpawn, totalWeight);

        // Spawn the mobs based on the calculated distribution
        for (Map.Entry<MobEntry, Integer> entry : spawnCounts.entrySet()) {
            MobEntry mobEntry = entry.getKey();
            int count = entry.getValue();

            for (int i = 0; i < count; i++) {
                if (spawnMob(mobEntry)) {
                    totalSpawned++;
                }
            }
        }

        return totalSpawned;
    }

    /**
     * Calculate how many of each mob type to spawn
     *
     * @param totalToSpawn Total mobs to spawn
     * @param totalWeight  Total weight of all mob entries
     * @return Map of mob entries to spawn counts
     */
    private Map<MobEntry, Integer> calculateSpawnDistribution(int totalToSpawn, int totalWeight) {
        Map<MobEntry, Integer> spawnCounts = new HashMap<>();
        int allocated = 0;

        // Initial allocation based on proportions
        for (MobEntry entry : mobEntries) {
            int proportion = (int) Math.floor((double) entry.getAmount() / totalWeight * totalToSpawn);
            spawnCounts.put(entry, proportion);
            allocated += proportion;
        }

        // Distribute any remaining counts due to rounding
        if (allocated < totalToSpawn) {
            int remaining = totalToSpawn - allocated;

            // Sort entries by those with the largest proportion of unallocated mobs
            List<MobEntry> sortedEntries = mobEntries.stream()
                    .sorted((e1, e2) -> {
                        double p1 = (double) e1.getAmount() / totalWeight * totalToSpawn - spawnCounts.get(e1);
                        double p2 = (double) e2.getAmount() / totalWeight * totalToSpawn - spawnCounts.get(e2);
                        return Double.compare(p2, p1);
                    })
                    .collect(Collectors.toList());

            // Distribute remaining slots
            for (int i = 0; i < remaining && i < sortedEntries.size(); i++) {
                spawnCounts.put(sortedEntries.get(i), spawnCounts.get(sortedEntries.get(i)) + 1);
            }
        }

        return spawnCounts;
    }

    /**
     * Spawn a single mob
     *
     * @param entry The mob entry to spawn
     * @return true if successfully spawned
     */
    private boolean spawnMob(MobEntry entry) {
        try {
            // Check with MobManager if this mob type can respawn
            if (!mobManager.canRespawn(entry.getMobType(), entry.getTier(), entry.isElite())) {
                if (isDebugMode()) {
                    logger.info("[Spawner] Prevented spawn of " +
                            entry.getMobType() + " T" + entry.getTier() + (entry.isElite() ? "+" : "") +
                            " - still in respawn cooldown");
                }
                return false;
            }

            // Get a spawn location near the spawner
            Location spawnLoc = getRandomSpawnLocation();

            // Spawn the mob
            LivingEntity entity = mobManager.spawnMob(spawnLoc, entry.getMobType(), entry.getTier(), entry.isElite());

            if (entity != null) {
                // Add metadata to track which spawner this mob belongs to
                entity.setMetadata("spawner", new FixedMetadataValue(YakRealms.getInstance(), uniqueId));

                // Add group metadata if in a group
                if (hasSpawnerGroup()) {
                    entity.setMetadata("spawnerGroup", new FixedMetadataValue(
                            YakRealms.getInstance(), properties.getSpawnerGroup()));
                }

                // Track this mob
                SpawnedMob spawnedMob = new SpawnedMob(
                        entity.getUniqueId(), entry.getMobType(), entry.getTier(), entry.isElite());
                activeMobs.put(entity.getUniqueId(), spawnedMob);

                // Track in metrics
                metrics.recordSpawnByType(1, entry.getTier(), entry.getMobType());

                return true;
            }
        } catch (Exception e) {
            logger.warning("[Spawner] Failed to spawn mob " + entry.getMobType() +
                    " at " + formatLocation() + ": " + e.getMessage());

            // Record failure in metrics
            metrics.recordFailedSpawn();
        }

        return false;
    }

    /**
     * Check if this spawner has a group assigned
     *
     * @return true if the spawner belongs to a group
     */
    private boolean hasSpawnerGroup() {
        return properties.getSpawnerGroup() != null && !properties.getSpawnerGroup().isEmpty();
    }

    /**
     * Generate a random spawn location near this spawner
     *
     * @return A randomized location for spawning
     */
    private Location getRandomSpawnLocation() {
        // Get spawn radius from properties
        double radiusX = properties.getSpawnRadiusX();
        double radiusY = properties.getSpawnRadiusY();
        double radiusZ = properties.getSpawnRadiusZ();

        // Generate random offsets
        double offsetX = (Math.random() * 2 - 1) * radiusX;
        double offsetY = (Math.random() * 2 - 1) * radiusY;
        double offsetZ = (Math.random() * 2 - 1) * radiusZ;

        // Create new location
        Location spawnLoc = location.clone().add(offsetX, offsetY, offsetZ);

        // Check if the location is valid (not in a solid block)
        if (spawnLoc.getBlock().getType().isSolid()) {
            // Try to find a safe spot above
            for (int y = 0; y < 3; y++) {
                Location adjusted = spawnLoc.clone().add(0, y + 1, 0);
                if (!adjusted.getBlock().getType().isSolid()) {
                    return adjusted;
                }
            }

            // If no safe spot found, return original center with a height offset
            return location.clone().add(0, 1, 0);
        }

        return spawnLoc;
    }

    /**
     * Check if this spawner is in a loaded chunk
     *
     * @return true if world and chunk are loaded
     */
    private boolean isWorldAndChunkLoaded() {
        return location.getWorld() != null &&
                location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Check if this spawner can spawn based on time and weather restrictions
     *
     * @return true if spawner can spawn
     */
    private boolean canSpawnByTimeAndWeather() {
        World world = location.getWorld();
        if (world == null) return false;

        // Check time restrictions
        if (properties.isTimeRestricted() && !properties.canSpawnByTime(world)) {
            return false;
        }

        // Check weather restrictions
        if (properties.isWeatherRestricted() && !properties.canSpawnByWeather(world)) {
            return false;
        }

        return true;
    }

    /**
     * Check if a player is nearby this spawner
     *
     * @return true if a player is within detection range
     */
    private boolean isPlayerNearby() {
        double range = properties.getPlayerDetectionRangeOverride() > 0 ?
                properties.getPlayerDetectionRangeOverride() :
                MobManager.getInstance().getPlayerDetectionRange();

        return MobUtils.isPlayerNearby(location, range);
    }

    /**
     * Get the maximum number of mobs allowed for this spawner
     *
     * @return Maximum mob count
     */
    public int getMaxMobsAllowed() {
        return properties.getMaxMobOverride() > 0 ?
                properties.getMaxMobOverride() :
                MobManager.getInstance().getMaxMobsPerSpawner();
    }

    /**
     * Get total number of active mobs from this spawner
     *
     * @return Number of active mobs
     */
    public int getTotalActiveMobs() {
        cleanupInvalidMobs();
        return activeMobs.size();
    }

    /**
     * Get total desired number of mobs based on configurations
     *
     * @return Total desired mob count
     */
    public int getTotalDesiredMobs() {
        return mobEntries.stream()
                .mapToInt(MobEntry::getAmount)
                .sum();
    }

    /**
     * Remove references to mobs that no longer exist
     */
    private void cleanupInvalidMobs() {
        // Collect invalid mobs
        Set<UUID> toRemove = new HashSet<>();

        for (UUID uuid : activeMobs.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || !entity.isValid() || entity.isDead()) {
                toRemove.add(uuid);
            }
        }

        // Remove invalid mobs
        for (UUID uuid : toRemove) {
            activeMobs.remove(uuid);
        }
    }

    /**
     * Create or update hologram for this spawner
     */
    public void updateHologram() {
        try {
            if (!isVisible()) {
                // Remove existing hologram if it exists
                removeHologram();
                return;
            }

            List<String> lines = generateHologramLines();
            String holoId = "spawner_" + uniqueId;

            int y = displayMode == 2 ? 4 : 2;
            // Create or update hologram
            HologramManager.createOrUpdateHologram(
                    holoId,
                    location.clone().add(0, y, 0),
                    lines,
                    0.25); // Line spacing
        } catch (Exception e) {
            logger.warning("[Spawner] Error updating hologram: " + e.getMessage());
        }
    }

    /**
     * Generate hologram lines based on current state
     *
     * @return List of formatted text lines
     */
    private List<String> generateHologramLines() {
        List<String> lines = new ArrayList<>();

        // Title - add display name or default
        lines.add(getDisplayTitle());

        // Group info if applicable
        addGroupInfoIfPresent(lines);

        // Basic info for all display modes
        lines.add(ChatColor.YELLOW + formatSpawnerData());

        // Status line showing active mobs
        addStatusLine(lines);

        // Add detailed stats if in appropriate display mode
        if (displayMode >= 1) {
            addDetailedStats(lines);
        }

        // Add admin info if in admin mode
        if (displayMode >= 2) {
            addAdminInfo(lines);
        }

        return lines;
    }

    /**
     * Get display title for the hologram
     *
     * @return Formatted title string
     */
    private String getDisplayTitle() {
        String displayName = properties.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            return ChatColor.GOLD + "" + ChatColor.BOLD + displayName;
        } else {
            return ChatColor.GOLD + "" + ChatColor.BOLD + "Spawner";
        }
    }

    /**
     * Add group info if spawner is in a group
     *
     * @param lines Line list to add to
     */
    private void addGroupInfoIfPresent(List<String> lines) {
        if (properties.getSpawnerGroup() != null && !properties.getSpawnerGroup().isEmpty()) {
            lines.add(ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
        }
    }

    /**
     * Add a status line showing active mobs
     *
     * @param lines Line list to add to
     */
    private void addStatusLine(List<String> lines) {
        int activeMobCount = getTotalActiveMobs();
        if (activeMobCount > 0) {
            lines.add(ChatColor.RED + "Active: " + ChatColor.WHITE + activeMobCount + " mobs");
        }
    }

    /**
     * Add detailed stats for display mode 1+
     *
     * @param lines Line list to add to
     */
    private void addDetailedStats(List<String> lines) {
        if (metrics.getTotalSpawned() > 0) {
            lines.add(ChatColor.GRAY + "Spawned: " + metrics.getTotalSpawned() +
                    " | Killed: " + metrics.getTotalKilled());

            // Add spawn rate info
            lines.add(ChatColor.GRAY + "Rate: " + String.format("%.1f", metrics.getSpawnRate()) +
                    "/hr | Efficiency: " + String.format("%.1f%%", metrics.getEfficiency()));
        }

        // IMPROVED: Always show respawn queue info if there are queued mobs
        if (!respawnQueue.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            int queuedMobs = respawnQueue.size();

            // Add a line showing number of mobs waiting to respawn
            lines.add(ChatColor.YELLOW + "Queued respawns: " + ChatColor.WHITE + queuedMobs);

            // Get next respawn time and calculate remaining time
            SpawnedMob nextMob = respawnQueue.peek();
            if (nextMob != null) {
                long nextRespawn = nextMob.getRespawnTime();
                long timeRemaining = nextRespawn - currentTime;

                if (timeRemaining > 0) {
                    lines.add(ChatColor.YELLOW + "Next respawn: " + ChatColor.WHITE +
                            formatTimeRemaining(timeRemaining));
                } else {
                    lines.add(ChatColor.GREEN + "Ready to spawn!");
                }
            }
        } else {
            // If there are no respawns queued but not at max capacity, show this
            int currentMobs = getTotalActiveMobs();
            int desiredMobs = getTotalDesiredMobs();

            if (currentMobs < desiredMobs) {
                lines.add(ChatColor.GRAY + "Waiting for new spawns...");
            }
        }
    }

    /**
     * Add admin info for display mode 2+
     *
     * @param lines Line list to add to
     */
    private void addAdminInfo(List<String> lines) {
        // Active mob counts by type
        if (!activeMobs.isEmpty()) {
            lines.add(ChatColor.GOLD + "Active mob counts:");

            // Group by mob type and tier
            Map<String, Integer> typeCounts = getActiveMobTypeCounts();

            // Add each type count
            for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                lines.add(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                        ChatColor.WHITE + entry.getValue());
            }
        }

        // Add location info
        lines.add(ChatColor.DARK_GRAY + formatLocation());

        // Add respawn queue info
        if (!respawnQueue.isEmpty()) {
            lines.add(ChatColor.GOLD + "Respawn queue: " + respawnQueue.size());
        }
    }

    /**
     * Get counts of active mobs by type
     *
     * @return Map of type descriptions to counts
     */
    private Map<String, Integer> getActiveMobTypeCounts() {
        Map<String, Integer> typeCounts = new HashMap<>();

        for (SpawnedMob mob : activeMobs.values()) {
            String key = formatMobName(mob.getMobType()) +
                    " T" + mob.getTier() +
                    (mob.isElite() ? "+" : "");

            typeCounts.put(key, typeCounts.getOrDefault(key, 0) + 1);
        }

        return typeCounts;
    }

    /**
     * Get the next respawn time from the queue
     *
     * @return Next respawn time or 0 if none
     */
    private long getNextRespawnTime() {
        if (respawnQueue.isEmpty()) {
            return 0;
        }

        return respawnQueue.peek().getRespawnTime();
    }

    /**
     * Remove hologram for this spawner
     */
    public void removeHologram() {
        String holoId = "spawner_" + uniqueId;
        HologramManager.removeHologram(holoId);
    }

    /**
     * Format mob entries for display
     *
     * @return Formatted string
     */
    private String formatSpawnerData() {
        if (mobEntries.isEmpty()) {
            return "Empty";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(mobEntries.size(), 3); i++) { // Limit to 3 entries
            MobEntry entry = mobEntries.get(i);
            if (i > 0) {
                result.append(", ");
            }

            result.append(formatMobName(entry.getMobType()))
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

        // Capitalize and format
        String name = type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
        name = name.replace("_", " ");

        // Special cases
        if (name.equals("Witherskeleton")) {
            return "Wither Skeleton";
        }

        return name;
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
     * Register a mob death and schedule its respawn
     *
     * @param entityId UUID of the entity that died
     */
    public void registerMobDeath(UUID entityId) {
        SpawnedMob mob = activeMobs.remove(entityId);

        if (mob != null) {
            // Record kill in metrics
            metrics.recordKill();

            // Get respawn time from MobManager
            long respawnTime = MobManager.getInstance().recordMobDeath(
                    mob.getMobType(), mob.getTier(), mob.isElite());

            // Calculate and log delay for easier debugging
            long currentTime = System.currentTimeMillis();
            long respawnDelay = respawnTime - currentTime;

            // Set the respawn time directly
            mob.setRespawnTime(respawnTime);

            // Add to respawn queue
            respawnQueue.add(mob);

            // Always log respawn scheduling (not just in debug mode)
            logger.info("[Spawner] Mob " + mob.getMobType() + " T" + mob.getTier() +
                    (mob.isElite() ? "+" : "") + " scheduled to respawn in " +
                    (respawnDelay / 1000) + " seconds at " + formatLocation());

            // Update hologram if visible
            if (visible) {
                updateHologram();
            }
        }
    }

    /**
     * Process respawn timers and spawn mobs as needed
     *
     * @return Number of mobs respawned
     */
    public int processRespawns() {
        // Skip if conditions aren't met
        if (!canProcessRespawns()) {
            return 0;
        }

        int spawned = 0;
        long currentTime = System.currentTimeMillis();

        // Only process ONE mob at a time
        if (!respawnQueue.isEmpty()) {
            SpawnedMob mob = respawnQueue.peek();

            // Check if it's time to respawn this mob
            if (isReadyToRespawn(mob, currentTime)) {
                // Remove from queue
                mob = respawnQueue.poll();

                // Check with MobManager if this mob can actually respawn now
                if (mobManager.canRespawn(mob.getMobType(), mob.getTier(), mob.isElite())) {
                    // Find matching entry and spawn just ONE mob
                    MobEntry entry = findMatchingEntry(mob);
                    if (entry != null && spawnMob(entry)) {
                        spawned = 1;
                        logRespawnSuccess(mob);
                    }
                } else {
                    // MobManager says it's still too early - put it back in queue with updated time
                    reQueueWithUpdatedTime(mob);
                }
            } else if (isDebugMode()) {
                // Not ready to spawn - log remaining time
                logWaitingForRespawn(mob, currentTime);
            }
        }

        // Update hologram
        if (spawned > 0 && visible) {
            updateHologram();
        }

        return spawned;
    }

    /**
     * Check if we can process respawns
     *
     * @return true if conditions allow respawn processing
     */
    private boolean canProcessRespawns() {
        return isWorldAndChunkLoaded() &&
                canSpawnByTimeAndWeather() &&
                isPlayerNearby() &&
                getTotalActiveMobs() < getMaxMobsAllowed();
    }

    /**
     * Check if a mob is ready to respawn
     *
     * @param mob         The mob to check
     * @param currentTime Current system time
     * @return true if ready to respawn
     */
    private boolean isReadyToRespawn(SpawnedMob mob, long currentTime) {
        return mob.getRespawnTime() <= currentTime;
    }

    /**
     * Re-queue a mob with updated respawn time
     *
     * @param mob The mob to requeue
     */
    private void reQueueWithUpdatedTime(SpawnedMob mob) {
        long updatedRespawnTime = mobManager.getResponTime(
                mob.getMobType(), mob.getTier(), mob.isElite());

        if (updatedRespawnTime > System.currentTimeMillis()) {
            mob.setRespawnTime(updatedRespawnTime);
            respawnQueue.add(mob);

            if (isDebugMode()) {
                long timeRemaining = (updatedRespawnTime - System.currentTimeMillis()) / 1000;
                logger.info("[Spawner] RESCHEDULED: Respawn delayed according to MobManager. " +
                        "Next attempt in " + timeRemaining + " seconds");
            }
        }
    }

    /**
     * Log successful respawn
     *
     * @param mob The respawned mob
     */
    private void logRespawnSuccess(SpawnedMob mob) {
        if (isDebugMode()) {
            logger.info("[Spawner] SPAWNED mob: " + mob.getMobType() +
                    " (T" + mob.getTier() + (mob.isElite() ? "+" : "") + ")");
        }
    }

    /**
     * Log waiting for respawn
     *
     * @param mob         The mob waiting to respawn
     * @param currentTime Current system time
     */
    private void logWaitingForRespawn(SpawnedMob mob, long currentTime) {
        long timeRemaining = mob.getRespawnTime() - currentTime;
        logger.info("[Spawner] NOT YET TIME: Next respawn in " +
                (timeRemaining / 1000) + " seconds at " + formatLocation());
    }

    /**
     * Find a matching mob entry for a spawned mob
     *
     * @param mob The spawned mob
     * @return Matching entry or null
     */
    private MobEntry findMatchingEntry(SpawnedMob mob) {
        for (MobEntry entry : mobEntries) {
            if (entry.getMobType().equals(mob.getMobType()) &&
                    entry.getTier() == mob.getTier() &&
                    entry.isElite() == mob.isElite()) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Reset this spawner's state
     */
    public void reset() {
        // Clear active mobs and respawn queue
        activeMobs.clear();
        respawnQueue.clear();

        // Reset metrics
        metrics.reset();

        // Update hologram if visible
        if (visible) {
            updateHologram();
        }

        if (isDebugMode()) {
            logger.info("[Spawner] Spawner at " + formatLocation() + " has been reset");
        }
    }

    /**
     * Format time remaining in a readable format
     *
     * @param millis Time in milliseconds
     * @return Formatted time string
     */
    private String formatTimeRemaining(long millis) {
        if (millis < 0) return "0s";

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
     * Check if this spawner has mobs waiting to respawn
     *
     * @return true if there are mobs in the respawn queue
     */
    public boolean hasRespawningMobs() {
        return !respawnQueue.isEmpty();
    }

    /**
     * Get the number of mobs waiting to respawn
     *
     * @return Respawn queue size
     */
    public int getQueuedCount() {
        return respawnQueue.size();
    }

    /**
     * Check if debug mode is enabled in YakRealms
     *
     * @return true if debug mode is enabled
     */
    private boolean isDebugMode() {
        return YakRealms.getInstance().isDebugMode();
    }

    /**
     * Send detailed spawner info to a player
     *
     * @param player The player to send info to
     */
    public void sendInfoTo(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔════ " + ChatColor.YELLOW + "Spawner Information" + ChatColor.GOLD + " ════╗");

        // Display name
        String displayName = properties.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Name: " + ChatColor.WHITE + displayName);
        }

        // Location
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Location: " +
                ChatColor.WHITE + formatLocation());

        // Group
        if (properties.getSpawnerGroup() != null && !properties.getSpawnerGroup().isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Group: " +
                    ChatColor.WHITE + properties.getSpawnerGroup());
        }

        // Mob types
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Mobs: " +
                ChatColor.WHITE + formatSpawnerData());

        // Active mobs
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Active Mobs: " +
                ChatColor.WHITE + getTotalActiveMobs() + "/" + getTotalDesiredMobs());

        // Ready to spawn
        if (!respawnQueue.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            long nextRespawn = getNextRespawnTime();

            if (nextRespawn > currentTime) {
                long timeUntil = nextRespawn - currentTime;
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Next Spawn: " +
                        ChatColor.WHITE + formatTimeRemaining(timeUntil));
            } else {
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Ready to Spawn: " +
                        ChatColor.WHITE + respawnQueue.size() + " mobs");
            }
        }

        // Visibility
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Visible: " +
                (visible ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        // Restrictions
        addRestrictionInfo(player);

        // Spawner settings
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Radius: " +
                ChatColor.WHITE + properties.getSpawnRadiusX() + "x" +
                properties.getSpawnRadiusY() + "x" +
                properties.getSpawnRadiusZ());

        // Display mode
        String modeText = getDisplayModeName();
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Display Mode: " +
                ChatColor.WHITE + modeText);

        // Stats if available
        addMetricsInfo(player);

        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════╝");
    }

    /**
     * Add restriction information to player spawner info
     *
     * @param player The player to send info to
     */
    private void addRestrictionInfo(Player player) {
        // Time restrictions
        if (properties.isTimeRestricted()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Time Restriction: " +
                    ChatColor.WHITE + properties.getStartHour() + "h - " + properties.getEndHour() + "h");
        }

        // Weather restrictions
        if (properties.isWeatherRestricted()) {
            String weather = "";
            if (properties.canSpawnInClear()) weather += "Clear ";
            if (properties.canSpawnInRain()) weather += "Rain ";
            if (properties.canSpawnInThunder()) weather += "Thunder";

            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Weather Restriction: " +
                    ChatColor.WHITE + weather);
        }
    }

    /**
     * Add metrics information to player spawner info
     *
     * @param player The player to send info to
     */
    private void addMetricsInfo(Player player) {
        if (metrics.getTotalSpawned() > 0) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Total Spawned: " +
                    ChatColor.WHITE + metrics.getTotalSpawned());

            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Total Killed: " +
                    ChatColor.WHITE + metrics.getTotalKilled());

            if (metrics.getTotalSpawned() > 10) {
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Efficiency: " +
                        ChatColor.WHITE + String.format("%.1f%%", metrics.getEfficiency()));
            }
        }
    }

    /**
     * Get display mode name
     *
     * @return Text description of current display mode
     */
    private String getDisplayModeName() {
        switch (displayMode) {
            case 0:
                return "Basic";
            case 1:
                return "Detailed";
            case 2:
                return "Admin";
            default:
                return "Unknown";
        }
    }

    // Getters and setters

    public Location getLocation() {
        return location.clone();
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;

        if (visible) {
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
    }

    public int getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(int displayMode) {
        if (displayMode < 0 || displayMode > 2) {
            displayMode = 0;
        }

        this.displayMode = displayMode;
        updateHologram();
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

    public Collection<SpawnedMob> getQueuedMobs() {
        return new ArrayList<>(respawnQueue);
    }

    /**
     * Check if a mob belongs to this spawner
     *
     * @param entityId The entity UUID
     * @return true if this spawner spawned the mob
     */
    public boolean ownsMob(UUID entityId) {
        return activeMobs.containsKey(entityId);
    }
}