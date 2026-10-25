package io.github.ihormel.craftablebottleofenchanting;

import org.bukkit.plugin.java.JavaPlugin;

public final class BottlePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new BottleListener(), this);
        HelpGuide.register(this, "craftablebottleofenchanting");
    }
}
