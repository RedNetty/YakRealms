package com.rednetty.server.mechanics.item.drops.types;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the details of a specific item for an elite mob drop
 * Contains name, material, lore, and stat overrides
 */
public class ItemDetails {

    private final String name;
    private final Material material;
    private final String lore;

    // Stat overrides that apply only to this specific item
    private final Map<String, StatRange> statOverrides = new HashMap<>();

    /**
     * Constructor for item details
     *
     * @param name     The display name of the item
     * @param material The Minecraft material for the item
     * @param lore     The lore text for the item
     */
    public ItemDetails(String name, Material material, String lore) {
        this.name = name != null ? name : "Elite Item";
        this.material = material != null ? material : Material.STONE;
        this.lore = lore != null ? lore : "An elite item";
    }

    /**
     * Constructor with just name and lore (material will be determined automatically)
     */
    public ItemDetails(String name, String lore) {
        this(name, null, lore);
    }

    /**
     * Constructor with minimal information
     */
    public ItemDetails(String name) {
        this(name, null, "An elite item");
    }

    // ===== GETTERS =====

    /**
     * Create a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a simple ItemDetails with just a name
     */
    public static ItemDetails simple(String name) {
        return new ItemDetails(name);
    }

    /**
     * Create an ItemDetails with name and lore
     */
    public static ItemDetails withLore(String name, String lore) {
        return new ItemDetails(name, lore);
    }

    // ===== STAT OVERRIDE MANAGEMENT =====

    /**
     * Create an ItemDetails with name, material, and lore
     */
    public static ItemDetails complete(String name, Material material, String lore) {
        return new ItemDetails(name, material, lore);
    }

    /**
     * Get the display name of the item
     */
    public String getName() {
        return name;
    }

    /**
     * Get the material for the item
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Get the lore text for the item
     */
    public String getLore() {
        return lore;
    }

    /**
     * Add a stat override for this specific item
     * This will override any stat ranges from the parent elite configuration
     *
     * @param statName The name of the stat to override
     * @param range    The stat range to use instead of the default
     */
    public void addStatOverride(String statName, StatRange range) {
        if (statName != null && range != null) {
            statOverrides.put(statName, range);
        }
    }

    /**
     * Get a stat override for this item
     *
     * @param statName The name of the stat
     * @return The stat range override, or null if no override exists
     */
    public StatRange getStatOverride(String statName) {
        return statOverrides.get(statName);
    }

    /**
     * Check if this item has a stat override for the given stat
     *
     * @param statName The name of the stat to check
     * @return true if an override exists
     */
    public boolean hasStatOverride(String statName) {
        return statOverrides.containsKey(statName);
    }

    // ===== UTILITY METHODS =====

    /**
     * Get all stat overrides for this item
     *
     * @return A copy of the stat overrides map
     */
    public Map<String, StatRange> getAllStatOverrides() {
        return new HashMap<>(statOverrides);
    }

    /**
     * Remove a stat override
     *
     * @param statName The name of the stat override to remove
     * @return The removed stat range, or null if it didn't exist
     */
    public StatRange removeStatOverride(String statName) {
        return statOverrides.remove(statName);
    }

    /**
     * Clear all stat overrides
     */
    public void clearStatOverrides() {
        statOverrides.clear();
    }

    /**
     * Get the number of stat overrides
     */
    public int getStatOverrideCount() {
        return statOverrides.size();
    }

    /**
     * Check if this item details object is valid
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty();
    }

    /**
     * Get a string representation of this item details
     */
    @Override
    public String toString() {
        return String.format("ItemDetails{name='%s', material=%s, lore='%s', overrides=%d}",
                name, material, lore, statOverrides.size());
    }

    /**
     * Check if two ItemDetails objects are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ItemDetails that = (ItemDetails) obj;
        return name.equals(that.name) &&
                material == that.material &&
                lore.equals(that.lore);
    }

    /**
     * Get hash code for this object
     */
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (material != null ? material.hashCode() : 0);
        result = 31 * result + lore.hashCode();
        return result;
    }

    /**
     * Create a copy of this ItemDetails object
     */
    public ItemDetails copy() {
        ItemDetails copy = new ItemDetails(name, material, lore);
        copy.statOverrides.putAll(this.statOverrides);
        return copy;
    }

    /**
     * Get debug information about this item
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("ItemDetails Debug:\n");
        info.append("  Name: ").append(name).append("\n");
        info.append("  Material: ").append(material).append("\n");
        info.append("  Lore: ").append(lore).append("\n");

        if (!statOverrides.isEmpty()) {
            info.append("  Stat Overrides:\n");
            statOverrides.forEach((stat, range) ->
                    info.append("    ").append(stat).append(": ").append(range).append("\n")
            );
        } else {
            info.append("  No stat overrides\n");
        }

        return info.toString();
    }

    /**
     * Builder pattern for creating ItemDetails
     */
    public static class Builder {
        private final Map<String, StatRange> statOverrides = new HashMap<>();
        private String name;
        private Material material;
        private String lore;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setMaterial(Material material) {
            this.material = material;
            return this;
        }

        public Builder setLore(String lore) {
            this.lore = lore;
            return this;
        }

        public Builder addStatOverride(String statName, StatRange range) {
            this.statOverrides.put(statName, range);
            return this;
        }

        public Builder addStatOverride(String statName, int min, int max) {
            this.statOverrides.put(statName, new StatRange(min, max));
            return this;
        }

        public ItemDetails build() {
            ItemDetails item = new ItemDetails(name, material, lore);
            item.statOverrides.putAll(this.statOverrides);
            return item;
        }
    }
}