package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphStoreTest {
    @TempDir Path temp;

    @Test
    void compressedGraphStoppersAndPendingMarkersSurviveRestart() throws IOException {
        var graph = new RailGraph();
        var world = UUID.randomUUID();
        for (int x = 0; x < 1000; x++) graph.observe(new TrackPos(world, x, 64, 0), RailShape.EAST_WEST, false);
        graph.observe(new TrackPos(world, -1, 64, 0), null, true);
        graph.dirty.add(new TrackPos.ChunkKey(world, 12, 0));
        graph.knownChunks.add(new TrackPos.ChunkKey(world, 10, 0));
        var path = temp.resolve("plugin/rail-graph.txt");
        var store = new GraphStore(path);
        store.save(graph);
        assertTrue(Files.size(path) < 500, "1000 straight rails must not be stored individually");
        var restored = store.load();
        assertEquals(new HashSet<>(graph.runs()), new HashSet<>(restored.runs()));
        assertEquals(graph.stoppers(), restored.stoppers());
        assertEquals(graph.dirty, restored.dirty);
        assertEquals(graph.knownChunks, restored.knownChunks);
        assertFalse(Files.exists(path.resolveSibling("rail-graph.txt.tmp")));
        store.save(restored);
        assertEquals(1, store.load().runs().size());
    }

    @Test
    void missingGraphIsANewEmptyGraph() throws IOException {
        assertTrue(new GraphStore(temp.resolve("missing")).load().runs().isEmpty());
    }

    @Test
    void corruptAndUnsupportedGraphFilesFailRatherThanSilentlyReset() throws IOException {
        var path = temp.resolve("graph");
        Files.writeString(path, "bad-header\n");
        assertThrows(IOException.class, () -> new GraphStore(path).load());
        Files.writeString(path, "minecarttravel-graph-1\nR not-a-uuid EAST_WEST 0 64 0 10 .\n");
        assertThrows(IOException.class, () -> new GraphStore(path).load());
        Files.writeString(path, "minecarttravel-graph-1\nunknown\n");
        assertThrows(IOException.class, () -> new GraphStore(path).load());
    }
}
