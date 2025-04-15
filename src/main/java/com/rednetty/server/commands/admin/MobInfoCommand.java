package com.rednetty.server.commands.admin;

import com.rednetty.server.mechanics.mobs.MobManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Command for getting information about a mob
 */
public class MobInfoCommand implements CommandExecutor {
    private final MobManager mobManager;

    /**
     * Constructor
     *
     * @param mobManager The mob manager
     */
    public MobInfoCommand(MobManager mobManager) {
        this.mobManager = mobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("yakrealms.admin.mobinfo")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Get the entity the player is looking at
        Entity targetEntity = getTargetEntity(player, 5);

        if (targetEntity == null || !(targetEntity instanceof LivingEntity)) {
            player.sendMessage(ChatColor.RED + "You must be looking at a mob to use this command.");
            return true;
        }

        LivingEntity mob = (LivingEntity) targetEntity;

        // Use the mobManager to get and display mob info
        mobManager.displayMobInfo(player, mob);

        return true;
    }

    /**
     * Get the entity the player is looking at
     *
     * @param player The player
     * @param range  The maximum range to check
     * @return The entity or null if none found
     */
    private Entity getTargetEntity(Player player, double range) {
        Entity target = null;
        double targetDistanceSquared = 0;

        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity)) continue;

            if (player.hasLineOfSight(entity)) {
                double distanceSquared = player.getLocation().distanceSquared(entity.getLocation());
                if (target == null || distanceSquared < targetDistanceSquared) {
                    target = entity;
                    targetDistanceSquared = distanceSquared;
                }
            }
        }

        return target;
    }
}