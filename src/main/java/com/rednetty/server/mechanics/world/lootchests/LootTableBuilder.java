package com.rednetty.server.mechanics.world.lootchests;

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

        // Basic materials pool - enhanced for instant refresh
        LootPool basicPool = builder.createPool("basic_materials")
                .setRolls(2, 5) // Increased from (2,4)
                .setBonusChance(0.15) // Increased from 0.1
                .setQualityMultiplier(1.1);

        basicPool.addItem(Material.IRON_INGOT, 0.85, 2, 4, 85); // Improved
        basicPool.addItem(Material.COAL, 0.95, 3, 10, 95); // Improved
        basicPool.addItem(Material.COPPER_INGOT, 0.75, 2, 6, 75); // Improved
        basicPool.addItem(Material.EMERALD, 0.35, 1, 2, 35); // Improved

        builder.addPool(basicPool);

        // Enhanced tool pool
        LootPool toolPool = builder.createPool("tools")
                .setRolls(0, 1)
                .setBonusChance(0.2); // Increased from 0.15

        LootEntry ironSword = new LootEntry(Material.IRON_SWORD, 0.25, 1, 1, 25); // Increased chance
        ironSword.addEnchantment(Enchantment.SHARPNESS, 1, 3, 0.4); // Improved enchantment
        ironSword.setCustomName("§7Iron Blade");
        ironSword.setRarity("COMMON");
        toolPool.addEntry(ironSword);

        LootEntry ironPickaxe = new LootEntry(Material.IRON_PICKAXE, 0.2, 1, 1, 20); // Increased chance
        ironPickaxe.addEnchantment(Enchantment.EFFICIENCY, 1, 3, 0.35); // Improved enchantment
        ironPickaxe.setCustomName("§7Sturdy Pickaxe");
        ironPickaxe.setRarity("COMMON");
        toolPool.addEntry(ironPickaxe);

        builder.addPool(toolPool);

        return builder;
    }

    public static LootTableBuilder tier1Food() {
        LootTableBuilder builder = new LootTableBuilder();

        LootPool foodPool = builder.createPool("food_items")
                .setRolls(4, 7) // Increased from (3,6)
                .setBonusChance(0.25) // Increased from 0.2
                .setQualityMultiplier(1.1);

        foodPool.addItem(Material.BREAD, 0.95, 2, 5, 95); // Improved
        foodPool.addItem(Material.COOKED_CHICKEN, 0.8, 2, 4, 80); // Improved
        foodPool.addItem(Material.APPLE, 0.85, 2, 3, 85); // Improved
        foodPool.addItem(Material.COOKED_BEEF, 0.6, 1, 3, 60); // Improved
        foodPool.addItem(Material.GOLDEN_APPLE, 0.15, 1, 1, 15); // Added for instant refresh

        builder.addPool(foodPool);

        return builder;
    }

    public static LootTableBuilder tier1Elite() {
        LootTableBuilder builder = new LootTableBuilder();

        LootPool elitePool = builder.createPool("elite_materials")
                .setRolls(3, 5) // Increased from (2,4)
                .setBonusChance(0.3) // Increased from 0.25
                .setQualityMultiplier(1.2);

        elitePool.addItem(Material.GOLD_INGOT, 0.85, 2, 4, 85); // Improved
        elitePool.addItem(Material.REDSTONE, 0.8, 3, 10, 80); // Improved
        elitePool.addItem(Material.LAPIS_LAZULI, 0.7, 2, 5, 70); // Improved
        elitePool.addItem(Material.DIAMOND, 0.3, 1, 2, 30); // Improved

        // Enhanced enchanted book pool
        LootPool bookPool = builder.createPool("enchanted_books")
                .setRolls(0, 2) // Increased from (0,1)
                .setBonusChance(0.4); // Increased from 0.3

        LootEntry enchantedBook = new LootEntry(Material.ENCHANTED_BOOK, 0.5, 1, 1, 50); // Improved chance
        enchantedBook.addEnchantment(Enchantment.SHARPNESS, 1, 4, 0.4); // Improved
        enchantedBook.addEnchantment(Enchantment.EFFICIENCY, 1, 4, 0.4); // Improved
        enchantedBook.addEnchantment(Enchantment.PROTECTION, 1, 3, 0.3);
        enchantedBook.setCustomName("§9Mystical Tome");
        enchantedBook.setLore("§7Contains ancient knowledge", "§7Found in elite vaults", "§a⚡ Instant Refresh System");
        enchantedBook.setRarity("UNCOMMON");
        bookPool.addEntry(enchantedBook);

        builder.addPool(elitePool);
        builder.addPool(bookPool);

        return builder;
    }

    public static LootTableBuilder tier6Elite() {
        LootTableBuilder builder = new LootTableBuilder();

        // Premium materials pool - enhanced for instant refresh
        LootPool premiumPool = builder.createPool("premium_materials")
                .setRolls(5, 10) // Increased from (4,8)
                .setBonusChance(0.6) // Increased from 0.5
                .setQualityMultiplier(1.5);

        premiumPool.addItem(Material.NETHERITE_INGOT, 1.0, 4, 10, 100); // Improved
        premiumPool.addItem(Material.DIAMOND, 1.0, 8, 20, 100); // Improved
        premiumPool.addItem(Material.EMERALD, 1.0, 5, 15, 100); // Improved

        // Ultra rare items pool - enhanced
        LootPool ultraRarePool = builder.createPool("ultra_rare")
                .setRolls(1, 3) // Increased from (1,2)
                .setBonusChance(0.9) // Increased from 0.8
                .setQualityMultiplier(2.0);

        LootEntry elytra = new LootEntry(Material.ELYTRA, 0.6, 1, 1, 60); // Improved chance
        elytra.setCustomName("§5§lMythic Wings");
        elytra.setLore("§7From the legendary T6 vault", "§5Soar through the skies", "§6Unbreakable", "§a⚡ Instant Refresh Reward");
        elytra.setUnbreakable(true);
        elytra.setGlowing(true);
        elytra.setRarity("MYTHIC");
        ultraRarePool.addEntry(elytra);

        LootEntry netherStar = new LootEntry(Material.NETHER_STAR, 0.4, 1, 3, 40); // Improved
        netherStar.setCustomName("§6§lCelestial Star");
        netherStar.setLore("§7A star fallen from the heavens", "§6Radiates mystical energy", "§c§lMYTHIC ITEM", "§a⚡ Instant Refresh System");
        netherStar.setGlowing(true);
        netherStar.setRarity("MYTHIC");
        ultraRarePool.addEntry(netherStar);

        LootEntry dragonEgg = new LootEntry(Material.DRAGON_EGG, 0.02, 1, 1, 2); // Slightly improved
        dragonEgg.setCustomName("§c§l§nDragon's Legacy");
        dragonEgg.setLore("§7The rarest of all treasures", "§cLegends speak of its power", "§4§l§kULTIMATE RARITY§r", "§a⚡ Instant Refresh Miracle");
        dragonEgg.setGlowing(true);
        dragonEgg.setRarity("LEGENDARY");
        ultraRarePool.addEntry(dragonEgg);

        // Enhanced enchanted items pool
        LootPool enchantedPool = builder.createPool("enchanted_items")
                .setRolls(3, 6) // Increased from (2,4)
                .setBonusChance(0.7) // Increased from 0.6
                .setQualityMultiplier(1.8);

        LootEntry mythicSword = new LootEntry(Material.NETHERITE_SWORD, 0.4, 1, 1, 40); // Improved chance
        mythicSword.setCustomName("§c§lVoidbreaker");
        mythicSword.addEnchantment(Enchantment.SHARPNESS, 5, 5, 1.0);
        mythicSword.addEnchantment(Enchantment.UNBREAKING, 3, 3, 0.9); // Improved
        mythicSword.addEnchantment(Enchantment.MENDING, 1, 1, 0.7); // Improved
        mythicSword.addEnchantment(Enchantment.FIRE_ASPECT, 2, 2, 0.8); // Improved
        mythicSword.setLore("§7Forged in the depths of the void", "§cDeals devastating damage", "§6Legendary Weapon", "§a⚡ Instant Refresh Forged");
        mythicSword.setGlowing(true);
        mythicSword.setRarity("MYTHIC");
        enchantedPool.addEntry(mythicSword);

        LootEntry mythicPickaxe = new LootEntry(Material.NETHERITE_PICKAXE, 0.35, 1, 1, 35); // Improved chance
        mythicPickaxe.setCustomName("§6§lEarthshaker");
        mythicPickaxe.addEnchantment(Enchantment.EFFICIENCY, 5, 5, 1.0);
        mythicPickaxe.addEnchantment(Enchantment.UNBREAKING, 3, 3, 0.9); // Improved
        mythicPickaxe.addEnchantment(Enchantment.MENDING, 1, 1, 0.7); // Improved
        mythicPickaxe.addEnchantment(Enchantment.FORTUNE, 3, 3, 0.8); // Improved
        mythicPickaxe.setLore("§7Mines through reality itself", "§6Grants incredible fortune", "§6Legendary Tool", "§a⚡ Instant Refresh Crafted");
        mythicPickaxe.setGlowing(true);
        mythicPickaxe.setRarity("MYTHIC");
        enchantedPool.addEntry(mythicPickaxe);

        // Enhanced enchanted books pool
        LootPool bookPool = builder.createPool("mythic_books")
                .setRolls(3, 7) // Increased from (2,5)
                .setBonusChance(0.7) // Increased from 0.6
                .setQualityMultiplier(1.6);

        LootEntry maxEnchantBook = new LootEntry(Material.ENCHANTED_BOOK, 0.9, 1, 2, 90); // Improved
        maxEnchantBook.addEnchantment(Enchantment.SHARPNESS, 4, 5, 0.7); // Improved
        maxEnchantBook.addEnchantment(Enchantment.EFFICIENCY, 4, 5, 0.7); // Improved
        maxEnchantBook.addEnchantment(Enchantment.PROTECTION, 3, 4, 0.6); // Improved
        maxEnchantBook.addEnchantment(Enchantment.UNBREAKING, 3, 3, 0.8); // Improved
        maxEnchantBook.addEnchantment(Enchantment.MENDING, 1, 1, 0.4); // Improved
        maxEnchantBook.setCustomName("§c§lTome of Ultimate Power");
        maxEnchantBook.setLore("§7Contains the most powerful enchantments", "§cExtremely rare knowledge", "§6Apply to your gear", "§a⚡ Instant Refresh Magic");
        maxEnchantBook.setGlowing(true);
        maxEnchantBook.setRarity("MYTHIC");
        bookPool.addEntry(maxEnchantBook);

        builder.addPool(premiumPool);
        builder.addPool(ultraRarePool);
        builder.addPool(enchantedPool);
        builder.addPool(bookPool);

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
        pool.addItem(Material.IRON_INGOT, 0.8 + quality * 0.12, 2 + quantity, 4 + quantity * 2, 80); // Improved
        pool.addItem(Material.GOLD_INGOT, 0.6 + quality * 0.18, 2 + quantity, 3 + quantity, 60); // Improved
        pool.addItem(Material.COAL, 0.9, 3 + quantity, 10 + quantity, 90); // Improved

        if (tier >= 3) {
            pool.addItem(Material.DIAMOND, 0.4 + quality * 0.25, 1, tier, 40 + tier * 12); // Improved
        }

        if (tier >= 5) {
            pool.addItem(Material.NETHERITE_INGOT, 0.15 + quality * 0.2, 1, tier - 2, 15 + tier * 8); // Improved
        }

        if (tier == 6) {
            pool.addItem(Material.NETHER_STAR, 0.08, 1, 2, 8); // Improved
        }
    }

    private static void addEnhancedScaledFoodLoot(LootPool pool, int tier, double quality, int quantity) {
        pool.addItem(Material.BREAD, 0.9 + quality * 0.05, 2 + quantity, 5 + quantity, 90); // Improved
        pool.addItem(Material.COOKED_BEEF, 0.7 + quality * 0.15, 2 + quantity, 4 + quantity, 70); // Improved
        pool.addItem(Material.APPLE, 0.8, 2 + quantity, 3 + quantity, 80); // Improved

        if (tier >= 3) {
            pool.addItem(Material.GOLDEN_APPLE, 0.3 + quality * 0.25, 1, tier / 2 + 2, 30 + tier * 12); // Improved
        }

        if (tier >= 5) {
            pool.addItem(Material.ENCHANTED_GOLDEN_APPLE, 0.08 + tier * 0.03, 1, 2, 8 + tier * 2); // Improved
        }
    }

    private static void addEnhancedScaledEliteLoot(LootPool pool, int tier, double quality, int quantity, double enchantChance) {
        pool.addItem(Material.DIAMOND, 0.9 + quality * 0.05, 2 + quantity, 3 + quantity * 2, 90); // Improved
        pool.addItem(Material.EMERALD, 0.7 + quality * 0.15, 2 + quantity, 2 + quantity, 70); // Improved

        // Enhanced enchanted books with scaling
        LootEntry enchantedBook = new LootEntry(Material.ENCHANTED_BOOK, 0.5 + quality * 0.35, 1, tier / 2 + 2, 50 + tier * 12); // Improved
        enchantedBook.addEnchantment(Enchantment.SHARPNESS, 1, Math.min(5, tier + 1), enchantChance);
        enchantedBook.addEnchantment(Enchantment.EFFICIENCY, 1, Math.min(5, tier + 1), enchantChance);
        if (tier >= 4) {
            enchantedBook.addEnchantment(Enchantment.MENDING, 1, 1, enchantChance * 0.4); // Improved
        }
        enchantedBook.setCustomName("§9Enhanced Tome T" + tier);
        enchantedBook.setLore("§7Tier " + tier + " enchantments", "§9Elite vault reward", "§a⚡ Instant Refresh Enhanced");
        enchantedBook.setRarity(tier >= 5 ? "MYTHIC" : tier >= 3 ? "RARE" : "UNCOMMON");
        pool.addEntry(enchantedBook);

        if (tier >= 4) {
            pool.addItem(Material.NETHERITE_INGOT, 0.2 + quality * 0.2, 1, tier - 1, 20 + tier * 8); // Improved
        }
    }

    private static void addEnhancedScaledEnchantedItems(LootPool pool, int tier, double enchantChance) {
        // Enhanced scaled enchanted tools
        if (tier >= 3) {
            LootEntry enchantedSword = new LootEntry(Material.DIAMOND_SWORD, 0.15 + tier * 0.07, 1, 1, 15 + tier * 7); // Improved
            enchantedSword.addEnchantment(Enchantment.SHARPNESS, Math.max(1, tier - 1), Math.min(5, tier + 1), enchantChance);
            enchantedSword.addEnchantment(Enchantment.UNBREAKING, 1, Math.min(3, tier), enchantChance * 0.8); // Improved
            enchantedSword.setCustomName("§b§lForged Blade T" + tier);
            enchantedSword.setLore("§7Tier " + tier + " weapon", "§bCrafted with care", "§a⚡ Instant Refresh Forged");
            enchantedSword.setRarity(tier >= 5 ? "MYTHIC" : "RARE");
            pool.addEntry(enchantedSword);
        }

        if (tier >= 4) {
            LootEntry enchantedPickaxe = new LootEntry(Material.DIAMOND_PICKAXE, 0.12 + tier * 0.06, 1, 1, 12 + tier * 6); // Improved
            enchantedPickaxe.addEnchantment(Enchantment.EFFICIENCY, Math.max(1, tier - 1), Math.min(5, tier + 1), enchantChance);
            enchantedPickaxe.addEnchantment(Enchantment.UNBREAKING, 1, Math.min(3, tier), enchantChance * 0.8); // Improved
            if (tier >= 5) {
                enchantedPickaxe.addEnchantment(Enchantment.FORTUNE, 1, 3, enchantChance * 0.6); // Improved
            }
            enchantedPickaxe.setCustomName("§b§lMaster's Pickaxe T" + tier);
            enchantedPickaxe.setLore("§7Tier " + tier + " tool", "§bMasterfully crafted", "§a⚡ Instant Refresh Crafted");
            enchantedPickaxe.setRarity(tier >= 5 ? "MYTHIC" : "RARE");
            pool.addEntry(enchantedPickaxe);
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
}