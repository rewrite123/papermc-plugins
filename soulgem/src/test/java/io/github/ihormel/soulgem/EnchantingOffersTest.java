package io.github.ihormel.soulgem;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class EnchantingOffersTest extends PaperTest {
    private ItemStack sword(int sharpness) {
        var result = new ItemStack(Material.DIAMOND_SWORD);
        if (sharpness > 0) result.addUnsafeEnchantment(Enchantment.SHARPNESS, sharpness);
        return result;
    }

    @Test
    void fullBookshelfThirdOfferAlwaysRequiresThirty() {
        for (int seed = 0; seed < 1000; seed++) {
            assertEquals(30, EnchantingOffers.requiredLevel(new Random(seed), 15, 2));
        }
    }

    @Test
    void shelfCountIsCappedAndNoShelfOffersStayInVanillaRanges() {
        for (int seed = 0; seed < 1000; seed++) {
            assertEquals(EnchantingOffers.requiredLevel(new Random(seed), 15, 2),
                    EnchantingOffers.requiredLevel(new Random(seed), 99, 2));
            int low = EnchantingOffers.requiredLevel(new Random(seed), 0, 0);
            int medium = EnchantingOffers.requiredLevel(new Random(seed), 0, 1);
            int high = EnchantingOffers.requiredLevel(new Random(seed), 0, 2);
            assertTrue(low >= 1 && low <= 2);
            assertTrue(medium >= 1 && medium <= 6);
            assertTrue(high >= 1 && high <= 8);
        }
    }

    @Test
    void normalGemsKeepRolledLevelsAndEchoGemsBoostThem() {
        assertEquals(5, EnchantingOffers.merge(sword(0), sword(5), false)
                .getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(6, EnchantingOffers.merge(sword(0), sword(5), true)
                .getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(3, EnchantingOffers.merge(sword(0), sword(2), true)
                .getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void echoBoostHasACapAndDoesNotIncrementExistingLevels() {
        assertEquals(6, EnchantingOffers.merge(sword(0), sword(9), true)
                .getEnchantmentLevel(Enchantment.SHARPNESS));
        assertNull(EnchantingOffers.merge(sword(6), sword(5), true));
        assertNull(EnchantingOffers.merge(sword(10), sword(5), true));
    }

    @Test
    void existingConflictsArePreservedAndNeverReplaced() {
        var existing = sword(0);
        existing.addUnsafeEnchantment(Enchantment.SMITE, 5);
        var rolled = sword(5);
        rolled.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        var result = EnchantingOffers.merge(existing, rolled, true);
        assertEquals(5, result.getEnchantmentLevel(Enchantment.SMITE));
        assertEquals(0, result.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(4, result.getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    void unchangedOffersAreDisabledWithoutMutatingInput() {
        var input = sword(5);
        assertNull(EnchantingOffers.merge(input, sword(3), false));
        assertEquals(5, input.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertNull(EnchantingOffers.merge(sword(0), sword(0), false));
    }

    @Test
    void upgradesPreserveDamageNamesLoreAndPersistentData() {
        var input = sword(2);
        input.editMeta(meta -> {
            meta.displayName(Component.text("My sword"));
            meta.lore(java.util.List.of(Component.text("Keep me")));
            ((Damageable) meta).setDamage(123);
            meta.getPersistentDataContainer().set(new NamespacedKey("test", "owner"),
                    PersistentDataType.STRING, "Alex");
        });
        var result = EnchantingOffers.merge(input, sword(5), true);
        var expected = input.clone();
        expected.addUnsafeEnchantment(Enchantment.SHARPNESS, 6);
        assertEquals(expected, result);
        assertEquals(2, input.getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void singleLevelEnchantmentsCanReachLevelTwo() {
        var rolled = new ItemStack(Material.DIAMOND_HELMET);
        rolled.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        var result = EnchantingOffers.merge(new ItemStack(Material.DIAMOND_HELMET), rolled, true);
        assertEquals(2, result.getEnchantmentLevel(Enchantment.AQUA_AFFINITY));
    }

    @Test
    void booksReceiveStoredEnchantmentsAndExistingBookEnchantmentsSurvive() {
        var rolled = new ItemStack(Material.ENCHANTED_BOOK);
        rolled.editMeta(EnchantmentStorageMeta.class,
                meta -> meta.addStoredEnchant(Enchantment.SHARPNESS, 5, true));
        var first = EnchantingOffers.merge(new ItemStack(Material.BOOK), rolled, true);
        assertEquals(Material.ENCHANTED_BOOK, first.getType());
        assertEquals(6, ((EnchantmentStorageMeta) first.getItemMeta()).getStoredEnchantLevel(Enchantment.SHARPNESS));
        var existing = new ItemStack(Material.ENCHANTED_BOOK);
        existing.editMeta(EnchantmentStorageMeta.class,
                meta -> meta.addStoredEnchant(Enchantment.MENDING, 1, true));
        var upgraded = EnchantingOffers.merge(existing, rolled, true);
        var meta = (EnchantmentStorageMeta) upgraded.getItemMeta();
        assertEquals(1, meta.getStoredEnchantLevel(Enchantment.MENDING));
        assertEquals(6, meta.getStoredEnchantLevel(Enchantment.SHARPNESS));
    }

    @Test
    void rollsAreDeterministicAndStartWithUnenchantedCopies() {
        var input = sword(1);
        var seeds = new java.util.ArrayList<Integer>();
        var service = new EnchantingOffers((clean, level, seed) -> {
            assertEquals(0, clean.getEnchantmentLevel(Enchantment.SHARPNESS));
            seeds.add(seed);
            clean.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            return clean;
        });
        var first = service.create(input, GemItems.Kind.AMETHYST, 15, 42);
        var second = service.create(input, GemItems.Kind.AMETHYST, 15, 42);
        assertEquals(first, second);
        assertEquals(java.util.List.of(42, 43, 44, 42, 43, 44), seeds);
        for (int index = 0; index < 3; index++) assertEquals(index + 1, first.get(index).cost());
        assertEquals(1, input.getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void multipleItemsCannotBeEnchantedAsOnePurchase() {
        var input = new ItemStack(Material.BOOK, 2);
        var service = new EnchantingOffers((item, level, seed) -> {
            fail("Stacked input must not be rolled");
            return item;
        });
        assertTrue(service.create(input, GemItems.Kind.ECHO, 15, 42)
                .stream().allMatch(offer -> offer.result() == null));
    }
}
