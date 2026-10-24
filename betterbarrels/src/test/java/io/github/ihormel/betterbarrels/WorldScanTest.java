package io.github.ihormel.betterbarrels;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldScanTest {
    @TempDir Path temp;
    private JavaPlugin plugin;
    private Server server;
    private World world;
    private Chunk chunk;
    private Conversion conversion;
    private ScanState state;
    private WorldScan scan;
    private Path region;

    @BeforeEach
    void setup() throws IOException {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        world = mock(World.class);
        chunk = mock(Chunk.class);
        conversion = mock(Conversion.class);
        UUID id = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getWorlds()).thenReturn(List.of(world));
        when(server.getWorld(id)).thenReturn(world);
        when(world.getUID()).thenReturn(id);
        when(world.getWorldFolder()).thenReturn(temp.resolve("world").toFile());
        when(world.getLoadedChunks()).thenReturn(new Chunk[0]);
        when(world.getChunkAt(anyInt(), anyInt(), eq(false))).thenReturn(chunk);
        when(chunk.getTileEntities()).thenReturn(new BlockState[0]);
        Path folder = temp.resolve("world/region");
        Files.createDirectories(folder);
        region = folder.resolve("r.-1.0.mca");
        state = new ScanState(temp.resolve("progress"));
        scan = new WorldScan(plugin, state, conversion);
    }

    @Test
    void visitsOnlyAllocatedChunksWithoutGeneratingAndCompletesAfterWorldSave() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4096);
        header.putInt((2 << 8) | 1);
        Files.write(region, header.array());
        scan.start(false, false);
        scan.tick();
        verify(world).getChunkAt(-32, 0, false);
        verify(world).unloadChunkRequest(-32, 0);
        assertFalse(state.complete());
        scan.tick();
        scan.tick();
        assertTrue(state.complete());
        assertFalse(scan.safeToUninstall());
        verify(world, atLeastOnce()).save();
        verify(world, never()).getChunkAt(anyInt(), anyInt(), eq(true));
    }

    @Test
    void savesCursorAndRestartResumesAtNextChunk() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4096);
        for (int i = 0; i < 1024; i++) header.putInt((2 << 8) | 1);
        Files.write(region, header.array());
        scan.start(false, false);
        for (int i = 0; i < 128; i++) scan.tick();
        WorldScan resumed = new WorldScan(plugin, new ScanState(temp.resolve("progress")), conversion);
        clearInvocations(world);
        resumed.start(false, false);
        resumed.tick();
        verify(world).getChunkAt(-32, 4, false);
        verify(world, never()).getChunkAt(-32, 0, false);
    }

    @Test
    void failedChunkCannotClaimUninstallCompletion() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4096);
        header.putInt((2 << 8) | 1);
        Files.write(region, header.array());
        scan.start(true, true);
        when(world.getChunkAt(-32, 0, false)).thenReturn(null);
        scan.tick();
        scan.tick();
        assertTrue(scan.status().contains("PAUSED"));
        assertFalse(scan.safeToUninstall());
        assertFalse(state.complete());
    }

    @Test
    void loadedBarrelsAreHandledBeforeBackgroundScanAndUninstallStaysSticky() throws IOException {
        Barrel barrel = mock(Barrel.class);
        when(chunk.getTileEntities()).thenReturn(new BlockState[]{barrel});
        when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
        scan.start(true, true);
        verify(conversion, never()).prepare(any(), anyBoolean());
        scan.tick();
        verify(conversion).prepare(barrel, true);
        scan.tick();
        assertTrue(scan.safeToUninstall());
        scan.start(true, state.uninstall());
        assertTrue(state.uninstall());
        assertFalse(state.complete());
    }

    @Test
    void truncatedRegionPausesScanWithoutAlteringProgress() throws IOException {
        Files.write(region, new byte[5]);
        scan.start(false, false);
        scan.tick();
        assertTrue(scan.status().contains("PAUSED"));
        assertFalse(scan.safeToUninstall());
        verify(world, never()).getChunkAt(anyInt(), anyInt(), anyBoolean());
    }
}
