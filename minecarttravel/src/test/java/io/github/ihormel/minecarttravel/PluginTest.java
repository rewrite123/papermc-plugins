package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PluginTest extends PaperTest {
    private static void drain(PlayerMock player) {
        while (player.nextMessage() != null) {
            // discard
        }
    }

    @Test
    void pluginLoadsAndSwitchesModes() {
        var plugin = MockBukkit.load(MinecartTravelPlugin.class);
        assertTrue(plugin.isEnabled());
        var player = server.addPlayer();
        assertTrue(server.dispatchCommand(player, "minecarttravel normal"));
        assertTrue(player.getPersistentDataContainer().has(new NamespacedKey(plugin, "normal_mode")));
        assertTrue(server.dispatchCommand(player, "minecartfasttravel fast"));
        assertFalse(player.getPersistentDataContainer().has(new NamespacedKey(plugin, "normal_mode")));
        server.getPluginManager().disablePlugin(plugin);
    }

    @Test
    void configFromVersion01StillLoads() throws Exception {
        // MockBukkit names the data folder after the plugin name and version.
        var folder = server.getPluginsFolder().toPath().resolve("minecarttravel-" + YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                PluginTest.class.getResourceAsStream("/plugin.yml"), java.nio.charset.StandardCharsets.UTF_8)).getString("version"));
        java.nio.file.Files.createDirectories(folder);
        java.nio.file.Files.writeString(folder.resolve("config.yml"), """
                boost-after-ticks: 400
                fast-travel-after-ticks: 2400
                minimum-blocks-per-second: 6.0
                boost-blocks-per-second: 90.0
                safety-lookahead-blocks: 12
                max-route-rails: 65536
                max-route-turns: 32
                scan-blocks-per-tick: 4096
                route-steps-per-tick: 512
                save-interval-ticks: 600
                """);
        var plugin = MockBukkit.load(MinecartTravelPlugin.class);
        assertTrue(plugin.isEnabled());
        var player = server.addPlayer();
        server.dispatchCommand(player, "minecarttravel settings");
        player.nextMessage();
        assertEquals(" boost-delay: 20 seconds", player.nextMessage());
        server.getPluginManager().disablePlugin(plugin);
    }

    @Test
    void adminsChangeSettingsLiveAndTheyArePersisted() {
        var plugin = MockBukkit.load(MinecartTravelPlugin.class);
        var admin = server.addPlayer();
        admin.setOp(true);
        server.dispatchCommand(admin, "minecarttravel set boost-delay 15");
        assertEquals("boost-delay is now 15 seconds.", admin.nextMessage());
        server.dispatchCommand(admin, "minecarttravel set fast-travel-delay 90");
        server.dispatchCommand(admin, "minecarttravel set min-speed 5");
        server.dispatchCommand(admin, "minecarttravel set boost-speed 70");
        drain(admin);
        server.dispatchCommand(admin, "minecarttravel set boost-delay 100");
        assertTrue(admin.nextMessage().startsWith("Setting not changed: boost-delay must be shorter"));
        server.dispatchCommand(admin, "minecarttravel set warp 3");
        assertTrue(admin.nextMessage().startsWith("Setting not changed: Unknown setting"));

        server.dispatchCommand(admin, "minecarttravel settings");
        assertEquals("MinecartTravel settings:", admin.nextMessage());
        assertEquals(" boost-delay: 15 seconds", admin.nextMessage());
        assertEquals(" fast-travel-delay: 90 seconds", admin.nextMessage());
        assertEquals(" min-speed: 5 blocks/second", admin.nextMessage());
        assertEquals(" boost-speed: 70 blocks/second", admin.nextMessage());

        var saved = YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("config.yml").toFile());
        assertEquals(300, saved.getInt("boost-after-ticks"));
        assertEquals(1800, saved.getInt("fast-travel-after-ticks"));
        assertEquals(5.0, saved.getDouble("minimum-blocks-per-second"));
        assertEquals(70.0, saved.getDouble("boost-blocks-per-second"));
        server.getPluginManager().disablePlugin(plugin);
    }

    @Test
    void regularPlayersCanViewSettingsAndRegisterButNotChangeSettings() {
        var plugin = MockBukkit.load(MinecartTravelPlugin.class);
        var player = server.addPlayer();
        server.dispatchCommand(player, "minecarttravel set boost-delay 15");
        assertEquals("You do not have permission to change MinecartTravel settings.", player.nextMessage());
        server.dispatchCommand(player, "minecarttravel rescan");
        assertEquals("You do not have permission to recheck registered routes.", player.nextMessage());
        server.dispatchCommand(player, "minecarttravel settings");
        assertEquals("MinecartTravel settings:", player.nextMessage());
        drain(player);
        server.dispatchCommand(player, "minecarttravel register");
        assertTrue(player.nextMessage().startsWith("Route registration ready."));
        server.dispatchCommand(player, "minecarttravel status");
        drain(player);
        server.dispatchCommand(player, "minecarttravel register cancel");
        assertEquals("Route registration cancelled.", player.nextMessage());
        server.dispatchCommand(player, "minecarttravel register cancel");
        assertEquals("You are not registering a route.", player.nextMessage());
        assertEquals(List.of("register"),
                plugin.onTabComplete(player, plugin.getCommand("minecarttravel"), "minecarttravel", new String[] {"re"}));
        player.setOp(true);
        assertEquals(List.of("register", "rescan"),
                plugin.onTabComplete(player, plugin.getCommand("minecarttravel"), "minecarttravel", new String[] {"re"}));
        server.dispatchCommand(player, "minecarttravel rescan");
        assertTrue(player.nextMessage().startsWith("Rechecking registered routes"));
        server.getPluginManager().disablePlugin(plugin);
    }
}
