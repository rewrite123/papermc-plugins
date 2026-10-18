package io.github.ihormel.craftablebottleofenchanting;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class BottlePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new BottleListener(this), this);
    }

    @Override
    public void onDisable() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BottleMenu) {
                player.closeInventory();
            }
        }
    }
}
