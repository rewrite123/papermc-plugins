package io.github.ihormel.minecarttravel;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TravelSettingsTest {
    private YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
    }

    @Test
    void defaultsMatchTheAgreedTimingSpeedAndWorkBudgets() {
        var settings = TravelSettings.read(defaults());
        assertEquals(600, settings.boostTicks());
        assertEquals(2400, settings.fastTicks());
        assertEquals(6, settings.minimumSpeed());
        assertEquals(90, settings.boostSpeed());
        assertTrue(settings.boostSpeed() > 73);
        assertEquals(32, settings.maxTurns());
        assertEquals(65536, settings.maxRails());
        assertEquals(512, settings.routeBudget());
        assertEquals(4096, settings.scanBudget());
    }

    @Test
    void invalidConfigurationFailsInsteadOfQuietlyFallingBack() {
        for (Object value : java.util.List.of(-1, 0, 2.5, "ten")) {
            var config = defaults();
            config.set("boost-after-ticks", value);
            assertThrows(IllegalArgumentException.class, () -> TravelSettings.read(config));
        }
        var badTiming = defaults();
        badTiming.set("fast-travel-after-ticks", 100);
        assertThrows(IllegalArgumentException.class, () -> TravelSettings.read(badTiming));
        var nonFinite = defaults();
        nonFinite.set("boost-blocks-per-second", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> TravelSettings.read(nonFinite));
    }
}
