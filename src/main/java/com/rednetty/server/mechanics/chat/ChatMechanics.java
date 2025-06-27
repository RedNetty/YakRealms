package com.rednetty.server.mechanics.chat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Handles all chat-related functionality:
 * - Chat formatting and display
 * - Chat tags management
 * - Chat filtering and moderation
 * - Item display in chat
 * - Chat cooldowns
 * - Guild tag integration
 * FIXED: Improved error handling, logic fixes, and item display functionality
 */
public class ChatMechanics implements Listener {

    private static ChatMechanics instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    // Memory caches
    private static final Map<UUID, ChatTag> playerTags = new ConcurrentHashMap<>();
    private static final Map<UUID, List<String>> unlockedPlayerTags = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> chatCooldown = new ConcurrentHashMap<>();
    private static final Map<UUID, Player> replyTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> mutedPlayers = new ConcurrentHashMap<>();

    // Chat configuration
    private static final int DEFAULT_CHAT_RANGE = 50;
    private static final int COOLDOWN_TICKS = 20; // 1 second

    // Chat filter - FIXED: Made pattern more robust
    private static final List<String> bannedWords = new ArrayList<>(Arrays.asList(
            "nigger"
            // Add other banned words here
    ));
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");

    /**
     * Gets the singleton instance of ChatMechanics
     *
     * @return The ChatMechanics instance
     */
    public static ChatMechanics getInstance() {
        if (instance == null) {
            instance = new ChatMechanics();
        }
        return instance;
    }

    /**
     * Private constructor for singleton pattern
     */
    private ChatMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Initialize the chat mechanics
     */
    public void onEnable() {
        try {
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Start cooldown task
            startCooldownTask();

            // Start mute timer task
            startMuteTimerTask();

            // Load muted players data
            loadMutedPlayers();

            // Load chat tags for online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                loadPlayerChatTag(player);
            }

            YakRealms.log("ChatMechanics has been enabled.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error enabling ChatMechanics", e);
        }
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        try {
            // Save all chat tags back to player data
            saveAllChatTags();

            // Save muted players data
            saveMutedPlayers();

            chatCooldown.clear();
            replyTargets.clear();
            mutedPlayers.clear();

            YakRealms.log("ChatMechanics has been disabled.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error disabling ChatMechanics", e);
        }
    }

    /**
     * Start the task that manages chat cooldowns
     */
    private void startCooldownTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Iterator<Map.Entry<UUID, Integer>> iterator = chatCooldown.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<UUID, Integer> entry = iterator.next();
                        int newValue = entry.getValue() - 1;
                        if (newValue <= 0) {
                            iterator.remove();
                        } else {
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
     * Start the task that decreases mute times
     */
    private void startMuteTimerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Iterator<Map.Entry<UUID, Integer>> iterator = mutedPlayers.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<UUID, Integer> entry = iterator.next();
                        int newValue = entry.getValue();

                        // Skip permanent mutes (value <= 0)
                        if (newValue > 0) {
                            newValue--;
                            if (newValue <= 0) {
                                iterator.remove();
                                // Update player data
                                UUID uuid = entry.getKey();
                                Player player = Bukkit.getPlayer(uuid);
                                if (player != null && player.isOnline()) {
                                    YakPlayer yakPlayer = playerManager.getPlayer(player);
                                    if (yakPlayer != null) {
                                        yakPlayer.setMuteTime(0);
                                        playerManager.savePlayer(yakPlayer);
                                    }
                                }
                            } else {
                                entry.setValue(newValue);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in mute timer task", e);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20, 20); // Run every second
    }

    /**
     * Load muted players from configuration or database
     */
    private void loadMutedPlayers() {
        try {
            File file = new File(YakRealms.getInstance().getDataFolder(), "muted.yml");
            if (!file.exists()) {
                file.createNewFile();
                return;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.getConfigurationSection("muted") != null) {
                for (String key : config.getConfigurationSection("muted").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        int time = config.getInt("muted." + key);
                        mutedPlayers.put(uuid, time);

                        // Update player data if they're online
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            YakPlayer yakPlayer = playerManager.getPlayer(player);
                            if (yakPlayer != null) {
                                yakPlayer.setMuteTime(time);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid UUID in muted.yml: " + key);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error loading muted players data", e);
        }
    }

    /**
     * Save muted players to configuration or database
     */
    private void saveMutedPlayers() {
        try {
            File file = new File(YakRealms.getInstance().getDataFolder(), "muted.yml");
            YamlConfiguration config = new YamlConfiguration();

            for (Map.Entry<UUID, Integer> entry : mutedPlayers.entrySet()) {
                config.set("muted." + entry.getKey().toString(), entry.getValue());
            }

            config.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error saving muted players data", e);
        }
    }

    /**
     * Save all chat tags from memory to player data
     */
    private void saveAllChatTags() {
        try {
            for (Map.Entry<UUID, ChatTag> entry : playerTags.entrySet()) {
                YakPlayer player = playerManager.getPlayer(entry.getKey());
                if (player != null) {
                    player.setChatTag(entry.getValue().name());
                    playerManager.savePlayer(player);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error saving chat tags", e);
        }
    }

    /**
     * Handler for player join to load chat tags
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();
            loadPlayerChatTag(player);

            // Load mute status if exists
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null && yakPlayer.isMuted()) {
                mutedPlayers.put(player.getUniqueId(), yakPlayer.getMuteTime());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling player join for " + event.getPlayer().getName(), e);
        }
    }

    /**
     * Handler for player quit to save chat tags
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        try {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            // Save chat tag
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null && playerTags.containsKey(uuid)) {
                yakPlayer.setChatTag(playerTags.get(uuid).name());
                playerManager.savePlayer(yakPlayer);
            }

            // Clean up
            chatCooldown.remove(uuid);
            replyTargets.remove(uuid);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling player quit for " + event.getPlayer().getName(), e);
        }
    }

    /**
     * Load a player's chat tag from their data
     * FIXED: Better error handling and validation
     *
     * @param player The player to load for
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

                // Load unlocked chat tags
                List<String> unlockedTags = new ArrayList<>();
                Set<String> playerUnlockedTags = yakPlayer.getUnlockedChatTags();
                if (playerUnlockedTags != null) {
                    unlockedTags.addAll(playerUnlockedTags);
                }
                unlockedPlayerTags.put(uuid, unlockedTags);

                playerManager.savePlayer(yakPlayer);
            } else {
                // New player
                playerTags.put(uuid, ChatTag.DEFAULT);
                unlockedPlayerTags.put(uuid, new ArrayList<>());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error loading chat tag for " + player.getName(), e);
            // Set defaults in case of error
            playerTags.put(player.getUniqueId(), ChatTag.DEFAULT);
            unlockedPlayerTags.put(player.getUniqueId(), new ArrayList<>());
        }
    }

    /**
     * Filter main chat to handle command execution via chat
     * FIXED: Better command filtering logic
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }

        try {
            Player player = event.getPlayer();
            String command = event.getMessage().toLowerCase();

            // If message starts with "//", it's a chat message, not a command
            if (command.startsWith("//")) {
                event.setCancelled(true);
                // Process as chat, removing the //
                String message = event.getMessage().substring(2);
                if (!message.trim().isEmpty()) {
                    processChat(player, message);
                }
                return;
            }

            // Check restricted commands for non-staff
            if (!player.isOp() && !ModerationMechanics.isStaff(player)) {
                // Extract command name
                String cmdName = command.startsWith("/") ? command.substring(1) : command;
                if (cmdName.contains(" ")) {
                    cmdName = cmdName.split(" ")[0];
                }

                // List of restricted commands
                Set<String> restrictedCommands = new HashSet<>(Arrays.asList(
                        "save-all", "stack", "stop", "restart", "reload", "tpall", "kill", "mute"
                ));

                if (restrictedCommands.contains(cmdName)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.WHITE + "Unknown command. View your Character Journal's Index for a list of commands.");
                    return;
                }
            }

            // If player tries to use /gl or /g without permissions, convert to chat
            if ((command.startsWith("/gl ") || command.startsWith("/g ")) &&
                    !player.hasPermission("yakrealms.guild.chat")) {
                event.setCancelled(true);
                String message = command.substring(command.indexOf(' ') + 1);
                if (!message.trim().isEmpty()) {
                    processChat(player, message);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing command for " + event.getPlayer().getName(), e);
        }
    }

    /**
     * Main chat event handler
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        try {
            Player player = event.getPlayer();
            String message = event.getMessage();

            // Cancel the default Bukkit chat behavior
            event.setCancelled(true);

            // Process the chat message synchronously to avoid thread issues
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                processChat(player, message);
            });
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling chat for " + event.getPlayer().getName(), e);
        }
    }

    /**
     * Process a chat message from a player
     * FIXED: Better validation and error handling
     *
     * @param player  The player sending the message
     * @param message The message text
     */
    private void processChat(Player player, String message) {
        if (player == null || !player.isOnline() || message == null || message.trim().isEmpty()) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();

            // Check if player is muted
            if (mutedPlayers.containsKey(uuid)) {
                int muteTime = mutedPlayers.get(uuid);
                player.sendMessage(ChatColor.RED + "You are currently muted");
                if (muteTime > 0) {
                    int minutes = muteTime / 60;
                    player.sendMessage(ChatColor.RED + "Your mute expires in " + minutes + " minutes.");
                } else {
                    player.sendMessage(ChatColor.RED + "Your mute WILL NOT expire.");
                }
                return;
            }

            // Check cooldown
            if (chatCooldown.containsKey(uuid)) {
                player.sendMessage(ChatColor.RED + "Please wait before chatting again.");
                return;
            }

            // Apply cooldown
            chatCooldown.put(uuid, COOLDOWN_TICKS);

            // Filter message
            message = filterMessage(message);

            // Check if showing an item
            if (message.contains("@i@") && isHoldingValidItem(player)) {
                sendItemMessage(player, message);
            } else {
                sendNormalMessage(player, message);
            }

            // Log chat message
            logger.info(player.getName() + ": " + message);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing chat for " + player.getName(), e);
            // Fallback - send as normal message
            try {
                sendNormalMessage(player, message);
            } catch (Exception e2) {
                logger.log(Level.SEVERE, "Failed to send fallback message", e2);
            }
        }
    }

    /**
     * Send a normal chat message (without item display)
     *
     * @param player  The player sending the message
     * @param message The message text
     */
    private void sendNormalMessage(Player player, String message) {
        try {
            String formattedName = getFormattedName(player);
            String fullMessage = formattedName + ChatColor.WHITE + ": " + message;

            // Always send message to the sender
            player.sendMessage(fullMessage);

            // Send to nearby players
            List<Player> recipients = getNearbyPlayers(player);

            if (recipients.isEmpty()) {
                player.sendMessage(ChatColor.GRAY.toString() + ChatColor.ITALIC + "No one heard you.");
            } else {
                for (Player recipient : recipients) {
                    if (recipient != null && recipient.isOnline()) {
                        try {
                            String recipientSpecificName = getFormattedNameFor(player, recipient);
                            recipient.sendMessage(recipientSpecificName + ChatColor.WHITE + ": " + message);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error sending message to " + recipient.getName(), e);
                        }
                    }
                }
            }

            // Send to vanished staff
            sendToVanishedStaff(player, formattedName, message);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending normal message", e);
        }
    }

    /**
     * Send a message with an item showcase
     * FIXED: Better error handling and validation
     *
     * @param player  The player sending the message
     * @param message The message text containing @i@ for item placement
     */
    private void sendItemMessage(Player player, String message) {
        try {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                // Fallback to normal message if item is not valid
                sendNormalMessage(player, message.replace("@i@", "[ITEM]"));
                return;
            }

            String formattedName = getFormattedName(player);

            // Send to self with item hover
            sendItemHoverMessage(player, item, formattedName, message, player);

            // Send to nearby players
            List<Player> recipients = getNearbyPlayers(player);

            if (recipients.isEmpty()) {
                player.sendMessage(ChatColor.GRAY.toString() + ChatColor.ITALIC + "No one heard you.");
            } else {
                for (Player recipient : recipients) {
                    if (recipient != null && recipient.isOnline()) {
                        String recipientSpecificName = null;
                        try {
                            recipientSpecificName = getFormattedNameFor(player, recipient);
                            sendItemHoverMessage(player, item, recipientSpecificName, message, recipient);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error sending item message to " + recipient.getName(), e);
                            // Fallback to normal message
                            recipient.sendMessage(recipientSpecificName + ChatColor.WHITE + ": " +
                                    message.replace("@i@", "[" + item.getType().name() + "]"));
                        }
                    }
                }
            }

            // Send to vanished staff
            sendToVanishedStaff(player, formattedName, message);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending item message", e);
            // Fallback to normal message
            sendNormalMessage(player, message.replace("@i@", "[ITEM]"));
        }
    }

    /**
     * Send a message to vanished staff members
     *
     * @param player  The player who sent the message
     * @param prefix  The player's prefix/name
     * @param message The message text
     */
    private void sendToVanishedStaff(Player player, String prefix, String message) {
        try {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff != null && staff.isOnline() &&
                        staff.hasPermission("yakrealms.vanish") &&
                        staff.hasMetadata("vanished") &&
                        !staff.equals(player)) {

                    try {
                        if (message.contains("@i@") && isHoldingValidItem(player)) {
                            ItemStack item = player.getInventory().getItemInMainHand();
                            sendItemHoverMessage(player, item, prefix, message, staff);
                        } else {
                            staff.sendMessage(prefix + ChatColor.WHITE + ": " + message);
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error sending to vanished staff " + staff.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending to vanished staff", e);
        }
    }

    /**
     * Send an item hover message to a player
     * FIXED: Better validation and error handling
     *
     * @param sender    The player sending the message
     * @param item      The item to display
     * @param prefix    The sender's prefix/name
     * @param message   The message text
     * @param recipient The player to send to
     */
    private void sendItemHoverMessage(Player sender, ItemStack item, String prefix,
                                      String message, Player recipient) {
        if (recipient == null || !recipient.isOnline() || item == null || item.getType() == Material.AIR) {
            return;
        }

        try {
            String[] parts = message.split("@i@", 2);
            String before = parts.length > 0 ? parts[0] : "";
            String after = parts.length > 1 ? parts[1] : "";

            // Create JSON component with hover
            JsonChatComponent component = new JsonChatComponent(prefix + ChatColor.WHITE + ": " + before);

            // Add hover text from item lore
            List<String> hoverText = getItemHoverText(item);
            component.addHoverItem("[" + getItemDisplayName(item) + "]", hoverText);

            // Add rest of message
            if (!after.isEmpty()) {
                component.addText(ChatColor.WHITE + after);
            }

            // Send to recipient
            component.send(recipient);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending item hover message", e);
            // Fallback to normal message
            try {
                String fallbackMessage = message.replace("@i@", "[" + getItemDisplayName(item) + "]");
                recipient.sendMessage(prefix + ChatColor.WHITE + ": " + fallbackMessage);
            } catch (Exception e2) {
                logger.log(Level.SEVERE, "Failed to send fallback item message", e2);
            }
        }
    }

    /**
     * Get a list of text to display when hovering over an item
     * FIXED: Better handling of item meta and null checks
     *
     * @param item The item to get hover text for
     * @return List of hover text lines
     */
    private List<String> getItemHoverText(ItemStack item) {
        List<String> text = new ArrayList<>();

        if (item == null || item.getType() == Material.AIR) {
            text.add("Air");
            return text;
        }

        try {
            ItemMeta meta = item.getItemMeta();

            // Add item name
            if (meta != null && meta.hasDisplayName()) {
                text.add(meta.getDisplayName());
            } else {
                text.add(getItemDisplayName(item));
            }

            // Add amount if more than 1
            if (item.getAmount() > 1) {
                text.add(ChatColor.GRAY + "Amount: " + item.getAmount());
            }

            // Add lore
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (lore != null && !lore.isEmpty()) {
                    text.add(""); // Empty line
                    text.addAll(lore);
                }
            }

            // Add material type if no custom name
            if (meta == null || !meta.hasDisplayName()) {
                text.add(ChatColor.DARK_GRAY + "(" + item.getType().name() + ")");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting item hover text", e);
            text.clear();
            text.add(getItemDisplayName(item));
        }

        return text;
    }

    /**
     * FIXED: Get proper display name for item
     *
     * @param item The item
     * @return The display name
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
                return formatItemName(item.getType().name());
            }
        } catch (Exception e) {
            return item.getType().name();
        }
    }

    /**
     * FIXED: Fallback item name formatting
     *
     * @param materialName The material name
     * @return Formatted name
     */
    private String formatItemName(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return "Unknown";
        }

        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Get nearby players who can receive a chat message
     *
     * @param player The player sending the message
     * @return List of nearby players
     */
    private List<Player> getNearbyPlayers(Player player) {
        List<Player> nearbyPlayers = new ArrayList<>();

        if (player == null || !player.isOnline()) {
            return nearbyPlayers;
        }

        try {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != null && other.isOnline() &&
                        !other.equals(player) &&
                        other.getWorld().equals(player.getWorld()) &&
                        other.getLocation().distance(player.getLocation()) < DEFAULT_CHAT_RANGE &&
                        !isVanished(other)) {

                    nearbyPlayers.add(other);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting nearby players", e);
        }

        return nearbyPlayers;
    }

    /**
     * Check if a player is vanished
     *
     * @param player The player to check
     * @return true if the player is vanished
     */
    private boolean isVanished(Player player) {
        if (player == null) {
            return false;
        }

        try {
            return player.hasMetadata("vanished");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Filter a chat message for inappropriate content
     * FIXED: More robust filtering
     *
     * @param message The message to filter
     * @return The filtered message
     */
    private String filterMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        try {
            // Filter banned words (case-insensitive)
            for (String word : bannedWords) {
                if (word != null && !word.isEmpty()) {
                    message = message.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", "*****");
                }
            }

            // Filter IP addresses
            message = IP_PATTERN.matcher(message).replaceAll("***.***.***.**");

            return message.trim();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error filtering message", e);
            return message; // Return original on error
        }
    }

    /**
     * Get a player's formatted name for chat
     * FIXED: Better null handling
     *
     * @param player The player
     * @return The formatted name with rank and tag
     */
    private String getFormattedName(Player player) {
        if (player == null) {
            return "[Unknown]";
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                return yakPlayer.getFormattedDisplayName();
            }

            // Fallback if YakPlayer not available
            return buildFormattedName(player, yakPlayer);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error formatting name for " + player.getName(), e);
            return ChatColor.GRAY + player.getName();
        }
    }

    /**
     * FIXED: Build formatted name with better error handling
     */
    private String buildFormattedName(Player player, YakPlayer yakPlayer) {
        StringBuilder name = new StringBuilder();

        try {
            // Add guild tag if available
            if (yakPlayer != null && yakPlayer.isInGuild()) {
                String guildName = yakPlayer.getGuildName();
                if (guildName != null && !guildName.isEmpty()) {
                    name.append(ChatColor.WHITE).append("[").append(guildName).append("] ");
                }
            }

            // Add chat tag if not default
            ChatTag tag = playerTags.getOrDefault(player.getUniqueId(), ChatTag.DEFAULT);
            if (tag != ChatTag.DEFAULT) {
                String tagString = tag.getTag();
                if (tagString != null && !tagString.isEmpty()) {
                    name.append(tagString).append(" ");
                }
            }

            // Add rank if not default
            if (yakPlayer != null) {
                String rankString = yakPlayer.getRank();
                if (rankString != null && !rankString.isEmpty()) {
                    Rank rank = Rank.fromString(rankString);
                    if (rank != null && rank != Rank.DEFAULT) {
                        name.append(ChatColor.translateAlternateColorCodes('&', rank.tag)).append(" ");
                    }
                }
            }

            // Add player name
            name.append(getNameColorByAlignment(player)).append(player.getName());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error building formatted name", e);
            name.setLength(0);
            name.append(ChatColor.GRAY).append(player.getName());
        }

        return name.toString();
    }

    /**
     * Get a player's formatted name for display to a specific recipient
     * This accounts for buddy status
     *
     * @param player    The player whose name to format
     * @param recipient The player who will see the name
     * @return The formatted name
     */
    private String getFormattedNameFor(Player player, Player recipient) {
        if (player == null || recipient == null) {
            return player != null ? player.getName() : "[Unknown]";
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            YakPlayer recipientYakPlayer = playerManager.getPlayer(recipient);

            StringBuilder name = new StringBuilder();

            // Add guild tag if available
            if (yakPlayer != null && yakPlayer.isInGuild()) {
                String guildName = yakPlayer.getGuildName();
                if (guildName != null && !guildName.isEmpty()) {
                    name.append(ChatColor.WHITE).append("[").append(guildName).append("] ");
                }
            }

            // Add chat tag if not default
            ChatTag tag = playerTags.getOrDefault(player.getUniqueId(), ChatTag.DEFAULT);
            if (tag != ChatTag.DEFAULT) {
                String tagString = tag.getTag();
                if (tagString != null && !tagString.isEmpty()) {
                    name.append(tagString).append(" ");
                }
            }

            // Add rank if not default
            if (yakPlayer != null) {
                String rankString = yakPlayer.getRank();
                if (rankString != null && !rankString.isEmpty()) {
                    Rank rank = Rank.fromString(rankString);
                    if (rank != null && rank != Rank.DEFAULT) {
                        name.append(ChatColor.translateAlternateColorCodes('&', rank.tag)).append(" ");
                    }
                }
            }

            // Add player name with color based on alignment and buddy status
            ChatColor nameColor = getNameColorForRecipient(player, recipient);
            name.append(nameColor).append(player.getName());

            return name.toString();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error formatting name for recipient", e);
            return ChatColor.GRAY + player.getName();
        }
    }

    /**
     * Get the color for a player's name when viewed by a specific recipient
     * This accounts for buddy status and alignment
     *
     * @param player    The player whose name color to determine
     * @param recipient The player who will see the name
     * @return The ChatColor for the name
     */
    private ChatColor getNameColorForRecipient(Player player, Player recipient) {
        if (player == null || recipient == null) {
            return ChatColor.GRAY;
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            YakPlayer recipientYakPlayer = playerManager.getPlayer(recipient);

            // Check alignment first
            if (yakPlayer != null) {
                String alignment = yakPlayer.getAlignment();
                if ("CHAOTIC".equals(alignment)) {
                    return ChatColor.RED;
                } else if ("NEUTRAL".equals(alignment)) {
                    return ChatColor.YELLOW;
                }
            }

            // Check buddy status
            if (recipientYakPlayer != null && recipientYakPlayer.isBuddy(player.getName())) {
                return ChatColor.GREEN;
            }

            // Default
            return ChatColor.GRAY;
        } catch (Exception e) {
            return ChatColor.GRAY;
        }
    }

    /**
     * Get the color for a player's name based on alignment
     *
     * @param player The player
     * @return The ChatColor for the player's name
     */
    private ChatColor getNameColorByAlignment(Player player) {
        if (player == null) {
            return ChatColor.GRAY;
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                String alignment = yakPlayer.getAlignment();
                if (alignment != null) {
                    switch (alignment) {
                        case "NEUTRAL":
                            return ChatColor.YELLOW;
                        case "CHAOTIC":
                            return ChatColor.RED;
                        default:
                            return ChatColor.GRAY;
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }

        // Default to gray if no alignment data
        return ChatColor.GRAY;
    }

    /**
     * Check if a player is holding an item that can be shown
     * FIXED: Better validation
     *
     * @param player The player to check
     * @return true if the player is holding a valid item
     */
    private boolean isHoldingValidItem(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        try {
            ItemStack item = player.getInventory().getItemInMainHand();
            return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the player's current chat tag
     *
     * @param player The player to check
     * @return The player's chat tag
     */
    public static ChatTag getPlayerTag(Player player) {
        if (player == null) {
            return ChatTag.DEFAULT;
        }
        return playerTags.getOrDefault(player.getUniqueId(), ChatTag.DEFAULT);
    }

    /**
     * Set a player's chat tag
     *
     * @param player The player to update
     * @param tag    The new chat tag
     */
    public static void setPlayerTag(Player player, ChatTag tag) {
        if (player == null || tag == null) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();
            playerTags.put(uuid, tag);

            // Save to player data
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setChatTag(tag.name());
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().log(Level.WARNING, "Error setting chat tag", e);
        }
    }

    /**
     * Check if a player has a chat tag unlocked
     *
     * @param player The player to check
     * @param tag    The tag to check
     * @return true if the player has the tag unlocked
     */
    public static boolean hasTagUnlocked(Player player, ChatTag tag) {
        if (player == null || tag == null) {
            return false;
        }

        try {
            UUID uuid = player.getUniqueId();
            if (!unlockedPlayerTags.containsKey(uuid)) {
                unlockedPlayerTags.put(uuid, new ArrayList<>());
            }

            return unlockedPlayerTags.get(uuid).contains(tag.name());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Unlock a chat tag for a player
     *
     * @param player The player
     * @param tag    The tag to unlock
     */
    public static void unlockTag(Player player, ChatTag tag) {
        if (player == null || tag == null) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();
            if (!unlockedPlayerTags.containsKey(uuid)) {
                unlockedPlayerTags.put(uuid, new ArrayList<>());
            }

            if (!unlockedPlayerTags.get(uuid).contains(tag.name())) {
                unlockedPlayerTags.get(uuid).add(tag.name());
            }

            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.unlockChatTag(tag);
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().log(Level.WARNING, "Error unlocking chat tag", e);
        }
    }

    /**
     * Set a player's reply target
     *
     * @param sender The player sending the message
     * @param target The player to reply to
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
     * Get a player's reply target
     *
     * @param player The player
     * @return The reply target or null if none
     */
    public static Player getReplyTarget(Player player) {
        if (player == null) {
            return null;
        }

        Player target = replyTargets.get(player.getUniqueId());
        // Clean up if target is offline
        if (target != null && !target.isOnline()) {
            replyTargets.remove(player.getUniqueId());
            return null;
        }
        return target;
    }

    /**
     * Access to player tags map for compatibility
     *
     * @return The player tags map
     */
    public static Map<UUID, ChatTag> getPlayerTags() {
        return playerTags;
    }

    /**
     * Set a player's mute status
     *
     * @param player        The player to mute
     * @param timeInSeconds The duration of the mute in seconds, or 0 for permanent
     */
    public static void mutePlayer(Player player, int timeInSeconds) {
        if (player == null) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();
            mutedPlayers.put(uuid, timeInSeconds);

            // Update player data
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setMuteTime(timeInSeconds);
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            }

            // Notify player
            player.sendMessage(ChatColor.RED + "You have been muted");
            if (timeInSeconds > 0) {
                int minutes = timeInSeconds / 60;
                player.sendMessage(ChatColor.RED + "Your mute expires in " + minutes + " minutes.");
            } else {
                player.sendMessage(ChatColor.RED + "Your mute WILL NOT expire.");
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().log(Level.WARNING, "Error muting player", e);
        }
    }

    /**
     * Unmute a player
     *
     * @param player The player to unmute
     */
    public static void unmutePlayer(Player player) {
        if (player == null) {
            return;
        }

        try {
            UUID uuid = player.getUniqueId();
            mutedPlayers.remove(uuid);

            // Update player data
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setMuteTime(0);
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            }

            // Notify player
            player.sendMessage(ChatColor.GREEN + "You have been unmuted.");
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().log(Level.WARNING, "Error unmuting player", e);
        }
    }

    /**
     * Check if a player is muted
     *
     * @param player The player to check
     * @return true if the player is muted
     */
    public static boolean isMuted(Player player) {
        if (player == null) {
            return false;
        }
        return mutedPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Get a player's mute time
     *
     * @param player The player to check
     * @return The mute time in seconds, or 0 if not muted or permanent
     */
    public static int getMuteTime(Player player) {
        if (player == null) {
            return 0;
        }
        return mutedPlayers.getOrDefault(player.getUniqueId(), 0);
    }
}