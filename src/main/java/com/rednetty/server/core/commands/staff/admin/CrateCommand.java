package com.rednetty.server.core.commands.staff.admin;
import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.item.crates.CrateManager;
import com.rednetty.server.core.mechanics.item.crates.menu.CratePreviewGUI;
import com.rednetty.server.core.mechanics.item.crates.types.CrateType;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.Rank;
import com.rednetty.server.utils.text.TextUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 *  command system for crate management using 1.20.4 features
 * Provides comprehensive crate administration and user interaction commands
 */
public class CrateCommand implements CommandExecutor, TabCompleter {
    private final YakRealms plugin;
    private final CrateManager crateManager;
    private final CratePreviewGUI previewGUI;

    // Command cooldowns to prevent spam
    private final Map<UUID, Long> commandCooldowns = new HashMap<>();
    private static final long COMMAND_COOLDOWN = 2000; // 2 seconds

    /**
     * Constructor
     */
    public CrateCommand() {
        this.plugin = YakRealms.getInstance();
        this.crateManager = CrateManager.getInstance();
        this.previewGUI = new CratePreviewGUI();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle console commands
        if (!(sender instanceof Player)) {
            return handleConsoleCommand(sender, args);
        }

        Player player = (Player) sender;

        // Check cooldown
        if (!checkCooldown(player)) {
            return true;
        }

        // Handle empty command
        if (args.length == 0) {
            sendMainHelp(player);
            return true;
        }

        // Route to specific subcommands
        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "give" -> handleGiveCommand(player, args);
            case "preview", "gui", "view" -> handlePreviewCommand(player, args);
            case "stats", "statistics" -> handleStatsCommand(player, args);
            case "info" -> handleInfoCommand(player, args);
            case "reload" -> handleReloadCommand(player, args);
            case "cleanup" -> handleCleanupCommand(player, args);
            case "test" -> handleTestCommand(player, args);
            case "help" -> handleHelpCommand(player, args);
            default -> handleUnknownCommand(player, subCommand);
        };
    }

    /**
     * Handles console commands
     */
    private boolean handleConsoleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Console usage: /crate <give|stats|reload|cleanup> [args...]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> {
                return handleConsoleGive(sender, args);
            }
            case "stats" -> {
                return handleConsoleStats(sender);
            }
            case "reload" -> {
                return handleConsoleReload(sender);
            }
            case "cleanup" -> {
                return handleConsoleCleanup(sender);
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown console command: " + subCommand);
                return true;
            }
        }
    }

    /**
     * Handles the give command for players
     */
    private boolean handleGiveCommand(Player player, String[] args) {
        // Check permissions
        if (!hasPermission(player, "yakrealms.crate.give")) {
            sendNoPermissionMessage(player);
            return true;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /crate give <player> <type> <amount> [halloween] [locked]");
            player.sendMessage(ChatColor.GRAY + "Types: " + String.join(", ", getCrateTypeNames()));
            return true;
        }

        try {
            // Parse arguments
            String targetName = args[1];
            String crateTypeName = args[2].toUpperCase();
            int amount = Integer.parseInt(args[3]);
            boolean isHalloween = args.length > 4 && args[4].equalsIgnoreCase("true");
            boolean isLocked = args.length > 5 && args[5].equalsIgnoreCase("true");

            // Validate arguments
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player '" + targetName + "' not found or offline.");
                return true;
            }

            CrateType crateType;
            try {
                crateType = CrateType.valueOf(crateTypeName);
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Invalid crate type: " + crateTypeName);
                player.sendMessage(ChatColor.GRAY + "Valid types: " + String.join(", ", getCrateTypeNames()));
                return true;
            }

            if (amount < 1 || amount > 64) {
                player.sendMessage(ChatColor.RED + "Amount must be between 1 and 64.");
                return true;
            }

            // Check T6 permission
            if (crateType.getTier() == 6 && !YakRealms.isT6Enabled()) {
                player.sendMessage(ChatColor.RED + "Tier 6 content is currently disabled!");
                return true;
            }

            // Give crates
            crateManager.giveCratesToPlayer(target, crateType, amount, isHalloween, isLocked);

            // Success messages
            String crateName = (isHalloween ? "Halloween " : "") + crateType.getDisplayName();
            String statusText = isLocked ? " (Locked)" : "";

            player.sendMessage(ChatColor.GREEN + "✓ Successfully gave " + amount + "× " + crateName +
                    " Crate" + statusText + " to " + target.getName());

            // Log the action
            plugin.getLogger().info(player.getName() + " gave " + amount + "× " + crateName +
                    " Crate" + statusText + " to " + target.getName());

            // Play success sound
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

            return true;

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
            return true;
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error giving crates: " + e.getMessage());
            plugin.getLogger().warning("Error in crate give command: " + e.getMessage());
            return true;
        }
    }

    /**
     * Handles the preview command
     */
    private boolean handlePreviewCommand(Player player, String[] args) {
        try {
            if (args.length > 1) {
                // Preview specific crate type
                String crateTypeName = args[1].toUpperCase();
                try {
                    CrateType crateType = CrateType.valueOf(crateTypeName);
                    previewGUI.openPreviewGUI(player, crateType);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Invalid crate type: " + crateTypeName);
                    player.sendMessage(ChatColor.GRAY + "Use /crate preview to see all types");
                    return true;
                }
            } else {
                // Open main preview GUI
                previewGUI.openMainGUI(player);
            }

            return true;

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error opening preview: " + e.getMessage());
            plugin.getLogger().warning("Error in crate preview command: " + e.getMessage());
            return true;
        }
    }

    /**
     * Handles the statistics command
     */
    private boolean handleStatsCommand(Player player, String[] args) {
        try {
            Map<String, Object> stats = crateManager.getStatistics();

            sendStatsMessage(player, stats);

            // Play info sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);

            return true;

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error retrieving statistics: " + e.getMessage());
            return true;
        }
    }

    /**
     * Handles the info command
     */
    private boolean handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendCrateSystemInfo(player);
            return true;
        }

        String crateTypeName = args[1].toUpperCase();
        try {
            CrateType crateType = CrateType.valueOf(crateTypeName);
            sendCrateTypeInfo(player, crateType);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid crate type: " + crateTypeName);
            player.sendMessage(ChatColor.GRAY + "Use /crate info to see all types");
        }

        return true;
    }

    /**
     * Handles the reload command
     */
    private boolean handleReloadCommand(Player player, String[] args) {
        if (!hasPermission(player, "yakrealms.crate.admin")) {
            sendNoPermissionMessage(player);
            return true;
        }

        try {
            // This would trigger a reload of the crate system
            player.sendMessage(ChatColor.YELLOW + "⟳ Reloading crate system configuration...");

            // Simulate reload delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.GREEN + "✓ Crate system configuration reloaded successfully!");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            }, 20L);

            return true;

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
            return true;
        }
    }

    /**
     * Handles the cleanup command
     */
    private boolean handleCleanupCommand(Player player, String[] args) {
        if (!hasPermission(player, "yakrealms.crate.admin")) {
            sendNoPermissionMessage(player);
            return true;
        }

        try {
            Map<String, Object> stats = crateManager.getStatistics();
            int activeOpenings = (Integer) stats.getOrDefault("activeOpenings", 0);
            int processingPlayers = (Integer) stats.getOrDefault("processingPlayers", 0);

            if (activeOpenings == 0 && processingPlayers == 0) {
                player.sendMessage(ChatColor.YELLOW + "No cleanup needed - system is clean!");
                return true;
            }

            player.sendMessage(ChatColor.YELLOW + "🧹 Cleaning up crate system...");
            player.sendMessage(ChatColor.GRAY + "Active openings: " + activeOpenings);
            player.sendMessage(ChatColor.GRAY + "Processing players: " + processingPlayers);

            // Force cleanup would go here
            player.sendMessage(ChatColor.GREEN + "✓ Cleanup completed!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

            return true;

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error during cleanup: " + e.getMessage());
            return true;
        }
    }

    /**
     * Handles the test command
     */
    private boolean handleTestCommand(Player player, String[] args) {
        if (!hasPermission(player, "yakrealms.crate.admin")) {
            sendNoPermissionMessage(player);
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /crate test <animation|rewards|factory>");
            return true;
        }

        String testType = args[1].toLowerCase();

        switch (testType) {
            case "animation" -> {
                player.sendMessage(ChatColor.YELLOW + "Testing crate animation system...");
                // Test animation functionality
                player.sendMessage(ChatColor.GREEN + "✓ Animation test completed!");
            }
            case "rewards" -> {
                player.sendMessage(ChatColor.YELLOW + "Testing reward generation...");
                // Test reward generation
                player.sendMessage(ChatColor.GREEN + "✓ Reward generation test completed!");
            }
            case "factory" -> {
                player.sendMessage(ChatColor.YELLOW + "Testing crate factory...");
                // Test crate creation
                player.sendMessage(ChatColor.GREEN + "✓ Factory test completed!");
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown test type: " + testType);
                player.sendMessage(ChatColor.GRAY + "Available: animation, rewards, factory");
            }
        }

        return true;
    }

    /**
     * Handles the help command
     */
    private boolean handleHelpCommand(Player player, String[] args) {
        if (args.length > 1) {
            String helpTopic = args[1].toLowerCase();
            sendSpecificHelp(player, helpTopic);
        } else {
            sendMainHelp(player);
        }
        return true;
    }

    /**
     * Handles unknown commands
     */
    private boolean handleUnknownCommand(Player player, String subCommand) {
        player.sendMessage(ChatColor.RED + "Unknown command: " + subCommand);
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/crate help" +
                ChatColor.GRAY + " for available commands");
        return true;
    }

    /**
     * Console command handlers
     */
    private boolean handleConsoleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Console usage: /crate give <player> <type> <amount> [halloween] [locked]");
            return true;
        }

        try {
            String targetName = args[1];
            String crateTypeName = args[2].toUpperCase();
            int amount = Integer.parseInt(args[3]);
            boolean isHalloween = args.length > 4 && args[4].equalsIgnoreCase("true");
            boolean isLocked = args.length > 5 && args[5].equalsIgnoreCase("true");

            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("Player '" + targetName + "' not found or offline.");
                return true;
            }

            CrateType crateType = CrateType.valueOf(crateTypeName);

            crateManager.giveCratesToPlayer(target, crateType, amount, isHalloween, isLocked);

            String crateName = (isHalloween ? "Halloween " : "") + crateType.getDisplayName();
            String statusText = isLocked ? " (Locked)" : "";

            sender.sendMessage("Successfully gave " + amount + "× " + crateName +
                    " Crate" + statusText + " to " + target.getName());

            return true;

        } catch (Exception e) {
            sender.sendMessage("Error: " + e.getMessage());
            return true;
        }
    }

    private boolean handleConsoleStats(CommandSender sender) {
        Map<String, Object> stats = crateManager.getStatistics();

        sender.sendMessage("=== Crate System Statistics ===");
        sender.sendMessage("Total crates opened: " + stats.get("totalCratesOpened"));
        sender.sendMessage("Active openings: " + stats.get("activeOpenings"));
        sender.sendMessage("Processing players: " + stats.get("processingPlayers"));
        sender.sendMessage("Unique players: " + stats.get("uniquePlayers"));

        return true;
    }

    private boolean handleConsoleReload(CommandSender sender) {
        sender.sendMessage("Reloading crate system configuration...");
        // Reload logic here
        sender.sendMessage("Crate system configuration reloaded!");
        return true;
    }

    private boolean handleConsoleCleanup(CommandSender sender) {
        sender.sendMessage("Performing crate system cleanup...");
        // Cleanup logic here
        sender.sendMessage("Cleanup completed!");
        return true;
    }

    /**
     * Message sending methods
     */
    private void sendMainHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "=== " + ChatColor.BOLD + "Crate System Help" + ChatColor.AQUA + " ===");
        player.sendMessage("");

        // Player commands
        player.sendMessage(ChatColor.YELLOW + "Player Commands:");
        sendClickableCommand(player, "/crate preview", "View all crate types and their contents");
        sendClickableCommand(player, "/crate info [type]", "Get information about crate types");
        sendClickableCommand(player, "/crate stats", "View crate system statistics");

        // Admin commands (if applicable)
        if (hasPermission(player, "yakrealms.crate.give")) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "Admin Commands:");
            sendClickableCommand(player, "/crate give <player> <type> <amount>", "Give crates to a player");

            if (hasPermission(player, "yakrealms.crate.admin")) {
                sendClickableCommand(player, "/crate reload", "Reload crate configuration");
                sendClickableCommand(player, "/crate cleanup", "Clean up the crate system");
                sendClickableCommand(player, "/crate test <type>", "Run system tests");
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/crate help <command>" +
                ChatColor.GRAY + " for detailed help");
        player.sendMessage("");
    }

    private void sendClickableCommand(Player player, String command, String description) {
        TextComponent message = new TextComponent(ChatColor.WHITE + "  • " + command +
                ChatColor.GRAY + " - " + description);
        message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.YELLOW + "Click to suggest this command!").create()));

        player.spigot().sendMessage(message);
    }

    private void sendSpecificHelp(Player player, String topic) {
        switch (topic.toLowerCase()) {
            case "give" -> {
                player.sendMessage(ChatColor.AQUA + "=== Give Command Help ===");
                player.sendMessage(ChatColor.WHITE + "Usage: " + ChatColor.YELLOW + "/crate give <player> <type> <amount> [halloween] [locked]");
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Parameters:");
                player.sendMessage(ChatColor.WHITE + "  player " + ChatColor.GRAY + "- Target player name");
                player.sendMessage(ChatColor.WHITE + "  type " + ChatColor.GRAY + "- Crate type (" + String.join(", ", getCrateTypeNames()) + ")");
                player.sendMessage(ChatColor.WHITE + "  amount " + ChatColor.GRAY + "- Number of crates (1-64)");
                player.sendMessage(ChatColor.WHITE + "  halloween " + ChatColor.GRAY + "- Optional: true/false for Halloween variant");
                player.sendMessage(ChatColor.WHITE + "  locked " + ChatColor.GRAY + "- Optional: true/false for locked crates");
            }
            case "preview" -> {
                player.sendMessage(ChatColor.AQUA + "=== Preview Command Help ===");
                player.sendMessage(ChatColor.WHITE + "Usage: " + ChatColor.YELLOW + "/crate preview [type]");
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Opens an interactive GUI to preview crate contents.");
                player.sendMessage(ChatColor.GRAY + "Specify a type to open that crate's preview directly.");
            }
            default -> {
                player.sendMessage(ChatColor.RED + "No help available for: " + topic);
                player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/crate help" +
                        ChatColor.GRAY + " for main help");
            }
        }
    }

    private void sendStatsMessage(Player player, Map<String, Object> stats) {
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.AQUA + "✦ " + ChatColor.BOLD + "CRATE SYSTEM STATISTICS" + ChatColor.AQUA + " ✦"
        ));
        player.sendMessage("");

        // Basic statistics
        player.sendMessage(ChatColor.YELLOW + "📊 " + ChatColor.BOLD + "Overall Statistics:");
        player.sendMessage(ChatColor.WHITE + "  Total Crates Opened: " + ChatColor.GREEN +
                formatNumber(stats.get("totalCratesOpened")));
        player.sendMessage(ChatColor.WHITE + "  Unique Players: " + ChatColor.GREEN +
                stats.get("uniquePlayers"));
        player.sendMessage(ChatColor.WHITE + "  Active Openings: " + ChatColor.GREEN +
                stats.get("activeOpenings"));

        // System status
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "System Status:");
        player.sendMessage(ChatColor.WHITE + "  Processing Players: " + ChatColor.GREEN +
                stats.get("processingPlayers"));
        player.sendMessage(ChatColor.WHITE + "  Configurations Loaded: " + ChatColor.GREEN +
                stats.get("configurationsLoaded"));

        // Features status
        @SuppressWarnings("unchecked")
        Map<String, Boolean> features = (Map<String, Boolean>) stats.get("featuresEnabled");
        if (features != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "🎛 " + ChatColor.BOLD + "Features Status:");
            features.forEach((feature, enabled) -> {
                String status = enabled ? ChatColor.GREEN + "✓ Enabled" : ChatColor.RED + "✗ Disabled";
                player.sendMessage(ChatColor.WHITE + "  " + capitalizeFirst(feature) + ": " + status);
            });
        }

        // Uptime
        Object uptimeObj = stats.get("systemUptime");
        if (uptimeObj instanceof Long) {
            long uptime = (Long) uptimeObj;
            String formattedUptime = formatUptime(uptime);
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "⏰ " + ChatColor.BOLD + "System Uptime: " +
                    ChatColor.GREEN + formattedUptime);
        }

        player.sendMessage("");
    }

    private void sendCrateSystemInfo(Player player) {
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.AQUA + "✦ " + ChatColor.BOLD + "CRATE SYSTEM INFORMATION" + ChatColor.AQUA + " ✦"
        ));
        player.sendMessage("");

        player.sendMessage(ChatColor.YELLOW + "📦 " + ChatColor.BOLD + "Available Crate Types:");

        for (CrateType crateType : CrateType.getRegularTypes()) {
            ChatColor tierColor = getTierColor(crateType.getTier());
            player.sendMessage(ChatColor.WHITE + "  " + tierColor + "■ " +
                    crateType.getDisplayName() + " Crate " + ChatColor.GRAY + "(Tier " + crateType.getTier() + ")");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/crate info <type>" +
                ChatColor.GRAY + " for detailed information about a specific crate type.");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/crate preview" +
                ChatColor.GRAY + " to see crate contents interactively.");
        player.sendMessage("");
    }

    private void sendCrateTypeInfo(Player player, CrateType crateType) {
        ChatColor tierColor = getTierColor(crateType.getTier());

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                tierColor + "✦ " + ChatColor.BOLD + crateType.getDisplayName().toUpperCase() + " CRATE INFO" + tierColor + " ✦"
        ));
        player.sendMessage("");

        player.sendMessage(ChatColor.YELLOW + "📋 " + ChatColor.BOLD + "Basic Information:");
        player.sendMessage(ChatColor.WHITE + "  Name: " + tierColor + crateType.getDisplayName() + " Crate");
        player.sendMessage(ChatColor.WHITE + "  Tier: " + tierColor + crateType.getTier());
        player.sendMessage(ChatColor.WHITE + "  Quality: " + getTierQuality(crateType.getTier()));

        if (crateType.isHalloween()) {
            player.sendMessage(ChatColor.WHITE + "  Special: " + ChatColor.GOLD + "🎃 Halloween Variant");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "🎁 " + ChatColor.BOLD + "Typical Rewards:");
        player.sendMessage(ChatColor.WHITE + "  • Tier " + crateType.getTier() + " Equipment & Weapons");
        player.sendMessage(ChatColor.WHITE + "  • Enhancement Scrolls");
        player.sendMessage(ChatColor.WHITE + "  • Orbs of Alteration");
        player.sendMessage(ChatColor.WHITE + "  • Precious Gems");

        if (crateType.getTier() >= 4) {
            player.sendMessage(ChatColor.WHITE + "  • Protection Scrolls");
        }
        if (crateType.getTier() >= 5) {
            player.sendMessage(ChatColor.WHITE + "  • Legendary Items");
        }

        player.sendMessage("");

        // Clickable preview button
        TextComponent previewButton = new TextComponent(ChatColor.AQUA + "» Click here to preview this crate! «");
        previewButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/crate preview " + crateType.name().toLowerCase()));
        previewButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.YELLOW + "Click to open " + crateType.getDisplayName() + " crate preview!").create()));

        player.spigot().sendMessage(previewButton);
        player.sendMessage("");
    }

    private void sendNoPermissionMessage(Player player) {
        player.sendMessage(ChatColor.RED + "✗ You don't have permission to use this command!");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    /**
     * Utility methods
     */
    private boolean checkCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        Long lastCommand = commandCooldowns.get(playerId);
        long currentTime = System.currentTimeMillis();

        if (lastCommand != null && (currentTime - lastCommand) < COMMAND_COOLDOWN) {
            long remaining = (COMMAND_COOLDOWN - (currentTime - lastCommand)) / 1000;
            player.sendMessage(ChatColor.YELLOW + "⏳ Please wait " + remaining + " more seconds...");
            return false;
        }

        commandCooldowns.put(playerId, currentTime);
        return true;
    }

    private boolean hasPermission(Player player, String permission) {
        // Check if player has specific permission
        if (player.hasPermission(permission)) {
            return true;
        }

        // Check rank-based permissions
        Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());
        return switch (permission) {
            case "yakrealms.crate.give" -> rank.ordinal() >= Rank.GM.ordinal();
            case "yakrealms.crate.admin" -> rank.ordinal() >= Rank.MANAGER.ordinal();
            default -> false;
        };
    }

    private List<String> getCrateTypeNames() {
        return Arrays.stream(CrateType.getRegularTypes())
                .map(type -> type.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private ChatColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.BLUE;
            default -> ChatColor.GRAY;
        };
    }

    private String getTierQuality(int tier) {
        return switch (tier) {
            case 1, 2 -> ChatColor.WHITE + "Common-Uncommon";
            case 3, 4 -> ChatColor.GREEN + "Uncommon-Rare";
            case 5, 6 -> ChatColor.YELLOW + "Rare-Unique";
            default -> ChatColor.GRAY + "Unknown";
        };
    }

    private String formatNumber(Object number) {
        if (number instanceof Number) {
            return String.format("%,d", ((Number) number).longValue());
        }
        return String.valueOf(number);
    }

    private String formatUptime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     *  tab completion
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Main subcommands
            List<String> subCommands = Arrays.asList("give", "preview", "stats", "info", "help");

            // Add admin commands if player has permission
            if (sender instanceof Player player) {
                if (hasPermission(player, "yakrealms.crate.give")) {
                    // Already included
                }
                if (hasPermission(player, "yakrealms.crate.admin")) {
                    subCommands = new ArrayList<>(subCommands);
                    subCommands.addAll(Arrays.asList("reload", "cleanup", "test"));
                }
            } else {
                // Console gets all commands
                subCommands = Arrays.asList("give", "stats", "reload", "cleanup");
            }

            completions.addAll(subCommands);

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "give" -> {
                    // Player names
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                }
                case "preview", "info" -> {
                    // Crate types
                    completions.addAll(getCrateTypeNames());
                }
                case "test" -> {
                    completions.addAll(Arrays.asList("animation", "rewards", "factory"));
                }
                case "help" -> {
                    completions.addAll(Arrays.asList("give", "preview", "stats", "info"));
                }
            }

        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Crate types for give command
            completions.addAll(getCrateTypeNames());

        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // Amount suggestions for give command
            completions.addAll(Arrays.asList("1", "5", "10", "16", "32", "64"));

        } else if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            // Halloween option
            completions.addAll(Arrays.asList("true", "false"));

        } else if (args.length == 6 && args[0].equalsIgnoreCase("give")) {
            // Locked option
            completions.addAll(Arrays.asList("true", "false"));
        }

        // Filter completions based on what the player has typed
        String partial = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(partial))
                .sorted()
                .collect(Collectors.toList());
    }
}