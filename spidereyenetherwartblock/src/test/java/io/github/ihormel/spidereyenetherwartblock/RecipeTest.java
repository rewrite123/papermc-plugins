package io.github.ihormel.spidereyenetherwartblock;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class RecipeTest {
    private ServerMock server;
    private SpiderEyeNetherWartBlockPlugin plugin;
    private NamespacedKey key;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SpiderEyeNetherWartBlockPlugin.class);
        key = new NamespacedKey(plugin, "spider_eyes_to_nether_wart_block");
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void recipeRequiresNineSpiderEyesAndProducesOneVanillaBlock() {
        var recipe = assertInstanceOf(ShapedRecipe.class, server.getRecipe(key));
        assertArrayEquals(new String[] {"EEE", "EEE", "EEE"}, recipe.getShape());
        assertEquals(1, recipe.getChoiceMap().size());
        var ingredient = recipe.getChoiceMap().get('E');
        assertTrue(ingredient.test(new ItemStack(Material.SPIDER_EYE)));
        assertFalse(ingredient.test(new ItemStack(Material.FERMENTED_SPIDER_EYE)));
        assertFalse(ingredient.test(new ItemStack(Material.NETHER_WART)));
        assertEquals(new ItemStack(Material.NETHER_WART_BLOCK), recipe.getResult());
    }

    @Test
    void playersDiscoverTheRecipeOnJoin() {
        var player = server.addPlayer();
        assertTrue(player.hasDiscoveredRecipe(key));
    }

    @Test
    void onlinePlayersDiscoverTheRecipeWhenPluginIsEnabled() {
        server.getPluginManager().disablePlugin(plugin);
        var player = server.addPlayer();
        player.undiscoverRecipe(key);
        assertFalse(player.hasDiscoveredRecipe(key));
        server.getPluginManager().enablePlugin(plugin);
        assertTrue(player.hasDiscoveredRecipe(key));
        assertNotNull(server.getRecipe(key));
    }

    @Test
    void disablingRemovesTheRecipeAndReenablingRestoresIt() {
        server.getPluginManager().disablePlugin(plugin);
        assertNull(server.getRecipe(key));
        server.getPluginManager().enablePlugin(plugin);
        assertNotNull(server.getRecipe(key));
    }
}
