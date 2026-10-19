package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class PluginTest extends PaperTest {
    @Test
    void pluginLoadsRegistersModeCommandsAndSavesGraphOnShutdown() {
        var plugin = MockBukkit.load(MinecartTravelPlugin.class);
        assertTrue(plugin.isEnabled());
        var player = server.addPlayer();
        player.setOp(true);
        assertTrue(server.dispatchCommand(player, "minecarttravel normal"));
        assertTrue(player.getPersistentDataContainer().has(new NamespacedKey(plugin, "normal_mode")));
        assertTrue(server.dispatchCommand(player, "minecartfasttravel fast"));
        assertFalse(player.getPersistentDataContainer().has(new NamespacedKey(plugin, "normal_mode")));
        assertTrue(server.dispatchCommand(player, "minecarttravel rescan"));
        server.getPluginManager().disablePlugin(plugin);
        assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve("rail-graph.txt")));
    }
}
