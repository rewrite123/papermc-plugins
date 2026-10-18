package io.github.ihormel.tpcost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TeleportCostTest {
    @ParameterizedTest
    @CsvSource({
            "0, 3", "999.99, 3", "1000, 3", "1000.01, 4",
            "1333, 4", "1334, 5", "2000, 6", "3000, 9",
            "3330, 10", "8000, 24", "8000.01, 25", "8334, 25",
            "100000000, 25"
    })
    void chargesRoundedUpLevelsWithMinimumAndCap(double distance, int expected) {
        assertEquals(expected, TeleportCost.levels(distance, false));
        assertEquals(expected + 3, TeleportCost.levels(distance, true));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidDistances(double distance) {
        assertThrows(IllegalArgumentException.class, () -> TeleportCost.levels(distance, false));
    }

    @Test
    void measuresThreeDimensionalDistance() {
        World world = world();
        assertEquals(15, TeleportCost.between(
                new Location(world, 0, 0, 0), new Location(world, 3000, 4000, 0)));
    }

    @Test
    void comparesRawCoordinatesAcrossWorldsWithoutNetherScaling() {
        assertEquals(13, TeleportCost.between(
                new Location(world(), -330, 64, 0), new Location(world(), 3000, 64, 0)));
        assertEquals(6, TeleportCost.between(
                new Location(world(), 100, 64, 100), new Location(world(), 100, 64, 100)));
        assertEquals(28, TeleportCost.between(
                new Location(world(), 0, 64, 0), new Location(world(), 30000000, 64, 0)));
    }

    private World world() {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        return world;
    }
}
