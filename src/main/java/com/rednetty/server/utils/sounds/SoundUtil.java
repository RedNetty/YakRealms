package com.rednetty.server.utils.sounds;

import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Unified Sound Utility System for YakRealms
 * Provides consistent sound feedback across all systems
 * Replaces scattered sound implementations with standardized approach
 */
public class SoundUtil {

    // ========================================
    // STANDARD FEEDBACK SOUNDS
    // ========================================

    /**
     * Play success sound - for positive actions and confirmations
     * @param player The player to play sound for
     */
    public static void playSuccess(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        } catch (Exception e) {
            // Fallback to simpler sound
            playFallbackSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }
    }

    /**
     * Play error sound - for failures and invalid actions
     * @param player The player to play sound for
     */
    public static void playError(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
        } catch (Exception e) {
            // Fallback to simpler sound
            playFallbackSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
        }
    }

    /**
     * Play warning sound - for cautions and important notices
     * @param player The player to play sound for
     */
    public static void playWarning(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
        } catch (Exception e) {
            // Fallback to simpler sound
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
        }
    }

    /**
     * Play notification sound - for general information and alerts
     * @param player The player to play sound for
     */
    public static void playNotification(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        } catch (Exception e) {
            // Fallback to simpler sound
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.3f, 1.0f);
        }
    }

    /**
     * Play confirmation sound - for user interactions and menu clicks
     * @param player The player to play sound for
     */
    public static void playConfirmation(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        } catch (Exception e) {
            // Fallback to simpler sound
            playFallbackSound(player, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.3f, 1.0f);
        }
    }

    // ========================================
    // PARTY SOUNDS
    // ========================================

    /**
     * Play party join sound - when someone joins a party
     * @param player The player to play sound for
     */
    public static void playPartyJoin(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        }
    }

    /**
     * Play party leave sound - when someone leaves a party
     * @param player The player to play sound for
     */
    public static void playPartyLeave(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.4f, 0.8f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.7f);
        }
    }

    /**
     * Play party invite sound - when receiving a party invitation
     * @param player The player to play sound for
     */
    public static void playPartyInvite(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.1f);
        }
    }

    /**
     * Play party chat sound - subtle confirmation for party messages
     * @param player The player to play sound for
     */
    public static void playPartyChat(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.5f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.2f, 1.3f);
        }
    }

    // ========================================
    // COMBAT SOUNDS
    // ========================================

    /**
     * Play combat start sound - when entering combat
     * @param player The player to play sound for
     */
    public static void playCombatStart(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.BLOCK_ANVIL_HIT, 0.5f, 1.2f);
        }
    }

    /**
     * Play combat end sound - when leaving combat
     * @param player The player to play sound for
     */
    public static void playCombatEnd(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
        }
    }

    /**
     * Play damage dealt sound - when dealing damage to enemies
     * @param player The player to play sound for
     */
    public static void playDamageDealt(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.7f, 1.6f);
        }
    }

    /**
     * Play friendly fire sound - when accidentally hitting party members
     * @param player The player to play sound for
     */
    public static void playFriendlyFire(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 0.6f);
        }
    }

    // ========================================
    // ECONOMY SOUNDS
    // ========================================

    /**
     * Play purchase success sound - when buying items successfully
     * @param player The player to play sound for
     */
    public static void playPurchaseSuccess(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            // Add coin sound effect
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }
    }

    /**
     * Play purchase failure sound - when purchase fails
     * @param player The player to play sound for
     */
    public static void playPurchaseFailure(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
        }
    }

    /**
     * Play item receive sound - when receiving items
     * @param player The player to play sound for
     */
    public static void playItemReceive(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.0f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
        }
    }

    // ========================================
    // CHAT SOUNDS
    // ========================================

    /**
     * Play private message received sound
     * @param player The player to play sound for
     */
    public static void playPrivateMessage(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.3f, 1.1f);
        }
    }

    /**
     * Play mute notification sound
     * @param player The player to play sound for
     */
    public static void playMuteNotification(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 0.7f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.5f);
        }
    }

    // ========================================
    // MENU SOUNDS
    // ========================================

    /**
     * Play menu open sound
     * @param player The player to play sound for
     */
    public static void playMenuOpen(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5f, 1.0f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.3f, 1.0f);
        }
    }

    /**
     * Play menu close sound
     * @param player The player to play sound for
     */
    public static void playMenuClose(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.5f, 0.8f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.UI_BUTTON_CLICK, 0.3f, 0.8f);
        }
    }

    /**
     * Play menu navigation sound - for cycling through options
     * @param player The player to play sound for
     */
    public static void playMenuNavigate(Player player) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.2f);
        } catch (Exception e) {
            playFallbackSound(player, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.2f, 1.0f);
        }
    }

    // ========================================
    // MOB SOUNDS ( from existing SoundUtil)
    // ========================================

    /**
     * Get appropriate hurt sound based on mob type
     * @param mob The mob to get hurt sound for
     * @return The appropriate hurt sound
     */
    public static Sound getMobHurtSound(LivingEntity mob) {
        EntityType type = mob.getType();

        switch (type) {
            case ZOMBIE:
                return Sound.ENTITY_ZOMBIE_HURT;
            case ZOMBIE_VILLAGER:
                return Sound.ENTITY_ZOMBIE_VILLAGER_HURT;
            case ZOMBIFIED_PIGLIN:
                return Sound.ENTITY_ZOMBIFIED_PIGLIN_HURT;
            case HUSK:
                return Sound.ENTITY_HUSK_HURT;
            case DROWNED:
                return Sound.ENTITY_DROWNED_HURT;

            case SKELETON:
                return Sound.ENTITY_SKELETON_HURT;
            case STRAY:
                return Sound.ENTITY_STRAY_HURT;
            case WITHER_SKELETON:
                return Sound.ENTITY_WITHER_SKELETON_HURT;

            case SPIDER:
            case CAVE_SPIDER:
                return Sound.ENTITY_SPIDER_HURT;

            case CREEPER:
                return Sound.ENTITY_CREEPER_HURT;

            case ENDERMAN:
                return Sound.ENTITY_ENDERMAN_HURT;

            case WITCH:
                return Sound.ENTITY_WITCH_HURT;

            case SLIME:
            case MAGMA_CUBE:
                return Sound.ENTITY_SLIME_HURT;

            case GHAST:
                return Sound.ENTITY_GHAST_HURT;

            case BLAZE:
                return Sound.ENTITY_BLAZE_HURT;

            case WOLF:
                return Sound.ENTITY_WOLF_HURT;

            case COW:
                return Sound.ENTITY_COW_HURT;

            case PIG:
                return Sound.ENTITY_PIG_HURT;

            case SHEEP:
                return Sound.ENTITY_SHEEP_HURT;

            case CHICKEN:
                return Sound.ENTITY_CHICKEN_HURT;

            case HORSE:
                return Sound.ENTITY_HORSE_HURT;

            case VILLAGER:
                return Sound.ENTITY_VILLAGER_HURT;

            case IRON_GOLEM:
                return Sound.ENTITY_IRON_GOLEM_HURT;

            case WITHER:
                return Sound.ENTITY_WITHER_HURT;

            case ENDER_DRAGON:
                return Sound.ENTITY_ENDER_DRAGON_HURT;

            case GUARDIAN:
            case ELDER_GUARDIAN:
                return Sound.ENTITY_GUARDIAN_HURT;

            case RABBIT:
                return Sound.ENTITY_RABBIT_HURT;

            case POLAR_BEAR:
                return Sound.ENTITY_POLAR_BEAR_HURT;

            case LLAMA:
                return Sound.ENTITY_LLAMA_HURT;

            case PARROT:
                return Sound.ENTITY_PARROT_HURT;

            case VEX:
                return Sound.ENTITY_VEX_HURT;

            case VINDICATOR:
                return Sound.ENTITY_VINDICATOR_HURT;

            case EVOKER:
                return Sound.ENTITY_EVOKER_HURT;

            case BAT:
                return Sound.ENTITY_BAT_HURT;

            case OCELOT:
                return Sound.ENTITY_OCELOT_HURT;

            case CAT:
                return Sound.ENTITY_CAT_HURT;

            case SQUID:
                return Sound.ENTITY_SQUID_HURT;

            case SILVERFISH:
                return Sound.ENTITY_SILVERFISH_HURT;

            case ENDERMITE:
                return Sound.ENTITY_ENDERMITE_HURT;

            case SHULKER:
                return Sound.ENTITY_SHULKER_HURT;

            // Default fallback sounds for any missing or custom mobs
            default:
                // Try to determine a reasonable fallback based on mob characteristics
                if (type.name().contains("ZOMBIE")) {
                    return Sound.ENTITY_ZOMBIE_HURT;
                } else if (type.name().contains("SKELETON")) {
                    return Sound.ENTITY_SKELETON_HURT;
                } else if (type.name().contains("SPIDER")) {
                    return Sound.ENTITY_SPIDER_HURT;
                } else {
                    // Generic hurt sound for unknown mobs
                    return Sound.ENTITY_GENERIC_HURT;
                }
        }
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Check if a player can receive sound effects
     * @param player The player to check
     * @return true if player can receive sounds
     */
    public static boolean canPlaySound(Player player) {
        return player != null && player.isOnline();
    }

    /**
     * Play a fallback sound if the primary sound fails
     * @param player The player to play sound for
     * @param sound The fallback sound to play
     * @param volume The sound volume
     * @param pitch The sound pitch
     */
    private static void playFallbackSound(Player player, Sound sound, float volume, float pitch) {
        try {
            if (canPlaySound(player)) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (Exception e) {
            // If even fallback fails, just ignore - no sounds is better than crashing
        }
    }

    /**
     * Play custom sound with specified parameters
     * @param player The player to play sound for
     * @param sound The sound to play
     * @param volume The sound volume (0.0 to 1.0)
     * @param pitch The sound pitch (0.0 to 2.0)
     */
    public static void playCustomSound(Player player, Sound sound, float volume, float pitch) {
        if (!canPlaySound(player)) return;

        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            // If custom sound fails, try with default parameters
            playFallbackSound(player, sound, 0.5f, 1.0f);
        }
    }

    /**
     * Play sound for multiple players
     * @param players Array of players to play sound for
     * @param sound The sound to play
     * @param volume The sound volume
     * @param pitch The sound pitch
     */
    public static void playSoundForPlayers(Player[] players, Sound sound, float volume, float pitch) {
        if (players == null) return;

        for (Player player : players) {
            playCustomSound(player, sound, volume, pitch);
        }
    }

    /**
     * Play sound based on action result
     * @param player The player to play sound for
     * @param success Whether the action was successful
     */
    public static void playResultSound(Player player, boolean success) {
        if (success) {
            playSuccess(player);
        } else {
            playError(player);
        }
    }
}