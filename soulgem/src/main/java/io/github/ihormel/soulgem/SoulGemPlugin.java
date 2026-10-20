package io.github.ihormel.soulgem;

import org.bukkit.plugin.java.JavaPlugin;

public class SoulGemPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        GemItems gems = new GemItems(this);
        GemRecipes recipes = new GemRecipes(this, gems);
        recipes.register();
        getServer().getPluginManager().registerEvents(recipes, this);
        getServer().getPluginManager().registerEvents(new GemCharging(gems), this);
        getServer().getPluginManager().registerEvents(new EnchantingListener(gems), this);
    }

    @Override
    public void onDisable() {
        getServer().removeRecipe(new org.bukkit.NamespacedKey(this, "amethyst_soul_gem"));
        getServer().removeRecipe(new org.bukkit.NamespacedKey(this, "echo_soul_gem"));
    }
}
