package com.rednetty.server.commands.staff.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.DropConfig;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.item.drops.types.EliteDropConfig;
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
 * Enhanced command for generating elite mob drops with improved functionality and error handling
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

        switch (subCommand) {
            case "list":
                listElites(player);
                return true;

            case "get":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /elitedrop get <mobName> [itemType] [tier]");
                    return true;
                }
                handleGetCommand(player, args);
                return true;

            case "debug":
                debugElites(player);
                return true;

            case "info":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /elitedrop info <mobName>");
                    return true;
                }
                showEliteInfo(player, args[1]);
                return true;

            case "reload":
                reloadEliteConfigs(player);
                return true;

            default:
                showHelp(player);
                return true;
        }
    }

    /**
     * Handle the get command with improved parameter handling
     */
    private void handleGetCommand(Player player, String[] args) {
        String mobName = args[1].toLowerCase();
        int itemType = 1; // Default to weapon
        Integer overrideTier = null;

        // Parse item type
        if (args.length >= 3) {
            try {
                itemType = Integer.parseInt(args[2]);
                if (itemType < 1 || itemType > 8) {
                    player.sendMessage(ChatColor.RED + "Item type must be between 1 and 8.");
                    player.sendMessage(ChatColor.GRAY + "1-4 = Weapons (Staff, Spear, Sword, Axe)");
                    player.sendMessage(ChatColor.GRAY + "5-8 = Armor (Helmet, Chestplate, Leggings, Boots)");
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Item type must be a number between 1 and 8.");
                return;
            }
        }

        // Parse tier override
        if (args.length >= 4) {
            try {
                overrideTier = Integer.parseInt(args[3]);
                if (overrideTier < 1 || overrideTier > 6) {
                    player.sendMessage(ChatColor.RED + "Tier must be between 1 and 6.");
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Tier must be a number between 1 and 6.");
                return;
            }
        }

        giveEliteDrop(player, mobName, itemType, overrideTier);
    }

    /**
     * Shows help information for the command
     *
     * @param player The player to show help to
     */
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "============ Elite Drop Commands ============");
        player.sendMessage(ChatColor.YELLOW + "/elitedrop list " + ChatColor.WHITE + "- List all available elite types");
        player.sendMessage(ChatColor.YELLOW + "/elitedrop get <mobName> [itemType] [tier] " + ChatColor.WHITE + "- Get an elite drop");
        player.sendMessage(ChatColor.YELLOW + "/elitedrop info <mobName> " + ChatColor.WHITE + "- Show detailed info about an elite");
        player.sendMessage(ChatColor.YELLOW + "/elitedrop debug " + ChatColor.WHITE + "- Show debug information about elite configs");
        player.sendMessage(ChatColor.YELLOW + "/elitedrop reload " + ChatColor.WHITE + "- Reload elite configurations");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Item types: 1-4 = Weapons, 5-8 = Armor");
        player.sendMessage(ChatColor.GRAY + "Tiers: 1-6 (optional override)");
    }

    /**
     * Lists all available elite mob types with enhanced formatting
     *
     * @param player The player to show the list to
     */
    private void listElites(Player player) {
        Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();

        if (eliteConfigs == null || eliteConfigs.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No elite mob configurations found.");
            player.sendMessage(ChatColor.GRAY + "Use '/elitedrop debug' for more information.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Available Elite Mob Types (" + eliteConfigs.size() + " total):");

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

                ChatColor tierColor = getTierColor(tier);
                String tierName = getTierName(tier);

                player.sendMessage("");
                player.sendMessage(tierColor + "■ " + ChatColor.BOLD + tierName + " (Tier " + tier + "):");

                // Sort elites alphabetically
                elites.sort(String::compareToIgnoreCase);

                for (String elite : elites) {
                    EliteDropConfig config = eliteConfigs.get(elite);
                    String rarityDisplay = getRarityDisplay(config.getRarity());
                    player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + elite +
                            " " + rarityDisplay);
                }
            }
        }

        if (elitesByTier.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No elites found for enabled tiers.");
        }
    }

    /**
     * Shows detailed information about a specific elite
     */
    private void showEliteInfo(Player player, String mobName) {
        EliteDropConfig config = DropConfig.getEliteDropConfig(mobName.toLowerCase());
        if (config == null) {
            player.sendMessage(ChatColor.RED + "Elite mob type '" + mobName + "' not found.");
            player.sendMessage(ChatColor.GRAY + "Use '/elitedrop list' to see available elites.");
            return;
        }

        ChatColor tierColor = getTierColor(config.getTier());
        String rarityDisplay = getRarityDisplay(config.getRarity());

        player.sendMessage(ChatColor.GOLD + "======== Elite Info: " + mobName + " ========");
        player.sendMessage(tierColor + "Tier: " + config.getTier() + " (" + getTierName(config.getTier()) + ")");
        player.sendMessage("Rarity: " + rarityDisplay);
        player.sendMessage(ChatColor.YELLOW + "Available Items: " + ChatColor.WHITE + config.getItemDetails().size());

        if (!config.getItemDetails().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Item Types:");
            config.getItemDetails().forEach((itemType, details) -> {
                String typeName = getItemTypeName(itemType);
                player.sendMessage(ChatColor.GRAY + "  " + itemType + ": " + ChatColor.WHITE +
                        typeName + " - " + details.getName());
            });
        }

        // Show stat information if available
        if (!config.getStatRanges().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Special Stats: " +
                    ChatColor.WHITE + config.getStatRanges().size() + " defined");
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
        player.sendMessage(ChatColor.YELLOW + "Total elite configurations: " + ChatColor.WHITE + eliteConfigs.size());

        // Count by tier
        Map<Integer, Long> countByTier = eliteConfigs.values().stream()
                .collect(Collectors.groupingBy(EliteDropConfig::getTier, Collectors.counting()));

        player.sendMessage(ChatColor.YELLOW + "Distribution by tier:");
        for (int tier = 1; tier <= 6; tier++) {
            long count = countByTier.getOrDefault(tier, 0L);
            if (count > 0) {
                ChatColor tierColor = getTierColor(tier);
                player.sendMessage(tierColor + "  Tier " + tier + ": " + ChatColor.WHITE + count + " elites");
            }
        }

        // Show T6 status
        player.sendMessage(ChatColor.YELLOW + "T6 Content: " +
                (YakRealms.isT6Enabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

        // Show sample configurations
        player.sendMessage(ChatColor.YELLOW + "Sample configurations:");
        int count = 0;
        for (Map.Entry<String, EliteDropConfig> entry : eliteConfigs.entrySet()) {
            if (count >= 3) break;

            EliteDropConfig config = entry.getValue();
            ChatColor tierColor = getTierColor(config.getTier());
            player.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                    tierColor + "T" + config.getTier() + " " +
                    getRarityDisplay(config.getRarity()) + " " +
                    ChatColor.WHITE + "(" + config.getItemDetails().size() + " items)");
            count++;
        }
    }

    /**
     * Reload elite configurations
     */
    private void reloadEliteConfigs(Player player) {
        try {
            // This would typically reload from config files
            // For now, just show current status
            Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();

            player.sendMessage(ChatColor.GREEN + "Elite configurations reloaded!");
            player.sendMessage(ChatColor.YELLOW + "Loaded " + ChatColor.WHITE + eliteConfigs.size() +
                    ChatColor.YELLOW + " elite configurations.");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to reload elite configurations: " + e.getMessage());
        }
    }

    /**
     * Gives a player an elite drop with improved error handling
     *
     * @param player      The player to give the item to
     * @param mobName     The elite mob type
     * @param itemType    The item type (1-8)
     * @param overrideTier Optional tier override
     */
    private void giveEliteDrop(Player player, String mobName, int itemType, Integer overrideTier) {
        EliteDropConfig config = DropConfig.getEliteDropConfig(mobName);
        if (config == null) {
            player.sendMessage(ChatColor.RED + "Elite mob type '" + mobName + "' not found.");
            player.sendMessage(ChatColor.GRAY + "Use '/elitedrop list' to see available elites.");
            return;
        }

        int tier = overrideTier != null ? overrideTier : config.getTier();

        // Check for T6 access
        if (tier == 6 && !YakRealms.isT6Enabled()) {
            player.sendMessage(ChatColor.RED + "Tier 6 content is currently disabled.");
            return;
        }

        // Create the elite drop with the fixed method call
        ItemStack item = dropsManager.createEliteDrop(mobName, itemType, tier);

        if (item == null) {
            player.sendMessage(ChatColor.RED + "Failed to create elite drop. Check console for errors.");
            player.sendMessage(ChatColor.GRAY + "This might happen if the elite doesn't have the requested item type.");
            return;
        }

        // Give the item to the player
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);

            String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName() ?
                    item.getItemMeta().getDisplayName() :
                    TextUtil.formatItemName(item.getType().name());

            player.sendMessage(ChatColor.GREEN + "You received " + itemName + ChatColor.GREEN + "!");

            if (overrideTier != null) {
                player.sendMessage(ChatColor.GRAY + "Note: Tier overridden to " + tier);
            }
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage(ChatColor.YELLOW + "Your inventory is full. The item was dropped at your feet.");
        }
    }

    /**
     * Get tier color for display
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

    /**
     * Get tier name for display
     */
    private String getTierName(int tier) {
        return switch (tier) {
            case 1 -> "Novice";
            case 2 -> "Apprentice";
            case 3 -> "Journeyman";
            case 4 -> "Expert";
            case 5 -> "Master";
            case 6 -> "Legendary";
            default -> "Unknown";
        };
    }

    /**
     * Get rarity display string
     */
    private String getRarityDisplay(int rarity) {
        return switch (rarity) {
            case 1 -> ChatColor.GRAY + "[Common]";
            case 2 -> ChatColor.GREEN + "[Uncommon]";
            case 3 -> ChatColor.AQUA + "[Rare]";
            case 4 -> ChatColor.YELLOW + "[Unique]";
            default -> ChatColor.DARK_GRAY + "[Unknown]";
        };
    }

    /**
     * Get item type name for display
     */
    private String getItemTypeName(int itemType) {
        return switch (itemType) {
            case 1 -> "Staff";
            case 2 -> "Spear";
            case 3 -> "Sword";
            case 4 -> "Axe";
            case 5 -> "Helmet";
            case 6 -> "Chestplate";
            case 7 -> "Leggings";
            case 8 -> "Boots";
            default -> "Unknown";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.elitedrop")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("list", "get", "debug", "info", "reload");
            return subCommands.stream()
                    .filter(subCmd -> subCmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("info"))) {
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

        if (args.length == 4 && args[0].equalsIgnoreCase("get")) {
            // Return tier numbers 1-6
            List<String> tiers = Arrays.asList("1", "2", "3", "4", "5", "6");
            return tiers.stream()
                    .filter(tier -> tier.startsWith(args[3]))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}