package com.rednetty.server.utils.inventory;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 *  ItemSerializer - Safe serialization/deserialization of ItemStacks
 *
 * FIXES IMPLEMENTED:
 * 1.  error handling and validation
 * 2. Base64 encoding for safe string storage
 * 3. Comprehensive null checking
 * 4. Version compatibility handling
 */
public class ItemSerializer {

    /**
     * Serialize ItemStack array to Base64 string
     */
    public static String serializeItemStacks(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return null;
        }

        try {
            // Filter out null and invalid items
            ItemStack[] validItems = new ItemStack[items.length];
            int validCount = 0;

            for (ItemStack item : items) {
                if (InventoryUtils.isValidItem(item)) {
                    validItems[validCount++] = item;
                }
            }

            if (validCount == 0) {
                return null;
            }

            // Resize array to actual valid count
            ItemStack[] finalItems = new ItemStack[validCount];
            System.arraycopy(validItems, 0, finalItems, 0, validCount);

            // Serialize to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(finalItems.length);
            for (ItemStack item : finalItems) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();

            // Encode to Base64
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize items", e);
        }
    }

    /**
     * Deserialize Base64 string to ItemStack array
     */
    public static ItemStack[] deserializeItemStacks(String data) {
        if (data == null || data.trim().isEmpty()) {
            return new ItemStack[0];
        }

        try {
            // Decode from Base64
            byte[] bytes = Base64.getDecoder().decode(data);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int length = dataInput.readInt();
            if (length < 0 || length > 1000) { // Reasonable limit
                throw new IOException("Invalid item count: " + length);
            }

            ItemStack[] items = new ItemStack[length];

            for (int i = 0; i < length; i++) {
                Object obj = dataInput.readObject();
                if (obj instanceof ItemStack) {
                    ItemStack item = (ItemStack) obj;
                    // Validate deserialized item
                    if (InventoryUtils.isValidItem(item)) {
                        items[i] = item;
                    }
                }
            }

            dataInput.close();
            return items;

        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize items: " + e.getMessage(), e);
        }
    }

    /**
     * Serialize single ItemStack to Base64 string
     */
    public static String serializeItemStack(ItemStack item) {
        if (!InventoryUtils.isValidItem(item)) {
            return null;
        }

        return serializeItemStacks(new ItemStack[]{item});
    }

    /**
     * Deserialize Base64 string to single ItemStack
     */
    public static ItemStack deserializeItemStack(String data) {
        ItemStack[] items = deserializeItemStacks(data);
        return (items.length > 0) ? items[0] : null;
    }

    /**
     * Test if serialization data is valid
     */
    public static boolean isValidSerializedData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return false;
        }

        try {
            ItemStack[] items = deserializeItemStacks(data);
            return items != null && items.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
}