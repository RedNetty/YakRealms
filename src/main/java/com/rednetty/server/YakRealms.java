package com.rednetty.server;

import com.rednetty.server.commands.staff.admin.*;
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
import com.rednetty.server.commands.staff.admin.CrateCommand;
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
import com.rednetty.server.mechanics.player.social.trade.TradeManager;
import com.rednetty.server.mechanics.player.listeners.TradeListener;
import com.rednetty.server.mechanics.world.lootchests.LootChestManager;
import com.rednetty.server.mechanics.economy.market.MarketManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.tasks.SpawnerHologramUpdater;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.mounts.MountManager;
import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
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
import com.rednetty.server.utils.async.AsyncUtil;
import com.rednetty.server.utils.config.ConfigUtil;
import com.rednetty.server.utils.text.StringUtil;
import com.rednetty.server.utils.collections.CollectionUtil;
import com.rednetty.server.commands.staff.admin.MenuCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Main plugin class for YakRealms - Refactored for better organization and maintainability
 */
public class YakRealms extends JavaPlugin {

    private static YakRealms instance;
    private static int sessionID = 0;
    private static boolean patchLockdown = false;
    private static boolean t6Enabled = false;

    // Core Systems
    private final CoreSystems coreSystems = new CoreSystems();
    private final GameSystems gameSystems = new GameSystems();
    private final EnhancementSystems enhancementSystems = new EnhancementSystems();

    // Game Configuration
    private final GameConfig gameConfig = new GameConfig();

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        try {
            initializePlugin();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Critical error during plugin startup", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            shutdownPlugin();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during plugin shutdown", e);
        }
    }

    // ========================================
    // INITIALIZATION METHODS
    // ========================================

    /**
     * Main plugin initialization method
     */
    private void initializePlugin() {
        getLogger().info("Starting YakRealms initialization...");

        setupEnvironment();
        loadConfiguration();

        boolean success = true;
        success &= initializeCoreSystemsInOrder();
        success &= initializeGameSystemsInOrder();
        success &= initializeCommands();

        if (!success) {
            throw new RuntimeException("Plugin initialization failed");
        }

        finalizeStartup();
        getLogger().info("YakRealms has been enabled successfully!");
    }

    /**
     * Setup basic environment and utilities
     */
    private void setupEnvironment() {
        // Ensure data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize utilities
        AsyncUtil.init(this);
        ConfigUtil.init(this);
        ActionBarUtil.init(this);

        // Generate session ID
        sessionID = ThreadLocalRandom.current().nextInt();

        getLogger().info("Environment setup completed");
    }

    /**
     * Load and validate configuration
     */
    private void loadConfiguration() {
        saveDefaultConfig();
        reloadConfig();

        gameConfig.loadFromConfig(getConfig());

        getLogger().info("Configuration loaded successfully");
    }

    /**
     * Initialize core systems in proper order
     */
    private boolean initializeCoreSystemsInOrder() {
        getLogger().info("Initializing core systems...");

        return executeWithErrorHandling("Database", this::initializeDatabase) &&
                executeWithErrorHandling("Player Systems", this::initializePlayerSystems) &&
                executeWithErrorHandling("Moderation Systems", this::initializeModerationSystems);
    }

    /**
     * Initialize game systems in proper order
     */
    private boolean initializeGameSystemsInOrder() {
        getLogger().info("Initializing game systems...");

        // Group related initializations
        Map<String, Runnable> systemInitializers = new HashMap<>();
        systemInitializers.put("Party & Alignment", this::initializePartyAndAlignment);
        systemInitializers.put("Player Movement", this::initializePlayerMovement);
        systemInitializers.put("Mount System", this::initializeMountSystem);
        systemInitializers.put("Item Systems", this::initializeItemSystems);
        systemInitializers.put("Item Enhancement", this::initializeItemEnhancementSystems);
        systemInitializers.put("Chat & Communication", this::initializeChatSystems);
        systemInitializers.put("Economy Systems", this::initializeEconomySystems);
        systemInitializers.put("Combat Systems", this::initializeCombatSystems);
        systemInitializers.put("World Systems", this::initializeWorldSystems);
        systemInitializers.put("Special Systems", this::initializeSpecialSystems);

        boolean allSuccess = true;
        for (Map.Entry<String, Runnable> entry : systemInitializers.entrySet()) {
            allSuccess &= executeWithErrorHandling(entry.getKey(), () -> {
                entry.getValue().run();
                return true;
            });
        }

        return allSuccess;
    }

    // ========================================
    // CORE SYSTEM INITIALIZERS
    // ========================================

    private boolean initializeDatabase() {
        coreSystems.mongoDBManager = MongoDBManager.initialize(getConfig(), this);
        if (!coreSystems.mongoDBManager.connect()) {
            throw new RuntimeException("Failed to connect to database!");
        }
        return true;
    }

    private boolean initializePlayerSystems() {
        // Initialize YakPlayerManager first
        coreSystems.playerManager = YakPlayerManager.getInstance();
        coreSystems.playerManager.onEnable();

        // Wait for player manager to be ready
        AsyncUtil.scheduleDelayed("player-manager-ready", () -> {
            // Initialize trade system components
            coreSystems.tradeManager = new TradeManager(this);
            coreSystems.tradeListener = new TradeListener(this);

            // Register trade listener events
            Bukkit.getServer().getPluginManager().registerEvents(coreSystems.tradeListener, this);

            // Link components after delay
            AsyncUtil.scheduleDelayed("trade-link", () -> {
                if (coreSystems.tradeListener != null && coreSystems.tradeManager != null) {
                    coreSystems.tradeListener.setTradeManager(coreSystems.tradeManager);
                    getLogger().info("TradeManager properly linked to TradeListener");
                }
            }, 10L);

            // Initialize PlayerMechanics
            coreSystems.playerMechanics = PlayerMechanics.getInstance();
            coreSystems.playerMechanics.onEnable();

        }, 20L); // 1 second delay

        return true;
    }

    private boolean initializeModerationSystems() {
        coreSystems.moderationMechanics = ModerationMechanics.getInstance();
        coreSystems.moderationMechanics.onEnable();
        return true;
    }

    // ========================================
    // GAME SYSTEM INITIALIZERS
    // ========================================

    private void initializePartyAndAlignment() {
        gameSystems.partyMechanics = PartyMechanics.getInstance();
        gameSystems.partyMechanics.onEnable();

        gameSystems.alignmentMechanics = AlignmentMechanics.getInstance();
        gameSystems.alignmentMechanics.onEnable();
    }

    private void initializePlayerMovement() {
        gameSystems.dashMechanics = new DashMechanics();
        gameSystems.dashMechanics.onEnable();

        gameSystems.speedfishMechanics = new SpeedfishMechanics();
        gameSystems.speedfishMechanics.onEnable();
    }

    private void initializeMountSystem() {
        gameSystems.mountManager = MountManager.getInstance();
        gameSystems.mountManager.onEnable();
    }

    private void initializeItemSystems() {
        gameSystems.scrollManager = ScrollManager.getInstance();
        gameSystems.scrollManager.initialize();

        gameSystems.orbManager = OrbManager.getInstance();
        gameSystems.orbManager.initialize();

        gameSystems.journalSystem = new Journal();
        gameSystems.menuItemManager = MenuItemManager.getInstance();
        gameSystems.menuItemManager.initialize();

        // Initialize menu system with delay
        AsyncUtil.scheduleDelayed("menu-system-init", () -> {
            try {
                MenuSystemInitializer.initialize();
                getLogger().info("Menu Item System initialized successfully!");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to initialize Menu Item System", e);
            }
        }, 20L);
    }

    private void initializeItemEnhancementSystems() {
        enhancementSystems.awakeningStoneSystem = AwakeningStoneSystem.getInstance();
        enhancementSystems.awakeningStoneSystem.initialize();

        enhancementSystems.bindingRuneSystem = BindingRuneSystem.getInstance();
        enhancementSystems.bindingRuneSystem.initialize();

        enhancementSystems.corruptionSystem = CorruptionSystem.getInstance();
        enhancementSystems.corruptionSystem.initialize();

        enhancementSystems.essenceCrystalSystem = EssenceCrystalSystem.getInstance();
        enhancementSystems.essenceCrystalSystem.initialize();

        enhancementSystems.forgeHammerSystem = ForgeHammerSystem.getInstance();
        enhancementSystems.forgeHammerSystem.initialize();

        getLogger().info("All item enhancement systems initialized successfully!");
    }

    private void initializeChatSystems() {
        gameSystems.chatMechanics = ChatMechanics.getInstance();
        gameSystems.chatMechanics.onEnable();
    }

    private void initializeEconomySystems() {
        gameSystems.economyManager = EconomyManager.getInstance();
        gameSystems.economyManager.onEnable();

        gameSystems.bankManager = BankManager.getInstance();
        gameSystems.bankManager.onEnable();

        gameSystems.gemPouchManager = GemPouchManager.getInstance();
        gameSystems.gemPouchManager.onEnable();

        gameSystems.vendorManager = VendorManager.getInstance(this);
        VendorSystemInitializer.initialize(this);

        gameSystems.marketManager = MarketManager.getInstance();
        gameSystems.marketManager.onEnable();
    }

    private void initializeCombatSystems() {
        gameSystems.combatMechanics = new CombatMechanics();
        gameSystems.combatMechanics.onEnable();

        gameSystems.magicStaff = new MagicStaff();
        gameSystems.magicStaff.onEnable();

        gameSystems.deathRemnantManager = new DeathRemnantManager(this);
        gameSystems.respawnManager = new RespawnManager();
        gameSystems.respawnManager.onEnable();
    }

    private void initializeWorldSystems() {
        // Mob system
        gameSystems.mobManager = MobManager.getInstance();
        gameSystems.mobManager.initialize();
        gameSystems.mobManager.setSpawnersEnabled(gameConfig.mobsEnabled);

        // Drops system
        gameSystems.dropsHandler = DropsHandler.getInstance();
        gameSystems.dropsHandler.initialize();
        gameSystems.lootBuffManager = LootBuffManager.getInstance();
        gameSystems.lootBuffManager.initialize();
        gameSystems.dropsManager = DropsManager.getInstance();
        gameSystems.dropsManager.initialize();

        // Teleport systems
        gameSystems.teleportManager = TeleportManager.getInstance();
        gameSystems.teleportManager.onEnable();
        gameSystems.teleportBookSystem = TeleportBookSystem.getInstance();
        gameSystems.hearthstoneSystem = HearthstoneSystem.getInstance();
        gameSystems.portalSystem = PortalSystem.getInstance();

        // Trail and path systems
        initializeTrailSystems();
    }

    private void initializeTrailSystems() {
        gameSystems.trailSystem = new TrailSystem(this);
        gameSystems.particleSystem = new ParticleSystem(this);

        // Initialize path manager if possible
        try {
            List<World> worlds = getServer().getWorlds();
            if (CollectionUtil.isNotEmpty(worlds)) {
                World mainWorld = worlds.get(0);
                AdvancedNodeMapGenerator nodeGenerator = new AdvancedNodeMapGenerator();
                File nodeMapFile = new File(getDataFolder(), mainWorld.getName() + "_advanced_navgraph.dat");
                List<NavNode> nodes = nodeGenerator.getOrGenerateNodeMap(mainWorld, nodeMapFile);
                gameSystems.pathManager = new PathManager(this, gameSystems.particleSystem);
                getLogger().info("Path manager initialized with " + nodes.size() + " navigation nodes");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to initialize PathManager", e);
        }
    }

    private void initializeSpecialSystems() {
        // Crate system
        gameSystems.crateManager = CrateManager.getInstance();
        gameSystems.crateManager.initialize();
        logSystemStats("Crate System", gameSystems.crateManager.getStatistics());

        // Loot chest system
        gameSystems.lootChestManager = LootChestManager.getInstance();
        gameSystems.lootChestManager.initialize();
        logSystemStats("Loot Chest System", gameSystems.lootChestManager.getStatistics());

        // Merchant system
        gameSystems.merchantSystem = MerchantSystem.getInstance();
        if (gameSystems.merchantSystem.validateDependencies()) {
            gameSystems.merchantSystem.initialize();
            getLogger().info("Merchant system initialized successfully");
        } else {
            getLogger().warning("Merchant system dependencies not satisfied - skipping initialization");
        }
    }

    // ========================================
    // COMMAND REGISTRATION
    // ========================================

    private boolean initializeCommands() {
        getLogger().info("Registering commands...");

        boolean success = true;

        // Player commands
        Map<String, CommandExecutor> playerCommands = new HashMap<>();
        playerCommands.put("logout", new LogoutCommand());
        playerCommands.put("toggles", new TogglesCommand());
        playerCommands.put("alignment", new AlignmentCommand(gameSystems.alignmentMechanics));
        playerCommands.put("invsee", new InvseeCommand());
        success &= registerCommands(playerCommands);

        // Economy commands
        Map<String, CommandExecutor> economyCommands = new HashMap<>();
        economyCommands.put("balance", new BalanceCommand(gameSystems.economyManager));
        economyCommands.put("pay", new PayCommand(gameSystems.economyManager));
        economyCommands.put("bank", new BankCommand(gameSystems.bankManager));
        economyCommands.put("gems", new GemsCommand(gameSystems.economyManager));
        economyCommands.put("gempouch", new GemPouchCommand(gameSystems.gemPouchManager));
        economyCommands.put("eco", new EcoCommand(gameSystems.economyManager));
        economyCommands.put("vendor", new VendorCommand(this));
        success &= registerCommands(economyCommands);

        // System-specific commands
        success &= registerSystemCommands();

        // Staff commands
        success &= registerStaffCommands();

        getLogger().info("Commands registered successfully!");
        return success;
    }

    private boolean registerSystemCommands() {
        boolean success = true;

        // Market command
        if (gameSystems.marketManager != null) {
            MarketCommand marketCommand = new MarketCommand();
            success &= registerCommandWithCompleter("market", marketCommand, marketCommand);
        }

        // Menu command
        if (gameSystems.menuItemManager != null) {
            MenuCommand menuCommand = new MenuCommand();
            success &= registerCommandWithCompleter("menu", menuCommand, menuCommand);
        }

        // Crate command
        if (gameSystems.crateManager != null) {
            CrateCommand crateCommand = new CrateCommand();
            success &= registerCommandWithCompleter("crate", crateCommand, crateCommand);
        }

        // More system commands...
        success &= registerMobCommands();
        success &= registerUtilityCommands();

        return success;
    }

    private boolean registerMobCommands() {
        if (gameSystems.mobManager == null) return true;

        gameSystems.spawnerCommand = new SpawnerCommand(gameSystems.mobManager);

        Map<String, CommandExecutor> mobCommands = new HashMap<>();
        mobCommands.put("spawner", gameSystems.spawnerCommand);
        mobCommands.put("spawnmob", new SpawnMobCommand(gameSystems.mobManager));
        mobCommands.put("mobinfo", new MobInfoCommand(gameSystems.mobManager));
        mobCommands.put("togglespawners", new ToggleSpawnersCommand(gameSystems.mobManager));
        mobCommands.put("boss", new BossCommand(gameSystems.mobManager));

        return registerCommands(mobCommands);
    }

    private boolean registerUtilityCommands() {
        Map<String, CommandExecutor> utilityCommands = new HashMap<>();
        utilityCommands.put("teleport", new TeleportCommand());
        utilityCommands.put("item", new ItemCommand(this));
        utilityCommands.put("orb", new OrbCommand(gameSystems.orbManager));
        utilityCommands.put("nodemap", new NodeMapCommand(this));

        return registerCommands(utilityCommands);
    }

    private boolean registerStaffCommands() {
        Map<String, CommandExecutor> staffCommands = new HashMap<>();
        staffCommands.put("kick", new KickCommand());
        staffCommands.put("ban", new BanCommand(coreSystems.moderationMechanics));
        staffCommands.put("unban", new UnbanCommand(coreSystems.moderationMechanics));
        staffCommands.put("mute", new MuteCommand(coreSystems.moderationMechanics));
        staffCommands.put("unmute", new UnmuteCommand(coreSystems.moderationMechanics));
        staffCommands.put("vanish", new VanishCommand(this));
        staffCommands.put("setrank", new SetRankCommand(coreSystems.moderationMechanics));
        staffCommands.put("shutdown", new ShutdownCommand());

        return registerCommands(staffCommands);
    }

    // ========================================
    // SHUTDOWN METHODS
    // ========================================

    private void shutdownPlugin() {
        getLogger().info("Starting YakRealms shutdown...");

        shutdownGameSystems();
        shutdownCoreSystems();

        // Cancel all named async tasks
        AsyncUtil.cancelAllNamed();

        getLogger().info("YakRealms has been disabled cleanly!");
    }

    private void shutdownGameSystems() {
        // Shutdown in reverse order of initialization
        if (gameSystems.merchantSystem != null) {
            executeWithErrorHandling("Merchant System", () -> {
                gameSystems.merchantSystem.shutdown();
                return true;
            });
        }

        if (gameSystems.lootChestManager != null) {
            executeWithErrorHandling("Loot Chest System", () -> {
                gameSystems.lootChestManager.shutdown();
                return true;
            });
        }

        if (gameSystems.crateManager != null) {
            executeWithErrorHandling("Crate System", () -> {
                gameSystems.crateManager.shutdown();
                return true;
            });
        }

        if (gameSystems.partyMechanics != null) {
            executeWithErrorHandling("Party Mechanics", () -> {
                gameSystems.partyMechanics.onDisable();
                return true;
            });
        }

        if (MenuSystemInitializer.isInitialized()) {
            executeWithErrorHandling("Menu System", () -> {
                MenuSystemInitializer.shutdown();
                return true;
            });
        }
    }

    private void shutdownCoreSystems() {
        // Shutdown trade system first
        if (coreSystems.tradeManager != null) {
            executeWithErrorHandling("Trade System", () -> {
                coreSystems.tradeManager.clearAllTrades();
                return true;
            });
        }

        if (coreSystems.tradeListener != null) {
            executeWithErrorHandling("Trade Listener", () -> {
                coreSystems.tradeListener.cleanup();
                return true;
            });
        }

        // Shutdown moderation mechanics
        if (coreSystems.moderationMechanics != null) {
            executeWithErrorHandling("Moderation Mechanics", () -> {
                coreSystems.moderationMechanics.onDisable();
                return true;
            });
        }

        // Shutdown player systems
        if (coreSystems.playerMechanics != null) {
            coreSystems.playerMechanics.onDisable();
        }

        if (coreSystems.playerManager != null) {
            coreSystems.playerManager.onDisable();
        }

        // Disconnect database last
        if (coreSystems.mongoDBManager != null) {
            coreSystems.mongoDBManager.disconnect();
        }
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Execute a system initialization with proper error handling
     */
    private boolean executeWithErrorHandling(String systemName, SystemInitFunction function) {
        try {
            getLogger().info("Initializing " + systemName + "...");
            boolean success = function.execute();

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
    private interface SystemInitFunction {
        boolean execute() throws Exception;
    }

    /**
     * Register multiple commands at once
     */
    private boolean registerCommands(Map<String, CommandExecutor> commands) {
        boolean success = true;
        for (Map.Entry<String, CommandExecutor> entry : commands.entrySet()) {
            success &= registerCommand(entry.getKey(), entry.getValue());
        }
        return success;
    }

    /**
     * Register command with executor and tab completer
     */
    private boolean registerCommandWithCompleter(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        boolean success = registerCommand(name, executor);
        if (success && getCommand(name) != null) {
            getCommand(name).setTabCompleter(tabCompleter);
        }
        return success;
    }

    /**
     * Register a single command safely
     */
    private boolean registerCommand(String name, CommandExecutor executor) {
        try {
            var command = getCommand(name);
            if (command != null) {
                command.setExecutor(executor);
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
     * Log system statistics in a formatted way
     */
    private void logSystemStats(String systemName, Map<String, Object> stats) {
        getLogger().info(systemName + " loaded successfully!");
        stats.forEach((key, value) ->
                getLogger().info("- " + StringUtil.capitalizeWords(key.replace("_", " ")) + ": " + value)
        );
    }

    /**
     * Finalize startup process
     */
    private void finalizeStartup() {
        // Start background tasks
        if (gameSystems.spawnerCommand != null) {
            SpawnerHologramUpdater.startTask();
        }

        // Log final system status
        logFinalStatus();
    }

    /**
     * Log comprehensive system status
     */
    private void logFinalStatus() {
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("Session ID", String.valueOf(sessionID));
        statusMap.put("T6 Content", gameConfig.t6Enabled ? "Enabled" : "Disabled");
        statusMap.put("Economy System", gameSystems.economyManager != null ? "Active" : "Inactive");
        statusMap.put("Trade System", coreSystems.tradeManager != null ? "Active" : "Inactive");
        statusMap.put("Moderation System", coreSystems.moderationMechanics != null ? "Active" : "Inactive");
        statusMap.put("Crate System", gameSystems.crateManager != null ? "Active" : "Inactive");
        statusMap.put("Enhancement Systems", enhancementSystems.isAllActive() ? "Active" : "Partial");

        getLogger().info("=== YakRealms System Status ===");
        statusMap.forEach((key, value) -> getLogger().info(key + ": " + value));
        getLogger().info("==============================");
        getLogger().info("YakRealms startup completed successfully!");
    }

    // ========================================
    // STATIC GETTERS AND UTILITY
    // ========================================

    public static YakRealms getInstance() { return instance; }
    public static boolean isPatchLockdown() { return patchLockdown; }
    public static void setPatchLockdown(boolean patchLockdown) { YakRealms.patchLockdown = patchLockdown; }
    public static int getSessionID() { return sessionID; }
    public static boolean isT6Enabled() { return t6Enabled; }
    public static void setT6Enabled(boolean enabled) {
        t6Enabled = enabled;
        if (instance != null) {
            instance.getConfig().set("game.t6-enabled", enabled);
            instance.saveConfig();
        }
    }

    // System getters - organized by category
    public MongoDBManager getMongoDBManager() { return coreSystems.mongoDBManager; }
    public YakPlayerManager getPlayerManager() { return coreSystems.playerManager; }
    public PlayerMechanics getPlayerMechanics() { return coreSystems.playerMechanics; }
    public ModerationMechanics getModerationMechanics() { return coreSystems.moderationMechanics; }
    public TradeManager getTradeManager() { return coreSystems.tradeManager; }
    public TradeListener getTradeListener() { return coreSystems.tradeListener; }

    public EconomyManager getEconomyManager() { return gameSystems.economyManager; }
    public CombatMechanics getCombatMechanics() { return gameSystems.combatMechanics; }
    public MobManager getMobManager() { return gameSystems.mobManager; }
    public CrateManager getCrateManager() { return gameSystems.crateManager; }

    public AwakeningStoneSystem getAwakeningStoneSystem() { return enhancementSystems.awakeningStoneSystem; }
    public BindingRuneSystem getBindingRuneSystem() { return enhancementSystems.bindingRuneSystem; }
    public CorruptionSystem getCorruptionSystem() { return enhancementSystems.corruptionSystem; }
    public EssenceCrystalSystem getEssenceCrystalSystem() { return enhancementSystems.essenceCrystalSystem; }
    public ForgeHammerSystem getForgeHammerSystem() { return enhancementSystems.forgeHammerSystem; }

    // Static utility methods
    public static void log(String message) { if (instance != null) instance.getLogger().info(message); }
    public static void warn(String message) { if (instance != null) instance.getLogger().warning(message); }
    public static void error(String message, Exception e) { if (instance != null) instance.getLogger().log(Level.SEVERE, message, e); }

    // ========================================
    // INNER CLASSES FOR ORGANIZATION
    // ========================================

    /**
     * Container for core plugin systems
     */
    private static class CoreSystems {
        MongoDBManager mongoDBManager;
        YakPlayerManager playerManager;
        PlayerMechanics playerMechanics;
        ModerationMechanics moderationMechanics;
        TradeManager tradeManager;
        TradeListener tradeListener;
    }

    /**
     * Container for game-related systems
     */
    private static class GameSystems {
        // Combat & PVP
        CombatMechanics combatMechanics;
        MagicStaff magicStaff;
        AlignmentMechanics alignmentMechanics;
        RespawnManager respawnManager;
        DeathRemnantManager deathRemnantManager;

        // Social & Communication
        PartyMechanics partyMechanics;
        ChatMechanics chatMechanics;

        // Player Systems
        DashMechanics dashMechanics;
        SpeedfishMechanics speedfishMechanics;
        MountManager mountManager;

        // Items & Inventory
        ScrollManager scrollManager;
        OrbManager orbManager;
        Journal journalSystem;
        MenuItemManager menuItemManager;

        // Economy
        EconomyManager economyManager;
        BankManager bankManager;
        GemPouchManager gemPouchManager;
        VendorManager vendorManager;
        MarketManager marketManager;

        // World Systems
        MobManager mobManager;
        SpawnerCommand spawnerCommand;
        DropsManager dropsManager;
        DropsHandler dropsHandler;
        LootBuffManager lootBuffManager;

        // Teleportation
        TeleportManager teleportManager;
        TeleportBookSystem teleportBookSystem;
        HearthstoneSystem hearthstoneSystem;
        PortalSystem portalSystem;

        // World Navigation
        TrailSystem trailSystem;
        ParticleSystem particleSystem;
        PathManager pathManager;

        // Special Systems
        CrateManager crateManager;
        LootChestManager lootChestManager;
        MerchantSystem merchantSystem;
    }

    /**
     * Container for item enhancement systems
     */
    private static class EnhancementSystems {
        AwakeningStoneSystem awakeningStoneSystem;
        BindingRuneSystem bindingRuneSystem;
        CorruptionSystem corruptionSystem;
        EssenceCrystalSystem essenceCrystalSystem;
        ForgeHammerSystem forgeHammerSystem;

        boolean isAllActive() {
            return awakeningStoneSystem != null && bindingRuneSystem != null &&
                    corruptionSystem != null && essenceCrystalSystem != null &&
                    forgeHammerSystem != null;
        }
    }

    /**
     * Container for game configuration
     */
    private static class GameConfig {
        boolean mobsEnabled = true;
        boolean spawnerVisibilityDefault = false;
        boolean t6Enabled = false;

        void loadFromConfig(org.bukkit.configuration.file.FileConfiguration config) {
            t6Enabled = ConfigUtil.getBoolean(config, "game.t6-enabled", false);
            mobsEnabled = ConfigUtil.getBoolean(config, "mechanics.mobs.enabled", true);
            spawnerVisibilityDefault = ConfigUtil.getBoolean(config, "mechanics.mobs.spawner-default-visibility", false);

            YakRealms.t6Enabled = this.t6Enabled;
        }
    }
}