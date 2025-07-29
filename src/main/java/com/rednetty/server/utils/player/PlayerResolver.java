package com.rednetty.server.utils.player;

import com.rednetty.server.utils.messaging.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified Player Resolution Utility System for YakRealms
 * Provides consistent player lookup and name resolution across all systems
 * Replaces scattered player lookup implementations with standardized approach
 */
public class PlayerResolver {

    // ========================================
    // CORE PLAYER RESOLUTION
    // ========================================

    /**
     * Resolve a player by various input methods (exact name, partial name, UUID)
     * @param input The input string (name or UUID)
     * @return The resolved player, or null if not found
     */
    public static Player resolvePlayer(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String cleanInput = input.trim();

        // Try exact match first (most common case)
        Player exactMatch = Bukkit.getPlayerExact(cleanInput);
        if (exactMatch != null && exactMatch.isOnline()) {
            return exactMatch;
        }

        // Try UUID lookup
        Player uuidMatch = resolveByUUID(cleanInput);
        if (uuidMatch != null) {
            return uuidMatch;
        }

        // Try partial name match
        return resolveByPartialName(cleanInput);
    }

    /**
     * Resolve a player by exact name only
     * @param name The exact player name
     * @return The player if found and online, null otherwise
     */
    public static Player resolvePlayerExact(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        Player player = Bukkit.getPlayerExact(name.trim());
        return (player != null && player.isOnline()) ? player : null;
    }

    /**
     * Resolve a player by partial name match
     * @param partialName The partial player name
     * @return The best matching player, or null if no match or multiple matches
     */
    public static Player resolveByPartialName(String partialName) {
        if (partialName == null || partialName.trim().isEmpty()) {
            return null;
        }

        String input = partialName.trim().toLowerCase();
        List<Player> matches = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(input)) {
                matches.add(player);
            }
        }

        // Return exact match if found
        for (Player player : matches) {
            if (player.getName().equalsIgnoreCase(partialName)) {
                return player;
            }
        }

        // Return single partial match
        if (matches.size() == 1) {
            return matches.get(0);
        }

        // Multiple matches or no matches
        return null;
    }

    /**
     * Resolve a player by UUID string
     * @param uuidString The UUID as a string
     * @return The player if found and online, null otherwise
     */
    public static Player resolveByUUID(String uuidString) {
        if (uuidString == null || uuidString.trim().isEmpty()) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(uuidString.trim());
            Player player = Bukkit.getPlayer(uuid);
            return (player != null && player.isOnline()) ? player : null;
        } catch (IllegalArgumentException e) {
            // Not a valid UUID
            return null;
        }
    }

    // ========================================
    // PLAYER RESOLUTION WITH MESSAGING
    // ========================================

    /**
     * Resolve a player and send appropriate error messages if resolution fails
     * @param requester The player requesting the resolution
     * @param input The input string to resolve
     * @return The resolved player, or null if failed (error message sent)
     */
    public static Player resolvePlayerWithMessage(Player requester, String input) {
        if (requester == null) {
            return null;
        }

        if (input == null || input.trim().isEmpty()) {
            MessageUtil.sendError(requester, "Please specify a player name.");
            return null;
        }

        Player target = resolvePlayer(input);

        if (target == null) {
            // Check for multiple partial matches
            List<Player> partialMatches = getPartialMatches(input);

            if (partialMatches.size() > 1) {
                MessageUtil.sendError(requester, "Multiple players found matching '" + input + "':");
                StringBuilder matches = new StringBuilder();
                for (int i = 0; i < Math.min(partialMatches.size(), 5); i++) {
                    if (i > 0) matches.append(", ");
                    matches.append(partialMatches.get(i).getName());
                }
                if (partialMatches.size() > 5) {
                    matches.append(" and ").append(partialMatches.size() - 5).append(" more...");
                }
                requester.sendMessage("§7" + matches.toString());
                MessageUtil.sendTip(requester, "Be more specific with the player name.");
            } else {
                MessageUtil.sendError(requester, "Player '" + input + "' is not online or doesn't exist.");
                MessageUtil.sendTip(requester, "Make sure to type the exact username.");
            }

            return null;
        }

        return target;
    }

    /**
     * Resolve a player for targeting (with additional validation)
     * @param requester The player requesting the target
     * @param input The input string to resolve
     * @return The target player, or null if failed/invalid
     */
    public static Player resolveTargetWithMessage(Player requester, String input) {
        Player target = resolvePlayerWithMessage(requester, input);

        if (target == null) {
            return null;
        }

        // Check if trying to target themselves
        if (target.equals(requester)) {
            MessageUtil.sendError(requester, "You cannot target yourself for this action.");
            return null;
        }

        return target;
    }

    /**
     * Resolve a player for party operations
     * @param requester The player requesting the resolution
     * @param input The input string to resolve
     * @return The target player, or null if failed
     */
    public static Player resolvePartyTargetWithMessage(Player requester, String input) {
        Player target = resolvePlayerWithMessage(requester, input);

        if (target == null) {
            return null;
        }

        // Check if trying to target themselves
        if (target.equals(requester)) {
            MessageUtil.sendError(requester, "You cannot invite yourself to your own party!");
            MessageUtil.sendTip(requester, "Ask another player to join you instead.");
            return null;
        }

        return target;
    }

    // ========================================
    // SEARCH AND MATCHING
    // ========================================

    /**
     * Get all partial matches for a given input
     * @param input The input to match against
     * @return List of players whose names start with the input
     */
    public static List<Player> getPartialMatches(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String searchTerm = input.trim().toLowerCase();

        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().toLowerCase().startsWith(searchTerm))
                .collect(Collectors.toList());
    }

    /**
     * Get all players whose names contain the input string
     * @param input The input to search for
     * @return List of players whose names contain the input
     */
    public static List<Player> searchPlayers(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String searchTerm = input.trim().toLowerCase();

        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().toLowerCase().contains(searchTerm))
                .collect(Collectors.toList());
    }

    /**
     * Get suggestions for tab completion
     * @param input The current input
     * @param maxSuggestions Maximum number of suggestions to return
     * @return List of player name suggestions
     */
    public static List<String> getPlayerSuggestions(String input, int maxSuggestions) {
        if (input == null) {
            input = "";
        }

        String searchTerm = input.trim().toLowerCase();

        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().toLowerCase().startsWith(searchTerm))
                .map(Player::getName)
                .limit(maxSuggestions)
                .collect(Collectors.toList());
    }

    /**
     * Get suggestions for tab completion with default limit
     * @param input The current input
     * @return List of player name suggestions (max 10)
     */
    public static List<String> getPlayerSuggestions(String input) {
        return getPlayerSuggestions(input, 10);
    }

    // ========================================
    // PLAYER VALIDATION
    // ========================================

    /**
     * Check if a player name is valid for resolution
     * @param name The name to validate
     * @return true if name is valid for resolution
     */
    public static boolean isValidPlayerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String cleanName = name.trim();

        // Check length (Minecraft usernames are 3-16 characters)
        if (cleanName.length() < 3 || cleanName.length() > 16) {
            return false;
        }

        // Check characters (only alphanumeric and underscore)
        return cleanName.matches("^[a-zA-Z0-9_]+$");
    }

    /**
     * Check if a string could be a UUID
     * @param input The string to check
     * @return true if string matches UUID format
     */
    public static boolean isUUIDFormat(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        try {
            UUID.fromString(input.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Check if a player is online and can be targeted
     * @param player The player to check
     * @return true if player is online and valid for targeting
     */
    public static boolean isValidTarget(Player player) {
        return player != null && player.isOnline();
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Get the display name for a player (handles null cases)
     * @param player The player to get display name for
     * @return The display name, or "Unknown" if player is null
     */
    public static String getDisplayName(Player player) {
        if (player == null) {
            return "Unknown";
        }

        try {
            String displayName = player.getDisplayName();
            return (displayName != null && !displayName.isEmpty()) ? displayName : player.getName();
        } catch (Exception e) {
            return player.getName();
        }
    }

    /**
     * Get the name for a player (handles null cases)
     * @param player The player to get name for
     * @return The player name, or "Unknown" if player is null
     */
    public static String getName(Player player) {
        return (player != null) ? player.getName() : "Unknown";
    }

    /**
     * Get all online players sorted by name
     * @return List of all online players sorted alphabetically
     */
    public static List<Player> getAllOnlinePlayersSorted() {
        return Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName))
                .collect(Collectors.toList());
    }

    /**
     * Get count of online players
     * @return Number of players currently online
     */
    public static int getOnlinePlayerCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    /**
     * Check if there are any online players
     * @return true if at least one player is online
     */
    public static boolean hasOnlinePlayers() {
        return !Bukkit.getOnlinePlayers().isEmpty();
    }

    /**
     * Get a random online player
     * @return A random online player, or null if no players online
     */
    public static Player getRandomOnlinePlayer() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

        if (onlinePlayers.isEmpty()) {
            return null;
        }

        List<Player> playerList = new ArrayList<>(onlinePlayers);
        Random random = new Random();
        return playerList.get(random.nextInt(playerList.size()));
    }

    /**
     * Get online players excluding a specific player
     * @param excludePlayer The player to exclude from the list
     * @return List of online players excluding the specified player
     */
    public static List<Player> getOnlinePlayersExcluding(Player excludePlayer) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.equals(excludePlayer))
                .collect(Collectors.toList());
    }

    /**
     * Get online players within a certain distance from a player
     * @param centerPlayer The center player
     * @param maxDistance The maximum distance
     * @return List of players within distance
     */
    public static List<Player> getNearbyPlayers(Player centerPlayer, double maxDistance) {
        if (centerPlayer == null || !centerPlayer.isOnline()) {
            return new ArrayList<>();
        }

        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.equals(centerPlayer))
                .filter(player -> player.getWorld().equals(centerPlayer.getWorld()))
                .filter(player -> player.getLocation().distance(centerPlayer.getLocation()) <= maxDistance)
                .collect(Collectors.toList());
    }

    /**
     * Format player names for display in lists
     * @param players The list of players to format
     * @return Formatted string of player names
     */
    public static String formatPlayerList(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return "None";
        }

        if (players.size() == 1) {
            return players.get(0).getName();
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) {
                if (i == players.size() - 1) {
                    builder.append(" and ");
                } else {
                    builder.append(", ");
                }
            }
            builder.append(players.get(i).getName());
        }

        return builder.toString();
    }

    /**
     * Get best match explanation for failed resolutions
     * @param input The input that failed to resolve
     * @return Helpful message explaining why resolution failed
     */
    public static String getResolutionFailureReason(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "No player name provided";
        }

        String cleanInput = input.trim();

        if (!isValidPlayerName(cleanInput) && !isUUIDFormat(cleanInput)) {
            return "Invalid player name format";
        }

        List<Player> partialMatches = getPartialMatches(cleanInput);

        if (partialMatches.isEmpty()) {
            return "No online players match '" + cleanInput + "'";
        } else if (partialMatches.size() > 1) {
            return "Multiple players match '" + cleanInput + "' - be more specific";
        } else {
            return "Player not found or offline";
        }
    }
}