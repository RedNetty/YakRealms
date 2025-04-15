package com.rednetty.server.mechanics.player;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core player data model that stores all player-related information
 * for centralized management and serialization.
 */
public class YakPlayer {
    private static final Logger logger = Logger.getLogger(YakPlayer.class.getName());

    // Basic identification
    @Expose
    @SerializedName("uuid")
    private final UUID uuid;

    @Expose
    @SerializedName("username")
    private String username;

    @Expose
    @SerializedName("last_login")
    private long lastLogin;

    @Expose
    @SerializedName("last_logout")
    private long lastLogout;

    @Expose
    @SerializedName("first_join")
    private long firstJoin;

    @Expose
    @SerializedName("ip_address")
    private String ipAddress;

    // Player progression and stats
    @Expose
    @SerializedName("level")
    private int level = 1;

    @Expose
    @SerializedName("exp")
    private int exp = 0;

    @Expose
    @SerializedName("monster_kills")
    private int monsterKills = 0;

    @Expose
    @SerializedName("player_kills")
    private int playerKills = 0;

    @Expose
    @SerializedName("deaths")
    private int deaths = 0;

    @Expose
    @SerializedName("ore_mined")
    private int oreMined = 0;

    @Expose
    @SerializedName("fish_caught")
    private int fishCaught = 0;

    // Economical data
    @Expose
    @SerializedName("gems")
    private int gems = 0;

    @Expose
    @SerializedName("bank_gems")
    private int bankGems = 0;

    @Expose
    @SerializedName("elite_shards")
    private int eliteShards = 0;

    // Bank system data
    @Expose
    @SerializedName("bank_pages")
    private int bankPages = 1; // Default to 1 page

    @Expose
    @SerializedName("bank_inventory")
    private Map<Integer, String> serializedBankItems = new HashMap<>();

    @Expose
    @SerializedName("bank_authorized_users")
    private List<String> bankAuthorizedUsers = new ArrayList<>();

    // Alignment data
    @Expose
    @SerializedName("alignment")
    private String alignment = "LAWFUL"; // LAWFUL, NEUTRAL, CHAOTIC

    @Expose
    @SerializedName("chaotic_time")
    private long chaoticTime = 0;

    @Expose
    @SerializedName("neutral_time")
    private long neutralTime = 0;

    // Moderation data
    @Expose
    @SerializedName("rank")
    private String rank = "DEFAULT";

    @Expose
    @SerializedName("banned")
    private boolean banned = false;

    @Expose
    @SerializedName("ban_reason")
    private String banReason = "";

    @Expose
    @SerializedName("ban_expiry")
    private long banExpiry = 0;

    @Expose
    @SerializedName("muted")
    private int muteTime = 0;

    // Chat data
    @Expose
    @SerializedName("chat_tag")
    private String chatTag = "DEFAULT";

    @Expose
    @SerializedName("unlocked_chat_tags")
    private List<String> unlockedChatTags = new ArrayList<>();

    // Mount data
    @Expose
    @SerializedName("horse_tier")
    private int horseTier = 0;

    // Guild data
    @Expose
    @SerializedName("guild_name")
    private String guildName = "";

    @Expose
    @SerializedName("guild_rank")
    private String guildRank = "";

    // Toggle preferences
    @Expose
    @SerializedName("toggle_settings")
    private Set<String> toggleSettings = new HashSet<>();

    // Quest data
    @Expose
    @SerializedName("current_quest")
    private String currentQuest = "";

    @Expose
    @SerializedName("quest_progress")
    private int questProgress = 0;

    @Expose
    @SerializedName("completed_quests")
    private List<String> completedQuests = new ArrayList<>();

    // Profession data
    @Expose
    @SerializedName("pickaxe_level")
    private int pickaxeLevel = 0;

    @Expose
    @SerializedName("fishing_level")
    private int fishingLevel = 0;

    @Expose
    @SerializedName("mining_xp")
    private int miningXp = 0;

    @Expose
    @SerializedName("fishing_xp")
    private int fishingXp = 0;

    // PvP stats
    @Expose
    @SerializedName("t1_kills")
    private int t1Kills = 0;

    @Expose
    @SerializedName("t2_kills")
    private int t2Kills = 0;

    @Expose
    @SerializedName("t3_kills")
    private int t3Kills = 0;

    @Expose
    @SerializedName("t4_kills")
    private int t4Kills = 0;

    @Expose
    @SerializedName("t5_kills")
    private int t5Kills = 0;

    @Expose
    @SerializedName("t6_kills")
    private int t6Kills = 0;

    // World Boss tracking
    @Expose
    @SerializedName("world_boss_damage")
    private Map<String, Integer> worldBossDamage = new HashMap<>();

    // Trading data
    @Expose
    @SerializedName("trade_disabled")
    private boolean tradeDisabled = false;

    // Player buddies (friends)
    @Expose
    @SerializedName("buddies")
    private List<String> buddies = new ArrayList<>();

    // Energy system settings
    @Expose
    @SerializedName("energy_disabled")
    private boolean energyDisabled = false;

    // NEW FIELDS: Location data
    @Expose
    @SerializedName("world")
    private String world;

    @Expose
    @SerializedName("location_x")
    private double locationX;

    @Expose
    @SerializedName("location_y")
    private double locationY;

    @Expose
    @SerializedName("location_z")
    private double locationZ;

    @Expose
    @SerializedName("location_yaw")
    private float locationYaw;

    @Expose
    @SerializedName("location_pitch")
    private float locationPitch;

    // NEW FIELDS: Inventory data
    @Expose
    @SerializedName("inventory_contents")
    private String serializedInventory;

    @Expose
    @SerializedName("armor_contents")
    private String serializedArmor;

    @Expose
    @SerializedName("ender_chest_contents")
    private String serializedEnderChest;

    @Expose
    @SerializedName("offhand_item")
    private String serializedOffhand;

    // NEW FIELDS: Player stats
    @Expose
    @SerializedName("health")
    private double health = 20.0;

    @Expose
    @SerializedName("max_health")
    private double maxHealth = 20.0;

    @Expose
    @SerializedName("food_level")
    private int foodLevel = 20;

    @Expose
    @SerializedName("saturation")
    private float saturation = 5.0f;

    @Expose
    @SerializedName("xp_level")
    private int xpLevel = 0;

    @Expose
    @SerializedName("xp_progress")
    private float xpProgress = 0.0f;

    @Expose
    @SerializedName("total_experience")
    private int totalExperience = 0;

    @Expose
    @SerializedName("bed_spawn_location")
    private String bedSpawnLocation;

    @Expose
    @SerializedName("gamemode")
    private String gameMode = "SURVIVAL";

    @Expose
    @SerializedName("active_potion_effects")
    private List<String> activePotionEffects = new ArrayList<>();

    // Non-serialized transient fields
    private transient Player bukkitPlayer;
    private transient boolean inCombat = false;
    private transient long lastCombatTime = 0;
    private transient Map<String, Object> temporaryData = new ConcurrentHashMap<>();
    private transient ItemStack[] respawnItems;

    /**
     * Constructor for creating a new YakPlayer
     *
     * @param player The Bukkit player this data represents
     */
    public YakPlayer(Player player) {
        this.uuid = player.getUniqueId();
        this.username = player.getName();
        this.ipAddress = player.getAddress().getAddress().getHostAddress();
        this.firstJoin = Instant.now().getEpochSecond();
        this.lastLogin = firstJoin;
        this.bukkitPlayer = player;

        // Initialize location
        updateLocation(player.getLocation());

        // Initialize inventory
        updateInventory(player);

        // Initialize stats
        updateStats(player);

        // Initialize energy system
        this.temporaryData.put("energy", 100);
    }

    /**
     * Constructor for loading YakPlayer from storage
     *
     * @param uuid The UUID of the player
     */
    public YakPlayer(UUID uuid) {
        this.uuid = uuid;
        this.bukkitPlayer = Bukkit.getPlayer(uuid);

        // Initialize energy system
        this.temporaryData.put("energy", 100);
    }

    /**
     * Updates the reference to the Bukkit player when they login
     */
    public void connect(Player player) {
        this.bukkitPlayer = player;
        this.username = player.getName();
        this.lastLogin = Instant.now().getEpochSecond();
        this.ipAddress = player.getAddress().getAddress().getHostAddress();

        // Initialize energy level display
        player.setExp(1.0f);
        player.setLevel(100);
    }

    /**
     * Records player logout time and cleans up references
     */
    public void disconnect() {
        this.lastLogout = Instant.now().getEpochSecond();
        this.bukkitPlayer = null;
    }

    /**
     * Checks if the player is currently online
     *
     * @return true if the player is online and has a valid Bukkit player reference
     */
    public boolean isOnline() {
        return bukkitPlayer != null && bukkitPlayer.isOnline();
    }

    /**
     * Gets the Bukkit player object
     *
     * @return The Bukkit player object or null if not online
     */
    public Player getBukkitPlayer() {
        return bukkitPlayer;
    }

    /**
     * Fetches the player's UUID
     *
     * @return The player's UUID
     */
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Gets the player's username
     *
     * @return The player's username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the player's username
     *
     * @param username The new username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the player's last login time
     *
     * @return The last login time in seconds since epoch
     */
    public long getLastLogin() {
        return lastLogin;
    }

    /**
     * Sets the player's last login time
     *
     * @param lastLogin The last login time in seconds since epoch
     */
    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    /**
     * Gets the player's last logout time
     *
     * @return The last logout time in seconds since epoch
     */
    public long getLastLogout() {
        return lastLogout;
    }

    /**
     * Sets the player's last logout time
     *
     * @param lastLogout The last logout time in seconds since epoch
     */
    public void setLastLogout(long lastLogout) {
        this.lastLogout = lastLogout;
    }

    /**
     * Gets the player's first join time
     *
     * @return The first join time in seconds since epoch
     */
    public long getFirstJoin() {
        return firstJoin;
    }

    /**
     * Sets the player's first join time
     *
     * @param firstJoin The first join time in seconds since epoch
     */
    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }

    /**
     * Gets the player's IP address
     *
     * @return The IP address
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Sets the player's IP address
     *
     * @param ipAddress The new IP address
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Gets the player's level
     *
     * @return The player's level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the player's level
     *
     * @param level The new level
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * Gets the player's experience
     *
     * @return The player's experience
     */
    public int getExp() {
        return exp;
    }

    /**
     * Sets the player's experience
     *
     * @param exp The new experience
     */
    public void setExp(int exp) {
        this.exp = exp;
    }

    /**
     * Sets the player's current alignment
     *
     * @param alignment The new alignment (LAWFUL, NEUTRAL, CHAOTIC)
     */
    public void setAlignment(String alignment) {
        this.alignment = alignment;
        if (alignment.equals("NEUTRAL")) {
            this.neutralTime = Instant.now().getEpochSecond();
        } else if (alignment.equals("CHAOTIC")) {
            this.chaoticTime = Instant.now().getEpochSecond();
        }
    }

    /**
     * Gets the player's current alignment
     *
     * @return The player's alignment
     */
    public String getAlignment() {
        return alignment;
    }

    /**
     * Gets the time when player became chaotic
     *
     * @return The chaotic time in seconds since epoch
     */
    public long getChaoticTime() {
        return chaoticTime;
    }

    /**
     * Sets the time when player became chaotic
     *
     * @param chaoticTime The chaotic time in seconds since epoch
     */
    public void setChaoticTime(long chaoticTime) {
        this.chaoticTime = chaoticTime;
    }

    /**
     * Gets the time when player became neutral
     *
     * @return The neutral time in seconds since epoch
     */
    public long getNeutralTime() {
        return neutralTime;
    }

    /**
     * Sets the time when player became neutral
     *
     * @param neutralTime The neutral time in seconds since epoch
     */
    public void setNeutralTime(long neutralTime) {
        this.neutralTime = neutralTime;
    }

    /**
     * Sets the player's rank
     *
     * @param rank The new rank
     */
    public void setRank(Rank rank) {
        this.rank = rank.name();
    }

    /**
     * Sets the player's rank
     *
     * @param rank The new rank as a string
     */
    public void setRank(String rank) {
        this.rank = rank;
    }

    /**
     * Gets the player's rank as an enum
     *
     * @return The player's rank as a Rank
     */
    public String getRank() {
        return rank;
    }

    /**
     * Sets the player's chat tag
     *
     * @param tag The new chat tag
     */
    public void setChatTag(ChatTag tag) {
        this.chatTag = tag.name();
    }

    /**
     * Sets the player's chat tag
     *
     * @param tag The new chat tag as a string
     */
    public void setChatTag(String tag) {
        this.chatTag = tag;
    }

    /**
     * Gets the player's chat tag as a string
     *
     * @return The player's chat tag
     */
    public String getChatTag() {
        return chatTag;
    }

    /**
     * Unlocks a chat tag for the player
     *
     * @param tag The chat tag to unlock
     */
    public void unlockChatTag(ChatTag tag) {
        if (!unlockedChatTags.contains(tag.name())) {
            unlockedChatTags.add(tag.name());
        }
    }

    /**
     * Checks if a chat tag is unlocked for the player
     *
     * @param tag The chat tag to check
     * @return true if the tag is unlocked
     */
    public boolean hasChatTagUnlocked(ChatTag tag) {
        return unlockedChatTags.contains(tag.name());
    }

    /**
     * Gets all unlocked chat tags
     *
     * @return A list of all unlocked chat tags
     */
    public List<String> getUnlockedChatTags() {
        return unlockedChatTags;
    }

    /**
     * Sets the unlocked chat tags
     *
     * @param unlockedChatTags The new unlocked chat tags
     */
    public void setUnlockedChatTags(List<String> unlockedChatTags) {
        this.unlockedChatTags = unlockedChatTags;
    }

    /**
     * Sets the player's gems balance
     *
     * @param gems The new gems balance
     */
    public void setGems(int gems) {
        this.gems = Math.max(0, gems);
    }

    /**
     * Gets the player's gems balance
     *
     * @return The player's gems
     */
    public int getGems() {
        return gems;
    }

    /**
     * Adds gems to the player's balance
     *
     * @param amount The amount to add
     */
    public void addGems(int amount) {
        this.gems += amount;
    }

    /**
     * Removes gems from the player's balance
     *
     * @param amount The amount to remove
     * @return true if the player had enough gems, false otherwise
     */
    public boolean removeGems(int amount) {
        if (gems >= amount) {
            gems -= amount;
            return true;
        }
        return false;
    }

    /**
     * Sets the player's bank gems balance
     *
     * @param gems The new bank gems balance
     */
    public void setBankGems(int gems) {
        this.bankGems = Math.max(0, gems);
    }

    /**
     * Gets the player's bank gems balance
     *
     * @return The player's bank gems
     */
    public int getBankGems() {
        return bankGems;
    }

    /**
     * Adds gems to the player's bank balance
     *
     * @param amount The amount to add
     */
    public void addBankGems(int amount) {
        this.bankGems += amount;
    }

    /**
     * Removes gems from the player's bank balance
     *
     * @param amount The amount to remove
     * @return true if the player had enough bank gems, false otherwise
     */
    public boolean removeBankGems(int amount) {
        if (bankGems >= amount) {
            bankGems -= amount;
            return true;
        }
        return false;
    }

    /**
     * Gets the number of bank pages the player has
     *
     * @return The number of bank pages
     */
    public int getBankPages() {
        return bankPages;
    }

    /**
     * Sets the number of bank pages
     *
     * @param bankPages The new number of bank pages
     */
    public void setBankPages(int bankPages) {
        this.bankPages = bankPages;
    }

    /**
     * Gets the serialized bank items for a specific page
     *
     * @param page The bank page
     * @return The serialized items or null if not set
     */
    public String getSerializedBankItems(int page) {
        return serializedBankItems.getOrDefault(page, null);
    }

    /**
     * Sets the serialized bank items for a specific page
     *
     * @param page       The bank page
     * @param serialized The serialized items
     */
    public void setSerializedBankItems(int page, String serialized) {
        serializedBankItems.put(page, serialized);
    }

    /**
     * Gets all serialized bank items
     *
     * @return Map of page number to serialized items
     */
    public Map<Integer, String> getAllSerializedBankItems() {
        return serializedBankItems;
    }

    /**
     * Gets the list of users authorized to access this bank
     *
     * @return List of authorized user UUIDs as strings
     */
    public List<String> getBankAuthorizedUsers() {
        return bankAuthorizedUsers;
    }

    /**
     * Sets the list of users authorized to access this bank
     *
     * @param users List of authorized user UUIDs as strings
     */
    public void setBankAuthorizedUsers(List<String> users) {
        this.bankAuthorizedUsers = users;
    }

    /**
     * Add a user to the authorized users list
     *
     * @param userUuid The UUID of the user to authorize
     */
    public void addBankAuthorizedUser(UUID userUuid) {
        String uuidStr = userUuid.toString();
        if (!bankAuthorizedUsers.contains(uuidStr)) {
            bankAuthorizedUsers.add(uuidStr);
        }
    }

    /**
     * Remove a user from the authorized users list
     *
     * @param userUuid The UUID of the user to remove
     */
    public void removeBankAuthorizedUser(UUID userUuid) {
        bankAuthorizedUsers.remove(userUuid.toString());
    }

    /**
     * Check if a user is authorized to access this bank
     *
     * @param userUuid The UUID of the user to check
     * @return true if the user is authorized
     */
    public boolean isBankAuthorizedUser(UUID userUuid) {
        return bankAuthorizedUsers.contains(userUuid.toString());
    }

    /**
     * Sets the player's elite shards
     *
     * @param shards The new elite shards amount
     */
    public void setEliteShards(int shards) {
        this.eliteShards = Math.max(0, shards);
    }

    /**
     * Gets the player's elite shards
     *
     * @return The player's elite shards
     */
    public int getEliteShards() {
        return eliteShards;
    }

    /**
     * Adds elite shards to the player
     *
     * @param amount The amount to add
     */
    public void addEliteShards(int amount) {
        this.eliteShards += amount;
    }

    /**
     * Removes elite shards from the player
     *
     * @param amount The amount to remove
     * @return true if the player had enough shards, false otherwise
     */
    public boolean removeEliteShards(int amount) {
        if (eliteShards >= amount) {
            eliteShards -= amount;
            return true;
        }
        return false;
    }

    /**
     * Increases monster kill count
     *
     * @param tier The tier of the monster killed
     */
    public void addMonsterKill(int tier) {
        this.monsterKills++;
        switch (tier) {
            case 1:
                this.t1Kills++;
                break;
            case 2:
                this.t2Kills++;
                break;
            case 3:
                this.t3Kills++;
                break;
            case 4:
                this.t4Kills++;
                break;
            case 5:
                this.t5Kills++;
                break;
            case 6:
                this.t6Kills++;
                break;
        }
    }

    /**
     * Sets monster kills count
     *
     * @param monsterKills The new monster kills count
     */
    public void setMonsterKills(int monsterKills) {
        this.monsterKills = monsterKills;
    }

    /**
     * Gets monster kills of a specific tier
     *
     * @param tier The tier to get kills for
     * @return The number of kills for that tier
     */
    public int getTierKills(int tier) {
        switch (tier) {
            case 1:
                return t1Kills;
            case 2:
                return t2Kills;
            case 3:
                return t3Kills;
            case 4:
                return t4Kills;
            case 5:
                return t5Kills;
            case 6:
                return t6Kills;
            default:
                return 0;
        }
    }

    /**
     * Gets total monster kills
     *
     * @return Total monster kills
     */
    public int getMonsterKills() {
        return monsterKills;
    }

    /**
     * Sets T1 kills
     *
     * @param t1Kills The new T1 kills count
     */
    public void setT1Kills(int t1Kills) {
        this.t1Kills = t1Kills;
    }

    /**
     * Gets T1 kills
     *
     * @return T1 kills count
     */
    public int getT1Kills() {
        return t1Kills;
    }

    /**
     * Sets T2 kills
     *
     * @param t2Kills The new T2 kills count
     */
    public void setT2Kills(int t2Kills) {
        this.t2Kills = t2Kills;
    }

    /**
     * Gets T2 kills
     *
     * @return T2 kills count
     */
    public int getT2Kills() {
        return t2Kills;
    }

    /**
     * Sets T3 kills
     *
     * @param t3Kills The new T3 kills count
     */
    public void setT3Kills(int t3Kills) {
        this.t3Kills = t3Kills;
    }

    /**
     * Gets T3 kills
     *
     * @return T3 kills count
     */
    public int getT3Kills() {
        return t3Kills;
    }

    /**
     * Sets T4 kills
     *
     * @param t4Kills The new T4 kills count
     */
    public void setT4Kills(int t4Kills) {
        this.t4Kills = t4Kills;
    }

    /**
     * Gets T4 kills
     *
     * @return T4 kills count
     */
    public int getT4Kills() {
        return t4Kills;
    }

    /**
     * Sets T5 kills
     *
     * @param t5Kills The new T5 kills count
     */
    public void setT5Kills(int t5Kills) {
        this.t5Kills = t5Kills;
    }

    /**
     * Gets T5 kills
     *
     * @return T5 kills count
     */
    public int getT5Kills() {
        return t5Kills;
    }

    /**
     * Sets T6 kills
     *
     * @param t6Kills The new T6 kills count
     */
    public void setT6Kills(int t6Kills) {
        this.t6Kills = t6Kills;
    }

    /**
     * Gets T6 kills
     *
     * @return T6 kills count
     */
    public int getT6Kills() {
        return t6Kills;
    }

    /**
     * Increments player kills and adds combat time
     */
    public void addPlayerKill() {
        this.playerKills++;
        this.lastCombatTime = Instant.now().getEpochSecond();
        this.inCombat = true;
    }

    /**
     * Sets player kills count
     *
     * @param playerKills The new player kills count
     */
    public void setPlayerKills(int playerKills) {
        this.playerKills = playerKills;
    }

    /**
     * Gets player kills
     *
     * @return Player kill count
     */
    public int getPlayerKills() {
        return playerKills;
    }

    /**
     * Increments death count
     */
    public void addDeath() {
        this.deaths++;
    }

    /**
     * Sets death count
     *
     * @param deaths The new death count
     */
    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    /**
     * Gets death count
     *
     * @return Death count
     */
    public int getDeaths() {
        return deaths;
    }

    /**
     * Adds ore mined count
     *
     * @param amount The amount to add
     */
    public void addOreMined(int amount) {
        this.oreMined += amount;
    }

    /**
     * Gets ore mined count
     *
     * @return Ore mined count
     */
    public int getOreMined() {
        return oreMined;
    }

    /**
     * Sets ore mined count
     *
     * @param oreMined The new ore mined count
     */
    public void setOreMined(int oreMined) {
        this.oreMined = oreMined;
    }

    /**
     * Adds fish caught count
     *
     * @param amount The amount to add
     */
    public void addFishCaught(int amount) {
        this.fishCaught += amount;
    }

    /**
     * Gets fish caught count
     *
     * @return Fish caught count
     */
    public int getFishCaught() {
        return fishCaught;
    }

    /**
     * Sets fish caught count
     *
     * @param fishCaught The new fish caught count
     */
    public void setFishCaught(int fishCaught) {
        this.fishCaught = fishCaught;
    }

    /**
     * Gets the player's pickaxe level
     *
     * @return The pickaxe level
     */
    public int getPickaxeLevel() {
        return pickaxeLevel;
    }

    /**
     * Sets the player's pickaxe level
     *
     * @param pickaxeLevel The new pickaxe level
     */
    public void setPickaxeLevel(int pickaxeLevel) {
        this.pickaxeLevel = pickaxeLevel;
    }

    /**
     * Gets the player's fishing level
     *
     * @return The fishing level
     */
    public int getFishingLevel() {
        return fishingLevel;
    }

    /**
     * Sets the player's fishing level
     *
     * @param fishingLevel The new fishing level
     */
    public void setFishingLevel(int fishingLevel) {
        this.fishingLevel = fishingLevel;
    }

    /**
     * Gets the player's mining experience
     *
     * @return The mining experience
     */
    public int getMiningXp() {
        return miningXp;
    }

    /**
     * Sets the player's mining experience
     *
     * @param miningXp The new mining experience
     */
    public void setMiningXp(int miningXp) {
        this.miningXp = miningXp;
    }

    /**
     * Gets the player's fishing experience
     *
     * @return The fishing experience
     */
    public int getFishingXp() {
        return fishingXp;
    }

    /**
     * Sets the player's fishing experience
     *
     * @param fishingXp The new fishing experience
     */
    public void setFishingXp(int fishingXp) {
        this.fishingXp = fishingXp;
    }

    /**
     * Sets the guild name for the player
     *
     * @param guildName The new guild name
     */
    public void setGuildName(String guildName) {
        this.guildName = guildName;
    }

    /**
     * Gets the player's guild name
     *
     * @return The guild name or an empty string if not in a guild
     */
    public String getGuildName() {
        return guildName;
    }

    /**
     * Checks if the player is in a guild
     *
     * @return true if the player is in a guild
     */
    public boolean isInGuild() {
        return guildName != null && !guildName.isEmpty();
    }

    /**
     * Sets the player's guild rank
     *
     * @param guildRank The new guild rank
     */
    public void setGuildRank(String guildRank) {
        this.guildRank = guildRank;
    }

    /**
     * Gets the player's guild rank
     *
     * @return The guild rank
     */
    public String getGuildRank() {
        return guildRank;
    }

    /**
     * Sets the player's horse tier
     *
     * @param tier The new horse tier
     */
    public void setHorseTier(int tier) {
        this.horseTier = tier;
    }

    /**
     * Gets the player's horse tier
     *
     * @return The horse tier
     */
    public int getHorseTier() {
        return horseTier;
    }

    /**
     * Toggles a setting on or off
     *
     * @param setting The setting to toggle
     * @return true if the setting is now on, false if it's off
     */
    public boolean toggleSetting(String setting) {
        if (toggleSettings.contains(setting)) {
            toggleSettings.remove(setting);
            return false;
        } else {
            toggleSettings.add(setting);
            return true;
        }
    }

    /**
     * Checks if a setting is toggled on
     *
     * @param setting The setting to check
     * @return true if the setting is on
     */
    public boolean isToggled(String setting) {
        return toggleSettings.contains(setting);
    }

    /**
     * Gets all toggled settings
     *
     * @return A Set of all toggled settings
     */
    public Set<String> getToggleSettings() {
        return toggleSettings;
    }

    /**
     * Sets the toggled settings
     *
     * @param toggleSettings The new toggled settings
     */
    public void setToggleSettings(Set<String> toggleSettings) {
        this.toggleSettings = toggleSettings;
    }

    /**
     * Sets the current quest
     *
     * @param questName The name of the quest
     */
    public void setCurrentQuest(String questName) {
        this.currentQuest = questName;
        this.questProgress = 0;
    }

    /**
     * Gets the current quest
     *
     * @return The current quest name
     */
    public String getCurrentQuest() {
        return currentQuest;
    }

    /**
     * Updates quest progress
     *
     * @param amount The amount to add to progress
     */
    public void updateQuestProgress(int amount) {
        this.questProgress += amount;
    }

    /**
     * Gets quest progress
     *
     * @return Current quest progress
     */
    public int getQuestProgress() {
        return questProgress;
    }

    /**
     * Sets quest progress
     *
     * @param questProgress The new quest progress
     */
    public void setQuestProgress(int questProgress) {
        this.questProgress = questProgress;
    }

    /**
     * Completes the current quest
     */
    public void completeQuest() {
        if (!currentQuest.isEmpty() && !completedQuests.contains(currentQuest)) {
            completedQuests.add(currentQuest);
        }
        this.currentQuest = "";
        this.questProgress = 0;
    }

    /**
     * Gets the completed quests
     *
     * @return A list of completed quests
     */
    public List<String> getCompletedQuests() {
        return completedQuests;
    }

    /**
     * Sets the completed quests
     *
     * @param completedQuests The new completed quests
     */
    public void setCompletedQuests(List<String> completedQuests) {
        this.completedQuests = completedQuests;
    }

    /**
     * Checks if a quest has been completed
     *
     * @param questName The quest to check
     * @return true if the quest has been completed
     */
    public boolean hasCompletedQuest(String questName) {
        return completedQuests.contains(questName);
    }

    /**
     * Records damage done to a world boss
     *
     * @param bossName The name of the boss
     * @param damage   The amount of damage done
     */
    public void addWorldBossDamage(String bossName, int damage) {
        worldBossDamage.merge(bossName, damage, Integer::sum);
    }

    /**
     * Gets the damage done to a specific world boss
     *
     * @param bossName The name of the boss
     * @return The amount of damage done
     */
    public int getWorldBossDamage(String bossName) {
        return worldBossDamage.getOrDefault(bossName, 0);
    }

    /**
     * Gets all world boss damage
     *
     * @return A map of boss names to damage done
     */
    public Map<String, Integer> getWorldBossDamage() {
        return worldBossDamage;
    }

    /**
     * Sets the world boss damage
     *
     * @param worldBossDamage The new world boss damage
     */
    public void setWorldBossDamage(Map<String, Integer> worldBossDamage) {
        this.worldBossDamage = worldBossDamage;
    }

    /**
     * Adds a buddy (friend) to the player's buddy list
     *
     * @param buddyName The name of the buddy to add
     */
    public void addBuddy(String buddyName) {
        if (!buddies.contains(buddyName.toLowerCase())) {
            buddies.add(buddyName.toLowerCase());
        }
    }

    /**
     * Removes a buddy from the player's buddy list
     *
     * @param buddyName The name of the buddy to remove
     */
    public void removeBuddy(String buddyName) {
        buddies.remove(buddyName.toLowerCase());
    }

    /**
     * Checks if a player is a buddy
     *
     * @param buddyName The name of the player to check
     * @return true if the player is a buddy
     */
    public boolean isBuddy(String buddyName) {
        return buddies.contains(buddyName.toLowerCase());
    }

    /**
     * Gets all buddies
     *
     * @return A list of all buddies
     */
    public List<String> getBuddies() {
        return buddies;
    }

    /**
     * Sets the buddies list
     *
     * @param buddies The new buddies list
     */
    public void setBuddies(List<String> buddies) {
        this.buddies = buddies;
    }

    /**
     * Sets the player's mute time
     *
     * @param timeInSeconds The mute time in seconds
     */
    public void setMuteTime(int timeInSeconds) {
        this.muteTime = timeInSeconds;
    }

    /**
     * Gets the player's mute time
     *
     * @return The mute time in seconds
     */
    public int getMuteTime() {
        return muteTime;
    }

    /**
     * Decreases the mute time by one second
     */
    public void decreaseMuteTime() {
        if (muteTime > 0) {
            muteTime--;
        }
    }

    /**
     * Checks if the player is muted
     *
     * @return true if the player is muted
     */
    public boolean isMuted() {
        return muteTime > 0;
    }

    /**
     * Sets the player's ban status
     *
     * @param banned Whether the player is banned
     * @param reason The reason for the ban
     * @param expiry The expiry time of the ban in seconds since epoch, 0 for permanent
     */
    public void setBanned(boolean banned, String reason, long expiry) {
        this.banned = banned;
        this.banReason = reason;
        this.banExpiry = expiry;
    }

    /**
     * Sets whether the player is banned
     *
     * @param banned Whether the player is banned
     */
    public void setBanned(boolean banned) {
        this.banned = banned;
    }

    /**
     * Checks if the player is banned
     *
     * @return true if the player is banned
     */
    public boolean isBanned() {
        if (!banned) return false;
        if (banExpiry == 0) return true; // Permanent ban
        return banExpiry > Instant.now().getEpochSecond();
    }

    /**
     * Gets the ban reason
     *
     * @return The ban reason
     */
    public String getBanReason() {
        return banReason;
    }

    /**
     * Sets the ban reason
     *
     * @param banReason The new ban reason
     */
    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    /**
     * Gets the ban expiry time
     *
     * @return The ban expiry time in seconds since epoch
     */
    public long getBanExpiry() {
        return banExpiry;
    }

    /**
     * Sets the ban expiry time
     *
     * @param banExpiry The new ban expiry time in seconds since epoch
     */
    public void setBanExpiry(long banExpiry) {
        this.banExpiry = banExpiry;
    }

    /**
     * Puts the player in combat
     *
     * @param seconds The number of seconds to remain in combat
     */
    public void setInCombat(int seconds) {
        this.inCombat = true;
        this.lastCombatTime = Instant.now().getEpochSecond() + seconds;
    }

    /**
     * Checks if the player is in combat
     *
     * @return true if the player is in combat
     */
    public boolean isInCombat() {
        if (lastCombatTime > Instant.now().getEpochSecond()) {
            return true;
        }
        inCombat = false;
        return false;
    }

    /**
     * Gets the remaining combat time in seconds
     *
     * @return The remaining combat time in seconds
     */
    public int getRemainingCombatTime() {
        long remaining = lastCombatTime - Instant.now().getEpochSecond();
        return remaining > 0 ? (int) remaining : 0;
    }

    /**
     * Sets whether trading is disabled for this player
     *
     * @param disabled Whether trading is disabled
     */
    public void setTradeDisabled(boolean disabled) {
        this.tradeDisabled = disabled;
    }

    /**
     * Checks if trading is disabled for this player
     *
     * @return true if trading is disabled
     */
    public boolean isTradeDisabled() {
        return tradeDisabled;
    }

    /**
     * Sets whether energy system is disabled for this player
     *
     * @param disabled Whether energy is disabled
     */
    public void setEnergyDisabled(boolean disabled) {
        this.energyDisabled = disabled;
    }

    /**
     * Checks if energy system is disabled for this player
     *
     * @return true if energy system is disabled
     */
    public boolean isEnergyDisabled() {
        return energyDisabled;
    }

    /**
     * Stores respawn items for the player
     *
     * @param items The items to store
     */
    public void setRespawnItems(ItemStack[] items) {
        this.respawnItems = items;
    }

    /**
     * Gets the player's respawn items
     *
     * @return The respawn items
     */
    public ItemStack[] getRespawnItems() {
        return respawnItems;
    }

    /**
     * Sets a temporary data value that won't be persisted
     *
     * @param key   The key
     * @param value The value
     */
    public void setTemporaryData(String key, Object value) {
        temporaryData.put(key, value);
    }

    /**
     * Gets a temporary data value
     *
     * @param key The key
     * @return The value or null if not found
     */
    public Object getTemporaryData(String key) {
        return temporaryData.get(key);
    }

    /**
     * Removes a temporary data value
     *
     * @param key The key to remove
     */
    public void removeTemporaryData(String key) {
        temporaryData.remove(key);
    }

    /**
     * Checks if the player has temporary data for a key
     *
     * @param key The key to check
     * @return true if the player has data for this key
     */
    public boolean hasTemporaryData(String key) {
        return temporaryData.containsKey(key);
    }

    /**
     * Gets the time since the player last logged in
     *
     * @return The time in seconds since last login
     */
    public long getTimeSinceLastLogin() {
        return Instant.now().getEpochSecond() - lastLogin;
    }

    public String getFormattedDisplayName() {
        String displayName = "";

        try {
            Rank rankEnum = Rank.fromString(rank);
            ChatTag chatTagEnum = ChatTag.getByName(chatTag);

            // Add chat tag if not default
            if (chatTagEnum != ChatTag.DEFAULT) {
                displayName += chatTagEnum.getTag() + " ";
            }

            // Add rank if not default
            if (rankEnum != Rank.DEFAULT) {
                displayName += rankEnum.tag + " ";
            }
        } catch (IllegalArgumentException e) {
            // Default if an enum value is invalid
        }

        // Add player name
        displayName += getColorByAlignment() + username;

        return ChatColor.translateAlternateColorCodes('&', displayName);
    }

    /**
     * Gets the color based on the player's alignment
     *
     * @return The ChatColor for the alignment
     */
    private ChatColor getColorByAlignment() {
        switch (alignment) {
            case "LAWFUL":
                return ChatColor.GRAY;
            case "NEUTRAL":
                return ChatColor.YELLOW;
            case "CHAOTIC":
                return ChatColor.RED;
            default:
                return ChatColor.GRAY;
        }
    }

    // NEW METHODS FOR LOCATION, INVENTORY, AND STATS

    /**
     * Update player location from Bukkit location
     */
    public void updateLocation(Location location) {
        if (location == null) return;

        this.world = location.getWorld().getName();
        this.locationX = location.getX();
        this.locationY = location.getY();
        this.locationZ = location.getZ();
        this.locationYaw = location.getYaw();
        this.locationPitch = location.getPitch();
    }

    /**
     * Get player location as Bukkit location
     */
    public Location getLocation() {
        if (world == null) return null;

        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) return null;

        return new Location(bukkitWorld, locationX, locationY, locationZ, locationYaw, locationPitch);
    }

    /**
     * Update inventory data from player
     */
    public void updateInventory(Player player) {
        if (player == null) return;

        try {
            this.serializedInventory = serializeItemStacks(player.getInventory().getContents());
            this.serializedArmor = serializeItemStacks(player.getInventory().getArmorContents());
            this.serializedEnderChest = serializeItemStacks(player.getEnderChest().getContents());
            this.serializedOffhand = serializeItemStack(player.getInventory().getItemInOffHand());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating inventory for player " + player.getName(), e);
        }
    }

    /**
     * Update player stats from player
     */
    public void updateStats(Player player) {
        if (player == null) return;

        this.health = player.getHealth();
        this.maxHealth = player.getMaxHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.xpLevel = player.getLevel();
        this.xpProgress = player.getExp();
        this.totalExperience = player.getTotalExperience();
        this.gameMode = player.getGameMode().name();

        // Save bed spawn location if set
        if (player.getBedSpawnLocation() != null) {
            Location bedLoc = player.getBedSpawnLocation();
            this.bedSpawnLocation = bedLoc.getWorld().getName() + ":" +
                    bedLoc.getX() + ":" + bedLoc.getY() + ":" +
                    bedLoc.getZ() + ":" + bedLoc.getYaw() + ":" + bedLoc.getPitch();
        }

        // Save active potion effects
        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(effect.getType().getName() + ":" + effect.getAmplifier() + ":" + effect.getDuration());
        }
        this.activePotionEffects = effects;
    }

    /**
     * Apply saved inventory to player
     */
    public void applyInventory(Player player) {
        if (player == null) return;

        try {
            if (serializedInventory != null) {
                player.getInventory().setContents(deserializeItemStacks(serializedInventory));
            }
            if (serializedArmor != null) {
                player.getInventory().setArmorContents(deserializeItemStacks(serializedArmor));
            }
            if (serializedEnderChest != null) {
                player.getEnderChest().setContents(deserializeItemStacks(serializedEnderChest));
            }
            if (serializedOffhand != null) {
                player.getInventory().setItemInOffHand(deserializeItemStack(serializedOffhand));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying inventory for player " + player.getName(), e);
        }
    }

    /**
     * Apply saved stats to player
     */
    public void applyStats(Player player) {
        if (player == null) return;

        // Set gamemode
        try {
            GameMode mode = GameMode.valueOf(gameMode);
            player.setGameMode(mode);
        } catch (Exception e) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        // Set health (must be after gamemode to avoid health scaling issues)
        player.setMaxHealth(maxHealth);
        player.setHealth(Math.min(health, maxHealth));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setLevel(xpLevel);
        player.setExp(xpProgress);
        player.setTotalExperience(totalExperience);

        // Set bed spawn location if saved
        if (bedSpawnLocation != null && !bedSpawnLocation.isEmpty()) {
            try {
                String[] parts = bedSpawnLocation.split(":");
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    Location bedLoc = new Location(
                            world,
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]),
                            Float.parseFloat(parts[4]),
                            Float.parseFloat(parts[5])
                    );
                    player.setBedSpawnLocation(bedLoc, true);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error setting bed spawn for player " + player.getName(), e);
            }
        }

        // Apply potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        for (String effectStr : activePotionEffects) {
            try {
                String[] parts = effectStr.split(":");
                PotionEffectType type = PotionEffectType.getByName(parts[0]);
                int amplifier = Integer.parseInt(parts[1]);
                int duration = Integer.parseInt(parts[2]);

                if (type != null) {
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error applying potion effect for player " + player.getName(), e);
            }
        }
    }

    /**
     * Serialize an ItemStack to a Base64 string
     */
    private String serializeItemStack(ItemStack item) throws IOException {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        dataOutput.writeObject(item);
        dataOutput.close();

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Deserialize an ItemStack from a Base64 string
     */
    private ItemStack deserializeItemStack(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) {
            return null;
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();

        return item;
    }

    /**
     * Serialize an array of ItemStacks to a Base64 string
     */
    public String serializeItemStacks(ItemStack[] items) throws IOException {
        if (items == null || items.length == 0) {
            return null;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        dataOutput.writeInt(items.length);

        for (ItemStack item : items) {
            dataOutput.writeObject(item);
        }

        dataOutput.close();

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Deserialize an array of ItemStacks from a Base64 string
     */
    public ItemStack[] deserializeItemStacks(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        int length = dataInput.readInt();
        ItemStack[] items = new ItemStack[length];

        for (int i = 0; i < length; i++) {
            items[i] = (ItemStack) dataInput.readObject();
        }

        dataInput.close();

        return items;
    }

    // GETTERS AND SETTERS FOR NEW FIELDS

    // Location data getters/setters
    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getLocationX() {
        return locationX;
    }

    public void setLocationX(double x) {
        this.locationX = x;
    }

    public double getLocationY() {
        return locationY;
    }

    public void setLocationY(double y) {
        this.locationY = y;
    }

    public double getLocationZ() {
        return locationZ;
    }

    public void setLocationZ(double z) {
        this.locationZ = z;
    }

    public float getLocationYaw() {
        return locationYaw;
    }

    public void setLocationYaw(float yaw) {
        this.locationYaw = yaw;
    }

    public float getLocationPitch() {
        return locationPitch;
    }

    public void setLocationPitch(float pitch) {
        this.locationPitch = pitch;
    }

    // Inventory data getters/setters
    public String getSerializedInventory() {
        return serializedInventory;
    }

    public void setSerializedInventory(String serializedInventory) {
        this.serializedInventory = serializedInventory;
    }

    public String getSerializedArmor() {
        return serializedArmor;
    }

    public void setSerializedArmor(String serializedArmor) {
        this.serializedArmor = serializedArmor;
    }

    public String getSerializedEnderChest() {
        return serializedEnderChest;
    }

    public void setSerializedEnderChest(String serializedEnderChest) {
        this.serializedEnderChest = serializedEnderChest;
    }

    public String getSerializedOffhand() {
        return serializedOffhand;
    }

    public void setSerializedOffhand(String serializedOffhand) {
        this.serializedOffhand = serializedOffhand;
    }

    // Player stats getters/setters
    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = foodLevel;
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    public int getXpLevel() {
        return xpLevel;
    }

    public void setXpLevel(int xpLevel) {
        this.xpLevel = xpLevel;
    }

    public float getXpProgress() {
        return xpProgress;
    }

    public void setXpProgress(float xpProgress) {
        this.xpProgress = xpProgress;
    }

    public int getTotalExperience() {
        return totalExperience;
    }

    public void setTotalExperience(int totalExperience) {
        this.totalExperience = totalExperience;
    }

    public String getBedSpawnLocation() {
        return bedSpawnLocation;
    }

    public void setBedSpawnLocation(String bedSpawnLocation) {
        this.bedSpawnLocation = bedSpawnLocation;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public List<String> getActivePotionEffects() {
        return activePotionEffects;
    }

    public void setActivePotionEffects(List<String> activePotionEffects) {
        this.activePotionEffects = activePotionEffects;
    }

    @Override
    public String toString() {
        return "YakPlayer{" +
                "uuid=" + uuid +
                ", username='" + username + '\'' +
                ", rank='" + rank + '\'' +
                ", alignment='" + alignment + '\'' +
                ", world='" + world + '\'' +
                ", location=(" + locationX + "," + locationY + "," + locationZ + ")" +
                ", health=" + health + "/" + maxHealth +
                ", gameMode='" + gameMode + '\'' +
                '}';
    }
}