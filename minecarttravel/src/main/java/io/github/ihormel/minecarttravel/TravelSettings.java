package io.github.ihormel.minecarttravel;

import java.util.Arrays;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

record TravelSettings(int boostTicks, int fastTicks, double minimumSpeed, double boostSpeed,
                      int lookahead, int maxRails, int maxTurns, int verifyBudget, int recordBudget, int saveTicks) {
    /** Settings admins can change in-game with /minecarttravel set. */
    enum Option {
        BOOST_DELAY("boost-delay", "boost-after-ticks", "seconds"),
        FAST_TRAVEL_DELAY("fast-travel-delay", "fast-travel-after-ticks", "seconds"),
        MIN_SPEED("min-speed", "minimum-blocks-per-second", "blocks/second"),
        BOOST_SPEED("boost-speed", "boost-blocks-per-second", "blocks/second");

        final String command;
        final String key;
        final String unit;

        Option(String command, String key, String unit) {
            this.command = command;
            this.key = key;
            this.unit = unit;
        }

        static Option named(String name) {
            return Arrays.stream(values()).filter(option -> option.command.equalsIgnoreCase(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown setting '" + name + "'. Choose one of: "
                            + String.join(", ", Arrays.stream(values()).map(option -> option.command).toList()) + "."));
        }
    }

    static TravelSettings read(FileConfiguration config) {
        int boost = integer(config, "boost-after-ticks", 1, 72000);
        int fast = integer(config, "fast-travel-after-ticks", boost + 1, 144000);
        double minimum = decimal(config, "minimum-blocks-per-second", 0.1, 8);
        double speed = decimal(config, "boost-blocks-per-second", minimum, 120);
        int lookahead = integer(config, "safety-lookahead-blocks", minimumLookahead(speed), 64);
        return new TravelSettings(boost, fast, minimum, speed, lookahead,
                integer(config, "max-route-rails", 2, 1000000),
                integer(config, "max-route-turns", 0, 1024),
                integer(config, "verify-blocks-per-tick", 16, 65536),
                integer(config, "record-steps-per-tick", 64, 4096),
                integer(config, "save-interval-ticks", 20, 72000));
    }

    static int minimumLookahead(double boostSpeed) {
        return (int) Math.ceil(boostSpeed / 20) + 2;
    }

    String describe(Option option) {
        return switch (option) {
            case BOOST_DELAY -> seconds(boostTicks);
            case FAST_TRAVEL_DELAY -> seconds(fastTicks);
            case MIN_SPEED -> number(minimumSpeed);
            case BOOST_SPEED -> number(boostSpeed);
        } + " " + option.unit;
    }

    /**
     * Validates and writes one setting into {@code config}, returning the new settings.
     * The configuration is left unchanged if the value is rejected.
     */
    TravelSettings adjust(FileConfiguration config, Option option, String raw) {
        final double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + raw + "' is not a number.");
        }
        if (!Double.isFinite(value)) throw new IllegalArgumentException("'" + raw + "' is not a number.");
        Object stored;
        switch (option) {
            case BOOST_DELAY -> {
                int ticks = ticks(value, 3600);
                if (ticks >= fastTicks) {
                    throw new IllegalArgumentException("boost-delay must be shorter than fast-travel-delay ("
                            + seconds(fastTicks) + " seconds).");
                }
                stored = ticks;
            }
            case FAST_TRAVEL_DELAY -> {
                int ticks = ticks(value, 7200);
                if (ticks <= boostTicks) {
                    throw new IllegalArgumentException("fast-travel-delay must be longer than boost-delay ("
                            + seconds(boostTicks) + " seconds).");
                }
                stored = ticks;
            }
            case MIN_SPEED -> {
                if (value < 0.1 || value > 8) {
                    throw new IllegalArgumentException("min-speed must be between 0.1 and 8 blocks/second "
                            + "(8 is the vanilla minecart top speed).");
                }
                if (value > boostSpeed) {
                    throw new IllegalArgumentException("min-speed cannot be above boost-speed ("
                            + number(boostSpeed) + " blocks/second).");
                }
                stored = value;
            }
            case BOOST_SPEED -> {
                if (value < minimumSpeed || value > 120) {
                    throw new IllegalArgumentException("boost-speed must be between min-speed ("
                            + number(minimumSpeed) + ") and 120 blocks/second.");
                }
                stored = value;
            }
            default -> throw new IllegalStateException();
        }
        Object old = config.get(option.key);
        Object oldLookahead = config.get("safety-lookahead-blocks");
        config.set(option.key, stored);
        if (option == Option.BOOST_SPEED && lookahead < minimumLookahead(value)) {
            config.set("safety-lookahead-blocks", minimumLookahead(value));
        }
        try {
            return read(config);
        } catch (IllegalArgumentException exception) {
            config.set(option.key, old);
            config.set("safety-lookahead-blocks", oldLookahead);
            throw exception;
        }
    }

    private static int ticks(double seconds, int maxSeconds) {
        if (seconds < 0.05 || seconds > maxSeconds) {
            throw new IllegalArgumentException("Delays must be between 0.05 and " + maxSeconds + " seconds.");
        }
        return (int) Math.round(seconds * 20);
    }

    static String seconds(int ticks) {
        return number(ticks / 20.0);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
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
