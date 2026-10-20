package io.github.ihormel.reduceendermanworlddmg;

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
import org.bukkit.block.BlockFace;
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

    private final java.util.Map<String, Block> cells = new java.util.HashMap<>();

    /** A world with solid ground at y <= 69 and air above it. */
    private Block cell(int x, int y, int z) {
        return cells.computeIfAbsent(x + "," + y + "," + z, key -> {
            Block cell = mock(Block.class);
            boolean ground = y <= 69;
            when(cell.getWorld()).thenReturn(world);
            when(cell.getX()).thenReturn(x);
            when(cell.getY()).thenReturn(y);
            when(cell.getZ()).thenReturn(z);
            when(cell.getLocation()).thenAnswer(i -> new Location(world, x, y, z));
            when(cell.isEmpty()).thenReturn(!ground);
            when(cell.isSolid()).thenReturn(ground);
            when(cell.canPlace(any())).thenReturn(true);
            when(cell.getRelative(BlockFace.DOWN)).thenAnswer(i -> cell(x, y - 1, z));
            return cell;
        });
    }

    @BeforeEach
    void setup() {
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(
                i -> cell(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        when(block.getRelative(BlockFace.DOWN)).thenAnswer(i -> cell(4, 69, 8));
        when(block.canPlace(any())).thenReturn(true);
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
        when(entity.getEyeLocation()).thenAnswer(i -> entity.getLocation().clone().add(0, 2.55, 0));
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
        verify(world, never()).getBlockAt(4, 70, 8);
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
        verify(path, never()).moveTo(any(Location.class), anyDouble());
        when(entity.hasAI()).thenReturn(true);
        when(entity.hasLineOfSight(any(Location.class))).thenReturn(false);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        verify(path).moveTo(any(Location.class), eq(1.0));
    }

    @Test
    void placesIntoAGapInAWallWhoseCenterIsHiddenFromItsEyes() {
        time.set(11_000);
        when(entity.getLocation()).thenReturn(new Location(world, 5.3, 70, 8.5));
        // Only the open +x face of the gap is visible; the ray to the center clips the block above.
        when(entity.hasLineOfSight(any(Location.class))).thenAnswer(i -> {
            Location target = i.getArgument(0);
            return target.getX() > 4.85;
        });
        returns.tick();
        verify(block).setBlockData(data, true);
    }

    @Test
    void reachIsMeasuredFromTheEyesSoHighGapsDoNotStrandIt() {
        time.set(11_000);
        when(entity.getLocation()).thenReturn(new Location(world, 5.5, 68, 8.5));
        returns.tick();
        verify(path, never()).moveTo(any(Location.class), anyDouble());
        verify(block).setBlockData(data, true);
    }

    @Test
    void stepsAwayWhenStandingInTheGap() {
        time.set(11_000);
        when(entity.getLocation()).thenReturn(new Location(world, 4.6, 70, 8.5));
        returns.tick();
        verify(path).moveTo(ArgumentMatchers.<Location>argThat(l -> l.getX() > 6), eq(1.0));
    }

    private void hideOriginalGap() {
        when(entity.hasLineOfSight(any(Location.class))).thenAnswer(i -> {
            Location target = i.getArgument(0);
            return !(target.getX() >= 4 && target.getX() <= 5 && target.getY() >= 70 && target.getY() <= 71
                    && target.getZ() >= 8 && target.getZ() <= 9);
        });
    }

    @Test
    void keepsTryingForTenSecondsThenPlacesNearestNearbySpot() {
        hideOriginalGap();
        when(entity.getLocation()).thenReturn(new Location(world, 6.5, 70, 8.5));
        time.set(11_000);
        returns.tick();
        time.set(20_999);
        returns.tick();
        verify(path, times(2)).moveTo(any(Location.class), eq(1.0));
        assertTrue(returns.tracked(entity));
        time.set(21_000);
        returns.tick();
        verify(block, never()).setBlockData(any(), anyBoolean());
        // One of the spots touching the original gap, which is as close to it as possible.
        verify(cell(4, 70, 7)).setBlockData(data, true);
        verify(entity).setCarriedBlock(null);
        assertFalse(returns.tracked(entity));
    }

    @Test
    void stillReturnsToTheOriginalSpotAfterTheWindowWhenPossible() {
        when(entity.getLocation()).thenReturn(new Location(world, 30, 70, 8));
        time.set(11_000);
        returns.tick();
        time.set(21_000);
        when(entity.getLocation()).thenReturn(new Location(world, 5.5, 70, 8.5));
        returns.tick();
        verify(block).setBlockData(data, true);
        verify(cell(5, 70, 8), never()).setBlockData(any(), anyBoolean());
    }

    @Test
    void fallbackSkipsUnsupportedOccupiedAndProtectedSpots() {
        hideOriginalGap();
        when(entity.getLocation()).thenReturn(new Location(world, 6.5, 70, 8.5));
        when(cell(4, 70, 7).canPlace(data)).thenReturn(false);
        when(world.getNearbyEntities(BoundingBox.of(cell(4, 70, 9)))).thenReturn(java.util.List.of(mock(Player.class)));
        event = e -> {
            if (e.getBlock() == cell(5, 70, 8)) e.setCancelled(true);
        };
        time.set(11_000);
        returns.tick();
        time.set(21_000);
        returns.tick();
        verify(cell(4, 70, 7), never()).setBlockData(any(), anyBoolean());
        verify(cell(4, 70, 9), never()).setBlockData(any(), anyBoolean());
        verify(cell(5, 70, 8), never()).setBlockData(any(), anyBoolean());
        verify(cell(5, 70, 7)).setBlockData(data, true);
        assertFalse(returns.tracked(entity));
        assertNull(carried.get());
    }

    @Test
    void waitsWithoutAnyNearbySpotAndNeverReplacesBlocks() {
        hideOriginalGap();
        when(entity.getLocation()).thenReturn(new Location(world, 6.5, 70, 8.5));
        for (int x = 4; x <= 8; x++) {
            for (int z = 6; z <= 10; z++) {
                for (int y = 70; y <= 72; y++) when(cell(x, y, z).canPlace(data)).thenReturn(false);
            }
        }
        time.set(11_000);
        returns.tick();
        time.set(21_000);
        returns.tick();
        assertTrue(returns.tracked(entity));
        assertSame(data, carried.get());
        for (Block any : cells.values()) verify(any, never()).setBlockData(any(), anyBoolean());
        when(cell(7, 70, 10).canPlace(data)).thenReturn(true);
        returns.tick();
        verify(cell(7, 70, 10)).setBlockData(data, true);
    }

    @Test
    void unloadedOriginChunkFallsBackNearbyAfterTheWindow() {
        hideOriginalGap();
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        when(entity.getLocation()).thenReturn(new Location(world, 6.5, 70, 8.5));
        time.set(11_000);
        returns.tick();
        verify(path, never()).moveTo(any(Location.class), anyDouble());
        time.set(21_000);
        returns.tick();
        verify(cell(4, 70, 7)).setBlockData(data, true);
    }

    @Test
    void migratesRecordsWrittenUnderTheOldPluginName() {
        String legacy = stored.get();
        stored.set(null);
        when(pdc.get(BlockReturns.LEGACY_ORIGIN, PersistentDataType.STRING)).thenReturn(legacy);
        when(pdc.has(BlockReturns.LEGACY_ORIGIN, PersistentDataType.STRING)).thenReturn(true);
        assertTrue(returns.tracked(entity));
        returns = controller(10_000);
        returns.load(entity);
        assertEquals(legacy, stored.get());
        verify(pdc).remove(BlockReturns.LEGACY_ORIGIN);
        time.set(11_000);
        returns.tick();
        verify(block).setBlockData(data, true);
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
