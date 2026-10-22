package io.github.ihormel.tpcost;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PrivacyCommandsTest {
    private ServerMock server;
    private TpCostPlugin plugin;
    private PlayerMock alice;
    private PlayerMock bob;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(TpCostPlugin.class);
        var world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        alice.teleport(new org.bukkit.Location(world, 0, 64, 0));
        bob.teleport(new org.bukkit.Location(world, 3000, 64, 0));
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void quotesAndRequestPricesAreHiddenByDefaultEvenForOperatorsAndNamespacedCommands() {
        alice.setOp(true);
        server.dispatchCommand(alice, "tpcost:tpcost Bob");
        assertTrue(alice.nextMessage().contains("lookup is disabled"));
        server.dispatchCommand(alice, "tpa Bob");
        var message = alice.nextMessage();
        assertTrue(message.contains("Request sent"));
        assertFalse(message.contains("9"));
        assertFalse(bob.nextMessage().contains("9"));
        assertTrue(plugin.getCommand("tpcost").tabComplete(alice, "tpcost", new String[] {""}).isEmpty());
        server.dispatchCommand(bob, "tpdeny Alice");
        assertTrue(alice.nextMessage().contains("denied"));
    }

    @Test
    void upgradingFromPricingOnlyConfigurationDefaultsToPrivate() throws IOException {
        var file = plugin.getDataFolder().toPath().resolve("config.yml");
        Files.writeString(file, "minimum-cost: 3\nlevels-per-1000-blocks: 3\nmaximum-cost: 25\ncross-world-surcharge: 3\n");
        server.getPluginManager().disablePlugin(plugin);
        plugin.reloadConfig();
        server.getPluginManager().enablePlugin(plugin);
        server.dispatchCommand(alice, "tpcost Bob");
        assertTrue(alice.nextMessage().contains("lookup is disabled"));
    }

    @Test
    void namespacedChatResponseAcceptsAndChargesOnlyAfterSuccess() {
        alice.setLevel(30);
        server.dispatchCommand(alice, "tpa Bob");
        alice.nextMessage();
        bob.nextMessage();
        assertEquals(30, alice.getLevel());
        server.dispatchCommand(bob, "tpcost:tpaccept Alice");
        assertEquals(bob.getLocation(), alice.getLocation());
        assertEquals(21, alice.getLevel());
        assertTrue(alice.nextMessage().contains("for 9 levels"));
        server.dispatchCommand(bob, "tpcost:tpaccept Alice");
        assertEquals(21, alice.getLevel());
    }

    @Test
    void adminToggleIsLivePermissionGatedValidatedAndPersistent() {
        server.dispatchCommand(alice, "tpcostadmin cost-command-enabled true");
        assertFalse(plugin.getConfig().getBoolean("cost-command-enabled"));
        var console = server.getConsoleSender();
        server.dispatchCommand(console, "tpcostadmin cost-command-enabled true");
        assertTrue(console.nextMessage().contains("set to true"));
        plugin.reloadConfig();
        assertTrue(plugin.getConfig().getBoolean("cost-command-enabled"));
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        server.dispatchCommand(bob, "tpcost Alice");
        assertTrue(bob.nextMessage().contains("costs 9 levels"));
        server.dispatchCommand(console, "tpcostadmin cost-command-enabled yes");
        assertTrue(console.nextMessage().contains("must be true or false"));
        assertTrue(plugin.getConfig().getBoolean("cost-command-enabled"));
        server.dispatchCommand(console, "tpcostadmin cost-command-enabled false");
        server.dispatchCommand(bob, "tpcost Alice");
        assertTrue(bob.nextMessage().contains("lookup is disabled"));
    }

    @Test
    void closeCancelsIncomingRequestsNotOutgoingAndReopenAllowsRequests() {
        alice.setLevel(30);
        bob.setLevel(30);
        server.dispatchCommand(alice, "tpa Bob");
        alice.nextMessage();
        bob.nextMessage();
        server.dispatchCommand(bob, "tpa Alice");
        bob.nextMessage();
        alice.nextMessage();
        server.dispatchCommand(bob, "tpclose");
        assertTrue(bob.nextMessage().contains("now closed"));
        assertTrue(alice.nextMessage().contains("closed incoming"));
        server.dispatchCommand(alice, "tpaccept Bob");
        assertTrue(alice.nextMessage().contains("teleported to you"));
        assertEquals(21, bob.getLevel());
        bob.nextMessage();
        server.dispatchCommand(bob, "tpaccept Alice");
        assertTrue(bob.nextMessage().contains("closed"));
        server.dispatchCommand(alice, "tpa Bob");
        assertTrue(alice.nextMessage().contains("not accepting"));
        assertEquals(30, alice.getLevel());
        server.dispatchCommand(bob, "tpopen");
        assertTrue(bob.nextMessage().contains("now open"));
        server.dispatchCommand(alice, "tpa Bob");
        assertTrue(alice.nextMessage().contains("Request sent"));
    }

    @Test
    void closeSurvivesReconnectAndPluginRestartAndOpenIsAlsoSaved() {
        server.dispatchCommand(bob, "tpclose");
        bob.disconnect();
        bob.reconnect();
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        server.dispatchCommand(alice, "tpa Bob");
        assertTrue(alice.nextMessage().contains("not accepting"));
        server.dispatchCommand(bob, "tpopen");
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        server.dispatchCommand(alice, "tpa Bob");
        assertTrue(alice.nextMessage().contains("Request sent"));
    }

    @Test
    void preferenceSaveFailureLeavesRequestsOpenAndReportsError() throws IOException {
        var file = plugin.getDataFolder().toPath().resolve("request-preferences.yml");
        Files.createDirectory(file);
        Files.writeString(file.resolve("obstruction"), "test");
        try {
            server.dispatchCommand(bob, "tpclose");
            assertTrue(bob.nextMessage().contains("Could not save"));
            server.dispatchCommand(alice, "tpa Bob");
            assertTrue(alice.nextMessage().contains("Request sent"));
        } finally {
            Files.delete(file.resolve("obstruction"));
            Files.delete(file);
        }
    }

    @Test
    void costToggleSaveFailureRollsBackActiveAndInMemoryValues() throws IOException {
        var file = plugin.getDataFolder().toPath().resolve("config.yml");
        var original = Files.readString(file);
        Files.delete(file);
        Files.createDirectory(file);
        try {
            var console = server.getConsoleSender();
            server.dispatchCommand(console, "tpcostadmin cost-command-enabled true");
            assertTrue(console.nextMessage().contains("Could not save"));
            assertFalse(plugin.getConfig().getBoolean("cost-command-enabled"));
            server.dispatchCommand(alice, "tpcost Bob");
            assertTrue(alice.nextMessage().contains("lookup is disabled"));
        } finally {
            Files.delete(file);
            Files.writeString(file, original);
        }
    }
}
