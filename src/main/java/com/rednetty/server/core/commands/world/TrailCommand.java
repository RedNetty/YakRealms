package com.rednetty.server.core.commands.world;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.trail.TrailSystem;
import com.rednetty.server.core.mechanics.world.trail.pathing.PathManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Command for path navigation and visualization
 */
public class TrailCommand implements CommandExecutor, TabCompleter, Listener {
    private final YakRealms plugin;
    private final PathManager pathManager;
    private final Map<UUID, Boolean> nodeVisualizationStates;
    private final Set<UUID> targetSelectionMode;
    private final Map<UUID, PathManager.PathType> playerPathStyles;
    private final Set<UUID> pendingNodeUpdates;

    public TrailCommand(YakRealms plugin, PathManager pathManager) {
        this.plugin = plugin;
        this.pathManager = pathManager;
        this.nodeVisualizationStates = new HashMap<>();
        this.targetSelectionMode = new HashSet<>();
        this.playerPathStyles = new HashMap<>();
        this.pendingNodeUpdates = new HashSet<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        try {
            switch (subCommand) {
                case "to" -> handlePathToCommand(player, args);
                case "click" -> handleClickModeCommand(player);
                case "cancel" -> handleCancelCommand(player);
                case "style" -> handleStyleCommand(player, args);
                case "nodes" -> handleNodesCommand(player);
                case "update" -> handleUpdateCommand(player);
                case "help" -> sendHelpMessage(player);
                default -> {
                    player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /trailtest help for usage.");
                    return true;
                }
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
            plugin.getLogger().warning("Error in trailtest command: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private void handlePathToCommand(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /trailtest to <x> <y> <z> [style]");
            return;
        }

        try {
            Location destination = parseLocation(player, args);
            PathManager.PathType pathType = args.length >= 5 ?
                    parsePathStyle(args[4]) : playerPathStyles.getOrDefault(player.getUniqueId(), PathManager.PathType.DEFAULT);

            if (!isValidDestination(destination)) {
                player.sendMessage(ChatColor.RED + "Invalid destination - location is not safe or too far.");
                return;
            }

            player.sendMessage(ChatColor.YELLOW + "Calculating path...");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

            pathManager.createPath(player, destination, pathType);

        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid coordinates or style. Use numbers for coordinates.");
        }
    }

    private void handleClickModeCommand(Player player) {
        UUID playerId = player.getUniqueId();
        if (targetSelectionMode.contains(playerId)) {
            targetSelectionMode.remove(playerId);
            player.sendMessage(ChatColor.YELLOW + "Click mode disabled.");
        } else {
            targetSelectionMode.add(playerId);
            player.sendMessage(ChatColor.GREEN + "Click mode enabled. Right-click to select destination.");
            player.sendMessage(ChatColor.GRAY + "Use /trailtest click again to disable.");
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleCancelCommand(Player player) {
        pathManager.cancelPath(player.getUniqueId());
        targetSelectionMode.remove(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "Navigation cancelled.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
    }

    private void handleStyleCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendStylesList(player);
            return;
        }

        try {
            PathManager.PathType style = parsePathStyle(args[1]);
            playerPathStyles.put(player.getUniqueId(), style);
            player.sendMessage(ChatColor.GREEN + "Path style set to: " + style.name());
            player.sendMessage(ChatColor.GRAY + "This style will be used for all your paths.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        } catch (IllegalArgumentException e) {
            sendStylesList(player);
        }
    }

    private void handleNodesCommand(Player player) {
        UUID playerId = player.getUniqueId();
        pathManager.toggleNodeVisualization(player);
        nodeVisualizationStates.put(playerId, !nodeVisualizationStates.getOrDefault(playerId, false));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    private void handleUpdateCommand(Player player) {
        if (!player.hasPermission("sacred.trailtest.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to update nodes.");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (pendingNodeUpdates.contains(playerId)) {
            player.sendMessage(ChatColor.RED + "Node update already in progress.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Starting node map update...");
        pendingNodeUpdates.add(playerId);
        // Logic to trigger node update would go here
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!targetSelectionMode.contains(player.getUniqueId())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        event.setCancelled(true);
        Location destination = clickedBlock.getLocation().add(0.5, 1, 0.5);

        if (!isValidDestination(destination)) {
            player.sendMessage(ChatColor.RED + "Invalid destination - location is not safe.");
            return;
        }

        PathManager.PathType pathType = playerPathStyles.getOrDefault(player.getUniqueId(), PathManager.PathType.DEFAULT);
        pathManager.createPath(player, destination, pathType);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private Location parseLocation(Player player, String[] args) {
        double x = Double.parseDouble(args[1]);
        double y = Double.parseDouble(args[2]);
        double z = Double.parseDouble(args[3]);
        return new Location(player.getWorld(), x, y, z);
    }

    private PathManager.PathType parsePathStyle(String style) {
        try {
            return PathManager.PathType.valueOf(style.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PathManager.PathType.DEFAULT;
        }
    }

    private boolean isValidDestination(Location location) {
        if (location == null) return false;

        // Check if location is within world border
        WorldBorder border = location.getWorld().getWorldBorder();
        double size = border.getSize() / 2.0;
        Location center = border.getCenter();
        double x = location.getX() - center.getX(), z = location.getZ() - center.getZ();
        if (Math.abs(x) > size || Math.abs(z) > size) return false;

        // Check if location is safe
        Block block = location.getBlock();
        Block above = block.getRelative(0, 1, 0);
        Block below = block.getRelative(0, -1, 0);

        return !block.getType().isSolid() &&
                !above.getType().isSolid() &&
                below.getType().isSolid() &&
                below.getType() != Material.LAVA &&
                below.getType() != Material.WATER;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Navigation Command Help ===");
        player.sendMessage(ChatColor.YELLOW + "/trailtest to <x> <y> <z> [style]" + ChatColor.GRAY + " - Create a path to coordinates");
        player.sendMessage(ChatColor.YELLOW + "/trailtest click" + ChatColor.GRAY + " - Toggle click-to-select mode");
        player.sendMessage(ChatColor.YELLOW + "/trailtest style <style>" + ChatColor.GRAY + " - Set path style");
        player.sendMessage(ChatColor.YELLOW + "/trailtest nodes" + ChatColor.GRAY + " - Toggle node visualization");
        player.sendMessage(ChatColor.YELLOW + "/trailtest cancel" + ChatColor.GRAY + " - Cancel current navigation");
        if (player.hasPermission("sacred.trailtest.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/trailtest update" + ChatColor.GRAY + " - Update node map");
        }
        player.sendMessage(ChatColor.GOLD + "Available Styles:");
        for (PathManager.PathType type : PathManager.PathType.values()) {
            player.sendMessage(ChatColor.GRAY + "- " + type.name().toLowerCase());
        }
    }

    private void sendStylesList(Player player) {
        player.sendMessage(ChatColor.GOLD + "Available path styles:");
        for (PathManager.PathType type : PathManager.PathType.values()) {
            player.sendMessage(ChatColor.GRAY + "- " + type.name().toLowerCase());
        }
        player.sendMessage(ChatColor.YELLOW + "Usage: /trailtest style <style>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("to", "click", "cancel", "style", "help").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("style")) {
            return Arrays.stream(TrailSystem.ParticleStyle.values())
                    .map(style -> style.name().toLowerCase())
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // For /trail to, suggest player's coordinates
        if (args.length >= 2 && args.length <= 4 && args[0].equalsIgnoreCase("to")) {
            Player player = (Player) sender;
            Location loc = player.getLocation();

            if (args.length == 2) {
                return List.of(String.valueOf(Math.round(loc.getX())));
            } else if (args.length == 3) {
                return List.of(String.valueOf(Math.round(loc.getY())));
            } else if (args.length == 4) {
                return List.of(String.valueOf(Math.round(loc.getZ())));
            }
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("to")) {
            return Arrays.stream(TrailSystem.ParticleStyle.values())
                    .map(style -> style.name().toLowerCase())
                    .filter(s -> s.startsWith(args[4].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}