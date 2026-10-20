package io.github.ihormel.minecarttravel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Runs /minecarttravel register sessions: arm, wait for a minecart, then record the ride. */
final class RouteRegistrar implements Listener {
    static final int START_SEARCH = 16;
    static final int ARM_TICKS = 1200;
    static final int STALL_TICKS = 1200;
    static final int SESSION_STEPS = 64;
    private final RouteRegistry registry;
    private final Supplier<TravelSettings> settings;
    private final Function<UUID, Player> players;
    private final Map<UUID, Long> armed = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private long tick;
    private int roundRobin;

    private static final class Session {
        final Player player;
        final Minecart cart;
        final RouteRecorder recorder;
        long progressTick;
        int lastRails;

        Session(Player player, Minecart cart, RouteRecorder recorder, long tick) {
            this.player = player;
            this.cart = cart;
            this.recorder = recorder;
            progressTick = tick;
            lastRails = recorder.rails();
        }
    }

    RouteRegistrar(RouteRegistry registry, Supplier<TravelSettings> settings, Function<UUID, Player> players) {
        this.registry = registry;
        this.settings = settings;
        this.players = players;
    }

    static RouteRecorder.Rails rails(World world) {
        return new RouteRecorder.Rails() {
            @Override
            public boolean loaded(TrackPos pos) {
                return world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4);
            }

            @Override
            public RailShape rail(TrackPos pos) {
                if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) return null;
                return world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData() instanceof Rail rail
                        ? RailShape.of(rail) : null;
            }

            @Override
            public boolean lodestone(TrackPos pos) {
                return pos.y() >= world.getMinHeight() && pos.y() < world.getMaxHeight()
                        && world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == Material.LODESTONE;
            }
        };
    }

    void request(Player player) {
        sessions.remove(player.getUniqueId());
        if (player.getVehicle() instanceof Minecart cart) {
            start(player, cart);
            return;
        }
        armed.put(player.getUniqueId(), tick + ARM_TICKS);
        player.sendMessage("Route registration ready. Within 60 seconds, get into a minecart on a rail line "
                + "that ends straight into a lodestone, then ride it to the lodestone at the other end.");
    }

    boolean cancel(Player player) {
        boolean wasArmed = armed.remove(player.getUniqueId()) != null;
        boolean wasRecording = sessions.remove(player.getUniqueId()) != null;
        return wasArmed || wasRecording;
    }

    boolean recording(Minecart cart) {
        for (var session : sessions.values()) if (session.cart.equals(cart)) return true;
        return false;
    }

    String status(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session != null) {
            return "Registering route: " + session.recorder.rails() + " rails, " + session.recorder.turns()
                    + "/" + settings.get().maxTurns() + " turns.";
        }
        return armed.containsKey(player.getUniqueId()) ? "Route registration is waiting for you to enter a minecart." : null;
    }

    private void start(Player player, Minecart cart) {
        armed.remove(player.getUniqueId());
        var pos = TrackSafety.railUnder(cart.getLocation());
        if (pos == null) {
            player.sendMessage("Route registration failed: your minecart must be on a rail.");
            return;
        }
        var current = settings.get();
        var recorder = RouteRecorder.begin(rails(cart.getWorld()), pos, current.maxRails(), current.maxTurns(), START_SEARCH);
        if (recorder.state() == RouteRecorder.State.FAILED) {
            player.sendMessage("Route registration failed: " + recorder.failure());
            return;
        }
        sessions.put(player.getUniqueId(), new Session(player, cart, recorder, tick));
        var stop = recorder.first().lodestone();
        player.sendMessage("Registering a route from the lodestone at " + coordinates(stop)
                + ". Ride to the lodestone at the other end. Leaving the minecart cancels registration.");
    }

    void tick() {
        tick++;
        for (var entry : new ArrayList<>(armed.entrySet())) {
            var player = players.apply(entry.getKey());
            if (player == null || !player.isOnline()) {
                armed.remove(entry.getKey());
            } else if (player.getVehicle() instanceof Minecart cart) {
                start(player, cart);
            } else if (tick >= entry.getValue()) {
                armed.remove(entry.getKey());
                player.sendMessage("Route registration expired because you did not enter a minecart.");
            }
        }
        var active = new ArrayList<>(sessions.values());
        if (active.isEmpty()) return;
        int remaining = settings.get().recordBudget();
        for (int index = 0; index < active.size() && remaining >= SESSION_STEPS; index++) {
            var session = active.get(Math.floorMod(roundRobin++, active.size()));
            remaining -= SESSION_STEPS;
            update(session);
        }
    }

    private void update(Session session) {
        var player = session.player;
        var cart = session.cart;
        var recorder = session.recorder;
        if (!player.isOnline() || player.getVehicle() != cart || !cart.isValid()
                || !cart.getWorld().getUID().equals(recorder.first().rail().world())) {
            end(session, "Route registration cancelled: you left the minecart.");
            return;
        }
        recorder.follow(TrackSafety.railUnder(cart.getLocation()), SESSION_STEPS);
        switch (recorder.state()) {
            case COMPLETE -> {
                var route = recorder.route();
                var replaced = registry.add(route);
                end(session, "Route registered! " + route.rails() + " rails and " + route.turns()
                        + " turns between the lodestones at " + coordinates(route.first().lodestone()) + " and "
                        + coordinates(route.second().lodestone()) + ". Ride it for "
                        + TravelSettings.seconds(settings.get().fastTicks()) + " seconds to fast travel."
                        + (replaced.isEmpty() ? "" : " Replaced " + replaced.size() + " older overlapping route"
                        + (replaced.size() == 1 ? "." : "s.")));
            }
            case FAILED -> end(session, recorder.failure() + " Run /minecarttravel register to try again.");
            case RECORDING -> {
                if (recorder.rails() != session.lastRails) {
                    session.lastRails = recorder.rails();
                    session.progressTick = tick;
                } else if (tick - session.progressTick > STALL_TICKS) {
                    end(session, "Route registration timed out: your minecart stopped for 60 seconds.");
                    return;
                }
                if (tick % 20 == 0) player.sendActionBar(Component.text(status(player)));
            }
        }
    }

    private void end(Session session, String message) {
        sessions.remove(session.player.getUniqueId());
        session.player.sendMessage(message);
    }

    static String coordinates(TrackPos pos) {
        return pos.x() + " " + pos.y() + " " + pos.z();
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }

    void close() {
        armed.clear();
        sessions.clear();
    }
}
