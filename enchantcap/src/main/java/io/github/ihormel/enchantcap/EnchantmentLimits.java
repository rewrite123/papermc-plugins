package io.github.ihormel.enchantcap;

import java.util.Map;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

final class EnchantmentLimits {
    static Map<Enchantment, Integer> levels(ItemStack item) {
        if (item == null || item.isEmpty()) return Map.of();
        return item.getItemMeta() instanceof EnchantmentStorageMeta book
                ? book.getStoredEnchants() : item.getEnchantments();
    }

    static int bounded(int level, int vanillaMaximum, int additional, int existing) {
        return Math.max(Math.min(level, Math.min(255, vanillaMaximum + additional)), existing);
    }

    static ItemStack restrict(ItemStack result, ItemStack first, int additional) {
        var existing = levels(first);
        var resultLevels = levels(result);
        boolean needsChange = resultLevels.entrySet().stream().anyMatch(entry ->
                entry.getValue() != bounded(entry.getValue(), entry.getKey().getMaxLevel(), additional,
                        existing.getOrDefault(entry.getKey(), 0)));
        if (!needsChange) return result;
        var adjusted = result.clone();
        adjusted.editMeta(meta -> {
            for (var entry : resultLevels.entrySet()) {
                int level = bounded(entry.getValue(), entry.getKey().getMaxLevel(), additional,
                        existing.getOrDefault(entry.getKey(), 0));
                if (entry.getValue() == level) continue;
                if (meta instanceof EnchantmentStorageMeta book) book.addStoredEnchant(entry.getKey(), level, true);
                else meta.addEnchant(entry.getKey(), level, true);
            }
        });
        return adjusted;
    }
}
