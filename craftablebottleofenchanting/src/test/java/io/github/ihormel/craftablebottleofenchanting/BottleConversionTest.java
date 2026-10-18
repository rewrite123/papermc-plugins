package io.github.ihormel.craftablebottleofenchanting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class BottleConversionTest extends PaperTest {
    @Test
    void consumesExactlyElevenRawXpAndOneBottleWithNoLapis() {
        Player player = mock(Player.class);
        PlayerInventory inventory = server.addPlayer().getInventory();
        inventory.setItem(0, new ItemStack(Material.GLASS_BOTTLE, 3));
        when(player.getInventory()).thenReturn(inventory);
        when(player.calculateTotalExperiencePoints()).thenReturn(100);

        assertTrue(BottleConversion.convert(player, 0));

        verify(player).setExperienceLevelAndProgress(89);
        assertEquals(2, inventory.getItem(0).getAmount());
        assertEquals(Material.EXPERIENCE_BOTTLE, inventory.getItem(1).getType());
        assertEquals(1, inventory.getItem(1).getAmount());
    }

    @Test
    void allowsExactlyElevenXpAndReusesLastBottleSlotInFullInventory() {
        Player player = mock(Player.class);
        PlayerInventory inventory = server.addPlayer().getInventory();
        inventory.setStorageContents(fullInventory(1));
        when(player.getInventory()).thenReturn(inventory);
        when(player.calculateTotalExperiencePoints()).thenReturn(11);

        assertTrue(BottleConversion.convert(player, 0));

        verify(player).setExperienceLevelAndProgress(0);
        assertEquals(Material.EXPERIENCE_BOTTLE, inventory.getItem(0).getType());
    }

    @Test
    void insufficientXpChangesNothing() {
        Player player = mock(Player.class);
        PlayerInventory inventory = server.addPlayer().getInventory();
        inventory.setItem(0, new ItemStack(Material.GLASS_BOTTLE, 3));
        when(player.getInventory()).thenReturn(inventory);
        when(player.calculateTotalExperiencePoints()).thenReturn(10);
        assertFalse(BottleConversion.convert(player, 0));
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
        assertEquals(3, inventory.getItem(0).getAmount());
    }

    @Test
    void fullInventoryWithMultipleInputBottlesChangesNothing() {
        Player player = mock(Player.class);
        PlayerInventory inventory = server.addPlayer().getInventory();
        inventory.setStorageContents(fullInventory(2));
        when(player.getInventory()).thenReturn(inventory);
        when(player.calculateTotalExperiencePoints()).thenReturn(100);
        assertFalse(BottleConversion.convert(player, 0));
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
        assertEquals(2, inventory.getItem(0).getAmount());
    }

    @Test
    void missingInputDoesNotConsumeExperience() {
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(server.addPlayer().getInventory());
        assertFalse(BottleConversion.convert(player, 0));
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
    }

    @Test
    void mergesOutputButDoesNotChangeOriginalPlanOrUnrelatedItems() {
        ItemStack[] input = fullInventory(3);
        input[1] = new ItemStack(Material.EXPERIENCE_BOTTLE, 63);
        input[2] = new ItemStack(Material.LAPIS_LAZULI, 17);
        var meta = input[3].getItemMeta();
        meta.displayName(Component.text("Keep me"));
        input[3].setItemMeta(meta);

        ItemStack[] result = BottleConversion.plan(input, 0);

        assertNotNull(result);
        assertEquals(2, result[0].getAmount());
        assertEquals(64, result[1].getAmount());
        assertEquals(63, input[1].getAmount());
        assertEquals(3, input[0].getAmount());
        assertEquals(input[2], result[2]);
        assertEquals(input[3], result[3]);
    }

    @Test
    void neverMergesWithFullOrCustomNamedBottles() {
        ItemStack[] input = fullInventory(2);
        input[1] = new ItemStack(Material.EXPERIENCE_BOTTLE, 64);
        input[2] = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
        var meta = input[2].getItemMeta();
        meta.displayName(Component.text("Special"));
        input[2].setItemMeta(meta);
        assertNull(BottleConversion.plan(input, 0));
    }

    @Test
    void repeatedConversionsDoNotCreateExtraBottles() {
        ItemStack[] storage = new ItemStack[36];
        storage[0] = new ItemStack(Material.GLASS_BOTTLE, 10);
        for (int count = 0; count < 10; count++) {
            storage = BottleConversion.plan(storage, 0);
            assertNotNull(storage);
        }
        assertEquals(10, Arrays.stream(storage).filter(item -> item != null
                && item.getType() == Material.EXPERIENCE_BOTTLE).mapToInt(ItemStack::getAmount).sum());
        assertTrue(Arrays.stream(storage).noneMatch(item -> item != null
                && item.getType() == Material.GLASS_BOTTLE));
    }

    private ItemStack[] fullInventory(int bottles) {
        ItemStack[] storage = new ItemStack[36];
        Arrays.setAll(storage, slot -> new ItemStack(Material.STONE, 64));
        storage[0] = new ItemStack(Material.GLASS_BOTTLE, bottles);
        return storage;
    }
}
