package io.github.ihormel.usefulcauldrons;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.DyeColor;
import org.bukkit.Material;

final class CauldronContents {
    static final int CAPACITY = 4096;
    final Map<Material, Integer> ingredients = new EnumMap<>(Material.class);
    DyeColor dye;
    int uses;
    String food;
    int servings;
    int charcoalTicks;

    int count(Material material) {
        return ingredients.getOrDefault(material, 0);
    }

    int size() {
        return ingredients.values().stream().mapToInt(Integer::intValue).sum();
    }

    int deposit(Material material, int amount) {
        if (amount < 1) throw new IllegalArgumentException("Deposit amount must be positive");
        int accepted = Math.min(amount, CAPACITY - size());
        if (accepted > 0) ingredients.merge(material, accepted, Integer::sum);
        return accepted;
    }

    void consume(Map<Material, Integer> costs) {
        if (costs.entrySet().stream().anyMatch(e -> count(e.getKey()) < e.getValue() || e.getValue() < 1)) {
            throw new IllegalArgumentException("Insufficient cauldron ingredients");
        }
        costs.forEach((material, amount) -> {
            int left = count(material) - amount;
            if (left == 0) ingredients.remove(material);
            else ingredients.put(material, left);
        });
    }

    void useDye(int amount) {
        if (amount < 1 || amount > uses) throw new IllegalArgumentException("Insufficient dye");
        uses -= amount;
        if (uses == 0) dye = null;
    }

    boolean hasItems() {
        return !ingredients.isEmpty() || servings > 0;
    }
}
