package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.behaviors.VendorBehavior;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles player interactions with vendors
 */
public class VendorInteractionHandler implements Listener {

    private final VendorManager vendorManager;
    private final JavaPlugin plugin;
    private final Map<UUID, Long> interactionCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MILLIS = 500; // 500ms cooldown between interactions

    /**
     * Constructor
     *
     * @param plugin The main plugin instance
     */
    public VendorInteractionHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Vendor interaction handler registered");
    }

    /**
     * Handle player interactions with entities
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVendorInteract(PlayerInteractEntityEvent event) {
        if (YakRealms.isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // Check if the entity is an NPC
        if (!isNPC(entity)) {
            return;
        }

        // Check for cooldown to prevent double-clicks
        if (isOnCooldown(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Add to cooldown
        addCooldown(player.getUniqueId());

        // Get the NPC ID
        int npcId = getNpcId(entity);
        if (npcId == -1) {
            return;
        }

        // Find vendor by NPC ID
        Vendor vendor = vendorManager.getVendorByNpcId(npcId);
        if (vendor == null) {
            return;
        }

        // Cancel the event to prevent default interaction
        event.setCancelled(true);

        // Get the behavior class
        String behaviorClassName = vendor.getBehaviorClass();
        VendorBehavior behavior = null;

        try {
            Class<?> behaviorClass = Class.forName(behaviorClassName);
            if (VendorBehavior.class.isAssignableFrom(behaviorClass)) {
                behavior = (VendorBehavior) behaviorClass.getDeclaredConstructor().newInstance();
            }
        } catch (ClassNotFoundException e) {
            // Log the original behavior class for debugging
            YakRealms.error("Vendor behavior class not found: " + behaviorClassName, e);

            // Try to fallback to the appropriate behavior based on vendor type
            String vendorType = vendor.getVendorType();
            String fallbackBehavior = getFallbackBehaviorForType(vendorType);

            try {
                Class<?> fallbackClass = Class.forName(fallbackBehavior);
                behavior = (VendorBehavior) fallbackClass.getDeclaredConstructor().newInstance();

                // Update the vendor's behavior class for future interactions
                vendor.setBehaviorClass(fallbackBehavior);

                // Save the change to config
                VendorManager.getInstance(plugin).saveVendorsToConfig();

                player.sendMessage(ChatColor.YELLOW + "This vendor had an issue but has been fixed. Please interact again.");
                return;
            } catch (Exception ex) {
                YakRealms.error("Failed to create fallback behavior: " + fallbackBehavior, ex);
                player.sendMessage(ChatColor.RED + "This vendor is not configured correctly. Please contact an administrator.");
                return;
            }
        } catch (Exception e) {
            YakRealms.error("Failed to instantiate vendor behavior: " + behaviorClassName, e);
            player.sendMessage(ChatColor.RED + "This vendor is not configured correctly. Please contact an administrator.");
            return;
        }

        // Execute the behavior
        if (behavior != null) {
            behavior.onInteract(player);
        }
    }

    /**
     * Gets a fallback behavior class for a vendor type
     *
     * @param vendorType The vendor type
     * @return The fallback behavior class name
     */
    private String getFallbackBehaviorForType(String vendorType) {
        String basePath = "com.rednetty.server.mechanics.economy.vendors.behaviors.";

        switch (vendorType.toLowerCase()) {
            case "item":
                return basePath + "ItemVendorBehavior";
            case "fisherman":
                return basePath + "FishermanBehavior";
            case "book":
                return basePath + "BookVendorBehavior";
            case "upgrade":
                return basePath + "UpgradeVendorBehavior";
            case "banker":
                return basePath + "BankerBehavior";
            case "medic":
                return basePath + "MedicBehavior";
            case "gambler":
                return basePath + "GamblerBehavior";
            default:
                return basePath + "ShopBehavior";
        }
    }

    /**
     * Check if an entity is an NPC
     *
     * @param entity The entity to check
     * @return true if the entity is an NPC
     */
    private boolean isNPC(Entity entity) {
        return entity.hasMetadata("NPC");
    }

    /**
     * Get the NPC ID from an entity
     *
     * @param entity The entity
     * @return The NPC ID, or -1 if not found
     */
    private int getNpcId(Entity entity) {
        if (!entity.hasMetadata("NPC")) {
            return -1;
        }

        for (MetadataValue meta : entity.getMetadata("NPC")) {
            if (meta.getOwningPlugin() != null && "Citizens".equals(meta.getOwningPlugin().getName())) {
                try {
                    // Try to get the NPC ID
                    return CitizensUtil.getNpcId(entity);
                } catch (Exception e) {
                    YakRealms.error("Failed to get NPC ID from entity", e);
                    return -1;
                }
            }
        }

        return -1;
    }

    /**
     * Check if a player is on interaction cooldown
     *
     * @param uuid The player's UUID
     * @return true if the player is on cooldown
     */
    private boolean isOnCooldown(UUID uuid) {
        if (!interactionCooldowns.containsKey(uuid)) {
            return false;
        }

        long lastInteraction = interactionCooldowns.get(uuid);
        return System.currentTimeMillis() - lastInteraction < COOLDOWN_MILLIS;
    }

    /**
     * Add a player to the interaction cooldown
     *
     * @param uuid The player's UUID
     */
    private void addCooldown(UUID uuid) {
        interactionCooldowns.put(uuid, System.currentTimeMillis());
    }

    /**
     * Utility class for Citizens integration
     */
    private static class CitizensUtil {
        /**
         * Get the NPC ID from an entity
         *
         * @param entity The entity
         * @return The NPC ID
         */
        public static int getNpcId(Entity entity) {
            try {
                // Use Citizens API directly
                net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entity);
                if (npc != null) {
                    return npc.getId();
                }

                // Fallback to metadata approach
                for (MetadataValue meta : entity.getMetadata("NPC")) {
                    if (meta.getOwningPlugin() != null && "Citizens".equals(meta.getOwningPlugin().getName())) {
                        // Some Citizens implementations store the ID directly, others need more work
                        if (meta.value() instanceof Integer) {
                            return (Integer) meta.value();
                        } else {
                            String value = meta.asString();
                            try {
                                return Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                // Not a simple integer, need more extraction
                            }
                        }
                    }
                }
            } catch (Exception e) {
                YakRealms.error("Error getting NPC ID", e);
            }

            return -1;
        }
    }
}