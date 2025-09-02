package com.rednetty.server.core.mechanics.player.limbo;

import com.rednetty.server.YakRealms;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  LimboManager - Simplified and more reliable limbo system
 * FIXES: Removed complex visual effects, simplified state management, better error handling
 */
public class LimboManager {
    private static volatile LimboManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Simplified constants
    private static final double VOID_LIMBO_Y = -64.0; // Changed to safer Y level
    private static final double VOID_LIMBO_X = 0.5;
    private static final double VOID_LIMBO_Z = 0.5;

    // Visual constants
    private static final int TITLE_FADE_IN_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 30;
    private static final int TITLE_FADE_OUT_TICKS = 15;

    // SIMPLIFIED loading phases
    public enum LoadingPhase {
        JOINING("Connecting", "Establishing connection...", Sound.BLOCK_PORTAL_AMBIENT),
        IN_LIMBO("Loading", "Please wait...", Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD),
        LOADING_DATA("Loading Data", "Retrieving character data...", Sound.BLOCK_ENCHANTMENT_TABLE_USE),
        APPLYING_INVENTORY("Loading Inventory", "Restoring items...", Sound.ITEM_ARMOR_EQUIP_GENERIC),
        APPLYING_STATS("Loading Stats", "Restoring character...", Sound.BLOCK_SPONGE_ABSORB),
        FINALIZING("Finalizing", "Almost ready...", Sound.BLOCK_BEACON_ACTIVATE),
        TELEPORTING("Entering World", "Teleporting...", Sound.ENTITY_ENDERMAN_TELEPORT),
        COMPLETED("Complete", "", Sound.UI_TOAST_CHALLENGE_COMPLETE),
        FAILED("Connection Failed", "Please try again...", Sound.ENTITY_GHAST_SCREAM);

        private final String title;
        private final String subtitle;
        private final Sound sound;

        LoadingPhase(String title, String subtitle, Sound sound) {
            this.title = title;
            this.subtitle = subtitle;
            this.sound = sound;
        }

        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public Sound getSound() { return sound; }
    }

    // Core dependencies
    private final Logger logger;
    private final YakRealms plugin;

    // SIMPLIFIED tracking
    private final Set<UUID> voidLimboPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerLimboStartTime = new ConcurrentHashMap<>();

    // World management
    private volatile World voidLimboWorld;

    // Background tasks - SIMPLIFIED
    private BukkitTask maintenanceTask;

    // State management
    private volatile boolean initialized = false;

    private LimboManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    public static LimboManager getInstance() {
        LimboManager result = instance;
        if (result == null) {
            synchronized (INSTANCE_LOCK) {
                result = instance;
                if (result == null) {
                    instance = result = new LimboManager();
                }
            }
        }
        return result;
    }

    /**
     * SIMPLIFIED Initialize the LimboManager
     */
    public void initialize(World limboWorld) {
        if (initialized) {
            logger.warning("LimboManager already initialized!");
            return;
        }

        try {
            logger.info("Initializing FIXED LimboManager...");

            this.voidLimboWorld = limboWorld;

            if (limboWorld != null) {
                prepareLimboArea();
            }

            startMaintenanceSystem();

            initialized = true;
            logger.info(" LimboManager initialized successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize FIXED LimboManager", e);
            throw new RuntimeException(" LimboManager initialization failed", e);
        }
    }

    /**
     * SIMPLIFIED Create limbo location
     */
    public Location createLimboLocation(World world) {
        try {
            Location location = new Location(world, VOID_LIMBO_X, VOID_LIMBO_Y, VOID_LIMBO_Z);
            location.setYaw(0f);
            location.setPitch(0f); // Changed from 90f to 0f for better player experience
            return location;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating limbo location", e);
            return null;
        }
    }

    /**
     * Check if location is void limbo location
     */
    public boolean isVoidLimboLocation(Location location) {
        if (location == null) return false;
        return Math.abs(location.getX() - VOID_LIMBO_X) < 2.0 &&
                Math.abs(location.getY() - VOID_LIMBO_Y) < 2.0 &&
                Math.abs(location.getZ() - VOID_LIMBO_Z) < 2.0;
    }

    /**
     * Add player to limbo
     */
    public void addPlayerToLimbo(UUID playerId) {
        if (playerId != null) {
            voidLimboPlayers.add(playerId);
            playerLimboStartTime.put(playerId, System.currentTimeMillis());
            logger.fine("Added player to limbo: " + playerId);
        }
    }

    /**
     * Remove player from limbo
     */
    public void removePlayerFromLimbo(UUID playerId) {
        if (playerId != null) {
            voidLimboPlayers.remove(playerId);
            playerLimboStartTime.remove(playerId);
            logger.fine("Removed player from limbo: " + playerId);
        }
    }

    /**
     * Check if player is in limbo
     */
    public boolean isPlayerInLimbo(UUID playerId) {
        return playerId != null && voidLimboPlayers.contains(playerId);
    }

    /**
     * SIMPLIFIED Apply limbo state to player
     */
    public void applyLimboState(Player player) {
        try {
            player.setGameMode(GameMode.ADVENTURE); // Changed from SPECTATOR to ADVENTURE to prevent issues
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setInvulnerable(true);
            clearPlayerInventory(player);

            // Simple entrance sound
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 0.8f);

            logger.fine("Applied limbo state to player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying limbo state to " + player.getName(), e);
        }
    }

    /**
     * SIMPLIFIED Maintain limbo state for player
     */
    public void maintainLimboState(Player player) {
        try {
            UUID playerId = player.getUniqueId();

            if (!isPlayerInLimbo(playerId)) {
                return; // Player not in limbo, nothing to maintain
            }

            Location currentLoc = player.getLocation();
            if (Math.abs(currentLoc.getY() - VOID_LIMBO_Y) > 1.0) {
                Location limboLocation = new Location(voidLimboWorld, VOID_LIMBO_X, VOID_LIMBO_Y, VOID_LIMBO_Z);
                player.teleport(limboLocation);
            }

            // Ensure proper game mode
            if (player.getGameMode() != GameMode.ADVENTURE) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            if (!player.isFlying()) player.setFlying(true);
            if (!player.getAllowFlight()) player.setAllowFlight(true);
            if (!player.isInvulnerable()) player.setInvulnerable(true);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error maintaining limbo state for " + player.getName(), e);
        }
    }

    /**
     * SIMPLIFIED Send limbo welcome message
     */
    public void sendLimboWelcome(Player player) {
        try {
            // Clear chat
            for (int i = 0; i < 10; i++) {
                player.sendMessage("");
            }

            // Simple welcome message
            player.sendMessage("");
            player.sendMessage(TextUtil.getCenteredMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Loading Character"));
            player.sendMessage("");
            player.sendMessage(TextUtil.getCenteredMessage(ChatColor.GRAY + "Please wait while your data loads..."));
            player.sendMessage("");

            // Subtle sound
            player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.2f, 1.0f);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending welcome message to " + player.getName(), e);
        }
    }

    // SIMPLIFIED Visual effects management
    public void startPlayerVisualEffects(Player player, LoadingPhase phase) {
        UUID uuid = player.getUniqueId();

        try {
            // SIMPLIFIED: Just show title and play sound
            showPhaseTitle(player, phase);
            logger.fine("Started visual effects for " + player.getName() + " - Phase: " + phase.name());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error starting visual effects for " + player.getName(), e);
        }
    }

    public void stopPlayerVisualEffects(UUID uuid) {
        // SIMPLIFIED: No complex effects to stop
        logger.fine("Stopped visual effects for " + uuid);
    }

    private void showPhaseTitle(Player player, LoadingPhase phase) {
        try {
            if (phase.getTitle().isEmpty()) return;

            String formattedTitle = ChatColor.AQUA + "" + ChatColor.BOLD + phase.getTitle();
            String formattedSubtitle = ChatColor.GRAY + phase.getSubtitle();

            player.sendTitle(
                    formattedTitle,
                    formattedSubtitle,
                    TITLE_FADE_IN_TICKS,
                    TITLE_STAY_TICKS,
                    TITLE_FADE_OUT_TICKS
            );

            // Play phase sound
            player.playSound(player.getLocation(), phase.getSound(), 0.3f, 1.0f);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error showing phase title for " + player.getName(), e);
        }
    }

    /**
     * SIMPLIFIED Progress indicator - Removed action bar functionality
     */
    public void showProgressIndicator(Player player, LoadingPhase phase) {
        // SIMPLIFIED: No action bar progress
        logger.fine("Progress indicator request for " + player.getName() + " - Phase: " + phase.name());
    }

    public void showProgressIndicator(Player player, LoadingPhase phase, long loadingTime) {
        // SIMPLIFIED: No action bar progress
        logger.fine("Progress indicator request for " + player.getName() + " - Phase: " + phase.name() + " - Time: " + loadingTime + "ms");
    }

    public void forceCompletionProgress(Player player) {
        // SIMPLIFIED: No action bar progress
        logger.fine("Completion progress forced for " + player.getName());
    }

    /**
     * SIMPLIFIED Show completion celebration
     */
    public void showCompletionCelebration(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.0f);

            // Simple title celebration
            player.sendTitle(
                    ChatColor.GREEN + "" + ChatColor.BOLD + "✓ Loading Complete!",
                    ChatColor.YELLOW + "Welcome to YakRealms",
                    10, 40, 20
            );

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in completion celebration for " + player.getName(), e);
        }
    }

    /**
     * SIMPLIFIED Handle emergency recovery for limbo players
     */
    public void performEmergencyRecovery(Player player) {
        try {
            logger.warning("Emergency limbo recovery for " + player.getName());

            UUID uuid = player.getUniqueId();
            removePlayerFromLimbo(uuid);

            player.setInvulnerable(false);

            World recoveryWorld = voidLimboWorld != null ? voidLimboWorld : Bukkit.getWorlds().get(0);
            if (recoveryWorld != null) {
                Location emergencyLoc = recoveryWorld.getSpawnLocation();
                boolean success = player.teleport(emergencyLoc);

                if (success) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setFlying(false);
                    player.setAllowFlight(false);

                    player.sendMessage("");
                    player.sendMessage(TextUtil.getCenteredMessage(ChatColor.GREEN + "Emergency Recovery Activated"));
                    player.sendMessage("");
                    player.sendMessage(TextUtil.getCenteredMessage(ChatColor.GRAY + "Loading failed - you've been moved to spawn."));
                    player.sendMessage(TextUtil.getCenteredMessage(ChatColor.GRAY + "Please reconnect or contact an admin."));
                    player.sendMessage("");

                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.0f);
                }
            }

            logger.info("Emergency recovery completed for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Emergency limbo recovery error for " + player.getName(), e);
        }
    }

    private void clearPlayerInventory(Player player) {
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getEnderChest().clear();
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.updateInventory();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error clearing inventory for " + player.getName(), e);
        }
    }

    private void prepareLimboArea() {
        try {
            if (voidLimboWorld == null) return;

            Location limboLocation = new Location(voidLimboWorld, VOID_LIMBO_X, VOID_LIMBO_Y, VOID_LIMBO_Z);
            Chunk limboChunk = limboLocation.getChunk();

            if (!limboChunk.isLoaded()) {
                limboChunk.load(true);
            }
            limboChunk.setForceLoaded(true);

            logger.info("Limbo area prepared at " + VOID_LIMBO_X + ", " + VOID_LIMBO_Y + ", " + VOID_LIMBO_Z +
                    " in " + voidLimboWorld.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error preparing limbo area", e);
        }
    }

    // SIMPLIFIED maintenance system
    private void startMaintenanceSystem() {
        maintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    performLimboMaintenance();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in limbo maintenance", e);
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // Every 5 seconds

        logger.info("SIMPLIFIED maintenance system started");
    }

    private void performLimboMaintenance() {
        if (voidLimboPlayers.isEmpty()) return;

        try {
            long currentTime = System.currentTimeMillis();

            for (UUID playerId : voidLimboPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    // Maintain limbo state
                    maintainLimboState(player);

                    // Check for stuck players (in limbo for more than 2 minutes)
                    Long startTime = playerLimboStartTime.get(playerId);
                    if (startTime != null && (currentTime - startTime) > 120000) { // 2 minutes
                        logger.warning("Player stuck in limbo for 2+ minutes: " + player.getName());
                        performEmergencyRecovery(player);
                    }
                } else {
                    // Player offline, clean up
                    removePlayerFromLimbo(playerId);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in limbo maintenance", e);
        }
    }

    /**
     * SIMPLIFIED Shutdown the LimboManager
     */
    public void shutdown() {
        try {
            logger.info("Shutting down FIXED LimboManager...");

            if (maintenanceTask != null && !maintenanceTask.isCancelled()) {
                maintenanceTask.cancel();
            }

            // Move all limbo players to safety
            for (UUID playerId : voidLimboPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendTitle(
                            ChatColor.YELLOW + "Server Restart",
                            ChatColor.GRAY + "Returning to spawn...",
                            10, 30, 15
                    );

                    if (voidLimboWorld != null) {
                        player.teleport(voidLimboWorld.getSpawnLocation());
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setInvulnerable(false);
                        player.setFlying(false);
                        player.setAllowFlight(false);
                        logger.info("Safely moved " + player.getName() + " from limbo to spawn");
                    }
                }
            }

            voidLimboPlayers.clear();
            playerLimboStartTime.clear();

            initialized = false;
            logger.info(" LimboManager shutdown completed");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during FIXED LimboManager shutdown", e);
        }
    }

    // Public API methods
    public int getLimboPlayersCount() {
        return voidLimboPlayers.size();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public World getLimboWorld() {
        return voidLimboWorld;
    }

    public long getPlayerLimboTime(UUID playerId) {
        Long startTime = playerLimboStartTime.get(playerId);
        if (startTime != null) {
            return System.currentTimeMillis() - startTime;
        }
        return 0;
    }
}