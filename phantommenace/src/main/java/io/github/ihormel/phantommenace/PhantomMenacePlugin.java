package io.github.ihormel.phantommenace;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.BrewingRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class PhantomMenacePlugin extends JavaPlugin {
    private final NamespacedKey recipeKey = new NamespacedKey("phantommenace", "repellant");

    @Override
    public void onEnable() {
        var enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(new NamespacedKey("phantommenace", "phantom_menace"));
        if (enchantment == null) throw new IllegalStateException("Phantom Menace enchantment was not registered at bootstrap");
        if (!getServer().addRecipe(new BrewingRecipe(recipeKey, RepellantItems.create(),
                RepellantItems.waterInput(), new RecipeChoice.MaterialChoice(Material.PHANTOM_MEMBRANE)))) {
            throw new IllegalStateException("Could not register phantom repellant brewing recipe");
        }
        getServer().getPluginManager().registerEvents(new MenaceListener(enchantment), this);
    }

    @Override
    public void onDisable() {
        getServer().removeRecipe(recipeKey);
    }
}
