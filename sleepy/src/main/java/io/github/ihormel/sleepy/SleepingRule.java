package io.github.ihormel.sleepy;

import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;

/**
 * Keeps each world's vanilla sleeping-percentage gamerule at the value that makes
 * the night skip once either the configured percentage or the configured number
 * of players is sleeping, whichever needs fewer players.
 */
final class SleepingRule {
    static final int DEFAULT_PERCENT = 50;
    static final int OFF = 0;

    private final Logger logger;
    private int percent = DEFAULT_PERCENT;
    private int minPlayers = OFF;

    SleepingRule(Logger logger) {
        this.logger = logger;
    }

    int percent() {
        return percent;
    }

    int minPlayers() {
        return minPlayers;
    }

    void configure(int percent, int minPlayers) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Percent must be 0-100");
        if (minPlayers < 0) throw new IllegalArgumentException("Minimum players must not be negative");
        this.percent = percent;
        this.minPlayers = minPlayers;
    }

    /** Vanilla's sleeper requirement: at least one, otherwise the percentage rounded up. */
    static int required(int active, int percent) {
        return Math.max(1, (int) Math.ceil(active * percent / 100.0F));
    }

    static int active(World world) {
        return (int) world.getPlayers().stream().filter(p -> p.getGameMode() != GameMode.SPECTATOR).count();
    }

    /** Sleepers needed in a world with this many active players. */
    int needed(int active) {
        int byPercent = required(active, percent);
        return minPlayers == OFF ? byPercent : Math.min(byPercent, minPlayers);
    }

    /** Gamerule value that makes vanilla require {@link #needed} sleepers. */
    int effectivePercent(int active) {
        int target = needed(active);
        if (required(active, percent) <= target) return percent;
        // Largest percentage whose requirement does not exceed the target; exact for up to 100 players.
        int best = 0;
        for (int p = 1; p < percent; p++) {
            if (required(active, p) <= target) best = p;
        }
        return best;
    }

    void apply(World world) {
        int value = effectivePercent(active(world));
        Integer current = world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (current != null && current == value) return;
        if (!world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, value)) {
            logger.severe("Could not set the sleeping percentage to " + value + " in world " + world.getName());
        }
    }
}
