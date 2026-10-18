package io.github.ihormel.sleepy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.logging.Logger;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class SleepingRuleTest extends PaperTest {
    private final Logger logger = mock(Logger.class);
    private final SleepingRule rule = new SleepingRule(logger);

    @Test
    void setsExactlyFiftyPercentInEachWorld() {
        for (String name : new String[]{"world", "world_nether", "world_the_end", "custom"}) {
            World world = mock(World.class);
            when(world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50)).thenReturn(true);
            rule.apply(world);
            verify(world).setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50);
        }
        verify(logger, never()).severe(anyString());
    }

    @Test
    void reportsFailureWithWorldName() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("custom");
        when(world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50)).thenReturn(false);
        rule.apply(world);
        verify(logger).severe("Could not set the sleeping percentage to 50 in world custom");
    }
}
