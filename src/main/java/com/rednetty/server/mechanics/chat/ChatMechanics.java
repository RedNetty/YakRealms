package com.rednetty.server.mechanics.chat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *  ChatMechanics system for YakRealms
 *
 * Handles all chat-related functionality including:
 * - Advanced chat formatting and display
 * - Chat tags management with animations
 * - Multi-layer chat filtering and moderation
 * - Interactive item display in chat with hover effects
 * - Intelligent cooldown management
 * - Guild tag integration with permissions
 * - Real-time mute system with persistence
 *
 * @version 2.0
 * @author YakRealms Development Team
 */
public class ChatMechanics implements Listener {

    // === CONSTANTS ===
    private static final class Config {
        static final int DEFAULT_CHAT_RANGE = 50;
        static final int COOLDOWN_TICKS = 20; // 1 second
        static final int MUTE_TIMER_INTERVAL = 20; // 1 second
        static final String MUTED_FILE = "muted.yml";
        static final String MUTED_CONFIG_PATH = "muted";
    }

    private static final class Messages {
        static final String MUTED_MESSAGE = ChatColor.RED + "You are currently muted";
        static final String MUTED_TEMPORARY = ChatColor.RED + "Your mute expires in %d minutes.";
        static final String MUTED_PERMANENT = ChatColor.RED + "Your mute WILL NOT expire.";
        static final String COOLDOWN_MESSAGE = ChatColor.RED + "Please wait before chatting again.";
        static final String NO_LISTENERS = ChatColor.GRAY.toString() + ChatColor.ITALIC + "No one heard you.";
        static final String UNMUTED_MESSAGE = ChatColor.GREEN + "You have been unmuted.";
        static final String UNKNOWN_COMMAND = ChatColor.WHITE + "Unknown command. View your Character Journal's Index for a list of commands.";
        static final String HOLD_ITEM_MESSAGE = ChatColor.GRAY + "Hold an item in your main hand to display it in chat.";
    }

    private static final class Sounds {
        static final Sound CLICK = Sound.UI_BUTTON_CLICK;
        static final Sound ERROR = Sound.ENTITY_VILLAGER_NO;
        static final float DEFAULT_VOLUME = 0.5f;
        static final float DEFAULT_PITCH = 1.0f;
    }

    // === INSTANCE MANAGEMENT ===
    private static volatile ChatMechanics instance;
    private static final Object INSTANCE_LOCK = new Object();

    // === CORE DEPENDENCIES ===
    private final YakPlayerManager playerManager;
    private final Logger logger;
    private final AtomicBoolean isEnabled = new AtomicBoolean(false);

    // === THREAD-SAFE DATA STORAGE ===
    private static final Map<UUID, ChatTag> playerTags = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> unlockedPlayerTags = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> chatCooldown = new ConcurrentHashMap<>();
    private static final Map<UUID, Player> replyTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> mutedPlayers = new ConcurrentHashMap<>();

    // === TASK MANAGEMENT ===
    private BukkitTask cooldownTask;
    private BukkitTask muteTimerTask;

    // === FILTER PATTERNS ===
    private static final List<String> BANNED_WORDS = Collections.synchronizedList(
            new ArrayList<>(Arrays.asList("nigger")) // Add other banned words here
    );
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"
    );
    private static final Set<String> RESTRICTED_COMMANDS = Set.of(
            "save-all", "stack", "stop", "restart", "reload", "tpall", "kill", "mute"
    );

    /**
     * Gets the singleton instance with double-checked locking for thread safety
     */
    public static ChatMechanics getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new ChatMechanics();
                }
            }
        }
        return instance;
    }

    /**
     * Private constructor implementing singleton pattern
     */
    private ChatMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }

    // === LIFECYCLE MANAGEMENT ===

    /**
     *  initialization with better error recovery
     */
    public void onEnable() {
        if (isEnabled.get()) {
            logger.warning("ChatMechanics is already enabled");
            return;
        }

        try {
            // Register event listeners
            registerEventListeners();

            // Initialize background tasks
            initializeBackgroundTasks();

            // Load persistent data
            loadPersistentData();

            // Load chat tags for online players
            loadOnlinePlayerData();

            isEnabled.set(true);
            logger.info("ChatMechanics v2.0 enabled successfully");
            logger.info("Chat system compatibility: " + getCompatibilityInfo());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error enabling ChatMechanics", e);
            // Attempt graceful fallback
            isEnabled.set(false);
            throw new RuntimeException("Failed to enable ChatMechanics", e);
        }
    }

    /**
     *  cleanup with better resource management
     */
    public void onDisable() {
        if (!isEnabled.get()) {
            return;
        }

        try {
            // Stop background tasks
            stopBackgroundTasks();

            // Save all persistent data
            savePersistentData();

            // Clear memory caches
            clearCaches();

            isEnabled.set(false);
            logger.info("ChatMechanics disabled successfully");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during ChatMechanics shutdown", e);
        }
    }

    // === INITIALIZATION HELPERS ===

    private void registerEventListeners() {
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
    }

    private void initializeBackgroundTasks() {
        startCooldownTask();
        startMuteTimerTask();
    }

    private void loadPersistentData() {
        loadMutedPlayers();
    }

    private void loadOnlinePlayerData() {
        Bukkit.getOnlinePlayers().forEach(this::loadPlayerChatTag);
    }

    private void stopBackgroundTasks() {
        Optional.ofNullable(cooldownTask)
                .filter(task -> !task.isCancelled())
                .ifPresent(BukkitTask::cancel);

        Optional.ofNullable(muteTimerTask)
                .filter(task -> !task.isCancelled())
                .ifPresent(BukkitTask::cancel);
    }

    private void savePersistentData() {
        saveAllChatTags();
        saveMutedPlayers();
    }

    private void clearCaches() {
        chatCooldown.clear();
        replyTargets.clear();
        // Note: Keep muted players and tags for persistence
    }

    // === BACKGROUND TASKS ===

    /**
     *   cooldown management with proper ConcurrentHashMap handling
     */
    private void startCooldownTask() {
        cooldownTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Use iterator to safely modify the map during iteration
                    Iterator<Map.Entry<UUID, Integer>> iterator = chatCooldown.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<UUID, Integer> entry = iterator.next();
                        int newValue = entry.getValue() - 1;

                        if (newValue <= 0) {
                            iterator.remove(); // Remove expired cooldowns
                        } else {
                            // Update the value in the map directly
                            chatCooldown.put(entry.getKey(), newValue);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in cooldown task", e);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 1, 1);
    }

    /**
     *   mute timer with proper ConcurrentHashMap handling
     */
    private void startMuteTimerTask() {
        muteTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Iterator<Map.Entry<UUID, Integer>> iterator = mutedPlayers.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<UUID, Integer> entry = iterator.next();
                        UUID uuid = entry.getKey();
                        int muteTime = entry.getValue();

                        // Skip permanent mutes (value <= 0)
                        if (muteTime <= 0) {
                            continue;
                        }

                        int newTime = muteTime - 1;
                        if (newTime <= 0) {
                            // Mute expired
                            updatePlayerMuteStatus(uuid, 0);
                            iterator.remove(); // Remove from map
                        } else {
                            // Update the mute time
                            mutedPlayers.put(uuid, newTime);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in mute timer task", e);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), Config.MUTE_TIMER_INTERVAL, Config.MUTE_TIMER_INTERVAL);
    }

    /**
     * Helper method to update player mute status in data
     */
    private void updatePlayerMuteStatus(UUID uuid, int muteTime) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setMuteTime(muteTime);
                playerManager.savePlayer(yakPlayer);
            }
        }
    }

    // === PERSISTENCE MANAGEMENT ===

    /**
     *  muted players loading with better error handling
     */
    private void loadMutedPlayers() {
        File file = new File(YakRealms.getInstance().getDataFolder(), Config.MUTED_FILE);

        if (!ensureFileExists(file)) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            var mutedSection = config.getConfigurationSection(Config.MUTED_CONFIG_PATH);

            if (mutedSection == null) {
                return;
            }

            for (String key : mutedSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int time = config.getInt(Config.MUTED_CONFIG_PATH + "." + key);
                    mutedPlayers.put(uuid, time);

                    // Update online player data
                    updatePlayerMuteStatus(uuid, time);

                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID in muted.yml: " + key);
                }
            }

            logger.info("Loaded " + mutedPlayers.size() + " muted players");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading muted players data", e);
        }
    }

    /**
     *  muted players saving with atomic operations
     */
    private void saveMutedPlayers() {
        File file = new File(YakRealms.getInstance().getDataFolder(), Config.MUTED_FILE);

        try {
            YamlConfiguration config = new YamlConfiguration();

            // Create a snapshot to avoid concurrent modification
            Map<UUID, Integer> snapshot = new HashMap<>(mutedPlayers);

            for (Map.Entry<UUID, Integer> entry : snapshot.entrySet()) {
                config.set(Config.MUTED_CONFIG_PATH + "." + entry.getKey().toString(), entry.getValue());
            }

            config.save(file);
            logger.fine("Saved " + snapshot.size() + " muted players");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error saving muted players data", e);
        }
    }

    /**
     *  chat tags saving with batch operations
     */
    private void saveAllChatTags() {
        try {
            int saved = 0;
            for (Map.Entry<UUID, ChatTag> entry : playerTags.entrySet()) {
                YakPlayer player = playerManager.getPlayer(entry.getKey());
                if (player != null) {
                    player.setChatTag(entry.getValue().name());
                    playerManager.savePlayer(player);
                    saved++;
                }
            }
            logger.fine("Saved " + saved + " chat tags");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error saving chat tags", e);
        }
    }

    // === EVENT HANDLERS ===

    /**
     *  player join handling with better error recovery
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();

            // Load chat data asynchronously to avoid blocking
            Bukkit.getScheduler().runTaskAsynchronously(YakRealms.getInstance(), () -> {
                loadPlayerChatTag(player);
                loadPlayerMuteStatus(player);
            });

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling player join for " + event.getPlayer().getName(), e);
        }
    }

    /**
     *  player quit handling with cleanup
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        try {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            // Save player data asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(YakRealms.getInstance(), () -> {
                savePlayerChatTag(player);
            });

            // Clean up runtime data
            chatCooldown.remove(uuid);
            replyTargets.remove(uuid);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling player quit for " + event.getPlayer().getName(), e);
        }
    }

    /**
     *  command preprocessing with better validation
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }

        try {
            Player player = event.getPlayer();
            String command = event.getMessage().toLowerCase();

            // Handle chat messages with // prefix
            if (handleChatPrefix(player, event, command)) {
                return;
            }

            // Handle restricted commands
            if (handleRestrictedCommands(player, event, command)) {
                return;
            }

            // Handle guild commands without permissions
            handleGuildCommands(player, event, command);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing command for " + event.getPlayer().getName(), e);
        }
    }

    /**
     *  chat event handling with async processing
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        try {
            Player player = event.getPlayer();
            String message = event.getMessage();

            // Cancel default behavior
            event.setCancelled(true);

            // Process synchronously to avoid thread issues
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                processChat(player, message);
            });

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling chat for " + event.getPlayer().getName(), e);
        }
    }

    // === COMMAND HANDLING HELPERS ===

    private boolean handleChatPrefix(Player player, PlayerCommandPreprocessEvent event, String command) {
        if (command.startsWith("//")) {
            event.setCancelled(true);
            String message = event.getMessage().substring(2).trim();
            if (!message.isEmpty()) {
                processChat(player, message);
            }
            return true;
        }
        return false;
    }

    private boolean handleRestrictedCommands(Player player, PlayerCommandPreprocessEvent event, String command) {
        if (player.isOp() || ModerationMechanics.isStaff(player)) {
            return false;
        }

        String cmdName = extractCommandName(command);
        if (RESTRICTED_COMMANDS.contains(cmdName)) {
            event.setCancelled(true);
            player.sendMessage(Messages.UNKNOWN_COMMAND);
            return true;
        }
        return false;
    }

    private void handleGuildCommands(Player player, PlayerCommandPreprocessEvent event, String command) {
        if ((command.startsWith("/g ")) &&
                !player.hasPermission("yakrealms.guild.chat")) {
            event.setCancelled(true);
            String message = command.substring(command.indexOf(' ') + 1).trim();
            if (!message.isEmpty()) {
                processChat(player, message);
            }
        }
    }

    private String extractCommandName(String command) {
        String cmdName = command.startsWith("/") ? command.substring(1) : command;
        if (cmdName.contains(" ")) {
            cmdName = cmdName.split(" ")[0];
        }
        return cmdName;
    }

    // === CHAT PROCESSING ===

    /**
     *  chat processing with comprehensive validation and features
     */
    public void processChat(Player player, String message) {
        if (!validateChatInput(player, message)) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();

            // Check mute status using YakPlayer
            if (isPlayerMuted(player)) {
                return;
            }

            // Check and apply cooldown
            if (hasChatCooldown(uuid)) {
                player.sendMessage(Messages.COOLDOWN_MESSAGE);
                return;
            }

            applyChatCooldown(uuid);

            // Filter and process message
            String filteredMessage = filterMessage(message);

            // Route message based on content
            if (containsItemPlaceholder(filteredMessage) && isHoldingValidItem(player)) {
                sendItemMessage(player, filteredMessage);
            } else {
                sendNormalMessage(player, filteredMessage);
            }

            // Log chat activity
            logChatMessage(player, filteredMessage);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing chat for " + player.getName(), e);
            // Fallback to basic message
            sendBasicFallbackMessage(player, message);
        }
    }

    // === VALIDATION HELPERS ===

    private boolean validateChatInput(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        return true;
    }

    private boolean isPlayerMuted(Player player) {
        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null && yakPlayer.isMuted()) {
                int muteTime = yakPlayer.getMuteTime();
                player.sendMessage(Messages.MUTED_MESSAGE);

                if (muteTime > 0) {
                    int minutes = muteTime / 60;
                    player.sendMessage(String.format(Messages.MUTED_TEMPORARY, minutes));
                } else {
                    player.sendMessage(Messages.MUTED_PERMANENT);
                }
                return true;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking mute status for " + player.getName(), e);
        }
        return false;
    }

    private boolean hasChatCooldown(UUID uuid) {
        return chatCooldown.containsKey(uuid);
    }

    private void applyChatCooldown(UUID uuid) {
        chatCooldown.put(uuid, Config.COOLDOWN_TICKS);
    }

    private boolean containsItemPlaceholder(String message) {
        return message.contains("@i@");
    }

    // === MESSAGE PROCESSING ===

    /**
     *  normal message sending with improved recipient handling
     */
    private void sendNormalMessage(Player player, String message) {
        try {
            String formattedName = getFormattedPlayerName(player);
            String fullMessage = formattedName + ChatColor.WHITE + ": " + message;

            // Send to sender
            player.sendMessage(fullMessage);

            // Get and send to nearby players
            List<Player> recipients = getNearbyPlayers(player);

            if (recipients.isEmpty()) {
                player.sendMessage(Messages.NO_LISTENERS);
            } else {
                sendToRecipients(player, recipients, message, formattedName);
            }

            // Send to vanished staff
            sendToVanishedStaff(player, formattedName, message, false);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending normal message", e);
        }
    }

    /**
     *  item message with improved error handling and fallbacks
     */
    private void sendItemMessage(Player player, String message) {
        try {
            ItemStack item = player.getInventory().getItemInMainHand();

            if (!isHoldingValidItem(player)) {
                handleNoItemFallback(player, message);
                return;
            }

            String formattedName = getFormattedPlayerName(player);

            // Try modern chat component approach
            if (supportsModernChat()) {
                sendModernItemMessage(player, item, formattedName, message);
            } else {
                sendFallbackItemMessage(player, message);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending item message for " + player.getName(), e);
            handleItemMessageError(player, message);
        }
    }

    private void handleNoItemFallback(Player player, String message) {
        String fallbackMessage = message.replace("@i@", ChatColor.RED + "[No Item]" + ChatColor.WHITE);
        sendNormalMessage(player, fallbackMessage);
        player.sendMessage(Messages.HOLD_ITEM_MESSAGE);
    }

    private void sendModernItemMessage(Player player, ItemStack item, String formattedName, String message) {
        // Send to sender with item hover
        boolean selfSuccess = sendItemHoverMessage(player, item, formattedName, message, player);
        if (!selfSuccess) {
            sendFallbackItemMessage(player, message);
            return;
        }

        // Send to nearby players
        List<Player> recipients = getNearbyPlayers(player);
        if (recipients.isEmpty()) {
            player.sendMessage(Messages.NO_LISTENERS);
        } else {
            sendItemToRecipients(player, item, recipients, message, formattedName);
        }

        // Send to vanished staff
        sendToVanishedStaff(player, formattedName, message, true);
    }

    private void sendToRecipients(Player sender, List<Player> recipients, String message, String senderName) {
        for (Player recipient : recipients) {
            if (recipient != null && recipient.isOnline()) {
                try {
                    String recipientSpecificName = getFormattedNameFor(sender, recipient);
                    recipient.sendMessage(recipientSpecificName + ChatColor.WHITE + ": " + message);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error sending message to " + recipient.getName(), e);
                }
            }
        }
    }

    private void sendItemToRecipients(Player sender, ItemStack item, List<Player> recipients,
                                      String message, String senderName) {
        int successCount = 0;

        for (Player recipient : recipients) {
            if (recipient != null && recipient.isOnline()) {
                try {
                    String recipientSpecificName = getFormattedNameFor(sender, recipient);
                    boolean success = sendItemHoverMessage(sender, item, recipientSpecificName, message, recipient);

                    if (success) {
                        successCount++;
                    } else {
                        // Fallback to normal message with item name
                        sendItemFallbackToRecipient(recipient, item, recipientSpecificName, message);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error sending item message to " + recipient.getName(), e);
                    sendItemFallbackToRecipient(recipient, item, senderName, message);
                }
            }
        }

        logger.fine("Successfully sent item message to " + successCount + "/" + recipients.size() + " recipients");
    }

    private void sendItemFallbackToRecipient(Player recipient, ItemStack item, String senderName, String message) {
        String itemName = getItemDisplayName(item);
        String itemDisplay = formatItemDisplay(itemName);
        String fallbackMessage = message.replace("@i@", itemDisplay);
        recipient.sendMessage(senderName + ChatColor.WHITE + ": " + fallbackMessage);
    }

    // === NEARBY PLAYER DETECTION ===

    /**
     *  nearby player detection with better filtering
     */
    private List<Player> getNearbyPlayers(Player player) {
        try {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(other -> isPlayerInRange(player, other))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting nearby players", e);
            return new ArrayList<>();
        }
    }

    private boolean isPlayerInRange(Player center, Player target) {
        if (target == null || !target.isOnline() || target.equals(center)) {
            return false;
        }

        if (!target.getWorld().equals(center.getWorld())) {
            return false;
        }

        if (isVanished(target)) {
            return false;
        }

        double distance = target.getLocation().distance(center.getLocation());
        return distance <= Config.DEFAULT_CHAT_RANGE;
    }

    private boolean isVanished(Player player) {
        try {
            return player.hasMetadata("vanished");
        } catch (Exception e) {
            return false;
        }
    }

    // === VANISHED STAFF MESSAGING ===

    /**
     *  vanished staff messaging with item support
     */
    private void sendToVanishedStaff(Player sender, String senderName, String message, boolean hasItem) {
        try {
            Bukkit.getOnlinePlayers().stream()
                    .filter(this::isVanishedStaff)
                    .filter(staff -> !staff.equals(sender))
                    .forEach(staff -> sendToStaffMember(sender, staff, senderName, message, hasItem));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending to vanished staff", e);
        }
    }

    private boolean isVanishedStaff(Player player) {
        return player != null &&
                player.isOnline() &&
                player.hasPermission("yakrealms.vanish") &&
                player.hasMetadata("vanished");
    }

    private void sendToStaffMember(Player sender, Player staff, String senderName, String message, boolean hasItem) {
        try {
            if (hasItem && isHoldingValidItem(sender)) {
                ItemStack item = sender.getInventory().getItemInMainHand();
                if (supportsModernChat()) {
                    sendItemHoverMessage(sender, item, senderName, message, staff);
                } else {
                    sendItemFallbackToRecipient(staff, item, senderName, message);
                }
            } else {
                staff.sendMessage(senderName + ChatColor.WHITE + ": " + message);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending to staff member " + staff.getName(), e);
        }
    }

    // === PLAYER DATA MANAGEMENT ===

    /**
     *  player data loading using YakPlayer system
     */
    private void loadPlayerChatTag(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            if (yakPlayer != null) {
                // Load chat tag
                loadChatTag(uuid, yakPlayer);

                // Load unlocked tags
                loadUnlockedTags(uuid, yakPlayer);
            } else {
                // Set defaults for new player
                setDefaultPlayerTags(uuid);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error loading chat tag for " + player.getName(), e);
            setDefaultPlayerTags(player.getUniqueId());
        }
    }

    private void loadChatTag(UUID uuid, YakPlayer yakPlayer) {
        String tagName = yakPlayer.getChatTag();
        if (tagName != null && !tagName.isEmpty()) {
            try {
                ChatTag tag = ChatTag.valueOf(tagName.toUpperCase());
                playerTags.put(uuid, tag);
            } catch (IllegalArgumentException e) {
                playerTags.put(uuid, ChatTag.DEFAULT);
                yakPlayer.setChatTag(ChatTag.DEFAULT.name());
            }
        } else {
            playerTags.put(uuid, ChatTag.DEFAULT);
            yakPlayer.setChatTag(ChatTag.DEFAULT.name());
        }
    }

    private void loadUnlockedTags(UUID uuid, YakPlayer yakPlayer) {
        Set<String> unlockedTags = yakPlayer.getUnlockedChatTags();
        unlockedPlayerTags.put(uuid, unlockedTags != null ? new HashSet<>(unlockedTags) : new HashSet<>());
    }

    private void setDefaultPlayerTags(UUID uuid) {
        playerTags.put(uuid, ChatTag.DEFAULT);
        unlockedPlayerTags.put(uuid, new HashSet<>());
    }

    private void loadPlayerMuteStatus(Player player) {
        // Mute status is now handled directly through YakPlayer, no separate loading needed
        logger.fine("Mute status loaded via YakPlayer for: " + player.getName());
    }

    private void savePlayerChatTag(Player player) {
        try {
            UUID uuid = player.getUniqueId();
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            if (yakPlayer != null && playerTags.containsKey(uuid)) {
                yakPlayer.setChatTag(playerTags.get(uuid).name());
                playerManager.savePlayer(yakPlayer);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error saving chat tag for " + player.getName(), e);
        }
    }

    // === FORMATTING AND DISPLAY ===

    /**
     *  player name formatting with caching
     */
    private String getFormattedPlayerName(Player player) {
        if (player == null) {
            return "[Unknown]";
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                return yakPlayer.getFormattedDisplayName();
            }

            // Fallback if YakPlayer not available
            return buildBasicFormattedName(player);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error formatting name for " + player.getName(), e);
            return ChatColor.GRAY + player.getName();
        }
    }

    private String buildBasicFormattedName(Player player) {
        StringBuilder name = new StringBuilder();

        try {
            // Add chat tag if not default
            ChatTag tag = playerTags.getOrDefault(player.getUniqueId(), ChatTag.DEFAULT);
            if (tag != ChatTag.DEFAULT) {
                String tagString = tag.getTag();
                if (tagString != null && !tagString.isEmpty()) {
                    name.append(tagString).append(" ");
                }
            }

            // Add player name with basic color
            name.append(ChatColor.GRAY).append(player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error building basic formatted name", e);
            name.setLength(0);
            name.append(ChatColor.GRAY).append(player.getName());
        }

        return name.toString();
    }

    /**
     *  player name formatting for specific recipients (considers buddy status)
     */
    private String getFormattedNameFor(Player player, Player recipient) {
        if (player == null || recipient == null) {
            return player != null ? player.getName() : "[Unknown]";
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            YakPlayer recipientYakPlayer = playerManager.getPlayer(recipient);

            if (yakPlayer != null) {
                // Use the base display name but potentially modify name color based on recipient relationship
                String baseName = yakPlayer.getFormattedDisplayName();

                // Check if we need to modify the name color based on buddy status
                if (recipientYakPlayer != null && recipientYakPlayer.isBuddy(player.getName())) {
                    // Replace the final color code with buddy color
                    return replaceFinalNameColor(baseName, ChatColor.GREEN);
                }

                return baseName;
            }

            return buildBasicFormattedName(player);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error formatting name for recipient", e);
            return ChatColor.GRAY + player.getName();
        }
    }

    private String replaceFinalNameColor(String formattedName, ChatColor newColor) {
        // Simple implementation - replace the last color code with buddy color
        // This is a basic implementation; you might want more sophisticated logic
        int lastColorIndex = formattedName.lastIndexOf('§');
        if (lastColorIndex != -1 && lastColorIndex < formattedName.length() - 1) {
            return formattedName.substring(0, lastColorIndex) + newColor +
                    formattedName.substring(lastColorIndex + 2);
        }
        return newColor + formattedName;
    }

    // === ITEM DISPLAY SYSTEM ===

    /**
     *  item validation with comprehensive checks
     */
    private boolean isHoldingValidItem(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        try {
            ItemStack item = player.getInventory().getItemInMainHand();
            return item != null &&
                    item.getType() != Material.AIR &&
                    item.getAmount() > 0 &&
                    item.getType() != null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking if player is holding valid item", e);
            return false;
        }
    }

    /**
     *  item display name generation with better formatting
     */
    private String getItemDisplayName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "Air";
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return meta.getDisplayName();
            }

            // Use TextUtil if available, otherwise format manually
            try {
                return TextUtil.formatItemName(item.getType().name());
            } catch (Exception e) {
                // Fallback formatting
                return formatItemNameFallback(item.getType().name());
            }
        } catch (Exception e) {
            return item.getType().name();
        }
    }

    private String formatItemNameFallback(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return "Unknown";
        }

        return Arrays.stream(materialName.toLowerCase().split("_"))
                .map(word -> word.isEmpty() ? "" :
                        Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    /**
     *  item display formatting with proper color matching
     */
    private String formatItemDisplay(String itemDisplayName) {
        if (itemDisplayName == null || itemDisplayName.isEmpty()) {
            return ChatColor.YELLOW + "[Unknown]";
        }

        // Check if the item name contains color codes
        if (itemDisplayName.contains("§") || itemDisplayName.contains(ChatColor.COLOR_CHAR + "")) {
            String firstColor = extractFirstColor(itemDisplayName);
            if (firstColor != null) {
                return firstColor + "[" + itemDisplayName + firstColor + "]" + ChatColor.WHITE;
            }
        }

        // Default: yellow brackets for items without custom colors
        return ChatColor.YELLOW + "[" + itemDisplayName + "]" + ChatColor.WHITE;
    }

    private String extractFirstColor(String text) {
        if (text == null || text.length() < 2) {
            return null;
        }

        for (int i = 0; i < text.length() - 1; i++) {
            char current = text.charAt(i);
            char next = text.charAt(i + 1);

            if (current == '§' || current == ChatColor.COLOR_CHAR) {
                // Check if it's a color code (not formatting code)
                switch (next) {
                    case '0': return ChatColor.BLACK.toString();
                    case '1': return ChatColor.DARK_BLUE.toString();
                    case '2': return ChatColor.DARK_GREEN.toString();
                    case '3': return ChatColor.DARK_AQUA.toString();
                    case '4': return ChatColor.DARK_RED.toString();
                    case '5': return ChatColor.GOLD.toString();
                    case '6': return ChatColor.GOLD.toString();
                    case '7': return ChatColor.GRAY.toString();
                    case '8': return ChatColor.DARK_GRAY.toString();
                    case '9': return ChatColor.BLUE.toString();
                    case 'a': return ChatColor.GREEN.toString();
                    case 'b': return ChatColor.AQUA.toString();
                    case 'c': return ChatColor.RED.toString();
                    case 'd': return ChatColor.LIGHT_PURPLE.toString();
                    case 'e': return ChatColor.YELLOW.toString();
                    case 'f': return ChatColor.WHITE.toString();
                    default: continue;
                }
            }
        }

        return null;
    }

    // === ADVANCED ITEM MESSAGING ===

    /**
     *  item hover message with better error handling
     */
    private boolean sendItemHoverMessage(Player sender, ItemStack item, String senderName,
                                         String message, Player recipient) {
        if (recipient == null || !recipient.isOnline() || item == null || item.getType() == Material.AIR) {
            return false;
        }

        try {
            // Split message around @i@ placeholder
            String[] parts = message.split("@i@", 2);
            String before = parts.length > 0 ? parts[0] : "";
            String after = parts.length > 1 ? parts[1] : "";

            // Create the base message part
            String baseMessage = senderName + ChatColor.WHITE + ": " + before;

            // Create JSON component
            JsonChatComponent component = new JsonChatComponent(baseMessage);

            // Get item display info
            String itemDisplayName = getItemDisplayName(item);
            List<String> hoverText = getItemHoverText(item);

            // Add the hoverable item part with proper color handling
            String itemText = formatItemDisplay(itemDisplayName);
            component.addHoverItem(itemText, hoverText);

            // Add rest of message if there is any
            if (!after.isEmpty()) {
                component.addText(after);
            }

            // Try to send the component
            component.send(recipient);
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating/sending item hover message to " + recipient.getName(), e);
            return false;
        }
    }

    /**
     *  item hover text generation with comprehensive information
     */
    private List<String> getItemHoverText(ItemStack item) {
        List<String> text = new ArrayList<>();

        if (item == null || item.getType() == Material.AIR) {
            text.add(ChatColor.GRAY + "Air");
            return text;
        }

        try {
            ItemMeta meta = item.getItemMeta();

            // Add item name (with color if custom)
            if (meta != null && meta.hasDisplayName()) {
                text.add(meta.getDisplayName());
            } else {
                text.add(ChatColor.WHITE + getItemDisplayName(item));
            }

            // Add amount if more than 1
            if (item.getAmount() > 1) {
                text.add(ChatColor.GRAY + "Amount: " + ChatColor.WHITE + item.getAmount());
            }

            // Add durability info for tools/armor
            if (item.getType().getMaxDurability() > 0) {
                short durability = item.getDurability();
                short maxDurability = item.getType().getMaxDurability();
                if (durability > 0) {
                    int remaining = maxDurability - durability;
                    text.add(ChatColor.GRAY + "Durability: " + ChatColor.WHITE + remaining + "/" + maxDurability);
                }
            }

            // Add lore if present
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (lore != null && !lore.isEmpty()) {
                    text.add(""); // Empty line separator
                    text.addAll(lore);
                }
            }

            // Add material type info
            text.add(""); // Empty line separator
            text.add(ChatColor.DARK_GRAY + "Type: " + ChatColor.GRAY + item.getType().name());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error generating item hover text", e);
            text.clear();
            text.add(ChatColor.WHITE + getItemDisplayName(item));
            text.add(ChatColor.GRAY + "(" + item.getType().name() + ")");
        }

        return text;
    }

    // === MESSAGE FILTERING ===

    /**
     *  message filtering with configurable word lists and IP detection
     */
    private String filterMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        try {
            String filtered = message;

            // Filter banned words (case-insensitive)
            for (String word : BANNED_WORDS) {
                if (word != null && !word.isEmpty()) {
                    filtered = filtered.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", "*****");
                }
            }

            // Filter IP addresses
            filtered = IP_PATTERN.matcher(filtered).replaceAll("***.***.***.**");

            return filtered.trim();

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error filtering message", e);
            return message; // Return original on error
        }
    }

    // === FALLBACK METHODS ===

    private void sendFallbackItemMessage(Player player, String message) {
        try {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isHoldingValidItem(player)) {
                handleNoItemFallback(player, message);
                return;
            }

            String formattedName = getFormattedPlayerName(player);
            String itemDisplay = formatItemDisplay(getItemDisplayName(item));
            String finalMessage = message.replace("@i@", itemDisplay);

            // Send to self
            player.sendMessage(formattedName + ChatColor.WHITE + ": " + finalMessage);

            // Send to nearby players
            List<Player> recipients = getNearbyPlayers(player);
            if (recipients.isEmpty()) {
                player.sendMessage(Messages.NO_LISTENERS);
            } else {
                sendToRecipients(player, recipients, finalMessage, formattedName);
            }

            // Send to vanished staff
            sendToVanishedStaff(player, formattedName, finalMessage, false);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in fallback item message", e);
            sendBasicFallbackMessage(player, message.replace("@i@", "[ITEM]"));
        }
    }

    private void sendBasicFallbackMessage(Player player, String message) {
        try {
            String basicMessage = ChatColor.GRAY + player.getName() + ChatColor.WHITE + ": " + message;
            player.sendMessage(basicMessage);

            // Send to nearby players with basic formatting
            getNearbyPlayers(player).forEach(recipient ->
                    recipient.sendMessage(basicMessage));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error in basic fallback message", e);
        }
    }

    private void handleItemMessageError(Player player, String message) {
        String errorMessage = message.replace("@i@", ChatColor.RED + "[ITEM ERROR]" + ChatColor.WHITE);
        sendNormalMessage(player, errorMessage);
    }

    // === UTILITY METHODS ===

    /**
     *  file existence checking with directory creation
     */
    private boolean ensureFileExists(File file) {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            if (!file.exists()) {
                file.createNewFile();
                return true;
            }
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not create file: " + file.getName(), e);
            return false;
        }
    }

    private void logChatMessage(Player player, String message) {
        logger.info(player.getName() + ": " + message);
    }

    // === COMPATIBILITY AND DETECTION ===

    /**
     *  modern chat support detection
     */
    public static boolean supportsModernChat() {
        try {
            Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class.forName("net.md_5.bungee.api.chat.HoverEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     *  compatibility information gathering
     */
    public static String getCompatibilityInfo() {
        StringBuilder info = new StringBuilder();

        info.append("Server: ").append(Bukkit.getName()).append(" ")
                .append(Bukkit.getVersion()).append(" | ");

        info.append("Bukkit Version: ").append(Bukkit.getBukkitVersion()).append(" | ");

        info.append("Modern Chat Support: ").append(supportsModernChat() ? "Yes" : "No").append(" | ");

        info.append("Players Online: ").append(Bukkit.getOnlinePlayers().size());

        return info.toString();
    }

    // === PUBLIC API METHODS ===

    /**
     *  mute management using YakPlayer system
     */
    public static void mutePlayer(Player player, int timeInSeconds) {
        if (player == null) {
            return;
        }

        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setMuteTime(timeInSeconds);
                YakPlayerManager.getInstance().savePlayer(yakPlayer);

                // Notify player
                player.sendMessage(Messages.MUTED_MESSAGE);
                if (timeInSeconds > 0) {
                    int minutes = timeInSeconds / 60;
                    player.sendMessage(String.format(Messages.MUTED_TEMPORARY, minutes));
                } else {
                    player.sendMessage(Messages.MUTED_PERMANENT);
                }

                getInstance().logger.info("Player " + player.getName() + " muted for " + timeInSeconds + " seconds");
            }
        } catch (Exception e) {
            getInstance().logger.log(Level.WARNING, "Error muting player " + player.getName(), e);
        }
    }

    /**
     *  unmute functionality using YakPlayer system
     */
    public static void unmutePlayer(Player player) {
        if (player == null) {
            return;
        }

        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setMuteTime(0);
                YakPlayerManager.getInstance().savePlayer(yakPlayer);

                player.sendMessage(Messages.UNMUTED_MESSAGE);
                getInstance().logger.info("Player " + player.getName() + " has been unmuted");
            }
        } catch (Exception e) {
            getInstance().logger.log(Level.WARNING, "Error unmuting player " + player.getName(), e);
        }
    }

    /**
     *  mute status checking using YakPlayer system
     */
    public static boolean isMuted(Player player) {
        if (player == null) {
            return false;
        }

        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            return yakPlayer != null && yakPlayer.isMuted();
        } catch (Exception e) {
            getInstance().logger.log(Level.WARNING, "Error checking mute status for " + player.getName(), e);
            return false;
        }
    }

    /**
     *  mute time retrieval using YakPlayer system
     */
    public static int getMuteTime(Player player) {
        if (player == null) {
            return 0;
        }

        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            return yakPlayer != null ? yakPlayer.getMuteTime() : 0;
        } catch (Exception e) {
            getInstance().logger.log(Level.WARNING, "Error getting mute time for " + player.getName(), e);
            return 0;
        }
    }

    // === CHAT TAG MANAGEMENT ===

    /**
     *  chat tag retrieval
     */
    public static ChatTag getPlayerTag(Player player) {
        if (player == null) {
            return ChatTag.DEFAULT;
        }
        return playerTags.getOrDefault(player.getUniqueId(), ChatTag.DEFAULT);
    }

    /**
     *  chat tag setting with validation
     */
    public static void setPlayerTag(Player player, ChatTag tag) {
        if (player == null || tag == null) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();
            playerTags.put(uuid, tag);

            // Save to YakPlayer data
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setChatTag(tag.name());
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            }

            getInstance().logger.fine("Set chat tag " + tag.name() + " for player " + player.getName());
        } catch (Exception e) {
            getInstance().logger.log(Level.WARNING, "Error setting chat tag", e);
        }
    }

    /**
     *  tag unlock checking
     */
    public static boolean hasTagUnlocked(Player player, ChatTag tag) {
        if (player == null || tag == null) {
            return false;
        }

        try {
            UUID uuid = player.getUniqueId();
            Set<String> unlockedTags = unlockedPlayerTags.get(uuid);
            return unlockedTags != null && unlockedTags.contains(tag.name());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     *  tag unlocking with YakPlayer persistence
     */
    public static void unlockTag(Player player, ChatTag tag) {
        if (player == null || tag == null) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();
            Set<String> unlockedTags = unlockedPlayerTags.computeIfAbsent(uuid, k -> new HashSet<>());

            if (unlockedTags.add(tag.name())) {
                // Save to YakPlayer data
                YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
                if (yakPlayer != null) {
                    yakPlayer.unlockChatTag(tag);
                    YakPlayerManager.getInstance().savePlayer(yakPlayer);
                }

                player.sendMessage(ChatColor.GREEN + "Unlocked chat tag: " + tag.getTag());
                getInstance().logger.info("Player " + player.getName() + " unlocked chat tag: " + tag.name());
            }
        } catch (Exception e) {
            getInstance().logger.log(Level.WARNING, "Error unlocking chat tag", e);
        }
    }

    // === REPLY SYSTEM ===

    /**
     *  reply target management
     */
    public static void setReplyTarget(Player sender, Player target) {
        if (sender != null) {
            if (target != null && target.isOnline()) {
                replyTargets.put(sender.getUniqueId(), target);
            } else {
                replyTargets.remove(sender.getUniqueId());
            }
        }
    }

    /**
     *  reply target retrieval with validation
     */
    public static Player getReplyTarget(Player player) {
        if (player == null) {
            return null;
        }

        Player target = replyTargets.get(player.getUniqueId());
        if (target != null && !target.isOnline()) {
            replyTargets.remove(player.getUniqueId());
            return null;
        }
        return target;
    }

    // === EXTERNAL API METHODS ===

    /**
     *  external item messaging API
     */
    public static boolean sendItemMessageToPlayer(Player sender, ItemStack item, String prefix,
                                                  String message, Player recipient) {
        ChatMechanics instance = getInstance();
        if (instance != null) {
            return instance.sendItemHoverMessage(sender, item, prefix, message, recipient);
        }
        return false;
    }

    /**
     *  external batch item messaging API
     */
    public static int sendItemMessageToPlayers(Player sender, ItemStack item, String prefix,
                                               String message, List<Player> recipients) {
        int successCount = 0;
        ChatMechanics instance = getInstance();

        if (instance == null || recipients == null) {
            return successCount;
        }

        for (Player recipient : recipients) {
            if (recipient != null && recipient.isOnline()) {
                try {
                    boolean success = instance.sendItemHoverMessage(sender, item, prefix, message, recipient);
                    if (success) {
                        successCount++;
                    } else {
                        //  fallback with proper formatting
                        String itemName = instance.getItemDisplayName(item);
                        String itemDisplay = instance.formatItemDisplay(itemName);
                        String fallbackMessage = message.replace("@i@", itemDisplay);
                        recipient.sendMessage(prefix + ChatColor.WHITE + ": " + fallbackMessage);
                        successCount++;
                    }
                } catch (Exception e) {
                    instance.logger.log(Level.WARNING, "Error sending item message to " + recipient.getName(), e);
                    // Final fallback
                    String fallbackMessage = message.replace("@i@", "[ITEM]");
                    recipient.sendMessage(prefix + ChatColor.WHITE + ": " + fallbackMessage);
                    successCount++;
                }
            }
        }

        return successCount;
    }

    /**
     *  external item validation API
     */
    public static boolean isPlayerHoldingValidItem(Player player) {
        ChatMechanics instance = getInstance();
        return instance != null && instance.isHoldingValidItem(player);
    }

    /**
     *  external item display name API
     */
    public static String getDisplayNameForItem(ItemStack item) {
        ChatMechanics instance = getInstance();
        if (instance != null) {
            return instance.getItemDisplayName(item);
        }
        return item != null ? item.getType().name() : "Unknown";
    }

    // === STATISTICS AND DEBUGGING ===

    /**
     *  statistics gathering
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("playerTags", playerTags.size());
        stats.put("unlockedTags", unlockedPlayerTags.size());
        stats.put("activeCooldowns", chatCooldown.size());
        stats.put("replyTargets", replyTargets.size());
        stats.put("supportsModernChat", supportsModernChat());
        stats.put("isEnabled", getInstance().isEnabled.get());
        return stats;
    }

    /**
     *  debug information for administrators
     */
    public static void debugChatSystem(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        player.sendMessage(ChatColor.GOLD + "===  Chat System Debug Info ===");

        // System information
        player.sendMessage(ChatColor.YELLOW + "Server Version: " + ChatColor.WHITE + Bukkit.getVersion());
        player.sendMessage(ChatColor.YELLOW + "Bukkit Version: " + ChatColor.WHITE + Bukkit.getBukkitVersion());
        player.sendMessage(ChatColor.YELLOW + "Modern Chat Support: " + ChatColor.WHITE + supportsModernChat());

        // Instance status
        ChatMechanics instance = getInstance();
        if (instance != null) {
            player.sendMessage(ChatColor.GREEN + "✓ ChatMechanics instance active");
            player.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE + instance.isEnabled.get());

            // Player-specific information
            ChatTag tag = getPlayerTag(player);
            player.sendMessage(ChatColor.YELLOW + "Your Chat Tag: " + ChatColor.WHITE + tag.name());

            boolean muted = isMuted(player);
            player.sendMessage(ChatColor.YELLOW + "Muted: " + ChatColor.WHITE + (muted ? "Yes" : "No"));

            boolean hasCooldown = chatCooldown.containsKey(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Chat Cooldown Active: " + ChatColor.WHITE + (hasCooldown ? "Yes" : "No"));

            // Statistics
            Map<String, Object> stats = getStatistics();
            player.sendMessage(ChatColor.YELLOW + "Active Statistics:");
            stats.forEach((key, value) ->
                    player.sendMessage(ChatColor.GRAY + "  " + key + ": " + ChatColor.WHITE + value));

        } else {
            player.sendMessage(ChatColor.RED + "✗ ChatMechanics instance not found");
        }

        player.sendMessage(ChatColor.GOLD + "=== End Debug Info ===");
    }

    /**
     *  test method for item display functionality
     */
    public static void testItemDisplay(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            ChatMechanics instance = getInstance();
            if (instance == null) {
                player.sendMessage(ChatColor.RED + "ChatMechanics not available for testing");
                return;
            }

            instance.logger.info("Testing item display for " + player.getName());

            // Check if player is holding an item
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Hold an item in your main hand to test item display.");
                return;
            }

            player.sendMessage(ChatColor.GREEN + "Testing  item display functionality...");

            // Test basic item info
            player.sendMessage(ChatColor.YELLOW + "Item Type: " + item.getType().name());
            player.sendMessage(ChatColor.YELLOW + "Item Amount: " + item.getAmount());
            player.sendMessage(ChatColor.YELLOW + "Display Name: " + instance.getItemDisplayName(item));

            // Test modern chat support
            if (supportsModernChat()) {
                player.sendMessage(ChatColor.GREEN + "✓ BungeeCord Chat API is available");
            } else {
                player.sendMessage(ChatColor.RED + "✗ BungeeCord Chat API is NOT available - using fallback mode");
            }

            // Test hover message creation
            try {
                JsonChatComponent testComponent = new JsonChatComponent(ChatColor.BLUE + "Hover over this text!");
                testComponent.addHoverText(ChatColor.YELLOW + " [HOVER TEST] ", "This is a hover test message");
                testComponent.send(player);
                player.sendMessage(ChatColor.GREEN + "✓ Basic hover message sent");
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "✗ Failed to send hover message: " + e.getMessage());
                instance.logger.warning("Failed to send hover message: " + e.getMessage());
            }

            // Test item display processing
            try {
                instance.processChat(player, "Testing  item display @i@ here!");
                player.sendMessage(ChatColor.GREEN + "✓  item display test completed");
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "✗ Item display test failed: " + e.getMessage());
                instance.logger.warning("Item display test failed: " + e.getMessage());
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error during testing: " + e.getMessage());
            getInstance().logger.log(Level.SEVERE, "Error during  item display testing", e);
        }
    }

    // === ACCESS METHODS ===

    /**
     * Access to player tags map for compatibility
     */
    public static Map<UUID, ChatTag> getPlayerTags() {
        return new HashMap<>(playerTags);
    }

    /**
     *  method to clear all temporary data (for testing/admin use)
     */
    public static void clearAllTempData() {
        chatCooldown.clear();
        replyTargets.clear();
        getInstance().logger.info("Cleared all temporary chat data");
    }

    /**
     *  method to reload player data (for admin use)
     */
    public static void reloadPlayerData(Player player) {
        if (player != null && getInstance() != null) {
            getInstance().loadPlayerChatTag(player);
            getInstance().logger.info("Reloaded chat data for player: " + player.getName());
        }
    }
}