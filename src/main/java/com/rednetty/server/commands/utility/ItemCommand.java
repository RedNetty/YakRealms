package com.rednetty.server.commands.utility;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.scroll.ScrollGenerator;
import com.rednetty.server.mechanics.player.items.SpeedfishMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Comprehensive command for spawning all custom items including enhancement systems
 */
public class ItemCommand implements CommandExecutor, TabCompleter {
    private final YakRealms plugin;
    private final SpeedfishMechanics speedfishMechanics;

    /**
     * Constructor
     *
     * @param plugin The YakRealms plugin instance
     */
    public ItemCommand(YakRealms plugin) {
        this.plugin = plugin;
        this.speedfishMechanics = plugin.getSpeedfishMechanics();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("yakrealms.admin.item")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sendHelp(player);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "speedfish":
                return handleSpeedfishCommand(player, args);
            case "journal":
                return handleJournalCommand(player, args);
            case "scroll":
                return handleScrollCommand(player, args);
            case "awakening":
                return handleAwakeningCommand(player, args);
            case "bindingrune":
            case "rune":
                return handleBindingRuneCommand(player, args);
            case "unbinder":
                return handleUnbindingSolventCommand(player, args);
            case "corruption":
                return handleCorruptionCommand(player, args);
            case "purification":
                return handlePurificationCommand(player, args);
            case "essence":
                return handleEssenceCommand(player, args);
            case "extractor":
                return handleExtractorCommand(player, args);
            case "infuser":
                return handleInfuserCommand(player, args);
            case "hammer":
            case "forge":
                return handleHammerCommand(player, args);
            case "custom":
                return handleCustomItemCommand(player, args);
            case "give":
                return handleGiveCommand(player, args);
            case "help":
                sendHelp(player);
                return true;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCmd);
                sendHelp(player);
                return true;
        }
    }

    /**
     * Handle the speedfish subcommand
     */
    private boolean handleSpeedfishCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /item speedfish <tier>");
            return true;
        }

        try {
            int tier = Integer.parseInt(args[1]);
            if (tier < 1 || tier > 5) {
                player.sendMessage(ChatColor.RED + "Tier must be between 1 and 5.");
                return true;
            }

            ItemStack speedfish = SpeedfishMechanics.createSpeedfish(tier, false);
            player.getInventory().addItem(speedfish);
            player.sendMessage(ChatColor.GREEN + "Created speedfish of tier " + tier);
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid tier: " + args[1]);
            return true;
        }
    }

    /**
     * Handle the journal subcommand
     */
    private boolean handleJournalCommand(Player player, String[] args) {
        if (args.length < 2) {
            // Default to empty journal
            ItemStack journal = plugin.getJournalSystem().createEmptyJournal();
            player.getInventory().addItem(journal);
            player.sendMessage(ChatColor.GREEN + "Created an empty character journal");
            return true;
        }

        String option = args[1].toLowerCase();

        if (option.equals("player")) {
            Player target = player;
            if (args.length >= 3) {
                target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
                    return true;
                }
            }

            ItemStack journal = plugin.getJournalSystem().createPlayerJournal(target);
            player.getInventory().addItem(journal);
            player.sendMessage(ChatColor.GREEN + "Created a player journal for " + target.getName());
            return true;
        } else if (option.equals("empty")) {
            ItemStack journal = plugin.getJournalSystem().createEmptyJournal();
            player.getInventory().addItem(journal);
            player.sendMessage(ChatColor.GREEN + "Created an empty character journal");
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Unknown journal type: " + option);
            player.sendMessage(ChatColor.GRAY + "Available types: player, empty");
            return true;
        }
    }

    /**
     * Handle the scroll subcommand
     */
    private boolean handleScrollCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /item scroll <protection|enhancement> <tier>");
            return true;
        }

        String scrollType = args[1].toLowerCase();
        ScrollGenerator scrollGen = plugin.getScrollManager().getScrollGenerator();

        if (scrollType.equals("protection")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /item scroll protection <tier>");
                return true;
            }

            try {
                int tier = Integer.parseInt(args[2]);
                if (tier < 0 || tier > 5) {
                    player.sendMessage(ChatColor.RED + "Tier must be between 0 and 5.");
                    return true;
                }

                ItemStack scroll = scrollGen.createProtectionScroll(tier);
                player.getInventory().addItem(scroll);
                player.sendMessage(ChatColor.GREEN + "Created protection scroll of tier " + tier);
                return true;
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid tier: " + args[2]);
                return true;
            }
        } else if (scrollType.equals("enhancement")) {
            if (args.length < 4) {
                player.sendMessage(ChatColor.RED + "Usage: /item scroll enhancement <armor|weapon> <tier>");
                return true;
            }

            String enhancementType = args[2].toLowerCase();
            boolean isArmor = enhancementType.equals("armor");
            boolean isWeapon = enhancementType.equals("weapon");

            if (!isArmor && !isWeapon) {
                player.sendMessage(ChatColor.RED + "Enhancement type must be either 'armor' or 'weapon'");
                return true;
            }

            try {
                int tier = Integer.parseInt(args[3]);
                if (tier < 1 || tier > 5) {
                    player.sendMessage(ChatColor.RED + "Tier must be between 1 and 5.");
                    return true;
                }

                ItemStack scroll;
                if (isArmor) {
                    scroll = scrollGen.createArmorEnhancementScroll(tier);
                } else {
                    scroll = scrollGen.createWeaponEnhancementScroll(tier);
                }

                player.getInventory().addItem(scroll);
                player.sendMessage(ChatColor.GREEN + "Created " + enhancementType + " enhancement scroll of tier " + tier);
                return true;
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid tier: " + args[3]);
                return true;
            }
        } else {
            player.sendMessage(ChatColor.RED + "Unknown scroll type: " + scrollType);
            player.sendMessage(ChatColor.GRAY + "Available types: protection, enhancement");
            return true;
        }
    }

    /**
     * Handle the awakening subcommand
     */
    private boolean handleAwakeningCommand(Player player, String[] args) {
        return false;
    }

    /**
     * Handle the binding rune subcommand
     */
    private boolean handleBindingRuneCommand(Player player, String[] args) {
        return false;
    }

    /**
     * Handle the unbinding solvent subcommand
     */
    private boolean handleUnbindingSolventCommand(Player player, String[] args) {
        return false;
    }

    /**
     * Handle the corruption subcommand
     */
    private boolean handleCorruptionCommand(Player player, String[] args) {
        return false;
    }

    /**
     * Handle the purification subcommand
     */
    private boolean handlePurificationCommand(Player player, String[] args) {
        return false;
    }

    /**
     * Handle the essence subcommand
     */
    private boolean handleEssenceCommand(Player player, String[] args) {
        return false;
    }

    /**
     * Handle the extractor subcommand
     */
    private boolean handleExtractorCommand(Player player, String[] args) {
        return false;
    }


    /**
     * Handle the infuser subcommand
     */
    private boolean handleInfuserCommand(Player player, String[] args) {
        return false;
    }

    /**
     * Handle the hammer subcommand
     */
    private boolean handleHammerCommand(Player player, String[] args) {
        return false;
    }

    /**
     * Handle the custom item subcommand
     */
    private boolean handleCustomItemCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /item custom <material> <name>");
            return true;
        }

        String materialName = args[1].toUpperCase();
        Material material;

        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid material: " + materialName);
            return true;
        }

        // Build the name from args
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            nameBuilder.append(args[i]).append(" ");
        }
        String itemName = ChatColor.translateAlternateColorCodes('&', nameBuilder.toString().trim());

        // Create the item
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(itemName);
        item.setItemMeta(meta);

        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.GREEN + "Created custom item: " + itemName);
        return true;
    }

    /**
     * Handle the give command
     */
    private boolean handleGiveCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /item give <player> <material> [amount]");
            return true;
        }

        // Get target player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }

        // Get material
        String materialName = args[2].toUpperCase();
        Material material;

        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid material: " + materialName);
            return true;
        }

        // Get amount
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) {
                    player.sendMessage(ChatColor.RED + "Amount must be positive.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
                return true;
            }
        }

        // Create the item
        ItemStack item = new ItemStack(material, amount);

        // Give to target
        target.getInventory().addItem(item);
        player.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + material.name() + " to " + target.getName());
        target.sendMessage(ChatColor.GREEN + "You received " + amount + "x " + material.name() + " from " + player.getName());
        return true;
    }

    /**
     * Send comprehensive help message
     */
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=====  Item Command Help =====");
        player.sendMessage("");

        player.sendMessage(ChatColor.YELLOW + "Basic Items:");
        player.sendMessage(ChatColor.GRAY + "/item speedfish <tier>" + ChatColor.WHITE + " - Create a speedfish");
        player.sendMessage(ChatColor.GRAY + "/item journal [player|empty] [player]" + ChatColor.WHITE + " - Create a journal");
        player.sendMessage(ChatColor.GRAY + "/item scroll <protection|enhancement> <tier>" + ChatColor.WHITE + " - Create a scroll");

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Enhancement Systems:");
        player.sendMessage(ChatColor.GRAY + "/item awakening <stone|true>" + ChatColor.WHITE + " - Create awakening stones");
        player.sendMessage(ChatColor.GRAY + "/item rune <fire|ice|poison|lightning|shadow|light>" + ChatColor.WHITE + " - Create binding runes");
        player.sendMessage(ChatColor.GRAY + "/item unbinder" + ChatColor.WHITE + " - Create unbinding solvent");
        player.sendMessage(ChatColor.GRAY + "/item corruption <minor|major>" + ChatColor.WHITE + " - Create corruption vials");
        player.sendMessage(ChatColor.GRAY + "/item purification" + ChatColor.WHITE + " - Create purification tome");

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Essence System:");
        player.sendMessage(ChatColor.GRAY + "/item extractor" + ChatColor.WHITE + " - Create essence extractor");
        player.sendMessage(ChatColor.GRAY + "/item infuser" + ChatColor.WHITE + " - Create essence infuser");
        player.sendMessage(ChatColor.GRAY + "/item essence <type> <value> <quality> <tier>" + ChatColor.WHITE + " - Create essence crystal");

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Forge System:");
        player.sendMessage(ChatColor.GRAY + "/item hammer <apprentice|master|legendary>" + ChatColor.WHITE + " - Create forge hammers");

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Utilities:");
        player.sendMessage(ChatColor.GRAY + "/item custom <material> <name>" + ChatColor.WHITE + " - Create a custom item");
        player.sendMessage(ChatColor.GRAY + "/item give <player> <material> [amount]" + ChatColor.WHITE + " - Give items to a player");
        player.sendMessage(ChatColor.GRAY + "/item help" + ChatColor.WHITE + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;
        if (!player.hasPermission("yakrealms.admin.item")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Stream.of("speedfish", "journal", "scroll", "awakening", "rune", "bindingrune",
                            "unbinder", "corruption", "purification", "essence", "extractor", "infuser",
                            "hammer", "forge", "custom", "give", "help")
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String subCmd = args[0].toLowerCase();

        // Tab completion for speedfish
        if (subCmd.equals("speedfish") && args.length == 2) {
            return Stream.of("1", "2", "3", "4", "5")
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        // Tab completion for journal
        if (subCmd.equals("journal")) {
            if (args.length == 2) {
                return Stream.of("player", "empty")
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 3 && args[1].toLowerCase().equals("player")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion for scroll
        if (subCmd.equals("scroll")) {
            if (args.length == 2) {
                return Stream.of("protection", "enhancement")
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 3 && args[1].toLowerCase().equals("protection")) {
                return Stream.of("0", "1", "2", "3", "4", "5")
                        .filter(s -> s.startsWith(args[2]))
                        .collect(Collectors.toList());
            } else if (args.length == 3 && args[1].toLowerCase().equals("enhancement")) {
                return Stream.of("armor", "weapon")
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 4 && args[1].toLowerCase().equals("enhancement")) {
                return Stream.of("1", "2", "3", "4", "5")
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion for awakening
        if (subCmd.equals("awakening") && args.length == 2) {
            return Stream.of("stone", "true")
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion for binding runes
        if ((subCmd.equals("rune") || subCmd.equals("bindingrune")) && args.length == 2) {
            return Stream.of("fire", "ice", "poison", "lightning", "shadow", "light")
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion for corruption
        if (subCmd.equals("corruption") && args.length == 2) {
            return Stream.of("minor", "major")
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion for essence
        if (subCmd.equals("essence")) {
            if (args.length == 2) {
                return Stream.of("damage", "hp", "armor", "dps", "strength", "intellect", "vitality",
                                "dexterity", "critical_hit", "life_steal", "accuracy", "dodge", "block",
                                "energy_regen", "hp_regen", "fire_damage", "ice_damage", "poison_damage", "pure_damage")
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 3) {
                return Stream.of("5", "10", "15", "20", "25", "50", "100")
                        .filter(s -> s.startsWith(args[2]))
                        .collect(Collectors.toList());
            } else if (args.length == 4) {
                return Stream.of("flawed", "normal", "perfect")
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 5) {
                return Stream.of("1", "2", "3", "4", "5", "6")
                        .filter(s -> s.startsWith(args[4]))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion for hammer
        if ((subCmd.equals("hammer") || subCmd.equals("forge")) && args.length == 2) {
            return Stream.of("apprentice", "master", "legendary")
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion for custom
        if (subCmd.equals("custom") && args.length == 2) {
            return Arrays.stream(Material.values())
                    .map(Material::name)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .limit(20) // Limit to first 20 matches to avoid overwhelming the player
                    .collect(Collectors.toList());
        }

        // Tab completion for give
        if (subCmd.equals("give")) {
            if (args.length == 2) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 3) {
                return Arrays.stream(Material.values())
                        .map(Material::name)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .limit(20) // Limit to first 20 matches to avoid overwhelming the player
                        .collect(Collectors.toList());
            } else if (args.length == 4) {
                return Stream.of("1", "8", "16", "32", "64")
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}