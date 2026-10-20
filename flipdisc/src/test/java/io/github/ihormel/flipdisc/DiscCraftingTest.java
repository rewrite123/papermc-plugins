package io.github.ihormel.flipdisc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscCraftingTest {
    private final Map<Material, ShapelessRecipe> recipes = new HashMap<>();
    private final AtomicBoolean customSong = new AtomicBoolean();
    private DiscCrafting crafting;
    private CraftingInventory inventory;
    private ItemStack input;
    private ItemStack output;
    private ItemStack[] matrix;

    @BeforeEach
    void setup() {
        Map<NamespacedKey, Material> keys = new HashMap<>();
        for (Material material : DiscPairs.OPPOSITES.keySet()) {
            NamespacedKey key = new NamespacedKey("flipdisc", material.name().toLowerCase(java.util.Locale.ROOT));
            ShapelessRecipe recipe = mock(ShapelessRecipe.class);
            when(recipe.getKey()).thenReturn(key);
            keys.put(key, material);
            recipes.put(material, recipe);
        }
        crafting = new DiscCrafting(keys, ignored -> customSong.get());
        inventory = mock(CraftingInventory.class);
        input = item(Material.MUSIC_DISC_CAT);
        output = item(Material.MUSIC_DISC_TEARS);
        when(input.withType(Material.MUSIC_DISC_TEARS)).thenReturn(output);
        when(inventory.getResult()).thenReturn(output);
        matrix = new ItemStack[]{null, input, null, null};
        when(inventory.getMatrix()).thenAnswer(ignored -> matrix);
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        return item;
    }

    private ShapelessRecipe recipe() {
        return recipes.get(Material.MUSIC_DISC_CAT);
    }

    private PrepareItemCraftEvent prepare(Recipe recipe) {
        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getRecipe()).thenReturn(recipe);
        when(event.getInventory()).thenReturn(inventory);
        return event;
    }

    private CraftItemEvent craft(ItemStack result) {
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getRecipe()).thenReturn(recipe());
        when(event.getInventory()).thenReturn(inventory);
        when(event.getCurrentItem()).thenReturn(result);
        return event;
    }

    @Test
    void previewUsesMetadataPreservingTypeConversionAndProducesOneDisc() {
        crafting.prepare(prepare(recipe()));
        verify(input).withType(Material.MUSIC_DISC_TEARS);
        verify(output).setAmount(1);
        verify(inventory).setResult(output);
        verify(input, never()).setAmount(anyInt());
        verify(input, never()).setType(any());
    }

    @Test
    void worksInEverySlotOfBothCraftingGridSizes() {
        for (int size : new int[]{4, 9}) {
            for (int slot = 0; slot < size; slot++) {
                ItemStack[] grid = new ItemStack[size];
                grid[slot] = input;
                assertSame(output, crafting.flip(recipe(), grid));
            }
        }
    }

    @Test
    void everyPairWorksInBothDirections() {
        DiscPairs.OPPOSITES.forEach((source, target) -> {
            ItemStack disc = item(source);
            ItemStack flipped = item(target);
            when(disc.withType(target)).thenReturn(flipped);
            assertSame(flipped, crafting.flip(recipes.get(source), new ItemStack[]{disc, null, null, null}));
            verify(flipped).setAmount(1);
        });
    }

    @Test
    void rejectsEmptyGridAndWrongIngredient() {
        assertNull(crafting.flip(recipe(), new ItemStack[4]));
        assertNull(crafting.flip(recipe(), new ItemStack[]{item(Material.STONE), null, null, null}));
        assertNull(crafting.flip(recipe(), new ItemStack[]{item(Material.MUSIC_DISC_5), null, null, null}));
        assertNull(crafting.flip(recipes.get(Material.MUSIC_DISC_BLOCKS),
                new ItemStack[]{item(Material.MUSIC_DISC_LAVA_CHICKEN), null, null, null}));
        verify(input, never()).withType(any());
    }

    @Test
    void rejectsTwoDiscsOrExtraIngredientsEvenIfTheyWouldStack() {
        assertNull(crafting.flip(recipe(), new ItemStack[]{input, input, null, null}));
        assertNull(crafting.flip(recipe(), new ItemStack[]{input, item(Material.DIRT), null, null}));
    }

    @Test
    void ignoresAirSlots() {
        ItemStack empty = item(Material.AIR);
        when(empty.isEmpty()).thenReturn(true);
        assertSame(output, crafting.flip(recipe(), new ItemStack[]{empty, input, empty, null}));
    }

    @Test
    void stackedCustomDiscsStillProduceOnlyOnePerCraft() {
        when(input.getAmount()).thenReturn(16);
        assertSame(output, crafting.flip(recipe(), matrix));
        verify(output).setAmount(1);
        verify(input, never()).setAmount(anyInt());
    }

    @Test
    void customPlaybackCannotBeConverted() {
        customSong.set(true);
        crafting.prepare(prepare(recipe()));
        verify(inventory).setResult(null);
        verify(input, never()).withType(any());
        CraftItemEvent event = craft(output);
        crafting.craft(event);
        verify(event).setCancelled(true);
    }

    @Test
    void unrelatedRecipesAndVanillaSpecialDiscsAreNotTouched() {
        ShapelessRecipe other = mock(ShapelessRecipe.class);
        when(other.getKey()).thenReturn(new NamespacedKey("anotherplugin", "flipdisc"));
        crafting.prepare(prepare(other));
        crafting.prepare(prepare(null));
        crafting.prepare(prepare(mock(Recipe.class)));
        verify(inventory, never()).setResult(any());
        assertNull(crafting.flip(other, matrix));
        assertFalse(crafting.owns(other));
    }

    @Test
    void validResultIsHandledByNativeCraftingNotManualInventoryMutation() {
        when(output.isSimilar(output)).thenReturn(true);
        CraftItemEvent event = craft(output);
        crafting.craft(event);
        verify(event, never()).setCancelled(anyBoolean());
        verify(inventory, never()).setMatrix(any());
        verify(event, never()).setCurrentItem(any());
        verify(input, never()).setAmount(anyInt());
    }

    @Test
    void preventsTakingStaleOrReplacedResults() {
        CraftItemEvent stale = craft(item(Material.MUSIC_DISC_13));
        crafting.craft(stale);
        verify(stale).setCancelled(true);
        CraftItemEvent missing = craft(null);
        crafting.craft(missing);
        verify(missing).setCancelled(true);
        when(output.getAmount()).thenReturn(2);
        CraftItemEvent doubled = craft(output);
        crafting.craft(doubled);
        verify(doubled).setCancelled(true);
    }

    @Test
    void rechecksInputAtCraftTime() {
        crafting.prepare(prepare(recipe()));
        matrix = new ItemStack[4];
        CraftItemEvent event = craft(output);
        crafting.craft(event);
        verify(event).setCancelled(true);
    }

    @Test
    void neverUncancelsEventsCancelledByProtectionPlugins() {
        CraftItemEvent event = craft(output);
        crafting.craft(event);
        verify(event, never()).setCancelled(false);
    }

    @Test
    void doesNotReinstatePreviewRemovedByAnotherPlugin() {
        when(inventory.getResult()).thenReturn(null);
        crafting.prepare(prepare(recipe()));
        verify(inventory, never()).setResult(any());
        verify(input, never()).withType(any());
    }

    @Test
    void preventsCrafterAutomationOnlyForOwnRecipes() {
        CrafterCraftEvent own = mock(CrafterCraftEvent.class);
        when(own.getRecipe()).thenReturn(recipe());
        crafting.automate(own);
        verify(own).setCancelled(true);
        CrafterCraftEvent other = mock(CrafterCraftEvent.class);
        crafting.automate(other);
        verify(other, never()).setCancelled(anyBoolean());
    }
}
