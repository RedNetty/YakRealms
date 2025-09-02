package com.rednetty.server.core.mechanics.player.utils;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;

/**
 *  validation and rollback system for combat logout processing
 * -  overly strict online validation that caused race conditions
 * - Improved error handling and recovery mechanisms
 * - Better state validation for different processing phases
 */
public class CombatLogoutValidator {


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