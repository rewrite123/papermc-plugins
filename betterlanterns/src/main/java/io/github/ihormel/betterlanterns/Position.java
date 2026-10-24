package io.github.ihormel.betterlanterns;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

record Position(UUID world, int x, int y, int z) {
    static Position of(Block block) {
        return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    Position offset(int dx, int dy, int dz) { return new Position(world, x + dx, y + dy, z + dz); }
    ChunkPosition chunk() { return new ChunkPosition(world, x >> 4, z >> 4); }
    Block block(World world) { return world.getBlockAt(x, y, z); }
    Location center(World world) { return new Location(world, x + .5, y + .5, z + .5); }
}
