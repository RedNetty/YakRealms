package com.rednetty.server.mechanics.mobs.core;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enhanced enum representing different mob types with improved properties and functionality
 */
public enum MobType {

    // ================ REGULAR MOBS ================

    SKELETON("skeleton", EntityType.SKELETON, false, 1, 6, "Skeleton",
            MobCategory.UNDEAD, MobDifficulty.NORMAL, EnumSet.of(MobAbility.RANGED_ATTACK)),

    ZOMBIE("zombie", EntityType.ZOMBIE, false, 1, 6, "Zombie",
            MobCategory.UNDEAD, MobDifficulty.NORMAL, EnumSet.of(MobAbility.MELEE_ATTACK)),

    SILVERFISH("silverfish", EntityType.SILVERFISH, false, 1, 6, "Silverfish",
            MobCategory.ARTHROPOD, MobDifficulty.EASY, EnumSet.of(MobAbility.FAST_MOVEMENT)),

    MAGMA_CUBE("magmacube", EntityType.MAGMA_CUBE, false, 1, 6, "Magma Cube",
            MobCategory.SLIME, MobDifficulty.NORMAL, EnumSet.of(MobAbility.FIRE_RESISTANCE, MobAbility.BOUNCING)),

    SPIDER("spider", EntityType.SPIDER, false, 1, 6, "Spider",
            MobCategory.ARTHROPOD, MobDifficulty.NORMAL, EnumSet.of(MobAbility.CLIMBING, MobAbility.NIGHT_VISION)),

    CAVE_SPIDER("cavespider", EntityType.CAVE_SPIDER, false, 1, 6, "Cave Spider",
            MobCategory.ARTHROPOD, MobDifficulty.HARD, EnumSet.of(MobAbility.CLIMBING, MobAbility.POISON_ATTACK)),

    IMP("imp", EntityType.ZOMBIFIED_PIGLIN, false, 1, 6, "Imp",
            MobCategory.NETHER, MobDifficulty.NORMAL, EnumSet.of(MobAbility.FIRE_RESISTANCE, MobAbility.MELEE_ATTACK)),

    WITHER_SKELETON("witherskeleton", EntityType.WITHER_SKELETON, false, 1, 6, "Wither Skeleton",
            MobCategory.UNDEAD, MobDifficulty.HARD, EnumSet.of(MobAbility.FIRE_RESISTANCE, MobAbility.WITHER_EFFECT)),

    DAEMON("daemon", EntityType.ZOMBIFIED_PIGLIN, false, 1, 6, "Daemon",
            MobCategory.NETHER, MobDifficulty.HARD, EnumSet.of(MobAbility.FIRE_RESISTANCE, MobAbility.MAGIC_RESISTANCE)),

    GOLEM("golem", EntityType.IRON_GOLEM, false, 1, 6, "Golem",
            MobCategory.CONSTRUCT, MobDifficulty.HARD, EnumSet.of(MobAbility.HEAVY_ARMOR, MobAbility.KNOCKBACK_RESISTANCE)),

    GIANT("giant", EntityType.GIANT, false, 1, 6, "Giant",
            MobCategory.UNDEAD, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.MASSIVE_HEALTH, MobAbility.AREA_DAMAGE)),

    TURKEY("turkey", EntityType.CHICKEN, false, 1, 6, "Turkey",
            MobCategory.ANIMAL, MobDifficulty.EASY, EnumSet.of(MobAbility.FAST_MOVEMENT)),

    // ================ TIER 1 ELITES ================

    PLAGUEBEARER("plaguebearer", EntityType.ZOMBIE, true, 1, 1, "Plaguebearer",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.POISON_AURA, MobAbility.DISEASE_SPREAD)),

    MITSUKI("mitsuki", EntityType.ZOMBIE, true, 1, 1, "Mitsuki The Dominator",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.LEADERSHIP, MobAbility.COMBAT_MASTERY)),

    // ================ TIER 2 ELITES ================

    BONEREAVER("bonereaver", EntityType.WITHER_SKELETON, true, 2, 2, "Bonereaver",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.BONE_SHARDS, MobAbility.NECROMANCY)),

    COPJAK("copjak", EntityType.ZOMBIE, true, 2, 2, "Cop'jak",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.AUTHORITY, MobAbility.CROWD_CONTROL)),

    RISK_ELITE("risk_elite", EntityType.ZOMBIE, true, 2, 2, "Riskan The Rotten",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.DECAY_AURA, MobAbility.CORRUPTION)),

    ORC_KING("orcking", EntityType.ZOMBIE, true, 2, 2, "The Orc King",
            MobCategory.HUMANOID, MobDifficulty.ELITE, EnumSet.of(MobAbility.ROYAL_COMMAND, MobAbility.TRIBAL_FURY)),

    // ================ TIER 3 ELITES ================

    SOULREAPER("soulreaper", EntityType.WITHER_SKELETON, true, 3, 3, "Soulreaper",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.SOUL_DRAIN, MobAbility.DEATH_MAGIC)),

    KING_OF_GREED("kingofgreed", EntityType.WITHER_SKELETON, true, 3, 3, "The King Of Greed",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.WEALTH_DRAIN, MobAbility.GOLDEN_TOUCH)),

    SKELETON_KING("skeletonking", EntityType.WITHER_SKELETON, true, 3, 3, "The Skeleton King",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.UNDEAD_COMMAND, MobAbility.BONE_ARMY)),

    SPIDER_QUEEN("spiderqueen", EntityType.SPIDER, true, 3, 3, "The Spider Queen",
            MobCategory.ARTHROPOD, MobDifficulty.ELITE, EnumSet.of(MobAbility.WEB_MASTERY, MobAbility.SPIDER_SPAWN)),

    IMPA("impa", EntityType.WITHER_SKELETON, true, 3, 3, "Impa The Impaler",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.IMPALING_STRIKES, MobAbility.BLOODLUST)),

    // ================ TIER 4 ELITES ================

    DOOMHERALD("doomherald", EntityType.ZOMBIE, true, 4, 4, "Doomherald",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.DOOM_PROPHECY, MobAbility.APOCALYPSE_AURA)),

    BLOOD_BUTCHER("bloodbutcher", EntityType.ZOMBIE, true, 4, 4, "The Blood Butcher",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.BLOOD_FRENZY, MobAbility.SAVAGE_STRIKES)),

    BLAYSHAN("blayshan", EntityType.ZOMBIE, true, 4, 4, "Blayshan The Naga",
            MobCategory.SERPENT, MobDifficulty.ELITE, EnumSet.of(MobAbility.SERPENT_COILS, MobAbility.VENOM_SPIT)),

    WATCHMASTER("watchmaster", EntityType.WITHER_SKELETON, true, 4, 4, "The Watchmaster",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.ETERNAL_VIGIL, MobAbility.TIME_SIGHT)),

    SPECTRAL_KNIGHT("spectralKnight", EntityType.ZOMBIFIED_PIGLIN, true, 4, 4, "The Evil Spectral Overlord",
            MobCategory.SPECTRAL, MobDifficulty.ELITE, EnumSet.of(MobAbility.PHASE_SHIFT, MobAbility.SPECTRAL_ARMY)),

    DURANOR("duranor", EntityType.WITHER_SKELETON, true, 4, 4, "Duranor The Cruel",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.CRUELTY_AURA, MobAbility.TORTURE_MASTERY)),

    // ================ TIER 5 ELITES ================

    NETHERMANCER("nethermancer", EntityType.WITHER_SKELETON, true, 5, 5, "Nethermancer",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.NETHER_MAGIC, MobAbility.FIRE_MASTERY)),

    JAYDEN("jayden", EntityType.WITHER_SKELETON, true, 5, 5, "King Jayden",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.ROYAL_DECREE, MobAbility.KING_AURA)),

    KILATAN("kilatan", EntityType.WITHER_SKELETON, true, 5, 5, "Daemon Lord Kilatan",
            MobCategory.DEMON, MobDifficulty.ELITE, EnumSet.of(MobAbility.DEMONIC_POWER, MobAbility.HELL_FLAMES)),

    FROST_KING("frostking", EntityType.WITHER_SKELETON, true, 5, 5, "Frost Walker",
            MobCategory.ELEMENTAL, MobDifficulty.ELITE, EnumSet.of(MobAbility.ICE_MASTERY, MobAbility.FROST_AURA)),

    WARDEN("warden", EntityType.WITHER_SKELETON, true, 5, 5, "The Warden",
            MobCategory.GUARDIAN, MobDifficulty.ELITE, EnumSet.of(MobAbility.SONIC_BOOM, MobAbility.DARKNESS_SHROUD)),

    WEAK_SKELETON("weakskeletonentity", EntityType.SKELETON, true, 5, 5, "Skeletal Keeper",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.SOUL_KEEPING, MobAbility.SPIRIT_GUARD)),

    BOSS_SKELETON("bossSkeletonDungeon", EntityType.WITHER_SKELETON, true, 5, 5, "The Restless Skeleton Deathlord",
            MobCategory.UNDEAD, MobDifficulty.BOSS, EnumSet.of(MobAbility.DEATH_COMMAND, MobAbility.UNDEAD_LEGION)),

    KRAMPUS("krampus", EntityType.WITHER_SKELETON, true, 5, 5, "Krampus The Warrior",
            MobCategory.MYTHICAL, MobDifficulty.ELITE, EnumSet.of(MobAbility.HOLIDAY_TERROR, MobAbility.PUNISHMENT_CHAINS)),

    GRAND_WIZARD("grandwizard", EntityType.ZOMBIE, true, 5, 5, "Grand Wizard of Psilocyland",
            MobCategory.CASTER, MobDifficulty.ELITE, EnumSet.of(MobAbility.ARCANE_MASTERY, MobAbility.REALITY_WARP)),

    // ================ TIER 6 ELITES ================

    VOIDLORD("voidlord", EntityType.WITHER_SKELETON, true, 6, 6, "Voidlord",
            MobCategory.VOID, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.VOID_MASTERY, MobAbility.REALITY_TEAR)),

    // ================ WORLD BOSSES ================

    FROZEN_ELITE("frozenelite", EntityType.WITHER_SKELETON, true, 5, 6, "Frost The Exiled King",
            MobCategory.ELEMENTAL, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.ICE_MASTERY, MobAbility.FROZEN_REALM), true),

    FROZEN_BOSS("frozenboss", EntityType.WITHER_SKELETON, true, 5, 6, "The Conqueror of The North",
            MobCategory.ELEMENTAL, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.ARCTIC_COMMAND, MobAbility.BLIZZARD_FURY), true),

    FROZEN_GOLEM("frozengolem", EntityType.IRON_GOLEM, true, 5, 6, "Crypt Guardian",
            MobCategory.CONSTRUCT, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.ICE_ARMOR, MobAbility.CRYPT_POWER), true),

    FROSTWING("frostwing", EntityType.WITHER_SKELETON, true, 5, 6, "Frost-Wing The Frozen Titan",
            MobCategory.DRAGON, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.FLIGHT, MobAbility.ICE_BREATH), true),

    CHRONOS("chronos", EntityType.WITHER_SKELETON, true, 5, 6, "Chronos, Lord of Time",
            MobCategory.TEMPORAL, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.TIME_MASTERY, MobAbility.TEMPORAL_SHIFT), true),

    // ================ SPECIAL TYPES ================

    PRISONER("prisoner", EntityType.ZOMBIE, false, 3, 5, "Prisoner",
            MobCategory.HUMANOID, MobDifficulty.NORMAL, EnumSet.of(MobAbility.DESPERATION)),

    SKELLY_GUARDIAN("skellyDSkeletonGuardian", EntityType.SKELETON, true, 4, 5, "Skeletal Guardian",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.GUARDIAN_DUTY, MobAbility.PROTECTIVE_AURA)),

    SPECTRAL_GUARD("spectralguard", EntityType.ZOMBIFIED_PIGLIN, true, 1, 1, "The Evil Spectral's Impish Guard",
            MobCategory.SPECTRAL, MobDifficulty.ELITE, EnumSet.of(MobAbility.SPECTRAL_FORM, MobAbility.GUARD_DUTY));

    // ================ ENUMS FOR CATEGORIZATION ================

    public enum MobCategory {
        UNDEAD, HUMANOID, ARTHROPOD, SLIME, NETHER, CONSTRUCT, ANIMAL,
        SERPENT, SPECTRAL, DEMON, ELEMENTAL, GUARDIAN, MYTHICAL, CASTER,
        VOID, DRAGON, TEMPORAL
    }

    public enum MobDifficulty {
        EASY(1.0, 0.8),
        NORMAL(1.0, 1.0),
        HARD(1.2, 1.3),
        ELITE(1.5, 2.0),
        BOSS(2.0, 3.0),
        LEGENDARY(2.5, 4.0),
        WORLD_BOSS(3.0, 5.0);

        private final double healthMultiplier;
        private final double damageMultiplier;

        MobDifficulty(double healthMultiplier, double damageMultiplier) {
            this.healthMultiplier = healthMultiplier;
            this.damageMultiplier = damageMultiplier;
        }

        public double getHealthMultiplier() { return healthMultiplier; }
        public double getDamageMultiplier() { return damageMultiplier; }
    }

    public enum MobAbility {
        // Combat Abilities
        MELEE_ATTACK, RANGED_ATTACK, AREA_DAMAGE, POISON_ATTACK, WITHER_EFFECT,
        CRITICAL_STRIKES, IMPALING_STRIKES, SAVAGE_STRIKES, BLOODLUST,

        // Movement Abilities
        FAST_MOVEMENT, CLIMBING, BOUNCING, FLIGHT, PHASE_SHIFT,

        // Defensive Abilities
        FIRE_RESISTANCE, MAGIC_RESISTANCE, HEAVY_ARMOR, KNOCKBACK_RESISTANCE,
        ICE_ARMOR, SPECTRAL_FORM,

        // Special Powers
        SOUL_DRAIN, DEATH_MAGIC, NECROMANCY, BONE_SHARDS, BONE_ARMY,
        ICE_MASTERY, FROST_AURA, FROZEN_REALM, ARCTIC_COMMAND, BLIZZARD_FURY,
        NETHER_MAGIC, FIRE_MASTERY, HELL_FLAMES, DEMONIC_POWER,
        ARCANE_MASTERY, REALITY_WARP, VOID_MASTERY, REALITY_TEAR,
        TIME_MASTERY, TEMPORAL_SHIFT, ICE_BREATH,

        // Aura Effects
        POISON_AURA, DECAY_AURA, CORRUPTION, DOOM_PROPHECY, APOCALYPSE_AURA,
        CRUELTY_AURA, KING_AURA, PROTECTIVE_AURA, DARKNESS_SHROUD,

        // Leadership & Control
        LEADERSHIP, AUTHORITY, CROWD_CONTROL, ROYAL_COMMAND, ROYAL_DECREE,
        TRIBAL_FURY, UNDEAD_COMMAND, DEATH_COMMAND, ETERNAL_VIGIL,

        // Summoning & Spawning
        SPIDER_SPAWN, SPECTRAL_ARMY, UNDEAD_LEGION,

        // Unique Abilities
        NIGHT_VISION, DISEASE_SPREAD, COMBAT_MASTERY, WEALTH_DRAIN, GOLDEN_TOUCH,
        WEB_MASTERY, SERPENT_COILS, VENOM_SPIT, TIME_SIGHT, TORTURE_MASTERY,
        SONIC_BOOM, SOUL_KEEPING, SPIRIT_GUARD, HOLIDAY_TERROR, PUNISHMENT_CHAINS,
        MASSIVE_HEALTH, DESPERATION, GUARDIAN_DUTY, GUARD_DUTY, CRYPT_POWER,
        BLOOD_FRENZY
    }

    // ================ INSTANCE FIELDS ================

    private final String id;
    private final EntityType entityType;
    private final boolean elite;
    private final int minTier;
    private final int maxTier;
    private final String defaultName;
    private final boolean worldBoss;
    private final MobCategory category;
    private final MobDifficulty difficulty;
    private final Set<MobAbility> abilities;

    // Static lookup cache for performance
    private static final Map<String, MobType> BY_ID = new ConcurrentHashMap<>();
    private static final Map<MobCategory, List<MobType>> BY_CATEGORY = new ConcurrentHashMap<>();
    private static final Map<MobDifficulty, List<MobType>> BY_DIFFICULTY = new ConcurrentHashMap<>();

    static {
        // Build lookup caches
        for (MobType type : values()) {
            BY_ID.put(type.getId().toLowerCase(), type);

            BY_CATEGORY.computeIfAbsent(type.getCategory(), k -> new ArrayList<>()).add(type);
            BY_DIFFICULTY.computeIfAbsent(type.getDifficulty(), k -> new ArrayList<>()).add(type);
        }
    }

    // ================ CONSTRUCTORS ================

    MobType(String id, EntityType entityType, boolean elite, int minTier, int maxTier, String defaultName,
            MobCategory category, MobDifficulty difficulty, Set<MobAbility> abilities) {
        this(id, entityType, elite, minTier, maxTier, defaultName, category, difficulty, abilities, false);
    }

    MobType(String id, EntityType entityType, boolean elite, int minTier, int maxTier, String defaultName,
            MobCategory category, MobDifficulty difficulty, Set<MobAbility> abilities, boolean worldBoss) {
        this.id = id;
        this.entityType = entityType;
        this.elite = elite;
        this.minTier = minTier;
        this.maxTier = maxTier;
        this.defaultName = defaultName;
        this.worldBoss = worldBoss;
        this.category = category;
        this.difficulty = difficulty;
        this.abilities = EnumSet.copyOf(abilities);
    }

    // ================ STATIC LOOKUP METHODS ================

    /**
     * Get MobType by ID string with enhanced lookup
     *
     * @param id The type ID (case-insensitive)
     * @return MobType or null if not found
     */
    public static MobType getById(String id) {
        if (id == null || id.isEmpty()) return null;

        String cleanId = id.toLowerCase().trim();

        // Handle common variations
        if (cleanId.equals("wither_skeleton")) {
            cleanId = "witherskeleton";
        } else if (cleanId.equals("cave_spider")) {
            cleanId = "cavespider";
        } else if (cleanId.equals("magma_cube")) {
            cleanId = "magmacube";
        }

        return BY_ID.get(cleanId);
    }

    /**
     * Check if a type ID is valid
     *
     * @param id The type ID to check
     * @return true if valid
     */
    public static boolean isValidType(String id) {
        return getById(id) != null;
    }

    /**
     * Get all mob types in a specific category
     *
     * @param category The category to search
     * @return List of mob types in that category
     */
    public static List<MobType> getByCategory(MobCategory category) {
        return BY_CATEGORY.getOrDefault(category, Collections.emptyList());
    }

    /**
     * Get all mob types of a specific difficulty
     *
     * @param difficulty The difficulty to search
     * @return List of mob types with that difficulty
     */
    public static List<MobType> getByDifficulty(MobDifficulty difficulty) {
        return BY_DIFFICULTY.getOrDefault(difficulty, Collections.emptyList());
    }

    /**
     * Get all world boss types
     *
     * @return List of world boss mob types
     */
    public static List<MobType> getWorldBosses() {
        return Arrays.stream(values())
                .filter(MobType::isWorldBoss)
                .collect(Collectors.toList());
    }

    /**
     * Get all elite mob types
     *
     * @return List of elite mob types
     */
    public static List<MobType> getElites() {
        return Arrays.stream(values())
                .filter(MobType::isElite)
                .collect(Collectors.toList());
    }

    /**
     * Get all mob types available for a specific tier
     *
     * @param tier The tier to check
     * @return List of mob types available at that tier
     */
    public static List<MobType> getByTier(int tier) {
        return Arrays.stream(values())
                .filter(type -> tier >= type.getMinTier() && tier <= type.getMaxTier())
                .collect(Collectors.toList());
    }

    /**
     * Search for mob types by name (fuzzy matching)
     *
     * @param name The name to search for
     * @return List of matching mob types
     */
    public static List<MobType> searchByName(String name) {
        if (name == null || name.isEmpty()) return Collections.emptyList();

        String searchTerm = name.toLowerCase();

        return Arrays.stream(values())
                .filter(type -> type.getId().toLowerCase().contains(searchTerm) ||
                        type.getDefaultName().toLowerCase().contains(searchTerm))
                .collect(Collectors.toList());
    }

    // ================ TIER-SPECIFIC NAMING ================

    /**
     * Get tier-specific name with enhanced variations
     *
     * @param tier Mob tier (1-6)
     * @return Name customized for tier
     */
    public String getTierSpecificName(int tier) {
        // For named elites, always use default name
        if (elite && !isGenericType()) {
            return defaultName;
        }

        // Generate tier-specific names for generic types
        return generateTierSpecificName(tier);
    }

    private boolean isGenericType() {
        return id.equals("skeleton") || id.equals("zombie") || id.equals("witherskeleton") ||
                id.equals("golem") || id.equals("imp") || id.equals("daemon") ||
                id.equals("magmacube") || id.equals("silverfish") || id.equals("spider") ||
                id.equals("cavespider");
    }

    private String generateTierSpecificName(int tier) {
        switch (id) {
            case "skeleton":
                return getSkeletonTierName(tier);
            case "witherskeleton":
                return getWitherSkeletonTierName(tier);
            case "zombie":
                return getZombieTierName(tier);
            case "magmacube":
                return getMagmaCubeTierName(tier);
            case "silverfish":
                return getSilverfishTierName(tier);
            case "spider":
            case "cavespider":
                return getSpiderTierName(tier);
            case "golem":
                return getGolemTierName(tier);
            case "imp":
                return getImpTierName(tier);
            case "daemon":
                return getDaemonTierName(tier);
            case "weakskeletonentity":
                return getRandomSkeletalKeeperName();
            case "prisoner":
                return getRandomPrisonerName();
            case "skellyDSkeletonGuardian":
                return getRandomGuardianName();
            default:
                return defaultName;
        }
    }

    private String getSkeletonTierName(int tier) {
        switch (tier) {
            case 1: return "Broken Skeleton";
            case 2: return "Wandering Cracking Skeleton";
            case 3: return "Demonic Skeleton";
            case 4: return "Skeleton Guardian";
            case 5: return "Infernal Skeleton";
            case 6: return "Frozen Skeleton";
            default: return "Skeleton";
        }
    }

    private String getWitherSkeletonTierName(int tier) {
        switch (tier) {
            case 1: return "Broken Chaos Skeleton";
            case 2: return "Wandering Cracking Chaos Skeleton";
            case 3: return "Demonic Chaos Skeleton";
            case 4: return "Skeleton Chaos Guardian";
            case 5: return "Infernal Chaos Skeleton";
            case 6: return "Frozen Skeletal Minion";
            default: return "Chaos Skeleton";
        }
    }

    private String getZombieTierName(int tier) {
        switch (tier) {
            case 1: return "Rotting Zombie";
            case 2: return "Savaged Zombie";
            case 3: return "Greater Zombie";
            case 4: return "Demonic Zombie";
            case 5: return "Infernal Zombie";
            case 6: return "Frozen Zombie";
            default: return "Zombie";
        }
    }

    private String getMagmaCubeTierName(int tier) {
        switch (tier) {
            case 1: return "Weak Magma Cube";
            case 2: return "Bubbling Magma Cube";
            case 3: return "Unstable Magma Cube";
            case 4: return "Boiling Magma Cube";
            case 5: return "Unstoppable Magma Cube";
            case 6: return "Ice Cube";
            default: return "Magma Cube";
        }
    }

    private String getSilverfishTierName(int tier) {
        switch (tier) {
            case 1: return "Weak SilverFish";
            case 2: return "Pointy SilverFish";
            case 3: return "Unstable SilverFish";
            case 4: return "Mean SilverFish";
            case 5: return "Rude SilverFish";
            case 6: return "Ice-Cold SilverFish";
            default: return "SilverFish";
        }
    }

    private String getSpiderTierName(int tier) {
        String prefix;
        switch (tier) {
            case 1: prefix = "Harmless"; break;
            case 2: prefix = "Wild"; break;
            case 3: prefix = "Fierce"; break;
            case 4: prefix = "Dangerous"; break;
            case 5: prefix = "Lethal"; break;
            case 6: prefix = "Devastating"; break;
            default: prefix = ""; break;
        }

        String baseType = id.equals("cavespider") ? "Cave Spider" : "Spider";
        return prefix.isEmpty() ? baseType : prefix + " " + baseType;
    }

    private String getGolemTierName(int tier) {
        switch (tier) {
            case 1: return "Broken Golem";
            case 2: return "Rusty Golem";
            case 3: return "Restored Golem";
            case 4: return "Mountain Golem";
            case 5: return "Powerful Golem";
            case 6: return "Devastating Golem";
            default: return "Golem";
        }
    }

    private String getImpTierName(int tier) {
        switch (tier) {
            case 1: return "Ugly Imp";
            case 2: return "Angry Imp";
            case 3: return "Warrior Imp";
            case 4: return "Armoured Imp";
            case 5: return "Infernal Imp";
            case 6: return "Arctic Imp";
            default: return "Imp";
        }
    }

    private String getDaemonTierName(int tier) {
        switch (tier) {
            case 1: return "Broken Daemon";
            case 2: return "Wandering Cracking Daemon";
            case 3: return "Demonic Daemon";
            case 4: return "Daemon Guardian";
            case 5: return "Infernal Daemon";
            case 6: return "Chilled Daemon";
            default: return "Daemon";
        }
    }

    private String getRandomSkeletalKeeperName() {
        String[] names = {
                "Infernal Skeletal Keeper",
                "Skeletal Soul Keeper",
                "Skeletal Soul Harvester",
                "Infernal Skeletal Soul Harvester"
        };
        return names[new Random().nextInt(names.length)];
    }

    private String getRandomPrisonerName() {
        String[] names = {
                "Tortured Prisoner",
                "Corrupted Prison Guard",
                "Tormented Guard"
        };
        return names[new Random().nextInt(names.length)];
    }

    private String getRandomGuardianName() {
        String[] names = {
                "Skeletal Guardian Deadlord",
                "Skeletal Guardian Overlord",
                "Restless Skeletal Guardian"
        };
        return names[new Random().nextInt(names.length)];
    }

    // ================ FORMATTED NAME METHODS ================

    /**
     * Get the formatted name with appropriate color based on tier and elite status
     *
     * @param tier Mob tier
     * @return Colored name string
     */
    public String getFormattedName(int tier) {
        String name = getTierSpecificName(tier);
        ChatColor color = getTierColor(tier);

        if (elite) {
            return color.toString() + ChatColor.BOLD + name;
        } else {
            return color + name;
        }
    }

    private ChatColor getTierColor(int tier) {
        switch (tier) {
            case 1: return ChatColor.WHITE;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.LIGHT_PURPLE;
            case 5: return ChatColor.YELLOW;
            case 6: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }

    // ================ VALIDATION METHODS ================

    /**
     * Check if a tier is valid for this mob type
     *
     * @param tier The tier to check
     * @return true if tier is valid
     */
    public boolean isValidTier(int tier) {
        return tier >= minTier && tier <= maxTier;
    }

    /**
     * Clamp a tier to the valid range for this mob type
     *
     * @param tier The tier to clamp
     * @return Clamped tier value
     */
    public int clampTier(int tier) {
        return Math.max(minTier, Math.min(maxTier, tier));
    }

    // ================ ABILITY CHECKING ================

    /**
     * Check if this mob type has a specific ability
     *
     * @param ability The ability to check
     * @return true if mob has this ability
     */
    public boolean hasAbility(MobAbility ability) {
        return abilities.contains(ability);
    }

    /**
     * Check if this mob type has any of the specified abilities
     *
     * @param abilities The abilities to check
     * @return true if mob has any of these abilities
     */
    public boolean hasAnyAbility(MobAbility... abilities) {
        return Arrays.stream(abilities).anyMatch(this::hasAbility);
    }

    /**
     * Check if this mob type has all of the specified abilities
     *
     * @param abilities The abilities to check
     * @return true if mob has all of these abilities
     */
    public boolean hasAllAbilities(MobAbility... abilities) {
        return Arrays.stream(abilities).allMatch(this::hasAbility);
    }

    /**
     * Get abilities by category
     *
     * @return Set of abilities this mob type has
     */
    public Set<MobAbility> getAbilities() {
        return EnumSet.copyOf(abilities);
    }

    // ================ UTILITY METHODS ================

    /**
     * Get display information about this mob type
     *
     * @return Formatted info string
     */
    public String getDisplayInfo() {
        StringBuilder info = new StringBuilder();
        info.append("§6").append(defaultName).append(" §7(").append(id).append(")");
        info.append("\n§7Category: §f").append(category);
        info.append("\n§7Difficulty: §f").append(difficulty);
        info.append("\n§7Tiers: §f").append(minTier).append("-").append(maxTier);

        if (elite) info.append("\n§7Type: §6Elite");
        if (worldBoss) info.append("\n§7Type: §cWorld Boss");

        if (!abilities.isEmpty()) {
            info.append("\n§7Abilities: §f").append(abilities.size());
        }

        return info.toString();
    }

    /**
     * Compare two mob types by difficulty and tier
     *
     * @param other The other mob type
     * @return Comparison result
     */
    public int compareType(MobType other) {
        int difficultyCompare = this.difficulty.compareTo(other.difficulty);
        if (difficultyCompare != 0) return difficultyCompare;

        int tierCompare = Integer.compare(this.maxTier, other.maxTier);
        if (tierCompare != 0) return tierCompare;

        return this.id.compareTo(other.id);
    }

    // ================ GETTERS ================

    public String getId() { return id; }
    public EntityType getEntityType() { return entityType; }
    public boolean isElite() { return elite; }
    public int getMinTier() { return minTier; }
    public int getMaxTier() { return maxTier; }
    public String getDefaultName() { return defaultName; }
    public boolean isWorldBoss() { return worldBoss; }
    public MobCategory getCategory() { return category; }
    public MobDifficulty getDifficulty() { return difficulty; }

    /**
     * Get the effective health multiplier for this mob type
     *
     * @return Health multiplier from difficulty
     */
    public double getHealthMultiplier() {
        return difficulty.getHealthMultiplier();
    }

    /**
     * Get the effective damage multiplier for this mob type
     *
     * @return Damage multiplier from difficulty
     */
    public double getDamageMultiplier() {
        return difficulty.getDamageMultiplier();
    }
}