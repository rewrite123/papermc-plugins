package io.github.ihormel.spidereyenetherwartblock;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class SpiderEyeNetherWartBlockPlugin extends JavaPlugin implements Listener {
    private NamespacedKey recipeKey;

    @Override
    public void onEnable() {
        recipeKey = new NamespacedKey(this, "spider_eyes_to_nether_wart_block");
        var recipe = new ShapedRecipe(recipeKey, new ItemStack(Material.NETHER_WART_BLOCK));
        recipe.shape("EEE", "EEE", "EEE");
        recipe.setIngredient('E', Material.SPIDER_EYE);
        if (!getServer().addRecipe(recipe)) {
            throw new IllegalStateException("Could not register recipe " + recipeKey);
        }
        getServer().getPluginManager().registerEvents(this, this);
        for (var player : getServer().getOnlinePlayers()) player.discoverRecipe(recipeKey);
        HelpGuide.register(this, "spidereyenetherwartblock");
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(recipeKey);
    }

    @Override
    public void onDisable() {
        if (recipeKey != null) getServer().removeRecipe(recipeKey);
    }
}
