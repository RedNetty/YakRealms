package com.rednetty.server.mechanics.player.utils;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 *  validation and rollback system for combat logout processing
 * - Fixed overly strict online validation that caused race conditions
 * - Improved error handling and recovery mechanisms
 * - Better state validation for different processing phases
 */
public class CombatLogoutValidator {

    /**
     * Validate player state for combat logout - improved validation logic
     */
    public static boolean validatePlayerState(Player player, YakPlayer yakPlayer) {
        try {
            // Basic null checks
            if (player == null || yakPlayer == null) {
                YakRealms.warn("Null player or yakPlayer in validation");
                return false;
            }

            // Check that serialized inventory data is valid
            if (yakPlayer.getSerializedInventory() != null) {
                ItemStack[] items = yakPlayer.deserializeItemStacks(yakPlayer.getSerializedInventory());
                if (items == null) {
                    YakRealms.warn("Invalid serialized inventory for combat logout player: " + player.getName());
                    return false;
                }
            }

            // Check that serialized armor data is valid
            if (yakPlayer.getSerializedArmor() != null) {
                ItemStack[] armor = yakPlayer.deserializeItemStacks(yakPlayer.getSerializedArmor());
                if (armor == null) {
                    YakRealms.warn("Invalid serialized armor for combat logout player: " + player.getName());
                    return false;
                }
            }

            // Check that flags are consistent
            YakPlayer.CombatLogoutState state = yakPlayer.getCombatLogoutState();
            if (state == YakPlayer.CombatLogoutState.PROCESSED && player.isOnline() && player.getHealth() > 0) {
                YakRealms.warn("Combat logout processed but player not dead: " + player.getName());
                return false;
            }

            // Check for conflicting temporary data
            boolean hasOldFlags = yakPlayer.hasTemporaryData("combat_logout_death_processed") ||
                    yakPlayer.hasTemporaryData("combat_logout_needs_death") ||
                    yakPlayer.hasTemporaryData("combat_logout_session") ||
                    yakPlayer.hasTemporaryData("combat_logout_processing");

            if (hasOldFlags && state == YakPlayer.CombatLogoutState.NONE) {
                YakRealms.warn("Player has old combat logout flags but state is NONE: " + player.getName());
                return false;
            }

            return true;

        } catch (Exception e) {
            YakRealms.error("Player state validation failed: " + player.getName(), e);
            return false;
        }
    }

    /**
     * Rollback combat logout processing with  error handling
     *
     * @return
     */
    public static boolean rollbackCombatLogout(Player player, YakPlayer yakPlayer) {
        YakRealms.warn("Rolling back combat logout for: " + player.getName());

        try {
            // Reset state to NONE
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);

            // Clear ALL temporary flags (old and new system)
            yakPlayer.removeTemporaryData("combat_logout_death_processed");
            yakPlayer.removeTemporaryData("combat_logout_needs_death");
            yakPlayer.removeTemporaryData("combat_logout_session");
            yakPlayer.removeTemporaryData("combat_logout_processing");
            yakPlayer.removeTemporaryData("respawn_pending");
            yakPlayer.removeTemporaryData("respawn_items");
            yakPlayer.removeTemporaryData("prevent_healing");

            // Try to restore from backup if available
            if (CombatLogoutBackup.restoreBackup(player, yakPlayer)) {
                YakRealms.log("Successfully restored backup for rollback: " + player.getName());
            } else {
                YakRealms.warn("No backup available for rollback: " + player.getName());
            }

            // Save the corrected state
            com.rednetty.server.mechanics.player.YakPlayerManager.getInstance().savePlayer(yakPlayer);

            YakRealms.log("Combat logout rollback completed for: " + player.getName());

        } catch (Exception e) {
            YakRealms.error("Error during combat logout rollback for " + player.getName(), e);
        }
        return false;
    }

    /**
     * Validate inventory serialization integrity
     */
    public static boolean validateInventorySerialization(YakPlayer yakPlayer) {
        try {
            // Test inventory serialization
            String inventoryData = yakPlayer.getSerializedInventory();
            if (inventoryData != null) {
                ItemStack[] items = yakPlayer.deserializeItemStacks(inventoryData);
                if (items == null) {
                    YakRealms.warn("Inventory serialization validation failed for: " + yakPlayer.getUsername());
                    return false;
                }
            }

            // Test armor serialization
            String armorData = yakPlayer.getSerializedArmor();
            if (armorData != null) {
                ItemStack[] armor = yakPlayer.deserializeItemStacks(armorData);
                if (armor == null) {
                    YakRealms.warn("Armor serialization validation failed for: " + yakPlayer.getUsername());
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            YakRealms.error("Inventory serialization validation error for " + yakPlayer.getUsername(), e);
            return false;
        }
    }

    /**
     * Validate that combat logout processing is safe to proceed
     * Removed overly strict online check that caused race conditions during PlayerQuitEvent
     */
    public static boolean validateCombatLogoutProcessing(Player player, YakPlayer yakPlayer) {
        try {
            // Check player is not null
            if (player == null || yakPlayer == null) {
                YakRealms.warn("Null player or yakPlayer in combat logout validation");
                return false;
            }

            // REMOVED: The overly strict online check that was causing race conditions
            // During PlayerQuitEvent, player.isOnline() might still return true even though
            // the player is in the process of leaving, causing false validation failures

            // Check state is appropriate for processing
            YakPlayer.CombatLogoutState state = yakPlayer.getCombatLogoutState();
            if (state == YakPlayer.CombatLogoutState.PROCESSED || state == YakPlayer.CombatLogoutState.COMPLETED) {
                YakRealms.warn("Combat logout already processed for: " + player.getName() + " (state: " + state + ")");
                return false;
            }

            // Check alignment is valid
            String alignment = yakPlayer.getAlignment();
            if (alignment == null || alignment.trim().isEmpty()) {
                YakRealms.warn("Invalid alignment for combat logout player: " + player.getName());
                return false;
            }

            // Validate alignment is a known value
            if (!alignment.equals("LAWFUL") && !alignment.equals("NEUTRAL") && !alignment.equals("CHAOTIC")) {
                YakRealms.warn("Unknown alignment for combat logout player " + player.getName() + ": " + alignment);
                return false;
            }

            // Check for existing processing flag to prevent duplicate processing
            if (yakPlayer.hasTemporaryData("combat_logout_processing")) {
                long processingStart = (long) yakPlayer.getTemporaryData("combat_logout_processing");
                long timeSinceProcessing = System.currentTimeMillis() - processingStart;

                if (timeSinceProcessing < 30000) { // 30 seconds
                    YakRealms.warn("Combat logout already being processed for: " + player.getName() +
                            " (started " + timeSinceProcessing + "ms ago)");
                    return false;
                }
            }

            // All checks passed
            YakRealms.log("Combat logout processing validation passed for: " + player.getName());
            return true;

        } catch (Exception e) {
            YakRealms.error("Combat logout processing validation failed for " + player.getName(), e);
            return false;
        }
    }

    /**
     *  validation for rejoin death processing
     */
    public static boolean validateRejoinDeath(Player player, YakPlayer yakPlayer) {
        try {
            // Check basic requirements
            if (player == null || yakPlayer == null) {
                YakRealms.warn("Null player or yakPlayer in rejoin death validation");
                return false;
            }

            // Check player is actually online for rejoin death
            if (!player.isOnline()) {
                YakRealms.warn("Player not online for rejoin death: " + player.getName());
                return false;
            }

            // Check state is appropriate for death
            YakPlayer.CombatLogoutState state = yakPlayer.getCombatLogoutState();
            if (state != YakPlayer.CombatLogoutState.PROCESSED) {
                YakRealms.warn("Invalid state for rejoin death: " + player.getName() + " (state: " + state + ")");
                return false;
            }

            // Check player is not already dead
            if (player.isDead() || player.getHealth() <= 0) {
                YakRealms.warn("Player already dead for rejoin death: " + player.getName());
                return false;
            }

            return true;

        } catch (Exception e) {
            YakRealms.error("Rejoin death validation failed for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Clean up orphaned combat logout data with  safety checks
     */
    public static void cleanupOrphanedData(YakPlayer yakPlayer) {
        try {
            boolean hadOrphanedData = false;

            // Remove conflicting old system flags
            if (yakPlayer.hasTemporaryData("combat_logout_death_processed")) {
                yakPlayer.removeTemporaryData("combat_logout_death_processed");
                hadOrphanedData = true;
            }
            if (yakPlayer.hasTemporaryData("combat_logout_needs_death")) {
                yakPlayer.removeTemporaryData("combat_logout_needs_death");
                hadOrphanedData = true;
            }
            if (yakPlayer.hasTemporaryData("combat_logout_session")) {
                yakPlayer.removeTemporaryData("combat_logout_session");
                hadOrphanedData = true;
            }
            if (yakPlayer.hasTemporaryData("combat_logout_processing")) {
                yakPlayer.removeTemporaryData("combat_logout_processing");
                hadOrphanedData = true;
            }

            // If state is NONE, also clear respawn data that shouldn't exist
            if (yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.NONE) {
                if (yakPlayer.hasTemporaryData("respawn_pending")) {
                    yakPlayer.removeTemporaryData("respawn_pending");
                    hadOrphanedData = true;
                }
                if (yakPlayer.hasTemporaryData("respawn_items")) {
                    yakPlayer.removeTemporaryData("respawn_items");
                    hadOrphanedData = true;
                }
            }

            if (hadOrphanedData) {
                YakRealms.log("Cleaned up orphaned combat logout data for: " + yakPlayer.getUsername());
            }

        } catch (Exception e) {
            YakRealms.error("Error cleaning up orphaned data for " + yakPlayer.getUsername(), e);
        }
    }

    /**
     * Migrate old system flags to new system with  error handling
     */
    public static void migrateOldFlags(YakPlayer yakPlayer) {
        try {
            boolean hasOldProcessedFlag = yakPlayer.hasTemporaryData("combat_logout_death_processed");
            boolean hasOldNeedsDeathFlag = yakPlayer.hasTemporaryData("combat_logout_needs_death");
            boolean hasOldSessionFlag = yakPlayer.hasTemporaryData("combat_logout_session");
            boolean hasOldProcessingFlag = yakPlayer.hasTemporaryData("combat_logout_processing");

            // Only migrate if any old flags exist
            if (!hasOldProcessedFlag && !hasOldNeedsDeathFlag && !hasOldSessionFlag && !hasOldProcessingFlag) {
                return; // No migration needed
            }

            // Determine new state based on old flags (priority order)
            YakPlayer.CombatLogoutState newState = yakPlayer.getCombatLogoutState();

            if (hasOldNeedsDeathFlag) {
                newState = YakPlayer.CombatLogoutState.PROCESSED;
                YakRealms.log("Migrated old combat logout flags to PROCESSED state for: " + yakPlayer.getUsername());
            } else if (hasOldProcessedFlag) {
                newState = YakPlayer.CombatLogoutState.COMPLETED;
                YakRealms.log("Migrated old combat logout flags to COMPLETED state for: " + yakPlayer.getUsername());
            } else if (hasOldProcessingFlag) {
                newState = YakPlayer.CombatLogoutState.PROCESSING;
                YakRealms.log("Migrated old combat logout flags to PROCESSING state for: " + yakPlayer.getUsername());
            } else if (hasOldSessionFlag) {
                // Session flag alone doesn't indicate a specific state, clear it
                newState = YakPlayer.CombatLogoutState.NONE;
                YakRealms.log("Migrated old combat logout session flag to NONE state for: " + yakPlayer.getUsername());
            }

            // Set the new state
            yakPlayer.setCombatLogoutState(newState);

            // Clean up old flags after migration
            cleanupOrphanedData(yakPlayer);

        } catch (Exception e) {
            YakRealms.error("Error migrating old combat logout flags for " + yakPlayer.getUsername(), e);

            // Safe fallback - just clean up the old flags
            try {
                cleanupOrphanedData(yakPlayer);
            } catch (Exception cleanupError) {
                YakRealms.error("Error during fallback cleanup for " + yakPlayer.getUsername(), cleanupError);
            }
        }
    }

    /**
     * Comprehensive state validation for debugging purposes
     */
    public static String generateStateReport(YakPlayer yakPlayer) {
        try {
            StringBuilder report = new StringBuilder();
            report.append("=== Combat Logout State Report ===\n");
            report.append("Player: ").append(yakPlayer.getUsername()).append("\n");
            report.append("State: ").append(yakPlayer.getCombatLogoutState()).append("\n");
            report.append("Alignment: ").append(yakPlayer.getAlignment()).append("\n");

            // Check for temporary data
            report.append("Temporary Data:\n");
            String[] keysToCheck = {
                    "combat_logout_death_processed",
                    "combat_logout_needs_death",
                    "combat_logout_session",
                    "combat_logout_processing",
                    "respawn_pending",
                    "respawn_items",
                    "prevent_healing"
            };

            for (String key : keysToCheck) {
                if (yakPlayer.hasTemporaryData(key)) {
                    Object value = yakPlayer.getTemporaryData(key);
                    report.append("  ").append(key).append(": ").append(value).append("\n");
                }
            }

            // Check inventory serialization
            report.append("Inventory Serialized: ").append(yakPlayer.getSerializedInventory() != null).append("\n");
            report.append("Armor Serialized: ").append(yakPlayer.getSerializedArmor() != null).append("\n");

            report.append("================================");
            return report.toString();

        } catch (Exception e) {
            return "Error generating state report: " + e.getMessage();
        }
    }

    /**
     * Emergency reset of all combat logout related data
     */
    public static void emergencyReset(YakPlayer yakPlayer) {
        try {
            YakRealms.warn("Performing emergency reset of combat logout data for: " + yakPlayer.getUsername());

            // Reset state
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);

            // Clear all temporary data
            yakPlayer.removeTemporaryData("combat_logout_death_processed");
            yakPlayer.removeTemporaryData("combat_logout_needs_death");
            yakPlayer.removeTemporaryData("combat_logout_session");
            yakPlayer.removeTemporaryData("combat_logout_processing");
            yakPlayer.removeTemporaryData("respawn_pending");
            yakPlayer.removeTemporaryData("respawn_items");
            yakPlayer.removeTemporaryData("prevent_healing");

            YakRealms.log("Emergency reset completed for: " + yakPlayer.getUsername());

        } catch (Exception e) {
            YakRealms.error("Error during emergency reset for " + yakPlayer.getUsername(), e);
        }
    }
}