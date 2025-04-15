package com.rednetty.server.mechanics.combat.death.remnant;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeathRemnant {
    private static final Logger LOGGER = Logger.getLogger(DeathRemnant.class.getName());
    private static final Map<BonePart, ItemStack> BONE_PARTS = new EnumMap<>(BonePart.class);

    private static final class Config {
        static final double GROUND_OFFSET = 0.5;
        static final int MAX_RIBS = 2;
        static final boolean DEBUG_MODE = true;
        static final double ITEM_HEIGHT_OFFSET = 0.02;
        static final int GROUND_SEARCH_RANGE = 15;
        static final double MIN_ITEM_DISTANCE = 0.8;
    }

    static {
        // Base bone types mapped to items
        BONE_PARTS.put(BonePart.SPINE, new ItemStack(Material.BONE));
        BONE_PARTS.put(BonePart.RIB, new ItemStack(Material.BONE));
        BONE_PARTS.put(BonePart.ARM, new ItemStack(Material.BONE));
        BONE_PARTS.put(BonePart.LEG, new ItemStack(Material.BONE));
    }

    private final UUID id;
    private final Location location;
    private final Map<BonePart, ArmorStand> boneStands;
    private final List<ArmorStand> itemStands;
    private final float rotation;
    private final NamespacedKey remnantKey;
    private boolean isValid = false;

    public DeathRemnant(Location location, List<ItemStack> items, UUID playerUuid,
                        String playerName, NamespacedKey key) {
        this.id = UUID.randomUUID();
        this.boneStands = new EnumMap<>(BonePart.class);
        this.itemStands = new ArrayList<>();
        this.remnantKey = key;
        this.rotation = (float) (Math.random() * 360);

        Location validatedLocation = findValidLocation(location.clone());
        if (validatedLocation == null) {
            this.location = location.clone();
            logError("Failed to validate location at %s", formatLocation(location));
            return;
        }
        this.location = validatedLocation;

        try {
            if (!constructSkeleton(playerUuid, playerName) || !validateStructure()) {
                logError("Failed skeleton construction at %s", formatLocation(this.location));
                cleanup();
                return;
            }

            if (items != null && !items.isEmpty()) {
                arrangeItems(items);
            }

            playSpawnEffects();
            isValid = true;
        } catch (Exception e) {
            logError("Construction failure: %s", e.getMessage());
            cleanup();
        }
    }

    // Location validation and ground checking methods
    private Location findValidLocation(Location initialLoc) {
        Block currentBlock = initialLoc.getBlock().getRelative(BlockFace.DOWN);
        if (isValidGround(currentBlock)) {
            return currentBlock.getLocation().add(0.5, Config.GROUND_OFFSET, 0.5);
        }

        Location downwardSearch = initialLoc.clone();
        for (int i = 0; i < Config.GROUND_SEARCH_RANGE; i++) {
            downwardSearch.subtract(0, 1, 0);
            Block check = downwardSearch.getBlock().getRelative(BlockFace.DOWN);
            if (isValidGround(check)) {
                return check.getLocation().add(0.5, Config.GROUND_OFFSET, 0.5);
            }
        }

        Location upwardSearch = initialLoc.clone();
        for (int i = 0; i < Config.GROUND_SEARCH_RANGE; i++) {
            upwardSearch.add(0, 1, 0);
            Block check = upwardSearch.getBlock().getRelative(BlockFace.DOWN);
            if (isValidGround(check)) {
                return check.getLocation().add(0.5, Config.GROUND_OFFSET, 0.5);
            }
        }

        return null;
    }

    private boolean isValidGround(Block block) {
        if (block.getType().isAir()) return false;
        BlockData data = block.getBlockData();
        if (data instanceof Stairs) {
            return ((Stairs) data).getHalf() == Stairs.Half.BOTTOM;
        }
        if (data instanceof Slab) {
            return ((Slab) data).getType() != Slab.Type.TOP;
        }
        return block.getType().isSolid();
    }

    // Skeleton construction methods
    private boolean constructSkeleton(UUID playerUuid, String playerName) {
        return createSpine() &&
                createSkull(playerUuid, playerName) &&
                createRibCage() &&
                createLimbs();
    }

    private boolean createSpine() {
        Location spineLoc = this.location.clone();
        ArmorStand spine = createBoneStand(spineLoc, BonePart.SPINE,
                DeathRemnantStructure.getPartRotation(rotation, BonePart.SPINE));
        return registerBone(BonePart.SPINE, spine);
    }

    private boolean createSkull(UUID playerUuid, String playerName) {
        Location skullLoc = DeathRemnantStructure.getSkullPosition(getSpineLocation(), rotation);
        // Change pitch from 90 to 0 degrees (first parameter)
        skullLoc.subtract(0, .25, 0);
        ArmorStand skullStand = createBoneStand(skullLoc, BonePart.SKULL,
                new EulerAngle(Math.toRadians(0), Math.toRadians(rotation), 0)); // Fix here


        ItemStack skullItem = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skullItem.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerUuid));
            meta.setDisplayName(playerName + "'s Remains");
            skullItem.setItemMeta(meta);
        }

        if (skullStand != null) skullStand.setHelmet(skullItem);
        return registerBone(BonePart.SKULL, skullStand);
    }

    private boolean createRibCage() {
        for (int i = 0; i < Config.MAX_RIBS; i++) {
            Location[] ribLocs = DeathRemnantStructure.getRibPositions(getSpineLocation(), rotation, i);
            if (!createRibPair(ribLocs, i)) return false;
        }
        return true;
    }

    private boolean createRibPair(Location[] locations, int index) {
        BonePart rightPart, leftPart;
        switch (index) {
            case 0:
                rightPart = BonePart.RIB_RIGHT_0;
                leftPart = BonePart.RIB_LEFT_0;
                break;
            case 1:
                rightPart = BonePart.RIB_RIGHT_1;
                leftPart = BonePart.RIB_LEFT_1;
                break;
            default:
                return false;
        }

        ArmorStand rightRib = createBoneStand(locations[0], rightPart,
                DeathRemnantStructure.getPartRotation(rotation + 90, rightPart));
        ArmorStand leftRib = createBoneStand(locations[1], leftPart,
                DeathRemnantStructure.getPartRotation(rotation - 90, leftPart));
        return registerBone(rightPart, rightRib) && registerBone(leftPart, leftRib);
    }

    private boolean createLimbs() {
        Location[] armLocs = DeathRemnantStructure.getArmPositions(getSpineLocation(), rotation);
        Location[] legLocs = DeathRemnantStructure.getLegPositions(getSpineLocation(), rotation);
        return createArmPair(armLocs) && createLegPair(legLocs);
    }

    private boolean createArmPair(Location[] locations) {
        ArmorStand rightArm = createBoneStand(locations[0], BonePart.ARM_RIGHT,
                DeathRemnantStructure.getPartRotation(rotation + 45, BonePart.ARM_RIGHT));
        ArmorStand leftArm = createBoneStand(locations[1], BonePart.ARM_LEFT,
                DeathRemnantStructure.getPartRotation(rotation - 45, BonePart.ARM_LEFT));
        return registerBone(BonePart.ARM_RIGHT, rightArm) && registerBone(BonePart.ARM_LEFT, leftArm);
    }

    private boolean createLegPair(Location[] locations) {
        ArmorStand rightLeg = createBoneStand(locations[0], BonePart.LEG_RIGHT,
                DeathRemnantStructure.getPartRotation(rotation + 20, BonePart.LEG_RIGHT));
        ArmorStand leftLeg = createBoneStand(locations[1], BonePart.LEG_LEFT,
                DeathRemnantStructure.getPartRotation(rotation - 20, BonePart.LEG_LEFT));
        return registerBone(BonePart.LEG_RIGHT, rightLeg) && registerBone(BonePart.LEG_LEFT, leftLeg);
    }

    // Bone stand creation and item management
    private ArmorStand createBoneStand(Location loc, BonePart part, EulerAngle angle) {
        if (loc == null || loc.getWorld() == null) return null;

        return loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setHeadPose(angle);
            stand.getPersistentDataContainer().set(remnantKey, PersistentDataType.BOOLEAN, true);

            if (part != BonePart.SKULL) {
                ItemStack bone = BONE_PARTS.get(part.getBasePart());
                if (bone != null) stand.setHelmet(bone.clone());
            }
        });
    }

    private void arrangeItems(List<ItemStack> items) {
        Vector[] positions = DeathRemnantStructure.calculateSafeScatteredPositions(
                items.size(),
                getSpineLocation(),
                boneStands.values(),
                Config.MIN_ITEM_DISTANCE
        );

        for (int i = 0; i < items.size() && i < positions.length; i++) {
            createItemStand(getSpineLocation().add(positions[i]), items.get(i));
        }
    }

    private void createItemStand(Location targetLoc, ItemStack item) {
        Location groundLoc = findExactGroundLocation(targetLoc);
        if (groundLoc == null) {
            logDebug("Failed to find ground for item at %s", formatLocation(targetLoc));
            return;
        }
        groundLoc.add(0, 0.15, 0);

        ArmorStand stand = groundLoc.getWorld().spawn(groundLoc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setSmall(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setBasePlate(false);
            s.setHelmet(item.clone());
            s.setHeadPose(new EulerAngle(
                    Math.toRadians(90),
                    Math.toRadians(new Random().nextInt(360)),
                    0
            ));
            s.getPersistentDataContainer().set(remnantKey, PersistentDataType.BOOLEAN, true);
        });
        itemStands.add(stand);
    }

    private Location findExactGroundLocation(Location loc) {
        Location current = loc.clone();
        for (int i = 0; i < Config.GROUND_SEARCH_RANGE; i++) {
            Block block = current.getBlock();
            if (block.getType().isSolid()) {
                return block.getLocation().add(0.5, Config.ITEM_HEIGHT_OFFSET, 0.5);
            }
            current.subtract(0, 1, 0);
        }
        return null;
    }

    // Effects and cleanup
    private void playSpawnEffects() {
        World world = location.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.SMOKE_NORMAL, location, 30, 0.5, 0.2, 0.5, 0.05);
        world.playSound(location, Sound.ENTITY_SKELETON_DEATH, 0.5f, 0.8f);

        boneStands.values().forEach(stand ->
                world.spawnParticle(Particle.WHITE_ASH, stand.getLocation(), 5, 0.1, 0.1, 0.1, 0.02)
        );
    }

    public void remove() {
        if (!isValid) return;
        cleanup();
        playRemovalEffects();
        isValid = false;
    }

    private void cleanup() {
        boneStands.values().forEach(stand -> {
            if (!stand.isDead()) stand.remove();
        });
        itemStands.forEach(stand -> {
            if (!stand.isDead()) stand.remove();
        });
        boneStands.clear();
        itemStands.clear();
    }

    private void playRemovalEffects() {
        World world = location.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.SMOKE_LARGE, location, 20, 0.5, 0.2, 0.5, 0.05);
        world.playSound(location, Sound.ENTITY_ITEM_BREAK, 0.7f, 0.9f);
    }

    // Validation and utility methods
    private boolean validateStructure() {
        boolean hasRibs = true;
        for (int i = 0; i < Config.MAX_RIBS; i++) {
            BonePart right = (i == 0) ? BonePart.RIB_RIGHT_0 : BonePart.RIB_RIGHT_1;
            BonePart left = (i == 0) ? BonePart.RIB_LEFT_0 : BonePart.RIB_LEFT_1;
            if (!boneStands.containsKey(right) || !boneStands.containsKey(left)) {
                hasRibs = false;
                break;
            }
        }
        return boneStands.containsKey(BonePart.SPINE) &&
                boneStands.containsKey(BonePart.SKULL) &&
                boneStands.containsKey(BonePart.ARM_RIGHT) &&
                boneStands.containsKey(BonePart.ARM_LEFT) &&
                boneStands.containsKey(BonePart.LEG_RIGHT) &&
                boneStands.containsKey(BonePart.LEG_LEFT) &&
                hasRibs;
    }

    private boolean registerBone(BonePart part, ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        boneStands.put(part, stand);
        return true;
    }

    private Location getSpineLocation() {
        return boneStands.containsKey(BonePart.SPINE) ?
                boneStands.get(BonePart.SPINE).getLocation() :
                location.clone();
    }

    // BonePart enum with proper base part resolution
    public enum BonePart {
        // Base types
        SPINE,
        RIB,
        ARM,
        LEG,
        SKULL,

        // Specific placements
        RIB_RIGHT_0,
        RIB_LEFT_0,
        RIB_RIGHT_1,
        RIB_LEFT_1,
        ARM_RIGHT,
        ARM_LEFT,
        LEG_RIGHT,
        LEG_LEFT;

        public BonePart getBasePart() {
            return switch (this) {
                case RIB_RIGHT_0, RIB_LEFT_0, RIB_RIGHT_1, RIB_LEFT_1 -> RIB;
                case ARM_RIGHT, ARM_LEFT -> ARM;
                case LEG_RIGHT, LEG_LEFT -> LEG;
                default -> this;
            };
        }
    }

    // Logging and helper methods
    private void logDebug(String format, Object... args) {
        if (Config.DEBUG_MODE) {
            LOGGER.log(Level.INFO, "[DeathRemnant] " + String.format(format, args));
        }
    }

    private void logError(String format, Object... args) {
        LOGGER.log(Level.SEVERE, "[DeathRemnant] " + String.format(format, args));
    }

    public Collection<ArmorStand> getEntityCollection() {
        List<ArmorStand> entities = new ArrayList<>(boneStands.values());
        entities.addAll(itemStands);
        return Collections.unmodifiableCollection(entities);
    }

    private String formatLocation(Location loc) {
        return String.format("World: %s, X: %.2f, Y: %.2f, Z: %.2f",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ()
        );
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location.clone();
    }

    public boolean isValid() {
        return isValid;
    }

    public float getRotation() {
        return rotation;
    }
}