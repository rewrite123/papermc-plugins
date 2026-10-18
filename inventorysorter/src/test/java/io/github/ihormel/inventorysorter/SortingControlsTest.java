package io.github.ihormel.inventorysorter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SortingControlsTest extends PaperTest {
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final InventoryView view = mock(InventoryView.class);
    private final InventoryClickEvent event = mock(InventoryClickEvent.class);
    private final List<Runnable> tasks = new ArrayList<>();
    private final SortingControls controls = new SortingControls(tasks::add, new DoubleShiftClick(() -> 0));

    @BeforeEach
    void setUp() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.hasPermission("inventorysorter.use")).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(inventory.getSize()).thenReturn(41);
        Inventory top = mock(Inventory.class);
        when(top.getType()).thenReturn(InventoryType.CRAFTING);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(inventory);
        when(view.getType()).thenReturn(InventoryType.CRAFTING);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getSlot()).thenReturn(9);
        when(event.getRawSlot()).thenReturn(9);
        when(event.isShiftClick()).thenReturn(true);
    }

    @Test
    void sortsOnlyAfterSecondEmptyShiftClickAndNextTick() {
        controls.click(event);
        assertTrue(tasks.isEmpty());
        controls.click(event);
        assertEquals(1, tasks.size());
        verify(event).setCancelled(true);
        verify(inventory, never()).setItem(anyInt(), any());
        tasks.getFirst().run();
        verify(inventory).setItem(9, null);
        verify(inventory).setItem(35, null);
        verify(player).sendMessage("Inventory sorted by group and item type.");
    }

    @Test
    void ignoresCancelledClicksAndResetsGesture() {
        controls.click(event);
        when(event.isCancelled()).thenReturn(true);
        controls.click(event);
        when(event.isCancelled()).thenReturn(false);
        controls.click(event);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void rejectsOccupiedSlotOrCursorAndOrdinaryClicks() {
        ItemStack occupied = mock(ItemStack.class);
        when(occupied.getType()).thenReturn(Material.STONE);
        when(occupied.getAmount()).thenReturn(1);
        when(event.getCurrentItem()).thenReturn(occupied);
        controls.click(event);
        controls.click(event);
        when(event.getCurrentItem()).thenReturn(null);
        when(event.getCursor()).thenReturn(occupied);
        controls.click(event);
        controls.click(event);
        when(event.getCursor()).thenReturn(null);
        when(event.isShiftClick()).thenReturn(false);
        controls.click(event);
        controls.click(event);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void refusesHotbarAndPlayersWithoutPermission() {
        when(event.getSlot()).thenReturn(0);
        controls.click(event);
        controls.click(event);
        when(event.getSlot()).thenReturn(9);
        when(player.hasPermission("inventorysorter.use")).thenReturn(false);
        controls.click(event);
        controls.click(event);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void doesNotWriteIfViewClosesBeforeScheduledSort() {
        controls.click(event);
        controls.click(event);
        when(player.getOpenInventory()).thenReturn(mock(InventoryView.class));
        tasks.getFirst().run();
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void doesNotWriteIfSlotBecomesOccupiedBeforeScheduledSort() {
        controls.click(event);
        controls.click(event);
        ItemStack occupied = mock(ItemStack.class);
        when(occupied.getType()).thenReturn(Material.STONE);
        when(occupied.getAmount()).thenReturn(1);
        when(view.getItem(9)).thenReturn(occupied);
        tasks.getFirst().run();
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void closingOrDisconnectingClearsGesture() {
        controls.click(event);
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        controls.close(close);
        controls.click(event);
        assertTrue(tasks.isEmpty());
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        controls.quit(quit);
        controls.click(event);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void commandSortsPlayerStorageWhenNoContainerIsOpen() {
        assertTrue(controls.onCommand(player, mock(Command.class), "sort", new String[0]));
        verify(inventory).setItem(9, null);
        verify(inventory).setItem(35, null);
        verify(inventory, never()).setItem(org.mockito.ArgumentMatchers.eq(0), any());
    }

    @Test
    void commandReportsMissingContainerAndInvalidArguments() {
        assertTrue(controls.onCommand(player, mock(Command.class), "sort", new String[]{"container"}));
        verify(player).sendMessage("Open a chest, barrel, shulker box or ender chest first.");
        assertFalse(controls.onCommand(player, mock(Command.class), "sort", new String[]{"invalid"}));
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void checksCursorAgainBeforeScheduledSort() {
        controls.click(event);
        controls.click(event);
        ItemStack cursor = mock(ItemStack.class);
        when(cursor.getType()).thenReturn(Material.STONE);
        when(cursor.getAmount()).thenReturn(1);
        when(view.getCursor()).thenReturn(cursor);
        tasks.getFirst().run();
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void permissionLossBeforeScheduledSortPreventsWriting() {
        controls.click(event);
        controls.click(event);
        when(player.hasPermission("inventorysorter.use")).thenReturn(false);
        tasks.getFirst().run();
        verify(inventory, never()).setItem(anyInt(), any());
    }
}
