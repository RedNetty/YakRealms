package com.rednetty.server.commands.utility;

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
 * Advanced teleportation command with additional features
 */
public class TeleportCommand implements CommandExecutor, TabCompleter {

    private final Map<UUID, Location> previousLocations = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) && args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Console must specify both players: /teleport <player1> <player2>");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        // Check base teleport permission
        if (!sender.hasPermission("yakrealms.admin.teleport") &&
                !sender.hasPermission("yakrealms.teleport")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Basic teleport to player
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify both players: /teleport <player1> <player2>");
                return true;
            }

            Player player = (Player) sender;
            Player target = Bukkit.getPlayer(args[0]);

            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }

            // Save previous location
            previousLocations.put(player.getUniqueId(), player.getLocation());

            // Teleport to target
            player.teleport(target);
            player.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName());
            return true;
        }

        // Teleport player to player or coordinates
        if (subCmd.equals("player") || subCmd.equals("p")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /teleport player <from> <to>");
                return true;
            }

            if (!sender.hasPermission("yakrealms.admin.teleport")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to teleport other players.");
                return true;
            }

            Player from = Bukkit.getPlayer(args[1]);
            if (from == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            if (args.length < 3) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Please specify a destination player or coordinates.");
                    return true;
                }

                // Teleport specified player to command sender
                Player player = (Player) sender;

                // Save previous location
                previousLocations.put(from.getUniqueId(), from.getLocation());

                // Teleport
                from.teleport(player);
                from.sendMessage(ChatColor.GREEN + "You were teleported to " + player.getName());
                player.sendMessage(ChatColor.GREEN + "Teleported " + from.getName() + " to you");
                return true;
            }

            // Teleport player to another player
            Player to = Bukkit.getPlayer(args[2]);
            if (to == null) {
                sender.sendMessage(ChatColor.RED + "Destination player not found: " + args[2]);
                return true;
            }

            // Save previous location
            previousLocations.put(from.getUniqueId(), from.getLocation());

            // Teleport
            from.teleport(to);
            from.sendMessage(ChatColor.GREEN + "You were teleported to " + to.getName());
            sender.sendMessage(ChatColor.GREEN + "Teleported " + from.getName() + " to " + to.getName());
            return true;
        }

        // Teleport to coordinates
        if (subCmd.equals("coordinates") || subCmd.equals("coord") || subCmd.equals("c")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /teleport coord <x> <y> <z> [world] [player]");
                return true;
            }

            double x, y, z;

            try {
                x = Double.parseDouble(args[1]);
                y = Double.parseDouble(args[2]);
                z = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates. Use numbers for x, y, z.");
                return true;
            }

            // Get world
            World world;
            if (args.length >= 5 && !args[4].equalsIgnoreCase("~")) {
                world = Bukkit.getWorld(args[4]);
                if (world == null) {
                    sender.sendMessage(ChatColor.RED + "World not found: " + args[4]);
                    return true;
                }
            } else if (sender instanceof Player) {
                world = ((Player) sender).getWorld();
            } else {
                world = Bukkit.getWorlds().get(0);
            }

            // Get target player(s)
            Player targetPlayer;
            if (args.length >= 6) {
                if (!sender.hasPermission("yakrealms.admin.teleport")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to teleport other players.");
                    return true;
                }

                targetPlayer = Bukkit.getPlayer(args[5]);
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[5]);
                    return true;
                }
            } else if (sender instanceof Player) {
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "Please specify a player to teleport.");
                return true;
            }

            // Create location
            Location location = new Location(world, x, y, z);

            // Save previous location
            previousLocations.put(targetPlayer.getUniqueId(), targetPlayer.getLocation());

            // Teleport
            targetPlayer.teleport(location);
            if (sender != targetPlayer) {
                sender.sendMessage(ChatColor.GREEN + "Teleported " + targetPlayer.getName() + " to " +
                        world.getName() + " at " + x + ", " + y + ", " + z);
                targetPlayer.sendMessage(ChatColor.GREEN + "You were teleported to " +
                        world.getName() + " at " + x + ", " + y + ", " + z);
            } else {
                targetPlayer.sendMessage(ChatColor.GREEN + "Teleported to " +
                        world.getName() + " at " + x + ", " + y + ", " + z);
            }

            return true;
        }

        // Teleport back to previous location
        if (subCmd.equals("back") || subCmd.equals("b")) {
            // Get target player
            Player targetPlayer;
            if (args.length >= 2) {
                if (!sender.hasPermission("yakrealms.admin.teleport")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to teleport other players back.");
                    return true;
                }

                targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
            } else if (sender instanceof Player) {
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "Please specify a player to teleport back.");
                return true;
            }

            // Check if previous location exists
            if (!previousLocations.containsKey(targetPlayer.getUniqueId())) {
                if (sender == targetPlayer) {
                    sender.sendMessage(ChatColor.RED + "You have no previous location to return to.");
                } else {
                    sender.sendMessage(ChatColor.RED + targetPlayer.getName() + " has no previous location to return to.");
                }
                return true;
            }

            Location previousLocation = previousLocations.get(targetPlayer.getUniqueId());

            // Save current location as previous for toggle between locations
            Location currentLocation = targetPlayer.getLocation();

            // Teleport back
            targetPlayer.teleport(previousLocation);

            // Update previous location to current for toggling
            previousLocations.put(targetPlayer.getUniqueId(), currentLocation);

            if (sender != targetPlayer) {
                sender.sendMessage(ChatColor.GREEN + "Teleported " + targetPlayer.getName() + " back to their previous location.");
                targetPlayer.sendMessage(ChatColor.GREEN + "You were teleported back to your previous location.");
            } else {
                targetPlayer.sendMessage(ChatColor.GREEN + "Teleported back to your previous location.");
            }

            return true;
        }

        // Teleport player to another player
        if (args.length == 2) {
            if (!(sender instanceof Player)) {
                Player fromPlayer = Bukkit.getPlayer(args[0]);
                Player toPlayer = Bukkit.getPlayer(args[1]);

                if (fromPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }

                if (toPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }

                // Save previous location
                previousLocations.put(fromPlayer.getUniqueId(), fromPlayer.getLocation());

                // Teleport
                fromPlayer.teleport(toPlayer);
                sender.sendMessage(ChatColor.GREEN + "Teleported " + fromPlayer.getName() + " to " + toPlayer.getName());
                fromPlayer.sendMessage(ChatColor.GREEN + "You were teleported to " + toPlayer.getName());
                return true;
            } else {
                // Player is sending command - check if they can teleport others
                if (!sender.hasPermission("yakrealms.admin.teleport")) {
                    Player player = (Player) sender;
                    Player target = Bukkit.getPlayer(args[0]);
                    Player destination = Bukkit.getPlayer(args[1]);

                    if (target == null) {
                        player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                        return true;
                    }

                    if (destination == null) {
                        player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                        return true;
                    }

                    player.sendMessage(ChatColor.RED + "You don't have permission to teleport other players.");
                    return true;
                } else {
                    Player fromPlayer = Bukkit.getPlayer(args[0]);
                    Player toPlayer = Bukkit.getPlayer(args[1]);

                    if (fromPlayer == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                        return true;
                    }

                    if (toPlayer == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                        return true;
                    }

                    // Save previous location
                    previousLocations.put(fromPlayer.getUniqueId(), fromPlayer.getLocation());

                    // Teleport
                    fromPlayer.teleport(toPlayer);
                    sender.sendMessage(ChatColor.GREEN + "Teleported " + fromPlayer.getName() + " to " + toPlayer.getName());
                    fromPlayer.sendMessage(ChatColor.GREEN + "You were teleported to " + toPlayer.getName());
                    return true;
                }
            }
        }

        sendHelp(sender);
        return true;
    }

    /**
     * Send help message
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Teleport Command Help =====");

        if (sender.hasPermission("yakrealms.teleport")) {
            sender.sendMessage(ChatColor.YELLOW + "/teleport <player>" + ChatColor.GRAY + " - Teleport yourself to a player");
            sender.sendMessage(ChatColor.YELLOW + "/teleport back" + ChatColor.GRAY + " - Return to your previous location");
            sender.sendMessage(ChatColor.YELLOW + "/teleport coord <x> <y> <z> [world]" + ChatColor.GRAY + " - Teleport to coordinates");
        }

        if (sender.hasPermission("yakrealms.admin.teleport")) {
            sender.sendMessage(ChatColor.YELLOW + "/teleport <player1> <player2>" + ChatColor.GRAY + " - Teleport player1 to player2");
            sender.sendMessage(ChatColor.YELLOW + "/teleport player <player> [destination]" +
                    ChatColor.GRAY + " - Teleport player to you or another player");
            sender.sendMessage(ChatColor.YELLOW + "/teleport coord <x> <y> <z> [world] [player]" +
                    ChatColor.GRAY + " - Teleport player to coordinates");
            sender.sendMessage(ChatColor.YELLOW + "/teleport back [player]" + ChatColor.GRAY + " - Return player to their previous location");
        }

        sender.sendMessage(ChatColor.YELLOW + "/teleport help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.teleport")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // Add subcommands
            completions.add("help");
            completions.add("back");
            completions.add("coord");

            // Admin subcommands
            if (sender.hasPermission("yakrealms.admin.teleport")) {
                completions.add("player");
            }

            // Add online players
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("player") ||
                    args[0].equalsIgnoreCase("back")) {
                if (sender.hasPermission("yakrealms.admin.teleport")) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (args[0].equalsIgnoreCase("coord") ||
                    args[0].equalsIgnoreCase("c") ||
                    args[0].equalsIgnoreCase("coordinates")) {
                if (sender instanceof Player) {
                    // Suggest current location
                    Player player = (Player) sender;
                    return List.of(String.valueOf((int) player.getLocation().getX())).stream()
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
            } else if (sender.hasPermission("yakrealms.admin.teleport")) {
                // Suggest second player for /tp player1 player2
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("player") ||
                    args[0].equalsIgnoreCase("p")) {
                if (sender.hasPermission("yakrealms.admin.teleport")) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (args[0].equalsIgnoreCase("coord") ||
                    args[0].equalsIgnoreCase("c") ||
                    args[0].equalsIgnoreCase("coordinates")) {
                if (sender instanceof Player) {
                    // Suggest current location
                    Player player = (Player) sender;
                    return List.of(String.valueOf((int) player.getLocation().getY())).stream()
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("coord") ||
                    args[0].equalsIgnoreCase("c") ||
                    args[0].equalsIgnoreCase("coordinates")) {
                if (sender instanceof Player) {
                    // Suggest current location
                    Player player = (Player) sender;
                    return List.of(String.valueOf((int) player.getLocation().getZ())).stream()
                            .filter(s -> s.startsWith(args[3]))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 5) {
            if (args[0].equalsIgnoreCase("coord") ||
                    args[0].equalsIgnoreCase("c") ||
                    args[0].equalsIgnoreCase("coordinates")) {
                // Suggest worlds
                List<String> worlds = new ArrayList<>();
                worlds.add("~"); // Current world
                worlds.addAll(Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .collect(Collectors.toList()));

                return worlds.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 6) {
            if (args[0].equalsIgnoreCase("coord") ||
                    args[0].equalsIgnoreCase("c") ||
                    args[0].equalsIgnoreCase("coordinates")) {
                if (sender.hasPermission("yakrealms.admin.teleport")) {
                    // Suggest players
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[5].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        return new ArrayList<>();
    }
}