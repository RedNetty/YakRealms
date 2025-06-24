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
 * Enhanced core player data model with improved thread safety,
 * validation, performance optimizations, and comprehensive functionality.
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

    // Economy data with validation
    @Expose @SerializedName("gems")
    private volatile int gems = 0;

    @Expose @SerializedName("bank_gems")
    private volatile int bankGems = 0;

    @Expose @SerializedName("elite_shards")
    private volatile int eliteShards = 0;

    @Expose @SerializedName("total_gems_earned")
    private volatile long totalGemsEarned = 0;

    @Expose @SerializedName("total_gems_spent")
    private volatile long totalGemsSpent = 0;

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

    // Non-serialized transient fields
    private transient volatile Player bukkitPlayer;
    private transient volatile boolean inCombat = false;
    private transient volatile long lastCombatTime = 0;
    private transient final Map<String, Object> temporaryData = new ConcurrentHashMap<>();
    private transient volatile ItemStack[] respawnItems;
    private transient volatile boolean dirty = false; // Optimization flag for saves
    private transient volatile long sessionStartTime;

    // Enhanced display name caching
    private transient volatile String cachedDisplayName;
    private transient volatile long displayNameCacheTime = 0;
    private static final long DISPLAY_NAME_CACHE_DURATION = 30000; // 30 seconds

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

    // Enhanced connection management
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

            markDirty();

            // Enhanced energy level display
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                player.setExp(1.0f);
                player.setLevel(100);
            });

            logger.fine("Connected player: " + player.getName());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void disconnect() {
        lock.writeLock().lock();
        try {
            this.lastLogout = Instant.now().getEpochSecond();

            // Update total playtime
            if (sessionStartTime > 0) {
                long sessionDuration = System.currentTimeMillis() - sessionStartTime;
                this.totalPlaytime += sessionDuration / 1000; // Convert to seconds
            }

            this.bukkitPlayer = null;
            markDirty();
            logger.fine("Disconnected player: " + username);
        } finally {
            lock.writeLock().unlock();
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

    // Enhanced gem management with validation and tracking
    public boolean addGems(int amount) {
        return addGems(amount, "Unknown");
    }

    public boolean addGems(int amount, String source) {
        if (amount < 0) {
            logger.warning("Attempted to add negative gems: " + amount + " from source: " + source);
            return false;
        }

        lock.writeLock().lock();
        try {
            long newTotal = (long) gems + amount;
            if (newTotal > MAX_GEMS) {
                logger.warning("Gem addition would exceed maximum: " + newTotal + " for player: " + username);
                return false;
            }

            this.gems += amount;
            this.totalGemsEarned += amount;
            markDirty();

            // Notify player if online
            sendMessageIfOnline(ChatColor.GREEN + "+" + amount + " gems!" +
                    (!"Unknown".equals(source) ? " (" + source + ")" : ""));

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeGems(int amount) {
        return removeGems(amount, "Unknown");
    }

    public boolean removeGems(int amount, String reason) {
        if (amount < 0) {
            logger.warning("Attempted to remove negative gems: " + amount + " for reason: " + reason);
            return false;
        }

        lock.writeLock().lock();
        try {
            if (gems >= amount) {
                gems -= amount;
                totalGemsSpent += amount;
                markDirty();

                // Notify player if online
                sendMessageIfOnline(ChatColor.RED + "-" + amount + " gems!" +
                        (!"Unknown".equals(reason) ? " (" + reason + ")" : ""));

                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Enhanced bank management with authorization and logging
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

    // Enhanced buddy management with limits and validation
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

    // Enhanced player blocking system
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

    // Enhanced toggle system with validation and feedback
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

            // Provide enhanced feedback if player is online
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

    // Enhanced notification settings
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

    // Enhanced inventory management with error handling
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

    public void applyInventory(Player player) {
        if (player == null) return;

        lock.readLock().lock();
        try {
            if (serializedInventory != null) {
                ItemStack[] contents = ItemSerializer.deserializeItemStacks(serializedInventory);
                if (contents != null) {
                    player.getInventory().setContents(contents);
                }
            }

            if (serializedArmor != null) {
                ItemStack[] armor = ItemSerializer.deserializeItemStacks(serializedArmor);
                if (armor != null) {
                    player.getInventory().setArmorContents(armor);
                }
            }

            if (serializedEnderChest != null) {
                ItemStack[] enderItems = ItemSerializer.deserializeItemStacks(serializedEnderChest);
                if (enderItems != null) {
                    player.getEnderChest().setContents(enderItems);
                }
            }

            if (serializedOffhand != null) {
                ItemStack offhand = ItemSerializer.deserializeItemStack(serializedOffhand);
                if (offhand != null) {
                    player.getInventory().setItemInOffHand(offhand);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying inventory for player " + player.getName(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Enhanced stats management with validation
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

    // Enhanced location management with validation
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

    public Location getPreviousLocation() {
        lock.readLock().lock();
        try {
            return LocationSerializer.deserialize(previousLocation);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Enhanced display name with caching and validation
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
                        displayName.append(rankEnum.tag).append(" ");
                    }
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid rank or chat tag for player " + username + ": " + e.getMessage());
            }

            // Add colored username based on alignment
            displayName.append(getColorByAlignment()).append(username);

            this.cachedDisplayName = ChatColor.translateAlternateColorCodes('&', displayName.toString());
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

    // Enhanced PvP tracking
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

    // Enhanced combat tracking
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

    // Enhanced achievement system
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

    // Enhanced quest system
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

    // Enhanced profession system
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

    // Enhanced moderation methods
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

    // Enhanced temporary data management
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

    public void clearTemporaryData() {
        temporaryData.clear();
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

    public int getGems() {
        lock.readLock().lock();
        try {
            return gems;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setGems(int gems) {
        lock.writeLock().lock();
        try {
            this.gems = Math.max(MIN_GEMS, Math.min(MAX_GEMS, gems));
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

    public boolean isToggled(String setting) {
        if (setting == null) return false;

        lock.readLock().lock();
        try {
            return toggleSettings.contains(setting);
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

    public List<String> getBuddies() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(buddies);
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

    public boolean isBanned() {
        lock.readLock().lock();
        try {
            return banned;
        } finally {
            lock.readLock().unlock();
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

    public long getBanExpiry() {
        lock.readLock().lock();
        try {
            return banExpiry;
        } finally {
            lock.readLock().unlock();
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

    // Additional getters for new fields
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

    public int getKillStreak() {
        lock.readLock().lock();
        try {
            return killStreak;
        } finally {
            lock.readLock().unlock();
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

    public int getPvpRating() {
        lock.readLock().lock();
        try {
            return pvpRating;
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

    public int getQuestPoints() {
        lock.readLock().lock();
        try {
            return questPoints;
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

    public int getQuestProgress() {
        lock.readLock().lock();
        try {
            return questProgress;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Profession getters
    public int getMiningLevel() { return pickaxeLevel; }
    public int getFishingLevel() { return fishingLevel; }
    public int getFarmingLevel() { return farmingLevel; }
    public int getWoodcuttingLevel() { return woodcuttingLevel; }

    public int getMiningXP() { return miningXp; }
    public int getFishingXP() { return fishingXp; }
    public int getFarmingXP() { return farmingXp; }
    public int getWoodcuttingXP() { return woodcuttingXp; }

    // Combat stat getters
    public long getDamageDealt() { return damageDealt; }
    public long getDamageTaken() { return damageTaken; }
    public long getDamageBlocked() { return damageBlocked; }
    public long getDamageDodged() { return damageDodged; }

    // Bank access methods
    public Set<String> getBankAuthorizedUsers() {
        lock.readLock().lock();
        try {
            return new HashSet<>(bankAuthorizedUsers);
        } finally {
            lock.readLock().unlock();
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

    public void setRespawnItems(ItemStack[] items) {
        this.respawnItems = items != null ? items.clone() : null;
    }

    public ItemStack[] getRespawnItems() {
        return respawnItems != null ? respawnItems.clone() : null;
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
                    ", gems=" + gems +
                    ", bankGems=" + bankGems +
                    ", online=" + isOnline() +
                    ", buddies=" + buddies.size() +
                    ", achievements=" + achievements.size() +
                    ", playtime=" + getTotalPlaytime() + "s" +
                    '}';
        } finally {
            lock.readLock().unlock();
        }
    }

    // Helper classes for serialization (unchanged from original)
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