package io.github.ihormel.tpcost;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RequestPreferencesTest {
    @TempDir
    Path directory;

    @Test
    void persistenceKeepsSeparatePlayersAndReopeningDoesNotResetOthers() throws Exception {
        var file = directory.resolve("preferences.yml");
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var preferences = new RequestPreferences(file);
        assertFalse(preferences.isClosed(first));
        preferences.setClosed(first, true);
        preferences.setClosed(second, true);
        preferences = new RequestPreferences(file);
        assertTrue(preferences.isClosed(first));
        assertTrue(preferences.isClosed(second));
        preferences.setClosed(first, false);
        preferences = new RequestPreferences(file);
        assertFalse(preferences.isClosed(first));
        assertTrue(preferences.isClosed(second));
        try (var entries = Files.list(directory)) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    void invalidStoredPreferencesFailRatherThanSilentlyReopeningPlayers() throws Exception {
        var file = directory.resolve("preferences.yml");
        for (var contents : new String[] {"closed-players: wrong", "closed-players: [not-a-uuid]", "invalid: ["}) {
            Files.writeString(file, contents);
            assertThrows(InvalidConfigurationException.class, () -> new RequestPreferences(file));
        }
    }
}
