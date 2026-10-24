package io.github.ihormel.endercraftbuildings;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class EnderCraftBuildingsPlugin extends JavaPlugin implements Listener {
    static BlockRules blockRules = BlockRules.PAPER;
    static java.util.function.DoubleSupplier random = () -> ThreadLocalRandom.current().nextDouble();
    static java.util.function.Function<org.bukkit.entity.Player, BuildingListener.Aim> aim = BuildingListener::aim;
    private Recipes recipes;
    private CrystalItems items;
    private BuildingListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new CrystalItems(this);
        var materials = new Materials(getServer(), blockRules);
        var buildings = new Buildings(getServer(), blockRules, materials);
        var tapers = new TaperRecords(new NamespacedKey(this, "tapers"));
        listener = new BuildingListener(getServer(), items, buildings, tapers, new PlacedCrystals(this),
                player -> aim.apply(player), () -> random.getAsDouble(),
                task -> getServer().getScheduler().runTask(this, task),
                () -> chance("endermite-chance.taper"), () -> chance("endermite-chance.placement"));
        recipes = new Recipes(this, items);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getScheduler().runTaskTimer(this, listener::tick, 1L, 1L);
        getServer().getOnlinePlayers().forEach(player -> player.discoverRecipes(recipes.keys()));
        for (var world : getServer().getWorlds()) {
            for (var chunk : world.getLoadedChunks()) tapers.blocks(chunk).forEach(BuildingListener::quiet);
        }
    }

    private double chance(String path) { return Math.clamp(getConfig().getDouble(path, 0.05), 0.0, 1.0); }

    CrystalItems items() { return items; }

    BuildingListener listener() { return listener; }

    @EventHandler
    public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipes(recipes.keys()); }

    @Override
    public void onDisable() {
        if (recipes != null) recipes.close();
    }
}
