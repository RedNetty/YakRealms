package com.rednetty.server.utils.collections;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility for easier collection operations
 * Provides common collection patterns and helper methods
 */
public class CollectionUtil {

    /**
     * Get random element from collection
     * @param collection The collection to select from
     * @return Random element or null if empty
     */
    public static <T> T getRandom(Collection<T> collection) {
        if (collection.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(collection.size());
        if (collection instanceof List) {
            return ((List<T>) collection).get(index);
        }
        return collection.stream().skip(index).findFirst().orElse(null);
    }

    /**
     * Get multiple random elements without replacement
     * @param collection The collection to select from
     * @param count Number of elements to select
     * @return List of random elements
     */
    public static <T> List<T> getRandomMultiple(Collection<T> collection, int count) {
        List<T> list = new ArrayList<>(collection);
        Collections.shuffle(list);
        return list.stream().limit(count).collect(Collectors.toList());
    }

    /**
     * Weighted random selection
     * @param weights Map of items to their weights
     * @return Randomly selected item based on weights
     */
    public static <T> T getWeightedRandom(Map<T, Double> weights) {
        if (weights.isEmpty()) return null;

        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0) return null;

        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;

        double currentWeight = 0;
        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            currentWeight += entry.getValue();
            if (random <= currentWeight) {
                return entry.getKey();
            }
        }

        // Fallback to last element if rounding errors occur
        return weights.keySet().iterator().next();
    }

    /**
     * Group by with count
     * @param collection The collection to group
     * @param classifier Function to extract grouping key
     * @return Map of keys to counts
     */
    public static <T, K> Map<K, Long> groupByCount(Collection<T> collection, Function<T, K> classifier) {
        return collection.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    /**
     * Partition collection by predicate
     * @param collection The collection to partition
     * @param predicate The predicate to test
     * @return Map with true/false keys containing matching/non-matching elements
     */
    public static <T> Map<Boolean, List<T>> partition(Collection<T> collection, Predicate<T> predicate) {
        return collection.stream().collect(Collectors.partitioningBy(predicate));
    }

    /**
     * Safe get from list with default value
     * @param list The list to get from
     * @param index The index to get
     * @param defaultValue Default value if index is out of bounds
     * @return Element at index or default value
     */
    public static <T> T safeGet(List<T> list, int index, T defaultValue) {
        return index >= 0 && index < list.size() ? list.get(index) : defaultValue;
    }

    /**
     * Create map from key-value pairs
     * @param pairs Alternating keys and values
     * @return Map created from pairs
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mapOf(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Pairs must be even number of arguments");
        }
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((K) pairs[i], (V) pairs[i + 1]);
        }
        return map;
    }

    /**
     * Batch process collection
     * @param collection The collection to process
     * @param batchSize Size of each batch
     * @param processor Function to process each batch
     */
    public static <T> void processBatches(Collection<T> collection, int batchSize, java.util.function.Consumer<List<T>> processor) {
        List<T> batch = new ArrayList<>();
        for (T item : collection) {
            batch.add(item);
            if (batch.size() >= batchSize) {
                processor.accept(new ArrayList<>(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            processor.accept(batch);
        }
    }

    /**
     * Find first element matching predicate
     * @param collection The collection to search
     * @param predicate The predicate to match
     * @return Optional containing first match
     */
    public static <T> Optional<T> findFirst(Collection<T> collection, Predicate<T> predicate) {
        return collection.stream().filter(predicate).findFirst();
    }

    /**
     * Check if any element matches predicate
     */
    public static <T> boolean anyMatch(Collection<T> collection, Predicate<T> predicate) {
        return collection.stream().anyMatch(predicate);
    }

    /**
     * Check if all elements match predicate
     */
    public static <T> boolean allMatch(Collection<T> collection, Predicate<T> predicate) {
        return collection.stream().allMatch(predicate);
    }

    /**
     * Create list with repeated element
     * @param element The element to repeat
     * @param count Number of repetitions
     * @return List with repeated elements
     */
    public static <T> List<T> repeat(T element, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> element)
                .collect(Collectors.toList());
    }

    /**
     * Flatten nested collections
     * @param collections Collection of collections
     * @return Flattened list
     */
    public static <T> List<T> flatten(Collection<? extends Collection<T>> collections) {
        return collections.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Get elements at specific indices
     * @param list The list to get from
     * @param indices The indices to get
     * @return List of elements at those indices
     */
    public static <T> List<T> getAtIndices(List<T> list, int... indices) {
        return Arrays.stream(indices)
                .filter(i -> i >= 0 && i < list.size())
                .mapToObj(list::get)
                .collect(Collectors.toList());
    }

    /**
     * Remove duplicates while preserving order
     * @param collection The collection to deduplicate
     * @return List without duplicates
     */
    public static <T> List<T> removeDuplicates(Collection<T> collection) {
        return collection.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Intersect multiple collections
     * @param collections Collections to intersect
     * @return Set containing common elements
     */
    @SafeVarargs
    public static <T> Set<T> intersect(Collection<T>... collections) {
        if (collections.length == 0) return new HashSet<>();

        Set<T> result = new HashSet<>(collections[0]);
        for (int i = 1; i < collections.length; i++) {
            result.retainAll(collections[i]);
        }
        return result;
    }

    /**
     * Union multiple collections
     * @param collections Collections to union
     * @return Set containing all elements
     */
    @SafeVarargs
    public static <T> Set<T> union(Collection<T>... collections) {
        Set<T> result = new HashSet<>();
        for (Collection<T> collection : collections) {
            result.addAll(collection);
        }
        return result;
    }

    /**
     * Create a map from a collection using key and value extractors
     * @param collection The source collection
     * @param keyExtractor Function to extract keys
     * @param valueExtractor Function to extract values
     * @return Map created from collection
     */
    public static <T, K, V> Map<K, V> toMap(Collection<T> collection, Function<T, K> keyExtractor, Function<T, V> valueExtractor) {
        return collection.stream().collect(Collectors.toMap(keyExtractor, valueExtractor));
    }

    /**
     * Group collection into map of lists
     * @param collection The collection to group
     * @param classifier Function to extract grouping key
     * @return Map of keys to lists of values
     */
    public static <T, K> Map<K, List<T>> groupBy(Collection<T> collection, Function<T, K> classifier) {
        return collection.stream().collect(Collectors.groupingBy(classifier));
    }

    /**
     * Check if collection is null or empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Check if collection is not null and not empty
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    /**
     * Get size safely (returns 0 for null)
     */
    public static int safeSize(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * Create paginated view of collection
     * @param collection The collection to paginate
     * @param page Page number (1-based)
     * @param pageSize Size of each page
     * @return Sublist for the specified page
     */
    public static <T> List<T> paginate(List<T> collection, int page, int pageSize) {
        if (page < 1 || pageSize < 1) return new ArrayList<>();

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, collection.size());

        if (start >= collection.size()) return new ArrayList<>();

        return collection.subList(start, end);
    }

    /**
     * Calculate number of pages for pagination
     */
    public static int getPageCount(Collection<?> collection, int pageSize) {
        if (isEmpty(collection) || pageSize < 1) return 0;
        return (collection.size() + pageSize - 1) / pageSize;
    }
}