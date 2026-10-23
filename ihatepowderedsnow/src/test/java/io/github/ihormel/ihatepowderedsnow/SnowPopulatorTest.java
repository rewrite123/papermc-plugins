package io.github.ihormel.ihatepowderedsnow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SnowPopulatorTest {
    record Position(int x, int y, int z) {}

    private Map<Position, Material> generate(int chunkX, int chunkZ, int minimumY, int maximumY) {
        var world = mock(WorldInfo.class);
        when(world.getMinHeight()).thenReturn(minimumY);
        when(world.getMaxHeight()).thenReturn(maximumY);
        var region = mock(LimitedRegion.class);
        int x = chunkX << 4;
        int z = chunkZ << 4;
        var powder = Set.of(new Position(x, minimumY, z), new Position(x + 15, maximumY - 1, z + 15),
                new Position(x + 8, minimumY + 1, z + 7));
        var replacements = new HashMap<Position, Material>();
        int[] reads = {0};
        when(region.getType(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int bx = invocation.getArgument(0);
            int by = invocation.getArgument(1);
            int bz = invocation.getArgument(2);
            assertTrue(bx >= x && bx < x + 16 && bz >= z && bz < z + 16);
            assertTrue(by >= minimumY && by < maximumY);
            reads[0]++;
            return powder.contains(new Position(bx, by, bz)) ? Material.POWDER_SNOW : Material.STONE;
        });
        doAnswer(invocation -> {
            var position = new Position(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            assertNull(replacements.put(position, invocation.getArgument(3)), "A block should only be replaced once");
            return null;
        }).when(region).setType(anyInt(), anyInt(), anyInt(), any(Material.class));
        new SnowPopulator().populate(world, new Random(1), chunkX, chunkZ, region);
        assertEquals(256 * (maximumY - minimumY), reads[0]);
        assertEquals(powder, replacements.keySet());
        assertTrue(replacements.values().stream().allMatch(material -> material == Material.SNOW_BLOCK));
        verify(region, never()).getWorld();
        return replacements;
    }

    @ParameterizedTest
    @CsvSource({"0,0,-64,320", "-2,-3,-64,0", "5,-7,0,256", "1874999,-1875000,-64,320"})
    void replacesOnlyPowderAcrossFullChunkAndHeightIncludingNegativeCoordinates(
            int chunkX, int chunkZ, int minY, int maxY) {
        generate(chunkX, chunkZ, minY, maxY);
    }

    @Test
    void chunksWithoutPowderAreNotWritten() {
        var world = mock(WorldInfo.class);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(4);
        for (var type : new Material[] {Material.AIR, Material.SNOW, Material.SNOW_BLOCK, Material.WATER, Material.ICE}) {
            var region = mock(LimitedRegion.class);
            when(region.getType(anyInt(), anyInt(), anyInt())).thenReturn(type);
            new SnowPopulator().populate(world, new Random(), 0, 0, region);
            verify(region, never()).setType(anyInt(), anyInt(), anyInt(), any(Material.class));
        }
    }

    @Test
    void onePopulatorSupportsConcurrentIndependentGeneration() throws Exception {
        var populator = new SnowPopulator();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 4).mapToObj(chunk -> executor.submit(() -> {
                var world = mock(WorldInfo.class);
                when(world.getMinHeight()).thenReturn(-4);
                when(world.getMaxHeight()).thenReturn(4);
                var region = mock(LimitedRegion.class);
                when(region.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.POWDER_SNOW);
                populator.populate(world, new Random(), chunk, -chunk, region);
                verify(region, times(2048)).setType(anyInt(), anyInt(), anyInt(), eq(Material.SNOW_BLOCK));
            })).toList();
            for (var task : tasks) task.get();
        }
    }
}
