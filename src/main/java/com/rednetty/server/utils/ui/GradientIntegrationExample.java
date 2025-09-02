package com.rednetty.server.utils.ui;

import com.rednetty.server.core.mechanics.item.drops.types.RarityConfig;
import com.rednetty.server.core.mechanics.item.drops.types.TierConfig;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

/**
 * Integration Example for Gradient Colors
 * 
 * This class shows how to integrate the new gradient color system
 * into existing code throughout the YakRealms plugin.
 */
public class GradientIntegrationExample {
    
    // ==================== ITEM CREATION INTEGRATION ====================
    
    /**
     * Example: How to create an item with gradient colors
     * This would replace existing item creation methods
     */
    public static ItemStack createPremiumItem(String itemName, int tier, int rarity, ItemStack baseItem) {
        ItemMeta meta = baseItem.getItemMeta();
        if (meta == null) return baseItem;
        
        // Use the new gradient system for item naming
        String displayName = ItemDisplayFormatter.formatCompleteItemName(itemName, tier, rarity);
        meta.setDisplayName(displayName);
        
        // Apply gradient formatting to the entire item
        baseItem.setItemMeta(meta);
        ItemDisplayFormatter.formatItemStack(baseItem, tier, rarity);
        
        return baseItem;
    }
    
    // ==================== LOOT NOTIFICATION INTEGRATION ====================
    
    /**
     * Example: How to integrate gradient colors into loot notifications
     * This would enhance existing drop notification systems
     */
    public static void notifyPremiumDrop(Player player, ItemStack item, int tier, int rarity, String mobName) {
        // Check if this is a premium item that deserves special treatment
        if (ItemDisplayFormatter.isPremiumItem(tier, rarity)) {
            
            // Create enhanced notification for premium items
            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                item.getItemMeta().getDisplayName() : item.getType().name();
                
            // Format the notification with gradients
            String notification;
            if (tier == 6 && rarity == 4) {
                // Ultra premium T6 Unique item
                notification = "§6§l✦ §e§lULTRA RARE DROP! §6§l✦\n" +
                    ItemDisplayFormatter.formatCompleteItemName(itemName, tier, rarity) + 
                    "\n§7from " + mobName;
            } else if (tier == 6) {
                // T6 Legendary item
                notification = "§6§l★ §e§lLEGENDARY DROP! §6§l★\n" +
                    ItemDisplayFormatter.formatTierItemName(itemName, tier) + 
                    "\n§7from " + mobName;
            } else if (rarity == 4) {
                // Unique item
                notification = "§e§l◆ §6§lUNIQUE DROP! §e§l◆\n" +
                    ItemDisplayFormatter.formatRarityItemName(itemName, rarity) + 
                    "\n§7from " + mobName;
            } else {
                // Fallback to standard notification
                notification = "§a§lRare Drop!\n§f" + itemName + "\n§7from " + mobName;
            }
            
            // Send with appropriate effects
            player.sendMessage(notification);
            
            // Play special sound for premium items
            if (tier == 6 || rarity == 4) {
                player.playSound(player.getLocation(), 
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            }
        }
    }
    
    // ==================== CHAT MESSAGE INTEGRATION ====================
    
    /**
     * Example: How to integrate gradient colors into chat messages
     * This would enhance existing chat systems
     */
    public static void announceGlobalDrop(Player player, ItemStack item, int tier, int rarity, String mobName) {
        if (!ItemDisplayFormatter.isPremiumItem(tier, rarity)) {
            return; // Only announce premium items globally
        }
        
        String playerName = player.getName();
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
            item.getItemMeta().getDisplayName() : item.getType().name();
            
        String announcement;
        
        if (tier == 6 && rarity == 4) {
            // T6 Unique - Server-wide celebration
            announcement = "§6§l✦ §e§lSERVER MILESTONE §6§l✦\n" +
                "§f" + playerName + " §7found " + 
                ItemDisplayFormatter.formatCompleteItemName(itemName, tier, rarity) + 
                " §7from §c" + mobName + "§7!";
        } else if (tier == 6) {
            // T6 Legendary - Important announcement
            announcement = "§6§l★ §e§lLEGENDARY DISCOVERY §6§l★\n" +
                "§f" + playerName + " §7discovered " + 
                ItemDisplayFormatter.formatTierItemName(itemName, tier) + 
                " §7from §c" + mobName + "§7!";
        } else if (rarity == 4) {
            // Unique - Notable announcement
            announcement = "§e§l◆ §6§lUNIQUE FIND §e§l◆\n" +
                "§f" + playerName + " §7found " + 
                ItemDisplayFormatter.formatRarityItemName(itemName, rarity) + 
                " §7from §c" + mobName + "§7!";
        } else {
            return; // Don't announce non-premium items
        }
        
        // Broadcast to all online players
        for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(announcement);
        }
    }
    
    // ==================== GUI/MENU INTEGRATION ====================
    
    /**
     * Example: How to integrate gradient colors into GUI item displays
     * This would enhance existing menu systems
     */
    public static ItemStack createMenuDisplayItem(ItemStack originalItem, int tier, int rarity) {
        ItemStack displayItem = originalItem.clone();
        
        // Apply gradient formatting for premium items
        ItemDisplayFormatter.formatItemStack(displayItem, tier, rarity);
        
        return displayItem;
    }
    
    // ==================== SCOREBOARD/TAB INTEGRATION ====================
    
    /**
     * Example: How to show premium items in scoreboards/tab lists
     */
    public static String formatScoreboardItem(String itemName, int tier, int rarity) {
        if (tier == 6) {
            return ItemDisplayFormatter.formatTierDisplay(tier, true) + " " + 
                   GradientColors.getT6Gradient(itemName);
        } else if (rarity == 4) {
            return ItemDisplayFormatter.formatRaritySymbol(rarity) + " " + 
                   GradientColors.getUniqueGradient(itemName);
        }
        
        // Standard formatting for non-premium items
        TierConfig tierConfig = TierConfig.createDefault(tier);
        return tierConfig.getTierColorCode() + itemName;
    }
    
    // ==================== ACTION BAR INTEGRATION ====================
    
    /**
     * Example: How to show gradient colors in action bar messages
     */
    public static void showActionBarItemPickup(Player player, ItemStack item, int tier, int rarity) {
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
            item.getItemMeta().getDisplayName() : item.getType().name();
            
        String message;
        
        if (tier == 6) {
            message = "§6§l+ " + GradientColors.getT6Gradient(itemName);
        } else if (rarity == 4) {
            message = "§e§l+ " + GradientColors.getUniqueGradient(itemName);
        } else {
            TierConfig tierConfig = TierConfig.createDefault(tier);
            message = tierConfig.getTierColorCode() + "+ " + itemName;
        }
        
        ActionBarUtil.addTemporaryMessage(player, message, 60L);
    }
    
    // ==================== HOLOGRAM INTEGRATION ====================
    
    /**
     * Example: How to create gradient holograms for premium items
     */
    public static String[] createHologramLines(ItemStack item, int tier, int rarity) {
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
            item.getItemMeta().getDisplayName() : item.getType().name();
            
        if (tier == 6 && rarity == 4) {
            // Ultra premium hologram
            return new String[] {
                "§6§l✦ §e§lULTRA RARE §6§l✦",
                ItemDisplayFormatter.formatCompleteItemName(itemName, tier, rarity),
                "§7§o\"A truly legendary artifact\""
            };
        } else if (tier == 6) {
            // T6 hologram
            return new String[] {
                "§6§l★ §e§lLEGENDARY §6§l★",
                GradientColors.getT6Gradient(itemName),
                "§7§o\"Forged by ancient masters\""
            };
        } else if (rarity == 4) {
            // Unique hologram
            return new String[] {
                "§e§l◆ §6§lUNIQUE §e§l◆",
                GradientColors.getUniqueGradient(itemName),
                "§7§o\"One of a kind\""
            };
        }
        
        // Standard hologram
        TierConfig tierConfig = TierConfig.createDefault(tier);
        return new String[] {
            tierConfig.getTierColorCode() + itemName,
            "§7Tier " + tier
        };
    }
    
    // ==================== PARTICLE EFFECT INTEGRATION ====================
    
    /**
     * Example: How to create appropriate particle effects for premium items
     */
    public static void createDropParticles(org.bukkit.Location location, int tier, int rarity) {
        if (tier == 6 && rarity == 4) {
            // Ultra premium - mixed particles
            location.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, 
                location.add(0, 0.5, 0), 30, 0.5, 0.5, 0.5, 0.1);
            location.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, 
                location, 10, 0.3, 0.3, 0.3, 0.02);
        } else if (tier == 6) {
            // T6 - golden particles
            location.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, 
                location.add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.1);
        } else if (rarity == 4) {
            // Unique - special particles
            location.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, 
                location.add(0, 0.5, 0), 15, 0.4, 0.4, 0.4, 0.05);
        }
    }
    
    // ==================== USAGE EXAMPLES ====================
    
    /**
     * Example of how existing code would be modified to use gradients
     */
    public static void exampleUsage() {
        // Instead of this old way:
        // String itemDisplay = ChatColor.GOLD + "Legendary Sword";
        
        // Use this new way:
        String itemDisplay = GradientColors.getT6Gradient("Legendary Sword");
        
        // For dynamic tier/rarity:
        int tier = 6;
        int rarity = 4;
        String dynamicDisplay = ItemDisplayFormatter.formatCompleteItemName("Epic Blade", tier, rarity);
        
        // For simple tier-based coloring:
        String tierDisplay = ItemDisplayFormatter.formatTierItemName("Magic Sword", 6);
        
        // For simple rarity-based coloring:
        String rarityDisplay = ItemDisplayFormatter.formatRarityItemName("Mystic Gem", 4);
    }
}