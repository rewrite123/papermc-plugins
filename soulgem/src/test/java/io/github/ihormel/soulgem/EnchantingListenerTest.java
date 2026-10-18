package io.github.ihormel.soulgem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class EnchantingListenerTest extends PaperTest {
    private final List<Runnable> tasks = new ArrayList<>();
    private GemItems gems;
    private EnchantingListener listener;
    private EnchantingOffers offers;
    private Player player;
    private PlayerInventory inventory;
    private World world;
    private GemMenu menu;
    private InventoryView view;
    private ItemStack output;

    @BeforeEach
    void setUpEnchanting() {
        gems = new GemItems(MockBukkit.createMockPlugin("soulgem"));
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server api = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(api);
        when(api.getScheduler()).thenReturn(scheduler);
        when(api.getPluginManager()).thenReturn(server.getPluginManager());
        when(scheduler.runTask(any(JavaPlugin.class), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return null;
        });
        offers = mock(EnchantingOffers.class);
        when(offers.bookshelves(any(Location.class))).thenReturn(15);
        listener = new EnchantingListener(plugin, gems, offers);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("soulgem.use")).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLevel()).thenReturn(30);
        when(player.getEnchantmentSeed()).thenReturn(42);
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.AIR));
        world = server.addSimpleWorld("world");
        world.getBlockAt(0, 64, 0).setType(Material.ENCHANTING_TABLE);
        world.getChunkAt(0, 0).load();
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        inventory = server.addPlayer().getInventory();
        inventory.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        var fuel = gems.create(GemItems.Kind.ECHO, true);
        fuel.setAmount(3);
        inventory.setItemInOffHand(fuel);
        when(player.getInventory()).thenReturn(inventory);
        output = new ItemStack(Material.DIAMOND_SWORD);
        output.addUnsafeEnchantment(Enchantment.SHARPNESS, 6);
        var choices = List.of(new EnchantingOffers.Offer(10, 1, output),
                new EnchantingOffers.Offer(20, 2, output),
                new EnchantingOffers.Offer(30, 3, output));
        when(offers.create(any(ItemStack.class), any(GemItems.Kind.class), anyInt(), anyInt()))
                .thenReturn(choices);
        menu = new GemMenu(player, new Location(world, 0, 64, 0), 15, choices);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(menu.getInventory());
        when(player.getOpenInventory()).thenReturn(view);
    }

    private InventoryClickEvent click(int slot, ClickType type) {
        var event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getClick()).thenReturn(type);
        return event;
    }

    private void assertNothingSpent() {
        assertEquals(Material.DIAMOND_SWORD, inventory.getItemInMainHand().getType());
        assertTrue(inventory.getItemInMainHand().getEnchantments().isEmpty());
        assertEquals(3, inventory.getItemInOffHand().getAmount());
        verify(player, never()).giveExpLevels(anyInt());
    }

    @Test
    void purchaseIsDeferredAndConsumesExactOfferCostOnlyOnce() {
        var event = click(6, ClickType.LEFT);
        listener.click(event);
        verify(event).setCancelled(true);
        assertNothingSpent();
        assertEquals(1, tasks.size());
        tasks.getFirst().run();
        assertEquals(output, inventory.getItemInMainHand());
        assertTrue(inventory.getItemInOffHand().getType().isAir());
        verify(player).giveExpLevels(-3);
        verify(player).setEnchantmentSeed(anyInt());
        verify(player).closeInventory();
        listener.commit(player, menu, 2);
        verify(player, times(1)).giveExpLevels(-3);
    }

    @Test
    void cheaperOfferLeavesTheUnspentGems() {
        listener.commit(player, menu, 0);
        assertEquals(2, inventory.getItemInOffHand().getAmount());
        assertTrue(gems.charged(inventory.getItemInOffHand()));
        verify(player).giveExpLevels(-1);
    }

    @Test
    void playerMustMeetRequiredLevelNotJustTheChargedLevels() {
        when(player.getLevel()).thenReturn(29);
        listener.commit(player, menu, 2);
        assertNothingSpent();
    }

    @Test
    void insufficientGemsDoesNotChargeOrModifyGear() {
        var fuel = inventory.getItemInOffHand();
        fuel.setAmount(2);
        inventory.setItemInOffHand(fuel);
        var current = new GemMenu(player, menu.table, 15, menu.offers);
        listener.commit(player, current, 2);
        assertTrue(inventory.getItemInMainHand().getEnchantments().isEmpty());
        assertEquals(2, inventory.getItemInOffHand().getAmount());
        verify(player, never()).giveExpLevels(anyInt());
    }

    @Test
    void changingHandsInvalidatesMenu() {
        inventory.setHeldItemSlot(1);
        listener.commit(player, menu, 2);
        verify(player, never()).giveExpLevels(anyInt());
        verify(player).closeInventory();
        assertEquals(3, inventory.getItemInOffHand().getAmount());
    }

    @Test
    void changingItemMetadataInvalidatesMenu() {
        var gear = inventory.getItemInMainHand();
        gear.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text("Changed")));
        inventory.setItemInMainHand(gear);
        listener.commit(player, menu, 2);
        assertNothingSpent();
    }

    @Test
    void replacingFuelWithPlainShardsInvalidatesMenu() {
        inventory.setItemInOffHand(new ItemStack(Material.ECHO_SHARD, 3));
        listener.commit(player, menu, 2);
        assertNothingSpent();
    }

    @Test
    void movingTooFarOrBreakingTheTablePreventsPurchase() {
        when(player.getLocation()).thenReturn(new Location(world, 20, 64, 0));
        listener.commit(player, menu, 2);
        assertNothingSpent();
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        world.getBlockAt(0, 64, 0).setType(Material.AIR);
        listener.commit(player, menu, 2);
        assertNothingSpent();
    }

    @Test
    void changingBookshelvesOrEnchantingSeedPreventsPurchase() {
        when(offers.bookshelves(any(Location.class))).thenReturn(14);
        listener.commit(player, menu, 2);
        assertNothingSpent();
        when(offers.bookshelves(any(Location.class))).thenReturn(15);
        when(player.getEnchantmentSeed()).thenReturn(43);
        listener.commit(player, menu, 2);
        assertNothingSpent();
    }

    @Test
    void cursorItemsPermissionLossDeathAndSpectatorModePreventPurchase() {
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.DIAMOND));
        listener.commit(player, menu, 2);
        assertNothingSpent();
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.AIR));
        when(player.hasPermission("soulgem.use")).thenReturn(false);
        listener.commit(player, menu, 2);
        assertNothingSpent();
        when(player.hasPermission("soulgem.use")).thenReturn(true);
        when(player.isDead()).thenReturn(true);
        listener.commit(player, menu, 2);
        assertNothingSpent();
        when(player.isDead()).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        listener.commit(player, menu, 2);
        assertNothingSpent();
    }

    @Test
    void wrongOwnerCannotUseTheMenu() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        listener.commit(player, menu, 2);
        assertNothingSpent();
    }

    @Test
    void cancelledClicksShiftClicksNumberKeysDoubleClicksAndDropsNeverPurchase() {
        for (var type : List.of(ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP,
                ClickType.SWAP_OFFHAND, ClickType.MIDDLE)) {
            var event = click(6, type);
            listener.click(event);
            verify(event).setCancelled(true);
        }
        var cancelled = click(6, ClickType.LEFT);
        when(cancelled.isCancelled()).thenReturn(true);
        listener.click(cancelled);
        assertTrue(tasks.isEmpty());
        assertNothingSpent();
    }

    @Test
    void displayAndBottomInventorySlotsCannotBeMovedOrUsedToPurchase() {
        for (int slot : List.of(-999, 0, 1, 3, 5, 7, 8, 9, 20, 40)) {
            var event = click(slot, ClickType.LEFT);
            listener.click(event);
            verify(event).setCancelled(true);
        }
        assertTrue(tasks.isEmpty());
        assertNothingSpent();
    }

    @Test
    void dragsAreBlocked() {
        var event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(view);
        listener.drag(event);
        verify(event).setCancelled(true);
    }

    @Test
    void closingTheMenuOrDisconnectingBeforeDispatchPreventsPurchase() {
        listener.click(click(6, ClickType.RIGHT));
        when(player.getOpenInventory()).thenReturn(mock(InventoryView.class));
        tasks.getFirst().run();
        assertNothingSpent();
        when(player.getOpenInventory()).thenReturn(view);
        when(player.isOnline()).thenReturn(false);
        tasks.getFirst().run();
        assertNothingSpent();
    }

    @Test
    void disabledOfferDoesNotConsumeAnything() {
        var disabled = new GemMenu(player, menu.table, 15,
                List.of(new EnchantingOffers.Offer(10, 1, null)));
        listener.commit(player, disabled, 0);
        assertNothingSpent();
    }

    @Test
    void nativeEnchantingIsBlockedButOtherInventoriesAreNot() {
        var event = mock(InventoryOpenEvent.class);
        var inventory = mock(Inventory.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getPlayer()).thenReturn(player);
        when(inventory.getType()).thenReturn(InventoryType.CHEST);
        listener.nativeOpen(event);
        verify(event, never()).setCancelled(true);
        when(inventory.getType()).thenReturn(InventoryType.ENCHANTING);
        listener.nativeOpen(event);
        verify(event).setCancelled(true);
        var prepare = mock(PrepareItemEnchantEvent.class);
        var enchant = mock(EnchantItemEvent.class);
        listener.nativePrepare(prepare);
        listener.nativeEnchant(enchant);
        verify(prepare).setCancelled(true);
        verify(enchant).setCancelled(true);
    }

    private PlayerInteractEvent interactEvent() {
        var event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.useInteractedBlock()).thenReturn(Event.Result.ALLOW);
        when(event.getClickedBlock()).thenReturn(world.getBlockAt(0, 64, 0));
        return event;
    }

    @Test
    void validTableInteractionOpensTheCustomMenu() {
        var event = interactEvent();
        listener.interact(event);
        verify(event).setCancelled(true);
        verify(player).openInventory(any(Inventory.class));
    }

    @Test
    void deniedAndOffHandInteractionsDoNotOpenMenus() {
        var event = interactEvent();
        when(event.useInteractedBlock()).thenReturn(Event.Result.DENY);
        listener.interact(event);
        when(event.useInteractedBlock()).thenReturn(Event.Result.ALLOW);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        listener.interact(event);
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    void plainShardsAndUnchargedGemsCannotOpenMenu() {
        var event = interactEvent();
        inventory.setItemInOffHand(new ItemStack(Material.ECHO_SHARD));
        listener.interact(event);
        inventory.setItemInOffHand(gems.create(GemItems.Kind.ECHO, false));
        listener.interact(event);
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    void glassBottlesKeepTheirExistingBottleExperienceInteraction() {
        MockBukkit.createMockPlugin("craftablebottleofenchanting");
        assertTrue(server.getPluginManager().isPluginEnabled("craftablebottleofenchanting"));
        inventory.setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        var event = interactEvent();
        listener.interact(event);
        verify(event, never()).setCancelled(true);
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    void changingWorldsPreventsPurchase() {
        when(player.getWorld()).thenReturn(server.addSimpleWorld("elsewhere"));
        listener.commit(player, menu, 2);
        assertNothingSpent();
    }

    @Test
    void creativePlayersStillPayTheAgreedCosts() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        listener.commit(player, menu, 2);
        assertTrue(inventory.getItemInOffHand().getType().isAir());
        verify(player).giveExpLevels(-3);
    }
}
