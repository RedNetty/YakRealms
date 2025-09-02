package com.rednetty.server.core.mechanics.player;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.chat.ChatTag;
import com.rednetty.server.core.mechanics.player.moderation.Rank;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Streamlined YakPlayer - Simplified data model with reliable inventory persistence
 *
 * KEY SIMPLIFICATIONS:
 * 1. Single inventory update method - no "force" variants
 * 2. No complex state checking during inventory operations
 * 3. Simplified combat logout state management
 * 4. Better error handling with fallbacks
 * 5. Removed duplicate coordination logic
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

    @Expose @SerializedName("last_save") @BsonProperty("last_save")
    private long lastSave = 0;

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

    // SIMPLIFIED Inventory persistence - single method, no complex state checking
    @Expose @SerializedName("inventory_contents") @BsonProperty("inventory_contents")
    private String serializedInventory;

    @Expose @SerializedName("armor_contents") @BsonProperty("armor_contents")
    private String serializedArmor;

    @Expose @SerializedName("ender_chest_contents") @BsonProperty("ender_chest_contents")
    private String serializedEnderChest;

    @Expose @SerializedName("offhand_item") @BsonProperty("offhand_item")
    private String serializedOffhand;

    @Expose @SerializedName("inventory_save_timestamp") @BsonProperty("inventory_save_timestamp")
    private long inventorySaveTimestamp = 0;

    // Respawn items (for death system)
    @Expose @SerializedName("respawn_items") @BsonProperty("respawn_items")
    private String serializedRespawnItems;

    @Expose @SerializedName("respawn_item_count") @BsonProperty("respawn_item_count")
    private int respawnItemCount = 0;

    @Expose @SerializedName("death_timestamp") @BsonProperty("death_timestamp")
    private long deathTimestamp = 0;

    // SIMPLIFIED Combat logout state - basic enum, no complex coordination
    @Expose @SerializedName("combat_logout_state") @BsonProperty("combat_logout_state")
    private CombatLogoutState combatLogoutState = CombatLogoutState.NONE;

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

    // SIMPLIFIED Transient fields - no complex state management
    private transient final Map<String, Object> temporaryData = new ConcurrentHashMap<>();
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

        // Initialize with current inventory state
        updateInventory(player);
        updateStats(player);

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

    // ==================== SIMPLIFIED INVENTORY MANAGEMENT ====================

    /**
     * SINGLE inventory update method - reliable and simple
     * No complex state checking, no "force" variants
     */
    public void updateInventory(Player player) {
        if (player == null || !player.isOnline()) {
            logger.warning("Cannot update inventory for null or offline player");
            return;
        }

        try {
            logger.fine("Updating inventory for player: " + player.getName());

            // Update location first
            Location location = player.getLocation();
            if (location != null && location.getWorld() != null) {
                updateLocation(location);
            }

            // Get current inventory state
            ItemStack[] inventoryContents = player.getInventory().getContents();
            ItemStack[] armorContents = player.getInventory().getArmorContents();
            ItemStack[] enderContents = player.getEnderChest().getContents();
            ItemStack offhandItem = player.getInventory().getItemInOffHand();

            // Serialize with validation
            this.serializedInventory = ItemSerializer.serializeItemStacks(inventoryContents);
            this.serializedArmor = ItemSerializer.serializeItemStacks(armorContents);
            this.serializedEnderChest = ItemSerializer.serializeItemStacks(enderContents);
            this.serializedOffhand = ItemSerializer.serializeItemStack(offhandItem);
            this.inventorySaveTimestamp = System.currentTimeMillis();

            logger.fine("Inventory update completed for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Inventory update failed for " + player.getName(), e);
        }
    }

    /**
     * SINGLE inventory application method - reliable and simple
     */
    public void applyInventory(Player player) {
        if (player == null || !player.isOnline()) {
            logger.warning("Cannot apply inventory to null or offline player");
            return;
        }

        try {
            logger.fine("Applying inventory to player: " + player.getName());

            // Clear inventory first
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getEnderChest().clear();
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

            // Apply inventory components
            applyMainInventory(player);
            applyArmor(player);
            applyEnderChest(player);
            applyOffhand(player);

            player.updateInventory();
            logger.fine("Inventory application completed for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Inventory application failed for " + player.getName(), e);
            // Apply default inventory on failure
            applyDefaultInventory(player);
        }
    }

    private void applyMainInventory(Player player) {
        if (serializedInventory == null || serializedInventory.isEmpty()) {
            return;
        }

        try {
            ItemStack[] contents = ItemSerializer.deserializeItemStacks(serializedInventory);
            if (contents != null) {
                int inventorySize = player.getInventory().getSize();
                for (int i = 0; i < contents.length && i < inventorySize; i++) {
                    if (contents[i] != null && contents[i].getType() != Material.AIR) {
                        player.getInventory().setItem(i, contents[i]);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying main inventory for " + player.getName(), e);
        }
    }

    private void applyArmor(Player player) {
        if (serializedArmor == null || serializedArmor.isEmpty()) {
            return;
        }

        try {
            ItemStack[] armor = ItemSerializer.deserializeItemStacks(serializedArmor);
            if (armor != null && armor.length >= 4) {
                player.getInventory().setArmorContents(armor);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying armor for " + player.getName(), e);
        }
    }

    private void applyEnderChest(Player player) {
        if (serializedEnderChest == null || serializedEnderChest.isEmpty()) {
            return;
        }

        try {
            ItemStack[] enderContents = ItemSerializer.deserializeItemStacks(serializedEnderChest);
            if (enderContents != null) {
                for (int i = 0; i < enderContents.length && i < 27; i++) {
                    if (enderContents[i] != null && enderContents[i].getType() != Material.AIR) {
                        player.getEnderChest().setItem(i, enderContents[i]);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying ender chest for " + player.getName(), e);
        }
    }

    private void applyOffhand(Player player) {
        if (serializedOffhand == null || serializedOffhand.isEmpty()) {
            return;
        }

        try {
            ItemStack offhandItem = ItemSerializer.deserializeItemStack(serializedOffhand);
            if (offhandItem != null) {
                player.getInventory().setItemInOffHand(offhandItem);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying offhand for " + player.getName(), e);
        }
    }

    private void applyDefaultInventory(Player player) {
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getEnderChest().clear();
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.updateInventory();
            logger.info("Applied default inventory for " + player.getName());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying default inventory", e);
        }
    }

    // ==================== RESPAWN ITEMS (For Death System) ====================

    public boolean setRespawnItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            clearRespawnItems();
            return true;
        }

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
            this.serializedRespawnItems = ItemSerializer.serializeItemStacks(
                    validItems.toArray(new ItemStack[0]));
            this.respawnItemCount = validItems.size();
            this.deathTimestamp = System.currentTimeMillis();
            logger.info("Stored " + validItems.size() + " respawn items for " + username);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to serialize respawn items for " + username, e);
            return false;
        }
    }

    public List<ItemStack> getRespawnItems() {
        if (serializedRespawnItems == null || serializedRespawnItems.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            ItemStack[] items = ItemSerializer.deserializeItemStacks(serializedRespawnItems);
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

    public boolean hasRespawnItems() {
        return serializedRespawnItems != null && !serializedRespawnItems.isEmpty() && respawnItemCount > 0;
    }

    public void clearRespawnItems() {
        this.serializedRespawnItems = null;
        this.respawnItemCount = 0;
        this.deathTimestamp = 0;
    }

    // ==================== SIMPLIFIED COMBAT LOGOUT STATE ====================

    public void setCombatLogoutState(CombatLogoutState newState) {
        if (newState == null) {
            newState = CombatLogoutState.NONE;
        }

        CombatLogoutState oldState = this.combatLogoutState;
        this.combatLogoutState = newState;

        logger.fine("Combat logout state transition for " + username + ": " + oldState + " -> " + newState);
    }

    public boolean isInCombatLogoutState() {
        return combatLogoutState != null && combatLogoutState != CombatLogoutState.NONE;
    }

    // ==================== CONNECTION MANAGEMENT ====================

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
                updateStats(bukkitPlayer);
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

    // ==================== LOCATION MANAGEMENT ====================

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

    // ==================== STATS MANAGEMENT ====================

    public void updateStats(Player player) {
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

    public void applyStats(Player player, boolean skipGameMode) {
        if (player == null) {
            logger.warning("Cannot apply stats to null player");
            return;
        }

        try {
            // Enhanced health application
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
                        try {
                            player.setHealth(player.getMaxHealth());
                        } catch (Exception e2) {
                            logger.log(Level.SEVERE, "Critical health application failure for " + player.getName(), e2);
                        }
                    }
                }
            }, 2L);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in health application for " + player.getName(), e);
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

    // ==================== SOCIAL SYSTEM ====================

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

    // ==================== DISPLAY FORMATTING ====================

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

    // ==================== UTILITY METHODS ====================

    public boolean isOnline() {
        return bukkitPlayer != null && bukkitPlayer.isOnline();
    }

    public void clearTemporaryData() {
        temporaryData.clear();
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

    // ==================== ENHANCED SETTERS WITH VALIDATION ====================

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

    // ==================== ALIGNMENT SYSTEM METHODS ====================

    public String getAlignment() {
        return alignment != null ? alignment : "LAWFUL";
    }

    public long getChaoticTime() {
        return chaoticTime;
    }

    public void setChaoticTime(long chaoticTime) {
        this.chaoticTime = chaoticTime;
    }

    public long getNeutralTime() {
        return neutralTime;
    }

    public void setNeutralTime(long neutralTime) {
        this.neutralTime = neutralTime;
    }

    public void setChatTag(String chatTag) {
        this.chatTag = chatTag != null ? chatTag : "DEFAULT";
    }

    public void setGuildName(String guildName) {
        this.guildName = guildName != null ? guildName : "";
    }

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
            this.maxHealth = maxHealth;
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

    // ==================== TEMPORARY DATA MANAGEMENT ====================

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

    // ==================== COLLECTION GETTERS WITH DEFENSIVE COPYING ====================

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

    public Map<String, Integer> getWorldBossKills() {
        return new HashMap<>(worldBossKills);
    }

    // ==================== BANK UTILITY METHODS ====================

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

    // ==================== MISSING METHODS ====================
    
    public String getRank() {
        return rank;
    }
    
    public String getUsername() {
        return username;
    }
    
    public boolean isBanned() {
        return banned;
    }
    
    public void setBanned(boolean banned) {
        this.banned = banned;
    }
    
    // ==================== ADDITIONAL MISSING GETTERS ====================
    
    public int getBankGems() {
        return bankGems;
    }
    
    public Player getBukkitPlayer() {
        return Bukkit.getPlayer(uuid);
    }
    
    public int getLevel() {
        return level;
    }
    
    public long getTotalPlaytime() {
        return totalPlaytime;
    }
    
    // Mount-related methods for MountManager
    public int getHorseTier() {
        return 0; // Default value - would need proper implementation
    }
    
    public void setHorseTier(int tier) {
        // Implementation would store tier in player data
    }
    
    public String getHorseName() {
        return ""; // Default value - would need proper implementation  
    }
    
    public void setHorseName(String name) {
        // Implementation would store name in player data
    }
    
    public void setBanExpiry(long banExpiry) {
        this.banExpiry = banExpiry;
    }
    
    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    // ==================== COMBAT LOGOUT STATE ENUM ====================

    public enum CombatLogoutState {
        NONE,           // Not in combat logout
        PROCESSING,     // Currently processing combat logout
        PROCESSED,      // Combat logout complete, ready for rejoin
        COMPLETED       // Rejoin completed
    }

    // ==================== SIMPLIFIED ITEM SERIALIZER ====================

    public static class ItemSerializer {
        private static final Logger logger = YakRealms.getInstance().getLogger();

        public static String serializeItemStacks(ItemStack[] items) {
            if (items == null) {
                return "";
            }

            try {
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                     BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

                    dataOutput.writeObject(items);
                    return Base64.getEncoder().encodeToString(outputStream.toByteArray());
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to serialize item stacks", e);
                return "";
            }
        }

        public static ItemStack[] deserializeItemStacks(String data) {
            if (data == null || data.trim().isEmpty()) {
                return new ItemStack[0];
            }

            try {
                byte[] decodedData = Base64.getDecoder().decode(data);
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decodedData);
                     BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

                    Object obj = dataInput.readObject();
                    if (obj instanceof ItemStack[] items) {
                        return items;
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to deserialize item stacks", e);
            }
            return new ItemStack[0];
        }

        public static String serializeItemStack(ItemStack item) {
            if (item == null || item.getType() == Material.AIR) return "";
            return serializeItemStacks(new ItemStack[]{item});
        }

        public static ItemStack deserializeItemStack(String data) {
            if (data == null || data.isEmpty()) return null;
            ItemStack[] items = deserializeItemStacks(data);
            return (items != null && items.length > 0) ? items[0] : null;
        }
    }

    // ==================== POTION EFFECT SERIALIZER ====================

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