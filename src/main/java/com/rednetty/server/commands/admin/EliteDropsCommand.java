package com.rednetty.server.commands.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.drops.DropConfig;
import com.rednetty.server.mechanics.drops.DropsManager;
import com.rednetty.server.mechanics.drops.types.EliteDropConfig;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command for generating elite mob drops
 */
public class EliteDropsCommand implements CommandExecutor, TabCompleter {
    private final DropsManager dropsManager;
    private final YakRealms plugin;

    /**
     * Constructor
     */
    public EliteDropsCommand() {
        this.plugin = YakRealms.getInstance();
        this.dropsManager = DropsManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("yakrealms.admin.elitedrop")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("list")) {
            listElites(player);
            return true;
        }

        if (subCommand.equals("get")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /elitedrop get <mobName> [itemType]");
                return true;
            }

            String mobName = args[1].toLowerCase();
            int itemType = 1; // Default to weapon

            if (args.length >= 3) {
                try {
                    itemType = Integer.parseInt(args[2]);
                    if (itemType < 1 || itemType > 8) {
                        player.sendMessage(ChatColor.RED + "Item type must be between 1 and 8.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Item type must be a number between 1 and 8.");
                    return true;
                }
            }

            giveEliteDrop(player, mobName, itemType);
            return true;
        }

        if (subCommand.equals("debug")) {
            debugElites(player);
            return true;
        }

        showHelp(player);
        return true;
    }

    /**
     * Shows help information for the command
     *
     * @param player The player to show help to
     */
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "------------ Elite Drop Commands ------------");
        player.sendMessage(ChatColor.YELLOW + "/elitedrop list " + ChatColor.WHITE + "- List all available elite types");
        player.sendMessage(ChatColor.YELLOW + "/elitedrop get <mobName> [itemType] " + ChatColor.WHITE + "- Get an elite drop");
        player.sendMessage(ChatColor.YELLOW + "/elitedrop debug " + ChatColor.WHITE + "- Show debug information about elite configs");
        player.sendMessage(ChatColor.GRAY + "Item types: 1-4 = Weapons, 5-8 = Armor");
    }

    /**
     * Lists all available elite mob types
     *
     * @param player The player to show the list to
     */
    private void listElites(Player player) {
        Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();

        if (eliteConfigs == null || eliteConfigs.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No elite mob configurations found.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Available Elite Mob Types:");

        // Group elites by tier
        Map<Integer, List<String>> elitesByTier = eliteConfigs.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getValue().getTier(),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));

        // Display elites by tier
        for (int tier = 1; tier <= 6; tier++) {
            List<String> elites = elitesByTier.getOrDefault(tier, new ArrayList<>());

            if (!elites.isEmpty()) {
                // Only show Tier 6 if t6 is enabled
                if (tier == 6 && !YakRealms.isT6Enabled()) {
                    continue;
                }

                ChatColor tierColor;
                switch (tier) {
                    case 1:
                        tierColor = ChatColor.WHITE;
                        break;
                    case 2:
                        tierColor = ChatColor.GREEN;
                        break;
                    case 3:
                        tierColor = ChatColor.AQUA;
                        break;
                    case 4:
                        tierColor = ChatColor.LIGHT_PURPLE;
                        break;
                    case 5:
                        tierColor = ChatColor.YELLOW;
                        break;
                    case 6:
                        tierColor = ChatColor.BLUE;
                        break;
                    default:
                        tierColor = ChatColor.WHITE;
                }

                player.sendMessage(tierColor + "Tier " + tier + ": " +
                        ChatColor.GRAY + String.join(", ", elites));
            }
        }
    }

    /**
     * Shows debug information about elite configurations
     *
     * @param player The player to show debug info to
     */
    private void debugElites(Player player) {
        Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();

        if (eliteConfigs == null || eliteConfigs.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No elite configurations loaded.");
            player.sendMessage(ChatColor.RED + "Check console for error messages during startup.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Elite Drop Debug Info:");
        player.sendMessage(ChatColor.YELLOW + "Total elite configurations: " + eliteConfigs.size());

        // Count by tier
        Map<Integer, Long> countByTier = eliteConfigs.values().stream()
                .collect(Collectors.groupingBy(EliteDropConfig::getTier, Collectors.counting()));

        player.sendMessage(ChatColor.YELLOW + "By tier:");
        for (int tier = 1; tier <= 6; tier++) {
            player.sendMessage(ChatColor.GRAY + "  Tier " + tier + ": " + countByTier.getOrDefault(tier, 0L));
        }

        player.sendMessage(ChatColor.YELLOW + "First 3 elite configs:");
        int count = 0;
        for (Map.Entry<String, EliteDropConfig> entry : eliteConfigs.entrySet()) {
            if (count >= 3) break;

            EliteDropConfig config = entry.getValue();
            player.sendMessage(ChatColor.GRAY + "  " + entry.getKey() +
                    ": tier=" + config.getTier() +
                    ", rarity=" + config.getRarity() +
                    ", item count=" + config.getItemDetails().size());
            count++;
        }
    }

    /**
     * Gives a player an elite drop
     *
     * @param player   The player to give the item to
     * @param mobName  The elite mob type
     * @param itemType The item type (1-8)
     */
    private void giveEliteDrop(Player player, String mobName, int itemType) {
        EliteDropConfig config = DropConfig.getEliteDropConfig(mobName);
        if (config == null) {
            player.sendMessage(ChatColor.RED + "Elite mob type '" + mobName + "' not found.");
            return;
        }

        // Check for T6 access
        if (config.getTier() == 6 && !YakRealms.isT6Enabled()) {
            player.sendMessage(ChatColor.RED + "Tier 6 content is currently disabled.");
            return;
        }

        // Create the elite drop
        ItemStack item = dropsManager.createEliteDrop(mobName, itemType);

        if (item == null) {
            player.sendMessage(ChatColor.RED + "Failed to create elite drop. Check console for errors.");
            return;
        }

        // Give the item to the player
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);

            String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName() ?
                    item.getItemMeta().getDisplayName() :
                    TextUtil.formatItemName(item.getType().name());

            player.sendMessage(ChatColor.GREEN + "You received " + itemName + ChatColor.GREEN + "!");
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage(ChatColor.YELLOW + "Your inventory is full. The item was dropped at your feet.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("list", "get", "debug");
            return subCommands.stream()
                    .filter(subCmd -> subCmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            // Return list of elite mob types
            Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();
            if (eliteConfigs != null) {
                return eliteConfigs.keySet().stream()
                        .filter(mobType -> mobType.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("get")) {
            // Return item types 1-8
            List<String> itemTypes = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8");
            return itemTypes.stream()
                    .filter(type -> type.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}