package io.github.ihormel.tpcost;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PricingCommandTest {
    private ServerMock server;
    private TpCostPlugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(TpCostPlugin.class);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void defaultConfigurationPreservesOriginalPricesAndAdminCanReadThem() {
        assertEquals(TeleportPricing.DEFAULT, TeleportPricing.load(plugin.getConfig()));
        var console = server.getConsoleSender();
        server.dispatchCommand(console, "tpcostadmin");
        for (var key : TeleportPricing.SETTINGS) {
            assertEquals("[tpcost] " + key + " = " + TeleportPricing.DEFAULT.value(key), console.nextMessage());
        }
        assertEquals("[tpcost] cost-command-enabled = false", console.nextMessage());
    }

    @ParameterizedTest
    @CsvSource({"minimum-cost,1", "levels-per-1000-blocks,7", "maximum-cost,50", "cross-world-surcharge,0"})
    void commandPersistsEverySettingAndRestartRetainsIt(String key, int value) {
        var console = server.getConsoleSender();
        server.dispatchCommand(console, "tpcostadmin " + key + " " + value);
        assertTrue(console.nextMessage().contains("set to " + value));
        plugin.reloadConfig();
        assertEquals(value, plugin.getConfig().getInt(key));
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        server.dispatchCommand(console, "tpcostadmin " + key);
        assertEquals("[tpcost] " + key + " = " + value, console.nextMessage());
    }

    @Test
    void ordinaryPlayersCannotChangePricesButOperatorsCan() {
        var player = server.addPlayer();
        server.dispatchCommand(player, "tpcostadmin minimum-cost 1");
        assertEquals(3, plugin.getConfig().getInt("minimum-cost"));
        player.setOp(true);
        server.dispatchCommand(player, "tpcostadmin minimum-cost 1");
        assertEquals(1, plugin.getConfig().getInt("minimum-cost"));
    }

    @ParameterizedTest
    @CsvSource({
        "minimum-cost,-1", "minimum-cost,26", "maximum-cost,2",
        "levels-per-1000-blocks,1.5", "cross-world-surcharge,wrong",
        "maximum-cost,2147483647", "minimum-cost,999999999999",
        "unknown,1"
    })
    void invalidInputsLeaveActiveAndSavedPricesUnchanged(String key, String value) {
        var console = server.getConsoleSender();
        server.dispatchCommand(console, "tpcostadmin " + key + " " + value);
        assertNotNull(console.nextMessage());
        assertEquals(TeleportPricing.DEFAULT, TeleportPricing.load(plugin.getConfig()));
        plugin.reloadConfig();
        assertEquals(TeleportPricing.DEFAULT, TeleportPricing.load(plugin.getConfig()));
        server.dispatchCommand(console, "tpcostadmin minimum-cost");
        assertEquals("[tpcost] minimum-cost = 3", console.nextMessage());
    }

    @Test
    void saveFailureReportsErrorAndDoesNotChangeActiveOrInMemoryPrices() throws IOException {
        var path = plugin.getDataFolder().toPath().resolve("config.yml");
        var original = Files.readString(path);
        Files.delete(path);
        Files.createDirectory(path);
        try {
            var console = server.getConsoleSender();
            server.dispatchCommand(console, "tpcostadmin minimum-cost 1");
            assertTrue(console.nextMessage().contains("Could not save"));
            assertEquals(TeleportPricing.DEFAULT, TeleportPricing.load(plugin.getConfig()));
            server.dispatchCommand(console, "tpcostadmin minimum-cost");
            assertEquals("[tpcost] minimum-cost = 3", console.nextMessage());
        } finally {
            Files.delete(path);
            Files.writeString(path, original);
        }
    }

    @Test
    void tabCompletionIsPermissionGatedAndIncludesCurrentValue() {
        var player = server.addPlayer();
        var command = plugin.getCommand("tpcostadmin");
        assertTrue(plugin.onTabComplete(player, command, "tpcostadmin", new String[] {""}).isEmpty());
        player.setOp(true);
        assertEquals(java.util.stream.Stream.concat(TeleportPricing.SETTINGS.stream(),
                        java.util.stream.Stream.of("cost-command-enabled")).toList(),
                plugin.onTabComplete(player, command, "tpcostadmin", new String[] {""}));
        assertEquals(java.util.List.of("25"),
                plugin.onTabComplete(player, command, "tpcostadmin", new String[] {"maximum-cost", ""}));
    }

    @Test
    void malformedConfigurationFailsExplicitly() {
        var config = new YamlConfiguration();
        TeleportPricing.DEFAULT.write(config);
        for (var value : new Object[] {-1, 1.5, "3"}) {
            config.set("minimum-cost", value);
            assertThrows(IllegalArgumentException.class, () -> TeleportPricing.load(config));
        }
    }

    @Test
    void commandChangesImmediatelyAffectNormalPlayerQuoteWithoutRestart() {
        var world = server.addSimpleWorld("world");
        var alice = server.addPlayer("Alice");
        var bob = server.addPlayer("Bob");
        alice.teleport(new org.bukkit.Location(world, 0, 64, 0));
        bob.teleport(new org.bukkit.Location(world, 3000, 64, 0));
        server.dispatchCommand(server.getConsoleSender(), "tpcostadmin cost-command-enabled true");
        server.dispatchCommand(alice, "tpcost Bob");
        assertTrue(alice.nextMessage().contains("costs 9 levels"));
        server.dispatchCommand(server.getConsoleSender(), "tpcostadmin levels-per-1000-blocks 2");
        server.dispatchCommand(alice, "tpcost Bob");
        assertTrue(alice.nextMessage().contains("costs 6 levels"));
    }
}
