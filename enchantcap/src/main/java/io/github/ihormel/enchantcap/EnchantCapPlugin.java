package io.github.ihormel.enchantcap;

import org.bukkit.plugin.java.JavaPlugin;

public class EnchantCapPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        var configured = getConfig().get("max-additional-enchantment-levels");
        if (!(configured instanceof Integer additional) || additional < 0 || additional > 254) {
            throw new IllegalArgumentException("max-additional-enchantment-levels must be an integer from 0 to 254");
        }
        getServer().getPluginManager().registerEvents(new AnvilCostListener(additional), this);
    }
}
