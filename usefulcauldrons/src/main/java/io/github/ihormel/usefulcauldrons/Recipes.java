package io.github.ihormel.usefulcauldrons;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import static org.bukkit.Material.*;

final class Recipes {
    record Ingredient(int amount, List<Material> alternatives) {
        Ingredient(int amount, Material... alternatives) {
            this(amount, List.of(alternatives));
        }
    }

    record Recipe(String food, int servings, List<Ingredient> ingredients) {
        Map<Material, Integer> match(CauldronContents contents) {
            var costs = new EnumMap<Material, Integer>(Material.class);
            for (var ingredient : ingredients) {
                int needed = ingredient.amount();
                for (var material : ingredient.alternatives()) {
                    int take = Math.min(needed, contents.count(material) - costs.getOrDefault(material, 0));
                    if (take > 0) {
                        costs.merge(material, take, Integer::sum);
                        needed -= take;
                    }
                }
                if (needed > 0) return null;
            }
            return costs;
        }
    }

    record Flower(PotionEffectType effect, int ticks) {}

    static final Map<Material, Flower> FLOWERS = flowers();
    static final List<Recipe> STEWS = stews();
    static final Recipe MILK = recipe("warm_milk", 4, one(MILK_BUCKET), new Ingredient(4, BOWL), one(BLAZE_POWDER));
    static final Recipe CHOCOLATE = recipe("hot_chocolate", 4, new Ingredient(6, COCOA_BEANS), new Ingredient(2, SUGAR));
    static final Recipe WAX = recipe("render_wax", 0, one(BEEF, PORKCHOP), one(GUNPOWDER));
    static final Recipe HONEY = recipe("extract_honey", 0, one(HONEYCOMB), one(GLASS_BOTTLE));
    static final Set<Material> CANDY = Set.of(APPLE, SWEET_BERRIES, CARROT, MELON_SLICE,
            GOLDEN_APPLE, GLISTERING_MELON_SLICE, GOLDEN_CARROT, PUMPKIN, CHORUS_FRUIT);

    static Ingredient one(Material... alternatives) {
        return new Ingredient(1, alternatives);
    }

    static Recipe recipe(String food, int servings, Ingredient... ingredients) {
        return new Recipe(food, servings, List.of(ingredients));
    }

    static List<Recipe> stews() {
        var recipes = new ArrayList<Recipe>();
        recipes.add(recipe("golden_flavor", 2, one(GOLDEN_APPLE), one(GOLDEN_CARROT),
                one(GLOW_BERRIES, COCOA_BEANS), one(BLAZE_POWDER)));
        recipes.add(recipe("speedy_stew", 2, one(RABBIT, COD, SALMON, TROPICAL_FISH, PUFFERFISH),
                one(NETHER_WART), one(CARROT), one(SWEET_BERRIES), one(BLAZE_POWDER)));
        recipes.add(recipe("carrota_stew", 2, new Ingredient(4, GOLDEN_CARROT), one(SHORT_GRASS),
                one(SPIDER_EYE), one(NETHER_WART), one(BLAZE_POWDER)));
        recipes.add(recipe("spicy_chilli", 2, one(BEEF), one(BEETROOT), one(DRIED_KELP), one(GUNPOWDER),
                one(MAGMA_CREAM), one(NETHER_WART), one(BLAZE_POWDER)));
        recipes.add(recipe("hearty_stew", 2, one(BEEF), one(PORKCHOP), one(MUTTON), one(PUMPKIN),
                one(POTATO), one(NETHER_WART), new Ingredient(2, BLAZE_POWDER)));
        recipes.add(recipe("fish_soup", 2, one(COD), one(SALMON), one(CARROT), one(SHORT_GRASS),
                one(VINE), one(NETHER_WART), one(BLAZE_POWDER)));
        FLOWERS.keySet().forEach(flower -> recipes.add(recipe("suspicious:" + flower.name(), 2,
                one(RED_MUSHROOM), one(BROWN_MUSHROOM), one(flower))));
        recipes.add(recipe("rabbit_stew", 2, one(COOKED_RABBIT), one(CARROT), one(BAKED_POTATO),
                one(RED_MUSHROOM, BROWN_MUSHROOM)));
        recipes.add(recipe("beetroot_soup", 2, new Ingredient(6, BEETROOT)));
        recipes.add(recipe("mushroom_stew", 2, one(RED_MUSHROOM), one(BROWN_MUSHROOM)));
        return List.copyOf(recipes);
    }

    static Map<Material, Flower> flowers() {
        var flowers = new LinkedHashMap<Material, Flower>();
        flowers.put(DANDELION, new Flower(PotionEffectType.SATURATION, 7));
        flowers.put(BLUE_ORCHID, new Flower(PotionEffectType.SATURATION, 7));
        flowers.put(POPPY, new Flower(PotionEffectType.NIGHT_VISION, 100));
        flowers.put(TORCHFLOWER, new Flower(PotionEffectType.NIGHT_VISION, 100));
        flowers.put(ALLIUM, new Flower(PotionEffectType.FIRE_RESISTANCE, 60));
        flowers.put(AZURE_BLUET, new Flower(PotionEffectType.BLINDNESS, 160));
        for (var tulip : List.of(RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP)) {
            flowers.put(tulip, new Flower(PotionEffectType.WEAKNESS, 180));
        }
        flowers.put(OXEYE_DAISY, new Flower(PotionEffectType.REGENERATION, 160));
        flowers.put(CORNFLOWER, new Flower(PotionEffectType.JUMP_BOOST, 120));
        flowers.put(LILY_OF_THE_VALLEY, new Flower(PotionEffectType.POISON, 240));
        flowers.put(WITHER_ROSE, new Flower(PotionEffectType.WITHER, 160));
        flowers.put(OPEN_EYEBLOSSOM, new Flower(PotionEffectType.BLINDNESS, 220));
        flowers.put(CLOSED_EYEBLOSSOM, new Flower(PotionEffectType.NAUSEA, 140));
        return Map.copyOf(flowers);
    }

    static boolean ingredient(Material material) {
        return material == SUGAR || material == HONEY_BOTTLE || CANDY.contains(material)
                || material == HONEYCOMB || material == GLASS_BOTTLE
                || MILK.ingredients().stream().anyMatch(i -> i.alternatives().contains(material))
                || CHOCOLATE.ingredients().stream().anyMatch(i -> i.alternatives().contains(material))
                || STEWS.stream().flatMap(r -> r.ingredients().stream())
                    .anyMatch(i -> i.alternatives().contains(material))
                || Charcoal.ratio(material) != null || material == SAND || material == GRAVEL;
    }
}
