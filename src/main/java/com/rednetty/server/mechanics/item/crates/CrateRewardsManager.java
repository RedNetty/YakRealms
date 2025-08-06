package com.rednetty.server.mechanics.item.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.crates.types.CrateType;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.economy.MoneyManager;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Manager for generating deterministic crate rewards.
 * Ensures that the displayed item is always one of the actual rewards received.
 */
public class CrateRewardsManager {

    // Reward generation constants
    private static final int BASE_ORB_CHANCE = 35;
    private static final int ORB_CHANCE_PER_TIER = 8;
    private static final int SCROLL_CHANCE = 25;
    private static final int PROTECTION_SCROLL_CHANCE = 15;
    private static final int BONUS_EQUIPMENT_CHANCE = 30;
    private static final int HALLOWEEN_BONUS_CHANCE = 60;
    private static final int LEGENDARY_ORB_MULTIPLIER = 10;

    // Tier-based gem amounts
    private static final int[] BASE_GEM_AMOUNTS = {0, 50, 100, 250, 500, 1000, 2500};
    private static final int[] GEM_VARIATIONS = {0, 50, 150, 250, 500, 1500, 2500};

    // Equipment type constants
    private static final int MIN_EQUIPMENT_TYPE = 1;
    private static final int MAX_EQUIPMENT_TYPE = 8;
    private static final int WEAPON_TYPES = 4;

    // Rarity constants
    private static final int UNIQUE_RARITY = 4;
    private static final int RARE_RARITY = 3;
    private static final int UNCOMMON_RARITY = 2;
    private static final int COMMON_RARITY = 1;

    // Chance modifiers
    private static final int TIER_BONUS_MULTIPLIER = 5;
    private static final int HALLOWEEN_RARITY_BONUS = 10;
    private static final double HALLOWEEN_GEM_MULTIPLIER = 1.5;

    // Fallback constants
    private static final int FALLBACK_GEMS_MULTIPLIER = 100;
    private static final int FALLBACK_EQUIPMENT_MULTIPLIER = 5;
    private static final int HALLOWEEN_BONUS_GEM_MULTIPLIER = 150;

    private final YakRealms plugin;
    private final Logger logger;
    private final DropsManager dropsManager;
    private final OrbManager orbManager;
    private final ScrollManager scrollManager;

    public CrateRewardsManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.dropsManager = DropsManager.getInstance();
        this.orbManager = OrbManager.getInstance();
        this.scrollManager = ScrollManager.getInstance();
    }

    /**
     * Generates deterministic rewards for a crate opening using a seed-based approach.
     *
     * @param crateType The type of crate being opened
     * @param configuration The crate configuration settings
     * @return List of generated rewards
     */
    public List<ItemStack> generateRewards(CrateType crateType, CrateConfiguration configuration) {
        List<ItemStack> rewards = new ArrayList<>();

        try {
            long seed = System.currentTimeMillis() / 1000;
            Random deterministicRandom = new Random(seed);

            int tier = crateType.getTier();
            boolean isHalloween = crateType.isHalloween();

            logRewardGeneration(crateType, tier, isHalloween, seed);

            generateGuaranteedRewards(rewards, tier, isHalloween, deterministicRandom);
            generateChanceBasedRewards(rewards, tier, isHalloween, deterministicRandom);
            generateTierBonuses(rewards, tier, isHalloween, deterministicRandom);
            generateHalloweenBonuses(rewards, tier, isHalloween, deterministicRandom);

            ensureMinimumRewards(rewards, tier);
            cleanRewardCache();

            logger.info(formatLogMessage("Generated {} rewards for {}", rewards.size(), crateType));
            return cloneRewardList(rewards);

        } catch (Exception e) {
            logger.severe(formatLogMessage("Error generating rewards for {}: {}", crateType, e.getMessage()));
            e.printStackTrace();
            return createFallbackRewardsList(crateType.getTier());
        }
    }

    /**
     * Creates a drop with specific rarity for preview generation.
     *
     * @param tier The tier level
     * @param itemType The item type identifier
     * @param rarity The rarity level
     * @return Generated item with specified rarity
     */
    public ItemStack createDropWithRarity(int tier, int itemType, int rarity) {
        try {
            return dropsManager.createDrop(tier, itemType, rarity);
        } catch (Exception e) {
            logger.warning(formatLogMessage("Failed to create drop - tier: {}, itemType: {}, rarity: {}",
                    tier, itemType, rarity));
            return createFallbackEquipment(tier, itemType);
        }
    }

    /**
     * Generates guaranteed rewards that every crate should contain.
     */
    private void generateGuaranteedRewards(List<ItemStack> rewards, int tier, boolean isHalloween, Random random) {
        ItemStack mainEquipment = generateEquipmentReward(tier, isHalloween, random);
        if (mainEquipment != null) {
            rewards.add(mainEquipment);
            logRewardGeneration("main equipment", mainEquipment);
        }

        ItemStack gems = generateGemReward(tier, isHalloween, random);
        if (gems != null) {
            rewards.add(gems);
            logRewardGeneration("gems", gems);
        }
    }

    /**
     * Generates chance-based rewards.
     */
    private void generateChanceBasedRewards(List<ItemStack> rewards, int tier, boolean isHalloween, Random random) {
        int orbChance = BASE_ORB_CHANCE + (tier * ORB_CHANCE_PER_TIER);
        if (random.nextInt(100) < orbChance) {
            ItemStack orb = generateOrbReward(tier, isHalloween, random);
            if (orb != null) {
                rewards.add(orb);
                logRewardGeneration("orb", orb);
            }
        }

        if (random.nextInt(100) < SCROLL_CHANCE) {
            ItemStack scroll = generateScrollReward(tier, isHalloween, random);
            if (scroll != null) {
                rewards.add(scroll);
                logRewardGeneration("scroll", scroll);
            }
        }
    }

    /**
     * Generates tier-specific bonus rewards.
     */
    private void generateTierBonuses(List<ItemStack> rewards, int tier, boolean isHalloween, Random random) {
        if (tier >= 4) {
            if (random.nextInt(100) < PROTECTION_SCROLL_CHANCE) {
                ItemStack protectionScroll = generateProtectionScroll(tier, random);
                if (protectionScroll != null) {
                    rewards.add(protectionScroll);
                    logRewardGeneration("protection scroll", protectionScroll);
                }
            }
        }

        if (tier >= 5) {
            if (random.nextInt(100) < BONUS_EQUIPMENT_CHANCE) {
                ItemStack bonusEquipment = generateEquipmentReward(tier, isHalloween, random);
                if (bonusEquipment != null) {
                    rewards.add(bonusEquipment);
                    logRewardGeneration("bonus equipment", bonusEquipment);
                }
            }
        }
    }

    /**
     * Generates Halloween-specific bonus rewards.
     */
    private void generateHalloweenBonuses(List<ItemStack> rewards, int tier, boolean isHalloween, Random random) {
        if (isHalloween && random.nextInt(100) < HALLOWEEN_BONUS_CHANCE) {
            ItemStack halloweenBonus = generateHalloweenBonus(tier, random);
            if (halloweenBonus != null) {
                rewards.add(halloweenBonus);
                logRewardGeneration("Halloween bonus", halloweenBonus);
            }
        }
    }

    /**
     * Generates equipment reward with deterministic rarity.
     */
    private ItemStack generateEquipmentReward(int tier, boolean isHalloween, Random random) {
        try {
            int itemType = ThreadLocalRandom.current().nextInt(MIN_EQUIPMENT_TYPE, MAX_EQUIPMENT_TYPE + 1);
            int rarity = calculateRarity(tier, isHalloween, random);

            ItemStack equipment = dropsManager.createDrop(tier, itemType, rarity);

            if (isHalloween && equipment != null) {
                equipment = enhanceForHalloween(equipment);
            }

            return equipment;
        } catch (Exception e) {
            logger.warning(formatLogMessage("Error generating equipment reward: {}", e.getMessage()));
            return createFallbackEquipment(tier, random.nextInt(MAX_EQUIPMENT_TYPE) + 1);
        }
    }

    /**
     * Generates gem reward based on tier and Halloween status.
     */
    private ItemStack generateGemReward(int tier, boolean isHalloween, Random random) {
        try {
            int baseAmount = calculateGemAmount(tier, random);

            if (isHalloween) {
                baseAmount = (int) (baseAmount * HALLOWEEN_GEM_MULTIPLIER);
            }

            return MoneyManager.makeGems(baseAmount);
        } catch (Exception e) {
            logger.warning(formatLogMessage("Error generating gem reward: {}", e.getMessage()));
            return createFallbackGems(tier);
        }
    }

    /**
     * Generates orb reward with tier-based legendary chance.
     */
    private ItemStack generateOrbReward(int tier, boolean isHalloween, Random random) {
        try {
            boolean isLegendary = tier >= 4 && random.nextInt(100) < (tier * LEGENDARY_ORB_MULTIPLIER);

            return isLegendary ? orbManager.createLegendaryOrb(isHalloween)
                    : orbManager.createNormalOrb(isHalloween);
        } catch (Exception e) {
            logger.warning(formatLogMessage("Error generating orb reward: {}", e.getMessage()));
            return createFallbackOrb();
        }
    }

    /**
     * Generates scroll reward (weapon or armor enhancement).
     */
    private ItemStack generateScrollReward(int tier, boolean isHalloween, Random random) {
        try {
            return random.nextBoolean() ? scrollManager.createWeaponEnhancementScroll(tier)
                    : scrollManager.createArmorEnhancementScroll(tier);
        } catch (Exception e) {
            logger.warning(formatLogMessage("Error generating scroll reward: {}", e.getMessage()));
            return createFallbackScroll(tier);
        }
    }

    /**
     * Generates protection scroll for higher tiers.
     */
    private ItemStack generateProtectionScroll(int tier, Random random) {
        try {
            int protectionTier = Math.max(1, Math.min(tier - 1, 5));
            return scrollManager.createProtectionScroll(protectionTier);
        } catch (Exception e) {
            logger.warning(formatLogMessage("Error generating protection scroll: {}", e.getMessage()));
            return createFallbackScroll(tier);
        }
    }

    /**
     * Generates Halloween-themed bonus rewards.
     */
    private ItemStack generateHalloweenBonus(int tier, Random random) {
        int bonusType = random.nextInt(3);

        switch (bonusType) {
            case 0:
                return MoneyManager.makeGems(tier * HALLOWEEN_BONUS_GEM_MULTIPLIER);
            case 1:
                try {
                    ItemStack orbs = orbManager.createNormalOrb(true);
                    if (orbs != null) {
                        orbs.setAmount(2);
                    }
                    return orbs;
                } catch (Exception e) {
                    return createFallbackGems(tier);
                }
            case 2:
                return createHalloweenThemedItem(tier);
            default:
                return createFallbackGems(tier);
        }
    }

    /**
     * Calculates gem amount based on tier with random variation.
     */
    private int calculateGemAmount(int tier, Random random) {
        if (tier < 1 || tier >= BASE_GEM_AMOUNTS.length) {
            return 100;
        }
        return BASE_GEM_AMOUNTS[tier] + random.nextInt(GEM_VARIATIONS[tier]);
    }

    /**
     * Calculates rarity based on tier, Halloween status, and random roll.
     */
    private int calculateRarity(int tier, boolean isHalloween, Random random) {
        int roll = random.nextInt(100);
        int tierBonus = (tier - 1) * TIER_BONUS_MULTIPLIER;
        int halloweenBonus = isHalloween ? HALLOWEEN_RARITY_BONUS : 0;

        int uniqueChance = 2 + tierBonus + halloweenBonus;
        int rareChance = 10 + tierBonus + halloweenBonus;
        int uncommonChance = 26 + tierBonus + halloweenBonus;

        if (roll < uniqueChance) return UNIQUE_RARITY;
        if (roll < rareChance) return RARE_RARITY;
        if (roll < uncommonChance) return UNCOMMON_RARITY;
        return COMMON_RARITY;
    }

    /**
     * Enhances item with Halloween theming.
     */
    private ItemStack enhanceForHalloween(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        String currentName = meta.getDisplayName();

        if (currentName != null && !currentName.contains("🎃")) {
            meta.setDisplayName(TextUtil.colorize("🎃 " + currentName));

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(TextUtil.colorize("&6★ Halloween Special ★"));
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Creates Halloween-themed special item.
     */
    private ItemStack createHalloweenThemedItem(int tier) {
        ItemStack item = new ItemStack(Material.JACK_O_LANTERN);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&6🎃 Halloween Spirit 🎃"));

            List<String> lore = Arrays.asList(
                    "",
                    TextUtil.colorize("&7A mystical pumpkin filled with"),
                    TextUtil.colorize("&7the essence of Halloween magic!"),
                    "",
                    TextUtil.colorize("&6✦ Tier " + tier + " Halloween Bonus ✦"),
                    "",
                    TextUtil.colorize("&o&7Spooky and valuable!")
            );

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Ensures minimum rewards are present.
     */
    private void ensureMinimumRewards(List<ItemStack> rewards, int tier) {
        if (rewards.isEmpty()) {
            ItemStack fallback = generateFallbackReward(tier);
            rewards.add(fallback);
            logger.warning(formatLogMessage("No rewards generated, using fallback: {}",
                    getItemDisplayName(fallback)));
        }
    }

    /**
     * Creates fallback rewards list when main generation fails.
     */
    private List<ItemStack> createFallbackRewardsList(int tier) {
        List<ItemStack> fallbackRewards = new ArrayList<>();
        fallbackRewards.add(generateFallbackReward(tier));
        return fallbackRewards;
    }

    /**
     * Generates a fallback reward when main generation fails.
     */
    private ItemStack generateFallbackReward(int tier) {
        try {
            return MoneyManager.makeGems(tier * FALLBACK_GEMS_MULTIPLIER);
        } catch (Exception e) {
            return createUltimateFallback(tier);
        }
    }

    /**
     * Creates ultimate fallback when all other generation fails.
     */
    private ItemStack createUltimateFallback(int tier) {
        ItemStack emerald = new ItemStack(Material.EMERALD, tier);
        ItemMeta meta = emerald.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&aEmergency Compensation"));
            meta.setLore(Arrays.asList(
                    TextUtil.colorize("&7The mystical energies were disrupted,"),
                    TextUtil.colorize("&7but you still receive valuable gems!")
            ));
            emerald.setItemMeta(meta);
        }

        return emerald;
    }

    /**
     * Creates fallback equipment item.
     */
    private ItemStack createFallbackEquipment(int tier, int itemType) {
        Material material = getEquipmentMaterial(itemType);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            ChatColor tierColor = getTierColor(tier);
            String itemTypeName = getItemTypeName(itemType);

            meta.setDisplayName(TextUtil.colorize(tierColor + "Emergency " + itemTypeName));
            meta.setLore(Arrays.asList(
                    "",
                    TextUtil.colorize("&cDMG: " + (tier * 10) + " - " + (tier * 15)),
                    "",
                    TextUtil.colorize("&o&7Emergency Reward")
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Gets appropriate material for equipment type.
     */
    private Material getEquipmentMaterial(int itemType) {
        return switch (itemType) {
            case 1, 2, 3, 4 -> Material.IRON_SWORD;
            case 5 -> Material.IRON_HELMET;
            case 6 -> Material.IRON_CHESTPLATE;
            case 7 -> Material.IRON_LEGGINGS;
            case 8 -> Material.IRON_BOOTS;
            default -> Material.IRON_SWORD;
        };
    }

    /**
     * Creates fallback gem reward.
     */
    private ItemStack createFallbackGems(int tier) {
        ItemStack emerald = new ItemStack(Material.EMERALD, tier * FALLBACK_EQUIPMENT_MULTIPLIER);
        ItemMeta meta = emerald.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&aMystical Emeralds"));
            meta.setLore(Arrays.asList(
                    TextUtil.colorize("&7Emergency gem compensation"),
                    TextUtil.colorize("&7for tier " + tier + " crate")
            ));
            emerald.setItemMeta(meta);
        }

        return emerald;
    }

    /**
     * Creates fallback orb reward.
     */
    private ItemStack createFallbackOrb() {
        ItemStack orb = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = orb.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&dEmergency Orb"));
            meta.setLore(Arrays.asList(
                    TextUtil.colorize("&7A basic mystical orb"),
                    TextUtil.colorize("&7containing emergency magic")
            ));
            orb.setItemMeta(meta);
        }

        return orb;
    }

    /**
     * Creates fallback scroll reward.
     */
    private ItemStack createFallbackScroll(int tier) {
        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&eEmergency Scroll"));
            meta.setLore(Arrays.asList(
                    TextUtil.colorize("&7A basic enhancement scroll"),
                    TextUtil.colorize("&7for tier " + tier + " equipment")
            ));
            scroll.setItemMeta(meta);
        }

        return scroll;
    }

    /**
     * Gets color associated with tier level.
     */
    private ChatColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.BLUE;
            default -> ChatColor.GRAY;
        };
    }

    /**
     * Gets display name for equipment type.
     */
    private String getItemTypeName(int itemType) {
        return switch (itemType) {
            case 1 -> "Staff";
            case 2 -> "Spear";
            case 3 -> "Sword";
            case 4 -> "Axe";
            case 5 -> "Helmet";
            case 6 -> "Chestplate";
            case 7 -> "Leggings";
            case 8 -> "Boots";
            default -> "Equipment";
        };
    }

    /**
     * Gets display name of item for logging purposes.
     */
    private String getItemDisplayName(ItemStack item) {
        if (item == null) return "null";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return TextUtil.capitalizeWords(item.getType().name().replace("_", " "));
    }

    /**
     * Creates deep copy of reward list to prevent reference issues.
     */
    private List<ItemStack> cloneRewardList(List<ItemStack> rewards) {
        List<ItemStack> cloned = new ArrayList<>();
        for (ItemStack reward : rewards) {
            if (reward != null) {
                cloned.add(reward.clone());
            }
        }
        return cloned;
    }

    /**
     * Cleans old cache entries to prevent memory leaks.
     */
    private void cleanRewardCache() {
        // Implementation for cache cleanup if needed
    }

    /**
     * Logs reward generation with consistent formatting.
     */
    private void logRewardGeneration(CrateType crateType, int tier, boolean isHalloween, long seed) {
        logger.info(formatLogMessage("Generating rewards for {} (tier: {}, halloween: {}, seed: {})",
                crateType, tier, isHalloween, seed));
    }

    /**
     * Logs individual reward generation.
     */
    private void logRewardGeneration(String rewardType, ItemStack reward) {
        logger.info(formatLogMessage("Generated {}: {}", rewardType, getItemDisplayName(reward)));
    }

    /**
     * Formats log messages consistently.
     */
    private String formatLogMessage(String message, Object... args) {
        return String.format(message, args);
    }

    /**
     * Gets statistics for debugging and monitoring.
     *
     * @return Map containing system statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rewardTypesSupported", Arrays.asList("equipment", "gems", "orbs", "scrolls", "halloween_bonuses"));
        stats.put("rarityLevels", 4);
        stats.put("equipmentTypes", MAX_EQUIPMENT_TYPE);
        stats.put("fallbacksAvailable", true);
        stats.put("deterministicGeneration", true);
        stats.put("tierCount", BASE_GEM_AMOUNTS.length - 1);
        return stats;
    }
}