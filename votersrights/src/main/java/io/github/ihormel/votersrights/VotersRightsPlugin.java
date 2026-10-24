package io.github.ihormel.votersrights;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class VotersRightsPlugin extends JavaPlugin implements Listener {
    private YamlConfiguration configuration;
    private VotingSettings settings;
    private ActionState state;
    private Vote vote;
    private Proposal proposal;
    private LongSupplier clock = System::currentTimeMillis;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            configuration = readConfiguration();
            settings = VotingSettings.load(configuration);
            state = new ActionState(getDataFolder().toPath().resolve("state.yml"));
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load votersrights configuration or saved actions.", exception);
        }
        Objects.requireNonNull(getCommand("vote")).setExecutor(this);
        Objects.requireNonNull(getCommand("vote")).setTabCompleter(this);
        var admin = new AdminCommands(this);
        Objects.requireNonNull(getCommand("votersrights")).setExecutor(admin);
        Objects.requireNonNull(getCommand("votersrights")).setTabCompleter(admin);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        HelpGuide.register(this, "votersrights");
    }

    @Override
    public void onDisable() {
        if (vote != null) broadcast("The current vote was cancelled because votersrights is stopping.");
        vote = null;
        proposal = null;
    }

    VotingSettings settings() { return settings; }
    ActionState state() { return state; }
    void clock(LongSupplier clock) { this.clock = clock; }
    long now() { return clock.getAsLong(); }

    private Path configPath() { return getDataFolder().toPath().resolve("config.yml"); }

    private YamlConfiguration readConfiguration() throws IOException, InvalidConfigurationException {
        var loaded = new YamlConfiguration();
        loaded.load(configPath().toFile());
        return loaded;
    }

    YamlConfiguration configurationCopy() throws InvalidConfigurationException {
        var copy = new YamlConfiguration();
        copy.loadFromString(configuration.saveToString());
        return copy;
    }

    void applyConfiguration(YamlConfiguration candidate, boolean save) throws IOException {
        var validated = VotingSettings.load(candidate);
        if (save) AtomicYaml.save(candidate, configPath());
        configuration = candidate;
        settings = validated;
        if (vote != null) {
            vote = null;
            proposal = null;
            broadcast("The current vote was cancelled because an admin changed voting settings.");
        }
    }

    void reloadVotingConfiguration() throws IOException, InvalidConfigurationException {
        applyConfiguration(readConfiguration(), false);
    }

    private boolean eligible(Player player) {
        return player.hasPermission("votersrights.vote") && settings.eligible(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            tell(sender, "Only online players can propose actions or vote.");
            return true;
        }
        if (!eligible(player)) {
            tell(player, "You are not allowed to vote.");
            return true;
        }
        tick();
        if (args.length == 0) {
            if (vote != null) tell(player, status());
            else tell(player, "No vote is active. Actions: " + String.join(", ",
                    settings.names().stream().filter(settings::enabled).toList())
                    + ". Use /vote <action> or /vote yes|no.");
            return true;
        }
        try {
            if (args[0].equalsIgnoreCase("yes") || args[0].equalsIgnoreCase("no")) {
                if (args.length != 1) throw new IllegalArgumentException("Use /vote yes or /vote no.");
                if (vote == null) throw new IllegalArgumentException("No vote is active.");
                vote.cast(player.getUniqueId(), args[0].equalsIgnoreCase("yes"), now());
                broadcast(player.getName() + " voted " + args[0].toLowerCase(Locale.ROOT) + ". " + status());
                finishIfPassed();
            } else {
                if (vote != null) throw new IllegalArgumentException("Another vote is active. " + status());
                if (state.pending(now())) throw new IllegalArgumentException("A passed action is still in its warning period.");
                Set<UUID> electorate = getServer().getOnlinePlayers().stream().filter(this::eligible)
                        .map(Player::getUniqueId).collect(Collectors.toUnmodifiableSet());
                if (electorate.size() < settings.minimumPlayers()) {
                    throw new IllegalArgumentException("At least " + settings.minimumPlayers() + " eligible players must be online.");
                }
                Proposal candidate = parseProposal(player, args);
                validateProposal(candidate);
                proposal = candidate;
                vote = new Vote(electorate, settings.percentage(), now());
                vote.cast(player.getUniqueId(), true, now());
                getServer().broadcast(Component.text("[votersrights] " + player.getName() + " proposes "
                        + proposal.description() + ". " + status() + " ")
                        .append(Component.text("[Vote YES]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/votersrights:vote yes")))
                        .append(Component.text(" "))
                        .append(Component.text("[Vote NO]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/votersrights:vote no"))));
                finishIfPassed();
            }
        } catch (IllegalArgumentException exception) {
            tell(player, exception.getMessage());
        }
        return true;
    }

    private Proposal parseProposal(Player player, String[] args) {
        String action = settings.actionName(args[0]);
        if (!settings.enabled(action)) throw new IllegalArgumentException("That voting action is disabled.");
        if (action.equals("timeout")) {
            if (args.length != 2) throw new IllegalArgumentException("Use /vote timeout <online-player>.");
            Player target = getServer().getPlayerExact(args[1]);
            if (target == null || !player.canSee(target)) throw new IllegalArgumentException("That player is not online or visible.");
            return new Proposal(action, target.getUniqueId(), target.getName(), null);
        }
        if (action.equals("difficulty")) {
            if (args.length != 2) throw new IllegalArgumentException("Use /vote difficulty <difficulty>.");
            Difficulty difficulty;
            try {
                difficulty = Difficulty.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown difficulty. Allowed: " + settings.difficulties());
            }
            if (!settings.difficulties().contains(difficulty)) throw new IllegalArgumentException("That difficulty is not allowed.");
            return new Proposal(action, null, null, difficulty);
        }
        if (args.length != 1) throw new IllegalArgumentException("This action takes no arguments.");
        return new Proposal(action, null, null, null);
    }

    private void validateProposal(Proposal candidate) {
        if (candidate.action().equals("restart") && now() < state.restartAvailableAt()) {
            throw new IllegalArgumentException("Restart is on cooldown for "
                    + seconds(state.restartAvailableAt() - now()) + " more seconds.");
        }
        if (candidate.action().equals("timeout")) {
            var existing = state.timeouts().get(candidate.target());
            if (existing != null && now() < existing.protectedUntil()) {
                throw new IllegalArgumentException("That player is timed out or protected for "
                        + seconds(existing.protectedUntil() - now()) + " more seconds.");
            }
            if (getServer().getPlayer(candidate.target()) == null) {
                throw new IllegalArgumentException("The timeout target is no longer online.");
            }
        }
        if (isDistance(candidate.action())) {
            boolean changed = getServer().getWorlds().stream().anyMatch(world ->
                    distance(world, candidate.action()) != adjustedDistance(world, candidate.action()));
            if (!changed) throw new IllegalArgumentException("All worlds are already at the configured distance limit.");
        }
    }

    private String status() {
        return proposal.description() + ": " + vote.yes() + " yes, " + vote.no() + " no; "
                + vote.required() + " yes required out of " + vote.total() + " eligible players. "
                + vote.secondsRemaining(now()) + " seconds remaining.";
    }

    private void finishIfPassed() {
        if (vote == null || !vote.passed()) return;
        Proposal passed = proposal;
        vote = null;
        proposal = null;
        try {
            validateProposal(passed);
            execute(passed);
        } catch (IOException | IllegalArgumentException | IllegalStateException | CommandException exception) {
            getLogger().log(Level.SEVERE, "Passed action could not be executed: " + passed.description(), exception);
            broadcast("The vote passed, but " + passed.description() + " could not be completed: "
                    + exception.getMessage() + ". Check the server log.");
        }
    }

    private void execute(Proposal passed) throws IOException {
        switch (passed.action()) {
            case "restart" -> {
                state.scheduleRestart(now() + 120_000);
                broadcast("Vote passed. Server restart in 2 minutes! Finish what you are doing.");
            }
            case "timeout" -> {
                state.timeout(passed.target(), passed.targetName(), now(), settings.timeoutMinutes(), settings.protectionMinutes());
                broadcast("Vote passed. " + passed.targetName() + " will be disconnected in 30 seconds for "
                        + settings.timeoutMinutes() + " minutes.");
            }
            case "difficulty" -> {
                getServer().getWorlds().forEach(world -> world.setDifficulty(passed.difficulty()));
                broadcast("Vote passed. All worlds now use " + passed.difficulty() + " difficulty.");
            }
            case "setTimeDay", "setTimeNight" -> {
                long time = passed.action().equals("setTimeDay") ? 1000 : 13000;
                getServer().getWorlds().forEach(world -> world.setTime(time));
                broadcast("Vote passed. " + passed.action() + " applied to all worlds.");
            }
            default -> {
                if (isDistance(passed.action())) {
                    getServer().getWorlds().forEach(world -> {
                        int value = adjustedDistance(world, passed.action());
                        if (passed.action().contains("Simulation")) world.setSimulationDistance(value);
                        else world.setViewDistance(value);
                    });
                    broadcast("Vote passed. " + passed.action() + " applied to all worlds (2 chunks, clamped to admin limits).");
                } else {
                    for (String consoleCommand : settings.customActions().get(passed.action()).commands()) {
                        if (!getServer().dispatchCommand(getServer().getConsoleSender(), consoleCommand)) {
                            throw new IllegalStateException("Console command was not handled: " + consoleCommand);
                        }
                    }
                    broadcast("Vote passed. Custom action " + passed.action() + " executed.");
                }
            }
        }
    }

    private static boolean isDistance(String action) {
        return action.equals("lowerViewDistance") || action.equals("increaseViewDistance")
                || action.equals("lowerSimulationDistance") || action.equals("increaseSimulationDistance");
    }

    private static int distance(World world, String action) {
        return action.contains("Simulation") ? world.getSimulationDistance() : world.getViewDistance();
    }

    private int adjustedDistance(World world, String action) {
        boolean simulation = action.contains("Simulation");
        int minimum = simulation ? settings.minimumSimulation() : settings.minimumView();
        int maximum = simulation ? settings.maximumSimulation() : settings.maximumView();
        return Math.clamp(distance(world, action) + (action.startsWith("lower") ? -2 : 2), minimum, maximum);
    }

    void tick() {
        long now = now();
        if (vote != null && vote.expired(now)) {
            broadcast("Vote failed: " + status());
            vote = null;
            proposal = null;
        }
        if (state.restartAt() != 0 && now >= state.restartAt()) {
            try {
                state.beginRestart(now + settings.restartCooldownMinutes() * 60_000L);
                broadcast("Restarting now after the successful vote.");
                if (!getServer().dispatchCommand(getServer().getConsoleSender(), "restart")) {
                    getLogger().severe("Paper restart command was not handled. Configure your host's restart setup.");
                    broadcast("Restart failed: the server did not handle its restart command. Contact an admin.");
                }
            } catch (IOException exception) {
                getLogger().log(Level.SEVERE, "Could not save restart cooldown; restart postponed.", exception);
                broadcast("Restart postponed because votersrights could not save its cooldown. Contact an admin.");
            } catch (CommandException exception) {
                getLogger().log(Level.SEVERE, "The server restart command failed.", exception);
                broadcast("Restart failed in the server's command handler. Contact an admin; the cooldown remains in place.");
            }
        }
        state.timeouts().forEach((id, timeout) -> {
            if (timeout.active(now)) {
                Player player = getServer().getPlayer(id);
                if (player != null) player.kick(Component.text(timeoutMessage(timeout, now)));
            }
        });
    }

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        var timeout = state.timeouts().get(event.getUniqueId());
        long now = now();
        if (timeout != null && timeout.active(now)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(timeoutMessage(timeout, now)));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var timeout = state.timeouts().get(event.getPlayer().getUniqueId());
        if (timeout != null && now() < timeout.startsAt()) {
            tell(event.getPlayer(), "Your voted timeout starts in " + seconds(timeout.startsAt() - now()) + " seconds.");
        }
        if (state.restartAt() > now()) {
            tell(event.getPlayer(), "A voted server restart is scheduled in " + seconds(state.restartAt() - now()) + " seconds.");
        }
    }

    private static String timeoutMessage(ActionState.Timeout timeout, long now) {
        return "[votersrights] You are timed out by player vote. You can reconnect in "
                + seconds(timeout.endsAt() - now) + " seconds.";
    }

    private static long seconds(long millis) { return Math.max(0, (millis + 999) / 1000); }
    private void broadcast(String message) { getServer().broadcast(Component.text("[votersrights] " + message)); }
    static void tell(CommandSender sender, String message) { sender.sendMessage("[votersrights] " + message); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !eligible(player)) return List.of();
        List<String> options = List.of();
        if (args.length == 1) {
            options = vote != null ? List.of("yes", "no") : settings.names().stream().filter(settings::enabled).toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("difficulty")) {
            options = settings.difficulties().stream().map(value -> value.name().toLowerCase(Locale.ROOT)).sorted().toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("timeout")) {
            options = getServer().getOnlinePlayers().stream().filter(player::canSee).map(Player::getName).sorted().toList();
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
