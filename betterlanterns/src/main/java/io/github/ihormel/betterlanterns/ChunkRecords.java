package io.github.ihormel.betterlanterns;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

final class ChunkRecords {
    record SavedLight(Material original, int level) {}
    record CandleState(boolean lit) {}

    private final NamespacedKey lightsKey;
    private final NamespacedKey candlesKey;

    ChunkRecords(NamespacedKey lightsKey, NamespacedKey candlesKey) {
        this.lightsKey = lightsKey;
        this.candlesKey = candlesKey;
    }

    Map<Position, SavedLight> lights(Chunk chunk) {
        var result = new HashMap<Position, SavedLight>();
        for (String row : rows(chunk, lightsKey)) {
            String[] fields = row.split(",");
            if (fields.length != 5) throw new IllegalStateException("Invalid saved light in " + chunk);
            var original = Material.valueOf(fields[3]);
            int level = Integer.parseInt(fields[4]);
            if (!original.isAir() || level < 1 || level > 15) throw new IllegalStateException("Invalid saved light state.");
            result.put(position(chunk, fields), new SavedLight(original, level));
        }
        return result;
    }

    Map<Position, CandleState> candles(Chunk chunk) {
        var result = new HashMap<Position, CandleState>();
        for (String row : rows(chunk, candlesKey)) {
            String[] fields = row.split(",");
            if (fields.length != 4 || !(fields[3].equals("true") || fields[3].equals("false"))) {
                throw new IllegalStateException("Invalid saved soul candle in " + chunk);
            }
            result.put(position(chunk, fields), new CandleState(Boolean.parseBoolean(fields[3])));
        }
        return result;
    }

    void lights(Chunk chunk, Map<Position, SavedLight> lights) {
        write(chunk, lightsKey, lights.entrySet().stream().filter(entry -> entry.getKey().chunk().equals(ChunkPosition.of(chunk)))
                .map(entry -> coordinates(entry.getKey()) + "," + entry.getValue().original().name()
                        + "," + entry.getValue().level()).sorted().toList());
    }

    void candles(Chunk chunk, Map<Position, CandleState> candles) {
        write(chunk, candlesKey, candles.entrySet().stream().filter(entry -> entry.getKey().chunk().equals(ChunkPosition.of(chunk)))
                .map(entry -> coordinates(entry.getKey()) + "," + entry.getValue().lit()).sorted().toList());
    }

    private static Position position(Chunk chunk, String[] fields) {
        int x = Integer.parseInt(fields[0]);
        int y = Integer.parseInt(fields[1]);
        int z = Integer.parseInt(fields[2]);
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < chunk.getWorld().getMinHeight()
                || y >= chunk.getWorld().getMaxHeight()) throw new IllegalStateException("Saved position outside chunk.");
        return new Position(chunk.getWorld().getUID(), (chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z);
    }

    private static String coordinates(Position position) {
        return (position.x() & 15) + "," + position.y() + "," + (position.z() & 15);
    }

    private static String[] rows(Chunk chunk, NamespacedKey key) {
        String text = chunk.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return text == null || text.isEmpty() ? new String[0] : text.split("\n");
    }

    private static void write(Chunk chunk, NamespacedKey key, java.util.List<String> rows) {
        if (rows.isEmpty()) chunk.getPersistentDataContainer().remove(key);
        else chunk.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join("\n", rows));
    }
}
