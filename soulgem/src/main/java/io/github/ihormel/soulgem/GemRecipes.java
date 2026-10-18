package io.github.ihormel.soulgem;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

final class GemRecipes implements Listener {
    private final JavaPlugin plugin;
    private final GemItems gems;

    GemRecipes(JavaPlugin plugin, GemItems gems) {
        this.plugin = plugin;
        this.gems = gems;
    }

    void register() {
        for (var kind : GemItems.Kind.values()) {
            var key = new NamespacedKey(plugin, kind.name().toLowerCase(java.util.Locale.ROOT) + "_soul_gem");
            var recipe = new ShapelessRecipe(key, gems.create(kind, false));
            recipe.addIngredient(Material.LAPIS_LAZULI);
            recipe.addIngredient(kind.material);
            if (!plugin.getServer().addRecipe(recipe)) {
                throw new IllegalStateException("Could not register soul gem recipe " + key);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        for (var ingredient : event.getInventory().getMatrix()) {
            if (gems.marked(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        for (var ingredient : event.getInventory().getMatrix()) {
            if (gems.marked(ingredient)) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage("Soul gems cannot be used as crafting ingredients.");
                return;
            }
        }
    }
}
