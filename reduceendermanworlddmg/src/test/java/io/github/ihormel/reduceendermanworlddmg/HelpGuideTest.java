package io.github.ihormel.reduceendermanworlddmg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpMap;
import org.bukkit.help.HelpTopic;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HelpGuideTest {
    private static final String GUIDE_URL = "https://github.com/rewrite123/papermc-plugins/tree/main/reduceendermanworlddmg";
    private Plugin plugin;
    private Server server;
    private HelpMap helpMap;
    private BukkitScheduler scheduler;
    private HelpGuide guide;

    @BeforeEach
    void setup() {
        plugin = mock(Plugin.class);
        server = mock(Server.class);
        helpMap = mock(HelpMap.class);
        scheduler = mock(BukkitScheduler.class);
        var meta = mock(PluginMeta.class);
        when(plugin.getName()).thenReturn("reduceendermanworlddmg");
        when(plugin.getPluginMeta()).thenReturn(meta);
        when(meta.getDescription()).thenReturn("Enderman return guide");
        when(plugin.getServer()).thenReturn(server);
        when(server.getHelpMap()).thenReturn(helpMap);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getResource("help.txt")).thenAnswer(ignored -> resource());
        guide = HelpGuide.register(plugin, "reduceendermanworlddmg");
        when(helpMap.getHelpTopic(plugin.getName())).thenReturn(guide);
    }

    private static InputStream resource() {
        return HelpGuideTest.class.getClassLoader().getResourceAsStream("help.txt");
    }

    @Test
    void registeredTopicExplainsUsageAndConsoleGetsLinks() {
        HelpTopic topic = helpMap.getHelpTopic(plugin.getName());
        assertNotNull(topic);
        assertEquals("Enderman return guide", topic.getShortText());
        String text = topic.getFullText(mock(CommandSender.class));
        assertTrue(text.contains("random return deadline of 10 to 45 seconds"));
        assertTrue(text.contains("nearby fallback"));
        assertTrue(text.contains("There are no plugin commands"));
        assertTrue(text.contains(GUIDE_URL));
        assertTrue(text.contains("https://github.com/rewrite123/papermc-plugins/issues"));
    }

    @Test
    void playersReceiveClickableLinksAndNoRawUrls() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        String text = guide.getFullText(player);
        assertTrue(text.contains("mobGriefing"));
        assertFalse(text.contains(GUIDE_URL));
        assertFalse(text.contains("[admin:"));
        assertFalse(text.contains("[/admin]"));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(message.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(message.getValue())
                .contains("[Full guide on GitHub]"));
        assertTrue(message.getValue().children().stream()
                .anyMatch(child -> ClickEvent.openUrl(GUIDE_URL).equals(child.clickEvent())));
    }
}
