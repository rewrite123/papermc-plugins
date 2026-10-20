package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteRecorderTest {
    private final UUID world = UUID.randomUUID();
    private final Map<TrackPos, RailShape> rails = new HashMap<>();
    private final Set<TrackPos> lodestones = new HashSet<>();
    private final Set<Integer> unloadedChunkX = new HashSet<>();
    private final List<TrackPos> path = new ArrayList<>();
    private final RouteRecorder.Rails reader = new RouteRecorder.Rails() {
        @Override public boolean loaded(TrackPos pos) { return !unloadedChunkX.contains(pos.x() >> 4); }
        @Override public RailShape rail(TrackPos pos) { return rails.get(pos); }
        @Override public boolean lodestone(TrackPos pos) { return lodestones.contains(pos); }
    };

    private TrackPos pos(int x, int y, int z) { return new TrackPos(world, x, y, z); }
    private TrackPos pos(int x) { return pos(x, 64, 0); }

    private void rail(TrackPos pos, RailShape shape) {
        rails.put(pos, shape);
        path.add(pos);
    }

    private void straight(int length) {
        lodestones.add(pos(-1));
        for (int x = 0; x < length; x++) rail(pos(x), RailShape.EAST_WEST);
        lodestones.add(pos(length));
    }

    /** East from the first lodestone, a curve, then south into the second lodestone. */
    private void lShape() {
        lodestones.add(pos(-1));
        for (int x = 0; x <= 10; x++) rail(pos(x), RailShape.EAST_WEST);
        rail(pos(11), RailShape.SOUTH_WEST);
        for (int z = 1; z <= 10; z++) rail(pos(11, 64, z), RailShape.NORTH_SOUTH);
        lodestones.add(pos(11, 64, 11));
    }

    private RouteRecorder ride(int startIndex, int stride, int maxRails, int maxTurns) {
        var recorder = RouteRecorder.begin(reader, path.get(startIndex), maxRails, maxTurns, 16);
        int last = path.size() - 1;
        for (int index = startIndex; recorder.state() == RouteRecorder.State.RECORDING; index = Math.min(index + stride, last)) {
            recorder.follow(path.get(index), 64);
            if (index == last) break;
        }
        return recorder;
    }

    @Test
    void ridingAStraightLineRegistersOneCompressedSegment() {
        straight(100);
        var recorder = ride(3, 1, 65536, 32);
        assertEquals(RouteRecorder.State.COMPLETE, recorder.state(), recorder.failure());
        var route = recorder.route();
        assertEquals(100, route.rails());
        assertEquals(0, route.turns());
        assertEquals(1, route.segments().size());
        assertEquals(pos(-1), route.first().lodestone());
        assertEquals(pos(100), route.second().lodestone());
        assertEquals(pos(99), route.second().rail());
        assertEquals(new RailShape.Port(-1, 0, 0), route.segments().getFirst().towardFirst());
    }

    @Test
    void boostedCartsSkippingSeveralRailsPerTickAreStillFullyRecorded() {
        straight(200);
        var recorder = ride(0, 5, 65536, 32);
        assertEquals(RouteRecorder.State.COMPLETE, recorder.state(), recorder.failure());
        assertEquals(200, recorder.route().rails());
    }

    @Test
    void curvesBecomeTheirOwnSegmentAndCountAsTurns() {
        lShape();
        var recorder = ride(0, 2, 65536, 32);
        assertEquals(RouteRecorder.State.COMPLETE, recorder.state(), recorder.failure());
        var route = recorder.route();
        assertEquals(22, route.rails());
        assertEquals(1, route.turns());
        assertEquals(3, route.segments().size());
        assertEquals(new RailShape.Port(0, -1, 0), route.segments().getLast().towardFirst());
        assertEquals(pos(11, 64, 11), route.second().lodestone());
    }

    @Test
    void routesWithTooManyTurnsAreRejected() {
        lShape();
        var recorder = ride(0, 1, 65536, 0);
        assertEquals(RouteRecorder.State.FAILED, recorder.state());
        assertTrue(recorder.failure().contains("turns"));
    }

    @Test
    void routesLongerThanTheRailLimitAreRejected() {
        straight(50);
        var recorder = ride(0, 1, 20, 32);
        assertEquals(RouteRecorder.State.FAILED, recorder.state());
        assertTrue(recorder.failure().contains("20-rail"));
    }

    @Test
    void registrationMustStartNearALodestoneEnd() {
        straight(100);
        var recorder = RouteRecorder.begin(reader, pos(50), 65536, 32, 16);
        assertEquals(RouteRecorder.State.FAILED, recorder.state());
        assertTrue(recorder.failure().contains("16 rails"));
        assertEquals(RouteRecorder.State.FAILED,
                RouteRecorder.begin(reader, pos(500), 65536, 32, 16).state());
    }

    @Test
    void startingNearerTheFarEndRecordsFromThatLodestone() {
        straight(20);
        var recorder = RouteRecorder.begin(reader, pos(17), 65536, 32, 16);
        assertEquals(pos(20), recorder.first().lodestone());
        for (int x = 17; x >= 0 && recorder.state() == RouteRecorder.State.RECORDING; x--) recorder.follow(pos(x), 64);
        assertEquals(RouteRecorder.State.COMPLETE, recorder.state(), recorder.failure());
        assertEquals(pos(-1), recorder.route().second().lodestone());
        assertEquals(new RailShape.Port(1, 0, 0), recorder.route().segments().getFirst().towardFirst());
    }

    @Test
    void lineEndingWithoutALodestoneFails() {
        straight(30);
        lodestones.remove(pos(30));
        var recorder = ride(0, 1, 65536, 32);
        assertEquals(RouteRecorder.State.FAILED, recorder.state());
        assertTrue(recorder.failure().contains("without running straight into a lodestone"));
    }

    @Test
    void goingBackwardOrLeavingTheLineEventuallyFails() {
        straight(40);
        var recorder = RouteRecorder.begin(reader, pos(5), 65536, 32, 16);
        recorder.follow(pos(10), 64);
        for (int tick = 0; tick <= RouteRecorder.LOST_LIMIT; tick++) recorder.follow(pos(8), 64);
        assertEquals(RouteRecorder.State.FAILED, recorder.state());
        var offRail = RouteRecorder.begin(reader, pos(5), 65536, 32, 16);
        for (int tick = 0; tick <= RouteRecorder.LOST_LIMIT; tick++) offRail.follow(null, 64);
        assertTrue(offRail.failure().contains("left the rails"));
    }

    @Test
    void stoppingInPlaceDoesNotCountAsLost() {
        straight(40);
        var recorder = RouteRecorder.begin(reader, pos(5), 65536, 32, 16);
        recorder.follow(pos(10), 64);
        for (int tick = 0; tick < 500; tick++) recorder.follow(pos(10), 64);
        assertEquals(RouteRecorder.State.RECORDING, recorder.state());
        assertEquals(11, recorder.rails());
    }

    @Test
    void sideBranchesDoNotStopRegistrationBecauseTheCartsActualPathIsRecorded() {
        straight(40);
        rails.put(pos(20, 64, 1), RailShape.NORTH_SOUTH);
        rails.put(pos(20, 64, 2), RailShape.NORTH_SOUTH);
        var recorder = ride(0, 1, 65536, 32);
        assertEquals(RouteRecorder.State.COMPLETE, recorder.state(), recorder.failure());
        assertEquals(40, recorder.route().rails());
    }

    @Test
    void slopesAreFollowedAndCountAsTurns() {
        lodestones.add(pos(-1));
        for (int x = 0; x < 5; x++) rail(pos(x), RailShape.EAST_WEST);
        rail(pos(5), RailShape.ASCENDING_EAST);
        for (int x = 6; x < 10; x++) rail(pos(x, 65, 0), RailShape.EAST_WEST);
        lodestones.add(pos(10, 65, 0));
        var recorder = ride(0, 1, 65536, 32);
        assertEquals(RouteRecorder.State.COMPLETE, recorder.state(), recorder.failure());
        assertEquals(10, recorder.route().rails());
        assertEquals(2, recorder.route().turns());
    }

    @Test
    void unloadedTrackAheadWaitsInsteadOfGuessing() {
        straight(40);
        unloadedChunkX.add(1);
        var recorder = RouteRecorder.begin(reader, pos(2), 65536, 32, 16);
        recorder.follow(pos(15), 64);
        assertEquals(16, recorder.rails());
        recorder.follow(pos(20), 64);
        assertEquals(16, recorder.rails());
        unloadedChunkX.clear();
        recorder.follow(pos(20), 64);
        assertEquals(21, recorder.rails());
    }
}
