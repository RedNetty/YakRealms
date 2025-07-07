package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.utils.text.TextUtil;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Enhanced notification system for item drops with improved visual design and messaging
 */
public class LootNotifier {
    private static LootNotifier instance;

    // Enhanced rarity detection patterns
    private static final Pattern COMMON_PATTERN = Pattern.compile("(?i)common");
    private static final Pattern UNCOMMON_PATTERN = Pattern.compile("(?i)uncommon");
    private static final Pattern RARE_PATTERN = Pattern.compile("(?i)rare");
    private static final Pattern UNIQUE_PATTERN = Pattern.compile("(?i)unique|legendary");

    // Enhanced visual elements
    private static final Map<String, String> RARITY_SYMBOLS = Map.of(
            "common", "◆",
            "uncommon", "◇",
            "rare", "★",
            "unique", "✦",
            "legendary", "✧"
    );

    private static final Map<String, ChatColor[]> RARITY_COLORS = Map.of(
            "common", new ChatColor[]{ChatColor.WHITE, ChatColor.GRAY},
            "uncommon", new ChatColor[]{ChatColor.GREEN, ChatColor.DARK_GREEN},
            "rare", new ChatColor[]{ChatColor.AQUA, ChatColor.DARK_AQUA},
            "unique", new ChatColor[]{ChatColor.YELLOW, ChatColor.GOLD},
            "legendary", new ChatColor[]{ChatColor.LIGHT_PURPLE, ChatColor.DARK_PURPLE}
    );

    // Enhanced sound mappings
    private static final Map<String, Sound[]> RARITY_SOUNDS = Map.of(
            "common", new Sound[]{Sound.ENTITY_ITEM_PICKUP},
            "uncommon", new Sound[]{Sound.ENTITY_EXPERIENCE_ORB_PICKUP},
            "rare", new Sound[]{Sound.BLOCK_NOTE_BLOCK_PLING, Sound.BLOCK_NOTE_BLOCK_CHIME},
            "unique", new Sound[]{Sound.ENTITY_PLAYER_LEVELUP, Sound.BLOCK_NOTE_BLOCK_BELL},
            "legendary", new Sound[]{Sound.ENTITY_ENDER_DRAGON_GROWL, Sound.BLOCK_NOTE_BLOCK_BELL}
    );

    // Drop type symbols
    private static final Map<String, String> DROP_TYPE_SYMBOLS = Map.of(
            "weapon", "⚔",
            "armor", "🛡",
            "gem", "💎",
            "scroll", "📜",
            "crate", "📦",
            "boss", "👑"
    );

    /**
     * Private constructor for singleton pattern
     */
    private LootNotifier() {
    }

    /**
     * Gets the singleton instance
     *
     * @return The LootNotifier instance
     */
    public static LootNotifier getInstance() {
        if (instance == null) {
            instance = new LootNotifier();
        }
        return instance;
    }

    /**
     * Enhanced drop notification with improved visual design and multiple notification methods
     *
     * @param player     The player to notify
     * @param item       The dropped item
     * @param source     The source entity or null if not from an entity
     * @param isBossLoot Whether this is from a boss
     */
    public void sendDropNotification(Player player, ItemStack item, LivingEntity source, boolean isBossLoot) {
        if (player == null || item == null || !player.isOnline()) {
            return;
        }

        try {
            DropNotificationData notificationData = analyzeDropNotification(item, source, isBossLoot);

            // Send multiple types of notifications for enhanced user experience
            sendChatNotification(player, notificationData);
            sendActionBarNotification(player, notificationData);
            playNotificationEffects(player, notificationData);

            // Special effects for high-value drops
            if (notificationData.isHighValue()) {
                sendSpecialEffects(player, notificationData);
            }

        } catch (Exception e) {
            // Fallback to basic notification
            sendBasicNotification(player, item, source, isBossLoot);
        }
    }

    /**
     * Enhanced buff activation announcement with improved visual design
     *
     * @param player          The player who activated the buff
     * @param buffRate        The percentage increase for drops
     * @param durationMinutes The duration in minutes
     */
    public void announceBuffActivation(Player player, int buffRate, int durationMinutes) {
        int eliteBuffRate = buffRate / 2;
        String playerName = player.getName();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            sendBuffActivationToPlayer(onlinePlayer, playerName, buffRate, eliteBuffRate, durationMinutes);
        }
    }

    /**
     * Enhanced buff end announcement with statistics
     *
     * @param playerName    The name of the player who activated the buff
     * @param improvedDrops The number of drops improved by the buff
     */
    public void announceBuffEnd(String playerName, int improvedDrops) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendBuffEndToPlayer(player, playerName, improvedDrops);
        }
    }

    /**
     * Enhanced world boss defeat announcement with improved formatting
     *
     * @param bossName    The name of the boss
     * @param topDamagers A list of top damage dealers (player names and damage amounts)
     */
    public void announceWorldBossDefeat(String bossName, List<Object[]> topDamagers) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendWorldBossDefeatToPlayer(player, bossName, topDamagers);
        }
    }

    /**
     * Analyzes drop data for enhanced notifications
     */
    private DropNotificationData analyzeDropNotification(ItemStack item, LivingEntity source, boolean isBossLoot) {
        String itemName = getEnhancedItemName(item);
        String sourceName = getEnhancedSourceName(source);
        String rarity = detectItemRarity(item);
        String dropType = detectDropType(item);
        boolean isHighValue = isHighValueDrop(rarity, isBossLoot);

        return new DropNotificationData(itemName, sourceName, rarity, dropType, isBossLoot, isHighValue);
    }

    /**
     * Enhanced item name extraction with color preservation
     */
    private String getEnhancedItemName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return TextUtil.formatItemName(item.getType().name());
    }

    /**
     * Enhanced source name with improved formatting
     */
    private String getEnhancedSourceName(LivingEntity source) {
        if (source == null) {
            return "Unknown Source";
        }

        if (source.getCustomName() != null) {
            return source.getCustomName();
        }
        int tier = MobManager.getInstance().getCustomMob(source).getTier();
        return MobManager.getInstance().getCustomMob(source).getType().getFormattedName(tier);
    }

    /**
     * Enhanced rarity detection from item lore and name
     */
    private String detectItemRarity(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "common";
        }

        // Check lore for rarity
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            String lastLine = lore.get(lore.size() - 1);
            String cleanLine = ChatColor.stripColor(lastLine).toLowerCase();

            if (UNIQUE_PATTERN.matcher(cleanLine).find()) return "unique";
            if (RARE_PATTERN.matcher(cleanLine).find()) return "rare";
            if (UNCOMMON_PATTERN.matcher(cleanLine).find()) return "uncommon";
            if (COMMON_PATTERN.matcher(cleanLine).find()) return "common";
        }

        // Check item name colors
        if (meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            if (displayName.contains(ChatColor.YELLOW.toString()) ||
                    displayName.contains(ChatColor.GOLD.toString())) return "unique";
            if (displayName.contains(ChatColor.AQUA.toString()) ||
                    displayName.contains(ChatColor.DARK_AQUA.toString())) return "rare";
            if (displayName.contains(ChatColor.GREEN.toString()) ||
                    displayName.contains(ChatColor.DARK_GREEN.toString())) return "uncommon";
        }

        return "common";
    }

    /**
     * Detects the type of drop for appropriate symbols
     */
    private String detectDropType(ItemStack item) {
        String materialName = item.getType().name().toLowerCase();

        if (materialName.contains("sword") || materialName.contains("axe") ||
                materialName.contains("pickaxe") || materialName.contains("shovel")) {
            return "weapon";
        }

        if (materialName.contains("helmet") || materialName.contains("chestplate") ||
                materialName.contains("leggings") || materialName.contains("boots")) {
            return "armor";
        }

        if (materialName.contains("emerald") || materialName.contains("diamond")) {
            return "gem";
        }

        if (materialName.contains("paper") || materialName.contains("book")) {
            return "scroll";
        }

        if (materialName.contains("chest") || materialName.contains("shulker")) {
            return "crate";
        }

        return "common";
    }

    /**
     * Determines if a drop is high value for special effects
     */
    private boolean isHighValueDrop(String rarity, boolean isBossLoot) {
        return isBossLoot || "unique".equals(rarity) || "legendary".equals(rarity);
    }

    /**
     * Sends enhanced chat notification
     */
    private void sendChatNotification(Player player, DropNotificationData data) {
        ChatColor[] colors = RARITY_COLORS.getOrDefault(data.rarity, RARITY_COLORS.get("common"));
        String symbol = RARITY_SYMBOLS.getOrDefault(data.rarity, "◆");
        String typeSymbol = DROP_TYPE_SYMBOLS.getOrDefault(data.dropType, "");

        String prefix = data.isBossLoot ?
                ChatColor.GOLD + "👑 " + ChatColor.YELLOW + "[BOSS LOOT] " :
                colors[0] + symbol + " ";

        String message;
        if (data.isBossLoot) {
            message = TextUtil.getCenteredMessage(
                    prefix + ChatColor.YELLOW + "You received " + data.itemName +
                            ChatColor.YELLOW + " from the World Boss!"
            );
        } else {
            message = colors[1] + "➤ " + ChatColor.RESET + data.sourceName +
                    ChatColor.YELLOW + " dropped " + typeSymbol + " " + data.itemName;
        }

        player.sendMessage(message);
    }

    /**
     * Sends action bar notification for immediate visual feedback
     */
    private void sendActionBarNotification(Player player, DropNotificationData data) {
        ChatColor[] colors = RARITY_COLORS.getOrDefault(data.rarity, RARITY_COLORS.get("common"));
        String symbol = RARITY_SYMBOLS.getOrDefault(data.rarity, "◆");

        String actionBarMessage = colors[0] + symbol + " " + ChatColor.stripColor(data.itemName) +
                " " + symbol;

        // Show for 3 seconds (60 ticks)
        ActionBarUtil.addTemporaryMessage(player, actionBarMessage, 60L);
    }

    /**
     * Plays enhanced notification effects
     */
    private void playNotificationEffects(Player player, DropNotificationData data) {
        Sound[] sounds = RARITY_SOUNDS.getOrDefault(data.rarity, RARITY_SOUNDS.get("common"));
        Sound selectedSound = sounds[ThreadLocalRandom.current().nextInt(sounds.length)];

        float volume = data.isHighValue ? 1.5f : 1.0f;
        float pitch = data.isBossLoot ? 0.8f : 1.0f + (ThreadLocalRandom.current().nextFloat() * 0.4f - 0.2f);

        player.playSound(player.getLocation(), selectedSound, volume, pitch);
    }

    /**
     * Sends special effects for high-value drops
     */
    private void sendSpecialEffects(Player player, DropNotificationData data) {
        // Particle effects around the player
        player.getWorld().spawnParticle(
                Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 2, 0),
                20, 1, 1, 1, 0.1
        );

        // Secondary sound effect with delay
        new BukkitRunnable() {
            @Override
            public void run() {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
        }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("YakRealms"), 10L);
    }

    /**
     * Sends buff activation message to a single player
     */
    private void sendBuffActivationToPlayer(Player player, String activatorName, int buffRate,
                                            int eliteBuffRate, int durationMinutes) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

        // Enhanced visual announcement
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GOLD + "✦ " + ChatColor.YELLOW + ChatColor.BOLD + "LOOT BUFF ACTIVATED" +
                        ChatColor.RESET + " " + ChatColor.GOLD + "✦"
        ));
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.AQUA + activatorName + ChatColor.YELLOW + " activated a server-wide loot buff!"
        ));

        // Buff details with enhanced formatting
        player.sendMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.WHITE + "Normal drop rates: " +
                ChatColor.GREEN + "+" + buffRate + "%");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.WHITE + "Elite drop rates: " +
                ChatColor.GREEN + "+" + eliteBuffRate + "%");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.WHITE + "Duration: " +
                ChatColor.YELLOW + durationMinutes + " minutes");
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GREEN + "Thank you for supporting the server!"
        ));
        player.sendMessage("");
    }

    /**
     * Sends buff end message to a single player
     */
    private void sendBuffEndToPlayer(Player player, String activatorName, int improvedDrops) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.RED + "✦ " + ChatColor.YELLOW + ChatColor.BOLD + "LOOT BUFF EXPIRED" +
                        ChatColor.RESET + " " + ChatColor.RED + "✦"
        ));
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.AQUA + activatorName + ChatColor.YELLOW + "'s loot buff has ended!"
        ));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.WHITE + "Enhanced drops provided: " +
                ChatColor.GREEN + improvedDrops);
        player.sendMessage("");
    }

    /**
     * Sends world boss defeat message to a single player
     */
    private void sendWorldBossDefeatToPlayer(Player player, String bossName, List<Object[]> topDamagers) {
        // Enhanced sound effects
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GOLD + "👑 " + ChatColor.RED + ChatColor.BOLD + "WORLD BOSS DEFEATED" +
                        ChatColor.RESET + " " + ChatColor.GOLD + "👑"
        ));
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.YELLOW + bossName + ChatColor.WHITE + " has fallen!"
        ));

        if (!topDamagers.isEmpty()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "⚔ " + ChatColor.YELLOW + "Top Contributors:");

            for (int i = 0; i < Math.min(3, topDamagers.size()); i++) {
                Object[] entry = topDamagers.get(i);
                String name = (String) entry[0];
                int damage = (int) entry[1];

                String rank = switch (i) {
                    case 0 -> ChatColor.GOLD + "🥇";
                    case 1 -> ChatColor.GRAY + "🥈";
                    case 2 -> ChatColor.YELLOW + "🥉";
                    default -> ChatColor.WHITE.toString() + (i + 1) + ".";
                };

                player.sendMessage(ChatColor.WHITE + "  " + rank + " " + ChatColor.AQUA + name +
                        ChatColor.GRAY + " - " + ChatColor.RED + TextUtil.formatNumber(damage) +
                        " damage");
            }
        }

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.YELLOW + "Legendary loot has been distributed!"
        ));
        player.sendMessage("");
    }

    /**
     * Fallback basic notification method
     */
    private void sendBasicNotification(Player player, ItemStack item, LivingEntity source, boolean isBossLoot) {
        String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : TextUtil.formatItemName(item.getType().name());

        String message;
        if (isBossLoot) {
            message = TextUtil.getCenteredMessage(
                    ChatColor.RED + "➤ " + ChatColor.YELLOW + "You received " + itemName +
                            ChatColor.YELLOW + " from the World Boss"
            );
        } else {
            int tier = MobManager.getInstance().getCustomMob(source).getTier();
            String sourceName = source != null ?
                    MobManager.getInstance().getCustomMob(source).getType().getFormattedName(tier) : "Unknown";
            message = ChatColor.GRAY + "➤ " + ChatColor.RESET + sourceName +
                    ChatColor.YELLOW + " dropped " + ChatColor.RESET + itemName;
        }

        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Data class for notification information
     */
    private static class DropNotificationData {
        final String itemName;
        final String sourceName;
        final String rarity;
        final String dropType;
        final boolean isBossLoot;
        final boolean isHighValue;

        DropNotificationData(String itemName, String sourceName, String rarity, String dropType,
                             boolean isBossLoot, boolean isHighValue) {
            this.itemName = itemName;
            this.sourceName = sourceName;
            this.rarity = rarity;
            this.dropType = dropType;
            this.isBossLoot = isBossLoot;
            this.isHighValue = isHighValue;
        }

        boolean isHighValue() {
            return isHighValue;
        }
    }

    /**
     * Sends a custom notification for special events
     *
     * @param player  The player to notify
     * @param title   The title of the notification
     * @param message The message content
     * @param sound   The sound to play
     */
    public void sendCustomNotification(Player player, String title, String message, Sound sound) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(ChatColor.GOLD + "✦ " + title + " ✦"));
        player.sendMessage(TextUtil.getCenteredMessage(message));
        player.sendMessage("");

        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    /**
     * Gets notification statistics for debugging
     *
     * @return A map containing notification statistics
     */
    public Map<String, Object> getNotificationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rarityTypes", RARITY_COLORS.size());
        stats.put("soundMappings", RARITY_SOUNDS.size());
        stats.put("dropTypes", DROP_TYPE_SYMBOLS.size());
        stats.put("symbolMappings", RARITY_SYMBOLS.size());
        return stats;
    }
}