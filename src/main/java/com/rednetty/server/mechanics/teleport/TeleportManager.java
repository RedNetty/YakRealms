package com.rednetty.server.mechanics.teleport;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central manager for all teleportation mechanics
 */
public class TeleportManager {
    private static TeleportManager instance;
    private final Logger logger;
    private final Map<String, TeleportDestination> destinations = new HashMap<>();
    private final Map<UUID, TeleportSession> activeSessions = new HashMap<>();
    private File configFile;
    private FileConfiguration config;

    /**
     * Private constructor for singleton pattern
     */
    private TeleportManager() {
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The TeleportManager instance
     */
    public static TeleportManager getInstance() {
        if (instance == null) {
            instance = new TeleportManager();
        }
        return instance;
    }

    /**
     * Initializes the teleport manager
     */
    public void onEnable() {
        try {
            // Load destinations from config
            loadConfiguration();

            // Register event listeners
            Bukkit.getServer().getPluginManager().registerEvents(new TeleportListener(this), YakRealms.getInstance());

            // Initialize components
            TeleportBookSystem.getInstance().onEnable();
            HearthstoneSystem.getInstance().onEnable();
            PortalSystem.getInstance().onEnable();

            // Start teleport tick task
            startTeleportTickTask();

            YakRealms.log("TeleportManager has been enabled.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable TeleportManager", e);
        }
    }

    /**
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
        try {
            // Cancel all active teleport sessions
            for (UUID uuid : new ArrayList<>(activeSessions.keySet())) {
                cancelTeleport(uuid, "Server is shutting down");
            }

            // Save destination config
            saveConfiguration();

            // Disable components
            TeleportBookSystem.getInstance().onDisable();
            HearthstoneSystem.getInstance().onDisable();
            PortalSystem.getInstance().onDisable();

            YakRealms.log("TeleportManager has been disabled.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during TeleportManager shutdown", e);
        }
    }

    /**
     * Loads destinations from configuration
     */
    private void loadConfiguration() {
        try {
            configFile = new File(YakRealms.getInstance().getDataFolder(), "teleports.yml");

            if (!configFile.exists()) {
                // Create directory if it doesn't exist
                YakRealms.getInstance().getDataFolder().mkdirs();

                try {
                    // Create the file with default content
                    configFile.createNewFile();
                    config = YamlConfiguration.loadConfiguration(configFile);

                    // Add default destinations
                    createDefaultConfiguration();

                    // Save the default config
                    config.save(configFile);
                    logger.info("Created default teleports.yml configuration");
                } catch (IOException e) {
                    logger.severe("Could not create default teleports.yml file: " + e.getMessage());
                    return;
                }
            } else {
                // Load existing configuration
                config = YamlConfiguration.loadConfiguration(configFile);
            }

            // Clear existing destinations
            destinations.clear();

            // Load destinations from config
            loadDestinationsFromConfig();

            logger.info("Loaded " + destinations.size() + " teleport destinations");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading teleport configuration", e);
        }
    }

    /**
     * Creates default configuration
     */
    private void createDefaultConfiguration() {
        if (config == null) {
            return;
        }

        // Add default destinations
        config.set("destinations.deadpeaks.display-name", "Dead Peaks");
        config.set("destinations.deadpeaks.world", "world");
        config.set("destinations.deadpeaks.x", 603.0);
        config.set("destinations.deadpeaks.y", 35.0);
        config.set("destinations.deadpeaks.z", -281.0);
        config.set("destinations.deadpeaks.yaw", 0.0);
        config.set("destinations.deadpeaks.pitch", 0.0);
        config.set("destinations.deadpeaks.cost", 50);
        config.set("destinations.deadpeaks.premium", false);

        config.set("destinations.tripoli.display-name", "Tripoli");
        config.set("destinations.tripoli.world", "world");
        config.set("destinations.tripoli.x", 817.0);
        config.set("destinations.tripoli.y", 9.0);
        config.set("destinations.tripoli.z", -80.0);
        config.set("destinations.tripoli.yaw", 0.0);
        config.set("destinations.tripoli.pitch", 0.0);
        config.set("destinations.tripoli.cost", 50);
        config.set("destinations.tripoli.premium", false);

        config.set("destinations.avalon.display-name", "Avalon");
        config.set("destinations.avalon.world", "world");
        config.set("destinations.avalon.x", 636.0);
        config.set("destinations.avalon.y", 97.0);
        config.set("destinations.avalon.z", 243.0);
        config.set("destinations.avalon.yaw", 0.0);
        config.set("destinations.avalon.pitch", 0.0);
        config.set("destinations.avalon.cost", 100);
        config.set("destinations.avalon.premium", true);
    }

    /**
     * Loads destinations from config section
     */
    private void loadDestinationsFromConfig() {
        ConfigurationSection destinationsSection = config.getConfigurationSection("destinations");
        if (destinationsSection == null) {
            logger.warning("No destinations section found in config");
            return;
        }

        for (String key : destinationsSection.getKeys(false)) {
            ConfigurationSection destSection = destinationsSection.getConfigurationSection(key);
            if (destSection == null) {
                logger.warning("Invalid destination section for " + key);
                continue;
            }

            try {
                String id = key;
                String displayName = destSection.getString("display-name");
                String worldName = destSection.getString("world");
                double x = destSection.getDouble("x");
                double y = destSection.getDouble("y");
                double z = destSection.getDouble("z");
                float yaw = (float) destSection.getDouble("yaw", 0.0);
                float pitch = (float) destSection.getDouble("pitch", 0.0);
                int cost = destSection.getInt("cost", 50);
                boolean premium = destSection.getBoolean("premium", false);

                if (worldName == null || displayName == null) {
                    logger.warning("Invalid destination config for " + key + ": missing required fields");
                    continue;
                }

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    logger.warning("World '" + worldName + "' not found for destination " + key);
                    continue;
                }

                Location location = new Location(world, x, y, z, yaw, pitch);

                TeleportDestination destination = new TeleportDestination(
                        id, displayName, location, cost, premium
                );

                destinations.put(id.toLowerCase(), destination);
                logger.fine("Loaded teleport destination: " + displayName);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error loading destination " + key, e);
            }
        }
    }

    /**
     * Saves destinations to configuration
     */
    public void saveConfiguration() {
        if (config == null) {
            logger.warning("Cannot save teleport configuration: config is null");
            return;
        }

        try {
            // Clear destinations section
            config.set("destinations", null);

            // Save each destination
            for (TeleportDestination destination : destinations.values()) {
                String path = "destinations." + destination.getId();
                config.set(path + ".display-name", destination.getDisplayName());

                Location location = destination.getLocation();
                if (location != null && location.getWorld() != null) {
                    config.set(path + ".world", location.getWorld().getName());
                    config.set(path + ".x", location.getX());
                    config.set(path + ".y", location.getY());
                    config.set(path + ".z", location.getZ());
                    config.set(path + ".yaw", location.getYaw());
                    config.set(path + ".pitch", location.getPitch());
                    config.set(path + ".cost", destination.getCost());
                    config.set(path + ".premium", destination.isPremium());
                } else {
                    logger.warning("Cannot save destination " + destination.getId() + ": location is null");
                }
            }

            // Save the config file
            config.save(configFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save teleport destinations", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error saving teleport destinations", e);
        }
    }

    /**
     * Starts the task that processes teleport sessions
     */
    private void startTeleportTickTask() {
        Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                Iterator<Map.Entry<UUID, TeleportSession>> it = activeSessions.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<UUID, TeleportSession> entry = it.next();
                    TeleportSession session = entry.getValue();

                    try {
                        if (session.tick()) {
                            // Session is complete, remove it
                            it.remove();
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error processing teleport session for " + entry.getKey(), e);
                        // Remove problematic session
                        it.remove();
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in teleport tick task", e);
            }
        }, 20L, 20L);
    }

    /**
     * Registers a new teleport destination
     *
     * @param destination The destination to register
     * @return True if registered, false if already exists
     */
    public boolean registerDestination(TeleportDestination destination) {
        if (destination == null || destination.getId() == null) {
            return false;
        }

        String id = destination.getId().toLowerCase();

        if (destinations.containsKey(id)) {
            return false;
        }

        destinations.put(id, destination);
        saveConfiguration();
        return true;
    }

    /**
     * Updates an existing teleport destination
     *
     * @param destination The destination to update
     * @return True if updated, false if not found
     */
    public boolean updateDestination(TeleportDestination destination) {
        if (destination == null || destination.getId() == null) {
            return false;
        }

        String id = destination.getId().toLowerCase();

        if (!destinations.containsKey(id)) {
            return false;
        }

        destinations.put(id, destination);
        saveConfiguration();
        return true;
    }

    /**
     * Removes a teleport destination
     *
     * @param id The destination ID
     * @return True if removed, false if not found
     */
    public boolean removeDestination(String id) {
        if (id == null) {
            return false;
        }

        boolean removed = destinations.remove(id.toLowerCase()) != null;

        if (removed) {
            saveConfiguration();
        }

        return removed;
    }

    /**
     * Gets a teleport destination by ID
     *
     * @param id The destination ID
     * @return The destination or null if not found
     */
    public TeleportDestination getDestination(String id) {
        if (id == null) {
            return null;
        }
        return destinations.get(id.toLowerCase());
    }

    /**
     * Gets all registered teleport destinations
     *
     * @return A collection of all destinations
     */
    public Collection<TeleportDestination> getAllDestinations() {
        return Collections.unmodifiableCollection(destinations.values());
    }

    /**
     * Creates a new teleport session for a player
     *
     * @param player      The player to teleport
     * @param destination The destination
     * @param castingTime The casting time in seconds
     * @param consumable  The item to consume (or null)
     * @param effectType  The effect type to use
     * @return True if successful, false if player is already teleporting
     */
    public boolean startTeleport(Player player, TeleportDestination destination,
                                 int castingTime, TeleportConsumable consumable,
                                 TeleportEffectType effectType) {
        if (player == null || destination == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();

        // Check if player is already teleporting
        if (activeSessions.containsKey(uuid)) {
            return false;
        }

        // Validate destination location
        if (destination.getLocation() == null || destination.getLocation().getWorld() == null) {
            player.sendMessage("§cTeleport destination is invalid.");
            return false;
        }

        // Create teleport session
        TeleportSession session = new TeleportSession(
                player, destination, castingTime, consumable, effectType
        );

        activeSessions.put(uuid, session);
        session.start();

        return true;
    }

    /**
     * Cancels an active teleport session
     *
     * @param uuid   The player's UUID
     * @param reason The reason for cancellation
     * @return True if cancelled, false if not active
     */
    public boolean cancelTeleport(UUID uuid, String reason) {
        if (uuid == null) {
            return false;
        }

        TeleportSession session = activeSessions.remove(uuid);

        if (session != null) {
            session.cancel(reason);
            return true;
        }

        return false;
    }

    /**
     * Checks if a player has an active teleport session
     *
     * @param uuid The player's UUID
     * @return True if teleporting, false otherwise
     */
    public boolean isTeleporting(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return activeSessions.containsKey(uuid);
    }

    /**
     * Performs an immediate teleport for a player
     *
     * @param player      The player to teleport
     * @param destination The destination
     * @param effectType  The effect type to use
     */
    public void teleportImmediately(Player player, TeleportDestination destination, TeleportEffectType effectType) {
        if (player == null || destination == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Cancel any existing teleport
        cancelTeleport(uuid, "Superseded by immediate teleport");

        // Validate destination
        if (destination.getLocation() == null || destination.getLocation().getWorld() == null) {
            player.sendMessage("§cTeleport destination is invalid.");
            return;
        }

        try {
            // Apply departure effects
            TeleportEffects.applyDepartureEffects(player, effectType);

            // Teleport the player
            player.teleport(destination.getLocation());

            // Apply arrival effects
            TeleportEffects.applyArrivalEffects(player, effectType);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during immediate teleport for " + player.getName(), e);
            player.sendMessage("§cTeleportation failed due to an error.");
        }
    }

    /**
     * Gets the active teleport session for a player
     *
     * @param uuid The player's UUID
     * @return The teleport session or null if not teleporting
     */
    public TeleportSession getTeleportSession(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return activeSessions.get(uuid);
    }

    /**
     * Gets all active teleport sessions
     *
     * @return A copy of the active sessions map
     */
    public Map<UUID, TeleportSession> getActiveSessions() {
        return new HashMap<>(activeSessions);
    }

    /**
     * Validates that all destinations have valid worlds
     *
     * @return True if all destinations are valid
     */
    public boolean validateDestinations() {
        boolean allValid = true;
        for (TeleportDestination destination : destinations.values()) {
            if (destination.getLocation() == null || destination.getLocation().getWorld() == null) {
                logger.warning("Invalid destination found: " + destination.getId());
                allValid = false;
            }
        }
        return allValid;
    }

    /**
     * Reloads the configuration from file
     */
    public void reloadConfiguration() {
        try {
            loadConfiguration();
            logger.info("Teleport configuration reloaded successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error reloading teleport configuration", e);
        }
    }

    /**
     * Gets performance statistics
     *
     * @return A map of performance statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_sessions", activeSessions.size());
        stats.put("total_destinations", destinations.size());
        stats.put("premium_destinations", destinations.values().stream().mapToInt(d -> d.isPremium() ? 1 : 0).sum());
        return stats;
    }
}