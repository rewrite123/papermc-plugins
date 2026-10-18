package io.github.ihormel.mobbanner;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;

final class WardPyramid {
    private static final Set<Material> VALUABLE = Set.of(Material.IRON_BLOCK, Material.GOLD_BLOCK,
            Material.EMERALD_BLOCK, Material.DIAMOND_BLOCK, Material.NETHERITE_BLOCK);

    private WardPyramid() {}

    static boolean powered(Banner banner) {
        if (banner.getType().name().contains("_WALL_")) return false;
        World world = banner.getWorld();
        int x = banner.getX();
        int y = banner.getY();
        int z = banner.getZ();
        if (y - 3 < world.getMinHeight()) return false;
        for (int depth = 1; depth <= 3; depth++) {
            int radius = depth - 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int bx = x + dx;
                    int bz = z + dz;
                    if (!world.isChunkLoaded(bx >> 4, bz >> 4)
                            || !VALUABLE.contains(world.getBlockAt(bx, y - depth, bz).getType())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
