package io.github.ihormel.votersrights;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Difficulty;
import org.bukkit.configuration.file.FileConfiguration;

record VotingSettings(int percentage, int minimumPlayers, int restartCooldownMinutes,
        int timeoutMinutes, int protectionMinutes, int minimumView, int maximumView,
        int minimumSimulation, int maximumSimulation, Set<Difficulty> difficulties,
        boolean whitelistEnabled, Set<UUID> whitelist, Set<UUID> blacklist,
        Map<String, Boolean> actions, Map<String, CustomAction> customActions) {

    record CustomAction(boolean enabled, List<String> commands) {}

    static final List<String> ACTIONS = List.of("restart", "lowerViewDistance", "increaseViewDistance",
            "lowerSimulationDistance", "increaseSimulationDistance", "timeout", "difficulty", "setTimeDay", "setTimeNight");
    static final List<String> NUMERIC = List.of("pass-percentage", "minimum-online-players",
            "restart-cooldown-minutes", "timeout-duration-minutes", "timeout-protection-minutes",
            "minimum-view-distance", "maximum-view-distance", "minimum-simulation-distance", "maximum-simulation-distance");

    static VotingSettings load(FileConfiguration config) {
        int percentage = number(config, NUMERIC.get(0), 1, 100);
        int players = number(config, NUMERIC.get(1), 1, Integer.MAX_VALUE);
        int restart = number(config, NUMERIC.get(2), 0, 525600);
        int timeout = number(config, NUMERIC.get(3), 1, 525600);
        int protection = number(config, NUMERIC.get(4), 0, 525600);
        int minView = number(config, NUMERIC.get(5), 2, 32);
        int maxView = number(config, NUMERIC.get(6), minView, 32);
        int minSim = number(config, NUMERIC.get(7), 2, 32);
        int maxSim = number(config, NUMERIC.get(8), minSim, 32);
        var difficulties = strings(config, "allowed-difficulties").stream()
                .map(value -> Difficulty.valueOf(value.toUpperCase(Locale.ROOT))).collect(Collectors.toUnmodifiableSet());
        if (difficulties.isEmpty()) throw new IllegalArgumentException("At least one difficulty must be allowed.");
        var actions = new LinkedHashMap<String, Boolean>();
        ACTIONS.forEach(action -> actions.put(action, bool(config, "actions." + action)));
        var custom = new LinkedHashMap<String, CustomAction>();
        var section = config.getConfigurationSection("custom-actions");
        if (section == null) throw new IllegalArgumentException("custom-actions must be a configuration section.");
        for (var name : section.getKeys(false)) {
            validateCustomName(name);
            var commands = strings(config, "custom-actions." + name + ".commands");
            if (commands.isEmpty()) throw new IllegalArgumentException("A custom action needs at least one console command.");
            commands.forEach(VotingSettings::validateCommand);
            custom.put(name, new CustomAction(bool(config, "custom-actions." + name + ".enabled"), List.copyOf(commands)));
        }
        return new VotingSettings(percentage, players, restart, timeout, protection, minView, maxView, minSim, maxSim,
                difficulties, bool(config, "whitelist-enabled"), ids(config, "whitelist"), ids(config, "blacklist"),
                Map.copyOf(actions), Map.copyOf(custom));
    }

    boolean eligible(UUID player) {
        return !blacklist.contains(player) && (!whitelistEnabled || whitelist.contains(player));
    }

    String actionName(String input) {
        return names().stream().filter(name -> name.equalsIgnoreCase(input)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown action. Use /vote to see available actions."));
    }

    List<String> names() {
        var result = new ArrayList<>(ACTIONS);
        result.addAll(customActions.keySet().stream().sorted().toList());
        return result;
    }

    boolean enabled(String action) {
        return actions.containsKey(action) ? actions.get(action) : customActions.get(action).enabled();
    }

    static void validateCustomName(String name) {
        if (!name.matches("[a-z][a-z0-9_-]{0,31}") || name.equals("yes") || name.equals("no")
                || ACTIONS.stream().anyMatch(action -> action.equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("Custom names must be lowercase letters/digits/_/-, start with a letter,"
                    + " and not collide with built-in actions or yes/no (maximum 32 characters).");
        }
    }

    static void validateCommand(String command) {
        if (command.isBlank() || command.startsWith("/") || command.contains("\n") || command.contains("\r")
                || command.contains("{") || command.contains("}")) {
            throw new IllegalArgumentException("Use a fixed console command without a leading slash, newlines, or placeholders.");
        }
    }

    private static int number(FileConfiguration config, String key, int min, int max) {
        if (!(config.get(key) instanceof Integer value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " must be a whole number from " + min + " to " + max + ".");
        }
        return value;
    }

    private static boolean bool(FileConfiguration config, String key) {
        if (!(config.get(key) instanceof Boolean value)) throw new IllegalArgumentException(key + " must be true or false.");
        return value;
    }

    private static List<String> strings(FileConfiguration config, String key) {
        if (!(config.get(key) instanceof List<?> list) || list.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException(key + " must be a list of strings.");
        }
        return list.stream().map(String.class::cast).toList();
    }

    private static Set<UUID> ids(FileConfiguration config, String key) {
        return strings(config, key).stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }
}
