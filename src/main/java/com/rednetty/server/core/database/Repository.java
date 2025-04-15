package com.rednetty.server.core.database;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Generic repository interface for database operations
 *
 * @param <T>  The entity type
 * @param <ID> The ID type
 */
public interface Repository<T, ID> {
    /**
     * Find an entity by its ID
     *
     * @param id The entity ID
     * @return A CompletableFuture containing the entity, or empty if not found
     */
    CompletableFuture<Optional<T>> findById(ID id);

    /**
     * Find all entities
     *
     * @return A CompletableFuture containing a list of all entities
     */
    CompletableFuture<List<T>> findAll();

    /**
     * Save an entity
     *
     * @param entity The entity to save
     * @return A CompletableFuture containing the saved entity
     */
    CompletableFuture<T> save(T entity);

    /**
     * Delete an entity
     *
     * @param entity The entity to delete
     * @return A CompletableFuture containing true if deleted, false otherwise
     */
    CompletableFuture<Boolean> delete(T entity);

    /**
     * Delete an entity by its ID
     *
     * @param id The entity ID
     * @return A CompletableFuture containing true if deleted, false otherwise
     */
    CompletableFuture<Boolean> deleteById(ID id);

    /**
     * Check if an entity exists
     *
     * @param id The entity ID
     * @return A CompletableFuture containing true if the entity exists, false otherwise
     */
    CompletableFuture<Boolean> existsById(ID id);
}