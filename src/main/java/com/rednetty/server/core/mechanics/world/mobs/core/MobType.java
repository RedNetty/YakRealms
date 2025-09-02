package com.rednetty.server.core.mechanics.world.mobs.core;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MobType enum with guaranteed initialization and comprehensive elite mob support.
 * UPDATED: T6 Netherite support with GOLD color throughout.
 * CLEANUP: Simplified mappings, removed redundant variations, improved thread-safety,
 *          reduced complexity in lookup methods, added better diagnostics.
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

    // ================ NEWER MOB TYPES (Paper 1.21.8) ================
    
    ALLAY("allay", EntityType.ALLAY, false, 1, 3, "Allay",
            MobCategory.SPECTRAL, MobDifficulty.EASY, EnumSet.of(MobAbility.FLIGHT, MobAbility.ITEM_COLLECTION)),
            
    SNIFFER("sniffer", EntityType.SNIFFER, false, 2, 4, "Sniffer",
            MobCategory.ANIMAL, MobDifficulty.NORMAL, EnumSet.of(MobAbility.MASSIVE_HEALTH, MobAbility.DIGGING)),
            
    CAMEL("camel", EntityType.CAMEL, false, 1, 3, "Camel",
            MobCategory.ANIMAL, MobDifficulty.EASY, EnumSet.of(MobAbility.FAST_MOVEMENT, MobAbility.DESERT_ADAPTATION)),
            
    BREEZE("breeze", EntityType.BREEZE, false, 3, 5, "Breeze",
            MobCategory.ELEMENTAL, MobDifficulty.HARD, EnumSet.of(MobAbility.FLIGHT, MobAbility.WIND_MASTERY)),
            
    ARMADILLO("armadillo", EntityType.ARMADILLO, false, 1, 3, "Armadillo",
            MobCategory.ANIMAL, MobDifficulty.EASY, EnumSet.of(MobAbility.HEAVY_ARMOR, MobAbility.ROLLING_DEFENSE)),

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

    // T6 ELITES
    APOCALYPSE("apocalypse", EntityType.WITHER_SKELETON, true, 6, 6, "Apocalypse The World Destroyer",
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
        MASSIVE_HEALTH, DESPERATION, GUARDIAN_DUTY, GUARD_DUTY, CRYPT_POWER,
        
        // Paper 1.21.8 New Mob Abilities
        ITEM_COLLECTION, DIGGING, DESERT_ADAPTATION, ROLLING_DEFENSE
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

    // ================ STATIC LOOKUP SYSTEM ================

    private static final Map<String, MobType> BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, MobType> NORMALIZED_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<MobCategory, List<MobType>> BY_CATEGORY = new ConcurrentHashMap<>();
    private static final Map<MobDifficulty, List<MobType>> BY_DIFFICULTY = new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;
    private static final Object initLock = new Object();

    /**
     * Static initialization that cannot fail.
     */
    static {
        try {
            initializeStaticMappings();
        } catch (Exception e) {
            System.err.println("[MobType] CRITICAL: Static initialization failed: " + e.getMessage());
            e.printStackTrace();
            emergencyInitialization();
        }
    }

    /**
     * Initialize all static mappings including all elite types.
     */
    private static void initializeStaticMappings() {
        synchronized (initLock) {
            if (initialized) return;

            try {
                System.out.println("[MobType] Starting comprehensive static initialization...");

                BY_ID.clear();
                NORMALIZED_MAPPINGS.clear();
                BY_CATEGORY.clear();
                BY_DIFFICULTY.clear();

                int successful = 0;
                int failed = 0;

                for (MobType type : values()) {
                    try {
                        if (type.id == null || type.id.trim().isEmpty()) {
                            System.err.println("[MobType] Skipping type with null/empty ID: " + type.name());
                            failed++;
                            continue;
                        }

                        String typeId = type.id.toLowerCase().trim();

                        BY_ID.put(typeId, type);
                        NORMALIZED_MAPPINGS.put(typeId, type);

                        addEssentialVariationsForType(type);

                        BY_CATEGORY.computeIfAbsent(type.category, k -> new ArrayList<>()).add(type);
                        BY_DIFFICULTY.computeIfAbsent(type.difficulty, k -> new ArrayList<>()).add(type);

                        successful++;

                        // Elite types registered quietly for cleaner startup

                    } catch (Exception e) {
                        System.err.println("[MobType] Error processing " + type.name() + ": " + e.getMessage());
                        failed++;
                    }
                }

                addEssentialLegacyMappings();

                validateCriticalEliteTypes();

                initialized = true;
                long eliteCount = Arrays.stream(values()).filter(MobType::isElite).count();
                System.out.println("[MobType] Initialization complete: " + successful + " types loaded, " +
                        failed + " failed, " + eliteCount + " elites, " + NORMALIZED_MAPPINGS.size() + " total mappings");

            } catch (Exception e) {
                System.err.println("[MobType] Critical initialization error: " + e.getMessage());
                e.printStackTrace();
                emergencyInitialization();
            }
        }
    }

    /**
     * Add essential variations for each mob type, focusing on elites.
     */
    private static void addEssentialVariationsForType(MobType type) {
        String baseId = type.id.toLowerCase().trim();

        // Basic variations
        addMapping(baseId.replace("_", ""), type);
        addMapping(baseId.replace(" ", ""), type);

        // Elite-specific variations
        if (type.isElite()) {
            addMapping(baseId + "_elite", type);
            addMapping("elite_" + baseId, type);
        }

        // Specific type variations (reduced redundancy)
        switch (baseId) {
            case "witherskeleton":
                addMapping("wither_skeleton", type);
                break;
            case "cavespider":
                addMapping("cave_spider", type);
                break;
            case "magmacube":
                addMapping("magma_cube", type);
                break;
            case "zombifiedpiglin":
                addMapping("zombified_piglin", type);
                addMapping("pigzombie", type);
                addMapping("zombiepigman", type);
                break;
            case "elderguardian":
                addMapping("elder_guardian", type);
                break;
            case "golem":
                addMapping("iron_golem", type);
                break;

            // Elite mappings (simplified)
            case "meridian":
                addMapping("warden", type);
                addMapping("reality_breaker", type);
                break;
            case "pyrion":
                addMapping("fire_lord", type);
                addMapping("flame_emperor", type);
                break;
            case "rimeclaw":
                addMapping("ice_lord", type);
                addMapping("eternal_winter", type);
                break;
            case "thalassa":
                addMapping("ocean_lord", type);
                addMapping("ocean_sovereign", type);
                break;
            case "nethys":
                addMapping("void_lord", type);
                addMapping("void_sovereign", type);
                break;

            // Other elites
            case "malachar":
                addMapping("ember_lord", type);
                break;
            case "xerathen":
                addMapping("thorn_warden", type);
                break;
            case "veridiana":
                addMapping("plague_bringer", type);
                break;
            case "thorgrim":
                addMapping("mountain_king", type);
                break;
            case "lysander":
                addMapping("divine_blade", type);
                break;
            case "morgana":
                addMapping("soul_ripper", type);
                break;
            case "vex_elite":
                addMapping("nightmare_weaver", type);
                break;
            case "cornelius":
                addMapping("golden_tyrant", type);
                break;
            case "valdris":
                addMapping("bone_emperor", type);
                break;
            case "seraphina":
                addMapping("mercy_maiden", type);
                break;
            case "arachnia":
                addMapping("web_mistress", type);
                break;
            case "karnath":
                addMapping("blood_reaper", type);
                break;
            case "zephyr":
                addMapping("storm_caller", type);
                break;

            // Boss variations
            case "bossskeletondungeon":
                addMapping("boss_skeleton_dungeon", type);
                break;
            case "frozenboss":
                addMapping("frozen_boss", type);
                break;
            case "frozenelite":
                addMapping("frozen_elite", type);
                break;
            case "frozengolem":
                addMapping("frozen_golem", type);
                break;
            case "spectralguard":
                addMapping("spectral_guard", type);
                break;
            case "spectralknight":
                addMapping("spectral_knight", type);
                break;
            case "weakskeleton":
                addMapping("weak_skeleton", type);
                break;
            case "skellydskeletonguardian":
                addMapping("skeletal_guardian", type);
                break;
            case "frostking":
                addMapping("frost_king", type);
                break;
            case "chronos":
                addMapping("time_lord", type);
                break;
        }
    }

    /**
     * Safely add a mapping.
     */
    private static void addMapping(String key, MobType type) {
        if (key != null && !key.trim().isEmpty() && type != null) {
            NORMALIZED_MAPPINGS.put(key.toLowerCase().trim(), type);
        }
    }

    /**
     * Add essential legacy mappings.
     */
    private static void addEssentialLegacyMappings() {
        // Common shortcuts
        safePutMapping("skelly", "skeleton");
        safePutMapping("zomb", "zombie");
        safePutMapping("spid", "spider");
        safePutMapping("creep", "creeper");
        safePutMapping("ender", "enderman");

        // Boss shortcuts
        safePutMapping("boss", "bossskeletondungeon");
        safePutMapping("frozen", "frozenboss");
        safePutMapping("ice", "frozenboss");

        // Elite shortcuts
        safePutMapping("warden", "meridian");

        System.out.println("[MobType] Legacy mappings added successfully");
    }

    /**
     * Safely add a legacy mapping.
     */
    private static void safePutMapping(String alias, String existingKey) {
        MobType existingType = NORMALIZED_MAPPINGS.get(existingKey.toLowerCase().trim());
        if (existingType != null) {
            NORMALIZED_MAPPINGS.put(alias.toLowerCase().trim(), existingType);
        }
    }

    /**
     * Validate critical elite types.
     */
    private static void validateCriticalEliteTypes() {
        String[] criticalEliteTypes = {
                "meridian", "apocalypse", "pyrion", "rimeclaw", "thalassa", "nethys",
                "malachar", "xerathen", "veridiana", "thorgrim", "lysander", "morgana",
                "cornelius", "valdris", "seraphina", "arachnia", "karnath", "zephyr",
                "frozenboss", "frozenelite", "frozengolem", "frostwing", "chronos"
        };

        boolean allValid = true;
        StringBuilder missing = new StringBuilder();

        for (String type : criticalEliteTypes) {
            if (!NORMALIZED_MAPPINGS.containsKey(type.toLowerCase().trim())) {
                System.err.println("[MobType] Missing critical elite mapping: " + type);
                missing.append(type).append(", ");
                allValid = false;
            }
        }

        if (allValid) {
            System.out.println("[MobType] All critical elite mappings validated successfully");
        } else {
            System.err.println("[MobType] Missing elite mappings: " + missing.toString());
        }
    }

    /**
     * Emergency initialization if normal init fails.
     */
    private static void emergencyInitialization() {
        System.err.println("[MobType] Running emergency initialization...");

        BY_ID.clear();
        NORMALIZED_MAPPINGS.clear();

        for (MobType type : values()) {
            if (type.id != null && !type.id.trim().isEmpty()) {
                String id = type.id.toLowerCase().trim();
                BY_ID.put(id, type);
                NORMALIZED_MAPPINGS.put(id, type);

                if (type.isElite()) {
                    NORMALIZED_MAPPINGS.put(id + "_elite", type);
                }
            }
        }

        initialized = true;
        System.err.println("[MobType] Emergency initialization complete with " +
                NORMALIZED_MAPPINGS.size() + " mappings");
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

    // ================ LOOKUP METHODS ================

    /**
     * Get mob type by ID with elite support.
     */
    public static MobType getById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }

        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    initializeStaticMappings();
                }
            }
        }

        String normalizedId = id.toLowerCase().trim();

        MobType type = NORMALIZED_MAPPINGS.get(normalizedId);
        if (type != null) {
            return type;
        }

        type = tryElitePatternMatching(normalizedId);
        if (type != null) {
            NORMALIZED_MAPPINGS.put(normalizedId, type);
            return type;
        }

        type = tryCommonTransformations(normalizedId);
        if (type != null) {
            NORMALIZED_MAPPINGS.put(normalizedId, type);
            return type;
        }

        type = tryPartialMatching(normalizedId);
        if (type != null) {
            NORMALIZED_MAPPINGS.put(normalizedId, type);
            return type;
        }

        return null;
    }

    /**
     * Elite pattern matching.
     */
    private static MobType tryElitePatternMatching(String id) {
        if (id.contains("elite")) {
            String baseId = id.replace("elite", "").replace("_", "").trim();
            MobType type = NORMALIZED_MAPPINGS.get(baseId);
            if (type != null && type.isElite()) {
                return type;
            }
        }

        // T5 elite names
        switch (id) {
            case "warden":
                return NORMALIZED_MAPPINGS.get("meridian");
            case "fire_lord":
            case "flame_emperor":
                return NORMALIZED_MAPPINGS.get("pyrion");
            case "ice_lord":
            case "eternal_winter":
                return NORMALIZED_MAPPINGS.get("rimeclaw");
            case "ocean_lord":
            case "ocean_sovereign":
                return NORMALIZED_MAPPINGS.get("thalassa");
            case "void_lord":
            case "void_sovereign":
                return NORMALIZED_MAPPINGS.get("nethys");
            case "reality_breaker":
                return NORMALIZED_MAPPINGS.get("meridian");
        }

        return null;
    }

    /**
     * Try common transformations.
     */
    private static MobType tryCommonTransformations(String id) {
        String withoutUnderscores = id.replace("_", "").trim();
        MobType type = NORMALIZED_MAPPINGS.get(withoutUnderscores);
        if (type != null) return type;

        String withoutSpaces = id.replace(" ", "").trim();
        type = NORMALIZED_MAPPINGS.get(withoutSpaces);
        if (type != null) return type;

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
            return NORMALIZED_MAPPINGS.get("bossskeletondungeon");
        }

        return null;
    }

    /**
     * Try partial matching as last resort.
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
     * Check if a type ID is valid - for elites.
     */
    public static boolean isValidType(String id) {
        return getById(id) != null;
    }

    /**
     * Get all available mob type IDs.
     */
    public static Set<String> getAllValidIds() {
        if (!initialized) {
            initializeStaticMappings();
        }
        return new HashSet<>(NORMALIZED_MAPPINGS.keySet());
    }

    // ================ TIER-SPECIFIC NAMING ================

    /**
     * Get tier-specific name with variations.
     */
    public String getTierSpecificName(int tier) {
        if (elite && !isGenericType()) {
            return defaultName;
        }
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
        return switch (tier) {
            case 1 -> "Broken Skeleton";
            case 2 -> "Wandering Cracking Skeleton";
            case 3 -> "Demonic Skeleton";
            case 4 -> "Skeleton Guardian";
            case 5 -> "Infernal Skeleton";
            case 6 -> "Celestial Skeleton";
            default -> "Skeleton";
        };
    }

    private String getWitherSkeletonTierName(int tier) {
        return switch (tier) {
            case 1 -> "Broken Chaos Skeleton";
            case 2 -> "Wandering Cracking Chaos Skeleton";
            case 3 -> "Demonic Chaos Skeleton";
            case 4 -> "Skeleton Chaos Guardian";
            case 5 -> "Infernal Chaos Skeleton";
            case 6 -> "Celestial Chaos Overlord";
            default -> "Chaos Skeleton";
        };
    }

    private String getZombieTierName(int tier) {
        return switch (tier) {
            case 1 -> "Rotting Zombie";
            case 2 -> "Savaged Zombie";
            case 3 -> "Greater Zombie";
            case 4 -> "Demonic Zombie";
            case 5 -> "Infernal Zombie";
            case 6 -> "Celestial Zombie";
            default -> "Zombie";
        };
    }

    private String getMagmaCubeTierName(int tier) {
        return switch (tier) {
            case 1 -> "Weak Magma Cube";
            case 2 -> "Bubbling Magma Cube";
            case 3 -> "Unstable Magma Cube";
            case 4 -> "Boiling Magma Cube";
            case 5 -> "Unstoppable Magma Cube";
            case 6 -> "Celestial Magma Cube";
            default -> "Magma Cube";
        };
    }

    private String getSilverfishTierName(int tier) {
        return switch (tier) {
            case 1 -> "Weak SilverFish";
            case 2 -> "Pointy SilverFish";
            case 3 -> "Unstable SilverFish";
            case 4 -> "Mean SilverFish";
            case 5 -> "Rude SilverFish";
            case 6 -> "Celestial SilverFish";
            default -> "SilverFish";
        };
    }

    private String getSpiderTierName(int tier) {
        String prefix = switch (tier) {
            case 1 -> "Harmless";
            case 2 -> "Wild";
            case 3 -> "Fierce";
            case 4 -> "Dangerous";
            case 5 -> "Lethal";
            case 6 -> "Divine";
            default -> "";
        };

        String baseType = id.equals("cavespider") ? "Cave Spider" : "Spider";
        return prefix.isEmpty() ? baseType : prefix + " " + baseType;
    }

    private String getGolemTierName(int tier) {
        return switch (tier) {
            case 1 -> "Broken Golem";
            case 2 -> "Rusty Golem";
            case 3 -> "Restored Golem";
            case 4 -> "Mountain Golem";
            case 5 -> "Powerful Golem";
            case 6 -> "Celestial Golem";
            default -> "Golem";
        };
    }

    private String getImpTierName(int tier) {
        return switch (tier) {
            case 1 -> "Ugly Imp";
            case 2 -> "Angry Imp";
            case 3 -> "Warrior Imp";
            case 4 -> "Armoured Imp";
            case 5 -> "Infernal Imp";
            case 6 -> "Celestial Imp";
            default -> "Imp";
        };
    }

    private String getDaemonTierName(int tier) {
        return switch (tier) {
            case 1 -> "Broken Daemon";
            case 2 -> "Wandering Cracking Daemon";
            case 3 -> "Demonic Daemon";
            case 4 -> "Daemon Guardian";
            case 5 -> "Infernal Daemon";
            case 6 -> "Celestial Daemon";
            default -> "Daemon";
        };
    }

    private String getCreeperTierName(int tier) {
        return switch (tier) {
            case 1 -> "Small Creeper";
            case 2 -> "Creeper";
            case 3 -> "Charged Creeper";
            case 4 -> "Explosive Beast";
            case 5 -> "Creeper Lord";
            case 6 -> "Celestial Bomber";
            default -> "Creeper";
        };
    }

    private String getEndermanTierName(int tier) {
        return switch (tier) {
            case 1 -> "Weak Enderman";
            case 2 -> "Void Walker";
            case 3 -> "Ender Stalker";
            case 4 -> "Dimensional Hunter";
            case 5 -> "Ender Lord";
            case 6 -> "Celestial Ender Demon";
            default -> "Enderman";
        };
    }

    private String getBlazeTierName(int tier) {
        return switch (tier) {
            case 1 -> "Weak Blaze";
            case 2 -> "Fire Spirit";
            case 3 -> "Blaze Warrior";
            case 4 -> "Ancient Guard";
            case 5 -> "Infernal Blaze Lord";
            case 6 -> "Celestial Blaze";
            default -> "Blaze";
        };
    }

    private String getGhastTierName(int tier) {
        return switch (tier) {
            case 1 -> "Lesser Ghast";
            case 2 -> "Ghast";
            case 3 -> "Greater Ghast";
            case 4 -> "Ghast Lord";
            case 5 -> "Ancient Ghast";
            case 6 -> "Celestial Ghast";
            default -> "Ghast";
        };
    }

    private String getZombifiedPiglinTierName(int tier) {
        return switch (tier) {
            case 1 -> "Zombie Pigman";
            case 2 -> "Nether Zombie";
            case 3 -> "Piglin Warrior";
            case 4 -> "Nether Champion";
            case 5 -> "Piglin Lord";
            case 6 -> "Celestial Piglin";
            default -> "Zombified Piglin";
        };
    }

    private String getGuardianTierName(int tier) {
        return switch (tier) {
            case 1 -> "Ocean Guardian";
            case 2 -> "Sea Guardian";
            case 3 -> "Deep Guardian";
            case 4 -> "Guardian Lord";
            case 5 -> "Ancient Guardian";
            case 6 -> "Celestial Guardian";
            default -> "Guardian";
        };
    }

    private String getElderGuardianTierName(int tier) {
        return switch (tier) {
            case 1 -> "Young Elder Guardian";
            case 2 -> "Elder Guardian";
            case 3 -> "Ancient Elder Guardian";
            case 4 -> "Elder Lord";
            case 5 -> "Primordial Guardian";
            case 6 -> "Celestial Elder Guardian";
            default -> "Elder Guardian";
        };
    }

    private String getWardenTierName(int tier) {
        return switch (tier) {
            case 1 -> "Young Warden";
            case 2 -> "Warden";
            case 3 -> "Ancient Warden";
            case 4 -> "Warden Lord";
            case 5 -> "Primordial Warden";
            case 6 -> "Celestial Warden";
            default -> "Warden";
        };
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
     * Get the formatted name with appropriate color based on tier and elite status.
     */
    public String getFormattedName(int tier) {
        String name = getTierSpecificName(tier);
        ChatColor color = getTierColor(tier);

        return elite ? color.toString() + ChatColor.BOLD + name : color + name;
    }

    private ChatColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.GOLD;
            default -> ChatColor.WHITE;
        };
    }

    /**
     * Check if a tier is valid for this mob type.
     */
    public boolean isValidTier(int tier) {
        return tier >= minTier && tier <= maxTier;
    }

    /**
     * Clamp a tier to the valid range for this mob type.
     */
    public int clampTier(int tier) {
        return Math.max(minTier, Math.min(maxTier, tier));
    }

    // ================ ABILITY CHECKING ================

    /**
     * Check if this mob type has a specific ability.
     */
    public boolean hasAbility(MobAbility ability) {
        return abilities.contains(ability);
    }

    /**
     * Check if this mob type has any of the specified abilities.
     */
    public boolean hasAnyAbility(MobAbility... abilities) {
        return Arrays.stream(abilities).anyMatch(this::hasAbility);
    }

    /**
     * Check if this mob type has all of the specified abilities.
     */
    public boolean hasAllAbilities(MobAbility... abilities) {
        return Arrays.stream(abilities).allMatch(this::hasAbility);
    }

    /**
     * Get abilities by category.
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
     * Get the effective health multiplier for this mob type.
     */
    public double getHealthMultiplier() {
        return difficulty.getHealthMultiplier();
    }

    /**
     * Get the effective damage multiplier for this mob type.
     */
    public double getDamageMultiplier() {
        return difficulty.getDamageMultiplier();
    }

    // ================ DIAGNOSTIC METHODS ================

    /**
     * Get diagnostic information about the MobType system.
     */
    public static String getSystemDiagnostics() {
        StringBuilder info = new StringBuilder();
        info.append("MobType System Diagnostics:\n");
        info.append("Initialized: ").append(initialized).append("\n");
        info.append("Total Types: ").append(values().length).append("\n");
        info.append("Total Mappings: ").append(NORMALIZED_MAPPINGS.size()).append("\n");
        info.append("Elite Types Count: ").append(
                Arrays.stream(values()).filter(MobType::isElite).count()).append("\n");

        info.append("Critical Types:\n");
        String[] criticalTypes = {"skeleton", "zombie", "witherskeleton", "spider", "meridian", "pyrion"};
        for (String type : criticalTypes) {
            MobType found = getById(type);
            info.append("  ").append(type).append(": ").append(found != null ? "✓" : "✗").append("\n");
        }

        info.append("Elite Types:\n");
        for (MobType type : values()) {
            if (type.isElite()) {
                boolean mapped = NORMALIZED_MAPPINGS.containsKey(type.getId().toLowerCase().trim());
                info.append("  ").append(type.getId()).append(": ").append(mapped ? "✓" : "✗").append("\n");
            }
        }

        return info.toString();
    }

    /**
     * Force re-initialization (for debugging).
     */
    public static void forceReinitialize() {
        synchronized (initLock) {
            initialized = false;
            initializeStaticMappings();
        }
    }
}