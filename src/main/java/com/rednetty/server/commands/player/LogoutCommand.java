package com.rednetty.server.commands.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logout command that allows players to safely disconnect with proper combat and safe zone checks
 */
public class LogoutCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int LOGOUT_TIMER_SECONDS = 20; // 20 second timer
    private static final Map<UUID, LogoutSession> activeLogouts = new ConcurrentHashMap<>();

    private final YakPlayerManager playerManager;
    private final AlignmentMechanics alignmentMechanics;

    public LogoutCommand() {
        this.playerManager = YakPlayerManager.getInstance();
        this.alignmentMechanics = AlignmentMechanics.getInstance();

        // Register this as a listener for damage/movement events
        YakRealms.getInstance().getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        // Handle cancel subcommand
        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            if (handleLogoutCancel(player)) {
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "You are not currently logging out!");
                return true;
            }
        }

        // Check if player is already logging out
        if (activeLogouts.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "You are already in the process of logging out!");
            player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.WHITE + "/logout cancel" + ChatColor.GRAY + " to cancel.");
            return true;
        }

        // Check if server is shutting down
        if (playerManager.isShuttingDown()) {
            player.sendMessage(ChatColor.YELLOW + "Server is shutting down, you will be disconnected automatically.");
            return true;
        }

        // Check if player is combat tagged
        if (CombatLogoutMechanics.getInstance().isPlayerTagged(player.getUniqueId())) {
            long combatTime = CombatLogoutMechanics.getInstance().getCombatTimeRemaining(player);
            int timeLeft = (int) ((10000 - combatTime) / 1000); // 10 second combat tag
            timeLeft = Math.max(0, timeLeft);

            TextUtil.sendCenteredMessage(player, ChatColor.RED + "✘ Cannot logout while in combat!");
            TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Combat ends in: " + ChatColor.BOLD + timeLeft + "s");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return true;
        }

        // Check if player is in a safe zone
        if (AlignmentMechanics.isSafeZone(player.getLocation())) {
            // Immediate logout in safe zone
            performImmediateLogout(player);
            return true;
        }

        // Start logout timer for players outside safe zones
        startLogoutTimer(player);
        return true;
    }

    /**
     * Perform immediate logout for players in safe zones
     */
    private void performImmediateLogout(Player player) {
        TextUtil.sendCenteredMessage(player, ChatColor.GREEN + "✓ Safe logout in progress...");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Saving your character data...");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

        // Save player data and then disconnect
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            // Update player data before saving
            yakPlayer.updateLocation(player.getLocation());
            yakPlayer.updateInventory(player);
            yakPlayer.updateStats(player);

            // Save and disconnect
            playerManager.savePlayer(yakPlayer).whenComplete((success, error) -> {
                if (success) {
                    YakRealms.getInstance().getServer().getScheduler().runTask(YakRealms.getInstance(), () -> {
                        if (player.isOnline()) {
                            player.sendMessage(ChatColor.GREEN + "✓ Character data saved successfully. Goodbye!");
                            player.kickPlayer(ChatColor.GREEN + "✓ Safe Logout Complete\n" +
                                    ChatColor.GRAY + "Your character has been saved safely.");
                        }
                    });
                } else {
                    YakRealms.getInstance().getServer().getScheduler().runTask(YakRealms.getInstance(), () -> {
                        if (player.isOnline()) {
                            player.sendMessage(ChatColor.RED + "✘ Error saving character data. Please try again.");
                        }
                    });
                }
            });
        } else {
            player.kickPlayer(ChatColor.YELLOW + "Logout Complete\n" +
                    ChatColor.GRAY + "Character data not found.");
        }
    }

    /**
     * Start the logout timer for players outside safe zones
     */
    private void startLogoutTimer(Player player) {
        UUID playerId = player.getUniqueId();
        Location startLocation = player.getLocation().clone();

        // Create logout session
        LogoutSession session = new LogoutSession(player, startLocation);
        activeLogouts.put(playerId, session);

        // Initial messages
        TextUtil.sendCenteredMessage(player, ChatColor.YELLOW + "⚠ LOGOUT TIMER STARTED ⚠");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Do not move or take damage for " +
                ChatColor.BOLD + LOGOUT_TIMER_SECONDS + " seconds");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Type " + ChatColor.WHITE + "/logout cancel" +
                ChatColor.GRAY + " to cancel");

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);

        // Start countdown task
        BukkitTask countdownTask = new BukkitRunnable() {
            int timeLeft = LOGOUT_TIMER_SECONDS;

            @Override
            public void run() {
                if (!player.isOnline() || !activeLogouts.containsKey(playerId)) {
                    this.cancel();
                    return;
                }

                // Check if timer completed
                if (timeLeft <= 0) {
                    this.cancel();
                    completeLogout(player);
                    return;
                }

                // Send countdown messages at specific intervals
                if (timeLeft <= 5 || timeLeft == 10 || timeLeft == 15) {
                    String message = ChatColor.YELLOW + "⏱ Logging out in " +
                            ChatColor.BOLD + timeLeft + ChatColor.YELLOW + " second" +
                            (timeLeft != 1 ? "s" : "") + "...";
                    player.sendMessage(message);

                    // Play sound
                    if (timeLeft <= 3) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    } else {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
                    }
                }

                timeLeft--;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 20L); // Every second

        session.setCountdownTask(countdownTask);
    }

    /**
     * Complete the logout process
     */
    private void completeLogout(Player player) {
        UUID playerId = player.getUniqueId();
        LogoutSession session = activeLogouts.remove(playerId);

        if (session != null && player.isOnline()) {
            TextUtil.sendCenteredMessage(player, ChatColor.GREEN + "✓ LOGOUT TIMER COMPLETE ✓");
            TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Saving your character data...");

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

            // Save and disconnect (same as immediate logout)
            performImmediateLogout(player);
        }
    }

    /**
     * Cancel a player's logout process
     */
    private void cancelLogout(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        LogoutSession session = activeLogouts.remove(playerId);

        if (session != null) {
            // Cancel the countdown task
            if (session.getCountdownTask() != null && !session.getCountdownTask().isCancelled()) {
                session.getCountdownTask().cancel();
            }

            // Notify player
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "✘ LOGOUT CANCELLED ✘");
            TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Reason: " + ChatColor.WHITE + reason);

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * Handle manual logout cancellation
     */
    public boolean handleLogoutCancel(Player player) {
        if (activeLogouts.containsKey(player.getUniqueId())) {
            cancelLogout(player, "Manual cancellation");
            return true;
        }
        return false;
    }

    // Event handlers to cancel logout on damage or movement

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            UUID playerId = player.getUniqueId();

            if (activeLogouts.containsKey(playerId)) {
                // Don't cancel for fall damage from staying still, or other environmental damage
                // Only cancel for actual combat damage
                if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                        event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK ||
                        event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE ||
                        event.getCause() == EntityDamageEvent.DamageCause.MAGIC ||
                        event.getCause() == EntityDamageEvent.DamageCause.THORNS) {

                    cancelLogout(player, "You took damage!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        LogoutSession session = activeLogouts.get(playerId);

        if (session != null) {
            // Check if player moved more than 1 block from start location
            Location startLoc = session.getStartLocation();
            Location currentLoc = player.getLocation();

            if (startLoc.getWorld().equals(currentLoc.getWorld())) {
                double distance = startLoc.distance(currentLoc);
                if (distance > 1.0) {
                    cancelLogout(player, "You moved too far!");
                }
            } else {
                cancelLogout(player, "You changed worlds!");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up any active logout sessions
        UUID playerId = event.getPlayer().getUniqueId();
        LogoutSession session = activeLogouts.remove(playerId);
        if (session != null && session.getCountdownTask() != null) {
            session.getCountdownTask().cancel();
        }
    }

    /**
     * Check if a player is currently logging out
     */
    public static boolean isLoggingOut(Player player) {
        return activeLogouts.containsKey(player.getUniqueId());
    }

    /**
     * Get remaining logout time for a player
     */
    public static int getRemainingLogoutTime(Player player) {
        LogoutSession session = activeLogouts.get(player.getUniqueId());
        return session != null ? session.getRemainingTime() : 0;
    }

    /**
     * Force cancel logout for a player (used by other systems)
     */
    public static void forceCancelLogout(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        LogoutSession session = activeLogouts.remove(playerId);
        if (session != null) {
            if (session.getCountdownTask() != null && !session.getCountdownTask().isCancelled()) {
                session.getCountdownTask().cancel();
            }
            player.sendMessage(ChatColor.RED + "Logout cancelled: " + reason);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (activeLogouts.containsKey(player.getUniqueId())) {
                completions.add("cancel");
            }

            // Filter based on what's typed
            String input = args[0].toLowerCase();
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(input));
        }

        return completions;
    }

    /**
     * Inner class to track logout sessions
     */
    private static class LogoutSession {
        private final Player player;
        private final Location startLocation;
        private final long startTime;
        private BukkitTask countdownTask;

        public LogoutSession(Player player, Location startLocation) {
            this.player = player;
            this.startLocation = startLocation;
            this.startTime = System.currentTimeMillis();
        }

        public Player getPlayer() {
            return player;
        }

        public Location getStartLocation() {
            return startLocation;
        }

        public long getStartTime() {
            return startTime;
        }

        public BukkitTask getCountdownTask() {
            return countdownTask;
        }

        public void setCountdownTask(BukkitTask countdownTask) {
            this.countdownTask = countdownTask;
        }

        public int getRemainingTime() {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            return Math.max(0, LOGOUT_TIMER_SECONDS - (int) elapsed);
        }
    }
}