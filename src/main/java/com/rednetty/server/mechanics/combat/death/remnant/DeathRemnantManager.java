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

/**
 * FIXED: Manages decorative death remnants that create visual skeleton displays
 * Death remnants no longer contain actual items - items drop normally on the ground
 */
public class DeathRemnantManager {
    private static final String REMNANT_ID_KEY = "death_remnant_id";
    private static final int AUTO_REMOVE_SECONDS = 3600; // 1 hour
    private static final int CLEANUP_INTERVAL_TICKS = 6000; // 5 minute

    private final YakRealms plugin;
    private final NamespacedKey remnantKey;
    private final Map<UUID, DeathRemnant> activeRemnants;
    private final Set<BukkitTask> activeTasks;

    public DeathRemnantManager(YakRealms plugin) {
        this.plugin = plugin;
        this.remnantKey = new NamespacedKey(plugin, REMNANT_ID_KEY);
        this.activeRemnants = new ConcurrentHashMap<>();
        this.activeTasks = new HashSet<>();

        initializeCleanupTask();
    }

    /**
     * FIXED: Creates a death remnant - items parameter can be null or empty for decorative-only remnants
     * @param location The location to create the remnant
     * @param items The items to display (can be null/empty for purely decorative remnant)
     * @param player The player who died
     * @return The created death remnant, or null if creation failed
     */
    public DeathRemnant createDeathRemnant(Location location, List<ItemStack> items, Player player) {
        // Handle null items list
        List<ItemStack> safeItems = (items != null) ? new ArrayList<>(items) : Collections.emptyList();

        DeathRemnant remnant = new DeathRemnant(
                location,
                safeItems,
                player.getUniqueId(),
                player.getName(),
                remnantKey
        );

        if (!remnant.isValid()) {
            plugin.getLogger().warning("Failed to create valid remnant for " + player.getName());
            return null;
        }

        activeRemnants.put(remnant.getId(), remnant);
        tagRemnantEntities(remnant);
        scheduleAutoRemoval(remnant.getId());

        // Log creation details
        if (remnant.isDecorativeOnly()) {
            plugin.getLogger().info("Created decorative death remnant for " + player.getName() +
                    " at " + formatLocation(location));
        } else {
            plugin.getLogger().info("Created death remnant with " + remnant.getItemCount() +
                    " items for " + player.getName() + " at " + formatLocation(location));
        }

        return remnant;
    }

    public boolean removeRemnant(UUID remnantId) {
        DeathRemnant remnant = activeRemnants.remove(remnantId);
        if (remnant == null) return false;

        remnant.remove();
        plugin.getLogger().info("Removed death remnant: " + remnantId);
        return true;
    }

    private void initializeCleanupTask() {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphanedEntities();
                removeExpiredRemnants();
            }
        }.runTaskTimer(plugin, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);

        activeTasks.add(task);
    }

    private void cleanupOrphanedEntities() {
        int cleaned = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isRemnantEntity(entity) && !isManagedEntity(entity)) {
                    entity.remove();
                    cleaned++;
                }
            }
        }
        if (cleaned > 0) {
            plugin.getLogger().info("Cleaned " + cleaned + " orphaned remnant entities");
        }
    }

    private void removeExpiredRemnants() {
        Iterator<Map.Entry<UUID, DeathRemnant>> iterator = activeRemnants.entrySet().iterator();
        while (iterator.hasNext()) {
            DeathRemnant remnant = iterator.next().getValue();
            if (!isRemnantValid(remnant)) {
                remnant.remove();
                iterator.remove();
            }
        }
    }

    private void tagRemnantEntities(DeathRemnant remnant) {
        remnant.getEntityCollection().forEach(entity ->
                entity.getPersistentDataContainer().set(
                        remnantKey,
                        PersistentDataType.STRING,
                        remnant.getId().toString()
                )
        );
    }

    private void scheduleAutoRemoval(UUID remnantId) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                removeRemnant(remnantId);
            }
        }.runTaskLater(plugin, AUTO_REMOVE_SECONDS * 20L);

        activeTasks.add(task);
    }

    private boolean isRemnantEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(remnantKey, PersistentDataType.STRING);
    }

    private boolean isManagedEntity(Entity entity) {
        String idString = entity.getPersistentDataContainer().get(remnantKey, PersistentDataType.STRING);
        if (idString == null) return false;

        try {
            UUID remnantId = UUID.fromString(idString);
            return activeRemnants.containsKey(remnantId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isRemnantValid(DeathRemnant remnant) {
        return remnant.isValid() &&
                remnant.getLocation().getWorld() != null &&
                remnant.getLocation().getChunk().isLoaded();
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
     * Get statistics about active remnants
     */
    public String getStatistics() {
        int total = activeRemnants.size();
        long decorativeOnly = activeRemnants.values().stream()
                .mapToLong(r -> r.isDecorativeOnly() ? 1 : 0)
                .sum();
        long withItems = total - decorativeOnly;

        return String.format("Death Remnants - Total: %d, Decorative: %d, With Items: %d",
                total, decorativeOnly, withItems);
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down death remnant manager - " + getStatistics());

        activeRemnants.values().forEach(DeathRemnant::remove);
        activeRemnants.clear();
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
    }
}