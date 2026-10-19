package io.github.ihormel.minecarttravel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

final class TravelEngine implements Listener {
    private final JavaPlugin plugin;
    private final RailGraph graph;
    private final TravelSettings settings;
    private final NamespacedKey normalKey;
    private final NamespacedKey originalSpeedKey;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private long tick;
    private int roundRobin;

    private static final class Ride {
        final Minecart cart;
        final double originalSpeed;
        final RideClock clock = new RideClock();
        Location previous;
        long lastTick = -2;
        long retryAt;
        boolean boosted;
        boolean transferring;
        RouteTrace trace;
        Player rider;
        RouteTrace.End destination;
        Location origin;
        long deadline;
        boolean loaded;
        final List<Chunk> tickets = new ArrayList<>();

        Ride(Minecart cart) {
            this.cart = cart;
            originalSpeed = cart.getMaxSpeed();
        }
    }

    TravelEngine(JavaPlugin plugin, RailGraph graph, TravelSettings settings) {
        this.plugin = plugin;
        this.graph = graph;
        this.settings = settings;
        normalKey = new NamespacedKey(plugin, "normal_mode");
        originalSpeedKey = new NamespacedKey(plugin, "original_speed");
    }

    boolean normal(Player player) {
        return player.getPersistentDataContainer().has(normalKey, PersistentDataType.BYTE);
    }

    void mode(Player player, boolean normal) {
        if (normal) player.getPersistentDataContainer().set(normalKey, PersistentDataType.BYTE, (byte) 1);
        else player.getPersistentDataContainer().remove(normalKey);
        if (player.getVehicle() instanceof Minecart cart) remove(cart);
    }

    String status(Player player) {
        var ride = player.getVehicle() == null ? null : rides.get(player.getVehicle().getUniqueId());
        return "Mode: " + (normal(player) ? "normal" : "fast") + ". Qualifying travel: "
                + (ride == null ? 0 : ride.clock.ticks() / 20) + "s. "
                + (ride != null && ride.destination != null ? "Preparing destination." : "");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void update(VehicleUpdateEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!rides.containsKey(cart.getUniqueId())) restoreStoredSpeed(cart);
        if (!enabled(cart)) {
            remove(cart);
            return;
        }
        var ride = rides.computeIfAbsent(cart.getUniqueId(), unused -> new Ride(cart));
        if (ride.transferring) return;
        if (ride.destination != null) {
            cart.setVelocity(new Vector());
            return;
        }
        var location = cart.getLocation();
        var pos = TrackSafety.railUnder(location);
        double distance = ride.previous == null || !ride.previous.getWorld().equals(location.getWorld())
                ? Double.NaN : ride.previous.distance(location);
        ride.clock.sample(ride.lastTick == tick - 1, pos != null, distance,
                settings.minimumSpeed(), settings.boostSpeed());
        ride.previous = location;
        ride.lastTick = tick;
        if (ride.clock.ticks() >= settings.boostTicks() && pos != null) {
            var data = cart.getWorld().getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData();
            var shape = RailShape.of((org.bukkit.block.data.Rail) data);
            var direction = TrackSafety.direction(shape, cart.getVelocity());
            boolean safe = TrackSafety.clear(cart.getWorld(), pos, shape, direction, settings.lookahead())
                    && TrackSafety.clearOfEntities(cart, pos, direction, settings.lookahead());
            if (safe) {
                cart.getPersistentDataContainer().set(originalSpeedKey, PersistentDataType.DOUBLE, ride.originalSpeed);
                cart.setMaxSpeed(settings.boostSpeed() / 20);
                cart.setVelocity(direction.multiply(settings.boostSpeed() / 20));
                if (!ride.boosted) tell(cart, "Minecart speed boost active!");
                ride.boosted = true;
            } else {
                restoreSpeed(ride, true);
            }
        } else {
            restoreSpeed(ride, true);
        }
        if (ride.clock.ticks() == 0 && ride.trace != null) {
            ride.trace = null;
            ride.rider = null;
        }
        if (ride.clock.ticks() >= settings.fastTicks() && tick >= ride.retryAt
                && ride.trace == null && pos != null && cart instanceof RideableMinecart
                && cart.getPassengers().size() == 1 && cart.getPassengers().getFirst() instanceof Player player) {
            ride.rider = player;
            ride.trace = new RouteTrace(graph, pos, settings.maxRails(), settings.maxTurns());
            ride.deadline = tick + 1200;
        }
    }

    void tick() {
        tick++;
        var active = new ArrayList<>(rides.values());
        for (var ride : active) {
            if (!ride.cart.isValid() || !enabled(ride.cart)) {
                remove(ride.cart);
                continue;
            }
            if (ride.rider != null && (!ride.rider.isOnline() || ride.cart.getPassengers().size() != 1
                    || !ride.cart.getPassengers().contains(ride.rider))) {
                abort(ride, "Fast travel cancelled because the rider changed.");
                continue;
            }
            if (ride.destination != null && !ride.transferring) {
                if (tick > ride.deadline || !ride.cart.getWorld().equals(ride.origin.getWorld())
                        || ride.cart.getLocation().distanceSquared(ride.origin) > 1) {
                    abort(ride, "Fast travel cancelled: the cart moved or destination preparation timed out.");
                    continue;
                }
                ride.cart.setVelocity(new Vector());
                if (ride.destination != null && ride.loaded && ride.trace == null && destinationReady(ride)) {
                    var pos = TrackSafety.railUnder(ride.cart.getLocation());
                    if (pos == null) abort(ride, "The rail below the cart was removed.");
                    else ride.trace = new RouteTrace(graph, pos, settings.maxRails(), settings.maxTurns());
                }
            } else if (ride.trace != null && tick > ride.deadline) {
                abort(ride, "Route verification timed out.");
            }
        }
        // One global round-robin work budget, even with many simultaneous carts.
        if (active.isEmpty()) return;
        for (int step = 0, idle = 0; step < settings.routeBudget() && idle < active.size(); step++) {
            var ride = active.get(Math.floorMod(roundRobin++, active.size()));
            if (!rides.containsKey(ride.cart.getUniqueId()) || ride.trace == null) {
                idle++;
                continue;
            }
            idle = 0;
            ride.trace.step();
            if (ride.trace.done()) finishTrace(ride);
        }
    }

    private void finishTrace(Ride ride) {
        var trace = ride.trace;
        ride.trace = null;
        if (trace.route() == null) {
            abort(ride, trace.failure());
            return;
        }
        if (ride.destination != null) {
            var currentPos = TrackSafety.railUnder(ride.cart.getLocation());
            var currentRun = currentPos == null ? null : graph.run(currentPos);
            if (!trace.route().towardFirst().containsKey(currentRun)
                    || (!trace.route().first().equals(ride.destination)
                    && !trace.route().second().equals(ride.destination))) {
                abort(ride, "This cart is no longer on its verified destination route.");
                return;
            }
            teleport(ride, trace.route().revision());
            return;
        }
        var pos = TrackSafety.railUnder(ride.cart.getLocation());
        if (pos == null) {
            abort(ride, "The cart left the rails.");
            return;
        }
        var towardFirst = trace.route().towardFirst().get(graph.run(pos));
        if (towardFirst == null) {
            abort(ride, "The cart left its verified route.");
            return;
        }
        var velocity = ride.cart.getVelocity();
        double dot = velocity.getX() * towardFirst.dx() + velocity.getZ() * towardFirst.dz();
        if (Math.abs(dot) < 0.01) {
            abort(ride, "Keep moving toward a destination to start fast travel.");
            return;
        }
        ride.destination = dot > 0 ? trace.route().first() : trace.route().second();
        ride.origin = ride.cart.getLocation();
        restoreSpeed(ride, true);
        ride.cart.setVelocity(new Vector());
        tell(ride.cart, "Preparing fast-travel arrival...");
        loadArrival(ride);
    }

    private void loadArrival(Ride ride) {
        var world = ride.cart.getWorld();
        var pos = ride.destination.rail();
        var futures = new ArrayList<CompletableFuture<Chunk>>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                futures.add(world.getChunkAtAsync((pos.x() >> 4) + dx, (pos.z() >> 4) + dz, false));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((unused, error) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (rides.get(ride.cart.getUniqueId()) != ride || ride.destination == null) return;
                if (error != null || futures.stream().anyMatch(future -> future.getNow(null) == null)) {
                    if (error != null) plugin.getLogger().log(Level.WARNING, "Arrival chunk loading failed", error);
                    abort(ride, "The destination area could not be loaded.");
                    return;
                }
                for (var future : futures) {
                    var chunk = future.getNow(null);
                    if (!world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
                        abort(ride, "The destination area unloaded before it was ready.");
                        return;
                    }
                    chunk.addPluginChunkTicket(plugin);
                    ride.tickets.add(chunk);
                }
                ride.loaded = true;
            });
        });
    }

    private boolean destinationReady(Ride ride) {
        return ride.tickets.stream().noneMatch(chunk -> graph.dirty.contains(
                new TrackPos.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())));
    }

    private void teleport(Ride ride, long revision) {
        if (graph.revision() != revision || !enabled(ride.cart) || !destinationReady(ride)
                || !TrackSafety.arrivalClear(ride.cart, ride.destination)
                || ride.rider == null || !ride.rider.isOnline()
                || !ride.cart.getPassengers().equals(List.of(ride.rider))) {
            abort(ride, "Fast travel cancelled: the route or arrival area is no longer safe.");
            return;
        }
        var destination = TrackSafety.arrival(ride.cart.getWorld(), ride.destination);
        destination.setYaw(ride.rider.getLocation().getYaw());
        ride.transferring = true;
        boolean success = ride.cart.teleport(destination, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (!success) {
            ride.transferring = false;
            abort(ride, "Fast travel was cancelled by the server or another plugin.");
            return;
        }
        ride.cart.setVelocity(new Vector());
        tell(ride.cart, "Fast travel complete! Your cart has arrived at the lodestone.");
        remove(ride.cart);
    }

    private boolean enabled(Minecart cart) {
        for (var passenger : cart.getPassengers()) {
            if (passenger instanceof Player player && (normal(player)
                    || !player.hasPermission("minecarttravel.use") || player.isDead()
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)) return false;
        }
        return true;
    }

    private void abort(Ride ride, String message) {
        if (ride.rider != null) ride.rider.sendMessage(message);
        boolean held = ride.destination != null;
        remove(ride.cart);
        // Retry incomplete graph walks at most once per five seconds without
        // resetting an otherwise continuously moving cart's earned timer.
        if (!held && ride.cart.isValid() && enabled(ride.cart)) {
            ride.trace = null;
            ride.rider = null;
            ride.retryAt = tick + 100;
            rides.put(ride.cart.getUniqueId(), ride);
        }
    }

    private void tell(Minecart cart, String message) {
        for (var passenger : cart.getPassengers()) {
            if (passenger instanceof Player player) player.sendMessage(message);
        }
    }

    private void restoreSpeed(Ride ride, boolean clamp) {
        boolean wasBoosted = ride.cart.getPersistentDataContainer().has(originalSpeedKey, PersistentDataType.DOUBLE);
        ride.cart.setMaxSpeed(ride.originalSpeed);
        ride.cart.getPersistentDataContainer().remove(originalSpeedKey);
        if (clamp && wasBoosted) {
            var velocity = ride.cart.getVelocity();
            double limit = Math.min(0.4, ride.originalSpeed);
            if (velocity.lengthSquared() > limit * limit) ride.cart.setVelocity(velocity.normalize().multiply(limit));
        }
        ride.boosted = false;
    }

    private void restoreStoredSpeed(Minecart cart) {
        Double original = cart.getPersistentDataContainer().get(originalSpeedKey, PersistentDataType.DOUBLE);
        if (original == null) return;
        if (!Double.isFinite(original) || original < 0) {
            plugin.getLogger().warning("Invalid stored original speed on minecart " + cart.getUniqueId());
            original = 0.4;
        }
        cart.setMaxSpeed(original);
        cart.setVelocity(new Vector());
        cart.getPersistentDataContainer().remove(originalSpeedKey);
    }

    private void remove(Minecart cart) {
        var ride = rides.remove(cart.getUniqueId());
        if (ride == null) return;
        restoreSpeed(ride, true);
        for (var chunk : ride.tickets) chunk.removePluginChunkTicket(plugin);
        ride.tickets.clear();
    }

    void close() {
        for (var ride : new ArrayList<>(rides.values())) remove(ride.cart);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void enter(VehicleEnterEvent event) { if (event.getVehicle() instanceof Minecart cart) remove(cart); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exit(VehicleExitEvent event) { if (event.getVehicle() instanceof Minecart cart) remove(cart); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void destroy(VehicleDestroyEvent event) { if (event.getVehicle() instanceof Minecart cart) remove(cart); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void externalTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Minecart cart) {
            var ride = rides.get(cart.getUniqueId());
            if (ride != null && !ride.transferring) remove(cart);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void unload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof Minecart cart) remove(cart);
    }
}
