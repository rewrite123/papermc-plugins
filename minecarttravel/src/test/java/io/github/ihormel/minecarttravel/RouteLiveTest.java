package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Registration and invalidation against real (mock server) blocks. */
class RouteLiveTest extends PaperTest {
    private static final int LENGTH = 48;
    private World world;
    private RouteRegistry registry;
    private RouteRegistrar registrar;
    private RouteWatcher watcher;
    private final List<String> log = new ArrayList<>();
    private Player player;
    private RideableMinecart cart;
    private Location location;
    private boolean riding;

    @BeforeEach
    void build() {
        world = server.addSimpleWorld("world");
        for (int chunk = -1; chunk <= 3; chunk++) world.loadChunk(chunk, 0);
        for (int x = 0; x < LENGTH; x++) rail(x, Rail.Shape.EAST_WEST);
        world.getBlockAt(-1, 64, 0).setType(Material.LODESTONE);
        world.getBlockAt(LENGTH, 64, 0).setType(Material.LODESTONE);
        registry = new RouteRegistry();
        var settings = new TravelSettings(600, 2400, 6, 90, 12, 65536, 32, 4096, 512, 600);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        registrar = new RouteRegistrar(registry, () -> settings, id -> player);
        watcher = new RouteWatcher(registry, 4096, server::getWorld, log::add);
        cart = mock(RideableMinecart.class);
        when(cart.isValid()).thenReturn(true);
        when(cart.getWorld()).thenReturn(world);
        when(cart.getLocation()).thenAnswer(unused -> location.clone());
        when(player.getVehicle()).thenAnswer(unused -> riding ? cart : null);
        location = new Location(world, 2.5, 64.1, 0.5);
    }

    private void rail(int x, Rail.Shape shape) {
        var block = world.getBlockAt(x, 64, 0);
        block.setType(Material.RAIL);
        var data = (Rail) block.getBlockData();
        data.setShape(shape);
        block.setBlockData(data);
    }

    private void rideToEnd() {
        for (double x = location.getX(); x < LENGTH; x += 1.5) {
            location.setX(Math.min(x, LENGTH - 0.5));
            registrar.tick();
        }
        location.setX(LENGTH - 0.5);
        registrar.tick();
    }

    @Test
    void armedPlayerRegistersTheLineByRidingIt() {
        registrar.request(player);
        verify(player).sendMessage(contains("Within 60 seconds"));
        registrar.tick();
        assertNull(registrar.status(player) == null ? null : registry.at(new TrackPos(world.getUID(), 5, 64, 0)));
        riding = true;
        registrar.tick();
        assertTrue(registrar.recording(cart));
        verify(player).sendMessage(contains("from the lodestone at -1 64 0"));
        rideToEnd();
        verify(player).sendMessage(contains("Route registered! 48 rails and 0 turns"));
        assertFalse(registrar.recording(cart));
        var hit = registry.at(new TrackPos(world.getUID(), 30, 64, 0), RailShape.EAST_WEST);
        assertNotNull(hit);
        assertEquals(new TrackPos(world.getUID(), LENGTH, 64, 0), hit.route().second().lodestone());
    }

    @Test
    void leavingTheCartOrCancellingStopsRegistration() {
        riding = true;
        registrar.request(player);
        assertTrue(registrar.recording(cart));
        assertNotNull(registrar.status(player));
        riding = false;
        registrar.tick();
        verify(player).sendMessage("Route registration cancelled: you left the minecart.");
        assertFalse(registrar.recording(cart));
        riding = true;
        registrar.request(player);
        assertTrue(registrar.cancel(player));
        assertFalse(registrar.cancel(player));
        rideToEnd();
        assertTrue(registry.routes().isEmpty());
    }

    @Test
    void armingExpiresAfterAMinute() {
        registrar.request(player);
        for (int tick = 0; tick < RouteRegistrar.ARM_TICKS; tick++) registrar.tick();
        verify(player).sendMessage("Route registration expired because you did not enter a minecart.");
        assertNull(registrar.status(player));
    }

    @Test
    void startingFarFromALodestoneExplainsWhy() {
        location = new Location(world, 24.5, 64.1, 0.5);
        riding = true;
        registrar.request(player);
        verify(player).sendMessage(contains("not within 16 rails"));
        assertFalse(registrar.recording(cart));
    }

    private RouteRegistry.Route registered() {
        riding = true;
        registrar.request(player);
        rideToEnd();
        assertEquals(1, registry.routes().size());
        return registry.routes().iterator().next();
    }

    @Test
    void breakingARailUnregistersTheRoute() {
        var route = registered();
        var block = world.getBlockAt(30, 64, 0);
        block.setType(Material.AIR);
        watcher.changed(block);
        assertTrue(registry.unverified.contains(new TrackPos(world.getUID(), 30, 64, 0).chunk()));
        watcher.tick();
        assertFalse(registry.contains(route));
        assertTrue(log.getFirst().contains("rail at 30 64 0 changed"));
        assertEquals(0, watcher.pending());
        assertTrue(registry.unverified.isEmpty());
    }

    @Test
    void reshapingARailOrRemovingALodestoneUnregistersTheRoute() {
        var route = registered();
        rail(10, Rail.Shape.NORTH_SOUTH);
        watcher.changed(world.getBlockAt(10, 64, 1));
        watcher.tick();
        assertFalse(registry.contains(route));

        location = new Location(world, 2.5, 64.1, 0.5);
        rail(10, Rail.Shape.EAST_WEST);
        var again = registered();
        world.getBlockAt(LENGTH, 64, 0).setType(Material.STONE);
        watcher.changed(world.getBlockAt(LENGTH, 64, 0));
        watcher.tick();
        assertFalse(registry.contains(again));
        assertTrue(log.getLast().contains("lodestone at 48 64 0 was removed"));
    }

    @Test
    void unrelatedChangesAndIntactChunksKeepTheRoute() {
        var route = registered();
        world.getBlockAt(10, 66, 0).setType(Material.STONE);
        watcher.changed(world.getBlockAt(10, 66, 0));
        watcher.changed(world.getBlockAt(500, 64, 500));
        world.getBlockAt(10, 63, 0).setType(Material.DIRT);
        watcher.changed(world.getBlockAt(10, 63, 0));
        watcher.loaded(world.getChunkAt(1, 0));
        assertTrue(watcher.pending() > 0);
        while (watcher.pending() > 0) watcher.tick();
        assertTrue(registry.contains(route));
        assertTrue(registry.unverified.isEmpty());
        assertTrue(log.isEmpty());
    }
}
