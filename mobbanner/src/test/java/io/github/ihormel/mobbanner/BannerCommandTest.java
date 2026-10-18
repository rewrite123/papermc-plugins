package io.github.ihormel.mobbanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class BannerCommandTest extends PaperTest {
    @Test
    void adminCanGiveMarkedBannerWithoutChangingKillCounts() {
        var target = server.addPlayer("Alex");
        var admin = mock(CommandSender.class);
        when(admin.hasPermission("mobbanner.give")).thenReturn(true);
        var command = new BannerCommand(server, items);

        assertTrue(command.onCommand(admin, mock(Command.class), "mobbanner",
                new String[]{"give", "Alex", "WITHER_SKELETON"}));

        assertEquals(EntityType.WITHER_SKELETON, items.read(target.getInventory().getItem(0)));
        assertTrue(target.getPersistentDataContainer().getKeys().isEmpty());
        assertEquals("An administrator gave you a Wither Skeleton Ward Banner.", target.nextMessage());
        verify(admin).sendMessage("Gave a Wither Skeleton Ward Banner to Alex.");
    }

    @Test
    void deniesNonAdminsWithoutGivingItemsOrCompletions() {
        var player = server.addPlayer("Alex");
        var sender = mock(CommandSender.class);
        var command = new BannerCommand(server, items);
        assertTrue(command.onCommand(sender, mock(Command.class), "mobbanner",
                new String[]{"give", "Alex", "bat"}));
        verify(sender).sendMessage("You do not have permission to give mob banners.");
        assertTrue(player.getInventory().isEmpty());
        assertTrue(command.onTabComplete(sender, mock(Command.class), "mobbanner",
                new String[]{"give", "Alex", ""}).isEmpty());
    }

    @Test
    void reportsOfflineAndUnsupportedTargetsAndRejectsBadSyntax() {
        var player = server.addPlayer("Alex");
        var sender = mock(CommandSender.class);
        when(sender.hasPermission("mobbanner.give")).thenReturn(true);
        var command = new BannerCommand(server, items);
        assertTrue(command.onCommand(sender, mock(Command.class), "mobbanner",
                new String[]{"give", "Missing", "bat"}));
        verify(sender).sendMessage("That player is not online.");
        assertTrue(command.onCommand(sender, mock(Command.class), "mobbanner",
                new String[]{"give", "Alex", "cow"}));
        verify(sender).sendMessage("Unsupported mob type. Use tab completion; multiword types use underscores.");
        assertFalse(command.onCommand(sender, mock(Command.class), "mobbanner", new String[]{"give"}));
        assertTrue(player.getInventory().isEmpty());
    }

    @Test
    void tabCompletionIncludesSupportedMobsAndOnlinePlayers() {
        server.addPlayer("Alex");
        var sender = mock(CommandSender.class);
        when(sender.hasPermission("mobbanner.give")).thenReturn(true);
        var command = new BannerCommand(server, items);
        assertEquals(java.util.List.of("give"), command.onTabComplete(sender, mock(Command.class),
                "mobbanner", new String[]{"g"}));
        assertEquals(java.util.List.of("Alex"), command.onTabComplete(sender, mock(Command.class),
                "mobbanner", new String[]{"give", "Al"}));
        assertEquals(java.util.List.of("bat"), command.onTabComplete(sender, mock(Command.class),
                "mobbanner", new String[]{"give", "Alex", "ba"}));
        assertEquals(java.util.List.of("wither", "wither_skeleton"), command.onTabComplete(sender,
                mock(Command.class), "mobbanner", new String[]{"give", "Alex", "WITHER"}));
    }
}
