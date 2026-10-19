package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteTraceTest {
    private final UUID world = UUID.randomUUID();
    private final RailGraph graph = new RailGraph();
    private TrackPos pos(int x, int y, int z) { return new TrackPos(world, x, y, z); }
    private TrackPos pos(int x) { return pos(x, 64, 0); }

    private void line(int length) {
        for (int x = 0; x < length; x++) graph.observe(pos(x), RailShape.EAST_WEST, false);
        graph.observe(pos(-1), null, true);
        graph.observe(pos(length), null, true);
    }

    private RouteTrace trace(TrackPos start, int maxRails, int maxTurns) {
        var trace = new RouteTrace(graph, start, maxRails, maxTurns);
        for (int step = 0; step <= maxRails + 4 && !trace.done(); step++) trace.step();
        assertTrue(trace.done(), "Route walk must have bounded completion");
        return trace;
    }

    @Test
    void completeStraightLineHasTwoEndsAndOneDirectionRecord() {
        line(100);
        var trace = trace(pos(50), 100, 0);
        assertNull(trace.failure());
        assertEquals(100, trace.route().rails());
        assertEquals(0, trace.route().turns());
        assertEquals(pos(0), trace.route().first().rail());
        assertEquals(pos(99), trace.route().second().rail());
        assertEquals(1, trace.route().towardFirst().size());
        assertEquals(-1, trace.route().towardFirst().get(graph.run(pos(20))).dx());
    }

    @Test
    void railFirstOrLodestoneFirstBothBecomeComplete() {
        graph.observe(pos(-1), null, true);
        for (int x = 0; x < 10; x++) graph.observe(pos(x), RailShape.EAST_WEST, false);
        assertNull(trace(pos(3), 100, 32).route());
        graph.observe(pos(10), null, true);
        assertNotNull(trace(pos(3), 100, 32).route());
    }

    @Test
    void breakingAnyRequiredRailDisablesFastTravelUntilRebuilt() {
        line(50);
        assertNotNull(trace(pos(10), 100, 32).route());
        graph.observe(pos(25), null, false);
        assertNull(trace(pos(10), 100, 32).route());
        graph.observe(pos(25), RailShape.EAST_WEST, false);
        assertNotNull(trace(pos(10), 100, 32).route());
        graph.observe(pos(50), null, false);
        assertNull(trace(pos(10), 100, 32).route());
    }

    @Test
    void joiningTwoUnfinishedGraphsEnablesTravel() {
        line(50);
        graph.observe(pos(25), null, false);
        assertNull(trace(pos(10), 100, 32).route());
        graph.observe(pos(25), RailShape.EAST_WEST, false);
        assertNotNull(trace(pos(40), 100, 32).route());
    }

    @Test
    void aThirdRailAimedIntoTheSideMakesAJunction() {
        line(20);
        graph.observe(pos(10, 64, 1), RailShape.NORTH_SOUTH, false);
        var trace = trace(pos(3), 100, 32);
        assertNull(trace.route());
        assertTrue(trace.failure().contains("Junction"));
    }

    @Test
    void adjacentParallelRailsDoNotCreateAJunction() {
        line(20);
        for (int x = 0; x < 20; x++) graph.observe(pos(x, 64, 1), RailShape.EAST_WEST, false);
        assertNotNull(trace(pos(3), 100, 32).route());
    }

    @Test
    void tooLongLineIsRejectedAtTheConfiguredBoundary() {
        line(20);
        assertNotNull(trace(pos(10), 20, 32).route());
        assertNull(trace(pos(10), 19, 32).route());
    }

    @Test
    void dirtyChunksAndGraphEditsInvalidateChecks() {
        line(20);
        graph.dirty.add(pos(19).chunk());
        assertNull(trace(pos(2), 100, 32).route());
        graph.dirty.clear();
        var trace = new RouteTrace(graph, pos(2), 100, 32);
        trace.step();
        graph.observe(pos(10), null, false);
        trace.step();
        assertTrue(trace.done());
        assertNull(trace.route());
        assertTrue(trace.failure().contains("changed"));
    }

    private void curvedLine() {
        graph.observe(pos(-1), null, true);
        for (int x = 0; x < 4; x++) graph.observe(pos(x), RailShape.EAST_WEST, false);
        graph.observe(pos(4), RailShape.SOUTH_WEST, false);
        for (int z = 1; z < 5; z++) graph.observe(pos(4, 64, z), RailShape.NORTH_SOUTH, false);
        graph.observe(pos(4, 64, 5), null, true);
    }

    @Test
    void curveUsesReciprocalConnectionsAndCountsAsOneTurn() {
        curvedLine();
        var trace = trace(pos(2), 100, 1);
        assertNotNull(trace.route());
        assertEquals(1, trace.route().turns());
        assertEquals(9, trace.route().rails());
        assertNull(trace(pos(4), 100, 0).route());
        assertNull(trace(pos(2), 100, 0).route());
    }

    @Test
    void startingOnACurveStillCountsItExactlyOnce() {
        curvedLine();
        var trace = trace(pos(4), 100, 1);
        assertNotNull(trace.route());
        assertEquals(1, trace.route().turns());
        assertEquals(9, trace.route().rails());
    }

    @Test
    void slopeConnectsToRailsAtTheCorrectHeight() {
        graph.observe(pos(-1), null, true);
        graph.observe(pos(0), RailShape.EAST_WEST, false);
        graph.observe(pos(1), RailShape.ASCENDING_EAST, false);
        graph.observe(pos(2, 65, 0), RailShape.EAST_WEST, false);
        graph.observe(pos(3, 65, 0), null, true);
        var trace = trace(pos(0), 10, 2);
        assertNotNull(trace.route());
        assertEquals(3, trace.route().rails());
        assertEquals(2, trace.route().turns());
        graph.observe(pos(2, 65, 0), null, false);
        graph.observe(pos(2), RailShape.EAST_WEST, false);
        assertNull(trace(pos(0), 10, 32).route());
    }

    @Test
    void closedLoopsFinishWithinTheRailBudgetWithoutPerRailStorage() {
        graph.observe(pos(0), RailShape.SOUTH_EAST, false);
        graph.observe(pos(1), RailShape.SOUTH_WEST, false);
        graph.observe(pos(1, 64, 1), RailShape.NORTH_WEST, false);
        graph.observe(pos(0, 64, 1), RailShape.NORTH_EAST, false);
        assertNull(trace(pos(0), 20, 100).route());
    }

    @Test
    void missingStartRailIsAnExplicitFailure() {
        var trace = trace(pos(0), 100, 32);
        assertNotNull(trace.failure());
        assertNull(trace.route());
    }

    @Test
    void stopperAtTheWrongHeightDoesNotTerminateTheRail() {
        line(10);
        graph.observe(pos(10), null, false);
        graph.observe(pos(10, 65, 0), null, true);
        assertNull(trace(pos(2), 100, 32).route());
    }

    @Test
    void aRouteCanUseCleanSavedChunksWithoutAnyWorldObject() {
        line(1000);
        assertNotNull(trace(pos(500), 1000, 0).route());
    }

    @Test
    void thirtyTwoBendsAreAcceptedButThirtyThreeAreNot() {
        var points = new java.util.ArrayList<TrackPos>();
        TrackPos cursor = pos(0);
        points.add(cursor);
        for (int segment = 0; segment < 34; segment++) {
            int dx = segment % 2 == 0 ? 1 : 0;
            int dz = segment % 2 == 0 ? 0 : 1;
            for (int step = 0; step < 4; step++) {
                cursor = cursor.add(dx, 0, dz);
                points.add(cursor);
            }
        }
        for (int index = 0; index < points.size(); index++) {
            var here = points.get(index);
            var before = index == 0 ? here.add(-1, 0, 0) : points.get(index - 1);
            var after = index == points.size() - 1 ? here.add(0, 0, 1) : points.get(index + 1);
            var ports = java.util.Set.of(new RailShape.Port(before.x() - here.x(), before.z() - here.z(), 0),
                    new RailShape.Port(after.x() - here.x(), after.z() - here.z(), 0));
            var shape = java.util.Arrays.stream(RailShape.values())
                    .filter(candidate -> java.util.Set.copyOf(candidate.ports).equals(ports)).findFirst().orElseThrow();
            graph.observe(here, shape, false);
        }
        graph.observe(pos(-1), null, true);
        graph.observe(cursor.add(0, 0, 1), null, true);
        assertNotNull(trace(pos(0), 1000, 33).route());
        assertNull(trace(pos(0), 1000, 32).route());
        // Remove the final bend, extending the preceding eastbound segment to a stopper.
        var finalBend = points.get(132);
        for (int index = 132; index < points.size(); index++) graph.observe(points.get(index), null, false);
        graph.observe(finalBend, null, true);
        assertNotNull(trace(pos(0), 1000, 32).route());
    }

    @Test
    void maximumLengthStraightRouteStaysCompressedAndSupportsResumableWork() {
        line(65536);
        var trace = new RouteTrace(graph, pos(32768), 65536, 32);
        for (int step = 0; step < 512; step++) trace.step();
        assertFalse(trace.done(), "A work budget must not secretly scan the rest of a long line");
        for (int step = 0; step < 65540 && !trace.done(); step++) trace.step();
        assertNotNull(trace.route());
        assertEquals(65536, trace.route().rails());
        assertEquals(1, trace.route().towardFirst().size());
        assertEquals(1, graph.runs().size());
    }
}
