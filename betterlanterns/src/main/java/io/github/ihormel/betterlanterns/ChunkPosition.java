package io.github.ihormel.betterlanterns;

import java.util.UUID;
import org.bukkit.Chunk;

record ChunkPosition(UUID world, int x, int z) {
    static ChunkPosition of(Chunk chunk) {
        return new ChunkPosition(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
