package io.github.ihormel.inventorysorter;

import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

final class SortTargets {
    record Target(Inventory inventory, int first, int end) {
        void sort() {
            InventorySorting.sort(inventory, first, end);
        }
    }

    private SortTargets() {}

    static Target player(Player player) {
        return new Target(player.getInventory(), 9, 36);
    }

    static Target container(Player player, InventoryView view) {
        Inventory top = view.getTopInventory();
        if (top == player.getEnderChest() && top.getType() == InventoryType.ENDER_CHEST) {
            return new Target(top, 0, top.getSize());
        }
        Object holder = top.getHolder(false);
        boolean supported = switch (top.getType()) {
            case CHEST -> holder instanceof Chest || holder instanceof DoubleChest;
            case BARREL -> holder instanceof Barrel;
            case SHULKER_BOX -> holder instanceof ShulkerBox;
            default -> false;
        };
        return supported ? new Target(top, 0, top.getSize()) : null;
    }

    static boolean supportedView(Player player, InventoryView view) {
        return container(player, view) != null
                || ((view.getType() == InventoryType.CRAFTING || view.getType() == InventoryType.PLAYER)
                    && view.getBottomInventory() == player.getInventory());
    }

    static Target clicked(Player player, InventoryView view, Inventory clicked, int slot) {
        if (!supportedView(player, view)) return null;
        if (clicked == player.getInventory()) {
            return slot >= 9 && slot < 36 ? player(player) : null;
        }
        Target target = container(player, view);
        return target != null && clicked == target.inventory() && slot >= 0 && slot < target.end()
                ? target : null;
    }
}
