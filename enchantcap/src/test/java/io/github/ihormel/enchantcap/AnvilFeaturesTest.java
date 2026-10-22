package io.github.ihormel.enchantcap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.bukkit.Material.*;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.view.AnvilView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;

class AnvilFeaturesTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private ItemStack damaged(Material material, int damage) {
        var item = new ItemStack(material);
        item.editMeta(Damageable.class, meta -> meta.setDamage(damage));
        // MockBukkit lacks ItemStack's native repair-material delegate.
        var repairable = spy(item);
        doReturn(true).when(repairable).isRepairableBy(any(ItemStack.class));
        return repairable;
    }

    private PrepareAnvilEvent operation(AnvilView view, ItemStack first, ItemStack second, ItemStack result, int cost, int consumed) {
        var inventory = mock(AnvilInventory.class);
        when(inventory.getFirstItem()).thenReturn(first);
        when(inventory.getSecondItem()).thenReturn(second);
        var event = mock(PrepareAnvilEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getView()).thenReturn(view);
        when(event.getResult()).thenReturn(result);
        when(view.getRepairCost()).thenReturn(cost);
        when(view.getRepairItemCountCost()).thenReturn(consumed);
        return event;
    }

    @ParameterizedTest
    @CsvSource({
        "DIAMOND_PICKAXE,DIAMOND,3,13",
        "IRON_PICKAXE,IRON_INGOT,3,13",
        "COPPER_PICKAXE,COPPER_INGOT,3,13",
        "STONE_PICKAXE,COBBLESTONE,3,13",
        "WOODEN_PICKAXE,OAK_PLANKS,3,13",
        "GOLDEN_PICKAXE,GOLD_INGOT,3,13",
        "NETHERITE_PICKAXE,NETHERITE_INGOT,3,13",
        "DIAMOND_AXE,DIAMOND,3,13",
        "DIAMOND_SWORD,DIAMOND,2,20",
        "DIAMOND_HOE,DIAMOND,2,20",
        "DIAMOND_SHOVEL,DIAMOND,1,39",
        "DIAMOND_HELMET,DIAMOND,5,8",
        "DIAMOND_CHESTPLATE,DIAMOND,8,5",
        "DIAMOND_LEGGINGS,DIAMOND,7,6",
        "DIAMOND_BOOTS,DIAMOND,4,10"
    })
    void singleIngredientUsesCraftingMaterialCount(Material tool, Material ingredient, int denominator, int charge) {
        var first = damaged(tool, 20);
        var result = damaged(tool, 10);
        assertEquals(denominator, MaterialRepairCost.materialCount(tool));
        var view = mock(AnvilView.class);
        var event = operation(view, first, new ItemStack(ingredient, 64), result, 80, 1);
        new AnvilCostListener().prepare(event);
        verify(view).setRepairCost(charge);
        verify(view, never()).setRepairItemCountCost(anyInt());
        verify(event, never()).setResult(any());
        assertEquals(20, ((Damageable) first.getItemMeta()).getDamage());
        assertEquals(10, ((Damageable) result.getItemMeta()).getDamage());
    }

    @ParameterizedTest
    @CsvSource({"1,13", "2,26", "3,39", "4,39"})
    void usesActuallyConsumedMaterialsAndNeverChargesAboveCappedCost(int consumed, int charge) {
        var first = damaged(DIAMOND_PICKAXE, 100);
        var result = damaged(DIAMOND_PICKAXE, 10);
        assertEquals(charge, MaterialRepairCost.charge(39, first, new ItemStack(DIAMOND, 64), result, consumed));
    }

    @Test
    void cheapRepairsRoundUpAndNeverBecomeFree() {
        var first = damaged(DIAMOND_CHESTPLATE, 100);
        var result = damaged(DIAMOND_CHESTPLATE, 10);
        assertEquals(1, MaterialRepairCost.charge(1, first, new ItemStack(DIAMOND), result, 1));
        assertEquals(2, MaterialRepairCost.charge(9, first, new ItemStack(DIAMOND), result, 1));
        assertEquals(0, MaterialRepairCost.charge(0, first, new ItemStack(DIAMOND), result, 1));
        assertEquals(-1, MaterialRepairCost.charge(-1, first, new ItemStack(DIAMOND), result, 1));
    }

    @Test
    void toolCombiningRenamingInvalidMaterialsAndNonCraftedItemsAreNotDiscounted() {
        var first = damaged(DIAMOND_PICKAXE, 100);
        var result = damaged(DIAMOND_PICKAXE, 10);
        assertEquals(39, MaterialRepairCost.charge(39, first, new ItemStack(DIAMOND_PICKAXE), result, 1));
        doReturn(false).when(first).isRepairableBy(argThat(item -> item.getType() == IRON_INGOT));
        assertEquals(39, MaterialRepairCost.charge(39, first, new ItemStack(IRON_INGOT), result, 1));
        assertEquals(39, MaterialRepairCost.charge(39, first, new ItemStack(DIAMOND), first.clone(), 1));
        assertEquals(39, MaterialRepairCost.charge(39, first, new ItemStack(DIAMOND), result, 0));
        assertEquals(39, MaterialRepairCost.charge(39, first, null, result, 1));
        assertEquals(0, MaterialRepairCost.materialCount(ELYTRA));
        assertEquals(0, MaterialRepairCost.materialCount(MACE));
    }

    @Test
    void optionalBypassIsEnabledBeforeVanillaCalculatesAndNotEnabledByDefault() {
        var view = mock(AnvilView.class);
        var event = mock(InventoryOpenEvent.class);
        when(event.getView()).thenReturn(view);
        new AnvilCostListener(1).open(event);
        verify(view).bypassEnchantmentLevelRestriction(true);
        assertThrows(IllegalArgumentException.class, () -> new AnvilCostListener(-1));
        assertThrows(IllegalArgumentException.class, () -> new AnvilCostListener(255));
    }

    @Test
    void configuredLimitBoundsVanillaGeneratedOutputWithoutChangingInputItemsOrConflicts() {
        var first = new ItemStack(DIAMOND_PICKAXE);
        first.addUnsafeEnchantment(Enchantment.UNBREAKING, 4);
        var result = first.clone();
        result.addUnsafeEnchantment(Enchantment.UNBREAKING, 5);
        result.addUnsafeEnchantment(Enchantment.EFFICIENCY, 7);
        var adjusted = EnchantmentLimits.restrict(result, first, 1);
        assertNotSame(result, adjusted);
        assertEquals(4, adjusted.getEnchantmentLevel(Enchantment.UNBREAKING));
        assertEquals(6, adjusted.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals(5, result.getEnchantmentLevel(Enchantment.UNBREAKING));
        assertEquals(4, first.getEnchantmentLevel(Enchantment.UNBREAKING));
        assertFalse(adjusted.containsEnchantment(Enchantment.FORTUNE));
    }

    @Test
    void oneExtraAllowsUnbreakingFourButDoesNotGrantItWithoutVanillaResult() {
        var first = new ItemStack(DIAMOND_PICKAXE);
        first.addEnchantment(Enchantment.UNBREAKING, 3);
        var vanillaResult = first.clone();
        vanillaResult.addUnsafeEnchantment(Enchantment.UNBREAKING, 4);
        assertSame(vanillaResult, EnchantmentLimits.restrict(vanillaResult, first, 1));
        assertEquals(3, EnchantmentLimits.restrict(vanillaResult, first, 0).getEnchantmentLevel(Enchantment.UNBREAKING));
        assertSame(first, EnchantmentLimits.restrict(first, first, 1));
        var view = mock(AnvilView.class);
        var event = operation(view, first, first.clone(), vanillaResult, 45, 0);
        new AnvilCostListener(1).prepare(event);
        verify(view).setRepairCost(39);
        verify(view).bypassEnchantmentLevelRestriction(true);
        verify(event, never()).setResult(any());
    }

    @Test
    void existingHigherLevelsAreNeitherErasedNorIncreasedWhenSettingIsLowered() {
        var first = new ItemStack(DIAMOND_PICKAXE);
        first.addUnsafeEnchantment(Enchantment.UNBREAKING, 8);
        var loweredByVanilla = first.clone();
        loweredByVanilla.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        assertEquals(8, EnchantmentLimits.restrict(loweredByVanilla, first, 0)
                .getEnchantmentLevel(Enchantment.UNBREAKING));
        var upgradedByBypass = first.clone();
        upgradedByBypass.addUnsafeEnchantment(Enchantment.UNBREAKING, 9);
        assertEquals(8, EnchantmentLimits.restrict(upgradedByBypass, first, 1)
                .getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    void enchantedBooksUseStoredLevelsAndCannotTransferAboveConfiguredLimit() {
        var first = new ItemStack(ENCHANTED_BOOK);
        first.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(Enchantment.UNBREAKING, 3, true));
        var result = first.clone();
        result.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(Enchantment.UNBREAKING, 8, true));
        var adjusted = EnchantmentLimits.restrict(result, first, 1);
        assertEquals(Map.of(Enchantment.UNBREAKING, 4), EnchantmentLimits.levels(adjusted));
        assertEquals(Map.of(Enchantment.UNBREAKING, 3), EnchantmentLimits.levels(first));
        assertEquals(255, EnchantmentLimits.bounded(255, 5, 254, 0));
    }

    @Test
    void invalidResultsAreNeverRecreatedEvenWhenExtraLevelsAreEnabled() {
        var view = mock(AnvilView.class);
        var event = mock(PrepareAnvilEvent.class);
        when(event.getView()).thenReturn(view);
        when(view.getRepairCost()).thenReturn(80);
        new AnvilCostListener(1).prepare(event);
        verify(event, never()).setResult(any());
        verify(view, never()).setRepairCost(anyInt());
    }
}
