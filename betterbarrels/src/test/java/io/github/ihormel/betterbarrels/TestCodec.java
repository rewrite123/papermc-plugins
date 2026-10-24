package io.github.ihormel.betterbarrels;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

final class TestCodec {
    private final Map<Integer, ItemStack> items = new HashMap<>();

    byte[] encode(ItemStack item) {
        int id = items.size();
        items.put(id, item.clone());
        return ByteBuffer.allocate(4).putInt(id).array();
    }

    ItemStack decode(byte[] bytes) {
        return items.get(ByteBuffer.wrap(bytes).getInt()).clone();
    }
}
