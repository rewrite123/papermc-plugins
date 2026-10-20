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
    private final RouteRegistry registry;
    private final java.util.function.Predicate<Minecart> recording;
    private TravelSettings settings;
    private final NamespacedKey normalKey;
    private final NamespacedKey originalSpeedKey;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private long tick;

    private static final class Ride {
        final Minecart cart;
        final double originalSpeed;
        final RideClock clock = new RideClock();
        Location previous;
        long lastTick = -2;
        boolean boosted;
        boolean transferring;
        boolean hinted;
        Player rider;
        RouteRegistry.Route route;
        RouteRegistry.End destination;
        Location origin;
        long deadline;
        boolean loaded;
        final List<Chunk> tickets = new ArrayList<>();

        Ride(Minecart cart) {
            this.cart = cart;
            originalSpeed = cart.getMaxSpeed();
        }
    }

    TravelEngine(JavaPlugin plugin, RouteRegistry registry, TravelSettings settings,
                 java.util.function.Predicate<Minecart> recording) {
        this.plugin = plugin;
        this.registry = registry;
        this.settings = settings;
        this.recording = recording;
        normalKey = new NamespacedKey(plugin, "normal_mode");
        originalSpeedKey = new NamespacedKey(plugin, "original_speed");
    }

    TravelSettings settings() {
        return settings;
    }

    void settings(TravelSettings settings) {
        this.settings = settings;
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
        var text = new StringBuilder("Mode: ").append(normal(player) ? "normal" : "fast")
                .append(". Qualifying travel: ").append(ride == null ? 0 : ride.clock.ticks() / 20).append("s.");
        if (player.getVehicle() instanceof Minecart cart) {
            var pos = TrackSafety.railUnder(cart.getLocation());
            var hit = pos == null ? null : registry.at(pos);
            text.append(hit == null ? " This rail is not on a registered fast-travel route."
                    : " This rail is on a registered route (" + hit.route().rails() + " rails).");
        }
        if (ride != null && ride.destination != null) text.append(" Preparing destination.");
        return text.toString();
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
        if (ride.clock.ticks() >= settings.fastTicks() && pos != null
                && cart instanceof RideableMinecart && !recording.test(cart)
                && cart.getPassengers().size() == 1 && cart.getPassengers().getFirst() instanceof Player player) {
            var data = cart.getWorld().getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData();
            var hit = data instanceof org.bukkit.block.data.Rail rail ? registry.at(pos, RailShape.of(rail)) : null;
            if (hit == null || registry.unverified.contains(pos.chunk())) {
                if (hit == null && !ride.hinted) {
                    player.sendMessage("This rail line is not registered for fast travel. "
                            + "Run /minecarttravel register and ride it from lodestone to lodestone to register it.");
                    ride.hinted = true;
                }
                return;
            }
            var toward = hit.segment().towardFirst();
            var velocity = cart.getVelocity();
            double dot = velocity.getX() * toward.dx() + velocity.getZ() * toward.dz();
            if (Math.abs(dot) < 0.01) return;
            ride.rider = player;
            ride.route = hit.route();
            ride.destination = dot > 0 ? hit.route().first() : hit.route().second();
            ride.origin = cart.getLocation();
            ride.deadline = tick + 1200;
            restoreSpeed(ride, true);
            cart.setVelocity(new Vector());
            tell(cart, "Preparing fast-travel arrival...");
            loadArrival(ride);
        }
    }

    void tick() {
        tick++;
        for (var ride : new ArrayList<>(rides.values())) {
            if (!ride.cart.isValid() || !enabled(ride.cart)) {
                remove(ride.cart);
                continue;
            }
            if (ride.destination == null || ride.transferring) continue;
            if (!ride.rider.isOnline() || ride.cart.getPassengers().size() != 1
                    || !ride.cart.getPassengers().contains(ride.rider)) {
                abort(ride, "Fast travel cancelled because the rider changed.");
                continue;
            }
            if (tick > ride.deadline || !ride.cart.getWorld().equals(ride.origin.getWorld())
                    || ride.cart.getLocation().distanceSquared(ride.origin) > 1) {
                abort(ride, "Fast travel cancelled: the cart moved or destination preparation timed out.");
                continue;
            }
            ride.cart.setVelocity(new Vector());
            if (!registry.contains(ride.route)) {
                abort(ride, "Fast travel cancelled: this route was unregistered because a rail or lodestone changed.");
            } else if (ride.loaded && destinationReady(ride)) {
                teleport(ride);
            }
        }
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
        return ride.tickets.stream().noneMatch(chunk -> registry.unverified.contains(
                new TrackPos.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())));
    }

    private void teleport(Ride ride) {
        if (!registry.contains(ride.route) || !enabled(ride.cart) || !destinationReady(ride)
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
        remove(ride.cart);
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
