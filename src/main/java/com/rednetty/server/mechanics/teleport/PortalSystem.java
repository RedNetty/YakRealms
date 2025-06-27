package com.rednetty.server.mechanics.teleport;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * System for managing teleport portals
 */
public class PortalSystem implements Listener {
    private static PortalSystem instance;
    private final Logger logger;
    private final TeleportManager teleportManager;
    private final WorldGuardManager worldGuardManager;

    // Players who are currently gliding (thread-safe)
    private final Map<Player, Long> glidingPlayers = new ConcurrentHashMap<>();

    // Map of portal region IDs to destination IDs
    private final Map<String, String> portalDestinations = new HashMap<>();

    // Task for gliding particles
    private BukkitTask glidingTask;

    // Constants
    private static final long GLIDING_PARTICLE_INTERVAL = 20L; // 1 second
    private static final long MAX_GLIDING_TIME = 300000L; // 5 minutes max gliding time

    /**
     * Private constructor for singleton pattern
     */
    private PortalSystem() {
        this.logger = YakRealms.getInstance().getLogger();
        this.teleportManager = TeleportManager.getInstance();
        this.worldGuardManager = WorldGuardManager.getInstance();
    }

    /**
     * Gets the singleton instance
     *
     * @return The PortalSystem instance
     */
    public static PortalSystem getInstance() {
        if (instance == null) {
            instance = new PortalSystem();
        }
        return instance;
    }

    /**
     * Initializes the portal system
     */
    public void onEnable() {
        try {
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Register default portal destinations
            registerDefaultPortalDestinations();

            // Start gliding particles task
            startGlidingParticlesTask();

            logger.info("PortalSystem has been enabled");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable PortalSystem", e);
        }
    }

    /**
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
        try {
            // Stop gliding task
            if (glidingTask != null && !glidingTask.isCancelled()) {
                glidingTask.cancel();
            }

            // Stop all players from gliding
            for (Player player : new ArrayList<>(glidingPlayers.keySet())) {
                stopGliding(player);
            }

            glidingPlayers.clear();
            logger.info("PortalSystem has been disabled");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during PortalSystem shutdown", e);
        }
    }

    /**
     * Registers default portal destinations
     */
    private void registerDefaultPortalDestinations() {
        try {
            portalDestinations.put("tp", "avalon");
            logger.fine("Registered default portal destinations");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error registering default portal destinations", e);
        }
    }

    /**
     * Starts the task for displaying gliding particles
     */
    private void startGlidingParticlesTask() {
        glidingTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    processGlidingPlayers();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in gliding particles task", e);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), GLIDING_PARTICLE_INTERVAL, GLIDING_PARTICLE_INTERVAL);
    }

    /**
     * Processes all gliding players
     */
    private void processGlidingPlayers() {
        long currentTime = System.currentTimeMillis();
        List<Player> playersToRemove = new ArrayList<>();

        for (Map.Entry<Player, Long> entry : glidingPlayers.entrySet()) {
            Player player = entry.getKey();
            long startTime = entry.getValue();

            try {
                // Check if player is still valid
                if (!isPlayerValid(player)) {
                    playersToRemove.add(player);
                    continue;
                }

                // Check for timeout
                if (currentTime - startTime > MAX_GLIDING_TIME) {
                    playersToRemove.add(player);
                    continue;
                }

                // Display particles
                displayGlidingParticles(player);

                // Ensure player is still gliding
                if (!player.isGliding()) {
                    player.setGliding(true);
                }

                // Check if player has landed
                if (player.isOnGround()) {
                    playersToRemove.add(player);
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing gliding player " + player.getName(), e);
                playersToRemove.add(player);
            }
        }

        // Remove players who should stop gliding
        for (Player player : playersToRemove) {
            stopGliding(player);
        }
    }

    /**
     * Checks if a player is valid for gliding
     *
     * @param player The player to check
     * @return True if valid
     */
    private boolean isPlayerValid(Player player) {
        return player != null &&
                player.isOnline() &&
                player.getLocation() != null &&
                player.getLocation().getWorld() != null;
    }

    /**
     * Displays gliding particles for a player
     *
     * @param player The player
     */
    private void displayGlidingParticles(Player player) {
        if (!isPlayerValid(player)) {
            return;
        }

        try {
            Location location = player.getLocation().clone();
            for (int i = 0; i < 360; i += 40) {
                double angle = i * Math.PI / 180;
                Vector v = new Vector(Math.cos(angle), 0, Math.sin(angle));
                location.add(v);

                if (location.getWorld() != null) {
                    location.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 1);
                }

                location.subtract(v);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error displaying gliding particles for " + player.getName(), e);
        }
    }

    /**
     * Checks if a location is within a portal zone
     *
     * @param location The location to check
     * @param regionId The region ID to check for
     * @return True if in portal zone
     */
    public boolean isInPortalRegion(Location location, String regionId) {
        if (location == null || location.getWorld() == null || regionId == null) {
            return false;
        }

        try {
            // Use WorldGuard API to check if location is in the specified region
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));

            if (regionManager == null) {
                return false;
            }

            BlockVector3 pos = BlockVector3.at(
                    location.getX(),
                    location.getY(),
                    location.getZ()
            );

            ApplicableRegionSet regions = regionManager.getApplicableRegions(pos);

            for (ProtectedRegion region : regions) {
                if (region.getId().equalsIgnoreCase(regionId)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking portal region for " + regionId, e);
        }

        return false;
    }

    /**
     * Registers a portal destination
     *
     * @param regionId      The WorldGuard region ID
     * @param destinationId The destination ID
     */
    public void registerPortalDestination(String regionId, String destinationId) {
        if (regionId == null || destinationId == null) {
            logger.warning("Cannot register portal destination with null parameters");
            return;
        }

        portalDestinations.put(regionId.toLowerCase(), destinationId.toLowerCase());
        logger.fine("Registered portal destination: " + regionId + " -> " + destinationId);
    }

    /**
     * Unregisters a portal destination
     *
     * @param regionId The WorldGuard region ID
     * @return True if removed
     */
    public boolean unregisterPortalDestination(String regionId) {
        if (regionId == null) {
            return false;
        }

        boolean removed = portalDestinations.remove(regionId.toLowerCase()) != null;
        if (removed) {
            logger.fine("Unregistered portal destination: " + regionId);
        }
        return removed;
    }

    /**
     * Starts gliding for a player
     *
     * @param player The player
     */
    public void startGliding(Player player) {
        if (!isPlayerValid(player)) {
            return;
        }

        try {
            if (!glidingPlayers.containsKey(player)) {
                glidingPlayers.put(player, System.currentTimeMillis());
                player.setGliding(true);
                logger.fine("Started gliding for player " + player.getName());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error starting gliding for " + player.getName(), e);
        }
    }

    /**
     * Stops gliding for a player
     *
     * @param player The player
     */
    public void stopGliding(Player player) {
        if (player == null) {
            return;
        }

        try {
            if (glidingPlayers.remove(player) != null) {
                if (player.isOnline()) {
                    player.setGliding(false);
                }
                logger.fine("Stopped gliding for player " + player.getName());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error stopping gliding for " + player.getName(), e);
        }
    }

    /**
     * Checks if a player is currently gliding through the portal system
     *
     * @param player The player
     * @return True if gliding
     */
    public boolean isGliding(Player player) {
        return player != null && glidingPlayers.containsKey(player);
    }

    /**
     * Teleports a player through a portal
     *
     * @param player   The player
     * @param regionId The portal region ID
     */
    private void handlePortalTeleport(Player player, String regionId) {
        if (player == null || regionId == null) {
            return;
        }

        try {
            String destinationId = portalDestinations.get(regionId.toLowerCase());
            if (destinationId == null) {
                logger.fine("No destination registered for portal region: " + regionId);
                return;
            }

            TeleportDestination destination = teleportManager.getDestination(destinationId);
            if (destination == null) {
                logger.warning("Portal destination not found: " + destinationId);
                return;
            }

            // Validate destination
            if (destination.getLocation() == null || destination.getLocation().getWorld() == null) {
                logger.warning("Portal destination has invalid location: " + destinationId);
                return;
            }

            // Immediate teleport with portal effects
            teleportManager.teleportImmediately(player, destination, TeleportEffectType.PORTAL);
            logger.fine("Teleported player " + player.getName() + " through portal " + regionId + " to " + destinationId);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling portal teleport for " + player.getName(), e);
        }
    }

    /**
     * Handles player movement into portal regions
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        if (player == null || to == null) {
            return;
        }

        try {
            // Check WorldGuard portal regions
            for (String regionId : portalDestinations.keySet()) {
                if (isInPortalRegion(to, regionId)) {
                    // Cancel the move event to prevent multiple triggers
                    event.setCancelled(true);

                    // Handle teleport in next tick to avoid timing issues
                    Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                        handlePortalTeleport(player, regionId);
                    });

                    return;
                }
            }

            // Handle special portal pairs (legacy support)
            handleLegacyPortalPairs(player, to, event);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in portal move event for " + player.getName(), e);
        }
    }

    /**
     * Handles the special portal pairs from the old system
     *
     * @param player The player
     * @param to     The destination location
     * @param event  The move event
     */
    private void handleLegacyPortalPairs(Player player, Location to, PlayerMoveEvent event) {
        if (to.getWorld() == null) {
            return;
        }

        try {
            World mainWorld = Bukkit.getWorlds().get(0);
            if (!to.getWorld().equals(mainWorld)) {
                return;
            }

            Location teleportLocation = null;

            // Avalon portal pair 1
            if (to.getX() > -1155.0 && to.getX() < -1145.0 &&
                    to.getY() > 90.0 && to.getY() < 100.0 &&
                    to.getZ() < -500.0 && to.getZ() > -530.0) {

                teleportLocation = new Location(mainWorld, -357.5, 171.0, -3440.5,
                        to.getYaw(), to.getPitch());
            }
            // Avalon portal pair 2
            else if (to.getX() < -360.0 && to.getX() > -370.0 &&
                    to.getY() > 165.0 && to.getY() < 190.0 &&
                    to.getZ() < -3426.0 && to.getZ() > -3455.0) {

                teleportLocation = new Location(mainWorld, -1158.5, 95.0, -515.5,
                        to.getYaw(), to.getPitch());
            }

            if (teleportLocation != null) {
                event.setCancelled(true);

                // Schedule teleport for next tick
                final Location finalLocation = teleportLocation;
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                    player.teleport(finalLocation);
                    logger.fine("Teleported player " + player.getName() + " via legacy portal");
                });
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling legacy portal pairs for " + player.getName(), e);
        }
    }

    /**
     * Prevents gliding players from taking fall damage
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGlidingDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player) || event.getDamage() <= 0.0) {
            return;
        }

        Player player = (Player) event.getEntity();

        try {
            if (isGliding(player) || player.isGliding()) {
                EntityDamageEvent.DamageCause cause = event.getCause();

                if (cause == EntityDamageEvent.DamageCause.FALL ||
                        cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
                        cause == EntityDamageEvent.DamageCause.CONTACT ||
                        cause == EntityDamageEvent.DamageCause.FALLING_BLOCK ||
                        cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL ||
                        cause == EntityDamageEvent.DamageCause.CUSTOM ||
                        cause == EntityDamageEvent.DamageCause.DROWNING) {

                    event.setDamage(0.0);
                    event.setCancelled(true);

                    // Stop gliding if player landed
                    if (player.isOnGround()) {
                        stopGliding(player);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling gliding damage for " + player.getName(), e);
        }
    }

    /**
     * Prevents gliding from being toggled off inappropriately
     */
    @EventHandler
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        try {
            if (isGliding(player)) {
                if (player.isOnGround()) {
                    // Allow stopping glide if on ground
                    stopGliding(player);
                } else if (!event.isGliding()) {
                    // Don't let them stop gliding mid-air
                    event.setCancelled(true);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling glide toggle for " + player.getName(), e);
        }
    }

    /**
     * Gets all registered portal destinations
     *
     * @return A copy of the portal destinations map
     */
    public Map<String, String> getPortalDestinations() {
        return new HashMap<>(portalDestinations);
    }

    /**
     * Gets all currently gliding players
     *
     * @return A copy of the gliding players map
     */
    public Map<Player, Long> getGlidingPlayers() {
        return new HashMap<>(glidingPlayers);
    }

    /**
     * Forces a player to stop gliding
     *
     * @param player The player
     */
    public void forceStopGliding(Player player) {
        if (player != null) {
            stopGliding(player);
        }
    }

    /**
     * Clears all portal destinations
     */
    public void clearPortalDestinations() {
        portalDestinations.clear();
        logger.info("Cleared all portal destinations");
    }

    /**
     * Gets performance statistics
     *
     * @return A map of performance statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("gliding_players", glidingPlayers.size());
        stats.put("portal_destinations", portalDestinations.size());
        stats.put("task_running", glidingTask != null && !glidingTask.isCancelled());
        return stats;
    }
}