package io.github.ihormel.betterbarrels;

import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class BarrelStorage {
    static final NamespacedKey COUNT = new NamespacedKey("betterbarrels", "count");
    static final NamespacedKey ITEM = new NamespacedKey("betterbarrels", "item");
    private final Function<byte[], ItemStack> decode;
    private final Function<ItemStack, byte[]> encode;

    BarrelStorage(Function<byte[], ItemStack> decode, Function<ItemStack, byte[]> encode) {
        this.decode = decode;
        this.encode = encode;
    }

    boolean managed(Barrel barrel) {
        return barrel.getPersistentDataContainer().has(COUNT);
    }

    Stock read(Barrel barrel) {
        var pdc = barrel.getPersistentDataContainer();
        if (!managed(barrel)) return new Stock(null, 0);
        Integer count = pdc.get(COUNT, PersistentDataType.INTEGER);
        byte[] item = pdc.get(ITEM, PersistentDataType.BYTE_ARRAY);
        if (count == null || (count > 0 && item == null)) {
            throw new IllegalStateException("Corrupt BetterBarrels data at " + barrel.getLocation());
        }
        return new Stock(item == null ? null : decode.apply(item), count);
    }

    void write(Barrel barrel, Stock stock) {
        var pdc = barrel.getPersistentDataContainer();
        pdc.set(COUNT, PersistentDataType.INTEGER, stock.count());
        if (stock.count() == 0) pdc.remove(ITEM);
        else pdc.set(ITEM, PersistentDataType.BYTE_ARRAY, encode.apply(stock.type()));
        if (!barrel.update(false, false)) {
            throw new IllegalStateException("Cannot save BetterBarrels data at " + barrel.getLocation());
        }
    }
}
