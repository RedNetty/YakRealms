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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * System for managing teleport portals
 */
public class PortalSystem implements Listener {
    private static PortalSystem instance;
    private final Logger logger;
    private final TeleportManager teleportManager;
    private final WorldGuardManager worldGuardManager;

    // Players who are currently gliding
    private final List<Player> glidingPlayers = new ArrayList<>();

    // Map of portal region IDs to destination IDs
    private final Map<String, String> portalDestinations = new HashMap<>();

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
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Register default portal destinations
        portalDestinations.put("tp", "avalon");

        // Start gliding particles task
        startGlidingParticlesTask();

        logger.info("PortalSystem has been enabled");
    }

    /**
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
        glidingPlayers.clear();
        logger.info("PortalSystem has been disabled");
    }

    /**
     * Starts the task for displaying gliding particles
     */
    private void startGlidingParticlesTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> playersToRemove = new ArrayList<>();

                for (Player player : glidingPlayers) {
                    if (!player.isOnline()) {
                        playersToRemove.add(player);
                        continue;
                    }

                    // Display particles
                    Location location = player.getLocation().clone();
                    for (int i = 0; i < 360; i += 40) {
                        double angle = i * Math.PI / 180;
                        Vector v = new Vector(Math.cos(angle), 0, Math.sin(angle));
                        location.add(v);
                        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 1);
                        location.subtract(v);
                    }

                    // Ensure player is still gliding
                    if (!player.isGliding()) {
                        player.setGliding(true);
                    }

                    // Check if player has landed
                    if (player.isOnGround()) {
                        playersToRemove.add(player);
                    }
                }

                // Remove players who have landed
                for (Player player : playersToRemove) {
                    stopGliding(player);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L);
    }

    /**
     * Checks if a location is within a portal zone
     *
     * @param location The location to check
     * @param regionId The region ID to check for
     * @return True if in portal zone
     */
    public boolean isInPortalRegion(Location location, String regionId) {
        if (location == null || location.getWorld() == null) {
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
            logger.warning("Error checking portal region: " + e.getMessage());
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
        portalDestinations.put(regionId.toLowerCase(), destinationId.toLowerCase());
    }

    /**
     * Starts gliding for a player
     *
     * @param player The player
     */
    public void startGliding(Player player) {
        if (!glidingPlayers.contains(player)) {
            glidingPlayers.add(player);
            player.setGliding(true);
        }
    }

    /**
     * Stops gliding for a player
     *
     * @param player The player
     */
    public void stopGliding(Player player) {
        if (glidingPlayers.contains(player)) {
            player.setGliding(false);
            glidingPlayers.remove(player);
        }
    }

    /**
     * Teleports a player through a portal
     *
     * @param player   The player
     * @param regionId The portal region ID
     */
    private void handlePortalTeleport(Player player, String regionId) {
        String destinationId = portalDestinations.get(regionId.toLowerCase());
        if (destinationId == null) {
            return;
        }

        TeleportDestination destination = teleportManager.getDestination(destinationId);
        if (destination == null) {
            return;
        }

        // Immediate teleport with portal effects
        teleportManager.teleportImmediately(player, destination, TeleportEffectType.PORTAL);
    }

    /**
     * Handles player movement into portal regions
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        // Check WorldGuard portal regions
        for (String regionId : portalDestinations.keySet()) {
            if (isInPortalRegion(to, regionId)) {
                // Cancel the move event to prevent multiple triggers
                event.setCancelled(true);

                // Handle teleport in next tick
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                    handlePortalTeleport(player, regionId);
                });

                return;
            }
        }

        // Handle special portal pairs (legacy support)
        handleLegacyPortalPairs(player, to);
    }

    /**
     * Handles the special portal pairs from the old system
     *
     * @param player The player
     * @param to     The destination location
     */
    private void handleLegacyPortalPairs(Player player, Location to) {
        World mainWorld = Bukkit.getWorlds().get(0);

        // Avalon portal pair 1
        if (to.getWorld().equals(mainWorld) &&
                to.getX() > -1155.0 && to.getX() < -1145.0 &&
                to.getY() > 90.0 && to.getY() < 100.0 &&
                to.getZ() < -500.0 && to.getZ() > -530.0) {

            Location dest = new Location(mainWorld, -357.5, 171.0, -3440.5,
                    to.getYaw(), to.getPitch());
            player.teleport(dest);
        }

        // Avalon portal pair 2
        else if (to.getWorld().equals(mainWorld) &&
                to.getX() < -360.0 && to.getX() > -370.0 &&
                to.getY() > 165.0 && to.getY() < 190.0 &&
                to.getZ() < -3426.0 && to.getZ() > -3455.0) {

            Location dest = new Location(mainWorld, -1158.5, 95.0, -515.5,
                    to.getYaw(), to.getPitch());
            player.teleport(dest);
        }
    }

    /**
     * Prevents gliding players from taking fall damage
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGlidingDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getDamage() <= 0.0) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (glidingPlayers.contains(player) || player.isGliding()) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                    event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION ||
                    event.getCause() == EntityDamageEvent.DamageCause.CONTACT ||
                    event.getCause() == EntityDamageEvent.DamageCause.FALLING_BLOCK ||
                    event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL ||
                    event.getCause() == EntityDamageEvent.DamageCause.CUSTOM ||
                    event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {

                event.setDamage(0.0);
                stopGliding(player);
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents gliding from being toggled off
     */
    @EventHandler
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (glidingPlayers.contains(player)) {
            if (player.isOnGround()) {
                stopGliding(player);
            } else if (event.isGliding()) {
                // Don't let them stop gliding mid-air
                event.setCancelled(true);
            }
        }
    }
}