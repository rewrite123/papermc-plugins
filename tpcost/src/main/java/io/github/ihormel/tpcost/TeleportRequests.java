package io.github.ihormel.tpcost;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

final class TeleportRequests {
    static final Duration LIFETIME = Duration.ofSeconds(60);

    record Request(UUID sender, UUID target, long createdAt) {
    }

    private final Map<UUID, Request> outgoing = new LinkedHashMap<>();
    private final LongSupplier nanoTime;

    TeleportRequests(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    Optional<Request> add(UUID sender, UUID target) {
        return Optional.ofNullable(outgoing.put(sender, new Request(sender, target, nanoTime.getAsLong())));
    }

    Optional<Request> remove(UUID sender) {
        return Optional.ofNullable(outgoing.remove(sender));
    }

    List<Request> incoming(UUID target) {
        return outgoing.values().stream().filter(request -> request.target().equals(target)).toList();
    }

    List<Request> expire() {
        long now = nanoTime.getAsLong();
        var expired = outgoing.values().stream()
                .filter(request -> now - request.createdAt() >= LIFETIME.toNanos())
                .toList();
        expired.forEach(request -> outgoing.remove(request.sender()));
        return expired;
    }

    List<Request> removeFor(UUID player) {
        var removed = outgoing.values().stream()
                .filter(request -> request.sender().equals(player) || request.target().equals(player))
                .toList();
        removed.forEach(request -> outgoing.remove(request.sender()));
        return removed;
    }

    void clear() {
        outgoing.clear();
    }
}
