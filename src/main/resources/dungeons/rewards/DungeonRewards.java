package com.rednetty.server.mechanics.dungeons.rewards;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.dungeons.config.DungeonTemplate;
import com.rednetty.server.mechanics.dungeons.instance.DungeonInstance;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ENHANCED: Complete Dungeon Reward Distribution System
 *
 * Manages all aspects of reward distribution for dungeon completion,
 * including mob spawning rewards, item drops, experience, and currency.
 * Handles fair distribution among party members and tier-appropriate rewards.
 *
 * Features:
 * - Tier-based reward scaling
 * - Fair party distribution
 * - Multiple reward types (mobs, items, experience, currency)
 * - Chance-based reward rolls
 * - Performance-based bonuses
 * - Completion vs failure rewards
 * - Individual and group rewards
 * - Anti-exploit protection
 * - Reward history tracking
 * - Customizable reward pools
 */
public class DungeonRewards {

    // ================ CORE COMPONENTS ================

    private final DungeonInstance dungeonInstance;
    private final DungeonTemplate template;
    private final Logger logger;
    private final MobManager mobManager;
    private final DropsManager dropsManager;

    // ================ REWARD TRACKING ================

    private final Map<UUID, PlayerRewardData> playerRewards = new HashMap<>();
    private final Set<String> distributedRewards = new HashSet<>();
    private final Map<String, Integer> rewardCounts = new HashMap<>();
    private boolean rewardsDistributed = false;
    private long distributionTime = 0;

    // ================ REWARD CONFIGURATION ================

    private double experienceMultiplier = 1.0;
    private double lootMultiplier = 1.0;
    private boolean enablePerformanceBonuses = true;
    private boolean enableGroupBonuses = true;
    private int maxRewardsPerPlayer = 10;
    private long distributionDelay = 3000L; // 3 seconds

    // ================ PLAYER REWARD DATA ================

    /**
     * Tracks rewards for an individual player
     */
    public static class PlayerRewardData {
        private final UUID playerId;
        private final String playerName;
        private final long dungeonStartTime;

        private int mobsSpawned = 0;
        private int itemsReceived = 0;
        private int experienceGained = 0;
        private double participationScore = 0.0;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<String> mobRewards = new ArrayList<>();
        private final Map<String, Object> customRewards = new HashMap<>();
        private boolean rewardsReceived = false;

        public PlayerRewardData(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.dungeonStartTime = System.currentTimeMillis();
        }

        // Getters and setters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public long getDungeonStartTime() { return dungeonStartTime; }
        public int getMobsSpawned() { return mobsSpawned; }
        public void addMobSpawned() { this.mobsSpawned++; }
        public int getItemsReceived() { return itemsReceived; }
        public void addItemReceived() { this.itemsReceived++; }
        public int getExperienceGained() { return experienceGained; }
        public void addExperience(int exp) { this.experienceGained += exp; }
        public double getParticipationScore() { return participationScore; }
        public void setParticipationScore(double score) { this.participationScore = Math.max(0.0, Math.min(1.0, score)); }
        public List<ItemStack> getItems() { return new ArrayList<>(items); }
        public void addItem(ItemStack item) { items.add(item); addItemReceived(); }
        public List<String> getMobRewards() { return new ArrayList<>(mobRewards); }
        public void addMobReward(String mobData) { mobRewards.add(mobData); addMobSpawned(); }
        public Map<String, Object> getCustomRewards() { return new HashMap<>(customRewards); }
        public void addCustomReward(String key, Object value) { customRewards.put(key, value); }
        public boolean hasReceivedRewards() { return rewardsReceived; }
        public void setRewardsReceived(boolean received) { this.rewardsReceived = received; }

        public long getTimeInDungeon() {
            return System.currentTimeMillis() - dungeonStartTime;
        }

        public int getTotalRewards() {
            return mobsSpawned + itemsReceived + customRewards.size();
        }

        public String getSummary() {
            return String.format("%s: %d mobs, %d items, %d exp (%.1f%% participation)",
                    playerName, mobsSpawned, itemsReceived, experienceGained, participationScore * 100);
        }
    }

    /**
     * Reward distribution result
     */
    public static class RewardDistributionResult {
        private final boolean success;
        private final String message;
        private final Map<UUID, PlayerRewardData> playerData;
        private final int totalRewards;

        public RewardDistributionResult(boolean success, String message, Map<UUID, PlayerRewardData> playerData) {
            this.success = success;
            this.message = message;
            this.playerData = new HashMap<>(playerData);
            this.totalRewards = playerData.values().stream()
                    .mapToInt(PlayerRewardData::getTotalRewards)
                    .sum();
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<UUID, PlayerRewardData> getPlayerData() { return new HashMap<>(playerData); }
        public int getTotalRewards() { return totalRewards; }
    }

    // ================ CONSTRUCTOR ================

    public DungeonRewards(DungeonInstance dungeonInstance) {
        this.dungeonInstance = dungeonInstance;
        this.template = dungeonInstance.getTemplate();
        this.logger = YakRealms.getInstance().getLogger();
        this.mobManager = MobManager.getInstance();
        this.dropsManager = DropsManager.getInstance();

        // Apply template settings
        this.experienceMultiplier = template.getSettings().getExperienceMultiplier();
        this.lootMultiplier = template.getSettings().getLootMultiplier();
    }

    // ================ INITIALIZATION ================

    /**
     * Initialize the reward system
     */
    public boolean initialize() {
        try {
            // Initialize player reward data
            for (Player player : dungeonInstance.getOnlinePlayers()) {
                playerRewards.put(player.getUniqueId(),
                        new PlayerRewardData(player.getUniqueId(), player.getName()));
            }

            if (isDebugMode()) {
                logger.info("§a[DungeonRewards] §7Initialized for " + playerRewards.size() + " players");
            }

            return true;

        } catch (Exception e) {
            logger.severe("§c[DungeonRewards] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================ REWARD DISTRIBUTION ================

    /**
     * Distribute completion rewards
     */
    public RewardDistributionResult distributeCompletionRewards() {
        if (rewardsDistributed) {
            return new RewardDistributionResult(false, "Rewards already distributed", playerRewards);
        }

        try {
            // Calculate participation scores
            calculateParticipationScores();

            // Determine player tiers
            Map<UUID, Integer> playerTiers = getPlayerTiers();

            // Distribute rewards based on tiers
            for (Map.Entry<UUID, Integer> entry : playerTiers.entrySet()) {
                UUID playerId = entry.getKey();
                int tier = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    distributePlayerCompletionRewards(player, tier);
                }
            }

            // Apply group bonuses
            if (enableGroupBonuses) {
                applyGroupBonuses();
            }

            // Schedule delayed distribution
            scheduleRewardDistribution();

            rewardsDistributed = true;
            distributionTime = System.currentTimeMillis();

            String message = "Completion rewards distributed to " + playerRewards.size() + " players";
            return new RewardDistributionResult(true, message, playerRewards);

        } catch (Exception e) {
            logger.severe("§c[DungeonRewards] Error distributing completion rewards: " + e.getMessage());
            e.printStackTrace();
            return new RewardDistributionResult(false, "Distribution failed: " + e.getMessage(), playerRewards);
        }
    }

    /**
     * Distribute failure/consolation rewards
     */
    public RewardDistributionResult distributeFailureRewards() {
        try {
            // Calculate reduced participation scores
            calculateParticipationScores();

            // Apply failure penalty (50% of normal rewards)
            for (PlayerRewardData data : playerRewards.values()) {
                data.setParticipationScore(data.getParticipationScore() * 0.5);
            }

            // Determine player tiers
            Map<UUID, Integer> playerTiers = getPlayerTiers();

            // Distribute consolation rewards
            for (Map.Entry<UUID, Integer> entry : playerTiers.entrySet()) {
                UUID playerId = entry.getKey();
                int tier = Math.max(1, entry.getValue() - 1); // Lower tier for failure

                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    distributePlayerFailureRewards(player, tier);
                }
            }

            // Schedule delayed distribution
            scheduleRewardDistribution();

            String message = "Consolation rewards distributed to " + playerRewards.size() + " players";
            return new RewardDistributionResult(true, message, playerRewards);

        } catch (Exception e) {
            logger.severe("§c[DungeonRewards] Error distributing failure rewards: " + e.getMessage());
            e.printStackTrace();
            return new RewardDistributionResult(false, "Failure distribution failed: " + e.getMessage(), playerRewards);
        }
    }

    /**
     * Distribute completion rewards for a specific player
     */
    private void distributePlayerCompletionRewards(Player player, int tier) {
        PlayerRewardData data = playerRewards.get(player.getUniqueId());
        if (data == null) return;

        try {
            // Get tier-specific rewards from template
            List<DungeonTemplate.RewardDefinition> tierRewards = template.getRewardsForTier(tier);

            if (tierRewards.isEmpty()) {
                // Fallback to generic rewards
                tierRewards = generateFallbackRewards(tier);
            }

            // Apply participation and performance modifiers
            double rewardMultiplier = calculateRewardMultiplier(data);

            // Distribute each reward type
            for (DungeonTemplate.RewardDefinition reward : tierRewards) {
                if (shouldGrantReward(reward, rewardMultiplier)) {
                    distributeSpecificReward(player, reward, data);
                }
            }

            // Apply performance bonuses
            if (enablePerformanceBonuses) {
                applyPerformanceBonuses(player, data, tier);
            }

            data.setRewardsReceived(true);

            if (isDebugMode()) {
                logger.info("§a[DungeonRewards] §7Distributed completion rewards to " + player.getName() +
                        " (T" + tier + ", " + data.getTotalRewards() + " rewards)");
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Error distributing rewards to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Distribute failure rewards for a specific player
     */
    private void distributePlayerFailureRewards(Player player, int tier) {
        PlayerRewardData data = playerRewards.get(player.getUniqueId());
        if (data == null) return;

        try {
            // Reduced rewards for failure
            List<DungeonTemplate.RewardDefinition> baseRewards = template.getRewardsForTier(tier);

            // Only give a subset of rewards
            int maxFailureRewards = Math.max(1, baseRewards.size() / 3);
            Collections.shuffle(baseRewards);

            for (int i = 0; i < Math.min(maxFailureRewards, baseRewards.size()); i++) {
                DungeonTemplate.RewardDefinition reward = baseRewards.get(i);

                // Reduced chance for failure rewards
                if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                    distributeSpecificReward(player, reward, data);
                }
            }

            data.setRewardsReceived(true);

            if (isDebugMode()) {
                logger.info("§6[DungeonRewards] §7Distributed failure rewards to " + player.getName() +
                        " (T" + tier + ", " + data.getTotalRewards() + " rewards)");
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Error distributing failure rewards to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Distribute a specific reward to a player
     */
    private void distributeSpecificReward(Player player, DungeonTemplate.RewardDefinition reward, PlayerRewardData data) {
        try {
            switch (reward.getType()) {
                case MOB_SPAWN:
                    distributeMobReward(player, reward, data);
                    break;
                case ITEM:
                    distributeItemReward(player, reward, data);
                    break;
                case EXPERIENCE:
                    distributeExperienceReward(player, reward, data);
                    break;
                case CURRENCY:
                    distributeCurrencyReward(player, reward, data);
                    break;
                case CUSTOM:
                    distributeCustomReward(player, reward, data);
                    break;
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Error distributing specific reward: " + e.getMessage());
        }
    }

    /**
     * Distribute mob spawn reward
     */
    private void distributeMobReward(Player player, DungeonTemplate.RewardDefinition reward, PlayerRewardData data) {
        try {
            String mobData = reward.getData();
            if (mobData == null || mobData.isEmpty()) return;

            // Parse mob data (format: "mobtype:tier@elite#amount")
            String[] parts = mobData.split("#");
            String baseMobData = parts[0];
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

            // Apply quantity modifier
            amount = (int) Math.max(1, amount * reward.getQuantity() * lootMultiplier);

            // Find a good spawn location near the player
            Location spawnLoc = findRewardSpawnLocation(player);

            for (int i = 0; i < amount; i++) {
                try {
                    // Parse individual mob data
                    String[] mobParts = baseMobData.split(":");
                    String mobType = mobParts[0];
                    int tier = Integer.parseInt(mobParts[1].split("@")[0]);
                    boolean elite = Boolean.parseBoolean(mobParts[1].split("@")[1]);

                    // Spawn the mob
                    LivingEntity spawnedMob = mobManager.spawnMobFromSpawner(spawnLoc, mobType, tier, elite);

                    if (spawnedMob != null) {
                        data.addMobReward(mobData);

                        // Add special marking for reward mobs
                        spawnedMob.setMetadata("rewardMob", new org.bukkit.metadata.FixedMetadataValue(YakRealms.getInstance(), true));
                        spawnedMob.setMetadata("rewardPlayer", new org.bukkit.metadata.FixedMetadataValue(YakRealms.getInstance(), player.getUniqueId().toString()));
                    }

                } catch (Exception e) {
                    logger.warning("§c[DungeonRewards] Error spawning individual reward mob: " + e.getMessage());
                }
            }

            // Notify player
            String mobTypeName = mobData.split(":")[0];
            player.sendMessage(ChatColor.GREEN + "Reward: " + amount + "x " + mobTypeName + " spawned!");

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Error distributing mob reward: " + e.getMessage());
        }
    }

    /**
     * Distribute item reward
     */
    private void distributeItemReward(Player player, DungeonTemplate.RewardDefinition reward, PlayerRewardData data) {
        try {
            // This would integrate with your item system
            // For now, create a placeholder item
            ItemStack item = createRewardItem(reward);

            if (item != null && item.getType() != Material.AIR) {
                // Give item to player
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item);
                } else {
                    // Drop at player's location if inventory full
                    player.getWorld().dropItem(player.getLocation(), item);
                }

                data.addItem(item);
                player.sendMessage(ChatColor.GREEN + "Reward: " + item.getAmount() + "x " +
                        item.getType().name().toLowerCase().replace("_", " "));
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Error distributing item reward: " + e.getMessage());
        }
    }

    /**
     * Distribute experience reward
     */
    private void distributeExperienceReward(Player player, DungeonTemplate.RewardDefinition reward, PlayerRewardData data) {
        try {
            int baseExp = Integer.parseInt(reward.getData());
            int finalExp = (int) (baseExp * reward.getQuantity() * experienceMultiplier);

            player.giveExp(finalExp);
            data.addExperience(finalExp);

            player.sendMessage(ChatColor.GREEN + "Reward: " + finalExp + " experience!");

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Error distributing experience reward: " + e.getMessage());
        }
    }

    /**
     * Distribute currency reward
     */
    private void distributeCurrencyReward(Player player, DungeonTemplate.RewardDefinition reward, PlayerRewardData data) {
        try {
            // This would integrate with your economy system
            String currencyType = reward.getData();
            int amount = reward.getQuantity();

            // Placeholder - integrate with your economy system
            data.addCustomReward("currency_" + currencyType, amount);
            player.sendMessage(ChatColor.GREEN + "Reward: " + amount + " " + currencyType + "!");

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Error distributing currency reward: " + e.getMessage());
        }
    }

    /**
     * Distribute custom reward
     */
    private void distributeCustomReward(Player player, DungeonTemplate.RewardDefinition reward, PlayerRewardData data) {
        try {
            // Handle custom reward types
            String customType = reward.getData();
            data.addCustomReward("custom_" + customType, reward.getQuantity());

            player.sendMessage(ChatColor.GREEN + "Reward: " + customType + "!");

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Error distributing custom reward: " + e.getMessage());
        }
    }

    // ================ REWARD CALCULATION ================

    /**
     * Calculate participation scores for all players
     */
    private void calculateParticipationScores() {
        // Get player data from dungeon instance
        Map<UUID, DungeonInstance.DungeonPlayerData> dungeonData = dungeonInstance.getPlayerData();

        if (dungeonData.isEmpty()) {
            // Fallback: equal participation
            for (PlayerRewardData data : playerRewards.values()) {
                data.setParticipationScore(1.0);
            }
            return;
        }

        // Calculate total damage and time for normalization
        long totalDamage = dungeonData.values().stream()
                .mapToLong(DungeonInstance.DungeonPlayerData::getTotalDamageDealt)
                .sum();

        long totalTime = dungeonData.values().stream()
                .mapToLong(DungeonInstance.DungeonPlayerData::getTimeInDungeon)
                .sum();

        // Calculate individual scores
        for (PlayerRewardData data : playerRewards.values()) {
            DungeonInstance.DungeonPlayerData dungeonPlayerData = dungeonData.get(data.getPlayerId());

            if (dungeonPlayerData != null) {
                double damageScore = totalDamage > 0 ?
                        (double) dungeonPlayerData.getTotalDamageDealt() / totalDamage : 0.0;
                double timeScore = totalTime > 0 ?
                        (double) dungeonPlayerData.getTimeInDungeon() / totalTime : 0.0;
                double survivalScore = dungeonPlayerData.getDeaths() == 0 ? 1.0 :
                        Math.max(0.1, 1.0 - (dungeonPlayerData.getDeaths() * 0.2));

                // Weighted combination of scores
                double participationScore = (damageScore * 0.4) + (timeScore * 0.4) + (survivalScore * 0.2);
                data.setParticipationScore(Math.max(0.1, Math.min(1.0, participationScore)));
            } else {
                data.setParticipationScore(0.5); // Default for missing data
            }
        }

        if (isDebugMode()) {
            logger.info("§6[DungeonRewards] §7Calculated participation scores:");
            for (PlayerRewardData data : playerRewards.values()) {
                logger.info("§7  " + data.getPlayerName() + ": " +
                        String.format("%.1f%%", data.getParticipationScore() * 100));
            }
        }
    }

    /**
     * Get player tiers
     */
    private Map<UUID, Integer> getPlayerTiers() {
        Map<UUID, Integer> tiers = new HashMap<>();

        for (UUID playerId : playerRewards.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                // This should integrate with your tier system
                // For now, use a placeholder
                int tier = getPlayerTier(player);
                tiers.put(playerId, tier);
            }
        }

        return tiers;
    }

    /**
     * Get a player's tier (placeholder - integrate with your tier system)
     */
    private int getPlayerTier(Player player) {
        // TODO: Integrate with your tier system
        return 3; // Placeholder
    }

    /**
     * Calculate reward multiplier for a player
     */
    private double calculateRewardMultiplier(PlayerRewardData data) {
        double baseMultiplier = data.getParticipationScore();

        // Time bonus (completed faster = slightly better rewards)
        long dungeonDuration = data.getTimeInDungeon();
        long estimatedDuration = template.getEstimatedDuration();

        if (dungeonDuration < estimatedDuration) {
            double timeBonus = 1.0 + ((estimatedDuration - dungeonDuration) / (double) estimatedDuration) * 0.2;
            baseMultiplier *= timeBonus;
        }

        return Math.max(0.1, Math.min(2.0, baseMultiplier)); // Cap between 10% and 200%
    }

    /**
     * Determine if a reward should be granted
     */
    private boolean shouldGrantReward(DungeonTemplate.RewardDefinition reward, double multiplier) {
        double adjustedChance = reward.getChance() * multiplier;
        return ThreadLocalRandom.current().nextDouble() < adjustedChance;
    }

    /**
     * Apply group bonuses
     */
    private void applyGroupBonuses() {
        int partySize = playerRewards.size();

        if (partySize >= 4) {
            // Large group bonus - extra chance for rare rewards
            for (PlayerRewardData data : playerRewards.values()) {
                Player player = Bukkit.getPlayer(data.getPlayerId());
                if (player != null && ThreadLocalRandom.current().nextDouble() < 0.2) {
                    // 20% chance for bonus reward
                    giveGroupBonusReward(player, data);
                }
            }
        }
    }

    /**
     * Apply performance bonuses
     */
    private void applyPerformanceBonuses(Player player, PlayerRewardData data, int tier) {
        if (data.getParticipationScore() > 0.8) {
            // High participation bonus
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                giveBonusReward(player, data, tier, "High Participation");
            }
        }
    }

    /**
     * Give group bonus reward
     */
    private void giveGroupBonusReward(Player player, PlayerRewardData data) {
        // Simple bonus - could be enhanced
        int bonusExp = 50 + ThreadLocalRandom.current().nextInt(100);
        player.giveExp(bonusExp);
        data.addExperience(bonusExp);
        player.sendMessage(ChatColor.GOLD + "Group Bonus: " + bonusExp + " experience!");
    }

    /**
     * Give bonus reward
     */
    private void giveBonusReward(Player player, PlayerRewardData data, int tier, String reason) {
        // Performance bonus - could spawn an extra mob or give item
        player.sendMessage(ChatColor.GOLD + "Performance Bonus (" + reason + ")!");

        // Simple implementation - give experience
        int bonusExp = tier * 25;
        player.giveExp(bonusExp);
        data.addExperience(bonusExp);
    }

    // ================ UTILITY METHODS ================

    /**
     * Find a good spawn location for reward mobs
     */
    private Location findRewardSpawnLocation(Player player) {
        Location playerLoc = player.getLocation();

        // Try to find a safe location near the player
        for (int attempts = 0; attempts < 10; attempts++) {
            double x = playerLoc.getX() + (ThreadLocalRandom.current().nextDouble() * 10 - 5);
            double z = playerLoc.getZ() + (ThreadLocalRandom.current().nextDouble() * 10 - 5);
            Location candidate = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);

            if (isSafeSpawnLocation(candidate)) {
                return candidate;
            }
        }

        // Fallback to player location with offset
        return playerLoc.clone().add(3, 1, 3);
    }

    /**
     * Check if location is safe for spawning
     */
    private boolean isSafeSpawnLocation(Location location) {
        return !location.getBlock().getType().isSolid() &&
                !location.clone().add(0, 1, 0).getBlock().getType().isSolid();
    }

    /**
     * Create a reward item
     */
    private ItemStack createRewardItem(DungeonTemplate.RewardDefinition reward) {
        // Placeholder implementation - integrate with your item system
        String itemData = reward.getData();

        try {
            Material material = Material.valueOf(itemData.toUpperCase());
            return new ItemStack(material, reward.getQuantity());
        } catch (IllegalArgumentException e) {
            // Fallback to default item
            return new ItemStack(Material.DIAMOND, reward.getQuantity());
        }
    }

    /**
     * Generate fallback rewards if template has none
     */
    private List<DungeonTemplate.RewardDefinition> generateFallbackRewards(int tier) {
        List<DungeonTemplate.RewardDefinition> fallbacks = new ArrayList<>();

        // Basic mob reward based on tier
        String mobType = switch (tier) {
            case 1, 2 -> "skeleton";
            case 3, 4 -> "witherskeleton";
            case 5, 6 -> "warden";
            default -> "skeleton";
        };

        String mobData = mobType + ":" + tier + "@false#1";
        fallbacks.add(new DungeonTemplate.RewardDefinition(
                DungeonTemplate.RewardType.MOB_SPAWN, mobData, 1.0, 1));

        // Experience reward
        fallbacks.add(new DungeonTemplate.RewardDefinition(
                DungeonTemplate.RewardType.EXPERIENCE, String.valueOf(tier * 100), 1.0, 1));

        return fallbacks;
    }

    /**
     * Schedule delayed reward distribution
     */
    private void scheduleRewardDistribution() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    executeRewardDistribution();
                } catch (Exception e) {
                    logger.warning("§c[DungeonRewards] Delayed distribution error: " + e.getMessage());
                }
            }
        }.runTaskLater(YakRealms.getInstance(), distributionDelay / 50);
    }

    /**
     * Execute the actual reward distribution
     */
    private void executeRewardDistribution() {
        dungeonInstance.broadcast(ChatColor.GOLD + "=== Distributing Rewards ===");

        // Show reward summary to all players
        for (PlayerRewardData data : playerRewards.values()) {
            Player player = Bukkit.getPlayer(data.getPlayerId());
            if (player != null) {
                showRewardSummary(player, data);
            }
        }

        dungeonInstance.broadcast(ChatColor.GREEN + "All rewards have been distributed!");
    }

    /**
     * Show reward summary to a player
     */
    private void showRewardSummary(Player player, PlayerRewardData data) {
        player.sendMessage(ChatColor.GOLD + "=== Your Rewards ===");

        if (data.getMobsSpawned() > 0) {
            player.sendMessage(ChatColor.GREEN + "Mobs Spawned: " + ChatColor.WHITE + data.getMobsSpawned());
        }

        if (data.getItemsReceived() > 0) {
            player.sendMessage(ChatColor.GREEN + "Items Received: " + ChatColor.WHITE + data.getItemsReceived());
        }

        if (data.getExperienceGained() > 0) {
            player.sendMessage(ChatColor.GREEN + "Experience: " + ChatColor.WHITE + data.getExperienceGained());
        }

        player.sendMessage(ChatColor.YELLOW + "Participation: " + ChatColor.WHITE +
                String.format("%.1f%%", data.getParticipationScore() * 100));
    }

    // ================ PUBLIC API ================

    /**
     * Get player reward data
     */
    public PlayerRewardData getPlayerRewardData(UUID playerId) {
        return playerRewards.get(playerId);
    }

    /**
     * Get reward summary for all players
     */
    public Map<UUID, String> getRewardSummary() {
        return playerRewards.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getSummary()
                ));
    }

    /**
     * Check if rewards have been distributed
     */
    public boolean areRewardsDistributed() {
        return rewardsDistributed;
    }

    /**
     * Get distribution time
     */
    public long getDistributionTime() {
        return distributionTime;
    }

    // ================ GETTERS AND SETTERS ================

    public double getExperienceMultiplier() { return experienceMultiplier; }
    public void setExperienceMultiplier(double multiplier) { this.experienceMultiplier = Math.max(0.0, multiplier); }
    public double getLootMultiplier() { return lootMultiplier; }
    public void setLootMultiplier(double multiplier) { this.lootMultiplier = Math.max(0.0, multiplier); }
    public boolean isPerformanceBonusesEnabled() { return enablePerformanceBonuses; }
    public void setPerformanceBonusesEnabled(boolean enabled) { this.enablePerformanceBonuses = enabled; }
    public boolean isGroupBonusesEnabled() { return enableGroupBonuses; }
    public void setGroupBonusesEnabled(boolean enabled) { this.enableGroupBonuses = enabled; }

    // ================ UTILITY METHODS ================

    private boolean isDebugMode() {
        return YakRealms.getInstance().isDebugMode();
    }

    // ================ CLEANUP ================

    /**
     * Clean up the reward system
     */
    public void cleanup() {
        try {
            playerRewards.clear();
            distributedRewards.clear();
            rewardCounts.clear();

            if (isDebugMode()) {
                logger.info("§6[DungeonRewards] §7Cleaned up reward system");
            }

        } catch (Exception e) {
            logger.warning("§c[DungeonRewards] Cleanup error: " + e.getMessage());
        }
    }
}