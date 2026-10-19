package io.github.ihormel.minecarttravel;

final class RideClock {
    private int ticks;

    int ticks() { return ticks; }
    void reset() { ticks = 0; }

    void sample(boolean consecutive, boolean onRail, double distance, double minimum, double maximum) {
        if (!consecutive || !onRail || !Double.isFinite(distance)
                || distance <= minimum / 20.0 || distance > maximum / 20.0 * 2 + 1) {
            reset();
        } else if (ticks < Integer.MAX_VALUE) {
            ticks++;
        }
    }
}
