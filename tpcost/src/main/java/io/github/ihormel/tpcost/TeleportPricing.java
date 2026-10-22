package io.github.ihormel.tpcost;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

record TeleportPricing(int minimumCost, int levelsPer1000Blocks, int maximumCost, int crossWorldSurcharge) {
    static final TeleportPricing DEFAULT = new TeleportPricing(3, 3, 25, 3);
    static final List<String> SETTINGS = List.of("minimum-cost", "levels-per-1000-blocks",
            "maximum-cost", "cross-world-surcharge");

    TeleportPricing {
        if (minimumCost < 0 || levelsPer1000Blocks < 0 || maximumCost < 0 || crossWorldSurcharge < 0) {
            throw new IllegalArgumentException("Prices must be non-negative whole numbers.");
        }
        if (minimumCost > maximumCost) {
            throw new IllegalArgumentException("minimum-cost cannot exceed maximum-cost.");
        }
        if ((long) maximumCost + crossWorldSurcharge > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maximum-cost plus cross-world-surcharge cannot exceed 2147483647.");
        }
    }

    static TeleportPricing load(FileConfiguration config) {
        for (var key : SETTINGS) {
            if (!(config.get(key) instanceof Integer)) {
                throw new IllegalArgumentException(key + " must be a non-negative whole number.");
            }
        }
        return new TeleportPricing(config.getInt(SETTINGS.get(0)), config.getInt(SETTINGS.get(1)),
                config.getInt(SETTINGS.get(2)), config.getInt(SETTINGS.get(3)));
    }

    int value(String key) {
        return switch (key) {
            case "minimum-cost" -> minimumCost;
            case "levels-per-1000-blocks" -> levelsPer1000Blocks;
            case "maximum-cost" -> maximumCost;
            case "cross-world-surcharge" -> crossWorldSurcharge;
            default -> throw new IllegalArgumentException("Unknown pricing setting: " + key);
        };
    }

    TeleportPricing with(String key, int value) {
        if (!SETTINGS.contains(key)) throw new IllegalArgumentException("Unknown pricing setting: " + key);
        return new TeleportPricing(key.equals(SETTINGS.get(0)) ? value : minimumCost,
                key.equals(SETTINGS.get(1)) ? value : levelsPer1000Blocks,
                key.equals(SETTINGS.get(2)) ? value : maximumCost,
                key.equals(SETTINGS.get(3)) ? value : crossWorldSurcharge);
    }

    void write(FileConfiguration config) {
        SETTINGS.forEach(key -> config.set(key, value(key)));
    }
}
