package io.github.ihormel.sleepy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockbukkit.mockbukkit.MockBukkit;

abstract class PaperTest {
    @BeforeAll
    static void startPaperApi() throws ClassNotFoundException {
        MockBukkit.mock();
        // Initialize the legacy bridge before GameRules to avoid its circular initialization in MockBukkit.
        Class.forName("org.bukkit.GameRule");
    }

    @AfterAll
    static void stopPaperApi() {
        MockBukkit.unmock();
    }
}
