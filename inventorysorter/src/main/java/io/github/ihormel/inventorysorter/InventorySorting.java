package io.github.ihormel.inventorysorter;

import java.util.Arrays;
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
        ItemStack[] occupied = Arrays.stream(contents)
                .filter(item -> !empty(item))
                .sorted(ORDER)
                .toArray(ItemStack[]::new);
        return Arrays.copyOf(occupied, contents.length);
    }

    public static void sort(Inventory inventory, int first, int end) {
        if (first < 0 || end > inventory.getSize() || first >= end) {
            throw new IllegalArgumentException("Invalid inventory sorting range");
        }
        ItemStack[] contents = new ItemStack[end - first];
        for (int slot = first; slot < end; slot++) {
            contents[slot - first] = inventory.getItem(slot);
        }
        ItemStack[] sorted = sorted(contents);
        for (int slot = first; slot < end; slot++) {
            inventory.setItem(slot, sorted[slot - first]);
        }
    }
}
