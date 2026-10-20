package io.github.ihormel.minecarttravel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Routes registered by riding them. Each route is stored as a few compressed
 * segments (one per straight run or curve cell), never as per-rail records.
 */
final class RouteRegistry {
    record End(TrackPos rail, RailShape.Port outward, TrackPos lodestone) {}

    record Segment(Lane lane, int from, int to, RailShape.Port towardFirst) {
        Segment {
            if (from > to || (!lane.shape().straight() && from != to)) {
                throw new IllegalArgumentException("Invalid route segment");
            }
        }
    }

    record Route(UUID id, End first, End second, List<Segment> segments, int rails, int turns) {
        Route {
            segments = List.copyOf(segments);
            if (segments.isEmpty() || !first.rail().world().equals(second.rail().world())
                    || first.lodestone().equals(second.lodestone())) {
                throw new IllegalArgumentException("Invalid route");
            }
        }

        UUID world() {
            return first.rail().world();
        }
    }

    record Hit(Route route, Segment segment) {}

    /** A position whose block must still match for its route to stay valid; null shape means lodestone. */
    record Check(TrackPos pos, RailShape shape) {}

    private record Cell(int to, Segment segment, Route route) {}

    private final Map<UUID, Route> routes = new LinkedHashMap<>();
    private final Map<Lane, TreeMap<Integer, Cell>> cells = new HashMap<>();
    private final Map<TrackPos, List<Route>> lodestones = new HashMap<>();
    private final Map<TrackPos.ChunkKey, Set<Route>> chunks = new HashMap<>();
    /** Chunks containing route blocks that changed or loaded and have not been re-checked yet. */
    final Set<TrackPos.ChunkKey> unverified = new HashSet<>();
    private long revision;

    long revision() {
        return revision;
    }

    Collection<Route> routes() {
        return List.copyOf(routes.values());
    }

    boolean contains(Route route) {
        return routes.get(route.id()) == route;
    }

    Hit at(TrackPos pos) {
        for (var shape : RailShape.values()) {
            var hit = at(pos, shape);
            if (hit != null) return hit;
        }
        return null;
    }

    Hit at(TrackPos pos, RailShape shape) {
        var lane = Lane.at(pos, shape);
        var intervals = cells.get(lane);
        if (intervals == null) return null;
        int t = lane.coordinate(pos);
        var entry = intervals.floorEntry(t);
        return entry != null && entry.getValue().to() >= t
                ? new Hit(entry.getValue().route(), entry.getValue().segment()) : null;
    }

    List<Route> lodestoneRoutes(TrackPos pos) {
        return List.copyOf(lodestones.getOrDefault(pos, List.of()));
    }

    boolean watched(TrackPos pos) {
        return lodestones.containsKey(pos) || at(pos) != null;
    }

    boolean touches(TrackPos.ChunkKey chunk) {
        return chunks.containsKey(chunk);
    }

    /** Registers a route, replacing any existing routes that share one of its rails. */
    List<Route> add(Route route) {
        var replaced = new LinkedHashSet<Route>();
        for (var segment : route.segments()) {
            for (int t = segment.from(); t <= segment.to(); t++) {
                var hit = at(segment.lane().pos(t));
                if (hit != null) replaced.add(hit.route());
            }
        }
        replaced.forEach(this::remove);
        insert(route);
        return List.copyOf(replaced);
    }

    /** Restores a saved route; saved routes may never overlap. */
    void restore(Route route) {
        for (var segment : route.segments()) {
            for (int t = segment.from(); t <= segment.to(); t++) {
                if (at(segment.lane().pos(t)) != null) throw new IllegalArgumentException("Overlapping routes");
            }
        }
        if (routes.containsKey(route.id())) throw new IllegalArgumentException("Duplicate route id");
        insert(route);
    }

    private void insert(Route route) {
        routes.put(route.id(), route);
        for (var segment : route.segments()) {
            cells.computeIfAbsent(segment.lane(), unused -> new TreeMap<>())
                    .put(segment.from(), new Cell(segment.to(), segment, route));
        }
        for (var end : List.of(route.first(), route.second())) {
            lodestones.computeIfAbsent(end.lodestone(), unused -> new ArrayList<>()).add(route);
        }
        for (var chunk : chunksOf(route)) chunks.computeIfAbsent(chunk, unused -> new HashSet<>()).add(route);
        revision++;
    }

    void remove(Route route) {
        if (!contains(route)) return;
        routes.remove(route.id());
        for (var segment : route.segments()) {
            var intervals = cells.get(segment.lane());
            intervals.remove(segment.from());
            if (intervals.isEmpty()) cells.remove(segment.lane());
        }
        for (var end : List.of(route.first(), route.second())) {
            var list = lodestones.get(end.lodestone());
            list.remove(route);
            if (list.isEmpty()) lodestones.remove(end.lodestone());
        }
        for (var chunk : chunksOf(route)) {
            var set = chunks.get(chunk);
            set.remove(route);
            if (set.isEmpty()) chunks.remove(chunk);
        }
        revision++;
    }

    List<Check> checksIn(TrackPos.ChunkKey chunk) {
        var result = new ArrayList<Check>();
        for (var route : chunks.getOrDefault(chunk, Set.of())) {
            for (var segment : route.segments()) {
                var lane = segment.lane();
                if ((lane.cross() >> 4) != (lane.shape().xAxis() ? chunk.z() : chunk.x())) continue;
                int low = (lane.shape().xAxis() ? chunk.x() : chunk.z()) * 16;
                for (int t = Math.max(segment.from(), low); t <= Math.min(segment.to(), low + 15); t++) {
                    result.add(new Check(lane.pos(t), lane.shape()));
                }
            }
            for (var end : List.of(route.first(), route.second())) {
                if (end.lodestone().chunk().equals(chunk)) result.add(new Check(end.lodestone(), null));
            }
        }
        return result;
    }

    private static Set<TrackPos.ChunkKey> chunksOf(Route route) {
        var result = new HashSet<TrackPos.ChunkKey>();
        for (var segment : route.segments()) {
            for (int t = segment.from(); t <= segment.to(); t++) result.add(segment.lane().pos(t).chunk());
        }
        result.add(route.first().lodestone().chunk());
        result.add(route.second().lodestone().chunk());
        return result;
    }
}
