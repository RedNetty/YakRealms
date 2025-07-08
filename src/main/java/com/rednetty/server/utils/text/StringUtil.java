package com.rednetty.server.utils.text;

import org.bukkit.ChatColor;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Advanced string manipulation utilities
 * Provides text processing, validation, and formatting methods
 */
public class StringUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<String, String> replacements = Map.ofEntries(
            Map.entry("à", "a"), Map.entry("á", "a"), Map.entry("â", "a"), Map.entry("ã", "a"), Map.entry("ä", "a"), Map.entry("å", "a"),
            Map.entry("è", "e"), Map.entry("é", "e"), Map.entry("ê", "e"), Map.entry("ë", "e"),
            Map.entry("ì", "i"), Map.entry("í", "i"), Map.entry("î", "i"), Map.entry("ï", "i"),
            Map.entry("ò", "o"), Map.entry("ó", "o"), Map.entry("ô", "o"), Map.entry("õ", "o"), Map.entry("ö", "o"), Map.entry("ø", "o"),
            Map.entry("ù", "u"), Map.entry("ú", "u"), Map.entry("û", "u"), Map.entry("ü", "u"),
            Map.entry("ý", "y"), Map.entry("ÿ", "y"), Map.entry("ñ", "n"), Map.entry("ç", "c")
    );

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Check if string is null, empty, or only whitespace
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Check if string is not null and not empty
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    /**
     * Check if string is not null, not empty, and not only whitespace
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * Get string or default if null/empty
     * @param str String to check
     * @param defaultValue Default value
     * @return Original string or default
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    /**
     * Get string or default if null/blank
     */
    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }

    /**
     * Repeat string multiple times
     * @param str String to repeat
     * @param count Number of times to repeat
     * @return Repeated string
     */
    public static String repeat(String str, int count) {
        if (isEmpty(str) || count <= 0) return "";
        return str.repeat(count);
    }

    /**
     * Reverse a string
     */
    public static String reverse(String str) {
        if (isEmpty(str)) return str;
        return new StringBuilder(str).reverse().toString();
    }

    /**
     * Count occurrences of substring
     * @param str String to search in
     * @param substring Substring to count
     * @return Number of occurrences
     */
    public static int countOccurrences(String str, String substring) {
        if (isEmpty(str) || isEmpty(substring)) return 0;

        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * Check if string contains only letters
     */
    public static boolean isAlpha(String str) {
        if (isEmpty(str)) return false;
        return str.chars().allMatch(Character::isLetter);
    }

    /**
     * Check if string contains only digits
     */
    public static boolean isNumeric(String str) {
        if (isEmpty(str)) return false;
        return str.chars().allMatch(Character::isDigit);
    }

    /**
     * Check if string contains only letters and digits
     */
    public static boolean isAlphanumeric(String str) {
        if (isEmpty(str)) return false;
        return str.chars().allMatch(Character::isLetterOrDigit);
    }

    /**
     * Check if string is a valid email
     */
    public static boolean isEmail(String str) {
        if (isEmpty(str)) return false;
        return EMAIL_PATTERN.matcher(str).matches();
    }

    /**
     * Check if string is a valid UUID
     */
    public static boolean isUUID(String str) {
        if (isEmpty(str)) return false;
        return UUID_PATTERN.matcher(str).matches();
    }

    /**
     * Remove all whitespace from string
     */
    public static String removeWhitespace(String str) {
        if (isEmpty(str)) return str;
        return str.replaceAll("\\s+", "");
    }

    /**
     * Remove all non-alphanumeric characters
     */
    public static String removeNonAlphanumeric(String str) {
        if (isEmpty(str)) return str;
        return str.replaceAll("[^a-zA-Z0-9]", "");
    }


    /**
     * Capitalize first letter of string
     */
    public static String capitalize(String str) {
        if (isEmpty(str)) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Capitalize first letter of each word
     */
    public static String capitalizeWords(String str) {
        if (isEmpty(str)) return str;

        return Arrays.stream(str.split("\\s+"))
                .map(StringUtil::capitalize)
                .collect(Collectors.joining(" "));
    }

    /**
     * Convert camelCase to snake_case
     */
    public static String camelToSnake(String str) {
        if (isEmpty(str)) return str;

        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Convert snake_case to camelCase
     */
    public static String snakeToCamel(String str) {
        if (isEmpty(str)) return str;

        String[] parts = str.toLowerCase().split("_");
        StringBuilder result = new StringBuilder(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            result.append(capitalize(parts[i]));
        }

        return result.toString();
    }

    /**
     * Pad string to specified length with character
     * @param str String to pad
     * @param length Target length
     * @param padChar Character to pad with
     * @param leftPad Whether to pad on left (true) or right (false)
     * @return Padded string
     */
    public static String pad(String str, int length, char padChar, boolean leftPad) {
        if (str == null) str = "";
        if (str.length() >= length) return str;

        String padding = repeat(String.valueOf(padChar), length - str.length());
        return leftPad ? padding + str : str + padding;
    }

    /**
     * Left pad string with spaces
     */
    public static String leftPad(String str, int length) {
        return pad(str, length, ' ', true);
    }

    /**
     * Right pad string with spaces
     */
    public static String rightPad(String str, int length) {
        return pad(str, length, ' ', false);
    }

    /**
     * Center string within specified width
     * @param str String to center
     * @param width Total width
     * @return Centered string
     */
    public static String center(String str, int width) {
        if (str == null) str = "";
        if (str.length() >= width) return str;

        int padding = width - str.length();
        int leftPadding = padding / 2;
        int rightPadding = padding - leftPadding;

        return repeat(" ", leftPadding) + str + repeat(" ", rightPadding);
    }

    /**
     * Truncate string to maximum length with ellipsis
     * @param str String to truncate
     * @param maxLength Maximum length
     * @param ellipsis Ellipsis string (e.g., "...")
     * @return Truncated string
     */
    public static String truncate(String str, int maxLength, String ellipsis) {
        if (isEmpty(str) || str.length() <= maxLength) return str;
        if (ellipsis == null) ellipsis = "...";

        int truncateLength = maxLength - ellipsis.length();
        if (truncateLength <= 0) return ellipsis.substring(0, maxLength);

        return str.substring(0, truncateLength) + ellipsis;
    }

    /**
     * Truncate string with default ellipsis
     */
    public static String truncate(String str, int maxLength) {
        return truncate(str, maxLength, "...");
    }

    /**
     * Word wrap text to specified width
     * @param text Text to wrap
     * @param width Maximum line width
     * @return List of wrapped lines
     */
    public static List<String> wordWrap(String text, int width) {
        if (isEmpty(text)) return new ArrayList<>();

        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > width) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                // Handle words longer than width
                if (word.length() > width) {
                    while (word.length() > width) {
                        lines.add(word.substring(0, width));
                        word = word.substring(width);
                    }
                    if (!word.isEmpty()) {
                        currentLine.append(word);
                    }
                } else {
                    currentLine.append(word);
                }
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
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
     * Calculate string similarity using Levenshtein distance
     * @param str1 First string
     * @param str2 Second string
     * @return Similarity percentage (0.0 - 1.0)
     */
    public static double similarity(String str1, String str2) {
        if (str1 == null || str2 == null) return 0.0;
        if (str1.equals(str2)) return 1.0;

        int maxLength = Math.max(str1.length(), str2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance(str1, str2);
        return 1.0 - (double) distance / maxLength;
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    public static int levenshteinDistance(String str1, String str2) {
        if (str1 == null || str2 == null) return Math.max(str1 == null ? 0 : str1.length(), str2 == null ? 0 : str2.length());

        int[][] dp = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                int cost = str1.charAt(i - 1) == str2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }

        return dp[str1.length()][str2.length()];
    }

    /**
     * Generate random string with specified length and character set
     * @param length Length of string
     * @param chars Characters to choose from
     * @return Random string
     */
    public static String randomString(int length, String chars) {
        if (length <= 0 || isEmpty(chars)) return "";

        Random random = new Random();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }

        return result.toString();
    }

    /**
     * Generate random alphanumeric string
     */
    public static String randomAlphanumeric(int length) {
        return randomString(length, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
    }

    /**
     * Generate random alphabetic string
     */
    public static String randomAlphabetic(int length) {
        return randomString(length, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
    }

    /**
     * Generate random numeric string
     */
    public static String randomNumeric(int length) {
        return randomString(length, "0123456789");
    }

    /**
     * Format number with thousand separators
     * @param number Number to format
     * @return Formatted number string
     */
    public static String formatNumber(long number) {
        return NumberFormat.getNumberInstance().format(number);
    }

    /**
     * Format decimal number with specified precision
     */
    public static String formatDecimal(double number, int precision) {
        DecimalFormat format = new DecimalFormat();
        format.setMaximumFractionDigits(precision);
        format.setMinimumFractionDigits(precision);
        return format.format(number);
    }

    /**
     * Parse integer safely
     * @param str String to parse
     * @param defaultValue Default value if parsing fails
     * @return Parsed integer or default
     */
    public static int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse double safely
     */
    public static double parseDouble(String str, double defaultValue) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Join strings with delimiter
     * @param delimiter Delimiter to use
     * @param strings Strings to join
     * @return Joined string
     */
    public static String join(String delimiter, String... strings) {
        return String.join(delimiter, strings);
    }

    /**
     * Join collection with delimiter
     */
    public static String join(String delimiter, Collection<String> strings) {
        return String.join(delimiter, strings);
    }

    /**
     * Colorize string by translating color codes and hex colors
     * @param str String with color codes
     * @return Colorized string
     */
    public static String colorize(String str) {
        if (isEmpty(str)) return str;

        // Process hex colors first
        str = HEX_COLOR_PATTERN.matcher(str).replaceAll(match -> {
            String hex = match.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            return replacement.toString();
        });

        // Process standard color codes
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    /**
     * Strip color codes from string
     */
    public static String stripColor(String str) {
        if (isEmpty(str)) return str;
        return ChatColor.stripColor(str);
    }

    /**
     * Check if two strings are equal ignoring case
     */
    public static boolean equalsIgnoreCase(String str1, String str2) {
        return Objects.equals(str1, str2) || (str1 != null && str1.equalsIgnoreCase(str2));
    }

    /**
     * Check if string starts with any of the given prefixes
     */
    public static boolean startsWithAny(String str, String... prefixes) {
        if (isEmpty(str) || prefixes == null) return false;

        for (String prefix : prefixes) {
            if (str.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if string ends with any of the given suffixes
     */
    public static boolean endsWithAny(String str, String... suffixes) {
        if (isEmpty(str) || suffixes == null) return false;

        for (String suffix : suffixes) {
            if (str.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}