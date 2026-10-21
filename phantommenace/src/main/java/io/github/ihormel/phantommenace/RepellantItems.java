package io.github.ihormel.phantommenace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

final class RepellantItems {
    static final NamespacedKey MARKER = new NamespacedKey("phantommenace", "repellant");

    static ItemStack create() {
        var item = new ItemStack(Material.LAPIS_LAZULI);
        item.editMeta(meta -> {
            meta.itemName(Component.text("Phantom Repellant", NamedTextColor.AQUA));
            meta.setItemModel(Material.CHARCOAL.getKey());
            meta.lore(java.util.List.of(Component.text("Enchant a helmet with Phantom Menace.", NamedTextColor.GRAY),
                    Component.text("Use in an enchanting table's lapis slot.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    static boolean isRepellant(ItemStack item) {
        return item != null && item.getType() == Material.LAPIS_LAZULI && item.hasItemMeta()
                && Byte.valueOf((byte) 1).equals(item.getItemMeta().getPersistentDataContainer()
                .get(MARKER, PersistentDataType.BYTE));
    }

    static RecipeChoice waterInput() {
        var water = new ItemStack(Material.POTION);
        water.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.WATER));
        return new RecipeChoice.ExactChoice(water);
    }
}
