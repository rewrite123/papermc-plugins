package io.github.ihormel.endercraftbuildings;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.List;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class PluginTest {
    private ServerMock server;
    private EnderCraftBuildingsPlugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private CrystalItems items;
    private BuildingListener listener;
    private double roll;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = new WorldMock(Material.AIR, 0);
        server.addWorld(world);
        EnderCraftBuildingsPlugin.blockRules = new TestRules();
        roll = 0.99;
        EnderCraftBuildingsPlugin.random = () -> roll;
        plugin = MockBukkit.load(EnderCraftBuildingsPlugin.class);
        items = plugin.items();
        listener = plugin.listener();
        player = server.addPlayer();
        player.teleport(world.getBlockAt(0, 64, -5).getLocation());
        player.setRotation(180, 0);
        player.setGameMode(GameMode.SURVIVAL);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
        EnderCraftBuildingsPlugin.blockRules = BlockRules.PAPER;
    }

    private Block at(int x, int y, int z) { return world.getBlockAt(x, y, z); }

    private ShapedRecipe recipe(String key) { return (ShapedRecipe) server.getRecipe(new NamespacedKey(plugin, key)); }

    private ItemStack scannedMemoryCrystal() {
        at(0, 64, 0).setType(Material.STONE);
        at(1, 64, 0).setType(Material.STONE);
        at(0, 65, 0).setType(Material.OAK_PLANKS);
        var crystal = items.memoryCrystal();
        player.getInventory().setItemInMainHand(crystal);
        listener.scanClick(player, EquipmentSlot.HAND, at(0, 64, 0));
        listener.scanClick(player, EquipmentSlot.HAND, at(1, 65, 0));
        listener.name(player, "Hut");
        return player.getInventory().getItemInMainHand();
    }

    private Block taper(int x, int y, int z) {
        Block block = at(x, y, z);
        block.setType(Material.VAULT);
        var event = new BlockPlaceEvent(block, block.getState(), at(x, y - 1, z), items.taper(), player, true,
                EquipmentSlot.HAND);
        listener.placeTaper(event);
        return block;
    }

    @Test
    void recipesMatchTheRequestedShapes() {
        var memory = recipe("memory_storage_crystal");
        assertArrayEquals(new String[] {"A", "P", "L"}, memory.getShape());
        assertTrue(memory.getChoiceMap().get('A').test(new ItemStack(Material.AMETHYST_SHARD)));
        assertTrue(memory.getChoiceMap().get('P').test(new ItemStack(Material.ENDER_PEARL)));
        assertTrue(memory.getChoiceMap().get('L').test(new ItemStack(Material.SOUL_LANTERN)));
        assertEquals(CrystalItems.MEMORY, items.kind(memory.getResult()));
        List<List<String>> diagonals = List.of(List.of("A ", " E"), List.of(" A", "E "), List.of("E ", " A"),
                List.of(" E", "A "));
        for (int i = 0; i < 4; i++) {
            var empty = recipe("empty_storage_crystal_" + i);
            assertEquals(diagonals.get(i), List.of(empty.getShape()));
            assertTrue(empty.getChoiceMap().get('E').test(new ItemStack(Material.ENDER_EYE)));
            assertEquals(CrystalItems.EMPTY, items.kind(empty.getResult()));
            assertEquals(1, empty.getResult().getAmount());
        }
        var taper = recipe("ender_biconical_taper");
        assertArrayEquals(new String[] {"P D", "O O", "OOO"}, taper.getShape());
        assertTrue(taper.getChoiceMap().get('P').test(new ItemStack(Material.ENDER_PEARL)));
        assertTrue(taper.getChoiceMap().get('D').test(new ItemStack(Material.DIAMOND)));
        assertTrue(taper.getChoiceMap().get('O').test(new ItemStack(Material.OBSIDIAN)));
        assertEquals(Material.VAULT, taper.getResult().getType());
        assertTrue(player.hasDiscoveredRecipe(new NamespacedKey(plugin, "ender_biconical_taper")));
    }

    @Test
    void itemsUseTheRequestedIcons() {
        var memory = items.memoryCrystal();
        assertEquals(Material.AMETHYST_SHARD, memory.getType());
        assertEquals(Material.SOUL_LANTERN.getKey(), memory.getItemMeta().getItemModel());
        assertEquals(1, memory.getData(DataComponentTypes.MAX_STACK_SIZE));
        var empty = items.emptyStorageCrystal(2);
        assertEquals(Material.AMETHYST_SHARD, empty.getType());
        assertNull(empty.getItemMeta().getItemModel());
        assertEquals(Material.VAULT, items.taper().getType());
    }

    @Test
    void scanningStoresTheNamedBlueprintAndKeepsTheCrystal() {
        var crystal = scannedMemoryCrystal();
        assertEquals(CrystalItems.MEMORY, items.kind(crystal));
        assertEquals("Hut", items.buildingName(crystal));
        assertEquals(3, items.blueprint(crystal).blockCount());
        assertNull(listener.pendingName(player));
        assertNull(listener.scan(player));
    }

    @Test
    void namePromptValidatesCancelsAndTimesOut() {
        at(0, 64, 0).setType(Material.STONE);
        player.getInventory().setItemInMainHand(items.memoryCrystal());
        listener.scanClick(player, EquipmentSlot.HAND, at(0, 64, 0));
        listener.scanClick(player, EquipmentSlot.HAND, at(0, 64, 0));
        listener.name(player, "x".repeat(33));
        assertNotNull(listener.pendingName(player));
        listener.name(player, "  cancel ");
        assertNull(listener.pendingName(player));
        assertNull(items.blueprint(player.getInventory().getItemInMainHand()));

        listener.scanClick(player, EquipmentSlot.HAND, at(0, 64, 0));
        listener.scanClick(player, EquipmentSlot.HAND, at(0, 64, 0));
        assertNotNull(listener.pendingName(player));
        for (int i = 0; i < BuildingListener.NAME_TIMEOUT_TICKS; i++) listener.tick();
        assertNull(listener.pendingName(player));
    }

    @Test
    void oversizedEmptyAndCancelledScansDoNotStoreAnything() {
        player.getInventory().setItemInMainHand(items.memoryCrystal());
        listener.scanClick(player, EquipmentSlot.HAND, at(0, 64, 0));
        listener.scanClick(player, EquipmentSlot.HAND, at(40, 64, 0));
        assertNotNull(listener.scan(player));
        listener.scanClick(player, EquipmentSlot.HAND, at(3, 64, 0));
        assertNull(listener.scan(player));
        assertNull(listener.pendingName(player));
        listener.scanClick(player, EquipmentSlot.HAND, at(0, 64, 0));
        player.setSneaking(true);
        listener.scanClick(player, EquipmentSlot.HAND, at(3, 64, 0));
        assertNull(listener.scan(player));
    }

    @Test
    void taperConsumesEmptyCrystalAndMaterialsButNotTheMemoryCrystal() {
        var memory = scannedMemoryCrystal();
        Block taper = taper(5, 64, 5);
        var inventory = player.getInventory();
        inventory.addItem(items.emptyStorageCrystal(2), new ItemStack(Material.STONE, 5),
                new ItemStack(Material.OAK_PLANKS, 1));
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, memory, taper, BlockFace.UP,
                EquipmentSlot.HAND);
        listener.interact(event);
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertEquals(3, BuildingListener.available(inventory, Material.STONE));
        assertEquals(0, BuildingListener.available(inventory, Material.OAK_PLANKS));
        assertTrue(inventory.containsAtLeast(items.emptyStorageCrystal(1), 1));
        assertFalse(inventory.containsAtLeast(items.emptyStorageCrystal(1), 2));
        assertEquals(CrystalItems.MEMORY, items.kind(inventory.getItemInMainHand()));
        var storage = java.util.Arrays.stream(inventory.getContents())
                .filter(stack -> CrystalItems.STORAGE.equals(items.kind(stack))).findFirst().orElseThrow();
        assertEquals("Hut", items.buildingName(storage));
        assertEquals(items.blueprint(memory), items.blueprint(storage));
        // MockBukkit inventories drop data components, so check the consumable timing on the produced item itself.
        assertEquals((float) CrystalItems.holdSeconds(3),
                items.copyFrom(storage).getData(DataComponentTypes.CONSUMABLE).consumeSeconds());
        assertTrue(world.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.ENDERMITE));
    }

    @Test
    void taperRefusesWithoutMaterialsOrEmptyCrystal() {
        var memory = scannedMemoryCrystal();
        Block taper = taper(5, 64, 5);
        var inventory = player.getInventory();
        inventory.addItem(new ItemStack(Material.STONE, 2), new ItemStack(Material.OAK_PLANKS, 1));
        listener.imprint(player, memory, taper);
        assertEquals(2, BuildingListener.available(inventory, Material.STONE));
        inventory.addItem(items.emptyStorageCrystal(1));
        inventory.removeItem(new ItemStack(Material.STONE, 1));
        var renamed = new ItemStack(Material.STONE, 5);
        renamed.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text("fancy")));
        inventory.addItem(renamed);
        listener.imprint(player, memory, taper);
        assertEquals(1, BuildingListener.available(inventory, Material.STONE));
        assertTrue(inventory.containsAtLeast(items.emptyStorageCrystal(1), 1));
        assertTrue(java.util.Arrays.stream(inventory.getContents())
                .noneMatch(stack -> CrystalItems.STORAGE.equals(items.kind(stack))));
    }

    @Test
    void creativeSkipsMaterialsAndStorageCrystalsCanBeCopiedWithEndermiteChance() {
        var memory = scannedMemoryCrystal();
        Block taper = taper(5, 64, 5);
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().addItem(items.emptyStorageCrystal(2));
        listener.imprint(player, memory, taper);
        var storage = java.util.Arrays.stream(player.getInventory().getContents())
                .filter(stack -> CrystalItems.STORAGE.equals(items.kind(stack))).findFirst().orElseThrow().clone();
        roll = 0.01;
        listener.imprint(player, storage, taper);
        assertEquals(2, java.util.Arrays.stream(player.getInventory().getContents())
                .filter(stack -> CrystalItems.STORAGE.equals(items.kind(stack))).mapToInt(ItemStack::getAmount).sum());
        assertFalse(player.getInventory().containsAtLeast(items.emptyStorageCrystal(1), 1));
        assertEquals(1, world.getEntities().stream().filter(entity -> entity.getType() == EntityType.ENDERMITE).count());
    }

    @Test
    void storageCrystalPlacesTheBuildingAndBreaks() {
        var memory = scannedMemoryCrystal();
        var storage = items.copyFrom(memory);
        player.getInventory().setItemInMainHand(storage);
        at(0, 64, 0).setType(Material.AIR);
        at(1, 64, 0).setType(Material.AIR);
        at(0, 65, 0).setType(Material.AIR);
        Block ground = at(10, 63, 10);
        ground.setType(Material.DIRT);
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, storage, ground, BlockFace.UP,
                EquipmentSlot.HAND);
        listener.interact(event);
        assertEquals(Event.Result.ALLOW, event.useItemInHand());
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        var session = listener.placement(player);
        assertNotNull(session);
        assertEquals(at(10, 64, 10), session.anchor);
        var consume = new PlayerItemConsumeEvent(player, storage, EquipmentSlot.HAND);
        listener.consume(consume);
        assertTrue(consume.isCancelled());
        assertEquals(Material.STONE, at(10, 64, 10).getType());
        assertEquals(Material.STONE, at(11, 64, 10).getType());
        assertEquals(Material.OAK_PLANKS, at(10, 65, 10).getType());
        assertTrue(player.getInventory().getItemInMainHand().isEmpty());
        assertNull(listener.placement(player));
    }

    @Test
    void obstructedOrProtectedPlacementKeepsTheCrystal() {
        var storage = items.copyFrom(scannedMemoryCrystal());
        player.getInventory().setItemInMainHand(storage);
        Block ground = at(10, 63, 10);
        ground.setType(Material.DIRT);
        at(11, 64, 10).setType(Material.COBBLESTONE);
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, storage, ground, BlockFace.UP,
                EquipmentSlot.HAND);
        listener.interact(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertNull(listener.placement(player));

        at(11, 64, 10).setType(Material.AIR);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void deny(BlockPlaceEvent place) {
                if (place.getBlock().getX() == 11) place.setCancelled(true);
            }
        }, plugin);
        listener.interact(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, storage, ground, BlockFace.UP,
                EquipmentSlot.HAND));
        listener.consume(new PlayerItemConsumeEvent(player, storage, EquipmentSlot.HAND));
        assertTrue(at(10, 64, 10).isEmpty());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void airClicksAndUnsessionedConsumptionNeverStartPlacement() {
        var storage = items.copyFrom(scannedMemoryCrystal());
        player.getInventory().setItemInMainHand(storage);
        var air = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, storage, null, BlockFace.SELF,
                EquipmentSlot.HAND);
        listener.interact(air);
        assertEquals(Event.Result.DENY, air.useItemInHand());
        var consume = new PlayerItemConsumeEvent(player, storage, EquipmentSlot.HAND);
        listener.consume(consume);
        assertTrue(consume.isCancelled());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void sessionIsDroppedWhenThePlayerNeverStartsHolding() {
        var storage = items.copyFrom(scannedMemoryCrystal());
        player.getInventory().setItemInMainHand(storage);
        Block ground = at(10, 63, 10);
        ground.setType(Material.DIRT);
        listener.interact(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, storage, ground, BlockFace.UP,
                EquipmentSlot.HAND));
        assertNotNull(listener.placement(player));
        for (int i = 0; i < 7; i++) listener.tick();
        assertNull(listener.placement(player));
    }

    @Test
    void taperBlockDropsItselfAndSurvivesExplosions() {
        Block taper = taper(5, 64, 5);
        var explosion = new EntityExplodeEvent(player, taper.getLocation(), new java.util.ArrayList<>(List.of(taper,
                at(6, 64, 5))), 1f, org.bukkit.ExplosionResult.DESTROY);
        listener.entityExplode(explosion);
        assertEquals(List.of(at(6, 64, 5)), explosion.blockList());
        var breaking = new BlockBreakEvent(taper, player);
        listener.breakTaper(breaking);
        assertFalse(breaking.isDropItems());
        assertTrue(world.getEntities().stream().anyMatch(entity -> entity instanceof org.bukkit.entity.Item item
                && CrystalItems.TAPER.equals(items.kind(item.getItemStack()))));
        var click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, taper, BlockFace.UP,
                EquipmentSlot.HAND);
        listener.interact(click);
        assertNotEquals(Event.Result.DENY, click.useInteractedBlock());
    }

    @Test
    void customItemsCannotBeUsedAsVanillaCraftingIngredients() {
        var inventory = mock(CraftingInventory.class);
        var view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getMatrix()).thenReturn(new ItemStack[] {items.emptyStorageCrystal(1), null});
        listener.prepareCraft(new PrepareItemCraftEvent(inventory, view, false));
        verify(inventory).setResult(null);
        var plain = mock(CraftingInventory.class);
        when(view.getTopInventory()).thenReturn(plain);
        when(plain.getMatrix()).thenReturn(new ItemStack[] {new ItemStack(Material.AMETHYST_SHARD)});
        listener.prepareCraft(new PrepareItemCraftEvent(plain, view, false));
        verify(plain, never()).setResult(any());
    }

    @Test
    void configDefaultsToFivePercentEndermites() {
        assertEquals(0.05, plugin.getConfig().getDouble("endermite-chance.taper"));
        assertEquals(0.05, plugin.getConfig().getDouble("endermite-chance.placement"));
        assertEquals(Map.of(), Map.of());
    }
}
