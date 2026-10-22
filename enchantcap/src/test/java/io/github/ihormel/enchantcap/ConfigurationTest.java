package io.github.ihormel.enchantcap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.view.AnvilView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class ConfigurationTest {
    private ServerMock server;
    private EnchantCapPlugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(EnchantCapPlugin.class);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void defaultConfigurationExistsAndKeepsVanillaLevelRestriction() {
        assertTrue(plugin.getDataFolder().toPath().resolve("config.yml").toFile().isFile());
        assertEquals(0, plugin.getConfig().getInt("max-additional-enchantment-levels"));
        var view = mock(AnvilView.class);
        var event = new InventoryOpenEvent(view);
        server.getPluginManager().callEvent(event);
        verify(view).setMaximumRepairCost(Integer.MAX_VALUE);
        verify(view, never()).bypassEnchantmentLevelRestriction(anyBoolean());
    }

    @Test
    void restartingReadsPersistedExtraLevelSettingAndEnablesNativeMerging() {
        server.getPluginManager().disablePlugin(plugin);
        plugin.getConfig().set("max-additional-enchantment-levels", 1);
        plugin.saveConfig();
        plugin.reloadConfig();
        server.getPluginManager().enablePlugin(plugin);
        var view = mock(AnvilView.class);
        var event = new InventoryOpenEvent(view);
        server.getPluginManager().callEvent(event);
        verify(view).bypassEnchantmentLevelRestriction(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "255", "1.5", "wrong"})
    void invalidConfigurationFailsExplicitly(String text) {
        Object value = switch (text) {
            case "-1" -> -1;
            case "255" -> 255;
            case "1.5" -> 1.5;
            default -> text;
        };
        plugin.getConfig().set("max-additional-enchantment-levels", value);
        assertThrows(IllegalArgumentException.class, plugin::onEnable);
    }
}
