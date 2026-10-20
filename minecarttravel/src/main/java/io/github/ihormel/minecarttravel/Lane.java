package io.github.ihormel.minecarttravel;

import java.util.UUID;

/** A straight line of same-shaped rail cells, addressed by one coordinate along its axis. */
record Lane(UUID world, RailShape shape, int cross, int intercept) {
    static Lane at(TrackPos pos, RailShape shape) {
        int t = shape.xAxis() ? pos.x() : pos.z();
        return new Lane(pos.world(), shape, shape.xAxis() ? pos.z() : pos.x(), pos.y() - shape.slope() * t);
    }

    TrackPos pos(int t) {
        return new TrackPos(world, shape.xAxis() ? t : cross, intercept + shape.slope() * t, shape.xAxis() ? cross : t);
    }

    int coordinate(TrackPos pos) {
        return shape.xAxis() ? pos.x() : pos.z();
    }
}
