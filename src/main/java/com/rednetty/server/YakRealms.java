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
 * Main plugin class for YakRealms
 * Handles initialization of all core systems
 */
public class YakRealms extends JavaPlugin {
    //TODO: Professions, Merchant, Gamblers, Worldboss, Races, Crates
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
    private SpawnerCommand spawnerCommand; // Added to track the SpawnerCommand instance

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

    // Game settings
    private static boolean patchLockdown = false;
    private static boolean t6Enabled = false;
    private static int sessionID = 0;
    private boolean mobsEnabled = true;
    private boolean spawnerVisibilityDefault = false; // Default visibility setting

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        // Ensure config directory exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Load default config if it doesn't exist
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        //Creates a sessionID for this run-time of the plugin
        sessionID = ThreadLocalRandom.current().nextInt();

        // Load game settings
        loadGameSettings();

        // Initialize database
        initializeDatabase();

        // Initialize player manager
        initializePlayerManager();

        // Initialize player mechanics
        initializePlayerMechanics();

        // Initialize party mechanics
        initializePartyMechanics();

        // Initialize world mechanics and navigation systems
        initializeWorldMechanics();

        // Initialize alignment mechanics
        initializeAlignmentMechanics();

        // Initialize player listener system
        initializePlayerListenerSystem();

        // Initialize movement and item systems
        initializePlayerEnhancements();

        // Initialize mount system
        initializeMountSystem();

        // Initialize item systems
        initializeItemSystems();

        // Initialize teleport systems
        initializeTeleportSystems();

        // Initialize chat mechanics
        initializeChatMechanics();

        // Initialize economy systems
        initializeEconomySystems();

        // Initialize market system
        initializeMarketSystem();

        // Initialize combat systems
        initializeCombatSystems();

        // Initialize death and respawn systems
        initializeDeathSystems();

        // Initialize mob system
        initializeMobSystem();

        // Initialize drops system
        initializeDropsSystem();

        // Register event handlers
        registerEventHandlers();

        // Register commands
        registerCommands();

        // Start the spawner hologram updater after systems are initialized
        if (spawnerCommand != null) {
            SpawnerHologramUpdater.startTask();
            getLogger().info("Spawner hologram updater started!");
        }

        ActionBarUtil.init(this);
        getLogger().info("YakRealms has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save all player data
        if (playerManager != null) {
            playerManager.onDisable();
        }

        // Disable player mechanics
        if (playerMechanics != null) {
            playerMechanics.onDisable();
        }

        // Disable party mechanics
        if (partyMechanics != null) {
            partyMechanics.onDisable();
        }

        // Disable player listener system
        if (playerListenerManager != null) {
            playerListenerManager.onDisable();
        }

        // Disable movement and item systems
        if (dashMechanics != null) {
            dashMechanics.onDisable();
        }

        if (speedfishMechanics != null) {
            speedfishMechanics.onDisable();
        }

        // Disable mount system
        if (mountManager != null) {
            mountManager.onDisable();
        }

        // Disable alignment mechanics
        if (alignmentMechanics != null) {
            alignmentMechanics.onDisable();
        }

        // Disable item systems
        if (scrollManager != null) {
            // ScrollManager doesn't have an explicit onDisable method
        }

        // Disable teleport systems
        if (teleportManager != null) {
            teleportManager.onDisable();
        }

        // Disable chat mechanics
        if (chatMechanics != null) {
            chatMechanics.onDisable();
        }

        // Disable death and respawn systems
        if (respawnManager != null) {
            respawnManager.onDisable();
        }

        if (deathRemnantManager != null) {
            // Clean up any remnants
        }

        // Disable economy systems
        if (bankManager != null) {
            bankManager.onDisable();
        }

        if (economyManager != null) {
            economyManager.onDisable();
        }

        if (gemPouchManager != null) {
            gemPouchManager.onDisable();
        }

        // Disable vendor system
        if (vendorManager != null) {
            vendorManager.shutdown();
        }

        // Disable market system
        if (marketManager != null) {
            marketManager.onDisable();
        }

        // Disable combat systems
        if (combatMechanics != null) {
            combatMechanics.onDisable();
        }

        if (magicStaff != null) {
            magicStaff.onDisable();
        }

        // Clean up hologram system
        HologramManager.cleanup();

        // Disable mob system
        if (mobManager != null) {
            mobManager.shutdown();
        }

        // Shutdown drop systems
        if (dropsManager != null) {
            dropsManager.shutdown();
        }

        if (dropsHandler != null) {
            dropsHandler.shutdown();
        }

        if (lootBuffManager != null) {
            lootBuffManager.shutdown();
        }

        // Shutdown world mechanics
        if (pathManager != null) {
            pathManager.shutdown();
        }

        if (particleSystem != null) {
            particleSystem.cleanup();
        }

        if (trailSystem != null) {
            trailSystem.onDisable();
        }

        // Disconnect from database
        if (mongoDBManager != null && mongoDBManager.isConnected()) {
            mongoDBManager.disconnect();
        }

        getLogger().info("YakRealms has been disabled!");
    }

    /**
     * Loads game settings from config
     */
    private void loadGameSettings() {
        FileConfiguration config = getConfig();

        // Game settings
        t6Enabled = config.getBoolean("game.t6-enabled", false);
        getLogger().info("T6 content is " + (t6Enabled ? "enabled" : "disabled"));

        // Mob system settings
        mobsEnabled = config.getBoolean("mechanics.mobs.enabled", true);
        getLogger().info("Mob spawning is " + (mobsEnabled ? "enabled" : "disabled"));

        // Default spawner visibility setting
        spawnerVisibilityDefault = config.getBoolean("mechanics.mobs.spawner-default-visibility", false);
        getLogger().info("Default spawner visibility is " + (spawnerVisibilityDefault ? "visible" : "hidden"));
    }

    public static int getSessionID() {
        return sessionID;
    }

    /**
     * Initializes the MongoDB database connection
     */
    private void initializeDatabase() {
        try {
            // Initialize MongoDB manager with config and plugin instance
            FileConfiguration config = getConfig();
            mongoDBManager = MongoDBManager.initialize(config, this);

            // Connect to database
            if (!mongoDBManager.connect()) {
                getLogger().severe("Failed to connect to MongoDB! Players data will not be saved!");
            } else {
                getLogger().info("Successfully connected to MongoDB!");
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing database", e);
        }
    }

    /**
     * Initializes the player manager system
     */
    private void initializePlayerManager() {
        try {
            playerManager = YakPlayerManager.getInstance();
            playerManager.onEnable();
            getLogger().info("Player manager initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing player manager", e);
        }
    }

    /**
     * Initializes the player listener system
     */
    private void initializePlayerListenerSystem() {
        try {
            playerListenerManager = PlayerListenerManager.getInstance();
            playerListenerManager.onEnable();
            getLogger().info("Player listener system initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing player listener system", e);
        }
    }

    /**
     * Initializes player mechanics (energy, toggles, buddies)
     */
    private void initializePlayerMechanics() {
        try {
            playerMechanics = PlayerMechanics.getInstance();
            playerMechanics.onEnable();
            getLogger().info("Player mechanics initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing player mechanics", e);
        }
    }

    /**
     * Initializes party mechanics system
     */
    private void initializePartyMechanics() {
        try {
            partyMechanics = PartyMechanics.getInstance();
            partyMechanics.onEnable();
            getLogger().info("Party mechanics initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing party mechanics", e);
        }
    }

    /**
     * Initializes world mechanics including trails, particles, and navigation
     */
    private void initializeWorldMechanics() {
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
            try {
                // Generate or load navigation node map for the world
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
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error initializing path manager", e);
            }

            getLogger().info("World mechanics initialized successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing world mechanics", e);
        }
    }

    /**
     * Initializes alignment mechanics
     */
    private void initializeAlignmentMechanics() {
        try {
            alignmentMechanics = AlignmentMechanics.getInstance();
            alignmentMechanics.onEnable();
            getLogger().info("Alignment mechanics initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing alignment mechanics", e);
        }
    }

    /**
     * Initializes player enhancement systems (dash, speedfish)
     */
    private void initializePlayerEnhancements() {
        try {
            // Initialize dash mechanics
            dashMechanics = new DashMechanics();
            dashMechanics.onEnable();
            getLogger().info("Dash mechanics initialized!");

            // Initialize speedfish mechanics
            speedfishMechanics = new SpeedfishMechanics();
            speedfishMechanics.onEnable();
            getLogger().info("Speedfish mechanics initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing player enhancement systems", e);
        }
    }

    /**
     * Initializes mount system
     */
    private void initializeMountSystem() {
        try {
            // Initialize mount manager
            mountManager = MountManager.getInstance();
            mountManager.onEnable();
            getLogger().info("Mount system initialized successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing mount system", e);
        }
    }

    /**
     * Initializes item systems (scrolls, orbs, journals)
     */
    private void initializeItemSystems() {
        try {
            // Initialize scroll manager
            scrollManager = ScrollManager.getInstance();
            scrollManager.initialize();
            getLogger().info("Scroll system initialized!");

            // Initialize orb manager
            orbManager = OrbManager.getInstance();
            orbManager.initialize();
            getLogger().info("Orb system initialized!");

            // Journal system doesn't have an explicit initialize method
            journalSystem = new Journal();
            getLogger().info("Journal system initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing item systems", e);
        }
    }

    /**
     * Initializes teleport systems
     */
    private void initializeTeleportSystems() {
        try {
            // Initialize teleport manager first since others depend on it
            teleportManager = TeleportManager.getInstance();
            teleportManager.onEnable();
            getLogger().info("Teleport manager initialized!");

            // These systems are initialized within the TeleportManager.onEnable()
            // but we'll get references to them here for access from the main class
            teleportBookSystem = TeleportBookSystem.getInstance();
            hearthstoneSystem = HearthstoneSystem.getInstance();
            portalSystem = PortalSystem.getInstance();

            getLogger().info("All teleport systems initialized successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing teleport systems", e);
        }
    }

    /**
     * Initializes chat mechanics
     */
    private void initializeChatMechanics() {
        try {
            chatMechanics = ChatMechanics.getInstance();
            chatMechanics.onEnable();
            getLogger().info("Chat mechanics initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing chat mechanics", e);
        }
    }

    /**
     * Initializes death and respawn systems
     */
    private void initializeDeathSystems() {
        try {
            // Initialize the DeathRemnantManager first
            deathRemnantManager = new DeathRemnantManager(this);
            getLogger().info("Death remnant manager initialized!");

            // Initialize the RespawnManager
            respawnManager = new RespawnManager();
            respawnManager.onEnable();
            getLogger().info("Respawn manager initialized!");

            getLogger().info("All death systems initialized successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing death systems", e);
        }
    }

    /**
     * Initializes economy systems (economy manager, bank, gem pouches)
     */
    private void initializeEconomySystems() {
        try {
            // Initialize economy manager
            economyManager = EconomyManager.getInstance();
            economyManager.onEnable();
            getLogger().info("Economy manager initialized!");

            // Initialize bank manager
            bankManager = BankManager.getInstance();
            bankManager.onEnable();
            getLogger().info("Bank manager initialized!");

            // Initialize gem pouch manager
            gemPouchManager = GemPouchManager.getInstance();
            gemPouchManager.onEnable();
            getLogger().info("Gem pouch manager initialized!");

            // Initialize vendor system
            try {
                vendorManager = VendorManager.getInstance(this);
                VendorSystemInitializer.initialize(this);
                getLogger().info("Vendor system initialized!");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error initializing vendor system", e);
            }

            getLogger().info("All economy systems initialized successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing economy systems", e);
        }
    }

    /**
     * Initializes the market system
     */
    private void initializeMarketSystem() {
        try {
            marketManager = MarketManager.getInstance();
            marketManager.onEnable();
            getLogger().info("Market system initialized successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing market system", e);
        }
    }

    /**
     * Initializes combat systems (damage, magic staffs)
     */
    private void initializeCombatSystems() {
        try {
            // Initialize combat mechanics
            combatMechanics = new CombatMechanics();
            combatMechanics.onEnable();
            getLogger().info("Combat mechanics initialized!");

            // Initialize magic staff system
            magicStaff = new MagicStaff();
            magicStaff.onEnable();
            getLogger().info("Magic staff system initialized!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing combat systems", e);
        }
    }

    /**
     * Initializes the new mob system
     */
    private void initializeMobSystem() {
        try {
            // Initialize the new mob manager
            mobManager = MobManager.getInstance();
            mobManager.initialize();
            mobManager.setSpawnersEnabled(mobsEnabled);

            // Save reference to spawner configuration
            FileConfiguration config = getConfig();
            config.set("mechanics.mobs.spawner-default-visibility", spawnerVisibilityDefault);
            saveConfig();

            getLogger().info("Mob system initialized successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing mob system", e);
        }
    }

    /**
     * Initializes the drops system
     */
    private void initializeDropsSystem() {
        try {
            // Initialize drops handler first (required by other systems)
            dropsHandler = DropsHandler.getInstance();
            dropsHandler.initialize();
            getLogger().info("Drops handler initialized!");

            // Initialize loot buff manager
            lootBuffManager = LootBuffManager.getInstance();
            lootBuffManager.initialize();
            getLogger().info("Loot buff manager initialized!");

            // Initialize drops manager
            dropsManager = DropsManager.getInstance();
            dropsManager.initialize();
            getLogger().info("Drops manager initialized!");

            getLogger().info("All drop systems initialized successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing drop systems", e);
        }
    }

    /**
     * Registers all event handlers
     */
    private void registerEventHandlers() {
        // Player manager events are registered in its own onEnable method
        // Most event handlers are registered within their respective managers
    }

    /**
     * Registers all commands
     */
    private void registerCommands() {
        // Register economy-related commands
        getCommand("balance").setExecutor(new BalanceCommand(economyManager));
        getCommand("pay").setExecutor(new PayCommand(economyManager));
        getCommand("bank").setExecutor(new BankCommand(bankManager));
        getCommand("gems").setExecutor(new GemsCommand(economyManager));
        getCommand("gempouch").setExecutor(new GemPouchCommand(gemPouchManager));
        getCommand("eco").setExecutor(new EcoCommand(economyManager));
        getCommand("vendor").setExecutor(new VendorCommand(this));

        // Register market command
        MarketCommand marketCommand = new MarketCommand();
        getCommand("market").setExecutor(marketCommand);
        getCommand("market").setTabCompleter(marketCommand);

        // Register mob and spawner related commands - Updated with enhanced SpawnerCommand
        spawnerCommand = new SpawnerCommand(mobManager);
        getCommand("spawner").setExecutor(spawnerCommand);
        getCommand("spawner").setTabCompleter(spawnerCommand);

        getCommand("spawnmob").setExecutor(new SpawnMobCommand(mobManager));
        getCommand("mobinfo").setExecutor(new MobInfoCommand(mobManager));
        getCommand("togglespawners").setExecutor(new ToggleSpawnersCommand(mobManager));
        getCommand("boss").setExecutor(new BossCommand(mobManager));

        // Register drop-related commands
        getCommand("droprate").setExecutor(new DropRateCommand(dropsManager));
        getCommand("lootbuff").setExecutor(new LootBuffCommand(lootBuffManager));
        getCommand("elitedrop").setExecutor(new EliteDropsCommand());

        // Register teleport-related commands
        getCommand("teleportbook").setExecutor(new TeleportBookCommand());
        getCommand("teleport").setExecutor(new TeleportCommand());

        // Register mount-related commands
        getCommand("mount").setExecutor(new MountCommand());

        // Register player mechanics commands
        getCommand("toggles").setExecutor(new TogglesCommand());
        getCommand("alignment").setExecutor(new AlignmentCommand(alignmentMechanics));
        getCommand("invsee").setExecutor(new InvseeCommand());

        // Register item-related commands
        getCommand("item").setExecutor(new ItemCommand(this));
        getCommand("journal").setExecutor(new JournalCommand());
        getCommand("scroll").setExecutor(new ScrollCommand(scrollManager));
        getCommand("speedfish").setExecutor(new SpeedfishCommand(speedfishMechanics));
        getCommand("orb").setExecutor(new OrbCommand(orbManager));

        // Register chat-related commands
        getCommand("buddy").setExecutor(new BuddiesCommand());
        getCommand("msg").setExecutor(new MessageCommand());
        getCommand("r").setExecutor(new ReplyCommand());
        getCommand("global").setExecutor(new GlobalChatCommand());
        getCommand("staffchat").setExecutor(new StaffChatCommand());
        getCommand("chattag").setExecutor(new ChatTagCommand());

        // Register moderation commands
        getCommand("kick").setExecutor(new KickCommand());
        getCommand("ban").setExecutor(new BanCommand(ModerationMechanics.getInstance()));
        getCommand("unban").setExecutor(new UnbanCommand(ModerationMechanics.getInstance())); // New
        getCommand("mute").setExecutor(new MuteCommand(ModerationMechanics.getInstance()));
        getCommand("unmute").setExecutor(new UnmuteCommand(ModerationMechanics.getInstance()));
        getCommand("vanish").setExecutor(new VanishCommand(this));
        getCommand("setrank").setExecutor(new SetRankCommand(ModerationMechanics.getInstance())); // New

        // Register navigation commands
        getCommand("trail").setExecutor(new TrailCommand(this, pathManager));
        getCommand("nodemap").setExecutor(new NodeMapCommand(this));

        // Register party-related commands
        getCommand("p").setExecutor(new PartyCommand());
        getCommand("paccept").setExecutor(new PAcceptCommand());
        getCommand("pdecline").setExecutor(new PDeclineCommand());
        getCommand("pinvite").setExecutor(new PInviteCommand());
        getCommand("pkick").setExecutor(new PKickCommand());
        getCommand("pquit").setExecutor(new PQuitCommand());

        getLogger().info("Registered all commands!");
    }

    /**
     * Gets the plugin instance
     *
     * @return The YakRealms instance
     */
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

    /**
     * Gets the MongoDB manager
     *
     * @return The MongoDB manager
     */
    public MongoDBManager getMongoDBManager() {
        return mongoDBManager;
    }

    /**
     * Gets the player manager
     *
     * @return The player manager
     */
    public YakPlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * Gets the player mechanics
     *
     * @return The player mechanics
     */
    public PlayerMechanics getPlayerMechanics() {
        return playerMechanics;
    }

    /**
     * Gets the party mechanics
     *
     * @return The party mechanics
     */
    public PartyMechanics getPartyMechanics() {
        return partyMechanics;
    }

    /**
     * Gets the player listener manager
     *
     * @return The player listener manager
     */
    public PlayerListenerManager getPlayerListenerManager() {
        return playerListenerManager;
    }

    /**
     * Gets the dash mechanics
     *
     * @return The dash mechanics
     */
    public DashMechanics getDashMechanics() {
        return dashMechanics;
    }

    /**
     * Gets the speedfish mechanics
     *
     * @return The speedfish mechanics
     */
    public SpeedfishMechanics getSpeedfishMechanics() {
        return speedfishMechanics;
    }

    /**
     * Gets the mount manager
     *
     * @return The mount manager
     */
    public MountManager getMountManager() {
        return mountManager;
    }

    /**
     * Gets the scroll manager
     *
     * @return The scroll manager
     */
    public ScrollManager getScrollManager() {
        return scrollManager;
    }

    /**
     * Gets the orb manager
     *
     * @return The orb manager
     */
    public OrbManager getOrbManager() {
        return orbManager;
    }

    /**
     * Gets the journal system
     *
     * @return The journal system
     */
    public Journal getJournalSystem() {
        return journalSystem;
    }

    /**
     * Gets the alignment mechanics
     *
     * @return The alignment mechanics
     */
    public AlignmentMechanics getAlignmentMechanics() {
        return alignmentMechanics;
    }

    /**
     * Gets the respawn manager
     *
     * @return The respawn manager
     */
    public RespawnManager getRespawnManager() {
        return respawnManager;
    }

    /**
     * Gets the death remnant manager
     *
     * @return The death remnant manager
     */
    public DeathRemnantManager getDeathRemnantManager() {
        return deathRemnantManager;
    }

    /**
     * Gets the chat mechanics
     *
     * @return The chat mechanics
     */
    public ChatMechanics getChatMechanics() {
        return chatMechanics;
    }

    /**
     * Gets the combat mechanics
     *
     * @return The combat mechanics
     */
    public CombatMechanics getCombatMechanics() {
        return combatMechanics;
    }

    /**
     * Gets the magic staff system
     *
     * @return The magic staff system
     */
    public MagicStaff getMagicStaff() {
        return magicStaff;
    }

    /**
     * Gets the economy manager
     *
     * @return The economy manager
     */
    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    /**
     * Gets the bank manager
     *
     * @return The bank manager
     */
    public BankManager getBankManager() {
        return bankManager;
    }

    /**
     * Gets the gem pouch manager
     *
     * @return The gem pouch manager
     */
    public GemPouchManager getGemPouchManager() {
        return gemPouchManager;
    }

    /**
     * Gets the vendor manager
     *
     * @return The vendor manager
     */
    public VendorManager getVendorManager() {
        return vendorManager;
    }

    /**
     * Gets the market manager
     *
     * @return The market manager
     */
    public MarketManager getMarketManager() {
        return marketManager;
    }

    /**
     * Gets the mob manager
     *
     * @return The mob manager
     */
    public MobManager getMobManager() {
        return mobManager;
    }

    /**
     * Gets the spawner command instance
     *
     * @return The spawner command instance
     */
    public SpawnerCommand getSpawnerCommand() {
        return spawnerCommand;
    }

    /**
     * Gets the drops manager
     *
     * @return The drops manager
     */
    public DropsManager getDropsManager() {
        return dropsManager;
    }

    /**
     * Gets the drops handler
     *
     * @return The drops handler
     */
    public DropsHandler getDropsHandler() {
        return dropsHandler;
    }

    /**
     * Gets the loot buff manager
     *
     * @return The loot buff manager
     */
    public LootBuffManager getLootBuffManager() {
        return lootBuffManager;
    }

    /**
     * Gets the teleport manager
     *
     * @return The teleport manager
     */
    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    /**
     * Gets the teleport book system
     *
     * @return The teleport book system
     */
    public TeleportBookSystem getTeleportBookSystem() {
        return teleportBookSystem;
    }

    /**
     * Gets the hearthstone system
     *
     * @return The hearthstone system
     */
    public HearthstoneSystem getHearthstoneSystem() {
        return hearthstoneSystem;
    }

    /**
     * Gets the portal system
     *
     * @return The portal system
     */
    public PortalSystem getPortalSystem() {
        return portalSystem;
    }

    /**
     * Gets the trail system
     *
     * @return The trail system
     */
    public TrailSystem getTrailSystem() {
        return trailSystem;
    }

    /**
     * Gets the particle system
     *
     * @return The particle system
     */
    public ParticleSystem getParticleSystem() {
        return particleSystem;
    }

    /**
     * Gets the path manager
     *
     * @return The path manager
     */
    public PathManager getPathManager() {
        return pathManager;
    }

    /**
     * Check if Tier 6 content is enabled
     *
     * @return true if T6 is enabled
     */
    public static boolean isT6Enabled() {
        return t6Enabled;
    }

    /**
     * Set T6 content enabled status
     *
     * @param enabled true to enable T6 content
     */
    public static void setT6Enabled(boolean enabled) {
        t6Enabled = enabled;
        if (instance != null) {
            instance.getConfig().set("game.t6-enabled", enabled);
            instance.saveConfig();
            instance.getLogger().info("T6 content is now " + (enabled ? "enabled" : "disabled"));
        }
    }

    /**
     * Check if mobs are enabled
     *
     * @return true if mobs are enabled
     */
    public boolean areMobsEnabled() {
        return mobsEnabled;
    }

    /**
     * Toggle mob spawning
     *
     * @param enabled true to enable mob spawning
     */
    public void setMobsEnabled(boolean enabled) {
        this.mobsEnabled = enabled;
        if (mobManager != null) {
            mobManager.setSpawnersEnabled(enabled);
        }
        getConfig().set("mechanics.mobs.enabled", enabled);
        saveConfig();
        getLogger().info("Mob spawning is now " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Get default spawner visibility setting
     *
     * @return true if spawners should be visible by default
     */
    public boolean getDefaultSpawnerVisibility() {
        return spawnerVisibilityDefault;
    }

    /**
     * Set default spawner visibility
     *
     * @param visible true to make spawners visible by default
     */
    public void setDefaultSpawnerVisibility(boolean visible) {
        this.spawnerVisibilityDefault = visible;
        getConfig().set("mechanics.mobs.spawner-default-visibility", visible);
        saveConfig();
        getLogger().info("Default spawner visibility is now " + (visible ? "visible" : "hidden"));
    }

    /**
     * Log an info message to the console
     *
     * @param message The message to log
     */
    public static void log(String message) {
        if (instance != null) {
            instance.getLogger().info(message);
        }
    }

    /**
     * Log a warning message to the console
     *
     * @param message The message to log
     */
    public static void warn(String message) {
        if (instance != null) {
            instance.getLogger().warning(message);
        }
    }

    /**
     * Log an error message to the console
     *
     * @param message The message to log
     * @param e       The exception that occurred
     */
    public static void error(String message, Exception e) {
        if (instance != null) {
            instance.getLogger().log(Level.SEVERE, message, e);
        }
    }

    /**
     * Check if debug mode is enabled
     *
     * @return true if debug mode is enabled
     */
    public boolean isDebugMode() {
        return false;
    }

    /**
     * Log a debug message if debug mode is enabled
     *
     * @param message The message to log
     */
    public static void debug(String message) {
        if (instance != null && instance.isDebugMode()) {
            instance.getLogger().info("[DEBUG] " + message);
        }
    }
}