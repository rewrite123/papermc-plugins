package io.github.ihormel.betterlanterns;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.RayTraceResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class EngineTest {
    private final UUID id = UUID.randomUUID();
    private final Server server = mock(Server.class);
    private final World world = mock(World.class);
    private final Map<Position, Cell> cells = new HashMap<>();
    private final Map<ChunkPosition, Chunk> chunks = new HashMap<>();
    private LanternEngine engine;
    private ChunkRecords records;
    private org.mockbukkit.mockbukkit.ServerMock bukkit;

    private final class Cell {
        final Block block = mock(Block.class);
        BlockData data;
        Cell(Position position) {
            data = data(Material.AIR);
            when(block.getWorld()).thenReturn(world);
            when(block.getX()).thenReturn(position.x());
            when(block.getY()).thenReturn(position.y());
            when(block.getZ()).thenReturn(position.z());
            when(block.getType()).thenAnswer(ignored -> data.getMaterial());
            when(block.getBlockData()).thenAnswer(ignored -> data);
            when(block.isPassable()).thenAnswer(ignored -> data.getMaterial().isAir() || data.getMaterial() == Material.LIGHT);
            when(block.rayTrace(any(), any(), anyDouble(), any())).thenReturn(mock(RayTraceResult.class));
            doAnswer(call -> { data = call.getArgument(0); return null; }).when(block).setBlockData(any(), eq(false));
            doAnswer(call -> { data = data(call.getArgument(0)); return null; }).when(block).setType(any(), eq(false));
        }
    }

    @BeforeEach
    void setup() {
        bukkit = MockBukkit.mock();
        when(world.getUID()).thenReturn(id);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(32);
        when(server.getWorld(id)).thenReturn(world);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                cell(position(call.getArgument(0), call.getArgument(1), call.getArgument(2))).block);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(call ->
                chunks.containsKey(new ChunkPosition(id, call.getArgument(0), call.getArgument(1))));
        when(world.getChunkAt(anyInt(), anyInt())).thenAnswer(call ->
                chunks.get(new ChunkPosition(id, call.getArgument(0), call.getArgument(1))));
        when(server.createBlockData(Material.LIGHT)).thenAnswer(ignored -> light(15, false));
        records = new ChunkRecords(new NamespacedKey("betterlanterns", "lights"),
                new NamespacedKey("betterlanterns", "candles"));
        engine = new LanternEngine(server, records);
        load(0, 0);
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    private Position position(int x, int y, int z) { return new Position(id, x, y, z); }
    private Cell cell(Position position) { return cells.computeIfAbsent(position, Cell::new); }
    private BlockData data(Material type) {
        var data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(type);
        return data;
    }
    private Light light(int initial, boolean waterlogged) {
        var light = mock(Light.class);
        var level = new AtomicInteger(initial);
        when(light.getMaterial()).thenReturn(Material.LIGHT);
        when(light.getLevel()).thenAnswer(ignored -> level.get());
        when(light.isWaterlogged()).thenReturn(waterlogged);
        doAnswer(call -> { level.set(call.getArgument(0)); return null; }).when(light).setLevel(anyInt());
        return light;
    }
    private Chunk load(int x, int z) {
        var chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(x);
        when(chunk.getZ()).thenReturn(z);
        // A real MockBukkit PDC exercises serialization, while blocks/ray shapes are explicit boundaries.
        PersistentDataContainer pdc = bukkit.addSimpleWorld("pdc-" + x + "-" + z).getChunkAt(0, 0).getPersistentDataContainer();
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        var snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                cell(position((x << 4) + (int) call.getArgument(0), call.getArgument(1),
                        (z << 4) + (int) call.getArgument(2))).data.getMaterial());
        when(chunk.getChunkSnapshot(false, false, false)).thenReturn(snapshot);
        chunks.put(new ChunkPosition(id, x, z), chunk);
        engine.load(chunk);
        return chunk;
    }
    private void lantern(Position position, Material type) {
        cell(position).data = data(type);
        engine.source(position, true);
        engine.refresh(position);
    }

    @Test
    void levelFifteenExtendsExactlyThreeAirStepsAndNeverReplacesOtherBlocks() {
        var source = position(8, 8, 8);
        cell(position(8, 9, 8)).data = data(Material.GLASS);
        cell(position(8, 7, 8)).data = data(Material.WATER);
        cell(position(9, 8, 8)).data = data(Material.SHORT_GRASS);
        lantern(source, Material.LANTERN);
        for (int i = 1; i <= 3; i++) {
            assertEquals(Material.LIGHT, cell(position(8 - i, 8, 8)).data.getMaterial());
            assertEquals(15, ((Light) cell(position(8 - i, 8, 8)).data).getLevel());
        }
        assertEquals(Material.AIR, cell(position(4, 8, 8)).data.getMaterial());
        assertEquals(Material.GLASS, cell(position(8, 9, 8)).data.getMaterial());
        assertEquals(Material.WATER, cell(position(8, 7, 8)).data.getMaterial());
        assertEquals(Material.SHORT_GRASS, cell(position(9, 8, 8)).data.getMaterial());
        assertEquals(Material.LANTERN, cell(source).data.getMaterial());
        assertFalse(records.lights(chunks.get(source.chunk())).isEmpty());
    }

    @Test
    void soulLanternHasTheSameHaloAndSolidWallsStopPropagation() {
        for (int y = 4; y <= 12; y++) for (int z = 4; z <= 12; z++) cell(position(9, y, z)).data = data(Material.STONE);
        lantern(position(8, 8, 8), Material.SOUL_LANTERN);
        assertEquals(Material.AIR, cell(position(10, 8, 8)).data.getMaterial());
        assertEquals(15, ((Light) cell(position(7, 8, 8)).data).getLevel());
    }

    @Test
    void sharedLightsSurviveOneLanternRemovalAndRestoreOriginalCaveAirAfterLast() {
        var shared = position(8, 8, 8);
        cell(shared).data = data(Material.CAVE_AIR);
        var first = position(6, 8, 8);
        var second = position(10, 8, 8);
        lantern(first, Material.LANTERN);
        lantern(second, Material.SOUL_LANTERN);
        cell(first).data = data(Material.AIR);
        engine.removeSource(first);
        assertEquals(Material.LIGHT, cell(shared).data.getMaterial());
        cell(second).data = data(Material.AIR);
        engine.removeSource(second);
        assertEquals(Material.CAVE_AIR, cell(shared).data.getMaterial());
        assertTrue(records.lights(chunks.get(shared.chunk())).isEmpty());
    }

    @Test
    void removalNeverDeletesPlayerBlockExternalLightsOrWater() {
        var source = position(8, 8, 8);
        var stone = position(9, 8, 8);
        var external = position(7, 8, 8);
        var wet = position(8, 8, 9);
        lantern(source, Material.LANTERN);
        cell(stone).data = data(Material.STONE);
        cell(external).data = light(7, false);
        cell(wet).data = light(15, true);
        engine.removeSource(source);
        assertEquals(Material.STONE, cell(stone).data.getMaterial());
        assertEquals(7, ((Light) cell(external).data).getLevel());
        assertEquals(Material.WATER, cell(wet).data.getMaterial());
    }

    @Test
    void lightsAreRecoveredFromChunkMetadataAfterEngineRestartAndDisableCleansLoadedChunks() {
        var source = position(8, 8, 8);
        lantern(source, Material.LANTERN);
        var chunk = chunks.get(source.chunk());
        assertFalse(records.lights(chunk).isEmpty());
        engine = new LanternEngine(server, records);
        engine.load(chunk);
        assertEquals(Material.AIR, cell(position(7, 8, 8)).data.getMaterial());
        engine.tick();
        assertEquals(Material.LIGHT, cell(position(7, 8, 8)).data.getMaterial());
        engine.close();
        assertEquals(Material.AIR, cell(position(7, 8, 8)).data.getMaterial());
        assertTrue(records.lights(chunk).isEmpty());
    }

    @Test
    void unloadingPreservesCleanupJournalButDoesNotForceLoadNeighbors() {
        var source = position(15, 8, 8);
        lantern(source, Material.LANTERN);
        assertEquals(Material.AIR, cell(position(16, 8, 8)).data.getMaterial());
        verify(world, never()).getChunkAt(1, 0);
        var chunk = chunks.get(source.chunk());
        engine.unload(chunk);
        assertFalse(records.lights(chunk).isEmpty());
        engine.load(chunk);
        assertTrue(records.lights(chunk).isEmpty());
        engine.tick();
        assertEquals(Material.LIGHT, cell(position(14, 8, 8)).data.getMaterial());
    }

    private LivingEntity hostile(double x, double y, double z) {
        var mob = mock(LivingEntity.class, withSettings().extraInterfaces(Enemy.class));
        when(mob.getLocation()).thenAnswer(ignored -> new Location(world, x, y, z));
        return mob;
    }

    @Test
    void wardsAreSphericalExactlyTwentyFourBlocksAndOnlyNaturalHostileSpawns() {
        load(1, 0);
        load(2, 0);
        lantern(position(8, 8, 8), Material.LANTERN);
        var edge = hostile(32.5, 8, 8.5);
        assertTrue(engine.protects(edge, SpawnReason.NATURAL));
        assertFalse(engine.protects(hostile(32.51, 8, 8.5), SpawnReason.NATURAL));
        assertFalse(engine.protects(hostile(30, 20, 20), SpawnReason.NATURAL));
        for (var reason : SpawnReason.values()) {
            if (reason != SpawnReason.NATURAL) assertFalse(engine.protects(edge, reason), reason.name());
        }
        var animal = mock(LivingEntity.class);
        assertFalse(engine.protects(animal, SpawnReason.NATURAL));
    }

    @Test
    void obstaclesIncludingGlassBlockWardButPassablePlantsDoNotAndStaleSourcesAreIgnored() {
        var source = position(8, 8, 8);
        lantern(source, Material.LANTERN);
        var mob = hostile(13.5, 8, 8.5);
        cell(position(10, 8, 8)).data = data(Material.GLASS);
        assertFalse(engine.protects(mob, SpawnReason.NATURAL));
        cell(position(10, 8, 8)).data = data(Material.SHORT_GRASS);
        when(cell(position(10, 8, 8)).block.isPassable()).thenReturn(true);
        assertTrue(engine.protects(mob, SpawnReason.NATURAL));
        cell(source).data = data(Material.AIR);
        assertFalse(engine.protects(mob, SpawnReason.NATURAL));
    }

    @Test
    void loadingNeighborChunkRebuildsHaloWithoutLosingCrossChunkSharedLights() {
        var source = position(15, 8, 8);
        lantern(source, Material.LANTERN);
        var neighbor = load(1, 0);
        engine.tick();
        engine.tick();
        assertEquals(Material.LIGHT, cell(position(16, 8, 8)).data.getMaterial());
        engine.unload(neighbor);
        chunks.remove(new ChunkPosition(id, 1, 0));
        assertEquals(Material.LIGHT, cell(position(14, 8, 8)).data.getMaterial());
        verify(world, never()).getChunkAt(2, 0);
    }

    @Test
    void changedObstaclesRecomputeHaloAndPreserveUnownedLightBlocks() {
        var source = position(8, 8, 8);
        cell(position(7, 8, 8)).data = light(4, false);
        lantern(source, Material.LANTERN);
        assertEquals(4, ((Light) cell(position(7, 8, 8)).data).getLevel());
        for (int y = 5; y <= 11; y++) for (int z = 5; z <= 11; z++) cell(position(9, y, z)).data = data(Material.STONE);
        engine.changed(position(9, 8, 8));
        engine.tick();
        assertEquals(Material.AIR, cell(position(10, 8, 8)).data.getMaterial());
        assertEquals(Material.STONE, cell(position(9, 8, 8)).data.getMaterial());
    }

    @Test
    void waterExtinguishesSoulCandleAndBlueParticlesAreOnlyEmittedWhenLit() {
        var source = position(8, 8, 8);
        var candle = mock(org.bukkit.block.data.type.Candle.class);
        when(candle.getMaterial()).thenReturn(Material.CANDLE);
        when(candle.getCandles()).thenReturn(1);
        cell(source).data = candle;
        engine.candle(source, true);
        for (int i = 0; i < 10; i++) engine.tick();
        verify(world).spawnParticle(eq(org.bukkit.Particle.SOUL_FIRE_FLAME), any(Location.class),
                eq(1), eq(0.0), eq(.01), eq(0.0), eq(0.0));
        when(candle.isWaterlogged()).thenReturn(true);
        for (int i = 0; i < 10; i++) engine.tick();
        assertFalse(engine.isLit(source));
        engine.refresh(source);
        assertEquals(Material.AIR, cell(position(9, 8, 8)).data.getMaterial());
        assertFalse(records.candles(chunks.get(source.chunk())).get(source).lit());
    }

    @Test
    void candleIdentityIgnitionAndLightArePersistedAndDoNotCreateMonsterWard() {
        var source = position(8, 8, 8);
        var candle = mock(org.bukkit.block.data.type.Candle.class);
        when(candle.getMaterial()).thenReturn(Material.CANDLE);
        when(candle.getCandles()).thenReturn(3);
        cell(source).data = candle;
        engine.candle(source, true);
        engine.refresh(source);
        assertTrue(engine.isLit(source));
        assertEquals(8, ((Light) cell(position(9, 8, 8)).data).getLevel());
        assertFalse(engine.protects(hostile(10.5, 8, 8.5), SpawnReason.NATURAL));
        var chunk = chunks.get(source.chunk());
        assertTrue(records.candles(chunk).get(source).lit());
        assertTrue(engine.claimCandleDrops(source, 2));
        assertFalse(engine.claimCandleDrops(source, 2));
        assertTrue(engine.claimCandleDrops(source, 1));
        assertFalse(engine.claimCandleDrops(source, 1));
        engine.candle(source, false);
        engine.refresh(source);
        assertEquals(Material.AIR, cell(position(9, 8, 8)).data.getMaterial());
        engine = new LanternEngine(server, records);
        engine.load(chunk);
        assertTrue(engine.isSoul(source));
        assertFalse(engine.isLit(source));
    }
}
