package io.github.ihormel.tpcost;

import java.util.Objects;
import org.bukkit.Location;

public final class TeleportCost {
    private TeleportCost() {
    }

    public static int levels(double distance, boolean crossWorld) {
        return levels(distance, crossWorld, TeleportPricing.DEFAULT);
    }

    static int levels(double distance, boolean crossWorld, TeleportPricing pricing) {
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Distance must be finite and non-negative");
        }
        double distanceCost = pricing.levelsPer1000Blocks() == 0 ? 0
                : Math.ceil(distance * pricing.levelsPer1000Blocks() / 1000.0);
        int base = (int) Math.min(pricing.maximumCost(), Math.max(pricing.minimumCost(), distanceCost));
        return base + (crossWorld ? pricing.crossWorldSurcharge() : 0);
    }

    public static int between(Location from, Location to) {
        return between(from, to, TeleportPricing.DEFAULT);
    }

    static int between(Location from, Location to, TeleportPricing pricing) {
        var fromWorld = Objects.requireNonNull(from.getWorld(), "Source world");
        var toWorld = Objects.requireNonNull(to.getWorld(), "Destination world");
        double distance = Math.hypot(
                Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ()),
                from.getY() - to.getY());
        return levels(distance, !fromWorld.getUID().equals(toWorld.getUID()), pricing);
    }
}
