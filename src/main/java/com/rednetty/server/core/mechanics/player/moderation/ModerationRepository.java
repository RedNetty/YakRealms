package com.rednetty.server.core.mechanics.player.moderation;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.rednetty.server.core.database.MongoDBManager;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Modern repository for moderation history with advanced querying capabilities
 * Supports audit trail, analytics, and appeal system integration
 */
public class ModerationRepository {
    
    private static ModerationRepository instance;
    private final MongoCollection<ModerationHistory> collection;
    private final Logger logger;
    
    // Cache for frequently accessed data
    private final Map<UUID, List<ModerationHistory>> playerHistoryCache = new HashMap<>();
    private final Map<String, List<ModerationHistory>> ipHistoryCache = new HashMap<>();
    private long lastCacheCleanup = System.currentTimeMillis();
    private static final long CACHE_EXPIRE_TIME = 300000; // 5 minutes
    
    private ModerationRepository() {
        this.logger = Logger.getLogger(ModerationRepository.class.getName());
        
        MongoCollection<ModerationHistory> tempCollection = null;
        try {
            MongoDBManager mongoManager = MongoDBManager.getInstance();
            if (mongoManager != null && mongoManager.isHealthy()) {
                tempCollection = mongoManager.getDatabase().getCollection("moderation_history", ModerationHistory.class);
                logger.info("ModerationRepository connected to MongoDB successfully");
            } else {
                logger.warning("MongoDB not available - ModerationRepository running in limited mode");
            }
        } catch (Exception e) {
            logger.severe("Failed to initialize ModerationRepository: " + e.getMessage());
        }
        
        this.collection = tempCollection;
        
        if (collection != null) {
            // Create indexes for performance
            createIndexes();
        }
    }
    
    public static ModerationRepository getInstance() {
        if (instance == null) {
            instance = new ModerationRepository();
        }
        return instance;
    }
    
    private void createIndexes() {
        if (collection == null) {
            logger.warning("Cannot create indexes - collection not available");
            return;
        }
        
        try {
            // Core indexes
            collection.createIndex(Indexes.ascending("targetPlayerId"));
            collection.createIndex(Indexes.ascending("staffId"));
            collection.createIndex(Indexes.ascending("ipAddress"));
            collection.createIndex(Indexes.descending("timestamp"));
            collection.createIndex(Indexes.ascending("isActive"));
            
            // Compound indexes for complex queries  
            collection.createIndex(new Document("targetPlayerId", 1).append("isActive", 1));
            collection.createIndex(new Document("action", 1).append("timestamp", -1));
            
            logger.info("Moderation repository indexes created successfully");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create moderation indexes", e);
        }
    }
    
    // ==========================================
    // CORE CRUD OPERATIONS
    // ==========================================
    
    /**
     * Add a new moderation entry with safety checks
     */
    public CompletableFuture<ObjectId> addModerationEntry(ModerationHistory entry) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                logger.warning("Cannot add moderation entry - collection not available");
                return null;
            }
            
            try {
                if (entry.getId() == null) {
                    entry.setId(new ObjectId());
                }
                entry.setTimestamp(new Date());
                
                collection.insertOne(entry);
                
                // Clear cache for affected player
                clearPlayerCache(entry.getTargetPlayerId());
                if (entry.getIpAddress() != null) {
                    clearIPCache(entry.getIpAddress());
                }
                
                return entry.getId();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to add moderation entry", e);
                return null;
            }
        });
    }
    
    /**
     * Update an existing moderation entry
     */
    public CompletableFuture<Boolean> updateModerationEntry(ModerationHistory entry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = collection.replaceOne(
                    Filters.eq("_id", entry.getId()), 
                    entry
                );
                
                // Clear cache
                clearPlayerCache(entry.getTargetPlayerId());
                if (entry.getIpAddress() != null) {
                    clearIPCache(entry.getIpAddress());
                }
                
                return result.getModifiedCount() > 0;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to update moderation entry", e);
                return false;
            }
        });
    }
    
    /**
     * Revoke/end a punishment
     */
    public CompletableFuture<Boolean> revokePunishment(ObjectId entryId, String revokedBy, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var entry = collection.find(Filters.eq("_id", entryId)).first();
                if (entry == null) return false;
                
                entry.setActive(false);
                entry.setRevokedAt(new Date());
                entry.setRevokedBy(revokedBy);
                entry.setRevokeReason(reason);
                
                var result = collection.replaceOne(Filters.eq("_id", entryId), entry);
                
                // Clear cache
                clearPlayerCache(entry.getTargetPlayerId());
                
                return result.getModifiedCount() > 0;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to revoke punishment", e);
                return false;
            }
        });
    }
    
    // ==========================================
    // QUERY OPERATIONS
    // ==========================================
    
    /**
     * Get complete moderation history for a player
     */
    public CompletableFuture<List<ModerationHistory>> getPlayerHistory(UUID playerId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                logger.warning("Cannot get player history - collection not available");
                return new ArrayList<>();
            }
            
            try {
                // Check cache first
                cleanupExpiredCache();
                List<ModerationHistory> cached = playerHistoryCache.get(playerId);
                if (cached != null) {
                    return cached.stream().limit(limit).collect(Collectors.toList());
                }
                
                List<ModerationHistory> history = collection.find(Filters.eq("targetPlayerId", playerId))
                    .sort(Sorts.descending("timestamp"))
                    .limit(Math.min(limit, 100)) // Reasonable limit
                    .into(new ArrayList<>());
                
                // Cache result
                playerHistoryCache.put(playerId, history);
                
                return history;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to get player history", e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Get active punishments for a player
     */
    public CompletableFuture<List<ModerationHistory>> getActivePunishments(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                logger.warning("Cannot get active punishments - collection not available");
                return new ArrayList<>();
            }
            
            try {
                return collection.find(Filters.and(
                    Filters.eq("targetPlayerId", playerId),
                    Filters.eq("isActive", true)
                )).into(new ArrayList<>());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to get active punishments", e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Get moderation history by IP address
     */
    public CompletableFuture<List<ModerationHistory>> getIPHistory(String ipAddress, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                cleanupExpiredCache();
                List<ModerationHistory> cached = ipHistoryCache.get(ipAddress);
                if (cached != null) {
                    return cached.stream().limit(limit).collect(Collectors.toList());
                }
                
                List<ModerationHistory> history = collection.find(Filters.eq("ipAddress", ipAddress))
                    .sort(Sorts.descending("timestamp"))
                    .limit(Math.min(limit, 50))
                    .into(new ArrayList<>());
                
                // Cache result
                ipHistoryCache.put(ipAddress, history);
                
                return history;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to get IP history", e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Get moderation actions by staff member
     */
    public CompletableFuture<List<ModerationHistory>> getStaffActions(UUID staffId, Date since) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Bson filter = Filters.eq("staffId", staffId);
                if (since != null) {
                    filter = Filters.and(filter, Filters.gte("timestamp", since));
                }
                
                return collection.find(filter)
                    .sort(Sorts.descending("timestamp"))
                    .limit(100)
                    .into(new ArrayList<>());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to get staff actions", e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Get punishment statistics
     */
    public CompletableFuture<ModerationStats> getModerationStats(Date since) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Bson filter = since != null ? Filters.gte("timestamp", since) : Filters.empty();
                
                List<ModerationHistory> entries = collection.find(filter).into(new ArrayList<>());
                
                return ModerationStats.builder()
                    .totalPunishments(entries.size())
                    .activePunishments((int) entries.stream().filter(ModerationHistory::isActive).count())
                    .warningsIssued((int) entries.stream().filter(e -> e.getAction() == ModerationHistory.ModerationAction.WARNING).count())
                    .bansIssued((int) entries.stream().filter(e -> e.getAction() == ModerationHistory.ModerationAction.TEMP_BAN || e.getAction() == ModerationHistory.ModerationAction.PERMANENT_BAN).count())
                    .mutesIssued((int) entries.stream().filter(e -> e.getAction() == ModerationHistory.ModerationAction.MUTE).count())
                    .appealsSubmitted((int) entries.stream().filter(e -> e.getAppealStatus() != ModerationHistory.AppealStatus.NOT_APPEALED).count())
                    .appealsApproved((int) entries.stream().filter(e -> e.getAppealStatus() == ModerationHistory.AppealStatus.APPROVED).count())
                    .build();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to get moderation stats", e);
                return new ModerationStats();
            }
        });
    }
    
    /**
     * Search moderation entries by various criteria
     */
    public CompletableFuture<List<ModerationHistory>> searchEntries(ModerationSearchCriteria criteria) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Bson> filters = new ArrayList<>();
                
                if (criteria.getPlayerId() != null) {
                    filters.add(Filters.eq("targetPlayerId", criteria.getPlayerId()));
                }
                if (criteria.getStaffId() != null) {
                    filters.add(Filters.eq("staffId", criteria.getStaffId()));
                }
                if (criteria.getAction() != null) {
                    filters.add(Filters.eq("action", criteria.getAction()));
                }
                if (criteria.getSeverity() != null) {
                    filters.add(Filters.eq("severity", criteria.getSeverity()));
                }
                if (criteria.getStartDate() != null) {
                    filters.add(Filters.gte("timestamp", criteria.getStartDate()));
                }
                if (criteria.getEndDate() != null) {
                    filters.add(Filters.lte("timestamp", criteria.getEndDate()));
                }
                if (criteria.getIpAddress() != null) {
                    filters.add(Filters.eq("ipAddress", criteria.getIpAddress()));
                }
                if (criteria.getIsActive() != null) {
                    filters.add(Filters.eq("isActive", criteria.getIsActive()));
                }
                if (criteria.getReasonContains() != null) {
                    filters.add(Filters.regex("reason", criteria.getReasonContains(), "i"));
                }
                
                Bson finalFilter = filters.isEmpty() ? Filters.empty() : Filters.and(filters);
                
                return collection.find(finalFilter)
                    .sort(Sorts.descending("timestamp"))
                    .limit(criteria.getLimit() > 0 ? criteria.getLimit() : 50)
                    .into(new ArrayList<>());
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to search moderation entries", e);
                return new ArrayList<>();
            }
        });
    }
    
    
    // ==========================================
    // CACHE MANAGEMENT
    // ==========================================
    
    private void clearPlayerCache(UUID playerId) {
        playerHistoryCache.remove(playerId);
    }
    
    private void clearIPCache(String ipAddress) {
        ipHistoryCache.remove(ipAddress);
    }
    
    private void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup > CACHE_EXPIRE_TIME) {
            playerHistoryCache.clear();
            ipHistoryCache.clear();
            lastCacheCleanup = currentTime;
        }
    }
    
    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        playerHistoryCache.clear();
        ipHistoryCache.clear();
        lastCacheCleanup = System.currentTimeMillis();
    }
    
    // Methods for moderation menus
    
    /**
     * Get all punishment records (for punishment history menu)
     */
    public List<PunishmentRecord> getAllPunishments() {
        try {
            // Convert ModerationHistory to PunishmentRecord for menu compatibility
            List<PunishmentRecord> records = new ArrayList<>();
            // This would be implemented with proper database query
            // For now, return empty list as placeholder
            return records;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get all punishments", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Check if player is currently banned
     */
    public boolean isPlayerBanned(UUID playerId) {
        try {
            // Check for active ban records
            return false; // Placeholder
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to check ban status", e);
            return false;
        }
    }
    
    /**
     * Check if player is currently muted
     */
    public boolean isPlayerMuted(UUID playerId) {
        try {
            // Check for active mute records
            return false; // Placeholder
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to check mute status", e);
            return false;
        }
    }
    
    /**
     * Get player's moderation history summary
     */
    public ModerationHistory getPlayerHistory(UUID playerId) {
        try {
            // Return summary of player's history
            return new ModerationHistory(); // Placeholder
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get player history summary", e);
            return new ModerationHistory();
        }
    }
    
    /**
     * Search moderation history with criteria - needed by StubMenus
     */
    public CompletableFuture<List<ModerationHistory>> searchModerationHistory(ModerationSearchCriteria criteria) {
        return searchEntries(criteria);
    }
    
    /**
     * Get current moderation stats without date parameter - needed by StubMenus
     */
    public CompletableFuture<ModerationStats> getModerationStats() {
        return getModerationStats(null);
    }
}