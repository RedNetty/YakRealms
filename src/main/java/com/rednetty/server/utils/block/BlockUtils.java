package com.rednetty.server.utils.block;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utility class for finding blocks and players in areas
 */
public class BlockUtils {


    /**
     * Get all players within a certain range of a location
     * @param center Center location
     * @param range Range to search
     * @return Collection of players in range
     */
    public static Collection<Player> getPlayersInRange(Location center, double range) {
        List<Player> players = new ArrayList<>();
        World world = center.getWorld();

        if (world == null) {
            return players;
        }

        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(center) <= range) {
                players.add(player);
            }
        }

        return players;
    }

    /**
     * Get all online players in the same world
     * @param location Location to get world from
     * @return Collection of players in the same world
     */
    public static Collection<Player> getPlayersInWorld(Location location) {
        List<Player> players = new ArrayList<>();
        World world = location.getWorld();

        if (world == null) {
            return players;
        }

        return world.getPlayers();
    }
}