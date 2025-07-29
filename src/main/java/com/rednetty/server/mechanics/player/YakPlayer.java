package com.rednetty.server.mechanics.player;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.player.moderation.Rank;
import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UPDATED YakPlayer with FIXED combat logout integration + comprehensive inventory management
 *
 * MAJOR UPDATES:
 * - Enhanced combat logout state management with proper enum transitions
 * - Improved inventory serialization for cross-server sync compatibility
 * - Better coordination with CombatLogoutMechanics and DeathMechanics
 * - Comprehensive respawn item handling for all death scenarios
 * - Bulletproof limbo loading support with combat logout awareness
 */
@Getter
@Setter
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

    @BsonId
    private final UUID uuid;

    // Basic identification
    @Expose
    @SerializedName("username")
    @BsonProperty("username")
    private String username;

    @Expose
    @SerializedName("last_login")
    @BsonProperty("last_login")
    private long lastLogin;

    @Expose
    @SerializedName("last_logout")
    @BsonProperty("last_logout")
    private long lastLogout;

    @Expose
    @SerializedName("first_join")
    @BsonProperty("first_join")
    private long firstJoin;

    @Expose
    @SerializedName("ip_address")
    @BsonProperty("ip_address")
    private String ipAddress;

    @Expose
    @SerializedName("total_playtime")
    @BsonProperty("total_playtime")
    private long totalPlaytime = 0;

    // Player progression and stats
    @Expose
    @SerializedName("level")
    @BsonProperty("level")
    private int level = 1;

    @Expose
    @SerializedName("exp")
    @BsonProperty("exp")
    private int exp = 0;

    @Expose
    @SerializedName("monster_kills")
    @BsonProperty("monster_kills")
    private int monsterKills = 0;

    @Expose
    @SerializedName("player_kills")
    @BsonProperty("player_kills")
    private int playerKills = 0;

    @Expose
    @SerializedName("deaths")
    @BsonProperty("deaths")
    private int deaths = 0;

    @Expose
    @SerializedName("ore_mined")
    @BsonProperty("ore_mined")
    private int oreMined = 0;

    @Expose
    @SerializedName("fish_caught")
    @BsonProperty("fish_caught")
    private int fishCaught = 0;

    @Expose
    @SerializedName("blocks_broken")
    @BsonProperty("blocks_broken")
    private int blocksBroken = 0;

    @Expose
    @SerializedName("distance_traveled")
    @BsonProperty("distance_traveled")
    private double distanceTraveled = 0.0;

    // Economy data - ONLY bank balance, no virtual player balance
    @Expose
    @SerializedName("bank_gems")
    @BsonProperty("bank_gems")
    private int bankGems = 0;

    @Expose
    @SerializedName("elite_shards")
    @BsonProperty("elite_shards")
    private int eliteShards = 0;

    // Bank system
    @Expose
    @SerializedName("bank_pages")
    @BsonProperty("bank_pages")
    private int bankPages = 1;

    @Expose
    @SerializedName("bank_inventory")
    @BsonProperty("bank_inventory")
    private final Map<Integer, String> serializedBankItems = new ConcurrentHashMap<>();

    @Expose
    @SerializedName("bank_authorized_users")
    @BsonProperty("bank_authorized_users")
    private final Set<String> bankAuthorizedUsers = ConcurrentHashMap.newKeySet();

    @Expose
    @SerializedName("bank_access_log")
    @BsonProperty("bank_access_log")
    private final List<String> bankAccessLog = new ArrayList<>();

    // Alignment data
    @Expose
    @SerializedName("alignment")
    @BsonProperty("alignment")
    private String alignment = "LAWFUL";

    @Expose
    @SerializedName("chaotic_time")
    @BsonProperty("chaotic_time")
    private long chaoticTime = 0;

    @Expose
    @SerializedName("neutral_time")
    @BsonProperty("neutral_time")
    private long neutralTime = 0;

    @Expose
    @SerializedName("alignment_changes")
    @BsonProperty("alignment_changes")
    private int alignmentChanges = 0;

    // Moderation data
    @Expose
    @SerializedName("rank")
    @BsonProperty("rank")
    private String rank = "DEFAULT";

    @Expose
    @SerializedName("banned")
    @BsonProperty("banned")
    private boolean banned = false;

    @Expose
    @SerializedName("ban_reason")
    @BsonProperty("ban_reason")
    private String banReason = "";

    @Expose
    @SerializedName("ban_expiry")
    @BsonProperty("ban_expiry")
    private long banExpiry = 0;

    @Expose
    @SerializedName("muted")
    @BsonProperty("muted")
    private int muteTime = 0;

    @Expose
    @SerializedName("warnings")
    @BsonProperty("warnings")
    private int warnings = 0;

    @Expose
    @SerializedName("last_warning")
    @BsonProperty("last_warning")
    private long lastWarning = 0;

    // Chat data
    @Expose
    @SerializedName("chat_tag")
    @BsonProperty("chat_tag")
    private String chatTag = "DEFAULT";

    @Expose
    @SerializedName("unlocked_chat_tags")
    @BsonProperty("unlocked_chat_tags")
    private Set<String> unlockedChatTags = ConcurrentHashMap.newKeySet();

    @Expose
    @SerializedName("chat_color")
    @BsonProperty("chat_color")
    private String chatColor = "WHITE";

    // Mount and Guild data
    @Expose
    @SerializedName("horse_tier")
    @BsonProperty("horse_tier")
    private int horseTier = 0;

    @Expose
    @SerializedName("horse_name")
    @BsonProperty("horse_name")
    private String horseName = "";

    @Expose
    @SerializedName("guild_name")
    @BsonProperty("guild_name")
    private String guildName = "";

    @Expose
    @SerializedName("guild_rank")
    @BsonProperty("guild_rank")
    private String guildRank = "";

    @Expose
    @SerializedName("guild_contribution")
    @BsonProperty("guild_contribution")
    private int guildContribution = 0;

    // Player preferences
    @Expose
    @SerializedName("toggle_settings")
    @BsonProperty("toggle_settings")
    private final Set<String> toggleSettings = ConcurrentHashMap.newKeySet();

    @Expose
    @SerializedName("notification_settings")
    @BsonProperty("notification_settings")
    private final Map<String, Boolean> notificationSettings = new ConcurrentHashMap<>();

    // Quest system
    @Expose
    @SerializedName("current_quest")
    @BsonProperty("current_quest")
    private String currentQuest = "";

    @Expose
    @SerializedName("quest_progress")
    @BsonProperty("quest_progress")
    private int questProgress = 0;

    @Expose
    @SerializedName("completed_quests")
    @BsonProperty("completed_quests")
    private final Set<String> completedQuests = ConcurrentHashMap.newKeySet();

    @Expose
    @SerializedName("quest_points")
    @BsonProperty("quest_points")
    private int questPoints = 0;

    @Expose
    @SerializedName("daily_quests_completed")
    @BsonProperty("daily_quests_completed")
    private int dailyQuestsCompleted = 0;

    @Expose
    @SerializedName("last_daily_quest_reset")
    @BsonProperty("last_daily_quest_reset")
    private long lastDailyQuestReset = 0;

    // Profession data
    @Expose
    @SerializedName("pickaxe_level")
    @BsonProperty("pickaxe_level")
    private int pickaxeLevel = 0;

    @Expose
    @SerializedName("fishing_level")
    @BsonProperty("fishing_level")
    private int fishingLevel = 0;

    @Expose
    @SerializedName("mining_xp")
    @BsonProperty("mining_xp")
    private int miningXp = 0;

    @Expose
    @SerializedName("fishing_xp")
    @BsonProperty("fishing_xp")
    private int fishingXp = 0;

    @Expose
    @SerializedName("farming_level")
    @BsonProperty("farming_level")
    private int farmingLevel = 0;

    @Expose
    @SerializedName("farming_xp")
    @BsonProperty("farming_xp")
    private int farmingXp = 0;

    @Expose
    @SerializedName("woodcutting_level")
    @BsonProperty("woodcutting_level")
    private int woodcuttingLevel = 0;

    @Expose
    @SerializedName("woodcutting_xp")
    @BsonProperty("woodcutting_xp")
    private int woodcuttingXp = 0;

    // PvP stats
    @Expose
    @SerializedName("t1_kills")
    @BsonProperty("t1_kills")
    private int t1Kills = 0;

    @Expose
    @SerializedName("t2_kills")
    @BsonProperty("t2_kills")
    private int t2Kills = 0;

    @Expose
    @SerializedName("t3_kills")
    @BsonProperty("t3_kills")
    private int t3Kills = 0;

    @Expose
    @SerializedName("t4_kills")
    @BsonProperty("t4_kills")
    private int t4Kills = 0;

    @Expose
    @SerializedName("t5_kills")
    @BsonProperty("t5_kills")
    private int t5Kills = 0;

    @Expose
    @SerializedName("t6_kills")
    @BsonProperty("t6_kills")
    private int t6Kills = 0;

    @Expose
    @SerializedName("kill_streak")
    @BsonProperty("kill_streak")
    private int killStreak = 0;

    @Expose
    @SerializedName("best_kill_streak")
    @BsonProperty("best_kill_streak")
    private int bestKillStreak = 0;

    @Expose
    @SerializedName("pvp_rating")
    @BsonProperty("pvp_rating")
    private int pvpRating = 1000;

    // World Boss tracking
    @Expose
    @SerializedName("world_boss_damage")
    @BsonProperty("world_boss_damage")
    private final Map<String, Integer> worldBossDamage = new ConcurrentHashMap<>();

    @Expose
    @SerializedName("world_boss_kills")
    @BsonProperty("world_boss_kills")
    private final Map<String, Integer> worldBossKills = new ConcurrentHashMap<>();

    // Social settings
    @Expose
    @SerializedName("trade_disabled")
    @BsonProperty("trade_disabled")
    private boolean tradeDisabled = false;

    @Expose
    @SerializedName("buddies")
    @BsonProperty("buddies")
    private Set<String> buddies = ConcurrentHashMap.newKeySet();

    @Expose
    @SerializedName("blocked_players")
    @BsonProperty("blocked_players")
    private final Set<String> blockedPlayers = ConcurrentHashMap.newKeySet();

    @Expose
    @SerializedName("energy_disabled")
    @BsonProperty("energy_disabled")
    private boolean energyDisabled = false;

    // Location and state data
    @Expose
    @SerializedName("world")
    @BsonProperty("world")
    private String world;

    @Expose
    @SerializedName("location_x")
    @BsonProperty("location_x")
    private double locationX;

    @Expose
    @SerializedName("location_y")
    @BsonProperty("location_y")
    private double locationY;

    @Expose
    @SerializedName("location_z")
    @BsonProperty("location_z")
    private double locationZ;

    @Expose
    @SerializedName("location_yaw")
    @BsonProperty("location_yaw")
    private float locationYaw;

    @Expose
    @SerializedName("location_pitch")
    @BsonProperty("location_pitch")
    private float locationPitch;

    @Expose
    @SerializedName("previous_location")
    @BsonProperty("previous_location")
    private String previousLocation;

    // UPDATED: Enhanced inventory data for combat logout compatibility
    @Expose
    @SerializedName("inventory_contents")
    @BsonProperty("inventory_contents")
    private String serializedInventory;

    @Expose
    @SerializedName("armor_contents")
    @BsonProperty("armor_contents")
    private String serializedArmor;

    @Expose
    @SerializedName("ender_chest_contents")
    @BsonProperty("ender_chest_contents")
    private String serializedEnderChest;

    @Expose
    @SerializedName("offhand_item")
    @BsonProperty("offhand_item")
    private String serializedOffhand;

    // UPDATED: Enhanced respawn items storage for combat logout integration
    @Expose
    @SerializedName("respawn_items")
    @BsonProperty("respawn_items")
    private String serializedRespawnItems;

    @Expose
    @SerializedName("respawn_item_count")
    @BsonProperty("respawn_item_count")
    private int respawnItemCount = 0;

    @Expose
    @SerializedName("death_timestamp")
    @BsonProperty("death_timestamp")
    private long deathTimestamp = 0;

    // Player stats
    @Expose
    @SerializedName("health")
    @BsonProperty("health")
    private double health = 20.0;

    @Expose
    @SerializedName("max_health")
    @BsonProperty("max_health")
    private double maxHealth = 20.0;

    @Expose
    @SerializedName("food_level")
    @BsonProperty("food_level")
    private int foodLevel = 20;

    @Expose
    @SerializedName("saturation")
    @BsonProperty("saturation")
    private float saturation = 5.0f;

    @Expose
    @SerializedName("xp_level")
    @BsonProperty("xp_level")
    private int xpLevel = 0;

    @Expose
    @SerializedName("xp_progress")
    @BsonProperty("xp_progress")
    private float xpProgress = 0.0f;

    @Expose
    @SerializedName("total_experience")
    @BsonProperty("total_experience")
    private int totalExperience = 0;

    @Expose
    @SerializedName("bed_spawn_location")
    @BsonProperty("bed_spawn_location")
    private String bedSpawnLocation;

    @Expose
    @SerializedName("gamemode")
    @BsonProperty("gamemode")
    private String gameMode = "SURVIVAL";

    @Expose
    @SerializedName("active_potion_effects")
    @BsonProperty("active_potion_effects")
    private final List<String> activePotionEffects = new ArrayList<>();

    // Achievement and reward tracking
    @Expose
    @SerializedName("achievements")
    @BsonProperty("achievements")
    private final Set<String> achievements = ConcurrentHashMap.newKeySet();

    @Expose
    @SerializedName("achievement_points")
    @BsonProperty("achievement_points")
    private int achievementPoints = 0;

    @Expose
    @SerializedName("daily_rewards_claimed")
    @BsonProperty("daily_rewards_claimed")
    private final Set<String> dailyRewardsClaimed = ConcurrentHashMap.newKeySet();

    @Expose
    @SerializedName("last_daily_reward")
    @BsonProperty("last_daily_reward")
    private long lastDailyReward = 0;

    // Event participation tracking
    @Expose
    @SerializedName("events_participated")
    @BsonProperty("events_participated")
    private final Map<String, Integer> eventsParticipated = new ConcurrentHashMap<>();

    @Expose
    @SerializedName("event_wins")
    @BsonProperty("event_wins")
    private final Map<String, Integer> eventWins = new ConcurrentHashMap<>();

    // Combat statistics
    @Expose
    @SerializedName("damage_dealt")
    @BsonProperty("damage_dealt")
    private long damageDealt = 0;

    @Expose
    @SerializedName("damage_taken")
    @BsonProperty("damage_taken")
    private long damageTaken = 0;

    @Expose
    @SerializedName("damage_blocked")
    @BsonProperty("damage_blocked")
    private long damageBlocked = 0;

    @Expose
    @SerializedName("damage_dodged")
    @BsonProperty("damage_dodged")
    private long damageDodged = 0;

    // UPDATED: Enhanced combat logout state management
    @Expose
    @SerializedName("combat_logout_state")
    @BsonProperty("combat_logout_state")
    private CombatLogoutState combatLogoutState = CombatLogoutState.NONE;

    @Expose
    @SerializedName("combat_logout_timestamp")
    @BsonProperty("combat_logout_timestamp")
    private long combatLogoutTimestamp = 0;

    @Expose
    @SerializedName("combat_logout_alignment")
    @BsonProperty("combat_logout_alignment")
    private String combatLogoutAlignment = null;

    // Non-serialized transient fields
    private transient Player bukkitPlayer;
    private transient boolean inCombat = false;
    private transient long lastCombatTime = 0;
    private transient final Map<String, Object> temporaryData = new ConcurrentHashMap<>();
    private transient long sessionStartTime;
    private transient final AtomicBoolean inventoryBeingApplied = new AtomicBoolean(false);

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

        // Default toggle settings -  to handle unknown toggles
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

    // ========================================
    // UPDATED COMBAT LOGOUT STATE MANAGEMENT
    // ========================================

    /**
     * UPDATED Combat logout state enum with proper transitions
     */
    public enum CombatLogoutState {
        NONE,        // No combat logout
        PROCESSING,  // Currently processing combat logout (items being handled)
        PROCESSED,   // Combat logout processed (items handled, waiting for rejoin)
        COMPLETED    // Combat logout completed (player respawned, cleanup done)
    }


    /**
     * CRITICAL FIX: Set combat logout state with improved validation and logging
     *
     * Replace the existing setCombatLogoutState method with this version
     */
    public void setCombatLogoutState(CombatLogoutState newState) {
        CombatLogoutState oldState = this.combatLogoutState;

        if (isValidCombatLogoutStateTransition(oldState, newState)) {
            this.combatLogoutState = newState;
            this.combatLogoutTimestamp = System.currentTimeMillis();

            // Store alignment when processing starts
            if (newState == CombatLogoutState.PROCESSING) {
                this.combatLogoutAlignment = this.alignment;
            }

            // Clear alignment reference when completed or reset to none
            if (newState == CombatLogoutState.NONE) {
                this.combatLogoutAlignment = null;
            }

            logger.info("Combat logout state transition for " + username + ": " + oldState + " -> " + newState);
        } else {
            // Log warning but allow the transition anyway for system stability
            logger.warning("Invalid combat logout state transition for " + username + ": " + oldState + " -> " + newState);

            // CRITICAL FIX: Allow the transition anyway but log it as forced
            this.combatLogoutState = newState;
            this.combatLogoutTimestamp = System.currentTimeMillis();

            // Handle alignment appropriately even for forced transitions
            if (newState == CombatLogoutState.PROCESSING) {
                this.combatLogoutAlignment = this.alignment;
            } else if (newState == CombatLogoutState.NONE) {
                this.combatLogoutAlignment = null;
            }

            logger.info("Forced combat logout state transition for " + username + ": " + oldState + " -> " + newState);
        }
    }
    /**
     * CRITICAL FIX: Validate combat logout state transitions (more permissive to handle edge cases)
     *
     * Replace the existing isValidCombatLogoutStateTransition method with this version
     */
    private boolean isValidCombatLogoutStateTransition(CombatLogoutState from, CombatLogoutState to) {
        if (from == null) from = CombatLogoutState.NONE;
        if (to == null) to = CombatLogoutState.NONE;

        // More permissive transitions to handle database loading and rejoin edge cases
        switch (from) {
            case NONE:
                // From NONE, allow most transitions for fresh combat logouts and database loading
                return to == CombatLogoutState.PROCESSING ||
                        to == CombatLogoutState.PROCESSED ||
                        to == CombatLogoutState.COMPLETED ||  // Allow for database loading edge cases
                        to == CombatLogoutState.NONE;         // Allow staying in NONE

            case PROCESSING:
                // From PROCESSING, can go to PROCESSED, COMPLETED, or back to NONE (on error)
                return to == CombatLogoutState.PROCESSED ||
                        to == CombatLogoutState.NONE ||
                        to == CombatLogoutState.COMPLETED;

            case PROCESSED:
                // From PROCESSED, can go to COMPLETED (on rejoin) or back to NONE (cleanup)
                return to == CombatLogoutState.COMPLETED ||
                        to == CombatLogoutState.NONE;

            case COMPLETED:
                // From COMPLETED, should go back to NONE (normal quit after rejoin)
                // Also allow staying in COMPLETED for multiple rejoins before state reset
                return to == CombatLogoutState.NONE ||
                        to == CombatLogoutState.COMPLETED;

            default:
                return true; // Allow any transition as fallback for unknown states
        }
    }

    /**
     * WORKING: Check if player is in any combat logout state
     */
    public boolean isInCombatLogoutState() {
        return combatLogoutState != null && combatLogoutState != CombatLogoutState.NONE;
    }

    /**
     * WORKING: Check if combat logout processing is complete
     */
    public boolean isCombatLogoutProcessed() {
        return combatLogoutState == CombatLogoutState.PROCESSED || combatLogoutState == CombatLogoutState.COMPLETED;
    }

    /**
     * WORKING: Get time since combat logout started
     */
    public long getCombatLogoutAge() {
        if (combatLogoutTimestamp <= 0) {
            return 0;
        }
        return System.currentTimeMillis() - combatLogoutTimestamp;
    }

    /**
     * WORKING: Get combat logout alignment (at time of logout)
     */
    public String getCombatLogoutAlignment() {
        return combatLogoutAlignment != null ? combatLogoutAlignment : alignment;
    }

    // ========================================
    // UPDATED INVENTORY SYSTEM WITH COMBAT LOGOUT SUPPORT
    // ========================================

    /**
     * UPDATED: Update inventory from player - enhanced for combat logout processing
     */
    public void updateInventory(Player player) {
        if (player == null) return;

        // Update location when updating inventory
        updateLocation(player.getLocation());

        try {
            // Get current inventory data
            ItemStack[] inventoryContents = player.getInventory().getContents();
            ItemStack[] armorContents = player.getInventory().getArmorContents();
            ItemStack[] enderContents = player.getEnderChest().getContents();
            ItemStack offhandItem = player.getInventory().getItemInOffHand();

            // Enhanced serialization with combat logout awareness
            this.serializedInventory = ItemSerializer.serializeItemStacksWithValidation(inventoryContents, "inventory");
            this.serializedArmor = ItemSerializer.serializeItemStacksWithValidation(armorContents, "armor");
            this.serializedEnderChest = ItemSerializer.serializeItemStacksWithValidation(enderContents, "enderchest");
            this.serializedOffhand = ItemSerializer.serializeItemStackWithValidation(offhandItem, "offhand");

            logger.fine("Updated inventory and location for player: " + player.getName() +
                    (isInCombatLogoutState() ? " (combat logout state: " + combatLogoutState + ")" : ""));

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating inventory for player " + player.getName(), e);
        }
    }

    /**
     * UPDATED: Apply inventory to player - enhanced with combat logout state awareness
     */
    public void applyInventory(Player player) {
        if (player == null) return;

        // Prevent multiple simultaneous applications
        if (!inventoryBeingApplied.compareAndSet(false, true)) {
            logger.warning("Inventory application already in progress for: " + player.getName());
            return;
        }

        try {
            boolean isCombatLogoutRejoin = isCombatLogoutProcessed();
            logger.info("Applying inventory to player: " + player.getName() +
                    (isCombatLogoutRejoin ? " (combat logout processed inventory)" : " (normal inventory)"));

            // Step 1: Clear inventory ONCE only
            clearInventoryForLoading(player);

            // Step 2: Apply inventory sequentially with proper validation
            applyInventorySequentially(player, isCombatLogoutRejoin);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying inventory for player " + player.getName(), e);
        } finally {
            // Always release the lock
            inventoryBeingApplied.set(false);
        }
    }

    /**
     * Proper inventory clearing for loading (not aggressive)
     */
    private void clearInventoryForLoading(Player player) {
        try {
            // Single clear without excessive force
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getEnderChest().clear();
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.updateInventory();

            logger.fine("Inventory cleared for loading: " + player.getName());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error clearing inventory for loading", e);
        }
    }

    /**
     * UPDATED: Apply inventory sequentially with combat logout awareness
     */
    private void applyInventorySequentially(Player player, boolean isCombatLogoutRejoin) {
        // Apply main inventory immediately
        applyMainInventory(player, isCombatLogoutRejoin);

        // Schedule armor application with small delay
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (!player.isOnline()) return;
            applyArmor(player, isCombatLogoutRejoin);

            // Schedule ender chest and offhand with another small delay
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (!player.isOnline()) return;
                applyEnderChestAndOffhand(player, isCombatLogoutRejoin);

                // Final validation
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    validateInventoryApplication(player, isCombatLogoutRejoin);
                }, 2L);
            }, 2L);
        }, 2L);
    }

    /**
     * UPDATED: Apply main inventory with combat logout awareness
     */
    private void applyMainInventory(Player player, boolean isCombatLogoutRejoin) {
        if (serializedInventory == null || serializedInventory.isEmpty()) {
            logger.fine("No main inventory data to apply for " + player.getName() +
                    (isCombatLogoutRejoin ? " (combat logout)" : ""));
            return;
        }

        try {
            ItemStack[] contents = ItemSerializer.repairInventoryData(serializedInventory, player.getInventory().getSize());
            if (contents != null) {
                // Apply items without clearing again
                int itemsApplied = 0;
                for (int i = 0; i < contents.length && i < player.getInventory().getSize(); i++) {
                    if (contents[i] != null && contents[i].getType() != Material.AIR) {
                        player.getInventory().setItem(i, contents[i].clone());
                        itemsApplied++;
                    }
                }
                player.updateInventory();
                logger.info("Applied main inventory for " + player.getName() + " (" + itemsApplied + " items)" +
                        (isCombatLogoutRejoin ? " - combat logout processed items" : ""));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying main inventory for " + player.getName(), e);
        }
    }

    /**
     * UPDATED: Apply armor with combat logout awareness
     */
    private void applyArmor(Player player, boolean isCombatLogoutRejoin) {
        if (serializedArmor == null || serializedArmor.isEmpty()) {
            logger.fine("No armor data to apply for " + player.getName() +
                    (isCombatLogoutRejoin ? " (combat logout)" : ""));
            return;
        }

        try {
            ItemStack[] armor = ItemSerializer.repairInventoryData(serializedArmor, 4);
            if (armor != null) {
                int armorApplied = 0;

                // Correct armor slot mapping
                // Bukkit armor array order: [Boots, Leggings, Chestplate, Helmet]

                // Index 0 = Boots
                if (armor.length > 0 && armor[0] != null && armor[0].getType() != Material.AIR) {
                    player.getInventory().setBoots(armor[0].clone());
                    armorApplied++;
                }

                // Index 1 = Leggings
                if (armor.length > 1 && armor[1] != null && armor[1].getType() != Material.AIR) {
                    player.getInventory().setLeggings(armor[1].clone());
                    armorApplied++;
                }

                // Index 2 = Chestplate
                if (armor.length > 2 && armor[2] != null && armor[2].getType() != Material.AIR) {
                    player.getInventory().setChestplate(armor[2].clone());
                    armorApplied++;
                }

                // Index 3 = Helmet
                if (armor.length > 3 && armor[3] != null && armor[3].getType() != Material.AIR) {
                    player.getInventory().setHelmet(armor[3].clone());
                    armorApplied++;
                }

                player.updateInventory();
                logger.info("Applied armor for " + player.getName() + " (" + armorApplied + " pieces)" +
                        (isCombatLogoutRejoin ? " - combat logout processed armor" : ""));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying armor for " + player.getName(), e);
        }
    }

    /**
     * UPDATED: Apply ender chest and offhand with combat logout awareness
     */
    private void applyEnderChestAndOffhand(Player player, boolean isCombatLogoutRejoin) {
        try {
            int enderItemsApplied = 0;

            // Apply ender chest
            if (serializedEnderChest != null && !serializedEnderChest.isEmpty()) {
                ItemStack[] enderContents = ItemSerializer.repairInventoryData(serializedEnderChest, 27);
                if (enderContents != null) {
                    // Apply with validation
                    for (int i = 0; i < enderContents.length && i < 27; i++) {
                        if (enderContents[i] != null && enderContents[i].getType() != Material.AIR) {
                            player.getEnderChest().setItem(i, enderContents[i].clone());
                            enderItemsApplied++;
                        }
                    }
                    logger.fine("Applied ender chest for " + player.getName() + " (" + enderItemsApplied + " items)" +
                            (isCombatLogoutRejoin ? " (combat logout)" : ""));
                }
            }

            // Apply offhand
            if (serializedOffhand != null && !serializedOffhand.isEmpty()) {
                ItemStack offhandItem = ItemSerializer.deserializeItemStackWithValidation(serializedOffhand);
                if (offhandItem != null && offhandItem.getType() != Material.AIR) {
                    player.getInventory().setItemInOffHand(offhandItem.clone());
                    logger.fine("Applied offhand for " + player.getName() +
                            (isCombatLogoutRejoin ? " (combat logout)" : ""));
                } else {
                    player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                }
            }

            player.updateInventory();
            logger.info("Applied ender chest and offhand for " + player.getName() +
                    (isCombatLogoutRejoin ? " (combat logout processed)" : ""));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying ender chest/offhand for " + player.getName(), e);
        }
    }

    /**
     * UPDATED: Final validation with combat logout awareness
     */
    private void validateInventoryApplication(Player player, boolean isCombatLogoutRejoin) {
        try {
            // Count items in inventory
            int itemCount = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                    itemCount++;
                }
            }

            // Count armor pieces
            int armorCount = 0;
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null && armor.getType() != Material.AIR && armor.getAmount() > 0) {
                    armorCount++;
                }
            }

            logger.info("Inventory application completed for " + player.getName() +
                    " - Items: " + itemCount + ", Armor: " + armorCount +
                    (isCombatLogoutRejoin ? " (combat logout processed inventory)" : " (normal inventory)"));

            // Final force update
            player.updateInventory();

            // Check if we have expected data but no items (improved check)
            boolean hasSerializedData = hasValidSerializedData();

            if (hasSerializedData && itemCount == 0 && armorCount == 0) {
                if (isCombatLogoutRejoin) {
                    logger.info("No items applied for combat logout rejoin " + player.getName() +
                            " - this is expected if all items were dropped");
                } else {
                    logger.warning("No items applied despite having serialized data for " + player.getName() +
                            " - this may be normal for empty inventories");
                }
            } else if (hasSerializedData) {
                logger.info("Successfully applied inventory data for " + player.getName() +
                        (isCombatLogoutRejoin ? " (combat logout processed)" : ""));
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in final inventory validation for " + player.getName(), e);
        }
    }

    /**
     * Check if player has valid serialized data
     */
    private boolean hasValidSerializedData() {
        return (serializedInventory != null && !serializedInventory.isEmpty() && !serializedInventory.equals("rO0ABXVyABNbTG9yZy5idWtraXQuaXRlbXN0YWNrO2OcFcNr7CtlAgAAeHAAAABFdAAA")) ||
                (serializedArmor != null && !serializedArmor.isEmpty() && !serializedArmor.equals("rO0ABXVyABNbTG9yZy5idWtrayXRlbTt0YWNrO2OcFcNr7CtlAgAAeHAAAABEdAAA"));
    }

    // ========================================
    // UPDATED RESPAWN ITEMS MANAGEMENT WITH COMBAT LOGOUT INTEGRATION
    // ========================================

    /**
     * UPDATED: Set respawn items with combat logout state awareness
     */
    public boolean setRespawnItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            clearRespawnItems();
            return true;
        }

        // Filter out null and air items
        List<ItemStack> validItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                validItems.add(item.clone());
            }
        }

        if (validItems.isEmpty()) {
            clearRespawnItems();
            return true;
        }

        try {
            String serialized = ItemSerializer.serializeItemStacksWithValidation(validItems.toArray(new ItemStack[0]), "respawn");
            if (serialized != null) {
                this.serializedRespawnItems = serialized;
                this.respawnItemCount = validItems.size();
                this.deathTimestamp = System.currentTimeMillis();

                String logMessage = "Stored " + validItems.size() + " respawn items for " + username;
                if (isInCombatLogoutState()) {
                    logMessage += " (combat logout state: " + combatLogoutState + ")";
                }
                logger.info(logMessage);

                return true;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to serialize respawn items for " + username, e);
        }

        return false;
    }

    /**
     * Get respawn items
     */
    public List<ItemStack> getRespawnItems() {
        if (serializedRespawnItems == null || serializedRespawnItems.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            ItemStack[] items = ItemSerializer.deserializeItemStacksWithValidation(serializedRespawnItems);
            if (items != null) {
                List<ItemStack> itemList = new ArrayList<>();
                for (ItemStack item : items) {
                    if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                        itemList.add(item.clone());
                    }
                }
                return itemList;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error deserializing respawn items for " + username, e);
        }

        return new ArrayList<>();
    }

    /**
     * Check if player has pending respawn items
     */
    public boolean hasRespawnItems() {
        return serializedRespawnItems != null && !serializedRespawnItems.isEmpty() && respawnItemCount > 0;
    }

    /**
     * UPDATED: Clear respawn items with combat logout state logging
     */
    public void clearRespawnItems() {
        this.serializedRespawnItems = null;
        this.respawnItemCount = 0;
        this.deathTimestamp = 0;

        String logMessage = "Cleared respawn items for " + username;
        if (isInCombatLogoutState()) {
            logMessage += " (combat logout state: " + combatLogoutState + ")";
        }
        logger.fine(logMessage);
    }

    // ========================================
    // CONNECTION MANAGEMENT
    // ========================================

    /**
     * Connect player
     */
    public void connect(Player player) {
        this.bukkitPlayer = player;
        this.username = validateUsername(player.getName());
        this.lastLogin = Instant.now().getEpochSecond();
        this.sessionStartTime = System.currentTimeMillis();

        if (player.getAddress() != null) {
            this.ipAddress = player.getAddress().getAddress().getHostAddress();
        }

        // Ensure player starts with proper health/food values
        if (health <= 0) {
            health = 20.0;
        }
        if (foodLevel <= 0) {
            foodLevel = 20;
        }

        // Set energy level display
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            player.setExp(1.0f);
            player.setLevel(100);
        });

        logger.info("Connected player: " + player.getName() +
                (isInCombatLogoutState() ? " (combat logout state: " + combatLogoutState + ")" : ""));
    }

    /**
     * UPDATED: Disconnect player with combat logout state preservation
     */
    public void disconnect() {
        this.lastLogout = Instant.now().getEpochSecond();

        // Update location before disconnect
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            updateLocation(bukkitPlayer.getLocation());
        }

        // Update total playtime
        if (sessionStartTime > 0) {
            long sessionDuration = System.currentTimeMillis() - sessionStartTime;
            this.totalPlaytime += sessionDuration / 1000; // Convert to seconds
        }

        // Clear bukkit player reference but preserve combat logout state
        String logMessage = "Disconnected player: " + username;
        if (isInCombatLogoutState()) {
            logMessage += " (preserving combat logout state: " + combatLogoutState + ")";
        }

        this.bukkitPlayer = null;
        logger.fine(logMessage);
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Check if player is online
     */
    public boolean isOnline() {
        return bukkitPlayer != null && bukkitPlayer.isOnline();
    }

    public boolean isBuddy(String buddyName) {
        if (buddyName == null) return false;
        return buddies.contains(buddyName.toLowerCase().trim());
    }

    /**
     * UPDATED: Clear temporary data while preserving combat logout state
     */
    public void clearTemporaryData() {
        CombatLogoutState savedState = combatLogoutState;
        long savedTimestamp = combatLogoutTimestamp;
        String savedAlignment = combatLogoutAlignment;

        temporaryData.clear();

        // Restore combat logout state
        combatLogoutState = savedState;
        combatLogoutTimestamp = savedTimestamp;
        combatLogoutAlignment = savedAlignment;
    }

    /**
     * Update location - improved validation
     */
    public void updateLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            logger.fine("Cannot update location - null location or world for " + username);
            return;
        }

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

            logger.fine("Updated location for " + username + " to " + world + " (" +
                    String.format("%.1f, %.1f, %.1f", locationX, locationY, locationZ) + ")" +
                    (isInCombatLogoutState() ? " (combat logout state: " + combatLogoutState + ")" : ""));

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating location for " + username, e);
        }
    }

    /**
     * Get location
     */
    public Location getLocation() {
        if (world == null) return null;

        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) return null;

        return new Location(bukkitWorld, locationX, locationY, locationZ, locationYaw, locationPitch);
    }

    /**
     * Update stats from player
     */
    public void updateStats(Player player) {
        if (player == null) return;

        this.health = Math.max(0, Math.min(player.getMaxHealth(), player.getHealth()));
        this.maxHealth = Math.max(1, player.getMaxHealth());
        this.foodLevel = Math.max(0, Math.min(20, player.getFoodLevel()));
        this.saturation = Math.max(0, player.getSaturation());
        this.xpLevel = Math.max(0, player.getLevel());
        this.xpProgress = Math.max(0, Math.min(1, player.getExp()));
        this.totalExperience = Math.max(0, player.getTotalExperience());
        this.gameMode = player.getGameMode().name();

        updatePotionEffects(player);
    }

    /**
     * Apply stats to player with option to skip game mode (SMOOTH LIMBO FIX)
     */
    public void applyStats(Player player, boolean skipGameMode) {
        if (player == null) return;

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

            // Apply game mode ONLY if not skipping (not in limbo)
            if (!skipGameMode) {
                try {
                    GameMode mode = GameMode.valueOf(gameMode);
                    if (player.getGameMode() != mode) {
                        player.setGameMode(mode);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid game mode for player " + username + ": " + gameMode);
                }
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
        }
    }

    /**
     * Apply stats to player (original method - applies game mode)
     */
    public void applyStats(Player player) {
        applyStats(player, false);
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

    public boolean addBuddy(String buddyName) {
        if (buddyName == null || buddyName.trim().isEmpty()) {
            return false;
        }

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
            sendMessageIfOnline(ChatColor.GREEN + "Successfully added " + buddyName + " as a buddy!");

            // Play sound if online
            Player player = getBukkitPlayer();
            if (player != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            }

            return true;
        }
        return false;
    }

    /**
     * Get formatted display name - NO CACHING for cross-server sync
     */
    public String getFormattedDisplayName() {
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

        return displayName.toString();
    }

    private ChatColor getColorByAlignment() {
        switch (alignment) {
            case "LAWFUL": return ChatColor.GRAY;
            case "NEUTRAL": return ChatColor.YELLOW;
            case "CHAOTIC": return ChatColor.RED;
            default: return ChatColor.GRAY;
        }
    }

    // ========================================
    // CUSTOM SETTERS WITH VALIDATION
    // ========================================

    public void setUsername(String username) {
        this.username = validateUsername(username);
    }

    public void setBankGems(int bankGems) {
        this.bankGems = Math.max(MIN_GEMS, Math.min(MAX_GEMS, bankGems));
    }

    public void setLevel(int level) {
        this.level = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    public void setRank(String rank) {
        this.rank = rank != null ? rank : "DEFAULT";
    }

    public void setAlignment(String alignment) {
        this.alignment = alignment != null ? alignment : "LAWFUL";
    }

    public void setChatTag(String chatTag) {
        this.chatTag = chatTag != null ? chatTag : "DEFAULT";
    }

    public void setGuildName(String guildName) {
        this.guildName = guildName != null ? guildName : "";
    }

    // ========================================
    // HELPER METHODS FOR EXTERNAL COMPATIBILITY
    // ========================================

    /**
     * Check if player is in guild
     */
    public boolean isInGuild() {
        return guildName != null && !guildName.trim().isEmpty();
    }

    /**
     * Check if player is in combat
     */
    public boolean isInCombat() {
        return inCombat && (System.currentTimeMillis() - lastCombatTime) < 15000;
    }

    /**
     * Set combat status
     */
    public void setInCombat(boolean inCombat) {
        this.inCombat = inCombat;
        if (inCombat) {
            this.lastCombatTime = System.currentTimeMillis();
        }
    }

    /**
     * Get session playtime
     */
    public long getSessionPlaytime() {
        if (sessionStartTime > 0) {
            return System.currentTimeMillis() - sessionStartTime;
        }
        return 0;
    }

    /**
     * Format playtime as readable string
     */
    public String getFormattedTotalPlaytime() {
        long totalSeconds = totalPlaytime;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    public void unlockChatTag(ChatTag tag) {
        if (tag == null) return;

        unlockedChatTags.add(tag.name());

        sendMessageIfOnline(ChatColor.GREEN + "Unlocked chat tag: " + tag.getTag());
    }

    /**
     * UPDATED: Get time since death with combat logout awareness
     */
    public String getTimeSinceDeath() {
        if (deathTimestamp <= 0) {
            return "Never";
        }

        long timeDiff = System.currentTimeMillis() - deathTimestamp;
        long seconds = timeDiff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        String timeString;
        if (days > 0) {
            timeString = days + " days, " + (hours % 24) + " hours ago";
        } else if (hours > 0) {
            timeString = hours + " hours, " + (minutes % 60) + " minutes ago";
        } else if (minutes > 0) {
            timeString = minutes + " minutes, " + (seconds % 60) + " seconds ago";
        } else {
            timeString = seconds + " seconds ago";
        }

        if (isInCombatLogoutState()) {
            timeString += " (combat logout: " + combatLogoutState + ")";
        }

        return timeString;
    }

    // ========================================
    // CONVENIENCE METHODS FOR EXTERNAL CLASSES
    // ========================================

    // Temporary data management
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

    // Bank management
    public Map<Integer, String> getAllSerializedBankItems() {
        return new HashMap<>(serializedBankItems);
    }

    public String getSerializedBankItems(int page) {
        return serializedBankItems.get(page);
    }

    public void setSerializedBankItems(int page, String serializedData) {
        if (serializedData == null || serializedData.trim().isEmpty()) {
            serializedBankItems.remove(page);
        } else {
            serializedBankItems.put(page, serializedData);
        }
    }

    // Collection getters with defensive copying
    public Set<String> getBankAuthorizedUsers() {
        return new HashSet<>(bankAuthorizedUsers);
    }

    public List<String> getBankAccessLog() {
        return new ArrayList<>(bankAccessLog);
    }

    public Set<String> getUnlockedChatTags() {
        return new HashSet<>(unlockedChatTags);
    }

    public Map<String, Boolean> getNotificationSettings() {
        return new HashMap<>(notificationSettings);
    }

    public void setToggleSettings(Collection<String> settings) {
        toggleSettings.clear();
        if (settings != null) {
            toggleSettings.addAll(settings);
        }
    }

    public Set<String> getCompletedQuests() {
        return new HashSet<>(completedQuests);
    }

    /**
     * Toggle handling - handles unknown toggles gracefully
     */
    public boolean isToggled(String setting) {
        if (setting == null) return false;

        // Handle known invalid toggles
        if ("God Mode Disabled".equals(setting)) {
            logger.fine("Attempted to check invalid toggle: " + setting + " for " + username);
            return false;
        }

        return toggleSettings.contains(setting);
    }

    public Map<String, Integer> getWorldBossDamage() {
        return new HashMap<>(worldBossDamage);
    }

    public Set<String> getBuddies() {
        return new HashSet<>(buddies);
    }

    public Set<String> getBlockedPlayers() {
        return new HashSet<>(blockedPlayers);
    }

    public List<String> getActivePotionEffects() {
        return new ArrayList<>(activePotionEffects);
    }

    public Set<String> getAchievements() {
        return new HashSet<>(achievements);
    }

    public Set<String> getDailyRewardsClaimed() {
        return new HashSet<>(dailyRewardsClaimed);
    }

    public Map<String, Integer> getEventsParticipated() {
        return new HashMap<>(eventsParticipated);
    }

    public Map<String, Integer> getEventWins() {
        return new HashMap<>(eventWins);
    }

    // Simple methods for external classes
    public boolean hasToggleSetting(String setting) {
        return setting != null && toggleSettings.contains(setting);
    }

    public boolean getNotificationSetting(String type) {
        return type != null && notificationSettings.getOrDefault(type, true);
    }

    public void setNotificationSetting(String type, boolean enabled) {
        if (type != null) {
            notificationSettings.put(type, enabled);
        }
    }

    /**
     * Toggle setting - handles unknown toggles gracefully
     */
    public boolean toggleSetting(String setting) {
        if (setting == null || setting.trim().isEmpty()) {
            return false;
        }

        // Handle known invalid toggles
        if ("God Mode Disabled".equals(setting)) {
            logger.warning("Attempted to toggle invalid setting: " + setting + " for " + username);
            return false;
        }

        boolean wasToggled = toggleSettings.contains(setting);
        if (wasToggled) {
            toggleSettings.remove(setting);
        } else {
            toggleSettings.add(setting);
        }

        return !wasToggled;
    }

    private void sendMessageIfOnline(String message) {
        Player player = getBukkitPlayer();
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    public UUID getUUID() {
        return uuid;
    }

    public void setUnlockedChatTags(List<String> tags) {
        unlockedChatTags = new HashSet<>(tags);
    }

    public boolean removeBuddy(String buddyName) {
        if (buddyName == null) return false;

        boolean removed = buddies.remove(buddyName.toLowerCase().trim());
        if (removed) {
            sendMessageIfOnline(ChatColor.YELLOW + "Removed " + buddyName + " from your buddy list.");

            // Play sound if online
            Player player = getBukkitPlayer();
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
            }
        }
        return removed;
    }

    public void setToggleSettings(HashSet<String> toggleSettings) {
        // Implementation handled by setToggleSettings(Collection<String>)
    }

    public void setBuddies(List<String> buddies) {
        this.buddies = new HashSet<>(buddies);
    }

    // Getter for toggle settings
    public Set<String> getToggleSettings() {
        return new HashSet<>(toggleSettings);
    }

    // ========================================
    // UPDATED UTILITY CLASSES WITH COMBAT LOGOUT SUPPORT
    // ========================================

    /**
     * UPDATED ItemSerializer with enhanced error handling and combat logout awareness
     */
    public static class ItemSerializer {
        private static final Logger logger = YakRealms.getInstance().getLogger();

        public static String serializeItemStacksWithValidation(ItemStack[] items, String context) {
            if (items == null) return null;

            try {
                // Pre-validate items to prevent serialization issues
                ItemStack[] validatedItems = new ItemStack[items.length];
                for (int i = 0; i < items.length; i++) {
                    ItemStack item = items[i];
                    if (item != null && item.getType() != Material.AIR) {
                        // Create a clean copy to avoid any transient issues
                        validatedItems[i] = new ItemStack(item);
                    } else {
                        validatedItems[i] = null;
                    }
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
                dataOutput.writeObject(validatedItems);
                dataOutput.close();

                String result = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                logger.fine("Serialized " + items.length + " items for context: " + context);
                return result;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error serializing items for context: " + context, e);
                return null;
            }
        }

        // Overloaded method for backward compatibility
        public static String serializeItemStacksWithValidation(ItemStack[] items) {
            return serializeItemStacksWithValidation(items, "unknown");
        }

        public static ItemStack[] deserializeItemStacksWithValidation(String data) {
            if (data == null || data.isEmpty()) {
                return new ItemStack[0];
            }

            // Try multiple deserialization methods
            ItemStack[] result = tryDeserializeDirectArray(data);
            if (result != null) return result;

            result = tryDeserializeLegacyFormat(data);
            if (result != null) return result;

            logger.warning("Failed to deserialize inventory data, returning empty array");
            return new ItemStack[0];
        }

        private static ItemStack[] tryDeserializeDirectArray(String data) {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                Object obj = dataInput.readObject();
                dataInput.close();
                if (obj instanceof ItemStack[]) {
                    ItemStack[] items = (ItemStack[]) obj;
                    // Validate deserialized items
                    for (int i = 0; i < items.length; i++) {
                        if (items[i] != null && items[i].getType() == Material.AIR) {
                            items[i] = null;
                        }
                    }
                    return items;
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        private static ItemStack[] tryDeserializeLegacyFormat(String data) {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                int length = dataInput.readInt();
                if (length < 0 || length > 1000) {
                    dataInput.close();
                    return null;
                }
                ItemStack[] items = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    try {
                        Object obj = dataInput.readObject();
                        if (obj instanceof ItemStack) {
                            ItemStack item = (ItemStack) obj;
                            if (item.getType() == Material.AIR) {
                                items[i] = null;
                            } else {
                                items[i] = item;
                            }
                        }
                    } catch (Exception e) {
                        items[i] = null;
                    }
                }
                dataInput.close();
                return items;
            } catch (Exception e) {
                return null;
            }
        }

        public static String serializeItemStackWithValidation(ItemStack item, String context) {
            if (item == null || item.getType() == Material.AIR) return null;
            return serializeItemStacksWithValidation(new ItemStack[]{item}, context);
        }

        // Overloaded method for backward compatibility
        public static String serializeItemStackWithValidation(ItemStack item) {
            return serializeItemStackWithValidation(item, "unknown");
        }

        public static ItemStack deserializeItemStackWithValidation(String data) {
            if (data == null || data.isEmpty()) return null;
            ItemStack[] items = deserializeItemStacksWithValidation(data);
            return (items != null && items.length > 0) ? items[0] : null;
        }

        public static ItemStack[] repairInventoryData(String data, int expectedSize) {
            if (data == null || data.isEmpty()) {
                return new ItemStack[expectedSize];
            }
            ItemStack[] items = deserializeItemStacksWithValidation(data);
            if (items.length != expectedSize) {
                ItemStack[] resized = new ItemStack[expectedSize];
                int copyLength = Math.min(items.length, expectedSize);
                System.arraycopy(items, 0, resized, 0, copyLength);
                return resized;
            }
            return items;
        }
    }

    private static class LocationSerializer {
        public static String serialize(Location location) {
            if (location == null || location.getWorld() == null) return null;
            return location.getWorld().getName() + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ() + ":" + location.getYaw() + ":" + location.getPitch();
        }

        public static Location deserialize(String data) {
            if (data == null || data.isEmpty()) return null;
            try {
                String[] parts = data.split(":");
                if (parts.length != 6) return null;
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) return null;
                return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static class PotionEffectSerializer {
        public static String serialize(PotionEffect effect) {
            if (effect == null) return null;
            return effect.getType().getKey().getKey() + ":" + effect.getAmplifier() + ":" + effect.getDuration() + ":" + effect.isAmbient() + ":" + effect.hasParticles() + ":" + effect.hasIcon();
        }

        public static PotionEffect deserialize(String data) {
            if (data == null || data.isEmpty()) return null;
            try {
                String[] parts = data.split(":");
                if (parts.length != 6) return null;
                PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(parts[0]));
                if (type == null) return null;
                return new PotionEffect(type, Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Boolean.parseBoolean(parts[3]), Boolean.parseBoolean(parts[4]), Boolean.parseBoolean(parts[5]));
            } catch (Exception e) {
                return null;
            }
        }
    }
}