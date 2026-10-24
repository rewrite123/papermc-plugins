package io.github.ihormel.endercraftbuildings;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/** Server-backed block operations, kept behind an interface so the planning logic can be unit tested. */
interface BlockRules {
    void rotate(BlockData data, int clockwiseSteps);

    Material placementMaterial(BlockData data);

    BlockRules PAPER = new BlockRules() {
        @Override
        public void rotate(BlockData data, int clockwiseSteps) {
            if (Math.floorMod(clockwiseSteps, 4) != 0) data.rotate(Facing.rotation(clockwiseSteps));
        }

        @Override
        public Material placementMaterial(BlockData data) { return data.getPlacementMaterial(); }
    };
}
