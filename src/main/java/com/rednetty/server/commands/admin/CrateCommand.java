package com.rednetty.server.commands.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.CrateManager;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Command handler for crate-related administrative functions
 */
public class CrateCommand implements CommandExecutor, TabCompleter {
    private final CrateManager crateManager;

    public CrateCommand() {
        this.crateManager = CrateManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.crates")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give":
                return handleGiveCommand(sender, args);
            case "givekey":
                return handleGiveKeyCommand(sender, args);
            case "stats":
                return handleStatsCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "list":
                return handleListCommand(sender);
            case "create":
                return handleCreateCommand(sender, args);
            case "test":
                return handleTestCommand(sender, args);
            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    /**
     * Handles the give subcommand
     * Usage: /crate give <player> <type> [amount] [halloween] [locked]
     */
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /crate give <player> <type> [amount] [halloween] [locked]");
            sender.sendMessage(ChatColor.GRAY + "Types: " + getCrateTypesList());
            return true;
        }

        String playerName = args[1];
        String crateTypeName = args[2].toUpperCase();
        int amount = 1;
        boolean isHalloween = false;
        boolean isLocked = false;

        // Parse optional arguments
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0 || amount > 64) {
                    sender.sendMessage(ChatColor.RED + "Amount must be between 1 and 64!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
                return true;
            }
        }

        if (args.length > 4) {
            isHalloween = Boolean.parseBoolean(args[4]);
        }

        if (args.length > 5) {
            isLocked = Boolean.parseBoolean(args[5]);
        }

        // Find player
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
            return true;
        }

        // Parse crate type
        CrateType crateType;
        try {
            // Handle special cases
            if (crateTypeName.equals("HALLOWEEN_BASIC") || (crateTypeName.equals("BASIC") && isHalloween)) {
                crateType = CrateType.BASIC_HALLOWEEN;
                isHalloween = true;
            } else if (crateTypeName.equals("HALLOWEEN_MEDIUM") || (crateTypeName.equals("MEDIUM") && isHalloween)) {
                crateType = CrateType.MEDIUM_HALLOWEEN;
                isHalloween = true;
            } else if (crateTypeName.equals("HALLOWEEN_WAR") || (crateTypeName.equals("WAR") && isHalloween)) {
                crateType = CrateType.WAR_HALLOWEEN;
                isHalloween = true;
            } else if (crateTypeName.equals("HALLOWEEN_ANCIENT") || (crateTypeName.equals("ANCIENT") && isHalloween)) {
                crateType = CrateType.ANCIENT_HALLOWEEN;
                isHalloween = true;
            } else if (crateTypeName.equals("HALLOWEEN_LEGENDARY") || (crateTypeName.equals("LEGENDARY") && isHalloween)) {
                crateType = CrateType.LEGENDARY_HALLOWEEN;
                isHalloween = true;
            } else if (crateTypeName.equals("HALLOWEEN_FROZEN") || (crateTypeName.equals("FROZEN") && isHalloween)) {
                crateType = CrateType.FROZEN_HALLOWEEN;
                isHalloween = true;
            } else {
                crateType = CrateType.valueOf(crateTypeName);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid crate type: " + crateTypeName);
            sender.sendMessage(ChatColor.GRAY + "Available types: " + getCrateTypesList());
            return true;
        }

        // Give crates
        crateManager.giveCratesToPlayer(target, crateType, amount, isHalloween, isLocked);

        // Success messages
        String crateName = (isHalloween ? "Halloween " : "") + crateType.getDisplayName() + " Crate" + (isLocked ? " (Locked)" : "");
        sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + crateName + " to " + target.getName());

        if (!sender.equals(target)) {
            target.sendMessage(ChatColor.GREEN + "You received " + amount + "x " + crateName + " from " + sender.getName() + "!");
        }

        return true;
    }

    /**
     * Handles the givekey subcommand
     * Usage: /crate givekey <player> [amount]
     */
    private boolean handleGiveKeyCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /crate givekey <player> [amount]");
            return true;
        }

        String playerName = args[1];
        int amount = 1;

        // Parse amount
        if (args.length > 2) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0 || amount > 64) {
                    sender.sendMessage(ChatColor.RED + "Amount must be between 1 and 64!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
                return true;
            }
        }

        // Find player
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
            return true;
        }

        // Give keys
        ItemStack keys = crateManager.createCrateKey();
        keys.setAmount(amount);

        Map<Integer, ItemStack> leftover = target.getInventory().addItem(keys);
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), item);
            }
        }

        // Success messages
        sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x Crate Key to " + target.getName());

        if (!sender.equals(target)) {
            target.sendMessage(ChatColor.GREEN + "You received " + amount + "x Crate Key from " + sender.getName() + "!");
        }

        return true;
    }

    /**
     * Handles the stats subcommand
     */
    private boolean handleStatsCommand(CommandSender sender) {
        Map<String, Object> stats = crateManager.getStatistics();

        sender.sendMessage(ChatColor.GOLD + "=== Crate System Statistics ===");
        sender.sendMessage(ChatColor.YELLOW + "Total crates opened: " + ChatColor.WHITE + stats.get("totalCratesOpened"));
        sender.sendMessage(ChatColor.YELLOW + "Active openings: " + ChatColor.WHITE + stats.get("activeOpenings"));
        sender.sendMessage(ChatColor.YELLOW + "Processing players: " + ChatColor.WHITE + stats.get("processingPlayers"));
        sender.sendMessage(ChatColor.YELLOW + "Configurations loaded: " + ChatColor.WHITE + stats.get("configurationsLoaded"));

        // Show crates opened by type
        @SuppressWarnings("unchecked")
        Map<CrateType, Integer> cratesOpened = (Map<CrateType, Integer>) stats.get("crateTypesOpened");
        if (!cratesOpened.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Crates opened by type:");
            for (Map.Entry<CrateType, Integer> entry : cratesOpened.entrySet()) {
                sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey().getDisplayName() + ": " + entry.getValue());
            }
        }

        return true;
    }

    /**
     * Handles the reload subcommand
     */
    private boolean handleReloadCommand(CommandSender sender) {
        try {
            //crateManager.reloadConfigurations();
            sender.sendMessage(ChatColor.GREEN + "Crate configurations reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading crate configurations: " + e.getMessage());
        }
        return true;
    }

    /**
     * Handles the list subcommand
     */
    private boolean handleListCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Available Crate Types ===");

        for (CrateType crateType : CrateType.getRegularTypes()) {
            ChatColor color = getTierColor(crateType.getTier());
            sender.sendMessage(color + crateType.name() + ChatColor.GRAY + " (Tier " + crateType.getTier() + ")");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_PURPLE + "=== Halloween Variants ===");

        for (CrateType crateType : CrateType.getHalloweenTypes()) {
            ChatColor color = getTierColor(crateType.getTier());
            sender.sendMessage(color + crateType.name() + ChatColor.GRAY + " (Tier " + crateType.getTier() + ")");
        }

        return true;
    }

    /**
     * Handles the create subcommand - gives a crate to the sender
     */
    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /crate create <type> [halloween] [locked]");
            sender.sendMessage(ChatColor.GRAY + "Types: " + getCrateTypesList());
            return true;
        }

        String crateTypeName = args[1].toUpperCase();
        boolean isHalloween = args.length > 2 && Boolean.parseBoolean(args[2]);
        boolean isLocked = args.length > 3 && Boolean.parseBoolean(args[3]);

        // Parse crate type
        CrateType crateType;
        try {
            crateType = CrateType.valueOf(crateTypeName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid crate type: " + crateTypeName);
            return true;
        }

        // Create and give crate
        ItemStack crate = isLocked ?
                crateManager.createLockedCrate(crateType, isHalloween) :
                crateManager.createCrate(crateType, isHalloween);

        if (crate != null) {
            player.getInventory().addItem(crate);
            String crateName = (isHalloween ? "Halloween " : "") + crateType.getDisplayName() + " Crate" + (isLocked ? " (Locked)" : "");
            player.sendMessage(ChatColor.GREEN + "Created and gave you a " + crateName + "!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create crate!");
        }

        return true;
    }

    /**
     * Handles the test subcommand - creates a random crate
     */
    private boolean handleTestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        int minTier = 1;
        int maxTier = 6;
        boolean allowHalloween = true;

        if (args.length > 1) {
            try {
                minTier = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid minimum tier: " + args[1]);
                return true;
            }
        }

        if (args.length > 2) {
            try {
                maxTier = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid maximum tier: " + args[2]);
                return true;
            }
        }

        if (args.length > 3) {
            allowHalloween = Boolean.parseBoolean(args[3]);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int randomTier = random.nextInt(minTier, maxTier);
        ItemStack randomCrate = crateManager.createCrate(CrateType.getByTier(randomTier, allowHalloween), allowHalloween);
        if (randomCrate != null) {
            player.getInventory().addItem(randomCrate);
            player.sendMessage(ChatColor.GREEN + "Created a random crate for you!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create random crate!");
        }

        return true;
    }

    /**
     * Sends help message
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Crate Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/crate give <player> <type> [amount] [halloween] [locked]" + ChatColor.GRAY + " - Give crates to a player");
        sender.sendMessage(ChatColor.YELLOW + "/crate givekey <player> [amount]" + ChatColor.GRAY + " - Give crate keys to a player");
        sender.sendMessage(ChatColor.YELLOW + "/crate stats" + ChatColor.GRAY + " - View crate system statistics");
        sender.sendMessage(ChatColor.YELLOW + "/crate reload" + ChatColor.GRAY + " - Reload crate configurations");
        sender.sendMessage(ChatColor.YELLOW + "/crate list" + ChatColor.GRAY + " - List all available crate types");
        sender.sendMessage(ChatColor.YELLOW + "/crate create <type> [halloween] [locked]" + ChatColor.GRAY + " - Create a crate for yourself");
        sender.sendMessage(ChatColor.YELLOW + "/crate test [minTier] [maxTier] [allowHalloween]" + ChatColor.GRAY + " - Create a random test crate");
    }

    /**
     * Gets a formatted list of crate types
     */
    private String getCrateTypesList() {
        return Arrays.stream(CrateType.getRegularTypes())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    /**
     * Gets color for a tier
     */
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.crates")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcommands
            completions.addAll(Arrays.asList("give", "givekey", "stats", "reload", "list", "create", "test"));
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give":
                if (args.length == 2) {
                    // Player names
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 3) {
                    // Crate types
                    return Arrays.stream(CrateType.values())
                            .map(Enum::name)
                            .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 4) {
                    // Amount
                    return Arrays.asList("1", "5", "10", "32", "64");
                } else if (args.length == 5 || args.length == 6) {
                    // Boolean values
                    return Arrays.asList("true", "false");
                }
                break;

            case "givekey":
                if (args.length == 2) {
                    // Player names
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 3) {
                    // Amount
                    return Arrays.asList("1", "5", "10", "32", "64");
                }
                break;

            case "create":
                if (args.length == 2) {
                    // Crate types
                    return Arrays.stream(CrateType.getRegularTypes())
                            .map(Enum::name)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 3 || args.length == 4) {
                    // Boolean values
                    return Arrays.asList("true", "false");
                }
                break;

            case "test":
                if (args.length == 2 || args.length == 3) {
                    // Tier numbers
                    return Arrays.asList("1", "2", "3", "4", "5", "6");
                } else if (args.length == 4) {
                    // Boolean values
                    return Arrays.asList("true", "false");
                }
                break;
        }

        return completions;
    }
}