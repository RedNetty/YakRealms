package com.rednetty.server.core.mechanics.chat;

import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Represents decorative tags that players can display in chat
 */
public enum ChatTag {
    DEFAULT("$none", false),
    SHOOTER("&c&lSHOOTER&r", true),
    NUTS("&b&lNUTS&r", true),
    PIMP("&b&lPIMP&r", true),
    MEMER("&9&lM&9E&9&lM&9E&9&lR&r", true),
    BASED("&eBASED&r", true),
    THINKING("&6&l:THINKING:", true),
    SILVER("&7&lSILVER", true),
    GAY("&d&lG&e&lA&a&lY", true),
    THOT("&d&lTHOT", true),
    CODER("&3&lCODER", true),
    DOG("&3&lDOG-WATER", true),
    HACKER("&9&lHACKER", true),
    DADDY("&b&lD&ba&b&lD&bd&b&lY", true),
    SHERIFF("&6✪", true),
    GREASY("&e&lG&6&lR&e&lE&6&lA&e&lS&6&lY", true),
    BOZO("&a&lB&e&lO&3&lZ&a&lO", true),
    YikesYouDied("&d&lYikesYouDied", true),
    MOD("&cM&9O&eD", true);

    private final String tag;
    private final boolean storeInstance;

    ChatTag(String tag, boolean storeInstance) {
        this.tag = tag;
        this.storeInstance = storeInstance;
    }

    /**
     * Get the formatted tag string
     *
     * @return The formatted tag
     */
    public String getTag() {
        if (this == DEFAULT) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', tag);
    }

    /**
     * Check if this is a store-only tag
     *
     * @return true if this tag is only available through the store
     */
    public boolean isStoreInstance() {
        return storeInstance;
    }

    /**
     * Get all chat tags as a stream
     *
     * @return Stream of all chat tags
     */
    public static Stream<ChatTag> stream() {
        return Arrays.stream(values());
    }

    /**
     * Get a list of all chat tag names
     *
     * @return Array of all tag names
     */
    public static String[] getTagNames() {
        return Arrays.stream(values())
                .map(Enum::name)
                .toArray(String[]::new);
    }

    /**
     * Find a tag by name (case-insensitive)
     *
     * @param name The tag name to search for
     * @return The matching tag or DEFAULT if not found
     */
    public static ChatTag getByName(String name) {
        try {
            return ChatTag.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}