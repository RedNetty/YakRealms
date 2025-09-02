package com.rednetty.server.core.mechanics.world.mobs.behaviors;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages actionbar messages to prevent spam and ensure clean message updates
 */
public class ActionBarMessageManager {
    
    private static ActionBarMessageManager instance;
    
    // Track active messages and their cleanup tasks
    private final Map<UUID, BukkitTask> activeMessages = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentMessages = new ConcurrentHashMap<>();
    
    private ActionBarMessageManager() {}
    
    public static ActionBarMessageManager getInstance() {
        if (instance == null) {
            instance = new ActionBarMessageManager();
        }
        return instance;
    }
    
    /**
     * Send an actionbar message to a player, automatically clearing any previous message
     * 
     * @param player The player to send the message to
     * @param message The message to display
     * @param durationTicks How long to display the message (in ticks)
     */
    public void sendActionBarMessage(Player player, String message, int durationTicks) {
        sendActionBarMessage(player, Component.text(message), durationTicks);
    }
    
    /**
     * Send an actionbar message to a player, automatically clearing any previous message
     * 
     * @param player The player to send the message to
     * @param message The message component to display
     * @param durationTicks How long to display the message (in ticks)
     */
    public void sendActionBarMessage(Player player, Component message, int durationTicks) {
        if (player == null || !player.isOnline()) return;
        
        UUID playerId = player.getUniqueId();
        
        // Cancel any existing message for this player
        clearMessage(playerId);
        
        // Send the new message
        player.sendActionBar(message);
        currentMessages.put(playerId, message.toString());
        
        // Schedule message cleanup
        BukkitTask clearTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.sendActionBar(Component.empty());
                }
                currentMessages.remove(playerId);
                activeMessages.remove(playerId);
            }
        }.runTaskLater(com.rednetty.server.YakRealms.getInstance(), durationTicks);
        
        activeMessages.put(playerId, clearTask);
    }
    
    /**
     * Immediately clear the actionbar message for a player
     */
    public void clearMessage(UUID playerId) {
        BukkitTask existingTask = activeMessages.get(playerId);
        if (existingTask != null) {
            existingTask.cancel();
            activeMessages.remove(playerId);
        }
        currentMessages.remove(playerId);
    }
    
    /**
     * Check if a player has an active actionbar message
     */
    public boolean hasActiveMessage(UUID playerId) {
        return activeMessages.containsKey(playerId);
    }
    
    /**
     * Get the current message for a player (if any)
     */
    public String getCurrentMessage(UUID playerId) {
        return currentMessages.get(playerId);
    }
    
    /**
     * Clear all active messages (for shutdown)
     */
    public void clearAll() {
        activeMessages.values().forEach(BukkitTask::cancel);
        activeMessages.clear();
        currentMessages.clear();
    }
}