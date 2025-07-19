package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.chat.JsonChatComponent;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 *  notification system for item drops with hoverable items and improved visual design
 * Now supports JsonChatComponent for interactive item tooltips
 */
public class LootNotifier {
    private static LootNotifier instance;

    //  rarity detection patterns
    private static final Pattern COMMON_PATTERN = Pattern.compile("(?i)common");
    private static final Pattern UNCOMMON_PATTERN = Pattern.compile("(?i)uncommon");
    private static final Pattern RARE_PATTERN = Pattern.compile("(?i)rare");
    private static final Pattern UNIQUE_PATTERN = Pattern.compile("(?i)unique|legendary");

    //  visual elements
    private static final Map<String, String> RARITY_SYMBOLS = Map.of(
            "common", "◆",
            "uncommon", "◇",
            "rare", "★",
            "unique", "✦",
            "legendary", "✧"
    );

    private static final Map<String, ChatColor[]> RARITY_COLORS = Map.of(
            "common", new ChatColor[]{ChatColor.WHITE, ChatColor.WHITE},
            "uncommon", new ChatColor[]{ChatColor.GREEN, ChatColor.GREEN},
            "rare", new ChatColor[]{ChatColor.AQUA, ChatColor.AQUA},
            "unique", new ChatColor[]{ChatColor.YELLOW, ChatColor.YELLOW}
    );

    //  sound mappings
    private static final Map<String, Sound[]> RARITY_SOUNDS = Map.of(
            "common", new Sound[]{Sound.ENTITY_ITEM_PICKUP},
            "uncommon", new Sound[]{Sound.ENTITY_EXPERIENCE_ORB_PICKUP},
            "rare", new Sound[]{Sound.ENTITY_DOLPHIN_PLAY, Sound.BLOCK_NOTE_BLOCK_CHIME},
            "unique", new Sound[]{Sound.ENTITY_ENDER_DRAGON_DEATH, Sound.BLOCK_NOTE_BLOCK_BELL}
    );

    // Drop type symbols
    private static final Map<String, String> DROP_TYPE_SYMBOLS = Map.of(
            "weapon", "⚔",
            "armor", "🛡",
            "gem", "💎",
            "scroll", "📜",
            "crate", "📦",
            "book", "📖",
            "boss", "👑"
    );

    // Tier colors updated for Tier 6 Netherite
    private static final Map<Integer, ChatColor> TIER_COLORS = Map.of(
            1, ChatColor.WHITE,
            2, ChatColor.GREEN,
            3, ChatColor.AQUA,
            4, ChatColor.LIGHT_PURPLE,
            5, ChatColor.YELLOW,
            6, ChatColor.GOLD  // Updated for Netherite
    );

    /**
     * Private constructor for singleton pattern
     */
    private LootNotifier() {
    }

    /**
     * Gets the singleton instance
     */
    public static LootNotifier getInstance() {
        if (instance == null) {
            instance = new LootNotifier();
        }
        return instance;
    }

    /**
     *  drop notification with hoverable items using JsonChatComponent
     */
    public void sendDropNotification(Player player, ItemStack item, LivingEntity source, boolean isBossLoot) {
        if (player == null || item == null || !player.isOnline()) {
            return;
        }

        try {
            DropNotificationData notificationData = analyzeDropNotification(item, source, isBossLoot);

            // Send interactive chat notification with hoverable item
            sendInteractiveChatNotification(player, notificationData, item);

            // Play notification effects
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
     *  Send notification for gems with hover details
     */
    public void sendGemDropNotification(Player player, ItemStack gems, LivingEntity source, int amount) {
        if (player == null || gems == null || !player.isOnline()) {
            return;
        }

        try {
            String sourceName = getSourceName(source);
            String gemHoverText = ChatColor.GREEN + "Gems: " + ChatColor.YELLOW + amount + "\n" +
                    ChatColor.GRAY + "Currency used for trading\n" +
                    ChatColor.GRAY + "and purchasing items";

            JsonChatComponent message = new JsonChatComponent(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped ");
            message.addHoverItem(ChatColor.GREEN + "💎 " + amount + " Gems",
                    List.of(gemHoverText));

            message.send(player);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);

        } catch (Exception e) {
            // Fallback
            sendBasicGemNotification(player, source, amount);
        }
    }

    /**
     *  Send notification for crates with hover details
     */
    public void sendCrateDropNotification(Player player, ItemStack crate, LivingEntity source, int tier) {
        if (player == null || crate == null || !player.isOnline()) {
            return;
        }

        try {
            String sourceName = getSourceName(source);
            ChatColor tierColor = TIER_COLORS.getOrDefault(tier, ChatColor.WHITE);

            List<String> hoverText = new ArrayList<>();
            if (crate.getItemMeta() != null && crate.getItemMeta().hasLore()) {
                hoverText.addAll(crate.getItemMeta().getLore());
            } else {
                hoverText.add(tierColor + "Tier " + tier + " Crate");
                hoverText.add(ChatColor.GRAY + "Right-click to open");
                hoverText.add(ChatColor.GRAY + "Contains tier " + tier + " items");
            }

            JsonChatComponent message = new JsonChatComponent(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped ");

            String crateName = crate.getItemMeta() != null && crate.getItemMeta().hasDisplayName()
                    ? crate.getItemMeta().getDisplayName()
                    : tierColor + "Tier " + tier + " Crate";

            message.addHoverItem("📦 " + ChatColor.stripColor(crateName), hoverText);

            message.send(player);

            //  sound for crates
            Sound crateSound = tier >= 4 ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.ENTITY_ITEM_PICKUP;
            player.playSound(player.getLocation(), crateSound, 1.0f, 1.0f + (tier * 0.1f));

        } catch (Exception e) {
            // Fallback
            sendBasicCrateNotification(player, crate, source, tier);
        }
    }

    /**
     *  Send notification for teleport books with hover details
     */
    public void sendTeleportBookNotification(Player player, ItemStack book, LivingEntity source, String destinationName) {
        if (player == null || book == null || !player.isOnline()) {
            return;
        }

        try {
            String sourceName = getSourceName(source);

            List<String> hoverText = new ArrayList<>();
            if (book.getItemMeta() != null && book.getItemMeta().hasLore()) {
                hoverText.addAll(book.getItemMeta().getLore());
            } else {
                hoverText.add(ChatColor.AQUA + "Teleport Book");
                hoverText.add(ChatColor.GRAY + "Destination: " + ChatColor.WHITE + destinationName);
                hoverText.add(ChatColor.GRAY + "Right-click to teleport");
            }

            JsonChatComponent message = new JsonChatComponent(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped ");

            String bookName = book.getItemMeta() != null && book.getItemMeta().hasDisplayName()
                    ? book.getItemMeta().getDisplayName()
                    : ChatColor.AQUA + destinationName + " Teleport Book";

            message.addHoverItem("📖 " + ChatColor.stripColor(bookName), hoverText);

            message.send(player);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        } catch (Exception e) {
            // Fallback
            sendBasicBookNotification(player, book, source, destinationName);
        }
    }

    /**
     *  interactive chat notification with hoverable items
     */
    private void sendInteractiveChatNotification(Player player, DropNotificationData data, ItemStack item) {
        ChatColor[] colors = RARITY_COLORS.getOrDefault(data.rarity, RARITY_COLORS.get("common"));
        String symbol = RARITY_SYMBOLS.getOrDefault(data.rarity, "◆");
        String typeSymbol = DROP_TYPE_SYMBOLS.getOrDefault(data.dropType, "");

        if (data.isBossLoot) {
            // Boss loot gets special treatment with centered message
            String bossMessage = ChatColor.GOLD + "👑 " + ChatColor.YELLOW + "You received " +
                    ChatColor.stripColor(data.itemName) + ChatColor.YELLOW + " from the Special Elite!";

            JsonChatComponent message = new JsonChatComponent("");
            message.addHoverItem(TextUtil.getCenteredMessage(bossMessage), getItemHoverText(item));
            message.send(player);
        } else {
            // Regular drops with interactive hover
            JsonChatComponent message = new JsonChatComponent(colors[1] + "➤ " + ChatColor.RESET + data.sourceName + ChatColor.YELLOW + " dropped ");

            String displayText = typeSymbol + " " + data.itemName;
            message.addHoverItem(displayText, getItemHoverText(item));

            message.send(player);
        }
    }

    /**
     * Extract hover text from an item for tooltips
     */
    private List<String> getItemHoverText(ItemStack item) {
        List<String> hoverText = new ArrayList<>();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Add item name
            if (meta.hasDisplayName()) {
                hoverText.add(meta.getDisplayName());
            } else {
                hoverText.add(ChatColor.WHITE + TextUtil.formatItemName(item.getType().name()));
            }

            // Add lore if present
            if (meta.hasLore() && meta.getLore() != null) {
                hoverText.add(""); // Empty line
                hoverText.addAll(meta.getLore());
            }
        } else {
            hoverText.add(ChatColor.WHITE + TextUtil.formatItemName(item.getType().name()));
        }

        return hoverText;
    }

    /**
     *  buff activation announcement with improved visual design
     */
    public void announceBuffActivation(Player player, int buffRate, int durationMinutes) {
        int eliteBuffRate = buffRate / 2;
        String playerName = player.getName();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            sendBuffActivationToPlayer(onlinePlayer, playerName, buffRate, eliteBuffRate, durationMinutes);
        }
    }

    /**
     * Get the proper name for a mob (original name instead of health bar)
     */
    private String getProperMobName(LivingEntity livingDamager) {
        try {
            // First, try to get the original name from CustomMob system
            if (livingDamager.hasMetadata("type")) {
                // This is a custom mob - get its proper name from MobManager
                MobManager mobManager = MobManager.getInstance();
                if (mobManager != null) {
                    CustomMob customMob = mobManager.getCustomMob(livingDamager);
                    if (customMob != null) {
                        // Get the original name (not health bar)
                        String originalName = customMob.getOriginalName();
                        if (originalName != null && !originalName.isEmpty()) {
                            // Strip color codes and return clean name
                            return ChatColor.stripColor(originalName);
                        }
                    }
                }

                // Fallback: Use metadata if available
                String metaName = livingDamager.getMetadata("customName").get(0).asString();
                if (metaName != null && !metaName.isEmpty()) {
                    return metaName;
                }
            }

            // Check for custom name that's not a health bar
            if (livingDamager.getCustomName() != null) {
                String customName = livingDamager.getCustomName();

                // If the name contains health bar characters (|) or health indicators,
                // it's probably a health bar, so fall back to entity type
                if (!customName.contains("|") && !customName.contains("§a") && !customName.contains("§c")) {
                    return ChatColor.stripColor(customName);
                }
            }

            // Get display name from entity type with proper formatting
            String entityTypeName = livingDamager.getType().name();
            return formatEntityTypeName(entityTypeName);

        } catch (Exception e) {
            // Fallback to entity type if anything goes wrong
            YakRealms.warn("Error getting proper mob name for " + livingDamager.getType() + ": " + e.getMessage());
            return formatEntityTypeName(livingDamager.getType().name());
        }
    }

    /**
     * Format entity type name for display
     */
    private String formatEntityTypeName(String entityTypeName) {
        // Convert WITHER_SKELETON to "Wither Skeleton"
        String formatted = entityTypeName.toLowerCase().replace("_", " ");

        // Capitalize each word
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (words[i].length() > 0) {
                result.append(words[i].substring(0, 1).toUpperCase());
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }

        return result.toString();
    }

    /**
     *  buff end announcement with statistics
     */
    public void announceBuffEnd(String playerName, int improvedDrops) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendBuffEndToPlayer(player, playerName, improvedDrops);
        }
    }

    /**
     *  world boss defeat announcement with improved formatting
     */
    public void announceWorldBossDefeat(String bossName, List<Object[]> topDamagers) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendWorldBossDefeatToPlayer(player, bossName, topDamagers);
        }
    }

    /**
     * Analyzes drop data for  notifications
     */
    private DropNotificationData analyzeDropNotification(ItemStack item, LivingEntity source, boolean isBossLoot) {
        String itemName = getItemName(item);
        String sourceName = getSourceName(source);
        String rarity = detectItemRarity(item);
        String dropType = detectDropType(item);
        boolean isHighValue = isHighValueDrop(rarity, isBossLoot);

        return new DropNotificationData(itemName, sourceName, rarity, dropType, isBossLoot, isHighValue);
    }

    /**
     *  item name extraction with color preservation
     */
    private String getItemName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return TextUtil.formatItemName(item.getType().name());
    }

    /**
     *  source name with improved formatting
     */
    private String getSourceName(LivingEntity source) {
        if (source == null) {
            return "Unknown Source";
        }

        // Get tier color
        int tier = 1;
        try {
            if (MobManager.getInstance() != null) {
                CustomMob customMob = MobManager.getInstance().getCustomMob(source);
                if (customMob != null) {
                    tier = customMob.getTier();
                }
            }
        } catch (Exception e) {
            // Ignore and use default tier
        }

        ChatColor tierColor = TIER_COLORS.getOrDefault(tier, ChatColor.WHITE);
        return tierColor + getProperMobName(source);
    }

    /**
     *  rarity detection from item lore and name
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

        // Check item name colors with Tier 6 support
        if (meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            if (displayName.contains(ChatColor.YELLOW.toString()) ||
                    displayName.contains(ChatColor.GOLD.toString())) return "unique";
            if (displayName.contains(ChatColor.AQUA.toString()) ||
                    displayName.contains(ChatColor.DARK_AQUA.toString())) return "rare";
            if (displayName.contains(ChatColor.GREEN.toString()) ||
                    displayName.contains(ChatColor.DARK_GREEN.toString())) return "uncommon";
            // Check for Tier 6 Netherite
            if (displayName.contains(ChatColor.GOLD.toString()) ||
                    displayName.toLowerCase().contains("netherite")) return "legendary";
        }

        return "common";
    }

    /**
     * Detects the type of drop for appropriate symbols
     */
    private String detectDropType(ItemStack item) {
        String materialName = item.getType().name().toLowerCase();

        if (materialName.contains("sword") || materialName.contains("axe") ||
                materialName.contains("pickaxe") || materialName.contains("shovel") ||
                materialName.contains("hoe")) {
            return "weapon";
        }

        if (materialName.contains("helmet") || materialName.contains("chestplate") ||
                materialName.contains("leggings") || materialName.contains("boots")) {
            return "armor";
        }

        if (materialName.contains("emerald") || materialName.contains("diamond")) {
            return "gem";
        }

        if (materialName.contains("paper") || materialName.contains("map")) {
            return "scroll";
        }

        if (materialName.contains("book")) {
            return "book";
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
     * Plays  notification effects
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
        }.runTaskLater(YakRealms.getInstance(), 10L);
    }

    // FALLBACK METHODS for when JsonChatComponent fails

    private void sendBasicGemNotification(Player player, LivingEntity source, int amount) {
        String sourceName = getSourceName(source);
        player.sendMessage(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped " +
                ChatColor.GREEN + "💎 " + amount + " Gems");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    private void sendBasicCrateNotification(Player player, ItemStack crate, LivingEntity source, int tier) {
        String sourceName = getSourceName(source);
        String crateName = crate.getItemMeta() != null && crate.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(crate.getItemMeta().getDisplayName())
                : "Tier " + tier + " Crate";

        player.sendMessage(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped " +
                "📦 " + crateName);

        Sound crateSound = tier >= 4 ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.ENTITY_ITEM_PICKUP;
        player.playSound(player.getLocation(), crateSound, 1.0f, 1.0f + (tier * 0.1f));
    }

    private void sendBasicBookNotification(Player player, ItemStack book, LivingEntity source, String destinationName) {
        String sourceName = getSourceName(source);
        player.sendMessage(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped " +
                "📖 " + destinationName + " Teleport Book");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    /**
     * Sends buff activation message to a single player
     */
    private void sendBuffActivationToPlayer(Player player, String activatorName, int buffRate,
                                            int eliteBuffRate, int durationMinutes) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

        //  visual announcement
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GOLD + "✦ " + ChatColor.YELLOW + ChatColor.BOLD + "LOOT BUFF ACTIVATED" +
                        ChatColor.RESET + " " + ChatColor.GOLD + "✦"
        ));
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.AQUA + activatorName + ChatColor.YELLOW + " activated a server-wide loot buff!"
        ));

        // Buff details with  formatting
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
        player.sendMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.WHITE + " drops provided: " +
                ChatColor.GREEN + improvedDrops);
        player.sendMessage("");
    }

    /**
     * Sends world boss defeat message to a single player
     */
    private void sendWorldBossDefeatToPlayer(Player player, String bossName, List<Object[]> topDamagers) {
        //  sound effects
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
            String sourceName = getSourceName(source);
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
     */
    public Map<String, Object> getNotificationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rarityTypes", RARITY_COLORS.size());
        stats.put("soundMappings", RARITY_SOUNDS.size());
        stats.put("dropTypes", DROP_TYPE_SYMBOLS.size());
        stats.put("symbolMappings", RARITY_SYMBOLS.size());
        stats.put("tierColors", TIER_COLORS.size());
        return stats;
    }
}