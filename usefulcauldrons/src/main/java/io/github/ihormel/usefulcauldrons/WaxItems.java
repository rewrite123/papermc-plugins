package io.github.ihormel.usefulcauldrons;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class WaxItems {
    static final NamespacedKey MARKER = new NamespacedKey("usefulcauldrons", "wax");

    static ItemStack create(int amount) {
        var wax = new ItemStack(Material.HONEYCOMB, amount);
        wax.editMeta(meta -> {
            meta.itemName(Component.text("Wax", NamedTextColor.WHITE));
            meta.setItemModel(Material.WHITE_DYE.getKey());
            meta.lore(List.of(Component.text("Use like honeycomb to craft candles or wax copper.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
        });
        return wax;
    }
}
