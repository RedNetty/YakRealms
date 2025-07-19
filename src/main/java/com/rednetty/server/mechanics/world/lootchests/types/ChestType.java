package com.rednetty.server.mechanics.world.lootchests.types;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Represents the different types of loot chests
 * Each type has different behaviors, appearance, and loot generation rules
 * FIXED: Method naming typo corrected
 */
public enum ChestType {
    /**
     * Standard loot chest - most common type
     */
    NORMAL("Normal", ChatColor.WHITE, Material.CHEST,
            "Standard loot chest", false, true),

    /**
     * Care package - special delivery with high-tier loot
     */
    CARE_PACKAGE("Care Package", ChatColor.GOLD, Material.ENDER_CHEST,
            "High-tier care package dropped from the sky", true, false),

    /**
     * Special chest - limited time with enhanced loot
     */
    SPECIAL("Special", ChatColor.LIGHT_PURPLE, Material.BARREL,
            "Limited-time chest with enhanced loot", true, true);

    private final String displayName;
    private final ChatColor color;
    private final Material blockMaterial;
    private final String description;
    private final boolean isTemporary;
    private final boolean canBeBroken; // FIXED: Renamed from canBeBreaking

    /**
     * Creates a chest type with the specified properties
     */
    ChestType(String displayName, ChatColor color, Material blockMaterial,
              String description, boolean isTemporary, boolean canBeBroken) {
        this.displayName = displayName;
        this.color = color;
        this.blockMaterial = blockMaterial;
        this.description = description;
        this.isTemporary = isTemporary;
        this.canBeBroken = canBeBroken; // FIXED: Updated field name
    }

    /**
     * Gets the display name of this type
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the color associated with this type
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Gets the block material used for this chest type
     */
    public Material getBlockMaterial() {
        return blockMaterial;
    }

    /**
     * Gets the description of this type
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this chest type is temporary (expires after time)
     */
    public boolean isTemporary() {
        return isTemporary;
    }

    /**
     * Checks if this chest type can be broken by players
     * FIXED: Method name corrected from canBeBreaking() to canBeBroken()
     */
    public boolean canBeBroken() {
        return canBeBroken;
    }

    /**
     * Gets a type by its name (case-insensitive)
     * @param name The type name
     * @return The corresponding type, or null if not found
     */
    public static ChestType fromName(String name) {
        if (name == null) return null;

        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try matching by display name
            for (ChestType type : values()) {
                if (type.displayName.equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Gets a type by its block material
     * @param material The block material
     * @return The corresponding type, or NORMAL if not found
     */
    public static ChestType fromMaterial(Material material) {
        if (material == null) return NORMAL;

        for (ChestType type : values()) {
            if (type.blockMaterial == material) {
                return type;
            }
        }
        return NORMAL; // Default fallback
    }

    /**
     * Gets the inventory size for this chest type
     */
    public int getInventorySize() {
        return switch (this) {
            case CARE_PACKAGE -> 9;  // Single row
            case SPECIAL -> 27;      // Full chest
            case NORMAL -> 27;       // Full chest
        };
    }

    /**
     * Gets the base loot multiplier for this chest type
     */
    public double getLootMultiplier() {
        return switch (this) {
            case NORMAL -> 1.0;      // Standard loot
            case CARE_PACKAGE -> 2.0; // Double loot
            case SPECIAL -> 1.5;     // 50% more loot
        };
    }

    /**
     * Gets the expiration time in milliseconds (0 = never expires)
     */
    public long getExpirationTime() {
        return switch (this) {
            case NORMAL -> 0;           // Never expires
            case CARE_PACKAGE -> 600000; // 10 minutes
            case SPECIAL -> 300000;     // 5 minutes
        };
    }

    /**
     * Gets the announcement radius for this chest type
     */
    public int getAnnouncementRadius() {
        return switch (this) {
            case NORMAL -> 0;        // No announcement
            case CARE_PACKAGE -> 1000; // Server-wide announcement
            case SPECIAL -> 100;     // Local area announcement
        };
    }

    /**
     * Checks if this chest type should be announced when spawned
     */
    public boolean shouldAnnounce() {
        return getAnnouncementRadius() > 0;
    }

    /**
     * Checks if this chest type should have enhanced particles
     */
    public boolean hasEnhancedParticles() {
        return this == CARE_PACKAGE || this == SPECIAL;
    }

    /**
     * Checks if this chest type should have enhanced sounds
     */
    public boolean hasEnhancedSounds() {
        return this == CARE_PACKAGE || this == SPECIAL;
    }

    /**
     * Gets the rarity of this chest type (1.0 = common, 0.1 = very rare)
     */
    public double getRarity() {
        return switch (this) {
            case NORMAL -> 1.0;      // Common
            case SPECIAL -> 0.3;     // Uncommon
            case CARE_PACKAGE -> 0.1; // Rare
        };
    }

    /**
     * Gets the minimum tier recommended for this chest type
     */
    public ChestTier getMinimumRecommendedTier() {
        return switch (this) {
            case NORMAL -> ChestTier.WOODEN;
            case SPECIAL -> ChestTier.IRON;
            case CARE_PACKAGE -> ChestTier.LEGENDARY;
        };
    }

    /**
     * Gets the maximum tier recommended for this chest type
     */
    public ChestTier getMaximumRecommendedTier() {
        return switch (this) {
            case NORMAL -> ChestTier.NETHER_FORGED;
            case SPECIAL -> ChestTier.NETHER_FORGED;
            case CARE_PACKAGE -> ChestTier.NETHER_FORGED;
        };
    }

    /**
     * Checks if a tier is appropriate for this chest type
     */
    public boolean isAppropriateForTier(ChestTier tier) {
        if (tier == null) return false;
        return tier.getLevel() >= getMinimumRecommendedTier().getLevel() &&
                tier.getLevel() <= getMaximumRecommendedTier().getLevel();
    }

    /**
     * Gets the spawn weight for random generation (higher = more common)
     */
    public int getSpawnWeight() {
        return switch (this) {
            case NORMAL -> 100;      // Very common
            case SPECIAL -> 10;      // Uncommon
            case CARE_PACKAGE -> 1;  // Very rare
        };
    }

    /**
     * Gets special properties for this chest type
     */
    public String[] getSpecialProperties() {
        return switch (this) {
            case NORMAL -> new String[]{"Standard respawn", "No expiration"};
            case CARE_PACKAGE -> new String[]{
                    "Server announcement", "Cannot be broken",
                    "Double loot", "Expires in 10 minutes"
            };
            case SPECIAL -> new String[]{
                    "Enhanced loot", "Limited time",
                    "Enhanced effects", "Expires in 5 minutes"
            };
        };
    }

    /**
     * Gets the priority for display sorting (lower = higher priority)
     */
    public int getDisplayPriority() {
        return switch (this) {
            case CARE_PACKAGE -> 1; // Highest priority
            case SPECIAL -> 2;
            case NORMAL -> 3;       // Lowest priority
        };
    }

    /**
     * Returns a colored string representation
     */
    @Override
    public String toString() {
        return color + displayName + ChatColor.RESET;
    }

    /**
     * Returns a plain string representation without color codes
     */
    public String toPlainString() {
        return displayName;
    }

    /**
     * Returns a detailed string with description
     */
    public String toDetailedString() {
        return color + displayName + ChatColor.GRAY + " - " + description + ChatColor.RESET;
    }

    /**
     * Returns a string with type and properties
     */
    public String toPropertiesString() {
        StringBuilder sb = new StringBuilder();
        sb.append(color).append(displayName).append(ChatColor.RESET).append("\n");
        sb.append(ChatColor.GRAY).append("Material: ").append(blockMaterial.name()).append("\n");
        sb.append("Inventory Size: ").append(getInventorySize()).append("\n");
        sb.append("Loot Multiplier: ").append(getLootMultiplier()).append("x\n");
        sb.append("Temporary: ").append(isTemporary ? "Yes" : "No").append("\n");
        sb.append("Can Break: ").append(canBeBroken ? "Yes" : "No").append("\n"); // FIXED: Updated method call

        if (isTemporary) {
            sb.append("Expires After: ").append(getExpirationTime() / 1000).append(" seconds\n");
        }

        if (shouldAnnounce()) {
            sb.append("Announcement Radius: ").append(getAnnouncementRadius()).append(" blocks\n");
        }

        return sb.toString();
    }

    /**
     * Gets a random chest type weighted by spawn weight
     */
    public static ChestType getRandomWeighted() {
        int totalWeight = 0;
        for (ChestType type : values()) {
            totalWeight += type.getSpawnWeight();
        }

        int random = (int) (Math.random() * totalWeight);
        int currentWeight = 0;

        for (ChestType type : values()) {
            currentWeight += type.getSpawnWeight();
            if (random < currentWeight) {
                return type;
            }
        }

        return NORMAL; // Fallback
    }

    /**
     * Gets a random chest type with equal probability
     */
    public static ChestType getRandomUniform() {
        ChestType[] types = values();
        return types[(int) (Math.random() * types.length)];
    }

    /**
     * Gets all temporary chest types
     */
    public static ChestType[] getTemporaryTypes() {
        return java.util.Arrays.stream(values())
                .filter(ChestType::isTemporary)
                .toArray(ChestType[]::new);
    }

    /**
     * Gets all permanent chest types
     */
    public static ChestType[] getPermanentTypes() {
        return java.util.Arrays.stream(values())
                .filter(type -> !type.isTemporary())
                .toArray(ChestType[]::new);
    }

    /**
     * Gets all chest types that can be broken
     * FIXED: Updated method call
     */
    public static ChestType[] getBreakableTypes() {
        return java.util.Arrays.stream(values())
                .filter(ChestType::canBeBroken)
                .toArray(ChestType[]::new);
    }
}