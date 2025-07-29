package com.rednetty.server.mechanics.player.limbo;

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
 * LimboManager - Handles void limbo visual effects and player management
 *
 * RESPONSIBILITIES:
 * - Manage visual effects during player loading
 * - Handle void limbo teleportation and state management
 * - Coordinate particle effects and loading experience
 * - Provide smooth loading experience with progress indicators
 * - Handle cleanup and emergency recovery for limbo players
 */
public class LimboManager {
    private static volatile LimboManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Void limbo constants
    private static final double VOID_LIMBO_Y = -114.0;
    private static final double VOID_LIMBO_X = 0.5;
    private static final double VOID_LIMBO_Z = 0.5;

    // Visual constants
    private static final int PARTICLE_DENSITY = 6;
    private static final double PARTICLE_RADIUS = 1.5;
    private static final int TITLE_FADE_IN_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 30;
    private static final int TITLE_FADE_OUT_TICKS = 15;

    // Loading phases with simplified visuals
    public enum LoadingPhase {
        JOINING("Connecting", "Establishing connection...", Particle.PORTAL, Sound.BLOCK_PORTAL_AMBIENT),
        IN_LIMBO("Loading", "Please wait...", Particle.SPELL_WITCH, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD),
        LOADING_DATA("Loading Data", "Retrieving character data...", Particle.ENCHANTMENT_TABLE, Sound.BLOCK_ENCHANTMENT_TABLE_USE),
        APPLYING_INVENTORY("Loading Inventory", "Restoring items...", Particle.ITEM_CRACK, Sound.ITEM_ARMOR_EQUIP_GENERIC),
        APPLYING_STATS("Loading Stats", "Restoring character...", Particle.CRIT_MAGIC, Sound.BLOCK_SPONGE_ABSORB),
        FINALIZING("Finalizing", "Almost ready...", Particle.END_ROD, Sound.BLOCK_BEACON_ACTIVATE),
        TELEPORTING("Entering World", "Teleporting...", Particle.DRAGON_BREATH, Sound.ENTITY_ENDERMAN_TELEPORT),
        COMPLETED("Complete", "", Particle.VILLAGER_HAPPY, Sound.UI_TOAST_CHALLENGE_COMPLETE),
        FAILED("Connection Failed", "Please try again...", Particle.SMOKE_LARGE, Sound.ENTITY_GHAST_SCREAM);

        private final String title;
        private final String subtitle;
        private final Particle particle;
        private final Sound sound;

        LoadingPhase(String title, String subtitle, Particle particle, Sound sound) {
            this.title = title;
            this.subtitle = subtitle;
            this.particle = particle;
            this.sound = sound;
        }

        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public Particle getParticle() { return particle; }
        public Sound getSound() { return sound; }
    }

    // Core dependencies
    private final Logger logger;
    private final YakRealms plugin;

    // Visual effect tracking
    private final Map<UUID, LimboVisualEffects> playerVisualEffects = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> effectTasks = new ConcurrentHashMap<>();
    private final Set<UUID> voidLimboPlayers = ConcurrentHashMap.newKeySet();

    // World management
    private volatile World voidLimboWorld;

    // Background tasks
    private BukkitTask ambientEffectsTask;

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
     * Initialize the LimboManager
     */
    public void initialize(World limboWorld) {
        if (initialized) {
            logger.warning("LimboManager already initialized!");
            return;
        }

        try {
            logger.info("Initializing LimboManager...");

            this.voidLimboWorld = limboWorld;

            if (limboWorld != null) {
                prepareLimboArea();
            }

            startVisualSystems();

            initialized = true;
            logger.info("LimboManager initialized successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize LimboManager", e);
            throw new RuntimeException("LimboManager initialization failed", e);
        }
    }

    /**
     * Create limbo location
     */
    public Location createLimboLocation(World world) {
        try {
            Location location = new Location(world, VOID_LIMBO_X, VOID_LIMBO_Y, VOID_LIMBO_Z);
            location.setYaw(0f);
            location.setPitch(90f);
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
        voidLimboPlayers.add(playerId);
    }

    /**
     * Remove player from limbo
     */
    public void removePlayerFromLimbo(UUID playerId) {
        voidLimboPlayers.remove(playerId);
        stopPlayerVisualEffects(playerId);
    }

    /**
     * Check if player is in limbo
     */
    public boolean isPlayerInLimbo(UUID playerId) {
        return voidLimboPlayers.contains(playerId);
    }

    /**
     * Apply limbo state to player
     */
    public void applyLimboState(Player player) {
        try {
            player.setGameMode(GameMode.SPECTATOR);
            player.setFlying(true);
            player.setAllowFlight(true);
            player.setInvulnerable(true);
            clearPlayerInventory(player);

            // Simple entrance sound
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 0.8f);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying limbo state", e);
        }
    }

    /**
     * Maintain limbo state for player
     */
    public void maintainLimboState(Player player) {
        try {
            Location currentLoc = player.getLocation();
            if (Math.abs(currentLoc.getY() - VOID_LIMBO_Y) > 1.0) {
                Location limboLocation = new Location(voidLimboWorld, VOID_LIMBO_X, VOID_LIMBO_Y, VOID_LIMBO_Z);
                player.teleport(limboLocation);
            }

            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            if (!player.isFlying()) player.setFlying(true);
            if (!player.getAllowFlight()) player.setAllowFlight(true);
            if (!player.isInvulnerable()) player.setInvulnerable(true);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error maintaining limbo state", e);
        }
    }

    /**
     * Send simplified limbo welcome message
     */
    public void sendLimboWelcome(Player player) {
        try {
            // Clear chat
            for (int i = 0; i < 15; i++) {
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
            logger.log(Level.WARNING, "Error sending welcome message", e);
        }
    }

    // Visual effects management
    public void startPlayerVisualEffects(Player player, LoadingPhase phase) {
        UUID uuid = player.getUniqueId();

        // Stop any existing effects
        stopPlayerVisualEffects(uuid);

        // Create new visual effects context
        LimboVisualEffects effects = new LimboVisualEffects(player, phase);
        playerVisualEffects.put(uuid, effects);

        // Start combined effects task
        startCombinedEffects(player, phase);

        // Show title
        showPhaseTitle(player, phase);

        logger.fine("Started visual effects for " + player.getName() + " - Phase: " + phase.name());
    }

    public void stopPlayerVisualEffects(UUID uuid) {
        try {
            // Stop effect task
            BukkitTask effectTask = effectTasks.remove(uuid);
            if (effectTask != null && !effectTask.isCancelled()) {
                effectTask.cancel();
            }

            // Remove visual effects context
            playerVisualEffects.remove(uuid);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error stopping visual effects for " + uuid, e);
        }
    }

    private void startCombinedEffects(Player player, LoadingPhase phase) {
        UUID uuid = player.getUniqueId();

        BukkitTask effectTask = new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !voidLimboPlayers.contains(uuid)) {
                    this.cancel();
                    return;
                }

                try {
                    // Create particles every 3 ticks
                    if (tick % 3 == 0) {
                        createPhaseParticles(player, phase, tick);
                    }

                    // Play ambient sound every 600 ticks (30 seconds)
                    if (tick % 600 == 0 && tick > 0) {
                        player.playSound(player.getLocation(), phase.getSound(), 0.1f, 1.0f);
                    }

                    tick++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in combined effects for " + player.getName(), e);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        effectTasks.put(uuid, effectTask);
    }

    private void createPhaseParticles(Player player, LoadingPhase phase, int tick) {
        Location playerLoc = player.getLocation();

        switch (phase) {
            case JOINING:
            case TELEPORTING:
                createPortalEffect(playerLoc, tick);
                break;
            case IN_LIMBO:
                createVoidEffect(playerLoc, tick);
                break;
            case LOADING_DATA:
            case FINALIZING:
                createDataEffect(playerLoc, tick);
                break;
            case APPLYING_INVENTORY:
                createItemEffect(playerLoc, tick);
                break;
            case APPLYING_STATS:
                createStatEffect(playerLoc, tick);
                break;
            case COMPLETED:
                createSuccessEffect(playerLoc, tick);
                break;
            case FAILED:
                createErrorEffect(playerLoc, tick);
                break;
        }
    }

    // Simplified particle effect methods
    private void createPortalEffect(Location center, int tick) {
        double radius = 1.5 + Math.sin(tick * 0.1) * 0.3;
        for (int i = 0; i < 6; i++) {
            double angle = (tick + i * 60) * 0.2;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY() + Math.sin(tick * 0.05 + i) * 0.2;

            Location particleLoc = new Location(center.getWorld(), x, y, z);
            center.getWorld().spawnParticle(Particle.PORTAL, particleLoc, 1, 0, 0, 0, 0.1);
        }
    }

    private void createVoidEffect(Location center, int tick) {
        for (int i = 0; i < 4; i++) {
            double angle = (tick + i * 90) * 0.03;
            double radius = 1.2 + Math.sin(angle) * 0.3;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY() + Math.sin(tick * 0.04 + i) * 0.5;

            Location particleLoc = new Location(center.getWorld(), x, y, z);
            center.getWorld().spawnParticle(Particle.SPELL_WITCH, particleLoc, 1, 0, 0, 0, 0);

            if (tick % 40 == i * 10) {
                center.getWorld().spawnParticle(Particle.ASH, particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    private void createDataEffect(Location center, int tick) {
        for (int i = 0; i < 8; i++) {
            double angle = i * 45;
            double radius = 1.0;
            double x = center.getX() + Math.cos(Math.toRadians(angle)) * radius;
            double z = center.getZ() + Math.sin(Math.toRadians(angle)) * radius;
            double y = center.getY() - 0.5 + ((tick + i * 5) % 30) * 0.05;

            Location streamLoc = new Location(center.getWorld(), x, y, z);
            center.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, streamLoc, 1, 0, 0, 0, 0.3);
        }
    }

    private void createItemEffect(Location center, int tick) {
        double phase = tick * 0.1;
        for (int i = 0; i < 6; i++) {
            double angle = i * 60 + phase;
            double radius = 1.3;
            double x = center.getX() + Math.cos(Math.toRadians(angle)) * radius;
            double z = center.getZ() + Math.sin(Math.toRadians(angle)) * radius;
            double y = center.getY() + Math.sin(phase + i) * 0.3;

            Location itemLoc = new Location(center.getWorld(), x, y, z);
            center.getWorld().spawnParticle(Particle.ITEM_CRACK, itemLoc, 1, 0.1, 0.1, 0.1, 0.1,
                    new ItemStack(Material.DIAMOND));
        }
    }

    private void createStatEffect(Location center, int tick) {
        double intensity = Math.sin(tick * 0.08) + 1.0;
        for (int i = 0; i < 9; i++) {
            double angle = tick + i * 40;
            double radius = intensity * 0.6;
            double x = center.getX() + Math.cos(Math.toRadians(angle)) * radius;
            double z = center.getZ() + Math.sin(Math.toRadians(angle)) * radius;
            double y = center.getY() + Math.sin(tick * 0.04 + i) * 0.3;

            Location powerLoc = new Location(center.getWorld(), x, y, z);
            center.getWorld().spawnParticle(Particle.CRIT_MAGIC, powerLoc, 1, 0, 0, 0, 0.1);
        }
    }

    private void createSuccessEffect(Location center, int tick) {
        if (tick % 15 == 0) {
            center.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, center, 5, 0.5, 0.5, 0.5, 0.2);
        }

        for (int i = 0; i < 6; i++) {
            double angle = tick * 3 + i * 60;
            double radius = 1.0;
            double x = center.getX() + Math.cos(Math.toRadians(angle)) * radius;
            double z = center.getZ() + Math.sin(Math.toRadians(angle)) * radius;
            double y = center.getY() + 0.3 + Math.sin(tick * 0.1 + i) * 0.4;

            Location celebLoc = new Location(center.getWorld(), x, y, z);
            center.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, celebLoc, 1, 0, 0, 0, 0.1);
        }
    }

    private void createErrorEffect(Location center, int tick) {
        if (tick % 8 == 0) {
            for (int i = 0; i < 3; i++) {
                Location errorLoc = center.clone().add(
                        (Math.random() - 0.5) * 1.5,
                        (Math.random() - 0.5) * 1.5,
                        (Math.random() - 0.5) * 1.5
                );
                center.getWorld().spawnParticle(Particle.SMOKE_LARGE, errorLoc, 1, 0, 0, 0, 0.05);
            }
        }
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
            logger.log(Level.WARNING, "Error showing phase title", e);
        }
    }

    /**
     * Show progress indicator - Action bar functionality removed
     * Maintained for API compatibility
     */
    public void showProgressIndicator(Player player, LoadingPhase phase) {
        // Action bar functionality removed - method maintained for API compatibility
        logger.fine("Progress indicator request for " + player.getName() + " - Phase: " + phase.name());
    }

    /**
     * Show progress indicator - Action bar functionality removed
     * Maintained for API compatibility
     */
    public void showProgressIndicator(Player player, LoadingPhase phase, long loadingTime) {
        // Action bar functionality removed - method maintained for API compatibility
        logger.fine("Progress indicator request for " + player.getName() + " - Phase: " + phase.name() + " - Time: " + loadingTime + "ms");
    }

    /**
     * Force completion progress - Action bar functionality removed
     * Maintained for API compatibility
     */
    public void forceCompletionProgress(Player player) {
        // Action bar functionality removed - method maintained for API compatibility
        logger.fine("Completion progress forced for " + player.getName());
    }

    /**
     * Show completion celebration without action bar progress
     */
    public void showCompletionCelebration(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.0f);

            Location playerLoc = player.getLocation();
            for (int i = 0; i < 10; i++) {
                double angle = i * 36;
                double radius = 1.5;
                double x = playerLoc.getX() + Math.cos(Math.toRadians(angle)) * radius;
                double z = playerLoc.getZ() + Math.sin(Math.toRadians(angle)) * radius;
                double y = playerLoc.getY() + 0.5;

                Location celebLoc = new Location(playerLoc.getWorld(), x, y, z);
                playerLoc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, celebLoc, 1, 0, 0, 0, 0.1);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in completion celebration", e);
        }
    }

    /**
     * Handle emergency recovery for limbo players
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
                    player.setHealth(20.0);
                    player.setFoodLevel(20);

                    player.sendMessage("");
                    player.sendMessage(TextUtil.getCenteredMessage(ChatColor.RED + "Emergency Recovery Activated"));
                    player.sendMessage("");
                    player.sendMessage(TextUtil.getCenteredMessage(ChatColor.GRAY + "Loading failed - you've been moved to spawn."));
                    player.sendMessage(TextUtil.getCenteredMessage(ChatColor.GRAY + "Please reconnect or contact an admin."));
                    player.sendMessage("");

                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.0f);
                }
            }

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
            logger.log(Level.WARNING, "Error clearing inventory", e);
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

    private void startVisualSystems() {
        ambientEffectsTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    maintainGlobalAmbientEffects();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in global ambient effects", e);
                }
            }
        }.runTaskTimer(plugin, 100L, 60L); // Every 3 seconds

        logger.info("Visual effect systems started");
    }

    private void maintainGlobalAmbientEffects() {
        if (voidLimboPlayers.isEmpty()) return;

        try {
            for (UUID playerId : voidLimboPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    createGlobalVoidAmbience(player.getLocation());
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in global ambient effects", e);
        }
    }

    private void createGlobalVoidAmbience(Location center) {
        try {
            for (int i = 0; i < 3; i++) {
                Location ambientLoc = center.clone().add(
                        (Math.random() - 0.5) * 6,
                        (Math.random() - 0.5) * 3,
                        (Math.random() - 0.5) * 6
                );

                if (Math.random() < 0.2) {
                    center.getWorld().spawnParticle(Particle.ASH, ambientLoc, 1, 0, 0, 0, 0.01);
                }
            }
        } catch (Exception e) {
            // Ignore particle errors
        }
    }

    /**
     * Shutdown the LimboManager
     */
    public void shutdown() {
        try {
            logger.info("Shutting down LimboManager...");

            for (UUID playerId : voidLimboPlayers) {
                stopPlayerVisualEffects(playerId);
            }

            if (ambientEffectsTask != null && !ambientEffectsTask.isCancelled()) {
                ambientEffectsTask.cancel();
            }

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
                        logger.info("Safely moved " + player.getName() + " from limbo to spawn");
                    }
                }
            }

            voidLimboPlayers.clear();
            playerVisualEffects.clear();
            effectTasks.clear();

            initialized = false;
            logger.info("LimboManager shutdown completed");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during LimboManager shutdown", e);
        }
    }

    // Public API methods
    public int getActiveEffectsCount() {
        return playerVisualEffects.size();
    }

    public int getEffectTasksCount() {
        return effectTasks.size();
    }

    public int getLimboPlayersCount() {
        return voidLimboPlayers.size();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public World getLimboWorld() {
        return voidLimboWorld;
    }

    /**
     * Visual effects context class
     */
    private static class LimboVisualEffects {
        private final UUID playerId;
        private final String playerName;
        private LoadingPhase currentPhase;
        private long effectStartTime;

        public LimboVisualEffects(Player player, LoadingPhase phase) {
            this.playerId = player.getUniqueId();
            this.playerName = player.getName();
            this.currentPhase = phase;
            this.effectStartTime = System.currentTimeMillis();
        }

        public void updatePhase(LoadingPhase newPhase) {
            this.currentPhase = newPhase;
            this.effectStartTime = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public LoadingPhase getCurrentPhase() { return currentPhase; }
        public long getEffectStartTime() { return effectStartTime; }
    }
}