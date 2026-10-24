package io.github.ihormel.endercraftbuildings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** Converts world regions into blueprints and blueprints back into world placements. */
final class Buildings {
    record Scan(Blueprint blueprint, int skipped) {}

    record Target(Block block, BlockData data) {}

    private final Server server;
    private final BlockRules rules;
    private final Materials materials;

    Buildings(Server server, BlockRules rules, Materials materials) {
        this.server = server;
        this.rules = rules;
        this.materials = materials;
    }

    static boolean withinLimit(Block a, Block b) {
        return Math.abs(a.getX() - b.getX()) < Blueprint.MAX_SIZE && Math.abs(a.getY() - b.getY()) < Blueprint.MAX_SIZE
                && Math.abs(a.getZ() - b.getZ()) < Blueprint.MAX_SIZE;
    }

    /** Scans the box between two corners as seen by a player facing {@code facing}. */
    Scan scan(Block first, Block second, Facing facing) {
        if (!first.getWorld().equals(second.getWorld()) || !withinLimit(first, second)) {
            throw new IllegalArgumentException("Selection too large");
        }
        World world = first.getWorld();
        int minX = Math.min(first.getX(), second.getX()), maxX = Math.max(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY()), maxY = Math.max(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ()), maxZ = Math.max(first.getZ(), second.getZ());
        Facing right = facing.right();
        int originX = right.dx == -1 || facing.dx == -1 ? maxX : minX;
        int originZ = right.dz == -1 || facing.dz == -1 ? maxZ : minZ;
        int spanX = maxX - minX + 1, spanZ = maxZ - minZ + 1;
        int width = right.dx != 0 ? spanX : spanZ;
        int length = right.dx != 0 ? spanZ : spanX;
        int height = maxY - minY + 1;
        var palette = new ArrayList<String>();
        var lookup = new HashMap<String, Integer>();
        int[] cells = new int[width * height * length];
        int skipped = 0;
        int toNorth = 4 - facing.steps();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    Block block = world.getBlockAt(originX + right.dx * x + facing.dx * z, minY + y,
                            originZ + right.dz * x + facing.dz * z);
                    int index = (y * length + z) * width + x;
                    cells[index] = Blueprint.EMPTY;
                    BlockData original = block.getBlockData();
                    if (original.getMaterial().isAir()) continue;
                    BlockData data = materials.normalize(original);
                    if (data == null) {
                        skipped++;
                        continue;
                    }
                    rules.rotate(data, toNorth);
                    String key = data.getAsString();
                    cells[index] = lookup.computeIfAbsent(key, value -> {
                        palette.add(value);
                        return palette.size() - 1;
                    });
                }
            }
        }
        return new Scan(new Blueprint(width, height, length, palette, cells), skipped);
    }

    /** World positions and rotated block data for a building whose front-left-bottom corner is {@code anchor}. */
    List<Target> plan(Blueprint blueprint, Block anchor, Facing facing) {
        Facing right = facing.right();
        var rotated = new HashMap<Integer, BlockData>();
        var targets = new ArrayList<Target>(blueprint.blockCount());
        for (int y = 0; y < blueprint.height(); y++) {
            for (int z = 0; z < blueprint.length(); z++) {
                for (int x = 0; x < blueprint.width(); x++) {
                    int cell = blueprint.cell(x, y, z);
                    if (cell == Blueprint.EMPTY) continue;
                    BlockData data = rotated.computeIfAbsent(cell, entry -> {
                        BlockData value = server.createBlockData(blueprint.palette().get(entry));
                        rules.rotate(value, facing.steps());
                        return value;
                    });
                    Block block = anchor.getRelative(right.dx * x + facing.dx * z, y, right.dz * x + facing.dz * z);
                    targets.add(new Target(block, data));
                }
            }
        }
        return targets;
    }

    /** Opposite corners of the placement box, for outlining. */
    static Block[] corners(Blueprint blueprint, Block anchor, Facing facing) {
        Facing right = facing.right();
        int x = blueprint.width() - 1, z = blueprint.length() - 1;
        return new Block[] {anchor, anchor.getRelative(right.dx * x + facing.dx * z, blueprint.height() - 1,
                right.dz * x + facing.dz * z)};
    }

    static boolean inWorld(Blueprint blueprint, Block anchor) {
        World world = anchor.getWorld();
        return anchor.getY() >= world.getMinHeight() && anchor.getY() + blueprint.height() <= world.getMaxHeight();
    }

    /** Number of target positions that are not free, ignoring {@code ignore} (the placed crystal itself). */
    static int obstructions(List<Target> targets, Block ignore) {
        int blocked = 0;
        for (Target target : targets) {
            if (!target.block().equals(ignore) && !Materials.free(target.block())) blocked++;
        }
        return blocked;
    }

    Map<org.bukkit.Material, Integer> cost(Blueprint blueprint) { return materials.cost(blueprint); }
}
