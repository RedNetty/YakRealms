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
 * FIXED: Spawner with direct respawn system - no queuing, immediate respawns when timer expires
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

    // FIXED: Replace queue system with direct respawn tracking
    private final Map<UUID, BukkitTask> pendingRespawns = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnedMob> respawningMobs = new ConcurrentHashMap<>();

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
     * FIXED: Simplified spawn all mobs - only for initial spawning
     *
     * @return Number of mobs successfully spawned
     */
    public int spawnAllMobs() {
        // Check basic prerequisites for initial spawning
        if (!canInitialSpawn()) {
            return 0;
        }

        int maxMobsAllowed = getMaxMobsAllowed();
        int currentActive = getTotalActiveMobs();
        int currentRespawning = respawningMobs.size();
        int totalCurrent = currentActive + currentRespawning;

        // Check capacity
        if (totalCurrent >= maxMobsAllowed) {
            if (isDebugMode()) {
                logger.info("[Spawner] At capacity: " + totalCurrent + "/" + maxMobsAllowed +
                        " (Active: " + currentActive + ", Respawning: " + currentRespawning + ")");
            }
            return 0;
        }

        // Calculate how many we need to spawn for initial spawn only
        int desiredTotal = getTotalDesiredMobs();
        int stillNeeded = desiredTotal - totalCurrent;
        int availableSlots = maxMobsAllowed - totalCurrent;
        int toSpawn = Math.min(availableSlots, stillNeeded);

        if (toSpawn <= 0) {
            return 0;
        }

        // Spawn missing mobs
        int totalSpawned = spawnMissingMobs(toSpawn);

        // Record metrics and update display if needed
        if (totalSpawned > 0) {
            metrics.recordSpawn(totalSpawned);

            if (isVisible()) {
                updateHologram();
            }

            if (isDebugMode()) {
                logger.info("[Spawner] INITIAL SPAWN: " + totalSpawned + " mobs at " + formatLocation() +
                        " (Now: " + (currentActive + totalSpawned) + " active, " + respawningMobs.size() + " respawning)");
            }
        }

        return totalSpawned;
    }

    /**
     * FIXED: Simplified conditions for initial spawning only
     */
    private boolean canInitialSpawn() {
        return isWorldAndChunkLoaded() &&
                canSpawnByTimeAndWeather() &&
                isPlayerNearby();
    }

    /**
     * Spawn missing mobs based on desired configuration
     */
    private int spawnMissingMobs(int maxToSpawn) {
        int totalSpawned = 0;

        // Calculate what we're missing for each mob type
        Map<MobEntry, Integer> missingCounts = calculateMissingMobs();

        // Sort by priority (could be based on amount, tier, etc.)
        List<Map.Entry<MobEntry, Integer>> sortedMissing = missingCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<MobEntry, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        // Spawn mobs in order of priority
        for (Map.Entry<MobEntry, Integer> entry : sortedMissing) {
            if (totalSpawned >= maxToSpawn) {
                break;
            }

            MobEntry mobEntry = entry.getKey();
            int missing = entry.getValue();
            int toSpawnForThisType = Math.min(missing, maxToSpawn - totalSpawned);

            for (int i = 0; i < toSpawnForThisType; i++) {
                if (spawnMob(mobEntry)) {
                    totalSpawned++;
                } else {
                    break; // Stop trying this type if spawning fails
                }
            }
        }

        return totalSpawned;
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
        int respawningCount = respawningMobs.size();
        int totalDesired = getTotalDesiredMobs();

        // FIXED: Show comprehensive status
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

        // FIXED: Show respawn timer info more clearly
        if (!respawningMobs.isEmpty()) {
            int respawningMobCount = respawningMobs.size();
            lines.add(ChatColor.YELLOW + "Respawning: " + ChatColor.WHITE + respawningMobCount + " mobs");

            // Show time until next respawn if any
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
        } else {
            // Better status when no respawns pending
            int currentMobs = getTotalActiveMobs();
            int desiredMobs = getTotalDesiredMobs();

            if (currentMobs < desiredMobs) {
                int missing = desiredMobs - currentMobs;
                lines.add(ChatColor.YELLOW + "Need " + missing + " more mobs");
            } else if (currentMobs >= desiredMobs) {
                lines.add(ChatColor.GREEN + "All mobs spawned");
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

        // Add capacity info
        int maxMobs = getMaxMobsAllowed();
        int currentTotal = getTotalActiveMobs() + respawningMobs.size();
        lines.add(ChatColor.DARK_GRAY + "Capacity: " + currentTotal + "/" + maxMobs);
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
     * FIXED: Direct respawn system - register death and schedule immediate respawn
     *
     * @param entityId UUID of the entity that died
     */
    public void registerMobDeath(UUID entityId) {
        SpawnedMob mob = activeMobs.remove(entityId);

        if (mob != null) {
            // Record kill in metrics
            metrics.recordKill();

            // Get respawn delay from MobManager
            long respawnDelay = MobManager.getInstance().calculateRespawnDelay(mob.getTier(), mob.isElite());

            // FIXED: Store the mob for respawning and schedule direct respawn
            respawningMobs.put(entityId, mob);

            // Schedule the respawn task
            BukkitTask respawnTask = Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                executeDirectRespawn(entityId, mob);
            }, respawnDelay / 50); // Convert milliseconds to ticks

            // Track the task so we can cancel it if needed
            pendingRespawns.put(entityId, respawnTask);

            // Always log respawn scheduling
            logger.info("[Spawner] DIRECT RESPAWN: " + mob.getMobType() + " T" + mob.getTier() +
                    (mob.isElite() ? "+" : "") + " scheduled to respawn in " +
                    (respawnDelay / 1000) + " seconds at " + formatLocation());

            // Update hologram if visible
            if (visible) {
                updateHologram();
            }
        }
    }

    /**
     * FIXED: Execute direct respawn - guaranteed to spawn the mob
     *
     * @param originalEntityId The original entity ID
     * @param deadMob The mob that died
     */
    private void executeDirectRespawn(UUID originalEntityId, SpawnedMob deadMob) {
        try {
            // Remove from tracking
            respawningMobs.remove(originalEntityId);
            pendingRespawns.remove(originalEntityId);

            // Check basic requirements for respawn location
            if (!isWorldAndChunkLoaded()) {
                // Reschedule for later if world/chunk not loaded
                rescheduleRespawn(originalEntityId, deadMob, 5000); // Try again in 5 seconds
                return;
            }

            // Find the mob entry for this mob
            MobEntry entry = findMatchingEntry(deadMob);
            if (entry == null) {
                logger.warning("[Spawner] Could not find mob entry for respawn: " + deadMob.getMobType());
                return;
            }

            // Check if we have capacity (bypass most other restrictions for respawns)
            int currentActive = getTotalActiveMobs();
            int maxAllowed = getMaxMobsAllowed();

            if (currentActive >= maxAllowed) {
                // Reschedule if at capacity
                rescheduleRespawn(originalEntityId, deadMob, 2000); // Try again in 2 seconds
                return;
            }

            // Get spawn location
            Location spawnLoc = getRandomSpawnLocation();

            // FIXED: Force respawn regardless of MobManager cooldowns for direct respawns
            LivingEntity entity = mobManager.spawnMob(spawnLoc, entry.getMobType(), entry.getTier(), entry.isElite());

            if (entity != null) {
                // Add metadata to track which spawner this mob belongs to
                entity.setMetadata("spawner", new FixedMetadataValue(YakRealms.getInstance(), uniqueId));

                // Add group metadata if in a group
                if (hasSpawnerGroup()) {
                    entity.setMetadata("spawnerGroup", new FixedMetadataValue(
                            YakRealms.getInstance(), properties.getSpawnerGroup()));
                }

                // Track this mob as active
                SpawnedMob spawnedMob = new SpawnedMob(
                        entity.getUniqueId(), entry.getMobType(), entry.getTier(), entry.isElite());
                activeMobs.put(entity.getUniqueId(), spawnedMob);

                // Track in metrics
                metrics.recordSpawnByType(1, entry.getTier(), entry.getMobType());

                // Update hologram
                if (visible) {
                    updateHologram();
                }

                logger.info("[Spawner] RESPAWN SUCCESS: " + entry.getMobType() + " T" + entry.getTier() +
                        (entry.isElite() ? "+" : "") + " respawned at " + formatLocation());

            } else {
                // Respawn failed, try again later
                logger.warning("[Spawner] RESPAWN FAILED: " + entry.getMobType() + " - retrying in 3 seconds");
                rescheduleRespawn(originalEntityId, deadMob, 3000);
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Error in direct respawn: " + e.getMessage());
            // Try to reschedule on error
            rescheduleRespawn(originalEntityId, deadMob, 5000);
        }
    }

    /**
     * Reschedule a respawn for later
     *
     * @param entityId The entity ID
     * @param mob The mob to respawn
     * @param delayMs Delay in milliseconds
     */
    private void rescheduleRespawn(UUID entityId, SpawnedMob mob, long delayMs) {
        // Put back in respawning tracking
        respawningMobs.put(entityId, mob);

        // Schedule new task
        BukkitTask respawnTask = Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            executeDirectRespawn(entityId, mob);
        }, delayMs / 50); // Convert to ticks

        // Track the task
        pendingRespawns.put(entityId, respawnTask);

        if (isDebugMode()) {
            logger.info("[Spawner] RESCHEDULED: " + mob.getMobType() + " respawn in " + (delayMs / 1000) + " seconds");
        }
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
     * REMOVED: processRespawns() method - no longer needed with direct respawn system
     */

    /**
     * Reset this spawner's state
     */
    public void reset() {
        // Cancel all pending respawn tasks
        for (BukkitTask task : pendingRespawns.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        // Clear all tracking
        activeMobs.clear();
        respawningMobs.clear();
        pendingRespawns.clear();

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
     * REMOVED: hasRespawningMobs() - replaced with simpler tracking
     */

    /**
     * Get the number of mobs waiting to respawn
     *
     * @return Respawn count
     */
    public int getQueuedCount() {
        return respawningMobs.size();
    }

    /**
     * Get the next respawn time
     *
     * @return Next respawn time or 0 if none
     */
    private long getNextRespawnTime() {
        // Since we're using scheduled tasks, we can't easily get the exact time
        // Just return current time if there are respawns pending
        return respawningMobs.isEmpty() ? 0 : System.currentTimeMillis() + 1000;
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

        // FIXED: Better status display
        int activeMobs = getTotalActiveMobs();
        int respawningMobs = getQueuedCount();
        int desiredMobs = getTotalDesiredMobs();
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Status: " +
                ChatColor.WHITE + activeMobs + " active, " + respawningMobs + " respawning, " + desiredMobs + " target");

        // Ready to spawn
        if (!this.respawningMobs.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Respawning: " +
                    ChatColor.WHITE + this.respawningMobs.size() + " mobs");
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

        // Capacity info
        int maxMobs = getMaxMobsAllowed();
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Capacity: " +
                ChatColor.WHITE + (activeMobs + respawningMobs) + "/" + maxMobs);

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
        return new ArrayList<>(respawningMobs.values());
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