package com.rednetty.server.utils.nbt;

import com.rednetty.server.YakRealms;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility class for accessing and modifying NBT data on items
 * using the Bukkit Persistent Data API.
 */
public class NBTAccessor {

    private final ItemStack itemStack;
    private ItemMeta itemMeta;
    private PersistentDataContainer container;
    private final JavaPlugin plugin;

    /**
     * Creates a new NBT accessor for the specified item
     *
     * @param itemStack The item to access NBT data on
     */
    public NBTAccessor(ItemStack itemStack) {
        this.plugin = YakRealms.getInstance();
        this.itemStack = itemStack;

        if (itemStack != null && itemStack.hasItemMeta()) {
            this.itemMeta = itemStack.getItemMeta();
            this.container = itemMeta.getPersistentDataContainer();
        } else {
            this.itemMeta = null;
            this.container = null;
        }
    }

    /**
     * Checks if the item has any NBT data
     *
     * @return true if the item has NBT data
     */
    public boolean hasTag() {
        return container != null && !container.isEmpty();
    }

    /**
     * Checks if the item has a specific NBT key
     *
     * @param key The key to check for
     * @return true if the key exists
     */
    public boolean hasKey(String key) {
        if (container == null) return false;
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        return container.has(namespacedKey, PersistentDataType.STRING) ||
                container.has(namespacedKey, PersistentDataType.INTEGER) ||
                container.has(namespacedKey, PersistentDataType.DOUBLE) ||
                container.has(namespacedKey, PersistentDataType.BYTE);
    }

    /**
     * Ensures the item has meta data that can be modified
     *
     * @return This NBTAccessor instance for method chaining
     */
    public NBTAccessor check() {
        if (itemStack != null && (itemMeta == null || container == null)) {
            this.itemMeta = itemStack.hasItemMeta() ?
                    itemStack.getItemMeta() : itemStack.getItemMeta();
            if (itemMeta != null) {
                this.container = itemMeta.getPersistentDataContainer();
            }
        }
        return this;
    }

    /**
     * Gets a string value from the item's NBT data
     *
     * @param key The key to get
     * @return The string value, or null if not found
     */
    public String getString(String key) {
        if (container == null) return null;
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        return container.has(namespacedKey, PersistentDataType.STRING) ?
                container.get(namespacedKey, PersistentDataType.STRING) : null;
    }

    /**
     * Gets an integer value from the item's NBT data
     *
     * @param key The key to get
     * @return The integer value, or 0 if not found
     */
    public int getInt(String key) {
        if (container == null) return 0;
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        return container.has(namespacedKey, PersistentDataType.INTEGER) ?
                container.get(namespacedKey, PersistentDataType.INTEGER) : 0;
    }

    /**
     * Gets an integer value from the item's NBT data
     * Alias of getInt for compatibility with other code
     *
     * @param key The key to get
     * @return The integer value, or 0 if not found
     */
    public int getInteger(String key) {
        return getInt(key);
    }

    /**
     * Gets a double value from the item's NBT data
     *
     * @param key The key to get
     * @return The double value, or 0.0 if not found
     */
    public double getDouble(String key) {
        if (container == null) return 0.0;
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        return container.has(namespacedKey, PersistentDataType.DOUBLE) ?
                container.get(namespacedKey, PersistentDataType.DOUBLE) : 0.0;
    }

    /**
     * Gets a boolean value from the item's NBT data
     *
     * @param key The key to get
     * @return The boolean value, or false if not found
     */
    public boolean getBoolean(String key) {
        if (container == null) return false;
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        return container.has(namespacedKey, PersistentDataType.BYTE) &&
                container.get(namespacedKey, PersistentDataType.BYTE) == 1;
    }

    /**
     * Sets a string value in the item's NBT data
     *
     * @param key   The key to set
     * @param value The string value to set
     * @return This NBTAccessor instance for method chaining
     */
    public NBTAccessor setString(String key, String value) {
        if (itemMeta == null) {
            check();
        }
        if (itemMeta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            itemMeta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, value);
        }
        return this;
    }

    /**
     * Sets an integer value in the item's NBT data
     *
     * @param key   The key to set
     * @param value The integer value to set
     * @return This NBTAccessor instance for method chaining
     */
    public NBTAccessor setInt(String key, int value) {
        if (itemMeta == null) {
            check();
        }
        if (itemMeta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            itemMeta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.INTEGER, value);
        }
        return this;
    }

    /**
     * Sets a double value in the item's NBT data
     *
     * @param key   The key to set
     * @param value The double value to set
     * @return This NBTAccessor instance for method chaining
     */
    public NBTAccessor setDouble(String key, double value) {
        if (itemMeta == null) {
            check();
        }
        if (itemMeta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            itemMeta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.DOUBLE, value);
        }
        return this;
    }

    /**
     * Sets a boolean value in the item's NBT data
     *
     * @param key   The key to set
     * @param value The boolean value to set
     * @return This NBTAccessor instance for method chaining
     */
    public NBTAccessor setBoolean(String key, boolean value) {
        if (itemMeta == null) {
            check();
        }
        if (itemMeta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            itemMeta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.BYTE, value ? (byte) 1 : (byte) 0);
        }
        return this;
    }

    /**
     * Removes a key from the item's NBT data
     *
     * @param key The key to remove
     * @return This NBTAccessor instance for method chaining
     */
    public NBTAccessor remove(String key) {
        if (itemMeta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            itemMeta.getPersistentDataContainer().remove(namespacedKey);
        }
        return this;
    }

    /**
     * Applies the changes to the item and returns the updated item
     *
     * @return The updated item
     */
    public ItemStack update() {
        if (itemStack != null && itemMeta != null) {
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }
}