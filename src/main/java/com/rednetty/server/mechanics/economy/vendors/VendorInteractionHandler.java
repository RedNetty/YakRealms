package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.mechanics.economy.vendors.behaviors.*;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Simple vendor interaction handler
 */
public class VendorInteractionHandler implements Listener {
    private final VendorManager vendorManager;

    public VendorInteractionHandler(JavaPlugin plugin) {
        this.vendorManager = VendorManager.getInstance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        Vendor vendor = vendorManager.getVendorByNpcId(event.getNPC().getId());

        if (vendor == null) {
            return; // Not a vendor NPC
        }

        VendorBehavior behavior = getBehavior(vendor.getVendorType());
        if (behavior == null) {
            player.sendMessage(ChatColor.RED + "This vendor type is not configured properly.");
            return;
        }

        behavior.onInteract(player);
    }

    /**
     * Get behavior instance for vendor type - no reflection, just direct instantiation
     */
    private VendorBehavior getBehavior(String vendorType) {
        switch (vendorType.toLowerCase()) {
            case "item":
                return new ItemVendorBehavior();
            case "fisherman":
                return new FishermanBehavior();
            case "book":
                return new BookVendorBehavior();
            case "upgrade":
                return new UpgradeVendorBehavior();
            case "banker":
                return new BankerBehavior();
            case "medic":
                return new MedicBehavior();
            case "gambler":
                return new GamblerBehavior();
            case "mount":
                return new MountVendorBehavior();
            case "shop":
                return new ShopBehavior();
            default:
                return null;
        }
    }
}