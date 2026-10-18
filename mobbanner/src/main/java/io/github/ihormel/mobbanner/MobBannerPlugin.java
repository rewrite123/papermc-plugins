package io.github.ihormel.mobbanner;

import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobBannerPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        BannerItems items = new BannerItems(new NamespacedKey(this, "mob"));
        var give = new BannerCommand(getServer(), items);
        var command = Objects.requireNonNull(getCommand("mobbanner"), "Missing mobbanner command in plugin.yml");
        command.setExecutor(give);
        command.setTabCompleter(give);
        var manager = getServer().getPluginManager();
        manager.registerEvents(new KillRewards(items), this);
        manager.registerEvents(new WardSpawns(items,
                task -> getServer().getScheduler().runTask(this, task), new SpawnBoost()), this);
        manager.registerEvents(new WardBlocks(items, getLogger()), this);
    }
}
