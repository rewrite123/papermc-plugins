package io.github.ihormel.enchantcap;

import org.bukkit.plugin.java.JavaPlugin;

public final class EnchantCapPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new AnvilCostListener(), this);
    }
}
