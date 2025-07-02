package com.rednetty.server.commands.party;

import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.party.PartyMechanics;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Enhanced Party Chat Command with comprehensive messaging features and validation
 * Integrates fully with the advanced PartyMechanics system including permission checks,
 * message formatting, interactive features, and item display support
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private final PartyMechanics partyMechanics;

    // Common chat shortcuts and emotes
    private static final List<String> CHAT_SHORTCUTS = Arrays.asList(
            "help", "follow", "wait", "go", "stop", "yes", "no", "thanks", "sorry",
            "ready", "attack", "defend", "retreat", "loot", "trade", "heal", "mana"
    );

    // Emotes that can be suggested
    private static final List<String> EMOTES = Arrays.asList(
            ":)", ":(", ":D", ":P", "<3", "o/", "\\o", "^^", "xD", ":|"
    );

    public PartyCommand() {
        this.partyMechanics = PartyMechanics.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Ensure command is executed by a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check if player is in a party
        if (!partyMechanics.isInParty(player)) {
            player.sendMessage(ChatColor.RED + "❌ You are not in a party!");
            player.sendMessage(ChatColor.GRAY + "💡 Join a party first with " + ChatColor.YELLOW + "/pinvite <player>"
                    + ChatColor.GRAY + " or accept an invitation with " + ChatColor.YELLOW + "/paccept");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return true;
        }

        // Handle different command patterns
        if (args.length == 0) {
            showPartyInformation(player);
            return true;
        }

        // Handle special commands
        String firstArg = args[0].toLowerCase();
        if (handleSpecialCommands(player, firstArg, args)) {
            return true;
        }

        // Validate message content
        String message = String.join(" ", args);
        if (!validateMessage(player, message)) {
            return true;
        }

        // Check if player is showing an item
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        boolean showingItem = message.contains("@i@") && ChatMechanics.isPlayerHoldingValidItem(player);

        boolean success = false;

        if (showingItem) {
            // Handle item display for party chat
            success = sendPartyItemMessage(player, itemInHand, message);
        } else {
            // Send regular party message using PartyMechanics
            success = partyMechanics.sendPartyMessage(player, message);
        }

        if (success) {
            // Play subtle chat sound to sender for confirmation
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.5f);

            // Handle special message types
            handleSpecialMessageTypes(player, message);
        }
        // Error messages are handled by PartyMechanics.sendPartyMessage() or our item message method

        return true;
    }

    /**
     * Send a party message with item display
     *
     * @param player  The player sending the message
     * @param item    The item to display
     * @param message The message containing @i@
     * @return true if successful
     */
    private boolean sendPartyItemMessage(Player player, ItemStack item, String message) {
        try {
            // Get party members
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers == null || partyMembers.isEmpty()) {
                player.sendMessage(ChatColor.RED + "❌ No party members to send message to!");
                return false;
            }

            // Get party formatted name - try to use PartyMechanics formatting if available
            String partyPrefix = getPartyMessagePrefix(player);

            // Send item message to all party members
            int successCount = ChatMechanics.sendItemMessageToPlayers(player, item, partyPrefix, message, partyMembers);

            if (successCount > 0) {
                // Log party message
                try {
                    String itemName = ChatMechanics.getDisplayNameForItem(item);
                    System.out.println("[PARTY] " + player.getName() + " (with item " + itemName + "): " + message +
                            " [sent to " + successCount + " members]");
                } catch (Exception e) {
                    System.out.println("[PARTY] " + player.getName() + " (with item): " + message);
                }
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "❌ Failed to send item message to party!");
                return false;
            }

        } catch (Exception e) {
            // Fallback: try to send without item display
            try {
                String itemName = ChatMechanics.getDisplayNameForItem(item);
                String fallbackMessage = message.replace("@i@", ChatColor.YELLOW + "[" + itemName + "]" + ChatColor.WHITE);
                boolean fallbackSuccess = partyMechanics.sendPartyMessage(player, fallbackMessage);

                if (fallbackSuccess) {
                    player.sendMessage(ChatColor.YELLOW + "⚠ Item display failed, sent as regular message.");
                }
                return fallbackSuccess;
            } catch (Exception e2) {
                player.sendMessage(ChatColor.RED + "❌ Failed to send party message!");
                System.err.println("Error sending party item message: " + e2.getMessage());
                return false;
            }
        }
    }

    /**
     * Get the party message prefix for the player
     * Try to format it similar to how PartyMechanics would
     *
     * @param player The player
     * @return Formatted prefix
     */
    private String getPartyMessagePrefix(Player player) {
        try {
            // Try to get party-specific formatting
            PartyMechanics.Party party = partyMechanics.getParty(player);
            StringBuilder prefix = new StringBuilder();

            // Party prefix
            prefix.append(ChatColor.LIGHT_PURPLE).append("[").append(ChatColor.BOLD).append("P").append(ChatColor.LIGHT_PURPLE).append("] ");

            // Add role indicator if available
            if (party != null) {
                if (party.isLeader(player.getUniqueId())) {
                    prefix.append(ChatColor.GOLD).append("★ ");
                } else if (party.isOfficer(player.getUniqueId())) {
                    prefix.append(ChatColor.YELLOW).append("♦ ");
                }
            }

            // Add player name
            prefix.append(ChatColor.WHITE).append(player.getName());

            return prefix.toString();
        } catch (Exception e) {
            // Fallback prefix
            return ChatColor.LIGHT_PURPLE + "[P] " + ChatColor.WHITE + player.getName();
        }
    }

    /**
     * Show comprehensive party information when no arguments provided
     */
    private void showPartyInformation(Player player) {
        PartyMechanics.Party party = partyMechanics.getParty(player);
        if (party == null) return;

        player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.BOLD + "PARTY INFORMATION" + ChatColor.LIGHT_PURPLE + " ✦");
        player.sendMessage("");

        // Party size and member list
        List<Player> members = partyMechanics.getPartyMembers(player);
        player.sendMessage(ChatColor.GRAY + "Members (" + ChatColor.WHITE + party.getSize()
                + ChatColor.GRAY + "/" + ChatColor.WHITE + party.getMaxSize() + ChatColor.GRAY + "):");

        if (members != null) {
            for (Player member : members) {
                String roleIndicator = "";
                String nameColor = ChatColor.WHITE.toString();

                if (party.isLeader(member.getUniqueId())) {
                    roleIndicator = ChatColor.GOLD + " ★ Leader";
                    nameColor = ChatColor.GOLD.toString();
                } else if (party.isOfficer(member.getUniqueId())) {
                    roleIndicator = ChatColor.YELLOW + " ♦ Officer";
                    nameColor = ChatColor.YELLOW.toString();
                }

                String onlineStatus = member.isOnline() ? ChatColor.GREEN + "●" : ChatColor.RED + "●";
                player.sendMessage(ChatColor.GRAY + "  " + onlineStatus + " " + nameColor + member.getName() + roleIndicator);
            }
        }

        player.sendMessage("");

        // Party settings
        showPartySettings(player, party);

        // Chat usage - updated to include item display
        player.sendMessage(ChatColor.AQUA + "💬 " + ChatColor.BOLD + "Party Chat:");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p <message>" + ChatColor.GRAY + " - Send message to party");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p <message> @i@" + ChatColor.GRAY + " - Show item in message");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p help" + ChatColor.GRAY + " - Show quick commands");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p info" + ChatColor.GRAY + " - Show this information");

        // Play info sound
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
    }

    /**
     * Show party settings information
     */
    private void showPartySettings(Player player, PartyMechanics.Party party) {
        player.sendMessage(ChatColor.GREEN + "⚙ " + ChatColor.BOLD + "Party Settings:");

        // Loot sharing
        boolean lootSharing = party.getBooleanSetting("loot_sharing", false);
        player.sendMessage(ChatColor.GRAY + "• Loot Sharing: " +
                (lootSharing ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

        // Friendly fire
        boolean friendlyFire = party.getBooleanSetting("friendly_fire", false);
        player.sendMessage(ChatColor.GRAY + "• Friendly Fire: " +
                (friendlyFire ? ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"));

        // Party MOTD if available
        if (!party.getMotd().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "• MOTD: " + ChatColor.WHITE + "\"" + party.getMotd() + "\"");
        }
    }

    /**
     * Handle special party commands
     */
    private boolean handleSpecialCommands(Player player, String command, String[] args) {
        switch (command) {
            case "help":
                showPartyHelp(player);
                return true;

            case "info":
                showPartyInformation(player);
                return true;

            case "online":
                showOnlineMembers(player);
                return true;

            case "stats":
                showPartyStatistics(player);
                return true;

            default:
                return false;
        }
    }

    /**
     * Show comprehensive party help
     */
    private void showPartyHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "📖 " + ChatColor.BOLD + "PARTY QUICK COMMANDS");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Chat Shortcuts:");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p help" + ChatColor.GRAY + " - Show this help");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p info" + ChatColor.GRAY + " - Party information");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p online" + ChatColor.GRAY + " - List online members");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p stats" + ChatColor.GRAY + " - Party statistics");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Item Display:");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p Check out this @i@!" + ChatColor.GRAY + " - Show item to party");
        player.sendMessage(ChatColor.GRAY + "• Hold item in main hand and use " + ChatColor.YELLOW + "@i@" + ChatColor.GRAY + " in your message");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Quick Messages:");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GREEN + "/p ready" + ChatColor.GRAY + " - Signal you're ready");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.RED + "/p help" + ChatColor.GRAY + " - Call for assistance");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.AQUA + "/p follow" + ChatColor.GRAY + " - Ask party to follow");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p wait" + ChatColor.GRAY + " - Ask party to wait");

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
    }

    /**
     * Show online party members
     */
    private void showOnlineMembers(Player player) {
        List<Player> members = partyMechanics.getPartyMembers(player);
        if (members == null) return;

        List<Player> onlineMembers = new ArrayList<>();
        for (Player member : members) {
            if (member.isOnline()) {
                onlineMembers.add(member);
            }
        }

        player.sendMessage(ChatColor.GREEN + "🌐 " + ChatColor.BOLD + "Online Party Members (" + onlineMembers.size() + "):");

        PartyMechanics.Party party = partyMechanics.getParty(player);
        for (Player member : onlineMembers) {
            String world = member.getWorld().getName();
            String roleIndicator = "";

            if (party != null) {
                if (party.isLeader(member.getUniqueId())) {
                    roleIndicator = ChatColor.GOLD + " ★";
                } else if (party.isOfficer(member.getUniqueId())) {
                    roleIndicator = ChatColor.YELLOW + " ♦";
                }
            }

            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + member.getName() + roleIndicator
                    + ChatColor.GRAY + " (in " + world + ")");
        }

        if (onlineMembers.size() != members.size()) {
            int offlineCount = members.size() - onlineMembers.size();
            player.sendMessage(ChatColor.GRAY + "(" + offlineCount + " member" + (offlineCount != 1 ? "s" : "") + " offline)");
        }
    }

    /**
     * Show party statistics
     */
    private void showPartyStatistics(Player player) {
        PartyMechanics.PartyStatistics stats = partyMechanics.getPartyStatistics(player.getUniqueId());
        PartyMechanics.Party party = partyMechanics.getParty(player);

        player.sendMessage(ChatColor.BLUE + "📊 " + ChatColor.BOLD + "PARTY STATISTICS");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Your Party History:");
        player.sendMessage(ChatColor.GRAY + "• Parties Created: " + ChatColor.WHITE + stats.getTotalPartiesCreated());
        player.sendMessage(ChatColor.GRAY + "• Parties Joined: " + ChatColor.WHITE + stats.getTotalPartiesJoined());
        player.sendMessage(ChatColor.GRAY + "• Messages Sent: " + ChatColor.WHITE + stats.getMessagesLent());
        player.sendMessage(ChatColor.GRAY + "• Experience Shared: " + ChatColor.WHITE + stats.getExperienceShared());

        if (party != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Current Party:");
            player.sendMessage(ChatColor.GRAY + "• Created: " + ChatColor.WHITE + formatTime(party.getCreationTime()));
            player.sendMessage(ChatColor.GRAY + "• Size: " + ChatColor.WHITE + party.getSize() + "/" + party.getMaxSize());
        }
    }

    /**
     * Validate message content
     */
    private boolean validateMessage(Player player, String message) {
        // Check message length
        if (message.trim().isEmpty()) {
            player.sendMessage(ChatColor.RED + "❌ Cannot send empty message!");
            player.sendMessage(ChatColor.GRAY + "💡 Type " + ChatColor.YELLOW + "/p help" + ChatColor.GRAY + " for quick commands.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        if (message.length() > 256) {
            player.sendMessage(ChatColor.RED + "❌ Message too long! Maximum 256 characters.");
            player.sendMessage(ChatColor.GRAY + "Current length: " + ChatColor.WHITE + message.length() + ChatColor.GRAY + " characters");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        // Basic spam protection (very simple)
        if (message.equals(message.toUpperCase()) && message.length() > 10) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Please avoid excessive caps in party chat.");
        }

        // Warn if trying to show item but not holding one
        if (message.contains("@i@") && !ChatMechanics.isPlayerHoldingValidItem(player)) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Hold an item in your main hand to display it with @i@");
        }

        return true;
    }

    /**
     * Handle special message types and provide enhanced feedback
     */
    private void handleSpecialMessageTypes(Player player, String message) {
        String lowerMessage = message.toLowerCase();

        // Handle quick action messages
        if (CHAT_SHORTCUTS.contains(lowerMessage)) {
            switch (lowerMessage) {
                case "ready":
                    // Add extra notification for readiness
                    playPartySound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                    break;
                case "help":
                    // Emphasize help requests
                    playPartySound(player, Sound.BLOCK_NOTE_BLOCK_BELL);
                    break;
                case "follow":
                case "go":
                    // Movement commands
                    playPartySound(player, Sound.ENTITY_HORSE_GALLOP);
                    break;
                case "wait":
                case "stop":
                    // Stop commands
                    playPartySound(player, Sound.BLOCK_NOTE_BLOCK_BASS);
                    break;
            }
        }

        // Handle questions (messages ending with ?)
        if (message.endsWith("?")) {
            // Questions get a different sound to draw attention
            playPartySound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
        }

        // Handle item display messages
        if (message.contains("@i@")) {
            // Item display gets a special sound
            playPartySound(player, Sound.ENTITY_ITEM_PICKUP);
        }
    }

    /**
     * Play sound to all party members
     */
    private void playPartySound(Player sender, Sound sound) {
        List<Player> members = partyMechanics.getPartyMembers(sender);
        if (members != null) {
            for (Player member : members) {
                if (member.isOnline() && !member.equals(sender)) {
                    member.playSound(member.getLocation(), sound, 0.5f, 1.0f);
                }
            }
        }
    }

    /**
     * Format timestamp for display
     */
    private String formatTime(long epochSeconds) {
        long currentTime = System.currentTimeMillis() / 1000;
        long diff = currentTime - epochSeconds;

        if (diff < 60) return diff + " seconds ago";
        if (diff < 3600) return (diff / 60) + " minutes ago";
        if (diff < 86400) return (diff / 3600) + " hours ago";
        return (diff / 86400) + " days ago";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (!partyMechanics.isInParty(player)) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Add special commands
            List<String> specialCommands = Arrays.asList("help", "info", "online", "stats");
            for (String cmd : specialCommands) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }

            // Add chat shortcuts
            for (String shortcut : CHAT_SHORTCUTS) {
                if (shortcut.startsWith(partial)) {
                    completions.add(shortcut);
                }
            }

            // Add player names for mentions
            List<Player> members = partyMechanics.getPartyMembers(player);
            if (members != null) {
                for (Player member : members) {
                    if (!member.equals(player) && member.getName().toLowerCase().startsWith(partial)) {
                        completions.add("@" + member.getName());
                    }
                }
            }

            // Add item display suggestion if holding an item
            if (ChatMechanics.isPlayerHoldingValidItem(player) && "@i@".startsWith(partial)) {
                completions.add("@i@");
            }
        } else if (args.length > 1) {
            // Suggest emotes and item display for later words
            String partial = args[args.length - 1].toLowerCase();

            // Suggest @i@ if holding an item
            if (ChatMechanics.isPlayerHoldingValidItem(player) && "@i@".startsWith(partial)) {
                completions.add("@i@");
            }

            // Suggest emotes
            for (String emote : EMOTES) {
                if (emote.startsWith(partial)) {
                    completions.add(emote);
                }
            }
        }

        return completions;
    }
}