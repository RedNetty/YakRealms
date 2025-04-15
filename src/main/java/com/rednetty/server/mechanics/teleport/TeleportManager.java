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
    }

    /**
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
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
    }

    /**
     * Loads destinations from configuration
     */
    private void loadConfiguration() {
        configFile = new File(YakRealms.getInstance().getDataFolder(), "teleports.yml");

        if (!configFile.exists()) {
            // Create directory if it doesn't exist
            YakRealms.getInstance().getDataFolder().mkdirs();

            try {
                // Create the file with default content
                configFile.createNewFile();
                config = YamlConfiguration.loadConfiguration(configFile);

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
        ConfigurationSection destinationsSection = config.getConfigurationSection("destinations");
        if (destinationsSection != null) {
            for (String key : destinationsSection.getKeys(false)) {
                ConfigurationSection destSection = destinationsSection.getConfigurationSection(key);
                if (destSection != null) {
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
                            logger.warning("Invalid destination config for " + key);
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
                        logger.info("Loaded teleport destination: " + displayName);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error loading destination " + key, e);
                    }
                }
            }
        }

        logger.info("Loaded " + destinations.size() + " teleport destinations");
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
                if (location.getWorld() != null) {
                    config.set(path + ".world", location.getWorld().getName());
                    config.set(path + ".x", location.getX());
                    config.set(path + ".y", location.getY());
                    config.set(path + ".z", location.getZ());
                    config.set(path + ".yaw", location.getYaw());
                    config.set(path + ".pitch", location.getPitch());
                    config.set(path + ".cost", destination.getCost());
                    config.set(path + ".premium", destination.isPremium());
                } else {
                    logger.warning("Cannot save destination " + destination.getId() + ": world is null");
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
     * Registers default teleport destinations
     */
    private void registerDefaultDestinations() {
        // Add default destinations
        registerDestination(new TeleportDestination(
                "deadpeaks", "Dead Peaks",
                new Location(Bukkit.getWorlds().get(0), 603.0, 35.0, -281.0, 1.0f, 1.0f),
                50, false
        ));

        registerDestination(new TeleportDestination(
                "tripoli", "Tripoli",
                new Location(Bukkit.getWorlds().get(0), 817.0, 9.0, -80.0, 1.0f, 1.0f),
                50, false
        ));

        registerDestination(new TeleportDestination(
                "avalon", "Avalon",
                new Location(Bukkit.getWorlds().get(0), 636.0, 97.0, 243.0, 1.0f, 1.0f),
                100, true
        ));
    }

    /**
     * Starts the task that processes teleport sessions
     */
    private void startTeleportTickTask() {
        Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            Iterator<Map.Entry<UUID, TeleportSession>> it = activeSessions.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, TeleportSession> entry = it.next();
                TeleportSession session = entry.getValue();

                if (session.tick()) {
                    // Session is complete, remove it
                    it.remove();
                }
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
        UUID uuid = player.getUniqueId();

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if(itemStack.getType() != Material.BOOK || itemStack == null) {
            return false;
        }
        int itemAmount = itemStack.getAmount();
        itemStack.setAmount(itemAmount - 1);


        // Check if player is already teleporting
        if (activeSessions.containsKey(uuid)) {
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
        UUID uuid = player.getUniqueId();

        // Cancel any existing teleport
        cancelTeleport(uuid, "Superseded by immediate teleport");

        // Apply departure effects
        TeleportEffects.applyDepartureEffects(player, effectType);

        // Teleport the player
        player.teleport(destination.getLocation());

        // Apply arrival effects
        TeleportEffects.applyArrivalEffects(player, effectType);
    }
}