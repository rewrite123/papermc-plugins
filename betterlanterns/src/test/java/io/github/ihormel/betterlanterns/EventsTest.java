package io.github.ihormel.betterlanterns;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Candle;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class EventsTest {
    private ServerMock server;
    private LanternEngine engine;
    private SoulItems items;
    private LanternEvents listener;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;
    private Block block;
    private Candle data;
    private final List<Runnable> tasks = new ArrayList<>();

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        engine = mock(LanternEngine.class);
        items = new SoulItems(new NamespacedKey("betterlanterns", "soul_candle"));
        listener = new LanternEvents(engine, items, tasks::add);
        server.getPluginManager().registerEvents(listener, plugin);
        block = mock(Block.class);
        var world = server.addSimpleWorld("candle-world");
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(8);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(8);
        when(block.getLocation()).thenReturn(new org.bukkit.Location(world, 8, 64, 8));
        when(block.getType()).thenReturn(Material.CANDLE);
        data = mock(Candle.class);
        when(data.getCandles()).thenReturn(1);
        when(block.getBlockData()).thenReturn(data);
        when(engine.isSoul(Position.of(block))).thenReturn(true);
        when(engine.claimCandleDrops(any(), anyInt())).thenReturn(true);
    }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    private PlayerInteractEvent interact(ItemStack item) {
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, block, BlockFace.UP,
                EquipmentSlot.HAND);
        event.setUseInteractedBlock(Event.Result.ALLOW);
        event.setUseItemInHand(Event.Result.ALLOW);
        return event;
    }

    @Test
    void soulCandleIgnitesWithFireChargeAndExtinguishesWithEmptyHand() {
        var charge = new ItemStack(Material.FIRE_CHARGE, 2);
        var ignite = interact(charge);
        listener.interact(ignite);
        assertTrue(ignite.isCancelled());
        verify(engine).candle(Position.of(block), true);
        assertEquals(1, charge.getAmount());
        when(engine.isLit(Position.of(block))).thenReturn(true);
        var duplicate = interact(null);
        listener.interact(duplicate);
        verify(engine, never()).candle(Position.of(block), false);
        new ArrayList<>(tasks).forEach(Runnable::run);
        var extinguish = interact(null);
        listener.interact(extinguish);
        assertTrue(extinguish.isCancelled());
        verify(engine).candle(Position.of(block), false);
    }

    @Test
    void deniedInteractionsWaterloggingAndCreativeCostsAreRespected() {
        var charge = new ItemStack(Material.FIRE_CHARGE, 2);
        var denied = interact(charge);
        denied.setUseInteractedBlock(Event.Result.DENY);
        listener.interact(denied);
        verify(engine, never()).candle(any(), anyBoolean());
        when(data.isWaterlogged()).thenReturn(true);
        listener.interact(interact(charge));
        verify(engine, never()).candle(any(), anyBoolean());
        when(data.isWaterlogged()).thenReturn(false);
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        listener.interact(interact(charge));
        assertEquals(2, charge.getAmount());
        verify(engine).candle(Position.of(block), true);
    }

    @Test
    void normalAndSoulCandlesCannotMixAndSoulCandlesCannotGoOnCake() {
        var mixed = interact(new ItemStack(Material.CANDLE));
        listener.interact(mixed);
        assertTrue(mixed.isCancelled());
        when(block.getType()).thenReturn(Material.CAKE);
        var cake = interact(items.candle(1));
        listener.interact(cake);
        assertTrue(cake.isCancelled());
        verify(engine, never()).candle(any(), anyBoolean());
    }

    @Test
    void cancelledPlacementDoesNotRecordSoulCandleAndSuccessfulPlacementDoes() {
        var replaced = mock(BlockState.class);
        when(replaced.getType()).thenReturn(Material.AIR);
        var cancelled = new BlockPlaceEvent(block, replaced, block, items.candle(1), player, true, EquipmentSlot.HAND);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        verify(engine, never()).candle(any(), anyBoolean());
        var placed = new BlockPlaceEvent(block, replaced, block, items.candle(1), player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(placed);
        verify(engine).candle(Position.of(block), false);
    }

    @Test
    void spawnEventIsCancelledOnlyWhenEngineConfirmsProtection() {
        var entity = mock(org.bukkit.entity.LivingEntity.class);
        var event = new org.bukkit.event.entity.CreatureSpawnEvent(entity,
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
        when(engine.protects(entity, event.getSpawnReason())).thenReturn(true);
        listener.spawn(event);
        assertTrue(event.isCancelled());
        var allowed = new org.bukkit.event.entity.CreatureSpawnEvent(entity,
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER);
        listener.spawn(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void cancelledBreakDoesNotQueueChangesAndRealBreaksAreCoalesced() {
        var event = new org.bukkit.event.block.BlockBreakEvent(block, player);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertTrue(tasks.isEmpty());
        event.setCancelled(false);
        server.getPluginManager().callEvent(event);
        server.getPluginManager().callEvent(event);
        assertEquals(1, tasks.size());
    }

    @Test
    void breakDropEventPreservesCustomIdentityBeforeItemSpawn() {
        var state = mock(BlockState.class);
        var item = mock(Item.class);
        when(item.getItemStack()).thenReturn(new ItemStack(Material.CANDLE, 2));
        listener.brokenDrops(new org.bukkit.event.block.BlockDropItemEvent(block, state, player, List.of(item)));
        var result = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        verify(item).setItemStack(result.capture());
        assertTrue(items.isSoul(result.getValue()));
        assertEquals(2, result.getValue().getAmount());
    }

    @Test
    void destroyedSoulCandlesDropCustomItemsButThrowingPlainCandlesIntoExistingOnesDoesNotConvertThem() {
        var location = mock(org.bukkit.Location.class);
        when(location.getBlock()).thenReturn(block);
        var entity = mock(Item.class);
        when(entity.getLocation()).thenReturn(location);
        when(entity.getItemStack()).thenReturn(new ItemStack(Material.CANDLE, 3));
        listener.dropped(new ItemSpawnEvent(entity, location));
        verify(entity, never()).setItemStack(any());
        when(block.getType()).thenReturn(Material.AIR);
        listener.dropped(new ItemSpawnEvent(entity, location));
        var result = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        verify(entity).setItemStack(result.capture());
        assertTrue(items.isSoul(result.getValue()));
        assertEquals(3, result.getValue().getAmount());
    }
}
