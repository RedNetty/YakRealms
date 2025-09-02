package com.rednetty.server.core.mechanics.economy.market;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  market item representation with MongoDB integration and improved functionality
 */
public class MarketItem {
    private static final Logger logger = Logger.getLogger(MarketItem.class.getName());

    @Expose @SerializedName("_id")
    private String mongoId;

    @Expose @SerializedName("item_id")
    private final UUID itemId;

    @Expose @SerializedName("owner_uuid")
    private final UUID ownerUuid;

    @Expose @SerializedName("owner_name")
    private String ownerName;

    @Expose @SerializedName("price")
    private int price;

    @Expose @SerializedName("item_data")
    private String serializedItemData;

    @Expose @SerializedName("material")
    private String materialName;

    @Expose @SerializedName("display_name")
    private String displayName;

    @Expose @SerializedName("amount")
    private int amount;

    @Expose @SerializedName("category")
    private MarketCategory category;

    @Expose @SerializedName("listed_time")
    private long listedTime;

    @Expose @SerializedName("expires_time")
    private long expiresTime;

    @Expose @SerializedName("views")
    private int views = 0;

    @Expose @SerializedName("is_featured")
    private boolean featured = false;

    @Expose @SerializedName("tags")
    private String[] tags;

    @Expose @SerializedName("min_level")
    private int minLevel = 1;

    @Expose @SerializedName("server_name")
    private String serverName;

    // Transient fields
    private transient ItemStack cachedItemStack;
    private transient long lastCacheTime = 0;
    private static final long CACHE_DURATION = 300000; // 5 minutes

    /**
     * Constructor for creating a new market item
     */
    public MarketItem(UUID ownerUuid, String ownerName, ItemStack itemStack, int price, MarketCategory category) {
        this.itemId = UUID.randomUUID();
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.price = Math.max(1, price);
        this.category = category != null ? category : MarketCategory.MISCELLANEOUS;
        this.listedTime = Instant.now().getEpochSecond();
        this.expiresTime = this.listedTime + (7 * 24 * 60 * 60); // 7 days default
        this.serverName = Bukkit.getServer().getName();

        if (itemStack != null) {
            this.amount = itemStack.getAmount();
            this.materialName = itemStack.getType().name();
            this.displayName = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()
                    ? itemStack.getItemMeta().getDisplayName()
                    : formatMaterialName(itemStack.getType());

            this.serializedItemData = serializeItemStack(itemStack);
            this.tags = generateTags(itemStack);
        }
    }

    /**
     * Constructor for MongoDB deserialization
     */
    public MarketItem() {
        this.itemId = UUID.randomUUID();
        this.ownerUuid = UUID.randomUUID();
        this.listedTime = Instant.now().getEpochSecond();
        this.expiresTime = this.listedTime + (7 * 24 * 60 * 60);
        this.serverName = "Unknown";
    }

    /**
     * Full constructor for deserialization
     */
    public MarketItem(UUID itemId, UUID ownerUuid, String ownerName, int price, String materialName,
                      String displayName, int amount, MarketCategory category, long listedTime,
                      long expiresTime, String serializedItemData) {
        this.itemId = itemId;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.price = price;
        this.materialName = materialName;
        this.displayName = displayName;
        this.amount = amount;
        this.category = category;
        this.listedTime = listedTime;
        this.expiresTime = expiresTime;
        this.serializedItemData = serializedItemData;
        this.serverName = "Unknown";
        this.tags = new String[0];
    }

    /**
     * Serialize ItemStack to Base64 string
     */
    private String serializeItemStack(ItemStack itemStack) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(itemStack);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to serialize ItemStack for market item", e);
            return "";
        }
    }

    /**
     * Deserialize ItemStack from Base64 string with caching
     */
    public ItemStack getItemStack() {
        long currentTime = System.currentTimeMillis();

        // Return cached version if still valid
        if (cachedItemStack != null && (currentTime - lastCacheTime) < CACHE_DURATION) {
            return cachedItemStack.clone();
        }

        if (serializedItemData == null || serializedItemData.isEmpty()) {
            // Fallback to material if serialization failed
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                cachedItemStack = new ItemStack(material, amount);
                lastCacheTime = currentTime;
                return cachedItemStack.clone();
            }
            return new ItemStack(Material.STONE);
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(serializedItemData));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            cachedItemStack = (ItemStack) dataInput.readObject();
            lastCacheTime = currentTime;
            return cachedItemStack.clone();
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.WARNING, "Failed to deserialize ItemStack for market item " + itemId, e);

            // Fallback to creating item from stored data
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                cachedItemStack = new ItemStack(material, amount);
                lastCacheTime = currentTime;
                return cachedItemStack.clone();
            }
            return new ItemStack(Material.STONE);
        }
    }

    /**
     * Generate search tags for the item
     */
    private String[] generateTags(ItemStack itemStack) {
        java.util.Set<String> tagSet = new java.util.HashSet<>();

        // Material name tags
        String materialName = itemStack.getType().name().toLowerCase();
        tagSet.add(materialName);
        tagSet.addAll(java.util.Arrays.asList(materialName.split("_")));

        // Display name tags
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            String displayName = itemStack.getItemMeta().getDisplayName().toLowerCase()
                    .replaceAll("§[0-9a-fk-or]", ""); // Remove color codes
            tagSet.addAll(java.util.Arrays.asList(displayName.split("\\s+")));
        }

        // Category tags
        tagSet.add(category.name().toLowerCase());

        // Material category tags
        if (itemStack.getType().name().contains("SWORD")) tagSet.add("weapon");
        if (itemStack.getType().name().contains("ARMOR") || itemStack.getType().name().contains("HELMET")
                || itemStack.getType().name().contains("CHESTPLATE") || itemStack.getType().name().contains("LEGGINGS")
                || itemStack.getType().name().contains("BOOTS")) tagSet.add("armor");
        if (itemStack.getType().name().contains("PICKAXE") || itemStack.getType().name().contains("AXE")
                || itemStack.getType().name().contains("SHOVEL") || itemStack.getType().name().contains("HOE")) tagSet.add("tool");

        return tagSet.toArray(new String[0]);
    }

    /**
     * Format material name for display
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Check if the item has expired
     */
    public boolean isExpired() {
        return Instant.now().getEpochSecond() > expiresTime;
    }

    /**
     * Check if the item is still valid (not expired and owner exists)
     */
    public boolean isValid() {
        if (isExpired()) return false;

        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
        return owner.hasPlayedBefore() || owner.isOnline();
    }

    /**
     * Get the owner as an OfflinePlayer
     */
    public OfflinePlayer getOwner() {
        return Bukkit.getOfflinePlayer(ownerUuid);
    }

    /**
     * Get formatted price string
     */
    public String getFormattedPrice() {
        return String.format("%,d", price);
    }

    /**
     * Get time remaining until expiration
     */
    public long getTimeRemaining() {
        return Math.max(0, expiresTime - Instant.now().getEpochSecond());
    }

    /**
     * Get formatted time remaining
     */
    public String getFormattedTimeRemaining() {
        long remaining = getTimeRemaining();

        if (remaining <= 0) return "Expired";

        long days = remaining / 86400;
        long hours = (remaining % 86400) / 3600;
        long minutes = (remaining % 3600) / 60;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    /**
     * Increment view count
     */
    public void incrementViews() {
        this.views++;
    }

    /**
     * Extend expiration time
     */
    public void extendExpiration(long seconds) {
        this.expiresTime += seconds;
    }

    /**
     * Check if item matches search query
     */
    public boolean matchesSearch(String query) {
        if (query == null || query.trim().isEmpty()) return true;

        String lowerQuery = query.toLowerCase().trim();

        // Check display name
        if (displayName.toLowerCase().contains(lowerQuery)) return true;

        // Check material name
        if (materialName.toLowerCase().contains(lowerQuery)) return true;

        // Check owner name
        if (ownerName.toLowerCase().contains(lowerQuery)) return true;

        // Check tags
        for (String tag : tags) {
            if (tag.contains(lowerQuery)) return true;
        }

        // Check category
        if (category.name().toLowerCase().contains(lowerQuery)) return true;

        return false;
    }

    // Getters and Setters
    public String getMongoId() { return mongoId; }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    public UUID getItemId() { return itemId; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = Math.max(1, price); }

    public String getDisplayName() { return displayName; }
    public String getMaterialName() { return materialName; }
    public int getAmount() { return amount; }

    public MarketCategory getCategory() { return category; }
    public void setCategory(MarketCategory category) { this.category = category; }

    public long getListedTime() { return listedTime; }
    public long getExpiresTime() { return expiresTime; }
    public void setExpiresTime(long expiresTime) { this.expiresTime = expiresTime; }

    public int getViews() { return views; }
    public void setViews(int views) { this.views = Math.max(0, views); }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public String[] getTags() { return tags != null ? tags.clone() : new String[0]; }
    public void setTags(String[] tags) { this.tags = tags != null ? tags.clone() : new String[0]; }

    public int getMinLevel() { return minLevel; }
    public void setMinLevel(int minLevel) { this.minLevel = Math.max(1, minLevel); }

    public String getServerName() { return serverName; }
    public String getSerializedItemData() { return serializedItemData; }
    public void setSerializedItemData(String serializedItemData) { this.serializedItemData = serializedItemData; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MarketItem that = (MarketItem) obj;
        return Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId);
    }

    @Override
    public String toString() {
        return "MarketItem{" +
                "itemId=" + itemId +
                ", ownerName='" + ownerName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", price=" + price +
                ", category=" + category +
                ", amount=" + amount +
                ", expired=" + isExpired() +
                '}';
    }
}