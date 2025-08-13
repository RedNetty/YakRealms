package com.rednetty.server.mechanics.item.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.crates.types.CrateType;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central manager for the crate system with animation integration
 * Handles crate opening, reward distribution, statistics, and player interactions
 */
public class CrateManager {
    private static CrateManager instance;
    private final YakRealms plugin;
    private final Logger logger;

    // Core components
    private CrateFactory crateFactory;
    private CrateAnimationManager animationManager;
    private CrateRewardsManager rewardsManager;
    private CrateHandler crateHandler;
    private EconomyManager economyManager;
    private YakPlayerManager playerManager;

    // Active sessions and processing tracking
    private final Map<UUID, CrateOpening> activeOpenings = new ConcurrentHashMap<>();
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<CrateType, CrateConfiguration> crateConfigurations = new HashMap<>();
    private final Map<UUID, Long> lastOpenTimes = new ConcurrentHashMap<>();

    // Statistics tracking
    private final Map<CrateType, Integer> cratesOpened = new ConcurrentHashMap<>();
    private final Map<CrateType, Integer> cratesGenerated = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerOpenCounts = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionStats = new ConcurrentHashMap<>();
    private long totalCratesOpened = 0;
    private long systemStartTime;

    // Configuration and data persistence
    private File configFile;
    private FileConfiguration config;
    private File statsFile;
    private FileConfiguration statsConfig;

    // Tasks and timers
    private BukkitTask cleanupTask;
    private BukkitTask statisticsTask;
    private BukkitTask notificationTask;

    // System settings
    private boolean enableAnimations = true;
    private boolean enableSounds = true;
    private boolean enableParticles = true;
    private boolean enableActionBar = true;
    private int maxConcurrentOpenings = 50;
    private long openingCooldown = 1000; // 1 second default
    private boolean enableStatistics = true;
    private boolean enableAutoSave = true;

    /**
     * Private constructor for singleton pattern
     */
    private CrateManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.systemStartTime = System.currentTimeMillis();
    }

    /**
     * Gets the singleton instance
     */
    public static synchronized CrateManager getInstance() {
        if (instance == null) {
            instance = new CrateManager();
        }
        return instance;
    }

    /**
     * Initialize the crate system with all components
     */
    public void initialize() {
        try {
            logger.info("Initializing crate system...");

            // Load configuration first
            loadConfiguration();

            // Initialize core components
            initializeComponents();

            // Load crate configurations
            loadCrateConfigurations();

            // Load statistics
            loadStatistics();

            // Register event handler
            plugin.getServer().getPluginManager().registerEvents(crateHandler, plugin);

            // Start background tasks
            startBackgroundTasks();

            // Validate system integrity
            validateSystemIntegrity();

            logger.info("Crate system initialized successfully with " +
                    crateConfigurations.size() + " crate types and " +
                    getFeatureString());

        } catch (Exception e) {
            logger.severe("Failed to initialize crate system: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Crate system initialization failed", e);
        }
    }

    /**
     * Loads configuration from files
     */
    private void loadConfiguration() {
        // Create config file if it doesn't exist
        configFile = new File(plugin.getDataFolder(), "crates_config.yml");
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Load settings
        enableAnimations = config.getBoolean("features.animations", true);
        enableSounds = config.getBoolean("features.sounds", true);
        enableParticles = config.getBoolean("features.particles", true);
        enableActionBar = config.getBoolean("features.action-bar", true);
        maxConcurrentOpenings = config.getInt("limits.max-concurrent-openings", 50);
        openingCooldown = config.getLong("limits.opening-cooldown-ms", 1000);
        enableStatistics = config.getBoolean("features.statistics", true);
        enableAutoSave = config.getBoolean("features.auto-save", true);

        logger.fine("Loaded crate configuration with " + getFeatureString());
    }

    /**
     * Creates default configuration
     */
    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();

            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(configFile);

            // Feature settings
            defaultConfig.set("features.animations", true);
            defaultConfig.set("features.sounds", true);
            defaultConfig.set("features.particles", true);
            defaultConfig.set("features.action-bar", true);
            defaultConfig.set("features.statistics", true);
            defaultConfig.set("features.auto-save", true);

            // Limit settings
            defaultConfig.set("limits.max-concurrent-openings", 50);
            defaultConfig.set("limits.opening-cooldown-ms", 1000);
            defaultConfig.set("limits.max-inventory-checks", 3);

            // Reward settings
            defaultConfig.set("rewards.base-equipment-chance", 70);
            defaultConfig.set("rewards.base-gem-chance", 85);
            defaultConfig.set("rewards.base-orb-chance", 25);
            defaultConfig.set("rewards.tier-scaling-bonus", 5);

            // Animation settings
            defaultConfig.set("animations.spin-duration", 200);
            defaultConfig.set("animations.initial-speed", 0.8);
            defaultConfig.set("animations.deceleration", 0.985);
            defaultConfig.set("animations.min-speed", 0.01);

            defaultConfig.save(configFile);
            logger.info("Created default crate configuration");

        } catch (IOException e) {
            logger.warning("Failed to create default crate configuration: " + e.getMessage());
        }
    }

    /**
     * Initializes core components
     */
    private void initializeComponents() {
        this.crateFactory = new CrateFactory();
        this.animationManager = new CrateAnimationManager();
        this.rewardsManager = new CrateRewardsManager();
        this.crateHandler = new CrateHandler();
        this.economyManager = EconomyManager.getInstance();
        this.playerManager = YakPlayerManager.getInstance();

        logger.fine("Initialized crate system components");
    }

    /**
     * Open a crate with single reward generation
     */
    public boolean openCrate(Player player, ItemStack crateItem) {
        UUID playerId = player.getUniqueId();

        try {
            // Validation
            if (!validateCrateOpening(player, crateItem)) {
                return false;
            }

            // Check cooldown
            if (!checkOpeningCooldown(player)) {
                return false;
            }

            // Check concurrent openings limit
            if (activeOpenings.size() >= maxConcurrentOpenings) {
                sendMessage(player, MessageType.ERROR, "System Overloaded",
                        "The mystical energies are overwhelmed! Please try again in a moment.");
                return false;
            }

            // Determine crate type with validation
            CrateType crateType = crateFactory.determineCrateType(crateItem);
            if (crateType == null) {
                sendMessage(player, MessageType.ERROR, "Invalid Crate",
                        "This crate's mystical signature is unreadable!");
                return false;
            }

            // Get configuration
            CrateConfiguration configuration = crateConfigurations.get(crateType);
            if (configuration == null) {
                logger.warning("No configuration found for crate type: " + crateType);
                sendMessage(player, MessageType.ERROR, "Configuration Error",
                        "This crate type is not properly configured. Please contact an administrator.");
                return false;
            }

            // Final validation checks
            if (!performFinalValidation(player, crateType)) {
                return false;
            }

            // Create opening session
            CrateOpening opening = new CrateOpening(player, crateType, configuration);
            activeOpenings.put(playerId, opening);
            processingPlayers.add(playerId);

            // Update cooldown
            lastOpenTimes.put(playerId, System.currentTimeMillis());

            // Send opening message
            sendCrateOpeningMessage(player, crateType);

            // Generate rewards once
            List<ItemStack> rewards = rewardsManager.generateRewards(crateType, configuration);

            if (rewards.isEmpty()) {
                logger.warning("No rewards generated for crate opening: " + crateType);
                rewards.add(createFallbackReward(crateType));
            }

            // Debug logging
            logger.info("=== CRATE OPENING DEBUG INFO ===");
            logger.info("Player: " + player.getName());
            logger.info("Crate Type: " + crateType);
            logger.info("Generated " + rewards.size() + " rewards:");
            for (int i = 0; i < rewards.size(); i++) {
                ItemStack reward = rewards.get(i);
                String rewardName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName() ?
                        reward.getItemMeta().getDisplayName() :
                        reward.getType().name().replace("_", " ");
                logger.info("  Reward " + (i + 1) + ": " + rewardName + " x" + reward.getAmount());
            }
            logger.info("=== END DEBUG INFO ===");

            // Start animation with the same rewards
            if (enableAnimations) {
                // Pass the exact same rewards to the animation
                animationManager.startCrateOpeningWithRewards(opening, rewards);
            } else {
                // Immediate completion with the same rewards
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Complete with the pre-generated rewards
                        completeCrateOpeningWithRewards(opening, rewards);
                    }
                }.runTaskLater(plugin, 20L); // 1 second delay
            }

            // Update statistics
            updateOpeningStatistics(crateType, playerId);

            logger.info("Started crate opening for " + player.getName() + " with " + rewards.size() + " rewards");
            return true;

        } catch (Exception e) {
            logger.severe("Error opening crate for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();

            // Cleanup on error
            cleanupFailedOpening(playerId, player);
            return false;
        }
    }

    /**
     * Validation for crate opening
     */
    private boolean validateCrateOpening(Player player, ItemStack crateItem) {
        UUID playerId = player.getUniqueId();

        // Basic parameter validation
        if (player == null || crateItem == null) {
            logger.warning("Invalid parameters for crate opening");
            return false;
        }

        // Player online check
        if (!player.isOnline()) {
            return false;
        }

        // Already processing check
        if (processingPlayers.contains(playerId) || activeOpenings.containsKey(playerId)) {
            sendMessage(player, MessageType.WARNING, "Already Processing",
                    "You are already opening a crate!");
            playErrorSound(player);
            return false;
        }

        // Inventory space check
        if (!hasInventorySpace(player, 3)) { // Check for at least 3 free slots
            sendInventoryFullMessage(player);
            return false;
        }

        // Check if crate is valid
        if (!crateFactory.isCrate(crateItem)) {
            sendMessage(player, MessageType.ERROR, "Invalid Item",
                    "This item is not a valid mystical crate!");
            playErrorSound(player);
            return false;
        }

        return true;
    }

    /**
     * Checks opening cooldown
     */
    private boolean checkOpeningCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        Long lastOpen = lastOpenTimes.get(playerId);

        if (lastOpen != null) {
            long timeSince = System.currentTimeMillis() - lastOpen;
            if (timeSince < openingCooldown) {
                long remaining = (openingCooldown - timeSince) / 1000;
                sendMessage(player, MessageType.INFO, "Cooldown Active",
                        "The mystical energies need " + remaining + " more seconds to recharge...");
                return false;
            }
        }

        return true;
    }

    /**
     * Performs final validation checks
     */
    private boolean performFinalValidation(Player player, CrateType crateType) {
        // Check if tier is enabled
        if (crateType.getTier() == 6 && !YakRealms.isT6Enabled()) {
            sendMessage(player, MessageType.ERROR, "Tier Disabled",
                    "Tier 6 content is currently disabled!");
            return false;
        }

        // Check if Halloween crates are allowed (seasonal check)
        if (crateType.isHalloween() && !isHalloweenSeason()) {
            sendMessage(player, MessageType.SPECIAL, "Seasonal Restriction",
                    "🎃 Halloween crates can only be opened during spooky season! 🎃");
            return false;
        }

        return true;
    }

    /**
     * Sends crate opening message
     */
    private void sendCrateOpeningMessage(Player player, CrateType crateType) {
        String crateName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName();

        // Send centered opening message
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage("&b✦ &l&bOPENING CRATE &b✦"));
        player.sendMessage(TextUtil.getCenteredMessage("&7Unsealing &e" + crateName + " &7Crate"));
        player.sendMessage("");

        // Action bar message if enabled
        if (enableActionBar) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(TextUtil.colorize("&6✨ Opening crate... ✨")));
        }

        // Play opening sound
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        }
    }

    /**
     * Original completion method (for non-animated openings)
     */
    public void completeCrateOpening(CrateOpening opening) {
        if (opening == null || opening.getPlayer() == null) {
            logger.warning("Invalid opening provided to completeCrateOpening");
            return;
        }

        Player player = opening.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Mark as completed
            opening.advanceToPhase(CrateOpening.OpeningPhase.COMPLETED);

            // Generate rewards
            List<ItemStack> rewards = rewardsManager.generateRewards(
                    opening.getCrateType(), opening.getConfiguration());

            if (rewards.isEmpty()) {
                logger.warning("No rewards generated for crate opening: " + opening.getCrateType());
                rewards.add(createFallbackReward(opening.getCrateType()));
            }

            // Give rewards to player
            giveRewardsToPlayer(player, rewards, opening.getCrateType());

            // Send completion message
            sendCompletionMessage(player, opening, rewards);

            // Play completion effects
            playCompletionEffects(player, opening);

            // Update player statistics
            updatePlayerStatistics(player, opening, rewards);

            // Save statistics if enabled
            if (enableStatistics && enableAutoSave) {
                saveStatistics();
            }

            logger.fine("Completed crate opening for player " + player.getName() +
                    " with " + rewards.size() + " rewards");

        } catch (Exception e) {
            logger.severe("Error completing crate opening for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            handleCompletionError(player, opening);
        } finally {
            // Always clean up
            processingPlayers.remove(playerId);
            activeOpenings.remove(playerId);
            animationManager.cancelAnimation(playerId);
        }
    }

    /**
     * Completes crate opening with pre-generated rewards (for animation system)
     * NOTE: This method assumes rewards have already been given to the player by the animation
     */
    public void completeCrateOpeningWithRewards(CrateOpening opening, List<ItemStack> rewards) {
        if (opening == null || opening.getPlayer() == null) {
            logger.warning("Invalid opening provided to completeCrateOpeningWithRewards");
            return;
        }

        Player player = opening.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Mark as completed
            opening.advanceToPhase(CrateOpening.OpeningPhase.COMPLETED);

            // Validate rewards
            if (rewards == null || rewards.isEmpty()) {
                logger.warning("No rewards provided for crate opening completion");
                rewards = Arrays.asList(createFallbackReward(opening.getCrateType()));
            }

            // DO NOT give rewards again - they were already given by the animation
            logger.info("Completing crate opening for " + player.getName() + " with " + rewards.size() + " pre-given rewards");

            // Send minimal completion message (detailed message already sent by animation)
            sendMinimalCompletionMessage(player, opening, rewards);

            // Play minimal completion effects
            playMinimalCompletionEffects(player, opening);

            // Update statistics
            updatePlayerStatistics(player, opening, rewards);

            // Save statistics if enabled
            if (enableStatistics && enableAutoSave) {
                saveStatistics();
            }

            logger.fine("Completed crate opening with pre-generated rewards for player " + player.getName());

        } catch (Exception e) {
            logger.severe("Error completing crate opening with rewards: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up
            processingPlayers.remove(playerId);
            activeOpenings.remove(playerId);
        }
    }

    /**
     * Gives rewards to player with overflow handling
     */
    private void giveRewardsToPlayer(Player player, List<ItemStack> rewards, CrateType crateType) {
        List<ItemStack> overflow = new ArrayList<>();

        for (ItemStack reward : rewards) {
            if (reward == null) continue;

            // Try to add to inventory
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);

            // Handle overflow
            overflow.addAll(leftover.values());
        }

        // Handle overflow items
        if (!overflow.isEmpty()) {
            handleRewardOverflow(player, overflow, crateType);
        }
    }

    /**
     * Handles reward overflow when inventory is full
     */
    private void handleRewardOverflow(Player player, List<ItemStack> overflow, CrateType crateType) {
        Location dropLocation = player.getLocation().add(0, 1, 0);

        for (ItemStack item : overflow) {
            player.getWorld().dropItemNaturally(dropLocation, item);
        }

        // Notify player
        sendMessage(player, MessageType.WARNING, "Inventory Full",
                "Some rewards were dropped nearby due to full inventory!");
        player.sendMessage(TextUtil.colorize("&7Items dropped: &f" + overflow.size()));

        // Action bar notification
        if (enableActionBar) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(TextUtil.colorize("&e⚠ Some rewards dropped nearby! ⚠")));
        }

        // Sound notification
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        }
    }

    /**
     * Sends completion message
     */
    private void sendCompletionMessage(Player player, CrateOpening opening, List<ItemStack> rewards) {
        String crateName = (opening.getCrateType().isHalloween() ? "Halloween " : "") +
                opening.getCrateType().getDisplayName();

        player.sendMessage("");
        player.sendMessage(TextUtil.colorize("&6✦ " + crateName + " Crate Opened! ✦"));
        player.sendMessage(TextUtil.colorize("&7You received &f" + rewards.size() +
                "&7 items from this crate."));

        // Show first few rewards
        for (int i = 0; i < Math.min(3, rewards.size()); i++) {
            ItemStack reward = rewards.get(i);
            if (reward != null && reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()) {
                player.sendMessage(TextUtil.colorize("&f  • " + reward.getItemMeta().getDisplayName()));
            }
        }

        if (rewards.size() > 3) {
            player.sendMessage(TextUtil.colorize("&7  ... and " + (rewards.size() - 3) + " more items!"));
        }

        player.sendMessage("");
    }

    /**
     * Plays completion effects
     */
    private void playCompletionEffects(Player player, CrateOpening opening) {
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        if (enableParticles) {
            Location location = player.getLocation().add(0, 1, 0);
            World world = location.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, location, 10, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    /**
     * Sends a minimal completion message (since detailed message was already sent by animation)
     */
    private void sendMinimalCompletionMessage(Player player, CrateOpening opening, List<ItemStack> rewards) {
        // Just log the completion - detailed message already sent by animation
        logger.fine("Crate opening completed for " + player.getName() + " - " + rewards.size() + " rewards given");

        // Optional: Send action bar confirmation
        if (enableActionBar) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(TextUtil.colorize("&a✓ Crate opening completed successfully!")));
        }
    }

    /**
     * Plays minimal completion effects (since main effects were already played by animation)
     */
    private void playMinimalCompletionEffects(Player player, CrateOpening opening) {
        // Minimal effects only - main effects already played by animation
        if (enableSounds) {
            // Just a subtle confirmation sound
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }
    }

    /**
     * Handles completion errors with user-friendly fallbacks
     */
    private void handleCompletionError(Player player, CrateOpening opening) {
        // Give fallback reward
        ItemStack fallback = createFallbackReward(opening.getCrateType());
        player.getInventory().addItem(fallback);

        // Send error message
        sendMessage(player, MessageType.WARNING, "Mystical Interference",
                "The cosmic energies were disrupted, but you received compensation!");
        player.sendMessage(TextUtil.colorize("&a✓ Emergency reward granted"));

        // Play error sound
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        }
    }

    // Message utility methods

    /**
     * Message types for consistent formatting
     */
    private enum MessageType {
        INFO("&b", "ℹ"),
        SUCCESS("&a", "✓"),
        WARNING("&e", "⚠"),
        ERROR("&c", "✗"),
        SPECIAL("&6", "✦");

        private final String color;
        private final String icon;

        MessageType(String color, String icon) {
            this.color = color;
            this.icon = icon;
        }

        public String getColor() { return color; }
        public String getIcon() { return icon; }
    }

    /**
     * Sends a formatted message to player
     */
    private void sendMessage(Player player, MessageType type, String title, String message) {
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(type.getColor() + type.getIcon() + " &l" + title.toUpperCase() + " " + type.getColor() + type.getIcon()));
        player.sendMessage(TextUtil.getCenteredMessage("&7" + message));
        player.sendMessage("");
    }

    // Utility methods

    /**
     * Check inventory space
     */
    private boolean hasInventorySpace(Player player, int requiredSlots) {
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots >= requiredSlots;
    }

    /**
     * Sends inventory full message with helpful suggestions
     */
    private void sendInventoryFullMessage(Player player) {
        sendMessage(player, MessageType.WARNING, "Inventory Full",
                "Your inventory is too full to receive crate rewards!");
        player.sendMessage(TextUtil.getCenteredMessage("&ePlease make at least 3 empty slots and try again."));
        player.sendMessage("");
        playErrorSound(player);
    }

    /**
     * Plays error sound
     */
    private void playErrorSound(Player player) {
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }

    /**
     * Cleans up failed opening
     */
    private void cleanupFailedOpening(UUID playerId, Player player) {
        processingPlayers.remove(playerId);
        activeOpenings.remove(playerId);
        lastOpenTimes.remove(playerId);

        if (player != null && player.isOnline()) {
            player.sendMessage(TextUtil.getCenteredMessage("&e✦ Crate opening has been safely cancelled. ✦"));
        }
    }

    /**
     * Updates opening statistics
     */
    private void updateOpeningStatistics(CrateType crateType, UUID playerId) {
        cratesOpened.merge(crateType, 1, Integer::sum);
        totalCratesOpened++;
        playerOpenCounts.merge(playerId, 1, Integer::sum);

        sessionStats.put("lastCrateOpened", crateType.name());
        sessionStats.put("lastOpenTime", System.currentTimeMillis());
    }

    /**
     * Updates player-specific statistics
     */
    private void updatePlayerStatistics(Player player, CrateOpening opening, List<ItemStack> rewards) {
        if (!enableStatistics) return;

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player.getUniqueId());
            if (yakPlayer != null) {
                // Update player's crate statistics
                sessionStats.put("player_" + player.getUniqueId() + "_crates_opened",
                        playerOpenCounts.get(player.getUniqueId()));
                sessionStats.put("player_" + player.getUniqueId() + "_last_crate", opening.getCrateType().name());
                sessionStats.put("player_" + player.getUniqueId() + "_last_rewards", rewards.size());
            }
        } catch (Exception e) {
            logger.fine("Could not update player statistics: " + e.getMessage());
        }
    }

    // Configuration and data management methods

    /**
     * Loads crate configurations with validation
     */
    private void loadCrateConfigurations() {
        ConfigurationSection cratesSection = config.getConfigurationSection("crate-types");

        if (cratesSection == null) {
            // Create default configurations
            createDefaultCrateConfigurations();
        } else {
            loadConfigurationsFromFile(cratesSection);
        }

        // Validate all configurations
        validateConfigurations();

        logger.info("Loaded " + crateConfigurations.size() + " crate configurations");
    }

    /**
     * Creates default crate configurations
     */
    private void createDefaultCrateConfigurations() {
        for (CrateType crateType : CrateType.values()) {
            CrateConfiguration config = createDefaultConfiguration(crateType);
            crateConfigurations.put(crateType, config);
        }

        // Save default configurations to file
        saveDefaultConfigurationsToFile();
    }

    /**
     * Creates a default configuration for a crate type
     */
    private CrateConfiguration createDefaultConfiguration(CrateType crateType) {
        String displayName = (crateType.isHalloween() ? "Halloween " : "") +
                crateType.getDisplayName() + " Mystical Crate";

        List<String> contents = Arrays.asList(
                "Tier " + crateType.getTier() + " Equipment & Weapons",
                "Enhancement Scrolls & Orbs",
                "Precious Gems & Materials",
                "Special Mystical Items"
        );

        Sound completionSound = switch (crateType.getTier()) {
            case 1, 2 -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3, 4 -> Sound.ENTITY_PLAYER_LEVELUP;
            case 5, 6 -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
            default -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        };

        // Animation duration
        long animationDuration = 10000; // 10 seconds

        return new CrateConfiguration(crateType, displayName, crateType.getTier(),
                contents, completionSound, animationDuration);
    }

    /**
     * Validates all configurations
     */
    private void validateConfigurations() {
        crateConfigurations.entrySet().removeIf(entry -> {
            CrateConfiguration config = entry.getValue();
            if (config.getTier() < 1 || config.getTier() > 6) {
                logger.warning("Invalid tier for crate " + entry.getKey() + ": " + config.getTier());
                return true;
            }
            if (config.getAnimationDuration() < 1000 || config.getAnimationDuration() > 30000) {
                logger.warning("Invalid animation duration for crate " + entry.getKey() + ": " +
                        config.getAnimationDuration());
                return true;
            }
            return false;
        });
    }

    /**
     * Background task management
     */
    private void startBackgroundTasks() {
        // Cleanup task
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup();
            }
        }.runTaskTimerAsynchronously(plugin, 600L, 600L); // Every 30 seconds

        // Statistics task
        if (enableStatistics) {
            statisticsTask = new BukkitRunnable() {
                @Override
                public void run() {
                    updateStatistics();
                }
            }.runTaskTimerAsynchronously(plugin, 1200L, 1200L); // Every minute
        }

        // Notification task for milestones
        notificationTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkMilestones();
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // Every 5 minutes
    }

    /**
     * Cleanup with better error handling
     */
    private void performCleanup() {
        try {
            // Clean up expired openings
            long currentTime = System.currentTimeMillis();

            activeOpenings.entrySet().removeIf(entry -> {
                CrateOpening opening = entry.getValue();
                UUID playerId = entry.getKey();

                // Check if opening is timed out or player is offline
                Player player = plugin.getServer().getPlayer(playerId);
                if (opening.isTimedOut() || player == null || !player.isOnline()) {
                    // Force completion on main thread
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                completeCrateOpening(opening);
                            } catch (Exception e) {
                                logger.warning("Error during cleanup completion: " + e.getMessage());
                            }
                        }
                    }.runTask(plugin);
                    return true;
                }

                return false;
            });

            // Clean up processing players who aren't in active openings
            processingPlayers.removeIf(playerId -> !activeOpenings.containsKey(playerId));

            // Clean up old cooldown entries (older than 5 minutes)
            lastOpenTimes.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > 300000);

        } catch (Exception e) {
            logger.warning("Error during crate cleanup: " + e.getMessage());
        }
    }

    /**
     * Updates runtime statistics
     */
    private void updateStatistics() {
        try {
            long uptime = System.currentTimeMillis() - systemStartTime;
            sessionStats.put("uptime", uptime);
            sessionStats.put("totalCratesOpened", totalCratesOpened);
            sessionStats.put("activeOpenings", activeOpenings.size());
            sessionStats.put("uniquePlayers", playerOpenCounts.size());

            // Calculate averages
            if (totalCratesOpened > 0) {
                double avgPerPlayer = (double) totalCratesOpened / playerOpenCounts.size();
                sessionStats.put("averageCratesPerPlayer", avgPerPlayer);
            }

        } catch (Exception e) {
            logger.fine("Error updating statistics: " + e.getMessage());
        }
    }

    /**
     * Checks for milestones and celebrates them
     */
    private void checkMilestones() {
        // Check for total crates opened milestones
        long total = totalCratesOpened;
        if (total > 0 && total % 100 == 0) {
            broadcastMilestone("✦ " + total + " crates have been opened server-wide! ✦");
        }

        // Check for concurrent openings record
        int concurrent = activeOpenings.size();
        Integer previousRecord = (Integer) sessionStats.get("concurrentRecord");
        if (previousRecord == null || concurrent > previousRecord) {
            if (concurrent >= 5) { // Only celebrate if significant
                sessionStats.put("concurrentRecord", concurrent);
                broadcastMilestone("⚡ New record: " + concurrent + " simultaneous crate openings! ⚡");
            }
        }
    }

    /**
     * Broadcasts milestone messages to online players
     */
    private void broadcastMilestone(String message) {
        String formattedMessage = TextUtil.getCenteredMessage("&6" + message);
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            onlinePlayer.sendMessage(formattedMessage);
            if (enableSounds) {
                onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.5f);
            }
        }
        logger.info("Milestone: " + ChatColor.stripColor(message));
    }

    // System management methods

    /**
     * Validates system integrity
     */
    private void validateSystemIntegrity() {
        if (crateFactory == null || animationManager == null || rewardsManager == null) {
            throw new RuntimeException("Core components not properly initialized");
        }

        if (crateConfigurations.isEmpty()) {
            throw new RuntimeException("No crate configurations loaded");
        }

        if (economyManager == null) {
            logger.warning("Economy manager not available - some features may not work");
        }

        logger.fine("System integrity validation passed");
    }

    /**
     * Gets feature string for logging
     */
    private String getFeatureString() {
        List<String> features = new ArrayList<>();
        if (enableAnimations) features.add("animations");
        if (enableSounds) features.add("sounds");
        if (enableParticles) features.add("particles");
        if (enableActionBar) features.add("action-bar");
        if (enableStatistics) features.add("statistics");

        return features.isEmpty() ? "minimal features" : String.join(", ", features) + " enabled";
    }

    /**
     * Checks if it's Halloween season
     */
    private boolean isHalloweenSeason() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        // October 15 - November 15
        return (month == Calendar.OCTOBER && day >= 15) ||
                (month == Calendar.NOVEMBER && day <= 15);
    }

    /**
     * Creates fallback reward
     */
    private ItemStack createFallbackReward(CrateType crateType) {
        try {
            int gemAmount = crateType.getTier() * 100;
            return YakRealms.getInstance().getBankManager().createBankNote(gemAmount);
        } catch (Exception e) {
            // Ultimate fallback
            ItemStack emerald = new ItemStack(Material.EMERALD, crateType.getTier() * 5);
            ItemMeta meta = emerald.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(TextUtil.colorize("&aEmergency Compensation"));
                meta.setLore(Arrays.asList(
                        TextUtil.colorize("&7Compensation for tier " + crateType.getTier() + " crate"),
                        TextUtil.colorize("&7Please contact an administrator")
                ));
                emerald.setItemMeta(meta);
            }
            return emerald;
        }
    }

    /**
     * Shutdown with proper cleanup and data saving
     */
    public void shutdown() {
        try {
            logger.info("Shutting down crate system...");

            // Cancel background tasks
            if (cleanupTask != null) cleanupTask.cancel();
            if (statisticsTask != null) statisticsTask.cancel();
            if (notificationTask != null) notificationTask.cancel();

            // Complete any active openings
            List<CrateOpening> openingsList = new ArrayList<>(activeOpenings.values());
            for (CrateOpening opening : openingsList) {
                if (!opening.isCompleted()) {
                    try {
                        completeCrateOpening(opening);
                    } catch (Exception e) {
                        logger.warning("Error completing opening during shutdown: " + e.getMessage());
                    }
                }
            }

            // Clean up animation manager
            if (animationManager != null) {
                animationManager.cleanup();
            }

            // Save statistics and configuration
            if (enableStatistics && enableAutoSave) {
                saveStatistics();
            }
            saveConfiguration();

            // Clear collections
            activeOpenings.clear();
            processingPlayers.clear();
            crateConfigurations.clear();
            lastOpenTimes.clear();
            sessionStats.clear();

            logger.info("Crate system shut down successfully");

        } catch (Exception e) {
            logger.warning("Error during crate system shutdown: " + e.getMessage());
        }
    }

    // Getters and accessors

    public CrateFactory getCrateFactory() { return crateFactory; }
    public CrateAnimationManager getAnimationManager() { return animationManager; }
    public CrateRewardsManager getRewardsManager() { return rewardsManager; }
    public Set<UUID> getProcessingPlayers() { return new HashSet<>(processingPlayers); }
    public Map<UUID, CrateOpening> getActiveOpenings() { return new HashMap<>(activeOpenings); }
    public CrateConfiguration getConfiguration(CrateType crateType) { return crateConfigurations.get(crateType); }

    // API methods

    /**
     * Creates a crate with options
     */
    public ItemStack createCrate(CrateType crateType, boolean isHalloween) {
        ItemStack crate = crateFactory.createCrate(crateType, isHalloween);
        if (crate != null) {
            cratesGenerated.merge(crateType, 1, Integer::sum);
        }
        return crate;
    }

    public ItemStack createCrateKey() { return crateFactory.createCrateKey(); }

    public ItemStack createLockedCrate(CrateType crateType, boolean isHalloween) {
        ItemStack crate = crateFactory.createLockedCrate(crateType, isHalloween);
        if (crate != null) {
            cratesGenerated.merge(crateType, 1, Integer::sum);
        }
        return crate;
    }

    /**
     * Method to give crates to players
     */
    public void giveCratesToPlayer(Player player, CrateType crateType, int amount, boolean isHalloween, boolean isLocked) {
        if (amount <= 0) return;

        int remaining = amount;
        List<ItemStack> allCrates = new ArrayList<>();

        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);

            ItemStack crates = isLocked ?
                    createLockedCrate(crateType, isHalloween) :
                    createCrate(crateType, isHalloween);

            if (crates != null) {
                crates.setAmount(stackSize);
                allCrates.add(crates);
            }

            remaining -= stackSize;
        }

        // Try to add to inventory
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack crate : allCrates) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(crate);
            overflow.addAll(leftover.values());
        }

        // Handle overflow
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        // Send notification message
        String crateName = (isHalloween ? "Halloween " : "") + crateType.getDisplayName() + " Crate";
        sendMessage(player, MessageType.SUCCESS, "Crates Received",
                "Received: " + amount + "× " + crateName);

        if (isLocked) {
            player.sendMessage(TextUtil.getCenteredMessage("&7Status: &cLocked &7(requires crate key)"));
        }
        if (!overflow.isEmpty()) {
            player.sendMessage(TextUtil.getCenteredMessage("&eNote: " + overflow.size() + " crates dropped due to full inventory"));
        }

        // Play sound
        if (enableSounds) {
            Sound giveSound = isLocked ? Sound.BLOCK_CHEST_LOCKED : Sound.ENTITY_ITEM_PICKUP;
            player.playSound(player.getLocation(), giveSound, 1.0f, 1.2f);
        }
    }

    /**
     * Statistics method
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Basic stats
        stats.put("totalCratesOpened", totalCratesOpened);
        stats.put("activeOpenings", activeOpenings.size());
        stats.put("processingPlayers", processingPlayers.size());
        stats.put("configurationsLoaded", crateConfigurations.size());

        // Detailed stats
        stats.put("crateTypesOpened", new HashMap<>(cratesOpened));
        stats.put("crateTypesGenerated", new HashMap<>(cratesGenerated));
        stats.put("uniquePlayers", playerOpenCounts.size());
        stats.put("systemUptime", System.currentTimeMillis() - systemStartTime);

        // Session stats
        stats.putAll(sessionStats);

        // Feature status
        stats.put("featuresEnabled", Map.of(
                "animations", enableAnimations,
                "sounds", enableSounds,
                "particles", enableParticles,
                "actionBar", enableActionBar,
                "statistics", enableStatistics
        ));

        return stats;
    }

    // Data persistence methods (simplified implementations)

    private void saveStatistics() {
        if (enableAutoSave) {
            // Implementation for saving statistics to file
            // This would save to a YAML file
        }
    }

    private void loadStatistics() {
        // Implementation for loading statistics from file
    }

    private void saveConfiguration() {
        // Implementation for saving configuration to file
    }

    private void loadConfigurationsFromFile(ConfigurationSection section) {
        // Implementation for loading configurations from file
    }

    private void saveDefaultConfigurationsToFile() {
        // Implementation for saving default configurations to file
    }
}