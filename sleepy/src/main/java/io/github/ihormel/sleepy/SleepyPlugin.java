package io.github.ihormel.sleepy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SleepyPlugin extends JavaPlugin implements Listener {
    private SleepingRule rule;

    @Override
    public void onEnable() {
        rule = new SleepingRule(getLogger());
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getWorlds().forEach(rule::apply);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        rule.apply(event.getWorld());
    }
}
