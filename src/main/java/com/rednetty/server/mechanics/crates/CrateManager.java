package com.rednetty.server.mechanics.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.CrateFactory;
import com.rednetty.server.mechanics.crates.types.CrateKey;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Central manager for the crate system - coordinates all crate-related functionality
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

    // Active sessions and processing
    private final Map<UUID, CrateOpening> activeOpenings = new ConcurrentHashMap<>();
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<CrateType, CrateConfiguration> crateConfigurations = new HashMap<>();

    // Statistics tracking
    private final Map<CrateType, Integer> cratesOpened = new ConcurrentHashMap<>();
    private final Map<CrateType, Integer> cratesGenerated = new ConcurrentHashMap<>();
    private long totalCratesOpened = 0;

    // Cleanup task
    private BukkitTask cleanupTask;

    /**
     * Private constructor for singleton pattern
     */
    private CrateManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The CrateManager instance
     */
    public static synchronized CrateManager getInstance() {
        if (instance == null) {
            instance = new CrateManager();
        }
        return instance;
    }

    /**
     * Initializes the crate system
     */
    public void initialize() {
        try {
            // Initialize core components
            this.crateFactory = new CrateFactory();
            this.animationManager = new CrateAnimationManager();
            this.rewardsManager = new CrateRewardsManager();
            this.crateHandler = new CrateHandler();
            this.economyManager = EconomyManager.getInstance();
            this.playerManager = YakPlayerManager.getInstance();

            // Load configurations
            loadCrateConfigurations();

            // Register event handler
            plugin.getServer().getPluginManager().registerEvents(crateHandler, plugin);

            // Start cleanup task
            startCleanupTask();

            logger.info("Crate system initialized successfully with " + crateConfigurations.size() + " crate types");

        } catch (Exception e) {
            logger.severe("Failed to initialize crate system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Shuts down the crate system
     */
    public void shutdown() {
        try {
            // Cancel cleanup task
            if (cleanupTask != null) {
                cleanupTask.cancel();
            }

            // Complete any active openings
            for (CrateOpening opening : activeOpenings.values()) {
                if (!opening.isCompleted()) {
                    completeCrateOpening(opening);
                }
            }

            // Clean up animation manager
            if (animationManager != null) {
                animationManager.cleanup();
            }

            // Clear collections
            activeOpenings.clear();
            processingPlayers.clear();
            crateConfigurations.clear();

            logger.info("Crate system shut down successfully");

        } catch (Exception e) {
            logger.warning("Error during crate system shutdown: " + e.getMessage());
        }
    }

    /**
     * Loads crate configurations
     */
    private void loadCrateConfigurations() {
        // Create default configurations for each crate type
        for (CrateType crateType : CrateType.values()) {
            CrateConfiguration config = createDefaultConfiguration(crateType);
            crateConfigurations.put(crateType, config);
        }

        logger.info("Loaded " + crateConfigurations.size() + " crate configurations");
    }

    /**
     * Creates a default configuration for a crate type
     */
    private CrateConfiguration createDefaultConfiguration(CrateType crateType) {
        String displayName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName() + " Loot Crate";

        List<String> contents = Arrays.asList(
                "Tier " + crateType.getTier() + " Equipment",
                "Enhancement Scrolls",
                "Gems and Orbs"
        );

        Sound completionSound = switch (crateType.getTier()) {
            case 1, 2 -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3, 4 -> Sound.ENTITY_PLAYER_LEVELUP;
            case 5, 6 -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            default -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        };

        long animationDuration = crateType.getTier() * 1000L + 5000L; // 6-11 seconds based on tier

        return new CrateConfiguration(crateType, displayName, crateType.getTier(),
                contents, completionSound, animationDuration);
    }

    /**
     * Attempts to open a crate
     *
     * @param player The player opening the crate
     * @param crateItem The crate item being opened
     * @return true if the opening was successful
     */
    public boolean openCrate(Player player, ItemStack crateItem) {
        UUID playerId = player.getUniqueId();

        try {
            // Validate parameters
            if (player == null || crateItem == null) {
                logger.warning("Invalid parameters for crate opening");
                return false;
            }

            // Check if player is already processing a crate
            if (processingPlayers.contains(playerId) || activeOpenings.containsKey(playerId)) {
                player.sendMessage(ChatColor.RED + "You are already opening a crate!");
                return false;
            }

            // Determine crate type
            CrateType crateType = crateFactory.determineCrateType(crateItem);
            if (crateType == null) {
                player.sendMessage(ChatColor.RED + "Invalid crate type!");
                return false;
            }

            // Get configuration
            CrateConfiguration configuration = crateConfigurations.get(crateType);
            if (configuration == null) {
                logger.warning("No configuration found for crate type: " + crateType);
                return false;
            }

            // Check inventory space
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(ChatColor.RED + "Your inventory is full! Please make space before opening crates.");
                return false;
            }

            // Create opening session
            CrateOpening opening = new CrateOpening(player, crateType, configuration);
            activeOpenings.put(playerId, opening);
            processingPlayers.add(playerId);

            // Start animation
            animationManager.startCrateOpening(opening);

            // Update statistics
            cratesOpened.merge(crateType, 1, Integer::sum);
            totalCratesOpened++;


            logger.fine("Started crate opening for player " + player.getName() + " with crate type " + crateType);
            return true;

        } catch (Exception e) {
            logger.severe("Error opening crate for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();

            // Cleanup on error
            processingPlayers.remove(playerId);
            activeOpenings.remove(playerId);
            return false;
        }
    }

    /**
     * Completes a crate opening and gives rewards
     *
     * @param opening The crate opening to complete
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

            // Generate and give rewards
            List<ItemStack> rewards = rewardsManager.generateRewards(opening.getCrateType(), opening.getConfiguration());

            if (rewards.isEmpty()) {
                logger.warning("No rewards generated for crate opening: " + opening.getCrateType());
                rewards.add(createFallbackReward(opening.getCrateType()));
            }

            // Give rewards to player
            giveRewardsToPlayer(player, rewards);

            // Send completion message
            sendCompletionMessage(player, opening, rewards);

            // Play completion effects
            playCompletionEffects(player, opening);

            logger.fine("Completed crate opening for player " + player.getName() +
                    " with " + rewards.size() + " rewards");

        } catch (Exception e) {
            logger.severe("Error completing crate opening for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();

            // Give fallback reward on error
            ItemStack fallback = createFallbackReward(opening.getCrateType());
            player.getInventory().addItem(fallback);
            player.sendMessage(ChatColor.YELLOW + "An error occurred, but you received a compensation reward!");

        } finally {
            // Always clean up
            processingPlayers.remove(playerId);
            activeOpenings.remove(playerId);
            animationManager.cancelAnimation(playerId);
        }
    }

    /**
     * Gives rewards to a player
     */
    private void giveRewardsToPlayer(Player player, List<ItemStack> rewards) {
        for (ItemStack reward : rewards) {
            if (reward == null) continue;

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);

            // Drop any items that don't fit
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
    }

    /**
     * Sends completion message to player
     */
    private void sendCompletionMessage(Player player, CrateOpening opening, List<ItemStack> rewards) {
        // Header
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "CRATE OPENED" + ChatColor.GOLD + " ✦"
        ));

        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GRAY + opening.getConfiguration().getDisplayName()
        ));

        // Rewards summary
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Rewards received:");

        for (ItemStack reward : rewards) {
            if (reward != null && reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()) {
                String name = reward.getItemMeta().getDisplayName();
                int amount = reward.getAmount();
                String line = ChatColor.WHITE + "• " + name;
                if (amount > 1) {
                    line += ChatColor.GRAY + " (x" + amount + ")";
                }
                player.sendMessage(line);
            }
        }

        player.sendMessage("");
    }

    /**
     * Plays completion effects
     */
    private void playCompletionEffects(Player player, CrateOpening opening) {
        Location location = player.getLocation();
        Sound sound = opening.getConfiguration().getCompletionSound();

        // Play sound
        player.playSound(location, sound, 1.0f, 1.0f);

        // Particle effects based on tier
        CrateType crateType = opening.getCrateType();
        switch (crateType.getTier()) {
            case 1, 2:
                location.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, location.add(0, 2, 0),
                        15, 1, 1, 1, 0.1);
                break;
            case 3, 4:
                location.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, location.add(0, 2, 0),
                        25, 1, 1, 1, 0.1);
                break;
            case 5, 6:
                location.getWorld().spawnParticle(Particle.DRAGON_BREATH, location.add(0, 2, 0),
                        35, 1, 2, 1, 0.1);
                break;
        }

        // Halloween effects
        if (crateType.isHalloween()) {
            location.getWorld().spawnParticle(Particle.FLAME, location.add(0, 2, 0),
                    20, 1, 1, 1, 0.1);
        }
    }

    /**
     * Creates a fallback reward
     */
    private ItemStack createFallbackReward(CrateType crateType) {
        int gemAmount = crateType.getTier() * 100;
        return plugin.getBankManager().createBankNote(gemAmount);
    }

    /**
     * Creates a crate of the specified type
     *
     * @param crateType The type of crate to create
     * @param isHalloween Whether this is a Halloween variant
     * @return The created crate item
     */
    public ItemStack createCrate(CrateType crateType, boolean isHalloween) {
        ItemStack crate = crateFactory.createCrate(crateType, isHalloween);
        if (crate != null) {
            cratesGenerated.merge(crateType, 1, Integer::sum);
        }
        return crate;
    }

    /**
     * Creates a crate key
     *
     * @return The created crate key
     */
    public ItemStack createCrateKey() {
        return crateFactory.createCrateKey();
    }

    /**
     * Creates a locked crate
     *
     * @param crateType The type of crate
     * @param isHalloween Whether it's Halloween variant
     * @return The locked crate item
     */
    public ItemStack createLockedCrate(CrateType crateType, boolean isHalloween) {
        ItemStack crate = crateFactory.createLockedCrate(crateType, isHalloween);
        if (crate != null) {
            cratesGenerated.merge(crateType, 1, Integer::sum);
        }
        return crate;
    }

    /**
     * Gives crates to a player
     *
     * @param player The player to give crates to
     * @param crateType The type of crate
     * @param amount The amount to give
     * @param isHalloween Whether Halloween variant
     * @param isLocked Whether the crates should be locked
     */
    public void giveCratesToPlayer(Player player, CrateType crateType, int amount, boolean isHalloween, boolean isLocked) {
        if (amount <= 0) return;

        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);

            ItemStack crates = isLocked ?
                    createLockedCrate(crateType, isHalloween) :
                    createCrate(crateType, isHalloween);

            if (crates != null) {
                crates.setAmount(stackSize);

                Map<Integer, ItemStack> leftover = player.getInventory().addItem(crates);

                // Drop any items that don't fit
                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
            }

            remaining -= stackSize;
        }

        // Send message
        String crateName = (isHalloween ? "Halloween " : "") + crateType.getDisplayName() + " Crate";
        player.sendMessage(ChatColor.GREEN + "You received " + amount + "x " + crateName +
                (isLocked ? " (Locked)" : "") + "!");
    }

    /**
     * Starts the cleanup task
     */
    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredOpenings();
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L); // Every minute
    }

    /**
     * Cleans up expired or invalid openings
     */
    private void cleanupExpiredOpenings() {
        try {
            activeOpenings.entrySet().removeIf(entry -> {
                CrateOpening opening = entry.getValue();
                UUID playerId = entry.getKey();

                // Check if opening is timed out or player is offline
                if (opening.isTimedOut() || !plugin.getServer().getPlayer(playerId).isOnline()) {
                    // Force completion
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            completeCrateOpening(opening);
                        }
                    }.runTask(plugin);
                    return true;
                }

                return false;
            });

            // Clean up processing players who aren't in active openings
            processingPlayers.removeIf(playerId -> !activeOpenings.containsKey(playerId));

        } catch (Exception e) {
            logger.warning("Error during crate cleanup: " + e.getMessage());
        }
    }

    // Getters for other components

    public CrateFactory getCrateFactory() {
        return crateFactory;
    }

    public CrateAnimationManager getAnimationManager() {
        return animationManager;
    }

    public CrateRewardsManager getRewardsManager() {
        return rewardsManager;
    }

    public Set<UUID> getProcessingPlayers() {
        return new HashSet<>(processingPlayers);
    }

    public Map<UUID, CrateOpening> getActiveOpenings() {
        return new HashMap<>(activeOpenings);
    }

    public CrateConfiguration getConfiguration(CrateType crateType) {
        return crateConfigurations.get(crateType);
    }

    /**
     * Gets statistics for the crate system
     *
     * @return Map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCratesOpened", totalCratesOpened);
        stats.put("activeOpenings", activeOpenings.size());
        stats.put("processingPlayers", processingPlayers.size());
        stats.put("crateTypesOpened", new HashMap<>(cratesOpened));
        stats.put("crateTypesGenerated", new HashMap<>(cratesGenerated));
        stats.put("configurationsLoaded", crateConfigurations.size());
        return stats;
    }

    /**
     * Reloads crate configurations
     */
    public void reloadConfigurations() {
        crateConfigurations.clear();
        loadCrateConfigurations();
        logger.info("Reloaded crate configurations");
    }

    /**
     * Checks if a player can open a crate
     *
     * @param player The player to check
     * @return true if the player can open a crate
     */
    public boolean canPlayerOpenCrate(Player player) {
        UUID playerId = player.getUniqueId();
        return !processingPlayers.contains(playerId) &&
                !activeOpenings.containsKey(playerId) &&
                player.getInventory().firstEmpty() != -1;
    }

    /**
     * Forces completion of all active openings (for shutdown)
     */
    public void forceCompleteAllOpenings() {
        List<CrateOpening> openingsList = new ArrayList<>(activeOpenings.values());
        for (CrateOpening opening : openingsList) {
            completeCrateOpening(opening);
        }
    }

    /**
     * Creates a random crate for events/rewards
     *
     * @param minTier Minimum tier
     * @param maxTier Maximum tier
     * @param allowHalloween Whether to allow Halloween variants
     * @return Random crate item
     */
    public ItemStack createRandomCrate(int minTier, int maxTier, boolean allowHalloween) {
        minTier = Math.max(1, Math.min(6, minTier));
        maxTier = Math.max(minTier, Math.min(6, maxTier));

        int tier = ThreadLocalRandom.current().nextInt(minTier, maxTier + 1);
        boolean isHalloween = allowHalloween && ThreadLocalRandom.current().nextBoolean();

        CrateType crateType = CrateType.getByTier(tier, isHalloween);
        return crateType != null ? createCrate(crateType, isHalloween) : null;
    }
}