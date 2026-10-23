package io.github.ihormel.votersrights;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class ActionState {
    record Timeout(String name, long startsAt, long endsAt, long protectedUntil) {
        boolean active(long now) { return now >= startsAt && now < endsAt; }
    }

    private final Path file;
    private volatile Map<UUID, Timeout> timeouts = Map.of();
    private long restartAt;
    private long restartAvailableAt;

    ActionState(Path file) throws IOException, InvalidConfigurationException {
        this.file = file;
        if (!Files.exists(file)) return;
        var config = new YamlConfiguration();
        config.load(file.toFile());
        restartAt = timestamp(config, "restart-at");
        restartAvailableAt = timestamp(config, "restart-available-at");
        var section = config.getConfigurationSection("timeouts");
        var loaded = new HashMap<UUID, Timeout>();
        if (config.contains("timeouts") && section == null) throw new InvalidConfigurationException("timeouts must be a section.");
        if (section != null) {
            for (var key : section.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException exception) {
                    throw new InvalidConfigurationException("Invalid timeout UUID: " + key, exception);
                }
                String path = "timeouts." + key + ".";
                String name = config.getString(path + "name");
                long start = timestamp(config, path + "starts-at");
                long end = timestamp(config, path + "ends-at");
                long protection = timestamp(config, path + "protected-until");
                if (name == null || name.isBlank() || end <= start || protection < end) {
                    throw new InvalidConfigurationException("Invalid timeout for " + key);
                }
                loaded.put(id, new Timeout(name, start, end, protection));
            }
        }
        timeouts = Map.copyOf(loaded);
    }

    Map<UUID, Timeout> timeouts() { return timeouts; }
    long restartAt() { return restartAt; }
    long restartAvailableAt() { return restartAvailableAt; }

    boolean pending(long now) {
        return restartAt != 0 || timeouts.values().stream().anyMatch(timeout -> now < timeout.startsAt());
    }

    void scheduleRestart(long at) throws IOException {
        save(at, restartAvailableAt, timeouts);
    }

    void beginRestart(long availableAt) throws IOException {
        save(0, availableAt, timeouts);
    }

    void timeout(UUID player, String name, long now, int durationMinutes, int protectionMinutes) throws IOException {
        var updated = new HashMap<>(timeouts);
        updated.entrySet().removeIf(entry -> now >= entry.getValue().protectedUntil());
        long start = now + 30_000;
        long end = start + durationMinutes * 60_000L;
        updated.put(player, new Timeout(name, start, end, end + protectionMinutes * 60_000L));
        save(restartAt, restartAvailableAt, updated);
    }

    private void save(long restart, long available, Map<UUID, Timeout> updated) throws IOException {
        var config = new YamlConfiguration();
        config.set("restart-at", restart);
        config.set("restart-available-at", available);
        updated.forEach((id, timeout) -> {
            String path = "timeouts." + id + ".";
            config.set(path + "name", timeout.name());
            config.set(path + "starts-at", timeout.startsAt());
            config.set(path + "ends-at", timeout.endsAt());
            config.set(path + "protected-until", timeout.protectedUntil());
        });
        AtomicYaml.save(config, file);
        restartAt = restart;
        restartAvailableAt = available;
        timeouts = Map.copyOf(updated);
    }

    private static long timestamp(YamlConfiguration config, String key) throws InvalidConfigurationException {
        Object value = config.get(key);
        if (!(value instanceof Long || value instanceof Integer) || ((Number) value).longValue() < 0) {
            throw new InvalidConfigurationException(key + " must be a non-negative timestamp.");
        }
        return ((Number) value).longValue();
    }
}
