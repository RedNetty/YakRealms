package com.rednetty.server.utils.messaging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/**
 * Centralized utility class for MiniMessage formatting and gradients.
 * Provides consistent styling across the YakRealms plugin.
 */
public class MessageUtils {
    
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    
    // Predefined gradient colors for consistent theming
    public static final class Gradients {
        public static final String UNIQUE_RARITY = "<gradient:#ff6b35:#f7931e:#ffd23f>";
        public static final String LEGENDARY = "<gradient:#ffd700:#ffb347:#ff8c00>";
        public static final String EPIC = "<gradient:#9d4edd:#c77dff:#e0aaff>";
        public static final String RARE = "<gradient:#3498db:#5dade2:#85c1e9>";
        public static final String UNCOMMON = "<gradient:#2ecc71:#58d68d:#82e0aa>";
        public static final String COMMON = "<gradient:#95a5a6:#bdc3c7:#d5dbdb>";
        
        // Special effect gradients
        public static final String FIRE = "<gradient:#ff4500:#ff6347:#ffa500>";
        public static final String ICE = "<gradient:#87ceeb:#b0e0e6:#e0f6ff>";
        public static final String LIGHTNING = "<gradient:#ffff00:#ffd700:#fff8dc>";
        public static final String NATURE = "<gradient:#228b22:#32cd32:#90ee90>";
        public static final String SHADOW = "<gradient:#2f2f2f:#696969:#a9a9a9>";
        
        // UI gradients
        public static final String WELCOME = "<gradient:#ff6b35:#f7931e:#ffd23f>";
        public static final String TAB_HEADER = "<gradient:#4a90e2:#7bb3f0:#b3d9ff>";
        public static final String ELITE_BRUTE = "<gradient:#dc143c:#ff6347:#ffa07a>";
        public static final String ELITE_ELEMENTALIST = "<gradient:#ff4500:#ffa500:#ffff00>";
        public static final String ELITE_ASSASSIN = "<gradient:#2f2f2f:#696969:#a9a9a9>";
        
        // Achievement gradients
        public static final String SUCCESS = "<gradient:#00ff00:#32cd32:#90ee90>";
        public static final String WARNING = "<gradient:#ff8c00:#ffa500>#ffd700>";
        public static final String ERROR = "<gradient:#ff0000:#ff6347:#ffa07a>";
        public static final String INFO = "<gradient:#4169e1:#6495ed:#b0c4de>";
    }
    
    /**
     * Parse MiniMessage text into a Component
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(text);
    }
    
    /**
     * Parse MiniMessage text with gradient and return as Component
     */
    public static Component parseWithGradient(String gradient, String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(gradient + text + "</gradient>");
    }
    
    /**
     * Convert legacy color codes to MiniMessage format
     */
    public static Component fromLegacy(String legacyText) {
        if (legacyText == null || legacyText.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(legacyText);
    }
    
    /**
     * Convert Component to legacy string (for backwards compatibility)
     */
    public static String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY_SERIALIZER.serialize(component);
    }
    
    /**
     * Send a MiniMessage formatted message to a player
     */
    public static void send(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        player.sendMessage(parse(message));
    }
    
    /**
     * Send a gradient message to a player
     */
    public static void sendWithGradient(Player player, String gradient, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        player.sendMessage(parseWithGradient(gradient, message));
    }
    
    /**
     * Send a title with MiniMessage formatting
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;
        
        Component titleComponent = title != null && !title.isEmpty() ? parse(title) : Component.empty();
        Component subtitleComponent = subtitle != null && !subtitle.isEmpty() ? parse(subtitle) : Component.empty();
        
        Title titleObj = Title.title(
            titleComponent,
            subtitleComponent,
            Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
            )
        );
        
        player.showTitle(titleObj);
    }
    
    /**
     * Send an action bar message with MiniMessage formatting
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        player.sendActionBar(parse(message));
    }
    
    /**
     * Create a rarity-based gradient message
     */
    public static Component createRarityMessage(String rarity, String text) {
        String gradient = switch (rarity.toUpperCase()) {
            case "UNIQUE" -> Gradients.UNIQUE_RARITY;
            case "LEGENDARY" -> Gradients.LEGENDARY;
            case "EPIC" -> Gradients.EPIC;
            case "RARE" -> Gradients.RARE;
            case "UNCOMMON" -> Gradients.UNCOMMON;
            case "COMMON" -> Gradients.COMMON;
            default -> "";
        };
        
        return gradient.isEmpty() ? parse(text) : parseWithGradient(gradient, text);
    }
    
    /**
     * Create an elite archetype gradient message
     */
    public static Component createEliteMessage(String archetype, String text) {
        String gradient = switch (archetype.toUpperCase()) {
            case "BRUTE" -> Gradients.ELITE_BRUTE;
            case "ELEMENTALIST" -> Gradients.ELITE_ELEMENTALIST;
            case "ASSASSIN" -> Gradients.ELITE_ASSASSIN;
            case "FIRE" -> Gradients.FIRE;
            case "ICE" -> Gradients.ICE;
            case "LIGHTNING" -> Gradients.LIGHTNING;
            default -> "";
        };
        
        return gradient.isEmpty() ? parse(text) : parseWithGradient(gradient, text);
    }
    
    /**
     * Create a status gradient message
     */
    public static Component createStatusMessage(String status, String text) {
        String gradient = switch (status.toUpperCase()) {
            case "SUCCESS" -> Gradients.SUCCESS;
            case "WARNING" -> Gradients.WARNING;
            case "ERROR" -> Gradients.ERROR;
            case "INFO" -> Gradients.INFO;
            default -> "";
        };
        
        return gradient.isEmpty() ? parse(text) : parseWithGradient(gradient, text);
    }
    
    /**
     * Create a welcome message with gradient
     */
    public static Component createWelcomeMessage(String playerName, String serverName) {
        return parseWithGradient(
            Gradients.WELCOME,
            "Welcome to " + serverName + ", " + playerName + "!"
        );
    }
    
    /**
     * Create a tab header with gradient
     */
    public static Component createTabHeader(String text) {
        return parseWithGradient(Gradients.TAB_HEADER, text);
    }
    
    /**
     * Send a list of messages to a player
     */
    public static void sendMessages(Player player, List<String> messages) {
        if (player == null || messages == null || messages.isEmpty()) {
            return;
        }
        
        for (String message : messages) {
            send(player, message);
        }
    }
    
    /**
     * Create a centered message (for tab list headers/footers)
     */
    public static Component createCenteredMessage(String message, int lineLength) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        
        // Remove color codes for length calculation
        String plainMessage = LEGACY_SERIALIZER.serialize(parse(message));
        int messageLength = plainMessage.length();
        int padding = Math.max(0, (lineLength - messageLength) / 2);
        
        StringBuilder centered = new StringBuilder();
        for (int i = 0; i < padding; i++) {
            centered.append(" ");
        }
        centered.append(message);
        
        return parse(centered.toString());
    }
    
    /**
     * Create a progress bar with gradient
     */
    public static Component createProgressBar(double percentage, int length, String gradient) {
        if (percentage < 0) percentage = 0;
        if (percentage > 1) percentage = 1;
        
        int filled = (int) (length * percentage);
        int empty = length - filled;
        
        StringBuilder bar = new StringBuilder();
        bar.append(gradient);
        
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        bar.append("</gradient>");
        
        bar.append("<gray>");
        for (int i = 0; i < empty; i++) {
            bar.append("█");
        }
        bar.append("</gray>");
        
        return parse(bar.toString());
    }
    
    /**
     * Strip all formatting from a message
     */
    public static String stripFormatting(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        
        // Convert to component and back to plain text
        Component component = parse(message);
        return component.toString(); // This gives plain text representation
    }
}