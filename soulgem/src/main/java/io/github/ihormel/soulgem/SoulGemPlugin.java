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
        getServer().getPluginManager().registerEvents(
                new EnchantingListener(this, gems, new EnchantingOffers()), this);
    }

    @Override
    public void onDisable() {
        for (var player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof GemMenu) {
                player.closeInventory();
            }
        }
        getServer().removeRecipe(new org.bukkit.NamespacedKey(this, "amethyst_soul_gem"));
        getServer().removeRecipe(new org.bukkit.NamespacedKey(this, "echo_soul_gem"));
    }
}
