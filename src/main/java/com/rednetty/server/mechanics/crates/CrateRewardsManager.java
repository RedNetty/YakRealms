
// ===== CrateRewardsManager.java =====
package com.rednetty.server.mechanics.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.CrateConfiguration;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.mechanics.drops.DropFactory;
import com.rednetty.server.mechanics.drops.DropsManager;
import com.rednetty.server.mechanics.economy.MoneyManager;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Enhanced rewards manager for crate contents
 */
public class CrateRewardsManager {
    private final YakRealms plugin;
    private final Logger logger;

    // Integration with other systems
    private DropsManager dropsManager;
    private DropFactory dropFactory;
    private OrbManager orbManager;
    private ScrollManager scrollManager;

    // Reward probabilities (per 100)
    private static final int BASE_ITEM_CHANCE = 85;
    private static final int GEM_CHANCE = 60;
    private static final int ORB_CHANCE = 25;
    private static final int SCROLL_CHANCE = 15;
    private static final int BONUS_ITEM_CHANCE = 10;

    /**
     * Constructor
     */
    public CrateRewardsManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        initializeIntegrations();
    }

    /**
     * Initializes integration with other managers
     */
    private void initializeIntegrations() {
        this.dropsManager = DropsManager.getInstance();
        this.dropFactory = DropFactory.getInstance();
        this.orbManager = OrbManager.getInstance();
        this.scrollManager = ScrollManager.getInstance();
    }

    /**
     * Generates rewards for a crate opening
     *
     * @param crateType     The type of crate being opened
     * @param configuration The crate configuration
     * @return List of reward items
     */
    public List<ItemStack> generateRewards(CrateType crateType, CrateConfiguration configuration) {
        List<ItemStack> rewards = new ArrayList<>();

        try {
            int tier = crateType.getTier();
            boolean isHalloween = crateType.isHalloween();

            // Always give at least one main item
            ItemStack mainItem = generateMainItem(tier, isHalloween);
            if (mainItem != null) {
                rewards.add(mainItem);
            }

            // Chance for gems
            if (rollChance(GEM_CHANCE)) {
                ItemStack gems = generateGems(tier, isHalloween);
                if (gems != null) {
                    rewards.add(gems);
                }
            }

            // Chance for orbs (higher tiers have better orb chances)
            int orbChance = ORB_CHANCE + (tier * 5);
            if (rollChance(orbChance)) {
                ItemStack orb = generateOrb(tier, isHalloween);
                if (orb != null) {
                    rewards.add(orb);
                }
            }

            // Chance for scrolls (teleport or enhancement)
            if (rollChance(SCROLL_CHANCE)) {
                ItemStack scroll = generateScroll(tier, isHalloween);
                if (scroll != null) {
                    rewards.add(scroll);
                }
            }

            // Halloween bonus rewards
            if (isHalloween && rollChance(50)) {
                ItemStack halloweenBonus = generateHalloweenBonus(tier);
                if (halloweenBonus != null) {
                    rewards.add(halloweenBonus);
                }
            }

            // High tier bonus chance
            if (tier >= 4 && rollChance(BONUS_ITEM_CHANCE * tier)) {
                ItemStack bonusItem = generateBonusItem(tier, isHalloween);
                if (bonusItem != null) {
                    rewards.add(bonusItem);
                }
            }

            logger.fine("Generated " + rewards.size() + " rewards for " + crateType + " crate");

        } catch (Exception e) {
            logger.warning("Error generating rewards for " + crateType + ": " + e.getMessage());
            // Fallback reward
            rewards.add(generateFallbackReward(crateType.getTier()));
        }

        return rewards;
    }

    /**
     * Generates the main item reward
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's a Halloween crate
     * @return The main item reward
     */
    private ItemStack generateMainItem(int tier, boolean isHalloween) {
        try {
            // 70% chance for equipment, 30% chance for other valuable items
            if (rollChance(70)) {
                // Generate equipment using drops manager
                int itemType = ThreadLocalRandom.current().nextInt(8) + 1; // 1-8

                // Higher tiers have better rarity chances
                int rarity = 1;
                if (tier >= 3 && rollChance(20)) rarity = 2; // Uncommon
                if (tier >= 4 && rollChance(15)) rarity = 3; // Rare
                if (tier >= 5 && rollChance(10)) rarity = 4; // Unique

                return dropsManager.createDrop(tier, itemType, rarity);
            } else {
                // Generate valuable alternative items
                return generateAlternativeMainItem(tier, isHalloween);
            }
        } catch (Exception e) {
            logger.warning("Error generating main item: " + e.getMessage());
            return generateFallbackReward(tier);
        }
    }

    /**
     * Generates alternative main items (non-equipment)
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's Halloween
     * @return Alternative main item
     */
    private ItemStack generateAlternativeMainItem(int tier, boolean isHalloween) {
        int choice = ThreadLocalRandom.current().nextInt(3);

        return switch (choice) {
            case 0 -> generateLargeGemReward(tier, isHalloween);
            case 1 -> generateMultipleOrbs(tier, isHalloween);
            case 2 -> generateEnhancementBundle(tier);
            default -> generateLargeGemReward(tier, isHalloween);
        };
    }

    /**
     * Generates gem rewards
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's Halloween
     * @return Gem reward item
     */
    private ItemStack generateGems(int tier, boolean isHalloween) {
        int baseAmount = tier * tier * 10; // Exponential scaling
        int variation = baseAmount / 2;
        int finalAmount = baseAmount + ThreadLocalRandom.current().nextInt(variation);

        if (isHalloween) {
            finalAmount = (int) (finalAmount * 1.5); // 50% bonus for Halloween
        }

        return MoneyManager.makeGems(finalAmount);
    }

    /**
     * Generates a large gem reward as a main item
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's Halloween
     * @return Large gem reward
     */
    private ItemStack generateLargeGemReward(int tier, boolean isHalloween) {
        int amount = generateGems(tier, isHalloween).getAmount() * 3; // Triple normal amount
        return MoneyManager.makeGems(Math.min(amount, 64)); // Cap at stack size
    }

    /**
     * Generates orb rewards
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's Halloween
     * @return Orb reward item
     */
    private ItemStack generateOrb(int tier, boolean isHalloween) {
        try {
            // Higher tiers have better chance for legendary orbs
            boolean isLegendary = tier >= 4 && rollChance(tier * 5);

            if (isLegendary) {
                return orbManager.createLegendaryOrb(false);
            } else {
                ItemStack normalOrb = orbManager.createNormalOrb(false);

                // Higher tiers get multiple orbs
                if (tier >= 3) {
                    int amount = Math.min(tier - 1, 3);
                    normalOrb.setAmount(amount);
                }

                return normalOrb;
            }
        } catch (Exception e) {
            logger.warning("Error generating orb: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generates multiple orbs as a main reward
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's Halloween
     * @return Multiple orbs bundle
     */
    private ItemStack generateMultipleOrbs(int tier, boolean isHalloween) {
        ItemStack orbs = generateOrb(tier, isHalloween);
        if (orbs != null) {
            int multiplier = isHalloween ? 3 : 2;
            orbs.setAmount(Math.min(orbs.getAmount() * multiplier, 64));
        }
        return orbs;
    }

    /**
     * Generates scroll rewards
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's Halloween
     * @return Scroll reward item
     */
    private ItemStack generateScroll(int tier, boolean isHalloween) {
        try {
            // 50% chance for enhancement scroll, 50% for teleport scroll
            if (rollChance(50)) {
                // Enhancement scroll
                boolean isWeapon = rollChance(50);
                if (isWeapon) {
                    return scrollManager.createWeaponEnhancementScroll(tier);
                } else {
                    return scrollManager.createArmorEnhancementScroll(tier);
                }
            } else {
                // Teleport scroll (handled by drop factory)
                return dropFactory.createScrollDrop(tier);
            }
        } catch (Exception e) {
            logger.warning("Error generating scroll: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generates enhancement bundle (multiple scrolls)
     *
     * @param tier The crate tier
     * @return Enhancement bundle
     */
    private ItemStack generateEnhancementBundle(int tier) {
        // This could be a custom bundle item or just multiple scrolls
        // For now, return a high-tier enhancement scroll
        try {
            return scrollManager.createArmorEnhancementScroll(Math.min(tier + 1, 6));
        } catch (Exception e) {
            return scrollManager.createArmorEnhancementScroll(tier);
        }
    }

    /**
     * Generates Halloween-specific bonus rewards
     *
     * @param tier The crate tier
     * @return Halloween bonus item
     */
    private ItemStack generateHalloweenBonus(int tier) {
        // This could include special Halloween items, cosmetics, etc.
        // For now, return extra orbs with Halloween theme
        ItemStack bonus = generateOrb(tier, true);
        if (bonus != null && bonus.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = bonus.getItemMeta();
            if (meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                meta.setDisplayName("🎃 " + name + " 🎃");
                bonus.setItemMeta(meta);
            }
        }
        return bonus;
    }

    /**
     * Generates bonus items for high-tier crates
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's Halloween
     * @return Bonus item
     */
    private ItemStack generateBonusItem(int tier, boolean isHalloween) {
        // Higher chance for really good items
        int choice = ThreadLocalRandom.current().nextInt(4);

        return switch (choice) {
            case 0 -> generateLegendaryOrb();
            case 1 -> generateProtectionScroll(tier);
            case 2 -> generatePremiumGems(tier, isHalloween);
            case 3 -> generateRareEquipment(tier);
            default -> generateLegendaryOrb();
        };
    }

    /**
     * Generates a legendary orb
     *
     * @return Legendary orb
     */
    private ItemStack generateLegendaryOrb() {
        try {
            return orbManager.createLegendaryOrb(false);
        } catch (Exception e) {
            return orbManager.createNormalOrb(false);
        }
    }

    /**
     * Generates a protection scroll
     *
     * @param tier The crate tier
     * @return Protection scroll
     */
    private ItemStack generateProtectionScroll(int tier) {
        try {
            return scrollManager.createProtectionScroll(Math.max(0, tier - 1));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generates premium gems
     *
     * @param tier        The crate tier
     * @param isHalloween Whether it's Halloween
     * @return Premium gem reward
     */
    private ItemStack generatePremiumGems(int tier, boolean isHalloween) {
        ItemStack gems = generateLargeGemReward(tier, isHalloween);
        gems.setAmount(Math.min(gems.getAmount() * 2, 64)); // Double the large amount
        return gems;
    }

    /**
     * Generates rare equipment
     *
     * @param tier The crate tier
     * @return Rare equipment
     */
    private ItemStack generateRareEquipment(int tier) {
        try {
            int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
            int rarity = Math.min(4, tier - 1); // High rarity
            return dropsManager.createDrop(tier, itemType, rarity);
        } catch (Exception e) {
            return generateFallbackReward(tier);
        }
    }

    /**
     * Generates a fallback reward in case of errors
     *
     * @param tier The crate tier
     * @return Fallback reward
     */
    private ItemStack generateFallbackReward(int tier) {
        return MoneyManager.makeGems(tier * 50); // Basic gem reward
    }

    /**
     * Utility method to roll a percentage chance
     *
     * @param chance The percentage chance (0-100)
     * @return true if the roll succeeded
     */
    private boolean rollChance(int chance) {
        return ThreadLocalRandom.current().nextInt(100) < chance;
    }

    /**
     * Gets reward statistics for debugging
     *
     * @return Statistics map
     */
    public java.util.Map<String, Object> getRewardStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("baseItemChance", BASE_ITEM_CHANCE);
        stats.put("gemChance", GEM_CHANCE);
        stats.put("orbChance", ORB_CHANCE);
        stats.put("scrollChance", SCROLL_CHANCE);
        stats.put("bonusItemChance", BONUS_ITEM_CHANCE);
        return stats;
    }
}