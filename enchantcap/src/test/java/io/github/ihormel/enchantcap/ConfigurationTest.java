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
    void consoleCommandSavesAndImmediatelyUpdatesLiveListenerIncludingResetToZero() {
        var console = server.getConsoleSender();
        assertTrue(server.dispatchCommand(console, "enchantcap max-additional-enchantment-levels 1"));
        assertEquals(1, plugin.getConfig().getInt("max-additional-enchantment-levels"));
        plugin.reloadConfig();
        assertEquals(1, plugin.getConfig().getInt("max-additional-enchantment-levels"));
        var view = mock(AnvilView.class);
        server.getPluginManager().callEvent(new InventoryOpenEvent(view));
        verify(view).bypassEnchantmentLevelRestriction(true);
        assertTrue(server.dispatchCommand(console, "enchantcap max-additional-enchantment-levels 0"));
        when(view.bypassesEnchantmentLevelRestriction()).thenReturn(true);
        server.getPluginManager().callEvent(new InventoryOpenEvent(view));
        verify(view).bypassEnchantmentLevelRestriction(false);
    }

    @Test
    void unauthorizedPlayerCannotChangeSettingButOperatorCan() {
        var player = server.addPlayer();
        server.dispatchCommand(player, "enchantcap max-additional-enchantment-levels 2");
        assertEquals(0, plugin.getConfig().getInt("max-additional-enchantment-levels"));
        player.setOp(true);
        server.dispatchCommand(player, "enchantcap max-additional-enchantment-levels 2");
        assertEquals(2, plugin.getConfig().getInt("max-additional-enchantment-levels"));
        server.dispatchCommand(player, "enchantcap max-additional-enchantment-levels");
        assertTrue(player.nextMessage().contains("permission"));
        assertTrue(player.nextMessage().contains("set to 2"));
        assertEquals("max-additional-enchantment-levels = 2", player.nextMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "255", "1.5", "wrong", "999999999999"})
    void commandRejectsInvalidValuesWithoutSaving(String value) {
        var console = server.getConsoleSender();
        server.dispatchCommand(console, "enchantcap max-additional-enchantment-levels " + value);
        assertEquals(0, plugin.getConfig().getInt("max-additional-enchantment-levels"));
        assertTrue(console.nextMessage().contains("whole number"));
    }

    @Test
    void failedSaveReportsErrorAndKeepsActiveSetting() throws java.io.IOException {
        var path = plugin.getDataFolder().toPath().resolve("config.yml");
        var original = java.nio.file.Files.readString(path);
        java.nio.file.Files.delete(path);
        java.nio.file.Files.createDirectory(path);
        try {
            var console = server.getConsoleSender();
            server.dispatchCommand(console, "enchantcap max-additional-enchantment-levels 1");
            assertEquals(0, plugin.getConfig().getInt("max-additional-enchantment-levels"));
            assertTrue(console.nextMessage().contains("Could not save"));
            var view = mock(AnvilView.class);
            server.getPluginManager().callEvent(new InventoryOpenEvent(view));
            verify(view, never()).bypassEnchantmentLevelRestriction(true);
        } finally {
            java.nio.file.Files.delete(path);
            java.nio.file.Files.writeString(path, original);
        }
    }

    @Test
    void tabCompletionRequiresPermissionAndSuggestsSettingAndValues() {
        var command = plugin.getCommand("enchantcap");
        var player = server.addPlayer();
        assertTrue(plugin.onTabComplete(player, command, "enchantcap", new String[] {""}).isEmpty());
        player.setOp(true);
        assertEquals(java.util.List.of("max-additional-enchantment-levels"),
                plugin.onTabComplete(player, command, "enchantcap", new String[] {"max"}));
        assertEquals(java.util.List.of("0", "1", "2"),
                plugin.onTabComplete(player, command, "enchantcap",
                        new String[] {"max-additional-enchantment-levels", ""}));
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
