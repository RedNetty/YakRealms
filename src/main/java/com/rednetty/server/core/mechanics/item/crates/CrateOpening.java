package com.rednetty.server.core.mechanics.item.crates;

import com.rednetty.server.core.mechanics.item.crates.types.CrateType;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Represents an active crate opening session
 */
public class CrateOpening {
    private final UUID sessionId;
    private final Player player;
    private final CrateType crateType;
    private final CrateConfiguration configuration;
    private final long startTime;

    private CrateOpening.OpeningPhase currentPhase;
    private long phaseStartTime;
    private int animationTicks;
    private boolean completed;

    /**
     * Phases of crate opening
     */
    public enum OpeningPhase {
        STARTING,
        SPINNING,
        SLOWING,
        REVEALING,
        COMPLETED
    }

    /**
     * Constructor for CrateOpening
     *
     * @param player        The player opening the crate
     * @param crateType     The type of crate being opened
     * @param configuration The crate configuration
     */
    public CrateOpening(Player player, CrateType crateType, CrateConfiguration configuration) {
        this.sessionId = UUID.randomUUID();
        this.player = player;
        this.crateType = crateType;
        this.configuration = configuration;
        this.startTime = System.currentTimeMillis();
        this.currentPhase = CrateOpening.OpeningPhase.STARTING;
        this.phaseStartTime = startTime;
        this.animationTicks = 0;
        this.completed = false;
    }

    /**
     * Advances to the next phase of opening
     *
     * @param nextPhase The next phase to advance to
     */
    public void advanceToPhase(CrateOpening.OpeningPhase nextPhase) {
        this.currentPhase = nextPhase;
        this.phaseStartTime = System.currentTimeMillis();

        if (nextPhase == CrateOpening.OpeningPhase.COMPLETED) {
            this.completed = true;
        }
    }

    /**
     * Increments the animation tick counter
     */
    public void incrementAnimationTicks() {
        this.animationTicks++;
    }

    /**
     * Gets the time elapsed in the current phase
     *
     * @return Time elapsed in milliseconds
     */
    public long getPhaseElapsedTime() {
        return System.currentTimeMillis() - phaseStartTime;
    }

    /**
     * Gets the total time elapsed since opening started
     *
     * @return Total time elapsed in milliseconds
     */
    public long getTotalElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Checks if the opening has been completed
     *
     * @return true if the opening is completed
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Checks if the opening has timed out
     *
     * @return true if the opening has exceeded the maximum duration
     */
    public boolean isTimedOut() {
        return getTotalElapsedTime() > (configuration.getAnimationDuration() + 10000); // 10 second buffer
    }

    /**
     * Gets the progress percentage of the current phase
     *
     * @return Progress as a value between 0.0 and 1.0
     */
    public double getPhaseProgress() {
        long phaseDuration = getPhaseDuration();
        if (phaseDuration <= 0) {
            return 1.0;
        }

        double progress = (double) getPhaseElapsedTime() / phaseDuration;
        return Math.min(1.0, Math.max(0.0, progress));
    }

    /**
     * Gets the expected duration of the current phase
     *
     * @return Duration in milliseconds
     */
    private long getPhaseDuration() {
        long totalDuration = configuration.getAnimationDuration();

        return switch (currentPhase) {
            case STARTING -> totalDuration / 10;     // 10% of total time
            case SPINNING -> totalDuration * 4 / 10; // 40% of total time
            case SLOWING -> totalDuration * 3 / 10;  // 30% of total time
            case REVEALING -> totalDuration * 2 / 10; // 20% of total time
            case COMPLETED -> 0;
        };
    }

    // Getters
    public UUID getSessionId() {
        return sessionId;
    }

    public Player getPlayer() {
        return player;
    }

    public CrateType getCrateType() {
        return crateType;
    }

    public CrateConfiguration getConfiguration() {
        return configuration;
    }

    public long getStartTime() {
        return startTime;
    }

    public CrateOpening.OpeningPhase getCurrentPhase() {
        return currentPhase;
    }

    public long getPhaseStartTime() {
        return phaseStartTime;
    }

    public int getAnimationTicks() {
        return animationTicks;
    }

    @Override
    public String toString() {
        return String.format("CrateOpening{player=%s, crateType=%s, phase=%s, elapsed=%dms}",
                player.getName(), crateType, currentPhase, getTotalElapsedTime());
    }
}
