package io.github.ihormel.minecarttravel;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Resumable graph-only walk. Each step has a fixed, small number of lookups. */
final class RouteTrace {
    record End(TrackPos rail, RailShape.Port outward, TrackPos lodestone) {}
    record Route(End first, End second, int rails, int turns, long revision,
                 Map<RailGraph.Run, RailShape.Port> towardFirst) {}
    private final RailGraph graph;
    private final TrackPos start;
    private final int maxRails;
    private final int maxTurns;
    private final long revision;
    private TrackPos position;
    private RailShape.Port exit;
    private End first;
    private int phase;
    private int rails = 1;
    private int turns;
    private boolean done;
    private String failure;
    private Route route;
    private final Map<RailGraph.Run, RailShape.Port> towardFirst = new HashMap<>();

    RouteTrace(RailGraph graph, TrackPos start, int maxRails, int maxTurns) {
        this.graph = graph;
        this.start = start;
        this.position = start;
        this.maxRails = maxRails;
        this.maxTurns = maxTurns;
        revision = graph.revision();
        var shape = graph.rail(start);
        if (shape == null) fail("No tracked rail under the cart.");
        else {
            exit = shape.ports.getFirst();
            if (!shape.straight()) turns = 1;
            if (turns > maxTurns) fail("This route has too many turns or slope transitions.");
        }
    }

    boolean done() { return done; }
    String failure() { return failure; }
    Route route() { return route; }

    void step() {
        if (done) return;
        if (graph.revision() != revision) {
            fail("The rail graph changed; fast travel will retry.");
            return;
        }
        if (graph.dirty.contains(position.chunk())
                || graph.dirty.contains(position.add(1, 0, 0).chunk())
                || graph.dirty.contains(position.add(-1, 0, 0).chunk())
                || graph.dirty.contains(position.add(0, 0, 1).chunk())
                || graph.dirty.contains(position.add(0, 0, -1).chunk())) {
            fail("Part of this route is still being reconciled.");
            return;
        }
        var shape = graph.rail(position);
        var firstDirection = phase == 0 ? exit : shape.ports.get(0).equals(exit)
                ? shape.ports.get(1) : shape.ports.get(0);
        if (phase == 0 || !position.equals(start)) {
            towardFirst.putIfAbsent(graph.run(position), firstDirection);
        }
        if (hasBranch(position, shape)) {
            fail("Junctions are not eligible for fast travel.");
            return;
        }
        var next = connections(position, exit);
        if (next.size() > 1) {
            fail("Ambiguous rail junction.");
            return;
        }
        if (next.isEmpty()) {
            TrackPos stop = position.add(exit.dx(), exit.height(), exit.dz());
            if (!shape.flatStraight() || !graph.stopper(stop) || graph.dirty.contains(stop.chunk())) {
                fail("This line does not terminate directly against two lodestones.");
                return;
            }
            var end = new End(position, exit, stop);
            if (phase == 0) {
                first = end;
                phase = 1;
                position = start;
                exit = graph.rail(start).ports.get(1);
            } else {
                if (first.lodestone().equals(end.lodestone())) {
                    fail("A route requires two different lodestones.");
                } else {
                    route = new Route(first, end, rails, turns, revision, Map.copyOf(towardFirst));
                    done = true;
                }
            }
            return;
        }
        var connection = next.getFirst();
        if (++rails > maxRails) {
            fail("The route is too long or contains a loop.");
            return;
        }
        RailShape nextShape = graph.rail(connection.pos());
        boolean bend = !nextShape.straight()
                || (nextShape != shape && nextShape.straight() && shape.straight());
        if (bend && ++turns > maxTurns) {
            fail("This route has too many turns or slope transitions.");
            return;
        }
        position = connection.pos();
        exit = nextShape.ports.get(0).equals(connection.incoming())
                ? nextShape.ports.get(1) : nextShape.ports.get(0);
    }

    private record Connection(TrackPos pos, RailShape.Port incoming) {}

    private List<Connection> connections(TrackPos pos, RailShape.Port port) {
        var result = new ArrayList<Connection>(2);
        for (int otherHeight = 0; otherHeight <= 1; otherHeight++) {
            var other = pos.add(port.dx(), port.height() - otherHeight, port.dz());
            var shape = graph.rail(other);
            if (shape == null) continue;
            for (var incoming : shape.ports) {
                if (incoming.dx() == -port.dx() && incoming.dz() == -port.dz()
                        && incoming.height() == otherHeight) result.add(new Connection(other, incoming));
            }
        }
        return result;
    }

    private boolean hasBranch(TrackPos pos, RailShape shape) {
        // A rail aimed into the side of this one is a junction even if a switch
        // currently selects a different shape.
        for (var direction : RailShape.NORTH_SOUTH.ports) {
            if (sideConnection(pos, shape, direction.dx(), direction.dz())) return true;
        }
        for (var direction : RailShape.EAST_WEST.ports) {
            if (sideConnection(pos, shape, direction.dx(), direction.dz())) return true;
        }
        return false;
    }

    private boolean sideConnection(TrackPos pos, RailShape shape, int dx, int dz) {
        if (shape.ports.stream().anyMatch(port -> port.dx() == dx && port.dz() == dz)) return false;
        for (int dy = -1; dy <= 1; dy++) {
            var other = graph.rail(pos.add(dx, dy, dz));
            if (other == null) continue;
            for (var port : other.ports) {
                if (port.dx() == -dx && port.dz() == -dz && dy + port.height() == 0) return true;
            }
        }
        return false;
    }

    private void fail(String reason) {
        failure = reason;
        done = true;
    }
}
