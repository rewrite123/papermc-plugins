package io.github.ihormel.soulgem;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

final class EnchantingRules {
    private EnchantingRules() {}

    static int boosted(Enchantment enchantment, int level, boolean echo) {
        return echo ? Math.min(level + 1, enchantment.getMaxLevel() + 1) : level;
    }

    static Map<Enchantment, Integer> enchantments(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Map.of();
        return item.getItemMeta() instanceof EnchantmentStorageMeta book
                ? book.getStoredEnchants() : item.getEnchantments();
    }

    /**
     * The rolled enchantments that actually improve the item: boosted for echo gems,
     * higher than the existing level, and not conflicting with what the item keeps.
     */
    static Map<Enchantment, Integer> improvements(Map<Enchantment, Integer> existing,
                                                  Map<Enchantment, Integer> rolled, boolean echo) {
        var merged = new HashMap<>(existing);
        var added = new LinkedHashMap<Enchantment, Integer>();
        // Key order keeps conflict resolution stable for the same roll.
        var entries = rolled.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(e -> e.getKey().toString())))
                .toList();
        for (var entry : entries) {
            var enchantment = entry.getKey();
            int level = boosted(enchantment, entry.getValue(), echo);
            if (level <= merged.getOrDefault(enchantment, 0)) continue;
            boolean conflict = merged.keySet().stream().anyMatch(other -> !other.equals(enchantment)
                    && (other.conflictsWith(enchantment) || enchantment.conflictsWith(other)));
            if (conflict) continue;
            merged.put(enchantment, level);
            added.put(enchantment, level);
        }
        return added;
    }
}
