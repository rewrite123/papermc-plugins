package io.github.ihormel.enchantcap;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

final class MaterialRepairCost {
    static int materialCount(Material material) {
        var name = material.name();
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) return 3;
        if (name.endsWith("_SWORD") || name.endsWith("_HOE")) return 2;
        if (name.endsWith("_SHOVEL")) return 1;
        if (name.endsWith("_CHESTPLATE")) return 8;
        if (name.endsWith("_LEGGINGS")) return 7;
        if (name.endsWith("_HELMET")) return 5;
        if (name.endsWith("_BOOTS")) return 4;
        return 0;
    }

    static int charge(int cappedCost, ItemStack first, ItemStack second, ItemStack result, int consumed) {
        if (cappedCost <= 0 || first == null || second == null || consumed <= 0
                || first.getType() == second.getType() || first.getType() != result.getType()) return cappedCost;
        int materials = materialCount(first.getType());
        if (materials < 2 || !first.isRepairableBy(second)
                || !(first.getItemMeta() instanceof Damageable before)
                || !(result.getItemMeta() instanceof Damageable after)
                || after.getDamage() >= before.getDamage()) return cappedCost;
        return (int) (((long) cappedCost * Math.min(consumed, materials) + materials - 1) / materials);
    }
}
