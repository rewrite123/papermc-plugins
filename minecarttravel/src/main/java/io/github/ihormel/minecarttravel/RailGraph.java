package io.github.ihormel.minecarttravel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Collinear cells of the same rail shape form one interval, including slopes.
 * Edits split/merge intervals; no block or chunk references survive an update.
 */
final class RailGraph {
    record Lane(UUID world, RailShape shape, int cross, int intercept) {
        static Lane at(TrackPos pos, RailShape shape) {
            int t = shape.xAxis() ? pos.x() : pos.z();
            return new Lane(pos.world(), shape, shape.xAxis() ? pos.z() : pos.x(), pos.y() - shape.slope() * t);
        }

        TrackPos pos(int t) {
            return new TrackPos(world, shape.xAxis() ? t : cross,
                    intercept + shape.slope() * t, shape.xAxis() ? cross : t);
        }

        int coordinate(TrackPos pos) {
            return shape.xAxis() ? pos.x() : pos.z();
        }
    }

    record Run(Lane lane, int from, int to) {}
    private final Map<Lane, NavigableMap<Integer, Integer>> lanes = new HashMap<>();
    private final Set<TrackPos> stoppers = new HashSet<>();
    final Set<TrackPos.ChunkKey> dirty = new HashSet<>();
    final Set<TrackPos.ChunkKey> knownChunks = new HashSet<>();
    private final Set<TrackPos.ChunkKey> trackChunks = new HashSet<>();
    private long revision;

    long revision() {
        return revision;
    }

    void invalidate() {
        revision++;
    }

    RailShape rail(TrackPos pos) {
        var run = run(pos);
        return run == null ? null : run.lane().shape();
    }

    Run run(TrackPos pos) {
        for (var shape : RailShape.values()) {
            var lane = Lane.at(pos, shape);
            var intervals = lanes.get(lane);
            if (intervals == null) continue;
            var entry = intervals.floorEntry(lane.coordinate(pos));
            if (entry != null && entry.getValue() >= lane.coordinate(pos)) {
                return new Run(lane, entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    boolean stopper(TrackPos pos) {
        return stoppers.contains(pos);
    }

    void observe(TrackPos pos, RailShape shape, boolean lodestone) {
        var old = rail(pos);
        if (old == shape && stopper(pos) == lodestone) return;
        if (old != null) remove(pos, old);
        if (shape != null) add(pos, shape);
        if (lodestone) stoppers.add(pos);
        else stoppers.remove(pos);
        if (shape != null || lodestone) trackChunks.add(pos.chunk());
        invalidate();
    }

    private void add(TrackPos pos, RailShape shape) {
        var lane = Lane.at(pos, shape);
        var intervals = lanes.computeIfAbsent(lane, unused -> new TreeMap<>());
        int t = lane.coordinate(pos);
        int from = t;
        int to = t;
        if (shape.straight()) {
            var lower = intervals.floorEntry(t);
            if (lower != null && lower.getValue() == t - 1) {
                from = lower.getKey();
                intervals.remove(lower.getKey());
            }
            Integer next = intervals.remove(t + 1);
            if (next != null) to = next;
        }
        intervals.put(from, to);
    }

    private void remove(TrackPos pos, RailShape shape) {
        var lane = Lane.at(pos, shape);
        var intervals = lanes.get(lane);
        int t = lane.coordinate(pos);
        var entry = intervals.floorEntry(t);
        int from = entry.getKey();
        int to = entry.getValue();
        intervals.remove(from);
        if (from < t) intervals.put(from, t - 1);
        if (to > t) intervals.put(t + 1, to);
        if (intervals.isEmpty()) lanes.remove(lane);
    }

    void clearChunk(TrackPos.ChunkKey chunk) {
        for (var run : runs()) {
            var lane = run.lane();
            if (!lane.world().equals(chunk.world())
                    || (lane.cross() >> 4) != (lane.shape().xAxis() ? chunk.z() : chunk.x())) continue;
            int low = (lane.shape().xAxis() ? chunk.x() : chunk.z()) * 16;
            int high = low + 15;
            int from = Math.max(run.from(), low);
            int to = Math.min(run.to(), high);
            if (from > to) continue;
            var intervals = lanes.get(lane);
            intervals.remove(run.from());
            if (run.from() < from) intervals.put(run.from(), from - 1);
            if (run.to() > to) intervals.put(to + 1, run.to());
            if (intervals.isEmpty()) lanes.remove(lane);
        }
        stoppers.removeIf(pos -> pos.chunk().equals(chunk));
        trackChunks.remove(chunk);
        invalidate();
    }

    List<TrackPos> observationsInChunk(TrackPos.ChunkKey chunk) {
        var result = new ArrayList<TrackPos>();
        if (!trackChunks.contains(chunk)) return result;
        for (var run : runs()) {
            var lane = run.lane();
            if (!lane.world().equals(chunk.world())
                    || (lane.cross() >> 4) != (lane.shape().xAxis() ? chunk.z() : chunk.x())) continue;
            int low = (lane.shape().xAxis() ? chunk.x() : chunk.z()) * 16;
            for (int t = Math.max(run.from(), low); t <= Math.min(run.to(), low + 15); t++) {
                result.add(lane.pos(t));
            }
        }
        for (var pos : stoppers) if (pos.chunk().equals(chunk)) result.add(pos);
        return result;
    }

    List<Run> runs() {
        var result = new ArrayList<Run>();
        lanes.forEach((lane, intervals) -> intervals.forEach((from, to) -> result.add(new Run(lane, from, to))));
        return result;
    }

    Set<TrackPos> stoppers() {
        return Set.copyOf(stoppers);
    }

    void restore(Run run) {
        if (run.from() > run.to() || (!run.lane().shape().straight() && run.from() != run.to())) {
            throw new IllegalArgumentException("Invalid rail interval");
        }
        var intervals = lanes.computeIfAbsent(run.lane(), unused -> new TreeMap<>());
        var lower = intervals.floorEntry(run.to());
        if (lower != null && lower.getValue() >= run.from()) {
            throw new IllegalArgumentException("Overlapping rail intervals");
        }
        intervals.put(run.from(), run.to());
        int firstChunk = run.from() >> 4;
        int lastChunk = run.to() >> 4;
        for (int chunk = firstChunk; chunk <= lastChunk; chunk++) {
            var lane = run.lane();
            trackChunks.add(new TrackPos.ChunkKey(lane.world(),
                    lane.shape().xAxis() ? chunk : lane.cross() >> 4,
                    lane.shape().xAxis() ? lane.cross() >> 4 : chunk));
        }
    }

    void restoreStopper(TrackPos pos) {
        stoppers.add(pos);
        trackChunks.add(pos.chunk());
    }
}
