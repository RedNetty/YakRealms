package com.rednetty.server;

import com.rednetty.server.commands.admin.*;
import com.rednetty.server.commands.economy.*;
import com.rednetty.server.commands.party.*;
import com.rednetty.server.commands.player.*;
import com.rednetty.server.commands.staff.*;
import com.rednetty.server.commands.utility.InvseeCommand;
import com.rednetty.server.commands.utility.ItemCommand;
import com.rednetty.server.commands.utility.OrbCommand;
import com.rednetty.server.commands.utility.TeleportCommand;
import com.rednetty.server.commands.world.NodeMapCommand;
import com.rednetty.server.commands.world.TrailCommand;
import com.rednetty.server.core.database.MongoDBManager;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.combat.CombatMechanics;
import com.rednetty.server.mechanics.combat.MagicStaff;
import com.rednetty.server.mechanics.combat.death.RespawnManager;
import com.rednetty.server.mechanics.combat.death.remnant.DeathRemnantManager;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.crates.CrateManager;
import com.rednetty.server.commands.admin.CrateCommand;
import com.rednetty.server.mechanics.drops.DropsHandler;
import com.rednetty.server.mechanics.drops.DropsManager;
import com.rednetty.server.mechanics.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.economy.BankManager;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.GemPouchManager;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import com.rednetty.server.mechanics.economy.vendors.VendorSystemInitializer;
import com.rednetty.server.mechanics.item.Journal;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.mechanics.market.MarketManager;
import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.mechanics.mobs.tasks.SpawnerHologramUpdater;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.mounts.MountManager;
import com.rednetty.server.mechanics.party.PartyMechanics;
import com.rednetty.server.mechanics.player.PlayerMechanics;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.items.SpeedfishMechanics;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.movement.DashMechanics;
import com.rednetty.server.mechanics.teleport.HearthstoneSystem;
import com.rednetty.server.mechanics.teleport.PortalSystem;
import com.rednetty.server.mechanics.teleport.TeleportBookSystem;
import com.rednetty.server.mechanics.teleport.TeleportManager;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import com.rednetty.server.mechanics.world.trail.TrailSystem;
import com.rednetty.server.mechanics.world.trail.pathing.ParticleSystem;
import com.rednetty.server.mechanics.world.trail.pathing.PathManager;
import com.rednetty.server.mechanics.world.trail.pathing.nodes.AdvancedNodeMapGenerator;
import com.rednetty.server.mechanics.world.trail.pathing.nodes.NavNode;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * FIXED: Main plugin class for YakRealms
 * Handles initialization of all core systems with enhanced error handling and proper load order
 */
public class YakRealms extends JavaPlugin {

    private static YakRealms instance;
    private MongoDBManager mongoDBManager;
    private YakPlayerManager playerManager;
    private PlayerMechanics playerMechanics;
    private PlayerListenerManager playerListenerManager;

    // All other system references
    private CombatMechanics combatMechanics;
    private MagicStaff magicStaff;
    private ChatMechanics chatMechanics;
    private AlignmentMechanics alignmentMechanics;
    private RespawnManager respawnManager;
    private DeathRemnantManager deathRemnantManager;
    private PartyMechanics partyMechanics;
    private DashMechanics dashMechanics;
    private SpeedfishMechanics speedfishMechanics;
    private MountManager mountManager;
    private ScrollManager scrollManager;
    private OrbManager orbManager;
    private Journal journalSystem;
    private EconomyManager economyManager;
    private BankManager bankManager;
    private GemPouchManager gemPouchManager;
    private VendorManager vendorManager;
    private MarketManager marketManager;
    private MobManager mobManager;
    private SpawnerCommand spawnerCommand;
    private DropsManager dropsManager;
    private DropsHandler dropsHandler;
    private LootBuffManager lootBuffManager;
    private TeleportManager teleportManager;
    private TeleportBookSystem teleportBookSystem;
    private HearthstoneSystem hearthstoneSystem;
    private PortalSystem portalSystem;
    private TrailSystem trailSystem;
    private ParticleSystem particleSystem;
    private PathManager pathManager;
    private CrateManager crateManager;

    // Game settings
    private static boolean patchLockdown = false;
    private static boolean t6Enabled = false;
    private static int sessionID = 0;
    private boolean mobsEnabled = true;
    private boolean spawnerVisibilityDefault = false;

    // FIXED: Initialization state tracking
    private final AtomicBoolean coreInitialized = new AtomicBoolean(false);
    private final AtomicBoolean systemsInitialized = new AtomicBoolean(false);
    private final AtomicBoolean commandsInitialized = new AtomicBoolean(false);

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        try {
            getLogger().info("Starting YakRealms initialization...");

            // Ensure config directory exists
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            // Load default config if it doesn't exist
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                saveDefaultConfig();
            }

            // Creates a sessionID for this run-time of the plugin
            sessionID = ThreadLocalRandom.current().nextInt();

            // FIXED: Wait for server to be fully loaded before initializing systems
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        // Check if server is ready
                        if (isServerReady()) {
                            getLogger().info("Server is ready, starting initialization...");

                            // Initialize in proper order with error checking
                            if (initializeCore()) {
                                getLogger().info("Core systems initialized successfully");

                                // Delayed system initialization
                                Bukkit.getScheduler().runTaskLater(YakRealms.this, () -> {
                                    try {
                                        if (initializeGameSystems()) {
                                            getLogger().info("Game systems initialized successfully");

                                            if (initializeCommands()) {
                                                getLogger().info("Commands registered successfully");
                                            } else {
                                                getLogger().warning("Some commands failed to register!");
                                            }

                                            finalizeStartup();
                                            getLogger().info("YakRealms has been enabled successfully!");
                                        } else {
                                            getLogger().severe("Failed to initialize game systems!");
                                        }
                                    } catch (Exception e) {
                                        getLogger().log(Level.SEVERE, "Error during delayed initialization", e);
                                    }
                                }, 20L); // 1 second delay

                            } else {
                                getLogger().severe("Failed to initialize core systems!");
                                getServer().getPluginManager().disablePlugin(YakRealms.this);
                            }

                            this.cancel(); // Stop the repeating task
                        } else {
                            getLogger().info("Waiting for server to be ready...");
                        }
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "Error during initialization check", e);
                        getServer().getPluginManager().disablePlugin(YakRealms.this);
                        this.cancel();
                    }
                }
            }.runTaskTimer(this, 20L, 20L); // Check every second

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Critical error during plugin startup", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * FIXED: Check if the server is ready for plugin initialization
     */
    private boolean isServerReady() {
        try {
            // Check if worlds are loaded
            List<World> worlds = getServer().getWorlds();
            if (worlds.isEmpty()) {
                return false;
            }

            // Check if the main world is accessible
            World mainWorld = getServer().getWorlds().get(0);
            if (mainWorld == null) {
                return false;
            }

            // Check if we can access world properties
            try {
                mainWorld.getName();
                mainWorld.getSpawnLocation();
            } catch (Exception e) {
                getLogger().warning("World not fully loaded yet: " + e.getMessage());
                return false;
            }

            return true;
        } catch (Exception e) {
            getLogger().warning("Error checking server readiness: " + e.getMessage());
            return false;
        }
    }

    /**
     * FIXED: Initialize core systems in the correct order with proper error handling
     */
    private boolean initializeCore() {
        if (coreInitialized.get()) {
            getLogger().warning("Core already initialized!");
            return true;
        }

        try {
            getLogger().info("Initializing core systems...");

            // Load game settings first
            loadGameSettings();

            // Initialize database with retry logic
            if (!initializeDatabaseWithRetry()) {
                getLogger().severe("Failed to initialize database after retries!");
                return false;
            }

            // FIXED: Initialize player systems in correct order
            if (!initializeCorePlayerSystems()) {
                getLogger().severe("Failed to initialize core player systems!");
                return false;
            }

            coreInitialized.set(true);
            getLogger().info("Core systems initialized successfully");
            return true;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing core systems", e);
            return false;
        }
    }

    /**
     * FIXED: Initialize database with retry logic
     */
    private boolean initializeDatabaseWithRetry() {
        int maxRetries = 3;
        int retryDelay = 5; // seconds

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                getLogger().info("Database initialization attempt " + attempt + "/" + maxRetries);

                FileConfiguration config = getConfig();
                mongoDBManager = MongoDBManager.initialize(config, this);

                if (mongoDBManager.connect()) {
                    getLogger().info("Successfully connected to MongoDB!");
                    return true;
                } else {
                    getLogger().warning("Failed to connect to MongoDB on attempt " + attempt);
                }

            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Database initialization attempt " + attempt + " failed", e);
            }

            // Wait before retry (except on last attempt)
            if (attempt < maxRetries) {
                try {
                    getLogger().info("Waiting " + retryDelay + " seconds before retry...");
                    Thread.sleep(retryDelay * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        getLogger().severe("Failed to connect to MongoDB after " + maxRetries + " attempts!");
        return false;
    }

    /**
     * FIXED: Initialize core player systems in proper order
     */
    private boolean initializeCorePlayerSystems() {
        try {
            getLogger().info("Initializing PlayerMechanics...");

            // 1. Initialize PlayerMechanics first (this will initialize YakPlayerManager)
            playerMechanics = PlayerMechanics.getInstance();
            if (playerMechanics == null) {
                getLogger().severe("Failed to get PlayerMechanics instance!");
                return false;
            }

            playerMechanics.onEnable();
            getLogger().info("PlayerMechanics initialized successfully");

            // 2. Get references to initialized systems
            playerManager = playerMechanics.getPlayerManager();
            if (playerManager == null) {
                getLogger().severe("PlayerManager is null after PlayerMechanics initialization!");
                return false;
            }

            playerListenerManager = playerMechanics.getListenerManager();
            if (playerListenerManager == null) {
                getLogger().severe("PlayerListenerManager is null after PlayerMechanics initialization!");
                return false;
            }

            getLogger().info("Core player systems initialized successfully!");
            return true;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing core player systems", e);
            return false;
        }
    }

    /**
     * FIXED: Initialize game-specific systems with better error handling
     */
    private boolean initializeGameSystems() {
        if (systemsInitialized.get()) {
            getLogger().warning("Systems already initialized!");
            return true;
        }

        boolean allSuccess = true;

        try {
            getLogger().info("Initializing game systems...");

            // Initialize systems in dependency order
            allSuccess &= safeInitialize("Party Mechanics", this::initializePartyMechanics);
            allSuccess &= safeInitialize("World Mechanics", this::initializeWorldMechanics);
            allSuccess &= safeInitialize("Alignment Mechanics", this::initializeAlignmentMechanics);
            allSuccess &= safeInitialize("Player Enhancements", this::initializePlayerEnhancements);
            allSuccess &= safeInitialize("Mount System", this::initializeMountSystem);
            allSuccess &= safeInitialize("Item Systems", this::initializeItemSystems);
            allSuccess &= safeInitialize("Teleport Systems", this::initializeTeleportSystems);
            allSuccess &= safeInitialize("Chat Mechanics", this::initializeChatMechanics);
            allSuccess &= safeInitialize("Economy Systems", this::initializeEconomySystems);
            allSuccess &= safeInitialize("Market System", this::initializeMarketSystem);
            allSuccess &= safeInitialize("Combat Systems", this::initializeCombatSystems);
            allSuccess &= safeInitialize("Death Systems", this::initializeDeathSystems);
            allSuccess &= safeInitialize("Mob System", this::initializeMobSystem);
            allSuccess &= safeInitialize("Drops System", this::initializeDropsSystem);
            allSuccess &= safeInitialize("Crate System", this::initializeCrateSystem);

            systemsInitialized.set(true);
            getLogger().info("Game systems initialization completed. Success rate: " +
                    (allSuccess ? "100%" : "Partial"));

            return allSuccess;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing game systems", e);
            return false;
        }
    }

    /**
     * FIXED: Safe initialization wrapper with proper error handling
     */
    private boolean safeInitialize(String systemName, SystemInitializer initializer) {
        try {
            getLogger().info("Initializing " + systemName + "...");
            boolean success = initializer.initialize();

            if (success) {
                getLogger().info(systemName + " initialized successfully!");
            } else {
                getLogger().warning(systemName + " failed to initialize!");
            }

            return success;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing " + systemName, e);
            return false;
        }
    }

    @FunctionalInterface
    private interface SystemInitializer {
        boolean initialize() throws Exception;
    }

    /**
     * FIXED: Initialize world mechanics with proper null checks
     */
    private boolean initializeWorldMechanics() {
        try {
            // FIXED: Ensure worlds are available before proceeding
            List<World> worlds = getServer().getWorlds();
            if (worlds.isEmpty()) {
                getLogger().warning("No worlds available for world mechanics initialization!");
                return false;
            }

            // Initialize TrailSystem
            if (this.trailSystem == null) {
                trailSystem = new TrailSystem(this);
                getLogger().info("Trail system initialized!");
            }

            // Initialize ParticleSystem
            particleSystem = new ParticleSystem(this);
            getLogger().info("Particle system initialized!");

            // FIXED: Safe world selection for PathManager
            World mainWorld = findMainWorld();
            if (mainWorld == null) {
                getLogger().warning("Could not find suitable world for PathManager, using default behavior");
                return true; // Don't fail completely, just skip path manager
            }

            // Initialize PathManager with node system
            try {
                AdvancedNodeMapGenerator nodeGenerator = new AdvancedNodeMapGenerator();
                File nodeMapFile = new File(getDataFolder(), mainWorld.getName() + "_advanced_navgraph.dat");
                List<NavNode> nodes = nodeGenerator.getOrGenerateNodeMap(mainWorld, nodeMapFile);

                // Initialize path manager with nodes
                pathManager = new PathManager(this, particleSystem);
                getLogger().info("Path manager initialized with " + nodes.size() + " navigation nodes!");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to initialize PathManager", e);
                // Continue without PathManager - non-critical
            }

            getLogger().info("World mechanics initialized successfully!");
            return true;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing world mechanics", e);
            return false;
        }
    }

    /**
     * FIXED: Safe world selection method
     */
    private World findMainWorld() {
        try {
            List<World> worlds = getServer().getWorlds();

            if (worlds.isEmpty()) {
                return null;
            }

            // Try to find world named "server" first
            for (World world : worlds) {
                if ("server".equalsIgnoreCase(world.getName())) {
                    return world;
                }
            }

            // Try to find the main overworld
            for (World world : worlds) {
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    return world;
                }
            }

            // Fallback to first world
            return worlds.get(0);

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error finding main world", e);
            return null;
        }
    }

    // All other initialization methods remain the same but with added error handling...

    private boolean initializePartyMechanics() {
        try {
            partyMechanics = PartyMechanics.getInstance();
            partyMechanics.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing party mechanics", e);
            return false;
        }
    }

    private boolean initializeAlignmentMechanics() {
        try {
            alignmentMechanics = AlignmentMechanics.getInstance();
            alignmentMechanics.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing alignment mechanics", e);
            return false;
        }
    }

    private boolean initializePlayerEnhancements() {
        try {
            dashMechanics = new DashMechanics();
            dashMechanics.onEnable();

            speedfishMechanics = new SpeedfishMechanics();
            speedfishMechanics.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing player enhancement systems", e);
            return false;
        }
    }

    private boolean initializeMountSystem() {
        try {
            mountManager = MountManager.getInstance();
            mountManager.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing mount system", e);
            return false;
        }
    }

    private boolean initializeItemSystems() {
        try {
            scrollManager = ScrollManager.getInstance();
            scrollManager.initialize();

            orbManager = OrbManager.getInstance();
            orbManager.initialize();

            journalSystem = new Journal();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing item systems", e);
            return false;
        }
    }

    private boolean initializeTeleportSystems() {
        try {
            teleportManager = TeleportManager.getInstance();
            teleportManager.onEnable();

            teleportBookSystem = TeleportBookSystem.getInstance();
            hearthstoneSystem = HearthstoneSystem.getInstance();
            portalSystem = PortalSystem.getInstance();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing teleport systems", e);
            return false;
        }
    }

    private boolean initializeChatMechanics() {
        try {
            chatMechanics = ChatMechanics.getInstance();
            chatMechanics.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing chat mechanics", e);
            return false;
        }
    }

    private boolean initializeEconomySystems() {
        try {
            economyManager = EconomyManager.getInstance();
            economyManager.onEnable();

            bankManager = BankManager.getInstance();
            bankManager.onEnable();

            gemPouchManager = GemPouchManager.getInstance();
            gemPouchManager.onEnable();

            vendorManager = VendorManager.getInstance(this);
            VendorSystemInitializer.initialize(this);
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing economy systems", e);
            return false;
        }
    }

    private boolean initializeMarketSystem() {
        try {
            marketManager = MarketManager.getInstance();
            marketManager.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing market system", e);
            return false;
        }
    }

    private boolean initializeCombatSystems() {
        try {
            combatMechanics = new CombatMechanics();
            combatMechanics.onEnable();

            magicStaff = new MagicStaff();
            magicStaff.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing combat systems", e);
            return false;
        }
    }

    private boolean initializeDeathSystems() {
        try {
            deathRemnantManager = new DeathRemnantManager(this);
            respawnManager = new RespawnManager();
            respawnManager.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing death systems", e);
            return false;
        }
    }

    private boolean initializeMobSystem() {
        try {
            mobManager = MobManager.getInstance();
            mobManager.initialize();
            mobManager.setSpawnersEnabled(mobsEnabled);

            FileConfiguration config = getConfig();
            config.set("mechanics.mobs.spawner-default-visibility", spawnerVisibilityDefault);
            saveConfig();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing mob system", e);
            return false;
        }
    }

    private boolean initializeDropsSystem() {
        try {
            dropsHandler = DropsHandler.getInstance();
            dropsHandler.initialize();

            lootBuffManager = LootBuffManager.getInstance();
            lootBuffManager.initialize();

            dropsManager = DropsManager.getInstance();
            dropsManager.initialize();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing drop systems", e);
            return false;
        }
    }

    private boolean initializeCrateSystem() {
        try {
            crateManager = CrateManager.getInstance();
            crateManager.initialize();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing crate system", e);
            return false;
        }
    }

    /**
     * Load game settings from config with validation
     */
    private void loadGameSettings() {
        FileConfiguration config = getConfig();

        // Game settings with defaults
        t6Enabled = config.getBoolean("game.t6-enabled", false);
        getLogger().info("T6 content is " + (t6Enabled ? "enabled" : "disabled"));

        // Mob system settings with validation
        mobsEnabled = config.getBoolean("mechanics.mobs.enabled", true);
        getLogger().info("Mob spawning is " + (mobsEnabled ? "enabled" : "disabled"));

        // Default spawner visibility setting
        spawnerVisibilityDefault = config.getBoolean("mechanics.mobs.spawner-default-visibility", false);
        getLogger().info("Default spawner visibility is " + (spawnerVisibilityDefault ? "visible" : "hidden"));

        // Validate settings
        if (config.getInt("party.max-size", 8) < 2) {
            getLogger().warning("Invalid party max size, using default of 8");
            config.set("party.max-size", 8);
            saveConfig();
        }
    }

    /**
     * Initialize commands with better error handling
     */
    private boolean initializeCommands() {
        if (commandsInitialized.get()) {
            getLogger().warning("Commands already initialized!");
            return true;
        }

        boolean success = true;

        try {
            getLogger().info("Registering commands...");

            // Register economy-related commands
            success &= registerCommand("balance", new BalanceCommand(economyManager));
            success &= registerCommand("pay", new PayCommand(economyManager));
            success &= registerCommand("bank", new BankCommand(bankManager));
            success &= registerCommand("gems", new GemsCommand(economyManager));
            success &= registerCommand("gempouch", new GemPouchCommand(gemPouchManager));
            success &= registerCommand("eco", new EcoCommand(economyManager));
            success &= registerCommand("vendor", new VendorCommand(this));

            // Register market command
            MarketCommand marketCommand = new MarketCommand();
            success &= registerCommand("market", marketCommand, marketCommand);

            // Register enhanced crate commands - FIXED: Check if crate command exists in plugin.yml
            if (getCommand("crate") != null) {
                CrateCommand crateCommand = new CrateCommand();
                success &= registerCommand("crate", crateCommand, crateCommand);
            }

            // Register mob and spawner related commands
            spawnerCommand = new SpawnerCommand(mobManager);
            success &= registerCommand("spawner", spawnerCommand, spawnerCommand);
            success &= registerCommand("spawnmob", new SpawnMobCommand(mobManager));
            success &= registerCommand("mobinfo", new MobInfoCommand(mobManager));
            success &= registerCommand("togglespawners", new ToggleSpawnersCommand(mobManager));
            success &= registerCommand("boss", new BossCommand(mobManager));

            // Register drop-related commands
            success &= registerCommand("droprate", new DropRateCommand(dropsManager));
            success &= registerCommand("lootbuff", new LootBuffCommand(lootBuffManager));
            success &= registerCommand("elitedrop", new EliteDropsCommand());

            // Register teleport-related commands
            success &= registerCommand("teleportbook", new TeleportBookCommand());
            success &= registerCommand("teleport", new TeleportCommand());

            // Register mount-related commands
            success &= registerCommand("mount", new MountCommand());

            // Register player mechanics commands
            success &= registerCommand("toggles", new TogglesCommand());
            success &= registerCommand("alignment", new AlignmentCommand(alignmentMechanics));
            success &= registerCommand("invsee", new InvseeCommand());

            // Register item-related commands
            success &= registerCommand("item", new ItemCommand(this));
            success &= registerCommand("journal", new JournalCommand());
            success &= registerCommand("scroll", new ScrollCommand(scrollManager));
            success &= registerCommand("speedfish", new SpeedfishCommand(speedfishMechanics));
            success &= registerCommand("orb", new OrbCommand(orbManager));

            // Register chat-related commands
            success &= registerCommand("buddy", new BuddiesCommand());
            success &= registerCommand("msg", new MessageCommand());
            success &= registerCommand("r", new ReplyCommand());
            success &= registerCommand("global", new GlobalChatCommand());
            success &= registerCommand("staffchat", new StaffChatCommand());
            success &= registerCommand("chattag", new ChatTagCommand());

            // Register moderation commands
            success &= registerCommand("kick", new KickCommand());
            success &= registerCommand("ban", new BanCommand(ModerationMechanics.getInstance()));
            success &= registerCommand("unban", new UnbanCommand(ModerationMechanics.getInstance()));
            success &= registerCommand("mute", new MuteCommand(ModerationMechanics.getInstance()));
            success &= registerCommand("unmute", new UnmuteCommand(ModerationMechanics.getInstance()));
            success &= registerCommand("vanish", new VanishCommand(this));
            success &= registerCommand("setrank", new SetRankCommand(ModerationMechanics.getInstance()));

            // Register navigation commands
            success &= registerCommand("trail", new TrailCommand(this, pathManager));
            success &= registerCommand("nodemap", new NodeMapCommand(this));

            // Register party-related commands
            success &= registerCommand("p", new PartyCommand());
            success &= registerCommand("paccept", new PAcceptCommand());
            success &= registerCommand("pdecline", new PDeclineCommand());
            success &= registerCommand("pinvite", new PInviteCommand());
            success &= registerCommand("pkick", new PKickCommand());
            success &= registerCommand("pquit", new PQuitCommand());

            commandsInitialized.set(true);
            getLogger().info("Registered " + (success ? "all" : "most") + " commands!");
            return success;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error registering commands", e);
            return false;
        }
    }

    /**
     * Helper method to register commands with better error handling
     */
    private boolean registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        return registerCommand(name, executor, null);
    }

    /**
     * Helper method to register commands with tab completion
     */
    private boolean registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                    org.bukkit.command.TabCompleter tabCompleter) {
        try {
            org.bukkit.command.PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(executor);
                if (tabCompleter != null) {
                    command.setTabCompleter(tabCompleter);
                }
                return true;
            } else {
                getLogger().warning("Command '" + name + "' not found in plugin.yml");
                return false;
            }
        } catch (Exception e) {
            getLogger().warning("Failed to register command '" + name + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Finalize startup tasks
     */
    private void finalizeStartup() {
        // Start the spawner hologram updater after systems are initialized
        if (spawnerCommand != null) {
            SpawnerHologramUpdater.startTask();
            getLogger().info("Spawner hologram updater started!");
        }

        // Initialize ActionBar utility
        ActionBarUtil.init(this);

        // Register event handlers that require all systems to be loaded
        registerEventHandlers();
    }

    private void registerEventHandlers() {
        // Most event handlers are registered within their respective managers
        getLogger().info("Additional event handlers registered");
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Starting YakRealms shutdown...");

            // Shutdown systems in reverse order of initialization
            shutdownSystems();

            // Disconnect from database last
            if (mongoDBManager != null && mongoDBManager.isConnected()) {
                mongoDBManager.disconnect();
            }

            getLogger().info("YakRealms has been disabled cleanly!");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during plugin shutdown", e);
        }
    }

    /**
     * Shutdown all systems in proper order
     */
    private void shutdownSystems() {
        // Shutdown crate system first to complete any pending operations
        if (crateManager != null) {
            crateManager.shutdown();
        }

        // FIXED: Shutdown PlayerMechanics which handles all player systems
        if (playerMechanics != null) {
            playerMechanics.onDisable();
        }

        // Disable other systems in reverse order
        if (partyMechanics != null) {
            partyMechanics.onDisable();
        }
    }

    // Static methods and getters remain the same...
    public static YakRealms getInstance() {
        return instance;
    }

    public static boolean isPatchLockdown() {
        return patchLockdown;
    }

    public static void setPatchLockdown(boolean patchLockdown) {
        YakRealms.patchLockdown = patchLockdown;
    }

    public static int getSessionID() {
        return sessionID;
    }

    public static boolean isT6Enabled() {
        return t6Enabled;
    }

    public static void setT6Enabled(boolean enabled) {
        t6Enabled = enabled;
        if (instance != null) {
            instance.getConfig().set("game.t6-enabled", enabled);
            instance.saveConfig();
            instance.getLogger().info("T6 content is now " + (enabled ? "enabled" : "disabled"));
        }
    }

    public boolean isMobsEnabled() {
        return mobsEnabled;
    }

    public MongoDBManager getMongoDBManager() {
        return mongoDBManager;
    }

    public static void log(String message) {
        if (instance != null) {
            instance.getLogger().info(message);
        }
    }

    public static void warn(String message) {
        if (instance != null) {
            instance.getLogger().warning(message);
        }
    }

    public static void error(String message, Exception e) {
        if (instance != null) {
            instance.getLogger().log(Level.SEVERE, message, e);
        }
    }

    public boolean isDebugMode() {
        return getConfig().getBoolean("debug", false);
    }

    public static void debug(String message) {
        if (instance != null && instance.isDebugMode()) {
            instance.getLogger().info("[DEBUG] " + message);
        }
    }

    // Add all missing getters for existing systems
    public YakPlayerManager getPlayerManager() { return playerManager; }
    public PlayerMechanics getPlayerMechanics() { return playerMechanics; }
    public PartyMechanics getPartyMechanics() { return partyMechanics; }
    public PlayerListenerManager getPlayerListenerManager() { return playerListenerManager; }
    public DashMechanics getDashMechanics() { return dashMechanics; }
    public SpeedfishMechanics getSpeedfishMechanics() { return speedfishMechanics; }
    public MountManager getMountManager() { return mountManager; }
    public ScrollManager getScrollManager() { return scrollManager; }
    public OrbManager getOrbManager() { return orbManager; }
    public Journal getJournalSystem() { return journalSystem; }
    public AlignmentMechanics getAlignmentMechanics() { return alignmentMechanics; }
    public RespawnManager getRespawnManager() { return respawnManager; }
    public DeathRemnantManager getDeathRemnantManager() { return deathRemnantManager; }
    public ChatMechanics getChatMechanics() { return chatMechanics; }
    public CombatMechanics getCombatMechanics() { return combatMechanics; }
    public MagicStaff getMagicStaff() { return magicStaff; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public BankManager getBankManager() { return bankManager; }
    public GemPouchManager getGemPouchManager() { return gemPouchManager; }
    public VendorManager getVendorManager() { return vendorManager; }
    public MarketManager getMarketManager() { return marketManager; }
    public MobManager getMobManager() { return mobManager; }
    public SpawnerCommand getSpawnerCommand() { return spawnerCommand; }
    public DropsManager getDropsManager() { return dropsManager; }
    public DropsHandler getDropsHandler() { return dropsHandler; }
    public LootBuffManager getLootBuffManager() { return lootBuffManager; }
    public TeleportManager getTeleportManager() { return teleportManager; }
    public TeleportBookSystem getTeleportBookSystem() { return teleportBookSystem; }
    public HearthstoneSystem getHearthstoneSystem() { return hearthstoneSystem; }
    public PortalSystem getPortalSystem() { return portalSystem; }
    public TrailSystem getTrailSystem() { return trailSystem; }
    public ParticleSystem getParticleSystem() { return particleSystem; }
    public PathManager getPathManager() { return pathManager; }
    public CrateManager getCrateManager() { return crateManager; }
}