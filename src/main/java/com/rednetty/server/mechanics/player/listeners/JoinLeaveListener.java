package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.stream.IntStream;

/**
 * Handles player join and leave events with customized messaging,
 * combat protection, and initial setup.
 */
public class JoinLeaveListener extends BaseListener {

    private final YakPlayerManager playerManager;
    private final HealthListener healthListener;

    public JoinLeaveListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
        this.healthListener = new HealthListener(); // For health calculation
    }

    @Override
    public void initialize() {
        logger.info("Join/Leave listener initialized");
    }

    /**
     * Check for server lockdown before player login
     */
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (isPatchLockdown()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "The server is in the middle of deploying a patch. Please join in a few seconds.");
        }
    }

    /**
     * Handle player join with custom message and setup
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Set custom join message
        event.setJoinMessage(ChatColor.AQUA + "[+] " + ChatColor.GRAY + player.getName());

        try {
            // Initialize player entity if needed
            if (!playerExists(player)) {
                createNewPlayerEntity(player);
            }

            // Set attack speed attribute to prevent attack cooldowns
            player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(1024.0D);

            // Set initial health display
            player.setHealthScale(20.0);
            player.setHealthScaled(true);

            // Delayed setup tasks after player fully loads
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                try {
                    // Send MOTD
                    sendMotd(player);

                    // Check health
                    if (!player.isDead()) {
                        healthListener.recalculateHealth(player);
                    }

                    // Handle GM mode for operators
                    handleGMMode(player);

                    // Set default toggles
                    Toggles.setToggle(player, "Player Messages", true);

                    // Play sound in separate task
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            playJoinSound(player);
                        } catch (Exception ex) {
                            logger.warning("Error playing join sound for " + player.getName() +
                                    ": " + ex.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    logger.severe("Error in delayed join task for " + player.getName() +
                            ": " + ex.getMessage());
                    ex.printStackTrace();
                }
            }, 10L);
        } catch (Exception ex) {
            logger.severe("Error in onJoin for " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Send server MOTD to a player
     */
    private void sendMotd(Player player) {
        // Send blank lines to clear chat
        IntStream.range(0, 30).forEach(number -> player.sendMessage(" "));

        // Send server info
        TextUtil.sendCenteredMessage(player, "§6✦ §e§lYAK REALMS §6✦");
        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&fRecode - Alpha &6v" + plugin.getDescription().getVersion());
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "" + ChatColor.ITALIC +
                "This server is still in early development, expect bugs.");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "" + ChatColor.ITALIC +
                "Join the discord at https://discord.gg/JYf6R2VKE7");
        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&f&oReport any issues to Red (Jack)");
    }

    /**
     * Handle GM mode for operators
     */
    private void handleGMMode(Player player) {
        if (player.isOp() && !player.isDead()) {
            if (!isGodModeDisabled(player)) {
                // Send action bar message
                player.sendMessage(ChatColor.BLUE + "You are in GM Mode");

                // Warn about vanish mode
                player.sendMessage(ChatColor.BLUE +
                        "You are currently not vanished! Use /vanish to vanish.");

                // Set high health for admin
                player.setMaxHealth(10000);
                player.setHealth(10000);
            }
        }
    }

    /**
     * Play sound when joining with weapon
     */
    private void playJoinSound(Player player) {
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem != null && isWeapon(mainHandItem)) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.5f);
        }
    }

    /**
     * Handle player quit with no message
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Set empty quit message
        event.setQuitMessage(null);

        // Additional cleanup if needed
    }

    /**
     * Handle player kick with no message
     */
    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        // Set empty kick message
        event.setLeaveMessage(null);
    }

    /**
     * Check if player entity already exists
     * This is a placeholder for the actual implementation
     */
    private boolean playerExists(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null;
    }

    /**
     * Create new player entity
     * This is a placeholder for the actual implementation
     */
    private void createNewPlayerEntity(Player player) {
        // In original code: new PlayerEntity(player.getUniqueId());
        // Create YakPlayer instance instead
        YakPlayer newPlayer = new YakPlayer(player);
        playerManager.savePlayer(newPlayer);
    }

    /**
     * Check if god mode is disabled for a player
     *
     * @param player The player to check
     * @return true if god mode is disabled
     */
    private boolean isGodModeDisabled(Player player) {
        return Toggles.isToggled(player, "God Mode Disabled");
    }

    /**
     * Check if an item is a weapon
     */
    private boolean isWeapon(ItemStack item) {
        if (item == null) return false;

        String typeName = item.getType().name();
        return typeName.contains("_SWORD") ||
                typeName.contains("_AXE") ||
                typeName.contains("_HOE") ||
                typeName.contains("_SHOVEL");
    }
}