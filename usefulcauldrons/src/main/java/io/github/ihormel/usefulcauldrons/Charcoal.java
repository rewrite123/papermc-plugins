package io.github.ihormel.usefulcauldrons;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Tag;

final class Charcoal {
    record Ratio(int input, int output) {}
    record Batch(Map<Material, Integer> costs, int output) {}

    static Ratio ratio(Material material) {
        if (Tag.LOGS.isTagged(material)) return new Ratio(1, 2);
        if (Tag.PLANKS.isTagged(material)) return new Ratio(2, 1);
        if (Tag.WOODEN_STAIRS.isTagged(material)) return new Ratio(2, 1);
        if (Tag.WOODEN_SLABS.isTagged(material)) return new Ratio(4, 1);
        if (Tag.WOODEN_FENCES.isTagged(material) || Tag.FENCE_GATES.isTagged(material)) return new Ratio(2, 1);
        if (Tag.WOODEN_DOORS.isTagged(material) || Tag.WOODEN_TRAPDOORS.isTagged(material)) return new Ratio(2, 1);
        if (Tag.LEAVES.isTagged(material) || Tag.FLOWERS.isTagged(material)
                || Tag.SAPLINGS.isTagged(material)) return new Ratio(4, 1);
        return switch (material) {
            case STICK, BAMBOO, SUGAR_CANE, PAPER, VINE, SHORT_GRASS, FERN, DEAD_BUSH,
                    HANGING_ROOTS, MANGROVE_ROOTS, BROWN_MUSHROOM, RED_MUSHROOM, WHEAT,
                    KELP, DRIED_KELP, CARROT, POTATO, BEETROOT, SWEET_BERRIES, GLOW_BERRIES ->
                    new Ratio(4, 1);
            case WHEAT_SEEDS, MELON_SEEDS, PUMPKIN_SEEDS, BEETROOT_SEEDS, TORCHFLOWER_SEEDS ->
                    new Ratio(8, 1);
            default -> null;
        };
    }

    static Batch batch(CauldronContents contents) {
        var costs = new EnumMap<Material, Integer>(Material.class);
        int output = 0;
        for (var entry : contents.ingredients.entrySet()) {
            var ratio = ratio(entry.getKey());
            if (ratio == null) continue;
            int batches = entry.getValue() / ratio.input();
            if (batches > 0) {
                costs.put(entry.getKey(), batches * ratio.input());
                output += batches * ratio.output();
            }
        }
        return new Batch(costs, output);
    }
}
