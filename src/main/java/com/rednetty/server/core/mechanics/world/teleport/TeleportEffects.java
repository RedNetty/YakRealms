package com.rednetty.server.core.mechanics.world.teleport;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Handles visual and sound effects for teleportation
 */
public class TeleportEffects {

    /**
     * Applies effects when starting a teleport cast
     *
     * @param player     The player
     * @param effectType The effect type
     */
    public static void applyCastingStartEffects(Player player, TeleportEffectType effectType) {
        switch (effectType) {
            case SCROLL:
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 1));
                player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 1.0f);
                break;

            case HEARTHSTONE:
                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 1.0f);
                player.getWorld().spawnParticle(Particle.ENTITY_EFFECT, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                break;

            case PORTAL:
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 1));
                break;
        }
    }

    /**
     * Applies effects during a teleport cast tick
     *
     * @param player     The player
     * @param effectType The effect type
     */
    public static void applyCastingTickEffects(Player player, TeleportEffectType effectType) {
        switch (effectType) {
            case SCROLL:
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 300, 0, 0, 0, 4.0);
                player.getWorld().playEffect(player.getLocation().add(0, 0, 0), Effect.STEP_SOUND, Material.NETHER_PORTAL);
                player.getWorld().playEffect(player.getLocation().add(0, 1, 0), Effect.STEP_SOUND, Material.NETHER_PORTAL);
                player.getWorld().playEffect(player.getLocation().add(0, 2, 0), Effect.STEP_SOUND, Material.NETHER_PORTAL);
                break;

            case HEARTHSTONE:
                player.getWorld().spawnParticle(Particle.ENTITY_EFFECT, player.getLocation().add(0, 0.15, 0), 80, 0.5, 0, 0.5, 0.05);
                break;

            case PORTAL:
                for (int i = 0; i < 5; i++) {
                    Location loc = player.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() * 2,
                            Math.random() * 2 - 1
                    );
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 1, 0, 0, 0, 0);
                }
                break;
        }
    }

    /**
     * Applies effects when a teleport cast is cancelled
     *
     * @param player     The player
     * @param effectType The effect type
     */
    public static void applyCastingCancelEffects(Player player, TeleportEffectType effectType) {
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.05);
    }

    /**
     * Applies effects at the departure location
     *
     * @param player     The player
     * @param effectType The effect type
     */
    public static void applyDepartureEffects(Player player, TeleportEffectType effectType) {
        switch (effectType) {
            case SCROLL:
                player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 200, 0, 0, 0, 0.2);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                break;

            case HEARTHSTONE:
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 200, 0.5, 1, 0.5, 1);
                break;

            case PORTAL:
                player.getWorld().strikeLightningEffect(player.getLocation());
                player.playSound(player.getLocation(), Sound.ENTITY_SHULKER_TELEPORT, 1.0f, 1.0f);
                break;
        }
    }

    /**
     * Applies effects at the arrival location
     *
     * @param player     The player
     * @param effectType The effect type
     */
    public static void applyArrivalEffects(Player player, TeleportEffectType effectType) {
        switch (effectType) {
            case SCROLL:
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 2));
                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.0f);
                break;

            case HEARTHSTONE:
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.INSTANT_EFFECT, player.getLocation().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.05);
                break;

            case PORTAL:
                player.getWorld().strikeLightningEffect(player.getLocation());
                player.playSound(player.getLocation(), Sound.ENTITY_SHULKER_TELEPORT, 1.0f, 1.0f);

                // For Avalon portal, enable gliding
                if (player.getLocation().getY() > 90) {
                    PortalSystem.getInstance().startGliding(player);
                }
                break;
        }
    }
}