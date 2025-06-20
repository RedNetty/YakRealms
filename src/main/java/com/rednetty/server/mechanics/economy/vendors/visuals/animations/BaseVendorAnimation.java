package com.rednetty.server.mechanics.economy.vendors.visuals.animations;

import com.rednetty.server.mechanics.economy.vendors.visuals.AnimationOptions;
import com.rednetty.server.mechanics.economy.vendors.visuals.VendorAnimation;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Enhanced base implementation with improved animation management, performance tracking,
 * and error handling. Provides comprehensive foundation for all vendor animations with
 * optimized state management and resource cleanup.
 */
public abstract class BaseVendorAnimation implements VendorAnimation {

    // Core animation data
    protected final JavaPlugin plugin;
    protected final String vendorId;
    protected final boolean particlesEnabled;
    protected final boolean soundsEnabled;
    protected final int effectDensity;

    // Enhanced animation state with thread safety
    protected final AtomicInteger animationTick = new AtomicInteger(0);
    protected final AtomicBoolean specialEffectRunning = new AtomicBoolean(false);
    protected final AtomicInteger specialEffectCooldown = new AtomicInteger(0);
    protected final AtomicBoolean isCleanedUp = new AtomicBoolean(false);
    protected final AtomicBoolean isPaused = new AtomicBoolean(false);

    // Performance tracking
    protected final AtomicLong totalUpdates = new AtomicLong(0);
    protected final AtomicLong lastUpdateTime = new AtomicLong(System.currentTimeMillis());
    protected final AtomicLong creationTime = new AtomicLong(System.currentTimeMillis());

    // Error tracking
    protected final AtomicInteger errorCount = new AtomicInteger(0);
    protected volatile String lastError = null;
    protected final AtomicLong lastErrorTime = new AtomicLong(0);

    // Resource management
    protected final Set<Entity> managedEntities = ConcurrentHashMap.newKeySet();
    protected final Set<BukkitTask> managedTasks = ConcurrentHashMap.newKeySet();

    // Performance optimization
    private long lastParticleTime = 0;
    private long lastSoundTime = 0;
    private static final long MIN_PARTICLE_INTERVAL = 50; // 50ms between particles
    private static final long MIN_SOUND_INTERVAL = 1000; // 1s between sounds

    /**
     * Enhanced constructor with validation
     */
    public BaseVendorAnimation(AnimationOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("AnimationOptions cannot be null");
        }

        this.plugin = options.plugin;
        this.vendorId = options.vendorId;
        this.particlesEnabled = options.particlesEnabled;
        this.soundsEnabled = options.soundsEnabled;
        this.effectDensity = Math.max(1, Math.min(3, options.effectDensity));

        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        if (vendorId == null || vendorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Vendor ID cannot be null or empty");
        }
    }

    /**
     * Enhanced particle application with performance optimization
     */
    @Override
    public boolean shouldApplyParticles(int configDensity) {
        if (!particlesEnabled || isPaused.get() || isCleanedUp.get()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();

        // Performance optimization: limit particle frequency
        if (currentTime - lastParticleTime < MIN_PARTICLE_INTERVAL) {
            return false;
        }

        int currentTick = animationTick.get();
        boolean shouldApply;

        // Apply based on configured density with performance consideration
        switch (Math.max(1, Math.min(3, configDensity))) {
            case 1: // Low density
                shouldApply = currentTick % 8 == 0;
                break;
            case 3: // High density
                shouldApply = currentTick % 2 == 0;
                break;
            default: // Medium density
                shouldApply = currentTick % 4 == 0;
                break;
        }

        if (shouldApply) {
            lastParticleTime = currentTime;
        }

        return shouldApply;
    }

    /**
     * Enhanced sound playing with performance optimization
     */
    @Override
    public boolean shouldPlaySound() {
        if (!soundsEnabled || isPaused.get() || isCleanedUp.get()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();

        // Performance optimization: limit sound frequency
        if (currentTime - lastSoundTime < MIN_SOUND_INTERVAL) {
            return false;
        }

        // Randomize sound frequency to prevent spam
        boolean shouldPlay = animationTick.get() % 60 == 0 && Math.random() < 0.3;

        if (shouldPlay) {
            lastSoundTime = currentTime;
        }

        return shouldPlay;
    }

    /**
     * Enhanced special effects processing with better state management
     */
    @Override
    public void processSpecialEffects(Location location) {
        if (isPaused.get() || isCleanedUp.get()) {
            return;
        }

        try {
            // Increment tick for all animations
            int currentTick = animationTick.incrementAndGet();
            totalUpdates.incrementAndGet();
            lastUpdateTime.set(System.currentTimeMillis());

            // Decrement cooldown if active
            int cooldown = specialEffectCooldown.get();
            if (cooldown > 0) {
                specialEffectCooldown.decrementAndGet();
            }

            // Process tick-based special effects
            processTickBasedEffects(location, currentTick);

            // Cleanup check every 1000 ticks
            if (currentTick % 1000 == 0) {
                performMaintenanceCheck();
            }

        } catch (Exception e) {
            handleError("processSpecialEffects", e);
        }
    }

    /**
     * Template method for subclasses to implement tick-based effects
     */
    protected void processTickBasedEffects(Location location, int tick) {
        // Default implementation - subclasses can override
    }

    /**
     * Enhanced cleanup with comprehensive resource management
     */
    @Override
    public void cleanup() {
        if (isCleanedUp.getAndSet(true)) {
            return; // Already cleaned up
        }

        try {
            // Set special effect flag to false
            specialEffectRunning.set(false);

            // Cancel all managed tasks
            for (BukkitTask task : managedTasks) {
                try {
                    if (task != null && !task.isCancelled()) {
                        task.cancel();
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error cancelling task for vendor " + vendorId, e);
                }
            }
            managedTasks.clear();

            // Remove all managed entities
            for (Entity entity : managedEntities) {
                try {
                    if (entity != null && entity.isValid()) {
                        entity.remove();
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error removing entity for vendor " + vendorId, e);
                }
            }
            managedEntities.clear();

            // Perform additional cleanup
            performAdditionalCleanup();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during cleanup for vendor " + vendorId, e);
        }
    }

    /**
     * Template method for subclasses to implement additional cleanup
     */
    protected void performAdditionalCleanup() {
        // Default implementation - subclasses can override
    }

    /**
     * Enhanced error handling with tracking
     */
    protected void handleError(String operation, Exception e) {
        errorCount.incrementAndGet();
        lastError = operation + ": " + e.getMessage();
        lastErrorTime.set(System.currentTimeMillis());

        String message = String.format("Animation error in %s for vendor %s during %s: %s",
                getClass().getSimpleName(), vendorId, operation, e.getMessage());

        plugin.getLogger().log(Level.WARNING, message, e);

        // If too many errors, pause the animation
        if (errorCount.get() > 10) {
            isPaused.set(true);
            plugin.getLogger().warning("Animation paused for vendor " + vendorId + " due to excessive errors (" + errorCount.get() + ")");
        }
    }

    /**
     * Entity management helpers
     */
    protected void registerEntity(Entity entity) {
        if (entity != null && entity.isValid()) {
            managedEntities.add(entity);
        }
    }

    protected void unregisterEntity(Entity entity) {
        managedEntities.remove(entity);
    }

    /**
     * Task management helpers
     */
    protected void registerTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            managedTasks.add(task);
        }
    }

    protected void unregisterTask(BukkitTask task) {
        managedTasks.remove(task);
    }

    /**
     * Maintenance check for performance and resource management
     */
    private void performMaintenanceCheck() {
        try {
            // Clean up invalid entities
            managedEntities.removeIf(entity -> entity == null || !entity.isValid());

            // Clean up cancelled tasks
            managedTasks.removeIf(task -> task == null || task.isCancelled());

            // Reset error count if no recent errors
            long timeSinceLastError = System.currentTimeMillis() - lastErrorTime.get();
            if (timeSinceLastError > 300000) { // 5 minutes
                if (errorCount.get() > 0) {
                    errorCount.set(0);
                    isPaused.set(false); // Resume if paused due to errors
                }
            }

        } catch (Exception e) {
            handleError("maintenanceCheck", e);
        }
    }

    /**
     * Pause/resume animation
     */
    public void pauseAnimation() {
        isPaused.set(true);
    }

    public void resumeAnimation() {
        if (!isCleanedUp.get()) {
            isPaused.set(false);
        }
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    /**
     * Get animation performance metrics
     */
    public AnimationMetrics getMetrics() {
        return new AnimationMetrics(
                vendorId,
                getClass().getSimpleName(),
                creationTime.get(),
                totalUpdates.get(),
                lastUpdateTime.get(),
                animationTick.get(),
                errorCount.get(),
                lastError,
                lastErrorTime.get(),
                managedEntities.size(),
                managedTasks.size(),
                isPaused.get(),
                isCleanedUp.get()
        );
    }

    /**
     * Reset animation state (useful for debugging)
     */
    public void resetAnimation() {
        if (!isCleanedUp.get()) {
            animationTick.set(0);
            specialEffectRunning.set(false);
            specialEffectCooldown.set(0);
            errorCount.set(0);
            lastError = null;
            lastErrorTime.set(0);
            isPaused.set(false);
        }
    }

    /**
     * Get current animation state information
     */
    public String getStateInfo() {
        return String.format("Animation[%s] - Vendor: %s, Tick: %d, Entities: %d, Tasks: %d, Errors: %d, Paused: %s, Cleaned: %s",
                getClass().getSimpleName(),
                vendorId,
                animationTick.get(),
                managedEntities.size(),
                managedTasks.size(),
                errorCount.get(),
                isPaused.get(),
                isCleanedUp.get()
        );
    }

    /**
     * Validate animation state
     */
    public boolean isHealthy() {
        return !isCleanedUp.get() &&
                errorCount.get() < 5 &&
                (System.currentTimeMillis() - lastUpdateTime.get()) < 60000; // Updated within last minute
    }

    /**
     * Get animation age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime.get();
    }

    /**
     * Animation metrics data class
     */
    public static class AnimationMetrics {
        public final String vendorId;
        public final String animationType;
        public final long creationTime;
        public final long totalUpdates;
        public final long lastUpdateTime;
        public final int currentTick;
        public final int errorCount;
        public final String lastError;
        public final long lastErrorTime;
        public final int managedEntitiesCount;
        public final int managedTasksCount;
        public final boolean isPaused;
        public final boolean isCleanedUp;

        public AnimationMetrics(String vendorId, String animationType, long creationTime,
                                long totalUpdates, long lastUpdateTime, int currentTick,
                                int errorCount, String lastError, long lastErrorTime,
                                int managedEntitiesCount, int managedTasksCount,
                                boolean isPaused, boolean isCleanedUp) {
            this.vendorId = vendorId;
            this.animationType = animationType;
            this.creationTime = creationTime;
            this.totalUpdates = totalUpdates;
            this.lastUpdateTime = lastUpdateTime;
            this.currentTick = currentTick;
            this.errorCount = errorCount;
            this.lastError = lastError;
            this.lastErrorTime = lastErrorTime;
            this.managedEntitiesCount = managedEntitiesCount;
            this.managedTasksCount = managedTasksCount;
            this.isPaused = isPaused;
            this.isCleanedUp = isCleanedUp;
        }

        public long getAge() {
            return System.currentTimeMillis() - creationTime;
        }

        public long getTimeSinceLastUpdate() {
            return System.currentTimeMillis() - lastUpdateTime;
        }

        public boolean isHealthy() {
            return !isCleanedUp && errorCount < 5 && getTimeSinceLastUpdate() < 60000;
        }

        @Override
        public String toString() {
            return String.format("AnimationMetrics{vendor=%s, type=%s, age=%dms, updates=%d, errors=%d, healthy=%s}",
                    vendorId, animationType, getAge(), totalUpdates, errorCount, isHealthy());
        }
    }
}