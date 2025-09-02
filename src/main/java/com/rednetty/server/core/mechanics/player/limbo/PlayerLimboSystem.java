package com.rednetty.server.core.mechanics.player.limbo;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Comprehensive limbo system that prevents players from moving or interacting
 * before their data is fully loaded and validated.
 * 
 * Features:
 * - Movement restriction during data loading
 * - Visual feedback with progress indicators
 * - Automatic timeout handling
 * - Graceful error recovery
 * - Integration with moderation system
 * - Performance monitoring
 */
public class PlayerLimboSystem implements Listener {
    
    private static PlayerLimboSystem instance;
    private final Logger logger;
    private final YakPlayerManager playerManager;
    private final ModerationMechanics moderationMechanics;
    
    // Limbo tracking
    private final Map<UUID, LimboSession> limboSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> originalLocations = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int LIMBO_TIMEOUT_SECONDS = 30; // 30 second timeout
    private static final int PROGRESS_UPDATE_INTERVAL = 20; // 1 second
    private static final int MAX_LOADING_ATTEMPTS = 3;
    
    // Statistics
    private final AtomicInteger totalLimboSessions = new AtomicInteger(0);
    private final AtomicInteger successfulLoads = new AtomicInteger(0);
    private final AtomicInteger timeoutOccurrences = new AtomicInteger(0);
    private final AtomicInteger errorOccurrences = new AtomicInteger(0);
    
    private PlayerLimboSystem() {
        this.logger = YakRealms.getInstance().getLogger();
        this.playerManager = YakPlayerManager.getInstance();
        this.moderationMechanics = ModerationMechanics.getInstance();
    }
    
    public static PlayerLimboSystem getInstance() {
        if (instance == null) {
            instance = new PlayerLimboSystem();
        }
        return instance;
    }
    
    /**
     * Initialize the limbo system
     */
    public void initialize() {
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
        
        logger.info("Player Limbo System initialized successfully");
    }
    
    /**
     * Shutdown the limbo system
     */
    public void shutdown() {
        // Cancel all timeout tasks
        for (BukkitTask task : timeoutTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        
        // Release all players from limbo
        for (UUID playerId : limboSessions.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                releaseFromLimbo(player, "Server shutdown");
            }
        }
        
        // Clear caches
        limboSessions.clear();
        timeoutTasks.clear();
        originalLocations.clear();
        
        logger.info("Player Limbo System shutdown complete");
    }
    
    // ==========================================
    // LIMBO MANAGEMENT
    // ==========================================
    
    /**
     * Put a player in limbo while their data loads
     */
    public void enterLimbo(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        
        // Don't put the same player in limbo twice
        if (limboSessions.containsKey(playerId)) {
            return;
        }
        
        logger.info("Putting player " + player.getName() + " into limbo: " + reason);
        
        // Store original location
        originalLocations.put(playerId, player.getLocation().clone());
        
        // Create limbo session
        LimboSession session = new LimboSession(playerId, player.getName(), reason);
        limboSessions.put(playerId, session);
        totalLimboSessions.incrementAndGet();
        
        // Apply limbo restrictions
        applyLimboRestrictions(player);
        
        // Start progress updates
        startProgressUpdates(player, session);
        
        // Start timeout timer
        startTimeoutTimer(player);
        
        // Notify player with enhanced loading message
        sendLimboMessage(player, "Loading your player data, please wait...");
        sendLimboMessage(player, "§8Your game experience will begin shortly!");
        sendLimboMessage(player, "§8Loading: Player Stats, Inventory, Economy Data...");
    }
    
    /**
     * Trigger MOTD sending after limbo release
     */
    private void triggerMOTDSending(Player player) {
        try {
            // Use the YakPlayerManager's join event coordination
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                // Trigger immediate MOTD through a custom approach
                Bukkit.getPluginManager().callEvent(new PlayerDataLoadedEvent(player, yakPlayer));
                logger.info("MOTD triggered immediately for " + player.getName() + " after limbo release");
            }
        } catch (Exception e) {
            logger.warning("Failed to trigger MOTD for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Release a player from limbo
     */
    public void releaseFromLimbo(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        
        LimboSession session = limboSessions.remove(playerId);
        if (session == null) {
            return; // Not in limbo
        }
        
        logger.info("Releasing player " + player.getName() + " from limbo: " + reason);
        
        // Cancel timeout task
        BukkitTask timeoutTask = timeoutTasks.remove(playerId);
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        
        // Remove limbo restrictions
        removeLimboRestrictions(player);
        
        // Restore original location if needed
        Location originalLocation = originalLocations.remove(playerId);
        if (originalLocation != null && player.isOnline()) {
            // Only teleport if they haven't moved significantly (anti-exploit)
            if (player.getLocation().distance(originalLocation) < 5.0) {
                player.teleport(originalLocation);
            }
        }
        
        // Update statistics
        if (reason.contains("success") || reason.contains("complete")) {
            successfulLoads.incrementAndGet();
        } else if (reason.contains("timeout")) {
            timeoutOccurrences.incrementAndGet();
        } else if (reason.contains("error")) {
            errorOccurrences.incrementAndGet();
        }
        
        // Notify player
        if (player.isOnline()) {
            if (reason.contains("success") || reason.contains("complete")) {
                sendLimboMessage(player, ChatColor.GREEN + "✓ Data loaded successfully! Welcome to YakRealms!");
            } else {
                sendLimboMessage(player, ChatColor.RED + "Loading failed: " + reason);
            }
        }
        
        // Log session duration
        long duration = System.currentTimeMillis() - session.getStartTime();
        logger.info("Limbo session for " + player.getName() + " lasted " + duration + "ms");
    }
    
    /**
     * Check if a player is in limbo
     */
    public boolean isInLimbo(UUID playerId) {
        return limboSessions.containsKey(playerId);
    }
    
    /**
     * Get limbo session information
     */
    public LimboSession getLimboSession(UUID playerId) {
        return limboSessions.get(playerId);
    }
    
    // ==========================================
    // RESTRICTION MANAGEMENT
    // ==========================================
    
    /**
     * Apply all limbo restrictions to a player
     */
    private void applyLimboRestrictions(Player player) {
        // Freeze movement
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        
        // Prevent interaction
        player.setGameMode(GameMode.ADVENTURE);
        
        // Apply visual effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 255, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, -10, true, false));
        
        // Prevent damage
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 255, true, false));
        
        // Make invulnerable
        if (player.isInvulnerable() != true) {
            player.setInvulnerable(true);
        }
    }
    
    /**
     * Remove all limbo restrictions from a player
     */
    private void removeLimboRestrictions(Player player) {
        // Restore movement
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        
        // Remove all potion effects applied by limbo
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        
        // Restore vulnerability
        player.setInvulnerable(false);
        
        // Restore appropriate game mode based on rank/permissions
        GameMode appropriateGameMode = determineAppropriateGameMode(player);
        player.setGameMode(appropriateGameMode);
    }
    
    /**
     * Determine the appropriate game mode for a player
     */
    private GameMode determineAppropriateGameMode(Player player) {
        // Check if player is staff (they might get creative)
        if (ModerationMechanics.isStaff(player) && player.hasPermission("yakrealms.gamemode.creative")) {
            return GameMode.CREATIVE;
        }
        
        // Check for spectator permissions
        if (player.hasPermission("yakrealms.gamemode.spectator")) {
            return GameMode.SPECTATOR;
        }
        
        // Default to survival
        return GameMode.SURVIVAL;
    }
    
    // ==========================================
    // PROGRESS AND FEEDBACK
    // ==========================================
    
    /**
     * Start sending progress updates to the player
     */
    private void startProgressUpdates(Player player, LimboSession session) {
        BukkitTask progressTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            if (!player.isOnline() || !limboSessions.containsKey(player.getUniqueId())) {
                return;
            }
            
            long elapsed = System.currentTimeMillis() - session.getStartTime();
            String progressMessage = createProgressMessage(elapsed, session.getReason());
            
            // Send as action bar for less intrusive display
            player.sendTitle("", progressMessage, 0, 25, 5);
            
        }, 0L, PROGRESS_UPDATE_INTERVAL);
        
        // Store task for cleanup
        session.setProgressTask(progressTask);
    }
    
    /**
     * Create a progress message with loading animation
     */
    private String createProgressMessage(long elapsedMs, String reason) {
        int dots = (int) ((elapsedMs / 500) % 4); // Animate dots every 500ms
        StringBuilder animation = new StringBuilder();
        
        for (int i = 0; i < 3; i++) {
            if (i < dots) {
                animation.append(ChatColor.YELLOW).append("●");
            } else {
                animation.append(ChatColor.DARK_GRAY).append("○");
            }
        }
        
        // Add progress stages for better user feedback
        String stage = "Loading Data";
        if (elapsedMs > 5000) {
            stage = "Finalizing";
        } else if (elapsedMs > 3000) {
            stage = "Processing";
        } else if (elapsedMs > 1000) {
            stage = "Validating";
        }
        
        return ChatColor.AQUA + stage + " " + animation.toString() + ChatColor.GRAY + " Please wait...";
    }
    
    /**
     * Send a limbo-specific message to the player
     */
    private void sendLimboMessage(Player player, String message) {
        player.sendMessage(ChatColor.GRAY + "[" + ChatColor.BLUE + "Loading" + ChatColor.GRAY + "] " + 
                          ChatColor.WHITE + message);
    }
    
    // ==========================================
    // TIMEOUT HANDLING
    // ==========================================
    
    /**
     * Start a timeout timer for a player in limbo
     */
    private void startTimeoutTimer(Player player) {
        UUID playerId = player.getUniqueId();
        
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (limboSessions.containsKey(playerId)) {
                handleLimboTimeout(player);
            }
        }, LIMBO_TIMEOUT_SECONDS * 20L);
        
        timeoutTasks.put(playerId, timeoutTask);
    }
    
    /**
     * Handle when a player's limbo session times out
     */
    private void handleLimboTimeout(Player player) {
        logger.warning("Limbo timeout for player " + player.getName());
        
        // Try to force-load the player data
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            // Data was loaded, release from limbo
            releaseFromLimbo(player, "Data loading completed after timeout");
        } else {
            // Still no data, kick the player with explanation
            String kickMessage = ChatColor.RED + "Failed to load your player data.\n" +
                                ChatColor.YELLOW + "This may be a temporary issue.\n" +
                                ChatColor.WHITE + "Please try reconnecting in a few moments.";
            
            player.kickPlayer(kickMessage);
            
            // Clean up limbo session
            releaseFromLimbo(player, "Timeout - player kicked");
        }
    }
    
    // ==========================================
    // EVENT HANDLERS
    // ==========================================
    
    /**
     * Handle player join - immediately put in limbo
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Put player in limbo immediately
        enterLimbo(player, "Loading player data");
        
        // Start async data loading process
        Bukkit.getScheduler().runTaskAsynchronously(YakRealms.getInstance(), () -> {
            loadPlayerData(player);
        });
    }
    
    /**
     * Handle player quit - cleanup limbo session
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Clean up limbo session
        if (limboSessions.containsKey(playerId)) {
            releaseFromLimbo(player, "Player disconnected");
        }
    }
    
    /**
     * Prevent movement while in limbo
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isInLimbo(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent interaction while in limbo
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isInLimbo(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent item dropping while in limbo
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isInLimbo(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent damage while in limbo
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isInLimbo(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
    
    /**
     * Prevent command execution while in limbo (except essential commands)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (isInLimbo(event.getPlayer().getUniqueId())) {
            String command = event.getMessage().toLowerCase();
            
            // Allow essential commands
            if (command.startsWith("/login") || command.startsWith("/register") || 
                command.startsWith("/help") || command.startsWith("/rules")) {
                return;
            }
            
            event.setCancelled(true);
            sendLimboMessage(event.getPlayer(), "Please wait for data loading to complete...");
        }
    }
    
    // ==========================================
    // DATA LOADING
    // ==========================================
    
    /**
     * Asynchronously load player data with retry logic
     */
    private void loadPlayerData(Player player) {
        UUID playerId = player.getUniqueId();
        int attempts = 0;
        boolean success = false;
        
        while (attempts < MAX_LOADING_ATTEMPTS && !success) {
            attempts++;
            
            try {
                // Attempt to load player data using public API
                YakPlayer yakPlayer = playerManager.getPlayer(player.getUniqueId());
                if (yakPlayer == null) {
                    // Player will be created automatically by the manager
                    yakPlayer = playerManager.getPlayer(player.getUniqueId());
                }
                
                if (yakPlayer != null) {
                    // Initialize moderation data - use public method or skip
                    // moderationMechanics.initializePlayer(player); // Skip private method
                    
                    // Data loaded successfully
                    success = true;
                    
                    // Release from limbo on main thread with proper MOTD coordination
                    Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                        if (player.isOnline()) {
                            releaseFromLimbo(player, "Data loading successful");
                            
                            // Trigger MOTD sending immediately after limbo release
                            // This ensures the player sees the MOTD as soon as they can interact
                            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                                if (player.isOnline()) {
                                    // Fire a custom event to notify JoinLeaveListener to send MOTD
                                    triggerMOTDSending(player);
                                }
                            }, 5L); // Very brief delay to ensure limbo restrictions are fully removed
                        }
                    });
                    
                } else if (attempts < MAX_LOADING_ATTEMPTS) {
                    // Wait before retry
                    Thread.sleep(1000 * attempts); // Progressive delay
                }
                
            } catch (Exception e) {
                logger.warning("Attempt " + attempts + " failed to load data for " + 
                             player.getName() + ": " + e.getMessage());
                
                if (attempts < MAX_LOADING_ATTEMPTS) {
                    try {
                        Thread.sleep(2000 * attempts); // Progressive delay
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        if (!success) {
            // All attempts failed
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    releaseFromLimbo(player, "Data loading failed - all attempts exhausted");
                    
                    String kickMessage = ChatColor.RED + "Failed to load your player data after multiple attempts.\n" +
                                       ChatColor.YELLOW + "Please contact an administrator if this persists.\n" +
                                       ChatColor.GRAY + "Error details have been logged.";
                    
                    player.kickPlayer(kickMessage);
                }
            });
        }
    }
    
    // ==========================================
    // STATISTICS AND MONITORING
    // ==========================================
    
    /**
     * Get limbo system statistics
     */
    public LimboStatistics getStatistics() {
        return new LimboStatistics(
            totalLimboSessions.get(),
            successfulLoads.get(),
            timeoutOccurrences.get(),
            errorOccurrences.get(),
            limboSessions.size()
        );
    }
    
    /**
     * Get current limbo sessions
     */
    public Map<UUID, LimboSession> getCurrentSessions() {
        return new ConcurrentHashMap<>(limboSessions);
    }
    
    // ==========================================
    // HELPER CLASSES
    // ==========================================
    
    /**
     * Represents a player's limbo session
     */
    public static class LimboSession {
        private final UUID playerId;
        private final String playerName;
        private final String reason;
        private final long startTime;
        private BukkitTask progressTask;
        
        public LimboSession(UUID playerId, String playerName, String reason) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.reason = reason;
            this.startTime = System.currentTimeMillis();
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public String getReason() { return reason; }
        public long getStartTime() { return startTime; }
        public long getDuration() { return System.currentTimeMillis() - startTime; }
        
        // Progress task management
        public void setProgressTask(BukkitTask progressTask) { 
            // Cancel existing task if any
            if (this.progressTask != null && !this.progressTask.isCancelled()) {
                this.progressTask.cancel();
            }
            this.progressTask = progressTask; 
        }
        
        public void cleanup() {
            if (progressTask != null && !progressTask.isCancelled()) {
                progressTask.cancel();
            }
        }
    }
    
    /**
     * Statistics for the limbo system
     */
    public static class LimboStatistics {
        private final int totalSessions;
        private final int successfulLoads;
        private final int timeouts;
        private final int errors;
        private final int currentSessions;
        
        public LimboStatistics(int totalSessions, int successfulLoads, int timeouts, 
                              int errors, int currentSessions) {
            this.totalSessions = totalSessions;
            this.successfulLoads = successfulLoads;
            this.timeouts = timeouts;
            this.errors = errors;
            this.currentSessions = currentSessions;
        }
        
        // Getters
        public int getTotalSessions() { return totalSessions; }
        public int getSuccessfulLoads() { return successfulLoads; }
        public int getTimeouts() { return timeouts; }
        public int getErrors() { return errors; }
        public int getCurrentSessions() { return currentSessions; }
        
        public double getSuccessRate() {
            return totalSessions > 0 ? (double) successfulLoads / totalSessions * 100.0 : 0.0;
        }
        
        public double getTimeoutRate() {
            return totalSessions > 0 ? (double) timeouts / totalSessions * 100.0 : 0.0;
        }
        
        public double getErrorRate() {
            return totalSessions > 0 ? (double) errors / totalSessions * 100.0 : 0.0;
        }
        
        public String getSummary() {
            return String.format(
                "Limbo Statistics:\n" +
                "├─ Total Sessions: %d\n" +
                "├─ Success Rate: %.1f%% (%d successful)\n" +
                "├─ Timeout Rate: %.1f%% (%d timeouts)\n" +
                "├─ Error Rate: %.1f%% (%d errors)\n" +
                "└─ Current Sessions: %d",
                totalSessions, getSuccessRate(), successfulLoads,
                getTimeoutRate(), timeouts, getErrorRate(), errors,
                currentSessions
            );
        }
    }
    
    /**
     * Custom event to signal that player data has been loaded and MOTD should be sent
     */
    public static class PlayerDataLoadedEvent extends org.bukkit.event.Event {
        private static final org.bukkit.event.HandlerList handlers = new org.bukkit.event.HandlerList();
        private final Player player;
        private final YakPlayer yakPlayer;
        
        public PlayerDataLoadedEvent(Player player, YakPlayer yakPlayer) {
            this.player = player;
            this.yakPlayer = yakPlayer;
        }
        
        public Player getPlayer() { return player; }
        public YakPlayer getYakPlayer() { return yakPlayer; }
        
        @Override
        public org.bukkit.event.HandlerList getHandlers() { return handlers; }
        public static org.bukkit.event.HandlerList getHandlerList() { return handlers; }
    }
}