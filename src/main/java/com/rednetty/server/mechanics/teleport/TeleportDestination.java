package com.rednetty.server.mechanics.teleport;

import org.bukkit.Location;

/**
 * Represents a teleport destination
 */
public class TeleportDestination {
    private final String id;
    private final String displayName;
    private Location location;
    private int cost;
    private boolean premium;

    /**
     * Creates a new teleport destination
     *
     * @param id          The unique identifier
     * @param displayName The display name
     * @param location    The teleport location
     * @param cost        The cost in gems
     * @param premium     Whether this is a premium destination
     */
    public TeleportDestination(String id, String displayName, Location location, int cost, boolean premium) {
        this.id = id;
        this.displayName = displayName;
        this.location = location;
        this.cost = cost;
        this.premium = premium;
    }

    /**
     * Gets the destination ID
     *
     * @return The destination ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the display name
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the teleport location
     *
     * @return The teleport location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Sets the teleport location
     *
     * @param location The new location
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Gets the cost in gems
     *
     * @return The cost
     */
    public int getCost() {
        return cost;
    }

    /**
     * Sets the cost in gems
     *
     * @param cost The new cost
     */
    public void setCost(int cost) {
        this.cost = cost;
    }

    /**
     * Checks if this is a premium destination
     *
     * @return true if premium
     */
    public boolean isPremium() {
        return premium;
    }

    /**
     * Sets whether this is a premium destination
     *
     * @param premium Whether this is premium
     */
    public void setPremium(boolean premium) {
        this.premium = premium;
    }
}