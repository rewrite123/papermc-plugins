package io.github.ihormel.votersrights;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.InetAddress;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Difficulty;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class VotersRightsTest {
    private ServerMock server;
    private VotersRightsPlugin plugin;
    private PlayerMock alice;
    private PlayerMock bob;
    private PlayerMock charlie;
    private WorldMock world;
    private final AtomicLong time = new AtomicLong(1_000_000);

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(VotersRightsPlugin.class);
        plugin.clock(time::get);
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        charlie = server.addPlayer("Charlie");
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    private void vote(PlayerMock player, String args) { server.dispatchCommand(player, "vote " + args); }
    private void admin(String args) { server.dispatchCommand(server.getConsoleSender(), "votersrights " + args); }
    private String messages(PlayerMock player) {
        StringBuilder text = new StringBuilder();
        String message;
        while ((message = player.nextMessage()) != null) text.append(message).append('\n');
        return text.toString();
    }

    @Test
    void savedTimeoutWarningSurvivesPluginRestartAndUsesOriginalDurations() throws Exception {
        vote(alice, "timeout Charlie");
        vote(bob, "yes");
        var saved = plugin.state().timeouts().get(charlie.getUniqueId());
        admin("set timeout-duration-minutes 1");
        admin("set timeout-protection-minutes 1");
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        assertEquals(saved, plugin.state().timeouts().get(charlie.getUniqueId()));
        assertTrue(plugin.state().pending(time.get()));
        time.set(saved.startsAt());
        var event = login(charlie);
        plugin.onLogin(event);
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
    }

    @Test
    void failedActionSaveDoesNotStartTimeoutAndReportsFailure() throws Exception {
        var stateFile = plugin.getDataFolder().toPath().resolve("state.yml");
        Files.createDirectory(stateFile);
        Files.writeString(stateFile.resolve("obstruction"), "test");
        try {
            vote(alice, "timeout Charlie");
            vote(bob, "yes");
            assertTrue(messages(alice).contains("could not be completed"));
            assertTrue(plugin.state().timeouts().isEmpty());
            assertTrue(charlie.isOnline());
        } finally {
            Files.delete(stateFile.resolve("obstruction"));
            Files.delete(stateFile);
        }
    }

    @Test
    void distanceAtLimitIsRejectedWithoutStartingVote() {
        var controlled = mock(org.bukkit.World.class);
        when(controlled.getViewDistance()).thenReturn(18);
        var serverSpy = spy(server);
        doReturn(java.util.List.of(controlled)).when(serverSpy).getWorlds();
        var subject = spy(plugin);
        doReturn(serverSpy).when(subject).getServer();
        subject.onCommand(alice, plugin.getCommand("vote"), "vote", new String[] {"increaseViewDistance"});
        assertTrue(messages(alice).contains("already at the configured distance limit"));
        verify(controlled, never()).setViewDistance(anyInt());
    }

    @Test
    void exactlyHalfOfFourEligiblePlayersPasses() {
        server.addPlayer("Dave");
        vote(alice, "setTimeNight");
        assertTrue(messages(alice).contains("2 yes required out of 4"));
        vote(bob, "yes");
        assertEquals(13000, world.getTime());
    }

    @Test
    void votingButtonsUseNamespacedCommandsAndSnapshotVotesSurviveDisconnect() {
        var subject = spy(plugin);
        var serverSpy = spy(server);
        doReturn(serverSpy).when(subject).getServer();
        subject.onCommand(alice, plugin.getCommand("vote"), "vote", new String[] {"setTimeNight"});
        var message = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(serverSpy).broadcast(message.capture());
        assertTrue(message.getValue().children().stream().anyMatch(component ->
                net.kyori.adventure.text.event.ClickEvent.runCommand("/votersrights:vote yes").equals(component.clickEvent())));
        alice.disconnect();
        subject.onCommand(bob, plugin.getCommand("vote"), "vote", new String[] {"yes"});
        assertEquals(13000, world.getTime());
    }

    @Test
    void defaultSettingsAndAdminOnlyMutationAreWired() {
        assertEquals(50, plugin.settings().percentage());
        assertEquals(3, plugin.settings().minimumPlayers());
        assertEquals(20, plugin.settings().restartCooldownMinutes());
        assertEquals(3, plugin.settings().timeoutMinutes());
        assertEquals(30, plugin.settings().protectionMinutes());
        assertEquals(java.util.Set.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD), plugin.settings().difficulties());
        server.dispatchCommand(alice, "votersrights set pass-percentage 100");
        assertEquals(50, plugin.settings().percentage());
        alice.setOp(true);
        server.dispatchCommand(alice, "votersrights set pass-percentage 100");
        assertEquals(100, plugin.settings().percentage());
    }

    @Test
    void votePassesAtHalfRoundedUpAndChangesTimeInEveryWorld() {
        var nether = server.addSimpleWorld("nether");
        world.setTime(7000);
        nether.setTime(7000);
        vote(alice, "setTimeNight");
        assertEquals(7000, world.getTime());
        assertTrue(messages(alice).contains("2 yes required out of 3"));
        vote(bob, "yes");
        assertEquals(13000, world.getTime());
        assertEquals(13000, nether.getTime());
        assertTrue(messages(alice).contains("Vote passed"));
        vote(charlie, "yes");
        assertTrue(messages(charlie).contains("No vote is active"));
    }

    @Test
    void electorateIsSnapshotDuplicateVotesDoNotCountAndVoteExpires() {
        vote(alice, "setTimeNight");
        vote(alice, "yes");
        assertTrue(messages(alice).contains("already voted"));
        var newcomer = server.addPlayer("Newcomer");
        vote(newcomer, "yes");
        assertTrue(messages(newcomer).contains("not eligible when"));
        vote(bob, "no");
        time.addAndGet(60_000);
        plugin.tick();
        assertTrue(messages(alice).contains("Vote failed"));
        assertNotEquals(13000, world.getTime());
    }

    @Test
    void onlyOneVoteAndMinimumEligiblePopulationAreEnforced() {
        charlie.disconnect();
        vote(alice, "setTimeNight");
        assertTrue(messages(alice).contains("At least 3 eligible"));
        charlie.reconnect();
        vote(alice, "setTimeNight");
        vote(bob, "setTimeDay");
        assertTrue(messages(bob).contains("Another vote is active"));
    }

    @Test
    void whitelistBlacklistAndPermissionAffectBothElectorateAndBallots() {
        admin("blacklist add Charlie");
        vote(alice, "setTimeNight");
        assertTrue(messages(alice).contains("At least 3 eligible"));
        vote(charlie, "setTimeNight");
        assertTrue(messages(charlie).contains("not allowed"));
        admin("blacklist remove Charlie");
        admin("set whitelist-enabled true");
        for (var player : new PlayerMock[] {alice, bob, charlie}) admin("whitelist add " + player.getName());
        vote(alice, "setTimeDay");
        bob.addAttachment(plugin, "votersrights.vote", false);
        plugin.onCommand(bob, plugin.getCommand("vote"), "vote", new String[] {"yes"});
        assertTrue(messages(bob).contains("not allowed"));
        vote(charlie, "yes");
        assertEquals(1000, world.getTime());
        admin("blacklist add Alice");
        vote(alice, "setTimeDay");
        assertTrue(messages(alice).contains("not allowed"));
    }

    @Test
    void disabledActionsAndDisallowedDifficultiesNeverStartVotes() {
        admin("allow restart false");
        vote(alice, "restart");
        assertTrue(messages(alice).contains("disabled"));
        vote(alice, "difficulty peaceful");
        assertTrue(messages(alice).contains("not allowed"));
        admin("difficulties peaceful hard");
        vote(alice, "difficulty peaceful");
        vote(bob, "yes");
        assertEquals(Difficulty.PEACEFUL, world.getDifficulty());
    }

    @ParameterizedTest
    @CsvSource({
        "pass-percentage,75", "minimum-online-players,5", "restart-cooldown-minutes,10",
        "timeout-duration-minutes,2", "timeout-protection-minutes,15", "minimum-view-distance,8",
        "maximum-view-distance,20", "minimum-simulation-distance,3", "maximum-simulation-distance,14"
    })
    void numericAdminSettingsAreSavedAndReloaded(String key, int value) throws Exception {
        admin("set " + key + " " + value);
        var saved = new YamlConfiguration();
        saved.load(plugin.getDataFolder().toPath().resolve("config.yml").toFile());
        assertEquals(value, saved.getInt(key));
        admin("reload");
        assertEquals(value, plugin.configurationCopy().getInt(key));
    }

    @ParameterizedTest
    @CsvSource({
        "pass-percentage,0", "pass-percentage,101", "minimum-online-players,0",
        "restart-cooldown-minutes,-1", "timeout-duration-minutes,0", "timeout-protection-minutes,-1",
        "minimum-view-distance,19", "maximum-view-distance,5", "minimum-simulation-distance,13",
        "maximum-simulation-distance,3", "pass-percentage,1.5"
    })
    void invalidAdminNumbersLeaveActiveAndSavedSettingsUnchanged(String key, String value) throws Exception {
        String original = plugin.configurationCopy().saveToString();
        String saved = Files.readString(plugin.getDataFolder().toPath().resolve("config.yml"));
        admin("set " + key + " " + value);
        assertEquals(original, plugin.configurationCopy().saveToString());
        assertEquals(saved, Files.readString(plugin.getDataFolder().toPath().resolve("config.yml")));
    }

    @Test
    void adminChangesCancelActiveVoteButInvalidChangesDoNot() {
        vote(alice, "setTimeNight");
        admin("set pass-percentage 0");
        vote(bob, "yes");
        assertEquals(13000, world.getTime());
        vote(alice, "setTimeDay");
        admin("set pass-percentage 100");
        vote(bob, "yes");
        assertTrue(messages(bob).contains("No vote is active"));
        assertEquals(13000, world.getTime());
    }

    @Test
    void customCommandsRunFixedTextInOrderAndAcceptNoPlayerArguments() {
        var executions = new java.util.ArrayList<String>();
        server.getCommandMap().register("test", new Command("recordvote") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                assertEquals(server.getConsoleSender(), sender);
                executions.add(String.join(" ", args));
                return true;
            }
        });
        admin("action add celebration recordvote first");
        admin("action add celebration recordvote second");
        vote(alice, "celebration injected");
        assertTrue(messages(alice).contains("takes no arguments"));
        vote(alice, "celebration");
        vote(bob, "yes");
        assertEquals(java.util.List.of("first", "second"), executions);
        admin("allow celebration false");
        vote(alice, "celebration");
        assertTrue(messages(alice).contains("disabled"));
        admin("action remove celebration");
        assertFalse(plugin.settings().names().contains("celebration"));
    }

    @Test
    void customCommandHandlerFailureIsReportedRatherThanAnnouncingSuccess() {
        server.getCommandMap().register("test", new Command("failingvote") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                throw new org.bukkit.command.CommandException("Test command handler failure");
            }
        });
        admin("action add failure failingvote");
        vote(alice, "failure");
        vote(bob, "yes");
        String result = messages(alice);
        assertTrue(result.contains("could not be completed"));
        assertFalse(result.contains("Custom action failure executed"));
    }

    @Test
    void unknownCustomCommandReportsFailureAndStopsLaterCommands() {
        admin("action add broken nonexistent-votersrights-command");
        admin("action add broken say should-not-run");
        vote(alice, "broken");
        vote(bob, "yes");
        assertTrue(messages(alice).contains("could not be completed"));
    }

    @Test
    void timeoutHasThirtySecondWarningBlocksLoginAndProtectsAfterItEnds() throws Exception {
        vote(alice, "timeout Charlie");
        vote(bob, "yes");
        var timeout = plugin.state().timeouts().get(charlie.getUniqueId());
        assertEquals(time.get() + 30_000, timeout.startsAt());
        assertEquals(timeout.startsAt() + 180_000, timeout.endsAt());
        assertEquals(timeout.endsAt() + 1_800_000, timeout.protectedUntil());
        plugin.tick();
        assertTrue(charlie.isOnline());
        var before = login(charlie);
        plugin.onLogin(before);
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, before.getLoginResult());
        vote(alice, "setTimeDay");
        assertTrue(messages(alice).contains("warning period"));
        time.set(timeout.startsAt());
        var blocked = login(charlie);
        plugin.onLogin(blocked);
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, blocked.getLoginResult());
        plugin.tick();
        assertFalse(charlie.isOnline());
        time.set(timeout.endsAt());
        var after = login(charlie);
        plugin.onLogin(after);
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, after.getLoginResult());
        charlie.reconnect();
        server.addPlayer(charlie);
        vote(alice, "timeout Charlie");
        String response = messages(alice);
        assertTrue(response.contains("protected"), response);
        time.set(timeout.protectedUntil());
        vote(alice, "timeout Charlie");
        assertTrue(messages(alice).contains("proposes timeout Charlie"));
    }

    private AsyncPlayerPreLoginEvent login(PlayerMock player) throws Exception {
        return new AsyncPlayerPreLoginEvent(player.getName(), InetAddress.getLoopbackAddress(), player.getUniqueId());
    }

    @Test
    void timeoutTargetLeavingBeforePassCausesExplicitFailure() {
        vote(alice, "timeout Charlie");
        charlie.disconnect();
        vote(bob, "yes");
        assertTrue(messages(alice).contains("no longer online"));
        assertTrue(plugin.state().timeouts().isEmpty());
    }

    @Test
    void restartWarningAndCooldownAreEnforcedWithNoEarlyExecution() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        server.getCommandMap().register("test", new Command("restart") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                calls.incrementAndGet();
                return true;
            }
        });
        vote(alice, "restart");
        vote(bob, "yes");
        assertEquals(time.get() + 120_000, plugin.state().restartAt());
        time.addAndGet(119_999);
        plugin.tick();
        assertEquals(0, calls.get());
        vote(alice, "setTimeDay");
        assertTrue(messages(alice).contains("warning period"));
        time.incrementAndGet();
        plugin.tick();
        assertEquals(1, calls.get());
        assertEquals(time.get() + 1_200_000, plugin.state().restartAvailableAt());
        assertEquals(0, plugin.state().restartAt());
        vote(alice, "restart");
        assertTrue(messages(alice).contains("cooldown"));
        time.set(plugin.state().restartAvailableAt());
        vote(alice, "restart");
        assertTrue(messages(alice).contains("proposes restart"));
    }

    @ParameterizedTest
    @CsvSource({
        "lowerViewDistance,10,8,false", "increaseViewDistance,10,12,false",
        "lowerViewDistance,7,6,false", "increaseViewDistance,17,18,false",
        "lowerSimulationDistance,8,6,true", "increaseSimulationDistance,8,10,true",
        "lowerSimulationDistance,5,4,true", "increaseSimulationDistance,11,12,true"
    })
    void distanceActionsChangeEachWorldByTwoAndClampToLimits(String action, int current, int expected, boolean simulation) {
        var controlled = spy(new WorldMock());
        controlled.setName("controlled");
        doReturn(current).when(controlled).getViewDistance();
        doReturn(current).when(controlled).getSimulationDistance();
        doNothing().when(controlled).setViewDistance(anyInt());
        doNothing().when(controlled).setSimulationDistance(anyInt());
        // Only controlled worlds are needed; use a server spy for unsupported native distance setters.
        var serverSpy = spy(server);
        doReturn(java.util.List.of(controlled)).when(serverSpy).getWorlds();
        var subject = spy(plugin);
        doReturn(serverSpy).when(subject).getServer();
        subject.onCommand(alice, plugin.getCommand("vote"), "vote", new String[] {action});
        subject.onCommand(bob, plugin.getCommand("vote"), "vote", new String[] {"yes"});
        if (simulation) verify(controlled).setSimulationDistance(expected);
        else verify(controlled).setViewDistance(expected);
    }

    @Test
    void failedConfigurationSaveOrReloadPreservesLiveSettings() throws Exception {
        var file = plugin.getDataFolder().toPath().resolve("config.yml");
        var original = Files.readString(file);
        Files.delete(file);
        Files.createDirectory(file);
        try {
            admin("set pass-percentage 100");
            assertEquals(50, plugin.settings().percentage());
        } finally {
            Files.delete(file);
            Files.writeString(file, original);
        }
        Files.writeString(file, "malformed: [");
        admin("reload");
        assertEquals(50, plugin.settings().percentage());
    }

    @Test
    void completionsRespectPermissionsEnabledActionsAndAllowedDifficulties() {
        admin("allow restart false");
        var vote = plugin.getCommand("vote");
        assertFalse(plugin.onTabComplete(alice, vote, "vote", new String[] {""}).contains("restart"));
        assertEquals(java.util.List.of("easy", "hard", "normal"),
                plugin.onTabComplete(alice, vote, "vote", new String[] {"difficulty", ""}));
        alice.addAttachment(plugin, "votersrights.vote", false);
        assertTrue(plugin.onTabComplete(alice, vote, "vote", new String[] {""}).isEmpty());
        assertTrue(new AdminCommands(plugin).onTabComplete(bob, plugin.getCommand("votersrights"),
                "votersrights", new String[] {""}).isEmpty());
    }
}
