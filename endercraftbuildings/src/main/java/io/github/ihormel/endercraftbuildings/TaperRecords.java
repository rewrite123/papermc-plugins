package io.github.ihormel.endercraftbuildings;

import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

/** Remembers which vault blocks are ender biconical tapers, in the owning chunk's persistent data. */
final class TaperRecords {
    private final NamespacedKey key;

    TaperRecords(NamespacedKey key) { this.key = key; }

    static int pack(Block block) { return block.getY() << 8 | (block.getZ() & 15) << 4 | (block.getX() & 15); }

    private int[] read(Block block) {
        return block.getChunk().getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER_ARRAY,
                new int[0]);
    }

    private void write(Block block, int[] values) {
        var pdc = block.getChunk().getPersistentDataContainer();
        if (values.length == 0) pdc.remove(key);
        else pdc.set(key, PersistentDataType.INTEGER_ARRAY, values);
    }

    boolean contains(Block block) {
        int packed = pack(block);
        if (Arrays.stream(read(block)).noneMatch(value -> value == packed)) return false;
        if (block.getType() == Material.VAULT) return true;
        remove(block);
        return false;
    }

    void add(Block block) {
        int packed = pack(block);
        int[] values = read(block);
        if (Arrays.stream(values).anyMatch(value -> value == packed)) return;
        int[] next = Arrays.copyOf(values, values.length + 1);
        next[values.length] = packed;
        write(block, next);
    }

    /** Every recorded taper block in a chunk. */
    java.util.List<Block> blocks(org.bukkit.Chunk chunk) {
        int[] values = chunk.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER_ARRAY,
                new int[0]);
        var result = new java.util.ArrayList<Block>(values.length);
        for (int value : values) result.add(chunk.getBlock(value & 15, value >> 8, value >> 4 & 15));
        return result;
    }

    void remove(Block block) {
        int packed = pack(block);
        write(block, Arrays.stream(read(block)).filter(value -> value != packed).toArray());
    }
}
