package com.rednetty.server.mechanics.world.lootchests;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles enhanced visual and audio effects when a vault is opened automatically
 * Designed for the instant refresh system with automatic opening
 * Provides satisfying feedback for the fully automated experience
 */
public class VaultOpenEffects {

    private final JavaPlugin plugin;
    private final VaultChest vault;
    private final Player player;

    public VaultOpenEffects(JavaPlugin plugin, VaultChest vault, Player player) {
        this.plugin = plugin;
        this.vault = vault;
        this.player = player;
    }

    public void start() {
        Location vaultLoc = vault.getLocation().add(0.5, 0.5, 0.5);

        // Immediate automation effects
        spawnInstantAutomationEffects(vaultLoc);

        // Extended effects animation emphasizing the automation
        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = 60; // 3 seconds (shorter for instant refresh)

            @Override
            public void run() {
                ticks++;

                if (ticks <= maxTicks) {
                    spawnOngoingAutomationEffects(vaultLoc, ticks, maxTicks);
                }

                // Final automation effects
                if (ticks == maxTicks) {
                    spawnFinalAutomationEffects(vaultLoc);
                    sendAutomationMessage();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 2L); // Start after 0.5 seconds, run every 2 ticks
    }

    private void spawnInstantAutomationEffects(Location location) {
        LootChestManager.ChestTier tier = vault.getTier();

        // Play enhanced vault opening sound with automation feel
        location.getWorld().playSound(location, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);

        // Instant automation burst effects
        switch (tier) {
            case TIER_1:
                location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 20, 0.6, 0.6, 0.6, 0.1);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 10, 0.5, 0.5, 0.5, 0.1);
                location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
                break;

            case TIER_2:
                location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 25, 0.7, 0.7, 0.7, 0.1);
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 20, 0.6, 0.6, 0.6, 0.1);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 15, 0.5, 0.5, 0.5, 0.1);
                location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.4f);
                location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
                break;

            case TIER_3:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 30, 0.8, 0.8, 0.8, 0.15);
                location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, 20, 0.6, 0.6, 0.6, 0.1);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 20, 0.6, 0.6, 0.6, 0.1);
                location.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 0.6f, 1.3f);
                location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.6f);
                location.getWorld().playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.5f);
                break;

            case TIER_4:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 35, 1.0, 1.0, 1.0, 0.2);
                location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 25, 0.7, 0.7, 0.7, 0.1);
                location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, 20, 0.6, 0.6, 0.6, 0.1);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 25, 0.7, 0.7, 0.7, 0.1);
                location.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 0.8f, 1.6f);
                location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
                location.getWorld().playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.6f);
                break;

            case TIER_5:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 40, 1.2, 1.2, 1.2, 0.25);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 30, 0.8, 0.8, 0.8, 0.15);
                location.getWorld().spawnParticle(Particle.GLOW, location, 25, 0.7, 0.7, 0.7, 0.1);
                location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 20, 0.6, 0.6, 0.6, 0.1);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 30, 0.8, 0.8, 0.8, 0.15);
                location.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 1.0f, 1.9f);
                location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
                location.getWorld().playSound(location, Sound.BLOCK_VAULT_BREAK, 0.6f, 1.6f);
                location.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
                break;

            case TIER_6:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 60, 1.5, 1.5, 1.5, 0.3);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 45, 1.2, 1.2, 1.2, 0.2);
                location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 35, 1.0, 1.0, 1.0, 0.15);
                location.getWorld().spawnParticle(Particle.FIREWORK, location, 30, 0.8, 0.8, 0.8, 0.25);
                location.getWorld().spawnParticle(Particle.GLOW, location, 25, 0.7, 0.7, 0.7, 0.1);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 40, 1.0, 1.0, 1.0, 0.2);

                // Epic automation sounds for mythic tier
                location.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 1.0f, 2.0f);
                location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.2f);
                location.getWorld().playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.2f);
                location.getWorld().playSound(location, Sound.BLOCK_VAULT_BREAK, 0.8f, 1.9f);
                location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 2.2f);
                location.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
                location.getWorld().playSound(location, Sound.BLOCK_CONDUIT_ACTIVATE, 0.6f, 1.8f);
                break;
        }
    }

    private void spawnOngoingAutomationEffects(Location location, int currentTick, int maxTicks) {
        LootChestManager.ChestTier tier = vault.getTier();
        double progress = (double) currentTick / maxTicks;

        // Intensity decreases over time but maintains automation theme
        double intensity = 1.0 - (progress * 0.6); // Reduce to 40% by the end

        int particleCount = (int) Math.max(2, (3 + tier.getLevel()) * intensity);
        double spread = 0.3 + (tier.getLevel() * 0.05) * intensity;

        // Base automation particles for all tiers
        if (currentTick % 4 == 0) { // Every 8 ticks
            location.getWorld().spawnParticle(Particle.ENCHANT, location, particleCount, spread, spread, spread, 0.08);
            // Automation spark effects
            location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location,
                    Math.max(1, particleCount / 3), spread * 0.7, spread * 0.7, spread * 0.7, 0.05);
        }

        // Tier-specific ongoing automation effects
        if (tier.getLevel() >= 3 && currentTick % 6 == 0) {
            location.getWorld().spawnParticle(Particle.SCULK_SOUL, location,
                    Math.max(1, particleCount / 2), spread, spread, spread, 0.03);
        }

        if (tier.getLevel() >= 4 && currentTick % 8 == 0) {
            location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location,
                    Math.max(1, particleCount / 3), spread, spread, spread, 0.03);
        }

        if (tier.getLevel() >= 5 && currentTick % 7 == 0) {
            location.getWorld().spawnParticle(Particle.END_ROD, location,
                    Math.max(1, particleCount / 2), spread, spread, spread, 0.03);
            location.getWorld().spawnParticle(Particle.GLOW, location,
                    Math.max(1, particleCount / 4), spread * 0.8, spread * 0.8, spread * 0.8, 0.02);
        }

        if (tier.getLevel() == 6 && currentTick % 10 == 0) {
            location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location,
                    Math.max(1, particleCount / 3), spread, spread, spread, 0.03);

            // Epic automation effects for mythic
            if (currentTick % 15 == 0) {
                location.getWorld().spawnParticle(Particle.FIREWORK, location, 2,
                        spread * 1.2, spread * 1.2, spread * 1.2, 0.1);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 5,
                        spread * 1.5, spread * 1.5, spread * 1.5, 0.1);
            }
        }

        // Periodic automation sound effects
        if (tier.getLevel() >= 4 && currentTick % 20 == 0) {
            float pitch = 1.2f + (float) (progress * 0.6);
            location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.3f, pitch);
        }

        // Electric automation sounds for higher tiers
        if (tier.getLevel() >= 5 && currentTick % 25 == 0) {
            location.getWorld().playSound(location, Sound.BLOCK_BEACON_AMBIENT, 0.2f, 1.5f + (float) progress);
        }
    }

    private void spawnFinalAutomationEffects(Location location) {
        LootChestManager.ChestTier tier = vault.getTier();

        // Final automation burst based on tier
        switch (tier) {
            case TIER_1:
            case TIER_2:
                location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 25, 1.0, 1.0, 1.0, 0.2);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 15, 0.8, 0.8, 0.8, 0.1);
                break;

            case TIER_3:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 30, 1.2, 1.2, 1.2, 0.25);
                location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, 20, 1.0, 1.0, 1.0, 0.15);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 20, 1.0, 1.0, 1.0, 0.1);
                break;

            case TIER_4:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 35, 1.5, 1.5, 1.5, 0.3);
                location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 25, 1.2, 1.2, 1.2, 0.15);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 25, 1.2, 1.2, 1.2, 0.15);
                break;

            case TIER_5:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 40, 1.8, 1.8, 1.8, 0.35);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 30, 1.5, 1.5, 1.5, 0.2);
                location.getWorld().spawnParticle(Particle.GLOW, location, 25, 1.2, 1.2, 1.2, 0.15);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 30, 1.5, 1.5, 1.5, 0.2);
                break;

            case TIER_6:
                location.getWorld().spawnParticle(Particle.EXPLOSION, location, 3);
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 60, 2.5, 2.5, 2.5, 0.4);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 50, 2.0, 2.0, 2.0, 0.3);
                location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 40, 1.5, 1.5, 1.5, 0.2);
                location.getWorld().spawnParticle(Particle.FIREWORK, location, 35, 1.2, 1.2, 1.2, 0.3);
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 50, 2.0, 2.0, 2.0, 0.25);

                // Final mythic automation sound
                location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
                location.getWorld().playSound(location, Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f, 1.5f);
                break;
        }

        // Universal final automation sound
        location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 1.8f);
    }

    private void sendAutomationMessage() {
        player.playSound(player.getLocation(), Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 1.2f);

        // Send instant refresh reminder
        if (vault.getTimesOpened() > 1) {
            player.sendMessage("§7Times opened: §f" + vault.getTimesOpened());
        }
    }
}