package io.github.ihormel.craftablebottleofenchanting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BottleListenerTest extends PaperTest {
    private BottleListener listener;
    private Player player;
    private PlayerInventory inventory;
    private World world;

    @BeforeEach
    void setUpListener() {
        listener = new BottleListener();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("craftablebottleofenchanting.use")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isSneaking()).thenReturn(true);
        world = server.addSimpleWorld("world");
        world.getBlockAt(0, 64, 0).setType(Material.ENCHANTING_TABLE);
        inventory = server.addPlayer().getInventory();
        inventory.setHeldItemSlot(0);
        inventory.setItem(0, new ItemStack(Material.GLASS_BOTTLE, 2));
        when(player.getInventory()).thenReturn(inventory);
        when(player.calculateTotalExperiencePoints()).thenReturn(30);
    }

    @Test
    void shiftRightClickBottlesOneWithoutOpeningAnyMenu() {
        var event = interaction(Material.GLASS_BOTTLE);
        listener.interact(event);
        verify(event).setCancelled(true);
        verify(player).setExperienceLevelAndProgress(19);
        verify(player, never()).openInventory(any(org.bukkit.inventory.Inventory.class));
        assertEquals(1, inventory.getItem(0).getAmount());
        assertEquals(new ItemStack(Material.EXPERIENCE_BOTTLE), inventory.getItem(1));

        when(player.calculateTotalExperiencePoints()).thenReturn(19);
        listener.interact(interaction(Material.GLASS_BOTTLE));
        verify(player).setExperienceLevelAndProgress(8);
        assertNull(inventory.getItem(0));
        assertEquals(2, inventory.getItem(1).getAmount());
    }

    @Test
    void ordinaryRightClickLeavesTheVanillaEnchantingTableAlone() {
        when(player.isSneaking()).thenReturn(false);
        var event = interaction(Material.GLASS_BOTTLE);
        listener.interact(event);
        verify(event, never()).setCancelled(true);
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
        assertEquals(2, inventory.getItem(0).getAmount());
    }

    @Test
    void onlyMainHandBottlesOnEnchantingTablesWithBlockUseAllowed() {
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
        var otherBlock = interaction(Material.GLASS_BOTTLE);
        world.getBlockAt(5, 64, 0).setType(Material.CRAFTING_TABLE);
        when(otherBlock.getClickedBlock()).thenReturn(world.getBlockAt(5, 64, 0));
        listener.interact(otherBlock);
        verify(otherBlock, never()).setCancelled(true);
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
    }

    @Test
    void permissionSpectatorOrMissingXpPreventsConversion() {
        when(player.hasPermission("craftablebottleofenchanting.use")).thenReturn(false);
        listener.interact(interaction(Material.GLASS_BOTTLE));
        when(player.hasPermission("craftablebottleofenchanting.use")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        listener.interact(interaction(Material.GLASS_BOTTLE));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.calculateTotalExperiencePoints()).thenReturn(10);
        listener.interact(interaction(Material.GLASS_BOTTLE));
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
        assertEquals(2, inventory.getItem(0).getAmount());
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
