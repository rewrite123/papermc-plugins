package io.github.ihormel.mobbanner;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

abstract class PaperTest {
    ServerMock server;
    BannerItems items;

    @BeforeEach
    void startPaper() {
        server = MockBukkit.mock();
        items = new BannerItems(new NamespacedKey("mobbanner", "mob"));
    }

    @AfterEach
    void stopPaper() {
        MockBukkit.unmock();
    }
}
