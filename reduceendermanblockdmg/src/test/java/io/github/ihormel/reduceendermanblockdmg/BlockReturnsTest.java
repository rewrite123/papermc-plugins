package io.github.ihormel.reduceendermanblockdmg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.entity.Pathfinder;
import java.util.UUID;
import org.mockito.ArgumentMatchers;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockReturnsTest {
    private final AtomicLong time = new AtomicLong(1000);
    private final AtomicBoolean griefing = new AtomicBoolean(true);
    private final AtomicReference<String> stored = new AtomicReference<>();
    private final AtomicReference<BlockData> carried = new AtomicReference<>();
    private final World world = mock(World.class);
    private final Enderman entity = mock(Enderman.class);
    private final Block block = mock(Block.class);
    private final BlockData data = mock(BlockData.class);
    private final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
    private final Pathfinder path = mock(Pathfinder.class);
    private java.util.function.Consumer<EntityChangeBlockEvent> event = ignored -> {};
    private BlockReturns returns;

    @BeforeEach
    void setup() {
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(4, 70, 8)).thenReturn(block);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(4);
        when(block.getY()).thenReturn(70);
        when(block.getZ()).thenReturn(8);
        when(block.getLocation()).thenAnswer(i -> new Location(world, 4, 70, 8));
        when(block.isEmpty()).thenReturn(true);
        when(data.getAsString()).thenReturn("minecraft:dirt");
        when(data.getMaterial()).thenReturn(Material.DIRT);
        when(data.clone()).thenReturn(data);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getWorld()).thenReturn(world);
        when(entity.isValid()).thenReturn(true);
        when(entity.hasAI()).thenReturn(true);
        when(entity.getLocation()).thenReturn(new Location(world, 5, 70, 8));
        when(entity.getBoundingBox()).thenAnswer(i -> {
            Location at = entity.getLocation();
            return new BoundingBox(at.getX() - 0.3, at.getY(), at.getZ() - 0.3,
                    at.getX() + 0.3, at.getY() + 2.9, at.getZ() + 0.3);
        });
        // Mirrors vanilla ray clipping: a ray ending on the gap's floor hits the block beneath it.
        when(entity.hasLineOfSight(any(Location.class))).thenAnswer(i -> {
            Location target = i.getArgument(0);
            return target.getY() > 70 && target.getY() < 71;
        });
        when(entity.getPathfinder()).thenReturn(path);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        carried.set(data);
        when(entity.getCarriedBlock()).thenAnswer(i -> carried.get());
        doAnswer(i -> { carried.set(i.getArgument(0)); return null; }).when(entity).setCarriedBlock(any());
        doAnswer(i -> { stored.set(i.getArgument(2)); return null; }).when(pdc)
                .set(eq(BlockReturns.ORIGIN), eq(PersistentDataType.STRING), anyString());
        when(pdc.get(BlockReturns.ORIGIN, PersistentDataType.STRING)).thenAnswer(i -> stored.get());
        when(pdc.has(BlockReturns.ORIGIN, PersistentDataType.STRING)).thenAnswer(i -> stored.get() != null);
        doAnswer(i -> { stored.set(null); return null; }).when(pdc).remove(BlockReturns.ORIGIN);
        returns = controller(10_000);
        returns.pickedUp(entity, block, data);
    }

    private BlockReturns controller(long delay) {
        return new BlockReturns(time::get, () -> delay, e -> event.accept(e),
                id -> id.equals(world.getUID()) ? world : null, value -> {
                    if (!value.equals("minecraft:dirt")) throw new IllegalArgumentException("Invalid block data");
                    return data;
                }, ignored -> {}, ignored -> griefing.get());
    }

    @Test
    void waitsForSelectedDelayThenReturnsExactlyOneBlock() {
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        time.set(10_999);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        time.set(11_000);
        returns.tick();
        verify(block).setBlockData(data, true);
        verify(entity).setCarriedBlock(null);
        assertFalse(returns.tracked(entity));
        returns.tick();
        verify(block, times(1)).setBlockData(data, true);
    }

    @Test
    void supportsLongestDelay() {
        returns = controller(45_000);
        returns.pickedUp(entity, block, data);
        time.set(45_999);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        time.set(46_000);
        returns.tick();
        verify(block).setBlockData(data, true);
    }

    @Test
    void pathsBackInsteadOfRestoringFromFarAway() {
        time.set(11_000);
        when(entity.getLocation()).thenReturn(new Location(world, 30, 70, 8));
        returns.tick();
        verify(path).moveTo(any(Location.class), eq(1.0));
        verify(block, never()).setBlockData(any(), anyBoolean());
        assertTrue(returns.tracked(entity));
    }

    @Test
    void checksLineOfSightToBlockCenterAndStopsWalkingWhenInRange() {
        time.set(11_000);
        when(entity.getLocation()).thenReturn(new Location(world, 5.5, 71, 8.5));
        returns.tick();
        verify(entity).hasLineOfSight(ArgumentMatchers.<Location>argThat(l -> l.getX() == 4.5 && l.getY() == 70.5 && l.getZ() == 8.5));
        verify(path).stopPathfinding();
        verify(path, never()).moveTo(any(Location.class), anyDouble());
        verify(block).setBlockData(data, true);
    }

    @Test
    void waitsWhileStandingInsideTheGap() {
        time.set(11_000);
        when(entity.getLocation()).thenReturn(new Location(world, 4.5, 70, 8.5));
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        assertTrue(returns.tracked(entity));
        when(entity.getLocation()).thenReturn(new Location(world, 5.3, 70, 8.5));
        returns.tick();
        verify(block).setBlockData(data, true);
    }

    @Test
    void waitsIfOriginalSpaceOccupiedThenRetries() {
        time.set(11_000);
        when(block.isEmpty()).thenReturn(false);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        when(block.isEmpty()).thenReturn(true);
        returns.tick();
        verify(block).setBlockData(data, true);
    }

    @Test
    void neverRestoresAfterDeathOrLosingCarriedBlock() {
        time.set(11_000);
        when(entity.isDead()).thenReturn(true);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        assertNull(stored.get());
        verify(entity, never()).setCarriedBlock(any());
        when(entity.isDead()).thenReturn(false);
        returns.pickedUp(entity, block, data);
        carried.set(null);
        returns.tick();
        assertNull(stored.get());
        verify(block, never()).setBlockData(any(), anyBoolean());
    }

    @Test
    void doesNotForceLoadChunks() {
        time.set(11_000);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        returns.tick();
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertTrue(returns.tracked(entity));
    }

    @Test
    void respectsMobGriefingCombatAiAndLineOfSight() {
        time.set(11_000);
        griefing.set(false);
        returns.tick();
        griefing.set(true);
        when(entity.getTarget()).thenReturn(mock(Player.class));
        returns.tick();
        when(entity.getTarget()).thenReturn(null);
        when(entity.hasAI()).thenReturn(false);
        returns.tick();
        when(entity.hasAI()).thenReturn(true);
        when(entity.hasLineOfSight(any(Location.class))).thenReturn(false);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        verify(path, never()).moveTo(any(Location.class), anyDouble());
    }

    @Test
    void protectionPluginsCanCancelReturnAndEventIdentityIsScoped() {
        time.set(11_000);
        event = e -> {
            assertTrue(returns.ownPlacement(e));
            e.setCancelled(true);
        };
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        assertTrue(returns.tracked(entity));
        EntityChangeBlockEvent other = new EntityChangeBlockEvent(entity, block, data);
        assertFalse(returns.ownPlacement(other));
    }

    @Test
    void rechecksDestinationAfterProtectionEvent() {
        time.set(11_000);
        event = e -> when(block.isEmpty()).thenReturn(false);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        verify(entity, never()).setCarriedBlock(any());
    }

    @Test
    void returnsOriginalStateEvenWhenVanillaCarriesDefaultState() {
        BlockData defaultState = mock(BlockData.class);
        when(defaultState.getMaterial()).thenReturn(Material.DIRT);
        carried.set(defaultState);
        time.set(11_000);
        returns.tick();
        verify(block).setBlockData(data, true);
    }

    @Test
    void differentCarriedMaterialAbandonsReturnWithoutRestoration() {
        BlockData replacement = mock(BlockData.class);
        when(replacement.getMaterial()).thenReturn(Material.SAND);
        carried.set(replacement);
        time.set(11_000);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        assertNull(stored.get());
    }

    @Test
    void teleportDuringProtectionEventCannotRestoreRemotely() {
        time.set(11_000);
        event = e -> when(entity.getLocation()).thenReturn(new Location(world, 100, 70, 8));
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
    }

    @Test
    void missingWorldOrDifferentDimensionDoesNotRestore() {
        stored.set(new ReturnOrigin(UUID.randomUUID(), 4, 70, 8, "minecraft:dirt", 1000).encode());
        returns.load(entity);
        time.set(11_000);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        returns.pickedUp(entity, block, data);
        when(entity.getWorld()).thenReturn(mock(World.class));
        time.set(21_000);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
    }

    @Test
    void invalidEntityDropsMemoryOnlyUntilItIsLoadedAgain() {
        when(entity.isValid()).thenReturn(false);
        returns.tick();
        assertNotNull(stored.get());
        when(entity.isValid()).thenReturn(true);
        time.set(11_000);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        returns.load(entity);
        returns.tick();
        verify(block).setBlockData(data, true);
    }

    @Test
    void unloadAndRestartPreserveDeadlineAndOrigin() {
        String saved = stored.get();
        returns.unload(entity);
        time.set(60_000);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        assertEquals(saved, stored.get());
        returns = controller(45_000);
        returns.load(entity);
        returns.tick();
        verify(block).setBlockData(data, true);
    }

    @Test
    void invalidPersistedDataDoesNotDestroyCarriedBlock() {
        stored.set("invalid");
        returns.load(entity);
        assertNull(stored.get());
        assertSame(data, carried.get());
    }

    @Test
    void originRoundTripsNegativeCoordinatesAndBlockProperties() {
        ReturnOrigin origin = new ReturnOrigin(UUID.randomUUID(), -17, -50, 32,
                "minecraft:grass_block[snowy=false]", 99_999);
        assertEquals(origin, ReturnOrigin.decode(origin.encode()));
        assertThrows(IllegalArgumentException.class, () -> ReturnOrigin.decode("bad"));
    }
}
