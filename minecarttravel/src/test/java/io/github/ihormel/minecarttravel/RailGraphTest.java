package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RailGraphTest {
    private final UUID world = UUID.randomUUID();
    private final RailGraph graph = new RailGraph();

    TrackPos pos(int x) { return new TrackPos(world, x, 64, 0); }

    @Test
    void longStraightTrackUsesOneRunAndOnlyMovesItsTail() {
        for (int x = 0; x < 65536; x++) graph.observe(pos(x), RailShape.EAST_WEST, false);
        assertEquals(1, graph.runs().size());
        var run = graph.runs().getFirst();
        assertEquals(0, run.from());
        assertEquals(65535, run.to());
        assertEquals(RailShape.EAST_WEST, graph.rail(pos(12345)));
        assertNull(graph.rail(pos(65536)));
    }

    @Test
    void breakingTheMiddleSplitsAndReplacingMergesTheGraph() {
        for (int x = -100; x <= 100; x++) graph.observe(pos(x), RailShape.EAST_WEST, false);
        graph.observe(pos(0), null, false);
        assertEquals(2, graph.runs().size());
        assertNull(graph.rail(pos(0)));
        graph.observe(pos(0), RailShape.EAST_WEST, false);
        assertEquals(1, graph.runs().size());
    }

    @Test
    void twoIndependentTailsMergeWhenTheGapIsFilled() {
        for (int x : new int[] {0, 1, 2, 4, 5, 6}) graph.observe(pos(x), RailShape.EAST_WEST, false);
        assertEquals(2, graph.runs().size());
        graph.observe(pos(3), RailShape.EAST_WEST, false);
        assertEquals(1, graph.runs().size());
    }

    @Test
    void slopeRunsCompressInThreeDimensions() {
        for (int x = 0; x < 100; x++) graph.observe(new TrackPos(world, x, 64 + x, 0), RailShape.ASCENDING_EAST, false);
        assertEquals(1, graph.runs().size());
        assertEquals(RailShape.ASCENDING_EAST, graph.rail(new TrackPos(world, 50, 114, 0)));
        assertNull(graph.rail(new TrackPos(world, 50, 113, 0)));
    }

    @Test
    void shapeEditsReplaceRatherThanOverlayTheOldShape() {
        graph.observe(pos(0), RailShape.EAST_WEST, false);
        graph.observe(pos(0), RailShape.NORTH_SOUTH, false);
        assertEquals(1, graph.runs().size());
        assertEquals(RailShape.NORTH_SOUTH, graph.rail(pos(0)));
        graph.observe(pos(0), null, true);
        assertNull(graph.rail(pos(0)));
        assertTrue(graph.stopper(pos(0)));
    }

    @Test
    void worldsAndParallelLinesStayIndependent() {
        graph.observe(pos(0), RailShape.EAST_WEST, false);
        graph.observe(new TrackPos(world, 0, 64, 1), RailShape.EAST_WEST, false);
        graph.observe(new TrackPos(UUID.randomUUID(), 0, 64, 0), RailShape.EAST_WEST, false);
        assertEquals(3, graph.runs().size());
    }

    @Test
    void clearingAChunkSplitsALongRunWithoutTouchingOtherChunks() {
        for (int x = -32; x < 64; x++) graph.observe(pos(x), RailShape.EAST_WEST, false);
        graph.clearChunk(new TrackPos.ChunkKey(world, 0, 0));
        assertEquals(2, graph.runs().size());
        assertNull(graph.rail(pos(0)));
        assertNull(graph.rail(pos(15)));
        assertEquals(RailShape.EAST_WEST, graph.rail(pos(-1)));
        assertEquals(RailShape.EAST_WEST, graph.rail(pos(16)));
    }

    @Test
    void unchangedObservationsDoNotInvalidateWork() {
        graph.observe(pos(0), RailShape.EAST_WEST, false);
        long revision = graph.revision();
        graph.observe(pos(0), RailShape.EAST_WEST, false);
        graph.observe(pos(99), null, false);
        assertEquals(revision, graph.revision());
    }

    @Test
    void adjacentCurvedRailsRemainIndividualNodes() {
        graph.observe(pos(0), RailShape.NORTH_EAST, false);
        graph.observe(pos(1), RailShape.NORTH_EAST, false);
        assertEquals(2, graph.runs().size());
    }

    @Test
    void malformedAndOverlappingIntervalsAreRejected() {
        var lane = RailGraph.Lane.at(pos(0), RailShape.EAST_WEST);
        assertThrows(IllegalArgumentException.class, () -> graph.restore(new RailGraph.Run(lane, 3, 1)));
        graph.restore(new RailGraph.Run(lane, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> graph.restore(new RailGraph.Run(lane, 5, 20)));
    }
}
