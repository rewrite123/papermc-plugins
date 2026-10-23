package io.github.ihormel.votersrights;

import java.util.UUID;
import java.util.Locale;
import org.bukkit.Difficulty;

record Proposal(String action, UUID target, String targetName, Difficulty difficulty) {
    String description() {
        return action + (targetName != null ? " " + targetName
                : difficulty != null ? " " + difficulty.name().toLowerCase(Locale.ROOT) : "");
    }
}
