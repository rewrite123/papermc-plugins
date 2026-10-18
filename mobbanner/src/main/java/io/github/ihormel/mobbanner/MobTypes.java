package io.github.ihormel.mobbanner;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

final class MobTypes {
    static final Set<EntityType> SUPPORTED = Set.copyOf(EnumSet.of(
            EntityType.BAT, EntityType.BLAZE, EntityType.BOGGED, EntityType.BREEZE,
            EntityType.CAVE_SPIDER, EntityType.CREAKING, EntityType.CREEPER,
            EntityType.DROWNED, EntityType.ELDER_GUARDIAN, EntityType.ENDER_DRAGON,
            EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.EVOKER, EntityType.GHAST, EntityType.GUARDIAN,
            EntityType.HOGLIN, EntityType.HUSK, EntityType.MAGMA_CUBE, EntityType.PARCHED,
            EntityType.PHANTOM, EntityType.PIGLIN_BRUTE, EntityType.PILLAGER, EntityType.RAVAGER,
            EntityType.SHULKER, EntityType.SILVERFISH, EntityType.SKELETON, EntityType.SLIME,
            EntityType.SPIDER, EntityType.STRAY, EntityType.VEX, EntityType.VINDICATOR,
            EntityType.WARDEN, EntityType.WITCH, EntityType.WITHER, EntityType.WITHER_SKELETON,
            EntityType.ZOGLIN, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER));

    private MobTypes() {}

    @SuppressWarnings("removal")
    static boolean blocks(SpawnReason reason) {
        return reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN
                || reason == SpawnReason.REINFORCEMENTS || reason == SpawnReason.PATROL;
    }

    static boolean rewards(long kills) {
        return kills == 100 || kills == 200 || kills == 300
                || (kills >= 500 && kills <= 2000 && kills % 500 == 0)
                || (kills > 2000 && kills % 1000 == 0);
    }

    static String name(EntityType type) {
        String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        for (int index = 0; index < words.length; index++) {
            words[index] = Character.toUpperCase(words[index].charAt(0)) + words[index].substring(1);
        }
        return String.join(" ", words);
    }
}
