package com.rednetty.server.mechanics.player;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.moderation.Rank;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  core player data model with improved thread safety,
 * validation, performance optimizations, and comprehensive functionality.
 * Physical gem economy - no virtual gem balance, only bank balance and physical items.
 *  with permanent respawn items storage for Spigot 1.20.4
 */
public class YakPlayer {
    private static final Logger logger = Logger.getLogger(YakPlayer.class.getName());

    // Configuration constants
    private static final int MAX_BANK_PAGES = 10;
    private static final int MAX_BUDDIES = 50;
    private static final int MAX_AUTHORIZED_USERS = 10;
    private static final int MIN_GEMS = 0;
    private static final int MAX_GEMS = Integer.MAX_VALUE;
    private static final int MAX_LEVEL = 200;
    private static final int MIN_LEVEL = 1;
    private static final int MAX_USERNAME_LENGTH = 16;

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Basic identification
    @Expose @SerializedName("uuid")
    private final UUID uuid;

    @Expose @SerializedName("username")
    private volatile String username;

    @Expose @SerializedName("last_login")
    private volatile long lastLogin;

    @Expose @SerializedName("last_logout")
    private volatile long lastLogout;

    @Expose @SerializedName("first_join")
    private volatile long firstJoin;

    @Expose @SerializedName("ip_address")
    private volatile String ipAddress;

    @Expose @SerializedName("total_playtime")
    private volatile long totalPlaytime = 0;

    // Player progression and stats
    @Expose @SerializedName("level")
    private volatile int level = 1;

    @Expose @SerializedName("exp")
    private volatile int exp = 0;

    @Expose @SerializedName("monster_kills")
    private volatile int monsterKills = 0;

    @Expose @SerializedName("player_kills")
    private volatile int playerKills = 0;

    @Expose @SerializedName("deaths")
    private volatile int deaths = 0;

    @Expose @SerializedName("ore_mined")
    private volatile int oreMined = 0;

    @Expose @SerializedName("fish_caught")
    private volatile int fishCaught = 0;

    @Expose @SerializedName("blocks_broken")
    private volatile int blocksBroken = 0;

    @Expose @SerializedName("distance_traveled")
    private volatile double distanceTraveled = 0.0;

    // Economy data - ONLY bank balance, no virtual player balance
    @Expose @SerializedName("bank_gems")
    private volatile int bankGems = 0;

    @Expose @SerializedName("elite_shards")
    private volatile int eliteShards = 0;

    // Bank system with improved structure
    @Expose @SerializedName("bank_pages")
    private volatile int bankPages = 1;

    @Expose @SerializedName("bank_inventory")
    private final Map<Integer, String> serializedBankItems = new ConcurrentHashMap<>();

    @Expose @SerializedName("bank_authorized_users")
    private final Set<String> bankAuthorizedUsers = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("bank_access_log")
    private final List<String> bankAccessLog = new ArrayList<>();

    // Alignment data
    @Expose @SerializedName("alignment")
    private volatile String alignment = "LAWFUL";

    @Expose @SerializedName("chaotic_time")
    private volatile long chaoticTime = 0;

    @Expose @SerializedName("neutral_time")
    private volatile long neutralTime = 0;

    @Expose @SerializedName("alignment_changes")
    private volatile int alignmentChanges = 0;

    // Moderation data
    @Expose @SerializedName("rank")
    private volatile String rank = "DEFAULT";

    @Expose @SerializedName("banned")
    private volatile boolean banned = false;

    @Expose @SerializedName("ban_reason")
    private volatile String banReason = "";

    @Expose @SerializedName("ban_expiry")
    private volatile long banExpiry = 0;

    @Expose @SerializedName("muted")
    private volatile int muteTime = 0;

    @Expose @SerializedName("warnings")
    private volatile int warnings = 0;

    @Expose @SerializedName("last_warning")
    private volatile long lastWarning = 0;

    // Chat data
    @Expose @SerializedName("chat_tag")
    private volatile String chatTag = "DEFAULT";

    @Expose @SerializedName("unlocked_chat_tags")
    private final Set<String> unlockedChatTags = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("chat_color")
    private volatile String chatColor = "WHITE";

    // Mount and Guild data
    @Expose @SerializedName("horse_tier")
    private volatile int horseTier = 0;

    @Expose @SerializedName("horse_name")
    private volatile String horseName = "";

    @Expose @SerializedName("guild_name")
    private volatile String guildName = "";

    @Expose @SerializedName("guild_rank")
    private volatile String guildRank = "";

    @Expose @SerializedName("guild_contribution")
    private volatile int guildContribution = 0;

    // Player preferences with thread safety
    @Expose @SerializedName("toggle_settings")
    private final Set<String> toggleSettings = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("notification_settings")
    private final Map<String, Boolean> notificationSettings = new ConcurrentHashMap<>();

    // Quest system
    @Expose @SerializedName("current_quest")
    private volatile String currentQuest = "";

    @Expose @SerializedName("quest_progress")
    private volatile int questProgress = 0;

    @Expose @SerializedName("completed_quests")
    private final Set<String> completedQuests = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("quest_points")
    private volatile int questPoints = 0;

    @Expose @SerializedName("daily_quests_completed")
    private volatile int dailyQuestsCompleted = 0;

    @Expose @SerializedName("last_daily_quest_reset")
    private volatile long lastDailyQuestReset = 0;

    // Profession data
    @Expose @SerializedName("pickaxe_level")
    private volatile int pickaxeLevel = 0;

    @Expose @SerializedName("fishing_level")
    private volatile int fishingLevel = 0;

    @Expose @SerializedName("mining_xp")
    private volatile int miningXp = 0;

    @Expose @SerializedName("fishing_xp")
    private volatile int fishingXp = 0;

    @Expose @SerializedName("farming_level")
    private volatile int farmingLevel = 0;

    @Expose @SerializedName("farming_xp")
    private volatile int farmingXp = 0;

    @Expose @SerializedName("woodcutting_level")
    private volatile int woodcuttingLevel = 0;

    @Expose @SerializedName("woodcutting_xp")
    private volatile int woodcuttingXp = 0;

    // PvP stats with atomic updates
    @Expose @SerializedName("t1_kills")
    private volatile int t1Kills = 0;

    @Expose @SerializedName("t2_kills")
    private volatile int t2Kills = 0;

    @Expose @SerializedName("t3_kills")
    private volatile int t3Kills = 0;

    @Expose @SerializedName("t4_kills")
    private volatile int t4Kills = 0;

    @Expose @SerializedName("t5_kills")
    private volatile int t5Kills = 0;

    @Expose @SerializedName("t6_kills")
    private volatile int t6Kills = 0;

    @Expose @SerializedName("kill_streak")
    private volatile int killStreak = 0;

    @Expose @SerializedName("best_kill_streak")
    private volatile int bestKillStreak = 0;

    @Expose @SerializedName("pvp_rating")
    private volatile int pvpRating = 1000;

    // World Boss tracking
    @Expose @SerializedName("world_boss_damage")
    private final Map<String, Integer> worldBossDamage = new ConcurrentHashMap<>();

    @Expose @SerializedName("world_boss_kills")
    private final Map<String, Integer> worldBossKills = new ConcurrentHashMap<>();

    // Social settings
    @Expose @SerializedName("trade_disabled")
    private volatile boolean tradeDisabled = false;

    @Expose @SerializedName("buddies")
    private final Set<String> buddies = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("blocked_players")
    private final Set<String> blockedPlayers = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("energy_disabled")
    private volatile boolean energyDisabled = false;

    // Location and state data
    @Expose @SerializedName("world")
    private volatile String world;

    @Expose @SerializedName("location_x")
    private volatile double locationX;

    @Expose @SerializedName("location_y")
    private volatile double locationY;

    @Expose @SerializedName("location_z")
    private volatile double locationZ;

    @Expose @SerializedName("location_yaw")
    private volatile float locationYaw;

    @Expose @SerializedName("location_pitch")
    private volatile float locationPitch;

    @Expose @SerializedName("previous_location")
    private volatile String previousLocation;

    // Serialized inventory data
    @Expose @SerializedName("inventory_contents")
    private volatile String serializedInventory;

    @Expose @SerializedName("armor_contents")
    private volatile String serializedArmor;

    @Expose @SerializedName("ender_chest_contents")
    private volatile String serializedEnderChest;

    @Expose @SerializedName("offhand_item")
    private volatile String serializedOffhand;

    // PERMANENT respawn items storage - no expiration
    @Expose
    @SerializedName("respawn_items")
    private volatile String serializedRespawnItems;

    @Expose
    @SerializedName("respawn_item_count")
    private volatile int respawnItemCount = 0;

    @Expose
    @SerializedName("death_timestamp")
    private volatile long deathTimestamp = 0;

    // Player stats
    @Expose @SerializedName("health")
    private volatile double health = 20.0;

    @Expose @SerializedName("max_health")
    private volatile double maxHealth = 20.0;

    @Expose @SerializedName("food_level")
    private volatile int foodLevel = 20;

    @Expose @SerializedName("saturation")
    private volatile float saturation = 5.0f;

    @Expose @SerializedName("xp_level")
    private volatile int xpLevel = 0;

    @Expose @SerializedName("xp_progress")
    private volatile float xpProgress = 0.0f;

    @Expose @SerializedName("total_experience")
    private volatile int totalExperience = 0;

    @Expose @SerializedName("bed_spawn_location")
    private volatile String bedSpawnLocation;

    @Expose @SerializedName("gamemode")
    private volatile String gameMode = "SURVIVAL";

    @Expose @SerializedName("active_potion_effects")
    private final List<String> activePotionEffects = new ArrayList<>();

    // Achievement and reward tracking
    @Expose @SerializedName("achievements")
    private final Set<String> achievements = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("achievement_points")
    private volatile int achievementPoints = 0;

    @Expose @SerializedName("daily_rewards_claimed")
    private final Set<String> dailyRewardsClaimed = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("last_daily_reward")
    private volatile long lastDailyReward = 0;

    // Event participation tracking
    @Expose @SerializedName("events_participated")
    private final Map<String, Integer> eventsParticipated = new ConcurrentHashMap<>();

    @Expose @SerializedName("event_wins")
    private final Map<String, Integer> eventWins = new ConcurrentHashMap<>();

    // Combat statistics
    @Expose @SerializedName("damage_dealt")
    private volatile long damageDealt = 0;

    @Expose @SerializedName("damage_taken")
    private volatile long damageTaken = 0;

    @Expose @SerializedName("damage_blocked")
    private volatile long damageBlocked = 0;

    @Expose @SerializedName("damage_dodged")
    private volatile long damageDodged = 0;

    // Combat logout state management
    @Expose
    @SerializedName("combat_logout_state")
    private volatile CombatLogoutState combatLogoutState = CombatLogoutState.NONE;

    // Non-serialized transient fields
    private transient volatile Player bukkitPlayer;
    private transient volatile boolean inCombat = false;
    private transient volatile long lastCombatTime = 0;
    private transient final Map<String, Object> temporaryData = new ConcurrentHashMap<>();
    private transient volatile ItemStack[] respawnItems;
    private transient volatile boolean dirty = false; // Optimization flag for saves
    private transient volatile long sessionStartTime;

    //  display name caching
    private transient volatile String cachedDisplayName;
    private transient volatile long displayNameCacheTime = 0;
    private static final long DISPLAY_NAME_CACHE_DURATION = 30000; // 30 seconds

    /**
     * Get the current combat logout state
     */
    public CombatLogoutState getCombatLogoutState() {
        return combatLogoutState;
    }

    /**
     * Constructor for creating a new YakPlayer
     */
    public YakPlayer(Player player) {
        this.uuid = player.getUniqueId();
        this.username = validateUsername(player.getName());
        this.ipAddress = player.getAddress().getAddress().getHostAddress();
        this.firstJoin = Instant.now().getEpochSecond();
        this.lastLogin = firstJoin;
        this.sessionStartTime = System.currentTimeMillis();
        this.bukkitPlayer = player;

        // Initialize location safely
        Location loc = player.getLocation();
        if (loc != null && loc.getWorld() != null) {
            updateLocation(loc);
        }

        // Initialize inventory and stats
        updateInventory(player);
        updateStats(player);

        // Initialize energy system
        this.temporaryData.put("energy", 100);

        // Initialize default settings
        initializeDefaultSettings();

        logger.info("Created new YakPlayer for: " + player.getName());
    }

    /**
     * Constructor for loading YakPlayer from storage
     */
    public YakPlayer(UUID uuid) {
        this.uuid = uuid;
        this.bukkitPlayer = Bukkit.getPlayer(uuid);
        this.temporaryData.put("energy", 100);
        this.sessionStartTime = System.currentTimeMillis();
        initializeDefaultSettings();
    }

    /**
     * Initialize default settings for new players
     */
    private void initializeDefaultSettings() {
        // Default notification settings
        notificationSettings.put("buddy_join", true);
        notificationSettings.put("buddy_leave", true);
        notificationSettings.put("guild_messages", true);
        notificationSettings.put("trade_requests", true);
        notificationSettings.put("party_invites", true);

        // Default toggle settings
        toggleSettings.add("Player Messages");
        toggleSettings.add("Drop Protection");
        toggleSettings.add("Sound Effects");
    }

    /**
     * Validate and clean username
     */
    private String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        String cleaned = username.trim();
        if (cleaned.length() > MAX_USERNAME_LENGTH) {
            logger.warning("Username exceeds maximum length: " + cleaned);
            cleaned = cleaned.substring(0, MAX_USERNAME_LENGTH);
        }

        return cleaned;
    }

    // Combat logout state management methods

    public void setCombatLogoutState(CombatLogoutState state) {
        lock.writeLock().lock();
        try {
            if (state == null) state = CombatLogoutState.NONE;

            // Log state transitions for debugging
            if (this.combatLogoutState != state) {
                logger.info("Combat logout state change for " + username +
                        ": " + this.combatLogoutState + " -> " + state);
            }

            //  Allow COMPLETED -> NONE and PROCESSING -> COMPLETED transitions
            // These are valid for cleanup and death processing
            this.combatLogoutState = state;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if player is in any combat logout state
     */
    public boolean isInCombatLogoutState() {
        lock.readLock().lock();
        try {
            return combatLogoutState != CombatLogoutState.NONE;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if player needs combat logout death
     */
    public boolean needsCombatLogoutDeath() {
        lock.readLock().lock();
        try {
            return combatLogoutState == CombatLogoutState.PROCESSED;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reset combat logout state to NONE
     */
    public void resetCombatLogoutState() {
        setCombatLogoutState(CombatLogoutState.NONE);
    }

    /**
     * PERMANENT respawn items management - stored in database forever until processed
     * Improved validation and error handling
     */
    public boolean setRespawnItems(List<ItemStack> items) {
        lock.writeLock().lock();
        try {
            // Clear if null or empty
            if (items == null || items.isEmpty()) {
                clearRespawnItems();
                return true;
            }

            // Filter out null and air items, create defensive copies
            List<ItemStack> validItems = new ArrayList<>();
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                    // Create a defensive copy to prevent external modifications
                    ItemStack copy = item.clone();
                    validItems.add(copy);
                }
            }

            if (validItems.isEmpty()) {
                clearRespawnItems();
                return true;
            }

            try {
                // Serialize items for permanent database storage
                String serialized = ItemSerializer.serializeItemStacks(validItems.toArray(new ItemStack[0]));
                if (serialized == null || serialized.isEmpty()) {
                    logger.severe("Failed to serialize respawn items for " + username);
                    return false;
                }

                // Validate serialization by attempting deserialization
                ItemStack[] testDeserialize = ItemSerializer.deserializeItemStacks(serialized);
                if (testDeserialize == null || testDeserialize.length != validItems.size()) {
                    logger.severe("Serialization validation failed for respawn items: " + username);
                    return false;
                }

                this.serializedRespawnItems = serialized;
                this.respawnItemCount = validItems.size();
                this.deathTimestamp = System.currentTimeMillis();
                markDirty();

                logger.info("Successfully stored " + validItems.size() + " respawn items permanently for " + username);
                return true;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to serialize respawn items for " + username, e);
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // PERMANENT respawn items management - stored in database forever until processed

    /**
     * Get respawn items (permanent storage)
     */
    public List<ItemStack> getRespawnItems() {
        lock.readLock().lock();
        try {
            if (serializedRespawnItems == null || serializedRespawnItems.isEmpty()) {
                return new ArrayList<>();
            }

            try {
                ItemStack[] items = ItemSerializer.deserializeItemStacks(serializedRespawnItems);
                if (items == null) {
                    logger.warning("Failed to deserialize respawn items for " + username);
                    return new ArrayList<>();
                }

                List<ItemStack> itemList = new ArrayList<>();
                for (ItemStack item : items) {
                    if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                        // Create defensive copy to prevent external modifications
                        ItemStack copy = item.clone();
                        itemList.add(copy);
                    }
                }

                // Validate count matches
                if (itemList.size() != respawnItemCount) {
                    logger.warning("Respawn items count mismatch for " + username +
                            ": expected " + respawnItemCount + ", got " + itemList.size());
                }

                logger.fine("Retrieved " + itemList.size() + " respawn items for " + username);
                return itemList;

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error deserializing respawn items for " + username, e);
                return new ArrayList<>();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if player has pending respawn items (permanent)
     */
    public boolean hasRespawnItems() {
        lock.readLock().lock();
        try {
            return serializedRespawnItems != null &&
                    !serializedRespawnItems.isEmpty() &&
                    respawnItemCount > 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear respawn items from permanent storage
     */
    public void clearRespawnItems() {
        lock.writeLock().lock();
        try {
            this.serializedRespawnItems = null;
            this.respawnItemCount = 0;
            this.deathTimestamp = 0;
            // Also clear transient respawn items if any
            this.respawnItems = null;
            markDirty();

            logger.fine("Cleared all respawn items for " + username);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the number of pending respawn items
     */
    public int getRespawnItemCount() {
        lock.readLock().lock();
        try {
            return respawnItemCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the timestamp when death/respawn items were stored
     */
    public long getDeathTimestamp() {
        lock.readLock().lock();
        try {
            return deathTimestamp;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Set death timestamp
     */
    public void setDeathTimestamp(long timestamp) {
        lock.writeLock().lock();
        try {
            this.deathTimestamp = timestamp;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get formatted time since death (for admin commands)
     */
    public String getTimeSinceDeath() {
        lock.readLock().lock();
        try {
            if (deathTimestamp <= 0) {
                return "Never";
            }

            long timeDiff = System.currentTimeMillis() - deathTimestamp;
            long seconds = timeDiff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return days + " days, " + (hours % 24) + " hours ago";
            } else if (hours > 0) {
                return hours + " hours, " + (minutes % 60) + " minutes ago";
            } else if (minutes > 0) {
                return minutes + " minutes, " + (seconds % 60) + " seconds ago";
            } else {
                return seconds + " seconds ago";
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Validate respawn items integrity
     */
    public boolean validateRespawnItems() {
        lock.readLock().lock();
        try {
            if (!hasRespawnItems()) {
                return true; // No items to validate
            }

            try {
                // Attempt to deserialize
                List<ItemStack> items = getRespawnItems();

                // Check if we got any items
                if (items.isEmpty() && respawnItemCount > 0) {
                    logger.warning("Failed to deserialize any respawn items for " + username +
                            " despite count of " + respawnItemCount);
                    return false;
                }

                // Validate each item
                int validCount = 0;
                for (ItemStack item : items) {
                    if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                        validCount++;
                    }
                }

                boolean valid = validCount > 0;

                if (!valid) {
                    logger.warning("No valid respawn items found for " + username);
                }

                return valid;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to validate respawn items for " + username, e);
                return false;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Create backup of current respawn items
     */
    public String backupRespawnItems() {
        lock.readLock().lock();
        try {
            if (!hasRespawnItems()) {
                return null;
            }

            // Return a defensive copy of the serialized data for backup
            return serializedRespawnItems;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Restore respawn items from backup
     */
    public boolean restoreRespawnItemsFromBackup(String backup) {
        if (backup == null || backup.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            // Validate the backup data first
            try {
                ItemStack[] items = ItemSerializer.deserializeItemStacks(backup);
                if (items == null || items.length == 0) {
                    logger.warning("Invalid backup data for respawn items restoration: " + username);
                    return false;
                }

                // Count valid items
                int validCount = 0;
                for (ItemStack item : items) {
                    if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                        validCount++;
                    }
                }

                if (validCount == 0) {
                    logger.warning("No valid items in backup data for: " + username);
                    return false;
                }

                this.serializedRespawnItems = backup;
                this.respawnItemCount = validCount;
                this.deathTimestamp = System.currentTimeMillis(); // Update timestamp
                markDirty();

                logger.info("Successfully restored " + validCount + " respawn items from backup for " + username);
                return true;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to restore respawn items from backup for " + username, e);
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get detailed respawn items info for admin commands
     */
    public String getRespawnItemsInfo() {
        lock.readLock().lock();
        try {
            if (!hasRespawnItems()) {
                return "No pending respawn items";
            }

            StringBuilder info = new StringBuilder();
            info.append("=== Respawn Items for ").append(username).append(" ===\n");
            info.append("Count: ").append(respawnItemCount).append("\n");
            info.append("Stored: ").append(getTimeSinceDeath()).append("\n");
            info.append("Combat Logout State: ").append(combatLogoutState).append("\n");
            info.append("Data Size: ").append(serializedRespawnItems != null ?
                    serializedRespawnItems.length() : 0).append(" bytes\n");

            try {
                List<ItemStack> items = getRespawnItems();
                info.append("\nItems (").append(items.size()).append(" total):\n");

                int index = 1;
                for (ItemStack item : items) {
                    if (item != null) {
                        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                                item.getItemMeta().getDisplayName() :
                                item.getType().name();
                        info.append("  ").append(index++).append(". ")
                                .append(ChatColor.stripColor(itemName))
                                .append(" x").append(item.getAmount());

                        // Add enchantment info if present
                        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                            info.append(" [Enchanted]");
                        }

                        info.append("\n");
                    }
                }
            } catch (Exception e) {
                info.append("\nError loading item details: ").append(e.getMessage()).append("\n");
            }

            return info.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Update the disconnect() method to handle combat logout state
     */
    public void disconnect() {
        lock.writeLock().lock();
        try {
            this.lastLogout = Instant.now().getEpochSecond();

            // Update total playtime
            if (sessionStartTime > 0) {
                long sessionDuration = System.currentTimeMillis() - sessionStartTime;
                this.totalPlaytime += sessionDuration / 1000; // Convert to seconds
            }

            // Don't reset combat logout state on disconnect - it needs to persist
            // across logout/login for proper processing

            this.bukkitPlayer = null;
            markDirty();
            logger.fine("Disconnected player: " + username);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Connection and lifecycle management

    /**
     * Handle player connection with combat logout state migration
     * Better state handling on join
     */
    public void connect(Player player) {
        lock.writeLock().lock();
        try {
            this.bukkitPlayer = player;
            this.username = validateUsername(player.getName());
            this.lastLogin = Instant.now().getEpochSecond();
            this.sessionStartTime = System.currentTimeMillis();

            if (player.getAddress() != null) {
                this.ipAddress = player.getAddress().getAddress().getHostAddress();
            }

            // Migrate old combat logout flags if they exist
            migrateOldCombatLogoutFlags();

            //  Clear any temporary death prevention on join
            removeTemporaryData("prevent_healing");

            //  Ensure player starts with proper health/food values
            if (health <= 0) {
                health = 20.0; // Default health if invalid
            }
            if (foodLevel <= 0) {
                foodLevel = 20; // Default food level
            }

            markDirty();

            //  energy level display
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                player.setExp(1.0f);
                player.setLevel(100);
            });

            logger.fine("Connected player: " + player.getName() +
                    " (Combat State: " + combatLogoutState +
                    ", Respawn Items: " + hasRespawnItems() + ")");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Migrate old combat logout flags to new state system
     */
    private void migrateOldCombatLogoutFlags() {
        try {
            boolean hasOldProcessedFlag = hasTemporaryData("combat_logout_death_processed");
            boolean hasOldNeedsDeathFlag = hasTemporaryData("combat_logout_needs_death");
            boolean hasOldSessionFlag = hasTemporaryData("combat_logout_session");
            boolean hasOldProcessingFlag = hasTemporaryData("combat_logout_processing");

            // Count how many old flags exist
            int oldFlagCount = 0;
            if (hasOldProcessedFlag) oldFlagCount++;
            if (hasOldNeedsDeathFlag) oldFlagCount++;
            if (hasOldSessionFlag) oldFlagCount++;
            if (hasOldProcessingFlag) oldFlagCount++;

            // Only migrate if we have old flags and current state is NONE
            if (oldFlagCount > 0 && combatLogoutState == CombatLogoutState.NONE) {
                // Priority order for migration
                if (hasOldNeedsDeathFlag) {
                    combatLogoutState = CombatLogoutState.PROCESSED;
                    logger.info("Migrated old combat logout flags to PROCESSED state for: " + username);
                } else if (hasOldProcessedFlag) {
                    combatLogoutState = CombatLogoutState.COMPLETED;
                    logger.info("Migrated old combat logout flags to COMPLETED state for: " + username);
                } else if (hasOldProcessingFlag) {
                    combatLogoutState = CombatLogoutState.PROCESSING;
                    logger.info("Migrated old combat logout flags to PROCESSING state for: " + username);
                } else if (hasOldSessionFlag) {
                    // Session flag alone means they were tagged but didn't logout
                    combatLogoutState = CombatLogoutState.NONE;
                    logger.info("Migrated old combat logout session flag to NONE state for: " + username);
                }

                markDirty();
            }

            // Clean up old flags regardless
            if (oldFlagCount > 0) {
                removeTemporaryData("combat_logout_death_processed");
                removeTemporaryData("combat_logout_needs_death");
                removeTemporaryData("combat_logout_session");
                removeTemporaryData("combat_logout_processing");

                logger.info("Cleaned up " + oldFlagCount + " old combat logout flags for: " + username);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error migrating old combat logout flags for " + username, e);
        }
    }

    public void applyInventory(Player player) {
        if (player == null || serializedInventory == null) return;

        lock.readLock().lock();
        try {
            ItemStack[] contents = ItemSerializer.deserializeItemStacks(serializedInventory);
            if (contents != null && contents.length == player.getInventory().getSize()) {
                player.getInventory().setContents(contents);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying inventory for player " + player.getName(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Thread-safe accessors
    public boolean isOnline() {
        lock.readLock().lock();
        try {
            return bukkitPlayer != null && bukkitPlayer.isOnline();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Player getBukkitPlayer() {
        lock.readLock().lock();
        try {
            return bukkitPlayer;
        } finally {
            lock.readLock().unlock();
        }
    }

    //  bank management with authorization and logging
    public boolean addBankAuthorizedUser(UUID userUuid) {
        if (userUuid == null) return false;

        lock.writeLock().lock();
        try {
            if (bankAuthorizedUsers.size() >= MAX_AUTHORIZED_USERS) {
                sendMessageIfOnline(ChatColor.RED + "Maximum authorized users reached (" + MAX_AUTHORIZED_USERS + ").");
                return false;
            }

            String userUuidStr = userUuid.toString();
            boolean added = bankAuthorizedUsers.add(userUuidStr);

            if (added) {
                // Log the authorization
                String logEntry = Instant.now().getEpochSecond() + ":" + username + ":AUTHORIZED:" + userUuidStr;
                bankAccessLog.add(logEntry);

                // Keep log size manageable
                if (bankAccessLog.size() > 100) {
                    bankAccessLog.remove(0);
                }

                markDirty();
                sendMessageIfOnline(ChatColor.GREEN + "Added bank access for user.");
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeBankAuthorizedUser(UUID userUuid) {
        if (userUuid == null) return false;

        lock.writeLock().lock();
        try {
            String userUuidStr = userUuid.toString();
            boolean removed = bankAuthorizedUsers.remove(userUuidStr);

            if (removed) {
                // Log the removal
                String logEntry = Instant.now().getEpochSecond() + ":" + username + ":REMOVED:" + userUuidStr;
                bankAccessLog.add(logEntry);

                markDirty();
                sendMessageIfOnline(ChatColor.YELLOW + "Removed bank access for user.");
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isBankAuthorized(UUID userUuid) {
        if (userUuid == null) return false;
        if (userUuid.equals(this.uuid)) return true; // Owner is always authorized

        lock.readLock().lock();
        try {
            return bankAuthorizedUsers.contains(userUuid.toString());
        } finally {
            lock.readLock().unlock();
        }
    }

    //  buddy management with limits and validation
    public boolean addBuddy(String buddyName) {
        if (buddyName == null || buddyName.trim().isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (buddies.size() >= MAX_BUDDIES) {
                sendMessageIfOnline(ChatColor.RED + "You have reached the maximum number of buddies (" + MAX_BUDDIES + ").");
                return false;
            }

            String normalizedName = buddyName.toLowerCase().trim();

            // Prevent adding self
            if (normalizedName.equals(username.toLowerCase())) {
                sendMessageIfOnline(ChatColor.RED + "You cannot add yourself as a buddy!");
                return false;
            }

            // Prevent adding blocked players
            if (blockedPlayers.contains(normalizedName)) {
                sendMessageIfOnline(ChatColor.RED + "You cannot add a blocked player as a buddy!");
                return false;
            }

            if (buddies.add(normalizedName)) {
                markDirty();
                sendMessageIfOnline(ChatColor.GREEN + "Successfully added " + buddyName + " as a buddy!");

                // Play sound if online
                Player player = getBukkitPlayer();
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                }

                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeBuddy(String buddyName) {
        if (buddyName == null) return false;

        lock.writeLock().lock();
        try {
            boolean removed = buddies.remove(buddyName.toLowerCase().trim());
            if (removed) {
                markDirty();
                sendMessageIfOnline(ChatColor.YELLOW + "Removed " + buddyName + " from your buddy list.");

                // Play sound if online
                Player player = getBukkitPlayer();
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isBuddy(String buddyName) {
        if (buddyName == null) return false;

        lock.readLock().lock();
        try {
            return buddies.contains(buddyName.toLowerCase().trim());
        } finally {
            lock.readLock().unlock();
        }
    }

    //  player blocking system
    public boolean blockPlayer(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            String normalizedName = playerName.toLowerCase().trim();

            // Cannot block self
            if (normalizedName.equals(username.toLowerCase())) {
                sendMessageIfOnline(ChatColor.RED + "You cannot block yourself!");
                return false;
            }

            // Remove from buddies if they are a buddy
            if (buddies.contains(normalizedName)) {
                buddies.remove(normalizedName);
                sendMessageIfOnline(ChatColor.YELLOW + "Removed " + playerName + " from your buddy list.");
            }

            if (blockedPlayers.add(normalizedName)) {
                markDirty();
                sendMessageIfOnline(ChatColor.RED + "Blocked " + playerName + ".");
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean unblockPlayer(String playerName) {
        if (playerName == null) return false;

        lock.writeLock().lock();
        try {
            boolean removed = blockedPlayers.remove(playerName.toLowerCase().trim());
            if (removed) {
                markDirty();
                sendMessageIfOnline(ChatColor.GREEN + "Unblocked " + playerName + ".");
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isBlocked(String playerName) {
        if (playerName == null) return false;

        lock.readLock().lock();
        try {
            return blockedPlayers.contains(playerName.toLowerCase().trim());
        } finally {
            lock.readLock().unlock();
        }
    }

    //  toggle system with validation and feedback
    public boolean toggleSetting(String setting) {
        if (setting == null || setting.trim().isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            boolean wasToggled = toggleSettings.contains(setting);
            if (wasToggled) {
                toggleSettings.remove(setting);
            } else {
                toggleSettings.add(setting);
            }
            markDirty();

            // Provide  feedback if player is online
            if (isOnline()) {
                Player player = getBukkitPlayer();
                if (player != null) {
                    String status = !wasToggled ? "enabled" : "disabled";
                    String color = !wasToggled ? ChatColor.GREEN.toString() : ChatColor.RED.toString();

                    player.sendMessage(color + "§l✦ " + setting + " " + status + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f,
                            !wasToggled ? 1.2f : 0.8f);
                }
            }

            return !wasToggled;
        } finally {
            lock.writeLock().unlock();
        }
    }

    //  notification settings
    public boolean setNotificationSetting(String type, boolean enabled) {
        if (type == null || type.trim().isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            notificationSettings.put(type, enabled);
            markDirty();

            // Notify player if online
            String status = enabled ? "enabled" : "disabled";
            sendMessageIfOnline(ChatColor.YELLOW + "Notification setting '" + type + "' " + status + ".");

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean getNotificationSetting(String type) {
        if (type == null) return true; // Default to enabled

        lock.readLock().lock();
        try {
            return notificationSettings.getOrDefault(type, true);
        } finally {
            lock.readLock().unlock();
        }
    }

    //  inventory management with error handling
    public void updateInventory(Player player) {
        if (player == null) return;

        lock.writeLock().lock();
        try {
            this.serializedInventory = ItemSerializer.serializeItemStacks(player.getInventory().getContents());
            this.serializedArmor = ItemSerializer.serializeItemStacks(player.getInventory().getArmorContents());
            this.serializedEnderChest = ItemSerializer.serializeItemStacks(player.getEnderChest().getContents());
            this.serializedOffhand = ItemSerializer.serializeItemStack(player.getInventory().getItemInOffHand());
            markDirty();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating inventory for player " + player.getName(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearTemporaryData() {
        // Save combat logout state before clearing
        CombatLogoutState savedState = combatLogoutState;
        temporaryData.clear();
        combatLogoutState = savedState;
    }

    //  stats management with validation
    public void updateStats(Player player) {
        if (player == null) return;

        lock.writeLock().lock();
        try {
            this.health = Math.max(0, Math.min(player.getMaxHealth(), player.getHealth()));
            this.maxHealth = Math.max(1, player.getMaxHealth());
            this.foodLevel = Math.max(0, Math.min(20, player.getFoodLevel()));
            this.saturation = Math.max(0, player.getSaturation());
            this.xpLevel = Math.max(0, player.getLevel());
            this.xpProgress = Math.max(0, Math.min(1, player.getExp()));
            this.totalExperience = Math.max(0, player.getTotalExperience());
            this.gameMode = player.getGameMode().name();

            // Update bed spawn location
            Location bedLoc = player.getBedSpawnLocation();
            if (bedLoc != null && bedLoc.getWorld() != null) {
                this.bedSpawnLocation = LocationSerializer.serialize(bedLoc);
            }

            // Update potion effects
            updatePotionEffects(player);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void applyStats(Player player) {
        if (player == null) return;

        lock.readLock().lock();
        try {
            // Apply health
            if (maxHealth > 0) {
                player.setMaxHealth(maxHealth);
                player.setHealth(Math.min(health, maxHealth));
            }

            // Apply food and saturation
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);

            // Apply experience
            player.setLevel(xpLevel);
            player.setExp(xpProgress);
            player.setTotalExperience(totalExperience);

            // Apply game mode if different
            try {
                GameMode mode = GameMode.valueOf(gameMode);
                if (player.getGameMode() != mode) {
                    player.setGameMode(mode);
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid game mode for player " + username + ": " + gameMode);
            }

            // Apply bed spawn location
            if (bedSpawnLocation != null) {
                Location bedLoc = LocationSerializer.deserialize(bedSpawnLocation);
                if (bedLoc != null) {
                    player.setBedSpawnLocation(bedLoc, true);
                }
            }

            // Apply potion effects
            applyPotionEffects(player);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying stats for player " + player.getName(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void updatePotionEffects(Player player) {
        activePotionEffects.clear();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            activePotionEffects.add(PotionEffectSerializer.serialize(effect));
        }
    }

    private void applyPotionEffects(Player player) {
        // Clear existing effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Apply saved effects
        for (String effectData : activePotionEffects) {
            PotionEffect effect = PotionEffectSerializer.deserialize(effectData);
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }
    }

    //  location management with validation
    public void updateLocation(Location location) {
        if (location == null || location.getWorld() == null) return;

        lock.writeLock().lock();
        try {
            // Store previous location
            if (this.world != null) {
                this.previousLocation = this.world + ":" + this.locationX + ":" + this.locationY + ":" + this.locationZ;
            }

            this.world = location.getWorld().getName();
            this.locationX = location.getX();
            this.locationY = location.getY();
            this.locationZ = location.getZ();
            this.locationYaw = location.getYaw();
            this.locationPitch = location.getPitch();
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Location getLocation() {
        lock.readLock().lock();
        try {
            if (world == null) return null;

            World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null) return null;

            return new Location(bukkitWorld, locationX, locationY, locationZ, locationYaw, locationPitch);
        } finally {
            lock.readLock().unlock();
        }
    }

    //  display name with caching and validation
    public String getFormattedDisplayName() {
        long currentTime = System.currentTimeMillis();

        lock.readLock().lock();
        try {
            if (cachedDisplayName != null &&
                    currentTime - displayNameCacheTime < DISPLAY_NAME_CACHE_DURATION) {
                return cachedDisplayName;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            // Double-check pattern
            if (cachedDisplayName != null &&
                    currentTime - displayNameCacheTime < DISPLAY_NAME_CACHE_DURATION) {
                return cachedDisplayName;
            }

            StringBuilder displayName = new StringBuilder();

            try {
                // Add guild tag if in guild
                if (isInGuild()) {
                    displayName.append(ChatColor.WHITE).append("[").append(guildName).append("] ");
                }

                // Add chat tag if not default
                if (!chatTag.equals("DEFAULT")) {
                    ChatTag chatTagEnum = ChatTag.getByName(chatTag);
                    if (chatTagEnum != ChatTag.DEFAULT) {
                        displayName.append(chatTagEnum.getTag()).append(" ");
                    }
                }

                // Add rank tag if not default
                if (!rank.equals("DEFAULT")) {
                    Rank rankEnum = Rank.fromString(rank);
                    if (rankEnum != Rank.DEFAULT) {
                        displayName.append(ChatColor.translateAlternateColorCodes('&', rankEnum.tag)).append(" ");
                    }
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid rank or chat tag for player " + username + ": " + e.getMessage());
            }

            // Add colored username based on alignment
            displayName.append(getColorByAlignment()).append(username);

            this.cachedDisplayName = displayName.toString();
            this.displayNameCacheTime = currentTime;

            return cachedDisplayName;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ChatColor getColorByAlignment() {
        switch (alignment) {
            case "LAWFUL": return ChatColor.GRAY;
            case "NEUTRAL": return ChatColor.YELLOW;
            case "CHAOTIC": return ChatColor.RED;
            default: return ChatColor.GRAY;
        }
    }

    //  PvP tracking
    public void recordKill(String victimName, int victimTier) {
        if (victimName == null || victimTier < 1 || victimTier > 6) return;

        lock.writeLock().lock();
        try {
            playerKills++;
            killStreak++;

            if (killStreak > bestKillStreak) {
                bestKillStreak = killStreak;
            }

            // Record tier-specific kill
            switch (victimTier) {
                case 1: t1Kills++; break;
                case 2: t2Kills++; break;
                case 3: t3Kills++; break;
                case 4: t4Kills++; break;
                case 5: t5Kills++; break;
                case 6: t6Kills++; break;
            }

            // Adjust PvP rating based on victim tier
            int ratingGain = victimTier * 5;
            pvpRating += ratingGain;

            markDirty();

            // Notify player
            sendMessageIfOnline(ChatColor.GREEN + "Kill recorded! " +
                    ChatColor.GRAY + "(Streak: " + killStreak + ", Rating: +" + ratingGain + ")");

        } finally {
            lock.writeLock().unlock();
        }
    }

    public void recordDeath() {
        lock.writeLock().lock();
        try {
            deaths++;
            killStreak = 0; // Reset kill streak

            // Lose PvP rating on death
            int ratingLoss = Math.min(50, pvpRating / 20);
            pvpRating = Math.max(0, pvpRating - ratingLoss);

            markDirty();

            // Notify player
            sendMessageIfOnline(ChatColor.RED + "Death recorded. " +
                    ChatColor.GRAY + "(Kill streak reset, Rating: -" + ratingLoss + ")");

        } finally {
            lock.writeLock().unlock();
        }
    }

    //  combat tracking
    public void addDamageDealt(double damage) {
        if (damage <= 0) return;

        lock.writeLock().lock();
        try {
            this.damageDealt += (long) damage;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addDamageTaken(double damage) {
        if (damage <= 0) return;

        lock.writeLock().lock();
        try {
            this.damageTaken += (long) damage;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addDamageBlocked(double damage) {
        if (damage <= 0) return;

        lock.writeLock().lock();
        try {
            this.damageBlocked += (long) damage;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addDamageDodged(double damage) {
        if (damage <= 0) return;

        lock.writeLock().lock();
        try {
            this.damageDodged += (long) damage;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    //  achievement system
    public boolean unlockAchievement(String achievementId) {
        if (achievementId == null || achievementId.trim().isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (achievements.add(achievementId)) {
                achievementPoints += 10; // Default points per achievement
                markDirty();

                // Notify player with fanfare
                Player player = getBukkitPlayer();
                if (player != null) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GOLD + "§l✦ ACHIEVEMENT UNLOCKED! ✦");
                    player.sendMessage(ChatColor.YELLOW + achievementId);
                    player.sendMessage(ChatColor.GRAY + "+10 Achievement Points");
                    player.sendMessage("");

                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                }

                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean hasAchievement(String achievementId) {
        if (achievementId == null) return false;

        lock.readLock().lock();
        try {
            return achievements.contains(achievementId);
        } finally {
            lock.readLock().unlock();
        }
    }

    //  quest system
    public boolean startQuest(String questId) {
        if (questId == null || questId.trim().isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (!currentQuest.isEmpty()) {
                sendMessageIfOnline(ChatColor.RED + "You already have an active quest!");
                return false;
            }

            if (completedQuests.contains(questId)) {
                sendMessageIfOnline(ChatColor.RED + "You have already completed this quest!");
                return false;
            }

            currentQuest = questId;
            questProgress = 0;
            markDirty();

            sendMessageIfOnline(ChatColor.GREEN + "Started quest: " + questId);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean completeQuest() {
        lock.writeLock().lock();
        try {
            if (currentQuest.isEmpty()) {
                return false;
            }

            String completedQuest = currentQuest;
            completedQuests.add(completedQuest);
            currentQuest = "";
            questProgress = 0;
            questPoints += 10; // Default quest points
            markDirty();

            sendMessageIfOnline(ChatColor.GREEN + "§lQUEST COMPLETED: " + completedQuest);
            sendMessageIfOnline(ChatColor.YELLOW + "+10 Quest Points");

            // Play completion sound
            Player player = getBukkitPlayer();
            if (player != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateQuestProgress(int progress) {
        lock.writeLock().lock();
        try {
            if (!currentQuest.isEmpty()) {
                this.questProgress = Math.max(0, progress);
                markDirty();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    //  profession system
    public void addProfessionXP(String profession, int xp) {
        if (profession == null || xp <= 0) return;

        lock.writeLock().lock();
        try {
            switch (profession.toLowerCase()) {
                case "mining":
                    miningXp += xp;
                    checkProfessionLevelUp("mining", miningXp, pickaxeLevel);
                    break;
                case "fishing":
                    fishingXp += xp;
                    checkProfessionLevelUp("fishing", fishingXp, fishingLevel);
                    break;
                case "farming":
                    farmingXp += xp;
                    checkProfessionLevelUp("farming", farmingXp, farmingLevel);
                    break;
                case "woodcutting":
                    woodcuttingXp += xp;
                    checkProfessionLevelUp("woodcutting", woodcuttingXp, woodcuttingLevel);
                    break;
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void checkProfessionLevelUp(String profession, int currentXp, int currentLevel) {
        int requiredXp = (currentLevel + 1) * 100; // Simple XP formula

        if (currentXp >= requiredXp) {
            // Level up!
            switch (profession) {
                case "mining":
                    pickaxeLevel++;
                    break;
                case "fishing":
                    fishingLevel++;
                    break;
                case "farming":
                    farmingLevel++;
                    break;
                case "woodcutting":
                    woodcuttingLevel++;
                    break;
            }

            // Notify player
            sendMessageIfOnline(ChatColor.GOLD + "§l" + profession.toUpperCase() + " LEVEL UP! " +
                    ChatColor.YELLOW + "Level " + (currentLevel + 1));

            Player player = getBukkitPlayer();
            if (player != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
    }

    //  moderation methods
    public void setBanned(boolean banned, String reason, long expiry) {
        lock.writeLock().lock();
        try {
            this.banned = banned;
            this.banReason = reason != null ? reason : "";
            this.banExpiry = expiry;
            markDirty();

            if (banned) {
                logger.info("Player " + username + " has been banned. Reason: " + reason);

                // Kick if online
                Player player = getBukkitPlayer();
                if (player != null && player.isOnline()) {
                    String kickMessage = ChatColor.RED + "You have been banned from this server!\n" +
                            ChatColor.GRAY + "Reason: " + reason;
                    player.kickPlayer(kickMessage);
                }
            } else {
                logger.info("Player " + username + " has been unbanned.");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addWarning(String reason) {
        lock.writeLock().lock();
        try {
            warnings++;
            lastWarning = Instant.now().getEpochSecond();
            markDirty();

            sendMessageIfOnline(ChatColor.RED + "§lWARNING: " + reason);
            sendMessageIfOnline(ChatColor.GRAY + "Total warnings: " + warnings);

            logger.info("Player " + username + " received warning #" + warnings + ": " + reason);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // PvP methods needed by other classes
    public void addPlayerKill() {
        lock.writeLock().lock();
        try {
            playerKills++;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addDeath() {
        lock.writeLock().lock();
        try {
            deaths++;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addMonsterKill() {
        lock.writeLock().lock();
        try {
            monsterKills++;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Chat tag methods
    public void unlockChatTag(ChatTag tag) {
        if (tag == null) return;

        lock.writeLock().lock();
        try {
            unlockedChatTags.add(tag.name());
            markDirty();

            sendMessageIfOnline(ChatColor.GREEN + "Unlocked chat tag: " + tag.getTag());
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Utility methods
    private void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    private void sendMessageIfOnline(String message) {
        Player player = getBukkitPlayer();
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    //  temporary data management
    public void setTemporaryData(String key, Object value) {
        if (key != null && value != null) {
            temporaryData.put(key, value);
        }
    }

    public Object getTemporaryData(String key) {
        return key != null ? temporaryData.get(key) : null;
    }

    public boolean hasTemporaryData(String key) {
        return key != null && temporaryData.containsKey(key);
    }

    public void removeTemporaryData(String key) {
        if (key != null) {
            temporaryData.remove(key);
        }
    }

    public String getSerializedRespawnItems() {
        lock.readLock().lock();
        try {
            return serializedRespawnItems;
        } finally {
            lock.readLock().unlock();
        }
    }

    // PUBLIC ItemStack serialization methods (used by other classes)
    public String serializeItemStacks(ItemStack[] items) {
        try {
            return ItemSerializer.serializeItemStacks(items);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error serializing item stacks", e);
            return null;
        }
    }

    public ItemStack[] deserializeItemStacks(String data) {
        try {
            return ItemSerializer.deserializeItemStacks(data);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deserializing item stacks", e);
            return new ItemStack[0];
        }
    }

    // Comprehensive getters and setters with proper synchronization
    public UUID getUUID() { return uuid; }

    public String getUsername() {
        lock.readLock().lock();
        try {
            return username;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setUsername(String username) {
        lock.writeLock().lock();
        try {
            this.username = validateUsername(username);
            this.cachedDisplayName = null; // Invalidate cache
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getLastLogin() {
        lock.readLock().lock();
        try {
            return lastLogin;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLastLogin(long lastLogin) {
        lock.writeLock().lock();
        try {
            this.lastLogin = lastLogin;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getLastLogout() {
        lock.readLock().lock();
        try {
            return lastLogout;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLastLogout(long lastLogout) {
        lock.writeLock().lock();
        try {
            this.lastLogout = lastLogout;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getFirstJoin() {
        lock.readLock().lock();
        try {
            return firstJoin;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setFirstJoin(long firstJoin) {
        lock.writeLock().lock();
        try {
            this.firstJoin = firstJoin;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getIpAddress() {
        lock.readLock().lock();
        try {
            return ipAddress;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setIpAddress(String ipAddress) {
        lock.writeLock().lock();
        try {
            this.ipAddress = ipAddress;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getLevel() {
        lock.readLock().lock();
        try {
            return level;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLevel(int level) {
        lock.writeLock().lock();
        try {
            this.level = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getExp() {
        lock.readLock().lock();
        try {
            return exp;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setExp(int exp) {
        lock.writeLock().lock();
        try {
            this.exp = Math.max(0, exp);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getMonsterKills() {
        lock.readLock().lock();
        try {
            return monsterKills;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setMonsterKills(int monsterKills) {
        lock.writeLock().lock();
        try {
            this.monsterKills = Math.max(0, monsterKills);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getPlayerKills() {
        lock.readLock().lock();
        try {
            return playerKills;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setPlayerKills(int playerKills) {
        lock.writeLock().lock();
        try {
            this.playerKills = Math.max(0, playerKills);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getDeaths() {
        lock.readLock().lock();
        try {
            return deaths;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDeaths(int deaths) {
        lock.writeLock().lock();
        try {
            this.deaths = Math.max(0, deaths);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getOreMined() {
        lock.readLock().lock();
        try {
            return oreMined;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setOreMined(int oreMined) {
        lock.writeLock().lock();
        try {
            this.oreMined = Math.max(0, oreMined);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getFishCaught() {
        lock.readLock().lock();
        try {
            return fishCaught;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setFishCaught(int fishCaught) {
        lock.writeLock().lock();
        try {
            this.fishCaught = Math.max(0, fishCaught);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getBankGems() {
        lock.readLock().lock();
        try {
            return bankGems;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBankGems(int bankGems) {
        lock.writeLock().lock();
        try {
            this.bankGems = Math.max(MIN_GEMS, Math.min(MAX_GEMS, bankGems));
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getEliteShards() {
        lock.readLock().lock();
        try {
            return eliteShards;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setEliteShards(int eliteShards) {
        lock.writeLock().lock();
        try {
            this.eliteShards = Math.max(0, eliteShards);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getBankPages() {
        lock.readLock().lock();
        try {
            return bankPages;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBankPages(int bankPages) {
        lock.writeLock().lock();
        try {
            this.bankPages = Math.max(1, Math.min(MAX_BANK_PAGES, bankPages));
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getSerializedBankItems(int page) {
        lock.readLock().lock();
        try {
            return serializedBankItems.get(page);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setSerializedBankItems(int page, String data) {
        lock.writeLock().lock();
        try {
            if (data != null && !data.trim().isEmpty()) {
                serializedBankItems.put(page, data);
            } else {
                serializedBankItems.remove(page);
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<Integer, String> getAllSerializedBankItems() {
        lock.readLock().lock();
        try {
            return new HashMap<>(serializedBankItems);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> getBankAuthorizedUsers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(bankAuthorizedUsers);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBankAuthorizedUsers(List<String> users) {
        lock.writeLock().lock();
        try {
            bankAuthorizedUsers.clear();
            if (users != null) {
                bankAuthorizedUsers.addAll(users);
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getAlignment() {
        lock.readLock().lock();
        try {
            return alignment;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setAlignment(String alignment) {
        if (alignment == null) return;

        lock.writeLock().lock();
        try {
            String oldAlignment = this.alignment;
            this.alignment = alignment;
            long currentTime = Instant.now().getEpochSecond();

            if ("NEUTRAL".equals(alignment)) {
                this.neutralTime = currentTime;
            } else if ("CHAOTIC".equals(alignment)) {
                this.chaoticTime = currentTime;
            }

            // Track alignment changes
            if (!oldAlignment.equals(alignment)) {
                this.alignmentChanges++;
            }

            this.cachedDisplayName = null; // Invalidate cache
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getChaoticTime() {
        lock.readLock().lock();
        try {
            return chaoticTime;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setChaoticTime(long chaoticTime) {
        lock.writeLock().lock();
        try {
            this.chaoticTime = chaoticTime;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getNeutralTime() {
        lock.readLock().lock();
        try {
            return neutralTime;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setNeutralTime(long neutralTime) {
        lock.writeLock().lock();
        try {
            this.neutralTime = neutralTime;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getRank() {
        lock.readLock().lock();
        try {
            return rank;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setRank(String rank) {
        lock.writeLock().lock();
        try {
            this.rank = rank != null ? rank : "DEFAULT";
            this.cachedDisplayName = null; // Invalidate cache
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isBanned() {
        lock.readLock().lock();
        try {
            return banned;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBanned(boolean banned) {
        lock.writeLock().lock();
        try {
            this.banned = banned;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getBanReason() {
        lock.readLock().lock();
        try {
            return banReason;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBanReason(String banReason) {
        lock.writeLock().lock();
        try {
            this.banReason = banReason != null ? banReason : "";
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getBanExpiry() {
        lock.readLock().lock();
        try {
            return banExpiry;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBanExpiry(long banExpiry) {
        lock.writeLock().lock();
        try {
            this.banExpiry = banExpiry;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getMuteTime() {
        lock.readLock().lock();
        try {
            return muteTime;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setMuteTime(int muteTime) {
        lock.writeLock().lock();
        try {
            this.muteTime = Math.max(0, muteTime);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isMuted() {
        lock.readLock().lock();
        try {
            return muteTime > 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getChatTag() {
        lock.readLock().lock();
        try {
            return chatTag;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setChatTag(String chatTag) {
        lock.writeLock().lock();
        try {
            this.chatTag = chatTag != null ? chatTag : "DEFAULT";
            this.cachedDisplayName = null; // Invalidate cache
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<String> getUnlockedChatTags() {
        lock.readLock().lock();
        try {
            return new HashSet<>(unlockedChatTags);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setUnlockedChatTags(List<String> tags) {
        lock.writeLock().lock();
        try {
            unlockedChatTags.clear();
            if (tags != null) {
                unlockedChatTags.addAll(tags);
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getHorseTier() {
        lock.readLock().lock();
        try {
            return horseTier;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setHorseTier(int horseTier) {
        lock.writeLock().lock();
        try {
            this.horseTier = Math.max(0, horseTier);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getGuildName() {
        lock.readLock().lock();
        try {
            return guildName != null ? guildName : "";
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setGuildName(String guildName) {
        lock.writeLock().lock();
        try {
            this.guildName = guildName != null ? guildName : "";
            this.cachedDisplayName = null; // Invalidate cache
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getGuildRank() {
        lock.readLock().lock();
        try {
            return guildRank != null ? guildRank : "";
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setGuildRank(String guildRank) {
        lock.writeLock().lock();
        try {
            this.guildRank = guildRank != null ? guildRank : "";
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isInGuild() {
        lock.readLock().lock();
        try {
            return guildName != null && !guildName.trim().isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<String> getToggleSettings() {
        lock.readLock().lock();
        try {
            return new HashSet<>(toggleSettings);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setToggleSettings(Set<String> settings) {
        lock.writeLock().lock();
        try {
            toggleSettings.clear();
            if (settings != null) {
                toggleSettings.addAll(settings);
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isToggled(String setting) {
        if (setting == null) return false;

        lock.readLock().lock();
        try {
            return toggleSettings.contains(setting);
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getCurrentQuest() {
        lock.readLock().lock();
        try {
            return currentQuest;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setCurrentQuest(String currentQuest) {
        lock.writeLock().lock();
        try {
            this.currentQuest = currentQuest != null ? currentQuest : "";
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getQuestProgress() {
        lock.readLock().lock();
        try {
            return questProgress;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setQuestProgress(int questProgress) {
        lock.writeLock().lock();
        try {
            this.questProgress = Math.max(0, questProgress);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<String> getCompletedQuests() {
        lock.readLock().lock();
        try {
            return new HashSet<>(completedQuests);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setCompletedQuests(List<String> quests) {
        lock.writeLock().lock();
        try {
            completedQuests.clear();
            if (quests != null) {
                completedQuests.addAll(quests);
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getPickaxeLevel() {
        lock.readLock().lock();
        try {
            return pickaxeLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setPickaxeLevel(int pickaxeLevel) {
        lock.writeLock().lock();
        try {
            this.pickaxeLevel = Math.max(0, pickaxeLevel);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getFishingLevel() {
        lock.readLock().lock();
        try {
            return fishingLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setFishingLevel(int fishingLevel) {
        lock.writeLock().lock();
        try {
            this.fishingLevel = Math.max(0, fishingLevel);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getMiningXp() {
        lock.readLock().lock();
        try {
            return miningXp;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setMiningXp(int miningXp) {
        lock.writeLock().lock();
        try {
            this.miningXp = Math.max(0, miningXp);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getFishingXp() {
        lock.readLock().lock();
        try {
            return fishingXp;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setFishingXp(int fishingXp) {
        lock.writeLock().lock();
        try {
            this.fishingXp = Math.max(0, fishingXp);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getT1Kills() {
        lock.readLock().lock();
        try {
            return t1Kills;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setT1Kills(int t1Kills) {
        lock.writeLock().lock();
        try {
            this.t1Kills = Math.max(0, t1Kills);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getT2Kills() {
        lock.readLock().lock();
        try {
            return t2Kills;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setT2Kills(int t2Kills) {
        lock.writeLock().lock();
        try {
            this.t2Kills = Math.max(0, t2Kills);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getT3Kills() {
        lock.readLock().lock();
        try {
            return t3Kills;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setT3Kills(int t3Kills) {
        lock.writeLock().lock();
        try {
            this.t3Kills = Math.max(0, t3Kills);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getT4Kills() {
        lock.readLock().lock();
        try {
            return t4Kills;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setT4Kills(int t4Kills) {
        lock.writeLock().lock();
        try {
            this.t4Kills = Math.max(0, t4Kills);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getT5Kills() {
        lock.readLock().lock();
        try {
            return t5Kills;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setT5Kills(int t5Kills) {
        lock.writeLock().lock();
        try {
            this.t5Kills = Math.max(0, t5Kills);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getT6Kills() {
        lock.readLock().lock();
        try {
            return t6Kills;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setT6Kills(int t6Kills) {
        lock.writeLock().lock();
        try {
            this.t6Kills = Math.max(0, t6Kills);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, Integer> getWorldBossDamage() {
        lock.readLock().lock();
        try {
            return new HashMap<>(worldBossDamage);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setWorldBossDamage(Map<String, Integer> damage) {
        lock.writeLock().lock();
        try {
            worldBossDamage.clear();
            if (damage != null) {
                worldBossDamage.putAll(damage);
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isTradeDisabled() {
        lock.readLock().lock();
        try {
            return tradeDisabled;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setTradeDisabled(boolean tradeDisabled) {
        lock.writeLock().lock();
        try {
            this.tradeDisabled = tradeDisabled;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<String> getBuddies() {
        lock.readLock().lock();
        try {
            return new HashSet<>(buddies);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBuddies(List<String> buddyList) {
        lock.writeLock().lock();
        try {
            buddies.clear();
            if (buddyList != null) {
                buddies.addAll(buddyList);
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isEnergyDisabled() {
        lock.readLock().lock();
        try {
            return energyDisabled;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setEnergyDisabled(boolean energyDisabled) {
        lock.writeLock().lock();
        try {
            this.energyDisabled = energyDisabled;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getWorld() {
        lock.readLock().lock();
        try {
            return world;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setWorld(String world) {
        lock.writeLock().lock();
        try {
            this.world = world;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getLocationX() {
        lock.readLock().lock();
        try {
            return locationX;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLocationX(double locationX) {
        lock.writeLock().lock();
        try {
            this.locationX = locationX;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getLocationY() {
        lock.readLock().lock();
        try {
            return locationY;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLocationY(double locationY) {
        lock.writeLock().lock();
        try {
            this.locationY = locationY;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getLocationZ() {
        lock.readLock().lock();
        try {
            return locationZ;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLocationZ(double locationZ) {
        lock.writeLock().lock();
        try {
            this.locationZ = locationZ;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public float getLocationYaw() {
        lock.readLock().lock();
        try {
            return locationYaw;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLocationYaw(float locationYaw) {
        lock.writeLock().lock();
        try {
            this.locationYaw = locationYaw;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public float getLocationPitch() {
        lock.readLock().lock();
        try {
            return locationPitch;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLocationPitch(float locationPitch) {
        lock.writeLock().lock();
        try {
            this.locationPitch = locationPitch;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getSerializedInventory() {
        lock.readLock().lock();
        try {
            return serializedInventory;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setSerializedInventory(String serializedInventory) {
        lock.writeLock().lock();
        try {
            this.serializedInventory = serializedInventory;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getSerializedArmor() {
        lock.readLock().lock();
        try {
            return serializedArmor;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setSerializedArmor(String serializedArmor) {
        lock.writeLock().lock();
        try {
            this.serializedArmor = serializedArmor;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getSerializedEnderChest() {
        lock.readLock().lock();
        try {
            return serializedEnderChest;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setSerializedEnderChest(String serializedEnderChest) {
        lock.writeLock().lock();
        try {
            this.serializedEnderChest = serializedEnderChest;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getSerializedOffhand() {
        lock.readLock().lock();
        try {
            return serializedOffhand;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setSerializedOffhand(String serializedOffhand) {
        lock.writeLock().lock();
        try {
            this.serializedOffhand = serializedOffhand;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setSerializedRespawnItems(String serializedRespawnItems) {
        lock.writeLock().lock();
        try {
            this.serializedRespawnItems = serializedRespawnItems;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getFarmingXp() {
        lock.readLock().lock();
        try {
            return farmingXp;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getHealth() {
        lock.readLock().lock();
        try {
            return health;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setHealth(double health) {
        if(bukkitPlayer != null && (bukkitPlayer.isDead() || bukkitPlayer.getHealth() <= 0.0)) return;
        lock.writeLock().lock();
        try {
            this.health = Math.max(0, health);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getMaxHealth() {
        lock.readLock().lock();
        try {
            return maxHealth;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setMaxHealth(double maxHealth) {
        lock.writeLock().lock();
        try {
            this.maxHealth = Math.max(1, maxHealth);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getFoodLevel() {
        lock.readLock().lock();
        try {
            return foodLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setFoodLevel(int foodLevel) {
        lock.writeLock().lock();
        try {
            this.foodLevel = Math.max(0, Math.min(20, foodLevel));
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public float getSaturation() {
        lock.readLock().lock();
        try {
            return saturation;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setSaturation(float saturation) {
        lock.writeLock().lock();
        try {
            this.saturation = Math.max(0, saturation);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getXpLevel() {
        lock.readLock().lock();
        try {
            return xpLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setXpLevel(int xpLevel) {
        lock.writeLock().lock();
        try {
            this.xpLevel = Math.max(0, xpLevel);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public float getXpProgress() {
        lock.readLock().lock();
        try {
            return xpProgress;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setXpProgress(float xpProgress) {
        lock.writeLock().lock();
        try {
            this.xpProgress = Math.max(0, Math.min(1, xpProgress));
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getTotalExperience() {
        lock.readLock().lock();
        try {
            return totalExperience;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setTotalExperience(int totalExperience) {
        lock.writeLock().lock();
        try {
            this.totalExperience = Math.max(0, totalExperience);
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getBedSpawnLocation() {
        lock.readLock().lock();
        try {
            return bedSpawnLocation;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBedSpawnLocation(String bedSpawnLocation) {
        lock.writeLock().lock();
        try {
            this.bedSpawnLocation = bedSpawnLocation;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getGameMode() {
        lock.readLock().lock();
        try {
            return gameMode;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setGameMode(String gameMode) {
        lock.writeLock().lock();
        try {
            this.gameMode = gameMode != null ? gameMode : "SURVIVAL";
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<String> getActivePotionEffects() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(activePotionEffects);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setActivePotionEffects(List<String> effects) {
        lock.writeLock().lock();
        try {
            activePotionEffects.clear();
            if (effects != null) {
                activePotionEffects.addAll(effects);
            }
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getTotalPlaytime() {
        lock.readLock().lock();
        try {
            // Add current session time if online
            long total = totalPlaytime;
            if (isOnline() && sessionStartTime > 0) {
                total += (System.currentTimeMillis() - sessionStartTime) / 1000;
            }
            return total;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setTotalPlaytime(long totalPlaytime) {
        lock.writeLock().lock();
        try {
            this.totalPlaytime = totalPlaytime;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getBlocksBroken() {
        lock.readLock().lock();
        try {
            return blocksBroken;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBlocksBroken(int blocksBroken) {
        lock.writeLock().lock();
        try {
            this.blocksBroken = blocksBroken;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getDistanceTraveled() {
        lock.readLock().lock();
        try {
            return distanceTraveled;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDistanceTraveled(double distanceTraveled) {
        lock.writeLock().lock();
        try {
            this.distanceTraveled = distanceTraveled;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<String> getBankAccessLog() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(bankAccessLog);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getAlignmentChanges() {
        lock.readLock().lock();
        try {
            return alignmentChanges;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setAlignmentChanges(int alignmentChanges) {
        lock.writeLock().lock();
        try {
            this.alignmentChanges = alignmentChanges;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getWarnings() {
        lock.readLock().lock();
        try {
            return warnings;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setWarnings(int warnings) {
        lock.writeLock().lock();
        try {
            this.warnings = warnings;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getLastWarning() {
        lock.readLock().lock();
        try {
            return lastWarning;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLastWarning(long lastWarning) {
        lock.writeLock().lock();
        try {
            this.lastWarning = lastWarning;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getChatColor() {
        lock.readLock().lock();
        try {
            return chatColor;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setChatColor(String chatColor) {
        lock.writeLock().lock();
        try {
            this.chatColor = chatColor;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getHorseName() {
        lock.readLock().lock();
        try {
            return horseName;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setHorseName(String horseName) {
        lock.writeLock().lock();
        try {
            this.horseName = horseName;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getGuildContribution() {
        lock.readLock().lock();
        try {
            return guildContribution;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setGuildContribution(int guildContribution) {
        lock.writeLock().lock();
        try {
            this.guildContribution = guildContribution;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, Boolean> getNotificationSettings() {
        lock.readLock().lock();
        try {
            return new HashMap<>(notificationSettings);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getQuestPoints() {
        lock.readLock().lock();
        try {
            return questPoints;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setQuestPoints(int questPoints) {
        lock.writeLock().lock();
        try {
            this.questPoints = questPoints;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getDailyQuestsCompleted() {
        lock.readLock().lock();
        try {
            return dailyQuestsCompleted;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDailyQuestsCompleted(int dailyQuestsCompleted) {
        lock.writeLock().lock();
        try {
            this.dailyQuestsCompleted = dailyQuestsCompleted;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getLastDailyQuestReset() {
        lock.readLock().lock();
        try {
            return lastDailyQuestReset;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLastDailyQuestReset(long lastDailyQuestReset) {
        lock.writeLock().lock();
        try {
            this.lastDailyQuestReset = lastDailyQuestReset;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getFarmingLevel() {
        lock.readLock().lock();
        try {
            return farmingLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setFarmingLevel(int farmingLevel) {
        lock.writeLock().lock();
        try {
            this.farmingLevel = farmingLevel;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setFarmingXp(int farmingXp) {
        lock.writeLock().lock();
        try {
            this.farmingXp = farmingXp;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getWoodcuttingXp() {
        lock.readLock().lock();
        try {
            return woodcuttingXp;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getWoodcuttingLevel() {
        lock.readLock().lock();
        try {
            return woodcuttingLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setWoodcuttingLevel(int woodcuttingLevel) {
        lock.writeLock().lock();
        try {
            this.woodcuttingLevel = woodcuttingLevel;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setWoodcuttingXp(int woodcuttingXp) {
        lock.writeLock().lock();
        try {
            this.woodcuttingXp = woodcuttingXp;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ItemStack[] getRespawnItemsArray() {
        return respawnItems != null ? respawnItems.clone() : null;
    }

    public int getKillStreak() {
        lock.readLock().lock();
        try {
            return killStreak;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setKillStreak(int killStreak) {
        lock.writeLock().lock();
        try {
            this.killStreak = killStreak;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getBestKillStreak() {
        lock.readLock().lock();
        try {
            return bestKillStreak;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setBestKillStreak(int bestKillStreak) {
        lock.writeLock().lock();
        try {
            this.bestKillStreak = bestKillStreak;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getPvpRating() {
        lock.readLock().lock();
        try {
            return pvpRating;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setPvpRating(int pvpRating) {
        lock.writeLock().lock();
        try {
            this.pvpRating = pvpRating;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, Integer> getWorldBossKills() {
        lock.readLock().lock();
        try {
            return new HashMap<>(worldBossKills);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<String> getBlockedPlayers() {
        lock.readLock().lock();
        try {
            return new HashSet<>(blockedPlayers);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setPreviousLocation(String previousLocation) {
        lock.writeLock().lock();
        try {
            this.previousLocation = previousLocation;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<String> getAchievements() {
        lock.readLock().lock();
        try {
            return new HashSet<>(achievements);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getAchievementPoints() {
        lock.readLock().lock();
        try {
            return achievementPoints;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setAchievementPoints(int achievementPoints) {
        lock.writeLock().lock();
        try {
            this.achievementPoints = achievementPoints;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<String> getDailyRewardsClaimed() {
        lock.readLock().lock();
        try {
            return new HashSet<>(dailyRewardsClaimed);
        } finally {
            lock.readLock().unlock();
        }
    }

    public long getLastDailyReward() {
        lock.readLock().lock();
        try {
            return lastDailyReward;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setLastDailyReward(long lastDailyReward) {
        lock.writeLock().lock();
        try {
            this.lastDailyReward = lastDailyReward;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, Integer> getEventsParticipated() {
        lock.readLock().lock();
        try {
            return new HashMap<>(eventsParticipated);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Integer> getEventWins() {
        lock.readLock().lock();
        try {
            return new HashMap<>(eventWins);
        } finally {
            lock.readLock().unlock();
        }
    }

    public long getDamageDealt() {
        lock.readLock().lock();
        try {
            return damageDealt;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDamageDealt(long damageDealt) {
        lock.writeLock().lock();
        try {
            this.damageDealt = damageDealt;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getDamageTaken() {
        lock.readLock().lock();
        try {
            return damageTaken;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDamageTaken(long damageTaken) {
        lock.writeLock().lock();
        try {
            this.damageTaken = damageTaken;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getDamageBlocked() {
        lock.readLock().lock();
        try {
            return damageBlocked;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDamageBlocked(long damageBlocked) {
        lock.writeLock().lock();
        try {
            this.damageBlocked = damageBlocked;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getDamageDodged() {
        lock.readLock().lock();
        try {
            return damageDodged;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDamageDodged(long damageDodged) {
        lock.writeLock().lock();
        try {
            this.damageDodged = damageDodged;
            markDirty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setRespawnItems(ItemStack[] items) {
        this.respawnItems = items != null ? items.clone() : null;
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return "YakPlayer{" +
                    "uuid=" + uuid +
                    ", username='" + username + '\'' +
                    ", level=" + level +
                    ", rank='" + rank + '\'' +
                    ", alignment='" + alignment + '\'' +
                    ", world='" + world + '\'' +
                    ", location=(" + locationX + "," + locationY + "," + locationZ + ")" +
                    ", health=" + health + "/" + maxHealth +
                    ", bankGems=" + bankGems +
                    ", online=" + isOnline() +
                    ", buddies=" + buddies.size() +
                    ", achievements=" + achievements.size() +
                    ", playtime=" + getTotalPlaytime() + "s" +
                    ", combatLogoutState=" + combatLogoutState +
                    ", respawnItems=" + hasRespawnItems() +
                    '}';
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Combat logout state enum for tracking processing status
     */
    public enum CombatLogoutState {
        NONE,           // No combat logout
        PROCESSING,     // Currently processing logout
        PROCESSED,      // Items already processed, needs death
        COMPLETED       // Death completed, cleanup done
    }

    // Helper classes for serialization
    private static class ItemSerializer {
        public static String serializeItemStack(ItemStack item) throws IOException {
            if (item == null || item.getType() == Material.AIR) {
                return null;
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

                dataOutput.writeObject(item);
                return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            }
        }

        public static ItemStack deserializeItemStack(String data) throws IOException, ClassNotFoundException {
            if (data == null || data.isEmpty()) {
                return null;
            }

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

                return (ItemStack) dataInput.readObject();
            }
        }

        public static String serializeItemStacks(ItemStack[] items) throws IOException {
            if (items == null || items.length == 0) {
                return null;
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

                dataOutput.writeInt(items.length);
                for (ItemStack item : items) {
                    dataOutput.writeObject(item);
                }

                return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            }
        }

        public static ItemStack[] deserializeItemStacks(String data) throws IOException, ClassNotFoundException {
            if (data == null || data.isEmpty()) {
                return new ItemStack[0];
            }

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

                int length = dataInput.readInt();
                ItemStack[] items = new ItemStack[length];

                for (int i = 0; i < length; i++) {
                    items[i] = (ItemStack) dataInput.readObject();
                }

                return items;
            }
        }
    }

    private static class LocationSerializer {
        public static String serialize(Location location) {
            if (location == null || location.getWorld() == null) {
                return null;
            }

            return location.getWorld().getName() + ":" +
                    location.getX() + ":" + location.getY() + ":" + location.getZ() + ":" +
                    location.getYaw() + ":" + location.getPitch();
        }

        public static Location deserialize(String data) {
            if (data == null || data.isEmpty()) {
                return null;
            }

            try {
                String[] parts = data.split(":");
                if (parts.length != 6) return null;

                World world = Bukkit.getWorld(parts[0]);
                if (world == null) return null;

                return new Location(
                        world,
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Float.parseFloat(parts[4]),
                        Float.parseFloat(parts[5])
                );
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to deserialize location: " + data, e);
                return null;
            }
        }
    }

    private static class PotionEffectSerializer {
        public static String serialize(PotionEffect effect) {
            return effect.getType().getName() + ":" + effect.getAmplifier() + ":" + effect.getDuration();
        }

        public static PotionEffect deserialize(String data) {
            try {
                String[] parts = data.split(":");
                PotionEffectType type = PotionEffectType.getByName(parts[0]);
                if (type != null) {
                    return new PotionEffect(type, Integer.parseInt(parts[2]), Integer.parseInt(parts[1]));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to deserialize potion effect: " + data, e);
            }
            return null;
        }
    }
}