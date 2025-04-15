package com.rednetty.server.utils.ui;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionBarUtil {

    // Existing fields
    private static final String RAW_DELIMITER = "&7-";
    private static final String DELIMITER = ChatColor.translateAlternateColorCodes('&', RAW_DELIMITER);
    private static final Map<UUID, List<ActionBarMessage>> messages = new ConcurrentHashMap<>();
    private static JavaPlugin plugin;

    // Initialize the utility (call this from your main plugin's onEnable)
    public static void init(JavaPlugin p) {
        plugin = p;
    }

    // Adds a standard action bar message for the player.
    public static void addMessage(Player player, String message) {
        ActionBarMessage abMessage = new ActionBarMessage(message, false, 0);
        addActionBarMessage(player, abMessage);
    }

    // Adds a countdown action bar message for the player.
    public static void addCountdownMessage(Player player, String baseMessage, int seconds) {
        ActionBarMessage abMessage = new ActionBarMessage(baseMessage, true, seconds);
        addActionBarMessage(player, abMessage);
        scheduleCountdown(player, abMessage);
    }

    // Clears any action bar messages for the player.
    public static void clearActionBar(Player player) {
        messages.remove(player.getUniqueId());
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    // **New Method**: Adds a temporary message that removes itself after a specified duration.
    public static void addTemporaryMessage(Player player, String message, long ticks) {
        ActionBarMessage abMessage = new ActionBarMessage(message, false, 0);
        addActionBarMessage(player, abMessage);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                removeActionBarMessage(player, abMessage);
            }
        }, ticks);
    }

    // Internal: Adds a message and updates the display.
    private static void addActionBarMessage(Player player, ActionBarMessage abMessage) {
        messages.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(abMessage);
        updateActionBar(player);
    }

    // Internal: Removes a message and updates the display.
    private static void removeActionBarMessage(Player player, ActionBarMessage abMessage) {
        List<ActionBarMessage> list = messages.get(player.getUniqueId());
        if (list != null) {
            list.remove(abMessage);
            if (list.isEmpty()) {
                messages.remove(player.getUniqueId());
            }
            updateActionBar(player);
        }
    }

    // Internal: Schedules the countdown update for countdown messages.
    private static void scheduleCountdown(Player player, ActionBarMessage abMessage) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (abMessage.remainingSeconds <= 0) {
                removeActionBarMessage(player, abMessage);
            } else {
                abMessage.remainingSeconds--;
                updateActionBar(player);
            }
        }, 20L, 20L);
        abMessage.task = task;
    }

    // Internal: Builds and sends the concatenated action bar message to the player.
    private static void updateActionBar(Player player) {
        List<ActionBarMessage> list = messages.get(player.getUniqueId());
        if (list == null || list.isEmpty()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            ActionBarMessage m = list.get(i);
            if (m.isCountdown) {
                sb.append(m.message).append(" ").append(m.remainingSeconds);
            } else {
                sb.append(m.message);
            }
            if (i < list.size() - 1) {
                sb.append(" ").append(DELIMITER).append(" ");
            }
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(sb.toString()));
    }

    // Internal class representing a single action bar message.
    private static class ActionBarMessage {
        String message;
        boolean isCountdown;
        int remainingSeconds;
        BukkitTask task;

        ActionBarMessage(String message, boolean isCountdown, int remainingSeconds) {
            this.message = message;
            this.isCountdown = isCountdown;
            this.remainingSeconds = remainingSeconds;
        }
    }
}