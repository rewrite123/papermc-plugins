package io.github.ihormel.minecarttravel;

import java.util.List;
import org.bukkit.block.data.Rail;

enum RailShape {
    NORTH_SOUTH(new Port(0, -1, 0), new Port(0, 1, 0)),
    EAST_WEST(new Port(-1, 0, 0), new Port(1, 0, 0)),
    ASCENDING_EAST(new Port(-1, 0, 0), new Port(1, 0, 1)),
    ASCENDING_WEST(new Port(-1, 0, 1), new Port(1, 0, 0)),
    ASCENDING_NORTH(new Port(0, -1, 1), new Port(0, 1, 0)),
    ASCENDING_SOUTH(new Port(0, -1, 0), new Port(0, 1, 1)),
    SOUTH_EAST(new Port(0, 1, 0), new Port(1, 0, 0)),
    SOUTH_WEST(new Port(0, 1, 0), new Port(-1, 0, 0)),
    NORTH_WEST(new Port(0, -1, 0), new Port(-1, 0, 0)),
    NORTH_EAST(new Port(0, -1, 0), new Port(1, 0, 0));

    record Port(int dx, int dz, int height) {}
    final List<Port> ports;

    RailShape(Port first, Port second) {
        ports = List.of(first, second);
    }

    static RailShape of(Rail rail) {
        return valueOf(rail.getShape().name());
    }

    boolean flatStraight() {
        return this == NORTH_SOUTH || this == EAST_WEST;
    }

    boolean straight() {
        return ordinal() < 6;
    }

    boolean xAxis() {
        return this == EAST_WEST || this == ASCENDING_EAST || this == ASCENDING_WEST || !straight();
    }

    int slope() {
        return switch (this) {
            case ASCENDING_EAST, ASCENDING_SOUTH -> 1;
            case ASCENDING_WEST, ASCENDING_NORTH -> -1;
            default -> 0;
        };
    }
}
