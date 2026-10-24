package io.github.ihormel.betterlanterns;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

final class LanternRecipes {
    private final Server server;
    private final List<NamespacedKey> keys;
    private final List<NamespacedKey> vanillaKeys = List.of(NamespacedKey.minecraft("lantern"),
            NamespacedKey.minecraft("soul_lantern"));
    private final java.util.Map<NamespacedKey, Recipe> originals = new java.util.HashMap<>();

    LanternRecipes(Plugin plugin, SoulItems items) {
        server = plugin.getServer();
        keys = List.of(new NamespacedKey(plugin, "lantern"), new NamespacedKey(plugin, "soul_candle"),
                new NamespacedKey(plugin, "soul_lantern"));
        for (var key : vanillaKeys) {
            Recipe original = server.getRecipe(key);
            if (original != null) originals.put(key, original);
            server.removeRecipe(key);
        }
        var soulCandle = new ShapedRecipe(keys.get(1), items.candle(4));
        soulCandle.shape(" C ", "CCC", " S ");
        soulCandle.setIngredient('S', Material.SOUL_SAND);
        soulCandle.setIngredient('C', new RecipeChoice.ExactChoice(new ItemStack(Material.CANDLE)));
        add(soulCandle);
        add(lantern(keys.get(0), Material.LANTERN, new ItemStack(Material.CANDLE)));
        add(lantern(keys.get(2), Material.SOUL_LANTERN, items.candle(1)));
    }

    private ShapedRecipe lantern(NamespacedKey key, Material result, ItemStack candle) {
        var recipe = new ShapedRecipe(key, new ItemStack(result));
        recipe.shape("GCG", "IPI", "III");
        recipe.setIngredient('G', Material.GOLD_NUGGET);
        recipe.setIngredient('C', new RecipeChoice.ExactChoice(candle));
        recipe.setIngredient('I', Material.IRON_NUGGET);
        recipe.setIngredient('P', Material.GLASS_PANE);
        return recipe;
    }

    private void add(ShapedRecipe recipe) {
        if (!server.addRecipe(recipe)) throw new IllegalStateException("Could not register " + recipe.getKey());
    }

    List<NamespacedKey> keys() { return keys; }

    void close() {
        keys.forEach(server::removeRecipe);
        originals.forEach((key, recipe) -> {
            if (server.getRecipe(key) == null && !server.addRecipe(recipe)) {
                throw new IllegalStateException("Could not restore recipe " + key);
            }
        });
    }
}
