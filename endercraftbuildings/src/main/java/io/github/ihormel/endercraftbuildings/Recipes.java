package io.github.ihormel.endercraftbuildings;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

final class Recipes {
    private final Server server;
    private final List<NamespacedKey> keys = new ArrayList<>();

    Recipes(Plugin plugin, CrystalItems items) {
        server = plugin.getServer();
        var memory = new ShapedRecipe(new NamespacedKey(plugin, "memory_storage_crystal"), items.memoryCrystal());
        memory.shape("A", "P", "L");
        memory.setIngredient('A', Material.AMETHYST_SHARD);
        memory.setIngredient('P', Material.ENDER_PEARL);
        memory.setIngredient('L', Material.SOUL_LANTERN);
        add(memory);
        String[][] diagonals = {{"A ", " E"}, {" A", "E "}, {"E ", " A"}, {" E", "A "}};
        for (int i = 0; i < diagonals.length; i++) {
            var empty = new ShapedRecipe(new NamespacedKey(plugin, "empty_storage_crystal_" + i),
                    items.emptyStorageCrystal(1));
            empty.shape(diagonals[i]);
            empty.setIngredient('A', Material.AMETHYST_SHARD);
            empty.setIngredient('E', Material.ENDER_EYE);
            add(empty);
        }
        var taper = new ShapedRecipe(new NamespacedKey(plugin, "ender_biconical_taper"), items.taper());
        taper.shape("P D", "O O", "OOO");
        taper.setIngredient('P', Material.ENDER_PEARL);
        taper.setIngredient('D', Material.DIAMOND);
        taper.setIngredient('O', Material.OBSIDIAN);
        add(taper);
    }

    private void add(ShapedRecipe recipe) {
        if (!server.addRecipe(recipe)) throw new IllegalStateException("Could not register " + recipe.getKey());
        keys.add(recipe.getKey());
    }

    List<NamespacedKey> keys() { return List.copyOf(keys); }

    void close() { keys.forEach(server::removeRecipe); }
}
