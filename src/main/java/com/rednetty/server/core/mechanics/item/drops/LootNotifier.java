package com.rednetty.server.core.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.MobManager;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.utils.messaging.MessageUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Notification system for item drops with Adventure API integration and full backwards compatibility.
 * Updated to use Paper Spigot 1.21.7 capabilities while maintaining all existing method signatures.
 */
public class LootNotifier {
    private static LootNotifier instance;

    // Adventure API components
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

    // Performance tracking
    private final Set<BukkitTask> activeEffectTasks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastNotificationTime = new ConcurrentHashMap<>();

    // Constants for particle effects
    private static final int SPECIAL_EFFECT_PARTICLES = 30;
    private static final int SPECIAL_EFFECT_DELAY_TICKS = 10;
    private static final double PARTICLE_SPREAD = 1.5;
    private static final double PARTICLE_SPEED = 0.15;
    private static final int PARTICLE_Y_OFFSET = 2;

    // Sound effect constants
    private static final float DEFAULT_VOLUME = 1.0f;
    private static final float HIGH_VALUE_VOLUME = 1.5f;
    private static final float BOSS_PITCH = 0.8f;
    private static final float PITCH_VARIANCE = 0.4f;
    private static final float PITCH_OFFSET = 0.2f;

    // Enhanced rarity detection patterns
    private static final Pattern COMMON_PATTERN = Pattern.compile("(?i)\\b(common|normal|basic)\\b");
    private static final Pattern UNCOMMON_PATTERN = Pattern.compile("(?i)\\b(uncommon|enhanced|improved)\\b");
    private static final Pattern RARE_PATTERN = Pattern.compile("(?i)\\b(rare|superior|fine)\\b");
    private static final Pattern UNIQUE_PATTERN = Pattern.compile("(?i)\\b(unique|legendary|epic|mythic|ancient)\\b");

    // Enhanced visual elements with more Unicode symbols
    private static final Map<String, String> RARITY_SYMBOLS = Map.of(
            "common", "◆",
            "uncommon", "◇",
            "rare", "★",
            "unique", "✦",
            "legendary", "✧",
            "mythic", "❋",
            "ancient", "⚜"
    );

    // Enhanced color schemes with Adventure API colors
    private static final Map<String, TextColor[]> RARITY_COLORS = Map.of(
            "common", new TextColor[]{NamedTextColor.WHITE, NamedTextColor.GRAY},
            "uncommon", new TextColor[]{NamedTextColor.GREEN, NamedTextColor.DARK_GREEN},
            "rare", new TextColor[]{NamedTextColor.AQUA, NamedTextColor.DARK_AQUA},
            "unique", new TextColor[]{NamedTextColor.YELLOW, NamedTextColor.GOLD},
            "legendary", new TextColor[]{NamedTextColor.GOLD, TextColor.color(255, 165, 0)},
            "mythic", new TextColor[]{TextColor.color(138, 43, 226), TextColor.color(75, 0, 130)},
            "ancient", new TextColor[]{TextColor.color(255, 20, 147), TextColor.color(199, 21, 133)}
    );

    // Enhanced sound mappings using Bukkit Sound enums - no deprecated methods
    private static final Map<String, org.bukkit.Sound[]> RARITY_SOUNDS = Map.of(
            "common", new org.bukkit.Sound[]{org.bukkit.Sound.ENTITY_ITEM_PICKUP},
            "uncommon", new org.bukkit.Sound[]{org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP},
            "rare", new org.bukkit.Sound[]{org.bukkit.Sound.ENTITY_DOLPHIN_PLAY, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME},
            "unique", new org.bukkit.Sound[]{org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL},
            "legendary", new org.bukkit.Sound[]{org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP},
            "mythic", new org.bukkit.Sound[]{org.bukkit.Sound.ENTITY_WITHER_SPAWN, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE},
            "ancient", new org.bukkit.Sound[]{org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, org.bukkit.Sound.BLOCK_CONDUIT_ACTIVATE}
    );

    // Enhanced drop type symbols
    private static final Map<String, String> DROP_TYPE_SYMBOLS = Map.of(
            "weapon", "⚔",
            "armor", "🛡",
            "gem", "💎",
            "scroll", "📜",
            "crate", "📦",
            "book", "📖",
            "boss", "👑",
            "special", "✨",
            "consumable", "🧪",
            "tool", "🔧"
    );

    // Enhanced tier colors with Adventure API
    private static final Map<Integer, TextColor> TIER_COLORS = Map.of(
            1, NamedTextColor.WHITE,
            2, NamedTextColor.GREEN,
            3, NamedTextColor.AQUA,
            4, NamedTextColor.LIGHT_PURPLE,
            5, NamedTextColor.YELLOW,
            6, NamedTextColor.GOLD  // Enhanced for Netherite
    );

    /**
     * Private constructor for singleton pattern
     */
    private LootNotifier() {
        // Initialize cleanup task
        Bukkit.getScheduler().runTaskTimerAsynchronously(YakRealms.getInstance(), this::performMaintenance, 6000L, 6000L);
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
     * Sends drop notification with enhanced Adventure API features and hoverable items.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public void sendDropNotification(Player player, ItemStack item, LivingEntity source, boolean isBossLoot) {
        if (!isValidNotificationTarget(player, item)) {
            return;
        }

        // Rate limiting
        if (!canSendNotification(player)) {
            return;
        }

        try {
            DropNotificationData notificationData = analyzeDropNotification(item, source, isBossLoot);
            sendEnhancedInteractiveChatNotification(player, notificationData, item);
            playEnhancedNotificationEffects(player, notificationData);

            if (notificationData.isHighValue()) {
                sendEnhancedSpecialEffects(player, notificationData);
            }

        } catch (Exception e) {
            handleNotificationError(e, "drop notification");
            sendBasicNotification(player, item, source, isBossLoot); // Fallback
        }
    }

    /**
     * Sends notification for gem drops with enhanced hover details.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public void sendGemDropNotification(Player player, ItemStack gems, LivingEntity source, int amount) {
        if (!isValidNotificationTarget(player, gems) || !canSendNotification(player)) {
            return;
        }

        try {
            Component sourceName = getEnhancedSourceName(source);
            Component gemHoverContent = buildEnhancedGemHoverText(amount);

            Component message = Component.text("➤ ", NamedTextColor.GRAY)
                    .append(sourceName)
                    .append(Component.text(" dropped ", NamedTextColor.YELLOW))
                    .append(Component.text("💎 " + amount + " Gems", NamedTextColor.GREEN)
                            .hoverEvent(HoverEvent.showText(gemHoverContent))
                            .clickEvent(ClickEvent.runCommand("/gems balance")));

            player.sendMessage(message);
            playEnhancedSound(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            spawnEnhancedParticleEffect(player.getLocation(), "gem", amount);

        } catch (Exception e) {
            handleNotificationError(e, "gem notification");
            sendBasicGemNotification(player, source, amount);
        }
    }

    /**
     * Sends notification for crate drops with enhanced tier-based effects.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public void sendCrateDropNotification(Player player, ItemStack crate, LivingEntity source, int tier) {
        if (!isValidNotificationTarget(player, crate) || !canSendNotification(player)) {
            return;
        }

        try {
            Component sourceName = getEnhancedSourceName(source);
            TextColor tierColor = TIER_COLORS.getOrDefault(tier, NamedTextColor.WHITE);
            Component crateName = getEnhancedCrateName(crate, tier, tierColor);

            Component message = Component.text("➤ ", NamedTextColor.GRAY)
                    .append(sourceName)
                    .append(Component.text(" dropped ", NamedTextColor.YELLOW))
                    .append(Component.text("📦 ").append(crateName.colorIfAbsent(tierColor))
                            .hoverEvent(crate.asHoverEvent())
                            .clickEvent(ClickEvent.suggestCommand("/crate open " + tier)));

            player.sendMessage(message);
            playEnhancedCrateSound(player, tier);
            spawnEnhancedParticleEffect(player.getLocation(), "crate", tier);

        } catch (Exception e) {
            handleNotificationError(e, "crate notification");
            sendBasicCrateNotification(player, crate, source, tier);
        }
    }

    /**
     * Sends notification for teleport books with enhanced hover details.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public void sendTeleportBookNotification(Player player, ItemStack book, LivingEntity source, String destinationName) {
        if (!isValidNotificationTarget(player, book) || !canSendNotification(player)) {
            return;
        }

        try {
            Component sourceName = getEnhancedSourceName(source);
            Component bookName = getEnhancedBookName(book, destinationName);
            Component hoverText = buildEnhancedBookHoverText(destinationName);

            Component message = Component.text("➤ ", NamedTextColor.GRAY)
                    .append(sourceName)
                    .append(Component.text(" dropped ", NamedTextColor.YELLOW))
                    .append(Component.text("📖 ").append(bookName.colorIfAbsent(NamedTextColor.AQUA))
                            .hoverEvent(HoverEvent.showText(hoverText))
                            .clickEvent(ClickEvent.suggestCommand("/teleport " + destinationName)));

            player.sendMessage(message);
            playEnhancedSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            spawnEnhancedParticleEffect(player.getLocation(), "book", 1);

        } catch (Exception e) {
            handleNotificationError(e, "teleport book notification");
            sendBasicBookNotification(player, book, source, destinationName);
        }
    }

    /**
     * Announces buff activation with enhanced visual design.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public void announceBuffActivation(Player player, int buffRate, int durationMinutes) {
        int eliteBuffRate = buffRate / 2;
        String playerName = player.getName();

        Component announcement = buildEnhancedBuffActivationMessage(playerName, buffRate, eliteBuffRate, durationMinutes);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            try {
                sendEnhancedBuffActivationToPlayer(onlinePlayer, announcement);
            } catch (Exception e) {
                // Fallback to basic notification
                sendBuffActivationToPlayer(onlinePlayer, playerName, buffRate, eliteBuffRate, durationMinutes);
            }
        }
    }

    /**
     * Announces buff end with enhanced statistics.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public void announceBuffEnd(String playerName, int improvedDrops) {
        Component announcement = buildEnhancedBuffEndMessage(playerName, improvedDrops);

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                sendEnhancedBuffEndToPlayer(player, announcement);
            } catch (Exception e) {
                // Fallback to basic notification
                sendBuffEndToPlayer(player, playerName, improvedDrops);
            }
        }
    }

    /**
     * Announces world boss defeat with enhanced formatting.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public void announceWorldBossDefeat(String bossName, List<Object[]> topDamagers) {
        Component announcement = buildEnhancedWorldBossDefeatMessage(bossName, topDamagers);

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                sendEnhancedWorldBossDefeatToPlayer(player, announcement, topDamagers);
            } catch (Exception e) {
                // Fallback to basic notification
                sendWorldBossDefeatToPlayer(player, bossName, topDamagers);
            }
        }
    }

    /**
     * Sends a custom notification for special events with enhanced features.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public void sendCustomNotification(Player player, String title, String message, org.bukkit.Sound sound) {
        if (player == null || !player.isOnline()) return;

        try {
            Component titleComponent = Component.text("✦ " + title + " ✦", NamedTextColor.GOLD, TextDecoration.BOLD);
            Component messageComponent = LEGACY_SERIALIZER.deserialize(message);

            // Enhanced title with subtitle support
            Title enhancedTitle = Title.title(
                    titleComponent,
                    messageComponent,
                    Title.Times.times(
                            Duration.ofMillis(500),
                            Duration.ofMillis(3000),
                            Duration.ofMillis(500)
                    )
            );

            player.showTitle(enhancedTitle);

            // Also send to chat with enhanced formatting
            player.sendMessage(Component.empty());
            player.sendMessage(centerComponent(titleComponent));
            player.sendMessage(centerComponent(messageComponent));
            player.sendMessage(Component.empty());

            if (sound != null) {
                // Use Adventure Sound.sound() method which accepts Bukkit Sound directly
                try {
                    Sound adventureSound = Sound.sound(sound, Sound.Source.MASTER, DEFAULT_VOLUME, DEFAULT_VOLUME);
                    player.playSound(adventureSound);
                } catch (Exception e) {
                    // Fallback to direct Bukkit sound if Adventure conversion fails
                    player.playSound(player.getLocation(), sound, DEFAULT_VOLUME, DEFAULT_VOLUME);
                }
            }

        } catch (Exception e) {
            // Fallback to basic notification
            Component titleComponent = Component.text("✦ " + title + " ✦", NamedTextColor.GOLD);
            player.sendMessage(Component.empty());
            player.sendMessage(centerComponent(titleComponent));
            player.sendMessage(centerComponent(Component.text(message)));
            player.sendMessage(Component.empty());

            if (sound != null) {
                player.playSound(player.getLocation(), sound, DEFAULT_VOLUME, DEFAULT_VOLUME);
            }
        }
    }

    /**
     * Gets enhanced notification statistics for debugging.
     * BACKWARDS COMPATIBLE - maintains exact same method signature.
     */
    public Map<String, Object> getNotificationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rarityTypes", RARITY_COLORS.size());
        stats.put("soundMappings", RARITY_SOUNDS.size());
        stats.put("dropTypes", DROP_TYPE_SYMBOLS.size());
        stats.put("symbolMappings", RARITY_SYMBOLS.size());
        stats.put("tierColors", TIER_COLORS.size());
        stats.put("activeEffectTasks", activeEffectTasks.size());
        stats.put("trackedPlayers", lastNotificationTime.size());
        stats.put("adventureApiEnabled", true);
        stats.put("noDeprecatedMethods", true);
        stats.put("paperVersion", Bukkit.getVersion());
        return stats;
    }

    // ===== ENHANCED HELPER METHODS =====

    /**
     * Rate limiting check
     */
    private boolean canSendNotification(Player player) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastNotificationTime.get(player.getUniqueId());

        if (lastTime != null && (currentTime - lastTime) < 50) { // 50ms cooldown
            return false;
        }

        lastNotificationTime.put(player.getUniqueId(), currentTime);
        return true;
    }

    /**
     * Enhanced notification validation
     */
    private boolean isValidNotificationTarget(Player player, ItemStack item) {
        return player != null && item != null && player.isOnline() &&
                item.getType() != org.bukkit.Material.AIR && !player.isDead();
    }

    /**
     * Enhanced error handling
     */
    private void handleNotificationError(Exception e, String notificationType) {
        YakRealms.getInstance().getLogger().warning("Enhanced notification error (" + notificationType + "): " + e.getMessage());
        if (YakRealms.getInstance().getLogger().isLoggable(java.util.logging.Level.FINE)) {
            e.printStackTrace();
        }
    }

    /**
     * Enhanced source name with tier-based coloring and interactive features
     */
    private Component getEnhancedSourceName(LivingEntity source) {
        if (source == null) {
            return Component.text("Unknown Source", NamedTextColor.GRAY);
        }

        int tier = getEnhancedMobTier(source);
        TextColor tierColor = TIER_COLORS.getOrDefault(tier, NamedTextColor.WHITE);
        String sourceName = getEnhancedProperMobName(source);

        Component baseComponent = Component.text(sourceName, tierColor);

        // Add tier indicator if tier > 1
        if (tier > 1) {
            baseComponent = baseComponent.append(Component.text(" [T" + tier + "]", NamedTextColor.GRAY));
        }

        // Add interactive hover for mob info
        Component hoverText = buildEnhancedMobHoverText(source, tier);
        return baseComponent.hoverEvent(HoverEvent.showText(hoverText));
    }

    /**
     * Enhanced mob tier detection
     */
    private int getEnhancedMobTier(LivingEntity source) {
        try {
            // Try MobManager first
            if (MobManager.getInstance() != null) {
                CustomMob customMob = MobManager.getInstance().getCustomMob(source);
                if (customMob != null) {
                    return customMob.getTier();
                }
            }

            // Check metadata
            if (source.hasMetadata("tier")) {
                return source.getMetadata("tier").get(0).asInt();
            }
            if (source.hasMetadata("equipTier")) {
                return source.getMetadata("equipTier").get(0).asInt();
            }

            // Fallback based on entity type and equipment
            return estimateTierFromEntity(source);

        } catch (Exception e) {
            return 1; // Safe fallback
        }
    }

    /**
     * Enhanced mob name extraction
     */
    private String getEnhancedProperMobName(LivingEntity source) {
        try {
            // Try custom mob name first
            String customMobName = getEnhancedCustomMobName(source);
            if (customMobName != null && !customMobName.trim().isEmpty()) {
                return customMobName;
            }

            // Try custom name without health bars
            String customName = source.getCustomName();
            if (customName != null && !customName.contains("❤") && !customName.contains("♥")) {
                String cleanName = ChatColor.stripColor(customName).trim();
                if (!cleanName.isEmpty() && !cleanName.matches(".*\\d+/\\d+.*")) {
                    return cleanName;
                }
            }

            // Format entity type name
            return formatEnhancedEntityTypeName(source.getType().name());

        } catch (Exception e) {
            return formatEnhancedEntityTypeName(source.getType().name());
        }
    }

    /**
     * Enhanced custom mob name extraction
     */
    private String getEnhancedCustomMobName(LivingEntity source) {
        try {
            if (source.hasMetadata("type")) {
                MobManager mobManager = MobManager.getInstance();
                if (mobManager != null) {
                    CustomMob customMob = mobManager.getCustomMob(source);
                    if (customMob != null && customMob.getOriginalName() != null) {
                        return ChatColor.stripColor(customMob.getOriginalName());
                    }
                }
            }

            // Check other metadata keys
            String[] metadataKeys = {"customName", "originalName", "mobType", "name"};
            for (String key : metadataKeys) {
                if (source.hasMetadata(key)) {
                    String value = source.getMetadata(key).get(0).asString();
                    if (value != null && !value.trim().isEmpty()) {
                        return ChatColor.stripColor(value).trim();
                    }
                }
            }

        } catch (Exception e) {
            // Ignore errors and use fallback
        }
        return null;
    }

    /**
     * Enhanced entity type name formatting
     */
    private String formatEnhancedEntityTypeName(String entityTypeName) {
        return Arrays.stream(entityTypeName.toLowerCase().replace("_", " ").split(" "))
                .map(word -> word.isEmpty() ? "" :
                        Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    /**
     * Enhanced tier estimation from entity
     */
    private int estimateTierFromEntity(LivingEntity entity) {
        // Check equipment for tier hints
        if (entity.getEquipment() != null) {
            ItemStack mainHand = entity.getEquipment().getItemInMainHand();
            if (mainHand != null && mainHand.getType() != org.bukkit.Material.AIR) {
                String material = mainHand.getType().name();
                if (material.contains("NETHERITE")) return 6;
                if (material.contains("DIAMOND")) return 4;
                if (material.contains("IRON")) return 3;
                if (material.contains("STONE")) return 2;
            }
        }

        // Entity type based estimation
        return switch (entity.getType()) {
            case WITHER, ENDER_DRAGON, WARDEN -> 6;
            case WITHER_SKELETON, EVOKER -> 5;
            case VINDICATOR, WITCH -> 4;
            case ENDERMAN, CREEPER -> 3;
            case ZOMBIE, SKELETON, SPIDER -> 2;
            default -> 1;
        };
    }

    /**
     * Enhanced sound playing with Adventure API - no deprecated methods
     */
    private void playEnhancedSound(Player player, Key soundKey, float volume, float pitch) {
        try {
            Sound adventureSound = Sound.sound(soundKey, Sound.Source.MASTER, volume, pitch);
            player.playSound(adventureSound);
        } catch (Exception e) {

                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, volume, pitch);

        }
    }

    /**
     * Enhanced sound playing with Bukkit Sound - no deprecated methods
     */
    private void playEnhancedSound(Player player, org.bukkit.Sound bukkitSound, float volume, float pitch) {
        try {
            Sound adventureSound = Sound.sound(bukkitSound, Sound.Source.MASTER, volume, pitch);
            player.playSound(adventureSound);
        } catch (Exception e) {
            // Fallback to direct Bukkit sound
            player.playSound(player.getLocation(), bukkitSound, volume, pitch);
        }
    }

    /**
     * Enhanced particle effects
     */
    private void spawnEnhancedParticleEffect(Location location, String effectType, int intensity) {
        if (activeEffectTasks.size() > 20) return; // Prevent overload

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Particle particle = getParticleForEffect(effectType);
                    int count = Math.max(5, Math.min(50, intensity * 2));

                    location.getWorld().spawnParticle(
                            particle,
                            location.add(0, PARTICLE_Y_OFFSET, 0),
                            count,
                            PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPREAD,
                            PARTICLE_SPEED
                    );
                } catch (Exception e) {
                    // Silently handle particle errors
                }
                activeEffectTasks.remove(this);
            }
        }.runTaskLater(YakRealms.getInstance(), 1);

        activeEffectTasks.add(task);
    }

    /**
     * Get appropriate particle for effect type
     */
    private Particle getParticleForEffect(String effectType) {
        return switch (effectType.toLowerCase()) {
            case "gem" -> Particle.ANGRY_VILLAGER;
            case "crate" -> Particle.ENCHANT;
            case "book" -> Particle.NOTE;
            case "rare" -> Particle.CRIT;
            case "unique", "legendary" -> Particle.TOTEM_OF_UNDYING;
            case "boss" -> Particle.DRAGON_BREATH;
            default -> Particle.HAPPY_VILLAGER;
        };
    }

    /**
     * Enhanced interactive chat notification
     */
    private void sendEnhancedInteractiveChatNotification(Player player, DropNotificationData data, ItemStack item) {
        try {
            if (data.isBossLoot) {
                sendEnhancedBossLootNotification(player, data, item);
            } else {
                sendEnhancedRegularDropNotification(player, data, item);
            }
        } catch (Exception e) {
            // Fallback to basic notification
            sendBasicNotification(player, item, data.source, data.isBossLoot);
        }
    }

    /**
     * Enhanced boss loot notification
     */
    private void sendEnhancedBossLootNotification(Player player, DropNotificationData data, ItemStack item) {
        TextColor[] colors = RARITY_COLORS.getOrDefault(data.rarity, RARITY_COLORS.get("common"));
        String symbol = RARITY_SYMBOLS.getOrDefault(data.rarity, "◆");

        Component bossMessage = Component.text("👑 ", NamedTextColor.GOLD)
                .append(Component.text("You received ", NamedTextColor.YELLOW))
                .append(Component.text(symbol + " ", colors[0]))
                .append(data.itemName.colorIfAbsent(colors[0]))
                .append(Component.text(" from the ", NamedTextColor.YELLOW))
                .append(Component.text("Elite Boss", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("! 👑", NamedTextColor.GOLD));

        Component enhancedMessage = centerComponent(bossMessage)
                .hoverEvent(item.asHoverEvent())
                .clickEvent(ClickEvent.runCommand("/item info"));

        player.sendMessage(enhancedMessage);

        // Enhanced boss title
        Title bossTitle = Title.title(
                Component.text("BOSS LOOT!", NamedTextColor.GOLD, TextDecoration.BOLD),
                data.itemName.colorIfAbsent(colors[0]),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500))
        );
        player.showTitle(bossTitle);
    }

    /**
     * Enhanced regular drop notification with MiniMessage gradients for unique+ items
     */
    private void sendEnhancedRegularDropNotification(Player player, DropNotificationData data, ItemStack item) {
        TextColor[] colors = RARITY_COLORS.getOrDefault(data.rarity, RARITY_COLORS.get("common"));
        String typeSymbol = DROP_TYPE_SYMBOLS.getOrDefault(data.dropType, "");
        String raritySymbol = RARITY_SYMBOLS.getOrDefault(data.rarity, "◆");

        Component message = Component.text("➤ ", colors[1])
                .append(data.sourceName.colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" dropped ", NamedTextColor.YELLOW))
                .append(Component.text(typeSymbol + raritySymbol + " ", colors[0]));

        // Apply gradient for unique rarity and above
        if (isHighRarity(data.rarity)) {
            Component gradientItemName = MessageUtils.createRarityMessage(data.rarity, 
                MessageUtils.toLegacy(data.itemName));
            message = message.append(gradientItemName);
        } else {
            message = message.append(data.itemName.colorIfAbsent(colors[0]));
        }

        message = message.hoverEvent(item.asHoverEvent())
                .clickEvent(ClickEvent.suggestCommand("/item pickup"));

        player.sendMessage(message);
    }
    
    /**
     * Check if rarity qualifies for gradient treatment
     */
    private boolean isHighRarity(String rarity) {
        return "unique".equals(rarity) || "legendary".equals(rarity) || 
               "mythic".equals(rarity) || "ancient".equals(rarity);
    }

    /**
     * Enhanced notification effects
     */
    private void playEnhancedNotificationEffects(Player player, DropNotificationData data) {
        // Play sound using Bukkit Sound directly to avoid deprecation
        org.bukkit.Sound[] sounds = RARITY_SOUNDS.getOrDefault(data.rarity, RARITY_SOUNDS.get("common"));
        org.bukkit.Sound selectedSound = sounds[ThreadLocalRandom.current().nextInt(sounds.length)];

        float volume = data.isHighValue ? HIGH_VALUE_VOLUME : DEFAULT_VOLUME;
        float pitch = calculateEnhancedPitch(data.isBossLoot, data.rarity);

        playEnhancedSound(player, selectedSound, volume, pitch);

        // Spawn particles
        String effectType = data.isHighValue ? data.rarity : data.dropType;
        spawnEnhancedParticleEffect(player.getLocation(), effectType, data.isHighValue ? 3 : 1);
    }

    /**
     * Enhanced pitch calculation
     */
    private float calculateEnhancedPitch(boolean isBossLoot, String rarity) {
        if (isBossLoot) return BOSS_PITCH;

        float basePitch = switch (rarity) {
            case "unique", "legendary" -> 1.2f;
            case "rare" -> 1.1f;
            case "uncommon" -> 1.0f;
            default -> 0.9f;
        };

        return basePitch + (ThreadLocalRandom.current().nextFloat() * PITCH_VARIANCE - PITCH_OFFSET);
    }

    /**
     * Enhanced special effects for high-value drops
     */
    private void sendEnhancedSpecialEffects(Player player, DropNotificationData data) {
        // Multiple particle bursts
        Location playerLoc = player.getLocation();

        spawnEnhancedParticleEffect(playerLoc, "rare", 5);

        BukkitTask delayedEffect = new BukkitRunnable() {
            @Override
            public void run() {
                spawnEnhancedParticleEffect(playerLoc, "legendary", 3);
                playEnhancedSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                activeEffectTasks.remove(this);
            }
        }.runTaskLater(YakRealms.getInstance(), SPECIAL_EFFECT_DELAY_TICKS);

        activeEffectTasks.add(delayedEffect);
    }

    /**
     * Enhanced gem hover text
     */
    private Component buildEnhancedGemHoverText(int amount) {
        return Component.join(JoinConfiguration.newlines(),
                Component.text("💎 Gems: ", NamedTextColor.GREEN).append(Component.text(amount, NamedTextColor.YELLOW)),
                Component.text("Currency used for trading", NamedTextColor.GRAY),
                Component.text("and purchasing items", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to check balance", NamedTextColor.AQUA, TextDecoration.ITALIC)
        );
    }

    /**
     * Enhanced crate name with tier information
     */
    private Component getEnhancedCrateName(ItemStack crate, int tier, TextColor tierColor) {
        if (crate.hasItemMeta() && crate.getItemMeta().hasDisplayName()) {
            return LEGACY_SERIALIZER.deserialize(crate.getItemMeta().getDisplayName());
        }
        return Component.text("Tier " + tier + " Crate", tierColor);
    }

    /**
     * Enhanced book name with destination highlighting
     */
    private Component getEnhancedBookName(ItemStack book, String destinationName) {
        if (book.hasItemMeta() && book.getItemMeta().hasDisplayName()) {
            return LEGACY_SERIALIZER.deserialize(book.getItemMeta().getDisplayName());
        }
        return Component.text(destinationName + " Teleport Book", NamedTextColor.AQUA);
    }

    /**
     * Enhanced book hover text
     */
    private Component buildEnhancedBookHoverText(String destinationName) {
        return Component.join(JoinConfiguration.newlines(),
                Component.text("📖 Teleport Book", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("Destination: ", NamedTextColor.GRAY).append(Component.text(destinationName, NamedTextColor.WHITE)),
                Component.text("Right-click to teleport", NamedTextColor.GREEN),
                Component.empty(),
                Component.text("Click to suggest teleport command", NamedTextColor.AQUA, TextDecoration.ITALIC)
        );
    }

    /**
     * Enhanced mob hover text
     */
    private Component buildEnhancedMobHoverText(LivingEntity source, int tier) {
        String mobName = getEnhancedProperMobName(source);
        TextColor tierColor = TIER_COLORS.getOrDefault(tier, NamedTextColor.WHITE);

        return Component.join(JoinConfiguration.newlines(),
                Component.text(mobName, tierColor, TextDecoration.BOLD),
                Component.text("Tier: ", NamedTextColor.GRAY).append(Component.text(tier, tierColor)),
                Component.text("Type: ", NamedTextColor.GRAY).append(Component.text(source.getType().name(), NamedTextColor.WHITE)),
                Component.text("Health: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f/%.1f", source.getHealth(), source.getMaxHealth()), NamedTextColor.RED))
        );
    }

    /**
     * Enhanced crate sound effects - no deprecated methods
     */
    private void playEnhancedCrateSound(Player player, int tier) {
        org.bukkit.Sound crateSound = tier >= 4 ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL : org.bukkit.Sound.ENTITY_ITEM_PICKUP;
        float pitch = DEFAULT_VOLUME + (tier * 0.1f);
        playEnhancedSound(player, crateSound, DEFAULT_VOLUME, pitch);
    }

    // ===== ENHANCED ANNOUNCEMENT METHODS =====

    /**
     * Enhanced buff activation message building
     */
    private Component buildEnhancedBuffActivationMessage(String activatorName, int buffRate, int eliteBuffRate, int durationMinutes) {
        return Component.join(JoinConfiguration.newlines(),
                Component.empty(),
                centerComponent(Component.text("✦ ", NamedTextColor.GOLD)
                        .append(Component.text("LOOT BUFF ACTIVATED", NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text(" ✦", NamedTextColor.GOLD))),
                centerComponent(Component.text(activatorName, NamedTextColor.AQUA)
                        .append(Component.text(" activated a server-wide loot buff!", NamedTextColor.YELLOW))),
                Component.empty(),
                Component.text("    ◆ ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("Normal drop rates: ", NamedTextColor.WHITE))
                        .append(Component.text("+" + buffRate + "%", NamedTextColor.GREEN)),
                Component.text("    ◆ ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("Elite drop rates: ", NamedTextColor.WHITE))
                        .append(Component.text("+" + eliteBuffRate + "%", NamedTextColor.GREEN)),
                Component.text("    ◆ ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("Duration: ", NamedTextColor.WHITE))
                        .append(Component.text(durationMinutes + " minutes", NamedTextColor.YELLOW)),
                Component.empty(),
                centerComponent(Component.text("Thank you for supporting the server!", NamedTextColor.GREEN)),
                Component.empty()
        );
    }

    /**
     * Enhanced buff end message building
     */
    private Component buildEnhancedBuffEndMessage(String activatorName, int improvedDrops) {
        return Component.join(JoinConfiguration.newlines(),
                Component.empty(),
                centerComponent(Component.text("✦ ", NamedTextColor.RED)
                        .append(Component.text("LOOT BUFF EXPIRED", NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text(" ✦", NamedTextColor.RED))),
                centerComponent(Component.text(activatorName, NamedTextColor.AQUA)
                        .append(Component.text("'s loot buff has ended!", NamedTextColor.YELLOW))),
                centerComponent(Component.text("◆ ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("Enhanced drops provided: ", NamedTextColor.GRAY))
                        .append(Component.text(improvedDrops, NamedTextColor.GREEN))),
                Component.empty()
        );
    }

    /**
     * Enhanced world boss defeat message building
     */
    private Component buildEnhancedWorldBossDefeatMessage(String bossName, List<Object[]> topDamagers) {
        List<Component> messageComponents = new ArrayList<>();

        messageComponents.add(Component.empty());
        messageComponents.add(centerComponent(Component.text("👑 ", NamedTextColor.GOLD)
                .append(Component.text("WORLD BOSS DEFEATED", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" 👑", NamedTextColor.GOLD))));
        messageComponents.add(centerComponent(Component.text(bossName, NamedTextColor.YELLOW)
                .append(Component.text(" has fallen!", NamedTextColor.WHITE))));

        if (!topDamagers.isEmpty()) {
            messageComponents.add(Component.empty());
            messageComponents.add(Component.text("⚔ ", NamedTextColor.GOLD)
                    .append(Component.text("Top Contributors:", NamedTextColor.YELLOW)));

            for (int i = 0; i < Math.min(3, topDamagers.size()); i++) {
                Object[] entry = topDamagers.get(i);
                messageComponents.add(Component.text("  ")
                        .append(getEnhancedRankSymbol(i))
                        .append(Component.text(" " + entry[0], NamedTextColor.AQUA))
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(String.format("%,d", entry[1]) + " damage", NamedTextColor.RED)));
            }
        }

        messageComponents.add(Component.empty());
        messageComponents.add(centerComponent(Component.text("Legendary loot has been distributed!", NamedTextColor.YELLOW)));
        messageComponents.add(Component.empty());

        return Component.join(JoinConfiguration.newlines(), messageComponents);
    }

    /**
     * Enhanced rank symbols
     */
    private Component getEnhancedRankSymbol(int position) {
        return switch (position) {
            case 0 -> Component.text("🥇", NamedTextColor.GOLD);
            case 1 -> Component.text("🥈", NamedTextColor.GRAY);
            case 2 -> Component.text("🥉", NamedTextColor.YELLOW);
            default -> Component.text((position + 1) + ".", NamedTextColor.WHITE);
        };
    }

    /**
     * Enhanced message sending methods
     */
    private void sendEnhancedBuffActivationToPlayer(Player player, Component announcement) {
        player.sendMessage(announcement);
        playEnhancedSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, DEFAULT_VOLUME, 0.5f);
        spawnEnhancedParticleEffect(player.getLocation(), "buff", 2);
    }

    private void sendEnhancedBuffEndToPlayer(Player player, Component announcement) {
        player.sendMessage(announcement);
        playEnhancedSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, DEFAULT_VOLUME, DEFAULT_VOLUME);
    }

    private void sendEnhancedWorldBossDefeatToPlayer(Player player, Component announcement, List<Object[]> topDamagers) {
        player.sendMessage(announcement);
        spawnEnhancedParticleEffect(player.getLocation(), "boss", 5);

        // Enhanced title for boss defeat
        Title bossTitle = Title.title(
                Component.text("WORLD BOSS DEFEATED!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Legendary loot distributed", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(1000), Duration.ofMillis(3000), Duration.ofMillis(1000))
        );
        player.showTitle(bossTitle);
    }

    // ===== MAINTENANCE AND UTILITY METHODS =====

    /**
     * Periodic maintenance task
     */
    private void performMaintenance() {
        try {
            // Clean up expired notification times
            long cutoff = System.currentTimeMillis() - 300000; // 5 minutes
            lastNotificationTime.entrySet().removeIf(entry -> entry.getValue() < cutoff);

            // Clean up completed effect tasks
            activeEffectTasks.removeIf(task -> task.isCancelled());

        } catch (Exception e) {
            // Silently handle maintenance errors
        }
    }

    /**
     * Enhanced text centering utility
     */
    private Component centerComponent(Component component) {
        try {
            String plainText = PLAIN_SERIALIZER.serialize(component);
            int messageLength = plainText.length();
            int padding = Math.max(0, (55 - messageLength) / 2);
            return Component.text(" ".repeat(padding)).append(component);
        } catch (Exception e) {
            return component; // Return original if centering fails
        }
    }

    /**
     * Enhanced drop notification data analysis
     */
    private DropNotificationData analyzeDropNotification(ItemStack item, LivingEntity source, boolean isBossLoot) {
        Component itemName = getEnhancedItemName(item);
        Component sourceName = getEnhancedSourceName(source);
        String rarity = detectEnhancedItemRarity(item);
        String dropType = detectEnhancedDropType(item);
        boolean isHighValue = isEnhancedHighValueDrop(rarity, isBossLoot);

        return new DropNotificationData(itemName, sourceName, rarity, dropType, isBossLoot, isHighValue, source);
    }

    /**
     * Enhanced item name extraction
     */
    private Component getEnhancedItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return LEGACY_SERIALIZER.deserialize(item.getItemMeta().getDisplayName());
        }
        return Component.text(formatEnhancedEntityTypeName(item.getType().name()));
    }

    /**
     * Enhanced rarity detection
     */
    private String detectEnhancedItemRarity(ItemStack item) {
        if (!item.hasItemMeta()) {
            return "common";
        }

        ItemMeta meta = item.getItemMeta();

        // Check lore first
        String loreRarity = detectEnhancedRarityFromLore(meta);
        if (loreRarity != null) {
            return loreRarity;
        }

        // Check display name colors
        return detectEnhancedRarityFromName(meta);
    }

    /**
     * Enhanced lore rarity detection
     */
    private String detectEnhancedRarityFromLore(ItemMeta meta) {
        if (!meta.hasLore()) {
            return null;
        }

        List<String> lore = meta.getLore();
        if (lore.isEmpty()) {
            return null;
        }

        String lastLine = ChatColor.stripColor(lore.get(lore.size() - 1)).toLowerCase();

        if (UNIQUE_PATTERN.matcher(lastLine).find()) return "unique";
        if (RARE_PATTERN.matcher(lastLine).find()) return "rare";
        if (UNCOMMON_PATTERN.matcher(lastLine).find()) return "uncommon";
        if (COMMON_PATTERN.matcher(lastLine).find()) return "common";

        // Check for additional rarity indicators
        if (lastLine.contains("mythic")) return "mythic";
        if (lastLine.contains("ancient")) return "ancient";

        return null;
    }

    /**
     * Enhanced name rarity detection with gradient support
     */
    private String detectEnhancedRarityFromName(ItemMeta meta) {
        if (!meta.hasDisplayName()) {
            return "common";
        }

        String displayName = meta.getDisplayName();
        
        // Check for gradient patterns first (T6/Unique gradients)
        if (containsGradientPattern(displayName)) {
            if (displayName.toLowerCase().contains("tier") || displayName.toLowerCase().contains("legendary")) {
                return "unique"; // T6 items are unique rarity
            }
            return "unique";
        }
        
        // Fallback to color code detection
        if (displayName.contains("§d") || displayName.contains("§5")) return "mythic";
        if (displayName.contains("§e") || displayName.contains("§6")) return "unique";
        if (displayName.contains("§b") || displayName.contains("§3")) return "rare";
        if (displayName.contains("§a") || displayName.contains("§2")) return "uncommon";
        if (displayName.toLowerCase().contains("netherite")) return "legendary";

        return "common";
    }
    
    /**
     * Check if display name contains gradient patterns (hex color codes)
     */
    private boolean containsGradientPattern(String displayName) {
        // Look for hex color patterns indicating gradients
        return displayName.contains("§x") || displayName.matches(".*§[0-9a-fA-F]{6}.*");
    }

    /**
     * Enhanced drop type detection
     */
    private String detectEnhancedDropType(ItemStack item) {
        String materialName = item.getType().name().toLowerCase();

        if (isEnhancedWeapon(materialName)) return "weapon";
        if (isEnhancedArmor(materialName)) return "armor";
        if (isEnhancedGem(materialName)) return "gem";
        if (isEnhancedScroll(materialName)) return "scroll";
        if (isEnhancedBook(materialName)) return "book";
        if (isEnhancedCrate(materialName)) return "crate";
        if (isEnhancedConsumable(materialName)) return "consumable";
        if (isEnhancedTool(materialName)) return "tool";

        return "common";
    }

    // Enhanced material type checking methods
    private boolean isEnhancedWeapon(String name) {
        return name.contains("sword") || name.contains("axe") || name.contains("pickaxe") ||
                name.contains("shovel") || name.contains("hoe") || name.contains("trident") ||
                name.contains("bow") || name.contains("crossbow");
    }

    private boolean isEnhancedArmor(String name) {
        return name.contains("helmet") || name.contains("chestplate") ||
                name.contains("leggings") || name.contains("boots") || name.contains("elytra");
    }

    private boolean isEnhancedGem(String name) {
        return name.contains("emerald") || name.contains("diamond") || name.contains("ruby") ||
                name.contains("sapphire") || name.contains("crystal");
    }

    private boolean isEnhancedScroll(String name) {
        return name.contains("paper") || name.contains("map") || name.contains("scroll");
    }

    private boolean isEnhancedBook(String name) {
        return name.contains("book") || name.contains("tome");
    }

    private boolean isEnhancedCrate(String name) {
        return name.contains("chest") || name.contains("shulker_box") || name.contains("barrel") ||
                name.contains("crate") || name.contains("container");
    }

    private boolean isEnhancedConsumable(String name) {
        return name.contains("potion") || name.contains("food") || name.contains("bread") ||
                name.contains("apple") || name.contains("stew") || name.contains("soup");
    }

    private boolean isEnhancedTool(String name) {
        return name.contains("shears") || name.contains("flint_and_steel") ||
                name.contains("compass") || name.contains("clock");
    }

    /**
     * Enhanced high value drop detection
     */
    private boolean isEnhancedHighValueDrop(String rarity, boolean isBossLoot) {
        return isBossLoot || "unique".equals(rarity) || "legendary".equals(rarity) ||
                "mythic".equals(rarity) || "ancient".equals(rarity);
    }

    // ===== FALLBACK METHODS (BACKWARDS COMPATIBILITY) =====

    private void sendBasicGemNotification(Player player, LivingEntity source, int amount) {
        Component sourceName = getEnhancedSourceName(source);
        player.sendMessage(Component.text("➤ ", NamedTextColor.GRAY)
                .append(sourceName)
                .append(Component.text(" dropped ", NamedTextColor.YELLOW))
                .append(Component.text("💎 " + amount + " Gems", NamedTextColor.GREEN)));
        playEnhancedSound(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    private void sendBasicCrateNotification(Player player, ItemStack crate, LivingEntity source, int tier) {
        Component sourceName = getEnhancedSourceName(source);
        Component crateName = getEnhancedCrateName(crate, tier, TIER_COLORS.getOrDefault(tier, NamedTextColor.WHITE));
        player.sendMessage(Component.text("➤ ", NamedTextColor.GRAY)
                .append(sourceName)
                .append(Component.text(" dropped 📦 ", NamedTextColor.YELLOW))
                .append(crateName));
        playEnhancedCrateSound(player, tier);
    }

    private void sendBasicBookNotification(Player player, ItemStack book, LivingEntity source, String destinationName) {
        Component sourceName = getEnhancedSourceName(source);
        Component bookName = getEnhancedBookName(book, destinationName);
        player.sendMessage(Component.text("➤ ", NamedTextColor.GRAY)
                .append(sourceName)
                .append(Component.text(" dropped 📖 ", NamedTextColor.YELLOW))
                .append(bookName));
        playEnhancedSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    private void sendBasicNotification(Player player, ItemStack item, LivingEntity source, boolean isBossLoot) {
        Component itemName = getEnhancedItemName(item);
        Component message;

        if (isBossLoot) {
            message = centerComponent(Component.text("➤ ", NamedTextColor.RED)
                    .append(Component.text("You received ", NamedTextColor.YELLOW))
                    .append(itemName)
                    .append(Component.text(" from the World Boss", NamedTextColor.YELLOW)));
        } else {
            Component sourceName = getEnhancedSourceName(source);
            message = Component.text("➤ ", NamedTextColor.GRAY)
                    .append(sourceName)
                    .append(Component.text(" dropped ", NamedTextColor.YELLOW))
                    .append(itemName);
        }

        player.sendMessage(message);
        playEnhancedSound(player, org.bukkit.Sound.ENTITY_ITEM_PICKUP, DEFAULT_VOLUME, DEFAULT_VOLUME);
    }

    // Original fallback methods for backwards compatibility
    private void sendBuffActivationToPlayer(Player player, String activatorName, int buffRate, int eliteBuffRate, int durationMinutes) {
        Component message = buildEnhancedBuffActivationMessage(activatorName, buffRate, eliteBuffRate, durationMinutes);
        player.sendMessage(message);
        playEnhancedSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, DEFAULT_VOLUME, 0.5f);
    }

    private void sendBuffEndToPlayer(Player player, String playerName, int improvedDrops) {
        Component message = buildEnhancedBuffEndMessage(playerName, improvedDrops);
        player.sendMessage(message);
        playEnhancedSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, DEFAULT_VOLUME, DEFAULT_VOLUME);
    }

    private void sendWorldBossDefeatToPlayer(Player player, String bossName, List<Object[]> topDamagers) {
        Component message = buildEnhancedWorldBossDefeatMessage(bossName, topDamagers);
        player.sendMessage(message);
        playEnhancedSound(player, org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH, DEFAULT_VOLUME, DEFAULT_VOLUME);
    }

    /**
     * Enhanced notification data class
     */
    private static class DropNotificationData {
        final Component itemName;
        final Component sourceName;
        final String rarity;
        final String dropType;
        final boolean isBossLoot;
        final boolean isHighValue;
        final LivingEntity source;

        DropNotificationData(Component itemName, Component sourceName, String rarity, String dropType,
                             boolean isBossLoot, boolean isHighValue, LivingEntity source) {
            this.itemName = itemName;
            this.sourceName = sourceName;
            this.rarity = rarity;
            this.dropType = dropType;
            this.isBossLoot = isBossLoot;
            this.isHighValue = isHighValue;
            this.source = source;
        }

        boolean isHighValue() {
            return isHighValue;
        }
    }
}