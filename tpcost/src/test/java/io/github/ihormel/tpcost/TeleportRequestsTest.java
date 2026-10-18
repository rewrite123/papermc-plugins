package io.github.ihormel.tpcost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TeleportRequestsTest {
    private final AtomicLong time = new AtomicLong();
    private final TeleportRequests requests = new TeleportRequests(time::get);
    private final UUID sender = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void allowsOnlyOneOutgoingRequestAndReturnsReplacedRequest() {
        assertTrue(requests.add(sender, target).isEmpty());
        UUID replacement = UUID.randomUUID();
        assertEquals(target, requests.add(sender, replacement).orElseThrow().target());
        assertTrue(requests.incoming(target).isEmpty());
        assertEquals(1, requests.incoming(replacement).size());
    }

    @Test
    void expiresAtExactlySixtySecondsAndOnlyOnce() {
        requests.add(sender, target);
        time.set(TeleportRequests.LIFETIME.toNanos() - 1);
        assertTrue(requests.expire().isEmpty());
        time.incrementAndGet();
        assertEquals(1, requests.expire().size());
        assertTrue(requests.expire().isEmpty());
        assertTrue(requests.incoming(target).isEmpty());
    }

    @Test
    void removesIncomingAndOutgoingRequestsOnDisconnectWithoutTouchingOthers() {
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        requests.add(sender, target);
        requests.add(target, third);
        requests.add(third, fourth);
        assertEquals(2, requests.removeFor(target).size());
        assertTrue(requests.incoming(target).isEmpty());
        assertTrue(requests.incoming(third).isEmpty());
        assertEquals(1, requests.incoming(fourth).size());
    }

    @Test
    void cancellationAndShutdownRemoveRequests() {
        requests.add(sender, target);
        assertEquals(target, requests.remove(sender).orElseThrow().target());
        assertTrue(requests.remove(sender).isEmpty());
        requests.add(sender, target);
        requests.clear();
        assertTrue(requests.incoming(target).isEmpty());
    }
}
