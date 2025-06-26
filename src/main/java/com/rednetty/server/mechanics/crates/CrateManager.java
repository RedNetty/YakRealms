package com.rednetty.server.mechanics.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced central manager for the crate system using modern 1.20.4 features
 * Coordinates all crate-related functionality with improved performance and features
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

    // Active sessions and processing with enhanced tracking
    private final Map<UUID, CrateOpening> activeOpenings = new ConcurrentHashMap<>();
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<CrateType, CrateConfiguration> crateConfigurations = new HashMap<>();
    private final Map<UUID, Long> lastOpenTimes = new ConcurrentHashMap<>();

    // Enhanced statistics tracking
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

    // Enhanced settings
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
     * Enhanced initialization with configuration loading and validation
     */
    public void initialize() {
        try {
            logger.info("Initializing enhanced crate system...");

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

            logger.info("Enhanced crate system initialized successfully with " +
                    crateConfigurations.size() + " crate types and " +
                    getFeatureString());

        } catch (Exception e) {
            logger.severe("Failed to initialize enhanced crate system: " + e.getMessage());
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
            defaultConfig.set("animations.preparation-duration", 20);
            defaultConfig.set("animations.spinning-duration", 120);
            defaultConfig.set("animations.slowing-duration", 80);
            defaultConfig.set("animations.revealing-duration", 60);
            defaultConfig.set("animations.celebration-duration", 40);

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
     * Enhanced crate opening with modern features and validation
     */
    public boolean openCrate(Player player, ItemStack crateItem) {
        UUID playerId = player.getUniqueId();

        try {
            // Enhanced validation
            if (!validateCrateOpening(player, crateItem)) {
                return false;
            }

            // Check cooldown
            if (!checkOpeningCooldown(player)) {
                return false;
            }

            // Check concurrent openings limit
            if (activeOpenings.size() >= maxConcurrentOpenings) {
                player.sendMessage(ChatColor.RED + "The mystical energies are overwhelmed! Please try again in a moment.");
                return false;
            }

            // Determine crate type with enhanced validation
            CrateType crateType = crateFactory.determineCrateType(crateItem);
            if (crateType == null) {
                player.sendMessage(ChatColor.RED + "This crate's mystical signature is unreadable!");
                return false;
            }

            // Get configuration
            CrateConfiguration configuration = crateConfigurations.get(crateType);
            if (configuration == null) {
                logger.warning("No configuration found for crate type: " + crateType);
                sendErrorMessage(player, "Configuration Error",
                        "This crate type is not properly configured. Please contact an administrator.");
                return false;
            }

            // Final validation checks
            if (!performFinalValidation(player, crateType)) {
                return false;
            }

            // Create enhanced opening session
            CrateOpening opening = new CrateOpening(player, crateType, configuration);
            activeOpenings.put(playerId, opening);
            processingPlayers.add(playerId);

            // Update cooldown
            lastOpenTimes.put(playerId, System.currentTimeMillis());

            // Send opening message with modern formatting
            sendCrateOpeningMessage(player, crateType);

            // Start enhanced animation
            if (enableAnimations) {
                animationManager.startCrateOpening(opening);
            } else {
                // Immediate completion if animations disabled
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        completeCrateOpening(opening);
                    }
                }.runTaskLater(plugin, 20L); // 1 second delay
            }

            // Update statistics
            updateOpeningStatistics(crateType, playerId);

            logger.fine("Started enhanced crate opening for player " + player.getName() +
                    " with crate type " + crateType);
            return true;

        } catch (Exception e) {
            logger.severe("Error opening crate for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();

            // Enhanced cleanup on error
            cleanupFailedOpening(playerId, player);
            return false;
        }
    }

    /**
     * Enhanced validation for crate opening
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
            player.sendMessage(ChatColor.RED + "✦ " + ChatColor.BOLD + "You are already opening a crate!" +
                    ChatColor.RED + " ✦");
            playEnhancedErrorSound(player);
            return false;
        }

        // Enhanced inventory space check
        if (!hasInventorySpace(player, 3)) { // Check for at least 3 free slots
            sendInventoryFullMessage(player);
            return false;
        }

        // Check if crate is valid
        if (!crateFactory.isCrate(crateItem)) {
            player.sendMessage(ChatColor.RED + "This item is not a valid mystical crate!");
            playEnhancedErrorSound(player);
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
                player.sendMessage(ChatColor.YELLOW + "⏳ The mystical energies need " + remaining +
                        " more seconds to recharge...");
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
            player.sendMessage(ChatColor.RED + "Tier 6 content is currently disabled!");
            return false;
        }

        // Check if Halloween crates are allowed (seasonal check)
        if (crateType.isHalloween() && !isHalloweenSeason()) {
            player.sendMessage(ChatColor.GOLD + "🎃 Halloween crates can only be opened during spooky season! 🎃");
            return false;
        }

        return true;
    }

    /**
     * Sends enhanced crate opening message
     */
    private void sendCrateOpeningMessage(Player player, CrateType crateType) {
        // Main message
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.AQUA + "✦ " + ChatColor.BOLD + "MYSTICAL CRATE OPENING" + ChatColor.AQUA + " ✦"
        ));

        String crateName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName();
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GRAY + "Unsealing " + ChatColor.YELLOW + crateName + ChatColor.GRAY + " Crate"
        ));

        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GRAY + "Tier " + ChatColor.WHITE + crateType.getTier() +
                        ChatColor.GRAY + " • Session " + ChatColor.WHITE + YakRealms.getSessionID()
        ));
        player.sendMessage("");

        // Action bar message if enabled
        if (enableActionBar) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GOLD + "✨ Preparing mystical energies... ✨"));
        }

        // Play opening sound
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        }
    }

    /**
     * Enhanced completion method with modern features
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

            // Generate enhanced rewards
            List<ItemStack> rewards = rewardsManager.generateRewards(
                    opening.getCrateType(), opening.getConfiguration());

            if (rewards.isEmpty()) {
                logger.warning("No rewards generated for crate opening: " + opening.getCrateType());
                rewards.add(createFallbackReward(opening.getCrateType()));
            }

            // Give rewards to player with enhanced handling
            giveEnhancedRewards(player, rewards, opening.getCrateType());

            // Send enhanced completion message
            sendEnhancedCompletionMessage(player, opening, rewards);

            // Play completion effects
            playEnhancedCompletionEffects(player, opening);

            // Update player statistics
            updatePlayerStatistics(player, opening, rewards);

            // Save statistics if enabled
            if (enableStatistics && enableAutoSave) {
                saveStatistics();
            }

            logger.fine("Completed enhanced crate opening for player " + player.getName() +
                    " with " + rewards.size() + " rewards");

        } catch (Exception e) {
            logger.severe("Error completing crate opening for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();

            // Enhanced fallback handling
            handleCompletionError(player, opening);

        } finally {
            // Always clean up
            processingPlayers.remove(playerId);
            activeOpenings.remove(playerId);
            animationManager.cancelAnimation(playerId);
        }
    }

    /**
     * Enhanced reward giving with inventory management
     */
    private void giveEnhancedRewards(Player player, List<ItemStack> rewards, CrateType crateType) {
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

        // Notify player with enhanced message
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "INVENTORY OVERFLOW" + ChatColor.YELLOW + " ⚠");
        player.sendMessage(ChatColor.GRAY + "Some rewards were dropped nearby due to full inventory!");
        player.sendMessage(ChatColor.GRAY + "Items dropped: " + ChatColor.WHITE + overflow.size());

        // Action bar notification
        if (enableActionBar) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.YELLOW + "⚠ Some rewards dropped nearby - Inventory full! ⚠"));
        }

        // Sound notification
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        }
    }

    /**
     * Sends enhanced completion message with interactive elements
     */
    private void sendEnhancedCompletionMessage(Player player, CrateOpening opening, List<ItemStack> rewards) {
        // Header with enhanced formatting
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GOLD + "✧ " + ChatColor.BOLD + "MYSTICAL CRATE UNSEALED" + ChatColor.GOLD + " ✧"
        ));

        String crateName = (opening.getCrateType().isHalloween() ? "Halloween " : "") +
                opening.getCrateType().getDisplayName();
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GRAY + crateName + " Crate • Tier " + opening.getCrateType().getTier()
        ));

        // Rewards summary with enhanced formatting
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "✦ " + ChatColor.BOLD + "REWARDS RECEIVED:");

        int itemCount = 0;
        for (ItemStack reward : rewards) {
            if (reward != null) {
                itemCount++;
                String displayName = getItemDisplayName(reward);
                int amount = reward.getAmount();

                String line = ChatColor.WHITE + "  ◆ " + displayName;
                if (amount > 1) {
                    line += ChatColor.GRAY + " (×" + amount + ")";
                }
                player.sendMessage(line);

                // Limit display to prevent spam
                if (itemCount >= 8) {
                    int remaining = rewards.size() - itemCount;
                    if (remaining > 0) {
                        player.sendMessage(ChatColor.GRAY + "  ... and " + remaining + " more items!");
                    }
                    break;
                }
            }
        }

        // Enhanced footer with statistics
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Total rewards: " + ChatColor.WHITE + rewards.size() +
                ChatColor.GRAY + " • Opening time: " + ChatColor.WHITE +
                String.format("%.1f", opening.getTotalElapsedTime() / 1000.0) + "s");

        // Interactive element - clickable message for crate preview
        if (opening.getCrateType().getTier() >= 3) {
            TextComponent previewMessage = new TextComponent(ChatColor.AQUA + "» Click here to preview other crate types! «");
            previewMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crate preview"));
            previewMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.YELLOW + "Click to open crate preview GUI!").create()));

            player.sendMessage(TextUtil.getCenteredMessage(""));
            player.spigot().sendMessage(previewMessage);
        }

        player.sendMessage("");
    }

    /**
     * Gets display name for an item
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // Format material name
        String materialName = item.getType().name().toLowerCase().replace('_', ' ');
        return ChatColor.WHITE + capitalizeWords(materialName);
    }

    /**
     * Capitalizes words in a string
     */
    private String capitalizeWords(String str) {
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Plays enhanced completion effects
     */
    private void playEnhancedCompletionEffects(Player player, CrateOpening opening) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) return;

        CrateType crateType = opening.getCrateType();

        // Enhanced sound effects
        if (enableSounds) {
            Sound completionSound = switch (crateType.getTier()) {
                case 1, 2 -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                case 3, 4 -> Sound.ENTITY_PLAYER_LEVELUP;
                case 5, 6 -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
                default -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            };

            player.playSound(location, completionSound, 1.0f, 1.0f);

            // Additional layered sounds for higher tiers
            if (crateType.getTier() >= 5) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.5f);
                    }
                }.runTaskLater(plugin, 10L);
            }
        }

        // Enhanced particle effects
        if (enableParticles) {
            createTieredParticleEffects(world, location, crateType);
        }

        // Final action bar message
        if (enableActionBar) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.GOLD + "✧ Mystical crate successfully unsealed! ✧"));
                }
            }.runTaskLater(plugin, 20L);
        }
    }

    /**
     * Creates tiered particle effects for completion
     */
    private void createTieredParticleEffects(World world, Location center, CrateType crateType) {
        // Base celebration particles
        world.spawnParticle(Particle.VILLAGER_HAPPY, center.clone().add(0, 2, 0),
                20, 1.5, 1.5, 1.5, 0.1);

        // Tier-specific effects
        Particle tierParticle = switch (crateType.getTier()) {
            case 1, 2 -> Particle.ENCHANTMENT_TABLE;
            case 3, 4 -> Particle.PORTAL;
            case 5, 6 -> Particle.TOTEM;
            default -> Particle.VILLAGER_HAPPY;
        };

        world.spawnParticle(tierParticle, center.clone().add(0, 2, 0),
                15 + crateType.getTier() * 5, 1, 1, 1, 0.1);

        // Halloween special effects
        if (crateType.isHalloween()) {
            world.spawnParticle(Particle.FLAME, center.clone().add(0, 2, 0),
                    15, 1, 1, 1, 0.1);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 2.5, 0),
                    10, 0.8, 0.8, 0.8, 0.05);
        }

        // Epic effects for highest tiers
        if (crateType.getTier() >= 6) {
            // Create ascending spiral
            new BukkitRunnable() {
                double y = 0;
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks > 40) {
                        cancel();
                        return;
                    }

                    double angle = ticks * 0.3;
                    double radius = 1.0 + (ticks * 0.05);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    Location spiralLoc = center.clone().add(x, y, z);
                    world.spawnParticle(Particle.END_ROD, spiralLoc, 1, 0, 0, 0, 0);

                    y += 0.1;
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    /**
     * Handles completion errors with user-friendly fallbacks
     */
    private void handleCompletionError(Player player, CrateOpening opening) {
        // Give fallback reward
        ItemStack fallback = createFallbackReward(opening.getCrateType());
        player.getInventory().addItem(fallback);

        // Enhanced error message
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "MYSTICAL INTERFERENCE" + ChatColor.YELLOW + " ⚠");
        player.sendMessage(ChatColor.GRAY + "The cosmic energies were disrupted, but you received compensation!");
        player.sendMessage(ChatColor.GREEN + "✓ Emergency reward granted");

        // Play error sound
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        }
    }

    // Utility methods

    /**
     * Enhanced inventory space checking
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
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.BOLD + "INVENTORY OVERFLOW PROTECTION" + ChatColor.RED + " ⚠");
        player.sendMessage(ChatColor.GRAY + "Your inventory is too full to safely receive crate rewards!");
        player.sendMessage(ChatColor.YELLOW + "Please make at least 3 empty slots and try again.");
        player.sendMessage("");

        // Helpful suggestions
        TextComponent bankTip = new TextComponent(ChatColor.AQUA + "» Tip: Use /bank to store items! «");
        bankTip.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bank"));
        bankTip.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.YELLOW + "Click to open your bank!").create()));

        player.spigot().sendMessage(bankTip);

        playEnhancedErrorSound(player);
    }

    /**
     * Plays enhanced error sound
     */
    private void playEnhancedErrorSound(Player player) {
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }

    /**
     * Sends formatted error message
     */
    private void sendErrorMessage(Player player, String title, String message) {
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "✗ " + ChatColor.BOLD + title + ChatColor.RED + " ✗");
        player.sendMessage(ChatColor.GRAY + message);
        player.sendMessage("");

        playEnhancedErrorSound(player);
    }

    /**
     * Cleans up failed opening
     */
    private void cleanupFailedOpening(UUID playerId, Player player) {
        processingPlayers.remove(playerId);
        activeOpenings.remove(playerId);
        lastOpenTimes.remove(playerId);

        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.YELLOW + "✦ Crate opening has been safely cancelled. ✦");
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
                // This would integrate with your player data system
                // For now, just track in memory
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
     * Loads crate configurations with enhanced validation
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

        long animationDuration = config.getLong("animations.preparation-duration", 20) +
                config.getLong("animations.spinning-duration", 120) +
                config.getLong("animations.slowing-duration", 80) +
                config.getLong("animations.revealing-duration", 60) +
                config.getLong("animations.celebration-duration", 40);

        return new CrateConfiguration(crateType, displayName, crateType.getTier(),
                contents, completionSound, animationDuration * 50); // Convert ticks to milliseconds
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
        // Cleanup task - Enhanced
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performEnhancedCleanup();
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
     * Enhanced cleanup with better error handling
     */
    private void performEnhancedCleanup() {
        try {
            // Clean up expired openings
            long currentTime = System.currentTimeMillis();

            activeOpenings.entrySet().removeIf(entry -> {
                CrateOpening opening = entry.getValue();
                UUID playerId = entry.getKey();

                // Check if opening is timed out or player is offline
                if (opening.isTimedOut() || !plugin.getServer().getPlayer(playerId).isOnline()) {
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
            logger.warning("Error during enhanced crate cleanup: " + e.getMessage());
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
            broadcastMilestone("✦ " + total + " mystical crates have been opened server-wide! ✦");
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
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            onlinePlayer.sendMessage(ChatColor.GOLD + message);
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
                meta.setDisplayName(ChatColor.GREEN + "Emergency Compensation");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Compensation for tier " + crateType.getTier() + " crate",
                        ChatColor.GRAY + "Please contact an administrator"
                ));
                emerald.setItemMeta(meta);
            }
            return emerald;
        }
    }

    // Enhanced shutdown method

    /**
     * Enhanced shutdown with proper cleanup and data saving
     */
    public void shutdown() {
        try {
            logger.info("Shutting down enhanced crate system...");

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

            logger.info("Enhanced crate system shut down successfully");

        } catch (Exception e) {
            logger.warning("Error during enhanced crate system shutdown: " + e.getMessage());
        }
    }

    // Getters and accessors

    public CrateFactory getCrateFactory() { return crateFactory; }
    public CrateAnimationManager getAnimationManager() { return animationManager; }
    public CrateRewardsManager getRewardsManager() { return rewardsManager; }
    public Set<UUID> getProcessingPlayers() { return new HashSet<>(processingPlayers); }
    public Map<UUID, CrateOpening> getActiveOpenings() { return new HashMap<>(activeOpenings); }
    public CrateConfiguration getConfiguration(CrateType crateType) { return crateConfigurations.get(crateType); }

    // Enhanced API methods

    /**
     * Creates a crate with enhanced options
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
     * Enhanced method to give crates to players
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

        // Enhanced notification message
        String crateName = (isHalloween ? "Halloween " : "") + crateType.getDisplayName() + " Crate";
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✦ " + ChatColor.BOLD + "CRATES RECEIVED" + ChatColor.GREEN + " ✦");
        player.sendMessage(ChatColor.WHITE + "Received: " + ChatColor.YELLOW + amount + "× " + crateName);
        if (isLocked) {
            player.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.RED + "Locked" +
                    ChatColor.GRAY + " (requires crate key)");
        }
        if (!overflow.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Note: " + overflow.size() + " crates dropped due to full inventory");
        }
        player.sendMessage("");

        // Play sound
        if (enableSounds) {
            Sound giveSound = isLocked ? Sound.BLOCK_CHEST_LOCKED : Sound.ENTITY_ITEM_PICKUP;
            player.playSound(player.getLocation(), giveSound, 1.0f, 1.2f);
        }
    }

    /**
     * Enhanced statistics method
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Basic stats
        stats.put("totalCratesOpened", totalCratesOpened);
        stats.put("activeOpenings", activeOpenings.size());
        stats.put("processingPlayers", processingPlayers.size());
        stats.put("configurationsLoaded", crateConfigurations.size());

        // Enhanced stats
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

    // Additional utility methods would go here...
    // (saveStatistics, loadStatistics, saveConfiguration, etc.)
    // These methods handle file I/O and are implementation-specific

    private void saveStatistics() {
        // Implementation for saving statistics to file
        if (enableAutoSave) {
            // Save to YAML file
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