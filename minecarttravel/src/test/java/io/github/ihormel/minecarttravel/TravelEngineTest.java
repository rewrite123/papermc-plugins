package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.persistence.PersistentDataContainerMock;

class TravelEngineTest extends PaperTest {
    private JavaPlugin plugin;
    private RailGraph graph;
    private TravelEngine engine;
    private World world;
    private RideableMinecart cart;
    private Player player;
    private Location location;
    private Vector velocity;
    private double maxSpeed;
    private final List<Chunk> tickets = new ArrayList<>();
    private VehicleUpdateEvent update;

    @BeforeEach
    void setUpRide() {
        plugin = MockBukkit.createMockPlugin("minecarttravel");
        graph = new RailGraph();
        var real = server.addSimpleWorld("world");
        for (int x = 0; x < 64; x++) {
            real.getBlockAt(x, 63, 8).setType(Material.STONE);
            var block = real.getBlockAt(x, 64, 8);
            block.setType(Material.RAIL);
            var rail = (Rail) block.getBlockData();
            rail.setShape(Rail.Shape.EAST_WEST);
            block.setBlockData(rail);
            graph.observe(new TrackPos(real.getUID(), x, 64, 8), RailShape.EAST_WEST, false);
        }
        real.getBlockAt(-1, 64, 8).setType(Material.LODESTONE);
        real.getBlockAt(64, 64, 8).setType(Material.LODESTONE);
        graph.observe(new TrackPos(real.getUID(), -1, 64, 8), null, true);
        graph.observe(new TrackPos(real.getUID(), 64, 64, 8), null, true);
        world = TestWorld.withCollisionStubs(real);
        doReturn(true).when(world).isChunkLoaded(anyInt(), anyInt());
        doReturn(List.of()).when(world).getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());
        doAnswer(call -> {
            var chunk = mock(Chunk.class);
            when(chunk.getWorld()).thenReturn(world);
            when(chunk.getX()).thenReturn(call.getArgument(0));
            when(chunk.getZ()).thenReturn(call.getArgument(1));
            tickets.add(chunk);
            return CompletableFuture.completedFuture(chunk);
        }).when(world).getChunkAtAsync(anyInt(), anyInt(), eq(false));
        engine = new TravelEngine(plugin, graph, settings(3, 8));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getPersistentDataContainer()).thenReturn(new PersistentDataContainerMock());
        when(player.hasPermission("minecarttravel.use")).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenAnswer(unused -> location.clone());
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        cart = mock(RideableMinecart.class);
        when(cart.getUniqueId()).thenReturn(UUID.randomUUID());
        when(cart.isValid()).thenReturn(true);
        when(cart.getWorld()).thenReturn(world);
        when(cart.getPassengers()).thenReturn(List.of(player));
        when(cart.getPersistentDataContainer()).thenReturn(new PersistentDataContainerMock());
        when(player.getVehicle()).thenReturn(cart);
        location = new Location(world, 4.5, 64.1, 8.5);
        velocity = new Vector(0.4, 0, 0);
        maxSpeed = 0.4;
        when(cart.getLocation()).thenAnswer(unused -> location.clone());
        when(cart.getVelocity()).thenAnswer(unused -> velocity.clone());
        when(cart.getMaxSpeed()).thenAnswer(unused -> maxSpeed);
        doAnswer(call -> { velocity = call.getArgument(0); return null; }).when(cart).setVelocity(any(Vector.class));
        doAnswer(call -> { maxSpeed = call.getArgument(0); return null; }).when(cart).setMaxSpeed(anyDouble());
        when(cart.teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenAnswer(call -> {
            location = call.getArgument(0);
            return true;
        });
        update = new VehicleUpdateEvent(cart);
    }

    private TravelSettings settings(int boost, int fast) {
        return new TravelSettings(boost, fast, 6, 90, 12, 65536, 32, 4096, 512, 600);
    }

    private void ride(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            engine.tick();
            location.add(0.4, 0, 0);
            engine.update(update);
        }
    }

    @Test
    void boostStartsOnlyAfterTheFullThresholdAndSetsNinetyBlocksPerSecond() {
        ride(3);
        assertEquals(0.4, maxSpeed);
        ride(1);
        assertEquals(4.5, maxSpeed);
        assertEquals(90, velocity.length() * 20);
    }

    @Test
    void defaultModeIsFastAndOptOutPersistsInPlayerData() {
        assertFalse(engine.normal(player));
        ride(4);
        engine.mode(player, true);
        assertTrue(engine.normal(player));
        assertEquals(0.4, maxSpeed);
        ride(10);
        assertEquals(0.4, maxSpeed);
        var restarted = new TravelEngine(plugin, graph, settings(3, 8));
        assertTrue(restarted.normal(player));
        restarted.mode(player, false);
        assertFalse(engine.normal(player));
    }

    @Test
    void stoppingResetsEarnedTimeAndRestoresOriginalSpeed() {
        ride(4);
        assertEquals(4.5, maxSpeed);
        engine.tick();
        engine.update(update);
        assertEquals(0.4, maxSpeed);
        assertTrue(engine.status(player).contains("0s"));
    }

    @Test
    void gapInLookaheadPreventsBoostButDoesNotRequireARegisteredRoute() {
        world.getBlockAt(15, 64, 8).setType(Material.AIR);
        ride(4);
        assertEquals(0.4, maxSpeed);
        world.getBlockAt(15, 64, 8).setType(Material.RAIL);
        var rail = (Rail) world.getBlockAt(15, 64, 8).getBlockData();
        rail.setShape(Rail.Shape.EAST_WEST);
        world.getBlockAt(15, 64, 8).setBlockData(rail);
        graph.observe(new TrackPos(world.getUID(), -1, 64, 8), null, false);
        ride(1);
        assertEquals(4.5, maxSpeed);
    }

    private void prepareArrival() {
        ride(9);
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
        engine.tick();
        assertEquals(9, tickets.size());
        assertEquals(0, velocity.length());
        server.getScheduler().performOneTick();
    }

    @Test
    void endpointTravelKeepsTheSameCartAndRiderAndReleasesAllArrivalTickets() {
        prepareArrival();
        engine.tick();
        verify(cart).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
        assertEquals(63.5, location.getX());
        assertEquals(List.of(player), cart.getPassengers());
        verify(cart, never()).eject();
        verify(cart, never()).removePassenger(any());
        verify(cart, never()).remove();
        assertEquals(0, velocity.length());
        assertEquals(0.4, maxSpeed);
        for (var chunk : tickets) {
            verify(chunk).addPluginChunkTicket(plugin);
            verify(chunk).removePluginChunkTicket(plugin);
        }
        verify(world, times(9)).getChunkAtAsync(anyInt(), anyInt(), eq(false));
        verify(world, never()).loadChunk(anyInt(), anyInt());
    }

    @Test
    void reversedTravelSelectsTheOppositeEndpoint() {
        ride(9);
        velocity = new Vector(-4.5, 0, 0);
        engine.tick();
        server.getScheduler().performOneTick();
        engine.tick();
        assertEquals(0.5, location.getX());
    }

    @Test
    void brokenRailDuringPreparationCancelsTeleportAndReleasesTickets() {
        prepareArrival();
        graph.observe(new TrackPos(world.getUID(), 40, 64, 8), null, false);
        engine.tick();
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
        for (var chunk : tickets) verify(chunk).removePluginChunkTicket(plugin);
    }

    @Test
    void unsafeArrivalIsRejectedWithoutEjectingTheRider() {
        prepareArrival();
        world.getBlockAt(63, 65, 8).setType(Material.STONE);
        engine.tick();
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
        verify(cart, never()).eject();
        for (var chunk : tickets) verify(chunk).removePluginChunkTicket(plugin);
    }

    @Test
    void serverCancelledTeleportDoesNotReportSuccess() {
        doReturn(false).when(cart).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
        prepareArrival();
        engine.tick();
        verify(player, never()).sendMessage("Fast travel complete! Your cart has arrived at the lodestone.");
        verify(player).sendMessage("Fast travel was cancelled by the server or another plugin.");
        for (var chunk : tickets) verify(chunk).removePluginChunkTicket(plugin);
    }

    @Test
    void dismountAndShutdownRestoreSpeedAndDiscardProgress() {
        ride(4);
        engine.exit(new VehicleExitEvent(cart, player));
        assertEquals(0.4, maxSpeed);
        ride(4);
        assertEquals(4.5, maxSpeed);
        engine.close();
        assertEquals(0.4, maxSpeed);
    }

    @Test
    void externalTeleportResetsTheRide() {
        ride(4);
        engine.externalTeleport(new EntityTeleportEvent(cart, location.clone(), location.clone().add(100, 0, 0)));
        assertEquals(0.4, maxSpeed);
    }

    @Test
    void cargoAndEmptyCartsCanBoostButDoNotTeleport() {
        when(cart.getPassengers()).thenReturn(List.of());
        ride(15);
        assertEquals(4.5, maxSpeed);
        assertTrue(tickets.isEmpty());
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    @Test
    void interruptedBoostRestoresItsPersistedOriginalSpeedOnNextUpdate() {
        var key = new NamespacedKey(plugin, "original_speed");
        cart.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, 0.3);
        maxSpeed = 4.5;
        engine.update(update);
        assertEquals(0.3, maxSpeed);
        assertFalse(cart.getPersistentDataContainer().has(key));
    }

    @Test
    void permissionLossAndNormalModePreventFastTravel() {
        when(player.hasPermission("minecarttravel.use")).thenReturn(false);
        ride(15);
        assertEquals(0.4, maxSpeed);
        assertTrue(tickets.isEmpty());
    }

    @Test
    void reconciliationKeepsTheCartStoppedUntilTheArrivalAreaIsReady() {
        prepareArrival();
        var key = new TrackPos.ChunkKey(world.getUID(), 3, 0);
        graph.dirty.add(key);
        engine.tick();
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
        graph.dirty.remove(key);
        engine.tick();
        verify(cart).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    @Test
    void lateChunkCallbacksCannotResurrectACancelledRide() {
        var futures = new ArrayList<CompletableFuture<Chunk>>();
        doAnswer(call -> {
            var future = new CompletableFuture<Chunk>();
            futures.add(future);
            return future;
        }).when(world).getChunkAtAsync(anyInt(), anyInt(), eq(false));
        ride(9);
        engine.tick();
        assertEquals(9, futures.size());
        engine.close();
        var chunk = mock(Chunk.class);
        futures.forEach(future -> future.complete(chunk));
        server.getScheduler().performOneTick();
        verify(chunk, never()).addPluginChunkTicket(plugin);
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    @Test
    void disconnectDuringPreparationReleasesTickets() {
        prepareArrival();
        when(player.isOnline()).thenReturn(false);
        engine.tick();
        for (var chunk : tickets) verify(chunk).removePluginChunkTicket(plugin);
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    @Test
    void sourceMovementDuringPreparationCancelsTravel() {
        prepareArrival();
        location.add(2, 0, 0);
        engine.tick();
        for (var chunk : tickets) verify(chunk).removePluginChunkTicket(plugin);
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    @Test
    void incompleteDestinationLoadingHasABoundedTimeout() {
        doReturn(new CompletableFuture<Chunk>()).when(world).getChunkAtAsync(anyInt(), anyInt(), eq(false));
        ride(9);
        engine.tick();
        for (int tick = 0; tick < 1201; tick++) engine.tick();
        verify(player).sendMessage("Fast travel cancelled: the cart moved or destination preparation timed out.");
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    @Test
    void blockingEntitiesAheadDisableThePhysicalBoost() {
        doReturn(List.of(mock(org.bukkit.entity.Entity.class)))
                .when(world).getNearbyEntities(any(org.bukkit.util.BoundingBox.class));
        ride(4);
        assertEquals(0.4, maxSpeed);
    }

    @Test
    void shutdownWhilePreparingReleasesAllPluginTickets() {
        prepareArrival();
        engine.close();
        for (var chunk : tickets) verify(chunk).removePluginChunkTicket(plugin);
        engine.tick();
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    @Test
    void ridingAnIncompleteLineStillAllowsPhysicalSpeedBoost() {
        graph.observe(new TrackPos(world.getUID(), 64, 64, 8), null, false);
        ride(15);
        assertEquals(4.5, maxSpeed);
        assertTrue(tickets.isEmpty());
        verify(cart, never()).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }
}
