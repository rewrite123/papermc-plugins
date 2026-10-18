package io.github.ihormel.craftablebottleofenchanting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BottleListenerTest extends PaperTest {
    private final List<Runnable> tasks = new ArrayList<>();
    private BottleListener listener;
    private Player player;
    private PlayerInventory inventory;
    private BottleMenu menu;
    private InventoryView view;
    private World world;

    @BeforeEach
    void setUpListener() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server api = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(api);
        when(api.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(JavaPlugin.class), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return null;
        });
        listener = new BottleListener(plugin);
        player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission("craftablebottleofenchanting.use")).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        world = server.addSimpleWorld("world");
        world.getBlockAt(0, 64, 0).setType(Material.ENCHANTING_TABLE);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.AIR));
        inventory = server.addPlayer().getInventory();
        inventory.setItem(0, new ItemStack(Material.GLASS_BOTTLE, 2));
        when(player.getInventory()).thenReturn(inventory);
        when(player.calculateTotalExperiencePoints()).thenReturn(11);
        menu = new BottleMenu(id, new Location(world, 0, 64, 0), 0);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(menu.getInventory());
        when(player.getOpenInventory()).thenReturn(view);
    }

    @Test
    void hasExactlyOneNamedAction() {
        assertEquals(9, menu.getInventory().getSize());
        assertEquals(Material.EXPERIENCE_BOTTLE, menu.getInventory().getItem(4).getType());
        assertEquals(net.kyori.adventure.text.Component.text("Bottle Experience"),
                menu.getInventory().getItem(4).getItemMeta().displayName());
        for (int slot = 0; slot < 9; slot++) {
            if (slot != 4) assertNull(menu.getInventory().getItem(slot));
        }
    }

    @Test
    void actionChargesAfterDispatchAndLeavesDisplayItemInPlace() {
        var event = click(4, ClickType.LEFT);
        listener.click(event);
        verify(event).setCancelled(true);
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
        tasks.getFirst().run();
        verify(player).setExperienceLevelAndProgress(0);
        assertEquals(Material.EXPERIENCE_BOTTLE, menu.getInventory().getItem(4).getType());
        assertEquals(1, inventory.getItem(0).getAmount());
    }

    @Test
    void blocksShiftClicksNumberKeysDoubleClicksAndBottomInventoryMoves() {
        for (ClickType type : new ClickType[]{ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.SWAP_OFFHAND}) {
            var event = click(4, type);
            listener.click(event);
            verify(event).setCancelled(true);
        }
        var bottom = click(10, ClickType.LEFT);
        listener.click(bottom);
        verify(bottom).setCancelled(true);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void respectsCancelledActionAndCancelsDrags() {
        var event = click(4, ClickType.LEFT);
        when(event.isCancelled()).thenReturn(true);
        listener.click(event);
        assertTrue(tasks.isEmpty());
        var drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        listener.drag(drag);
        verify(drag).setCancelled(true);
    }

    @Test
    void closedMenuDoesNotCharge() {
        listener.click(click(4, ClickType.LEFT));
        when(player.getOpenInventory()).thenReturn(mock(InventoryView.class));
        tasks.getFirst().run();
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
    }

    @Test
    void brokenTableOrDistancePreventsConversion() {
        listener.click(click(4, ClickType.LEFT));
        world.getBlockAt(0, 64, 0).setType(Material.AIR);
        tasks.getFirst().run();
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
        verify(player).closeInventory();
    }

    @Test
    void distantPlayerCannotUseMenu() {
        listener.click(click(4, ClickType.LEFT));
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 0));
        tasks.getFirst().run();
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
    }

    @Test
    void permissionLossOrOccupiedCursorPreventsConversion() {
        listener.click(click(4, ClickType.LEFT));
        when(player.hasPermission("craftablebottleofenchanting.use")).thenReturn(false);
        tasks.getFirst().run();
        when(player.hasPermission("craftablebottleofenchanting.use")).thenReturn(true);
        listener.click(click(4, ClickType.LEFT));
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.STONE));
        tasks.getLast().run();
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
    }

    @Test
    void opensOnlyForMainHandBottlesAtEnchantingTable() {
        var event = interaction(Material.GLASS_BOTTLE);
        listener.interact(event);
        verify(event).setCancelled(true);
        verify(player).openInventory(any(org.bukkit.inventory.Inventory.class));
        var ordinary = interaction(Material.DIAMOND_PICKAXE);
        listener.interact(ordinary);
        verify(ordinary, never()).setCancelled(true);
        var offhand = interaction(Material.GLASS_BOTTLE);
        when(offhand.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        listener.interact(offhand);
        verify(offhand, never()).setCancelled(true);
        var denied = interaction(Material.GLASS_BOTTLE);
        when(denied.useInteractedBlock()).thenReturn(Event.Result.DENY);
        listener.interact(denied);
        verify(denied, never()).setCancelled(true);
    }

    @Test
    void dropsOneBottleAtImpactWithoutChangingXp() {
        World dropWorld = mock(World.class);
        ThrownExpBottle bottle = mock(ThrownExpBottle.class);
        Location impact = new Location(dropWorld, 10, 65, 20);
        when(bottle.getWorld()).thenReturn(dropWorld);
        when(bottle.getLocation()).thenReturn(impact);
        ExpBottleEvent event = mock(ExpBottleEvent.class);
        when(event.getEntity()).thenReturn(bottle);
        listener.breakBottle(event);
        verify(dropWorld).dropItemNaturally(impact, new ItemStack(Material.GLASS_BOTTLE));
        verify(event, never()).setExperience(anyInt());
        verify(event, never()).setShowEffect(anyBoolean());
    }

    @Test
    void cancelledImpactsDoNotDropEmptyBottles() {
        var plugin = MockBukkit.createMockPlugin();
        server.getPluginManager().registerEvents(listener, plugin);
        ThrownExpBottle bottle = mock(ThrownExpBottle.class);
        World dropWorld = mock(World.class);
        when(bottle.getWorld()).thenReturn(dropWorld);
        ExpBottleEvent event = new ExpBottleEvent(bottle, null, null, null, 7);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        verify(dropWorld, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        assertEquals(7, event.getExperience());
    }

    private InventoryClickEvent click(int rawSlot, ClickType type) {
        var event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(type);
        return event;
    }

    private PlayerInteractEvent interaction(Material item) {
        var event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        Block table = world.getBlockAt(0, 64, 0);
        when(event.getClickedBlock()).thenReturn(table);
        when(event.getItem()).thenReturn(new ItemStack(item));
        when(event.getPlayer()).thenReturn(player);
        return event;
    }
}
