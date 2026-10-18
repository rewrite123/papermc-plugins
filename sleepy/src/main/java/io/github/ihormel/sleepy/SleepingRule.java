package io.github.ihormel.sleepy;

import java.util.logging.Logger;
import org.bukkit.GameRules;
import org.bukkit.World;

final class SleepingRule {
    private final Logger logger;

    SleepingRule(Logger logger) {
        this.logger = logger;
    }

    void apply(World world) {
        if (!world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50)) {
            logger.severe("Could not set the sleeping percentage to 50 in world " + world.getName());
        }
    }
}
