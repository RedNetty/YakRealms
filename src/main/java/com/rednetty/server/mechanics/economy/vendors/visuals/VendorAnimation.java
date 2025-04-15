package com.rednetty.server.mechanics.economy.vendors.visuals;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;

import java.util.Set; /**
 * Interface for all vendor animations
 */
public interface VendorAnimation {
    /**
     * Creates display entities for this animation
     */
    Set<Entity> createDisplayEntities(Location location, NamespacedKey key);

    /**
     * Updates animations for display entities
     */
    void updateDisplayAnimations(Set<Entity> entities, Location location);

    /**
     * Applies particle effects
     */
    void applyParticleEffects(Location location);

    /**
     * Plays ambient sound effects
     */
    void playAmbientSound(Location location);

    /**
     * Processes special effects (like special animations)
     */
    void processSpecialEffects(Location location);

    /**
     * Checks if particles should be applied based on density
     */
    boolean shouldApplyParticles(int effectDensity);

    /**
     * Checks if ambient sounds should be played
     */
    boolean shouldPlaySound();

    /**
     * Cleans up resources used by this animation
     */
    void cleanup();
}
