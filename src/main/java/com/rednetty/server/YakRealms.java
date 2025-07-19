package com.rednetty.server;

import com.rednetty.server.commands.economy.*;
import com.rednetty.server.commands.party.*;
import com.rednetty.server.commands.player.*;
import com.rednetty.server.commands.staff.*;
import com.rednetty.server.commands.staff.admin.*;
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
import com.rednetty.server.mechanics.combat.death.DeathMechanics;
import com.rednetty.server.mechanics.combat.death.remnant.DeathRemnantManager;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.economy.BankManager;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.GemPouchManager;
import com.rednetty.server.mechanics.economy.market.MarketManager;
import com.rednetty.server.mechanics.economy.merchant.MerchantSystem;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import com.rednetty.server.mechanics.economy.vendors.VendorSystemInitializer;
import com.rednetty.server.mechanics.item.Journal;
import com.rednetty.server.mechanics.item.MenuItemManager;
import com.rednetty.server.mechanics.item.MenuSystemInitializer;
import com.rednetty.server.mechanics.item.awakening.AwakeningStoneSystem;
import com.rednetty.server.mechanics.item.binding.BindingRuneSystem;
import com.rednetty.server.mechanics.item.corruption.CorruptionSystem;
import com.rednetty.server.mechanics.item.crates.CrateManager;
import com.rednetty.server.mechanics.item.drops.DropsHandler;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.item.drops.glowing.GlowingDropsInitializer;
import com.rednetty.server.mechanics.item.essence.EssenceCrystalSystem;
import com.rednetty.server.mechanics.item.forge.ForgeHammerSystem;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.PlayerMechanics;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.items.SpeedfishMechanics;
import com.rednetty.server.mechanics.player.listeners.TradeListener;
import com.rednetty.server.mechanics.player.mounts.MountManager;
import com.rednetty.server.mechanics.player.movement.DashMechanics;
import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import com.rednetty.server.mechanics.player.social.trade.TradeManager;
import com.rednetty.server.mechanics.ui.tab.TabPluginIntegration;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import com.rednetty.server.mechanics.world.lootchests.core.ChestManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.tasks.SpawnerHologramUpdater;
import com.rednetty.server.mechanics.world.teleport.HearthstoneSystem;
import com.rednetty.server.mechanics.world.teleport.PortalSystem;
import com.rednetty.server.mechanics.world.teleport.TeleportBookSystem;
import com.rednetty.server.mechanics.world.teleport.TeleportManager;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Main plugin class for YakRealms with modern TAB integration
 */
public class YakRealms extends JavaPlugin {

    private static YakRealms instance;

    // Game settings
    private static boolean patchLockdown = false;
    private static int sessionID = 0;
    // Core systems - initialized in order
    private MongoDBManager mongoDBManager;
    private YakPlayerManager playerManager;
    private PlayerMechanics playerMechanics;
    private ModerationMechanics moderationMechanics;
    // All other systems
    private CombatMechanics combatMechanics;
    private MagicStaff magicStaff;
    private ChatMechanics chatMechanics;
    private AlignmentMechanics alignmentMechanics;
    private DeathRemnantManager deathRemnantManager;
    private PartyMechanics partyMechanics;
    private DashMechanics dashMechanics;
    private SpeedfishMechanics speedfishMechanics;
    private MountManager mountManager;
    private ScrollManager scrollManager;
    private OrbManager orbManager;
    private Journal journalSystem;
    private MenuItemManager menuItemManager;
    // Trade system components
    private TradeManager tradeManager;
    private TradeListener tradeListener;
    // Item Enhancement Systems
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
    private ChestManager lootChestManager;
    private MerchantSystem merchantSystem;
    private static boolean t6Enabled = false;
    // NEW: Modern TAB integration
    private TabPluginIntegration tabPluginIntegration;
    private boolean mobsEnabled = true;
    private boolean spawnerVisibilityDefault = false;

    @Override
    public void onLoad() {
        instance = this;
    }
    private File file = null;

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

    /**
     * Get TAB Plugin Integration instance safely
     */
    public static TabPluginIntegration getTabPluginIntegrationSafe() {
        if (instance == null || instance.tabPluginIntegration == null) {
            throw new IllegalStateException("TAB Plugin Integration not available");
        }
        return instance.tabPluginIntegration;
    }

    /**
     * Check if TAB Plugin Integration is available and enabled
     */
    public static boolean isTabPluginIntegrationAvailable() {
        return instance != null && instance.tabPluginIntegration != null && instance.tabPluginIntegration.isEnabled();
    }

    private static File getLogFile() {
        return YakRealms.getInstance().file;
    }

    public static void log(String message) {
        if (instance != null && getLogFile() != null) {
            try {
                FileWriter writer = new FileWriter(getLogFile());
                writer.append("\n").append(message);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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

    public static void debug(String message) {
        if (instance != null && instance.isDebugMode()) {
            instance.getLogger().info("[DEBUG] " + message);
        }
    }

    public static CrateManager getCrateManagerSafe() {
        if (instance == null || instance.crateManager == null) {
            throw new IllegalStateException("Crate manager not available");
        }
        return instance.crateManager;
    }

    public static boolean isCrateSystemAvailable() {
        return instance != null && instance.crateManager != null;
    }

    public static MenuItemManager getMenuItemManagerSafe() {
        if (instance == null || instance.menuItemManager == null) {
            throw new IllegalStateException("Menu item manager not available");
        }
        return instance.menuItemManager;
    }

    public static boolean isMenuItemSystemAvailable() {
        return instance != null && instance.menuItemManager != null && MenuSystemInitializer.isInitialized();
    }

    public static TradeManager getTradeManagerSafe() {
        if (instance == null || instance.tradeManager == null) {
            throw new IllegalStateException("Trade manager not available");
        }
        return instance.tradeManager;
    }

    public static boolean isTradeSystemAvailable() {
        return instance != null && instance.tradeManager != null && instance.tradeListener != null;
    }

    public static ChestManager getLootChestManagerSafe() {
        if (instance == null || instance.lootChestManager == null) {
            throw new IllegalStateException("Loot chest manager not available");
        }
        return instance.lootChestManager;
    }

    public static boolean isLootChestSystemAvailable() {
        return instance != null && instance.lootChestManager != null;
    }

    // Item enhancement system safe getters
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

    // Item enhancement system availability checks
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

    @Override
    public void onEnable() {
        try {

            getLogger().info("Starting YakRealms initialization...");
            initializeLogFile();
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

            if (!initializeModerationSystems()) {
                getLogger().severe("Failed to initialize moderation systems!");
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
     * Initialize TAB Plugin Integration with delayed retry logic
     */
    private boolean initializeTabPluginIntegration() {
        getLogger().info("Initializing TAB Plugin Integration...");

        // Schedule delayed initialization to avoid plugin loading order issues
        Bukkit.getScheduler().runTaskLater(this, this::attemptTabIntegration, 60L); // 3 second delay

        // Always return true - TAB integration is optional
        getLogger().info("TAB Plugin Integration scheduled for delayed initialization");
        return true;
    }

    /**
     * Attempt TAB integration with retry logic
     */
    private void attemptTabIntegration() {
        attemptTabIntegrationWithRetry(0, 5); // Try 5 times
    }

    // =============================================================================
    // STATIC GETTERS AND UTILITY METHODS
    // =============================================================================

    /**
     * Recursive method to retry TAB integration
     */
    private void attemptTabIntegrationWithRetry(int attempt, int maxAttempts) {
        try {
            getLogger().info("Attempting TAB Plugin Integration (attempt " + (attempt + 1) + "/" + maxAttempts + ")");

            if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
                if (attempt < maxAttempts - 1) {
                    getLogger().info("TAB plugin not ready yet, retrying in 2 seconds...");
                    Bukkit.getScheduler().runTaskLater(this,
                            () -> attemptTabIntegrationWithRetry(attempt + 1, maxAttempts), 40L); // 2 second delay
                    return;
                } else {
                    getLogger().warning("TAB plugin not found after " + maxAttempts + " attempts - Player stats tablist disabled");
                    getLogger().info("Download TAB from: https://modrinth.com/plugin/tab-was-taken");
                    return;
                }
            }

            // TAB is available, initialize integration
            tabPluginIntegration = TabPluginIntegration.getInstance();
            tabPluginIntegration.initialize();

            getLogger().info("TAB Plugin Integration initialized successfully on attempt " + (attempt + 1));

        } catch (Exception e) {
            if (attempt < maxAttempts - 1) {
                getLogger().warning("TAB integration failed on attempt " + (attempt + 1) + ", retrying: " + e.getMessage());
                Bukkit.getScheduler().runTaskLater(this,
                        () -> attemptTabIntegrationWithRetry(attempt + 1, maxAttempts), 40L);
            } else {
                getLogger().log(Level.WARNING, "TAB Plugin Integration failed after " + maxAttempts + " attempts", e);
            }
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
     * Initialize player management systems with proper trade system setup
     */
    private boolean initializePlayerSystems() {
        try {
            getLogger().info("Initializing player systems...");

            // Initialize YakPlayerManager first (it doesn't depend on PlayerMechanics)
            playerManager = YakPlayerManager.getInstance();
            playerManager.onEnable();

            // Wait a moment for player manager to be ready
            Thread.sleep(1000);

            // Initialize TradeManager BEFORE PlayerMechanics
            tradeManager = new TradeManager(this);
            getLogger().info("TradeManager initialized successfully");

            // Initialize TradeListener with proper error handling
            tradeListener = new TradeListener(this);
            getLogger().info("TradeListener created");

            // Register trade listener events
            Bukkit.getServer().getPluginManager().registerEvents(tradeListener, this);
            getLogger().info("TradeListener events registered");

            // Schedule a task to ensure TradeManager is properly linked to TradeListener
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (tradeListener != null && tradeManager != null) {
                    tradeListener.setTradeManager(tradeManager);
                    getLogger().info("TradeManager properly linked to TradeListener");
                }
            }, 10L); // 0.5 second delay

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
     * Initialize moderation systems separately for better organization
     */
    private boolean initializeModerationSystems() {
        try {
            getLogger().info("Initializing moderation systems...");

            // Initialize moderation mechanics with enhanced error handling
            moderationMechanics = ModerationMechanics.getInstance();
            moderationMechanics.onEnable();

            getLogger().info("Moderation systems initialized successfully");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing moderation systems", e);
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
            GlowingDropsInitializer.initialize();
            // NEW: Initialize TAB Plugin Integration last (after all player data systems are ready)
            allSuccess &= safeInitialize("TAB Plugin Integration", this::initializeTabPluginIntegration);

            getLogger().info("Game systems initialization completed");
            return allSuccess;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing game systems", e);
            return false;
        }
    }

    public static void setT6Enabled(boolean enabled) {
        t6Enabled = enabled;
        if (instance != null) {
            instance.getConfig().set("game.t6-enabled", enabled);
            instance.saveConfig();
        }
    }

    // =============================================================================
    // NEW: TAB PLUGIN INTEGRATION GETTERS
    // =============================================================================

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

    // =============================================================================
    // SYSTEM GETTERS
    // =============================================================================

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
            VendorManager.initialize(this);
            vendorManager = VendorManager.getInstance();
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
            // 2. Initialize death mechanics
            DeathMechanics.getInstance().onEnable();

            // 3. Initialize combat logout mechanics
            CombatLogoutMechanics.getInstance().onEnable();
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
            lootChestManager = ChestManager.getInstance();
            lootChestManager.initialize();

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

            // Player commands
            success &= registerCommand("logout", new com.rednetty.server.commands.player.LogoutCommand(),
                    new com.rednetty.server.commands.player.LogoutCommand());
            success &= registerCommand("toggles", new TogglesCommand());
            success &= registerCommand("alignment", new AlignmentCommand(alignmentMechanics));
            success &= registerCommand("invsee", new InvseeCommand());

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

            // Crate commands
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

            // Item commands
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
            success &= registerCommand("ban", new BanCommand(moderationMechanics));
            success &= registerCommand("unban", new UnbanCommand(moderationMechanics));
            success &= registerCommand("mute", new MuteCommand(moderationMechanics));
            success &= registerCommand("unmute", new UnmuteCommand(moderationMechanics));
            success &= registerCommand("vanish", new VanishCommand(this));
            success &= registerCommand("setrank", new SetRankCommand(moderationMechanics));

            // Admin commands
            success &= registerCommand("shutdown", new com.rednetty.server.commands.staff.admin.ShutdownCommand(),
                    new com.rednetty.server.commands.staff.admin.ShutdownCommand());

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
        getLogger().info("Trade System: " + (tradeManager != null ? "Active" : "Inactive"));
        getLogger().info("Trade Listener: " + (tradeListener != null ? "Active" : "Inactive"));
        getLogger().info("Moderation System: " + (moderationMechanics != null ? "Active" : "Inactive"));
        getLogger().info("Crate System: " + (crateManager != null ? "Active" : "Inactive"));
        getLogger().info("Loot Chest System: " + (lootChestManager != null ? "Active" : "Inactive"));
        getLogger().info("Mob System: " + (mobManager != null ? "Active" : "Inactive"));
        getLogger().info("Market System: " + (marketManager != null ? "Active" : "Inactive"));
        getLogger().info("Awakening Stone System: " + (awakeningStoneSystem != null ? "Active" : "Inactive"));
        getLogger().info("Binding Rune System: " + (bindingRuneSystem != null ? "Active" : "Inactive"));
        getLogger().info("Corruption System: " + (corruptionSystem != null ? "Active" : "Inactive"));
        getLogger().info("Essence Crystal System: " + (essenceCrystalSystem != null ? "Active" : "Inactive"));
        getLogger().info("Forge Hammer System: " + (forgeHammerSystem != null ? "Active" : "Inactive"));
        getLogger().info("TAB Plugin Integration: " + (tabPluginIntegration != null && tabPluginIntegration.isEnabled() ? "Active" : "Inactive"));
        getLogger().info("==============================");

        getLogger().info("YakRealms startup completed successfully!");
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Starting YakRealms shutdown...");

            // Shutdown TAB integration first
            if (tabPluginIntegration != null) {
                getLogger().info("Shutting down TAB Plugin Integration...");
                tabPluginIntegration.shutdown();
                getLogger().info("TAB Plugin Integration shutdown completed");
            }

            // Shutdown trade system first to clean up active trades
            if (tradeManager != null) {
                getLogger().info("Shutting down trade system...");
                tradeManager.clearAllTrades();
                getLogger().info("Trade system shutdown completed");
            }

            if (tradeListener != null) {
                getLogger().info("Cleaning up trade listener...");
                tradeListener.cleanup();
                getLogger().info("Trade listener cleanup completed");
            }

            // Shutdown menu system first to clean up all online players
            if (MenuSystemInitializer.isInitialized()) {
                getLogger().info("Shutting down menu item system...");
                MenuSystemInitializer.shutdown();
                getLogger().info("Menu item system shutdown completed");
            }

            // Shutdown moderation mechanics
            if (moderationMechanics != null) {
                getLogger().info("Shutting down moderation mechanics...");
                moderationMechanics.onDisable();
                getLogger().info("Moderation mechanics shutdown completed");
            }

            // Shutdown in reverse order
            if (playerMechanics != null) {
                playerMechanics.onDisable();
            }

            if (playerManager != null) {
                playerManager.onDisable();
            }


            teleportBookSystem.onDisable();
            HologramManager.cleanup();
            getLogger().info("HologramManager cleanup completed");
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
    }

    /**
     * Get TAB Plugin Integration instance
     */
    public TabPluginIntegration getTabPluginIntegration() {
        return tabPluginIntegration;
    }

    public MongoDBManager getMongoDBManager() {
        return mongoDBManager;
    }

    public YakPlayerManager getPlayerManager() {
        return playerManager;
    }

    public PlayerMechanics getPlayerMechanics() {
        return playerMechanics;
    }

    public ModerationMechanics getModerationMechanics() {
        return moderationMechanics;
    }

    public PartyMechanics getPartyMechanics() {
        return partyMechanics;
    }

    public DashMechanics getDashMechanics() {
        return dashMechanics;
    }

    public SpeedfishMechanics getSpeedfishMechanics() {
        return speedfishMechanics;
    }

    public MountManager getMountManager() {
        return mountManager;
    }

    public ScrollManager getScrollManager() {
        return scrollManager;
    }

    public OrbManager getOrbManager() {
        return orbManager;
    }

    public Journal getJournalSystem() {
        return journalSystem;
    }

    public MenuItemManager getMenuItemManager() {
        return menuItemManager;
    }

    // Trade system getters
    public TradeManager getTradeManager() {
        return tradeManager;
    }

    public TradeListener getTradeListener() {
        return tradeListener;
    }

    // Item enhancement systems getters
    public AwakeningStoneSystem getAwakeningStoneSystem() {
        return awakeningStoneSystem;
    }

    public BindingRuneSystem getBindingRuneSystem() {
        return bindingRuneSystem;
    }

    public CorruptionSystem getCorruptionSystem() {
        return corruptionSystem;
    }

    public EssenceCrystalSystem getEssenceCrystalSystem() {
        return essenceCrystalSystem;
    }

    public ForgeHammerSystem getForgeHammerSystem() {
        return forgeHammerSystem;
    }

    public AlignmentMechanics getAlignmentMechanics() {
        return alignmentMechanics;
    }

    // =============================================================================
    // LOGGING AND UTILITY METHODS
    // =============================================================================

    public DeathRemnantManager getDeathRemnantManager() {
        return deathRemnantManager;
    }

    public ChatMechanics getChatMechanics() {
        return chatMechanics;
    }

    public CombatMechanics getCombatMechanics() {
        return combatMechanics;
    }

    public MagicStaff getMagicStaff() {
        return magicStaff;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public BankManager getBankManager() {
        return bankManager;
    }

    public GemPouchManager getGemPouchManager() {
        return gemPouchManager;
    }

    public VendorManager getVendorManager() {
        return vendorManager;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }

    // =============================================================================
    // SAFE GETTERS FOR OTHER SYSTEMS
    // =============================================================================

    public MobManager getMobManager() {
        return mobManager;
    }

    public SpawnerCommand getSpawnerCommand() {
        return spawnerCommand;
    }

    public DropsManager getDropsManager() {
        return dropsManager;
    }

    public DropsHandler getDropsHandler() {
        return dropsHandler;
    }

    public LootBuffManager getLootBuffManager() {
        return lootBuffManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public TeleportBookSystem getTeleportBookSystem() {
        return teleportBookSystem;
    }

    public HearthstoneSystem getHearthstoneSystem() {
        return hearthstoneSystem;
    }

    public PortalSystem getPortalSystem() {
        return portalSystem;
    }

    public TrailSystem getTrailSystem() {
        return trailSystem;
    }

    public ParticleSystem getParticleSystem() {
        return particleSystem;
    }

    public PathManager getPathManager() {
        return pathManager;
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public ChestManager getLootChestManager() {
        return lootChestManager;
    }

    private void initializeLogFile() {
        file = new File(this.getDataFolder(), "logs_" + System.currentTimeMillis() + ".log");
    }

    public boolean isDebugMode() {
        return getConfig().getBoolean("debug", false);
    }

    public boolean isMobsEnabled() {
        return mobsEnabled;
    }

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

    // =============================================================================
    // MERCHANT SYSTEM METHODS
    // =============================================================================

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

    @FunctionalInterface
    private interface SystemInitializer {
        boolean initialize() throws Exception;
    }
}