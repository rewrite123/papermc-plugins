package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Rail;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrackWorldTest extends PaperTest {
    private RailGraph graph;
    private TrackWorld tracks;
    private World world;
    private TrackPos pos;

    @BeforeEach
    void createTrackWorld() {
        graph = new RailGraph();
        tracks = new TrackWorld(graph, 256);
        world = server.addSimpleWorld("world");
        var block = world.getBlockAt(8, 64, 8);
        block.setType(Material.RAIL);
        var data = (Rail) block.getBlockData();
        data.setShape(Rail.Shape.EAST_WEST);
        block.setBlockData(data);
        pos = TrackPos.at(block.getLocation());
        world.getChunkAt(0, 0).load();
    }

    @Test
    void loadedChunkScanIsBoundedAndSeedsExistingRails() {
        tracks.enqueue(world.getChunkAt(0, 0));
        tracks.tick();
        assertTrue(graph.dirty.contains(pos.chunk()));
        for (int tick = 0; tick < world.getMaxHeight() - world.getMinHeight() + 10; tick++) tracks.tick();
        assertFalse(graph.dirty.contains(pos.chunk()));
        assertEquals(RailShape.EAST_WEST, graph.rail(pos));
    }

    @Test
    void breakingARailInvalidatesImmediatelyAndThenRemovesItsGraphCell() {
        graph.observe(pos, RailShape.EAST_WEST, false);
        long revision = graph.revision();
        var event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(world.getBlockAt(8, 64, 8));
        tracks.breakBlock(event);
        assertTrue(graph.revision() > revision);
        assertTrue(graph.dirty.contains(pos.chunk()));
        world.getBlockAt(8, 64, 8).setType(Material.AIR);
        tracks.tick();
        assertNull(graph.rail(pos));
        assertFalse(graph.dirty.contains(pos.chunk()));
    }

    @Test
    void placingLodestoneAndRailsIsObservedWithoutRegistration() {
        var block = world.getBlockAt(9, 64, 8);
        block.setType(Material.LODESTONE);
        tracks.changed(block);
        tracks.changed(world.getBlockAt(8, 64, 8));
        tracks.tick();
        assertTrue(graph.stopper(new TrackPos(world.getUID(), 9, 64, 8)));
        assertEquals(RailShape.EAST_WEST, graph.rail(pos));
    }

    @Test
    void chunkReconciliationRemovesStaleSavedRails() {
        graph.observe(pos, RailShape.EAST_WEST, false);
        world.getBlockAt(8, 64, 8).setType(Material.AIR);
        tracks.enqueue(world.getChunkAt(0, 0));
        for (int tick = 0; tick < world.getMaxHeight() - world.getMinHeight() + 10; tick++) tracks.tick();
        assertNull(graph.rail(pos));
    }

    @Test
    void irrelevantEditsDoNotInvalidateTheGraph() {
        long revision = graph.revision();
        tracks.changed(world.getBlockAt(0, 100, 0));
        assertEquals(revision, graph.revision());
        assertTrue(graph.dirty.isEmpty());
    }

    @Test
    void cancelledBlockBreaksAreIgnoredByTheRegisteredListener() {
        graph.observe(pos, RailShape.EAST_WEST, false);
        server.getPluginManager().registerEvents(tracks,
                org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin());
        long revision = graph.revision();
        var event = new BlockBreakEvent(world.getBlockAt(8, 64, 8), server.addPlayer());
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertEquals(revision, graph.revision());
        assertTrue(graph.dirty.isEmpty());
    }

    @Test
    void knownTrackChunksOnlyRecheckRecordedCellsRatherThanAllTerrain() {
        graph.observe(pos, RailShape.EAST_WEST, false);
        graph.knownChunks.add(pos.chunk());
        tracks.discover(world.getChunkAt(0, 0));
        assertTrue(graph.dirty.contains(pos.chunk()));
        tracks.tick();
        assertFalse(graph.dirty.contains(pos.chunk()));
        assertEquals(RailShape.EAST_WEST, graph.rail(pos));
    }

    @Test
    void unchangedChunkReloadDoesNotCancelAnInFlightGraphWalk() {
        graph.observe(pos, RailShape.EAST_WEST, false);
        graph.knownChunks.add(pos.chunk());
        long revision = graph.revision();
        tracks.discover(world.getChunkAt(0, 0));
        tracks.tick();
        assertEquals(revision, graph.revision());
    }

    @Test
    void emptyChunkPaletteCheckAvoidsScanningUnrelatedTerrain() {
        var chunk = mock(org.bukkit.Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(10);
        when(chunk.getZ()).thenReturn(10);
        tracks.discover(chunk);
        var key = new TrackPos.ChunkKey(world.getUID(), 10, 10);
        assertTrue(graph.knownChunks.contains(key));
        assertFalse(graph.dirty.contains(key));
        clearInvocations(chunk);
        tracks.discover(chunk);
        verify(chunk, never()).contains(any(org.bukkit.block.data.BlockData.class));
    }

    @Test
    void recordedRailsRemovedOfflineAreDetectedOnTheirNextLoad() {
        graph.observe(pos, RailShape.EAST_WEST, false);
        graph.knownChunks.add(pos.chunk());
        world.getBlockAt(8, 64, 8).setType(Material.AIR);
        tracks.discover(world.getChunkAt(0, 0));
        tracks.tick();
        assertNull(graph.rail(pos));
    }
}
