package io.github.ihormel.flipdisc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DiscPairsTest {
    @Test
    void everyDiscHasOneDifferentOppositeAndFlippingTwiceReturnsOriginal() {
        assertEquals(14, DiscPairs.OPPOSITES.size());
        DiscPairs.OPPOSITES.forEach((disc, opposite) -> {
            assertNotEquals(disc, opposite);
            assertEquals(disc, DiscPairs.OPPOSITES.get(opposite));
            assertEquals(2, Set.of(disc, opposite).size());
            assertTrue(disc.name().startsWith("MUSIC_DISC_"));
        });
    }

    @Test
    void specialDiscsAndElevenHaveNoOpposite() {
        for (Material special : Set.of(Material.MUSIC_DISC_PIGSTEP, Material.MUSIC_DISC_OTHERSIDE,
                Material.MUSIC_DISC_5, Material.MUSIC_DISC_RELIC, Material.MUSIC_DISC_CREATOR,
                Material.MUSIC_DISC_CREATOR_MUSIC_BOX, Material.MUSIC_DISC_PRECIPICE, Material.MUSIC_DISC_11)) {
            assertFalse(DiscPairs.OPPOSITES.containsKey(special), special.name());
            assertFalse(DiscPairs.OPPOSITES.containsValue(special), special.name());
        }
    }

    @Test
    void allCurrentDiscsAreExplicitlyPairedOrExcluded() {
        long count = java.util.Arrays.stream(Material.values())
                .filter(material -> material.name().startsWith("MUSIC_DISC_")).count();
        assertEquals(22, count, "Review new Minecraft discs before changing pair assignments");
        assertEquals(count - 8, DiscPairs.OPPOSITES.size());
    }

    @Test
    void nonC418ArtistsEachHaveADifferentArtistOnTheOtherSide() {
        assertEquals(Material.MUSIC_DISC_CAT, DiscPairs.OPPOSITES.get(Material.MUSIC_DISC_TEARS));
        assertEquals(Material.MUSIC_DISC_BLOCKS, DiscPairs.OPPOSITES.get(Material.MUSIC_DISC_LAVA_CHICKEN));
        assertEquals(Material.MUSIC_DISC_CHIRP, DiscPairs.OPPOSITES.get(Material.MUSIC_DISC_BOUNCE));
    }

    @Test
    void assignmentsCannotBeMutatedAtRuntime() {
        assertThrows(UnsupportedOperationException.class,
                () -> DiscPairs.OPPOSITES.put(Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_13));
    }
}
