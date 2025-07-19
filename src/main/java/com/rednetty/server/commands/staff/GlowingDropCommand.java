package com.rednetty.server.commands.staff;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.glowing.GlowingDropsManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 *  debug command for GlowingEntities-based system
 */
public class GlowingDropCommand implements CommandExecutor, TabCompleter {
    private static GlowingDropCommand instance;
    private final Logger logger;
    private final GlowingDropsManager manager;
    private boolean initialized = false;

    private GlowingDropCommand() {
        this.logger = YakRealms.getInstance().getLogger();
        this.manager = GlowingDropsManager.getInstance();
    }

    public static GlowingDropCommand getInstance() {
        if (instance == null) {
            instance = new GlowingDropCommand();
        }
        return instance;
    }

    public void initialize() {
        if (initialized) {
            logger.warning("Glowing Drops command already initialized");
            return;
        }

        logger.info("Initializing GlowingEntities-based Glowing Drops command...");

        try {
            // Register command
            if (YakRealms.getInstance().getCommand("glowingdrops") != null) {
                YakRealms.getInstance().getCommand("glowingdrops").setExecutor(this);
                YakRealms.getInstance().getCommand("glowingdrops").setTabCompleter(this);
                logger.info("GlowingDrops command registered successfully");
            } else {
                logger.warning("GlowingDrops command not found in plugin.yml - manual registration required");
            }

            initialized = true;
            logger.info("GlowingEntities-based Glowing Drops command initialized successfully");

        } catch (Exception e) {
            logger.severe("Failed to initialize GlowingEntities-based Glowing Drops command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }

        logger.info("Shutting down GlowingEntities-based Glowing Drops command...");

        try {
            initialized = false;
            logger.info("GlowingEntities-based Glowing Drops command shut down successfully");
        } catch (Exception e) {
            logger.severe("Failed to shutdown GlowingEntities-based Glowing Drops command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                sendStatus(sender);
                break;

            case "reload":
                reload(sender);
                break;

            case "radius":
                if (args.length >= 2) {
                    setRadius(sender, args[1]);
                } else {
                    sender.sendMessage("§cUsage: /glowingdrops radius <blocks>");
                }
                break;

            case "toggle":
                if (sender instanceof Player player) {
                    toggleForPlayer(player);
                } else {
                    sender.sendMessage("§cThis command can only be used by players.");
                }
                break;

            case "test":
                if (sender instanceof Player player) {
                    testGlowing(player);
                } else {
                    sender.sendMessage("§cThis command can only be used by players.");
                }
                break;

            case "debug":
                if (sender instanceof Player player) {
                    debugPlayer(player);
                } else {
                    sender.sendMessage("§cThis command can only be used by players.");
                }
                break;

            case "api":
                showApiStatus(sender);
                break;

            case "autoEnable":
                if (args.length >= 2) {
                    setAutoEnable(sender, args[1]);
                } else {
                    sender.sendMessage("§cUsage: /glowingdrops autoEnable <true|false>");
                }
                break;

            case "showCommon":
                if (args.length >= 2) {
                    setShowCommon(sender, args[1]);
                } else {
                    sender.sendMessage("§cUsage: /glowingdrops showCommon <true|false>");
                }
                break;

            case "nearby":
                if (sender instanceof Player player) {
                    showNearbyItems(player);
                } else {
                    sender.sendMessage("§cThis command can only be used by players.");
                }
                break;

            case "diagnose":
                if (sender instanceof Player player) {
                    diagnoseSystem(player);
                } else {
                    sender.sendMessage("§cThis command can only be used by players.");
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String[] subcommands = {"status", "reload", "radius", "toggle", "test", "debug", "api",
                    "autoEnable", "showCommon", "nearby", "diagnose"};
            for (String subcommand : subcommands) {
                if (subcommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "radius":
                    completions.add("16");
                    completions.add("32");
                    completions.add("48");
                    completions.add("64");
                    break;
                case "autoenable":
                case "showcommon":
                    completions.add("true");
                    completions.add("false");
                    break;
            }
        }

        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l✦ GlowingEntities Drops Commands ✦");
        sender.sendMessage("§e/glowingdrops status §7- Show system status");
        sender.sendMessage("§e/glowingdrops reload §7- Reload the system");
        sender.sendMessage("§e/glowingdrops radius <blocks> §7- Set glow radius");
        sender.sendMessage("§e/glowingdrops toggle §7- Toggle your glowing drops");
        sender.sendMessage("§e/glowingdrops test §7- Test glowing with different rarities");
        sender.sendMessage("§e/glowingdrops debug §7- Debug your current state");
        sender.sendMessage("§e/glowingdrops api §7- Show GlowingEntities API status");
        sender.sendMessage("§e/glowingdrops autoEnable <true|false> §7- Auto-enable toggle for players");
        sender.sendMessage("§e/glowingdrops showCommon <true|false> §7- Show common items (debug)");
        sender.sendMessage("§e/glowingdrops nearby §7- Show nearby items and their rarities");
        sender.sendMessage("§e/glowingdrops diagnose §7- Run full system diagnosis");
    }

    private void sendStatus(CommandSender sender) {
        Map<String, Object> stats = manager.getStatistics();

        sender.sendMessage("§6§l✦ GlowingEntities Drops Status ✦");
        sender.sendMessage("§7System: " + (initialized && manager.isEnabled() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7GlowingEntities API: " + (stats.get("glowingEntitiesInitialized").equals(true) ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7Toggle Name: §f" + stats.get("toggleName"));
        sender.sendMessage("§7Glow Radius: §f" + stats.get("glowRadius") + " blocks");
        sender.sendMessage("§7Auto-Enable Toggle: " + (manager.isAutoEnableToggle() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7Show Common Items: " + (manager.isShowCommonItems() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7Tracked Items: §f" + stats.get("trackedItems"));
        sender.sendMessage("§7Players with Visible Items: §f" + stats.get("playersWithVisibleItems"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> rarityCount = (Map<String, Integer>) stats.get("itemsByRarity");
        if (rarityCount != null && !rarityCount.isEmpty()) {
            sender.sendMessage("§7Items by Rarity:");
            rarityCount.forEach((rarity, count) -> {
                String color = switch (rarity) {
                    case "uncommon" -> "§a";
                    case "rare" -> "§b";
                    case "unique" -> "§e";
                    case "legendary" -> "§6";
                    default -> "§7";
                };
                sender.sendMessage("  " + color + rarity + ": §f" + count);
            });
        }
    }

    private void debugPlayer(Player player) {
        player.sendMessage("§6§l✦ Debug Info for " + player.getName() + " ✦");

        try {
            boolean hasToggle = Toggles.isToggled(player, manager.getToggleName());
            player.sendMessage("§7Toggle State: " + (hasToggle ? "§aEnabled" : "§cDisabled"));
        } catch (Exception e) {
            player.sendMessage("§7Toggle State: §cError - " + e.getMessage());
            player.sendMessage("§eConsider setting autoEnable to true: /glowingdrops autoEnable true");
        }

        // Count nearby items
        int totalItems = 0;
        int glowingItems = 0;

        for (org.bukkit.entity.Item item : player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class)) {
            if (item.getLocation().distance(player.getLocation()) <= manager.getGlowRadius()) {
                totalItems++;
                String rarity = manager.detectItemRarity(item.getItemStack());
                if (!"common".equals(rarity) || manager.isShowCommonItems()) {
                    glowingItems++;
                }
            }
        }

        player.sendMessage("§7Nearby Items (within " + manager.getGlowRadius() + " blocks): §f" + totalItems);
        player.sendMessage("§7Should be Glowing: §f" + glowingItems);
        player.sendMessage("§7GlowingEntities API: " + (manager.getGlowingEntities() != null ? "§aAvailable" : "§cUnavailable"));
        player.sendMessage("§7Auto-Enable Toggle: " + (manager.isAutoEnableToggle() ? "§aEnabled" : "§cDisabled"));

        // Force update
        player.sendMessage("§eForcing update...");
        manager.updateAllItemsForPlayer(player);
        player.sendMessage("§aUpdate complete!");
    }

    private void showNearbyItems(Player player) {
        player.sendMessage("§6§l✦ Nearby Items Analysis ✦");

        int count = 0;
        for (org.bukkit.entity.Item item : player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class)) {
            double distance = item.getLocation().distance(player.getLocation());
            if (distance <= manager.getGlowRadius()) {
                String rarity = manager.detectItemRarity(item.getItemStack());
                String color = switch (rarity) {
                    case "uncommon" -> "§a";
                    case "rare" -> "§b";
                    case "unique" -> "§e";
                    case "legendary" -> "§6";
                    default -> "§7";
                };

                player.sendMessage(String.format("§7%d. %s%s §7(%s) - %s%.1f blocks",
                        ++count, color, item.getItemStack().getType(), rarity, "§7", distance));

                if (count >= 10) {
                    player.sendMessage("§7... (showing first 10 items)");
                    break;
                }
            }
        }

        if (count == 0) {
            player.sendMessage("§7No items found within " + manager.getGlowRadius() + " blocks");
        }
    }

    private void diagnoseSystem(Player player) {
        player.sendMessage("§6§l✦ System Diagnosis ✦");

        // Check API initialization
        boolean apiInitialized = manager.getGlowingEntities() != null;
        player.sendMessage("§71. GlowingEntities API: " + (apiInitialized ? "§aInitialized" : "§cNot Initialized"));
        player.sendMessage("   §7Using shaded library (no external plugin needed)");

        // Check system enabled
        player.sendMessage("§72. System Enabled: " + (manager.isEnabled() ? "§aYes" : "§cNo"));

        // Check toggle system
        try {
            boolean hasToggle = Toggles.isToggled(player, manager.getToggleName());
            player.sendMessage("§73. Your Toggle: " + (hasToggle ? "§aEnabled" : "§cDisabled"));
        } catch (Exception e) {
            player.sendMessage("§73. Your Toggle: §cError - " + e.getMessage());
            player.sendMessage("   §eAuto-enable is " + (manager.isAutoEnableToggle() ? "§aenabled" : "§cdisabled"));
        }

        // Check nearby items
        int totalItems = 0;
        int glowableItems = 0;
        for (org.bukkit.entity.Item item : player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class)) {
            if (item.getLocation().distance(player.getLocation()) <= manager.getGlowRadius()) {
                totalItems++;
                String rarity = manager.detectItemRarity(item.getItemStack());
                if (!"common".equals(rarity) || manager.isShowCommonItems()) {
                    glowableItems++;
                }
            }
        }

        player.sendMessage("§74. Nearby Items: §f" + totalItems + " §7(§f" + glowableItems + " §7glowable)");

        // Recommendations
        player.sendMessage("§6§l✦ Recommendations ✦");

        if (!apiInitialized) {
            player.sendMessage("§c• Check server logs for GlowingEntities initialization errors");
        }
        if (!manager.isEnabled()) {
            player.sendMessage("§c• Enable the glowing drops system");
        }
        if (glowableItems == 0) {
            player.sendMessage("§e• Try using /glowingdrops test to spawn test items");
            player.sendMessage("§e• Enable showCommon to see all items: /glowingdrops showCommon true");
        }
        if (!manager.isAutoEnableToggle()) {
            player.sendMessage("§e• Consider enabling auto-toggle: /glowingdrops autoEnable true");
        }
    }

    private void showApiStatus(CommandSender sender) {
        sender.sendMessage("§6§l✦ GlowingEntities API Status ✦");
        sender.sendMessage("§7API Instance: " + (manager.getGlowingEntities() != null ? "§aLoaded" : "§cNot Loaded"));
        sender.sendMessage("§7Using: §aShaded GlowingEntities Library (no external plugin needed)");
        sender.sendMessage("§7Manager Enabled: " + (manager.isEnabled() ? "§aYes" : "§cNo"));

        if (manager.getGlowingEntities() != null) {
            sender.sendMessage("§aGlowingEntities API is working correctly!");
        } else {
            sender.sendMessage("§cGlowingEntities API is not available. Check server logs for initialization errors.");
        }
    }

    private void reload(CommandSender sender) {
        sender.sendMessage("§eReloading GlowingEntities Drops system...");
        manager.onDisable();

        // Small delay before re-enabling
        org.bukkit.Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            manager.onEnable();
            sender.sendMessage("§aGlowingEntities Drops system reloaded successfully!");
        }, 20L);
    }

    private void setRadius(CommandSender sender, String radiusStr) {
        try {
            int radius = Integer.parseInt(radiusStr);
            if (radius < 1 || radius > 64) {
                sender.sendMessage("§cRadius must be between 1 and 64 blocks.");
                return;
            }

            manager.setGlowRadius(radius);
            sender.sendMessage("§aGlow radius set to " + radius + " blocks.");

        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + radiusStr);
        }
    }

    private void setAutoEnable(CommandSender sender, String value) {
        try {
            boolean autoEnable = Boolean.parseBoolean(value);
            manager.setAutoEnableToggle(autoEnable);
            sender.sendMessage("§aAuto-enable toggle set to: " + (autoEnable ? "§aEnabled" : "§cDisabled"));
        } catch (Exception e) {
            sender.sendMessage("§cInvalid value. Use true or false.");
        }
    }

    private void setShowCommon(CommandSender sender, String value) {
        try {
            boolean showCommon = Boolean.parseBoolean(value);
            manager.setShowCommonItems(showCommon);
            sender.sendMessage("§aShow common items set to: " + (showCommon ? "§aEnabled" : "§cDisabled"));
        } catch (Exception e) {
            sender.sendMessage("§cInvalid value. Use true or false.");
        }
    }

    private void toggleForPlayer(Player player) {
        try {
            boolean currentState = Toggles.isToggled(player, manager.getToggleName());
            Toggles.setToggle(player, manager.getToggleName(), !currentState);

            player.sendMessage("§6Glowing Drops " + (!currentState ? "§aEnabled" : "§cDisabled"));

            // Force update
            player.sendMessage("§eUpdating items...");
            manager.updateAllItemsForPlayer(player);
            player.sendMessage("§aUpdate complete!");

        } catch (Exception e) {
            player.sendMessage("§cError toggling glowing drops: " + e.getMessage());
            player.sendMessage("§eThe toggle might not be registered. Try using auto-enable: /glowingdrops autoEnable true");
        }
    }

    private void testGlowing(Player player) {
        player.sendMessage("§6Testing glowing drops...");

        org.bukkit.Location loc = player.getLocation().add(0, 1, 0);

        // Test items with different rarities
        String[] rarities = {"common", "uncommon", "rare", "unique", "legendary"};
        Material[] materials = {
                Material.WOODEN_SWORD,
                Material.IRON_SWORD,
                Material.DIAMOND_SWORD,
                Material.NETHERITE_SWORD,
                Material.NETHERITE_SWORD
        };
        String[] colors = {"§7", "§a", "§b", "§e", "§6"};

        for (int i = 0; i < rarities.length; i++) {
            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(materials[i]);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(colors[i] + rarities[i].substring(0, 1).toUpperCase() + rarities[i].substring(1) + " Sword");
                meta.setLore(java.util.Arrays.asList(
                        "§7Test item for glowing drops",
                        "§7This item should glow " + colors[i] + rarities[i],
                        "",
                        colors[i] + rarities[i].substring(0, 1).toUpperCase() + rarities[i].substring(1)
                ));
                item.setItemMeta(meta);
            }
            player.getWorld().dropItem(loc.clone().add(i * 2, 0, 0), item);
        }

        player.sendMessage("§aDropped test items with different rarities!");
        player.sendMessage("§eWait a moment, then use §f/glowingdrops debug §eto check status");
        player.sendMessage("§eIf you don't see glowing, try §f/glowingdrops diagnose");
    }

    public boolean isInitialized() {
        return initialized;
    }
}