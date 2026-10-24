package io.github.ihormel.minecarttravel;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MinecartTravelPlugin extends JavaPlugin implements TabExecutor {
    static final String ADMIN = "minecarttravel.admin";
    static final String REGISTER = "minecarttravel.register";
    private RouteStore store;
    private RouteRegistry registry;
    private RouteWatcher watcher;
    private RouteRegistrar registrar;
    private TravelEngine travel;
    private long savedRevision = -1;
    private int ticks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final TravelSettings settings;
        try {
            settings = TravelSettings.read(getConfig());
            store = new RouteStore(getDataFolder().toPath().resolve("routes.txt"));
            registry = store.load();
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().log(Level.SEVERE, "MinecartTravel could not load its configuration or routes; disabling.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        savedRevision = registry.revision();
        if (Files.exists(getDataFolder().toPath().resolve("rail-graph.txt"))) {
            getLogger().info("rail-graph.txt from MinecartTravel 0.1.x is no longer used and can be deleted. "
                    + "Register routes with /minecarttravel register.");
        }
        watcher = new RouteWatcher(registry, settings.verifyBudget(), getServer()::getWorld, getLogger()::info);
        registrar = new RouteRegistrar(registry, () -> travel.settings(), getServer()::getPlayer);
        travel = new TravelEngine(this, registry, settings, registrar::recording);
        var manager = getServer().getPluginManager();
        manager.registerEvents(watcher, this);
        manager.registerEvents(registrar, this);
        manager.registerEvents(travel, this);
        for (var world : getServer().getWorlds()) {
            for (var chunk : world.getLoadedChunks()) watcher.loaded(chunk);
        }
        getServer().getScheduler().runTaskTimer(this, () -> {
            watcher.tick();
            registrar.tick();
            travel.tick();
            if (++ticks % travel.settings().saveTicks() == 0) saveRoutes();
        }, 1, 1);
        var command = Objects.requireNonNull(getCommand("minecarttravel"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        HelpGuide.register(this, "minecarttravel");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "settings" -> {
                if (args.length != 1) return false;
                var current = travel.settings();
                sender.sendMessage("MinecartTravel settings:");
                for (var option : TravelSettings.Option.values()) {
                    sender.sendMessage(" " + option.command + ": " + current.describe(option));
                }
                return true;
            }
            case "set" -> {
                if (!sender.hasPermission(ADMIN)) {
                    sender.sendMessage("You do not have permission to change MinecartTravel settings.");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage("Usage: /" + label + " set <" + String.join("|", optionNames()) + "> <value>");
                    return true;
                }
                try {
                    var option = TravelSettings.Option.named(args[1]);
                    var updated = travel.settings().adjust(getConfig(), option, args[2]);
                    saveConfig();
                    travel.settings(updated);
                    sender.sendMessage(option.command + " is now " + updated.describe(option) + ".");
                } catch (IllegalArgumentException exception) {
                    sender.sendMessage("Setting not changed: " + exception.getMessage());
                }
                return true;
            }
            case "rescan" -> {
                if (!sender.hasPermission(ADMIN)) {
                    sender.sendMessage("You do not have permission to recheck registered routes.");
                    return true;
                }
                for (var world : getServer().getWorlds()) {
                    for (var chunk : world.getLoadedChunks()) watcher.loaded(chunk);
                }
                sender.sendMessage("Rechecking registered routes in loaded chunks: " + watcher.pending() + " blocks queued.");
                return true;
            }
            default -> {
            }
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Registered fast-travel routes: " + registry.routes().size() + ".");
            return true;
        }
        switch (sub) {
            case "status" -> {
                if (args.length > 1) return false;
                player.sendMessage(travel.status(player));
                var registering = registrar.status(player);
                if (registering != null) player.sendMessage(registering);
                return true;
            }
            case "normal", "fast" -> {
                if (args.length != 1) return false;
                boolean normal = sub.equals("normal");
                travel.mode(player, normal);
                player.sendMessage(normal ? "Normal minecart mode enabled. No boosts or fast travel."
                        : "Accelerated minecart mode enabled. Travel continuously above "
                        + travel.settings().describe(TravelSettings.Option.MIN_SPEED) + " to qualify.");
                return true;
            }
            case "boost", "fasttravel" -> {
                if (args.length > 2) return false;
                boolean boost = sub.equals("boost");
                boolean current = boost ? travel.boostEnabled(player) : travel.fastTravelEnabled(player);
                boolean enable;
                if (args.length == 1) {
                    enable = !current;
                } else if (args[1].equalsIgnoreCase("on")) {
                    enable = true;
                } else if (args[1].equalsIgnoreCase("off")) {
                    enable = false;
                } else {
                    return false;
                }
                if (boost) travel.boost(player, enable);
                else travel.fastTravel(player, enable);
                player.sendMessage((boost ? "Minecart speed boost " : "Minecart fast travel ")
                        + (enable ? "turned on." : "turned off.") + " " + travel.preferences(player));
                return true;
            }
            case "register" -> {
                if (!player.hasPermission(REGISTER)) {
                    player.sendMessage("You do not have permission to register fast-travel routes.");
                    return true;
                }
                if (args.length == 2 && args[1].equalsIgnoreCase("cancel")) {
                    player.sendMessage(registrar.cancel(player) ? "Route registration cancelled."
                            : "You are not registering a route.");
                    return true;
                }
                if (args.length != 1) return false;
                registrar.request(player);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options;
        if (args.length == 1) {
            options = new ArrayList<>(List.of("normal", "fast", "boost", "fasttravel", "status", "register", "settings"));
            if (sender.hasPermission(ADMIN)) options.addAll(List.of("set", "rescan"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("boost") || args[0].equalsIgnoreCase("fasttravel"))) {
            options = List.of("on", "off");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("register")) {
            options = List.of("cancel");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set") && sender.hasPermission(ADMIN)) {
            options = optionNames();
        } else {
            return List.of();
        }
        var prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private static List<String> optionNames() {
        return Arrays.stream(TravelSettings.Option.values()).map(option -> option.command).toList();
    }

    private void saveRoutes() {
        if (registry == null || registry.revision() == savedRevision) return;
        try {
            store.save(registry);
            savedRevision = registry.revision();
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not save registered minecart routes.", exception);
        }
    }

    @Override
    public void onDisable() {
        if (travel != null) travel.close();
        if (registrar != null) registrar.close();
        saveRoutes();
    }
}
