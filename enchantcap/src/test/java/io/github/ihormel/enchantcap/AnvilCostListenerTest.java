package io.github.ihormel.enchantcap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnvilCostListenerTest {
    private final AnvilCostListener listener = new AnvilCostListener();

    @Test
    void removesServerLimitBeforeResultCalculation() {
        AnvilView anvil = mock(AnvilView.class);
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getView()).thenReturn(anvil);
        listener.open(event);
        verify(anvil).setMaximumRepairCost(Integer.MAX_VALUE);
        verify(anvil, never()).bypassEnchantmentLevelRestriction(anyBoolean());
        verify(anvil, never()).setRepairCost(anyInt());
    }

    @Test
    void respectsCancelledOpens() {
        AnvilView anvil = mock(AnvilView.class);
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getView()).thenReturn(anvil);
        when(event.isCancelled()).thenReturn(true);
        listener.open(event);
        verifyNoInteractions(anvil);
    }

    @Test
    void leavesOtherInventoriesAlone() {
        InventoryView view = mock(InventoryView.class);
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getView()).thenReturn(view);
        listener.open(event);
        verifyNoInteractions(view);
    }

    @ParameterizedTest
    @ValueSource(ints = {40, 41, 80, 1000, Integer.MAX_VALUE - 1})
    void capsExpensiveValidResultsWithoutModifyingItems(int cost) {
        AnvilView anvil = mock(AnvilView.class);
        PrepareAnvilEvent event = validResult(anvil, cost);
        listener.prepare(event);
        verify(anvil).setRepairCost(39);
        verify(anvil).setMaximumRepairCost(Integer.MAX_VALUE);
        verify(anvil, never()).setRepairItemCountCost(anyInt());
        verify(anvil, never()).bypassEnchantmentLevelRestriction(anyBoolean());
        verify(event, never()).setResult(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 20, 38, 39})
    void preservesAffordableAndDeniedCosts(int cost) {
        AnvilView anvil = mock(AnvilView.class);
        listener.prepare(validResult(anvil, cost));
        verify(anvil, never()).setRepairCost(anyInt());
    }

    @Test
    void doesNotCreateOrDiscountInvalidResult() {
        for (boolean emptyStack : new boolean[] {false, true}) {
            AnvilView anvil = mock(AnvilView.class);
            PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
            when(event.getView()).thenReturn(anvil);
            when(anvil.getRepairCost()).thenReturn(80);
            if (emptyStack) {
                ItemStack air = mock(ItemStack.class);
                when(air.isEmpty()).thenReturn(true);
                when(event.getResult()).thenReturn(air);
            }
            listener.prepare(event);
            verify(anvil, never()).setRepairCost(anyInt());
            verify(event, never()).setResult(any());
        }
    }

    private PrepareAnvilEvent validResult(AnvilView anvil, int cost) {
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        ItemStack result = mock(ItemStack.class);
        when(event.getView()).thenReturn(anvil);
        when(event.getInventory()).thenReturn(mock(AnvilInventory.class));
        when(event.getResult()).thenReturn(result);
        when(result.getEnchantments()).thenReturn(java.util.Map.of());
        when(anvil.getRepairCost()).thenReturn(cost);
        return event;
    }
}
