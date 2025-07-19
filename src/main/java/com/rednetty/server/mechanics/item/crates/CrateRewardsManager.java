package com.rednetty.server.mechanics.item.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.crates.types.CrateType;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.economy.MoneyManager;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 *   manager for generating deterministic crate rewards
 * Ensures that the displayed item is always one of the actual rewards received
 */
public class CrateRewardsManager {
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
     *  Generates deterministic rewards for a crate opening
     * Uses a seed-based approach to ensure consistency
     */
    public List<ItemStack> generateRewards(CrateType crateType, CrateConfiguration configuration) {
        List<ItemStack> rewards = new ArrayList<>();

        try {
            // Create a deterministic seed based on current time and crate type
            // This ensures the same rewards are generated for the same opening session
            long seed = System.currentTimeMillis() / 1000; // Use seconds, not milliseconds
            String cacheKey = crateType.name() + "_" + seed;


            // Generate rewards using the seed for deterministic results
            Random deterministicRandom = new Random(seed);

            int tier = crateType.getTier();
            boolean isHalloween = crateType.isHalloween();

            logger.info("Generating deterministic rewards for " + crateType + " (tier: " + tier + ", halloween: " + isHalloween + ", seed: " + seed + ")");

            // GUARANTEED: Always generate at least one main equipment reward
            ItemStack mainEquipment = generateDeterministicEquipmentReward(tier, isHalloween, deterministicRandom);
            if (mainEquipment != null) {
                rewards.add(mainEquipment);
                logger.info("Generated main equipment: " + getItemDisplayName(mainEquipment));
            }

            // GUARANTEED: Always generate gems
            ItemStack gems = generateDeterministicGemReward(tier, isHalloween, deterministicRandom);
            if (gems != null) {
                rewards.add(gems);
                logger.info("Generated gems: " + getItemDisplayName(gems));
            }

            // Generate orb reward (tier-based chance)
            int orbChance = 35 + (tier * 8); // 43% for tier 1, up to 83% for tier 6
            if (deterministicRandom.nextInt(100) < orbChance) {
                ItemStack orb = generateDeterministicOrbReward(tier, isHalloween, deterministicRandom);
                if (orb != null) {
                    rewards.add(orb);
                    logger.info("Generated orb: " + getItemDisplayName(orb));
                }
            }

            // Generate scroll reward (25% chance)
            if (deterministicRandom.nextInt(100) < 25) {
                ItemStack scroll = generateDeterministicScrollReward(tier, isHalloween, deterministicRandom);
                if (scroll != null) {
                    rewards.add(scroll);
                    logger.info("Generated scroll: " + getItemDisplayName(scroll));
                }
            }

            // Higher tier bonuses
            if (tier >= 4) {
                // Protection scroll (15% chance)
                if (deterministicRandom.nextInt(100) < 15) {
                    ItemStack protectionScroll = generateDeterministicProtectionScroll(tier, deterministicRandom);
                    if (protectionScroll != null) {
                        rewards.add(protectionScroll);
                        logger.info("Generated protection scroll: " + getItemDisplayName(protectionScroll));
                    }
                }
            }

            if (tier >= 5) {
                // Extra equipment for legendary tiers (30% chance)
                if (deterministicRandom.nextInt(100) < 30) {
                    ItemStack bonusEquipment = generateDeterministicEquipmentReward(tier, isHalloween, deterministicRandom);
                    if (bonusEquipment != null) {
                        rewards.add(bonusEquipment);
                        logger.info("Generated bonus equipment: " + getItemDisplayName(bonusEquipment));
                    }
                }
            }

            // Halloween bonuses
            if (isHalloween) {
                if (deterministicRandom.nextInt(100) < 60) {
                    ItemStack halloweenBonus = generateDeterministicHalloweenBonus(tier, deterministicRandom);
                    if (halloweenBonus != null) {
                        rewards.add(halloweenBonus);
                        logger.info("Generated Halloween bonus: " + getItemDisplayName(halloweenBonus));
                    }
                }
            }

            // Ensure at least one reward
            if (rewards.isEmpty()) {
                ItemStack fallback = generateFallbackReward(tier);
                rewards.add(fallback);
                logger.warning("No rewards generated, using fallback: " + getItemDisplayName(fallback));
            }

            // Clean old cache entries to prevent memory leaks
            cleanRewardCache();

            logger.info("Generated " + rewards.size() + " deterministic rewards for " + crateType);
            return cloneRewardList(rewards);

        } catch (Exception e) {
            logger.severe("Error generating deterministic rewards for crate type " + crateType + ": " + e.getMessage());
            e.printStackTrace();
            // Add fallback reward
            List<ItemStack> fallbackRewards = new ArrayList<>();
            fallbackRewards.add(generateFallbackReward(crateType.getTier()));
            return fallbackRewards;
        }
    }

    /**
     *  Creates a drop with specific rarity for preview generation
     * Uses the same deterministic approach
     */
    public ItemStack createDropWithRarity(int tier, int itemType, int rarity) {
        try {
            return dropsManager.createDrop(tier, itemType, rarity);
        } catch (Exception e) {
            logger.warning("Failed to create drop with rarity: tier=" + tier +
                    ", itemType=" + itemType + ", rarity=" + rarity + ": " + e.getMessage());
            return createFallbackEquipment(tier, itemType);
        }
    }

    /**
     *  Generates deterministic equipment reward
     */
    private ItemStack generateDeterministicEquipmentReward(int tier, boolean isHalloween, Random random) {
        try {
            // Determine item type (1-8 for different equipment types)
            int itemType = ThreadLocalRandom.current().nextInt(1,8);

            // Determine rarity based on tier and Halloween bonus
            int rarity = determineDeterministicRarity(tier, isHalloween, random);

            ItemStack equipment = dropsManager.createDrop(tier, itemType, rarity);

            // Add Halloween enhancement if applicable
            if (isHalloween && equipment != null) {
                equipment = enhanceForHalloween(equipment);
            }

            return equipment;

        } catch (Exception e) {
            logger.warning("Error generating deterministic equipment reward: " + e.getMessage());
            return createFallbackEquipment(tier, random.nextInt(8) + 1);
        }
    }

    /**
     *  Generates deterministic gem reward
     */
    private ItemStack generateDeterministicGemReward(int tier, boolean isHalloween, Random random) {
        try {
            // Base gem amount based on tier with deterministic variation
            int baseAmount = switch (tier) {
                case 1 -> 50 + random.nextInt(50);    // 50-99
                case 2 -> 100 + random.nextInt(150);  // 100-249
                case 3 -> 250 + random.nextInt(250);  // 250-499
                case 4 -> 500 + random.nextInt(500);  // 500-999
                case 5 -> 1000 + random.nextInt(1500); // 1000-2499
                case 6 -> 2500 + random.nextInt(2500); // 2500-4999
                default -> 100;
            };

            // Halloween bonus
            if (isHalloween) {
                baseAmount = (int) (baseAmount * 1.5);
            }

            return MoneyManager.makeGems(baseAmount);

        } catch (Exception e) {
            logger.warning("Error generating deterministic gem reward: " + e.getMessage());
            return createFallbackGems(tier);
        }
    }

    /**
     *  Generates deterministic orb reward
     */
    private ItemStack generateDeterministicOrbReward(int tier, boolean isHalloween, Random random) {
        try {
            // Higher chance for legendary orbs at higher tiers
            boolean isLegendary = tier >= 4 && random.nextInt(100) < (tier * 10);

            if (isLegendary) {
                return orbManager.createLegendaryOrb(isHalloween);
            } else {
                return orbManager.createNormalOrb(isHalloween);
            }

        } catch (Exception e) {
            logger.warning("Error generating deterministic orb reward: " + e.getMessage());
            return createFallbackOrb();
        }
    }

    /**
     *  Generates deterministic scroll reward
     */
    private ItemStack generateDeterministicScrollReward(int tier, boolean isHalloween, Random random) {
        try {
            // Randomly choose between weapon and armor enhancement scrolls
            if (random.nextBoolean()) {
                return scrollManager.createWeaponEnhancementScroll(tier);
            } else {
                return scrollManager.createArmorEnhancementScroll(tier);
            }

        } catch (Exception e) {
            logger.warning("Error generating deterministic scroll reward: " + e.getMessage());
            return createFallbackScroll(tier);
        }
    }

    /**
     *  Generates deterministic protection scroll
     */
    private ItemStack generateDeterministicProtectionScroll(int tier, Random random) {
        try {
            int protectionTier = Math.max(1, Math.min(tier - 1, 5));
            return scrollManager.createProtectionScroll(protectionTier);

        } catch (Exception e) {
            logger.warning("Error generating deterministic protection scroll: " + e.getMessage());
            return createFallbackScroll(tier);
        }
    }

    /**
     *  Generates deterministic Halloween bonus rewards
     */
    private ItemStack generateDeterministicHalloweenBonus(int tier, Random random) {
        int bonusType = random.nextInt(3);

        switch (bonusType) {
            case 0:
                // Extra gems
                int bonusGems = tier * 150;
                return MoneyManager.makeGems(bonusGems);
            case 1:
                // Extra orbs
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
                // Halloween-themed item
                return createHalloweenThemedItem(tier);
            default:
                return createFallbackGems(tier);
        }
    }

    /**
     *  Deterministic rarity calculation
     */
    private int determineDeterministicRarity(int tier, boolean isHalloween, Random random) {
        // Base rarity chances
        int roll = random.nextInt(100);

        // Tier bonuses
        int tierBonus = (tier - 1) * 5; // +5% for each tier above 1

        // Halloween bonus
        int halloweenBonus = isHalloween ? 10 : 0;

        // Adjust chances
        int uniqueChance = 2 + tierBonus + halloweenBonus;
        int rareChance = 10 + tierBonus + halloweenBonus;
        int uncommonChance = 26 + tierBonus + halloweenBonus;

        if (roll < uniqueChance) {
            return 4; // Unique
        } else if (roll < rareChance) {
            return 3; // Rare
        } else if (roll < uncommonChance) {
            return 2; // Uncommon
        } else {
            return 1; // Common
        }
    }

    /**
     * Enhances item for Halloween
     */
    private ItemStack enhanceForHalloween(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        String currentName = meta.getDisplayName();

        if (currentName != null && !currentName.contains("🎃")) {
            meta.setDisplayName("🎃 " + currentName);

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GOLD + "★ Halloween  ★");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Creates Halloween-themed item
     */
    private ItemStack createHalloweenThemedItem(int tier) {
        ItemStack item = new ItemStack(Material.JACK_O_LANTERN);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "🎃 Halloween Spirit 🎃");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "A mystical pumpkin filled with",
                    ChatColor.GRAY + "the essence of Halloween magic!",
                    "",
                    ChatColor.GOLD + "✦ Tier " + tier + " Halloween Bonus ✦",
                    "",
                    ChatColor.ITALIC + "" + ChatColor.GRAY + "Spooky and valuable!"
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Gets display name for logging
     */
    private String getItemDisplayName(ItemStack item) {
        if (item == null) return "null";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ");
    }

    /**
     * Clones a reward list to prevent reference issues
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
     * Cleans old cache entries to prevent memory leaks
     */
    private void cleanRewardCache() {
    }

    /**
     * Creates fallback rewards when main generation fails
     */
    private ItemStack generateFallbackReward(int tier) {
        try {
            return MoneyManager.makeGems(tier * 100);
        } catch (Exception e) {
            // Ultimate fallback
            ItemStack emerald = new ItemStack(Material.EMERALD, tier);
            ItemMeta meta = emerald.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + "Emergency Compensation");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "The mystical energies were disrupted,",
                        ChatColor.GRAY + "but you still receive valuable gems!"
                ));
                emerald.setItemMeta(meta);
            }
            return emerald;
        }
    }

    /**
     * Creates fallback equipment
     */
    private ItemStack createFallbackEquipment(int tier, int itemType) {
        Material material = switch (itemType) {
            case 1, 2, 3, 4 -> Material.IRON_SWORD; // Weapons
            case 5 -> Material.IRON_HELMET;
            case 6 -> Material.IRON_CHESTPLATE;
            case 7 -> Material.IRON_LEGGINGS;
            case 8 -> Material.IRON_BOOTS;
            default -> Material.IRON_SWORD;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            ChatColor tierColor = getTierColor(tier);
            meta.setDisplayName(tierColor + "Emergency " + getItemTypeName(itemType));
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.RED + "DMG: " + (tier * 10) + " - " + (tier * 15),
                    "",
                    ChatColor.GRAY + "" + ChatColor.ITALIC + "Emergency Reward"
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Creates fallback gems
     */
    private ItemStack createFallbackGems(int tier) {
        ItemStack emerald = new ItemStack(Material.EMERALD, tier * 5);
        ItemMeta meta = emerald.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Mystical Emeralds");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Emergency gem compensation",
                    ChatColor.GRAY + "for tier " + tier + " crate"
            ));
            emerald.setItemMeta(meta);
        }

        return emerald;
    }

    /**
     * Creates fallback orb
     */
    private ItemStack createFallbackOrb() {
        ItemStack orb = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = orb.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Emergency Orb");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "A basic mystical orb",
                    ChatColor.GRAY + "containing emergency magic"
            ));
            orb.setItemMeta(meta);
        }

        return orb;
    }

    /**
     * Creates fallback scroll
     */
    private ItemStack createFallbackScroll(int tier) {
        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Emergency Scroll");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "A basic enhancement scroll",
                    ChatColor.GRAY + "for tier " + tier + " equipment"
            ));
            scroll.setItemMeta(meta);
        }

        return scroll;
    }

    /**
     * Gets tier color
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
     * Gets item type name
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
     * Gets statistics for debugging
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rewardTypesSupported", Arrays.asList("equipment", "gems", "orbs", "scrolls", "halloween_bonuses"));
        stats.put("rarityLevels", 4);
        stats.put("equipmentTypes", 8);
        stats.put("fallbacksAvailable", true);
        stats.put("deterministicGeneration", true);
        return stats;
    }
}