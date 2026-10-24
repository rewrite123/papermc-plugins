package io.github.ihormel.betterbarrels;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanStateTest {
    @TempDir Path temp;

    @Test
    void progressAndUninstallModeSurviveRestart() throws IOException {
        Path file = temp.resolve("progress.properties");
        ScanState state = new ScanState(file);
        assertFalse(state.uninstall());
        state.reset(true);
        state.cursor("world/r.-1.2.mca", 512);
        state.complete(true);
        ScanState restarted = new ScanState(file);
        assertTrue(restarted.uninstall());
        assertTrue(restarted.complete());
        assertEquals(512, restarted.cursor("world/r.-1.2.mca"));
        restarted.reset(restarted.uninstall());
        assertTrue(new ScanState(file).uninstall());
        assertEquals(0, restarted.cursor("world/r.-1.2.mca"));
        assertFalse(restarted.complete());
    }

    @Test
    void rejectsBadModeAndCursorRatherThanClaimingCompletion() throws IOException {
        Path file = temp.resolve("progress.properties");
        Files.writeString(file, "mode=bad\n");
        assertThrows(IOException.class, () -> new ScanState(file));
        Files.writeString(file, "region.test=2048\n");
        ScanState state = new ScanState(file);
        assertThrows(IllegalStateException.class, () -> state.cursor("test"));
    }

    @Test
    void regionHeaderOnlySelectsGeneratedChunksAndResolvesNegativeCoordinates() throws IOException {
        Path region = temp.resolve("r.-2.3.mca");
        ByteBuffer header = ByteBuffer.allocate(4096);
        header.putInt(0, (2 << 8) | 1);
        header.putInt(1023 * 4, (123 << 8) | 2);
        Files.write(region, header.array());
        boolean[] allocated = RegionChunks.allocated(region);
        assertTrue(allocated[0]);
        assertTrue(allocated[1023]);
        assertFalse(allocated[1]);
        assertEquals(new RegionChunks.Position(-64, 96), RegionChunks.position(region, 0));
        assertEquals(new RegionChunks.Position(-33, 127), RegionChunks.position(region, 1023));
    }

    @Test
    void corruptOrNonRegionFilesCannotBeUsedToGenerateChunks() throws IOException {
        Path truncated = temp.resolve("r.0.0.mca");
        Files.write(truncated, new byte[100]);
        assertThrows(IOException.class, () -> RegionChunks.allocated(truncated));
        assertFalse(RegionChunks.isRegion(temp.resolve("random.dat")));
        assertThrows(IllegalArgumentException.class, () -> RegionChunks.position(temp.resolve("bad.mca"), 0));
        assertThrows(IllegalArgumentException.class, () -> RegionChunks.position(truncated, 1024));
    }

    @Test
    void nonzeroInvalidAllocationIsAnErrorNotSilentlySkipped() throws IOException {
        Path region = temp.resolve("r.0.0.mca");
        ByteBuffer header = ByteBuffer.allocate(4096);
        header.putInt(256);
        Files.write(region, header.array());
        assertThrows(IOException.class, () -> RegionChunks.allocated(region));
    }
}
