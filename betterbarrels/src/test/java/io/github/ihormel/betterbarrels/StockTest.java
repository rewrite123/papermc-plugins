package io.github.ihormel.betterbarrels;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class StockTest extends PaperTest {
    @Test
    void acceptsExactly2048AndNeverOverflowsOrConsumesOfferedItems() {
        Stock stock = new Stock(null, 0);
        ItemStack stack = new ItemStack(Material.DIAMOND, 64);
        for (int i = 0; i < 32; i++) assertEquals(64, stock.insert(stack, 64));
        assertEquals(2048, stock.count());
        assertEquals(0, stock.insert(stack, 1));
        assertEquals(64, stack.getAmount());
    }

    @Test
    void singleInsertAndPartialStackAtCapacity() {
        Stock stock = new Stock(null, 0);
        ItemStack stack = new ItemStack(Material.COBBLESTONE, 64);
        assertEquals(1, stock.insert(stack, 1));
        stock = new Stock(stack, 2040);
        assertEquals(8, stock.insert(stack, 64));
        assertEquals(2048, stock.count());
    }

    @Test
    void rejectsDifferentMaterialsAndMetadata() {
        Stock stock = new Stock(new ItemStack(Material.DIAMOND), 1);
        assertEquals(0, stock.insert(new ItemStack(Material.DIRT), 1));
        ItemStack named = new ItemStack(Material.DIAMOND);
        named.editMeta(meta -> meta.displayName(Component.text("Special")));
        assertEquals(0, stock.insert(named, 1));
    }

    @Test
    void singleAndFullStackRetrievalKeepExactCounts() {
        Stock stock = new Stock(new ItemStack(Material.DIAMOND), 100);
        assertEquals(1, stock.take(1).getAmount());
        assertEquals(99, stock.count());
        assertEquals(64, stock.take(64).getAmount());
        assertEquals(35, stock.take(64).getAmount());
        assertEquals(0, stock.count());
        assertNull(stock.type());
        assertNull(stock.take(1));
        assertEquals(1, stock.insert(new ItemStack(Material.DIRT), 1));
    }

    @Test
    void respectsSixteenAndOneItemStackLimits() {
        Stock pearls = new Stock(new ItemStack(Material.ENDER_PEARL), 2048);
        assertEquals(16, pearls.take(64).getAmount());
        Stock swords = new Stock(new ItemStack(Material.DIAMOND_SWORD), 2048);
        assertEquals(1, swords.take(64).getAmount());
    }

    @Test
    void conversionStacksPreserveTotalAndMetadataWithoutMutatingStock() {
        ItemStack named = new ItemStack(Material.DIAMOND);
        named.editMeta(meta -> meta.lore(List.of(Component.text("Keep this"))));
        Stock stock = new Stock(named, 2048);
        var stacks = stock.stacks();
        assertEquals(32, stacks.size());
        assertEquals(2048, stacks.stream().mapToInt(ItemStack::getAmount).sum());
        assertTrue(stacks.stream().allMatch(named::isSimilar));
        assertEquals(2048, stock.count());
        stacks.getFirst().setAmount(1);
        assertEquals(1, stock.type().getAmount());
    }

    @Test
    void rejectsInvalidPersistedState() {
        assertThrows(IllegalArgumentException.class, () -> new Stock(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new Stock(new ItemStack(Material.AIR), 1));
        assertThrows(IllegalArgumentException.class, () -> new Stock(new ItemStack(Material.DIRT), 2049));
        assertThrows(IllegalArgumentException.class, () -> new Stock(null, -1));
    }

    @Test
    void emptyAndInvalidTransferRequestsAreNoOps() {
        Stock stock = new Stock(null, 0);
        assertTrue(stock.stacks().isEmpty());
        assertEquals(0, stock.insert(null, 1));
        assertEquals(0, stock.insert(new ItemStack(Material.AIR), 1));
        assertEquals(0, stock.insert(new ItemStack(Material.DIRT), 0));
        assertNull(stock.take(-1));
    }
}
