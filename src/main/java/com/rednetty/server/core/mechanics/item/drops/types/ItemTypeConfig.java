package com.rednetty.server.core.mechanics.item.drops.types;

/**
 * Configuration class for item type-specific settings
 * Defines material suffixes and properties for different item types (weapons/armor)
 */
public class ItemTypeConfig {

    private final int itemType;
    private final String materialSuffix;
    private final boolean isWeapon;
    private final String displayName;
    private final String category;

    /**
     * Constructor for item type configuration
     *
     * @param itemType       The item type number (1-8)
     * @param materialSuffix The material suffix (e.g., "SWORD", "HELMET")
     * @param isWeapon       true if this is a weapon, false if armor
     * @param displayName    The display name for this item type
     * @param category       The category this item belongs to
     */
    public ItemTypeConfig(int itemType, String materialSuffix, boolean isWeapon, String displayName, String category) {
        this.itemType = itemType;
        this.materialSuffix = materialSuffix != null ? materialSuffix : "SWORD";
        this.isWeapon = isWeapon;
        this.displayName = displayName != null ? displayName : getDefaultDisplayName(itemType);
        this.category = category != null ? category : (isWeapon ? "Weapon" : "Armor");
    }

    /**
     * Constructor with automatic display name and category
     */
    public ItemTypeConfig(int itemType, String materialSuffix, boolean isWeapon) {
        this(itemType, materialSuffix, isWeapon, getDefaultDisplayName(itemType), isWeapon ? "Weapon" : "Armor");
    }

    // ===== GETTERS =====

    /**
     * Get the default display name for an item type
     */
    private static String getDefaultDisplayName(int itemType) {
        switch (itemType) {
            case 1:
                return "Staff";
            case 2:
                return "Spear";
            case 3:
                return "Sword";
            case 4:
                return "Axe";
            case 5:
                return "Helmet";
            case 6:
                return "Chestplate";
            case 7:
                return "Leggings";
            case 8:
                return "Boots";
            default:
                return "Unknown Item";
        }
    }

    /**
     * Get the default material suffix for an item type
     * Changed SPADE to SHOVEL for spears (item type 2)
     */
    public static String getDefaultMaterialSuffix(int itemType) {
        switch (itemType) {
            case 1:
                return "HOE";       // Staff
            case 2:
                return "SHOVEL";    // Spear -  from SPADE
            case 3:
                return "SWORD";     // Sword
            case 4:
                return "AXE";       // Axe
            case 5:
                return "HELMET";    // Helmet
            case 6:
                return "CHESTPLATE"; // Chestplate
            case 7:
                return "LEGGINGS";  // Leggings
            case 8:
                return "BOOTS";     // Boots
            default:
                return "SWORD";
        }
    }

    /**
     * Check if an item type is a weapon
     */
    public static boolean isWeaponType(int itemType) {
        return itemType >= 1 && itemType <= 4;
    }

    /**
     * Check if an item type is armor
     */
    public static boolean isArmorType(int itemType) {
        return itemType >= 5 && itemType <= 8;
    }

    /**
     * Create a default item type configuration for the given type
     *
     * @param itemType The item type number (1-8)
     * @return A default ItemTypeConfig for that type
     */
    public static ItemTypeConfig createDefault(int itemType) {
        String suffix = getDefaultMaterialSuffix(itemType);
        boolean weapon = isWeaponType(itemType);
        return new ItemTypeConfig(itemType, suffix, weapon);
    }

    /**
     * Create a custom item type configuration
     *
     * @param itemType       The item type number
     * @param materialSuffix The material suffix
     * @param isWeapon       Whether this is a weapon
     * @param displayName    The display name
     * @return A custom ItemTypeConfig
     */
    public static ItemTypeConfig custom(int itemType, String materialSuffix, boolean isWeapon, String displayName) {
        return new ItemTypeConfig(itemType, materialSuffix, isWeapon, displayName, isWeapon ? "Weapon" : "Armor");
    }

    // ===== UTILITY METHODS =====

    /**
     * Create a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create all default item type configurations
     *
     * @return An array of all default item type configs (indices 0-7 = types 1-8)
     */
    public static ItemTypeConfig[] createAllDefaults() {
        return new ItemTypeConfig[]{
                createDefault(1), // Staff
                createDefault(2), // Spear
                createDefault(3), // Sword
                createDefault(4), // Axe
                createDefault(5), // Helmet
                createDefault(6), // Chestplate
                createDefault(7), // Leggings
                createDefault(8)  // Boots
        };
    }

    /**
     * Get all weapon type configurations
     *
     * @return An array of weapon type configs
     */
    public static ItemTypeConfig[] getAllWeaponTypes() {
        return new ItemTypeConfig[]{
                createDefault(1), // Staff
                createDefault(2), // Spear
                createDefault(3), // Sword
                createDefault(4)  // Axe
        };
    }

    /**
     * Get all armor type configurations
     *
     * @return An array of armor type configs
     */
    public static ItemTypeConfig[] getAllArmorTypes() {
        return new ItemTypeConfig[]{
                createDefault(5), // Helmet
                createDefault(6), // Chestplate
                createDefault(7), // Leggings
                createDefault(8)  // Boots
        };
    }

    /**
     * Get the item type number
     */
    public int getItemType() {
        return itemType;
    }

    /**
     * Get the material suffix for this item type
     */
    public String getMaterialSuffix() {
        return materialSuffix;
    }

    /**
     * Check if this is a weapon type
     */
    public boolean isWeapon() {
        return isWeapon;
    }

    /**
     * Check if this is an armor type
     */
    public boolean isArmor() {
        return !isWeapon;
    }

    /**
     * Get the display name for this item type
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the category for this item type
     */
    public String getCategory() {
        return category;
    }

    /**
     * Check if this item type configuration is valid
     */
    public boolean isValid() {
        return itemType >= 1 && itemType <= 8 &&
                materialSuffix != null && !materialSuffix.trim().isEmpty() &&
                displayName != null && !displayName.trim().isEmpty();
    }

    /**
     * Get the slot this item type occupies
     */
    public String getSlot() {
        if (isWeapon) {
            return "Main Hand";
        } else {
            switch (itemType) {
                case 5:
                    return "Head";
                case 6:
                    return "Chest";
                case 7:
                    return "Legs";
                case 8:
                    return "Feet";
                default:
                    return "Unknown";
            }
        }
    }

    // ===== OBJECT METHODS =====

    /**
     * Get the weapon type (for weapons only)
     */
    public String getWeaponType() {
        if (!isWeapon) {
            return "N/A";
        }

        switch (itemType) {
            case 1:
                return "Staff";
            case 2:
                return "Spear";
            case 3:
                return "Sword";
            case 4:
                return "Axe";
            default:
                return "Unknown";
        }
    }

    /**
     * Get the armor piece type (for armor only)
     */
    public String getArmorPiece() {
        if (isWeapon) {
            return "N/A";
        }

        switch (itemType) {
            case 5:
                return "Helmet";
            case 6:
                return "Chestplate";
            case 7:
                return "Leggings";
            case 8:
                return "Boots";
            default:
                return "Unknown";
        }
    }

    /**
     * Check if this is a two-handed weapon
     */
    public boolean isTwoHanded() {
        return isWeapon && (itemType == 1 || itemType == 2 || itemType == 4); // Staff, Spear, Axe
    }

    // ===== STATIC UTILITY METHODS =====

    /**
     * Check if this is a one-handed weapon
     */
    public boolean isOneHanded() {
        return isWeapon && itemType == 3; // Sword
    }

    /**
     * Check if this is a heavy armor piece
     */
    public boolean isHeavyArmor() {
        return isArmor() && (itemType == 6 || itemType == 7); // Chestplate, Leggings
    }

    /**
     * Check if this is a light armor piece
     */
    public boolean isLightArmor() {
        return isArmor() && (itemType == 5 || itemType == 8); // Helmet, Boots
    }

    /**
     * Get the stat priority for this item type
     */
    public String getStatPriority() {
        if (isWeapon) {
            switch (itemType) {
                case 1:
                    return "Intelligence"; // Staff
                case 2:
                    return "Dexterity";    // Spear
                case 3:
                    return "Strength";     // Sword
                case 4:
                    return "Strength";     // Axe
                default:
                    return "Damage";
            }
        } else {
            return "Health";
        }
    }

    // ===== STATIC FACTORY METHODS =====

    /**
     * Get the damage type for weapons
     */
    public String getDamageType() {
        if (!isWeapon) {
            return "N/A";
        }

        switch (itemType) {
            case 1:
                return "Magical";  // Staff
            case 2:
                return "Piercing"; // Spear
            case 3:
                return "Slashing"; // Sword
            case 4:
                return "Chopping"; // Axe
            default:
                return "Physical";
        }
    }

    /**
     * Get the expected stat count for this item type
     */
    public int getExpectedStatCount() {
        if (isWeapon) {
            return 3; // Weapons typically have 3 stats
        } else {
            return isHeavyArmor() ? 3 : 2; // Heavy armor has more stats
        }
    }

    /**
     * Get a description of this item type
     */
    public String getDescription() {
        if (isWeapon) {
            return String.format("%s - A %s weapon that deals %s damage",
                    displayName, isTwoHanded() ? "two-handed" : "one-handed", getDamageType().toLowerCase());
        } else {
            return String.format("%s - %s armor piece worn on the %s",
                    displayName, isHeavyArmor() ? "Heavy" : "Light", getSlot().toLowerCase());
        }
    }

    /**
     * Get a string representation of this item type configuration
     */
    @Override
    public String toString() {
        return String.format("ItemTypeConfig{type=%d, suffix='%s', weapon=%s, name='%s', category='%s'}",
                itemType, materialSuffix, isWeapon, displayName, category);
    }

    /**
     * Check if two ItemTypeConfig objects are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ItemTypeConfig that = (ItemTypeConfig) obj;
        return itemType == that.itemType &&
                isWeapon == that.isWeapon &&
                materialSuffix.equals(that.materialSuffix) &&
                displayName.equals(that.displayName) &&
                category.equals(that.category);
    }

    /**
     * Get hash code for this object
     */
    @Override
    public int hashCode() {
        int result = itemType;
        result = 31 * result + materialSuffix.hashCode();
        result = 31 * result + (isWeapon ? 1 : 0);
        result = 31 * result + displayName.hashCode();
        result = 31 * result + category.hashCode();
        return result;
    }

    /**
     * Builder pattern for creating ItemTypeConfig
     */
    public static class Builder {
        private int itemType = 1;
        private String materialSuffix = "SWORD";
        private boolean isWeapon = true;
        private String displayName = "Sword";
        private String category = "Weapon";

        public Builder setItemType(int itemType) {
            this.itemType = itemType;
            return this;
        }

        public Builder setMaterialSuffix(String materialSuffix) {
            this.materialSuffix = materialSuffix;
            return this;
        }

        public Builder setWeapon(boolean weapon) {
            this.isWeapon = weapon;
            this.category = weapon ? "Weapon" : "Armor";
            return this;
        }

        public Builder setDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder setCategory(String category) {
            this.category = category;
            return this;
        }

        public ItemTypeConfig build() {
            return new ItemTypeConfig(itemType, materialSuffix, isWeapon, displayName, category);
        }
    }
}