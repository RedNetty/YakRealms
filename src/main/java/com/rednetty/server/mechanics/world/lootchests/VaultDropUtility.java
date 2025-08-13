package com.rednetty.server.mechanics.world.lootchests;

import com.rednetty.server.YakRealms;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;

/**
 * utility class for dropping vault keys in the instant refresh automation system
 * Used by mob systems, quest systems, etc. to drop keys for existing vaults
 * Optimized for continuous use with instant refresh capability
 */
public class VaultDropUtility {

    private static final Random random = new Random();
    private final LootChestManager lootChestManager;

    public VaultDropUtility() {
        this.lootChestManager = YakRealms.getInstance().getLootChestManager();
    }

    // ========================================
    // CORE KEY DROPPING METHODS - INSTANT REFRESH OPTIMIZED
    // ========================================

    /**
     * Drop a key for the nearest available vault with instant refresh support
     * This is the main method used by mob kill systems
     */
    public boolean dropKeyForNearestVault(Location dropLocation) {
        return lootChestManager.dropKeyForNearestVault(dropLocation);
    }

    /**
     * Drop key with percentage chance - perfect for mob drops
     * Works seamlessly with instant refresh system
     */
    public boolean dropKeyWithChance(Location dropLocation, double chancePercent) {
        if (random.nextDouble() * 100 > chancePercent) {
            return false; // No drop
        }

        return dropKeyForNearestVault(dropLocation);
    }

    /**
     * Drop multiple keys with staggered timing for high-value events
     * Takes advantage of instant refresh for rapid consecutive openings
     */
    public boolean dropMultipleKeys(Location dropLocation, int keyCount, boolean staggered) {
        if (keyCount <= 1) {
            return dropKeyForNearestVault(dropLocation);
        }

        boolean anyDropped = false;
        for (int i = 0; i < keyCount; i++) {
            if (dropKeyForNearestVault(dropLocation)) {
                anyDropped = true;

                if (staggered && i < keyCount - 1) {
                    // Slight stagger to allow animations to complete
                    try {
                        Thread.sleep(100); // 0.1 second stagger
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return anyDropped;
    }

    /**
     * Drop key for nearest vault of specific tier or higher
     * Useful for boss kills where you want to guarantee higher tier vaults
     */
    public boolean dropKeyForTierOrHigher(Location dropLocation, LootChestManager.ChestTier minTier) {
        VaultChest targetVault = findNearestVaultOfTierOrHigher(dropLocation, minTier);

        if (targetVault == null) {
            // Fallback to any available vault for instant refresh system
            return dropKeyForNearestVault(dropLocation);
        }

        return lootChestManager.dropKeyForNearestVault(dropLocation);
    }

    /**
     * Drop key for nearest vault of specific type
     * Useful for themed drops (food mobs drop keys for food vaults, etc.)
     */
    public boolean dropKeyForType(Location dropLocation, LootChestManager.ChestType type) {
        VaultChest targetVault = findNearestVaultOfType(dropLocation, type);

        if (targetVault == null) {
            // Fallback to any type for instant refresh continuity
            return dropKeyForNearestVault(dropLocation);
        }

        return lootChestManager.dropKeyForNearestVault(dropLocation);
    }

    /**
     * Smart key drop that considers vault usage patterns in instant refresh system
     * Prefers less recently used vaults to distribute usage evenly
     */
    public boolean dropKeySmartDistribution(Location dropLocation) {
        List<VaultChest> nearbyVaults = getVaultsNear(dropLocation, 200.0);

        if (nearbyVaults.isEmpty()) {
            return false;
        }

        // Filter available vaults and sort by usage patterns
        VaultChest targetVault = nearbyVaults.stream()
                .filter(VaultChest::isInstantlyAvailable)
                .min((a, b) -> {
                    // Prefer vaults that were used less recently
                    long aLastUse = a.getSecondsSinceLastUse();
                    long bLastUse = b.getSecondsSinceLastUse();

                    // If never used, prioritize
                    if (aLastUse == -1 && bLastUse != -1) return -1;
                    if (bLastUse == -1 && aLastUse != -1) return 1;
                    if (aLastUse == -1 && bLastUse == -1) return 0;

                    // Otherwise, prefer vault used longer ago
                    return Long.compare(bLastUse, aLastUse);
                })
                .orElse(null);

        if (targetVault != null) {
            return lootChestManager.dropKeyForNearestVault(dropLocation);
        }

        return false;
    }

    // ========================================
    // VAULT QUERY METHODS - INSTANT REFRESH AWARE
    // ========================================

    /**
     * Get the nearest vault to a location
     */
    public VaultChest getNearestVault(Location location) {
        return lootChestManager.getNearestVault(location);
    }

    /**
     * Get the nearest vault within a specific distance
     */
    public VaultChest getNearestVault(Location location, double maxDistance) {
        return lootChestManager.getNearestVault(location, maxDistance);
    }

    /**
     * Get all vaults near a location
     */
    public List<VaultChest> getVaultsNear(Location location, double radius) {
        return lootChestManager.getVaultsNear(location, radius);
    }

    /**
     * Check if there's a vault within range that's instantly available
     */
    public boolean hasInstantlyAvailableVaultNearby(Location location, double maxDistance) {
        VaultChest nearest = getNearestVault(location, maxDistance);
        return nearest != null && nearest.isInstantlyAvailable();
    }

    /**
     * Get count of instantly available vaults in area
     */
    public int getAvailableVaultCount(Location location, double radius) {
        return (int) getVaultsNear(location, radius).stream()
                .filter(VaultChest::isInstantlyAvailable)
                .count();
    }

    /**
     * Check if area is suitable for continuous key drops
     */
    public boolean isAreaSuitableForContinuousDrops(Location location, double radius) {
        List<VaultChest> nearbyVaults = getVaultsNear(location, radius);

        // Need at least 2 vaults for good continuous operation
        long availableCount = nearbyVaults.stream()
                .filter(VaultChest::isInstantlyAvailable)
                .count();

        return availableCount >= 2 || (availableCount >= 1 && nearbyVaults.size() >= 1);
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Find nearest vault of specific tier or higher
     */
    private VaultChest findNearestVaultOfTierOrHigher(Location location, LootChestManager.ChestTier minTier) {
        List<VaultChest> nearbyVaults = getVaultsNear(location, 200.0);

        return nearbyVaults.stream()
                .filter(vault -> vault.getTier().getLevel() >= minTier.getLevel())
                .filter(VaultChest::isInstantlyAvailable) // Instant refresh check
                .min((a, b) -> Double.compare(
                        a.getLocation().distance(location),
                        b.getLocation().distance(location)
                ))
                .orElse(null);
    }

    /**
     * Find nearest vault of specific type
     */
    private VaultChest findNearestVaultOfType(Location location, LootChestManager.ChestType type) {
        List<VaultChest> nearbyVaults = getVaultsNear(location, 200.0);

        return nearbyVaults.stream()
                .filter(vault -> vault.getType() == type)
                .filter(VaultChest::isInstantlyAvailable) // Instant refresh check
                .min((a, b) -> Double.compare(
                        a.getLocation().distance(location),
                        b.getLocation().distance(location)
                ))
                .orElse(null);
    }

    // ========================================
    // ENHANCED USAGE EXAMPLES FOR INSTANT REFRESH SYSTEM
    // ========================================

    /**
     * boss kill key drop - supports instant refresh with multiple keys
     * Call this when a boss is killed
     */
    public static void handleBossKill(Player killer, Location bossLocation, String bossType) {
        VaultDropUtility utility = new VaultDropUtility();

        // Different bosses have different drop patterns optimized for instant refresh
        double dropChance;
        int keyCount = 1;
        LootChestManager.ChestTier minTier = LootChestManager.ChestTier.TIER_1;

        switch (bossType.toLowerCase()) {
            case "mini_boss":
                dropChance = 75.0; // Higher chance with instant refresh
                minTier = LootChestManager.ChestTier.TIER_2;
                break;
            case "dungeon_boss":
                dropChance = 100.0; // Guaranteed
                keyCount = 2; // Multiple keys for instant refresh
                minTier = LootChestManager.ChestTier.TIER_4;
                break;
            case "raid_boss":
                dropChance = 100.0; // Guaranteed
                keyCount = 3; // Multiple keys
                minTier = LootChestManager.ChestTier.TIER_5;
                break;
            case "world_boss":
                dropChance = 100.0; // Guaranteed
                keyCount = 5; // Many keys for instant refresh
                minTier = LootChestManager.ChestTier.TIER_6;
                break;
            default:
                dropChance = 35.0; // Higher base chance
                minTier = LootChestManager.ChestTier.TIER_1;
                break;
        }

        // Try for tier-specific vault first, fallback to any vault
        boolean dropped = false;

        if (keyCount > 1) {
            // Multiple keys for high-value bosses
            dropped = utility.dropMultipleKeys(bossLocation, keyCount, true);
        } else {
            dropped = utility.dropKeyForTierOrHigher(bossLocation, minTier);
            if (!dropped) {
                dropped = utility.dropKeyWithChance(bossLocation, dropChance);
            }
        }

        if (dropped) {
            if (keyCount > 1) {
                killer.sendMessage("§6✦ §7" + keyCount + " vault keys have been dropped from the " + bossType + "!");
                killer.sendMessage("§a⚡ Instant refresh allows rapid consecutive openings!");
            } else {
                killer.sendMessage("§6✦ §7A vault key has been dropped from the " + bossType + "!");
            }
            YakRealms.log(killer.getName() + " received " + keyCount + " vault key(s) from " + bossType + " kill");
        } else {
            // Optionally notify why no key was dropped
            if (!utility.hasInstantlyAvailableVaultNearby(bossLocation, 200.0)) {
                killer.sendMessage("§7No available vaults are nearby to receive a key.");
            }
        }
    }

    /**
     * mob kill key drop with instant refresh awareness
     * Call this in your mob death event
     */
    public static void handleMobKill(Player killer, Location mobLocation, String mobType) {
        VaultDropUtility utility = new VaultDropUtility();

        // Slightly higher chances due to instant refresh availability
        double dropChance;
        LootChestManager.ChestType preferredType = null;

        switch (mobType.toLowerCase()) {
            case "zombie":
            case "skeleton":
            case "spider":
                dropChance = 1.5; // Increased from 1.0%
                break;
            case "enderman":
            case "creeper":
                dropChance = 2.5; // Increased from 2.0%
                break;
            case "witch":
            case "vindicator":
                dropChance = 4.0; // Increased from 3.0%
                break;
            case "cow":
            case "pig":
            case "chicken":
                dropChance = 1.0; // Increased from 0.5%
                preferredType = LootChestManager.ChestType.FOOD;
                break;
            case "elite_mob":
                dropChance = 12.0; // Increased from 8.0%
                preferredType = LootChestManager.ChestType.ELITE;
                break;
            default:
                dropChance = 1.0; // Increased from 0.5%
                break;
        }

        // Use smart distribution for better vault usage patterns
        boolean dropped = false;
        if (preferredType != null) {
            dropped = utility.dropKeyForType(mobLocation, preferredType);
        }

        if (!dropped) {
            // Use smart distribution instead of random chance
            if (random.nextDouble() * 100 <= dropChance) {
                dropped = utility.dropKeySmartDistribution(mobLocation);
            }
        }

        if (dropped) {
            killer.sendMessage("§6✦ §7A vault key has been dropped!");
            killer.sendMessage("§a⚡ Automatic opening with instant refresh!");
        }
    }

    /**
     * quest reward with instant refresh optimization
     * Call this when a quest is completed
     */
    public static void handleQuestReward(Player player, String questId, String difficulty) {
        VaultDropUtility utility = new VaultDropUtility();

        LootChestManager.ChestTier minTier;
        int keyCount = 1;

        switch (difficulty.toLowerCase()) {
            case "easy":
                minTier = LootChestManager.ChestTier.TIER_1;
                break;
            case "medium":
                minTier = LootChestManager.ChestTier.TIER_2;
                break;
            case "hard":
                minTier = LootChestManager.ChestTier.TIER_4;
                keyCount = 2; // Multiple keys for harder quests
                break;
            case "legendary":
                minTier = LootChestManager.ChestTier.TIER_5;
                keyCount = 3; // Many keys for legendary quests
                break;
            default:
                minTier = LootChestManager.ChestTier.TIER_1;
                break;
        }

        boolean dropped = false;
        if (keyCount > 1) {
            dropped = utility.dropMultipleKeys(player.getLocation(), keyCount, true);
        } else {
            dropped = utility.dropKeyForTierOrHigher(player.getLocation(), minTier);
            if (!dropped) {
                dropped = utility.dropKeyForNearestVault(player.getLocation());
            }
        }

        if (dropped) {
            if (keyCount > 1) {
                player.sendMessage("§a✓ Quest completed! " + keyCount + " vault keys have been dropped for you!");
                player.sendMessage("§a⚡ Instant refresh allows you to open them quickly!");
            } else {
                player.sendMessage("§a✓ Quest completed! A vault key has been dropped for you!");
            }
            YakRealms.log(player.getName() + " completed quest " + questId + " and received " + keyCount + " vault key(s)");
        } else {
            player.sendMessage("§c✗ Quest completed, but no vaults are nearby for key drop!");
        }
    }

    /**
     * mining key drop with instant refresh consideration
     * Call this when rare ores are mined
     */
    public static void handleRareMining(Player player, Location mineLocation, String oreType) {
        VaultDropUtility utility = new VaultDropUtility();

        double dropChance;
        LootChestManager.ChestTier minTier = LootChestManager.ChestTier.TIER_1;

        switch (oreType.toLowerCase()) {
            case "diamond_ore":
                dropChance = 4.0; // Increased from 3.0%
                minTier = LootChestManager.ChestTier.TIER_3;
                break;
            case "emerald_ore":
                dropChance = 6.0; // Increased from 5.0%
                minTier = LootChestManager.ChestTier.TIER_4;
                break;
            case "ancient_debris":
                dropChance = 10.0; // Increased from 8.0%
                minTier = LootChestManager.ChestTier.TIER_5;
                break;
            default:
                return; // No key drop for this ore
        }

        boolean dropped = utility.dropKeyForTierOrHigher(mineLocation, minTier);
        if (!dropped) {
            dropped = utility.dropKeyWithChance(mineLocation, dropChance);
        }

        if (dropped) {
            player.sendMessage("§6⛏ §7Your mining has uncovered a vault key!");
            player.sendMessage("§a⚡ It will open automatically with instant refresh!");
        }
    }

    /**
     * farming/fishing key drop with instant refresh
     */
    public static void handleFarmingReward(Player player, Location farmLocation, String activityType, String item) {
        VaultDropUtility utility = new VaultDropUtility();

        double dropChance = 0.0;
        LootChestManager.ChestType preferredType = LootChestManager.ChestType.FOOD;

        switch (activityType.toLowerCase()) {
            case "fishing":
                if ("treasure".equals(item.toLowerCase())) {
                    dropChance = 8.0; // Increased from 5.0%
                }
                break;
            case "farming":
                if (item.toLowerCase().contains("golden") || item.toLowerCase().contains("enchanted")) {
                    dropChance = 3.0;
                }
                break;
            case "breeding":
                dropChance = 2.0;
                break;
        }

        if (dropChance > 0.0) {
            boolean dropped = utility.dropKeyForType(farmLocation, preferredType);
            if (!dropped) {
                dropped = utility.dropKeyWithChance(farmLocation, dropChance);
            }

            if (dropped) {
                player.sendMessage("§6🌾 §7Your " + activityType + " has yielded a vault key!");
                player.sendMessage("§a⚡ Automatic food vault opening!");
            }
        }
    }

    /**
     * event key drop with instant refresh support
     * Call this during special events
     */
    public static void handleEventKeyDrop(Player player, Location eventLocation, String eventType) {
        VaultDropUtility utility = new VaultDropUtility();

        LootChestManager.ChestTier minTier;
        LootChestManager.ChestType preferredType = LootChestManager.ChestType.ELITE;
        int keyCount = 1;

        switch (eventType.toLowerCase()) {
            case "holiday_event":
                minTier = LootChestManager.ChestTier.TIER_3;
                keyCount = 2;
                break;
            case "server_anniversary":
                minTier = LootChestManager.ChestTier.TIER_5;
                keyCount = 5; // Generous for special events
                break;
            case "special_boss_event":
                minTier = LootChestManager.ChestTier.TIER_6;
                keyCount = 3;
                break;
            default:
                minTier = LootChestManager.ChestTier.TIER_2;
                break;
        }

        boolean dropped = false;
        if (keyCount > 1) {
            dropped = utility.dropMultipleKeys(eventLocation, keyCount, true);
        } else {
            dropped = utility.dropKeyForType(eventLocation, preferredType);
            if (!dropped) {
                dropped = utility.dropKeyForTierOrHigher(eventLocation, minTier);
            }
            if (!dropped) {
                dropped = utility.dropKeyForNearestVault(eventLocation);
            }
        }

        if (dropped) {
            if (keyCount > 1) {
                player.sendMessage("§d✦ §7" + keyCount + " special event keys have been dropped!");
                player.sendMessage("§a⚡ Instant refresh allows rapid opening!");
            } else {
                player.sendMessage("§d✦ §7A special event key has been dropped!");
            }
            YakRealms.log("Event keys (" + keyCount + ") dropped for " + player.getName() + " during " + eventType);
        }
    }

    // ========================================
    // AREA ANALYSIS AND UTILITY METHODS
    // ========================================

    /**
     * Check if key drops are viable in the area (for balancing)
     */
    public static boolean canDropKeysInArea(Location location, double radius) {
        VaultDropUtility utility = new VaultDropUtility();
        return utility.hasInstantlyAvailableVaultNearby(location, radius);
    }

    /**
     * Get enhanced area information for admins
     */
    public static String getAreaInfo(Location location) {
        VaultDropUtility utility = new VaultDropUtility();
        List<VaultChest> nearbyVaults = utility.getVaultsNear(location, 200.0);

        if (nearbyVaults.isEmpty()) {
            return "§cNo vaults within 200 blocks - key drops will not work here";
        }

        VaultChest nearest = nearbyVaults.get(0);
        double distance = nearest.getLocation().distance(location);

        int availableCount = utility.getAvailableVaultCount(location, 200.0);
        boolean suitableForContinuous = utility.isAreaSuitableForContinuousDrops(location, 200.0);

        return String.format("§aNearest vault: %s §a(%.1fm away)\n" +
                        "§7Available vaults: §f%d§7/§f%d §7within 200m\n" +
                        "§7Continuous drops: %s\n" +
                        "§a⚡ Instant refresh system active",
                nearest.getDisplayName(), distance, availableCount, nearbyVaults.size(),
                suitableForContinuous ? "§a✓ Supported" : "§e⚠ Limited");
    }

    /**
     * Get area performance metrics
     */
    public static String getAreaPerformanceInfo(Location location, double radius) {
        VaultDropUtility utility = new VaultDropUtility();
        List<VaultChest> nearbyVaults = utility.getVaultsNear(location, radius);

        if (nearbyVaults.isEmpty()) {
            return "§cNo vaults in area for performance analysis";
        }

        int totalVaults = nearbyVaults.size();
        int availableVaults = utility.getAvailableVaultCount(location, radius);
        long totalOpenings = nearbyVaults.stream().mapToLong(VaultChest::getTimesOpened).sum();

        StringBuilder info = new StringBuilder();
        info.append("§6Area Performance (").append(String.format("%.0f", radius)).append("m radius):\n");
        info.append("§7Total Vaults: §f").append(totalVaults).append("\n");
        info.append("§7Available Now: §a").append(availableVaults).append(" §7(§f")
                .append(String.format("%.1f", (double) availableVaults / totalVaults * 100)).append("%§7)\n");
        info.append("§7Total Openings: §f").append(totalOpenings).append("\n");

        if (totalOpenings > 0) {
            double avgPerVault = (double) totalOpenings / totalVaults;
            info.append("§7Avg Opens/Vault: §f").append(String.format("%.1f", avgPerVault)).append("\n");
        }

        info.append("§a⚡ Instant Refresh: Active\n");
        info.append("§7Continuous Operation: ").append(utility.isAreaSuitableForContinuousDrops(location, radius) ? "§a✓" : "§e⚠");

        return info.toString();
    }
}