package io.github.ihormel.craftablebottleofenchanting;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

final class BottleMenu implements InventoryHolder {
    static final int ACTION_SLOT = 4;
    private final UUID owner;
    private final Location table;
    private final int bottleSlot;
    private final Inventory inventory;

    BottleMenu(UUID owner, Location table, int bottleSlot) {
        this.owner = owner;
        this.table = table.clone();
        this.bottleSlot = bottleSlot;
        inventory = Bukkit.createInventory(this, 9, Component.text("Bottle Experience"));
        ItemStack action = new ItemStack(Material.EXPERIENCE_BOTTLE);
        var meta = action.getItemMeta();
        meta.displayName(Component.text("Bottle Experience"));
        meta.lore(List.of(Component.text("1 glass bottle + 11 XP points"),
                Component.text("No lapis required. Click to bottle experience.")));
        action.setItemMeta(meta);
        inventory.setItem(ACTION_SLOT, action);
    }

    UUID owner() {
        return owner;
    }

    Location table() {
        return table.clone();
    }

    int bottleSlot() {
        return bottleSlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
