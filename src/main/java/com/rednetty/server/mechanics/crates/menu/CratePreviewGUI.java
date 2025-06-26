package com.rednetty.server.mechanics.crates.menu;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.CrateManager;
import com.rednetty.server.mechanics.crates.CrateFactory;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.mechanics.drops.DropFactory;
import com.rednetty.server.mechanics.drops.DropsManager;
import com.rednetty.server.mechanics.economy.MoneyManager;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GUI for previewing crate contents and rewards
 */
public class CratePreviewGUI implements Listener {
    private final YakRealms plugin;
    private final CrateManager crateManager;
    private final CrateFactory crateFactory;
    private final DropsManager dropsManager;
    private final DropFactory dropFactory;
    private final OrbManager orbManager;
    private final ScrollManager scrollManager;

    // Track open GUIs
    private final Set<UUID> openGUIs = new HashSet<>();

    // GUI constants
    private static final String MAIN_TITLE = "✦ Crate Preview ✦";
    private static final String CRATE_TITLE = "✦ %s Contents ✦";
    private static final int MAIN_SIZE = 54; // 6 rows
    private static final int PREVIEW_SIZE = 54; // 6 rows

    /**
     * Constructor
     */
    public CratePreviewGUI() {
        this.plugin = YakRealms.getInstance();
        this.crateManager = CrateManager.getInstance();
        this.crateFactory = crateManager.getCrateFactory();
        this.dropsManager = DropsManager.getInstance();
        this.dropFactory = DropFactory.getInstance();
        this.orbManager = OrbManager.getInstance();
        this.scrollManager = ScrollManager.getInstance();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the main crate selection GUI
     *
     * @param player The player to show the GUI to
     */
    public void openMainGUI(Player player) {
        Inventory inventory = Bukkit.createInventory(null, MAIN_SIZE, MAIN_TITLE);

        // Add crate types for selection
        populateMainGUI(inventory);

        player.openInventory(inventory);
        openGUIs.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    /**
     * Populates the main GUI with crate selection
     */
    private void populateMainGUI(Inventory inventory) {
        // Fill background with glass panes
        fillBackground(inventory, Material.GRAY_STAINED_GLASS_PANE);

        // Add regular crate types
        int slot = 10; // Start position
        for (CrateType crateType : CrateType.getRegularTypes()) {
            ItemStack crateItem = crateFactory.createCrate(crateType, false);
            if (crateItem != null) {
                enhanceCrateDisplayItem(crateItem, crateType, false);
                inventory.setItem(slot, crateItem);
                slot += 2; // Skip one slot for spacing

                if (slot >= 17) { // Move to next row
                    slot = 28;
                }
                if (slot >= 35) { // Move to next row
                    slot = 46;
                }
            }
        }

        // Add Halloween variants if enabled (bottom row)
        if (isHalloweenSeason()) {
            slot = 19; // Halloween row
            for (CrateType crateType : CrateType.getHalloweenTypes()) {
                if (slot < 26) {
                    ItemStack crateItem = crateFactory.createCrate(crateType, true);
                    if (crateItem != null) {
                        enhanceCrateDisplayItem(crateItem, crateType, true);
                        inventory.setItem(slot, crateItem);
                        slot += 2;
                    }
                }
            }
        }

        // Add info item
        ItemStack infoItem = createInfoItem();
        inventory.setItem(49, infoItem);
    }

    /**
     * Enhances a crate display item with preview information
     */
    private void enhanceCrateDisplayItem(ItemStack crateItem, CrateType crateType, boolean isHalloween) {
        if (!crateItem.hasItemMeta()) return;

        ItemMeta meta = crateItem.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Add preview information
        lore.add("");
        lore.add(ChatColor.YELLOW + "Tier " + crateType.getTier() + " Rewards:");
        lore.add(ChatColor.WHITE + "• Equipment & Weapons");
        lore.add(ChatColor.WHITE + "• Enhancement Scrolls");
        lore.add(ChatColor.WHITE + "• Orbs of Alteration");
        lore.add(ChatColor.WHITE + "• Gems");

        if (crateType.getTier() >= 4) {
            lore.add(ChatColor.WHITE + "• Protection Scrolls");
        }

        if (crateType.getTier() >= 5) {
            lore.add(ChatColor.WHITE + "• Legendary Items");
        }

        if (isHalloween) {
            lore.add(ChatColor.GOLD + "• Halloween Bonuses");
        }

        lore.add("");
        lore.add(ChatColor.GREEN + "Click to preview contents!");

        meta.setLore(lore);
        crateItem.setItemMeta(meta);
    }

    /**
     * Creates an info item for the main GUI
     */
    private ItemStack createInfoItem() {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "ℹ Crate Information");
        List<String> lore = Arrays.asList(
                "",
                ChatColor.GRAY + "Crates contain randomized rewards",
                ChatColor.GRAY + "based on their tier level.",
                "",
                ChatColor.YELLOW + "Higher tiers = Better rewards!",
                "",
                ChatColor.WHITE + "Tiers:",
                ChatColor.WHITE + "• Tier 1-2: Basic equipment",
                ChatColor.AQUA + "• Tier 3-4: Advanced gear",
                ChatColor.LIGHT_PURPLE + "• Tier 5-6: Legendary items",
                "",
                ChatColor.GREEN + "Click any crate to preview!"
        );
        meta.setLore(lore);
        info.setItemMeta(meta);

        return info;
    }

    /**
     * Opens the preview GUI for a specific crate type
     */
    public void openPreviewGUI(Player player, CrateType crateType) {
        String title = String.format(CRATE_TITLE,
                (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName());
        Inventory inventory = Bukkit.createInventory(null, PREVIEW_SIZE, title);

        // Populate with example rewards
        populatePreviewGUI(inventory, crateType);

        player.openInventory(inventory);
        openGUIs.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.2f);
    }

    /**
     * Populates the preview GUI with example rewards
     */
    private void populatePreviewGUI(Inventory inventory, CrateType crateType) {
        fillBackground(inventory, Material.BLACK_STAINED_GLASS_PANE);

        int tier = crateType.getTier();
        boolean isHalloween = crateType.isHalloween();

        // Show example equipment (top row)
        addExampleEquipment(inventory, tier, 10, 16);

        // Show scrolls (second row)
        addExampleScrolls(inventory, tier, 19, 25);

        // Show orbs and gems (third row)
        addExampleOrbs(inventory, tier, 28, 31);
        addExampleGems(inventory, tier, 33, 34);

        // Show special items for higher tiers
        if (tier >= 4) {
            addProtectionScrolls(inventory, tier, 37, 39);
        }

        if (tier >= 5) {
            addLegendaryItems(inventory, tier, 42, 44);
        }

        // Halloween bonuses
        if (isHalloween) {
            addHalloweenBonuses(inventory, tier, 46, 48);
        }

        // Add back button
        ItemStack backButton = createBackButton();
        inventory.setItem(53, backButton);

        // Add crate info
        ItemStack crateInfo = createCrateInfoItem(crateType);
        inventory.setItem(49, crateInfo);
    }

    /**
     * Adds example equipment to the preview
     */
    private void addExampleEquipment(Inventory inventory, int tier, int startSlot, int endSlot) {
        int slot = startSlot;
        for (int itemType = 1; itemType <= 8 && slot <= endSlot; itemType++) {
            try {
                // Create example items with different rarities
                int rarity = (itemType <= 4) ? 1 : Math.min(itemType - 2, 4);
                ItemStack item = dropsManager.createDrop(tier, itemType, rarity);
                if (item != null) {
                    addPreviewLabel(item, "Example " + getItemTypeName(itemType));
                    inventory.setItem(slot, item);
                    slot++;
                }
            } catch (Exception e) {
                // Skip if creation fails
            }
        }
    }

    /**
     * Adds example scrolls to the preview
     */
    private void addExampleScrolls(Inventory inventory, int tier, int startSlot, int endSlot) {
        int slot = startSlot;

        // Weapon enhancement scroll
        if (slot <= endSlot) {
            ItemStack weaponScroll = scrollManager.createWeaponEnhancementScroll(tier);
            if (weaponScroll != null) {
                addPreviewLabel(weaponScroll, "Enhancement Scroll");
                inventory.setItem(slot++, weaponScroll);
            }
        }

        // Armor enhancement scroll
        if (slot <= endSlot) {
            ItemStack armorScroll = scrollManager.createArmorEnhancementScroll(tier);
            if (armorScroll != null) {
                addPreviewLabel(armorScroll, "Enhancement Scroll");
                inventory.setItem(slot++, armorScroll);
            }
        }

        // Teleport scroll
        if (slot <= endSlot) {
            try {
                ItemStack teleportScroll = dropFactory.createScrollDrop(tier);
                if (teleportScroll != null) {
                    addPreviewLabel(teleportScroll, "Teleport Scroll");
                    inventory.setItem(slot++, teleportScroll);
                }
            } catch (Exception e) {
                // Skip if creation fails
            }
        }
    }

    /**
     * Adds example orbs to the preview
     */
    private void addExampleOrbs(Inventory inventory, int tier, int startSlot, int endSlot) {
        int slot = startSlot;

        // Normal orb
        if (slot <= endSlot) {
            ItemStack normalOrb = orbManager.createNormalOrb(false);
            if (normalOrb != null) {
                addPreviewLabel(normalOrb, "Orb Reward");
                inventory.setItem(slot++, normalOrb);
            }
        }

        // Legendary orb (for higher tiers)
        if (tier >= 4 && slot <= endSlot) {
            ItemStack legendaryOrb = orbManager.createLegendaryOrb(false);
            if (legendaryOrb != null) {
                addPreviewLabel(legendaryOrb, "Rare Orb Reward");
                inventory.setItem(slot++, legendaryOrb);
            }
        }
    }

    /**
     * Adds example gems to the preview
     */
    private void addExampleGems(Inventory inventory, int tier, int startSlot, int endSlot) {
        int slot = startSlot;

        if (slot <= endSlot) {
            ItemStack gems = MoneyManager.makeGems(tier * tier * 20);
            if (gems != null) {
                addPreviewLabel(gems, "Gem Reward");
                inventory.setItem(slot, gems);
            }
        }
    }

    /**
     * Adds protection scrolls for higher tiers
     */
    private void addProtectionScrolls(Inventory inventory, int tier, int startSlot, int endSlot) {
        int slot = startSlot;
        int protectionTier = Math.max(0, Math.min(tier - 1, 5));

        if (slot <= endSlot) {
            ItemStack protectionScroll = scrollManager.createProtectionScroll(protectionTier);
            if (protectionScroll != null) {
                addPreviewLabel(protectionScroll, "Protection Scroll");
                inventory.setItem(slot, protectionScroll);
            }
        }
    }

    /**
     * Adds legendary items for highest tiers
     */
    private void addLegendaryItems(Inventory inventory, int tier, int startSlot, int endSlot) {
        int slot = startSlot;

        // Legendary equipment
        if (slot <= endSlot) {
            try {
                ItemStack legendaryItem = dropsManager.createDrop(tier, 3, 4); // Legendary sword
                if (legendaryItem != null) {
                    addPreviewLabel(legendaryItem, "Legendary Equipment");
                    inventory.setItem(slot++, legendaryItem);
                }
            } catch (Exception e) {
                // Skip if creation fails
            }
        }

        // Multiple orbs
        if (slot <= endSlot) {
            ItemStack multipleOrbs = orbManager.createNormalOrb(false);
            if (multipleOrbs != null) {
                multipleOrbs.setAmount(3);
                addPreviewLabel(multipleOrbs, "Multiple Orbs");
                inventory.setItem(slot, multipleOrbs);
            }
        }
    }

    /**
     * Adds Halloween bonus items
     */
    private void addHalloweenBonuses(Inventory inventory, int tier, int startSlot, int endSlot) {
        int slot = startSlot;

        if (slot <= endSlot) {
            ItemStack halloweenGems = MoneyManager.makeGems(tier * 150);
            if (halloweenGems != null) {
                addPreviewLabel(halloweenGems, "Halloween Bonus");
                inventory.setItem(slot++, halloweenGems);
            }
        }

        if (slot <= endSlot) {
            ItemStack halloweenOrbs = orbManager.createNormalOrb(false);
            if (halloweenOrbs != null) {
                halloweenOrbs.setAmount(2);
                addPreviewLabel(halloweenOrbs, "Halloween Bonus");
                inventory.setItem(slot, halloweenOrbs);
            }
        }
    }

    /**
     * Adds a preview label to an item
     */
    private void addPreviewLabel(ItemStack item, String label) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GOLD + "» " + label + " «");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Creates a back button
     */
    private ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "← Back to Crate Selection");
        back.setItemMeta(meta);
        return back;
    }

    /**
     * Creates crate info item for preview GUI
     */
    private ItemStack createCrateInfoItem(CrateType crateType) {
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();

        String displayName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName();
        meta.setDisplayName(ChatColor.YELLOW + displayName + " Crate Info");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Tier: " + ChatColor.WHITE + crateType.getTier());
        lore.add(ChatColor.GRAY + "Quality: " + getTierQuality(crateType.getTier()));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Reward Types:");
        lore.add(ChatColor.WHITE + "• Equipment (70% chance)");
        lore.add(ChatColor.WHITE + "• Gems (70% chance)");
        lore.add(ChatColor.WHITE + "• Orbs (" + (35 + crateType.getTier() * 8) + "% chance)");
        lore.add(ChatColor.WHITE + "• Scrolls (25% chance)");

        if (crateType.getTier() >= 4) {
            lore.add(ChatColor.LIGHT_PURPLE + "• Bonus Items (15% chance)");
        }

        if (crateType.isHalloween()) {
            lore.add(ChatColor.GOLD + "• Halloween Bonuses (60% chance)");
        }

        lore.add("");
        lore.add(ChatColor.GRAY + "Note: These are example items.");
        lore.add(ChatColor.GRAY + "Actual rewards are randomized!");

        meta.setLore(lore);
        info.setItemMeta(meta);
        return info;
    }

    /**
     * Fills background with glass panes
     */
    private void fillBackground(Inventory inventory, Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass);
            }
        }
    }

    /**
     * Gets the quality description for a tier
     */
    private String getTierQuality(int tier) {
        return switch (tier) {
            case 1, 2 -> ChatColor.WHITE + "Common-Uncommon";
            case 3, 4 -> ChatColor.GREEN + "Uncommon-Rare";
            case 5, 6 -> ChatColor.YELLOW + "Rare-Legendary";
            default -> ChatColor.GRAY + "Unknown";
        };
    }

    /**
     * Gets item type name for display
     */
    private String getItemTypeName(int itemType) {
        return switch (itemType) {
            case 1 -> "Staff";
            case 2 -> "Spear";
            case 3 -> "Sword";
            case 4 -> "Axe";
            case 5 -> "Helmet";
            case 6 -> "Chestplate";
            case 7 -> "Leggings";
            case 8 -> "Boots";
            default -> "Equipment";
        };
    }

    /**
     * Checks if it's Halloween season (October-November)
     */
    private boolean isHalloweenSeason() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        return month == Calendar.OCTOBER || month == Calendar.NOVEMBER;
    }

    /**
     * Handles inventory clicks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (!openGUIs.contains(player.getUniqueId())) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String title = event.getView().getTitle();

        if (title.equals(MAIN_TITLE)) {
            handleMainGUIClick(player, clickedItem);
        } else if (title.contains("Contents")) {
            handlePreviewGUIClick(player, clickedItem);
        }
    }

    /**
     * Handles clicks in the main GUI
     */
    private void handleMainGUIClick(Player player, ItemStack clickedItem) {
        // Check if clicked item is a crate
        CrateType crateType = crateFactory.determineCrateType(clickedItem);
        if (crateType != null) {
            openPreviewGUI(player, crateType);
        }
    }

    /**
     * Handles clicks in the preview GUI
     */
    private void handlePreviewGUIClick(Player player, ItemStack clickedItem) {
        // Check for back button
        if (clickedItem.getType() == Material.ARROW &&
                clickedItem.hasItemMeta() &&
                clickedItem.getItemMeta().hasDisplayName() &&
                clickedItem.getItemMeta().getDisplayName().contains("Back")) {
            openMainGUI(player);
        }
    }

    /**
     * Handles inventory close
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            openGUIs.remove(player.getUniqueId());
        }
    }

    /**
     * Gets statistics for debugging
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("openGUIs", openGUIs.size());
        stats.put("registeredEvents", true);
        return stats;
    }
}