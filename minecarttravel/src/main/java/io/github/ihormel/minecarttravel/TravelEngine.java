package io.github.ihormel.minecarttravel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final NamespacedKey noBoostKey;
    private final NamespacedKey noFastTravelKey;
    private final NamespacedKey originalSpeedKey;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<Chunk, Integer> ticketUsers = new HashMap<>();
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
        boolean fastTravelFailed;
        Vector departureVelocity;
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
        noBoostKey = new NamespacedKey(plugin, "no_boost");
        noFastTravelKey = new NamespacedKey(plugin, "no_fast_travel");
        originalSpeedKey = new NamespacedKey(plugin, "original_speed");
    }

    TravelSettings settings() {
        return settings;
    }

    void settings(TravelSettings settings) {
        this.settings = settings;
    }

    /** True when the player has opted out of both the speed boost and fast travel. */
    boolean normal(Player player) {
        return player.getPersistentDataContainer().has(normalKey, PersistentDataType.BYTE);
    }

    boolean boostEnabled(Player player) {
        return !normal(player) && !player.getPersistentDataContainer().has(noBoostKey, PersistentDataType.BYTE);
    }

    boolean fastTravelEnabled(Player player) {
        return !normal(player) && !player.getPersistentDataContainer().has(noFastTravelKey, PersistentDataType.BYTE);
    }

    void mode(Player player, boolean normal) {
        var data = player.getPersistentDataContainer();
        data.remove(noBoostKey);
        data.remove(noFastTravelKey);
        if (normal) data.set(normalKey, PersistentDataType.BYTE, (byte) 1);
        else data.remove(normalKey);
        if (normal && player.getVehicle() instanceof Minecart cart) remove(cart);
    }

    void boost(Player player, boolean enabled) {
        preferences(player, enabled, fastTravelEnabled(player));
    }

    void fastTravel(Player player, boolean enabled) {
        preferences(player, boostEnabled(player), enabled);
    }

    // Opting out of both is stored as the original normal-mode flag so 0.1.x/0.2.0 preferences keep working.
    private void preferences(Player player, boolean boost, boolean fastTravel) {
        if (!boost && !fastTravel) {
            mode(player, true);
            return;
        }
        var data = player.getPersistentDataContainer();
        data.remove(normalKey);
        if (boost) data.remove(noBoostKey);
        else data.set(noBoostKey, PersistentDataType.BYTE, (byte) 1);
        if (fastTravel) data.remove(noFastTravelKey);
        else data.set(noFastTravelKey, PersistentDataType.BYTE, (byte) 1);
    }

    String preferences(Player player) {
        return "Speed boost: " + (boostEnabled(player) ? "on" : "off")
                + ". Fast travel: " + (fastTravelEnabled(player) ? "on" : "off") + ".";
    }

    String status(Player player) {
        var ride = player.getVehicle() == null ? null : rides.get(player.getVehicle().getUniqueId());
        var text = new StringBuilder(preferences(player))
                .append(" Qualifying travel: ").append(ride == null ? 0 : ride.clock.ticks() / 20).append("s.");
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
        if (ride.clock.ticks() >= settings.boostTicks() && pos != null && boostAllowed(cart)) {
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
        if (!ride.fastTravelFailed && ride.clock.ticks() >= settings.fastTicks() && pos != null
                && cart instanceof RideableMinecart && !recording.test(cart)
                && cart.getPassengers().size() == 1 && cart.getPassengers().getFirst() instanceof Player player
                && fastTravelEnabled(player)) {
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
            ride.departureVelocity = cart.getVelocity();
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
            if (!fastTravelEnabled(ride.rider)) {
                abort(ride, "Fast travel cancelled because you turned it off.");
                continue;
            }
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
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getChunkAtAsync((pos.x() >> 4) + dx, (pos.z() >> 4) + dz, false)
                        .whenComplete((chunk, error) -> {
                            if (!plugin.isEnabled()) return;
                            Runnable complete = () -> {
                                if (rides.get(ride.cart.getUniqueId()) != ride || ride.destination == null) return;
                                if (error != null || chunk == null) {
                                    if (error != null) plugin.getLogger().log(Level.WARNING, "Arrival chunk loading failed", error);
                                    abort(ride, "The destination area could not be loaded.");
                                    return;
                                }
                                // Adding the ticket also reloads a chunk if it unloaded before this callback.
                                if (!ticketUsers.containsKey(chunk)) chunk.addPluginChunkTicket(plugin);
                                ticketUsers.merge(chunk, 1, Integer::sum);
                                ride.tickets.add(chunk);
                                ride.loaded = ride.tickets.size() == 9;
                            };
                            plugin.getServer().getScheduler().runTask(plugin, complete);
                        });
            }
        }
    }

    private boolean destinationReady(Ride ride) {
        return ride.tickets.stream().noneMatch(chunk -> registry.unverified.contains(
                new TrackPos.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())));
    }

    private void teleport(Ride ride) {
        if (!registry.contains(ride.route) || !enabled(ride.cart) || !destinationReady(ride)
                || !TrackSafety.arrivalClear(ride.cart, ride.destination)
                || ride.rider == null || !ride.rider.isOnline() || !fastTravelEnabled(ride.rider)
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

    private boolean boostAllowed(Minecart cart) {
        for (var passenger : cart.getPassengers()) {
            if (passenger instanceof Player player && !boostEnabled(player)) return false;
        }
        return true;
    }

    private void abort(Ride ride, String message) {
        if (ride.rider != null) ride.rider.sendMessage(message);
        releaseTickets(ride);
        ride.destination = null;
        ride.route = null;
        ride.fastTravelFailed = true;
        ride.loaded = false;
        var cart = ride.cart;
        if (!cart.isValid() || !enabled(cart) || !cart.getPassengers().equals(List.of(ride.rider))
                || !ride.rider.isOnline() || !cart.getWorld().equals(ride.origin.getWorld())
                || cart.getLocation().distanceSquared(ride.origin) > 1) {
            remove(cart);
            return;
        }
        var pos = TrackSafety.railUnder(cart.getLocation());
        if (pos != null && cart.getWorld().getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData()
                instanceof org.bukkit.block.data.Rail rail) {
            var shape = RailShape.of(rail);
            var direction = TrackSafety.direction(shape, ride.departureVelocity);
            if (TrackSafety.clear(cart.getWorld(), pos, shape, direction, settings.lookahead())
                    && TrackSafety.clearOfEntities(cart, pos, direction, settings.lookahead())) {
                var velocity = ride.departureVelocity.clone();
                if (!boostAllowed(cart)) {
                    restoreSpeed(ride, false);
                    double limit = Math.min(0.4, ride.originalSpeed);
                    if (velocity.lengthSquared() > limit * limit) velocity.normalize().multiply(limit);
                }
                cart.setVelocity(velocity);
            } else {
                restoreSpeed(ride, true);
            }
        } else {
            restoreSpeed(ride, true);
        }
        ride.previous = cart.getLocation();
        ride.lastTick = tick;
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
        releaseTickets(ride);
    }

    private void releaseTickets(Ride ride) {
        for (var chunk : ride.tickets) {
            int users = ticketUsers.get(chunk);
            if (users == 1) {
                ticketUsers.remove(chunk);
                chunk.removePluginChunkTicket(plugin);
            } else {
                ticketUsers.put(chunk, users - 1);
            }
        }
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
