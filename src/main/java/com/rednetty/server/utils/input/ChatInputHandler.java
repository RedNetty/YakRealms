package com.rednetty.server.utils.input;

import com.rednetty.server.YakRealms;
import com.rednetty.server.utils.messaging.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Shared chat input handler utility for handling user input across different systems
 * Based on the Adventure API pattern from MarketManager
 */
public class ChatInputHandler implements Listener {
    private static ChatInputHandler instance;
    private final Map<UUID, ChatInputContext> chatInputContexts = new ConcurrentHashMap<>();
    
    private ChatInputHandler() {
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
    }
    
    public static ChatInputHandler getInstance() {
        if (instance == null) {
            synchronized (ChatInputHandler.class) {
                if (instance == null) {
                    instance = new ChatInputHandler();
                }
            }
        }
        return instance;
    }
    
    /**
     * Starts a chat input session for a player
     */
    public void startInput(Player player, String prompt, Consumer<String> callback) {
        startInput(player, prompt, callback, null);
    }
    
    /**
     * Starts a chat input session for a player with cancellation callback
     */
    public void startInput(Player player, String prompt, Consumer<String> callback, Runnable onCancel) {
        ChatInputContext context = new ChatInputContext(callback, onCancel, System.currentTimeMillis());
        chatInputContexts.put(player.getUniqueId(), context);
        
        player.closeInventory();
        player.sendMessage(Component.empty());
        MessageUtils.send(player, "<yellow>💬 " + prompt);
        player.sendMessage(Component.empty());
        MessageUtils.send(player, "<gray>Type <red>'cancel'</red> to cancel");
    }
    
    /**
     * Starts a search input session
     */
    public void startSearchInput(Player player, String searchType, Consumer<String> callback) {
        startSearchInput(player, searchType, callback, null);
    }
    
    /**
     * Starts a search input session with cancellation callback
     */
    public void startSearchInput(Player player, String searchType, Consumer<String> callback, Runnable onCancel) {
        ChatInputContext context = new ChatInputContext(callback, onCancel, System.currentTimeMillis());
        chatInputContexts.put(player.getUniqueId(), context);
        
        player.closeInventory();
        player.sendMessage(Component.empty());
        MessageUtils.send(player, "<yellow>🔍 " + searchType);
        player.sendMessage(Component.empty());
        MessageUtils.send(player, "<gray>Enter your search term:");
        MessageUtils.send(player, "<gray>Type <red>'cancel'</red> to cancel");
    }
    
    /**
     * Starts a confirmation input session
     */
    public void startConfirmationInput(Player player, String action, String details, Runnable onConfirm) {
        startConfirmationInput(player, action, details, onConfirm, null);
    }
    
    /**
     * Starts a confirmation input session with cancellation callback
     */
    public void startConfirmationInput(Player player, String action, String details, Runnable onConfirm, Runnable onCancel) {
        Consumer<String> callback = (input) -> {
            if (input.equalsIgnoreCase("confirm") || input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y")) {
                onConfirm.run();
            } else if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("no") || input.equalsIgnoreCase("n")) {
                if (onCancel != null) {
                    onCancel.run();
                } else {
                    MessageUtils.send(player, "<red>❌ " + action + " cancelled.");
                }
            } else {
                MessageUtils.send(player, "<yellow>Please type 'confirm' or 'cancel'");
                // Re-prompt
                startConfirmationInput(player, action, details, onConfirm, onCancel);
            }
        };
        
        ChatInputContext context = new ChatInputContext(callback, onCancel, System.currentTimeMillis());
        chatInputContexts.put(player.getUniqueId(), context);
        
        player.closeInventory();
        player.sendMessage(Component.empty());
        MessageUtils.send(player, "<yellow>⚠️ Confirm " + action);
        player.sendMessage(Component.empty());
        if (details != null && !details.isEmpty()) {
            MessageUtils.send(player, "<gray>" + details);
            player.sendMessage(Component.empty());
        }
        MessageUtils.send(player, "<gray>Type <green>'confirm'</green> to proceed or <red>'cancel'</red> to abort");
    }
    
    /**
     * Checks if a player is currently in a chat input session
     */
    public boolean isWaitingForInput(Player player) {
        return chatInputContexts.containsKey(player.getUniqueId());
    }
    
    /**
     * Cancels any active input session for a player
     */
    public void cancelInput(Player player) {
        ChatInputContext context = chatInputContexts.remove(player.getUniqueId());
        if (context != null && context.getOnCancel() != null) {
            context.getOnCancel().run();
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        ChatInputContext context = chatInputContexts.get(playerId);
        if (context == null) {
            return; // Not waiting for input from this player
        }
        
        // Cancel the chat event to prevent public message
        event.setCancelled(true);
        
        String message = event.getMessage().trim();
        
        // Handle cancellation
        if (message.equalsIgnoreCase("cancel")) {
            chatInputContexts.remove(playerId);
            
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                MessageUtils.send(player, "<red>❌ Input cancelled.");
                if (context.getOnCancel() != null) {
                    context.getOnCancel().run();
                }
            });
            return;
        }
        
        // Remove context before processing to prevent duplicate handling
        chatInputContexts.remove(playerId);
        
        // Process input on main thread
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            try {
                context.getCallback().accept(message);
            } catch (Exception e) {
                MessageUtils.send(player, "<red>❌ Error processing input: " + e.getMessage());
                if (context.getOnCancel() != null) {
                    context.getOnCancel().run();
                }
            }
        });
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        chatInputContexts.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * Clean up expired contexts (called periodically)
     */
    public void cleanupExpired() {
        long currentTime = System.currentTimeMillis();
        chatInputContexts.entrySet().removeIf(entry -> {
            ChatInputContext context = entry.getValue();
            return currentTime - context.getStartTime() > 60000; // 1 minute timeout
        });
    }
    
    /**
     * Context for tracking chat input state
     */
    private static class ChatInputContext {
        private final Consumer<String> callback;
        private final Runnable onCancel;
        private final long startTime;
        
        public ChatInputContext(Consumer<String> callback, Runnable onCancel, long startTime) {
            this.callback = callback;
            this.onCancel = onCancel;
            this.startTime = startTime;
        }
        
        public Consumer<String> getCallback() { return callback; }
        public Runnable getOnCancel() { return onCancel; }
        public long getStartTime() { return startTime; }
    }
}