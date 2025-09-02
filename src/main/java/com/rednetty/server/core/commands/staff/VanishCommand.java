package com.rednetty.server.core.commands.staff;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VanishCommand implements CommandExecutor {
    private final Plugin plugin;
    private static final List<UUID> vanishedPlayers = new ArrayList<>();

    public VanishCommand(YakRealms plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (!sender.hasPermission("yakrealms.staff.vanish")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Toggle vanish state
        boolean isVanished = player.hasMetadata("vanished");

        if (isVanished) {
            // Unvanish
            player.removeMetadata("vanished", plugin);
            vanishedPlayers.remove(player.getUniqueId());

            // Remove night vision effect
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);

            // Show player to everyone
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(plugin, player);
            }

            player.sendMessage(ChatColor.GREEN + "You are now visible to other players.");

            // Notify staff
            notifyStaff(player.getName() + " is no longer vanished.", player);

        } else {
            // Vanish
            player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
            vanishedPlayers.add(player.getUniqueId());

            // Add night vision effect for QoL
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));

            // Hide player from non-staff
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("yakrealms.staff.vanish") && online != player) {
                    online.hidePlayer(plugin, player);
                }
            }

            player.sendMessage(ChatColor.GREEN + "You are now vanished. Only staff can see you.");

            // Notify staff
            notifyStaff(player.getName() + " is now vanished.", player);
        }

        return true;
    }

    private void notifyStaff(String message, Player source) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if ((staff.hasPermission("yakrealms.staff.vanish") || staff.hasPermission("yakrealms.staff"))
                    && staff != source) {
                staff.sendMessage(ChatColor.GRAY + "[Vanish] " + message);
            }
        }
    }

    /**
     * Check if a player is currently vanished
     *
     * @param player The player to check
     * @return true if the player is vanished
     */
    public static boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    /**
     * Handle vanish for a player joining the server
     *
     * @param joiningPlayer The player that is joining
     */
    public static void handlePlayerJoin(Player joiningPlayer, Plugin plugin) {
        // Hide vanished players from the joining player if they're not staff
        if (!joiningPlayer.hasPermission("yakrealms.staff.vanish")) {
            for (UUID uuid : vanishedPlayers) {
                Player vanished = Bukkit.getPlayer(uuid);
                if (vanished != null && vanished.isOnline()) {
                    joiningPlayer.hidePlayer(plugin, vanished);
                }
            }
        }
    }
}