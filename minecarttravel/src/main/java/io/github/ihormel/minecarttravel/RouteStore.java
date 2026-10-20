package io.github.ihormel.minecarttravel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.UUID;

final class RouteStore {
    static final String HEADER = "minecarttravel-routes-1";
    private final Path path;

    RouteStore(Path path) {
        this.path = path;
    }

    RouteRegistry load() throws IOException {
        var registry = new RouteRegistry();
        if (!Files.exists(path)) return registry;
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.getFirst().equals(HEADER)) {
            throw new IOException("Unsupported or missing route file header: " + path);
        }
        int index = 1;
        try {
            while (index < lines.size()) {
                var head = split(lines.get(index++), "R", 5);
                var id = UUID.fromString(head[1]);
                var world = UUID.fromString(head[2]);
                int rails = Integer.parseInt(head[3]);
                int turns = Integer.parseInt(head[4]);
                var first = end(world, split(line(lines, index++), "E", 10));
                var second = end(world, split(line(lines, index++), "E", 10));
                var segments = new ArrayList<RouteRegistry.Segment>();
                while (index < lines.size() && lines.get(index).startsWith("S ")) {
                    var s = split(lines.get(index++), "S", 9);
                    segments.add(new RouteRegistry.Segment(new Lane(world, RailShape.valueOf(s[1]),
                            Integer.parseInt(s[2]), Integer.parseInt(s[3])), Integer.parseInt(s[4]),
                            Integer.parseInt(s[5]), port(s, 6)));
                }
                registry.restore(new RouteRegistry.Route(id, first, second, segments, rails, turns));
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            throw new IOException("Invalid route record near line " + index, exception);
        }
        return registry;
    }

    void save(RouteRegistry registry) throws IOException {
        var text = new StringBuilder(HEADER).append('\n');
        for (var route : registry.routes()) {
            text.append("R ").append(route.id()).append(' ').append(route.world()).append(' ')
                    .append(route.rails()).append(' ').append(route.turns()).append('\n');
            for (var end : java.util.List.of(route.first(), route.second())) {
                var rail = end.rail();
                var stop = end.lodestone();
                text.append("E ").append(rail.x()).append(' ').append(rail.y()).append(' ').append(rail.z()).append(' ')
                        .append(port(end.outward())).append(' ')
                        .append(stop.x()).append(' ').append(stop.y()).append(' ').append(stop.z()).append('\n');
            }
            for (var segment : route.segments()) {
                var lane = segment.lane();
                text.append("S ").append(lane.shape()).append(' ').append(lane.cross()).append(' ')
                        .append(lane.intercept()).append(' ').append(segment.from()).append(' ')
                        .append(segment.to()).append(' ').append(port(segment.towardFirst())).append('\n');
            }
        }
        Files.createDirectories(path.getParent());
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String line(java.util.List<String> lines, int index) {
        if (index >= lines.size()) throw new IllegalArgumentException("Truncated route");
        return lines.get(index);
    }

    private static String[] split(String line, String type, int length) {
        var parts = line.split(" ");
        if (parts.length != length || !parts[0].equals(type)) {
            throw new IllegalArgumentException("Expected " + type + " record");
        }
        return parts;
    }

    private static RouteRegistry.End end(UUID world, String[] parts) {
        return new RouteRegistry.End(new TrackPos(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])), port(parts, 4), new TrackPos(world, Integer.parseInt(parts[7]),
                Integer.parseInt(parts[8]), Integer.parseInt(parts[9])));
    }

    private static RailShape.Port port(String[] parts, int at) {
        return new RailShape.Port(Integer.parseInt(parts[at]), Integer.parseInt(parts[at + 1]),
                Integer.parseInt(parts[at + 2]));
    }

    private static String port(RailShape.Port port) {
        return port.dx() + " " + port.dz() + " " + port.height();
    }
}
