package com.rednetty.server.utils.particles;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Modernized particle utility class for Minecraft 1.21.7
 * Based on the original ParticleEffect library by DarkBlade12
 */
public enum Particles {
    // Map old particle names to new Bukkit Particle enum (updated for 1.21.7)
    EXPLOSION_NORMAL(Particle.EXPLOSION),
    EXPLOSION_LARGE(Particle.EXPLOSION_EMITTER),
    EXPLOSION_HUGE(Particle.EXPLOSION_EMITTER),
    FIREWORKS_SPARK(Particle.FIREWORK),
    WATER_BUBBLE(Particle.BUBBLE),
    WATER_SPLASH(Particle.SPLASH),
    WATER_WAKE(Particle.FISHING),
    CRIT(Particle.CRIT),
    CRIT_MAGIC(Particle.ENCHANTED_HIT),
    SMOKE_NORMAL(Particle.SMOKE),
    SMOKE_LARGE(Particle.LARGE_SMOKE),
    SPELL(Particle.EFFECT),
    SPELL_INSTANT(Particle.INSTANT_EFFECT),
    SPELL_MOB(Particle.ENTITY_EFFECT),
    SPELL_WITCH(Particle.WITCH),
    DRIP_WATER(Particle.DRIPPING_WATER),
    DRIP_LAVA(Particle.DRIPPING_LAVA),
    VILLAGER_ANGRY(Particle.ANGRY_VILLAGER),
    VILLAGER_HAPPY(Particle.HAPPY_VILLAGER),
    TOWN_AURA(Particle.MYCELIUM),
    NOTE(Particle.NOTE),
    PORTAL(Particle.PORTAL),
    ENCHANTMENT_TABLE(Particle.ENCHANT),
    FLAME(Particle.FLAME),
    LAVA(Particle.LAVA),
    FOOTSTEP(Particle.BLOCK), // FOOTSTEP is removed, using BLOCK as alternative
    CLOUD(Particle.CLOUD),
    REDSTONE(Particle.DUST),
    SNOWBALL(Particle.POOF),
    SNOW_SHOVEL(Particle.POOF),
    SLIME(Particle.ITEM_SLIME),
    HEART(Particle.HEART),
    ITEM_CRACK(Particle.ITEM),
    BLOCK_CRACK(Particle.BLOCK),
    WATER_DROP(Particle.FALLING_WATER),
    MOB_APPEARANCE(Particle.ELDER_GUARDIAN);

    private final Particle bukkitParticle;

    Particles(Particle bukkitParticle) {
        this.bukkitParticle = bukkitParticle;
    }

    /**
     * Gets the corresponding Bukkit Particle
     *
     * @return The Bukkit Particle enum value
     */
    public Particle getBukkitParticle() {
        return bukkitParticle;
    }

    /**
     * Display particles at a location with specified parameters
     *
     * @param offsetX Maximum distance particles can fly away from the center on the x-axis
     * @param offsetY Maximum distance particles can fly away from the center on the y-axis
     * @param offsetZ Maximum distance particles can fly away from the center on the z-axis
     * @param speed   Display speed of the particles
     * @param amount  Amount of particles
     * @param center  Center location of the effect
     * @param range   Range of the visibility
     */
    public void display(float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, double range) {
        World world = center.getWorld();
        if (world == null) return;

        world.spawnParticle(bukkitParticle, center, amount, offsetX, offsetY, offsetZ, speed);
    }

    /**
     * Display particles at a location for specific players
     *
     * @param offsetX Maximum distance particles can fly away from the center on the x-axis
     * @param offsetY Maximum distance particles can fly away from the center on the y-axis
     * @param offsetZ Maximum distance particles can fly away from the center on the z-axis
     * @param speed   Display speed of the particles
     * @param amount  Amount of particles
     * @param center  Center location of the effect
     * @param players List of players who should see the particles
     */
    public void display(float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, List<Player> players) {
        for (Player player : players) {
            player.spawnParticle(bukkitParticle, center, amount, offsetX, offsetY, offsetZ, speed);
        }
    }

    /**
     * Display particles at a location for specific players
     *
     * @param offsetX Maximum distance particles can fly away from the center on the x-axis
     * @param offsetY Maximum distance particles can fly away from the center on the y-axis
     * @param offsetZ Maximum distance particles can fly away from the center on the z-axis
     * @param speed   Display speed of the particles
     * @param amount  Amount of particles
     * @param center  Center location of the effect
     * @param players Array of players who should see the particles
     */
    public void display(float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, Player... players) {
        for (Player player : players) {
            player.spawnParticle(bukkitParticle, center, amount, offsetX, offsetY, offsetZ, speed);
        }
    }

    /**
     * Display a single particle with a direction vector
     *
     * @param direction Direction vector
     * @param speed     Speed of the particle
     * @param center    Center location of the effect
     * @param range     Range of visibility
     */
    public void display(Vector direction, float speed, Location center, double range) {
        World world = center.getWorld();
        if (world == null) return;

        // Convert direction vector to offsets
        float offsetX = (float) direction.getX();
        float offsetY = (float) direction.getY();
        float offsetZ = (float) direction.getZ();

        world.spawnParticle(bukkitParticle, center, 0, offsetX, offsetY, offsetZ, speed);
    }

    /**
     * Display a single particle with a direction vector to specific players
     *
     * @param direction Direction vector
     * @param speed     Speed of the particle
     * @param center    Center location of the effect
     * @param players   List of players who should see the particle
     */
    public void display(Vector direction, float speed, Location center, List<Player> players) {
        float offsetX = (float) direction.getX();
        float offsetY = (float) direction.getY();
        float offsetZ = (float) direction.getZ();

        for (Player player : players) {
            player.spawnParticle(bukkitParticle, center, 0, offsetX, offsetY, offsetZ, speed);
        }
    }

    /**
     * Display a colored particle (only works with DUST, ENTITY_EFFECT, AMBIENT_ENTITY_EFFECT, NOTE)
     *
     * @param color  The color to display
     * @param center Center location of the effect
     * @param range  Range of visibility
     */
    public void display(Color color, Location center, double range) {
        World world = center.getWorld();
        if (world == null) return;

        if (bukkitParticle == Particle.DUST) {
            Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);
            world.spawnParticle(bukkitParticle, center, 1, 0, 0, 0, 0, dustOptions);
        } else if (bukkitParticle == Particle.ENTITY_EFFECT || bukkitParticle == Particle.ENTITY_EFFECT) {
            // Color format for colored mob spell particles: RGB values are packed into the offset parameters
            double red = color.getRed() / 255.0;
            double green = color.getGreen() / 255.0;
            double blue = color.getBlue() / 255.0;
            world.spawnParticle(bukkitParticle, center, 0, red, green, blue, 1);
        } else if (bukkitParticle == Particle.NOTE) {
            // Note particles use a single color value from 0-24, packed into offsetX
            world.spawnParticle(bukkitParticle, center, 0, color.getRed() % 24 / 24.0, 0, 0, 1);
        }
    }

    /**
     * Display a colored particle to specific players
     *
     * @param color   The color to display
     * @param center  Center location of the effect
     * @param players List of players who should see the particle
     */
    public void display(Color color, Location center, List<Player> players) {
        if (bukkitParticle == Particle.DUST) {
            Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);
            for (Player player : players) {
                player.spawnParticle(bukkitParticle, center, 1, 0, 0, 0, 0, dustOptions);
            }
        } else if (bukkitParticle == Particle.ENTITY_EFFECT || bukkitParticle == Particle.BLOCK) {
            double red = color.getRed() / 255.0;
            double green = color.getGreen() / 255.0;
            double blue = color.getBlue() / 255.0;
            for (Player player : players) {
                player.spawnParticle(bukkitParticle, center, 0, red, green, blue, 1);
            }
        } else if (bukkitParticle == Particle.NOTE) {
            for (Player player : players) {
                player.spawnParticle(bukkitParticle, center, 0, color.getRed() % 24 / 24.0, 0, 0, 1);
            }
        }
    }

    /**
     * Display a block or item particle
     *
     * @param data    Material data for the particle
     * @param offsetX Maximum distance particles can fly away from the center on the x-axis
     * @param offsetY Maximum distance particles can fly away from the center on the y-axis
     * @param offsetZ Maximum distance particles can fly away from the center on the z-axis
     * @param speed   Display speed of the particles
     * @param amount  Amount of particles
     * @param center  Center location of the effect
     * @param range   Range of visibility
     */
    public void display(org.bukkit.Material data, float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, double range) {
        World world = center.getWorld();
        if (world == null) return;

        if (bukkitParticle == Particle.ITEM) {
            world.spawnParticle(bukkitParticle, center, amount, offsetX, offsetY, offsetZ, speed, new org.bukkit.inventory.ItemStack(data));
        } else if (bukkitParticle == Particle.BLOCK || bukkitParticle == Particle.BLOCK_CRUMBLE || bukkitParticle == Particle.FALLING_DUST) {
            world.spawnParticle(bukkitParticle, center, amount, offsetX, offsetY, offsetZ, speed, data.createBlockData());
        }
    }

    /**
     * Display a block or item particle to specific players
     *
     * @param data    Material data for the particle
     * @param offsetX Maximum distance particles can fly away from the center on the x-axis
     * @param offsetY Maximum distance particles can fly away from the center on the y-axis
     * @param offsetZ Maximum distance particles can fly away from the center on the z-axis
     * @param speed   Display speed of the particles
     * @param amount  Amount of particles
     * @param center  Center location of the effect
     * @param players List of players who should see the particles
     */
    public void display(org.bukkit.Material data, float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, List<Player> players) {
        if (bukkitParticle == Particle.ITEM) {
            for (Player player : players) {
                player.spawnParticle(bukkitParticle, center, amount, offsetX, offsetY, offsetZ, speed, new org.bukkit.inventory.ItemStack(data));
            }
        } else if (bukkitParticle == Particle.BLOCK || bukkitParticle == Particle.BLOCK || bukkitParticle == Particle.FALLING_DUST) {
            for (Player player : players) {
                player.spawnParticle(bukkitParticle, center, amount, offsetX, offsetY, offsetZ, speed, data.createBlockData());
            }
        }
    }
}