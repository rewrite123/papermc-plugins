package io.github.ihormel.betterbarrels;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListenerTest extends PaperTest {
    @TempDir Path temp;
    private BarrelListener listener;
    private BarrelStorage storage;
    private ScanState state;
    private Barrel barrel;
    private Player player;
    private WorldScan scan;

    @BeforeEach
    void setupListener() throws IOException {
        var world = server.addSimpleWorld("interactions");
        world.getBlockAt(0, 65, 0).setType(Material.BARREL);
        barrel = (Barrel) world.getBlockAt(0, 65, 0).getState();
        TestCodec codec = new TestCodec();
        storage = new BarrelStorage(codec::decode, codec::encode);
        Conversion conversion = new Conversion(storage, temp.resolve("backups"), codec::encode);
        state = new ScanState(temp.resolve("progress"));
        JavaPlugin plugin = mock(JavaPlugin.class);
        scan = mock(WorldScan.class);
        listener = new BarrelListener(plugin, storage, conversion, state, scan);
        player = server.addPlayer();
    }

    private PlayerInteractEvent click(EquipmentSlot hand) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(barrel.getBlock());
        when(event.getHand()).thenReturn(hand);
        when(event.getPlayer()).thenReturn(player);
        when(event.useInteractedBlock()).thenReturn(Event.Result.DEFAULT);
        return event;
    }

    private Stock saved() {
        return storage.read((Barrel) barrel.getBlock().getState());
    }

    @Test
    void storesOneNormallyAndFullHeldStackWhenSneaking() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 64));
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(1, saved().count());
        assertEquals(63, player.getInventory().getItemInMainHand().getAmount());
        player.setSneaking(true);
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(64, saved().count());
        assertTrue(player.getInventory().getItemInMainHand().isEmpty());
    }

    @Test
    void emptyHandRetrievesOneOrActualMaximumStack() {
        storage.write(barrel, new Stock(new ItemStack(Material.ENDER_PEARL), 100));
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(99, saved().count());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        player.getInventory().clear();
        player.setSneaking(true);
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(83, saved().count());
        assertEquals(16, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void mainAndOffhandEventsDoNotDoubleTransfer() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 10));
        listener.interact(click(EquipmentSlot.HAND));
        listener.interact(click(EquipmentSlot.OFF_HAND));
        assertEquals(1, saved().count());
        assertEquals(9, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void protectionDeniedClickCannotTransferOrConvert() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 10));
        PlayerInteractEvent event = click(EquipmentSlot.HAND);
        when(event.useInteractedBlock()).thenReturn(Event.Result.DENY);
        listener.interact(event);
        assertEquals(0, saved().count());
        assertEquals(10, player.getInventory().getItemInMainHand().getAmount());
        verify(event).setCancelled(true);
    }

    @Test
    void lockedBarrelAndSpectatorCannotUseStorage() {
        barrel.setLock("key");
        barrel.update();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 10));
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(0, saved().count());
        barrel = (Barrel) barrel.getBlock().getState();
        barrel.setLock("");
        barrel.update();
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(0, saved().count());
    }

    @Test
    void fullAndWrongTypeBarrelsDoNotConsumeHeldItems() {
        storage.write(barrel, new Stock(new ItemStack(Material.DIAMOND), 2048));
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 64));
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(64, player.getInventory().getItemInMainHand().getAmount());
        player.getInventory().setItemInMainHand(new ItemStack(Material.EMERALD, 30));
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(30, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(2048, saved().count());
    }

    @Test
    void roomCalculationAccountsForFullInventoryAndPartialMatchingStacks() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getMaxStackSize()).thenReturn(64);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{new ItemStack(Material.DIRT, 64)});
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        assertEquals(0, BarrelListener.room(inventory, diamond, 64));
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{new ItemStack(Material.DIAMOND, 60)});
        assertEquals(4, BarrelListener.room(inventory, diamond, 64));
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{null});
        assertEquals(16, BarrelListener.room(inventory, new ItemStack(Material.ENDER_PEARL), 64));
    }

    @Test
    void preventsUiAndHopperAccessButLeavesChestAlone() {
        InventoryOpenEvent open = mock(InventoryOpenEvent.class);
        when(open.getInventory()).thenReturn(barrel.getInventory());
        listener.open(open);
        verify(open).setCancelled(true);
        InventoryMoveItemEvent move = mock(InventoryMoveItemEvent.class);
        when(move.getSource()).thenReturn(barrel.getInventory());
        when(move.getDestination()).thenReturn(player.getInventory());
        listener.move(move);
        verify(move).setCancelled(true);
        InventoryOpenEvent other = mock(InventoryOpenEvent.class);
        when(other.getInventory()).thenReturn(player.getInventory());
        listener.open(other);
        verify(other, never()).setCancelled(anyBoolean());
    }

    @Test
    void nonemptyBreakDeniedButEmptyBarrelCanBeBroken() {
        BlockBreakEvent empty = new BlockBreakEvent(barrel.getBlock(), player);
        listener.breakBlock(empty);
        assertFalse(empty.isCancelled());
        storage.write(barrel, new Stock(new ItemStack(Material.DIAMOND), 1));
        BlockBreakEvent full = new BlockBreakEvent(barrel.getBlock(), player);
        listener.breakBlock(full);
        assertTrue(full.isCancelled());
        assertEquals(1, saved().count());
    }

    @Test
    void explosionsAndFireCannotDestroyBarrelStorage() {
        var blocks = new ArrayList<>(List.of(barrel.getBlock()));
        EntityExplodeEvent entity = mock(EntityExplodeEvent.class);
        when(entity.blockList()).thenReturn(blocks);
        listener.explosion(entity);
        assertTrue(blocks.isEmpty());
        blocks.add(barrel.getBlock());
        BlockExplodeEvent block = mock(BlockExplodeEvent.class);
        when(block.blockList()).thenReturn(blocks);
        listener.explosion(block);
        assertTrue(blocks.isEmpty());
        BlockBurnEvent burn = new BlockBurnEvent(barrel.getBlock(), null);
        listener.burn(burn);
        assertTrue(burn.isCancelled());
    }

    @Test
    void uninstallModeConvertsOnAccessInsteadOfConsumingItems() throws IOException {
        state.reset(true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 64));
        listener.interact(click(EquipmentSlot.HAND));
        assertEquals(Material.CHEST, barrel.getBlock().getType());
        assertEquals(64, player.getInventory().getItemInMainHand().getAmount());
    }
}
