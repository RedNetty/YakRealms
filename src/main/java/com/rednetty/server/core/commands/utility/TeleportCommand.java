package com.rednetty.server.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced teleportation command with clean, organized structure
 */
public class TeleportCommand implements CommandExecutor, TabCompleter {

    private final Map<UUID, Location> previousLocations = new HashMap<>();

    // Permission constants
    private static final String PERM_TELEPORT = "yakrealms.teleport";
    private static final String PERM_ADMIN = "yakrealms.admin.teleport";

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Check basic permissions
        if (!hasPermission(sender, PERM_TELEPORT)) {
            sendMessage(sender, ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Handle no arguments or help
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        // Parse and execute command
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "back":
            case "b":
                return handleBack(sender, args);
            case "coord":
            case "c":
            case "coordinates":
                return handleCoordinates(sender, args);
            case "player":
            case "p":
                return handlePlayer(sender, args);
            default:
                return handleDefault(sender, args);
        }
    }

    /**
     * Handles teleporting back to previous location
     */
    private boolean handleBack(CommandSender sender, String[] args) {
        Player targetPlayer = getTargetPlayer(sender, args, 1);
        if (targetPlayer == null) return true;

        // Admin check for teleporting others
        if (targetPlayer != sender && !hasPermission(sender, PERM_ADMIN)) {
            sendMessage(sender, ChatColor.RED + "You don't have permission to teleport other players back.");
            return true;
        }

        Location previousLocation = previousLocations.get(targetPlayer.getUniqueId());
        if (previousLocation == null) {
            String message = (targetPlayer == sender)
                    ? "You have no previous location to return to."
                    : targetPlayer.getName() + " has no previous location to return to.";
            sendMessage(sender, ChatColor.RED + message);
            return true;
        }

        // Perform teleport
        Location currentLocation = targetPlayer.getLocation();
        targetPlayer.teleport(previousLocation);

        // Update previous location for toggling
        previousLocations.put(targetPlayer.getUniqueId(), currentLocation);

        // Send messages
        if (targetPlayer != sender) {
            sendMessage(sender, ChatColor.GREEN + "Teleported " + targetPlayer.getName() + " back to their previous location.");
            sendMessage(targetPlayer, ChatColor.GREEN + "You were teleported back to your previous location.");
        } else {
            sendMessage(targetPlayer, ChatColor.GREEN + "Teleported back to your previous location.");
        }

        return true;
    }

    /**
     * Handles teleporting to coordinates
     */
    private boolean handleCoordinates(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendMessage(sender, ChatColor.RED + "Usage: /teleport coord <x> <y> <z> [world] [player]");
            return true;
        }

        // Parse coordinates
        double x, y, z;
        try {
            x = Double.parseDouble(args[1]);
            y = Double.parseDouble(args[2]);
            z = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sendMessage(sender, ChatColor.RED + "Invalid coordinates. Use numbers for x, y, z.");
            return true;
        }

        // Get world
        World world = getWorld(sender, args, 4);
        if (world == null) return true;

        // Get target player
        Player targetPlayer = getTargetPlayer(sender, args, 5);
        if (targetPlayer == null) return true;

        // Admin check for teleporting others
        if (targetPlayer != sender && !hasPermission(sender, PERM_ADMIN)) {
            sendMessage(sender, ChatColor.RED + "You don't have permission to teleport other players.");
            return true;
        }

        // Perform teleport
        Location location = new Location(world, x, y, z);
        savePreviousLocation(targetPlayer);
        targetPlayer.teleport(location);

        // Send messages
        String locationStr = world.getName() + " at " + x + ", " + y + ", " + z;
        if (targetPlayer != sender) {
            sendMessage(sender, ChatColor.GREEN + "Teleported " + targetPlayer.getName() + " to " + locationStr);
            sendMessage(targetPlayer, ChatColor.GREEN + "You were teleported to " + locationStr);
        } else {
            sendMessage(targetPlayer, ChatColor.GREEN + "Teleported to " + locationStr);
        }

        return true;
    }

    /**
     * Handles teleporting player to player
     */
    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, ChatColor.RED + "Usage: /teleport player <from> [to]");
            return true;
        }

        if (!hasPermission(sender, PERM_ADMIN)) {
            sendMessage(sender, ChatColor.RED + "You don't have permission to teleport other players.");
            return true;
        }

        Player fromPlayer = Bukkit.getPlayer(args[1]);
        if (fromPlayer == null) {
            sendMessage(sender, ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }

        Player toPlayer;
        if (args.length < 3) {
            // Teleport to sender
            if (!(sender instanceof Player)) {
                sendMessage(sender, ChatColor.RED + "Please specify a destination player.");
                return true;
            }
            toPlayer = (Player) sender;
        } else {
            // Teleport to specified player
            toPlayer = Bukkit.getPlayer(args[2]);
            if (toPlayer == null) {
                sendMessage(sender, ChatColor.RED + "Destination player not found: " + args[2]);
                return true;
            }
        }

        // Perform teleport
        savePreviousLocation(fromPlayer);
        fromPlayer.teleport(toPlayer);

        // Send messages
        sendMessage(fromPlayer, ChatColor.GREEN + "You were teleported to " + toPlayer.getName());
        sendMessage(sender, ChatColor.GREEN + "Teleported " + fromPlayer.getName() + " to " + toPlayer.getName());

        return true;
    }

    /**
     * Handles default teleportation (player to player)
     */
    private boolean handleDefault(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Teleport sender to target
            if (!(sender instanceof Player senderPlayer)) {
                sendMessage(sender, ChatColor.RED + "Console must specify both players: /teleport <player1> <player2>");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) {
                sendMessage(sender, ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }

            savePreviousLocation(senderPlayer);
            senderPlayer.teleport(targetPlayer);
            sendMessage(senderPlayer, ChatColor.GREEN + "Teleported to " + targetPlayer.getName());
            return true;
        }

        if (args.length == 2) {
            // Teleport first player to second player
            if (!hasPermission(sender, PERM_ADMIN)) {
                sendMessage(sender, ChatColor.RED + "You don't have permission to teleport other players.");
                return true;
            }

            Player fromPlayer = Bukkit.getPlayer(args[0]);
            Player toPlayer = Bukkit.getPlayer(args[1]);

            if (fromPlayer == null) {
                sendMessage(sender, ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }

            if (toPlayer == null) {
                sendMessage(sender, ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            savePreviousLocation(fromPlayer);
            fromPlayer.teleport(toPlayer);

            sendMessage(sender, ChatColor.GREEN + "Teleported " + fromPlayer.getName() + " to " + toPlayer.getName());
            sendMessage(fromPlayer, ChatColor.GREEN + "You were teleported to " + toPlayer.getName());
            return true;
        }

        sendHelp(sender);
        return true;
    }

    /**
     * Gets target player from arguments or sender
     */
    private Player getTargetPlayer(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player player = Bukkit.getPlayer(args[index]);
            if (player == null) {
                sendMessage(sender, ChatColor.RED + "Player not found: " + args[index]);
                return null;
            }
            return player;
        }

        if (sender instanceof Player) {
            return (Player) sender;
        }

        sendMessage(sender, ChatColor.RED + "Please specify a player to teleport.");
        return null;
    }

    /**
     * Gets world from arguments or sender's world
     */
    private World getWorld(CommandSender sender, String[] args, int index) {
        if (args.length > index && !args[index].equalsIgnoreCase("~")) {
            World world = Bukkit.getWorld(args[index]);
            if (world == null) {
                sendMessage(sender, ChatColor.RED + "World not found: " + args[index]);
                return null;
            }
            return world;
        }

        if (sender instanceof Player) {
            return ((Player) sender).getWorld();
        }

        return Bukkit.getWorlds().get(0);
    }

    /**
     * Saves a player's current location
     */
    private void savePreviousLocation(Player player) {
        previousLocations.put(player.getUniqueId(), player.getLocation());
    }

    /**
     * Checks if sender has permission
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    /**
     * Sends a message to the sender
     */
    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    /**
     * Sends help message to the sender
     */
    private void sendHelp(CommandSender sender) {
        sendMessage(sender, ChatColor.GOLD + "===== Teleport Command Help =====");

        if (hasPermission(sender, PERM_TELEPORT)) {
            sendMessage(sender, ChatColor.YELLOW + "/teleport <player>" + ChatColor.GRAY + " - Teleport to a player");
            sendMessage(sender, ChatColor.YELLOW + "/teleport back" + ChatColor.GRAY + " - Return to previous location");
            sendMessage(sender, ChatColor.YELLOW + "/teleport coord <x> <y> <z> [world]" + ChatColor.GRAY + " - Teleport to coordinates");
        }

        if (hasPermission(sender, PERM_ADMIN)) {
            sendMessage(sender, ChatColor.YELLOW + "/teleport <player1> <player2>" + ChatColor.GRAY + " - Teleport player1 to player2");
            sendMessage(sender, ChatColor.YELLOW + "/teleport player <player> [destination]" + ChatColor.GRAY + " - Teleport player to destination");
            sendMessage(sender, ChatColor.YELLOW + "/teleport coord <x> <y> <z> [world] [player]" + ChatColor.GRAY + " - Teleport player to coordinates");
            sendMessage(sender, ChatColor.YELLOW + "/teleport back [player]" + ChatColor.GRAY + " - Return player to previous location");
        }

        sendMessage(sender, ChatColor.YELLOW + "/teleport help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!hasPermission(sender, PERM_TELEPORT)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        switch (args.length) {
            case 1:
                completions.addAll(getSubcommands(sender));
                completions.addAll(getOnlinePlayerNames());
                break;
            case 2:
                completions.addAll(getSecondArgCompletions(sender, args[0]));
                break;
            case 3:
                completions.addAll(getThirdArgCompletions(sender, args[0]));
                break;
            case 4:
                completions.addAll(getFourthArgCompletions(sender, args[0]));
                break;
            case 5:
                completions.addAll(getFifthArgCompletions(sender, args[0]));
                break;
            case 6:
                completions.addAll(getSixthArgCompletions(sender, args[0]));
                break;
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getSubcommands(CommandSender sender) {
        List<String> subcommands = Arrays.asList("help", "back", "coord", "coordinates", "c");
        if (hasPermission(sender, PERM_ADMIN)) {
            subcommands = new ArrayList<>(subcommands);
            subcommands.add("player");
            subcommands.add("p");
        }
        return subcommands;
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> getSecondArgCompletions(CommandSender sender, String firstArg) {
        switch (firstArg.toLowerCase()) {
            case "player":
            case "p":
            case "back":
                return hasPermission(sender, PERM_ADMIN) ? getOnlinePlayerNames() : Collections.emptyList();
            case "coord":
            case "c":
            case "coordinates":
                return getSenderCoordinate(sender, Location::getX);
            default:
                return hasPermission(sender, PERM_ADMIN) ? getOnlinePlayerNames() : Collections.emptyList();
        }
    }

    private List<String> getThirdArgCompletions(CommandSender sender, String firstArg) {
        switch (firstArg.toLowerCase()) {
            case "player":
            case "p":
                return hasPermission(sender, PERM_ADMIN) ? getOnlinePlayerNames() : Collections.emptyList();
            case "coord":
            case "c":
            case "coordinates":
                return getSenderCoordinate(sender, Location::getY);
            default:
                return Collections.emptyList();
        }
    }

    private List<String> getFourthArgCompletions(CommandSender sender, String firstArg) {
        if (firstArg.toLowerCase().matches("coord|c|coordinates")) {
            return getSenderCoordinate(sender, Location::getZ);
        }
        return Collections.emptyList();
    }

    private List<String> getFifthArgCompletions(CommandSender sender, String firstArg) {
        if (firstArg.toLowerCase().matches("coord|c|coordinates")) {
            return getWorldNames();
        }
        return Collections.emptyList();
    }

    private List<String> getSixthArgCompletions(CommandSender sender, String firstArg) {
        if (firstArg.toLowerCase().matches("coord|c|coordinates") && hasPermission(sender, PERM_ADMIN)) {
            return getOnlinePlayerNames();
        }
        return Collections.emptyList();
    }

    private List<String> getSenderCoordinate(CommandSender sender, CoordinateExtractor extractor) {
        if (sender instanceof Player player) {
            return Collections.singletonList(String.valueOf((int) extractor.extract(player.getLocation())));
        }
        return Collections.emptyList();
    }

    private List<String> getWorldNames() {
        List<String> worlds = new ArrayList<>();
        worlds.add("~"); // Current world
        worlds.addAll(Bukkit.getWorlds().stream()
                .map(World::getName)
                .collect(Collectors.toList()));
        return worlds;
    }

    @FunctionalInterface
    private interface CoordinateExtractor {
        double extract(Location location);
    }
}