package io.github.ihormel.endercraftbuildings;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

/** Stand-in for the server-only rotate/placement-material calls that MockBukkit does not implement. */
final class TestRules implements BlockRules {
    private static final BlockFace[] ORDER = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    @Override
    public void rotate(BlockData data, int clockwiseSteps) {
        if (!(data instanceof Directional directional)) return;
        for (int i = 0; i < 4; i++) {
            if (ORDER[i] == directional.getFacing()) {
                directional.setFacing(ORDER[Math.floorMod(i + clockwiseSteps, 4)]);
                return;
            }
        }
    }

    @Override
    public Material placementMaterial(BlockData data) {
        return switch (data.getMaterial()) {
            case WALL_TORCH -> Material.TORCH;
            case REDSTONE_WIRE -> Material.REDSTONE;
            default -> data.getMaterial();
        };
    }
}
