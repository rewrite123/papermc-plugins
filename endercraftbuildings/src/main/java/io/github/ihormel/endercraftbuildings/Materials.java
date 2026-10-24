package io.github.ihormel.endercraftbuildings;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.SeaPickle;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.data.type.TurtleEgg;

/** Decides which blocks can be copied and which items they cost. */
final class Materials {
    private static final Set<String> UNOBTAINABLE = Set.of("BEDROCK", "BARRIER", "LIGHT", "COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW",
            "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "END_PORTAL_FRAME",
            "END_PORTAL", "NETHER_PORTAL", "END_GATEWAY", "REINFORCED_DEEPSLATE", "BUDDING_AMETHYST",
            "PETRIFIED_OAK_SLAB", "FROGSPAWN", "CHORUS_PLANT", "MOVING_PISTON", "PISTON_HEAD", "FIRE",
            "SOUL_FIRE", "WATER", "LAVA", "BUBBLE_COLUMN", "POWDER_SNOW");
    private static final Set<Material> FILLED_CAULDRONS = Set.of(Material.WATER_CAULDRON, Material.LAVA_CAULDRON,
            Material.POWDER_SNOW_CAULDRON);
    private static final Set<Material> UNDERWATER_PLANTS = Set.of(Material.SEAGRASS, Material.TALL_SEAGRASS,
            Material.KELP, Material.KELP_PLANT, Material.BUBBLE_COLUMN);

    private final Server server;
    private final BlockRules rules;

    Materials(Server server, BlockRules rules) {
        this.server = server;
        this.rules = rules;
    }

    static boolean unobtainable(Material material) {
        return UNOBTAINABLE.contains(material.name()) || material.name().startsWith("INFESTED_");
    }

    /**
     * Returns the copyable form of a block, or {@code null} if it must be skipped.
     * Fluids, filled cauldron contents and extended piston arms are never duplicated.
     */
    BlockData normalize(BlockData original) {
        Material type = original.getMaterial();
        if (type.isAir() || unobtainable(type)) return null;
        BlockData data = FILLED_CAULDRONS.contains(type) ? server.createBlockData(Material.CAULDRON) : original.clone();
        if (data instanceof Waterlogged waterlogged) waterlogged.setWaterlogged(false);
        if (data instanceof Piston piston) piston.setExtended(false);
        Material item = item(data);
        if (item == null || item.isAir() || !item.isItem() || unobtainable(item)) return null;
        return data;
    }

    Material item(BlockData data) {
        Material type = data.getMaterial();
        if (type == Material.FARMLAND || type == Material.DIRT_PATH) return Material.DIRT;
        return rules.placementMaterial(data);
    }

    int count(BlockData data) {
        if (data instanceof Bed bed) return bed.getPart() == Bed.Part.HEAD ? 0 : 1;
        if (data instanceof Bisected half && !(data instanceof Stairs) && !(data instanceof TrapDoor)) {
            return half.getHalf() == Bisected.Half.TOP ? 0 : 1;
        }
        if (data instanceof Slab slab) return slab.getType() == Slab.Type.DOUBLE ? 2 : 1;
        if (data instanceof Candle candle) return candle.getCandles();
        if (data instanceof SeaPickle pickle) return pickle.getPickles();
        if (data instanceof TurtleEgg egg) return egg.getEggs();
        if (data instanceof Snow snow) return snow.getLayers();
        return 1;
    }

    Map<Material, Integer> cost(Blueprint blueprint) {
        var perEntry = new int[blueprint.palette().size()];
        for (int cell : blueprint.cells()) if (cell != Blueprint.EMPTY) perEntry[cell]++;
        var total = new TreeMap<Material, Integer>();
        for (int i = 0; i < perEntry.length; i++) {
            if (perEntry[i] == 0) continue;
            BlockData data = server.createBlockData(blueprint.palette().get(i));
            int each = count(data);
            if (each > 0) total.merge(item(data), each * perEntry[i], Integer::sum);
        }
        return total;
    }

    /** Space is free when it holds air or a dry replaceable plant/snow layer. */
    static boolean free(org.bukkit.block.Block block) {
        if (block.isEmpty()) return true;
        if (block.isLiquid() || UNDERWATER_PLANTS.contains(block.getType())) return false;
        if (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) return false;
        return block.isReplaceable();
    }
}
