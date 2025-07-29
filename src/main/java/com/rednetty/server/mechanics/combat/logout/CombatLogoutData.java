package com.rednetty.server.mechanics.combat.logout;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * FIXED Combat logout data tracking for FIXED combat logout system
 *
 * SIMPLIFIED APPROACH:
 * - Tracks combat logout processing state
 * - Records what items were dropped vs kept
 * - Maintains logout location for item dropping
 * - Simple state management for completion tracking
 */
public class CombatLogoutData implements ConfigurationSerializable {
    public final long timestamp;
    public final UUID attackerUUID;
    public final Location logoutLocation;
    public final String alignment;
    public final List<ItemStack> droppedItems;
    public final List<ItemStack> keptItems;
    public final String world;
    public final double x, y, z;
    public final float yaw, pitch;

    // Processing state tracking
    public boolean itemsProcessed = false;
    public boolean deathNotificationSent = false;
    public boolean spawnLocationSet = false;

    public CombatLogoutData(long timestamp, UUID attackerUUID, Location logoutLocation, String alignment) {
        this.timestamp = timestamp;
        this.attackerUUID = attackerUUID;
        this.logoutLocation = logoutLocation.clone();
        this.alignment = alignment;
        this.droppedItems = new ArrayList<>();
        this.keptItems = new ArrayList<>();

        // Store exact location data
        this.world = logoutLocation.getWorld().getName();
        this.x = logoutLocation.getX();
        this.y = logoutLocation.getY();
        this.z = logoutLocation.getZ();
        this.yaw = logoutLocation.getYaw();
        this.pitch = logoutLocation.getPitch();
    }

    // Convenience constructor for new instances
    public CombatLogoutData(Player attacker, Location logoutLocation, String alignment) {
        this(System.currentTimeMillis(), (attacker != null ? attacker.getUniqueId() : null), logoutLocation, alignment);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();

        // Basic data
        map.put("timestamp", timestamp);
        if (attackerUUID != null) {
            map.put("attackerUUID", attackerUUID.toString());
        }
        map.put("alignment", alignment);

        // Item data
        map.put("droppedItems", new ArrayList<>(droppedItems));
        map.put("keptItems", new ArrayList<>(keptItems));

        // Location data
        map.put("world", world);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("yaw", yaw);
        map.put("pitch", pitch);

        // State data
        map.put("itemsProcessed", itemsProcessed);
        map.put("deathNotificationSent", deathNotificationSent);
        map.put("spawnLocationSet", spawnLocationSet);

        return map;
    }

    public static CombatLogoutData deserialize(Map<String, Object> map) {
        long timestamp = (Long) map.get("timestamp");
        UUID attackerUUID = map.containsKey("attackerUUID") ? UUID.fromString((String) map.get("attackerUUID")) : null;
        String alignment = (String) map.get("alignment");
        String world = (String) map.get("world");
        double x = (Double) map.get("x");
        double y = (Double) map.get("y");
        double z = (Double) map.get("z");
        float yaw = ((Double) map.getOrDefault("yaw", 0.0)).floatValue();
        float pitch = ((Double) map.getOrDefault("pitch", 0.0)).floatValue();

        Location location = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        CombatLogoutData data = new CombatLogoutData(timestamp, attackerUUID, location, alignment);

        // Deserialize ItemStack lists
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> droppedMaps = (List<Map<String, Object>>) map.getOrDefault("droppedItems", new ArrayList<>());
        for (Map<String, Object> itemMap : droppedMaps) {
            try {
                data.droppedItems.add(ItemStack.deserialize(itemMap));
            } catch (Exception e) {
                // Skip invalid items
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keptMaps = (List<Map<String, Object>>) map.getOrDefault("keptItems", new ArrayList<>());
        for (Map<String, Object> itemMap : keptMaps) {
            try {
                data.keptItems.add(ItemStack.deserialize(itemMap));
            } catch (Exception e) {
                // Skip invalid items
            }
        }

        // Deserialize state data
        data.itemsProcessed = (Boolean) map.getOrDefault("itemsProcessed", false);
        data.deathNotificationSent = (Boolean) map.getOrDefault("deathNotificationSent", false);
        data.spawnLocationSet = (Boolean) map.getOrDefault("spawnLocationSet", false);

        return data;
    }

    // Optional: valueOf alias for deserialize (some Bukkit APIs use this)
    public static CombatLogoutData valueOf(Map<String, Object> map) {
        return deserialize(map);
    }

    /**
     * Check if combat logout processing is complete
     */
    public boolean isProcessingComplete() {
        return itemsProcessed && deathNotificationSent && spawnLocationSet;
    }

    /**
     * Get the original logout location
     */
    public Location getLogoutLocation() {
        return logoutLocation.clone();
    }

    /**
     * Get attacker name for display
     */
    public String getAttackerName() {
        if (attackerUUID == null) {
            return "unknown forces";
        }

        Player attacker = Bukkit.getPlayer(attackerUUID);
        if (attacker != null) {
            return attacker.getName();
        }

        return "offline player";
    }

    /**
     * Get summary of processing results
     */
    public String getProcessingSummary() {
        return String.format("Combat logout processed: %d items dropped, %d items kept (alignment: %s)",
                droppedItems.size(), keptItems.size(), alignment);
    }

    /**
     * Mark processing steps as complete
     */
    public void markItemsProcessed() {
        this.itemsProcessed = true;
    }

    public void markDeathNotificationSent() {
        this.deathNotificationSent = true;
    }

    public void markSpawnLocationSet() {
        this.spawnLocationSet = true;
    }

    /**
     * Get age of combat logout in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Check if combat logout is recent (within last 5 minutes)
     */
    public boolean isRecent() {
        return getAge() < 300000; // 5 minutes
    }
}