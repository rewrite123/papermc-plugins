package io.github.ihormel.usefulcauldrons;

import java.util.Map;
import org.bukkit.Material;
import static org.bukkit.Material.*;

final class Cooking {
    record Plan(String id, String food, int servings, Map<Material, Integer> costs,
                Map<Material, Integer> returns, int water, int dye) {
        void apply(CauldronContents state) {
            state.consume(costs);
            if (dye > 0) state.useDye(dye);
            if (food != null) {
                state.food = food;
                state.servings = servings;
            }
        }
    }

    static Plan next(CauldronContents state, boolean water, boolean heated, boolean sealed) {
        if (sealed) return null;
        if (water && state.dye != null) {
            if (state.count(SAND) >= 4 && state.count(GRAVEL) >= 4 && state.uses >= 8) {
                return new Plan("dyed_concrete", null, 0, Map.of(SAND, 4, GRAVEL, 4),
                        Map.of(Material.valueOf(state.dye.name() + "_CONCRETE"), 8), 0, 8);
            }
            return null;
        }
        if (!heated) return null;
        if ("warm_milk".equals(state.food) && state.servings == 4) {
            var costs = Recipes.CHOCOLATE.match(state);
            return costs == null ? null : new Plan("hot_chocolate", "hot_chocolate", 4, costs, Map.of(), 0, 0);
        }
        if (state.servings > 0) return null;
        if (water) {
            for (var recipe : Recipes.STEWS) {
                var costs = recipe.match(state);
                if (costs != null) return new Plan(recipe.food(), recipe.food(), recipe.servings(), costs, Map.of(), 1, 0);
            }
        } else {
            var wax = Recipes.WAX.match(state);
            if (wax != null) return new Plan("render_wax", null, 0, wax, Map.of(HONEYCOMB, 1), 0, 0);
            var extraction = Recipes.HONEY.match(state);
            if (extraction != null) return new Plan("extract_honey", null, 0, extraction,
                    Map.of(HONEYCOMB, 1, HONEY_BOTTLE, 1), 0, 0);
            var milk = Recipes.MILK.match(state);
            if (milk != null) return new Plan("warm_milk", "warm_milk", 4, milk, Map.of(BUCKET, 1), 0, 0);
            for (var material : Material.values()) {
                if (!Recipes.CANDY.contains(material) || state.count(material) < 1) continue;
                boolean honey = state.count(HONEY_BOTTLE) >= 12;
                if (!honey && state.count(SUGAR) < 26) continue;
                return new Plan("candy", (honey ? "honey:" : "candy:") + material.name(), 1,
                        Map.of(material, 1, honey ? HONEY_BOTTLE : SUGAR, honey ? 12 : 26),
                        honey ? Map.of(GLASS_BOTTLE, 12) : Map.of(), 0, 0);
            }
        }
        return null;
    }
}
