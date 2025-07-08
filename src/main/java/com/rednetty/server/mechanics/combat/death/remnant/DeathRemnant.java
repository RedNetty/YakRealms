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

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  Creates purely decorative death remnants with robust entity management
 * - All remnants are visual-only decorative skeletons
 * - No item storage or display - items always drop separately on ground
 * - Enhanced entity tracking and cleanup integration
 * - Improved location validation and placement
 */
public class DeathRemnant {
    private static final Logger LOGGER = Logger.getLogger(DeathRemnant.class.getName());
    private static final Map<BonePart, ItemStack> BONE_PARTS = new EnumMap<>(BonePart.class);

    private static final class Config {
        static final double GROUND_OFFSET = 0.5;
        static final int MAX_RIBS = 2;
        static final boolean DEBUG_MODE = false; // Disabled for production
        static final int GROUND_SEARCH_RANGE = 10;
        static final double MIN_ENTITY_SPACING = 0.3;
    }

    static {
        // Initialize bone parts with consistent items
        BONE_PARTS.put(BonePart.SPINE, new ItemStack(Material.BONE));
        BONE_PARTS.put(BonePart.RIB, new ItemStack(Material.BONE));
        BONE_PARTS.put(BonePart.ARM, new ItemStack(Material.BONE));
        BONE_PARTS.put(BonePart.LEG, new ItemStack(Material.BONE));
    }

    private final UUID id;
    private final Location location;
    private final Map<BonePart, ArmorStand> boneStands;
    private final float rotation;
    private final NamespacedKey remnantKey;
    private final String playerName;
    private final UUID playerUuid;
    private boolean isValid = false;
    private final long creationTime;

    /**
     *  Creates a purely decorative death remnant with enhanced validation
     * @param location The location to create the remnant
     * @param items The items list (ignored - always decorative only)
     * @param playerUuid The UUID of the deceased player
     * @param playerName The name of the deceased player
     * @param key The namespaced key for tracking remnant entities
     */
    public DeathRemnant(Location location, List<ItemStack> items, UUID playerUuid,
                        String playerName, NamespacedKey key) {
        this.id = UUID.randomUUID();
        this.boneStands = new EnumMap<>(BonePart.class);
        this.remnantKey = key;
        this.playerName = playerName != null ? playerName : "Unknown";
        this.playerUuid = playerUuid != null ? playerUuid : UUID.randomUUID();
        this.rotation = (float) (Math.random() * 360);
        this.creationTime = System.currentTimeMillis();

        // Note: items parameter is completely ignored - remnants are always decorative only

        
        Location validatedLocation = findValidLocation(location.clone());
        if (validatedLocation == null) {
            this.location = location.clone();
            logError("Failed to validate location at %s for player %s", formatLocation(location), this.playerName);
            return;
        }
        this.location = validatedLocation;

        try {
            
            if (!constructSkeleton() || !validateStructure()) {
                logError("Failed skeleton construction at %s for player %s", formatLocation(this.location), this.playerName);
                cleanup();
                return;
            }

            // Play creation effects
            playSpawnEffects();
            isValid = true;

            logDebug("Created decorative death remnant for %s at %s (ID: %s)",
                    this.playerName, formatLocation(this.location), this.id.toString().substring(0, 8));

        } catch (Exception e) {
            logError("Construction failure for player %s: %s", this.playerName, e.getMessage());
            cleanup();
        }
    }

    /**
     *  Enhanced location validation with better ground detection
     */
    private Location findValidLocation(Location initialLoc) {
        if (initialLoc == null || initialLoc.getWorld() == null) {
            return null;
        }

        // Try the initial location first
        Block initialBlock = initialLoc.getBlock().getRelative(BlockFace.DOWN);
        if (isValidGround(initialBlock) && hasEnoughClearance(initialLoc)) {
            return adjustLocationForGround(initialBlock.getLocation().add(0.5, Config.GROUND_OFFSET, 0.5));
        }

        // Search downward for valid ground
        Location downwardSearch = initialLoc.clone();
        for (int i = 0; i < Config.GROUND_SEARCH_RANGE; i++) {
            downwardSearch.subtract(0, 1, 0);
            Block check = downwardSearch.getBlock().getRelative(BlockFace.DOWN);
            if (isValidGround(check) && hasEnoughClearance(downwardSearch)) {
                return adjustLocationForGround(check.getLocation().add(0.5, Config.GROUND_OFFSET, 0.5));
            }
        }

        // Search upward as fallback
        Location upwardSearch = initialLoc.clone();
        for (int i = 0; i < Config.GROUND_SEARCH_RANGE; i++) {
            upwardSearch.add(0, 1, 0);
            Block check = upwardSearch.getBlock().getRelative(BlockFace.DOWN);
            if (isValidGround(check) && hasEnoughClearance(upwardSearch)) {
                return adjustLocationForGround(check.getLocation().add(0.5, Config.GROUND_OFFSET, 0.5));
            }
        }

        return null; // No valid location found
    }

    /**
     * Check if there's enough clearance above the location
     */
    private boolean hasEnoughClearance(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        // Check 2 blocks high for clearance
        for (int y = 0; y < 2; y++) {
            Block check = loc.clone().add(0, y, 0).getBlock();
            if (!check.getType().isAir() && check.getType() != Material.WATER) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adjust location based on ground block type
     */
    private Location adjustLocationForGround(Location baseLoc) {
        Block groundBlock = baseLoc.getBlock().getRelative(BlockFace.DOWN);
        BlockData data = groundBlock.getBlockData();

        if (data instanceof Stairs) {
            Stairs stairs = (Stairs) data;
            if (stairs.getHalf() == Stairs.Half.TOP) {
                baseLoc.add(0, 0.5, 0);
            }
        } else if (data instanceof Slab) {
            Slab slab = (Slab) data;
            if (slab.getType() == Slab.Type.TOP) {
                baseLoc.add(0, 0.5, 0);
            }
        }

        return baseLoc;
    }

    /**
     * Enhanced ground validation
     */
    private boolean isValidGround(Block block) {
        if (block == null || block.getType().isAir()) return false;

        BlockData data = block.getBlockData();
        if (data instanceof Stairs) {
            return ((Stairs) data).getHalf() == Stairs.Half.BOTTOM;
        }
        if (data instanceof Slab) {
            return ((Slab) data).getType() != Slab.Type.TOP;
        }

        return block.getType().isSolid();
    }

    /**
     *  Enhanced skeleton construction with better error handling
     */
    private boolean constructSkeleton() {
        try {
            // Create components in order with validation
            if (!createSpine()) {
                logError("Failed to create spine for remnant %s", id.toString().substring(0, 8));
                return false;
            }

            if (!createSkull()) {
                logError("Failed to create skull for remnant %s", id.toString().substring(0, 8));
                return false;
            }

            if (!createRibCage()) {
                logError("Failed to create rib cage for remnant %s", id.toString().substring(0, 8));
                return false;
            }

            if (!createLimbs()) {
                logError("Failed to create limbs for remnant %s", id.toString().substring(0, 8));
                return false;
            }

            return true;
        } catch (Exception e) {
            logError("Exception during skeleton construction: %s", e.getMessage());
            return false;
        }
    }

    private boolean createSpine() {
        Location spineLoc = this.location.clone();
        ArmorStand spine = createBoneStand(spineLoc, BonePart.SPINE,
                DeathRemnantStructure.getPartRotation(rotation, BonePart.SPINE));
        return registerBone(BonePart.SPINE, spine);
    }

    private boolean createSkull() {
        Location skullLoc = DeathRemnantStructure.getSkullPosition(getSpineLocation(), rotation);
        skullLoc.subtract(0, .25, 0);
        ArmorStand skullStand = createBoneStand(skullLoc, BonePart.SKULL,
                new EulerAngle(Math.toRadians(0), Math.toRadians(rotation), 0));

        if (skullStand != null) {
            ItemStack skullItem = createPlayerSkull();
            skullStand.setHelmet(skullItem);
        }

        return registerBone(BonePart.SKULL, skullStand);
    }

    /**
     * Create a player skull item
     */
    private ItemStack createPlayerSkull() {
        ItemStack skullItem = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skullItem.getItemMeta();
        if (meta != null) {
            try {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerUuid));
                meta.setDisplayName(ChatColor.GRAY + playerName + "'s Remains");
                skullItem.setItemMeta(meta);
            } catch (Exception e) {
                logError("Error setting skull meta: %s", e.getMessage());
            }
        }
        return skullItem;
    }

    private boolean createRibCage() {
        for (int i = 0; i < Config.MAX_RIBS; i++) {
            Location[] ribLocs = DeathRemnantStructure.getRibPositions(getSpineLocation(), rotation, i);
            if (!createRibPair(ribLocs, i)) {
                return false;
            }
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

    /**
     *  Enhanced bone stand creation with better error handling
     */
    private ArmorStand createBoneStand(Location loc, BonePart part, EulerAngle angle) {
        if (loc == null || loc.getWorld() == null) {
            logError("Invalid location for bone stand creation: %s", part);
            return null;
        }

        try {
            return loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
                // Configure armor stand properties
                stand.setVisible(false);
                stand.setSmall(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.setInvulnerable(true);
                stand.setHeadPose(angle);

                
                stand.getPersistentDataContainer().set(remnantKey, PersistentDataType.STRING, id.toString());

                // Set helmet item if not skull
                if (part != BonePart.SKULL) {
                    ItemStack bone = BONE_PARTS.get(part.getBasePart());
                    if (bone != null) {
                        stand.setHelmet(bone.clone());
                    }
                }
            });
        } catch (Exception e) {
            logError("Error creating armor stand for part %s: %s", part, e.getMessage());
            return null;
        }
    }

    /**
     * Play spawn effects
     */
    private void playSpawnEffects() {
        World world = location.getWorld();
        if (world == null) return;

        try {
            // Main spawn effects
            world.spawnParticle(Particle.SMOKE_NORMAL, location, 30, 0.5, 0.2, 0.5, 0.05);
            world.playSound(location, Sound.ENTITY_SKELETON_DEATH, 0.5f, 0.8f);

            // Individual bone effects
            boneStands.values().forEach(stand -> {
                if (stand != null && !stand.isDead()) {
                    world.spawnParticle(Particle.WHITE_ASH, stand.getLocation(), 5, 0.1, 0.1, 0.1, 0.02);
                }
            });
        } catch (Exception e) {
            logError("Error playing spawn effects: %s", e.getMessage());
        }
    }

    /**
     *  Enhanced removal with comprehensive cleanup
     */
    public void remove() {
        if (!isValid) return;

        try {
            cleanup();
            playRemovalEffects();
        } catch (Exception e) {
            logError("Error during remnant removal: %s", e.getMessage());
        } finally {
            isValid = false;
        }
    }

    /**
     *  Comprehensive cleanup of all entities
     */
    private void cleanup() {
        List<ArmorStand> toRemove = new ArrayList<>();

        // Collect all valid entities
        for (ArmorStand stand : boneStands.values()) {
            if (stand != null && !stand.isDead()) {
                toRemove.add(stand);
            }
        }

        // Remove entities safely
        for (ArmorStand stand : toRemove) {
            try {
                stand.remove();
            } catch (Exception e) {
                logError("Error removing armor stand: %s", e.getMessage());
            }
        }

        boneStands.clear();
    }

    /**
     * Play removal effects
     */
    private void playRemovalEffects() {
        World world = location.getWorld();
        if (world == null) return;

        try {
            world.spawnParticle(Particle.SMOKE_LARGE, location, 20, 0.5, 0.2, 0.5, 0.05);
            world.playSound(location, Sound.ENTITY_ITEM_BREAK, 0.7f, 0.9f);
        } catch (Exception e) {
            logError("Error playing removal effects: %s", e.getMessage());
        }
    }

    /**
     *  Enhanced structure validation
     */
    private boolean validateStructure() {
        // Check essential parts
        if (!boneStands.containsKey(BonePart.SPINE) ||
                !boneStands.containsKey(BonePart.SKULL) ||
                !boneStands.containsKey(BonePart.ARM_RIGHT) ||
                !boneStands.containsKey(BonePart.ARM_LEFT) ||
                !boneStands.containsKey(BonePart.LEG_RIGHT) ||
                !boneStands.containsKey(BonePart.LEG_LEFT)) {
            return false;
        }

        // Check ribs
        for (int i = 0; i < Config.MAX_RIBS; i++) {
            BonePart right = (i == 0) ? BonePart.RIB_RIGHT_0 : BonePart.RIB_RIGHT_1;
            BonePart left = (i == 0) ? BonePart.RIB_LEFT_0 : BonePart.RIB_LEFT_1;
            if (!boneStands.containsKey(right) || !boneStands.containsKey(left)) {
                return false;
            }
        }

        // Validate all entities are alive
        for (ArmorStand stand : boneStands.values()) {
            if (stand == null || stand.isDead()) {
                return false;
            }
        }

        return true;
    }

    private boolean registerBone(BonePart part, ArmorStand stand) {
        if (stand == null || stand.isDead()) {
            logError("Failed to register bone part: %s (null or dead entity)", part);
            return false;
        }
        boneStands.put(part, stand);
        return true;
    }

    private Location getSpineLocation() {
        ArmorStand spine = boneStands.get(BonePart.SPINE);
        return spine != null ? spine.getLocation() : location.clone();
    }

    /**
     * BonePart enum with proper base part resolution
     */
    public enum BonePart {
        // Base types
        SPINE, RIB, ARM, LEG, SKULL,

        // Specific placements
        RIB_RIGHT_0, RIB_LEFT_0, RIB_RIGHT_1, RIB_LEFT_1,
        ARM_RIGHT, ARM_LEFT, LEG_RIGHT, LEG_LEFT;

        public BonePart getBasePart() {
            return switch (this) {
                case RIB_RIGHT_0, RIB_LEFT_0, RIB_RIGHT_1, RIB_LEFT_1 -> RIB;
                case ARM_RIGHT, ARM_LEFT -> ARM;
                case LEG_RIGHT, LEG_LEFT -> LEG;
                default -> this;
            };
        }
    }

    // Logging methods
    private void logDebug(String format, Object... args) {
        if (Config.DEBUG_MODE) {
            LOGGER.log(Level.INFO, "[DeathRemnant] " + String.format(format, args));
        }
    }

    private void logError(String format, Object... args) {
        LOGGER.log(Level.WARNING, "[DeathRemnant] " + String.format(format, args));
    }

    private String formatLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "Unknown location";
        }
        return String.format("World: %s, X: %.2f, Y: %.2f, Z: %.2f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location.clone();
    }

    public boolean isValid() {
        return isValid && validateStructure();
    }

    public float getRotation() {
        return rotation;
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Get all entities belonging to this remnant
     */
    public Collection<ArmorStand> getEntityCollection() {
        return Collections.unmodifiableCollection(boneStands.values());
    }

    /**
     *  Always returns 0 since remnants are purely decorative
     */
    public int getItemCount() {
        return 0;
    }

    /**
     *  Always returns true since remnants are purely decorative
     */
    public boolean isDecorativeOnly() {
        return true;
    }

    /**
     * Get the number of entities in this remnant
     */
    public int getEntityCount() {
        return boneStands.size();
    }

    /**
     * Check if the remnant has aged beyond a certain time
     */
    public boolean isAged(long maxAgeMillis) {
        return System.currentTimeMillis() - creationTime > maxAgeMillis;
    }
}