package com.rednetty.server.utils.text;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for text formatting operations
 */
public class TextUtil {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final int MINECRAFT_CHAT_WIDTH = 320; // Width of the chat in pixels
    private static final int DEFAULT_CHAR_WIDTH = 6; // Width of a normal character in pixels
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final static int CENTER_PX = 154;

    /**
     * Private constructor to prevent instantiation
     */
    private TextUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Broadcast a centered message to all online players
     *
     * @param message The message to center and broadcast
     */
    public static void broadcastCentered(String message) {
        if (message == null || message.isEmpty()) return;

        String centeredMessage = getCenteredMessage(message);
        org.bukkit.Bukkit.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(centeredMessage));
    }

    /**
     * Broadcast multiple centered messages to all online players
     *
     * @param messages The messages to center and broadcast
     */
    public static void broadcastMultipleCentered(String... messages) {
        if (messages == null) return;

        List<String> centeredMessages = Arrays.stream(messages)
                .map(TextUtil::getCenteredMessage)
                .collect(java.util.stream.Collectors.toList());

        org.bukkit.Bukkit.getServer().getOnlinePlayers().forEach(p ->
                centeredMessages.forEach(p::sendMessage));
    }

    /**
     * Format a Minecraft item name to be more readable
     * Converts "DIAMOND_SWORD" to "Diamond Sword"
     *
     * @param itemName The raw item name (typically from Material.name())
     * @return The formatted item name
     */
    public static String formatItemName(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return "";
        }

        // Replace underscores with spaces
        String formattedName = itemName.replace('_', ' ');

        // Split into words and capitalize each word
        String[] words = formattedName.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;

            // Capitalize first letter, lowercase the rest
            result.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
        }

        return result.toString().trim();
    }

    /**
     * Generates padding spaces for centering.
     *
     * @param toCompensate The number of pixels to compensate.
     * @return A string of spaces for padding.
     */
    private static String getPaddingSpaces(int toCompensate) {
        StringBuilder sb = new StringBuilder();
        int spaceLength = DefaultFontInfo.SPACE.getLength() + 1;
        int compensated = 0;
        while (compensated < toCompensate) {
            sb.append(" ");
            compensated += spaceLength;
        }
        return sb.toString();
    }

    /**
     * Checks if a string is null or empty.
     *
     * @param input The string to check.
     * @return True if the string is null or empty.
     */
    public static boolean isNullOrEmpty(String input) {
        return input == null || input.isEmpty();
    }

    /**
     * Centers a message in the chat.
     *
     * @param message The message to center.
     * @return The centered message.
     */
    public static String getCenteredMessage(String message) {
        if (isNullOrEmpty(message)) {
            return "";
        }

        String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
        int messagePxSize = getMessagePixelSize(coloredMessage);
        int toCompensate = CENTER_PX - (messagePxSize / 2);
        return getPaddingSpaces(toCompensate) + coloredMessage;
    }

    /**
     * Calculates the pixel size of a message.
     *
     * @param message The message with color codes.
     * @return The pixel size of the message.
     */
    private static int getMessagePixelSize(String message) {
        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        for (char c : message.toCharArray()) {
            if (c == ChatColor.COLOR_CHAR) {
                previousCode = true;
            } else if (previousCode) {
                previousCode = false;
                isBold = c == 'l' || c == 'L';
            } else {
                DefaultFontInfo dFI = DefaultFontInfo.getDefaultFontInfo(c);
                messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
                messagePxSize++;
            }
        }

        return messagePxSize;
    }


    /**
     * Send a centered message to a player
     *
     * @param player  The player to send message to
     * @param message The message to center and send
     */
    public static void sendCenteredMessage(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage(getCenteredMessage(colorize(message)));
    }

    /**
     * Send multiple centered messages to a player
     *
     * @param player   The player to send messages to
     * @param messages The messages to center and send
     */
    public static void sendMultipleCenteredMessages(Player player, String... messages) {
        if (player == null || !player.isOnline() || messages == null) return;

        Arrays.stream(messages)
                .map(TextUtil::getCenteredMessage)
                .forEach(player::sendMessage);
    }

    /**
     * Get the width of a character in pixels
     *
     * @param c The character
     * @return The width in pixels
     */
    private static int getCharWidth(char c) {
        if (c == '§') return 0;
        if (c == ' ') return 4;
        if ("il|,.".indexOf(c) != -1) return 2;
        if ("!;:\"'()[]{}".indexOf(c) != -1) return 4;
        if ("ft".indexOf(c) != -1) return 5;

        // Default character width
        return DEFAULT_CHAR_WIDTH;
    }

    /**
     * Capitalize the first letter of each word in a string
     *
     * @param input The input string
     * @return The capitalized string
     */
    public static String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String[] words = input.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;

            result.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
        }

        return result.toString().trim();
    }

    /**
     * Format a number with commas for thousands
     *
     * @param number The number to format
     * @return The formatted number as a string
     */
    public static String formatNumber(int number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }

    /**
     * Format a number with commas for thousands
     *
     * @param number The number to format
     * @return The formatted number as a string
     */
    public static String formatNumber(long number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }

    /**
     * Format a number with commas for thousands and decimal places
     *
     * @param number   The number to format
     * @param decimals The number of decimal places
     * @return The formatted number as a string
     */
    public static String formatNumber(double number, int decimals) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        return format.format(number);
    }

    /**
     * Format a duration in seconds to a readable string
     *
     * @param seconds The duration in seconds
     * @return A formatted string like "2d 5h 30m 10s"
     */
    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "0s";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder result = new StringBuilder();

        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (secs > 0 || result.length() == 0) result.append(secs).append("s");

        return result.toString().trim();
    }

    /**
     * Format a duration in milliseconds to a readable string
     *
     * @param millis The duration in milliseconds
     * @return A formatted string like "2d 5h 30m 10s"
     */
    public static String formatDurationMillis(long millis) {
        return formatDuration(millis / 1000);
    }

    /**
     * Check if a string is numeric
     *
     * @param str The string to check
     * @return true if the string is numeric
     */
    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return NUMERIC_PATTERN.matcher(str).matches();
    }

    /**
     * Truncate a string to a maximum length, adding ellipsis if needed
     *
     * @param input     The input string
     * @param maxLength The maximum length
     * @return The truncated string
     */
    public static String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength - 3) + "...";
    }

    /**
     * Colorize a string by translating color codes
     *
     * @param input The input string with color codes (&)
     * @return The colorized string
     */
    public static String colorize(String input) {
        if (input == null) {
            return "";
        }

        // First process hex colors (Minecraft 1.16+)
        String processed = translateHexColorCodes(input);

        // Then process standard color codes
        return ChatColor.translateAlternateColorCodes('&', processed);
    }

    /**
     * Translate hex color codes in the format &#RRGGBB to the format that Minecraft uses
     * For Minecraft 1.16+
     *
     * @param message String to translate hex colors in
     * @return String with translated hex colors
     */
    private static String translateHexColorCodes(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Split a string into lines of a given maximum width
     *
     * @param input    The input string
     * @param maxWidth The maximum width in characters
     * @return A list of lines
     */
    public static List<String> wordWrap(String input, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return lines;
        }

        String[] words = input.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxWidth) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(' ');
                }
                currentLine.append(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * Convert a time string to seconds
     * Format: 1d2h3m4s = 1 day, 2 hours, 3 minutes, 4 seconds
     *
     * @param timeString The time string
     * @return The time in seconds, -1 if invalid format
     */
    public static long parseTimeString(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return -1;
        }

        long seconds = 0;
        StringBuilder numBuilder = new StringBuilder();

        for (int i = 0; i < timeString.length(); i++) {
            char c = timeString.charAt(i);

            if (Character.isDigit(c)) {
                numBuilder.append(c);
            } else {
                if (numBuilder.length() == 0) {
                    continue;
                }

                int num = Integer.parseInt(numBuilder.toString());
                numBuilder = new StringBuilder();

                switch (c) {
                    case 'd':
                        seconds += num * 86400;
                        break;
                    case 'h':
                        seconds += num * 3600;
                        break;
                    case 'm':
                        seconds += num * 60;
                        break;
                    case 's':
                        seconds += num;
                        break;
                    default:
                        return -1;
                }
            }
        }

        return seconds;
    }

    /**
     * Format item lore text with word wrapping and proper coloring
     *
     * @param text      The text to format
     * @param lineColor The color code to apply to each line
     * @param maxLength The maximum line length
     * @return List of formatted lore lines
     */
    public static List<String> formatLore(String text, ChatColor lineColor, int maxLength) {
        List<String> lore = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lore;
        }

        List<String> wrapped = wordWrap(text, maxLength);
        for (String line : wrapped) {
            lore.add(lineColor + line);
        }

        return lore;
    }

    /**
     * Check if text is a valid toggle name
     *
     * @return true if valid toggle name
     */
    public static boolean isToggled(Player player, String toggle) {
        // First try to get the toggle from the player's YakPlayer data
        try {
            com.rednetty.server.mechanics.player.YakPlayer yakPlayer =
                    com.rednetty.server.mechanics.player.YakPlayerManager.getInstance().getPlayer(player);

            if (yakPlayer != null) {
                return yakPlayer.isToggled(toggle);
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }
}