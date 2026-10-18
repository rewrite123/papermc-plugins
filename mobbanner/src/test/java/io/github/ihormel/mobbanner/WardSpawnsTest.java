package io.github.ihormel.mobbanner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;

class WardSpawnsTest extends PaperTest {
    @Test
    void onlyMatchingMobInSameChunkIsBlockedForSelectedReasons() {
        Chunk chunk = mock(Chunk.class);
        Banner zombie = banner(EntityType.ZOMBIE);
        Banner bat = banner(EntityType.BAT);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of(zombie, bat));
        WardSpawns wards = new WardSpawns(items, Runnable::run, new SpawnBoost());
        for (SpawnReason reason : SpawnReason.values()) {
            CreatureSpawnEvent event = spawn(chunk, EntityType.BAT, reason);
            wards.onSpawn(event);
            if (MobTypes.blocks(reason)) verify(event).setCancelled(true);
            else verify(event, never()).setCancelled(true);
        }
        var husk = spawn(chunk, EntityType.HUSK, SpawnReason.NATURAL);
        wards.onSpawn(husk);
        verify(husk, never()).setCancelled(true);
        Chunk neighbor = mock(Chunk.class);
        when(neighbor.getTileEntities(any(), eq(false))).thenReturn(List.of());
        var nextChunk = spawn(neighbor, EntityType.BAT, SpawnReason.NATURAL);
        wards.onSpawn(nextChunk);
        verify(nextChunk, never()).setCancelled(true);
    }

    @Test
    void multipleWardsRemainActiveUntilLastOneRemovedWithoutStaleCache() {
        Chunk chunk = mock(Chunk.class);
        List<BlockState> states = new ArrayList<>(List.of(banner(EntityType.BAT), banner(EntityType.BAT)));
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(states);
        WardSpawns wards = new WardSpawns(items, Runnable::run, new SpawnBoost());
        var first = spawn(chunk, EntityType.BAT, SpawnReason.NATURAL);
        wards.onSpawn(first);
        verify(first).setCancelled(true);
        states.removeFirst();
        var second = spawn(chunk, EntityType.BAT, SpawnReason.NATURAL);
        wards.onSpawn(second);
        verify(second).setCancelled(true);
        states.clear();
        var last = spawn(chunk, EntityType.BAT, SpawnReason.NATURAL);
        wards.onSpawn(last);
        verify(last, never()).setCancelled(true);
    }

    private Banner banner(EntityType type) {
        Banner banner = mock(Banner.class);
        when(banner.getType()).thenReturn(org.bukkit.Material.WHITE_WALL_BANNER);
        when(banner.getPersistentDataContainer()).thenReturn(
                server.addPlayer().getPersistentDataContainer());
        items.write(banner.getPersistentDataContainer(), type);
        return banner;
    }

    private CreatureSpawnEvent spawn(Chunk chunk, EntityType type, SpawnReason reason) {
        Location location = mock(Location.class);
        when(location.getChunk()).thenReturn(chunk);
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        when(event.getEntityType()).thenReturn(type);
        when(event.getSpawnReason()).thenReturn(reason);
        when(event.getLocation()).thenReturn(location);
        return event;
    }
}
