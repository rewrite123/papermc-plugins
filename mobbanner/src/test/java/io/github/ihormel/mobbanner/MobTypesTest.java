package io.github.ihormel.mobbanner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MobTypesTest {
    @ParameterizedTest
    @ValueSource(longs = {100, 200, 300, 500, 1000, 1500, 2000, 3000, 4000, 10000, 100000})
    void awardsExactlyAtMilestones(long total) {
        assertTrue(MobTypes.rewards(total));
        assertFalse(MobTypes.rewards(total - 1));
        assertFalse(MobTypes.rewards(total + 1));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1000, 0, 1, 99, 400, 600, 800, 1300, 1800, 2500, 2800, Long.MAX_VALUE})
    void doesNotRewardOtherCounts(long total) {
        assertFalse(MobTypes.rewards(total));
    }

    @Test
    void includesBatAndHostileVariantsButNotNeutralOrPassiveMobs() {
        assertEquals(38, MobTypes.SUPPORTED.size());
        assertTrue(MobTypes.SUPPORTED.contains(EntityType.ENDERMAN));
        assertTrue(MobTypes.SUPPORTED.containsAll(Set.of(EntityType.BAT, EntityType.ZOMBIE,
                EntityType.HUSK, EntityType.DROWNED, EntityType.SPIDER, EntityType.CAVE_SPIDER,
                EntityType.PARCHED, EntityType.CREAKING, EntityType.ENDER_DRAGON, EntityType.WITHER)));
        for (EntityType excluded : new EntityType[]{EntityType.PIGLIN,
                EntityType.ZOMBIFIED_PIGLIN, EntityType.WOLF, EntityType.COW, EntityType.BEE}) {
            assertFalse(MobTypes.SUPPORTED.contains(excluded));
        }
    }

    @Test
    @SuppressWarnings("removal")
    void limitsBlockedSpawnReasonsPrecisely() {
        Set<SpawnReason> blocked = Set.of(SpawnReason.NATURAL, SpawnReason.CHUNK_GEN,
                SpawnReason.REINFORCEMENTS, SpawnReason.PATROL);
        for (SpawnReason reason : SpawnReason.values()) {
            assertEquals(blocked.contains(reason), MobTypes.blocks(reason), reason.name());
        }
    }
}
