package io.github.ihormel.tpcost;

import java.util.Objects;
import org.bukkit.Location;

public final class TeleportCost {
    private TeleportCost() {
    }

    public static int levels(double distance, boolean crossWorld) {
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Distance must be finite and non-negative");
        }
        int base = (int) Math.min(25, Math.max(3, Math.ceil(distance * 3.0 / 1000.0)));
        return base + (crossWorld ? 3 : 0);
    }

    public static int between(Location from, Location to) {
        var fromWorld = Objects.requireNonNull(from.getWorld(), "Source world");
        var toWorld = Objects.requireNonNull(to.getWorld(), "Destination world");
        double distance = Math.hypot(
                Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ()),
                from.getY() - to.getY());
        return levels(distance, !fromWorld.getUID().equals(toWorld.getUID()));
    }
}
