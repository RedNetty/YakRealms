package com.rednetty.server.mechanics.world.mobs.core;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIXED: MobType enum with guaranteed initialization and comprehensive elite mob support
 * - Fixed static initialization to include ALL mob types including elites
 * - Enhanced validation for elite-only types
 * - Better error handling and debugging
 * - Comprehensive ID mapping with elite variations
 * - UPDATED: Enhanced epic names for all named elite mobs
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

    WARDEN("warden", EntityType.WARDEN, false, 5, 6, "Warden",
            MobCategory.CONSTRUCT, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.MASSIVE_HEALTH, MobAbility.SONIC_BOOM)),

    // ================ TIER 1 ELITES ================

    MALACHAR("malachar", EntityType.WITHER_SKELETON, true, 1, 1, "Malachar The Ember Lord",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.FIRE_MASTERY, MobAbility.LIFE_STEAL, MobAbility.EMBER_AURA)),

    XERATHEN("xerathen", EntityType.ZOMBIE, true, 1, 1, "Xerathen The Thorn Warden",
            MobCategory.NATURE, MobDifficulty.ELITE, EnumSet.of(MobAbility.POISON_MASTERY, MobAbility.THORN_STRIKE, MobAbility.NATURE_CORRUPTION)),

    VERIDIANA("veridiana", EntityType.HUSK, true, 1, 1, "Veridiana The Plague Bringer",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.PLAGUE_MASTERY, MobAbility.DISEASE_SPREAD, MobAbility.TOXIC_AURA)),

    // ================ TIER 2 ELITES ================

    THORGRIM("thorgrim", EntityType.ZOMBIFIED_PIGLIN, true, 2, 2, "Thorgrim The Mountain King",
            MobCategory.DWARF, MobDifficulty.ELITE, EnumSet.of(MobAbility.MOUNTAIN_STRENGTH, MobAbility.SIEGE_MASTERY, MobAbility.DWARVEN_FURY)),

    LYSANDER("lysander", EntityType.VINDICATOR, true, 2, 2, "Lysander The Divine Blade",
            MobCategory.HOLY, MobDifficulty.ELITE, EnumSet.of(MobAbility.DIVINE_POWER, MobAbility.ICE_MASTERY, MobAbility.CELESTIAL_EDGE)),

    MORGANA("morgana", EntityType.WITHER_SKELETON, true, 2, 2, "Morgana The Soul Ripper",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.SOUL_RIPPER, MobAbility.DEATH_MASTERY, MobAbility.MOURNING_AURA)),

    // ================ TIER 3 ELITES ================

    VEX_ELITE("vex_elite", EntityType.ZOMBIE, true, 3, 3, "Vex The Nightmare Weaver",
            MobCategory.SPECTRAL, MobDifficulty.ELITE, EnumSet.of(MobAbility.NIGHTMARE_MASTERY, MobAbility.REALITY_SHIFT, MobAbility.DREAM_WEAVER)),

    CORNELIUS("cornelius", EntityType.ZOMBIE_VILLAGER, true, 3, 3, "Cornelius The Golden Tyrant",
            MobCategory.HUMANOID, MobDifficulty.ELITE, EnumSet.of(MobAbility.AVARICE_MASTERY, MobAbility.GOLDEN_TOUCH, MobAbility.TREASURE_HOARD)),

    VALDRIS("valdris", EntityType.WITHER_SKELETON, true, 3, 3, "Valdris The Bone Emperor",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.BONE_MASTERY, MobAbility.ETERNAL_BLADE, MobAbility.UNDEAD_COMMAND)),

    SERAPHINA("seraphina", EntityType.ZOMBIE, true, 3, 3, "Seraphina The Mercy Maiden",
            MobCategory.HOLY, MobDifficulty.ELITE, EnumSet.of(MobAbility.MERCY_MASTERY, MobAbility.COMPASSION_AURA, MobAbility.DIVINE_HEALING)),

    ARACHNIA("arachnia", EntityType.SPIDER, true, 3, 3, "Arachnia The Web Mistress",
            MobCategory.ARTHROPOD, MobDifficulty.ELITE, EnumSet.of(MobAbility.WEB_MASTERY, MobAbility.SPIDER_SPAWN, MobAbility.SILK_SPINNER)),

    // ================ TIER 4 ELITES ================

    KARNATH("karnath", EntityType.ZOMBIE, true, 4, 4, "Karnath The Blood Reaper",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.CARNAGE_MASTERY, MobAbility.BLOOD_FRENZY, MobAbility.SLAUGHTER_AURA)),

    ZEPHYR("zephyr", EntityType.WITHER_SKELETON, true, 4, 4, "Zephyr The Storm Caller",
            MobCategory.ELEMENTAL, MobDifficulty.ELITE, EnumSet.of(MobAbility.WIND_MASTERY, MobAbility.STORM_CALLER, MobAbility.TEMPEST_FURY)),

    // ================ TIER 5 ELITES - THE ANCIENT LORDS ================

    RIMECLAW("rimeclaw", EntityType.STRAY, true, 5, 5, "Rimeclaw The Eternal Winter",
            MobCategory.ELEMENTAL, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.ETERNAL_WINTER, MobAbility.GLACIER_MASTERY, MobAbility.PERMAFROST_AURA)),

    THALASSA("thalassa", EntityType.DROWNED, true, 5, 5, "Thalassa The Ocean Sovereign",
            MobCategory.ELEMENTAL, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.OCEAN_MASTERY, MobAbility.TIDE_TURNER, MobAbility.ABYSSAL_POWER)),

    PYRION("pyrion", EntityType.WITHER_SKELETON, true, 5, 5, "Pyrion The Flame Emperor",
            MobCategory.ELEMENTAL, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.INFERNO_MASTERY, MobAbility.PHOENIX_HEART, MobAbility.SOLAR_CROWN)),

    MERIDIAN("meridian", EntityType.WARDEN, true, 5, 5, "Meridian The Reality Breaker",
            MobCategory.COSMIC, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.REALITY_FRACTURE, MobAbility.QUANTUM_FLUX, MobAbility.COSMIC_EYE)),

    NETHYS("nethys", EntityType.WITHER_SKELETON, true, 5, 5, "Nethys The Void Sovereign",
            MobCategory.VOID, MobDifficulty.LEGENDARY, EnumSet.of(MobAbility.VOID_CALLING, MobAbility.SHADOW_FLAME, MobAbility.ABYSSAL_CROWN)),

    // ================ WORLD BOSSES ================

    FROZEN_ELITE("frozenelite", EntityType.WITHER_SKELETON, true, 5, 6, "Frost The Exiled King",
            MobCategory.ELEMENTAL, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.ICE_MASTERY, MobAbility.FROZEN_REALM), true),

    FROZEN_BOSS("frozenboss", EntityType.WITHER_SKELETON, true, 5, 6, "The Conqueror of The North",
            MobCategory.ELEMENTAL, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.ARCTIC_COMMAND, MobAbility.BLIZZARD_FURY), true),

    FROZEN_GOLEM("frozengolem", EntityType.IRON_GOLEM, true, 5, 6, "The Eternal Crypt Guardian",
            MobCategory.CONSTRUCT, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.ICE_ARMOR, MobAbility.CRYPT_POWER), true),

    FROSTWING("frostwing", EntityType.PHANTOM, true, 5, 6, "Frost-Wing The Frozen Titan",
            MobCategory.DRAGON, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.FLIGHT, MobAbility.ICE_BREATH), true),

    CHRONOS("chronos", EntityType.WITHER, true, 5, 6, "Chronos, The Time Lord",
            MobCategory.TEMPORAL, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.TIME_MASTERY, MobAbility.TEMPORAL_SHIFT), true),

    // ================ SPECIAL TYPES ================

    PRISONER("prisoner", EntityType.ZOMBIE, false, 3, 5, "Prisoner",
            MobCategory.HUMANOID, MobDifficulty.NORMAL, EnumSet.of(MobAbility.DESPERATION)),

    SKELLY_GUARDIAN("skellyDSkeletonGuardian", EntityType.SKELETON, true, 4, 5, "The Skeletal Guardian Overlord",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.GUARDIAN_DUTY, MobAbility.PROTECTIVE_AURA)),

    SPECTRAL_GUARD("spectralguard", EntityType.ZOMBIFIED_PIGLIN, true, 1, 1, "The Evil Spectral's Impish Guard",
            MobCategory.SPECTRAL, MobDifficulty.ELITE, EnumSet.of(MobAbility.SPECTRAL_FORM, MobAbility.GUARD_DUTY)),

    BOSS_SKELETON("bossSkeletonDungeon", EntityType.WITHER_SKELETON, true, 5, 5, "The Restless Skeleton Deathlord",
            MobCategory.UNDEAD, MobDifficulty.BOSS, EnumSet.of(MobAbility.DEATH_COMMAND, MobAbility.UNDEAD_LEGION)),

    WEAK_SKELETON("weakskeleton", EntityType.SKELETON, true, 5, 5, "The Infernal Skeletal Keeper",
            MobCategory.UNDEAD, MobDifficulty.ELITE, EnumSet.of(MobAbility.SOUL_KEEPING, MobAbility.SPIRIT_GUARD)),

    SPECTRAL_KNIGHT("spectralKnight", EntityType.ZOMBIFIED_PIGLIN, true, 4, 4, "The Evil Spectral Overlord",
            MobCategory.SPECTRAL, MobDifficulty.ELITE, EnumSet.of(MobAbility.PHASE_SHIFT, MobAbility.SPECTRAL_ARMY)),

    FROSTKING("frostking", EntityType.WITHER_SKELETON, true, 6, 6, "The Frost King Eternal",
            MobCategory.ELEMENTAL, MobDifficulty.WORLD_BOSS, EnumSet.of(MobAbility.ICE_MASTERY, MobAbility.ROYAL_COMMAND), true);

    // ================ ENUMS FOR CATEGORIZATION ================

    public enum MobCategory {
        UNDEAD, HUMANOID, ARTHROPOD, SLIME, NETHER, CONSTRUCT, ANIMAL,
        SERPENT, SPECTRAL, DEMON, ELEMENTAL, GUARDIAN, MYTHICAL, CASTER,
        VOID, DRAGON, TEMPORAL, DWARF, HOLY, COSMIC, NATURE
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
        CRITICAL_STRIKES, IMPALING_STRIKES, SAVAGE_STRIKES, BLOODLUST, LIFE_STEAL,

        // Movement Abilities
        FAST_MOVEMENT, CLIMBING, BOUNCING, FLIGHT, PHASE_SHIFT,

        // Defensive Abilities
        FIRE_RESISTANCE, MAGIC_RESISTANCE, HEAVY_ARMOR, KNOCKBACK_RESISTANCE,
        ICE_ARMOR, SPECTRAL_FORM,

        // Mastery Abilities
        FIRE_MASTERY, POISON_MASTERY, PLAGUE_MASTERY, ICE_MASTERY, WIND_MASTERY,
        BONE_MASTERY, MERCY_MASTERY, AVARICE_MASTERY, NIGHTMARE_MASTERY, CARNAGE_MASTERY,
        INFERNO_MASTERY, OCEAN_MASTERY, ETERNAL_WINTER, GLACIER_MASTERY,

        // Aura Effects
        EMBER_AURA, TOXIC_AURA, MOURNING_AURA, COMPASSION_AURA, SLAUGHTER_AURA,
        PERMAFROST_AURA, POISON_AURA, DECAY_AURA, CORRUPTION, DOOM_PROPHECY,
        APOCALYPSE_AURA, CRUELTY_AURA, KING_AURA, PROTECTIVE_AURA, DARKNESS_SHROUD,

        // Strike Abilities
        THORN_STRIKE, CELESTIAL_EDGE, SOUL_RIPPER, ETERNAL_BLADE, GOLDEN_TOUCH,
        TREASURE_HOARD, REALITY_SHIFT, DREAM_WEAVER, STORM_CALLER, TEMPEST_FURY,

        // Nature & Corruption
        NATURE_CORRUPTION, DISEASE_SPREAD,

        // Divine & Holy
        DIVINE_POWER, DIVINE_HEALING,

        // Death & Undead
        DEATH_MASTERY, UNDEAD_COMMAND, SOUL_DRAIN, DEATH_MAGIC, NECROMANCY,
        BONE_SHARDS, BONE_ARMY, UNDEAD_LEGION, DEATH_COMMAND,

        // Dwarven & Mountain
        MOUNTAIN_STRENGTH, SIEGE_MASTERY, DWARVEN_FURY,

        // Elemental Powers
        TIDE_TURNER, ABYSSAL_POWER, PHOENIX_HEART, SOLAR_CROWN, BLIZZARD_FURY,
        ARCTIC_COMMAND, FROZEN_REALM,

        // Cosmic & Void
        REALITY_FRACTURE, QUANTUM_FLUX, COSMIC_EYE, VOID_CALLING, SHADOW_FLAME,
        ABYSSAL_CROWN, VOID_MASTERY, REALITY_TEAR,

        // Spider Abilities
        WEB_MASTERY, SPIDER_SPAWN, SILK_SPINNER,

        // Blood & Violence
        BLOOD_FRENZY,

        // Special Powers
        NETHER_MAGIC, HELL_FLAMES, DEMONIC_POWER, ARCANE_MASTERY, REALITY_WARP,
        TIME_MASTERY, TEMPORAL_SHIFT, ICE_BREATH,

        // Leadership & Control
        LEADERSHIP, AUTHORITY, CROWD_CONTROL, ROYAL_COMMAND, ROYAL_DECREE,
        TRIBAL_FURY, ETERNAL_VIGIL,

        // Summoning & Spawning
        SPECTRAL_ARMY,

        // Unique Abilities
        NIGHT_VISION, COMBAT_MASTERY, WEALTH_DRAIN, TIME_SIGHT, TORTURE_MASTERY,
        SONIC_BOOM, SOUL_KEEPING, SPIRIT_GUARD, HOLIDAY_TERROR, PUNISHMENT_CHAINS,
        MASSIVE_HEALTH, DESPERATION, GUARDIAN_DUTY, GUARD_DUTY, CRYPT_POWER
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

    // ================ ENHANCED STATIC LOOKUP SYSTEM ================

    // Static lookup cache - using ConcurrentHashMap for thread safety
    private static final Map<String, MobType> BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, MobType> NORMALIZED_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<MobCategory, List<MobType>> BY_CATEGORY = new ConcurrentHashMap<>();
    private static final Map<MobDifficulty, List<MobType>> BY_DIFFICULTY = new ConcurrentHashMap<>();

    // Initialization state tracking
    private static volatile boolean initialized = false;
    private static final Object initLock = new Object();

    /**
     * FIXED: Static initialization that CANNOT fail
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
     * FIXED: Initialize all static mappings including ALL elite types
     */
    private static void initializeStaticMappings() {
        synchronized (initLock) {
            if (initialized) return;

            try {
                System.out.println("[MobType] Starting comprehensive static initialization...");

                // Clear all maps first
                BY_ID.clear();
                NORMALIZED_MAPPINGS.clear();
                BY_CATEGORY.clear();
                BY_DIFFICULTY.clear();

                // Process each enum value with comprehensive error handling
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

                        // Add comprehensive variations for this type (ESPECIALLY elites)
                        addComprehensiveVariationsForType(type);

                        // Category mappings
                        BY_CATEGORY.computeIfAbsent(type.category, k -> new ArrayList<>()).add(type);
                        BY_DIFFICULTY.computeIfAbsent(type.difficulty, k -> new ArrayList<>()).add(type);

                        successful++;

                        // Debug elite types specifically
                        if (type.isElite()) {
                            System.out.println("[MobType] Registered elite type: " + typeId + " -> " + type.name());
                        }

                    } catch (Exception e) {
                        System.err.println("[MobType] Error processing " + type.name() + ": " + e.getMessage());
                        failed++;
                    }
                }

                // Add comprehensive legacy mappings
                addComprehensiveLegacyMappings();

                // CRITICAL: Validate that all T5 elites are properly mapped
                validateCriticalEliteTypes();

                initialized = true;
                System.out.println("[MobType] Initialization complete: " + successful + " types loaded, " +
                        failed + " failed, " + NORMALIZED_MAPPINGS.size() + " total mappings");

                // Debug: Print all registered elite types
                System.out.println("[MobType] Registered elite types:");
                for (Map.Entry<String, MobType> entry : NORMALIZED_MAPPINGS.entrySet()) {
                    if (entry.getValue().isElite()) {
                        System.out.println("  " + entry.getKey() + " -> " + entry.getValue().name());
                    }
                }

            } catch (Exception e) {
                System.err.println("[MobType] Critical initialization error: " + e.getMessage());
                e.printStackTrace();
                emergencyInitialization();
            }
        }
    }

    /**
     * FIXED: Add comprehensive variations for each mob type, especially elites
     */
    private static void addComprehensiveVariationsForType(MobType type) {
        String baseId = type.id;

        try {
            // Basic variations
            addMapping(baseId.replace("_", ""), type);
            addMapping(baseId.replace(" ", ""), type);

            // ELITE-SPECIFIC VARIATIONS - This is critical for T5 elites
            if (type.isElite()) {
                // Add elite suffix variations
                addMapping(baseId + "_elite", type);
                addMapping(baseId + "elite", type);
                addMapping("elite_" + baseId, type);
                addMapping("elite" + baseId, type);
            }

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

                // CRITICAL: Elite-specific mappings for T5 elites
                case "meridian":
                    addMapping("meridian_elite", type);
                    addMapping("warden", type); // Entity type mapping
                    addMapping("reality_breaker", type);
                    addMapping("realitybreaker", type);
                    break;

                case "pyrion":
                    addMapping("pyrion_elite", type);
                    addMapping("fire_lord", type);
                    addMapping("firelord", type);
                    addMapping("flame_emperor", type);
                    addMapping("flameemperor", type);
                    break;

                case "rimeclaw":
                    addMapping("rimeclaw_elite", type);
                    addMapping("ice_lord", type);
                    addMapping("icelord", type);
                    addMapping("eternal_winter", type);
                    addMapping("eternalwinter", type);
                    break;

                case "thalassa":
                    addMapping("thalassa_elite", type);
                    addMapping("ocean_lord", type);
                    addMapping("oceanlord", type);
                    addMapping("ocean_sovereign", type);
                    addMapping("oceansovereign", type);
                    break;

                case "nethys":
                    addMapping("nethys_elite", type);
                    addMapping("void_lord", type);
                    addMapping("voidlord", type);
                    addMapping("void_sovereign", type);
                    addMapping("voidsovereign", type);
                    break;

                // Other elite mappings with new titles
                case "malachar":
                    addMapping("malachar_elite", type);
                    addMapping("ember_lord", type);
                    addMapping("emberlord", type);
                    break;

                case "xerathen":
                    addMapping("xerathen_elite", type);
                    addMapping("thorn_warden", type);
                    addMapping("thornwarden", type);
                    break;

                case "veridiana":
                    addMapping("veridiana_elite", type);
                    addMapping("plague_bringer", type);
                    addMapping("plaguebringer", type);
                    break;

                case "thorgrim":
                    addMapping("thorgrim_elite", type);
                    addMapping("mountain_king", type);
                    addMapping("mountainking", type);
                    break;

                case "lysander":
                    addMapping("lysander_elite", type);
                    addMapping("divine_blade", type);
                    addMapping("divineblade", type);
                    break;

                case "morgana":
                    addMapping("morgana_elite", type);
                    addMapping("soul_ripper", type);
                    addMapping("soulripper", type);
                    break;

                case "vex_elite":
                    addMapping("vex", type);
                    addMapping("vexelite", type);
                    addMapping("nightmare_weaver", type);
                    addMapping("nightmareweaver", type);
                    break;

                case "cornelius":
                    addMapping("cornelius_elite", type);
                    addMapping("golden_tyrant", type);
                    addMapping("goldentyrant", type);
                    break;

                case "valdris":
                    addMapping("valdris_elite", type);
                    addMapping("bone_emperor", type);
                    addMapping("boneemperor", type);
                    break;

                case "seraphina":
                    addMapping("seraphina_elite", type);
                    addMapping("mercy_maiden", type);
                    addMapping("mercymaiden", type);
                    break;

                case "arachnia":
                    addMapping("arachnia_elite", type);
                    addMapping("spider_queen", type);
                    addMapping("spiderqueen", type);
                    addMapping("web_mistress", type);
                    addMapping("webmistress", type);
                    break;

                case "karnath":
                    addMapping("karnath_elite", type);
                    addMapping("blood_reaper", type);
                    addMapping("bloodreaper", type);
                    break;

                case "zephyr":
                    addMapping("zephyr_elite", type);
                    addMapping("storm_caller", type);
                    addMapping("stormcaller", type);
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
                    addMapping("crypt_guardian", type);
                    addMapping("cryptguardian", type);
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

                case "frostking":
                    addMapping("frost_king", type);
                    addMapping("iceking", type);
                    addMapping("ice_king", type);
                    addMapping("frost_king_eternal", type);
                    addMapping("frostkingeteranl", type);
                    break;

                case "chronos":
                    addMapping("time_lord", type);
                    addMapping("timelord", type);
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
            String normalizedKey = key.toLowerCase().trim();
            NORMALIZED_MAPPINGS.put(normalizedKey, type);
        }
    }

    /**
     * Add comprehensive legacy mappings including elite shortcuts
     */
    private static void addComprehensiveLegacyMappings() {
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

            // CRITICAL: Elite shortcuts for T5 elites
            safePutMapping("warden", "meridian"); // Warden entity type maps to Meridian elite

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
     * CRITICAL: Validate that all important elite types are properly mapped
     */
    private static void validateCriticalEliteTypes() {
        String[] criticalEliteTypes = {
                "meridian", "pyrion", "rimeclaw", "thalassa", "nethys",
                "malachar", "xerathen", "veridiana", "thorgrim", "lysander", "morgana",
                "cornelius", "valdris", "seraphina", "arachnia", "karnath", "zephyr",
                "frozenboss", "frozenelite", "frozengolem", "frostwing", "chronos"
        };

        boolean allValid = true;
        StringBuilder missing = new StringBuilder();

        for (String type : criticalEliteTypes) {
            if (!NORMALIZED_MAPPINGS.containsKey(type)) {
                System.err.println("[MobType] Missing critical elite mapping: " + type);
                missing.append(type).append(", ");
                allValid = false;
            } else {
                System.out.println("[MobType] ✓ Elite type validated: " + type);
            }
        }

        if (allValid) {
            System.out.println("[MobType] All critical elite mappings validated successfully");
        } else {
            System.err.println("[MobType] Missing elite mappings: " + missing.toString());
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

                        // For elites, add basic variations
                        if (type.isElite()) {
                            NORMALIZED_MAPPINGS.put(id + "_elite", type);
                            NORMALIZED_MAPPINGS.put(id + "elite", type);
                        }
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

    // ================ ENHANCED LOOKUP METHODS ================

    /**
     * FIXED: Get mob type by ID with enhanced elite support
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

            // Enhanced elite pattern matching
            type = tryElitePatternMatching(normalizedId);
            if (type != null) {
                // Cache successful transformation
                NORMALIZED_MAPPINGS.put(normalizedId, type);
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
     * NEW: Enhanced elite pattern matching
     */
    private static MobType tryElitePatternMatching(String id) {
        // Handle elite-specific patterns
        if (id.contains("elite")) {
            String baseId = id.replace("elite", "").replace("_", "");
            MobType type = NORMALIZED_MAPPINGS.get(baseId);
            if (type != null && type.isElite()) {
                return type;
            }
        }

        // Handle T5 elite names specifically
        switch (id) {
            case "warden":
                return NORMALIZED_MAPPINGS.get("meridian");
            case "fire_lord":
            case "firelord":
            case "flame_emperor":
            case "flameemperor":
                return NORMALIZED_MAPPINGS.get("pyrion");
            case "ice_lord":
            case "icelord":
            case "eternal_winter":
            case "eternalwinter":
                return NORMALIZED_MAPPINGS.get("rimeclaw");
            case "ocean_lord":
            case "oceanlord":
            case "ocean_sovereign":
            case "oceansovereign":
                return NORMALIZED_MAPPINGS.get("thalassa");
            case "void_lord":
            case "voidlord":
            case "void_sovereign":
            case "voidsovereign":
                return NORMALIZED_MAPPINGS.get("nethys");
            case "reality_breaker":
            case "realitybreaker":
                return NORMALIZED_MAPPINGS.get("meridian");
        }

        return null;
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
     * FIXED: Check if a type ID is valid - enhanced for elites
     */
    public static boolean isValidType(String id) {
        MobType found = getById(id);
        return found != null;
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
                id.equals("shulker") || id.equals("slime") || id.equals("turkey") ||
                id.equals("giant") || id.equals("prisoner") || id.equals("warden");
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
            case "warden":
                return getWardenTierName(tier);
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

    private String getWardenTierName(int tier) {
        switch (tier) {
            case 1: return "Young Warden";
            case 2: return "Warden";
            case 3: return "Ancient Warden";
            case 4: return "Warden Lord";
            case 5: return "Primordial Warden";
            case 6: return "Cosmic Warden";
            default: return "Warden";
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
     * FIXED: Check if a tier is valid for this mob type
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
        info.append("Elite Types Count: ").append(
                Arrays.stream(values()).mapToInt(t -> t.isElite() ? 1 : 0).sum()).append("\n");

        info.append("Critical Types:\n");
        String[] criticalTypes = {"skeleton", "zombie", "witherskeleton", "spider", "cavespider", "magmacube", "meridian", "pyrion"};
        for (String type : criticalTypes) {
            MobType found = getById(type);
            info.append("  ").append(type).append(": ").append(found != null ? "✓" : "✗").append("\n");
        }

        info.append("Elite Types:\n");
        for (MobType type : values()) {
            if (type.isElite()) {
                boolean mapped = NORMALIZED_MAPPINGS.containsKey(type.getId());
                info.append("  ").append(type.getId()).append(": ").append(mapped ? "✓" : "✗").append("\n");
            }
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