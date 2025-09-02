package com.rednetty.server.core.mechanics.player.moderation;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Comprehensive appeal system for moderation punishments with
 * automated processing, staff review queues, and decision tracking.
 * 
 * Features:
 * - Player appeal submission and tracking
 * - Staff review queue management
 * - Automated appeal processing for minor cases
 * - Appeal history and analytics
 * - Notification system for all parties
 * - Appeal deadline management
 * - Evidence attachment support
 */
public class AppealSystem {
    
    private static AppealSystem instance;
    private final Logger logger;
    private final ModerationRepository repository;
    private final YakPlayerManager playerManager;
    
    // Appeal queues and tracking
    private final Map<ObjectId, Appeal> activeAppeals = new HashMap<>();
    private final Map<UUID, List<ObjectId>> playerAppeals = new HashMap<>();
    private final Map<UUID, List<ObjectId>> staffReviews = new HashMap<>();
    
    // Configuration
    private static final long APPEAL_DEADLINE_DAYS = 14; // 14 days to appeal
    private static final long APPEAL_REVIEW_DAYS = 7; // 7 days for staff to review
    private static final int MAX_APPEALS_PER_PLAYER = 3; // Max active appeals per player
    private static final long AUTO_APPROVE_THRESHOLD = 30 * 24 * 60 * 60; // 30 days for minor offenses
    
    private AppealSystem() {
        this.logger = YakRealms.getInstance().getLogger();
        this.repository = ModerationRepository.getInstance();
        this.playerManager = YakPlayerManager.getInstance();
    }
    
    public static AppealSystem getInstance() {
        if (instance == null) {
            instance = new AppealSystem();
        }
        return instance;
    }
    
    // ==========================================
    // APPEAL SUBMISSION
    // ==========================================
    
    /**
     * Submit an appeal for a punishment
     */
    public CompletableFuture<AppealResult> submitAppeal(UUID playerId, String playerName,
                                                       ObjectId punishmentId, String reason,
                                                       List<String> evidence) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate appeal eligibility - use synchronous version
                AppealEligibility eligibility = checkAppealEligibility(playerId, punishmentId).get();
                if (!eligibility.isEligible()) {
                    return AppealResult.failure(eligibility.getReason());
                }
                
                // Check player appeal limits
                List<ObjectId> playerActiveAppeals = playerAppeals.getOrDefault(playerId, new ArrayList<>());
                if (playerActiveAppeals.size() >= MAX_APPEALS_PER_PLAYER) {
                    return AppealResult.failure("You have reached the maximum number of active appeals (" + 
                                              MAX_APPEALS_PER_PLAYER + ")");
                }
                
                // Create appeal
                Appeal appeal = new Appeal();
                appeal.setId(new ObjectId());
                appeal.setPlayerId(playerId);
                appeal.setPlayerName(playerName);
                appeal.setPunishmentId(punishmentId);
                appeal.setReason(reason);
                appeal.setEvidence(evidence != null ? evidence : new ArrayList<>());
                appeal.setSubmittedAt(new Date());
                appeal.setStatus(AppealStatus.PENDING);
                appeal.setPriority(calculateAppealPriority(eligibility.getPunishment()));
                appeal.setDeadline(new Date(System.currentTimeMillis() + (APPEAL_REVIEW_DAYS * 24 * 60 * 60 * 1000)));
                
                // Check for automatic approval eligibility
                if (isEligibleForAutoApproval(eligibility.getPunishment())) {
                    appeal.setStatus(AppealStatus.AUTO_APPROVED);
                    appeal.setReviewedAt(new Date());
                    appeal.setReviewerName("System");
                    appeal.setResponse("Automatically approved due to age and severity of punishment");
                    
                    // Process auto-approval
                    processAppealApproval(appeal, eligibility.getPunishment());
                }
                
                // Store appeal
                activeAppeals.put(appeal.getId(), appeal);
                playerActiveAppeals.add(appeal.getId());
                playerAppeals.put(playerId, playerActiveAppeals);
                
                // Update punishment entry
                eligibility.getPunishment().setAppealStatus(ModerationHistory.AppealStatus.PENDING);
                eligibility.getPunishment().setAppealedAt(new Date());
                eligibility.getPunishment().setAppealReason(reason);
                repository.updateModerationEntry(eligibility.getPunishment());
                
                // Notify staff about new appeal
                if (appeal.getStatus() == AppealStatus.PENDING) {
                    notifyStaffNewAppeal(appeal, eligibility.getPunishment());
                }
                
                // Notify dashboard
                ModerationDashboard.getInstance().recordAppeal(playerId, playerName, 
                    appeal.getStatus() == AppealStatus.AUTO_APPROVED ? 
                    ModerationHistory.AppealStatus.APPROVED : ModerationHistory.AppealStatus.PENDING);
                
                logger.info("Appeal submitted by " + playerName + " for punishment " + punishmentId + 
                           " (Status: " + appeal.getStatus() + ")");
                
                return AppealResult.success(appeal, 
                    appeal.getStatus() == AppealStatus.AUTO_APPROVED ? "Appeal automatically approved" : null);
                
            } catch (Exception e) {
                logger.severe("Error submitting appeal: " + e.getMessage());
                return AppealResult.failure("Internal error processing appeal");
            }
        });
    }
    
    /**
     * Check if a punishment can be appealed
     */
    public CompletableFuture<AppealEligibility> checkAppealEligibility(UUID playerId, ObjectId punishmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Find the punishment
                List<ModerationHistory> history = repository.getPlayerHistory(playerId, 100).join();
                ModerationHistory punishment = history.stream()
                    .filter(h -> punishmentId.equals(h.getId()))
                    .findFirst()
                    .orElse(null);
                
                if (punishment == null) {
                    return new AppealEligibility(false, "Punishment not found", null);
                }
                
                // Check if punishment is appealable
                if (!punishment.isAppealable()) {
                    return new AppealEligibility(false, "This punishment type cannot be appealed", punishment);
                }
                
                // Check if already appealed
                if (punishment.getAppealStatus() != ModerationHistory.AppealStatus.NOT_APPEALED) {
                    return new AppealEligibility(false, "This punishment has already been appealed", punishment);
                }
                
                // Check appeal deadline
                long punishmentAge = System.currentTimeMillis() - punishment.getTimestamp().getTime();
                long deadlineMs = APPEAL_DEADLINE_DAYS * 24 * 60 * 60 * 1000;
                
                if (punishmentAge > deadlineMs) {
                    return new AppealEligibility(false, "Appeal deadline has passed (14 days)", punishment);
                }
                
                // Check if punishment is still active (can't appeal expired punishments)
                if (!punishment.isActive() && !punishment.isPermanent()) {
                    return new AppealEligibility(false, "Cannot appeal expired punishments", punishment);
                }
                
                return new AppealEligibility(true, "Appeal eligible", punishment);
                
            } catch (Exception e) {
                logger.severe("Error checking appeal eligibility: " + e.getMessage());
                return new AppealEligibility(false, "Error checking eligibility", null);
            }
        });
    }
    
    // ==========================================
    // STAFF REVIEW SYSTEM
    // ==========================================
    
    /**
     * Get appeals pending review
     */
    public List<Appeal> getPendingAppeals() {
        return activeAppeals.values().stream()
            .filter(appeal -> appeal.getStatus() == AppealStatus.PENDING || 
                            appeal.getStatus() == AppealStatus.UNDER_REVIEW)
            .sorted((a, b) -> {
                // Sort by priority, then by submission date
                int priorityCompare = Integer.compare(b.getPriority(), a.getPriority());
                if (priorityCompare != 0) return priorityCompare;
                return a.getSubmittedAt().compareTo(b.getSubmittedAt());
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Claim an appeal for review
     */
    public CompletableFuture<AppealResult> claimAppeal(ObjectId appealId, UUID staffId, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            Appeal appeal = activeAppeals.get(appealId);
            if (appeal == null) {
                return AppealResult.failure("Appeal not found");
            }
            
            if (appeal.getStatus() != AppealStatus.PENDING) {
                return AppealResult.failure("Appeal is not available for claiming");
            }
            
            appeal.setStatus(AppealStatus.UNDER_REVIEW);
            appeal.setReviewerId(staffId);
            appeal.setReviewerName(staffName);
            appeal.setClaimedAt(new Date());
            
            // Track staff reviews
            List<ObjectId> staffAppeals = staffReviews.getOrDefault(staffId, new ArrayList<>());
            staffAppeals.add(appealId);
            staffReviews.put(staffId, staffAppeals);
            
            logger.info("Appeal " + appealId + " claimed by " + staffName);
            return AppealResult.success(appeal, "Appeal claimed for review");
        });
    }
    
    /**
     * Review and decide on an appeal
     */
    public CompletableFuture<AppealResult> reviewAppeal(ObjectId appealId, UUID reviewerId, 
                                                       boolean approved, String response) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Appeal appeal = activeAppeals.get(appealId);
                if (appeal == null) {
                    return AppealResult.failure("Appeal not found");
                }
                
                if (appeal.getStatus() != AppealStatus.UNDER_REVIEW || 
                    !reviewerId.equals(appeal.getReviewerId())) {
                    return AppealResult.failure("You are not authorized to review this appeal");
                }
                
                // Get the original punishment
                List<ModerationHistory> history = repository.getPlayerHistory(appeal.getPlayerId(), 100).join();
                ModerationHistory punishment = history.stream()
                    .filter(h -> appeal.getPunishmentId().equals(h.getId()))
                    .findFirst()
                    .orElse(null);
                
                if (punishment == null) {
                    return AppealResult.failure("Original punishment not found");
                }
                
                // Update appeal status
                appeal.setStatus(approved ? AppealStatus.APPROVED : AppealStatus.DENIED);
                appeal.setReviewedAt(new Date());
                appeal.setResponse(response);
                
                // Update punishment
                punishment.setAppealStatus(approved ? ModerationHistory.AppealStatus.APPROVED : 
                                                    ModerationHistory.AppealStatus.DENIED);
                punishment.setAppealResponse(response);
                punishment.setAppealHandledBy(appeal.getReviewerName());
                punishment.setAppealHandledAt(new Date());
                
                // Process the decision
                if (approved) {
                    processAppealApproval(appeal, punishment);
                } else {
                    processAppealDenial(appeal, punishment);
                }
                
                // Update repository
                repository.updateModerationEntry(punishment);
                
                // Notify player
                notifyPlayerAppealDecision(appeal, approved);
                
                // Update dashboard
                ModerationDashboard.getInstance().recordAppeal(appeal.getPlayerId(), appeal.getPlayerName(),
                    approved ? ModerationHistory.AppealStatus.APPROVED : ModerationHistory.AppealStatus.DENIED);
                
                // Clean up from active appeals
                activeAppeals.remove(appealId);
                List<ObjectId> playerActiveAppeals = playerAppeals.get(appeal.getPlayerId());
                if (playerActiveAppeals != null) {
                    playerActiveAppeals.remove(appealId);
                }
                
                logger.info("Appeal " + appealId + " " + (approved ? "approved" : "denied") + 
                           " by " + appeal.getReviewerName());
                
                return AppealResult.success(appeal, "Appeal " + (approved ? "approved" : "denied"));
                
            } catch (Exception e) {
                logger.severe("Error reviewing appeal: " + e.getMessage());
                return AppealResult.failure("Internal error processing appeal review");
            }
        });
    }
    
    // ==========================================
    // UTILITY METHODS
    // ==========================================
    
    /**
     * Get appeal history for a player
     */
    public List<Appeal> getPlayerAppealHistory(UUID playerId) {
        List<ObjectId> playerAppealIds = playerAppeals.getOrDefault(playerId, new ArrayList<>());
        return playerAppealIds.stream()
            .map(activeAppeals::get)
            .filter(Objects::nonNull)
            .sorted((a, b) -> b.getSubmittedAt().compareTo(a.getSubmittedAt()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get appeals assigned to a staff member
     */
    public List<Appeal> getStaffAppeals(UUID staffId) {
        List<ObjectId> staffAppealIds = staffReviews.getOrDefault(staffId, new ArrayList<>());
        return staffAppealIds.stream()
            .map(activeAppeals::get)
            .filter(Objects::nonNull)
            .filter(appeal -> appeal.getStatus() == AppealStatus.UNDER_REVIEW)
            .sorted((a, b) -> a.getClaimedAt().compareTo(b.getClaimedAt()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get appeal statistics
     */
    public AppealStatistics getAppealStatistics(Date since) {
        // This would typically query a more permanent storage
        // For now, calculate from active appeals
        List<Appeal> allAppeals = new ArrayList<>(activeAppeals.values());
        
        if (since != null) {
            allAppeals = allAppeals.stream()
                .filter(appeal -> appeal.getSubmittedAt().after(since))
                .collect(Collectors.toList());
        }
        
        int totalAppeals = allAppeals.size();
        int approvedAppeals = (int) allAppeals.stream().filter(a -> a.getStatus() == AppealStatus.APPROVED || a.getStatus() == AppealStatus.AUTO_APPROVED).count();
        int deniedAppeals = (int) allAppeals.stream().filter(a -> a.getStatus() == AppealStatus.DENIED).count();
        int pendingAppeals = (int) allAppeals.stream().filter(a -> a.getStatus() == AppealStatus.PENDING || a.getStatus() == AppealStatus.UNDER_REVIEW).count();
        int autoApproved = (int) allAppeals.stream().filter(a -> a.getStatus() == AppealStatus.AUTO_APPROVED).count();
        
        return new AppealStatistics(totalAppeals, approvedAppeals, deniedAppeals, pendingAppeals, autoApproved);
    }
    
    
    private int calculateAppealPriority(ModerationHistory punishment) {
        int priority = 1; // Default priority
        
        // Higher priority for more severe punishments
        switch (punishment.getSeverity()) {
            case CRITICAL:
                priority = 5;
                break;
            case SEVERE:
                priority = 4;
                break;
            case HIGH:
                priority = 3;
                break;
            case MEDIUM:
                priority = 2;
                break;
            case LOW:
                priority = 1;
                break;
        }
        
        // Higher priority for permanent punishments
        if (punishment.isPermanent()) {
            priority += 2;
        }
        
        // Higher priority for longer punishments
        if (punishment.getDurationSeconds() > 7 * 24 * 60 * 60) { // More than 7 days
            priority += 1;
        }
        
        return Math.min(priority, 5); // Cap at 5
    }
    
    private boolean isEligibleForAutoApproval(ModerationHistory punishment) {
        // Auto-approve very old minor punishments
        long punishmentAge = System.currentTimeMillis() - punishment.getTimestamp().getTime();
        
        return punishmentAge > AUTO_APPROVE_THRESHOLD * 1000 &&
               punishment.getSeverity() == ModerationHistory.PunishmentSeverity.LOW &&
               (punishment.getAction() == ModerationHistory.ModerationAction.WARNING ||
                punishment.getAction() == ModerationHistory.ModerationAction.MUTE);
    }
    
    private void processAppealApproval(Appeal appeal, ModerationHistory punishment) {
        // Remove/revoke the punishment
        punishment.setActive(false);
        punishment.setRevokedAt(new Date());
        punishment.setRevokedBy("Appeal System");
        punishment.setRevokeReason("Appeal approved");
        
        // Apply revocation effects
        Player player = Bukkit.getPlayer(appeal.getPlayerId());
        if (player != null) {
            switch (punishment.getAction()) {
                case MUTE:
                    ModerationMechanics.getInstance().unmutePlayer(player, "Appeal System");
                    break;
                case TEMP_BAN:
                case PERMANENT_BAN:
                    ModerationMechanics.getInstance().unbanPlayer(appeal.getPlayerId(), "Appeal System");
                    break;
            }
        }
    }
    
    private void processAppealDenial(Appeal appeal, ModerationHistory punishment) {
        // No action needed for denied appeals, punishment remains active
    }
    
    private void notifyStaffNewAppeal(Appeal appeal, ModerationHistory punishment) {
        String message = ChatColor.BLUE + "[APPEAL] " + ChatColor.YELLOW +
                        "New appeal from " + appeal.getPlayerName() + 
                        " for " + punishment.getAction().name().toLowerCase() + 
                        " (Priority: " + appeal.getPriority() + ")";
        
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakrealms.staff.appeals")) {
                staff.sendMessage(message);
            }
        }
    }
    
    private void notifyPlayerAppealDecision(Appeal appeal, boolean approved) {
        Player player = Bukkit.getPlayer(appeal.getPlayerId());
        if (player != null && player.isOnline()) {
            String decision = approved ? "APPROVED" : "DENIED";
            ChatColor color = approved ? ChatColor.GREEN : ChatColor.RED;
            
            player.sendMessage(color + "Your appeal has been " + decision);
            player.sendMessage(ChatColor.GRAY + "Staff Response: " + appeal.getResponse());
            
            if (approved) {
                player.sendMessage(ChatColor.GREEN + "Your punishment has been revoked.");
            }
        }
    }
    
    // ==========================================
    // HELPER CLASSES
    // ==========================================
    
    public static class Appeal {
        private ObjectId id;
        private UUID playerId;
        private String playerName;
        private ObjectId punishmentId;
        private String reason;
        private List<String> evidence = new ArrayList<>();
        private Date submittedAt;
        private AppealStatus status;
        private int priority;
        private Date deadline;
        
        // Review information
        private UUID reviewerId;
        private String reviewerName;
        private Date claimedAt;
        private Date reviewedAt;
        private String response;
        
        // Getters and setters
        public ObjectId getId() { return id; }
        public void setId(ObjectId id) { this.id = id; }
        public UUID getPlayerId() { return playerId; }
        public void setPlayerId(UUID playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public ObjectId getPunishmentId() { return punishmentId; }
        public void setPunishmentId(ObjectId punishmentId) { this.punishmentId = punishmentId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public List<String> getEvidence() { return evidence; }
        public void setEvidence(List<String> evidence) { this.evidence = evidence; }
        public Date getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }
        public AppealStatus getStatus() { return status; }
        public void setStatus(AppealStatus status) { this.status = status; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public Date getDeadline() { return deadline; }
        public void setDeadline(Date deadline) { this.deadline = deadline; }
        public UUID getReviewerId() { return reviewerId; }
        public void setReviewerId(UUID reviewerId) { this.reviewerId = reviewerId; }
        public String getReviewerName() { return reviewerName; }
        public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
        public Date getClaimedAt() { return claimedAt; }
        public void setClaimedAt(Date claimedAt) { this.claimedAt = claimedAt; }
        public Date getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
    }
    
    public enum AppealStatus {
        PENDING,
        UNDER_REVIEW,
        APPROVED,
        DENIED,
        AUTO_APPROVED,
        WITHDRAWN
    }
    
    public static class AppealResult {
        private final boolean success;
        private final String message;
        private final Appeal appeal;
        
        private AppealResult(boolean success, String message, Appeal appeal) {
            this.success = success;
            this.message = message;
            this.appeal = appeal;
        }
        
        public static AppealResult success(Appeal appeal, String message) {
            return new AppealResult(true, message, appeal);
        }
        
        public static AppealResult failure(String message) {
            return new AppealResult(false, message, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Appeal getAppeal() { return appeal; }
    }
    
    public static class AppealEligibility {
        private final boolean eligible;
        private final String reason;
        private final ModerationHistory punishment;
        
        public AppealEligibility(boolean eligible, String reason, ModerationHistory punishment) {
            this.eligible = eligible;
            this.reason = reason;
            this.punishment = punishment;
        }
        
        public boolean isEligible() { return eligible; }
        public String getReason() { return reason; }
        public ModerationHistory getPunishment() { return punishment; }
    }
    
    public static class AppealStatistics {
        private final int totalAppeals;
        private final int approvedAppeals;
        private final int deniedAppeals;
        private final int pendingAppeals;
        private final int autoApproved;
        
        public AppealStatistics(int totalAppeals, int approvedAppeals, int deniedAppeals, 
                               int pendingAppeals, int autoApproved) {
            this.totalAppeals = totalAppeals;
            this.approvedAppeals = approvedAppeals;
            this.deniedAppeals = deniedAppeals;
            this.pendingAppeals = pendingAppeals;
            this.autoApproved = autoApproved;
        }
        
        public int getTotalAppeals() { return totalAppeals; }
        public int getApprovedAppeals() { return approvedAppeals; }
        public int getDeniedAppeals() { return deniedAppeals; }
        public int getPendingAppeals() { return pendingAppeals; }
        public int getAutoApproved() { return autoApproved; }
        
        public double getApprovalRate() {
            return totalAppeals > 0 ? (double) approvedAppeals / totalAppeals * 100.0 : 0.0;
        }
        
        public double getAutoApprovalRate() {
            return totalAppeals > 0 ? (double) autoApproved / totalAppeals * 100.0 : 0.0;
        }
    }
    
    // Methods for moderation menus
    
    /**
     * Get all appeals (for appeal management menu)
     */
    public List<Appeal> getAllAppeals() {
        try {
            // This would query all appeals from database
            // For now, return empty list as placeholder
            return new ArrayList<>();
        } catch (Exception e) {
            logger.severe("Failed to get all appeals: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}