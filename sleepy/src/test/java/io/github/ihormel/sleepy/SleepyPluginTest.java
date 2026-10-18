package io.github.ihormel.sleepy;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.GameRules;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class SleepyPluginTest extends PaperTest {
    @Test
    void appliesToExistingWorldsAndRegistersListener() {
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        World overworld = world();
        World custom = world();
        SleepyPlugin plugin = plugin(server);
        when(server.getWorlds()).thenReturn(List.of(overworld, custom));
        when(server.getPluginManager()).thenReturn(manager);

        plugin.onEnable();

        verify(manager).registerEvents(plugin, plugin);
        verify(overworld).setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50);
        verify(custom).setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50);
    }

    @Test
    void appliesToWorldsLoadedAfterEnable() {
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.getWorlds()).thenReturn(List.of());
        SleepyPlugin plugin = plugin(server);
        plugin.onEnable();
        World laterWorld = world();

        plugin.onWorldLoad(new WorldLoadEvent(laterWorld));

        verify(laterWorld).setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50);
    }

    private SleepyPlugin plugin(Server server) {
        SleepyPlugin plugin = mock(SleepyPlugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        doCallRealMethod().when(plugin).onEnable();
        doCallRealMethod().when(plugin).onWorldLoad(org.mockito.ArgumentMatchers.any(WorldLoadEvent.class));
        return plugin;
    }

    private World world() {
        World world = mock(World.class);
        when(world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50)).thenReturn(true);
        return world;
    }
}
