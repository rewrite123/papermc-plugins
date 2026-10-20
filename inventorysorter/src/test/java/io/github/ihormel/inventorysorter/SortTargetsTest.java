package io.github.ihormel.inventorysorter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SortTargetsTest extends PaperTest {
    private final Player player = mock(Player.class);
    private final PlayerInventory storage = mock(PlayerInventory.class);
    private final InventoryView view = mock(InventoryView.class);
    private final Inventory top = mock(Inventory.class);

    @BeforeEach
    void setUp() {
        when(player.getInventory()).thenReturn(storage);
        when(view.getBottomInventory()).thenReturn(storage);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getType()).thenReturn(InventoryType.CRAFTING);
        when(view.getType()).thenReturn(InventoryType.CRAFTING);
    }

    @Test
    void playerTargetsExcludeHotbarArmorAndOffhand() {
        assertEquals(new SortTargets.Target(storage, 9, 36), SortTargets.clicked(player, view, storage, 9));
        assertEquals(new SortTargets.Target(storage, 9, 36), SortTargets.clicked(player, view, storage, 35));
        for (int slot : new int[]{-1, 0, 8, 36, 39, 40}) {
            assertNull(SortTargets.clicked(player, view, storage, slot));
        }
        assertNull(SortTargets.clicked(player, view, null, -999));
        assertNull(SortTargets.clicked(player, view, top, 0));
    }

    @Test
    void supportsPhysicalStorageContainersIncludingDoubleChests() {
        checkContainer(InventoryType.CHEST, mock(Chest.class), 27);
        checkContainer(InventoryType.CHEST, mock(DoubleChest.class), 54);
        checkContainer(InventoryType.BARREL, mock(Barrel.class), 27);
        checkContainer(InventoryType.SHULKER_BOX, mock(ShulkerBox.class), 27);
    }

    @Test
    void supportsOnlyPlayersOwnEnderChest() {
        when(player.getEnderChest()).thenReturn(top);
        when(top.getType()).thenReturn(InventoryType.ENDER_CHEST);
        when(top.getSize()).thenReturn(27);
        assertEquals(new SortTargets.Target(top, 0, 27), SortTargets.container(player, view));
    }

    @Test
    void processingScreensAndCustomMenusSortOnlyPlayerStorage() {
        for (InventoryType type : new InventoryType[]{InventoryType.CHEST, InventoryType.FURNACE,
                InventoryType.BLAST_FURNACE, InventoryType.SMOKER, InventoryType.ANVIL,
                InventoryType.MERCHANT, InventoryType.WORKBENCH, InventoryType.ENCHANTING,
                InventoryType.BREWING, InventoryType.HOPPER, InventoryType.DISPENSER}) {
            when(view.getType()).thenReturn(type);
            when(top.getType()).thenReturn(type);
            when(top.getSize()).thenReturn(9);
            assertTrue(SortTargets.supportedView(player, view));
            assertNull(SortTargets.container(player, view));
            assertNull(SortTargets.clicked(player, view, top, 0));
            assertEquals(SortTargets.player(player), SortTargets.clicked(player, view, storage, 9));
            assertNull(SortTargets.clicked(player, view, storage, 0));
        }
    }

    @Test
    void refusesViewsThatDoNotShowThePlayersOwnInventory() {
        when(view.getBottomInventory()).thenReturn(mock(PlayerInventory.class));
        assertFalse(SortTargets.supportedView(player, view));
        assertNull(SortTargets.clicked(player, view, storage, 9));
    }

    private void checkContainer(InventoryType type, InventoryHolder holder, int size) {
        when(view.getType()).thenReturn(type);
        when(top.getType()).thenReturn(type);
        when(top.getHolder(false)).thenReturn(holder);
        when(top.getSize()).thenReturn(size);
        assertTrue(SortTargets.supportedView(player, view));
        assertEquals(new SortTargets.Target(top, 0, size), SortTargets.clicked(player, view, top, size - 1));
        assertNull(SortTargets.clicked(player, view, top, size));
        assertEquals(SortTargets.player(player), SortTargets.clicked(player, view, storage, 9));
    }
}
