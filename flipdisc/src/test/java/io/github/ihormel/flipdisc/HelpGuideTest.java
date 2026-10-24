package io.github.ihormel.flipdisc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class HelpGuideTest {
    private static final String GUIDE_URL = "https://github.com/rewrite123/papermc-plugins/tree/main/flipdisc";
    private ServerMock server;
    private FlipDiscPlugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FlipDiscPlugin.class);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void consoleGuideLinksToGitHubAndListsDeclaredCommands() throws Exception {
        var topic = server.getHelpMap().getHelpTopic(plugin.getName());
        assertNotNull(topic);
        assertEquals(plugin.getPluginMeta().getDescription(), topic.getShortText());
        var text = topic.getFullText(server.getConsoleSender());
        assertTrue(text.contains(GUIDE_URL));
        assertTrue(text.contains("https://github.com/rewrite123/papermc-plugins/issues"));
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
    void playerGetsClickableLinksAndNoAdminMarkers() {
        var topic = server.getHelpMap().getHelpTopic(plugin.getName());
        var player = server.addPlayer("Steve");
        var playerText = topic.getFullText(player);
        assertFalse(playerText.contains("[admin:"));
        assertFalse(playerText.contains("[/admin]"));
        assertFalse(playerText.contains(GUIDE_URL));
        server.getScheduler().performOneTick();
        Component links = player.nextComponentMessage();
        assertNotNull(links);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(links).contains("[Full guide on GitHub]"));
        assertTrue(links.children().stream().anyMatch(child -> ClickEvent.openUrl(GUIDE_URL).equals(child.clickEvent())));
    }
}
