package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouteRegistryTest {
    static final UUID WORLD = UUID.randomUUID();
    private static final RailShape.Port WEST = new RailShape.Port(-1, 0, 0);
    private static final RailShape.Port EAST = new RailShape.Port(1, 0, 0);

    static TrackPos pos(int x, int z) { return new TrackPos(WORLD, x, 64, z); }

    /** A straight east-west route on row {@code z} from x = {@code start} to {@code end}. */
    static RouteRegistry.Route line(int z, int start, int end) {
        return new RouteRegistry.Route(UUID.randomUUID(),
                new RouteRegistry.End(pos(start, z), WEST, pos(start - 1, z)),
                new RouteRegistry.End(pos(end, z), EAST, pos(end + 1, z)),
                List.of(new RouteRegistry.Segment(Lane.at(pos(start, z), RailShape.EAST_WEST), start, end, WEST)),
                end - start + 1, 0);
    }

    @Test
    void lookupFindsRoutesOnlyOnRegisteredCellsWithMatchingShape() {
        var registry = new RouteRegistry();
        var route = line(0, 0, 40);
        registry.add(route);
        assertSame(route, registry.at(pos(20, 0)).route());
        assertNull(registry.at(pos(41, 0)));
        assertNull(registry.at(pos(20, 1)));
        assertNull(registry.at(pos(20, 0), RailShape.NORTH_SOUTH));
        assertNotNull(registry.at(pos(20, 0), RailShape.EAST_WEST));
        assertEquals(List.of(route), registry.lodestoneRoutes(pos(41, 0)));
        assertTrue(registry.watched(pos(-1, 0)));
        assertTrue(registry.touches(pos(40, 0).chunk()));
        assertFalse(registry.touches(pos(100, 0).chunk()));
    }

    @Test
    void reRegisteringOverlappingTrackReplacesTheOldRoute() {
        var registry = new RouteRegistry();
        var old = line(0, 0, 40);
        var other = line(5, 0, 40);
        registry.add(old);
        registry.add(other);
        long revision = registry.revision();
        var replacement = line(0, 10, 60);
        assertEquals(List.of(old), registry.add(replacement));
        assertFalse(registry.contains(old));
        assertTrue(registry.contains(other));
        assertSame(replacement, registry.at(pos(15, 0)).route());
        assertNull(registry.at(pos(5, 0)));
        assertTrue(registry.revision() > revision);
        registry.remove(replacement);
        assertNull(registry.at(pos(15, 0)));
        assertTrue(registry.lodestoneRoutes(pos(61, 0)).isEmpty());
        assertEquals(1, registry.routes().size());
    }

    @Test
    void chunkChecksCoverOnlyCellsAndLodestonesInsideThatChunk() {
        var registry = new RouteRegistry();
        registry.add(line(0, 0, 40));
        var first = registry.checksIn(pos(0, 0).chunk());
        assertEquals(16, first.stream().filter(check -> check.shape() != null).count());
        assertEquals(0, first.stream().filter(check -> check.shape() == null).count());
        var west = registry.checksIn(pos(-1, 0).chunk());
        assertEquals(List.of(new RouteRegistry.Check(pos(-1, 0), null)), west);
        var last = registry.checksIn(pos(40, 0).chunk());
        assertEquals(9 + 1, last.size());
    }

    @Test
    void segmentsRejectInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new RouteRegistry.Segment(
                Lane.at(pos(0, 0), RailShape.EAST_WEST), 5, 4, WEST));
    }

    @Test
    void storeRoundTripsRoutesAndRejectsUnknownFiles(@TempDir Path directory) throws Exception {
        var registry = new RouteRegistry();
        var a = line(0, 0, 40);
        var curve = new RouteRegistry.Route(UUID.randomUUID(),
                new RouteRegistry.End(pos(0, 10), WEST, pos(-1, 10)),
                new RouteRegistry.End(new TrackPos(WORLD, 3, 64, 14), new RailShape.Port(0, 1, 0), new TrackPos(WORLD, 3, 64, 15)),
                List.of(new RouteRegistry.Segment(Lane.at(pos(0, 10), RailShape.EAST_WEST), 0, 2, WEST),
                        new RouteRegistry.Segment(Lane.at(pos(3, 10), RailShape.SOUTH_WEST), 3, 3, WEST),
                        new RouteRegistry.Segment(Lane.at(pos(3, 11), RailShape.NORTH_SOUTH), 11, 14,
                                new RailShape.Port(0, -1, 0))),
                8, 1);
        registry.add(a);
        registry.add(curve);
        var file = directory.resolve("routes.txt");
        var store = new RouteStore(file);
        store.save(registry);
        var loaded = store.load();
        assertEquals(List.copyOf(registry.routes()), List.copyOf(loaded.routes()));
        assertEquals(curve.id(), loaded.at(pos(3, 12)).route().id());
        assertTrue(new RouteStore(directory.resolve("missing.txt")).load().routes().isEmpty());
        Files.writeString(file, "minecarttravel-graph-1\n");
        assertThrows(java.io.IOException.class, store::load);
        Files.writeString(file, RouteStore.HEADER + "\nR nonsense\n");
        assertThrows(java.io.IOException.class, store::load);
    }
}
