package io.github.ihormel.sleepy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class SleepyPluginTest {
    private ServerMock server;
    private SleepyPlugin plugin;
    private WorldMock world;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        server = MockBukkit.mock();
        Class.forName("org.bukkit.GameRule");
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(SleepyPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private int gamerule() {
        return world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
    }

    private List<PlayerMock> join(int count) {
        var players = new java.util.ArrayList<PlayerMock>();
        for (int i = 0; i < count; i++) {
            var player = server.addPlayer();
            player.teleport(world.getSpawnLocation());
            player.setGameMode(GameMode.SURVIVAL);
            players.add(player);
        }
        return players;
    }

    @Test
    void appliesDefaultsAndWritesDefaultConfig() {
        assertEquals(50, gamerule());
        assertEquals(50, plugin.getConfig().getInt("sleeping-percentage"));
        assertEquals(0, plugin.getConfig().getInt("min-sleeping-players"));
        assertTrue(new java.io.File(plugin.getDataFolder(), "config.yml").isFile());
    }

    @Test
    void adminSetsMinimumPlayersAndGameruleFollowsPlayerCount() {
        var players = join(30);
        var admin = players.getFirst();
        admin.setOp(true);

        assertTrue(admin.performCommand("sleepy minplayers 10"));
        assertEquals("Sleep settings updated. Night skips when 50% of players or 10 players are sleeping,"
                + " whichever is fewer. Here: 10 of 30 needed.", admin.nextMessage());
        assertEquals(33, gamerule());
        assertEquals(10, plugin.getConfig().getInt("min-sleeping-players"));

        for (int i = 4; i < players.size(); i++) players.get(i).disconnect();
        server.getScheduler().performTicks(20);
        assertEquals(50, gamerule());
        assertEquals(2, plugin.rule().needed(SleepingRule.active(world)));

        assertTrue(admin.performCommand("sleepy minplayers off"));
        admin.nextMessage();
        assertEquals(0, plugin.getConfig().getInt("min-sleeping-players"));
        assertTrue(admin.performCommand("sleepy percent 100"));
        admin.nextMessage();
        assertEquals(100, gamerule());
        assertEquals(100, plugin.getConfig().getInt("sleeping-percentage"));
    }

    @Test
    void validatesValuesAndPermissions() {
        var player = join(1).getFirst();
        assertTrue(player.performCommand("sleepy percent 10"));
        assertEquals("You do not have permission to change sleep settings.", player.nextMessage());
        assertTrue(player.performCommand("sleepy"));
        assertEquals("Night skips when 50% of players are sleeping (minimum players: off). Here: 1 of 1 needed.",
                player.nextMessage());
        assertEquals(List.of("status"), server.getCommandTabComplete(player, "sleepy "));

        player.setOp(true);
        assertTrue(player.performCommand("sleepy percent 101"));
        assertEquals("Percent must be between 0 and 100.", player.nextMessage());
        assertTrue(player.performCommand("sleepy minplayers -2"));
        assertEquals("Minimum players must be 0 (off) or more.", player.nextMessage());
        assertTrue(player.performCommand("sleepy minplayers lots"));
        assertEquals("Value must be a whole number or off.", player.nextMessage());
        assertEquals(List.of("status", "percent", "minplayers"), server.getCommandTabComplete(player, "sleepy "));
        assertEquals(List.of("off"), server.getCommandTabComplete(player, "sleepy minplayers o"));
        assertEquals(50, gamerule());
    }

    @Test
    void settingsSurviveReload() {
        var admin = join(1).getFirst();
        admin.setOp(true);
        admin.performCommand("sleepy percent 30");
        admin.performCommand("sleepy minplayers 7");
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        assertEquals(30, plugin.rule().percent());
        assertEquals(7, plugin.rule().minPlayers());
    }
}
