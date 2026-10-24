package io.github.ihormel.betterlanterns;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class RecipesTest {
    private ServerMock server;
    private BetterLanternsPlugin plugin;
    private SoulItems items;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BetterLanternsPlugin.class);
        items = new SoulItems(new NamespacedKey(plugin, "soul_candle"));
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    private ShapedRecipe recipe(String name) {
        return (ShapedRecipe) server.getRecipe(new NamespacedKey(plugin, name));
    }

    @Test
    void exactLanternShapesAndIngredientAmountsMatchRequest() {
        for (String key : new String[] {"lantern", "soul_lantern"}) {
            var recipe = recipe(key);
            assertArrayEquals(new String[] {"GCG", "IPI", "III"}, recipe.getShape());
            var choices = recipe.getChoiceMap();
            assertTrue(choices.get('G').test(new ItemStack(Material.GOLD_NUGGET)));
            assertTrue(choices.get('I').test(new ItemStack(Material.IRON_NUGGET)));
            assertTrue(choices.get('P').test(new ItemStack(Material.GLASS_PANE)));
            assertFalse(choices.get('P').test(new ItemStack(Material.GLASS)));
            assertEquals(key.equals("lantern") ? Material.LANTERN : Material.SOUL_LANTERN, recipe.getResult().getType());
            assertEquals(1, recipe.getResult().getAmount());
            assertEquals(key.equals("lantern"), choices.get('C').test(new ItemStack(Material.CANDLE)));
            assertEquals(key.equals("soul_lantern"), choices.get('C').test(items.candle(1)));
            assertFalse(choices.get('C').test(new ItemStack(Material.BLUE_CANDLE)));
        }
    }

    @Test
    void soulCandleRecipeUsesThreePlainCandlesAndTwoSoulSandForOneResult() {
        var recipe = recipe("soul_candle");
        assertArrayEquals(new String[] {" S ", "CCC", " S "}, recipe.getShape());
        assertTrue(recipe.getChoiceMap().get('S').test(new ItemStack(Material.SOUL_SAND)));
        assertFalse(recipe.getChoiceMap().get('S').test(new ItemStack(Material.SOUL_SOIL)));
        assertInstanceOf(RecipeChoice.ExactChoice.class, recipe.getChoiceMap().get('C'));
        assertFalse(recipe.getChoiceMap().get('C').test(items.candle(1)));
        assertTrue(items.isSoul(recipe.getResult()));
        assertEquals(1, recipe.getResult().getAmount());
        assertFalse(items.isSoul(new ItemStack(Material.CANDLE)));
    }

    @Test
    void disablingRestoresOriginalVanillaRecipesWithoutRemovingOtherPluginRecipes() {
        server.getPluginManager().disablePlugin(plugin);
        // MockBukkit's recipe iterator can leave vanilla-keyed entries visible after removal.
        var registry = new java.util.HashMap<NamespacedKey, org.bukkit.inventory.Recipe>();
        var recipeServer = org.mockito.Mockito.mock(org.bukkit.Server.class);
        org.mockito.Mockito.when(recipeServer.getRecipe(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> registry.get(call.getArgument(0)));
        org.mockito.Mockito.when(recipeServer.removeRecipe(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> registry.remove(call.getArgument(0)) != null);
        org.mockito.Mockito.when(recipeServer.addRecipe(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    var recipe = (org.bukkit.inventory.Recipe) call.getArgument(0);
                    return registry.putIfAbsent(((org.bukkit.Keyed) recipe).getKey(), recipe) == null;
                });
        var recipePlugin = org.mockito.Mockito.spy(plugin);
        org.mockito.Mockito.doReturn(recipeServer).when(recipePlugin).getServer();
        var key = NamespacedKey.minecraft("lantern");
        var original = new ShapedRecipe(key, new ItemStack(Material.LANTERN));
        original.shape("I");
        original.setIngredient('I', Material.IRON_INGOT);
        registry.put(key, original);
        var thirdParty = new NamespacedKey("otherplugin", "lantern");
        var other = new ShapedRecipe(thirdParty, new ItemStack(Material.LANTERN));
        other.shape("G");
        other.setIngredient('G', Material.GOLD_INGOT);
        registry.put(thirdParty, other);
        var registered = new LanternRecipes(recipePlugin, items);
        assertNull(registry.get(key));
        assertSame(other, registry.get(thirdParty));
        registered.close();
        assertSame(original, registry.get(key));
        assertSame(other, registry.get(thirdParty));
    }

    @Test
    void playersDiscoverAllRecipesAndDisableRemovesPluginRecipes() {
        var player = server.addPlayer();
        for (String key : new String[] {"lantern", "soul_candle", "soul_lantern"}) {
            assertTrue(player.hasDiscoveredRecipe(new NamespacedKey(plugin, key)));
        }
        assertNull(server.getRecipe(NamespacedKey.minecraft("lantern")));
        assertNull(server.getRecipe(NamespacedKey.minecraft("soul_lantern")));
        server.getPluginManager().disablePlugin(plugin);
        assertNull(recipe("lantern"));
        assertNull(recipe("soul_candle"));
        assertNull(recipe("soul_lantern"));
    }
}
