package io.github.ihormel.usefulcauldrons;

import static org.junit.jupiter.api.Assertions.*;
import static org.bukkit.Material.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class WaxTest {
    @TempDir Path directory;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void rawBeefAndPorkEachMakeOneWaxPerGunpowderLeavingExcessIngredients() {
        for (var meat : new Material[] {BEEF, PORKCHOP}) {
            var state = new CauldronContents();
            state.deposit(meat, 3);
            assertNull(Cooking.next(state, false, true, false));
            state.deposit(GUNPOWDER, 2);
            var plan = Cooking.next(state, false, true, false);
            assertEquals("render_wax", plan.id());
            assertEquals(Map.of(HONEYCOMB, 1), plan.returns());
            assertNull(plan.food());
            assertEquals(0, plan.water());
            plan.apply(state);
            assertEquals(2, state.count(meat));
            assertEquals(1, state.count(GUNPOWDER));
            Cooking.next(state, false, true, false).apply(state);
            assertEquals(1, state.count(meat));
            assertEquals(0, state.count(GUNPOWDER));
            assertNull(Cooking.next(state, false, true, false));
            assertEquals(0, state.servings);
        }
    }

    @Test
    void honeyExtractionConsumesOneCombAndBottleAndProducesOneOfEachOutput() {
        var state = new CauldronContents();
        state.deposit(HONEYCOMB, 2);
        assertNull(Cooking.next(state, false, true, false));
        state.deposit(GLASS_BOTTLE, 1);
        var plan = Cooking.next(state, false, true, false);
        assertEquals("extract_honey", plan.id());
        assertEquals(Map.of(HONEYCOMB, 1, HONEY_BOTTLE, 1), plan.returns());
        plan.apply(state);
        assertEquals(Map.of(HONEYCOMB, 1), state.ingredients);
        assertEquals(0, state.servings);
        assertNull(Cooking.next(state, false, true, false));
    }

    @Test
    void bothRecipesRequireEmptyHeatedUncoveredCauldronAndCannotReplaceStoredFood() {
        for (var recipe : new Recipes.Recipe[] {Recipes.WAX, Recipes.HONEY}) {
            var state = new CauldronContents();
            recipe.ingredients().forEach(i -> state.deposit(i.alternatives().getFirst(), i.amount()));
            assertNull(Cooking.next(state, true, true, false));
            assertNull(Cooking.next(state, false, false, false));
            assertNull(Cooking.next(state, false, true, true));
            state.food = "warm_milk";
            state.servings = 3;
            assertNull(Cooking.next(state, false, true, false));
        }
    }

    @Test
    void cookedMeatsCannotSubstituteForRawMeats() {
        for (var meat : new Material[] {COOKED_BEEF, COOKED_PORKCHOP}) {
            var state = new CauldronContents();
            state.deposit(meat, 1);
            state.deposit(GUNPOWDER, 1);
            assertNull(Cooking.next(state, false, true, false));
        }
    }

    @Test
    void waxHasWhiteDyeAppearanceButRetainsNativeHoneycombIngredientIdentity() {
        var wax = WaxItems.create(3);
        assertEquals(HONEYCOMB, wax.getType());
        assertEquals(3, wax.getAmount());
        assertEquals(WHITE_DYE.getKey(), wax.getItemMeta().getItemModel());
        assertEquals(Component.text("Wax", net.kyori.adventure.text.format.NamedTextColor.WHITE),
                wax.getItemMeta().itemName());
        assertEquals((byte) 1, wax.getItemMeta().getPersistentDataContainer()
                .get(WaxItems.MARKER, PersistentDataType.BYTE));
        assertTrue(new RecipeChoice.MaterialChoice(HONEYCOMB).test(wax));
        assertFalse(new RecipeChoice.MaterialChoice(WHITE_DYE).test(wax));
        assertFalse(wax.isSimilar(new ItemStack(HONEYCOMB)));
        assertNull(Dyeing.dye(wax.getType()));
        assertEquals(wax, ItemStack.deserializeBytes(wax.serializeAsBytes()));
    }

    @Test
    void partialHoneyIngredientsPersistAcrossRestarts() throws IOException {
        assertTrue(Recipes.ingredient(HONEYCOMB));
        assertTrue(Recipes.ingredient(GLASS_BOTTLE));
        var state = new CauldronContents();
        state.deposit(HONEYCOMB, 3);
        state.deposit(GLASS_BOTTLE, 2);
        var store = new ContentsStore(directory.resolve("contents.yml"));
        var position = new ContentsStore.Position(UUID.randomUUID(), 0, 65, 0);
        store.save(Map.of(position, state));
        var loaded = store.load().get(position);
        assertEquals(state.ingredients, loaded.ingredients);
        assertEquals("extract_honey", Cooking.next(loaded, false, true, false).id());
    }
}
