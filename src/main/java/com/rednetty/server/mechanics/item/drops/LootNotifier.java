package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.chat.JsonChatComponent;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Notification system for item drops with hoverable items and interactive tooltips.
 * Supports JsonChatComponent for interactive item tooltips and fallback methods for compatibility.
 */
public class LootNotifier {
    private static LootNotifier instance;

    // Constants for particle effects
    private static final int SPECIAL_EFFECT_PARTICLES = 20;
    private static final int SPECIAL_EFFECT_DELAY_TICKS = 10;
    private static final double PARTICLE_SPREAD = 1.0;
    private static final double PARTICLE_SPEED = 0.1;
    private static final int PARTICLE_Y_OFFSET = 2;

    // Sound effect constants
    private static final float DEFAULT_VOLUME = 1.0f;
    private static final float HIGH_VALUE_VOLUME = 1.5f;
    private static final float BOSS_PITCH = 0.8f;
    private static final float PITCH_VARIANCE = 0.4f;
    private static final float PITCH_OFFSET = 0.2f;

    // Rarity detection patterns
    private static final Pattern COMMON_PATTERN = Pattern.compile("(?i)common");
    private static final Pattern UNCOMMON_PATTERN = Pattern.compile("(?i)uncommon");
    private static final Pattern RARE_PATTERN = Pattern.compile("(?i)rare");
    private static final Pattern UNIQUE_PATTERN = Pattern.compile("(?i)unique|legendary");

    // Visual elements mapping
    private static final Map<String, String> RARITY_SYMBOLS = Map.of(
            "common", "◆",
            "uncommon", "◇",
            "rare", "★",
            "unique", "✦",
            "legendary", "✧"
    );

    private static final Map<String, ChatColor[]> RARITY_COLORS = Map.of(
            "common", new ChatColor[]{ChatColor.WHITE, ChatColor.WHITE},
            "uncommon", new ChatColor[]{ChatColor.GREEN, ChatColor.GREEN},
            "rare", new ChatColor[]{ChatColor.AQUA, ChatColor.AQUA},
            "unique", new ChatColor[]{ChatColor.YELLOW, ChatColor.YELLOW}
    );

    // Sound mappings for different rarities
    private static final Map<String, Sound[]> RARITY_SOUNDS = Map.of(
            "common", new Sound[]{Sound.ENTITY_ITEM_PICKUP},
            "uncommon", new Sound[]{Sound.ENTITY_EXPERIENCE_ORB_PICKUP},
            "rare", new Sound[]{Sound.ENTITY_DOLPHIN_PLAY, Sound.BLOCK_NOTE_BLOCK_CHIME},
            "unique", new Sound[]{Sound.ENTITY_ENDER_DRAGON_DEATH, Sound.BLOCK_NOTE_BLOCK_BELL}
    );

    // Drop type symbols for visual identification
    private static final Map<String, String> DROP_TYPE_SYMBOLS = Map.of(
            "weapon", "⚔",
            "armor", "🛡",
            "gem", "💎",
            "scroll", "📜",
            "crate", "📦",
            "book", "📖",
            "boss", "👑"
    );

    // Tier colors including Tier 6 Netherite support
    private static final Map<Integer, ChatColor> TIER_COLORS = Map.of(
            1, ChatColor.WHITE,
            2, ChatColor.GREEN,
            3, ChatColor.AQUA,
            4, ChatColor.LIGHT_PURPLE,
            5, ChatColor.YELLOW,
            6, ChatColor.GOLD
    );

    /**
     * Private constructor for singleton pattern
     */
    private LootNotifier() {
    }

    /**
     * Gets the singleton instance
     */
    public static LootNotifier getInstance() {
        if (instance == null) {
            instance = new LootNotifier();
        }
        return instance;
    }

    /**
     * Sends drop notification with hoverable items using JsonChatComponent
     */
    public void sendDropNotification(Player player, ItemStack item, LivingEntity source, boolean isBossLoot) {
        if (!isValidNotificationTarget(player, item)) {
            return;
        }

        try {
            DropNotificationData notificationData = analyzeDropNotification(item, source, isBossLoot);

            sendInteractiveChatNotification(player, notificationData, item);
            playNotificationEffects(player, notificationData);

            if (notificationData.isHighValue()) {
                sendSpecialEffects(player, notificationData);
            }

        } catch (Exception e) {
            handleNotificationError(e, "drop notification");
            sendBasicNotification(player, item, source, isBossLoot);
        }
    }

    /**
     * Sends notification for gem drops with hover details
     */
    public void sendGemDropNotification(Player player, ItemStack gems, LivingEntity source, int amount) {
        if (!isValidNotificationTarget(player, gems)) {
            return;
        }

        try {
            String sourceName = getSourceName(source);
            String gemHoverText = buildGemHoverText(amount);

            JsonChatComponent message = new JsonChatComponent(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped ");
            message.addHoverItem(ChatColor.GREEN + "💎 " + amount + " Gems", List.of(gemHoverText));

            message.send(player);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);

        } catch (Exception e) {
            handleNotificationError(e, "gem notification");
            sendBasicGemNotification(player, source, amount);
        }
    }

    /**
     * Sends notification for crate drops with hover details
     */
    public void sendCrateDropNotification(Player player, ItemStack crate, LivingEntity source, int tier) {
        if (!isValidNotificationTarget(player, crate)) {
            return;
        }

        try {
            String sourceName = getSourceName(source);
            ChatColor tierColor = TIER_COLORS.getOrDefault(tier, ChatColor.WHITE);
            List<String> hoverText = buildCrateHoverText(crate, tier, tierColor);

            JsonChatComponent message = new JsonChatComponent(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped ");

            String crateName = getCrateName(crate, tier, tierColor);
            message.addHoverItem("📦 " + ChatColor.stripColor(crateName), hoverText);

            message.send(player);
            playCrateSound(player, tier);

        } catch (Exception e) {
            handleNotificationError(e, "crate notification");
            sendBasicCrateNotification(player, crate, source, tier);
        }
    }

    /**
     * Sends notification for teleport books with hover details
     */
    public void sendTeleportBookNotification(Player player, ItemStack book, LivingEntity source, String destinationName) {
        if (!isValidNotificationTarget(player, book)) {
            return;
        }

        try {
            String sourceName = getSourceName(source);
            List<String> hoverText = buildBookHoverText(book, destinationName);

            JsonChatComponent message = new JsonChatComponent(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped ");

            String bookName = getBookName(book, destinationName);
            message.addHoverItem("📖 " + ChatColor.stripColor(bookName), hoverText);

            message.send(player);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        } catch (Exception e) {
            handleNotificationError(e, "teleport book notification");
            sendBasicBookNotification(player, book, source, destinationName);
        }
    }

    /**
     * Announces buff activation with visual design
     */
    public void announceBuffActivation(Player player, int buffRate, int durationMinutes) {
        int eliteBuffRate = buffRate / 2;
        String playerName = player.getName();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            sendBuffActivationToPlayer(onlinePlayer, playerName, buffRate, eliteBuffRate, durationMinutes);
        }
    }

    /**
     * Announces buff end with statistics
     */
    public void announceBuffEnd(String playerName, int improvedDrops) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendBuffEndToPlayer(player, playerName, improvedDrops);
        }
    }

    /**
     * Announces world boss defeat with formatting
     */
    public void announceWorldBossDefeat(String bossName, List<Object[]> topDamagers) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendWorldBossDefeatToPlayer(player, bossName, topDamagers);
        }
    }

    /**
     * Sends a custom notification for special events
     */
    public void sendCustomNotification(Player player, String title, String message, Sound sound) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(ChatColor.GOLD + "✦ " + title + " ✦"));
        player.sendMessage(TextUtil.getCenteredMessage(message));
        player.sendMessage("");

        if (sound != null) {
            player.playSound(player.getLocation(), sound, DEFAULT_VOLUME, DEFAULT_VOLUME);
        }
    }

    /**
     * Gets notification statistics for debugging
     */
    public Map<String, Object> getNotificationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rarityTypes", RARITY_COLORS.size());
        stats.put("soundMappings", RARITY_SOUNDS.size());
        stats.put("dropTypes", DROP_TYPE_SYMBOLS.size());
        stats.put("symbolMappings", RARITY_SYMBOLS.size());
        stats.put("tierColors", TIER_COLORS.size());
        return stats;
    }

    // Private helper methods

    /**
     * Validates notification target requirements
     */
    private boolean isValidNotificationTarget(Player player, ItemStack item) {
        return player != null && item != null && player.isOnline();
    }

    /**
     * Handles notification errors consistently
     */
    private void handleNotificationError(Exception e, String notificationType) {
        YakRealms.warn("Error sending " + notificationType + ": " + e.getMessage());
    }

    /**
     * Builds hover text for gem notifications
     */
    private String buildGemHoverText(int amount) {
        return ChatColor.GREEN + "Gems: " + ChatColor.YELLOW + amount + "\n" +
                ChatColor.GRAY + "Currency used for trading\n" +
                ChatColor.GRAY + "and purchasing items";
    }

    /**
     * Builds hover text for crate notifications
     */
    private List<String> buildCrateHoverText(ItemStack crate, int tier, ChatColor tierColor) {
        List<String> hoverText = new ArrayList<>();

        if (crate.getItemMeta() != null && crate.getItemMeta().hasLore()) {
            hoverText.addAll(crate.getItemMeta().getLore());
        } else {
            hoverText.add(tierColor + "Tier " + tier + " Crate");
            hoverText.add(ChatColor.GRAY + "Right-click to open");
            hoverText.add(ChatColor.GRAY + "Contains tier " + tier + " items");
        }

        return hoverText;
    }

    /**
     * Builds hover text for teleport book notifications
     */
    private List<String> buildBookHoverText(ItemStack book, String destinationName) {
        List<String> hoverText = new ArrayList<>();

        if (book.getItemMeta() != null && book.getItemMeta().hasLore()) {
            hoverText.addAll(book.getItemMeta().getLore());
        } else {
            hoverText.add(ChatColor.AQUA + "Teleport Book");
            hoverText.add(ChatColor.GRAY + "Destination: " + ChatColor.WHITE + destinationName);
            hoverText.add(ChatColor.GRAY + "Right-click to teleport");
        }

        return hoverText;
    }

    /**
     * Gets display name for crates
     */
    private String getCrateName(ItemStack crate, int tier, ChatColor tierColor) {
        return crate.getItemMeta() != null && crate.getItemMeta().hasDisplayName()
                ? crate.getItemMeta().getDisplayName()
                : tierColor + "Tier " + tier + " Crate";
    }

    /**
     * Gets display name for teleport books
     */
    private String getBookName(ItemStack book, String destinationName) {
        return book.getItemMeta() != null && book.getItemMeta().hasDisplayName()
                ? book.getItemMeta().getDisplayName()
                : ChatColor.AQUA + destinationName + " Teleport Book";
    }

    /**
     * Plays appropriate sound for crate drops
     */
    private void playCrateSound(Player player, int tier) {
        Sound crateSound = tier >= 4 ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.ENTITY_ITEM_PICKUP;
        player.playSound(player.getLocation(), crateSound, DEFAULT_VOLUME, DEFAULT_VOLUME + (tier * 0.1f));
    }

    /**
     * Sends interactive chat notification with hoverable items
     */
    private void sendInteractiveChatNotification(Player player, DropNotificationData data, ItemStack item) {
        ChatColor[] colors = RARITY_COLORS.getOrDefault(data.rarity, RARITY_COLORS.get("common"));
        String symbol = RARITY_SYMBOLS.getOrDefault(data.rarity, "◆");
        String typeSymbol = DROP_TYPE_SYMBOLS.getOrDefault(data.dropType, "");

        if (data.isBossLoot) {
            sendBossLootNotification(player, data, item);
        } else {
            sendRegularDropNotification(player, data, item, colors, typeSymbol);
        }
    }

    /**
     * Sends boss loot notification with special formatting
     */
    private void sendBossLootNotification(Player player, DropNotificationData data, ItemStack item) {
        String bossMessage = ChatColor.GOLD + "👑 " + ChatColor.YELLOW + "You received " +
                ChatColor.stripColor(data.itemName) + ChatColor.YELLOW + " from the Special Elite!";

        JsonChatComponent message = new JsonChatComponent("");
        message.addHoverItem(TextUtil.getCenteredMessage(bossMessage), getItemHoverText(item));
        message.send(player);
    }

    /**
     * Sends regular drop notification with interactive hover
     */
    private void sendRegularDropNotification(Player player, DropNotificationData data, ItemStack item,
                                             ChatColor[] colors, String typeSymbol) {
        JsonChatComponent message = new JsonChatComponent(colors[1] + "➤ " + ChatColor.RESET +
                data.sourceName + ChatColor.YELLOW + " dropped ");

        String displayText = typeSymbol + " " + data.itemName;
        message.addHoverItem(displayText, getItemHoverText(item));
        message.send(player);
    }

    /**
     * Extracts hover text from an item for tooltips
     */
    private List<String> getItemHoverText(ItemStack item) {
        List<String> hoverText = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            addItemNameToHover(hoverText, meta, item);
            addItemLoreToHover(hoverText, meta);
        } else {
            hoverText.add(ChatColor.WHITE + TextUtil.formatItemName(item.getType().name()));
        }

        return hoverText;
    }

    /**
     * Adds item name to hover text
     */
    private void addItemNameToHover(List<String> hoverText, ItemMeta meta, ItemStack item) {
        if (meta.hasDisplayName()) {
            hoverText.add(meta.getDisplayName());
        } else {
            hoverText.add(ChatColor.WHITE + TextUtil.formatItemName(item.getType().name()));
        }
    }

    /**
     * Adds item lore to hover text
     */
    private void addItemLoreToHover(List<String> hoverText, ItemMeta meta) {
        if (meta.hasLore() && meta.getLore() != null) {
            hoverText.add("");
            hoverText.addAll(meta.getLore());
        }
    }

    /**
     * Gets the proper name for a mob without health bar formatting
     */
    private String getProperMobName(LivingEntity livingDamager) {
        try {
            String customMobName = getCustomMobName(livingDamager);
            if (customMobName != null) {
                return customMobName;
            }

            String customName = getCleanCustomName(livingDamager);
            if (customName != null) {
                return customName;
            }

            return formatEntityTypeName(livingDamager.getType().name());

        } catch (Exception e) {
            YakRealms.warn("Error getting proper mob name for " + livingDamager.getType() + ": " + e.getMessage());
            return formatEntityTypeName(livingDamager.getType().name());
        }
    }

    /**
     * Gets custom mob name from MobManager system
     */
    private String getCustomMobName(LivingEntity livingDamager) {
        if (!livingDamager.hasMetadata("type")) {
            return null;
        }

        MobManager mobManager = MobManager.getInstance();
        if (mobManager != null) {
            CustomMob customMob = mobManager.getCustomMob(livingDamager);
            if (customMob != null) {
                String originalName = customMob.getOriginalName();
                if (originalName != null && !originalName.isEmpty()) {
                    return ChatColor.stripColor(originalName);
                }
            }
        }

        // Fallback to metadata
        try {
            String metaName = livingDamager.getMetadata("customName").get(0).asString();
            if (metaName != null && !metaName.isEmpty()) {
                return metaName;
            }
        } catch (Exception e) {
            // Ignore metadata errors
        }

        return null;
    }

    /**
     * Gets clean custom name that's not a health bar
     */
    private String getCleanCustomName(LivingEntity livingDamager) {
        String customName = livingDamager.getCustomName();
        if (customName == null) {
            return null;
        }

        // Check if name contains health bar indicators
        if (customName.contains("|") || customName.contains("§a") || customName.contains("§c")) {
            return null;
        }

        return ChatColor.stripColor(customName);
    }

    /**
     * Formats entity type name for display
     */
    private String formatEntityTypeName(String entityTypeName) {
        String formatted = entityTypeName.toLowerCase().replace("_", " ");
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (words[i].length() > 0) {
                result.append(words[i].substring(0, 1).toUpperCase());
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }

        return result.toString();
    }

    /**
     * Analyzes drop data for notifications
     */
    private DropNotificationData analyzeDropNotification(ItemStack item, LivingEntity source, boolean isBossLoot) {
        String itemName = getItemName(item);
        String sourceName = getSourceName(source);
        String rarity = detectItemRarity(item);
        String dropType = detectDropType(item);
        boolean isHighValue = isHighValueDrop(rarity, isBossLoot);

        return new DropNotificationData(itemName, sourceName, rarity, dropType, isBossLoot, isHighValue);
    }

    /**
     * Extracts item name with color preservation
     */
    private String getItemName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return TextUtil.formatItemName(item.getType().name());
    }

    /**
     * Gets source name with tier-based formatting
     */
    private String getSourceName(LivingEntity source) {
        if (source == null) {
            return "Unknown Source";
        }

        int tier = getMobTier(source);
        ChatColor tierColor = TIER_COLORS.getOrDefault(tier, ChatColor.WHITE);
        return tierColor + getProperMobName(source);
    }

    /**
     * Gets mob tier from MobManager
     */
    private int getMobTier(LivingEntity source) {
        try {
            if (MobManager.getInstance() != null) {
                CustomMob customMob = MobManager.getInstance().getCustomMob(source);
                if (customMob != null) {
                    return customMob.getTier();
                }
            }
        } catch (Exception e) {
            // Use default tier on error
        }
        return 1;
    }

    /**
     * Detects rarity from item lore and name
     */
    private String detectItemRarity(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "common";
        }

        String loreRarity = detectRarityFromLore(meta);
        if (loreRarity != null) {
            return loreRarity;
        }

        return detectRarityFromName(meta);
    }

    /**
     * Detects rarity from item lore
     */
    private String detectRarityFromLore(ItemMeta meta) {
        if (!meta.hasLore()) {
            return null;
        }

        List<String> lore = meta.getLore();
        String lastLine = lore.get(lore.size() - 1);
        String cleanLine = ChatColor.stripColor(lastLine).toLowerCase();

        if (UNIQUE_PATTERN.matcher(cleanLine).find()) return "unique";
        if (RARE_PATTERN.matcher(cleanLine).find()) return "rare";
        if (UNCOMMON_PATTERN.matcher(cleanLine).find()) return "uncommon";
        if (COMMON_PATTERN.matcher(cleanLine).find()) return "common";

        return null;
    }

    /**
     * Detects rarity from item name colors
     */
    private String detectRarityFromName(ItemMeta meta) {
        if (!meta.hasDisplayName()) {
            return "common";
        }

        String displayName = meta.getDisplayName();

        if (displayName.contains(ChatColor.YELLOW.toString()) ||
                displayName.contains(ChatColor.GOLD.toString())) return "unique";
        if (displayName.contains(ChatColor.AQUA.toString()) ||
                displayName.contains(ChatColor.DARK_AQUA.toString())) return "rare";
        if (displayName.contains(ChatColor.GREEN.toString()) ||
                displayName.contains(ChatColor.DARK_GREEN.toString())) return "uncommon";
        if (displayName.toLowerCase().contains("netherite")) return "legendary";

        return "common";
    }

    /**
     * Detects the type of drop for appropriate symbols
     */
    private String detectDropType(ItemStack item) {
        String materialName = item.getType().name().toLowerCase();

        if (isWeapon(materialName)) return "weapon";
        if (isArmor(materialName)) return "armor";
        if (isGem(materialName)) return "gem";
        if (isScroll(materialName)) return "scroll";
        if (isBook(materialName)) return "book";
        if (isCrate(materialName)) return "crate";

        return "common";
    }

    /**
     * Checks if item is a weapon
     */
    private boolean isWeapon(String materialName) {
        return materialName.contains("sword") || materialName.contains("axe") ||
                materialName.contains("pickaxe") || materialName.contains("shovel") ||
                materialName.contains("hoe");
    }

    /**
     * Checks if item is armor
     */
    private boolean isArmor(String materialName) {
        return materialName.contains("helmet") || materialName.contains("chestplate") ||
                materialName.contains("leggings") || materialName.contains("boots");
    }

    /**
     * Checks if item is a gem
     */
    private boolean isGem(String materialName) {
        return materialName.contains("emerald") || materialName.contains("diamond");
    }

    /**
     * Checks if item is a scroll
     */
    private boolean isScroll(String materialName) {
        return materialName.contains("paper") || materialName.contains("map");
    }

    /**
     * Checks if item is a book
     */
    private boolean isBook(String materialName) {
        return materialName.contains("book");
    }

    /**
     * Checks if item is a crate
     */
    private boolean isCrate(String materialName) {
        return materialName.contains("chest") || materialName.contains("shulker");
    }

    /**
     * Determines if a drop is high value for special effects
     */
    private boolean isHighValueDrop(String rarity, boolean isBossLoot) {
        return isBossLoot || "unique".equals(rarity) || "legendary".equals(rarity);
    }

    /**
     * Plays notification effects based on rarity
     */
    private void playNotificationEffects(Player player, DropNotificationData data) {
        Sound[] sounds = RARITY_SOUNDS.getOrDefault(data.rarity, RARITY_SOUNDS.get("common"));
        Sound selectedSound = sounds[ThreadLocalRandom.current().nextInt(sounds.length)];

        float volume = data.isHighValue ? HIGH_VALUE_VOLUME : DEFAULT_VOLUME;
        float pitch = calculatePitch(data.isBossLoot);

        player.playSound(player.getLocation(), selectedSound, volume, pitch);
    }

    /**
     * Calculates pitch for sound effects
     */
    private float calculatePitch(boolean isBossLoot) {
        if (isBossLoot) {
            return BOSS_PITCH;
        }
        return DEFAULT_VOLUME + (ThreadLocalRandom.current().nextFloat() * PITCH_VARIANCE - PITCH_OFFSET);
    }

    /**
     * Sends special effects for high-value drops
     */
    private void sendSpecialEffects(Player player, DropNotificationData data) {
        spawnParticleEffects(player);
        scheduleDelayedSoundEffect(player);
    }

    /**
     * Spawns particle effects around the player
     */
    private void spawnParticleEffects(Player player) {
        player.getWorld().spawnParticle(
                Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, PARTICLE_Y_OFFSET, 0),
                SPECIAL_EFFECT_PARTICLES,
                PARTICLE_SPREAD,
                PARTICLE_SPREAD,
                PARTICLE_SPREAD,
                PARTICLE_SPEED
        );
    }

    /**
     * Schedules delayed sound effect for special drops
     */
    private void scheduleDelayedSoundEffect(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
        }.runTaskLater(YakRealms.getInstance(), SPECIAL_EFFECT_DELAY_TICKS);
    }

    // Fallback methods for when JsonChatComponent fails

    private void sendBasicGemNotification(Player player, LivingEntity source, int amount) {
        String sourceName = getSourceName(source);
        player.sendMessage(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped " +
                ChatColor.GREEN + "💎 " + amount + " Gems");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    private void sendBasicCrateNotification(Player player, ItemStack crate, LivingEntity source, int tier) {
        String sourceName = getSourceName(source);
        String crateName = crate.getItemMeta() != null && crate.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(crate.getItemMeta().getDisplayName())
                : "Tier " + tier + " Crate";

        player.sendMessage(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped " +
                "📦 " + crateName);

        playCrateSound(player, tier);
    }

    private void sendBasicBookNotification(Player player, ItemStack book, LivingEntity source, String destinationName) {
        String sourceName = getSourceName(source);
        player.sendMessage(ChatColor.GRAY + "➤ " + sourceName + ChatColor.YELLOW + " dropped " +
                "📖 " + destinationName + " Teleport Book");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    private void sendBasicNotification(Player player, ItemStack item, LivingEntity source, boolean isBossLoot) {
        String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : TextUtil.formatItemName(item.getType().name());

        String message;
        if (isBossLoot) {
            message = TextUtil.getCenteredMessage(
                    ChatColor.RED + "➤ " + ChatColor.YELLOW + "You received " + itemName +
                            ChatColor.YELLOW + " from the World Boss"
            );
        } else {
            String sourceName = getSourceName(source);
            message = ChatColor.GRAY + "➤ " + ChatColor.RESET + sourceName +
                    ChatColor.YELLOW + " dropped " + ChatColor.RESET + itemName;
        }

        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, DEFAULT_VOLUME, DEFAULT_VOLUME);
    }

    /**
     * Sends buff activation message to a single player
     */
    private void sendBuffActivationToPlayer(Player player, String activatorName, int buffRate,
                                            int eliteBuffRate, int durationMinutes) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, DEFAULT_VOLUME, 0.5f);

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GOLD + "✦ " + ChatColor.YELLOW + ChatColor.BOLD + "LOOT BUFF ACTIVATED" +
                        ChatColor.RESET + " " + ChatColor.GOLD + "✦"
        ));
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.AQUA + activatorName + ChatColor.YELLOW + " activated a server-wide loot buff!"
        ));

        sendBuffDetails(player, buffRate, eliteBuffRate, durationMinutes);

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GREEN + "Thank you for supporting the server!"
        ));
        player.sendMessage("");
    }

    /**
     * Sends buff details to player
     */
    private void sendBuffDetails(Player player, int buffRate, int eliteBuffRate, int durationMinutes) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.WHITE + "Normal drop rates: " +
                ChatColor.GREEN + "+" + buffRate + "%");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.WHITE + "Elite drop rates: " +
                ChatColor.GREEN + "+" + eliteBuffRate + "%");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.WHITE + "Duration: " +
                ChatColor.YELLOW + durationMinutes + " minutes");
    }

    /**
     * Sends buff end message to a single player
     */
    private void sendBuffEndToPlayer(Player player, String activatorName, int improvedDrops) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, DEFAULT_VOLUME, DEFAULT_VOLUME);

        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.RED + "✦ " + ChatColor.YELLOW + ChatColor.BOLD + "LOOT BUFF EXPIRED" +
                        ChatColor.RESET + " " + ChatColor.RED + "✦"
        ));
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.AQUA + activatorName + ChatColor.YELLOW + "'s loot buff has ended!"
        ));
        player.sendMessage(TextUtil.getCenteredMessage(ChatColor.LIGHT_PURPLE + "    ◆ " + ChatColor.GRAY + " drops provided: " + ChatColor.GREEN + improvedDrops));
        player.sendMessage("");
    }

    /**
     * Sends world boss defeat message to a single player
     */
    private void sendWorldBossDefeatToPlayer(Player player, String bossName, List<Object[]> topDamagers) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, DEFAULT_VOLUME, DEFAULT_VOLUME);

        sendBossDefeatHeader(player, bossName);
        sendTopContributors(player, topDamagers);
        sendBossDefeatFooter(player);
    }

    /**
     * Sends boss defeat header message
     */
    private void sendBossDefeatHeader(Player player, String bossName) {
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GOLD + "👑 " + ChatColor.RED + ChatColor.BOLD + "WORLD BOSS DEFEATED" +
                        ChatColor.RESET + " " + ChatColor.GOLD + "👑"
        ));
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.YELLOW + bossName + ChatColor.WHITE + " has fallen!"
        ));
    }

    /**
     * Sends top contributors list
     */
    private void sendTopContributors(Player player, List<Object[]> topDamagers) {
        if (topDamagers.isEmpty()) {
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "⚔ " + ChatColor.YELLOW + "Top Contributors:");

        for (int i = 0; i < Math.min(3, topDamagers.size()); i++) {
            Object[] entry = topDamagers.get(i);
            String name = (String) entry[0];
            int damage = (int) entry[1];

            String rank = getRankSymbol(i);
            player.sendMessage(ChatColor.WHITE + "  " + rank + " " + ChatColor.AQUA + name +
                    ChatColor.GRAY + " - " + ChatColor.RED + TextUtil.formatNumber(damage) +
                    " damage");
        }
    }

    /**
     * Gets rank symbol for contributor position
     */
    private String getRankSymbol(int position) {
        return switch (position) {
            case 0 -> ChatColor.GOLD + "🥇";
            case 1 -> ChatColor.GRAY + "🥈";
            case 2 -> ChatColor.YELLOW + "🥉";
            default -> ChatColor.WHITE.toString() + (position + 1) + ".";
        };
    }

    /**
     * Sends boss defeat footer message
     */
    private void sendBossDefeatFooter(Player player) {
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.YELLOW + "Legendary loot has been distributed!"
        ));
        player.sendMessage("");
    }

    /**
     * Data class for notification information
     */
    private static class DropNotificationData {
        final String itemName;
        final String sourceName;
        final String rarity;
        final String dropType;
        final boolean isBossLoot;
        final boolean isHighValue;

        DropNotificationData(String itemName, String sourceName, String rarity, String dropType,
                             boolean isBossLoot, boolean isHighValue) {
            this.itemName = itemName;
            this.sourceName = sourceName;
            this.rarity = rarity;
            this.dropType = dropType;
            this.isBossLoot = isBossLoot;
            this.isHighValue = isHighValue;
        }

        boolean isHighValue() {
            return isHighValue;
        }
    }
}