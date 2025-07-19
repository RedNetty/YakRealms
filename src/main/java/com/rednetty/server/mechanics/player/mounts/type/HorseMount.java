package com.rednetty.server.mechanics.player.mounts.type;

import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.mounts.MountConfig;
import com.rednetty.server.mechanics.player.mounts.MountManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Handler for horse mounts - FIXED to allow Tier 1+ summoning with ROBUST mounting system
 * Players purchase tier access through vendors, then summon through this system
 *  with anti-glitch protection, remounting capabilities, and epic visual effects
 */
public class HorseMount implements Mount {
    private final MountManager manager;
    private final Map<UUID, BukkitTask> summonTasks = new HashMap<>();
    private final Map<UUID, Location> summonLocations = new HashMap<>();
    private final Map<UUID, Horse> activeHorses = new HashMap<>();
    private final Map<UUID, BukkitTask> effectTasks = new HashMap<>();
    private final NamespacedKey ownerKey;

    public HorseMount(MountManager manager) {
        this.manager = manager;
        this.ownerKey = new NamespacedKey(manager.getPlugin(), "horse_owner");
    }

    @Override
    public MountType getType() {
        return MountType.HORSE;
    }

    @Override
    public boolean summonMount(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) {
            player.sendMessage(ChatColor.RED + "Could not load your player data.");
            return false;
        }

        // Check if player has ANY horse tier (>= 1, not >= 2)
        int horseTier = yakPlayer.getHorseTier();
        if (horseTier < 1) {
            player.sendMessage(ChatColor.RED + "You don't have a horse mount.");
            player.sendMessage(ChatColor.YELLOW + "Visit the Mount Stable to purchase tier access!");
            return false;
        }

        // Check if already summoning
        if (isSummoning(player)) {
            player.sendMessage(ChatColor.RED + "You are already summoning a mount.");
            return false;
        }

        // Check if already has active mount
        if (manager.hasActiveMount(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You already have an active mount! Dismiss it first.");
            return false;
        }

        boolean inSafeZone = AlignmentMechanics.getInstance().isSafeZone(player.getLocation());

        // If in safe zone and instant mounting is enabled, spawn immediately
        if (inSafeZone && manager.getConfig().isInstantMountInSafeZone()) {
            spawnHorse(player, horseTier);
            return true;
        }

        // Otherwise, start summoning process
        int summonTime = yakPlayer.getAlignment().equalsIgnoreCase("CHAOTIC")
                ? manager.getConfig().getChaoticHorseSummonTime()
                : manager.getConfig().getHorseSummonTime();

        summonLocations.put(player.getUniqueId(), player.getLocation());

        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(horseTier);
        String tierColor = getTierColor(horseTier);

        player.sendMessage(ChatColor.WHITE + "Summoning " +
                tierColor + stats.getName() +
                ChatColor.WHITE + " ... " + summonTime + "s");

        BukkitTask task = new BukkitRunnable() {
            int countdown = summonTime;

            @Override
            public void run() {
                // Check if player is still online
                if (!player.isOnline()) {
                    cancel();
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    return;
                }

                // Decrement countdown
                countdown--;

                if (countdown <= 0) {
                    // Spawn the horse
                    spawnHorse(player, horseTier);

                    // Clean up
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    cancel();
                } else {
                    // Update message with tier info
                    player.sendMessage(ChatColor.WHITE + "Summoning " +
                            tierColor + stats.getName() +
                            ChatColor.WHITE + " ... " + countdown + "s");
                    playSummonEffects(player, horseTier);
                }
            }
        }.runTaskTimer(manager.getPlugin(), 20L, 20L);

        summonTasks.put(player.getUniqueId(), task);
        return true;
    }

    @Override
    public boolean dismount(Player player, boolean sendMessage) {
        UUID playerUUID = player.getUniqueId();

        // Check if player has an active horse
        Horse horse = activeHorses.remove(playerUUID);
        if (horse == null || !horse.isValid()) {
            return false;
        }

        // Clean up visual effects
        cleanupEffects(player);

        // Remove horse and dismount player
        if (horse.getPassengers().contains(player)) {
            horse.removePassenger(player);
        }

        // Play dismount effects based on tier
        int tier = getTierFromHorse(horse);
        playDismountEffects(horse.getLocation(), tier);

        // Teleport player to horse location before removing (safer dismount)
        Location dismountLocation = horse.getLocation().clone();
        dismountLocation.setY(dismountLocation.getY() + 0.5);
        player.teleport(dismountLocation);

        horse.remove();

        // Unregister from mount manager
        manager.unregisterActiveMount(playerUUID);

        if (sendMessage) {
            player.sendMessage(ChatColor.RED + "Your mount has been dismissed.");
        }

        return true;
    }

    @Override
    public boolean isSummoning(Player player) {
        return summonTasks.containsKey(player.getUniqueId());
    }

    @Override
    public void cancelSummoning(Player player, String reason) {
        UUID playerUUID = player.getUniqueId();

        BukkitTask task = summonTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }

        summonLocations.remove(playerUUID);

        player.sendMessage(ChatColor.RED + "Mount Summon - " + ChatColor.BOLD + "CANCELLED" +
                (reason != null ? " - " + reason : ""));
    }

    /**
     * Checks if the player has moved from their summoning location
     */
    public boolean hasMoved(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!summonLocations.containsKey(playerUUID)) {
            return false;
        }

        Location summonLocation = summonLocations.get(playerUUID);
        Location currentLocation = player.getLocation();

        return summonLocation.distanceSquared(currentLocation) > 2.0;
    }

    /**
     *  method for spawning horses with proper tier support
     */
    private void spawnHorse(Player player, int tier) {
        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(tier);

        if (stats == null) {
            player.sendMessage(ChatColor.RED + "Error: Could not load Tier " + tier + " mount data.");
            return;
        }

        // Create the horse
        Horse horse = player.getWorld().spawn(player.getLocation(), Horse.class);

        // Set basic horse properties
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(player);

        // Set horse appearance based on tier
        setHorseAppearance(horse, tier);

        // Set  attributes based on tier
        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(stats.getSpeed());
        horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH).setBaseValue(stats.getJump());
        horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0 + (tier * 2)); // More health per tier
        horse.setHealth(horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());

        // Set inventory with saddle
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));

        // Add tier-appropriate armor
        addTierArmor(horse, tier, stats);

        // Set  custom name with tier
        String tierColor = getTierColor(tier);
        String displayName = tierColor + stats.getName();
        horse.setCustomName(displayName);
        horse.setCustomNameVisible(true);

        // Store owner and tier in persistent data
        horse.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        horse.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

        // Add the player as a passenger
        horse.addPassenger(player);

        // Register active horse
        activeHorses.put(player.getUniqueId(), horse);
        manager.registerActiveMount(player.getUniqueId(), this);

        // Apply tier-based effects and bonuses
        applyTierEffects(horse, player, tier);

        //  success message with tier effects
        player.sendMessage(ChatColor.GRAY + "🐎 Successfully summoned " + displayName + ChatColor.GRAY + "! 🐎");

    }

    /**
     * Set horse appearance based on tier
     */
    private void setHorseAppearance(Horse horse, int tier) {
        switch (tier) {
            case 1:
                horse.setColor(Horse.Color.BROWN);
                horse.setStyle(Horse.Style.NONE);
                break;
            case 2:
                horse.setColor(Horse.Color.CHESTNUT);
                horse.setStyle(Horse.Style.WHITE);
                break;
            case 3:
                horse.setColor(Horse.Color.DARK_BROWN);
                horse.setStyle(Horse.Style.WHITEFIELD);
                break;
            case 4:
                horse.setColor(Horse.Color.BLACK);
                horse.setStyle(Horse.Style.WHITE_DOTS);
                break;
            case 5:
                horse.setColor(Horse.Color.GRAY);
                horse.setStyle(Horse.Style.BLACK_DOTS);
                break;
            case 6:
                horse.setColor(Horse.Color.WHITE);
                horse.setStyle(Horse.Style.NONE);
                break;
            default:
                horse.setColor(Horse.Color.BROWN);
                horse.setStyle(Horse.Style.NONE);
        }
    }

    /**
     * Add appropriate armor based on tier
     */
    private void addTierArmor(Horse horse, int tier, MountConfig.HorseStats stats) {
        String armorType = stats.getArmorType();

        if (armorType.equals("NONE")) {
            // Auto-assign armor based on tier if not specified
            armorType = switch (tier) {
                case 1, 2 -> "LEATHER";
                case 3 -> "IRON";
                case 4 -> "DIAMOND";
                case 5, 6 -> "GOLD";
                default -> "NONE";
            };
        }

        Material armorMaterial = switch (armorType.toUpperCase()) {
            case "IRON" -> Material.IRON_HORSE_ARMOR;
            case "GOLD", "GOLDEN" -> Material.GOLDEN_HORSE_ARMOR;
            case "DIAMOND" -> Material.DIAMOND_HORSE_ARMOR;
            case "LEATHER" -> Material.LEATHER_HORSE_ARMOR;
            default -> null;
        };

        if (armorMaterial != null) {
            horse.getInventory().setArmor(new ItemStack(armorMaterial));
        }
    }

    /**
     * Get color for tier display
     */
    private String getTierColor(int tier) {
        return switch (tier) {
            case 1 -> "§f"; // White
            case 2 -> "§a"; // Green
            case 3 -> "§b"; // Aqua
            case 4 -> "§d"; // Light Purple
            case 5 -> "§e"; // Yellow
            case 6 -> "§6"; // Gold
            default -> "§7"; // Gray
        };
    }

    /**
     *  Apply tier-based visual effects and bonuses to player only (no horse potions)
     */
    private void applyTierEffects(Horse horse, Player player, int tier) {
        // Start continuous particle effects
        startContinuousEffects(horse, player, tier);
    }

    /**
     *  Start continuous particle and sound effects for the mount
     */
    private void startContinuousEffects(Horse horse, Player player, int tier) {
        UUID playerUUID = player.getUniqueId();

        // Cancel any existing effect task
        BukkitTask existingTask = effectTasks.get(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }

        BukkitTask effectTask = new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !horse.isValid() ||
                        !activeHorses.containsKey(playerUUID) ||
                        !player.isInsideVehicle()) {
                    cancel();
                    effectTasks.remove(playerUUID);
                    return;
                }

                tickCount++;
                Location horseLoc = horse.getLocation();

                // Apply different effects based on tier
                switch (tier) {
                    case 1 -> {
                        // Basic sparkle trail
                        if (tickCount % 5 == 0) {
                            spawnParticleTrail(horseLoc, Particle.FIREWORKS_SPARK, 3);
                        }
                    }
                    case 2 -> {
                        // Healing aura with green particles
                        if (tickCount % 3 == 0) {
                            spawnParticleTrail(horseLoc, Particle.VILLAGER_HAPPY, 5);
                        }
                    }
                    case 3 -> {
                        // Frost trail with speed effects
                        if (tickCount % 2 == 0) {
                            spawnParticleTrail(horseLoc, Particle.SNOWFLAKE, 8);
                            spawnParticleAura(horseLoc, Particle.DOLPHIN, 3);
                        }
                        if (tickCount % 40 == 0) {
                            horse.getWorld().playSound(horseLoc, Sound.BLOCK_SNOW_BREAK, 0.3f, 1.5f);
                        }
                    }
                    case 4 -> {
                        // Magic aura with enchantment particles
                        if (tickCount % 2 == 0) {
                            spawnParticleAura(horseLoc, Particle.ENCHANTMENT_TABLE, 10);
                            spawnParticleTrail(horseLoc, Particle.PORTAL, 6);
                        }
                        if (tickCount % 60 == 0) {
                            horse.getWorld().playSound(horseLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.2f);
                        }
                    }
                    case 5 -> {
                        // Golden flames with power effects
                        if (tickCount % 2 == 0) {
                            spawnParticleTrail(horseLoc, Particle.FLAME, 8);
                            spawnParticleAura(horseLoc, Particle.LAVA, 4);
                        }
                        if (tickCount % 3 == 0) {
                            spawnParticleCircle(horseLoc, Particle.DRIP_LAVA, 12, 2.0);
                        }
                        if (tickCount % 80 == 0) {
                            horse.getWorld().playSound(horseLoc, Sound.ENTITY_BLAZE_AMBIENT, 0.3f, 0.8f);
                        }
                    }
                    case 6 -> {
                        // Epic multi-effect storm
                        if (0 == 0) {
                            spawnParticleTrail(horseLoc, Particle.END_ROD, 12);
                            spawnParticleAura(horseLoc, Particle.DRAGON_BREATH, 8);
                        }
                        if (tickCount % 2 == 0) {
                            spawnParticleCircle(horseLoc, Particle.FLAME, 16, 3.0);
                            spawnParticleCircle(horseLoc, Particle.ENCHANTMENT_TABLE, 20, 2.5);
                        }
                        if (tickCount % 3 == 0) {
                            spawnParticleSpiral(horseLoc, Particle.FIREWORKS_SPARK, 15);
                        }
                        if (tickCount % 100 == 0) {
                            horse.getWorld().playSound(horseLoc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.2f, 1.5f);
                            // Epic burst effect
                            spawnParticleBurst(horseLoc, Particle.TOTEM, 30);
                        }
                    }
                }

                // Special jump effect for all tiers
                if (horse.getVelocity().getY() > 0.3) {
                    switch (tier) {
                        case 1, 2 -> spawnParticleBurst(horseLoc, Particle.CLOUD, 8);
                        case 3 -> spawnParticleBurst(horseLoc, Particle.SNOWFLAKE, 15);
                        case 4 -> spawnParticleBurst(horseLoc, Particle.ENCHANTMENT_TABLE, 20);
                        case 5 -> spawnParticleBurst(horseLoc, Particle.FLAME, 25);
                        case 6 -> {
                            spawnParticleBurst(horseLoc, Particle.END_ROD, 30);
                            spawnParticleBurst(horseLoc, Particle.DRAGON_BREATH, 15);
                        }
                    }
                }
            }
        }.runTaskTimer(manager.getPlugin(), 0L, 1L);

        effectTasks.put(playerUUID, effectTask);
    }

    /**
     *  Spawn particle trail behind the horse
     */
    private void spawnParticleTrail(Location center, Particle particle, int count) {
        Location trailLoc = center.clone().subtract(center.getDirection().multiply(1.5));
        trailLoc.getWorld().spawnParticle(particle, trailLoc, count, 0.3, 0.1, 0.3, 0.02);
    }

    /**
     *  Spawn particle aura around the horse
     */
    private void spawnParticleAura(Location center, Particle particle, int count) {
        center.getWorld().spawnParticle(particle, center.clone().add(0, 1, 0), count, 1.0, 0.5, 1.0, 0.02);
    }

    /**
     *  Spawn particles in a circle around the horse
     */
    private void spawnParticleCircle(Location center, Particle particle, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            Location particleLoc = center.clone().add(x, 0.5, z);
            center.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0.01);
        }
    }

    /**
     *  Spawn particles in a spiral pattern
     */
    private void spawnParticleSpiral(Location center, Particle particle, int count) {
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / 8;
            double radius = 1.5 + (i * 0.1);
            double y = i * 0.1;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            Location particleLoc = center.clone().add(x, y, z);
            center.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0.01);
        }
    }

    /**
     *  Spawn particle burst effect
     */
    private void spawnParticleBurst(Location center, Particle particle, int count) {
        center.getWorld().spawnParticle(particle, center.clone().add(0, 1, 0), count, 1.5, 1.0, 1.5, 0.1);
    }

    /**
     *  Play tier-specific summoning effects
     */
    private void playSummonEffects(Player player, int tier) {
        Location loc = player.getLocation();

        // Play summoning sound
        Sound summonSound = switch (tier) {
            case 1 -> Sound.ENTITY_HORSE_BREATHE;
            case 2 -> Sound.ENTITY_HORSE_GALLOP;
            case 3 -> Sound.BLOCK_SNOW_BREAK;
            case 4 -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            case 5 -> Sound.ENTITY_BLAZE_SHOOT;
            case 6 -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            default -> Sound.ENTITY_HORSE_BREATHE;
        };

        player.getWorld().playSound(loc, summonSound, 1.0f, 1.0f);

        // Summoning particle effects
        switch (tier) {
            case 1 -> spawnParticleBurst(loc, Particle.FIREWORKS_SPARK, 20);
            case 2 -> {
                spawnParticleBurst(loc, Particle.HEART, 15);
                spawnParticleBurst(loc, Particle.VILLAGER_HAPPY, 25);
            }
            case 3 -> {
                spawnParticleBurst(loc, Particle.SNOWFLAKE, 30);
                spawnParticleCircle(loc, Particle.DOLPHIN, 16, 2.0);
            }
            case 4 -> {
                spawnParticleBurst(loc, Particle.ENCHANTMENT_TABLE, 40);
                spawnParticleSpiral(loc, Particle.PORTAL, 20);
            }
            case 5 -> {
                spawnParticleBurst(loc, Particle.FLAME, 50);
                spawnParticleCircle(loc, Particle.LAVA, 20, 3.0);
                spawnParticleBurst(loc, Particle.DRIP_LAVA, 30);
            }
            case 6 -> {
                spawnParticleBurst(loc, Particle.END_ROD, 60);
                spawnParticleBurst(loc, Particle.DRAGON_BREATH, 40);
                spawnParticleCircle(loc, Particle.FLAME, 24, 4.0);
                spawnParticleSpiral(loc, Particle.TOTEM, 30);

                // Extra epic effect
                new BukkitRunnable() {
                    int count = 0;

                    @Override
                    public void run() {
                        if (count >= 10) {
                            cancel();
                            return;
                        }
                        spawnParticleBurst(loc, Particle.FIREWORKS_SPARK, 20);
                        count++;
                    }
                }.runTaskTimer(manager.getPlugin(), 0L, 3L);
            }
        }
    }

    /**
     *  Clean up visual effects for a player
     */
    private void cleanupEffects(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Cancel effect task
        BukkitTask effectTask = effectTasks.remove(playerUUID);
        if (effectTask != null) {
            effectTask.cancel();
        }

        // Remove potion effects (only mount-related ones)
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
    }

    /**
     *  Get tier from horse persistent data
     */
    private int getTierFromHorse(Horse horse) {
        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        return horse.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 1);
    }

    /**
     *  Play dismount effects based on tier
     */
    private void playDismountEffects(Location loc, int tier) {
        // Play dismount sound
        Sound dismountSound = switch (tier) {
            case 1 -> Sound.ENTITY_HORSE_DEATH;
            case 2 -> Sound.ENTITY_HORSE_AMBIENT;
            case 3 -> Sound.BLOCK_SNOW_STEP;
            case 4 -> Sound.BLOCK_BEACON_DEACTIVATE;
            case 5 -> Sound.ENTITY_BLAZE_DEATH;
            case 6 -> Sound.ENTITY_ENDER_DRAGON_DEATH;
            default -> Sound.ENTITY_HORSE_DEATH;
        };

        loc.getWorld().playSound(loc, dismountSound, 0.7f, 1.0f);

        // Dismount particle effects (smaller than summon effects)
        switch (tier) {
            case 1 -> spawnParticleBurst(loc, Particle.SMOKE_NORMAL, 10);
            case 2 -> spawnParticleBurst(loc, Particle.VILLAGER_HAPPY, 15);
            case 3 -> spawnParticleBurst(loc, Particle.SNOWFLAKE, 20);
            case 4 -> spawnParticleBurst(loc, Particle.ENCHANTMENT_TABLE, 25);
            case 5 -> spawnParticleBurst(loc, Particle.FLAME, 30);
            case 6 -> {
                spawnParticleBurst(loc, Particle.END_ROD, 35);
                spawnParticleBurst(loc, Particle.DRAGON_BREATH, 20);
            }
        }
    }

    /**
     *  Attempts to remount a player to their horse if they've been dismounted accidentally
     *
     * @param player The player to remount
     * @return True if remounting was successful
     */
    public boolean attemptRemount(Player player) {
        UUID playerUUID = player.getUniqueId();
        Horse horse = activeHorses.get(playerUUID);

        if (horse == null || !horse.isValid()) {
            return false;
        }

        // Check if player is already mounted
        if (player.isInsideVehicle()) {
            return true;
        }

        // Check if horse is close enough to player
        if (horse.getLocation().distance(player.getLocation()) > 10.0) {
            return false;
        }

        // Check if horse already has passengers
        if (!horse.getPassengers().isEmpty()) {
            return false;
        }

        // Attempt to remount
        try {
            return horse.addPassenger(player);
        } catch (Exception e) {
            manager.getPlugin().getLogger().warning("Failed to remount player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     *  Forces a safe dismount and immediately removes the horse
     * This is for emergency situations where the horse is glitching
     *
     * @param player The player
     * @return True if emergency dismount was successful
     */
    public boolean emergencyDismount(Player player) {
        UUID playerUUID = player.getUniqueId();
        Horse horse = activeHorses.get(playerUUID);

        if (horse == null) {
            return false;
        }

        // Clean up visual effects first
        cleanupEffects(player);

        // Force remove passenger
        if (horse.getPassengers().contains(player)) {
            horse.removePassenger(player);
        }

        // Teleport player to a safe location near the horse
        Location safeLoc = horse.getLocation().clone();
        safeLoc.setY(safeLoc.getY() + 1);
        player.teleport(safeLoc);

        // Remove the horse
        horse.remove();
        activeHorses.remove(playerUUID);
        manager.unregisterActiveMount(playerUUID);

        player.sendMessage(ChatColor.YELLOW + "Your mount has been emergency dismissed due to a glitch.");
        return true;
    }

    /**
     *  Check if a horse is in a valid state (not glitched)
     *
     * @param horse The horse to check
     * @return True if the horse is in a valid state
     */
    public boolean isHorseInValidState(Horse horse) {
        if (horse == null || !horse.isValid()) {
            return false;
        }

        // Check if horse is in a solid block (common glitch)
        if (horse.getLocation().getBlock().getType().isSolid()) {
            return false;
        }

        // Check if horse is too far underground
        if (horse.getLocation().getY() < 0) {
            return false;
        }

        // Check if horse is in void
        return !(horse.getLocation().getY() < -64);
    }

    /**
     *  Perform health check on all active horses and fix issues
     * This can be called periodically to maintain horse integrity
     */
    public void performHealthCheck() {
        Iterator<Map.Entry<UUID, Horse>> iterator = activeHorses.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Horse> entry = iterator.next();
            UUID playerUUID = entry.getKey();
            Horse horse = entry.getValue();
            Player player = manager.getPlugin().getServer().getPlayer(playerUUID);

            // Remove invalid horses
            if (!isHorseInValidState(horse)) {
                iterator.remove();
                manager.unregisterActiveMount(playerUUID);

                if (player != null && player.isOnline()) {
                    cleanupEffects(player);
                    player.sendMessage(ChatColor.YELLOW + "Your mount was automatically dismissed due to an error.");
                }
                continue;
            }

            // Check for disconnected players
            if (player == null || !player.isOnline()) {
                horse.remove();
                iterator.remove();
                manager.unregisterActiveMount(playerUUID);
                continue;
            }

            // Check if player should be mounted but isn't
            if (!player.isInsideVehicle() && horse.getPassengers().isEmpty()) {
                // Try to remount if player is close
                if (horse.getLocation().distance(player.getLocation()) <= 5.0) {
                    horse.addPassenger(player);
                }
            }
        }
    }

    /**
     * Create mount items for display (now includes tier 1)
     */
    public ItemStack createMountItem(int tier, boolean inShop) {
        if (tier < 1) { // Allow tier 1
            return new ItemStack(Material.AIR);
        }

        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(tier);
        if (stats == null) {
            return new ItemStack(Material.AIR);
        }

        // Choose material based on tier
        Material material = switch (tier) {
            case 1 -> Material.LEATHER_HORSE_ARMOR;
            case 2 -> Material.IRON_HORSE_ARMOR;
            case 3 -> Material.GOLDEN_HORSE_ARMOR;
            case 4 -> Material.DIAMOND_HORSE_ARMOR;
            case 6 -> Material.SADDLE; // Special for max tier
            default -> Material.SADDLE;
        };

        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null) {
            return itemStack;
        }

        // Set name with tier color
        String tierColor = getTierColor(tier);
        String name = tierColor + stats.getName();
        itemMeta.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        lore.add("§7Speed: §f" + (int) (stats.getSpeed() * 100) + "%");
        lore.add("§7Jump: §f" + (int) (stats.getJump() * 100) + "%");
        lore.add("§7Health: §f" + (20 + (tier * 2)) + "❤");

        // Add requirement for shop items
        if (inShop && tier > 1) {
            lore.add("§c§lREQ: §7Tier " + (tier - 1));
        }

        lore.add("");
        lore.add("§7§o" + stats.getDescription());

        // Add usage info
        if (!inShop) {
            lore.add("");
            lore.add("§eRight-click to summon mount");
        }

        // Add price for shop items
        if (inShop) {
            lore.add("");
            lore.add("§6Price: §f" + stats.getPrice() + " gems");
        }

        itemMeta.setLore(lore);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Store tier in custom data
        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        itemMeta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    /**
     * Gets the tier of a mount item
     */
    public int getMountTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        if (meta.getPersistentDataContainer().has(tierKey, PersistentDataType.INTEGER)) {
            return meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        }

        return 0;
    }

    /**
     *  method to check if a horse is owned by a specific player
     */
    public boolean isHorseOwner(Horse horse, UUID playerUUID) {
        String storedUUID = horse.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return storedUUID != null && storedUUID.equals(playerUUID.toString());
    }

    /**
     * Gets the owner of a horse by its UUID
     */
    public UUID getHorseOwner(UUID horseUUID) {
        for (Map.Entry<UUID, Horse> entry : activeHorses.entrySet()) {
            if (entry.getValue().getUniqueId().equals(horseUUID)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Check if player has active horse
     */
    public boolean hasActiveHorse(Player player) {
        return activeHorses.containsKey(player.getUniqueId());
    }

    /**
     * Get player's active horse
     */
    public Horse getActiveHorse(Player player) {
        return activeHorses.get(player.getUniqueId());
    }

    /**
     *  cleanup for plugin disable with effect cleanup
     */
    public void cleanup() {
        // Cancel all summoning tasks
        for (BukkitTask task : summonTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        summonTasks.clear();
        summonLocations.clear();

        // Cancel all effect tasks and clean up players
        for (Map.Entry<UUID, BukkitTask> entry : effectTasks.entrySet()) {
            BukkitTask task = entry.getValue();
            if (task != null) {
                task.cancel();
            }

            // Clean up player effects
            Player player = manager.getPlugin().getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                cleanupEffects(player);
            }
        }
        effectTasks.clear();

        // Remove all active horses
        for (Horse horse : activeHorses.values()) {
            if (horse != null && horse.isValid()) {
                horse.remove();
            }
        }
        activeHorses.clear();
    }
}