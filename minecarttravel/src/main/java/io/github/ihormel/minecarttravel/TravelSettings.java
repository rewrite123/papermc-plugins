package io.github.ihormel.minecarttravel;

import org.bukkit.configuration.file.FileConfiguration;

record TravelSettings(int boostTicks, int fastTicks, double minimumSpeed, double boostSpeed,
                      int lookahead, int maxRails, int maxTurns, int scanBudget, int routeBudget, int saveTicks) {
    static TravelSettings read(FileConfiguration config) {
        int boost = integer(config, "boost-after-ticks", 1, 72000);
        int fast = integer(config, "fast-travel-after-ticks", boost + 1, 144000);
        double minimum = decimal(config, "minimum-blocks-per-second", 0.1, 8);
        double speed = decimal(config, "boost-blocks-per-second", minimum, 120);
        int lookahead = integer(config, "safety-lookahead-blocks", (int) Math.ceil(speed / 20) + 2, 64);
        return new TravelSettings(boost, fast, minimum, speed, lookahead,
                integer(config, "max-route-rails", 2, 1000000),
                integer(config, "max-route-turns", 0, 1024),
                integer(config, "scan-blocks-per-tick", 256, 65536),
                integer(config, "route-steps-per-tick", 1, 4096),
                integer(config, "save-interval-ticks", 20, 72000));
    }

    private static int integer(FileConfiguration config, String key, int min, int max) {
        Object raw = config.get(key);
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()
                || number.intValue() < min || number.intValue() > max) {
            throw new IllegalArgumentException(key + " must be an integer between " + min + " and " + max);
        }
        return number.intValue();
    }

    private static double decimal(FileConfiguration config, String key, double min, double max) {
        Object raw = config.get(key);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < min || number.doubleValue() > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return number.doubleValue();
    }
}
