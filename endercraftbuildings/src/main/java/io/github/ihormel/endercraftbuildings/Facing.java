package io.github.ihormel.endercraftbuildings;

import org.bukkit.block.structure.StructureRotation;

/** Horizontal facing used to orient blueprints; blueprints are stored as if scanned facing north. */
enum Facing {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    final int dx;
    final int dz;

    Facing(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    static Facing fromYaw(float yaw) {
        int index = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (index) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }

    Facing right() { return values()[(ordinal() + 1) % 4]; }

    /** Clockwise quarter turns needed to turn north into this facing. */
    int steps() { return ordinal(); }

    static StructureRotation rotation(int clockwiseSteps) {
        return switch (Math.floorMod(clockwiseSteps, 4)) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }
}
