package io.github.ihormel.ihatepowderedsnow;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class IHatePowderedSnowPlugin extends JavaPlugin implements Listener {
    private final SnowPopulator populator = new SnowPopulator();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getWorlds().forEach(this::register);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
        register(event.getWorld());
    }

    private void register(World world) {
        if (!world.getPopulators().contains(populator)) {
            world.getPopulators().add(populator);
        }
    }

    @Override
    public void onDisable() {
        getServer().getWorlds().forEach(world -> world.getPopulators().remove(populator));
    }
}
