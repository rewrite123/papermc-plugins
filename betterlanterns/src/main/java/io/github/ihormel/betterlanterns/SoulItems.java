package io.github.ihormel.betterlanterns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class SoulItems {
    private final NamespacedKey key;

    SoulItems(NamespacedKey key) { this.key = key; }

    ItemStack candle(int amount) {
        var item = new ItemStack(Material.CANDLE, amount);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Soul Candle", NamedTextColor.AQUA));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    boolean isSoul(ItemStack item) {
        return item != null && item.getType() == Material.CANDLE
                && Byte.valueOf((byte) 1).equals(item.getPersistentDataContainer().get(key, PersistentDataType.BYTE));
    }
}
