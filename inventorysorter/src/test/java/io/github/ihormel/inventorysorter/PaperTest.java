package io.github.ihormel.inventorysorter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockbukkit.mockbukkit.MockBukkit;

abstract class PaperTest {
    @BeforeAll
    static void startPaperApi() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopPaperApi() {
        MockBukkit.unmock();
    }
}
