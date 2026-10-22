package io.github.ihormel.usefulcauldrons;

import static org.junit.jupiter.api.Assertions.*;
import static org.bukkit.Material.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;

class CauldronTest {
    @TempDir Path directory;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    static Stream<Recipes.Recipe> stews() {
        return Recipes.STEWS.stream();
    }

    private CauldronContents fill(Recipes.Recipe recipe) {
        var state = new CauldronContents();
        for (var ingredient : recipe.ingredients()) {
            state.deposit(ingredient.alternatives().getFirst(), ingredient.amount());
        }
        return state;
    }

    @ParameterizedTest
    @MethodSource("stews")
    void everyStewConsumesExactlyItsIngredientsAndYieldsDocumentedServings(Recipes.Recipe recipe) {
        var state = fill(recipe);
        var plan = Cooking.next(state, true, true, false);
        assertNotNull(plan);
        assertEquals(recipe.food(), plan.food());
        assertEquals(recipe.servings(), plan.servings());
        assertEquals(1, plan.water());
        plan.apply(state);
        assertEquals(0, state.size());
        assertEquals(recipe.servings(), state.servings);
        assertEquals(recipe.food(), state.food);
        assertTrue(plan.returns().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("stews")
    void everyRecipeRejectsEachMissingIngredient(Recipes.Recipe recipe) {
        for (var ingredient : recipe.ingredients()) {
            var state = fill(recipe);
            state.consume(Map.of(ingredient.alternatives().getFirst(), 1));
            assertNull(recipe.match(state), recipe.food() + ": " + ingredient);
        }
    }

    @Test
    void customRecipesHaveExactlyTwoServingsAndCorrectBlazeCosts() {
        for (var recipe : Recipes.STEWS.subList(0, 6)) {
            assertEquals(2, recipe.servings());
            assertEquals(recipe.food().equals("hearty_stew") ? 2 : 1, fill(recipe).count(BLAZE_POWDER));
        }
        assertEquals(4, fill(Recipes.STEWS.get(2)).count(GOLDEN_CARROT));
    }

    @Test
    void speedyAcceptsAllFiveProteins() {
        for (var protein : new Material[] {RABBIT, COD, SALMON, TROPICAL_FISH, PUFFERFISH}) {
            var state = new CauldronContents();
            for (var item : new Material[] {protein, NETHER_WART, CARROT, SWEET_BERRIES, BLAZE_POWDER}) {
                state.deposit(item, 1);
            }
            assertEquals("speedy_stew", Cooking.next(state, true, true, false).food());
        }
    }

    @Test
    void goldenFlavorAcceptsCocoaAndGlowBerries() {
        for (var flavor : new Material[] {COCOA_BEANS, GLOW_BERRIES}) {
            var state = new CauldronContents();
            for (var item : new Material[] {GOLDEN_APPLE, GOLDEN_CARROT, flavor, BLAZE_POWDER}) state.deposit(item, 1);
            assertEquals("golden_flavor", Cooking.next(state, true, true, false).food());
        }
    }

    @Test
    void customRecipesRequireHeatWaterAndAnOpenLid() {
        var state = fill(Recipes.STEWS.getFirst());
        assertNull(Cooking.next(state, true, false, false));
        assertNull(Cooking.next(state, false, true, false));
        assertNull(Cooking.next(state, true, true, true));
    }

    @Test
    void collectingOneBatchIsRequiredBeforeNextBatchCanCook() {
        var state = fill(Recipes.STEWS.getFirst());
        state.deposit(COCOA_BEANS, 8);
        var plan = Cooking.next(state, true, true, false);
        plan.apply(state);
        assertEquals(8, state.count(COCOA_BEANS));
        assertNull(Cooking.next(state, true, true, false));
    }

    @Test
    void milkConsumesFourBowlsReturnsOneBucketAndCanUpgradeOnlyAllFourStoredServings() {
        var state = fill(Recipes.MILK);
        assertNull(Cooking.next(state, true, true, false));
        var milk = Cooking.next(state, false, true, false);
        assertEquals(Map.of(BUCKET, 1), milk.returns());
        milk.apply(state);
        assertEquals(4, state.servings);
        assertEquals(0, state.size());
        assertTrue(Foods.prepaid(state.food));
        state.deposit(COCOA_BEANS, 6);
        state.deposit(SUGAR, 2);
        state.servings = 3;
        assertNull(Cooking.next(state, false, true, false));
        state.servings = 4;
        var chocolate = Cooking.next(state, false, true, false);
        chocolate.apply(state);
        assertEquals("hot_chocolate", state.food);
        assertEquals(4, state.servings);
        assertEquals(0, state.size());
    }

    @Test
    void allNineCandyFoodsUseExactSugarAndHoneyCosts() {
        for (var food : Recipes.CANDY) {
            for (boolean honey : new boolean[] {false, true}) {
                var state = new CauldronContents();
                state.deposit(food, 2);
                state.deposit(honey ? HONEY_BOTTLE : SUGAR, honey ? 12 : 26);
                var plan = Cooking.next(state, false, true, false);
                assertNotNull(plan, food.toString());
                assertEquals((honey ? "honey:" : "candy:") + food.name(), plan.food());
                assertEquals(honey ? Map.of(GLASS_BOTTLE, 12) : Map.of(), plan.returns());
                plan.apply(state);
                assertEquals(1, state.count(food));
                assertEquals(0, state.count(honey ? HONEY_BOTTLE : SUGAR));
            }
        }
    }

    @Test
    void candyRejectsInsufficientSweetenerAndPrefersHoney() {
        var state = new CauldronContents();
        state.deposit(APPLE, 1);
        state.deposit(SUGAR, 25);
        state.deposit(HONEY_BOTTLE, 11);
        assertNull(Cooking.next(state, false, true, false));
        state.deposit(SUGAR, 1);
        state.deposit(HONEY_BOTTLE, 1);
        var plan = Cooking.next(state, false, true, false);
        assertEquals("honey:APPLE", plan.food());
        plan.apply(state);
        assertEquals(26, state.count(SUGAR));
    }

    @Test
    void concreteCostsEightUsesPerEightBlocksAndCannotOverdrawDye() {
        var state = new CauldronContents();
        state.dye = DyeColor.BLUE;
        state.uses = 64;
        state.deposit(SAND, 36);
        state.deposit(GRAVEL, 36);
        for (int i = 0; i < 8; i++) {
            var plan = Cooking.next(state, true, false, false);
            assertEquals(Map.of(BLUE_CONCRETE, 8), plan.returns());
            plan.apply(state);
        }
        assertEquals(0, state.uses);
        assertNull(state.dye);
        assertEquals(4, state.count(SAND));
        assertNull(Cooking.next(state, true, false, false));
        state.dye = DyeColor.RED;
        state.uses = 7;
        assertNull(Cooking.next(state, true, true, false));
        assertThrows(IllegalArgumentException.class, () -> state.useDye(8));
    }

    @Test
    void capacityAndAtomicIngredientChecksPreventLossAndNegativeCounts() {
        var state = new CauldronContents();
        assertEquals(4096, state.deposit(SUGAR, 5000));
        assertEquals(0, state.deposit(APPLE, 1));
        assertThrows(IllegalArgumentException.class, () -> state.consume(Map.of(APPLE, 1, SUGAR, 4)));
        assertEquals(4096, state.count(SUGAR));
        assertThrows(IllegalArgumentException.class, () -> state.consume(Map.of(SUGAR, -1)));
    }

    @Test
    void charcoalHonorsRatesKeepsRemaindersAndDoesNotBurnUnlistedItems() {
        var state = new CauldronContents();
        state.deposit(OAK_LOG, 1);
        state.deposit(OAK_PLANKS, 3);
        state.deposit(OAK_SLAB, 5);
        state.deposit(OAK_LEAVES, 7);
        state.deposit(WHEAT_SEEDS, 9);
        state.deposit(BLAZE_POWDER, 1);
        var batch = Charcoal.batch(state);
        assertEquals(6, batch.output());
        state.consume(batch.costs());
        assertEquals(Map.of(OAK_PLANKS, 1, OAK_SLAB, 1, OAK_LEAVES, 3, WHEAT_SEEDS, 1,
                BLAZE_POWDER, 1), state.ingredients);
        assertEquals(0, Charcoal.batch(state).output());
        assertNull(Charcoal.ratio(DIAMOND));
    }

    @Test
    void everyWoodCategoryUsesBalancedRatesAndKeepsIncompleteGroups() {
        var categories = Map.of(
                org.bukkit.Tag.LOGS, new Charcoal.Ratio(1, 2),
                org.bukkit.Tag.PLANKS, new Charcoal.Ratio(2, 1),
                org.bukkit.Tag.WOODEN_STAIRS, new Charcoal.Ratio(2, 1),
                org.bukkit.Tag.WOODEN_SLABS, new Charcoal.Ratio(4, 1),
                org.bukkit.Tag.WOODEN_FENCES, new Charcoal.Ratio(2, 1),
                org.bukkit.Tag.FENCE_GATES, new Charcoal.Ratio(2, 1),
                org.bukkit.Tag.WOODEN_DOORS, new Charcoal.Ratio(2, 1),
                org.bukkit.Tag.WOODEN_TRAPDOORS, new Charcoal.Ratio(2, 1));
        categories.forEach((tag, ratio) -> {
            assertFalse(tag.getValues().isEmpty(), tag.getKey().toString());
            for (var material : tag.getValues()) {
                assertEquals(ratio, Charcoal.ratio(material), material.toString());
                var state = new CauldronContents();
                state.deposit(material, 3 * ratio.input() - 1);
                var batch = Charcoal.batch(state);
                assertEquals(2 * ratio.output(), batch.output(), material.toString());
                state.consume(batch.costs());
                assertEquals(ratio.input() - 1, state.count(material), material.toString());
                assertEquals(0, Charcoal.batch(state).output(), material.toString());
            }
        });
    }

    @Test
    void dyeingPreservesLeatherMetadataAndCleansItInOrdinaryWater() {
        var armor = new ItemStack(LEATHER_HELMET);
        armor.editMeta(LeatherArmorMeta.class, meta -> {
            meta.setDisplayName("My Helmet");
            assertInstanceOf(org.bukkit.inventory.meta.Damageable.class, meta).setDamage(4);
            meta.setColor(DyeColor.RED.getColor());
        });
        var dyed = Dyeing.transform(armor, DyeColor.BLUE);
        var meta = (LeatherArmorMeta) dyed.getItemMeta();
        assertEquals(DyeColor.BLUE.getColor(), meta.getColor());
        assertEquals(4, assertInstanceOf(org.bukkit.inventory.meta.Damageable.class, meta).getDamage());
        assertEquals("My Helmet", meta.getDisplayName());
        var clean = Dyeing.transform(dyed, null);
        assertEquals(MockBukkit.getMock().getItemFactory().getDefaultLeatherColor(),
                ((LeatherArmorMeta) clean.getItemMeta()).getColor());
    }

    @Test
    void recoloringShulkerBoxesPreservesStoredItems() {
        var item = new ItemStack(RED_SHULKER_BOX);
        item.editMeta(BlockStateMeta.class, meta -> {
            var box = (org.bukkit.block.ShulkerBox) meta.getBlockState();
            box.getInventory().setItem(0, new ItemStack(DIAMOND, 12));
            meta.setBlockState(box);
        });
        var dyed = Dyeing.transform(item, DyeColor.BLUE);
        assertEquals(BLUE_SHULKER_BOX, dyed.getType());
        var box = (org.bukkit.block.ShulkerBox) ((BlockStateMeta) dyed.getItemMeta()).getBlockState();
        assertEquals(new ItemStack(DIAMOND, 12), box.getInventory().getItem(0));
    }

    @Test
    void concreteAndStandardFamiliesAreTransformedCorrectly() {
        assertEquals(RED_CONCRETE, Dyeing.transform(new ItemStack(RED_CONCRETE_POWDER), null).getType());
        assertEquals(BLUE_CONCRETE, Dyeing.transform(new ItemStack(RED_CONCRETE_POWDER), DyeColor.BLUE).getType());
        assertEquals(BLUE_WOOL, Dyeing.transform(new ItemStack(WHITE_WOOL), DyeColor.BLUE).getType());
        assertEquals(BLUE_STAINED_GLASS, Dyeing.transform(new ItemStack(GLASS), DyeColor.BLUE).getType());
        assertEquals(BLUE_CANDLE, Dyeing.transform(new ItemStack(CANDLE), DyeColor.BLUE).getType());
        assertNull(Dyeing.transform(new ItemStack(STONE), DyeColor.BLUE));
        assertNull(Dyeing.transform(new ItemStack(RED_WOOL), null));
        assertNull(Dyeing.transform(new ItemStack(BLUE_WOOL), DyeColor.BLUE));
    }

    @Test
    void storageRoundTripsIngredientsReadyFoodDyeAndCharcoalProgress() throws IOException {
        var position = new ContentsStore.Position(UUID.randomUUID(), -17, 80, 32);
        var state = fill(Recipes.MILK);
        state.food = "warm_milk";
        state.servings = 4;
        state.dye = DyeColor.PURPLE;
        state.uses = 17;
        state.charcoalTicks = 750;
        var store = new ContentsStore(directory.resolve("contents.yml"));
        store.save(Map.of(position, state));
        var loaded = store.load().get(position);
        assertEquals(state.ingredients, loaded.ingredients);
        assertEquals("warm_milk", loaded.food);
        assertEquals(4, loaded.servings);
        assertEquals(DyeColor.PURPLE, loaded.dye);
        assertEquals(17, loaded.uses);
        assertEquals(750, loaded.charcoalTicks);
        assertFalse(Files.exists(directory.resolve("contents.yml.tmp")));
        store.save(Map.of());
        assertTrue(store.load().isEmpty());
    }

    @Test
    void previouslyCookedEightServingBatchesRemainLoadableAfterYieldChange() throws IOException {
        var state = new CauldronContents();
        state.food = "golden_flavor";
        state.servings = 8;
        var position = new ContentsStore.Position(UUID.randomUUID(), 1, 65, 3);
        var store = new ContentsStore(directory.resolve("contents.yml"));
        store.save(Map.of(position, state));
        var loaded = store.load().get(position);
        assertEquals("golden_flavor", loaded.food);
        assertEquals(8, loaded.servings);
        assertNull(Cooking.next(loaded, true, true, false));
    }

    @Test
    void corruptStorageFailsExplicitlyWithoutOverwriting() throws IOException {
        var file = directory.resolve("contents.yml");
        Files.writeString(file, "schema: 999\n");
        assertThrows(IOException.class, () -> new ContentsStore(file).load());
        assertEquals("schema: 999\n", Files.readString(file));
    }

    @Test
    void storageRejectsExcessDyeAndUnknownFood() throws IOException {
        var state = new CauldronContents();
        var position = new ContentsStore.Position(UUID.randomUUID(), 1, 2, 3);
        var file = directory.resolve("contents.yml");
        var store = new ContentsStore(file);
        state.dye = DyeColor.RED;
        state.uses = 65;
        store.save(Map.of(position, state));
        assertThrows(IOException.class, store::load);
        state.uses = 4;
        state.food = "not_a_food";
        state.servings = 1;
        store.save(Map.of(position, state));
        assertThrows(IOException.class, store::load);
    }

    @Test
    void effectCatalogAndCandyDurationsHaveExactRequestedThresholds() {
        assertEquals(new Foods.Effect(PotionEffectType.REGENERATION, 8), Foods.EFFECTS.get("golden_flavor"));
        assertEquals(30, Foods.EFFECTS.get("speedy_stew").seconds());
        assertEquals(30, Foods.EFFECTS.get("carrota_stew").seconds());
        assertEquals(30, Foods.EFFECTS.get("spicy_chilli").seconds());
        assertEquals(10, Foods.EFFECTS.get("hearty_stew").seconds());
        assertEquals(10, Foods.EFFECTS.get("fish_soup").seconds());
        assertEquals(10, Foods.candySeconds("candy:APPLE"));
        assertEquals(20, Foods.candySeconds("honey:APPLE"));
        assertEquals(15, Foods.candySeconds("candy:GOLDEN_APPLE"));
        assertEquals(25, Foods.candySeconds("honey:GLISTERING_MELON_SLICE"));
        assertEquals(25, Foods.candySeconds("honey:GOLDEN_CARROT"));
    }
}
