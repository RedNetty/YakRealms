package com.rednetty.server.mechanics.player;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.player.moderation.Rank;
import com.rednetty.server.utils.text.TextUtil;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * YakPlayer - Player data model with inventory persistence
 * Compatible with Paper 1.21.7+
 */
@Getter
@Setter
public class YakPlayer {
    private static final Logger logger = Logger.getLogger(YakPlayer.class.getName());

    // Configuration constants with safer defaults
    private static final int MAX_BANK_PAGES = 10;
    private static final int MAX_BUDDIES = 50;
    private static final int MAX_AUTHORIZED_USERS = 10;
    private static final int MIN_GEMS = 0;
    private static final int MAX_GEMS = Integer.MAX_VALUE;
    private static final int MAX_LEVEL = 200;
    private static final int MIN_LEVEL = 1;
    private static final int MAX_USERNAME_LENGTH = 16;
    private static final double DEFAULT_HEALTH = 50.0;
    private static final double DEFAULT_MAX_HEALTH = 50.0;

    // Player identification
    @BsonId
    private final UUID uuid;

    @Expose @SerializedName("username") @BsonProperty("username")
    private String username;

    @Expose @SerializedName("last_login") @BsonProperty("last_login")
    private long lastLogin;

    @Expose @SerializedName("last_logout") @BsonProperty("last_logout")
    private long lastLogout;

    @Expose @SerializedName("first_join") @BsonProperty("first_join")
    private long firstJoin;

    @Expose @SerializedName("ip_address") @BsonProperty("ip_address")
    private String ipAddress;

    @Expose @SerializedName("total_playtime") @BsonProperty("total_playtime")
    private long totalPlaytime = 0;

    // Player progression
    @Expose @SerializedName("level") @BsonProperty("level")
    private int level = 1;

    @Expose @SerializedName("exp") @BsonProperty("exp")
    private int exp = 0;

    @Expose @SerializedName("achievement_points") @BsonProperty("achievement_points")
    private int achievementPoints = 0;

    // Statistics
    @Expose @SerializedName("monster_kills") @BsonProperty("monster_kills")
    private int monsterKills = 0;

    @Expose @SerializedName("player_kills") @BsonProperty("player_kills")
    private int playerKills = 0;

    @Expose @SerializedName("deaths") @BsonProperty("deaths")
    private int deaths = 0;

    @Expose @SerializedName("ore_mined") @BsonProperty("ore_mined")
    private int oreMined = 0;

    @Expose @SerializedName("fish_caught") @BsonProperty("fish_caught")
    private int fishCaught = 0;

    @Expose @SerializedName("blocks_broken") @BsonProperty("blocks_broken")
    private int blocksBroken = 0;

    @Expose @SerializedName("distance_traveled") @BsonProperty("distance_traveled")
    private double distanceTraveled = 0.0;

    // Economy
    @Expose @SerializedName("bank_gems") @BsonProperty("bank_gems")
    private int bankGems = 0;

    @Expose @SerializedName("elite_shards") @BsonProperty("elite_shards")
    private int eliteShards = 0;

    @Expose @SerializedName("total_gems_deposited") @BsonProperty("total_gems_deposited")
    private long totalGemsDeposited = 0;

    @Expose @SerializedName("total_gems_withdrawn") @BsonProperty("total_gems_withdrawn")
    private long totalGemsWithdrawn = 0;

    @Expose @SerializedName("highest_bank_balance") @BsonProperty("highest_bank_balance")
    private int highestBankBalance = 0;

    // Bank system
    @Expose @SerializedName("bank_pages") @BsonProperty("bank_pages")
    private int bankPages = 1;

    @Expose @SerializedName("collection_bin") @BsonProperty("collection_bin")
    private String serializedCollectionBin;

    @Expose @SerializedName("currency_tab_unlocked") @BsonProperty("currency_tab_unlocked")
    private boolean currencyTabUnlocked = false;

    @Expose @SerializedName("max_scrap_storage") @BsonProperty("max_scrap_storage")
    private int maxScrapStorage = 500;

    // Alignment system
    @Expose @SerializedName("alignment") @BsonProperty("alignment")
    private String alignment = "LAWFUL";

    @Expose @SerializedName("chaotic_time") @BsonProperty("chaotic_time")
    private long chaoticTime = 0;

    @Expose @SerializedName("neutral_time") @BsonProperty("neutral_time")
    private long neutralTime = 0;

    @Expose @SerializedName("alignment_changes") @BsonProperty("alignment_changes")
    private int alignmentChanges = 0;

    // Moderation
    @Expose @SerializedName("rank") @BsonProperty("rank")
    private String rank = "DEFAULT";

    @Expose @SerializedName("banned") @BsonProperty("banned")
    private boolean banned = false;

    @Expose @SerializedName("ban_reason") @BsonProperty("ban_reason")
    private String banReason = "";

    @Expose @SerializedName("ban_expiry") @BsonProperty("ban_expiry")
    private long banExpiry = 0;

    @Expose @SerializedName("muted") @BsonProperty("muted")
    private int muteTime = 0;

    @Expose @SerializedName("warnings") @BsonProperty("warnings")
    private int warnings = 0;

    @Expose @SerializedName("last_warning") @BsonProperty("last_warning")
    private long lastWarning = 0;

    // Chat system
    @Expose @SerializedName("chat_tag") @BsonProperty("chat_tag")
    private String chatTag = "DEFAULT";

    @Expose @SerializedName("chat_color") @BsonProperty("chat_color")
    private String chatColor = "WHITE";

    // Mount and guild
    @Expose @SerializedName("horse_tier") @BsonProperty("horse_tier")
    private int horseTier = 0;

    @Expose @SerializedName("horse_name") @BsonProperty("horse_name")
    private String horseName = "";

    @Expose @SerializedName("guild_name") @BsonProperty("guild_name")
    private String guildName = "";

    @Expose @SerializedName("guild_rank") @BsonProperty("guild_rank")
    private String guildRank = "";

    @Expose @SerializedName("guild_contribution") @BsonProperty("guild_contribution")
    private int guildContribution = 0;

    // Quest system
    @Expose @SerializedName("current_quest") @BsonProperty("current_quest")
    private String currentQuest = "";

    @Expose @SerializedName("quest_progress") @BsonProperty("quest_progress")
    private int questProgress = 0;

    @Expose @SerializedName("quest_points") @BsonProperty("quest_points")
    private int questPoints = 0;

    @Expose @SerializedName("daily_quests_completed") @BsonProperty("daily_quests_completed")
    private int dailyQuestsCompleted = 0;

    @Expose @SerializedName("last_daily_quest_reset") @BsonProperty("last_daily_quest_reset")
    private long lastDailyQuestReset = 0;

    // Professions
    @Expose @SerializedName("pickaxe_level") @BsonProperty("pickaxe_level")
    private int pickaxeLevel = 0;

    @Expose @SerializedName("fishing_level") @BsonProperty("fishing_level")
    private int fishingLevel = 0;

    @Expose @SerializedName("mining_xp") @BsonProperty("mining_xp")
    private int miningXp = 0;

    @Expose @SerializedName("fishing_xp") @BsonProperty("fishing_xp")
    private int fishingXp = 0;

    @Expose @SerializedName("farming_level") @BsonProperty("farming_level")
    private int farmingLevel = 0;

    @Expose @SerializedName("farming_xp") @BsonProperty("farming_xp")
    private int farmingXp = 0;

    @Expose @SerializedName("woodcutting_level") @BsonProperty("woodcutting_level")
    private int woodcuttingLevel = 0;

    @Expose @SerializedName("woodcutting_xp") @BsonProperty("woodcutting_xp")
    private int woodcuttingXp = 0;

    // PvP statistics
    @Expose @SerializedName("t1_kills") @BsonProperty("t1_kills")
    private int t1Kills = 0;

    @Expose @SerializedName("t2_kills") @BsonProperty("t2_kills")
    private int t2Kills = 0;

    @Expose @SerializedName("t3_kills") @BsonProperty("t3_kills")
    private int t3Kills = 0;

    @Expose @SerializedName("t4_kills") @BsonProperty("t4_kills")
    private int t4Kills = 0;

    @Expose @SerializedName("t5_kills") @BsonProperty("t5_kills")
    private int t5Kills = 0;

    @Expose @SerializedName("t6_kills") @BsonProperty("t6_kills")
    private int t6Kills = 0;

    @Expose @SerializedName("kill_streak") @BsonProperty("kill_streak")
    private int killStreak = 0;

    @Expose @SerializedName("best_kill_streak") @BsonProperty("best_kill_streak")
    private int bestKillStreak = 0;

    @Expose @SerializedName("pvp_rating") @BsonProperty("pvp_rating")
    private int pvpRating = 1000;

    // Settings
    @Expose @SerializedName("trade_disabled") @BsonProperty("trade_disabled")
    private boolean tradeDisabled = false;

    @Expose @SerializedName("energy_disabled") @BsonProperty("energy_disabled")
    private boolean energyDisabled = false;

    @Expose @SerializedName("last_daily_reward") @BsonProperty("last_daily_reward")
    private long lastDailyReward = 0;

    // Combat statistics
    @Expose @SerializedName("damage_dealt") @BsonProperty("damage_dealt")
    private long damageDealt = 0;

    @Expose @SerializedName("damage_taken") @BsonProperty("damage_taken")
    private long damageTaken = 0;

    @Expose @SerializedName("damage_blocked") @BsonProperty("damage_blocked")
    private long damageBlocked = 0;

    @Expose @SerializedName("damage_dodged") @BsonProperty("damage_dodged")
    private long damageDodged = 0;

    // Location and state
    @Expose @SerializedName("world") @BsonProperty("world")
    private String world;

    @Expose @SerializedName("location_x") @BsonProperty("location_x")
    private double locationX;

    @Expose @SerializedName("location_y") @BsonProperty("location_y")
    private double locationY;

    @Expose @SerializedName("location_z") @BsonProperty("location_z")
    private double locationZ;

    @Expose @SerializedName("location_yaw") @BsonProperty("location_yaw")
    private float locationYaw;

    @Expose @SerializedName("location_pitch") @BsonProperty("location_pitch")
    private float locationPitch;

    @Expose @SerializedName("previous_location") @BsonProperty("previous_location")
    private String previousLocation;

    // Player state with better defaults and validation
    @Expose @SerializedName("health") @BsonProperty("health")
    private double health = DEFAULT_HEALTH;

    @Expose @SerializedName("max_health") @BsonProperty("max_health")
    private double maxHealth = DEFAULT_MAX_HEALTH;

    @Expose @SerializedName("food_level") @BsonProperty("food_level")
    private int foodLevel = 20;

    @Expose @SerializedName("saturation") @BsonProperty("saturation")
    private float saturation = 5.0f;

    @Expose @SerializedName("xp_level") @BsonProperty("xp_level")
    private int xpLevel = 0;

    @Expose @SerializedName("xp_progress") @BsonProperty("xp_progress")
    private float xpProgress = 0.0f;

    @Expose @SerializedName("total_experience") @BsonProperty("total_experience")
    private int totalExperience = 0;

    @Expose @SerializedName("bed_spawn_location") @BsonProperty("bed_spawn_location")
    private String bedSpawnLocation;

    @Expose @SerializedName("gamemode") @BsonProperty("gamemode")
    private String gameMode = "SURVIVAL";

    // Inventory persistence - Enhanced with validation and backup
    @Expose @SerializedName("inventory_contents") @BsonProperty("inventory_contents")
    private String serializedInventory;

    @Expose @SerializedName("armor_contents") @BsonProperty("armor_contents")
    private String serializedArmor;

    @Expose @SerializedName("ender_chest_contents") @BsonProperty("ender_chest_contents")
    private String serializedEnderChest;

    @Expose @SerializedName("offhand_item") @BsonProperty("offhand_item")
    private String serializedOffhand;

    // Backup inventory system
    @Expose @SerializedName("backup_inventory") @BsonProperty("backup_inventory")
    private String backupSerializedInventory;

    @Expose @SerializedName("backup_armor") @BsonProperty("backup_armor")
    private String backupSerializedArmor;

    @Expose @SerializedName("backup_ender_chest") @BsonProperty("backup_ender_chest")
    private String backupSerializedEnderChest;

    @Expose @SerializedName("backup_offhand") @BsonProperty("backup_offhand")
    private String backupSerializedOffhand;

    @Expose @SerializedName("inventory_save_timestamp") @BsonProperty("inventory_save_timestamp")
    private long inventorySaveTimestamp = 0;

    @Expose @SerializedName("respawn_items") @BsonProperty("respawn_items")
    private String serializedRespawnItems;

    @Expose @SerializedName("respawn_item_count") @BsonProperty("respawn_item_count")
    private int respawnItemCount = 0;

    @Expose @SerializedName("death_timestamp") @BsonProperty("death_timestamp")
    private long deathTimestamp = 0;

    // Combat logout state with thread safety
    @Expose @SerializedName("combat_logout_state") @BsonProperty("combat_logout_state")
    private volatile CombatLogoutState combatLogoutState = CombatLogoutState.NONE;

    @Expose @SerializedName("combat_logout_timestamp") @BsonProperty("combat_logout_timestamp")
    private volatile long combatLogoutTimestamp = 0;

    @Expose @SerializedName("combat_logout_alignment") @BsonProperty("combat_logout_alignment")
    private volatile String combatLogoutAlignment = null;

    // Collections - initialized with thread-safe implementations
    @Expose @SerializedName("buddies") @BsonProperty("buddies")
    private Set<String> buddies = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("unlocked_chat_tags") @BsonProperty("unlocked_chat_tags")
    private Set<String> unlockedChatTags = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("bank_achievements") @BsonProperty("bank_achievements")
    private final Set<String> bankAchievements = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("bank_inventory") @BsonProperty("bank_inventory")
    private final Map<Integer, String> serializedBankItems = new ConcurrentHashMap<>();

    @Expose @SerializedName("bank_authorized_users") @BsonProperty("bank_authorized_users")
    private final Set<String> bankAuthorizedUsers = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("bank_access_log") @BsonProperty("bank_access_log")
    private final List<String> bankAccessLog = Collections.synchronizedList(new ArrayList<>());

    @Expose @SerializedName("bank_upgrades_purchased") @BsonProperty("bank_upgrades_purchased")
    private final List<String> bankUpgradesPurchased = Collections.synchronizedList(new ArrayList<>());

    @Expose @SerializedName("bank_transactions") @BsonProperty("bank_transactions")
    private final Map<Long, String> bankTransactions = new ConcurrentHashMap<>();

    @Expose @SerializedName("scrap_storage") @BsonProperty("scrap_storage")
    private final Map<String, Integer> scrapStorage = new ConcurrentHashMap<>();

    @Expose @SerializedName("scrap_tier_limits") @BsonProperty("scrap_tier_limits")
    private final Map<String, Integer> scrapTierLimits = new ConcurrentHashMap<>();

    @Expose @SerializedName("toggle_settings") @BsonProperty("toggle_settings")
    private final Set<String> toggleSettings = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("notification_settings") @BsonProperty("notification_settings")
    private final Map<String, Boolean> notificationSettings = new ConcurrentHashMap<>();

    @Expose @SerializedName("completed_quests") @BsonProperty("completed_quests")
    private final Set<String> completedQuests = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("world_boss_damage") @BsonProperty("world_boss_damage")
    private final Map<String, Integer> worldBossDamage = new ConcurrentHashMap<>();

    @Expose @SerializedName("world_boss_kills") @BsonProperty("world_boss_kills")
    private final Map<String, Integer> worldBossKills = new ConcurrentHashMap<>();

    @Expose @SerializedName("blocked_players") @BsonProperty("blocked_players")
    private final Set<String> blockedPlayers = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("active_potion_effects") @BsonProperty("active_potion_effects")
    private final List<String> activePotionEffects = Collections.synchronizedList(new ArrayList<>());

    @Expose @SerializedName("achievements") @BsonProperty("achievements")
    private final Set<String> achievements = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("daily_rewards_claimed") @BsonProperty("daily_rewards_claimed")
    private final Set<String> dailyRewardsClaimed = ConcurrentHashMap.newKeySet();

    @Expose @SerializedName("events_participated") @BsonProperty("events_participated")
    private final Map<String, Integer> eventsParticipated = new ConcurrentHashMap<>();

    @Expose @SerializedName("event_wins") @BsonProperty("event_wins")
    private final Map<String, Integer> eventWins = new ConcurrentHashMap<>();

    // Transient fields
    private transient final Map<String, Object> temporaryData = new ConcurrentHashMap<>();
    private transient final AtomicBoolean inventoryBeingApplied = new AtomicBoolean(false);
    private transient final AtomicBoolean inventoryUpdateInProgress = new AtomicBoolean(false);
    private transient Player bukkitPlayer;
    private transient boolean inCombat = false;
    private transient long lastCombatTime = 0;
    private transient long sessionStartTime;

    public YakPlayer(Player player) {
        this.uuid = player.getUniqueId();
        this.username = validateUsername(player.getName());
        this.ipAddress = extractIpAddress(player);
        this.firstJoin = Instant.now().getEpochSecond();
        this.lastLogin = firstJoin;
        this.sessionStartTime = System.currentTimeMillis();
        this.bukkitPlayer = player;

        Location loc = player.getLocation();
        if (loc != null && loc.getWorld() != null) {
            updateLocation(loc);
        }

        // Initialize with safe defaults
        forceUpdateInventory(player);
        updateStatsSafely(player);

        this.temporaryData.put("energy", 100);
        initializeDefaultSettings();

        logger.info("Created new YakPlayer for: " + player.getName());
    }

    public YakPlayer(UUID uuid) {
        this.uuid = uuid;
        this.bukkitPlayer = Bukkit.getPlayer(uuid);
        this.temporaryData.put("energy", 100);
        this.sessionStartTime = System.currentTimeMillis();
        initializeDefaultSettings();
    }

    private void initializeDefaultSettings() {
        // Initialize notification settings with safe defaults
        if (notificationSettings.isEmpty()) {
            notificationSettings.put("buddy_join", true);
            notificationSettings.put("buddy_leave", true);
            notificationSettings.put("guild_messages", true);
            notificationSettings.put("trade_requests", true);
            notificationSettings.put("party_invites", true);
        }

        // Initialize toggle settings with safe defaults
        if (toggleSettings.isEmpty()) {
            toggleSettings.add("Player Messages");
            toggleSettings.add("Drop Protection");
            toggleSettings.add("Sound Effects");
        }
    }

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

    private String extractIpAddress(Player player) {
        try {
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
        } catch (Exception e) {
            logger.warning("Could not extract IP address for player " + player.getName() + ": " + e.getMessage());
        }
        return "unknown";
    }

    // Inventory persistence methods
    // This method ALWAYS updates inventory regardless of state
    public void forceUpdateInventory(Player player) {
        if (player == null || !player.isOnline()) {
            logger.warning("Cannot force update inventory for null or offline player");
            return;
        }

        if (!inventoryUpdateInProgress.compareAndSet(false, true)) {
            logger.warning("Inventory update already in progress for: " + player.getName());
            return;
        }

        try {
            logger.info("Updating inventory for player: " + player.getName());

            // Update location first
            Location location = player.getLocation();
            if (location != null && location.getWorld() != null) {
                updateLocation(location);
            }

            // Create backup before updating
            createInventoryBackup();

            // Get current inventory state
            ItemStack[] inventoryContents = player.getInventory().getContents();
            ItemStack[] armorContents = player.getInventory().getArmorContents();
            ItemStack[] enderContents = player.getEnderChest().getContents();
            ItemStack offhandItem = player.getInventory().getItemInOffHand();

            // Serialize with validation
            String newInventoryData = ItemSerializer.serializeItemStacksWithValidation(inventoryContents, "main_inventory");
            String newArmorData = ItemSerializer.serializeItemStacksWithValidation(armorContents, "armor");
            String newEnderData = ItemSerializer.serializeItemStacksWithValidation(enderContents, "ender_chest");
            String newOffhandData = ItemSerializer.serializeItemStackWithValidation(offhandItem, "offhand");

            // Update with validation
            this.serializedInventory = newInventoryData;
            this.serializedArmor = newArmorData;
            this.serializedEnderChest = newEnderData;
            this.serializedOffhand = newOffhandData;
            this.inventorySaveTimestamp = System.currentTimeMillis();

            // Count items for logging
            int itemCount = 0;
            for (ItemStack item : inventoryContents) {
                if (item != null && item.getType() != Material.AIR) itemCount++;
            }
            for (ItemStack item : armorContents) {
                if (item != null && item.getType() != Material.AIR) itemCount++;
            }
            for (ItemStack item : enderContents) {
                if (item != null && item.getType() != Material.AIR) itemCount++;
            }
            if (offhandItem != null && offhandItem.getType() != Material.AIR) itemCount++;

            logger.info("Inventory update completed for " + player.getName() + " - " + itemCount + " items saved");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Inventory update failed for " + player.getName(), e);
            // Try to restore from backup
            restoreInventoryFromBackup();
        } finally {
            inventoryUpdateInProgress.set(false);
        }
    }

    // Regular inventory update (maintains backward compatibility)
    public void updateInventory(Player player) {
        // Always force update to prevent any rollback issues
        forceUpdateInventory(player);
    }

    private void createInventoryBackup() {
        try {
            this.backupSerializedInventory = this.serializedInventory;
            this.backupSerializedArmor = this.serializedArmor;
            this.backupSerializedEnderChest = this.serializedEnderChest;
            this.backupSerializedOffhand = this.serializedOffhand;
            logger.fine("Created inventory backup for " + username);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create inventory backup for " + username, e);
        }
    }

    private void restoreInventoryFromBackup() {
        try {
            if (backupSerializedInventory != null) {
                this.serializedInventory = this.backupSerializedInventory;
                this.serializedArmor = this.backupSerializedArmor;
                this.serializedEnderChest = this.backupSerializedEnderChest;
                this.serializedOffhand = this.backupSerializedOffhand;
                logger.warning("Restored inventory from backup for " + username);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to restore inventory from backup for " + username, e);
        }
    }

    // Inventory application with recovery
    public void applyInventory(Player player) {
        if (player == null || !player.isOnline()) {
            logger.warning("Cannot apply inventory to null or offline player");
            return;
        }

        if (!inventoryBeingApplied.compareAndSet(false, true)) {
            logger.warning("Inventory application already in progress for: " + player.getName());
            return;
        }

        try {
            boolean isCombatLogoutRejoin = isCombatLogoutProcessed();
            logger.info("Applying inventory to player: " + player.getName() +
                    (isCombatLogoutRejoin ? " (combat logout processed)" : " (normal)") +
                    " - Save timestamp: " + inventorySaveTimestamp);

            // Clear inventory first
            clearInventoryForLoading(player);

            // Apply inventory components with error handling
            boolean success = applyInventoryWithRecovery(player);

            if (!success) {
                logger.warning("Primary inventory application failed, attempting backup restore for " + player.getName());
                applyBackupInventory(player);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Inventory application failed for " + player.getName(), e);
            // Apply default inventory on complete failure
            applyDefaultInventoryState(player);
        } finally {
            inventoryBeingApplied.set(false);
        }
    }

    private boolean applyBackupInventory(Player player) {
        try {
            if (backupSerializedInventory == null) {
                logger.warning("No backup inventory available for " + player.getName());
                return false;
            }

            logger.info("Applying backup inventory for " + player.getName());

            // Temporarily swap data
            String originalInventory = this.serializedInventory;
            String originalArmor = this.serializedArmor;
            String originalEnderChest = this.serializedEnderChest;
            String originalOffhand = this.serializedOffhand;

            this.serializedInventory = this.backupSerializedInventory;
            this.serializedArmor = this.backupSerializedArmor;
            this.serializedEnderChest = this.backupSerializedEnderChest;
            this.serializedOffhand = this.backupSerializedOffhand;

            boolean success = applyInventoryWithRecovery(player);

            // Restore original data if backup application failed
            if (!success) {
                this.serializedInventory = originalInventory;
                this.serializedArmor = originalArmor;
                this.serializedEnderChest = originalEnderChest;
                this.serializedOffhand = originalOffhand;
            }

            return success;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Backup inventory application failed for " + player.getName(), e);
            return false;
        }
    }

    private void clearInventoryForLoading(Player player) {
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getEnderChest().clear();
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.updateInventory();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error clearing inventory for loading", e);
        }
    }

    private boolean applyInventoryWithRecovery(Player player) {
        int successfulComponents = 0;
        int totalComponents = 4;

        // Apply main inventory
        if (applyMainInventorySafely(player)) {
            successfulComponents++;
        }

        // Apply armor
        if (applyArmorSafely(player)) {
            successfulComponents++;
        }

        // Apply ender chest
        if (applyEnderChestSafely(player)) {
            successfulComponents++;
        }

        // Apply offhand
        if (applyOffhandSafely(player)) {
            successfulComponents++;
        }

        player.updateInventory();

        boolean success = successfulComponents >= 3; // Allow 1 component to fail
        logger.info("Applied inventory components for " + player.getName() +
                " (" + successfulComponents + "/" + totalComponents + " successful) - " +
                (success ? "SUCCESS" : "FAILED"));

        return success;
    }

    private boolean applyMainInventorySafely(Player player) {
        if (serializedInventory == null || serializedInventory.isEmpty()) {
            logger.fine("No main inventory data to apply for " + player.getName());
            return true; // Empty is considered successful
        }

        try {
            ItemStack[] contents = ItemSerializer.deserializeItemStacksWithValidation(serializedInventory);
            if (contents != null) {
                int itemsApplied = 0;
                int inventorySize = player.getInventory().getSize();

                for (int i = 0; i < contents.length && i < inventorySize; i++) {
                    if (contents[i] != null && contents[i].getType() != Material.AIR) {
                        try {
                            player.getInventory().setItem(i, contents[i].clone());
                            itemsApplied++;
                        } catch (Exception e) {
                            logger.fine("Failed to apply inventory item at slot " + i + " for " + player.getName() + ": " + e.getMessage());
                        }
                    }
                }

                logger.fine("Applied main inventory for " + player.getName() + " (" + itemsApplied + " items)");
                return true;
            } else {
                logger.warning("Failed to deserialize inventory data for " + player.getName());
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying main inventory for " + player.getName(), e);
            return false;
        }
    }

    private boolean applyArmorSafely(Player player) {
        if (serializedArmor == null || serializedArmor.isEmpty()) {
            logger.fine("No armor data to apply for " + player.getName());
            return true; // Empty is considered successful
        }

        try {
            ItemStack[] armor = ItemSerializer.deserializeItemStacksWithValidation(serializedArmor);
            if (armor != null) {
                int armorApplied = 0;

                if (armor.length > 0 && armor[0] != null && armor[0].getType() != Material.AIR) {
                    try {
                        player.getInventory().setBoots(armor[0].clone());
                        armorApplied++;
                    } catch (Exception e) {
                        logger.fine("Failed to apply boots for " + player.getName() + ": " + e.getMessage());
                    }
                }

                if (armor.length > 1 && armor[1] != null && armor[1].getType() != Material.AIR) {
                    try {
                        player.getInventory().setLeggings(armor[1].clone());
                        armorApplied++;
                    } catch (Exception e) {
                        logger.fine("Failed to apply leggings for " + player.getName() + ": " + e.getMessage());
                    }
                }

                if (armor.length > 2 && armor[2] != null && armor[2].getType() != Material.AIR) {
                    try {
                        player.getInventory().setChestplate(armor[2].clone());
                        armorApplied++;
                    } catch (Exception e) {
                        logger.fine("Failed to apply chestplate for " + player.getName() + ": " + e.getMessage());
                    }
                }

                if (armor.length > 3 && armor[3] != null && armor[3].getType() != Material.AIR) {
                    try {
                        player.getInventory().setHelmet(armor[3].clone());
                        armorApplied++;
                    } catch (Exception e) {
                        logger.fine("Failed to apply helmet for " + player.getName() + ": " + e.getMessage());
                    }
                }

                logger.fine("Applied armor for " + player.getName() + " (" + armorApplied + " pieces)");
                return true;
            } else {
                logger.warning("Failed to deserialize armor data for " + player.getName());
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying armor for " + player.getName(), e);
            return false;
        }
    }

    private boolean applyEnderChestSafely(Player player) {
        try {
            int enderItemsApplied = 0;

            // Apply ender chest contents
            if (serializedEnderChest != null && !serializedEnderChest.isEmpty()) {
                ItemStack[] enderContents = ItemSerializer.deserializeItemStacksWithValidation(serializedEnderChest);
                if (enderContents != null) {
                    for (int i = 0; i < enderContents.length && i < 27; i++) {
                        if (enderContents[i] != null && enderContents[i].getType() != Material.AIR) {
                            try {
                                player.getEnderChest().setItem(i, enderContents[i].clone());
                                enderItemsApplied++;
                            } catch (Exception e) {
                                logger.fine("Failed to apply ender chest item at slot " + i + " for " + player.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                } else {
                    logger.warning("Failed to deserialize ender chest data for " + player.getName());
                }
            }

            logger.fine("Applied ender chest for " + player.getName() + " (" + enderItemsApplied + " items)");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying ender chest for " + player.getName(), e);
            return false;
        }
    }

    private boolean applyOffhandSafely(Player player) {
        try {
            if (serializedOffhand != null && !serializedOffhand.isEmpty()) {
                try {
                    ItemStack offhandItem = ItemSerializer.deserializeItemStackWithValidation(serializedOffhand);
                    if (offhandItem != null && offhandItem.getType() != Material.AIR) {
                        player.getInventory().setItemInOffHand(offhandItem.clone());
                    } else {
                        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                    }
                } catch (Exception e) {
                    logger.fine("Failed to apply offhand item for " + player.getName() + ": " + e.getMessage());
                    player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                    return false;
                }
            } else {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }

            logger.fine("Applied offhand for " + player.getName());
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying offhand for " + player.getName(), e);
            return false;
        }
    }

    private void applyDefaultInventoryState(Player player) {
        try {
            logger.warning("Applying default inventory state for " + player.getName());

            clearInventoryForLoading(player);

            // Give basic starter items if it's a new player
            if (isNewPlayer()) {
                giveStarterItems(player);
            }

            player.updateInventory();
            logger.info("Applied default inventory state for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to apply default inventory state for " + player.getName(), e);
        }
    }

    private boolean isNewPlayer() {
        try {
            long twoHoursAgo = (System.currentTimeMillis() / 1000) - 7200;
            return firstJoin > twoHoursAgo;
        } catch (Exception e) {
            return true;
        }
    }

    private void giveStarterItems(Player player) {
        try {
            // Give basic starter items
            player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD, 1));
            player.getInventory().setItem(1, new ItemStack(Material.BREAD, 16));
            logger.info("Gave starter items to new player: " + player.getName());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to give starter items to " + player.getName(), e);
        }
    }

    // Respawn items management with safety
    public boolean setRespawnItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            clearRespawnItems();
            return true;
        }

        List<ItemStack> validItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                try {
                    validItems.add(item.clone());
                } catch (Exception e) {
                    logger.warning("Failed to clone item for respawn storage: " + e.getMessage());
                }
            }
        }

        if (validItems.isEmpty()) {
            clearRespawnItems();
            return true;
        }

        try {
            String serialized = ItemSerializer.serializeItemStacksWithValidation(
                    validItems.toArray(new ItemStack[0]), "respawn");
            if (serialized != null) {
                this.serializedRespawnItems = serialized;
                this.respawnItemCount = validItems.size();
                this.deathTimestamp = System.currentTimeMillis();
                logger.info("Stored " + validItems.size() + " respawn items for " + username);
                return true;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to serialize respawn items for " + username, e);
        }

        return false;
    }

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
                        try {
                            itemList.add(item.clone());
                        } catch (Exception e) {
                            logger.warning("Failed to clone respawn item: " + e.getMessage());
                        }
                    }
                }
                return itemList;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error deserializing respawn items for " + username, e);
        }

        return new ArrayList<>();
    }

    public boolean hasRespawnItems() {
        return serializedRespawnItems != null && !serializedRespawnItems.isEmpty() && respawnItemCount > 0;
    }

    public void clearRespawnItems() {
        this.serializedRespawnItems = null;
        this.respawnItemCount = 0;
        this.deathTimestamp = 0;
    }

    // Combat logout state management
    public void setCombatLogoutState(CombatLogoutState newState) {
        if (newState == null) {
            newState = CombatLogoutState.NONE;
        }

        CombatLogoutState oldState = this.combatLogoutState;

        synchronized (this) {
            this.combatLogoutState = newState;
            this.combatLogoutTimestamp = System.currentTimeMillis();

            if (newState == CombatLogoutState.PROCESSING) {
                this.combatLogoutAlignment = this.alignment;
            }

            if (newState == CombatLogoutState.NONE) {
                this.combatLogoutAlignment = null;
            }

            logger.info("Combat logout state transition for " + username + ": " + oldState + " -> " + newState);
        }
    }

    public boolean isInCombatLogoutState() {
        return combatLogoutState != null && combatLogoutState != CombatLogoutState.NONE;
    }

    public boolean isCombatLogoutProcessed() {
        return combatLogoutState == CombatLogoutState.PROCESSED || combatLogoutState == CombatLogoutState.COMPLETED;
    }

    public long getCombatLogoutAge() {
        if (combatLogoutTimestamp <= 0) {
            return 0;
        }
        return System.currentTimeMillis() - combatLogoutTimestamp;
    }

    public String getCombatLogoutAlignment() {
        return combatLogoutAlignment != null ? combatLogoutAlignment : alignment;
    }

    // Accessor method for inventory update state
    public boolean isInventoryBeingApplied() {
        return inventoryBeingApplied.get();
    }

    // Connection management with better error handling
    public void connect(Player player) {
        if (player == null) {
            logger.warning("Cannot connect null player");
            return;
        }

        this.bukkitPlayer = player;
        this.username = validateUsername(player.getName());
        this.lastLogin = Instant.now().getEpochSecond();
        this.sessionStartTime = System.currentTimeMillis();

        String newIpAddress = extractIpAddress(player);
        if (!newIpAddress.equals("unknown")) {
            this.ipAddress = newIpAddress;
        }

        // Validate and fix health values on connect
        validateAndFixHealthValues();

        logger.info("Connected player: " + player.getName());
    }

    private void validateAndFixHealthValues() {
        // Validate max health
        if (maxHealth <= 0 || Double.isNaN(maxHealth) || Double.isInfinite(maxHealth)) {
            maxHealth = DEFAULT_MAX_HEALTH;
            logger.info("Fixed invalid max health for " + username + " to " + DEFAULT_MAX_HEALTH);
        }

        // Validate health
        if (health <= 0 || health > maxHealth || Double.isNaN(health) || Double.isInfinite(health)) {
            health = maxHealth;
            logger.info("Fixed invalid health for " + username + " to " + health);
        }

        // Validate food level
        if (foodLevel < 0 || foodLevel > 20) {
            foodLevel = 20;
            logger.info("Fixed invalid food level for " + username + " to 20");
        }

        // Validate saturation
        if (saturation < 0 || saturation > 20 || Float.isNaN(saturation) || Float.isInfinite(saturation)) {
            saturation = 5.0f;
            logger.info("Fixed invalid saturation for " + username + " to 5.0");
        }
    }

    public void disconnect() {
        this.lastLogout = Instant.now().getEpochSecond();

        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            try {
                updateLocation(bukkitPlayer.getLocation());
                updateStatsSafely(bukkitPlayer);
            } catch (Exception e) {
                logger.warning("Failed to update location/stats on disconnect: " + e.getMessage());
            }
        }

        if (sessionStartTime > 0) {
            long sessionDuration = System.currentTimeMillis() - sessionStartTime;
            this.totalPlaytime += sessionDuration / 1000;
        }

        this.bukkitPlayer = null;
    }

    // Location management with better validation
    public void updateLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            logger.fine("Cannot update location - null location or world");
            return;
        }

        try {
            if (this.world != null) {
                this.previousLocation = this.world + ":" + this.locationX + ":" +
                        this.locationY + ":" + this.locationZ;
            }

            this.world = location.getWorld().getName();
            this.locationX = location.getX();
            this.locationY = location.getY();
            this.locationZ = location.getZ();
            this.locationYaw = location.getYaw();
            this.locationPitch = location.getPitch();

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating location for " + username, e);
        }
    }

    public Location getLocation() {
        if (world == null) {
            logger.fine("No world set for player " + username);
            return null;
        }

        try {
            World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null) {
                logger.warning("World '" + world + "' not found for player " + username);
                return null;
            }

            return new Location(bukkitWorld, locationX, locationY, locationZ, locationYaw, locationPitch);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting location for " + username, e);
            return null;
        }
    }

    // Stats management with proper validation and health handling
    public void updateStats(Player player) {
        updateStatsSafely(player);
    }

    private void updateStatsSafely(Player player) {
        if (player == null) {
            logger.warning("Cannot update stats for null player");
            return;
        }

        try {
            // Proper health handling with validation
            double playerHealth = player.getHealth();
            double playerMaxHealth = player.getMaxHealth();

            // Validate and store health values
            if (playerMaxHealth > 0 && !Double.isNaN(playerMaxHealth) && !Double.isInfinite(playerMaxHealth)) {
                this.maxHealth = playerMaxHealth;
            } else {
                this.maxHealth = DEFAULT_MAX_HEALTH;
                logger.warning("Invalid max health detected for " + player.getName() + ", using default");
            }

            if (playerHealth >= 0 && playerHealth <= this.maxHealth && !Double.isNaN(playerHealth) && !Double.isInfinite(playerHealth)) {
                this.health = playerHealth;
            } else {
                this.health = this.maxHealth;
                logger.warning("Invalid health detected for " + player.getName() + ", using max health");
            }

            // Update other stats with validation
            this.foodLevel = Math.max(0, Math.min(20, player.getFoodLevel()));

            float playerSaturation = player.getSaturation();
            this.saturation = Float.isNaN(playerSaturation) || Float.isInfinite(playerSaturation) ? 5.0f : Math.max(0, Math.min(20, playerSaturation));

            this.xpLevel = Math.max(0, Math.min(21863, player.getLevel()));

            float playerExp = player.getExp();
            this.xpProgress = Float.isNaN(playerExp) || Float.isInfinite(playerExp) ? 0.0f : Math.max(0, Math.min(1, playerExp));

            this.totalExperience = Math.max(0, player.getTotalExperience());
            this.gameMode = player.getGameMode().name();

            updatePotionEffects(player);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating stats for " + player.getName(), e);
        }
    }

    // Stats application with health management
    public void applyStats(Player player, boolean skipGameMode) {
        if (player == null) {
            logger.warning("Cannot apply stats to null player");
            return;
        }

        try {
            // Enhanced health application with proper sequencing
            applyHealthSafely(player);

            // Apply other stats
            player.setFoodLevel(Math.max(0, Math.min(20, foodLevel)));

            float safeSaturation = Float.isNaN(saturation) || Float.isInfinite(saturation) ? 5.0f : Math.max(0, Math.min(20, saturation));
            player.setSaturation(safeSaturation);

            player.setLevel(Math.max(0, Math.min(21863, xpLevel)));

            float safeXpProgress = Float.isNaN(xpProgress) || Float.isInfinite(xpProgress) ? 0.0f : Math.max(0, Math.min(1, xpProgress));
            player.setExp(safeXpProgress);

            player.setTotalExperience(Math.max(0, totalExperience));

            // Apply game mode if not skipped
            if (!skipGameMode) {
                try {
                    GameMode mode = GameMode.valueOf(gameMode);
                    if (player.getGameMode() != mode) {
                        player.setGameMode(mode);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid game mode for player " + username + ": " + gameMode);
                    player.setGameMode(GameMode.SURVIVAL);
                    this.gameMode = "SURVIVAL";
                }
            }

            // Apply bed spawn location
            if (bedSpawnLocation != null) {
                try {
                    Location bedLoc = parseBedSpawnLocation(bedSpawnLocation);
                    if (bedLoc != null) {
                        player.setBedSpawnLocation(bedLoc, true);
                    }
                } catch (Exception e) {
                    logger.fine("Failed to apply bed spawn location for " + username + ": " + e.getMessage());
                }
            }

            applyPotionEffects(player);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying stats for player " + player.getName(), e);
        }
    }

    private void applyHealthSafely(Player player) {
        try {
            // Validate our stored health values first
            validateAndFixHealthValues();

            // Apply max health first
            player.setMaxHealth(maxHealth);

            // Schedule health application after max health is set
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    try {
                        double currentMaxHealth = player.getMaxHealth();
                        double safeHealth = Math.min(Math.max(0.1, health), currentMaxHealth);

                        player.setHealth(safeHealth);
                        logger.fine("Applied health for " + player.getName() + ": " + safeHealth + "/" + currentMaxHealth);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error in delayed health application for " + player.getName(), e);
                        // Fallback to safe values
                        try {
                            player.setHealth(player.getMaxHealth());
                        } catch (Exception e2) {
                            logger.log(Level.SEVERE, "Critical health application failure for " + player.getName(), e2);
                        }
                    }
                }
            }, 2L); // 2 tick delay to ensure max health is applied first

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in health application for " + player.getName(), e);
            // Emergency fallback
            try {
                player.setMaxHealth(DEFAULT_MAX_HEALTH);
                player.setHealth(DEFAULT_HEALTH);
                this.maxHealth = DEFAULT_MAX_HEALTH;
                this.health = DEFAULT_HEALTH;
            } catch (Exception e2) {
                logger.log(Level.SEVERE, "Emergency health fallback failed for " + player.getName(), e2);
            }
        }
    }

    private Location parseBedSpawnLocation(String locationStr) {
        if (locationStr == null || locationStr.isEmpty()) return null;

        try {
            String[] parts = locationStr.split(":");
            if (parts.length >= 4) {
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    return new Location(world,
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                }
            }
        } catch (Exception e) {
            logger.fine("Error parsing bed spawn location: " + locationStr);
        }
        return null;
    }

    public void applyStats(Player player) {
        applyStats(player, false);
    }

    private void updatePotionEffects(Player player) {
        try {
            activePotionEffects.clear();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                try {
                    String serialized = PotionEffectSerializer.serialize(effect);
                    if (serialized != null) {
                        activePotionEffects.add(serialized);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to serialize potion effect: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating potion effects for " + player.getName(), e);
        }
    }

    private void applyPotionEffects(Player player) {
        try {
            // Remove existing effects
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                try {
                    player.removePotionEffect(effect.getType());
                } catch (Exception e) {
                    logger.fine("Failed to remove potion effect: " + e.getMessage());
                }
            }

            // Apply saved effects
            for (String effectData : activePotionEffects) {
                try {
                    PotionEffect effect = PotionEffectSerializer.deserialize(effectData);
                    if (effect != null) {
                        player.addPotionEffect(effect);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to apply potion effect: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying potion effects for " + player.getName(), e);
        }
    }

    // Social system methods with error handling - Using Paper's Adventure API
    public boolean addBuddy(String buddyName) {
        if (buddyName == null || buddyName.trim().isEmpty()) {
            return false;
        }

        if (buddies.size() >= MAX_BUDDIES) {
            sendMessageIfOnline(Component.text("You have reached the maximum number of buddies (" + MAX_BUDDIES + ").")
                    .color(NamedTextColor.RED));
            return false;
        }

        String normalizedName = buddyName.toLowerCase().trim();

        if (normalizedName.equals(username.toLowerCase())) {
            sendMessageIfOnline(Component.text("You cannot add yourself as a buddy!")
                    .color(NamedTextColor.RED));
            return false;
        }

        if (blockedPlayers.contains(normalizedName)) {
            sendMessageIfOnline(Component.text("You cannot add a blocked player as a buddy!")
                    .color(NamedTextColor.RED));
            return false;
        }

        if (buddies.add(normalizedName)) {
            sendMessageIfOnline(Component.text("Successfully added " + buddyName + " as a buddy!")
                    .color(NamedTextColor.GREEN));

            Player player = getBukkitPlayer();
            if (player != null) {
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                } catch (Exception e) {
                    logger.fine("Failed to play sound for buddy add: " + e.getMessage());
                }
            }

            return true;
        }
        return false;
    }

    public boolean removeBuddy(String buddyName) {
        if (buddyName == null) return false;

        boolean removed = buddies.remove(buddyName.toLowerCase().trim());
        if (removed) {
            sendMessageIfOnline(Component.text("Removed " + buddyName + " from your buddy list.")
                    .color(NamedTextColor.YELLOW));

            Player player = getBukkitPlayer();
            if (player != null) {
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
                } catch (Exception e) {
                    logger.fine("Failed to play sound for buddy remove: " + e.getMessage());
                }
            }
        }
        return removed;
    }

    public boolean isBuddy(String buddyName) {
        if (buddyName == null) return false;
        return buddies.contains(buddyName.toLowerCase().trim());
    }

    public void unlockChatTag(ChatTag tag) {
        if (tag == null) return;

        unlockedChatTags.add(tag.name());
        sendMessageIfOnline(Component.text("Unlocked chat tag: " + tag.getTag())
                .color(NamedTextColor.GREEN));
    }

    // Display formatting methods
    public String getFormattedDisplayName() {
        StringBuilder displayName = new StringBuilder();

        try {
            if (isInGuild()) {
                displayName.append(ChatColor.WHITE).append("[").append(guildName).append("] ");
            }

            if (!chatTag.equals("DEFAULT")) {
                try {
                    ChatTag chatTagEnum = ChatTag.getByName(chatTag);
                    if (chatTagEnum != ChatTag.DEFAULT) {
                        displayName.append(chatTagEnum.getTag()).append(" ");
                    }
                } catch (Exception e) {
                    logger.fine("Invalid chat tag: " + chatTag);
                }
            }

            if (!rank.equals("DEFAULT")) {
                try {
                    Rank rankEnum = Rank.fromString(rank);
                    if (rankEnum != Rank.DEFAULT) {
                        displayName.append(ChatColor.translateAlternateColorCodes('&', rankEnum.tag)).append(" ");
                    }
                } catch (Exception e) {
                    logger.fine("Invalid rank: " + rank);
                }
            }
        } catch (Exception e) {
            logger.warning("Error formatting display name for " + username + ": " + e.getMessage());
        }

        displayName.append(getColorByAlignment()).append(username);

        return displayName.toString();
    }

    private ChatColor getColorByAlignment() {
        if (alignment == null) return ChatColor.GRAY;

        switch (alignment) {
            case "LAWFUL": return ChatColor.GRAY;
            case "NEUTRAL": return ChatColor.YELLOW;
            case "CHAOTIC": return ChatColor.RED;
            default: return ChatColor.GRAY;
        }
    }

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

    public String getTimeSinceDeath() {
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
    }

    // Bank utility methods
    public void addBankTransaction(String description) {
        if (description == null || description.trim().isEmpty()) {
            return;
        }

        try {
            bankTransactions.put(System.currentTimeMillis(), description);
            if (bankTransactions.size() > 100) {
                Long oldest = bankTransactions.keySet().stream().min(Long::compareTo).orElse(null);
                if (oldest != null) {
                    bankTransactions.remove(oldest);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to add bank transaction: " + e.getMessage());
        }
    }

    public void addBankAccessLog(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return;
        }

        try {
            bankAccessLog.add(String.format("[%s] %s", new Date(), entry));
            while (bankAccessLog.size() > 50) {
                bankAccessLog.remove(0);
            }
        } catch (Exception e) {
            logger.warning("Failed to add bank access log: " + e.getMessage());
        }
    }

    public boolean hasBankAchievement(String achievement) {
        return achievement != null && bankAchievements.contains(achievement);
    }

    public void addBankAchievement(String achievement) {
        if (achievement == null || achievement.trim().isEmpty()) {
            return;
        }

        if (bankAchievements.add(achievement)) {
            Player player = getBukkitPlayer();
            if (player != null) {
                try {
                    player.sendMessage(Component.text("*** ACHIEVEMENT UNLOCKED ***")
                            .color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD));
                    player.sendMessage(Component.text(achievement)
                            .color(NamedTextColor.YELLOW));
                } catch (Exception e) {
                    logger.fine("Failed to send achievement message: " + e.getMessage());
                }
            }
        }
    }

    public String getSerializedBankItems(int page) {
        return serializedBankItems.get(page);
    }

    public void setSerializedBankItems(int page, String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            serializedBankItems.remove(page);
        } else {
            serializedBankItems.put(page, serialized);
        }
    }

    public Map<Integer, String> getAllSerializedBankItems() {
        return new HashMap<>(serializedBankItems);
    }

    // Utility methods
    public boolean isOnline() {
        return bukkitPlayer != null && bukkitPlayer.isOnline();
    }

    public void clearTemporaryData() {
        try {
            CombatLogoutState savedState = combatLogoutState;
            long savedTimestamp = combatLogoutTimestamp;
            String savedAlignment = combatLogoutAlignment;

            temporaryData.clear();

            combatLogoutState = savedState;
            combatLogoutTimestamp = savedTimestamp;
            combatLogoutAlignment = savedAlignment;
        } catch (Exception e) {
            logger.warning("Error clearing temporary data: " + e.getMessage());
        }
    }

    public boolean isInGuild() {
        return guildName != null && !guildName.trim().isEmpty();
    }

    public boolean isInCombat() {
        return inCombat && (System.currentTimeMillis() - lastCombatTime) < 15000;
    }

    public void setInCombat(boolean inCombat) {
        this.inCombat = inCombat;
        if (inCombat) {
            this.lastCombatTime = System.currentTimeMillis();
        }
    }

    public long getSessionPlaytime() {
        if (sessionStartTime > 0) {
            return System.currentTimeMillis() - sessionStartTime;
        }
        return 0;
    }

    public boolean isToggled(String setting) {
        if (setting == null) return false;

        if ("God Mode Disabled".equals(setting)) {
            return false;
        }

        return toggleSettings.contains(setting);
    }

    public boolean toggleSetting(String setting) {
        if (setting == null || setting.trim().isEmpty()) {
            return false;
        }

        if ("God Mode Disabled".equals(setting)) {
            logger.warning("Attempted to toggle invalid setting: " + setting + " for " + username);
            return false;
        }

        try {
            boolean wasToggled = toggleSettings.contains(setting);
            if (wasToggled) {
                toggleSettings.remove(setting);
            } else {
                toggleSettings.add(setting);
            }

            return !wasToggled;
        } catch (Exception e) {
            logger.warning("Error toggling setting '" + setting + "' for " + username + ": " + e.getMessage());
            return false;
        }
    }

    private void sendMessageIfOnline(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        Player player = getBukkitPlayer();
        if (player != null && player.isOnline()) {
            try {
                player.sendMessage(message);
            } catch (Exception e) {
                logger.fine("Failed to send message to player: " + e.getMessage());
            }
        }
    }

    // Paper 1.21.7+ Adventure API support for messages
    private void sendMessageIfOnline(Component message) {
        if (message == null) {
            return;
        }

        Player player = getBukkitPlayer();
        if (player != null && player.isOnline()) {
            try {
                player.sendMessage(message);
            } catch (Exception e) {
                logger.fine("Failed to send component message to player: " + e.getMessage());
            }
        }
    }

    // Enhanced setters with validation
    public void setUsername(String username) {
        this.username = validateUsername(username);
    }

    public void setBankPages(int bankPages) {
        this.bankPages = Math.max(1, Math.min(bankPages, MAX_BANK_PAGES));
    }

    public void setBankGems(int bankGems) {
        this.bankGems = Math.max(MIN_GEMS, Math.min(MAX_GEMS, bankGems));
        if (this.bankGems > highestBankBalance) {
            highestBankBalance = this.bankGems;
        }
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

    // Health setters with proper validation
    public void setHealth(double health) {
        if (Double.isNaN(health) || Double.isInfinite(health) || health < 0) {
            this.health = DEFAULT_HEALTH;
            logger.warning("Invalid health value provided for " + username + ", using default");
        } else {
            this.health = Math.min(health, maxHealth);
        }
    }

    public void setMaxHealth(double maxHealth) {
        if (Double.isNaN(maxHealth) || Double.isInfinite(maxHealth) || maxHealth <= 0) {
            this.maxHealth = DEFAULT_MAX_HEALTH;
            logger.warning("Invalid max health value provided for " + username + ", using default");
        } else {
            // Ensure current health doesn't exceed new max health
            if (this.health > this.maxHealth) {
                this.health = this.maxHealth;
            }
        }
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = Math.max(0, Math.min(20, foodLevel));
    }

    public void setSaturation(float saturation) {
        if (Float.isNaN(saturation) || Float.isInfinite(saturation)) {
            this.saturation = 5.0f;
        } else {
            this.saturation = Math.max(0, Math.min(20, saturation));
        }
    }

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

    // Collection getters with defensive copying
    public Set<String> getUnlockedChatTags() {
        return new HashSet<>(unlockedChatTags);
    }

    public void setUnlockedChatTags(List<String> tags) {
        unlockedChatTags.clear();
        if (tags != null) {
            unlockedChatTags.addAll(tags);
        }
    }

    public Map<String, Boolean> getNotificationSettings() {
        return new HashMap<>(notificationSettings);
    }

    public Set<String> getCompletedQuests() {
        return new HashSet<>(completedQuests);
    }

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

    public Map<String, Integer> getWorldBossDamage() {
        return new HashMap<>(worldBossDamage);
    }

    public Set<String> getBuddies() {
        return new HashSet<>(buddies);
    }

    public void setBuddies(List<String> buddies) {
        this.buddies.clear();
        if (buddies != null) {
            this.buddies.addAll(buddies);
        }
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

    public UUID getUUID() {
        return uuid;
    }

    public Set<String> getToggleSettings() {
        return new HashSet<>(toggleSettings);
    }

    public void setToggleSettings(Collection<String> settings) {
        toggleSettings.clear();
        if (settings != null) {
            toggleSettings.addAll(settings);
        }
    }

    public void setToggleSettings(HashSet<String> toggleSettings) {
        setToggleSettings((Collection<String>) toggleSettings);
    }

    public Map<String, Integer> getWorldBossKills() {
        return new HashMap<>(worldBossKills);
    }

    public Set<String> getBankAchievements() {
        return new HashSet<>(bankAchievements);
    }

    public Set<String> getBankAuthorizedUsers() {
        return new HashSet<>(bankAuthorizedUsers);
    }

    public List<String> getBankAccessLog() {
        return new ArrayList<>(bankAccessLog);
    }

    public List<String> getBankUpgradesPurchased() {
        return new ArrayList<>(bankUpgradesPurchased);
    }

    public Map<Long, String> getBankTransactions() {
        return new HashMap<>(bankTransactions);
    }

    public Map<String, Integer> getScrapStorage() {
        return new HashMap<>(scrapStorage);
    }

    public Map<String, Integer> getScrapTierLimits() {
        return new HashMap<>(scrapTierLimits);
    }

    // Combat logout state enum
    public enum CombatLogoutState {
        NONE,
        PROCESSING,
        PROCESSED,
        COMPLETED
    }

    // Item serializer - Enhanced for absolute reliability
    public static class ItemSerializer {
        private static final Logger logger = YakRealms.getInstance().getLogger();

        public static String serializeItemStacksWithValidation(ItemStack[] items, String context) {
            if (items == null) {
                logger.fine("Serializing null items array for context: " + context);
                return "";
            }

            try {
                logger.fine("Starting serialization for " + context + " with " + items.length + " slots");

                // Create validated copy with error handling
                ItemStack[] validatedItems = new ItemStack[items.length];
                int validItemCount = 0;

                for (int i = 0; i < items.length; i++) {
                    ItemStack item = items[i];
                    if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                        try {
                            if (isValidItemStack(item)) {
                                validatedItems[i] = item.clone();
                                validItemCount++;
                            } else {
                                logger.fine("Invalid item at slot " + i + " in " + context + ": " + item.getType());
                                validatedItems[i] = null;
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to validate item at slot " + i + " in " + context + ": " + e.getMessage());
                            validatedItems[i] = null;
                        }
                    } else {
                        validatedItems[i] = null;
                    }
                }

                // Multiple serialization attempts
                String result = attemptSerialization(validatedItems, context);
                if (result != null) {
                    logger.fine("Serialized " + validItemCount + " items for " + context + " (total slots: " + items.length + ")");
                    return result;
                }

                // Fallback to safe empty serialization
                logger.warning("All serialization attempts failed for " + context + ", using empty fallback");
                return createEmptySerializedArray(items.length);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Serialization completely failed for context: " + context, e);
                return createEmptySerializedArray(items != null ? items.length : 0);
            }
        }

        private static String attemptSerialization(ItemStack[] items, String context) {
            // Primary attempt - standard Bukkit serialization
            try {
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                     BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

                    dataOutput.writeObject(items);
                    return Base64.getEncoder().encodeToString(outputStream.toByteArray());
                }
            } catch (Exception e) {
                logger.warning("Primary serialization failed for " + context + ": " + e.getMessage());
            }

            // Secondary attempt - item by item serialization
            try {
                return serializeItemByItem(items);
            } catch (Exception e) {
                logger.warning("Secondary serialization failed for " + context + ": " + e.getMessage());
            }

            // Tertiary attempt - material only serialization
            try {
                return serializeMaterialOnly(items);
            } catch (Exception e) {
                logger.warning("Tertiary serialization failed for " + context + ": " + e.getMessage());
            }

            return null;
        }

        private static String serializeItemByItem(ItemStack[] items) throws Exception {
            StringBuilder result = new StringBuilder();
            result.append(items.length).append(";");

            for (int i = 0; i < items.length; i++) {
                ItemStack item = items[i];
                if (item != null && item.getType() != Material.AIR) {
                    result.append(i).append(":").append(item.getType().name()).append(":").append(item.getAmount()).append(",");
                }
            }

            return Base64.getEncoder().encodeToString(result.toString().getBytes());
        }

        private static String serializeMaterialOnly(ItemStack[] items) throws Exception {
            StringBuilder result = new StringBuilder();
            result.append("MATERIAL_ONLY:").append(items.length).append(";");

            for (int i = 0; i < items.length; i++) {
                ItemStack item = items[i];
                if (item != null && item.getType() != Material.AIR) {
                    result.append(i).append(":").append(item.getType().name()).append(",");
                }
            }

            return Base64.getEncoder().encodeToString(result.toString().getBytes());
        }

        private static boolean isValidItemStack(ItemStack item) {
            if (item == null || item.getType() == Material.AIR) {
                return false;
            }

            if (item.getAmount() <= 0 || item.getAmount() > item.getMaxStackSize()) {
                return false;
            }

            try {
                // Test if item can be cloned
                item.clone();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private static String createEmptySerializedArray(int size) {
            try {
                ItemStack[] emptyArray = new ItemStack[size];
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                     BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

                    dataOutput.writeObject(emptyArray);
                    return Base64.getEncoder().encodeToString(outputStream.toByteArray());
                }
            } catch (Exception e) {
                logger.severe("Failed to create empty serialized array: " + e.getMessage());
                return "";
            }
        }

        public static ItemStack[] deserializeItemStacksWithValidation(String data) {
            if (data == null || data.trim().isEmpty()) {
                logger.fine("Deserializing null or empty data");
                return new ItemStack[0];
            }

            // Try multiple deserialization methods
            ItemStack[] result = tryStandardDeserialization(data);
            if (result != null) return result;

            result = tryItemByItemDeserialization(data);
            if (result != null) return result;

            result = tryMaterialOnlyDeserialization(data);
            if (result != null) return result;

            logger.warning("All deserialization methods failed, returning empty array");
            return new ItemStack[0];
        }

        private static ItemStack[] tryStandardDeserialization(String data) {
            try {
                byte[] decodedData = Base64.getDecoder().decode(data);
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decodedData);
                     BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

                    Object obj = dataInput.readObject();
                    if (obj instanceof ItemStack[] items) {
                        return validateAndCleanItemArray(items);
                    }
                }
            } catch (Exception e) {
                logger.fine("Standard deserialization failed: " + e.getMessage());
            }
            return null;
        }

        private static ItemStack[] tryItemByItemDeserialization(String data) {
            try {
                String decoded = new String(Base64.getDecoder().decode(data));
                if (decoded.startsWith("MATERIAL_ONLY:")) {
                    return null; // Handle in next method
                }

                String[] parts = decoded.split(";");
                int length = Integer.parseInt(parts[0]);
                ItemStack[] items = new ItemStack[length];

                if (parts.length > 1 && !parts[1].isEmpty()) {
                    String[] itemData = parts[1].split(",");
                    for (String itemInfo : itemData) {
                        if (!itemInfo.isEmpty()) {
                            String[] itemParts = itemInfo.split(":");
                            if (itemParts.length >= 3) {
                                int slot = Integer.parseInt(itemParts[0]);
                                Material material = Material.valueOf(itemParts[1]);
                                int amount = Integer.parseInt(itemParts[2]);

                                if (slot >= 0 && slot < length) {
                                    items[slot] = new ItemStack(material, amount);
                                }
                            }
                        }
                    }
                }

                return items;
            } catch (Exception e) {
                logger.fine("Item by item deserialization failed: " + e.getMessage());
            }
            return null;
        }

        private static ItemStack[] tryMaterialOnlyDeserialization(String data) {
            try {
                String decoded = new String(Base64.getDecoder().decode(data));
                if (!decoded.startsWith("MATERIAL_ONLY:")) {
                    return null;
                }

                String content = decoded.substring("MATERIAL_ONLY:".length());
                String[] parts = content.split(";");
                int length = Integer.parseInt(parts[0]);
                ItemStack[] items = new ItemStack[length];

                if (parts.length > 1 && !parts[1].isEmpty()) {
                    String[] itemData = parts[1].split(",");
                    for (String itemInfo : itemData) {
                        if (!itemInfo.isEmpty()) {
                            String[] itemParts = itemInfo.split(":");
                            if (itemParts.length >= 2) {
                                int slot = Integer.parseInt(itemParts[0]);
                                Material material = Material.valueOf(itemParts[1]);

                                if (slot >= 0 && slot < length) {
                                    items[slot] = new ItemStack(material, 1);
                                }
                            }
                        }
                    }
                }

                logger.info("Recovered inventory using material-only deserialization");
                return items;
            } catch (Exception e) {
                logger.fine("Material only deserialization failed: " + e.getMessage());
            }
            return null;
        }

        private static ItemStack[] validateAndCleanItemArray(ItemStack[] items) {
            if (items == null) return new ItemStack[0];

            ItemStack[] cleaned = new ItemStack[items.length];
            int validItems = 0;

            for (int i = 0; i < items.length; i++) {
                ItemStack item = items[i];
                if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                    try {
                        if (isValidItemStack(item)) {
                            cleaned[i] = item.clone();
                            validItems++;
                        } else {
                            cleaned[i] = null;
                        }
                    } catch (Exception e) {
                        logger.fine("Failed to validate item at index " + i + ": " + e.getMessage());
                        cleaned[i] = null;
                    }
                } else {
                    cleaned[i] = null;
                }
            }

            logger.fine("Validated item array: " + validItems + " valid items out of " + items.length + " slots");
            return cleaned;
        }

        public static String serializeItemStackWithValidation(ItemStack item, String context) {
            if (item == null || item.getType() == Material.AIR) return "";
            return serializeItemStacksWithValidation(new ItemStack[]{item}, context);
        }

        public static ItemStack deserializeItemStackWithValidation(String data) {
            if (data == null || data.isEmpty()) return null;
            ItemStack[] items = deserializeItemStacksWithValidation(data);
            return (items != null && items.length > 0) ? items[0] : null;
        }
    }

    // Utility classes
    private static class PotionEffectSerializer {
        public static String serialize(PotionEffect effect) {
            if (effect == null) return null;
            try {
                return effect.getType().getKey().getKey() + ":" + effect.getAmplifier() + ":" +
                        effect.getDuration() + ":" + effect.isAmbient() + ":" + effect.hasParticles() +
                        ":" + effect.hasIcon();
            } catch (Exception e) {
                logger.warning("Failed to serialize potion effect: " + e.getMessage());
                return null;
            }
        }

        public static PotionEffect deserialize(String data) {
            if (data == null || data.isEmpty()) return null;
            try {
                String[] parts = data.split(":");
                if (parts.length != 6) {
                    logger.warning("Invalid potion effect data format: " + data);
                    return null;
                }

                PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(parts[0]));
                if (type == null) {
                    logger.warning("Unknown potion effect type: " + parts[0]);
                    return null;
                }

                return new PotionEffect(type,
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[1]),
                        Boolean.parseBoolean(parts[3]),
                        Boolean.parseBoolean(parts[4]),
                        Boolean.parseBoolean(parts[5]));
            } catch (Exception e) {
                logger.warning("Failed to deserialize potion effect: " + data + " - " + e.getMessage());
                return null;
            }
        }
    }
}