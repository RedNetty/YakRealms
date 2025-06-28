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
 * FIXED: Completely synchronous spawner with reliable respawn system and proper hologram updates
 * No async tasks, no race conditions, deterministic behavior
 */
public class Spawner {
    private final Location location;
    private final List<MobEntry> mobEntries = new ArrayList<>();
    private final Map<UUID, SpawnedMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnedMob> respawningMobs = new ConcurrentHashMap<>();

    private boolean visible;
    private SpawnerProperties properties;
    private int displayMode = 2;
    private SpawnerMetrics metrics;

    private final Logger logger;
    private final MobManager mobManager;
    private final String uniqueId;

    // FIXED: Simple tick-based timing with proper hologram update tracking
    private long lastProcessTick = 0;
    private boolean needsHologramUpdate = false;
    private long lastHologramUpdate = 0;
    private static final long HOLOGRAM_UPDATE_INTERVAL = 5000; // 5 seconds between updates

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

        // Parse and initialize mob entries
        if (data != null && !data.isEmpty()) {
            parseSpawnerData(data);
        }

        this.lastProcessTick = System.currentTimeMillis();
        this.needsHologramUpdate = true; // Initial update needed
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
     */
    public void parseSpawnerData(String data) {
        mobEntries.clear();
        data = data.trim();

        String[] entries = data.split(",");
        for (String entry : entries) {
            try {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                String[] parts = entry.split(":");
                if (parts.length != 2) {
                    logger.warning("[Spawner] Invalid entry format: " + entry);
                    continue;
                }

                String mobType = parts[0].toLowerCase().trim();
                if (mobType.equals("wither_skeleton")) {
                    mobType = "witherskeleton";
                }

                String[] tierInfo = parts[1].split("@");
                if (tierInfo.length != 2) {
                    logger.warning("[Spawner] Invalid tier section: " + entry);
                    continue;
                }

                int tier;
                try {
                    tier = Integer.parseInt(tierInfo[0].trim());
                    if (tier < 1 || tier > 6) {
                        logger.warning("[Spawner] Invalid tier: " + tier);
                        continue;
                    }
                } catch (NumberFormatException e) {
                    logger.warning("[Spawner] Invalid tier format: " + tierInfo[0]);
                    continue;
                }

                String[] eliteInfo = tierInfo[1].split("#");
                if (eliteInfo.length != 2) {
                    logger.warning("[Spawner] Invalid elite/amount section: " + entry);
                    continue;
                }

                boolean elite;
                try {
                    String eliteStr = eliteInfo[0].trim().toLowerCase();
                    if (!eliteStr.equals("true") && !eliteStr.equals("false")) {
                        logger.warning("[Spawner] Invalid elite value: " + eliteInfo[0]);
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
                        amount = 1;
                    }
                } catch (NumberFormatException e) {
                    logger.warning("[Spawner] Invalid amount: " + eliteInfo[1]);
                    continue;
                }

                mobEntries.add(new MobEntry(mobType, tier, elite, amount));
            } catch (Exception e) {
                logger.warning("[Spawner] Error parsing mob entry: " + entry + " - " + e.getMessage());
            }
        }

        // Flag for hologram update after parsing
        needsHologramUpdate = true;
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
     * FIXED: Main synchronous processing method with proper hologram updates
     * Called every tick from MobSpawner - handles all spawning and respawning
     */
    public void processTick() {
        long currentTick = System.currentTimeMillis();

        try {
            // Clean up invalid mobs first
            cleanupInvalidMobs();

            // Process respawns
            processRespawns(currentTick);

            // Handle initial spawning if needed
            if (shouldAttemptSpawning()) {
                spawnMissingMobs();
            }

            // FIXED: Update hologram periodically or when needed
            updateHologramIfNeeded(currentTick);

            this.lastProcessTick = currentTick;

        } catch (Exception e) {
            logger.warning("[Spawner] Error processing tick: " + e.getMessage());
            if (isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * FIXED: Proper hologram update logic with timing control
     */
    private void updateHologramIfNeeded(long currentTime) {
        try {
            // Update hologram if needed and enough time has passed
            if (needsHologramUpdate && visible &&
                    (currentTime - lastHologramUpdate) >= HOLOGRAM_UPDATE_INTERVAL) {

                updateHologram();
                needsHologramUpdate = false;
                lastHologramUpdate = currentTime;

                if (isDebugMode()) {
                    logger.info("[Spawner] Updated hologram for " + formatLocation());
                }
            }
        } catch (Exception e) {
            logger.warning("[Spawner] Error updating hologram: " + e.getMessage());
        }
    }

    /**
     * FIXED: Synchronous respawn processing with better tracking
     */
    private void processRespawns(long currentTick) {
        if (respawningMobs.isEmpty()) return;

        List<UUID> readyToRespawn = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // Find mobs ready to respawn
        for (Map.Entry<UUID, SpawnedMob> entry : respawningMobs.entrySet()) {
            SpawnedMob mob = entry.getValue();
            if (mob.isReadyToRespawn(currentTime)) {
                readyToRespawn.add(entry.getKey());
            }
        }

        // Respawn ready mobs
        for (UUID deadMobId : readyToRespawn) {
            SpawnedMob deadMob = respawningMobs.remove(deadMobId);
            if (deadMob != null) {
                attemptRespawn(deadMob);
            }
        }

        if (!readyToRespawn.isEmpty()) {
            needsHologramUpdate = true;
            if (isDebugMode()) {
                logger.info("[Spawner] Processed " + readyToRespawn.size() + " respawns at " + formatLocation());
            }
        }
    }

    /**
     * FIXED: Direct respawn attempt with better error handling and retry logic
     */
    private void attemptRespawn(SpawnedMob deadMob) {
        // Check basic requirements
        if (!canSpawnHere()) {
            // Retry in 10 seconds
            deadMob.setRespawnTime(System.currentTimeMillis() + 10000);
            respawningMobs.put(UUID.randomUUID(), deadMob);
            return;
        }

        // Check capacity
        if (isAtCapacity()) {
            // Wait for capacity to free up - retry in 5 seconds
            deadMob.setRespawnTime(System.currentTimeMillis() + 5000);
            respawningMobs.put(UUID.randomUUID(), deadMob);
            return;
        }

        // Find the mob entry for this mob
        MobEntry entry = findMatchingEntry(deadMob);
        if (entry == null) {
            logger.warning("[Spawner] Could not find mob entry for respawn: " + deadMob.getMobType());
            return;
        }

        // FIXED: Check if MobManager can spawn this mob type
        if (!mobManager.canSpawnerSpawnMob(entry.getMobType(), entry.getTier(), entry.isElite())) {
            // Retry in 30 seconds if spawn conditions aren't met
            deadMob.setRespawnTime(System.currentTimeMillis() + 30000);
            respawningMobs.put(UUID.randomUUID(), deadMob);
            if (isDebugMode()) {
                logger.info("[Spawner] MobManager rejected spawn, retrying later: " + entry.getMobType());
            }
            return;
        }

        // Attempt to spawn
        if (spawnSingleMob(entry)) {
            metrics.recordSpawn(1);
            needsHologramUpdate = true;

            logger.info("[Spawner] RESPAWN SUCCESS: " + entry.getMobType() + " T" + entry.getTier() +
                    (entry.isElite() ? "+" : "") + " at " + formatLocation());
        } else {
            // Failed to spawn - try again in 15 seconds
            deadMob.setRespawnTime(System.currentTimeMillis() + 15000);
            respawningMobs.put(UUID.randomUUID(), deadMob);

            if (isDebugMode()) {
                logger.info("[Spawner] Respawn failed, will retry: " + entry.getMobType());
            }
        }
    }

    /**
     * FIXED: Determine if we should attempt spawning
     */
    private boolean shouldAttemptSpawning() {
        if (!canSpawnHere()) return false;
        if (isAtCapacity()) return false;

        // Check if we need more mobs
        int totalCurrent = getTotalActiveMobs() + getQueuedCount();
        int totalDesired = getTotalDesiredMobs();

        return totalCurrent < totalDesired;
    }

    /**
     * FIXED: Spawn missing mobs based on configuration
     */
    private void spawnMissingMobs() {
        Map<MobEntry, Integer> missingCounts = calculateMissingMobs();
        int totalSpawned = 0;
        int maxToSpawn = getMaxMobsAllowed() - (getTotalActiveMobs() + getQueuedCount());

        // Sort by priority (could be based on amount, tier, etc.)
        List<Map.Entry<MobEntry, Integer>> sortedMissing = missingCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<MobEntry, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        // Spawn mobs in order of priority
        for (Map.Entry<MobEntry, Integer> entry : sortedMissing) {
            if (totalSpawned >= maxToSpawn) break;

            MobEntry mobEntry = entry.getKey();
            int missing = entry.getValue();
            int toSpawnForThisType = Math.min(missing, maxToSpawn - totalSpawned);

            for (int i = 0; i < toSpawnForThisType; i++) {
                if (spawnSingleMob(mobEntry)) {
                    totalSpawned++;
                } else {
                    break; // Stop trying this type if spawning fails
                }
            }
        }

        if (totalSpawned > 0) {
            metrics.recordSpawn(totalSpawned);
            needsHologramUpdate = true;

            if (isDebugMode()) {
                logger.info("[Spawner] INITIAL SPAWN: " + totalSpawned + " mobs at " + formatLocation());
            }
        }
    }

    /**
     * Calculate how many of each mob type we're missing
     */
    private Map<MobEntry, Integer> calculateMissingMobs() {
        Map<MobEntry, Integer> missing = new HashMap<>();

        for (MobEntry entry : mobEntries) {
            int desired = entry.getAmount();
            int current = getCurrentCountForEntry(entry);
            int stillMissing = Math.max(0, desired - current);
            missing.put(entry, stillMissing);
        }

        return missing;
    }

    /**
     * Get current count of mobs (active + respawning) for a specific entry
     */
    private int getCurrentCountForEntry(MobEntry entry) {
        int count = 0;

        // Count active mobs
        for (SpawnedMob mob : activeMobs.values()) {
            if (mob.getMobType().equals(entry.getMobType()) &&
                    mob.getTier() == entry.getTier() &&
                    mob.isElite() == entry.isElite()) {
                count++;
            }
        }

        // Count respawning mobs
        for (SpawnedMob mob : respawningMobs.values()) {
            if (mob.getMobType().equals(entry.getMobType()) &&
                    mob.getTier() == entry.getTier() &&
                    mob.isElite() == entry.isElite()) {
                count++;
            }
        }

        return count;
    }

    /**
     * FIXED: Spawn a single mob through MobManager with proper error handling
     */
    private boolean spawnSingleMob(MobEntry entry) {
        try {
            // Get a spawn location near the spawner
            Location spawnLoc = getRandomSpawnLocation();

            // FIXED: Use the MobManager's spawner-specific spawn method
            LivingEntity entity = mobManager.spawnMobFromSpawner(spawnLoc, entry.getMobType(), entry.getTier(), entry.isElite());

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
            } else {
                if (isDebugMode()) {
                    logger.warning("[Spawner] MobManager returned null entity for " + entry.getMobType());
                }
            }
        } catch (Exception e) {
            logger.warning("[Spawner] Failed to spawn mob " + entry.getMobType() +
                    " at " + formatLocation() + ": " + e.getMessage());
            metrics.recordFailedSpawn();
        }

        return false;
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
        int totalCurrent = getTotalActiveMobs() + getQueuedCount();
        int maxAllowed = getMaxMobsAllowed();
        return totalCurrent >= maxAllowed;
    }

    /**
     * Check if this spawner has a group assigned
     */
    private boolean hasSpawnerGroup() {
        return properties.getSpawnerGroup() != null && !properties.getSpawnerGroup().isEmpty();
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

        // Check if the location is valid
        if (spawnLoc.getBlock().getType().isSolid()) {
            // Try to find a safe spot above
            for (int y = 0; y < 3; y++) {
                Location adjusted = spawnLoc.clone().add(0, y + 1, 0);
                if (!adjusted.getBlock().getType().isSolid()) {
                    return adjusted;
                }
            }
            // Fallback to center with height offset
            return location.clone().add(0, 1, 0);
        }

        return spawnLoc;
    }

    /**
     * Check if this spawner is in a loaded chunk
     */
    private boolean isWorldAndChunkLoaded() {
        return location.getWorld() != null &&
                location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Check if this spawner can spawn based on time and weather restrictions
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
     */
    private boolean isPlayerNearby() {
        double range = properties.getPlayerDetectionRangeOverride() > 0 ?
                properties.getPlayerDetectionRangeOverride() :
                MobManager.getInstance().getPlayerDetectionRange();

        return MobUtils.isPlayerNearby(location, range);
    }

    /**
     * Remove references to mobs that no longer exist
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
        }
    }

    /**
     * FIXED: Simple synchronous mob death registration
     */
    public void registerMobDeath(UUID entityId) {
        SpawnedMob mob = activeMobs.remove(entityId);

        if (mob != null) {
            // Record kill in metrics
            metrics.recordKill();

            // Calculate respawn time
            long respawnDelay = calculateRespawnDelay(mob.getTier(), mob.isElite());
            long respawnTime = System.currentTimeMillis() + respawnDelay;

            // Set respawn time and add to respawning queue
            mob.setRespawnTime(respawnTime);
            respawningMobs.put(entityId, mob);

            needsHologramUpdate = true;

            logger.info("[Spawner] MOB DEATH: " + mob.getMobType() + " T" + mob.getTier() +
                    (mob.isElite() ? "+" : "") + " will respawn in " + (respawnDelay / 1000) + " seconds");
        }
    }

    /**
     * Calculate respawn delay for a mob
     */
    private long calculateRespawnDelay(int tier, boolean elite) {
        // Base delay of 3 minutes
        long baseDelay = 180000L; // 3 minutes

        // Tier factor
        double tierFactor = 1.0 + ((tier - 1) * 0.2);

        // Elite multiplier
        double eliteMultiplier = elite ? 1.5 : 1.0;

        long calculatedDelay = (long) (baseDelay * tierFactor * eliteMultiplier);

        // Add some randomness (±10%)
        double randomFactor = 0.9 + (Math.random() * 0.2);
        calculatedDelay = (long) (calculatedDelay * randomFactor);

        // Clamp between 3 and 15 minutes
        return Math.min(Math.max(calculatedDelay, 180000L), 900000L);
    }

    /**
     * Find a matching mob entry for a spawned mob
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
        activeMobs.clear();
        respawningMobs.clear();
        metrics.reset();
        needsHologramUpdate = true;

        if (visible) {
            // Force immediate hologram update on reset
            lastHologramUpdate = 0;
            updateHologram();
        }

        if (isDebugMode()) {
            logger.info("[Spawner] Spawner at " + formatLocation() + " has been reset");
        }
    }

    /**
     * FIXED: Create or update hologram for this spawner with proper error handling
     */
    public void updateHologram() {
        try {
            if (!isVisible()) {
                removeHologram();
                return;
            }

            List<String> lines = generateHologramLines();
            String holoId = "spawner_" + uniqueId;

            // Adjust height based on display mode
            double yOffset = switch (displayMode) {
                case 0 -> 1.5; // Basic mode - lower
                case 1 -> 2.5; // Detailed mode - medium
                case 2 -> 3.5; // Admin mode - higher
                default -> 2.0;
            };

            Location holoLocation = location.clone().add(0, yOffset, 0);

            HologramManager.createOrUpdateHologram(
                    holoId,
                    holoLocation,
                    lines,
                    0.25);

            if (isDebugMode()) {
                logger.info("[Spawner] Updated hologram with " + lines.size() + " lines at " + formatLocation());
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Error updating hologram: " + e.getMessage());
            if (isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * FIXED: Generate hologram lines based on current state with better formatting
     */
    private List<String> generateHologramLines() {
        List<String> lines = new ArrayList<>();

        try {
            // Title
            lines.add(getDisplayTitle());

            // Group info if applicable
            addGroupInfoIfPresent(lines);

            // Basic info
            lines.add(ChatColor.YELLOW + formatSpawnerData());

            // Status line
            addStatusLine(lines);

            // Add detailed stats if in appropriate display mode
            if (displayMode >= 1) {
                addDetailedStats(lines);
            }

            // Add admin info if in admin mode
            if (displayMode >= 2) {
                addAdminInfo(lines);
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Error generating hologram lines: " + e.getMessage());
            lines.clear();
            lines.add(ChatColor.RED + "Error generating hologram");
        }

        return lines;
    }

    /**
     * Get display title for the hologram
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
     */
    private void addGroupInfoIfPresent(List<String> lines) {
        if (properties.getSpawnerGroup() != null && !properties.getSpawnerGroup().isEmpty()) {
            lines.add(ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
        }
    }

    /**
     * Add a status line showing active mobs
     */
    private void addStatusLine(List<String> lines) {
        int activeMobCount = getTotalActiveMobs();
        int respawningCount = getQueuedCount();
        int totalDesired = getTotalDesiredMobs();

        if (activeMobCount > 0 || respawningCount > 0) {
            lines.add(ChatColor.RED + "Active: " + ChatColor.WHITE + activeMobCount +
                    ChatColor.GRAY + " | Respawning: " + ChatColor.WHITE + respawningCount +
                    ChatColor.GRAY + " | Target: " + ChatColor.WHITE + totalDesired);
        } else {
            lines.add(ChatColor.GRAY + "Target: " + ChatColor.WHITE + totalDesired + " mobs");
        }
    }

    /**
     * Add detailed stats for display mode 1+
     */
    private void addDetailedStats(List<String> lines) {
        if (metrics.getTotalSpawned() > 0) {
            lines.add(ChatColor.GRAY + "Spawned: " + metrics.getTotalSpawned() +
                    " | Killed: " + metrics.getTotalKilled());
        }

        if (!respawningMobs.isEmpty()) {
            long nextRespawnTime = getNextRespawnTime();
            if (nextRespawnTime > 0) {
                long timeRemaining = nextRespawnTime - System.currentTimeMillis();
                if (timeRemaining > 0) {
                    lines.add(ChatColor.YELLOW + "Next respawn: " + ChatColor.WHITE +
                            formatTimeRemaining(timeRemaining));
                } else {
                    lines.add(ChatColor.GREEN + "Respawning now!");
                }
            }
        }
    }

    /**
     * Add admin info for display mode 2+
     */
    private void addAdminInfo(List<String> lines) {
        if (!activeMobs.isEmpty()) {
            lines.add(ChatColor.GOLD + "Active mob counts:");
            Map<String, Integer> typeCounts = getActiveMobTypeCounts();
            for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                lines.add(ChatColor.GRAY + "  " + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
            }
        }

        lines.add(ChatColor.DARK_GRAY + formatLocation());
        int maxMobs = getMaxMobsAllowed();
        int currentTotal = getTotalActiveMobs() + getQueuedCount();
        lines.add(ChatColor.DARK_GRAY + "Capacity: " + currentTotal + "/" + maxMobs);
    }

    /**
     * Get counts of active mobs by type
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
     * Remove hologram for this spawner
     */
    public void removeHologram() {
        try {
            String holoId = "spawner_" + uniqueId;
            HologramManager.removeHologram(holoId);

            if (isDebugMode()) {
                logger.info("[Spawner] Removed hologram for " + formatLocation());
            }
        } catch (Exception e) {
            logger.warning("[Spawner] Error removing hologram: " + e.getMessage());
        }
    }

    /**
     * Format mob entries for display
     */
    private String formatSpawnerData() {
        if (mobEntries.isEmpty()) {
            return "Empty";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(mobEntries.size(), 3); i++) {
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

        String name = type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
        name = name.replace("_", " ");

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
     * Format time remaining in a readable format
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
     * Get the next respawn time
     */
    private long getNextRespawnTime() {
        return respawningMobs.values().stream()
                .mapToLong(SpawnedMob::getRespawnTime)
                .filter(time -> time > 0)
                .min()
                .orElse(0);
    }

    /**
     * Check if debug mode is enabled
     */
    private boolean isDebugMode() {
        return YakRealms.getInstance().isDebugMode();
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

        if (properties.getSpawnerGroup() != null && !properties.getSpawnerGroup().isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE + formatSpawnerData());

        int activeMobs = getTotalActiveMobs();
        int respawningMobs = getQueuedCount();
        int desiredMobs = getTotalDesiredMobs();
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Status: " +
                ChatColor.WHITE + activeMobs + " active, " + respawningMobs + " respawning, " + desiredMobs + " target");

        if (respawningMobs > 0) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Next respawn: " +
                    ChatColor.WHITE + formatTimeRemaining(getNextRespawnTime() - System.currentTimeMillis()));
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Visible: " +
                (visible ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        if (properties.isTimeRestricted()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Time Restriction: " +
                    ChatColor.WHITE + properties.getStartHour() + "h - " + properties.getEndHour() + "h");
        }

        if (properties.isWeatherRestricted()) {
            String weather = "";
            if (properties.canSpawnInClear()) weather += "Clear ";
            if (properties.canSpawnInRain()) weather += "Rain ";
            if (properties.canSpawnInThunder()) weather += "Thunder";
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Weather Restriction: " + ChatColor.WHITE + weather);
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Radius: " +
                ChatColor.WHITE + properties.getSpawnRadiusX() + "x" +
                properties.getSpawnRadiusY() + "x" +
                properties.getSpawnRadiusZ());

        int maxMobs = getMaxMobsAllowed();
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Capacity: " +
                ChatColor.WHITE + (activeMobs + respawningMobs) + "/" + maxMobs);

        if (metrics.getTotalSpawned() > 0) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Total Spawned: " +
                    ChatColor.WHITE + metrics.getTotalSpawned());
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Total Killed: " +
                    ChatColor.WHITE + metrics.getTotalKilled());
        }

        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════╝");
    }

    // ================ GETTERS AND SETTERS ================

    public Location getLocation() { return location.clone(); }

    public boolean isVisible() { return visible; }

    public void setVisible(boolean visible) {
        this.visible = visible;
        needsHologramUpdate = true;
        if (visible) {
            // Force immediate update when making visible
            lastHologramUpdate = 0;
            updateHologram();
        } else {
            removeHologram();
        }
    }

    public SpawnerProperties getProperties() { return properties; }

    public void setProperties(SpawnerProperties properties) {
        this.properties = properties;
        needsHologramUpdate = true;
    }

    public int getDisplayMode() { return displayMode; }

    public void setDisplayMode(int displayMode) {
        this.displayMode = Math.max(0, Math.min(2, displayMode));
        needsHologramUpdate = true;
        // Force immediate update when changing display mode
        lastHologramUpdate = 0;
        if (visible) {
            updateHologram();
        }
    }

    public SpawnerMetrics getMetrics() { return metrics; }
    public List<MobEntry> getMobEntries() { return new ArrayList<>(mobEntries); }
    public String getUniqueId() { return uniqueId; }
    public Collection<SpawnedMob> getActiveMobs() { return new ArrayList<>(activeMobs.values()); }
    public Collection<SpawnedMob> getQueuedMobs() { return new ArrayList<>(respawningMobs.values()); }

    public int getTotalActiveMobs() {
        cleanupInvalidMobs();
        return activeMobs.size();
    }

    public int getQueuedCount() {
        return respawningMobs.size();
    }

    public int getTotalDesiredMobs() {
        return mobEntries.stream().mapToInt(MobEntry::getAmount).sum();
    }

    public int getMaxMobsAllowed() {
        return properties.getMaxMobOverride() > 0 ?
                properties.getMaxMobOverride() :
                MobManager.getInstance().getMaxMobsPerSpawner();
    }

    public boolean ownsMob(UUID entityId) {
        return activeMobs.containsKey(entityId);
    }
}