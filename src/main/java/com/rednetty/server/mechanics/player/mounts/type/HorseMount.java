package com.rednetty.server.mechanics.player.mounts.type;

import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.mounts.MountConfig;
import com.rednetty.server.mechanics.player.mounts.MountManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
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
 * Handler for horse mounts - to allow Tier 1+ summoning with ROBUST mounting system
 * Players purchase tier access through vendors, then summon through this system
 * with anti-glitch protection, remounting capabilities, and epic visual effects
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
            player.sendMessage(Component.text("Could not load your player data.", NamedTextColor.RED));
            return false;
        }

        int horseTier = yakPlayer.getHorseTier();
        if (horseTier < 1) {
            player.sendMessage(Component.text("You don't have a horse mount.", NamedTextColor.RED));
            player.sendMessage(Component.text("Visit the Mount Stable to purchase tier access!", NamedTextColor.YELLOW));
            return false;
        }

        if (isSummoning(player)) {
            player.sendMessage(Component.text("You are already summoning a mount.", NamedTextColor.RED));
            return false;
        }

        if (manager.hasActiveMount(player.getUniqueId())) {
            player.sendMessage(Component.text("You already have an active mount! Dismiss it first.", NamedTextColor.RED));
            return false;
        }

        boolean inSafeZone = AlignmentMechanics.getInstance().isSafeZone(player.getLocation());

        if (inSafeZone && manager.getConfig().isInstantMountInSafeZone()) {
            spawnHorse(player, horseTier);
            return true;
        }

        int summonTime = yakPlayer.getAlignment().equalsIgnoreCase("CHAOTIC")
                ? manager.getConfig().getChaoticHorseSummonTime()
                : manager.getConfig().getHorseSummonTime();

        summonLocations.put(player.getUniqueId(), player.getLocation());

        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(horseTier);
        NamedTextColor tierColor = getTierColor(horseTier);

        player.sendMessage(Component.text("Summoning ", NamedTextColor.WHITE)
                .append(Component.text(stats.getName(), tierColor))
                .append(Component.text(" ... " + summonTime + "s", NamedTextColor.WHITE)));

        BukkitTask task = new BukkitRunnable() {
            int countdown = summonTime;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    return;
                }

                countdown--;

                if (countdown <= 0) {
                    spawnHorse(player, horseTier);
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    cancel();
                } else {
                    player.sendMessage(Component.text("Summoning ", NamedTextColor.WHITE)
                            .append(Component.text(stats.getName(), tierColor))
                            .append(Component.text(" ... " + countdown + "s", NamedTextColor.WHITE)));
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
        Horse horse = activeHorses.remove(playerUUID);
        if (horse == null || !horse.isValid()) {
            return false;
        }

        cleanupEffects(player);

        if (horse.getPassengers().contains(player)) {
            horse.removePassenger(player);
        }

        int tier = getTierFromHorse(horse);
        playDismountEffects(horse.getLocation(), tier);

        Location dismountLocation = horse.getLocation().clone();
        dismountLocation.setY(dismountLocation.getY() + 0.5);
        player.teleport(dismountLocation);

        horse.remove();
        manager.unregisterActiveMount(playerUUID);

        if (sendMessage) {
            player.sendMessage(Component.text("Your mount has been dismissed.", NamedTextColor.RED));
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

        Component reasonComponent = reason != null ? Component.text(" - " + reason) : Component.empty();
        player.sendMessage(Component.text("Mount Summon - ", NamedTextColor.RED)
                .append(Component.text("CANCELLED", Style.style(TextDecoration.BOLD)))
                .append(reasonComponent));
    }

    public boolean hasMoved(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (!summonLocations.containsKey(playerUUID)) {
            return false;
        }
        Location summonLocation = summonLocations.get(playerUUID);
        Location currentLocation = player.getLocation();
        return summonLocation.distanceSquared(currentLocation) > 2.0;
    }

    private void spawnHorse(Player player, int tier) {
        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(tier);
        if (stats == null) {
            player.sendMessage(Component.text("Error: Could not load Tier " + tier + " mount data.", NamedTextColor.RED));
            return;
        }

        Horse horse = player.getWorld().spawn(player.getLocation(), Horse.class);
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(player);

        setHorseAppearance(horse, tier);

        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(stats.getSpeed());
        horse.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(stats.getJump());
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(50.0 + (tier * 2));
        horse.setHealth(horse.getAttribute(Attribute.MAX_HEALTH).getValue());

        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        addTierArmor(horse, tier, stats);

        NamedTextColor tierColor = getTierColor(tier);
        Component displayName = Component.text(stats.getName(), tierColor);
        horse.customName(displayName);
        horse.setCustomNameVisible(true);

        horse.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        horse.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

        horse.addPassenger(player);

        activeHorses.put(player.getUniqueId(), horse);
        manager.registerActiveMount(player.getUniqueId(), this);

        applyTierEffects(horse, player, tier);

        player.sendMessage(Component.text("🐎 Successfully summoned ", NamedTextColor.GRAY)
                .append(displayName)
                .append(Component.text("! 🐎", NamedTextColor.GRAY)));
    }

    private void setHorseAppearance(Horse horse, int tier) {
        switch (tier) {
            case 1 -> {
                horse.setColor(Horse.Color.BROWN);
                horse.setStyle(Horse.Style.NONE);
            }
            case 2 -> {
                horse.setColor(Horse.Color.CHESTNUT);
                horse.setStyle(Horse.Style.WHITE);
            }
            case 3 -> {
                horse.setColor(Horse.Color.DARK_BROWN);
                horse.setStyle(Horse.Style.WHITEFIELD);
            }
            case 4 -> {
                horse.setColor(Horse.Color.BLACK);
                horse.setStyle(Horse.Style.WHITE_DOTS);
            }
            case 5 -> {
                horse.setColor(Horse.Color.GRAY);
                horse.setStyle(Horse.Style.BLACK_DOTS);
            }
            case 6 -> {
                horse.setColor(Horse.Color.WHITE);
                horse.setStyle(Horse.Style.NONE);
            }
            default -> {
                horse.setColor(Horse.Color.BROWN);
                horse.setStyle(Horse.Style.NONE);
            }
        }
    }

    private void addTierArmor(Horse horse, int tier, MountConfig.HorseStats stats) {
        String armorType = stats.getArmorType();
        if (armorType.equals("NONE")) {
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

    private NamedTextColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> NamedTextColor.WHITE;
            case 2 -> NamedTextColor.GREEN;
            case 3 -> NamedTextColor.AQUA;
            case 4 -> NamedTextColor.LIGHT_PURPLE;
            case 5 -> NamedTextColor.YELLOW;
            case 6 -> NamedTextColor.GOLD;
            default -> NamedTextColor.GRAY;
        };
    }

    private void applyTierEffects(Horse horse, Player player, int tier) {
        startContinuousEffects(horse, player, tier);
    }

    private void startContinuousEffects(Horse horse, Player player, int tier) {
        UUID playerUUID = player.getUniqueId();
        BukkitTask existingTask = effectTasks.get(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }

        BukkitTask effectTask = new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !horse.isValid() || !activeHorses.containsKey(playerUUID) || !player.isInsideVehicle()) {
                    cancel();
                    effectTasks.remove(playerUUID);
                    return;
                }

                tickCount++;
                Location horseLoc = horse.getLocation();

                switch (tier) {
                    case 1 -> {
                        if (tickCount % 5 == 0) spawnParticleTrail(horseLoc, Particle.FIREWORK, 3);
                    }
                    case 2 -> {
                        if (tickCount % 3 == 0) spawnParticleTrail(horseLoc, Particle.HAPPY_VILLAGER, 5);
                    }
                    case 3 -> {
                        if (tickCount % 2 == 0) {
                            spawnParticleTrail(horseLoc, Particle.SNOWFLAKE, 8);
                            spawnParticleAura(horseLoc, Particle.DOLPHIN, 3);
                        }
                        if (tickCount % 40 == 0) horse.getWorld().playSound(horseLoc, Sound.BLOCK_SNOW_BREAK, 0.3f, 1.5f);
                    }
                    case 4 -> {
                        if (tickCount % 2 == 0) {
                            spawnParticleAura(horseLoc, Particle.ENCHANT, 10);
                            spawnParticleTrail(horseLoc, Particle.PORTAL, 6);
                        }
                        if (tickCount % 60 == 0) horse.getWorld().playSound(horseLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.2f);
                    }
                    case 5 -> {
                        if (tickCount % 2 == 0) {
                            spawnParticleTrail(horseLoc, Particle.FLAME, 8);
                            spawnParticleAura(horseLoc, Particle.LAVA, 4);
                        }
                        if (tickCount % 3 == 0) spawnParticleCircle(horseLoc, Particle.DRIPPING_LAVA, 12, 2.0);
                        if (tickCount % 80 == 0) horse.getWorld().playSound(horseLoc, Sound.ENTITY_BLAZE_AMBIENT, 0.3f, 0.8f);
                    }
                    case 6 -> {
                        spawnParticleTrail(horseLoc, Particle.END_ROD, 12);
                        spawnParticleAura(horseLoc, Particle.DRAGON_BREATH, 8);
                        if (tickCount % 2 == 0) {
                            spawnParticleCircle(horseLoc, Particle.FLAME, 16, 3.0);
                            spawnParticleCircle(horseLoc, Particle.ENCHANT, 20, 2.5);
                        }
                        if (tickCount % 3 == 0) spawnParticleSpiral(horseLoc, Particle.FIREWORK, 15);
                        if (tickCount % 100 == 0) {
                            horse.getWorld().playSound(horseLoc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.2f, 1.5f);
                            spawnParticleBurst(horseLoc, Particle.TOTEM_OF_UNDYING, 30);
                        }
                    }
                }

                if (horse.getVelocity().getY() > 0.3) {
                    switch (tier) {
                        case 1, 2 -> spawnParticleBurst(horseLoc, Particle.CLOUD, 8);
                        case 3 -> spawnParticleBurst(horseLoc, Particle.SNOWFLAKE, 15);
                        case 4 -> spawnParticleBurst(horseLoc, Particle.ENCHANT, 20);
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

    private void spawnParticleTrail(Location center, Particle particle, int count) {
        Location trailLoc = center.clone().subtract(center.getDirection().multiply(1.5));
        trailLoc.getWorld().spawnParticle(particle, trailLoc, count, 0.3, 0.1, 0.3, 0.02);
    }

    private void spawnParticleAura(Location center, Particle particle, int count) {
        center.getWorld().spawnParticle(particle, center.clone().add(0, 1, 0), count, 1.0, 0.5, 1.0, 0.02);
    }

    private void spawnParticleCircle(Location center, Particle particle, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            Location particleLoc = center.clone().add(x, 0.5, z);
            center.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0.01);
        }
    }

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

    private void spawnParticleBurst(Location center, Particle particle, int count) {
        center.getWorld().spawnParticle(particle, center.clone().add(0, 1, 0), count, 1.5, 1.0, 1.5, 0.1);
    }

    private void playSummonEffects(Player player, int tier) {
        Location loc = player.getLocation();
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

        switch (tier) {
            case 1 -> spawnParticleBurst(loc, Particle.FIREWORK, 20);
            case 2 -> {
                spawnParticleBurst(loc, Particle.HEART, 15);
                spawnParticleBurst(loc, Particle.HAPPY_VILLAGER, 25);
            }
            case 3 -> {
                spawnParticleBurst(loc, Particle.SNOWFLAKE, 30);
                spawnParticleCircle(loc, Particle.DOLPHIN, 16, 2.0);
            }
            case 4 -> {
                spawnParticleBurst(loc, Particle.ENCHANT, 40);
                spawnParticleSpiral(loc, Particle.PORTAL, 20);
            }
            case 5 -> {
                spawnParticleBurst(loc, Particle.FLAME, 50);
                spawnParticleCircle(loc, Particle.LAVA, 20, 3.0);
                spawnParticleBurst(loc, Particle.DRIPPING_LAVA, 30);
            }
            case 6 -> {
                spawnParticleBurst(loc, Particle.END_ROD, 60);
                spawnParticleBurst(loc, Particle.DRAGON_BREATH, 40);
                spawnParticleCircle(loc, Particle.FLAME, 24, 4.0);
                spawnParticleSpiral(loc, Particle.TOTEM_OF_UNDYING, 30);
                new BukkitRunnable() {
                    int count = 0;
                    @Override
                    public void run() {
                        if (count >= 10) {
                            cancel();
                            return;
                        }
                        spawnParticleBurst(loc, Particle.FIREWORK, 20);
                        count++;
                    }
                }.runTaskTimer(manager.getPlugin(), 0L, 3L);
            }
        }
    }

    private void cleanupEffects(Player player) {
        UUID playerUUID = player.getUniqueId();
        BukkitTask effectTask = effectTasks.remove(playerUUID);
        if (effectTask != null) {
            effectTask.cancel();
        }
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
    }

    private int getTierFromHorse(Horse horse) {
        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        return horse.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 1);
    }

    private void playDismountEffects(Location loc, int tier) {
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

        switch (tier) {
            case 1 -> spawnParticleBurst(loc, Particle.SMOKE, 10);
            case 2 -> spawnParticleBurst(loc, Particle.HAPPY_VILLAGER, 15);
            case 3 -> spawnParticleBurst(loc, Particle.SNOWFLAKE, 20);
            case 4 -> spawnParticleBurst(loc, Particle.ENCHANT, 25);
            case 5 -> spawnParticleBurst(loc, Particle.FLAME, 30);
            case 6 -> {
                spawnParticleBurst(loc, Particle.END_ROD, 35);
                spawnParticleBurst(loc, Particle.DRAGON_BREATH, 20);
            }
        }
    }

    public boolean attemptRemount(Player player) {
        UUID playerUUID = player.getUniqueId();
        Horse horse = activeHorses.get(playerUUID);
        if (horse == null || !horse.isValid()) {
            return false;
        }
        if (player.isInsideVehicle()) {
            return true;
        }
        if (horse.getLocation().distance(player.getLocation()) > 10.0) {
            return false;
        }
        if (!horse.getPassengers().isEmpty()) {
            return false;
        }
        try {
            return horse.addPassenger(player);
        } catch (Exception e) {
            manager.getPlugin().getLogger().warning("Failed to remount player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean emergencyDismount(Player player) {
        UUID playerUUID = player.getUniqueId();
        Horse horse = activeHorses.get(playerUUID);
        if (horse == null) {
            return false;
        }

        cleanupEffects(player);

        if (horse.getPassengers().contains(player)) {
            horse.removePassenger(player);
        }

        Location safeLoc = horse.getLocation().clone();
        safeLoc.setY(safeLoc.getY() + 1);
        player.teleport(safeLoc);

        horse.remove();
        activeHorses.remove(playerUUID);
        manager.unregisterActiveMount(playerUUID);

        player.sendMessage(Component.text("Your mount has been emergency dismissed due to a glitch.", NamedTextColor.YELLOW));
        return true;
    }

    public boolean isHorseInValidState(Horse horse) {
        if (horse == null || !horse.isValid()) {
            return false;
        }
        if (horse.getLocation().getBlock().getType().isSolid()) {
            return false;
        }
        if (horse.getLocation().getY() < 0) {
            return false;
        }
        return !(horse.getLocation().getY() < -64);
    }

    public void performHealthCheck() {
        Iterator<Map.Entry<UUID, Horse>> iterator = activeHorses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Horse> entry = iterator.next();
            UUID playerUUID = entry.getKey();
            Horse horse = entry.getValue();
            Player player = manager.getPlugin().getServer().getPlayer(playerUUID);

            if (!isHorseInValidState(horse)) {
                iterator.remove();
                manager.unregisterActiveMount(playerUUID);
                if (player != null && player.isOnline()) {
                    cleanupEffects(player);
                    player.sendMessage(Component.text("Your mount was automatically dismissed due to an error.", NamedTextColor.YELLOW));
                }
                continue;
            }

            if (player == null || !player.isOnline()) {
                horse.remove();
                iterator.remove();
                manager.unregisterActiveMount(playerUUID);
                continue;
            }

            if (!player.isInsideVehicle() && horse.getPassengers().isEmpty()) {
                if (horse.getLocation().distance(player.getLocation()) <= 5.0) {
                    horse.addPassenger(player);
                }
            }
        }
    }

    public ItemStack createMountItem(int tier, boolean inShop) {
        if (tier < 1) {
            return new ItemStack(Material.AIR);
        }
        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(tier);
        if (stats == null) {
            return new ItemStack(Material.AIR);
        }

        Material material = switch (tier) {
            case 1 -> Material.LEATHER_HORSE_ARMOR;
            case 2 -> Material.IRON_HORSE_ARMOR;
            case 3 -> Material.GOLDEN_HORSE_ARMOR;
            case 4 -> Material.DIAMOND_HORSE_ARMOR;
            case 6 -> Material.SADDLE;
            default -> Material.SADDLE;
        };

        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        NamedTextColor tierColor = getTierColor(tier);
        Component name = Component.text(stats.getName(), tierColor);
        itemMeta.displayName(name);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Speed: ", NamedTextColor.GRAY).append(Component.text((int) (stats.getSpeed() * 100) + "%", NamedTextColor.WHITE)));
        lore.add(Component.text("Jump: ", NamedTextColor.GRAY).append(Component.text((int) (stats.getJump() * 100) + "%", NamedTextColor.WHITE)));
        lore.add(Component.text("Health: ", NamedTextColor.GRAY).append(Component.text((20 + (tier * 2)) + "❤", NamedTextColor.WHITE)));

        if (inShop && tier > 1) {
            lore.add(Component.text("REQ: ", NamedTextColor.RED, TextDecoration.BOLD).append(Component.text("Tier " + (tier - 1), NamedTextColor.GRAY)));
        }

        lore.add(Component.empty());
        lore.add(Component.text(stats.getDescription(), NamedTextColor.GRAY, TextDecoration.ITALIC));

        if (!inShop) {
            lore.add(Component.empty());
            lore.add(Component.text("Right-click to summon mount", NamedTextColor.YELLOW));
        }

        if (inShop) {
            lore.add(Component.empty());
            lore.add(Component.text("Price: ", NamedTextColor.GOLD).append(Component.text(stats.getPrice() + " gems", NamedTextColor.WHITE)));
        }

        itemMeta.lore(lore);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        itemMeta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

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

    public boolean isHorseOwner(Horse horse, UUID playerUUID) {
        String storedUUID = horse.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return storedUUID != null && storedUUID.equals(playerUUID.toString());
    }

    public UUID getHorseOwner(UUID horseUUID) {
        for (Map.Entry<UUID, Horse> entry : activeHorses.entrySet()) {
            if (entry.getValue().getUniqueId().equals(horseUUID)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean hasActiveHorse(Player player) {
        return activeHorses.containsKey(player.getUniqueId());
    }

    public Horse getActiveHorse(Player player) {
        return activeHorses.get(player.getUniqueId());
    }

    public void cleanup() {
        for (BukkitTask task : summonTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        summonTasks.clear();
        summonLocations.clear();

        for (Map.Entry<UUID, BukkitTask> entry : effectTasks.entrySet()) {
            BukkitTask task = entry.getValue();
            if (task != null) {
                task.cancel();
            }
            Player player = manager.getPlugin().getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                cleanupEffects(player);
            }
        }
        effectTasks.clear();

        for (Horse horse : activeHorses.values()) {
            if (horse != null && horse.isValid()) {
                horse.remove();
            }
        }
        activeHorses.clear();
    }
}