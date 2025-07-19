package com.rednetty.server.utils.sounds;

import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public class SoundUtil {

    /**
     * Get appropriate hurt sound based on mob type
     */
    public static Sound getMobHurtSound(LivingEntity mob) {
        EntityType type = mob.getType();

        switch (type) {
            case ZOMBIE:
                return Sound.ENTITY_ZOMBIE_HURT;
            case ZOMBIE_VILLAGER:
                return Sound.ENTITY_ZOMBIE_VILLAGER_HURT;
            case ZOMBIFIED_PIGLIN:
                return Sound.ENTITY_ZOMBIFIED_PIGLIN_HURT;
            case HUSK:
                return Sound.ENTITY_HUSK_HURT;
            case DROWNED:
                return Sound.ENTITY_DROWNED_HURT;

            case SKELETON:
                return Sound.ENTITY_SKELETON_HURT;

            case STRAY:
                return Sound.ENTITY_STRAY_HURT;

            case WITHER_SKELETON:
                return Sound.ENTITY_WITHER_SKELETON_HURT;

            case SPIDER:
            case CAVE_SPIDER:
                return Sound.ENTITY_SPIDER_HURT;

            case CREEPER:
                return Sound.ENTITY_CREEPER_HURT;

            case ENDERMAN:
                return Sound.ENTITY_ENDERMAN_HURT;

            case WITCH:
                return Sound.ENTITY_WITCH_HURT;

            case SLIME:
            case MAGMA_CUBE:
                return Sound.ENTITY_SLIME_HURT;

            case GHAST:
                return Sound.ENTITY_GHAST_HURT;

            case BLAZE:
                return Sound.ENTITY_BLAZE_HURT;

            case WOLF:
                return Sound.ENTITY_WOLF_HURT;

            case COW:
            case MUSHROOM_COW:
                return Sound.ENTITY_COW_HURT;

            case PIG:
                return Sound.ENTITY_PIG_HURT;

            case SHEEP:
                return Sound.ENTITY_SHEEP_HURT;

            case CHICKEN:
                return Sound.ENTITY_CHICKEN_HURT;

            case HORSE:
                return Sound.ENTITY_HORSE_HURT;

            case VILLAGER:
                return Sound.ENTITY_VILLAGER_HURT;

            case IRON_GOLEM:
                return Sound.ENTITY_IRON_GOLEM_HURT;

            case WITHER:
                return Sound.ENTITY_WITHER_HURT;

            case ENDER_DRAGON:
                return Sound.ENTITY_ENDER_DRAGON_HURT;

            case GUARDIAN:
            case ELDER_GUARDIAN:
                return Sound.ENTITY_GUARDIAN_HURT;

            case RABBIT:
                return Sound.ENTITY_RABBIT_HURT;

            case POLAR_BEAR:
                return Sound.ENTITY_POLAR_BEAR_HURT;

            case LLAMA:
                return Sound.ENTITY_LLAMA_HURT;

            case PARROT:
                return Sound.ENTITY_PARROT_HURT;

            case VEX:
                return Sound.ENTITY_VEX_HURT;

            case VINDICATOR:
                return Sound.ENTITY_VINDICATOR_HURT;

            case EVOKER:
                return Sound.ENTITY_EVOKER_HURT;

            case ILLUSIONER:
                return Sound.ENTITY_ILLUSIONER_HURT;

            case BAT:
                return Sound.ENTITY_BAT_HURT;

            case OCELOT:
                return Sound.ENTITY_OCELOT_HURT;

            case CAT:
                return Sound.ENTITY_CAT_HURT;

            case SQUID:
                return Sound.ENTITY_SQUID_HURT;

            case SILVERFISH:
                return Sound.ENTITY_SILVERFISH_HURT;

            case ENDERMITE:
                return Sound.ENTITY_ENDERMITE_HURT;

            case SHULKER:
                return Sound.ENTITY_SHULKER_HURT;

            // Default fallback sounds for any missing or custom mobs
            default:
                // Try to determine a reasonable fallback based on mob characteristics
                if (type.name().contains("ZOMBIE")) {
                    return Sound.ENTITY_ZOMBIE_HURT;
                } else if (type.name().contains("SKELETON")) {
                    return Sound.ENTITY_SKELETON_HURT;
                } else if (type.name().contains("SPIDER")) {
                    return Sound.ENTITY_SPIDER_HURT;
                } else {
                    // Generic hurt sound for unknown mobs
                    return Sound.ENTITY_GENERIC_HURT;
                }
        }
    }

}
