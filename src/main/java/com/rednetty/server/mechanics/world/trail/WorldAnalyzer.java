package com.rednetty.server.mechanics.world.trail;

import com.rednetty.server.YakRealms;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.EnumSet;
import java.util.Set;

public class WorldAnalyzer {
    private final YakRealms plugin;
    private final Set<Material> passableMaterials;

    public WorldAnalyzer(YakRealms plugin) {
        this.plugin = plugin;
        this.passableMaterials = EnumSet.noneOf(Material.class);
        initializePassableMaterials();
    }

    private void initializePassableMaterials() {
        // Air blocks
        passableMaterials.add(Material.AIR);
        passableMaterials.add(Material.CAVE_AIR);
        passableMaterials.add(Material.VOID_AIR);

        // Fluids (be cautious with these)
        passableMaterials.add(Material.WATER);
        passableMaterials.add(Material.LAVA);

        // Vegetation
        passableMaterials.add(Material.GRASS_BLOCK);
        passableMaterials.add(Material.TALL_GRASS);
        passableMaterials.add(Material.SEAGRASS);
        passableMaterials.add(Material.TALL_SEAGRASS);
        passableMaterials.add(Material.FERN);
        passableMaterials.add(Material.LARGE_FERN);
        passableMaterials.add(Material.DEAD_BUSH);
        passableMaterials.add(Material.DANDELION);
        passableMaterials.add(Material.POPPY);
        passableMaterials.add(Material.BLUE_ORCHID);
        passableMaterials.add(Material.ALLIUM);
        passableMaterials.add(Material.AZURE_BLUET);
        passableMaterials.add(Material.RED_TULIP);
        passableMaterials.add(Material.ORANGE_TULIP);
        passableMaterials.add(Material.WHITE_TULIP);
        passableMaterials.add(Material.PINK_TULIP);
        passableMaterials.add(Material.OXEYE_DAISY);
        passableMaterials.add(Material.CORNFLOWER);
        passableMaterials.add(Material.LILY_OF_THE_VALLEY);
        passableMaterials.add(Material.WITHER_ROSE);
        passableMaterials.add(Material.SUNFLOWER);
        passableMaterials.add(Material.LILAC);
        passableMaterials.add(Material.ROSE_BUSH);
        passableMaterials.add(Material.PEONY);
        passableMaterials.add(Material.KELP);
        passableMaterials.add(Material.KELP_PLANT);
        passableMaterials.add(Material.WARPED_ROOTS);
        passableMaterials.add(Material.NETHER_SPROUTS);
        passableMaterials.add(Material.CRIMSON_ROOTS);
        passableMaterials.add(Material.VINE);
        passableMaterials.add(Material.GLOW_LICHEN);
        passableMaterials.add(Material.SMALL_DRIPLEAF);
        passableMaterials.add(Material.HANGING_ROOTS);
        passableMaterials.add(Material.SPORE_BLOSSOM);

        // Saplings
        passableMaterials.add(Material.OAK_SAPLING);
        passableMaterials.add(Material.SPRUCE_SAPLING);
        passableMaterials.add(Material.BIRCH_SAPLING);
        passableMaterials.add(Material.JUNGLE_SAPLING);
        passableMaterials.add(Material.ACACIA_SAPLING);
        passableMaterials.add(Material.DARK_OAK_SAPLING);
        passableMaterials.add(Material.CHERRY_SAPLING);

        // Fungus
        passableMaterials.add(Material.CRIMSON_FUNGUS);
        passableMaterials.add(Material.WARPED_FUNGUS);

        // Mushrooms
        passableMaterials.add(Material.BROWN_MUSHROOM);
        passableMaterials.add(Material.RED_MUSHROOM);

        // Crops (be cautious, as these might damage the farm)
        passableMaterials.add(Material.WHEAT);
        passableMaterials.add(Material.CARROTS);
        passableMaterials.add(Material.POTATOES);
        passableMaterials.add(Material.BEETROOTS);
        passableMaterials.add(Material.PUMPKIN_STEM);
        passableMaterials.add(Material.MELON_STEM);
        passableMaterials.add(Material.NETHER_WART);
        passableMaterials.add(Material.COCOA);
        passableMaterials.add(Material.SWEET_BERRY_BUSH);

        // Misc plants
        passableMaterials.add(Material.SUGAR_CANE);
        passableMaterials.add(Material.BAMBOO);
        passableMaterials.add(Material.BAMBOO_SAPLING);
        passableMaterials.add(Material.CACTUS);

        // Corals
        passableMaterials.add(Material.BRAIN_CORAL);
        passableMaterials.add(Material.BUBBLE_CORAL);
        passableMaterials.add(Material.FIRE_CORAL);
        passableMaterials.add(Material.HORN_CORAL);
        passableMaterials.add(Material.TUBE_CORAL);
        passableMaterials.add(Material.DEAD_BRAIN_CORAL);
        passableMaterials.add(Material.DEAD_BUBBLE_CORAL);
        passableMaterials.add(Material.DEAD_FIRE_CORAL);
        passableMaterials.add(Material.DEAD_HORN_CORAL);
        passableMaterials.add(Material.DEAD_TUBE_CORAL);

        // Coral fans
        passableMaterials.add(Material.BRAIN_CORAL_FAN);
        passableMaterials.add(Material.BUBBLE_CORAL_FAN);
        passableMaterials.add(Material.FIRE_CORAL_FAN);
        passableMaterials.add(Material.HORN_CORAL_FAN);
        passableMaterials.add(Material.TUBE_CORAL_FAN);
        passableMaterials.add(Material.DEAD_BRAIN_CORAL_FAN);
        passableMaterials.add(Material.DEAD_BUBBLE_CORAL_FAN);
        passableMaterials.add(Material.DEAD_FIRE_CORAL_FAN);
        passableMaterials.add(Material.DEAD_HORN_CORAL_FAN);
        passableMaterials.add(Material.DEAD_TUBE_CORAL_FAN);

        // Wall coral fans
        passableMaterials.add(Material.BRAIN_CORAL_WALL_FAN);
        passableMaterials.add(Material.BUBBLE_CORAL_WALL_FAN);
        passableMaterials.add(Material.FIRE_CORAL_WALL_FAN);
        passableMaterials.add(Material.HORN_CORAL_WALL_FAN);
        passableMaterials.add(Material.TUBE_CORAL_WALL_FAN);
        passableMaterials.add(Material.DEAD_BRAIN_CORAL_WALL_FAN);
        passableMaterials.add(Material.DEAD_BUBBLE_CORAL_WALL_FAN);
        passableMaterials.add(Material.DEAD_FIRE_CORAL_WALL_FAN);
        passableMaterials.add(Material.DEAD_HORN_CORAL_WALL_FAN);
        passableMaterials.add(Material.DEAD_TUBE_CORAL_WALL_FAN);

        // Rails
        passableMaterials.add(Material.RAIL);
        passableMaterials.add(Material.POWERED_RAIL);
        passableMaterials.add(Material.DETECTOR_RAIL);
        passableMaterials.add(Material.ACTIVATOR_RAIL);

        // Redstone components
        passableMaterials.add(Material.REDSTONE_WIRE);
        passableMaterials.add(Material.REPEATER);
        passableMaterials.add(Material.COMPARATOR);

        // Misc
        passableMaterials.add(Material.SNOW);
        passableMaterials.add(Material.BROWN_MUSHROOM_BLOCK);
        passableMaterials.add(Material.RED_MUSHROOM_BLOCK);
        passableMaterials.add(Material.MUSHROOM_STEM);
        passableMaterials.add(Material.COBWEB);
        passableMaterials.add(Material.TORCH);
        passableMaterials.add(Material.WALL_TORCH);
        passableMaterials.add(Material.SOUL_TORCH);
        passableMaterials.add(Material.SOUL_WALL_TORCH);
        passableMaterials.add(Material.LADDER);
        passableMaterials.add(Material.LEVER);
        passableMaterials.add(Material.STONE_BUTTON);
        passableMaterials.add(Material.OAK_BUTTON);
        passableMaterials.add(Material.SPRUCE_BUTTON);
        passableMaterials.add(Material.BIRCH_BUTTON);
        passableMaterials.add(Material.JUNGLE_BUTTON);
        passableMaterials.add(Material.ACACIA_BUTTON);
        passableMaterials.add(Material.DARK_OAK_BUTTON);
        passableMaterials.add(Material.CRIMSON_BUTTON);
        passableMaterials.add(Material.WARPED_BUTTON);
        passableMaterials.add(Material.POLISHED_BLACKSTONE_BUTTON);
        passableMaterials.add(Material.LIGHT);

        // 1.19 additions
        passableMaterials.add(Material.MANGROVE_PROPAGULE);
        passableMaterials.add(Material.FROGSPAWN);

        // 1.20 additions
        passableMaterials.add(Material.CHERRY_BUTTON);
        passableMaterials.add(Material.BAMBOO_BUTTON);
        passableMaterials.add(Material.PINK_PETALS);
        passableMaterials.add(Material.TORCHFLOWER);
        passableMaterials.add(Material.TORCHFLOWER_CROP);
    }

    public boolean isPassable(Block block) {
        return passableMaterials.contains(block.getType()) || block.isPassable();
    }

    public boolean isSolid(Block block) {
        return block.getType().isSolid() && !block.getType().name().contains("LEAVES");
    }

    public boolean canStandOn(Block block) {
        return isSolid(block) || block.getType() == Material.FARMLAND;
    }

    public boolean isClimbable(Block block) {
        return block.getType() == Material.LADDER || block.getType() == Material.VINE ||
                block.getType() == Material.TWISTING_VINES || block.getType() == Material.WEEPING_VINES;
    }

    public boolean isWater(Block block) {
        return block.getType() == Material.WATER;
    }

    public boolean isLava(Block block) {
        return block.getType() == Material.LAVA;
    }

    public boolean isSafe(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        Block above = block.getRelative(BlockFace.UP);
        return isPassable(block) && isPassable(above) && canStandOn(below);
    }

    public int getHighestSolidBlock(int x, int z, int maxY) {
        for (int y = maxY; y >= 0; y--) {
            Block block = plugin.getServer().getWorlds().get(0).getBlockAt(x, y, z);
            if (canStandOn(block)) {
                return y + 1;
            }
        }
        return 0; // Bedrock level
    }

    public boolean canJumpTo(Block from, Block to) {
        // Check if the jump is possible (1 block up and 1 block over)
        if (to.getY() - from.getY() != 1 || from.getLocation().distanceSquared(to.getLocation()) > 2) {
            return false;
        }

        // Check if there's enough headroom
        Block aboveTo = to.getRelative(BlockFace.UP);
        return isPassable(to) && isPassable(aboveTo);
    }

    public boolean canDescendTo(Block from, Block to) {
        // Check if the descent is possible (up to 3 blocks down and 1 block over)
        if (from.getY() - to.getY() > 3 || from.getY() - to.getY() < 1 || from.getLocation().distanceSquared(to.getLocation()) > 2) {
            return false;
        }

        // Check if there's enough headroom
        Block aboveTo = to.getRelative(BlockFace.UP);
        return isPassable(to) && isPassable(aboveTo) && canStandOn(to.getRelative(BlockFace.DOWN));
    }

    public boolean isDangerous(Block block) {
        Material type = block.getType();
        return type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE ||
                type == Material.WITHER_ROSE || type == Material.CACTUS || type == Material.SWEET_BERRY_BUSH ||
                type == Material.MAGMA_BLOCK;
    }
}