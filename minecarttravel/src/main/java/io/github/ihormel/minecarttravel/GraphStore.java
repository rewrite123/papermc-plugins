package io.github.ihormel.minecarttravel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class GraphStore {
    private final Path path;

    GraphStore(Path path) {
        this.path = path;
    }

    RailGraph load() throws IOException {
        var graph = new RailGraph();
        if (!Files.exists(path)) return graph;
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.getFirst().equals("minecarttravel-graph-1")) {
            throw new IOException("Unsupported or missing graph header: " + path);
        }
        for (int index = 1; index < lines.size(); index++) {
            var parts = lines.get(index).split(" ");
            try {
                switch (parts[0]) {
                    case "R" -> {
                        if (parts.length != 8) throw new IllegalArgumentException("Invalid run");
                        var lane = new RailGraph.Lane(UUID.fromString(parts[1]), RailShape.valueOf(parts[2]),
                                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                        if (!parts[7].equals(".")) throw new IllegalArgumentException("Invalid run terminator");
                        graph.restore(new RailGraph.Run(lane, Integer.parseInt(parts[5]), Integer.parseInt(parts[6])));
                    }
                    case "L" -> {
                        if (parts.length != 5) throw new IllegalArgumentException("Invalid stopper");
                        graph.restoreStopper(new TrackPos(UUID.fromString(parts[1]), Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
                    }
                    case "D", "K" -> {
                        if (parts.length != 4) throw new IllegalArgumentException("Invalid dirty chunk");
                        var key = new TrackPos.ChunkKey(UUID.fromString(parts[1]),
                                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                        if (parts[0].equals("D")) graph.dirty.add(key);
                        else graph.knownChunks.add(key);
                    }
                    default -> throw new IllegalArgumentException("Unknown record");
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid graph record at line " + (index + 1), exception);
            }
        }
        return graph;
    }

    void save(RailGraph graph) throws IOException {
        var text = new StringBuilder("minecarttravel-graph-1\n");
        for (var run : graph.runs()) {
            var lane = run.lane();
            text.append("R ").append(lane.world()).append(' ').append(lane.shape()).append(' ')
                    .append(lane.cross()).append(' ').append(lane.intercept()).append(' ')
                    .append(run.from()).append(' ').append(run.to()).append(" .\n");
        }
        for (var pos : graph.stoppers()) {
            text.append("L ").append(pos.world()).append(' ').append(pos.x()).append(' ')
                    .append(pos.y()).append(' ').append(pos.z()).append('\n');
        }
        for (var chunk : graph.dirty) {
            text.append("D ").append(chunk.world()).append(' ').append(chunk.x()).append(' ')
                    .append(chunk.z()).append('\n');
        }
        for (var chunk : graph.knownChunks) {
            text.append("K ").append(chunk.world()).append(' ').append(chunk.x()).append(' ')
                    .append(chunk.z()).append('\n');
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
}
