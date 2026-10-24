package io.github.ihormel.betterbarrels;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

final class Stock {
    static final int CAPACITY = 2048;
    private ItemStack type;
    private int count;

    Stock(ItemStack type, int count) {
        if (count < 0 || count > CAPACITY || (count > 0 && (type == null || type.isEmpty()))) {
            throw new IllegalArgumentException("Invalid BetterBarrels item count or item type");
        }
        this.type = count == 0 ? null : type.asOne();
        this.count = count;
    }

    ItemStack type() {
        return type == null ? null : type.clone();
    }

    int count() {
        return count;
    }

    int insert(ItemStack offered, int requested) {
        if (offered == null || offered.isEmpty() || requested <= 0
                || (type != null && !type.isSimilar(offered))) return 0;
        int accepted = Math.min(Math.min(offered.getAmount(), requested), CAPACITY - count);
        if (accepted > 0) {
            if (type == null) type = offered.asOne();
            count += accepted;
        }
        return accepted;
    }

    ItemStack take(int requested) {
        if (count == 0 || requested <= 0) return null;
        int amount = Math.min(count, Math.min(requested, type.getMaxStackSize()));
        ItemStack result = type.asQuantity(amount);
        count -= amount;
        if (count == 0) type = null;
        return result;
    }

    List<ItemStack> stacks() {
        List<ItemStack> result = new ArrayList<>();
        if (type == null) return result;
        for (int remaining = count; remaining > 0;) {
            int amount = Math.min(remaining, type.getMaxStackSize());
            result.add(type.asQuantity(amount));
            remaining -= amount;
        }
        return result;
    }
}
