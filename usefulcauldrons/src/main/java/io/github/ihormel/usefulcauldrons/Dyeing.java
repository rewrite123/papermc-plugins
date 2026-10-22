package io.github.ihormel.usefulcauldrons;

import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

final class Dyeing {
    private static final List<String> FAMILIES = List.of("WOOL", "CARPET", "BED", "BANNER",
            "CONCRETE", "CONCRETE_POWDER", "TERRACOTTA", "STAINED_GLASS", "STAINED_GLASS_PANE",
            "SHULKER_BOX", "CANDLE", "BUNDLE", "HARNESS");

    static DyeColor dye(Material material) {
        if (!material.name().endsWith("_DYE")) return null;
        return DyeColor.valueOf(material.name().substring(0, material.name().length() - 4));
    }

    static ItemStack transform(ItemStack original, DyeColor color) {
        if (original.getItemMeta() instanceof LeatherArmorMeta) {
            var result = original.clone();
            result.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color == null ? null : color.getColor()));
            return result.isSimilar(original) ? null : result;
        }
        if (original.getType().name().endsWith("_CONCRETE_POWDER")) {
            String name = original.getType().name().replace("_POWDER", "");
            if (color != null) name = color.name() + "_CONCRETE";
            var result = original.clone();
            result.setType(Material.valueOf(name));
            return result;
        }
        if (color == null) return null;
        String base = original.getType().name();
        for (var old : DyeColor.values()) {
            if (base.startsWith(old.name() + "_")) {
                base = base.substring(old.name().length() + 1);
                break;
            }
        }
        if (base.equals("GLASS")) base = "STAINED_GLASS";
        if (base.equals("GLASS_PANE")) base = "STAINED_GLASS_PANE";
        if (!FAMILIES.contains(base)) return null;
        var material = Material.matchMaterial(color.name() + "_" + base);
        if (material == null || material == original.getType()) return null;
        var result = original.clone();
        result.setType(material);
        return result;
    }
}
