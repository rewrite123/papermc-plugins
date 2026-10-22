package io.github.ihormel.usefulcauldrons;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class Foods {
    static final NamespacedKey ID = new NamespacedKey("usefulcauldrons", "food");
    record Effect(PotionEffectType type, int seconds) {}
    static final Map<String, Effect> EFFECTS = Map.of(
            "golden_flavor", new Effect(PotionEffectType.REGENERATION, 8),
            "speedy_stew", new Effect(PotionEffectType.SPEED, 30),
            "carrota_stew", new Effect(PotionEffectType.NIGHT_VISION, 30),
            "spicy_chilli", new Effect(PotionEffectType.FIRE_RESISTANCE, 30),
            "hearty_stew", new Effect(PotionEffectType.STRENGTH, 10),
            "fish_soup", new Effect(PotionEffectType.WATER_BREATHING, 10));

    static boolean valid(String id) {
        if (id == null) return false;
        if (EFFECTS.containsKey(id) || List.of("warm_milk", "hot_chocolate", "mushroom_stew",
                "beetroot_soup", "rabbit_stew").contains(id)) return true;
        if (id.startsWith("suspicious:")) {
            var flower = Material.matchMaterial(id.substring(11));
            return flower != null && Recipes.FLOWERS.containsKey(flower);
        }
        if (id.startsWith("candy:") || id.startsWith("honey:")) {
            var material = Material.matchMaterial(id.substring(id.indexOf(':') + 1));
            return material != null && Recipes.CANDY.contains(material);
        }
        return false;
    }

    static String id(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var id = item.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING);
        return valid(id) ? id : null;
    }

    static boolean prepaid(String id) {
        return "warm_milk".equals(id) || "hot_chocolate".equals(id);
    }

    static boolean candy(String id) {
        return id.startsWith("candy:") || id.startsWith("honey:");
    }

    static ItemStack create(String id) {
        if (!valid(id)) throw new IllegalArgumentException("Unknown cauldron food: " + id);
        boolean candy = candy(id);
        Material material = switch (id) {
            case "beetroot_soup" -> Material.BEETROOT_SOUP;
            case "rabbit_stew" -> Material.RABBIT_STEW;
            default -> id.startsWith("suspicious:") ? Material.SUSPICIOUS_STEW : Material.MUSHROOM_STEW;
        };
        Material model = material;
        String name = title(id);
        if (candy) {
            model = Material.valueOf(id.substring(id.indexOf(':') + 1));
            material = model == Material.PUMPKIN ? Material.PUMPKIN_PIE : model;
            if (model == Material.PUMPKIN) model = Material.PUMPKIN_PIE;
            name = (id.startsWith("honey:") ? "Honey Candied " : "Candied ")
                    + title(id.substring(id.indexOf(':') + 1));
        }
        var item = new ItemStack(material);
        var itemModel = prepaid(id) ? Material.MILK_BUCKET : model;
        var itemName = name;
        item.editMeta(meta -> {
            meta.itemName(Component.text(itemName, NamedTextColor.GOLD));
            meta.setItemModel(itemModel.getKey());
            meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
            meta.lore(List.of(Component.text(description(id), NamedTextColor.GRAY)));
        });
        int nutrition = candy ? 12 : id.equals("hot_chocolate") ? 8 : id.equals("warm_milk") ? 0
                : id.equals("rabbit_stew") ? 10 : 6;
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(nutrition)
                .saturation(candy ? 14.4f : nutrition * 1.2f).canAlwaysEat(true).build());
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(1.6f)
                .animation(prepaid(id)
                        ? io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation.DRINK
                        : io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation.EAT)
                .sound(Key.key(prepaid(id) ? "minecraft:entity.generic.drink" : "minecraft:entity.generic.eat"))
                .hasConsumeParticles(!prepaid(id)).effects(List.of()).build());
        // Suppress native honey/chorus/golden-apple behavior; effects are applied only after a successful consume.
        item.unsetData(DataComponentTypes.USE_REMAINDER);
        if (!candy) item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        return item;
    }

    static String title(String id) {
        if (id.startsWith("suspicious:")) return "Suspicious Stew";
        StringBuilder result = new StringBuilder();
        for (var word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    static String description(String id) {
        if (prepaid(id)) return "Clears effects and blocks new effects for " + (id.equals("warm_milk") ? 6 : 8) + "s.";
        if (candy(id)) return "Speed I for " + candySeconds(id) + "s. Restores 6 hunger icons.";
        if (EFFECTS.containsKey(id)) {
            var effect = EFFECTS.get(id);
            return title(effect.type().getKey().getKey()) + " I for " + effect.seconds() + "s.";
        }
        return "Freshly cooked in a cauldron.";
    }

    static int candySeconds(String id) {
        int seconds = id.startsWith("honey:") ? 20 : 10;
        var material = Material.valueOf(id.substring(id.indexOf(':') + 1));
        return seconds + (List.of(Material.GOLDEN_APPLE, Material.GOLDEN_CARROT,
                Material.GLISTERING_MELON_SLICE).contains(material) ? 5 : 0);
    }

    static void effect(Player player, PotionEffectType type, int seconds) {
        player.addPotionEffect(new PotionEffect(type, seconds * 20, 0));
    }

    static void absorption(Player player, double points) {
        double previous = player.getAbsorptionAmount();
        var old = player.getPotionEffect(PotionEffectType.ABSORPTION);
        int amplifier = Math.max(0, (int) Math.ceil(Math.max(previous, points) / 4) - 1);
        if (old != null && old.getAmplifier() >= amplifier && old.getDuration() >= 600) {
            player.setAbsorptionAmount(Math.max(previous, points));
            return;
        }
        int duration = Math.max(600, old == null ? 0 : old.getDuration());
        amplifier = Math.max(amplifier, old == null ? 0 : old.getAmplifier());
        if (player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier))) {
            player.setAbsorptionAmount(Math.max(previous, points));
        }
    }
}
