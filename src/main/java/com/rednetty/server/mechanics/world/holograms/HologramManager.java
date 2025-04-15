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
            this.baseLocation = baseLocation;
            this.lineSpacing = lineSpacing;
            if(!hasCleanedHolograms) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(YakRealms.getInstance(), this::cleanInvalidHolograms, 100L);
                hasCleanedHolograms = true;
            }
            spawn(lines);
        }

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

        public void cleanInvalidHolograms() {
            // Loop through every world in the server
            for (World world : Bukkit.getWorlds()) {
                // Loop through all entities in the world
                for (Entity entity : world.getEntities()) {
                    // Check if the entity is an ArmorStand
                    if (entity instanceof ArmorStand) {
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
         *
         * @param lines The text lines for the hologram.
         */
        private void spawn(List<String> lines) {
            if (baseLocation == null || baseLocation.getWorld() == null) {
                Bukkit.getLogger().warning("Cannot spawn hologram: invalid base location.");
                return;
            }
            Location spawnLocation = baseLocation.clone();
            for (String line : lines) {
                try {
                    ArmorStand as = (ArmorStand) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);
                    as.setVisible(false);
                    as.setGravity(false);
                    as.setMarker(true);
                    as.setCustomName(line);
                    as.setCustomNameVisible(true);
                    as.setInvulnerable(true);
                    as.setSmall(true);
                    as.setMetadata("id", new FixedMetadataValue(YakRealms.getInstance(), YakRealms.getSessionID()));
                    armorStands.add(as);
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Failed to spawn hologram line: " + e.getMessage());
                }
                // Move down for the next line.
                spawnLocation.subtract(0, lineSpacing, 0);
            }
        }

        /**
         * Updates the hologram by removing existing ArmorStands and spawning new ones.
         *
         * @param newLines The new lines to display.
         */
        public void updateLines(List<String> newLines) {
            remove();
            spawn(newLines);
        }

        /**
         * Removes all ArmorStands associated with this hologram.
         */
        public void remove() {
            for (ArmorStand as : armorStands) {
                if (as != null && !as.isDead()) {
                    as.remove();
                }
            }
            armorStands.clear();
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
        if (id == null || location == null || lines == null || lines.isEmpty()) {
            Bukkit.getLogger().warning("Invalid parameters for creating hologram.");
            return;
        }
        // Remove existing hologram if present.
        removeHologram(id);
        Hologram hologram = new Hologram(location, lines, lineSpacing);
        holograms.put(id, hologram);
    }

    /**
     * Updates an existing hologram's lines.
     *
     * @param id       The hologram identifier.
     * @param newLines The new lines to update the hologram with.
     */
    public static synchronized void updateHologram(String id, List<String> newLines) {
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
        Hologram hologram = holograms.remove(id);
        if (hologram != null) {
            hologram.remove();
        }
    }

    /**
     * Removes all holograms managed by this HologramManager.
     * Call this during plugin disable or server shutdown to ensure cleanup.
     */
    public static synchronized void cleanup() {
        for (Hologram hologram : holograms.values()) {
            if (hologram != null) {
                hologram.remove();
            }
        }
        holograms.clear();
    }
}