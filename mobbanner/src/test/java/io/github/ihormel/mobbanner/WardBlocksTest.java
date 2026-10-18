package io.github.ihormel.mobbanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class WardBlocksTest extends PaperTest {
    private WardBlocks blocks() {
        return new WardBlocks(items, Logger.getLogger("mobbanner-test"));
    }

    @Test
    void placementPersistsWardAndOrdinaryPlacementDoesNotBecomeAWard() {
        Block block = server.addSimpleWorld("world").getBlockAt(0, 64, 0);
        block.setType(Material.BROWN_BANNER);
        BlockPlaceEvent event = placement(block, items.create(EntityType.BAT));
        blocks().onPlace(event);
        assertEquals(EntityType.BAT, items.read((Banner) block.getState()));

        Block other = block.getRelative(BlockFace.EAST);
        other.setType(Material.WHITE_BANNER);
        blocks().onPlace(placement(other, new ItemStack(Material.WHITE_BANNER)));
        assertNull(items.read((Banner) other.getState()));
    }

    @Test
    void cancelledOrDeniedPlacementDoesNotPersistWard() {
        var listener = blocks();
        server.getPluginManager().registerEvents(listener, MockBukkit.createMockPlugin());
        Block block = server.addSimpleWorld("world").getBlockAt(0, 64, 0);
        block.setType(Material.BROWN_BANNER);
        BlockPlaceEvent cancelled = placement(block, items.create(EntityType.BAT));
        when(cancelled.isCancelled()).thenReturn(true);
        server.getPluginManager().callEvent(cancelled);
        assertNull(items.read((Banner) block.getState()));
        BlockPlaceEvent denied = placement(block, items.create(EntityType.BAT));
        when(denied.canBuild()).thenReturn(false);
        listener.onPlace(denied);
        assertNull(items.read((Banner) block.getState()));
    }

    @Test
    void minedBannerDropRetainsIdentityAndCanBePlacedAgain() {
        Block block = ward(EntityType.BAT);
        var state = block.getState();
        BlockDropItemEvent event = mock(BlockDropItemEvent.class);
        when(event.getBlockState()).thenReturn(state);
        Item drop = mock(Item.class);
        when(drop.getItemStack()).thenReturn(new ItemStack(Material.BROWN_BANNER));
        when(event.getItems()).thenReturn(List.of(drop));
        blocks().onDrop(event);
        verify(drop).setItemStack(items.create(EntityType.BAT));
        block.setType(Material.AIR);
        Block replacement = block.getRelative(BlockFace.EAST);
        replacement.setType(Material.BROWN_BANNER);
        blocks().onPlace(placement(replacement, items.create(EntityType.BAT)));
        assertEquals(EntityType.BAT, items.read((Banner) replacement.getState()));
    }

    @Test
    void explosionsRemoveWardAndPreserveItsDropWithoutDuplicatingVanillaLoot() {
        Block block = ward(EntityType.BAT);
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        List<Block> affected = new ArrayList<>(List.of(block));
        when(event.blockList()).thenReturn(affected);
        when(event.getYield()).thenReturn(1F);
        blocks().onEntityExplosion(event);
        assertTrue(affected.isEmpty());
        assertEquals(Material.AIR, block.getType());
        var dropped = block.getWorld().getEntities().stream().filter(Item.class::isInstance)
                .map(Item.class::cast).toList();
        assertEquals(1, dropped.size());
        assertEquals(EntityType.BAT, items.read(dropped.getFirst().getItemStack()));
    }

    @Test
    void zeroYieldExplosionDropsNothingButStillRemovesWard() {
        Block block = ward(EntityType.BAT);
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(new ArrayList<>(List.of(block)));
        when(event.getYield()).thenReturn(0F);
        blocks().onEntityExplosion(event);
        assertEquals(Material.AIR, block.getType());
        assertTrue(block.getWorld().getEntities().stream().noneMatch(Item.class::isInstance));
    }

    @Test
    void supportLossDropsMarkedWardAndDoesNotLeaveBlockBehind() {
        Block block = ward(EntityType.BAT);
        BlockPhysicsEvent event = mock(BlockPhysicsEvent.class);
        when(event.getBlock()).thenReturn(block);
        blocks().onPhysics(event);
        verify(event).setCancelled(true);
        assertEquals(Material.AIR, block.getType());
        var dropped = block.getWorld().getEntities().stream().filter(Item.class::isInstance)
                .map(Item.class::cast).toList();
        assertEquals(1, dropped.size());
        assertEquals(EntityType.BAT, items.read(dropped.getFirst().getItemStack()));
    }

    @Test
    void supportedBannerRemainsInPlace() {
        Block block = ward(EntityType.BAT);
        block.getRelative(BlockFace.DOWN).setType(Material.STONE);
        BlockPhysicsEvent event = mock(BlockPhysicsEvent.class);
        when(event.getBlock()).thenReturn(block);
        blocks().onPhysics(event);
        verify(event, never()).setCancelled(true);
        assertTrue(block.getState() instanceof Banner);
    }

    @Test
    void flowingWaterDropsMarkedWardBeforeReplacingBlock() {
        Block block = ward(EntityType.BAT);
        BlockFromToEvent event = mock(BlockFromToEvent.class);
        when(event.getToBlock()).thenReturn(block);
        blocks().onFlow(event);
        assertEquals(Material.AIR, block.getType());
        var dropped = block.getWorld().getEntities().stream().filter(Item.class::isInstance)
                .map(Item.class::cast).toList();
        assertEquals(1, dropped.size());
        assertEquals(EntityType.BAT, items.read(dropped.getFirst().getItemStack()));
    }

    @Test
    void wallBannerUsesItsAttachmentSideRatherThanBlockBelow() {
        var world = server.addSimpleWorld("wall");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.BROWN_WALL_BANNER);
        Banner banner = (Banner) block.getState();
        items.write(banner.getPersistentDataContainer(), EntityType.BAT);
        banner.update();
        assertEquals(Material.BROWN_WALL_BANNER, block.getType());
        BlockFace support = ((org.bukkit.block.data.Directional) block.getBlockData()).getFacing().getOppositeFace();
        assertNotEquals(BlockFace.DOWN, support);
        block.getRelative(support).setType(Material.STONE);
        BlockPhysicsEvent event = mock(BlockPhysicsEvent.class);
        when(event.getBlock()).thenReturn(block);
        blocks().onPhysics(event);
        verify(event, never()).setCancelled(true);
        block.getRelative(support).setType(Material.AIR);
        blocks().onPhysics(event);
        verify(event).setCancelled(true);
        assertEquals(Material.AIR, block.getType());
    }

    @Test
    void pistonCannotDirectlyDestroyWard() {
        Block block = ward(EntityType.BAT);
        BlockPistonExtendEvent event = mock(BlockPistonExtendEvent.class);
        when(event.getBlock()).thenReturn(block.getRelative(BlockFace.WEST));
        when(event.getDirection()).thenReturn(BlockFace.EAST);
        when(event.getBlocks()).thenReturn(List.of());
        blocks().onExtend(event);
        verify(event).setCancelled(true);
    }

    @Test
    void craftingCannotCloneWardButOrdinaryBannersRemainUsable() {
        var event = mock(PrepareItemCraftEvent.class);
        var inventory = mock(CraftingInventory.class);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getMatrix()).thenReturn(new ItemStack[]{items.create(EntityType.BAT), null});
        blocks().onCraft(event);
        verify(inventory).setResult(null);
        reset(inventory);
        when(inventory.getMatrix()).thenReturn(new ItemStack[]{new ItemStack(Material.WHITE_BANNER), null});
        blocks().onCraft(event);
        verify(inventory, never()).setResult(any());
    }

    private Block ward(EntityType type) {
        World world = server.addSimpleWorld("world");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.BROWN_BANNER);
        Banner banner = (Banner) block.getState();
        items.write(banner.getPersistentDataContainer(), type);
        banner.update();
        return block;
    }

    private BlockPlaceEvent placement(Block block, ItemStack item) {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.getItemInHand()).thenReturn(item);
        when(event.canBuild()).thenReturn(true);
        when(event.getHandlers()).thenReturn(BlockPlaceEvent.getHandlerList());
        return event;
    }
}
