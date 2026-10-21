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
        assertEquals(300, settings.boostTicks());
        assertEquals(600, settings.fastTicks());
        assertEquals(6, settings.minimumSpeed());
        assertEquals(90, settings.boostSpeed());
        assertTrue(settings.boostSpeed() > 73);
        assertEquals(32, settings.maxTurns());
        assertEquals(65536, settings.maxRails());
        assertEquals(512, settings.recordBudget());
        assertEquals(4096, settings.verifyBudget());
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

    @Test
    void adminAdjustmentsConvertSecondsAndPersistIntoTheConfig() {
        var config = defaults();
        var settings = TravelSettings.read(config);
        settings = settings.adjust(config, TravelSettings.Option.BOOST_DELAY, "10");
        assertEquals(200, settings.boostTicks());
        assertEquals(200, config.getInt("boost-after-ticks"));
        settings = settings.adjust(config, TravelSettings.Option.FAST_TRAVEL_DELAY, "45.5");
        assertEquals(910, settings.fastTicks());
        settings = settings.adjust(config, TravelSettings.Option.MIN_SPEED, "4");
        assertEquals(4, settings.minimumSpeed());
        settings = settings.adjust(config, TravelSettings.Option.BOOST_SPEED, "60");
        assertEquals(60, settings.boostSpeed());
        assertEquals("10 seconds", settings.describe(TravelSettings.Option.BOOST_DELAY));
        assertEquals("45.5 seconds", settings.describe(TravelSettings.Option.FAST_TRAVEL_DELAY));
        assertEquals(TravelSettings.Option.MIN_SPEED, TravelSettings.Option.named("MIN-SPEED"));
    }

    @Test
    void fasterBoostRaisesTheSafetyLookaheadToKeepUp() {
        var config = defaults();
        config.set("safety-lookahead-blocks", TravelSettings.minimumLookahead(90));
        var settings = TravelSettings.read(config).adjust(config, TravelSettings.Option.BOOST_SPEED, "120");
        assertEquals(TravelSettings.minimumLookahead(120), settings.lookahead());
        assertTrue(settings.lookahead() > TravelSettings.minimumLookahead(90));
        assertEquals(settings.lookahead(), config.getInt("safety-lookahead-blocks"));
    }

    @Test
    void rejectedAdjustmentsLeaveSettingsAndConfigUnchanged() {
        var config = defaults();
        var settings = TravelSettings.read(config);
        for (var bad : java.util.List.of(
                new String[] {"boost-delay", "120"}, new String[] {"boost-delay", "0"},
                new String[] {"fast-travel-delay", "15"}, new String[] {"fast-travel-delay", "abc"},
                new String[] {"min-speed", "9"}, new String[] {"min-speed", "NaN"},
                new String[] {"boost-speed", "5"}, new String[] {"boost-speed", "121"})) {
            var option = TravelSettings.Option.named(bad[0]);
            assertThrows(IllegalArgumentException.class, () -> settings.adjust(config, option, bad[1]), bad[0] + " " + bad[1]);
        }
        assertEquals(settings, TravelSettings.read(config));
        assertEquals(defaults().getValues(true), config.getValues(true));
        assertThrows(IllegalArgumentException.class, () -> TravelSettings.Option.named("speed"));
    }
}
