package io.github.ihormel.soulgem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GemRecipesTest extends PaperTest {
    private GemItems gems;
    private GemRecipes recipes;

    @BeforeEach
    void setUpRecipes() {
        var plugin = MockBukkit.createMockPlugin("soulgem");
        gems = new GemItems(plugin);
        recipes = new GemRecipes(plugin, gems);
        recipes.register();
    }

    @Test
    void recipesUseExactlyOneLapisAndOneAppropriateShard() {
        for (var kind : GemItems.Kind.values()) {
            var key = new NamespacedKey("soulgem",
                    kind.name().toLowerCase(java.util.Locale.ROOT) + "_soul_gem");
            var recipe = (ShapelessRecipe) server.getRecipe(key);
            assertNotNull(recipe);
            assertEquals(2, recipe.getIngredientList().size());
            assertEquals(java.util.Set.of(Material.LAPIS_LAZULI, kind.material),
                    recipe.getIngredientList().stream().map(ItemStack::getType)
                            .collect(java.util.stream.Collectors.toSet()));
            assertEquals(kind, gems.kind(recipe.getResult()));
            assertFalse(gems.charged(recipe.getResult()));
            assertEquals(1, recipe.getResult().getAmount());
        }
    }

    @Test
    void markedGemsCannotBeCraftingIngredients() {
        var inventory = mock(CraftingInventory.class);
        var prepare = mock(PrepareItemCraftEvent.class);
        when(prepare.getInventory()).thenReturn(inventory);
        when(inventory.getMatrix()).thenReturn(new ItemStack[] {
                gems.create(GemItems.Kind.ECHO, true), new ItemStack(Material.COMPASS)
        });
        recipes.prepare(prepare);
        verify(inventory).setResult(null);
        var craft = mock(CraftItemEvent.class);
        when(craft.getInventory()).thenReturn(inventory);
        when(craft.getWhoClicked()).thenReturn(mock(Player.class));
        recipes.craft(craft);
        verify(craft).setCancelled(true);
    }

    @Test
    void ordinaryShardsAndLapisAreNotBlocked() {
        var inventory = mock(CraftingInventory.class);
        when(inventory.getMatrix()).thenReturn(new ItemStack[] {
                null, new ItemStack(Material.AMETHYST_SHARD), new ItemStack(Material.LAPIS_LAZULI)
        });
        var prepare = mock(PrepareItemCraftEvent.class);
        when(prepare.getInventory()).thenReturn(inventory);
        recipes.prepare(prepare);
        verify(inventory, never()).setResult(any());
    }

    @Test
    void pluginRegistersAndRemovesRecipesDuringItsLifecycle() {
        server.removeRecipe(new NamespacedKey("soulgem", "amethyst_soul_gem"));
        server.removeRecipe(new NamespacedKey("soulgem", "echo_soul_gem"));
        var plugin = MockBukkit.load(SoulGemPlugin.class);
        assertNotNull(server.getRecipe(new NamespacedKey(plugin, "echo_soul_gem")));
        server.getPluginManager().disablePlugin(plugin);
        assertNull(server.getRecipe(new NamespacedKey(plugin, "echo_soul_gem")));
    }
}
