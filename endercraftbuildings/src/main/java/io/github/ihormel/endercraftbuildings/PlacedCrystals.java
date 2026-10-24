package io.github.ihormel.endercraftbuildings;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Storage crystals placed in the world as amethyst clusters, kept in the owning chunk's persistent data. */
final class PlacedCrystals {
    record Placed(byte[] blueprint, String name, int count, Facing facing) {}

    private final Plugin plugin;
    private final NamespacedKey blueprintKey;
    private final NamespacedKey nameKey;
    private final NamespacedKey countKey;
    private final NamespacedKey facingKey;

    PlacedCrystals(Plugin plugin) {
        this.plugin = plugin;
        blueprintKey = new NamespacedKey(plugin, "blueprint");
        nameKey = new NamespacedKey(plugin, "building_name");
        countKey = new NamespacedKey(plugin, "block_count");
        facingKey = new NamespacedKey(plugin, "facing");
    }

    private NamespacedKey key(Block block) { return new NamespacedKey(plugin, "crystal_" + TaperRecords.pack(block)); }

    /** The crystal at {@code block}, or {@code null}; records whose cluster has vanished are forgotten. */
    Placed get(Block block) {
        var chunk = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = key(block);
        PersistentDataContainer data = chunk.get(key, PersistentDataType.TAG_CONTAINER);
        if (data == null) return null;
        if (block.getType() != Material.AMETHYST_CLUSTER) {
            chunk.remove(key);
            return null;
        }
        byte[] blueprint = data.get(blueprintKey, PersistentDataType.BYTE_ARRAY);
        if (blueprint == null) return null;
        Facing facing;
        try {
            facing = Facing.valueOf(data.getOrDefault(facingKey, PersistentDataType.STRING, Facing.NORTH.name()));
        } catch (IllegalArgumentException exception) {
            facing = Facing.NORTH;
        }
        return new Placed(blueprint, data.getOrDefault(nameKey, PersistentDataType.STRING, "Building"),
                data.getOrDefault(countKey, PersistentDataType.INTEGER, 0), facing);
    }

    void put(Block block, Placed placed) {
        var chunk = block.getChunk().getPersistentDataContainer();
        PersistentDataContainer data = chunk.getAdapterContext().newPersistentDataContainer();
        data.set(blueprintKey, PersistentDataType.BYTE_ARRAY, placed.blueprint());
        data.set(nameKey, PersistentDataType.STRING, placed.name());
        data.set(countKey, PersistentDataType.INTEGER, placed.count());
        data.set(facingKey, PersistentDataType.STRING, placed.facing().name());
        chunk.set(key(block), PersistentDataType.TAG_CONTAINER, data);
    }

    void remove(Block block) { block.getChunk().getPersistentDataContainer().remove(key(block)); }
}
