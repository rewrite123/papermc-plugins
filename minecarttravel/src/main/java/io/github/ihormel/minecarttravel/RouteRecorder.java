package io.github.ihormel.minecarttravel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Records a route while its rider travels it. A cursor walks the real rail
 * blocks from the starting lodestone toward the cart and never passes the
 * cart, so every recorded rail is one the cart actually traveled over.
 */
final class RouteRecorder {
    interface Rails {
        boolean loaded(TrackPos pos);
        RailShape rail(TrackPos pos);
        boolean lodestone(TrackPos pos);
    }

    enum State { RECORDING, COMPLETE, FAILED }

    private record Connection(TrackPos pos, RailShape.Port incoming) {}
    private record Step(TrackPos pos, RailShape shape, RailShape.Port incoming) {}

    static final int LOST_LIMIT = 40;
    private final Rails rails;
    private final int maxRails;
    private final int maxTurns;
    private final Set<TrackPos> visited = new HashSet<>();
    private final List<RouteRegistry.Segment> segments = new ArrayList<>();
    private RouteRegistry.End first;
    private TrackPos cursor;
    private RailShape cursorShape;
    private RailShape.Port exit;
    private Lane lane;
    private int from;
    private int to;
    private RailShape.Port toward;
    private int count;
    private int turns;
    private int lost;
    private State state = State.RECORDING;
    private String failure;
    private RouteRegistry.Route route;

    private RouteRecorder(Rails rails, int maxRails, int maxTurns) {
        this.rails = rails;
        this.maxRails = maxRails;
        this.maxTurns = maxTurns;
    }

    /** Starts recording from the lodestone end nearest the cart, searching at most {@code search} rails each way. */
    static RouteRecorder begin(Rails rails, TrackPos start, int maxRails, int maxTurns, int search) {
        var recorder = new RouteRecorder(rails, maxRails, maxTurns);
        var shape = rails.loaded(start) ? rails.rail(start) : null;
        if (shape == null) return recorder.fail("Your minecart must be on a rail.");
        List<Step> best = null;
        for (var port : shape.ports) {
            var path = recorder.endPath(start, shape, port, search);
            if (path != null && (best == null || path.size() < best.size())) best = path;
        }
        if (best == null) {
            return recorder.fail("This minecart is not within " + search
                    + " rails of a line end that runs straight into a lodestone.");
        }
        var end = best.getLast();
        recorder.first = new RouteRegistry.End(end.pos(), end.incoming(),
                end.pos().add(end.incoming().dx(), 0, end.incoming().dz()));
        recorder.cursor = end.pos();
        recorder.cursorShape = end.shape();
        recorder.exit = other(end.shape(), end.incoming());
        recorder.record(end.pos(), end.shape(), end.incoming());
        // Commit the rails between the lodestone end and the cart, nearest the end first.
        for (int index = best.size() - 2; index >= 0 && recorder.state == State.RECORDING; index--) {
            var step = best.get(index);
            recorder.advance(step.pos(), step.shape(), step.incoming());
        }
        return recorder;
    }

    State state() { return state; }
    String failure() { return failure; }
    RouteRegistry.Route route() { return route; }
    RouteRegistry.End first() { return first; }
    int rails() { return count; }
    int turns() { return turns; }

    /**
     * Advances the cursor up to the cart, walking at most {@code budget} rails.
     * Returns the number of rails walked.
     */
    int follow(TrackPos cart, int budget) {
        if (state != State.RECORDING) return 0;
        if (cart == null) {
            if (++lost > LOST_LIMIT) fail("Registration stopped: your minecart left the rails.");
            return 0;
        }
        if (cart.equals(cursor)) {
            lost = 0;
            finishIfEnd();
            return 0;
        }
        var path = new ArrayList<Step>();
        var position = cursor;
        var port = exit;
        for (int walked = 0; walked < budget; walked++) {
            var next = connections(position, port);
            if (next == null || next.size() != 1) break;
            var connection = next.getFirst();
            var shape = rails.rail(connection.pos());
            path.add(new Step(connection.pos(), shape, connection.incoming()));
            if (connection.pos().equals(cart)) {
                lost = 0;
                for (var step : path) {
                    advance(step.pos(), step.shape(), step.incoming());
                    if (state != State.RECORDING) return path.size();
                }
                finishIfEnd();
                return path.size();
            }
            position = connection.pos();
            port = other(shape, connection.incoming());
        }
        if (++lost > LOST_LIMIT) {
            fail("Registration stopped: your minecart left the line, went backward, or took an ambiguous junction.");
        }
        return path.size();
    }

    /** Path from the cart's rail to a lodestone end in one direction, or null. The last step is the end rail. */
    private List<Step> endPath(TrackPos start, RailShape shape, RailShape.Port port, int search) {
        var path = new ArrayList<Step>();
        var position = start;
        var current = shape;
        var out = port;
        var seen = new HashSet<TrackPos>();
        for (int walked = 0; walked <= search; walked++) {
            if (!seen.add(position)) return null;
            var next = connections(position, out);
            // Store each step's port toward the lodestone end so begin() can reverse the walk.
            path.add(new Step(position, current, out));
            if (next == null || next.size() > 1) return null;
            if (next.isEmpty()) {
                var stop = position.add(out.dx(), out.height(), out.dz());
                return current.flatStraight() && rails.loaded(stop) && rails.lodestone(stop) ? path : null;
            }
            var connection = next.getFirst();
            position = connection.pos();
            current = rails.rail(position);
            out = other(current, connection.incoming());
        }
        return null;
    }

    private void advance(TrackPos pos, RailShape shape, RailShape.Port incoming) {
        if (count >= maxRails) {
            fail("This line is longer than the " + maxRails + "-rail limit.");
            return;
        }
        boolean bend = !shape.straight() || (shape != cursorShape && cursorShape.straight());
        if (bend && ++turns > maxTurns) {
            fail("This line has more than " + maxTurns + " turns or slope changes.");
            return;
        }
        if (!record(pos, shape, incoming)) return;
        cursor = pos;
        cursorShape = shape;
        exit = other(shape, incoming);
    }

    private boolean record(TrackPos pos, RailShape shape, RailShape.Port incoming) {
        if (!visited.add(pos)) {
            fail("This line loops back on itself.");
            return false;
        }
        count++;
        var cellLane = Lane.at(pos, shape);
        int t = cellLane.coordinate(pos);
        if (cellLane.equals(lane) && shape.straight() && incoming.equals(toward) && (t == to + 1 || t == from - 1)) {
            from = Math.min(from, t);
            to = Math.max(to, t);
            return true;
        }
        closeSegment();
        lane = cellLane;
        from = t;
        to = t;
        toward = incoming;
        return true;
    }

    private void closeSegment() {
        if (lane != null) segments.add(new RouteRegistry.Segment(lane, from, to, toward));
    }

    private void finishIfEnd() {
        var next = connections(cursor, exit);
        if (next == null || !next.isEmpty()) return;
        var stop = cursor.add(exit.dx(), exit.height(), exit.dz());
        if (!cursorShape.flatStraight() || !rails.loaded(stop) || !rails.lodestone(stop)) {
            fail("This line ends without running straight into a lodestone.");
            return;
        }
        if (stop.equals(first.lodestone())) {
            fail("A route needs two different lodestones.");
            return;
        }
        closeSegment();
        lane = null;
        route = new RouteRegistry.Route(UUID.randomUUID(), first, new RouteRegistry.End(cursor, exit, stop),
                segments, count, turns);
        state = State.COMPLETE;
    }

    /** Connected rails beyond {@code port}; null when that area is not loaded. */
    private List<Connection> connections(TrackPos pos, RailShape.Port port) {
        var beside = pos.add(port.dx(), 0, port.dz());
        if (!rails.loaded(beside)) return null;
        var result = new ArrayList<Connection>(2);
        for (int otherHeight = 0; otherHeight <= 1; otherHeight++) {
            var other = pos.add(port.dx(), port.height() - otherHeight, port.dz());
            var shape = rails.rail(other);
            if (shape == null) continue;
            for (var incoming : shape.ports) {
                if (incoming.dx() == -port.dx() && incoming.dz() == -port.dz()
                        && incoming.height() == otherHeight) result.add(new Connection(other, incoming));
            }
        }
        return result;
    }

    private static RailShape.Port other(RailShape shape, RailShape.Port port) {
        return shape.ports.get(0).equals(port) ? shape.ports.get(1) : shape.ports.get(0);
    }

    private RouteRecorder fail(String reason) {
        state = State.FAILED;
        failure = reason;
        return this;
    }
}
