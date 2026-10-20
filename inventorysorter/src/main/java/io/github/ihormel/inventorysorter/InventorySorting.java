package io.github.ihormel.inventorysorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventorySorting {
    private static final Comparator<ItemStack> ORDER = Comparator
            .comparing((ItemStack item) -> ItemGroups.group(item.getType()))
            .thenComparing(item -> item.getType().name());

    private InventorySorting() {}

    public static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static ItemStack[] sorted(ItemStack[] contents) {
        return sorted(contents, Integer.MAX_VALUE);
    }

    /**
     * Combines similar stacks (up to their stack size, capped by {@code slotLimit}),
     * then sorts. Untouched stacks are returned as the same instances.
     */
    public static ItemStack[] sorted(ItemStack[] contents, int slotLimit) {
        ItemStack[] occupied = combined(contents, slotLimit).stream()
                .sorted(ORDER)
                .toArray(ItemStack[]::new);
        return Arrays.copyOf(occupied, contents.length);
    }

    private static List<ItemStack> combined(ItemStack[] contents, int slotLimit) {
        int cap = slotLimit > 0 ? slotLimit : Integer.MAX_VALUE;
        List<ItemStack> piles = new ArrayList<>();
        Set<Integer> copied = new HashSet<>();
        for (ItemStack item : contents) {
            if (empty(item)) continue;
            int remaining = item.getAmount();
            for (int i = 0; i < piles.size() && remaining > 0; i++) {
                ItemStack pile = piles.get(i);
                int room = Math.min(pile.getMaxStackSize(), cap) - pile.getAmount();
                if (room <= 0 || !pile.isSimilar(item)) continue;
                if (copied.add(i)) {
                    pile = pile.clone();
                    piles.set(i, pile);
                }
                int moved = Math.min(room, remaining);
                pile.setAmount(pile.getAmount() + moved);
                remaining -= moved;
            }
            if (remaining == item.getAmount()) {
                piles.add(item);
            } else if (remaining > 0) {
                ItemStack rest = item.clone();
                rest.setAmount(remaining);
                copied.add(piles.size());
                piles.add(rest);
            }
        }
        return piles;
    }

    public static void sort(Inventory inventory, int first, int end) {
        if (first < 0 || end > inventory.getSize() || first >= end) {
            throw new IllegalArgumentException("Invalid inventory sorting range");
        }
        ItemStack[] contents = new ItemStack[end - first];
        for (int slot = first; slot < end; slot++) {
            contents[slot - first] = inventory.getItem(slot);
        }
        ItemStack[] sorted = sorted(contents, inventory.getMaxStackSize());
        for (int slot = first; slot < end; slot++) {
            inventory.setItem(slot, sorted[slot - first]);
        }
    }
}
