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
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * FIXED: Main plugin class for YakRealms
 * Handles initialization of all core systems with enhanced error handling and modern features
 * Fixed initialization order to prevent duplicate event handlers and health bar issues
 */
public class YakRealms extends JavaPlugin {
    //TODO: Professions, Merchant, Worldboss, Races
    private static YakRealms instance;
    private MongoDBManager mongoDBManager;
    private YakPlayerManager playerManager;
    private PlayerMechanics playerMechanics;
    private PlayerListenerManager playerListenerManager;
    private CombatMechanics combatMechanics;
    private MagicStaff magicStaff;
    private ChatMechanics chatMechanics;
    private AlignmentMechanics alignmentMechanics;
    private RespawnManager respawnManager;
    private DeathRemnantManager deathRemnantManager;
    private PartyMechanics partyMechanics;

    // Player enhancement systems
    private DashMechanics dashMechanics;
    private SpeedfishMechanics speedfishMechanics;

    // Mount system
    private MountManager mountManager;

    // Item systems
    private ScrollManager scrollManager;
    private OrbManager orbManager;
    private Journal journalSystem;

    // Economy systems
    private EconomyManager economyManager;
    private BankManager bankManager;
    private GemPouchManager gemPouchManager;
    private VendorManager vendorManager;

    // Market system
    private MarketManager marketManager;

    // Mob system
    private MobManager mobManager;
    private SpawnerCommand spawnerCommand;

    // Drop systems
    private DropsManager dropsManager;
    private DropsHandler dropsHandler;
    private LootBuffManager lootBuffManager;

    // Teleport systems
    private TeleportManager teleportManager;
    private TeleportBookSystem teleportBookSystem;
    private HearthstoneSystem hearthstoneSystem;
    private PortalSystem portalSystem;

    // World Systems
    private TrailSystem trailSystem;
    private ParticleSystem particleSystem;
    private PathManager pathManager;

    // Crate system - Enhanced
    private CrateManager crateManager;

    // Game settings
    private static boolean patchLockdown = false;
    private static boolean t6Enabled = false;
    private static int sessionID = 0;
    private boolean mobsEnabled = true;
    private boolean spawnerVisibilityDefault = false;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        try {
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

            // FIXED: Initialize systems in proper order to prevent conflicts
            if (!initializeCore()) {
                getLogger().severe("Failed to initialize core systems! Disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (!initializeGameSystems()) {
                getLogger().severe("Failed to initialize game systems! Some features may not work.");
            }

            if (!initializeCommands()) {
                getLogger().warning("Some commands failed to register!");
            }

            // Final startup tasks
            finalizeStartup();

            getLogger().info("YakRealms has been enabled successfully!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Critical error during plugin startup", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            // Shutdown in reverse order of initialization
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
     * FIXED: Initialize core systems in the correct order
     */
    private boolean initializeCore() {
        try {
            // Load game settings first
            loadGameSettings();

            // Initialize database
            if (!initializeDatabase()) {
                return false;
            }

            // FIXED: Initialize player systems in correct order to prevent conflicts
            if (!initializeCorePlayerSystems()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing core systems", e);
            return false;
        }
    }

    /**
     * FIXED: Initialize core player systems in proper order
     */
    private boolean initializeCorePlayerSystems() {
        try {
            // 1. Initialize PlayerMechanics first (this will initialize YakPlayerManager)
            getLogger().info("Initializing PlayerMechanics...");
            playerMechanics = PlayerMechanics.getInstance();
            playerMechanics.onEnable();

            // 2. Get references to initialized systems
            playerManager = playerMechanics.getPlayerManager();
            playerListenerManager = playerMechanics.getListenerManager();

            getLogger().info("Core player systems initialized!");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing core player systems", e);
            return false;
        }
    }

    /**
     * Initialize game-specific systems
     */
    private boolean initializeGameSystems() {
        boolean success = true;

        try {
            // Initialize party mechanics
            success &= initializePartyMechanics();

            // Initialize world mechanics
            success &= initializeWorldMechanics();

            // Initialize alignment mechanics
            success &= initializeAlignmentMechanics();

            // Initialize movement and item systems
            success &= initializePlayerEnhancements();

            // Initialize mount system
            success &= initializeMountSystem();

            // Initialize item systems
            success &= initializeItemSystems();

            // Initialize teleport systems
            success &= initializeTeleportSystems();

            // Initialize chat mechanics
            success &= initializeChatMechanics();

            // Initialize economy systems
            success &= initializeEconomySystems();

            // Initialize market system
            success &= initializeMarketSystem();

            // Initialize combat systems
            success &= initializeCombatSystems();

            // Initialize death and respawn systems
            success &= initializeDeathSystems();

            // Initialize mob system
            success &= initializeMobSystem();

            // Initialize drops system
            success &= initializeDropsSystem();

            // Initialize crate system - Enhanced
            success &= initializeCrateSystem();

            return success;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing game systems", e);
            return false;
        }
    }

    /**
     * Enhanced crate system initialization
     */
    private boolean initializeCrateSystem() {
        try {
            crateManager = CrateManager.getInstance();
            crateManager.initialize();
            getLogger().info("Enhanced crate system initialized successfully!");
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
     * Initialize database with enhanced error handling
     */
    private boolean initializeDatabase() {
        try {
            FileConfiguration config = getConfig();
            mongoDBManager = MongoDBManager.initialize(config, this);

            if (!mongoDBManager.connect()) {
                getLogger().severe("Failed to connect to MongoDB! Players data will not be saved!");
                return false;
            } else {
                getLogger().info("Successfully connected to MongoDB!");
                return true;
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing database", e);
            return false;
        }
    }

    /**
     * Initialize commands with better error handling
     */
    private boolean initializeCommands() {
        boolean success = true;

        try {
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

            // Register enhanced crate commands
            CrateCommand crateCommand = new CrateCommand();
            success &= registerCommand("crate", crateCommand, crateCommand);

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

    private boolean initializePartyMechanics() {
        try {
            partyMechanics = PartyMechanics.getInstance();
            partyMechanics.onEnable();
            getLogger().info("Party mechanics initialized!");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing party mechanics", e);
            return false;
        }
    }

    private boolean initializeWorldMechanics() {
        try {
            // Initialize TrailSystem
            if (this.trailSystem == null) {
                trailSystem = new TrailSystem(this);
                getLogger().info("Trail system initialized!");
            }

            // Initialize ParticleSystem
            particleSystem = new ParticleSystem(this);
            getLogger().info("Particle system initialized!");

            // Initialize PathManager with node system
            World mainWorld = getServer().getWorld("server");
            if (mainWorld == null) {
                mainWorld = getServer().getWorlds().get(0);
            }

            AdvancedNodeMapGenerator nodeGenerator = new AdvancedNodeMapGenerator();
            File nodeMapFile = new File(getDataFolder(), "server_advanced_navgraph.dat");
            List<NavNode> nodes = nodeGenerator.getOrGenerateNodeMap(mainWorld, nodeMapFile);

            // Initialize path manager with nodes
            pathManager = new PathManager(this, particleSystem);
            getLogger().info("Path manager initialized with " + nodes.size() + " navigation nodes!");

            getLogger().info("World mechanics initialized successfully!");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing world mechanics", e);
            return false;
        }
    }

    // Keep all existing getter methods and add the new crate manager getter
    public CrateManager getCrateManager() {
        return crateManager;
    }

    // Add placeholder implementations for missing initialization methods
    private boolean initializeAlignmentMechanics() {
        try {
            alignmentMechanics = AlignmentMechanics.getInstance();
            alignmentMechanics.onEnable();
            getLogger().info("Alignment mechanics initialized!");
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
            getLogger().info("Dash mechanics initialized!");

            speedfishMechanics = new SpeedfishMechanics();
            speedfishMechanics.onEnable();
            getLogger().info("Speedfish mechanics initialized!");
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
            getLogger().info("Mount system initialized successfully!");
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
            getLogger().info("Scroll system initialized!");

            orbManager = OrbManager.getInstance();
            orbManager.initialize();
            getLogger().info("Orb system initialized!");

            journalSystem = new Journal();
            getLogger().info("Journal system initialized!");
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
            getLogger().info("Teleport manager initialized!");

            teleportBookSystem = TeleportBookSystem.getInstance();
            hearthstoneSystem = HearthstoneSystem.getInstance();
            portalSystem = PortalSystem.getInstance();

            getLogger().info("All teleport systems initialized successfully!");
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
            getLogger().info("Chat mechanics initialized!");
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
            getLogger().info("Economy manager initialized!");

            bankManager = BankManager.getInstance();
            bankManager.onEnable();
            getLogger().info("Bank manager initialized!");

            gemPouchManager = GemPouchManager.getInstance();
            gemPouchManager.onEnable();
            getLogger().info("Gem pouch manager initialized!");

            vendorManager = VendorManager.getInstance(this);
            VendorSystemInitializer.initialize(this);
            getLogger().info("Vendor system initialized!");

            getLogger().info("All economy systems initialized successfully!");
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
            getLogger().info("Market system initialized successfully!");
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
            getLogger().info("Combat mechanics initialized!");

            magicStaff = new MagicStaff();
            magicStaff.onEnable();
            getLogger().info("Magic staff system initialized!");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing combat systems", e);
            return false;
        }
    }

    private boolean initializeDeathSystems() {
        try {
            deathRemnantManager = new DeathRemnantManager(this);
            getLogger().info("Death remnant manager initialized!");

            respawnManager = new RespawnManager();
            respawnManager.onEnable();
            getLogger().info("Respawn manager initialized!");

            getLogger().info("All death systems initialized successfully!");
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

            getLogger().info("Mob system initialized successfully!");
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
            getLogger().info("Drops handler initialized!");

            lootBuffManager = LootBuffManager.getInstance();
            lootBuffManager.initialize();
            getLogger().info("Loot buff manager initialized!");

            dropsManager = DropsManager.getInstance();
            dropsManager.initialize();
            getLogger().info("Drops manager initialized!");

            getLogger().info("All drop systems initialized successfully!");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing drop systems", e);
            return false;
        }
    }

    private void registerEventHandlers() {
        // Player manager events are registered in its own onEnable method
        // Most event handlers are registered within their respective managers
    }

    // Keep all existing getter methods and static methods...
    public static YakRealms getInstance() {
        return instance;
    }

    public static boolean isPatchLockdown() {
        return patchLockdown;
    }

    public boolean isMobsEnabled() {
        return mobsEnabled;
    }

    public static void setPatchLockdown(boolean patchLockdown) {
        YakRealms.patchLockdown = patchLockdown;
    }

    public MongoDBManager getMongoDBManager() {
        return mongoDBManager;
    }

    // Keep all other existing getters...
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

    // Keep all other existing methods...
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
}