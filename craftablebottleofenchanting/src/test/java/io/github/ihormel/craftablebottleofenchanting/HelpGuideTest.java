package io.github.ihormel.craftablebottleofenchanting;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelpGuideTest {
    private static final String GUIDE_URL =
            "https://github.com/rewrite123/papermc-plugins/tree/main/craftablebottleofenchanting";
    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getName()).thenReturn("craftablebottleofenchanting");
        when(plugin.getPluginMeta().getDescription())
                .thenReturn("Bottle experience at enchanting tables without lapis and recover empty bottles.");
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getResource("help.txt")).thenReturn(HelpGuideTest.class.getResourceAsStream("/help.txt"));
        HelpGuide.register(plugin, "craftablebottleofenchanting");
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void consoleGuideLinksToGitHubAndExplainsUse() throws Exception {
        var topic = server.getHelpMap().getHelpTopic(plugin.getName());
        assertNotNull(topic);
        assertEquals(plugin.getPluginMeta().getDescription(), topic.getShortText());
        var text = topic.getFullText(server.getConsoleSender());
        assertTrue(text.contains(GUIDE_URL));
        assertTrue(text.contains("https://github.com/rewrite123/papermc-plugins/issues"));
        assertTrue(text.contains("11 raw XP points"));
        assertTrue(text.contains("Right-click an enchanting table"));
        try (var reader = new InputStreamReader(plugin.getResource("plugin.yml"), StandardCharsets.UTF_8)) {
            var commands = YamlConfiguration.loadConfiguration(reader).getConfigurationSection("commands");
            if (commands != null) {
                for (String command : commands.getKeys(false)) {
                    assertTrue(text.contains("/" + command), "Guide is missing /" + command);
                }
            }
        }
    }

    @Test
    void playerGetsClickableGuideLink() {
        var topic = server.getHelpMap().getHelpTopic(plugin.getName());
        var player = server.addPlayer("Steve");
        assertTrue(topic.getFullText(player).contains("Bottle Experience"));
        server.getScheduler().performOneTick();
        Component links = player.nextComponentMessage();
        assertNotNull(links);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(links).contains("[Full guide on GitHub]"));
        assertTrue(links.children().stream().anyMatch(child ->
                ClickEvent.openUrl(GUIDE_URL).equals(child.clickEvent())));
    }
}
