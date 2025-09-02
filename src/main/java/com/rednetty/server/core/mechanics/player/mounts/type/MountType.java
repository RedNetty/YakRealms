package com.rednetty.server.core.mechanics.player.mounts.type;

/**
 * Enum representing the different types of mounts
 */
public enum MountType {
    NONE(0),
    HORSE(1),
    ELYTRA(2);

    private final int tier;

    MountType(int tier) {
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }
}