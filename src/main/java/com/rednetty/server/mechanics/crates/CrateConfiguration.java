package com.rednetty.server.mechanics.crates;

// ===== CrateConfiguration.java =====
import com.rednetty.server.mechanics.crates.types.CrateType;
import org.bukkit.Sound;

import java.util.List;

/**
 * Configuration data for a crate type
 */
public class CrateConfiguration {
    private final CrateType crateType;
    private final String displayName;
    private final int tier;
    private final List<String> contents;
    private final Sound completionSound;
    private final long animationDuration;

    /**
     * Constructor for CrateConfiguration
     *
     * @param crateType         The crate type
     * @param displayName       The display name
     * @param tier             The tier level
     * @param contents         List of content descriptions
     * @param completionSound  Sound to play on completion
     * @param animationDuration Animation duration in milliseconds
     */
    public CrateConfiguration(CrateType crateType, String displayName, int tier,
                              List<String> contents, Sound completionSound, long animationDuration) {
        this.crateType = crateType;
        this.displayName = displayName;
        this.tier = tier;
        this.contents = contents;
        this.completionSound = completionSound;
        this.animationDuration = animationDuration;
    }

    // Getters
    public CrateType getCrateType() { return crateType; }
    public String getDisplayName() { return displayName; }
    public int getTier() { return tier; }
    public List<String> getContents() { return contents; }
    public Sound getCompletionSound() { return completionSound; }
    public long getAnimationDuration() { return animationDuration; }

    /**
     * Gets a content description as a formatted string
     *
     * @return Formatted content description
     */
    public String getFormattedContents() {
        return String.join(", ", contents);
    }

    /**
     * Checks if this configuration is for a Halloween crate
     *
     * @return true if this is a Halloween crate configuration
     */
    public boolean isHalloween() {
        return crateType.isHalloween();
    }
}

