package com.rednetty.server.mechanics.combat.logout;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Combat logout data tracking with  state management
 */
public class CombatLogoutData {
    public final long timestamp;
    public final Player attacker;
    public final Location logoutLocation;
    public final String alignment;
    public final List<ItemStack> droppedItems;
    public final List<ItemStack> keptItems;
    public final String world;
    public final double x, y, z;
    public final float yaw, pitch;
    public boolean messageShown = false;
    public boolean deathScheduled = false;
    public boolean itemsProcessed = false;
    public String respawnItemsBackup = null; //  Store backup of respawn items

    CombatLogoutData(Player attacker, Location logoutLocation, String alignment) {
        this.timestamp = System.currentTimeMillis();
        this.attacker = attacker;
        this.logoutLocation = logoutLocation.clone();
        this.alignment = alignment;
        this.droppedItems = new ArrayList<>();
        this.keptItems = new ArrayList<>();
        // Store exact location data
        this.world = logoutLocation.getWorld().getName();
        this.x = logoutLocation.getX();
        this.y = logoutLocation.getY();
        this.z = logoutLocation.getZ();
        this.yaw = logoutLocation.getYaw();
        this.pitch = logoutLocation.getPitch();
    }
}