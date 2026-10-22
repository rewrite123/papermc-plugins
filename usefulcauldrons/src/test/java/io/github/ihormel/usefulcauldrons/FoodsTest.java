package io.github.ihormel.usefulcauldrons;

import static org.junit.jupiter.api.Assertions.*;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class FoodsTest {
    private ServerMock server;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    static Stream<String> foods() {
        return Stream.concat(Recipes.STEWS.stream().map(Recipes.Recipe::food),
                Stream.concat(Stream.of("warm_milk", "hot_chocolate"),
                        Recipes.CANDY.stream().flatMap(m -> Stream.of("candy:" + m.name(), "honey:" + m.name()))));
    }

    @ParameterizedTest
    @MethodSource("foods")
    void everyFoodHasANativeConsumableAndAnUnambiguousPersistentIdentity(String id) {
        var item = Foods.create(id);
        assertEquals(id, Foods.id(item));
        assertTrue(item.getData(DataComponentTypes.FOOD).canAlwaysEat());
        assertNotNull(item.getData(DataComponentTypes.CONSUMABLE));
        assertTrue(item.getData(DataComponentTypes.CONSUMABLE).consumeEffects().isEmpty());
        assertNull(item.getData(DataComponentTypes.USE_REMAINDER));
        if (!Foods.candy(id)) assertEquals(1, item.getMaxStackSize());
    }

    @Test
    void candyPumpkinUsesPieModelAndAllCandyHasIncreasedNutrition() {
        var pumpkin = Foods.create("candy:PUMPKIN");
        assertEquals(Material.PUMPKIN_PIE, pumpkin.getType());
        assertEquals(Material.PUMPKIN_PIE.getKey(), pumpkin.getItemMeta().getItemModel());
        assertEquals(12, pumpkin.getData(DataComponentTypes.FOOD).nutrition());
        assertEquals(14.4f, pumpkin.getData(DataComponentTypes.FOOD).saturation());
    }

    @Test
    void milkAndChocolateUsePrepaidBowlsAndTheirExactFoodValues() {
        var milk = Foods.create("warm_milk");
        assertEquals(Material.MILK_BUCKET.getKey(), milk.getItemMeta().getItemModel());
        assertEquals(0, milk.getData(DataComponentTypes.FOOD).nutrition());
        var chocolate = Foods.create("hot_chocolate");
        assertEquals(8, chocolate.getData(DataComponentTypes.FOOD).nutrition());
        assertEquals(9.6f, chocolate.getData(DataComponentTypes.FOOD).saturation(), 0.001f);
    }

    @Test
    void milkClearsEveryEffectAndStartsSixSecondsOfImmunity() {
        var player = server.addPlayer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 600, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
        var listener = new MealsListener(null);
        long now = System.currentTimeMillis();
        listener.apply(player, "warm_milk", now);
        assertTrue(player.getActivePotionEffects().isEmpty());
        assertEquals(now + 6000, player.getPersistentDataContainer().get(MealsListener.IMMUNE, PersistentDataType.LONG));
        assertTrue(MealsListener.active(player, MealsListener.IMMUNE, now + 5999));
        assertFalse(MealsListener.active(player, MealsListener.IMMUNE, now + 6000));
    }

    @Test
    void exactAbsorptionQuantitiesAndDurationsAreApplied() {
        var listener = new MealsListener(null);
        var golden = server.addPlayer();
        listener.apply(golden, "golden_flavor", System.currentTimeMillis());
        assertEquals(5, golden.getAbsorptionAmount());
        assertEquals(600, golden.getPotionEffect(PotionEffectType.ABSORPTION).getDuration());
        assertEquals(160, golden.getPotionEffect(PotionEffectType.REGENERATION).getDuration());
        var hearty = server.addPlayer();
        hearty.setFoodLevel(3);
        listener.apply(hearty, "hearty_stew", System.currentTimeMillis());
        assertEquals(1, hearty.getAbsorptionAmount());
        assertEquals(20, hearty.getFoodLevel());
        assertEquals(200, hearty.getPotionEffect(PotionEffectType.STRENGTH).getDuration());
        var chocolate = server.addPlayer();
        long now = System.currentTimeMillis();
        listener.apply(chocolate, "hot_chocolate", now);
        assertEquals(2, chocolate.getAbsorptionAmount());
        assertEquals(now + 8000, chocolate.getPersistentDataContainer().get(MealsListener.IMMUNE, PersistentDataType.LONG));
    }

    @Test
    void absorptionDoesNotShortenStrongerExistingEffectsAndCanReplenishHearts() {
        var player = server.addPlayer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 3));
        player.setAbsorptionAmount(9);
        Foods.absorption(player, 5);
        assertEquals(9, player.getAbsorptionAmount());
        assertEquals(2400, player.getPotionEffect(PotionEffectType.ABSORPTION).getDuration());
        assertEquals(3, player.getPotionEffect(PotionEffectType.ABSORPTION).getAmplifier());
        player.setAbsorptionAmount(0);
        Foods.absorption(player, 5);
        assertEquals(5, player.getAbsorptionAmount());
    }

    @Test
    void persistentBurnHasExactTwentySecondDurationAndMilkDoesNotCureIt() {
        var player = server.addPlayer();
        long now = System.currentTimeMillis();
        MealsListener.burn(player, now);
        assertEquals(400, player.getFireTicks());
        assertTrue(MealsListener.active(player, MealsListener.BURN, now + 19_999));
        assertFalse(MealsListener.active(player, MealsListener.BURN, now + 20_000));
        new MealsListener(null).apply(player, "warm_milk", now);
        assertTrue(MealsListener.active(player, MealsListener.BURN, now + 6000));
    }
}
