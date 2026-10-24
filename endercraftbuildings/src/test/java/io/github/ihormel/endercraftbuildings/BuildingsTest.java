package io.github.ihormel.endercraftbuildings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class BuildingsTest {
    private ServerMock server;
    private WorldMock world;
    private Buildings buildings;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = new WorldMock(Material.AIR, 0);
        server.addWorld(world);
        buildings = new Buildings(server, new TestRules(), new Materials(server, new TestRules()));
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    private Block at(int x, int y, int z) { return world.getBlockAt(x, y, z); }

    private void stairs(int x, int y, int z, BlockFace facing) {
        var data = (Stairs) server.createBlockData(Material.OAK_STAIRS);
        data.setFacing(facing);
        at(x, y, z).setBlockData(data);
    }

    private void sample() {
        at(0, 64, 0).setType(Material.STONE);
        at(1, 64, 0).setType(Material.OAK_PLANKS);
        at(0, 65, 2).setType(Material.GLASS);
        stairs(1, 65, 1, BlockFace.NORTH);
    }

    private void place(Buildings.Scan scan, Block anchor, Facing facing) {
        for (var target : buildings.plan(scan.blueprint(), anchor, facing)) target.block().setBlockData(target.data());
    }

    @Test
    void sameFacingPlacementIsAPureTranslation() {
        sample();
        var scan = buildings.scan(at(1, 65, 2), at(0, 64, 0), Facing.NORTH);
        assertEquals(2, scan.blueprint().width());
        assertEquals(2, scan.blueprint().height());
        assertEquals(3, scan.blueprint().length());
        assertEquals(4, scan.blueprint().blockCount());
        assertEquals(0, scan.skipped());
        place(scan, at(10, 64, 10), Facing.NORTH);
        assertEquals(Material.STONE, at(10, 64, 8).getType());
        assertEquals(Material.OAK_PLANKS, at(11, 64, 8).getType());
        assertEquals(Material.GLASS, at(10, 65, 10).getType());
        assertEquals(BlockFace.NORTH, ((Stairs) at(11, 65, 9).getBlockData()).getFacing());
        assertTrue(at(11, 64, 10).isEmpty());
    }

    @Test
    void placementRotatesToTheNewFacing() {
        sample();
        var scan = buildings.scan(at(0, 64, 0), at(1, 65, 2), Facing.NORTH);
        place(scan, at(10, 64, 10), Facing.EAST);
        assertEquals(Material.STONE, at(12, 64, 10).getType());
        assertEquals(Material.OAK_PLANKS, at(12, 64, 11).getType());
        assertEquals(Material.GLASS, at(10, 65, 10).getType());
        assertEquals(BlockFace.EAST, ((Stairs) at(11, 65, 11).getBlockData()).getFacing());
    }

    @Test
    void everyScanAndPlaceFacingPairKeepsTheBuildingRelativeToTheViewer() {
        for (Facing scanFacing : Facing.values()) {
            for (Facing placeFacing : Facing.values()) {
                cleanup();
                setup();
                sample();
                var scan = buildings.scan(at(0, 64, 0), at(1, 65, 2), scanFacing);
                var expected = buildings.scan(at(0, 64, 0), at(1, 65, 2), scanFacing).blueprint();
                Block anchor = at(20, 64, 20);
                place(scan, anchor, placeFacing);
                Block[] corners = Buildings.corners(scan.blueprint(), anchor, placeFacing);
                var rescanned = buildings.scan(corners[0], corners[1], placeFacing);
                assertEquals(expected, rescanned.blueprint(), scanFacing + " -> " + placeFacing);
            }
        }
    }

    @Test
    void sideFacingScanTranslatesAndKeepsStairsDirection() {
        stairs(5, 64, 5, BlockFace.EAST);
        at(6, 64, 5).setType(Material.STONE);
        var scan = buildings.scan(at(5, 64, 5), at(6, 64, 5), Facing.EAST);
        assertEquals("minecraft:oak_stairs", scan.blueprint().palette().getFirst().split("\\[")[0]);
        assertTrue(scan.blueprint().palette().getFirst().contains("facing=north"));
        place(scan, at(30, 64, 30), Facing.EAST);
        assertEquals(BlockFace.EAST, ((Stairs) at(30, 64, 30).getBlockData()).getFacing());
        assertEquals(Material.STONE, at(31, 64, 30).getType());
    }

    @Test
    void unobtainableBlocksAndFluidsAreSkippedAndFreeContentsStripped() {
        at(0, 64, 0).setType(Material.BEDROCK);
        at(1, 64, 0).setType(Material.WATER);
        at(2, 64, 0).setType(Material.SPAWNER);
        at(0, 65, 0).setType(Material.INFESTED_STONE);
        at(1, 65, 0).setType(Material.WATER_CAULDRON);
        var stairs = (Stairs) server.createBlockData(Material.OAK_STAIRS);
        stairs.setWaterlogged(true);
        at(2, 65, 0).setBlockData(stairs);
        var scan = buildings.scan(at(0, 64, 0), at(2, 65, 0), Facing.NORTH);
        assertEquals(4, scan.skipped());
        assertEquals(2, scan.blueprint().blockCount());
        assertTrue(scan.blueprint().palette().contains("minecraft:cauldron"));
        for (String entry : scan.blueprint().palette()) {
            var data = server.createBlockData(entry);
            if (data instanceof Waterlogged waterlogged) assertFalse(waterlogged.isWaterlogged());
        }
    }

    @Test
    void materialCostCountsMultiBlockItemsOnce() {
        var door = (Door) server.createBlockData(Material.OAK_DOOR);
        door.setHalf(Bisected.Half.BOTTOM);
        at(0, 64, 0).setBlockData(door);
        var top = (Door) door.clone();
        top.setHalf(Bisected.Half.TOP);
        at(0, 65, 0).setBlockData(top);
        var bed = (Bed) server.createBlockData(Material.RED_BED);
        bed.setPart(Bed.Part.FOOT);
        at(1, 64, 0).setBlockData(bed);
        var head = (Bed) bed.clone();
        head.setPart(Bed.Part.HEAD);
        at(1, 64, 1).setBlockData(head);
        var slab = (Slab) server.createBlockData(Material.OAK_SLAB);
        slab.setType(Slab.Type.DOUBLE);
        at(2, 64, 0).setBlockData(slab);
        var candle = (Candle) server.createBlockData(Material.CANDLE);
        candle.setCandles(3);
        at(2, 65, 0).setBlockData(candle);
        at(2, 64, 1).setType(Material.WALL_TORCH);
        at(0, 64, 1).setType(Material.STONE);
        at(0, 65, 1).setType(Material.STONE);
        var scan = buildings.scan(at(0, 64, 0), at(2, 65, 1), Facing.SOUTH);
        assertEquals(Map.of(Material.OAK_DOOR, 1, Material.RED_BED, 1, Material.OAK_SLAB, 2, Material.CANDLE, 3,
                Material.TORCH, 1, Material.STONE, 2), buildings.cost(scan.blueprint()));
    }

    @Test
    void selectionsAreLimitedToThirtyTwoPerSide() {
        assertTrue(Buildings.withinLimit(at(0, 0, 0), at(31, 31, -31)));
        assertFalse(Buildings.withinLimit(at(0, 0, 0), at(32, 0, 0)));
        assertFalse(Buildings.withinLimit(at(0, 0, 0), at(0, 32, 0)));
        assertThrows(IllegalArgumentException.class, () -> buildings.scan(at(0, 0, 0), at(0, 0, 40), Facing.NORTH));
    }

    @Test
    void placementNeedsDryAirOrReplaceableSpaceInsideTheWorld() {
        at(0, 64, 0).setType(Material.STONE);
        var scan = buildings.scan(at(0, 64, 0), at(0, 64, 0), Facing.NORTH);
        assertEquals(0, Buildings.obstructions(buildings.plan(scan.blueprint(), at(5, 64, 5), Facing.NORTH)));
        at(5, 64, 5).setType(Material.SHORT_GRASS);
        assertEquals(0, Buildings.obstructions(buildings.plan(scan.blueprint(), at(5, 64, 5), Facing.NORTH)));
        at(5, 64, 5).setType(Material.WATER);
        assertEquals(1, Buildings.obstructions(buildings.plan(scan.blueprint(), at(5, 64, 5), Facing.NORTH)));
        at(5, 64, 5).setType(Material.DIRT);
        assertEquals(1, Buildings.obstructions(buildings.plan(scan.blueprint(), at(5, 64, 5), Facing.NORTH)));
        assertTrue(Buildings.inWorld(scan.blueprint(), at(5, world.getMaxHeight() - 1, 5)));
        at(0, 65, 0).setType(Material.STONE);
        var tall = buildings.scan(at(0, 64, 0), at(0, 65, 0), Facing.NORTH);
        assertFalse(Buildings.inWorld(tall.blueprint(), at(5, world.getMaxHeight() - 1, 5)));
        assertTrue(Buildings.inWorld(tall.blueprint(), at(5, world.getMaxHeight() - 2, 5)));
    }
}
