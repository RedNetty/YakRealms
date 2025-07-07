package com.rednetty.server.commands.staff.admin;

import com.rednetty.server.mechanics.teleport.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for teleport-related commands
 */
public class TeleportBookCommand implements CommandExecutor, TabCompleter {
    private final TeleportManager teleportManager;
    private final TeleportBookSystem bookSystem;
    private final HearthstoneSystem hearthstoneSystem;
    private final PortalSystem portalSystem;

    /**
     * Creates a new teleport command handler
     */
    public TeleportBookCommand() {
        this.teleportManager = TeleportManager.getInstance();
        this.bookSystem = TeleportBookSystem.getInstance();
        this.hearthstoneSystem = HearthstoneSystem.getInstance();
        this.portalSystem = PortalSystem.getInstance();

        // Remove self-registration - this is now handled in YakRealms.registerCommands()
        // YakRealms.getInstance().getCommand("teleportbook").setExecutor(this);
        // YakRealms.getInstance().getCommand("teleportbook").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) && !cmd.getName().equalsIgnoreCase("teleportbook")) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (sender instanceof Player) ? (Player) sender : null;

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                showHelp(sender);
                return true;

            case "list":
                return handleListCommand(sender);

            case "create":
            case "book":
            case "get":
                return handleBookCommand(sender, args);

            case "hearthstone":
                return handleHearthstoneCommand(sender, args);

            case "add":
                return handleAddCommand(sender, args);

            case "update":
                return handleUpdateCommand(sender, args);

            case "remove":
                return handleRemoveCommand(sender, args);

            case "portal":
                return handlePortalCommand(sender, args);

            case "home":
                return handleHomeCommand(sender, args);

            case "info":
                return handleInfoCommand(sender, args);

            case "register":
                return handleRegisterCommand(sender, args);

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                showHelp(sender);
                return true;
        }
    }

    /**
     * Shows command help
     *
     * @param sender The command sender
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Teleport Book Commands =====");
        sender.sendMessage(ChatColor.YELLOW + "/teleportbook help" + ChatColor.GRAY + " - Show this help");
        sender.sendMessage(ChatColor.YELLOW + "/teleportbook list" + ChatColor.GRAY + " - List all teleport destinations");
        sender.sendMessage(ChatColor.YELLOW + "/teleportbook create <destination> [player]" + ChatColor.GRAY + " - Create a teleport book");
        sender.sendMessage(ChatColor.YELLOW + "/teleportbook info <destination>" + ChatColor.GRAY + " - Show destination info");
        sender.sendMessage(ChatColor.YELLOW + "/teleportbook register <book_id> <destination> <display_name>" +
                ChatColor.GRAY + " - Register a custom book");
        sender.sendMessage(ChatColor.YELLOW + "/teleportbook hearthstone [player] [home]" + ChatColor.GRAY + " - Create a hearthstone");

        if (sender.hasPermission("yakrealms.admin.teleport")) {
            sender.sendMessage(ChatColor.GOLD + "===== Admin Commands =====");
            sender.sendMessage(ChatColor.YELLOW + "/teleportbook add <id> <name> <cost> <premium>" + ChatColor.GRAY + " - Add a new destination");
            sender.sendMessage(ChatColor.YELLOW + "/teleportbook update <id> <name> <cost> <premium>" + ChatColor.GRAY + " - Update a destination");
            sender.sendMessage(ChatColor.YELLOW + "/teleportbook remove <id>" + ChatColor.GRAY + " - Remove a destination");
            sender.sendMessage(ChatColor.YELLOW + "/teleportbook portal <region> <destination>" + ChatColor.GRAY + " - Link a portal region");
            sender.sendMessage(ChatColor.YELLOW + "/teleportbook home <player> <destination>" + ChatColor.GRAY + " - Set a player's home");
        }
    }

    /**
     * Handles the list command
     *
     * @param sender The command sender
     * @return Command result
     */
    private boolean handleListCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Teleport Destinations =====");

        for (TeleportDestination destination : teleportManager.getAllDestinations()) {
            String premium = destination.isPremium() ? ChatColor.LIGHT_PURPLE + " [PREMIUM]" : "";
            sender.sendMessage(
                    ChatColor.YELLOW + destination.getId() + ChatColor.GRAY +
                            " - " + ChatColor.WHITE + destination.getDisplayName() +
                            ChatColor.GRAY + " (" + destination.getCost() + "g)" + premium
            );
        }

        return true;
    }

    /**
     * Handles the book command (create/get)
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handleBookCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /teleportbook create <destination> <player>");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /teleportbook create <destination> [player]");
            return true;
        }

        String destId = args[1].toLowerCase();
        TeleportDestination destination = teleportManager.getDestination(destId);

        if (destination == null) {
            sender.sendMessage(ChatColor.RED + "Unknown destination: " + destId);
            return true;
        }

        Player target = null;

        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);

            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
                return true;
            }

            if (!(sender instanceof Player) || !sender.equals(target)) {
                if (!sender.hasPermission("yakrealms.admin.teleport")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to give teleport books to others.");
                    return true;
                }
            }
        } else {
            // Default to sender if no player specified
            target = (Player) sender;
        }

        // Check if this is for shop display
        boolean isShop = args.length >= 3 && args[2].equalsIgnoreCase("shop") ||
                args.length >= 4 && args[3].equalsIgnoreCase("shop");

        // Create and give the book
        ItemStack book = bookSystem.createTeleportBook(destId, isShop);

        if (book == null) {
            sender.sendMessage(ChatColor.RED + "Failed to create teleport book.");
            return true;
        }

        target.getInventory().addItem(book);

        if (sender.equals(target)) {
            sender.sendMessage(ChatColor.GREEN + "You received a teleport book to " + destination.getDisplayName() + ".");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Gave a teleport book to " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "You received a teleport book to " + destination.getDisplayName() + ".");
        }

        return true;
    }

    /**
     * Handles the info command
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /teleportbook info <destination>");
            return true;
        }

        String destId = args[1].toLowerCase();
        TeleportDestination destination = teleportManager.getDestination(destId);

        if (destination == null) {
            sender.sendMessage(ChatColor.RED + "Unknown destination: " + destId);
            return true;
        }

        Location loc = destination.getLocation();

        sender.sendMessage(ChatColor.GOLD + "===== Destination: " + destination.getDisplayName() + " =====");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + destination.getId());
        sender.sendMessage(ChatColor.YELLOW + "Display Name: " + ChatColor.WHITE + destination.getDisplayName());
        sender.sendMessage(ChatColor.YELLOW + "Cost: " + ChatColor.WHITE + destination.getCost() + " gems");
        sender.sendMessage(ChatColor.YELLOW + "Premium: " + ChatColor.WHITE + (destination.isPremium() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE +
                String.format("%s (%.1f, %.1f, %.1f)",
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));

        return true;
    }

    /**
     * Handles the register command
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handleRegisterCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yakrealms.command.teleportbook")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /teleportbook register <book_id> <destination> <display_name>");
            return true;
        }

        String bookId = args[1].toLowerCase();
        String destId = args[2].toLowerCase();

        TeleportDestination destination = teleportManager.getDestination(destId);
        if (destination == null) {
            sender.sendMessage(ChatColor.RED + "Unknown destination: " + destId);
            return true;
        }

        // Build display name from remaining args
        StringBuilder displayName = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (i > 3) displayName.append(" ");
            displayName.append(args[i]);
        }

        // Create custom lore
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Teleports the user to " + destination.getDisplayName() + ".");
        lore.add(ChatColor.GRAY + "Custom teleport book.");

        // Register the book
        ItemStack book = bookSystem.registerCustomBook(bookId, destId,
                ChatColor.translateAlternateColorCodes('&', displayName.toString()), lore);

        if (book == null) {
            sender.sendMessage(ChatColor.RED + "Failed to register custom book.");
            return true;
        }

        // Give to player if they are in-game
        if (sender instanceof Player) {
            ((Player) sender).getInventory().addItem(book);
            sender.sendMessage(ChatColor.GREEN + "Created and registered custom book '" + bookId + "'.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Registered custom book '" + bookId + "'.");
        }

        return true;
    }

    /**
     * Handles the hearthstone command
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handleHearthstoneCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /teleportbook hearthstone <player> [home]");
            return true;
        }

        Player target = null;
        String homeId = null;

        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);

            if (target == null) {
                // Check if this is a destination ID instead of player
                TeleportDestination destination = teleportManager.getDestination(args[1]);

                if (destination != null) {
                    // It's a destination ID, so the sender is the target
                    homeId = args[1];
                    target = (Player) sender;
                } else {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
            }

            if (args.length >= 3) {
                homeId = args[2].toLowerCase();
            }
        } else {
            // Default to sender
            target = (Player) sender;
        }

        // Check permissions for giving to others
        if (!(sender instanceof Player) || !sender.equals(target)) {
            if (!sender.hasPermission("yakrealms.admin.teleport")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to give hearthstones to others.");
                return true;
            }
        }

        // Create and give the hearthstone
        ItemStack hearthstone = hearthstoneSystem.createHearthstone(homeId);

        if (hearthstone == null) {
            sender.sendMessage(ChatColor.RED + "Failed to create hearthstone.");
            return true;
        }

        target.getInventory().addItem(hearthstone);

        if (sender.equals(target)) {
            sender.sendMessage(ChatColor.GREEN + "You received a hearthstone.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Gave a hearthstone to " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "You received a hearthstone.");
        }

        return true;
    }

    /**
     * Handles the add command
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handleAddCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.teleport")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /teleportbook add <id> <name> [cost] [premium]");
            return true;
        }

        String id = args[1].toLowerCase();
        String name = args[2];
        int cost = 50;
        boolean premium = false;

        if (args.length >= 4) {
            try {
                cost = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid cost: " + args[3]);
                return true;
            }
        }

        if (args.length >= 5) {
            premium = Boolean.parseBoolean(args[4]);
        }

        Player player = (Player) sender;
        Location location = player.getLocation();

        // Create and register the destination
        TeleportDestination destination = new TeleportDestination(id, name, location, cost, premium);

        if (teleportManager.registerDestination(destination)) {
            sender.sendMessage(ChatColor.GREEN + "Destination added: " + name);
        } else {
            sender.sendMessage(ChatColor.RED + "A destination with ID '" + id + "' already exists.");
        }

        return true;
    }

    /**
     * Handles the update command
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handleUpdateCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.teleport")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /teleportbook update <id> <name> [cost] [premium]");
            return true;
        }

        String id = args[1].toLowerCase();
        TeleportDestination existing = teleportManager.getDestination(id);

        if (existing == null) {
            sender.sendMessage(ChatColor.RED + "Destination not found: " + id);
            return true;
        }

        String name = args[2];
        int cost = existing.getCost();
        boolean premium = existing.isPremium();

        if (args.length >= 4) {
            try {
                cost = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid cost: " + args[3]);
                return true;
            }
        }

        if (args.length >= 5) {
            premium = Boolean.parseBoolean(args[4]);
        }

        Player player = (Player) sender;
        Location location = player.getLocation();

        // Create and update the destination
        TeleportDestination destination = new TeleportDestination(id, name, location, cost, premium);

        if (teleportManager.updateDestination(destination)) {
            sender.sendMessage(ChatColor.GREEN + "Destination updated: " + name);
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to update destination.");
        }

        return true;
    }

    /**
     * Handles the remove command
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.teleport")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /teleportbook remove <id>");
            return true;
        }

        String id = args[1].toLowerCase();

        if (teleportManager.removeDestination(id)) {
            sender.sendMessage(ChatColor.GREEN + "Destination removed: " + id);
        } else {
            sender.sendMessage(ChatColor.RED + "Destination not found: " + id);
        }

        return true;
    }

    /**
     * Handles the portal command
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handlePortalCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.teleport")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /teleportbook portal <region> <destination>");
            return true;
        }

        String regionId = args[1].toLowerCase();
        String destId = args[2].toLowerCase();

        TeleportDestination destination = teleportManager.getDestination(destId);

        if (destination == null) {
            sender.sendMessage(ChatColor.RED + "Destination not found: " + destId);
            return true;
        }

        portalSystem.registerPortalDestination(regionId, destId);
        sender.sendMessage(ChatColor.GREEN + "Portal region '" + regionId + "' linked to destination '" + destId + "'");

        return true;
    }

    /**
     * Handles the home command
     *
     * @param sender The command sender
     * @param args   Command arguments
     * @return Command result
     */
    private boolean handleHomeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.teleport")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /teleportbook home <player> <destination>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }

        String destId = args[2].toLowerCase();
        TeleportDestination destination = teleportManager.getDestination(destId);

        if (destination == null) {
            sender.sendMessage(ChatColor.RED + "Destination not found: " + destId);
            return true;
        }

        if (hearthstoneSystem.setPlayerHomeLocation(target, destId)) {
            sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s home location to " + destination.getDisplayName());

            if (!sender.equals(target)) {
                target.sendMessage(ChatColor.GREEN + "Your home location has been set to " + destination.getDisplayName());
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to set home location.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("help");
            subCommands.add("list");
            subCommands.add("create");
            subCommands.add("get");
            subCommands.add("info");
            subCommands.add("register");
            subCommands.add("hearthstone");

            if (sender.hasPermission("yakrealms.admin.teleport")) {
                subCommands.add("add");
                subCommands.add("update");
                subCommands.add("remove");
                subCommands.add("portal");
                subCommands.add("home");
            }

            String input = args[0].toLowerCase();
            return subCommands.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            switch (subCommand) {
                case "create":
                case "get":
                case "info":
                case "update":
                case "remove":
                case "portal":
                case "register":
                    // For commands that need a destination ID
                    for (TeleportDestination dest : teleportManager.getAllDestinations()) {
                        if (dest.getId().startsWith(input)) {
                            completions.add(dest.getId());
                        }
                    }
                    break;

                case "home":
                    // Player names for admin commands
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(input)) {
                            completions.add(player.getName());
                        }
                    }
                    break;

                case "hearthstone":
                    // First arg can be player or destination
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(input)) {
                            completions.add(player.getName());
                        }
                    }

                    for (TeleportDestination dest : teleportManager.getAllDestinations()) {
                        if (dest.getId().startsWith(input)) {
                            completions.add(dest.getId());
                        }
                    }
                    break;
            }

            return completions;
        }

        // Additional tab completions for deeper command arguments
        if (args.length >= 3) {
            String subCommand = args[0].toLowerCase();
            String input = args[args.length - 1].toLowerCase();

            switch (subCommand) {
                case "portal":
                case "home":
                case "register":
                    if (args.length == 3) {
                        // Destination ID
                        for (TeleportDestination dest : teleportManager.getAllDestinations()) {
                            if (dest.getId().startsWith(input)) {
                                completions.add(dest.getId());
                            }
                        }
                    }
                    break;

                case "create":
                case "get":
                    if (args.length == 3) {
                        // Player name or "shop"
                        if ("shop".startsWith(input)) {
                            completions.add("shop");
                        }

                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(input)) {
                                completions.add(player.getName());
                            }
                        }
                    }
                    break;
            }

            return completions;
        }

        return completions;
    }
}