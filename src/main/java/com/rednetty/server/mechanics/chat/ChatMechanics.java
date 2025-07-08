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
 *  Improved error handling, logic fixes, and item display functionality
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
            logger.info("Chat system compatibility: " + getCompatibilityInfo());
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
     *  Better error handling and validation
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
     *  Better command filtering logic
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
     *  Better validation and error handling
     *
     * @param player  The player sending the message
     * @param message The message text
     */
    public void processChat(Player player, String message) {
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
     *  Better error handling, validation, and fallback
     *
     * @param player  The player sending the message
     * @param message The message text containing @i@ for item placement
     */
    private void sendItemMessage(Player player, String message) {
        try {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isHoldingValidItem(player)) {
                // Better fallback message
                String fallbackMessage = message.replace("@i@", ChatColor.RED + "[No Item]" + ChatColor.WHITE);
                sendNormalMessage(player, fallbackMessage);
                player.sendMessage(ChatColor.GRAY + "Hold an item in your main hand to display it in chat.");
                return;
            }

            String formattedName = getFormattedName(player);

            // Log for debugging
            logger.info("Player " + player.getName() + " is showing item: " + item.getType().name() +
                    " with message: " + message);

            // Try modern item display first, fall back to simple display if it fails
            if (supportsModernChat()) {
                // Send to self with item hover
                boolean selfSuccess = sendItemHoverMessage(player, item, formattedName, message, player);
                if (!selfSuccess) {
                    logger.warning("Failed to send item hover message to self for " + player.getName());
                    sendItemMessageFallback(player, message);
                    return;
                }

                // Send to nearby players
                List<Player> recipients = getNearbyPlayers(player);

                if (recipients.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY.toString() + ChatColor.ITALIC + "No one heard you.");
                } else {
                    int successCount = 0;
                    for (Player recipient : recipients) {
                        if (recipient != null && recipient.isOnline()) {
                            try {
                                String recipientSpecificName = getFormattedNameFor(player, recipient);
                                boolean success = sendItemHoverMessage(player, item, recipientSpecificName, message, recipient);
                                if (success) {
                                    successCount++;
                                } else {
                                    // Fallback to normal message with item name
                                    String fallbackMessage = message.replace("@i@",
                                            ChatColor.YELLOW + "[" + getItemDisplayName(item) + "]" + ChatColor.WHITE);
                                    recipient.sendMessage(recipientSpecificName + ChatColor.WHITE + ": " + fallbackMessage);
                                }
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Error sending item message to " + recipient.getName(), e);
                                // Fallback to normal message
                                String recipientSpecificName = getFormattedNameFor(player, recipient);
                                String fallbackMessage = message.replace("@i@", "[" + item.getType().name() + "]");
                                recipient.sendMessage(recipientSpecificName + ChatColor.WHITE + ": " + fallbackMessage);
                            }
                        }
                    }

                    logger.info("Successfully sent item message to " + successCount + "/" + recipients.size() + " recipients");
                }

                // Send to vanished staff
                sendToVanishedStaff(player, formattedName, message);
            } else {
                // Use fallback method for servers without modern chat support
                sendItemMessageFallback(player, message);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending item message for " + player.getName(), e);
            // Fallback to normal message
            String fallbackMessage = message.replace("@i@", ChatColor.RED + "[ITEM ERROR]" + ChatColor.WHITE);
            sendNormalMessage(player, fallbackMessage);
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
                            if (supportsModernChat()) {
                                sendItemHoverMessage(player, item, prefix, message, staff);
                            } else {
                                String fallbackMessage = message.replace("@i@",
                                        ChatColor.YELLOW + "[" + getItemDisplayName(item) + "]" + ChatColor.WHITE);
                                staff.sendMessage(prefix + ChatColor.WHITE + ": " + fallbackMessage);
                            }
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
     *  Better validation, error handling, and success tracking
     *
     * @param sender    The player sending the message
     * @param item      The item to display
     * @param prefix    The sender's prefix/name
     * @param message   The message text
     * @param recipient The player to send to
     * @return true if the message was sent successfully
     */
    private boolean sendItemHoverMessage(Player sender, ItemStack item, String prefix,
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
            String baseMessage = prefix + ChatColor.WHITE + ": " + before;

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
     * Get a list of text to display when hovering over an item
     *  Better handling of item meta and null checks (no enchantments)
     *
     * @param item The item to get hover text for
     * @return List of hover text lines
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
     *  Better null handling
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
     *  More thorough validation
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
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                return false;
            }

            // Additional check: ensure item type is not null (shouldn't happen but just in case)
            return item.getType() != null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking if player is holding valid item", e);
            return false;
        }
    }

    /**
     *  Get proper display name for item
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
     * Format item display with proper bracket colors
     *  Matches bracket colors to item name color
     *
     * @param itemDisplayName The item's display name (may contain color codes)
     * @return Formatted display with proper bracket colors
     */
    private String formatItemDisplay(String itemDisplayName) {
        if (itemDisplayName == null || itemDisplayName.isEmpty()) {
            return ChatColor.YELLOW + "[Unknown]";
        }

        // Check if the item name contains color codes
        if (itemDisplayName.contains("§") || itemDisplayName.contains(ChatColor.COLOR_CHAR + "")) {
            // Extract the first color from the item name
            String firstColor = extractFirstColor(itemDisplayName);
            if (firstColor != null) {
                // Use the item's color for brackets too
                return firstColor + "[" + itemDisplayName + firstColor + "]" + ChatColor.WHITE;
            }
        }

        // Default: yellow brackets for items without custom colors
        return ChatColor.YELLOW + "[" + itemDisplayName + "]" + ChatColor.WHITE;
    }

    /**
     * Extract the first color code from a string
     *
     * @param text The text to check
     * @return The first color code found, or null if none
     */
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
                    // Skip formatting codes (k, l, m, n, o, r) and continue looking
                    default:
                        continue;
                }
            }
        }

        return null;
    }

    /**
     *  Fallback item name formatting
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
     * Alternative item display method using plain text formatting
     * Use this as a fallback if JSON components don't work
     */
    private void sendItemMessageFallback(Player player, String message) {
        try {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isHoldingValidItem(player)) {
                String fallbackMessage = message.replace("@i@", ChatColor.RED + "[No Item]" + ChatColor.WHITE);
                sendNormalMessage(player, fallbackMessage);
                return;
            }

            String formattedName = getFormattedName(player);

            // Create a detailed item description with proper color matching
            StringBuilder itemDesc = new StringBuilder();
            String itemText = formatItemDisplay(getItemDisplayName(item));
            itemDesc.append(itemText);

            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchants()) {
                itemDesc.append(ChatColor.LIGHT_PURPLE).append("*"); // Indicate enchanted
            }

            if (item.getAmount() > 1) {
                itemDesc.append(ChatColor.GRAY).append(" x").append(item.getAmount());
            }

            String finalMessage = message.replace("@i@", itemDesc.toString() + ChatColor.WHITE);

            // Send to self
            player.sendMessage(formattedName + ChatColor.WHITE + ": " + finalMessage);

            // Send item details in a separate message
            player.sendMessage(ChatColor.GRAY + "Item Details: " + ChatColor.WHITE +
                    item.getType().name() +
                    (meta != null && meta.hasDisplayName() ? " (" + meta.getDisplayName() + ")" : ""));

            // Send to nearby players
            List<Player> recipients = getNearbyPlayers(player);
            if (recipients.isEmpty()) {
                player.sendMessage(ChatColor.GRAY.toString() + ChatColor.ITALIC + "No one heard you.");
            } else {
                for (Player recipient : recipients) {
                    if (recipient != null && recipient.isOnline()) {
                        String recipientSpecificName = getFormattedNameFor(player, recipient);
                        recipient.sendMessage(recipientSpecificName + ChatColor.WHITE + ": " + finalMessage);
                    }
                }
            }

            // Send to vanished staff
            sendToVanishedStaff(player, formattedName, finalMessage);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in fallback item message", e);
            sendNormalMessage(player, message.replace("@i@", "[ITEM]"));
        }
    }

    /**
     * Check if the server supports modern chat features
     */
    public static boolean supportsModernChat() {
        try {
            // Check if BungeeCord chat classes are available
            Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class.forName("net.md_5.bungee.api.chat.HoverEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Get server compatibility information
     */
    public static String getCompatibilityInfo() {
        StringBuilder info = new StringBuilder();

        info.append("Server: ").append(Bukkit.getName()).append(" ")
                .append(Bukkit.getVersion()).append(" | ");

        info.append("Bukkit Version: ").append(Bukkit.getBukkitVersion()).append(" | ");

        info.append("Modern Chat Support: ").append(supportsModernChat() ? "Yes" : "No");

        return info.toString();
    }

    /**
     * Test method to verify item display functionality
     * Add this as a command: /testitemchat
     */
    public static void testItemDisplay(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            YakRealms.getInstance().getLogger().info("Testing item display for " + player.getName());

            // Check if player is holding an item
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Hold an item in your main hand to test item display.");
                return;
            }

            player.sendMessage(ChatColor.GREEN + "Testing item display functionality...");

            // Test 1: Basic item info
            player.sendMessage(ChatColor.YELLOW + "Item Type: " + item.getType().name());
            player.sendMessage(ChatColor.YELLOW + "Item Amount: " + item.getAmount());

            // Test 2: Check if BungeeCord Chat API is available
            if (supportsModernChat()) {
                player.sendMessage(ChatColor.GREEN + "✓ BungeeCord Chat API is available");
            } else {
                player.sendMessage(ChatColor.RED + "✗ BungeeCord Chat API is NOT available - using fallback mode");
            }

            // Test 3: Try sending a simple hover message
            try {
                JsonChatComponent testComponent = new JsonChatComponent(ChatColor.BLUE + "Hover over this text!");
                testComponent.addHoverText(ChatColor.YELLOW + " [HOVER TEST] ", "This is a hover test message");
                testComponent.send(player);
                player.sendMessage(ChatColor.GREEN + "✓ Basic hover message sent");
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "✗ Failed to send hover message: " + e.getMessage());
                YakRealms.getInstance().getLogger().warning("Failed to send hover message: " + e.getMessage());
            }

            // Test 4: Try item display
            try {
                getInstance().processChat(player, "Testing item display @i@ here!");
                player.sendMessage(ChatColor.GREEN + "✓ Item display test completed");
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "✗ Item display test failed: " + e.getMessage());
                YakRealms.getInstance().getLogger().warning("Item display test failed: " + e.getMessage());
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error during testing: " + e.getMessage());
            YakRealms.getInstance().getLogger().log(Level.SEVERE, "Error during item display testing", e);
        }
    }

    /**
     * Debug method to check chat system status
     */
    public static void debugChatSystem(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Chat System Debug Info ===");

        // Server version info
        String version = Bukkit.getVersion();
        String bukkitVersion = Bukkit.getBukkitVersion();
        player.sendMessage(ChatColor.YELLOW + "Server Version: " + ChatColor.WHITE + version);
        player.sendMessage(ChatColor.YELLOW + "Bukkit Version: " + ChatColor.WHITE + bukkitVersion);

        // Check if Spigot methods are available
        try {
            player.spigot().sendMessage(new net.md_5.bungee.api.chat.TextComponent("Spigot test"));
            player.sendMessage(ChatColor.GREEN + "✓ Spigot API available");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✗ Spigot API not available: " + e.getMessage());
        }

        // Chat mechanics status
        ChatMechanics mechanics = getInstance();
        if (mechanics != null) {
            player.sendMessage(ChatColor.GREEN + "✓ ChatMechanics instance active");

            // Player data status
            ChatTag tag = getPlayerTag(player);
            player.sendMessage(ChatColor.YELLOW + "Current Chat Tag: " + ChatColor.WHITE + tag.name());

            boolean muted = isMuted(player);
            player.sendMessage(ChatColor.YELLOW + "Muted: " + ChatColor.WHITE + (muted ? "Yes" : "No"));

            // Cooldown status
            boolean hasCooldown = chatCooldown.containsKey(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Chat Cooldown Active: " + ChatColor.WHITE + (hasCooldown ? "Yes" : "No"));

        } else {
            player.sendMessage(ChatColor.RED + "✗ ChatMechanics instance not found");
        }

        player.sendMessage(ChatColor.GOLD + "=== End Debug Info ===");
    }

    // Static utility methods for external access

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

    /**
     * PUBLIC METHOD: Send an item message to a specific player
     * This method can be called from other commands like GlobalChatCommand and PartyCommand
     *
     * @param sender    The player sending the message
     * @param item      The item to display
     * @param prefix    The message prefix (including sender name)
     * @param message   The message text containing @i@
     * @param recipient The player to receive the message
     * @return true if the message was sent successfully
     */
    public static boolean sendItemMessageToPlayer(Player sender, ItemStack item, String prefix, String message, Player recipient) {
        if (getInstance() != null) {
            return getInstance().sendItemHoverMessage(sender, item, prefix, message, recipient);
        }
        return false;
    }

    /**
     * PUBLIC METHOD: Send an item message to multiple players
     * This method can be called from other commands for broadcasting
     *
     * @param sender     The player sending the message
     * @param item       The item to display
     * @param prefix     The message prefix (including sender name)
     * @param message    The message text containing @i@
     * @param recipients List of players to receive the message
     * @return number of successful sends
     */
    public static int sendItemMessageToPlayers(Player sender, ItemStack item, String prefix, String message, List<Player> recipients) {
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
                        // Fallback to normal message with item name using proper color formatting
                        String itemName = instance.getItemDisplayName(item);
                        String itemDisplay = instance.formatItemDisplay(itemName);
                        String fallbackMessage = message.replace("@i@", itemDisplay);
                        recipient.sendMessage(prefix + ChatColor.WHITE + ": " + fallbackMessage);
                        successCount++; // Count fallback as success
                    }
                } catch (Exception e) {
                    instance.logger.log(Level.WARNING, "Error sending item message to " + recipient.getName(), e);
                    // Final fallback
                    String fallbackMessage = message.replace("@i@", "[ITEM]");
                    recipient.sendMessage(prefix + ChatColor.WHITE + ": " + fallbackMessage);
                    successCount++; // Count fallback as success
                }
            }
        }

        return successCount;
    }

    /**
     * PUBLIC METHOD: Check if a player is holding a valid item for display
     *
     * @param player The player to check
     * @return true if the player is holding a valid item
     */
    public static boolean isPlayerHoldingValidItem(Player player) {
        if (getInstance() != null) {
            return getInstance().isHoldingValidItem(player);
        }
        return false;
    }

    /**
     * PUBLIC METHOD: Get the display name of an item
     *
     * @param item The item
     * @return The formatted display name
     */
    public static String getDisplayNameForItem(ItemStack item) {
        if (getInstance() != null) {
            return getInstance().getItemDisplayName(item);
        }
        return item != null ? item.getType().name() : "Unknown";
    }
}