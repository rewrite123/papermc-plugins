package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RideClockTest {
    @Test
    void fullFifteenAndThirtySecondsHaveExactBoundaries() {
        var clock = new RideClock();
        for (int tick = 1; tick <= 600; tick++) {
            clock.sample(true, true, 0.4, 6, 90);
            assertEquals(tick, clock.ticks());
            if (tick == 299) assertTrue(clock.ticks() < 300);
            if (tick == 300) assertEquals(300, clock.ticks());
            if (tick == 599) assertTrue(clock.ticks() < 600);
            if (tick == 600) assertEquals(600, clock.ticks());
        }
    }

    @Test
    void thresholdRequiresStrictlyAboveSixBlocksPerSecond() {
        var clock = new RideClock();
        clock.sample(true, true, 0.31, 6, 90);
        assertEquals(1, clock.ticks());
        clock.sample(true, true, 0.3, 6, 90);
        assertEquals(0, clock.ticks());
    }

    @Test
    void stoppingDerailingSkippedTicksAndExternalTeleportsResetTheTimer() {
        var clock = new RideClock();
        for (int type = 0; type < 5; type++) {
            clock.sample(true, true, 0.4, 6, 90);
            clock.sample(type != 0, type != 1, type == 2 ? 0 : type == 3 ? 100 : Double.NaN, 6, 90);
            assertEquals(0, clock.ticks());
        }
    }

    @Test
    void boostedMotionCountsAndTheTargetExceedsBlueIceBoatSpeed() {
        var clock = new RideClock();
        clock.sample(true, true, 4.5, 6, 90);
        assertEquals(1, clock.ticks());
        assertTrue(4.5 * 20 > 73);
        clock.reset();
        assertEquals(0, clock.ticks());
    }
}
