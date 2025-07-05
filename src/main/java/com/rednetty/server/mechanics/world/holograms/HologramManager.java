package com.rednetty.server.mechanics.world.holograms;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A robust hologram system that uses invisible ArmorStands to display floating text.
 * Supports multi-line holograms, safe removal, and cleanup of orphaned holograms.
 *
 * Fixed to prevent ArmorStands from ever being visible during spawn.
 */
public class HologramManager {
    private static boolean hasCleanedHolograms = false;

    /**
     * Internal class representing a multi-line hologram.
     */
    public static class Hologram {
        private final List<ArmorStand> armorStands = new ArrayList<>();
        private final Location baseLocation;
        private final double lineSpacing;

        /**
         * Constructs a hologram at the given base location with specified lines.
         *
         * @param baseLocation The location at which to display the hologram.
         * @param lines        The lines of text to display.
         * @param lineSpacing  The vertical spacing between each line.
         */
        public Hologram(Location baseLocation, List<String> lines, double lineSpacing) {
            this.baseLocation = baseLocation.clone();
            this.lineSpacing = lineSpacing;

            if (!hasCleanedHolograms) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(YakRealms.getInstance(), this::cleanInvalidHolograms, 100L);
                hasCleanedHolograms = true;
            }

            spawn(lines);
        }

        /**
         * Checks if an ArmorStand belongs to the current server session.
         *
         * @param armorStand The ArmorStand to check.
         * @return true if the ArmorStand belongs to this session, false otherwise.
         */
        public boolean checkSessionID(ArmorStand armorStand) {
            if (!armorStand.hasMetadata("id")) {
                return false;
            }

            List<MetadataValue> metadataValues = armorStand.getMetadata("id");
            for (MetadataValue meta : metadataValues) {
                if (meta.asInt() == YakRealms.getSessionID()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Cleans up invalid holograms from previous server sessions.
         */
        public void cleanInvalidHolograms() {
            // Loop through every world in the server
            for (World world : Bukkit.getWorlds()) {
                // Create a copy of the entities list to avoid ConcurrentModificationException
                List<Entity> entities = new ArrayList<>(world.getEntities());

                // Loop through all entities in the world
                for (Entity entity : entities) {
                    // Check if the entity is an ArmorStand and still valid
                    if (entity instanceof ArmorStand && !entity.isDead()) {
                        ArmorStand armorStand = (ArmorStand) entity;

                        // Check if the ArmorStand has the "id" metadata
                        if (armorStand.hasMetadata("id")) {
                            if (!checkSessionID(armorStand)) {
                                armorStand.remove();
                            }
                        }
                    }
                }
            }
        }

        /**
         * Spawns the ArmorStand entities for each line.
         * Uses delayed spawning to ensure ArmorStands are never visible.
         *
         * @param lines The text lines for the hologram.
         */
        private void spawn(List<String> lines) {
            if (baseLocation == null || baseLocation.getWorld() == null) {
                Bukkit.getLogger().warning("Cannot spawn hologram: invalid base location.");
                return;
            }

            // Schedule the actual spawning for the next tick to ensure proper initialization
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                Location spawnLocation = baseLocation.clone();

                for (String line : lines) {
                    try {
                        // Spawn the ArmorStand directly at the correct location
                        ArmorStand armorStand = (ArmorStand) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);

                        // Immediately set all invisibility properties in the same tick
                        armorStand.setVisible(false);
                        armorStand.setGravity(false);
                        armorStand.setMarker(true);
                        armorStand.setInvulnerable(true);
                        armorStand.setSmall(true);
                        armorStand.setCanPickupItems(false);
                        armorStand.setCollidable(false);

                        // Set text properties
                        armorStand.setCustomName(line);
                        armorStand.setCustomNameVisible(true);

                        // Set metadata for cleanup
                        armorStand.setMetadata("id", new FixedMetadataValue(YakRealms.getInstance(), YakRealms.getSessionID()));

                        armorStands.add(armorStand);

                    } catch (Exception e) {
                        Bukkit.getLogger().warning("Failed to spawn hologram line '" + line + "': " + e.getMessage());
                        e.printStackTrace();
                    }

                    // Move down for the next line
                    spawnLocation.subtract(0, lineSpacing, 0);
                }
            });
        }

        /**
         * Updates the hologram by removing existing ArmorStands and spawning new ones.
         *
         * @param newLines The new lines to display.
         */
        public void updateLines(List<String> newLines) {
            if (newLines == null) {
                Bukkit.getLogger().warning("Cannot update hologram with null lines.");
                return;
            }

            remove();
            spawn(newLines);
        }

        /**
         * Removes all ArmorStands associated with this hologram.
         */
        public void remove() {
            // Create a copy to avoid ConcurrentModificationException
            List<ArmorStand> armorStandsCopy = new ArrayList<>(armorStands);

            for (ArmorStand armorStand : armorStandsCopy) {
                if (armorStand != null && !armorStand.isDead()) {
                    try {
                        armorStand.remove();
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("Error removing hologram ArmorStand: " + e.getMessage());
                    }
                }
            }
            armorStands.clear();
        }

        /**
         * Gets the base location of this hologram.
         *
         * @return A clone of the base location.
         */
        public Location getBaseLocation() {
            return baseLocation.clone();
        }

        /**
         * Gets the number of lines in this hologram.
         *
         * @return The number of lines.
         */
        public int getLineCount() {
            return armorStands.size();
        }

        /**
         * Checks if this hologram is valid (has living ArmorStands).
         *
         * @return true if the hologram has valid ArmorStands, false otherwise.
         */
        public boolean isValid() {
            return armorStands.stream().anyMatch(as -> as != null && !as.isDead());
        }
    }

    // Map of holograms keyed by a unique identifier (for example, vendor id).
    private static final Map<String, Hologram> holograms = new HashMap<>();

    /**
     * Creates or updates a hologram with the given identifier.
     *
     * @param id          A unique identifier for the hologram (e.g. vendor id).
     * @param location    The base location of the hologram.
     * @param lines       The lines of text to display.
     * @param lineSpacing The spacing between each line (suggested: 0.25 - 0.5).
     */
    public static synchronized void createOrUpdateHologram(String id, Location location, List<String> lines, double lineSpacing) {
        if (id == null || id.trim().isEmpty()) {
            Bukkit.getLogger().warning("Cannot create hologram: ID is null or empty.");
            return;
        }

        if (location == null || location.getWorld() == null) {
            Bukkit.getLogger().warning("Cannot create hologram: location is null or world is null.");
            return;
        }

        if (lines == null || lines.isEmpty()) {
            Bukkit.getLogger().warning("Cannot create hologram: lines are null or empty.");
            return;
        }

        if (lineSpacing < 0) {
            Bukkit.getLogger().warning("Line spacing cannot be negative, using default 0.25.");
            lineSpacing = 0.25;
        }

        // Remove existing hologram if present
        removeHologram(id);

        try {
            Hologram hologram = new Hologram(location, lines, lineSpacing);
            holograms.put(id, hologram);
        } catch (Exception e) {
            Bukkit.getLogger().severe("Failed to create hologram with id '" + id + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates or updates a hologram with default line spacing.
     *
     * @param id       A unique identifier for the hologram.
     * @param location The base location of the hologram.
     * @param lines    The lines of text to display.
     */
    public static synchronized void createOrUpdateHologram(String id, Location location, List<String> lines) {
        createOrUpdateHologram(id, location, lines, 0.25);
    }

    /**
     * Updates an existing hologram's lines.
     *
     * @param id       The hologram identifier.
     * @param newLines The new lines to update the hologram with.
     */
    public static synchronized void updateHologram(String id, List<String> newLines) {
        if (id == null || id.trim().isEmpty()) {
            Bukkit.getLogger().warning("Cannot update hologram: ID is null or empty.");
            return;
        }

        Hologram hologram = holograms.get(id);
        if (hologram != null) {
            hologram.updateLines(newLines);
        } else {
            Bukkit.getLogger().warning("No hologram found with id: " + id);
        }
    }

    /**
     * Removes the hologram identified by the given id.
     *
     * @param id The unique identifier of the hologram.
     */
    public static synchronized void removeHologram(String id) {
        if (id == null) {
            return;
        }

        Hologram hologram = holograms.remove(id);
        if (hologram != null) {
            hologram.remove();
        }
    }

    /**
     * Checks if a hologram with the given ID exists.
     *
     * @param id The hologram identifier.
     * @return true if the hologram exists, false otherwise.
     */
    public static synchronized boolean hologramExists(String id) {
        return id != null && holograms.containsKey(id);
    }

    /**
     * Gets the number of active holograms.
     *
     * @return The number of holograms.
     */
    public static synchronized int getHologramCount() {
        return holograms.size();
    }

    /**
     * Gets a copy of all hologram IDs.
     *
     * @return A list of all hologram IDs.
     */
    public static synchronized List<String> getAllHologramIds() {
        return new ArrayList<>(holograms.keySet());
    }

    /**
     * Removes all holograms managed by this HologramManager.
     * Call this during plugin disable or server shutdown to ensure cleanup.
     */
    public static synchronized void cleanup() {
        List<String> hologramIds = new ArrayList<>(holograms.keySet());

        for (String id : hologramIds) {
            Hologram hologram = holograms.get(id);
            if (hologram != null) {
                hologram.remove();
            }
        }

        holograms.clear();
        hasCleanedHolograms = false; // Reset for next server start

        Bukkit.getLogger().info("HologramManager: Cleaned up " + hologramIds.size() + " holograms.");
    }

    /**
     * Performs maintenance on all holograms, removing any that are no longer valid.
     */
    public static synchronized void performMaintenance() {
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, Hologram> entry : holograms.entrySet()) {
            Hologram hologram = entry.getValue();
            if (hologram == null || !hologram.isValid()) {
                toRemove.add(entry.getKey());
            }
        }

        for (String id : toRemove) {
            removeHologram(id);
            Bukkit.getLogger().info("Removed invalid hologram: " + id);
        }

        if (!toRemove.isEmpty()) {
            Bukkit.getLogger().info("HologramManager maintenance: Removed " + toRemove.size() + " invalid holograms.");
        }
    }
}