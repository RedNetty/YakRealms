package com.rednetty.server.mechanics.economy.vendors.visuals;

import com.rednetty.server.mechanics.economy.vendors.Vendor;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import com.rednetty.server.mechanics.economy.vendors.visuals.animations.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Modern vendor aura manager using Minecraft 1.20.2+ Display Entities
 * Creates immersive, themed environments around vendors
 */
public class VendorAuraManager {
    private final JavaPlugin plugin;
    private final VendorManager vendorManager;
    private final NamespacedKey vendorEntityKey;
    private BukkitTask mainAuraTask;

    // Maps to track vendor entities and animations
    private final Map<String, Set<Entity>> vendorEntities = new ConcurrentHashMap<>();
    private final Map<String, VendorAnimation> activeAnimations = new ConcurrentHashMap<>();
    private final Set<String> pendingRefresh = ConcurrentHashMap.newKeySet();

    // Configuration
    private int renderDistance = 48;
    private boolean enableParticles = true;
    private boolean enableDisplays = true;
    private boolean enableSounds = true;
    private int effectDensity = 2; // 1=low, 2=medium, 3=high

    /**
     * Creates a new vendor aura manager
     */
    public VendorAuraManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance(plugin);
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
        loadConfiguration();
        plugin.getLogger().info("VendorAuraManager initialized with 1.20.2+ features");
    }

    /**
     * Loads configuration values
     */
    private void loadConfiguration() {
        renderDistance = plugin.getConfig().getInt("vendors.aura-render-distance", 48);
        enableParticles = plugin.getConfig().getBoolean("vendors.enable-particle-effects", true);
        enableDisplays = plugin.getConfig().getBoolean("vendors.enable-display-entities", true);
        enableSounds = plugin.getConfig().getBoolean("vendors.enable-sound-effects", true);
        effectDensity = plugin.getConfig().getInt("vendors.effect-density", 2);

        // Validate effect density (1-3)
        if (effectDensity < 1 || effectDensity > 3) {
            effectDensity = 2;
        }
    }

    /**
     * Starts all vendor auras
     */
    public void startAllAuras() {
        stopAllAuras(); // Prevent duplicates

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (mainAuraTask != null) return;

            plugin.getLogger().info("Starting aura effects for " + vendorManager.getVendors().size() + " vendors");

            mainAuraTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllAuras, 20, 1);
        }, 40); // 2-second delay after server start
    }

    /**
     * Main update method that processes all vendor auras
     */
    private void updateAllAuras() {
        try {
            // Process any pending display refreshes
            if (!pendingRefresh.isEmpty()) {
                processDisplayRefreshes();
            }

            // Process each vendor
            for (Vendor vendor : vendorManager.getVendors().values()) {
                String vendorId = vendor.getVendorId();
                Location loc = getVendorLocation(vendor);

                if (loc == null) continue;

                // Skip if no players nearby
                if (!hasPlayersNearby(loc, renderDistance)) {
                    if (vendorEntities.containsKey(vendorId)) {
                        removeVendorDisplays(vendorId);
                    }
                    continue;
                }

                // Setup or update vendor animation
                setupOrUpdateVendorAnimation(vendor, loc);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error in vendor aura system: " + e.getMessage(), e);
        }
    }

    /**
     * Sets up or updates a vendor's animation
     */
    private void setupOrUpdateVendorAnimation(Vendor vendor, Location loc) {
        String vendorId = vendor.getVendorId();
        String vendorType = vendor.getVendorType().toLowerCase();

        // Create animation if needed
        if (!activeAnimations.containsKey(vendorId)) {
            VendorAnimation animation = createAnimationForType(vendorType, vendor, loc);
            if (animation != null) {
                activeAnimations.put(vendorId, animation);

                // Create initial displays
                if (enableDisplays) {
                    Set<Entity> entities = animation.createDisplayEntities(loc, vendorEntityKey);
                    if (!entities.isEmpty()) {
                        vendorEntities.put(vendorId, entities);
                    }
                }
            }
        }

        // Update existing animation
        VendorAnimation animation = activeAnimations.get(vendorId);
        if (animation != null) {
            // Update display animations
            if (enableDisplays && vendorEntities.containsKey(vendorId)) {
                animation.updateDisplayAnimations(vendorEntities.get(vendorId), loc);
            }

            // Apply particle effects based on density setting
            if (enableParticles && animation.shouldApplyParticles(effectDensity)) {
                animation.applyParticleEffects(loc);
            }

            // Play ambient sounds occasionally
            if (enableSounds && animation.shouldPlaySound()) {
                animation.playAmbientSound(loc);
            }

            // Process special effects
            animation.processSpecialEffects(loc);
        }
    }

    /**
     * Creates the appropriate animation for a vendor type
     */
    private VendorAnimation createAnimationForType(String vendorType, Vendor vendor, Location loc) {
        AnimationOptions options = new AnimationOptions(
                plugin,
                vendor.getVendorId(),
                enableParticles,
                enableSounds,
                effectDensity
        );

        switch (vendorType) {
            case "gambler":
                return new GamblerAnimation(options);
            case "fisherman":
                return new FishermanAnimation(options);
            case "book":
                return new BookVendorAnimation(options);
            case "banker":
                return new BankerAnimation(options);
            case "upgrade":
                return new UpgradeVendorAnimation(options);
            case "medic":
                return new MedicAnimation(options);
            case "item":
            case "shop":
                return new ItemVendorAnimation(options);
            default:
                return new DefaultVendorAnimation(options);
        }
    }

    /**
     * Process any pending display refreshes
     */
    private void processDisplayRefreshes() {
        Set<String> toProcess = new HashSet<>(pendingRefresh);
        pendingRefresh.clear();

        for (String vendorId : toProcess) {
            Vendor vendor = vendorManager.getVendor(vendorId);
            if (vendor != null) {
                // Remove existing displays
                removeVendorDisplays(vendorId);

                // Stop and cleanup the animation
                VendorAnimation animation = activeAnimations.remove(vendorId);
                if (animation != null) {
                    animation.cleanup();
                }

                // Location check
                Location loc = getVendorLocation(vendor);
                if (loc != null) {
                    // Will be recreated on next tick
                    setupOrUpdateVendorAnimation(vendor, loc);
                }
            }
        }
    }

    /**
     * Stops all vendor auras and cleans up resources
     */
    public void stopAllAuras() {
        // Cancel the main task
        if (mainAuraTask != null) {
            mainAuraTask.cancel();
            mainAuraTask = null;
        }

        // Clean up animations
        for (VendorAnimation animation : activeAnimations.values()) {
            try {
                animation.cleanup();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error cleaning up animation", e);
            }
        }
        activeAnimations.clear();

        // Remove all display entities
        removeAllDisplayEntities();

        plugin.getLogger().info("Stopped all vendor aura effects");
    }

    /**
     * Updates a specific vendor's aura
     */
    public void updateVendorAura(Vendor vendor) {
        pendingRefresh.add(vendor.getVendorId());
    }

    /**
     * Removes all display entities
     */
    private void removeAllDisplayEntities() {
        for (String vendorId : new ArrayList<>(vendorEntities.keySet())) {
            removeVendorDisplays(vendorId);
        }
        vendorEntities.clear();
    }

    /**
     * Removes display entities for a specific vendor
     */
    private void removeVendorDisplays(String vendorId) {
        Set<Entity> displays = vendorEntities.remove(vendorId);
        if (displays != null) {
            for (Entity entity : new HashSet<>(displays)) {
                try {
                    entity.remove();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error removing entity for vendor " + vendorId, e);
                }
            }
        }
    }

    /**
     * Gets the current location of a vendor
     */
    private Location getVendorLocation(Vendor vendor) {
        try {
            // First try to get location from NPC if spawned
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
            if (npc != null && npc.isSpawned()) {
                return npc.getEntity().getLocation();
            }
        } catch (Exception ignored) {}

        // Fall back to stored location
        Location loc = vendor.getLocation();
        return (loc != null && loc.getWorld() != null && isValidLocation(loc)) ? loc : null;
    }

    /**
     * Check if there are players within a specified distance
     */
    private boolean hasPlayersNearby(Location loc, double distance) {
        if (loc == null || loc.getWorld() == null) return false;

        double distanceSquared = distance * distance;
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= distanceSquared) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a location is valid (not extreme coordinates)
     */
    private boolean isValidLocation(Location loc) {
        return !(Math.abs(loc.getY()) > 1000 ||
                Math.abs(loc.getX()) > 10000 ||
                Math.abs(loc.getZ()) > 10000);
    }

    /**
     * Get statistics about aura performance
     */
    public Map<String, Object> getAuraStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("activeVendorAuras", activeAnimations.size());
        stats.put("totalDisplayEntities", countDisplayEntities());
        stats.put("renderDistance", renderDistance);
        stats.put("particlesEnabled", enableParticles);
        stats.put("displayEntitiesEnabled", enableDisplays);
        stats.put("soundsEnabled", enableSounds);
        stats.put("effectDensity", effectDensity);

        return stats;
    }

    /**
     * Count total display entities
     */
    private int countDisplayEntities() {
        return vendorEntities.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    // Configuration setters

    public void setEffectDensity(int level) {
        if (level >= 1 && level <= 3) {
            this.effectDensity = level;
            plugin.getConfig().set("vendors.effect-density", level);
            plugin.saveConfig();
        }
    }

    public void setParticlesEnabled(boolean enabled) {
        this.enableParticles = enabled;
        plugin.getConfig().set("vendors.enable-particle-effects", enabled);
        plugin.saveConfig();
    }

    public void setDisplayEntitiesEnabled(boolean enabled) {
        this.enableDisplays = enabled;
        plugin.getConfig().set("vendors.enable-display-entities", enabled);
        plugin.saveConfig();

        if (!enabled) {
            removeAllDisplayEntities();
        } else {
            // Will be recreated on next tick
            pendingRefresh.addAll(activeAnimations.keySet());
        }
    }

    public void setRenderDistance(int distance) {
        if (distance > 0 && distance <= 128) {
            this.renderDistance = distance;
            plugin.getConfig().set("vendors.aura-render-distance", distance);
            plugin.saveConfig();
        }
    }

    public void setSoundsEnabled(boolean enabled) {
        this.enableSounds = enabled;
        plugin.getConfig().set("vendors.enable-sound-effects", enabled);
        plugin.saveConfig();
    }
}