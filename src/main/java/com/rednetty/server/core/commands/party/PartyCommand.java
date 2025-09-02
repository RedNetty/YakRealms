package com.rednetty.server.core.commands.party;

import com.rednetty.server.core.mechanics.chat.ChatMechanics;
import com.rednetty.server.core.mechanics.player.social.party.PartyMechanics;
import com.rednetty.server.utils.messaging.MessageUtil;
import com.rednetty.server.utils.sounds.SoundUtil;
import com.rednetty.server.utils.permissions.PermissionUtil;
import com.rednetty.server.utils.cooldowns.CooldownManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 *  Party Command with unified utility integration
 * Uses MessageUtil, SoundUtil, PermissionUtil, and CooldownManager for consistency
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private final PartyMechanics partyMechanics;

    public PartyCommand() {
        this.partyMechanics = PartyMechanics.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Ensure command is executed by a player
        if (!(sender instanceof Player)) {
            MessageUtil.sendError((Player) sender, "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check base party permission
        if (!PermissionUtil.checkPartyPermissionWithMessage(player, "use")) {
            return true;
        }

        // Check if player is in a party
        if (!partyMechanics.isInParty(player)) {
            MessageUtil.sendError(player, "You are not in a party!");
            MessageUtil.sendTip(player, "Join a party first with /pinvite <player> or accept an invitation with /paccept");
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

        // Check chat cooldown
        if (!CooldownManager.checkChatCooldown(player)) {
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
            // Apply chat cooldown and play sound
            CooldownManager.applyChatCooldown(player);
            SoundUtil.playPartyChat(player);

            // Handle special message types
            handleSpecialMessageTypes(player, message);
        }

        return true;
    }

    /**
     * Handle special party commands
     */
    private boolean handleSpecialCommands(Player player, String command, String[] args) {
        switch (command) {
            case "info":
            case "status":
            case "list":
                showDetailedPartyInformation(player);
                return true;

            case "help":
                showPartyHelp(player);
                return true;

            case "settings":
                showPartySettings(player);
                return true;

            default:
                return false;
        }
    }

    /**
     * Show basic party information
     */
    private void showPartyInformation(Player player) {
        try {
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);

            if (partyMembers == null || partyMembers.isEmpty()) {
                MessageUtil.sendError(player, "Unable to retrieve party information.");
                return;
            }

            MessageUtil.sendHeader(player, "PARTY INFORMATION");

            player.sendMessage(ChatColor.GRAY + "Party Members " + ChatColor.WHITE + "(" + partyMembers.size() + "):");

            for (Player member : partyMembers) {
                String roleIndicator = "";
                if (partyMechanics.isPartyLeader(member)) {
                    roleIndicator = ChatColor.GOLD + "★ " + ChatColor.YELLOW + "[Leader] ";
                } else if (partyMechanics.isPartyOfficer(member)) {
                    roleIndicator = ChatColor.YELLOW + "♦ " + ChatColor.GRAY + "[Officer] ";
                }

                String statusIndicator = member.isOnline() ? ChatColor.GREEN + "●" : ChatColor.RED + "●";

                player.sendMessage(ChatColor.GRAY + "  " + statusIndicator + " " + roleIndicator +
                        ChatColor.WHITE + member.getName());
            }

            MessageUtil.sendBlankLine(player);
            MessageUtil.sendTip(player, "Use /p <message> to chat with your party");

            SoundUtil.playNotification(player);

        } catch (Exception e) {
            MessageUtil.sendError(player, "Error retrieving party information.");
        }
    }

    /**
     * Show detailed party information
     */
    private void showDetailedPartyInformation(Player player) {
        try {
            MessageUtil.sendHeader(player, "DETAILED PARTY INFORMATION");

            // Basic party info
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            player.sendMessage(ChatColor.GRAY + "Party Size: " + ChatColor.WHITE + partyMembers.size() + "/8");


            MessageUtil.sendBlankLine(player);

            // Member details
            player.sendMessage(ChatColor.GRAY + "Members:");
            for (Player member : partyMembers) {
                showMemberDetails(player, member);
            }

            MessageUtil.sendBlankLine(player);
            MessageUtil.sendTip(player, "Use /p help for available party commands");

            SoundUtil.playNotification(player);

        } catch (Exception e) {
            MessageUtil.sendError(player, "Error retrieving detailed party information.");
        }
    }

    /**
     * Show details for a specific party member
     */
    private void showMemberDetails(Player viewer, Player member) {
        StringBuilder memberInfo = new StringBuilder();

        // Status indicator
        String statusColor = member.isOnline() ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
        memberInfo.append(ChatColor.GRAY).append("  ").append(statusColor).append("● ");

        // Role indicator
        if (partyMechanics.isPartyLeader(member)) {
            memberInfo.append(ChatColor.GOLD).append("★ ").append(ChatColor.YELLOW).append("[Leader] ");
        } else if (partyMechanics.isPartyOfficer(member)) {
            memberInfo.append(ChatColor.YELLOW).append("♦ ").append(ChatColor.GRAY).append("[Officer] ");
        }

        // Name
        memberInfo.append(ChatColor.WHITE).append(member.getName());

        // Distance (if online and in same world)
        if (member.isOnline() && viewer.getWorld().equals(member.getWorld())) {
            double distance = viewer.getLocation().distance(member.getLocation());
            String distanceColor = distance < 50 ? ChatColor.GREEN.toString() :
                    distance < 100 ? ChatColor.YELLOW.toString() : ChatColor.RED.toString();
            memberInfo.append(ChatColor.GRAY).append(" (").append(distanceColor)
                    .append(String.format("%.0f", distance)).append("m").append(ChatColor.GRAY).append(")");
        }

        viewer.sendMessage(memberInfo.toString());
    }

    /**
     * Show party help
     */
    private void showPartyHelp(Player player) {
        MessageUtil.sendHeader(player, "PARTY COMMANDS");

        player.sendMessage(ChatColor.YELLOW + "/p <message>" + ChatColor.GRAY + " - Send message to party");
        player.sendMessage(ChatColor.YELLOW + "/p info" + ChatColor.GRAY + " - Show detailed party information");
        player.sendMessage(ChatColor.YELLOW + "/p help" + ChatColor.GRAY + " - Show this help menu");
        player.sendMessage(ChatColor.YELLOW + "/pinvite <player>" + ChatColor.GRAY + " - Invite a player to party");
        player.sendMessage(ChatColor.YELLOW + "/paccept" + ChatColor.GRAY + " - Accept party invitation");
        player.sendMessage(ChatColor.YELLOW + "/pdecline" + ChatColor.GRAY + " - Decline party invitation");
        player.sendMessage(ChatColor.YELLOW + "/pkick <player>" + ChatColor.GRAY + " - Kick player from party");
        player.sendMessage(ChatColor.YELLOW + "/pquit" + ChatColor.GRAY + " - Leave the party");

        MessageUtil.sendBlankLine(player);
        MessageUtil.sendTip(player, "Use @i@ in party chat to show items you're holding");

        SoundUtil.playNotification(player);
    }

    /**
     * Show party settings
     */
    private void showPartySettings(Player player) {
        MessageUtil.sendHeader(player, "PARTY SETTINGS");

        player.sendMessage(ChatColor.GRAY + "Coming soon: Party settings will allow you to:");
        player.sendMessage(ChatColor.GRAY + "• Toggle friendly fire");
        player.sendMessage(ChatColor.GRAY + "• Set party privacy");
        player.sendMessage(ChatColor.GRAY + "• Configure auto-invite");
        player.sendMessage(ChatColor.GRAY + "• Manage party roles");

        MessageUtil.sendBlankLine(player);
        MessageUtil.sendTip(player, "Settings will be available in a future update");

        SoundUtil.playNotification(player);
    }

    /**
     * Validate message content
     */
    private boolean validateMessage(Player player, String message) {
        if (message == null || message.trim().isEmpty()) {
            MessageUtil.sendError(player, "Please provide a message to send.");
            MessageUtil.sendTip(player, "Usage: /p <message>");
            return false;
        }

        // Check message length
        if (message.length() > 256) {
            MessageUtil.sendError(player, "Message is too long! Maximum 256 characters.");
            return false;
        }

        // Check for spam or inappropriate content (basic check)
        if (message.trim().equals(message.trim().substring(0, 1).repeat(message.trim().length()))) {
            MessageUtil.sendError(player, "Please don't spam repeated characters.");
            return false;
        }

        return true;
    }

    /**
     * Send a party message with item display
     */
    private boolean sendPartyItemMessage(Player player, ItemStack item, String message) {
        try {
            // Get party members
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers == null || partyMembers.isEmpty()) {
                MessageUtil.sendError(player, "No party members to send message to!");
                return false;
            }

            // Use ChatMechanics for item display functionality
            int success = ChatMechanics.sendItemMessageToPlayers(player, item, "&d<PARTY>", message, partyMembers);

            if (!(success > 0)) {
                MessageUtil.sendError(player, "Failed to send item message to party.");
                MessageUtil.sendTip(player, "Make sure you're holding a valid item when using @i@");
            }

            return (success > 0);

        } catch (Exception e) {
            MessageUtil.sendError(player, "Error sending item message to party.");
            return false;
        }
    }

    /**
     * Handle special message types (announcements, etc.)
     */
    private void handleSpecialMessageTypes(Player player, String message) {
        // Check for special message patterns
        if (message.toLowerCase().contains("help") || message.toLowerCase().contains("stuck")) {
            // Party member is asking for help
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            for (Player member : partyMembers) {
                if (!member.equals(player)) {
                    member.sendMessage(ChatColor.YELLOW + "💡 " + player.getName() + " might need assistance!");
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        // Check permission
        if (!PermissionUtil.hasPartyPermission(player, "use")) {
            return completions;
        }

        if (args.length == 1) {
            // Suggest special commands
            String[] commands = {"info", "help", "settings", "status", "list"};
            String input = args[0].toLowerCase();

            for (String command : commands) {
                if (command.startsWith(input)) {
                    completions.add(command);
                }
            }

            // If no special commands match, don't suggest anything (let them type message)
        }

        return completions;
    }
}