package io.github.ihormel.minecarttravel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

abstract class PaperTest {
    ServerMock server;

    @BeforeEach
    void startPaper() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void stopPaper() {
        MockBukkit.unmock();
    }
}
