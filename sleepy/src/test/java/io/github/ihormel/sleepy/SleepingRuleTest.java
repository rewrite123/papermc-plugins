package io.github.ihormel.sleepy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SleepingRuleTest extends PaperTest {
    private final Logger logger = mock(Logger.class);
    private final SleepingRule rule = new SleepingRule(logger);

    @Test
    void matchesVanillaRoundingAtFiftyPercent() {
        assertEquals(1, SleepingRule.required(0, 50));
        assertEquals(1, SleepingRule.required(1, 50));
        assertEquals(1, SleepingRule.required(2, 50));
        assertEquals(2, SleepingRule.required(3, 50));
        assertEquals(2, SleepingRule.required(4, 50));
        assertEquals(3, SleepingRule.required(5, 50));
        assertEquals(1, SleepingRule.required(10, 0));
    }

    @Test
    void defaultsToFiftyPercentWithMinimumOff() {
        assertEquals(50, rule.percent());
        assertEquals(0, rule.minPlayers());
        for (int active = 0; active <= 200; active++) assertEquals(50, rule.effectivePercent(active));
    }

    @Test
    void minimumPlayersWinsOnBigServersAndPercentWinsOnSmallOnes() {
        rule.configure(50, 10);
        assertEquals(10, rule.needed(30));
        assertEquals(33, rule.effectivePercent(30));
        assertEquals(10, SleepingRule.required(30, 33));
        assertEquals(2, rule.needed(4));
        assertEquals(50, rule.effectivePercent(4));
        assertEquals(10, rule.needed(20));
        assertEquals(50, rule.effectivePercent(20));
    }

    @Test
    void effectivePercentRequiresExactlyTheNeededSleepersUpToOneHundredPlayers() {
        for (int percent = 0; percent <= 100; percent++) {
            for (int min = 0; min <= 101; min++) {
                rule.configure(percent, min);
                for (int active = 0; active <= 100; active++) {
                    int value = rule.effectivePercent(active);
                    assertTrue(value >= 0 && value <= percent);
                    assertEquals(rule.needed(active), SleepingRule.required(active, value),
                            "percent=" + percent + " min=" + min + " active=" + active);
                }
            }
        }
    }

    @Test
    void neverRequiresMoreThanTheTargetOnHugeWorlds() {
        rule.configure(50, 10);
        for (int active = 101; active <= 1000; active++) {
            int needed = SleepingRule.required(active, rule.effectivePercent(active));
            assertTrue(needed <= 10, "active=" + active);
        }
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> rule.configure(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> rule.configure(101, 0));
        assertThrows(IllegalArgumentException.class, () -> rule.configure(50, -1));
    }

    @Test
    void countsOnlyNonSpectatorsAndSetsGameruleOnlyWhenItChanges() {
        rule.configure(50, 2);
        World world = world(30, 50);
        var crowd = players(6, 3);
        when(world.getPlayers()).thenReturn(crowd);
        rule.apply(world);
        // 6 active players: 50% needs 3, minimum needs 2 -> 33% gives ceil(1.98) = 2.
        verify(world).setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 33);

        World settled = world(33, 33);
        var six = players(6, 0);
        when(settled.getPlayers()).thenReturn(six);
        rule.apply(settled);
        verify(settled, never()).setGameRule(any(), any());
        verify(logger, never()).severe(anyString());
    }

    @Test
    void reportsFailureWithWorldName() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("custom");
        when(world.getPlayers()).thenReturn(List.of());
        when(world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE)).thenReturn(100);
        when(world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50)).thenReturn(false);
        rule.apply(world);
        verify(logger).severe("Could not set the sleeping percentage to 50 in world custom");
    }

    private World world(int current, int accepted) {
        World world = mock(World.class);
        when(world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE)).thenReturn(current);
        when(world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, accepted)).thenReturn(true);
        when(world.setGameRule(org.mockito.ArgumentMatchers.eq(GameRules.PLAYERS_SLEEPING_PERCENTAGE),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        return world;
    }

    private List<Player> players(int active, int spectators) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < active + spectators; i++) {
            Player player = mock(Player.class);
            when(player.getGameMode()).thenReturn(i < active ? GameMode.SURVIVAL : GameMode.SPECTATOR);
            players.add(player);
        }
        return players;
    }
}
