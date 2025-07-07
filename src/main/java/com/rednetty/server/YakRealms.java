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
import com.rednetty.server.mechanics.economy.merchant.MerchantSystem;
import com.rednetty.server.mechanics.item.crates.CrateManager;
import com.rednetty.server.commands.admin.CrateCommand;
import com.rednetty.server.mechanics.item.drops.DropsHandler;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.economy.BankManager;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.GemPouchManager;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import com.rednetty.server.mechanics.economy.vendors.VendorSystemInitializer;
import com.rednetty.server.mechanics.item.Journal;
import com.rednetty.server.mechanics.item.MenuItemManager;
import com.rednetty.server.mechanics.item.MenuSystemInitializer;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.mechanics.item.awakening.AwakeningStoneSystem;
import com.rednetty.server.mechanics.item.binding.BindingRuneSystem;
import com.rednetty.server.mechanics.item.corruption.CorruptionSystem;
import com.rednetty.server.mechanics.item.essence.EssenceCrystalSystem;
import com.rednetty.server.mechanics.item.forge.ForgeHammerSystem;
import com.rednetty.server.mechanics.world.lootchests.LootChestManager;
import com.rednetty.server.mechanics.economy.market.MarketManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.tasks.SpawnerHologramUpdater;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.mounts.MountManager;
import com.rednetty.server.mechanics.party.PartyMechanics;
import com.rednetty.server.mechanics.player.PlayerMechanics;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.items.SpeedfishMechanics;
import com.rednetty.server.mechanics.player.movement.DashMechanics;
import com.rednetty.server.mechanics.teleport.HearthstoneSystem;
import com.rednetty.server.mechanics.teleport.PortalSystem;
import com.rednetty.server.mechanics.teleport.TeleportBookSystem;
import com.rednetty.server.mechanics.teleport.TeleportManager;
import com.rednetty.server.mechanics.world.trail.TrailSystem;
import com.rednetty.server.mechanics.world.trail.pathing.ParticleSystem;
import com.rednetty.server.mechanics.world.trail.pathing.PathManager;
import com.rednetty.server.mechanics.world.trail.pathing.nodes.AdvancedNodeMapGenerator;
import com.rednetty.server.mechanics.world.trail.pathing.nodes.NavNode;
import com.rednetty.server.utils.ui.ActionBarUtil;
import com.rednetty.server.commands.admin.MenuCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Main plugin class for YakRealms with fixed initialization order
 */
public class YakRealms extends JavaPlugin {

    private static YakRealms instance;

    // Core systems - initialized in order
    private MongoDBManager mongoDBManager;
    private YakPlayerManager playerManager;
    private PlayerMechanics playerMechanics;

    // All other systems
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
    private MenuItemManager menuItemManager;

    // New Item Enhancement Systems
    private AwakeningStoneSystem awakeningStoneSystem;
    private BindingRuneSystem bindingRuneSystem;
    private CorruptionSystem corruptionSystem;
    private EssenceCrystalSystem essenceCrystalSystem;
    private ForgeHammerSystem forgeHammerSystem;

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
    private LootChestManager lootChestManager;
    private MerchantSystem merchantSystem;
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
            getLogger().info("Starting YakRealms initialization...");

            // Ensure data folder exists
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            // Save default config
            saveDefaultConfig();
            reloadConfig();

            // Generate session ID
            sessionID = ThreadLocalRandom.current().nextInt();

            // Load game settings
            loadGameSettings();

            // Initialize in strict order to prevent circular dependencies
            if (!initializeDatabase()) {
                getLogger().severe("Failed to initialize database!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (!initializePlayerSystems()) {
                getLogger().severe("Failed to initialize player systems!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (!initializeGameSystems()) {
                getLogger().severe("Failed to initialize game systems!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (!initializeCommands()) {
                getLogger().warning("Some commands failed to register!");
            }

            finalizeStartup();
            getLogger().info("YakRealms has been enabled successfully!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Critical error during plugin startup", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Initialize database connection
     */
    private boolean initializeDatabase() {
        try {
            getLogger().info("Initializing database connection...");
            mongoDBManager = MongoDBManager.initialize(getConfig(), this);

            if (!mongoDBManager.connect()) {
                getLogger().severe("Failed to connect to database!");
                return false;
            }

            getLogger().info("Database connected successfully");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing database", e);
            return false;
        }
    }

    /**
     * Initialize player management systems
     */
    private boolean initializePlayerSystems() {
        try {
            getLogger().info("Initializing player systems...");

            // Initialize YakPlayerManager first (it doesn't depend on PlayerMechanics)
            playerManager = YakPlayerManager.getInstance();
            playerManager.onEnable();

            // Wait a moment for player manager to be ready
            Thread.sleep(1000);

            // Then initialize PlayerMechanics
            playerMechanics = PlayerMechanics.getInstance();
            playerMechanics.onEnable();

            getLogger().info("Player systems initialized successfully");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing player systems", e);
            return false;
        }
    }

    /**
     * Initialize all game systems
     */
    private boolean initializeGameSystems() {
        boolean allSuccess = true;

        try {
            getLogger().info("Initializing game systems...");

            // Initialize in dependency order
            allSuccess &= safeInitialize("Party Mechanics", this::initializePartyMechanics);
            allSuccess &= safeInitialize("Alignment Mechanics", this::initializeAlignmentMechanics);
            allSuccess &= safeInitialize("Player Movement", this::initializePlayerMovement);
            allSuccess &= safeInitialize("Mount System", this::initializeMountSystem);
            allSuccess &= safeInitialize("Item Systems", this::initializeItemSystems);
            allSuccess &= safeInitialize("Item Enhancement Systems", this::initializeItemEnhancementSystems);
            allSuccess &= safeInitialize("Chat Mechanics", this::initializeChatMechanics);
            allSuccess &= safeInitialize("Economy Systems", this::initializeEconomySystems);
            allSuccess &= safeInitialize("Market System", this::initializeMarketSystem);
            allSuccess &= safeInitialize("Combat Systems", this::initializeCombatSystems);
            allSuccess &= safeInitialize("Death Systems", this::initializeDeathSystems);
            allSuccess &= safeInitialize("Mob System", this::initializeMobSystem);
            allSuccess &= safeInitialize("Merchant System", this::initializeMerchantSystem);
            allSuccess &= safeInitialize("Drops System", this::initializeDropsSystem);
            allSuccess &= safeInitialize("Teleport Systems", this::initializeTeleportSystems);
            allSuccess &= safeInitialize("World Systems", this::initializeWorldSystems);
            allSuccess &= safeInitialize("Crate System", this::initializeCrateSystem);
            allSuccess &= safeInitialize("Loot Chest System", this::initializeLootChestSystem);

            getLogger().info("Game systems initialization completed");
            return allSuccess;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing game systems", e);
            return false;
        }
    }

    /**
     * Safe initialization wrapper
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

    // Individual system initialization methods
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

    private boolean initializePlayerMovement() {
        try {
            dashMechanics = new DashMechanics();
            dashMechanics.onEnable();

            speedfishMechanics = new SpeedfishMechanics();
            speedfishMechanics.onEnable();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing player movement", e);
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

            // Initialize Menu Item System
            menuItemManager = MenuItemManager.getInstance();
            menuItemManager.initialize();

            // Initialize the menu system after a small delay to ensure all dependencies are ready
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    MenuSystemInitializer.initialize();
                    getLogger().info("Menu Item System initialized successfully!");
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Failed to initialize Menu Item System", e);
                }
            }, 20L); // 1 second delay

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing item systems", e);
            return false;
        }
    }

    /**
     * Initialize all item enhancement systems
     */
    private boolean initializeItemEnhancementSystems() {
        try {
            getLogger().info("Initializing item enhancement systems...");

            // Initialize Awakening Stone System
            awakeningStoneSystem = AwakeningStoneSystem.getInstance();
            awakeningStoneSystem.initialize();
            getLogger().info("Awakening Stone System initialized successfully!");

            // Initialize Binding Rune System
            bindingRuneSystem = BindingRuneSystem.getInstance();
            bindingRuneSystem.initialize();
            getLogger().info("Binding Rune System initialized successfully!");

            // Initialize Corruption System
            corruptionSystem = CorruptionSystem.getInstance();
            corruptionSystem.initialize();
            getLogger().info("Corruption System initialized successfully!");

            // Initialize Essence Crystal System
            essenceCrystalSystem = EssenceCrystalSystem.getInstance();
            essenceCrystalSystem.initialize();
            getLogger().info("Essence Crystal System initialized successfully!");

            // Initialize Forge Hammer System
            forgeHammerSystem = ForgeHammerSystem.getInstance();
            forgeHammerSystem.initialize();
            getLogger().info("Forge Hammer System initialized successfully!");

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing item enhancement systems", e);
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

    private boolean initializeWorldSystems() {
        try {
            trailSystem = new TrailSystem(this);
            particleSystem = new ParticleSystem(this);

            // Initialize path manager if possible
            try {
                List<World> worlds = getServer().getWorlds();
                if (!worlds.isEmpty()) {
                    World mainWorld = worlds.get(0);
                    AdvancedNodeMapGenerator nodeGenerator = new AdvancedNodeMapGenerator();
                    File nodeMapFile = new File(getDataFolder(), mainWorld.getName() + "_advanced_navgraph.dat");
                    List<NavNode> nodes = nodeGenerator.getOrGenerateNodeMap(mainWorld, nodeMapFile);
                    pathManager = new PathManager(this, particleSystem);
                    getLogger().info("Path manager initialized with " + nodes.size() + " navigation nodes");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to initialize PathManager", e);
            }

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing world systems", e);
            return false;
        }
    }

    private boolean initializeCrateSystem() {
        try {
            getLogger().info("Initializing Enhanced Crate System...");
            crateManager = CrateManager.getInstance();
            crateManager.initialize();

            // Log crate system status
            var stats = crateManager.getStatistics();
            getLogger().info("Crate System loaded successfully!");
            getLogger().info("- Configurations: " + stats.get("configurationsLoaded"));
            getLogger().info("- Features: " + stats.get("featuresEnabled"));
            getLogger().info("- Factory Version: " + crateManager.getCrateFactory().getFactoryStats().get("factoryVersion"));

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing crate system", e);
            return false;
        }
    }

    /**
     * Initialize the loot chest system
     */
    private boolean initializeLootChestSystem() {
        try {
            getLogger().info("Initializing Loot Chest System...");
            lootChestManager = LootChestManager.getInstance();
            lootChestManager.initialize();

            // Log loot chest system status
            var stats = lootChestManager.getStatistics();
            getLogger().info("Loot Chest System loaded successfully!");
            getLogger().info("- Total Chests: " + stats.get("totalChests"));
            getLogger().info("- Opened Chests: " + stats.get("openedChests"));
            getLogger().info("- Viewing Players: " + stats.get("viewingPlayers"));

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing loot chest system", e);
            return false;
        }
    }

    /**
     * Load game settings from config
     */
    private void loadGameSettings() {
        FileConfiguration config = getConfig();

        t6Enabled = config.getBoolean("game.t6-enabled", false);
        getLogger().info("T6 content is " + (t6Enabled ? "enabled" : "disabled"));

        mobsEnabled = config.getBoolean("mechanics.mobs.enabled", true);
        getLogger().info("Mob spawning is " + (mobsEnabled ? "enabled" : "disabled"));

        spawnerVisibilityDefault = config.getBoolean("mechanics.mobs.spawner-default-visibility", false);
        getLogger().info("Default spawner visibility is " + (spawnerVisibilityDefault ? "visible" : "hidden"));
    }

    /**
     * Initialize commands
     */
    private boolean initializeCommands() {
        boolean success = true;

        try {
            getLogger().info("Registering commands...");

            // Economy commands
            success &= registerCommand("balance", new BalanceCommand(economyManager));
            success &= registerCommand("pay", new PayCommand(economyManager));
            success &= registerCommand("bank", new BankCommand(bankManager));
            success &= registerCommand("gems", new GemsCommand(economyManager));
            success &= registerCommand("gempouch", new GemPouchCommand(gemPouchManager));
            success &= registerCommand("eco", new EcoCommand(economyManager));
            success &= registerCommand("vendor", new VendorCommand(this));

            // Market command
            if (marketManager != null) {
                MarketCommand marketCommand = new MarketCommand();
                success &= registerCommand("market", marketCommand, marketCommand);
            }

            // Menu system command
            if (getCommand("menu") != null) {
                if (menuItemManager != null) {
                    MenuCommand menuCommand = new MenuCommand();
                    boolean menuRegistered = registerCommand("menu", menuCommand, menuCommand);
                    success &= menuRegistered;

                    if (menuRegistered) {
                        getLogger().info("Menu command registered successfully!");
                    } else {
                        getLogger().warning("Failed to register menu command!");
                    }
                } else {
                    getLogger().warning("Menu item manager is null - command not registered!");
                }
            } else {
                getLogger().warning("Menu command not found in plugin.yml!");
            }

            // Crate commands - Enhanced with better error checking
            if (getCommand("crate") != null) {
                if (crateManager != null) {
                    CrateCommand crateCommand = new CrateCommand();
                    boolean crateRegistered = registerCommand("crate", crateCommand, crateCommand);
                    success &= crateRegistered;

                    if (crateRegistered) {
                        getLogger().info("Crate command registered successfully!");
                    } else {
                        getLogger().warning("Failed to register crate command!");
                    }
                } else {
                    getLogger().warning("Crate manager is null - command not registered!");
                }
            } else {
                getLogger().warning("Crate command not found in plugin.yml!");
            }

            // Loot Chest commands
            if (getCommand("lootchest") != null) {
                if (lootChestManager != null) {
                    LootChestCommand lootChestCommand = new LootChestCommand();
                    boolean lootChestRegistered = registerCommand("lootchest", lootChestCommand, lootChestCommand);
                    success &= lootChestRegistered;

                    if (lootChestRegistered) {
                        getLogger().info("Loot chest command registered successfully!");
                    } else {
                        getLogger().warning("Failed to register loot chest command!");
                    }
                } else {
                    getLogger().warning("Loot chest manager is null - command not registered!");
                }
            } else {
                getLogger().warning("Loot chest command not found in plugin.yml!");
            }

            // Mob commands
            if (mobManager != null) {
                spawnerCommand = new SpawnerCommand(mobManager);
                success &= registerCommand("spawner", spawnerCommand, spawnerCommand);
                success &= registerCommand("spawnmob", new SpawnMobCommand(mobManager));
                success &= registerCommand("mobinfo", new MobInfoCommand(mobManager));
                success &= registerCommand("togglespawners", new ToggleSpawnersCommand(mobManager));
                success &= registerCommand("boss", new BossCommand(mobManager));
            }

            // Drop commands
            if (dropsManager != null) {
                success &= registerCommand("droprate", new DropRateCommand(dropsManager));
                success &= registerCommand("lootbuff", new LootBuffCommand(lootBuffManager));
                success &= registerCommand("elitedrop", new EliteDropsCommand());
            }

            // Teleport commands
            success &= registerCommand("teleportbook", new TeleportBookCommand());
            success &= registerCommand("teleport", new TeleportCommand());

            // Mount commands
            success &= registerCommand("mount", new MountCommand());

            // Player commands
            success &= registerCommand("toggles", new TogglesCommand());
            success &= registerCommand("alignment", new AlignmentCommand(alignmentMechanics));
            success &= registerCommand("invsee", new InvseeCommand());

            // Item commands - Updated with enhanced ItemCommand
            success &= registerCommand("item", new ItemCommand(this));
            success &= registerCommand("journal", new JournalCommand());
            success &= registerCommand("scroll", new ScrollCommand(scrollManager));
            success &= registerCommand("speedfish", new SpeedfishCommand(speedfishMechanics));
            success &= registerCommand("orb", new OrbCommand(orbManager));

            // Chat commands
            success &= registerCommand("buddy", new BuddiesCommand());
            success &= registerCommand("msg", new MessageCommand());
            success &= registerCommand("r", new ReplyCommand());
            success &= registerCommand("global", new GlobalChatCommand());
            success &= registerCommand("staffchat", new StaffChatCommand());
            success &= registerCommand("chattag", new ChatTagCommand());

            // Moderation commands
            success &= registerCommand("kick", new KickCommand());
            success &= registerCommand("ban", new BanCommand(ModerationMechanics.getInstance()));
            success &= registerCommand("unban", new UnbanCommand(ModerationMechanics.getInstance()));
            success &= registerCommand("mute", new MuteCommand(ModerationMechanics.getInstance()));
            success &= registerCommand("unmute", new UnmuteCommand(ModerationMechanics.getInstance()));
            success &= registerCommand("vanish", new VanishCommand(this));
            success &= registerCommand("setrank", new SetRankCommand(ModerationMechanics.getInstance()));

            // Navigation commands
            if (pathManager != null) {
                success &= registerCommand("trail", new TrailCommand(this, pathManager));
            }
            success &= registerCommand("nodemap", new NodeMapCommand(this));

            // Party commands
            success &= registerCommand("p", new PartyCommand());
            success &= registerCommand("paccept", new PAcceptCommand());
            success &= registerCommand("pdecline", new PDeclineCommand());
            success &= registerCommand("pinvite", new PInviteCommand());
            success &= registerCommand("pkick", new PKickCommand());
            success &= registerCommand("pquit", new PQuitCommand());

            getLogger().info("Commands registered successfully!");
            return success;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error registering commands", e);
            return false;
        }
    }

    /**
     * Helper method to register commands
     */
    private boolean registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        return registerCommand(name, executor, null);
    }

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
     * Finalize startup
     */
    private void finalizeStartup() {
        // Start background tasks
        if (spawnerCommand != null) {
            SpawnerHologramUpdater.startTask();
        }

        // Initialize ActionBar utility
        ActionBarUtil.init(this);

        // Log final system status
        getLogger().info("=== YakRealms System Status ===");
        getLogger().info("Session ID: " + sessionID);
        getLogger().info("T6 Content: " + (t6Enabled ? "Enabled" : "Disabled"));
        getLogger().info("Economy System: " + (economyManager != null ? "Active" : "Inactive"));
        getLogger().info("Menu Item System: " + (menuItemManager != null ? "Active" : "Inactive"));
        getLogger().info("Crate System: " + (crateManager != null ? "Active" : "Inactive"));
        getLogger().info("Loot Chest System: " + (lootChestManager != null ? "Active" : "Inactive"));
        getLogger().info("Mob System: " + (mobManager != null ? "Active" : "Inactive"));
        getLogger().info("Market System: " + (marketManager != null ? "Active" : "Inactive"));
        getLogger().info("Awakening Stone System: " + (awakeningStoneSystem != null ? "Active" : "Inactive"));
        getLogger().info("Binding Rune System: " + (bindingRuneSystem != null ? "Active" : "Inactive"));
        getLogger().info("Corruption System: " + (corruptionSystem != null ? "Active" : "Inactive"));
        getLogger().info("Essence Crystal System: " + (essenceCrystalSystem != null ? "Active" : "Inactive"));
        getLogger().info("Forge Hammer System: " + (forgeHammerSystem != null ? "Active" : "Inactive"));
        getLogger().info("==============================");

        getLogger().info("YakRealms startup completed successfully!");
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Starting YakRealms shutdown...");

            // Shutdown menu system first to clean up all online players
            if (MenuSystemInitializer.isInitialized()) {
                getLogger().info("Shutting down menu item system...");
                MenuSystemInitializer.shutdown();
                getLogger().info("Menu item system shutdown completed");
            }

            // Shutdown in reverse order
            if (playerMechanics != null) {
                playerMechanics.onDisable();
            }

            if (playerManager != null) {
                playerManager.onDisable();
            }

            // Shutdown other systems
            shutdownGameSystems();

            // Disconnect database last
            if (mongoDBManager != null) {
                mongoDBManager.disconnect();
            }

            getLogger().info("YakRealms has been disabled cleanly!");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during plugin shutdown", e);
        }
    }

    private void shutdownGameSystems() {
        // Shutdown systems in reverse order
        if (lootChestManager != null) {
            try {
                getLogger().info("Shutting down loot chest system...");
                lootChestManager.shutdown();
                getLogger().info("Loot chest system shutdown completed");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error shutting down loot chest system", e);
            }
        }

        if (crateManager != null) {
            try {
                getLogger().info("Shutting down crate system...");
                crateManager.shutdown();
                getLogger().info("Crate system shutdown completed");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error shutting down crate system", e);
            }
        }

        if (partyMechanics != null) {
            try {
                partyMechanics.onDisable();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error shutting down party mechanics", e);
            }
        }

        shutdownMerchantSystem();

        // Shutdown item enhancement systems
        getLogger().info("Shutting down item enhancement systems...");
        // These systems typically don't need explicit shutdown as they're event-based

        // Add other shutdowns as needed
    }

    // Static getters
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
        }
    }

    // Getters for all systems
    public MongoDBManager getMongoDBManager() { return mongoDBManager; }
    public YakPlayerManager getPlayerManager() { return playerManager; }
    public PlayerMechanics getPlayerMechanics() { return playerMechanics; }
    public PartyMechanics getPartyMechanics() { return partyMechanics; }
    public DashMechanics getDashMechanics() { return dashMechanics; }
    public SpeedfishMechanics getSpeedfishMechanics() { return speedfishMechanics; }
    public MountManager getMountManager() { return mountManager; }
    public ScrollManager getScrollManager() { return scrollManager; }
    public OrbManager getOrbManager() { return orbManager; }
    public Journal getJournalSystem() { return journalSystem; }
    public MenuItemManager getMenuItemManager() { return menuItemManager; }

    // New getters for item enhancement systems
    public AwakeningStoneSystem getAwakeningStoneSystem() { return awakeningStoneSystem; }
    public BindingRuneSystem getBindingRuneSystem() { return bindingRuneSystem; }
    public CorruptionSystem getCorruptionSystem() { return corruptionSystem; }
    public EssenceCrystalSystem getEssenceCrystalSystem() { return essenceCrystalSystem; }
    public ForgeHammerSystem getForgeHammerSystem() { return forgeHammerSystem; }

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
    public LootChestManager getLootChestManager() { return lootChestManager; }

    // Utility methods
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
        }
    }

    public boolean isMobsEnabled() {
        return mobsEnabled;
    }

    /**
     * Get crate manager safely
     */
    public static CrateManager getCrateManagerSafe() {
        if (instance == null || instance.crateManager == null) {
            throw new IllegalStateException("Crate manager not available");
        }
        return instance.crateManager;
    }

    /**
     * Check if crate system is available
     */
    public static boolean isCrateSystemAvailable() {
        return instance != null && instance.crateManager != null;
    }

    /**
     * Get menu item manager safely
     */
    public static MenuItemManager getMenuItemManagerSafe() {
        if (instance == null || instance.menuItemManager == null) {
            throw new IllegalStateException("Menu item manager not available");
        }
        return instance.menuItemManager;
    }

    /**
     * Check if menu item system is available
     */
    public static boolean isMenuItemSystemAvailable() {
        return instance != null && instance.menuItemManager != null && MenuSystemInitializer.isInitialized();
    }

    /**
     * Get loot chest manager safely
     */
    public static LootChestManager getLootChestManagerSafe() {
        if (instance == null || instance.lootChestManager == null) {
            throw new IllegalStateException("Loot chest manager not available");
        }
        return instance.lootChestManager;
    }
    /**
     * Initialize the merchant system
     */
    private boolean initializeMerchantSystem() {
        try {
            getLogger().info("Initializing merchant system...");

            merchantSystem = MerchantSystem.getInstance();

            // Validate dependencies before initialization
            if (!merchantSystem.validateDependencies()) {
                getLogger().warning("Merchant system dependencies not satisfied - skipping initialization");
                return false;
            }

            // Initialize the system
            merchantSystem.initialize();

            getLogger().info("Merchant system initialized successfully");

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize merchant system", e);
            throw new RuntimeException("Merchant system initialization failed", e);
        }
    }

    /**
     * Shutdown the merchant system
     */
    private boolean shutdownMerchantSystem() {
        if (merchantSystem != null) {
            try {
                merchantSystem.shutdown();
                getLogger().info("Merchant system shutdown completed");
                return true;
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error shutting down merchant system", e);
            }
        }
        return false;
    }

    /**
     * Check if loot chest system is available
     */
    public static boolean isLootChestSystemAvailable() {
        return instance != null && instance.lootChestManager != null;
    }

    /**
     * Safe getters for item enhancement systems
     */
    public static AwakeningStoneSystem getAwakeningStoneSystemSafe() {
        if (instance == null || instance.awakeningStoneSystem == null) {
            throw new IllegalStateException("Awakening Stone System not available");
        }
        return instance.awakeningStoneSystem;
    }

    public static BindingRuneSystem getBindingRuneSystemSafe() {
        if (instance == null || instance.bindingRuneSystem == null) {
            throw new IllegalStateException("Binding Rune System not available");
        }
        return instance.bindingRuneSystem;
    }

    public static CorruptionSystem getCorruptionSystemSafe() {
        if (instance == null || instance.corruptionSystem == null) {
            throw new IllegalStateException("Corruption System not available");
        }
        return instance.corruptionSystem;
    }

    public static EssenceCrystalSystem getEssenceCrystalSystemSafe() {
        if (instance == null || instance.essenceCrystalSystem == null) {
            throw new IllegalStateException("Essence Crystal System not available");
        }
        return instance.essenceCrystalSystem;
    }

    public static ForgeHammerSystem getForgeHammerSystemSafe() {
        if (instance == null || instance.forgeHammerSystem == null) {
            throw new IllegalStateException("Forge Hammer System not available");
        }
        return instance.forgeHammerSystem;
    }

    /**
     * Check if item enhancement systems are available
     */
    public static boolean isAwakeningStoneSystemAvailable() {
        return instance != null && instance.awakeningStoneSystem != null;
    }

    public static boolean isBindingRuneSystemAvailable() {
        return instance != null && instance.bindingRuneSystem != null;
    }

    public static boolean isCorruptionSystemAvailable() {
        return instance != null && instance.corruptionSystem != null;
    }

    public static boolean isEssenceCrystalSystemAvailable() {
        return instance != null && instance.essenceCrystalSystem != null;
    }

    public static boolean isForgeHammerSystemAvailable() {
        return instance != null && instance.forgeHammerSystem != null;
    }
}