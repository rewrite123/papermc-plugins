package io.github.ihormel.usefulcauldrons;

import static org.junit.jupiter.api.Assertions.*;
import static org.bukkit.Material.*;
import java.io.IOException;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.World;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PluginTest {
    private ServerMock server;
    private World world;
    private UsefulCauldronsPlugin plugin;
    private Block pot;
    private PlayerMock player;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        world.loadChunk(0, 0);
        plugin = MockBukkit.load(UsefulCauldronsPlugin.class);
        pot = world.getBlockAt(0, 65, 0);
        pot.setType(CAULDRON);
        pot.getRelative(BlockFace.DOWN).setType(LAVA);
        player = server.addPlayer();
        player.teleport(world.getSpawnLocation());
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private void tick(int ticks) {
        server.getScheduler().performTicks(ticks);
    }

    private Item drop(Material material, int amount) {
        var item = world.dropItem(pot.getLocation().clone().add(0.5, 0.4, 0.5), new ItemStack(material, amount));
        assertTrue(plugin.isEnabled());
        assertTrue(world.isChunkLoaded(0, 0));
        assertEquals(pot, UsefulCauldronsPlugin.containing(item.getLocation()));
        assertFalse(item.getPersistentDataContainer().has(UsefulCauldronsPlugin.OUTPUT));
        assertTrue(item.getItemStack().isSimilar(new ItemStack(material)));
        // MockBukkit's dropItem creates ItemEntityMock, which its spawn-event dispatch does not recognize.
        server.getPluginManager().callEvent(new org.bukkit.event.entity.ItemSpawnEvent(item));
        return item;
    }

    private void water(int levels) {
        pot.setType(WATER_CAULDRON);
        var data = (Levelled) pot.getBlockData();
        data.setLevel(levels);
        pot.setBlockData(data);
        pot.getState().setBlockData(data);
    }

    private PlayerInteractEvent click(ItemStack hand, boolean sneak) {
        player.getInventory().setItemInMainHand(hand);
        player.setSneaking(sneak);
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hand, pot, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private CauldronContents saved() throws IOException {
        tick(20);
        return new ContentsStore(plugin.getDataFolder().toPath().resolve("contents.yml")).load()
                .get(ContentsStore.Position.of(pot));
    }

    private int dropped(Material material) {
        return world.getEntitiesByClass(Item.class).stream().filter(Item::isValid)
                .filter(i -> i.getItemStack().getType() == material).mapToInt(i -> i.getItemStack().getAmount()).sum();
    }

    @Test
    void realItemIntakeCooksStewDrainsOneLevelAndCollectionChargesOneBowl() throws IOException {
        water(3);
        var red = drop(RED_MUSHROOM, 1);
        var brown = drop(BROWN_MUSHROOM, 1);
        tick(10);
        assertFalse(red.isValid());
        assertFalse(brown.isValid());
        assertEquals(2, ((Levelled) pot.getBlockData()).getLevel());
        assertEquals(2, saved().servings);
        var event = click(new ItemStack(BOWL), false);
        assertTrue(event.isCancelled());
        assertEquals(1, player.getInventory().all(MUSHROOM_STEW).size());
        assertFalse(player.getInventory().contains(BOWL));
        assertEquals(1, saved().servings);
    }

    @Test
    void lastWaterLevelCanCookAndInputsRemainRetrievableWithoutHeat() throws IOException {
        water(1);
        drop(RED_MUSHROOM, 1);
        drop(BROWN_MUSHROOM, 1);
        drop(SUGAR, 5);
        tick(10);
        assertEquals(CAULDRON, pot.getType());
        assertEquals(2, saved().servings);
        pot.getRelative(BlockFace.DOWN).setType(STONE);
        click(null, true);
        assertEquals(2, saved().servings);
        assertEquals(0, player.getInventory().all(SUGAR).size());
        player.getInventory().setItem(9, new ItemStack(BOWL, 2));
        click(null, true);
        assertEquals(1, player.getInventory().all(SUGAR).size());
        assertEquals(2, player.getInventory().all(MUSHROOM_STEW).size());
        assertFalse(player.getInventory().contains(BOWL));
        assertNull(saved());
    }

    @Test
    void warmMilkCollectionDoesNotChargeAdditionalBowlsAndReturnsBucket() throws IOException {
        drop(MILK_BUCKET, 1);
        drop(BOWL, 4);
        drop(BLAZE_POWDER, 1);
        tick(10);
        assertEquals(1, dropped(BUCKET));
        assertEquals(4, saved().servings);
        click(null, false);
        var milk = player.getInventory().all(MUSHROOM_STEW).values().iterator().next();
        assertEquals("warm_milk", Foods.id(milk));
        assertEquals(3, saved().servings);
        assertFalse(player.getInventory().contains(BOWL));
    }

    @Test
    void candyIsEjectedOnceAndItsReturnedBottlesAreNeverReabsorbed() throws IOException {
        drop(APPLE, 1);
        drop(HONEY_BOTTLE, 12);
        tick(20);
        assertEquals(12, dropped(GLASS_BOTTLE));
        assertEquals(1, dropped(APPLE));
        assertNull(saved());
        for (var entity : world.getEntitiesByClass(Item.class)) {
            if (entity.isValid()) entity.teleport(pot.getLocation().clone().add(0.5, 0.4, 0.5));
        }
        tick(40);
        assertEquals(12, dropped(GLASS_BOTTLE));
        assertEquals(1, dropped(APPLE));
        assertNull(saved());
    }

    @Test
    void cancelledAutomaticActionsPreserveItems() throws IOException {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void cancel(CauldronProcessEvent event) {
                event.setCancelled(true);
            }
        }, plugin);
        var sugar = drop(SUGAR, 26);
        var apple = drop(APPLE, 1);
        tick(30);
        assertTrue(sugar.isValid());
        assertTrue(apple.isValid());
        assertNull(saved());
    }

    @Test
    void cancelledWaterDrainDoesNotConsumeRecipeIngredients() throws IOException {
        water(3);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void cancel(CauldronLevelChangeEvent event) {
                event.setCancelled(true);
            }
        }, plugin);
        drop(RED_MUSHROOM, 1);
        drop(BROWN_MUSHROOM, 1);
        tick(20);
        var state = saved();
        assertEquals(0, state.servings);
        assertEquals(1, state.count(RED_MUSHROOM));
        assertEquals(1, state.count(BROWN_MUSHROOM));
        assertEquals(3, ((Levelled) pot.getBlockData()).getLevel());
    }

    @Test
    void cancelledInteractionDoesNotCollectStoredFood() throws IOException {
        drop(MILK_BUCKET, 1);
        drop(BOWL, 4);
        drop(BLAZE_POWDER, 1);
        tick(20);
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, pot, BlockFace.UP, EquipmentSlot.HAND);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertEquals(4, saved().servings);
        assertFalse(player.getInventory().contains(MUSHROOM_STEW));
    }

    @Test
    void dyeConsumesExactlyOneDyeThenEightUsesForConcreteAndPreservesWater() throws IOException {
        water(3);
        var dye = drop(RED_DYE, 3);
        tick(10);
        assertEquals(2, dye.getItemStack().getAmount());
        drop(SAND, 4);
        drop(GRAVEL, 4);
        tick(10);
        assertEquals(8, dropped(RED_CONCRETE));
        assertEquals(56, saved().uses);
        assertEquals(3, ((Levelled) pot.getBlockData()).getLevel());
        assertEquals(2, dye.getItemStack().getAmount());
    }

    @Test
    void charcoalRequiresSixtySecondsAndRemovingLidResetsTimer() throws IOException {
        drop(OAK_LOG, 1);
        tick(10);
        pot.getRelative(BlockFace.UP).setType(STONE);
        tick(600);
        int progress = saved().charcoalTicks;
        assertTrue(progress >= 600 && progress <= 620);
        pot.getRelative(BlockFace.UP).setType(AIR);
        tick(10);
        assertEquals(0, saved().charcoalTicks);
        pot.getRelative(BlockFace.UP).setType(STONE);
        tick(1190);
        assertEquals(0, dropped(CHARCOAL));
        tick(10);
        assertEquals(2, dropped(CHARCOAL));
        assertNull(saved());
    }

    @Test
    void blockBreakAndFluidReplacementAreBlockedUntilIngredientsRetrieved() {
        drop(SUGAR, 5);
        tick(10);
        var breaking = new BlockBreakEvent(pot, player);
        server.getPluginManager().callEvent(breaking);
        assertTrue(breaking.isCancelled());
        var replacement = pot.getState();
        replacement.setType(LAVA_CAULDRON);
        var fluid = new CauldronLevelChangeEvent(pot, player, CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY, replacement);
        server.getPluginManager().callEvent(fluid);
        assertTrue(fluid.isCancelled());
        click(null, true);
        breaking = new BlockBreakEvent(pot, player);
        server.getPluginManager().callEvent(breaking);
        assertFalse(breaking.isCancelled());
    }

    @Test
    void milkImmunityCancelsBothBeneficialAndHarmfulNewEffects() {
        new MealsListener(plugin).apply(player, "warm_milk", System.currentTimeMillis());
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        assertTrue(player.getActivePotionEffects().isEmpty());
        player.getPersistentDataContainer().set(MealsListener.IMMUNE, PersistentDataType.LONG, 1L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        assertTrue(player.hasPotionEffect(PotionEffectType.SPEED));
    }

    @Test
    void cancelledConsumptionDoesNotGrantEffectsOrBowl() {
        var event = new PlayerItemConsumeEvent(player, Foods.create("speedy_stew"), EquipmentSlot.HAND);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        tick(1);
        assertFalse(player.hasPotionEffect(PotionEffectType.SPEED));
        assertFalse(player.getInventory().contains(BOWL));
    }

    @Test
    void successfulConsumptionReturnsExactlyOneBowlAndAppliesEffect() {
        var event = new PlayerItemConsumeEvent(player, Foods.create("speedy_stew"), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        tick(1);
        assertEquals(1, player.getInventory().getItem(0).getAmount());
        assertEquals(BOWL, player.getInventory().getItem(0).getType());
        assertEquals(600, player.getPotionEffect(PotionEffectType.SPEED).getDuration());
    }

    @Test
    void namedRecipeIngredientsAreNotDestroyed() {
        var item = new ItemStack(APPLE);
        item.editMeta(meta -> meta.setDisplayName("Keepsake"));
        var dropped = world.dropItem(pot.getLocation().clone().add(0.5, 0.4, 0.5), item);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.ItemSpawnEvent(dropped));
        drop(SUGAR, 26);
        tick(20);
        assertTrue(dropped.isValid());
        assertEquals("Keepsake", dropped.getItemStack().getItemMeta().getDisplayName());
    }

    @Test
    void hotChocolateUpgradeAndItsOwnAbsorptionWorkWithRegisteredImmunityListener() throws IOException {
        drop(MILK_BUCKET, 1);
        drop(BOWL, 4);
        drop(BLAZE_POWDER, 1);
        tick(10);
        drop(COCOA_BEANS, 6);
        drop(SUGAR, 2);
        tick(10);
        assertEquals("hot_chocolate", saved().food);
        var event = new PlayerItemConsumeEvent(player, Foods.create("hot_chocolate"), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        tick(1);
        assertEquals(2, player.getAbsorptionAmount());
        assertTrue(MealsListener.active(player, MealsListener.IMMUNE, System.currentTimeMillis() + 7000));
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
        assertFalse(player.hasPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void dyeingAStackConsumesOnlyRemainingUsesAndLeavesRestUntouched() throws IOException {
        water(3);
        drop(BLUE_DYE, 1);
        tick(10);
        var wool = drop(RED_WOOL, 60);
        tick(10);
        assertFalse(wool.isValid());
        assertEquals(60, dropped(BLUE_WOOL));
        var second = drop(RED_WOOL, 10);
        tick(10);
        assertTrue(second.isValid());
        assertEquals(6, second.getItemStack().getAmount());
        assertEquals(64, dropped(BLUE_WOOL));
        assertNull(saved());
        assertEquals(3, ((Levelled) pot.getBlockData()).getLevel());
    }

    @Test
    void playerColorTrailCostsOneUsePerEntryRatherThanPerTick() throws IOException {
        water(3);
        drop(PURPLE_DYE, 1);
        tick(10);
        player.teleport(pot.getLocation().clone().add(0.5, 0.4, 0.5));
        tick(10);
        assertEquals(63, saved().uses);
        assertEquals("PURPLE", player.getPersistentDataContainer().get(UsefulCauldronsPlugin.TRAIL, PersistentDataType.STRING));
        tick(40);
        assertEquals(63, saved().uses);
        player.teleport(world.getSpawnLocation());
        tick(10);
        player.teleport(pot.getLocation().clone().add(0.5, 0.4, 0.5));
        tick(10);
        assertEquals(62, saved().uses);
    }

    @Test
    void sheepEntryRecolorsOnlyOnceAndKeepsRemainingDyeUses() throws IOException {
        water(3);
        drop(BLUE_DYE, 1);
        tick(10);
        var sheep = world.spawn(pot.getLocation().clone().add(0.5, 0.4, 0.5), org.bukkit.entity.Sheep.class);
        tick(10);
        assertEquals(DyeColor.BLUE, sheep.getColor());
        assertEquals(63, saved().uses);
        tick(40);
        assertEquals(63, saved().uses);
    }

    @Test
    void emptyBucketCanReachVanillaDyedWaterCollectionAndEmptyPotLosesDye() throws IOException {
        water(3);
        drop(BLUE_DYE, 1);
        tick(10);
        assertEquals(64, saved().uses);
        var interaction = click(new ItemStack(BUCKET), false);
        assertNotEquals(org.bukkit.event.Event.Result.DENY, interaction.useInteractedBlock());
        assertNotEquals(org.bukkit.event.Event.Result.DENY, interaction.useItemInHand());
        var empty = pot.getState(true);
        empty.setType(CAULDRON);
        var collection = new CauldronLevelChangeEvent(pot, player,
                CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL, empty);
        server.getPluginManager().callEvent(collection);
        assertFalse(collection.isCancelled());
        assertTrue(collection.getNewState().update(true, false));
        assertEquals(CAULDRON, pot.getType());
        assertNull(saved());
        water(3);
        assertNull(saved());
    }

    @Test
    void bucketCollectionStillProtectsStoredIngredients() {
        water(3);
        drop(RED_DYE, 1);
        tick(10);
        drop(SAND, 1);
        tick(10);
        assertTrue(click(new ItemStack(BUCKET), false).isCancelled());
        var empty = pot.getState(true);
        empty.setType(CAULDRON);
        var collection = new CauldronLevelChangeEvent(pot, player,
                CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL, empty);
        server.getPluginManager().callEvent(collection);
        assertTrue(collection.isCancelled());
        click(null, true);
        assertNotEquals(org.bukkit.event.Event.Result.DENY,
                click(new ItemStack(BUCKET), false).useInteractedBlock());
    }

    @Test
    void bucketCollectionStillProtectsCookedServings() {
        water(3);
        drop(RED_MUSHROOM, 1);
        drop(BROWN_MUSHROOM, 1);
        tick(10);
        assertTrue(click(new ItemStack(BUCKET), false).isCancelled());
        var empty = pot.getState(true);
        empty.setType(CAULDRON);
        var collection = new CauldronLevelChangeEvent(pot, player,
                CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL, empty);
        server.getPluginManager().callEvent(collection);
        assertTrue(collection.isCancelled());
    }

    @Test
    void cancelledBucketInteractionsAndFluidCollectionPreserveDyedWater() throws IOException {
        water(3);
        drop(PURPLE_DYE, 1);
        tick(10);
        var interaction = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(BUCKET),
                pot, BlockFace.UP, EquipmentSlot.HAND);
        interaction.setCancelled(true);
        server.getPluginManager().callEvent(interaction);
        assertTrue(interaction.isCancelled());
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void cancel(CauldronLevelChangeEvent event) {
                event.setCancelled(true);
            }
        }, plugin);
        var empty = pot.getState(true);
        empty.setType(CAULDRON);
        var collection = new CauldronLevelChangeEvent(pot, player,
                CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL, empty);
        server.getPluginManager().callEvent(collection);
        assertTrue(collection.isCancelled());
        assertEquals(WATER_CAULDRON, pot.getType());
        assertEquals(DyeColor.PURPLE, saved().dye);
        assertEquals(64, saved().uses);
    }

    @Test
    void floatingDisplaysFollowIntakeCraftingAndRetrievalWithoutExtraDroppedItems() {
        drop(MILK_BUCKET, 1);
        tick(10);
        assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).stream()
                .filter(org.bukkit.entity.Entity::isValid).count());
        assertEquals(0, dropped(MILK_BUCKET));
        drop(BOWL, 4);
        drop(BLAZE_POWDER, 1);
        tick(10);
        var visible = world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).stream()
                .filter(org.bukkit.entity.Entity::isValid).toList();
        assertEquals(1, visible.size());
        assertEquals("warm_milk", Foods.id(visible.getFirst().getItemStack()));
        assertEquals(1, dropped(BUCKET));
        click(null, true);
        assertFalse(visible.getFirst().isValid());
        assertEquals(4, player.getInventory().all(MUSHROOM_STEW).size());
        assertEquals(0, dropped(MUSHROOM_STEW));
    }

    @Test
    void normalDisableEnablePreservesPartialRecipesWithoutForceLoading() throws IOException {
        drop(SUGAR, 25);
        tick(10);
        var original = world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).stream()
                .filter(org.bukkit.entity.Entity::isValid).findFirst().orElseThrow();
        server.getPluginManager().disablePlugin(plugin);
        assertFalse(original.isValid());
        server.getPluginManager().enablePlugin(plugin);
        assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).stream()
                .filter(org.bukkit.entity.Entity::isValid).count());
        assertEquals(25, saved().count(SUGAR));
        drop(SUGAR, 1);
        drop(APPLE, 1);
        tick(10);
        assertEquals(1, dropped(APPLE));
        assertNull(saved());
    }
}
