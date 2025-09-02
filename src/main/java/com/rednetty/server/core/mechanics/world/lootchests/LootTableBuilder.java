package com.rednetty.server.core.mechanics.world.lootchests;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Loot Table Builder for instant refresh vault chests
 * Provides comprehensive loot generation with enchantments, custom items, and advanced features
 * Optimized for continuous use in the instant refresh system
 *
 * Version 3.0 - Enhanced for instant refresh system with improved balance
 */
public class LootTableBuilder {

    private final List<LootPool> pools = new ArrayList<>();
    private final Random random = new Random();

    // Enhanced for instant refresh - track generation statistics
    private int totalGenerations = 0;
    private Map<Material, Integer> materialDistribution = new HashMap<>();

    // ========================================
    // ENHANCED LOOT POOL SYSTEM
    // ========================================

    public static class LootPool {
        private final List<LootEntry> entries = new ArrayList<>();
        private int minRolls = 1;
        private int maxRolls = 1;
        private double bonusChance = 0.0;
        private String poolName;
        private double qualityMultiplier = 1.0; // For instant refresh balancing

        public LootPool() {
            this("default");
        }

        public LootPool(String poolName) {
            this.poolName = poolName;
        }

        public LootPool setRolls(int min, int max) {
            this.minRolls = Math.max(1, min);
            this.maxRolls = Math.max(minRolls, max);
            return this;
        }

        public LootPool setRolls(int exact) {
            return setRolls(exact, exact);
        }

        public LootPool setBonusChance(double chance) {
            this.bonusChance = Math.max(0.0, Math.min(1.0, chance));
            return this;
        }

        public LootPool setQualityMultiplier(double multiplier) {
            this.qualityMultiplier = Math.max(0.1, Math.min(3.0, multiplier));
            return this;
        }

        public LootPool addEntry(LootEntry entry) {
            entries.add(entry);
            return this;
        }

        public LootPool addItem(Material material, double chance, int minAmount, int maxAmount, int weight) {
            return addEntry(new LootEntry(material, chance, minAmount, maxAmount, weight));
        }

        public LootPool addItem(Material material, double chance, int minAmount, int maxAmount) {
            return addItem(material, chance, minAmount, maxAmount, 50);
        }

        public LootEntry createItem(Material material, double chance, int minAmount, int maxAmount, int weight) {
            LootEntry entry = new LootEntry(material, chance, minAmount, maxAmount, weight);
            addEntry(entry);
            return entry;
        }

        public LootEntry createItem(Material material, double chance, int minAmount, int maxAmount) {
            return createItem(material, chance, minAmount, maxAmount, 50);
        }

        /**
         * Add a pre-made ItemStack to this pool
         */
        public LootPool addPreMadeItem(ItemStack item, double chance, int minAmount, int maxAmount, int weight) {
            LootEntry entry = new LootEntry(item.getType(), chance, minAmount, maxAmount, weight);
            entry.setPreMadeItem(item);
            return addEntry(entry);
        }

        public LootPool addPreMadeItem(ItemStack item, double chance, int minAmount, int maxAmount) {
            return addPreMadeItem(item, chance, minAmount, maxAmount, 50);
        }

        public List<ItemStack> generateLoot(Random random) {
            List<ItemStack> loot = new ArrayList<>();

            // Calculate number of rolls with quality multiplier
            int rolls = minRolls;
            if (maxRolls > minRolls) {
                rolls = random.nextInt(maxRolls - minRolls + 1) + minRolls;
            }

            // Apply quality multiplier for instant refresh balancing
            if (qualityMultiplier > 1.0) {
                double extraRollChance = (qualityMultiplier - 1.0) * 0.5;
                if (random.nextDouble() < extraRollChance) {
                    rolls++;
                }
            }

            // Add bonus roll chance
            if (bonusChance > 0 && random.nextDouble() < bonusChance) {
                rolls++;
            }

            // Generate items
            for (int i = 0; i < rolls; i++) {
                ItemStack item = selectRandomItem(random);
                if (item != null) {
                    loot.add(item);
                }
            }

            return loot;
        }

        private ItemStack selectRandomItem(Random random) {
            if (entries.isEmpty()) {
                return null;
            }

            // Calculate total weight
            int totalWeight = entries.stream().mapToInt(LootEntry::getWeight).sum();

            if (totalWeight <= 0) {
                // Fallback to random selection
                LootEntry entry = entries.get(random.nextInt(entries.size()));
                return entry.generateItem(random);
            }

            // Weighted random selection
            int randomWeight = random.nextInt(totalWeight);
            int currentWeight = 0;

            for (LootEntry entry : entries) {
                currentWeight += entry.getWeight();
                if (randomWeight < currentWeight) {
                    return entry.generateItem(random);
                }
            }

            // Fallback
            return entries.get(entries.size() - 1).generateItem(random);
        }

        public List<LootEntry> getEntries() {
            return new ArrayList<>(entries);
        }

        public String getPoolName() {
            return poolName;
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public double getQualityMultiplier() {
            return qualityMultiplier;
        }
    }

    // ========================================
    // ENHANCED LOOT ENTRY SYSTEM
    // ========================================

    public static class LootEntry {
        private final Material material;
        private final double chance;
        private final int minAmount;
        private final int maxAmount;
        private final int weight;
        private final Map<Enchantment, EnchantmentData> enchantments = new HashMap<>();
        private String customName;
        private List<String> lore;
        private boolean glowing = false;
        private boolean unbreakable = false;
        private String rarity = "COMMON"; // For instant refresh display
        private ItemStack preMadeItem = null; // For YakRealms items

        public LootEntry(Material material, double chance, int minAmount, int maxAmount, int weight) {
            this.material = material;
            this.chance = Math.max(0.0, Math.min(1.0, chance));
            this.minAmount = Math.max(1, minAmount);
            this.maxAmount = Math.max(minAmount, maxAmount);
            this.weight = Math.max(1, weight);
        }

        public LootEntry addEnchantment(Enchantment enchantment, int minLevel, int maxLevel, double chance) {
            enchantments.put(enchantment, new EnchantmentData(minLevel, maxLevel, chance));
            return this;
        }

        public LootEntry addEnchantment(Enchantment enchantment, int level, double chance) {
            return addEnchantment(enchantment, level, level, chance);
        }

        public LootEntry setCustomName(String name) {
            this.customName = name;
            return this;
        }

        public LootEntry setLore(String... loreLines) {
            this.lore = Arrays.asList(loreLines);
            return this;
        }

        public LootEntry setLore(List<String> lore) {
            this.lore = new ArrayList<>(lore);
            return this;
        }

        public LootEntry setGlowing(boolean glowing) {
            this.glowing = glowing;
            return this;
        }

        public LootEntry setUnbreakable(boolean unbreakable) {
            this.unbreakable = unbreakable;
            return this;
        }

        public LootEntry setRarity(String rarity) {
            this.rarity = rarity;
            return this;
        }

        public LootEntry setPreMadeItem(ItemStack item) {
            this.preMadeItem = item;
            return this;
        }

        // Getters
        public Material getMaterial() { return material; }
        public double getChance() { return chance; }
        public int getMinAmount() { return minAmount; }
        public int getMaxAmount() { return maxAmount; }
        public int getWeight() { return weight; }
        public String getRarity() { return rarity; }

        public ItemStack generateItem(Random random) {
            if (random.nextDouble() > chance) {
                return null;
            }

            int amount = minAmount;
            if (maxAmount > minAmount) {
                amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
            }

            // Use pre-made item if available (for YakRealms items)
            if (preMadeItem != null) {
                ItemStack item = preMadeItem.clone();
                item.setAmount(amount);
                return item;
            }

            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                // Set custom name
                if (customName != null) {
                    meta.setDisplayName(customName);
                }

                // Set lore with instant refresh indicators
                List<String> finalLore = new ArrayList<>();
                if (lore != null && !lore.isEmpty()) {
                    finalLore.addAll(lore);
                }

                // Add instant refresh system indicators for special items
                if (glowing || unbreakable || !enchantments.isEmpty()) {
                    finalLore.add("");
                    finalLore.add("§8From Instant Refresh Vault");
                }

                if (!finalLore.isEmpty()) {
                    meta.setLore(finalLore);
                }

                // Set unbreakable
                if (unbreakable) {
                    meta.setUnbreakable(true);
                }

                // Add enchantments
                for (Map.Entry<Enchantment, EnchantmentData> enchEntry : enchantments.entrySet()) {
                    EnchantmentData enchData = enchEntry.getValue();
                    if (random.nextDouble() < enchData.chance) {
                        int level = enchData.minLevel;
                        if (enchData.maxLevel > enchData.minLevel) {
                            level = random.nextInt(enchData.maxLevel - enchData.minLevel + 1) + enchData.minLevel;
                        }

                        if (material == Material.ENCHANTED_BOOK) {
                            BookMeta bookMeta = (BookMeta) meta;
                            bookMeta.addEnchant(enchEntry.getKey(), level, true);
                        } else {
                            meta.addEnchant(enchEntry.getKey(), level, true);
                        }
                    }
                }

                // Add glowing effect
                if (glowing) {
                    if (!meta.hasEnchants()) {
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    }
                }

                item.setItemMeta(meta);
            }

            return item;
        }

        private static class EnchantmentData {
            final int minLevel;
            final int maxLevel;
            final double chance;

            EnchantmentData(int minLevel, int maxLevel, double chance) {
                this.minLevel = Math.max(1, minLevel);
                this.maxLevel = Math.max(minLevel, maxLevel);
                this.chance = Math.max(0.0, Math.min(1.0, chance));
            }
        }
    }

    // ========================================
    // MAIN BUILDER METHODS
    // ========================================

    public LootTableBuilder addPool(LootPool pool) {
        pools.add(pool);
        return this;
    }

    public LootPool createPool() {
        return new LootPool();
    }

    public LootPool createPool(String name) {
        return new LootPool(name);
    }

    // Convenience method for simple items (creates a single-entry pool)
    public LootTableBuilder addItem(Material material, double chance, int minAmount, int maxAmount, int weight) {
        LootPool pool = new LootPool("simple_" + material.name().toLowerCase());
        pool.addEntry(new LootEntry(material, chance, minAmount, maxAmount, weight));
        pools.add(pool);
        return this;
    }

    public LootTableBuilder addItem(Material material, double chance, int minAmount, int maxAmount) {
        return addItem(material, chance, minAmount, maxAmount, 50);
    }

    /**
     * Generates loot based on all configured pools - enhanced for instant refresh
     */
    public List<ItemStack> generateLoot(int baseRolls, int bonusRolls) {
        List<ItemStack> loot = new ArrayList<>();
        totalGenerations++;

        // Generate from all pools
        for (LootPool pool : pools) {
            List<ItemStack> poolLoot = pool.generateLoot(random);
            loot.addAll(poolLoot);

            // Track material distribution for balancing
            for (ItemStack item : poolLoot) {
                materialDistribution.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }

        // Add additional global rolls if specified
        if (bonusRolls > 0 && !pools.isEmpty()) {
            int additionalRolls = random.nextInt(bonusRolls + 1);
            for (int i = 0; i < additionalRolls; i++) {
                LootPool randomPool = pools.get(random.nextInt(pools.size()));
                List<ItemStack> bonusLoot = randomPool.generateLoot(random);
                loot.addAll(bonusLoot);

                // Track bonus loot too
                for (ItemStack item : bonusLoot) {
                    materialDistribution.merge(item.getType(), item.getAmount(), Integer::sum);
                }
            }
        }

        // Ensure at least one item if pools exist
        if (loot.isEmpty() && !pools.isEmpty()) {
            LootPool fallbackPool = pools.get(0);
            if (!fallbackPool.getEntries().isEmpty()) {
                LootEntry fallback = fallbackPool.getEntries().get(0);
                ItemStack fallbackItem = new ItemStack(fallback.getMaterial(), fallback.getMinAmount());
                loot.add(fallbackItem);
                materialDistribution.merge(fallback.getMaterial(), fallback.getMinAmount(), Integer::sum);
            }
        }

        return loot;
    }

    public List<LootPool> getPools() {
        return new ArrayList<>(pools);
    }

    public void clear() {
        pools.clear();
    }

    public boolean isEmpty() {
        return pools.isEmpty();
    }

    // ========================================
    // ENHANCED STATIC FACTORY METHODS FOR INSTANT REFRESH
    // ========================================

    public static LootTableBuilder tier1Normal() {
        LootTableBuilder builder = new LootTableBuilder();

        // YakRealms Crates Pool
        LootPool cratesPool = builder.createPool("yakrealms_crates")
                .setRolls(1, 2)
                .setBonusChance(0.15)
                .setQualityMultiplier(1.1);

        // Basic and Medium crates for tier 1 - Using actual YakRealms crates
        addYakRealmsCrate(cratesPool, 1, false, 0.40, 1, 1, 40); // Basic crate
        addYakRealmsCrate(cratesPool, 2, false, 0.25, 1, 1, 25); // Medium crate

        builder.addPool(cratesPool);

        // Orbs Pool 
        LootPool orbsPool = builder.createPool("orbs")
                .setRolls(0, 1)
                .setBonusChance(0.10)
                .setQualityMultiplier(1.0);

        addYakRealmsOrb(orbsPool, false, 0.15, 1, 1, 15); // Normal orb

        builder.addPool(orbsPool);

        // Gems Pool
        LootPool gemsPool = builder.createPool("gems")
                .setRolls(2, 4)
                .setBonusChance(0.25)
                .setQualityMultiplier(1.1);

        addYakRealmsGems(gemsPool, 0.80, 5, 25, 80); // YakRealms gems
        addYakRealmsGemPouch(gemsPool, 1, 0.25, 1, 1, 25); // Small gem pouch

        builder.addPool(gemsPool);

        return builder;
    }

    public static LootTableBuilder tier1Food() {
        LootTableBuilder builder = new LootTableBuilder();

        // More gem-focused rewards for "nourishment" theme
        LootPool gemsPool = builder.createPool("nourishing_gems")
                .setRolls(3, 6)
                .setBonusChance(0.30)
                .setQualityMultiplier(1.2);

        addYakRealmsGems(gemsPool, 0.90, 8, 35, 90); // Enhanced gem drops
        addYakRealmsGemPouch(gemsPool, 1, 0.40, 1, 2, 40); // Multiple small gem pouches

        builder.addPool(gemsPool);

        // Orbs for "nourishing" equipment enhancement
        LootPool orbsPool = builder.createPool("nourishing_orbs")
                .setRolls(0, 2)
                .setBonusChance(0.20)
                .setQualityMultiplier(1.1);

        addYakRealmsOrb(orbsPool, false, 0.25, 1, 1, 25); // Normal orbs

        builder.addPool(orbsPool);

        // Basic Crates
        LootPool cratesPool = builder.createPool("nourishing_crates")
                .setRolls(1, 1)
                .setBonusChance(0.15)
                .setQualityMultiplier(1.0);

        addYakRealmsCrate(cratesPool, 1, false, 0.35, 1, 1, 35); // Basic crates

        builder.addPool(cratesPool);

        return builder;
    }

    public static LootTableBuilder tier1Elite() {
        LootTableBuilder builder = new LootTableBuilder();

        // Elite Crates Pool - Higher tier crates with YakRealms system
        LootPool eliteCratesPool = builder.createPool("elite_crates")
                .setRolls(1, 2)
                .setBonusChance(0.35)
                .setQualityMultiplier(1.3);

        // Medium Mystical Crate (Tier 2)
        addYakRealmsCrate(eliteCratesPool, 2, false, 0.30, 1, 1, 30);
        // War Mystical Crate (Tier 3)
        addYakRealmsCrate(eliteCratesPool, 3, false, 0.20, 1, 1, 20);

        builder.addPool(eliteCratesPool);

        // Enhanced Orbs Pool with YakRealms system
        LootPool orbsPool = builder.createPool("elite_orbs")
                .setRolls(0, 2)
                .setBonusChance(0.40)
                .setQualityMultiplier(1.2);

        // Normal Orbs
        addYakRealmsOrb(orbsPool, false, 0.35, 1, 2, 35);
        // Legendary Orbs (small chance)
        addYakRealmsOrb(orbsPool, true, 0.05, 1, 1, 5);

        builder.addPool(orbsPool);

        // Premium Gems Pool with YakRealms system
        LootPool gemsPool = builder.createPool("elite_gems")
                .setRolls(2, 4)
                .setBonusChance(0.30)
                .setQualityMultiplier(1.2);

        // Elite tier gem pouches (1000g-5000g range)
        addYakRealmsGemPouch(gemsPool, 1000, 0.40, 1, 1, 40);
        addYakRealmsGemPouch(gemsPool, 2500, 0.30, 1, 1, 30);
        addYakRealmsGemPouch(gemsPool, 5000, 0.15, 1, 1, 15);

        builder.addPool(gemsPool);

        return builder;
    }

    public static LootTableBuilder tier6Elite() {
        LootTableBuilder builder = new LootTableBuilder();

        // Mythical Frozen Crates Pool - Top tier crates with YakRealms system
        LootPool mythicalCratesPool = builder.createPool("mythical_crates")
                .setRolls(2, 4)
                .setBonusChance(0.70)
                .setQualityMultiplier(2.0);

        // Legendary Mystical Crate (Tier 5)
        addYakRealmsCrate(mythicalCratesPool, 5, false, 0.60, 1, 2, 60);
        // Frozen Mystical Crate (Tier 6)
        addYakRealmsCrate(mythicalCratesPool, 6, false, 0.40, 1, 1, 40);

        builder.addPool(mythicalCratesPool);

        // Legendary Orbs Pool - High chance for legendary orbs with YakRealms system
        LootPool legendaryOrbsPool = builder.createPool("legendary_orbs")
                .setRolls(1, 3)
                .setBonusChance(0.60)
                .setQualityMultiplier(1.8);

        // High chance for Legendary Orbs
        addYakRealmsOrb(legendaryOrbsPool, true, 0.75, 1, 2, 75);
        // Normal Orbs as backup
        addYakRealmsOrb(legendaryOrbsPool, false, 0.50, 1, 3, 50);

        builder.addPool(legendaryOrbsPool);

        // Massive Gems Pool - Epic gem rewards
        LootPool epicGemsPool = builder.createPool("epic_gems")
                .setRolls(4, 8)
                .setBonusChance(0.50)
                .setQualityMultiplier(2.0);

        // Epic gem pouches for highest tier using YakRealms system
        addYakRealmsGemPouch(epicGemsPool, 10000, 0.40, 1, 2, 40);
        addYakRealmsGemPouch(epicGemsPool, 50000, 0.25, 1, 1, 25);
        addYakRealmsGemPouch(epicGemsPool, 100000, 0.20, 1, 1, 20);

        builder.addPool(epicGemsPool);

        // Ultra Rare Special Items Pool with YakRealms system
        LootPool ultraRarePool = builder.createPool("ultra_rare_specials")
                .setRolls(0, 2)
                .setBonusChance(0.80)
                .setQualityMultiplier(2.5);

        // Halloween Legendary Crates (Tier 5)
        addYakRealmsCrate(ultraRarePool, 5, true, 0.25, 1, 2, 25);
        // Halloween Frozen Crates (Tier 6)
        addYakRealmsCrate(ultraRarePool, 6, true, 0.10, 1, 1, 10);

        builder.addPool(ultraRarePool);

        return builder;
    }

    // ========================================
    // ENHANCED DYNAMIC TIER BUILDERS
    // ========================================

    /**
     * Create a loot table builder for any tier and type combination - enhanced for instant refresh
     */
    public static LootTableBuilder createTierBuilder(int tier, String type) {
        switch (tier) {
            case 1:
                switch (type.toLowerCase()) {
                    case "food": return tier1Food();
                    case "elite": return tier1Elite();
                    default: return tier1Normal();
                }
            case 6:
                if ("elite".equalsIgnoreCase(type)) {
                    return tier6Elite();
                }
                // Fall through to scaled version
            default:
                return createEnhancedScaledBuilder(tier, type);
        }
    }

    private static LootTableBuilder createEnhancedScaledBuilder(int tier, String type) {
        LootTableBuilder builder = new LootTableBuilder();

        // Enhanced scaling for instant refresh system
        double qualityMultiplier = 0.7 + (tier * 0.2); // Improved from 0.5 + (tier * 0.15)
        int quantityBonus = tier;
        double enchantChance = Math.min(0.9, 0.3 + (tier * 0.12)); // Improved

        LootPool mainPool = builder.createPool("main_loot")
                .setRolls(3 + tier, 5 + tier * 2) // Increased from (2 + tier, 4 + tier * 2)
                .setBonusChance(0.15 + (tier * 0.06)) // Improved
                .setQualityMultiplier(1.0 + (tier * 0.1));

        switch (type.toLowerCase()) {
            case "food":
                addEnhancedScaledFoodLoot(mainPool, tier, qualityMultiplier, quantityBonus);
                break;
            case "elite":
                addEnhancedScaledEliteLoot(mainPool, tier, qualityMultiplier, quantityBonus, enchantChance);
                break;
            default:
                addEnhancedScaledNormalLoot(mainPool, tier, qualityMultiplier, quantityBonus, enchantChance);
                break;
        }

        builder.addPool(mainPool);

        // Enhanced enchanted items pool for higher tiers
        if (tier >= 3) {
            LootPool enchantedPool = builder.createPool("enchanted_items")
                    .setRolls(0, 2) // Increased from (0,1)
                    .setBonusChance(0.15 + (tier * 0.07)) // Improved
                    .setQualityMultiplier(1.0 + (tier * 0.15));

            addEnhancedScaledEnchantedItems(enchantedPool, tier, enchantChance);
            builder.addPool(enchantedPool);
        }

        return builder;
    }

    private static void addEnhancedScaledNormalLoot(LootPool pool, int tier, double quality, int quantity, double enchantChance) {
        // Use YakRealms system for tier-appropriate crates
        addYakRealmsCrate(pool, Math.min(tier, 6), false, 0.6 + quality * 0.15, 1, 1 + quantity / 3, 60);

        // Add orbs scaled by tier using YakRealms system
        if (tier >= 2) {
            addYakRealmsOrb(pool, false, 0.25 + quality * 0.15, 1, Math.max(1, tier / 3), 25);
        }
        if (tier >= 4) {
            addYakRealmsOrb(pool, true, 0.08 + quality * 0.12, 1, 1, 8);
        }

        // Add gem pouches scaled by tier using YakRealms system
        if (tier >= 2) {
            int gemAmount = getGemAmountForTier(tier);
            addYakRealmsGemPouch(pool, gemAmount, 0.20 + quality * 0.15, 1, Math.max(1, tier / 3), 20);
        }
    }

    private static void addEnhancedScaledFoodLoot(LootPool pool, int tier, double quality, int quantity) {
        // Food vaults focus on gems and gem pouches for "nourishment" using YakRealms system
        
        // Multiple gem pouches for "nourishment"
        if (tier >= 1) {
            int gemAmount = getGemAmountForTier(Math.min(tier, 4)); // Cap at tier 4 for food vaults
            addYakRealmsGemPouch(pool, gemAmount, 0.50 + quality * 0.20, 1, 1 + quantity / 2, 50);
        }

        // Orbs for equipment "nourishment"
        if (tier >= 2) {
            addYakRealmsOrb(pool, false, 0.35 + quality * 0.20, 1, Math.max(1, tier / 2), 35);
        }

        // Slightly better crates for food vaults using YakRealms system
        addYakRealmsCrate(pool, Math.min(tier + 1, 6), false, 0.40 + quality * 0.10, 1, 1, 40);
    }

    private static void addEnhancedScaledEliteLoot(LootPool pool, int tier, double quality, int quantity, double enchantChance) {
        // Elite vaults get higher tier crates using YakRealms system (2 tiers higher)
        addYakRealmsCrate(pool, Math.min(tier + 2, 6), false, 0.60 + quality * 0.20, 1, 1 + quantity / 2, 60);

        // High chance for both normal and legendary orbs using YakRealms system
        addYakRealmsOrb(pool, false, 0.55 + quality * 0.20, 1, Math.max(1, tier / 2), 55);
        if (tier >= 3) {
            addYakRealmsOrb(pool, true, 0.25 + quality * 0.25, 1, Math.max(1, tier / 3), 25);
        }

        // High-tier gem pouches using YakRealms system
        if (tier >= 2) {
            int gemAmount = getGemAmountForTier(Math.min(tier + 1, 6)); // +1 tier pouches
            addYakRealmsGemPouch(pool, gemAmount, 0.40 + quality * 0.25, 1, Math.max(1, tier / 3), 40);
        }

        // Halloween crates for higher tiers using YakRealms system
        if (tier >= 4) {
            addYakRealmsCrate(pool, Math.min(tier + 2, 6), true, 0.15 + quality * 0.15, 1, 1, 15);
        }
    }

    private static void addEnhancedScaledEnchantedItems(LootPool pool, int tier, double enchantChance) {
        // Replace enchanted items with more orbs and enhanced items using YakRealms system
        if (tier >= 3) {
            // Additional normal orbs for higher tiers
            addYakRealmsOrb(pool, false, 0.30 + tier * 0.05, 1, Math.max(1, tier / 3), 30);
        }

        if (tier >= 4) {
            // Legendary orbs for high tiers
            addYakRealmsOrb(pool, true, 0.15 + tier * 0.03, 1, 1, 15);

            // Premium gem pouches for high tiers
            int gemAmount = getGemAmountForTier(Math.min(tier, 5)); // Cap at tier 5 to avoid insane containers
            addYakRealmsGemPouch(pool, gemAmount, 0.20 + tier * 0.05, 1, 1, 20);
        }
    }

    // ========================================
    // ENHANCED UTILITY METHODS
    // ========================================

    /**
     * Get enhanced statistics about this loot table
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_pools", pools.size());
        stats.put("total_entries", pools.stream().mapToInt(pool -> pool.getEntries().size()).sum());
        stats.put("total_generations", totalGenerations);

        Map<String, Integer> materialCounts = new HashMap<>();
        for (LootPool pool : pools) {
            for (LootEntry entry : pool.getEntries()) {
                materialCounts.merge(entry.getMaterial().name(), 1, Integer::sum);
            }
        }
        stats.put("unique_materials", materialCounts.size());
        stats.put("material_distribution", materialCounts);
        stats.put("generation_distribution", new HashMap<>(materialDistribution));

        // Enhanced stats for instant refresh
        double avgQualityMultiplier = pools.stream()
                .mapToDouble(LootPool::getQualityMultiplier)
                .average()
                .orElse(1.0);
        stats.put("avg_quality_multiplier", avgQualityMultiplier);
        stats.put("instant_refresh_optimized", true);

        return stats;
    }

    /**
     * Create a copy of this loot table builder
     */
    public LootTableBuilder copy() {
        LootTableBuilder copy = new LootTableBuilder();
        for (LootPool pool : pools) {
            LootPool poolCopy = new LootPool(pool.getPoolName())
                    .setRolls(pool.minRolls, pool.maxRolls)
                    .setBonusChance(pool.bonusChance)
                    .setQualityMultiplier(pool.qualityMultiplier);

            for (LootEntry entry : pool.getEntries()) {
                poolCopy.addEntry(entry); // Note: This creates a reference, not a deep copy
            }

            copy.addPool(poolCopy);
        }
        return copy;
    }

    /**
     * Merge another loot table builder into this one
     */
    public LootTableBuilder merge(LootTableBuilder other) {
        for (LootPool pool : other.getPools()) {
            pools.add(pool);
        }
        return this;
    }

    /**
     * Get performance metrics for instant refresh system
     */
    public String getPerformanceMetrics() {
        StringBuilder metrics = new StringBuilder();
        metrics.append("§6Loot Table Performance (Instant Refresh):\n");
        metrics.append("§7Total Generations: §f").append(totalGenerations).append("\n");
        metrics.append("§7Active Pools: §f").append(pools.size()).append("\n");

        if (totalGenerations > 0) {
            int totalItems = materialDistribution.values().stream().mapToInt(Integer::intValue).sum();
            double avgItemsPerGeneration = (double) totalItems / totalGenerations;
            metrics.append("§7Avg Items/Generation: §f").append(String.format("%.1f", avgItemsPerGeneration)).append("\n");
        }

        double avgQuality = pools.stream()
                .mapToDouble(LootPool::getQualityMultiplier)
                .average()
                .orElse(1.0);
        metrics.append("§7Avg Quality Multiplier: §f").append(String.format("%.2f", avgQuality)).append("\n");
        metrics.append("§a⚡ Instant Refresh Optimized: §f✓");

        return metrics.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LootTableBuilder{pools=").append(pools.size()).append(", entries=");
        sb.append(pools.stream().mapToInt(pool -> pool.getEntries().size()).sum());
        sb.append(", generations=").append(totalGenerations);
        sb.append(", instant_refresh=true}");
        return sb.toString();
    }

    // ========================================
    // YAKREALMS INTEGRATION HELPER METHODS
    // ========================================

    /**
     * Get the crate name for a given tier
     */
    private static String getCrateNameForTier(int tier) {
        return switch (tier) {
            case 1 -> "Basic";
            case 2 -> "Medium";
            case 3 -> "War";
            case 4 -> "Ancient";
            case 5 -> "Legendary";
            case 6 -> "Frozen";
            default -> "Basic";
        };
    }

    /**
     * Get the crate symbol for a given tier
     */
    private static String getCrateSymbolForTier(int tier) {
        return switch (tier) {
            case 1 -> "◆"; // ◆
            case 2 -> "◇"; // ◇
            case 3 -> "★"; // ★
            case 4 -> "✦"; // ✦
            case 5 -> "★"; // ★ (reused for legendary)
            case 6 -> "❄"; // ❄
            default -> "◆";
        };
    }

    /**
     * Get the crate color for a given tier
     */
    private static String getCrateColorForTier(int tier) {
        return switch (tier) {
            case 1 -> "§7"; // §7 Gray
            case 2 -> "§a"; // §a Green
            case 3 -> "§b"; // §b Aqua
            case 4 -> "§d"; // §d Light Purple
            case 5 -> "§6"; // §6 Gold
            case 6 -> "§6"; // §6 Gold (Frozen)
            default -> "§7";
        };
    }

    /**
     * Get the crate material for a given tier
     */
    private static Material getCrateMaterialForTier(int tier) {
        return switch (tier) {
            case 1, 2 -> Material.CHEST;
            case 3 -> Material.TRAPPED_CHEST;
            case 4, 5, 6 -> Material.ENDER_CHEST;
            default -> Material.CHEST;
        };
    }

    /**
     * Get the gem amount for a given tier for YakRealms gem pouches
     */
    private static int getGemAmountForTier(int tier) {
        return switch (tier) {
            case 1 -> 200;
            case 2 -> 500;
            case 3 -> 1000;
            case 4 -> 2500;
            case 5 -> 10000;
            case 6 -> 50000;
            default -> 200;
        };
    }

    /**
     * Get the tier quality name
     */
    private static String getTierQuality(int tier) {
        return switch (tier) {
            case 1 -> "§fCommon";
            case 2 -> "§aUncommon";
            case 3 -> "§bRare";
            case 4 -> "§dEpic";
            case 5 -> "§6Legendary";
            case 6 -> "§6Mythical";
            default -> "§fCommon";
        };
    }

    /**
     * Get the tier rarity string
     */
    private static String getTierRarity(int tier) {
        return switch (tier) {
            case 1 -> "COMMON";
            case 2 -> "UNCOMMON";
            case 3 -> "RARE";
            case 4 -> "EPIC";
            case 5 -> "LEGENDARY";
            case 6 -> "MYTHIC";
            default -> "COMMON";
        };
    }

    /**
     * Get the gem pouch name for a given tier
     */
    private static String getGemPouchNameForTier(int tier) {
        return switch (tier) {
            case 1 -> "§fSmall Gem Pouch";
            case 2 -> "§aMedium Gem Sack";
            case 3 -> "§bLarge Gem Satchel";
            case 4 -> "§dGigantic Gem Container";
            case 5 -> "§6Legendary Gem Container";
            case 6 -> "§cInsane Gem Container";
            default -> "§fSmall Gem Pouch";
        };
    }

    /**
     * Get the gem pouch lore for a given tier
     */
    private static String getGemPouchLoreForTier(int tier) {
        return switch (tier) {
            case 1 -> "§7A small linen pouch that holds §l200g";
            case 2 -> "§7A medium wool sack that holds §l350g";
            case 3 -> "§7A large leather satchel that holds §l500g";
            case 4 -> "§7A giant container that holds §l3000g";
            case 5 -> "§7A giant container that holds §l8000g";
            case 6 -> "§7A giant container that holds §l100000g";
            default -> "§7A small linen pouch that holds §l200g";
        };
    }

    // ========================================
    // YAKREALMS ITEM GENERATION HELPERS
    // ========================================

    /**
     * Add YakRealms crate to a loot pool using CrateFactory
     */
    private static void addYakRealmsCrate(LootPool pool, int tier, boolean halloween, double chance, int minAmount, int maxAmount, int weight) {
        try {
            // Try to use CrateFactory
            ItemStack crate = createYakRealmsCrateItem(tier, halloween);
            pool.addPreMadeItem(crate, chance, minAmount, maxAmount, weight);
        } catch (Exception e) {
            // Fallback to basic chest
            pool.addItem(Material.CHEST, chance, minAmount, maxAmount, weight);
        }
    }

    /**
     * Add YakRealms orb to a loot pool using OrbGenerator
     */
    private static void addYakRealmsOrb(LootPool pool, boolean legendary, double chance, int minAmount, int maxAmount, int weight) {
        try {
            // Try to use OrbGenerator
            ItemStack orb = createYakRealmsOrbItem(legendary);
            pool.addPreMadeItem(orb, chance, minAmount, maxAmount, weight);
        } catch (Exception e) {
            // Fallback to basic magma cream
            pool.addItem(Material.MAGMA_CREAM, chance, minAmount, maxAmount, weight);
        }
    }

    /**
     * Add YakRealms gem pouch to a loot pool using GemPouchManager
     */
    private static void addYakRealmsGemPouch(LootPool pool, int tier, double chance, int minAmount, int maxAmount, int weight) {
        try {
            // Try to use GemPouchManager
            ItemStack pouch = createYakRealmsGemPouchItem(tier);
            pool.addPreMadeItem(pouch, chance, minAmount, maxAmount, weight);
        } catch (Exception e) {
            // Fallback to basic ink sac
            pool.addItem(Material.INK_SAC, chance, minAmount, maxAmount, weight);
        }
    }

    /**
     * Add YakRealms gems with proper metadata
     */
    private static void addYakRealmsGems(LootPool pool, double chance, int minAmount, int maxAmount, int weight) {
        try {
            ItemStack gems = createYakRealmsGemsItem();
            pool.addPreMadeItem(gems, chance, minAmount, maxAmount, weight);
        } catch (Exception e) {
            // Fallback to basic emerald
            pool.addItem(Material.EMERALD, chance, minAmount, maxAmount, weight);
        }
    }

    // Item creation methods using the actual YakRealms systems
    private static ItemStack createYakRealmsCrateItem(int tier, boolean halloween) {
        try {
            // Try to use CrateFactory from the game
            com.rednetty.server.core.mechanics.item.crates.CrateFactory factory = new com.rednetty.server.core.mechanics.item.crates.CrateFactory();
            com.rednetty.server.core.mechanics.item.crates.types.CrateType crateType = getCrateTypeForTier(tier);
            return factory.createCrate(crateType, halloween);
        } catch (Exception e) {
            // Create manual crate as fallback
            return createManualYakRealmsCrate(tier, halloween);
        }
    }

    private static ItemStack createYakRealmsOrbItem(boolean legendary) {
        try {
            // Try to use OrbGenerator from the game
            com.rednetty.server.core.mechanics.item.orb.OrbGenerator generator = new com.rednetty.server.core.mechanics.item.orb.OrbGenerator();
            return legendary ? generator.createLegendaryOrb(false) : generator.createNormalOrb(false);
        } catch (Exception e) {
            // Create manual orb as fallback
            return createManualYakRealmsOrb(legendary);
        }
    }

    private static ItemStack createYakRealmsGemPouchItem(int tier) {
        try {
            // Try to use GemPouchManager from the game
            return com.rednetty.server.core.mechanics.economy.GemPouchManager.createGemPouch(tier, false);
        } catch (Exception e) {
            // Create manual gem pouch as fallback
            return createManualYakRealmsGemPouch(tier);
        }
    }

    private static ItemStack createYakRealmsGemsItem() {
        try {
            ItemStack gems = new ItemStack(Material.EMERALD, 1);
            ItemMeta meta = gems.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§fGem");
                meta.setLore(Arrays.asList("§7The currency of Andalucia"));
                gems.setItemMeta(meta);
            }
            return gems;
        } catch (Exception e) {
            return new ItemStack(Material.EMERALD, 1);
        }
    }

    // Helper methods for crate type conversion
    private static com.rednetty.server.core.mechanics.item.crates.types.CrateType getCrateTypeForTier(int tier) {
        return switch (tier) {
            case 1 -> com.rednetty.server.core.mechanics.item.crates.types.CrateType.BASIC;
            case 2 -> com.rednetty.server.core.mechanics.item.crates.types.CrateType.MEDIUM;
            case 3 -> com.rednetty.server.core.mechanics.item.crates.types.CrateType.WAR;
            case 4 -> com.rednetty.server.core.mechanics.item.crates.types.CrateType.ANCIENT;
            case 5 -> com.rednetty.server.core.mechanics.item.crates.types.CrateType.LEGENDARY;
            case 6 -> com.rednetty.server.core.mechanics.item.crates.types.CrateType.FROZEN;
            default -> com.rednetty.server.core.mechanics.item.crates.types.CrateType.BASIC;
        };
    }

    // Manual creation fallbacks (same as LootChestManager methods)
    private static ItemStack createManualYakRealmsCrate(int tier, boolean halloween) {
        Material crateMaterial = halloween ? Material.CARVED_PUMPKIN : getCrateMaterialForTier(tier);
        ItemStack crate = new ItemStack(crateMaterial);
        ItemMeta meta = crate.getItemMeta();
        
        if (meta != null) {
            String crateName = getCrateNameForTier(tier);
            String crateSymbol = getCrateSymbolForTier(tier);
            String crateColor = getCrateColorForTier(tier);
            String qualityName = getTierQuality(tier);
            
            String displayName = crateColor + crateSymbol + " " + crateColor;
            if (halloween) displayName += "Halloween ";
            displayName += crateName + " Mystical Crate " + crateColor + crateSymbol;
            
            meta.setDisplayName(displayName);
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Quality: " + crateColor + qualityName);
            lore.add("§7Tier: §7[" + tier + "/6]");
            if (halloween) {
                lore.add("§7Season: §6§l🎃 Halloween Special 🎃");
                lore.add("§6🎃 Halloween Bonus Rewards with spooky magic!");
            }
            lore.add("");
            lore.add("§e⚠ §lMystical Contents:");
            lore.add(crateColor + "• Tier " + tier + " treasures");
            lore.add("");
            lore.add("§a⚡ From Vault Chest");
            
            meta.setLore(lore);
            
            // Add glow for higher tiers or halloween
            if (tier >= 3 || halloween) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            
            crate.setItemMeta(meta);
        }
        
        return crate;
    }

    private static ItemStack createManualYakRealmsOrb(boolean legendary) {
        ItemStack orb = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta meta = orb.getItemMeta();
        
        if (meta != null) {
            if (legendary) {
                meta.setDisplayName("§6L§ee§ag§be§cn§dd§ea§fr§gy §6Orb of Alteration");
                meta.setLore(Arrays.asList(
                    "§7Plus 4s Items that have a plus lower than 4.",
                    "§7It also has a extremely high chance of good orbs.",
                    "§a⚡ From Vault Chest"
                ));
                
                // Add glow
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.setDisplayName("§dOrb of Alteration");
                meta.setLore(Arrays.asList(
                    "§7Randomizes stats of selected equipment.",
                    "§a⚡ From Vault Chest"
                ));
            }
            
            orb.setItemMeta(meta);
        }
        
        return orb;
    }

    private static ItemStack createManualYakRealmsGemPouch(int tier) {
        ItemStack pouch = new ItemStack(Material.INK_SAC);
        ItemMeta meta = pouch.getItemMeta();
        
        if (meta != null) {
            String pouchName = getGemPouchNameForTier(tier);
            String pouchLore = getGemPouchLoreForTier(tier);
            
            meta.setDisplayName(pouchName + " §a§l0g");
            
            List<String> lore = new ArrayList<>();
            lore.add(pouchLore);
            if (tier == 6) {
                lore.add("");
                lore.add("§cSoulbound");
            }
            lore.add("§a⚡ From Vault Chest");
            
            meta.setLore(lore);
            
            // Add glow for higher tier pouches
            if (tier >= 4) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            
            pouch.setItemMeta(meta);
        }
        
        return pouch;
    }
}