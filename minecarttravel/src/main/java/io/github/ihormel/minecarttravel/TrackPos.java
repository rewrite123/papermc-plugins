package io.github.ihormel.minecarttravel;

import java.util.UUID;
import org.bukkit.Location;

record TrackPos(UUID world, int x, int y, int z) {
    static TrackPos at(Location location) {
        return new TrackPos(location.getWorld().getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    TrackPos add(int dx, int dy, int dz) {
        return new TrackPos(world, x + dx, y + dy, z + dz);
    }

    ChunkKey chunk() {
        return new ChunkKey(world, x >> 4, z >> 4);
    }

    record ChunkKey(UUID world, int x, int z) {}
}
