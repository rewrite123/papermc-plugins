package io.github.ihormel.flipdisc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlipDiscPlugin extends JavaPlugin implements Listener {
    private final Set<NamespacedKey> registered = new HashSet<>();

    @Override
    public void onEnable() {
        Map<NamespacedKey, Material> recipes = new HashMap<>();
        for (var pair : DiscPairs.OPPOSITES.entrySet()) {
            NamespacedKey key = new NamespacedKey(this, "flip_" + pair.getKey().name().toLowerCase(Locale.ROOT));
            ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(pair.getValue()));
            recipe.addIngredient(pair.getKey());
            if (!getServer().addRecipe(recipe)) {
                throw new IllegalStateException("Unable to register flip recipe " + key);
            }
            registered.add(key);
            recipes.put(key, pair.getKey());
        }
        getServer().getPluginManager().registerEvents(new DiscCrafting(recipes,
                item -> item.isDataOverridden(DataComponentTypes.JUKEBOX_PLAYABLE)), this);
        getServer().getPluginManager().registerEvents(this, this);
        for (var player : getServer().getOnlinePlayers()) player.discoverRecipes(registered);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(registered);
    }

    @Override
    public void onDisable() {
        for (NamespacedKey key : registered) getServer().removeRecipe(key);
        registered.clear();
    }
}
