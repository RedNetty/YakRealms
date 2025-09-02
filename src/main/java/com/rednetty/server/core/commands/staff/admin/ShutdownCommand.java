package com.rednetty.server.core.commands.staff.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.commands.player.LogoutCommand;
import com.rednetty.server.core.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.Rank;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Shutdown command for safely restarting the server with proper player data saving
 * Modernized to use Adventure API and Paper 1.21.7 capabilities
 */
public class ShutdownCommand implements CommandExecutor, TabCompleter {

    private static final int DEFAULT_COUNTDOWN_SECONDS = 30;
    private static final int MIN_COUNTDOWN_SECONDS = 5;
    private static final int MAX_COUNTDOWN_SECONDS = 300; // 5 minutes

    private final YakPlayerManager playerManager;
    private boolean shutdownInProgress = false;

    // Adventure API Sound constants
    private static final Sound SOUND_ANVIL_LAND = Sound.sound(Key.key("minecraft:block.anvil.land"), Sound.Source.PLAYER, 0.8f, 1.2f);
    private static final Sound SOUND_NOTE_PLING = Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.PLAYER, 1.0f, 2.0f);
    private static final Sound SOUND_NOTE_BASS = Sound.sound(Key.key("minecraft:block.note_block.bass"), Sound.Source.PLAYER, 0.8f, 0.8f);
    private static final Sound SOUND_ENDER_DRAGON_DEATH = Sound.sound(Key.key("minecraft:entity.ender_dragon.death"), Sound.Source.PLAYER, 0.5f, 1.5f);

    public ShutdownCommand() {
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permissions
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // Check if shutdown is already in progress
        if (shutdownInProgress) {
            sender.sendMessage(Component.text("Server shutdown is already in progress!", NamedTextColor.YELLOW));
            return true;
        }

        // Parse arguments
        int countdownSeconds = DEFAULT_COUNTDOWN_SECONDS;
        String reason = "Server Restart";
        boolean immediate = false;

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("now") || args[0].equalsIgnoreCase("immediate")) {
                immediate = true;
                if (args.length > 1) {
                    reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                }
            } else {
                try {
                    countdownSeconds = Integer.parseInt(args[0]);
                    if (countdownSeconds < MIN_COUNTDOWN_SECONDS) {
                        sender.sendMessage(Component.text("Minimum countdown time is " + MIN_COUNTDOWN_SECONDS + " seconds.", NamedTextColor.RED));
                        return true;
                    }
                    if (countdownSeconds > MAX_COUNTDOWN_SECONDS) {
                        sender.sendMessage(Component.text("Maximum countdown time is " + MAX_COUNTDOWN_SECONDS + " seconds.", NamedTextColor.RED));
                        return true;
                    }

                    if (args.length > 1) {
                        reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    }
                } catch (NumberFormatException e) {
                    // First argument is not a number, treat as reason
                    reason = String.join(" ", args);
                }
            }
        }

        // Start shutdown process
        if (immediate) {
            performImmediateShutdown(sender, reason);
        } else {
            startShutdownCountdown(sender, countdownSeconds, reason);
        }

        return true;
    }

    /**
     * Start shutdown countdown with warnings
     */
    private void startShutdownCountdown(CommandSender initiator, int countdownSeconds, String reason) {
        shutdownInProgress = true;

        // Log shutdown initiation
        String initiatorName = initiator instanceof Player ?
                ((Player) initiator).getName() : "Console";
        YakRealms.log("Server shutdown initiated by " + initiatorName + " with " +
                countdownSeconds + "s countdown. Reason: " + reason);

        // Initial announcement
        broadcastShutdownWarning(countdownSeconds, reason, true);

        // Start countdown task
        new BukkitRunnable() {
            int timeLeft = countdownSeconds;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    this.cancel();
                    executeShutdown(reason);
                    return;
                }

                // Broadcast warnings at specific intervals
                if (shouldBroadcastWarning(timeLeft)) {
                    broadcastShutdownWarning(timeLeft, reason, false);
                }

                // Cancel any active logout timers when shutdown is imminent
                if (timeLeft <= 10) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (LogoutCommand.isLoggingOut(player)) {
                            LogoutCommand.forceCancelLogout(player, "Server shutdown imminent");
                        }
                    }
                }

                timeLeft--;
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Every second
    }

    /**
     * Perform immediate shutdown without countdown
     */
    private void performImmediateShutdown(CommandSender initiator, String reason) {
        shutdownInProgress = true;

        String initiatorName = initiator instanceof Player ?
                ((Player) initiator).getName() : "Console";
        YakRealms.log("Immediate server shutdown initiated by " + initiatorName + ". Reason: " + reason);

        // Cancel all logout timers
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (LogoutCommand.isLoggingOut(player)) {
                LogoutCommand.forceCancelLogout(player, "Emergency server shutdown");
            }
        }

        executeShutdown(reason);
    }

    /**
     * Execute the actual shutdown process
     */
    private void executeShutdown(String reason) {
        YakRealms.log("Executing server shutdown. Reason: " + reason);

        // Final shutdown warning
        broadcastFinalShutdownMessage(reason);

        // Give players a moment to see the message
        new BukkitRunnable() {
            @Override
            public void run() {
                // Step 1: Save all player data
                saveAllPlayersForShutdown().whenComplete((success, error) -> {
                    // Step 2: Kick all players
                    Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                        kickAllPlayers(reason);

                        // Step 3: Shutdown server systems after a brief delay
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                shutdownServerSystems();
                            }
                        }.runTaskLater(YakRealms.getInstance(), 40L); // 2 seconds
                    });
                });
            }
        }.runTaskLater(YakRealms.getInstance(), 60L); // 3 seconds
    }

    /**
     * Broadcast shutdown warning to all players using Adventure API
     */
    private void broadcastShutdownWarning(int timeLeft, String reason, boolean initial) {
        String timeString = formatTime(timeLeft);

        if (initial) {
            // Initial announcement with more detail
            Bukkit.broadcast(Component.empty());

            Component title = Component.text("âš  SERVER RESTART SCHEDULED âš ", NamedTextColor.RED, TextDecoration.BOLD);
            Component timeMessage = Component.text("Server will restart in ", NamedTextColor.YELLOW)
                    .append(Component.text(timeString, NamedTextColor.YELLOW, TextDecoration.BOLD));
            Component reasonMessage = Component.text("Reason: ", NamedTextColor.GRAY)
                    .append(Component.text(reason, NamedTextColor.WHITE));
            Component warningMessage = Component.text("Please finish what you're doing and find a safe location!", NamedTextColor.GRAY);

            // Use legacy serializer for TextUtil compatibility
            TextUtil.broadcastCenteredMessage(LegacyComponentSerializer.legacySection().serialize(title));
            TextUtil.broadcastCenteredMessage(LegacyComponentSerializer.legacySection().serialize(timeMessage));
            TextUtil.broadcastCenteredMessage(LegacyComponentSerializer.legacySection().serialize(reasonMessage));
            TextUtil.broadcastCenteredMessage(LegacyComponentSerializer.legacySection().serialize(warningMessage));

            Bukkit.broadcast(Component.empty());

            // Play alert sound using Adventure API
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(SOUND_ANVIL_LAND);
            }
        } else {
            // Regular countdown warnings
            Component warningIcon = Component.text("âš  ", NamedTextColor.RED);
            Component restartMessage = Component.text("Server restart in ", NamedTextColor.YELLOW);
            Component timeComponent = Component.text(timeString, NamedTextColor.YELLOW, TextDecoration.BOLD);
            Component exclamation = Component.text("!", NamedTextColor.YELLOW);

            Component fullMessage = warningIcon.append(restartMessage).append(timeComponent).append(exclamation);

            if (timeLeft <= 10) {
                // More urgent for final 10 seconds
                Component urgentMessage = Component.text(LegacyComponentSerializer.legacySection().serialize(fullMessage),
                        NamedTextColor.RED, TextDecoration.BOLD);
                TextUtil.broadcastCenteredMessage(LegacyComponentSerializer.legacySection().serialize(urgentMessage));

                // Play urgent sound
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(SOUND_NOTE_PLING);
                }
            } else {
                Bukkit.broadcast(fullMessage);

                // Play warning sound
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(SOUND_NOTE_BASS);
                }
            }
        }
    }

    /**
     * Broadcast final shutdown message using Adventure API
     */
    private void broadcastFinalShutdownMessage(String reason) {
        Bukkit.broadcast(Component.empty());

        Component title = Component.text("âš  SERVER SHUTTING DOWN NOW âš ", NamedTextColor.RED, TextDecoration.BOLD);
        Component savingMessage = Component.text("Saving all player data...", NamedTextColor.YELLOW);
        Component reconnectMessage = Component.text("You will be reconnected automatically in a moment.", NamedTextColor.GRAY);

        TextUtil.broadcastCenteredMessage(LegacyComponentSerializer.legacySection().serialize(title));
        TextUtil.broadcastCenteredMessage(LegacyComponentSerializer.legacySection().serialize(savingMessage));
        TextUtil.broadcastCenteredMessage(LegacyComponentSerializer.legacySection().serialize(reconnectMessage));

        Bukkit.broadcast(Component.empty());

        // Play final shutdown sound using Adventure API
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(SOUND_ENDER_DRAGON_DEATH);
        }
    }

    /**
     * Save all online players' data for shutdown
     */
    private CompletableFuture<Boolean> saveAllPlayersForShutdown() {
        YakRealms.log("Saving all player data for shutdown...");

        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                // Update player data before saving
                yakPlayer.updateLocation(player.getLocation());
                yakPlayer.updateInventory(player);
                yakPlayer.updateStats(player);

                // Add to save futures
                saveFutures.add(playerManager.savePlayer(yakPlayer));
            }
        }

        // Wait for all saves to complete
        return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    long successful = saveFutures.stream()
                            .mapToLong(future -> {
                                try {
                                    return future.get(5, TimeUnit.SECONDS) ? 1 : 0;
                                } catch (Exception e) {
                                    YakRealms.getInstance().getLogger().log(Level.WARNING,
                                            "Save failed during shutdown", e);
                                    return 0;
                                }
                            }).sum();

                    YakRealms.log("Shutdown save completed: " + successful + "/" + saveFutures.size() + " players saved");
                    return successful == saveFutures.size();
                })
                .exceptionally(error -> {
                    YakRealms.getInstance().getLogger().log(Level.SEVERE,
                            "Error during shutdown save process", error);
                    return false;
                });
    }

    /**
     * Kick all players with shutdown message using Adventure API
     */
    private void kickAllPlayers(String reason) {
        YakRealms.log("Kicking all players for shutdown...");

        Component kickMessage = formatKickMessage(reason);

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                CombatLogoutMechanics.getInstance().clearPlayerCombatTag(player);
                player.kick(kickMessage);
            } catch (Exception e) {
                YakRealms.getInstance().getLogger().log(Level.WARNING,
                        "Error kicking player " + player.getName() + " during shutdown", e);
            }
        }

        YakRealms.log("All players kicked for shutdown");
    }

    /**
     * Shutdown all server systems
     */
    private void shutdownServerSystems() {
        YakRealms.log("Shutting down server systems...");

        try {
            // Disable the plugin which triggers onDisable()
            YakRealms plugin = YakRealms.getInstance();
            Bukkit.getPluginManager().disablePlugin(plugin);

            // Wait a moment for clean shutdown
            new BukkitRunnable() {
                @Override
                public void run() {
                    YakRealms.log("Server shutdown complete. Stopping server...");

                    // Stop the server
                    Bukkit.getServer().shutdown();
                }
            }.runTaskLater(YakRealms.getInstance(), 20L); // 1 second

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().log(Level.SEVERE,
                    "Error during server system shutdown", e);

            // Emergency shutdown
            System.exit(0);
        }
    }

    /**
     * Check if warning should be broadcast at this time
     */
    private boolean shouldBroadcastWarning(int timeLeft) {
        // Broadcast at: 5, 4, 3, 2, 1 seconds (final countdown)
        if (timeLeft <= 5) return true;

        // Broadcast at: 10, 15, 30 seconds
        if (timeLeft == 10 || timeLeft == 15 || timeLeft == 30) return true;

        // Broadcast every minute for longer countdowns
        if (timeLeft >= 60 && timeLeft % 60 == 0) return true;

        return false;
    }

    /**
     * Format time into human readable string
     */
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;

            String result = minutes + " minute" + (minutes != 1 ? "s" : "");
            if (remainingSeconds > 0) {
                result += " " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "");
            }
            return result;
        }
    }

    /**
     * Format kick message for players using Adventure API Components
     */
    private Component formatKickMessage(String reason) {
        return Component.text("âš  SERVER RESTART âš ", NamedTextColor.RED, TextDecoration.BOLD)
                .appendNewline()
                .appendNewline()
                .append(Component.text("The server is restarting for maintenance.", NamedTextColor.YELLOW))
                .appendNewline()
                .append(Component.text("Reason: ", NamedTextColor.GRAY))
                .append(Component.text(reason, NamedTextColor.WHITE))
                .appendNewline()
                .appendNewline()
                .append(Component.text("Your character data has been saved safely!", NamedTextColor.GREEN))
                .appendNewline()
                .append(Component.text("Please reconnect in a moment.", NamedTextColor.AQUA))
                .appendNewline()
                .appendNewline()
                .append(Component.text("Visit ", NamedTextColor.GRAY))
                .append(Component.text("discord.gg/yakrealms", NamedTextColor.BLUE))
                .append(Component.text(" for updates.", NamedTextColor.GRAY));
    }

    /**
     * Check if sender has permission to use shutdown command
     */
    private boolean hasPermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true; // Console always has permission
        }

        Player player = (Player) sender;
        Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());

        // Only DEV, MANAGER, and GM can shutdown
        return rank == Rank.DEV || rank == Rank.MANAGER || rank == Rank.GM;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasPermission(sender)) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("now");
            completions.add("immediate");
            completions.add("30");
            completions.add("60");
            completions.add("120");
            completions.add("300");

            // Filter based on what's typed
            String input = args[0].toLowerCase();
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(input));
        } else if (args.length > 1) {
            // Suggest common reasons
            completions.add("Maintenance");
            completions.add("Update");
            completions.add("Restart");
            completions.add("Bug fixes");
            completions.add("Performance optimization");
        }

        return completions;
    }

    /**
     * Check if shutdown is currently in progress
     */
    public boolean isShutdownInProgress() {
        return shutdownInProgress;
    }
}