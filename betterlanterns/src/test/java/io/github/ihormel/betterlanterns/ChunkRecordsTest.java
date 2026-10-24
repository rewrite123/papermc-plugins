package io.github.ihormel.betterlanterns;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

class ChunkRecordsTest {
    private final NamespacedKey lightKey = new NamespacedKey("betterlanterns", "lights");
    private final NamespacedKey candleKey = new NamespacedKey("betterlanterns", "candles");
    private final ChunkRecords records = new ChunkRecords(lightKey, candleKey);
    private Chunk chunk;

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        chunk = server.addSimpleWorld("world").getChunkAt(-2, -3);
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    @Test
    void chunkDataRoundTripsNegativeCoordinatesAndExcludesOtherChunks() {
        var position = new Position(chunk.getWorld().getUID(), -17, 70, -33);
        var elsewhere = position.offset(16, 0, 0);
        var saved = new ChunkRecords.SavedLight(Material.CAVE_AIR, 15);
        records.lights(chunk, Map.of(position, saved, elsewhere, saved));
        records.candles(chunk, Map.of(position, new ChunkRecords.CandleState(true)));
        assertEquals(Map.of(position, saved), records.lights(chunk));
        assertEquals(Map.of(position, new ChunkRecords.CandleState(true)), records.candles(chunk));
        records.lights(chunk, Map.of());
        assertFalse(chunk.getPersistentDataContainer().has(lightKey));
        assertFalse(records.candles(chunk).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0,70,0,STONE,15", "16,70,0,AIR,15", "0,70,0,AIR,16",
            "0,70,0,AIR,0", "0,99999,0,AIR,15", "invalid"})
    void malformedLightRecordsFailExplicitly(String text) {
        chunk.getPersistentDataContainer().set(lightKey, PersistentDataType.STRING, text);
        assertThrows(RuntimeException.class, () -> records.lights(chunk));
    }

    @Test
    void malformedCandleFlagsAreNotSilentlyTreatedAsUnlit() {
        chunk.getPersistentDataContainer().set(candleKey, PersistentDataType.STRING, "0,70,0,not-a-boolean");
        assertThrows(IllegalStateException.class, () -> records.candles(chunk));
    }
}
