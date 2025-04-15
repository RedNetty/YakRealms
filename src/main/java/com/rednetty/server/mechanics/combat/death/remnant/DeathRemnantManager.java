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

    public DeathRemnant createDeathRemnant(Location location, List<ItemStack> items, Player player) {
        DeathRemnant remnant = new DeathRemnant(
                location,
                new ArrayList<>(items),
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

    public void shutdown() {
        activeRemnants.values().forEach(DeathRemnant::remove);
        activeRemnants.clear();
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
    }
}