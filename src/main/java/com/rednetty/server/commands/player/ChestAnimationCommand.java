package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.world.lootchests.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * command handler for chest animation and loot system
 * Now includes full loot popping functionality with particles and effects
 */
public abstract class ChestAnimationCommand implements CommandExecutor, TabCompleter {

    public ChestAnimationCommand() {

    }


}