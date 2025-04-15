package com.rednetty.server.mechanics.economy.vendors.visuals.animations;

import com.rednetty.server.mechanics.economy.vendors.visuals.AnimationOptions;
import com.rednetty.server.mechanics.economy.vendors.visuals.VendorAnimation;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin; /**
 * Base implementation with common animation functionality
 */
public abstract class BaseVendorAnimation implements VendorAnimation {
    protected final JavaPlugin plugin;
    protected final String vendorId;
    protected final boolean particlesEnabled;
    protected final boolean soundsEnabled;
    protected final int effectDensity;

    // Animation state variables
    protected int animationTick = 0;
    protected boolean specialEffectRunning = false;
    protected int specialEffectCooldown = 0;

    public BaseVendorAnimation(AnimationOptions options) {
        this.plugin = options.plugin;
        this.vendorId = options.vendorId;
        this.particlesEnabled = options.particlesEnabled;
        this.soundsEnabled = options.soundsEnabled;
        this.effectDensity = options.effectDensity;
    }

    @Override
    public boolean shouldApplyParticles(int configDensity) {
        if (!particlesEnabled) return false;

        // Apply based on configured density (higher = more frequent)
        switch (configDensity) {
            case 1: return animationTick % 8 == 0; // Low density
            case 3: return animationTick % 2 == 0; // High density
            default: return animationTick % 4 == 0; // Medium density
        }
    }

    @Override
    public boolean shouldPlaySound() {
        if (!soundsEnabled) return false;

        // Randomize sound frequency
        return animationTick % 60 == 0 && Math.random() < 0.3;
    }

    @Override
    public void processSpecialEffects(Location location) {
        // Increment tick for all animations
        animationTick++;

        // Decrement cooldown if active
        if (specialEffectCooldown > 0) {
            specialEffectCooldown--;
        }
    }

    @Override
    public void cleanup() {
        // Base cleanup - subclasses can extend
        specialEffectRunning = false;
    }
}
