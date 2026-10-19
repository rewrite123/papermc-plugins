package io.github.ihormel.minecarttravel;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

final class TestWorld {
    private TestWorld() {}

    static World withCollisionStubs(World delegate) {
        var world = mock(World.class, delegatesTo(delegate));
        var border = mock(org.bukkit.WorldBorder.class);
        when(border.isInside(any(Location.class))).thenReturn(true);
        doReturn(border).when(world).getWorldBorder();
        Map<List<Integer>, Block> blocks = new HashMap<>();
        doAnswer(call -> {
            int x = call.getArgument(0);
            int y = call.getArgument(1);
            int z = call.getArgument(2);
            return blocks.computeIfAbsent(List.of(x, y, z), unused -> {
                var block = mock(Block.class, delegatesTo(delegate.getBlockAt(x, y, z)));
                // MockBukkit has no collision shapes; the fixtures use full cubes,
                // air, and the explicit passable hazards tested here.
                doAnswer(ignored -> block.getType().isAir() || switch (block.getType()) {
                    case WATER, LAVA, FIRE, SOUL_FIRE, POWDER_SNOW, TORCH, REDSTONE_TORCH -> true;
                    default -> false;
                }).when(block).isPassable();
                doAnswer(ignored -> block.getType() == Material.WATER || block.getType() == Material.LAVA)
                        .when(block).isLiquid();
                return block;
            });
        }).when(world).getBlockAt(anyInt(), anyInt(), anyInt());
        doReturn(List.of()).when(world).getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());
        doReturn(List.of()).when(world).getNearbyEntities(any(org.bukkit.util.BoundingBox.class));
        return world;
    }
}
