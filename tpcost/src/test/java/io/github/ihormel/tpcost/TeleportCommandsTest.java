package io.github.ihormel.tpcost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeleportCommandsTest {
    private final Server server = mock(Server.class);
    private final World world = mock(World.class);
    private final AtomicLong time = new AtomicLong();
    private final TeleportRequests requests = new TeleportRequests(time::get);
    private final TeleportCommands commands = new TeleportCommands(server, requests);
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() {
        when(world.getUID()).thenReturn(UUID.randomUUID());
        alice = player("Alice", 0);
        bob = player("Bob", 3000);
        when(alice.teleport(any(Location.class), any(TeleportCause.class))).thenReturn(true);
    }

    @Test
    void requiresApprovalAndChargesExactlyOnceOnlyAfterSuccess() {
        run(alice, "tpa", "Bob");
        verify(alice, never()).teleport(any(Location.class), any(TeleportCause.class));
        verify(alice, never()).giveExpLevels(anyInt());
        run(bob, "tpaccept");
        var order = inOrder(alice);
        order.verify(alice).teleport(bob.getLocation(), TeleportCause.PLUGIN);
        order.verify(alice).giveExpLevels(-9);
        run(bob, "tpaccept", "Alice");
        verify(alice).giveExpLevels(-9);
        verify(bob, never()).giveExpLevels(anyInt());
    }

    @Test
    void recalculatesPriceAndWorldSurchargeAtAcceptance() {
        run(alice, "tpa", "Bob");
        World otherWorld = mock(World.class);
        when(otherWorld.getUID()).thenReturn(UUID.randomUUID());
        when(bob.getLocation()).thenReturn(new Location(otherWorld, 3330, 64, 0));
        run(bob, "tpaccept", "Alice");
        verify(alice).giveExpLevels(-13);
    }

    @Test
    void permitsPayingWithExactlyEnoughLevels() {
        when(alice.getLevel()).thenReturn(9);
        run(alice, "tpa", "Bob");
        run(bob, "tpaccept");
        verify(alice).giveExpLevels(-9);
    }

    @Test
    void cancelledTeleportDoesNotCharge() {
        when(alice.teleport(any(Location.class), any(TeleportCause.class))).thenReturn(false);
        run(alice, "tpa", "Bob");
        run(bob, "tpaccept");
        verify(alice, never()).giveExpLevels(anyInt());
        verify(alice).sendMessage(contains("No levels were charged"));
        assertTrue(requests.incoming(bob.getUniqueId()).isEmpty());
    }

    @Test
    void insufficientLevelsAtRequestDoNotCreateRequest() {
        when(alice.getLevel()).thenReturn(8);
        run(alice, "tpa", "Bob");
        assertTrue(requests.incoming(bob.getUniqueId()).isEmpty());
        verify(alice).sendMessage(contains("You need 9 levels"));
    }

    @Test
    void insufficientLevelsAfterMovementDoNotTeleportOrCharge() {
        when(alice.getLevel()).thenReturn(9);
        run(alice, "tpa", "Bob");
        when(bob.getLocation()).thenReturn(new Location(world, 3330, 64, 0));
        run(bob, "tpaccept");
        verify(alice, never()).teleport(any(Location.class), any(TeleportCause.class));
        verify(alice, never()).giveExpLevels(anyInt());
        verify(alice).sendMessage(contains("you now need 10 levels"));
    }

    @Test
    void denialCancellationAndExpiryDoNotCharge() {
        run(alice, "tpa", "Bob");
        run(bob, "tpdeny", "Alice");
        assertTrue(requests.incoming(bob.getUniqueId()).isEmpty());
        run(alice, "tpa", "Bob");
        run(alice, "tpcancel");
        assertTrue(requests.incoming(bob.getUniqueId()).isEmpty());
        run(alice, "tpa", "Bob");
        time.set(TeleportRequests.LIFETIME.toNanos());
        run(bob, "tpaccept");
        verify(alice).sendMessage(contains("request expired"));
        verify(alice, never()).teleport(any(Location.class), any(TeleportCause.class));
        verify(alice, never()).giveExpLevels(anyInt());
    }

    @Test
    void quoteDoesNotCreateRequestOrCharge() {
        run(alice, "tpcost", "Bob");
        verify(alice).sendMessage(contains("currently costs 9 levels"));
        assertTrue(requests.incoming(bob.getUniqueId()).isEmpty());
        verify(alice, never()).giveExpLevels(anyInt());
    }

    @Test
    void multipleRequestsRequireExplicitSender() {
        Player charlie = player("Charlie", 500);
        run(alice, "tpa", "Bob");
        run(charlie, "tpa", "Bob");
        run(bob, "tpaccept");
        assertEquals(2, requests.incoming(bob.getUniqueId()).size());
        verify(bob).sendMessage(contains("multiple requests"));
        run(bob, "tpaccept", "Alice");
        verify(alice).giveExpLevels(-9);
        assertEquals(1, requests.incoming(bob.getUniqueId()).size());
    }

    @Test
    void onlyRecipientCanAcceptRequest() {
        Player charlie = player("Charlie", 500);
        run(alice, "tpa", "Bob");
        run(charlie, "tpaccept", "Alice");
        verify(alice, never()).teleport(any(Location.class), any(TeleportCause.class));
        assertEquals(1, requests.incoming(bob.getUniqueId()).size());
    }

    @Test
    void rejectsSelfOfflineAndHiddenPlayers() {
        run(alice, "tpa", "Alice");
        run(alice, "tpa", "Missing");
        when(alice.canSee(bob)).thenReturn(false);
        run(alice, "tpa", "Bob");
        assertTrue(requests.incoming(bob.getUniqueId()).isEmpty());
        assertTrue(requests.incoming(alice.getUniqueId()).isEmpty());
    }

    @Test
    void permissionLossOrDeathPreventsAcceptedTeleport() {
        run(alice, "tpa", "Bob");
        when(alice.hasPermission("tpcost.use")).thenReturn(false);
        run(bob, "tpaccept");
        when(alice.hasPermission("tpcost.use")).thenReturn(true);
        run(alice, "tpa", "Bob");
        when(alice.isDead()).thenReturn(true);
        run(bob, "tpaccept");
        verify(alice, never()).teleport(any(Location.class), any(TeleportCause.class));
        verify(alice, never()).giveExpLevels(anyInt());
    }

    @Test
    void disconnectCancelsRequestsAndNotifiesOtherPlayer() {
        run(alice, "tpa", "Bob");
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(bob);
        commands.onQuit(event);
        assertTrue(requests.incoming(bob.getUniqueId()).isEmpty());
        verify(alice).sendMessage(contains("Bob disconnected"));
    }

    @Test
    void tabCompletionFiltersHiddenPlayersAndIncomingRequests() {
        when(server.getOnlinePlayers()).thenAnswer(invocation -> List.of(alice, bob));
        assertEquals(List.of("Bob"), commands.onTabComplete(alice, command("tpa"), "tpa", new String[]{"b"}));
        run(alice, "tpa", "Bob");
        assertEquals(List.of("Alice"), commands.onTabComplete(bob, command("tpaccept"), "tpaccept", new String[]{""}));
        when(alice.canSee(bob)).thenReturn(false);
        assertTrue(commands.onTabComplete(alice, command("tpa"), "tpa", new String[]{""}).isEmpty());
    }

    private Player player(String name, double x) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.getLocation()).thenReturn(new Location(world, x, 64, 0));
        when(player.getLevel()).thenReturn(30);
        when(player.hasPermission("tpcost.use")).thenReturn(true);
        when(player.canSee(any(Player.class))).thenReturn(true);
        when(server.getPlayerExact(name)).thenReturn(player);
        when(server.getPlayer(id)).thenReturn(player);
        return player;
    }

    private void run(Player player, String name, String... args) {
        assertTrue(commands.onCommand(player, command(name), name, args));
    }

    private Command command(String name) {
        Command command = mock(Command.class);
        when(command.getName()).thenReturn(name);
        return command;
    }
}
