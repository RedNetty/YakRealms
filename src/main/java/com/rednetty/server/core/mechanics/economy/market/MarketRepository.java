package com.rednetty.server.core.mechanics.economy.market;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.MongoDBManager;
import com.rednetty.server.core.database.Repository;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  MongoDB repository for market items with caching and advanced querying
 */
public class MarketRepository implements Repository<MarketItem, UUID> {
    private static final String COLLECTION_NAME = "market_items";
    private static final String EXPIRED_COLLECTION_NAME = "market_items_expired";
    private static final Logger logger = Logger.getLogger(MarketRepository.class.getName());

    private MongoCollection<Document> collection;
    private MongoCollection<Document> expiredCollection;
    private final YakRealms plugin;

    // Caching system
    private final Map<UUID, CachedMarketItem> itemCache = new ConcurrentHashMap<>();
    private final Map<String, List<MarketItem>> searchCache = new ConcurrentHashMap<>();
    private final Map<MarketCategory, List<MarketItem>> categoryCache = new ConcurrentHashMap<>();
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(5);
    private static final long SEARCH_CACHE_DURATION = TimeUnit.MINUTES.toMillis(2);

    // Performance tracking
    private volatile int totalQueries = 0;
    private volatile int cacheHits = 0;

    /**
     * Cached market item with expiration
     */
    private static class CachedMarketItem {
        private final MarketItem item;
        private final long cacheTime;

        public CachedMarketItem(MarketItem item) {
            this.item = item;
            this.cacheTime = System.currentTimeMillis();
        }

        public MarketItem getItem() { return item; }
        public boolean isExpired(long duration) {
            return System.currentTimeMillis() - cacheTime > duration;
        }
    }

    /**
     * Constructor
     */
    public MarketRepository() {
        this.plugin = YakRealms.getInstance();
        initializeCollections();
        createIndexes();
        startCacheCleanupTask();
    }

    /**
     * Initialize MongoDB collections
     */
    private void initializeCollections() {
        try {
            MongoDBManager mongoDBManager = MongoDBManager.getInstance();
            if (mongoDBManager.isConnected()) {
                this.collection = mongoDBManager.getCollection(COLLECTION_NAME);
                this.expiredCollection = mongoDBManager.getCollection(EXPIRED_COLLECTION_NAME);
                logger.info("Market repository initialized with MongoDB");
            } else {
                logger.severe("Failed to initialize market repository - MongoDB not connected");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing market repository", e);
        }
    }

    /**
     * Create database indexes for optimal performance
     */
    private void createIndexes() {
        if (collection == null) return;

        try {
            // Compound index for category and price queries
            collection.createIndex(new Document("category", 1)
                    .append("price", 1)
                    .append("listed_time", -1));

            // Index for owner queries
            collection.createIndex(new Document("owner_uuid", 1));

            // Index for expiration queries
            collection.createIndex(new Document("expires_time", 1));

            // Text index for search functionality on display name
            collection.createIndex(new Document("display_name", "text"));

            // Additional indexes for search fields
            collection.createIndex(new Document("material", 1));
            collection.createIndex(new Document("owner_name", 1));

            // Index for featured items
            collection.createIndex(new Document("is_featured", -1)
                    .append("listed_time", -1));

            // Index for category browsing
            collection.createIndex(new Document("category", 1));

            // Index for price sorting
            collection.createIndex(new Document("price", 1));
            collection.createIndex(new Document("price", -1));

            // Index for listing time sorting
            collection.createIndex(new Document("listed_time", -1));
            collection.createIndex(new Document("listed_time", 1));

            // Index for views sorting
            collection.createIndex(new Document("views", -1));

            logger.info("Created database indexes for market collection");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create indexes", e);
        }
    }

    /**
     * Start cache cleanup task
     */
    private void startCacheCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                cleanupCache();
                cleanupExpiredItems();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during cache cleanup", e);
            }
        }, 6000L, 6000L); // Every 5 minutes
    }

    /**
     * Cleanup expired cache entries
     */
    private void cleanupCache() {
        long currentTime = System.currentTimeMillis();

        // Clean item cache
        itemCache.entrySet().removeIf(entry ->
                entry.getValue().isExpired(CACHE_DURATION));

        // Clean search cache
        searchCache.entrySet().removeIf(entry ->
                currentTime - lastCacheUpdate > SEARCH_CACHE_DURATION);

        // Clean category cache
        if (currentTime - lastCacheUpdate > CACHE_DURATION) {
            categoryCache.clear();
            lastCacheUpdate = currentTime;
        }
    }

    /**
     * Find and move expired items to expired collection
     */
    private void cleanupExpiredItems() {
        if (collection == null || expiredCollection == null) return;

        try {
            long currentTime = Instant.now().getEpochSecond();
            Bson expiredFilter = Filters.lt("expires_time", currentTime);

            FindIterable<Document> expiredDocs = collection.find(expiredFilter);
            List<Document> expiredList = new ArrayList<>();

            for (Document doc : expiredDocs) {
                expiredList.add(doc);
            }

            if (!expiredList.isEmpty()) {
                // Move to expired collection
                expiredCollection.insertMany(expiredList);

                // Remove from active collection
                collection.deleteMany(expiredFilter);

                // Clear relevant cache entries
                expiredList.forEach(doc -> {
                    String uuidStr = doc.getString("item_id");
                    if (uuidStr != null) {
                        try {
                            UUID itemId = UUID.fromString(uuidStr);
                            itemCache.remove(itemId);
                        } catch (IllegalArgumentException ignored) {}
                    }
                });

                categoryCache.clear();
                searchCache.clear();

                logger.info("Moved " + expiredList.size() + " expired items to expired collection");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up expired items", e);
        }
    }

    @Override
    public CompletableFuture<Optional<MarketItem>> findById(UUID itemId) {
        return CompletableFuture.supplyAsync(() -> {
            totalQueries++;

            // Check cache first
            CachedMarketItem cached = itemCache.get(itemId);
            if (cached != null && !cached.isExpired(CACHE_DURATION)) {
                cacheHits++;
                return Optional.of(cached.getItem());
            }

            if (collection == null) return Optional.empty();

            try {
                Document doc = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find(Filters.eq("item_id", itemId.toString())).first()
                );

                if (doc != null) {
                    MarketItem item = documentToMarketItem(doc);
                    if (item != null) {
                        itemCache.put(itemId, new CachedMarketItem(item));
                        return Optional.of(item);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error finding market item by ID: " + itemId, e);
            }

            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<MarketItem>> findAll() {
        return findActiveItems(null, null, SortOrder.NEWEST_FIRST, 0, 1000);
    }

    /**
     * Find active (non-expired) market items with advanced filtering
     */
    public CompletableFuture<List<MarketItem>> findActiveItems(MarketCategory category, String searchQuery,
                                                               SortOrder sortOrder, int skip, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            totalQueries++;

            // Create cache key
            String cacheKey = String.format("%s_%s_%s_%d_%d",
                    category, searchQuery, sortOrder, skip, limit);

            // Check search cache
            List<MarketItem> cached = searchCache.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - lastCacheUpdate < SEARCH_CACHE_DURATION) {
                cacheHits++;
                return new ArrayList<>(cached);
            }

            if (collection == null) return new ArrayList<>();

            try {
                List<Bson> filters = new ArrayList<>();

                // Filter out expired items
                filters.add(Filters.gt("expires_time", Instant.now().getEpochSecond()));

                // Category filter
                if (category != null) {
                    filters.add(Filters.eq("category", category.name()));
                }

                // Search filter
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    String query = searchQuery.trim();
                    // Use regex search for multiple fields since text index is single-field
                    List<Bson> searchFilters = new ArrayList<>();
                    searchFilters.add(Filters.regex("display_name", ".*" + query + ".*", "i"));
                    searchFilters.add(Filters.regex("material", ".*" + query + ".*", "i"));
                    searchFilters.add(Filters.regex("owner_name", ".*" + query + ".*", "i"));

                    filters.add(Filters.or(searchFilters));
                }

                Bson combinedFilter = filters.size() > 1 ? Filters.and(filters) : filters.get(0);

                // Sort order
                Bson sort = getSortBson(sortOrder);

                FindIterable<Document> iterable = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find(combinedFilter)
                                .sort(sort)
                                .skip(skip)
                                .limit(limit)
                );

                List<MarketItem> results = new ArrayList<>();
                if (iterable != null) {
                    for (Document doc : iterable) {
                        MarketItem item = documentToMarketItem(doc);
                        if (item != null && item.isValid()) {
                            results.add(item);
                            // Cache individual items
                            itemCache.put(item.getItemId(), new CachedMarketItem(item));
                        }
                    }
                }

                // Cache search results
                searchCache.put(cacheKey, results);

                return results;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error finding active market items", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Find items by owner
     */
    public CompletableFuture<List<MarketItem>> findByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) return new ArrayList<>();

            try {
                Bson filter = Filters.and(
                        Filters.eq("owner_uuid", ownerUuid.toString()),
                        Filters.gt("expires_time", Instant.now().getEpochSecond())
                );

                FindIterable<Document> iterable = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find(filter)
                                .sort(new Document("listed_time", -1))
                );

                List<MarketItem> results = new ArrayList<>();
                if (iterable != null) {
                    for (Document doc : iterable) {
                        MarketItem item = documentToMarketItem(doc);
                        if (item != null) {
                            results.add(item);
                        }
                    }
                }

                return results;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error finding items by owner: " + ownerUuid, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get category statistics
     */
    public CompletableFuture<Map<MarketCategory, Integer>> getCategoryStats() {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) return new HashMap<>();

            try {
                Map<MarketCategory, Integer> stats = new HashMap<>();

                for (MarketCategory category : MarketCategory.values()) {
                    Bson filter = Filters.and(
                            Filters.eq("category", category.name()),
                            Filters.gt("expires_time", Instant.now().getEpochSecond())
                    );

                    Long count = MongoDBManager.getInstance().performSafeOperation(() ->
                            collection.countDocuments(filter)
                    );

                    stats.put(category, count != null ? count.intValue() : 0);
                }

                return stats;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error getting category stats", e);
                return new HashMap<>();
            }
        });
    }

    /**
     * Get price statistics for a category
     */
    public CompletableFuture<PriceStats> getPriceStats(MarketCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) return new PriceStats(0, 0, 0, 0);

            try {
                Bson filter = Filters.and(
                        Filters.eq("category", category.name()),
                        Filters.gt("expires_time", Instant.now().getEpochSecond())
                );

                FindIterable<Document> iterable = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.find(filter).projection(new Document("price", 1))
                );

                List<Integer> prices = new ArrayList<>();
                if (iterable != null) {
                    for (Document doc : iterable) {
                        prices.add(doc.getInteger("price", 0));
                    }
                }

                if (prices.isEmpty()) {
                    return new PriceStats(0, 0, 0, 0);
                }

                Collections.sort(prices);
                int min = prices.get(0);
                int max = prices.get(prices.size() - 1);
                double average = prices.stream().mapToInt(Integer::intValue).average().orElse(0);
                int median = prices.get(prices.size() / 2);

                return new PriceStats(min, max, (int) average, median);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error getting price stats for category: " + category, e);
                return new PriceStats(0, 0, 0, 0);
            }
        });
    }

    @Override
    public CompletableFuture<MarketItem> save(MarketItem item) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null || item == null) return item;

            try {
                Document doc = marketItemToDocument(item);

                MongoDBManager.getInstance().performSafeOperation(() -> {
                    collection.replaceOne(
                            Filters.eq("item_id", item.getItemId().toString()),
                            doc,
                            new ReplaceOptions().upsert(true)
                    );
                    return null;
                });

                // Update cache
                itemCache.put(item.getItemId(), new CachedMarketItem(item));

                // Clear related caches
                categoryCache.remove(item.getCategory());
                searchCache.clear();

                return item;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving market item: " + item.getItemId(), e);
                return item;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(MarketItem item) {
        return deleteById(item.getItemId());
    }

    @Override
    public CompletableFuture<Boolean> deleteById(UUID itemId) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) return false;

            try {
                DeleteResult result = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.deleteOne(Filters.eq("item_id", itemId.toString()))
                );

                boolean deleted = result != null && result.getDeletedCount() > 0;

                if (deleted) {
                    // Clear caches
                    itemCache.remove(itemId);
                    categoryCache.clear();
                    searchCache.clear();
                }

                return deleted;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error deleting market item: " + itemId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> existsById(UUID itemId) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache first
            if (itemCache.containsKey(itemId)) {
                return true;
            }

            if (collection == null) return false;

            try {
                Long count = MongoDBManager.getInstance().performSafeOperation(() ->
                        collection.countDocuments(Filters.eq("item_id", itemId.toString()))
                );

                return count != null && count > 0;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error checking if market item exists: " + itemId, e);
                return false;
            }
        });
    }

    /**
     * Get sort BSON object
     */
    private Bson getSortBson(SortOrder sortOrder) {
        switch (sortOrder) {
            case PRICE_LOW_TO_HIGH:
                return new Document("price", 1);
            case PRICE_HIGH_TO_LOW:
                return new Document("price", -1);
            case OLDEST_FIRST:
                return new Document("listed_time", 1);
            case MOST_VIEWED:
                return new Document("views", -1);
            case NEWEST_FIRST:
            default:
                return new Document("listed_time", -1);
        }
    }

    /**
     * Convert Document to MarketItem
     */
    private MarketItem documentToMarketItem(Document doc) {
        try {
            UUID itemId = UUID.fromString(doc.getString("item_id"));
            UUID ownerUuid = UUID.fromString(doc.getString("owner_uuid"));
            String ownerName = doc.getString("owner_name");
            int price = doc.getInteger("price", 0);
            String materialName = doc.getString("material");
            String displayName = doc.getString("display_name");
            Integer amount = doc.getInteger("amount");
            if (amount == null) amount = 1;

            MarketCategory category = MarketCategory.fromString(doc.getString("category"));
            long listedTime = doc.getLong("listed_time");
            long expiresTime = doc.getLong("expires_time");
            String serializedItemData = doc.getString("item_data");

            // Create the item using full constructor
            MarketItem item = new MarketItem(itemId, ownerUuid, ownerName, price, materialName,
                    displayName, amount, category, listedTime, expiresTime, serializedItemData);

            // Set additional fields
            if (doc.getObjectId("_id") != null) {
                item.setMongoId(doc.getObjectId("_id").toString());
            }
            item.setViews(doc.getInteger("views", 0));
            item.setFeatured(doc.getBoolean("is_featured", false));
            item.setMinLevel(doc.getInteger("min_level", 1));

            @SuppressWarnings("unchecked")
            List<String> tagsList = doc.getList("tags", String.class);
            if (tagsList != null) {
                item.setTags(tagsList.toArray(new String[0]));
            }

            return item;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error converting document to market item", e);
            return null;
        }
    }

    /**
     * Convert MarketItem to Document
     */
    private Document marketItemToDocument(MarketItem item) {
        Document doc = new Document();

        doc.append("item_id", item.getItemId().toString())
                .append("owner_uuid", item.getOwnerUuid().toString())
                .append("owner_name", item.getOwnerName())
                .append("price", item.getPrice())
                .append("category", item.getCategory().name())
                .append("listed_time", item.getListedTime())
                .append("expires_time", item.getExpiresTime())
                .append("views", item.getViews())
                .append("is_featured", item.isFeatured())
                .append("min_level", item.getMinLevel())
                .append("display_name", item.getDisplayName())
                .append("material", item.getMaterialName())
                .append("amount", item.getAmount())
                .append("tags", Arrays.asList(item.getTags()))
                .append("server_name", item.getServerName());

        // Only add item_data if it exists
        if (item.getSerializedItemData() != null && !item.getSerializedItemData().isEmpty()) {
            doc.append("item_data", item.getSerializedItemData());
        }

        return doc;
    }

    /**
     * Clear all caches
     */
    public void clearCache() {
        itemCache.clear();
        searchCache.clear();
        categoryCache.clear();
        lastCacheUpdate = 0;
    }

    /**
     * Get performance statistics
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalQueries", totalQueries);
        stats.put("cacheHits", cacheHits);
        stats.put("cacheHitRate", totalQueries > 0 ? (double) cacheHits / totalQueries * 100 : 0);
        stats.put("itemCacheSize", itemCache.size());
        stats.put("searchCacheSize", searchCache.size());
        stats.put("categoryCacheSize", categoryCache.size());
        return stats;
    }

    /**
     * Sort order enumeration
     */
    public enum SortOrder {
        NEWEST_FIRST,
        OLDEST_FIRST,
        PRICE_LOW_TO_HIGH,
        PRICE_HIGH_TO_LOW,
        MOST_VIEWED
    }

    /**
     * Price statistics data class
     */
    public static class PriceStats {
        private final int min;
        private final int max;
        private final int average;
        private final int median;

        public PriceStats(int min, int max, int average, int median) {
            this.min = min;
            this.max = max;
            this.average = average;
            this.median = median;
        }

        public int getMin() { return min; }
        public int getMax() { return max; }
        public int getAverage() { return average; }
        public int getMedian() { return median; }
    }
}