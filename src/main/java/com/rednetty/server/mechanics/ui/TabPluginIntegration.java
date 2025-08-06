package com.rednetty.server.mechanics.ui;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.moderation.Rank;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.placeholder.PlaceholderManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 *  TAB Plugin Integration for YakRealms
 * Individual placeholders for each stat - DungeonRealms style
 * Each placeholder returns ONE value for ONE row
 */
public class TabPluginIntegration implements Listener {

    // Formatting constants
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##");
    // Color scheme
    private static final String LABEL_COLOR = "§7";
    private static final String VALUE_COLOR = "§f";
    private static final String ACCENT_COLOR = "§e";
    private static final String SUCCESS_COLOR = "§a";
    private static final String WARNING_COLOR = "§c";
    private static TabPluginIntegration instance;
    private final Logger logger;
    private final YakRealms plugin;
    // Tracking
    private final Set<String> registeredPlaceholders = new HashSet<>();
    private TabAPI tabAPI;
    private PlaceholderManager placeholderManager;
    private boolean enabled = false;
    private long initializationTime = 0;

    private TabPluginIntegration() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    public static TabPluginIntegration getInstance() {
        if (instance == null) {
            instance = new TabPluginIntegration();
        }
        return instance;
    }

    /**
     * Initialize TAB plugin integration
     */
    public void initialize() {
        try {
            logger.info("Initializing Individual TAB Placeholders...");
            initializationTime = System.currentTimeMillis();

            if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
                logger.warning("TAB plugin not found - Player stats tablist disabled");
                return;
            }

            tabAPI = TabAPI.getInstance();
            if (tabAPI == null) {
                logger.severe("Failed to get TAB API instance!");
                return;
            }

            placeholderManager = tabAPI.getPlaceholderManager();
            if (placeholderManager == null) {
                logger.severe("Failed to get TAB PlaceholderManager!");
                return;
            }

            // Wait for systems to be ready
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    registerAllIndividualPlaceholders();
                    Bukkit.getPluginManager().registerEvents(this, plugin);

                    enabled = true;
                    long duration = System.currentTimeMillis() - initializationTime;
                    logger.info("Individual TAB placeholders initialized successfully!");
                    logger.info("- Registered " + registeredPlaceholders.size() + " individual placeholders");
                    logger.info("- Each placeholder = one row like DungeonRealms");
                    logger.info("- Initialization took " + duration + "ms");

                } catch (Exception e) {
                    logger.severe("Error during TAB initialization: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 40L);

        } catch (Exception e) {
            logger.severe("Failed to initialize TAB Plugin integration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register individual placeholders - each returns ONE value for ONE row
     */
    private void registerAllIndividualPlaceholders() {
        try {
            logger.info("Registering individual TAB placeholders...");

            // PARTY/GUILD PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_party_size", 2000, this::getPartySize);
            registerPlayerPlaceholder("yakrealms_party_leader", 2000, this::getPartyLeader);
            registerPlayerPlaceholder("yakrealms_party_member_1", 2000, player -> getPartyMember(player, 0));
            registerPlayerPlaceholder("yakrealms_party_member_2", 2000, player -> getPartyMember(player, 1));
            registerPlayerPlaceholder("yakrealms_party_member_3", 2000, player -> getPartyMember(player, 2));
            registerPlayerPlaceholder("yakrealms_party_member_4", 2000, player -> getPartyMember(player, 3));
            registerPlayerPlaceholder("yakrealms_party_member_5", 2000, player -> getPartyMember(player, 4));

            registerPlayerPlaceholder("yakrealms_guild_name", 8000, this::getGuildName);
            registerPlayerPlaceholder("yakrealms_guild_rank", 8000, this::getGuildRank);
            registerPlayerPlaceholder("yakrealms_guild_contribution", 8000, this::getGuildContribution);

            // ECONOMY PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_bank_gems", 3000, this::getBankGems);
            registerPlayerPlaceholder("yakrealms_elite_shards", 3000, this::getEliteShards);
            registerPlayerPlaceholder("yakrealms_bank_pages", 3000, this::getBankPages);

            // GATHERING PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_ore_mined", 5000, this::getOreMined);
            registerPlayerPlaceholder("yakrealms_fish_caught", 5000, this::getFishCaught);
            registerPlayerPlaceholder("yakrealms_blocks_broken", 5000, this::getBlocksBroken);

            // PROFESSION PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_mining_level", 8000, this::getMiningLevel);
            registerPlayerPlaceholder("yakrealms_fishing_level", 8000, this::getFishingLevel);
            registerPlayerPlaceholder("yakrealms_farming_level", 8000, this::getFarmingLevel);
            registerPlayerPlaceholder("yakrealms_woodcutting_level", 8000, this::getWoodcuttingLevel);

            // PLAYER INFO PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_class", 10000, this::getPlayerClass);
            registerPlayerPlaceholder("yakrealms_alignment", 10000, this::getPlayerAlignment);
            registerPlayerPlaceholder("yakrealms_playtime", 10000, this::getPlaytime);

            // COMBAT STAT PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_player_kills", 3000, this::getPlayerKills);
            registerPlayerPlaceholder("yakrealms_deaths", 3000, this::getDeaths);
            registerPlayerPlaceholder("yakrealms_monster_kills", 3000, this::getMonsterKills);
            registerPlayerPlaceholder("yakrealms_kd_ratio", 3000, this::getKDRatio);
            registerPlayerPlaceholder("yakrealms_kill_streak", 3000, this::getKillStreak);
            registerPlayerPlaceholder("yakrealms_pvp_rating", 3000, this::getPvpRating);

            // TIER KILL PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_t1_kills", 8000, this::getT1Kills);
            registerPlayerPlaceholder("yakrealms_t2_kills", 8000, this::getT2Kills);
            registerPlayerPlaceholder("yakrealms_t3_kills", 8000, this::getT3Kills);
            registerPlayerPlaceholder("yakrealms_t4_kills", 8000, this::getT4Kills);
            registerPlayerPlaceholder("yakrealms_t5_kills", 8000, this::getT5Kills);
            registerPlayerPlaceholder("yakrealms_t6_kills", 8000, this::getT6Kills);

            // DAMAGE STAT PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_damage_dealt", 5000, this::getDamageDealt);
            registerPlayerPlaceholder("yakrealms_damage_taken", 5000, this::getDamageTaken);
            registerPlayerPlaceholder("yakrealms_damage_blocked", 5000, this::getDamageBlocked);
            registerPlayerPlaceholder("yakrealms_damage_dodged", 5000, this::getDamageDodged);

            // SOCIAL PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_buddies", 5000, this::getBuddies);
            registerPlayerPlaceholder("yakrealms_chat_tag", 5000, this::getChatTag);
            registerPlayerPlaceholder("yakrealms_unlocked_tags", 5000, this::getUnlockedTags);

            // MOUNT PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_horse_tier", 10000, this::getHorseTier);
            registerPlayerPlaceholder("yakrealms_horse_name", 10000, this::getHorseName);

            // QUEST PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_current_quest", 5000, this::getCurrentQuest);
            registerPlayerPlaceholder("yakrealms_quest_progress", 5000, this::getQuestProgress);
            registerPlayerPlaceholder("yakrealms_completed_quests", 5000, this::getCompletedQuests);
            registerPlayerPlaceholder("yakrealms_quest_points", 5000, this::getQuestPoints);

            // ACHIEVEMENT PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_achievements_unlocked", 10000, this::getAchievementsUnlocked);
            registerPlayerPlaceholder("yakrealms_achievement_points", 10000, this::getAchievementPoints);

            // UTILITY PLACEHOLDERS
            registerPlayerPlaceholder("yakrealms_player_ping", 1000, this::getPlayerPing);
            registerServerPlaceholder("yakrealms_server_info", 5000, this::getServerInfo);

            logger.info("Successfully registered " + registeredPlaceholders.size() + " individual placeholders");

        } catch (Exception e) {
            logger.severe("Error registering individual placeholders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register a player-specific placeholder
     */
    private void registerPlayerPlaceholder(String name, int refreshInterval, PlaceholderFunction function) {
        try {
            String identifier = "%" + name + "%";

            placeholderManager.registerPlayerPlaceholder(identifier, refreshInterval, (tabPlayer) -> {
                try {
                    Player player = getBukkitPlayer(tabPlayer);
                    if (player == null) return "";

                    String result = function.apply(player);
                    return result != null ? result : "";

                } catch (Exception e) {
                    logger.fine("Error in placeholder " + name + ": " + e.getMessage());
                    return "";
                }
            });

            registeredPlaceholders.add(name);

        } catch (Exception e) {
            logger.warning("Failed to register placeholder " + name + ": " + e.getMessage());
        }
    }

    /**
     * Register a server-wide placeholder
     */
    private void registerServerPlaceholder(String name, int refreshInterval, ServerPlaceholderFunction function) {
        try {
            String identifier = "%" + name + "%";

            placeholderManager.registerServerPlaceholder(identifier, refreshInterval, () -> {
                try {
                    String result = function.apply();
                    return result != null ? result : "";
                } catch (Exception e) {
                    logger.warning("Error in server placeholder " + name + ": " + e.getMessage());
                    return "";
                }
            });

            registeredPlaceholders.add(name);

        } catch (Exception e) {
            logger.warning("Failed to register server placeholder " + name + ": " + e.getMessage());
        }
    }

    /**
     * Safely get Bukkit Player from TabPlayer
     */
    private Player getBukkitPlayer(TabPlayer tabPlayer) {
        if (tabPlayer == null) return null;

        try {
            UUID uuid = tabPlayer.getUniqueId();
            if (uuid != null) {
                return Bukkit.getPlayer(uuid);
            }
        } catch (Exception e) {
            logger.fine("Error getting Bukkit player: " + e.getMessage());
        }

        return null;
    }

    // =============================================================================
    // INDIVIDUAL PLACEHOLDER METHODS - ONE VALUE PER METHOD
    // =============================================================================

    // PARTY PLACEHOLDERS
    private String getPartySize(Player player) {
        try {
            PartyMechanics partyMechanics = PartyMechanics.getInstance();
            if (partyMechanics == null || !partyMechanics.isInParty(player)) {
                return LABEL_COLOR + "No Party";
            }

            List<Player> members = partyMechanics.getPartyMembers(player);
            int size = members != null ? members.size() : 0;
            return LABEL_COLOR + "Party Size: " + VALUE_COLOR + size;
        } catch (Exception e) {
            return "";
        }
    }

    private String getPartyLeader(Player player) {
        try {
            PartyMechanics partyMechanics = PartyMechanics.getInstance();
            if (partyMechanics == null || !partyMechanics.isInParty(player)) {
                return "";
            }

            List<Player> members = partyMechanics.getPartyMembers(player);
            if (members == null || members.isEmpty()) return "";

            for (Player member : members) {
                if (partyMechanics.isPartyLeader(member)) {
                    return "§6★ " + getPlayerNameColor(member) + member.getName() + " " + getHealthIndicator(member);
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String getPartyMember(Player player, int index) {
        try {
            PartyMechanics partyMechanics = PartyMechanics.getInstance();
            if (partyMechanics == null || !partyMechanics.isInParty(player)) {
                return "";
            }

            List<Player> members = partyMechanics.getPartyMembers(player);
            if (members == null || members.size() <= index + 1) return ""; // +1 because leader is separate

            // Skip leader (index 0), start from index 1
            int memberIndex = 0;
            for (Player member : members) {
                if (!partyMechanics.isPartyLeader(member)) {
                    if (memberIndex == index) {
                        String prefix = partyMechanics.isPartyOfficer(member) ? ACCENT_COLOR + "♦ " : LABEL_COLOR + "• ";
                        return prefix + getPlayerNameColor(member) + member.getName() + " " + getHealthIndicator(member);
                    }
                    memberIndex++;
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // GUILD PLACEHOLDERS
    private String getGuildName(Player player) {
        try {
            YakPlayer yakPlayer = getYakPlayer(player);
            if (yakPlayer == null) return "";

            if (yakPlayer.isInGuild()) {
                String guildName = yakPlayer.getGuildName();
                return guildName != null ? ACCENT_COLOR + "§l" + guildName : LABEL_COLOR + "Loading...";
            }
            return LABEL_COLOR + "No Guild";
        } catch (Exception e) {
            return LABEL_COLOR + "Guild System Coming Soon!";
        }
    }

    private String getGuildRank(Player player) {
        try {
            YakPlayer yakPlayer = getYakPlayer(player);
            if (yakPlayer == null || !yakPlayer.isInGuild()) return "";

            String rank = yakPlayer.getGuildRank();
            return LABEL_COLOR + "Rank: " + VALUE_COLOR + (rank != null ? rank : "Member");
        } catch (Exception e) {
            return "";
        }
    }

    private String getGuildContribution(Player player) {
        try {
            YakPlayer yakPlayer = getYakPlayer(player);
            if (yakPlayer == null || !yakPlayer.isInGuild()) return "";

            return LABEL_COLOR + "Contribution: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getGuildContribution());
        } catch (Exception e) {
            return "";
        }
    }

    // ECONOMY PLACEHOLDERS
    private String getBankGems(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Bank Gems: " + ACCENT_COLOR + NUMBER_FORMAT.format(yakPlayer.getBankGems());
    }

    private String getEliteShards(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Elite Shards: " + ACCENT_COLOR + NUMBER_FORMAT.format(yakPlayer.getEliteShards());
    }

    private String getBankPages(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Bank Pages: " + VALUE_COLOR + yakPlayer.getBankPages() + "/10";
    }

    // GATHERING PLACEHOLDERS
    private String getOreMined(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Ore Mined: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getOreMined());
    }

    private String getFishCaught(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Fish Caught: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getFishCaught());
    }

    private String getBlocksBroken(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Blocks Broken: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getBlocksBroken());
    }

    // PROFESSION PLACEHOLDERS
    private String getMiningLevel(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Mining: " + SUCCESS_COLOR + "Lvl " + yakPlayer.getPickaxeLevel();
    }

    private String getFishingLevel(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Fishing: " + SUCCESS_COLOR + "Lvl " + yakPlayer.getFishingLevel();
    }

    private String getFarmingLevel(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Farming: " + SUCCESS_COLOR + "Lvl " + yakPlayer.getFarmingLevel();
    }

    private String getWoodcuttingLevel(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Woodcutting: " + SUCCESS_COLOR + "Lvl " + yakPlayer.getWoodcuttingLevel();
    }

    // PLAYER INFO PLACEHOLDERS
    private String getPlayerClass(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Rank: " + getRankDisplay(yakPlayer.getRank());
    }

    private String getPlayerAlignment(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Alignment: " + getAlignmentDisplay(yakPlayer.getAlignment());
    }

    private String getPlaytime(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Playtime: " + VALUE_COLOR + formatTime(yakPlayer.getTotalPlaytime());
    }

    // COMBAT STAT PLACEHOLDERS
    private String getPlayerKills(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Player Kills: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getPlayerKills());
    }

    private String getDeaths(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Deaths: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getDeaths());
    }

    private String getMonsterKills(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Monster Kills: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getMonsterKills());
    }

    private String getKDRatio(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        double kdr = yakPlayer.getDeaths() > 0 ? (double) yakPlayer.getPlayerKills() / yakPlayer.getDeaths() : yakPlayer.getPlayerKills();
        return LABEL_COLOR + "K/D Ratio: " + ACCENT_COLOR + DECIMAL_FORMAT.format(kdr);
    }

    private String getKillStreak(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Kill Streak: " + VALUE_COLOR + yakPlayer.getKillStreak();
    }

    private String getPvpRating(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "PvP Rating: " + VALUE_COLOR + yakPlayer.getPvpRating();
    }

    // TIER KILL PLACEHOLDERS
    private String getT1Kills(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "T1 Kills: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getT1Kills());
    }

    private String getT2Kills(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "T2 Kills: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getT2Kills());
    }

    private String getT3Kills(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "T3 Kills: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getT3Kills());
    }

    private String getT4Kills(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "T4 Kills: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getT4Kills());
    }

    private String getT5Kills(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "T5 Kills: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getT5Kills());
    }

    private String getT6Kills(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "T6 Kills: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getT6Kills());
    }

    // DAMAGE STAT PLACEHOLDERS
    private String getDamageDealt(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Damage Dealt: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getDamageDealt());
    }

    private String getDamageTaken(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Damage Taken: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getDamageTaken());
    }

    private String getDamageBlocked(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Damage Blocked: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getDamageBlocked());
    }

    private String getDamageDodged(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Damage Dodged: " + VALUE_COLOR + NUMBER_FORMAT.format(yakPlayer.getDamageDodged());
    }

    // SOCIAL PLACEHOLDERS
    private String getBuddies(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Buddies: " + VALUE_COLOR + yakPlayer.getBuddies().size() + "/50";
    }

    private String getChatTag(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Chat Tag: " + ACCENT_COLOR + yakPlayer.getChatTag();
    }

    private String getUnlockedTags(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Unlocked Tags: " + VALUE_COLOR + yakPlayer.getUnlockedChatTags().size();
    }

    // MOUNT PLACEHOLDERS
    private String getHorseTier(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";

        if (yakPlayer.getHorseTier() > 0) {
            return LABEL_COLOR + "Horse Tier: " + ACCENT_COLOR + yakPlayer.getHorseTier();
        }
        return LABEL_COLOR + "No Mount";
    }

    private String getHorseName(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null || yakPlayer.getHorseTier() <= 0) return "";

        String horseName = yakPlayer.getHorseName();
        return LABEL_COLOR + "Horse Name: " + VALUE_COLOR + (horseName != null && !horseName.isEmpty() ? horseName : "Unnamed");
    }

    // QUEST PLACEHOLDERS
    private String getCurrentQuest(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";

        String currentQuest = yakPlayer.getCurrentQuest();
        if (currentQuest != null && !currentQuest.isEmpty()) {
            return LABEL_COLOR + "Current: " + ACCENT_COLOR + currentQuest;
        }
        return LABEL_COLOR + "No Active Quest";
    }

    private String getQuestProgress(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";

        String currentQuest = yakPlayer.getCurrentQuest();
        if (currentQuest != null && !currentQuest.isEmpty()) {
            return LABEL_COLOR + "Progress: " + VALUE_COLOR + yakPlayer.getQuestProgress();
        }
        return "";
    }

    private String getCompletedQuests(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Completed: " + VALUE_COLOR + yakPlayer.getCompletedQuests().size();
    }

    private String getQuestPoints(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Quest Points: " + ACCENT_COLOR + NUMBER_FORMAT.format(yakPlayer.getQuestPoints());
    }

    // ACHIEVEMENT PLACEHOLDERS
    private String getAchievementsUnlocked(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Unlocked: " + VALUE_COLOR + yakPlayer.getAchievements().size();
    }

    private String getAchievementPoints(Player player) {
        YakPlayer yakPlayer = getYakPlayer(player);
        if (yakPlayer == null) return "";
        return LABEL_COLOR + "Points: " + ACCENT_COLOR + NUMBER_FORMAT.format(yakPlayer.getAchievementPoints());
    }

    // UTILITY PLACEHOLDERS
    private String getPlayerPing(Player player) {
        if (player == null) return "0";
        try {
            return String.valueOf(player.getPing());
        } catch (Exception e) {
            return "0";
        }
    }

    private String getServerInfo() {
        return "§6YakRealms v1.0.0";
    }

    // =============================================================================
    // UTILITY METHODS
    // =============================================================================

    private YakPlayer getYakPlayer(Player player) {
        if (player == null) return null;

        try {
            YakPlayerManager manager = YakPlayerManager.getInstance();
            if (manager == null) return null;
            return manager.getPlayer(player);
        } catch (Exception e) {
            return null;
        }
    }

    private String getHealthIndicator(Player player) {
        try {
            double healthPercent = player.getHealth() / player.getMaxHealth();
            String color = healthPercent > 0.75 ? SUCCESS_COLOR : healthPercent > 0.5 ? ACCENT_COLOR :
                    healthPercent > 0.25 ? WARNING_COLOR : "§4";
            return color + "❤";
        } catch (Exception e) {
            return SUCCESS_COLOR + "❤";
        }
    }

    private String getPlayerNameColor(Player player) {
        try {
            Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());
            if (rank != null && rank != Rank.DEFAULT) {
                return switch (rank) {
                    case DEV -> "§6";
                    case MANAGER -> ACCENT_COLOR;
                    case GM -> "§b";
                    default -> VALUE_COLOR;
                };
            }

            YakPlayer yakPlayer = getYakPlayer(player);
            if (yakPlayer != null) {
                try {
                    String alignment = yakPlayer.getAlignment();
                    if (alignment != null && !alignment.isEmpty()) {
                        return switch (alignment.toUpperCase()) {
                            case "CHAOTIC" -> WARNING_COLOR;
                            case "NEUTRAL" -> ACCENT_COLOR;
                            case "LAWFUL" -> LABEL_COLOR;
                            default -> VALUE_COLOR;
                        };
                    }
                } catch (Exception e) {
                    // Alignment system might not be ready
                }
            }
        } catch (Exception e) {
            // Fallback
        }

        return VALUE_COLOR;
    }

    private String getRankDisplay(String rank) {
        if (rank == null || rank.equals("DEFAULT")) return VALUE_COLOR + "Player";

        try {
            Rank rankEnum = Rank.fromString(rank);
            return switch (rankEnum) {
                case DEV -> "§6Developer";
                case MANAGER -> ACCENT_COLOR + "Manager";
                case GM -> "§bGame Master";
                default -> VALUE_COLOR + "Player";
            };
        } catch (Exception e) {
            return VALUE_COLOR + rank;
        }
    }

    private String getAlignmentDisplay(String alignment) {
        if (alignment == null) return VALUE_COLOR + "Unknown";

        return switch (alignment.toUpperCase()) {
            case "CHAOTIC" -> WARNING_COLOR + "Chaotic";
            case "NEUTRAL" -> ACCENT_COLOR + "Neutral";
            case "LAWFUL" -> LABEL_COLOR + "Lawful";
            default -> VALUE_COLOR + alignment;
        };
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0s";

        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(seconds));

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m");

        return result.toString().trim();
    }

    // =============================================================================
    // EVENT HANDLERS
    // =============================================================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // TAB handles automatically
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // TAB handles automatically
    }

    // =============================================================================
    // PUBLIC API
    // =============================================================================

    public void shutdown() {
        try {
            enabled = false;
            registeredPlaceholders.clear();
            logger.info("Individual TAB placeholders shutdown completed");
        } catch (Exception e) {
            logger.warning("Error during TAB shutdown: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled && tabAPI != null;
    }

    public int getPlaceholderCount() {
        return registeredPlaceholders.size();
    }

    // =============================================================================
    // FUNCTIONAL INTERFACES
    // =============================================================================

    @FunctionalInterface
    private interface PlaceholderFunction {
        String apply(Player player) throws Exception;
    }

    @FunctionalInterface
    private interface ServerPlaceholderFunction {
        String apply() throws Exception;
    }
}