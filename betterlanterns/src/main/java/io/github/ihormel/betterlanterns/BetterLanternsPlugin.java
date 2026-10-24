package io.github.ihormel.betterlanterns;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BetterLanternsPlugin extends JavaPlugin implements Listener {
    private LanternEngine engine;
    private LanternRecipes recipes;

    @Override
    public void onEnable() {
        var items = new SoulItems(new NamespacedKey(this, "soul_candle"));
        var records = new ChunkRecords(new NamespacedKey(this, "managed_lights"), new NamespacedKey(this, "soul_candles"));
        engine = new LanternEngine(getServer(), records);
        recipes = new LanternRecipes(this, items);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new LanternEvents(engine, items,
                task -> getServer().getScheduler().runTask(this, task)), this);
        getServer().getWorlds().forEach(world -> {
            for (var chunk : world.getLoadedChunks()) engine.load(chunk);
        });
        getServer().getScheduler().runTaskTimer(this, engine::tick, 1L, 1L);
        getServer().getOnlinePlayers().forEach(player -> player.discoverRecipes(recipes.keys()));
    }

    @EventHandler
    public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipes(recipes.keys()); }

    @Override
    public void onDisable() {
        if (engine != null) engine.close();
        if (recipes != null) recipes.close();
    }
}
