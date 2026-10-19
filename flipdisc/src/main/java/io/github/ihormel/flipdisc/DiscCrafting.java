package io.github.ihormel.flipdisc;

import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;

final class DiscCrafting implements Listener {
    private final Map<NamespacedKey, Material> recipes;
    private final Predicate<ItemStack> customPlayback;

    DiscCrafting(Map<NamespacedKey, Material> recipes, Predicate<ItemStack> customPlayback) {
        this.recipes = Map.copyOf(recipes);
        this.customPlayback = customPlayback;
    }

    boolean owns(Recipe recipe) {
        return recipe instanceof ShapelessRecipe shapeless && recipes.containsKey(shapeless.getKey());
    }

    ItemStack flip(Recipe recipe, ItemStack[] matrix) {
        if (!(recipe instanceof ShapelessRecipe shapeless)) return null;
        Material expected = recipes.get(shapeless.getKey());
        if (expected == null) return null;
        ItemStack disc = null;
        for (ItemStack item : matrix) {
            if (item == null || item.isEmpty()) continue;
            if (disc != null || item.getType() != expected) return null;
            disc = item;
        }
        if (disc == null || customPlayback.test(disc)) return null;
        ItemStack result = disc.withType(DiscPairs.OPPOSITES.get(expected));
        result.setAmount(1);
        return result;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void prepare(PrepareItemCraftEvent event) {
        if (!owns(event.getRecipe())) return;
        ItemStack current = event.getInventory().getResult();
        if (current == null || current.isEmpty()) return;
        event.getInventory().setResult(flip(event.getRecipe(), event.getInventory().getMatrix()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (!owns(event.getRecipe())) return;
        ItemStack expected = flip(event.getRecipe(), event.getInventory().getMatrix());
        ItemStack actual = event.getCurrentItem();
        // Never undo another plugin's rejected or replaced preview.
        if (expected == null || actual == null || actual.getAmount() != 1 || !expected.isSimilar(actual)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automate(CrafterCraftEvent event) {
        // These are manual flips; prevent automation from discarding item metadata.
        if (owns(event.getRecipe())) event.setCancelled(true);
    }
}
