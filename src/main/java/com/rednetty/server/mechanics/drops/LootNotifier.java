package com.rednetty.server.mechanics.drops;

import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Handles notifications for item drops
 */
public class LootNotifier {
    private static LootNotifier instance;

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
     * Sends a drop notification to a player
     *
     * @param player     The player to notify
     * @param item       The dropped item
     * @param source     The source entity or null if not from an entity
     * @param isBossLoot Whether this is from a boss
     */
    public void sendDropNotification(Player player, ItemStack item, LivingEntity source, boolean isBossLoot) {
        if (player == null || item == null) {
            return;
        }

        // Get source name if available
        String sourceName = "";
        if (source != null && source.getCustomName() != null) {
            sourceName = source.getCustomName();
        } else if (source != null) {
            // Get entity type name and format it nicely
            String typeName = source.getType().name();
            sourceName = TextUtil.formatItemName(typeName);
        }

        // Get item name
        String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : TextUtil.formatItemName(item.getType().name());

        // Create and send message
        String message;
        if (isBossLoot) {
            message = TextUtil.getCenteredMessage(ChatColor.RED + "➤ " + ChatColor.YELLOW +
                    "You have received " + itemName + ChatColor.YELLOW + " from the World-Boss");
        } else if (source != null) {
            message = ChatColor.GRAY + "➤ " + ChatColor.RESET + MobManager.getInstance().getDefaultNameForEntity(source) +
                    ChatColor.YELLOW + " has dropped " + ChatColor.RESET + itemName;
        } else {
            message = ChatColor.GRAY + "➤ " + ChatColor.YELLOW +
                    "You have received " + ChatColor.RESET + itemName;
        }

        player.sendMessage(message);

        // Play notification sound based on item rarity
        playNotificationSound(player, item);
    }

    /**
     * Plays an appropriate sound based on the rarity of the item
     *
     * @param player The player to play the sound for
     * @param item   The dropped item
     */
    private void playNotificationSound(Player player, ItemStack item) {
        if (player == null) return;

        Sound sound = Sound.ENTITY_ITEM_PICKUP;
        float volume = 1.0f;
        float pitch = 1.0f;

        // Try to determine rarity from lore
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                String lastLine = lore.get(lore.size() - 1);

                if (lastLine.contains(ChatColor.AQUA.toString())) {
                    // Rare
                    sound = Sound.BLOCK_NOTE_BLOCK_PLING;
                } else if (lastLine.contains(ChatColor.YELLOW.toString())) {
                    // Unique
                    sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
                    volume = 0.5f;
                    pitch = 1.2f;
                } else if (lastLine.contains(ChatColor.GREEN.toString())) {
                    // Uncommon
                    sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                }
            }
        }

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Announces a buff activation to all players
     *
     * @param player          The player who activated the buff
     * @param buffRate        The percentage increase for drops
     * @param durationMinutes The duration in minutes
     */
    public void announceBuffActivation(Player player, int buffRate, int durationMinutes) {
        int eliteBuffRate = buffRate / 2;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // Play sound
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            // Send messages
            onlinePlayer.sendMessage("");
            TextUtil.sendCenteredMessage(onlinePlayer, ChatColor.GOLD + "⚔ LOOT BUFF ACTIVATED ⚔");
            TextUtil.sendCenteredMessage(onlinePlayer,
                    ChatColor.AQUA + player.getName() + ChatColor.YELLOW + " has activated a loot buff!");
            onlinePlayer.sendMessage(ChatColor.LIGHT_PURPLE + "        - Drop rates improved by " +
                    ChatColor.WHITE + buffRate + "%" + ChatColor.LIGHT_PURPLE + "!");
            onlinePlayer.sendMessage(ChatColor.LIGHT_PURPLE + "        - Elite drop rates improved by " +
                    ChatColor.WHITE + eliteBuffRate + "%" + ChatColor.LIGHT_PURPLE + "!");
            onlinePlayer.sendMessage(ChatColor.LIGHT_PURPLE + "        - Duration: " +
                    ChatColor.WHITE + durationMinutes + " minutes" + ChatColor.LIGHT_PURPLE + "!");
            onlinePlayer.sendMessage("");
        }
    }

    /**
     * Announces a buff expiration to all players
     *
     * @param playerName    The name of the player who activated the buff
     * @param improvedDrops The number of drops improved by the buff
     */
    public void announceBuffEnd(String playerName, int improvedDrops) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Play sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            // Send messages
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "⚔ LOOT BUFF EXPIRED ⚔");
            TextUtil.sendCenteredMessage(player,
                    ChatColor.AQUA + playerName + ChatColor.YELLOW + "'s loot buff has expired!");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "        - " +
                    ChatColor.WHITE + improvedDrops + ChatColor.LIGHT_PURPLE + " drops were improved by this buff!");
            player.sendMessage("");
        }
    }

    /**
     * Announces a world boss defeat to all players
     *
     * @param bossName    The name of the boss
     * @param topDamagers A list of top damage dealers (player names and damage amounts)
     */
    public void announceWorldBossDefeat(String bossName, List<Object[]> topDamagers) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "✦ " + ChatColor.RED + "World Boss Defeated" + ChatColor.GOLD + " ✦");
            player.sendMessage(ChatColor.YELLOW + bossName + ChatColor.WHITE + " has been defeated!");

            // List top damage dealers
            if (!topDamagers.isEmpty()) {
                player.sendMessage(ChatColor.GOLD + "Top damage dealers:");

                int count = 0;
                for (Object[] entry : topDamagers) {
                    if (count >= 3) break;

                    String name = (String) entry[0];
                    int damage = (int) entry[1];
                    player.sendMessage(ChatColor.YELLOW + "  " + (count + 1) + ". " + name +
                            ChatColor.GRAY + " - " + ChatColor.RED + damage + " damage");

                    count++;
                }
            }

            player.sendMessage("");
        }
    }
}