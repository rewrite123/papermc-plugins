package io.github.ihormel.mobbanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpawnBoostTest extends PaperTest {
    private World world;
    private LivingEntity source;
    private Location origin;
    private Block air;
    private Block floor;

    @BeforeEach
    void setUpWorld() {
        world = mock(World.class);
        var border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        air = mock(Block.class);
        floor = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(floor.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                (int) call.getArgument(1) == 63 ? floor : air);
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location location = call.getArgument(0);
            return world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        });
        when(world.getPlayers()).thenReturn(List.of());
        when(world.getNearbyEntities(any(BoundingBox.class), any())).thenReturn(List.of());
        origin = new Location(world, 8.5, 64, 8.5);
        source = mock(LivingEntity.class);
        when(source.getType()).thenReturn(EntityType.ENDERMAN);
        when(source.getLocation()).thenReturn(origin);
        when(source.getBoundingBox()).thenReturn(new BoundingBox(8.2, 64, 8.2, 8.8, 66.9, 8.8));
    }

    @Test
    void acceptsClearGroundedSpaceWithThreeBlockEndermanHeadroom() {
        assertTrue(new SpawnBoost().safe(source, origin, origin.clone().add(2, 0, 0)));
        when(world.getBlockAt(10, 66, 8)).thenReturn(floor);
        assertFalse(new SpawnBoost().safe(source, origin, origin.clone().add(2, 0, 0)));
    }

    @Test
    void refusesNeighborChunksPlayerProximityAndCollisions() {
        var boost = new SpawnBoost();
        assertFalse(boost.safe(source, origin, origin.clone().add(8, 0, 0)));
        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(origin);
        when(world.getPlayers()).thenReturn(List.of(player));
        assertFalse(boost.safe(source, origin, origin.clone().add(2, 0, 0)));
        when(world.getPlayers()).thenReturn(List.of());
        when(world.getNearbyEntities(any(BoundingBox.class), any())).thenReturn(List.of(source));
        assertFalse(boost.safe(source, origin, origin.clone().add(2, 0, 0)));
    }

    @Test
    void requiresWaterForAquaticMobsAndSolidGroundForWalkingMobs() {
        var boost = new SpawnBoost();
        when(world.getBlockAt(10, 63, 8)).thenReturn(air);
        assertFalse(boost.safe(source, origin, origin.clone().add(2, 0, 0)));
        when(source.getType()).thenReturn(EntityType.BAT);
        assertTrue(boost.safe(source, origin, origin.clone().add(2, 0, 0)));
        when(source.getType()).thenReturn(EntityType.GUARDIAN);
        assertFalse(boost.safe(source, origin, origin.clone().add(2, 0, 0)));
        when(air.getType()).thenReturn(Material.WATER);
        assertTrue(boost.safe(source, origin, origin.clone().add(2, 0, 0)));
    }

    @Test
    void singleAttemptUsesCustomReasonAndResetsRecursionGuardEvenOnFailure() {
        var boost = new SpawnBoost(() -> 2);
        Location expected = origin.clone().add(2, 0, 2);
        when(world.spawnEntity(expected, EntityType.ENDERMAN, SpawnReason.CUSTOM)).thenAnswer(call -> {
            assertTrue(boost.isSpawning());
            return source;
        });
        boost.attempt(source, origin);
        verify(world).spawnEntity(expected, EntityType.ENDERMAN, SpawnReason.CUSTOM);
        assertFalse(boost.isSpawning());
        doThrow(new IllegalArgumentException("test spawn failure"))
                .when(world).spawnEntity(expected, EntityType.ENDERMAN, SpawnReason.CUSTOM);
        assertThrows(IllegalArgumentException.class, () -> boost.attempt(source, origin));
        assertFalse(boost.isSpawning());
    }

    @Test
    void unsafeCandidateNeverCallsSpawn() {
        var boost = new SpawnBoost(() -> 2);
        when(air.getType()).thenReturn(Material.STONE);
        boost.attempt(source, origin);
        verify(world, never()).spawnEntity(any(), any(), any(SpawnReason.class));
    }
}
