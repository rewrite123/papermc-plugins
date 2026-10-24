package io.github.ihormel.soulgem;

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
    private static final String GUIDE_URL = "https://github.com/rewrite123/papermc-plugins/tree/main/soulgem";
    private ServerMock server;
    private SoulGemPlugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SoulGemPlugin.class);
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
        assertTrue(text.contains("lapis lazuli + 1 amethyst shard"));
        assertTrue(text.contains("off-hand"));
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
        assertTrue(topic.getFullText(player).contains("Echo Soul Gems"));
        server.getScheduler().performOneTick();
        Component links = player.nextComponentMessage();
        assertNotNull(links);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(links).contains("[Full guide on GitHub]"));
        assertTrue(links.children().stream().anyMatch(child ->
                ClickEvent.openUrl(GUIDE_URL).equals(child.clickEvent())));
    }
}
