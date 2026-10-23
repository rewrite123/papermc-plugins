package io.github.ihormel.ihatepowderedsnow;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

final class SnowPopulator extends BlockPopulator {
    @Override
    public void populate(WorldInfo world, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        int minimumY = world.getMinHeight();
        int maximumY = world.getMaxHeight();
        for (int x = originX; x < originX + 16; x++) {
            for (int z = originZ; z < originZ + 16; z++) {
                for (int y = minimumY; y < maximumY; y++) {
                    if (region.getType(x, y, z) == Material.POWDER_SNOW) {
                        region.setType(x, y, z, Material.SNOW_BLOCK);
                    }
                }
            }
        }
    }
}
