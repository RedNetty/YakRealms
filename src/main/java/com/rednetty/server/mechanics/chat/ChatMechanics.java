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

    // Chat filter
    private static final List<String> bannedWords = new ArrayList<>(Arrays.asList(
            "nigger"
            // Add other banned words here
    ));
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

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
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        // Save all chat tags back to player data
        saveAllChatTags();

        // Save muted players data
        saveMutedPlayers();

        chatCooldown.clear();
        replyTargets.clear();
        mutedPlayers.clear();

        YakRealms.log("ChatMechanics has been disabled.");
    }

    /**
     * Start the task that manages chat cooldowns
     */
    private void startCooldownTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
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
                            if (player != null) {
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
                        if (player != null) {
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
        for (Map.Entry<UUID, ChatTag> entry : playerTags.entrySet()) {
            YakPlayer player = playerManager.getPlayer(entry.getKey());
            if (player != null) {
                player.setChatTag(entry.getValue().name());
                playerManager.savePlayer(player);
            }
        }
    }

    /**
     * Handler for player join to load chat tags
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadPlayerChatTag(player);

        // Load mute status if exists
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null && yakPlayer.isMuted()) {
            mutedPlayers.put(player.getUniqueId(), yakPlayer.getMuteTime());
        }
    }

    /**
     * Handler for player quit to save chat tags
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
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
    }

    /**
     * Load a player's chat tag from their data
     *
     * @param player The player to load for
     */
    private void loadPlayerChatTag(Player player) {
        UUID uuid = player.getUniqueId();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer != null) {
            // Load chat tag
            try {
                ChatTag tag = ChatTag.valueOf(yakPlayer.getChatTag());
                playerTags.put(uuid, tag);
            } catch (IllegalArgumentException e) {
                playerTags.put(uuid, ChatTag.DEFAULT);
                yakPlayer.setChatTag(ChatTag.DEFAULT.name());
            }

            // Load unlocked chat tags
            List<String> unlockedTags = new ArrayList<>();
            for (String tagName : yakPlayer.getUnlockedChatTags()) {
                unlockedTags.add(tagName);
            }
            unlockedPlayerTags.put(uuid, unlockedTags);

            playerManager.savePlayer(yakPlayer);
        } else {
            // New player
            playerTags.put(uuid, ChatTag.DEFAULT);
            unlockedPlayerTags.put(uuid, new ArrayList<>());
        }
    }

    /**
     * Filter main chat to handle command execution via chat
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // If message starts with "//", it's a chat message, not a command
        if (command.startsWith("//")) {
            event.setCancelled(true);
            // Process as chat, removing the //
            processChat(player, command.substring(2));
            return;
        }

        // Check restricted commands
        if (!player.isOp() && !ModerationMechanics.isStaff(player)) {
            // Extract command name
            String cmdName = command.startsWith("/") ? command.substring(1) : command;
            if (cmdName.contains(" ")) {
                cmdName = cmdName.split(" ")[0];
            }

            // List of restricted commands
            List<String> restrictedCommands = Arrays.asList(
                    "save-all", "stack", "stop", "restart", "reload", "tpall", "kill", "mute"
            );

            if (restrictedCommands.contains(cmdName)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.WHITE + "Unknown command. View your Character Journal's Index for a list of commands.");
                return;
            }

            // Handle allowed commands by checking permission or a whitelist
            // (This is simplified from the original - would need more complete implementation)
        }

        // If player tries to use /gl or /g without permissions, convert to chat
        if ((command.startsWith("/gl ") || command.startsWith("/g ")) &&
                !player.hasPermission("yakrealms.guild.chat")) {
            event.setCancelled(true);
            processChat(player, command.substring(command.indexOf(' ') + 1));
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

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Cancel the default Bukkit chat behavior
        event.setCancelled(true);

        // Process the chat message
        processChat(player, message);
    }

    /**
     * Process a chat message from a player
     *
     * @param player  The player sending the message
     * @param message The message text
     */
    private void processChat(Player player, String message) {
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

        // Check for chat filters
        message = filterMessage(message);

        // Check if showing an item
        if (message.contains("@i@") && isHoldingItem(player)) {
            sendItemMessage(player, message);
        } else {
            sendNormalMessage(player, message);
        }

        // Log chat message
        logger.info(player.getName() + ": " + message);
    }

    /**
     * Send a normal chat message (without item display)
     *
     * @param player  The player sending the message
     * @param message The message text
     */
    private void sendNormalMessage(Player player, String message) {
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
                String recipientSpecificName = getFormattedNameFor(player, recipient);
                recipient.sendMessage(recipientSpecificName + ChatColor.WHITE + ": " + message);
            }
        }

        // Send to vanished staff
        sendToVanishedStaff(player, formattedName, message);
    }

    /**
     * Send a message with an item showcase
     *
     * @param player  The player sending the message
     * @param message The message text containing @i@ for item placement
     */
    private void sendItemMessage(Player player, String message) {
        ItemStack item = player.getInventory().getItemInMainHand();
        String formattedName = getFormattedName(player);

        // Send to self with item hover
        sendItemHoverMessage(player, item, formattedName, message, player);

        // Send to nearby players
        List<Player> recipients = getNearbyPlayers(player);

        if (recipients.isEmpty()) {
            player.sendMessage(ChatColor.GRAY.toString() + ChatColor.ITALIC + "No one heard you.");
        } else {
            for (Player recipient : recipients) {
                String recipientSpecificName = getFormattedNameFor(player, recipient);
                sendItemHoverMessage(player, item, recipientSpecificName, message, recipient);
            }
        }

        // Send to vanished staff
        sendToVanishedStaff(player, formattedName, message);
    }

    /**
     * Send a message to vanished staff members
     *
     * @param player  The player who sent the message
     * @param prefix  The player's prefix/name
     * @param message The message text
     */
    private void sendToVanishedStaff(Player player, String prefix, String message) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakrealms.vanish") &&
                    staff.hasMetadata("vanished") &&
                    staff != player) {

                if (message.contains("@i@") && isHoldingItem(player)) {
                    sendItemHoverMessage(player, player.getInventory().getItemInMainHand(),
                            prefix, message, staff);
                } else {
                    staff.sendMessage(prefix + ChatColor.WHITE + ": " + message);
                }
            }
        }
    }

    /**
     * Send an item hover message to a player
     *
     * @param sender    The player sending the message
     * @param item      The item to display
     * @param prefix    The sender's prefix/name
     * @param message   The message text
     * @param recipient The player to send to
     */
    private void sendItemHoverMessage(Player sender, ItemStack item, String prefix,
                                      String message, Player recipient) {
        String[] parts = message.split("@i@");
        String before = parts.length > 0 ? parts[0] : "";
        String after = parts.length > 1 ? parts[1] : "";

        // Create JSON component with hover
        JsonChatComponent component = new JsonChatComponent(prefix + ": " + ChatColor.WHITE + before);

        // Add hover text from item lore
        List<String> hoverText = getItemHoverText(item);
        component.addHoverItem("[SHOW]", hoverText);

        // Add rest of message
        component.addText(ChatColor.WHITE + after);

        // Send to recipient
        component.send(recipient);
    }

    /**
     * Get a list of text to display when hovering over an item
     *
     * @param item The item to get hover text for
     * @return List of hover text lines
     */
    private List<String> getItemHoverText(ItemStack item) {
        List<String> text = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();

        // Add item name
        if (meta != null && meta.hasDisplayName()) {
            text.add(meta.getDisplayName());
        } else {
            text.add(TextUtil.formatItemName(item.getType().name()));
        }

        // Add lore
        if (meta != null && meta.hasLore()) {
            text.addAll(meta.getLore());
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
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != player &&
                    other.getWorld() == player.getWorld() &&
                    other.getLocation().distance(player.getLocation()) < DEFAULT_CHAT_RANGE &&
                    !isVanished(other)) {

                nearbyPlayers.add(other);
            }
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
        return player.hasMetadata("vanished");
    }

    /**
     * Filter a chat message for inappropriate content
     *
     * @param message The message to filter
     * @return The filtered message
     */
    private String filterMessage(String message) {
        // Filter banned words
        for (String word : bannedWords) {
            message = message.replaceAll("(?i)" + word, "*****");
        }

        // Filter IP addresses
        message = IP_PATTERN.matcher(message).replaceAll("***.***.***.**");

        return message.trim();
    }

    /**
     * Get a player's formatted name for chat
     *
     * @param player The player
     * @return The formatted name with rank and tag
     */
    private String getFormattedName(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            return yakPlayer.getFormattedDisplayName();
        }

        // Fallback if YakPlayer not available
        StringBuilder name = new StringBuilder();

        // Add guild tag if available
        if (yakPlayer != null && yakPlayer.isInGuild()) {
            name.append(ChatColor.WHITE).append("[").append(yakPlayer.getGuildName()).append("] ");
        }

        // Add chat tag if not default
        ChatTag tag = playerTags.getOrDefault(player.getUniqueId(), ChatTag.DEFAULT);
        if (tag != ChatTag.DEFAULT) {
            name.append(tag.getTag()).append(" ");
        }

        // Add rank if not default
        Rank rank = Rank.fromString(yakPlayer.getRank());
        if (rank != Rank.DEFAULT) {
            name.append(ChatColor.translateAlternateColorCodes('&', rank.tag)).append(" ");
        }


        // Add player name
        name.append(getNameColorByAlignment(player)).append(player.getName());

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
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        YakPlayer recipientYakPlayer = playerManager.getPlayer(recipient);

        StringBuilder name = new StringBuilder();

        // Add guild tag if available
        if (yakPlayer != null && yakPlayer.isInGuild()) {
            name.append(ChatColor.WHITE).append("[").append(yakPlayer.getGuildName()).append("] ");
        }

        // Add chat tag if not default
        ChatTag tag = playerTags.getOrDefault(player.getUniqueId(), ChatTag.DEFAULT);
        if (tag != ChatTag.DEFAULT) {
            name.append(tag.getTag()).append(" ");
        }

        // Add rank if not default
        Rank rank = Rank.fromString(yakPlayer.getRank());
        if (rank != Rank.DEFAULT) {
            name.append(ChatColor.translateAlternateColorCodes('&', rank.tag)).append(" ");
        }

        // Add player name with color based on alignment and buddy status
        ChatColor nameColor = getNameColorForRecipient(player, recipient);
        name.append(nameColor).append(player.getName());

        return name.toString();
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
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        YakPlayer recipientYakPlayer = playerManager.getPlayer(recipient);

        // Check alignment first
        if (yakPlayer != null) {
            if (yakPlayer.getAlignment().equals("CHAOTIC")) {
                return ChatColor.RED;
            } else if (yakPlayer.getAlignment().equals("NEUTRAL")) {
                return ChatColor.YELLOW;
            }
        }

        // Check buddy status
        if (recipientYakPlayer != null && recipientYakPlayer.isBuddy(player.getName())) {
            return ChatColor.GREEN;
        }

        // Default
        return ChatColor.GRAY;
    }

    /**
     * Get the color for a player's name based on alignment
     *
     * @param player The player
     * @return The ChatColor for the player's name
     */
    private ChatColor getNameColorByAlignment(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            switch (yakPlayer.getAlignment()) {
                case "NEUTRAL":
                    return ChatColor.YELLOW;
                case "CHAOTIC":
                    return ChatColor.RED;
                default:
                    return ChatColor.GRAY;
            }
        }

        // Default to gray if no alignment data
        return ChatColor.GRAY;
    }

    /**
     * Check if a player is holding an item that can be shown
     *
     * @param player The player to check
     * @return true if the player is holding a valid item
     */
    private boolean isHoldingItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item != null && item.getType() != Material.AIR;
    }

    /**
     * Get the player's current chat tag
     *
     * @param player The player to check
     * @return The player's chat tag
     */
    public static ChatTag getPlayerTag(Player player) {
        return playerTags.getOrDefault(player.getUniqueId(), ChatTag.DEFAULT);
    }

    /**
     * Set a player's chat tag
     *
     * @param player The player to update
     * @param tag    The new chat tag
     */
    public static void setPlayerTag(Player player, ChatTag tag) {
        UUID uuid = player.getUniqueId();
        playerTags.put(uuid, tag);

        // Save to player data
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setChatTag(tag.name());
            YakPlayerManager.getInstance().savePlayer(yakPlayer);
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
        UUID uuid = player.getUniqueId();
        if (!unlockedPlayerTags.containsKey(uuid)) {
            unlockedPlayerTags.put(uuid, new ArrayList<>());
        }

        return unlockedPlayerTags.get(uuid).contains(tag.name());
    }

    /**
     * Unlock a chat tag for a player
     *
     * @param player The player
     * @param tag    The tag to unlock
     */
    public static void unlockTag(Player player, ChatTag tag) {
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
    }

    /**
     * Set a player's reply target
     *
     * @param sender The player sending the message
     * @param target The player to reply to
     */
    public static void setReplyTarget(Player sender, Player target) {
        replyTargets.put(sender.getUniqueId(), target);
    }

    /**
     * Get a player's reply target
     *
     * @param player The player
     * @return The reply target or null if none
     */
    public static Player getReplyTarget(Player player) {
        return replyTargets.get(player.getUniqueId());
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
    }

    /**
     * Unmute a player
     *
     * @param player The player to unmute
     */
    public static void unmutePlayer(Player player) {
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
    }

    /**
     * Check if a player is muted
     *
     * @param player The player to check
     * @return true if the player is muted
     */
    public static boolean isMuted(Player player) {
        return mutedPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Get a player's mute time
     *
     * @param player The player to check
     * @return The mute time in seconds, or 0 if not muted or permanent
     */
    public static int getMuteTime(Player player) {
        return mutedPlayers.getOrDefault(player.getUniqueId(), 0);
    }
}