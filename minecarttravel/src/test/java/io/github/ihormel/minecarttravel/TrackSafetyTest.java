package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrackSafetyTest extends PaperTest {
    private World world;
    private TrackPos start;
    private final Vector east = new Vector(1, 0, 0);

    @BeforeEach
    void buildTrack() {
        world = TestWorld.withCollisionStubs(server.addSimpleWorld("world"));
        for (int x = 0; x <= 15; x++) {
            world.getBlockAt(x, 63, 8).setType(Material.STONE);
            var block = world.getBlockAt(x, 64, 8);
            block.setType(Material.RAIL);
            var rail = (Rail) block.getBlockData();
            rail.setShape(Rail.Shape.EAST_WEST);
            block.setBlockData(rail);
        }
        world.getChunkAt(0, 0).load();
        start = new TrackPos(world.getUID(), 1, 64, 8);
    }

    private boolean clear() {
        return TrackSafety.clear(world, start, RailShape.EAST_WEST, east, 12);
    }

    @Test
    void longClearStraightTrackAllowsTheConfiguredBoost() {
        assertTrue(clear());
    }

    @Test
    void gapCurveSlopeAndSolidHeadroomDisableBoost() {
        var block = world.getBlockAt(9, 64, 8);
        block.setType(Material.AIR);
        assertFalse(clear());
        block.setType(Material.RAIL);
        var rail = (Rail) block.getBlockData();
        rail.setShape(Rail.Shape.SOUTH_WEST);
        block.setBlockData(rail);
        assertFalse(clear());
        rail.setShape(Rail.Shape.ASCENDING_EAST);
        block.setBlockData(rail);
        assertFalse(clear());
        rail.setShape(Rail.Shape.EAST_WEST);
        block.setBlockData(rail);
        assertTrue(clear());
        world.getBlockAt(9, 65, 8).setType(Material.STONE);
        assertFalse(clear());
    }

    @Test
    void unpoweredPoweredRailIsNeverForcedThrough() {
        var block = world.getBlockAt(9, 64, 8);
        block.setType(Material.POWERED_RAIL);
        var rail = (org.bukkit.block.data.type.RedstoneRail) block.getBlockData();
        rail.setShape(Rail.Shape.EAST_WEST);
        rail.setPowered(false);
        block.setBlockData(rail);
        assertFalse(clear());
        rail.setPowered(true);
        block.setBlockData(rail);
        assertTrue(clear());
    }

    @Test
    void lavaWaterAndFireHeadroomAreRejected() {
        for (var material : List.of(Material.LAVA, Material.WATER, Material.FIRE, Material.POWDER_SNOW)) {
            world.getBlockAt(9, 65, 8).setType(material);
            assertFalse(clear(), material.toString());
        }
    }

    @Test
    void unloadedLookaheadDoesNotForceLoadChunks() {
        var unloaded = mock(World.class);
        assertFalse(TrackSafety.clear(unloaded, start, RailShape.EAST_WEST, east, 12));
        verify(unloaded, never()).getChunkAt(anyInt(), anyInt());
        verify(unloaded, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void arrivalRequiresRailStopperSupportAndClearHeadroom() {
        var cart = mock(Minecart.class);
        when(cart.getWorld()).thenReturn(world);
        when(cart.getPassengers()).thenReturn(List.of());
        var end = new RouteTrace.End(new TrackPos(world.getUID(), 14, 64, 8),
                new RailShape.Port(1, 0, 0), new TrackPos(world.getUID(), 15, 64, 8));
        world.getBlockAt(15, 64, 8).setType(Material.LODESTONE);
        assertTrue(TrackSafety.arrivalClear(cart, end));
        world.getBlockAt(14, 65, 8).setType(Material.STONE);
        assertFalse(TrackSafety.arrivalClear(cart, end));
        world.getBlockAt(14, 65, 8).setType(Material.AIR);
        world.getBlockAt(15, 64, 8).setType(Material.STONE);
        assertFalse(TrackSafety.arrivalClear(cart, end));
    }

    @Test
    void directionOnlyUsesTheStraightRailAxis() {
        assertEquals(new Vector(-1, 0, 0), TrackSafety.direction(RailShape.EAST_WEST, new Vector(-4, 0, 0.1)));
        assertEquals(new Vector(0, 0, 1), TrackSafety.direction(RailShape.NORTH_SOUTH, new Vector(0.1, 0, 4)));
        assertNull(TrackSafety.direction(RailShape.NORTH_EAST, east));
        assertNull(TrackSafety.direction(RailShape.EAST_WEST, new Vector()));
    }

    @Test
    void railUnderCartAcceptsTheRailBelowItsFloorCoordinate() {
        assertEquals(start, TrackSafety.railUnder(new Location(world, 1.5, 65.1, 8.5)));
        assertNull(TrackSafety.railUnder(new Location(world, 1.5, 67, 8.5)));
    }

    @Test
    void arrivalOutsideTheWorldBorderOrBesideAnotherEntityIsBlocked() {
        var cart = mock(Minecart.class);
        when(cart.getWorld()).thenReturn(world);
        when(cart.getPassengers()).thenReturn(List.of());
        var end = new RouteTrace.End(new TrackPos(world.getUID(), 14, 64, 8),
                new RailShape.Port(1, 0, 0), new TrackPos(world.getUID(), 15, 64, 8));
        world.getBlockAt(15, 64, 8).setType(Material.LODESTONE);
        when(world.getWorldBorder().isInside(any(Location.class))).thenReturn(false);
        assertFalse(TrackSafety.arrivalClear(cart, end));
        when(world.getWorldBorder().isInside(any(Location.class))).thenReturn(true);
        doReturn(List.of(mock(org.bukkit.entity.Entity.class)))
                .when(world).getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());
        assertFalse(TrackSafety.arrivalClear(cart, end));
    }
}
