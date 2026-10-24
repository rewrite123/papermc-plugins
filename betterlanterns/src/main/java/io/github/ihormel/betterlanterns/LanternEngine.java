package io.github.ihormel.betterlanterns;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Light;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.entity.Enemy;

final class LanternEngine {
    private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private final Server server;
    private final ChunkRecords records;
    private final Set<ChunkPosition> loaded = new HashSet<>();
    private final Set<ChunkPosition> scans = new LinkedHashSet<>();
    private final Map<Position, Boolean> sources = new HashMap<>();
    private final Map<ChunkPosition, Set<Position>> sourceIndex = new HashMap<>();
    private final Set<Position> dirty = new LinkedHashSet<>();
    private final Map<Position, Map<Position, Integer>> footprints = new HashMap<>();
    private final Map<Position, Map<Position, Integer>> contributions = new HashMap<>();
    private final Map<Position, ChunkRecords.SavedLight> lights = new HashMap<>();
    private final Map<Position, ChunkRecords.CandleState> candles = new HashMap<>();
    private final Map<Position, Integer> candleDrops = new HashMap<>();
    private int ticks;

    LanternEngine(Server server, ChunkRecords records) {
        this.server = server;
        this.records = records;
    }

    void load(Chunk chunk) {
        var key = ChunkPosition.of(chunk);
        if (!loaded.add(key)) return;
        var recovered = records.lights(chunk);
        recovered.forEach((position, saved) -> restore(position, saved));
        records.lights(chunk, Map.of());
        candles.putAll(records.candles(chunk));
        candles.keySet().stream().filter(position -> position.chunk().equals(key)).forEach(position -> {
            if (position.block(chunk.getWorld()).getBlockData() instanceof Candle candle) {
                candleDrops.put(position, candle.getCandles());
            }
        });
        scans.add(key);
        changed(new Position(key.world(), key.x() << 4, chunk.getWorld().getMinHeight(), key.z() << 4));
    }

    void unload(Chunk chunk) {
        var key = ChunkPosition.of(chunk);
        loaded.remove(key);
        scans.remove(key);
        for (var source : Set.copyOf(sourceIndex.getOrDefault(key, Set.of()))) removeSource(source);
        lights.keySet().removeIf(position -> position.chunk().equals(key));
        contributions.keySet().removeIf(position -> position.chunk().equals(key));
        candles.keySet().removeIf(position -> position.chunk().equals(key));
        candleDrops.keySet().removeIf(position -> position.chunk().equals(key));
        changed(new Position(key.world(), key.x() << 4, chunk.getWorld().getMinHeight(), key.z() << 4));
    }

    void tick() {
        if (!scans.isEmpty()) {
            var key = scans.iterator().next();
            scans.remove(key);
            World world = server.getWorld(key.world());
            if (world != null && loaded.contains(key) && world.isChunkLoaded(key.x(), key.z())) scan(world.getChunkAt(key.x(), key.z()));
        }
        for (int i = 0; i < 8 && !dirty.isEmpty(); i++) {
            Position position = dirty.iterator().next();
            dirty.remove(position);
            refresh(position);
        }
        if (++ticks % 10 == 0) candleParticles();
        if (ticks % 20 == 0) {
            for (var position : Set.copyOf(sources.keySet())) {
                World world = world(position);
                if (world == null) continue;
                if (sources.get(position)) {
                    if (!isLantern(position.block(world).getType())) removeSource(position);
                } else if (!candles.containsKey(position) || position.block(world).getType() != Material.CANDLE) {
                    removeSource(position);
                    removeCandle(position);
                } else {
                    dirty.add(position);
                }
            }
        }
    }

    private void scan(Chunk chunk) {
        var snapshot = chunk.getChunkSnapshot(false, false, false);
        int minimum = chunk.getWorld().getMinHeight();
        int maximum = chunk.getWorld().getMaxHeight();
        for (int section = minimum >> 4; section <= (maximum - 1) >> 4; section++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = Math.max(minimum, section << 4); y < Math.min(maximum, (section + 1) << 4); y++) {
                        if (isLantern(snapshot.getBlockType(x, y, z))) {
                            source(new Position(chunk.getWorld().getUID(), (chunk.getX() << 4) + x,
                                    y, (chunk.getZ() << 4) + z), true);
                        }
                    }
                }
            }
        }
        for (var position : Set.copyOf(candles.keySet())) {
            if (!position.chunk().equals(ChunkPosition.of(chunk))) continue;
            if (position.block(chunk.getWorld()).getType() == Material.CANDLE) source(position, false);
            else removeCandle(position);
        }
    }

    static boolean isLantern(Material material) {
        return material == Material.LANTERN || material == Material.SOUL_LANTERN;
    }

    void source(Position position, boolean lantern) {
        sources.put(position, lantern);
        sourceIndex.computeIfAbsent(position.chunk(), ignored -> new HashSet<>()).add(position);
        dirty.add(position);
    }

    void changed(Position position) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                dirty.addAll(sourceIndex.getOrDefault(new ChunkPosition(position.world(),
                        (position.x() >> 4) + dx, (position.z() >> 4) + dz), Set.of()));
            }
        }
    }

    boolean nearby(Position position) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (sourceIndex.containsKey(new ChunkPosition(position.world(),
                        (position.x() >> 4) + dx, (position.z() >> 4) + dz))) return true;
            }
        }
        return false;
    }

    boolean isSoul(Position position) { return candles.containsKey(position); }
    boolean isLit(Position position) { return candles.containsKey(position) && candles.get(position).lit(); }

    void candle(Position position, boolean lit) {
        candles.put(position, new ChunkRecords.CandleState(lit));
        World world = world(position);
        if (world != null && position.block(world).getBlockData() instanceof Candle candle) {
            candleDrops.put(position, candle.getCandles());
        }
        saveCandles(position);
        source(position, false);
    }

    void removeCandle(Position position) {
        candleDrops.remove(position);
        if (candles.remove(position) != null) saveCandles(position);
        if (Boolean.FALSE.equals(sources.get(position))) removeSource(position);
    }

    boolean claimCandleDrops(Position position, int amount) {
        int available = candleDrops.getOrDefault(position, 0);
        if (amount < 1 || amount > available) return false;
        candleDrops.put(position, available - amount);
        return true;
    }

    private void saveCandles(Position position) {
        World world = world(position);
        if (world != null) records.candles(world.getChunkAt(position.x() >> 4, position.z() >> 4), candles);
    }

    void removeSource(Position position) {
        sources.remove(position);
        dirty.remove(position);
        var indexed = sourceIndex.get(position.chunk());
        if (indexed != null) {
            indexed.remove(position);
            if (indexed.isEmpty()) sourceIndex.remove(position.chunk());
        }
        apply(position, Map.of());
    }

    void refresh(Position position) {
        World world = world(position);
        if (world == null || !sources.containsKey(position)) return;
        boolean lantern = sources.get(position);
        if (lantern && !isLantern(position.block(world).getType())) {
            removeSource(position);
            return;
        }
        if (!lantern) {
            if (position.block(world).getType() != Material.CANDLE) {
                removeCandle(position);
                return;
            }
            var data = (Candle) position.block(world).getBlockData();
            if (data.isWaterlogged() && isLit(position)) candle(position, false);
            if (data.isLit()) {
                data.setLit(false);
                position.block(world).setBlockData(data, false);
            }
            int emission = isLit(position) ? data.getCandles() * 3 : 0;
            apply(position, emission > 0 ? halo(position, 1, emission - 1) : Map.of());
        } else {
            apply(position, halo(position, 3, 15));
        }
    }

    private Map<Position, Integer> halo(Position source, int radius, int level) {
        var result = new HashMap<Position, Integer>();
        var visited = new HashSet<Position>();
        var queue = new ArrayDeque<Position>();
        queue.add(source);
        visited.add(source);
        for (int depth = 0; depth < radius; depth++) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                Position current = queue.remove();
                for (int[] offset : NEIGHBORS) {
                    Position next = current.offset(offset[0], offset[1], offset[2]);
                    if (!visited.add(next)) continue;
                    World world = world(next);
                    if (world == null) continue;
                    var block = next.block(world);
                    if (block.getType().isAir() || (block.getType() == Material.LIGHT && lights.containsKey(next))) {
                        result.put(next, level);
                        queue.add(next);
                    }
                }
            }
        }
        return result;
    }

    private void apply(Position source, Map<Position, Integer> next) {
        var previous = footprints.remove(source);
        Set<Position> affected = new HashSet<>(next.keySet());
        if (previous != null) affected.addAll(previous.keySet());
        if (!next.isEmpty()) footprints.put(source, next);
        var placements = new HashMap<Position, Light>();
        for (var position : affected) {
            var owners = contributions.computeIfAbsent(position, ignored -> new HashMap<>());
            owners.remove(source);
            if (next.containsKey(position)) owners.put(source, next.get(position));
            if (owners.isEmpty()) contributions.remove(position);
            World world = world(position);
            if (world == null) continue;
            int required = owners.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            var saved = lights.get(position);
            var block = position.block(world);
            if (saved != null && block.getBlockData() instanceof Light light && light.getLevel() != saved.level()) {
                lights.remove(position);
                continue;
            }
            if (required == 0) {
                if (saved != null) {
                    restore(position, saved);
                    lights.remove(position);
                }
            } else if (block.getType().isAir() || (saved != null && block.getType() == Material.LIGHT)) {
                var original = saved == null ? block.getType() : saved.original();
                boolean waterlogged = block.getBlockData() instanceof Light light && light.isWaterlogged();
                if (waterlogged) {
                    restore(position, saved);
                    lights.remove(position);
                } else if (saved == null || saved.level() != required || block.getType() != Material.LIGHT) {
                    lights.put(position, new ChunkRecords.SavedLight(original, required));
                    var data = (Light) server.createBlockData(Material.LIGHT);
                    data.setLevel(required);
                    placements.put(position, data);
                }
            } else {
                lights.remove(position);
            }
        }
        Set<ChunkPosition> chunks = affected.stream().map(Position::chunk).collect(java.util.stream.Collectors.toSet());
        for (var chunk : chunks) {
            World world = server.getWorld(chunk.world());
            if (world != null && loaded.contains(chunk) && world.isChunkLoaded(chunk.x(), chunk.z())) {
                records.lights(world.getChunkAt(chunk.x(), chunk.z()), lights);
            }
        }
        placements.forEach((position, data) -> {
            World world = world(position);
            if (world != null) position.block(world).setBlockData(data, false);
        });
    }

    private void restore(Position position, ChunkRecords.SavedLight saved) {
        World world = world(position);
        if (world == null) return;
        var block = position.block(world);
        if (block.getBlockData() instanceof Light light && light.getLevel() == saved.level()) {
            block.setType(light.isWaterlogged() ? Material.WATER : saved.original(), false);
        }
    }

    private World world(Position position) {
        World world = server.getWorld(position.world());
        if (world == null || !loaded.contains(position.chunk()) || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)
                || position.y() < world.getMinHeight() || position.y() >= world.getMaxHeight()) return null;
        return world;
    }

    boolean protects(org.bukkit.entity.LivingEntity entity, SpawnReason reason) {
        if (!(entity instanceof Enemy) || reason != SpawnReason.NATURAL) return false;
        Location target = entity.getLocation().add(0, .5, 0);
        int chunkX = target.getBlockX() >> 4;
        int chunkZ = target.getBlockZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (var source : sourceIndex.getOrDefault(new ChunkPosition(target.getWorld().getUID(), chunkX + dx, chunkZ + dz), Set.of())) {
                    if (!Boolean.TRUE.equals(sources.get(source))) continue;
                    World world = world(source);
                    if (world == null || !isLantern(source.block(world).getType())) continue;
                    if (source.center(world).distanceSquared(target) <= 24 * 24 && unobstructed(source, target)) return true;
                }
            }
        }
        return false;
    }

    boolean unobstructed(Position source, Location target) {
        World world = world(source);
        if (world == null || target.getWorld() != world) return false;
        Location start = source.center(world);
        var delta = target.toVector().subtract(start.toVector());
        double distance = delta.length();
        if (distance < 1e-8) return true;
        var direction = delta.clone().multiply(1 / distance);
        int x = source.x(), y = source.y(), z = source.z();
        int stepX = direction.getX() > 0 ? 1 : -1;
        int stepY = direction.getY() > 0 ? 1 : -1;
        int stepZ = direction.getZ() > 0 ? 1 : -1;
        double strideX = direction.getX() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.getX());
        double strideY = direction.getY() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.getY());
        double strideZ = direction.getZ() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.getZ());
        double nextX = strideX / 2, nextY = strideY / 2, nextZ = strideZ / 2;
        while (true) {
            double traveled = Math.min(nextX, Math.min(nextY, nextZ));
            if (traveled > distance) return true;
            if (nextX <= traveled) { x += stepX; nextX += strideX; }
            if (nextY <= traveled) { y += stepY; nextY += strideY; }
            if (nextZ <= traveled) { z += stepZ; nextZ += strideZ; }
            Position position = new Position(source.world(), x, y, z);
            if (world(position) == null) return false;
            var block = position.block(world);
            if (!block.isPassable() && block.rayTrace(start, direction, distance, FluidCollisionMode.NEVER) != null) return false;
        }
    }

    private void candleParticles() {
        for (var entry : Set.copyOf(candles.entrySet())) {
            Position position = entry.getKey();
            World world = world(position);
            if (world == null) continue;
            if (position.block(world).getType() != Material.CANDLE) { removeCandle(position); continue; }
            var data = (Candle) position.block(world).getBlockData();
            if (data.isWaterlogged() && entry.getValue().lit()) { candle(position, false); continue; }
            if (!entry.getValue().lit()) continue;
            int count = data.getCandles();
            for (int i = 0; i < count; i++) {
                double x = count == 1 ? .5 : .35 + (i % 2) * .3;
                double z = count <= 2 ? .5 : .35 + (i / 2) * .3;
                world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                        new Location(world, position.x() + x, position.y() + .55, position.z() + z),
                        1, 0, .01, 0, 0);
            }
        }
    }

    void close() {
        for (var entry : Map.copyOf(lights).entrySet()) restore(entry.getKey(), entry.getValue());
        for (var chunk : loaded) {
            World world = server.getWorld(chunk.world());
            if (world != null && world.isChunkLoaded(chunk.x(), chunk.z())) {
                records.lights(world.getChunkAt(chunk.x(), chunk.z()), Map.of());
            }
        }
        loaded.clear();
        sources.clear();
        sourceIndex.clear();
        candles.clear();
        candleDrops.clear();
        lights.clear();
        contributions.clear();
        footprints.clear();
        dirty.clear();
        scans.clear();
    }
}
