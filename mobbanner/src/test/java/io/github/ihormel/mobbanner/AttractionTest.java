package io.github.ihormel.mobbanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;

class AttractionTest extends PaperTest {
    @Test
    void duplicatesOneAttemptAfterSuccessfulSpawnAndNeverStacks() {
        var world = server.addSimpleWorld("pyramids");
        Banner first = WardPyramidTest.build(world, 5, 70, 5);
        Banner second = WardPyramidTest.build(world, 11, 70, 11);
        items.write(first.getPersistentDataContainer(), EntityType.ENDERMAN);
        items.write(second.getPersistentDataContainer(), EntityType.ENDERMAN);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of(first, second));
        var boost = mock(SpawnBoost.class);
        List<Runnable> tasks = new ArrayList<>();
        var wards = new WardSpawns(items, tasks::add, boost);
        var event = event(chunk);
        var location = event.getLocation();
        // The deferred check resolves the live chunk, not the event's test double.
        WardSpawns live = spy(wards);
        doReturn(WardSpawns.Mode.ATTRACT).when(live).mode(any(Chunk.class), eq(EntityType.ENDERMAN));
        live.onSpawn(event);
        live.onAttract(event);
        verify(event, never()).setCancelled(true);
        assertEquals(1, tasks.size());
        tasks.getFirst().run();
        var captured = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(boost).attempt(eq(event.getEntity()), captured.capture());
        assertEquals(location.getWorld(), captured.getValue().getWorld());
        assertEquals(location.getX(), captured.getValue().getX());
        assertEquals(location.getZ(), captured.getValue().getZ());
    }

    @Test
    void suppressionWinsOverPoweredBannerRegardlessOfOrder() {
        var world = server.addSimpleWorld("pyramid");
        Banner powered = WardPyramidTest.build(world, 8, 70, 8);
        items.write(powered.getPersistentDataContainer(), EntityType.ENDERMAN);
        Banner plain = mock(Banner.class);
        when(plain.getType()).thenReturn(Material.PURPLE_WALL_BANNER);
        when(plain.getPersistentDataContainer()).thenReturn(server.addPlayer().getPersistentDataContainer());
        items.write(plain.getPersistentDataContainer(), EntityType.ENDERMAN);
        Chunk chunk = mock(Chunk.class);
        var wards = new WardSpawns(items, Runnable::run, mock(SpawnBoost.class));
        for (var states : List.of(List.of(powered, plain), List.of(plain, powered))) {
            when(chunk.getTileEntities(any(), eq(false))).thenReturn(new ArrayList<BlockState>(states));
            assertEquals(WardSpawns.Mode.SUPPRESS, wards.mode(chunk, EntityType.ENDERMAN));
        }
    }

    @Test
    void poweredBannerIsAttractorAndRemovingPyramidRestoresSuppression() {
        var world = server.addSimpleWorld("pyramid");
        Banner powered = WardPyramidTest.build(world, 8, 70, 8);
        items.write(powered.getPersistentDataContainer(), EntityType.ENDERMAN);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of(powered));
        var wards = new WardSpawns(items, Runnable::run, mock(SpawnBoost.class));
        assertEquals(WardSpawns.Mode.ATTRACT, wards.mode(chunk, EntityType.ENDERMAN));
        world.getBlockAt(8, 69, 8).setType(Material.AIR);
        assertEquals(WardSpawns.Mode.SUPPRESS, wards.mode(chunk, EntityType.ENDERMAN));
    }

    @Test
    void cancelledCustomAndReentrantSpawnsDoNotBoost() {
        var boost = mock(SpawnBoost.class);
        List<Runnable> tasks = new ArrayList<>();
        var wards = spy(new WardSpawns(items, tasks::add, boost));
        doReturn(WardSpawns.Mode.ATTRACT).when(wards).mode(any(), any());
        var event = event(mock(Chunk.class));
        when(event.getSpawnReason()).thenReturn(SpawnReason.CUSTOM);
        wards.onAttract(event);
        assertTrue(tasks.isEmpty());
        when(event.getSpawnReason()).thenReturn(SpawnReason.NATURAL);
        when(boost.isSpawning()).thenReturn(true);
        wards.onAttract(event);
        assertTrue(tasks.isEmpty());
        when(boost.isSpawning()).thenReturn(false);
        wards.onAttract(event);
        when(event.isCancelled()).thenReturn(true);
        tasks.getFirst().run();
        verify(boost, never()).attempt(any(), any());
    }

    @Test
    void deferredAttemptRechecksWardModeBeforeSpawning() {
        var boost = mock(SpawnBoost.class);
        List<Runnable> tasks = new ArrayList<>();
        var wards = spy(new WardSpawns(items, tasks::add, boost));
        doReturn(WardSpawns.Mode.ATTRACT).when(wards).mode(any(), any());
        var event = event(mock(Chunk.class));
        wards.onAttract(event);
        assertEquals(1, tasks.size());
        doReturn(WardSpawns.Mode.SUPPRESS).when(wards).mode(any(), any());
        tasks.getFirst().run();
        verify(boost, never()).attempt(any(), any());
    }

    private CreatureSpawnEvent event(Chunk chunk) {
        var world = server.addSimpleWorld("spawn");
        world.loadChunk(0, 0);
        Location location = spy(new Location(world, 8, 64, 8));
        when(location.getChunk()).thenReturn(chunk);
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.isValid()).thenReturn(true);
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        when(event.getLocation()).thenReturn(location);
        when(event.getEntity()).thenReturn(entity);
        when(event.getEntityType()).thenReturn(EntityType.ENDERMAN);
        when(event.getSpawnReason()).thenReturn(SpawnReason.NATURAL);
        return event;
    }
}
