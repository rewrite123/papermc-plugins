package io.github.ihormel.votersrights;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionStateTest {
    @TempDir Path directory;

    @Test
    void restartWarningAndCooldownSurviveReloadAndAreClearedBeforeRestart() throws Exception {
        var file = directory.resolve("state.yml");
        var state = new ActionState(file);
        state.scheduleRestart(120000);
        state = new ActionState(file);
        assertEquals(120000, state.restartAt());
        assertTrue(state.pending(1));
        state.beginRestart(1320000);
        state = new ActionState(file);
        assertEquals(0, state.restartAt());
        assertEquals(1320000, state.restartAvailableAt());
        assertFalse(state.pending(120000));
    }

    @Test
    void timeoutWarningDurationAndProtectionAreDurableAndExactlyBounded() throws Exception {
        var file = directory.resolve("state.yml");
        var id = UUID.randomUUID();
        var state = new ActionState(file);
        state.timeout(id, "Alice", 1000, 3, 30);
        state = new ActionState(file);
        var timeout = state.timeouts().get(id);
        assertEquals(31000, timeout.startsAt());
        assertEquals(211000, timeout.endsAt());
        assertEquals(2011000, timeout.protectedUntil());
        assertFalse(timeout.active(30999));
        assertTrue(timeout.active(31000));
        assertFalse(timeout.active(211000));
        assertTrue(state.pending(30999));
        assertFalse(state.pending(31000));
        var snapshot = state.timeouts();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void failedSaveLeavesActiveStateUnchangedAndCleansTemporaryFiles() throws Exception {
        var file = directory.resolve("state.yml");
        var state = new ActionState(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("obstruction"), "test");
        assertThrows(java.io.IOException.class, () -> state.scheduleRestart(1));
        assertEquals(0, state.restartAt());
        try (var paths = Files.list(directory)) {
            assertEquals(1, paths.count());
        }
    }

    @Test
    void malformedStateFailsExplicitly() throws Exception {
        var file = directory.resolve("state.yml");
        for (String contents : new String[] {"restart-at: wrong", "restart-at: -1\nrestart-available-at: 0",
                "restart-at: 0\nrestart-available-at: 0\ntimeouts: wrong",
                "restart-at: 0\nrestart-available-at: 0\ntimeouts:\n  not-a-uuid:\n    name: Alice"}) {
            Files.writeString(file, contents);
            assertThrows(InvalidConfigurationException.class, () -> new ActionState(file));
        }
    }
}
