package com.rednetty.server.mechanics.combat.death.remnant;

import com.rednetty.server.YakRealms;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * FIXED: Robust death remnant manager with comprehensive entity tracking and cleanup
 * - Implements consistent entity tagging and tracking system
 * - Enhanced cleanup that handles orphaned entities properly
 * - Death remnants are purely decorative visual elements only
 * - No item storage or management - items always drop on ground separately
 */
public class DeathRemnantManager {
    private static final String REMNANT_ID_KEY = "death_remnant_id";
    private static final String REMNANT_TYPE_KEY = "death_remnant_type";
    private static final String REMNANT_OWNER_KEY = "death_remnant_owner";
    private static final int AUTO_REMOVE_SECONDS = 3600; // 1 hour
    private static final int CLEANUP_INTERVAL_TICKS = 1200; // 1 minute
    private static final int DEEP_CLEANUP_INTERVAL_TICKS = 6000; // 5 minutes

    private final YakRealms plugin;
    private final NamespacedKey remnantIdKey;
    private final NamespacedKey remnantTypeKey;
    private final NamespacedKey remnantOwnerKey;

    // FIXED: Robust tracking system with multiple data structures
    private final Map<UUID, DeathRemnant> activeRemnants;
    private final Map<UUID, Set<UUID>> remnantEntityIds; // Remnant ID -> Set of Entity UUIDs
    private final Map<UUID, UUID> entityToRemnant; // Entity UUID -> Remnant ID
    private final Set<BukkitTask> activeTasks;
    private final Set<UUID> removalInProgress;

    // Statistics tracking
    private int totalCreated = 0;
    private int totalRemoved = 0;
    private int orphanedEntitiesCleanedUp = 0;

    public DeathRemnantManager(YakRealms plugin) {
        this.plugin = plugin;
        this.remnantIdKey = new NamespacedKey(plugin, REMNANT_ID_KEY);
        this.remnantTypeKey = new NamespacedKey(plugin, REMNANT_TYPE_KEY);
        this.remnantOwnerKey = new NamespacedKey(plugin, REMNANT_OWNER_KEY);

        this.activeRemnants = new ConcurrentHashMap<>();
        this.remnantEntityIds = new ConcurrentHashMap<>();
        this.entityToRemnant = new ConcurrentHashMap<>();
        this.activeTasks = ConcurrentHashMap.newKeySet();
        this.removalInProgress = ConcurrentHashMap.newKeySet();

        initializeCleanupTasks();
        performInitialWorldScan();
    }

    /**
     * FIXED: Creates a decorative death remnant with robust entity tracking
     * @param location The location to create the remnant
     * @param items The items list (ignored - for compatibility only)
     * @param player The player who died
     * @return The created death remnant, or null if creation failed
     */
    public DeathRemnant createDeathRemnant(Location location, List<ItemStack> items, Player player) {
        if (location == null || location.getWorld() == null || player == null) {
            plugin.getLogger().warning("Invalid parameters for death remnant creation");
            return null;
        }

        try {
            // Create purely decorative remnant (items parameter ignored)
            DeathRemnant remnant = new DeathRemnant(
                    location,
                    Collections.emptyList(), // Always empty - remnants are purely decorative
                    player.getUniqueId(),
                    player.getName(),
                    remnantIdKey
            );

            if (!remnant.isValid()) {
                plugin.getLogger().warning("Failed to create valid remnant for " + player.getName());
                return null;
            }

            // FIXED: Register remnant with comprehensive tracking
            UUID remnantId = remnant.getId();
            activeRemnants.put(remnantId, remnant);

            // Track all entities belonging to this remnant
            Set<UUID> entityIds = new HashSet<>();
            for (ArmorStand entity : remnant.getEntityCollection()) {
                UUID entityId = entity.getUniqueId();
                entityIds.add(entityId);
                entityToRemnant.put(entityId, remnantId);

                // FIXED: Tag entity with comprehensive persistent data
                tagRemnantEntity(entity, remnantId, player.getName());
            }
            remnantEntityIds.put(remnantId, entityIds);

            // Schedule automatic removal
            scheduleAutoRemoval(remnantId);

            totalCreated++;
            plugin.getLogger().info("Created decorative death remnant for " + player.getName() +
                    " at " + formatLocation(location) + " (ID: " + remnantId.toString().substring(0, 8) + "...)");

            return remnant;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating death remnant for " + player.getName(), e);
            return null;
        }
    }

    /**
     * FIXED: Safely removes a remnant with comprehensive cleanup
     */
    public boolean removeRemnant(UUID remnantId) {
        if (remnantId == null || removalInProgress.contains(remnantId)) {
            return false;
        }

        try {
            removalInProgress.add(remnantId);

            DeathRemnant remnant = activeRemnants.remove(remnantId);
            if (remnant == null) {
                // Try to clean up orphaned entities anyway
                cleanupOrphanedEntitiesForRemnant(remnantId);
                return false;
            }

            // Remove the actual remnant
            remnant.remove();

            // FIXED: Clean up all tracking data
            Set<UUID> entityIds = remnantEntityIds.remove(remnantId);
            if (entityIds != null) {
                for (UUID entityId : entityIds) {
                    entityToRemnant.remove(entityId);
                }
            }

            totalRemoved++;
            plugin.getLogger().info("Removed death remnant: " + remnantId.toString().substring(0, 8) + "...");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing death remnant " + remnantId, e);
            return false;
        } finally {
            removalInProgress.remove(remnantId);
        }
    }

    /**
     * FIXED: Initialize comprehensive cleanup tasks
     */
    private void initializeCleanupTasks() {
        // Regular cleanup task
        BukkitTask regularCleanup = new BukkitRunnable() {
            @Override
            public void run() {
                performRegularCleanup();
            }
        }.runTaskTimer(plugin, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
        activeTasks.add(regularCleanup);

        // Deep cleanup task
        BukkitTask deepCleanup = new BukkitRunnable() {
            @Override
            public void run() {
                performDeepCleanup();
            }
        }.runTaskTimer(plugin, DEEP_CLEANUP_INTERVAL_TICKS, DEEP_CLEANUP_INTERVAL_TICKS);
        activeTasks.add(deepCleanup);
    }

    /**
     * FIXED: Performs regular maintenance cleanup
     */
    private void performRegularCleanup() {
        try {
            removeInvalidRemnants();
            cleanupOrphanedEntityMappings();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during regular cleanup", e);
        }
    }

    /**
     * FIXED: Performs comprehensive deep cleanup
     */
    private void performDeepCleanup() {
        try {
            cleanupOrphanedEntities();
            validateEntityMappings();
            logStatistics();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during deep cleanup", e);
        }
    }

    /**
     * FIXED: Comprehensive orphaned entity cleanup
     */
    private void cleanupOrphanedEntities() {
        int cleaned = 0;

        for (World world : plugin.getServer().getWorlds()) {
            Collection<ArmorStand> armorStands = world.getEntitiesByClass(ArmorStand.class);

            for (ArmorStand entity : armorStands) {
                if (isRemnantEntity(entity)) {
                    UUID entityId = entity.getUniqueId();
                    UUID remnantId = entityToRemnant.get(entityId);

                    // Check if this entity belongs to a known remnant
                    if (remnantId == null || !activeRemnants.containsKey(remnantId)) {
                        // This is an orphaned remnant entity
                        try {
                            entity.remove();
                            entityToRemnant.remove(entityId);
                            cleaned++;
                            orphanedEntitiesCleanedUp++;
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Error removing orphaned entity", e);
                        }
                    }
                }
            }
        }

        if (cleaned > 0) {
            plugin.getLogger().info("Cleaned up " + cleaned + " orphaned remnant entities");
        }
    }

    /**
     * Clean up orphaned entities for a specific remnant ID
     */
    private void cleanupOrphanedEntitiesForRemnant(UUID remnantId) {
        Set<UUID> entityIds = remnantEntityIds.remove(remnantId);
        if (entityIds != null) {
            for (UUID entityId : entityIds) {
                entityToRemnant.remove(entityId);
            }
        }
    }

    /**
     * Remove invalid remnants that are no longer valid
     */
    private void removeInvalidRemnants() {
        Iterator<Map.Entry<UUID, DeathRemnant>> iterator = activeRemnants.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DeathRemnant> entry = iterator.next();
            DeathRemnant remnant = entry.getValue();

            if (!isRemnantValid(remnant)) {
                UUID remnantId = entry.getKey();
                iterator.remove();

                // Clean up tracking data
                cleanupOrphanedEntitiesForRemnant(remnantId);

                // Remove the remnant
                try {
                    remnant.remove();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error removing invalid remnant", e);
                }
            }
        }
    }

    /**
     * Clean up orphaned entity mappings
     */
    private void cleanupOrphanedEntityMappings() {
        Iterator<Map.Entry<UUID, UUID>> iterator = entityToRemnant.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            UUID remnantId = entry.getValue();

            if (!activeRemnants.containsKey(remnantId)) {
                iterator.remove();
            }
        }
    }

    /**
     * Validate that entity mappings are consistent
     */
    private void validateEntityMappings() {
        for (Map.Entry<UUID, Set<UUID>> entry : remnantEntityIds.entrySet()) {
            UUID remnantId = entry.getKey();
            Set<UUID> entityIds = entry.getValue();

            if (!activeRemnants.containsKey(remnantId)) {
                // Remove orphaned mapping
                remnantEntityIds.remove(remnantId);
                for (UUID entityId : entityIds) {
                    entityToRemnant.remove(entityId);
                }
            }
        }
    }

    /**
     * FIXED: Comprehensive entity tagging system
     */
    private void tagRemnantEntity(ArmorStand entity, UUID remnantId, String playerName) {
        try {
            entity.getPersistentDataContainer().set(remnantIdKey, PersistentDataType.STRING, remnantId.toString());
            entity.getPersistentDataContainer().set(remnantTypeKey, PersistentDataType.STRING, "death_remnant");
            entity.getPersistentDataContainer().set(remnantOwnerKey, PersistentDataType.STRING, playerName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error tagging remnant entity", e);
        }
    }

    /**
     * Schedule automatic removal of a remnant
     */
    private void scheduleAutoRemoval(UUID remnantId) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                removeRemnant(remnantId);
            }
        }.runTaskLater(plugin, AUTO_REMOVE_SECONDS * 20L);

        activeTasks.add(task);
    }

    /**
     * FIXED: Improved remnant entity detection
     */
    private boolean isRemnantEntity(ArmorStand entity) {
        try {
            return entity.getPersistentDataContainer().has(remnantIdKey, PersistentDataType.STRING) ||
                    entity.getPersistentDataContainer().has(remnantTypeKey, PersistentDataType.STRING);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a remnant is still valid
     */
    private boolean isRemnantValid(DeathRemnant remnant) {
        try {
            return remnant != null &&
                    remnant.isValid() &&
                    remnant.getLocation().getWorld() != null &&
                    remnant.getLocation().getChunk().isLoaded();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Perform initial world scan to find existing remnant entities
     */
    private void performInitialWorldScan() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int found = 0;
                for (World world : plugin.getServer().getWorlds()) {
                    for (ArmorStand entity : world.getEntitiesByClass(ArmorStand.class)) {
                        if (isRemnantEntity(entity)) {
                            // Clean up any pre-existing remnant entities from previous sessions
                            entity.remove();
                            found++;
                        }
                    }
                }
                if (found > 0) {
                    plugin.getLogger().info("Cleaned up " + found + " remnant entities from previous session");
                }
            }
        }.runTaskLater(plugin, 20L); // 1 second delay
    }

    /**
     * Log system statistics
     */
    private void logStatistics() {
        plugin.getLogger().info("Death Remnant Statistics - Active: " + activeRemnants.size() +
                ", Total Created: " + totalCreated +
                ", Total Removed: " + totalRemoved +
                ", Orphaned Cleaned: " + orphanedEntitiesCleanedUp);
    }

    /**
     * Formats a location for logging
     */
    private String formatLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "Unknown location";
        }
        return String.format("%s (%.1f, %.1f, %.1f)",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ());
    }

    // Public API methods

    public Collection<DeathRemnant> getActiveRemnants() {
        return Collections.unmodifiableCollection(activeRemnants.values());
    }

    public List<DeathRemnant> getRemnantsInWorld(World world) {
        return activeRemnants.values().stream()
                .filter(r -> r.getLocation().getWorld().equals(world))
                .toList();
    }

    public Optional<DeathRemnant> getNearestRemnant(Location location, double maxRadius) {
        return activeRemnants.values().stream()
                .filter(r -> r.getLocation().getWorld().equals(location.getWorld()))
                .filter(r -> r.getLocation().distanceSquared(location) <= maxRadius * maxRadius)
                .min(Comparator.comparingDouble(r -> r.getLocation().distanceSquared(location)));
    }

    /**
     * Get comprehensive statistics about active remnants
     */
    public String getStatistics() {
        return String.format("Death Remnants - Active: %d, Entities Tracked: %d, Total Created: %d, Total Removed: %d, Orphaned Cleaned: %d",
                activeRemnants.size(), entityToRemnant.size(), totalCreated, totalRemoved, orphanedEntitiesCleanedUp);
    }

    /**
     * FIXED: Comprehensive shutdown with full cleanup
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down death remnant manager - " + getStatistics());

        // Remove all active remnants
        for (UUID remnantId : new HashSet<>(activeRemnants.keySet())) {
            removeRemnant(remnantId);
        }

        // Cancel all tasks
        for (BukkitTask task : activeTasks) {
            try {
                task.cancel();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error canceling task during shutdown", e);
            }
        }
        activeTasks.clear();

        // Final cleanup of any remaining orphaned entities
        performDeepCleanup();

        // Clear all tracking data
        activeRemnants.clear();
        remnantEntityIds.clear();
        entityToRemnant.clear();
        removalInProgress.clear();

        plugin.getLogger().info("Death remnant manager shutdown complete");
    }

    /**
     * Force cleanup of all remnants (admin command utility)
     */
    public int forceCleanupAll() {
        int cleaned = 0;

        // Remove all tracked remnants
        for (UUID remnantId : new HashSet<>(activeRemnants.keySet())) {
            if (removeRemnant(remnantId)) {
                cleaned++;
            }
        }

        // Perform deep cleanup
        performDeepCleanup();

        return cleaned;
    }
}