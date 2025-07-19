package com.rednetty.server.mechanics.player.utils;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Emergency backup system for combat logout processing
 * Creates backups before processing and provides restoration capabilities
 */
public class CombatLogoutBackup {

    // Backup storage
    private static final ConcurrentHashMap<UUID, BackupData> playerBackups = new ConcurrentHashMap<>();

    // Statistics
    private static final AtomicInteger backupsCreated = new AtomicInteger(0);
    private static final AtomicInteger backupsRestored = new AtomicInteger(0);
    private static final AtomicInteger backupsExpired = new AtomicInteger(0);

    // Configuration
    private static final long BACKUP_EXPIRY_TIME = 3600000; // 1 hour in milliseconds
    private static final long CLEANUP_INTERVAL = 300000; // 5 minutes in milliseconds

    /**
     * Initialize the backup system
     */
    public static void initialize() {
        // Start cleanup task
        new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(),
                CLEANUP_INTERVAL / 50,
                CLEANUP_INTERVAL / 50);

        YakRealms.log("CombatLogoutBackup system initialized");
    }

    /**
     * Create backup of current player state before processing
     */
    public static boolean createBackup(Player player, YakPlayer yakPlayer) {
        try {
            UUID playerId = player.getUniqueId();

            // Remove any existing backup for this player
            if (playerBackups.containsKey(playerId)) {
                YakRealms.log("Replacing existing backup for player: " + player.getName());
                playerBackups.remove(playerId);
            }

            // Create new backup
            BackupData backup = new BackupData(yakPlayer);
            playerBackups.put(playerId, backup);

            backupsCreated.incrementAndGet();
            YakRealms.log("Created combat logout backup for: " + player.getName());

            return true;

        } catch (Exception e) {
            YakRealms.error("Failed to create combat logout backup for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Restore backup for a player
     */
    public static boolean restoreBackup(Player player, YakPlayer yakPlayer) {
        try {
            UUID playerId = player.getUniqueId();
            BackupData backup = playerBackups.remove(playerId);

            if (backup == null) {
                YakRealms.log("No backup found for player: " + player.getName());
                return false;
            }

            if (backup.isExpired()) {
                YakRealms.warn("Backup expired for player: " + player.getName());
                backupsExpired.incrementAndGet();
                return false;
            }

            // Restore inventory data
            yakPlayer.setSerializedInventory(backup.inventoryData);
            yakPlayer.setSerializedArmor(backup.armorData);
            yakPlayer.setSerializedOffhand(backup.offhandData);

            // Restore other state
            yakPlayer.setAlignment(backup.alignment);
            yakPlayer.setHealth(backup.health);
            yakPlayer.setMaxHealth(backup.maxHealth);
            yakPlayer.setCombatLogoutState(backup.originalState);

            backupsRestored.incrementAndGet();
            YakRealms.log("Successfully restored backup for: " + player.getName());

            return true;

        } catch (Exception e) {
            YakRealms.error("Failed to restore backup for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Check if a backup exists for a player
     */
    public static boolean hasBackup(UUID playerId) {
        BackupData backup = playerBackups.get(playerId);
        return backup != null && !backup.isExpired();
    }

    /**
     * Get backup age in milliseconds
     */
    public static long getBackupAge(UUID playerId) {
        BackupData backup = playerBackups.get(playerId);
        if (backup == null) {
            return -1;
        }
        return System.currentTimeMillis() - backup.timestamp;
    }

    /**
     * Remove backup for a player (called when no longer needed)
     */
    public static void removeBackup(UUID playerId) {
        playerBackups.remove(playerId);
    }

    /**
     * Perform cleanup of expired backups
     */
    private static void performCleanup() {
        try {
            int initialSize = playerBackups.size();

            // Remove expired backups
            playerBackups.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    backupsExpired.incrementAndGet();
                    return true;
                }
                return false;
            });

            int removed = initialSize - playerBackups.size();
            if (removed > 0) {
                YakRealms.log("Cleaned up " + removed + " expired combat logout backups");
            }

        } catch (Exception e) {
            YakRealms.error("Error during combat logout backup cleanup", e);
        }
    }

    /**
     * Emergency cleanup - remove all backups
     */
    public static void emergencyCleanup() {
        try {
            int cleared = playerBackups.size();
            playerBackups.clear();

            YakRealms.warn("Emergency cleanup: cleared " + cleared + " combat logout backups");

        } catch (Exception e) {
            YakRealms.error("Error during emergency cleanup", e);
        }
    }

    /**
     * Get backup statistics
     */
    public static BackupStats getStats() {
        return new BackupStats(
                backupsCreated.get(),
                backupsRestored.get(),
                backupsExpired.get(),
                playerBackups.size()
        );
    }

    /**
     * Log backup statistics
     */
    public static void logStats() {
        BackupStats stats = getStats();
        YakRealms.log("=== Combat Logout Backup Stats ===");
        YakRealms.log("Created: " + stats.created);
        YakRealms.log("Restored: " + stats.restored);
        YakRealms.log("Expired: " + stats.expired);
        YakRealms.log("Active: " + stats.active);
        YakRealms.log("===============================");
    }

    /**
     * Validate backup integrity
     */
    public static boolean validateBackup(UUID playerId) {
        try {
            BackupData backup = playerBackups.get(playerId);
            if (backup == null) {
                return false;
            }

            // Check if backup is expired
            if (backup.isExpired()) {
                return false;
            }

            // Basic validation of backup data
            if (backup.alignment == null || backup.alignment.trim().isEmpty()) {
                YakRealms.warn("Invalid alignment in backup for player: " + playerId);
                return false;
            }

            if (backup.maxHealth <= 0) {
                YakRealms.warn("Invalid max health in backup for player: " + playerId);
                return false;
            }

            return true;

        } catch (Exception e) {
            YakRealms.error("Error validating backup for player: " + playerId, e);
            return false;
        }
    }

    /**
     * Shutdown the backup system
     */
    public static void shutdown() {
        try {
            // Log final stats
            logStats();

            // Clear all backups
            emergencyCleanup();

            YakRealms.log("CombatLogoutBackup system shut down");

        } catch (Exception e) {
            YakRealms.error("Error during backup system shutdown", e);
        }
    }

    /**
     * Backup data structure
     */
    private static class BackupData {
        final long timestamp;
        final String inventoryData;
        final String armorData;
        final String offhandData;
        final String alignment;
        final double health;
        final double maxHealth;
        final YakPlayer.CombatLogoutState originalState;

        BackupData(YakPlayer yakPlayer) {
            this.timestamp = System.currentTimeMillis();
            this.inventoryData = yakPlayer.getSerializedInventory();
            this.armorData = yakPlayer.getSerializedArmor();
            this.offhandData = yakPlayer.getSerializedOffhand();
            this.alignment = yakPlayer.getAlignment();
            this.health = yakPlayer.getHealth();
            this.maxHealth = yakPlayer.getMaxHealth();
            this.originalState = yakPlayer.getCombatLogoutState();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > BACKUP_EXPIRY_TIME;
        }
    }

    /**
     * Statistics class
     */
    public static class BackupStats {
        public final int created;
        public final int restored;
        public final int expired;
        public final int active;

        BackupStats(int created, int restored, int expired, int active) {
            this.created = created;
            this.restored = restored;
            this.expired = expired;
            this.active = active;
        }

        @Override
        public String toString() {
            return String.format("BackupStats{created=%d, restored=%d, expired=%d, active=%d}",
                    created, restored, expired, active);
        }
    }
}