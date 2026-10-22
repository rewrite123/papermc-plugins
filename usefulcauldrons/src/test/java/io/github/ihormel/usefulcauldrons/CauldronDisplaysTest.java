package io.github.ihormel.usefulcauldrons;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class CauldronDisplaysTest {
    private World world;
    private Block pot;
    private CauldronDisplays displays;
    private CauldronContents contents;

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        world.loadChunk(0, 0);
        pot = world.getBlockAt(0, 65, 0);
        pot.setType(Material.CAULDRON);
        displays = new CauldronDisplays();
        contents = new CauldronContents();
    }

    @AfterEach
    void cleanup() {
        displays.clear();
        MockBukkit.unmock();
    }

    private List<ItemDisplay> visible() {
        return world.getEntitiesByClass(ItemDisplay.class).stream().filter(ItemDisplay::isValid).toList();
    }

    @Test
    void eachIngredientTypeHasOneNonPhysicalNonPersistentVisualRatherThanPickupItems() {
        contents.deposit(Material.SUGAR, 128);
        contents.deposit(Material.APPLE, 3);
        displays.update(pot, contents, 0);
        assertEquals(2, visible().size());
        assertTrue(world.getEntitiesByClass(Item.class).isEmpty());
        for (var display : visible()) {
            assertFalse(display.isPersistent());
            assertFalse(display.hasGravity());
            assertTrue(display.isInvulnerable());
            assertTrue(display.getPersistentDataContainer().has(CauldronDisplays.MARKER));
            assertEquals(ItemDisplay.ItemDisplayTransform.FIXED, display.getItemDisplayTransform());
            assertEquals(10, display.getInterpolationDuration());
            assertEquals(pot.getLocation().clone().add(0.5, 0.94, 0.5), display.getLocation());
        }
        var sugar = visible().stream().filter(d -> d.getItemStack().getType() == Material.SUGAR).findFirst().orElseThrow();
        assertEquals(64, sugar.getItemStack().getAmount());
        assertEquals(128, contents.count(Material.SUGAR));
    }

    @Test
    void syncReusesEntitiesAndRefreshesAmountsInsteadOfSpawningDuplicates() {
        contents.deposit(Material.SUGAR, 4);
        displays.update(pot, contents, 0);
        var display = visible().getFirst();
        var firstPose = display.getTransformation();
        contents.deposit(Material.SUGAR, 3);
        displays.update(pot, contents, 10);
        assertEquals(List.of(display), visible());
        assertEquals(7, display.getItemStack().getAmount());
        assertNotEquals(firstPose.getTranslation(), display.getTransformation().getTranslation());
        assertNotEquals(firstPose.getLeftRotation(), display.getTransformation().getLeftRotation());
    }

    @Test
    void cookingReplacesConsumedIngredientsWithFoodVisualButKeepsLeftovers() {
        contents.deposit(Material.SUGAR, 2);
        contents.deposit(Material.MILK_BUCKET, 1);
        contents.deposit(Material.BOWL, 4);
        contents.deposit(Material.BLAZE_POWDER, 1);
        displays.update(pot, contents, 0);
        var old = visible();
        Cooking.next(contents, false, true, false).apply(contents);
        displays.update(pot, contents, 10);
        assertEquals(2, visible().size());
        assertTrue(old.stream().filter(d -> d.getItemStack().getType() != Material.SUGAR).noneMatch(ItemDisplay::isValid));
        assertTrue(visible().stream().anyMatch(d -> "warm_milk".equals(Foods.id(d.getItemStack()))));
        assertTrue(visible().stream().anyMatch(d -> d.getItemStack().getType() == Material.SUGAR));
        contents.deposit(Material.COCOA_BEANS, 6);
        Cooking.next(contents, false, true, false).apply(contents);
        displays.update(pot, contents, 20);
        assertEquals(1, visible().size());
        assertEquals("hot_chocolate", Foods.id(visible().getFirst().getItemStack()));
    }

    @Test
    void clearingContentsOrReplacingCauldronRemovesVisualsWithoutDroppingAnything() {
        contents.deposit(Material.SUGAR, 26);
        displays.update(pot, contents, 0);
        contents.ingredients.clear();
        displays.update(pot, contents, 10);
        assertTrue(visible().isEmpty());
        contents.deposit(Material.APPLE, 1);
        displays.update(pot, contents, 20);
        pot.setType(Material.STONE);
        displays.update(pot, contents, 30);
        assertTrue(visible().isEmpty());
        assertTrue(world.getEntitiesByClass(Item.class).isEmpty());
    }

    @Test
    void removingStoredPositionAndDisablingRemoveAllTrackedVisuals() {
        contents.deposit(Material.SUGAR, 1);
        var stored = Map.of(ContentsStore.Position.of(pot), contents);
        displays.sync(stored, 0);
        assertEquals(1, visible().size());
        displays.sync(Map.of(), 10);
        assertTrue(visible().isEmpty());
        displays.sync(stored, 20);
        assertEquals(1, visible().size());
        displays.clear();
        assertTrue(visible().isEmpty());
        displays.sync(stored, 30);
        assertEquals(1, visible().size());
    }

    @Test
    void chunkUnloadRemovesVisualsAndLaterSyncRecreatesExactlyOnePerIngredient() {
        contents.deposit(Material.SUGAR, 1);
        displays.update(pot, contents, 0);
        var original = visible().getFirst();
        displays.unload(new EntitiesUnloadEvent(pot.getChunk(), List.of(original)));
        assertFalse(original.isValid());
        assertTrue(visible().isEmpty());
        assertEquals(1, contents.count(Material.SUGAR));
        displays.update(pot, contents, 10);
        assertEquals(1, visible().size());
        assertNotEquals(original.getUniqueId(), visible().getFirst().getUniqueId());
    }

    @Test
    void startupAndEntityLoadingRemoveOnlyOurMarkedRemnants() {
        var stale = world.spawn(pot.getLocation(), ItemDisplay.class);
        stale.getPersistentDataContainer().set(CauldronDisplays.MARKER, PersistentDataType.BYTE, (byte) 1);
        var unrelated = world.spawn(pot.getLocation(), ItemDisplay.class);
        unrelated.setItemStack(new ItemStack(Material.DIAMOND));
        displays.removeLoadedRemnants();
        assertFalse(stale.isValid());
        assertTrue(unrelated.isValid());
        var loaded = world.spawn(pot.getLocation(), ItemDisplay.class);
        loaded.getPersistentDataContainer().set(CauldronDisplays.MARKER, PersistentDataType.BYTE, (byte) 1);
        displays.load(new EntitiesLoadEvent(pot.getChunk(), List.of(loaded, unrelated)));
        assertFalse(loaded.isValid());
        assertTrue(unrelated.isValid());
    }

    @Test
    void syncingUnloadedContentsDoesNotLoadChunksOrCreateVisuals() {
        contents.deposit(Material.SUGAR, 4);
        var unloaded = new ContentsStore.Position(world.getUID(), 320, 65, 320);
        assertFalse(world.isChunkLoaded(20, 20));
        displays.sync(Map.of(unloaded, contents), 0);
        assertFalse(world.isChunkLoaded(20, 20));
        assertTrue(visible().isEmpty());
        assertEquals(4, contents.count(Material.SUGAR));
    }

    @Test
    void externallyRemovedVisualIsRecreatedWithoutChangingStoredContents() {
        contents.deposit(Material.SUGAR, 4);
        displays.update(pot, contents, 0);
        var original = visible().getFirst();
        original.remove();
        displays.update(pot, contents, 10);
        assertEquals(1, visible().size());
        assertNotEquals(original.getUniqueId(), visible().getFirst().getUniqueId());
        assertEquals(4, contents.count(Material.SUGAR));
        assertTrue(world.getEntitiesByClass(Item.class).isEmpty());
    }

    @Test
    void animationStaysInsideCauldronOpeningAndAboveWaterAtEveryPhase() {
        for (int count : new int[] {1, 2, 6, 32}) {
            for (int index = 0; index < count; index++) {
                for (int ticks = 0; ticks <= 240; ticks++) {
                    var pose = CauldronDisplays.pose(index, count, ticks);
                    var offset = pose.getTranslation();
                    assertTrue(Math.abs(offset.x) + pose.getScale().x / 2 < 0.375);
                    assertTrue(Math.abs(offset.z) + pose.getScale().z / 2 < 0.375);
                    assertTrue(0.94 + offset.y + pose.getScale().y / 2 > 1);
                    assertTrue(0.94 + offset.y < 1);
                }
            }
        }
    }
}
