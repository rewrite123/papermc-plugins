package io.github.ihormel.soulgem;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

class EnchantingOffers {
    record Offer(int requiredLevel, int cost, ItemStack result) {}

    @FunctionalInterface
    interface Roller {
        ItemStack roll(ItemStack item, int level, int seed);
    }

    private final Roller roller;

    EnchantingOffers() {
        this((item, level, seed) -> item.enchantWithLevels(level,
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                        .getTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE),
                new Random(seed)));
    }

    EnchantingOffers(Roller roller) {
        this.roller = roller;
    }

    List<Offer> create(ItemStack input, GemItems.Kind kind, int shelves, int seed) {
        var random = new Random(seed);
        var offers = new ArrayList<Offer>();
        for (int index = 0; index < 3; index++) {
            int required = requiredLevel(random, shelves, index);
            ItemStack result = null;
            if (input.getAmount() == 1 && required >= index + 1) {
                ItemStack clean = input.clone();
                clean.removeEnchantments();
                if (clean.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                    for (var enchantment : meta.getStoredEnchants().keySet()) {
                        meta.removeStoredEnchant(enchantment);
                    }
                    clean.setItemMeta(meta);
                    clean = clean.withType(Material.BOOK);
                }
                ItemStack rolled = roller.roll(clean, required, seed + index);
                result = merge(input, rolled, kind == GemItems.Kind.ECHO);
            }
            offers.add(new Offer(required, index + 1, result));
        }
        return List.copyOf(offers);
    }

    static int requiredLevel(Random random, int shelves, int index) {
        int power = Math.clamp(shelves, 0, 15);
        int base = random.nextInt(8) + 1 + (power >> 1) + random.nextInt(power + 1);
        return switch (index) {
            case 0 -> Math.max(base / 3, 1);
            case 1 -> base * 2 / 3 + 1;
            case 2 -> Math.max(base, power * 2);
            default -> throw new IllegalArgumentException("Offer index must be 0, 1, or 2");
        };
    }

    static Map<Enchantment, Integer> enchantments(ItemStack item) {
        return item.getItemMeta() instanceof EnchantmentStorageMeta book
                ? book.getStoredEnchants() : item.getEnchantments();
    }

    static ItemStack merge(ItemStack input, ItemStack rolled, boolean echo) {
        var existing = enchantments(input);
        var merged = new HashMap<>(existing);
        // Registry order makes conflict resolution stable across menu reopens.
        var entries = enchantments(rolled).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(e -> e.getKey().toString())))
                .toList();
        for (var entry : entries) {
            var enchantment = entry.getKey();
            int level = entry.getValue();
            if (echo) level = Math.min(level + 1, enchantment.getMaxLevel() + 1);
            boolean conflict = merged.keySet().stream().anyMatch(other -> !other.equals(enchantment)
                    && (other.conflictsWith(enchantment) || enchantment.conflictsWith(other)));
            if (!conflict) merged.merge(enchantment, level, Math::max);
        }
        if (merged.equals(existing)) return null;
        ItemStack result = input.getType() == Material.BOOK ? rolled.clone() : input.clone();
        var meta = result.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta book) {
            for (var entry : merged.entrySet()) {
                book.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
        } else {
            for (var entry : merged.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        result.setItemMeta(meta);
        return result;
    }

    int bookshelves(Location table) {
        int count = 0;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                for (int y = 0; y <= 1; y++) {
                    var gap = table.clone().add(x / 2, y, z / 2);
                    if (!loaded(gap)
                            || !Tag.ENCHANTMENT_POWER_TRANSMITTER.isTagged(gap.getBlock().getType())) continue;
                    var shelf = table.clone().add(x, y, z);
                    if (loaded(shelf) && Tag.ENCHANTMENT_POWER_PROVIDER.isTagged(shelf.getBlock().getType())) {
                        count++;
                    }
                }
            }
        }
        return Math.min(count, 15);
    }

    private boolean loaded(Location location) {
        return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
