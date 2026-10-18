package io.github.ihormel.soulgem;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GemItemsTest extends PaperTest {
    private GemItems gems;

    @BeforeEach
    void createGems() {
        gems = new GemItems(MockBukkit.createMockPlugin("soulgem"));
    }

    @Test
    void eachKindUsesItsShardAndPersistentIdentity() {
        for (var kind : GemItems.Kind.values()) {
            var item = gems.create(kind, false);
            assertEquals(kind.material, item.getType());
            assertEquals(kind, gems.kind(item));
            assertTrue(gems.marked(item));
            assertFalse(gems.charged(item));
            assertEquals(1, item.getMaxStackSize());
            assertEquals(0, gems.progress(item));
            assertEquals(0, gems.required(item));
        }
    }

    @Test
    void chargedGemsStackAndGlowWithoutFunctionalEnchantments() {
        for (var kind : GemItems.Kind.values()) {
            var item = gems.create(kind, true);
            assertTrue(gems.charged(item));
            assertEquals(64, item.getMaxStackSize());
            assertTrue(item.getItemMeta().getEnchantmentGlintOverride());
            assertTrue(item.getEnchantments().isEmpty());
        }
    }

    @Test
    void aRenamedOrdinaryShardIsNotAGem() {
        var item = new ItemStack(Material.ECHO_SHARD);
        item.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text("Charged Echo Soul Gem")));
        assertNull(gems.kind(item));
        assertFalse(gems.charged(item));
        assertFalse(gems.marked(null));
    }

    @Test
    void partialProgressSurvivesSerializationWithoutModifyingOriginal() {
        var original = gems.create(GemItems.Kind.AMETHYST, false);
        var progress = gems.withProgress(original, 19, 112);
        var restored = ItemStack.deserialize(progress.serialize());
        assertEquals(19, gems.progress(restored));
        assertEquals(112, gems.required(restored));
        assertEquals(0, gems.progress(original));
        assertEquals(GemItems.Kind.AMETHYST, gems.kind(restored));
    }

    @Test
    void changingTheUnderlyingMaterialCannotForgeAGem() {
        var item = gems.create(GemItems.Kind.ECHO, true).withType(Material.AMETHYST_SHARD);
        assertNull(gems.kind(item));
        assertFalse(gems.charged(item));
        assertTrue(gems.marked(item));
    }

    @Test
    void invalidStatesAreRejected() {
        var gem = gems.create(GemItems.Kind.AMETHYST, false);
        assertThrows(IllegalArgumentException.class, () -> gems.withProgress(gem, -1, 7));
        assertThrows(IllegalArgumentException.class, () -> gems.withProgress(gem, 7, 7));
        assertThrows(IllegalArgumentException.class, () -> gems.withProgress(gem, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                gems.withProgress(gems.create(GemItems.Kind.ECHO, true), 1, 7));
    }

    @Test
    void unknownKindDoesNotCountAsChargeable() {
        var item = gems.create(GemItems.Kind.ECHO, false);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("soulgem", "kind"), PersistentDataType.STRING, "UNKNOWN"));
        assertNull(gems.kind(item));
    }
}
