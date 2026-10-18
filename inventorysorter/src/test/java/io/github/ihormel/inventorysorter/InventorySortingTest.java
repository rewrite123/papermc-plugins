package io.github.ihormel.inventorysorter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.Damageable;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InventorySortingTest extends PaperTest {
    @ParameterizedTest
    @CsvSource({
            "DIAMOND_HELMET, Armor", "POTION, Brewing", "STONE, Building Blocks",
            "PAINTING, Decoration", "ENCHANTED_BOOK, Enchanting", "CARROT, Food",
            "ROTTEN_FLESH, Food", "DIAMOND_ORE, Minerals", "IRON_INGOT, Minerals",
            "STICK, Miscellaneous", "BONE, Mob Drops", "STRING, Mob Drops",
            "OAK_SAPLING, Plants", "POPPY, Plants", "HOPPER, Redstone",
            "OAK_BUTTON, Redstone", "ZOMBIE_SPAWN_EGG, Spawn Eggs",
            "SHULKER_BOX, Storage", "CHEST, Storage", "DIAMOND_AXE, Tools",
            "IRON_PICKAXE, Tools", "SADDLE, Transport", "MINECART, Transport",
            "DIAMOND_SWORD, Weapons"
    })
    void classifiesRelatedItems(Material material, String group) {
        assertEquals(group, ItemGroups.group(material));
    }

    @Test
    void sortsGroupsThenMaterialsAndPreservesStacksAndStableTies() {
        ItemStack pickaxe = item(Material.IRON_PICKAXE, 1);
        ItemStack axe = item(Material.DIAMOND_AXE, 1);
        ItemStack carrots = item(Material.CARROT, 17);
        ItemStack moreCarrots = item(Material.CARROT, 32);
        ItemStack bread = item(Material.BREAD, 4);
        ItemStack bone = item(Material.BONE, 9);
        ItemStack flower = item(Material.POPPY, 5);
        ItemStack[] input = {pickaxe, null, carrots, flower, axe, moreCarrots, bone, bread};
        ItemStack[] original = input.clone();

        assertArrayEquals(new ItemStack[]{bread, carrots, moreCarrots, bone, flower, axe, pickaxe, null},
                InventorySorting.sorted(input));
        assertArrayEquals(original, input);
        for (ItemStack stack : input) {
            if (stack != null) {
                verify(stack, never()).setAmount(anyInt());
                verify(stack, never()).setItemMeta(any());
            }
        }
    }

    @Test
    void discardsAirAndZeroCountAsEmptyWithoutChangingCapacity() {
        ItemStack stone = item(Material.STONE, 64);
        assertArrayEquals(new ItemStack[]{stone, null, null, null},
                InventorySorting.sorted(new ItemStack[]{null, item(Material.AIR, 1),
                        item(Material.DIRT, 0), stone}));
        assertEquals(0, InventorySorting.sorted(new ItemStack[0]).length);
    }

    @Test
    void writesOnlyRequestedPlayerStorageRange() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(41);
        ItemStack axe = item(Material.DIAMOND_AXE, 1);
        ItemStack bread = item(Material.BREAD, 8);
        when(inventory.getItem(9)).thenReturn(axe);
        when(inventory.getItem(35)).thenReturn(bread);
        InventorySorting.sort(inventory, 9, 36);
        verify(inventory).setItem(9, bread);
        verify(inventory).setItem(10, axe);
        for (int slot = 0; slot < 41; slot++) {
            if (slot < 9 || slot >= 36) {
                verify(inventory, never()).setItem(eq(slot), any());
            }
        }
    }

    @Test
    void rejectsInvalidRangesBeforeWriting() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(27);
        assertThrows(IllegalArgumentException.class, () -> InventorySorting.sort(inventory, -1, 27));
        assertThrows(IllegalArgumentException.class, () -> InventorySorting.sort(inventory, 0, 28));
        assertThrows(IllegalArgumentException.class, () -> InventorySorting.sort(inventory, 9, 9));
    }

    @Test
    void preservesRealStackMetadataAndCountsInAContainer() {
        Inventory inventory = MockBukkit.getMock().createInventory(null, 27);
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        var meta = tool.getItemMeta();
        meta.displayName(Component.text("Favorite pickaxe"));
        ((Damageable) meta).setDamage(37);
        tool.setItemMeta(meta);
        ItemStack carrots = new ItemStack(Material.CARROT, 37);
        ItemStack otherCarrots = new ItemStack(Material.CARROT, 12);
        inventory.setItem(0, tool);
        inventory.setItem(17, carrots);
        inventory.setItem(26, otherCarrots);
        ItemStack originalTool = tool.clone();

        InventorySorting.sort(inventory, 0, 27);

        assertEquals(carrots, inventory.getItem(0));
        assertEquals(otherCarrots, inventory.getItem(1));
        assertEquals(originalTool, inventory.getItem(2));
        assertNotNull(inventory.getItem(2).getItemMeta().displayName());
        assertEquals(3, java.util.Arrays.stream(inventory.getContents())
                .filter(stack -> !InventorySorting.empty(stack)).count());
    }

    private ItemStack item(Material material, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        return item;
    }
}
