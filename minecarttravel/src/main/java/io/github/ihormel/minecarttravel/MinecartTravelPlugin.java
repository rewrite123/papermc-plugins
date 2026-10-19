package io.github.ihormel.minecarttravel;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MinecartTravelPlugin extends JavaPlugin {
    private GraphStore store;
    private RailGraph graph;
    private TravelEngine travel;
    private int ticks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final TravelSettings settings;
        try {
            settings = TravelSettings.read(getConfig());
            store = new GraphStore(getDataFolder().toPath().resolve("rail-graph.txt"));
            graph = store.load();
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().log(Level.SEVERE, "MinecartTravel could not load its configuration or graph; disabling.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var tracks = new TrackWorld(graph, settings.scanBudget());
        travel = new TravelEngine(this, graph, settings);
        var manager = getServer().getPluginManager();
        manager.registerEvents(tracks, this);
        manager.registerEvents(travel, this);
        for (var world : getServer().getWorlds()) {
            for (var chunk : world.getLoadedChunks()) tracks.discover(chunk);
        }
        getServer().getScheduler().runTaskTimer(this, () -> {
            tracks.tick();
            travel.tick();
            if (++ticks % settings.saveTicks() == 0) saveGraph();
        }, 1, 1);
        var command = Objects.requireNonNull(getCommand("minecarttravel"));
        command.setExecutor((sender, ignored, label, args) -> {
            if (args.length == 1 && args[0].equalsIgnoreCase("rescan")) {
                if (!sender.hasPermission("minecarttravel.admin")) {
                    sender.sendMessage("You do not have permission to rescan the rail graph.");
                    return true;
                }
                for (var world : getServer().getWorlds()) {
                    for (var chunk : world.getLoadedChunks()) tracks.enqueue(chunk);
                }
                sender.sendMessage("Loaded chunks queued for bounded rail-graph reconciliation.");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Graph: " + graph.runs().size() + " compressed rail runs, "
                        + graph.stoppers().size() + " lodestones, " + graph.dirty.size() + " pending chunks.");
                return true;
            }
            if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("status"))) {
                player.sendMessage(travel.status(player));
                return true;
            }
            if (args.length == 1 && (args[0].equalsIgnoreCase("normal") || args[0].equalsIgnoreCase("fast"))) {
                boolean normal = args[0].equalsIgnoreCase("normal");
                travel.mode(player, normal);
                player.sendMessage(normal ? "Normal minecart mode enabled. No boosts or fast travel."
                        : "Accelerated minecart mode enabled. Travel continuously above "
                        + settings.minimumSpeed() + " blocks/second to qualify.");
                return true;
            }
            return false;
        });
        command.setTabCompleter((sender, ignored, label, args) -> args.length == 1
                ? (sender.hasPermission("minecarttravel.admin")
                        ? List.of("normal", "fast", "status", "rescan") : List.of("normal", "fast", "status")).stream()
                        .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList()
                : List.of());
    }

    private void saveGraph() {
        if (graph == null) return;
        try {
            store.save(graph);
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not persist the minecart rail graph.", exception);
        }
    }

    @Override
    public void onDisable() {
        if (travel != null) travel.close();
        saveGraph();
    }
}
