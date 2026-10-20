package io.github.ihormel.soulgem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.EnchantmentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class EnchantingListenerTest extends PaperTest {
    private GemItems gems;
    private EnchantingListener listener;
    private Player player;
    private EnchantingInventory table;
    private EnchantmentView view;

    @BeforeEach
    void setUpEnchanting() {
        gems = new GemItems(MockBukkit.createMockPlugin("soulgem"));
        listener = new EnchantingListener(gems);
        player = mock(Player.class);
        when(player.hasPermission("soulgem.use")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        table = mock(EnchantingInventory.class);
        view = mock(EnchantmentView.class);
        when(view.getTopInventory()).thenReturn(table);
        when(view.getPlayer()).thenReturn(player);
    }

    private EnchantmentOffer[] offers() {
        return new EnchantmentOffer[]{
                new EnchantmentOffer(Enchantment.UNBREAKING, 1, 5),
                new EnchantmentOffer(Enchantment.SHARPNESS, 3, 17),
                new EnchantmentOffer(Enchantment.SHARPNESS, 5, 30)};
    }

    private PrepareItemEnchantEvent prepare(ItemStack item, ItemStack fuel, boolean preCancelled) {
        when(table.getSecondary()).thenReturn(fuel);
        var event = new PrepareItemEnchantEvent(player, view, null, item, offers(), 15);
        event.setCancelled(preCancelled);
        listener.prepare(event);
        return event;
    }

    private EnchantItemEvent enchant(ItemStack item, ItemStack fuel, Map<Enchantment, Integer> rolled) {
        when(table.getSecondary()).thenReturn(fuel);
        var event = new EnchantItemEvent(player, view, null, item, 30, new HashMap<>(rolled),
                rolled.keySet().iterator().next(), 1, 2);
        listener.enchant(event);
        return event;
    }

    private ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        var item = new ItemStack(material);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }

    @Test
    void chargedGemsShowVanillaOffersAndPlainLapisClearsThem() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        assertFalse(prepare(sword, gems.create(GemItems.Kind.AMETHYST, true).asQuantity(3), false).isCancelled());
        assertFalse(prepare(sword, null, false).isCancelled());
        verify(player, never()).sendActionBar(any(net.kyori.adventure.text.Component.class));

        assertTrue(prepare(sword, new ItemStack(Material.LAPIS_LAZULI, 3), false).isCancelled());
        verify(player).sendActionBar(EnchantingListener.LAPIS_REFUSED);
    }

    @Test
    void amethystOffersStayVanillaAndEchoOffersShowTheBoostedLevel() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        var plain = prepare(sword, gems.create(GemItems.Kind.AMETHYST, true), false).getOffers();
        assertEquals(1, plain[0].getEnchantmentLevel());
        assertEquals(5, plain[2].getEnchantmentLevel());
        var echo = prepare(sword, gems.create(GemItems.Kind.ECHO, true), false).getOffers();
        assertEquals(2, echo[0].getEnchantmentLevel());
        assertEquals(4, echo[1].getEnchantmentLevel());
        assertEquals(6, echo[2].getEnchantmentLevel());
        assertEquals(30, echo[2].getCost());
    }

    @Test
    void alreadyEnchantedGearIsUnlockedForUpgrades() {
        var gem = gems.create(GemItems.Kind.AMETHYST, true);
        var enchantedSword = enchanted(Material.DIAMOND_SWORD, Enchantment.UNBREAKING, 1);
        assertFalse(prepare(enchantedSword, gem, true).isCancelled());
        assertTrue(prepare(new ItemStack(Material.DIAMOND_SWORD).asQuantity(2), gem, true).isCancelled());
        assertTrue(prepare(new ItemStack(Material.ENCHANTED_BOOK), gem, true).isCancelled());
        assertTrue(prepare(new ItemStack(Material.STICK), gem, true).isCancelled());
    }

    @Test
    void playersWithoutPermissionOrInSpectatorCannotEnchant() {
        var gem = gems.create(GemItems.Kind.AMETHYST, true);
        when(player.hasPermission("soulgem.use")).thenReturn(false);
        assertTrue(prepare(new ItemStack(Material.DIAMOND_SWORD), gem, false).isCancelled());
        assertTrue(enchant(new ItemStack(Material.DIAMOND_SWORD), gem, Map.of(Enchantment.SHARPNESS, 2)).isCancelled());
        when(player.hasPermission("soulgem.use")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertTrue(prepare(new ItemStack(Material.DIAMOND_SWORD), gem, false).isCancelled());
    }

    @Test
    void enchantingRequiresChargedGemsExceptCreativeWithAnEmptySlot() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        var rolled = Map.of(Enchantment.SHARPNESS, 3);
        assertTrue(enchant(sword, new ItemStack(Material.LAPIS_LAZULI, 3), rolled).isCancelled());
        assertTrue(enchant(sword, null, rolled).isCancelled());
        verify(player, times(2)).sendMessage(EnchantingListener.LAPIS_REFUSED);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertFalse(enchant(sword, null, rolled).isCancelled());
        assertTrue(enchant(sword, new ItemStack(Material.LAPIS_LAZULI), rolled).isCancelled());
    }

    @Test
    void amethystAppliesTheVanillaRollAndEchoAddsALevelAboveMaximum() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        var rolled = Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 2);
        var plain = enchant(sword, gems.create(GemItems.Kind.AMETHYST, true), rolled);
        assertFalse(plain.isCancelled());
        assertEquals(rolled, plain.getEnchantsToAdd());
        var echo = enchant(sword, gems.create(GemItems.Kind.ECHO, true), rolled);
        assertEquals(Map.of(Enchantment.SHARPNESS, 6, Enchantment.UNBREAKING, 3), echo.getEnchantsToAdd());
        var capped = enchant(sword, gems.create(GemItems.Kind.ECHO, true), Map.of(Enchantment.SHARPNESS, 6));
        assertEquals(Map.of(Enchantment.SHARPNESS, 6), capped.getEnchantsToAdd());
    }

    @Test
    void upgradesSkipConflictsAndLowerLevelsAndRefuseUselessOffers() {
        var sword = enchanted(Material.DIAMOND_SWORD, Enchantment.SMITE, 4);
        sword.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        var gem = gems.create(GemItems.Kind.AMETHYST, true);
        var event = enchant(sword, gem, Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 2,
                Enchantment.LOOTING, 2));
        assertEquals(Map.of(Enchantment.LOOTING, 2), event.getEnchantsToAdd());

        var useless = enchant(sword, gem, Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3));
        assertTrue(useless.isCancelled());
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));

        var echo = enchant(sword, gems.create(GemItems.Kind.ECHO, true), Map.of(Enchantment.UNBREAKING, 3));
        assertEquals(Map.of(Enchantment.UNBREAKING, 4), echo.getEnchantsToAdd());
    }

    @Test
    void booksUseTheirStoredEnchantmentsAsTheExistingSet() {
        var book = new ItemStack(Material.ENCHANTED_BOOK);
        book.editMeta(org.bukkit.inventory.meta.EnchantmentStorageMeta.class,
                meta -> meta.addStoredEnchant(Enchantment.SHARPNESS, 5, true));
        assertEquals(Map.of(Enchantment.SHARPNESS, 5), EnchantingRules.enchantments(book));
        assertEquals(Map.of(), EnchantingRules.enchantments(new ItemStack(Material.BOOK)));
        assertEquals(Map.of(), EnchantingRules.improvements(Map.of(Enchantment.SHARPNESS, 5),
                Map.of(Enchantment.SMITE, 5), false));
    }

    @Test
    void legacyChargedShardsAreConvertedInInventories() {
        var inventory = server.addPlayer().getInventory();
        var legacy = gems.create(GemItems.Kind.ECHO, true).withType(Material.ECHO_SHARD).asQuantity(7);
        var uncharged = gems.create(GemItems.Kind.AMETHYST, false);
        inventory.setItem(3, legacy);
        inventory.setItemInOffHand(uncharged);
        inventory.setItem(5, new ItemStack(Material.ECHO_SHARD, 2));
        listener.upgradeLegacy(inventory);
        assertEquals(gems.create(GemItems.Kind.ECHO, true).asQuantity(7), inventory.getItem(3));
        assertEquals(uncharged, inventory.getItemInOffHand());
        assertEquals(new ItemStack(Material.ECHO_SHARD, 2), inventory.getItem(5));
    }

    @Test
    void pluginRegistersAndDoesNotInterceptTheVanillaTable() {
        var plugin = MockBukkit.load(SoulGemPlugin.class);
        var joined = server.addPlayer();
        var legacy = new GemItems(plugin).create(GemItems.Kind.AMETHYST, true).withType(Material.AMETHYST_SHARD);
        joined.getInventory().setItem(0, legacy);
        var opened = joined.openInventory(server.createInventory(null, org.bukkit.event.inventory.InventoryType.ENCHANTING));
        assertNotNull(opened);
        assertEquals(org.bukkit.event.inventory.InventoryType.ENCHANTING, joined.getOpenInventory().getType());
        assertEquals(Material.LAPIS_LAZULI, joined.getInventory().getItem(0).getType());
    }
}
