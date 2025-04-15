package com.rednetty.server.mechanics.mobs.core;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Enum representing different mob types with their properties
 */
public enum MobType {
    // Regular mobs
    SKELETON("skeleton", EntityType.SKELETON, false, 1, 6, "Skeleton"),
    ZOMBIE("zombie", EntityType.ZOMBIE, false, 1, 6, "Zombie"),
    SILVERFISH("silverfish", EntityType.SILVERFISH, false, 1, 6, "Silverfish"),
    MAGMA_CUBE("magmacube", EntityType.MAGMA_CUBE, false, 1, 6, "Magma Cube"),
    SPIDER("spider", EntityType.SPIDER, false, 1, 6, "Spider"),
    CAVE_SPIDER("cavespider", EntityType.CAVE_SPIDER, false, 1, 6, "Cave Spider"),
    IMP("imp", EntityType.ZOMBIFIED_PIGLIN, false, 1, 6, "Imp"),
    WITHER_SKELETON("witherskeleton", EntityType.WITHER_SKELETON, false, 1, 6, "Wither Skeleton"),
    DAEMON("daemon", EntityType.ZOMBIFIED_PIGLIN, false, 1, 6, "Daemon"),
    GOLEM("golem", EntityType.IRON_GOLEM, false, 1, 6, "Golem"),
    GIANT("giant", EntityType.GIANT, false, 1, 6, "Giant"),
    TURKEY("turkey", EntityType.CHICKEN, false, 1, 6, "Turkey"),

    // Elite mobs - Tier 1
    PLAGUEBEARER("plaguebearer", EntityType.ZOMBIE, true, 1, 1, "Plaguebearer"),
    MITSUKI("mitsuki", EntityType.ZOMBIE, true, 1, 1, "Mitsuki The Dominator"),

    // Elite mobs - Tier 2
    BONEREAVER("bonereaver", EntityType.WITHER_SKELETON, true, 2, 2, "Bonereaver"),
    COPJAK("copjak", EntityType.ZOMBIE, true, 2, 2, "Cop'jak"),
    RISK_ELITE("risk_elite", EntityType.ZOMBIE, true, 2, 2, "Riskan The Rotten"),
    ORC_KING("orcking", EntityType.ZOMBIE, true, 2, 2, "The Orc King"),

    // Elite mobs - Tier 3
    SOULREAPER("soulreaper", EntityType.WITHER_SKELETON, true, 3, 3, "Soulreaper"),
    KING_OF_GREED("kingofgreed", EntityType.WITHER_SKELETON, true, 3, 3, "The King Of Greed"),
    SKELETON_KING("skeletonking", EntityType.WITHER_SKELETON, true, 3, 3, "The Skeleton King"),
    SPIDER_QUEEN("spiderqueen", EntityType.SPIDER, true, 3, 3, "The Spider Queen"),
    IMPA("impa", EntityType.WITHER_SKELETON, true, 3, 3, "Impa The Impaler"),

    // Elite mobs - Tier 4
    DOOMHERALD("doomherald", EntityType.ZOMBIE, true, 4, 4, "Doomherald"),
    BLOOD_BUTCHER("bloodbutcher", EntityType.ZOMBIE, true, 4, 4, "The Blood Butcher"),
    BLAYSHAN("blayshan", EntityType.ZOMBIE, true, 4, 4, "Blayshan The Naga"),
    WATCHMASTER("watchmaster", EntityType.WITHER_SKELETON, true, 4, 4, "The Watchmaster"),
    SPECTRAL_KNIGHT("spectralKnight", EntityType.ZOMBIFIED_PIGLIN, true, 4, 4, "The Evil Spectral Overlord"),
    DURANOR("duranor", EntityType.WITHER_SKELETON, true, 4, 4, "Duranor The Cruel"),

    // Elite mobs - Tier 5
    NETHERMANCER("nethermancer", EntityType.WITHER_SKELETON, true, 5, 5, "Nethermancer"),
    JAYDEN("jayden", EntityType.WITHER_SKELETON, true, 5, 5, "King Jayden"),
    KILATAN("kilatan", EntityType.WITHER_SKELETON, true, 5, 5, "Daemon Lord Kilatan"),
    FROST_KING("frostking", EntityType.WITHER_SKELETON, true, 5, 5, "Frost Walker"),
    WARDEN("warden", EntityType.WITHER_SKELETON, true, 5, 5, "The Warden"),
    WEAK_SKELETON("weakskeletonentity", EntityType.SKELETON, true, 5, 5, "Skeletal Keeper"),
    BOSS_SKELETON("bossSkeletonDungeon", EntityType.WITHER_SKELETON, true, 5, 5, "The Restless Skeleton Deathlord"),
    KRAMPUS("krampus", EntityType.WITHER_SKELETON, true, 5, 5, "Krampus The Warrior"),
    GRAND_WIZARD("grandwizard", EntityType.ZOMBIE, true, 5, 5, "Grand Wizard of Psilocyland"),

    // Elite mobs - Tier 6
    VOIDLORD("voidlord", EntityType.WITHER_SKELETON, true, 6, 6, "Voidlord"),

    // World bosses
    FROZEN_ELITE("frozenelite", EntityType.WITHER_SKELETON, true, 5, 6, "Frost The Exiled King", true),
    FROZEN_BOSS("frozenboss", EntityType.WITHER_SKELETON, true, 5, 6, "The Conqueror of The North", true),
    FROZEN_GOLEM("frozengolem", EntityType.IRON_GOLEM, true, 5, 6, "Crypt Guardian", true),
    FROSTWING("frostwing", EntityType.WITHER_SKELETON, true, 5, 6, "Frost-Wing The Frozen Titan", true),
    CHRONOS("chronos", EntityType.WITHER_SKELETON, true, 5, 6, "Chronos, Lord of Time", true),

    // Special types
    PRISONER("prisoner", EntityType.ZOMBIE, false, 3, 5, "Prisoner"),
    SKELLY_GUARDIAN("skellyDSkeletonGuardian", EntityType.SKELETON, true, 4, 5, "Skeletal Guardian"),
    SPECTRAL_GUARD("spectralguard", EntityType.ZOMBIFIED_PIGLIN, true, 1, 1, "The Evil Spectral's Impish Guard");

    private final String id;
    private final EntityType entityType;
    private final boolean elite;
    private final int minTier;
    private final int maxTier;
    private final String defaultName;
    private final boolean worldBoss;

    // Map for quick lookups by ID
    private static final Map<String, MobType> BY_ID = new HashMap<>();

    static {
        for (MobType type : values()) {
            BY_ID.put(type.getId().toLowerCase(), type);
        }
    }

    MobType(String id, EntityType entityType, boolean elite, int minTier, int maxTier, String defaultName) {
        this(id, entityType, elite, minTier, maxTier, defaultName, false);
    }

    MobType(String id, EntityType entityType, boolean elite, int minTier, int maxTier, String defaultName, boolean worldBoss) {
        this.id = id;
        this.entityType = entityType;
        this.elite = elite;
        this.minTier = minTier;
        this.maxTier = maxTier;
        this.defaultName = defaultName;
        this.worldBoss = worldBoss;
    }

    /**
     * Get MobType by ID string
     *
     * @param id The type ID
     * @return MobType or null if not found
     */
    public static MobType getById(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase());
    }

    /**
     * Check if a type ID is valid
     *
     * @param id The type ID to check
     * @return true if valid
     */
    public static boolean isValidType(String id) {
        return BY_ID.containsKey(id.toLowerCase());
    }

    /**
     * Get the formatted name with appropriate color based on tier
     *
     * @param tier Mob tier
     * @return Colored name string
     */
    public String getFormattedName(int tier) {
        ChatColor color;

        if (elite) {
            color = ChatColor.LIGHT_PURPLE;
            return color + "" + ChatColor.BOLD + defaultName;
        }

        // Apply tier-based color for regular mobs
        switch (tier) {
            case 1:
                color = ChatColor.WHITE;
                break;
            case 2:
                color = ChatColor.GREEN;
                break;
            case 3:
                color = ChatColor.AQUA;
                break;
            case 4:
                color = ChatColor.LIGHT_PURPLE;
                break;
            case 5:
                color = ChatColor.YELLOW;
                break;
            case 6:
                color = ChatColor.BLUE;
                break;
            default:
                color = ChatColor.WHITE;
                break;
        }

        return color + defaultName;
    }

    /**
     * Get tier-specific name for standard mobs
     * Matches the legacy tier-specific naming from Spawners.java
     *
     * @param tier Mob tier
     * @return Name customized for tier
     */
    public String getTierSpecificName(int tier) {
        // For named elites, always use default name
        if (elite && !id.equals("skeleton") && !id.equals("zombie") &&
                !id.equals("witherskeleton") && !id.equals("golem") &&
                !id.equals("imp") && !id.equals("daemon") &&
                !id.equals("magmacube") && !id.equals("silverfish")) {
            return defaultName;
        }

        // For regular mobs, return tier-specific name
        switch (id) {
            case "skeleton":
                switch (tier) {
                    case 1:
                        return "Broken Skeleton";
                    case 2:
                        return "Wandering Cracking Skeleton";
                    case 3:
                        return "Demonic Skeleton";
                    case 4:
                        return "Skeleton Guardian";
                    case 5:
                        return "Infernal Skeleton";
                    case 6:
                        return "Frozen Skeleton";
                    default:
                        return "Skeleton";
                }
            case "witherskeleton":
                switch (tier) {
                    case 1:
                        return "Broken Chaos Skeleton";
                    case 2:
                        return "Wandering Cracking Chaos Skeleton";
                    case 3:
                        return "Demonic Chaos Skeleton";
                    case 4:
                        return "Skeleton Chaos Guardian";
                    case 5:
                        return "Infernal Chaos Skeleton";
                    case 6:
                        return "Frozen Skeletal Minion";
                    default:
                        return "Chaos Skeleton";
                }
            case "zombie":
                switch (tier) {
                    case 1:
                        return "Rotting Zombie";
                    case 2:
                        return "Savaged Zombie";
                    case 3:
                        return "Greater Zombie";
                    case 4:
                        return "Demonic Zombie";
                    case 5:
                        return "Infernal Zombie";
                    case 6:
                        return "Frozen Zombie";
                    default:
                        return "Zombie";
                }
            case "magmacube":
                switch (tier) {
                    case 1:
                        return "Weak Magma Cube";
                    case 2:
                        return "Bubbling Magma Cube";
                    case 3:
                        return "Unstable Magma Cube";
                    case 4:
                        return "Boiling Magma Cube";
                    case 5:
                        return "Unstoppable Magma Cube";
                    case 6:
                        return "Ice Cube";
                    default:
                        return "Magma Cube";
                }
            case "silverfish":
                switch (tier) {
                    case 1:
                        return "Weak SilverFish";
                    case 2:
                        return "Pointy SilverFish";
                    case 3:
                        return "Unstable SilverFish";
                    case 4:
                        return "Mean SilverFish";
                    case 5:
                        return "Rude SilverFish";
                    case 6:
                        return "Ice-Cold SilverFish";
                    default:
                        return "SilverFish";
                }
            case "spider":
            case "cavespider":
                String prefix;
                switch (tier) {
                    case 1:
                        prefix = "Harmless";
                        break;
                    case 2:
                        prefix = "Wild";
                        break;
                    case 3:
                        prefix = "Fierce";
                        break;
                    case 4:
                        prefix = "Dangerous";
                        break;
                    case 5:
                        prefix = "Lethal";
                        break;
                    case 6:
                        prefix = "Devastating";
                        break;
                    default:
                        prefix = "";
                        break;
                }

                if (id.equals("cavespider")) {
                    return prefix + " Cave Spider";
                } else {
                    return prefix + " Spider";
                }
            case "golem":
                switch (tier) {
                    case 1:
                        return "Broken Golem";
                    case 2:
                        return "Rusty Golem";
                    case 3:
                        return "Restored Golem";
                    case 4:
                        return "Mountain Golem";
                    case 5:
                        return "Powerful Golem";
                    case 6:
                        return "Devastating Golem";
                    default:
                        return "Golem";
                }
            case "imp":
                switch (tier) {
                    case 1:
                        return "Ugly Imp";
                    case 2:
                        return "Angry Imp";
                    case 3:
                        return "Warrior Imp";
                    case 4:
                        return "Armoured Imp";
                    case 5:
                        return "Infernal Imp";
                    case 6:
                        return "Arctic Imp";
                    default:
                        return "Imp";
                }
            case "daemon":
                switch (tier) {
                    case 1:
                        return "Broken Daemon";
                    case 2:
                        return "Wandering Cracking Daemon";
                    case 3:
                        return "Demonic Daemon";
                    case 4:
                        return "Daemon Guardian";
                    case 5:
                        return "Infernal Daemon";
                    case 6:
                        return "Chilled Daemon";
                    default:
                        return "Daemon";
                }
            case "weakskeletonentity":
                int id = new Random().nextInt(3);
                switch (id) {
                    case 0:
                        return "Infernal Skeletal Keeper";
                    case 1:
                        return "Skeletal Soul Keeper";
                    case 2:
                        return "Skeletal Soul Harvester";
                    default:
                        return "Infernal Skeletal Soul Harvester";
                }
            case "prisoner":
                int prisonerId = new Random().nextInt(2);
                switch (prisonerId) {
                    case 0:
                        return "Tortured Prisoner";
                    case 1:
                        return "Corrupted Prison Guard";
                    default:
                        return "Tortmented Guard";
                }
            case "skellyDSkeletonGuardian":
                int guardianId = new Random().nextInt(2);
                switch (guardianId) {
                    case 0:
                        return "Skeletal Guardian Deadlord";
                    case 1:
                        return "Skeletal Guardian Overlord";
                    default:
                        return "Restless Skeletal Guardian";
                }
            default:
                return defaultName;
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public boolean isElite() {
        return elite;
    }

    public int getMinTier() {
        return minTier;
    }

    public int getMaxTier() {
        return maxTier;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public boolean isWorldBoss() {
        return worldBoss;
    }
}