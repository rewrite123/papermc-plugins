package io.github.ihormel.votersrights;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VotingSettingsTest {
    private YamlConfiguration config;

    @BeforeEach
    void setup() {
        config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
    }

    @Test
    void defaultsMatchAllRequestedLimitsAndCaseInsensitiveActions() {
        var settings = VotingSettings.load(config);
        assertEquals(6, settings.minimumView());
        assertEquals(18, settings.maximumView());
        assertEquals(4, settings.minimumSimulation());
        assertEquals(12, settings.maximumSimulation());
        assertEquals("increaseViewDistance", settings.actionName("INCREASEVIEWDISTANCE"));
        assertTrue(settings.names().stream().allMatch(settings::enabled));
    }

    @Test
    void whitelistModeAndBlacklistPrecedenceAreUuidBased() {
        var first = UUID.randomUUID();
        var other = UUID.randomUUID();
        config.set("whitelist-enabled", true);
        config.set("whitelist", List.of(first.toString()));
        var settings = VotingSettings.load(config);
        assertTrue(settings.eligible(first));
        assertFalse(settings.eligible(other));
        config.set("blacklist", List.of(first.toString()));
        assertFalse(VotingSettings.load(config).eligible(first));
    }

    @Test
    void configurationRequiresCorrectTypesRatherThanSilentlyDefaulting() {
        for (Object value : List.of("50", 50.0, -1, 101, 2147483648L)) {
            config.set("pass-percentage", value);
            assertThrows(IllegalArgumentException.class, () -> VotingSettings.load(config));
        }
        config.set("pass-percentage", 50);
        config.set("actions.restart", "false");
        assertThrows(IllegalArgumentException.class, () -> VotingSettings.load(config));
        config.set("actions.restart", false);
        config.set("allowed-difficulties", List.of());
        assertThrows(IllegalArgumentException.class, () -> VotingSettings.load(config));
        config.set("allowed-difficulties", List.of("easy"));
        config.set("blacklist", List.of("player-name-instead-of-uuid"));
        assertThrows(IllegalArgumentException.class, () -> VotingSettings.load(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "no", "restart", "increaseviewdistance", "bad.name", "UPPERCASE", "1action", "a b"})
    void customActionNamesCannotCollideOrCreateNestedPaths(String name) {
        assertThrows(IllegalArgumentException.class, () -> VotingSettings.validateCustomName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/stop", "say hello\nstop", "kick {player}", "say hi\rstop"})
    void customCommandsRejectSubstitutionsAndMultipleCommandLines(String command) {
        assertThrows(IllegalArgumentException.class, () -> VotingSettings.validateCommand(command));
    }

    @Test
    void customDefinitionsMustHaveEnabledFlagAndNonemptyStringCommands() {
        config.set("custom-actions.example.enabled", true);
        config.set("custom-actions.example.commands", List.of());
        assertThrows(IllegalArgumentException.class, () -> VotingSettings.load(config));
        config.set("custom-actions.example.commands", List.of(123));
        assertThrows(IllegalArgumentException.class, () -> VotingSettings.load(config));
        config.set("custom-actions.example.commands", List.of("say approved"));
        assertEquals(List.of("say approved"), VotingSettings.load(config).customActions().get("example").commands());
        config.set("custom-actions.example.enabled", "true");
        assertThrows(IllegalArgumentException.class, () -> VotingSettings.load(config));
    }
}
