package io.github.ihormel.mobbanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.junit.jupiter.api.Test;

class WardPyramidTest extends PaperTest {
    @Test
    void requiresAllThirtyFiveValuableBlocksAndUpdatesImmediately() {
        var world = server.addSimpleWorld("pyramid");
        var banner = build(world, 8, 70, 8);
        assertTrue(WardPyramid.powered(banner));
        world.getBlockAt(10, 67, 10).setType(Material.STONE);
        assertFalse(WardPyramid.powered(banner));
        world.getBlockAt(10, 67, 10).setType(Material.NETHERITE_BLOCK);
        assertTrue(WardPyramid.powered(banner));
        world.getBlockAt(8, 69, 8).setType(Material.AIR);
        assertFalse(WardPyramid.powered(banner));
    }

    @Test
    void doesNotAcceptCopperOrWallBanner() {
        var world = server.addSimpleWorld("pyramid");
        var banner = build(world, 8, 70, 8);
        world.getBlockAt(8, 69, 8).setType(Material.COPPER_BLOCK);
        assertFalse(WardPyramid.powered(banner));
        world.getBlockAt(8, 69, 8).setType(Material.IRON_BLOCK);
        world.getBlockAt(8, 70, 8).setType(Material.PURPLE_WALL_BANNER);
        assertFalse(WardPyramid.powered((Banner) world.getBlockAt(8, 70, 8).getState()));
    }

    @Test
    void doesNotLoadUnloadedNeighboringChunks() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        when(world.getMinHeight()).thenReturn(-64);
        Banner banner = mock(Banner.class);
        when(banner.getType()).thenReturn(Material.PURPLE_BANNER);
        when(banner.getWorld()).thenReturn(world);
        when(banner.getY()).thenReturn(70);
        assertFalse(WardPyramid.powered(banner));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    static Banner build(World world, int x, int y, int z) {
        Material[] materials = {Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.EMERALD_BLOCK,
                Material.DIAMOND_BLOCK, Material.NETHERITE_BLOCK};
        for (int depth = 1; depth <= 3; depth++) {
            for (int dx = 1 - depth; dx < depth; dx++) {
                for (int dz = 1 - depth; dz < depth; dz++) {
                    world.loadChunk((x + dx) >> 4, (z + dz) >> 4);
                    world.getBlockAt(x + dx, y - depth, z + dz)
                            .setType(materials[Math.floorMod(dx + dz + depth, materials.length)]);
                }
            }
        }
        world.getBlockAt(x, y, z).setType(Material.PURPLE_BANNER);
        return (Banner) world.getBlockAt(x, y, z).getState();
    }
}
