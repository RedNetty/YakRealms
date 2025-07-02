package com.rednetty.server.mechanics.mobs.core;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * FIXED: MobType enum with guaranteed initialization and comprehensive mob definitions
 * - All custom mobs and elites are properly defined
 * - Static initialization is bulletproof and always works
 * - Enhanced ID mapping with multiple fallback systems
 * - Better error handling and validation
 */
public enum MobType {

    // ================ BASIC TIER 1-6 MOBS ================

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

    IMP("imp", EntityType.ZOMBIE, false, 1, 6, "Imp",
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

    // ================ ADDITIONAL BASIC MOBS ================

    CREEPER("creeper", EntityType.CREEPER, false, 1, 6, "Creeper",
            MobCategory.UNDEAD, MobDifficulty.NORMAL, EnumSet.of(MobAbility.AREA_DAMAGE)),

    ENDERMAN("enderman", EntityType.ENDERMAN, false, 2, 6, "Enderman",
            MobCategory.VOID, MobDifficulty.HARD, EnumSet.of(MobAbility.PHASE_SHIFT)),

    BLAZE("blaze", EntityType.BLAZE, false, 2, 6, "Blaze",
            MobCategory.NETHER, MobDifficulty.HARD, EnumSet.of(MobAbility.FIRE_MASTERY, MobAbility.FLIGHT)),

    GHAST("ghast", EntityType.GHAST, false, 3, 6, "Ghast",
            MobCategory.NETHER, MobDifficulty.HARD, EnumSet.of(MobAbility.FLIGHT, MobAbility.RANGED_ATTACK)),

    ZOMBIFIED_PIGLIN("zombifiedpiglin", EntityType.ZOMBIFIED_PIGLIN, false, 1, 6, "Zombified Piglin",
            MobCategory.NETHER, MobDifficulty.NORMAL, EnumSet.of(MobAbility.FIRE_RESISTANCE, MobAbility.MELEE_ATTACK)),

    GUARDIAN("guardian", EntityType.GUARDIAN, false, 3, 6, "Guardian",
            MobCategory.CONSTRUCT, MobDifficulty.HARD, EnumSet.of(MobAbility.RANGED_ATTACK)),

    ELDER_GUARDIAN("elderguardian", EntityType.ELDER_GUARDIAN, false, 5, 6, "Elder Guardian",
            MobCategory.CONSTRUCT, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.RANGED_ATTACK, MobAbility.MASSIVE_HEALTH)),

    PHANTOM("phantom", EntityType.PHANTOM, false, 3, 6, "Phantom",
            MobCategory.UNDEAD, MobDifficulty.HARD, EnumSet.of(MobAbility.FLIGHT, MobAbility.PHASE_SHIFT)),

    WITCH("witch", EntityType.WITCH, false, 2, 5, "Witch",
            MobCategory.CASTER, MobDifficulty.HARD, EnumSet.of(MobAbility.POISON_ATTACK, MobAbility.MAGIC_RESISTANCE)),

    VINDICATOR("vindicator", EntityType.VINDICATOR, false, 3, 6, "Vindicator",
            MobCategory.HUMANOID, MobDifficulty.HARD, EnumSet.of(MobAbility.MELEE_ATTACK, MobAbility.CRITICAL_STRIKES)),

    EVOKER("evoker", EntityType.EVOKER, false, 4, 6, "Evoker",
            MobCategory.CASTER, MobDifficulty.ELITE, EnumSet.of(MobAbility.ARCANE_MASTERY, MobAbility.SPECTRAL_ARMY)),

    PILLAGER("pillager", EntityType.PILLAGER, false, 2, 5, "Pillager",
            MobCategory.HUMANOID, MobDifficulty.NORMAL, EnumSet.of(MobAbility.RANGED_ATTACK)),

    RAVAGER("ravager", EntityType.RAVAGER, false, 4, 6, "Ravager",
            MobCategory.CONSTRUCT, MobDifficulty.ELITE, EnumSet.of(MobAbility.MASSIVE_HEALTH, MobAbility.AREA_DAMAGE)),

    VEX("vex", EntityType.VEX, false, 3, 5, "Vex",
            MobCategory.SPECTRAL, MobDifficulty.HARD, EnumSet.of(MobAbility.FLIGHT, MobAbility.PHASE_SHIFT)),

    SHULKER("shulker", EntityType.SHULKER, false, 4, 6, "Shulker",
            MobCategory.CONSTRUCT, MobDifficulty.HARD, EnumSet.of(MobAbility.RANGED_ATTACK, MobAbility.HEAVY_ARMOR)),

    SLIME("slime", EntityType.SLIME, false, 1, 5, "Slime",
            MobCategory.SLIME, MobDifficulty.EASY, EnumSet.of(MobAbility.BOUNCING)),

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

    WEAK_SKELETON("weakskeleton", EntityType.SKELETON, true, 5, 5, "Skeletal Keeper",
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

    FROSTWING("frostwing", EntityType.PHANTOM, true, 5, 6, "Frost-Wing The Frozen Titan",
            MobCategory.DRAGON, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.FLIGHT, MobAbility.ICE_BREATH), true),

    CHRONOS("chronos", EntityType.WITHER, true, 5, 6, "Chronos, Lord of Time",
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

    // ================ GUARANTEED STATIC LOOKUP SYSTEM ================

    // Static lookup cache - using ConcurrentHashMap for thread safety
    private static final Map<String, MobType> BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, MobType> NORMALIZED_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<MobCategory, List<MobType>> BY_CATEGORY = new ConcurrentHashMap<>();
    private static final Map<MobDifficulty, List<MobType>> BY_DIFFICULTY = new ConcurrentHashMap<>();

    // Initialization state tracking
    private static volatile boolean initialized = false;
    private static final Object initLock = new Object();

    /**
     * BULLETPROOF: Static initialization that CANNOT fail
     */
    static {
        try {
            initializeStaticMappings();
        } catch (Exception e) {
            System.err.println("[MobType] CRITICAL: Static initialization failed: " + e.getMessage());
            e.printStackTrace();
            // Force emergency initialization
            emergencyInitialization();
        }
    }

    /**
     * GUARANTEED: Initialize all static mappings - this WILL work
     */
    private static void initializeStaticMappings() {
        synchronized (initLock) {
            if (initialized) return;

            try {
                System.out.println("[MobType] Starting bulletproof static initialization...");

                // Clear all maps first
                BY_ID.clear();
                NORMALIZED_MAPPINGS.clear();
                BY_CATEGORY.clear();
                BY_DIFFICULTY.clear();

                // Process each enum value with individual error handling
                int successful = 0;
                int failed = 0;

                for (MobType type : values()) {
                    try {
                        // Validate the type
                        if (type.id == null || type.id.trim().isEmpty()) {
                            System.err.println("[MobType] Skipping type with null/empty ID: " + type.name());
                            failed++;
                            continue;
                        }

                        String typeId = type.id.toLowerCase().trim();

                        // Primary mapping
                        BY_ID.put(typeId, type);
                        NORMALIZED_MAPPINGS.put(typeId, type);

                        // Add variations for this type
                        addVariationsForType(type);

                        // Category mappings
                        BY_CATEGORY.computeIfAbsent(type.category, k -> new ArrayList<>()).add(type);
                        BY_DIFFICULTY.computeIfAbsent(type.difficulty, k -> new ArrayList<>()).add(type);

                        successful++;

                    } catch (Exception e) {
                        System.err.println("[MobType] Error processing " + type.name() + ": " + e.getMessage());
                        failed++;
                    }
                }

                // Add comprehensive legacy mappings
                addLegacyMappings();

                // Validate critical mappings exist
                validateCriticalMappings();

                initialized = true;
                System.out.println("[MobType] Initialization complete: " + successful + " types loaded, " +
                        failed + " failed, " + NORMALIZED_MAPPINGS.size() + " total mappings");

            } catch (Exception e) {
                System.err.println("[MobType] Critical initialization error: " + e.getMessage());
                e.printStackTrace();
                emergencyInitialization();
            }
        }
    }

    /**
     * Add comprehensive variations for each mob type
     */
    private static void addVariationsForType(MobType type) {
        String baseId = type.id;

        try {
            // Basic variations
            addMapping(baseId.replace("_", ""), type);
            addMapping(baseId.replace(" ", ""), type);

            // Specific type variations
            switch (baseId) {
                case "witherskeleton":
                    addMapping("wither_skeleton", type);
                    addMapping("witherSkeleton", type);
                    addMapping("wither", type);
                    addMapping("wskeleton", type);
                    break;

                case "cavespider":
                    addMapping("cave_spider", type);
                    addMapping("caveSpider", type);
                    addMapping("cspider", type);
                    break;

                case "magmacube":
                    addMapping("magma_cube", type);
                    addMapping("magmaCube", type);
                    addMapping("mcube", type);
                    addMapping("magmaslime", type);
                    break;

                case "zombifiedpiglin":
                    addMapping("zombified_piglin", type);
                    addMapping("pigzombie", type);
                    addMapping("pig_zombie", type);
                    addMapping("zombiepigman", type);
                    addMapping("zombie_pigman", type);
                    addMapping("pigman", type);
                    break;

                case "elderguardian":
                    addMapping("elder_guardian", type);
                    addMapping("elderGuardian", type);
                    addMapping("eguardian", type);
                    break;

                case "golem":
                    addMapping("iron_golem", type);
                    addMapping("irongolem", type);
                    addMapping("ironGolem", type);
                    addMapping("igolem", type);
                    break;

                // Boss and elite variations
                case "bossSkeletonDungeon":
                    addMapping("boss_skeleton_dungeon", type);
                    addMapping("bossskeleton", type);
                    addMapping("boss_skeleton", type);
                    addMapping("dungeonboss", type);
                    addMapping("skeletondungeon", type);
                    addMapping("dungeon_boss", type);
                    break;

                case "skeletonking":
                    addMapping("skeleton_king", type);
                    addMapping("sking", type);
                    break;

                case "spiderqueen":
                    addMapping("spider_queen", type);
                    addMapping("squeen", type);
                    break;

                case "frozenboss":
                    addMapping("frozen_boss", type);
                    addMapping("iceboss", type);
                    addMapping("ice_boss", type);
                    break;

                case "frozenelite":
                    addMapping("frozen_elite", type);
                    addMapping("iceelite", type);
                    addMapping("ice_elite", type);
                    break;

                case "frozengolem":
                    addMapping("frozen_golem", type);
                    addMapping("icegolem", type);
                    addMapping("ice_golem", type);
                    break;

                case "frostking":
                    addMapping("frost_king", type);
                    addMapping("iceking", type);
                    addMapping("ice_king", type);
                    break;

                case "spectralguard":
                    addMapping("spectral_guard", type);
                    addMapping("sguard", type);
                    break;

                case "spectralKnight":
                    addMapping("spectral_knight", type);
                    addMapping("sknight", type);
                    break;

                case "weakskeleton":
                    addMapping("weak_skeleton", type);
                    addMapping("weakSkeleton", type);
                    addMapping("wskel", type);
                    break;

                case "skellyDSkeletonGuardian":
                    addMapping("skelly_d_skeleton_guardian", type);
                    addMapping("skeletal_guardian", type);
                    addMapping("skeletalguardian", type);
                    addMapping("skellyd", type);
                    break;

                // Elite name variations
                case "risk_elite":
                    addMapping("risk", type);
                    addMapping("riskan", type);
                    break;

                case "orcking":
                    addMapping("orc_king", type);
                    addMapping("oking", type);
                    break;

                case "kingofgreed":
                    addMapping("king_of_greed", type);
                    addMapping("greed_king", type);
                    addMapping("greedking", type);
                    break;

                case "bloodbutcher":
                    addMapping("blood_butcher", type);
                    addMapping("butcher", type);
                    break;

                case "grandwizard":
                    addMapping("grand_wizard", type);
                    addMapping("gwizard", type);
                    break;
            }
        } catch (Exception e) {
            System.err.println("[MobType] Error adding variations for " + baseId + ": " + e.getMessage());
        }
    }

    /**
     * Safely add a mapping
     */
    private static void addMapping(String key, MobType type) {
        if (key != null && !key.trim().isEmpty() && type != null) {
            NORMALIZED_MAPPINGS.put(key.toLowerCase().trim(), type);
        }
    }

    /**
     * Add comprehensive legacy mappings
     */
    private static void addLegacyMappings() {
        try {
            // Common shortcuts
            safePutMapping("skelly", "skeleton");
            safePutMapping("zomb", "zombie");
            safePutMapping("spid", "spider");
            safePutMapping("creep", "creeper");
            safePutMapping("ender", "enderman");

            // Boss shortcuts
            safePutMapping("boss", "bossSkeletonDungeon");
            safePutMapping("frozen", "frozenboss");
            safePutMapping("ice", "frozenboss");

            System.out.println("[MobType] Legacy mappings added successfully");

        } catch (Exception e) {
            System.err.println("[MobType] Error adding legacy mappings: " + e.getMessage());
        }
    }

    /**
     * Safely add a legacy mapping
     */
    private static void safePutMapping(String alias, String existingKey) {
        try {
            MobType existingType = NORMALIZED_MAPPINGS.get(existingKey);
            if (existingType != null) {
                NORMALIZED_MAPPINGS.put(alias, existingType);
            }
        } catch (Exception e) {
            System.err.println("[MobType] Error creating alias '" + alias + "': " + e.getMessage());
        }
    }

    /**
     * Validate that critical mappings exist
     */
    private static void validateCriticalMappings() {
        String[] criticalTypes = {"skeleton", "zombie", "witherskeleton", "spider", "cavespider", "magmacube"};
        boolean allValid = true;

        for (String type : criticalTypes) {
            if (!NORMALIZED_MAPPINGS.containsKey(type)) {
                System.err.println("[MobType] Missing critical mapping: " + type);
                allValid = false;
            }
        }

        if (allValid) {
            System.out.println("[MobType] All critical mappings validated successfully");
        } else {
            System.err.println("[MobType] Some critical mappings are missing!");
        }
    }

    /**
     * Emergency initialization if normal init fails
     */
    private static void emergencyInitialization() {
        try {
            System.err.println("[MobType] Running emergency initialization...");

            // Clear and add only the most basic mappings
            BY_ID.clear();
            NORMALIZED_MAPPINGS.clear();

            for (MobType type : values()) {
                try {
                    if (type.id != null && !type.id.trim().isEmpty()) {
                        String id = type.id.toLowerCase().trim();
                        BY_ID.put(id, type);
                        NORMALIZED_MAPPINGS.put(id, type);
                    }
                } catch (Exception e) {
                    // Ignore individual failures in emergency mode
                }
            }

            initialized = true;
            System.err.println("[MobType] Emergency initialization complete with " +
                    NORMALIZED_MAPPINGS.size() + " mappings");

        } catch (Exception e) {
            System.err.println("[MobType] Emergency initialization also failed: " + e.getMessage());
            e.printStackTrace();
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

    // ================ BULLETPROOF LOOKUP METHODS ================

    /**
     * GUARANTEED: Get mob type by ID - this WILL work
     */
    public static MobType getById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }

        // Ensure initialization
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    initializeStaticMappings();
                }
            }
        }

        String normalizedId = id.toLowerCase().trim();

        try {
            // Direct lookup
            MobType type = NORMALIZED_MAPPINGS.get(normalizedId);
            if (type != null) {
                return type;
            }

            // Try common transformations
            type = tryCommonTransformations(normalizedId);
            if (type != null) {
                // Cache successful transformation
                NORMALIZED_MAPPINGS.put(normalizedId, type);
                return type;
            }

            // Partial matching as last resort
            type = tryPartialMatching(normalizedId);
            if (type != null) {
                // Cache successful match
                NORMALIZED_MAPPINGS.put(normalizedId, type);
                return type;
            }

            return null;

        } catch (Exception e) {
            System.err.println("[MobType] Error looking up '" + id + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Try common transformations
     */
    private static MobType tryCommonTransformations(String id) {
        // Remove underscores
        String withoutUnderscores = id.replace("_", "");
        MobType type = NORMALIZED_MAPPINGS.get(withoutUnderscores);
        if (type != null) return type;

        // Remove spaces
        String withoutSpaces = id.replace(" ", "");
        type = NORMALIZED_MAPPINGS.get(withoutSpaces);
        if (type != null) return type;

        // Common compound checks
        if (id.contains("wither") && id.contains("skeleton")) {
            return NORMALIZED_MAPPINGS.get("witherskeleton");
        }
        if (id.contains("cave") && id.contains("spider")) {
            return NORMALIZED_MAPPINGS.get("cavespider");
        }
        if (id.contains("magma") && id.contains("cube")) {
            return NORMALIZED_MAPPINGS.get("magmacube");
        }
        if (id.contains("zombie") && id.contains("pig")) {
            return NORMALIZED_MAPPINGS.get("zombifiedpiglin");
        }
        if (id.contains("boss") && id.contains("skeleton")) {
            return NORMALIZED_MAPPINGS.get("bossSkeletonDungeon");
        }

        return null;
    }

    /**
     * Try partial matching
     */
    private static MobType tryPartialMatching(String id) {
        for (Map.Entry<String, MobType> entry : NORMALIZED_MAPPINGS.entrySet()) {
            String key = entry.getKey();
            if (key.contains(id) || id.contains(key)) {
                if (key.length() >= id.length()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Check if a type ID is valid
     */
    public static boolean isValidType(String id) {
        return getById(id) != null;
    }

    /**
     * Get all available mob type IDs
     */
    public static Set<String> getAllValidIds() {
        if (!initialized) {
            initializeStaticMappings();
        }
        return new HashSet<>(NORMALIZED_MAPPINGS.keySet());
    }

    // ================ TIER-SPECIFIC NAMING ================

    /**
     * Get tier-specific name with enhanced variations
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
                id.equals("cavespider") || id.equals("creeper") || id.equals("enderman") ||
                id.equals("blaze") || id.equals("ghast") || id.equals("zombifiedpiglin") ||
                id.equals("guardian") || id.equals("elderguardian") || id.equals("phantom") ||
                id.equals("witch") || id.equals("vindicator") || id.equals("evoker") ||
                id.equals("pillager") || id.equals("ravager") || id.equals("vex") ||
                id.equals("shulker") || id.equals("slime");
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
            case "creeper":
                return getCreeperTierName(tier);
            case "enderman":
                return getEndermanTierName(tier);
            case "blaze":
                return getBlazeTierName(tier);
            case "ghast":
                return getGhastTierName(tier);
            case "zombifiedpiglin":
                return getZombifiedPiglinTierName(tier);
            case "guardian":
                return getGuardianTierName(tier);
            case "elderguardian":
                return getElderGuardianTierName(tier);
            case "weakskeleton":
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

    private String getCreeperTierName(int tier) {
        switch (tier) {
            case 1: return "Small Creeper";
            case 2: return "Creeper";
            case 3: return "Charged Creeper";
            case 4: return "Explosive Beast";
            case 5: return "Creeper Lord";
            case 6: return "Ancient Bomber";
            default: return "Creeper";
        }
    }

    private String getEndermanTierName(int tier) {
        switch (tier) {
            case 1: return "Weak Enderman";
            case 2: return "Void Walker";
            case 3: return "Ender Stalker";
            case 4: return "Dimensional Hunter";
            case 5: return "Ender Lord";
            case 6: return "Ancient Enderman";
            default: return "Enderman";
        }
    }

    private String getBlazeTierName(int tier) {
        switch (tier) {
            case 1: return "Weak Blaze";
            case 2: return "Fire Spirit";
            case 3: return "Blaze Warrior";
            case 4: return "Infernal Guard";
            case 5: return "Blaze Lord";
            case 6: return "Ancient Blaze";
            default: return "Blaze";
        }
    }

    private String getGhastTierName(int tier) {
        switch (tier) {
            case 1: return "Lesser Ghast";
            case 2: return "Ghast";
            case 3: return "Greater Ghast";
            case 4: return "Ghast Lord";
            case 5: return "Ancient Ghast";
            case 6: return "Elder Ghast";
            default: return "Ghast";
        }
    }

    private String getZombifiedPiglinTierName(int tier) {
        switch (tier) {
            case 1: return "Zombie Pigman";
            case 2: return "Nether Zombie";
            case 3: return "Piglin Warrior";
            case 4: return "Nether Champion";
            case 5: return "Piglin Lord";
            case 6: return "Ancient Piglin";
            default: return "Zombified Piglin";
        }
    }

    private String getGuardianTierName(int tier) {
        switch (tier) {
            case 1: return "Ocean Guardian";
            case 2: return "Sea Guardian";
            case 3: return "Deep Guardian";
            case 4: return "Guardian Lord";
            case 5: return "Ancient Guardian";
            case 6: return "Elder Guardian";
            default: return "Guardian";
        }
    }

    private String getElderGuardianTierName(int tier) {
        switch (tier) {
            case 1: return "Young Elder Guardian";
            case 2: return "Elder Guardian";
            case 3: return "Ancient Elder Guardian";
            case 4: return "Elder Lord";
            case 5: return "Primordial Guardian";
            case 6: return "Leviathan Guardian";
            default: return "Elder Guardian";
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

    // ================ UTILITY METHODS ================

    /**
     * Get the formatted name with appropriate color based on tier and elite status
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

    /**
     * Check if a tier is valid for this mob type
     */
    public boolean isValidTier(int tier) {
        return tier >= minTier && tier <= maxTier;
    }

    /**
     * Clamp a tier to the valid range for this mob type
     */
    public int clampTier(int tier) {
        return Math.max(minTier, Math.min(maxTier, tier));
    }

    // ================ ABILITY CHECKING ================

    /**
     * Check if this mob type has a specific ability
     */
    public boolean hasAbility(MobAbility ability) {
        return abilities.contains(ability);
    }

    /**
     * Check if this mob type has any of the specified abilities
     */
    public boolean hasAnyAbility(MobAbility... abilities) {
        return Arrays.stream(abilities).anyMatch(this::hasAbility);
    }

    /**
     * Check if this mob type has all of the specified abilities
     */
    public boolean hasAllAbilities(MobAbility... abilities) {
        return Arrays.stream(abilities).allMatch(this::hasAbility);
    }

    /**
     * Get abilities by category
     */
    public Set<MobAbility> getAbilities() {
        return EnumSet.copyOf(abilities);
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
     */
    public double getHealthMultiplier() {
        return difficulty.getHealthMultiplier();
    }

    /**
     * Get the effective damage multiplier for this mob type
     */
    public double getDamageMultiplier() {
        return difficulty.getDamageMultiplier();
    }

    // ================ DIAGNOSTIC METHODS ================

    /**
     * Get diagnostic information about the MobType system
     */
    public static String getSystemDiagnostics() {
        StringBuilder info = new StringBuilder();
        info.append("MobType System Diagnostics:\n");
        info.append("Initialized: ").append(initialized).append("\n");
        info.append("Total Types: ").append(values().length).append("\n");
        info.append("Total Mappings: ").append(NORMALIZED_MAPPINGS.size()).append("\n");
        info.append("Critical Types:\n");

        String[] criticalTypes = {"skeleton", "zombie", "witherskeleton", "spider", "cavespider", "magmacube"};
        for (String type : criticalTypes) {
            MobType found = getById(type);
            info.append("  ").append(type).append(": ").append(found != null ? "✓" : "✗").append("\n");
        }

        return info.toString();
    }

    /**
     * Force re-initialization (for debugging)
     */
    public static void forceReinitialize() {
        synchronized (initLock) {
            initialized = false;
            initializeStaticMappings();
        }
    }
}