package io.github.ihormel.endercraftbuildings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BlueprintTest {
    @Test
    void encodesAndDecodesExactly() {
        var blueprint = new Blueprint(2, 1, 3, List.of("minecraft:stone", "minecraft:oak_stairs[facing=north]"),
                new int[] {0, -1, 1, 0, -1, -1});
        var copy = Blueprint.decode(blueprint.encode());
        assertEquals(blueprint, copy);
        assertEquals(3, copy.blockCount());
        assertEquals(1, copy.cell(0, 0, 1));
        assertEquals(-1, copy.cell(1, 0, 2));
    }

    @Test
    void largestBlueprintStaysCompact() {
        int[] cells = new int[32 * 32 * 32];
        for (int i = 0; i < cells.length; i++) cells[i] = i % 3 == 0 ? -1 : i % 2;
        var blueprint = new Blueprint(32, 32, 32, List.of("minecraft:stone", "minecraft:glass"), cells);
        byte[] data = blueprint.encode();
        assertTrue(data.length < 16_000, "encoded size " + data.length);
        assertEquals(blueprint, Blueprint.decode(data));
    }

    @Test
    void rejectsInvalidShapesAndData() {
        assertThrows(IllegalArgumentException.class, () -> new Blueprint(33, 1, 1, List.of("a"), new int[33]));
        assertThrows(IllegalArgumentException.class, () -> new Blueprint(1, 1, 1, List.of("a"), new int[] {1}));
        assertThrows(IllegalArgumentException.class, () -> new Blueprint(1, 1, 2, List.of("a"), new int[] {0}));
        assertThrows(IllegalArgumentException.class, () -> Blueprint.decode(new byte[] {1, 2, 3}));
    }

    @Test
    void cellsAreDefensivelyCopied() {
        int[] cells = {0};
        var blueprint = new Blueprint(1, 1, 1, List.of("minecraft:stone"), cells);
        cells[0] = -1;
        blueprint.cells()[0] = -1;
        assertEquals(1, blueprint.blockCount());
    }

    @Test
    void facingFollowsMinecraftYaw() {
        assertEquals(Facing.SOUTH, Facing.fromYaw(0));
        assertEquals(Facing.WEST, Facing.fromYaw(90));
        assertEquals(Facing.NORTH, Facing.fromYaw(180));
        assertEquals(Facing.NORTH, Facing.fromYaw(-180));
        assertEquals(Facing.EAST, Facing.fromYaw(-90));
        assertEquals(Facing.EAST, Facing.fromYaw(265));
        assertEquals(Facing.SOUTH, Facing.fromYaw(-30));
        assertEquals(Facing.EAST, Facing.NORTH.right());
        assertEquals(Facing.NORTH, Facing.WEST.right());
    }

    @Test
    void holdTimeScalesAndCaps() {
        assertEquals(2.05, CrystalItems.holdSeconds(1), 1e-9);
        assertEquals(7.0, CrystalItems.holdSeconds(100), 1e-9);
        assertEquals(30.0, CrystalItems.holdSeconds(32768), 1e-9);
    }
}
