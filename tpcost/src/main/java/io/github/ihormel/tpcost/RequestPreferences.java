package io.github.ihormel.tpcost;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class RequestPreferences {
    private final Path file;
    private final Set<UUID> closed = new HashSet<>();

    RequestPreferences(Path file) throws IOException, InvalidConfigurationException {
        this.file = file;
        if (!Files.exists(file)) return;
        var config = new YamlConfiguration();
        config.load(file.toFile());
        if (!(config.get("closed-players") instanceof java.util.List<?> entries)) {
            throw new InvalidConfigurationException("closed-players must be a list of player UUIDs.");
        }
        for (var entry : entries) {
            try {
                closed.add(UUID.fromString(String.valueOf(entry)));
            } catch (IllegalArgumentException exception) {
                throw new InvalidConfigurationException("Invalid closed player UUID: " + entry, exception);
            }
        }
    }

    boolean isClosed(UUID player) {
        return closed.contains(player);
    }

    void setClosed(UUID player, boolean value) throws IOException {
        var updated = new HashSet<>(closed);
        if (value) updated.add(player);
        else updated.remove(player);
        var config = new YamlConfiguration();
        config.set("closed-players", updated.stream().map(UUID::toString).sorted().toList());
        Path temporary = Files.createTempFile(file.getParent(), "requests-", ".tmp");
        try {
            config.save(temporary.toFile());
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        closed.clear();
        closed.addAll(updated);
    }
}
